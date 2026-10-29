package com.alihaydarsayar.communesky.work

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertThreshold
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Telefon üreticisi: bazıları uygulamayı arka planda agresifçe kapatır; ona göre kısa bir yönlendirme gösterilir. */
enum class Maker(@param:StringRes val tip: Int?) {
    Xiaomi(R.string.notif_tip_xiaomi),
    Samsung(R.string.notif_tip_samsung),
    Huawei(R.string.notif_tip_huawei),
    Oppo(R.string.notif_tip_oppo),
    Other(null),
    ;

    companion object {
        /** [manufacturer] ve [brand] ([android.os.Build.MANUFACTURER], [android.os.Build.BRAND]). Saf; test edilir. */
        fun of(manufacturer: String, brand: String = ""): Maker {
            val name = "${manufacturer.lowercase()} ${brand.lowercase()}"
            return when {
                listOf("xiaomi", "redmi", "poco").any { it in name } -> Xiaomi
                "samsung" in name -> Samsung
                listOf("huawei", "honor").any { it in name } -> Huawei
                listOf("oppo", "realme", "oneplus", "vivo", "iqoo").any { it in name } -> Oppo
                else -> Other
            }
        }
    }
}

/** Bildirimlerin gelmesini engelleyebilecek, uygulamanın görebildiği durumlar. */
data class NotificationIssues(
    /** Kullanıcının açtığı türlerden birinin Android kanalı (ya da grubu) kapalı. */
    val blockedChannel: String? = null,
    /** Kullanıcı Android'de uygulamayı "Kısıtlı" yapmış: arka planda iş ve alarm çalışmaz. */
    val backgroundRestricted: Boolean = false,
) {
    val any: Boolean get() = blockedChannel != null || backgroundRestricted
}

/**
 * Bildirim güvenilirliği: engelleyen durumları algılar, ilgili Android ayarını açar ve deneme bildirimi gösterir.
 * Veri göndermez.
 */
object NotificationHealth {

    private const val TEST_ID = 7399

    /**
     * Kullanıcının açtığı bildirim türlerinden, seçtiği (sesli ya da sessiz) kanalı Android'de kapatılmış olanlar.
     * [importance] bir kanalın önemini verir (kanal yoksa null). Saf; test edilir.
     */
    fun blockedKinds(settings: NotificationSettings, importance: (String) -> Int?): List<NotificationKind> {
        return wantedKinds(settings).filter { kind ->
            val channel = if (NotificationRouting.wantsSound(kind, settings)) kind.soundChannel else kind.silentChannel
            importance(channel) == NotificationManager.IMPORTANCE_NONE
        }
    }

    /** Kullanıcının açtığı bildirim türleri (hangi kanalların önemli olduğu). Saf; test edilir. */
    fun wantedKinds(settings: NotificationSettings): List<NotificationKind> = buildList {
        if (settings.alerts) {
            add(NotificationKind.RedAlert)
            if (settings.alertThreshold != AlertThreshold.Red) add(NotificationKind.OrangeAlert)
            if (settings.alertThreshold == AlertThreshold.Yellow) add(NotificationKind.YellowAlert)
        }
        if (settings.morning) add(NotificationKind.Morning)
        if (settings.evening) add(NotificationKind.Evening)
        if (settings.airQuality) add(NotificationKind.AirQuality)
    }

    fun issues(context: Context, settings: NotificationSettings): NotificationIssues {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationChannels.ensure(context)
        val blocked = manager?.let { m ->
            blockedKinds(settings) { m.getNotificationChannel(it)?.importance }.firstOrNull()?.let { kind ->
                if (NotificationRouting.wantsSound(kind, settings)) kind.soundChannel else kind.silentChannel
            }
        }
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        // Hiçbir tür açık değilse arka plan kısıtı bir şeyi bozmaz: uyarı gösterilmez.
        return NotificationIssues(blockedChannel = blocked, backgroundRestricted = restricted && settings.anyOn)
    }

    /** Bir kanalın Android ayarları; ya da kanal yoksa uygulamanın bildirim ayarları. */
    fun channelSettingsIntent(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Arka plan ayarlarının olduğu yer. Xiaomi'de otomatik başlatma sayfası denenir (cihaza göre yoksa [startBackgroundSettings]
     * uygulama bilgi sayfasına düşer). Dışarıya bağlantı yoktur.
     */
    fun autostartIntent(): Intent = Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Üreticiye özel sayfa açılamazsa uygulamanın Android bilgi sayfasını açar. */
    fun startBackgroundSettings(context: Context, maker: Maker) {
        if (maker == Maker.Xiaomi && runCatching { context.startActivity(autostartIntent()) }.isSuccess) return
        context.startActivity(appDetailsIntent(context))
    }

    /** Uygulamanın Android bilgi sayfası: pil ve arka plan ayarlarına buradan gidilir. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android 13 ve üstünde bildirim izni verilmiş mi (eski sürümlerde izin yoktur). */
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Bildirimin gösterilmesini engelleyen durum; engel yoksa null. Sıra: izin, uygulamanın bildirimleri, kanal.
     * [channelImportance] kanalın önemidir (kanal yoksa null). Saf; test edilir.
     */
    fun blocker(permissionGranted: Boolean, appEnabled: Boolean, channelImportance: Int?): AttemptOutcome? = when {
        !permissionGranted -> AttemptOutcome.NoPermission
        !appEnabled -> AttemptOutcome.AppOff
        channelImportance == NotificationManager.IMPORTANCE_NONE -> AttemptOutcome.ChannelOff
        else -> null
    }

    /** [channelId] kanalından şu an bildirim gösterilebilir mi; gösterilemiyorsa nedeni. Kanal önceden kurulmuş olmalı. */
    fun blocker(context: Context, channelId: String): AttemptOutcome? = blocker(
        permissionGranted(context),
        NotificationManagerCompat.from(context).areNotificationsEnabled(),
        context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)?.importance,
    )

    /**
     * Bir bildirim denemesinin sonucu: engel varsa o; gönderilemediyse [AttemptOutcome.Failed]; gönderildi ama listede
     * görünmediyse [AttemptOutcome.NotVisible]; yoksa gönderildi. Sessizce başarılı sayılmaz. Saf; test edilir.
     */
    suspend fun outcome(blocker: AttemptOutcome?, oldData: Boolean, post: () -> Boolean, shown: suspend () -> Boolean): AttemptOutcome = when {
        blocker != null -> blocker
        !post() -> AttemptOutcome.Failed
        !shown() -> AttemptOutcome.NotVisible
        oldData -> AttemptOutcome.SentOldData
        else -> AttemptOutcome.Sent
    }

    /**
     * [id] numaralı bildirimin Android'in etkin bildirim listesinde göründüğünü doğrular. Bildirimi sistem kısa bir
     * gecikmeyle işler; bu yüzden birkaç kez bakılır.
     */
    suspend fun awaitShown(context: Context, id: Int, polls: Int = 10, gapMillis: Long = 150): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        repeat(polls) {
            if (runCatching { manager.activeNotifications.any { it.id == id } }.getOrDefault(false)) return true
            delay(gapMillis)
        }
        return false
    }

    /**
     * Gerçek özet bildirimiyle aynı kanala (ve aynı sesli/sessiz seçime) bir örnek bildirim gönderir ve sonucu
     * doğrular: izin, uygulama bildirimleri ve kanal denetlenir, gönderildikten sonra bildirimin gerçekten listede
     * olduğuna bakılır. Sonuç kayda yazılır; hiçbir şey bir yere gönderilmez.
     */
    /** Deneme bildiriminin kanalı: sabah özetinin şu an gideceği kanal. */
    fun testChannel(settings: NotificationSettings, time: LocalTime = LocalTime.now()): String =
        NotificationRouting.channel(NotificationKind.Morning, settings, time.hour * 60 + time.minute)

    suspend fun sendTest(context: Context, settings: NotificationSettings): AttemptOutcome {
        val result = runCatching { post(context, settings) }.getOrDefault(AttemptOutcome.Failed)
        NotificationLog(context).record(AttemptKind.Test, result)
        return result
    }

    private suspend fun post(context: Context, settings: NotificationSettings): AttemptOutcome {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return AttemptOutcome.Failed
        val localized = WidgetLanguage.context(context)
        NotificationChannels.ensure(context)
        val channel = testChannel(settings)
        val open = PendingIntent.getActivity(
            context, TEST_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = localized.getString(R.string.notif_test_notification_text)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_wl_partly_cloudy_day)
            .setSubText(localized.getString(R.string.channel_morning))
            .setContentTitle(localized.getString(R.string.notif_test_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return outcome(
            blocker = blocker(context, channel),
            oldData = false,
            post = { runCatching { manager.notify(TEST_ID, notification) }.isSuccess },
            shown = { awaitShown(context, TEST_ID) },
        )
    }
}
