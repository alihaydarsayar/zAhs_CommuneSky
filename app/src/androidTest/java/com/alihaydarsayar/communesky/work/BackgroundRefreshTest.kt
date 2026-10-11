package com.alihaydarsayar.communesky.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saatlik arka plan güncellemesini elle bir kez çalıştırır (WorkManager, zamanı gelmeden çalıştırmaya izin
 * vermiyor). Gerçek ağ isteği yapar; hata fırlatmadan bitmesi beklenir. Doğruluk özeti debug sürümünde
 * logcat'e yazılır: adb logcat -s ForecastAccuracy
 */
@RunWith(AndroidJUnit4::class)
class BackgroundRefreshTest {

    @Test
    fun runsOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).weatherUpdater().refreshInBackground()
    }
}
