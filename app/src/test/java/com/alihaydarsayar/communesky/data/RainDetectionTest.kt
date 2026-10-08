package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.PrecipitationSlice
import com.alihaydarsayar.communesky.model.RainOutlook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RainDetectionTest {

    private fun current(code: Int, mm: Double?, showers: Double? = 0.0, snow: Double? = 0.0) =
        RainDetection.code(code, mm, showers, snow, sliceHours = 0.25)

    @Test
    fun `partly cloudy with real rain becomes rain`() {
        // Atakum, 8 Ekim 20:54: kod 2 geliyordu, istasyonda sağanak vardı.
        assertEquals(81, current(code = 2, mm = 0.65, showers = 0.65))
        assertEquals(61, current(code = 2, mm = 0.3, showers = 0.0))
    }

    @Test
    fun `model noise does not turn the sky rainy`() {
        assertEquals(2, current(code = 2, mm = 0.02))
        assertEquals(0, current(code = 0, mm = 0.0))
        assertEquals(1, current(code = 1, mm = null))
    }

    @Test
    fun `existing wet codes are never changed`() {
        assertEquals(95, current(code = 95, mm = 0.0))
        assertEquals(45, current(code = 45, mm = 2.0))
        assertEquals(61, current(code = 61, mm = 5.0))
    }

    @Test
    fun `intensity follows the hourly rate`() {
        assertEquals(51, RainDetection.code(3, 0.3, 0.0, 0.0, sliceHours = 1.0))
        assertEquals(61, RainDetection.code(3, 1.5, 0.0, 0.0, sliceHours = 1.0))
        assertEquals(63, RainDetection.code(3, 5.0, 0.0, 0.0, sliceHours = 1.0))
        assertEquals(65, RainDetection.code(3, 9.0, 0.0, 0.0, sliceHours = 1.0))
        assertEquals(82, RainDetection.code(3, 9.0, 8.0, 0.0, sliceHours = 1.0))
    }

    @Test
    fun `snowfall becomes snow`() {
        assertEquals(73, RainDetection.code(3, 2.0, 0.0, 1.4, sliceHours = 1.0))
        assertEquals(85, RainDetection.code(3, 1.0, 1.0, 0.7, sliceHours = 1.0))
    }

    // --- Yağış uyarısı ---

    private val day = LocalDate.of(2026, 10, 8)

    private fun forecast(currentCode: Int, wetSlices: Set<Int>): Forecast {
        val slices = (0 until 12).map { i ->
            val end = day.atTime(21, 0).plusMinutes(15L * (i + 1))
            val wet = i in wetSlices
            PrecipitationSlice(end.minusMinutes(15), end, if (wet) 0.5 else 0.0, isWet = wet)
        }
        return Forecast(
            current = CurrentWeather(day.atTime(21, 0), 18.0, 18.0, 90, null, 5.0, null, currentCode, false, null, null, null),
            hourly = emptyList(),
            daily = emptyList(),
            utcOffsetSeconds = 0,
            minutely = slices,
        )
    }

    private fun at(minute: Int): LocalDateTime = day.atTime(21, minute)

    @Test
    fun `rain starting later is announced`() {
        val outlook = forecast(currentCode = 2, wetSlices = setOf(2, 3)).rainOutlook(at(5))
        assertEquals(RainOutlook(RainOutlook.Kind.Starting, 25, isSnow = false), outlook)
    }

    @Test
    fun `rain stopping is announced while it rains`() {
        val outlook = forecast(currentCode = 61, wetSlices = setOf(0, 1)).rainOutlook(at(0))
        assertEquals(RainOutlook(RainOutlook.Kind.Stopping, 30, isSnow = false), outlook)
    }

    @Test
    fun `no change means no message`() {
        assertNull(forecast(currentCode = 2, wetSlices = emptySet()).rainOutlook(at(0)))
        assertNull(forecast(currentCode = 61, wetSlices = (0 until 12).toSet()).rainOutlook(at(0)))
    }

    @Test
    fun `empty minutely data from an old cache gives no message`() {
        val old = forecast(currentCode = 2, wetSlices = setOf(1)).copy(minutely = emptyList())
        assertNull(old.rainOutlook(at(0)))
    }
}
