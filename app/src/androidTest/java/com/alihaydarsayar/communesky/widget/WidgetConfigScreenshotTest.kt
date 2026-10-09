package com.alihaydarsayar.communesky.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.widget.config.WidgetConfigActivity
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Widget ekleme ekranını gerçek bir widget kimliğiyle açar ve ekran görüntüsü alır:
 * Android/data/com.alihaydarsayar.communesky/files/config_*.png
 *
 * Widget bağlamak için izin gerekir: adb shell appwidget grantbind --package com.alihaydarsayar.communesky
 * (izin yoksa test atlanır).
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun configScreens() {
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 4711)
        for (kind in listOf(WidgetKind.HomeLocation, WidgetKind.Clock)) {
            val id = host.allocateAppWidgetId()
            val bound = manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, kind.receiverClass))
            assumeTrue("Widget bağlama izni yok", bound)
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = instrumentation.startActivitySync(intent)
            Thread.sleep(4_000)
            save(instrumentation.uiAutomation.takeScreenshot(), "config_${kind.name.lowercase()}")
            activity.finish()
            host.deleteAppWidgetId(id)
        }
    }

    private fun save(bitmap: Bitmap?, name: String) {
        bitmap ?: return
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
