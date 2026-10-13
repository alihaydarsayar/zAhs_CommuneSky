package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/** Ay hesapları, bilinen tarihlerle: yeni ay ve dolunay anları (±1 saat), aydınlanma, evre adı, doğuş ve batış. */
class MoonTest {

    private fun assertWithinAnHour(expected: String, actual: Instant) {
        val difference = abs(Duration.between(Instant.parse(expected), actual).toMinutes())
        assertTrue("beklenen $expected, bulunan $actual", difference <= 60)
    }

    @Test
    fun `known new moons`() {
        // 8 Nisan 2024 (tam güneş tutulması), 29 Ocak 2025, 17 Şubat 2026 (halkalı tutulma), 12 Ağustos 2026 (tam tutulma).
        assertWithinAnHour("2024-04-08T18:21:00Z", Moon.nextNewMoon(Instant.parse("2024-04-01T00:00:00Z")))
        assertWithinAnHour("2025-01-29T12:36:00Z", Moon.nextNewMoon(Instant.parse("2025-01-15T00:00:00Z")))
        assertWithinAnHour("2026-02-17T12:01:00Z", Moon.nextNewMoon(Instant.parse("2026-02-01T00:00:00Z")))
        assertWithinAnHour("2026-08-12T17:37:00Z", Moon.nextNewMoon(Instant.parse("2026-08-01T00:00:00Z")))
        assertWithinAnHour("2025-01-29T12:36:00Z", Moon.previousNewMoon(Instant.parse("2025-02-10T00:00:00Z")))
    }

    @Test
    fun `known full moons`() {
        // 21 Ocak 2000 (tam ay tutulması), 13 Ocak 2025, 3 Mart 2026 (tam ay tutulması).
        assertWithinAnHour("2000-01-21T04:40:00Z", Moon.nextFullMoon(Instant.parse("2000-01-10T00:00:00Z")))
        assertWithinAnHour("2025-01-13T22:27:00Z", Moon.nextFullMoon(Instant.parse("2025-01-01T00:00:00Z")))
        assertWithinAnHour("2026-03-03T11:38:00Z", Moon.nextFullMoon(Instant.parse("2026-02-20T00:00:00Z")))
    }

    @Test
    fun `next phases are always in the future and one lunation apart`() {
        var at = Instant.parse("2026-10-12T00:00:00Z")
        repeat(14) {
            val newMoon = Moon.nextNewMoon(at)
            val fullMoon = Moon.nextFullMoon(at)
            assertTrue(newMoon.isAfter(at))
            assertTrue(fullMoon.isAfter(at))
            val following = Moon.nextNewMoon(newMoon)
            val days = Duration.between(newMoon, following).toHours() / 24.0
            assertTrue("ay $days gün", days in 29.2..29.9)
            at = newMoon
        }
    }

    @Test
    fun `illumination and phase names follow the cycle`() {
        val full = Instant.parse("2025-01-13T22:27:00Z")
        val newMoon = Instant.parse("2025-01-29T12:36:00Z")
        assertTrue(Moon.illumination(full) > 0.995)
        assertTrue(Moon.illumination(newMoon) < 0.005)
        assertEquals(MoonPhase.Full, Moon.phaseName(Moon.phase(full)))
        assertEquals(MoonPhase.New, Moon.phaseName(Moon.phase(newMoon)))
        // İlk dördün 5 Şubat 2025 08:02 UTC: yarısı aydınlık, büyüyor.
        val firstQuarter = Instant.parse("2025-02-05T08:02:00Z")
        assertEquals(0.5, Moon.illumination(firstQuarter), 0.03)
        assertEquals(MoonPhase.FirstQuarter, Moon.phaseName(Moon.phase(firstQuarter)))
        assertEquals(MoonPhase.WaxingCrescent, Moon.phaseName(Moon.phase(Instant.parse("2025-02-01T12:00:00Z"))))
        assertEquals(MoonPhase.WaxingGibbous, Moon.phaseName(Moon.phase(Instant.parse("2025-02-09T12:00:00Z"))))
        assertEquals(MoonPhase.WaningGibbous, Moon.phaseName(Moon.phase(Instant.parse("2025-01-17T12:00:00Z"))))
        assertEquals(MoonPhase.LastQuarter, Moon.phaseName(Moon.phase(Instant.parse("2025-01-21T20:31:00Z"))))
        assertEquals(MoonPhase.WaningCrescent, Moon.phaseName(Moon.phase(Instant.parse("2025-01-25T12:00:00Z"))))
    }

    @Test
    fun `the full moon rises around sunset and the new moon around sunrise`() {
        // İstanbul yakınları (örnek yer), UTC+3. 13 Ocak 2025 dolunay: gün batımı yaklaşık 17:00.
        val (fullRise, _) = Moon.riseAndSet(LocalDate.of(2025, 1, 13), 41.04, 29.0, 3 * 3600)
        assertNotNull(fullRise)
        assertTrue("dolunay doğuşu $fullRise", fullRise!!.hour in 15..18)
        // 29 Ocak 2025 yeni ay: gün doğumu yaklaşık 08:15, gün batımı yaklaşık 17:15.
        val (newRise, newSet) = Moon.riseAndSet(LocalDate.of(2025, 1, 29), 41.04, 29.0, 3 * 3600)
        assertTrue("yeni ay doğuşu $newRise", newRise!!.hour in 7..9)
        assertTrue("yeni ay batışı $newSet", newSet!!.hour in 16..18)
    }

    @Test
    fun `the moon is at the horizon at the computed rise and climbs afterwards`() {
        val offset = ZoneOffset.ofHours(1)
        for (day in 1..28 step 3) {
            val date = LocalDate.of(2026, 10, day)
            val (rise, set) = Moon.riseAndSet(date, 53.41, -2.99, 3600)
            if (rise != null) {
                val at = rise.toInstant(offset)
                assertEquals(0.125, Moon.altitude(at, 53.41, -2.99), 0.2)
                assertTrue(Moon.altitude(at.plusSeconds(1800), 53.41, -2.99) > Moon.altitude(at, 53.41, -2.99))
            }
            if (set != null) {
                val at = set.toInstant(offset)
                assertEquals(0.125, Moon.altitude(at, 53.41, -2.99), 0.2)
                assertTrue(Moon.altitude(at.plusSeconds(1800), 53.41, -2.99) < Moon.altitude(at, 53.41, -2.99))
            }
            assertTrue(rise != null || set != null)
        }
    }

    @Test
    fun `thirty days cover a whole cycle`() {
        val days = Moon.days(LocalDate.of(2026, 10, 12), 30, 3 * 3600)
        assertEquals(30, days.size)
        assertTrue(days.any { it.phase == MoonPhase.Full })
        assertTrue(days.any { it.phase == MoonPhase.New })
        assertTrue(days.maxOf { it.illumination } > 0.98)
        assertTrue(days.minOf { it.illumination } < 0.02)
        // Dolunaya kadar büyür, sonra küçülür.
        val full = days.indexOfFirst { it.phase == MoonPhase.Full }
        assertTrue(days[full - 1].waxing || days[full].waxing)
        val info = Moon.info(Instant.parse("2026-10-12T09:00:00Z"), 41.04, 29.0, 3 * 3600)
        assertTrue(info.nextFullMoon.isAfter(info.nextNewMoon) || info.nextNewMoon.isAfter(info.nextFullMoon))
        assertTrue(info.illumination in 0.0..1.0)
    }

    /** Biçimin aydınlık alanı, birim çemberin alanına oranla (ızgarayla sayılır). */
    private fun litShare(shape: MoonShape): Double {
        var lit = 0
        var disc = 0
        val n = 200
        for (i in 0 until n) {
            for (j in 0 until n) {
                val x = -1 + (i + 0.5) * 2 / n
                val y = -1 + (j + 0.5) * 2 / n
                if (x * x + y * y > 1) continue
                disc++
                if (shape.isLit(x, y)) lit++
            }
        }
        return lit.toDouble() / disc
    }

    @Test
    fun `the drawn shape covers the lit share of the disc`() {
        // Hilal, yarım, şişkin, dolunay: çizilen alan aydınlanma oranına eşittir.
        for (illumination in listOf(0.0, 0.05, 0.25, 0.5, 0.75, 0.95, 1.0)) {
            assertEquals("aydınlanma $illumination", illumination, litShare(MoonShape(illumination, litOnRight = true)), 0.01)
            assertEquals("aydınlanma $illumination", illumination, litShare(MoonShape(illumination, litOnRight = false)), 0.01)
        }
    }

    @Test
    fun `the lit side is right when waxing in the north and mirrored in the south`() {
        val waxingNorth = MoonShape.of(0.2, waxing = true, latitude = 41.04)
        assertTrue(waxingNorth.litOnRight)
        // Büyüyen hilal: sağ kenar aydınlık, orta ve sol karanlık.
        assertTrue(waxingNorth.isLit(0.95, 0.0))
        assertTrue(!waxingNorth.isLit(0.0, 0.0))
        assertTrue(!waxingNorth.isLit(-0.95, 0.0))
        val waningNorth = MoonShape.of(0.2, waxing = false, latitude = 53.41)
        assertTrue(!waningNorth.litOnRight)
        assertTrue(waningNorth.isLit(-0.95, 0.0))
        assertTrue(!waningNorth.isLit(0.95, 0.0))
        // Güney yarımkürede ayna görüntüsü.
        assertTrue(!MoonShape.of(0.2, waxing = true, latitude = -33.0).litOnRight)
        assertTrue(MoonShape.of(0.2, waxing = false, latitude = -33.0).litOnRight)
        // Şişkin ay: merkez de aydınlık, yalnız karşı kenar karanlık.
        val gibbous = MoonShape.of(0.8, waxing = true, latitude = 41.04)
        assertTrue(gibbous.isLit(0.0, 0.0))
        assertTrue(!gibbous.isLit(-0.95, 0.0))
        // Dolunayda her yer, yeni ayda hiçbir yer; çemberin dışı hiçbir zaman.
        assertTrue(MoonShape(1.0, true).isLit(-0.99, 0.0))
        assertTrue(!MoonShape(0.0, true).isLit(0.5, 0.0))
        assertTrue(!MoonShape(1.0, true).isLit(0.9, 0.9))
    }

    @Test
    fun `the scene moon follows the phase of the day`() {
        // 12 Ekim 2026: yeni aydan iki gün sonra, ince büyüyen hilal (dolunay değil). 26 Ekim: dolunay.
        val crescent = MoonShape.at(Instant.parse("2026-10-12T19:00:00Z"), 41.04)
        assertTrue(crescent.illumination < 0.1)
        assertTrue(crescent.litOnRight)
        assertTrue(crescent.terminator > 0.8)
        val full = MoonShape.at(Instant.parse("2026-10-26T04:00:00Z"), 41.04)
        assertTrue(full.illumination > 0.99)
        assertEquals(-1.0, full.terminator, 0.02)
        // Dolunaydan sonra küçülür: aydınlık yan sola geçer.
        assertTrue(!MoonShape.at(Instant.parse("2026-10-30T19:00:00Z"), 41.04).litOnRight)
    }
}
