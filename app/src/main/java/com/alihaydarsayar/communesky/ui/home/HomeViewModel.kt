package com.alihaydarsayar.communesky.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.WeatherUpdater
import com.alihaydarsayar.communesky.data.location.LocationRepository
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

/** Ekranın tüm durumu. */
data class HomeUiState(
    /** Önbellek henüz okunmadı (uygulama açılışının ilk birkaç milisaniyesi). */
    val isCacheLoading: Boolean = true,
    val weather: WeatherSnapshot? = null,
    /** Arka planda yenileniyor (otomatik veya kullanıcı aşağı çekti). */
    val isRefreshing: Boolean = false,
    /** Kullanıcı aşağı çekerek yeniledi; çekme göstergesi sadece bu durumda döner. */
    val isUserRefresh: Boolean = false,
    /** Son yenileme başarısız olduysa nedeni. Eldeki veri gösterilmeye devam eder. */
    val error: LoadError? = null,
    val locationStatus: LocationStatus = LocationStatus.Current,
)

/** Gösterilen yerin nereden geldiği; ekran buna göre konum düğmesini gösterir. */
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
    private var refreshJob: Job? = null
    private var canAskPermissionAgain = true

    /**
     * Önbellek (Room) + yenileme durumu birleşip tek bir ekran durumu olur.
     * Önbellek değişince ekran kendiliğinden güncellenir; ViewModel veriyi elle taşımaz.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        weatherRepository.weather,
        refreshState,
    ) { weather, refresh ->
        HomeUiState(
            isCacheLoading = false,
            weather = weather,
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

    fun refresh(userInitiated: Boolean = false) {
        // Önceki yenileme bitmeden yenisi başlarsa eskisini iptal et.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = true, isUserRefresh = userInitiated) }
            val resolved = updater.resolvePlace()
            val locationStatus = when {
                resolved.located -> LocationStatus.Current
                !resolved.hasPermission -> LocationStatus.NoPermission(canAskPermissionAgain)
                else -> LocationStatus.Unavailable
            }
            val error = try {
                updater.refresh(resolved.place)
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
     * Uygulamaya geri dönüldüğünde: kullanıcı izni ayarlardan verdiyse ya da veri eskidiyse yenile.
     * Taze veri varken gereksiz istek atılmaz (pil ve veri tasarrufu).
     */
    fun onResume() {
        val refresh = refreshState.value
        if (refresh.isRefreshing) return
        val permissionJustGranted = refresh.locationStatus is LocationStatus.NoPermission &&
            locationRepository.hasPermission()
        val fetchedAt = uiState.value.weather?.fetchedAt
        val isStale = fetchedAt == null ||
            Duration.between(fetchedAt, Instant.now()) > STALE_AFTER
        if (permissionJustGranted || isStale) refresh()
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private val STALE_AFTER: Duration = Duration.ofMinutes(15)
    }
}
