package com.alihaydarsayar.communesky.data.observation

import android.util.Log
import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.model.CoordinatePrivacy
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
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Bir yere yakın MGM istasyonu. */
@Serializable
data class MgmCandidate(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
)

/**
 * Bir yer için bulunmuş MGM istasyonları: yere en yakından uzağa. Liste MGM'nin il istasyon
 * listesinden çıkarılır; ilçenin resmi "son durum" istasyonu da her zaman listededir.
 */
data class MgmStationInfo(
    val queryLatitude: Double,
    val queryLongitude: Double,
    val candidates: List<MgmCandidate>,
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
 * Anlık durum için istasyon ölçümleri.
 *
 * Adaylar:
 * - MGM istasyonları (sadece Türkiye'deki yerler): yere en yakın birkaç istasyon, en yakından
 *   uzağa sırayla sorulur; sıcaklık ölçen ve 30 dakikadan taze ilk ölçüm alınır. İstasyonlar yer
 *   başına bir kez bulunup saklanır.
 * - Yakındaki havalimanları (METAR, NOAA Aviation Weather Center): sadece taze olanlar.
 * Bunlar arasından önce mesafeye, sonra tazeliğe göre en iyisi seçilir (bkz. ObservationPolicy).
 * Hiçbiri yoksa ölçüm kullanılmaz; ekran Open-Meteo tahminiyle devam eder (hata gösterilmez).
 *
 * Güncellik: aynı yer için son istekten 3 dakika geçmeden tekrar sorulmaz. Her istek en fazla
 * birkaç saniye beklenir; bu iş tahminden ayrı yürür, ekran beklemez.
 * Dışarı giden koordinatlar yaklaşık 1 km'ye yuvarlanır; mesafeler cihazda hassas konumla hesaplanır.
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

    private suspend fun fetch(place: Place, now: Instant): Observation? = coroutineScope {
        val lat = place.city.latitude
        val lon = place.city.longitude
        log(place, "konum %.4f, %.4f".format(Locale.ROOT, lat, lon))
        val mgm = async { if (isInTurkey(lat, lon)) mgm(place, now) else null }
        val metars = async { metars(place, now) }
        val candidates = listOfNotNull(mgm.await()) + metars.await()
        val best = ObservationPolicy.chooseBest(candidates, lat, lon, now)
        if (best == null) {
            log(place, "kullanılabilir ölçüm yok (${candidates.size} aday); tahmin kullanılacak")
        } else {
            log(
                place,
                "seçilen: ${best.source} ${best.stationName}, " +
                    "%.1f km, %d dk önce".format(Locale.ROOT, ObservationPolicy.distanceKm(best, lat, lon), ageMinutes(best, now)),
            )
        }
        best
    }

    // --- MGM ---

    /** Yere en yakın, sıcaklık ölçen ve taze ölçüm veren MGM istasyonunun ölçümü. */
    private suspend fun mgm(place: Place, now: Instant): Observation? {
        val station = mgmStations(place) ?: return null
        for (candidate in station.candidates.take(MAX_STATION_TRIES + 1)) {
            val distance = GeoDistance.kilometers(candidate.latitude, candidate.longitude, place.city.latitude, place.city.longitude)
            val dto = attempt("MGM ${candidate.id}") { mgmApi.get().latest(candidate.id).firstOrNull() }
            if (dto == null) {
                log(place, "MGM ${candidate.name} (%.1f km) atlandı: cevap yok".format(Locale.ROOT, distance))
                continue
            }
            val observation = mgmObservation(candidate, dto)
            when {
                observation == null ->
                    log(place, "MGM ${candidate.name} atlandı: ölçüm zamanı okunamadı (${dto.time})")
                observation.temperature == null ->
                    log(place, "MGM ${candidate.name} (%.1f km) atlandı: sıcaklık ölçmüyor".format(Locale.ROOT, distance))
                !ObservationPolicy.isFresh(observation, now) ->
                    log(
                        place,
                        "MGM ${candidate.name} (%.1f km) atlandı: ölçüm %d dk önce (en fazla %d)"
                            .format(Locale.ROOT, distance, ageMinutes(observation, now), ObservationPolicy.MAX_AGE.toMinutes()),
                    )
                else -> {
                    log(place, "MGM ${candidate.name} (%.1f km): %d dk önce".format(Locale.ROOT, distance, ageMinutes(observation, now)))
                    return observation
                }
            }
        }
        return null
    }

    private fun mgmObservation(candidate: MgmCandidate, dto: MgmObservationDto): Observation? {
        val time = runCatching { Instant.parse(dto.time) }.getOrNull() ?: return null
        val temperature = dto.temperature.valid()
        val weather = MgmWeatherCodes.map(dto.weatherCode, dto.precipitation10Min.valid(), temperature)
        if (!weather.isKnown) Log.w(TAG, "Bilinmeyen MGM hadise kodu: ${dto.weatherCode} (istasyon ${candidate.id})")
        return Observation(
            source = ObservationSource.Mgm,
            stationName = candidate.name,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            elevationMeters = candidate.elevation,
            observedAtMillis = time.toEpochMilli(),
            temperature = temperature,
            humidity = dto.humidity.valid()?.let { Math.round(it).toInt() },
            windSpeedKmh = dto.windSpeedKmh.valid(),
            windDirection = dto.windDirection.valid()?.let { Math.round(it).toInt() },
            weatherCode = weather.wmoCode,
            isWet = weather.isWet,
            hasThunder = weather.hasThunder,
        )
    }

    /**
     * Yerin MGM istasyonları: kayıtlıysa onları kullanır; yoksa (ya da "Bulunduğum yer" 2 km'den
     * fazla değiştiyse) bulur. Bulamazsa hiçbir şey saklamaz; bir sonraki yenilemede tekrar dener.
     */
    private suspend fun mgmStations(place: Place): MgmStationInfo? {
        val lat = place.city.latitude
        val lon = place.city.longitude
        store.station(place.id)?.let { saved ->
            val moved = GeoDistance.kilometers(saved.queryLatitude, saved.queryLongitude, lat, lon)
            if (moved < STATION_REUSE_KM && saved.candidates.isNotEmpty()) return saved
        }
        val center = attempt("MGM merkez") {
            mgmApi.get().center(CoordinatePrivacy.round(lat), CoordinatePrivacy.round(lon))
        }
        if (center == null) {
            log(place, "MGM merkez bulunamadı; bir sonraki yenilemede tekrar denenecek")
            return null
        }
        log(
            place,
            "MGM merkezi: ${center.ilce}/${center.il} (${center.latitude}, ${center.longitude}), " +
                "resmi istasyon ${center.observationStationId}",
        )
        val province = center.il?.let { il -> attempt("MGM il istasyonları") { mgmApi.get().stationsInProvince(il) } }.orEmpty()
        val official = center.observationStationId?.let { id ->
            province.firstOrNull { it.id == id } ?: attempt("MGM istasyon") { mgmApi.get().station(id).firstOrNull() }
        }
        val candidates = nearestCandidates(province, official, lat, lon)
        if (candidates.isEmpty()) {
            log(place, "MGM istasyonu bulunamadı; bir sonraki yenilemede tekrar denenecek")
            return null
        }
        log(
            place,
            "MGM adayları: " + candidates.joinToString {
                "${it.name} %.1f km".format(Locale.ROOT, GeoDistance.kilometers(it.latitude, it.longitude, lat, lon))
            },
        )
        val info = MgmStationInfo(lat, lon, candidates)
        store.saveStation(place.id, info)
        return info
    }

    // --- METAR ---

    /** Yakındaki havalimanlarının taze ölçümleri. */
    private suspend fun metars(place: Place, now: Instant): List<Observation> {
        val lat = CoordinatePrivacy.round(place.city.latitude)
        val lon = CoordinatePrivacy.round(place.city.longitude)
        // Yaklaşık 50 km yarıçaplı kutu.
        val bbox = String.format(
            Locale.ROOT, "%.2f,%.2f,%.2f,%.2f",
            lat - BBOX_DEG, lon - BBOX_DEG * 1.4, lat + BBOX_DEG, lon + BBOX_DEG * 1.4,
        )
        val metars = attempt("METAR") { metarApi.get().metars(bbox) } ?: return emptyList()
        return metars.map { it.toObservation() }.filter { obs ->
            ObservationPolicy.isFresh(obs, now).also { fresh ->
                if (!fresh) log(place, "METAR ${obs.stationName} atlandı: ${ageMinutes(obs, now)} dk önce")
            }
        }
    }

    private fun MetarDto.toObservation(): Observation {
        val code = MetarWeatherCodes.map(wxString, cover)
        return Observation(
            source = ObservationSource.Metar,
            stationName = cleanAirportName(name) ?: icaoId,
            latitude = lat,
            longitude = lon,
            elevationMeters = elev,
            observedAtMillis = obsTime * 1000,
            temperature = temp,
            humidity = if (temp != null && dewp != null) ObservationPolicy.relativeHumidity(temp, dewp) else null,
            windSpeedKmh = wspd?.let { it * KNOT_TO_KMH },
            // "VRB" (değişken) rüzgârda yön yok; o durumda hız da kullanılmaz.
            windDirection = wdir?.content?.toIntOrNull(),
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

    private fun log(place: Place, message: String) {
        Log.i(TAG, "[${place.city.name ?: place.id}] $message")
    }

    private fun ageMinutes(observation: Observation, now: Instant): Long =
        Duration.between(observation.observedAt, now).toMinutes()

    internal companion object {
        const val TAG = "Observations"
        const val TIMEOUT_MS = 4_000L
        const val MIN_INTERVAL_MS = 3 * 60 * 1000L
        const val STATION_REUSE_KM = 2.0
        const val BBOX_DEG = 0.45
        const val KNOT_TO_KMH = 1.852

        /** Bu mesafeden uzak MGM istasyonu aday sayılmaz (resmi ilçe istasyonu hariç). */
        const val CANDIDATE_KM = 15.0

        /** En yakın kaç MGM istasyonu aday olur (resmi ilçe istasyonu ayrıca eklenebilir). */
        const val MAX_STATION_TRIES = 3

        /** Türkiye'yi kabaca kapsayan kutu; dışındaki yerler için MGM'ye hiç sorulmaz. */
        fun isInTurkey(lat: Double, lon: Double) = lat in 35.8..42.2 && lon in 25.6..44.9

        /** MGM eksik ölçümü -9999 olarak verir. */
        private fun Double?.valid(): Double? = this?.takeIf { it > -9000 }

        /**
         * Yere en yakın istasyonlar (15 km içinde, en fazla 3), en yakından uzağa. Resmi ilçe
         * istasyonu bu listede yoksa sona eklenir: başka hiçbiri ölçüm vermezse o denenir.
         */
        fun nearestCandidates(
            province: List<MgmStationDto>,
            official: MgmStationDto?,
            latitude: Double,
            longitude: Double,
        ): List<MgmCandidate> {
            fun MgmStationDto.distance() = GeoDistance.kilometers(this.latitude, this.longitude, latitude, longitude)
            val nearest = province
                .filter { it.distance() <= CANDIDATE_KM }
                .sortedBy { it.distance() }
                .take(MAX_STATION_TRIES)
            val all = if (official != null && nearest.none { it.id == official.id }) nearest + official else nearest
            return all.map { MgmCandidate(it.id, titleCaseTurkish(it.name ?: it.id.toString()), it.latitude, it.longitude, it.elevation) }
        }

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
    }
}
