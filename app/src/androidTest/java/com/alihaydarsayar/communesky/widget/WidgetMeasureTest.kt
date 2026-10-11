package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.widget.config.WidgetSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Önce/sonra karşılaştırması için ölçüm ve ekran görüntüleri. Sonuçlar
 * Android/data/com.alihaydarsayar.communesky/files/measure/ altına yazılır:
 *
 * - `shots/tr/<Stil>_dark.png`, `<Stil>_light.png` ve `shots/en/...`: her stil, koyu ve açık duvar kâğıdında, önce Türkçe sonra İngilizce.
 * - `widgets.csv`: her stil için launcher'a giden resimlerin toplamı (bayt) ve görünüm sayısı.
 *   Çizim süreleri logdan okunur: adb logcat -s WidgetTiming
 *
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetMeasureTest {

    private var context = localized(InstrumentationRegistry.getInstrumentation().targetContext, "tr")
    private var input = WidgetSamples.input(context)
    private val out = File(context.getExternalFilesDir(null), "measure").apply { mkdirs() }

    /**
     * İki set: önce Türkçe (shots/tr), sonra İngilizce (shots/en). Her sette metinler de tarih de
     * aynı dildedir: tarihi yazan saat bileşeni sürecin varsayılan dilini kullandığı için o da değiştirilir.
     */
    @Test
    fun screenshots() = runBlocking {
        val system = Locale.getDefault()
        try {
            for (language in listOf("tr", "en")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                context = localized(InstrumentationRegistry.getInstrumentation().targetContext, language)
                input = WidgetSamples.input(context)
                val shots = File(out, "shots/$language").apply { mkdirs() }
                for (style in WidgetStyleId.entries) {
                    val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle())
                    for (light in listOf(false, true)) {
                        val bitmap = draw(style, config, light)
                        File(shots, "${style.name}_${if (light) "light" else "dark"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        } finally {
            Locale.setDefault(system)
            context = localized(InstrumentationRegistry.getInstrumentation().targetContext, "tr")
            input = WidgetSamples.input(context)
        }
    }

    @Test
    fun drawTimesAndSizes() = runBlocking {
        val lines = mutableListOf("style,size_dp,bitmap_bytes,views,parcel_bytes,all_bitmap_bytes,package_bytes")
        // Isınma: ilk çizim sınıfları ve yazı tiplerini yükler.
        repeat(3) { compose(WidgetStyleId.SmallClassic, WidgetConfig.default(WidgetKind.Small)) }
        // Her stil iki kez: varsayılan ayarıyla ve "Gökyüzü" arka planıyla (gradyan resmi ya da vektörü).
        for ((style, sky) in WidgetStyleId.entries.flatMap { listOf(it to false, it to true) }) {
            if (sky && (style.isAnalog || style.defaultStyle().background == BackgroundKind.Sky)) continue
            val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle().let { if (sky) it.withBackground(BackgroundKind.Sky) else it })
            // Çizim süresini uygulamanın kendi ölçümü loglar ("adb logcat -s WidgetTiming"); burada her
            // stil yedi kez çizilir, süreler logdan okunur.
            repeat(6) { compose(style, config) }
            // Bu çizimde widget'a verilen bütün resimler (kollar dahil) ve RemoteViews paketinin kendisi.
            val handed = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>()))
            WidgetBitmaps.handedOut = handed
            val views = compose(style, config)
            WidgetBitmaps.handedOut = null
            val allBitmaps = handed.sumOf { it.allocationByteCount }
            val parcel = android.os.Parcel.obtain()
            views.writeToParcel(parcel, 0)
            val parcelBytes = parcel.dataSize()
            parcel.recycle()
            val (bytes, count) = withContext(Dispatchers.Main) {
                val root = views.apply(context, FrameLayout(context))
                val bitmaps = java.util.IdentityHashMap<Bitmap, Boolean>()
                collectBitmaps(root, bitmaps)
                bitmaps.keys.sumOf { it.allocationByteCount } to countViews(root)
            }
            val size = WidgetSizes.of(style.kind, style.previewSize).draw
            lines += "%s,%dx%d,%d,%d,%d,%d,%d".format(Locale.US, style.name + (if (sky) "+Sky" else ""), size.width.value.toInt(), size.height.value.toInt(), bytes, count, parcelBytes, allBitmaps, parcelBytes + allBitmaps)
        }
        File(out, "widgets.csv").writeText(lines.joinToString("\n"))
    }

    /**
     * Launcher tarafındaki maliyet (uygulamanın kendi çizim süresine girmez): widget'ın görünümlerini
     * kurma (RemoteViews.apply) ve bir kez çizme süresi. Saat her dakika değişince launcher bu çizimi
     * yeniden yapar. Sonuç: adb logcat -s WidgetCost
     */
    @Test
    fun launcherCost() = runBlocking {
        for (style in listOf(WidgetStyleId.ClockRadial, WidgetStyleId.ClockBig)) {
            val views = compose(style, WidgetConfig(WidgetPlace.Smart, style.defaultStyle()))
            val density = context.resources.displayMetrics.density
            val width = (style.previewSize.width.value * density).toInt()
            val height = (style.previewSize.height.value * density).toInt()
            withContext(Dispatchers.Main) {
                fun build(): View = views.apply(context, FrameLayout(context)).also {
                    it.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    it.layout(0, 0, width, height)
                }
                val canvas = Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
                repeat(10) { build().draw(canvas) }
                val applyMs = (1..30).map { val t = System.nanoTime(); build(); (System.nanoTime() - t) / 1e6 }.sorted()
                val view = build()
                val drawMs = (1..60).map { val t = System.nanoTime(); view.draw(canvas); (System.nanoTime() - t) / 1e6 }.sorted()
                android.util.Log.i("WidgetCost", "%s views=%d apply_median=%.2fms draw_median=%.2fms".format(Locale.US, style.name, countViews(view), applyMs[applyMs.size / 2], drawMs[drawMs.size / 2]))
            }
        }
    }

    private suspend fun compose(style: WidgetStyleId, config: WidgetConfig): RemoteViews {
        val size: DpSize = WidgetSizes.of(style.kind, style.previewSize).draw
        return style.kind.widget(config, input).compose(context, size = size)
    }

    private suspend fun draw(style: WidgetStyleId, config: WidgetConfig, lightWallpaper: Boolean): Bitmap {
        WallpaperTone.override = lightWallpaper
        val views = try {
            compose(style, config)
        } finally {
            WallpaperTone.override = null
        }
        val density = context.resources.displayMetrics.density
        val width = (style.previewSize.width.value * density).toInt()
        val height = (style.previewSize.height.value * density).toInt()
        return withContext(Dispatchers.Main) {
            val view = views.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(if (lightWallpaper) 0xFFE9EEF3.toInt() else 0xFF44564F.toInt())
                view.draw(canvas)
            }
        }
    }

    /**
     * Widget'ın launcher'a gönderdiği resimler: çizilen görünümlerdeki (resim ve arka plan) bitmap'ler,
     * her biri bir kez sayılır. Vektörler ve AnalogClock'un kolları sayılmaz.
     */
    private fun collectBitmaps(view: View, into: java.util.IdentityHashMap<Bitmap, Boolean>) {
        fun add(drawable: android.graphics.drawable.Drawable?) {
            (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { into[it] = true }
        }
        add(view.background)
        if (view is android.widget.ImageView) add(view.drawable)
        if (view is ViewGroup) for (i in 0 until view.childCount) collectBitmaps(view.getChildAt(i), into)
    }

    private fun countViews(view: View): Int =
        1 + if (view is ViewGroup) (0 until view.childCount).sumOf { countViews(view.getChildAt(it)) } else 0

    private fun localized(base: Context, language: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }
}
