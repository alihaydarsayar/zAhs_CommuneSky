package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uyarı bildirimi: yalnız turuncu ve kırmızı, aynı uyarı için bir kez. */
class AlertNotifierTest {

    private fun alert(id: String, level: AlertLevel) =
        WeatherAlert(id, level, listOf(AlertEvent.Wind), "Kuvvetli rüzgâr bekleniyor.", "2026-10-12T09:00", "2026-10-12T21:00", "MGM")

    @Test
    fun `only orange and red warnings are announced`() {
        val alerts = listOf(alert("y", AlertLevel.Yellow), alert("o", AlertLevel.Orange), alert("r", AlertLevel.Red))
        assertEquals(listOf("o", "r"), AlertNotifier.pending(alerts, emptySet()).map { it.id })
    }

    @Test
    fun `the same warning is announced once`() {
        val alerts = listOf(alert("o", AlertLevel.Orange))
        val first = AlertNotifier.pending(alerts, emptySet())
        assertEquals(1, first.size)
        val sent = AlertNotifier.remembered(emptySet(), alerts, first)
        // Bir saat sonraki kontrol: aynı uyarı hâlâ geçerli, yeniden bildirilmez.
        assertTrue(AlertNotifier.pending(alerts, sent).isEmpty())
        // Aynı uyarı iki yerde de geçerliyse (iki kayıtlı yer aynı ilçede) bir kez.
        assertEquals(1, AlertNotifier.pending(alerts + alerts, emptySet()).size)
    }

    @Test
    fun `expired warnings are forgotten and a new one is announced`() {
        val sent = setOf("old")
        val now = listOf(alert("new", AlertLevel.Red))
        val pending = AlertNotifier.pending(now, sent)
        assertEquals(listOf("new"), pending.map { it.id })
        // "old" artık listede yok: hatırlanmaz.
        assertEquals(setOf("new"), AlertNotifier.remembered(sent, now, pending))
    }
}
