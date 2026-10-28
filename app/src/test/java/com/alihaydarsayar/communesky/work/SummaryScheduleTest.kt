package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.work.SummarySchedule.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Sabah ve akşam özetinin ne zaman tetikleneceği, günde bir kez gönderim, pencere dışında atlama ve yeniden
 * planlama olayları (saat/saat dilimi değişimi). Saat hep sahte; örnek yer: Beşiktaş (İstanbul, UTC+3).
 */
class SummaryScheduleTest {

    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val day = LocalDate.of(2026, 10, 28)
    private val morning = MorningSummary.DEFAULT_MINUTES
    private val evening = MorningSummary.DEFAULT_EVENING_MINUTES

    private fun at(h: Int, m: Int = 0, date: LocalDate = day, zone: ZoneId = istanbul) = ZonedDateTime.of(date, LocalTime.of(h, m), zone)

    // --- Sonraki tetik

    @Test
    fun `the next trigger is today's chosen time when it has not come yet`() {
        assertEquals(at(7, 30), SummarySchedule.nextTrigger(at(6, 0), morning, sentOn = null))
        assertEquals(at(21, 0), SummarySchedule.nextTrigger(at(12, 0), evening, sentOn = day.minusDays(1)))
    }

    @Test
    fun `after the chosen time or after sending today the next trigger is tomorrow`() {
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.nextTrigger(at(7, 30), morning, sentOn = null))
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.nextTrigger(at(23, 0), morning, sentOn = day))
        // Bugün zaten gönderildi: saat daha gelmemiş olsa bile (ayar saati ileri alındı) ikinci kez planlanmaz.
        assertEquals(at(21, 0, day.plusDays(1)), SummarySchedule.nextTrigger(at(8, 0), evening, sentOn = day))
    }

    @Test
    fun `the end of the day and midnight`() {
        // 23:59 seçildi, 23:58'deyiz: bugün. 23:59'dan sonra yarın.
        assertEquals(at(23, 59), SummarySchedule.nextTrigger(at(23, 58), 23 * 60 + 59, null))
        assertEquals(at(23, 59, day.plusDays(1)), SummarySchedule.nextTrigger(at(23, 59), 23 * 60 + 59, null))
        // 00:00 seçildi: bugünün 00:00'ı geçti, yarının gece yarısı gelir.
        assertEquals(at(0, 0, day.plusDays(1)), SummarySchedule.nextTrigger(at(0, 0), 0, null))
        assertEquals(at(0, 0, day.plusDays(1)), SummarySchedule.nextTrigger(at(22, 0), 0, null))
    }

    @Test
    fun `a time zone change moves the trigger to the same wall clock time in the new zone`() {
        val london = ZoneId.of("Europe/London")
        val before = SummarySchedule.nextTrigger(at(6, 0), morning, null)
        // Aynı an Londra'da 04:00 (BST, UTC+1): 07:30 Londra saati sonraki tetik.
        val after = SummarySchedule.nextTrigger(at(6, 0).withZoneSameInstant(london), morning, null)
        assertEquals(london, after.zone)
        assertEquals(LocalTime.of(7, 30), after.toLocalTime())
        assertFalse(before.toInstant() == after.toInstant())
    }

    @Test
    fun `setting the clock forward past the chosen time schedules tomorrow, back before it schedules today`() {
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.nextTrigger(at(9, 0), morning, null))
        assertEquals(at(7, 30), SummarySchedule.nextTrigger(at(5, 0), morning, null))
    }

    @Test
    fun `a spring forward gap moves the trigger to the first valid time`() {
        // Avrupa/Paris 2026-03-29: 02:00–03:00 yok. 02:30 seçilirse 03:30 olur.
        val paris = ZoneId.of("Europe/Paris")
        val gapDay = LocalDate.of(2026, 3, 29)
        val trigger = SummarySchedule.nextTrigger(ZonedDateTime.of(gapDay, LocalTime.of(1, 0), paris), 2 * 60 + 30, null)
        assertEquals(LocalTime.of(3, 30), trigger.toLocalTime())
        assertEquals(gapDay, trigger.toLocalDate())
    }

    // --- Yedek yol

    @Test
    fun `the backup sends a missed summary inside the window`() {
        assertTrue(SummarySchedule.shouldCatchUp(at(7, 31), morning, sentOn = null))
        assertTrue(SummarySchedule.shouldCatchUp(at(10, 29), morning, sentOn = day.minusDays(1)))
    }

    @Test
    fun `the backup does not send before the time, after the window or twice a day`() {
        assertFalse(SummarySchedule.shouldCatchUp(at(7, 29), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(10, 30), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(14, 0), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(8, 0), morning, sentOn = day))
    }

    @Test
    fun `the backup window never crosses midnight`() {
        val late = 23 * 60 + 30
        assertTrue(SummarySchedule.shouldCatchUp(at(23, 45), late, null))
        // 23:30 + 3 saat gece yarısını aşar; ertesi gün 01:00'da dünün özeti atlanır, bugünün saati de gelmemiştir.
        assertFalse(SummarySchedule.shouldCatchUp(at(1, 0, day.plusDays(1)), late, null))
    }

    @Test
    fun `an evening summary can be sent late in the evening but not the next morning`() {
        assertTrue(SummarySchedule.shouldCatchUp(at(22, 30), evening, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(8, 0, day.plusDays(1)), evening, null))
    }

    // --- Gönderim kararı

    private val clean = Duration.ofMinutes(20)

    @Test
    fun `the summary is sent once a day`() {
        assertEquals(Verdict.Send, SummarySchedule.verdict(true, sentOn = null, today = day, cacheAge = clean))
        assertEquals(Verdict.Send, SummarySchedule.verdict(true, sentOn = day.minusDays(1), today = day, cacheAge = clean))
        assertEquals(Verdict.Skip, SummarySchedule.verdict(true, sentOn = day, today = day, cacheAge = clean))
        assertEquals(Verdict.Skip, SummarySchedule.verdict(false, sentOn = null, today = day, cacheAge = clean))
    }

    @Test
    fun `old data is named, very old or missing data is not sent`() {
        assertEquals(Verdict.Send, SummarySchedule.verdict(true, null, day, Duration.ofHours(3)))
        assertEquals(Verdict.SendCached, SummarySchedule.verdict(true, null, day, Duration.ofHours(5)))
        assertEquals(Verdict.SendCached, SummarySchedule.verdict(true, null, day, Duration.ofHours(24)))
        assertEquals(Verdict.Skip, SummarySchedule.verdict(true, null, day, Duration.ofHours(25)))
        assertEquals(Verdict.Skip, SummarySchedule.verdict(true, null, day, cacheAge = null))
    }

    @Test
    fun `forcing skips the enabled and sent checks only`() {
        assertEquals(Verdict.Send, SummarySchedule.verdict(false, day, day, clean, force = true))
        assertEquals(Verdict.Skip, SummarySchedule.verdict(false, day, day, null, force = true))
    }

    // --- Sessiz saatler: özet yine gelir, yalnız sessiz kanala gider

    @Test
    fun `quiet hours do not move the trigger, they only make the notification silent`() {
        val settings = NotificationSettings(morning = true, morningSound = true, quietHours = true, quietStart = 23 * 60, quietEnd = 8 * 60)
        // 07:30 sessiz saatlerin içinde (23:00–08:00): alarm yine 07:30'da, kanal sessiz.
        assertEquals(at(7, 30), SummarySchedule.nextTrigger(at(6, 0), settings.morningMinutes, null))
        assertEquals(NotificationKind.Morning.silentChannel, NotificationRouting.channel(NotificationKind.Morning, settings, 7 * 60 + 30))
        assertEquals(NotificationKind.Morning.soundChannel, NotificationRouting.channel(NotificationKind.Morning, settings, 9 * 60))
    }
}
