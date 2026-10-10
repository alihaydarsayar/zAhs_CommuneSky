package com.alihaydarsayar.communesky.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

const val PACKAGE_NAME = "com.alihaydarsayar.communesky"

/**
 * Uygulamanın kritik yollarını (açılış, ana ekranı kaydırma, widget ayar ekranı: stil kaydırma ve
 * sayfalar) yürütür ve bu sırada çalışan kodu Baseline Profile olarak kaydeder.
 * Android Studio: Run > Generate Baseline Profile; komut satırı: gradlew :app:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        openAndScroll()
        widgetSettings()
    }
}

/**
 * Ayarlar > Widget'larım > ilk widget: önizlemede stil kaydırma, ayar sayfalarını açıp kapama.
 * Ana ekranda widget yoksa bu bölüm atlanır (profil yine üretilir).
 */
fun MacrobenchmarkScope.widgetSettings() {
    val settings = device.wait(Until.findObject(By.desc(Pattern.compile("Settings|Ayarlar"))), 10_000) ?: return
    settings.click()
    val myWidgets = device.wait(Until.findObject(By.text(Pattern.compile("My widgets|Widget'larım"))), 10_000) ?: return
    myWidgets.click()
    val first = device.wait(Until.findObject(By.text(Pattern.compile("Clock|Saat|Home & location|Ev ve konum|Hourly|Saatlik|Weekly|Haftalık|Small|Küçük"))), 10_000) ?: return
    first.click()
    if (!device.wait(Until.hasObject(By.text(Pattern.compile("Save widget|Widget'ı kaydet|Add widget|Widget'ı ekle"))), 10_000)) return
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(3) {
        device.swipe(width * 4 / 5, height * 3 / 10, width / 5, height * 3 / 10, 12)
        device.waitForIdle()
    }
    for (page in listOf("Background|Arka plan", "Colors|Renkler", "Text|Yazı", "What's shown|Gösterilenler")) {
        val row = device.wait(Until.findObject(By.text(Pattern.compile(page))), 3_000) ?: continue
        row.click()
        device.waitForIdle()
        device.swipe(width / 2, height * 8 / 10, width / 2, height * 6 / 10, 12)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
    // Kaydetmeden çık.
    device.pressBack()
    device.waitForIdle()
}

/** İzin penceresi akışı bölmesin diye konum iznini önceden verir, açar ve listeyi kaydırır. */
fun MacrobenchmarkScope.openAndScroll() {
    device.executeShellCommand("pm grant $PACKAGE_NAME android.permission.ACCESS_COARSE_LOCATION")
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.scrollable(true)), 20_000)
    device.findObject(By.scrollable(true))?.let { list ->
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}
