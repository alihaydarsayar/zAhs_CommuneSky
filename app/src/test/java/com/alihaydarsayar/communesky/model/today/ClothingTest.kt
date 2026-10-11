package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** "Ne giysem": katman eşikleri, kişisel kaydırma, ekler, gün içi fark ve cümleler. */
class ClothingTest {

    private fun part(part: DayPart, feels: Double, gust: Double? = 10.0, chance: Int = 0, mm: Double = 0.0, uv: Double? = 2.0) =
        PartWeather(part, feels, gust, chance, mm, uv)

    private fun day(feels: Double, gust: Double? = 10.0, chance: Int = 0, mm: Double = 0.0, uv: Double? = 2.0) =
        DayPart.entries.map { part(it, feels, gust, chance, mm, uv) }

    @Test
    fun `every layer threshold`() {
        assertEquals(Layer.Light, Clothing.layer(26.1))
        assertEquals(Layer.TShirt, Clothing.layer(26.0))
        assertEquals(Layer.TShirt, Clothing.layer(21.0))
        assertEquals(Layer.LongSleeve, Clothing.layer(20.9))
        assertEquals(Layer.LongSleeve, Clothing.layer(16.0))
        assertEquals(Layer.Sweater, Clothing.layer(15.9))
        assertEquals(Layer.Sweater, Clothing.layer(11.0))
        assertEquals(Layer.Coat, Clothing.layer(10.9))
        assertEquals(Layer.Coat, Clothing.layer(5.0))
        assertEquals(Layer.HeavyCoat, Clothing.layer(4.9))
        assertEquals(Layer.HeavyCoat, Clothing.layer(0.0))
        assertEquals(Layer.FullWinter, Clothing.layer(-0.1))
    }

    @Test
    fun `sensitivity shifts the thresholds by three degrees`() {
        // 18°: normalde uzun kollu; çabuk üşüyen için 15° gibi (kazak), sıcaklayan için 21° gibi (tişört).
        assertEquals(Layer.LongSleeve, Clothing.layer(18.0, Clothing.personalShift(Sensitivity.Normal, 0.0)))
        assertEquals(Layer.Sweater, Clothing.layer(18.0, Clothing.personalShift(Sensitivity.Cold, 0.0)))
        assertEquals(Layer.TShirt, Clothing.layer(18.0, Clothing.personalShift(Sensitivity.Warm, 0.0)))
    }

    @Test
    fun `each feedback press moves half a degree up to four degrees`() {
        var feedback = 0.0
        feedback = Clothing.afterFeedback(feedback, feltCold = true)
        assertEquals(0.5, feedback, 1e-9)
        repeat(20) { feedback = Clothing.afterFeedback(feedback, feltCold = true) }
        assertEquals(4.0, feedback, 1e-9)
        repeat(30) { feedback = Clothing.afterFeedback(feedback, feltCold = false) }
        assertEquals(-4.0, feedback, 1e-9)
        // Ayar ile düğmeler toplanır: en çok 3 + 4 = 7°.
        assertEquals(7.0, Clothing.personalShift(Sensitivity.Cold, 9.0), 1e-9)
        // 16,2°: normalde uzun kollu; iki kez "Üşüdüm" (1°) sonrası kazak.
        assertEquals(Layer.LongSleeve, Clothing.layer(16.2, 0.0))
        assertEquals(Layer.Sweater, Clothing.layer(16.2, 1.0))
    }

    @Test
    fun `extras follow their limits`() {
        fun extras(feels: Double = 18.0, gust: Double? = 10.0, chance: Int = 0, mm: Double = 0.0, uv: Double? = 2.0) =
            Clothing.advise(listOf(part(DayPart.Midday, feels, gust, chance, mm, uv)))!!.extras

        assertTrue(extras().isEmpty())
        assertFalse(Extra.Umbrella in extras(chance = 40))
        assertTrue(Extra.Umbrella in extras(chance = 41))
        assertFalse(Extra.Umbrella in extras(mm = 1.0))
        assertTrue(Extra.Umbrella in extras(mm = 1.1))
        assertFalse(Extra.Windbreaker in extras(gust = 40.0))
        assertTrue(Extra.Windbreaker in extras(gust = 40.5))
        assertFalse(Extra.Sunglasses in extras(uv = 5.9))
        assertEquals(setOf(Extra.Sunglasses, Extra.Sunscreen), extras(uv = 6.0))
        assertFalse(Extra.Hat in extras(uv = 7.9))
        assertTrue(Extra.Hat in extras(uv = 8.0))
        assertFalse(Extra.Hat in extras(feels = 29.9))
        assertTrue(Extra.Hat in extras(feels = 30.0))
        assertFalse(Extra.WaterproofShoes in extras(mm = 5.0))
        assertEquals(setOf(Extra.Umbrella, Extra.WaterproofShoes), extras(mm = 5.1))
    }

    @Test
    fun `the day uses the thickest remaining part`() {
        val parts = listOf(part(DayPart.Morning, 9.0), part(DayPart.Midday, 15.0), part(DayPart.Evening, 12.0))
        assertEquals(Layer.Coat, Clothing.advise(parts)!!.layer)
        // Öğleden sonra sabah dilimi bitmiştir: artık kazak yeter; ayrıntıda yine üç dilim görünür.
        val afternoon = Clothing.advise(parts, fromHour = 13)!!
        assertEquals(Layer.Sweater, afternoon.layer)
        assertEquals(3, afternoon.parts.size)
        assertNull(Clothing.advise(emptyList()))
    }

    @Test
    fun `late in the evening the advice is for tomorrow`() {
        val today = TestForecast.day
        val hours = TestForecast.hours(today) { TestForecast.hour(it, feels = 22.0) } +
            TestForecast.hours(today.plusDays(1)) { TestForecast.hour(it, feels = 3.0) }
        val forecast = TestForecast.forecast(hours)
        val evening = Clothing.advise(forecast, today.atTime(20, 30))!!
        assertFalse(evening.forTomorrow)
        assertEquals(Layer.TShirt, evening.layer)
        val night = Clothing.advise(forecast, today.atTime(21, 5))!!
        assertTrue(night.forTomorrow)
        assertEquals(Layer.HeavyCoat, night.layer)
    }

    @Test
    fun `parts average the feels-like temperature of their hours`() {
        val hours = TestForecast.hours {
            TestForecast.hour(it, feels = if (it < 11) 10.0 else 20.0, chance = if (it == 18) 70 else 0, uv = if (it == 13) 7.0 else 1.0)
        }
        val parts = Clothing.parts(TestForecast.forecast(hours), TestForecast.day)
        assertEquals(listOf(DayPart.Morning, DayPart.Midday, DayPart.Evening), parts.map { it.part })
        assertEquals(10.0, parts[0].feelsLike, 1e-9)
        assertEquals(20.0, parts[1].feelsLike, 1e-9)
        assertEquals(7.0, parts[1].uv!!, 1e-9)
        assertEquals(70, parts[2].rainChance)
    }

    @Test
    fun `a swing of eight degrees or more is told in one sentence`() {
        val small = Clothing.advise(listOf(part(DayPart.Morning, 11.0), part(DayPart.Midday, 18.9)))!!
        assertNull(small.swing)
        val advice = Clothing.advise(listOf(part(DayPart.Morning, 11.0), part(DayPart.Midday, 21.0), part(DayPart.Evening, 17.0)))!!
        assertNotNull(advice.swing)
        assertEquals(
            "Sabah 11°, öğleden sonra 21°: ince bir mont al, öğlen çıkarırsın.",
            WearText.sentence(advice, TestStrings.turkish) { it.roundToInt() },
        )
        assertEquals(
            "11° in the morning, 21° in the afternoon: take a light jacket, you can take it off at midday.",
            WearText.sentence(advice, TestStrings.english) { it.roundToInt() },
        )
        // Öğleden sonra sabah geçmiştir: fark cümlesi artık söylenmez.
        assertNull(Clothing.advise(advice.parts.map { it.weather }, fromHour = 12)!!.swing)
    }

    @Test
    fun `sentences in Turkish and English`() {
        fun tr(parts: List<PartWeather>) = WearText.sentence(Clothing.advise(parts)!!, TestStrings.turkish) { it.roundToInt() }
        fun en(parts: List<PartWeather>) = WearText.sentence(Clothing.advise(parts)!!, TestStrings.english) { it.roundToInt() }

        assertEquals("Kazak ya da ince mont giy.", tr(day(13.0)))
        assertEquals("Wear a sweater or a light jacket.", en(day(13.0)))
        assertEquals("Mont giy; şemsiye al.", tr(day(8.0, chance = 70)))
        assertEquals("Wear a coat; take an umbrella.", en(day(8.0, chance = 70)))
        assertEquals("Tişört ve ince pantolon yeter; güneş gözlüğü ve güneş kremi al.", tr(day(24.0, uv = 6.5)))
        assertEquals("A T-shirt and light trousers will do; take sunglasses and sunscreen.", en(day(24.0, uv = 6.5)))
        assertEquals(
            "İnce ve açık renk giy: tişört, şort ya da etek; güneş gözlüğü, güneş kremi ve şapka al.",
            tr(day(32.0, uv = 9.0)),
        )
        assertEquals(
            "Kalın mont, atkı, bere ve eldivenle çık; şemsiye, rüzgârlık ve su geçirmez ayakkabı al.",
            tr(day(-3.0, gust = 55.0, mm = 3.0)),
        )
        // Soğuyan gün: öğlenin üst giysisi yanına alınır.
        val cooling = listOf(part(DayPart.Morning, 20.0), part(DayPart.Midday, 10.0))
        assertEquals("Sabah 20°, öğleden sonra 10°: hava soğuyacak, yanına bir mont al.", tr(cooling))
        assertEquals("20° in the morning, 10° in the afternoon: it gets colder, take a coat.", en(cooling))
        // Kısa biçim (widget, bildirim).
        assertEquals("Mont; şemsiye al.", WearText.short(Clothing.advise(day(8.0, chance = 70))!!, TestStrings.turkish))
    }

    @Test
    fun `fahrenheit users read their own unit`() {
        val advice = Clothing.advise(listOf(part(DayPart.Morning, 10.0), part(DayPart.Midday, 20.0)))!!
        assertEquals(
            "50° in the morning, 68° in the afternoon: take a coat, you can take it off at midday.",
            WearText.sentence(advice, TestStrings.english) { (it * 9 / 5 + 32).roundToInt() },
        )
    }

    @Test
    fun `icons start with the layer and fit in one row`() {
        val winter = Clothing.advise(day(-3.0, gust = 55.0, mm = 8.0, uv = 9.0))!!
        assertEquals(4, WearText.icons(winter.layer).size)
        assertTrue(WearText.icons(winter).size <= 10)
        assertEquals(WearText.icons(Layer.Coat), WearText.icons(Clothing.advise(day(8.0))!!))
    }
}
