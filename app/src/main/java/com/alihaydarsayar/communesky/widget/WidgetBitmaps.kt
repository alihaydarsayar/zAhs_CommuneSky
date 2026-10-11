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
import android.graphics.RectF
import android.graphics.Shader
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
 * Widget'ta Glance'in ve sabit vektörlerin çizemediği, veriye ya da widget'ın tam boyutuna bağlı şekiller
 * (sıcaklık eğrisi, güneş yayı, ışınsal çizgiler, gökyüzü gradyanı, sıcaklık aralığının renk şeridi) resim
 * olarak çizilir. Resimler ekranın tam çözünürlüğündedir; küçük kalsınlar diye tek renkli şekiller tek
 * kanallı maske (piksel başına 1 bayt) olarak çizilir ve widget'ta boyanır. Kadranlar, kollar ve yumuşak
 * perde vektördür (res/drawable/widget_dial_*, widget_hand_*, widget_scrim). Yazı hiçbir zaman resme çevrilmez: rakamlar ve etiketler sistem
 * yazı tipiyle, Glance'in yazısıyla çizilir.
 *
 * Resimler girdilerine göre önbelleğe alınır: aynı veriyle ikinci kez çizilmez, sadece veri
 * (ya da boyut, renk) değişince yeniden üretilir.
 */
object WidgetBitmaps {

    /**
     * Köşesiz gökyüzü gradyanı bu oranda küçük çizilip widget'a yayılır. Düz bir renk geçişinde ayrıntı
     * yoktur: yayılmış hâli tam çözünürlükte çizilenle aynıdır (bkz. WidgetBitmapTest), resim 64'te bire iner.
     */
    private const val GRADIENT_DOWNSCALE = 8f

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Sistem bellek istediğinde (onTrimMemory) önbellek boşaltılır; resimler gerektiğinde yeniden çizilir. */
    fun clearCache() = cache.evictAll()

    /**
     * Ölçüm için (testler): açıkken bir çizimde widget'a verilen bütün resimler burada toplanır.
     * Normal çalışmada null'dır ve hiçbir şey tutulmaz.
     */
    @Volatile
    var handedOut: MutableSet<Bitmap>? = null

    private inline fun cached(key: String, create: () -> Bitmap): Bitmap =
        (cache.get(key) ?: create().also { cache.put(key, it) }).also { handedOut?.add(it) }

    /** dp ölçüsünü, sınırı aşmayacak bir piksel boyuta çevirir. */
    fun pixels(widthDp: Float, heightDp: Float, density: Float): Triple<Int, Int, Float> =
        Triple(max(1, (widthDp * density).roundToInt()), max(1, (heightDp * density).roundToInt()), density)

    /** Bir rengin saydamlığı, maskeye işlenecek düzey olarak (0–255). Maske widget'ta opak renkle boyanır. */
    fun level(color: Color): Int = (color.alpha.coerceIn(0f, 1f) * 255).roundToInt()

    private fun ink(level: Int) = android.graphics.Color.argb(level.coerceIn(0, 255), 0, 0, 0)

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
        // Köşe yuvarlanmayacaksa (Android 12+) gradyan küçük çizilir; yuvarlak köşe keskin kalsın diye tam boyut.
        val (w, h, scale) = pixels(widthDp, heightDp, if (radiusDp <= 0f) density / GRADIENT_DOWNSCALE else density)
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
     * Saatlik sıcaklık eğrisi: çizgi ve saat noktaları, tek kanallı maske. Noktaların yeri [pointsYDp] ile
     * verilir; üstlerindeki sıcaklık yazıları Glance'te aynı sütunlara konur, böylece hizalı kalır.
     * İlk ("şimdi") nokta burada yoktur: vurgu renginde ayrı bir vektördür.
     */
    fun curveLine(pointsYDp: List<Float>, widthDp: Float, heightDp: Float, density: Float, level: Int): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("cl:${pointsYDp.joinToString()}:$w:$h:$level") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                val canvas = Canvas(bitmap)
                val n = pointsYDp.size
                fun x(i: Int) = (i + 0.5f) / n * w
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink(level)
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * scale
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                val path = Path()
                pointsYDp.forEachIndexed { i, y -> if (i == 0) path.moveTo(x(i), y * scale) else path.lineTo(x(i), y * scale) }
                canvas.drawPath(path, linePaint)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink(level) }
                pointsYDp.forEachIndexed { i, y -> if (i > 0) canvas.drawCircle(x(i), y * scale, 2.5f * scale, dot) }
            }
        }
    }

    /**
     * Saatlik eğrinin altındaki yağış ihtimali çubukları, tek kanallı maske. Resim yalnızca çubukların
     * kapladığı şerit kadardır ([heightDp]: en yüksek çubuk); çubuklar alta yaslıdır.
     */
    fun curveBars(barHeightsDp: List<Float>, widthDp: Float, heightDp: Float, density: Float, level: Int): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("cb:${barHeightsDp.joinToString()}:$w:$h:$level") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                val canvas = Canvas(bitmap)
                val n = barHeightsDp.size
                fun x(i: Int) = (i + 0.5f) / n * w
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink(level) }
                val barWidth = min(12f * scale, w / n * 0.4f)
                barHeightsDp.forEachIndexed { i, height ->
                    if (height <= 0f) return@forEachIndexed
                    canvas.drawRoundRect(RectF(x(i) - barWidth / 2, h - height * scale, x(i) + barWidth / 2, h.toFloat()), 2f * scale, 2f * scale, paint)
                }
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

    /**
     * Sıcaklık aralığı çubuğunun renk şeridi: [from] renginden [to] rengine, 32×1 piksel. Widget'ta
     * çubuğun boyuna yayılır; uçlarını widget yuvarlar (Android 12+).
     */
    fun rangeStrip(from: Color, to: Color): Bitmap = cached("rs1:${from.toArgb()}:${to.toArgb()}") {
        Bitmap.createBitmap(RANGE_STRIP_PX, 1, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (i in 0 until RANGE_STRIP_PX) bitmap.setPixel(i, 0, mix(from, to, (i + 0.5f) / RANGE_STRIP_PX).toArgb())
        }
    }

    private const val RANGE_STRIP_PX = 32

    /** İki rengin düz (sRGB) karışımı: gradyanın o noktadaki rengi. */
    fun mix(from: Color, to: Color, t: Float): Color {
        val f = t.coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * f,
            green = from.green + (to.green - from.green) * f,
            blue = from.blue + (to.blue - from.blue) * f,
            alpha = from.alpha + (to.alpha - from.alpha) * f,
        )
    }

    /** Güneş yayının yolu ve ufuk çizgisinin yüksekliği (piksel). */
    private fun sunPath(w: Int, h: Int, scale: Float): Path {
        val pad = 8f * scale
        val baseY = h - 4f * scale
        return Path().apply {
            moveTo(pad, baseY)
            quadTo(w / 2f, -baseY * 0.85f, w - pad, baseY)
        }
    }

    /** Güneş yayı: bütün yol, kesikli. Tek kanallı maske; ufuk çizgisini widget çizer. */
    fun sunArcDashed(widthDp: Float, heightDp: Float, density: Float, level: Int): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("sd:$w:$h:$level") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                Canvas(bitmap).drawPath(sunPath(w, h, scale), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink(level)
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * scale
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(3f * scale, 6f * scale), 0f)
                })
            }
        }
    }

    /** Güneş yayı: geçen kısım dolu çizgi ve şu anki konumda nokta. Tek kanallı maske. */
    fun sunArcDone(progress: Float, widthDp: Float, heightDp: Float, density: Float, level: Int): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val p = (progress.coerceIn(0f, 1f) * 200).roundToInt()
        return cached("sp:$p:$w:$h:$level") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                val canvas = Canvas(bitmap)
                val measure = PathMeasure(sunPath(w, h, scale), false)
                val done = Path()
                val length = measure.length * p / 200f
                measure.getSegment(0f, length, done, true)
                canvas.drawPath(done, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink(level)
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * scale
                    strokeCap = Paint.Cap.ROUND
                })
                val position = FloatArray(2)
                measure.getPosTan(length, position, null)
                canvas.drawCircle(position[0], position[1], 6f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink(level) })
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

    /**
     * Çizgiler, saydam zeminde (saniye efekti yokken ya da cam ve saydam arka planlarda). Tek kanallı
     * maske (piksel başına 1 bayt, renkli resmin dörtte biri); rengi çizerken boya ile verilir.
     * Çizginin saydamlığı ([alpha]) maskenin içindedir: boya her sürümde aynı sonucu versin diye tam opak verilir.
     */
    fun radialTicks(widthDp: Float, heightDp: Float, density: Float, corner: Float, alpha: Float): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        val level = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        return cached("rt:$w:$h:$corner:$level") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                drawRadial(Canvas(bitmap), widthDp, heightDp, corner, scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(level, 0, 0, 0) })
            }
        }
    }

    /** Tek renkli zemin için şablon: tek kanallı maske (dolu, çizgilerde delik); zemin rengi boya ile verilir. */
    fun radialStencilMask(widthDp: Float, heightDp: Float, density: Float, corner: Float): Bitmap {
        val (w, h, scale) = pixels(widthDp, heightDp, density)
        return cached("rm:$w:$h:$corner") {
            Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.BLACK)
                drawRadial(canvas, widthDp, heightDp, corner, scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
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

    // --- Analog kadranlar: kadran ve kollar vektördür; burada yalnızca kadran türü kalır ---------------

    enum class Dial { Plain, Field, Ring }
}
