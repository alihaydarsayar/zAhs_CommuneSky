package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.Moon
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Yaşam önerileri: her birinin iyi / idare eder / uygun değil sınırları ve cümleleri. */
class LifestyleTest {

    private val today = TestForecast.day
    private val morning: LocalDateTime = today.atTime(8, 0)

    /** Bugün ve sonraki iki gün; [each] bugünün saatlerini, [later] sonraki günleri belirler. */
    private fun weather(
        now: LocalDateTime = morning,
        moon: Double = 0.05,
        min: Double = 12.0,
        max: Double = 18.0,
        later: (Int) -> HourlyForecast = { hour(it) },
        each: (Int) -> HourlyForecast = { hour(it) },
    ): TodayWeather {
        val hours = TestForecast.hours(today, each) + TestForecast.hours(today.plusDays(1), later) + TestForecast.hours(today.plusDays(2), later)
        val forecast = TestForecast.forecast(hours, listOf(TestForecast.daily(min = min, max = max)))
        return TodayWeather.from(forecast, now).copy(moonIllumination = moon)
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private fun tr(result: TipResult) = result.sentence(TestStrings.turkish, { it.format(clock) })

    private fun en(result: TipResult) = result.sentence(TestStrings.english, { it.format(clock) })

    @Test
    fun `umbrella limits`() {
        fun tip(chance: Int = 0, mm: Double = 0.0) = UmbrellaTip.evaluate(weather { hour(it, chance = if (it == 15) chance else 0, mm = if (it == 15) mm else 0.0) })

        assertEquals(TipStatus.Good, tip().status)
        assertEquals(TipStatus.Good, tip(chance = 40, mm = 1.0).status)
        assertEquals(TipStatus.Fair, tip(chance = 41).status)
        assertEquals(TipStatus.Fair, tip(mm = 1.1).status)
        assertEquals(TipStatus.Fair, tip(chance = 59).status)
        assertEquals(TipStatus.Poor, tip(chance = 60).status)
        assertEquals(TipStatus.Poor, tip(mm = 5.0).status)
        assertEquals("Gerek yok", tr(tip()))
        assertEquals("Yanına al: yağış ihtimali %45", tr(tip(chance = 45)))
        assertEquals("Take one with you: 45% chance of rain", en(tip(chance = 45)))
        assertEquals("Mutlaka al: yağış ihtimali %80", tr(tip(chance = 80)))
        assertEquals("Mutlaka al: yağış bekleniyor", tr(tip(chance = 20, mm = 6.0)))
        assertEquals("Take one with you: some rain is expected", en(tip(chance = 20, mm = 2.0)))
    }

    @Test
    fun `rain that has already passed does not ask for an umbrella`() {
        val day = weather(now = today.atTime(16, 0)) { hour(it, chance = if (it == 9) 90 else 0) }
        assertEquals(TipStatus.Good, UmbrellaTip.evaluate(day).status)
    }

    @Test
    fun `sunscreen limits`() {
        fun tip(uv: Double) = SunscreenTip.evaluate(weather { hour(it, uv = if (it == 13) uv else 0.5) })

        assertEquals(TipStatus.Good, tip(2.9).status)
        assertEquals(TipStatus.Fair, tip(3.0).status)
        assertEquals(TipStatus.Fair, tip(5.9).status)
        assertEquals(TipStatus.Poor, tip(6.0).status)
        assertEquals("Gerek yok: UV düşük", tr(tip(1.0)))
        assertEquals("İyi olur: UV en fazla 4", tr(tip(4.2)))
        assertEquals("Needed: UV peaks at 8", en(tip(8.0)))
    }

    @Test
    fun `laundry limits`() {
        fun tip(humidity: Int = 50, wind: Double = 12.0, cloud: Int = 20, chance: Int = 0, now: LocalDateTime = morning) =
            LaundryTip.evaluate(weather(now) { hour(it, humidity = humidity, wind = wind, cloud = cloud, chance = if (it == 14) chance else 0) })

        assertEquals(TipStatus.Good, tip().status)
        // Rüzgâr yok ama güneş var: kurur. İkisi de yoksa yavaş kurur.
        assertEquals(TipStatus.Good, tip(wind = 2.0, cloud = 20).status)
        assertEquals(TipStatus.Good, tip(wind = 12.0, cloud = 90).status)
        assertEquals(TipStatus.Fair, tip(wind = 2.0, cloud = 90).status)
        assertEquals(TipStatus.Good, tip(humidity = 69).status)
        assertEquals(TipStatus.Fair, tip(humidity = 70).status)
        assertEquals(TipStatus.Poor, tip(chance = 50).status)
        assertEquals(TipStatus.Good, tip(chance = 49).status)
        // Akşamüstü: kurumaya vakit kalmadı.
        assertEquals("Bugün için geç oldu", tr(tip(now = today.atTime(17, 30))))
        assertEquals("Bugün dışarıda iyi kurur", tr(tip()))
        assertEquals("Not today: rain is expected", en(tip(chance = 80)))
    }

    @Test
    fun `best two hours for a run`() {
        // Sabah serin (5°), öğlen UV yüksek, 17–19 arası her koşul tutuyor.
        val day = weather {
            when (it) {
                in 0..10 -> hour(it, feels = 5.0)
                in 11..16 -> hour(it, feels = 20.0, uv = 7.0)
                17, 18 -> hour(it, feels = 18.0)
                else -> hour(it, feels = 16.0, wind = 30.0)
            }
        }
        val result = RunTip.evaluate(day)
        assertEquals(TipStatus.Good, result.status)
        assertEquals("En iyi saat: 17:00–19:00", tr(result))
        assertEquals("Best time: 17:00–19:00", en(result))
        // Eşit puanda en erken aralık seçilir.
        val calm = RunTip.evaluate(weather { hour(it, feels = 15.0) })
        assertEquals("En iyi saat: 08:00–10:00", tr(calm))
    }

    @Test
    fun `run conditions each count once`() {
        assertEquals(4, RunTip.score(hour(10, feels = 15.0)))
        assertEquals(4, RunTip.score(hour(10, feels = 8.0)))
        assertEquals(3, RunTip.score(hour(10, feels = 7.9)))
        assertEquals(4, RunTip.score(hour(10, feels = 22.0)))
        assertEquals(3, RunTip.score(hour(10, feels = 22.1)))
        assertEquals(3, RunTip.score(hour(10, feels = 15.0, wind = 25.0)))
        assertEquals(3, RunTip.score(hour(10, feels = 15.0, uv = 6.0)))
        assertEquals(3, RunTip.score(hour(10, feels = 15.0, mm = 0.5)))
        assertEquals(0, RunTip.score(hour(10, feels = 30.0, wind = 40.0, uv = 9.0, chance = 80)))
    }

    @Test
    fun `no good hour for a run still gives an answer`() {
        // Bütün gün sıcak, rüzgârlı ve yağışlı.
        val bad = RunTip.evaluate(weather { hour(it, feels = 31.0, wind = 35.0, chance = 80, mm = 2.0) })
        assertEquals(TipStatus.Poor, bad.status)
        assertEquals("Bugün uygun saat yok", tr(bad))
        assertEquals("No good time today", en(bad))
        // Gece 21:30: bugün iki saatlik aralık kalmadı.
        assertEquals(TipStatus.Poor, RunTip.evaluate(weather(now = today.atTime(21, 30))).status)
        assertNull(RunTip.bestWindow(emptyList()))
        // Yalnız bir koşul tutmuyorsa (hafif rüzgâr) idare eder.
        val fair = RunTip.evaluate(weather { hour(it, feels = 15.0, wind = 28.0) })
        assertEquals(TipStatus.Fair, fair.status)
        assertEquals("İdare eder: 08:00–10:00", tr(fair))
        // Hava kalitesi kötüyse (bölüm C) koşu önerilmez.
        val smog = RunTip.evaluate(weather { hour(it, feels = 15.0) }.copy(airQualityPoor = true))
        assertEquals(TipStatus.Poor, smog.status)
    }

    @Test
    fun `car wash needs two dry days`() {
        assertEquals("Uygun: iki gün yağış yok", tr(CarWashTip.evaluate(weather())))
        val tomorrowEvening = CarWashTip.evaluate(weather(later = { hour(it, mm = if (it == 20) 1.0 else 0.0) }))
        assertEquals(TipStatus.Fair, tomorrowEvening.status)
        val tonight = CarWashTip.evaluate(weather { hour(it, chance = if (it == 22) 70 else 0) })
        assertEquals(TipStatus.Poor, tonight.status)
        assertEquals("Not now: rain within a day", en(tonight))
    }

    @Test
    fun `stargazing needs a clear dark night`() {
        fun tip(cloud: Int, moon: Double = 0.05, visibility: Double = 20_000.0) =
            StargazingTip.evaluate(weather(moon = moon, later = { hour(it, cloud = cloud, visibility = visibility) }) { hour(it, cloud = cloud, visibility = visibility) })

        assertEquals(TipStatus.Good, tip(cloud = 19).status)
        assertEquals("Bu gece gökyüzü açık ve karanlık", tr(tip(cloud = 10)))
        assertEquals(TipStatus.Fair, tip(cloud = 20).status)
        assertEquals("Gökyüzü açık ama ay parlak", tr(tip(cloud = 10, moon = 0.9)))
        assertEquals("Clear but hazy tonight", en(tip(cloud = 10, visibility = 6_000.0)))
        assertEquals(TipStatus.Fair, tip(cloud = 49).status)
        assertEquals(TipStatus.Poor, tip(cloud = 50).status)
        assertEquals("Too cloudy tonight", en(tip(cloud = 80)))
    }

    @Test
    fun `cold risk is worded carefully`() {
        fun tip(min: Double, max: Double, feels: Double = 15.0, humidity: Int = 50) =
            ColdRiskTip.evaluate(weather(min = min, max = max) { hour(it, feels = feels, humidity = humidity) })

        assertEquals(TipStatus.Good, tip(12.0, 18.0).status)
        assertEquals("Düşük", tr(tip(12.0, 18.0)))
        assertEquals(TipStatus.Fair, tip(10.0, 17.0).status)
        assertEquals(TipStatus.Poor, tip(8.0, 18.0).status)
        assertEquals("Dikkat: gece ile gündüz arasında 10° fark var", tr(tip(8.0, 18.0)))
        assertEquals(TipStatus.Poor, tip(2.0, 6.0, feels = 3.0, humidity = 85).status)
        assertEquals("Take care: cold and damp", en(tip(2.0, 6.0, feels = 3.0, humidity = 85)))
        // Soğuk ama kuru: risk sayılmaz.
        assertEquals(TipStatus.Good, tip(2.0, 6.0, feels = 3.0, humidity = 40).status)
        // Fahrenhayt kullanan, farkı kendi biriminde okur (10 °C = 18 °F).
        val fahrenheit = tip(8.0, 18.0).sentence(TestStrings.english, { it.format(clock) }) { Math.round(it * 9 / 5).toInt() }
        assertEquals("Take care: 18° between day and night", fahrenheit)
    }

    @Test
    fun `every tip always answers and the card shows the two that matter most`() {
        val results = Lifestyle.evaluate(weather { hour(it, chance = if (it == 15) 80 else 0, uv = 1.0) })
        assertEquals(7, results.size)
        for ((_, result) in results) assertTrue(tr(result).isNotBlank())
        val top = Lifestyle.top(results)
        assertEquals(2, top.size)
        assertEquals(UmbrellaTip, top[0].first)
        // Hiç veri yokken de (boş tahmin) çökmez, cevap verir.
        val empty = Lifestyle.evaluate(TodayWeather.from(TestForecast.forecast(emptyList(), emptyList()), morning))
        assertEquals(7, empty.size)
        for ((_, result) in empty) assertTrue(en(result).isNotBlank())
    }

    @Test
    fun `moon illumination follows the phase`() {
        // 13 Ocak 2025 dolunay, 29 Ocak 2025 yeni ay.
        assertTrue(Moon.illumination(Instant.parse("2025-01-13T22:00:00Z")) > 0.97)
        assertTrue(Moon.illumination(Instant.parse("2025-01-29T12:00:00Z")) < 0.03)
        assertEquals(0.5, Moon.illumination(Instant.parse("2025-01-21T20:00:00Z")), 0.12)
    }
}
