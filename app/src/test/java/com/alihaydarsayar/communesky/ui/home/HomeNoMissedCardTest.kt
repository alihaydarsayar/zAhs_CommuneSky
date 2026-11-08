package com.alihaydarsayar.communesky.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ana (hava durumu) ekranında gelmeyen özetle ilgili hiçbir gösterge yoktur: kart, rozet, nokta ya da kapatma durumu.
 * Bu bilgi yalnız Ayarlar › Bildirimler › Bildirim durumu'nda görünür (1.5.13).
 */
class HomeNoMissedCardTest {

    private val home = File("src/main/java/com/alihaydarsayar/communesky/ui/home")

    @Test
    fun `home actions carry no missed summary state`() {
        val names = HomeActions::class.java.declaredFields.map { it.name.lowercase() } + HomeActions::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue(names.toString(), names.none { "missed" in it || "dismiss" in it })
    }

    @Test
    fun `no home screen source reads the notification log or a missed summary`() {
        val files = home.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found", files.isNotEmpty())
        val forbidden = listOf("NotificationLog", "MissedSummary", "missedSummary", "pendingMissed", "lastMissed", "AttemptOutcome.Missed", "status_last_missed", "missed_reason")
        for (file in files) {
            val text = file.readText()
            forbidden.forEach { assertTrue("${file.name} contains $it", it !in text) }
        }
    }

    @Test
    fun `the missed summary card is gone for good`() {
        assertTrue(!File("src/main/java/com/alihaydarsayar/communesky/ui/settings/MissedSummaryCard.kt").exists())
        val main = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue("MissedSummaryCard" !in main && "dismissMissed" !in main)
    }
}
