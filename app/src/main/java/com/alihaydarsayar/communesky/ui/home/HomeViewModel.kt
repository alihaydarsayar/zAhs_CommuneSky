package com.alihaydarsayar.communesky.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.location.LocationRepository
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.Forecast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Ekranın o anda içinde olabileceği durumlar. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val city: City,
        val forecast: Forecast,
        val locationStatus: LocationStatus,
    ) : HomeUiState

    data class Error(val error: LoadError) : HomeUiState
}

/** Gösterilen şehrin nereden geldiği; ekran buna göre konum düğmesini gösterir. */
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

class HomeViewModel(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var canAskPermissionAgain = true
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // Önceki yükleme bitmeden yenisi başlarsa eskisini iptal et; ekrana eski sonuç yazılmasın.
        loadJob?.cancel()
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            val currentCity = locationRepository.getCurrentCity()
            val locationStatus = when {
                currentCity != null -> LocationStatus.Current
                !locationRepository.hasPermission() -> LocationStatus.NoPermission(canAskPermissionAgain)
                else -> LocationStatus.Unavailable
            }
            // Konum yoksa İstanbul'u göster; kullanıcı boş ekranla karşılaşmasın.
            val city = currentCity ?: City.Istanbul
            _uiState.value = try {
                HomeUiState.Success(city, weatherRepository.getForecast(city), locationStatus)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                HomeUiState.Error(LoadError.Network)
            } catch (e: HttpException) {
                HomeUiState.Error(LoadError.Server(e.code()))
            } catch (e: Exception) {
                Log.e(TAG, "Hava durumu alınamadı", e)
                HomeUiState.Error(LoadError.Unknown)
            }
        }
    }

    /** İzin penceresinin sonucu. */
    fun onLocationPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        canAskPermissionAgain = canAskAgain
        if (granted) {
            refresh()
        } else {
            _uiState.update { state ->
                if (state is HomeUiState.Success) {
                    state.copy(locationStatus = LocationStatus.NoPermission(canAskAgain))
                } else {
                    state
                }
            }
        }
    }

    /** Uygulamaya geri dönüldüğünde: kullanıcı izni ayarlardan vermişse konumla yeniden yükle. */
    fun onResume() {
        val state = _uiState.value
        if (state is HomeUiState.Success &&
            state.locationStatus is LocationStatus.NoPermission &&
            locationRepository.hasPermission()
        ) {
            refresh()
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"

        /** ViewModel'i bağımlılıklarıyla oluşturur. 5. adımda bu işi Hilt üstlenecek. */
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    weatherRepository = WeatherRepository(),
                    locationRepository = LocationRepository(checkNotNull(this[APPLICATION_KEY])),
                )
            }
        }
    }
}
