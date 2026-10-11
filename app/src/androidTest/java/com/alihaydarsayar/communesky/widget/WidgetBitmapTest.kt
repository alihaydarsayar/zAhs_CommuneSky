package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Widget'a giden resimlerin kalitesi ve boyutu. */
@RunWith(AndroidJUnit4::class)
class WidgetBitmapTest {

    private val density = 2.625f

    /**
     * Gökyüzü gradyanı küçük çizilip widget'a yayılır. Yayılmış hâli, tam çözünürlükte çizilenle aynı
     * olmalı: hiçbir pikselde renk kanalı başına 3 birimden (255 üzerinden) fazla fark yok.
     */
    @Test
    fun theSmallGradientLooksLikeTheFullOne() {
        val colors = listOf(Color(0xFF2F6BD8), Color(0xFF4A8AE8), Color(0xFF7FB2F2))
        for ((widthDp, heightDp) in listOf(374f to 210f, 374f to 99f, 179f to 210f, 530f to 530f)) {
            val small = WidgetBitmaps.gradient(colors, widthDp, heightDp, density, radiusDp = 0f)
            // Çok küçük bir köşe yarıçapı: aynı gradyan, tam çözünürlükte.
            val full = WidgetBitmaps.gradient(colors, widthDp, heightDp, density, radiusDp = 0.001f)
            assertTrue("Küçük resim en az 50 kat küçük olmalı", small.byteCount * 50 <= full.byteCount)
            val stretched = Bitmap.createScaledBitmap(small, full.width, full.height, true)
            var worst = 0
            // Kenardaki bir piksellik şerit hariç (köşe yuvarlaması ve kenar örneklemesi).
            for (y in 2 until full.height - 2 step 3) for (x in 2 until full.width - 2 step 3) {
                val a = full.getPixel(x, y)
                val b = stretched.getPixel(x, y)
                worst = maxOf(worst, abs(AndroidColor.red(a) - AndroidColor.red(b)), abs(AndroidColor.green(a) - AndroidColor.green(b)), abs(AndroidColor.blue(a) - AndroidColor.blue(b)))
            }
            assertTrue("${widthDp.toInt()}x${heightDp.toInt()} dp: en büyük renk farkı $worst", worst <= 3)
        }
    }

    /** Resimler ekranın tam çözünürlüğünde çizilir; tek renkli şekiller piksel başına 1 bayttır. */
    @Test
    fun masksAreFullResolutionAndOneBytePerPixel() {
        val line = WidgetBitmaps.curveLine(listOf(10f, 20f, 15f, 30f), 342f, 120f, density, 255)
        assertEquals((342f * density).toInt().toFloat(), line.width.toFloat(), 1f)
        assertEquals(Bitmap.Config.ALPHA_8, line.config)
        assertEquals(line.width * line.height, line.byteCount)
        assertEquals(Bitmap.Config.ALPHA_8, WidgetBitmaps.sunArcDashed(342f, 48f, density, 255).config)
        assertEquals(Bitmap.Config.ALPHA_8, WidgetBitmaps.sunArcDone(0.4f, 342f, 48f, density, 255).config)
        assertEquals(Bitmap.Config.ALPHA_8, WidgetBitmaps.radialTicks(374f, 210f, density, 28f, 1f).config)
        // Haftalık çubuğun renk şeridi: 32 piksel.
        assertEquals(32 * 4, WidgetBitmaps.rangeStrip(Color(0xFF2DD4BF), Color(0xFFFF7A59)).byteCount)
    }

    /**
     * Işınsal saatin gradyan zeminli şablonu parçalara bölünür (orta: küçük gradyan; kenarlar: boyanan tek
     * kanallı maskeler). Parçalar üst üste çizilince tek, dört kanallı resimle aynı görüntüyü vermeli: açık ve
     * koyu bir zeminin üstünde hiçbir pikselde kanal başına 3 birimden (255 üzerinden) fazla fark yok (ortadaki küçük gradyanın payı; arka plan
     * gradyanıyla aynı sınır); boyutu en çok üçte biri.
     */
    @Test
    fun theSplitRadialCoverLooksLikeTheSingleImage() {
        val ground = listOf(Color(0xFF2F6BD8), Color(0xFF4A8AE8), Color(0xFF7FB2F2))
        for ((widthDp, heightDp) in listOf(374f to 210f, 340f to 150f, 530f to 530f)) {
            val corner = 28f
            val single = WidgetBitmaps.radialStencil(widthDp, heightDp, density, corner, ground)
            val cover = WidgetBitmaps.radialCover(widthDp, heightDp, density, corner, ground)!!
            assertTrue("Parçalar tek resmin en çok üçte biri olmalı", cover.bitmaps.sumOf { it.byteCount } * 3 <= single.byteCount)
            val w = single.width
            val h = single.height
            val band = cover.bandPx
            for (backdrop in listOf(AndroidColor.WHITE, AndroidColor.BLACK)) {
                val expected = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    canvas.drawColor(backdrop)
                    canvas.drawBitmap(single, 0f, 0f, null)
                }
                val actual = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    canvas.drawColor(backdrop)
                    fun strip(layers: List<RadialCover.Layer>, left: Int, top: Int) = layers.forEach { layer ->
                        val paint = android.graphics.Paint().apply { color = layer.color.toArgb() }
                        canvas.drawBitmap(layer.mask, left.toFloat(), top.toFloat(), paint)
                    }
                    strip(cover.top, 0, 0)
                    strip(cover.bottom, 0, h - band)
                    strip(cover.left, 0, band)
                    strip(cover.right, w - band, band)
                    canvas.drawBitmap(cover.center, null, android.graphics.Rect(band, band, w - band, h - band), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                }
                // Karşılaştırma görüntüleri ve boyutlar (files/kalite): önce = tek resim, sonra = parçalar.
                val out = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "kalite").apply { mkdirs() }
                val name = "radial_${widthDp.toInt()}x${heightDp.toInt()}_${if (backdrop == AndroidColor.WHITE) "acik" else "koyu"}"
                java.io.File(out, name + "_once.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
                java.io.File(out, name + "_sonra.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
                android.util.Log.i("WidgetBitmapTest", "$name tek=${single.byteCount} parcali=${cover.bitmaps.sumOf { it.byteCount }}")
                var worst = 0
                for (y in 0 until h) for (x in 0 until w) {
                    val a = expected.getPixel(x, y)
                    val b = actual.getPixel(x, y)
                    worst = maxOf(worst, abs(AndroidColor.red(a) - AndroidColor.red(b)), abs(AndroidColor.green(a) - AndroidColor.green(b)), abs(AndroidColor.blue(a) - AndroidColor.blue(b)))
                }
                assertTrue("${widthDp.toInt()}x${heightDp.toInt()} dp, zemin ${Integer.toHexString(backdrop)}: en büyük renk farkı $worst", worst <= 3)
                android.util.Log.i("WidgetBitmapTest", "$name en buyuk fark=$worst")
            }
        }
    }
}
