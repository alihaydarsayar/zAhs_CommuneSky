package com.alihaydarsayar.communesky.work

import android.app.NotificationManager
import com.alihaydarsayar.communesky.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Bildirimleri engelleyen durumların algılanması ve üreticiye göre yönlendirme. */
class NotificationHealthTest {

    private val none = NotificationManager.IMPORTANCE_NONE
    private val low = NotificationManager.IMPORTANCE_LOW

    @Test
    fun `makers are recognised by manufacturer or brand`() {
        assertEquals(Maker.Xiaomi, Maker.of("Xiaomi", "Redmi"))
        assertEquals(Maker.Xiaomi, Maker.of("Xiaomi", "POCO"))
        assertEquals(Maker.Samsung, Maker.of("samsung", "samsung"))
        assertEquals(Maker.Huawei, Maker.of("HONOR", "honor"))
        assertEquals(Maker.Oppo, Maker.of("realme", "realme"))
        assertEquals(Maker.Other, Maker.of("Google", "google"))
        assertNotNull(Maker.Xiaomi.tip)
        assertNull(Maker.Other.tip)
    }

    @Test
    fun `a turned off channel of a type the user switched on is reported`() {
        val settings = NotificationSettings(morning = true, morningSound = false)
        // Sessiz kanal kapalı: bildirim gelmez.
        val blocked = NotificationHealth.blockedKinds(settings) { if (it == NotificationKind.Morning.silentChannel) none else low }
        assertEquals(listOf(NotificationKind.Morning), blocked)
        // Seçilmeyen (sesli) kanal kapalıysa sorun yok.
        assertEquals(emptyList<NotificationKind>(), NotificationHealth.blockedKinds(settings) { if (it == NotificationKind.Morning.soundChannel) none else low })
    }

    @Test
    fun `types that are off are never reported`() {
        assertEquals(emptyList<NotificationKind>(), NotificationHealth.blockedKinds(NotificationSettings()) { none })
    }

    @Test
    fun `alert channels follow the chosen threshold`() {
        val orange = NotificationSettings(alerts = true)
        val kinds = NotificationHealth.blockedKinds(orange) { none }
        assertEquals(listOf(NotificationKind.RedAlert, NotificationKind.OrangeAlert), kinds)
    }
}
