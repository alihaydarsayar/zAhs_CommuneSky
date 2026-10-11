package com.alihaydarsayar.communesky.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AirHour
import com.alihaydarsayar.communesky.model.AirQuality
import com.alihaydarsayar.communesky.model.AqiLevel
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.detail.Chart
import com.alihaydarsayar.communesky.model.detail.ChartKind
import com.alihaydarsayar.communesky.model.detail.ChartPoint
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassShape
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.rememberDecimalFormat
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.theme.Outfit
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import java.time.LocalTime
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.ceil
import kotlin.math.max

/** İndeks basamağının rengi (Avrupa Çevre Ajansı'nın ölçeğine yakın; koyu zeminde okunacak tonlar). */
val AqiLevel.color: Color
    get() = when (this) {
        AqiLevel.Good -> Color(0xFF50F0E6)
        AqiLevel.Fair -> Color(0xFF50CCAA)
        AqiLevel.Moderate -> Color(0xFFF0E641)
        AqiLevel.Poor -> Color(0xFFFF6B5E)
        AqiLevel.VeryPoor -> Color(0xFFD0345E)
        AqiLevel.ExtremelyPoor -> Color(0xFFA855C8)
    }

@get:StringRes
val AqiLevel.label: Int
    get() = when (this) {
        AqiLevel.Good -> R.string.aqi_level_good
        AqiLevel.Fair -> R.string.aqi_level_fair
        AqiLevel.Moderate -> R.string.aqi_level_moderate
        AqiLevel.Poor -> R.string.aqi_level_poor
        AqiLevel.VeryPoor -> R.string.aqi_level_very_poor
        AqiLevel.ExtremelyPoor -> R.string.aqi_level_extremely_poor
    }

@get:StringRes
val AqiLevel.advice: Int
    get() = when (this) {
        AqiLevel.Good -> R.string.aqi_advice_good
        AqiLevel.Fair -> R.string.aqi_advice_fair
        AqiLevel.Moderate -> R.string.aqi_advice_moderate
        AqiLevel.Poor -> R.string.aqi_advice_poor
        AqiLevel.VeryPoor -> R.string.aqi_advice_very_poor
        AqiLevel.ExtremelyPoor -> R.string.aqi_advice_extremely_poor
    }

private val AqiValue = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp)

/** Renkli ölçek: altı basamak yan yana, üstünde değerin yerini gösteren beyaz nokta. Ölçek 0–120 arasıdır. */
@Composable
fun AqiScale(aqi: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val bar = 6.dp.toPx()
        val top = (size.height - bar) / 2
        val gap = 2.dp.toPx()
        val segment = (size.width - gap * 5) / 6
        AqiLevel.entries.forEachIndexed { index, level ->
            drawRoundRect(level.color, Offset(index * (segment + gap), top), Size(segment, bar), CornerRadius(bar / 2))
        }
        val x = (aqi.coerceIn(0, 120) / 120f) * size.width
        drawCircle(Color(0xFF0B1A33), 7.dp.toPx(), Offset(x.coerceIn(7.dp.toPx(), size.width - 7.dp.toPx()), size.height / 2))
        drawCircle(Color.White, 5.dp.toPx(), Offset(x.coerceIn(7.dp.toPx(), size.width - 7.dp.toPx()), size.height / 2))
    }
}

/** Ana ekrandaki küçük kart: indeks, basamağı ve ölçek. Dokununca hava kalitesi sayfası açılır. */
@Composable
fun AirQualityCard(air: AirQuality, weather: WeatherSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val now = remember(weather) { weather.forecast.localNow() }
    val current = remember(air, now) { air.at(now) } ?: return
    val aqi = current.aqi ?: return
    val level = AqiLevel.of(aqi)
    GlassCard(
        modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.detail_open), onClick = onOpen),
        title = stringResource(R.string.air_quality),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(aqi.toString(), color = TextPrimary, style = AqiValue.copy(fontSize = 36.sp, lineHeight = 40.sp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(level.label), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        AqiScale(aqi)
    }
}

/**
 * Hava kalitesi sayfası: indeks ve renk ölçeği, sağlık önerisi, kirleticiler, 48 saatlik grafik, 3 günlük özet,
 * açıklama ve kaynak.
 */
@Composable
fun AirQualityPage(weather: WeatherSnapshot) {
    val air = weather.airQuality
    val now = remember(weather) { weather.forecast.localNow() }
    val current = remember(air, now) { air?.at(now) }
    val aqi = current?.aqi
    if (air == null || current == null || aqi == null) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.air_quality)) {
            Text(stringResource(R.string.aqi_none), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val level = AqiLevel.of(aqi)
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.air_quality)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(aqi.toString(), color = TextPrimary, style = AqiValue)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(level.color))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(level.label), color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        AqiScale(aqi)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.aqi_index_name), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.aqi_advice_title)) {
        Text(stringResource(level.advice), color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        if (level >= AqiLevel.Moderate) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.aqi_sensitive), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.aqi_pollutants)) {
        PollutantRow(stringResource(R.string.aqi_pm25), current.pm25)
        PollutantRow(stringResource(R.string.aqi_pm10), current.pm10)
        PollutantRow(stringResource(R.string.aqi_ozone), current.ozone)
        PollutantRow(stringResource(R.string.aqi_no2), current.no2)
        PollutantRow(stringResource(R.string.aqi_so2), current.so2)
        PollutantRow(stringResource(R.string.aqi_co), current.co)
    }

    // 48 saat: şimdiden başlar; veri bir kez hazırlanır.
    val chart = remember(air, now) { airChart(air.upcoming(now)) }
    if (chart.hasData) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.aqi_next_48)) {
            var selected by remember(chart) { mutableStateOf<Int?>(null) }
            val hour = rememberHourFormatter()
            val time = rememberTimeFormatter()
            val locale = currentLocale()
            val point = selected?.let { chart.points.getOrNull(it) }
            val value = point?.value
            val readout = if (point != null && value != null) {
                val day = point.time.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
                val pointLevel = AqiLevel.of(value.toInt())
                stringResource(R.string.detail_readout, "$day ${point.time.format(time)}", stringResource(R.string.aqi_card_line, value.toInt(), stringResource(pointLevel.label)))
            } else {
                stringResource(R.string.detail_chart_hint)
            }
            Text(readout, color = if (point != null) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            MetricChart(
                chart = chart,
                selected = selected,
                onSelect = { selected = it },
                hourLabel = { LocalTime.of(it, 0).format(hour) },
                axisLabel = { it.toInt().toString() },
                color = level.color,
            )
        }
    }

    val days = remember(air, now) { air.days(now.toLocalDate()) }
    if (days.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.aqi_three_days)) {
            val locale = currentLocale()
            for (day in days) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val name = when (day.date) {
                        now.toLocalDate() -> stringResource(R.string.today)
                        now.toLocalDate().plusDays(1) -> stringResource(R.string.tomorrow)
                        else -> day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
                    }
                    Text(name, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(day.level.color))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.aqi_card_line, day.aqi, stringResource(day.level.label)),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Text(stringResource(R.string.aqi_days_note), color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.detail_what)) {
        Text(stringResource(R.string.what_air_quality), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        stringResource(R.string.aqi_source),
        color = TextSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun PollutantRow(name: String, value: Double?) {
    val decimal = rememberDecimalFormat(maxFractionDigits = if ((value ?: 0.0) >= 100) 0 else 1)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(name, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (value == null) "–" else stringResource(R.string.aqi_micrograms, decimal.format(value)),
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 48 saatlik indeks grafiği: eksen 0'dan en az 100'e; ilk nokta şimdi. */
fun airChart(hours: List<AirHour>): Chart {
    val top = hours.mapNotNull { it.aqi }.maxOrNull() ?: 0
    return Chart(
        points = hours.map { ChartPoint(it.at, it.aqi?.toDouble()) },
        kind = ChartKind.Line,
        min = 0.0,
        max = max(100.0, ceil(top / 20.0) * 20),
        nowPosition = 0f,
    )
}
