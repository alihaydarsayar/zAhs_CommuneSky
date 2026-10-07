package com.alihaydarsayar.communesky.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.weatherDescriptionRes
import com.alihaydarsayar.communesky.ui.common.relativeTimeSince
import com.alihaydarsayar.communesky.ui.theme.HeroTemperatureStyle
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.theme.WarningAccent
import kotlin.math.roundToInt

/** Parlak gökyüzünde beyaz yazının okunması için çok hafif gölge. */
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.18f), Offset(0f, 2f), blurRadius = 14f)

@Composable
fun CurrentHeader(
    weather: WeatherSnapshot,
    today: DailyForecast?,
    status: HeaderStatus,
    locationStatus: LocationStatus,
    onLocationAction: (LocationStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = weather.forecast.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 44.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (weather.isCurrentLocation) {
                Icon(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = stringResource(R.string.current_location),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = weather.city.name ?: stringResource(R.string.my_location),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(shadow = TextShadow),
            )
        }
        Spacer(Modifier.height(4.dp))
        StatusLine(status, weather)

        // Sıcaklık değişince rakamlar yumuşakça geçiş yapar.
        AnimatedContent(
            targetState = current.temperature.roundToInt(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "heroTemperature",
        ) { temperature ->
            Text(
                text = buildAnnotatedString {
                    append(temperature.toString())
                    // Derece işareti sağa taşar; rakamlar ortada dursun diye solda eşit boşluk bırakıyoruz.
                    withStyle(SpanStyle(color = TextPrimary)) { append("°") }
                },
                color = TextPrimary,
                style = HeroTemperatureStyle.copy(shadow = TextShadow),
                modifier = Modifier.padding(start = 36.dp),
            )
        }
        Text(
            text = stringResource(weatherDescriptionRes(current.weatherCode)),
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow),
        )
        if (today != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.high_low,
                    today.maxTemperature.roundToInt(),
                    today.minTemperature.roundToInt(),
                ),
                color = TextSecondary,
                style = MaterialTheme.typography.titleMedium.copy(shadow = TextShadow),
            )
        }
        if (locationStatus != LocationStatus.Current) {
            Spacer(Modifier.height(18.dp))
            LocationChip(locationStatus, onClick = { onLocationAction(locationStatus) })
        }
    }
}

/** Başlığın altındaki küçük durum satırının hali. */
enum class HeaderStatus { Idle, Refreshing, Failed }

@Composable
private fun StatusLine(status: HeaderStatus, weather: WeatherSnapshot) {
    val ago = relativeTimeSince(weather.fetchedAt)
    val text = when (status) {
        HeaderStatus.Refreshing -> stringResource(R.string.updating)
        HeaderStatus.Failed -> stringResource(R.string.update_failed)
        HeaderStatus.Idle -> if (ago == null) {
            stringResource(R.string.updated_just_now)
        } else {
            stringResource(R.string.updated_ago, ago)
        }
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "statusLine",
    ) { value ->
        Text(
            text = value,
            color = if (status == HeaderStatus.Failed) WarningAccent else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Konum kullanılamadığında ne yapılabileceğini söyleyen, tıklanabilir hap düğme. */
@Composable
private fun LocationChip(status: LocationStatus, onClick: () -> Unit) {
    val text = when (status) {
        is LocationStatus.NoPermission -> if (status.canAskAgain) {
            stringResource(R.string.use_my_location)
        } else {
            stringResource(R.string.location_permission_in_settings)
        }
        LocationStatus.Unavailable -> stringResource(R.string.location_unavailable)
        LocationStatus.Current -> return
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
