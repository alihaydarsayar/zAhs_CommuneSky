package com.alihaydarsayar.communesky.work

import android.app.NotificationManager
import androidx.work.ListenableWorker
import com.alihaydarsayar.communesky.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 1.5.13 bildirim güvenilirliği: 15 dakikalık güvenlik ağı, 20 saniye sınırı, alarm tetiklenme kaydı (bildirimden
 * ayrı), gönderim yolu ve saatlik turun yenileme başarısız olsa da özeti denetlemesi.
 */
class SummaryWatchTest {

    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val evening = 19 * 60 + 3
    private fun at(hour: Int, minute: Int, day: Int = 7) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, istanbul)

    // --- Güvenlik ağı penceresi: hedef saat ile hedef + 3 saat

    @Test
    fun `the background check sends from the target time until three hours later`() {
        assertFalse(SummarySchedule.shouldWatch(at(19, 2), evening, null))
        assertTrue(SummarySchedule.shouldWatch(at(19, 3), evening, null))
        // Alarm hiç gelmediyse ya da geciktiyse: bekleme payı yok (1.5.12'de yedek yol 15 dk sonra başlıyordu).
        assertTrue(SummarySchedule.shouldWatch(at(19, 10), evening, null))
        assertTrue(SummarySchedule.shouldWatch(at(22, 2), evening, null))
        assertFalse(SummarySchedule.shouldWatch(at(22, 3), evening, null))
    }

    @Test
    fun `the background check never sends a summary twice on the same day`() {
        val today = LocalDate.of(2026, 11, 7)
        assertFalse(SummarySchedule.shouldWatch(at(19, 30), evening, today))
        // Dün gönderildi: bugün yeniden gönderilir.
        assertTrue(SummarySchedule.shouldWatch(at(19, 30), evening, today.minusDays(1)))
    }

    @Test
    fun `the window ends at midnight for a late evening summary`() {
        val late = 23 * 60 + 30
        assertTrue(SummarySchedule.shouldWatch(at(23, 59), late, null))
        assertFalse(SummarySchedule.shouldWatch(at(0, 10, day = 8), late, null))
    }

    @Test
    fun `without weather the check still sends within twenty seconds`() {
        // Yükleme (4 sn) + yenileme + bildirimin görünmesini bekleme (1,5 sn) toplamı 20 sn'yi aşmaz.
        val worst = SummarySchedule.LOAD_TIMEOUT_MS + SummarySchedule.watchRefreshBudgetMs(0) + SummarySchedule.VERIFY_MS
        assertTrue("$worst ms", worst <= 20_000)
        // Zaman dolmuşsa yenileme atlanır, eldeki veriyle gönderilir.
        assertEquals(0L, SummarySchedule.watchRefreshBudgetMs(19_000))
        assertTrue(SummarySchedule.watchRefreshBudgetMs(1_000) > 0)
    }

    @Test
    fun `the period of the safety net is fifteen minutes`() {
        assertEquals(15L, SummaryWatchWorker.PERIOD_MINUTES)
    }

    // --- Saatlik tur: yenileme başarısız olsa da özet denetlenir (1.5.12'de denetim yenilemenin sonundaydı)

    @Test
    fun `the hourly round checks the summaries even when the weather refresh fails`() = runBlocking {
        var checked = 0
        val offline = WeatherRefreshWorker.round(refresh = { throw IOException("offline") }, summaries = { checked++ })
        assertEquals(1, checked)
        assertTrue(offline is ListenableWorker.Result.Retry)
        val broken = WeatherRefreshWorker.round(refresh = { throw IllegalStateException("x") }, summaries = { checked++ })
        assertEquals(2, checked)
        assertTrue(broken is ListenableWorker.Result.Failure)
        val fine = WeatherRefreshWorker.round(refresh = { }, summaries = { checked++ })
        assertEquals(3, checked)
        assertTrue(fine is ListenableWorker.Result.Success)
        // Özet denetiminin kendi hatası turu bozmaz.
        assertTrue(WeatherRefreshWorker.round(refresh = { }, summaries = { error("x") }) is ListenableWorker.Result.Success)
    }

    // --- Gönderim yolu kaydı

    @Test
    fun `the path that sent a summary is recorded and old records still read`() {
        val now = Instant.parse("2026-11-07T16:05:00Z")
        val entries = listOf(
            Attempt(now, AttemptKind.Evening, AttemptOutcome.Sent, via = SummaryPath.Watcher),
            Attempt(now.plusSeconds(1), AttemptKind.Morning, AttemptOutcome.Error, "LoadTimeout", SummaryPath.Alarm),
            Attempt(now.plusSeconds(2), AttemptKind.Test, AttemptOutcome.Sent),
        )
        assertEquals(entries, AttemptLog.decode(AttemptLog.encode(entries)))
        // 1.5.12 satırları (3 ve 4 alan) okunur, yolu yoktur.
        val old = AttemptLog.decode("1794000000000,Morning,Sent\n1794000000001,Evening,Error,LoadTimeout")
        assertEquals(listOf(null, null), old.map { it.via })
        assertEquals("LoadTimeout", old[1].detail)
        // Bilinmeyen yol ya da bozuk ayrıntı içeren satır atlanır.
        assertTrue(AttemptLog.decode("1794000000000,Morning,Sent,,Somewhere").isEmpty())
    }

    // --- Alarm tetiklenme kaydı (bildirimden ayrı)

    @Test
    fun `alarm fires are kept apart with their delay`() {
        val target = Instant.parse("2026-11-07T16:03:00Z")
        val fire = AlarmFire(target.plus(Duration.ofMinutes(7)), AttemptKind.Evening, target, AlarmWhich.Backup)
        assertEquals(Duration.ofMinutes(7), fire.delay)
        val back = AlarmTrace.decode(AlarmTrace.encode(listOf(fire)))
        assertEquals(listOf(fire), back)
        assertTrue(AlarmTrace.decode("garbage\n1,Evening,2").isEmpty())
    }

    @Test
    fun `at most ten alarm fires are kept, none older than seven days`() {
        val now = Instant.parse("2026-11-07T16:00:00Z")
        var entries = emptyList<AlarmFire>()
        repeat(14) { i ->
            val at = now.minus(Duration.ofHours((14 - i).toLong()))
            entries = AlarmTrace.add(entries, AlarmFire(at, if (i % 2 == 0) AttemptKind.Morning else AttemptKind.Evening, at, AlarmWhich.Final), now)
        }
        assertEquals(AlarmTrace.MAX, entries.size)
        assertEquals(AttemptKind.Evening, AlarmTrace.last(entries, AttemptKind.Evening)?.kind)
        val old = AlarmFire(now.minus(Duration.ofDays(8)), AttemptKind.Morning, now, AlarmWhich.Final)
        assertTrue(AlarmTrace.prune(listOf(old), now).isEmpty())
        assertNull(AlarmTrace.last(emptyList(), AttemptKind.Morning))
    }

    // --- Durum etiketleri

    @Test
    fun `standby buckets and do not disturb have readable names`() {
        val buckets = mapOf(
            5 to R.string.status_bucket_exempt, 10 to R.string.status_bucket_active, 20 to R.string.status_bucket_working,
            30 to R.string.status_bucket_frequent, 40 to R.string.status_bucket_rare, 45 to R.string.status_bucket_restricted, 50 to R.string.status_bucket_never,
        )
        buckets.forEach { (bucket, name) -> assertEquals("$bucket", name, StatusLabels.standbyBucket(bucket)) }
        assertNull(StatusLabels.standbyBucket(99))
        assertEquals(R.string.status_dnd_off, StatusLabels.interruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL))
        assertEquals(R.string.status_dnd_priority, StatusLabels.interruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertEquals(R.string.status_dnd_none, StatusLabels.interruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertEquals(R.string.status_dnd_alarms, StatusLabels.interruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS))
        assertEquals(R.string.status_unreadable, StatusLabels.interruptionFilter(null))
    }
}
