package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Arka plan güncellemesini planlar: saatte bir, saat başına denk gelecek biçimde. Böylece widget'lardaki
 * "Şimdi" sütunu ve saatlik şerit saat değişince yenilenir. Fazladan uyanma yoktur: saatlik işin
 * zamanı hizalanır.
 */
object RefreshScheduler {

    private const val WORK_NAME = "weather-refresh"

    /** İş saat başından bu kadar sonra çalışır: saat kesin değişmiş olsun. */
    private const val AFTER_THE_HOUR_SECONDS = 20L

    /** Bir sonraki saat başı bundan yakınsa o atlanır: iki güncelleme art arda yapılmasın. */
    private const val MIN_GAP_MINUTES = 10L

    /** İlk planlama; iş zaten planlıysa dokunmaz (KEEP). İlk çalışma hemen olur: yeni eklenen widget veri bekler. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request(null))
    }

    /**
     * Her çalışmanın sonunda çağrılır: bir sonraki çalışmayı saat başına hizalar. Aynı iş güncellenir
     * (UPDATE); yeni bir iş ya da alarm eklenmez.
     */
    fun align(context: Context, now: Instant = Instant.now()) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request(nextRun(now)))
    }

    /** Bir sonraki çalışma zamanı: telefonun saatine göre sıradaki saat başı. Saf hesap; test edilir. */
    fun nextRun(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val nextHour = now.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).plusSeconds(AFTER_THE_HOUR_SECONDS).toInstant()
        return if (Duration.between(now, nextHour).toMinutes() < MIN_GAP_MINUTES) nextHour.plus(1, ChronoUnit.HOURS) else nextHour
    }

    private fun request(nextRun: Instant?): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            // Sadece internet varken ve pil düşük değilken çalış: boşa pil harcama.
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        // Saatte bir; Android pili korumak için işleri gruplayıp biraz kaydırabilir.
        return PeriodicWorkRequestBuilder<WeatherRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .apply { if (nextRun != null) setNextScheduleTimeOverride(nextRun.toEpochMilli()) }
            .build()
    }
}
