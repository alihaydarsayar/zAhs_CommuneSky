package com.alihaydarsayar.communesky.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.updateAll
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassRadioRow
import com.alihaydarsayar.communesky.ui.common.GlassSegmented
import com.alihaydarsayar.communesky.ui.common.GlassSlider
import com.alihaydarsayar.communesky.ui.common.GlassSwitchRow
import com.alihaydarsayar.communesky.ui.common.LocalSkyIsLight
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.home.placeholderScene
import com.alihaydarsayar.communesky.ui.sky.SkyBackground
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.widget.BackgroundKind
import com.alihaydarsayar.communesky.widget.ColorTheme
import com.alihaydarsayar.communesky.widget.CornerSize
import com.alihaydarsayar.communesky.widget.TextColorMode
import com.alihaydarsayar.communesky.widget.TextSize
import com.alihaydarsayar.communesky.widget.WallpaperTone
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetConfigStore
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetKind
import com.alihaydarsayar.communesky.widget.WidgetPalettes
import com.alihaydarsayar.communesky.widget.WidgetPlace
import com.alihaydarsayar.communesky.widget.WidgetSamples
import com.alihaydarsayar.communesky.widget.WidgetStyleId
import com.alihaydarsayar.communesky.model.SkyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Widget ekleme ekranı: widget eklenirken açılır, sonradan widget'a uzun basıp "Widget'ı
 * düzenle" ile tekrar açılabilir (Android 12+). En üstte canlı önizleme her değişiklikte
 * anında güncellenir: önizleme, ana ekrandaki widget'ın kendisiyle aynı kodla çizilir.
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
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        val kind = WidgetKind.fromReceiver(provider?.className)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            CommuneSkyTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    kind = kind,
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
    kind: WidgetKind,
    placesRepository: PlacesRepository,
    weatherRepository: WeatherRepository,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { WidgetConfigStore.get(context) }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<WidgetConfig?>(null) }
    var isNew by remember { mutableStateOf(true) }
    var places by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }
    var deviceName by remember { mutableStateOf<String?>(null) }
    var scene by remember { mutableStateOf(placeholderScene()) }
    var showSavedPlaces by remember { mutableStateOf(false) }
    // Önizlemeler için veri bir kez okunur; henüz hiç veri yoksa örnek veri.
    var input by remember { mutableStateOf<WidgetInput?>(null) }
    LaunchedEffect(appWidgetId) {
        places = placesRepository.all()
        val device = weatherRepository.snapshot(DEVICE_PLACE_ID)
        deviceName = device?.city?.name
        device?.let { scene = it.currentScene() }
        input = WidgetDataLoader(context).input.first().takeIf { it.weather.isNotEmpty() } ?: WidgetSamples.input()
        isNew = !store.exists(appWidgetId)
        config = store.get(appWidgetId, kind).also { showSavedPlaces = it.place is WidgetPlace.Saved }
    }
    val current = config ?: return
    val previewInput = input ?: return
    val style = current.style
    val styleId = style.styleId(kind)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    fun update(transform: (WidgetConfig) -> WidgetConfig) {
        config = transform(current)
    }

    CompositionLocalProvider(LocalSkyIsLight provides scene.theme.isLight) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(scene, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar(stringResource(R.string.widget_config_title), onCancel)
                LivePreviewBox(kind, current, styleId.previewSize, previewInput)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (kind.styles.size > 1) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_style)) {
                                StylePicker(kind, current, previewInput, onSelect = { id ->
                                    // Stilin kendi görünümü gelir; yer ve içerik seçimleri korunur.
                                    update { it.copy(style = id.defaultStyle().copy(contents = it.style.contents, textSize = it.style.textSize)) }
                                })
                            }
                        }
                    }
                    if (kind.choosesPlace && styleId != WidgetStyleId.ClockHome) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_place)) {
                                val home = places.firstOrNull { it.isHome }
                                GlassRadioRow(
                                    title = stringResource(R.string.widget_place_smart),
                                    subtitle = stringResource(R.string.widget_place_smart_hint),
                                    selected = current.place == WidgetPlace.Smart,
                                    onClick = { showSavedPlaces = false; update { it.copy(place = WidgetPlace.Smart) } },
                                )
                                GlassRadioRow(
                                    title = stringResource(R.string.current_location),
                                    subtitle = deviceName,
                                    icon = R.drawable.ic_location,
                                    selected = current.place == WidgetPlace.Device,
                                    onClick = { showSavedPlaces = false; update { it.copy(place = WidgetPlace.Device) } },
                                )
                                GlassRadioRow(
                                    title = stringResource(R.string.home_place),
                                    subtitle = home?.name ?: stringResource(R.string.widget_config_no_home),
                                    icon = R.drawable.ic_home,
                                    selected = current.place == WidgetPlace.Home,
                                    onClick = { showSavedPlaces = false; update { it.copy(place = WidgetPlace.Home) } },
                                )
                                if (places.isNotEmpty()) {
                                    GlassRadioRow(
                                        title = stringResource(R.string.widget_place_saved),
                                        selected = current.place is WidgetPlace.Saved || showSavedPlaces,
                                        onClick = { showSavedPlaces = true },
                                    )
                                    if (showSavedPlaces || current.place is WidgetPlace.Saved) {
                                        places.forEach { place ->
                                            GlassRadioRow(
                                                title = place.name,
                                                subtitle = place.region,
                                                selected = current.place == WidgetPlace.Saved(place.id),
                                                onClick = { update { it.copy(place = WidgetPlace.Saved(place.id)) } },
                                                modifier = Modifier.padding(start = 24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_background)) {
                            GlassSegmented(
                                options = BackgroundKind.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.background.ordinal,
                                onSelect = { index ->
                                    val background = BackgroundKind.entries[index]
                                    update { it.copy(style = it.style.copy(background = background, transparency = background.defaultTransparency)) }
                                },
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(stringResource(R.string.widget_config_transparency), color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.widget_percent, style.transparency), color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                            }
                            GlassSlider(
                                value = style.transparency.toFloat(),
                                onValueChange = { value -> update { it.copy(style = it.style.copy(transparency = (value / 5).roundToInt() * 5)) } },
                                valueRange = 0f..100f,
                            )
                        }
                    }
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_colors)) {
                            ThemePicker(style.colorTheme, scene.theme) { theme -> update { it.copy(style = it.style.copy(colorTheme = theme)) } }
                            Column {
                                Spacer(Modifier.height(16.dp))
                                Label(stringResource(R.string.widget_config_text_color))
                                GlassSegmented(
                                    options = TextColorMode.entries.map { stringResource(it.labelRes) },
                                    selectedIndex = style.textColor.ordinal,
                                    onSelect = { i -> update { it.copy(style = it.style.copy(textColor = TextColorMode.entries[i])) } },
                                )
                                Spacer(Modifier.height(16.dp))
                                Label(stringResource(R.string.widget_config_corners))
                                GlassSegmented(
                                    options = CornerSize.entries.map { stringResource(it.labelRes) },
                                    selectedIndex = style.corners.ordinal,
                                    onSelect = { i -> update { it.copy(style = it.style.copy(corners = CornerSize.entries[i])) } },
                                )
                                Spacer(Modifier.height(16.dp))
                                Label(stringResource(R.string.widget_config_text_size))
                                GlassSegmented(
                                    options = TextSize.entries.map { stringResource(it.labelRes) },
                                    selectedIndex = style.textSize.ordinal,
                                    onSelect = { i -> update { it.copy(style = it.style.copy(textSize = TextSize.entries[i])) } },
                                )
                            }
                        }
                    }
                    if (kind.contents.isNotEmpty()) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_contents)) {
                                val enabled = style.contents ?: kind.defaultContents
                                kind.contents.forEach { content ->
                                    GlassSwitchRow(
                                        title = stringResource(content.labelRes),
                                        checked = content in enabled,
                                        onCheckedChange = { on ->
                                            update { it.copy(style = it.style.copy(contents = if (on) enabled + content else enabled - content)) }
                                        },
                                    )
                                }
                                if (kind == WidgetKind.HomeLocation) {
                                    Text(
                                        stringResource(R.string.widget_config_home_hint),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(if (isNew) R.string.widget_config_add else R.string.widget_config_save),
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
                                kind.widget().updateAll(context)
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
private fun Label(text: String) {
    Text(text, color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

/** En üstte canlı önizleme; arkada duvar kâğıdının renkleri (bilinmiyorsa yeşil-gri). */
@Composable
private fun LivePreviewBox(kind: WidgetKind, config: WidgetConfig, size: DpSize, input: WidgetInput) {
    val context = LocalContext.current
    val wallpaper = remember { WallpaperTone.colors(context) ?: listOf(Color(0xFF44564F), Color(0xFF44564F)) }
    val available = LocalConfiguration.current.screenWidthDp - 32 - 32
    val scale = min(1f, available / size.width.value)
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(wallpaper))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        WidgetRender(kind, config, size, scale, input)
    }
}

/** Seçilen widget türünün stilleri, yatay kaydırmalı küçük önizlemeler. */
@Composable
private fun StylePicker(kind: WidgetKind, config: WidgetConfig, input: WidgetInput, onSelect: (WidgetStyleId) -> Unit) {
    val selected = config.style.styleId(kind)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(kind.styles) { id ->
            val thumbHeight = 84.dp
            val scale = min(thumbHeight.value / id.previewSize.height.value, 150f / id.previewSize.width.value)
            val name = stringResource(id.nameRes)
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .selectable(selected = id == selected, role = Role.RadioButton, onClick = { onSelect(id) })
                    .border(
                        width = if (id == selected) 2.dp else 1.dp,
                        color = if (id == selected) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .background(Color.Black.copy(alpha = 0.12f))
                    .padding(10.dp)
                    .semantics { contentDescription = name },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(150.dp, thumbHeight), contentAlignment = Alignment.Center) {
                    // Küçük önizlemeler stilin kendi görünümüyle; kullanıcının içerik seçimleri korunur.
                    val thumbConfig = config.copy(style = id.defaultStyle().copy(contents = config.style.contents))
                    WidgetRender(kind, thumbConfig, id.previewSize, scale, input)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    name,
                    color = if (id == selected) TextPrimary else TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(150.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Renk teması seçimi: her tema küçük bir renk örneğiyle. */
@Composable
private fun ThemePicker(selected: ColorTheme, sky: SkyTheme, onSelect: (ColorTheme) -> Unit) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ColorTheme.entries) { theme ->
            val palette = remember(theme, sky) { WidgetPalettes.of(context, theme, sky) }
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = theme == selected, role = Role.RadioButton, onClick = { onSelect(theme) })
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(palette.gradient))
                        .border(if (theme == selected) 3.dp else 1.dp, if (theme == selected) Color.White else Color.White.copy(alpha = 0.25f), CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(theme.labelRes),
                    color = if (theme == selected) TextPrimary else TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Widget'ı, ana ekrandakiyle aynı kodla (Glance → RemoteViews) çizer ve [scale] ile küçültür.
 * Ayar her değiştiğinde kısa bir beklemeden sonra yeniden çizilir.
 */
@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetRender(kind: WidgetKind, config: WidgetConfig, size: DpSize, scale: Float, input: WidgetInput) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(kind, config, size) {
        delay(80)
        views = runCatching { kind.widget(config, input).compose(context, size = size) }.getOrNull()
    }
    Box(Modifier.size(size.width * scale, size.height * scale), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { frame ->
                val remote = views ?: return@AndroidView
                frame.removeAllViews()
                runCatching { remote.apply(frame.context, frame) }.getOrNull()?.let(frame::addView)
            },
            modifier = Modifier
                .requiredSize(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
