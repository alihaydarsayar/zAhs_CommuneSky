package com.alihaydarsayar.communesky.work

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Birleştirilmiş Open-Meteo isteklerinin gerçekten çalıştığını dener: bütün yerlerin ek verisi silinir, arka plan
 * güncellemesi bir kez çalıştırılır; her yerin öteki modelleri ve hava kalitesi yeniden gelmiş olmalıdır. Gerçek ağ
 * isteği yapar. Cihazda en az iki yer varsa çok yerli (liste cevaplı) istek de denenmiş olur.
 */
@RunWith(AndroidJUnit4::class)
class BatchedExtrasTest {

    @Test
    fun everyPlaceGetsModelsAndAirQualityAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        entry.extrasStore().deleteExcept(emptyList())
        entry.weatherUpdater().refreshInBackground()
        val weather = entry.weatherRepository().allWeather.first()
        assertTrue("yer yok", weather.isNotEmpty())
        for ((id, snapshot) in weather) {
            val extras = entry.extrasStore().get(id)
            assertNotNull("yer #$id: ek veri yok", extras)
            assertTrue("yer #$id: modeller gelmedi", extras!!.models.isNotEmpty())
            assertNotNull("yer #$id: hava kalitesi gelmedi", extras.airQuality)
            Log.i("BatchedExtras", "yer #$id modeller=${extras.models.map { it.source }} hava=${extras.airQuality?.hours?.size} kaynak=${snapshot.sources?.forecast}")
        }
        Log.i("BatchedExtras", "yer sayisi=${weather.size}")
        Unit
    }
}
