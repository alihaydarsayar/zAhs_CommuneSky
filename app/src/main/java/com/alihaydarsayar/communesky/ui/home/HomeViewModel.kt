package com.alihaydarsayar.communesky.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.Forecast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Ekranın o anda içinde olabileceği durumlar. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val city: City, val forecast: Forecast) : HomeUiState
    data class Error(val error: LoadError) : HomeUiState
}

/** Hatanın türü; hangi dilde gösterileceğine ekran (strings.xml) karar verir. */
sealed interface LoadError {
    data object Network : LoadError
    data class Server(val code: Int) : LoadError
    data object Unknown : LoadError
}

class HomeViewModel(
    private val repository: WeatherRepository = WeatherRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            val city = City.Istanbul
            _uiState.value = try {
                HomeUiState.Success(city, repository.getForecast(city))
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

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
