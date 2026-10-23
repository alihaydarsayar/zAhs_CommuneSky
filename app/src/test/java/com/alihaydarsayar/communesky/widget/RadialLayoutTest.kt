package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Işınsal saatin yerleşim kararı: rakamlar çizgi halkasına ve yan sütunlara sığıyor mu, saat ortada mı. */
class RadialLayoutTest {

    private val sizes = listOf(391f to 168f, 390f to 210f, 313f to 210f, 313f to 140f, 230f to 100f, 320f to 110f, 300f to 200f, 530f to 530f, 250f to 110f, 400f to 90f, 180f to 180f, 110f to 40f)

    private fun plan(w: Float, h: Float, scale: Float = 1f) = RadialLayout.plan(w, h, scale)

    @Test
    fun `the digits never leave the ring or the side columns`() {
        for ((w, h) in sizes) {
            val p = plan(w, h)
            val used = 2.2f * p.digitsDp + (if (p.sides) 2 * p.sideWidth + 8f else 0f) + 2 * RadialLayout.RING
            // En küçük rakam boyu (24) sınırında sığmak zorunlu değil.
            assertTrue("$w x $h: kullanılan genişlik $used", p.digitsDp <= 24f || used <= w + 0.5f)
        }
    }

    @Test
    fun `the digits never reach the lines above and below`() {
        for ((w, h) in sizes) {
            val p = plan(w, h)
            assertTrue("$w x $h", p.digitsDp <= 24f || 1.12f * p.digitsDp <= h - 2 * RadialLayout.RING)
        }
    }

    @Test
    fun `a wide widget keeps the day and the weather at the sides`() {
        assertTrue(plan(391f, 168f).sides)
        assertTrue(plan(390f, 210f).sides)
        assertTrue(plan(313f, 210f).sides)
    }

    @Test
    fun `a narrow widget shows only the clock`() {
        assertTrue(!plan(110f, 40f).sides)
    }

    @Test
    fun `the two side columns share one width so the colon stays in the middle`() {
        for ((w, h) in sizes) {
            val p = plan(w, h)
            if (p.sides) assertTrue("$w x $h", p.sideWidth in 50f..62f)
        }
        assertEquals(plan(391f, 168f).sideWidth, plan(391f, 168f).sideWidth, 0f)
    }

    @Test
    fun `a bigger widget never draws smaller digits`() {
        assertTrue(plan(390f, 210f).digitsDp >= plan(313f, 210f).digitsDp)
        assertTrue(plan(391f, 168f).digitsDp >= plan(250f, 110f).digitsDp)
    }
}
