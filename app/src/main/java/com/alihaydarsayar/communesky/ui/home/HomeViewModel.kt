package com.alihaydarsayar.communesky.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.data.PlaceSelection
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.WeatherUpdater
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.location.LocationRepository
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.work.RefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Ana ekrandaki bir sayfa: "Bulunduğum yer" ya da kayıtlı bir yer. */
data class HomePage(
    val placeId: Long,
    /** Kayıtlı yerin adı; "Bulunduğum yer" için null (ad, hava verisindeki şehir adından gelir). */
    val name: String?,
    val isHome: Boolean,
    val weather: WeatherSnapshot?,
) {
    val isDevice: Boolean get() = placeId == DEVICE_PLACE_ID
}

/** Ekranın tüm durumu. */
data class HomeUiState(
    /** Önbellek henüz okunmadı (uygulama açılışının ilk birkaç milisaniyesi). */
    val isLoading: Boolean = true,
    val pages: List<HomePage> = emptyList(),
    /** En son bakılan yer; uygulama açılınca bu sayfadan başlanır. */
    val selectedPlaceId: Long? = null,
    /** Arka planda yenileniyor (otomatik veya kullanıcı aşağı çekti). */
    val isRefreshing: Boolean = false,
    /** Kullanıcı aşağı çekerek yeniledi; çekme göstergesi sadece bu durumda döner. */
    val isUserRefresh: Boolean = false,
    /** Son yenileme başarısız olduysa nedeni. Eldeki veri gösterilmeye devam eder. */
    val error: LoadError? = null,
    val locationStatus: LocationStatus = LocationStatus.Current,
) {
    /** Seçili sayfa; seçim yoksa ya da o yer silindiyse ilk sayfa. */
    val selectedPage: HomePage?
        get() = pages.firstOrNull { it.placeId == selectedPlaceId } ?: pages.firstOrNull()
}

/** "Bulunduğum yer"in nereden geldiği; ekran buna göre konum düğmesini gösterir. */
sealed interface LocationStatus {
    /** Cihazın konumu kullanılıyor. */
    data object Current : LocationStatus

    /** İzin yok; [canAskAgain] false ise Android artık izin penceresi göstermez, ayarlara gidilmeli. */
    data class NoPermission(val canAskAgain: Boolean) : LocationStatus

    /** İzin var ama konum bulunamadı (ör. telefonda konum kapalı). */
    data object Unavailable : LocationStatus
}

/** Hatanın türü; hangi dilde gösterileceğine ekran (strings.xml) karar verir. */
sealed interface LoadError {
    data object Network : LoadError
    data class Server(val code: Int) : LoadError
    data object Unknown : LoadError
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    weatherRepository: WeatherRepository,
    placesRepository: PlacesRepository,
    private val settingsRepository: SettingsRepository,
    private val updater: WeatherUpdater,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private data class RefreshState(
        val isRefreshing: Boolean = false,
        val isUserRefresh: Boolean = false,
        val error: LoadError? = null,
        val locationStatus: LocationStatus = LocationStatus.Current,
    )

    private val refreshState = MutableStateFlow(RefreshState())
    private val hasPermission = MutableStateFlow(locationRepository.hasPermission())
    private var refreshJob: Job? = null
    private var canAskPermissionAgain = true

    /** Birimler ve tema; uygulamanın her ekranı kullanır. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Kayıtlı yerler + önbellek + izin + yenileme durumu birleşip tek bir ekran durumu olur.
     * Önbellek değişince ekran kendiliğinden güncellenir; ViewModel veriyi elle taşımaz.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        placesRepository.places,
        weatherRepository.allWeather,
        hasPermission,
        refreshState,
        settingsRepository.selectedPlaceId,
    ) { saved, weather, permission, refresh, selectedId ->
        val savedById = saved.associateBy { it.id }
        HomeUiState(
            isLoading = false,
            pages = PlaceSelection.pageIds(permission, saved).map { id ->
                val place = savedById[id]
                HomePage(
                    placeId = id,
                    name = place?.name,
                    isHome = place?.isHome == true,
                    weather = weather[id],
                )
            },
            selectedPlaceId = selectedId,
            isRefreshing = refresh.isRefreshing,
            isUserRefresh = refresh.isUserRefresh,
            error = refresh.error,
            locationStatus = refresh.locationStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
        RefreshScheduler.schedule(application)
    }

    /** Konumu bulur ve bütün yerleri tek istekle yeniler. */
    fun refresh(userInitiated: Boolean = false) {
        // Önceki yenileme bitmeden yenisi başlarsa eskisini iptal et.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = true, isUserRefresh = userInitiated) }
            val resolved = updater.resolveDevicePlace()
            hasPermission.value = resolved.hasPermission
            // İstasyon ölçümü ayrı yürür: tahmin bunu beklemez, ölçüm gelince ekran kendiliğinden güncellenir.
            viewModelScope.launch { updater.refreshObservations(resolved) }
            val locationStatus = when {
                resolved.located -> LocationStatus.Current
                !resolved.hasPermission -> LocationStatus.NoPermission(canAskPermissionAgain)
                else -> LocationStatus.Unavailable
            }
            val error = try {
                updater.refreshAll(resolved)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                LoadError.Network
            } catch (e: HttpException) {
                LoadError.Server(e.code())
            } catch (e: Exception) {
                Log.e(TAG, "Hava durumu alınamadı", e)
                LoadError.Unknown
            }
            refreshState.value = RefreshState(error = error, locationStatus = locationStatus)
        }
    }

    /** Kullanıcı yerler arasında kaydırınca: bir sonraki açılışta bu yerden başlansın. */
    fun onPageSelected(placeId: Long) {
        if (uiState.value.selectedPlaceId == placeId) return
        viewModelScope.launch { settingsRepository.setSelectedPlaceId(placeId) }
    }

    /** İzin penceresinin sonucu. */
    fun onLocationPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        canAskPermissionAgain = canAskAgain
        if (granted) {
            refresh()
        } else {
            refreshState.update { state ->
                if (state.locationStatus is LocationStatus.NoPermission) {
                    state.copy(locationStatus = LocationStatus.NoPermission(canAskAgain))
                } else {
                    state
                }
            }
        }
    }

    /**
     * Uygulamaya geri dönüldüğünde: izin değiştiyse, verisi olmayan bir yer eklendiyse ya da
     * veri eskidiyse yenile. Taze veri varken gereksiz istek atılmaz (pil ve veri tasarrufu).
     */
    fun onResume() {
        val refresh = refreshState.value
        if (refresh.isRefreshing) return
        val permissionChanged = hasPermission.value != locationRepository.hasPermission()
        val pages = uiState.value.pages
        val oldest = pages.minOfOrNull { it.weather?.fetchedAt ?: Instant.EPOCH }
        val isStale = oldest == null || Duration.between(oldest, Instant.now()) > STALE_AFTER
        if (permissionChanged || isStale) {
            refresh()
        } else {
            // Tahmin taze ama anlık ölçüm 3 dakikadan eskiyse yeniden sorulur (kısa ve ucuz bir istek).
            viewModelScope.launch { updater.refreshObservations() }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private val STALE_AFTER: Duration = Duration.ofMinutes(15)
    }
}
