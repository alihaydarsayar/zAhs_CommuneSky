package com.alihaydarsayar.communesky.model.detail

import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.today.Compared
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Ayrıntı sayfaları: grafik verisinin hazırlanması, dünle karşılaştırma cümleleri ve durum sözcükleri. */
class MetricDetailTest {

    private val today = TestForecast.day
    private val yesterday = today.minusDays(1)
    private val now: LocalDateTime = today.atTime(14, 30)

    /** Dün ve bugün; rüzgâr öğleden sonra artıyor, basınç düşüyor. */
    private fun forecast(): Forecast {
        val hours = TestForecast.hours(yesterday) { hour(it, feels = 20.0, wind = 10.0, gust = 18.0, humidity = 40, uv = if (it == 13) 5.0 else 0.0) } +
            TestForecast.hours(today) {
                hour(it, feels = 10.0 + it / 2.0, temperature = 12.0 + it / 2.0, wind = 5.0 + it, gust = 10.0 + 2 * it, humidity = 70, uv = if (it in 11..14) 7.0 else 0.0, mm = if (it == 16) 3.0 else 0.0, chance = if (it == 16) 80 else 10)
                    .copy(pressure = 1020.0 - it, windDirection = 90, dewPoint = 8.0)
            }
        val days = listOf(
            TestForecast.daily(yesterday, min = 14.0, max = 22.0, mm = 0.0, wind = 12.0),
            TestForecast.daily(today, min = 10.0, max = 18.0, mm = 3.0, wind = 28.0),
        ) + (1..7).map { TestForecast.daily(today.plusDays(it.toLong()), min = 9.0, max = 17.0, mm = it.toDouble(), wind = 10.0 + it) }
        return TestForecast.forecast(hours, days, now)
    }

    @Test
    fun `the chart holds the twenty-four hours of today with the now position`() {
        val detail = MetricData.of(Metric.Wind, forecast(), now)
        val chart = detail.chart!!
        assertEquals(24, chart.points.size)
        assertEquals(today.atTime(0, 0), chart.points.first().time)
        assertEquals(today.atTime(23, 0), chart.points.last().time)
        assertEquals(14.5f, chart.nowPosition!!, 1e-6f)
        assertEquals(ChartKind.Line, chart.kind)
        // Eksen sıfırdan başlar ve en kuvvetli hamleyi (56) içine alır; 10'un katına yuvarlanır.
        assertEquals(0.0, chart.min, 1e-9)
        assertEquals(70.0, chart.max, 1e-9)
        assertEquals(19.0, chart.points[14].value!!, 1e-9)
        assertEquals(38.0, chart.points[14].secondary!!, 1e-9)
        assertEquals(90, chart.points[14].direction)
    }

    @Test
    fun `each metric picks its own values and axis`() {
        val forecast = forecast()
        val feels = MetricData.of(Metric.FeelsLike, forecast, now).chart!!
        // Hissedilen 10–21,5 ve gerçek 12–23,5: 2° pay ile 5'in katları.
        assertEquals(5.0, feels.min, 1e-9)
        assertEquals(30.0, feels.max, 1e-9)
        val humidity = MetricData.of(Metric.Humidity, forecast, now).chart!!
        assertEquals(0.0 to 100.0, humidity.min to humidity.max)
        val uv = MetricData.of(Metric.Uv, forecast, now).chart!!
        assertEquals(ChartKind.Bars, uv.kind)
        assertEquals(8.0, uv.max, 1e-9)
        val rain = MetricData.of(Metric.Precipitation, forecast, now)
        assertEquals(ChartKind.Bars, rain.chart!!.kind)
        assertTrue(rain.chart!!.secondaryPercent)
        assertEquals(3.0, rain.chart!!.points[16].value!!, 1e-9)
        assertEquals(80.0, rain.chart!!.points[16].secondary!!, 1e-9)
        assertEquals(3.0, rain.currentSecondary!!, 1e-9)
        val pressure = MetricData.of(Metric.Pressure, forecast, now)
        // 997–1020 hPa: 2 hPa pay, 5'in katları.
        assertEquals(995.0 to 1025.0, pressure.chart!!.min to pressure.chart!!.max)
        // Görüş kilometreye çevrilir.
        assertEquals(20.0, MetricData.of(Metric.Visibility, forecast, now).current!!, 1e-9)
        // Güneşin grafiği yoktur; değeri gün uzunluğudur (07:10–18:30 = 680 dk).
        val sun = MetricData.of(Metric.Sun, forecast, now)
        assertNull(sun.chart)
        assertEquals(680.0, sun.current!!, 1e-9)
    }

    @Test
    fun `the week starts today and has seven days`() {
        val week = MetricData.of(Metric.Precipitation, forecast(), now).week
        assertEquals(7, week.size)
        assertEquals(today, week.first().date)
        assertEquals(3.0, week.first().value!!, 1e-9)
        assertEquals(today.plusDays(6), week.last().date)
        // Hissedilende günün en yükseği ve en düşüğü birlikte.
        val feels = MetricData.of(Metric.FeelsLike, forecast(), now).week.first()
        assertNotNull(feels.value)
        assertNotNull(feels.low)
    }

    @Test
    fun `pressure trend compares now with three hours ago`() {
        // Saatte 1 hPa düşüyor: üç saatte 3 hPa.
        assertEquals(Trend.Falling, MetricData.of(Metric.Pressure, forecast(), now).trend)
        val steady = TestForecast.forecast(TestForecast.hours(today) { hour(it).copy(pressure = 1015.0 + it * 0.4) }, now = now)
        assertEquals(Trend.Steady, MetricData.pressureTrend(steady, now))
        val rising = TestForecast.forecast(TestForecast.hours(today) { hour(it).copy(pressure = 1000.0 + it * 0.5) }, now = now)
        assertEquals(Trend.Rising, MetricData.pressureTrend(rising, now))
        assertNull(MetricData.pressureTrend(TestForecast.forecast(emptyList(), now = now).let { it.copy(current = it.current.copy(pressure = null)) }, now))
    }

    @Test
    fun `comparison levels follow each metric's thresholds`() {
        fun level(metric: Metric, today: Double, yesterday: Double) = MetricData.level(metric, Compared(today, yesterday))
        assertEquals(ComparisonLevel.Same, level(Metric.Wind, 14.0, 10.0))
        assertEquals(ComparisonLevel.Higher, level(Metric.Wind, 15.0, 10.0))
        assertEquals(ComparisonLevel.MuchHigher, level(Metric.Wind, 25.0, 10.0))
        assertEquals(ComparisonLevel.Lower, level(Metric.Wind, 10.0, 15.0))
        assertEquals(ComparisonLevel.MuchLower, level(Metric.Wind, 10.0, 25.0))
        assertEquals(ComparisonLevel.Same, level(Metric.FeelsLike, 20.9, 20.0))
        assertEquals(ComparisonLevel.Higher, level(Metric.FeelsLike, 21.0, 20.0))
        assertEquals(ComparisonLevel.MuchLower, level(Metric.Pressure, 1010.0, 1016.0))
    }

    @Test
    fun `comparison sentences in Turkish and English`() {
        val forecast = forecast()
        fun tr(metric: Metric) = MetricText.comparison(MetricData.of(metric, forecast, now), TestStrings.turkish)
        fun en(metric: Metric) = MetricText.comparison(MetricData.of(metric, forecast, now), TestStrings.english)
        // Rüzgâr 28'e karşı 12; hissedilen dünden düşük; yağış 3 mm'ye karşı 0.
        assertEquals("Dünkünden yüksek", tr(Metric.Wind))
        assertEquals("Higher than yesterday", en(Metric.Wind))
        assertEquals("Dünkünden biraz yüksek", tr(Metric.Precipitation))
        assertEquals("A little higher than yesterday", en(Metric.Precipitation))
        assertEquals("Dünkünden yüksek", tr(Metric.Humidity))
        assertEquals("Gün uzunluğu dünle aynı", tr(Metric.Sun))
        // Dünün verisi yoksa cümle de yoktur.
        val alone = TestForecast.forecast(TestForecast.hours(today), listOf(TestForecast.daily(today)), now)
        assertNull(MetricText.comparison(MetricData.of(Metric.Wind, alone, now), TestStrings.turkish))
        // Gün uzunluğu dakikayla söylenir.
        val sun = MetricDetail(Metric.Sun, 678.0, null, null, Compared(678.0, 680.0), emptyList())
        assertEquals("Gündüz, dünden 2 dk kısa", MetricText.comparison(sun, TestStrings.turkish))
        assertEquals("Daylight is 2 min shorter than yesterday", MetricText.comparison(sun, TestStrings.english))
        assertEquals("Günler kısalıyor", MetricText.status(sun, TestStrings.turkish))
    }

    @Test
    fun `status words`() {
        fun status(metric: Metric, value: Double, secondary: Double? = null, trend: Trend? = null, strings: TestStrings = TestStrings.turkish) =
            MetricText.status(MetricDetail(metric, value, secondary, null, null, emptyList(), trend), strings)

        // Yağış: miktar sıfırken ihtimal yüksekse "Kuru bir gün" denmez.
        assertEquals("Kuru bir gün", status(Metric.Precipitation, 10.0, secondary = 0.0))
        assertEquals("Yağış olabilir", status(Metric.Precipitation, 65.0, secondary = 0.0))
        assertEquals("Hafif yağış", status(Metric.Precipitation, 65.0, secondary = 1.2))
        assertEquals("Sakin", status(Metric.Wind, 1.0))
        assertEquals("Hafif esinti", status(Metric.Wind, 8.0))
        assertEquals("Light breeze", status(Metric.Wind, 8.0, strings = TestStrings.english))
        assertEquals("Kuvvetli rüzgâr", status(Metric.Wind, 35.0))
        assertEquals("Fırtına", status(Metric.Wind, 60.0))
        assertEquals("Yüksek UV", status(Metric.Uv, 6.5))
        assertEquals("High UV", status(Metric.Uv, 6.5, strings = TestStrings.english))
        assertEquals("Düşük UV", status(Metric.Uv, 1.0))
        assertEquals("Normal basınç", status(Metric.Pressure, 1013.0))
        assertEquals("Normal basınç, düşüyor", status(Metric.Pressure, 1013.0, trend = Trend.Falling))
        assertEquals("High pressure, rising", status(Metric.Pressure, 1026.0, trend = Trend.Rising, strings = TestStrings.english))
        assertEquals("Düşük basınç", status(Metric.Pressure, 1000.0))
        assertEquals("Rahat", status(Metric.Humidity, 50.0))
        assertEquals("Çok nemli", status(Metric.Humidity, 85.0))
        assertEquals("Daha soğuk hissettiriyor", status(Metric.FeelsLike, 8.0, secondary = 11.0))
        assertEquals("Gerçeğe yakın", status(Metric.FeelsLike, 10.5, secondary = 11.0))
        assertEquals("Zayıf görüş", status(Metric.Visibility, 0.8))
        assertEquals("Kapalı", status(Metric.Cloud, 95.0))
        assertEquals("Kuru bir gün", status(Metric.Precipitation, 20.0, secondary = 0.0))
        assertEquals("Kuvvetli yağış", status(Metric.Precipitation, 90.0, secondary = 16.0))
    }

    @Test
    fun `an empty forecast gives pages without a chart instead of failing`() {
        val empty = TestForecast.forecast(emptyList(), emptyList(), now)
        for (metric in Metric.entries) {
            val detail = MetricData.of(metric, empty, now)
            assertNull(detail.chart)
            assertTrue(detail.week.isEmpty())
            // Her bilginin başlığı ve açıklaması iki dilde de vardır.
            for (strings in listOf(TestStrings.turkish, TestStrings.english)) {
                assertTrue(strings.get(MetricText.title(metric)).isNotBlank())
                assertTrue(strings.get(MetricText.what(metric)).length > 100)
                assertTrue(strings.get(MetricText.compareCaption(metric)).isNotBlank())
            }
        }
    }
}
