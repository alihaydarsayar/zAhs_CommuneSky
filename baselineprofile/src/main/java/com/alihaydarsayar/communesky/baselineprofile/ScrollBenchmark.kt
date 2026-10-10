package com.alihaydarsayar.communesky.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Soğuk açılış ve kaydırma akıcılığı ölçümleri (release, varsayılan derleme).
 *
 * - [coldStartup]: uygulamanın soğuk açılışı, 10 tekrar.
 * - [homeScroll]: ana ekran listesinin kaydırılması.
 * - [widgetSettingsScroll]: widget ayar ekranı (stil kaydırma, liste, sayfalar). Ana ekranda en az bir
 *   Commune Sky widget'ı olmalı; yoksa test atlanır. Ekrana Ayarlar > Widget'larım'dan girilir.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 10,
        // Hazırlık adımı yok: soğuk açılıştan önce uygulama süreci hiç çalışmamalı (ana ekrana dönmek,
        // oradaki widget yüzünden süreci yeniden başlatabiliyor).
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeScroll() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.ACCESS_COARSE_LOCATION")
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.scrollable(true)), 20_000)
        },
    ) {
        flingList()
    }

    @Test
    fun widgetSettingsScroll() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            assumeTrue("Ana ekranda Commune Sky widget'ı yok", openWidgetSettings())
        },
    ) {
        // Yatay kaydırma (stiller ya da renk temaları), sonra ayar listesi aşağı ve yukarı.
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(2) {
            device.swipe(width * 4 / 5, height * 3 / 10, width / 5, height * 3 / 10, 12)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(width / 5, height * 3 / 10, width * 4 / 5, height * 3 / 10, 12)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(width / 2, height * 8 / 10, width / 2, height * 4 / 10, 12)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(width / 2, height * 5 / 10, width / 2, height * 8 / 10, 12)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.flingList() {
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    /** Ayarlar > Widget'larım > ilk widget. Açılamadıysa false. */
    private fun MacrobenchmarkScope.openWidgetSettings(): Boolean {
        val settings = device.wait(Until.findObject(By.desc(Pattern.compile("Settings|Ayarlar"))), 20_000) ?: return false
        settings.click()
        val myWidgets = device.wait(Until.findObject(By.text(Pattern.compile("My widgets|Widget'larım"))), 10_000) ?: return false
        myWidgets.click()
        val first = device.wait(Until.findObject(By.text(Pattern.compile("Clock|Saat|Home & location|Ev ve konum|Hourly|Saatlik|Weekly|Haftalık|Small|Küçük"))), 10_000)
            ?: return false
        first.click()
        return device.wait(Until.hasObject(By.text(Pattern.compile("Save|Kaydet|Save widget|Widget'ı kaydet"))), 10_000)
    }
}
