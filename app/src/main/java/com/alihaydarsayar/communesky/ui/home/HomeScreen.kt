package com.alihaydarsayar.communesky.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import com.alihaydarsayar.communesky.ui.theme.SkyDarkColors
import com.alihaydarsayar.communesky.ui.theme.SkyLightColors
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** ViewModel'e bağlı ekran: durumu dinler ve çizilecek içeriğe aktarır. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Android'in standart izin penceresini açar ve sonucu ViewModel'e iletir.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Kullanıcı iki kez reddederse Android pencereyi bir daha göstermez; bunu buradan anlarız.
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, LocationPermission)
        viewModel.onLocationPermissionResult(granted, canAskAgain)
    }

    // Uygulama açıldığında izin yoksa bir kez sor. rememberSaveable sayesinde ekran dönünce tekrar sormaz.
    var askedOnLaunch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, LocationPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (!askedOnLaunch && !granted) {
            askedOnLaunch = true
            permissionLauncher.launch(LocationPermission)
        }
    }

    // Ayarlar'dan izin verip geri dönülürse ViewModel konumla yeniden yükler.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    HomeContent(
        uiState = uiState,
        onRetry = viewModel::refresh,
        onLocationAction = { status ->
            when (status) {
                is LocationStatus.NoPermission ->
                    if (status.canAskAgain) {
                        permissionLauncher.launch(LocationPermission)
                    } else {
                        context.startActivity(appSettingsIntent(context))
                    }
                LocationStatus.Unavailable -> viewModel.refresh()
                LocationStatus.Current -> Unit
            }
        },
    )
}

private const val LocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

/** Telefon ayarlarında bu uygulamanın sayfasını açar (izinler oradan verilebilir). */
private fun appSettingsIntent(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

/** Sadece durumu çizen ekran; ViewModel'den bağımsız olduğu için Preview'da da görülebilir. */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onLocationAction: (LocationStatus) -> Unit,
) {
    // Geçici: sistemin açık/koyu temasına göre seçiliyor; 4. adımda havaya ve saate göre değişecek.
    val skyColors = if (isSystemInDarkTheme()) SkyDarkColors else SkyLightColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(skyColors)),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            HomeUiState.Loading -> CircularProgressIndicator(color = Color.White)
            is HomeUiState.Error -> ErrorContent(uiState.error, onRetry)
            is HomeUiState.Success -> ForecastContent(uiState, onLocationAction)
        }
    }
}

@Composable
private fun ForecastContent(
    state: HomeUiState.Success,
    onLocationAction: (LocationStatus) -> Unit,
) {
    val forecast = state.forecast
    // İçerik durum çubuğunun altından kayabilsin diye boşluğu Modifier yerine contentPadding ile veriyoruz.
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 48.dp,
            bottom = insets.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            CurrentHeader(
                city = state.city,
                weather = forecast.current,
                today = forecast.daily.firstOrNull(),
                locationStatus = state.locationStatus,
                onLocationAction = onLocationAction,
            )
        }
        item(key = "hourly") { HourlyForecastCard(forecast.current, forecast.hourly) }
        item(key = "daily") { DailyForecastCard(forecast.daily) }
        item(key = "details") { DetailsCard(forecast.current) }
    }
}

@Composable
private fun CurrentHeader(
    city: City,
    weather: CurrentWeather,
    today: DailyForecast?,
    locationStatus: LocationStatus,
    onLocationAction: (LocationStatus) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (locationStatus == LocationStatus.Current) {
                Icon(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = stringResource(R.string.current_location),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = city.name ?: stringResource(R.string.my_location),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
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
        if (today != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.high_low,
                    today.maxTemperature.roundToInt(),
                    today.minTemperature.roundToInt(),
                ),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (locationStatus != LocationStatus.Current) {
            Spacer(Modifier.height(16.dp))
            LocationChip(locationStatus, onClick = { onLocationAction(locationStatus) })
        }
    }
}

/** Konum kullanılamadığında ne yapılabileceğini söyleyen küçük, tıklanabilir hap düğme. */
@Composable
private fun LocationChip(status: LocationStatus, onClick: () -> Unit) {
    val text = when (status) {
        is LocationStatus.NoPermission -> if (status.canAskAgain) {
            stringResource(R.string.use_my_location)
        } else {
            stringResource(R.string.location_permission_in_settings)
        }
        LocationStatus.Unavailable -> stringResource(R.string.location_unavailable)
        LocationStatus.Current -> return
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ErrorContent(error: LoadError, onRetry: () -> Unit) {
    val message = when (error) {
        LoadError.Network -> stringResource(R.string.error_network)
        is LoadError.Server -> stringResource(R.string.error_server, error.code)
        LoadError.Unknown -> stringResource(R.string.error_unknown)
    }
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

private val PreviewState = run {
    val now = LocalDateTime.of(2026, 10, 7, 14, 0)
    val today = LocalDate.of(2026, 10, 7)
    HomeUiState.Success(
        city = City.Istanbul,
        forecast = Forecast(
            current = CurrentWeather(
                temperature = 18.4,
                apparentTemperature = 17.1,
                humidity = 64,
                windSpeed = 12.3,
                weatherCode = 2,
                isDay = true,
            ),
            hourly = List(24) { i ->
                HourlyForecast(
                    time = now.plusHours(i.toLong()),
                    temperature = 18.0 - i % 12,
                    weatherCode = listOf(0, 2, 3, 61)[i % 4],
                    precipitationProbability = (i * 7) % 60,
                    isDay = i < 5,
                )
            },
            daily = List(7) { i ->
                DailyForecast(
                    date = today.plusDays(i.toLong()),
                    weatherCode = listOf(2, 45, 0, 0, 3, 80, 95)[i],
                    minTemperature = 12.0 + i,
                    maxTemperature = 20.0 + i % 3 * 2,
                    precipitationProbability = listOf(0, 0, 5, 10, 25, 60, 80)[i],
                )
            },
        ),
        locationStatus = LocationStatus.Current,
    )
}

/** Açık ve koyu temayı yan yana gösterir. */
@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    CommuneSkyTheme {
        HomeContent(uiState = PreviewState, onRetry = {}, onLocationAction = {})
    }
}

/** Türkçe metinleri gösterir. */
@Preview(locale = "tr", heightDp = 1100)
@Composable
private fun HomeContentTurkishPreview() {
    CommuneSkyTheme {
        HomeContent(
            uiState = PreviewState.copy(locationStatus = LocationStatus.NoPermission(canAskAgain = true)),
            onRetry = {},
            onLocationAction = {},
        )
    }
}
