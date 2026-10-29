package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRoutingTest {

    private val defaults = NotificationSettings()
    private val noon = 12 * 60
    private val midnight = 0

    @Test
    fun `default sounds follow the warning level`() {
        assertEquals("alert_red_sound", NotificationRouting.channel(NotificationKind.RedAlert, defaults, noon))
        assertEquals("alert_orange_sound", NotificationRouting.channel(NotificationKind.OrangeAlert, defaults, noon))
        assertEquals("alert_yellow_silent", NotificationRouting.channel(NotificationKind.YellowAlert, defaults, noon))
        assertEquals("summary_morning_quiet", NotificationRouting.channel(NotificationKind.Morning, defaults, noon))
        assertEquals("summary_evening_quiet", NotificationRouting.channel(NotificationKind.Evening, defaults, noon))
        assertEquals("air_quality_v2_silent", NotificationRouting.channel(NotificationKind.AirQuality, defaults, noon))
    }

    @Test
    fun `the user's choice picks the channel`() {
        val chosen = defaults.copy(orangeSound = false, morningSound = true)
        assertEquals("alert_orange_silent", NotificationRouting.channel(NotificationKind.OrangeAlert, chosen, noon))
        assertEquals("summary_morning_sound", NotificationRouting.channel(NotificationKind.Morning, chosen, noon))
    }

    @Test
    fun `quiet hours silence everything except red when the user wants red to sound`() {
        val quiet = defaults.copy(quietHours = true, morningSound = true)
        assertEquals("alert_orange_silent", NotificationRouting.channel(NotificationKind.OrangeAlert, quiet, midnight))
        assertEquals("summary_morning_quiet", NotificationRouting.channel(NotificationKind.Morning, quiet, midnight))
        assertEquals("alert_red_sound", NotificationRouting.channel(NotificationKind.RedAlert, quiet, midnight))
        assertEquals("alert_red_silent", NotificationRouting.channel(NotificationKind.RedAlert, quiet.copy(quietRedSounds = false), midnight))
        // Gündüz sessiz saatlerin dışında: seçim geçerli.
        assertEquals("alert_orange_sound", NotificationRouting.channel(NotificationKind.OrangeAlert, quiet, noon))
    }

    @Test
    fun `quiet hours can cross midnight`() {
        assertTrue(NotificationRouting.inQuietHours(23 * 60, 23 * 60, 7 * 60))
        assertTrue(NotificationRouting.inQuietHours(3 * 60, 23 * 60, 7 * 60))
        assertFalse(NotificationRouting.inQuietHours(7 * 60, 23 * 60, 7 * 60))
        assertFalse(NotificationRouting.inQuietHours(22 * 60 + 59, 23 * 60, 7 * 60))
        assertTrue(NotificationRouting.inQuietHours(14 * 60, 13 * 60, 15 * 60))
        assertFalse(NotificationRouting.inQuietHours(12 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun `every kind has its own pair of channels`() {
        val ids = NotificationKind.entries.flatMap { listOf(it.soundChannel, it.silentChannel) }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.any { it in listOf("weather_alerts", "morning_summary", "air_quality") })
    }

    @Test
    fun `silent summaries stay visible in the shade, other silent channels stay low`() {
        for (kind in listOf(NotificationKind.Morning, NotificationKind.Evening)) {
            assertTrue(kind.silentImportance >= android.app.NotificationManager.IMPORTANCE_DEFAULT)
            // Eski düşük önemli kanal başka bir kimlikte: silinir, yenisi kurulur.
            assertEquals("${kind.id}_silent", kind.retiredSilentChannel)
            assertFalse(kind.silentChannel == kind.retiredSilentChannel)
        }
        for (kind in NotificationKind.entries - listOf(NotificationKind.Morning, NotificationKind.Evening)) {
            assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, kind.silentImportance)
            assertEquals(null, kind.retiredSilentChannel)
            assertEquals("${kind.id}_silent", kind.silentChannel)
        }
    }

    @Test
    fun `a retired channel id is never an active one`() {
        val active = NotificationKind.entries.flatMap { listOf(it.soundChannel, it.silentChannel) }.toSet()
        assertTrue(NotificationKind.entries.mapNotNull { it.retiredSilentChannel }.none { it in active })
    }

    @Test
    fun `quiet hours never skip a summary, they only move it to the silent channel`() {
        val quiet = NotificationSettings(morning = true, morningSound = true, evening = true, eveningSound = true, quietHours = true, quietStart = 23 * 60, quietEnd = 7 * 60)
        // Sabah özeti 07:00: sessiz saatler 07:00'de biter, sesli kanal; bir dakika önce sessiz kanal. İkisi de bir kanaldır, atlama yok.
        assertEquals("summary_morning_sound", NotificationRouting.channel(NotificationKind.Morning, quiet, 7 * 60))
        assertEquals("summary_morning_quiet", NotificationRouting.channel(NotificationKind.Morning, quiet, 6 * 60 + 59))
        // Akşam özeti 18:00 sessiz saatlerin dışında; 23:30'a kaydırılırsa sessiz kanal.
        assertEquals("summary_evening_sound", NotificationRouting.channel(NotificationKind.Evening, quiet, 18 * 60))
        assertEquals("summary_evening_quiet", NotificationRouting.channel(NotificationKind.Evening, quiet, 23 * 60 + 30))
        // Sessiz saatler kapalıyken (varsayılan) hiçbir saat etkilenmez.
        assertEquals("summary_morning_sound", NotificationRouting.channel(NotificationKind.Morning, quiet.copy(quietHours = false), 3 * 60))
        assertEquals(false, NotificationSettings().quietHours)
    }
}
