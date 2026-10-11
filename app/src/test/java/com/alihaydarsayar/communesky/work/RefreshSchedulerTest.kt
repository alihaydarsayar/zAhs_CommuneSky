package com.alihaydarsayar.communesky.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Saatlik güncelleme saat başına hizalanır: widget'lardaki "Şimdi" sütunu saat değişince yenilenir. */
class RefreshSchedulerTest {

    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test
    fun `the next run is just after the next full hour`() {
        // 14:23 → 15:00:20 (İstanbul, UTC+3).
        assertEquals(Instant.parse("2026-10-11T12:00:20Z"), RefreshScheduler.nextRun(Instant.parse("2026-10-11T11:23:00Z"), istanbul))
        // Saat başından hemen sonra çalıştıysa bir sonraki saat başı.
        assertEquals(Instant.parse("2026-10-11T13:00:20Z"), RefreshScheduler.nextRun(Instant.parse("2026-10-11T12:00:45Z"), istanbul))
    }

    @Test
    fun `a full hour that is only minutes away is skipped`() {
        // 14:55'te çalıştı: 15:00 çok yakın, 16:00'da çalışır (iki güncelleme art arda yapılmaz).
        assertEquals(Instant.parse("2026-10-11T13:00:20Z"), RefreshScheduler.nextRun(Instant.parse("2026-10-11T11:55:00Z"), istanbul))
    }

    @Test
    fun `the hour is the phone's hour, also in half-hour time zones`() {
        // Hindistan (UTC+5:30): 10:10 yerel → 11:00:20 yerel = 05:30:20 UTC.
        assertEquals(Instant.parse("2026-10-11T05:30:20Z"), RefreshScheduler.nextRun(Instant.parse("2026-10-11T04:40:00Z"), ZoneId.of("Asia/Kolkata")))
    }
}
