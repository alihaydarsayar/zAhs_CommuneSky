package com.alihaydarsayar.communesky.data

import android.content.Context
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.location.LocationRepository
import com.alihaydarsayar.communesky.data.observation.ObservationRepository
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Hangi yerlerin havasını yenileyelim?" sorusunu cevaplayıp yenilemeyi yapan sınıf.
 * Hem ekran (ViewModel) hem de arka plan işi (WorkManager) bunu kullanır.
 */
@Singleton
class WeatherUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val placesRepository: PlacesRepository,
    private val observationRepository: ObservationRepository,
) {
    /** Konum izni varsa cihaz konumunu bulur; yoksa en mantıklı yedeği seçer. Hata fırlatmaz. */
    suspend fun resolveDevicePlace(): ResolvedPlace {
        val hasPermission = locationRepository.hasPermission()
        val located = if (hasPermission) locationRepository.getCurrentLocation() else null
        val place = when {
            located != null -> Place(
                id = DEVICE_PLACE_ID,
                city = located.city,
                isCurrentLocation = true,
                accuracyMeters = located.accuracyMeters,
            )
            // İzin var ama konum şu an bulunamadı: son bilinen konumu kullanmaya devam et.
            hasPermission -> weatherRepository.devicePlace()?.takeIf { it.isCurrentLocation }
                ?: fallbackPlace()
            else -> fallbackPlace()
        }
        return ResolvedPlace(place, hasPermission = hasPermission, located = located != null)
    }

    /**
     * Ekrandaki bütün yerleri tek istekle yeniler: "Bulunduğum yer" (gösteriliyorsa) ve
     * kayıtlı yerler. Önbelleği ve widget'ları günceller; ağ hatasında istisna fırlatır.
     */
    suspend fun refreshAll(resolved: ResolvedPlace) {
        val saved = placesRepository.all()
        val device = resolved.place.takeIf { PlaceSelection.showsDevicePage(resolved.hasPermission, saved.size) }
        refresh(listOfNotNull(device) + saved.map { it.toPlace() })
    }

    /**
     * Ekrandaki yerlerin istasyon ölçümlerini yeniler (3 dakikadan yeni olanlar atlanır).
     * Tahminden ayrı çağrılır; ekran bunu beklemez ve hiçbir hata fırlatmaz.
     */
    suspend fun refreshObservations(resolved: ResolvedPlace? = null) {
        val saved = placesRepository.all()
        val hasPermission = resolved?.hasPermission ?: locationRepository.hasPermission()
        val device = if (PlaceSelection.showsDevicePage(hasPermission, saved.size)) {
            resolved?.place ?: weatherRepository.devicePlace()
        } else {
            null
        }
        if (observationRepository.refresh(listOfNotNull(device) + saved.map { it.toPlace() })) {
            WeatherWidgetUpdater.updateAll(context)
        }
    }

    /** Sadece verilen yerleri yeniler (ör. yeni eklenen yer). */
    suspend fun refresh(places: List<Place>) {
        weatherRepository.refresh(places)
        WeatherWidgetUpdater.updateAll(context)
    }

    /**
     * Arka plan güncellemesi: konuma dokunmadan (arka planda konum izni gerektirir) son bilinen
     * cihaz konumunu ve bütün kayıtlı yerleri tek istekle yeniler.
     */
    suspend fun refreshInBackground() {
        val saved = placesRepository.all()
        val showsDevice = PlaceSelection.showsDevicePage(locationRepository.hasPermission(), saved.size)
        val device = if (showsDevice) weatherRepository.devicePlace() ?: fallbackPlace() else null
        refresh(listOfNotNull(device) + saved.map { it.toPlace() })
        refreshObservations()
    }

    private fun fallbackPlace() = Place(DEVICE_PLACE_ID, City.Istanbul, isCurrentLocation = false)
}

fun SavedPlace.toPlace() = Place(
    id = id,
    city = City(name, latitude, longitude),
    isCurrentLocation = false,
)

data class ResolvedPlace(
    val place: Place,
    val hasPermission: Boolean,
    val located: Boolean,
)

/** Hangi yerlerin gösterileceğine dair kurallar. Saf mantık; test edilir. */
object PlaceSelection {

    /**
     * "Bulunduğum yer" sayfası: konum izni varsa her zaman ilk sırada. İzin yoksa sadece hiç
     * kayıtlı yer yokken gösterilir (yedek şehirle ve "Konumumu kullan" düğmesiyle).
     */
    fun showsDevicePage(hasPermission: Boolean, savedCount: Int): Boolean =
        hasPermission || savedCount == 0

    /** Ana ekrandaki sayfaların kimlikleri, sırasıyla. */
    fun pageIds(hasPermission: Boolean, saved: List<SavedPlace>): List<Long> {
        val savedIds = saved.map { it.id }
        return if (showsDevicePage(hasPermission, saved.size)) listOf(DEVICE_PLACE_ID) + savedIds else savedIds
    }
}
