package com.alihaydarsayar.communesky.work

import android.app.NotificationManager
import com.alihaydarsayar.communesky.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

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

    // --- Test bildirimi dürüst: engel varsa nedeni söyler, gönderdiyse listede görüldüğünü doğrular (1.5.11)

    @Test
    fun `the blocker names permission first, then app notifications, then the channel`() {
        assertEquals(AttemptOutcome.NoPermission, NotificationHealth.blocker(permissionGranted = false, appEnabled = false, channelImportance = none))
        assertEquals(AttemptOutcome.AppOff, NotificationHealth.blocker(permissionGranted = true, appEnabled = false, channelImportance = none))
        assertEquals(AttemptOutcome.ChannelOff, NotificationHealth.blocker(permissionGranted = true, appEnabled = true, channelImportance = none))
        assertNull(NotificationHealth.blocker(permissionGranted = true, appEnabled = true, channelImportance = low))
        // Kanal henüz yoksa (null) engel sayılmaz: kanal gönderimden önce kurulur.
        assertNull(NotificationHealth.blocker(permissionGranted = true, appEnabled = true, channelImportance = null))
    }

    @Test
    fun `a blocked test is never posted and never reported as sent`() = runBlocking {
        for (blocker in listOf(AttemptOutcome.NoPermission, AttemptOutcome.AppOff, AttemptOutcome.ChannelOff)) {
            var posted = false
            val result = NotificationHealth.outcome(blocker, oldData = false, post = { posted = true; true }, shown = { true })
            assertEquals(blocker, result)
            assertEquals(false, posted)
        }
    }

    @Test
    fun `a notification Android refuses is a failure`() = runBlocking {
        assertEquals(AttemptOutcome.Failed, NotificationHealth.outcome(null, false, post = { false }, shown = { true }))
    }

    @Test
    fun `a notification that is posted but not in the active list is not reported as sent`() = runBlocking {
        assertEquals(AttemptOutcome.NotVisible, NotificationHealth.outcome(null, false, post = { true }, shown = { false }))
    }

    @Test
    fun `a notification found in the active list is sent, from old data when it was built from old data`() = runBlocking {
        assertEquals(AttemptOutcome.Sent, NotificationHealth.outcome(null, false, post = { true }, shown = { true }))
        assertEquals(AttemptOutcome.SentOldData, NotificationHealth.outcome(null, true, post = { true }, shown = { true }))
    }

    @Test
    fun `the test uses the channel the morning summary would use`() {
        val sound = NotificationSettings(morningSound = true)
        assertEquals(NotificationKind.Morning.soundChannel, NotificationHealth.testChannel(sound, LocalTime.of(12, 0)))
        val silent = NotificationSettings(morningSound = false)
        assertEquals(NotificationKind.Morning.silentChannel, NotificationHealth.testChannel(silent, LocalTime.of(12, 0)))
        // Sessiz saatlerde sesli seçim bile sessiz kanala gider; gerçek özet de öyle.
        val quiet = NotificationSettings(morningSound = true, quietHours = true, quietStart = 23 * 60, quietEnd = 7 * 60)
        assertEquals(NotificationKind.Morning.silentChannel, NotificationHealth.testChannel(quiet, LocalTime.of(23, 30)))
    }
}
