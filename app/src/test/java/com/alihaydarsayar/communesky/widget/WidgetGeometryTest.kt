package com.alihaydarsayar.communesky.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.widget.config.WidgetSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/** Işınsal saatin çizgileri ve önizlemenin boyut seçimi. */
class WidgetGeometryTest {

    @Test
    fun `sixty lines of equal length point at the centre`() {
        val w = 340f
        val h = 150f
        val lines = WidgetBitmaps.radialLines(w, h, corner = 28f)
        assertEquals(60, lines.size)
        lines.forEachIndexed { i, line ->
            assertEquals("çizgi $i boyu", WidgetBitmaps.RADIAL_LENGTH, hypot(line.outerX - line.innerX, line.outerY - line.innerY), 0.05f)
            // i. çizgi i. saniyenin açısında: saat 12'den başlar, saat yönünde 6°.
            val angle = (Math.toDegrees(atan2((line.outerX - w / 2).toDouble(), -(line.outerY - h / 2).toDouble())) + 360) % 360
            assertEquals("çizgi $i açısı", i * 6.0, if (i == 0 && angle > 359) angle - 360 else angle, 0.05)
            // Hiçbir çizgi kenara 6 dp'den fazla yaklaşmaz.
            assertTrue(line.outerX >= 5.9f && line.outerX <= w - 5.9f && line.outerY >= 5.9f && line.outerY <= h - 5.9f)
        }
        // Üstteki ve yandaki çizgiler kenardan tam 6 dp içeride.
        assertEquals(6f, lines[0].outerY, 0.05f)
        assertEquals(w - 6f, lines[15].outerX, 0.05f)
        assertEquals(h - 6f, lines[30].outerY, 0.05f)
        assertEquals(6f, lines[45].outerX, 0.05f)
    }

    @Test
    fun `the preview is drawn at the size the home screen uses`() {
        val sizes = WidgetKind.Hourly.sizes
        // Sığan en yakın basamak seçilir.
        assertEquals(DpSize(250.dp, 150.dp), WidgetSizes.responsive(sizes, DpSize(344.dp, 190.dp)))
        assertEquals(DpSize(250.dp, 100.dp), WidgetSizes.responsive(sizes, DpSize(344.dp, 120.dp)))
        assertEquals(DpSize(180.dp, 40.dp), WidgetSizes.responsive(sizes, DpSize(200.dp, 90.dp)))
        // Hiçbiri sığmıyorsa en küçüğü.
        assertEquals(DpSize(180.dp, 40.dp), WidgetSizes.responsive(sizes, DpSize(120.dp, 30.dp)))
        // Saat widget'ı gerçek boyutuyla çizilir; diğerleri basamağıyla, ama gerçek boyutta durur.
        val frame = DpSize(344.dp, 190.dp)
        assertEquals(frame, WidgetSizes.of(WidgetKind.Clock, frame).draw)
        assertEquals(DpSize(250.dp, 150.dp), WidgetSizes.of(WidgetKind.Hourly, frame).draw)
        assertEquals(frame, WidgetSizes.of(WidgetKind.Hourly, frame).frame)
    }

    @Test
    fun `numbers use the narrow digits and words do not`() {
        for (text in listOf("16°", "-3°", "−3°", "1022", "14", "%64", "64%", "7.5")) assertTrue(text, isNumeric(text, 14f))
        for (text in listOf("16° Açık", "19° / 12°", "18:00", "Y 19°", "km", "", "–")) assertTrue(text, !isNumeric(text, 14f))
        // Çok küçük yazılar (yüzde etiketleri) düz yazı tipinde kalır.
        assertTrue(!isNumeric("%45", 10f))
    }

    @Test
    fun `text weight follows the chosen weight`() {
        val normal = WidgetStyle()
        assertEquals("s5", fontKey(normal, WWeight.Medium, WFont.Text))
        assertEquals("s4", fontKey(normal.copy(weight = WeightChoice.Thin), WWeight.Medium, WFont.Text))
        assertEquals("s7", fontKey(normal.copy(weight = WeightChoice.Bold), WWeight.Medium, WFont.Text))
        assertEquals("s3", fontKey(normal.copy(weight = WeightChoice.Thin), WWeight.Light, WFont.Text))
        assertEquals("s7", fontKey(normal.copy(weight = WeightChoice.Bold), WWeight.Bold, WFont.Text))
        // Rakamlar: dar ve kalın; ışınsal saatin dakikası dar ve ince.
        assertEquals("dg", fontKey(normal, WWeight.Light, WFont.Digits))
        assertEquals("ol", fontKey(normal, WWeight.Light, WFont.Outline))
    }
}
