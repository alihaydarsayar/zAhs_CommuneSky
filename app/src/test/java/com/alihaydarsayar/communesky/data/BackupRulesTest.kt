package com.alihaydarsayar.communesky.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android'in otomatik yedeği yalnız ayarları ve kayıtlı yerleri alır; çalışma durumu ("gönderildi" işaretleri,
 * bildirim deneme ve alarm kayıtları, önbellekler, veritabanı) yedekte yoktur. API 26–30 (backup_rules) ve
 * API 31+ (data_extraction_rules: bulut ve cihazdan cihaza) aynı listeyi kullanır.
 */
class BackupRulesTest {

    private val xml = File("src/main/res/xml")

    private fun includes(file: String, section: String?): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xml, file))
        val root = if (section == null) document.documentElement else document.getElementsByTagName(section).item(0) as org.w3c.dom.Element
        val nodes = root.getElementsByTagName("include")
        assertTrue("$file $section has no include", nodes.length > 0)
        assertEquals("$file $section must not exclude (include is a whitelist)", 0, root.getElementsByTagName("exclude").length)
        return (0 until nodes.length).map { val e = nodes.item(it) as org.w3c.dom.Element; "${e.getAttribute("domain")}:${e.getAttribute("path")}" }.toSet()
    }

    private val expected = setOf(
        "file:datastore/settings.preferences_pb",
        "file:datastore/widgets.preferences_pb",
        "file:backup/places.json",
        "file:androidx.appcompat.app.AppCompatDelegate.application_locales_record_file",
        "sharedpref:widget_language.xml",
    )

    @Test
    fun `all three rule sets back up only settings and places`() {
        assertEquals(expected, includes("backup_rules.xml", null))
        assertEquals(expected, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(expected, includes("data_extraction_rules.xml", "device-transfer"))
    }

    @Test
    fun `no working state is in the backup`() {
        val state = listOf("morning_summary", "notification_log", "network_failures", "alert_notifications", "air_quality_notifications",
            "weather.db", "mgm_istasyonlar", "accuracy", "widget_draw_state", "permissions", "http")
        for (entry in expected) state.forEach { assertTrue("$entry contains $it", it !in entry) }
    }

    @Test
    fun `the state files the app writes are the ones the rules leave out`() {
        // Kodda adı geçen SharedPreferences dosyaları: yedeğe giren tek dosya dil ve saat biçimi.
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        for (name in listOf("morning_summary", "notification_log", "network_failures")) assertTrue(name, "\"$name\"" in sources)
        assertTrue("\"widget_language\"" in sources)
    }
}
