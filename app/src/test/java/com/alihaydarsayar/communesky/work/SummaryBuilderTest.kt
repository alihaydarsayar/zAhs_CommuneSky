package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.today.BuiltSummary
import com.alihaydarsayar.communesky.model.today.Strings
import com.alihaydarsayar.communesky.model.today.SummaryBuilder
import com.alihaydarsayar.communesky.model.today.SummaryKind
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Özet hiçbir koşulda sessizce düşmez: tam özet, yoksa basit özet. Yer yalnız Beşiktaş (MGM var) ve Bakü (Türkiye
 * dışı: MGM uyarısı, normaller ve dünle karşılaştırma yok).
 */
class SummaryBuilderTest {

    private val day = TestForecast.day
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** Bakü: Türkiye dışı; uyarı yok, dünün verisi yok. Saat farkı UTC+4. */
    private val baku = TestForecast.forecast(
        TestForecast.hours(day) + TestForecast.hours(day.plusDays(1)),
        listOf(TestForecast.daily(day, min = 14.0, max = 23.0), TestForecast.daily(day.plusDays(1), min = 13.0, max = 21.0)),
    ).copy(utcOffsetSeconds = 4 * 3600)

    private val besiktas = TestForecast.forecast(
        TestForecast.hours(day.minusDays(1)) + TestForecast.hours(day) + TestForecast.hours(day.plusDays(1)),
        listOf(TestForecast.daily(day.minusDays(1)), TestForecast.daily(day), TestForecast.daily(day.plusDays(1))),
    )

    private fun build(
        forecast: com.alihaydarsayar.communesky.model.Forecast, kind: SummaryKind = SummaryKind.Morning, strings: Strings = TestStrings.turkish,
        now: LocalDateTime = day.atTime(7, 0),
    ) = SummaryBuilder.build(kind, forecast, emptyList(), now, 0.0, strings, { it.roundToInt() }, { it.format(clock) })

    @Test
    fun `a place outside Turkiye without alerts or yesterday still gets a full morning and evening summary`() {
        val morning = build(baku) as BuiltSummary.Full
        assertEquals(day, morning.text.date)
        assertTrue(morning.text.title, morning.text.title.contains("14") && morning.text.title.contains("23"))
        val evening = build(baku, SummaryKind.Evening, now = day.atTime(18, 0)) as BuiltSummary.Full
        assertEquals(day.plusDays(1), evening.text.date)
    }

    @Test
    fun `a place with every field present gets a full summary in all three languages`() {
        for (strings in listOf(TestStrings.turkish, TestStrings.english, TestStrings.french)) {
            assertTrue(build(besiktas, strings = strings) is BuiltSummary.Full)
        }
    }

    /** Yalnız yedek özetin kullandığı metinlere izin verir; öbürleri istisna fırlatır (tam özet düşer). */
    private class Brittle(private val real: Strings) : Strings by real {
        private val allowed = setOf(
            R.string.summary_title, R.string.summary_title_tomorrow, R.string.summary_no_advice,
            R.string.weather_clear, R.string.weather_partly_cloudy, R.string.weather_fog, R.string.weather_overcast,
        )

        override fun get(id: Int, vararg args: Any): String {
            check(id in allowed) { "Beşiktaş 41.04,29.00" }
            return real.get(id, *args)
        }
    }

    @Test
    fun `when the full summary throws a basic summary with the temperatures and the sky is built, and the error is kept`() {
        val built = build(besiktas, strings = Brittle(TestStrings.turkish))
        built as BuiltSummary.Basic
        assertTrue(built.title, built.title.contains("12") && built.title.contains("20"))
        assertTrue(built.body.isNotBlank())
        assertTrue(built.error is IllegalStateException)
        // Kayda yalnız sınıf adı yazılır, iletisi (yer adı, konum) yazılmaz.
        assertEquals("java.lang.IllegalStateException", com.alihaydarsayar.communesky.work.Attempt.nameOf(built.error!!))
    }

    @Test
    fun `the basic evening summary describes tomorrow`() {
        val built = build(besiktas, SummaryKind.Evening, Brittle(TestStrings.turkish), now = day.atTime(21, 0)) as BuiltSummary.Basic
        assertTrue(built.title, built.title.startsWith("Yarın") || built.title.contains("Tomorrow"))
    }

    @Test
    fun `no forecast for the day is reported as unavailable without an error`() {
        val empty = besiktas.copy(daily = emptyList())
        val built = build(empty) as BuiltSummary.Unavailable
        assertNull(built.error)
    }

    @Test
    fun `no day in the forecast is unavailable even when the strings are broken`() {
        val empty = besiktas.copy(daily = emptyList())
        val broken = object : Strings by TestStrings.turkish {
            override fun get(id: Int, vararg args: Any): String = error("boom")
        }
        val built = build(empty, strings = broken)
        // Gün yoksa tam özet null döner (istisna yok), yedek de kurulamaz.
        assertTrue(built is BuiltSummary.Unavailable)
    }

    // --- Veri 24 saatten eski: sessizce atlanmaz (1.5.11)

    private val today = LocalDate.of(2026, 10, 29)

    @Test
    fun `data older than 24 hours gets a notice instead of silence, once a day`() {
        val old = Duration.ofHours(30)
        // Önceki davranış: karar Skip, kullanıcı hiçbir şey görmüyordu.
        assertEquals(SummarySchedule.Verdict.Skip, SummarySchedule.verdict(true, null, today, old))
        assertEquals(SummarySchedule.Action.StaleNotice, SummarySchedule.action(true, null, null, today, old))
        // Bildirim bugün gittiyse yeniden gitmez; dün gittiyse yine gider.
        assertEquals(SummarySchedule.Action.Nothing, SummarySchedule.action(true, null, today, today, old))
        assertEquals(SummarySchedule.Action.StaleNotice, SummarySchedule.action(true, null, today.minusDays(1), today, old))
    }

    @Test
    fun `no weather data at all gets its own notice`() {
        assertEquals(SummarySchedule.Action.NoDataNotice, SummarySchedule.action(true, null, null, today, null))
    }

    @Test
    fun `three to twenty four hour old data is sent with its time, fresh data as it is`() {
        assertEquals(SummarySchedule.Action.Full, SummarySchedule.action(true, null, null, today, Duration.ofHours(1)))
        assertEquals(SummarySchedule.Action.FullOldData, SummarySchedule.action(true, null, null, today, Duration.ofHours(3).plusMinutes(1)))
        assertEquals(SummarySchedule.Action.FullOldData, SummarySchedule.action(true, null, null, today, Duration.ofHours(24)))
        assertEquals(SummarySchedule.Action.StaleNotice, SummarySchedule.action(true, null, null, today, Duration.ofHours(24).plusMinutes(1)))
    }

    @Test
    fun `nothing happens when the summary is off or already sent today, even with old data`() {
        assertEquals(SummarySchedule.Action.Nothing, SummarySchedule.action(false, null, null, today, Duration.ofHours(30)))
        assertEquals(SummarySchedule.Action.Nothing, SummarySchedule.action(true, today, null, today, Duration.ofHours(30)))
    }
}
