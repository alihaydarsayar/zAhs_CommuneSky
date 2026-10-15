package com.alihaydarsayar.communesky.work

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alihaydarsayar.communesky.MainActivity
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.lifestyle.isWet
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.TurkishSuffix
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.today.AndroidStrings
import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.Strings
import com.alihaydarsayar.communesky.model.today.WearText
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
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Sabah özeti: günde bir bildirim, ör. "Bugün 9–17°, 15:00'te yağmur. Kazak ya da ince mont giy; şemsiye al."
 * Varsayılan kapalı. Metin, telefondaki önbellekten kurulur; bildirim için internete çıkılmaz.
 *
 * Zamanlama gevşek bir penceredir ([AlarmManager.setWindow], seçilen saatin ±15 dakikası): kesin alarm ve ek
 * izin yoktur, günde bir uyanma olur. Android bu pencerede başka işlerle birleştirebilir.
 */
object MorningSummary {

    /** Varsayılan saat: 07:30 (gece yarısından itibaren dakika). */
    const val DEFAULT_MINUTES = 7 * 60 + 30

    /** Pencere, seçilen saatten bu kadar önce başlar ve bu kadar sonra biter. */
    const val SLACK_MINUTES = 15L

    /** Önbellek bundan eskiyse özet gönderilmez (yanlış bilgi vermektense susar). */
    private val MAX_AGE: Duration = Duration.ofHours(8)

    /** Yağış sayılan ihtimal ve "kesin gibi" sayılan ihtimal. */
    private const val RAIN_CHANCE = 30
    private const val RAIN_LIKELY = 50

    private const val NOTIFICATION_ID = 7301
    private const val PREFS = "morning_summary"
    private const val KEY_LAST_DAY = "last_day"

    /**
     * Bir sonraki pencerenin başlangıcı. Bugünün penceresi başladıysa (ya da geçtiyse) yarınki. Saf mantık;
     * test edilir.
     */
    fun nextWindowStart(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong()).minusMinutes(SLACK_MINUTES)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    /** Bugün [now] saatinden sonra yağışın başladığı ilk saat; yoksa null. */
    fun firstRain(forecast: Forecast, now: LocalDateTime): HourlyForecast? {
        val from = now.withMinute(0).withSecond(0).withNano(0)
        return forecast.hourly.firstOrNull { hour ->
            hour.time.toLocalDate() == now.toLocalDate() && !hour.time.isBefore(from) &&
                (hour.showsPrecipitation || (hour.isWet() && hour.precipitationProbability >= RAIN_CHANCE) || hour.precipitationProbability >= RAIN_LIKELY)
        }
    }

    /** "15:00'te" (Türkçe) ya da "at 15:00". */
    fun at(strings: Strings, time: String): String =
        if (strings.locale.language == "tr") TurkishSuffix.locativeTime(time) else strings.get(R.string.widget_at_time, time)

    /**
     * Bildirimin metni. [degrees] sıcaklığı kullanıcının biriminde yuvarlar; [clock] saati kullanıcının saat
     * biçimiyle yazar. Bugünün günlük verisi yoksa null. Saf mantık; test edilir.
     */
    fun text(
        forecast: Forecast,
        now: LocalDateTime,
        wearShift: Double,
        strings: Strings,
        degrees: (Double) -> Int,
        clock: (LocalDateTime) -> String,
    ): String? {
        val today = forecast.today(now) ?: return null
        var head = strings.get(R.string.summary_range, degrees(today.minTemperature), degrees(today.maxTemperature))
        firstRain(forecast, now)?.let { hour ->
            val kind = if (hour.condition == WeatherCondition.Snow) R.string.summary_snow else R.string.summary_rain
            head = strings.get(R.string.summary_with, head, strings.get(kind, at(strings, clock(hour.time))))
        }
        // Sabah özetinde hep bugünün önerisi verilir (gece geç saatte açılsa bile).
        val wear = Clothing.advise(Clothing.parts(forecast, now.toLocalDate()), wearShift, fromHour = now.hour)
        val sentence = wear?.let { WearText.sentence(it, strings, degrees) }
        return if (sentence == null) strings.get(R.string.wear_plain, head) else strings.get(R.string.summary_sentences, head, sentence)
    }

    /**
     * Ayara göre alarmı kurar ya da kaldırır. Aynı alarm güncellenir; birden fazla kurulmaz. Uygulama açılınca,
     * ayar değişince ve saatlik güncellemenin sonunda çağrılır (telefon yeniden başlatılınca alarm silinir;
     * saatlik iş onu geri kurar, açılış izni gerekmez).
     */
    fun schedule(context: Context, enabled: Boolean, minutes: Int, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, MorningSummaryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!enabled) {
            alarms.cancel(intent)
            return
        }
        val start = nextWindowStart(now, minutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { alarms.setWindow(AlarmManager.RTC_WAKEUP, start, 2 * SLACK_MINUTES * 60_000, intent) }
    }

    /** Alarm çaldı: bugün gönderilmediyse özeti gönder, yarınki alarmı kur. */
    suspend fun onAlarm(context: Context) {
        val app = context.applicationContext
        val input = withTimeoutOrNull(5_000) { WidgetDataLoader(app).input.first() } ?: return
        val settings = input.settings
        schedule(app, settings.notifications.morning, settings.notifications.morningMinutes)
        if (!settings.notifications.morning || !AlertNotifier.canNotify(app)) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_DAY, null) == today) return
        // Özetin yeri: Ev; yoksa bulunduğun yer; o da yoksa ilk kayıtlı yer.
        val snapshot = input.home?.snapshot ?: input.device ?: input.weather.values.firstOrNull() ?: return
        if (Duration.between(snapshot.fetchedAt, Instant.now()) > MAX_AGE) return
        // Bildirim, uygulamanın diliyle yazılır.
        val localized = WidgetLanguage.context(app)
        val clock = timeFormatter(localized)
        val unit = settings.temperatureUnit
        val text = text(
            snapshot.forecast, snapshot.forecast.localNow(), settings.wear.shift, AndroidStrings(localized),
            { unit.fromCelsius(it).roundToInt() }, { it.format(clock) },
        ) ?: return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannels.ensure(app)
        val clockNow = java.time.LocalTime.now()
        val channel = NotificationRouting.channel(NotificationKind.Morning, settings.notifications, clockNow.hour * 60 + clockNow.minute)
        val open = PendingIntent.getActivity(
            app, NOTIFICATION_ID,
            Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.OpenPlaceKey.name, snapshot.placeId)
                .putExtra(MainActivity.OpenTodayKey.name, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_wl_partly_cloudy_day)
            .setContentTitle(snapshot.displayName(localized))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onSuccess { prefs.edit().putString(KEY_LAST_DAY, today).apply() }
    }
}

/** Sabah özeti alarmını alır; işi bitince kapanır. */
class MorningSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                MorningSummary.onAlarm(context)
            } finally {
                pending.finish()
            }
        }
    }
}
