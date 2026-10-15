package com.alihaydarsayar.communesky.work

import android.Manifest
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
import com.alihaydarsayar.communesky.model.AlertPlaces
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.widget.displayName
import com.alihaydarsayar.communesky.widget.timeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Meteorolojik uyarı bildirimleri. Ek uyanma yoktur: saatlik güncellemenin sonunda çağrılır.
 *
 * - Yeni uyarı: seviyesi ve yeri kullanıcının seçimine uyuyorsa bir bildirim (aynı uyarı için bir kez).
 * - Yükseltilen uyarı (sarı → turuncu → kırmızı): yeni bildirim, "kırmızıya yükseldi" diye.
 * - MGM uyarıyı kaldırınca (ya da süresi bitince, ya da kullanıcı o seviyeyi/yeri artık istemiyorsa) bildirim silinir.
 * - Aynı yerin uyarıları gruplanır.
 */
object AlertNotifier {

    private const val PREFS = "alert_notifications_v2"
    private const val KEY_POSTED = "posted"

    /** Bildirimi gösterilen bir uyarı: hangi yer, hangi uyarı (renginden bağımsız) ve hangi seviyeyle. */
    data class Posted(val placeId: Long, val key: String, val level: AlertLevel) {
        val notificationId: Int get() = "alert|$placeId|$key".hashCode()
        fun encode(): String = "$placeId|$key|${level.name}"

        companion object {
            fun decode(text: String): Posted? {
                val parts = text.split('|')
                if (parts.size != 3) return null
                return Posted(parts[0].toLongOrNull() ?: return null, parts[1], AlertLevel.entries.firstOrNull { it.name == parts[2] } ?: return null)
            }
        }
    }

    /** Bir yer ve o yerde geçerli uyarılar. */
    data class PlaceAlerts(val placeId: Long, val isCurrentLocation: Boolean, val alerts: List<WeatherAlert>)

    /** Gönderilecek bildirim; [raisedFrom] doluysa uyarı bu seviyeden yükseltildi. */
    data class Post(val placeId: Long, val alert: WeatherAlert, val raisedFrom: AlertLevel?)

    data class Plan(val post: List<Post>, val cancel: List<Posted>, val active: List<Posted>)

    /**
     * Aynı uyarının bütün renklerini birleştiren anahtar: MGM bir uyarıyı her renk için ayrı kimlikle verir
     * ("mgm-2026101439-orange"); sondaki renk atılır.
     */
    fun key(alert: WeatherAlert): String = alert.id.substringBeforeLast('-').ifEmpty { alert.id }

    /** Kullanıcının seçtiği yerler: bulunduğu yer, ev (yoksa bulunduğu yer) ya da hepsi. */
    fun wantedPlaces(places: List<PlaceAlerts>, choice: AlertPlaces, homeId: Long?): List<PlaceAlerts> = when (choice) {
        AlertPlaces.All -> places
        AlertPlaces.Current -> places.filter { it.isCurrentLocation }
        AlertPlaces.Home -> places.filter { it.placeId == homeId }.ifEmpty { places.filter { it.isCurrentLocation } }
    }

    /**
     * Ne gönderilecek, ne silinecek. Saf; test edilir.
     * @param posted şu an gösterilen bildirimler (önceki kontrolden).
     */
    fun plan(places: List<PlaceAlerts>, settings: NotificationSettings, homeId: Long?, posted: List<Posted>, now: LocalDateTime): Plan {
        if (!settings.alerts) return Plan(emptyList(), posted, emptyList())
        val wanted = wantedPlaces(places, settings.alertPlaces, homeId).flatMap { place ->
            place.alerts
                .filter { settings.alertThreshold.includes(it.level) && it.endsAt.isAfter(now) }
                // Aynı uyarının birden çok rengi aynı anda listedeyse en ağırı.
                .groupBy(::key).map { (_, same) -> same.maxBy { it.level } }
                .map { place.placeId to it }
        }
        val previous = posted.associateBy { it.placeId to it.key }
        val post = mutableListOf<Post>()
        val active = mutableListOf<Posted>()
        for ((placeId, alert) in wanted) {
            val before = previous[placeId to key(alert)]
            active += Posted(placeId, key(alert), alert.level)
            when {
                before == null -> {
                    // Aynı yerde daha hafif bir uyarı gösteriliyorsa ve bu uyarı onun yerini aldıysa (eskisi listeden
                    // çıktı) ya da aynı olay için aynı saatleri kapsıyorsa bu bir yükseltmedir.
                    val current = places.firstOrNull { it.placeId == placeId }?.alerts.orEmpty()
                    val lighter = posted.filter { it.placeId == placeId && it.level < alert.level }.firstOrNull { old ->
                        val still = current.firstOrNull { key(it) == old.key }
                        still == null || (still.events.any { it in alert.events } && still.beginsAt.isBefore(alert.endsAt) && alert.beginsAt.isBefore(still.endsAt))
                    }
                    post += Post(placeId, alert, lighter?.level)
                }
                alert.level > before.level -> post += Post(placeId, alert, before.level)
                // Aynı ya da daha hafif seviye: bildirim yeniden çalmaz.
                else -> Unit
            }
        }
        val activeKeys = active.map { it.placeId to it.key }.toSet()
        val cancel = posted.filter { (it.placeId to it.key) !in activeKeys }
        return Plan(post, cancel, active)
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun check(context: Context, weather: Collection<WeatherSnapshot>, settings: NotificationSettings, homeId: Long?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val posted = prefs.getStringSet(KEY_POSTED, emptySet()).orEmpty().mapNotNull(Posted::decode)
        val places = weather.map { PlaceAlerts(it.placeId, it.isCurrentLocation, it.alerts) }
        val allowed = canNotify(context)
        // İzin yokken gelen uyarılar "gösterildi" sayılmaz: izin verilince bildirilir.
        val plan = plan(places, settings.copy(alerts = settings.alerts && allowed), homeId, posted, LocalDateTime.now())
        plan.cancel.forEach { manager.cancel(it.notificationId) }
        if (plan.post.isNotEmpty()) NotificationChannels.ensure(context)
        val byId = weather.associateBy { it.placeId }
        for (item in plan.post) byId[item.placeId]?.let { notify(context, manager, it, item, settings) }
        // Grup özeti: bir yerin iki ve daha çok uyarısı gösteriliyorsa.
        for (placeId in (plan.active + plan.cancel).map { it.placeId }.distinct()) {
            val count = plan.active.count { it.placeId == placeId }
            val snapshot = byId[placeId]
            if (count >= 2 && snapshot != null) summary(context, manager, snapshot, plan.active.filter { it.placeId == placeId }, settings) else manager.cancel(summaryId(placeId))
        }
        prefs.edit().putStringSet(KEY_POSTED, plan.active.map { it.encode() }.toSet()).apply()
    }

    private fun group(placeId: Long) = "alerts_$placeId"
    private fun summaryId(placeId: Long) = "alert_summary|$placeId".hashCode()

    private fun openPlace(context: Context, placeId: Long, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenPlaceKey.name, placeId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, manager: NotificationManager, snapshot: WeatherSnapshot, item: Post, settings: NotificationSettings) {
        // Bildirim, öteki bildirimler gibi uygulamanın diliyle yazılır (telefonun diliyle değil).
        val localized = WidgetLanguage.context(context)
        val alert = item.alert
        val place = snapshot.displayName(localized)
        val events = alert.events.ifEmpty { listOf(AlertEvent.Other) }.joinToString(", ") { localized.getString(eventName(it)) }
        val period = period(localized, alert, snapshot.forecast.localNow().toLocalDate())
        val title = if (item.raisedFrom != null) {
            localized.getString(if (alert.level == AlertLevel.Red) R.string.alert_raised_red else R.string.alert_raised_orange, place)
        } else {
            localized.getString(R.string.alert_notification_title, localized.getString(levelName(alert.level)), place)
        }
        val line = listOfNotNull(
            item.raisedFrom?.let { localized.getString(R.string.alert_raised_steps, localized.getString(levelWord(it)), localized.getString(levelWord(alert.level))) },
            events,
            period,
        ).joinToString(" · ")
        val time = LocalTime.now()
        val channel = NotificationRouting.channel(NotificationKind.of(alert.level), settings, time.hour * 60 + time.minute)
        val posted = Posted(item.placeId, key(alert), alert.level)
        val open = openPlace(context, item.placeId, posted.notificationId)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_wl_thunderstorm)
            .setColor(levelColor(alert.level))
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(line, alert.text).filter { it.isNotBlank() }.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (alert.level == AlertLevel.Red) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(group(item.placeId))
            .setContentIntent(open)
            .addAction(0, localized.getString(R.string.alert_details), open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(posted.notificationId, notification) }
    }

    private fun summary(context: Context, manager: NotificationManager, snapshot: WeatherSnapshot, active: List<Posted>, settings: NotificationSettings) {
        val localized = WidgetLanguage.context(context)
        val worst = active.maxOf { it.level }
        val place = snapshot.displayName(localized)
        val time = LocalTime.now()
        val style = NotificationCompat.InboxStyle()
        active.sortedByDescending { it.level }.forEach { posted ->
            val alert = snapshot.alerts.firstOrNull { key(it) == posted.key } ?: return@forEach
            val events = alert.events.ifEmpty { listOf(AlertEvent.Other) }.joinToString(", ") { localized.getString(eventName(it)) }
            style.addLine(localized.getString(levelName(alert.level)) + " · " + events)
        }
        val notification = NotificationCompat.Builder(context, NotificationRouting.channel(NotificationKind.of(worst), settings, time.hour * 60 + time.minute))
            .setSmallIcon(R.drawable.ic_wl_thunderstorm)
            .setColor(levelColor(worst))
            .setContentTitle(localized.resources.getQuantityString(R.plurals.alert_group_title, active.size, place, active.size))
            .setStyle(style.setSummaryText(place))
            .setGroup(group(snapshot.placeId))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openPlace(context, snapshot.placeId, summaryId(snapshot.placeId)))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(summaryId(snapshot.placeId), notification) }
    }

    /** "Bugün 09:00–23:59", "Bugün 15:00 – yarın 06:00" (uygulamadaki uyarı şeridiyle aynı biçim). */
    fun period(context: Context, alert: WeatherAlert, today: LocalDate): String {
        val time = timeFormatter(context)
        val date = java.time.format.DateTimeFormatter.ofPattern("d MMM", context.resources.configuration.locales[0])
        fun day(at: LocalDateTime) = when (at.toLocalDate()) {
            today -> context.getString(R.string.today)
            today.plusDays(1) -> context.getString(R.string.tomorrow)
            else -> at.format(date)
        }
        val begin = alert.beginsAt
        val end = alert.endsAt
        return if (begin.toLocalDate() == end.toLocalDate()) {
            context.getString(R.string.alert_period_same_day, day(begin), begin.format(time), end.format(time))
        } else {
            context.getString(R.string.alert_period_two_days, day(begin), begin.format(time), day(end).lowercase(), end.format(time))
        }
    }

    private fun levelColor(level: AlertLevel): Int = when (level) {
        AlertLevel.Yellow -> 0xFFF2C230.toInt()
        AlertLevel.Orange -> 0xFFF08A24.toInt()
        AlertLevel.Red -> 0xFFE5484D.toInt()
    }

    private fun levelName(level: AlertLevel): Int = when (level) {
        AlertLevel.Yellow -> R.string.alert_level_yellow
        AlertLevel.Orange -> R.string.alert_level_orange
        AlertLevel.Red -> R.string.alert_level_red
    }

    private fun levelWord(level: AlertLevel): Int = when (level) {
        AlertLevel.Yellow -> R.string.notify_level_yellow
        AlertLevel.Orange -> R.string.notify_level_orange
        AlertLevel.Red -> R.string.notify_level_red
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
