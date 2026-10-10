package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.work.RefreshScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

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
     * Saat widget'ı gerçek boyutuyla çizilir (ışınsal çizgiler ve saniye şeritleri widget'ın tam
     * ölçüsüne göre hesaplanır). Diğerleri boyut basamaklarıyla: küçültülünce daha az bilgi.
     */
    override val sizeMode: SizeMode = if (kind == WidgetKind.Clock) SizeMode.Exact else SizeMode.Responsive(kind.sizes)

    override val previewSizeMode = SizeMode.Responsive(kind.sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val started = SystemClock.elapsedRealtimeNanos()
        val configFlow = configFlow(context, id)
        val inputFlow = previewInput?.let { flowOf(it) } ?: WidgetDataLoader(context).input
        // İlk çizim hemen dolu gelsin; sonrası akışlardan.
        val initialConfig = configFlow.first()
        val initialInput = inputFlow.first()
        val loaded = SystemClock.elapsedRealtimeNanos()
        val debug = WidgetTiming.enabled(context)
        provideContent {
            val config by configFlow.collectAsState(initialConfig)
            val input by inputFlow.collectAsState(initialInput)
            if (debug) {
                // Debug sürümünde çizim süresi ölçülür: veri yüklendikten sonra 50 ms'yi geçmemeli.
                val composeStart = SystemClock.elapsedRealtimeNanos()
                WidgetRoot(kind, config, input)
                WidgetTiming.log(kind, config, loadNanos = loaded - started, drawNanos = SystemClock.elapsedRealtimeNanos() - composeStart)
            } else {
                WidgetRoot(kind, config, input)
            }
        }
    }

    /**
     * Android 15+ widget seçicisindeki önizleme: widget'ın gerçek çizimi, ama her zaman örnek veriyle.
     * Seçici görselinde kullanıcının kendi yeri (evi, mahallesi) görünmez.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val input = WidgetSamples.input(context, scenario = WidgetSamples.Scenario.AtHome)
        provideContent { WidgetRoot(kind, WidgetConfig.default(kind), input) }
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

    private suspend fun configFlow(context: Context, id: GlanceId): Flow<WidgetConfig> {
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

/** Debug sürümünde her widget çiziminin süresini loglar ("adb logcat -s WidgetTiming"). */
object WidgetTiming {
    private const val TAG = "WidgetTiming"

    fun enabled(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun log(kind: WidgetKind, config: WidgetConfig, loadNanos: Long, drawNanos: Long) {
        Log.d(TAG, "${kind.name}/${config.style.styleId(kind).name} load=${loadNanos / 1_000_000.0}ms draw=${drawNanos / 1_000_000.0}ms")
    }
}

/** Hava sahnesinin teması (gökyüzü renkleri için); veri yoksa açık gece. */
fun WeatherSnapshot?.skyTheme(): SkyTheme = this?.currentScene()?.theme ?: SkyTheme.ClearNight

/**
 * Widget'ın kökü: stili ve renkleri hesaplar, birim ayarlarını sağlar ve stile göre çizer.
 * Renkler, widget'ın gösterdiği ana yerin havasına göre seçilir.
 */
@Composable
fun WidgetRoot(kind: WidgetKind, config: WidgetConfig, input: WidgetInput) {
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
    val theme = WidgetTheme(
        kind = kind,
        styleId = styleId,
        style = config.style,
        colors = WidgetColors.resolve(context, config.style, primary.skyTheme()),
        fontScaleFix = WidgetTheme.fontScaleFix(context),
    )
    CompositionLocalProvider(
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

    /** Önbellek ya da ayarlar değişince ana ekrandaki tüm widget'ları yeniden çizer. */
    suspend fun updateAll(context: Context) {
        WidgetKind.entries.forEach { kind ->
            runCatching { kind.widget().updateAll(context) }.onFailure { Log.w(TAG, "${kind.name} çizilemedi", it) }
        }
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
            .let { code -> "$code:${locale(context).toLanguageTag()}:$PREVIEWS_REVISION".hashCode() }
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
