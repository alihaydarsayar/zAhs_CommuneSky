package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.widgetBackgroundRes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Widget'lar, Hilt'in yönettiği depolara bu "giriş noktası" üzerinden ulaşır. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun placesRepository(): PlacesRepository
    fun settingsRepository(): SettingsRepository
}

/** Widget'ta gösterilecek bir yer: önbellekteki havası ve Ev olup olmadığı. */
data class WidgetPlaceData(val snapshot: WeatherSnapshot, val isHome: Boolean)

/** Widget'ların çizimden önce önbellekten okuduğu her şey. Hiçbiri internete çıkmaz. */
class WidgetDataLoader(context: Context) {
    private val entryPoint = EntryPointAccessors
        .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    private val weather = entryPoint.weatherRepository()
    private val places = entryPoint.placesRepository()

    suspend fun settings(): AppSettings = entryPoint.settingsRepository().settings.first()

    suspend fun savedPlaces(): List<SavedPlace> = places.all()

    suspend fun device(): WeatherSnapshot? = weather.snapshot(DEVICE_PLACE_ID)

    suspend fun home(): WidgetPlaceData? {
        val home = places.all().firstOrNull { it.isHome } ?: return null
        return weather.snapshot(home.id)?.let { WidgetPlaceData(it, isHome = true) }
    }

    /** Ayardaki yer; yer silinmişse ya da Ev seçilmemişse cihaz konumuna döner. */
    suspend fun place(place: WidgetPlace): WidgetPlaceData? {
        val saved = places.all()
        val id = when (place) {
            WidgetPlace.Device -> DEVICE_PLACE_ID
            WidgetPlace.Home -> saved.firstOrNull { it.isHome }?.id ?: DEVICE_PLACE_ID
            is WidgetPlace.Saved -> place.placeId.takeIf { id -> saved.any { it.id == id } } ?: DEVICE_PLACE_ID
        }
        val snapshot = weather.snapshot(id) ?: weather.snapshot(DEVICE_PLACE_ID) ?: return null
        return WidgetPlaceData(snapshot, isHome = saved.any { it.id == snapshot.placeId && it.isHome })
    }
}

/** Bu widget'ın ayarı. Önizleme ve testlerde (gerçek widget kimliği yokken) varsayılan ayar. */
suspend fun widgetConfig(context: Context, id: GlanceId): WidgetConfig {
    val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        ?: return WidgetConfig()
    return WidgetConfigStore.get(context).get(appWidgetId)
}

/** Çizilen widget'ın arka plan türü; yazılar buna göre gölgeli ya da düz çizilir. */
val LocalWidgetBackground = compositionLocalOf { WidgetBackground.Sky }

val WidgetWhite = Color.White
val WidgetWhiteSecondary = Color.White.copy(alpha = 0.78f)
val WidgetRainBlue = Color(0xFFB5E3FF)

enum class WidgetFont { Regular, Light, Medium }

/** Widget'ın kök arka planı: gökyüzü gradyanı, yarı saydam cam ya da hiçbiri. */
fun GlanceModifier.widgetBackground(background: WidgetBackground, theme: SkyTheme): GlanceModifier {
    val base = appWidgetBackground()
    return when (background) {
        WidgetBackground.Sky -> base.background(ImageProvider(theme.widgetBackgroundRes))
        WidgetBackground.Translucent -> base.background(ImageProvider(R.drawable.widget_bg_translucent))
        WidgetBackground.Transparent -> base
    }
}

/**
 * Widget yazısı. Saydam arka planda Glance'in kendi yazısı gölge desteklemediği için Android'in
 * gölgeli TextView'ı (RemoteViews) kullanılır; böylece açık duvar kâğıdında da okunur.
 */
@Composable
fun WText(
    text: String,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    color: Color = WidgetWhite,
    font: WidgetFont = WidgetFont.Regular,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    if (LocalWidgetBackground.current == WidgetBackground.Transparent) {
        val context = LocalContext.current
        // Duvar kâğıdı açık renkli olabilir: soluk (yarı saydam) yazılar burada neredeyse tam beyaz olsun.
        val solid = if (color.alpha < 0.9f) color.copy(alpha = 0.92f) else color
        val layout = when (font) {
            WidgetFont.Regular -> R.layout.widget_text_shadow_regular
            WidgetFont.Light -> R.layout.widget_text_shadow_light
            WidgetFont.Medium -> R.layout.widget_text_shadow_medium
        }
        val views = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_text, text)
            setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size.value)
            setTextColor(R.id.widget_text, solid.toArgb())
            if (maxLines != 1) setInt(R.id.widget_text, "setMaxLines", maxLines)
        }
        val alignment = when (align) {
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
        Box(modifier, contentAlignment = alignment) { AndroidRemoteViews(views) }
    } else {
        Text(
            text,
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = size,
                fontWeight = if (font == WidgetFont.Medium) FontWeight.Medium else FontWeight.Normal,
                fontFamily = if (font == WidgetFont.Light) FontFamily("sans-serif-light") else null,
                textAlign = align,
            ),
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

@Composable
fun temperatureText(celsius: Double): String =
    "${LocalAppSettings.current.temperatureUnit.fromCelsius(celsius).roundToInt()}°"

@Composable
fun highLowText(context: Context, max: Double, min: Double): String {
    val unit = LocalAppSettings.current.temperatureUnit
    return context.getString(R.string.high_low, unit.fromCelsius(max).roundToInt(), unit.fromCelsius(min).roundToInt())
}

fun hourFormatter(context: Context): DateTimeFormatter {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "h a"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** Kayıtlı yerin adı; cihaz konumunda konumdan bulunan şehir adı. */
fun WeatherSnapshot.displayName(context: Context): String =
    city.name ?: context.getString(R.string.my_location)
