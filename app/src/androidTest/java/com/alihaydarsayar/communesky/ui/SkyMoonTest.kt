package com.alihaydarsayar.communesky.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.model.MoonShape
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherScene
import com.alihaydarsayar.communesky.ui.sky.SkyBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gece sahnesindeki ay gerçek evresiyle çizilir: hilalde yalnız aydınlık yan parlaktır, dolunayda bütün yüz.
 * Ayın yeri ve boyutu sahnedeki sabit değerlerdir (genişliğin %80'i, yüksekliğin %11'i, 22 dp yarıçap).
 * Görüntüler Android/data/…/files/sky/ altına da yazılır.
 */
@RunWith(AndroidJUnit4::class)
class SkyMoonTest {

    @get:Rule
    val compose = createComposeRule()

    private val night = WeatherScene(SkyTheme.ClearNight, WeatherCondition.Clear, isNight = true)

    /** Sahneyi çizer; ayın solundaki, ortasındaki ve sağındaki parlaklığı (0–255) döner. */
    private fun brightness(name: String, moon: MoonShape?): Triple<Int, Int, Int> {
        // Sahnenin saati kare kare ilerler; test saati elle ilerletilir ki çizim beklemede kalmasın.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) { SkyBackground(night, Modifier.size(360.dp, 640.dp).testTag("sky"), moon) }
        }
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onNodeWithTag("sky").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "sky").apply { mkdirs() }.resolve("$name.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val centerX = bitmap.width * 0.8f
        val centerY = bitmap.height * 0.11f
        val radius = 22f * context.resources.displayMetrics.density
        // Küçük bir karenin ortalaması: tek bir yıldız ya da leke sonucu değiştirmesin.
        fun at(offset: Float): Int {
            var sum = 0
            var count = 0
            for (dx in -2..2) for (dy in -2..2) {
                val pixel = bitmap.getPixel((centerX + offset * radius).toInt() + dx, centerY.toInt() + dy)
                sum += (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                count++
            }
            return sum / count
        }
        return Triple(at(-0.8f), at(0f), at(0.8f))
    }

    @Test
    fun waxingCrescentIsLitOnTheRightOnly() {
        val (left, middle, right) = brightness("hilal-buyuyen", MoonShape(0.12, litOnRight = true))
        assertTrue("sağ $right, orta $middle, sol $left", right > 200 && middle < 120 && left < 120)
    }

    @Test
    fun waningGibbousIsDarkOnTheRightEdge() {
        val (left, middle, right) = brightness("siskin-kuculen", MoonShape(0.8, litOnRight = false))
        assertTrue("sağ $right, orta $middle, sol $left", left > 200 && middle > 200 && right < 120)
    }

    @Test
    fun fullMoonIsLitAllOver() {
        val (left, middle, right) = brightness("dolunay", MoonShape(1.0, litOnRight = true))
        assertTrue("sağ $right, orta $middle, sol $left", left > 200 && middle > 200 && right > 200)
    }

    @Test
    fun firstQuarterIsHalfLit() {
        val (left, _, right) = brightness("ilk-dordun", MoonShape(0.5, litOnRight = true))
        assertTrue("sağ $right, sol $left", right > 200 && left < 120)
    }
}
