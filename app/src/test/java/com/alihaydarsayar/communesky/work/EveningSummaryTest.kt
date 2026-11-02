package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.today.SummaryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Sabah ve akşam özeti birbirinden bağımsızdır (E1, E2, E4, E6). */
class EveningSummaryTest {

    private val zone = ZoneId.of("Asia/Baku")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 28, h, m, 0, 0, zone)
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun `morning and evening keep separate sent-day records, notification ids and alarm ids`() {
        assertNotEquals(MorningSummary.lastDayKey(SummaryKind.Morning), MorningSummary.lastDayKey(SummaryKind.Evening))
        assertNotEquals(MorningSummary.notificationId(SummaryKind.Morning), MorningSummary.notificationId(SummaryKind.Evening))
        val ids = SummaryKind.entries.flatMap { listOf(false, true).map { b -> MorningSummary.alarmRequestCode(it, b) } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a morning summary sent today does not stop the evening one`() {
        // Sabah bugün gönderildi (sentOn = bugün); akşamın kaydı ayrı ve boş.
        assertFalse(SummarySchedule.shouldCatchUp(at(8, 0), 7 * 60 + 30, today))
        assertTrue(SummarySchedule.shouldCatchUp(at(18, 20), 18 * 60, null))
        // Akşamın alarmı sabah gönderildikten sonra bugünün 18:00'ine kurulur.
        assertEquals(at(18, 0), SummarySchedule.alarmTarget(at(7, 31), 18 * 60, null, handledToday = false))
        // Sabahın alarmı yarına kurulur.
        assertEquals(at(7, 30).plusDays(1), SummarySchedule.alarmTarget(at(7, 31), 7 * 60 + 30, today, handledToday = true))
    }

    @Test
    fun `the evening window is three hours and the late path works after 18 00`() {
        assertTrue(SummarySchedule.shouldCatchUp(at(20, 59), 18 * 60, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(21, 1), 18 * 60, null))
        assertEquals(at(21, 0), SummarySchedule.catchUpEnd(at(18, 0)))
    }

    @Test
    fun `the chain of alarms toward 18 00 ends at the target and stays shorter than a day`() {
        var now = at(7, 31).toInstant()
        val target = at(18, 0).toInstant()
        var hops = 0
        while (SummarySchedule.wakeAt(now, target) != target) {
            now = SummarySchedule.wakeAt(now, target)
            hops++
        }
        assertTrue("hops=$hops", hops in 3..8)
        assertTrue(Duration.between(now, target) <= SummarySchedule.FINAL_HOP)
    }

    @Test
    fun `without any cache or with an old one a notice is sent for both summaries`() {
        for (age in listOf<Duration?>(null, Duration.ofHours(30))) {
            val action = SummarySchedule.action(true, null, null, today, age)
            assertTrue(action == SummarySchedule.Action.NoDataNotice || action == SummarySchedule.Action.StaleNotice)
        }
    }
}
