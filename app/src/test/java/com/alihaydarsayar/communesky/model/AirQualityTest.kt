package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.data.remote.AirHourlyDto
import com.alihaydarsayar.communesky.data.remote.AirQualityDto
import com.alihaydarsayar.communesky.lifestyle.RunTip
import com.alihaydarsayar.communesky.lifestyle.TipStatus
import com.alihaydarsayar.communesky.lifestyle.TodayAdvice
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestStrings
import com.alihaydarsayar.communesky.ui.detail.advice
import com.alihaydarsayar.communesky.ui.detail.airChart
import com.alihaydarsayar.communesky.ui.detail.label
import com.alihaydarsayar.communesky.work.AirQualityNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Hava kalitesi: indeks basamakları, veri hazırlığı, bildirim kuralı ve koşu önerisine etkisi. */
class AirQualityTest {

    private val today = TestForecast.day

    /** Üç günün saatlik verisi: indeks saatle birlikte artıyor. */
    private fun air(value: (day: Int, hour: Int) -> Int? = { day, hour -> 10 + day * 20 + hour }) = AirQuality(
        (0..2).flatMap { day -> (0..23).map { hour -> AirHour(today.plusDays(day.toLong()).atTime(hour, 0).toString(), value(day, hour), pm25 = 12.0) } },
    )

    @Test
    fun `scale limits of the European index`() {
        assertEquals(AqiLevel.Good, AqiLevel.of(0))
        assertEquals(AqiLevel.Good, AqiLevel.of(19))
        assertEquals(AqiLevel.Fair, AqiLevel.of(20))
        assertEquals(AqiLevel.Fair, AqiLevel.of(39))
        assertEquals(AqiLevel.Moderate, AqiLevel.of(40))
        assertEquals(AqiLevel.Moderate, AqiLevel.of(59))
        assertEquals(AqiLevel.Poor, AqiLevel.of(60))
        assertEquals(AqiLevel.Poor, AqiLevel.of(79))
        assertEquals(AqiLevel.VeryPoor, AqiLevel.of(80))
        assertEquals(AqiLevel.VeryPoor, AqiLevel.of(99))
        assertEquals(AqiLevel.ExtremelyPoor, AqiLevel.of(100))
        assertEquals(AqiLevel.ExtremelyPoor, AqiLevel.of(400))
        assertEquals(AqiLevel.Good, AqiLevel.of(-3))
        // "Kötü" ve üstü: koşu önerilmez, bildirim gönderilebilir.
        assertEquals(listOf(false, false, false, true, true, true), AqiLevel.entries.map { it.isPoor })
    }

    @Test
    fun `the current value is the hour of now and old data is not shown`() {
        val quality = air()
        assertEquals(24, quality.at(today.atTime(14, 40))!!.aqi)
        // Üç günün sonundan 2 saat sonra: son değer hâlâ geçerli; 5 saat sonra artık eski.
        val end = today.plusDays(2).atTime(23, 0)
        assertEquals(73, quality.at(end.plusHours(2))!!.aqi)
        assertNull(quality.at(end.plusHours(5)))
        assertNull(AirQuality().at(today.atTime(10, 0)))
    }

    @Test
    fun `chart data and the three day summary`() {
        val quality = air()
        val now: LocalDateTime = today.atTime(14, 40)
        val hours = quality.upcoming(now)
        assertEquals(48, hours.size)
        assertEquals(today.atTime(14, 0), hours.first().at)
        val chart = airChart(hours)
        assertEquals(48, chart.points.size)
        assertEquals(0f, chart.nowPosition)
        assertEquals(0.0, chart.min, 1e-9)
        // En yüksek değer 73: eksen en az 100.
        assertEquals(100.0, chart.max, 1e-9)
        assertEquals(140.0, airChart(listOf(AirHour(now.toString(), 130), AirHour(now.plusHours(1).toString(), 90))).max, 1e-9)
        val days = quality.days(today)
        assertEquals(listOf(33, 53, 73), days.map { it.aqi })
        assertEquals(listOf(AqiLevel.Fair, AqiLevel.Moderate, AqiLevel.Poor), days.map { it.level })
    }

    @Test
    fun `the response is read hour by hour and missing values stay empty`() {
        val dto = AirQualityDto(AirHourlyDto(time = listOf("2026-10-12T00:00", "2026-10-12T01:00"), aqi = listOf(62.6, null), pm25 = listOf(56.6), co = listOf(317.0, 300.0)))
        val quality = dto.toModel()
        assertEquals(63, quality.hours[0].aqi)
        assertNull(quality.hours[1].aqi)
        assertEquals(56.6, quality.hours[0].pm25!!, 1e-9)
        assertNull(quality.hours[1].pm25)
        assertEquals(300.0, quality.hours[1].co!!, 1e-9)
        assertTrue(AirQualityDto().toModel().hours.isEmpty())
    }

    @Test
    fun `a place is announced once a day when its air turns poor`() {
        val levels = mapOf(1L to AqiLevel.Poor, 2L to AqiLevel.Moderate, 3L to AqiLevel.VeryPoor, 4L to null)
        assertEquals(listOf(1L, 3L), AirQualityNotifier.pending(levels, emptySet()))
        assertEquals(listOf(3L), AirQualityNotifier.pending(levels, setOf(1L)))
        assertTrue(AirQualityNotifier.pending(levels, setOf(1L, 3L)).isEmpty())
    }

    @Test
    fun `poor air makes the run tip unsuitable`() {
        val forecast = TestForecast.forecast(TestForecast.hours(today) { TestForecast.hour(it, feels = 15.0) }, now = today.atTime(8, 0))
        val clean = TodayAdvice.of(forecast, today.atTime(8, 0)).tips.first { it.tip == RunTip }
        assertEquals(TipStatus.Good, clean.result.status)
        val smog = TodayAdvice.of(forecast, today.atTime(8, 0), airQualityPoor = true).tips.first { it.tip == RunTip }
        assertEquals(TipStatus.Poor, smog.result.status)
        assertFalse(clean.result.text == smog.result.text)
    }

    @Test
    fun `every level has a name and advice in both languages`() {
        assertEquals("Kötü", TestStrings.turkish.get(AqiLevel.Poor.label))
        assertEquals("Poor", TestStrings.english.get(AqiLevel.Poor.label))
        assertEquals("Hassas gruplar uzun süre dışarıda kalmasın. Herkes dışarıdaki yorucu etkinlikleri azaltsın.", TestStrings.turkish.get(AqiLevel.Poor.advice))
        for (level in AqiLevel.entries) {
            for (strings in listOf(TestStrings.turkish, TestStrings.english)) {
                assertTrue(strings.get(level.label).isNotBlank())
                assertTrue(strings.get(level.advice).length > 20)
            }
        }
    }
}
