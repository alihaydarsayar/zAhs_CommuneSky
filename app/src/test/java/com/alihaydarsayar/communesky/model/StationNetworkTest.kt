package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** İstasyon ağı. İstasyonlar sahtedir; Beşiktaş'ın çevresine belli uzaklıklarla yerleştirilir. */
class StationNetworkTest {

    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val besiktas = City("Beşiktaş", 41.0422, 29.0083)
    private val placeElevation = 30.0

    /** Beşiktaş'ın [km] kuzeyinde bir istasyon (1 km ≈ 0,009°). */
    private fun station(
        name: String,
        km: Double,
        temperature: Double? = 17.0,
        elevation: Double? = 30.0,
        minutesAgo: Long = 5,
        wet: Boolean = false,
        thunder: Boolean = false,
        code: Int? = if (thunder) 95 else if (wet) 61 else 2,
        humidity: Int? = 70,
        wind: Double? = 12.0,
        knows: Boolean = true,
    ) = Observation(
        source = ObservationSource.Mgm,
        stationName = name,
        latitude = besiktas.latitude + km / 111.19,
        longitude = besiktas.longitude,
        elevationMeters = elevation,
        observedAtMillis = now.minusSeconds(minutesAgo * 60).toEpochMilli(),
        temperature = temperature,
        humidity = humidity,
        windSpeedKmh = wind,
        windDirection = 40,
        weatherCode = code,
        isWet = wet,
        hasThunder = thunder,
        knowsPrecipitation = knows,
    )

    private fun blend(stations: List<Observation>, model: Double = 18.0, at: Instant = now) =
        StationNetwork.blend(stations, besiktas.latitude, besiktas.longitude, placeElevation, model, at)

    private fun snapshot(city: City, temperature: Double = 18.0, code: Int = 3, elevation: Double? = placeElevation) = WeatherSnapshot(
        placeId = 5,
        city = city,
        isCurrentLocation = false,
        forecast = Forecast(
            current = CurrentWeather(
                time = LocalDateTime.of(2026, 10, 13, 15, 0),
                temperature = temperature,
                apparentTemperature = temperature - 1.0,
                humidity = 60,
                dewPoint = 10.0,
                windSpeed = 20.0,
                windDirection = 200,
                weatherCode = code,
                isDay = true,
                pressure = 1015.0,
                uvIndex = 3.0,
                visibility = 20_000.0,
            ),
            hourly = emptyList(),
            daily = emptyList(),
            utcOffsetSeconds = 10_800,
            minutely = emptyList(),
            elevation = elevation,
        ),
        fetchedAt = now,
    )

    // --- Kalite kontrolü ve sıcaklık ---

    private val six = listOf(
        station("Yakın", 1.2, temperature = 17.0),
        station("Eski", 2.0, temperature = 12.0, minutesAgo = 40),
        station("Orta", 3.0, temperature = 17.4),
        station("Arızalı", 4.0, temperature = 25.2),
        station("Uzak", 5.0, temperature = 16.8),
        station("Yüksek", 8.0, temperature = 14.4, elevation = 430.0),
    )

    @Test
    fun `a faulty, a high and a stale station do not move the temperature`() {
        val valid = StationNetwork.valid(six, besiktas.latitude, besiktas.longitude, now)
        // 40 dakikalık ölçüm ve komşularından 8° sıcak istasyon dışarıda; yüksekteki istasyon arızalı sayılmaz.
        assertEquals(listOf("Yakın", "Orta", "Uzak", "Yüksek"), valid.map { it.stationName })

        val result = blend(six)!!
        assertEquals(4, result.stationCount)
        // 400 m yüksekteki istasyon sıcaklığa katılmaz (yağış için ağda kalır).
        assertEquals(3, result.temperatureStations)
        assertEquals("Yakın", result.nearest.stationName)
        assertEquals(1.2, result.nearestKm, 0.05)
        // Ağırlıklar 1/km²: Yakın 0,69 · Orta 0,11 · Uzak 0,04 · model 0,004.
        assertEquals(17.05, result.temperature, 0.05)
    }

    @Test
    fun `temperatures are brought down to the height of the place`() {
        // İki istasyon da 200 m yukarıda ve 1,3° daha soğuk: yerin yüksekliğinde 17° eder.
        val stations = listOf(station("A", 1.0, temperature = 15.7, elevation = 230.0), station("B", 1.5, temperature = 15.7, elevation = 230.0))
        assertEquals(17.0, blend(stations, model = 17.0)!!.temperature, 0.01)
    }

    @Test
    fun `a station within 2 km carries most of the weight`() {
        val stations = listOf(station("Dip", 1.9, temperature = 20.0)) + (1..5).map { station("Çevre $it", 2.1 + it * 0.01, temperature = 16.0) }
        val result = blend(stations, model = 16.0)!!
        // Çıplak 1/km² ile payı %18 olurdu; kural en az %70 verir.
        assertEquals(16.0 + 0.7 * 4.0, result.temperature, 0.01)
    }

    @Test
    fun `the model counts little beside a near station and a lot beside far ones`() {
        val near = blend(listOf(station("A", 1.0, temperature = 10.0), station("B", 3.0, temperature = 10.0)), model = 20.0)!!
        assertEquals(10.0, near.temperature, 0.1)
        val far = blend(listOf(station("A", 20.0, temperature = 10.0), station("B", 22.0, temperature = 10.0)), model = 20.0)!!
        // İstasyonlar 20 km'de 0,0025 ve 0,0021; model 15 km'deymiş gibi 0,0044: neredeyse yarı yarıya.
        assertEquals(14.9, far.temperature, 0.2)
    }

    @Test
    fun `stations beyond 25 km are not part of the network`() {
        assertNull(blend(listOf(station("A", 3.0), station("Çok uzak", 26.0))))
    }

    @Test
    fun `impossible values are dropped, the station stays`() {
        val cleaned = StationNetwork.clean(station("A", 1.0, humidity = 120, wind = -3.0))
        assertNull(cleaned.humidity)
        assertNull(cleaned.windSpeedKmh)
        assertNull(cleaned.windDirection)
        assertEquals(17.0, cleaned.temperature!!, 0.0)
        assertEquals(70, StationNetwork.clean(station("B", 1.0)).humidity)
    }

    @Test
    fun `one faulty station among three does not take a sound neighbour with it`() {
        val stations = listOf(station("Arızalı", 1.6, temperature = 25.3), station("Fener", 2.9, temperature = 17.4), station("Havalimanı", 7.0, temperature = 16.2, elevation = 99.0))
        val valid = StationNetwork.valid(stations, besiktas.latitude, besiktas.longitude, now)
        assertEquals(listOf("Fener", "Havalimanı"), valid.map { it.stationName })
    }

    @Test
    fun `two stations that disagree are both kept`() {
        // İki istasyonda hangisinin yanıldığı bilinemez; karşılaştırma için en az iki komşu gerekir.
        val stations = listOf(station("A", 1.0, temperature = 12.0), station("B", 2.0, temperature = 20.0))
        assertEquals(2, StationNetwork.valid(stations, besiktas.latitude, besiktas.longitude, now).size)
    }

    // --- Yağış ---

    @Test
    fun `a single dry station cannot overrule the rain around it`() {
        val stations = listOf(
            station("Kuru", 1.0),
            station("Yağışlı 1", 6.0, wet = true),
            station("Yağışlı 2", 8.0, wet = true, code = 80),
            station("Yağışlı 3", 10.0, wet = true),
        )
        val result = blend(stations)!!
        assertTrue(result.isRaining)
        // Hava kodu en yakın yağışlı istasyondan; nem ve rüzgâr yine en yakın geçerli istasyondan.
        assertEquals(61, result.weatherCode)
        assertEquals("Kuru", result.nearest.stationName)
        assertNull(result.nearbyWet)
    }

    @Test
    fun `rain measured within 5 km means it is raining`() {
        val stations = listOf(station("Kuru 1", 1.0), station("Yağışlı", 4.5, wet = true), station("Kuru 2", 7.0), station("Kuru 3", 9.0))
        assertTrue(blend(stations)!!.isRaining)
    }

    @Test
    fun `one wet station farther away is only a note`() {
        val stations = listOf(station("Kuru 1", 1.0), station("Kuru 2", 4.0), station("Yağışlı", 9.0, wet = true), station("Kuru 3", 12.0))
        val result = blend(stations)!!
        assertFalse(result.isRaining)
        assertEquals(2, result.weatherCode)
        assertEquals("Yağışlı", result.nearbyWet!!.stationName)
    }

    @Test
    fun `rain beyond 15 km does not count as rain here`() {
        val stations = listOf(station("Kuru", 2.0), station("Yağışlı 1", 18.0, wet = true), station("Yağışlı 2", 20.0, wet = true))
        assertFalse(blend(stations)!!.isRaining)
    }

    @Test
    fun `thunder anywhere within 25 km is reported`() {
        val stations = listOf(station("Kuru 1", 1.0), station("Kuru 2", 4.0), station("Fırtına", 22.0, wet = true, thunder = true))
        val result = blend(stations)!!
        assertTrue(result.hasThunder)
        assertFalse(result.isRaining)
        assertEquals("Fırtına", result.nearbyWet!!.stationName)

        val here = blend(listOf(station("Yağışlı", 2.0, wet = true), station("Kuru", 6.0), station("Fırtına", 22.0, wet = true, thunder = true)))!!
        assertTrue(here.isRaining)
        assertEquals(95, here.weatherCode)
    }

    // --- Ekrana işlenmesi ---

    @Test
    fun `the screen shows the network temperature and the nearest station's humidity and wind`() {
        val main = six.first().copy(network = StationNetwork.valid(six, besiktas.latitude, besiktas.longitude, now))
        val blended = ObservationBlend.apply(snapshot(besiktas), main, now)

        val use = blended.observation as ObservationUse.Override
        assertEquals(4, use.network!!.stationCount)
        assertEquals("Yakın", use.observation.stationName)
        val current = blended.forecast.current
        assertEquals(17.05, current.temperature, 0.05)
        // Hissedilen, sıcaklıkla aynı miktarda kayar (18 → 17,05).
        assertEquals(16.05, current.apparentTemperature, 0.05)
        assertEquals(70, current.humidity)
        assertEquals(12.0, current.windSpeed, 0.0)
        assertEquals(2, current.weatherCode)
    }

    @Test
    fun `the stored measurement stays the nearest station's raw reading`() {
        // Hakem ve öğrenen düzeltme saklanan ölçümün sıcaklığını kullanır: model payı karışmış değeri değil.
        val main = six.first().copy(network = StationNetwork.valid(six, besiktas.latitude, besiktas.longitude, now))
        val blended = ObservationBlend.apply(snapshot(besiktas, temperature = 30.0), main, now)
        assertEquals(17.0, main.temperature!!, 0.0)
        assertTrue(blended.forecast.current.temperature > 17.0)
    }

    @Test
    fun `when the network thins out to one station the old single-station rules apply`() {
        val stations = listOf(station("Yakın", 1.2, minutesAgo = 5), station("Eskiyen", 3.0, minutesAgo = 25))
        val main = stations.first().copy(network = stations)
        // Şimdi iki istasyon da taze: ağ.
        assertNotNull((ObservationBlend.apply(snapshot(besiktas), main, now).observation as ObservationUse.Override).network)
        // On dakika sonra biri 35 dakikalık oldu: tek istasyon, eski kurallar (ölçüm doğrudan kullanılır).
        val later = ObservationBlend.apply(snapshot(besiktas), main, now.plusSeconds(600))
        val use = later.observation as ObservationUse.Override
        assertNull(use.network)
        assertEquals(17.0, later.forecast.current.temperature, 0.0)
    }

    @Test
    fun `with no station the model stays as it is`() {
        val model = snapshot(besiktas)
        assertSame(model, ObservationBlend.apply(model, null, now))
        assertNull(blend(emptyList()))
    }

    // --- Türkiye dışı: havalimanı ölçümü (METAR) eskisi gibi ---

    private fun metar(name: String, city: City) = Observation(
        source = ObservationSource.Metar,
        stationName = name,
        latitude = city.latitude + 0.05,
        longitude = city.longitude,
        elevationMeters = 20.0,
        observedAtMillis = now.minusSeconds(600).toEpochMilli(),
        temperature = 12.0,
        humidity = 80,
        windSpeedKmh = 22.0,
        windDirection = 250,
        weatherCode = 61,
        isWet = true,
    )

    @Test
    fun `Liverpool and Limerick keep the single airport measurement`() {
        for ((city, airport) in listOf(City("Liverpool", 53.4084, -2.9916) to "Liverpool John Lennon", City("Limerick", 52.6638, -8.6267) to "Shannon")) {
            val observation = metar(airport, city)
            val model = snapshot(city, temperature = 14.0)
            val blended = ObservationBlend.apply(model, observation, now)

            val use = blended.observation as ObservationUse.Override
            assertNull(use.network)
            assertEquals(airport, use.observation.stationName)
            // Tek istasyonun kuralı: ölçüm sıcaklığı doğrudan belirler, model karışmaz.
            val expected = ObservationPolicy.applyTo(model.forecast.current, ObservationUse.Override(observation, use.distanceKm), model.forecast.elevation)
            assertEquals(expected, blended.forecast.current)
            assertEquals(12.0, blended.forecast.current.temperature, 0.0)
        }
    }

    // --- Yağışı ölçmeyen istasyonlar (ör. deniz feneri)

    /** Telefonda görülen örnek (14 Ekim): şiddetli yağmurda en yakın istasyon bir fenerdi ve yağış ölçmüyordu. */
    private fun lighthouse(km: Double = 1.0) = station("Fener", km, temperature = 16.0, code = null, humidity = 96, knows = false)

    @Test
    fun `a lighthouse that measures no rain does not make heavy rain look dry`() {
        // Fener 1 km'de (sıcaklık var, yağış yok); ilçe istasyonu 3 km'de yağmur ölçüyor (10 dk'da 0,5 mm).
        val result = blend(listOf(lighthouse(), station("İlçe", 3.0, temperature = 15.6, wet = true, code = 63)))!!
        assertTrue(result.isRaining)
        assertEquals(63, result.weatherCode)
        assertTrue(result.knowsPrecipitation)
        assertEquals("İlçe", result.precipitationStation!!.stationName)
        assertEquals(3.0, result.precipitationStationKm!!, 0.05)
        // Sıcaklığa fener katılmaya devam eder; nem ve rüzgâr da en yakın istasyondan (fener).
        assertEquals("Fener", result.nearest.stationName)
        assertEquals(2, result.temperatureStations)
    }

    @Test
    fun `with only stations that measure no rain the network does not say dry`() {
        val result = blend(listOf(lighthouse(1.0), lighthouse(4.0).copy(stationName = "Fener 2")))!!
        assertFalse(result.knowsPrecipitation)
        assertFalse(result.isRaining)
        assertNull(result.weatherCode)
        assertNull(result.precipitationStation)
        assertNull(result.nearbyWet)
    }

    @Test
    fun `stations that measure no rain are not counted on either side of the vote`() {
        // nearWet: 5 km içinde yağış ölçen yok; heavierWet ve majorityWet yalnız bilenlerle hesaplanır.
        // Bilen iki istasyonun ikisi de yağışlı (6 ve 8 km); bilmeyen üç fener 1–2 km'de. Fenerler paydaya girseydi
        // ağırlıkça ve sayıca azınlıkta kalırlardı.
        val stations = listOf(lighthouse(1.0), lighthouse(1.5).copy(stationName = "Fener 2"), lighthouse(2.0).copy(stationName = "Fener 3"),
            station("Yağışlı 1", 6.0, wet = true), station("Yağışlı 2", 8.0, wet = true))
        assertTrue(blend(stations)!!.isRaining)

        // Tersi: yalnız bir bilen istasyon var ve kuru; fenerler onu "azınlık" yapamaz.
        val dry = blend(listOf(lighthouse(1.0), station("Kuru", 9.0)))!!
        assertFalse(dry.isRaining)
        assertEquals(2, dry.weatherCode)
        assertEquals("Kuru", dry.precipitationStation!!.stationName)
    }

    @Test
    fun `a dry station beyond 15 km cannot call the place dry`() {
        val result = blend(listOf(lighthouse(1.0), station("Uzak", 20.0)))!!
        assertFalse(result.knowsPrecipitation)
        assertNull(result.weatherCode)
    }

    /** Modelin anlık kodu "kapalı" (3); MGM'nin o saatlik tahmini [hourCode]. Yerel saat 15:00 (UTC 12:00). */
    private fun withHour(hourCode: Int): WeatherSnapshot {
        val base = snapshot(besiktas, code = 3)
        val hour = HourlyForecast(LocalDateTime.of(2026, 10, 13, 15, 0), 16.0, hourCode, 80, isDay = true)
        return base.copy(forecast = base.forecast.copy(hourly = listOf(hour)))
    }

    @Test
    fun `heavy rain at the district station shows as rain even with a lighthouse nearer`() {
        val stations = listOf(lighthouse(), station("İlçe", 3.0, temperature = 15.6, wet = true, code = 63))
        val blended = ObservationBlend.apply(withHour(3), stations.first().copy(network = stations), now)
        assertEquals(63, blended.forecast.current.weatherCode)
        assertTrue(blended.forecast.current.condition.isWet)
        assertTrue(blended.precipitationMeasured)
    }

    @Test
    fun `with only a lighthouse the weather comes from the forecast, never a measured dry sky`() {
        // Ağ: iki fener. MGM'nin bu saatlik tahmini sağanak (80): simge onu gösterir.
        val pair = listOf(lighthouse(1.0), lighthouse(4.0).copy(stationName = "Fener 2"))
        val rainy = ObservationBlend.apply(withHour(80), pair.first().copy(network = pair), now)
        assertEquals(80, rainy.forecast.current.weatherCode)
        assertFalse(rainy.precipitationMeasured)
        // Tahmin de kuru diyorsa modelin kodu kalır; sıcaklık yine istasyonlardan.
        val cloudy = ObservationBlend.apply(withHour(2), pair.first().copy(network = pair), now)
        assertEquals(3, cloudy.forecast.current.weatherCode)
        assertEquals(16.0, cloudy.forecast.current.temperature, 0.2)

        // Tek fener (ağ yok): eski tek istasyon yolu da aynı kurala uyar.
        val single = ObservationBlend.apply(withHour(95), lighthouse(), now)
        assertEquals(95, single.forecast.current.weatherCode)
        assertEquals(16.0, single.forecast.current.temperature, 0.0)
        assertFalse(single.precipitationMeasured)
    }

    // --- Denizdeki istasyonlar

    @Test
    fun `the referee uses the land station, not the lighthouse`() {
        // Fener 1 km'de, kara istasyonu 3 km'de: hakemin ham ölçümü kara istasyonundan.
        val stations = listOf(lighthouse(1.0), station("İlçe", 3.0, temperature = 15.6))
        assertEquals("İlçe", StationNetwork.referenceStation(stations)!!.stationName)
        // Karada istasyon yoksa fener de olur.
        assertEquals("Fener", StationNetwork.referenceStation(listOf(lighthouse(1.0)))!!.stationName)
    }

    @Test
    fun `sea stations are recognised by their names`() {
        assertTrue(StationNetwork.isAtSea(station("İtü Güney Mendirek Feneri", 1.0)))
        assertTrue(StationNetwork.isAtSea(station("Beşiktaş İskele Feneri", 1.0)))
        assertTrue(StationNetwork.isAtSea(station("Örnek Şamandıra", 1.0)))
        assertFalse(StationNetwork.isAtSea(station("Beşiktaş", 1.0)))
        // Havalimanı raporları hiçbir zaman denizde sayılmaz.
        assertFalse(StationNetwork.isAtSea(station("Fener", 1.0).copy(source = ObservationSource.Metar)))
    }

    @Test
    fun `a sea station counts half in the temperature and takes no dominant share`() {
        // Fener 1,5 km'de 14°, kara istasyonu 3 km'de 18°; model kara istasyonuyla aynı.
        val result = blend(listOf(station("Fener", 1.5, temperature = 14.0), station("Kara", 3.0, temperature = 18.0)), model = 18.0)!!
        // Ağırlıklar: fener 0,5/2,25 = 0,222, kara 1/9 = 0,111, model 0,0044. Fener yarıya inmeseydi ve
        // 2 km kuralıyla %70 alsaydı sonuç 15,2° olurdu.
        val expected = (14.0 * 0.5 / 2.25 + 18.0 / 9 + 18.0 / 225) / (0.5 / 2.25 + 1.0 / 9 + 1.0 / 225)
        assertEquals(expected, result.temperature, 0.01)
    }
}
