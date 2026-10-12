package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.PrecipitationOutlook
import com.alihaydarsayar.communesky.model.today.Extra
import com.alihaydarsayar.communesky.model.today.Layer
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import com.alihaydarsayar.communesky.work.MorningSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Senaryo testleri: öneriler ekranda görünen havayla çelişmemeli. Ekranda yağış görünürken (şu an yağıyor ya da
 * bir saatin simgesi yağmur, sağanak, gök gürültüsü) şemsiye "gerek yok" demez, koşu o saatleri önermez.
 */
class TipScenarioTest {

    private val today = TestForecast.day
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private companion object {
        const val CLEAR = 0
        const val RAIN = 61
        const val THUNDER = 95
        const val SNOW = 73
    }

    /** Üç günlük tahmin; [each] bugünün saatlerini belirler, sonraki günler kuru ve ılıktır. */
    private fun forecast(currentCode: Int = 1, min: Double = 12.0, max: Double = 18.0, each: (Int) -> HourlyForecast): Forecast {
        val hours = TestForecast.hours(today, each) + TestForecast.hours(today.plusDays(1)) + TestForecast.hours(today.plusDays(2))
        return TestForecast.forecast(hours, listOf(TestForecast.daily(min = min, max = max)), currentCode = currentCode)
    }

    private fun advice(forecast: Forecast, now: LocalDateTime = today.atTime(8, 0), airQualityPoor: Boolean = false) =
        TodayAdvice.of(forecast, now, airQualityPoor = airQualityPoor)

    private fun TodayAdvice.result(tip: LifestyleTip) = tips.first { it.tip == tip }.result

    private fun TodayAdvice.text(tip: LifestyleTip) = result(tip).sentence(TestStrings.turkish, { it.format(clock) })

    @Test
    fun `it is raining now although the forecast numbers are dry`() {
        // Telefondaki durum: şu an yağıyor (istasyon ölçümü), saatlik yüzdeler düşük, miktar sıfır.
        val advice = advice(forecast(currentCode = RAIN) { hour(it, feels = 15.0, chance = 10) })
        assertEquals(TipStatus.Poor, advice.result(UmbrellaTip).status)
        assertEquals("Mutlaka al: şu an yağıyor", advice.text(UmbrellaTip))
        assertEquals("Take one for sure: it is raining now", advice.result(UmbrellaTip).sentence(TestStrings.english, { it.format(clock) }))
        assertTrue(Extra.Umbrella in advice.wear!!.extras)
        assertEquals(TipStatus.Poor, advice.result(LaundryTip).status)
        assertEquals(TipStatus.Poor, advice.result(CarWashTip).status)
        // Koşu için içinde bulunulan saat "yağış yok" sayılmaz: en iyi aralık bir sonraki saatten başlar.
        assertEquals("En iyi saat: 09:00–11:00", advice.text(RunTip))
    }

    @Test
    fun `only the icon says rain`() {
        // MGM simgesi 14:00–16:00 arası yağmur; Open-Meteo'nun yüzdesi %10, miktarı sıfır.
        val advice = advice(forecast { hour(it, feels = 15.0, chance = 10, code = if (it in 14..16) RAIN else 1) })
        assertNotEquals(TipStatus.Good, advice.result(UmbrellaTip).status)
        assertEquals("Mutlaka al: yağış bekleniyor", advice.text(UmbrellaTip))
        assertTrue(Extra.Umbrella in advice.wear!!.extras)
        assertEquals(TipStatus.Poor, advice.result(LaundryTip).status)
        assertEquals(TipStatus.Poor, advice.result(CarWashTip).status)
        // Tek bir saatin simgesi yağış gösteriyorsa en azından "yanına al".
        val single = advice(forecast { hour(it, feels = 15.0, code = if (it == 15) RAIN else 1) })
        assertEquals(TipStatus.Fair, single.result(UmbrellaTip).status)
        assertEquals("Yanına al: biraz yağış bekleniyor", single.text(UmbrellaTip))
        // Sabah özeti de simgeye bakar.
        val summary = MorningSummary.text(forecast { hour(it, feels = 15.0, chance = 10, code = if (it in 14..16) RAIN else 1) }, today.atTime(7, 30), 0.0, TestStrings.turkish, { it.roundToInt() }, { it.format(clock) })
        assertTrue(summary, summary!!.contains("14:00'te yağmur"))
    }

    @Test
    fun `only the percentage is high`() {
        val advice = advice(forecast { hour(it, feels = 15.0, chance = if (it in 13..15) 75 else 5) })
        assertEquals(TipStatus.Poor, advice.result(UmbrellaTip).status)
        assertEquals("Mutlaka al: yağış ihtimali %75", advice.text(UmbrellaTip))
        assertTrue(Extra.Umbrella in advice.wear!!.extras)
        assertEquals(TipStatus.Poor, advice.result(LaundryTip).status)
        // En iyi koşu aralığı yüzdesi yüksek saatlere denk gelmez.
        val best = advice.result(RunTip).args.first() as LocalTime
        assertFalse(best.hour in 12..15)
    }

    @Test
    fun `thunderstorms in the evening are never the best time for a run`() {
        // Gündüz sıcak (hissedilen 27°), akşam serin ama 19:00–21:00 gök gürültülü: eski kural akşamı öneriyordu.
        val stormy = forecast { hour(it, feels = if (it >= 18) 18.0 else 27.0, chance = 10, code = if (it in 19..21) THUNDER else 1) }
        val advice = advice(stormy)
        assertEquals(TipStatus.Poor, advice.result(UmbrellaTip).status)
        assertEquals("Mutlaka al: gök gürültülü sağanak bekleniyor", advice.text(UmbrellaTip))
        assertEquals("Take one for sure: thunderstorms are expected", advice.result(UmbrellaTip).sentence(TestStrings.english, { it.format(clock) }))
        val run = advice.result(RunTip)
        if (run.args.isNotEmpty()) {
            val from = (run.args.first() as LocalTime).hour
            assertTrue("koşu $from", from !in 18..21)
        }
        // Bütün gün gök gürültüsü: hiçbir aralık önerilmez.
        val allDay = advice(forecast { hour(it, feels = 15.0, code = THUNDER) })
        assertEquals("Bugün uygun saat yok", allDay.text(RunTip))
        assertEquals(TipStatus.Poor, allDay.result(RunTip).status)
        // Gece gök gürültüsü: yıldız gözlemi olmaz (bulut oranı düşük görünse de).
        val night = advice(forecast { hour(it, feels = 15.0, cloud = 5, code = if (it >= 21) THUNDER else CLEAR) })
        assertEquals("Bu gece yağış bekleniyor", night.text(StargazingTip))
        assertEquals(TipStatus.Poor, night.result(StargazingTip).status)
    }

    @Test
    fun `thunder right now rules out the current hour`() {
        val advice = advice(forecast(currentCode = THUNDER) { hour(it, feels = 15.0) })
        assertEquals("Mutlaka al: şu an yağıyor", advice.text(UmbrellaTip))
        assertEquals("En iyi saat: 09:00–11:00", advice.text(RunTip))
    }

    @Test
    fun `a dry sunny day`() {
        val advice = advice(forecast(currentCode = CLEAR, min = 14.0, max = 20.0) { hour(it, feels = 17.0, code = CLEAR, cloud = 5, uv = if (it in 11..15) 4.0 else 1.0, humidity = 45, wind = 12.0) })
        assertEquals("Gerek yok", advice.text(UmbrellaTip))
        assertFalse(Extra.Umbrella in advice.wear!!.extras)
        assertEquals(TipStatus.Good, advice.result(LaundryTip).status)
        assertEquals(TipStatus.Good, advice.result(CarWashTip).status)
        assertEquals(TipStatus.Good, advice.result(RunTip).status)
        assertEquals(TipStatus.Fair, advice.result(SunscreenTip).status)
        assertEquals(TipStatus.Good, advice.result(ColdRiskTip).status)
    }

    @Test
    fun `a very cold day and a very hot day`() {
        val cold = advice(forecast(min = -6.0, max = -1.0) { hour(it, feels = -8.0, humidity = 85, code = if (it in 10..12) SNOW else 3) })
        assertEquals(Layer.FullWinter, cold.wear!!.layer)
        // Kar da yağıştır: şemsiye "gerek yok" demez, çamaşır ve araba yıkama uygun değildir.
        assertNotEquals(TipStatus.Good, cold.result(UmbrellaTip).status)
        assertEquals(TipStatus.Poor, cold.result(LaundryTip).status)
        assertEquals(TipStatus.Poor, cold.result(CarWashTip).status)
        assertEquals(TipStatus.Poor, cold.result(ColdRiskTip).status)
        // Dondurucu havada koşu "en iyi saat" diye önerilmez.
        assertNotEquals(TipStatus.Good, cold.result(RunTip).status)

        val hot = advice(forecast(currentCode = CLEAR, min = 24.0, max = 38.0) { hour(it, feels = if (it in 9..19) 37.0 else 26.0, code = CLEAR, cloud = 0, uv = if (it in 10..16) 9.0 else 1.0, humidity = 30) })
        assertEquals(Layer.Light, hot.wear!!.layer)
        assertTrue(hot.wear!!.extras.containsAll(listOf(Extra.Sunglasses, Extra.Sunscreen, Extra.Hat)))
        assertEquals(TipStatus.Poor, hot.result(SunscreenTip).status)
        assertEquals("Gerek yok", hot.text(UmbrellaTip))
        // Sıcak saatler koşu için önerilmez: en iyi aralık sabah erken ya da akşam.
        val run = hot.result(RunTip)
        if (run.args.isNotEmpty()) assertTrue((run.args.first() as LocalTime).hour !in 9..18)
    }

    @Test
    fun `the rain tile does not say no rain next to a high chance`() {
        // Yüzde ve miktar ayrı modellerden gelir: %63 yazarken "Bugün yağış beklenmiyor" denmez.
        assertEquals(PrecipitationOutlook.Possible, TestForecast.daily(mm = 0.0).copy(precipitationProbability = 63).precipitationOutlook)
        assertEquals(PrecipitationOutlook.Possible, TestForecast.daily(mm = 0.0).copy(weatherCode = THUNDER).precipitationOutlook)
        assertEquals(PrecipitationOutlook.Expected, TestForecast.daily(mm = 0.2).copy(precipitationProbability = 29).precipitationOutlook)
        assertEquals(PrecipitationOutlook.None, TestForecast.daily(mm = 0.0).copy(precipitationProbability = 20).precipitationOutlook)
        assertEquals("Yağış ihtimali var; miktar çok az.", TestStrings.turkish.get(com.alihaydarsayar.communesky.R.string.precipitation_possible))
    }

    @Test
    fun `poor air quality`() {
        val advice = advice(forecast { hour(it, feels = 15.0, code = CLEAR) }, airQualityPoor = true)
        assertEquals(TipStatus.Poor, advice.result(RunTip).status)
        assertEquals("Hava kalitesi kötü: içeride daha iyi", advice.text(RunTip))
        // Öteki öneriler hava kalitesinden etkilenmez.
        assertEquals("Gerek yok", advice.text(UmbrellaTip))
    }

    @Test
    fun `no tip contradicts the weather shown on the screen`() {
        val codes = listOf(CLEAR, 2, 3, 45, 51, RAIN, 65, SNOW, 80, THUNDER)
        // Değişik günler: her saat için simge, yüzde ve miktar birbirinden bağımsız seçilir (tekrarlanabilir).
        var seed = 7L
        fun next(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33) % bound).toInt().let { if (it < 0) it + bound else it }
        }
        repeat(400) { round ->
            val wetShare = next(4)
            val day = forecast(currentCode = codes[if (next(4) == 0) next(codes.size) else 0]) {
                val code = if (next(4) < wetShare) codes[next(codes.size)] else codes[next(3)]
                hour(it, feels = 5.0 + next(25), chance = next(101), mm = if (next(5) == 0) next(40) / 10.0 else 0.0, code = code, cloud = next(101), wind = next(40).toDouble(), uv = next(10).toDouble())
            }
            val now = today.atTime(6 + next(15), next(60))
            val advice = TodayAdvice.of(day, now)
            val weather = TodayWeather.from(day, now)
            val label = "tur $round, saat $now"
            val shownWet = weather.precipitatingNow || weather.remaining(6, 23).any { it.showsPrecipitation }
            if (shownWet) {
                assertNotEquals("$label: şemsiye", TipStatus.Good, advice.result(UmbrellaTip).status)
                assertNotEquals("$label: araba", TipStatus.Good, advice.result(CarWashTip).status)
            }
            if (weather.precipitatingNow || weather.remaining(9, 19).any { it.showsPrecipitation }) {
                assertNotEquals("$label: çamaşır", TipStatus.Good, advice.result(LaundryTip).status)
            }
            if (weather.tonight.any { it.showsPrecipitation }) assertEquals("$label: yıldız", TipStatus.Poor, advice.result(StargazingTip).status)
            // Önerilen koşu aralığında gök gürültüsü olmaz; "en iyi saat" denmişse hiç yağış simgesi de olmaz.
            val run = advice.result(RunTip)
            if (run.args.isNotEmpty()) {
                val from = (run.args.first() as LocalTime).hour
                val window = weather.hours.filter { it.time.hour == from || it.time.hour == from + 1 }
                assertTrue("$label: koşu gök gürültüsünde", window.none { it.isThunderstorm })
                if (run.status == TipStatus.Good) assertTrue("$label: koşu yağışta", window.none { it.showsPrecipitation })
                if (weather.thunderNow) assertTrue("$label: şu an gök gürültüsü", from != now.hour)
            }
            // Giyim önerisi: bugünün kalan dilimlerinde yağış simgesi varsa ya da şu an yağıyorsa şemsiye.
            val wear = advice.wear
            if (wear != null && !wear.forTomorrow) {
                val partsWet = wear.parts.any { it.weather.part.until > now.hour && it.weather.wet }
                if (weather.precipitatingNow || partsWet) assertTrue("$label: giyimde şemsiye", Extra.Umbrella in wear.extras)
            }
        }
    }
}
