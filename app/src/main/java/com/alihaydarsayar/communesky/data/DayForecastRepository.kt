package com.alihaydarsayar.communesky.data

import android.util.Log
import com.alihaydarsayar.communesky.data.extras.ExtrasStore
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import com.alihaydarsayar.communesky.model.CoordinatePrivacy
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.model.detail.DayData
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gün sayfası için tek bir günün saatlik tahmini. 3.–7. günün saatleri telefonda durmaz (arka plan isteği
 * büyümesin diye); sayfa açılınca yalnız o yer ve o gün için istenir, yerin ek verileriyle birleştirilir ve
 * 30 dakika bellekte tutulur. İnternet yoksa null döner; sayfa günlük bilgilerle kalır.
 */
@Singleton
class DayForecastRepository @Inject constructor(
    private val api: Lazy<OpenMeteoApi>,
    private val extrasStore: ExtrasStore,
) {
    data class Day(val hours: List<HourlyForecast>, val source: ForecastSource)

    private data class Key(val placeId: Long, val date: LocalDate, val latitude: Double, val longitude: Double)

    private val cache = ConcurrentHashMap<Key, Pair<Long, Day>>()

    /** Testlerde saat değiştirilebilsin diye. */
    internal var clock: () -> Long = System::currentTimeMillis

    suspend fun day(snapshot: WeatherSnapshot, date: LocalDate): Day? {
        // Koordinatlar yaklaşık 1 km'ye yuvarlanarak gönderilir.
        val latitude = CoordinatePrivacy.round(snapshot.city.latitude)
        val longitude = CoordinatePrivacy.round(snapshot.city.longitude)
        val key = Key(snapshot.placeId, date, latitude, longitude)
        cache[key]?.takeIf { clock() - it.first < FRESH_MS }?.let { return it.second }
        return try {
            val day = withContext(Dispatchers.IO) {
                // Bir saatin yağışı sonraki satırda durur: günün son saati için ertesi gün de istenir.
                val response = api.get().getDays(latitude, longitude, date.toString(), date.plusDays(1).toString())
                val fetched = response.hourly.toModel(response.daily)
                val (hours, source) = DayData.mergeFetched(fetched, snapshot.forecast, extrasStore.get(snapshot.placeId), date)
                Day(hours, source)
            }.takeIf { it.hours.isNotEmpty() } ?: return null
            cache.keys.removeAll { clock() - (cache[it]?.first ?: 0) >= FRESH_MS }
            cache[key] = clock() to day
            day
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Günün saatlik tahmini alınamadı: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "DayForecast"
        const val FRESH_MS = 30 * 60 * 1000L
    }
}
