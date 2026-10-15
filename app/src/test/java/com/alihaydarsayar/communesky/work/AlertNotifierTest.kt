package com.alihaydarsayar.communesky.work

import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.AlertPlaces
import com.alihaydarsayar.communesky.model.AlertThreshold
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.work.AlertNotifier.PlaceAlerts
import com.alihaydarsayar.communesky.work.AlertNotifier.Posted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Uyarı bildirimleri: seviye ve yer seçimi, bir kez gösterme, yükseltme, kaldırılınca silme. Örnek yerler: Beşiktaş (5), Liverpool (7). */
class AlertNotifierTest {

    private val now = LocalDateTime.of(2026, 10, 12, 8, 0)
    private val on = NotificationSettings(alerts = true)

    private fun alert(no: Int, level: AlertLevel, event: AlertEvent = AlertEvent.Thunderstorm, begin: String = "2026-10-12T09:00", end: String = "2026-10-12T23:59") =
        WeatherAlert("mgm-$no-${level.name.lowercase()}", level, listOf(event), "Kuvvetli sağanak bekleniyor.", begin, end, "MGM")

    private fun besiktas(vararg alerts: WeatherAlert, current: Boolean = false) = PlaceAlerts(5, current, alerts.toList())

    @Test
    fun `nothing is sent while warnings are off, and shown ones are removed`() {
        val shown = listOf(Posted(5, "mgm-1", AlertLevel.Orange))
        val plan = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange))), NotificationSettings(), null, shown, now)
        assertTrue(plan.post.isEmpty())
        assertEquals(shown, plan.cancel)
    }

    @Test
    fun `the chosen level decides which warnings come`() {
        val alerts = besiktas(alert(1, AlertLevel.Yellow), alert(2, AlertLevel.Orange, AlertEvent.Wind), alert(3, AlertLevel.Red, AlertEvent.Rain))
        fun sent(threshold: AlertThreshold) = AlertNotifier.plan(listOf(alerts), on.copy(alertThreshold = threshold), null, emptyList(), now).post.map { it.alert.level }
        assertEquals(listOf(AlertLevel.Yellow, AlertLevel.Orange, AlertLevel.Red), sent(AlertThreshold.Yellow))
        assertEquals(listOf(AlertLevel.Orange, AlertLevel.Red), sent(AlertThreshold.Orange))
        assertEquals(listOf(AlertLevel.Red), sent(AlertThreshold.Red))
    }

    @Test
    fun `the chosen places decide where warnings come from`() {
        val device = PlaceAlerts(0, true, listOf(alert(1, AlertLevel.Orange)))
        val home = besiktas(alert(2, AlertLevel.Orange))
        val liverpool = PlaceAlerts(7, false, listOf(alert(3, AlertLevel.Orange)))
        val places = listOf(device, home, liverpool)
        fun from(choice: AlertPlaces, homeId: Long?) = AlertNotifier.plan(places, on.copy(alertPlaces = choice), homeId, emptyList(), now).post.map { it.placeId }
        assertEquals(listOf(0L, 5L, 7L), from(AlertPlaces.All, 5))
        assertEquals(listOf(0L), from(AlertPlaces.Current, 5))
        assertEquals(listOf(5L), from(AlertPlaces.Home, 5))
        // Ev seçilmemişse "Ev" bulunduğun yer demektir.
        assertEquals(listOf(0L), from(AlertPlaces.Home, null))
    }

    @Test
    fun `the same warning is shown once`() {
        val first = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange))), on, null, emptyList(), now)
        assertEquals(1, first.post.size)
        val second = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange))), on, null, first.active, now)
        assertTrue(second.post.isEmpty())
        assertTrue(second.cancel.isEmpty())
        assertEquals(first.active, second.active)
    }

    @Test
    fun `a raised warning is shown again, saying where it rose from`() {
        val shown = listOf(Posted(5, "mgm-1", AlertLevel.Yellow))
        // Aynı uyarı numarası, daha ağır renk.
        val same = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange))), on.copy(alertThreshold = AlertThreshold.Yellow), null, shown, now)
        assertEquals(AlertLevel.Yellow, same.post.single().raisedFrom)
        // MGM yeni bir uyarıyla değiştirdi: eskisi listeden çıktı, yenisi kırmızı.
        val orange = listOf(Posted(5, "mgm-1", AlertLevel.Orange))
        val replaced = AlertNotifier.plan(listOf(besiktas(alert(9, AlertLevel.Red))), on, null, orange, now)
        assertEquals(AlertLevel.Orange, replaced.post.single().raisedFrom)
        assertEquals(orange, replaced.cancel)
        // Başka bir olay için ayrı bir kırmızı uyarı yükseltme sayılmaz.
        val other = AlertNotifier.plan(
            listOf(besiktas(alert(1, AlertLevel.Orange), alert(9, AlertLevel.Red, AlertEvent.Snow))), on, null, orange, now,
        )
        assertNull(other.post.single().raisedFrom)
    }

    @Test
    fun `a lighter colour of a shown warning does not ring again`() {
        val shown = listOf(Posted(5, "mgm-1", AlertLevel.Red))
        val plan = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange))), on, null, shown, now)
        assertTrue(plan.post.isEmpty())
        assertEquals(AlertLevel.Orange, plan.active.single().level)
    }

    @Test
    fun `a warning lifted by MGM or ended is removed`() {
        val shown = listOf(Posted(5, "mgm-1", AlertLevel.Orange), Posted(5, "mgm-2", AlertLevel.Red))
        val lifted = AlertNotifier.plan(listOf(besiktas(alert(2, AlertLevel.Red, AlertEvent.Rain))), on, null, shown, now)
        assertEquals(listOf(Posted(5, "mgm-1", AlertLevel.Orange)), lifted.cancel)
        // Süresi bitti.
        val ended = AlertNotifier.plan(listOf(besiktas(alert(2, AlertLevel.Red, end = "2026-10-12T07:00"))), on, null, shown.drop(1), now)
        assertEquals(shown.drop(1), ended.cancel)
    }

    @Test
    fun `two colours of the same warning at once count as the heavier one`() {
        val plan = AlertNotifier.plan(listOf(besiktas(alert(1, AlertLevel.Orange), alert(1, AlertLevel.Red))), on, null, emptyList(), now)
        assertEquals(AlertLevel.Red, plan.post.single().alert.level)
    }

    @Test
    fun `stored entries survive a round trip`() {
        val posted = Posted(5, "mgm-2026101439", AlertLevel.Orange)
        assertEquals(posted, Posted.decode(posted.encode()))
        assertNull(Posted.decode("bozuk"))
    }
}
