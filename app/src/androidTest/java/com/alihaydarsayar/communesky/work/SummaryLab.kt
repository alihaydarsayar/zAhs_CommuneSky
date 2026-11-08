package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.debug.DemoWeather
import com.alihaydarsayar.communesky.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Bildirim test matrisi için yardımcı (elle çalıştırılır; argüman verilmezse hiçbir şey yapmaz):
 *
 *   adb shell am instrument -w -e class com.alihaydarsayar.communesky.work.SummaryLab#configure \
 *     -e kind evening -e in 3 -e reset true -e demo true com.alihaydarsayar.communesky.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Özeti telefonun saatinden "in" dakika sonraya kurar (eksi değer: geçmiş), isteğe bağlı olarak "gönderildi"
 * işaretlerini ve kayıtları siler (reset), örnek hava kurar (demo; age: kaç saat eski), alarmları ayara göre yeniden
 * kurar ve istenirse siler (noalarm). Sonra alarm, denetim ya da
 * engel durumları `dumpsys alarm` ve Bildirim durumu ile izlenir.
 */
@RunWith(AndroidJUnit4::class)
class SummaryLab {

    @Test
    fun configure(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val kind = args.getString("kind")
        assumeTrue("no arguments: nothing to do", kind != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        // "off": iki özeti de kapatır (bir sonraki senaryo önceki saatin kalıntısıyla başlamasın).
        if (kind == "off") {
            val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).settingsRepository()
            val off = repository.settings.first().notifications.copy(morning = false, evening = false)
            repository.setNotifications(off)
            MorningSummary.schedule(context, off)
            Thread.sleep(1_500)
            return@runBlocking
        }
        if (args.getString("demo") == "true") {
            // "age": verinin kaç saat önce alınmış sayılacağı (eski veriyle gönderimi ve yenileme denemesini görmek için).
            val age = args.getString("age")?.toLongOrNull() ?: 0L
            DemoWeather.install(
                context, listOf(DemoWeather.Entry(DemoWeather.place("besiktas"), DemoWeather.SKIES.getValue("clear"), DemoWeather.Role.Home, 28, false)),
                fetchedAt = java.time.Instant.now().minus(java.time.Duration.ofHours(age)),
            )
        }
        if (args.getString("reset") == "true") {
            context.getSharedPreferences("morning_summary", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("notification_log", Context.MODE_PRIVATE).edit().clear().commit()
        }
        val now = LocalTime.now()
        val minutes = Math.floorMod(now.hour * 60 + now.minute + (args.getString("in")?.toIntOrNull() ?: 3), 24 * 60)
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).settingsRepository()
        val old = repository.settings.first().notifications
        val updated = if (kind == "morning") old.copy(morning = true, morningMinutes = minutes) else old.copy(evening = true, eveningMinutes = minutes)
        repository.setNotifications(updated)
        MorningSummary.schedule(context, updated)
        // "noalarm": alarmlar silinir (üretici arayüzünün alarmı silmesi); yalnız güvenlik ağı kalır.
        // Alarm niyeti bileşen ve istek koduyla eşlenir (ekler sayılmaz): zincir (0/1) ve yedek (10/11). Eski sürümlerde de çalışır.
        if (args.getString("noalarm") == "true") {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java)
            val base = if (kind == "morning") 0 else 1
            for (code in listOf(base, base + 10)) {
                android.app.PendingIntent.getBroadcast(
                    context, code, android.content.Intent(context, MorningSummaryReceiver::class.java),
                    android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE,
                )?.let { alarms.cancel(it); it.cancel() }
            }
        }
        android.util.Log.i("SummaryLab", "$kind at ${minutes / 60}:${minutes % 60}")
        // apply() ile yazılan kayıtlar diske geçsin: instrument bitince süreç kapanır.
        Thread.sleep(1_500)
    }
}
