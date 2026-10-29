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
        assertTrue(SummarySchedule.shouldCatchUp(at(7, 45), morning, sentOn = null))
        assertTrue(SummarySchedule.shouldCatchUp(at(10, 29), morning, sentOn = day.minusDays(1)))
    }

    @Test
    fun `the backup does not send before the time, after the window or twice a day`() {
        assertFalse(SummarySchedule.shouldCatchUp(at(7, 29), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(10, 30), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(10, 31), morning, null))
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

    // --- Atlanan özetin nedeni kayda yazılır (1.5.11)

    @Test
    fun `a skipped summary names its reason, a quiet skip leaves no record`() {
        val clean = Duration.ofMinutes(10)
        // Kapalı ya da bugün zaten gönderilmiş: kayıt yok.
        assertEquals(null, SummarySchedule.skipOutcome(false, null, day, clean))
        assertEquals(null, SummarySchedule.skipOutcome(true, day, day, clean))
        // Veri yok ya da 24 saatten eski: neden yazılır.
        assertEquals(AttemptOutcome.NoData, SummarySchedule.skipOutcome(true, null, day, null))
        assertEquals(AttemptOutcome.DataTooOld, SummarySchedule.skipOutcome(true, null, day, Duration.ofHours(25)))
        // Eski ama 24 saat içindeki veriyle özet gönderilir: atlama yok.
        assertEquals(null, SummarySchedule.skipOutcome(true, null, day, Duration.ofHours(5)))
        // Hata ayıklama gönderimi kapalı/gönderilmiş denetimlerini atlar, veri denetimini atlamaz.
        assertEquals(null, SummarySchedule.skipOutcome(false, day, day, clean, force = true))
        assertEquals(AttemptOutcome.NoData, SummarySchedule.skipOutcome(false, day, day, null, force = true))
    }

    @Test
    fun `skip reasons agree with the verdict`() {
        for (age in listOf(null, Duration.ofMinutes(5), Duration.ofHours(5), Duration.ofHours(25))) {
            val skipped = SummarySchedule.verdict(true, null, day, age) == Verdict.Skip
            assertEquals("age=$age", skipped, SummarySchedule.skipOutcome(true, null, day, age) != null)
        }
    }

    @Test
    fun `the backup leaves the first fifteen minutes to the alarm`() {
        assertFalse(SummarySchedule.shouldCatchUp(at(7, 30), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(7, 44), morning, null))
        assertTrue(SummarySchedule.shouldCatchUp(at(7, 45), morning, null))
    }

    @Test
    fun `the window is three hours after the chosen time, start and end inclusive of the start`() {
        // Pencere [saat + 15 dk, saat + 3 sa): sınırlar.
        assertTrue(SummarySchedule.shouldCatchUp(at(10, 29), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(10, 30), morning, null))
    }

    @Test
    fun `a time chosen after it has passed today skips today, a later time does not`() {
        assertTrue(SummarySchedule.passedToday(at(8, 0), morning))
        assertTrue(SummarySchedule.passedToday(at(7, 30), morning))
        assertFalse(SummarySchedule.passedToday(at(7, 29), morning))
        assertFalse(SummarySchedule.passedToday(at(0, 5), morning))
    }

    @Test
    fun `a time that is only minutes away is still today`() {
        val now = at(20, 55)
        assertFalse(SummarySchedule.passedToday(now, evening))
        assertEquals(at(21, 0), SummarySchedule.nextTrigger(now, evening, null))
        // Geçmiş saat seçilirse ilk özet yarın.
        assertEquals(at(21, 0, day.plusDays(1)), SummarySchedule.nextTrigger(at(21, 5), evening, day))
    }

    // --- Telefonun saati UTC+4 (Bakü), yer UTC+3 (Beşiktaş): özet telefonun saatine göre (1.5.11)

    private val baku = ZoneId.of("Asia/Baku")

    @Test
    fun `the trigger, the sent day and the window follow the phone zone, not the place`() {
        val phone = ZonedDateTime.of(day, LocalTime.of(6, 0), baku)
        // 07:00 Bakü saati; Beşiktaş'ta o an 06:00 ama özet telefonun 07:00'sinde gelir.
        assertEquals(ZonedDateTime.of(day, LocalTime.of(7, 0), baku), SummarySchedule.nextTrigger(phone, 7 * 60, null))
        assertEquals(ZonedDateTime.of(day, LocalTime.of(7, 0), baku).toInstant(), day.atTime(3, 0).toInstant(java.time.ZoneOffset.UTC))
        // Yedek pencere de telefonun saatinde: 07:15 ile 10:00 arası.
        assertFalse(SummarySchedule.shouldCatchUp(ZonedDateTime.of(day, LocalTime.of(7, 14), baku), 7 * 60, null))
        assertTrue(SummarySchedule.shouldCatchUp(ZonedDateTime.of(day, LocalTime.of(7, 15), baku), 7 * 60, null))
        assertFalse(SummarySchedule.shouldCatchUp(ZonedDateTime.of(day, LocalTime.of(10, 0), baku), 7 * 60, null))
        // 18:00 akşam özeti.
        assertEquals(ZonedDateTime.of(day, LocalTime.of(18, 0), baku), SummarySchedule.nextTrigger(ZonedDateTime.of(day, LocalTime.of(17, 0), baku), 18 * 60, null))
        // Bugün gönderildiyse sıradaki yarın, gün sonu geçişinde de.
        assertEquals(
            ZonedDateTime.of(day.plusDays(1), LocalTime.of(7, 0), baku),
            SummarySchedule.nextTrigger(ZonedDateTime.of(day, LocalTime.of(23, 59), baku), 7 * 60, day),
        )
    }

    @Test
    fun `changing the phone time zone moves the trigger to the same wall clock time in the new zone`() {
        val istanbulNow = ZonedDateTime.of(day, LocalTime.of(5, 0), istanbul)
        val inIstanbul = SummarySchedule.nextTrigger(istanbulNow, 7 * 60, null)
        val inBaku = SummarySchedule.nextTrigger(istanbulNow.withZoneSameInstant(baku), 7 * 60, null)
        assertEquals(LocalTime.of(7, 0), inIstanbul.toLocalTime())
        assertEquals(LocalTime.of(7, 0), inBaku.toLocalTime())
        assertEquals(java.time.Duration.ofHours(1), java.time.Duration.between(inBaku, inIstanbul))
    }

    @Test
    fun `the summary describes the phone day, so a phone just past midnight does not describe the place's previous day`() {
        // Telefon (Bakü) 00:30, yer (Beşiktaş) bir önceki gün 23:30.
        val placeNow = day.minusDays(1).atTime(23, 30)
        assertEquals(day.atStartOfDay(), SummarySchedule.summaryNow(placeNow, day))
        // Aynı gündeyse yerin saati korunur.
        val same = day.atTime(6, 0)
        assertEquals(same, SummarySchedule.summaryNow(same, day))
        // Yer bir gün ileride (Tokyo): telefonun günü anlatılır.
        assertEquals(day.atStartOfDay(), SummarySchedule.summaryNow(day.plusDays(1).atTime(2, 0), day))
    }

    // --- Alıcı süre bütçesi: ANR olmasın (1.5.11)

    @Test
    fun `the network refresh in the alarm receiver always leaves time to post the notification`() {
        for (elapsed in listOf(0L, 500L, 2_000L, 4_000L, 5_000L, 7_400L)) {
            val budget = SummarySchedule.refreshBudgetMs(elapsed)
            assertTrue("elapsed=$elapsed budget=$budget", elapsed + budget + SummarySchedule.VERIFY_MS <= SummarySchedule.RECEIVER_BUDGET_MS)
            assertTrue(budget <= SummarySchedule.MAX_REFRESH_MS)
        }
        assertEquals(SummarySchedule.MAX_REFRESH_MS, SummarySchedule.refreshBudgetMs(0))
        // Yükleme çok sürdüyse ağ yenilemesi atlanır, eldeki veriyle gönderilir.
        assertEquals(0L, SummarySchedule.refreshBudgetMs(8_000))
        assertEquals(0L, SummarySchedule.refreshBudgetMs(60_000))
        assertTrue(SummarySchedule.RECEIVER_BUDGET_MS < 10_000)
    }

    // --- Alarm penceresi: hedefe adım adım yaklaşan alarm (1.5.11)

    private val t0 = java.time.Instant.parse("2026-10-29T00:00:00Z")

    /**
     * Android'in izinsiz (inexact) alarmı: asla erken çalmaz, en geç kurulma anından itibaren kalan sürenin %75'i
     * kadar sonra çalar. [lateShare] 0 = hemen (en erken), 1 = en geç. Sonuç: özetin gönderildiği an ve kurulan alarm sayısı.
     */
    private fun simulate(start: java.time.Instant, target: java.time.Instant, lateShare: Double): Pair<java.time.Instant, Int> {
        var now = start
        var alarms = 0
        while (true) {
            val wake = SummarySchedule.wakeAt(now, target)
            alarms++
            val window = java.time.Duration.between(now, wake).toMillis() * 0.75
            val fires = wake.plusMillis((window * lateShare).toLong())
            assertFalse("never earlier than requested", fires.isBefore(wake))
            now = fires
            if (SummarySchedule.reached(now, target)) return now to alarms
            assertTrue("each step moves forward", alarms < 20)
        }
    }

    @Test
    fun `the alarm gets close to the target in a few steps and never overshoots it by more than nine minutes`() {
        val target = t0.plus(java.time.Duration.ofHours(12))
        for (share in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val (fired, alarms) = simulate(t0, target, share)
            val late = java.time.Duration.between(target, fired)
            assertTrue("share=$share late=$late", late <= java.time.Duration.ofMinutes(9) + java.time.Duration.ofSeconds(1))
            assertTrue("share=$share alarms=$alarms", alarms <= 8)
        }
    }

    @Test
    fun `a target within the last twelve minutes is set directly, a far one is approached`() {
        val target = t0.plus(java.time.Duration.ofHours(10))
        assertEquals(target, SummarySchedule.wakeAt(target.minusSeconds(12 * 60), target))
        assertEquals(target, SummarySchedule.wakeAt(target.minusSeconds(30), target))
        val far = SummarySchedule.wakeAt(t0, target)
        assertTrue(far.isAfter(t0) && far.isBefore(target.minus(java.time.Duration.ofHours(3))))
        // Hedef uzaktayken tek alarm en geç hedefte çalar (4/7 + 0,75 × 4/7 = 1).
        assertEquals(t0.plus(java.time.Duration.ofHours(10).multipliedBy(4).dividedBy(7)), SummarySchedule.wakeAt(t0, target))
    }

    @Test
    fun `an alarm that fires after the target still sends, one far before it only sets the next step`() {
        val target = t0.plus(java.time.Duration.ofHours(2))
        assertTrue(SummarySchedule.reached(target, target))
        assertTrue(SummarySchedule.reached(target.plusSeconds(600), target))
        assertTrue(SummarySchedule.reached(target.minusSeconds(60), target))
        assertFalse(SummarySchedule.reached(target.minusSeconds(120), target))
        assertFalse(SummarySchedule.reached(t0, target))
    }

    @Test
    fun `a clock that jumps forward past the target sends instead of stepping again`() {
        val target = t0.plus(java.time.Duration.ofHours(2))
        assertTrue(SummarySchedule.reached(t0.plus(java.time.Duration.ofHours(5)), target))
    }

    // --- Kurulu alarm yarına kaydırılıp iptal edilmez (1.5.11, emülatörde bulundu)

    @Test
    fun `a time that has just passed stays today so a pending alarm is not replaced by tomorrow's`() {
        // Alarm 07:30'da çalacak ama biraz gecikebilir; 07:30'da, 07:31'de ya da 07:44'te yeniden kurulursa hedef hâlâ bugündür.
        assertEquals(at(7, 30), SummarySchedule.alarmTarget(at(7, 30), morning, null))
        assertEquals(at(7, 30), SummarySchedule.alarmTarget(at(7, 31), morning, null))
        assertEquals(at(7, 30), SummarySchedule.alarmTarget(at(7, 44), morning, null))
        // Bekleme payı (15 dk) geçince yarın; yedek yol zaten bundan sonra devreye girer.
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.alarmTarget(at(7, 45), morning, null))
        // Bugün gönderildiyse ya da alarm şu an çalıp işlendiyse sıradaki yarındır.
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.alarmTarget(at(7, 31), morning, day))
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.alarmTarget(at(7, 31), morning, null, handledToday = true))
        // Saat gelmeden önce nextTrigger ile aynı.
        assertEquals(at(7, 30), SummarySchedule.alarmTarget(at(5, 0), morning, null))
    }

    @Test
    fun `the alarm path and the backup path do not leave a gap`() {
        assertEquals(at(7, 30), SummarySchedule.alarmTarget(at(7, 44), morning, null))
        assertFalse(SummarySchedule.shouldCatchUp(at(7, 44), morning, null))
        assertTrue(SummarySchedule.shouldCatchUp(at(7, 45), morning, null))
        assertEquals(at(7, 30, day.plusDays(1)), SummarySchedule.alarmTarget(at(7, 45), morning, null))
    }

    @Test
    fun `in the zone of a phone on UTC+4 the just-passed rule is the same`() {
        val baku = ZoneId.of("Asia/Baku")
        val seven = 7 * 60
        val late = ZonedDateTime.of(day, LocalTime.of(7, 5), baku)
        assertEquals(ZonedDateTime.of(day, LocalTime.of(7, 0), baku), SummarySchedule.alarmTarget(late, seven, null))
    }
}
