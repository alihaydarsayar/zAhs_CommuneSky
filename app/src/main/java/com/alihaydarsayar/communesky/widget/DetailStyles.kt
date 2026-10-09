package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.SunPhase
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.labelRes
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// --- Yağış ------------------------------------------------------------------------------------------

@Composable
fun PrecipitationContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        size.height.value < TINY_HEIGHT -> PrecipitationTiny(data)
        style == WidgetStyleId.PrecipitationSentence || size.width < 250.dp -> PrecipitationSentence(data)
        else -> PrecipitationBars(data)
    }
}

/**
 * Tasarım "Sonraki 2 saat yağış": solda tek cümle; sağda önümüzdeki 2 saatin 15 dakikalık yağış
 * çubukları, altta Şimdi / +1 sa / +2 sa.
 */
@Composable
private fun PrecipitationBars(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val now = data.snapshot.forecast.localNow()
    val nowcast = WidgetPhrases.nowcast(data.snapshot, now)
    val sentence = WidgetPhrases.nowcastSentence(context, nowcast, now)
    val barArea = min(58f, size.height.value - 44f).coerceAtLeast(20f)
    val maxMm = max(1.2, nowcast.slices.maxOfOrNull { it.precipitationMm } ?: 0.0)
    // Cümle, sol sütunda kesilmeden sığacak en büyük boyutta (en çok 3 satır).
    val sentenceWidth = if (size.width >= 300.dp) 132f else 112f
    val sentenceHeight = size.height.value - 20f - (if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(13f, theme) else 0f)
    val sentenceSize = listOf(19f, 17f, 15f, 14f, 13f, 12f).firstOrNull { sp ->
        val lines = kotlin.math.ceil(estimateWidthDp(sentence, sp, theme) / (sentenceWidth - 4f) * 1.15f).toInt().coerceAtLeast(1)
        lines <= 3 && lines * lineHeightDp(sp, theme) <= sentenceHeight
    } ?: 12f
    WidgetSurface(onClick = openAppAction(), horizontal = 20.dp, vertical = 10.dp, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(sentenceWidth.dp)) {
                if (theme.shows(WidgetContent.PlaceName)) {
                    PlaceLabel(data, 13f, weight = WWeight.Regular, color = theme.colors.rain)
                }
                WText(sentence, sentenceSize, weight = WWeight.Medium, maxLines = 3)
            }
            HSpace(16.dp)
            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth().height(barArea.dp), verticalAlignment = Alignment.Bottom) {
                    val slices = nowcast.slices
                    // Glance bir satırda en çok 10 öğe çizer: aralık, her çubuğun kendi kenar boşluğu.
                    (0 until PRECIP_SLOTS).forEach { i ->
                        val slice = slices.getOrNull(i)
                        val mm = slice?.precipitationMm ?: 0.0
                        val wet = slice?.isWet == true
                        val height = if (!wet) 3f else max(6f, (mm / maxMm * (barArea - 2f)).toFloat())
                        Box(GlanceModifier.defaultWeight().padding(horizontal = 2.5.dp)) {
                            Box(
                                GlanceModifier.fillMaxWidth().height(height.dp)
                                    .rounded(if (wet) theme.colors.rain else theme.colors.text.copy(alpha = 0.18f), 4.dp),
                            ) {}
                        }
                    }
                }
                VSpace(1.dp)
                HDivider()
                VSpace(3.dp)
                Row(GlanceModifier.fillMaxWidth()) {
                    WText(context.getString(R.string.now), 11f, color = theme.colors.tertiary)
                    Spacer(GlanceModifier.defaultWeight())
                    WText(context.getString(R.string.widget_plus_hours, 1), 11f, color = theme.colors.tertiary, align = TextAlign.Center)
                    Spacer(GlanceModifier.defaultWeight())
                    WText(context.getString(R.string.widget_plus_hours, 2), 11f, color = theme.colors.tertiary, align = TextAlign.End)
                }
            }
        }
    }
}

private const val PRECIP_SLOTS = 8

/** Sadece cümle: damla ikonu, yer ve "Yağmur 20:00'de başlıyor". */
@Composable
private fun PrecipitationSentence(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val now = data.snapshot.forecast.localNow()
    val nowcast = WidgetPhrases.nowcast(data.snapshot, now)
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 10.dp, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WIcon(R.drawable.ic_wl_drop, 26.dp, theme.colors.rain)
            HSpace(12.dp)
            Column {
                if (theme.shows(WidgetContent.PlaceName)) PlaceLabel(data, 12f, weight = WWeight.Regular, color = theme.colors.secondary)
                WText(WidgetPhrases.nowcastSentence(context, nowcast, now), 16f, weight = WWeight.Medium, maxLines = 2)
            }
        }
    }
}

// --- Ayrıntılar -------------------------------------------------------------------------------------

@Composable
fun DetailsContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        style == WidgetStyleId.DetailsSun || size.width < 180.dp -> SunDetails(data)
        style == WidgetStyleId.DetailsList -> DetailsList(data)
        else -> DetailsTiles(data)
    }
}

/** Bir ayrıntı karosu: başlık, değer ve kısa açıklama. */
data class DetailTile(val label: String, val value: String, val note: String?, val unit: String? = null)

/** Hissedilen, nem, rüzgâr, UV, basınç, görüş: değerler ve kısa açıklamalar. */
fun detailTiles(context: Context, snapshot: WeatherSnapshot, settings: AppSettings): List<DetailTile> {
    val forecast = snapshot.forecast
    val current = forecast.current
    val now = forecast.localNow()
    val unit = settings.temperatureUnit
    fun t(c: Double) = "${unit.fromCelsius(c).roundToInt()}°"
    val decimal = NumberFormat.getNumberInstance(locale(context)).apply { maximumFractionDigits = 1 }

    val feelsDiff = current.apparentTemperature - current.temperature
    val feelsNote = when {
        feelsDiff <= -1.5 && current.windSpeed >= 15 -> R.string.widget_note_feels_wind
        feelsDiff <= -1.5 -> R.string.widget_note_feels_colder
        feelsDiff >= 1.5 && current.humidity >= 60 -> R.string.widget_note_feels_humid
        feelsDiff >= 1.5 -> R.string.widget_note_feels_warmer
        else -> R.string.widget_note_feels_same
    }
    val points = context.resources.getStringArray(R.array.compass_points)
    val windNote = current.windDirection?.let { points[((it + 22.5) / 45).toInt() % 8] }
    val tomorrow = forecast.daily.firstOrNull { it.date == now.toLocalDate().plusDays(1) }
    val today = forecast.today(now)
    val uvNote = if (current.isDay) {
        today?.uvIndexMax?.let { context.getString(R.string.widget_note_uv_today, decimal.format(it)) }
    } else {
        tomorrow?.uvIndexMax?.let { context.getString(R.string.widget_note_uv_tomorrow, decimal.format(it)) }
    }
    val pressureNote = current.pressure?.let {
        context.getString(
            R.string.widget_note_pressure,
            context.getString(
                when {
                    it > 1022 -> R.string.widget_pressure_high
                    it < 1005 -> R.string.widget_pressure_low
                    else -> R.string.widget_pressure_normal
                },
            ),
        )
    }
    val visibilityKm = current.visibility?.div(1000.0)
    val visibilityNote = visibilityKm?.let {
        context.getString(
            when {
                it >= 20 -> R.string.widget_visibility_clear
                it >= 8 -> R.string.widget_visibility_good
                it >= 2 -> R.string.widget_visibility_moderate
                else -> R.string.widget_visibility_poor
            },
        )
    }
    val dash = "–"
    return listOf(
        DetailTile(context.getString(R.string.feels_like), t(current.apparentTemperature), context.getString(feelsNote)),
        DetailTile(
            context.getString(R.string.humidity),
            context.getString(R.string.humidity_value, current.humidity),
            current.dewPoint?.let { context.getString(R.string.widget_note_dew, t(it)) },
        ),
        DetailTile(
            context.getString(R.string.wind),
            settings.windUnit.fromKmh(current.windSpeed).roundToInt().toString(),
            windNote,
            unit = context.getString(settings.windUnit.labelRes),
        ),
        DetailTile(context.getString(R.string.widget_uv), current.uvIndex?.let { decimal.format(it) } ?: dash, uvNote),
        DetailTile(context.getString(R.string.pressure), current.pressure?.roundToInt()?.toString() ?: dash, pressureNote),
        DetailTile(
            context.getString(R.string.widget_visibility_label),
            visibilityKm?.let { decimal.format(min(it, 99.0).roundToInt()) } ?: dash,
            visibilityNote,
            unit = visibilityKm?.let { "km" },
        ),
    )
}

/** Tasarım "Ayrıntılar": üstte yer ve anlık durum; 3×2 karo. */
@Composable
private fun DetailsTiles(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val tiles = detailTiles(context, data.snapshot, LocalAppSettings.current)
    val twoRows = size.height >= 150.dp
    val columns = if (size.width >= 250.dp) 3 else 2
    val shown = tiles.take(if (twoRows) columns * 2 else columns)
    // Açıklama satırı sadece karolar yeterince yüksekse (ana ekranda 4×2 ve üstü).
    val notes = theme.shows(WidgetContent.TileNotes) && size.height >= 190.dp
    WidgetSurface(onClick = openAppAction(), horizontal = 14.dp, vertical = 12.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val current = data.snapshot.forecast.current
                PlaceLabel(
                    data, 14f, GlanceModifier.defaultWeight(),
                    suffix = " · ${temp(current.temperature)} ${context.getString(weatherDescriptionRes(current.weatherCode))}",
                )
                WClock(ClockPart.Time, 12f, weight = WWeight.Regular, color = theme.colors.tertiary)
            }
            VSpace(8.dp)
            shown.chunked(columns).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) VSpace(8.dp)
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { index, tile ->
                        if (index > 0) HSpace(8.dp)
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight()
                                .rounded(theme.colors.surface, 16.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WText(tile.label, 12f, color = theme.colors.secondary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                WText(tile.value, if (twoRows) 19f else 17f, weight = WWeight.Medium)
                                if (tile.unit != null) WText(" " + tile.unit, 11f, color = theme.colors.secondary)
                            }
                            if (notes && tile.note != null) WText(tile.note, 11f, color = theme.colors.tertiary)
                        }
                    }
                }
            }
        }
    }
}

/** Ayrıntılar alt alta: başlık solda, değer sağda. */
@Composable
private fun DetailsList(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val tiles = detailTiles(context, data.snapshot, LocalAppSettings.current)
    val rows = ((size.height.value - 50f) / (24f * theme.style.textSize.scale)).toInt().coerceIn(2, tiles.size)
    val notes = theme.shows(WidgetContent.TileNotes) && size.width >= 250.dp
    WidgetSurface(onClick = openAppAction(), horizontal = 18.dp, vertical = 12.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            PlaceLabel(data, 14f)
            VSpace(4.dp)
            // Glance bir sütunda en çok 10 öğe çizer: satırlar eşit yükseklikte kendi sütunlarında.
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                tiles.take(rows).forEach { tile ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        WText(tile.label, 13f, color = theme.colors.secondary)
                        Spacer(GlanceModifier.defaultWeight())
                        if (notes && tile.note != null) {
                            WText(tile.note, 11f, color = theme.colors.tertiary, align = TextAlign.End)
                            HSpace(10.dp)
                        }
                        WText(tile.value + (tile.unit?.let { " $it" } ?: ""), 14f, weight = WWeight.Medium, align = TextAlign.End)
                    }
                }
            }
        }
    }
}

/** Tasarım "Gün doğumu ve batımı" (2×2): sıradaki olay büyük; yay; altta diğeri ve gün uzunluğu. */
@Composable
private fun SunDetails(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val phase = SunPhase.of(forecast, forecast.localNow()) ?: return DetailsList(data)
    val formatter = timeFormatter(context)
    val nextIsSunrise = !phase.isDay
    val timeSize = min(30f, size.height.value * 0.18f)
    // Yay, yazılardan (en kötü durumda alt satır ikiye bölünür) kalan alana sığar.
    val arcHeight = (size.height.value - 28f - lineHeightDp(12f, theme) * 3 - lineHeightDp(timeSize, theme) - 6f).coerceIn(16f, 60f)
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            WText(context.getString(if (nextIsSunrise) R.string.sunrise else R.string.sunset), 12f, color = theme.colors.secondary)
            WText(phase.end.format(formatter), timeSize)
            Spacer(GlanceModifier.defaultWeight())
            val bitmap = WidgetBitmaps.sunArc(
                if (phase.isDay) phase.progress else 0f,
                size.width.value - 32f, arcHeight, context.resources.displayMetrics.density,
                accent = theme.colors.sun, faint = theme.colors.sun.copy(alpha = 0.35f), horizon = theme.colors.text.copy(alpha = 0.2f),
            )
            Image(ImageProvider(bitmap), null, GlanceModifier.fillMaxWidth().height(arcHeight.dp), contentScale = ContentScale.FillBounds)
            Spacer(GlanceModifier.defaultWeight())
            val other = if (nextIsSunrise) {
                context.getString(R.string.widget_sunset_short, phase.sunset.format(formatter))
            } else {
                context.getString(R.string.widget_sunrise_short, phase.sunrise.format(formatter))
            }
            val dayLength = context.getString(R.string.widget_day_length, durationText(context, phase.dayLength.toMinutes()))
            // Yan yana sığmıyorsa alt alta.
            if (estimateWidthDp(other + dayLength, 12f, theme) + 16f <= size.width.value - 32f) {
                Row(GlanceModifier.fillMaxWidth()) {
                    WText(other, 12f, GlanceModifier.defaultWeight(), color = theme.colors.secondary)
                    WText(dayLength, 12f, color = theme.colors.secondary, align = TextAlign.End)
                }
            } else {
                WText(other, 12f, color = theme.colors.secondary)
                WText(dayLength, 12f, color = theme.colors.secondary)
            }
        }
    }
}

/** En küçük boyut: damla ve tek satırlık cümle, sığacak boyutta. */
@Composable
private fun PrecipitationTiny(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val now = data.snapshot.forecast.localNow()
    val sentence = WidgetPhrases.nowcastSentence(context, WidgetPhrases.nowcast(data.snapshot, now), now)
    val textSize = fitWidth(sentence, 15f, size.width.value - 24f - 24f, theme).coerceAtLeast(11f)
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 4.dp, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WIcon(R.drawable.ic_wl_drop, 18.dp, theme.colors.rain)
            HSpace(6.dp)
            WText(sentence, textSize, weight = WWeight.Medium, maxLines = 2)
        }
    }
}
