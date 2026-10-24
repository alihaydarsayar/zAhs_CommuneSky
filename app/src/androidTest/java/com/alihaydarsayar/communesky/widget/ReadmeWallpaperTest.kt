package com.alihaydarsayar.communesky.widget

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Elle çalıştırılan yardımcı: görsel çekimleri için telefona nötr, sakin, koyu mavi-gri bir duvar kâğıdı koyar
 * (README widget görselleriyle aynı gradyan). Fotoğraf yoktur; widget'ların altından renk sızmaz.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeWallpaperTest {

    @Test
    fun setNeutralWallpaper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, bitmap.height.toFloat(), 0xFF2A3550.toInt(), 0xFF141B2E.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        WallpaperManager.getInstance(context).setBitmap(bitmap)
    }
}
