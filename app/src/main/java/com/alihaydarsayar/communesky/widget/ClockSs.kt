package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.min

/** "SS" analog saatinin renkleri ve hazır temaları. */
object SsClock {

    /** Kadran, mürekkep (çizgi, rakam, logo, kol), vurgu (yıldız, ay, saniye kolu) ve tarih penceresi. */
    data class Look(val dial: Int, val ink: Int, val accent: Int, val window: Int)

    enum class Preset(@param:StringRes val labelRes: Int, val look: Look) {
        Classic(R.string.widget_ss_classic, Look(0xFFF4F2EC.toInt(), 0xFF16171A.toInt(), 0xFFD93A3F.toInt(), 0xFFFFFFFF.toInt())),
        Dark(R.string.widget_theme_dark, Look(0xFF16171A.toInt(), 0xFFF4F2EC.toInt(), 0xFFD93A3F.toInt(), 0xFF16171A.toInt())),
        NightBlue(R.string.widget_ss_night_blue, Look(0xFF0F2233.toInt(), 0xFFEDE6D6.toInt(), 0xFFE8B04B.toInt(), 0xFF0F2233.toInt())),
    }

    /** Ayardaki renkler; seçilmeyenler "Klasik" temadan. */
    fun look(style: WidgetStyle): Look {
        val classic = Preset.Classic.look
        return Look(
            dial = style.dialColor ?: classic.dial,
            ink = style.numeralColor ?: classic.ink,
            accent = style.accent ?: classic.accent,
            window = style.windowColor ?: classic.window,
        )
    }

    /** Ayar şu an hazır temalardan biriyle aynıysa o tema. */
    fun preset(style: WidgetStyle): Preset? = Preset.entries.firstOrNull { it.look == look(style) }

    fun apply(style: WidgetStyle, preset: Preset): WidgetStyle = style.copy(
        dialColor = preset.look.dial,
        numeralColor = preset.look.ink,
        accent = preset.look.accent,
        windowColor = preset.look.window,
    )

    /** Kadranın çapı 400 birimdir; ölçüler tasarımdan (design/ss-saat). */
    const val UNITS = 400f

    /** Kollar 360 dp'lik kadrana göre tanımlı; saat bileşeni küçültür ama büyütmez. */
    const val MAX_DIAL_DP = 360f
}

/**
 * Tasarım "SS" (2×2, 3×3'e kadar): açık kadran, tırnaklı 3-6-9-12, çift halkalı SS mührü, katedral
 * tipi kollar, 3'ün solunda tarih penceresi, altta hava durumu.
 *
 * Tamamen resimsiz (bitmap'siz): kadran, üst üste boyanan vektör katmanlarıdır; renk değişince
 * yalnızca boya değişir. Kolları ve tarihi Android'in AnalogClock ve TextClock bileşenleri çizer;
 * uygulama saat için hiç uyanmaz.
 */
@Composable
fun ClockSsFace(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val style = theme.style
    val look = SsClock.look(style)
    val dialColor = Color(look.dial)
    val ink = Color(look.ink)
    val accent = Color(look.accent)
    val window = Color(look.window)
    val dial = min(size.width.value, size.height.value).coerceAtMost(SsClock.MAX_DIAL_DP)
    // Bir tasarım birimi kaç dp?
    val u = dial / SsClock.UNITS
    val lightDial = dialColor.luminance() > 0.45f

    val onDial = theme.colors.copy(
        darkText = lightDial,
        shadow = false,
        scrim = false,
        text = ink,
        secondary = ink.copy(alpha = 0.62f),
        tertiary = ink.copy(alpha = 0.5f),
        accent = accent,
        sun = if (lightDial) VividOnLight.Sun else WidgetInk.Sun,
        rain = if (lightDial) VividOnLight.Rain else WidgetInk.Rain,
        moon = if (lightDial) VividOnLight.Moon else WidgetInk.Moon,
    )
    WidgetSurface(horizontal = 0.dp, vertical = 0.dp, contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(dial.dp), contentAlignment = Alignment.Center) {
            // Kadranın sabit katmanları tek kutuda (Glance bir kutuda en çok 10 öğe çizer).
            Box(GlanceModifier.fillMaxSize().clickable(clockAction(context))) {
                Layer(R.drawable.widget_ss_dial_background, dialColor)
                Layer(R.drawable.widget_ss_dial_inner_ring, ink)
                Layer(R.drawable.widget_ss_dial_ink, ink)
                if (style.logo) {
                    Layer(R.drawable.widget_ss_dial_seal, ink)
                    Layer(R.drawable.widget_ss_dial_accent, accent)
                }
                if (style.showDate) {
                    Layer(R.drawable.widget_ss_dial_window, window)
                    Layer(R.drawable.widget_ss_dial_window_frame, ink)
                }
            }
            WithColors(onDial) {
                if (style.showDate) SsDate(u, accent, window, ink)
                SsWeather(data, u)
            }
            // Kollar en üstte; dokunmayı tutmaz, alttaki hava bilgisine ve kadrana geçer.
            AndroidRemoteViews(ssHands(context, style, ink, accent), GlanceModifier.fillMaxSize())
        }
    }
}

@Composable
private fun Layer(@DrawableRes drawable: Int, color: Color) {
    Image(
        ImageProvider(drawable),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
}

/**
 * Tarih penceresi (x 234–306, y 185–215): ay kısaltması vurgu renginde, gün numarası mürekkep
 * renginde. İkisi de TextClock: gece yarısı sistem kendisi değiştirir.
 */
@Composable
private fun SsDate(u: Float, accent: Color, window: Color, ink: Color) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    // Gün numarası pencerenin üstünde okunmalı: mürekkep rengi pencereyle aynı tondaysa karşıtı.
    val dayColor = if (Contrast.ratio(ink.toArgb(), window.toArgb()) >= 3.0) ink else if (window.luminance() > 0.45f) WidgetInk.Dark else Color.White
    val text = dpToBase(13.5f * u, theme, context)
    Box(GlanceModifier.fillMaxSize().padding(start = (234f * u).dp, top = (185f * u).dp)) {
        Box(GlanceModifier.width((72f * u).dp).height((30f * u).dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, text, weight = WWeight.Bold, color = accent, dateSkeleton = "MMM", uppercase = true, font = WFont.Text)
                WClock(ClockPart.Date, text, weight = WWeight.Bold, color = dayColor, dateSkeleton = "d", font = WFont.Text)
            }
        }
    }
}

/**
 * Hava durumu, 6'nın üstünde üç satır (taban çizgileri y 271, 293, 312; yazı 21, 14,5 ve 13,5 birim):
 * ikon, sıcaklık ve durum; "Y 19° · D 12° · Beşiktaş"; yağmur bekleniyorsa damla ve saat.
 * Her parça ayrı açılıp kapanır; hepsi kapalıysa alan tamamen kalkar. Sığmayan parça düşer.
 */
@Composable
private fun SsWeather(data: WidgetPlaceData, u: Float) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val forecast = data.snapshot.forecast
    val current = forecast.current
    val today = forecast.today(forecast.localNow())
    val showIcon = theme.shows(WidgetContent.WeatherIcon)
    val temperature = if (theme.shows(WidgetContent.Temperature)) temp(current.temperature) else null
    val condition = if (theme.shows(WidgetContent.Condition)) context.getString(weatherDescriptionRes(current.weatherCode)) else null
    val highLow = if (theme.shows(WidgetContent.HighLow) && today != null) highLow(today.maxTemperature, today.minTemperature) else null
    val place = if (theme.shows(WidgetContent.PlaceName)) data.snapshot.displayName(context) else null
    val rain = if (theme.shows(WidgetContent.RainAlert)) {
        WidgetPhrases.nextRain(data.snapshot)?.let { start ->
            if (start.isNow) WidgetPhrases.rainShort(context, data.snapshot) else context.getString(R.string.widget_rain_time, start.time.format(timeFormatter(context)))
        }
    } else {
        null
    }
    if (!showIcon && temperature == null && condition == null && highLow == null && place == null && rain == null) return

    val first = dpToBase(21f * u, theme, context)
    val second = dpToBase(14.5f * u, theme, context)
    val third = dpToBase(13.5f * u, theme, context)
    // Satırlar kadranın içinde kalmalı: bu yükseklikte kadranın genişliği yaklaşık 250 birim.
    val conditionFits = condition != null &&
        estimateWidthDp((temperature ?: "") + " " + condition, first, theme) + (if (showIcon) 27f * u else 0f) <= 190f * u
    val detail = listOfNotNull(
        listOfNotNull(highLow, place).joinToString(" · ").ifEmpty { null },
        highLow,
        place,
    ).firstOrNull { estimateWidthDp(it, second, theme) <= 200f * u }
    Column(
        GlanceModifier.fillMaxSize().padding(top = (247f * u).dp).clickableApp(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIcon || temperature != null || conditionFits) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    WeatherGlyph(current.condition, !current.isDay, (22f * u).dp)
                    HSpace((5f * u).dp)
                }
                if (temperature != null) WText(temperature, first, weight = WWeight.Bold, font = WFont.Text)
                if (conditionFits) WText(condition!!, first, font = WFont.Text)
            }
        }
        if (detail != null) WText(detail, second, color = theme.colors.secondary, align = TextAlign.Center, font = WFont.Text)
        if (rain != null && estimateWidthDp(rain, third, theme) + 16f * u <= 190f * u) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WIcon(R.drawable.ic_wl_drop, (14f * u).dp, theme.colors.rain)
                HSpace((3f * u).dp)
                WText(rain, third, color = theme.colors.rain, font = WFont.Text)
            }
        }
    }
}

/**
 * Kollar: düzendeki vektörlerle AnalogClock. Android 12+: kollar mürekkep, saniye kolu vurgu rengine
 * boyanır. Öncesinde saniye kolu yoktur ve kol rengi değiştirilemez; koyu kadranda açık renkli
 * kolları olan ikinci düzen kullanılır.
 */
private fun ssHands(context: Context, style: WidgetStyle, ink: Color, accent: Color): RemoteViews {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return RemoteViews(context.packageName, if (ink.luminance() > 0.45f) R.layout.widget_ss_hands_light else R.layout.widget_ss_hands)
    }
    return RemoteViews(context.packageName, if (style.secondHand) R.layout.widget_ss_hands_seconds else R.layout.widget_ss_hands).apply {
        val tint = ColorStateList.valueOf(ink.toArgb())
        setColorStateList(R.id.clock, "setHourHandTintList", tint)
        setColorStateList(R.id.clock, "setMinuteHandTintList", tint)
        if (style.secondHand) setColorStateList(R.id.clock, "setSecondHandTintList", ColorStateList.valueOf(accent.toArgb()))
    }
}
