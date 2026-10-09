package com.alihaydarsayar.communesky.data.observation

import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/**
 * Gerçek cevaplara dayanır (8–9 Ekim 2026): MGM "SAMSUN BÖLGE" (Atakum) ve "TUZLA" istasyonları,
 * Samsun-Çarşamba (27 km) ve Sabiha Gökçen havalimanı METAR'ları.
 */
class ObservationRepositoryTest {

    private val now = Instant.parse("2026-10-08T21:10:00Z")
    private val atakum = Place(3, City("Atakum", 41.3322588, 36.2704652), isCurrentLocation = false)
    private val yayla = Place(5, City("Yayla", 40.8306571, 29.3110728), isCurrentLocation = true)
    private val london = Place(7, City("London", 51.5072, -0.1276), isCurrentLocation = false)

    private val samsunCenter = MgmCenterDto(observationStationId = 17030, latitude = 41.3442, longitude = 36.2564, il = "Samsun", ilce = "Atakum")
    private val samsunStation = MgmStationDto(17030, "SAMSUN BÖLGE", 41.344286, 36.255794, 4.0)
    private val bafra = MgmStationDto(17622, "BAFRA", 41.551018, 35.924752, 103.0)

    private fun mgmObservation(
        id: Int = 17030,
        time: String = "2026-10-08T21:04:00.000Z",
        code: String = "CB",
        temperature: Double? = 18.6,
        tenMinutes: Double? = 0.0,
    ) = MgmObservationDto(id, time, temperature, humidity = 69.0, windSpeedKmh = 9.36, windDirection = 245.0, precipitation10Min = tenMinutes, weatherCode = code)

    private val carsambaMetar = MetarDto(
        icaoId = "LTFH",
        obsTime = Instant.parse("2026-10-08T20:50:00Z").epochSecond,
        temp = 19.0,
        dewp = 16.0,
        wspd = 11.0,
        wdir = JsonPrimitive(140),
        wxString = "-TSRA",
        cover = "SCT",
        lat = 41.255,
        lon = 36.567,
        elev = 7.0,
        name = "Samsun-Çarşamba Arpt, SA, TR",
    )

    private class FakeMgm : MgmApi {
        var centerAnswer: suspend () -> MgmCenterDto = { throw IOException() }
        var stationAnswer: suspend () -> List<MgmStationDto> = { throw IOException() }
        var provinceAnswer: suspend () -> List<MgmStationDto> = { throw IOException() }
        var latestAnswer: suspend (Int) -> List<MgmObservationDto> = { throw IOException() }
        var centerCalls = 0
        val latestCalls = mutableListOf<Int>()
        override suspend fun center(latitude: Double, longitude: Double): MgmCenterDto {
            centerCalls++
            // Dışarı giden koordinat yaklaşık 1 km'ye yuvarlanmış olmalı.
            check(latitude == Math.round(latitude * 100) / 100.0) { "yuvarlanmamış enlem: $latitude" }
            return centerAnswer()
        }
        override suspend fun station(stationId: Int) = stationAnswer()
        override suspend fun stationsInProvince(province: String) = provinceAnswer()
        override suspend fun latest(stationId: Int): List<MgmObservationDto> {
            latestCalls += stationId
            return latestAnswer(stationId)
        }
    }

    private class FakeMetar : AviationWeatherApi {
        var answer: suspend () -> List<MetarDto> = { emptyList() }
        var calls = 0
        var lastBbox = ""
        override suspend fun metars(bbox: String, format: String): List<MetarDto> {
            calls++
            lastBbox = bbox
            return answer()
        }
    }

    private class MemoryStore : ObservationStore {
        val observations = MutableStateFlow<Map<Long, Observation>>(emptyMap())
        val requested = mutableMapOf<Long, Long>()
        val stations = mutableMapOf<Long, MgmStationInfo>()
        override fun observeAll(): Flow<Map<Long, Observation>> = observations
        override suspend fun get(placeId: Long) = observations.value[placeId]
        override suspend fun lastRequestMillis(placeId: Long) = requested[placeId]
        override suspend fun save(placeId: Long, observation: Observation?, requestedAtMillis: Long) {
            requested[placeId] = requestedAtMillis
            observations.value = if (observation == null) observations.value - placeId else observations.value + (placeId to observation)
        }
        override suspend fun station(placeId: Long) = stations[placeId]
        override suspend fun saveStation(placeId: Long, station: MgmStationInfo) {
            stations[placeId] = station
        }
    }

    private val mgm = FakeMgm()
    private val metar = FakeMetar()
    private val store = MemoryStore()
    private var clockMillis = now.toEpochMilli()
    private val repository = ObservationRepository({ mgm }, { metar }, store).also { it.clock = { clockMillis } }

    private fun mgmWorks(code: String = "CB") {
        mgm.centerAnswer = { samsunCenter }
        mgm.stationAnswer = { listOf(samsunStation) }
        mgm.provinceAnswer = { listOf(bafra, samsunStation) }
        mgm.latestAnswer = { listOf(mgmObservation(code = code)) }
    }

    @Test
    fun `MGM success is used and the stations are remembered`() = runTest {
        mgmWorks()
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))

        val obs = store.get(3)!!
        assertEquals(ObservationSource.Mgm, obs.source)
        assertEquals("Samsun Bölge", obs.stationName)
        assertEquals(18.6, obs.temperature!!, 0.0)
        assertEquals(245, obs.windDirection)
        assertEquals(3, obs.weatherCode)

        // Üç dakika sonra yeniden sorulur ama istasyonlar tekrar aranmaz.
        clockMillis += 4 * 60 * 1000
        repository.refresh(listOf(atakum))
        assertEquals(1, mgm.centerCalls)
        assertEquals(listOf(17030, 17030), mgm.latestCalls)
    }

    @Test
    fun `asking again within three minutes uses the cache`() = runTest {
        mgmWorks()
        repository.refresh(listOf(atakum))
        clockMillis += 2 * 60 * 1000
        repository.refresh(listOf(atakum))
        assertEquals(1, mgm.latestCalls.size)
    }

    @Test
    fun `MGM error falls back to the airport`() = runTest {
        mgmWorks()
        mgm.latestAnswer = { throw HttpException(Response.error<Any>(500, "".toResponseBody())) }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))

        val obs = store.get(3)!!
        assertEquals(ObservationSource.Metar, obs.source)
        assertEquals("Samsun-Çarşamba", obs.stationName)
        assertTrue(obs.hasThunder && obs.isWet)
        assertEquals(95, obs.weatherCode)
        assertEquals(11 * 1.852, obs.windSpeedKmh!!, 0.001)
        assertEquals(140, obs.windDirection)
        assertEquals(83, obs.humidity)
    }

    @Test
    fun `MGM not answering in time falls back to the airport`() = runTest {
        mgmWorks()
        mgm.latestAnswer = { awaitCancellation() }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Metar, store.get(3)!!.source)
    }

    @Test
    fun `old MGM measurement falls back to the airport`() = runTest {
        mgmWorks()
        mgm.latestAnswer = { listOf(mgmObservation(time = "2026-10-08T20:20:00.000Z")) } // 50 dk önce
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Metar, store.get(3)!!.source)
    }

    @Test
    fun `a 27 minute old MGM measurement nearby is still used`() = runTest {
        // Madde 8: "eskimek üzere" kuralı yakın istasyonu gereksiz yere atlamasın.
        mgmWorks()
        mgm.latestAnswer = { listOf(mgmObservation(time = "2026-10-08T20:43:00.000Z")) }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Mgm, store.get(3)!!.source)
    }

    @Test
    fun `nothing usable falls back to the forecast silently`() = runTest {
        mgmWorks()
        mgm.latestAnswer = { throw IOException() }
        metar.answer = { throw IOException() }
        repository.refresh(listOf(atakum))
        assertNull(store.get(3))
        // İstek zamanı yine de kaydedilir: servisler düşükken her açılışta tekrar tekrar sorulmaz.
        assertEquals(clockMillis, store.lastRequestMillis(3))
    }

    @Test
    fun `failed station lookup is retried on the next refresh`() = runTest {
        mgm.centerAnswer = { throw IOException() }
        repository.refresh(listOf(atakum))
        assertNull(store.station(3))

        mgmWorks()
        clockMillis += 4 * 60 * 1000
        repository.refresh(listOf(atakum))
        assertEquals(2, mgm.centerCalls)
        assertEquals(ObservationSource.Mgm, store.get(3)!!.source)
    }

    @Test
    fun `old airport reports are ignored`() = runTest {
        metar.answer = { listOf(carsambaMetar.copy(obsTime = Instant.parse("2026-10-08T19:50:00Z").epochSecond)) }
        repository.refresh(listOf(atakum))
        assertNull(store.get(3))
    }

    @Test
    fun `places outside Turkey never ask MGM`() = runTest {
        metar.answer = {
            listOf(carsambaMetar.copy(icaoId = "EGLC", lat = 51.505, lon = 0.055, name = "London City Arpt, EN, GB", wxString = null))
        }
        repository.refresh(listOf(london))
        assertEquals(0, mgm.centerCalls)
        assertEquals("London City", store.get(7)!!.stationName)
    }

    @Test
    fun `nearest fresh station wins over a closer stale one and over far airports`() = runTest {
        // Madde 6 ve 9: Yayla'ya en yakın istasyon Tuzla (1,6 km); deniz feneri istasyonu sıcaklık
        // ölçmüyorsa atlanır; Sabiha Gökçen METAR'ı (7 km) daha uzak olduğu için seçilmez.
        val tuzla = MgmStationDto(18100, "TUZLA", 40.827623, 29.292767, 3.0)
        val fener = MgmStationDto(17448, "TUZLA İTÜ GÜNEY MENDİREK FENERİ", 40.81, 29.29, 1.0)
        val saw = MgmStationDto(17063, "İSTANBUL SABİHA GÖKÇEN HAVALİMANI", 40.892797, 29.297236, 99.0)
        mgm.centerAnswer = { MgmCenterDto(observationStationId = 18100, il = "İstanbul", ilce = "Tuzla") }
        mgm.provinceAnswer = { listOf(saw, fener, tuzla) }
        mgm.latestAnswer = { id ->
            when (id) {
                18100 -> listOf(mgmObservation(id = 18100, time = "2026-10-08T21:09:00.000Z", temperature = 23.1))
                17448 -> listOf(mgmObservation(id = 17448, temperature = null))
                else -> listOf(mgmObservation(id = id))
            }
        }
        metar.answer = {
            listOf(carsambaMetar.copy(icaoId = "LTFJ", lat = 40.899, lon = 29.309, name = "Istanbul/Gokcen Arpt, IS, TR", wxString = null))
        }
        repository.refresh(listOf(yayla))

        val obs = store.get(5)!!
        assertEquals(ObservationSource.Mgm, obs.source)
        assertEquals("Tuzla", obs.stationName)
        assertEquals(23.1, obs.temperature!!, 0.0)
        assertEquals(listOf(18100), mgm.latestCalls)
        // NOAA'ya giden kutu yuvarlanmış koordinattan kurulur.
        assertTrue(metar.lastBbox.startsWith("40.38,28.68"))
    }

    @Test
    fun `MGM rain amount marks the measurement wet`() = runTest {
        mgmWorks()
        mgm.latestAnswer = { listOf(mgmObservation(code = "CB", tenMinutes = 0.4)) }
        repository.refresh(listOf(atakum))
        val obs = store.get(3)!!
        assertTrue(obs.isWet)
        assertEquals(61, obs.weatherCode) // 0,4 mm / 10 dk = 2,4 mm/sa
    }

    @Test
    fun `MGM time is UTC`() {
        // veriZamani "Z" ile biter: 07:39 UTC = 10:39 Türkiye saati (MGM sitesinde görünen saat).
        val time = Instant.parse("2026-10-09T07:39:00.000Z")
        assertEquals(
            "10:39",
            time.atZone(java.time.ZoneId.of("Europe/Istanbul")).toLocalTime().toString(),
        )
    }

    @Test
    fun `nearest candidates keep the official station last`() {
        val tuzla = MgmStationDto(18100, "TUZLA", 40.827623, 29.292767, 3.0)
        val near1 = MgmStationDto(1, "A", 40.83, 29.31, null)
        val near2 = MgmStationDto(2, "B", 40.84, 29.32, null)
        val near3 = MgmStationDto(3, "C", 40.85, 29.33, null)
        val far = MgmStationDto(4, "UZAK", 41.2, 29.9, null)
        val candidates = ObservationRepository.nearestCandidates(listOf(far, near3, near2, near1), tuzla, 40.8306, 29.3110)
        assertEquals(listOf(1, 2, 3, 18100), candidates.map { it.id })
        assertFalse(candidates.any { it.id == 4 })
    }

    @Test
    fun `helpers`() {
        assertEquals("Samsun Bölge", ObservationRepository.titleCaseTurkish("SAMSUN BÖLGE"))
        assertEquals("İstanbul Kartal", ObservationRepository.titleCaseTurkish("İSTANBUL KARTAL"))
        assertEquals("Istanbul Ataturk", ObservationRepository.cleanAirportName("Istanbul Ataturk Intl Arpt, IS, TR"))
        assertFalse(ObservationRepository.isInTurkey(51.5, -0.1))
        assertTrue(ObservationRepository.isInTurkey(41.33, 36.27))
    }
}
