package com.alihaydarsayar.communesky.work

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hava bilgisi hassas değil: her bildirim kilit ekranında tam görünür (VISIBILITY_PUBLIC). */
class LockScreenVisibilityTest {

    private val work = File("src/main/java/com/alihaydarsayar/communesky/work")

    private fun count(text: String, part: String) = text.split(part).size - 1

    @Test
    fun `every notification built by the app is public on the lock screen`() {
        val files = work.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found", files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            val builders = count(text, "NotificationCompat.Builder(")
            val public = count(text, "setVisibility(NotificationCompat.VISIBILITY_PUBLIC)")
            assertTrue("${file.name}: $builders builders, $public public", public >= builders)
        }
    }

    @Test
    fun `every channel is created public on the lock screen`() {
        val text = File(work, "NotificationChannels.kt").readText()
        assertTrue(count(text, "lockscreenVisibility = Notification.VISIBILITY_PUBLIC") >= 2)
    }
}
