package com.alihaydarsayar.communesky.work

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.widget.WidgetLanguage

/** Bildirim türleri. Her tür için iki kanal vardır: sesli ve sessiz. */
enum class NotificationKind(
    val id: String,
    val group: String,
    /** Sesli kanalın önemi: kırmızı uyarı ekranın üstünde belirir, diğerleri yalnız ses çıkarır. */
    val soundImportance: Int,
    @param:StringRes val channelName: Int,
) {
    RedAlert("alert_red", GROUP_ALERTS, NotificationManager.IMPORTANCE_HIGH, R.string.channel_red),
    OrangeAlert("alert_orange", GROUP_ALERTS, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_orange),
    YellowAlert("alert_yellow", GROUP_ALERTS, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_yellow),
    Morning("summary_morning", GROUP_SUMMARIES, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_morning),
    Evening("summary_evening", GROUP_SUMMARIES, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_evening),
    AirQuality("air_quality_v2", GROUP_AIR, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_air),
    ;

    val soundChannel: String get() = "${id}_sound"

    /**
     * Sessiz kanal. Özetlerin sessiz kanalı bildirim gölgesinde görünür kalır (önem varsayılan, yalnız ses ve
     * titreşim kapalı): MIUI/HyperOS gibi arayüzler düşük önemli bildirimleri "sessiz bildirimler" altında toplayıp
     * gizleyebilir. Önem sonradan değiştirilemediği için yeni kimlik kullanılır; eski "_silent" kanalı silinir.
     */
    val silentChannel: String get() = if (visibleWhenSilent) "${id}_quiet" else "${id}_silent"

    /** Sessiz kanalı özetlerden önceki sürümlerde (düşük önem) kurulmuş kimlik; kullanılıyorsa silinir. */
    val retiredSilentChannel: String? get() = if (visibleWhenSilent) "${id}_silent" else null

    /** Özet bildirimleri sessiz olsa da bildirim gölgesinde görünür. */
    val visibleWhenSilent: Boolean get() = group == GROUP_SUMMARIES

    val silentImportance: Int get() = if (visibleWhenSilent) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW

    companion object {
        fun of(level: AlertLevel): NotificationKind = when (level) {
            AlertLevel.Red -> RedAlert
            AlertLevel.Orange -> OrangeAlert
            AlertLevel.Yellow -> YellowAlert
        }
    }
}

private const val GROUP_ALERTS = "alerts"
private const val GROUP_SUMMARIES = "summaries"
private const val GROUP_AIR = "air"

/**
 * Bir bildirimin hangi kanala gideceği. Android bir kanalın önemini sonradan uygulamanın değiştirmesine izin
 * vermez; bu yüzden her türün sesli ve sessiz iki kanalı vardır ve kullanıcının seçimi kanalı belirler. Sessiz
 * saatlerde bütün bildirimler sessiz kanala gider; kırmızı uyarı, kullanıcı öyle istediyse sesli kalır.
 * Saf; test edilir.
 */
object NotificationRouting {

    fun wantsSound(kind: NotificationKind, settings: NotificationSettings): Boolean = when (kind) {
        NotificationKind.RedAlert -> settings.redSound
        NotificationKind.OrangeAlert -> settings.orangeSound
        NotificationKind.YellowAlert -> settings.yellowSound
        NotificationKind.Morning -> settings.morningSound
        NotificationKind.Evening -> settings.eveningSound
        NotificationKind.AirQuality -> settings.airSound
    }

    /** [minute] (gece yarısından itibaren) sessiz saatlerin içinde mi? Aralık gece yarısını aşabilir (23:00–07:00). */
    fun inQuietHours(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    fun channel(kind: NotificationKind, settings: NotificationSettings, minuteOfDay: Int): String {
        val quiet = settings.quietHours && inQuietHours(minuteOfDay, settings.quietStart, settings.quietEnd) &&
            !(kind == NotificationKind.RedAlert && settings.quietRedSounds)
        return if (wantsSound(kind, settings) && !quiet) kind.soundChannel else kind.silentChannel
    }
}

/** Kanalları ve grupları uygulamanın diliyle kurar; eski tek kanalları kaldırır. */
object NotificationChannels {

    /** 1.5.1'e kadarki tek kanallar. */
    private val LEGACY = listOf("weather_alerts", "morning_summary", "air_quality")

    /** Eski kanallardan kullanıcının Android'de kapattıkları (geçişte ilgili ayar da kapanır). */
    data class LegacyBlocked(val alerts: Boolean, val morning: Boolean, val airQuality: Boolean)

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = WidgetLanguage.context(context)
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_ALERTS, localized.getString(R.string.channel_group_alerts)),
                NotificationChannelGroup(GROUP_SUMMARIES, localized.getString(R.string.channel_group_summaries)),
                NotificationChannelGroup(GROUP_AIR, localized.getString(R.string.channel_group_air)),
            ),
        )
        // Düşük önemli eski sessiz özet kanalları silinir; kullanıcının ses/sessiz seçimi ayarlarda durur, yeni kanala uyar.
        NotificationKind.entries.mapNotNull { it.retiredSilentChannel }.forEach { id ->
            if (manager.getNotificationChannel(id) != null) runCatching { manager.deleteNotificationChannel(id) }
        }
        val channels = NotificationKind.entries.flatMap { kind ->
            val name = localized.getString(kind.channelName)
            listOf(
                NotificationChannel(kind.soundChannel, name, kind.soundImportance).apply {
                    group = kind.group
                    description = localized.getString(R.string.channel_sound_description)
                },
                NotificationChannel(kind.silentChannel, localized.getString(R.string.channel_silent, name), kind.silentImportance).apply {
                    group = kind.group
                    description = localized.getString(R.string.channel_silent_description)
                    // Önem varsayılan olsa da ses ve titreşim yok: bildirim gölgesinde görünür, çalmaz.
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        manager.createNotificationChannels(channels)
    }

    /** Eski kanalları siler; hangilerinin kullanıcı tarafından kapatılmış olduğunu döndürür. Bir kez çağrılır. */
    fun removeLegacy(context: Context): LegacyBlocked {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return LegacyBlocked(false, false, false)
        fun blocked(id: String) = manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
        val result = LegacyBlocked(blocked(LEGACY[0]), blocked(LEGACY[1]), blocked(LEGACY[2]))
        LEGACY.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        return result
    }

    /** Android'in bu uygulamaya ait bildirim ayarları (titreşim, kilit ekranı, kanal ayrıntıları). */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
