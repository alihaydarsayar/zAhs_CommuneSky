package com.alihaydarsayar.communesky.data.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/** Android'in yerleşik Geocoder'ı ile yer arama (cihazda Google servisleri varsa Google'a sorar). */
class AndroidFallbackGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : FallbackGeocoder {

    override suspend fun search(query: String, locale: Locale): List<PlaceSearchResult>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        val addresses: List<Address>? = withTimeoutOrNull(PlaceSearchRepository.TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(PlaceSearchRepository.TAG, "Geocoder hatası: $errorMessage")
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocationName(query, 5) }
                        .onFailure { Log.w(PlaceSearchRepository.TAG, "Geocoder hatası", it) }
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
}

/** Telefonun bağlı olduğu ağın ve SIM'in ülkesi ile dil ayarındaki ülke. Ağa bir şey gönderilmez. */
class TelephonyCountryHints @Inject constructor(
    @ApplicationContext private val context: Context,
) : CountryHints {
    override fun countries(locale: Locale): Set<String> {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return setOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, locale.country)
            .filter { it.isNotBlank() }
            .toSet()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds abstract fun bindFallbackGeocoder(impl: AndroidFallbackGeocoder): FallbackGeocoder
    @Binds abstract fun bindCountryHints(impl: TelephonyCountryHints): CountryHints
}
