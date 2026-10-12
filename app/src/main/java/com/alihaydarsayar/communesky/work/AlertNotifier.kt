package com.alihaydarsayar.communesky.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.widget.displayName

/**
 * Turuncu ve kırmızı meteorolojik uyarı ilk kez çıktığında bir bildirim gönderir (aynı uyarı için bir kez).
 * Ek uyanma yoktur: saatlik güncellemenin sonunda çağrılır. Gönderilen uyarıların kimlikleri telefonda tutulur.
 */
object AlertNotifier {

    private const val CHANNEL = "weather_alerts"
    private const val PREFS = "alert_notifications"
    private const val KEY_SENT = "sent"

    /** Bildirim gönderilecek uyarılar: turuncu ya da kırmızı ve daha önce bildirilmemiş. Saf mantık; test edilir. */
    fun pending(alerts: List<WeatherAlert>, alreadySent: Set<String>): List<WeatherAlert> =
        alerts.filter { it.level != AlertLevel.Yellow && it.id !in alreadySent }.distinctBy { it.id }

    /** Hatırlanacak kimlikler: yalnızca hâlâ geçerli olan uyarılar (liste sonsuza kadar büyümesin). */
    fun remembered(alreadySent: Set<String>, current: List<WeatherAlert>, newlySent: List<WeatherAlert>): Set<String> =
        alreadySent.intersect(current.map { it.id }.toSet()) + newlySent.map { it.id }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun check(context: Context, weather: Collection<WeatherSnapshot>, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
        val current = weather.flatMap { it.alerts }
        val toSend = if (enabled && canNotify(context)) pending(current, sent) else emptyList()
        for (snapshot in weather) {
            for (alert in snapshot.alerts.filter { it in toSend }) notify(context, snapshot, alert)
        }
        // Kapalıyken ya da izin yokken gelen uyarılar "gönderildi" sayılmaz; açılınca bildirilir.
        prefs.edit().putStringSet(KEY_SENT, remembered(sent, current, toSend)).apply()
    }

    private fun notify(context: Context, snapshot: WeatherSnapshot, alert: WeatherAlert) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Bildirim, öteki bildirimler gibi uygulamanın diliyle yazılır (telefonun diliyle değil).
        val localized = WidgetLanguage.context(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, localized.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val events = alert.events.ifEmpty { listOf(AlertEvent.Other) }.joinToString(", ") { localized.getString(eventName(it)) }
        val level = localized.getString(if (alert.level == AlertLevel.Red) R.string.alert_level_red else R.string.alert_level_orange)
        val open = PendingIntent.getActivity(
            context, alert.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenPlaceKey.name, snapshot.placeId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_wl_thunderstorm)
            .setContentTitle(localized.getString(R.string.alert_notification_title, level, snapshot.displayName(localized)))
            .setContentText(events)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(events, alert.text).filter { it.isNotBlank() }.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(alert.id.hashCode(), notification) }
    }

    private fun eventName(event: AlertEvent): Int = when (event) {
        AlertEvent.Wind -> R.string.alert_event_wind
        AlertEvent.Rain -> R.string.alert_event_rain
        AlertEvent.Thunderstorm -> R.string.alert_event_thunderstorm
        AlertEvent.Snow -> R.string.alert_event_snow
        AlertEvent.Ice -> R.string.alert_event_ice
        AlertEvent.Fog -> R.string.alert_event_fog
        AlertEvent.Heat -> R.string.alert_event_heat
        AlertEvent.Cold -> R.string.alert_event_cold
        AlertEvent.Flood -> R.string.alert_event_flood
        AlertEvent.Avalanche -> R.string.alert_event_avalanche
        AlertEvent.Dust -> R.string.alert_event_dust
        AlertEvent.Fire -> R.string.alert_event_fire
        AlertEvent.Coastal -> R.string.alert_event_coastal
        AlertEvent.Other -> R.string.alert_event_other
    }
}
