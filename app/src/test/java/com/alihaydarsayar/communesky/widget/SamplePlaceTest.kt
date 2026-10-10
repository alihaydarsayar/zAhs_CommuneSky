package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Örnek veride ve önizlemelerde gerçek kimsenin yeri görünmez: örnek ev dile göre "Beşiktaş"
 * (Türkçe) ya da "Besiktas" (diğer diller), diğer örnek yerler Liverpool ve Limerick.
 */
class SamplePlaceTest {

    private val module = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { if (File(it, "app/src/main").isDirectory) File(it, "app") else it }
        .first { File(it, "src/main/res/values/strings.xml").isFile }

    private fun sampleName(folder: String): String {
        val xml = File(module, "src/main/res/$folder/strings.xml").readText()
        return Regex("<string name=\"sample_place_home\">(.*?)</string>").find(xml)!!.groupValues[1]
    }

    @Test
    fun `the sample home is written for the language`() {
        assertEquals("Besiktas", sampleName("values"))
        assertEquals("Beşiktaş", sampleName("values-tr"))
        // Başka bir dil klasörü eklenirse o da "Besiktas" yazmalı (ya da hiç yazmayıp İngilizceyi kullanmalı).
        File(module, "src/main/res").listFiles { file -> file.name.startsWith("values-") && File(file, "strings.xml").isFile }!!
            .filter { it.name != "values-tr" }
            .forEach { folder ->
                val xml = File(folder, "strings.xml").readText()
                if (xml.contains("sample_place_home")) assertEquals(folder.name, "Besiktas", sampleName(folder.name))
            }
    }

    @Test
    fun `sample data only uses the sample places`() {
        for (homeName in listOf("Beşiktaş", "Besiktas")) {
            for (scenario in WidgetSamples.Scenario.entries) {
                val input = WidgetSamples.input(homeName, scenario = scenario)
                val names = input.places.map { it.name } + input.weather.values.mapNotNull { it.city.name }
                assertTrue("$scenario: $names", names.all { it in setOf(homeName, "Liverpool", "Limerick") })
                assertEquals(homeName, input.places.first().name)
            }
        }
    }

    @Test
    fun `away from home the sample shows the distance from the design`() {
        val state = WidgetSamples.input("Beşiktaş", scenario = WidgetSamples.Scenario.Away).homeLocation as HomeLocationState.Away
        assertEquals("Liverpool", state.here.snapshot.city.name)
        assertEquals("Beşiktaş", state.home.snapshot.city.name)
        assertEquals(2747.0, state.distanceKm, 3.0)
        assertTrue(WidgetSamples.input("Beşiktaş", scenario = WidgetSamples.Scenario.AtHome).homeLocation is HomeLocationState.AtHome)
        assertTrue(WidgetSamples.input("Beşiktaş", scenario = WidgetSamples.Scenario.Nearby).homeLocation is HomeLocationState.Nearby)
    }

    @Test
    fun `no real neighbourhood names remain in the sources`() {
        // Adlar burada da düz yazılmaz: bu dosyanın kendisi taramaya takılmasın.
        val banned = listOf("Yay" + "la", "Kadı" + "köy", "Kadi" + "koy", "Sarı" + "yer", "Sari" + "yer", "Karşı" + "yaka", "Karsi" + "yaka", "Kem" + "ah")
        val hits = File(module, "src").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .flatMap { file ->
                val text = file.readText()
                banned.filter { text.contains(it, ignoreCase = true) }.map { "${file.relativeTo(module)}: $it" }
            }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }
}
