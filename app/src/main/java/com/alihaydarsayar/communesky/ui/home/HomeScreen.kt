package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import com.alihaydarsayar.communesky.ui.theme.SkyDarkColors
import com.alihaydarsayar.communesky.ui.theme.SkyLightColors
import kotlin.math.roundToInt

/** ViewModel'e bağlı ekran: durumu dinler ve çizilecek içeriğe aktarır. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onRetry = viewModel::refresh)
}

/** Sadece durumu çizen ekran; ViewModel'den bağımsız olduğu için Preview'da da görülebilir. */
@Composable
fun HomeContent(uiState: HomeUiState, onRetry: () -> Unit) {
    // Geçici: sistemin açık/koyu temasına göre seçiliyor; 4. adımda havaya ve saate göre değişecek.
    val skyColors = if (isSystemInDarkTheme()) SkyDarkColors else SkyLightColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(skyColors))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            HomeUiState.Loading -> CircularProgressIndicator(color = Color.White)
            is HomeUiState.Error -> ErrorContent(uiState.error, onRetry)
            is HomeUiState.Success -> CurrentWeatherContent(uiState.city, uiState.weather)
        }
    }
}

@Composable
private fun CurrentWeatherContent(city: City, weather: CurrentWeather) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = city.name,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.temperature_value, weather.temperature.roundToInt()),
            color = Color.White,
            fontSize = 112.sp,
            fontWeight = FontWeight.Thin,
        )
        Text(
            text = stringResource(weatherDescriptionRes(weather.weatherCode)),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.feels_like, weather.apparentTemperature.roundToInt()),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            WeatherDetail(
                label = stringResource(R.string.humidity),
                value = stringResource(R.string.humidity_value, weather.humidity),
            )
            WeatherDetail(
                label = stringResource(R.string.wind),
                value = stringResource(R.string.wind_speed_value, weather.windSpeed.roundToInt()),
            )
        }
    }
}

@Composable
private fun WeatherDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ErrorContent(error: LoadError, onRetry: () -> Unit) {
    val message = when (error) {
        LoadError.Network -> stringResource(R.string.error_network)
        is LoadError.Server -> stringResource(R.string.error_server, error.code)
        LoadError.Unknown -> stringResource(R.string.error_unknown)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

private val PreviewState = HomeUiState.Success(
    city = City.Istanbul,
    weather = CurrentWeather(
        temperature = 18.4,
        apparentTemperature = 17.1,
        humidity = 64,
        windSpeed = 12.3,
        weatherCode = 2,
        isDay = true,
    ),
)

/** Açık ve koyu temayı yan yana gösterir. */
@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    CommuneSkyTheme {
        HomeContent(uiState = PreviewState, onRetry = {})
    }
}

/** Türkçe metinleri gösterir. */
@Preview(locale = "tr")
@Composable
private fun HomeContentTurkishPreview() {
    CommuneSkyTheme {
        HomeContent(uiState = PreviewState, onRetry = {})
    }
}
