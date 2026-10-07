package com.alihaydarsayar.communesky.data

import android.content.Context
import com.alihaydarsayar.communesky.data.location.LocationRepository
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Hangi yerin havasını yenileyelim?" sorusunu cevaplayıp yenilemeyi yapan sınıf.
 * Hem ekran (ViewModel) hem de arka plan işi (WorkManager) bunu kullanır.
 */
@Singleton
class WeatherUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
) {
    /** Konum izni varsa cihaz konumunu bulur; yoksa en mantıklı yedeği seçer. Hata fırlatmaz. */
    suspend fun resolvePlace(): ResolvedPlace {
        val hasPermission = locationRepository.hasPermission()
        val located = if (hasPermission) locationRepository.getCurrentCity() else null
        val place = when {
            located != null -> Place(located, isCurrentLocation = true)
            // İzin var ama konum şu an bulunamadı: son bilinen konumu kullanmaya devam et.
            hasPermission -> weatherRepository.lastPlace()?.takeIf { it.isCurrentLocation }
                ?: Place(City.Istanbul, isCurrentLocation = false)
            else -> Place(City.Istanbul, isCurrentLocation = false)
        }
        return ResolvedPlace(place, hasPermission = hasPermission, located = located != null)
    }

    /** Seçilen yerin hava durumunu indirir, önbelleğe yazar ve widget'ları günceller. */
    suspend fun refresh(place: Place) {
        weatherRepository.refresh(place)
        WeatherWidgetUpdater.updateAll(context)
    }

    /**
     * Arka plan güncellemesi: konuma dokunmadan (arka planda konum izni gerektirir)
     * son gösterilen yeri yeniler.
     */
    suspend fun refreshLastPlace() {
        val place = weatherRepository.lastPlace() ?: Place(City.Istanbul, isCurrentLocation = false)
        refresh(place)
    }
}

data class ResolvedPlace(
    val place: Place,
    val hasPermission: Boolean,
    val located: Boolean,
)
