package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.hypot
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
        now: Color = line,
    ): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val key = "c:${pointsYDp.joinToString()}:${barHeightsDp.joinToString()}:$w:$h:$barBottomDp:${line.toArgb()}:${bar.toArgb()}:${now.toArgb()}"
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
                val nowDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = now.toArgb() }
                pointsYDp.forEachIndexed { i, y -> canvas.drawCircle(x(i), y * scale, (if (i == 0) 4f else 2.5f) * scale, if (i == 0) nowDot else dot) }
            }
        }
    }

    /** Haftalık widget'ın renkli sıcaklık aralığı çubuğu (haftanın en düşük–en yüksek aralığına göre). */
    fun rangeBar(
        start: Float,
        end: Float,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        track: Color,
        cool: Color = WidgetInk.Cool,
        warm: Color = WidgetInk.Warm,
    ): Bitmap {
        val (w, h, _) = pixels(widthDp, heightDp, density)
        val s = (start.coerceIn(0f, 1f) * 100).roundToInt()
        val e = (end.coerceIn(0f, 1f) * 100).roundToInt()
        return cached("r:$s:$e:$w:$h:${track.toArgb()}:${cool.toArgb()}:${warm.toArgb()}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val r = h / 2f
                canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track.toArgb() })
                val left = w * s / 100f
                val right = max(left + h, w * e / 100f).coerceAtMost(w.toFloat())
                // Renk haftanın bütün aralığına göre: serin günler turkuaz, sıcak günler mercan.
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), 0f, cool.toArgb(), warm.toArgb(), Shader.TileMode.CLAMP)
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

    /**
     * Hafif perde: yazının arkasında kenarları yumuşak, yarı saydam katman. Küçük çizilir ve
     * widget'a yayılır (yumuşak bir geçişte çözünürlük fark edilmez).
     */
    fun scrim(widthDp: Float, heightDp: Float, density: Float, color: Color): Bitmap {
        val w = 96
        val h = max(24, (96 * heightDp / max(1f, widthDp)).roundToInt()).coerceAtMost(192)
        return cached("m:$w:$h:${color.toArgb()}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val solid = color.toArgb()
                val clear = color.copy(alpha = 0f).toArgb()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                // Elips biçiminde: ortası dolu, kenarlara doğru söner.
                canvas.save()
                canvas.scale(1f, h / w.toFloat())
                paint.shader = RadialGradient(w / 2f, w / 2f, w * 0.62f, intArrayOf(solid, solid, clear), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), w.toFloat(), paint)
                canvas.restore()
            }
        }
    }

    // --- Işınsal saat ---------------------------------------------------------------------------------

    /** Işınsal saatin bir çizgisi (dp): dış uç kenara yakın, iç uç merkeze doğru. */
    data class RadialLine(val outerX: Float, val outerY: Float, val innerX: Float, val innerY: Float)

    /**
     * 60 ışınsal çizgi: her biri merkezden çıkan ışın üzerinde, hepsi [length] boyunda ve widget
     * kenarından [inset] kadar içeride (köşeler [corner] yarıçapıyla yuvarlak). i. çizgi i. saniyenin
     * açısındadır (saat 12'den başlar, saat yönünde 6°). Saf hesap; test edilir.
     */
    fun radialLines(widthDp: Float, heightDp: Float, corner: Float, inset: Float = RADIAL_INSET, length: Float = RADIAL_LENGTH): List<RadialLine> {
        val cx = widthDp / 2f
        val cy = heightDp / 2f
        val left = inset
        val top = inset
        val right = widthDp - inset
        val bottom = heightDp - inset
        val r = (corner - inset).coerceIn(0f, min(right - left, bottom - top) / 2f)
        fun inside(x: Float, y: Float): Boolean {
            if (x < left || x > right || y < top || y > bottom) return false
            val nx = if (x < left + r) left + r else if (x > right - r) right - r else x
            val ny = if (y < top + r) top + r else if (y > bottom - r) bottom - r else y
            return hypot(x - nx, y - ny) <= r + 0.001f
        }
        return (0 until 60).map { i ->
            val angle = Math.toRadians(i * 6.0)
            val dx = sin(angle).toFloat()
            val dy = -cos(angle).toFloat()
            var lo = 0f
            var hi = hypot(widthDp, heightDp)
            repeat(24) {
                val mid = (lo + hi) / 2f
                if (inside(cx + dx * mid, cy + dy * mid)) lo = mid else hi = mid
            }
            val inner = max(0f, lo - length)
            RadialLine(cx + dx * lo, cy + dy * lo, cx + dx * inner, cy + dy * inner)
        }
    }

    const val RADIAL_INSET = 6f
    const val RADIAL_LENGTH = 14f
    private const val RADIAL_STROKE = 1.5f

    /** Çizgiler, saydam zeminde (saniye efekti yokken ya da cam ve saydam arka planlarda). */
    fun radialTicks(widthDp: Float, heightDp: Float, density: Float, corner: Float, color: Color): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("rt:$w:$h:$corner:${color.toArgb()}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawRadial(Canvas(bitmap), widthDp, heightDp, corner, scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
            }
        }
    }

    /**
     * Şablon (stencil): zemin renginde (ya da gradyanında) dolu, sadece çizgilerin olduğu yerde
     * delikler var. Arkasında dönen saniye yelpazesi yalnızca bu deliklerden görünür; böylece
     * çizgiler saniyeyle birlikte parlar ve uygulama hiç uyanmaz.
     */
    fun radialStencil(widthDp: Float, heightDp: Float, density: Float, corner: Float, ground: List<Color>): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("rs:$w:$h:$corner:${ground.joinToString { it.toArgb().toString() }}") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                if (ground.size == 1) {
                    canvas.drawColor(ground[0].toArgb())
                } else {
                    canvas.drawBitmap(gradient(ground, widthDp, heightDp, density, 0f), null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                }
                drawRadial(canvas, widthDp, heightDp, corner, scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
            }
        }
    }

    private fun drawRadial(canvas: Canvas, widthDp: Float, heightDp: Float, corner: Float, scale: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = RADIAL_STROKE * scale
        paint.strokeCap = Paint.Cap.ROUND
        radialLines(widthDp, heightDp, corner).forEach {
            canvas.drawLine(it.outerX * scale, it.outerY * scale, it.innerX * scale, it.innerY * scale, paint)
        }
    }

    // --- Analog kadranlar -----------------------------------------------------------------------------

    enum class Dial { Plain, Field, Ring }

    /**
     * Analog saatin kadranı. Kadran sabittir: yalnızca ayar (renkler, vurgulu rakamlar) ya da hava
     * halkasının verisi değişince yeniden çizilir; kolları Android'in AnalogClock'u çizer.
     *
     * [marks]: çizgi ve rakam rengi; [highlighted]: vurgu renginde gösterilecek rakamlar (Saha);
     * [arcs]: önümüzdeki 12 saatin renkleri, [arcStartHour] saatinden başlayarak (Hava halkası).
     */
    fun dial(
        kind: Dial,
        sizeDp: Float,
        density: Float,
        ground: Color,
        marks: Color,
        accent: Color,
        typeface: Typeface? = null,
        highlighted: Set<Int> = emptySet(),
        arcs: List<Color> = emptyList(),
        arcStartHour: Int = 0,
    ): Bitmap {
        val (px, _, scale) = pixels(sizeDp, sizeDp, density)
        val key = "d:$kind:$px:${ground.toArgb()}:${marks.toArgb()}:${accent.toArgb()}:${highlighted.sorted()}:${arcs.joinToString { it.toArgb().toString() }}:$arcStartHour"
        return cached(key) {
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val c = px / 2f
                val r = c - 2f * scale
                canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ground.toArgb() })
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.BUTT
                }
                fun tick(index: Int, of: Int, from: Float, to: Float, width: Float, color: Color) {
                    val a = Math.toRadians(index * 360.0 / of)
                    val dx = sin(a).toFloat()
                    val dy = -cos(a).toFloat()
                    line.color = color.toArgb()
                    line.strokeWidth = width
                    canvas.drawLine(c + dx * r * from, c + dy * r * from, c + dx * r * to, c + dy * r * to, line)
                }
                when (kind) {
                    Dial.Plain -> {
                        for (i in 0 until 60) if (i % 5 != 0) tick(i, 60, 0.9f, 0.955f, r * 0.012f, marks)
                        for (i in 0 until 12) tick(i, 12, 0.78f, 0.955f, r * 0.045f, marks)
                    }
                    Dial.Field -> {
                        for (i in 0 until 60) tick(i, 60, 0.925f, 0.975f, r * (if (i % 5 == 0) 0.016f else 0.009f), marks.copy(alpha = 0.7f))
                        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        fun numeral(value: Int, radius: Float, size: Float, color: Color) {
                            val a = Math.toRadians(value % 12 * 30.0)
                            text.textSize = size
                            text.color = (if (value in highlighted) accent else color).toArgb()
                            val x = c + sin(a).toFloat() * r * radius
                            val y = c - cos(a).toFloat() * r * radius
                            // Rakamın görsel ortası: büyük harf yüksekliğinin yarısı.
                            canvas.drawText(value.toString(), x, y + size * 0.35f, text)
                        }
                        for (n in 1..12) numeral(n, 0.74f, r * 0.27f, marks)
                        for (n in 13..24) numeral(n, 0.47f, r * 0.115f, marks.copy(alpha = 0.6f))
                    }
                    Dial.Ring -> {
                        val stroke = r * 0.075f
                        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = stroke
                            strokeCap = Paint.Cap.ROUND
                        }
                        val box = RectF(c - r + stroke, c - r + stroke, c + r - stroke, c + r - stroke)
                        arcs.take(12).forEachIndexed { i, color ->
                            arc.color = color.toArgb()
                            // Yay, saatin kadrandaki yerinden bir sonraki saate kadar; uçlar arasında boşluk.
                            val start = (arcStartHour + i) % 12 * 30f - 90f + 5f
                            canvas.drawArc(box, start, 20f, false, arc)
                        }
                        line.strokeCap = Paint.Cap.ROUND
                        for (i in 0 until 12) {
                            val major = i % 3 == 0
                            tick(i, 12, if (major) 0.7f else 0.76f, 0.8f, r * (if (major) 0.03f else 0.014f), marks.copy(alpha = if (major) 1f else 0.5f))
                        }
                    }
                }
            }
        }
    }
}
