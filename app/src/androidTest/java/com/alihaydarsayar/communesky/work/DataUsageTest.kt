package com.alihaydarsayar.communesky.work

import android.net.TrafficStats
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.alihaydarsayar.communesky.CommuneSkyApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Saatlik hava işinin bir kez çalışmasının internet kullanımı (indirilen ve gönderilen bayt).
 * Günlük kullanım bunun 24 katıdır. Sonuç Android/data/.../files/measure/data.txt dosyasına ve
 * "DataUsage" etiketiyle loga yazılır. İnternet bağlantısı gerekir.
 */
@RunWith(AndroidJUnit4::class)
class DataUsageTest {

    @Test
    fun oneRefresh() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CommuneSkyApp
        val uid = Process.myUid()
        // Açılışta başlayan istekler bitsin.
        delay(8_000)
        val runs = (1..3).map {
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val worker = TestListenableWorkerBuilder<WeatherRefreshWorker>(context).setWorkerFactory(app.workerFactory).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            delay(2_000)
            (TrafficStats.getUidRxBytes(uid) - rx) to (TrafficStats.getUidTxBytes(uid) - tx)
        }
        val text = runs.joinToString("\n") { (rx, tx) -> "refresh_rx_bytes=$rx refresh_tx_bytes=$tx" }
        Log.i("DataUsage", text)
        File(context.getExternalFilesDir(null), "measure").apply { mkdirs() }.resolve("data.txt").writeText(text)
    }
}
