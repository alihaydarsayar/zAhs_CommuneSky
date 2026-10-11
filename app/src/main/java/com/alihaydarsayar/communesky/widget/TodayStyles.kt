package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.lifestyle.EvaluatedTip
import com.alihaydarsayar.communesky.lifestyle.SunscreenTip
import com.alihaydarsayar.communesky.lifestyle.TipStatus
import com.alihaydarsayar.communesky.lifestyle.TodayAdvice
import com.alihaydarsayar.communesky.lifestyle.UmbrellaTip
import com.alihaydarsayar.communesky.lifestyle.sentence
import com.alihaydarsayar.communesky.model.today.AndroidStrings
import com.alihaydarsayar.communesky.model.today.ClothingAdvice
import com.alihaydarsayar.communesky.model.today.Layer
import com.alihaydarsayar.communesky.model.today.WearText
import com.alihaydarsayar.communesky.model.today.list
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

// "Bugün için" widget'ları: Küçük ve Yazı türlerinde "Ne giysem" stili ve 4×2 "Bugün için" türü.
// Hepsi vektör ikon ve yazıdır; hesap uygulamadaki kartla aynıdır ([TodayAdvice]).

/** Uygulamayı, widget'ın yeri seçili ve "Bugün için" sayfası açık olarak başlatır. */
@Composable
fun openTodayAction(): Action {
    val placeId = LocalWidgetPlaceId.current
    return if (placeId != null) {
        actionStartActivity<MainActivity>(actionParametersOf(MainActivity.OpenPlaceKey to placeId, MainActivity.OpenTodayKey to true))
    } else {
        actionStartActivity<MainActivity>(actionParametersOf(MainActivity.OpenTodayKey to true))
    }
}

@Composable
private fun todayAdvice(data: WidgetPlaceData): TodayAdvice {
    val forecast = data.snapshot.forecast
    return TodayAdvice.of(forecast, forecast.localNow(), LocalAppSettings.current.wear.shift, data.snapshot.airQualityPoor)
}

/** En kısa ad ("Kazak"): dar widget'larda. */
private fun tinyName(layer: Layer): Int = when (layer) {
    Layer.Light -> R.string.wear_tiny_light
    Layer.TShirt -> R.string.wear_tiny_tshirt
    Layer.LongSleeve -> R.string.wear_tiny_long_sleeve
    Layer.Sweater -> R.string.wear_tiny_sweater
    Layer.Coat -> R.string.wear_tiny_coat
    Layer.HeavyCoat -> R.string.wear_tiny_heavy_coat
    Layer.FullWinter -> R.string.wear_tiny_full_winter
}

/**
 * [candidates] içinden, [width] dp genişlikte en çok [maxLines] satıra sığan ilk metin ve kaplayacağı satır
 * sayısı. Satır sonlarında kelime bölünmediği için genişliğe pay bırakılır. Hiçbiri sığmazsa null: yazı
 * kesilmek yerine hiç gösterilmez.
 */
private fun fitText(
    context: Context,
    candidates: List<String>,
    size: Float,
    width: Float,
    maxLines: Int,
    theme: WidgetTheme,
    weight: WWeight = WWeight.Regular,
): Pair<String, Int>? {
    if (maxLines < 1 || width <= 0f) return null
    for (text in candidates) {
        val measured = measureWidthDp(context, text, size, theme, weight)
        val lines = if (measured <= width) 1 else ceil(measured * WRAP_MARGIN / width).toInt()
        // En uzun kelime tek başına satıra sığmalı.
        val longest = text.split(' ').maxOf { measureWidthDp(context, it, size, theme, weight) }
        if (lines <= maxLines && longest <= width) return text to lines
    }
    return null
}

private const val WRAP_MARGIN = 1.2f

/** Satıra sığan ikonlar: önce katmanın ilk ikonu, sonra ekler (şemsiye…), yer kalırsa katmanın diğer ikonları. */
private fun wearIcons(advice: ClothingAdvice, widthDp: Float, iconDp: Float, gapDp: Float): List<Int> {
    val layer = WearText.icons(advice.layer)
    val ordered = layer.take(1) + advice.extras.sorted().map { WearText.icon(it) } + layer.drop(1)
    val count = ((widthDp + gapDp) / (iconDp + gapDp)).toInt().coerceIn(1, 9)
    return ordered.take(count)
}

@Composable
private fun IconRow(icons: List<Int>, iconDp: Float, gapDp: Float, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        icons.forEachIndexed { index, icon ->
            if (index > 0) HSpace(gapDp.sd)
            WIcon(icon, iconDp.sd)
        }
    }
}

/** Sıcaklığı kullanıcının biriminde, işaretsiz tam sayı olarak verir (cümleler için). */
@Composable
private fun degrees(): (Double) -> Int {
    val unit = LocalAppSettings.current.temperatureUnit
    return { unit.fromCelsius(it).roundToInt() }
}

// --- Küçük: "Ne giysem" ------------------------------------------------------------------------------

/** Kare kutu: yer ve sıcaklık, ikonlar, katmanın adı, varsa "şemsiye al". */
@Composable
fun SmallWear(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val wear = todayAdvice(data).wear ?: return EmptyContent()
    val strings = AndroidStrings(context)
    if (size.height.value < TINY_HEIGHT) return TinyWear(data, wear)

    val pad = 12f
    val width = size.width.value - 2 * pad
    val height = size.height.value - 2 * pad
    val iconDp = (size.height.value * 0.24f).coerceIn(22f, 40f)
    val gap = 8f
    val icons = wearIcons(wear, width, iconDp, gap)
    val temperature = temp(data.snapshot.forecast.current.temperature)
    val name = data.snapshot.displayName(context)
    val headerHeight = lineHeightDp(12f, theme)
    val showName = theme.shows(WidgetContent.PlaceName) &&
        measureWidthDp(context, name, 12f, theme, WWeight.Medium) + 20f + measureWidthDp(context, temperature, 12f, theme) + 6f <= width
    var left = height - headerHeight - iconDp - 8f
    // Katmanın adı: önce tam ad (iki satıra kadar), sığmazsa kısa ad.
    val titleSize = if (size.height.value >= 150f) 15f else 13f
    val title = fitText(
        context,
        listOf(strings.get(WearText.name(wear.layer)), strings.get(tinyName(wear.layer))),
        titleSize, width, min(2, (left / lineHeightDp(titleSize, theme)).toInt()), theme, WWeight.Medium,
    )
    if (title != null) left -= title.second * lineHeightDp(titleSize, theme)
    val extras = wear.extras.sorted().map { strings.get(WearText.name(it)) }
    val take = if (extras.isEmpty()) null else fitText(
        context,
        listOf(sentenceCase(strings.get(R.string.wear_take, strings.list(extras)), context), sentenceCase(strings.get(R.string.wear_take, extras.first()), context)),
        12f, width, min(2, (left / lineHeightDp(12f, theme)).toInt()), theme,
    )
    WidgetSurface(onClick = openTodayAction(), horizontal = pad.sd, vertical = pad.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showName) PlaceLabel(data, 12f, GlanceModifier.defaultWeight(), color = theme.colors.secondary) else Spacer(GlanceModifier.defaultWeight())
                WText(temperature, 12f, color = theme.colors.secondary)
            }
            Spacer(GlanceModifier.defaultWeight())
            IconRow(icons, iconDp, gap)
            Spacer(GlanceModifier.defaultWeight())
            if (title != null) WText(title.first, titleSize, GlanceModifier.fillMaxWidth(), weight = WWeight.Medium, maxLines = title.second)
            if (take != null) WText(take.first, 12f, GlanceModifier.fillMaxWidth(), color = theme.colors.secondary, maxLines = take.second)
        }
    }
}

private fun sentenceCase(text: String, context: Context): String = text.replaceFirstChar { it.titlecase(locale(context)) }

/** Tek satır (çok alçak widget): ikonlar ve sığarsa katmanın kısa adı. */
@Composable
private fun TinyWear(data: WidgetPlaceData, wear: ClothingAdvice) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val strings = AndroidStrings(context)
    val iconDp = min(24f, size.height.value - 14f).coerceAtLeast(14f)
    val width = size.width.value - 24f
    val text = fitText(
        context,
        listOf(strings.get(WearText.name(wear.layer)), strings.get(tinyName(wear.layer))),
        13f, width - iconDp - 8f, 1, theme, WWeight.Medium,
    )?.first
    val used = if (text != null) measureWidthDp(context, text, 13f, theme, WWeight.Medium) + 8f else 0f
    // Yazıdan artan yere ikonlar: en az bir tane.
    val icons = wearIcons(wear, width - used, iconDp, 6f)
    WidgetSurface(onClick = openTodayAction(), horizontal = 12.sd, vertical = 4.sd, contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconRow(icons, iconDp, 6f)
            if (text != null) {
                HSpace(8.sd)
                WText(text, 13f, weight = WWeight.Medium)
            }
        }
    }
}

// --- Yazı: "Ne giysem" -------------------------------------------------------------------------------

/** Arka plansız: solda katmanın ikonu, sağda cümle ve altında yer ile sıcaklık. */
@Composable
fun TextWear(data: WidgetPlaceData) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val wear = todayAdvice(data).wear ?: return EmptyContent()
    if (size.height.value < TINY_HEIGHT) return TinyWear(data, wear)
    val strings = AndroidStrings(context)
    val iconDp = min(34f, size.height.value * 0.34f)
    val width = size.width.value - 24f - iconDp - 10f
    val height = size.height.value - 8f
    val footer = buildList {
        if (theme.shows(WidgetContent.PlaceName)) add(data.snapshot.displayName(context))
        add(temp(data.snapshot.forecast.current.temperature))
    }.joinToString(" · ")
    val showFooter = measureWidthDp(context, footer, 12f, theme) <= width && height >= lineHeightDp(15f, theme) + lineHeightDp(12f, theme)
    val footerHeight = if (showFooter) lineHeightDp(12f, theme) else 0f
    val sentence = fitText(
        context,
        listOf(
            WearText.sentence(wear, strings, degrees()),
            WearText.short(wear, strings),
            strings.get(WearText.name(wear.layer)),
            strings.get(tinyName(wear.layer)),
        ),
        15f, width, min(3, ((height - footerHeight) / lineHeightDp(15f, theme)).toInt()), theme, WWeight.Medium,
    )
    WidgetSurface(onClick = openTodayAction(), horizontal = 12.sd, vertical = 4.sd, contentAlignment = Alignment.CenterStart) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WIcon(WearText.icons(wear.layer).first(), iconDp.sd)
            HSpace(10.sd)
            Column(GlanceModifier.defaultWeight()) {
                if (sentence != null) WText(sentence.first, 15f, GlanceModifier.fillMaxWidth(), weight = WWeight.Medium, maxLines = sentence.second)
                if (showFooter) WText(footer, 12f, color = theme.colors.secondary)
            }
        }
    }
}

// --- Bugün için (4×2) --------------------------------------------------------------------------------

@Composable
fun TodayContent(style: WidgetStyleId, data: WidgetPlaceData) {
    val size = LocalSize.current
    val advice = todayAdvice(data)
    val wear = advice.wear ?: return EmptyContent()
    when {
        size.height.value < TINY_HEIGHT -> TinyWear(data, wear)
        size.height < 126.dp -> TodayBrief(data, wear)
        else -> TodayFull(data, advice, wear)
    }
}

/** Alçak boy: ikonlar ve cümle. */
@Composable
private fun TodayBrief(data: WidgetPlaceData, wear: ClothingAdvice) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val strings = AndroidStrings(context)
    val pad = 12f
    val width = size.width.value - 2 * pad
    val height = size.height.value - 2 * pad
    val iconDp = 22f
    val header = header(context, data, theme, width, iconDp, wear)
    val sentence = fitText(
        context,
        listOf(WearText.sentence(wear, strings, degrees()), WearText.short(wear, strings), strings.get(WearText.name(wear.layer)), strings.get(tinyName(wear.layer))),
        14f, width, min(3, ((height - iconDp - 4f) / lineHeightDp(14f, theme)).toInt()), theme, WWeight.Medium,
    )
    WidgetSurface(onClick = openTodayAction(), horizontal = pad.sd, vertical = pad.sd) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            HeaderRow(data, header, iconDp)
            VSpace(4.sd)
            if (sentence != null) WText(sentence.first, 14f, GlanceModifier.fillMaxWidth(), weight = WWeight.Medium, maxLines = sentence.second)
        }
    }
}

/** Üst satırın içeriği: sığan ikonlar ve (yer kalırsa) yer adı. */
private class Header(val icons: List<Int>, val showPlace: Boolean)

@Composable
private fun header(context: Context, data: WidgetPlaceData, theme: WidgetTheme, width: Float, iconDp: Float, wear: ClothingAdvice): Header {
    val name = data.snapshot.displayName(context)
    val nameWidth = measureWidthDp(context, name, 12f, theme, WWeight.Medium) + 24f
    val all = wearIcons(wear, width, iconDp, 8f)
    val iconsWidth = all.size * (iconDp + 8f)
    val showPlace = theme.shows(WidgetContent.PlaceName) && iconsWidth + nameWidth <= width
    return Header(all, showPlace)
}

@Composable
private fun HeaderRow(data: WidgetPlaceData, header: Header, iconDp: Float) {
    val theme = LocalWidgetTheme.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconRow(header.icons, iconDp, 8f)
        Spacer(GlanceModifier.defaultWeight())
        if (header.showPlace) PlaceLabel(data, 12f, color = theme.colors.secondary)
    }
}

/**
 * Tam düzen: üstte ikonlar ve yer; cümle; altta sabah / öğle / akşam (ikon ve hissedilen sıcaklık) ve yer varsa
 * sağda şemsiye ile güneş kremi göstergeleri.
 */
@Composable
private fun TodayFull(data: WidgetPlaceData, advice: TodayAdvice, wear: ClothingAdvice) {
    val context = LocalContext.current
    val theme = LocalWidgetTheme.current
    val size = LocalSize.current
    val strings = AndroidStrings(context)
    val pad = 14f
    val width = size.width.value - 2 * pad
    val height = size.height.value - 2 * pad
    val iconDp = 24f
    val header = header(context, data, theme, width, iconDp, wear)
    val partsHeight = lineHeightDp(11f, theme) + lineHeightDp(14f, theme)
    val sentence = fitText(
        context,
        listOf(WearText.sentence(wear, strings, degrees()), WearText.short(wear, strings), strings.get(WearText.name(wear.layer)), strings.get(tinyName(wear.layer))),
        14f, width, min(3, ((height - iconDp - 6f - partsHeight - 6f) / lineHeightDp(14f, theme)).toInt()), theme, WWeight.Medium,
    )
    // Göstergeler: şemsiye ve güneş kremi. Dar widget'ta yalnız dilimler gösterilir.
    val indicators = listOf(UmbrellaTip, SunscreenTip).mapNotNull { tip -> advice.tips.firstOrNull { it.tip == tip } }
    val indicatorTexts = indicators.map { context.getString(indicatorLabel(it)) }
    val indicatorWidth = (indicatorTexts.maxOfOrNull { measureWidthDp(context, it, 11f, theme, WWeight.Medium) } ?: 0f) + 14f + 5f
    val partWidth = 62f
    val showIndicators = indicators.size == 2 && wear.parts.size * partWidth + indicatorWidth + 12f <= width
    // Yüksek widget'ta arada kalan yere öteki öneriler girer (önemli olan önce); her biri tek satır, sığmayan girmez.
    val sentenceHeight = (sentence?.second ?: 0) * lineHeightDp(14f, theme)
    val tipLine = lineHeightDp(12f, theme) + 5f
    val room = height - iconDp - 6f - sentenceHeight - partsHeight - 14f
    val hour = timeFormatter(context)
    val fahrenheit = LocalAppSettings.current.temperatureUnit == com.alihaydarsayar.communesky.model.TemperatureUnit.Fahrenheit
    val tips = advice.tips
        .filter { !showIndicators || it !in indicators }
        .sortedByDescending { it.result.attention }
        .mapNotNull { tip ->
            val text = tip.result.sentence(strings, { it.format(hour) }) { (if (fahrenheit) it * 1.8 else it).roundToInt() }
            fitText(context, listOf(text, context.getString(tip.tip.title)), 12f, width - 14f - 7f, 1, theme)?.let { tip to it.first }
        }
        .take((room / tipLine).toInt().coerceIn(0, 7))
    WidgetSurface(onClick = openTodayAction(), horizontal = pad.sd, vertical = pad.sd) {
        Column(GlanceModifier.fillMaxSize()) {
            HeaderRow(data, header, iconDp)
            VSpace(6.sd)
            if (sentence != null) WText(sentence.first, 14f, GlanceModifier.fillMaxWidth(), weight = WWeight.Medium, maxLines = sentence.second)
            Spacer(GlanceModifier.defaultWeight())
            if (tips.isNotEmpty()) {
                Column(GlanceModifier.fillMaxWidth()) {
                    for ((tip, text) in tips) {
                        Row(GlanceModifier.fillMaxWidth().height(tipLine.sd), verticalAlignment = Alignment.CenterVertically) {
                            WIcon(tip.tip.icon, 14.sd, theme.colors.secondary)
                            HSpace(7.sd)
                            WText(text, 12f, color = if (tip.result.status == TipStatus.Good) theme.colors.secondary else theme.colors.text)
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                for (part in wear.parts) {
                    Column(GlanceModifier.width(partWidth.sd)) {
                        WText(context.getString(WearText.label(part.weather.part)), 11f, color = theme.colors.secondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WIcon(WearText.icons(part.layer).first(), 16.sd)
                            HSpace(5.sd)
                            WText(temp(part.weather.feelsLike), 14f, weight = WWeight.Medium)
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (showIndicators) {
                    Column(horizontalAlignment = Alignment.Start) {
                        indicators.forEachIndexed { index, tip ->
                            val attention = tip.result.status != TipStatus.Good
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WIcon(tip.tip.icon, 14.sd, if (attention) theme.colors.text else theme.colors.secondary)
                                HSpace(5.sd)
                                WText(
                                    indicatorTexts[index],
                                    11f,
                                    weight = if (attention) WWeight.Medium else WWeight.Regular,
                                    color = if (attention) theme.colors.text else theme.colors.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Göstergenin kısa yazısı: "Gerek yok" / "Yanına al" / "Mutlaka al"; kremde "Gerek yok" / "İyi olur" / "Gerekli". */
private fun indicatorLabel(tip: EvaluatedTip): Int = when (tip.tip) {
    UmbrellaTip -> when (tip.result.status) {
        TipStatus.Good -> R.string.tip_short_not_needed
        TipStatus.Fair -> R.string.tip_short_umbrella_take
        TipStatus.Poor -> R.string.tip_short_umbrella_must
    }
    else -> when (tip.result.status) {
        TipStatus.Good -> R.string.tip_short_not_needed
        TipStatus.Fair -> R.string.tip_short_sunscreen_good
        TipStatus.Poor -> R.string.tip_short_sunscreen_must
    }
}
