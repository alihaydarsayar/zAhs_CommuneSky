package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yazı–zemin kontrastı (WCAG): renk seçicideki ve ekleme ekranındaki uyarının dayandığı hesap. */
class ContrastTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `black on white is 21 to 1 and a colour on itself is 1 to 1`() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.001)
        assertEquals(21.0, Contrast.ratio(white, black), 0.001)
        assertEquals(1.0, Contrast.ratio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001)
    }

    @Test
    fun `known colour pairs match the published ratios`() {
        // #767676 beyaz üstünde 4,54:1: normal yazı için sınırı geçen en açık gri.
        assertEquals(4.54, Contrast.ratio(0xFF767676.toInt(), white), 0.01)
        assertTrue(Contrast.isReadable(0xFF767676.toInt(), white))
        // #777777 beyaz üstünde 4,48:1: sınırın hemen altında.
        assertEquals(4.48, Contrast.ratio(0xFF777777.toInt(), white), 0.01)
        assertFalse(Contrast.isReadable(0xFF777777.toInt(), white))
        // Saf kırmızı beyaz üstünde 4,0:1.
        assertEquals(4.0, Contrast.ratio(0xFFFF0000.toInt(), white), 0.01)
    }

    @Test
    fun `the default widget colours are readable`() {
        val ground = 0xFF121316.toInt()
        assertTrue(Contrast.ratio(0xFFF2F3F5.toInt(), ground) > 15)
        // İkincil yazı (%70) ve vurgu rengi de koyu zeminde okunur.
        assertTrue(Contrast.isReadable(0xB3F2F3F5.toInt(), ground))
        assertTrue(Contrast.isReadable(0xFFE5484D.toInt(), ground))
        // Açık kadranda koyu yazı.
        assertTrue(Contrast.isReadable(0xFF111214.toInt(), 0xFFF4F2EC.toInt()))
    }

    @Test
    fun `see-through text is blended with the background first`() {
        // %50 beyaz, siyah üstünde orta gri olur.
        assertEquals(0xFF808080.toInt(), Contrast.composite(0x80FFFFFF.toInt(), black))
        assertEquals(Contrast.ratio(0xFF808080.toInt(), black), Contrast.ratio(0x80FFFFFF.toInt(), black), 0.001)
        // Tamamen saydam yazı zeminle aynıdır.
        assertEquals(1.0, Contrast.ratio(0x00FFFFFF, black), 0.001)
        assertEquals(white, Contrast.composite(white, black))
    }

    @Test
    fun `light text on a light wallpaper is flagged and a veil helps`() {
        val text = 0xFFF2F3F5.toInt()
        val lightWallpaper = 0xFFE9EEF3.toInt()
        assertFalse(Contrast.isReadable(text, lightWallpaper))
        // %25 koyu perde kontrastı artırır.
        val veiled = Contrast.composite(0x40000000, lightWallpaper)
        assertTrue(Contrast.ratio(text, veiled) > Contrast.ratio(text, lightWallpaper))
    }

    @Test
    fun `hex colours are parsed and printed`() {
        assertEquals(0xFFE5484D.toInt(), Contrast.parseHex("#E5484D"))
        assertEquals(0xFFE5484D.toInt(), Contrast.parseHex(" e5484d "))
        assertEquals(0x80112233.toInt(), Contrast.parseHex("#80112233"))
        assertNull(Contrast.parseHex("#E548"))
        assertNull(Contrast.parseHex("zzzzzz"))
        assertEquals("#E5484D", Contrast.toHex(0xFFE5484D.toInt()))
        assertEquals(12, WidgetInk.Presets.size)
        assertEquals(12, WidgetInk.Presets.toSet().size)
    }
}
