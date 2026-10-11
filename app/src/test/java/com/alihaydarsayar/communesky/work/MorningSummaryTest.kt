package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Sabah özeti: bildirim metni ve alarmın zamanı. */
class MorningSummaryTest {

    private val today = TestForecast.day
    private val morning: LocalDateTime = today.atTime(7, 30)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private fun text(strings: TestStrings, min: Double = 9.0, max: Double = 17.0, each: (Int) -> com.alihaydarsayar.communesky.model.HourlyForecast) =
        MorningSummary.text(
            TestForecast.forecast(TestForecast.hours(today, each), listOf(TestForecast.daily(min = min, max = max))),
            morning, 0.0, strings, { it.roundToInt() }, { it.format(clock) },
        )

    @Test
    fun `a rainy day in Turkish and English`() {
        val rainy: (Int) -> com.alihaydarsayar.communesky.model.HourlyForecast = {
            hour(it, feels = 13.0, chance = if (it >= 15) 70 else 10, mm = if (it >= 15) 0.3 else 0.0)
        }
        assertEquals("Bugün 9–17°, 15:00'te yağmur. Kazak ya da ince mont giy; şemsiye al.", text(TestStrings.turkish, each = rainy))
        assertEquals("Today 9–17°, rain at 15:00. Wear a sweater or a light jacket; take an umbrella.", text(TestStrings.english, each = rainy))
    }

    @Test
    fun `the Turkish suffix follows the hour`() {
        fun at(hourOfDay: Int) = text(TestStrings.turkish) { hour(it, feels = 13.0, chance = if (it == hourOfDay) 80 else 0) }!!
        assertEquals("Bugün 9–17°, 09:00'da yağmur. Kazak ya da ince mont giy; şemsiye al.", at(9))
        assertEquals("Bugün 9–17°, 20:00'de yağmur. Kazak ya da ince mont giy; şemsiye al.", at(20))
        assertEquals("Bugün 9–17°, 16:00'da yağmur. Kazak ya da ince mont giy; şemsiye al.", at(16))
    }

    @Test
    fun `a dry day has no rain part`() {
        assertEquals("Bugün 12–24°. Tişört ve ince pantolon yeter.", text(TestStrings.turkish, min = 12.0, max = 24.0) { hour(it, feels = 23.0) })
        assertEquals("Today 12–24°. A T-shirt and light trousers will do.", text(TestStrings.english, min = 12.0, max = 24.0) { hour(it, feels = 23.0) })
    }

    @Test
    fun `rain before the summary is not announced`() {
        // 05:00'teki yağmur geçmiştir; özet onu söylemez.
        val earlier = text(TestStrings.turkish) { hour(it, feels = 13.0, chance = if (it == 5) 90 else 0) }
        assertEquals("Bugün 9–17°. Kazak ya da ince mont giy.", earlier)
    }

    @Test
    fun `snow is called snow`() {
        val snowy = text(TestStrings.english, min = -4.0, max = 1.0) {
            hour(it, feels = -5.0, chance = if (it >= 11) 80 else 0).copy(weatherCode = if (it >= 11) 73 else 3)
        }
        assertEquals("Today -4–1°, snow at 11:00. Wear a heavy coat, scarf, hat and gloves; take an umbrella.", snowy)
    }

    @Test
    fun `no daily data means no summary`() {
        val forecast = TestForecast.forecast(TestForecast.hours(today), emptyList())
        assertNull(MorningSummary.text(forecast, morning, 0.0, TestStrings.turkish, { it.roundToInt() }, { it.format(clock) }))
    }

    @Test
    fun `the alarm window starts fifteen minutes before the chosen time`() {
        val minutes = MorningSummary.DEFAULT_MINUTES
        assertEquals(today.atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(6, 0), minutes))
        // Pencere başladıktan sonra (alarm çaldığında) bir sonraki alarm yarın içindir: günde bir uyanma.
        assertEquals(today.plusDays(1).atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(7, 15), minutes))
        assertEquals(today.plusDays(1).atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(7, 40), minutes))
        assertEquals(today.plusDays(1).atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(23, 0), minutes))
        // Gece yarısından hemen sonraki bir saat seçilirse pencere bir önceki günün sonunda başlar.
        assertEquals(today.atTime(23, 50), MorningSummary.nextWindowStart(today.atTime(12, 0), 5))
    }
}
