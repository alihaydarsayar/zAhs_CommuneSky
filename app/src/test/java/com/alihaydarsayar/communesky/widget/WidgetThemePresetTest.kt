package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ayar ekranında hazır tema seçmek: tema bütünüyle uygulanır ve widget gerçekten değişir. */
class WidgetThemePresetTest {

    @Test
    fun `picking a theme clears the colors that were chosen one by one`() {
        val style = WidgetStyleId.ClockSide.defaultStyle().withBackground(BackgroundKind.Solid).copy(
            hourColor = 0xFFFFFFFF.toInt(),
            minuteColor = 0xFFFF3B3B.toInt(),
            accent = 0xFF30C85A.toInt(),
            lineColor = 0xFF78C8FF.toInt(),
            pillColor = 0xFF102A43.toInt(),
            customText = 0xFFFDE68A.toInt(),
            textColor = TextColorMode.Light,
        )
        assertTrue(style.hasOwnColors)
        val themed = style.withThemePreset(ColorTheme.Paper)
        assertEquals(ColorTheme.Paper, themed.colorTheme)
        assertFalse(themed.hasOwnColors)
        assertNull(themed.hourColor)
        assertNull(themed.minuteColor)
        assertNull(themed.accent)
        assertEquals(TextColorMode.Auto, themed.textColor)
        // Renkle ilgisi olmayan seçimler durur.
        assertEquals(style.corners, themed.corners)
        assertEquals(style.style, themed.style)
        assertEquals(style.contents, themed.contents)
    }

    @Test
    fun `a theme on a clear or glass background makes the background solid`() {
        for (background in listOf(BackgroundKind.Transparent, BackgroundKind.Glass)) {
            val style = WidgetStyleId.ClockSide.defaultStyle().withBackground(background)
            assertFalse(style.showsThemeGround)
            val themed = style.withThemePreset(ColorTheme.Paper)
            assertEquals(BackgroundKind.Solid, themed.background)
            assertEquals(0, themed.effectiveTransparency)
            assertTrue(themed.showsThemeGround)
        }
        // Zaten dolu bir arka plan olduğu gibi kalır.
        val solid = WidgetStyleId.ClockWeather.defaultStyle().withBackground(BackgroundKind.Sky).withTransparency(20)
        val themed = solid.withThemePreset(ColorTheme.Forest)
        assertEquals(BackgroundKind.Sky, themed.background)
        assertEquals(20, themed.effectiveTransparency)
    }

    @Test
    fun `only the styles that use a color list it`() {
        // "Bir bakışta" saat rakamı göstermez; kadranların kolları ayrı ayardır.
        assertFalse(WidgetStyleId.ClockGlance.hasClockDigits)
        assertFalse(WidgetStyleId.ClockSS.hasClockDigits)
        assertTrue(WidgetStyleId.ClockSide.hasClockDigits)
        assertFalse(WidgetStyleId.HourlyRow.hasClockDigits)
        // Vurgu rengi: bugünün adı, "şimdi" noktası, ışınsal saatte gün, kadranlarda saniye kolu.
        assertTrue(WidgetStyleId.WeeklyBars.usesAccent)
        assertTrue(WidgetStyleId.ClockRadial.usesAccent)
        assertFalse(WidgetStyleId.ClockSide.usesAccent)
        assertFalse(WidgetStyleId.SmallClassic.usesAccent)
    }
}
