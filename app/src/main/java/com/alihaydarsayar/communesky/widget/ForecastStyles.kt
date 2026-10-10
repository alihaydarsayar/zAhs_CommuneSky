package com.alihaydarsayar.communesky.widget

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
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.max
import kotlin.math.min

// --- Saatlik ----------------------------------------------------------------------------------------

@Composable
fun HourlyContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        size.height.value < TINY_HEIGHT -> TinyRow(data, extra = nextHourText(data))
        style == WidgetStyleId.HourlyRow || size.height < 100.dp -> HourlyRowStyle(data)
        style == WidgetStyleId.HourlyIcons || size.height < 150.dp -> HourlyIconsStyle(data)
        else -> HourlyCurve(data)
    }
}

/** Üstte sıcaklık; yanında yer, altında durum ve Y/D; sağda ikon. */
@Composable
private fun HourlyHeader(data: WidgetPlaceData, tempSize: Float = 34f) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    val temperature = temp(current.temperature)
    val detailWidth = size.width.value - 32f - estimateWidthDp(temperature, tempSize, theme) - 10f - 34f
    val parts = buildList {
        if (theme.shows(WidgetContent.Condition)) add(context.getString(weatherDescriptionRes(current.weatherCode)))
        if (theme.shows(WidgetContent.HighLow) && today != null) add(highLow(today.maxTemperature, today.minTemperature))
    }
    // Sığmayan parça sondan düşer; yazı kesilmez.
    val detail = generateSequence(parts) { if (it.size > 1) it.dropLast(1) else null }
        .map { it.joinToString(" · ") }
        .firstOrNull { estimateWidthDp(it, 12f, theme) <= detailWidth }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WText(temperature, tempSize, weight = WWeight.Light)
        HSpace(10.dp)
        Column(GlanceModifier.defaultWeight()) {
            PlaceLabel(data, 14f)
            if (!detail.isNullOrEmpty()) WText(detail, 12f, color = theme.colors.secondary)
        }
        CurrentGlyph(data.snapshot, 26.dp)
    }
}

/**
 * Tasarım "Saatlik": 8 saatlik sıcaklık eğrisi (noktaların üstünde değerler), altında yağış
 * ihtimali çubukları (yüzde yazısı sadece %30 ve üstü), en altta saatler.
 */
@Composable
private fun HourlyCurve(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val count = if (size.width >= 250.dp) 8 else 6
    val hours = forecast.upcomingHours(forecast.localNow(), count = count)
    if (hours.size < 2) return HourlyIconsStyle(data)
    val temps = hours.mapIndexed { i, hour -> tempValue(if (i == 0) forecast.current.temperature else hour.temperature) }
    val showRain = theme.shows(WidgetContent.Precipitation)
    val hourFormat = hourFormatter(context)

    // Eğri alanının yüksekliği: yüzeyin iç boşlukları, başlık ve saat satırı çıkınca kalan.
    val scale = theme.style.textSize.scale
    // Alçak widget'ta başlık küçülür ve yüzde yazıları düşer: eğriye yer kalsın.
    val headerTemp = if (size.height >= 170.dp) 34f else 30f
    val header = max(lineHeightDp(headerTemp, theme), lineHeightDp(14f, theme) + lineHeightDp(12f, theme))
    val areaHeight = size.height.value - 28f - header - lineHeightDp(11f, theme) - 12f
    val labelSize = if (areaHeight >= 70f) 13f else 12f
    val labelHeight = lineHeightDp(labelSize, theme)
    val showPct = showRain && areaHeight >= 70f
    val pctHeight = if (showPct) lineHeightDp(10f, theme) else 0f
    val barMax = if (showRain) min(24f, areaHeight * 0.22f) else 0f
    val barBottom = areaHeight - pctHeight - 2f
    val curveTop = 2f
    val curveRange = max(8f, barBottom - barMax - 6f - labelHeight - 4f - curveTop)
    val minT = temps.min()
    val maxT = temps.max()
    val span = max(1, maxT - minT).toFloat()
    val labelTops = temps.map { curveTop + (1f - (it - minT) / span) * curveRange }
    val pointsY = labelTops.map { it + labelHeight + 4f }
    val probabilities = hours.map { it.precipitationProbability }
    val bars = if (showRain) probabilities.map { if (it >= 10) max(3f, barMax * it / 100f) else 0f } else emptyList()
    val bitmap = WidgetBitmaps.hourlyCurve(
        pointsYDp = pointsY,
        barHeightsDp = bars,
        widthDp = size.width.value - 32f,
        heightDp = areaHeight,
        barBottomDp = barBottom,
        density = context.resources.displayMetrics.density,
        line = theme.colors.text,
        bar = theme.colors.rain.copy(alpha = 0.7f),
        // "Şimdi" noktası vurgu renginde.
        now = theme.colors.accent,
    )

    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            HourlyHeader(data, tempSize = headerTemp)
            VSpace(4.dp)
            Box(GlanceModifier.fillMaxWidth().height(areaHeight.dp)) {
                Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Row(GlanceModifier.fillMaxSize()) {
                    temps.forEachIndexed { i, t ->
                        Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(GlanceModifier.height(labelTops[i].dp))
                            WText("$t°", labelSize, weight = WWeight.Medium, align = TextAlign.Center)
                            Spacer(GlanceModifier.defaultWeight())
                            if (showPct && probabilities[i] >= 30) {
                                WText(context.getString(R.string.precipitation_value, probabilities[i]), 10f, color = theme.colors.rain, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            VSpace(4.dp)
            Row(GlanceModifier.fillMaxWidth()) {
                hours.forEachIndexed { i, hour ->
                    WText(
                        if (i == 0) context.getString(R.string.now) else hour.time.format(hourFormat),
                        11f,
                        GlanceModifier.defaultWeight(),
                        color = if (i == 0) theme.colors.text else theme.colors.tertiary,
                        weight = if (i == 0) WWeight.Medium else WWeight.Regular,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Başlık ve altında ikonlu saatler. */
@Composable
private fun HourlyIconsStyle(data: WidgetPlaceData) {
    val size = LocalSize.current
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            HourlyHeader(data, tempSize = if (size.height >= 150.dp) 34f else 28f)
            Spacer(GlanceModifier.defaultWeight())
            HourlyStrip(data, count = if (size.width >= 300.dp) 7 else if (size.width >= 250.dp) 6 else 5, compact = size.height < 150.dp)
        }
    }
}

/** 4×1: şimdi ve sonraki saatler tek satırda. */
@Composable
private fun HourlyRowStyle(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val count = if (size.width >= 300.dp) 5 else if (size.width >= 250.dp) 4 else 3
    val hours = forecast.upcomingHours(forecast.localNow(), count = count + 1).drop(1)
    // Sütun dar ise "22" gibi kısa saat.
    val column = (size.width.value - 36f - 80f) / count
    val hourFormat = if (column < 40f) hourFormatter(context) else timeFormatter(context)
    WidgetSurface(onClick = openAppAction(), horizontal = 18.dp, vertical = 8.dp, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                WText(temp(forecast.current.temperature), 30f, weight = WWeight.Light)
                PlaceLabel(data, 12f, weight = WWeight.Regular, color = theme.colors.secondary)
            }
            HSpace(10.dp)
            hours.forEach { hour ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WText(hour.time.format(hourFormat), 11f, color = theme.colors.tertiary, align = TextAlign.Center)
                    WeatherGlyph(hour.condition, !hour.isDay, 18.dp)
                    WText(temp(hour.temperature), 12f, weight = WWeight.Medium, align = TextAlign.Center)
                }
            }
        }
    }
}

// --- Haftalık ---------------------------------------------------------------------------------------

@Composable
fun WeeklyContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        style == WidgetStyleId.WeeklyColumns || size.height < 150.dp -> WeeklyColumns(data)
        style == WidgetStyleId.WeeklyList -> WeeklyRows(data, bars = false)
        else -> WeeklyRows(data, bars = true)
    }
}

/**
 * Tasarım "Haftalık": gün adı, ikon, yağış yüzdesi (%30 ve üstü vurgulu), en düşük, renkli sıcaklık
 * aralığı çubuğu (bütün haftanın en düşük–en yüksek aralığına göre), en yüksek.
 */
@Composable
private fun WeeklyRows(data: WidgetPlaceData, bars: Boolean) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val now = forecast.localNow()
    val scale = theme.style.textSize.scale
    val header = theme.shows(WidgetContent.Current)
    val available = size.height.value - 28f - (if (header) 36f * scale else 0f)
    val dayCount = (available / (26f * scale * theme.fontScaleFix)).toInt().coerceIn(3, 6)
    val days = forecast.upcomingDays(now).take(dayCount)
    if (days.isEmpty()) return EmptyContent()
    val weekMin = days.minOf { it.minTemperature }
    val weekMax = days.maxOf { it.maxTemperature }
    val span = max(1.0, weekMax - weekMin)
    val showPct = theme.shows(WidgetContent.Precipitation) && size.width >= 250.dp
    val density = context.resources.displayMetrics.density
    val barWidth = size.width.value - 32f - 56f - 26f - (if (showPct) 40f else 0f) - 32f - 32f - 30f
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            if (header) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlaceLabel(data, 15f, GlanceModifier.defaultWeight())
                    val current = forecast.current
                    WText(
                        context.getString(
                            R.string.widget_now_summary,
                            temp(current.temperature),
                            context.getString(weatherDescriptionRes(current.weatherCode)),
                        ),
                        13f,
                        color = theme.colors.secondary,
                        align = TextAlign.End,
                    )
                }
                VSpace(6.dp)
                HDivider()
            }
            // Glance bir sütunda en çok 10 öğe çizer: günler kendi sütunlarında, eşit yükseklikte.
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                days.forEach { day ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        val isToday = day.date == now.toLocalDate()
                        WText(
                            dayName(context, day.date, now.toLocalDate()), 14f, GlanceModifier.width(56.dp),
                            weight = if (isToday) WWeight.Bold else WWeight.Medium,
                            color = if (isToday) theme.colors.accent else null,
                        )
                        WeatherGlyph(day.condition, isNight = false, size = 22.dp)
                        HSpace(4.dp)
                        if (showPct) {
                            val pct = day.precipitationProbability
                            WText(
                                if (pct > 0) context.getString(R.string.precipitation_value, pct) else "",
                                12f,
                                GlanceModifier.width(40.dp),
                                color = if (pct >= 30) theme.colors.rain else theme.colors.tertiary,
                            )
                        }
                        if (bars) {
                            WText(temp(day.minTemperature), 16f, GlanceModifier.width(32.dp), color = theme.colors.secondary, align = TextAlign.End)
                            HSpace(8.dp)
                            val start = ((day.minTemperature - weekMin) / span).toFloat()
                            val end = ((day.maxTemperature - weekMin) / span).toFloat()
                            Image(
                                ImageProvider(
                                    WidgetBitmaps.rangeBar(start, end, max(40f, barWidth), 6f, density, theme.colors.text.copy(alpha = 0.14f), theme.colors.cool, theme.colors.warm),
                                ),
                                contentDescription = null,
                                modifier = GlanceModifier.defaultWeight().height(6.dp),
                                contentScale = ContentScale.FillBounds,
                            )
                            HSpace(8.dp)
                            WText(temp(day.maxTemperature), 16f, GlanceModifier.width(32.dp), weight = WWeight.Medium, align = TextAlign.End)
                        } else {
                            Spacer(GlanceModifier.defaultWeight())
                            WText(temp(day.minTemperature), 14f, GlanceModifier.width(40.dp), color = theme.colors.secondary, align = TextAlign.End)
                            WText(temp(day.maxTemperature), 14f, GlanceModifier.width(40.dp), weight = WWeight.Medium, align = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

/** Günler sütun sütun: ad, ikon, en yüksek, en düşük. */
@Composable
private fun WeeklyColumns(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val roomy = size.height >= 120.dp
    val now = forecast.localNow()
    val count = when {
        size.width >= 300.dp -> 7
        size.width >= 250.dp -> 6
        else -> 4
    }
    val days = forecast.upcomingDays(now).take(count)
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 12.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            if (size.height >= 150.dp && theme.shows(WidgetContent.Current)) {
                PlaceLabel(data, 14f)
                VSpace(6.dp)
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                days.forEach { day ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val isToday = day.date == now.toLocalDate()
                        WText(dayName(context, day.date, now.toLocalDate()), 12f, weight = WWeight.Medium, align = TextAlign.Center, color = if (isToday) theme.colors.accent else null)
                        VSpace(if (roomy) 4.dp else 1.dp)
                        WeatherGlyph(day.condition, isNight = false, size = if (roomy) 22.dp else 18.dp)
                        if (theme.shows(WidgetContent.Precipitation) && day.precipitationProbability >= 30 && size.height >= 120.dp) {
                            WText(context.getString(R.string.precipitation_value, day.precipitationProbability), 10f, color = theme.colors.rain, align = TextAlign.Center)
                        } else {
                            VSpace(if (roomy) 4.dp else 1.dp)
                        }
                        WText(temp(day.maxTemperature), 13f, weight = WWeight.Medium, align = TextAlign.Center)
                        WText(temp(day.minTemperature), 12f, color = theme.colors.secondary, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** "22:00 17°": tek satırlık en küçük düzende bir sonraki saat. */
@Composable
private fun nextHourText(data: WidgetPlaceData): String? {
    val context = LocalContext.current
    val forecast = data.snapshot.forecast
    val next = forecast.upcomingHours(forecast.localNow(), count = 2).getOrNull(1) ?: return null
    return "${next.time.format(timeFormatter(context))} ${temp(next.temperature)}"
}
