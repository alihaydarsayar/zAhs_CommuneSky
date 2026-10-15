package com.alihaydarsayar.communesky.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Segmentli düğme: yazılar kısalacaksa alt alta dizilir. */
class SegmentedLayoutTest {

    @Test
    fun `labels that fit stay in one row`() {
        // 343 dp genişlikte üç seçenek: her kutu ~109 dp; en uzun yazı 95 dp.
        assertTrue(SegmentedLayout.fitsInOneRow(listOf(60f, 95f, 70f), 343f))
        // İki seçenek, dar da olsa sığar.
        assertTrue(SegmentedLayout.fitsInOneRow(listOf(20f, 20f), 200f))
        assertTrue(SegmentedLayout.fitsInOneRow(emptyList(), 100f))
    }

    @Test
    fun `labels that would be cut go one under the other`() {
        // Dört dil adı, ekran büyütülmüş telefonda: kutu ~63 dp, "Français" 70 dp.
        assertFalse(SegmentedLayout.fitsInOneRow(listOf(40f, 48f, 52f, 70f), 285f))
        // Aynı yazılar normal genişlikte (360 dp) sığar.
        assertTrue(SegmentedLayout.fitsInOneRow(listOf(40f, 48f, 52f, 70f), 360f))
    }
}
