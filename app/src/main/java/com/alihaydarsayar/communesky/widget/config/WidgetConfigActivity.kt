package com.alihaydarsayar.communesky.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import com.alihaydarsayar.communesky.widget.WallpaperTone
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetConfigStore
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetKind
import com.alihaydarsayar.communesky.widget.WidgetPlace
import com.alihaydarsayar.communesky.widget.WidgetSamples
import com.alihaydarsayar.communesky.widget.WidgetStyle
import com.alihaydarsayar.communesky.widget.WidgetStyleId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Widget ayar ekranı: widget eklenirken açılır; sonradan widget'a uzun basıp "Widget'ı düzenle"
 * ile (Android 12+) ya da uygulamada Ayarlar > Widget'larım'dan tekrar açılabilir.
 *
 * Düzen: üstte önizleme sahnesi (sola-sağa kaydırınca stil değişir), altında ayar satırları. Her
 * satır alttan bir sayfa açar; önizleme kısalır ama tam görünür kalır ve her değişiklik anında
 * önizlemede görünür. Önizleme, ana ekrandaki widget'la aynı kodla ve aynı boyutta çizilir.
 *
 * Görünüm düz ve nötrdür (gökyüzü ve cam kart yok); renk sadece widget'ın kendisindedir.
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
        setContent {
            val dark = isSystemInDarkTheme()
            // Durum çubuğu ikonları zemine göre: koyuda açık, açıkta koyu.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CommuneSkyTheme {
                CompositionLocalProvider(LocalConfigColors provides configColors()) {
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
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

/** Alttan açılan sayfalar. */
enum class ConfigPage { Background, Colors, Text, Contents, Place }

/** Önizleme sahnesinin zemini. */
enum class StageMode { Wallpaper, Light, Dark }

@OptIn(ExperimentalFoundationApi::class)
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
    // Ekran açıldığındaki ayar: değişiklik var mı diye bununla karşılaştırılır.
    var saved by remember { mutableStateOf<WidgetConfig?>(null) }
    var isNew by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }
    var deviceName by remember { mutableStateOf<String?>(null) }
    var sky by remember { mutableStateOf(SkyTheme.ClearNight) }
    var input by remember { mutableStateOf<WidgetInput?>(null) }
    var recent by remember { mutableStateOf<List<Int>>(emptyList()) }
    var page by remember { mutableStateOf<ConfigPage?>(null) }
    var mode by remember { mutableStateOf(StageMode.Wallpaper) }
    var undo by remember { mutableStateOf<WidgetConfig?>(null) }
    var askOnExit by remember { mutableStateOf(false) }

    LaunchedEffect(appWidgetId) {
        places = placesRepository.all()
        val device = weatherRepository.snapshot(DEVICE_PLACE_ID)
        deviceName = device?.city?.name
        device?.let { sky = it.currentScene().theme }
        // Önizlemeler için veri bir kez okunur; henüz hiç veri yoksa örnek veri.
        input = WidgetDataLoader(context).input.first().takeIf { it.weather.isNotEmpty() } ?: WidgetSamples.input(context)
        recent = store.recentColors()
        isNew = !store.exists(appWidgetId)
        // Yeni widget: aynı türden en son kaydedilen ayarla açılır (silip yeniden ekleyince ayarlar geri gelir).
        val initial = store.initial(appWidgetId, kind)
        config = initial
        saved = initial
    }
    val current = config ?: return Box(Modifier.fillMaxSize().background(C.ground))
    val previewInput = input ?: return Box(Modifier.fillMaxSize().background(C.ground))
    val style = current.style
    val styleId = style.styleId(kind)
    val dirty = saved != current
    fun update(transform: (WidgetConfig) -> WidgetConfig) {
        config = transform(current)
    }
    fun restyle(transform: (WidgetStyle) -> WidgetStyle) = update { it.copy(style = transform(it.style)) }
    fun pickColor(color: Int?) {
        if (color == null) return
        recent = (listOf(color) + recent.filter { it != color }).take(WidgetConfigStore.RECENT_COLORS)
        scope.launch { store.setRecentColors(recent) }
    }
    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            // Önce yazma biter (ayar diske ulaşır), sonra yalnızca bu widget yeniden çizilir, en son ekran kapanır.
            store.set(appWidgetId, current, kind)
            WeatherWidgetUpdater.update(context, kind, appWidgetId)
            onDone()
        }
    }
    fun close() {
        // Değişiklik varsa çıkarken sorulur.
        if (dirty) askOnExit = true else onCancel()
    }
    BackHandler {
        if (page != null) page = null else close()
    }
    // "Geri al" bildirimi kısa süre sonra kendiliğinden kapanır.
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(6_000)
            undo = null
        }
    }

    val styles = kind.styles
    val pager = rememberPagerState(initialPage = styles.indexOf(styleId).coerceAtLeast(0)) { styles.size }
    // Önizleme kaydırılıp yeni sayfada durunca stil değişir; kullanıcının seçtiği her şey korunur.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { index ->
            val id = styles[index]
            config?.let { latest -> if (latest.style.styleId(kind) != id) config = latest.copy(style = latest.style.withStyle(id)) }
        }
    }
    // Sıfırla ya da geri al stili değiştirirse önizleme de o stile gelir.
    LaunchedEffect(styleId) {
        val index = styles.indexOf(styleId)
        if (index >= 0 && index != pager.settledPage && !pager.isScrollInProgress) pager.scrollToPage(index)
    }

    val stageHeight by animateDpAsState(if (page == null) 300.dp else 200.dp, tween(MOTION_MS), label = "stage")
    Column(
        Modifier
            .fillMaxSize()
            .background(C.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(
            title = stringResource(R.string.widget_config_kind_title, stringResource(kind.nameRes)),
            onClose = ::close,
            onReset = {
                undo = current
                config = current.copy(style = styleId.defaultStyle())
            },
        )
        PreviewStage(
            kind = kind,
            config = current,
            appWidgetId = appWidgetId,
            input = previewInput,
            pager = pager,
            mode = mode,
            onMode = { mode = it },
            compact = page != null,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(stageHeight),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(MOTION_MS)) togetherWith fadeOut(tween(MOTION_MS)) },
            modifier = Modifier.weight(1f),
            label = "page",
        ) { open ->
            if (open == null) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(if (styles.size > 1) R.string.widget_config_swipe_hint else R.string.widget_config_single_style_hint),
                        color = C.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        SettingsList(kind, current, places, sky, onOpen = { page = it })
                        Spacer(Modifier.height(12.dp))
                    }
                    undo?.let { previous ->
                        UndoBar(onUndo = {
                            config = previous
                            undo = null
                        })
                    }
                    CfgButton(
                        stringResource(if (isNew) R.string.widget_config_add else R.string.widget_config_save_widget),
                        onClick = ::save,
                        enabled = !saving,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth().heightIn(min = 56.dp),
                    )
                }
            } else {
                Sheet(
                    title = stringResource(open.titleRes()),
                    onDone = { page = null },
                ) {
                    when (open) {
                        ConfigPage.Background -> BackgroundPage(style, sky, ::restyle)
                        ConfigPage.Colors -> ColorsPage(kind, style, sky, recent, ::restyle, ::pickColor)
                        ConfigPage.Text -> TextPage(kind, style, ::restyle)
                        ConfigPage.Contents -> ContentsPage(kind, style, ::restyle)
                        ConfigPage.Place -> PlacePage(current, places, deviceName) { place -> update { it.copy(place = place) } }
                    }
                }
            }
        }
    }

    if (askOnExit) {
        AlertDialog(
            onDismissRequest = { askOnExit = false },
            containerColor = C.card,
            titleContentColor = C.text,
            textContentColor = C.secondary,
            title = { Text(stringResource(R.string.widget_config_unsaved_title)) },
            text = { Text(stringResource(R.string.widget_config_unsaved_text)) },
            confirmButton = { CfgButton(stringResource(R.string.widget_config_save), onClick = { askOnExit = false; save() }) },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CfgButton(stringResource(R.string.widget_config_keep_editing), onClick = { askOnExit = false }, filled = false)
                    CfgButton(stringResource(R.string.widget_config_discard), onClick = { askOnExit = false; onCancel() }, filled = false)
                }
            },
        )
    }
}

private fun ConfigPage.titleRes(): Int = when (this) {
    ConfigPage.Background -> R.string.widget_config_page_background
    ConfigPage.Colors -> R.string.widget_config_colors
    ConfigPage.Text -> R.string.widget_config_text
    ConfigPage.Contents -> R.string.widget_config_shown
    ConfigPage.Place -> R.string.widget_config_location
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val closeLabel = stringResource(R.string.close)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = C.text, modifier = Modifier.size(22.dp))
        }
        Text(
            title,
            color = C.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(
            Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onReset).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.widget_config_reset), color = C.secondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UndoBar(onUndo: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.widget_config_reset_done), color = C.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        CfgButton(stringResource(R.string.undo), onClick = onUndo, filled = false)
    }
}

/**
 * Önizleme sahnesi. Ortada widget gerçek boyut oranıyla; sığmazsa küçültülür, kırpılmaz. Sola-sağa
 * kaydırınca stil değişir: her sayfa o stilin, kullanıcının o anki ayarlarıyla önizlemesidir.
 * Yalnızca görünen ve yanındaki sayfalar çizilir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewStage(
    kind: WidgetKind,
    config: WidgetConfig,
    appWidgetId: Int,
    input: WidgetInput,
    pager: PagerState,
    mode: StageMode,
    onMode: (StageMode) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val styles = kind.styles
    val wallpaper = remember { WallpaperTone.colors(context) }
    val wallpaperDarkText = remember { WallpaperTone.prefersDarkText(context) }
    // "Duvar kâğıdı": telefonun duvar kâğıdı renklerinden yumuşak gradyan; resmin kendisi okunmaz.
    val ground = when (mode) {
        StageMode.Wallpaper -> wallpaper ?: listOf(Color(0xFF3A3F47), Color(0xFF23262B))
        StageMode.Light -> listOf(Color(0xFFE9EEF3), Color(0xFFD5DCE4))
        StageMode.Dark -> listOf(Color(0xFF2A2D33), Color(0xFF15171A))
    }
    val darkText = when (mode) {
        StageMode.Wallpaper -> wallpaperDarkText
        StageMode.Light -> true
        StageMode.Dark -> false
    }
    val labelColor = if (darkText) Color(0xFF141416) else Color(0xFFF5F3EF)
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    Box(modifier.clip(StageShape).background(Brush.verticalGradient(ground))) {
        val current = config.style.styleId(kind)
        // Stil sayfaları: yandaki stillerin kenarı soluk görünür.
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { styles[it].name },
            modifier = Modifier.fillMaxSize().padding(top = if (compact) 8.dp else 44.dp, bottom = if (compact) 8.dp else 56.dp),
        ) { index ->
            val id = styles[index]
            val size = remember(appWidgetId, id) { WidgetSizes.preview(context, kind, appWidgetId, id.previewSize) }
            val styled = remember(config, id) { config.copy(style = config.style.withStyle(id)) }
            val availableWidth = configuration.screenWidthDp - 32f - 88f
            val availableHeight = if (compact) 184f else 200f
            val scale = min(1f, min(availableWidth / size.frame.width.value, availableHeight / size.frame.height.value))
            val name = stringResource(id.nameRes)
            val description = stringResource(R.string.widget_config_style_position, name, styles.size, index + 1)
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(if (index == pager.currentPage) 1f else 0.4f)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                WidgetRender(kind, styled, size, scale, input, wallpaperDarkText = darkText)
            }
        }
        if (!compact) {
            // Sol üstte sahnenin zemini, sağ üstte widget'ın boyutu.
            Row(
                Modifier.padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StageMode.entries.forEach { option ->
                    val selected = option == mode
                    Box(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFFF5F3EF) else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onMode(option) })
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    StageMode.Wallpaper -> R.string.widget_stage_wallpaper
                                    StageMode.Light -> R.string.widget_stage_light
                                    StageMode.Dark -> R.string.widget_stage_dark
                                },
                            ),
                            color = if (selected) Color(0xFF141416) else Color(0xFFF5F3EF),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            val frame = remember(appWidgetId, current) { WidgetSizes.preview(context, kind, appWidgetId, current.previewSize).frame }
            Text(
                stringResource(R.string.widget_config_cells, (frame.width.value / 86f).roundToInt().coerceAtLeast(1), (frame.height.value / 96f).roundToInt().coerceAtLeast(1)),
                color = Color(0xFFF5F3EF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 12.dp, vertical = 9.dp),
            )
            // Altta stilin adı, oklar ve sayfa noktaları.
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previous = stringResource(R.string.widget_config_previous_style)
                    val next = stringResource(R.string.widget_config_next_style)
                    Arrow("‹", previous, labelColor, enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                    Text(
                        stringResource(styles[pager.currentPage].nameRes),
                        color = labelColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    Arrow("›", next, labelColor, enabled = pager.currentPage < styles.size - 1) { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                }
                if (styles.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Çok stilli türlerde noktalar sığsın diye en fazla 9 nokta: baştan, ortadan ya da sondan bir pencere.
                        val window = 9
                        val start = (pager.currentPage - window / 2).coerceIn(0, (styles.size - window).coerceAtLeast(0))
                        (start until min(styles.size, start + window)).forEach { index ->
                            val selected = index == pager.currentPage
                            Box(Modifier.height(4.dp).width(if (selected) 14.dp else 4.dp).clip(CircleShape).background(labelColor.copy(alpha = if (selected) 1f else 0.4f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Arrow(symbol: String, description: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp, 32.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color.copy(alpha = if (enabled) 0.8f else 0.25f), style = MaterialTheme.typography.titleMedium)
    }
}

/** Alttan açılan sayfa: önizlemenin altında başlar, widget'ın üstüne binmez; karartma yok. */
@Composable
private fun Sheet(title: String, onDone: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp).clip(SheetShape).background(C.card)) {
        Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(C.line.copy(alpha = 0.3f)))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = C.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CfgButton(stringResource(R.string.widget_config_done), onClick = onDone)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            content()
        }
    }
}
