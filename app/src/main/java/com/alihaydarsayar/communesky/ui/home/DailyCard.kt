package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.WeatherIcon
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.theme.TemperatureScale
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Önümüzdeki günler. Her günün sıcaklık aralığı, haftanın tamamına göre ölçeklenmiş bir çubukla
 * gösterilir; çubuğun rengi gerçek sıcaklığa göre soğuk maviden sıcak kırmızıya değişir.
 * Bugünün çubuğunda şu anki sıcaklık beyaz bir noktayla işaretlenir.
 */
@Composable
fun DailyCard(
    days: List<DailyForecast>,
    currentTemperature: Double,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.minTemperature }
    val weekMax = days.maxOf { it.maxTemperature }
    val locale = currentLocale()
    GlassCard(modifier = modifier, title = stringResource(R.string.daily_forecast)) {
        days.forEachIndexed { index, day ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            }
            DayRow(
                name = if (index == 0) {
                    stringResource(R.string.today)
                } else {
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                        .replaceFirstChar { it.titlecase(locale) }
                },
                day = day,
                weekMin = weekMin,
                weekMax = weekMax,
                currentTemperature = if (index == 0) currentTemperature else null,
            )
        }
    }
}

@Composable
private fun DayRow(
    name: String,
    day: DailyForecast,
    weekMin: Double,
    weekMax: Double,
    currentTemperature: Double?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (currentTemperature != null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
        )
        WeatherIcon(
            condition = day.condition,
            isNight = false,
            size = 30.dp,
            contentDescription = stringResource(weatherDescriptionRes(day.weatherCode)),
        )
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            PrecipitationChance(day.precipitationProbability, MaterialTheme.typography.labelMedium)
        }
        Text(
            text = stringResource(R.string.temperature_value, day.minTemperature.roundToInt()),
            color = TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
        Spacer(Modifier.width(10.dp))
        TemperatureRangeBar(
            dayMin = day.minTemperature,
            dayMax = day.maxTemperature,
            weekMin = weekMin,
            weekMax = weekMax,
            current = currentTemperature,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.temperature_value, day.maxTemperature.roundToInt()),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun TemperatureRangeBar(
    dayMin: Double,
    dayMax: Double,
    weekMin: Double,
    weekMax: Double,
    current: Double?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(6.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = Color.White.copy(alpha = 0.14f), cornerRadius = radius)

        val range = (weekMax - weekMin).coerceAtLeast(1.0)
        fun xOf(t: Double) = ((t - weekMin) / range).toFloat().coerceIn(0f, 1f) * size.width
        val start = xOf(dayMin)
        val end = xOf(dayMax).coerceAtLeast(start + size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(temperatureColor(dayMin), temperatureColor(dayMax)),
                startX = start,
                endX = end,
            ),
            topLeft = Offset(start, 0f),
            size = Size(end - start, size.height),
            cornerRadius = radius,
        )
        if (current != null) {
            val center = Offset(xOf(current), size.height / 2)
            drawCircle(Color(0xFF0B1A33).copy(alpha = 0.55f), radius = size.height * 1.05f, center = center)
            drawCircle(Color.White, radius = size.height * 0.75f, center = center)
        }
    }
}

/** -5°C buz mavisi ... 35°C sıcak kırmızı; aradaki değerler renkler arasında yumuşakça geçer. */
fun temperatureColor(celsius: Double): Color {
    val fraction = ((celsius + 5.0) / 40.0).toFloat().coerceIn(0f, 1f)
    val scaled = fraction * (TemperatureScale.size - 1)
    val index = scaled.toInt().coerceAtMost(TemperatureScale.size - 2)
    return lerp(TemperatureScale[index], TemperatureScale[index + 1], scaled - index)
}
