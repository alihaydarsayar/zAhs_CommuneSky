package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dünle karşılaştırma. */
class YesterdayTest {

    private val today = TestForecast.day
    private val yesterday = today.minusDays(1)

    @Test
    fun `less than one degree is the same as yesterday`() {
        assertEquals(YesterdayLine.Same, YesterdayLine.of(0.0))
        assertEquals(YesterdayLine.Same, YesterdayLine.of(0.9))
        assertEquals(YesterdayLine.Same, YesterdayLine.of(-0.9))
        assertEquals(YesterdayLine.Warmer(1), YesterdayLine.of(1.0))
        assertEquals(YesterdayLine.Colder(1), YesterdayLine.of(-1.0))
        assertEquals(YesterdayLine.Colder(4), YesterdayLine.of(-4.4))
        assertEquals(YesterdayLine.Warmer(5), YesterdayLine.of(4.5))
        // Fahrenhayt: 4 °C fark 7 °F okunur.
        assertEquals(YesterdayLine.Colder(7), YesterdayLine.of(-4.0, scale = 1.8))
    }

    @Test
    fun `compares the daily values of today and yesterday`() {
        val hours = TestForecast.hours(yesterday) { hour(it, feels = 20.0, humidity = 40, wind = 10.0) } +
            TestForecast.hours(today) { hour(it, feels = 15.0, humidity = 70, wind = 25.0, mm = 0.5) }
        val days = listOf(
            TestForecast.daily(yesterday, min = 14.0, max = 22.0, mm = 0.0, wind = 12.0),
            TestForecast.daily(today, min = 10.0, max = 18.0, mm = 12.0, wind = 30.0),
        )
        val comparison = YesterdayComparison.of(TestForecast.forecast(hours, days), today.atTime(9, 0))!!
        assertEquals(-4.0, comparison.temperatureDifference!!, 1e-9)
        assertEquals(YesterdayLine.Colder(4), YesterdayLine.of(comparison.temperatureDifference!!))
        assertEquals(-5.0, comparison.feelsLike!!.difference, 1e-9)
        assertEquals(18.0, comparison.wind!!.difference, 1e-9)
        assertEquals(30.0, comparison.humidity!!.difference, 1e-9)
        assertEquals(12.0, comparison.precipitation!!.difference, 1e-9)
        assertEquals(22.0, comparison.temperature!!.yesterday, 1e-9)
    }

    @Test
    fun `falls back to hourly values when the daily ones are missing`() {
        val hours = TestForecast.hours(yesterday) { hour(it, temperature = if (it == 14) 25.0 else 15.0, mm = 1.0) } +
            TestForecast.hours(today) { hour(it, temperature = if (it == 14) 19.0 else 12.0) }
        val comparison = YesterdayComparison.of(TestForecast.forecast(hours, listOf(TestForecast.daily(today))), today.atTime(9, 0))!!
        assertEquals(-6.0, comparison.temperatureDifference!!, 1e-9)
        assertEquals(-24.0, comparison.precipitation!!.difference, 1e-9)
    }

    @Test
    fun `an old cache without yesterday gives no comparison`() {
        val forecast = TestForecast.forecast(TestForecast.hours(today), listOf(TestForecast.daily(today)))
        assertNull(YesterdayComparison.of(forecast, today.atTime(9, 0)))
        // Dünün yalnız birkaç saati varsa saatlik karşılaştırma yapılmaz.
        val partial = TestForecast.forecast(TestForecast.hours(yesterday).takeLast(5) + TestForecast.hours(today), listOf(TestForecast.daily(today)))
        assertNull(YesterdayComparison.of(partial, today.atTime(9, 0)))
    }
}
