package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Sabah ve akşam özetinin güvenlik ağı: 15 dakikada bir (WorkManager'ın en kısa periyodu) çalışır. Ağ ya da pil
 * koşulu yoktur: Wi-Fi kapalıyken de çalışır ve eldeki veriyle gönderir. Hedef saatle hedef + 3 saat arasında bugünün
 * özeti gönderilmediyse gönderir ([MorningSummary.watch]); silinmiş alarmları da geri kurar. İş yoksa birkaç
 * milisaniyede biter. Özetler kapalıyken kaldırılır.
 */
class SummaryWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { MorningSummary.watch(applicationContext) }
            .exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        return Result.success()
    }

    companion object {
        const val NAME = "summary-watch"
        const val PERIOD_MINUTES = 15L

        /** [on]: en az bir özet açık. Açıksa iş kurulur (zaten kuruluysa dokunulmaz), kapalıysa kaldırılır. */
        fun ensure(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!on) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SummaryWatchWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
