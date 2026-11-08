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

    override suspend fun doWork(): Result = round(
        refresh = {
            updater.refreshInBackground()
            // Bir sonraki çalışma saat başına hizalanır (widget'larda "Şimdi" doğru saati göstersin).
            RefreshScheduler.align(applicationContext)
        },
        // Özet alarmları geri kurulur ve gönderilmemiş özet pencere içindeyse gönderilir: yenileme başarısız olsa da
        // (ağ yok, 503). 1.5.12'de bu adım yalnız yenileme başarılıysa çalışıyordu.
        summaries = { MorningSummary.backgroundCheck(applicationContext) },
    )

    companion object {
        private const val TAG = "WeatherRefreshWorker"

        /** Bir saatlik tur: önce yenileme, sonra her durumda özet denetimi. Saf; test edilir. */
        suspend fun round(refresh: suspend () -> Unit, summaries: suspend () -> Unit): Result {
            val result = try {
                refresh()
                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                // Geçici ağ sorunu: WorkManager biraz bekleyip tekrar dener.
                Result.retry()
            } catch (e: HttpException) {
                if (e.code() >= 500) Result.retry() else Result.failure()
            } catch (e: Exception) {
                Log.e(TAG, "Arka plan güncellemesi başarısız", e)
                Result.failure()
            }
            runCatching { summaries() }.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            return result
        }
    }
}
