package com.alihaydarsayar.communesky.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SunPhase
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.min

/**
 * Saat widget'ı: 14 stil. Saatler her zaman Android'in kendi bileşenleriyle (TextClock,
 * AnalogClock) çizilir; uygulama dakikada ya da saniyede bir uyanmaz.
 */
@Composable
fun ClockContent(style: WidgetStyleId, place: WidgetPlaceData?, input: WidgetInput) {
    val size = LocalSize.current
    if (place == null && style != WidgetStyleId.ClockHome) return EmptyContent()
    val small = size.width < 180.dp || size.height < 100.dp
    when (style) {
        WidgetStyleId.ClockRadial -> if (size.width < 250.dp || size.height < 100.dp) ClockCompact(place!!) else ClockRadial(place!!)
        WidgetStyleId.ClockSS -> if (size.height < 100.dp) ClockCompact(place!!) else ClockSsFace(place!!)
        WidgetStyleId.ClockAnalog, WidgetStyleId.ClockField, WidgetStyleId.ClockRing ->
            if (size.height < 100.dp) ClockCompact(place!!) else ClockAnalogFace(style, place!!)
        WidgetStyleId.ClockBold -> if (size.height < 100.dp) ClockCompact(place!!) else ClockBold(place!!)
        WidgetStyleId.ClockCard -> if (size.height < 100.dp) ClockCompact(place!!) else ClockCard(place!!)
        WidgetStyleId.ClockLine -> if (size.width < 180.dp) ClockCompact(place!!) else ClockLine(place!!)
        WidgetStyleId.ClockGlance -> if (size.width < 180.dp) ClockCompact(place!!) else ClockGlance(place!!)
        else -> if (small) {
            ClockCompact(place ?: input.home ?: return EmptyContent())
        } else {
            when (style) {
                WidgetStyleId.ClockBig -> ClockBig(place!!)
                WidgetStyleId.ClockSide -> ClockSide(place!!)
                WidgetStyleId.ClockHome -> ClockHome(input)
                WidgetStyleId.ClockSun -> ClockSun(place!!)
                else -> ClockWeather(place!!)
            }
        }
    }
}

/**
 * Saat yazısının boyutu: hem genişliğe hem yüksekliğe sığsın. [ems]: yazının genişliği, yazı
 * boyutu cinsinden ("18:03" ≈ 2,6; rakam yazı tipinde daha dar).
 */
@Composable
private fun clockSize(max: Float, widthDp: Float, heightDp: Float, ems: Float = LocalWidgetTheme.current.clockEms): Float {
    val theme = LocalWidgetTheme.current
    val scale = theme.style.textSize.scale * theme.fontScaleFix
    return min(max, min(widthDp / (ems * scale), heightDp / (1.2f * scale))).coerceAtLeast(18f)
}

/** Tasarım "Saat ve hava": solda saat ve tarih, sağda hava; altta yağmur uyarısı. */
@Composable
private fun ClockWeather(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    val rain = if (theme.shows(WidgetContent.RainAlert) && size.height >= 150.dp) WidgetPhrases.rainAlert(context, data.snapshot) else null
    WidgetSurface(horizontal = 20.dp, vertical = 16.dp) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WClock(ClockPart.Time, clockSize(72f, size.width.value - 40f - 150f, size.height.value - 32f - lineHeightDp(15f, theme) - (if (rain != null) lineHeightDp(13f, theme) + 30f else 0f)))
                    WClock(ClockPart.Date, 15f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEEdMMMM")
                }
                if (theme.shows(WidgetContent.Weather)) {
                    Column(GlanceModifier.clickableApp(), horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CurrentGlyph(data.snapshot, 38.dp)
                            HSpace(8.dp)
                            WText(temp(data.snapshot.forecast.current.temperature), 38f, weight = WWeight.Light)
                        }
                        val detail = buildList {
                            if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
                            if (theme.shows(WidgetContent.HighLow) && today != null) add(highLow(today.maxTemperature, today.minTemperature))
                        }.joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (theme.shows(WidgetContent.PlaceName)) placeIcon(data)?.let {
                                    WIcon(it, 12.dp, theme.colors.secondary)
                                    HSpace(5.dp)
                                }
                                WText(detail, 13f, color = theme.colors.secondary, align = TextAlign.End)
                            }
                        }
                    }
                }
            }
            if (rain != null) {
                Spacer(GlanceModifier.defaultWeight())
                Row(
                    GlanceModifier.fillMaxWidth().rounded(theme.colors.surface.copy(alpha = 0.12f), 16.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WIcon(R.drawable.ic_wl_drop, 16.dp, theme.colors.rain)
                    HSpace(10.dp)
                    WText(rain, 13f, color = theme.colors.rain)
                }
            }
        }
    }
}

/** Tasarım "Büyük": üstte yer, dev ince saat, altta tarih; sağda büyük ikon ve sıcaklık. */
@Composable
private fun ClockBig(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val showWeather = theme.shows(WidgetContent.Weather)
    WidgetSurface(horizontal = 24.dp, vertical = 14.dp, contentAlignment = Alignment.CenterStart) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (theme.shows(WidgetContent.PlaceName)) PlaceLabel(data, 16f, iconAfter = true)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Time, clockSize(96f, size.width.value - 48f - (if (showWeather) 82f else 0f), size.height.value - 28f - (if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(16f, theme) else 0f) - lineHeightDp(22f, theme) - 4f), GlanceModifier.defaultWeight())
                if (showWeather) {
                    Box(GlanceModifier.clickableApp().padding(end = 12.dp)) { CurrentGlyph(data.snapshot, min(70f, size.height.value * 0.36f).dp) }
                }
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, 17f, GlanceModifier.defaultWeight(), weight = WWeight.Medium, dateSkeleton = "dMMMMEEEE")
                if (showWeather) {
                    WText(temp(data.snapshot.forecast.current.temperature), 22f, GlanceModifier.padding(end = 18.dp), weight = WWeight.Medium)
                }
            }
        }
    }
}

/** Tasarım "Yan yana": sağa yaslı saat, tarih ve yer; ince dikey çizgi; ikon, sıcaklık, Y/D. */
@Composable
private fun ClockSide(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    WidgetSurface(horizontal = 20.dp, vertical = 14.dp, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                WClock(ClockPart.Time, clockSize(88f, size.width.value - 40f - (if (theme.shows(WidgetContent.Weather)) 140f else 0f), size.height.value - 28f - lineHeightDp(17f, theme) - (if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(15f, theme) else 0f)), contentAlignment = Alignment.CenterEnd)
                WClock(ClockPart.Date, 17f, weight = WWeight.Regular, dateSkeleton = "dMMMMEEEE", contentAlignment = Alignment.CenterEnd)
                if (theme.shows(WidgetContent.PlaceName)) PlaceLabel(data, 15f, weight = WWeight.Regular, color = theme.colors.secondary, iconAfter = true)
            }
            if (theme.shows(WidgetContent.Weather)) {
                HSpace(22.dp)
                VDivider(GlanceModifier.height(min(120f, size.height.value * 0.7f).dp), width = 1.5.dp)
                HSpace(22.dp)
                Column(GlanceModifier.clickableApp()) {
                    CurrentGlyph(data.snapshot, 46.dp)
                    WText(temp(data.snapshot.forecast.current.temperature), 38f, weight = WWeight.Light)
                    if (theme.shows(WidgetContent.HighLow) && today != null) {
                        WText(highSlashLow(today.maxTemperature, today.minTemperature), 15f, color = theme.colors.secondary)
                    }
                }
            }
        }
    }
}

/** Tasarım "Tek satır": ortada saat, altında "9 Ekim Cum | Beşiktaş | 15° | 21:00". */
@Composable
private fun ClockLine(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    // Alt satıra sığmayan parçalar düşer: önce yer adı, sonra yağmur saati.
    val rainTime = if (theme.shows(WidgetContent.RainAlert)) WidgetPhrases.rainTime(context, data.snapshot) else null
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val available = size.width.value - 32f
    var used = estimateWidthDp("09 Ekim Cum", 15f, theme) + (if (theme.shows(WidgetContent.Weather)) 41f + estimateWidthDp(temperature, 15f, theme) else 0f)
    val showRain = rainTime != null && used + 39f + estimateWidthDp(rainTime, 15f, theme) <= available
    if (showRain) used += 39f + estimateWidthDp(rainTime!!, 15f, theme)
    val showPlace = theme.shows(WidgetContent.PlaceName) && used + 41f + estimateWidthDp(data.snapshot.displayName(context), 15f, theme) <= available
    WidgetSurface(horizontal = 16.dp, vertical = 8.dp, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WClock(ClockPart.Time, clockSize(68f, size.width.value - 32f, size.height.value - 16f - lineHeightDp(15f, theme)), contentAlignment = Alignment.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, 15f, weight = WWeight.Regular, dateSkeleton = "dMMMMEEE")
                if (showPlace) {
                    LineSeparator()
                    PlaceLabel(data, 15f, weight = WWeight.Regular)
                }
                if (theme.shows(WidgetContent.Weather)) {
                    LineSeparator()
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 16.dp)
                        HSpace(4.dp)
                        WText(temperature, 15f)
                    }
                }
                if (showRain) {
                    rainTime?.let {
                        LineSeparator()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WIcon(R.drawable.ic_wl_drop, 14.dp, theme.colors.rain)
                            HSpace(4.dp)
                            WText(it, 15f, color = theme.colors.rain)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineSeparator() {
    // Tek öğe: Glance bir satırda en çok 10 öğe çizer.
    Box(GlanceModifier.padding(horizontal = 10.dp)) { VDivider(GlanceModifier.height(14.dp)) }
}

/** Tasarım "Kalın": açık renkli dolu kare (Android 12+ duvar kâğıdı rengi), alt alta kalın saat. */
@Composable
private fun ClockBold(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val palette = theme.colors.palette
    val filled = theme.style.background == BackgroundKind.Solid || theme.style.background == BackgroundKind.Sky
    val hourColor = theme.style.hourColor?.let { Color(it) } ?: if (filled) palette.onContainer else theme.colors.text
    val minuteColor = theme.style.minuteColor?.let { Color(it) } ?: if (filled) palette.containerAccent else theme.colors.secondary
    val digits = clockSize(72f, size.width.value - 36f, (size.height.value - 28f - lineHeightDp(15f, theme) - 6f) / 2f, ems = theme.clockEms / 2f)
    val content: @Composable () -> Unit = {
        Column(GlanceModifier.fillMaxSize()) {
            WClock(ClockPart.Hours, digits, weight = WWeight.Bold, color = hourColor)
            WClock(ClockPart.Minutes, digits, weight = WWeight.Bold, color = minuteColor, font = if (theme.clockFont == ClockFont.DigitsOutline) WFont.Outline else null)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, 15f, GlanceModifier.defaultWeight(), weight = WWeight.Medium, color = hourColor, dateSkeleton = "EEEd")
                if (theme.shows(WidgetContent.Weather)) {
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        val current = data.snapshot.forecast.current
                        WIcon(current.condition.lineIconRes(!current.isDay), 16.dp, hourColor)
                        HSpace(4.dp)
                        WText(temp(current.temperature), 15f, weight = WWeight.Medium, color = hourColor)
                    }
                }
            }
        }
    }
    if (filled) {
        // Kalın saat temanın açık "kap" rengini kullanır (duvar kâğıdı temasında Material You).
        WidgetSurface(drawBackground = false) {
            Box(
                GlanceModifier.fillMaxSize()
                    .rounded(palette.container.copy(alpha = theme.backgroundAlpha), theme.style.corners.radius + 12.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                WithColors(onLightColors(theme.colors)) { content() }
            }
        }
    } else {
        WidgetSurface(horizontal = 18.dp, vertical = 14.dp) { content() }
    }
}

/** Tasarım "Saat + ev/konum": solda saat ve tarih; sağda alt alta bulunduğun yer ve ev. */
@Composable
private fun ClockHome(input: WidgetInput) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val rows: List<Pair<WidgetPlaceData, String?>> = when (val state = input.homeLocation) {
        is HomeLocationState.Away -> listOf(state.here to null, state.home to distanceText(context, state.distanceKm))
        is HomeLocationState.Nearby -> listOf(state.here to null, state.home to distanceText(context, state.distanceKm))
        is HomeLocationState.AtHome -> listOf(state.home to context.getString(R.string.widget_you_are_home_short))
        is HomeLocationState.Single -> listOf(state.place to null)
        HomeLocationState.Empty -> emptyList()
    }
    WidgetSurface(horizontal = 24.dp, vertical = 14.dp, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                WClock(ClockPart.Time, clockSize(84f, size.width.value - 48f - 160f, size.height.value - 28f - lineHeightDp(17f, theme)))
                WClock(ClockPart.Date, 17f, weight = WWeight.Regular, dateSkeleton = "dMMMMEEEE")
            }
            Column(GlanceModifier.clickableApp()) {
                rows.forEachIndexed { index, (data, extra) ->
                    if (index > 0) VSpace(12.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 30.dp)
                        HSpace(10.dp)
                        Column {
                            WText(temp(data.snapshot.forecast.current.temperature), 24f)
                            PlaceLabel(data, 13f, weight = WWeight.Regular, suffix = extra?.let { " · $it" })
                        }
                    }
                }
            }
        }
    }
}

/** Tasarım "Saat + güneş": koyu kart; saat ve sıcaklık; altta gün doğumu–batımı yayı. */
@Composable
private fun ClockSun(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val forecast = data.snapshot.forecast
    val phase = SunPhase.of(forecast, forecast.localNow())
    val current = forecast.current
    WidgetSurface(horizontal = 22.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    WClock(ClockPart.Time, clockSize(60f, size.width.value - 44f - 100f, size.height.value - 28f - lineHeightDp(14f, theme) - min(62f, size.height.value * 0.22f) - lineHeightDp(13f, theme) - 8f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WClock(ClockPart.Date, 14f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEEdMMMM")
                        if (theme.shows(WidgetContent.PlaceName)) {
                            WText(" · " + data.snapshot.displayName(context), 14f, color = theme.colors.secondary)
                        }
                    }
                }
                if (theme.shows(WidgetContent.Weather)) {
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        val icon = if (phase?.isDay == false && current.condition == WeatherCondition.Clear) R.drawable.ic_wl_clear_night else current.condition.lineIconRes(!current.isDay)
                        WIcon(icon, 30.dp, theme.colors.sun)
                        HSpace(8.dp)
                        WText(temp(current.temperature), 30f, weight = WWeight.Light)
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (phase != null) SunArc(phase, heightDp = min(62f, size.height.value * 0.22f))
        }
    }
}

/** Gün doğumu–batımı yayı ve altında saatler, "Gece · 11 sa 39 dk kaldı". */
@Composable
fun SunArc(phase: SunPhase, heightDp: Float, compactLabels: Boolean = false) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val formatter = timeFormatter(context)
    val width = size.width.value - 44f
    val bitmap = WidgetBitmaps.sunArc(
        phase.progress, width, heightDp, context.resources.displayMetrics.density,
        accent = theme.colors.sun, faint = theme.colors.text.copy(alpha = 0.22f), horizon = theme.colors.text.copy(alpha = 0.15f),
    )
    Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp), contentScale = ContentScale.FillBounds)
    VSpace(2.dp)
    val remaining = durationText(context, phase.remaining.toMinutes())
    val center = context.getString(if (phase.isDay) R.string.widget_day_left else R.string.widget_night_left, remaining)
    val startLabel = if (phase.isDay) {
        context.getString(R.string.widget_sunrise_at, phase.start.format(formatter))
    } else {
        context.getString(R.string.widget_sunset_at, phase.start.format(formatter))
    }
    val endLabel = if (phase.isDay) {
        context.getString(R.string.widget_sunset_at, phase.end.format(formatter))
    } else {
        context.getString(R.string.widget_sunrise_at, phase.end.format(formatter))
    }
    // Üç etiket sığarsa tam haliyle; sığmazsa kısaltılır ("Batım 18:41"); yine sığmazsa sadece ortadaki.
    val shortStart = context.getString(if (phase.isDay) R.string.widget_sunrise_short else R.string.widget_sunset_short, phase.start.format(formatter))
    val shortEnd = context.getString(if (phase.isDay) R.string.widget_sunset_short else R.string.widget_sunrise_short, phase.end.format(formatter))
    fun fits(a: String, b: String, sp: Float) = estimateWidthDp(a + center + b, sp, theme) + 24f <= width
    val labels = when {
        compactLabels -> null
        fits(startLabel, endLabel, 13f) -> Triple(startLabel, endLabel, 13f)
        fits(shortStart, shortEnd, 12f) -> Triple(shortStart, shortEnd, 12f)
        else -> null
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (labels != null) {
            WText(labels.first, labels.third, GlanceModifier.defaultWeight(), color = theme.colors.secondary)
            WText(center, labels.third, color = theme.colors.sun, align = TextAlign.Center)
            WText(labels.second, labels.third, GlanceModifier.defaultWeight(), color = theme.colors.secondary, align = TextAlign.End)
        } else {
            WText(center, 12f, GlanceModifier.defaultWeight(), color = theme.colors.sun, align = TextAlign.Center)
        }
    }
}

/** Tasarım "Bir bakışta": büyük tarih; altında sıcaklık ve varsa yağmur hapı. */
@Composable
private fun ClockGlance(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    WidgetSurface(horizontal = 24.dp, vertical = 10.dp, contentAlignment = Alignment.CenterStart) {
        Column(verticalAlignment = Alignment.CenterVertically) {
            WClock(ClockPart.Date, clockSize(30f, size.width.value - 48f, size.height.value - 20f - 6f - 24f, ems = 6.2f), weight = WWeight.Regular, dateSkeleton = "EEEEdMMMM")
            VSpace(6.dp)
            Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                if (theme.shows(WidgetContent.Weather)) {
                    CurrentGlyph(data.snapshot, 20.dp)
                    HSpace(6.dp)
                    WText(temp(data.snapshot.forecast.current.temperature), 17f)
                }
                if (theme.shows(WidgetContent.RainAlert)) {
                    WidgetPhrases.rainStartSentence(context, data.snapshot)?.let { text ->
                        HSpace(14.dp)
                        Pill(color = if (theme.colors.darkText) theme.colors.pill else Color(0xFF141830).copy(alpha = 0.35f)) {
                            WIcon(R.drawable.ic_wl_drop, 15.dp, theme.colors.rain)
                            HSpace(6.dp)
                            WithColors(theme.colors.copy(shadow = false)) { WText(text, 15f, color = theme.colors.rain) }
                        }
                    }
                }
            }
        }
    }
}

/** Tasarım "Kart": 2×2 gökyüzü kartında saat, tarih, çizgi, sıcaklık ve ikon. */
@Composable
private fun ClockCard(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val current = data.snapshot.forecast.current
    WidgetSurface(horizontal = 16.dp, vertical = 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            WClock(ClockPart.Time, clockSize(52f, size.width.value - 32f, size.height.value - 28f - lineHeightDp(13f, theme) - 9f - lineHeightDp(24f, theme) - lineHeightDp(12f, theme) - 4f))
            WClock(ClockPart.Date, 13f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEEdMMMM")
            Spacer(GlanceModifier.defaultWeight())
            HDivider()
            VSpace(8.dp)
            Row(GlanceModifier.fillMaxWidth().clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WText(temp(current.temperature), 24f, weight = WWeight.Light)
                    val detail = buildList {
                        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
                        if (size.width >= 200.dp || !theme.shows(WidgetContent.PlaceName)) add(context.getString(weatherDescriptionRes(current.weatherCode)))
                    }.joinToString(" · ")
                    WText(detail, 12f, color = theme.colors.secondary)
                }
                CurrentGlyph(data.snapshot, 32.dp)
            }
        }
    }
}

/** Küçük boyutlar için ortak düzen: saat ve tarih, yanında (ya da altında) sıcaklık. */
@Composable
private fun ClockCompact(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val narrow = size.width < 180.dp
    WidgetSurface(horizontal = 14.dp, vertical = 8.dp, contentAlignment = Alignment.CenterStart) {
        if (narrow) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Time, clockSize(40f, size.width.value - 28f, size.height.value - 16f - (if (size.height >= 100.dp) 18f + 22f else 0f)))
                if (size.height >= 100.dp) WClock(ClockPart.Date, 13f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEdMMM")
                if (theme.shows(WidgetContent.Weather) && size.height >= 100.dp) {
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 18.dp)
                        HSpace(4.dp)
                        WText(temp(data.snapshot.forecast.current.temperature), 15f)
                    }
                }
            }
        } else {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WClock(ClockPart.Time, clockSize(40f, size.width.value - 28f - 80f, size.height.value - 16f - (if (size.height >= 70.dp) 17f else 0f)))
                    if (size.height >= 70.dp) WClock(ClockPart.Date, 12f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEdMMM")
                }
                if (theme.shows(WidgetContent.Weather)) {
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 26.dp)
                        HSpace(6.dp)
                        WText(temp(data.snapshot.forecast.current.temperature), 24f, weight = WWeight.Light)
                    }
                }
            }
        }
    }
}

/** Saat widget'larında havaya dokununca uygulama o yerle açılır (saate dokununca saat uygulaması). */
@Composable
fun GlanceModifier.clickableApp(): GlanceModifier = this.then(GlanceModifier.clickable(openAppAction()))

/** Bir bölümü farklı renklerle çizmek için (koyu kadran, açık kutu). */
@Composable
fun WithColors(colors: WidgetColors, content: @Composable () -> Unit) {
    val theme = LocalWidgetTheme.current
    CompositionLocalProvider(LocalWidgetTheme provides theme.copy(colors = colors), content = content)
}

/** Koyu bir yüzeyin üstü: beyaz yazı, gölgesiz. */
fun dialColors(base: WidgetColors): WidgetColors = base.copy(
    darkText = false,
    shadow = false,
    scrim = false,
    text = Color.White,
    secondary = Color.White.copy(alpha = 0.8f),
    tertiary = Color.White.copy(alpha = 0.7f),
    rain = Color(0xFFB5E3FF),
    sun = Color(0xFFFDE68A),
    divider = Color.White.copy(alpha = 0.18f),
)

/** Açık bir kutunun üstü: kutunun koyu tonu, gölgesiz. */
fun onLightColors(base: WidgetColors): WidgetColors {
    val ink = base.palette.onContainer
    return base.copy(
        darkText = true,
        shadow = false,
        scrim = false,
        text = ink,
        secondary = ink.copy(alpha = 0.75f),
        tertiary = ink.copy(alpha = 0.6f),
        rain = VividOnLight.Rain,
        sun = VividOnLight.Sun,
        divider = ink.copy(alpha = 0.14f),
    )
}
