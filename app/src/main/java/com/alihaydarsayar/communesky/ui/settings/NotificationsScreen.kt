package com.alihaydarsayar.communesky.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.AlertPlaces
import com.alihaydarsayar.communesky.model.AlertThreshold
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassRadioRow
import com.alihaydarsayar.communesky.ui.common.GlassSegmented
import com.alihaydarsayar.communesky.ui.common.GlassSwitchRow
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.rememberTimeFormatter
import com.alihaydarsayar.communesky.ui.home.color
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.today.GlassPill
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.work.AlertNotifier
import com.alihaydarsayar.communesky.work.Maker
import com.alihaydarsayar.communesky.work.NotificationHealth
import com.alihaydarsayar.communesky.work.NotificationIssues
import com.alihaydarsayar.communesky.work.NotificationChannels
import java.time.LocalTime

/**
 * Ayarlar › Bildirimler: her türün açma/kapama, ses ve saat seçimi, sessiz saatler ve Android'in
 * kanal ayarlarına bağlantı. Bildirim izni yalnızca bir tür açılırken istenir.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications = settings.notifications
    val context = LocalContext.current
    val activity = LocalActivity.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // İzin Android ayarlarında değişebilir; ekrana her dönüşte yeniden bakılır.
    var permitted by remember { mutableStateOf(AlertNotifier.canNotify(context)) }
    // Kanal ve arka plan kısıtı da Android ayarlarında değişir: ekrana her dönüşte ve ayar değişince yeniden bakılır.
    var issues by remember { mutableStateOf(NotificationIssues()) }
    LifecycleResumeEffect(notifications) {
        permitted = AlertNotifier.canNotify(context)
        issues = NotificationHealth.issues(context, notifications)
        onPauseOrDispose { }
    }
    val maker = remember { Maker.of(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = AlertNotifier.canNotify(context)
        // İzin bir kez daha sorulamıyorsa (iki kez reddedildiyse) Android'in ayarları açılır.
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (!granted && !canAskAgain) context.startActivity(NotificationChannels.settingsIntent(context))
    }
    fun requestPermission() {
        // Android 13+ izni sorulur; izin varken bildirimler Android ayarlarından kapatıldıysa oraya gidilir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(NotificationChannels.settingsIntent(context))
        }
    }

    /** Bir türü açarken izin yoksa sorar. */
    fun change(turnsOn: Boolean = false, update: (NotificationSettings) -> NotificationSettings) {
        viewModel.updateNotifications(update)
        if (turnsOn && !AlertNotifier.canNotify(context)) requestPermission()
    }

    fun pickTime(minutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            activity ?: return,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            minutes / 60, minutes % 60, WidgetLanguage.is24Hour(context),
        ).show()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings_notifications), onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!permitted) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(stringResource(R.string.notifications_permission_off), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.notifications_permission_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            GlassPill(stringResource(R.string.notifications_permission_allow)) { requestPermission() }
                        }
                    }
                }
            }
            if (issues.any) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.notif_health_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        issues.blockedChannel?.let { channel ->
                            HealthRow(stringResource(R.string.notif_health_channel), stringResource(R.string.notif_health_open)) {
                                context.startActivity(NotificationHealth.channelSettingsIntent(context, channel))
                            }
                        }
                        if (issues.backgroundRestricted) {
                            HealthRow(stringResource(R.string.notif_health_background), stringResource(R.string.notif_health_open)) {
                                context.startActivity(NotificationHealth.appDetailsIntent(context))
                            }
                        }
                        maker.tip?.let { Hint(stringResource(it)) }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.channel_group_alerts)) {
                    GlassSwitchRow(
                        title = stringResource(R.string.settings_alert_notifications),
                        checked = notifications.alerts,
                        onCheckedChange = { on -> change(on) { it.copy(alerts = on) } },
                        subtitle = stringResource(R.string.settings_alert_notifications_hint),
                    )
                    if (notifications.alerts) {
                        Spacer(Modifier.height(8.dp))
                        Label(stringResource(R.string.notifications_which_alerts))
                        GlassSegmented(
                            options = listOf(
                                stringResource(R.string.notifications_threshold_yellow),
                                stringResource(R.string.notifications_threshold_orange),
                                stringResource(R.string.notifications_threshold_red),
                            ),
                            selectedIndex = notifications.alertThreshold.ordinal,
                            onSelect = { index -> change { it.copy(alertThreshold = AlertThreshold.entries[index]) } },
                        )
                        Spacer(Modifier.height(12.dp))
                        Label(stringResource(R.string.notifications_which_places))
                        val places = listOf(R.string.notifications_places_current, R.string.notifications_places_home, R.string.notifications_places_all)
                        AlertPlaces.entries.forEachIndexed { index, choice ->
                            GlassRadioRow(
                                title = stringResource(places[index]),
                                selected = notifications.alertPlaces == choice,
                                onClick = { change { it.copy(alertPlaces = choice) } },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Label(stringResource(R.string.notifications_sound))
                        LevelSoundRow(AlertLevel.Red, R.string.notify_level_red, notifications.redSound, enabled = true) { on ->
                            change { it.copy(redSound = on) }
                        }
                        LevelSoundRow(AlertLevel.Orange, R.string.notify_level_orange, notifications.orangeSound, enabled = notifications.alertThreshold != AlertThreshold.Red) { on ->
                            change { it.copy(orangeSound = on) }
                        }
                        LevelSoundRow(AlertLevel.Yellow, R.string.notify_level_yellow, notifications.yellowSound, enabled = notifications.alertThreshold == AlertThreshold.Yellow) { on ->
                            change { it.copy(yellowSound = on) }
                        }
                        Hint(stringResource(R.string.notifications_sound_hint))
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.channel_group_summaries)) {
                    GlassSwitchRow(
                        title = stringResource(R.string.settings_morning_summary),
                        checked = notifications.morning,
                        onCheckedChange = { on -> change(on) { it.copy(morning = on) } },
                        subtitle = stringResource(R.string.settings_morning_summary_hint),
                    )
                    if (notifications.morning) {
                        TimeRow(stringResource(R.string.settings_morning_summary_time), notifications.morningMinutes) {
                            pickTime(notifications.morningMinutes) { minutes -> change { it.copy(morningMinutes = minutes) } }
                        }
                        SoundRow(notifications.morningSound) { on -> change { it.copy(morningSound = on) } }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                    GlassSwitchRow(
                        title = stringResource(R.string.notifications_evening),
                        checked = notifications.evening,
                        onCheckedChange = { on -> change(on) { it.copy(evening = on) } },
                        subtitle = stringResource(R.string.notifications_evening_hint),
                    )
                    if (notifications.evening) {
                        TimeRow(stringResource(R.string.settings_morning_summary_time), notifications.eveningMinutes) {
                            pickTime(notifications.eveningMinutes) { minutes -> change { it.copy(eveningMinutes = minutes) } }
                        }
                        SoundRow(notifications.eveningSound) { on -> change { it.copy(eveningSound = on) } }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.channel_group_air)) {
                    GlassSwitchRow(
                        title = stringResource(R.string.settings_air_notifications),
                        checked = notifications.airQuality,
                        onCheckedChange = { on -> change(on) { it.copy(airQuality = on) } },
                        subtitle = stringResource(R.string.settings_air_notifications_hint),
                    )
                    if (notifications.airQuality) {
                        SoundRow(notifications.airSound) { on -> change { it.copy(airSound = on) } }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.notifications_quiet)) {
                    GlassSwitchRow(
                        title = stringResource(R.string.notifications_quiet),
                        checked = notifications.quietHours,
                        onCheckedChange = { on -> change { it.copy(quietHours = on) } },
                        subtitle = stringResource(R.string.notifications_quiet_hint),
                    )
                    if (notifications.quietHours) {
                        TimeRow(stringResource(R.string.notifications_quiet_start), notifications.quietStart) {
                            pickTime(notifications.quietStart) { minutes -> change { it.copy(quietStart = minutes) } }
                        }
                        TimeRow(stringResource(R.string.notifications_quiet_end), notifications.quietEnd) {
                            pickTime(notifications.quietEnd) { minutes -> change { it.copy(quietEnd = minutes) } }
                        }
                        GlassSwitchRow(
                            title = stringResource(R.string.notifications_quiet_red),
                            checked = notifications.quietRedSounds,
                            onCheckedChange = { on -> change { it.copy(quietRedSounds = on) } },
                            subtitle = stringResource(R.string.notifications_quiet_red_hint),
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.notif_test_title)) {
                    Text(stringResource(R.string.notif_test_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    // Özetlerle aynı kanaldan örnek bildirim; izin yoksa önce izin istenir. Hiçbir veri gönderilmez.
                    Spacer(Modifier.height(10.dp))
                    GlassPill(stringResource(R.string.notif_test_button)) {
                        if (NotificationHealth.sendTest(context, notifications)) {
                            issues = NotificationHealth.issues(context, notifications)
                        } else {
                            requestPermission()
                        }
                    }
                    // Hiçbir sorun algılanmasa da bazı üreticiler uygulamayı arka planda kapatır; yönlendirme yalnız onlarda.
                    if (!issues.any && notifications.anyOn) maker.tip?.let { Hint(stringResource(it)) }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { context.startActivity(NotificationChannels.settingsIntent(context)) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.notifications_android), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.notifications_android_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/** Uyarı kartındaki bir satır: durum ve tek dokunuşla ilgili Android ayarını açan düğme. */
@Composable
private fun HealthRow(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(end = 12.dp))
        GlassPill(action, onClick = onClick)
    }
}

/** Uyarı seviyesinin ses seçimi: renkli nokta, adı ve Sesli/Sessiz. Seçilen eşiğin altındaki seviyeler soluk. */
@Composable
private fun LevelSoundRow(level: AlertLevel, name: Int, sound: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(name), color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SoundChoice(sound) { if (enabled) onChange(it) }
    }
}

/** Özetlerin ve hava kalitesinin "Ses" satırı. */
@Composable
private fun SoundRow(sound: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.notifications_sound), color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SoundChoice(sound, onChange)
    }
}

@Composable
private fun SoundChoice(sound: Boolean, onChange: (Boolean) -> Unit) {
    GlassSegmented(
        options = listOf(stringResource(R.string.notifications_sound_on), stringResource(R.string.notifications_sound_off)),
        selectedIndex = if (sound) 0 else 1,
        onSelect = { onChange(it == 0) },
        modifier = Modifier.width(184.dp),
    )
}

/** "Saat 07:30 ›" satırı; dokununca Android'in saat seçicisi açılır. */
@Composable
private fun TimeRow(label: String, minutes: Int, onClick: () -> Unit) {
    val formatter = rememberTimeFormatter()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(LocalTime.of(minutes / 60, minutes % 60).format(formatter), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun ColumnScope.Hint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}
