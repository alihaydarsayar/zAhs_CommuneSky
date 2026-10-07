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

const val PACKAGE_NAME = "com.alihaydarsayar.communesky"

/**
 * Uygulamanın kritik yolunu (açılış + ana ekranı kaydırma) yürütür ve bu sırada çalışan kodu
 * Baseline Profile olarak kaydeder. Android Studio: Run > Generate Baseline Profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        openAndScroll()
    }
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
