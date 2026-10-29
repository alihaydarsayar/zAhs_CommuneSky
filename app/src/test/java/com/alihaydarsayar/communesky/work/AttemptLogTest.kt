package com.alihaydarsayar.communesky.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

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
}
