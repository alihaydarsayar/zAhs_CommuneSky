package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.model.today.DailySummary
import com.alihaydarsayar.communesky.model.today.DayPart
import com.alihaydarsayar.communesky.model.today.SummaryEvent
import com.alihaydarsayar.communesky.model.today.SummaryKind
import com.alihaydarsayar.communesky.model.today.SummaryText
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Sabah ve akşam özeti: başlık, önemli olay, dilimler, satırlar ve alarmın zamanı. Örnek yer: Beşiktaş. */
class MorningSummaryTest {

    private val today = TestForecast.day
    private val tomorrow = today.plusDays(1)
    private val yesterday = today.minusDays(1)
    private val morning: LocalDateTime = today.atTime(7, 30)
    private val evening: LocalDateTime = today.atTime(21, 0)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private fun summary(
        kind: SummaryKind = SummaryKind.Morning,
        strings: TestStrings = TestStrings.turkish,
        alerts: List<WeatherAlert> = emptyList(),
        yesterdayMax: Double = 19.0,
        todayMax: Double = 19.0,
        tomorrowMax: Double = 16.0,
        each: (Int) -> HourlyForecast = { hour(it, feels = 13.0) },
        tomorrowEach: (Int) -> HourlyForecast = { hour(it, feels = 11.0) },
    ): SummaryText? {
        val forecast = TestForecast.forecast(
            TestForecast.hours(today, each) + TestForecast.hours(tomorrow, tomorrowEach),
            listOf(
                TestForecast.daily(yesterday, min = 11.0, max = yesterdayMax),
                TestForecast.daily(today, min = 12.0, max = todayMax),
                TestForecast.daily(tomorrow, min = 10.0, max = tomorrowMax),
            ),
        )
        val now = if (kind == SummaryKind.Morning) morning else evening
        return DailySummary.of(kind, forecast, alerts, now, 0.0, strings, { it.roundToInt() }, { it.format(clock) })
    }

    private val afternoonShowers: (Int) -> HourlyForecast = {
        hour(it, feels = 13.0, chance = if (it in 14..17) 80 else 10, mm = if (it in 14..17) 1.5 else 0.0)
            .copy(weatherCode = if (it in 14..17) 81 else 3)
    }

    @Test
    fun `a morning with afternoon showers`() {
        val text = summary(each = afternoonShowers)!!
        assertEquals("12–19° · öğleden sonra sağanak", text.title)
        assertTrue(text.event is SummaryEvent.Precipitation)
        assertEquals("Sağanak 14:00–18:00 arası.", text.lines.first())
        assertEquals(listOf(DayPart.Morning, DayPart.Midday, DayPart.Evening), text.slices.map { it.part })
        assertEquals(81, text.slices[1].weatherCode)
        assertEquals(80, text.slices[1].chance)
        assertNull(text.slices[0].chance)
        // Kapalı görünümün satırı giyim önerisi; şemsiye de orada.
        assertTrue(text.body, text.body.contains("şemsiye"))
    }

    @Test
    fun `the same morning in English and French`() {
        assertEquals("12–19° · showers in the afternoon", summary(strings = TestStrings.english, each = afternoonShowers)!!.title)
        assertEquals("12–19° · averses l’après-midi", summary(strings = TestStrings.french, each = afternoonShowers)!!.title)
    }

    @Test
    fun `a warning comes first and is shown in the summary`() {
        val alert = WeatherAlert("mgm-1-orange", AlertLevel.Orange, listOf(AlertEvent.Thunderstorm), "", "2026-10-12T12:00", "2026-10-12T23:59", "MGM")
        val text = summary(alerts = listOf(alert), each = afternoonShowers)!!
        assertEquals(alert, text.warning)
        // Başlıkta uyarının olay adı (MGM sitesindeki terim).
        assertEquals("12–19° · gök gürültülü sağanak yağış uyarısı", text.title)
    }

    @Test
    fun `thunder comes before rain`() {
        val text = summary(each = { hour(it, feels = 13.0, chance = if (it in 15..16) 70 else 0).copy(weatherCode = if (it in 15..16) 95 else 2) })!!
        assertTrue(text.event is SummaryEvent.Thunder)
        assertEquals("12–19° · öğleden sonra gök gürültülü sağanak", text.title)
    }

    @Test
    fun `rain before the summary is not announced`() {
        val text = summary(each = { hour(it, feels = 13.0, chance = if (it == 5) 90 else 0) })!!
        assertTrue(text.event !is SummaryEvent.Precipitation)
    }

    @Test
    fun `a calm day says the sky, a big change says the change`() {
        assertEquals("12–19° · parçalı bulutlu", summary(each = { hour(it, feels = 16.0, code = 0) })!!.title)
        assertEquals("12–19° · dünden 6° serin", summary(yesterdayMax = 25.0, each = { hour(it, feels = 16.0, code = 0) })!!.title)
        // Dünle farkın satırı (1°'den büyükse).
        assertTrue(summary(yesterdayMax = 22.0)!!.lines.contains("Dünden 3° daha soğuk."))
    }

    @Test
    fun `strong wind and high UV`() {
        assertEquals("12–19° · kuvvetli rüzgâr", summary(each = { hour(it, feels = 16.0, gust = if (it == 13) 62.0 else 20.0, code = 1) })!!.title)
        assertEquals("12–19° · UV çok yüksek", summary(each = { hour(it, feels = 25.0, uv = if (it == 13) 9.0 else 3.0, code = 0) })!!.title)
    }

    @Test
    fun `the evening summary tells about tomorrow`() {
        val text = summary(
            kind = SummaryKind.Evening,
            tomorrowEach = { hour(it, feels = 10.0, chance = if (it in 6..9) 70 else 10, mm = if (it in 6..9) 0.8 else 0.0).copy(weatherCode = if (it in 6..9) 61 else 3) },
        )!!
        assertEquals(tomorrow, text.date)
        assertEquals("Yarın 10–16° · sabah yağmur", text.title)
        // Kapalı görünüm: bugünle fark, sonra giyim.
        assertTrue(text.body, text.body.startsWith("Bugünden 3° daha soğuk."))
        assertEquals("Yağmur 06:00–10:00 arası.", text.lines.first())
        // Gün doğumu ve batımı yalnız akşam özetinde.
        assertTrue(text.lines.last().startsWith("Gün doğumu 07:10"))
        assertEquals("Tomorrow 10–16° · rain in the morning", summary(kind = SummaryKind.Evening, strings = TestStrings.english, tomorrowEach = {
            hour(it, feels = 10.0, chance = if (it in 6..9) 70 else 10).copy(weatherCode = if (it in 6..9) 61 else 3)
        })!!.title)
    }

    @Test
    fun `an all-day event has no time range line`() {
        val text = summary(
            kind = SummaryKind.Evening,
            tomorrowEach = { hour(it, feels = 10.0, chance = 80, mm = 1.0).copy(weatherCode = 61) },
        )!!
        assertEquals("Yarın 10–16° · gün boyu yağmur", text.title)
        assertTrue(text.lines.toString(), text.lines.none { it.contains("arası") })
    }

    @Test
    fun `no daily data means no summary`() {
        val forecast = TestForecast.forecast(TestForecast.hours(today), emptyList())
        assertNull(DailySummary.of(SummaryKind.Morning, forecast, emptyList(), morning, 0.0, TestStrings.turkish, { it.roundToInt() }, { it.format(clock) }))
    }

    @Test
    fun `the time of day of an event`() {
        fun at(from: Int, until: Int) = DailySummary.whenRes(today.atTime(from, 0), today.atTime(until, 0))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_morning, at(7, 9))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_noon, at(12, 13))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_afternoon, at(15, 17))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_evening, at(19, 21))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_night, at(23, 23))
        assertEquals(com.alihaydarsayar.communesky.R.string.summary_when_all_day, at(7, 19))
    }

    @Test
    fun `the alarm window starts fifteen minutes before the chosen time`() {
        val minutes = MorningSummary.DEFAULT_MINUTES
        assertEquals(today.atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(6, 0), minutes))
        // Pencere başladıktan sonra (alarm çaldığında) bir sonraki alarm yarın içindir: günde bir uyanma.
        assertEquals(today.plusDays(1).atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(7, 15), minutes))
        assertEquals(today.plusDays(1).atTime(7, 15), MorningSummary.nextWindowStart(today.atTime(23, 0), minutes))
        // Akşam özeti 21:00.
        assertEquals(today.atTime(20, 45), MorningSummary.nextWindowStart(today.atTime(12, 0), MorningSummary.DEFAULT_EVENING_MINUTES))
    }
}
