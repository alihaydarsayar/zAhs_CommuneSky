package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
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
 * Widget'ları ana ekrana eklemeden bütün boyutlarda ve arka planlarda çizer ve PNG olarak kaydeder:
 * Android/data/com.alihaydarsayar.communesky/files/widget_*.png
 * Hem çizimin hatasız çalıştığını doğrular hem de tasarımı görmeyi sağlar. Saydam olanlar
 * açık renkli bir duvar kâğıdı üstünde çizilir (gölgenin işe yaradığı görülsün).
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderWeatherWidget() = runBlocking {
        val sizes = mapOf(
            "small" to WeatherWidget.Small,
            "wide" to WeatherWidget.Wide,
            "medium" to WeatherWidget.Medium,
            "large" to WeatherWidget.Large,
        )
        for (background in WidgetBackground.entries) {
            for ((name, size) in sizes) {
                render(WeatherWidget(WidgetConfig(background = background)), size, "weather_${name}_$background")
            }
        }
    }

    @Test
    fun renderClockWidget() = runBlocking {
        val sizes = mapOf("compact" to ClockWidget.Compact, "wide" to ClockWidget.Wide, "tall" to ClockWidget.Tall)
        for (background in WidgetBackground.entries) {
            for ((name, size) in sizes) {
                render(ClockWidget(WidgetConfig(background = background)), size, "clock_${name}_$background")
            }
        }
    }

    @Test
    fun renderHomeWidget() = runBlocking {
        val sizes = mapOf("compact" to HomeWidget.Compact, "roomy" to HomeWidget.Roomy)
        for (background in WidgetBackground.entries) {
            for ((name, size) in sizes) {
                render(HomeWidget(WidgetConfig(background = background)), size, "home_${name}_$background")
            }
        }
    }

    private suspend fun render(widget: GlanceAppWidget, size: DpSize, name: String) {
        val density = context.resources.displayMetrics.density
        val remoteViews = widget.compose(context, size = size)
        val bitmap = withContext(Dispatchers.Main) {
            val view = remoteViews.apply(context, FrameLayout(context))
            draw(view, size, density, lightWallpaper = name.endsWith("Transparent"))
        }
        val file = File(context.getExternalFilesDir(null), "widget_$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    private fun draw(view: View, size: DpSize, density: Float, lightWallpaper: Boolean): Bitmap {
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            // Açık bir duvar kâğıdı taklidi: en zor durum.
            canvas.drawColor(if (lightWallpaper) 0xFFE9EEF3.toInt() else 0xFF2B3A55.toInt())
            view.draw(canvas)
        }
    }
}
