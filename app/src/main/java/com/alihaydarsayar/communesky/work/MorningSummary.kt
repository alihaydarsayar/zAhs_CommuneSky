package com.alihaydarsayar.communesky.work

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
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
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.today.WearText
import com.alihaydarsayar.communesky.ui.common.iconRes
import com.alihaydarsayar.communesky.ui.home.nameRes
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetEntryPoint
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetLanguage
import com.alihaydarsayar.communesky.widget.displayName
import com.alihaydarsayar.communesky.widget.timeFormatter
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Sabah özeti (bugün) ve akşam özeti (yarın): günde birer bildirim. İkisi de varsayılan kapalı. Metin telefondaki
 * önbellekten kurulur; bildirim için ek bir sunucu ya da veri gönderimi yoktur (bkz. DailySummary).
 *
 * Katmanlı zamanlama (bkz. SummarySchedule):
 * 1. AlarmManager, Doze'da da çalışan alarm: tam zamanlı alarm izni varsa (Android 11 ve altında her zaman)
 *    setExactAndAllowWhileIdle, yoksa setAndAllowWhileIdle; birkaç dakika sapma olabilir. Alarm uygulama
 *    açılınca, ayar değişince, telefon yeniden başlayınca, uygulama güncellenince ve saat ya da saat dilimi
 *    değişince yeniden kurulur ([SummaryRescheduleReceiver]).
 * 2. Yedek: saatlik arka plan işi, bugünün özeti gönderilmediyse ve seçilen saatten sonra en çok 3 saat geçtiyse
 *    gönderir ([catchUp]); pencere geçtiyse o günün özetini atlar.
 * Günde bir kez gönderim, "son gönderilen gün" kaydıyla garanti edilir (cihazda, bildirim tekrarını önleyen kısa kayıt).
 */
object MorningSummary {

    /** Varsayılan saatler (gece yarısından itibaren dakika). */
    const val DEFAULT_MINUTES = 7 * 60 + 30
    const val DEFAULT_EVENING_MINUTES = 21 * 60

    private const val PREFS = "morning_summary"
    private const val EXTRA_KIND = "summary_kind"
    private const val REFRESH_TIMEOUT_MS = 20_000L

    /** Anlık hava (yağıyor mu) yalnız bu kadar tazeyse sabah özetinin şemsiye önerisine girer. */
    private val WET_NOW_MAX_AGE: Duration = Duration.ofHours(1)

    /** Alarm ile yedek yol aynı anda çalışırsa özet iki kez gitmesin. */
    private val lock = Mutex()

    private fun notificationId(kind: SummaryKind) = if (kind == SummaryKind.Morning) 7301 else 7302
    private fun lastDayKey(kind: SummaryKind) = if (kind == SummaryKind.Morning) "last_day" else "last_day_evening"

    /** Özetin en son gönderildiği günün tarihi (cihazın tarihi); hiç gönderilmediyse null. */
    private fun sentOn(context: Context, kind: SummaryKind): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(lastDayKey(kind), null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun enabled(settings: NotificationSettings, kind: SummaryKind) =
        if (kind == SummaryKind.Morning) settings.morning else settings.evening

    private fun minutes(settings: NotificationSettings, kind: SummaryKind) =
        if (kind == SummaryKind.Morning) settings.morningMinutes else settings.eveningMinutes

    /** Ayara göre iki özetin alarmlarını kurar ya da kaldırır. Uygulama açılınca, ayar değişince ve saatlik işte çağrılır. */
    fun schedule(context: Context, settings: NotificationSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        for (kind in SummaryKind.entries) schedule(context, kind, enabled(settings, kind), minutes(settings, kind), now)
    }

    private fun schedule(context: Context, kind: SummaryKind, enabled: Boolean, minutes: Int, now: ZonedDateTime) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, kind.ordinal, Intent(context, MorningSummaryReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!enabled) {
            alarms.cancel(intent)
            return
        }
        val at = SummarySchedule.nextTrigger(now, minutes, sentOn(context, kind)).toInstant().toEpochMilli()
        setAlarm(alarms, at, intent)
    }

    /**
     * Doze'da da çalışan alarm. Tam zamanlı alarm için ek izin istenmez: Android 12+ ta izin verilmemişse (varsayılan)
     * zamanı biraz oynayabilen setAndAllowWhileIdle kullanılır; bildirim akışı bozulmaz.
     */
    // Tam zamanlı yol yalnız Android 11 ve altında ya da canScheduleExactAlarms() true iken çalışır; izin bildirilmez.
    @android.annotation.SuppressLint("MissingPermission")
    private fun setAlarm(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        runCatching {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }.onFailure {
            // İzin sonradan geri alınmış olabilir: izinsiz yola düşülür.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
        }
    }

    private suspend fun load(context: Context): WidgetInput? = withTimeoutOrNull(5_000) { WidgetDataLoader(context).input.first() }

    /** Özetin yeri: Ev; yoksa bulunduğun yer; o da yoksa ilk kayıtlı yer. */
    private fun pick(input: WidgetInput): WeatherSnapshot? = input.home?.snapshot ?: input.device ?: input.weather.values.firstOrNull()

    /**
     * Telefon yeniden başladı, uygulama güncellendi ya da saat/saat dilimi değişti: alarmlar yeniden kurulur ve
     * kaçan bir özet varsa (pencere içindeyse) gönderilir.
     */
    suspend fun restore(context: Context) {
        val app = context.applicationContext
        load(app)?.let { schedule(app, it.settings.notifications) }
        catchUp(app)
    }

    /** Alarm çaldı: bugün gönderilmediyse özeti gönder, sonraki alarmı kur. Önbellek eskiyse önce yenilemeyi dener. */
    suspend fun onAlarm(context: Context, kind: SummaryKind = SummaryKind.Morning, force: Boolean = false) = lock.withLock {
        val app = context.applicationContext
        var input = load(app) ?: return@withLock
        schedule(app, input.settings.notifications)
        val snapshot = pick(input)
        if (snapshot != null && Duration.between(snapshot.fetchedAt, Instant.now()) > SummarySchedule.FRESH) {
            // Saatlik iş çalışmamış (süreç öldürülmüş, pil tasarrufu): özet için tahmin burada yenilenir.
            val refreshed = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                runCatching { EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java).weatherUpdater().refreshForSummary() }.isSuccess
            }
            if (refreshed == true) input = load(app) ?: input
        }
        deliver(app, input, kind, force)
    }

    /**
     * Yedek yol (saatlik işin sonunda ve yeniden başlatma olaylarında): bugünün özeti gönderilmediyse ve seçilen
     * saatten sonra en çok [SummarySchedule.CATCH_UP] geçtiyse gönderir.
     */
    suspend fun catchUp(context: Context) = lock.withLock {
        val app = context.applicationContext
        val input = load(app) ?: return@withLock
        val now = ZonedDateTime.now()
        for (kind in SummaryKind.entries) {
            val settings = input.settings.notifications
            if (enabled(settings, kind) && SummarySchedule.shouldCatchUp(now, minutes(settings, kind), sentOn(app, kind))) {
                deliver(app, input, kind, force = false)
            }
        }
    }

    private fun deliver(app: Context, input: WidgetInput, kind: SummaryKind, force: Boolean) {
        val settings = input.settings
        if (!AlertNotifier.canNotify(app)) return
        val today = LocalDate.now()
        val snapshot = pick(input)
        val age = snapshot?.let { Duration.between(it.fetchedAt, Instant.now()) }
        val verdict = SummarySchedule.verdict(enabled(settings.notifications, kind), sentOn(app, kind), today, age, force)
        if (verdict == SummarySchedule.Verdict.Skip || snapshot == null) return
        // Bildirim, uygulamanın diliyle yazılır.
        val localized = WidgetLanguage.context(app)
        val clock = timeFormatter(localized)
        val unit = settings.temperatureUnit
        // Şu an yağıyorsa (taze önbellekte) sabah özetinin önerisine de şemsiye girer; ana ekrandaki öneriyle aynı kural.
        val wetNow = kind == SummaryKind.Morning && age != null && age <= WET_NOW_MAX_AGE && snapshot.forecast.current.condition.let {
            it.isWet || it == WeatherCondition.Snow
        }
        var text = DailySummary.of(
            kind, snapshot.forecast, snapshot.alerts, snapshot.forecast.localNow(), settings.wear.shift, AndroidStrings(localized),
            { unit.fromCelsius(it).roundToInt() }, { it.format(clock) }, wetNow,
        ) ?: return
        if (verdict == SummarySchedule.Verdict.SendCached) {
            // Eski veriden kurulan özet bunu söyler; yanlış bir özet gibi görünmesin.
            val fetched = snapshot.fetchedAt.atOffset(ZoneOffset.ofTotalSeconds(snapshot.forecast.utcOffsetSeconds)).toLocalDateTime()
            text = text.copy(lines = text.lines.take(3) + localized.getString(R.string.summary_cached_at, fetched.format(clock)))
        }
        notify(app, input, snapshot, kind, text, today)
    }

    private fun notify(app: Context, input: WidgetInput, snapshot: WeatherSnapshot, kind: SummaryKind, text: SummaryText, today: LocalDate) {
        val settings = input.settings
        val localized = WidgetLanguage.context(app)
        val unit = settings.temperatureUnit
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            // Kapalı görünümde öneri; uyarı açık görünümde kendi renkli satırında.
            .setContentText(text.body)
            // Günün simgesi kapalı görünümün içinde: büyük simge (setLargeIcon) açık görünümü daraltıyordu.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed(app, text))
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
            .onSuccess { prefs.edit().putString(lastDayKey(kind), today.toString()).apply() }
    }

    private fun open(context: Context, placeId: Long, requestCode: Int, extra: (Intent) -> Intent): PendingIntent = PendingIntent.getActivity(
        context, 7300 + requestCode,
        extra(Intent(context, MainActivity::class.java).putExtra(MainActivity.OpenPlaceKey.name, placeId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Kapalı görünüm: başlık, tek satır öneri ve günün simgesi. */
    private fun collapsed(context: Context, text: SummaryText): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_summary_small).apply {
            setTextViewText(R.id.summary_small_title, text.title)
            setTextViewText(R.id.summary_small_text, text.body)
            setImageViewResource(R.id.summary_small_icon, WeatherCondition.fromCode(text.dayCode).iconRes(isNight = false))
        }

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

/**
 * Telefon yeniden başlayınca, uygulama güncellenince ve saat ya da saat dilimi değişince özet alarmlarını yeniden
 * kurar (Android bunları siler ya da kaydırır) ve kaçan özet varsa gönderir.
 */
class SummaryRescheduleReceiver : BroadcastReceiver() {
    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                MorningSummary.restore(context)
            } finally {
                pending.finish()
            }
        }
    }
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
