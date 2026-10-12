package com.alihaydarsayar.communesky.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
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
import androidx.glance.layout.width
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
    // Stil "biraz" sığmıyorsa kendini küçültür; yedek düzene yalnızca gerçekten sığmayacak boyutta
    // geçilir (bkz. WidgetScale). Launcher'lar ölçüyü olduğundan küçük bildirebildiği için aralık geniştir.
    val compact = LocalWidgetTheme.current.compact
    when (style) {
        WidgetStyleId.ClockRadial -> if (compact) ClockCompact(place!!) else ClockRadial(place!!)
        WidgetStyleId.ClockSS -> if (compact) ClockCompact(place!!) else ClockSsFace(place!!)
        WidgetStyleId.ClockAnalog, WidgetStyleId.ClockField, WidgetStyleId.ClockRing ->
            if (compact) ClockCompact(place!!) else ClockAnalogFace(style, place!!)
        WidgetStyleId.ClockBold -> if (compact) ClockCompact(place!!) else ClockBold(place!!)
        WidgetStyleId.ClockCard -> if (compact) ClockCompact(place!!) else ClockCard(place!!)
        WidgetStyleId.ClockLine -> if (compact) ClockCompact(place!!) else ClockLine(place!!)
        WidgetStyleId.ClockGlance -> if (compact) ClockCompact(place!!) else ClockGlance(place!!)
        else -> if (compact) {
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
    val scale = theme.style.textSize.scale * theme.fontScaleDp
    // Genişlikte %10 pay: launcher'ın yazı tipi ve bildirdiği ölçü tahminden biraz farklı olabilir.
    return min(max, min(widthDp * FIT_MARGIN / (ems * scale), heightDp / (1.2f * scale))).coerceAtLeast(18f)
}

/**
 * Tarih iskeleti, [widthDp] genişliğe sığan en uzun hâliyle: "10 Ekim Cumartesi" → "10 Ekim Cmt" → "10 Ekim".
 * Bugünün tarihi gerçek genişliğiyle ölçülür; widget en geç bir saat sonra yeniden çizildiği için
 * gün değişince biçim de yenilenir.
 */
@Composable
private fun dateSkeletonFor(widthDp: Float, sizeSp: Float, short: Boolean = false): String {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    // [short]: küçük düzenlerde "10 Eki Cmt" ile başlanır; o da sığmazsa "10 Eki".
    val choices = if (short) listOf("EEEdMMM") else listOf("EEEEdMMMM", "EEEdMMMM")
    return choices.firstOrNull { measureWidthDp(context, todayText(context, it), sizeSp, theme) <= widthDp } ?: if (short) "dMMM" else "dMMMM"
}

/** Tasarım "Saat ve hava": solda saat ve tarih, sağda hava; altta yağmur uyarısı. */
@Composable
private fun ClockWeather(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    val rain = if (theme.shows(WidgetContent.RainAlert) && size.height >= 150.dp) WidgetPhrases.rainAlert(context, data.snapshot) else null
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val inner = size.width.value - 32f
    val pinned = theme.shows(WidgetContent.PlaceName) && placeIcon(data) != null
    // Sağ sütun: ikon + sıcaklık, altında "yer · Y/D". Ayrıntı gerçek genişliğiyle ölçülür; saate en az
    // 48 sp'lik yer kalmıyorsa (büyük yazı boyutu, uzun yer adı) sondan düşer.
    val clockFloor = theme.clockEms * 48f * theme.style.textSize.scale * theme.fontScaleDp
    fun detailWidth(text: String) = if (text.isEmpty()) 0f else measureWidthDp(context, text, 13f, theme) + (if (pinned) 16f else 0f)
    val detailParts = buildList {
        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
        if (theme.shows(WidgetContent.HighLow) && today != null) add(highLow(today.maxTemperature, today.minTemperature))
    }
    val detail = if (!theme.shows(WidgetContent.Weather)) "" else generateSequence(detailParts) { if (it.size > 1) it.dropLast(1) else null }
        .map { it.joinToString(" · ") }
        .firstOrNull { detailWidth(it) + 12f <= inner - clockFloor }
        .orEmpty()
    val side = if (!theme.shows(WidgetContent.Weather)) 0f else maxOf(38f + 8f + estimateWidthDp(temperature, 38f, theme), detailWidth(detail)) + 12f
    val clockWidth = inner - side
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WClock(ClockPart.Time, clockSize(72f, clockWidth, size.height.value - 32f - lineHeightDp(15f, theme) - (if (rain != null) lineHeightDp(13f, theme) + 30f else 0f)))
                    WClock(ClockPart.Date, 15f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = dateSkeletonFor(clockWidth, 15f))
                }
                if (theme.shows(WidgetContent.Weather)) {
                    Column(GlanceModifier.clickableApp(), horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CurrentGlyph(data.snapshot, 38.sd)
                            HSpace(8.sd)
                            WText(temperature, 38f, weight = WWeight.Light)
                        }
                        if (detail.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pinned) placeIcon(data)?.let {
                                    WIcon(it, 12.sd, theme.colors.secondary)
                                    HSpace(4.sd)
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
                    GlanceModifier.fillMaxWidth().rounded(theme.colors.surface.copy(alpha = 0.12f), 16.sd).padding(horizontal = 12.sd, vertical = 9.sd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WIcon(R.drawable.ic_wl_drop, 16.sd, theme.colors.rain)
                    HSpace(8.sd)
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
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd, contentAlignment = Alignment.CenterStart) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (theme.shows(WidgetContent.PlaceName)) PlaceLabel(data, 16f, GlanceModifier.clickableApp(), iconAfter = true)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Time, clockSize(96f, size.width.value - 32f - (if (showWeather) 82f else 0f), size.height.value - 32f - (if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(16f, theme) else 0f) - lineHeightDp(22f, theme) - 4f), GlanceModifier.defaultWeight())
                if (showWeather) {
                    Box(GlanceModifier.clickableApp().padding(end = 12.sd)) { CurrentGlyph(data.snapshot, min(70f, size.height.value * 0.36f).sd) }
                }
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, 17f, GlanceModifier.defaultWeight(), weight = WWeight.Medium, dateSkeleton = "dMMMMEEEE")
                if (showWeather) {
                    WText(temp(data.snapshot.forecast.current.temperature), 22f, GlanceModifier.padding(end = 18.sd), weight = WWeight.Medium)
                }
            }
        }
    }
}

/** Tasarım "Yan yana": sağa yaslı saat, tarih ve yer; ince dikey çizgi; ikon, sıcaklık, Y/D. */
@Composable
private fun ClockSide(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val today = data.snapshot.forecast.today(data.snapshot.forecast.localNow())
    val weather = theme.shows(WidgetContent.Weather)
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val inner = size.width.value - 32f
    // Hava sütununun genişliği önce ayrılır: sıcaklık asla kısaltılmaz. Y/D sığmıyorsa o düşer.
    val gap = 20f + 1.5f + 20f
    val tempWidth = estimateWidthDp(temperature, 38f, theme) / FIT_MARGIN
    // Ölçülen genişliğe %10 pay eklenir: küçültülmüş ya da büyütülmüş yazı ölçümden birkaç piksel geniş çıkabiliyor.
    val highLowText = if (weather && theme.shows(WidgetContent.HighLow) && today != null) highLow(today.maxTemperature, today.minTemperature) else null
    val highLowWidth = highLowText?.let { measureWidthDp(context, it, 13f, theme) / FIT_MARGIN } ?: 0f
    val highLow = highLowText?.takeIf { highLowWidth <= inner * 0.4f }
    val weatherWidth = if (weather) maxOf(46f, tempWidth, if (highLow != null) highLowWidth else 0f) else 0f
    // Saat sütunu kalan yere sığar; tarih de o genişliğe göre kısalır (uzun gün ve ay adları taşmasın).
    val clockWidth = inner - (if (weather) gap + weatherWidth else 0f)
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                WClock(ClockPart.Time, clockSize(88f, clockWidth, size.height.value - 32f - lineHeightDp(17f, theme) - (if (theme.shows(WidgetContent.PlaceName)) lineHeightDp(15f, theme) else 0f)), contentAlignment = Alignment.CenterEnd)
                WClock(ClockPart.Date, 17f, weight = WWeight.Regular, dateSkeleton = dateSkeletonFor(clockWidth, 17f), contentAlignment = Alignment.CenterEnd)
                if (theme.shows(WidgetContent.PlaceName)) PlaceLabel(data, 15f, GlanceModifier.clickableApp(), weight = WWeight.Regular, color = theme.colors.secondary, iconAfter = true)
            }
            if (weather) {
                HSpace(20.sd)
                VDivider(GlanceModifier.height(min(120f, size.height.value * 0.7f).sd), width = 1.5.sd)
                HSpace(20.sd)
                Column(GlanceModifier.width(weatherWidth.sd).clickableApp()) {
                    CurrentGlyph(data.snapshot, 46.sd)
                    WText(temperature, 38f, weight = WWeight.Light)
                    if (highLow != null) WText(highLow, 13f, color = theme.colors.secondary)
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
    // Tarih bugünkü gerçek yazısıyla ölçülür ("Sun, October 11", "9 Ekim Cum"dan uzundur).
    var used = measureWidthDp(context, todayText(context, "dMMMMEEE"), 15f, theme) / FIT_MARGIN + (if (theme.shows(WidgetContent.Weather)) 41f + estimateWidthDp(temperature, 15f, theme) else 0f)
    val showRain = rainTime != null && used + 39f + estimateWidthDp(rainTime, 15f, theme) <= available
    if (showRain) used += 39f + estimateWidthDp(rainTime!!, 15f, theme)
    val showPlace = theme.shows(WidgetContent.PlaceName) && used + 41f + estimateWidthDp(data.snapshot.displayName(context), 15f, theme) <= available
    WidgetSurface(horizontal = 16.sd, vertical = 8.sd, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WClock(ClockPart.Time, clockSize(68f, size.width.value - 32f, size.height.value - 16f - lineHeightDp(15f, theme)), contentAlignment = Alignment.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WClock(ClockPart.Date, 15f, weight = WWeight.Regular, dateSkeleton = "dMMMMEEE")
                if (showPlace) {
                    LineSeparator()
                    PlaceLabel(data, 15f, GlanceModifier.clickableApp(), weight = WWeight.Regular)
                }
                if (theme.shows(WidgetContent.Weather)) {
                    LineSeparator()
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 16.sd)
                        HSpace(4.sd)
                        WText(temperature, 15f)
                    }
                }
                if (showRain) {
                    rainTime?.let {
                        LineSeparator()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WIcon(R.drawable.ic_wl_drop, 14.sd, theme.colors.rain)
                            HSpace(4.sd)
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
    Box(GlanceModifier.padding(horizontal = 10.sd)) { VDivider(GlanceModifier.height(14.sd)) }
}

/** Tasarım "Kalın": açık renkli dolu kare (Android 12+ duvar kâğıdı rengi), alt alta kalın saat. */
@Composable
private fun ClockBold(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val palette = theme.colors.palette
    val filled = ClockDigitColors.boldFilled(theme.style)
    val hourColor = theme.hourColor
    val minuteColor = theme.minuteColor
    val digits = clockSize(72f, size.width.value - 32f, (size.height.value - 32f - lineHeightDp(15f, theme) - 6f) / 2f, ems = theme.clockEms / 2f)
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
                        WIcon(current.condition.lineIconRes(!current.isDay), 16.sd, hourColor)
                        HSpace(4.sd)
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
                    .rounded(palette.container.copy(alpha = theme.backgroundAlpha), theme.style.corners.radius(context) + 12.sd)
                    .padding(horizontal = 16.sd, vertical = 16.sd),
            ) {
                WithColors(onLightColors(theme.colors)) { content() }
            }
        }
    } else {
        WidgetSurface(horizontal = 16.sd, vertical = 16.sd) { content() }
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
    // Sağ sütun: ikon + en uzun satır; widget'ın yarısını geçmez (uzun yer adı kendi içinde kısalır).
    val temps = rows.map { (data, _) -> temp(data.snapshot.forecast.current.temperature) }
    fun sideWidth(withExtra: Boolean) = 38f + 6f + (
        rows.mapIndexed { i, (data, extra) ->
            maxOf(
                estimateWidthDp(temps[i], 24f, theme),
                estimateWidthDp(data.snapshot.displayName(context) + (extra?.takeIf { withExtra }?.let { " · $it" } ?: ""), 13f, theme) + (if (placeIcon(data) != null) 18f else 0f),
            )
        }.maxOrNull() ?: 0f
    )
    // Ek bilgi (mesafe, "evdesin") widget'ın yarısına sığmıyorsa düşer; yazı kesilmez, saat büyük kalır.
    val showExtra = sideWidth(true) <= (size.width.value - 32f) * 0.5f
    val side = min((size.width.value - 32f) * 0.55f, sideWidth(showExtra))
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                WClock(ClockPart.Time, clockSize(84f, size.width.value - 32f - 12f - side, size.height.value - 32f - lineHeightDp(17f, theme)))
                WClock(ClockPart.Date, 17f, weight = WWeight.Regular, dateSkeleton = dateSkeletonFor(size.width.value - 32f - 12f - side, 17f))
            }
            HSpace(12.sd)
            Column(GlanceModifier.width(side.sd)) {
                rows.forEachIndexed { index, (data, extra) ->
                    if (index > 0) VSpace(12.sd)
                    // Her yer kendi sayfasını açar.
                    Row(GlanceModifier.fillMaxWidth().clickable(openPlaceAction(data.snapshot.placeId)), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, 30.sd)
                        HSpace(8.sd)
                        Column {
                            WText(temp(data.snapshot.forecast.current.temperature), 24f)
                            PlaceLabel(data, 13f, weight = WWeight.Regular, suffix = extra?.takeIf { showExtra }?.let { " · $it" })
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
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    WClock(ClockPart.Time, clockSize(60f, size.width.value - 32f - 100f, size.height.value - 32f - lineHeightDp(14f, theme) - min(62f, size.height.value * 0.22f) - lineHeightDp(13f, theme) - 8f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WClock(ClockPart.Date, 14f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEEdMMMM")
                        val place = " · " + data.snapshot.displayName(context)
                        // Sağdaki ikon ve sıcaklıktan kalan yer; tarih ve yer adı gerçek genişlikleriyle ölçülür.
                        val weatherWidth = if (theme.shows(WidgetContent.Weather)) 30f + 8f + estimateWidthDp(temp(current.temperature), 30f, theme) + 8f else 0f
                        val lineWidth = measureWidthDp(context, todayText(context, "EEEEdMMMM"), 14f, theme) + measureWidthDp(context, place, 14f, theme)
                        if (theme.shows(WidgetContent.PlaceName) && lineWidth <= size.width.value - 32f - weatherWidth) {
                            WText(place, 14f, color = theme.colors.secondary)
                        }
                    }
                }
                if (theme.shows(WidgetContent.Weather)) {
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        val icon = if (phase?.isDay == false && current.condition == WeatherCondition.Clear) R.drawable.ic_wl_clear_night else current.condition.lineIconRes(!current.isDay)
                        WIcon(icon, 30.sd, theme.colors.sun)
                        HSpace(8.sd)
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
    val width = size.width.value - 32f
    Box(GlanceModifier.fillMaxWidth().height(heightDp.sd)) {
        // Ufuk çizgisi: alttan 4 dp yukarıda, 1 dp.
        Box(GlanceModifier.fillMaxSize().padding(bottom = 3.5.sd), contentAlignment = Alignment.BottomStart) {
            Box(GlanceModifier.fillMaxWidth().height(1.sd).background(theme.colors.text.copy(alpha = 0.15f))) {}
        }
        // Yay tek kanallı iki maskedir (tam çözünürlük): kesikli yol ve geçen kısım; renkleri burada verilir.
        TintedLayer(ImageProvider(WidgetBitmaps.sunArcDashed(width, heightDp, bitmapDensity(), 255)), theme.colors.text.copy(alpha = 0.22f))
        TintedLayer(ImageProvider(WidgetBitmaps.sunArcDone(phase.progress, width, heightDp, bitmapDensity(), 255)), theme.colors.sun)
    }
    VSpace(2.sd)
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
    WidgetSurface(horizontal = 16.sd, vertical = 12.sd, contentAlignment = Alignment.CenterStart) {
        Column(verticalAlignment = Alignment.CenterVertically) {
            val rowHeight = lineHeightDp(15f, theme) + 12f
            WClock(ClockPart.Date, clockSize(30f, size.width.value - 32f, size.height.value - 24f - 8f - rowHeight, ems = 11f), weight = WWeight.Regular, dateSkeleton = "EEEEdMMMM")
            VSpace(8.sd)
            Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                if (theme.shows(WidgetContent.Weather)) {
                    CurrentGlyph(data.snapshot, 20.sd)
                    HSpace(8.sd)
                    WText(temp(data.snapshot.forecast.current.temperature), 17f)
                }
                if (theme.shows(WidgetContent.RainAlert)) {
                    val weatherWidth = if (theme.shows(WidgetContent.Weather)) 28f + estimateWidthDp(temp(data.snapshot.forecast.current.temperature), 17f, theme) + 12f else 0f
                    // Uzun cümle sığmıyorsa kısa uyarı.
                    listOfNotNull(WidgetPhrases.rainStartSentence(context, data.snapshot), WidgetPhrases.rainShort(context, data.snapshot))
                        .firstOrNull { estimateWidthDp(it, 15f, theme) + 15f + 8f + 24f <= size.width.value - 32f - weatherWidth }
                        ?.let { text ->
                        HSpace(12.sd)
                        Pill(color = if (theme.colors.darkText) theme.colors.pill else Color(0xFF141830).copy(alpha = 0.35f)) {
                            WIcon(R.drawable.ic_wl_drop, 15.sd, theme.colors.rain)
                            HSpace(8.sd)
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
    WidgetSurface(horizontal = 16.sd, vertical = 16.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            WClock(ClockPart.Time, clockSize(52f, size.width.value - 32f, size.height.value - 32f - lineHeightDp(13f, theme) - 9f - lineHeightDp(24f, theme) - lineHeightDp(12f, theme) - 4f))
            // Uzun tarih ("Wednesday, September 30") dar kartta sıkışır: sığmıyorsa kısa yazım ("Wed, Sep 30").
            val longDate = measureWidthDp(context, todayText(context, "EEEEdMMMM"), 13f, theme) * 1.06f <= size.width.value - 32f
            WClock(ClockPart.Date, 13f, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = if (longDate) "EEEEdMMMM" else "EEEdMMM")
            Spacer(GlanceModifier.defaultWeight())
            HDivider()
            VSpace(8.sd)
            Row(GlanceModifier.fillMaxWidth().clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    WText(temp(current.temperature), 24f, weight = WWeight.Light)
                    val detail = buildList {
                        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
                        if (size.width >= 200.dp || !theme.shows(WidgetContent.PlaceName)) add(context.getString(weatherDescriptionRes(current.weatherCode)))
                    }.joinToString(" · ")
                    WText(detail, 12f, color = theme.colors.secondary)
                }
                CurrentGlyph(data.snapshot, 32.sd)
            }
        }
    }
}

/**
 * Küçük boyutlar için ortak yedek düzen: saat ve tarih, yanında (ya da altında) sıcaklık. Widget'ı
 * doldurur ve ortalanır: saat, alanın elverdiği kadar büyür; asla sol üstte küçük kalmaz.
 */
@Composable
private fun ClockCompact(data: WidgetPlaceData) {
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
    val weather = theme.shows(WidgetContent.Weather)
    val temperature = temp(data.snapshot.forecast.current.temperature)
    // Dar ya da yüksekse alt alta; geniş ve alçaksa yan yana.
    val stacked = w < 160f || h >= 88f
    WidgetSurface(horizontal = 12.sd, vertical = 8.sd, contentAlignment = Alignment.Center) {
        if (stacked) {
            val showDate = h >= 72f
            val showWeather = weather && h >= 96f
            val dateSize = (h * 0.11f).coerceIn(11f, 15f)
            val weatherSize = (h * 0.13f).coerceIn(12f, 18f)
            val below = (if (showDate) lineHeightDp(dateSize, theme) else 0f) + (if (showWeather) lineHeightDp(weatherSize, theme) + 4f else 0f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                // Saat ve dakika ayrı renkteyse satır biraz daha yüksek çizilir (bkz. WClock); pay bırakılır.
                WClock(ClockPart.Time, clockSize(h * 0.6f, (w - 24f) * 0.92f, (h - 16f - below) * 0.9f), contentAlignment = Alignment.Center)
                if (showDate) WClock(ClockPart.Date, dateSize, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = dateSkeletonFor(w - 24f, dateSize, short = true), contentAlignment = Alignment.Center)
                if (showWeather) {
                    VSpace(4.sd)
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, (weatherSize * 1.15f).sd)
                        HSpace(4.sd)
                        WText(temperature, weatherSize)
                    }
                }
            }
        } else {
            // Tek sıra: saat (altında tarih) ve hava; aralarındaki boşlukla birlikte genişliği doldurur.
            val showDate = h >= 56f
            val dateSize = 12f
            val weatherSize = (h * 0.34f).coerceIn(14f, 26f)
            val weatherWidth = if (weather) weatherSize * 1.1f + 6f + estimateWidthDp(temperature, weatherSize, theme) + 16f else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WClock(ClockPart.Time, clockSize(h * 0.62f, (w - 24f - weatherWidth) * 0.92f, (h - 16f - (if (showDate) lineHeightDp(dateSize, theme) else 0f)) * 0.9f), contentAlignment = Alignment.Center)
                    if (showDate) WClock(ClockPart.Date, dateSize, weight = WWeight.Regular, color = theme.colors.secondary, dateSkeleton = "EEEdMMM", contentAlignment = Alignment.Center)
                }
                if (weather) {
                    HSpace(16.sd)
                    Row(GlanceModifier.clickableApp(), verticalAlignment = Alignment.CenterVertically) {
                        CurrentGlyph(data.snapshot, (weatherSize * 1.1f).sd)
                        HSpace(6.sd)
                        WText(temperature, weatherSize)
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
