package com.alihaydarsayar.communesky.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.TextAlign
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import kotlin.math.min

/**
 * Ev ve konum widget'ı. Üç durum (bkz. HomeDetection):
 * - Evde: ev; büyük sıcaklık, durum, sağda ikon, Y/D, hissedilen; altta 6 saat.
 * - Yakında: ev ana bilgi; altında "Tuzla Merkez'desin · 3 km · hava aynı" hapı ve yağmur uyarısı.
 * - Uzakta: ikiye bölünür, solda bulunduğun yer, sağda ev, altta koyu bir şerit.
 */
@Composable
fun HomeLocationContent(style: WidgetStyleId, input: WidgetInput) {
    val state = input.homeLocation
    if (state == HomeLocationState.Empty) return EmptyContent()
    val size = LocalSize.current
    when {
        size.height.value < TINY_HEIGHT -> HomeTiny(state)
        style == WidgetStyleId.HomeRow || size.height < 100.dp -> HomeRow(state)
        style == WidgetStyleId.HomeList -> HomeList(state)
        else -> HomeSmart(state, input)
    }
}

@Composable
private fun HomeSmart(state: HomeLocationState, input: WidgetInput) {
    val homeAway = input.settings.homeAway
    when (state) {
        is HomeLocationState.AtHome -> MainPlace(state.home)
        is HomeLocationState.Single -> MainPlace(state.place, hint = state.hint)
        is HomeLocationState.Nearby -> if (homeAway.showBothWhenNearby) {
            SplitPlaces(state.here, state.home, state.distanceKm, input)
        } else {
            NearbyPlace(state)
        }
        is HomeLocationState.Away -> SplitPlaces(state.here, state.home, state.distanceKm, input)
        HomeLocationState.Empty -> Unit
    }
}

/** Tek yer, tasarımdaki "Evde" düzeni. */
@Composable
private fun MainPlace(data: WidgetPlaceData, hint: HomeLocationState.Hint? = null) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val big = size.height >= 190.dp
    val roomy = size.height >= 150.dp
    val current = data.snapshot.forecast.current
    val now = data.snapshot.forecast.localNow()
    val today = data.snapshot.forecast.today(now)
    val showHourly = hint == null && roomy && theme.shows(WidgetContent.Hourly)
    val rain = if (hint == null && !showHourly && theme.shows(WidgetContent.RainAlert)) WidgetPhrases.rainAlert(context, data.snapshot) else null
    val vertical = if (big) 16f else 12f
    val bottom = when {
        hint != null -> lineHeightDp(12f, theme) * 2
        showHourly -> 1f + (if (big) 10f else 6f) + if (big) lineHeightDp(12f, theme) + lineHeightDp(13f, theme) + 24f else lineHeightDp(11f, theme) + lineHeightDp(12f, theme) + 20f
        rain != null -> lineHeightDp(13f, theme)
        else -> 0f
    }
    val temperature = temp(current.temperature)
    val tempSize = fitTemperature(
        temperature,
        max = if (big) 64f else 50f,
        widthDp = size.width.value - 40f - 120f,
        heightDp = size.height.value - 2 * vertical - bottom - lineHeightDp(if (big) 15f else 14f, theme) - lineHeightDp(if (big) 15f else 13f, theme) - 4f,
        theme = theme,
    )
    WidgetSurface(onClick = openAppAction(openHomeSettings = hint == HomeLocationState.Hint.NoHome), horizontal = 20.dp, vertical = vertical.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaceLabel(data, if (big) 15f else 14f)
                        if (theme.shows(WidgetContent.Clock)) {
                            WText(" · ", 14f, color = theme.colors.secondary)
                            WClock(ClockPart.Time, 14f, weight = WWeight.Medium, color = theme.colors.secondary)
                        }
                    }
                    WText(temperature, tempSize, weight = WWeight.Light)
                    WText(context.getString(weatherDescriptionRes(current.weatherCode)), if (big) 15f else 13f, color = theme.colors.secondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    CurrentGlyph(data.snapshot, if (big) 52.dp else 40.dp)
                    VSpace(if (big) 6.dp else 3.dp)
                    if (theme.shows(WidgetContent.HighLow) && today != null) {
                        WText(highLow(today.maxTemperature, today.minTemperature), 13f, align = TextAlign.End)
                    }
                    if (theme.shows(WidgetContent.FeelsLike)) {
                        WText(
                            context.getString(R.string.widget_feels_like, temp(current.apparentTemperature)),
                            13f,
                            color = theme.colors.secondary,
                            align = TextAlign.End,
                        )
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            when {
                hint != null -> HintLine(hint)
                showHourly -> {
                    HDivider()
                    VSpace(if (big) 10.dp else 6.dp)
                    HourlyStrip(data, count = if (size.width >= 300.dp) 6 else 5, compact = !big)
                }
                rain != null -> RainLine(rain)
            }
        }
    }
}

/** Yakında: ev ana bilgi; altta bulunduğun yerin hapı ve yağmur uyarısı. */
@Composable
private fun NearbyPlace(state: HomeLocationState.Nearby) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val big = size.height >= 190.dp
    val roomy = size.height >= 150.dp
    val home = state.home
    val current = home.snapshot.forecast.current
    val today = home.snapshot.forecast.today(home.snapshot.forecast.localNow())
    val unit = LocalAppSettings.current.temperatureUnit
    val vertical = if (big) 16f else 12f
    val rain = if (theme.shows(WidgetContent.RainAlert)) WidgetPhrases.rainAlert(context, home.snapshot) else null
    // Hap tek satıra sığmazsa yazı küçülür, yine sığmazsa hava karşılaştırması düşer.
    val pillWidth = size.width.value - 40f - 43f
    var pillText = WidgetPhrases.nearbyPill(context, state.here, home, state.distanceKm, unit)
    var pillSize = 13f
    if (estimateWidthDp(pillText, pillSize, theme) > pillWidth) pillSize = 12f
    if (estimateWidthDp(pillText, pillSize, theme) > pillWidth) pillText = pillText.substringBeforeLast(" · ")
    val bottom = (if (roomy) lineHeightDp(pillSize, theme) + 12f else 0f) + (if (rain != null) lineHeightDp(13f, theme) + 8f else 0f)
    val temperature = temp(current.temperature)
    val tempSize = fitTemperature(
        temperature,
        max = if (big) 64f else 50f,
        widthDp = size.width.value - 40f - 60f,
        heightDp = size.height.value - 2 * vertical - bottom - lineHeightDp(if (big) 15f else 14f, theme) - lineHeightDp(if (big) 15f else 13f, theme) - 4f,
        theme = theme,
    )
    WidgetSurface(onClick = openAppAction(), horizontal = 20.dp, vertical = vertical.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    PlaceLabel(home, if (big) 15f else 14f)
                    WText(temperature, tempSize, weight = WWeight.Light)
                    val detail = buildList {
                        add(context.getString(weatherDescriptionRes(current.weatherCode)))
                        if (theme.shows(WidgetContent.HighLow) && today != null) add(highLow(today.maxTemperature, today.minTemperature))
                    }.joinToString(" · ")
                    WText(detail, if (big) 15f else 13f, color = theme.colors.secondary)
                }
                CurrentGlyph(home.snapshot, if (big) 52.dp else 40.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (roomy) {
                Pill {
                    WIcon(R.drawable.ic_wl_pin, 14.dp)
                    HSpace(7.dp)
                    WText(pillText, pillSize)
                }
            }
            if (rain != null) {
                VSpace(8.dp)
                RainLine(rain)
            }
        }
    }
}

/** Uzakta: dikey ikiye bölünmüş; her yarı kendi havasının gradyanıyla. Altta koyu şerit. */
@Composable
private fun SplitPlaces(here: WidgetPlaceData, home: WidgetPlaceData, distanceKm: Double, input: WidgetInput) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val allItems = WidgetPhrases.awayItems(context, here, home, distanceKm, input.settings.homeAway, input.settings.temperatureUnit)
    val showStrip = allItems.isNotEmpty() && size.height >= 150.dp
    val stripHeight = 42.dp
    val sky = theme.style.background == BackgroundKind.Sky && theme.backgroundAlpha > 0f
    val halfHeight = if (showStrip) size.height - stripHeight else size.height
    // Şerit sığmazsa yazı küçülür; yine sığmazsa önce mesafe, sonra sıcaklık farkı düşer (yağmur en önemlisi).
    val stripWidth = size.width.value - 40f
    var items = allItems
    var stripText = 13f
    fun stripFits(list: List<WidgetPhrases.AwayItem>, sp: Float) =
        list.sumOf { estimateWidthDp(it.text, sp, theme).toDouble() + if (it.rain) 19.0 else 0.0 } + 14.0 * (list.size - 1) <= stripWidth
    if (!stripFits(items, stripText)) stripText = 12f
    while (items.size > 1 && !stripFits(items, stripText)) items = items.drop(1)
    WidgetSurface(onClick = openAppAction(), horizontal = 0.dp, vertical = 0.dp, drawBackground = !sky) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Half(here, GlanceModifier.defaultWeight().fillMaxHeight(), sky, halfHeight.value, left = true, roundBottom = !showStrip)
                if (!sky) VDivider(GlanceModifier.fillMaxHeight().padding(vertical = 14.dp))
                Half(home, GlanceModifier.defaultWeight().fillMaxHeight(), sky, halfHeight.value, left = false, roundBottom = !showStrip)
            }
            if (showStrip) {
                val stripColor = when {
                    !sky -> theme.colors.surface
                    theme.colors.darkText -> lerp(theme.colors.palette.mid, Color.White, 0.45f).copy(alpha = theme.backgroundAlpha)
                    else -> theme.colors.strip.copy(alpha = theme.colors.strip.alpha * theme.backgroundAlpha)
                }
                Row(
                    GlanceModifier.fillMaxWidth().height(stripHeight).background(stripColor).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Her öğe tek bir satır: Glance bir satırda en çok 10 öğe çizer.
                    items.forEachIndexed { index, item ->
                        Row(GlanceModifier.padding(start = if (index > 0) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.rain) {
                                WIcon(R.drawable.ic_wl_drop, 14.dp, theme.colors.rain)
                                HSpace(5.dp)
                            }
                            WText(item.text, stripText, color = if (item.rain) theme.colors.rain else theme.colors.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Half(data: WidgetPlaceData, modifier: GlanceModifier, sky: Boolean, heightDp: Float, left: Boolean, roundBottom: Boolean) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    var m = modifier
    if (sky) {
        val colors = if (theme.style.colorTheme == ColorTheme.Sky) {
            WidgetPalettes.sky(data.snapshot.skyTheme()).let { listOf(it.top, it.bottom) }
        } else {
            theme.colors.palette.gradient
        }
        m = m.background(
            imageProvider = ImageProvider(
                gradientBitmap(
                    colors, theme.style.corners.radius,
                    widthDp = size.width.value / 2f, heightDp = heightDp,
                    roundLeft = left, roundRight = !left, roundBottom = roundBottom,
                ),
            ),
            alpha = theme.backgroundAlpha,
        )
    }
    val current = data.snapshot.forecast.current
    val temperature = temp(current.temperature)
    // Sıcaklık hem yarının genişliğine (ikon yanında) hem yüksekliğine sığacak boyutta.
    val iconSize = if (heightDp >= 110f) 40f else 30f
    val textWidth = size.width.value / 2f - 36f - iconSize - 6f
    val textHeight = heightDp - 26f - lineHeightDp(14f, theme) - lineHeightDp(13f, theme) - 6f
    val tempSize = min(fitWidth(temperature, 52f, textWidth, theme), textHeight / (1.2f * theme.style.textSize.scale))
    Column(m.padding(start = if (left) 20.dp else 16.dp, end = if (left) 16.dp else 20.dp, top = 14.dp, bottom = 12.dp)) {
        PlaceLabel(data, 14f)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WText(temperature, tempSize, weight = WWeight.Light)
            Spacer(GlanceModifier.defaultWeight())
            CurrentGlyph(data.snapshot, iconSize.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        WText(context.getString(weatherDescriptionRes(current.weatherCode)), 13f, color = theme.colors.secondary)
    }
}

/** 4×1: solda bulunduğun yer, ortada mesafe ve ok, sağda ev. Evdeyken tek yer. */
@Composable
private fun HomeRow(state: HomeLocationState) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    WidgetSurface(onClick = openAppAction(), horizontal = 20.dp, vertical = 8.dp, contentAlignment = Alignment.CenterStart) {
        when (state) {
            is HomeLocationState.Away -> TwoPlaceRow(state.here, state.home, state.distanceKm)
            is HomeLocationState.Nearby -> TwoPlaceRow(state.here, state.home, state.distanceKm)
            is HomeLocationState.AtHome -> SinglePlaceRow(state.home)
            is HomeLocationState.Single -> SinglePlaceRow(state.place)
            HomeLocationState.Empty -> WText(context.getString(R.string.widget_no_data), 13f, color = theme.colors.secondary)
        }
    }
}

@Composable
private fun TwoPlaceRow(here: WidgetPlaceData, home: WidgetPlaceData, distanceKm: Double) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    // Yer adları sığmıyorsa önce hava ikonları kalkar.
    val column = (LocalSize.current.width.value - 40f - 64f) / 2f
    val hereTemp = temp(here.snapshot.forecast.current.temperature)
    val homeTemp = temp(home.snapshot.forecast.current.temperature)
    fun needs(data: WidgetPlaceData, temperature: String) = maxOf(
        estimateWidthDp(data.snapshot.displayName(context), 13f, theme) + 18f,
        estimateWidthDp(temperature, 32f, theme),
    ) + 42f
    val wide = LocalSize.current.width >= 250.dp && needs(here, hereTemp) <= column && needs(home, homeTemp) <= column
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                CurrentGlyph(here.snapshot, 32.dp)
                HSpace(10.dp)
            }
            Column {
                WText(temp(here.snapshot.forecast.current.temperature), 32f, weight = WWeight.Light)
                PlaceLabel(here, 13f, weight = WWeight.Regular, color = theme.colors.secondary)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WText(distanceText(context, distanceKm), 12f, color = theme.colors.tertiary, align = TextAlign.Center)
            WIcon(R.drawable.ic_wl_arrow, if (wide) 48.dp else 32.dp, theme.colors.tertiary, modifier = GlanceModifier.height(8.dp))
        }
        Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.End, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                WText(temp(home.snapshot.forecast.current.temperature), 32f, weight = WWeight.Light, align = TextAlign.End)
                PlaceLabel(home, 13f, weight = WWeight.Regular, color = theme.colors.secondary)
            }
            if (wide) {
                HSpace(10.dp)
                CurrentGlyph(home.snapshot, 32.dp)
            }
        }
    }
}

@Composable
private fun SinglePlaceRow(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        CurrentGlyph(data.snapshot, 32.dp)
        HSpace(10.dp)
        WText(temp(current.temperature), 32f, weight = WWeight.Light)
        HSpace(12.dp)
        Column(GlanceModifier.defaultWeight()) {
            PlaceLabel(data, 13f)
            WText(context.getString(weatherDescriptionRes(current.weatherCode)), 12f, color = theme.colors.secondary)
        }
        if (theme.shows(WidgetContent.HighLow) && today != null && LocalSize.current.width >= 250.dp) {
            WText(highLow(today.maxTemperature, today.minTemperature), 12f, color = theme.colors.secondary, align = TextAlign.End)
        }
    }
}

/** İki satır: bulunduğun yer ve ev (evdeyken tek satır). */
@Composable
private fun HomeList(state: HomeLocationState) {
    val context = LocalContext.current
    val rows: List<WidgetPlaceData> = when (state) {
        is HomeLocationState.Away -> listOf(state.here, state.home)
        is HomeLocationState.Nearby -> listOf(state.here, state.home)
        is HomeLocationState.AtHome -> listOf(state.home)
        is HomeLocationState.Single -> listOf(state.place)
        HomeLocationState.Empty -> emptyList()
    }
    WidgetSurface(onClick = openAppAction(), horizontal = 18.dp, vertical = 12.dp, contentAlignment = Alignment.CenterStart) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            rows.forEachIndexed { index, data ->
                if (index > 0) {
                    VSpace(8.dp)
                    HDivider()
                    VSpace(8.dp)
                }
                ListRow(data)
            }
            if (state is HomeLocationState.AtHome) {
                VSpace(6.dp)
                WText(context.getString(R.string.widget_you_are_home), 12f, color = LocalWidgetTheme.current.colors.secondary)
            }
            if (state is HomeLocationState.Single) {
                VSpace(6.dp)
                HintLine(state.hint)
            }
        }
    }
}

@Composable
private fun ListRow(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val current = data.snapshot.forecast.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            PlaceLabel(data, 14f)
            val detail = buildList {
                add(context.getString(weatherDescriptionRes(current.weatherCode)))
                if (theme.shows(WidgetContent.HighLow) && today != null) add(highLow(today.maxTemperature, today.minTemperature))
            }.joinToString(" · ")
            WText(detail, 12f, color = theme.colors.secondary)
        }
        CurrentGlyph(data.snapshot, 30.dp)
        HSpace(8.dp)
        WText(temp(current.temperature), 30f, weight = WWeight.Light, align = TextAlign.End)
    }
}

/** Saatlik şerit: saat, ikon, sıcaklık. */
@Composable
fun HourlyStrip(data: WidgetPlaceData, count: Int, compact: Boolean = false) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val forecast = data.snapshot.forecast
    val hours = forecast.upcomingHours(forecast.localNow(), count = count)
    val formatter = timeFormatter(context)
    Row(GlanceModifier.fillMaxWidth()) {
        hours.forEachIndexed { index, hour ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                WText(
                    if (index == 0) context.getString(R.string.now) else hour.time.format(formatter),
                    if (compact) 11f else 12f,
                    color = theme.colors.secondary,
                    align = TextAlign.Center,
                )
                VSpace(if (compact) 2.dp else 3.dp)
                WeatherGlyph(hour.condition, !hour.isDay, if (compact) 16.dp else 18.dp)
                VSpace(if (compact) 2.dp else 3.dp)
                WText(
                    temp(if (index == 0) forecast.current.temperature else hour.temperature),
                    if (compact) 12f else 13f,
                    weight = WWeight.Medium,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

/** Yağmur uyarısı satırı: damla ikonu ve mavi yazı. */
@Composable
fun RainLine(text: String, size: Float = 13f) {
    val theme = LocalWidgetTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WIcon(R.drawable.ic_wl_drop, (size + 2).dp, theme.colors.rain)
        HSpace(7.dp)
        WText(text, size, color = theme.colors.rain)
    }
}

/** Ev yoksa ya da konum izni yoksa küçük bir not. */
@Composable
fun HintLine(hint: HomeLocationState.Hint) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WIcon(if (hint == HomeLocationState.Hint.NoHome) R.drawable.ic_wl_home else R.drawable.ic_wl_pin, 13.dp, theme.colors.tertiary)
        HSpace(6.dp)
        WText(
            context.getString(
                if (hint == HomeLocationState.Hint.NoHome) R.string.widget_hint_no_home else R.string.widget_hint_no_location,
            ),
            12f,
            color = theme.colors.tertiary,
            maxLines = 2,
        )
    }
}

/** En küçük boyut: tek satır; uzaktayken yanında evin sıcaklığı. */
@Composable
private fun HomeTiny(state: HomeLocationState) {
    val context = LocalContext.current
    when (state) {
        is HomeLocationState.Away -> TinyRow(state.here, extra = "${temp(state.home.snapshot.forecast.current.temperature)} ${state.home.snapshot.displayName(context)}")
        is HomeLocationState.Nearby -> TinyRow(state.home)
        is HomeLocationState.AtHome -> TinyRow(state.home)
        is HomeLocationState.Single -> TinyRow(state.place)
        HomeLocationState.Empty -> EmptyContent()
    }
}
