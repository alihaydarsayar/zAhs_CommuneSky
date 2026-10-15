package com.alihaydarsayar.communesky.work

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import android.view.View
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.today.AndroidStrings
import com.alihaydarsayar.communesky.model.today.DailySummary
import com.alihaydarsayar.communesky.model.today.SummaryKind
import com.alihaydarsayar.communesky.model.today.SummaryText
import com.alihaydarsayar.communesky.model.today.WearText
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.home.nameRes
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.widget.displayName
import com.alihaydarsayar.communesky.widget.timeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Sabah özeti (bugün) ve akşam özeti (yarın): günde birer bildirim. İkisi de varsayılan kapalı. Metin telefondaki
 * önbellekten kurulur; bildirim için internete çıkılmaz (bkz. DailySummary).
 *
 * Zamanlama gevşek bir penceredir ([AlarmManager.setWindow], seçilen saatin ±15 dakikası): kesin alarm ve ek
 * izin yoktur, her özet için günde bir uyanma olur.
 */
object MorningSummary {

    /** Varsayılan saatler (gece yarısından itibaren dakika). */
    const val DEFAULT_MINUTES = 7 * 60 + 30
    const val DEFAULT_EVENING_MINUTES = 21 * 60

    /** Pencere, seçilen saatten bu kadar önce başlar ve bu kadar sonra biter. */
    const val SLACK_MINUTES = 15L

    /** Önbellek bundan eskiyse özet gönderilmez (yanlış bilgi vermektense susar). */
    private val MAX_AGE: Duration = Duration.ofHours(8)

    private const val PREFS = "morning_summary"
    private const val EXTRA_KIND = "summary_kind"

    private fun notificationId(kind: SummaryKind) = if (kind == SummaryKind.Morning) 7301 else 7302
    private fun lastDayKey(kind: SummaryKind) = if (kind == SummaryKind.Morning) "last_day" else "last_day_evening"

    /**
     * Bir sonraki pencerenin başlangıcı. Bugünün penceresi başladıysa (ya da geçtiyse) yarınki. Saf mantık;
     * test edilir.
     */
    fun nextWindowStart(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong()).minusMinutes(SLACK_MINUTES)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    /** Ayara göre iki özetin alarmlarını kurar ya da kaldırır. Uygulama açılınca, ayar değişince ve saatlik işte çağrılır. */
    fun schedule(context: Context, settings: NotificationSettings, now: LocalDateTime = LocalDateTime.now()) {
        schedule(context, SummaryKind.Morning, settings.morning, settings.morningMinutes, now)
        schedule(context, SummaryKind.Evening, settings.evening, settings.eveningMinutes, now)
    }

    private fun schedule(context: Context, kind: SummaryKind, enabled: Boolean, minutes: Int, now: LocalDateTime) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, kind.ordinal, Intent(context, MorningSummaryReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!enabled) {
            alarms.cancel(intent)
            return
        }
        val start = nextWindowStart(now, minutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms.setWindow(AlarmManager.RTC_WAKEUP, start, 2 * SLACK_MINUTES * 60_000, intent) }
    }

    /** Alarm çaldı: bugün gönderilmediyse özeti gönder, sonraki alarmı kur. */
    suspend fun onAlarm(context: Context, kind: SummaryKind = SummaryKind.Morning, force: Boolean = false) {
        val app = context.applicationContext
        val input = withTimeoutOrNull(5_000) { WidgetDataLoader(app).input.first() } ?: return
        val settings = input.settings
        schedule(app, settings.notifications)
        val enabled = if (kind == SummaryKind.Morning) settings.notifications.morning else settings.notifications.evening
        if (!(enabled || force) || !AlertNotifier.canNotify(app)) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (!force && prefs.getString(lastDayKey(kind), null) == today) return
        // Özetin yeri: Ev; yoksa bulunduğun yer; o da yoksa ilk kayıtlı yer.
        val snapshot = input.home?.snapshot ?: input.device ?: input.weather.values.firstOrNull() ?: return
        if (Duration.between(snapshot.fetchedAt, Instant.now()) > MAX_AGE) return
        // Bildirim, uygulamanın diliyle yazılır.
        val localized = WidgetLanguage.context(app)
        val clock = timeFormatter(localized)
        val unit = settings.temperatureUnit
        val text = DailySummary.of(
            kind, snapshot.forecast, snapshot.alerts, snapshot.forecast.localNow(), settings.wear.shift, AndroidStrings(localized),
            { unit.fromCelsius(it).roundToInt() }, { it.format(clock) },
        ) ?: return
        val place = snapshot.displayName(localized)
        val warning = text.warning?.let { alert ->
            listOf(
                localized.getString(levelName(alert.level)),
                alert.events.joinToString(", ") { localized.getString(it.nameRes) },
                AlertNotifier.period(localized, alert, snapshot.forecast.localNow().toLocalDate()),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannels.ensure(app)
        val time = LocalTime.now()
        val channel = NotificationRouting.channel(
            if (kind == SummaryKind.Morning) NotificationKind.Morning else NotificationKind.Evening, settings.notifications, time.hour * 60 + time.minute,
        )
        val label = localized.getString(if (kind == SummaryKind.Morning) R.string.channel_morning else R.string.channel_evening)
        val dayPage = open(app, snapshot.placeId, kind.ordinal * 10 + 1) { it.putExtra(MainActivity.OpenDayKey.name, text.date.toString()) }
        val wearPage = open(app, snapshot.placeId, kind.ordinal * 10 + 2) { it.putExtra(MainActivity.OpenTodayKey.name, true) }
        val builder = NotificationCompat.Builder(app, channel)
            .setSmallIcon(if (kind == SummaryKind.Morning) R.drawable.ic_wl_partly_cloudy_day else R.drawable.ic_wl_partly_cloudy_night)
            .setSubText("$label · $place")
            .setContentTitle(text.title)
            .setContentText(warning ?: text.body)
            .setLargeIcon(ContextCompat.getDrawable(app, WeatherCondition.fromCode(text.dayCode).iconRes(isNight = false))?.toBitmap(192, 192))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expanded(app, localized, text, warning, unit))
            .setContentIntent(if (kind == SummaryKind.Morning) wearPage else dayPage)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        if (kind == SummaryKind.Morning) {
            builder.addAction(0, localized.getString(R.string.summary_action_hours), dayPage)
            builder.addAction(0, localized.getString(R.string.summary_action_wear), wearPage)
        } else {
            builder.addAction(0, localized.getString(R.string.summary_action_tomorrow), dayPage)
        }
        runCatching { manager.notify(notificationId(kind), builder.build()) }
            .onSuccess { prefs.edit().putString(lastDayKey(kind), today).apply() }
    }

    private fun open(context: Context, placeId: Long, requestCode: Int, extra: (Intent) -> Intent): PendingIntent = PendingIntent.getActivity(
        context, 7300 + requestCode,
        extra(Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenPlaceKey.name, placeId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Açık görünüm: başlık, uyarı satırı, sabah/öğle/akşam dilimleri ve kısa satırlar. */
    private fun expanded(context: Context, localized: Context, text: SummaryText, warning: String?, unit: com.alihaydarsayar.communesky.model.TemperatureUnit): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_summary)
        views.setTextViewText(R.id.summary_title, text.title)
        if (warning != null && text.warning != null) {
            views.setViewVisibility(R.id.summary_warning, View.VISIBLE)
            views.setTextViewText(R.id.summary_warning, warning)
            views.setInt(R.id.summary_warning, "setBackgroundResource", warningBackground(text.warning.level))
        }
        val slots = listOf(
            listOf(R.id.slice_0, R.id.slice_0_label, R.id.slice_0_icon, R.id.slice_0_temperature, R.id.slice_0_chance),
            listOf(R.id.slice_1, R.id.slice_1_label, R.id.slice_1_icon, R.id.slice_1_temperature, R.id.slice_1_chance),
            listOf(R.id.slice_2, R.id.slice_2_label, R.id.slice_2_icon, R.id.slice_2_temperature, R.id.slice_2_chance),
        )
        if (text.slices.isEmpty()) views.setViewVisibility(R.id.summary_slices, View.GONE)
        slots.forEachIndexed { index, (root, label, icon, temperature, chance) ->
            val slice = text.slices.getOrNull(index)
            if (slice == null) {
                views.setViewVisibility(root, View.INVISIBLE)
                return@forEachIndexed
            }
            views.setTextViewText(label, localized.getString(WearText.label(slice.part)))
            views.setImageViewResource(icon, WeatherCondition.fromCode(slice.weatherCode).iconRes(isNight = !slice.isDay))
            views.setTextViewText(temperature, localized.getString(R.string.temperature_value, unit.fromCelsius(slice.temperature).roundToInt()))
            views.setTextViewText(chance, slice.chance?.let { localized.getString(R.string.humidity_value, it) } ?: "")
        }
        listOf(R.id.summary_line_0, R.id.summary_line_1, R.id.summary_line_2, R.id.summary_line_3).forEachIndexed { index, id ->
            val line = text.lines.getOrNull(index) ?: return@forEachIndexed
            views.setViewVisibility(id, View.VISIBLE)
            views.setTextViewText(id, line)
        }
        return views
    }

    private fun warningBackground(level: AlertLevel): Int = when (level) {
        AlertLevel.Yellow -> R.drawable.notification_warning_yellow
        AlertLevel.Orange -> R.drawable.notification_warning_orange
        AlertLevel.Red -> R.drawable.notification_warning_red
    }

    private fun levelName(level: AlertLevel): Int = when (level) {
        AlertLevel.Yellow -> R.string.alert_level_yellow
        AlertLevel.Orange -> R.string.alert_level_orange
        AlertLevel.Red -> R.string.alert_level_red
    }

    /** Alarmın hangi özet için olduğu. */
    fun kindOf(intent: Intent): SummaryKind =
        SummaryKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: SummaryKind.Morning
}

/** Sabah ve akşam özeti alarmlarını alır; işi bitince kapanır. */
class MorningSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                MorningSummary.onAlarm(context, MorningSummary.kindOf(intent))
            } finally {
                pending.finish()
            }
        }
    }
}
