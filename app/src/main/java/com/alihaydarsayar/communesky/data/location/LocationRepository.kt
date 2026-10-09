package com.alihaydarsayar.communesky.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.PlaceNaming
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Cihazın konumunu bulur ve koordinatı şehir/semt adına çevirir. */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(context) }


    /** Yaklaşık ya da hassas konum izni var mı? Uygulama ikisiyle de çalışır. */
    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Kullanıcı hassas konuma izin verdi mi? (Android 12+ izin penceresinde "Yaklaşık"ı seçerse
     * sadece yaklaşık konum verilir.) Mahalle adı sadece hassas konumda gösterilir.
     */
    fun hasPreciseLocation(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * İzin yoksa, konum kapalıysa veya bulunamazsa null döner. Sadece uygulama açıkken çağrılır;
     * GPS'e zorlanmaz:
     * 1. Wi-Fi/baz istasyonu (pil dostu, "balanced"). Son 15 dakikada bulunmuş konum varsa anında döner.
     * 2. Olmazsa cihazın bildiği son konum (anında); hava durumu için biraz eski konum da yeterli.
     */
    @SuppressLint("MissingPermission") // İzin hasPermission() ile kontrol ediliyor.
    suspend fun getCurrentLocation(): DeviceLocation? {
        if (!hasPermission()) return null
        val location = requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, BALANCED_TIMEOUT_MS)
            ?: locationClient.lastLocation.await()
            ?: return null
        val precise = hasPreciseLocation()
        val address = findAddress(location.latitude, location.longitude)
        val naming = PlaceNaming.fromAddress(
            subLocality = address?.subLocality,
            locality = address?.locality,
            subAdminArea = address?.subAdminArea,
            adminArea = address?.adminArea,
            precise = precise,
        )
        return DeviceLocation(
            city = City(
                name = naming.name,
                latitude = location.latitude,
                longitude = location.longitude,
                region = naming.region,
            ),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int, timeoutMs: Long): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
            .build()
        val cancellation = CancellationTokenSource()
        // Süre dolarsa withTimeoutOrNull isteği iptal eder; await(cancellation) isteği de kapatır.
        return withTimeoutOrNull(timeoutMs) {
            locationClient.getCurrentLocation(request, cancellation.token).await(cancellation)
        }
    }

    /**
     * Android'in Geocoder servisiyle koordinatın adresi (telefonun dilinde). Mahalle ve ilçe adı
     * için hassas koordinat kullanılır; bu servis cihazın işletim sistemi tarafından sağlanır.
     */
    private suspend fun findAddress(latitude: Double, longitude: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, context.resources.configuration.locales[0])
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoder hatası: $errorMessage")
                            continuation.resume(null)
                        }
                    })
                }
            }
        } else {
            // Eski sürümlerde bu çağrı bekletici (blocking) olduğu için arka plan iş parçacığında.
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }
                    .onFailure { Log.w(TAG, "Geocoder hatası", it) }
                    .getOrNull()
                    ?.firstOrNull()
            }
        }
    }

    /** Cihaz konumu ve hata payı (metre; Android'in verdiği %68 güven yarıçapı). */
    data class DeviceLocation(val city: City, val accuracyMeters: Float?)

    private companion object {
        const val TAG = "LocationRepository"
        const val MAX_LOCATION_AGE_MS = 15 * 60 * 1000L
        const val BALANCED_TIMEOUT_MS = 6_000L
        const val GEOCODER_TIMEOUT_MS = 6_000L
    }
}
