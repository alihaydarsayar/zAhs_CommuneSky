package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sabah özeti alarmının yaptığı işi elle bir kez çalıştırır (alarmı beklemeden bildirimi görmek için).
 * Ayarlar'da "Sabah özeti" açık ve bildirim izni verilmiş olmalı; değilse sessizce hiçbir şey yapmaz.
 */
@RunWith(AndroidJUnit4::class)
class MorningSummaryRunTest {

    @Test
    fun runsOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        // Bugün gönderildiyse yeniden gönderebilmek için "son gün" kaydı silinir.
        context.getSharedPreferences("morning_summary", Context.MODE_PRIVATE).edit().clear().apply()
        MorningSummary.onAlarm(context)
    }
}
