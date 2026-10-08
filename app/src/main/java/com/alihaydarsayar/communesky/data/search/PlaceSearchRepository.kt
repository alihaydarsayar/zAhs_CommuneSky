package com.alihaydarsayar.communesky.data.search

import android.util.Log
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Aramanın sonucu: bulunan yerler ya da hiçbir servise ulaşılamadı bilgisi. */
sealed interface SearchOutcome {
    data class Found(val results: List<PlaceSearchResult>) : SearchOutcome
    data object Failed : SearchOutcome
}

/** Son çare yer arama (Android Geocoder). Testte sahtesi verilebilsin diye arayüz. */
interface FallbackGeocoder {
    /** Geocoder yoksa ya da hata verirse null. */
    suspend fun search(query: String, locale: Locale): List<PlaceSearchResult>?
}

/** Sonuçlarda öne alınacak ülkeler (telefonun ağı, SIM'i, dili). */
fun interface CountryHints {
    fun countries(locale: Locale): Set<String>
}

/**
 * Yer arama. Neden bu sıra:
 * 1. Photon (OpenStreetMap): Atakum, Tuzla, Moda, Kurupelit gibi ilçe, semt ve köyleri en iyi bulan
 *    ücretsiz kaynak; yazarken arama için tasarlanmış. Google servisi gerektirmez.
 * 2. Open-Meteo Geocoding (GeoNames): Photon ile aynı anda sorulur; Photon'un kaçırdığı yerleri
 *    tamamlar ve Photon'a ulaşılamazsa tek başına yeter.
 * 3. Android Geocoder: ikisi de sonuç vermezse son çare. Google servisleri olmayan cihazlarda
 *    çoğu zaman hiç yoktur; o yüzden birincil olamaz.
 *
 * Photon sınır koyarsa (429) ya da sunucu hatası verirse bir süre hiç sorulmaz; arama bu sürede
 * Open-Meteo ile devam eder, kullanıcı bekletilmez.
 */
@Singleton
class PlaceSearchRepository @Inject constructor(
    private val photonApi: Lazy<PhotonApi>,
    private val openMeteoApi: Lazy<OpenMeteoGeocodingApi>,
    private val fallbackGeocoder: FallbackGeocoder,
    private val countryHints: CountryHints,
) {
    /** Testlerde saat değiştirilebilsin diye. */
    internal var clock: () -> Long = System::currentTimeMillis

    @Volatile private var photonPausedUntil = 0L

    suspend fun search(
        query: String,
        locale: Locale,
        anchors: List<GeoPoint> = emptyList(),
    ): SearchOutcome = coroutineScope {
        val language = locale.language
        // İki servis paralel sorulur; yavaş olan diğerini bekletmez (her biri en fazla 6 sn).
        val photon = async {
            if (clock() < photonPausedUntil) return@async null
            fetch("Photon", onHttpError = ::pausePhotonIfLimited) {
                SearchResultMapping.fromPhoton(
                    photonApi.get().search(query, language = if (language == "en") "en" else "default"),
                    query = query,
                    preferredCountries = countryHints.countries(locale),
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
                val fallback = fallbackGeocoder.search(query, locale)
                when {
                    !fallback.isNullOrEmpty() -> SearchOutcome.Found(fallback)
                    // Servisler cevap verdi ama yer yok: "sonuç yok". Hiçbiri cevap vermediyse hata.
                    photonResults != null || meteoResults != null || fallback != null ->
                        SearchOutcome.Found(emptyList())
                    else -> SearchOutcome.Failed
                }
            }
        }
    }

    /** 429 (çok fazla istek) ya da 5xx: Photon'u bir süre rahat bırak. */
    private fun pausePhotonIfLimited(e: HttpException) {
        if (e.code() == 429 || e.code() >= 500) {
            photonPausedUntil = clock() + PHOTON_PAUSE_MS
        }
    }

    /** Hata ya da zaman aşımında null döner; arama diğer kaynaklarla devam eder. */
    private suspend fun fetch(
        source: String,
        onHttpError: (HttpException) -> Unit = {},
        block: suspend () -> List<PlaceSearchResult>,
    ): List<PlaceSearchResult>? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        // Kullanıcı yazmaya devam ettiyse arama iptal edilir; bunu yutmamalıyız.
        throw e
    } catch (e: HttpException) {
        Log.w(TAG, "$source araması başarısız: HTTP ${e.code()}")
        onHttpError(e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "$source araması başarısız", e)
        null
    }

    internal companion object {
        const val TAG = "PlaceSearch"
        const val TIMEOUT_MS = 6_000L
        const val PHOTON_PAUSE_MS = 10 * 60 * 1000L
    }
}
