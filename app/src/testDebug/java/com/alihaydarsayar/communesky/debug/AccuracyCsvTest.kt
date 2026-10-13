package com.alihaydarsayar.communesky.debug

import com.alihaydarsayar.communesky.data.accuracy.ForecastRecordEntity
import com.alihaydarsayar.communesky.data.accuracy.ObservedRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AccuracyCsvTest {

    // 2026-10-10 12:00 UTC, 1970'ten beri saat.
    private val hour = 1_791_633_600L / 3600

    @Test
    fun `rows carry the source, lead, target hour and value`() {
        // Beşiktaş: 41,043 / 29,009 → 0,1 dereceye yuvarlanır.
        val csv = AccuracyCsv.build(
            places = mapOf(3L to (41.043 to 29.009)),
            forecasts = listOf(ForecastRecordEntity(3, "MGM", "H", hour, 24, 17.456)),
            observed = listOf(ObservedRecordEntity(3, hour, 18.2, 1.234)),
        ).trim().lines()
        assertEquals(AccuracyCsv.HEADER, csv[0])
        assertEquals("F,3,41.0,29.0,MGM,H,24,2026-10-10T12:00,17.46,", csv[1])
        assertEquals("O,3,41.0,29.0,,,,2026-10-10T12:00,18.20,1.23", csv[2])
    }

    @Test
    fun `every row has as many columns as the header`() {
        val csv = AccuracyCsv.build(
            places = emptyMap(),
            forecasts = listOf(ForecastRecordEntity(0, "ECMWF", "X", hour, 24, 21.0)),
            observed = listOf(ObservedRecordEntity(0, hour, 20.0, 4.0)),
        ).trim().lines()
        val columns = AccuracyCsv.HEADER.split(',').size
        csv.forEach { assertEquals(it, columns, it.split(',').size) }
    }

    @Test
    fun `numbers use a dot whatever the phone language`() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("41.0", AccuracyCsv.coordinate(41.043))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
