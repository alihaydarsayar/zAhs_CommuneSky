package com.alihaydarsayar.communesky.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Uyarı renkleri: meteorolojik uyarıların bilinen sarı, turuncu ve kırmızısı. */
val AlertLevel.color: Color
    get() = when (this) {
        AlertLevel.Yellow -> Color(0xFFF2C230)
        AlertLevel.Orange -> Color(0xFFF08A24)
        AlertLevel.Red -> Color(0xFFE5484D)
    }

/** Renkli şeridin üstündeki yazı: sarı ve turuncuda koyu, kırmızıda beyaz. */
private val AlertLevel.onColor: Color
    get() = if (this == AlertLevel.Red) Color.White else Color(0xFF141416)

@get:StringRes
val AlertLevel.nameRes: Int
    get() = when (this) {
        AlertLevel.Yellow -> R.string.alert_level_yellow
        AlertLevel.Orange -> R.string.alert_level_orange
        AlertLevel.Red -> R.string.alert_level_red
    }

/** Olay türlerinin adları (MGM sitesindeki terimler). */
@get:StringRes
val AlertEvent.nameRes: Int
    get() = when (this) {
        AlertEvent.Wind -> R.string.alert_event_wind
        AlertEvent.Rain -> R.string.alert_event_rain
        AlertEvent.Thunderstorm -> R.string.alert_event_thunderstorm
        AlertEvent.Snow -> R.string.alert_event_snow
        AlertEvent.Ice -> R.string.alert_event_ice
        AlertEvent.Fog -> R.string.alert_event_fog
        AlertEvent.Heat -> R.string.alert_event_heat
        AlertEvent.Cold -> R.string.alert_event_cold
        AlertEvent.Flood -> R.string.alert_event_flood
        AlertEvent.Avalanche -> R.string.alert_event_avalanche
        AlertEvent.Dust -> R.string.alert_event_dust
        AlertEvent.Fire -> R.string.alert_event_fire
        AlertEvent.Coastal -> R.string.alert_event_coastal
        AlertEvent.Other -> R.string.alert_event_other
    }

/** "Gök gürültülü sağanak yağış, Rüzgâr"; tür yoksa "Meteorolojik uyarı". */
@Composable
fun alertTitle(alert: WeatherAlert): String =
    alert.events.ifEmpty { listOf(AlertEvent.Other) }.map { stringResource(it.nameRes) }.joinToString(", ")

/** "Bugün 09:00 – 20:59", "Yarın 03:00 – 12:00" ya da iki günü kapsıyorsa "Bugün 21:00 – yarın 06:00". */
@Composable
fun alertPeriod(alert: WeatherAlert, today: LocalDate): String {
    val time = rememberTimeFormatter()
    val date = DateTimeFormatter.ofPattern("d MMM")
    @Composable
    fun day(at: LocalDateTime) = when (at.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> at.format(date)
    }
    val begin = alert.beginsAt
    val end = alert.endsAt
    return if (begin.toLocalDate() == end.toLocalDate()) {
        stringResource(R.string.alert_period_same_day, day(begin), begin.format(time), end.format(time))
    } else {
        stringResource(R.string.alert_period_two_days, day(begin), begin.format(time), day(end).lowercase(), end.format(time))
    }
}

/**
 * Ana ekranın üstündeki uyarı şeridi: en ağır uyarının rengi, olay türü ve geçerli olduğu saatler.
 * Dokununca yayımlayan kurumun kendi metni açılır.
 */
@Composable
fun AlertBanner(alerts: List<WeatherAlert>, today: LocalDate, modifier: Modifier = Modifier) {
    val alert = alerts.firstOrNull() ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val more = alerts.size - 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(alert.level.color)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.alert_open)) { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = alertTitle(alert) + if (more > 0) stringResource(R.string.alert_more, more) else "",
                color = alert.level.onColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.alert_banner_line, stringResource(alert.level.nameRes), alertPeriod(alert, today)),
                color = alert.level.onColor.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = alert.level.onColor, modifier = Modifier.size(20.dp))
    }
    if (open) AlertDialogContent(alerts, today, onDismiss = { open = false })
}

@Composable
private fun AlertDialogContent(alerts: List<WeatherAlert>, today: LocalDate, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close), color = TextPrimary) } },
        title = { Text(stringResource(R.string.alert_dialog_title), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (alert in alerts) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(10.dp).clip(CircleShape).background(alert.level.color))
                            Spacer(Modifier.width(8.dp))
                            Text(alertTitle(alert), color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            stringResource(R.string.alert_banner_line, stringResource(alert.level.nameRes), alertPeriod(alert, today)),
                            color = TextSecondary, style = MaterialTheme.typography.labelLarge,
                        )
                        if (alert.text.isNotBlank()) Text(alert.text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.alert_source, alerts.first().source), color = TextSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            }
        },
        containerColor = Color(0xFF1B2233),
        shape = RoundedCornerShape(28.dp),
    )
}

/** Uyarı şeridinin altında: "Bu tür uyarılar için bildirim almak ister misin?" [Aç] [✕]. */
@Composable
fun AlertNotificationPrompt(onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    com.alihaydarsayar.communesky.ui.common.GlassCard(
        modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.notification_prompt),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            com.alihaydarsayar.communesky.ui.today.GlassPill(stringResource(R.string.notification_prompt_enable)) { onAnswer(true) }
            androidx.compose.material3.IconButton(onClick = { onAnswer(false) }) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.close),
                    tint = TextSecondary,
                )
            }
        }
    }
}
