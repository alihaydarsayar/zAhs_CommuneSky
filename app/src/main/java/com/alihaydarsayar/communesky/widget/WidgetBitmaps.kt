package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Widget'ta Glance'in çizemediği şekiller (gradyan, sıcaklık eğrisi, güneş yayı, sıcaklık aralığı)
 * küçük resimler olarak çizilir. Yazı hiçbir zaman resme çevrilmez: rakamlar ve etiketler sistem
 * yazı tipiyle, Glance'in yazısıyla çizilir.
 *
 * Resimler girdilerine göre önbelleğe alınır: aynı veriyle ikinci kez çizilmez, sadece veri
 * (ya da boyut, renk) değişince yeniden üretilir.
 */
object WidgetBitmaps {

    /** Widget'a giden resimlerin toplam boyutu sınırlı; büyük widget'larda çözünürlük düşürülür. */
    private const val MAX_SIDE_PX = 720

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private inline fun cached(key: String, create: () -> Bitmap): Bitmap =
        cache.get(key) ?: create().also { cache.put(key, it) }

    /** dp ölçüsünü, sınırı aşmayacak bir piksel boyuta çevirir. */
    fun pixels(widthDp: Float, heightDp: Float, density: Float): Triple<Int, Int, Float> {
        var scale = density
        val longest = max(widthDp, heightDp) * scale
        if (longest > MAX_SIDE_PX) scale *= MAX_SIDE_PX / longest
        return Triple(max(1, (widthDp * scale).roundToInt()), max(1, (heightDp * scale).roundToInt()), scale)
    }

    /**
     * Arka plan gradyanı (tasarımdaki 165°: sol üstten sağ alta). [radiusDp] > 0 ise köşeler
     * yuvarlatılır (Android 12 öncesi; sonrasında köşeyi sistem keser). [roundLeft]/[roundRight]:
     * ikiye bölünmüş widget'ta sadece dış köşeler yuvarlanır.
     */
    fun gradient(
        colors: List<Color>,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        radiusDp: Float,
        roundLeft: Boolean = true,
        roundRight: Boolean = true,
        roundBottom: Boolean = true,
    ): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val key = "g:${colors.joinToString { it.toArgb().toString() }}:$w:$h:$radiusDp:$roundLeft$roundRight$roundBottom"
        return cached(key) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val angle = Math.toRadians(165.0 - 90.0)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val half = (kotlin.math.abs(w * dx) + kotlin.math.abs(h * dy)) / 2f
                val cx = w / 2f
                val cy = h / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                        colors.map { it.toArgb() }.toIntArray(),
                        if (colors.size == 3) floatArrayOf(0f, 0.55f, 1f) else null,
                        Shader.TileMode.CLAMP,
                    )
                }
                val r = radiusDp * scale
                if (r <= 0f) {
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                } else {
                    val radii = floatArrayOf(
                        if (roundLeft) r else 0f, if (roundLeft) r else 0f,
                        if (roundRight) r else 0f, if (roundRight) r else 0f,
                        if (roundRight && roundBottom) r else 0f, if (roundRight && roundBottom) r else 0f,
                        if (roundLeft && roundBottom) r else 0f, if (roundLeft && roundBottom) r else 0f,
                    )
                    val path = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radii, Path.Direction.CW) }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    /**
     * Saatlik sıcaklık eğrisi ve yağış ihtimali çubukları. Noktaların yeri [pointsYDp] ile verilir;
     * üstlerindeki sıcaklık yazıları Glance'te aynı sütunlara konur, böylece hizalı kalır.
     */
    fun hourlyCurve(
        pointsYDp: List<Float>,
        barHeightsDp: List<Float>,
        widthDp: Float,
        heightDp: Float,
        barBottomDp: Float,
        density: Float,
        line: Color,
        bar: Color,
    ): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val key = "c:${pointsYDp.joinToString()}:${barHeightsDp.joinToString()}:$w:$h:$barBottomDp:${line.toArgb()}:${bar.toArgb()}"
        return cached(key) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val n = pointsYDp.size
                fun x(i: Int) = (i + 0.5f) / n * w
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar.toArgb() }
                val barWidth = min(12f * scale, w / n * 0.4f)
                barHeightsDp.forEachIndexed { i, height ->
                    if (height <= 0f) return@forEachIndexed
                    val bottom = barBottomDp * scale
                    canvas.drawRoundRect(
                        RectF(x(i) - barWidth / 2, bottom - height * scale, x(i) + barWidth / 2, bottom),
                        2f * scale, 2f * scale, barPaint,
                    )
                }
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * scale
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                val path = Path()
                pointsYDp.forEachIndexed { i, y -> if (i == 0) path.moveTo(x(i), y * scale) else path.lineTo(x(i), y * scale) }
                canvas.drawPath(path, linePaint)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line.toArgb() }
                pointsYDp.forEachIndexed { i, y -> canvas.drawCircle(x(i), y * scale, (if (i == 0) 3.5f else 2.5f) * scale, dot) }
            }
        }
    }

    /** Haftalık widget'ın renkli sıcaklık aralığı çubuğu (haftanın en düşük–en yüksek aralığına göre). */
    fun rangeBar(start: Float, end: Float, widthDp: Float, heightDp: Float, density: Float, track: Color): Bitmap {
        val (w, h, _) = pixels(widthDp, heightDp, density)
        val s = (start.coerceIn(0f, 1f) * 100).roundToInt()
        val e = (end.coerceIn(0f, 1f) * 100).roundToInt()
        return cached("r:$s:$e:$w:$h:${track.toArgb()}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val r = h / 2f
                canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track.toArgb() })
                val left = w * s / 100f
                val right = max(left + h, w * e / 100f).coerceAtMost(w.toFloat())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        left, 0f, right, 0f,
                        intArrayOf(0xFF7DD3FC.toInt(), 0xFF86EFAC.toInt(), 0xFFFDE047.toInt(), 0xFFFB923C.toInt()),
                        floatArrayOf(0f, 0.4f, 0.75f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRoundRect(RectF(left, 0f, right, h.toFloat()), r, r, paint)
            }
        }
    }

    /** Güneş yayı: kesikli yay, geçen kısım dolu, şu anki konumda bir nokta. */
    fun sunArc(progress: Float, widthDp: Float, heightDp: Float, density: Float, accent: Color, faint: Color, horizon: Color): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val p = (progress.coerceIn(0f, 1f) * 200).roundToInt()
        return cached("s:$p:$w:$h:${accent.toArgb()}:${faint.toArgb()}:${horizon.toArgb()}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val pad = 8f * scale
                val baseY = h - 4f * scale
                val arc = Path().apply {
                    moveTo(pad, baseY)
                    quadTo(w / 2f, -baseY * 0.85f, w - pad, baseY)
                }
                canvas.drawLine(0f, baseY, w.toFloat(), baseY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = horizon.toArgb()
                    strokeWidth = 1f * scale
                })
                canvas.drawPath(arc, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = faint.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * scale
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(3f * scale, 6f * scale), 0f)
                })
                val measure = PathMeasure(arc, false)
                val done = Path()
                val length = measure.length * p / 200f
                measure.getSegment(0f, length, done, true)
                canvas.drawPath(done, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * scale
                    strokeCap = Paint.Cap.ROUND
                })
                val position = FloatArray(2)
                measure.getPosTan(length, position, null)
                canvas.drawCircle(position[0], position[1], 6f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.toArgb() })
            }
        }
    }
}
