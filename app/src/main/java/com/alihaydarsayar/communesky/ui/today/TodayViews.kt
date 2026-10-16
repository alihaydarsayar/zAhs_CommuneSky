package com.alihaydarsayar.communesky.ui.today

import com.alihaydarsayar.communesky.ui.common.touchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.lifestyle.EvaluatedTip
import com.alihaydarsayar.communesky.lifestyle.TipStatus
import com.alihaydarsayar.communesky.lifestyle.TodayAdvice
import com.alihaydarsayar.communesky.lifestyle.label
import com.alihaydarsayar.communesky.lifestyle.sentence
import com.alihaydarsayar.communesky.model.TemperatureUnit
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.rememberHourFormatter
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Durum renkleri: iyi / idare eder / uygun değil. */
val TipStatus.color: Color
    get() = when (this) {
        TipStatus.Good -> Color(0xFF86EFAC)
        TipStatus.Fair -> Color(0xFFFDE047)
        TipStatus.Poor -> Color(0xFFFB923C)
    }

/** Bu yerin "Bugün için" önerileri; hava verisi ya da kişisel ayar değişince yeniden hesaplanır. */
@Composable
fun rememberTodayAdvice(weather: WeatherSnapshot): TodayAdvice {
    val shift = LocalAppSettings.current.wear.shift
    return remember(weather, shift) { TodayAdvice.of(weather.forecast, weather.forecast.localNow(), shift, weather.airQualityPoor) }
}

/**
 * Ana ekrandaki "Bugün için" kartı: ne giysem (ikonlar ve cümle), en önemli iki öneri ve "Tümünü gör".
 * Akşam geç saatte giyim önerisi yarın içindir; başlık bunu söyler.
 */
@Composable
fun TodayCard(advice: TodayAdvice, onFeedback: (Boolean) -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), title = stringResource(R.string.today_card_title)) {
        val wear = advice.wear
        if (wear != null) {
            if (wear.forTomorrow) {
                Text(stringResource(R.string.wear_title_tomorrow), color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
            }
            WearSection(wear, onFeedback)
            Divider()
        }
        for (tip in advice.top) TipRow(tip)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onSeeAll)
                .touchTarget()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.today_see_all), color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(top = 14.dp, bottom = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f)),
    )
}

/** Bir öneri: ikon, başlık, tek satır gerekçe ve sağda durum noktası. */
@Composable
fun TipRow(tip: EvaluatedTip, modifier: Modifier = Modifier) {
    val strings = rememberStrings()
    val hour = rememberHourFormatter()
    val fahrenheit = LocalAppSettings.current.temperatureUnit == TemperatureUnit.Fahrenheit
    val sentence = remember(tip, strings, hour, fahrenheit) {
        tip.result.sentence(strings, { it.format(hour) }) { (if (fahrenheit) it * 1.8 else it).roundToInt() }
    }
    val status = stringResource(tip.result.status.label)
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(tip.tip.icon), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(tip.tip.title), color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(sentence, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tip.result.status.color)
                .semantics { contentDescription = status },
        )
    }
}

/** "Bugün için" sayfası: giyim önerisi dilimleriyle birlikte ve bütün yaşam önerileri. */
@Composable
fun TodayScreen(weather: WeatherSnapshot?, onFeedback: (Boolean) -> Unit, onBack: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.today_card_title), onBack)
        if (weather == null) return@Column
        val advice = rememberTodayAdvice(weather)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val wear = advice.wear
            if (wear != null) {
                item(key = "wear") {
                    GlassCard(Modifier.fillMaxWidth(), title = stringResource(if (wear.forTomorrow) R.string.wear_title_tomorrow else R.string.wear_title)) {
                        WearSection(wear, onFeedback, alwaysExpanded = true)
                    }
                }
            }
            item(key = "tips") {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.today_tips_title)) {
                    for (tip in advice.tips) TipRow(tip)
                    StatusLegend()
                }
            }
            item(key = "note") {
                Text(
                    stringResource(R.string.tips_disclaimer),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Noktaların anlamı: iyi / idare eder / uygun değil. */
@Composable
private fun StatusLegend() {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (status in TipStatus.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(status.color))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(status.label), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
