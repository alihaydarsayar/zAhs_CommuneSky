package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.common.weatherEmoji
import java.time.format.TextStyle
import kotlin.math.roundToInt

// Yağış ihtimali bu değerin altındaysa gösterilmez; her saatte "%0" yazması kalabalık yapar.
private const val PrecipitationThreshold = 20
private val PrecipitationColor = Color(0xFF9AD8FF)

@Composable
fun HourlyForecastCard(
    current: CurrentWeather,
    hourly: List<HourlyForecast>,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberHourFormatter()
    GlassCard(title = stringResource(R.string.hourly_forecast), modifier = modifier) {
        // LazyRow sadece ekranda görünen saatleri çizer; uzun listelerde performanslıdır.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(hourly, key = { _, hour -> hour.time.toString() }) { index, hour ->
                if (index == 0) {
                    // "Şimdi" sütunu, üstteki büyük sıcaklıkla çelişmesin diye anlık veriyi gösterir.
                    val now = hour.copy(
                        temperature = current.temperature,
                        weatherCode = current.weatherCode,
                        isDay = current.isDay,
                    )
                    HourItem(stringResource(R.string.now), now)
                } else {
                    HourItem(hour.time.format(formatter), hour)
                }
            }
        }
    }
}

@Composable
private fun HourItem(label: String, hour: HourlyForecast) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(text = weatherEmoji(hour.weatherCode, hour.isDay), fontSize = 24.sp)
        PrecipitationText(hour.precipitationProbability)
        Text(
            text = stringResource(R.string.temperature_value, hour.temperature.roundToInt()),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun DailyForecastCard(daily: List<DailyForecast>, modifier: Modifier = Modifier) {
    // Sıcaklık çubuklarının hepsi aynı ölçekte olsun diye haftanın en düşük/en yüksek değerleri.
    val weekMin = daily.minOf { it.minTemperature }
    val weekMax = daily.maxOf { it.maxTemperature }
    val locale = currentLocale()
    GlassCard(title = stringResource(R.string.daily_forecast), modifier = modifier) {
        daily.forEachIndexed { index, day ->
            val dayName = if (index == 0) {
                stringResource(R.string.today)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            }
            DayRow(dayName, day, weekMin, weekMax)
        }
    }
}

@Composable
private fun DayRow(dayName: String, day: DailyForecast, weekMin: Double, weekMax: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(64.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp),
        ) {
            Text(text = weatherEmoji(day.weatherCode, isDay = true), fontSize = 22.sp)
            PrecipitationText(day.precipitationProbability)
        }
        TemperatureText(day.minTemperature, alpha = 0.7f)
        TemperatureRangeBar(
            dayMin = day.minTemperature,
            dayMax = day.maxTemperature,
            weekMin = weekMin,
            weekMax = weekMax,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        TemperatureText(day.maxTemperature, alpha = 1f)
    }
}

@Composable
private fun TemperatureText(value: Double, alpha: Float) {
    Text(
        text = stringResource(R.string.temperature_value, value.roundToInt()),
        color = Color.White.copy(alpha = alpha),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.End,
        modifier = Modifier.width(40.dp),
    )
}

/** Günün sıcaklık aralığını haftalık ölçek üzerinde soğuktan sıcağa renkli bir çubukla gösterir. */
@Composable
private fun TemperatureRangeBar(
    dayMin: Double,
    dayMax: Double,
    weekMin: Double,
    weekMax: Double,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(6.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = Color.White.copy(alpha = 0.2f), cornerRadius = radius)

        val range = (weekMax - weekMin).coerceAtLeast(1.0)
        val start = ((dayMin - weekMin) / range).toFloat() * size.width
        val end = ((dayMax - weekMin) / range).toFloat() * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF7DD3FC), Color(0xFFFDE047), Color(0xFFFB923C)),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(start, 0f),
            size = Size((end - start).coerceAtLeast(size.height), size.height),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun PrecipitationText(probability: Int) {
    Text(
        text = if (probability >= PrecipitationThreshold) {
            stringResource(R.string.precipitation_value, probability)
        } else {
            ""
        },
        color = PrecipitationColor,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun DetailsCard(weather: CurrentWeather, modifier: Modifier = Modifier) {
    GlassCard(title = stringResource(R.string.details), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            WeatherDetail(
                label = stringResource(R.string.feels_like),
                value = stringResource(
                    R.string.temperature_value,
                    weather.apparentTemperature.roundToInt(),
                ),
            )
            WeatherDetail(
                label = stringResource(R.string.humidity),
                value = stringResource(R.string.humidity_value, weather.humidity),
            )
            WeatherDetail(
                label = stringResource(R.string.wind),
                value = stringResource(R.string.wind_speed_value, weather.windSpeed.roundToInt()),
            )
        }
    }
}

@Composable
private fun WeatherDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
