package com.alihaydarsayar.communesky.ui.places

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.WeatherUpdater
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.location.LocationRepository
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.search.PlaceSearchRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.data.toPlace
import com.alihaydarsayar.communesky.di.ApplicationScope
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import com.alihaydarsayar.communesky.model.SavedPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Ekranda bir kez gösterilecek bildirimler (alt çubukta kısa mesaj). */
sealed interface PlacesEvent {
    data class Added(val name: String) : PlacesEvent
    data class Removed(val place: SavedPlace) : PlacesEvent
}

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val searchRepository: PlaceSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val updater: WeatherUpdater,
    weatherRepository: WeatherRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow.asStateFlow()

    /** Arama dili ekrandan gelir (uygulama dili sistemden farklı olabilir). */
    private var locale: Locale = Locale.getDefault()

    val searchState: StateFlow<SearchState> = queryFlow
        .searchAsYouType { query -> searchRepository.search(query, locale, anchors()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    val places: StateFlow<List<SavedPlace>> = placesRepository.places
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val deviceLocation: StateFlow<GeoPoint?> = weatherRepository.allWeather
        .map { all -> all[DEVICE_PLACE_ID]?.takeIf { it.isCurrentLocation }?.city?.let { GeoPoint(it.latitude, it.longitude) } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "Bulunduğum yer" satırında gösterilecek ad. */
    val deviceName: StateFlow<String?> = weatherRepository.allWeather
        .map { it[DEVICE_PLACE_ID]?.takeIf { snapshot -> snapshot.isCurrentLocation }?.city?.name }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val hasPermissionFlow = MutableStateFlow(locationRepository.hasPermission())
    val hasPermission: StateFlow<Boolean> = hasPermissionFlow.asStateFlow()

    private val events = Channel<PlacesEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PlacesEvent> = events.receiveAsFlow()

    /**
     * Sonuçları sıralarken referans alınan noktalar: son bilinen konum ve kayıtlı yerler.
     * Sadece telefonda karşılaştırma için kullanılır; arama servisine gönderilmez.
     */
    private fun anchors(): List<GeoPoint> =
        listOfNotNull(deviceLocation.value) + places.value.map { GeoPoint(it.latitude, it.longitude) }

    fun setLocale(locale: Locale) {
        this.locale = locale
    }

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    /**
     * Yeri kaydeder ve hemen havasını indirir. İndirme uygulama kapsamında çalışır; kullanıcı
     * ekrandan çıksa da yarıda kalmaz, ana ekrana dönünce veri hazır olur.
     */
    fun add(result: PlaceSearchResult) {
        queryFlow.value = ""
        appScope.launch {
            val id = placesRepository.add(result)
            events.send(PlacesEvent.Added(result.name))
            placesRepository.all().firstOrNull { it.id == id }?.let { saved ->
                runCatching { updater.refresh(listOf(saved.toPlace())) }
                    .onFailure { Log.w(TAG, "Yeni yerin havası indirilemedi", it) }
            }
        }
    }

    fun remove(place: SavedPlace) {
        viewModelScope.launch {
            placesRepository.remove(place.id)?.let { events.send(PlacesEvent.Removed(it)) }
        }
    }

    fun undoRemove(place: SavedPlace) {
        appScope.launch {
            placesRepository.restore(place)
            runCatching { updater.refresh(listOf(place.toPlace())) }
                .onFailure { Log.w(TAG, "Geri alınan yerin havası indirilemedi", it) }
        }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { placesRepository.reorder(ids) }
    }

    fun toggleHome(place: SavedPlace) {
        viewModelScope.launch { placesRepository.setHome(if (place.isHome) null else place.id) }
    }

    /** Ana ekranda bu yerden devam edilsin. */
    fun select(id: Long) {
        viewModelScope.launch { settingsRepository.setSelectedPlaceId(id) }
    }

    fun onResume() {
        hasPermissionFlow.value = locationRepository.hasPermission()
    }

    private companion object {
        const val TAG = "PlacesViewModel"
    }
}
