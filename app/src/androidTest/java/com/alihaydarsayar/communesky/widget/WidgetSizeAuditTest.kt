package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Üretici launcher'larının bildirdiği ölçüleri taklit eden denetim. Her stil, launcher'ların
 * bildirebildiği ölçülerde (eski "70n − 30" formülü dahil) çizilir ve her çizim için şunlar ölçülür:
 *
 * - hangi düzen seçildi (stilin kendisi mi, ortak yedek düzen mi);
 * - kesilen ya da "…" ile kısaltılan yazı var mı;
 * - içerik alanın ne kadarını dolduruyor (genişlik ve yükseklik yüzdesi).
 *
 * Sonuçlar Android/data/…/files/audit/<dil>/ altına: her çizimin görüntüsü ve `olcu.csv`.
 * Sıcaklık ve sayıların kısaltılması testi başarısız yapar.
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
@com.alihaydarsayar.communesky.SlowAudit
class WidgetSizeAuditTest {

    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    /** Launcher'ların bildirdiği ölçüler: 4×2 (eski formül ve biraz altı), 5×2, 4×3, geniş ve alçak, 2×1, 3×3, çok büyük. */
    private val sizes = listOf(
        DpSize(245.dp, 105.dp), DpSize(250.dp, 110.dp), DpSize(300.dp, 200.dp), DpSize(320.dp, 110.dp),
        DpSize(400.dp, 90.dp), DpSize(110.dp, 40.dp), DpSize(180.dp, 180.dp), DpSize(530.dp, 530.dp),
        DpSize(391.dp, 168.dp), DpSize(390.dp, 210.dp), DpSize(313.dp, 210.dp), DpSize(230.dp, 100.dp), DpSize(313.dp, 140.dp), DpSize(340.dp, 95.dp), DpSize(560.dp, 320.dp),
    )

    private data class Result(val cut: List<String>, val fillWidth: Int, val fillHeight: Int)

    @Test
    fun everyStyleAtLauncherSizes() = runBlocking {
        val system = Locale.getDefault()
        val shortened = mutableListOf<String>()
        try {
            for (language in listOf("tr", "en", "fr")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                val context = localized(target, language)
                val input = WidgetSamples.input(context)
                val out = File(context.getExternalFilesDir(null), "audit/$language").apply { mkdirs() }
                val lines = mutableListOf("style,size,layout,grow,fill_width_pct,fill_height_pct,cut_texts")
                // Tek bir stili ya da boyutu denemek için: -e style ClockSide,ClockBold -e size 530x530
                val onlyStyle = InstrumentationRegistry.getArguments().getString("style")
                val onlySize = InstrumentationRegistry.getArguments().getString("size")
                for (style in WidgetStyleId.entries.filter { onlyStyle == null || it.name in onlyStyle.split(",") }) {
                    // İçeriğin kapladığı alan ölçülebilsin diye arka plansız ve gölgesiz çizilir.
                    val measured = style.defaultStyle().let { if (style.scalesItself || style == WidgetStyleId.ClockBold) it else it.withBackground(BackgroundKind.Transparent).copy(legibility = Legibility.Off) }
                    // Launcher ölçüleri, stilin kendi düzeniyle çizildiği en küçük boyut ve ölçü bildirilmeyen durum.
                    val smallest = WidgetScale.fits(style)
                    for ((index, size) in (sizes + smallest + listOf<DpSize?>(null)).withIndex()) {
                        val label = when {
                            size == null -> "bildirilmedi"
                            index == sizes.size -> "enkucuk"
                            else -> "%dx%d".format(Locale.ROOT, size.width.value.toInt(), size.height.value.toInt())
                        }
                        if (onlySize != null && label != onlySize) continue
                        // Görüntü, kullanıcının göreceği hâliyle (varsayılan arka plan); ölçüm arka plansız.
                        val shot = draw(context, input, style, style.defaultStyle(), size, File(out, "${style.kind.name}_${style.name}_$label.png"))
                        val result = draw(context, input, style, measured, size, null)
                        val drawn = size ?: DpSize(shot.first.dp, shot.second.dp)
                        val grow = WidgetScale.grow(style, drawn)
                        val layout = if (WidgetScale.compact(style, drawn)) "yedek" else "stil"
                        val cut = result.third.cut
                        lines += "%s,%s,%s,%.2f,%d,%d,%s".format(Locale.ROOT, style.name, label, layout, grow, result.third.fillWidth, result.third.fillHeight, cut.joinToString(" | ").replace(",", ";"))
                        cut.filter { text -> text.any { it.isDigit() } }.forEach { shortened += "$language ${style.name} $label: $it" }
                    }
                }
                File(out, "olcu.csv").writeText(lines.joinToString("\n"))
            }
        } finally {
            Locale.setDefault(system)
        }
        assertTrue("Kısaltılan sıcaklık, saat ve sayılar:\n" + shortened.joinToString("\n"), shortened.isEmpty())
    }

    /**
     * Yağış widget'ının her cümlesi (başlıyor, başlamak üzere, diniyor, sürüyor, yok, veri yok; yağmur ve kar)
     * üç dilde ve launcher ölçülerinde kesilmeden sığmalı. "Yağmur başlamak üzere" dar sütunda üç satıra
     * sarılıp alt satırı kesiliyordu.
     */
    @Test
    fun precipitationSentencesAreNeverCut(): Unit = runBlocking {
        val system = Locale.getDefault()
        val cut = mutableListOf<String>()
        val sizes = listOf(
            DpSize(250.dp, 110.dp), DpSize(306.dp, 95.dp), DpSize(320.dp, 78.dp), DpSize(320.dp, 110.dp), DpSize(400.dp, 90.dp),
            DpSize(260.dp, 66.dp), DpSize(300.dp, 200.dp), DpSize(245.dp, 105.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 70.dp),
        )
        // Yağışlı dilimler, anlık hava kodu, sıcaklık (1° ve altı kar), istasyon "kuru" ölçüyor mu, dilim var mı.
        data class Case(val name: String, val wet: List<Int>, val code: Int = 3, val temperature: Double = 15.0, val dryStation: Boolean = false, val slices: Int = 12)
        val cases = listOf(
            Case("yok", emptyList()), Case("basliyor", (3..11).toList()), Case("baslamak-uzere", (0..11).toList(), dryStation = true),
            Case("diniyor", (0..3).toList(), code = 61), Case("suruyor", (0..11).toList(), code = 61),
            Case("kar-basliyor", (3..11).toList(), temperature = 0.0), Case("kar-baslamak-uzere", (0..11).toList(), temperature = 0.0, dryStation = true),
            Case("kar-diniyor", (0..3).toList(), code = 73, temperature = 0.0), Case("kar-suruyor", (0..11).toList(), code = 73, temperature = 0.0),
            Case("veri-yok", emptyList(), slices = 0),
        )
        try {
            for (language in listOf("tr", "en", "fr")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                val context = localized(target, language)
                val base = WidgetSamples.input(context)
                for (case in cases) {
                    val input = base.copy(weather = base.weather.mapValues { (_, snapshot) ->
                        val forecast = snapshot.forecast
                        val now = forecast.localNow()
                        val quarter = now.withMinute(now.minute / 15 * 15).withSecond(0).withNano(0)
                        val minutely = (0 until case.slices).map { i ->
                            val start = quarter.plusMinutes(15L * i)
                            com.alihaydarsayar.communesky.model.PrecipitationSlice(start, start.plusMinutes(15), if (i in case.wet) 0.6 else 0.0, isWet = i in case.wet)
                        }
                        val station = com.alihaydarsayar.communesky.model.Observation(
                            com.alihaydarsayar.communesky.model.ObservationSource.Mgm, "Beşiktaş", snapshot.city.latitude, snapshot.city.longitude,
                            observedAtMillis = System.currentTimeMillis(), weatherCode = case.code,
                        )
                        snapshot.copy(
                            forecast = forecast.copy(current = forecast.current.copy(weatherCode = case.code, temperature = case.temperature), minutely = minutely),
                            observation = if (case.dryStation) com.alihaydarsayar.communesky.model.ObservationUse.Override(station, 1.0) else null,
                        )
                    })
                    for (style in listOf(WidgetStyleId.PrecipitationBars, WidgetStyleId.PrecipitationSentence)) {
                        for (size in sizes) {
                            val result = draw(context, input, style, style.defaultStyle(), size, null).third
                            result.cut.forEach { cut += "$language ${case.name} ${style.name} ${size.width.value.toInt()}x${size.height.value.toInt()}: $it" }
                        }
                    }
                }
            }
        } finally {
            Locale.setDefault(system)
        }
        assertTrue("Kesilen yağış cümleleri:\n" + cut.joinToString("\n"), cut.isEmpty())
    }

    /** Çizer; (genişlik dp, yükseklik dp, ölçüm) döner. [size] null: launcher ölçü bildirmedi. */
    private suspend fun draw(context: Context, input: WidgetInput, style: WidgetStyleId, widgetStyle: WidgetStyle, size: DpSize?, file: File?): Triple<Int, Int, Result> {
        val widget = style.kind.widget(WidgetConfig(WidgetPlace.Smart, widgetStyle), input)
        WallpaperTone.override = false
        val views = try {
            if (size != null) widget.compose(context, size = size) else widget.compose(context)
        } finally {
            WallpaperTone.override = null
        }
        val density = context.resources.displayMetrics.density
        // Ölçü bildirilmediyse launcher widget'a seçicideki hedef boyutu verir; çizim için stilin tasarım boyutu alınır.
        val frame = size ?: style.previewSize
        val width = (frame.width.value * density).toInt()
        val height = (frame.height.value * density).toInt()
        return withContext(Dispatchers.Main) {
            val view = views.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (file != null) canvas.drawColor(0xFF44564F.toInt())
            view.draw(canvas)
            file?.outputStream()?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val cut = mutableListOf<String>()
            collectCut(view, cut)
            collectOverflow(view, IntArray(2).also { view.getLocationInWindow(it) }, width, height, cut)
            val (fw, fh) = if (file == null) fill(bitmap) else 0 to 0
            Triple(frame.width.value.toInt(), frame.height.value.toInt(), Result(cut, fw, fh))
        }
    }

    /** Kesilen yazılar: "…" ile kısaltılanlar ve kutusuna sığmayıp kırpılanlar. */
    private fun collectCut(view: View, into: MutableList<String>) {
        if (view is ViewGroup) for (i in 0 until view.childCount) collectCut(view.getChildAt(i), into)
        if (view !is TextView || view.visibility != View.VISIBLE || view.width == 0) return
        val layout = view.layout ?: return
        val ellipsized = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
        val room = view.width - view.paddingLeft - view.paddingRight
        val clipped = (0 until layout.lineCount).any { layout.getLineMax(it) > room + 1f }
        // Yüksekliği yetmeyen yazı da kırpılır (alt satırı ya da harflerin altı görünmez).
        // (Yazı tipinin kendi satır boşluğu kadar küçük farklar sayılmaz.)
        val squeezed = layout.height - (view.height - view.paddingTop - view.paddingBottom) > 0.25f * layout.height / layout.lineCount.coerceAtLeast(1)
        if (ellipsized || clipped || squeezed) {
            android.util.Log.i("WidgetAudit", "'${view.text}' kisaltma=$ellipsized tasma=$clipped sikisma=$squeezed yazi=${layout.width}x${layout.height} kutu=${view.width}x${view.height}")
            into += view.text.toString()
        }
    }

    /** Widget'ın dışına taşan (ve bu yüzden kırpılan) yazı ve resimler. */
    private fun collectOverflow(view: View, origin: IntArray, width: Int, height: Int, into: MutableList<String>) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectOverflow(view.getChildAt(i), origin, width, height, into)
            return
        }
        if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return
        if (view !is TextView && view !is android.widget.ImageView) return
        // Arka plan resimleri widget'ı tam kaplar; taşma sayılmaz.
        if (view is android.widget.ImageView && view.width >= width - 2 && view.height >= height - 2) return
        val at = IntArray(2).also { view.getLocationInWindow(it) }
        val left = at[0] - origin[0]
        val top = at[1] - origin[1]
        if (left < -2 || top < -2 || left + view.width > width + 2 || top + view.height > height + 2) {
            into += "TAŞMA:" + ((view as? TextView)?.text?.toString() ?: "resim")
        }
    }

    /** Saydam zemine çizilmiş içeriğin kapladığı kutu, alanın yüzdesi olarak (genişlik, yükseklik). */
    private fun fill(bitmap: Bitmap): Pair<Int, Int> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 40) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return 0 to 0
        return ((right - left + 1) * 100 / w) to ((bottom - top + 1) * 100 / h)
    }

    private fun localized(base: Context, language: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }
}
