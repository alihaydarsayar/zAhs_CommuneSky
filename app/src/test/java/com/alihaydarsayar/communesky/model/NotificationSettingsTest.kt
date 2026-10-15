package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsTest {

    @Test
    fun `every kind is off on a new install`() {
        val settings = NotificationSettings()
        assertFalse(settings.alerts)
        assertFalse(settings.morning)
        assertFalse(settings.evening)
        assertFalse(settings.airQuality)
        assertFalse(settings.quietHours)
        assertFalse(settings.anyOn)
        // Varsayılan sesler: kırmızı ve turuncu sesli, sarı ve özetler sessiz.
        assertTrue(settings.redSound && settings.orangeSound)
        assertFalse(settings.yellowSound || settings.morningSound || settings.eveningSound || settings.airSound)
        assertEquals(AlertThreshold.Orange, settings.alertThreshold)
        assertEquals(AlertPlaces.All, settings.alertPlaces)
        assertEquals(23 * 60, settings.quietStart)
        assertEquals(7 * 60, settings.quietEnd)
        assertTrue(settings.quietRedSounds)
    }

    @Test
    fun `warnings stay on after the update only for people who allowed notifications`() {
        // Güncelleme, izin verilmiş, uyarılar açık (ya da hiç değiştirilmemiş: eski varsayılan açıktı): korunur.
        assertTrue(NotificationMigration.alertsAfterUpdate(stored = true, permitted = true, freshInstall = false))
        assertTrue(NotificationMigration.alertsAfterUpdate(stored = null, permitted = true, freshInstall = false))
        // İzin hiç verilmemiş: kapanır.
        assertFalse(NotificationMigration.alertsAfterUpdate(stored = true, permitted = false, freshInstall = false))
        // Kullanıcı kapatmışsa kapalı kalır.
        assertFalse(NotificationMigration.alertsAfterUpdate(stored = false, permitted = true, freshInstall = false))
        // Yeni kurulum: hep kapalı (Android 12 ve öncesinde izin kendiliğinden verilmiş sayılsa da).
        assertFalse(NotificationMigration.alertsAfterUpdate(stored = null, permitted = true, freshInstall = true))
    }

    @Test
    fun `thresholds`() {
        assertTrue(AlertThreshold.Yellow.includes(AlertLevel.Yellow))
        assertFalse(AlertThreshold.Orange.includes(AlertLevel.Yellow))
        assertTrue(AlertThreshold.Orange.includes(AlertLevel.Red))
        assertFalse(AlertThreshold.Red.includes(AlertLevel.Orange))
    }
}
