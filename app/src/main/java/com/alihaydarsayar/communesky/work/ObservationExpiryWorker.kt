package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * İstasyon ölçümü 30 dakikayı geçince widget'tan da kalksın diye, ölçümün eskiyeceği an
 * widget'ları yeniden çizer. İnternete çıkmaz; sadece önbellekteki veriyle yeniden çizim yapar.
 */
class ObservationExpiryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WeatherWidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "observation-expiry"

        /** [expiresAt] anında (bir dakika sonra) widget'ları yeniden çizer; öncekini değiştirir. */
        fun schedule(context: Context, expiresAt: Instant?) {
            val workManager = WorkManager.getInstance(context)
            if (expiresAt == null) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val delay = Duration.between(Instant.now(), expiresAt).plusMinutes(1).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ObservationExpiryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
