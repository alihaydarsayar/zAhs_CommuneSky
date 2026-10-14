package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Hata özeti, otomatik hakem ve öğrenen düzeltme. */
class ForecastAccuracyTest {

    /** [days] gün boyunca her gün bir gündüz (14:00) ve bir gece (02:00) eşi; hata sabit. */
    private fun pairs(source: ForecastSource, days: Int, dayError: Double, nightError: Double, lead: Int = 3) =
        (0 until days).flatMap { day ->
            listOf(
                ForecastPair(source, lead, day.toLong(), 14, 20.0 + dayError, 20.0),
                ForecastPair(source, lead, day.toLong(), 2, 10.0 + nightError, 10.0),
            )
        }

    @Test
    fun `the summary gives absolute error, signed bias and day and night apart`() {
        val all = pairs(ForecastSource.Mgm, 12, dayError = 2.0, nightError = -1.0) + pairs(ForecastSource.Mgm, 12, 3.0, 3.0, lead = 24)
        val summary = AccuracyStats.summarize(all, mapOf(ForecastSource.Mgm to listOf(25.0 to 24.0, 20.0 to 22.0))).single()
        assertEquals(1.5, summary.shortTermError!!, 0.001)
        assertEquals(3.0, summary.dayAheadError!!, 0.001)
        assertEquals(0.5, summary.bias!!, 0.001)
        assertEquals(2.0, summary.dayBias!!, 0.001)
        assertEquals(-1.0, summary.nightBias!!, 0.001)
        assertEquals(1.5, summary.nextDayMaxError!!, 0.001)
        assertEquals(12, summary.days)
        // Hiç eş yoksa özet de yok.
        assertEquals(emptyList<SourceAccuracy>(), AccuracyStats.summarize(emptyList()))
    }

    private fun accuracy(source: ForecastSource, dayAhead: Double, days: Int) =
        SourceAccuracy(source, shortTermError = dayAhead, dayAheadError = dayAhead, bias = 0.0, dayBias = 0.0, nightBias = 0.0, nextDayMaxError = null, days = days)

    @Test
    fun `the referee switches only for a clearly better candidate with enough days`() {
        val mgm = accuracy(ForecastSource.Mgm, 2.0, days = 12)
        // %15'ten fazla daha yakın: seçilir.
        assertEquals(ForecastSource.EcmwfIfs, ForecastReferee.choose(ForecastSource.Mgm, listOf(mgm, accuracy(ForecastSource.EcmwfIfs, 1.6, 12))))
        // Yalnız %10 daha iyi: varsayılan kalır.
        assertNull(ForecastReferee.choose(ForecastSource.Mgm, listOf(mgm, accuracy(ForecastSource.EcmwfIfs, 1.8, 12))))
        // 10 gün dolmadan karar verilmez (adayın ya da varsayılanın günü az).
        assertNull(ForecastReferee.choose(ForecastSource.Mgm, listOf(mgm, accuracy(ForecastSource.EcmwfIfs, 1.0, 9))))
        assertNull(ForecastReferee.choose(ForecastSource.Mgm, listOf(mgm.copy(days = 9), accuracy(ForecastSource.EcmwfIfs, 1.0, 12))))
        // Birden çok iyi aday: en iyisi.
        assertEquals(
            ForecastSource.EcmwfAifs,
            ForecastReferee.choose(ForecastSource.Mgm, listOf(mgm, accuracy(ForecastSource.EcmwfIfs, 1.5, 12), accuracy(ForecastSource.EcmwfAifs, 1.2, 12), accuracy(ForecastSource.OpenMeteo, 2.5, 12))),
        )
        // Varsayılanın kaydı yoksa karşılaştırma yapılamaz.
        assertNull(ForecastReferee.choose(ForecastSource.Mgm, listOf(accuracy(ForecastSource.EcmwfIfs, 1.0, 12))))
    }

    @Test
    fun `a steady bias is corrected, day and night apart`() {
        // Gündüz hep 1° sıcak, gece hep 1,5° soğuk tahmin ediliyor.
        val steady = pairs(ForecastSource.Mgm, 8, dayError = 1.0, nightError = -1.5)
        val result = LearnedCorrection.compute(steady, stationKm = 4.0)
        assertEquals(-1.0, result.day, 0.001)
        assertEquals(1.5, result.night, 0.001)
    }

    @Test
    fun `no correction without enough days, a near station and a consistent bias`() {
        val none = LearnedCorrection.Result(0.0, 0.0)
        // 7 günden az.
        assertEquals(none, LearnedCorrection.compute(pairs(ForecastSource.Mgm, 6, 1.0, -1.5), stationKm = 4.0))
        // İstasyon 15 km'den uzak ya da bilinmiyor.
        assertEquals(none, LearnedCorrection.compute(pairs(ForecastSource.Mgm, 8, 1.0, -1.5), stationKm = 15.1))
        assertEquals(none, LearnedCorrection.compute(pairs(ForecastSource.Mgm, 8, 1.0, -1.5), stationKm = null))
        // Tutarsız: bir gün +4°, bir gün −2° (ortalama +1°, ama dağınık).
        val scattered = (0 until 8).map { day -> ForecastPair(ForecastSource.Mgm, 3, day.toLong(), 14, 20.0 + if (day % 2 == 0) 4.0 else -2.0, 20.0) }
        assertEquals(0.0, LearnedCorrection.compute(scattered, stationKm = 4.0).day, 0.0)
        // Önemsiz sapma (0,2°) düzeltilmez.
        assertEquals(none, LearnedCorrection.compute(pairs(ForecastSource.Mgm, 8, 0.2, -0.2), stationKm = 4.0))
    }

    @Test
    fun `the correction never exceeds three degrees`() {
        val result = LearnedCorrection.compute(pairs(ForecastSource.Mgm, 8, dayError = 5.0, nightError = -4.5), stationKm = 4.0)
        assertEquals(-3.0, result.day, 0.0)
        assertEquals(3.0, result.night, 0.0)
    }

    @Test
    fun `after the MGM hour fix the place forgets what it learned from shifted hours`() {
        val extras = PlaceExtras(
            centerId = 93414,
            chosen = ForecastSource.EcmwfIfs,
            dayCorrection = -1.5,
            nightCorrection = 0.6,
            accuracy = listOf(StoredAccuracy(ForecastSource.Mgm, 12, 2.1), StoredAccuracy(ForecastSource.EcmwfIfs, 12, 1.2)),
            decidedDay = 20_000,
            nationalFetchedAt = 1_000,
        )
        val fixed = MgmHourlyFix.forget(extras)
        assertEquals(null, fixed.chosen)
        assertEquals(0.0, fixed.dayCorrection, 0.0)
        assertEquals(0.0, fixed.nightCorrection, 0.0)
        assertEquals(listOf(ForecastSource.EcmwfIfs), fixed.accuracy.map { it.source })
        assertEquals(-1L, fixed.decidedDay)
        assertEquals(0L, fixed.nationalFetchedAt)
        assertEquals(93414, fixed.centerId)
    }
}
