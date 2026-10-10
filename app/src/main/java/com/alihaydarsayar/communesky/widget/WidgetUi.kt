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
import androidx.glance.text.TextAlign
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

    /** Saat rakamlarının yazı tipi: kullanıcı seçmediyse stilin kendi yazı tipi. */
    val clockFont: ClockFont get() = style.clockFont ?: styleId.clockFont

    /** "18:03" yazısının genişliği, yazı boyutu cinsinden (dar rakamlar daha az yer tutar). */
    val clockEms: Float get() = if (clockFont == ClockFont.Digits || clockFont == ClockFont.DigitsOutline) 2.35f else 2.6f

    val hourColor: Color get() = style.hourColor?.let { Color(it) } ?: colors.text
    val minuteColor: Color get() = style.minuteColor?.let { Color(it) } ?: hourColor

    fun shows(content: WidgetContent): Boolean = style.shows(kind, content)

    /** Arka planın görünürlüğü (0–1), saydamlık kaydırıcısından. */
    val backgroundAlpha: Float get() = (100 - style.effectiveTransparency) / 100f

    companion object {
        fun fontScaleFix(context: Context): Float {
            val scale = context.resources.configuration.fontScale
            return min(scale, 1.15f) / scale
        }
    }
}

val LocalWidgetTheme = staticCompositionLocalOf<WidgetTheme> { error("Widget teması verilmedi") }

enum class WWeight { Light, Regular, Medium, Bold }

/** Yazının rolü: düz yazı, dar kalın rakam ya da dar ince rakam. */
enum class WFont { Text, Digits, Outline }

private val NUMERIC = Regex("^[-−]?[0-9][0-9.,]*[°%]?$|^%[0-9]+$")

/** Sıcaklık ve sayılar ("16°", "1022", "%64") dar ve kalın rakamlarla çizilir; çok küçük yazılar hariç. */
fun isNumeric(text: String, sizeSp: Float): Boolean = sizeSp >= 12f && NUMERIC.matches(text)

/** Düzen anahtarı (bkz. [WidgetLayouts]): kalınlık seçimi ve rol birlikte. */
fun fontKey(style: WidgetStyle, weight: WWeight, font: WFont): String {
    if (font == WFont.Digits) return "dg"
    if (font == WFont.Outline) return "ol"
    val step = when (weight) {
        WWeight.Light -> 0
        WWeight.Regular -> 1
        WWeight.Medium -> 2
        WWeight.Bold -> 3
    } + when (style.weight) {
        WeightChoice.Thin -> -1
        WeightChoice.Normal -> 0
        WeightChoice.Bold -> 1
    }
    return "s" + "3457"[step.coerceIn(0, 3)]
}

/**
 * Widget yazısı. Glance'in kendi yazısı dar rakamları ve gölgeyi desteklemediği için Android'in
 * TextView'ı (RemoteViews) kullanılır: sistemin yazı tipi, sayılar dar ve kalın rakamlarla,
 * duvar kâğıdının üstünde ([WidgetColors.shadow]) gölgeli.
 *
 * Uygulamanın kendi yazı tipi dosyaları (res/font) burada kullanılamaz: widget'ı launcher çizer ve
 * Android ona başka bir uygulamanın yazı tipini yükletmez (kısıtlı bağlam). Önizleme ile ana ekran
 * aynı görünsün diye her yerde sistem yazı tipleri kullanılır.
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
    font: WFont? = null,
) {
    val theme = LocalWidgetTheme.current
    val context = LocalContext.current
    val role = font ?: if (isNumeric(text, size)) WFont.Digits else WFont.Text
    val gravity = when (align) {
        TextAlign.End, TextAlign.Right -> Gravity.END
        TextAlign.Center -> Gravity.CENTER_HORIZONTAL
        else -> Gravity.START
    }
    val views = RemoteViews(context.packageName, WidgetLayouts.text(fontKey(theme.style, weight, role), theme.colors.shadowKind)).apply {
        setTextViewText(R.id.widget_text, text)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, theme.sp(size).value)
        setTextColor(R.id.widget_text, (color ?: theme.colors.text).toArgb())
        // Çok satırlı yazının hizası. Android 12 öncesinde bu çağrı widget'larda desteklenmez (widget hata verir);
        // orada yazı kutusu içeriği kadar yer kaplar ve hizayı dıştaki kutu verir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setInt(R.id.widget_text, "setGravity", gravity or Gravity.CENTER_VERTICAL)
        if (maxLines != 1) setInt(R.id.widget_text, "setMaxLines", maxLines)
    }
    val alignment = when (align) {
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Box(modifier, contentAlignment = alignment) { AndroidRemoteViews(views) }
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
        condition == WeatherCondition.Clear -> if (isNight) colors.moon else colors.sun
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
        BackgroundKind.Glass -> root.rounded(Color.White.copy(alpha = WidgetColors.GLASS_ALPHA * alpha), radius)
        BackgroundKind.Transparent -> if (alpha > 0f) root.rounded(WidgetColors.TransparentTint.copy(alpha = 0.75f * alpha), radius) else root
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
        // Hafif perde: yazının arkasında kenarları yumuşak, yarı saydam bir katman.
        if (theme.colors.scrim) {
            val size = LocalSize.current
            val density = LocalContext.current.resources.displayMetrics.density
            Image(
                ImageProvider(WidgetBitmaps.scrim(size.width.value, size.height.value, density, WidgetColors.scrimColor(theme.colors.darkText))),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = androidx.glance.layout.ContentScale.FillBounds,
            )
        }
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

/** "9 km", "2.747 km" (dile göre binlik ayırıcı); 1 km'nin altında "<1 km". */
fun distanceText(context: Context, km: Double): String =
    if (km < 1) {
        context.getString(R.string.widget_distance_under_one)
    } else {
        context.getString(R.string.widget_distance_km, java.text.NumberFormat.getIntegerInstance(locale(context)).format(km.roundToInt()))
    }

// --- Saat (Android'in kendi bileşenleri) ------------------------------------------------------------

enum class ClockPart { Time, Hours, Minutes, Date }

/**
 * Android'in TextClock bileşeni: dakika (ve gün) değişince sistem kendisi günceller, uygulama hiç
 * uyanmaz, alarm kurulmaz. Dokununca telefonun saat/alarm uygulaması açılır.
 *
 * Saat ve dakika ayrı renkteyse ya da yazı tipi "dolu + çizgi" ise saat iki ayrı TextClock olarak
 * çizilir (tek tek rakam renklendirilemez: bunun için uygulamanın dakikada bir uyanması gerekirdi).
 *
 * [dateSkeleton]: tarih için dile göre biçim iskeleti ("EEEEdMMMM" → "9 Ekim Cuma").
 */
@Composable
fun WClock(
    part: ClockPart,
    size: Float,
    modifier: GlanceModifier = GlanceModifier,
    weight: WWeight = WWeight.Medium,
    color: Color? = null,
    dateSkeleton: String = "EEEEdMMMM",
    uppercase: Boolean = false,
    contentAlignment: Alignment = Alignment.CenterStart,
    font: WFont? = null,
) {
    val theme = LocalWidgetTheme.current
    val split = part == ClockPart.Time && color == null &&
        (theme.clockFont == ClockFont.DigitsOutline || theme.hourColor != theme.minuteColor)
    if (split) {
        // Satırın yüksekliği açıkça verilir: aksi halde iki nokta resmi satırı kısaltıp rakamları kesiyor.
        val rowHeight = theme.sp(size).value * LocalContext.current.resources.configuration.fontScale * 1.3f
        Box(modifier, contentAlignment = contentAlignment) {
            Row(GlanceModifier.height(rowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
                ClockView(ClockPart.Hours, size, weight, null, dateSkeleton, uppercase, font, hour12 = "h")
                ClockColon(size, theme.hourColor)
                ClockView(ClockPart.Minutes, size, weight, null, dateSkeleton, uppercase, font)
            }
        }
    } else {
        Box(modifier, contentAlignment = contentAlignment) { ClockView(part, size, weight, color, dateSkeleton, uppercase, font) }
    }
}

/** Saat ile dakika arasındaki iki nokta: saat renginde iki küçük daire. */
@Composable
fun ClockColon(size: Float, color: Color) {
    val theme = LocalWidgetTheme.current
    val height = theme.sp(size).value * LocalContext.current.resources.configuration.fontScale * 0.5f
    Image(
        ImageProvider(R.drawable.widget_clock_colon),
        contentDescription = null,
        modifier = GlanceModifier.width((height * 0.36f).dp).height(height.dp),
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
}

@Composable
private fun ClockView(
    part: ClockPart,
    size: Float,
    weight: WWeight,
    color: Color?,
    dateSkeleton: String,
    uppercase: Boolean,
    font: WFont?,
    hour12: String = "hh",
) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val key = when {
        font != null -> fontKey(theme.style, weight, font)
        part == ClockPart.Date -> fontKey(theme.style, weight, WFont.Text)
        else -> when (theme.clockFont) {
            ClockFont.System -> fontKey(theme.style, weight, WFont.Text)
            ClockFont.Digits -> "dg"
            ClockFont.DigitsOutline -> if (part == ClockPart.Minutes) "ol" else "dg"
        }
    }
    val (format12, format24) = when (part) {
        ClockPart.Time -> "h:mm" to "HH:mm"
        ClockPart.Hours -> hour12 to "HH"
        ClockPart.Minutes -> "mm" to "mm"
        ClockPart.Date -> DateFormat.getBestDateTimePattern(locale(context), dateSkeleton).let { it to it }
    }
    val textColor = color ?: when (part) {
        ClockPart.Minutes -> theme.minuteColor
        ClockPart.Date -> theme.colors.text
        else -> theme.hourColor
    }
    val views = RemoteViews(context.packageName, WidgetLayouts.clock(key, theme.colors.shadowKind, uppercase)).apply {
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, theme.sp(size).value)
        setTextColor(R.id.clock, textColor.toArgb())
        // Biçimi ayrıca vermek saatin ilk çizimde de dolu gelmesini sağlar.
        setCharSequence(R.id.clock, "setFormat12Hour", format12)
        setCharSequence(R.id.clock, "setFormat24Hour", format24)
        setOnClickPendingIntent(R.id.clock, clockAppIntent(context))
    }
    AndroidRemoteViews(views)
}

/**
 * Saate dokununca telefonun saat/alarm uygulaması açılır; böyle bir uygulama yoksa Commune Sky.
 */
fun clockAppIntent(context: Context): PendingIntent {
    val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val intent = if (alarms.resolveActivity(context.packageManager) != null) {
        alarms
    } else {
        Intent(context, com.alihaydarsayar.communesky.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

@Composable
fun VSpace(height: Dp) = Spacer(GlanceModifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(GlanceModifier.width(width))

/** Bu boyuttan büyük yazılar yazı dolgusu olmadan (sıkı satır yüksekliğiyle) çizilir. */
const val TIGHT_TEXT_SP = 26f

/** Sistem yazı tipinin satır yüksekliği (em). */
private const val LINE_EM = 1.2f

/**
 * Yazının yaklaşık genişliği (dp): ortalama harf genişliği ~0,55 em, dar rakamlarda ~0,5 em.
 * Yazının kesilmemesi için boyut seçerken kullanılır.
 */
fun estimateWidthDp(text: String, sizeSp: Float, theme: WidgetTheme): Float {
    val em = if (isNumeric(text, 12f)) 0.5f else 0.55f
    return text.length * sizeSp * em * theme.style.textSize.scale * theme.fontScaleFix
}

/** [text] [availableDp] genişliğe sığacak en büyük boyut, [max]'ı geçmeden. */
fun fitWidth(text: String, max: Float, availableDp: Float, theme: WidgetTheme): Float {
    val atOne = estimateWidthDp(text, 1f, theme)
    return if (atOne <= 0f) max else min(max, availableDp / atOne)
}

/** Sıkı (yazı dolgusuz) bir satırın yüksekliği (dp). */
fun lineHeightDp(sizeSp: Float, theme: WidgetTheme): Float =
    sizeSp * theme.style.textSize.scale * theme.fontScaleFix * LINE_EM + 2f

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
