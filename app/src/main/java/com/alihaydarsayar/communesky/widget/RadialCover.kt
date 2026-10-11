package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Işınsal saatin gradyan zeminli şablonu, küçük parçalar hâlinde.
 *
 * Şablon, dönen saniye yelpazesinin üstünü zeminin gradyanıyla örter; yelpaze yalnızca çizgilerin olduğu
 * deliklerden görünür. Tek resim olarak dört kanallı ve widget boyutundadır (4×2 hücrede yaklaşık 1,4 MB).
 * Burada aynı görüntü şöyle kurulur:
 *
 * - **Orta** (çizgilerin içinde kalan dikdörtgen): delik yoktur, yalnızca gradyan. Küçük çizilip yayılır.
 * - **Kenar şeritleri** (üst, alt, sol, sağ; çizgilerin bulunduğu bant): tam çözünürlükte, ama dört kanallı
 *   resim yerine gradyanın her durak rengi için tek kanallı bir maske. Maskeler üst üste, kendi renklerine
 *   boyanarak çizilir; saydamlıkları, üst üste bindiklerinde o pikselde tam olarak "gradyanın rengi × deliğin
 *   örtme oranı" çıkacak biçimde hesaplanır (aşağıda [layerAlphas]).
 *
 * Sonuç tek resimle aynıdır (kanal başına en çok 3/255 fark, arka plan gradyanıyla aynı sınır; bkz. WidgetBitmapTest),
 * boyutu dörtte bire yakındır.
 */
class RadialCover(
    /** Bandın kalınlığı (piksel): şeritlerin kısa kenarı. */
    val bandPx: Int,
    val top: List<Layer>,
    val bottom: List<Layer>,
    val left: List<Layer>,
    val right: List<Layer>,
    /** Ortadaki gradyan, küçük çizilmiş (dört kanallı). */
    val center: Bitmap,
) {
    /** Tek kanallı maske ve boyanacağı (opak) renk. */
    class Layer(val mask: Bitmap, val color: Color)

    val bitmaps: List<Bitmap> get() = (top + bottom + left + right).map { it.mask } + center

    companion object {

        private const val CENTER_DOWNSCALE = 8f

        /** Çizgilerin sığdığı bandın kalınlığı (dp): en içteki çizgi ucunun kenara uzaklığı ve küçük bir pay. */
        fun bandDp(widthDp: Float, heightDp: Float, corner: Float): Float {
            fun edge(x: Float, y: Float) = min(min(x, widthDp - x), min(y, heightDp - y))
            val deepest = WidgetBitmaps.radialLines(widthDp, heightDp, corner).maxOf { max(edge(it.outerX, it.outerY), edge(it.innerX, it.innerY)) }
            return deepest + WidgetBitmaps.RADIAL_STROKE / 2f + 1f
        }

        /** Bu boyutta parçalı şablonun toplam boyutu (bayt), resimleri çizmeden. [stops]: gradyanın durak sayısı. */
        fun estimateBytes(widthPx: Int, heightPx: Int, bandPx: Int, stops: Int): Long {
            val band = 2L * widthPx * bandPx + 2L * bandPx * (heightPx - 2 * bandPx).coerceAtLeast(0)
            val center = ((widthPx - 2 * bandPx) / CENTER_DOWNSCALE).toLong().coerceAtLeast(1) * ((heightPx - 2 * bandPx) / CENTER_DOWNSCALE).toLong().coerceAtLeast(1) * 4
            return band * stops + center
        }

        /**
         * Şablonun parçaları; widget, bandın iki katından dar ya da alçaksa null (o zaman tek resim çizilir).
         * [stencil]: tam boyutlu tek kanallı şablon maskesi (dolu, çizgilerde delik).
         */
        fun build(stencil: Bitmap, bandDp: Float, density: Float, ground: List<Color>): RadialCover? {
            val w = stencil.width
            val h = stencil.height
            val band = ceil(bandDp * density).toInt()
            if (ground.size !in 2..3 || w <= 2 * band + 8 || h <= 2 * band + 8) return null
            val rowBytes = stencil.rowBytes
            val coverage = ByteArray(rowBytes * h).also { stencil.copyPixelsToBuffer(ByteBuffer.wrap(it)) }
            val axis = GradientAxis(w, h)
            fun strip(left: Int, top: Int, right: Int, bottom: Int): List<Layer> {
                val sw = right - left
                val sh = bottom - top
                val masks = Array(ground.size) { Bitmap.createBitmap(sw, sh, Bitmap.Config.ALPHA_8) }
                val rows = masks[0].rowBytes
                val data = Array(ground.size) { ByteArray(rows * sh) }
                val used = BooleanArray(ground.size)
                val alphas = IntArray(ground.size)
                for (y in 0 until sh) for (x in 0 until sw) {
                    val k = coverage[(top + y) * rowBytes + left + x].toInt() and 0xFF
                    if (k == 0) continue
                    layerAlphas(k, weights(axis.at(left + x + 0.5f, top + y + 0.5f), ground.size), alphas)
                    for (i in alphas.indices) if (alphas[i] != 0) {
                        data[i][y * rows + x] = alphas[i].toByte()
                        used[i] = true
                    }
                }
                // Bu şeritte hiç görünmeyen durak rengi için katman çizilmez (ör. üst şeritte gradyanın son rengi).
                return masks.indices.filter { used[it] }.map { i ->
                    masks[i].copyPixelsFromBuffer(ByteBuffer.wrap(data[i]))
                    Layer(masks[i], ground[i])
                }
            }
            val cw = max(1, ((w - 2 * band) / CENTER_DOWNSCALE).roundToInt())
            val ch = max(1, ((h - 2 * band) / CENTER_DOWNSCALE).roundToInt())
            val pixels = IntArray(cw * ch)
            for (j in 0 until ch) for (i in 0 until cw) {
                val x = band + (i + 0.5f) * (w - 2 * band) / cw
                val y = band + (j + 0.5f) * (h - 2 * band) / ch
                pixels[j * cw + i] = colorAt(axis.at(x, y), ground).toArgb()
            }
            return RadialCover(
                bandPx = band,
                top = strip(0, 0, w, band),
                bottom = strip(0, h - band, w, h),
                left = strip(0, band, band, h - band),
                right = strip(w - band, band, w, h - band),
                center = Bitmap.createBitmap(pixels, cw, ch, Bitmap.Config.ARGB_8888),
            )
        }

        /** Gradyanın ekseni: [WidgetBitmaps.gradient] ile aynı hesap (165°, sol üstten sağ alta). */
        private class GradientAxis(w: Int, h: Int) {
            private val angle = Math.toRadians(165.0 - 90.0)
            private val dx = cos(angle).toFloat()
            private val dy = sin(angle).toFloat()
            private val half = (abs(w * dx) + abs(h * dy)) / 2f
            private val cx = w / 2f
            private val cy = h / 2f

            /** Noktanın gradyandaki konumu, 0–1. */
            fun at(x: Float, y: Float): Float = (((x - cx) * dx + (y - cy) * dy) / (2f * half) + 0.5f).coerceIn(0f, 1f)
        }

        /** Üç renkli gradyanda orta rengin konumu ([WidgetBitmaps.gradient] ile aynı). */
        private const val MIDDLE_STOP = 0.55f

        /** Gradyanın [t] konumunda her durak renginin payı (toplamı 1). Saf hesap; test edilir. */
        fun weights(t: Float, stops: Int): FloatArray = when {
            stops == 2 -> floatArrayOf(1f - t, t)
            t <= MIDDLE_STOP -> (t / MIDDLE_STOP).let { floatArrayOf(1f - it, it, 0f) }
            else -> ((t - MIDDLE_STOP) / (1f - MIDDLE_STOP)).let { floatArrayOf(0f, 1f - it, it) }
        }

        fun colorAt(t: Float, ground: List<Color>): Color {
            val w = weights(t, ground.size)
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in ground.indices) {
                r += ground[i].red * w[i]
                g += ground[i].green * w[i]
                b += ground[i].blue * w[i]
            }
            return Color(r, g, b)
        }

        /**
         * Üst üste çizilen katmanların saydamlıkları (0–255; [into] içine, alttan üste). [coverage] o pikselin
         * örtme oranı (0–255; delikte 0), [weights] durak renklerinin payları. Katmanlar sırayla üst üste
         * bindiğinde her rengin katkısı tam "pay × örtme", toplam örtme de [coverage] olur:
         *
         *   üstteki: a₂ = k·w₂;  ortadaki: a₁ = k·w₁ / (1 − a₂);  alttaki: a₀ = k·w₀ / ((1 − a₂)(1 − a₁))
         *
         * Her saydamlık 8 bite yuvarlanır ve alttakiler yuvarlanmış değerlere göre hesaplanır; yuvarlama
         * hatası birikmez. Saf hesap; test edilir.
         */
        fun layerAlphas(coverage: Int, weights: FloatArray, into: IntArray) {
            val k = coverage / 255f
            var through = 1f
            for (i in weights.indices.reversed()) {
                val alpha = if (through <= 0f) 0f else (k * weights[i] / through).coerceIn(0f, 1f)
                val rounded = (alpha * 255f).roundToInt()
                into[i] = rounded
                through *= 1f - rounded / 255f
            }
        }
    }
}
