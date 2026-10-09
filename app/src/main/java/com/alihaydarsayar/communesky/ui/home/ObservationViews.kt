package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationSource
import com.alihaydarsayar.communesky.model.ObservationUse
import com.alihaydarsayar.communesky.ui.common.relativeTimeSince
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextTertiary

/**
 * "Samsun Bölge istasyonu" ya da "Samsun-Çarşamba Havalimanı". MGM istasyonunun adı zaten
 * "… Havalimanı" ise ("İstanbul Sabiha Gökçen Havalimanı") ayrıca "istasyonu" eklenmez.
 */
@Composable
private fun stationLabel(observation: Observation): String = when (observation.source) {
    ObservationSource.Mgm -> if (observation.stationName.endsWith("Havalimanı")) {
        observation.stationName
    } else {
        stringResource(R.string.station_label_mgm, observation.stationName)
    }
    ObservationSource.Metar -> stringResource(R.string.station_label_airport, observation.stationName)
}

@Composable
private fun observedAgo(observation: Observation): String =
    relativeTimeSince(observation.observedAt) ?: stringResource(R.string.observation_just_now)

/** Anlık durum ölçümden geldiğinde başlığın altındaki küçük kaynak notu. */
@Composable
fun ObservationNote(use: ObservationUse.Override, modifier: Modifier = Modifier) {
    val observation = use.observation
    val text = when (observation.source) {
        ObservationSource.Mgm -> stringResource(R.string.observation_note_mgm, stationLabel(observation), observedAgo(observation))
        ObservationSource.Metar -> stringResource(R.string.observation_note_metar, stationLabel(observation), observedAgo(observation))
    }
    Text(
        text = text,
        color = TextTertiary,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Uzaktaki istasyonda yağış ya da gök gürültüsü ölçüldüğünde gösterilen uyarı. */
@Composable
fun ObservationAdvisoryChip(use: ObservationUse.Advisory) {
    val observation = use.observation
    val label = stationLabel(observation)
    val ago = observedAgo(observation)
    val text = if (observation.hasThunder) {
        stringResource(R.string.observation_thunder_nearby, label, ago)
    } else {
        stringResource(R.string.observation_rain_nearby, label, ago)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (observation.hasThunder) R.drawable.ic_wx_thunderstorm else R.drawable.ic_drop),
            contentDescription = null,
            tint = if (observation.hasThunder) Color.Unspecified else TextPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = TextPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
