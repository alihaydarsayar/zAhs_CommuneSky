package com.alihaydarsayar.communesky.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Bildirim deneme kaydı: en çok 5 kayıt, 7 günden eskisi silinir, tekrarlar listeyi doldurmaz. */
class AttemptLogTest {

    private val now = Instant.parse("2026-10-29T08:00:00Z")
    private fun ago(hours: Long) = now.minus(Duration.ofHours(hours))
    private fun attempt(hours: Long, kind: AttemptKind, outcome: AttemptOutcome) = Attempt(ago(hours), kind, outcome)

    @Test
    fun `only the last five attempts are kept`() {
        var entries = emptyList<Attempt>()
        val outcomes = listOf(AttemptOutcome.Sent, AttemptOutcome.NoPermission, AttemptOutcome.ChannelOff, AttemptOutcome.NoData, AttemptOutcome.DataTooOld, AttemptOutcome.Failed)
        outcomes.forEachIndexed { index, outcome ->
            entries = AttemptLog.add(entries, attempt(30L - index, AttemptKind.Morning, outcome), now)
        }
        assertEquals(5, entries.size)
        assertEquals(outcomes.drop(1), entries.map { it.outcome })
    }

    @Test
    fun `entries older than seven days are deleted`() {
        val old = Attempt(now.minus(Duration.ofDays(7)).minusSeconds(1), AttemptKind.Evening, AttemptOutcome.Sent)
        val edge = Attempt(now.minus(Duration.ofDays(7)), AttemptKind.Morning, AttemptOutcome.Sent)
        val entries = AttemptLog.add(listOf(old, edge), attempt(1, AttemptKind.Test, AttemptOutcome.Sent), now)
        assertEquals(listOf(edge.at, ago(1)), entries.map { it.at })
    }

    @Test
    fun `the same result of the same kind updates the last record instead of adding one`() {
        var entries = AttemptLog.add(emptyList(), attempt(3, AttemptKind.Morning, AttemptOutcome.NoPermission), now)
        entries = AttemptLog.add(entries, attempt(2, AttemptKind.Morning, AttemptOutcome.NoPermission), now)
        entries = AttemptLog.add(entries, attempt(1, AttemptKind.Morning, AttemptOutcome.NoPermission), now)
        assertEquals(1, entries.size)
        assertEquals(ago(1), entries.single().at)
        // Başka sonuç ya da başka tür yeni kayıt açar.
        entries = AttemptLog.add(entries, attempt(0, AttemptKind.Morning, AttemptOutcome.Sent), now)
        entries = AttemptLog.add(entries, attempt(0, AttemptKind.Evening, AttemptOutcome.NoPermission), now)
        assertEquals(3, entries.size)
    }

    @Test
    fun `entries survive encoding and broken lines are skipped`() {
        val entries = listOf(attempt(5, AttemptKind.Morning, AttemptOutcome.SentOldData), attempt(1, AttemptKind.Test, AttemptOutcome.NotVisible))
        assertEquals(entries, AttemptLog.decode(AttemptLog.encode(entries)))
        val broken = AttemptLog.encode(entries) + "\nnonsense\n12,Nope,Sent\nabc,Morning,Sent\n1,Morning,Gone"
        assertEquals(entries, AttemptLog.decode(broken))
        assertTrue(AttemptLog.decode(null).isEmpty())
        assertTrue(AttemptLog.decode("").isEmpty())
    }

    @Test
    fun `last sent ignores tests and skipped attempts`() {
        val entries = listOf(
            attempt(30, AttemptKind.Morning, AttemptOutcome.Sent),
            attempt(20, AttemptKind.Evening, AttemptOutcome.SentOldData),
            attempt(10, AttemptKind.Morning, AttemptOutcome.NoData),
            attempt(1, AttemptKind.Test, AttemptOutcome.Sent),
        )
        assertEquals(ago(20), AttemptLog.lastSent(entries)?.at)
        assertEquals(AttemptOutcome.Sent, AttemptLog.last(entries, AttemptKind.Test)?.outcome)
        assertNull(AttemptLog.lastSent(listOf(attempt(1, AttemptKind.Test, AttemptOutcome.Sent))))
        assertEquals(AttemptOutcome.NoData, AttemptLog.last(entries, AttemptKind.Morning)?.outcome)
    }

    @Test
    fun `an error keeps only the exception class name`() {
        val entries = listOf(Attempt(ago(1), AttemptKind.Morning, AttemptOutcome.Error, Attempt.nameOf(IllegalStateException("Beşiktaş 41.04,29.00"))))
        assertEquals("java.lang.IllegalStateException", entries.single().detail)
        assertEquals(entries, AttemptLog.decode(AttemptLog.encode(entries)))
        // Yer adı ya da konum içeren bir ayrıntı kabul edilmez; bozuk satır atlanır.
        assertTrue(AttemptLog.decode("1,Morning,Error,Beşiktaş 41.04").isEmpty())
        assertTrue(runCatching { Attempt(ago(1), AttemptKind.Morning, AttemptOutcome.Error, "Limerick, 52.66") }.isFailure)
    }

    @Test
    fun `fallback counts as delivered, a stale notice and an error do not`() {
        assertTrue(AttemptOutcome.Fallback.delivered)
        assertTrue(!AttemptOutcome.StaleNotice.delivered && !AttemptOutcome.Error.delivered && !AttemptOutcome.Missed.delivered)
    }

    // --- Kaçırma algılama

    private val baku = java.time.ZoneId.of("Asia/Baku")
    private val planned = Instant.parse("2026-10-29T03:00:00Z") // Bakü 07:00

    @Test
    fun `no attempt after the window is a missed summary`() {
        val after = planned.plus(Duration.ofHours(3)).plusSeconds(1)
        assertTrue(MissedCheck.missed(planned, AttemptKind.Morning, emptyList(), null, after, baku))
    }

    @Test
    fun `inside the window nothing is missed yet because the backup may still send`() {
        assertTrue(!MissedCheck.missed(planned, AttemptKind.Morning, emptyList(), null, planned.plus(Duration.ofMinutes(179)), baku))
        assertTrue(!MissedCheck.missed(null, AttemptKind.Morning, emptyList(), null, planned.plus(Duration.ofDays(1)), baku))
    }

    @Test
    fun `any attempt record or a sent day after the planned time means it was not missed`() {
        val after = planned.plus(Duration.ofHours(5))
        for (outcome in listOf(AttemptOutcome.Sent, AttemptOutcome.NoPermission, AttemptOutcome.DataTooOld, AttemptOutcome.Error, AttemptOutcome.StaleNotice)) {
            val record = listOf(Attempt(planned.plusSeconds(60), AttemptKind.Morning, outcome))
            assertTrue(outcome.name, !MissedCheck.missed(planned, AttemptKind.Morning, record, null, after, baku))
        }
        // Başka türün kaydı ya da plandan önceki kayıt sayılmaz.
        assertTrue(MissedCheck.missed(planned, AttemptKind.Morning, listOf(Attempt(planned.plusSeconds(60), AttemptKind.Evening, AttemptOutcome.Sent)), null, after, baku))
        assertTrue(MissedCheck.missed(planned, AttemptKind.Morning, listOf(Attempt(planned.minusSeconds(60), AttemptKind.Morning, AttemptOutcome.Sent)), null, after, baku))
        // Kayıt listeden düşse bile "bugün gönderildi" işareti varsa kaçırılmış sayılmaz.
        assertTrue(!MissedCheck.missed(planned, AttemptKind.Morning, emptyList(), LocalDate.of(2026, 10, 29), after, baku))
        assertTrue(MissedCheck.missed(planned, AttemptKind.Morning, emptyList(), LocalDate.of(2026, 10, 28), after, baku))
    }

    @Test
    fun `the window ends at midnight in the device zone`() {
        val late = Instant.parse("2026-10-29T19:30:00Z") // Bakü 23:30
        // 23:30 + 3 saat gece yarısını aşar: pencere 00:00'da biter (Bakü 2026-10-30 00:00 = 20:00Z).
        assertTrue(!MissedCheck.missed(late, AttemptKind.Evening, emptyList(), null, Instant.parse("2026-10-29T19:59:00Z"), baku))
        assertTrue(MissedCheck.missed(late, AttemptKind.Evening, emptyList(), null, Instant.parse("2026-10-29T20:00:00Z"), baku))
    }
}
