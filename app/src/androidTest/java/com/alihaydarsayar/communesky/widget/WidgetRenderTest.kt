package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
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

/**
 * Bütün widget stillerini ana ekrana eklemeden, örnek veriyle çizer ve PNG olarak kaydeder:
 * Android/data/com.alihaydarsayar.communesky/files/widget_*.png
 * Hem çizimin hatasız çalıştığını doğrular hem de tasarımı görmeyi sağlar. Duvar kâğıdının
 * üstünde duran stiller (saydam, cam) hem koyu hem açık bir duvar kâğıdı üstünde çizilir.
 *
 * "preview_*.png" dosyaları Android 12 öncesi widget seçicisinin önizleme resimleridir
 * (res/drawable-nodpi/widget_preview_image_*.png).
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val input = WidgetSamples.input()

    @Test
    fun renderEveryStyle() = runBlocking {
        for (style in WidgetStyleId.entries) {
            val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle())
            val onWallpaper = style.background == BackgroundKind.Transparent || style.background == BackgroundKind.Glass
            render(style.kind, config, style.previewSize, "${style.name}_dark", lightWallpaper = false)
            if (onWallpaper) render(style.kind, config, style.previewSize, "${style.name}_light", lightWallpaper = true)
            // Büyük yazı boyutunda da hiçbir yazı kesilmemeli.
            val large = config.copy(style = config.style.copy(textSize = TextSize.Large))
            render(style.kind, large, style.previewSize, "${style.name}_large", lightWallpaper = false)
        }
    }

    @Test
    fun renderSmallSizes() = runBlocking {
        // Her türün en küçük boyutu: yazılar kesilmeden daha az bilgi gösterilmeli.
        for (kind in WidgetKind.entries) {
            val smallest = kind.sizes.minBy { it.width.value * it.height.value }
            for (style in kind.styles) {
                render(kind, WidgetConfig(WidgetPlace.Smart, style.defaultStyle()), smallest, "small_${style.name}", lightWallpaper = false)
            }
        }
    }

    @Test
    fun renderLooks() = runBlocking {
        // Ortak kişiselleştirme: arka plan, renk teması, yazı rengi, köşe, yazı boyutu.
        val base = WidgetStyleId.HomeSmart.defaultStyle()
        val looks = mapOf(
            "glass" to base.copy(background = BackgroundKind.Glass, transparency = 65),
            "solid_ocean" to base.copy(background = BackgroundKind.Solid, colorTheme = ColorTheme.Ocean),
            "sky_sunset_half" to base.copy(colorTheme = ColorTheme.Sunset, transparency = 50),
            "pastel" to base.copy(colorTheme = ColorTheme.Pastel),
            "wallpaper" to base.copy(colorTheme = ColorTheme.Wallpaper),
            "large_text_round" to base.copy(textSize = TextSize.Large, corners = CornerSize.Large),
            "clear_dark_text" to base.copy(background = BackgroundKind.Transparent, transparency = 100, textColor = TextColorMode.Dark),
        )
        for ((name, style) in looks) {
            render(WidgetKind.HomeLocation, WidgetConfig(WidgetPlace.Smart, style), WidgetStyleId.HomeSmart.previewSize, "look_$name", lightWallpaper = name.contains("dark_text"))
        }
    }

    @Test
    fun renderHomeScenarios() = runBlocking {
        // Evde / yakında / uzakta / ev yok: Ev ve konum widget'ı ve saat + ev stili.
        for (scenario in WidgetSamples.Scenario.entries) {
            val sample = WidgetSamples.input(scenario = scenario)
            for (style in listOf(WidgetStyleId.HomeSmart, WidgetStyleId.HomeRow, WidgetStyleId.HomeList, WidgetStyleId.ClockHome)) {
                for (size in listOf(style.previewSize, DpSize(style.previewSize.width, style.previewSize.height + 40.dp))) {
                    render(style.kind, WidgetConfig(WidgetPlace.Smart, style.defaultStyle()), size, "scenario_${scenario}_${style.name}_${size.height.value.toInt()}", lightWallpaper = false, input = sample)
                }
            }
        }
    }

    @Test
    fun renderPickerPreviews() = runBlocking {
        for (kind in WidgetKind.entries) {
            val style = kind.defaultStyle
            render(kind, WidgetConfig.default(kind), style.previewSize, "preview_${kind.name.lowercase()}", lightWallpaper = false, wallpaper = false, input = WidgetSamples.input(scenario = WidgetSamples.Scenario.AtHome))
        }
    }

    private suspend fun render(
        kind: WidgetKind,
        config: WidgetConfig,
        size: DpSize,
        name: String,
        lightWallpaper: Boolean,
        wallpaper: Boolean = true,
        input: WidgetInput = this.input,
    ) {
        val density = context.resources.displayMetrics.density
        val remoteViews = kind.widget(config, input).compose(context, size = size)
        val bitmap = withContext(Dispatchers.Main) {
            val view = remoteViews.apply(context, FrameLayout(context))
            draw(view, size, density, lightWallpaper, wallpaper)
        }
        val file = File(context.getExternalFilesDir(null), "widget_$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    private fun draw(view: View, size: DpSize, density: Float, lightWallpaper: Boolean, wallpaper: Boolean): Bitmap {
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            // Açık bir duvar kâğıdı taklidi: en zor durum. Seçici önizlemeleri saydam kalır.
            if (wallpaper) canvas.drawColor(if (lightWallpaper) 0xFFE9EEF3.toInt() else 0xFF44564F.toInt())
            view.draw(canvas)
        }
    }
}
