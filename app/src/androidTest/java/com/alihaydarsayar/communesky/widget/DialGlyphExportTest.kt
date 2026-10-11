package com.alihaydarsayar.communesky.widget

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathIterator
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Analog saha" kadranındaki rakamların vektör yollarını üretir (res/drawable/widget_dial_field_n1…n24, _outer ve
 * _inner bu çıktıdan yazıldı). Rakamlar uygulamanın dar rakam yazı tipiyle, 200 birimlik kadrana göre
 * yerleştirilir; launcher uygulamanın yazı tipini yükleyemediği için yazı olarak değil yol olarak çizilir.
 * Çıktı: Android/data/<paket>/files/dial/glyphs.txt ("ad=yol"). Android 14+ gerekir (PathIterator).
 */
@RunWith(AndroidJUnit4::class)
class DialGlyphExportTest {

    @Test
    fun export() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.commune_digits)
            textAlign = Paint.Align.CENTER
        }
        val center = 100f
        val radius = 100f
        fun numeral(value: Int, at: Float, size: Float): Path {
            val a = Math.toRadians(value % 12 * 30.0)
            paint.textSize = size
            val x = center + sin(a).toFloat() * radius * at
            val y = center - cos(a).toFloat() * radius * at
            val text = value.toString()
            // WidgetBitmaps.dial ile aynı yerleşim: görsel orta, büyük harf yüksekliğinin yarısı.
            return Path().also { paint.getTextPath(text, 0, text.length, x, y + size * 0.35f, it) }
        }
        val lines = mutableListOf<String>()
        for (n in 1..12) lines += "n$n=" + data(numeral(n, 0.74f, radius * 0.27f))
        for (n in 13..24) lines += "n$n=" + data(numeral(n, 0.47f, radius * 0.115f))
        File(context.getExternalFilesDir(null), "dial").apply { mkdirs() }.resolve("glyphs.txt").writeText(lines.joinToString("\n"))
    }

    private fun data(path: Path): String {
        val out = StringBuilder()
        val points = FloatArray(8)
        fun f(i: Int) = String.format(Locale.ROOT, "%.2f", points[i])
        val iterator = path.pathIterator
        while (iterator.hasNext()) {
            when (iterator.next(points, 0)) {
                PathIterator.VERB_MOVE -> out.append("M${f(0)},${f(1)}")
                PathIterator.VERB_LINE -> out.append("L${f(2)},${f(3)}")
                PathIterator.VERB_QUAD -> out.append("Q${f(2)},${f(3)} ${f(4)},${f(5)}")
                PathIterator.VERB_CUBIC -> out.append("C${f(2)},${f(3)} ${f(4)},${f(5)} ${f(6)},${f(7)}")
                PathIterator.VERB_CLOSE -> out.append("Z")
                // Yazı yollarında konik parça olmaz.
                else -> error("Beklenmeyen yol parçası")
            }
        }
        return out.toString()
    }
}
