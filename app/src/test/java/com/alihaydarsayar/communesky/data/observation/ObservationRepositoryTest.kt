package com.alihaydarsayar.communesky.data.observation

import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
 * Gerçek cevaplara dayanır (8 Ekim 2026 akşamı, Atakum): MGM "SAMSUN BÖLGE" istasyonu 18,6°,
 * Samsun-Çarşamba havalimanı (27 km) METAR'ı gök gürültülü yağmur.
 */
class ObservationRepositoryTest {

    private val now = Instant.parse("2026-10-08T21:10:00Z")
    private val atakum = Place(3, City("Atakum", 41.3322588, 36.2704652), isCurrentLocation = false)
    private val london = Place(7, City("London", 51.5072, -0.1276), isCurrentLocation = false)

    private val samsunCenter = MgmCenterDto(observationStationId = 17030, il = "Samsun", ilce = "Atakum")
    private val samsunStation = MgmStationDto(17030, "SAMSUN BÖLGE", 41.344286, 36.255794, 4.0)
    private fun mgmObservation(time: String = "2026-10-08T21:04:00.000Z", code: String = "CB") =
        MgmObservationDto(17030, time, temperature = 18.6, humidity = 69.0, windSpeedKmh = 9.36, weatherCode = code)

    private val carsambaMetar = MetarDto(
        icaoId = "LTFH",
        obsTime = Instant.parse("2026-10-08T20:50:00Z").epochSecond,
        temp = 19.0,
        dewp = 16.0,
        wspd = 11.0,
        wxString = "-TSRA",
        cover = "SCT",
        lat = 41.255,
        lon = 36.567,
        elev = 7.0,
        name = "Samsun-Çarşamba Arpt, SA, TR",
    )

    private class FakeMgm : MgmApi {
        var center: suspend () -> MgmCenterDto = { throw IOException() }
        var station: suspend () -> List<MgmStationDto> = { throw IOException() }
        var latest: suspend () -> List<MgmObservationDto> = { throw IOException() }
        var centerCalls = 0
        var latestCalls = 0
        override suspend fun center(latitude: Double, longitude: Double): MgmCenterDto {
            centerCalls++
            return center()
        }
        override suspend fun station(stationId: Int) = station()
        override suspend fun latest(stationId: Int): List<MgmObservationDto> {
            latestCalls++
            return latest()
        }
    }

    private class FakeMetar : AviationWeatherApi {
        var answer: suspend () -> List<MetarDto> = { emptyList() }
        var calls = 0
        override suspend fun metars(bbox: String, format: String): List<MetarDto> {
            calls++
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
        mgm.center = { samsunCenter }
        mgm.station = { listOf(samsunStation) }
        mgm.latest = { listOf(mgmObservation(code = code)) }
    }

    @Test
    fun `MGM success is used and the station is remembered`() = runTest {
        mgmWorks()
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))

        val obs = store.get(3)!!
        assertEquals(ObservationSource.Mgm, obs.source)
        assertEquals("Samsun Bölge", obs.stationName)
        assertEquals(18.6, obs.temperature!!, 0.0)
        assertEquals(3, obs.weatherCode)
        assertEquals(0, metar.calls)

        // Üç dakika sonra yeniden sorulur ama istasyon tekrar aranmaz.
        clockMillis += 4 * 60 * 1000
        repository.refresh(listOf(atakum))
        assertEquals(1, mgm.centerCalls)
        assertEquals(2, mgm.latestCalls)
    }

    @Test
    fun `asking again within three minutes uses the cache`() = runTest {
        mgmWorks()
        repository.refresh(listOf(atakum))
        clockMillis += 2 * 60 * 1000
        repository.refresh(listOf(atakum))
        assertEquals(1, mgm.latestCalls)
    }

    @Test
    fun `MGM error falls back to the airport`() = runTest {
        mgm.center = { samsunCenter }
        mgm.station = { listOf(samsunStation) }
        mgm.latest = { throw HttpException(Response.error<Any>(500, "".toResponseBody())) }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))

        val obs = store.get(3)!!
        assertEquals(ObservationSource.Metar, obs.source)
        assertEquals("Samsun-Çarşamba", obs.stationName)
        assertTrue(obs.hasThunder)
        assertTrue(obs.isWet)
        assertEquals(95, obs.weatherCode)
        assertEquals(19.0 * 1, obs.temperature!!, 0.0)
        assertEquals(11 * 1.852, obs.windSpeedKmh!!, 0.001)
    }

    @Test
    fun `MGM not answering in time falls back to the airport`() = runTest {
        mgm.center = { samsunCenter }
        mgm.station = { listOf(samsunStation) }
        mgm.latest = { awaitCancellation() }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Metar, store.get(3)!!.source)
    }

    @Test
    fun `old MGM measurement falls back to the airport`() = runTest {
        mgmWorks()
        mgm.latest = { listOf(mgmObservation(time = "2026-10-08T20:20:00.000Z")) } // 50 dk önce
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Metar, store.get(3)!!.source)
    }

    @Test
    fun `MGM measurement about to expire prefers a fresher airport report`() = runTest {
        // 27 dk önce: 30 dakikaya 3 dk kalmış; bir sonraki yenilemeden önce eskiyeceği için METAR'a geçilir.
        mgmWorks()
        mgm.latest = { listOf(mgmObservation(time = "2026-10-08T20:43:00.000Z")) }
        metar.answer = { listOf(carsambaMetar) }
        repository.refresh(listOf(atakum))
        assertEquals(ObservationSource.Metar, store.get(3)!!.source)
    }

    @Test
    fun `nothing usable falls back to the forecast silently`() = runTest {
        mgm.center = { samsunCenter }
        mgm.station = { listOf(samsunStation) }
        mgm.latest = { throw IOException() }
        metar.answer = { throw IOException() }
        repository.refresh(listOf(atakum))
        assertNull(store.get(3))
        // İstek zamanı yine de kaydedilir: servisler düşükken her açılışta tekrar tekrar sorulmaz.
        assertEquals(clockMillis, store.lastRequestMillis(3))
    }

    @Test
    fun `old airport measurement is not used`() = runTest {
        metar.answer = { listOf(carsambaMetar.copy(obsTime = Instant.parse("2026-10-08T19:50:00Z").epochSecond)) }
        repository.refresh(listOf(atakum.copy(id = 9)))
        // MGM servisi yok (sahte hata veriyor), METAR 80 dk önce: kullanılmaz.
        assertNull(store.get(9))
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
    fun `a far station beyond 40 km is ignored`() = runTest {
        metar.answer = { listOf(carsambaMetar.copy(lat = 41.0, lon = 36.9)) } // ~63 km
        repository.refresh(listOf(london.copy(id = 8, city = atakum.city)))
        assertNull(store.get(8))
    }

    @Test
    fun `helpers`() {
        assertEquals("Samsun Bölge", ObservationRepository.titleCaseTurkish("SAMSUN BÖLGE"))
        assertEquals("İstanbul Kartal", ObservationRepository.titleCaseTurkish("İSTANBUL KARTAL"))
        assertEquals("Istanbul Ataturk", ObservationRepository.cleanAirportName("Istanbul Ataturk Intl Arpt, IS, TR"))
        assertEquals(83, ObservationRepository.relativeHumidity(19.0, 16.0))
        assertFalse(ObservationRepository.isInTurkey(51.5, -0.1))
        assertTrue(ObservationRepository.isInTurkey(41.33, 36.27))
    }
}
