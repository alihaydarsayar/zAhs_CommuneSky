package com.alihaydarsayar.communesky.model.detail

import com.alihaydarsayar.communesky.model.CandidateSeries
import com.alihaydarsayar.communesky.model.ForecastMerge
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.NationalDay
import com.alihaydarsayar.communesky.model.NationalHour
import com.alihaydarsayar.communesky.model.PlaceExtras
import com.alihaydarsayar.communesky.testing.TestForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Gün sayfası. Örnek: Beşiktaş, 12 Ekim Pazartesi sabah 08:00. Telefonda bugünün ve yarının saatleri var;
 * 4. gün (15 Ekim) sayfası açılınca saatleri ayrıca istenir ve yerin ek verileriyle birleştirilir.
 */
class DayDataTest {

    private val today: LocalDate = TestForecast.day
    private val now = today.atTime(8, 0)
    private val fourth = today.plusDays(3)

    /** Ana tahmin: bugün ve yarının 48 saati, 7 gün. */
    private fun base() = TestForecast.forecast(
        hourly = TestForecast.hours(today) + TestForecast.hours(today.plusDays(1)),
        daily = (0 until 7).map { TestForecast.daily(today.plusDays(it.toLong()), min = 10.0, max = 18.0) },
        now = now,
    )

    /** Open-Meteo'dan ayrıca istenen 4. gün: 14°, yağış ihtimali %40, 15:00'te 1,2 mm. */
    private fun fetched() = TestForecast.hours(fourth) { TestForecast.hour(it, feels = 13.0, temperature = 14.0, chance = 40, mm = if (it == 15) 1.2 else 0.0, date = fourth) } +
        TestForecast.hours(fourth.plusDays(1)) { TestForecast.hour(it, temperature = 9.0, date = fourth.plusDays(1)) }

    /** MGM: saatlik tahmin yalnız iki gün; günlük tahmin beş gün. */
    private fun mgm() = CandidateSeries(
        ForecastSource.Mgm,
        hours = (0 until 16).map { NationalHour(today.atStartOfDay().plusHours(it * 3L).toString(), weatherCode = 3, temperature = 20.0) },
        days = (0 until 5).map { NationalDay(today.plusDays(it.toLong()).toString(), weatherCode = 61, maxTemperature = 25.0 - it, minTemperature = 15.0 - it) },
    )

    /** ECMWF: sekiz günün her saati, hep 22°. */
    private fun ecmwf() = CandidateSeries(
        ForecastSource.EcmwfIfs,
        hours = (0 until 8 * 24).map { NationalHour(today.atStartOfDay().plusHours(it.toLong()).toString(), weatherCode = 2, temperature = 22.0, humidity = 55, windSpeed = 14.0) },
        days = (0 until 8).map { NationalDay(today.plusDays(it.toLong()).toString(), weatherCode = 2, maxTemperature = 23.0, minTemperature = 12.0) },
    )

    private val besiktas = PlaceExtras(queryLatitude = 41.04, queryLongitude = 29.01, centerId = 93414)

    @Test
    fun `today and tomorrow come from the hours on the phone, later days do not`() {
        val forecast = base()
        assertEquals(24, DayData.localHours(forecast, today)!!.size)
        assertEquals(24, DayData.localHours(forecast, today.plusDays(1))!!.size)
        assertNull(DayData.localHours(forecast, today.plusDays(2)))
        assertNull(DayData.localHours(forecast, fourth))
    }

    @Test
    fun `the fourth day takes its hours from ECMWF when MGM does not reach that far`() {
        val extras = besiktas.copy(national = mgm(), models = listOf(ecmwf()))
        val (hours, source) = DayData.mergeFetched(fetched(), base(), extras, fourth)

        assertEquals(ForecastSource.EcmwfIfs, source)
        assertEquals(24, hours.size)
        assertTrue(hours.all { it.time.toLocalDate() == fourth })
        // Sıcaklık, simge, nem ve rüzgâr ECMWF'ten; yağış ihtimali ve miktarı ana tahminden.
        assertEquals(22.0, hours[15].temperature, 0.001)
        assertEquals(2, hours[15].weatherCode)
        assertEquals(55, hours[15].humidity)
        assertEquals(14.0, hours[15].windSpeed!!, 0.001)
        assertEquals(40, hours[15].precipitationProbability)
        assertEquals(1.2, hours[15].precipitation!!, 0.001)
    }

    @Test
    fun `without ECMWF the fourth day stays with the main forecast`() {
        val (hours, source) = DayData.mergeFetched(fetched(), base(), besiktas.copy(national = mgm()), fourth)
        assertEquals(ForecastSource.OpenMeteo, source)
        assertEquals(14.0, hours[15].temperature, 0.001)
        assertEquals(40, hours[15].precipitationProbability)
    }

    @Test
    fun `a place with no extra data keeps the main forecast`() {
        val (hours, source) = DayData.mergeFetched(fetched(), base(), null, fourth)
        assertEquals(ForecastSource.OpenMeteo, source)
        assertEquals(24, hours.size)
    }

    @Test
    fun `the learned correction also applies to the fetched hours`() {
        val extras = besiktas.copy(national = mgm(), models = listOf(ecmwf()), dayCorrection = -1.0, nightCorrection = 0.5)
        val (hours, _) = DayData.mergeFetched(fetched(), base(), extras, fourth)
        assertEquals(21.0, hours[15].temperature, 0.001)   // gündüz
        assertEquals(22.5, hours[2].temperature, 0.001)    // gece
    }

    @Test
    fun `the page shows the same high and low as the row in the 7-day card`() {
        val extras = besiktas.copy(national = mgm(), models = listOf(ecmwf()))
        // Ana ekranın gördüğü tahmin: günlük değerler MGM'den (4. gün: en yüksek 22°, en düşük 12°).
        val merged = ForecastMerge.merge(base(), extras, now).forecast
        val row = merged.daily.first { it.date == fourth }
        assertEquals(22.0, row.maxTemperature, 0.001)

        // Sayfanın günü, kartın satırındaki nesnenin ta kendisidir; saatler başka kaynaktan gelse de değişmez.
        val day = DayData.days(merged, now)[3]
        val (hours, _) = DayData.mergeFetched(fetched(), merged, extras, fourth)
        val detail = DayData.of(day, hours, now)
        assertEquals(row, detail.day)
        assertEquals(row.maxTemperature, detail.day.maxTemperature, 0.0)
        assertEquals(row.minTemperature, detail.day.minTemperature, 0.0)
        assertEquals(row.weatherCode, detail.day.weatherCode)
    }

    @Test
    fun `charts and the day's values are built from the hours`() {
        val day = TestForecast.daily(fourth, min = 10.0, max = 18.0, mm = 1.2)
        val hours = TestForecast.hours(fourth) {
            TestForecast.hour(it, feels = 10.0 + it / 4.0, temperature = 11.0 + it / 4.0, chance = 40, mm = if (it == 15) 1.2 else 0.0, humidity = 40 + it, wind = 10.0 + it, gust = 20.0 + it, date = fourth)
        }
        val detail = DayData.of(day, hours, now)

        assertEquals(false, detail.isToday)
        val temperature = detail.temperature!!
        assertEquals(24, temperature.points.size)
        assertNull(temperature.nowPosition)
        assertEquals(11.0, temperature.points[0].value!!, 0.001)
        assertEquals(10.0, temperature.points[0].secondary!!, 0.001)
        assertTrue(temperature.min <= 10.0 && temperature.max >= 16.75)
        // Yağış: çubuk miktar, ikinci değer ihtimal (yüzde).
        val rain = detail.precipitation!!
        assertEquals(ChartKind.Bars, rain.kind)
        assertTrue(rain.secondaryPercent)
        assertEquals(1.2, rain.points[15].value!!, 0.001)
        assertEquals(40.0, rain.points[15].secondary!!, 0.001)
        // Rüzgâr: hamle ikinci değer.
        assertEquals(43.0, detail.wind!!.points[23].secondary!!, 0.001)
        assertEquals(40, detail.humidityLow)
        assertEquals(63, detail.humidityHigh)
        assertEquals(1.2, detail.precipitationTotal!!, 0.001)
        assertEquals(4.0, detail.uvMax!!, 0.001)
        // Gün doğumu 07:10, batımı 18:30.
        assertEquals(680.0, detail.dayLengthMinutes!!, 0.001)
    }

    @Test
    fun `today's charts mark the present hour`() {
        val detail = DayData.of(TestForecast.daily(today), TestForecast.hours(today), today.atTime(8, 30))
        assertTrue(detail.isToday)
        assertEquals(8.5f, detail.temperature!!.nowPosition!!, 0.001f)
    }

    @Test
    fun `without hours only the day's summary remains`() {
        // İnternet yok: grafikler yok; günün değerleri günlük tahminden.
        val detail = DayData.of(TestForecast.daily(fourth, min = 10.0, max = 18.0, mm = 3.0), emptyList(), now)
        assertNull(detail.temperature)
        assertNull(detail.precipitation)
        assertNull(detail.wind)
        assertEquals(18.0, detail.day.maxTemperature, 0.0)
        assertEquals(3.0, detail.precipitationTotal!!, 0.001)
        assertEquals(12.0, detail.windMax!!, 0.001)
        assertNotNull(detail.dayLengthMinutes)
    }

    @Test
    fun `an hour tapped in the hourly card is selected on the page`() {
        val hours = TestForecast.hours(today)
        assertEquals(15, DayData.indexOfHour(hours, 15))
        assertNull(DayData.indexOfHour(hours, null))
        assertNull(DayData.indexOfHour(hours.take(10), 15))
    }

    @Test
    fun `the pages are the seven days of the card, starting today`() {
        val days = DayData.days(base(), now)
        assertEquals(7, days.size)
        assertEquals(today, days.first().date)
    }
}
