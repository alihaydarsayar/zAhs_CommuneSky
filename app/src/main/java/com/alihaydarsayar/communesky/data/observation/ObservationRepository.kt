package com.alihaydarsayar.communesky.data.observation

import android.util.Log
import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.model.GeoDistance
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationPolicy
import com.alihaydarsayar.communesky.model.ObservationSource
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.roundToInt

/** Bir yer için bulunmuş MGM istasyonu ([stationId] null: bu yerin MGM istasyonu yok). */
data class MgmStationInfo(
    val queryLatitude: Double,
    val queryLongitude: Double,
    val stationId: Int?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
)

/** Ölçümlerin cihazda saklandığı yer. Testlerde bellek içi sahtesi kullanılır. */
interface ObservationStore {
    fun observeAll(): Flow<Map<Long, Observation>>
    suspend fun get(placeId: Long): Observation?
    suspend fun lastRequestMillis(placeId: Long): Long?
    suspend fun save(placeId: Long, observation: Observation?, requestedAtMillis: Long)
    suspend fun station(placeId: Long): MgmStationInfo?
    suspend fun saveStation(placeId: Long, station: MgmStationInfo)
}

/**
 * Anlık durum için istasyon ölçümleri. Öncelik sırası:
 * 1. MGM istasyonu (sadece Türkiye'deki yerler): yer başına bir kez en yakın istasyon bulunur.
 * 2. MGM hata verir, cevap vermez ya da ölçüm 30 dakikadan eskiyse en yakın havalimanı (METAR).
 * 3. O da yoksa ölçüm kullanılmaz; ekran Open-Meteo tahminiyle devam eder (hata gösterilmez).
 *
 * Güncellik: aynı yer için son istekten 3 dakika geçmeden tekrar sorulmaz. Her kaynak en fazla
 * birkaç saniye beklenir; bu iş tahminden ayrı yürür, ekran beklemez.
 */
@Singleton
class ObservationRepository @Inject constructor(
    private val mgmApi: Lazy<MgmApi>,
    private val metarApi: Lazy<AviationWeatherApi>,
    private val store: ObservationStore,
) {
    /** Testlerde saat değiştirilebilsin diye. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Açılış ve ön plana gelme aynı anda tetiklerse istekler çift gitmesin. */
    private val mutex = Mutex()

    val observations: Flow<Map<Long, Observation>> = store.observeAll()

    suspend fun observation(placeId: Long): Observation? = store.get(placeId)

    /**
     * Verilen yerlerin ölçümlerini yeniler. Hiçbir zaman istisna fırlatmaz.
     * @return En az bir yerin ölçümü değiştiyse true (widget'lar yeniden çizilsin).
     */
    suspend fun refresh(places: List<Place>): Boolean = mutex.withLock {
        coroutineScope {
            places.map { place -> async { refreshOne(place) } }.awaitAll().any { it }
        }
    }

    private suspend fun refreshOne(place: Place): Boolean {
        val now = clock()
        val last = store.lastRequestMillis(place.id)
        if (last != null && now - last < MIN_INTERVAL_MS) return false
        val observation = try {
            fetch(place, Instant.ofEpochMilli(now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Ölçüm alınamadı", e)
            null
        }
        val previous = store.get(place.id)
        store.save(place.id, observation, now)
        return observation != previous
    }

    private suspend fun fetch(place: Place, now: Instant): Observation? {
        val lat = place.city.latitude
        val lon = place.city.longitude
        if (isInTurkey(lat, lon)) {
            mgm(place)?.takeIf { ObservationPolicy.isUsable(it, lat, lon, now) }?.let { return it }
        }
        return metar(lat, lon)?.takeIf { ObservationPolicy.isUsable(it, lat, lon, now) }
    }

    // --- MGM ---

    private suspend fun mgm(place: Place): Observation? {
        val station = mgmStation(place) ?: return null
        val stationId = station.stationId ?: return null
        val dto = attempt("MGM") { mgmApi.get().latest(stationId).firstOrNull() } ?: return null
        val code = MgmWeatherCodes.map(dto.weatherCode)
        return Observation(
            source = ObservationSource.Mgm,
            stationName = station.name ?: return null,
            latitude = station.latitude ?: return null,
            longitude = station.longitude ?: return null,
            elevationMeters = station.elevation,
            observedAtMillis = runCatching { Instant.parse(dto.time).toEpochMilli() }.getOrNull() ?: return null,
            temperature = dto.temperature.valid(),
            humidity = dto.humidity.valid()?.toInt(),
            windSpeedKmh = dto.windSpeedKmh.valid(),
            weatherCode = code.wmoCode,
            isWet = code.isWet,
            hasThunder = code.hasThunder,
        )
    }

    /** Yerin MGM istasyonu: kayıtlıysa onu kullanır, yoksa (ya da yer 2 km'den fazla değiştiyse) bulur. */
    private suspend fun mgmStation(place: Place): MgmStationInfo? {
        val lat = place.city.latitude
        val lon = place.city.longitude
        store.station(place.id)?.let { saved ->
            val moved = GeoDistance.kilometers(saved.queryLatitude, saved.queryLongitude, lat, lon)
            if (moved < STATION_REUSE_KM) return saved
        }
        val center = attempt("MGM istasyon") { mgmApi.get().center(lat, lon) } ?: return null
        val stationId = center.observationStationId
        val station = stationId?.let { id -> attempt("MGM istasyon") { mgmApi.get().station(id).firstOrNull() } }
        if (stationId != null && station == null) return null // geçici hata; bir dahaki sefere tekrar denenir
        val info = MgmStationInfo(
            queryLatitude = lat,
            queryLongitude = lon,
            stationId = station?.id,
            name = station?.name?.let(::titleCaseTurkish),
            latitude = station?.latitude,
            longitude = station?.longitude,
            elevation = station?.elevation,
        )
        store.saveStation(place.id, info)
        return info
    }

    // --- METAR ---

    private suspend fun metar(lat: Double, lon: Double): Observation? {
        // Yaklaşık 50 km yarıçaplı kutu; en yakın istasyon seçilir.
        val bbox = String.format(
            Locale.ROOT, "%.2f,%.2f,%.2f,%.2f",
            lat - BBOX_DEG, lon - BBOX_DEG * 1.4, lat + BBOX_DEG, lon + BBOX_DEG * 1.4,
        )
        val metars = attempt("METAR") { metarApi.get().metars(bbox) } ?: return null
        val nearest = metars.minByOrNull { GeoDistance.kilometers(it.lat, it.lon, lat, lon) } ?: return null
        val code = MetarWeatherCodes.map(nearest.wxString, nearest.cover)
        return Observation(
            source = ObservationSource.Metar,
            stationName = cleanAirportName(nearest.name) ?: nearest.icaoId,
            latitude = nearest.lat,
            longitude = nearest.lon,
            elevationMeters = nearest.elev,
            observedAtMillis = nearest.obsTime * 1000,
            temperature = nearest.temp,
            humidity = relativeHumidity(nearest.temp, nearest.dewp),
            windSpeedKmh = nearest.wspd?.let { it * KNOT_TO_KMH },
            weatherCode = code.wmoCode,
            isWet = code.isWet,
            hasThunder = code.hasThunder,
        )
    }

    /** Hata ya da zaman aşımında null; ekrana hata yansımaz, sıradaki kaynağa geçilir. */
    private suspend fun <T> attempt(source: String, block: suspend () -> T): T? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$source cevap vermedi: ${e.message}")
        null
    }

    internal companion object {
        const val TAG = "Observations"
        const val TIMEOUT_MS = 4_000L
        const val MIN_INTERVAL_MS = 3 * 60 * 1000L
        const val STATION_REUSE_KM = 2.0
        const val BBOX_DEG = 0.45
        const val KNOT_TO_KMH = 1.852

        /** Türkiye'yi kabaca kapsayan kutu; dışındaki yerler için MGM'ye hiç sorulmaz. */
        fun isInTurkey(lat: Double, lon: Double) = lat in 35.8..42.2 && lon in 25.6..44.9

        /** MGM eksik ölçümü -9999 olarak verir. */
        private fun Double?.valid(): Double? = this?.takeIf { it > -9000 }

        /** "SAMSUN BÖLGE" → "Samsun Bölge" (Türkçe büyük/küçük harf kurallarıyla). */
        fun titleCaseTurkish(name: String): String {
            val tr = Locale.forLanguageTag("tr")
            return name.lowercase(tr).split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(tr) }
            }
        }

        /** "Samsun-Çarşamba Arpt, SA, TR" → "Samsun-Çarşamba". */
        fun cleanAirportName(name: String?): String? = name
            ?.substringBefore(',')
            ?.replace(Regex("\\s+(Intl\\s+)?(Arpt|Airport|Intl|International|Airfield|AB|Ab)$"), "")
            ?.trim()
            ?.ifEmpty { null }

        /** Sıcaklık ve çiy noktasından bağıl nem (Magnus formülü). */
        fun relativeHumidity(temp: Double?, dewPoint: Double?): Int? {
            if (temp == null || dewPoint == null) return null
            fun saturation(t: Double) = exp(17.625 * t / (243.04 + t))
            return (100 * saturation(dewPoint) / saturation(temp)).roundToInt().coerceIn(0, 100)
        }
    }
}
