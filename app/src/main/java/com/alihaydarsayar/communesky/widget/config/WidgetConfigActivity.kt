package com.alihaydarsayar.communesky.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassSegmented
import com.alihaydarsayar.communesky.ui.common.LocalSkyIsLight
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.home.placeholderScene
import com.alihaydarsayar.communesky.ui.sky.SkyBackground
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.widget.HomeWidgetReceiver
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import com.alihaydarsayar.communesky.widget.WidgetBackground
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetConfigStore
import com.alihaydarsayar.communesky.widget.WidgetPlace
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Widget ayar ekranı: widget eklenirken açılır, sonradan widget'a uzun basıp "Widget'ı
 * düzenle" ile tekrar açılabilir (Android 12+). Yer ve arka plan seçilir.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var placesRepository: PlacesRepository
    @Inject lateinit var weatherRepository: WeatherRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Kullanıcı kaydetmeden çıkarsa widget eklenmesin (Android'in beklediği davranış).
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Ev + Bulunduğum yer widget'ında yer seçimi yok; sadece arka plan.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        val choosesPlace = provider?.className != HomeWidgetReceiver::class.java.name

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            CommuneSkyTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    choosesPlace = choosesPlace,
                    placesRepository = placesRepository,
                    weatherRepository = weatherRepository,
                    onDone = {
                        setResult(RESULT_OK, resultIntent(appWidgetId))
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WidgetConfigScreen(
    appWidgetId: Int,
    choosesPlace: Boolean,
    placesRepository: PlacesRepository,
    weatherRepository: WeatherRepository,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { WidgetConfigStore.get(context) }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<WidgetConfig?>(null) }
    var places by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }
    var deviceName by remember { mutableStateOf<String?>(null) }
    var scene by remember { mutableStateOf(placeholderScene()) }
    LaunchedEffect(appWidgetId) {
        places = placesRepository.all()
        val device = weatherRepository.snapshot(DEVICE_PLACE_ID)
        deviceName = device?.city?.name
        device?.let { scene = it.currentScene() }
        config = store.get(appWidgetId)
    }
    val current = config ?: return
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalSkyIsLight provides scene.theme.isLight) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(scene, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar(stringResource(R.string.widget_config_title), onCancel)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (choosesPlace) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_place)) {
                                PlaceOption(
                                    title = stringResource(R.string.current_location),
                                    subtitle = deviceName,
                                    icon = R.drawable.ic_location,
                                    selected = current.place == WidgetPlace.Device,
                                    onClick = { config = current.copy(place = WidgetPlace.Device) },
                                )
                                val home = places.firstOrNull { it.isHome }
                                PlaceOption(
                                    title = stringResource(R.string.home_place),
                                    subtitle = home?.name ?: stringResource(R.string.widget_config_no_home),
                                    icon = R.drawable.ic_home,
                                    selected = current.place == WidgetPlace.Home,
                                    onClick = { config = current.copy(place = WidgetPlace.Home) },
                                )
                                places.forEach { place ->
                                    PlaceOption(
                                        title = place.name,
                                        subtitle = place.region,
                                        icon = null,
                                        selected = current.place == WidgetPlace.Saved(place.id),
                                        onClick = { config = current.copy(place = WidgetPlace.Saved(place.id)) },
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.widget_config_home_hint),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_background)) {
                            GlassSegmented(
                                options = listOf(
                                    stringResource(R.string.widget_background_sky),
                                    stringResource(R.string.widget_background_translucent),
                                    stringResource(R.string.widget_background_transparent),
                                ),
                                selectedIndex = current.background.ordinal,
                                onSelect = { config = current.copy(background = WidgetBackground.entries[it]) },
                            )
                            Text(
                                stringResource(
                                    when (current.background) {
                                        WidgetBackground.Sky -> R.string.widget_background_sky_hint
                                        WidgetBackground.Translucent -> R.string.widget_background_translucent_hint
                                        WidgetBackground.Transparent -> R.string.widget_background_transparent_hint
                                    },
                                ),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.widget_config_save),
                    color = Color(0xFF14284A),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 16.dp)
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(role = Role.Button) {
                            scope.launch {
                                store.set(appWidgetId, current)
                                WeatherWidgetUpdater.updateAll(context)
                                onDone()
                            }
                        }
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaceOption(
    title: String,
    subtitle: String?,
    icon: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Seçim işareti: dolu beyaz daire.
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14284A)),
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
