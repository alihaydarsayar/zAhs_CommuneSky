package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Arka plan güncellemesini planlar. Birden çok kez çağrılması güvenlidir (KEEP). */
object RefreshScheduler {

    private const val WORK_NAME = "weather-refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            // Sadece internet varken ve pil düşük değilken çalış: boşa pil harcama.
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        // Saatte bir; Android pili korumak için işleri gruplayıp biraz kaydırabilir.
        val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
