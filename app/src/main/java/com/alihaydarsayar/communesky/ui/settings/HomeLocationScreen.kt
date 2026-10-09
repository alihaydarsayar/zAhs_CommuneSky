package com.alihaydarsayar.communesky.ui.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.WeatherUpdater
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.data.toPlace
import com.alihaydarsayar.communesky.di.ApplicationScope
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.HomeAwaySettings
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.HomeProximity
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassRadioRow
import com.alihaydarsayar.communesky.ui.common.GlassSlider
import com.alihaydarsayar.communesky.ui.common.GlassSwitchRow
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeLocationUiState(
    val settings: HomeAwaySettings = HomeAwaySettings(),
    val places: List<SavedPlace> = emptyList(),
    /** Bulunduğun yer (konum izni varsa). */
    val device: WeatherSnapshot? = null,
) {
    val home: SavedPlace? get() = places.firstOrNull { it.isHome }
}

@HiltViewModel
class HomeLocationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val placesRepository: PlacesRepository,
    private val updater: WeatherUpdater,
    weatherRepository: WeatherRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<HomeLocationUiState> = combine(
        settingsRepository.settings,
        placesRepository.places,
        weatherRepository.allWeather,
    ) { settings, places, weather ->
        HomeLocationUiState(settings.homeAway, places, weather[DEVICE_PLACE_ID]?.takeIf { it.isCurrentLocation })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeLocationUiState())

    fun update(transform: (HomeAwaySettings) -> HomeAwaySettings) {
        viewModelScope.launch { settingsRepository.setHomeAway(transform(state.value.settings)) }
    }

    fun setHome(id: Long) {
        viewModelScope.launch { placesRepository.setHome(id) }
    }

    /** Bulunduğun yeri kayıtlı yer olarak ekler ve Ev yapar (konum cihazda kalır). */
    fun makeCurrentLocationHome(fallbackName: String) {
        val device = state.value.device ?: return
        appScope.launch {
            val city = device.city
            val id = placesRepository.add(
                PlaceSearchResult(
                    name = city.name ?: fallbackName,
                    region = city.region,
                    countryCode = null,
                    latitude = city.latitude,
                    longitude = city.longitude,
                ),
            )
            placesRepository.setHome(id)
            placesRepository.all().firstOrNull { it.id == id }?.let { saved ->
                runCatching { updater.refresh(listOf(saved.toPlace())) }
                    .onFailure { Log.w("HomeLocation", "Evin havası indirilemedi", it) }
            }
        }
    }
}

/**
 * Ayarlar > Ev ve konum: ev, yakınlık mesafesi ve uzaktayken widget'ta neler gösterileceği.
 * Evde/yakında/uzakta hesabı telefonda yapılır; konum bunun için hiçbir yere gönderilmez.
 */
@Composable
fun HomeLocationScreen(
    onBack: () -> Unit,
    onManagePlaces: () -> Unit,
    viewModel: HomeLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var choosingHome by remember { mutableStateOf(false) }
    // Kaydırırken liste anında güncellensin; ayar parmak kalkınca kaydedilir.
    var radius by remember(state.settings.nearbyRadiusKm) { mutableFloatStateOf(state.settings.nearbyRadiusKm.toFloat()) }
    val radiusKm = radius.roundToInt()
    val myLocation = stringResource(R.string.my_location)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.home_settings_title), onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.home_settings_home)) {
                    val home = state.home
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(home?.name ?: stringResource(R.string.home_settings_no_home), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                home?.region ?: stringResource(R.string.home_settings_no_home_hint),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_settings_change),
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable(role = Role.Button) {
                                    if (state.places.isEmpty()) onManagePlaces() else choosingHome = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    if (state.device != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button) { viewModel.makeCurrentLocationHome(myLocation) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_settings_use_location), color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.home_settings_radius)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_settings_radius_label), color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.widget_distance_km, radiusKm), color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                    }
                    GlassSlider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = HomeDetection.MIN_NEARBY_KM.toFloat()..HomeDetection.MAX_NEARBY_KM.toFloat(),
                        steps = HomeDetection.MAX_NEARBY_KM - HomeDetection.MIN_NEARBY_KM - 1,
                        onValueChangeFinished = { viewModel.update { it.copy(nearbyRadiusKm = radius.roundToInt()) } },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text(stringResource(R.string.widget_distance_km, HomeDetection.MIN_NEARBY_KM), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.widget_distance_km, 10), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.widget_distance_km, HomeDetection.MAX_NEARBY_KM), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    val home = state.home
                    val others = state.places.filter { !it.isHome }
                    if (home != null && others.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                        Spacer(Modifier.height(4.dp))
                        others.forEach { place ->
                            val distance = HomeDetection.distanceKm(GeoPoint(place.latitude, place.longitude), GeoPoint(home.latitude, home.longitude))
                            val proximity = HomeDetection.classifyDistance(distance, 0.0, radiusKm)
                            PlaceDistanceRow(place.name, distance, proximity)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.home_settings_away)) {
                    val settings = state.settings
                    GlassSwitchRow(stringResource(R.string.home_settings_temperature_difference), settings.showTemperatureDifference, { on ->
                        viewModel.update { it.copy(showTemperatureDifference = on) }
                    })
                    GlassSwitchRow(stringResource(R.string.home_settings_home_rain), settings.showHomeRain, { on ->
                        viewModel.update { it.copy(showHomeRain = on) }
                    })
                    GlassSwitchRow(stringResource(R.string.home_settings_distance), settings.showDistance, { on ->
                        viewModel.update { it.copy(showDistance = on) }
                    })
                    GlassSwitchRow(stringResource(R.string.home_settings_both_nearby), settings.showBothWhenNearby, { on ->
                        viewModel.update { it.copy(showBothWhenNearby = on) }
                    })
                }
            }
            item {
                Text(
                    stringResource(R.string.home_settings_privacy),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (choosingHome) {
        AlertDialog(
            onDismissRequest = { choosingHome = false },
            title = { Text(stringResource(R.string.home_settings_choose_home)) },
            text = {
                Column {
                    state.places.forEach { place ->
                        GlassRadioRow(
                            title = place.name,
                            subtitle = place.region,
                            selected = place.isHome,
                            onClick = {
                                viewModel.setHome(place.id)
                                choosingHome = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingHome = false }) { Text(stringResource(R.string.home_settings_close)) }
            },
            containerColor = Color(0xFF16213F),
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
        )
    }
}

/** "Kadıköy 18 km — Uzakta": yakınlık mesafesi değiştikçe canlı güncellenir. */
@Composable
private fun PlaceDistanceRow(name: String, distanceKm: Double, proximity: HomeProximity) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            if (distanceKm < 1) stringResource(R.string.widget_distance_under_one) else stringResource(R.string.widget_distance_km, distanceKm.roundToInt()),
            color = TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        val (label, fill) = when (proximity) {
            HomeProximity.AtHome -> R.string.home_state_at_home to Color(0xFF86EFAC).copy(alpha = 0.25f)
            HomeProximity.Nearby -> R.string.home_state_nearby to Color(0xFF9CC4FF).copy(alpha = 0.25f)
            HomeProximity.Away -> R.string.home_state_away to Color.White.copy(alpha = 0.10f)
        }
        Text(
            stringResource(label),
            color = TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(CircleShape).background(fill).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
