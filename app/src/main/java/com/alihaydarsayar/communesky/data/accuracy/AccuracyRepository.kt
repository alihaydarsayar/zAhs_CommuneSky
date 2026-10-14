package com.alihaydarsayar.communesky.data.accuracy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.data.extras.ExtrasStore
import com.alihaydarsayar.communesky.model.AccuracyStats
import com.alihaydarsayar.communesky.model.ForecastMerge
import com.alihaydarsayar.communesky.model.ForecastPair
import com.alihaydarsayar.communesky.model.ForecastReferee
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.GeoDistance
import com.alihaydarsayar.communesky.model.LearnedCorrection
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.PlaceExtras
import com.alihaydarsayar.communesky.model.StoredAccuracy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bir kaynağın bir an için yaptığı tahmin. [kind]: "H" saatlik sıcaklık, "X" günün en yükseği, "N" en düşüğü.
 * [targetHour]: hedef an, 1970'ten beri saat (günlük değerlerde o yerel günün başlangıcı).
 */
@Entity(tableName = "forecast_records", primaryKeys = ["placeId", "source", "kind", "targetHour", "leadHours"])
data class ForecastRecordEntity(
    val placeId: Long,
    val source: String,
    val kind: String,
    val targetHour: Long,
    val leadHours: Int,
    val value: Double,
)

/** Bir saatte en yakın istasyonda ölçülen sıcaklık. */
@Entity(tableName = "observed_records", primaryKeys = ["placeId", "hour"])
data class ObservedRecordEntity(
    val placeId: Long,
    val hour: Long,
    val temperature: Double,
    /** İstasyonun yere uzaklığı (km). */
    val stationKm: Double,
)

@Dao
interface AccuracyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertForecasts(records: List<ForecastRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObserved(record: ObservedRecordEntity)

    @Query("SELECT * FROM forecast_records WHERE placeId = :placeId AND targetHour >= :sinceHour")
    suspend fun forecasts(placeId: Long, sinceHour: Long): List<ForecastRecordEntity>

    @Query("SELECT * FROM observed_records WHERE placeId = :placeId AND hour >= :sinceHour")
    suspend fun observed(placeId: Long, sinceHour: Long): List<ObservedRecordEntity>

    @Query("DELETE FROM forecast_records WHERE targetHour < :beforeHour")
    suspend fun deleteOldForecasts(beforeHour: Long)

    @Query("DELETE FROM observed_records WHERE hour < :beforeHour")
    suspend fun deleteOldObserved(beforeHour: Long)

    @Query("DELETE FROM forecast_records WHERE source = :source AND kind = :kind")
    suspend fun deleteForecasts(source: String, kind: String)

    @Query("DELETE FROM forecast_records WHERE placeId NOT IN (:placeIds)")
    suspend fun deleteForecastsExcept(placeIds: List<Long>)

    @Query("DELETE FROM observed_records WHERE placeId NOT IN (:placeIds)")
    suspend fun deleteObservedExcept(placeIds: List<Long>)
}

/**
 * Doğruluk kaydı, otomatik hakem ve öğrenen düzeltme. Saatlik arka plan işinin içinde çalışır: ek internet
 * isteği ya da uyanma yoktur. Kayıtlar telefonda kalır; yer başına son 45 gün tutulur, yer silinince silinir.
 */
@Singleton
class AccuracyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: AccuracyDao,
    private val extrasStore: ExtrasStore,
) {
    /**
     * Her yer için: adayların önümüzdeki saatlere ait tahminlerini ve o anki ölçümü kaydeder; günde bir kez
     * karneyi çıkarıp hakem kararını ve düzeltmeyi yeniler. Hata fırlatmaz.
     */
    suspend fun update(places: List<Place>, utcOffsetSeconds: (Long) -> Int?, observation: suspend (Long) -> Observation?, now: Instant = Instant.now()) {
        runCatching {
            forgetShiftedMgmHours(places)
            val nowHour = now.epochSecond / 3600
            for (place in places) {
                val extras = extrasStore.get(place.id) ?: continue
                val offset = utcOffsetSeconds(place.id) ?: continue
                record(place, extras, offset, observation(place.id), now)
                val today = (now.epochSecond + offset) / 86_400
                if (extras.decidedDay != today) extrasStore.save(place.id, decide(place.id, extras, offset, nowHour).copy(decidedDay = today))
            }
            dao.deleteOldForecasts(nowHour - KEEP_DAYS * 24)
            dao.deleteOldObserved(nowHour - KEEP_DAYS * 24)
            val ids = places.map { it.id }
            dao.deleteForecastsExcept(ids)
            dao.deleteObservedExcept(ids)
        }
    }

    /**
     * 1.5.2'ye kadar MGM'nin saatlik tahmini 3 saat erken okunuyordu (bkz. MgmParsing.hourlyTime). O tahminlerle
     * yazılmış kayıtlar bir kez silinir; hakem ve öğrenen düzeltme MGM'yi doğru saatlerle yeniden öğrenir.
     */
    private suspend fun forgetShiftedMgmHours(places: List<Place>) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(MGM_HOURS_FIXED, false)) return
        dao.deleteForecasts(com.alihaydarsayar.communesky.model.ForecastSource.Mgm.name, HOURLY)
        for (place in places) {
            val extras = extrasStore.get(place.id) ?: continue
            extrasStore.save(place.id, com.alihaydarsayar.communesky.model.MgmHourlyFix.forget(extras))
        }
        preferences.edit().putBoolean(MGM_HOURS_FIXED, true).apply()
    }

    private suspend fun record(place: Place, extras: PlaceExtras, offset: Int, observation: Observation?, now: Instant) {
        val nowHour = now.epochSecond / 3600
        val records = mutableListOf<ForecastRecordEntity>()
        for (series in listOfNotNull(extras.national) + extras.models) {
            for (lead in AccuracyStats.SHORT_LEADS.toList() + AccuracyStats.DAY_AHEAD) {
                val target = nowHour + lead
                val temperature = ForecastMerge.temperatureAt(series.hours, localTime(target, offset)) ?: continue
                records += ForecastRecordEntity(place.id, series.source.name, HOURLY, target, lead, temperature)
            }
            // Yarının en yükseği ve en düşüğü.
            val tomorrow = localTime(nowHour, offset).toLocalDate().plusDays(1)
            series.days.firstOrNull { it.day == tomorrow }?.let { day ->
                val dayStart = tomorrow.atStartOfDay().toEpochSecond(ZoneOffset.ofTotalSeconds(offset)) / 3600
                day.maxTemperature?.let { records += ForecastRecordEntity(place.id, series.source.name, MAX, dayStart, AccuracyStats.DAY_AHEAD, it) }
                day.minTemperature?.let { records += ForecastRecordEntity(place.id, series.source.name, MIN, dayStart, AccuracyStats.DAY_AHEAD, it) }
            }
        }
        if (records.isNotEmpty()) dao.insertForecasts(records)
        // O saatin ölçümü: taze bir istasyon ölçümü varsa, en yakın tam saate yazılır.
        val temperature = observation?.temperature
        if (observation != null && temperature != null && now.toEpochMilli() - observation.observedAtMillis <= FRESH_MS) {
            val km = GeoDistance.kilometers(observation.latitude, observation.longitude, place.city.latitude, place.city.longitude)
            dao.insertObserved(ObservedRecordEntity(place.id, (observation.observedAtMillis / 1000 + 1800) / 3600, temperature, km))
        }
    }

    /** Son 30 günün karnesi: hata özeti, hakemin seçimi ve düzeltme. */
    private suspend fun decide(placeId: Long, extras: PlaceExtras, offset: Int, nowHour: Long): PlaceExtras {
        val since = nowHour - WINDOW_DAYS * 24
        val observed = dao.observed(placeId, since)
        val byHour = observed.associate { it.hour to it.temperature }
        val forecasts = dao.forecasts(placeId, since)
        fun source(name: String) = ForecastSource.entries.firstOrNull { it.name == name }
        val pairs = forecasts.filter { it.kind == HOURLY }.mapNotNull { record ->
            val measured = byHour[record.targetHour] ?: return@mapNotNull null
            val local = localTime(record.targetHour, offset)
            ForecastPair(source(record.source) ?: return@mapNotNull null, record.leadHours, local.toLocalDate().toEpochDay(), local.hour, record.value, measured)
        }
        // Bir günün ölçülen en yükseği: o yerel günde en az 16 saatin ölçümü varsa.
        val maxByDay = observed.groupBy { localTime(it.hour, offset).toLocalDate() }
            .filterValues { it.size >= MIN_HOURS_FOR_DAY }
            .mapValues { (_, hours) -> hours.maxOf { it.temperature } }
        val maxPairs = forecasts.filter { it.kind == MAX }.mapNotNull { record ->
            val measured = maxByDay[localTime(record.targetHour, offset).toLocalDate()] ?: return@mapNotNull null
            (source(record.source) ?: return@mapNotNull null) to (record.value to measured)
        }.groupBy({ it.first }, { it.second })

        val summary = AccuracyStats.summarize(pairs, maxPairs)
        val default = com.alihaydarsayar.communesky.model.ForecastMerge.defaultSource(extras)
        val chosen = ForecastReferee.choose(default, summary)
        val used = chosen ?: default
        val correction = LearnedCorrection.compute(pairs.filter { it.source == used }, observed.maxByOrNull { it.hour }?.stationKm)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Yalnız debug sürümünde: adb logcat -s ForecastAccuracy. Yer adı ya da konum yazılmaz.
            Log.d(TAG, "yer #$placeId varsayılan=$default seçilen=${chosen ?: "-"} düzeltme gündüz=${correction.day} gece=${correction.night}")
            summary.forEach { Log.d(TAG, String.format(Locale.ROOT, "  %s gün=%d 1-6sa=%s 24sa=%s sapma=%s enYüksek=%s", it.source, it.days, it.shortTermError, it.dayAheadError, it.bias, it.nextDayMaxError)) }
        }
        return extras.copy(
            chosen = chosen,
            dayCorrection = correction.day,
            nightCorrection = correction.night,
            accuracy = summary.map { StoredAccuracy(it.source, it.days, it.dayAheadError, it.nextDayMaxError) },
        )
    }

    private fun localTime(epochHour: Long, offsetSeconds: Int): LocalDateTime =
        LocalDateTime.ofEpochSecond(epochHour * 3600, 0, ZoneOffset.ofTotalSeconds(offsetSeconds))

    private companion object {
        const val TAG = "ForecastAccuracy"
        const val PREFERENCES = "accuracy"
        const val MGM_HOURS_FIXED = "mgm_hours_utc"
        const val HOURLY = "H"
        const val MAX = "X"
        const val MIN = "N"
        const val KEEP_DAYS = 45L
        const val WINDOW_DAYS = 30L
        const val FRESH_MS = 40 * 60_000L
        const val MIN_HOURS_FOR_DAY = 16
    }
}
