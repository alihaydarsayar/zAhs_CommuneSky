package com.alihaydarsayar.communesky.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.SkyTheme
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
import com.alihaydarsayar.communesky.ui.theme.WarningAccent
import com.alihaydarsayar.communesky.widget.BackgroundKind
import com.alihaydarsayar.communesky.widget.ClockFont
import com.alihaydarsayar.communesky.widget.ColorTheme
import com.alihaydarsayar.communesky.widget.Contrast
import com.alihaydarsayar.communesky.widget.CornerSize
import com.alihaydarsayar.communesky.widget.InfoRow
import com.alihaydarsayar.communesky.widget.Legibility
import com.alihaydarsayar.communesky.widget.RadialSeconds
import com.alihaydarsayar.communesky.widget.SecondEffect
import com.alihaydarsayar.communesky.widget.SecondSymbol
import com.alihaydarsayar.communesky.widget.SsClock
import com.alihaydarsayar.communesky.widget.TextColorMode
import com.alihaydarsayar.communesky.widget.TextSize
import com.alihaydarsayar.communesky.widget.WallpaperTone
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import com.alihaydarsayar.communesky.widget.WeightChoice
import com.alihaydarsayar.communesky.widget.WidgetColors
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetConfigStore
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetInk
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetKind
import com.alihaydarsayar.communesky.widget.WidgetPalettes
import com.alihaydarsayar.communesky.widget.WidgetPlace
import com.alihaydarsayar.communesky.widget.WidgetSamples
import com.alihaydarsayar.communesky.widget.WidgetStyle
import com.alihaydarsayar.communesky.widget.WidgetStyleId
import com.alihaydarsayar.communesky.widget.radialSeconds
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Widget ayar ekranı: widget eklenirken açılır; sonradan widget'a uzun basıp "Widget'ı düzenle"
 * ile (Android 12+) ya da uygulamada Ayarlar > Widget'larım'dan tekrar açılabilir. En üstte canlı
 * önizleme her değişiklikte güncellenir: önizleme, ana ekrandaki widget'ın kendisiyle aynı kodla
 * ve aynı boyutta çizilir.
 *
 * Bölümler her widget'ta aynı sırada: Stil, Yer, Arka plan ve saydamlık, Renkler, Yazı, İçerik.
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
    var saving by remember { mutableStateOf(false) }
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
        input = WidgetDataLoader(context).input.first().takeIf { it.weather.isNotEmpty() } ?: WidgetSamples.input(context)
        isNew = !store.exists(appWidgetId)
        // Yeni widget: aynı türden en son kaydedilen ayarla açılır (silip yeniden ekleyince ayarlar geri gelir).
        config = store.initial(appWidgetId, kind).also { showSavedPlaces = it.place is WidgetPlace.Saved }
    }
    val current = config ?: return
    val previewInput = input ?: return
    val style = current.style
    val styleId = style.styleId(kind)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    fun update(transform: (WidgetConfig) -> WidgetConfig) {
        config = transform(current)
    }
    fun restyle(transform: (WidgetStyle) -> WidgetStyle) = update { it.copy(style = transform(it.style)) }

    // Önizleme, widget'ın ana ekrandaki gerçek boyutuyla; yeni eklenirken seçicideki hedef boyutla.
    val previewSize = remember(appWidgetId, styleId) { WidgetSizes.preview(context, kind, appWidgetId, styleId.previewSize) }
    val wallpaper = remember { WallpaperTone.colors(context) }
    val colors = remember(style, scene) { WidgetColors.resolve(context, style, scene.theme) }
    val isClock = kind == WidgetKind.Clock
    val isRadial = styleId == WidgetStyleId.ClockRadial
    val isSs = styleId == WidgetStyleId.ClockSS

    CompositionLocalProvider(LocalSkyIsLight provides scene.theme.isLight) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(scene, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar(stringResource(R.string.widget_config_title), onCancel)
                LivePreviewBox(kind, current, previewSize, previewInput, wallpaper)
                ContrastWarning(style, colors, wallpaper) { restyle { it.copy(legibility = Legibility.Scrim) } }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // --- Stil
                    if (kind.styles.size > 1) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_style)) {
                                // Stil değişince kullanıcının seçtiği her şey korunur; sadece stil değişir.
                                StylePicker(kind, current, previewInput, onSelect = { id -> restyle { it.withStyle(id) } })
                            }
                        }
                    }
                    // --- Yer
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
                    // --- Arka plan ve saydamlık
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_background)) {
                            GlassSegmented(
                                options = BackgroundKind.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.background.ordinal,
                                // Saydamlık elle ayarlandıysa korunur.
                                onSelect = { index -> restyle { it.withBackground(BackgroundKind.entries[index]) } },
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(stringResource(R.string.widget_config_transparency), color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.widget_percent, style.effectiveTransparency), color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                            }
                            GlassSlider(
                                value = style.effectiveTransparency.toFloat(),
                                onValueChange = { value -> restyle { it.withTransparency((value / 5).roundToInt() * 5) } },
                                valueRange = 0f..style.background.maxTransparency.toFloat(),
                            )
                            if (style.background == BackgroundKind.Glass) Note(stringResource(R.string.widget_config_glass_hint))
                            Spacer(Modifier.height(12.dp))
                            Label(stringResource(R.string.widget_config_corners))
                            GlassSegmented(
                                options = CornerSize.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.corners.ordinal,
                                onSelect = { i -> restyle { it.copy(corners = CornerSize.entries[i]) } },
                            )
                            Spacer(Modifier.height(16.dp))
                            Label(stringResource(R.string.widget_config_legibility))
                            GlassSegmented(
                                options = Legibility.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.legibility.ordinal,
                                onSelect = { i -> restyle { it.copy(legibility = Legibility.entries[i]) } },
                            )
                            Note(stringResource(R.string.widget_config_legibility_hint))
                        }
                    }
                    // --- Renkler
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_colors)) {
                            ThemePicker(style, scene.theme) { theme -> restyle { it.withColorTheme(theme) } }
                            if (style.colorTheme == ColorTheme.Custom) {
                                Spacer(Modifier.height(8.dp))
                                ColorRow(stringResource(R.string.widget_color_background), style.customBackground, WidgetInk.Ground) { c -> restyle { it.copy(customBackground = c) } }
                                ColorRow(stringResource(R.string.widget_color_text), style.customText, colors.text) { c -> restyle { it.copy(customText = c) } }
                                ColorRow(stringResource(R.string.widget_color_secondary), style.customSecondary, colors.secondary.copy(alpha = 1f)) { c -> restyle { it.copy(customSecondary = c) } }
                            }
                            if (isSs) {
                                // SS saati: hazır üç tema ve kadranın dört rengi.
                                Spacer(Modifier.height(12.dp))
                                Label(stringResource(R.string.widget_config_ss_looks))
                                GlassSegmented(
                                    options = SsClock.Preset.entries.map { stringResource(it.labelRes) },
                                    selectedIndex = SsClock.preset(style)?.ordinal ?: -1,
                                    onSelect = { i -> restyle { SsClock.apply(it, SsClock.Preset.entries[i]) } },
                                )
                                Spacer(Modifier.height(8.dp))
                                val look = SsClock.Preset.Classic.look
                                ColorRow(stringResource(R.string.widget_color_dial), style.dialColor, Color(look.dial)) { c -> restyle { it.copy(dialColor = c) } }
                                ColorRow(stringResource(R.string.widget_color_ink), style.numeralColor, Color(look.ink)) { c -> restyle { it.copy(numeralColor = c) } }
                                ColorRow(stringResource(R.string.widget_color_window), style.windowColor, Color(look.window)) { c -> restyle { it.copy(windowColor = c) } }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Note(stringResource(R.string.widget_config_hands_old_android))
                            }
                            ColorRow(
                                stringResource(R.string.widget_color_accent), style.accent,
                                if (isSs) Color(SsClock.Preset.Classic.look.accent) else WidgetInk.Accent,
                            ) { c -> restyle { it.copy(accent = c) } }
                            if (isClock && !styleId.isAnalog) {
                                ColorRow(stringResource(R.string.widget_color_hours), style.hourColor, colors.text) { c -> restyle { it.copy(hourColor = c) } }
                                ColorRow(stringResource(R.string.widget_color_minutes), style.minuteColor, style.hourColor?.let { Color(it) } ?: colors.text) { c -> restyle { it.copy(minuteColor = c) } }
                                Note(stringResource(R.string.widget_config_digit_color_hint))
                            }
                            if (isRadial) {
                                ColorRow(stringResource(R.string.widget_color_lines), style.lineColor, colors.text) { c -> restyle { it.copy(lineColor = c) } }
                                ColorRow(stringResource(R.string.widget_color_pill), style.pillColor, colors.text) { c -> restyle { it.copy(pillColor = c) } }
                            }
                            if (styleId.isAnalog && !isSs) {
                                val lightDial = styleId == WidgetStyleId.ClockAnalog
                                val dial = if (lightDial) Color(0xFFF4F2EC) else Color(0xFF141414)
                                val ink = if (lightDial) WidgetInk.Dark else WidgetInk.Light
                                ColorRow(stringResource(R.string.widget_color_dial), style.dialColor, dial) { c -> restyle { it.copy(dialColor = c) } }
                                ColorRow(
                                    stringResource(if (styleId == WidgetStyleId.ClockField) R.string.widget_color_numerals else R.string.widget_color_marks),
                                    style.numeralColor, ink,
                                ) { c -> restyle { it.copy(numeralColor = c) } }
                                ColorRow(stringResource(R.string.widget_color_hands), style.handColor, ink) { c -> restyle { it.copy(handColor = c) } }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Note(stringResource(R.string.widget_config_hands_old_android))
                            }
                            if (styleId == WidgetStyleId.ClockRing) {
                                ColorRow(stringResource(R.string.widget_color_ring_cool), style.ringCool, WidgetInk.Cool) { c -> restyle { it.copy(ringCool = c) } }
                                ColorRow(stringResource(R.string.widget_color_ring_warm), style.ringWarm, WidgetInk.Warm) { c -> restyle { it.copy(ringWarm = c) } }
                                ColorRow(stringResource(R.string.widget_color_ring_rain), style.ringRain, WidgetInk.Rain) { c -> restyle { it.copy(ringRain = c) } }
                            }
                            if (style.colorTheme != ColorTheme.Custom || style.customText == null) {
                                Spacer(Modifier.height(12.dp))
                                Label(stringResource(R.string.widget_config_text_color))
                                GlassSegmented(
                                    options = TextColorMode.entries.map { stringResource(it.labelRes) },
                                    selectedIndex = style.textColor.ordinal,
                                    onSelect = { i -> restyle { it.copy(textColor = TextColorMode.entries[i]) } },
                                )
                            }
                        }
                    }
                    // --- Yazı
                    item {
                        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_text)) {
                            if (isClock && !styleId.isAnalog) {
                                Label(stringResource(R.string.widget_config_clock_font))
                                val selected = style.clockFont ?: styleId.clockFont
                                Column {
                                    ClockFont.entries.forEach { font ->
                                        GlassRadioRow(
                                            title = stringResource(font.labelRes),
                                            selected = font == selected,
                                            onClick = { restyle { it.copy(clockFont = font) } },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            Label(stringResource(R.string.widget_config_weight))
                            GlassSegmented(
                                options = WeightChoice.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.weight.ordinal,
                                onSelect = { i -> restyle { it.copy(weight = WeightChoice.entries[i]) } },
                            )
                            Spacer(Modifier.height(16.dp))
                            Label(stringResource(R.string.widget_config_text_size))
                            GlassSegmented(
                                options = TextSize.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.textSize.ordinal,
                                onSelect = { i -> restyle { it.copy(textSize = TextSize.entries[i]) } },
                            )
                            Spacer(Modifier.height(16.dp))
                            Label(stringResource(R.string.widget_config_info_row))
                            GlassSegmented(
                                options = InfoRow.entries.map { stringResource(it.labelRes) },
                                selectedIndex = style.infoRow.ordinal,
                                onSelect = { i -> restyle { it.copy(infoRow = InfoRow.entries[i]) } },
                            )
                        }
                    }
                    // --- İçerik
                    if (kind.contents.isNotEmpty() || isClock) {
                        item {
                            GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.widget_config_contents)) {
                                val enabled = style.contents ?: kind.defaultContents
                                if (isSs) {
                                    GlassSwitchRow(
                                        title = stringResource(R.string.widget_config_logo),
                                        checked = style.logo,
                                        onCheckedChange = { on -> restyle { it.copy(logo = on) } },
                                    )
                                    GlassSwitchRow(
                                        title = stringResource(R.string.widget_config_date),
                                        checked = style.showDate,
                                        onCheckedChange = { on -> restyle { it.copy(showDate = on) } },
                                    )
                                }
                                styleId.contents.forEach { content ->
                                    GlassSwitchRow(
                                        title = stringResource(content.labelRes),
                                        checked = content in enabled,
                                        onCheckedChange = { on ->
                                            restyle { it.copy(contents = if (on) enabled + content else enabled - content) }
                                        },
                                    )
                                }
                                if (kind == WidgetKind.HomeLocation) Note(stringResource(R.string.widget_config_home_hint))
                                if (isRadial) {
                                    Spacer(Modifier.height(12.dp))
                                    Label(stringResource(R.string.widget_config_second_effect))
                                    GlassSegmented(
                                        options = SecondEffect.entries.map { stringResource(it.labelRes) },
                                        selectedIndex = style.secondEffect.ordinal,
                                        onSelect = { i -> restyle { it.copy(secondEffect = SecondEffect.entries[i]) } },
                                    )
                                    Note(
                                        stringResource(
                                            when {
                                                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> R.string.widget_config_second_old_android
                                                style.secondEffect != SecondEffect.Off && radialSeconds(style) == RadialSeconds.StripLine -> R.string.widget_config_second_glass
                                                else -> R.string.widget_config_second_hint
                                            },
                                        ),
                                    )
                                }
                                if (styleId.isAnalog) {
                                    GlassSwitchRow(
                                        title = stringResource(R.string.widget_config_second_hand),
                                        checked = style.secondHand,
                                        onCheckedChange = { on -> restyle { it.copy(secondHand = on) } },
                                        subtitle = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) stringResource(R.string.widget_config_second_old_android) else null,
                                    )
                                    if (style.secondHand && !isSs) {
                                        Spacer(Modifier.height(8.dp))
                                        Label(stringResource(R.string.widget_config_second_symbol))
                                        GlassSegmented(
                                            options = SecondSymbol.entries.map { stringResource(it.labelRes) },
                                            selectedIndex = style.secondSymbol.ordinal,
                                            onSelect = { i -> restyle { it.copy(secondSymbol = SecondSymbol.entries[i]) } },
                                        )
                                    }
                                }
                                if (styleId == WidgetStyleId.ClockField) {
                                    Spacer(Modifier.height(16.dp))
                                    Label(stringResource(R.string.widget_config_highlighted))
                                    NumeralPicker(style.highlighted, style.accent?.let { Color(it) } ?: WidgetInk.Accent) { n ->
                                        restyle { it.copy(highlighted = if (n in it.highlighted) it.highlighted - n else it.highlighted + n) }
                                    }
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
                        .background(Color.White.copy(alpha = if (saving) 0.6f else 1f))
                        .clickable(enabled = !saving, role = Role.Button) {
                            saving = true
                            scope.launch {
                                // Önce yazma biter (ayar diske ulaşır), sonra yalnızca bu widget yeniden
                                // çizilir, en son ekran kapanır.
                                store.set(appWidgetId, current, kind)
                                WeatherWidgetUpdater.update(context, kind, appWidgetId)
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

@Composable
private fun Note(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
}

/** En üstte canlı önizleme; arkada duvar kâğıdının renkleri (bilinmiyorsa yeşil-gri). */
@Composable
private fun LivePreviewBox(kind: WidgetKind, config: WidgetConfig, size: PreviewSize, input: WidgetInput, wallpaper: List<Color>?) {
    val colors = wallpaper ?: listOf(Color(0xFF44564F), Color(0xFF44564F))
    val configuration = LocalConfiguration.current
    val available = configuration.screenWidthDp - 32 - 32
    // Önizleme ekranın en çok üçte birini kaplar; ayarlara yer kalsın.
    val maxHeight = (configuration.screenHeightDp * 0.3f).coerceAtLeast(120f)
    val scale = min(1f, min(available / size.frame.width.value, maxHeight / size.frame.height.value))
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        WidgetRender(kind, config, size, scale, input)
    }
}

/**
 * Yazı ile tahmini zemin (arka plan + duvar kâğıdı rengi) arasındaki kontrast 4,5:1'in altındaysa
 * kısa bir uyarı; yazı duvar kâğıdının üstündeyse "Hafif perdeyi aç" düğmesi.
 */
@Composable
private fun ContrastWarning(style: WidgetStyle, colors: WidgetColors, wallpaper: List<Color>?, onEnableScrim: () -> Unit) {
    val ground = (wallpaper?.firstOrNull() ?: Color(0xFF44564F)).toArgb()
    val ratio = Contrast.ratio(colors.text.toArgb(), WidgetColors.estimatedBackground(style, colors, ground))
    if (ratio >= Contrast.MIN) return
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.widget_config_contrast_warning),
            color = WarningAccent,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (colors.onWallpaper && style.legibility != Legibility.Scrim) {
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.widget_config_enable_scrim),
                color = Color(0xFF14284A),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = onEnableScrim)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
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
                    // Küçük önizleme, bu stil seçilince widget'ın alacağı görünümü gösterir.
                    val thumbConfig = config.copy(style = config.style.withStyle(id))
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

/** Renk teması seçimi: her tema küçük bir renk örneğiyle; sonda "Özel". */
@Composable
private fun ThemePicker(style: WidgetStyle, sky: SkyTheme, onSelect: (ColorTheme) -> Unit) {
    val context = LocalContext.current
    val selected = style.colorTheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ColorTheme.entries) { theme ->
            val palette = remember(theme, sky, style.customBackground) { WidgetPalettes.of(context, theme, sky, style.customBackground) }
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

/** Saha kadranında vurgu renginde gösterilecek rakamlar: 1–24, dokununca açılır/kapanır. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumeralPicker(selected: Set<Int>, accent: Color, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..24).forEach { n ->
            val on = n in selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = if (on) 0.9f else 0.14f), CircleShape)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(n) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(n.toString(), color = TextPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

