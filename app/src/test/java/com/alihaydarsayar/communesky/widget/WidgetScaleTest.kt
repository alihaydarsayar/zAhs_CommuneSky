package com.alihaydarsayar.communesky.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.widget.config.WidgetSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget, launcher'ın bildirdiği boyuta nasıl uyar? Üretici launcher'ları 4×2 hücre için 250×110 dp
 * gibi, gerçek alandan küçük ölçüler bildirir; stil yine de kendi düzeniyle çizilmeli.
 */
class WidgetScaleTest {

    private fun size(width: Int, height: Int) = DpSize(width.dp, height.dp)

    @Test
    fun `a clock keeps its own layout at the sizes launchers report for a 4 by 2 widget`() {
        for (reported in listOf(size(245, 105), size(250, 110), size(320, 110), size(300, 200))) {
            for (style in WidgetKind.Clock.styles) {
                assertFalse("${style.name} ${reported.width}×${reported.height}", WidgetScale.compact(style, reported))
            }
        }
    }

    @Test
    fun `the shared small layout is only used when the style really cannot fit`() {
        // 2×1 hücre: hiçbir saat stili sığmaz.
        for (style in WidgetKind.Clock.styles) assertTrue(style.name, WidgetScale.compact(style, size(110, 40)))
        // Işınsal saat 200 dp'den dar alanda çizgilerini ve rakamlarını sığdıramaz.
        assertTrue(WidgetScale.compact(WidgetStyleId.ClockRadial, size(180, 180)))
        assertFalse(WidgetScale.compact(WidgetStyleId.ClockRadial, size(200, 84)))
        // Kadranlar kare çizilir; 3×3 hücrede kendi düzenleriyle.
        assertFalse(WidgetScale.compact(WidgetStyleId.ClockSS, size(180, 180)))
        // Yedek düzen yalnızca saatler içindir.
        assertFalse(WidgetScale.compact(WidgetStyleId.HourlyCurve, size(110, 40)))
    }

    @Test
    fun `a style that almost fits shrinks instead of switching layout`() {
        // Yan yana: tasarım 340×170, en küçük 300×150. 250×110'da %73'e küçülür.
        assertEquals(0.733f, WidgetScale.grow(WidgetStyleId.ClockSide, size(250, 110)), 0.01f)
        // Arada oran 1'dir: stil kendi kurallarıyla yerleşir.
        assertEquals(1f, WidgetScale.grow(WidgetStyleId.ClockSide, size(320, 160)), 0.001f)
        // Hiçbir zaman sınırın altına küçültülmez.
        for (style in WidgetStyleId.entries) {
            for (reported in listOf(size(110, 40), size(180, 180), size(245, 105), size(400, 90))) {
                val grow = WidgetScale.grow(style, reported)
                assertTrue("${style.name}: $grow", grow >= WidgetScale.FLOOR_OTHER)
            }
        }
    }

    @Test
    fun `content grows with the widget so that it fills the space`() {
        // 4×2 Pixel hücresi (373×210): tasarımdan biraz büyük.
        assertEquals(1.097f, WidgetScale.grow(WidgetStyleId.ClockSide, size(373, 210)), 0.01f)
        assertEquals(WidgetScale.MAX_GROW, WidgetScale.grow(WidgetStyleId.SmallClassic, size(530, 530)), 0.2f)
        // Kadranlar ve ışınsal saat her ölçüsünü kendisi hesaplar.
        assertEquals(1f, WidgetScale.grow(WidgetStyleId.ClockSS, size(530, 530)), 0.001f)
        assertEquals(1f, WidgetScale.grow(WidgetStyleId.ClockRadial, size(530, 530)), 0.001f)
    }

    @Test
    fun `a widget whose size is not reported is drawn at the design size`() {
        assertEquals(WidgetStyleId.ClockRadial.previewSize, WidgetScale.real(WidgetStyleId.ClockRadial, size(0, 0)))
        assertEquals(size(250, 110), WidgetScale.real(WidgetStyleId.ClockRadial, size(250, 110)))
    }

    @Test
    fun `every style fits at its smallest size and is larger at its design size`() {
        for (style in WidgetStyleId.entries) {
            val smallest = WidgetScale.fits(style)
            assertTrue(style.name, smallest.width <= style.previewSize.width && smallest.height <= style.previewSize.height)
            assertEquals(style.name, 1f, WidgetScale.grow(style, smallest), 0.001f)
        }
    }

    @Test
    fun `the preview uses the portrait size the launcher gives`() {
        // Dikey ekranın ölçüsü listedeki en dar olandır.
        assertEquals(size(245, 105), WidgetSizes.portrait(listOf(size(410, 90), size(245, 105))))
        assertEquals(null, WidgetSizes.portrait(emptyList()))
        // Bütün widget'lar bildirilen boyutla çizilir.
        val frame = size(344, 190)
        for (kind in WidgetKind.entries) assertEquals(frame, WidgetSizes.of(kind, frame).draw)
    }
}
