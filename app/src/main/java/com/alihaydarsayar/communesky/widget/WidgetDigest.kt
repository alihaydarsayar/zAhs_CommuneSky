package com.alihaydarsayar.communesky.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.util.TimeZone

/**
 * Son çizilen widget içeriğinin özeti. Widget'ların gösterdiği her şey (hava verisi, yerler, birimler,
 * widget ayarları, dil, yazı boyutu, duvar kâğıdı tonu) tek bir özete indirgenir; özet bir önceki
 * çizimle aynıysa widget'lar yeniden çizilmez (bkz. [WeatherWidgetUpdater.updateAll]).
 *
 * Özet diske yazılır: saatlik arka plan işi her seferinde yeni bir süreçte çalışabilir.
 */
internal object WidgetDigest {

    private const val PREFS = "widget_draw_state"
    private const val KEY = "digest"

    /**
     * Zamana bağlı yazılar ("Yağmur 21:00'de başlıyor", gün batımına kalan süre, saatlik şerit) veri
     * değişmese de eskir; özet bu yüzden çeyrek saatlik dilimi de içerir. Aynı dilimdeki tekrar
     * istekler atlanır, sonraki dilimde gelen ilk istek yeniden çizer. Bunun için ayrıca uyanılmaz.
     */
    const val TIME_BUCKET_MINUTES = 15L

    /** Ana ekrandaki bütün Commune Sky widget'larının kimlikleri. */
    fun widgetIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { manager.getAppWidgetIds(ComponentName(context, it.receiverClass)).toList() }.sorted()
    }

    suspend fun current(context: Context, widgetIds: List<Int>): String = compute(
        input = WidgetDataLoader(context).input.first(),
        configs = WidgetConfigStore.get(context).dump(),
        environment = environment(context, widgetIds),
        now = Instant.now(),
    )

    /**
     * Saf hesap; test edilir. Verinin alındığı an ([com.alihaydarsayar.communesky.model.WeatherSnapshot.fetchedAt])
     * widget'ta gösterilmediği için özete girmez: aynı veri yeniden indirilince çizim atlanır.
     * Metin hâli kullanılır (hashCode değil): enum'ların hashCode'u süreçten sürece değişir.
     */
    fun compute(input: WidgetInput, configs: String, environment: String, now: Instant): String {
        val weather = input.weather.toSortedMap().map { (id, snapshot) -> id to snapshot.copy(fetchedAt = Instant.EPOCH) }
        val text = buildString {
            append(input.settings).append('\n')
            append(input.places).append('\n')
            append(weather).append('\n')
            append(configs).append('\n')
            append(environment).append('\n')
            append(now.epochSecond / 60 / TIME_BUCKET_MINUTES)
        }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Veri dışında çizimi etkileyen her şey: uygulama sürümü, dil, yazı ölçeği, tema, saat biçimi, duvar kâğıdı. */
    private fun environment(context: Context, widgetIds: List<Int>): String {
        val configuration = context.resources.configuration
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val systemColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // "Duvar kâğıdı" teması Material You renklerini kullanır.
            listOf(android.R.color.system_accent1_100, android.R.color.system_accent1_600, android.R.color.system_neutral1_900, android.R.color.system_accent2_100)
                .map { context.getColor(it) }
        } else {
            emptyList()
        }
        return listOf(
            info.lastUpdateTime,
            Build.VERSION.SDK_INT,
            // Uygulamanın seçili dili ve bugünün tarihi (tarih metin olarak yazılıyorsa gün değişince yenilenir).
            WidgetLanguage.tag(context),
            java.time.LocalDate.now(),
            configuration.locales.toLanguageTags(),
            configuration.fontScale,
            configuration.densityDpi,
            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            WidgetLanguage.is24Hour(context),
            TimeZone.getDefault().id,
            WallpaperTone.prefersDarkText(context),
            WallpaperTone.colors(context),
            systemColors,
            widgetIds,
        ).joinToString("|")
    }

    fun saved(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun save(context: Context, digest: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, digest).apply()
    }

    /** Bir sonraki istek kesinlikle çizsin (ör. tek bir widget'ın ayarı değişti). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
