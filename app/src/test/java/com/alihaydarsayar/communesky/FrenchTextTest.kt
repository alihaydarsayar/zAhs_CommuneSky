package com.alihaydarsayar.communesky

import com.alihaydarsayar.communesky.lifestyle.RunTip
import com.alihaydarsayar.communesky.lifestyle.TodayAdvice
import com.alihaydarsayar.communesky.lifestyle.UmbrellaTip
import com.alihaydarsayar.communesky.lifestyle.sentence
import com.alihaydarsayar.communesky.model.detail.Metric
import com.alihaydarsayar.communesky.model.detail.MetricDetail
import com.alihaydarsayar.communesky.model.detail.MetricText
import com.alihaydarsayar.communesky.model.detail.Trend
import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.DayPart
import com.alihaydarsayar.communesky.model.today.PartWeather
import com.alihaydarsayar.communesky.model.today.WearText
import com.alihaydarsayar.communesky.testing.TestForecast
import com.alihaydarsayar.communesky.testing.TestForecast.hour
import com.alihaydarsayar.communesky.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Fransızca: kurulan cümleler doğal okunuyor mu, bütün metinler çevrilmiş mi, biçim argümanları tutuyor mu? */
class FrenchTextTest {

    private val nbsp = ' '
    private val today = TestForecast.day

    private fun day(feels: Double, chance: Int = 0, uv: Double = 2.0) = DayPart.entries.map { PartWeather(it, feels, 10.0, chance, 0.0, uv) }

    @Test
    fun `what to wear in French`() {
        fun fr(parts: List<PartWeather>) = WearText.sentence(Clothing.advise(parts)!!, TestStrings.french) { it.roundToInt() }
        assertEquals("Mettez un pull ou une veste légère.", fr(day(13.0)))
        assertEquals("Mettez un manteau$nbsp; prenez un parapluie.", fr(day(8.0, chance = 70)))
        assertEquals(
            "Un tee-shirt et un pantalon léger suffisent$nbsp; prenez des lunettes de soleil et de la crème solaire.",
            fr(day(24.0, uv = 6.5)),
        )
        val swing = listOf(PartWeather(DayPart.Morning, 11.0, 10.0, 0, 0.0, 2.0), PartWeather(DayPart.Midday, 21.0, 10.0, 0, 0.0, 2.0))
        assertEquals("11° le matin, 21° l’après-midi$nbsp: prenez une veste légère, vous pourrez l’enlever à midi.", fr(swing))
    }

    @Test
    fun `tips and the morning summary in French`() {
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val hours = TestForecast.hours(today) { hour(it, feels = 13.0, chance = if (it >= 15) 70 else 10, mm = if (it >= 15) 0.3 else 0.0) }
        val forecast = TestForecast.forecast(hours, listOf(TestForecast.daily(min = 9.0, max = 17.0)))
        val now = today.atTime(7, 30)
        val summary = com.alihaydarsayar.communesky.model.today.DailySummary.of(
            com.alihaydarsayar.communesky.model.today.SummaryKind.Morning, forecast, emptyList(), now, 0.0, TestStrings.french, { it.roundToInt() }, { it.format(clock) },
        )!!
        assertEquals("9–17° · pluie l’après-midi", summary.title)
        assertEquals("Pluie entre 15:00 et 00:00.", summary.lines.first())
        val tips = TodayAdvice.of(forecast, now).tips.associate { it.tip to it.result.sentence(TestStrings.french, { time -> time.format(clock) }) }
        assertEquals("À prendre absolument$nbsp: 70$nbsp% de risque de pluie", tips[UmbrellaTip])
        assertEquals("Meilleur moment$nbsp: 07:00–09:00", tips[RunTip])
    }

    @Test
    fun `detail pages in French`() {
        val pressure = MetricDetail(Metric.Pressure, 1013.0, null, null, null, emptyList(), Trend.Falling)
        assertEquals("Pression normale, en baisse", MetricText.status(pressure, TestStrings.french))
        assertEquals("Brise légère", MetricText.status(MetricDetail(Metric.Wind, 8.0, null, null, null, emptyList()), TestStrings.french))
        assertEquals("UV$nbsp: Élevé", MetricText.status(MetricDetail(Metric.Uv, 6.5, null, null, null, emptyList()), TestStrings.french))
    }

    @Test
    fun `every text is translated with the same format arguments`() {
        fun read(folder: String): Map<String, String> {
            val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
            return Regex("<string name=\"([^\"]+)\"([^>]*)>([\\s\\S]*?)</string>").findAll(file.readText())
                .filter { !it.groupValues[2].contains("translatable=\"false\"") }
                .associate { it.groupValues[1] to it.groupValues[3] }
        }
        val english = read("values")
        val arguments = Regex("%(\\d+\\$)?[ds]")
        for (language in listOf("values-fr", "values-tr")) {
            val translated = read(language)
            assertEquals("$language: eksik ya da fazla metin", english.keys, translated.keys)
            for ((key, text) in english) {
                val expected = arguments.findAll(text).map { it.value }.sorted().toList()
                val actual = arguments.findAll(translated.getValue(key)).map { it.value }.sorted().toList()
                assertEquals("$language/$key", expected, actual)
            }
        }
        // Fransızca metinlerde düz kesme işareti ve çevrilmemiş İngilizce kalıp kalmamalı.
        val french = read("values-fr")
        assertTrue(french.values.none { it.contains("'") })
        assertTrue(french.getValue("today") != english.getValue("today"))
    }
}
