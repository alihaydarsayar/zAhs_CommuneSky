package com.alihaydarsayar.communesky.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alihaydarsayar.communesky.data.WeatherUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import java.io.IOException

/**
 * WorkManager'ın periyodik olarak çalıştırdığı arka plan işi: bütün yerlerin hava durumunu yeniler,
 * önbelleği ve widget'ları günceller. Uygulama kapalıyken de çalışır.
 */
@HiltWorker
class WeatherRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updater: WeatherUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        updater.refreshInBackground()
        Result.success()
    } catch (e: IOException) {
        // Geçici ağ sorunu: WorkManager biraz bekleyip tekrar dener.
        Result.retry()
    } catch (e: HttpException) {
        if (e.code() >= 500) Result.retry() else Result.failure()
    } catch (e: Exception) {
        Log.e(TAG, "Arka plan güncellemesi başarısız", e)
        Result.failure()
    }

    private companion object {
        const val TAG = "WeatherRefreshWorker"
    }
}
