package com.alihaydarsayar.communesky.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AqiLevel
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.detail.advice
import com.alihaydarsayar.communesky.ui.detail.label
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.widget.displayName
import java.time.LocalDate

/**
 * Hava kalitesi "kötü" ya da daha kötü olunca isteğe bağlı bildirim (varsayılan kapalı). Ek uyanma yoktur:
 * saatlik güncellemenin sonunda çağrılır. Aynı yer için günde en çok bir bildirim gönderilir.
 */
object AirQualityNotifier {

    private const val CHANNEL = "air_quality"
    private const val PREFS = "air_quality_notifications"
    private const val KEY_DAY = "day"
    private const val KEY_SENT = "sent"

    /**
     * Bildirim gönderilecek yerler: şu an havası kötü olan ve bugün daha önce bildirilmemiş yerler. Saf mantık;
     * test edilir.
     */
    fun pending(levels: Map<Long, AqiLevel?>, sentToday: Set<Long>): List<Long> =
        levels.filter { (id, level) -> level?.isPoor == true && id !in sentToday }.keys.sorted()

    fun check(context: Context, weather: Collection<WeatherSnapshot>, enabled: Boolean, today: LocalDate = LocalDate.now()) {
        if (!enabled || !AlertNotifier.canNotify(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameDay = prefs.getString(KEY_DAY, null) == today.toString()
        val sent = if (sameDay) prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet() else emptySet()
        val levels = weather.associate { snapshot -> snapshot.placeId to snapshot.airQuality?.at(snapshot.forecast.localNow())?.level }
        val toSend = pending(levels, sent)
        for (snapshot in weather.filter { it.placeId in toSend }) notify(context, snapshot, levels.getValue(snapshot.placeId)!!)
        prefs.edit().putString(KEY_DAY, today.toString()).putStringSet(KEY_SENT, (sent + toSend).map { it.toString() }.toSet()).apply()
    }

    private fun notify(context: Context, snapshot: WeatherSnapshot, level: AqiLevel) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = WidgetLanguage.context(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, localized.getString(R.string.air_quality), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val id = 7400 + (snapshot.placeId % 1000).toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.OpenPlaceKey.name, snapshot.placeId)
                .putExtra(MainActivity.OpenAirQualityKey.name, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = localized.getString(level.advice)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_wl_fog)
            .setContentTitle(localized.getString(R.string.aqi_notification_title, snapshot.displayName(localized), localized.getString(level.label)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }
}
