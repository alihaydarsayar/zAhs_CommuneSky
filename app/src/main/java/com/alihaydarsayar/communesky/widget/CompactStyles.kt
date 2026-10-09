package com.alihaydarsayar.communesky.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.min

// --- Küçük ------------------------------------------------------------------------------------------

@Composable
fun SmallContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        size.height.value < TINY_HEIGHT -> TinyRow(data, showName = LocalWidgetTheme.current.shows(WidgetContent.PlaceName))
        size.height < 100.dp -> SmallRow(data)
        style == WidgetStyleId.SmallCentered -> SmallCentered(data)
        style == WidgetStyleId.SmallMinimal -> SmallMinimal(data)
        else -> SmallClassic(data)
    }
}

/** Tasarım "Küçük": yer, ikon, büyük sıcaklık, durum, Y/D. Cam görünümü varsayılan. */
@Composable
private fun SmallClassic(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    WidgetSurface(onClick = openAppAction(), horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (theme.shows(WidgetContent.PlaceName)) {
                    PlaceLabel(data, 14f, GlanceModifier.defaultWeight())
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                CurrentGlyph(data.snapshot, 30.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            val temperature = temp(current.temperature)
            val textRows = (if (theme.shows(WidgetContent.Condition)) 1 else 0) + (if (theme.shows(WidgetContent.HighLow)) 1 else 0)
            WText(
                temperature,
                fitTemperature(temperature, 56f, size.width.value - 32f, size.height.value - 28f - 30f - textRows * lineHeightDp(13f, theme) - 4f, theme),
                weight = WWeight.Light,
            )
            Spacer(GlanceModifier.defaultWeight())
            if (theme.shows(WidgetContent.Condition)) {
                WText(context.getString(weatherDescriptionRes(current.weatherCode)), 13f)
            }
            if (theme.shows(WidgetContent.HighLow) && today != null) {
                WText(highLow(today.maxTemperature, today.minTemperature), 13f, color = theme.colors.secondary)
            }
        }
    }
}

/** Ortada büyük ikon, sıcaklık ve yer. */
@Composable
private fun SmallCentered(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    val temperature = temp(current.temperature)
    val icon = min(48f, size.height.value * 0.28f)
    // Satırlar yukarıdan aşağı yerleşir; sığmayan alt satır gösterilmez.
    var left = size.height.value - 24f - icon - 4f
    val tempSize = fitTemperature(temperature, 42f, size.width.value - 24f, min(left, size.height.value * 0.3f), theme)
    left -= lineHeightDp(tempSize, theme)
    val showPlace = theme.shows(WidgetContent.PlaceName) && left >= lineHeightDp(13f, theme)
    if (showPlace) left -= lineHeightDp(13f, theme)
    val detail = buildList {
        if (theme.shows(WidgetContent.Condition) && size.height >= 150.dp) add(context.getString(weatherDescriptionRes(current.weatherCode)))
        if (theme.shows(WidgetContent.HighLow) && today != null) add(highSlashLow(today.maxTemperature, today.minTemperature))
    }.joinToString(" · ")
    val showDetail = detail.isNotEmpty() && left >= lineHeightDp(12f, theme) &&
        estimateWidthDp(detail, 12f, theme) <= size.width.value - 24f
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 12.dp, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            CurrentGlyph(data.snapshot, icon.dp)
            WText(temperature, tempSize, weight = WWeight.Light, align = TextAlign.Center)
            if (showPlace) PlaceLabel(data, 13f)
            if (showDetail) WText(detail, 12f, color = theme.colors.secondary, align = TextAlign.Center)
        }
    }
}

/** Dolu renkte sade kutu: dev sıcaklık, köşede ikon. */
@Composable
private fun SmallMinimal(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    WidgetSurface(onClick = openAppAction(), horizontal = 18.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (theme.shows(WidgetContent.PlaceName)) {
                    WText(data.snapshot.displayName(context), 13f, GlanceModifier.defaultWeight(), weight = WWeight.Medium, color = theme.colors.secondary)
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                CurrentGlyph(data.snapshot, 24.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            WText(temp(current.temperature), min(64f, size.height.value * 0.4f), weight = WWeight.Medium)
            if (theme.shows(WidgetContent.HighLow) && today != null) {
                WText(highSlashLow(today.maxTemperature, today.minTemperature), 13f, color = theme.colors.secondary)
            } else if (theme.shows(WidgetContent.Condition)) {
                WText(context.getString(weatherDescriptionRes(current.weatherCode)), 13f, color = theme.colors.secondary)
            }
        }
    }
}

/** 2×1: tek satır. */
@Composable
private fun SmallRow(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    WidgetSurface(onClick = openAppAction(), horizontal = 14.dp, vertical = 6.dp, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CurrentGlyph(data.snapshot, 26.dp)
            HSpace(8.dp)
            val temperature = temp(data.snapshot.forecast.current.temperature)
            WText(temperature, fitTemperature(temperature, 26f, LocalSize.current.width.value - 28f - 34f, LocalSize.current.height.value - 12f, theme), weight = WWeight.Light)
            if (theme.shows(WidgetContent.PlaceName) && LocalSize.current.width >= 150.dp) {
                HSpace(8.dp)
                PlaceLabel(data, 12f, GlanceModifier.defaultWeight(), weight = WWeight.Regular, color = theme.colors.secondary)
            }
        }
    }
}

// --- Yazı -------------------------------------------------------------------------------------------

@Composable
fun TextContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    when {
        style == WidgetStyleId.TextLine -> TextLine(data)
        style == WidgetStyleId.TextTemperature -> TextTemperature(data)
        size.width < 180.dp -> TextTemperature(data)
        else -> TextStack(data)
    }
}

/** Tasarım "Yazı": arka plansız; sıcaklık, ikon, yer, durum, varsa yağmur saati. */
@Composable
private fun TextStack(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val temperature = temp(current.temperature)
    val tempSize = fitTemperature(temperature, 52f, size.width.value * 0.42f, size.height.value - 8f, theme)
    // Sağ sütunun genişliği; sığmayan satırlar gösterilmez (yazı kesilmez).
    val column = size.width.value - 24f - estimateWidthDp(temperature, tempSize, theme) - 12f
    val name = data.snapshot.displayName(context)
    val condition = context.getString(weatherDescriptionRes(current.weatherCode))
    val rain = if (theme.shows(WidgetContent.RainAlert)) WidgetPhrases.rainShort(context, data.snapshot) else null
    var height = size.height.value - 8f - lineHeightDp(16f, theme)
    val showCondition = theme.shows(WidgetContent.Condition) && estimateWidthDp(condition, 14f, theme) <= column &&
        height >= lineHeightDp(14f, theme)
    if (showCondition) height -= lineHeightDp(14f, theme)
    val showRain = rain != null && estimateWidthDp(rain, 13f, theme) <= column && height >= lineHeightDp(13f, theme)
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 4.dp, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WText(temperature, tempSize, weight = WWeight.Light)
            HSpace(12.dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CurrentGlyph(data.snapshot, 20.dp)
                    if (theme.shows(WidgetContent.PlaceName) && estimateWidthDp(name, 16f, theme) + 26f <= column) {
                        HSpace(6.dp)
                        WText(name, 16f, weight = WWeight.Medium)
                    }
                }
                if (showCondition) WText(condition, 14f)
                if (showRain) WText(rain!!, 13f, color = theme.colors.rain)
            }
        }
    }
}

/** Tek satır: "17° · Yayla · Parçalı bulutlu · 21:00'de yağmur"; sığmayan parçalar sondan düşer. */
@Composable
private fun TextLine(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    val temperature = temp(current.temperature)
    val parts = buildList {
        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context) to false)
        if (theme.shows(WidgetContent.Condition)) add(context.getString(weatherDescriptionRes(current.weatherCode)) to false)
        if (theme.shows(WidgetContent.RainAlert)) WidgetPhrases.rainShort(context, data.snapshot)?.let { add(it to true) }
    }.toMutableList()
    val available = size.width.value - 24f - 28f - estimateWidthDp(temperature, 22f, theme)
    fun width() = parts.sumOf { estimateWidthDp("  ·  " + it.first, 15f, theme).toDouble() }
    // Önce durum düşer (yağmur uyarısı daha önemli), sonra en sondaki.
    if (width() > available) parts.removeAll { !it.second && parts.indexOf(it) > 0 }
    while (parts.isNotEmpty() && width() > available) parts.removeAt(parts.lastIndex)
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 4.dp, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrentGlyph(data.snapshot, 22.dp)
            HSpace(6.dp)
            WText(temperature, 22f, weight = WWeight.Medium)
            parts.forEach { (text, rain) ->
                WText("  ·  $text", 15f, color = if (rain) theme.colors.rain else null)
            }
        }
    }
}

/** Büyük sıcaklık ve ikon, altında yer. */
@Composable
private fun TextTemperature(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val icon = min(36f, size.height.value * 0.35f)
    val name = data.snapshot.displayName(context)
    val withName = theme.shows(WidgetContent.PlaceName) && size.height.value >= 70f
    val nameHeight = if (withName) lineHeightDp(14f, theme) else 0f
    val tempSize = fitTemperature(temperature, 60f, size.width.value - 24f - icon - 6f, size.height.value - 8f - nameHeight, theme)
    WidgetSurface(onClick = openAppAction(), horizontal = 12.dp, vertical = 4.dp, contentAlignment = Alignment.CenterStart) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WText(temperature, tempSize, weight = WWeight.Light)
                HSpace(6.dp)
                CurrentGlyph(data.snapshot, icon.dp)
            }
            if (withName && estimateWidthDp(name, 14f, theme) <= size.width.value - 24f) {
                WText(name, 14f, weight = WWeight.Medium)
            }
        }
    }
}

// --- Yerlerim ---------------------------------------------------------------------------------------

@Composable
fun PlacesContent(style: WidgetStyleId, input: WidgetInput) {
    val theme = LocalWidgetTheme.current
    val places = input.savedPlaces(includeLocation = theme.shows(WidgetContent.IncludeLocation))
    if (places.isEmpty()) return EmptyContent()
    val size = LocalSize.current
    if (style == WidgetStyleId.PlacesList && size.height >= 100.dp) PlacesList(places) else PlacesColumns(places)
}

/** Tasarım "Yerlerim": üç yer yan yana, ev ilk sırada; isim, ikon, sıcaklık, Y/D. */
@Composable
private fun PlacesColumns(places: List<WidgetPlaceData>) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val count = when {
        size.width >= 330.dp -> 4
        size.width >= 250.dp -> 3
        else -> 2
    }
    val shown = places.take(count)
    val compact = size.height < 100.dp
    val tiny = size.height.value < TINY_HEIGHT
    val columnWidth = (size.width.value - 16f) / shown.size - 8f
    val context = LocalContext.current
    WidgetSurface(onClick = openAppAction(), horizontal = 8.dp, vertical = if (compact) 6.dp else 12.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { index, data ->
                if (index > 0) VDivider(GlanceModifier.fillMaxHeight().padding(vertical = 8.dp))
                val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val temperature = temp(data.snapshot.forecast.current.temperature)
                    if (tiny) {
                        // Tek satır: sıcaklık ve (sığarsa) yer adı.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WText(temperature, 18f, weight = WWeight.Medium)
                            val name = data.snapshot.displayName(context)
                            if (estimateWidthDp(temperature, 18f, theme) + estimateWidthDp(name, 12f, theme) + 6f <= columnWidth) {
                                HSpace(6.dp)
                                WText(name, 12f, color = theme.colors.secondary)
                            }
                        }
                    } else {
                        if (estimateWidthDp(data.snapshot.displayName(context), 13f, theme) + 18f <= columnWidth) PlaceLabel(data, 13f) else WText(data.snapshot.displayName(context), 12f, weight = WWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CurrentGlyph(data.snapshot, if (compact) 20.dp else 24.dp)
                            HSpace(6.dp)
                            WText(temperature, if (compact) 24f else 28f, weight = WWeight.Light)
                        }
                    }
                    if (theme.shows(WidgetContent.HighLow) && today != null && !compact) {
                        WText(highSlashLow(today.maxTemperature, today.minTemperature), 12f, color = theme.colors.tertiary, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Yerler alt alta. */
@Composable
private fun PlacesList(places: List<WidgetPlaceData>) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    // En çok 5 yer: Glance bir sütunda en çok 10 öğe çizer (yerler + aralarındaki çizgiler).
    val rows = ((size.height.value - 24f) / (40f * theme.style.textSize.scale)).toInt().coerceIn(1, min(5, places.size))
    WidgetSurface(onClick = openAppAction(), horizontal = 18.dp, vertical = 12.dp) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            places.take(rows).forEachIndexed { index, data ->
                if (index > 0) HDivider()
                val current = data.snapshot.forecast.current
                val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        PlaceLabel(data, 14f)
                        WText(context.getString(weatherDescriptionRes(current.weatherCode)), 12f, color = theme.colors.secondary)
                    }
                    if (theme.shows(WidgetContent.HighLow) && today != null) {
                        WText(highSlashLow(today.maxTemperature, today.minTemperature), 12f, color = theme.colors.tertiary)
                        HSpace(10.dp)
                    }
                    CurrentGlyph(data.snapshot, 24.dp)
                    HSpace(8.dp)
                    WText(temp(current.temperature), 24f, weight = WWeight.Light, align = TextAlign.End)
                }
            }
        }
    }
}
