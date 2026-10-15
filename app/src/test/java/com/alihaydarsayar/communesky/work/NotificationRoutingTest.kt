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
        assertEquals("summary_morning_silent", NotificationRouting.channel(NotificationKind.Morning, defaults, noon))
        assertEquals("summary_evening_silent", NotificationRouting.channel(NotificationKind.Evening, defaults, noon))
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
        assertEquals("summary_morning_silent", NotificationRouting.channel(NotificationKind.Morning, quiet, midnight))
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
}
