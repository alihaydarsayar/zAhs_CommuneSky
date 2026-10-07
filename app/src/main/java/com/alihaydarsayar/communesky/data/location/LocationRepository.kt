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
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Cihazın konumunu bulur ve koordinatı şehir/semt adına çevirir. */
class LocationRepository(private val context: Context) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** İzin yoksa, konum kapalıysa veya hiçbir yoldan bulunamazsa null döner. */
    @SuppressLint("MissingPermission") // İzin hasPermission() ile kontrol ediliyor.
    suspend fun getCurrentCity(): City? {
        if (!hasPermission()) return null
        // Hızlıdan yavaşa, ucuzdan pahalıya üç deneme:
        // 1. Wi-Fi/baz istasyonu (pil dostu). Son 15 dakikada bulunmuş konum varsa anında döner.
        // 2. Olmazsa cihazın bildiği son konum (anında); hava durumu için biraz eski konum da yeterli.
        // 3. Cihaz hiç konum bilmiyorsa son çare olarak kısa süreliğine GPS.
        val location = requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, BALANCED_TIMEOUT_MS)
            ?: locationClient.lastLocation.await()
            ?: requestLocation(Priority.PRIORITY_HIGH_ACCURACY, HIGH_ACCURACY_TIMEOUT_MS)
            ?: return null
        val name = findPlaceName(location.latitude, location.longitude)
        return City(name = name, latitude = location.latitude, longitude = location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int, timeoutMs: Long): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
            .build()
        val cancellation = CancellationTokenSource()
        // Süre dolarsa withTimeoutOrNull isteği iptal eder; await(cancellation) GPS'i de kapatır.
        return withTimeoutOrNull(timeoutMs) {
            locationClient.getCurrentLocation(request, cancellation.token).await(cancellation)
        }
    }

    /** Android'in Geocoder servisiyle koordinatı telefonun dilinde bir yer adına çevirir. */
    private suspend fun findPlaceName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, context.resources.configuration.locales[0])
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        return address?.run { locality ?: subAdminArea ?: adminArea }
    }

    private companion object {
        const val TAG = "LocationRepository"
        const val MAX_LOCATION_AGE_MS = 15 * 60 * 1000L
        const val BALANCED_TIMEOUT_MS = 5_000L
        const val HIGH_ACCURACY_TIMEOUT_MS = 10_000L
    }
}
