package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
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
 * çubukları, altta Şimdi ve bir, iki saat sonrasının saati.
 */
@Composable
private fun PrecipitationBars(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val now = data.snapshot.forecast.localNow()
    val nowcast = WidgetPhrases.nowcast(data.snapshot, now)
    val sentence = WidgetPhrases.nowcastSentence(context, nowcast, now)
    val barArea = min(58f, size.height.value - 48f).coerceAtLeast(20f)
    val maxMm = max(1.2, nowcast.slices.maxOfOrNull { it.precipitationMm } ?: 0.0)
    // Cümle, sol sütunda kesilmeden sığacak en büyük boyutta (en çok 3 satır).
    val sentenceWidth = if (size.width >= 300.dp) 132f else 112f
    val column = (sentenceWidth - 4f) * FIT_MARGIN
    fun fits(sp: Float, placeHeight: Float): Boolean {
        // Satır sayısı gerçek sarmayla sayılır: sözcükler bölünmediği için tahmin eksik çıkıyor, son satır kesiliyordu.
        val lines = wrappedLines(context, sentence, sp, column, theme, WWeight.Medium)
        // En uzun sözcük de sütuna sığmalı; yoksa sözcüğün sonu kırpılır.
        val longest = sentence.split(' ').maxOf { measureWidthDp(context, it, sp, theme, WWeight.Medium) }
        return lines <= 3 && lines * lineHeightDp(sp, theme) <= size.height.value - 24f - placeHeight && longest <= column
    }
    val sizes = listOf(19f, 17f, 15f, 14f, 13f, 12f)
    val placeHeight = if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(13f, theme) + 2f else 0f
    // Cümle yer adıyla birlikte sığmıyorsa önce yazı küçülür, sonra yer adı düşer (yalın cümle düzenindeki gibi).
    val withPlace = sizes.firstOrNull { placeHeight > 0f && fits(it, placeHeight) }
    val sentenceSize = withPlace ?: sizes.firstOrNull { fits(it, 0f) } ?: 12f
    val formatter = timeFormatter(context)
    val origin = nowcast.slices.firstOrNull()?.start ?: now
    WidgetSurface(onClick = openAppAction(), horizontal = 16.sd, vertical = 12.sd, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(sentenceWidth.sd)) {
                if (withPlace != null) {
                    PlaceLabel(data, 13f)
                    VSpace(2.sd)
                }
                WText(sentence, sentenceSize, weight = WWeight.Medium, maxLines = 3)
            }
            HSpace(16.sd)
            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth().height(barArea.sd), verticalAlignment = Alignment.Bottom) {
                    val slices = nowcast.slices
                    // Glance bir satırda en çok 10 öğe çizer: aralık, her çubuğun kendi kenar boşluğu.
                    (0 until PRECIP_SLOTS).forEach { i ->
                        val slice = slices.getOrNull(i)
                        val mm = slice?.precipitationMm ?: 0.0
                        val wet = slice?.isWet == true
                        val height = if (!wet) 3f else max(6f, (mm / maxMm * (barArea - 2f)).toFloat())
                        Box(GlanceModifier.defaultWeight().padding(horizontal = 2.5.sd)) {
                            Box(
                                GlanceModifier.fillMaxWidth().height(height.sd)
                                    .rounded(if (wet) theme.colors.rain else theme.colors.text.copy(alpha = 0.14f), 4.sd),
                            ) {}
                        }
                    }
                }
                VSpace(1.sd)
                HDivider()
                VSpace(3.sd)
                Row(GlanceModifier.fillMaxWidth()) {
                    WText(context.getString(R.string.now), 11f, color = theme.colors.tertiary)
                    Spacer(GlanceModifier.defaultWeight())
                    WText(origin.plusHours(1).format(formatter), 11f, color = theme.colors.tertiary, align = TextAlign.Center)
                    Spacer(GlanceModifier.defaultWeight())
                    WText(origin.plusHours(2).format(formatter), 11f, color = theme.colors.tertiary, align = TextAlign.End)
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
    val size = LocalSize.current
    val sentence = WidgetPhrases.nowcastSentence(context, nowcast, now)
    // Cümle iki satıra sığacak boyutta yazılır; en küçük boyutta da sığmıyorsa kesilmez, kısa düzen çizilir.
    val room = size.width.value - 32f - 26f - 12f
    if (estimateWidthDp(sentence, 12f, theme) > room * 2f * FIT_MARGIN) return PrecipitationTiny(data)
    // Yüksekliğe de sığmalı: iki satır ve yer adı sığmıyorsa önce yazı küçülür, sonra yer adı düşer.
    val height = (size.height.value - 24f) * FIT_MARGIN
    fun fits(sp: Float, placeHeight: Float): Boolean {
        val width = estimateWidthDp(sentence, sp, theme)
        val lines = if (width <= room * FIT_MARGIN) 1 else 2
        return width <= room * 2f * FIT_MARGIN && lines * lineHeightDp(sp, theme) + placeHeight <= height
    }
    val sizes = listOf(16f, 15f, 14f, 13f, 12f)
    val placeHeight = if (theme.shows(WidgetContent.PlaceName) && size.height >= 76.dp) lineHeightDp(12f, theme) else 0f
    val withPlace = sizes.firstOrNull { placeHeight > 0f && fits(it, placeHeight) }
    val sentenceSize = withPlace ?: sizes.firstOrNull { fits(it, 0f) } ?: return PrecipitationTiny(data)
    WidgetSurface(onClick = openAppAction(), horizontal = 16.sd, vertical = 12.sd, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WIcon(R.drawable.ic_wl_drop, 26.sd, theme.colors.rain)
            HSpace(12.sd)
            Column {
                if (withPlace != null) PlaceLabel(data, 12f, weight = WWeight.Regular, color = theme.colors.secondary)
                WText(sentence, sentenceSize, weight = WWeight.Medium, maxLines = 2)
            }
        }
    }
}

// --- Ayrıntılar -------------------------------------------------------------------------------------

@Composable
fun DetailsContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        size.height.value < TINY_HEIGHT -> TinyRow(data)
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
    // Açıklama satırı sadece karolara sığıyorsa: başlık + değer + açıklama.
    val valueSize = if (twoRows) 20f else 19f
    val tileRows = if (twoRows) 2 else 1
    val tileHeight = (size.height.value - 24f - lineHeightDp(20f, theme) - 8f - 8f * (tileRows - 1)) / tileRows - 8f
    val notes = theme.shows(WidgetContent.TileNotes) &&
        tileHeight >= lineHeightDp(11f, theme) + lineHeightDp(valueSize, theme) + lineHeightDp(10f, theme) + 8f
    WidgetSurface(onClick = openAppAction(), horizontal = 16.sd, vertical = 12.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.sd), verticalAlignment = Alignment.CenterVertically) {
                val current = data.snapshot.forecast.current
                PlaceLabel(data, 15f, GlanceModifier.defaultWeight())
                CurrentGlyph(data.snapshot, 18.sd)
                HSpace(8.sd)
                WText(temp(current.temperature), 20f, weight = WWeight.Medium)
            }
            VSpace(8.sd)
            shown.chunked(columns).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) VSpace(8.sd)
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { index, tile ->
                        if (index > 0) HSpace(8.sd)
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight()
                                .rounded(theme.colors.surface, 16.sd)
                                .padding(horizontal = 12.sd, vertical = 4.sd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tileWidth = (size.width.value - 32f - 8f * (columns - 1)) / columns - 24f
                            // Başlık karoya sığacak boyutta; kesilmez.
                            WText(tile.label, fitWidth(tile.label, 11f, tileWidth, theme).coerceAtLeast(8f), color = theme.colors.secondary)
                            if (notes) {
                                WText(tile.value, valueSize, weight = WWeight.Medium)
                                // Birim açıklamayla aynı satırda: "km/s · Kuzeydoğu". Sığmazsa açıklama düşer.
                                val note = listOfNotNull(tile.unit, tile.note).joinToString(" · ")
                                val shown = if (estimateWidthDp(note, 10f, theme) <= tileWidth) note else tile.unit ?: ""
                                if (shown.isNotEmpty()) WText(shown, 10f, color = theme.colors.tertiary)
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    WText(tile.value, valueSize, weight = WWeight.Medium)
                                    // Birim, değerin yanına sığıyorsa yazılır; kesilmez.
                                    val unit = tile.unit?.takeIf { maxOf(estimateWidthDp(tile.value, valueSize, theme), measureWidthDp(context, tile.value, valueSize, theme, WWeight.Medium)) + measureWidthDp(context, " $it", 10f, theme) <= tileWidth * FIT_MARGIN }
                                    if (unit != null) WText(" $unit", 10f, color = theme.colors.secondary)
                                }
                            }
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
    WidgetSurface(onClick = openAppAction(), horizontal = 16.sd, vertical = 12.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            PlaceLabel(data, 14f)
            VSpace(4.sd)
            // Glance bir sütunda en çok 10 öğe çizer: satırlar eşit yükseklikte kendi sütunlarında.
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                tiles.take(rows).forEach { tile ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        WText(tile.label, 13f, color = theme.colors.secondary)
                        Spacer(GlanceModifier.defaultWeight())
                        if (notes && tile.note != null) {
                            WText(tile.note, 11f, color = theme.colors.tertiary, align = TextAlign.End)
                            HSpace(8.sd)
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
    val arcHeight = (size.height.value - 32f - lineHeightDp(12f, theme) * 3 - lineHeightDp(timeSize, theme) - 6f).coerceIn(16f, 60f)
    WidgetSurface(onClick = openAppAction(), horizontal = 16.sd, vertical = 16.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            val title = context.getString(if (nextIsSunrise) R.string.sunrise else R.string.sunset)
            WText(title, fitWidth(title, 12f, size.width.value - 32f - 8f, theme).coerceAtLeast(9f), color = theme.colors.secondary)
            WText(phase.end.format(formatter), timeSize)
            Spacer(GlanceModifier.defaultWeight())
            val arcWidth = size.width.value - 32f
            Box(GlanceModifier.fillMaxWidth().height(arcHeight.sd)) {
                // Ufuk çizgisi: alttan 4 dp yukarıda, 1 dp.
                Box(GlanceModifier.fillMaxSize().padding(bottom = 3.5.sd), contentAlignment = Alignment.BottomStart) {
                    Box(GlanceModifier.fillMaxWidth().height(1.sd).background(theme.colors.text.copy(alpha = 0.2f))) {}
                }
                // Yay tek kanallı iki maskedir (tam çözünürlük): kesikli yol ve geçen kısım; renkleri burada verilir.
                TintedLayer(ImageProvider(WidgetBitmaps.sunArcDashed(arcWidth, arcHeight, bitmapDensity(), 255)), theme.colors.sun.copy(alpha = 0.35f))
                TintedLayer(ImageProvider(WidgetBitmaps.sunArcDone(if (phase.isDay) phase.progress else 0f, arcWidth, arcHeight, bitmapDensity(), 255)), theme.colors.sun)
            }
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
                // Alt alta da sığmıyorsa yazı küçülür; kesilmez.
                val room = size.width.value - 32f - 4f
                WText(other, fitWidth(other, 12f, room, theme).coerceAtLeast(9f), color = theme.colors.secondary)
                WText(dayLength, fitWidth(dayLength, 12f, room, theme).coerceAtLeast(9f), color = theme.colors.secondary)
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
    // İki satıra bile sığmıyorsa cümle kesilmez; yerine ikon, sıcaklık ve kısa uyarı gösterilir.
    val room = size.width.value - 24f - 26f
    if (estimateWidthDp(sentence, 11f, theme) > room * 2f * FIT_MARGIN) {
        return TinyRow(data, extra = WidgetPhrases.rainShort(context, data.snapshot), extraColor = theme.colors.rain, showName = false)
    }
    val textSize = fitWidth(sentence, 15f, room, theme).coerceAtLeast(11f)
    WidgetSurface(onClick = openAppAction(), horizontal = 12.sd, vertical = 4.sd, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WIcon(R.drawable.ic_wl_drop, 18.sd, theme.colors.rain)
            HSpace(8.sd)
            WText(sentence, textSize, weight = WWeight.Medium, maxLines = 2)
        }
    }
}
