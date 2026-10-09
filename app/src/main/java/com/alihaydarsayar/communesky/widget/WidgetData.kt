package com.alihaydarsayar.communesky.widget

import android.content.Context
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.HomeProximity
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Widget'lar, Hilt'in yönettiği depolara bu "giriş noktası" üzerinden ulaşır. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun placesRepository(): PlacesRepository
    fun settingsRepository(): SettingsRepository
}

/** Widget'ta gösterilecek bir yer: önbellekteki havası ve Ev olup olmadığı. */
data class WidgetPlaceData(val snapshot: WeatherSnapshot, val isHome: Boolean) {
    val point: GeoPoint get() = GeoPoint(snapshot.city.latitude, snapshot.city.longitude)
    val isCurrentLocation: Boolean get() = snapshot.isCurrentLocation
}

/**
 * Bütün widget'ların ortak girdisi: ayarlar, kayıtlı yerler ve önbellekteki hava. Veri tek yerden
 * gelir: WorkManager işi tüm yerleri tek istekte günceller, widget'lar sadece Room önbelleğinden
 * okur ve kendi başına internete çıkmaz.
 */
data class WidgetInput(
    val settings: AppSettings,
    val places: List<SavedPlace>,
    val weather: Map<Long, WeatherSnapshot>,
) {
    val home: WidgetPlaceData?
        get() = places.firstOrNull { it.isHome }?.let { weather[it.id] }?.let { WidgetPlaceData(it, isHome = true) }

    val device: WeatherSnapshot? get() = weather[DEVICE_PLACE_ID]

    /** Evde / yakında / uzakta kararı (tamamen cihazda). */
    val homeLocation: HomeLocationState
        get() = HomeLocationState.of(device, home, settings.homeAway.nearbyRadiusKm)

    /** Tek yer gösteren widget'ların yeri. */
    fun place(place: WidgetPlace): WidgetPlaceData? = resolvePlace(place, this)

    /** Yerlerim: önce Ev, sonra kayıtlı sıra; istenirse sona bulunduğun yer. */
    fun savedPlaces(includeLocation: Boolean): List<WidgetPlaceData> {
        val saved = places.sortedWith(compareByDescending<SavedPlace> { it.isHome }.thenBy { it.sortOrder })
            .mapNotNull { place -> weather[place.id]?.let { WidgetPlaceData(it, place.isHome) } }
        val here = device?.takeIf { it.isCurrentLocation }?.let { WidgetPlaceData(it, isHome = false) }
        return when {
            here == null || !includeLocation -> saved
            saved.isEmpty() -> listOf(here)
            // Evdeysen konum Ev'i tekrar etmesin.
            homeLocation is HomeLocationState.AtHome -> saved
            else -> saved.take(1) + here + saved.drop(1)
        }.ifEmpty { listOfNotNull(device?.let { WidgetPlaceData(it, isHome = false) }) }
    }

    companion object {
        /** Saf mantık; test edilir. Yer silinmişse ya da Ev seçilmemişse cihaz konumuna döner. */
        fun resolvePlace(place: WidgetPlace, input: WidgetInput): WidgetPlaceData? {
            val saved = input.places
            val weather = input.weather
            if (place == WidgetPlace.Smart) {
                return when (val state = input.homeLocation) {
                    is HomeLocationState.AtHome -> state.home
                    is HomeLocationState.Nearby -> state.home
                    is HomeLocationState.Away -> state.here
                    is HomeLocationState.Single -> state.place
                    HomeLocationState.Empty -> null
                }
            }
            val id = when (place) {
                WidgetPlace.Home -> saved.firstOrNull { it.isHome }?.id ?: DEVICE_PLACE_ID
                is WidgetPlace.Saved -> place.placeId.takeIf { id -> saved.any { it.id == id } } ?: DEVICE_PLACE_ID
                else -> DEVICE_PLACE_ID
            }
            val snapshot = weather[id] ?: weather[DEVICE_PLACE_ID] ?: return null
            return WidgetPlaceData(snapshot, isHome = saved.any { it.id == snapshot.placeId && it.isHome })
        }
    }
}

/** Ev ve konum widget'ının durumu. */
sealed interface HomeLocationState {
    /** Evdesin: ev gösterilir. */
    data class AtHome(val home: WidgetPlaceData, val here: WidgetPlaceData) : HomeLocationState

    /** Evin yakınındasın: ev ana bilgi, altında bulunduğun yer. */
    data class Nearby(val home: WidgetPlaceData, val here: WidgetPlaceData, val distanceKm: Double) : HomeLocationState

    /** Uzaktasın: iki yer yan yana. */
    data class Away(val here: WidgetPlaceData, val home: WidgetPlaceData, val distanceKm: Double) : HomeLocationState

    /** Ev seçilmemiş ya da konum yok: tek yer ve ev ayarlamaya yönlendiren bir not. */
    data class Single(val place: WidgetPlaceData, val hint: Hint) : HomeLocationState

    data object Empty : HomeLocationState

    enum class Hint {
        /** Ev seçilmemiş. */
        NoHome,

        /** Ev var ama konum izni yok. */
        NoLocation,
    }

    companion object {
        /** Saf mantık; test edilir. Karar konumun hata payıyla verilir (bkz. HomeDetection). */
        fun of(device: WeatherSnapshot?, home: WidgetPlaceData?, nearbyRadiusKm: Int): HomeLocationState {
            val here = device?.takeIf { it.isCurrentLocation }?.let { WidgetPlaceData(it, isHome = false) }
            return when {
                here != null && home != null -> {
                    val distance = HomeDetection.distanceKm(here.point, home.point)
                    when (HomeDetection.classify(here.point, here.snapshot.accuracyMeters, home.point, nearbyRadiusKm)) {
                        HomeProximity.AtHome -> AtHome(home, here)
                        HomeProximity.Nearby -> Nearby(home, here, distance)
                        HomeProximity.Away -> Away(here, home, distance)
                    }
                }
                home != null -> Single(home, Hint.NoLocation)
                here != null -> Single(here, Hint.NoHome)
                // İzin yok, Ev yok: yedek şehri göster.
                device != null -> Single(WidgetPlaceData(device, isHome = false), Hint.NoHome)
                else -> Empty
            }
        }
    }
}

/**
 * Widget'ların gösterdiği her şey, önbellekten (internete çıkmadan) akış olarak okunur. Widget bu
 * akışı çizim sırasında dinler: ayar, Ev, birimler ya da hava verisi değişince, widget oturumu
 * açıkken bile kendiliğinden yeniden çizilir.
 */
class WidgetDataLoader(context: Context) {
    private val entryPoint = EntryPointAccessors
        .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    val input: Flow<WidgetInput> = combine(
        entryPoint.settingsRepository().settings,
        entryPoint.placesRepository().places,
        entryPoint.weatherRepository().allWeather,
    ) { settings, places, weather -> WidgetInput(settings, places, weather) }
        .distinctUntilChanged()
}

/** Kayıtlı yerin adı; cihaz konumunda konumdan bulunan yer adı. */
fun WeatherSnapshot.displayName(context: Context): String =
    city.name ?: context.getString(R.string.my_location)
