package com.alihaydarsayar.communesky.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.today.AndroidStrings
import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.ClothingAdvice
import com.alihaydarsayar.communesky.model.today.PartAdvice
import com.alihaydarsayar.communesky.model.today.Strings
import com.alihaydarsayar.communesky.model.today.WearText
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.asTemperature
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ekranın dilindeki metin kaynağı; dil değişince yenilenir. */
@Composable
fun rememberStrings(): Strings {
    val context = LocalContext.current
    return remember(context, LocalConfiguration.current) { AndroidStrings(context) }
}

/** Bir sıra çizgi ikon: önce katman (tişört, kazak, mont…), sonra ekler (şemsiye, gözlük…). */
@Composable
fun WearIcons(advice: ClothingAdvice, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (icon in WearText.icons(advice)) {
            Icon(painterResource(icon), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(size))
        }
    }
}

/**
 * "Ne giysem": ikonlar ve tek cümle. Dokununca sabah / öğle / akşam ayrıntısı ve "Üşüdüm" / "Bunaldım"
 * düğmeleri açılır.
 */
@Composable
fun WearSection(advice: ClothingAdvice, onFeedback: (feltCold: Boolean) -> Unit, modifier: Modifier = Modifier, alwaysExpanded: Boolean = false) {
    val strings = rememberStrings()
    val unit = LocalAppSettings.current.temperatureUnit
    val sentence = remember(advice, strings, unit) { WearText.sentence(advice, strings) { unit.fromCelsius(it).roundToInt() } }
    var opened by rememberSaveable { mutableStateOf(false) }
    val expanded = opened || alwaysExpanded
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    enabled = !alwaysExpanded,
                    role = Role.Button,
                    onClickLabel = stringResource(if (expanded) R.string.wear_details_hide else R.string.wear_details_show),
                ) { opened = !opened }
                .padding(vertical = 4.dp),
        ) {
            WearIcons(advice)
            Spacer(Modifier.height(10.dp))
            Text(sentence, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                for (part in advice.parts) PartRow(part)
                Spacer(Modifier.height(12.dp))
                FeedbackButtons(onFeedback)
            }
        }
    }
}

/** "Sabah   hissedilen 11°   Kazak ya da ince mont". */
@Composable
private fun PartRow(part: PartAdvice) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(WearText.label(part.weather.part)),
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(WearText.name(part.layer)), color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.wear_part_feels, part.weather.feelsLike.asTemperature()),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (icon in WearText.icons(part.layer).take(2)) {
                Icon(painterResource(icon), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** "Üşüdüm" / "Bunaldım": her basış öneri eşiğini yarım derece kaydırır (en çok ±4°). */
@Composable
private fun FeedbackButtons(onFeedback: (feltCold: Boolean) -> Unit) {
    val feedback = LocalAppSettings.current.wear.feedback
    // Son basılan düğme: altında kısa bir onay yazısı çıkar.
    var last by remember { mutableStateOf<Boolean?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPill(stringResource(R.string.wear_felt_cold), Modifier.weight(1f)) {
            last = true
            onFeedback(true)
        }
        GlassPill(stringResource(R.string.wear_felt_hot), Modifier.weight(1f)) {
            last = false
            onFeedback(false)
        }
    }
    last?.let { feltCold ->
        val atLimit = abs(feedback) >= Clothing.FEEDBACK_LIMIT && (feedback > 0) == feltCold
        Text(
            stringResource(
                when {
                    atLimit -> R.string.wear_feedback_limit
                    feltCold -> R.string.wear_feedback_colder
                    else -> R.string.wear_feedback_warmer
                },
            ),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
}

/** Kart içinde ince kenarlı küçük düğme. */
@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
