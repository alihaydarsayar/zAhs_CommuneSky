package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.LocalSize
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bütün widget'ların ortak sınıfı. Ayar ve veri akış olarak okunur; ayar, Ev, birimler ya da hava
 * verisi değişince widget kendiliğinden yeniden çizilir. Hangi stilin çizileceği ayardan gelir.
 *
 * [previewConfig]: ekleme ekranındaki canlı önizleme ve testler için; verilirse kayıtlı ayar yerine
 * o kullanılır. [previewInput]: testlerde ve önizleme resimlerinde önbellek yerine örnek veri.
 */
abstract class CommuneSkyWidget(
    val kind: WidgetKind,
    private val previewConfig: WidgetConfig?,
    private val previewInput: WidgetInput? = null,
) : GlanceAppWidget() {

    /**
     * Bütün widget'lar launcher'ın bildirdiği gerçek boyutla çizilir: içerik o boyuta göre yerleşir ve
     * büyür (bkz. [WidgetScale]). Boyut basamaklarıyla çizmek, basamaktan büyük alanda içeriği küçük bırakıyordu.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(kind.sizes)

    /**
     * Ayar ekranındaki önizleme için: duvar kâğıdı açık mı (koyu yazı mı gerekir)? Önizleme sahnesi
     * "Açık" ya da "Koyu" zemine alınınca widget o zemindeki haliyle çizilir. null: telefonun duvar kâğıdı.
     */
    var previewWallpaperDarkText: Boolean? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val started = SystemClock.elapsedRealtimeNanos()
        // Widget'ın türü, ana ekrana eklendiği alıcıdan okunur; bu çizimi hangi sınıfın başlattığına bakılmaz. Böylece
        // yanlış sınıfla başlayan bir çizim (bkz. [WeatherWidgetUpdater.drawAll]) widget'ı başka türe çeviremez.
        val kind = realKind(context, id)
        val configFlow = configFlow(context, id, kind)
        val inputFlow = previewInput?.let { flowOf(it) } ?: WidgetDataLoader(context).input
        // İlk çizim hemen dolu gelsin; sonrası akışlardan.
        val initialConfig = configFlow.first()
        val initialInput = inputFlow.first()
        val loaded = SystemClock.elapsedRealtimeNanos()
        val debug = WidgetTiming.enabled(context)
        if (debug) WidgetSizeLog.options(context, kind, id)
        // Ana ekrandaki widget uygulamanın seçili diliyle çizilir (süreç telefonun diliyle çalışır).
        // Önizleme ve testlerde verilen bağlam zaten doğru dildedir.
        val drawContext = if (previewConfig == null) WidgetLanguage.context(context) else context
        val appWidgetId = if (previewConfig == null) runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull() else null
        // Tarih metin olarak yazılıyorsa (diller farklı) gün değişince bir kez yenilenmesi planlanır.
        if (previewConfig == null && WidgetLanguage.differsFromSystem(drawContext)) WidgetDateReceiver.schedule(context)
        provideContent {
            // Launcher'ın bildirdiği iki ölçü her çizimde yeniden okunur (widget yeniden boyutlandırılmış olabilir).
            val bounds = appWidgetId?.let { WidgetBounds.of(context, it) } ?: WidgetBounds(null, null)
            CompositionLocalProvider(LocalContext provides drawContext, LocalWidgetBounds provides bounds, LocalAppWidgetId provides appWidgetId) {
            val config by configFlow.collectAsState(initialConfig)
            val input by inputFlow.collectAsState(initialInput)
            // Çizim, sistem izlerinde (Perfetto, Macrobenchmark) "CommuneSky.widget.<tür>" bölümü olarak görünür;
            // iz alınmıyorken bu çağrıların maliyeti yok denecek kadar azdır.
            Trace.beginSection(WidgetTiming.section(kind))
            if (debug) {
                // Debug sürümünde çizim süresi ayrıca loglanır: veri yüklendikten sonra 50 ms'yi geçmemeli.
                val composeStart = SystemClock.elapsedRealtimeNanos()
                if (debug) WidgetSizeLog.drawn(kind, config, LocalSize.current)
                WidgetRoot(kind, config, input, previewWallpaperDarkText)
                WidgetTiming.log(kind, config, loadNanos = loaded - started, drawNanos = SystemClock.elapsedRealtimeNanos() - composeStart)
            } else {
                WidgetRoot(kind, config, input, previewWallpaperDarkText)
            }
            Trace.endSection()
            }
        }
    }

    /**
     * Android 15+ widget seçicisindeki önizleme: widget'ın gerçek çizimi, ama her zaman örnek veriyle.
     * Seçici görselinde kullanıcının kendi yeri (evi, mahallesi) görünmez.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val drawContext = WidgetLanguage.context(context)
        val input = WidgetSamples.input(drawContext, scenario = WidgetSamples.Scenario.AtHome)
        provideContent {
            CompositionLocalProvider(LocalContext provides drawContext) { WidgetRoot(kind, WidgetConfig.default(kind), input) }
        }
    }

    /**
     * Widget ana ekrandan kaldırılınca ayarı da silinir. Glance bunu kendi arka plan işinde çağırır
     * (alıcıda ayrıca goAsync() çağırmak ikinci kez boş döner ve uygulamayı çökertiyordu).
     */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull() ?: return
        WidgetConfigStore.get(context).remove(intArrayOf(appWidgetId))
    }

    /** Bu widget kimliğinin gerçek türü: eklendiği alıcıdan; önizlemede ve alıcı bilinmiyorsa bu sınıfın türü. */
    private fun realKind(context: Context, id: GlanceId): WidgetKind {
        if (previewConfig != null) return kind
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull() ?: return kind
        return WidgetKind.of(context, appWidgetId) ?: kind
    }

    private suspend fun configFlow(context: Context, id: GlanceId, kind: WidgetKind): Flow<WidgetConfig> {
        if (previewConfig != null) return flowOf(previewConfig)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
            ?: return flowOf(WidgetConfig.default(kind))
        return WidgetConfigStore.get(context).observe(appWidgetId, kind)
    }
}

class HomeLocationWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.HomeLocation, preview, input)
class ClockWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Clock, preview, input)
class HourlyWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Hourly, preview, input)
class WeeklyWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Weekly, preview, input)
class SmallWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Small, preview, input)
class TextWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Text, preview, input)
class PlacesWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Places, preview, input)
class PrecipitationWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Precipitation, preview, input)
class DetailsWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Details, preview, input)
class TodayWidget(preview: WidgetConfig? = null, input: WidgetInput? = null) : CommuneSkyWidget(WidgetKind.Today, preview, input)

/** Çizilen widget'ın kimliği (tanılama kaydı için); önizlemede null. */
val LocalAppWidgetId = staticCompositionLocalOf<Int?> { null }

/** Widget'ın gösterdiği ana yerin kimliği: havaya dokununca uygulama o yerle açılır. */
val LocalWidgetPlaceId = staticCompositionLocalOf<Long?> { null }

/**
 * Uygulamayı, widget'ın gösterdiği yer seçili olarak açar; [openHomeSettings] ise doğrudan
 * Ayarlar > Ev ve konum.
 */
@Composable
fun openAppAction(openHomeSettings: Boolean = false): Action {
    val placeId = LocalWidgetPlaceId.current
    return when {
        openHomeSettings -> actionStartActivity<MainActivity>(actionParametersOf(MainActivity.OpenHomeSettingsKey to true))
        placeId != null -> actionStartActivity<MainActivity>(actionParametersOf(MainActivity.OpenPlaceKey to placeId))
        else -> actionStartActivity<MainActivity>()
    }
}

/** Birden çok yer gösteren widget'larda (ev ve konum, yerlerim) dokunulan yerle uygulamayı açar. */
fun openPlaceAction(placeId: Long): Action =
    actionStartActivity<MainActivity>(actionParametersOf(MainActivity.OpenPlaceKey to placeId))

/**
 * Debug sürümünde launcher'ın bildirdiği bütün widget ölçülerini ve her çizimin hangi ölçüyle, hangi
 * düzenle yapıldığını loglar ("adb logcat -s WidgetSize"). Üretici launcher'ları ölçüyü Pixel'den farklı
 * (çoğu zaman olduğundan küçük) bildirir; widget yanlış düzenle ya da küçük çizilirse buradan görülür.
 * Yalnızca sayılar loglanır; yer adı ya da hava verisi yazılmaz.
 */
object WidgetSizeLog {
    private const val TAG = "WidgetSize"

    fun options(context: Context, kind: WidgetKind, id: GlanceId) {
        runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(appWidgetId)
            fun int(key: String) = if (options.containsKey(key)) options.getInt(key).toString() else "-"
            val sizes = if (android.os.Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<android.util.SizeF>(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    ?.joinToString(" ") { "%.0fx%.0f".format(java.util.Locale.ROOT, it.width, it.height) } ?: "-"
            } else {
                "-"
            }
            val info = manager.getAppWidgetInfo(appWidgetId)
            val density = context.resources.displayMetrics.density
            Log.d(
                TAG,
                "${kind.name} #$appWidgetId launcher=${info?.let { launcherOf(context) } ?: "-"} sdk=${android.os.Build.VERSION.SDK_INT} " +
                    "minW=${int(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} maxW=${int(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} " +
                    "minH=${int(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} maxH=${int(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} " +
                    "sizes=[$sizes] provider=${info?.let { "%.0fx%.0f".format(java.util.Locale.ROOT, it.minWidth / density, it.minHeight / density) } ?: "-"} " +
                    "screen=${context.resources.configuration.screenWidthDp}x${context.resources.configuration.screenHeightDp}dp",
            )
        }
    }

    /** Hangi ana ekran uygulaması? (Ölçüleri bildiren o.) */
    private fun launcherOf(context: Context): String = runCatching {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        // Varsayılan ana ekran sorulamadığı için kurulu ana ekran uygulamaları listelenir (ayarların yedek ekranı hariç).
        context.packageManager.queryIntentActivities(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.filter { it != "com.android.settings" }.distinct().joinToString("+")
    }.getOrNull()?.ifEmpty { null } ?: "?"

    /** Ayarın kısa özeti: kaydedilen ile çizilen ayar aynı mı diye karşılaştırmak için (içeriği loglanmaz). */
    private fun digest(config: WidgetConfig): Int = config.toString().hashCode()

    /** Ayar ekranı "Kaydet"e basınca: hangi widget'a hangi ayar yazıldı. Debug sürümünde loglanır. */
    fun saved(context: Context, kind: WidgetKind, appWidgetId: Int, config: WidgetConfig) {
        if (WidgetTiming.enabled(context)) Log.d(TAG, "${kind.name} #$appWidgetId kaydedildi stil=${config.style.styleId(kind).name} ayar=%08x".format(java.util.Locale.ROOT, digest(config)))
    }

    fun drawn(kind: WidgetKind, config: WidgetConfig, size: DpSize) {
        val style = config.style.styleId(kind)
        val real = WidgetScale.real(style, size)
        val grow = WidgetScale.grow(style, real)
        val layout = if (WidgetScale.compact(style, real)) "yedek(kucuk)" else "stil"
        Log.d(TAG, "${kind.name}/${style.name} cizim=%.0fx%.0fdp buyume=%.2f duzen=%s ayar=%08x".format(java.util.Locale.ROOT, size.width.value, size.height.value, grow, layout, digest(config)))
    }
}

/** Debug sürümünde her widget çiziminin süresini loglar ("adb logcat -s WidgetTiming"). */
object WidgetTiming {
    private const val TAG = "WidgetTiming"

    fun enabled(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Sistem izindeki bölüm adı. */
    fun section(kind: WidgetKind): String = "CommuneSky.widget.${kind.name}"

    fun log(kind: WidgetKind, config: WidgetConfig, loadNanos: Long, drawNanos: Long) {
        Log.d(TAG, "${kind.name}/${config.style.styleId(kind).name} load=${loadNanos / 1_000_000.0}ms draw=${drawNanos / 1_000_000.0}ms")
    }
}

/**
 * Stil, widget'ın boyutuna nasıl uyar? Her stilin bir tasarım boyutu ([WidgetStyleId.previewSize]) ve
 * düzeninin hâlâ temiz sığdığı en küçük boyutu ([fits]) vardır.
 *
 * - Tasarım boyutundan büyük widget'ta her şey (yazı, ikon, boşluk) aynı oranda büyür: içerik alanı doldurur.
 * - Tasarım ile en küçük boyut arasında oran 1'dir; stil kendi kurallarıyla yerleşir.
 * - En küçük boyuttan biraz küçükse stil başka düzene geçmez, olduğu gibi küçülür ([FLOOR]'a kadar).
 * - Daha da küçükse saatler ortak yedek düzene geçer; diğer türler kendi küçük düzenlerini seçer.
 *
 * Launcher'lar ölçüyü olduğundan küçük bildirebildiği için (ör. 4×2 hücre için 250×110 dp) bu aralık
 * geniştir. Saf mantık; test edilir.
 */
object WidgetScale {
    const val MAX_GROW = 3.2f

    /** Saatler bundan fazla küçültülmez; altında ortak yedek düzen çizilir. */
    const val FLOOR = 0.7f

    /**
     * Diğer türlerde sınır biraz daha düşük: 3×3 hücreyi 180×180 dp bildiren launcher'larda geniş
     * stiller (260–270 dp) kesilmek yerine küçülerek sığar.
     */
    const val FLOOR_OTHER = 0.66f

    private fun floor(style: WidgetStyleId) = if (style.kind == WidgetKind.Clock) FLOOR else FLOOR_OTHER

    /** Stilin düzeninin kesilmeden sığdığı en küçük boyut (dp). */
    fun fits(style: WidgetStyleId): DpSize = when (style) {
        WidgetStyleId.ClockWeather -> DpSize(280.dp, 120.dp)
        WidgetStyleId.ClockBig -> DpSize(280.dp, 150.dp)
        WidgetStyleId.ClockSide -> DpSize(300.dp, 150.dp)
        WidgetStyleId.ClockHome -> DpSize(300.dp, 150.dp)
        WidgetStyleId.ClockSun -> DpSize(280.dp, 150.dp)
        WidgetStyleId.ClockLine -> DpSize(240.dp, 90.dp)
        WidgetStyleId.ClockGlance -> DpSize(240.dp, 84.dp)
        WidgetStyleId.ClockBold -> DpSize(140.dp, 150.dp)
        WidgetStyleId.ClockCard -> DpSize(150.dp, 150.dp)
        // Kadranlar ve ışınsal saat kendi kendine ölçeklenir; bu boyutun altında okunmazlar.
        WidgetStyleId.ClockRadial -> DpSize(200.dp, 84.dp)
        WidgetStyleId.ClockAnalog, WidgetStyleId.ClockField, WidgetStyleId.ClockRing, WidgetStyleId.ClockSS -> DpSize(84.dp, 84.dp)
        // Diğer türler: stilin kendi düzeniyle çizildiği en küçük boyut. Daha küçükte (oran [FLOOR]'un
        // altına inince) tür, o boyut için hazırlanmış daha sade düzenini seçer.
        WidgetStyleId.HomeSmart -> DpSize(260.dp, 150.dp)
        WidgetStyleId.HomeRow -> DpSize(250.dp, 86.dp)
        WidgetStyleId.HomeList -> DpSize(270.dp, 130.dp)
        WidgetStyleId.HourlyCurve -> DpSize(260.dp, 150.dp)
        WidgetStyleId.HourlyIcons -> DpSize(260.dp, 135.dp)
        WidgetStyleId.HourlyRow -> DpSize(250.dp, 86.dp)
        WidgetStyleId.WeeklyBars, WidgetStyleId.WeeklyList -> DpSize(260.dp, 170.dp)
        WidgetStyleId.WeeklyColumns -> DpSize(250.dp, 100.dp)
        WidgetStyleId.SmallClassic -> DpSize(140.dp, 150.dp)
        WidgetStyleId.SmallCentered -> DpSize(110.dp, 110.dp)
        WidgetStyleId.SmallMinimal -> DpSize(130.dp, 145.dp)
        WidgetStyleId.TextStack -> DpSize(180.dp, 64.dp)
        WidgetStyleId.TextLine -> DpSize(220.dp, 40.dp)
        WidgetStyleId.TextTemperature -> DpSize(100.dp, 40.dp)
        WidgetStyleId.PlacesColumns -> DpSize(260.dp, 64.dp)
        WidgetStyleId.PlacesList -> DpSize(270.dp, 104.dp)
        WidgetStyleId.PrecipitationBars -> DpSize(270.dp, 90.dp)
        WidgetStyleId.PrecipitationSentence -> DpSize(220.dp, 80.dp)
        WidgetStyleId.DetailsTiles -> DpSize(270.dp, 120.dp)
        WidgetStyleId.DetailsSun -> DpSize(140.dp, 140.dp)
        WidgetStyleId.DetailsList -> DpSize(240.dp, 120.dp)
        WidgetStyleId.SmallWear -> DpSize(110.dp, 110.dp)
        WidgetStyleId.TextWear -> DpSize(180.dp, 64.dp)
        WidgetStyleId.TodayCard -> DpSize(250.dp, 130.dp)
    }

    /** Sınırlanmamış oran: 1'den küçükse widget, stilin en küçük boyutundan küçüktür. */
    private fun raw(style: WidgetStyleId, real: DpSize): Float {
        val design = style.previewSize
        val up = minOf(real.width.value / design.width.value, real.height.value / design.height.value)
        if (up >= 1f) return up
        val min = fits(style)
        return minOf(real.width.value / min.width.value, real.height.value / min.height.value).coerceAtMost(1f)
    }

    /** Saat stili bu boyutta kendi düzeniyle değil, ortak yedek düzenle mi çizilir? */
    fun compact(style: WidgetStyleId, real: DpSize): Boolean =
        style.kind == WidgetKind.Clock && raw(style, real) < (if (style.scalesItself) 1f else FLOOR)

    /** İçeriğin büyüme (1'den büyük) ya da küçülme (1'den küçük) oranı. */
    fun grow(style: WidgetStyleId, real: DpSize): Float {
        if (style.scalesItself) return 1f
        val raw = raw(style, real)
        // Stilin sığmayacağı kadar küçük: küçültmek yerine o boyutun düzeni (saatte yedek düzen) çizilir.
        return if (raw < floor(style)) 1f else raw.coerceAtMost(MAX_GROW)
    }

    /** Launcher ölçü bildirmediyse (sıfır ya da anlamsız küçük) stilin tasarım boyutu kullanılır. */
    fun real(style: WidgetStyleId, reported: DpSize): DpSize =
        if (reported.width.value < 40f || reported.height.value < 24f) style.previewSize else reported
}

/** Hava sahnesinin teması (gökyüzü renkleri için); veri yoksa açık gece. */
fun WeatherSnapshot?.skyTheme(): SkyTheme = this?.currentScene()?.theme ?: SkyTheme.ClearNight

/**
 * Widget'ın kökü: stili ve renkleri hesaplar, birim ayarlarını sağlar ve stile göre çizer.
 * Renkler, widget'ın gösterdiği ana yerin havasına göre seçilir.
 */
@Composable
fun WidgetRoot(kind: WidgetKind, config: WidgetConfig, input: WidgetInput, wallpaperDarkText: Boolean? = null) {
    val context = LocalContext.current
    val styleId = config.style.styleId(kind)
    val primary: WeatherSnapshot? = when (kind) {
        WidgetKind.HomeLocation -> when (val state = input.homeLocation) {
            is HomeLocationState.AtHome -> state.home.snapshot
            is HomeLocationState.Nearby -> state.home.snapshot
            is HomeLocationState.Away -> state.here.snapshot
            is HomeLocationState.Single -> state.place.snapshot
            HomeLocationState.Empty -> null
        }
        WidgetKind.Places -> input.savedPlaces(includeLocation = false).firstOrNull()?.snapshot
        else -> if (styleId == WidgetStyleId.ClockHome) input.device ?: input.home?.snapshot else input.place(config.place)?.snapshot
    }
    // Stil, tasarım boyutundan büyük widget'ta oranlı büyür; yerleşimini "tasarım dp"siyle yapar.
    // Telefon dikeyken yatay için hazırlanan çizim de dikey ölçüyle yerleşir (bkz. WidgetVariants): bazı ana
    // ekranlar dikeyde yanlışlıkla yatay çizimi gösterip içeriği kesiyordu.
    val variant = LocalSize.current
    val real = WidgetScale.real(styleId, WidgetVariants.layoutSize(variant, LocalWidgetBounds.current, WidgetVariants.isPortrait(context)))
    val grow = WidgetScale.grow(styleId, real)
    WidgetDiagnostics.drawn(context, LocalAppWidgetId.current, kind, styleId, variant, real, WidgetScale.compact(styleId, real))
    // Işınsal saatin resimleri launcher'ın sınırını aşacak kadar büyükse (çok büyük widget) widget boş kalmasın:
    // sade yedek düzen çizilir.
    val tooHeavy = styleId == WidgetStyleId.ClockRadial && run {
        val style = config.style
        val gradientGround = style.background == BackgroundKind.Sky
        val corner = style.corners.radius(context).value
        WidgetBitmaps.radialBytes(radialSeconds(style), gradientGround, 3, real.width.value, real.height.value, context.resources.displayMetrics.density, corner) >
            WidgetBitmaps.budgetBytes(context)
    }
    val theme = WidgetTheme(
        kind = kind,
        styleId = styleId,
        style = config.style,
        colors = WidgetColors.resolve(context, config.style, primary.skyTheme(), wallpaperDarkText ?: WallpaperTone.prefersDarkText(context)),
        fontScaleFix = WidgetTheme.fontScaleFix(context),
        systemFontScale = context.resources.configuration.fontScale,
        staticDate = WidgetLanguage.differsFromSystem(context),
        grow = grow,
        compact = WidgetScale.compact(styleId, real) || tooHeavy,
    )
    CompositionLocalProvider(
        LocalSize provides DpSize(real.width / grow, real.height / grow),
        LocalAppSettings provides input.settings,
        LocalWidgetTheme provides theme,
        LocalWidgetPlaceId provides primary?.placeId,
    ) {
        val place = input.place(config.place)
        when (kind) {
            WidgetKind.HomeLocation -> HomeLocationContent(styleId, input)
            WidgetKind.Places -> PlacesContent(styleId, input)
            else -> if (place == null && styleId != WidgetStyleId.ClockHome) {
                EmptyContent()
            } else {
                when (kind) {
                    WidgetKind.Clock -> ClockContent(styleId, place, input)
                    WidgetKind.Hourly -> HourlyContent(styleId, place!!)
                    WidgetKind.Weekly -> WeeklyContent(styleId, place!!)
                    WidgetKind.Small -> SmallContent(styleId, place!!)
                    WidgetKind.Text -> TextContent(styleId, place!!)
                    WidgetKind.Precipitation -> PrecipitationContent(styleId, place!!)
                    WidgetKind.Details -> DetailsContent(styleId, place!!)
                    WidgetKind.Today -> TodayContent(styleId, place!!)
                    else -> Unit
                }
            }
        }
    }
}

/** Henüz veri yok: uygulamayı açmaya davet. */
@Composable
fun EmptyContent() {
    val context = LocalContext.current
    WidgetSurface(onClick = openAppAction(), contentAlignment = Alignment.Center) {
        WText(context.getString(R.string.widget_no_data), 13f, align = TextAlign.Center, maxLines = 3)
    }
}

/** Bütün widget alıcılarının ortak davranışı. */
abstract class CommuneSkyWidgetReceiver(kind: WidgetKind) : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = kind.widget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Uygulama hiç açılmasa bile widget güncel kalsın.
        RefreshScheduler.schedule(context)
    }
}

// Alıcı adları 1.2'den korunuyor: ana ekrandaki eski widget'lar silinmeden yeni tasarıma geçer.
class HomeWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.HomeLocation)
class ClockWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Clock)
class MediumWeatherWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Hourly)
class LargeWeatherWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Weekly)
class SmallWeatherWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Small)
class TextWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Text)
class PlacesWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Places)
class PrecipitationWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Precipitation)
class DetailsWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Details)
class TodayWidgetReceiver : CommuneSkyWidgetReceiver(WidgetKind.Today)

object WeatherWidgetUpdater {
    private const val TAG = "WidgetUpdater"

    /** Önizlemelerin içeriği sürüm değişmeden değişirse artırılır; önizlemeler yeniden yayınlanır. */
    private const val PREVIEWS_REVISION = 2

    /** Tek bir widget'ı yeniden çizer (ayarı kaydedilince); diğer widget'lara dokunmaz. */
    suspend fun update(context: Context, kind: WidgetKind, appWidgetId: Int) {
        runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            kind.widget().update(context, glanceId)
        }.onFailure { Log.w(TAG, "${kind.name} #$appWidgetId çizilemedi", it) }
    }

    /** Kısa aralıkla gelen istekler tek çizimde birleşir (ör. tahmin ve istasyon ölçümü art arda yenilenince). */
    private const val COALESCE_MS = 300L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val drawing = Mutex()
    private var pending: Deferred<Unit>? = null

    /**
     * Önbellek ya da ayarlar değişince ana ekrandaki widget'ları yeniden çizer; çizim bitince döner.
     *
     * - Aynı anda gelen istekler birleşir: kısa bir bekleme sonunda tek çizim yapılır.
     * - Gösterilecek içerik son çizimle aynıysa (bkz. [WidgetDigest]) hiç çizilmez.
     * - Ana ekranda widget yoksa veri bile okunmaz.
     */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        val job = synchronized(lock) {
            pending ?: scope.async {
                delay(COALESCE_MS)
                synchronized(lock) { pending = null }
                drawing.withLock { drawIfChanged(app) }
            }.also { pending = it }
        }
        job.await()
    }

    private suspend fun drawIfChanged(context: Context) {
        val ids = runCatching { WidgetDigest.widgetIds(context) }.getOrDefault(emptyList())
        if (ids.isEmpty()) return
        // Özet hesaplanamazsa her zamanki gibi çizilir.
        val digest = runCatching { WidgetDigest.current(context, ids) }.getOrNull()
        if (digest != null && digest == WidgetDigest.saved(context)) {
            Log.d(TAG, "İçerik değişmedi; widget'lar yeniden çizilmedi")
            return
        }
        val drawn = drawAll(context)
        if (drawn && digest != null) WidgetDigest.save(context, digest) else WidgetDigest.clear(context)
    }

    /**
     * Ana ekrandaki bütün widget'ları, kendi türünün widget'ıyla çizer.
     *
     * Kimlikler Glance'in "bu sınıfın widget'ları" sorgusundan ([androidx.glance.appwidget.updateAll]) değil, her türün
     * alıcısından alınır. Release derlemesinde R8 birbirine benzeyen widget sınıflarını tek sınıfta birleştirir; o
     * zaman Glance her tür için bütün widget'ları döndürüyordu ve süreç yeni başlamışsa (arka plan yenilemesi, saat
     * değişimi) listenin ilki olan "Ev ve konum" bütün widget'ların çizimini başlatıp onları Ev ve konum widget'ına çeviriyordu.
     */
    private suspend fun drawAll(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val glance = GlanceAppWidgetManager(context)
        var drawn = true
        WidgetKind.entries.forEach { kind ->
            val widget = kind.widget()
            for (appWidgetId in manager.getAppWidgetIds(android.content.ComponentName(context, kind.receiverClass))) {
                runCatching { widget.update(context, glance.getGlanceIdBy(appWidgetId)) }.onFailure {
                    drawn = false
                    Log.w(TAG, "${kind.name} #$appWidgetId çizilemedi", it)
                }
            }
        }
        return drawn
    }

    /**
     * Android 15+ widget seçicisine gerçek önizlemeleri yükler. Sistem bunu saatte birkaç kezle
     * sınırladığı için her sürümde ve her dilde bir kez yapılır (örnek yer adı dile göre yazılır).
     */
    suspend fun publishPreviews(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val store = WidgetConfigStore.get(context)
        val version = androidx.core.content.pm.PackageInfoCompat
            .getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
            .let { code -> "$code:${locale(WidgetLanguage.context(context)).toLanguageTag()}:$PREVIEWS_REVISION".hashCode() }
        if (store.previewsPublishedVersion() == version) return
        val manager = GlanceAppWidgetManager(context)
        var allPublished = true
        WidgetKind.entries.forEach { kind ->
            val result = runCatching { manager.setWidgetPreviews(kind.receiverClass.kotlin) }
                .onFailure { Log.w(TAG, "${kind.name} önizlemesi yayınlanamadı", it) }
                .getOrNull()
            if (result != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                Log.i(TAG, "${kind.name} önizlemesi yayınlanmadı (sonuç $result); sonraki açılışta yeniden denenecek")
                allPublished = false
            }
        }
        if (allPublished) store.setPreviewsPublishedVersion(version)
    }
}

/** Kök yüzeyin varsayılan iç boşluğu. */
internal val DefaultPadding = 18.dp
