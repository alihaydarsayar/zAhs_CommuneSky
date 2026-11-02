package com.alihaydarsayar.communesky.work

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.remote.NetworkFailure
import com.alihaydarsayar.communesky.data.remote.NetworkFailureLog
import com.alihaydarsayar.communesky.data.remote.ServiceName
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.model.today.SummaryKind
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Bir bildirim kanalının Android'deki durumu. [importance] null: kanal kurulmamış. */
data class ChannelState(val kind: NotificationKind, val channelId: String, val silent: Boolean, val importance: Int?, val lockscreen: Int? = null)

/** Telefonun güç ve ağ durumu; okunamayan null. Ağ adı ya da konum asla okunmaz. */
data class DeviceState(val powerSave: Boolean?, val ignoresBatteryLimits: Boolean?, val standbyBucket: Int?, val connection: Connection?)

/** Bağlantı türü: yalnızca "Wi-Fi", "mobil" ya da "yok". */
enum class Connection { WiFi, Mobile, Other, None }

/** Sistemin kilit ekranı bildirim ayarları; okunamayan null. */
data class LockScreenSettings(val showNotifications: Boolean?, val showSilent: Boolean?)

/** Bir özetin sıradaki planlı zamanı ve Android'de gerçekten kayıtlı bir alarm olup olmadığı. */
data class PlannedSummary(val kind: SummaryKind, val enabled: Boolean, val at: Instant?, val alarmSet: Boolean)

/**
 * Ayarlar › Bildirimler'deki "Bildirim durumu" ve "Durumu kopyala" metni için bilgiler. Yalnız izin, kanal, pil,
 * saat dilimi, planlanan zaman ve son deneme sonuçlarıdır; yer adı, konum, hava verisi ve widget bilgisi yoktur.
 * Hiçbir yere gönderilmez; yalnız kullanıcı dokununca panoya kopyalanır.
 */
data class NotificationStatus(
    val permission: Boolean,
    val appEnabled: Boolean,
    val channels: List<ChannelState>,
    val backgroundRestricted: Boolean,
    val zone: ZoneId,
    val planned: List<PlannedSummary>,
    val attempts: List<Attempt>,
    val lockScreen: LockScreenSettings,
    val device: DeviceState,
    val failures: List<NetworkFailure>,
    val lastUpdate: Long?,
    val appVersion: String,
    val androidSdk: Int,
    val model: String,
) {
    val lastSent: Attempt? get() = AttemptLog.lastSent(attempts)
    val lastTest: Attempt? get() = AttemptLog.last(attempts, AttemptKind.Test)

    companion object {

        fun read(context: Context, settings: NotificationSettings, now: Instant = Instant.now()): NotificationStatus {
            NotificationChannels.ensure(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            val log = NotificationLog(context)
            val channels = NotificationHealth.wantedKinds(settings).map { kind ->
                val silent = !NotificationRouting.wantsSound(kind, settings)
                val id = if (silent) kind.silentChannel else kind.soundChannel
                manager?.getNotificationChannel(id).let { ChannelState(kind, id, silent, it?.importance, it?.lockscreenVisibility) }
            }
            val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
            val planned = SummaryKind.entries.map { kind ->
                val attempt = if (kind == SummaryKind.Morning) AttemptKind.Morning else AttemptKind.Evening
                val enabled = if (kind == SummaryKind.Morning) settings.morning else settings.evening
                PlannedSummary(kind, enabled, log.planned(attempt, now), MorningSummary.isScheduled(context, kind))
            }
            val version = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
            }.getOrDefault("?")
            return NotificationStatus(
                permission = NotificationHealth.permissionGranted(context),
                appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                channels = channels,
                backgroundRestricted = restricted,
                zone = ZoneId.systemDefault(),
                planned = planned,
                attempts = log.entries(now),
                lockScreen = readLockScreen(context),
                device = readDevice(context),
                failures = NetworkFailureLog(context).entries(now.toEpochMilli()),
                lastUpdate = NetworkFailureLog(context).lastSuccess(),
                appVersion = version,
                androidSdk = Build.VERSION.SDK_INT,
                model = Build.MODEL.orEmpty(),
            )
        }

        /** Sistemin kilit ekranı ayarları (Settings.Secure); bu anahtarlar her telefonda okunamaz, o zaman null. */
        fun readLockScreen(context: Context): LockScreenSettings {
            fun flag(key: String): Boolean? = runCatching {
                android.provider.Settings.Secure.getInt(context.contentResolver, key).let { it != 0 }
            }.getOrNull()
            return LockScreenSettings(flag("lock_screen_show_notifications"), flag("lock_screen_show_silent_notifications"))
        }

        /** Güç tasarrufu, pil optimizasyonu, bekleme kovası ve bağlantı türü. İzin gerektirmez (bağlantı için ACCESS_NETWORK_STATE zaten bildirilmiş). */
        fun readDevice(context: Context): DeviceState {
            val power = context.getSystemService(android.os.PowerManager::class.java)
            val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { context.getSystemService(android.app.usage.UsageStatsManager::class.java)?.appStandbyBucket }.getOrNull()
            } else {
                null
            }
            val connection = runCatching {
                val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
                val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork ?: return@runCatching Connection.None)
                when {
                    capabilities == null -> Connection.None
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> Connection.WiFi
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> Connection.Mobile
                    else -> Connection.Other
                }
            }.getOrNull()
            return DeviceState(
                powerSave = runCatching { power?.isPowerSaveMode }.getOrNull(),
                ignoresBatteryLimits = runCatching { power?.isIgnoringBatteryOptimizations(context.packageName) }.getOrNull(),
                standbyBucket = bucket,
                connection = connection,
            )
        }

        /** Kanalın kilit ekranı görünürlüğü, kullanıcının dilinde. */
        fun lockscreenLabel(context: Context, visibility: Int?): String = context.getString(
            when (visibility) {
                null -> R.string.status_unreadable
                android.app.Notification.VISIBILITY_PUBLIC -> R.string.status_lock_public
                android.app.Notification.VISIBILITY_PRIVATE -> R.string.status_lock_private
                android.app.Notification.VISIBILITY_SECRET -> R.string.status_lock_secret
                else -> R.string.status_lock_default
            },
        )

        /** Android kanal önemi, kullanıcının dilinde. */
        fun importanceLabel(context: Context, importance: Int?): String = context.getString(
            when (importance) {
                null -> R.string.status_channel_missing
                NotificationManager.IMPORTANCE_NONE -> R.string.status_importance_none
                NotificationManager.IMPORTANCE_MIN, NotificationManager.IMPORTANCE_LOW -> R.string.status_importance_low
                NotificationManager.IMPORTANCE_DEFAULT -> R.string.status_importance_default
                else -> R.string.status_importance_high
            },
        )
    }

    /** Ekranda ve kopyalanan metinde görünen satırlar: (etiket, değer). Sıra sabit; metin uygulamanın dilinde. */
    fun rows(context: Context): List<Pair<String, String>> {
        val localized = WidgetLanguage.context(context)
        val locale = localized.resources.configuration.locales[0]
        val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        fun time(at: Instant) = at.atZone(zone).format(dateTime)
        fun yes(value: Boolean) = localized.getString(if (value) R.string.status_on else R.string.status_off)
        val rows = mutableListOf<Pair<String, String>>()
        rows += localized.getString(R.string.status_permission) to yes(permission)
        rows += localized.getString(R.string.status_app) to yes(appEnabled)
        for (channel in channels) {
            val name = localized.getString(channel.kind.channelName).let { if (channel.silent) localized.getString(R.string.channel_silent, it) else it }
            rows += name to importanceLabel(localized, channel.importance)
            rows += localized.getString(R.string.status_lock_channel, name) to lockscreenLabel(localized, channel.lockscreen)
        }
        fun known(value: Boolean?) = value?.let { yes(it) } ?: localized.getString(R.string.status_unreadable)
        rows += localized.getString(R.string.status_lock_system) to known(lockScreen.showNotifications)
        rows += localized.getString(R.string.status_lock_silent) to known(lockScreen.showSilent)
        rows += localized.getString(R.string.status_battery) to localized.getString(if (backgroundRestricted) R.string.status_restricted else R.string.status_unrestricted)
        rows += localized.getString(R.string.status_power_save) to (device.powerSave?.let { yes(it) } ?: localized.getString(R.string.status_unreadable))
        rows += localized.getString(R.string.status_battery_optimization) to (device.ignoresBatteryLimits?.let {
            localized.getString(if (it) R.string.status_unrestricted else R.string.status_restricted)
        } ?: localized.getString(R.string.status_unreadable))
        rows += localized.getString(R.string.status_standby) to standbyLabel(localized, device.standbyBucket)
        rows += localized.getString(R.string.status_connection) to localized.getString(
            when (device.connection) {
                Connection.WiFi -> R.string.status_conn_wifi
                Connection.Mobile -> R.string.status_conn_mobile
                Connection.Other -> R.string.status_conn_other
                Connection.None -> R.string.status_conn_none
                null -> R.string.status_unreadable
            },
        )
        rows += localized.getString(R.string.status_zone) to zone.id
        for (plan in planned) {
            val name = localized.getString(R.string.status_next, localized.getString(if (plan.kind == SummaryKind.Morning) R.string.channel_morning else R.string.channel_evening))
            val value = when {
                !plan.enabled -> localized.getString(R.string.status_summary_off)
                plan.at == null -> localized.getString(R.string.status_alarm_not_set)
                plan.alarmSet -> localized.getString(R.string.status_alarm_set, time(plan.at))
                else -> localized.getString(R.string.status_alarm_not_set_at, time(plan.at))
            }
            rows += name to value
        }
        rows += localized.getString(R.string.status_last_update) to (lastUpdate?.let { time(Instant.ofEpochMilli(it)) } ?: localized.getString(R.string.status_none))
        rows += localized.getString(R.string.status_failures) to if (failures.isEmpty()) {
            localized.getString(R.string.status_none)
        } else {
            failures.joinToString("; ") { failure ->
                val service = ServiceName.of(failure.host)
                val what = if (failure.code == 0) localized.getString(R.string.status_no_connection) else "HTTP ${failure.code}" + (failure.retryAfterSeconds?.let { " (Retry-After ${it}s)" } ?: "")
                "${time(Instant.ofEpochMilli(failure.at))} $service $what"
            }
        }
        rows += localized.getString(R.string.status_last_sent) to (lastSent?.let { time(it.at) } ?: localized.getString(R.string.status_none))
        rows += localized.getString(R.string.status_last_test) to (lastTest?.let { "${time(it.at)} · ${text(localized, it)}" } ?: localized.getString(R.string.status_none))
        attempts.filter { it.kind != AttemptKind.Test }.sortedByDescending { it.at }.forEach { attempt ->
            val kind = localized.getString(if (attempt.kind == AttemptKind.Morning) R.string.channel_morning else R.string.channel_evening)
            rows += "${time(attempt.at)} · $kind" to text(localized, attempt)
        }
        rows += localized.getString(R.string.status_app_info) to "$appVersion · Android $androidSdk · $model"
        return rows
    }

    /** Panoya kopyalanan metin: yalnız [rows]. */
    fun copyText(context: Context): String {
        val localized = WidgetLanguage.context(context)
        return (listOf(localized.getString(R.string.status_title)) + rows(context).map { (label, value) -> "$label: $value" }).joinToString("\n")
    }

    private fun standbyLabel(context: Context, bucket: Int?): String = when (bucket) {
        null -> context.getString(R.string.status_unreadable)
        10 -> context.getString(R.string.status_bucket_active)
        20 -> context.getString(R.string.status_bucket_working)
        30 -> context.getString(R.string.status_bucket_frequent)
        40 -> context.getString(R.string.status_bucket_rare)
        45 -> context.getString(R.string.status_bucket_restricted)
        50 -> context.getString(R.string.status_bucket_never)
        else -> bucket.toString()
    }

    private fun text(context: Context, attempt: Attempt): String = com.alihaydarsayar.communesky.ui.settings.AttemptText.of(context, attempt.kind, attempt.outcome, attempt.detail)

    /** Bir sorun var mı (uyarı kartı için). */
    val hasProblem: Boolean
        get() = !permission || !appEnabled || channels.any { it.importance == NotificationManager.IMPORTANCE_NONE } ||
            planned.any { it.enabled && !it.alarmSet }
}
