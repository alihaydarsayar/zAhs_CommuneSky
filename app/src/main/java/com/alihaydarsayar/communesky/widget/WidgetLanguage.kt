package com.alihaydarsayar.communesky.widget

import android.app.AlarmManager
import android.app.LocaleManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Widget'ların dili: uygulamanın Ayarlar'da seçilen dili. Widget'ı uygulamanın arka plan süreci
 * çizer ve o süreç telefonun diliyle çalışır; uygulama Türkçe, telefon İngilizce ise widget'taki
 * yazılar İngilizce çıkıyordu. Burada seçili dille bir bağlam kurulur; bütün yazılar, sayılar ve
 * tarih biçimleri o bağlamla yapılır.
 */
object WidgetLanguage {

    private const val PREFS = "widget_language"
    private const val KEY = "tag"

    /** Uygulamanın seçili dili ("tr", "en", "fr"); "telefonun dili" seçiliyse boş. */
    fun tag(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY)) return prefs.getString(KEY, "").orEmpty()
        // Bu sürüme güncelleyip uygulamayı henüz açmamış kullanıcı: Android 13+ seçimi sistemde tutar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
                ?.takeIf { !it.isEmpty }?.let { return it[0].language }
        }
        return ""
    }

    /** Ayarlar'da dil değişince ve uygulama açılınca çağrılır: widget'lar süreç kapalıyken de doğru dili bilsin. */
    fun remember(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Seçili dille kurulmuş bağlam; "telefonun dili" seçiliyse [base] olduğu gibi döner. */
    fun context(base: Context): Context {
        val tag = tag(base)
        if (tag.isEmpty()) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    /**
     * Telefonun kendi dili. Android 13+ uygulamaya özel dil seçilince sürecin bütün kaynaklarını
     * (Resources.getSystem dahil) o dile çevirir; gerçek telefon dili LocaleManager'dan sorulur.
     */
    fun systemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { context.getSystemService(LocaleManager::class.java)?.systemLocales }.getOrNull()
                ?.takeIf { !it.isEmpty }?.let { return it[0] }
        }
        return Resources.getSystem().configuration.locales[0]
    }

    private const val KEY_CLOCK = "clock_format"

    /**
     * Ayarlar'daki "Saat biçimi". Kullanıcı seçmediyse varsayılan dile göredir ([defaultClockFormat]).
     * Widget'lar uygulama kapalıyken de okuyabilsin diye burada (küçük bir tercih dosyasında) saklanır.
     */
    fun clockFormat(context: Context): ClockFormat {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CLOCK, null)
        return ClockFormat.entries.firstOrNull { it.name == stored } ?: defaultClockFormat(context(context).resources.configuration.locales[0].language)
    }

    fun setClockFormat(context: Context, format: ClockFormat) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CLOCK, format.name).apply()
    }

    /** Varsayılan saat biçimi: Türkçede 24 saat ("9öö", "12ös" yazılmasın); diğer dillerde telefonla aynı. Saf; test edilir. */
    fun defaultClockFormat(language: String): ClockFormat = if (language == "tr" || language == "fr") ClockFormat.H24 else ClockFormat.System

    /**
     * Saat 24 saatlik mi yazılacak? Ayarlar'daki "Saat biçimi"ne göre; "Telefonla aynı" seçiliyse telefonun
     * ayarı ve dili. Widget'taki saat bileşeni, widget'ın kendi yazdığı saatler ("21:00'de yağmur"), önizleme
     * ve uygulamanın ekranları aynı kuralı kullanır.
     */
    fun is24Hour(context: Context): Boolean = when (clockFormat(context)) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.System -> phoneIs24Hour(context)
    }

    /** Telefonun kendi kuralı: sistem ayarı; "dilin varsayılanı" seçiliyse telefonun dili. */
    fun phoneIs24Hour(context: Context): Boolean =
        when (runCatching { android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24) }.getOrNull()) {
            "24" -> true
            "12" -> false
            else -> localeUses24Hour(systemLocale(context))
        }

    /** Dilin kendi alışkanlığı (telefonda "dilin varsayılanı" seçiliyken). */
    fun localeUses24Hour(locale: Locale): Boolean =
        android.text.format.DateFormat.getBestDateTimePattern(locale, "jm").contains('H')

    /**
     * Çizim dili telefonun dilinden farklı mı? Farklıysa tarih, Android'in saat bileşeniyle (TextClock)
     * yazılamaz: onu ana ekran uygulaması kendi diliyle yazar. O zaman tarih normal metin olarak yazılır
     * ve gece yarısı bir kez yenilenir (bkz. [WidgetDateReceiver]). Saf mantık; test edilir.
     */
    fun differs(drawing: Locale, system: Locale): Boolean = drawing.language != system.language

    fun differsFromSystem(context: Context): Boolean = differs(context.resources.configuration.locales[0], systemLocale(context))
}

/** Ayarlar › Saat biçimi. */
enum class ClockFormat { System, H24, H12 }

/**
 * Tarih metin olarak yazıldığında (uygulama dili telefonunkinden farklıyken) gün değişince widget'ları
 * bir kez yeniden çizer. Kesin alarm değildir ve telefonu uyandırmaz: gece yarısından sonra telefon
 * ilk uyandığında çalışır. Saat ya da saat dilimi elle değişince de çalışır.
 */
class WidgetDateReceiver : BroadcastReceiver() {

    // Alıcı dışa kapalıdır (exported=false): yalnızca sistem ve uygulamanın kendi alarmı ulaşır; yaptığı tek iş
    // widget'ları yeniden çizmektir. Bu yüzden gelen eylemin adı ayrıca denetlenmez.
    @android.annotation.SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Yeniden çizim, tarih hâlâ metin olarak yazılıyorsa bir sonraki gece yarısını da planlar.
                WeatherWidgetUpdater.updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val WINDOW_MS = 10 * 60 * 1000L

        /** Bir sonraki gece yarısı için tek bir yenileme planlar; aynı istek yeniden planlanırsa öncekinin yerini alır. */
        fun schedule(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(context, WidgetDateReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // RTC (uyandırmayan) ve pencereli: sistem başka işlerle birleştirebilir.
            runCatching { alarms.setWindow(AlarmManager.RTC, midnight + 2_000L, WINDOW_MS, intent) }
        }
    }
}
