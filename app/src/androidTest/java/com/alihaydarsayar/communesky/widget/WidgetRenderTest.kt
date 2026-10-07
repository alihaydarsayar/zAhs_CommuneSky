package com.alihaydarsayar.communesky.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
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
 * Widget'ı ana ekrana eklemeden üç boyutta çizer ve PNG olarak kaydeder:
 * Android/data/com.alihaydarsayar.communesky/files/widget_*.png
 * Hem çizimin hatasız çalıştığını doğrular hem de tasarımı görmeyi sağlar.
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    @Test
    fun renderAllSizes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val sizes = mapOf(
            "small" to WeatherWidget.Small,
            "wide" to WeatherWidget.Wide,
            "medium" to WeatherWidget.Medium,
            "large" to WeatherWidget.Large,
        )
        for ((name, size) in sizes) {
            val remoteViews = WeatherWidget().compose(context, size = size)
            val bitmap = withContext(Dispatchers.Main) {
                val view = remoteViews.apply(context, FrameLayout(context))
                render(view, size, density)
            }
            val file = File(context.getExternalFilesDir(null), "widget_$name.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(file.length() > 0)
        }
    }

    private fun render(view: View, size: DpSize, density: Float): Bitmap {
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
