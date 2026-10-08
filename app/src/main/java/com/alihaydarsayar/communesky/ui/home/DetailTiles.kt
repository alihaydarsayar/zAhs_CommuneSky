package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.asWindSpeed
import com.alihaydarsayar.communesky.ui.common.labelRes
import com.alihaydarsayar.communesky.ui.common.asTemperature
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.rememberDecimalFormat
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.theme.Outfit
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val TileHeight = 188.dp
private val TileSpacing = 12.dp

private val ValueStyle = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 40.sp)

/** Ayrıntı kutucukları: iki sütunlu ızgara, güneş kutucuğu tam genişlikte. */
@Composable
fun DetailTiles(
    current: CurrentWeather,
    today: DailyForecast?,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        TileRow(
            { PrecipitationTile(today, it) },
            { FeelsLikeTile(current, it) },
        )
        TileRow(
            { WindTile(current, it) },
            { HumidityTile(current, it) },
        )
        val sunrise = today?.sunrise
        val sunset = today?.sunset
        if (sunrise != null && sunset != null) {
            SunTile(sunrise, sunset, now, Modifier.fillMaxWidth())
        }
        TileRow(
            { UvTile(current, today, it) },
            { CloudTile(current, it) },
        )
        TileRow(
            { VisibilityTile(current, it) },
            { PressureTile(current, it) },
        )
    }
}

@Composable
private fun PrecipitationTile(today: DailyForecast?, modifier: Modifier) {
    val probability = today?.precipitationProbability ?: 0
    val sum = today?.precipitationSum ?: 0.0
    val decimal = rememberDecimalFormat()
    val caption = if (sum >= 0.1) {
        stringResource(R.string.precipitation_expected, decimal.format(sum))
    } else {
        stringResource(R.string.precipitation_none)
    }
    Tile(stringResource(R.string.precipitation), modifier, caption) {
        Text(stringResource(R.string.precipitation_value, probability), color = TextPrimary, style = ValueStyle)
        Spacer(Modifier.height(10.dp))
        ScaleBar(
            fraction = probability / 100f,
            colors = listOf(Color(0xFFCDEBFF), Color(0xFF7CC4FF), Color(0xFF3B82F6)),
        )
    }
}

/**
 * Bulutluluk ve bulutların türü: ince, yüksek bulutlar (sirrus) gökyüzünü kaplasa da güneşi
 * kesmez; bunu ayrıca söylemek "kapalı ama güneşli" görünen durumları anlaşılır kılar.
 */
@Composable
private fun CloudTile(current: CurrentWeather, modifier: Modifier) {
    val cover = current.cloudCover ?: return Tile(stringResource(R.string.cloud_cover), modifier) {}
    val lowMid = maxOf(current.cloudLow ?: 0, current.cloudMid ?: 0)
    val high = current.cloudHigh ?: 0
    val caption = when {
        cover < 20 -> R.string.clouds_clear
        high >= 50 && lowMid < 40 -> if (current.isDay) R.string.clouds_thin else R.string.clouds_thin_night
        else -> if (current.isDay) R.string.clouds_thick else R.string.clouds_thick_night
    }
    Tile(stringResource(R.string.cloud_cover), modifier, stringResource(caption)) {
        Text(stringResource(R.string.humidity_value, cover), color = TextPrimary, style = ValueStyle)
        Spacer(Modifier.height(10.dp))
        ScaleBar(
            fraction = cover / 100f,
            colors = listOf(Color(0xFF93C5FD), Color(0xFFCBD5E1), Color(0xFF64748B)),
        )
    }
}

@Composable
private fun TileRow(
    start: @Composable (Modifier) -> Unit,
    end: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
        start(Modifier.weight(1f))
        end(Modifier.weight(1f))
    }
}

@Composable
private fun Tile(
    title: String,
    modifier: Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier.height(TileHeight), title = title) {
        content()
        Spacer(Modifier.weight(1f))
        if (caption != null) {
            Text(
                text = caption,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun FeelsLikeTile(current: CurrentWeather, modifier: Modifier) {
    val difference = current.apparentTemperature - current.temperature
    val caption = when {
        difference >= 1.5 -> R.string.feels_like_warmer
        difference <= -1.5 -> R.string.feels_like_colder
        else -> R.string.feels_like_similar
    }
    Tile(stringResource(R.string.feels_like), modifier, stringResource(caption)) {
        Text(
            stringResource(R.string.temperature_value, current.apparentTemperature.asTemperature()),
            color = TextPrimary,
            style = ValueStyle,
        )
    }
}

@Composable
private fun HumidityTile(current: CurrentWeather, modifier: Modifier) {
    val caption = current.dewPoint?.let { stringResource(R.string.dew_point, it.asTemperature()) }
    Tile(stringResource(R.string.humidity), modifier, caption) {
        Text(stringResource(R.string.humidity_value, current.humidity), color = TextPrimary, style = ValueStyle)
        Spacer(Modifier.height(10.dp))
        ScaleBar(
            fraction = current.humidity / 100f,
            colors = listOf(Color(0xFF9BE7FF), Color(0xFF4FA8F5), Color(0xFF3B6FD9)),
        )
    }
}

@Composable
private fun WindTile(current: CurrentWeather, modifier: Modifier) {
    val direction = current.windDirection
    val points = stringArrayResource(R.array.compass_points)
    val caption = direction?.let {
        stringResource(R.string.wind_from, points[((it + 22.5) / 45).toInt() % 8])
    }
    Tile(stringResource(R.string.wind), modifier, caption) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Compass(direction, Modifier.size(104.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    current.windSpeed.asWindSpeed().toString(),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(LocalAppSettings.current.windUnit.labelRes),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Pusula: kenarda yön harfleri, ortadan geçen ok rüzgârın estiği yönü gösterir. */
@Composable
private fun Compass(direction: Int?, modifier: Modifier) {
    val letters = stringArrayResource(R.array.compass_letters)
    val measurer = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val center = this.center
        for (i in 0 until 36) {
            val major = i % 9 == 0
            rotate(i * 10f, center) {
                drawLine(
                    color = Color.White.copy(alpha = if (major) 0.7f else 0.22f),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y - radius + (if (major) 7.dp else 4.dp).toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }
        letters.forEachIndexed { index, letter ->
            val layout = measurer.measure(letter, letterStyle)
            val angle = index * PI / 2
            val distance = radius - 16.dp.toPx()
            val x = center.x + (sin(angle) * distance).toFloat() - layout.size.width / 2
            val y = center.y - (kotlin.math.cos(angle) * distance).toFloat() - layout.size.height / 2
            drawText(layout, topLeft = Offset(x, y))
        }
        if (direction != null) {
            // Meteorolojide yön, rüzgârın geldiği yerdir; ok ise gittiği yönü gösterir.
            rotate((direction + 180f) % 360f, center) {
                val tip = Offset(center.x, center.y - radius + 3.dp.toPx())
                val tail = Offset(center.x, center.y + radius - 3.dp.toPx())
                drawLine(Color.White, tail, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 2.dp.toPx())
                    lineTo(tip.x - 5.dp.toPx(), tip.y + 7.dp.toPx())
                    lineTo(tip.x + 5.dp.toPx(), tip.y + 7.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = tail)
            }
            // Ortadaki hız yazısı okun üstünde rahat okunsun.
            drawCircle(Color(0xFF0B1A33).copy(alpha = 0.55f), radius = radius * 0.4f, center = center)
        }
    }
}

@Composable
private fun UvTile(current: CurrentWeather, today: DailyForecast?, modifier: Modifier) {
    val uv = current.uvIndex ?: 0.0
    val decimal = rememberDecimalFormat()
    val caption = today?.uvIndexMax?.let { stringResource(R.string.uv_max_today, decimal.format(it)) }
    Tile(stringResource(R.string.uv_index), modifier, caption) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(uv.roundToInt().toString(), color = TextPrimary, style = ValueStyle)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(uvLevel(uv)),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        ScaleBar(
            fraction = (uv / 11.0).toFloat(),
            colors = listOf(
                Color(0xFF4ADE80),
                Color(0xFFFACC15),
                Color(0xFFFB923C),
                Color(0xFFF87171),
                Color(0xFFC084FC),
            ),
        )
    }
}

private fun uvLevel(uv: Double): Int = when {
    uv < 3 -> R.string.uv_low
    uv < 6 -> R.string.uv_moderate
    uv < 8 -> R.string.uv_high
    uv < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

@Composable
private fun VisibilityTile(current: CurrentWeather, modifier: Modifier) {
    val km = (current.visibility ?: return Tile(stringResource(R.string.visibility), modifier) {}) / 1000.0
    val decimal = rememberDecimalFormat(maxFractionDigits = if (km >= 10) 0 else 1)
    val caption = when {
        km >= 20 -> R.string.visibility_excellent
        km >= 8 -> R.string.visibility_good
        km >= 2 -> R.string.visibility_moderate
        else -> R.string.visibility_poor
    }
    Tile(stringResource(R.string.visibility), modifier, stringResource(caption)) {
        Text(
            stringResource(R.string.visibility_value, decimal.format(km)),
            color = TextPrimary,
            style = ValueStyle,
        )
    }
}

@Composable
private fun PressureTile(current: CurrentWeather, modifier: Modifier) {
    val pressure = current.pressure ?: return Tile(stringResource(R.string.pressure), modifier) {}
    val caption = when {
        pressure > 1022 -> R.string.pressure_high
        pressure < 1005 -> R.string.pressure_low
        else -> R.string.pressure_normal
    }
    Tile(stringResource(R.string.pressure), modifier, stringResource(caption)) {
        Text(
            stringResource(R.string.pressure_value, pressure.roundToInt()),
            color = TextPrimary,
            style = ValueStyle.copy(fontSize = 30.sp),
        )
        Spacer(Modifier.height(10.dp))
        ScaleBar(
            fraction = ((pressure - 970) / 80).toFloat(),
            colors = listOf(Color(0xFF93C5FD), Color(0xFFE2E8F0), Color(0xFFFCD34D)),
        )
    }
}

/** Renkli ölçek çubuğu ve üzerinde değerin yerini gösteren beyaz nokta. */
@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        drawRoundRect(brush = Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
        val center = Offset(fraction.coerceIn(0f, 1f) * size.width, size.height / 2)
        drawCircle(Color(0xFF0B1A33).copy(alpha = 0.5f), radius = size.height * 1.1f, center = center)
        drawCircle(Color.White, radius = size.height * 0.8f, center = center)
    }
}

/** Güneşin gün içindeki yolu: ufuk çizgisi, yay ve güneşin şu anki yeri. */
@Composable
private fun SunTile(sunrise: LocalDateTime, sunset: LocalDateTime, now: LocalDateTime, modifier: Modifier) {
    val formatter = rememberTimeFormatter()
    val dayLength = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
    val progress = Duration.between(sunrise, now).toMinutes().toFloat() / dayLength
    GlassCard(modifier = modifier, title = stringResource(R.string.sun)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            drawSunPath(progress)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunTime(stringResource(R.string.sunrise), sunrise.format(formatter), Alignment.Start)
            SunTime(stringResource(R.string.sunset), sunset.format(formatter), Alignment.End)
        }
    }
}

private fun DrawScope.drawSunPath(progress: Float) {
    val horizon = size.height * 0.82f
    val amplitude = size.height * 0.72f
    val path = Path().apply {
        val steps = 48
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = f * size.width
            val y = horizon - sin(PI * f).toFloat() * amplitude
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawLine(
        Color.White.copy(alpha = 0.3f),
        Offset(0f, horizon),
        Offset(size.width, horizon),
        strokeWidth = 1.dp.toPx(),
    )
    drawPath(
        path,
        Color.White.copy(alpha = 0.35f),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        ),
    )
    if (progress in 0f..1f) {
        val x = progress * size.width
        val y = horizon - sin(PI * progress).toFloat() * amplitude
        val sun = Offset(x, y)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFE7A3).copy(alpha = 0.7f),
                1f to Color.Transparent,
                center = sun,
                radius = 22.dp.toPx(),
            ),
            radius = 22.dp.toPx(),
            center = sun,
        )
        drawCircle(Color(0xFFFFD166), radius = 7.dp.toPx(), center = sun)
    } else {
        // Gece: güneş ufkun altında; yanında hafif bir işaret.
        val x = if (abs(progress) < abs(progress - 1)) 0f else size.width
        drawCircle(Color.White.copy(alpha = 0.45f), radius = 4.dp.toPx(), center = Offset(x, horizon))
    }
}

@Composable
private fun SunTime(label: String, time: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(time, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}
