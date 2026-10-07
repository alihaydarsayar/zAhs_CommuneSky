package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.SectionTitle
import com.alihaydarsayar.communesky.ui.common.WeatherIcon
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.theme.RainAccent
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import kotlin.math.roundToInt

private val ColumnWidth = 60.dp
private val CurveAreaHeight = 78.dp
private val PointTop = 32.dp
private val PointBottom = 66.dp

/** Yağış ihtimali bu değerin altındaysa gösterilmez; her saatte "%0" yazması kalabalık yapar. */
const val PrecipitationThreshold = 20

private val CurveWarm = Color(0xFFFFC27A)
private val CurveCool = Color(0xFF8FD3FF)

/**
 * Önümüzdeki 24 saat: üstte saat, ikon ve yağış ihtimali; altta sıcaklıkları birleştiren
 * yumuşak bir eğri. 24 sütun az olduğu için LazyRow yerine düz Row + yatay kaydırma kullanıyoruz;
 * böylece eğri tüm sütunların arkasında tek parça çizilebiliyor.
 */
@Composable
fun HourlyCard(
    current: CurrentWeather,
    hours: List<HourlyForecast>,
    modifier: Modifier = Modifier,
) {
    if (hours.isEmpty()) return
    // "Şimdi" sütunu üstteki büyük sıcaklıkla çelişmesin diye anlık veriyi gösterir.
    val items = remember(current, hours) {
        hours.mapIndexed { index, hour ->
            if (index == 0) {
                hour.copy(
                    temperature = current.temperature,
                    weatherCode = current.weatherCode,
                    isDay = current.isDay,
                )
            } else {
                hour
            }
        }
    }
    val pointOffsets = remember(items) { pointOffsets(items.map { it.temperature }) }
    val formatter = rememberHourFormatter()

    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp),
    ) {
        // Kaydırılan satır kartın kenarına kadar uzansın diye başlığa ayrı iç boşluk veriyoruz.
        SectionTitle(stringResource(R.string.hourly_forecast), Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
        ) {
            Row {
                items.forEachIndexed { index, hour ->
                    HourHeader(
                        label = if (index == 0) stringResource(R.string.now) else hour.time.format(formatter),
                        hour = hour,
                        isNow = index == 0,
                    )
                }
            }
            Box(
                Modifier
                    .width(ColumnWidth * items.size)
                    .height(CurveAreaHeight),
            ) {
                TemperatureCurve(pointOffsets, Modifier.matchParentSize())
                items.forEachIndexed { index, hour ->
                    Text(
                        text = stringResource(R.string.temperature_value, hour.temperature.roundToInt()),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(ColumnWidth)
                            .offset(x = ColumnWidth * index, y = pointOffsets[index] - 28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourHeader(label: String, hour: HourlyForecast, isNow: Boolean) {
    Column(
        modifier = Modifier.width(ColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (isNow) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        WeatherIcon(
            condition = hour.condition,
            isNight = !hour.isDay,
            size = 32.dp,
            contentDescription = stringResource(weatherDescriptionRes(hour.weatherCode)),
        )
        Text(
            text = if (hour.precipitationProbability >= PrecipitationThreshold) {
                stringResource(R.string.precipitation_value, hour.precipitationProbability)
            } else {
                ""
            },
            color = RainAccent,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Her sıcaklığın eğri alanı içindeki dikey konumu: en sıcak en üstte. */
private fun pointOffsets(temperatures: List<Double>): List<Dp> {
    val min = temperatures.min()
    val max = temperatures.max()
    val range = (max - min).takeIf { it > 0.1 } ?: 1.0
    return temperatures.map { t ->
        val fraction = if (max - min > 0.1) ((t - min) / range).toFloat() else 0.5f
        PointTop + (PointBottom - PointTop) * (1f - fraction)
    }
}

@Composable
private fun TemperatureCurve(points: List<Dp>, modifier: Modifier) {
    Canvas(modifier) {
        val column = ColumnWidth.toPx()
        val offsets = points.mapIndexed { i, y -> Offset(column * i + column / 2, y.toPx()) }
        // Komşu noktalar arasında yatay kontrol noktalı kübik eğri: yumuşak ama taşmayan bir çizgi.
        val line = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            for (i in 1 until offsets.size) {
                val previous = offsets[i - 1]
                val point = offsets[i]
                val midX = (previous.x + point.x) / 2
                cubicTo(midX, previous.y, midX, point.y, point.x, point.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.16f),
                1f to Color.White.copy(alpha = 0f),
                startY = PointTop.toPx(),
                endY = size.height,
            ),
        )
        drawPath(
            path = line,
            brush = Brush.verticalGradient(
                0f to CurveWarm,
                1f to CurveCool,
                startY = PointTop.toPx(),
                endY = PointBottom.toPx(),
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        offsets.forEachIndexed { index, point ->
            if (index == 0) {
                drawCircle(Color.White.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = point)
                drawCircle(Color.White, radius = 4.5.dp.toPx(), center = point)
            } else {
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = point)
            }
        }
    }
}

