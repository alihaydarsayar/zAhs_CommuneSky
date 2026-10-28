package com.alihaydarsayar.communesky.work

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertThreshold
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.widget.WidgetLanguage
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
                listOf("oppo", "realme", "oneplus").any { it in name } -> Oppo
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
 * Bildirim izni ayrı bir uyarıyla (bkz. NotificationsScreen) ele alınır. Veri göndermez.
 */
object NotificationHealth {

    private const val TEST_ID = 7399

    /**
     * Kullanıcının açtığı bildirim türlerinden, seçtiği (sesli ya da sessiz) kanalı Android'de kapatılmış olanlar.
     * [importance] bir kanalın önemini verir (kanal yoksa null). Saf; test edilir.
     */
    fun blockedKinds(settings: NotificationSettings, importance: (String) -> Int?): List<NotificationKind> {
        val wanted = buildList {
            if (settings.alerts) {
                add(NotificationKind.RedAlert)
                if (settings.alertThreshold != AlertThreshold.Red) add(NotificationKind.OrangeAlert)
                if (settings.alertThreshold == AlertThreshold.Yellow) add(NotificationKind.YellowAlert)
            }
            if (settings.morning) add(NotificationKind.Morning)
            if (settings.evening) add(NotificationKind.Evening)
            if (settings.airQuality) add(NotificationKind.AirQuality)
        }
        return wanted.filter { kind ->
            val channel = if (NotificationRouting.wantsSound(kind, settings)) kind.soundChannel else kind.silentChannel
            importance(channel) == NotificationManager.IMPORTANCE_NONE
        }
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

    /** Uygulamanın Android bilgi sayfası: pil ve arka plan ayarlarına buradan gidilir. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Gerçek özet bildirimiyle aynı kanala (ve aynı sesli/sessiz seçime) bir örnek bildirim gönderir; kanal ve izin
     * çalışıyor mu görülsün. Hiçbir şey kaydedilmez ya da gönderilmez. İzin yoksa false döner.
     */
    fun sendTest(context: Context, settings: NotificationSettings): Boolean {
        if (!AlertNotifier.canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val localized = WidgetLanguage.context(context)
        NotificationChannels.ensure(context)
        val time = LocalTime.now()
        val channel = NotificationRouting.channel(NotificationKind.Morning, settings, time.hour * 60 + time.minute)
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
        return runCatching { manager.notify(TEST_ID, notification) }.isSuccess
    }
}
