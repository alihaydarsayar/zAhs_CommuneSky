package com.alihaydarsayar.communesky.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SkyCorrectionTest {

    private val day = LocalDate.of(2026, 10, 8)
    private val sunrise = day.atTime(7, 6)
    private val sunset = day.atTime(18, 34)

    private fun hourly(code: Int, low: Int, mid: Int, high: Int, sunshine: Double?, hour: Int) =
        SkyCorrection.hourlyCode(code, low, mid, high, sunshine, day.atTime(hour, 0), sunrise, sunset)

    @Test
    fun `thin cirrus with full sunshine is not overcast`() {
        // Tuzla, 8 Ekim 13:00: Open-Meteo "kapalı" diyordu ama güneş bütün saat parlıyordu.
        assertEquals(1, hourly(code = 3, low = 0, mid = 9, high = 100, sunshine = 3600.0, hour = 13))
    }

    @Test
    fun `mid clouds with sunshine become partly cloudy`() {
        assertEquals(2, hourly(code = 3, low = 0, mid = 94, high = 94, sunshine = 3600.0, hour = 16))
    }

    @Test
    fun `no sunshine stays overcast`() {
        assertEquals(3, hourly(code = 3, low = 90, mid = 80, high = 50, sunshine = 0.0, hour = 12))
    }

    @Test
    fun `near sunset only cloud layers count`() {
        // 17:00–18:00 güneş alçak; düşük güneşlenme bulut sanılmasın, katmanlara bakılır.
        assertEquals(3, hourly(code = 3, low = 0, mid = 89, high = 96, sunshine = 517.0, hour = 17))
        assertEquals(2, hourly(code = 3, low = 0, mid = 10, high = 100, sunshine = 100.0, hour = 17))
    }

    @Test
    fun `correction never makes the sky darker`() {
        assertEquals(0, hourly(code = 0, low = 80, mid = 80, high = 80, sunshine = 0.0, hour = 12))
    }

    @Test
    fun `precipitation codes are untouched`() {
        assertEquals(63, hourly(code = 63, low = 0, mid = 0, high = 0, sunshine = 3600.0, hour = 12))
    }

    @Test
    fun `daily code follows the share of sunshine`() {
        assertEquals(1, SkyCorrection.dailyCode(3, sunshineSeconds = 38224.0, daylightSeconds = 40778.0))
        assertEquals(2, SkyCorrection.dailyCode(3, sunshineSeconds = 29497.0, daylightSeconds = 41250.0))
        assertEquals(0, SkyCorrection.dailyCode(0, sunshineSeconds = 38365.0, daylightSeconds = 41093.0))
        assertEquals(80, SkyCorrection.dailyCode(80, sunshineSeconds = 35000.0, daylightSeconds = 40600.0))
    }

    @Test
    fun `short morning fog does not define a sunny day`() {
        assertEquals(2, SkyCorrection.dailyCode(45, sunshineSeconds = 30000.0, daylightSeconds = 41000.0))
        assertEquals(45, SkyCorrection.dailyCode(45, sunshineSeconds = 5000.0, daylightSeconds = 41000.0))
    }
}
