package com.alihaydarsayar.communesky.work

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.CommuneSkyApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arka plan işçisinin Hilt ile oluşturulabildiğini ve gerçekten veriyi yenilediğini doğrular
 * (internet bağlantısı gerekir).
 */
@RunWith(AndroidJUnit4::class)
class WeatherRefreshWorkerTest {

    @Test
    fun workerRefreshesTheCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CommuneSkyApp
        val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(context)
            .setWorkerFactory(app.workerFactory)
            .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
