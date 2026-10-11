package com.alihaydarsayar.communesky.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.Moon
import com.alihaydarsayar.communesky.model.MoonInfo
import com.alihaydarsayar.communesky.model.MoonPhase
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassShape
import com.alihaydarsayar.communesky.ui.common.currentLocale
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.theme.Outfit
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@get:StringRes
val MoonPhase.label: Int
    get() = when (this) {
        MoonPhase.New -> R.string.moon_new
        MoonPhase.WaxingCrescent -> R.string.moon_waxing_crescent
        MoonPhase.FirstQuarter -> R.string.moon_first_quarter
        MoonPhase.WaxingGibbous -> R.string.moon_waxing_gibbous
        MoonPhase.Full -> R.string.moon_full
        MoonPhase.WaningGibbous -> R.string.moon_waning_gibbous
        MoonPhase.LastQuarter -> R.string.moon_last_quarter
        MoonPhase.WaningCrescent -> R.string.moon_waning_crescent
    }

/** Bu yerin şu anki ay bilgisi; hava verisi yenilenince yeniden hesaplanır (hesap telefonda, hızlıdır). */
@Composable
fun rememberMoon(weather: WeatherSnapshot): MoonInfo = remember(weather) {
    Moon.info(Instant.now(), weather.city.latitude, weather.city.longitude, weather.forecast.utcOffsetSeconds)
}

/**
 * Ayın çizimi (vektör): koyu disk, üstünde evreye göre aydınlık kısım. Aydınlık kısım, ayın kenarı ile gölge
 * sınırının (yarım elips) arasıdır. Kuzey yarımkürede büyüyen ayın sağı aydınlıktır; güneyde tersi ([flipped]).
 */
@Composable
fun MoonDrawing(illumination: Double, waxing: Boolean, size: Dp, modifier: Modifier = Modifier, flipped: Boolean = false) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val radius = this.size.minDimension / 2 - 1.dp.toPx()
        val center = this.center
        drawCircle(Color.White.copy(alpha = 0.10f), radius, center)
        // Sınır elipsinin yatay yarıçapı: yeni ayda kenarla çakışır (alan sıfır), dolunayda karşı kenara ulaşır.
        val edge = radius * (1 - 2 * illumination).toFloat()
        val side = if (waxing != flipped) 1f else -1f
        val lit = Path().apply {
            val steps = 48
            // Aydınlık kenar: tepeden alta yarım daire.
            for (i in 0..steps) {
                val angle = -Math.PI / 2 + Math.PI * i / steps
                val x = center.x + side * radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            // Gölge sınırı: alttan tepeye yarım elips.
            for (i in steps downTo 0) {
                val angle = -Math.PI / 2 + Math.PI * i / steps
                lineTo(center.x + side * edge * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            }
            close()
        }
        drawPath(lit, Color(0xFFF4F1E6))
        drawCircle(Color.White.copy(alpha = 0.35f), radius, center, style = Stroke(1.dp.toPx()))
    }
}

private val MoonValue = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp)

/** Ana ekrandaki ay kutucuğu: küçük ay, evre adı, aydınlanma, doğuş ve batış. Dokununca ay sayfası açılır. */
@Composable
fun MoonTile(weather: WeatherSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val moon = rememberMoon(weather)
    val time = rememberTimeFormatter()
    GlassCard(
        modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.detail_open), onClick = onOpen),
        title = stringResource(R.string.moon),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonDrawing(moon.illumination, moon.waxing, 56.dp, flipped = weather.city.latitude < 0)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(moon.phase.label), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.moon_illuminated, (moon.illumination * 100).roundToInt()),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.moon_rise_short, moon.rise?.format(time) ?: "–"), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.moon_set_short, moon.set?.format(time) ?: "–"), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Ay sayfası: ayın görseli ve evresi, doğuş ve batış, sonraki dolunay ve yeni ay, 30 günlük şerit, açıklama. */
@Composable
fun MoonPage(weather: WeatherSnapshot) {
    val moon = rememberMoon(weather)
    val now = remember(weather) { weather.forecast.localNow() }
    val time = rememberTimeFormatter()
    val locale = currentLocale()
    val flipped = weather.city.latitude < 0

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.moon)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonDrawing(moon.illumination, moon.waxing, 132.dp, flipped = flipped)
            Spacer(Modifier.width(20.dp))
            Column {
                Text(stringResource(R.string.humidity_value, (moon.illumination * 100).roundToInt()), color = TextPrimary, style = MoonValue)
                Text(stringResource(moon.phase.label), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (moon.waxing) R.string.moon_growing else R.string.moon_shrinking),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.today)) {
        MoonRow(stringResource(R.string.moon_rise), moon.rise?.format(time) ?: stringResource(R.string.moon_no_rise))
        MoonRow(stringResource(R.string.moon_set), moon.set?.format(time) ?: stringResource(R.string.moon_no_set))
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.moon_next)) {
        val date = remember(locale) { DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMMEEEE"), locale) }
        @Composable
        fun whenText(at: LocalDateTime): String {
            val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate()).toInt().coerceAtLeast(0)
            val inDays = when (days) {
                0 -> stringResource(R.string.today)
                1 -> stringResource(R.string.tomorrow)
                else -> pluralStringResource(R.plurals.in_days, days, days)
            }
            return stringResource(R.string.moon_when, at.format(date), at.format(time), inDays)
        }
        // Hangisi önce geliyorsa o üstte.
        val phases = listOf(R.string.moon_full to moon.nextFullMoon, R.string.moon_new to moon.nextNewMoon).sortedBy { it.second }
        for ((label, at) in phases) {
            Text(stringResource(label), color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(whenText(at), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
        }
    }

    // 30 günlük şerit: her günün ayı.
    val days = remember(weather) { Moon.days(now.toLocalDate(), 30, weather.forecast.utcOffsetSeconds) }
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.moon_thirty_days)) {
        val month = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(days, key = { it.date.toEpochDay() }) { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (day.date == now.toLocalDate()) stringResource(R.string.today) else day.date.format(month),
                        color = if (day.date == now.toLocalDate()) TextPrimary else TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    MoonDrawing(day.illumination, day.waxing, 34.dp, flipped = flipped)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.humidity_value, (day.illumination * 100).roundToInt()),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.detail_what)) {
        Text(stringResource(R.string.what_moon), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MoonRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}
