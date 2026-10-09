package com.alihaydarsayar.communesky.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import kotlin.math.min
import kotlin.math.roundToInt

/** Çizilen widget'ın ortak teması: stil, renkler ve yazı ölçeği. */
data class WidgetTheme(
    val kind: WidgetKind,
    val styleId: WidgetStyleId,
    val style: WidgetStyle,
    val colors: WidgetColors,
    /** Sistem yazı boyutu çok büyükse widget düzeni bozulmasın diye ölçek en fazla 1,15 sayılır. */
    val fontScaleFix: Float,
) {
    fun sp(base: Float): TextUnit = (base * style.textSize.scale * fontScaleFix).sp

    fun shows(content: WidgetContent): Boolean = style.shows(kind, content)

    /** Arka planın görünürlüğü (0–1), saydamlık kaydırıcısından. */
    val backgroundAlpha: Float get() = (100 - style.transparency.coerceIn(0, 100)) / 100f

    companion object {
        fun fontScaleFix(context: Context): Float {
            val scale = context.resources.configuration.fontScale
            return min(scale, 1.15f) / scale
        }
    }
}

val LocalWidgetTheme = staticCompositionLocalOf<WidgetTheme> { error("Widget teması verilmedi") }

enum class WWeight { Light, Regular, Medium, Bold }

/**
 * Widget yazısı, sistem yazı tipiyle. Duvar kâğıdının üstünde ([WidgetColors.shadow]) Glance'in
 * kendi yazısı gölge desteklemediği için Android'in gölgeli TextView'ı (RemoteViews) kullanılır;
 * böylece açık duvar kâğıdında da okunur.
 */
@Composable
fun WText(
    text: String,
    size: Float,
    modifier: GlanceModifier = GlanceModifier,
    color: Color? = null,
    weight: WWeight = WWeight.Regular,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    val theme = LocalWidgetTheme.current
    val textColor = color ?: theme.colors.text
    val fontSize = theme.sp(size)
    // Büyük rakamlar ve gölgeli yazılar Android TextView ile: yazı dolgusu olmadan, tasarımdaki gibi sıkı.
    if (theme.colors.shadow || size >= TIGHT_TEXT_SP) {
        val context = LocalContext.current
        val layout = when (weight) {
            WWeight.Light -> if (theme.colors.shadow) R.layout.widget_text_shadow_light else R.layout.widget_text_plain_light
            WWeight.Regular -> if (theme.colors.shadow) R.layout.widget_text_shadow_regular else R.layout.widget_text_plain_regular
            WWeight.Medium -> if (theme.colors.shadow) R.layout.widget_text_shadow_medium else R.layout.widget_text_plain_medium
            WWeight.Bold -> if (theme.colors.shadow) R.layout.widget_text_shadow_bold else R.layout.widget_text_plain_bold
        }
        val gravity = when (align) {
            TextAlign.End, TextAlign.Right -> Gravity.END
            TextAlign.Center -> Gravity.CENTER_HORIZONTAL
            else -> Gravity.START
        }
        val views = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_text, text)
            setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            setTextColor(R.id.widget_text, textColor.toArgb())
            setInt(R.id.widget_text, "setGravity", gravity or Gravity.CENTER_VERTICAL)
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
                color = ColorProvider(textColor),
                fontSize = fontSize,
                fontWeight = when (weight) {
                    WWeight.Medium -> FontWeight.Medium
                    WWeight.Bold -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                fontFamily = if (weight == WWeight.Light) FontFamily("sans-serif-light") else null,
                textAlign = align,
            ),
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

/** Tek renkli çizgi ikon (res/drawable/ic_wl_*.xml), istenen renge boyanır. */
@Composable
fun WIcon(@DrawableRes res: Int, size: Dp, tint: Color? = null, modifier: GlanceModifier = GlanceModifier, description: String? = null) {
    val color = tint ?: LocalWidgetTheme.current.colors.text
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
}

/** Hava ikonu: açık havada güneş/ay sarısı, yağışta yağmur mavisi, diğerlerinde yazı rengi. */
@Composable
fun WeatherGlyph(condition: WeatherCondition, isNight: Boolean, size: Dp, modifier: GlanceModifier = GlanceModifier) {
    val colors = LocalWidgetTheme.current.colors
    val tint = when {
        condition == WeatherCondition.Clear -> colors.sun
        condition.isWet -> colors.rain
        else -> colors.text
    }
    WIcon(condition.lineIconRes(isNight), size, tint, modifier)
}

@DrawableRes
fun WeatherCondition.lineIconRes(isNight: Boolean): Int = when (this) {
    WeatherCondition.Clear -> if (isNight) R.drawable.ic_wl_clear_night else R.drawable.ic_wl_clear_day
    WeatherCondition.PartlyCloudy -> if (isNight) R.drawable.ic_wl_partly_cloudy_night else R.drawable.ic_wl_partly_cloudy_day
    WeatherCondition.Cloudy -> R.drawable.ic_wl_cloudy
    WeatherCondition.Fog -> R.drawable.ic_wl_fog
    WeatherCondition.Drizzle -> R.drawable.ic_wl_drizzle
    WeatherCondition.Rain -> R.drawable.ic_wl_rain
    WeatherCondition.HeavyRain -> R.drawable.ic_wl_heavy_rain
    WeatherCondition.Snow -> R.drawable.ic_wl_snow
    WeatherCondition.Thunderstorm -> R.drawable.ic_wl_thunderstorm
}

/** Şu anki hava ikonu. */
@Composable
fun CurrentGlyph(snapshot: WeatherSnapshot, size: Dp, modifier: GlanceModifier = GlanceModifier) {
    val current = snapshot.forecast.current
    WeatherGlyph(current.condition, !current.isDay, size, modifier)
}

/** Yuvarlak köşeli düz renk; Android'in her sürümünde çalışır (şekil + boya + saydamlık). */
fun GlanceModifier.rounded(color: Color, radius: Dp): GlanceModifier = background(
    imageProvider = ImageProvider(shapeRes(radius)),
    alpha = color.alpha,
    colorFilter = ColorFilter.tint(ColorProvider(color.copy(alpha = 1f))),
)

@DrawableRes
private fun shapeRes(radius: Dp): Int = when {
    radius <= 4.dp -> R.drawable.widget_shape_4
    radius <= 8.dp -> R.drawable.widget_shape_8
    radius <= 12.dp -> R.drawable.widget_shape_12
    radius <= 16.dp -> R.drawable.widget_shape_16
    radius <= 20.dp -> R.drawable.widget_shape_20
    radius <= 28.dp -> R.drawable.widget_shape_28
    radius <= 40.dp -> R.drawable.widget_shape_40
    else -> R.drawable.widget_shape_pill
}

@DrawableRes
private fun strokeRes(radius: Dp): Int = when {
    radius <= 16.dp -> R.drawable.widget_stroke_16
    radius <= 28.dp -> R.drawable.widget_stroke_28
    else -> R.drawable.widget_stroke_40
}

/**
 * Widget'ın kök yüzeyi: seçilen arka plan (gökyüzü gradyanı, cam, düz renk, saydam), saydamlık
 * ve köşe yuvarlaklığı. Android 12+ köşeyi sistem keser; öncesinde köşeler çizimde yuvarlanır.
 *
 * [gradient]: gökyüzü arka planında kullanılacak renkler (varsayılan: temanın paleti).
 */
@Composable
fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    horizontal: Dp = 18.dp,
    vertical: Dp = 14.dp,
    onClick: Action? = null,
    gradient: List<Color>? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    /** false: arka planı içerik kendisi çizer (ikiye bölünmüş widget); sadece köşe ve tıklama. */
    drawBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val theme = LocalWidgetTheme.current
    val style = theme.style
    val alpha = theme.backgroundAlpha
    val radius = style.corners.radius
    var root = GlanceModifier.fillMaxSize().appWidgetBackground()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) root = root.cornerRadius(radius)
    if (!drawBackground) {
        if (onClick != null) root = root.clickable(onClick)
        Box(root.then(modifier), contentAlignment = contentAlignment) { content() }
        return
    }
    root = when (style.background) {
        BackgroundKind.Sky -> if (alpha > 0f) root.background(
            imageProvider = ImageProvider(gradientBitmap(gradient ?: theme.colors.palette.gradient, radius)),
            alpha = alpha,
        ) else root
        BackgroundKind.Solid -> if (alpha > 0f) root.rounded(theme.colors.palette.solid.copy(alpha = alpha), radius) else root
        BackgroundKind.Glass -> root.rounded(Color.White.copy(alpha = 0.4f * alpha), radius)
        BackgroundKind.Transparent -> if (alpha > 0f) root.rounded(Color(0xFF0B1026).copy(alpha = 0.75f * alpha), radius) else root
    }
    if (onClick != null) root = root.clickable(onClick)
    var inner = GlanceModifier.fillMaxSize()
    if (style.background == BackgroundKind.Glass && alpha > 0f) {
        inner = inner.background(
            imageProvider = ImageProvider(strokeRes(radius)),
            alpha = (0.26f + 0.3f * alpha).coerceAtMost(0.5f),
        )
    }
    Box(root.then(modifier)) {
        Box(inner.padding(horizontal = horizontal, vertical = vertical), contentAlignment = contentAlignment) { content() }
    }
}

/** Gökyüzü gradyanı, widget boyutunda. Android 12 öncesinde köşeler resimde yuvarlanır. */
@Composable
fun gradientBitmap(
    colors: List<Color>,
    radius: Dp,
    widthDp: Float = LocalSize.current.width.value,
    heightDp: Float = LocalSize.current.height.value,
    roundLeft: Boolean = true,
    roundRight: Boolean = true,
    roundBottom: Boolean = true,
): android.graphics.Bitmap {
    val context = LocalContext.current
    val corner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0f else radius.value
    return WidgetBitmaps.gradient(
        colors, widthDp, heightDp, context.resources.displayMetrics.density, corner,
        roundLeft = roundLeft, roundRight = roundRight, roundBottom = roundBottom,
    )
}

@Composable
fun HDivider(modifier: GlanceModifier = GlanceModifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalWidgetTheme.current.colors.divider)) {}
}

@Composable
fun VDivider(modifier: GlanceModifier = GlanceModifier.fillMaxHeight(), width: Dp = 1.dp) {
    Box(modifier.width(width).background(LocalWidgetTheme.current.colors.divider)) {}
}

/** Yuvarlak hap: "Tuzla Merkez'desin · 3 km · hava aynı". */
@Composable
fun Pill(modifier: GlanceModifier = GlanceModifier, color: Color? = null, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .rounded(color ?: LocalWidgetTheme.current.colors.pill, 999.dp)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Yer adı; Ev ise ev işareti, bulunduğun yerse konum işareti önünde. */
@Composable
fun PlaceLabel(
    data: WidgetPlaceData,
    size: Float,
    modifier: GlanceModifier = GlanceModifier,
    weight: WWeight = WWeight.Medium,
    color: Color? = null,
    suffix: String? = null,
    iconAfter: Boolean = false,
    uppercase: Boolean = false,
) {
    val context = LocalContext.current
    val icon = placeIcon(data)
    val iconSize = (size * 0.95f).dp
    var name = data.snapshot.displayName(context)
    if (uppercase) name = name.uppercase(context.resources.configuration.locales[0])
    if (suffix != null) name += suffix
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null && !iconAfter) {
            WIcon(icon, iconSize, color, description = context.getString(if (data.isHome) R.string.home_place else R.string.current_location))
            Spacer(GlanceModifier.width(5.dp))
        }
        WText(name, size, weight = weight, color = color)
        if (icon != null && iconAfter) {
            Spacer(GlanceModifier.width(5.dp))
            WIcon(icon, iconSize, color)
        }
    }
}

@DrawableRes
fun placeIcon(data: WidgetPlaceData): Int? = when {
    data.isHome -> R.drawable.ic_wl_home
    data.isCurrentLocation -> R.drawable.ic_wl_pin
    else -> null
}

// --- Metin biçimleri -----------------------------------------------------------------------------

/** "17°" (kullanıcının birimiyle). */
@Composable
fun temp(celsius: Double): String = "${LocalAppSettings.current.temperatureUnit.fromCelsius(celsius).roundToInt()}°"

@Composable
fun tempValue(celsius: Double): Int = LocalAppSettings.current.temperatureUnit.fromCelsius(celsius).roundToInt()

/** "Y 21° · D 14°". */
@Composable
fun highLow(max: Double, min: Double): String =
    LocalContext.current.getString(R.string.widget_high_low, temp(max), temp(min))

/** "18° / 6°". */
@Composable
fun highSlashLow(max: Double, min: Double): String = "${temp(max)} / ${temp(min)}"

fun locale(context: Context) = context.resources.configuration.locales[0]

/** "21:00" ya da "9:00 PM", telefonun ayarına göre. */
fun timeFormatter(context: Context): DateTimeFormatter {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), skeleton), locale(context))
}

/** Saat ekseni: "20" ya da "8 PM". */
fun hourFormatter(context: Context): DateTimeFormatter {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH" else DateFormat.getBestDateTimePattern(locale(context), "h a")
    return DateTimeFormatter.ofPattern(pattern, locale(context))
}

/** "Cmt", "Paz"; bugün için "Bugün". */
fun dayName(context: Context, date: LocalDate, today: LocalDate): String =
    if (date == today) {
        context.getString(R.string.today)
    } else {
        date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale(context)).replaceFirstChar { it.titlecase(locale(context)) }
    }

/** "20:00'de" (Türkçe) ya da "at 20:00". */
fun atTime(context: Context, time: LocalDateTime): String {
    val text = time.format(timeFormatter(context))
    return if (locale(context).language == "tr") {
        com.alihaydarsayar.communesky.model.TurkishSuffix.locativeTime(text)
    } else {
        context.getString(R.string.widget_at_time, text)
    }
}

/** "11 sa 39 dk". */
fun durationText(context: Context, minutes: Long): String {
    val total = minutes.coerceAtLeast(0)
    return if (total >= 60) {
        context.getString(R.string.widget_duration_hm, total / 60, total % 60)
    } else {
        context.getString(R.string.widget_duration_m, total)
    }
}

/** "9 km"; 1 km'nin altında "<1 km". */
fun distanceText(context: Context, km: Double): String =
    if (km < 1) context.getString(R.string.widget_distance_under_one) else context.getString(R.string.widget_distance_km, km.roundToInt())

// --- Saat (Android'in kendi bileşenleri) ------------------------------------------------------------

enum class ClockPart { Time, Hours, Minutes, Date }

/**
 * Android'in TextClock bileşeni: dakika (ve gün) değişince sistem kendisi günceller, uygulama hiç
 * uyanmaz, alarm kurulmaz. Dokununca telefonun saat/alarm uygulaması açılır.
 *
 * [dateSkeleton]: tarih için dile göre biçim iskeleti ("EEEEdMMMM" → "9 Ekim Cuma").
 */
@Composable
fun WClock(
    part: ClockPart,
    size: Float,
    modifier: GlanceModifier = GlanceModifier,
    weight: WWeight = WWeight.Light,
    color: Color? = null,
    dateSkeleton: String = "EEEEdMMMM",
    uppercase: Boolean = false,
    contentAlignment: Alignment = Alignment.CenterStart,
) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val shadow = theme.colors.shadow
    val layout = when (weight) {
        WWeight.Light -> if (shadow) R.layout.widget_clock_light_shadow else R.layout.widget_clock_light
        WWeight.Regular -> if (shadow) R.layout.widget_clock_regular_shadow else R.layout.widget_clock_regular
        WWeight.Medium -> when {
            uppercase -> R.layout.widget_clock_medium_caps
            shadow -> R.layout.widget_clock_medium_shadow
            else -> R.layout.widget_clock_medium
        }
        WWeight.Bold -> if (shadow) R.layout.widget_clock_black_shadow else R.layout.widget_clock_black
    }
    val (format12, format24) = when (part) {
        ClockPart.Time -> "h:mm" to "HH:mm"
        ClockPart.Hours -> "hh" to "HH"
        ClockPart.Minutes -> "mm" to "mm"
        ClockPart.Date -> DateFormat.getBestDateTimePattern(locale(context), dateSkeleton).let { it to it }
    }
    val views = RemoteViews(context.packageName, layout).apply {
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, theme.sp(size).value)
        setTextColor(R.id.clock, (color ?: theme.colors.text).toArgb())
        // Biçimi ayrıca vermek saatin ilk çizimde de dolu gelmesini sağlar.
        setCharSequence(R.id.clock, "setFormat12Hour", format12)
        setCharSequence(R.id.clock, "setFormat24Hour", format24)
        setOnClickPendingIntent(R.id.clock, clockAppIntent(context))
    }
    Box(modifier, contentAlignment = contentAlignment) { AndroidRemoteViews(views) }
}

/** Kadranlı saat (AnalogClock): akrep beyaz, yelkovan açık mavi. Sistem çizer ve günceller. */
@Composable
fun WAnalogClock(modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_clock_analog).apply {
        setOnClickPendingIntent(R.id.clock, clockAppIntent(context))
    }
    Box(modifier, contentAlignment = Alignment.Center) { AndroidRemoteViews(views) }
}

fun clockAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

@Composable
fun VSpace(height: Dp) = Spacer(GlanceModifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(GlanceModifier.width(width))

/** Bu boyuttan büyük yazılar yazı dolgusu olmadan (sıkı satır yüksekliğiyle) çizilir. */
const val TIGHT_TEXT_SP = 26f

/**
 * Yazının yaklaşık genişliği (dp): sistem yazı tipinde ortalama harf genişliği ~0,55 em. Yazının
 * kesilmemesi için boyut seçerken kullanılır.
 */
fun estimateWidthDp(text: String, sizeSp: Float, theme: WidgetTheme): Float =
    text.length * sizeSp * 0.55f * theme.style.textSize.scale * theme.fontScaleFix

/** [text] [availableDp] genişliğe sığacak en büyük boyut, [max]'ı geçmeden. */
fun fitWidth(text: String, max: Float, availableDp: Float, theme: WidgetTheme): Float {
    val atOne = estimateWidthDp(text, 1f, theme)
    return if (atOne <= 0f) max else min(max, availableDp / atOne)
}

/** Sıkı (yazı dolgusuz) bir satırın yüksekliği (dp). */
fun lineHeightDp(sizeSp: Float, theme: WidgetTheme): Float =
    sizeSp * theme.style.textSize.scale * theme.fontScaleFix * (if (sizeSp >= TIGHT_TEXT_SP) 1.2f else 1.35f)

/** Büyük sıcaklık yazısı: hem genişliğe hem yüksekliğe sığan en büyük boyut. */
fun fitTemperature(text: String, max: Float, widthDp: Float, heightDp: Float, theme: WidgetTheme): Float =
    min(fitWidth(text, max, widthDp, theme), heightDp / (1.2f * theme.style.textSize.scale * theme.fontScaleFix))
        .coerceAtLeast(18f)

/** Bu yükseklikten (dp) alçak widget'lar tek satıra düşer. */
const val TINY_HEIGHT = 64f

/**
 * En küçük boyutlar için ortak tek satır: ikon, sıcaklık, yer adı ve [extra]. Sığmayan parça
 * gösterilmez; hiçbir yazı kesilmez.
 */
@Composable
fun TinyRow(data: WidgetPlaceData, extra: String? = null, extraColor: Color? = null, showName: Boolean = true) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val icon = min(24f, size.height.value - 16f).coerceAtLeast(14f)
    val tempSize = fitTemperature(temperature, 24f, size.width.value * 0.4f, size.height.value - 12f, theme)
    var left = size.width.value - 24f - icon - 6f - estimateWidthDp(temperature, tempSize, theme)
    val name = data.snapshot.displayName(context)
    val nameWidth = estimateWidthDp(name, 13f, theme) + 8f + (if (placeIcon(data) != null) 18f else 0f)
    val withName = showName && nameWidth <= left
    if (withName) left -= nameWidth
    val withExtra = extra != null && estimateWidthDp("  ·  $extra", 13f, theme) <= left
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 4.dp, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrentGlyph(data.snapshot, icon.dp)
            HSpace(6.dp)
            WText(temperature, tempSize, weight = WWeight.Light)
            if (withName) {
                HSpace(8.dp)
                PlaceLabel(data, 13f, weight = WWeight.Regular, color = theme.colors.secondary)
            }
            if (withExtra) WText("  ·  $extra", 13f, color = extraColor ?: theme.colors.secondary)
        }
    }
}
