package com.alihaydarsayar.communesky.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.today.GlassPill
import com.alihaydarsayar.communesky.work.Attempt
import com.alihaydarsayar.communesky.work.AttemptKind
import com.alihaydarsayar.communesky.work.Maker
import com.alihaydarsayar.communesky.work.NotificationHealth

/**
 * Bir özet gelmediyse (alarm tetiklenmedi) uygulama açılınca gösterilen, kapatılabilir kart: nedeni olabilecek telefon
 * ayarına tek dokunuşla gider ve üreticiye özel kısa yönlendirmeyi yazar. Dışarıya bağlantı yoktur.
 */
@Composable
fun MissedSummaryCard(missed: Attempt, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val maker = remember { Maker.of(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) }
    val name = stringResource(if (missed.kind == AttemptKind.Evening) R.string.channel_evening else R.string.channel_morning)
    GlassCard(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.missed_title, name), color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.missed_text), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                maker.tip?.let { Text(stringResource(it), color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
            GlassPill(stringResource(R.string.notif_health_open)) { NotificationHealth.startBackgroundSettings(context, maker) }
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close), tint = TextSecondary)
            }
        }
    }
}
