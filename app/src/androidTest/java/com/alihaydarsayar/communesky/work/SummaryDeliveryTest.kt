package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.debug.DemoWeather
import com.alihaydarsayar.communesky.model.today.SummaryKind
import com.alihaydarsayar.communesky.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Çift bildirim olmaz: alarm, yedek alarm, 15 dakikalık denetim, saatlik iş ve uygulama açılışı aynı anda çalışsa da
 * bir özet günde bir kez Android'e verilir. Örnek hava (DemoWeather) kurulur; bildirim izni gerekir.
 */
@RunWith(AndroidJUnit4::class)
class SummaryDeliveryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val posts = CopyOnWriteArrayList<Pair<SummaryKind, SummaryPath?>>()

    @Before
    fun setUp() = runBlocking {
        assumeTrue("notification permission", NotificationHealth.permissionGranted(context))
        DemoWeather.install(context, listOf(DemoWeather.Entry(DemoWeather.place("besiktas"), DemoWeather.SKIES.getValue("clear"), DemoWeather.Role.Home, 28, false)))
        context.getSharedPreferences("morning_summary", Context.MODE_PRIVATE).edit().clear().commit()
        MorningSummary.onPosted = { kind, via -> posts += kind to via }
    }

    @After
    fun tearDown() {
        MorningSummary.onPosted = null
    }

    /** Akşam özeti açık, saati iki dakika önce: pencere açık, bugün gönderilmedi. */
    private suspend fun eveningDue() {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).settingsRepository()
        val now = LocalTime.now()
        val minutes = (now.hour * 60 + now.minute - 2).coerceAtLeast(0)
        val settings = repository.settings.first().notifications.copy(evening = true, eveningMinutes = minutes, morning = false)
        repository.setNotifications(settings)
    }

    @Test
    fun everyPathAtOnceSendsOneSummary() = runBlocking {
        eveningDue()
        val target = System.currentTimeMillis() - 120_000
        listOf(
            async { MorningSummary.onAlarm(context, SummaryKind.Evening, via = SummaryPath.Alarm) },
            async { MorningSummary.onAlarm(context, SummaryKind.Evening, via = SummaryPath.Backup) },
            async { MorningSummary.watch(context) },
            async { MorningSummary.catchUp(context, SummaryPath.Hourly) },
            async { MorningSummary.restore(context, SummaryPath.AppOpen) },
        ).awaitAll()
        assertEquals(posts.toString(), 1, posts.count { it.first == SummaryKind.Evening })
        val sentOn = context.getSharedPreferences("morning_summary", Context.MODE_PRIVATE).getString("last_day_evening", null)
        assertEquals(LocalDate.now().toString(), sentOn)
        assertTrue(target > 0)
        // İkinci tur: hiçbir yol yeniden göndermez.
        MorningSummary.watch(context)
        MorningSummary.onAlarm(context, SummaryKind.Evening, via = SummaryPath.Alarm)
        MorningSummary.catchUp(context, SummaryPath.Hourly)
        assertEquals(posts.toString(), 1, posts.count { it.first == SummaryKind.Evening })
    }

    @Test
    fun theBackgroundCheckSendsWhenNoAlarmCame() = runBlocking {
        eveningDue()
        MorningSummary.watch(context)
        assertEquals(posts.toString(), listOf(SummaryKind.Evening to SummaryPath.Watcher), posts.toList())
        val last = NotificationLog(context).entries().last { it.kind == AttemptKind.Evening }
        assertEquals(SummaryPath.Watcher, last.via)
        assertTrue(last.outcome.delivered)
    }
}
