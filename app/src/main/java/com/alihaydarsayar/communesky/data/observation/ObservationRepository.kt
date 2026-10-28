package com.alihaydarsayar.communesky.data.observation

import android.util.Log
import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.model.CoordinatePrivacy
import com.alihaydarsayar.communesky.model.GeoDistance
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationPolicy
import com.alihaydarsayar.communesky.model.ObservationSource
import com.alihaydarsayar.communesky.model.StationNetwork
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
    /** İlin plaka numarası: ilin bütün ölçümlerini tek istekte almak için. Eski kayıtlarda yoktur. */
    val provincePlate: Int? = null,
)

/**
 * Bir yer için bulunmuş MGM istasyonları: yere en yakından uzağa. Liste MGM'nin il istasyon
 * listesinden çıkarılır; ilçenin resmi "son durum" istasyonu da her zaman listededir.
 */
data class MgmStationInfo(
    val queryLatitude: Double,
    val queryLongitude: Double,
    val candidates: List<MgmCandidate>,
    /** Listenin bulunduğu an; 0: bilinmiyor (eski kayıt). Liste en çok [ObservationRepository.STATION_LIST_MAX_AGE_MS] kadar yaşar. */
    val resolvedAtMillis: Long = 0,
    /** Listeyi bulan uygulamanın sürüm kodu; 0: bilinmiyor. Sürüm değişince liste yeniden bulunur. */
    val appVersion: Int = 0,
    /** Liste MGM'nin bütün Türkiye listesinden mi çıkarıldı? Yalnız yerin kendi ilinden çıkarılan liste eksik olabilir. */
    val complete: Boolean = true,
)

/** Ölçümlerin cihazda saklandığı yer. Testlerde bellek içi sahtesi kullanılır. */
interface ObservationStore {
    fun observeAll(): Flow<Map<Long, Observation>>
    suspend fun get(placeId: Long): Observation?
    suspend fun lastRequestMillis(placeId: Long): Long?
    suspend fun save(placeId: Long, observation: Observation?, requestedAtMillis: Long)
    suspend fun station(placeId: Long): MgmStationInfo?
    suspend fun saveStation(placeId: Long, station: MgmStationInfo)

    /** Çalışan uygulamanın sürüm kodu; saklanan istasyon listeleri bununla damgalanır. */
    val appVersion: Int

    /** MGM'nin bütün istasyonlarının listesi: bütün yerler için tek ortak kopya, diskte. */
    suspend fun stationDirectory(): StationDirectory?
    suspend fun saveStationDirectory(directory: StationDirectory)
}

/** MGM'nin istasyon listesi ve indirildiği an. */
@Serializable
data class StationDirectory(val fetchedAtMillis: Long, val stations: List<MgmStationDto>, val appVersion: Int = 0)

/**
 * Anlık durum için istasyon ölçümleri.
 *
 * Adaylar:
 * - MGM istasyonları (sadece Türkiye'deki yerler): yere 25 km'den yakın istasyonlar, en yakından
 *   uzağa; 30 dakikadan taze ölçümler ağı kurar. İstasyonlar yer başına bulunup saklanır; liste en çok
 *   24 saat ve aynı uygulama sürümü boyunca kullanılır, sonra yeniden bulunur. Yeniden bulma başarısız ya da
 *   eksik kalırsa eski iyi liste korunur.
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

    /** İl ölçümleri: plaka → (alındığı an, istasyon numarası → ölçüm). Aynı ildeki yerler paylaşır. */
    private val provinceCache = mutableMapOf<Int, Pair<Long, Map<Int, MgmObservationDto>>>()
    private val provinceMutex = Mutex()
    private val directoryMutex = Mutex()

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
        // Yeni ölçüm bulunamadıysa (kısa bir kesinti, servis düşük) elimizdeki ölçüm hâlâ geçerliyse ve aynı çevredense
        // silinmez: ekran bir anda istasyondan tahmine atlamasın. Eskiyince kendiliğinden kalkar.
        val result = observation ?: previous?.takeIf { stillGood(it, place, Instant.ofEpochMilli(now)) }
        store.save(place.id, result, now)
        return result != previous
    }

    /** Eski ölçüm bu yer için hâlâ kullanılabilir mi: taze ve yerin çevresinde (yer başka şehre geçmediyse). */
    private fun stillGood(observation: Observation, place: Place, now: Instant): Boolean =
        ObservationPolicy.isFresh(observation, now) &&
            ObservationPolicy.distanceKm(observation, place.city.latitude, place.city.longitude) <= StationNetwork.RADIUS_KM

    private suspend fun fetch(place: Place, now: Instant): Observation? = coroutineScope {
        val lat = place.city.latitude
        val lon = place.city.longitude
        log(place, "konum %.4f, %.4f".format(Locale.ROOT, lat, lon))
        val metars = async { metars(place, now) }
        // Türkiye'de havalimanı ölçümleri de ağa bir istasyon olarak girer; MGM istekleri onları beklemeden gider.
        val mgm = async { if (isInTurkey(lat, lon)) mgm(place, now) { metars.await() } else null }
        val network = mgm.await()
        val candidates = listOfNotNull(network) + metars.await()
        // İstasyon ağı kurulduysa o kullanılır; yoksa adaylar arasından en yakın ve en taze olan.
        val best = network?.takeIf { it.network.isNotEmpty() } ?: ObservationPolicy.chooseBest(candidates, lat, lon, now)
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

    /**
     * Yerin MGM ölçümü. Çevrede (25 km) en az iki geçerli istasyon varsa istasyon ağı kurulur; yakındaki
     * havalimanlarının ölçümleri ([airports]) de ağa girer. Dönen ölçüm en yakın geçerli istasyonun ham ölçümüdür
     * ve ağın bütün ölçümlerini taşır. Ağ kurulamazsa eskisi gibi sıcaklık ölçen, taze ilk MGM adayı (ilçenin
     * resmi istasyonu dahil) alınır.
     */
    private suspend fun mgm(place: Place, now: Instant, airports: suspend () -> List<Observation>): Observation? {
        val station = mgmStations(place) ?: return null
        val lat = place.city.latitude
        val lon = place.city.longitude
        val latest = latestReadings(place, station.candidates)
        val observations = station.candidates.mapNotNull { candidate ->
            val dto = latest[candidate.id]
            if (dto == null) {
                log(place, "MGM ${candidate.name} atlandı: cevap yok")
                return@mapNotNull null
            }
            mgmObservation(candidate, dto).also {
                if (it == null) log(place, "MGM ${candidate.name} atlandı: ölçüm zamanı okunamadı (${dto.time})")
            }
        }
        val nearbyAirports = airports().filter { ObservationPolicy.distanceKm(it, lat, lon) <= StationNetwork.RADIUS_KM }
        val network = StationNetwork.valid(observations + nearbyAirports, lat, lon, now)
        // Saklanan ölçüm hakemin ve öğrenen düzeltmenin kullandığı ham ölçümdür: denizdeki istasyon değil, en yakın kara istasyonu.
        val nearest = StationNetwork.referenceStation(network)
        if (nearest != null && network.size >= 2) {
            log(
                place,
                "İstasyon ağı: ${network.size}/${observations.size + nearbyAirports.size} istasyon geçerli " +
                    "(${nearbyAirports.size} havalimanı); ölçüm istasyonu ${nearest.stationName} " +
                    "(%.1f km)".format(Locale.ROOT, ObservationPolicy.distanceKm(nearest, lat, lon)),
            )
            return nearest.copy(network = network)
        }
        // Tek istasyon: önce karadakiler denenir.
        for (observation in observations.sortedBy { StationNetwork.isAtSea(it) }) {
            val distance = ObservationPolicy.distanceKm(observation, lat, lon)
            when {
                observation.temperature == null ->
                    log(place, "MGM ${observation.stationName} (%.1f km) atlandı: sıcaklık ölçmüyor".format(Locale.ROOT, distance))
                !ObservationPolicy.isFresh(observation, now) ->
                    log(
                        place,
                        "MGM ${observation.stationName} (%.1f km) atlandı: ölçüm %d dk önce (en fazla %d)"
                            .format(Locale.ROOT, distance, ageMinutes(observation, now), ObservationPolicy.MAX_AGE.toMinutes()),
                    )
                else -> {
                    log(place, "MGM ${observation.stationName} (%.1f km): %d dk önce".format(Locale.ROOT, distance, ageMinutes(observation, now)))
                    return StationNetwork.clean(observation)
                }
            }
        }
        return null
    }

    /**
     * Adayların son ölçümleri (istasyon numarası → ölçüm). Önce ilin bütün ölçümleri tek istekte alınır; aynı
     * ildeki yerler bu cevabı 3 dakika boyunca paylaşır. İl isteği cevap vermezse ya da cevap, yere en yakın
     * adayları içermiyorsa (kısmi cevap) o adaylar tek tek, aynı anda sorulur (yer başına en çok 6 istek).
     */
    private suspend fun latestReadings(place: Place, candidates: List<MgmCandidate>): Map<Int, MgmObservationDto> = coroutineScope {
        val result = mutableMapOf<Int, MgmObservationDto>()
        val provinces = candidates.mapNotNull { it.provincePlate }.filter { it != UNKNOWN_PLATE }.distinct()
            .associateWith { provinceLatest(it) }
        for (candidate in candidates) {
            val reading = candidate.provincePlate?.let { provinces[it] }?.get(candidate.id)
            if (reading != null) result[candidate.id] = reading
        }
        // Yere en yakın adaylardan il cevabında bulunmayanlar (il isteği hiç cevap vermediyse hepsi) tek tek sorulur.
        val missing = candidates.take(StationNetwork.MAX_SINGLE_REQUESTS).filter { it.id !in result }
        if (missing.isNotEmpty()) {
            log(place, "MGM il ölçümlerinde ${missing.size} yakın istasyon yok; tek tek soruluyor")
            missing.map { candidate -> async { candidate.id to attempt("MGM ${candidate.id}") { mgmApi.get().latest(candidate.id).firstOrNull() } } }
                .awaitAll()
                .forEach { (id, dto) -> if (dto != null) result[id] = dto }
        }
        result
    }

    /**
     * MGM'nin bütün istasyonları: diskteki ortak kopya haftada bir ve uygulama güncellenince yenilenir; bütün yerler onu kullanır. Yenilenemezse
     * eski kopya kullanılır; hiç yoksa null.
     */
    private suspend fun allStations(): List<MgmStationDto>? = directoryMutex.withLock {
        val saved = store.stationDirectory()
        val age = clock() - (saved?.fetchedAtMillis ?: 0)
        if (saved != null && saved.appVersion == store.appVersion && age in 0 until DIRECTORY_MAX_AGE_MS) return@withLock saved.stations
        // Eksik gelen bir liste (ör. yarım kalmış cevap) iyi kopyayı ezmez: belirgin biçimde kısalmışsa atılır.
        val fresh = attempt("MGM istasyonları") { mgmApi.get().allStations() }
            ?.takeIf { it.isNotEmpty() && (saved == null || it.size >= saved.stations.size * DIRECTORY_MIN_SHARE) }
            ?: return@withLock saved?.stations
        store.saveStationDirectory(StationDirectory(clock(), fresh, store.appVersion))
        fresh
    }

    /** Bir ilin bütün son ölçümleri; 3 dakikadan taze cevap yeniden istenmez. Alınamazsa null. */
    private suspend fun provinceLatest(plate: Int): Map<Int, MgmObservationDto>? = provinceMutex.withLock {
        val now = clock()
        provinceCache[plate]?.takeIf { now - it.first < MIN_INTERVAL_MS }?.let { return@withLock it.second }
        val answer = attempt("MGM il $plate") { mgmApi.get().latestInProvince(plate) }?.takeIf { it.isNotEmpty() }
            ?: return@withLock null
        answer.associateBy { it.stationId }.also { provinceCache[plate] = now to it }
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
            knowsPrecipitation = MgmWeatherCodes.knowsPrecipitation(dto.weatherCode, dto.precipitation10Min),
        )
    }

    /**
     * Yerin MGM istasyonları. Kayıtlı liste, yer 2 km'den fazla oynamadıysa, 24 saatten ve aynı uygulama sürümünden
     * eskimediyse ve MGM'nin bütün istasyon listesinden çıkarıldıysa kullanılır; yoksa yeniden bulunur.
     * Yeniden bulma başarısız olursa ya da eksik (yalnız yerin kendi ili) kalırsa elimizdeki liste korunur: kısmi
     * bir cevap iyi listeyi ezmez. Hiç liste yoksa ve bulunamazsa null; bir sonraki yenilemede tekrar denenir.
     */
    private suspend fun mgmStations(place: Place): MgmStationInfo? {
        val lat = place.city.latitude
        val lon = place.city.longitude
        val saved = store.station(place.id)?.takeIf { it.candidates.isNotEmpty() }
        val now = clock()
        if (saved != null) {
            val moved = GeoDistance.kilometers(saved.queryLatitude, saved.queryLongitude, lat, lon)
            val reusable = moved < STATION_REUSE_KM &&
                // Plakası olmayan kayıt eski sürümdendir (en çok 3 istasyon, 15 km): ağ için yeniden bulunur.
                saved.candidates.all { it.provincePlate != null } &&
                saved.appVersion == store.appVersion &&
                now - saved.resolvedAtMillis in 0 until (if (saved.complete) STATION_LIST_MAX_AGE_MS else INCOMPLETE_LIST_RETRY_MS)
            if (reusable) return saved
        }
        // Yer çok oynadıysa eski liste bu yerin değildir; yoksa kötü bir yeniden bulma onu geri getirir.
        val fallback = saved?.takeIf { GeoDistance.kilometers(it.queryLatitude, it.queryLongitude, lat, lon) < STATION_REUSE_KM }
        val found = discoverStations(place)
        // Eski liste, yalnız kendisi eksiksizse ve yeni bulunan eksikse tercih edilir; eski sürümün listesi (plakasız) değil.
        val keepOld = fallback != null && fallback.complete && fallback.candidates.all { it.provincePlate != null }
        if (found == null || (!found.complete && keepOld)) {
            if (fallback != null) log(place, "MGM istasyonları yenilenemedi; kayıtlı liste (${fallback.candidates.size} istasyon) kullanılıyor")
            return fallback
        }
        store.saveStation(place.id, found)
        return found
    }

    /** Yerin istasyonlarını MGM'den bulur; bulunamazsa null. [MgmStationInfo.complete] false ise liste yalnız yerin kendi ilinden. */
    private suspend fun discoverStations(place: Place): MgmStationInfo? {
        val lat = place.city.latitude
        val lon = place.city.longitude
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
        // Bütün Türkiye'nin istasyon listesi (il plakalarıyla): il sınırına yakın yerlerde komşu ilin istasyonları da
        // aday olur. Alınamazsa yalnız yerin kendi ili; bu liste eksik sayılır ve saklanmaz.
        val everything = allStations()
        val province = everything
            ?: center.il?.let { il -> attempt("MGM il istasyonları") { mgmApi.get().stationsInProvince(il) } }.orEmpty()
        val official = center.observationStationId?.let { id ->
            province.firstOrNull { it.id == id } ?: attempt("MGM istasyon") { mgmApi.get().station(id).firstOrNull() }
        }
        val candidates = nearestCandidates(province, official, lat, lon, center.provincePlate)
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
        return MgmStationInfo(lat, lon, candidates, resolvedAtMillis = clock(), appVersion = store.appVersion, complete = everything != null)
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
            // Hava grubu ya da bulut bilgisi olan rapor yağışı da söyler (hava grubu yoksa yağış yoktur).
            knowsPrecipitation = wxString != null || cover != null,
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
        const val CANDIDATE_KM = StationNetwork.RADIUS_KM


        /** Bir yerin istasyon listesi en çok bu kadar eskiyebilir (24 saat); sonra yeniden bulunur. */
        const val STATION_LIST_MAX_AGE_MS = 24L * 60 * 60 * 1000

        /** Yalnız yerin kendi ilinden çıkarılmış (eksik olabilen) liste bu süre sonra yeniden bulunur. */
        const val INCOMPLETE_LIST_RETRY_MS = 15L * 60 * 1000

        /** MGM'nin bütün istasyonlarının ortak kopyası en çok bu kadar eskiyebilir (7 gün). */
        const val DIRECTORY_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** Yeni indirilen istasyon listesi eskisinin bu oranından kısaysa eksik sayılır ve atılır. */
        const val DIRECTORY_MIN_SHARE = 0.7

        /** İlin plakası öğrenilemedi: ölçümler istasyon istasyon sorulur. */
        const val UNKNOWN_PLATE = 0

        /** Türkiye'yi kabaca kapsayan kutu; dışındaki yerler için MGM'ye hiç sorulmaz. */
        fun isInTurkey(lat: Double, lon: Double) = lat in 35.8..42.2 && lon in 25.6..44.9

        /** MGM eksik ölçümü -9999 olarak verir. */
        private fun Double?.valid(): Double? = this?.takeIf { it > -9000 }

        /**
         * Yere 25 km'den yakın bütün istasyonlar, en yakından uzağa (komşu illerinkiler dahil). Resmi ilçe
         * istasyonu bu listede yoksa sona eklenir: başka hiçbiri ölçüm vermezse o denenir.
         * [provincePlate]: istasyonun kendi kaydında plaka yoksa kullanılacak il plakası.
         */
        fun nearestCandidates(
            province: List<MgmStationDto>,
            official: MgmStationDto?,
            latitude: Double,
            longitude: Double,
            provincePlate: Int? = null,
        ): List<MgmCandidate> {
            fun MgmStationDto.distance() = GeoDistance.kilometers(this.latitude, this.longitude, latitude, longitude)
            val nearest = province
                .filter { it.distance() <= CANDIDATE_KM }
                .sortedBy { it.distance() }
            val all = if (official != null && nearest.none { it.id == official.id }) nearest + official else nearest
            return all.map {
                MgmCandidate(it.id, titleCaseTurkish(it.name ?: it.id.toString()), it.latitude, it.longitude, it.elevation, it.provincePlate ?: provincePlate ?: UNKNOWN_PLATE)
            }
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
