package com.alihaydarsayar.communesky.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.ForecastSources
import com.alihaydarsayar.communesky.model.RainNote
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary

/** Kaynağın kısa adı ("MGM", "ECMWF"): kaynak satırında. */
@get:StringRes
val ForecastSource.shortNameRes: Int
    get() = when (this) {
        ForecastSource.Mgm -> R.string.source_mgm_short
        ForecastSource.EcmwfIfs -> R.string.source_ecmwf
        ForecastSource.EcmwfAifs -> R.string.source_ecmwf_aifs
        ForecastSource.OpenMeteo -> R.string.source_open_meteo
        ForecastSource.MeteoFrance -> R.string.source_meteo_france
        ForecastSource.UkMetOffice -> R.string.source_met_office
    }

/** Kaynağın tam adı ("Meteoroloji Genel Müdürlüğü"): bilgi kutusunda. */
@get:StringRes
val ForecastSource.fullNameRes: Int
    get() = when (this) {
        ForecastSource.Mgm -> R.string.source_mgm
        ForecastSource.EcmwfIfs -> R.string.source_ecmwf_full
        ForecastSource.EcmwfAifs -> R.string.source_ecmwf_aifs_full
        ForecastSource.OpenMeteo -> R.string.source_open_meteo_full
        ForecastSource.MeteoFrance -> R.string.source_meteo_france_full
        ForecastSource.UkMetOffice -> R.string.source_met_office_full
    }

/**
 * Tahminin altındaki küçük kaynak satırı: "Tahmin: MGM · Yağış: Open-Meteo". Ulusal servise ulaşılamadıysa
 * bunu söyler. Dokununca kaynağı açıklayan bilgi kutusu açılır.
 */
@Composable
fun SourceLine(weather: WeatherSnapshot, modifier: Modifier = Modifier) {
    val sources = weather.sources ?: return
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val forecast = stringResource(sources.forecast.shortNameRes)
    val text = when {
        sources.nationalUnavailable -> stringResource(R.string.source_line_unavailable, stringResource(R.string.source_mgm_short), forecast)
        sources.forecast == sources.precipitation -> stringResource(R.string.source_line_single, forecast)
        else -> stringResource(R.string.source_line, forecast, stringResource(sources.precipitation.shortNameRes))
    }
    Text(
        text = text,
        color = TextSecondary,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.source_info_title)) { showInfo = true }
            .padding(vertical = 10.dp, horizontal = 12.dp),
    )
    if (showInfo) SourceInfoDialog(weather, sources, onDismiss = { showInfo = false })
}

/** Herkese açık bilgi kutusu: bu yer için tahmin nereden geliyor. */
@Composable
private fun SourceInfoDialog(weather: WeatherSnapshot, sources: ForecastSources, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close), color = TextPrimary) } },
        title = { Text(stringResource(R.string.source_info_title), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (line in sourceInfoLines(weather, sources)) Text(line, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        },
        containerColor = Color(0xFF1B2233),
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun sourceInfoLines(weather: WeatherSnapshot, sources: ForecastSources): List<String> = buildList {
    add(stringResource(R.string.source_info_forecast, stringResource(sources.forecast.fullNameRes)))
    if (sources.nationalUnavailable) add(stringResource(R.string.source_info_unavailable, stringResource(R.string.source_mgm)))
    if (sources.forecast != sources.precipitation) add(stringResource(R.string.source_info_precipitation, stringResource(sources.precipitation.fullNameRes)))
    if (weather.observation != null) add(stringResource(R.string.source_info_observation))
}

/** İkon ile yağış ihtimali çeliştiğinde gösterilen tek satır. */
@Composable
fun RainNoteLine(note: RainNote, modifier: Modifier = Modifier) {
    val text = when (note) {
        is RainNote.Expected -> stringResource(R.string.rain_expected_by, stringResource(note.by.shortNameRes))
        is RainNote.Chance -> stringResource(R.string.rain_chance_note, note.percent)
    }
    Text(text, color = TextSecondary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}
