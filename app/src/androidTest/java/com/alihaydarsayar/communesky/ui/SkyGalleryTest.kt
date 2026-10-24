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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.model.MoonShape
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherScene
import com.alihaydarsayar.communesky.ui.sky.SkyBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Elle çalıştırılan yardımcı: uygulamanın bütün gökyüzü sahnelerini (güneşli, bulutlu, yağmurlu, karlı, sisli,
 * fırtınalı; gündüz, gün doğumu, gün batımı ve ayla gece) birer görüntü olarak Android/data/…/files/sky-gallery/
 * altına yazar. README'deki görseller buradan alınır.
 */
@RunWith(AndroidJUnit4::class)
class SkyGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Shot(val name: String, val theme: SkyTheme, val condition: WeatherCondition, val night: Boolean, val moon: MoonShape? = null)

    private val shots = listOf(
        Shot("clear-day", SkyTheme.ClearDay, WeatherCondition.Clear, false),
        Shot("sunrise", SkyTheme.Sunrise, WeatherCondition.Clear, false),
        Shot("sunset", SkyTheme.Sunset, WeatherCondition.Clear, false),
        Shot("cloudy-day", SkyTheme.CloudyDay, WeatherCondition.Cloudy, false),
        Shot("rain-day", SkyTheme.RainDay, WeatherCondition.Rain, false),
        Shot("storm", SkyTheme.Storm, WeatherCondition.Thunderstorm, false),
        Shot("snow-day", SkyTheme.SnowDay, WeatherCondition.Snow, false),
        Shot("fog-day", SkyTheme.FogDay, WeatherCondition.Fog, false),
        Shot("clear-night-crescent", SkyTheme.ClearNight, WeatherCondition.Clear, true, MoonShape(0.25, litOnRight = true)),
        Shot("clear-night-full", SkyTheme.ClearNight, WeatherCondition.Clear, true, MoonShape(1.0, litOnRight = true)),
        Shot("cloudy-night", SkyTheme.CloudyNight, WeatherCondition.Cloudy, true),
        Shot("rain-night", SkyTheme.RainNight, WeatherCondition.Rain, true),
        Shot("snow-night", SkyTheme.SnowNight, WeatherCondition.Snow, true),
    )

    @Test
    fun everySkyScene() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.getExternalFilesDir(null), "sky-gallery").apply { mkdirs() }
        var current by androidx.compose.runtime.mutableStateOf(shots[0])
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                val shot = current
                // Her sahne kendi anahtarıyla kurulur: önceki sahnenin renk geçişi ve efekt durumu taşınmasın.
                androidx.compose.runtime.key(shot.name) {
                    SkyBackground(WeatherScene(shot.theme, shot.condition, shot.night), Modifier.size(360.dp, 640.dp).testTag("sky"), shot.moon)
                }
            }
        }
        for (shot in shots) {
            current = shot
            compose.mainClock.advanceTimeBy(100)
            // Renk geçişi (1,6 sn) ve hava efekti oturana kadar.
            compose.mainClock.advanceTimeBy(4_000)
            val bitmap = compose.onNodeWithTag("sky").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            File(out, "${shot.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
