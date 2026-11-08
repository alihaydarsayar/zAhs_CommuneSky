package com.alihaydarsayar.communesky.model.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hakkında: gizlilik metni, açık kaynak lisansları ve veri kaynakları. Hepsi uygulamaya gömülüdür, ağdan çekilmez. */
class AboutDataTest {

    private val res = File("src/main/res")
    private fun policy(folder: String) = File(res, "$folder/privacy_policy.txt").readText()

    @Test
    fun `policy text is parsed into title, sections, paragraphs and bullets with bold parts`() {
        val blocks = PolicyText.parse("# Title\nLast updated: x\n\n## Section\n- **Bold:** rest\nPlain")
        assertEquals(
            listOf(
                PolicyBlock.Title("Title"), PolicyBlock.Paragraph("Last updated: x"), PolicyBlock.Section("Section"),
                PolicyBlock.Bullet("**Bold:** rest"), PolicyBlock.Paragraph("Plain"),
            ),
            blocks,
        )
        assertEquals(listOf("Bold:" to true, " rest" to false), PolicyText.spans("**Bold:** rest"))
        assertEquals(listOf("a b" to false), PolicyText.spans("a **b"))
    }

    @Test
    fun `the three translations of the policy have the same structure`() {
        val shapes = listOf("raw", "raw-tr", "raw-fr").associateWith { folder ->
            PolicyText.parse(policy(folder)).map { it::class.simpleName }
        }
        assertEquals(shapes["raw"], shapes["raw-tr"])
        assertEquals(shapes["raw"], shapes["raw-fr"])
        for (folder in shapes.keys) {
            val text = policy(folder)
            assertTrue(folder, "https://alihaydarsayar.com/communesky/privacy/" in text)
            assertTrue(folder, "alihaydarsayar.info@gmail.com" in text)
            assertTrue(folder, "2026" in text.lines()[1])
        }
    }

    @Test
    fun `the policy mentions every record the app keeps and the backup rule`() {
        val en = policy("raw")
        for (part in listOf("last 5 attempts", "last 10", "latest network errors", "Android backup", "Copy status", "Do Not Disturb")) {
            assertTrue(part, part in en)
        }
    }

    @Test
    fun `license definitions put bundled assets first and fall back when a license is unknown`() {
        val json = """{"libraries":[
            {"uniqueId":"b:lib","name":"Zeta","artifactVersion":"1.0","licenses":["Apache-2.0"],"developers":[{"name":"X"}]},
            {"uniqueId":"asset:font","name":"Font","licenses":["OFL-1.1"]},
            {"uniqueId":"a:lib","licenses":["Unknown"]}],
            "licenses":{"Apache-2.0":{"name":"Apache License 2.0","url":"u","content":"text"},"OFL-1.1":{"name":"OFL","content":""}}}"""
        val items = OpenSourceLicenses.parse(json)
        assertEquals(listOf("asset:font", "a:lib", "b:lib"), items.map { it.id })
        assertEquals("Unknown", items[1].licenses.single().name)
        assertEquals("a:lib", items[1].name)
        assertEquals("text", items[2].licenses.single().content)
        assertEquals(null, items[0].licenses.single().content)
        assertTrue(OpenSourceLicenses.parse("broken").isEmpty())
    }

    @Test
    fun `every bundled font and borrowed icon is listed with its license`() {
        val config = File("aboutlibraries/libraries").listFiles()!!.joinToString("\n") { it.readText() }
        File(res, "font").listFiles()!!.forEach { font -> assertTrue(font.name, "res/font/${font.name}" in config) }
        val borrowed = File(res, "drawable").listFiles()!!.filter { "Material Symbols" in it.readText() }.map { it.nameWithoutExtension } + "ic_location"
        borrowed.forEach { name -> assertTrue(name, Regex("[ :]$name[,\"]").containsMatchIn(config)) }
        // Lisans metinleri gömülü: Apache-2.0, BSD-3-Clause (protobuf), OFL-1.1 ve uygulamanın kendi GPL-3.0'ı.
        val licenses = File("aboutlibraries/licenses").listFiles()!!.joinToString("\n") { it.readText() }
        for (id in listOf("\"Apache-2.0\"", "\"BSD-3-Clause\"", "\"OFL-1.1\"", "\"GPL-3.0-only\"", "\"ASDKL\"")) assertTrue(id, id in licenses)
    }

    @Test
    fun `every data source has its use and terms in all three languages`() {
        val sources = DataSources.all(2026)
        assertEquals(sources.size, sources.map { it.key }.toSet().size)
        for (folder in listOf("values", "values-tr", "values-fr")) {
            val strings = File(res, "$folder/strings.xml").readText()
            for (source in sources) {
                assertTrue("$folder ${source.key}", "name=\"source_${source.key}_use\"" in strings && "name=\"source_${source.key}_terms\"" in strings)
            }
        }
        sources.forEach { assertTrue(it.key, it.link.startsWith("https://") && it.terms.startsWith("https://") && it.attribution.isNotBlank()) }
        assertTrue(sources.first { it.key == "cams" }.attribution.contains("2026"))
        assertTrue(sources.first { it.key == "osm" }.attribution == "© OpenStreetMap contributors")
    }

    @Test
    fun `the about screens never touch the network themselves`() {
        val text = File("src/main/java/com/alihaydarsayar/communesky/ui/settings/AboutScreens.kt").readText() +
            File("src/main/java/com/alihaydarsayar/communesky/model/about/AboutData.kt").readText()
        for (banned in listOf("Retrofit", "OkHttp", "HttpURLConnection", "URL(", "openConnection")) assertTrue(banned, banned !in text)
        // Lisans verisi derleme sırasında üretilir ve gömülür; çalışma zamanı kütüphanesi eklenmez.
        val build = File("build.gradle.kts").readText()
        assertTrue("offlineMode = true" in build && "aboutlibraries-core" !in build)
    }
}
