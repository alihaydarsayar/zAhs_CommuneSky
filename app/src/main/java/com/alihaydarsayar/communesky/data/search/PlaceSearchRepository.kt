package com.alihaydarsayar.communesky.data.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Aramanın sonucu: bulunan yerler ya da hiçbir servise ulaşılamadı bilgisi. */
sealed interface SearchOutcome {
    data class Found(val results: List<PlaceSearchResult>) : SearchOutcome
    data object Failed : SearchOutcome
}

/**
 * Yer arama. Neden bu sıra:
 * 1. Photon (OpenStreetMap): Atakum, Tuzla, Moda, Kurupelit gibi ilçe, semt ve köyleri en iyi bulan
 *    ücretsiz kaynak; yazarken arama için tasarlanmış. Google servisi gerektirmez.
 * 2. Open-Meteo Geocoding (GeoNames): Photon ile aynı anda sorulur; Photon'un kaçırdığı yerleri
 *    tamamlar ve Photon'a ulaşılamazsa tek başına yeter.
 * 3. Android Geocoder: ikisi de sonuç vermezse son çare. Google servisleri olmayan cihazlarda
 *    çoğu zaman hiç yoktur; o yüzden birincil olamaz.
 */
@Singleton
class PlaceSearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photonApi: Lazy<PhotonApi>,
    private val openMeteoApi: Lazy<OpenMeteoGeocodingApi>,
) {
    suspend fun search(
        query: String,
        locale: Locale,
        anchors: List<GeoPoint> = emptyList(),
    ): SearchOutcome = coroutineScope {
        val language = locale.language
        // İki servis paralel sorulur; yavaş olan diğerini bekletmez (her biri en fazla 6 sn).
        val photon = async {
            fetch("Photon") {
                SearchResultMapping.fromPhoton(
                    photonApi.get().search(query, language = if (language == "en") "en" else "default"),
                    query = query,
                    preferredCountries = preferredCountries(locale),
                    anchors = anchors,
                )
            }
        }
        val meteo = async {
            fetch("Open-Meteo") {
                SearchResultMapping.fromOpenMeteo(openMeteoApi.get().search(query, language = language))
            }
        }
        val photonResults = photon.await()
        val meteoResults = meteo.await()
        val merged = SearchResultMapping.merge(photonResults.orEmpty(), meteoResults.orEmpty())
        when {
            merged.isNotEmpty() -> SearchOutcome.Found(merged)
            else -> {
                val fallback = androidGeocoder(query, locale)
                when {
                    !fallback.isNullOrEmpty() -> SearchOutcome.Found(fallback)
                    // Servisler cevap verdi ama yer yok: "sonuç yok". Hiçbiri cevap vermediyse hata.
                    photonResults != null || meteoResults != null -> SearchOutcome.Found(emptyList())
                    else -> SearchOutcome.Failed
                }
            }
        }
    }

    /** Hata ya da zaman aşımında null döner; arama diğer kaynaklarla devam eder. */
    private suspend fun fetch(
        source: String,
        block: suspend () -> List<PlaceSearchResult>,
    ): List<PlaceSearchResult>? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        // Kullanıcı yazmaya devam ettiyse arama iptal edilir; bunu yutmamalıyız.
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$source araması başarısız", e)
        null
    }

    /** Sonuçlarda önce bu ülkelerdeki yerler gelir: telefonun bağlı olduğu ağ ve dil ayarı. */
    private fun preferredCountries(locale: Locale): Set<String> {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return setOfNotNull(
            telephony?.networkCountryIso,
            telephony?.simCountryIso,
            locale.country,
        ).filter { it.isNotBlank() }.toSet()
    }

    private suspend fun androidGeocoder(query: String, locale: Locale): List<PlaceSearchResult>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        val addresses: List<Address>? = withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoder hatası: $errorMessage")
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocationName(query, 5) }
                        .onFailure { Log.w(TAG, "Geocoder hatası", it) }
                        .getOrNull()
                }
            }
        }
        return addresses?.mapNotNull { address ->
            val name = address.locality ?: address.subAdminArea ?: address.featureName ?: return@mapNotNull null
            PlaceSearchResult(
                name = name,
                region = listOfNotNull(address.adminArea, address.countryName)
                    .filter { it != name }
                    .joinToString(", ")
                    .ifEmpty { null },
                countryCode = address.countryCode,
                latitude = address.latitude,
                longitude = address.longitude,
            )
        }
    }

    private companion object {
        const val TAG = "PlaceSearch"
        const val TIMEOUT_MS = 6_000L
    }
}
