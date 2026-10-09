package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.model.PrecipitationNowcast.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Yağış widget'ının cümlesi: başlıyor, diniyor, sürüyor, yok. Cümlenin saati ve Türkçe eki
 * ("20:00'de", "20:30'da", "21:15'te") ayrıca test edilir.
 */
class PrecipitationNowcastTest {

    private val now = LocalDateTime.of(2026, 10, 9, 19, 33)
    private val quarter = LocalDateTime.of(2026, 10, 9, 19, 30)

    @Test
    fun `rain starting later gives the start time`() {
        val nowcast = PrecipitationNowcast.of(forecast(wet = listOf(2, 3, 4, 5)), now)
        assertEquals(Kind.Starting, nowcast.kind)
        assertEquals(LocalDateTime.of(2026, 10, 9, 20, 0), nowcast.at)
        assertFalse(nowcast.isImminent(now))
        assertEquals(8, nowcast.slices.size)
    }

    @Test
    fun `rain stopping gives the stop time`() {
        val nowcast = PrecipitationNowcast.of(forecast(wet = listOf(0, 1, 2), code = 61), now)
        assertEquals(Kind.Stopping, nowcast.kind)
        assertEquals(LocalDateTime.of(2026, 10, 9, 20, 15), nowcast.at)
    }

    @Test
    fun `rain all the way through is continuing`() {
        val nowcast = PrecipitationNowcast.of(forecast(wet = (0..11).toList(), code = 61), now)
        assertEquals(Kind.Continuing, nowcast.kind)
        assertNull(nowcast.at)
    }

    @Test
    fun `no rain in two hours is dry, rain after the horizon does not count`() {
        assertEquals(Kind.Dry, PrecipitationNowcast.of(forecast(wet = emptyList()), now).kind)
        // 21:45 ve sonrası iki saatin dışında.
        assertEquals(Kind.Dry, PrecipitationNowcast.of(forecast(wet = listOf(9, 10, 11)), now).kind)
    }

    @Test
    fun `rain in the current slice is imminent when the station says dry`() {
        // Model şu anki dilimde yağış diyor ama istasyon kuru ölçüyor: "başlamak üzere".
        val nowcast = PrecipitationNowcast.of(forecast(wet = listOf(0, 1)), now, observed = true)
        assertEquals(Kind.Starting, nowcast.kind)
        assertTrue(nowcast.isImminent(now))
    }

    @Test
    fun `without quarter-hour data the sentence is unknown`() {
        val nowcast = PrecipitationNowcast.of(forecast(wet = emptyList(), slices = 0), now)
        assertEquals(Kind.Unknown, nowcast.kind)
    }

    @Test
    fun `snow is told apart from rain`() {
        val nowcast = PrecipitationNowcast.of(forecast(wet = listOf(2), temperature = 0.5), now)
        assertTrue(nowcast.isSnow)
    }

    @Test
    fun `next rain uses hourly data after the quarter-hour data ends`() {
        val start = RainTiming.next(forecast(wet = emptyList(), wetHours = listOf(22)), now)!!
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 30), start.time)
        assertFalse(start.isNow)
        assertTrue(RainTiming.next(forecast(wet = emptyList(), code = 61), now)!!.isNow)
        assertNull(RainTiming.next(forecast(wet = emptyList()), now))
    }

    @Test
    fun `turkish time suffix follows the spoken number`() {
        assertEquals("20:00'de", TurkishSuffix.locativeTime("20:00"))
        assertEquals("20:30'da", TurkishSuffix.locativeTime("20:30"))
        assertEquals("21:15'te", TurkishSuffix.locativeTime("21:15"))
        assertEquals("21:45'te", TurkishSuffix.locativeTime("21:45"))
        assertEquals("22:40'ta", TurkishSuffix.locativeTime("22:40"))
        assertEquals("06:00'da", TurkishSuffix.locativeTime("06:00"))
        assertEquals("09:00'da", TurkishSuffix.locativeTime("09:00"))
        assertEquals("13:00'te", TurkishSuffix.locativeTime("13:00"))
        assertEquals("00:00'da", TurkishSuffix.locativeTime("00:00"))
    }

    @Test
    fun `turkish place suffix follows vowel harmony`() {
        assertEquals("Tuzla Merkez'de", TurkishSuffix.locative("Tuzla Merkez"))
        assertEquals("Pendik'te", TurkishSuffix.locative("Pendik"))
        assertEquals("Kadıköy'de", TurkishSuffix.locative("Kadıköy"))
        assertEquals("Atakum'da", TurkishSuffix.locative("Atakum"))
        assertEquals("Yayla'da", TurkishSuffix.locative("Yayla"))
        assertEquals("Moda'da", TurkishSuffix.locative("Moda"))
        assertEquals("Üsküdar'da", TurkishSuffix.locative("Üsküdar"))
        assertEquals("Beşiktaş'ta", TurkishSuffix.locative("Beşiktaş"))
    }

    /**
     * 19:30'dan başlayan 15 dakikalık dilimler; [wet] dilim numaraları yağışlı. [wetHours]: saatlik
     * tahminde yağışlı saatler (dilimler 22:30'a kadar sürer).
     */
    private fun forecast(
        wet: List<Int>,
        code: Int = 2,
        temperature: Double = 15.0,
        slices: Int = 12,
        wetHours: List<Int> = emptyList(),
    ) = Forecast(
        current = CurrentWeather(
            time = now,
            temperature = temperature,
            apparentTemperature = temperature,
            humidity = 80,
            dewPoint = null,
            windSpeed = 10.0,
            windDirection = null,
            weatherCode = code,
            isDay = false,
            pressure = null,
            uvIndex = null,
            visibility = null,
        ),
        hourly = (0 until 12).map { i ->
            val time = LocalDateTime.of(2026, 10, 9, 19, 0).plusHours(i.toLong())
            HourlyForecast(time, 15.0, if (time.hour in wetHours) 61 else 2, 0, isDay = false)
        },
        daily = emptyList(),
        utcOffsetSeconds = 10_800,
        minutely = (0 until slices).map { i ->
            val start = quarter.plusMinutes(15L * i)
            val mm = if (i in wet) 0.4 else 0.0
            PrecipitationSlice(start, start.plusMinutes(15), mm, isWet = mm > 0)
        },
    )
}
