package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.data.observation.MetarWeatherCodes
import com.alihaydarsayar.communesky.data.observation.MgmWeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ObservationPolicyTest {

    private val now = Instant.parse("2026-10-08T21:10:00Z")
    private val atakumLat = 41.3322588
    private val atakumLon = 36.2704652

    private fun obs(
        lat: Double = 41.344286,
        lon: Double = 36.255794,
        minutesAgo: Long = 4,
        code: Int? = 95,
        wet: Boolean = true,
        thunder: Boolean = true,
        elevation: Double? = 4.0,
        windDirection: Int? = 245,
        source: ObservationSource = ObservationSource.Mgm,
    ) = Observation(
        source = source,
        stationName = "Samsun Bölge",
        latitude = lat,
        longitude = lon,
        elevationMeters = elevation,
        observedAtMillis = now.minusSeconds(minutesAgo * 60).toEpochMilli(),
        temperature = 18.6,
        humidity = 69,
        windSpeedKmh = 9.4,
        windDirection = windDirection,
        weatherCode = code,
        isWet = wet,
        hasThunder = thunder,
    )

    private val modelCurrent = CurrentWeather(
        time = LocalDateTime.of(2026, 10, 9, 0, 0),
        temperature = 17.0,
        apparentTemperature = 17.8,
        humidity = 90,
        dewPoint = 15.4,
        windSpeed = 7.1,
        windDirection = 100,
        weatherCode = 3,
        isDay = false,
        pressure = 1020.2,
        uvIndex = 0.0,
        visibility = 17780.0,
    )

    /** Atakum, 9 Ekim 00:10 (yerel): model kuru, 15 dakikalık yağış tamamen 0. */
    private fun atakumSnapshot(): WeatherSnapshot {
        val localNow = LocalDateTime.of(2026, 10, 9, 0, 10)
        val slices = (0 until 12).map { i ->
            val end = LocalDateTime.of(2026, 10, 9, 0, 0).plusMinutes(15L * i)
            PrecipitationSlice(end.minusMinutes(15), end, 0.0, isWet = false)
        }
        return WeatherSnapshot(
            placeId = 3,
            city = City("Atakum", atakumLat, atakumLon),
            isCurrentLocation = false,
            forecast = Forecast(
                current = modelCurrent.copy(time = localNow),
                hourly = emptyList(),
                daily = emptyList(),
                utcOffsetSeconds = 10_800,
                minutely = slices,
                elevation = 12.0,
            ),
            fetchedAt = now,
        )
    }

    // --- Madde 1 ---

    @Test
    fun `Atakum - measured thunderstorm with a dry model does not say rain is stopping`() {
        val blended = ObservationBlend.apply(atakumSnapshot(), obs(), now)
        assertTrue(blended.observation is ObservationUse.Override)
        assertEquals(WeatherCondition.Thunderstorm, blended.forecast.current.condition)
        // Önceden: RainOutlook(Stopping, 0) — "yağmur birkaç dakika içinde dinecek".
        assertNull(blended.rainOutlook(LocalDateTime.of(2026, 10, 9, 0, 10)))
        // Ölçüm olmasaydı model kendi verisiyle tutarlı olduğu için uyarı yine yoktu.
        assertNull(atakumSnapshot().rainOutlook(LocalDateTime.of(2026, 10, 9, 0, 10)))
    }

    @Test
    fun `measured dry weather does not say rain is starting now when the model slice is wet`() {
        val snapshot = atakumSnapshot().let { s ->
            s.copy(forecast = s.forecast.copy(minutely = s.forecast.minutely.map { it.copy(precipitationMm = 1.0, isWet = true) }))
        }
        val dry = obs(code = 1, wet = false, thunder = false)
        val blended = ObservationBlend.apply(snapshot, dry, now)
        assertNull(blended.rainOutlook(LocalDateTime.of(2026, 10, 9, 0, 10)))
    }

    @Test
    fun `without a measurement the model outlook works as before`() {
        val snapshot = atakumSnapshot().let { s ->
            val slices = s.forecast.minutely.mapIndexed { i, slice -> if (i >= 3) slice.copy(precipitationMm = 1.0, isWet = true) else slice }
            s.copy(forecast = s.forecast.copy(minutely = slices))
        }
        val outlook = snapshot.rainOutlook(LocalDateTime.of(2026, 10, 9, 0, 10))
        assertEquals(RainOutlook.Kind.Starting, outlook!!.kind)
    }

    // --- Madde 4 ---

    @Test
    fun `a near station decides temperature, sky, humidity, dew point and wind`() {
        val use = ObservationPolicy.evaluate(obs(), atakumLat, atakumLon, now, modelIsWet = false)
        assertTrue(use is ObservationUse.Override)
        val current = ObservationPolicy.applyTo(modelCurrent, use, modelElevation = 12.0)
        assertEquals(18.6, current.temperature, 0.0)
        assertEquals(19.4, current.apparentTemperature, 0.001)
        assertEquals(95, current.weatherCode)
        assertEquals(69, current.humidity)
        // 18,6°C ve %69 nemde çiy noktası ~12,7°C (modelin 15,4°C'si değil).
        assertEquals(12.7, current.dewPoint!!, 0.2)
        assertEquals(9.4, current.windSpeed, 0.0)
        assertEquals(245, current.windDirection)
    }

    @Test
    fun `wind without a direction keeps the model wind`() {
        val use = ObservationPolicy.evaluate(obs(windDirection = null), atakumLat, atakumLon, now, false)
        val current = ObservationPolicy.applyTo(modelCurrent, use, 12.0)
        assertEquals(7.1, current.windSpeed, 0.0)
        assertEquals(100, current.windDirection)
    }

    @Test
    fun `dew point and humidity formulas agree`() {
        val dew = ObservationPolicy.dewPoint(19.0, 83)
        assertEquals(83, ObservationPolicy.relativeHumidity(19.0, dew))
        assertEquals(16.0, ObservationPolicy.dewPoint(19.0, ObservationPolicy.relativeHumidity(19.0, 16.0)), 0.3)
    }

    // --- Madde 5 ---

    @Test
    fun `a measurement disappears by itself after 30 minutes`() {
        val measurement = obs(minutesAgo = 25)
        val snapshot = atakumSnapshot()
        assertTrue(ObservationBlend.apply(snapshot, measurement, now).observation != null)
        val later = now.plusSeconds(6 * 60)
        val blended = ObservationBlend.apply(snapshot, measurement, later)
        assertNull(blended.observation)
        assertEquals(17.0, blended.forecast.current.temperature, 0.0)
        assertEquals(now.plusSeconds(5 * 60), ObservationPolicy.expiresAt(measurement))
    }

    // --- Madde 6 ---

    @Test
    fun `best source is the nearest, then the freshest`() {
        val mgmNear = obs(minutesAgo = 20)
        val metarFar = obs(lat = 41.255, lon = 36.567, minutesAgo = 1, source = ObservationSource.Metar)
        val metarStale = obs(lat = 41.335, lon = 36.27, minutesAgo = 50, source = ObservationSource.Metar)
        assertEquals(mgmNear, ObservationPolicy.chooseBest(listOf(metarFar, metarStale, mgmNear), atakumLat, atakumLon, now))

        val sameDistanceFresher = mgmNear.copy(observedAtMillis = now.minusSeconds(60).toEpochMilli(), source = ObservationSource.Metar)
        assertEquals(sameDistanceFresher, ObservationPolicy.chooseBest(listOf(mgmNear, sameDistanceFresher), atakumLat, atakumLon, now))
        assertNull(ObservationPolicy.chooseBest(listOf(metarStale), atakumLat, atakumLon, now))
    }

    // --- Mesafe ve yaş kuralları ---

    @Test
    fun `a far station only warns about storms`() {
        val far = obs(lat = 41.255, lon = 36.567)
        val use = ObservationPolicy.evaluate(far, atakumLat, atakumLon, now, modelIsWet = false)
        assertTrue(use is ObservationUse.Advisory)
        assertEquals(27.0, use!!.distanceKm, 1.5)
        assertEquals(modelCurrent, ObservationPolicy.applyTo(modelCurrent, use, 12.0))
        assertNull(ObservationPolicy.evaluate(far.copy(isWet = false, hasThunder = false), atakumLat, atakumLon, now, false))
        assertNull(ObservationPolicy.evaluate(far, atakumLat, atakumLon, now, modelIsWet = true))
    }

    @Test
    fun `age, distance and height limits`() {
        assertNull(ObservationPolicy.evaluate(obs(minutesAgo = 31), atakumLat, atakumLon, now, false))
        assertTrue(ObservationPolicy.evaluate(obs(minutesAgo = 29), atakumLat, atakumLon, now, false) != null)
        assertNull(ObservationPolicy.evaluate(obs(lat = 41.0, lon = 36.9), atakumLat, atakumLon, now, false))
        val mountain = ObservationPolicy.evaluate(obs(elevation = 900.0), atakumLat, atakumLon, now, false)
        assertEquals(17.0, ObservationPolicy.applyTo(modelCurrent, mountain, 12.0).temperature, 0.0)
    }

    // --- Madde 2 ve 3 ---

    @Test
    fun `MGM codes match the full MGM list`() {
        assertEquals(95, MgmWeatherCodes.map("GSY").wmoCode)
        assertTrue(MgmWeatherCodes.map("KGY").hasThunder)
        assertEquals(75, MgmWeatherCodes.map("KYK").wmoCode)
        assertEquals(75, MgmWeatherCodes.map("YKY").wmoCode)
        assertEquals(45, MgmWeatherCodes.map("PUS").wmoCode)
        assertNull(MgmWeatherCodes.map("DNM").wmoCode)
        assertTrue(MgmWeatherCodes.map("DNM").isKnown)
        assertTrue(MgmWeatherCodes.map("HHY").isWet)
        for (windy in listOf("R", "GKR", "KKR", "KF", "SCK", "SGK")) {
            assertNull(MgmWeatherCodes.map(windy).wmoCode)
            assertTrue(MgmWeatherCodes.map(windy).isKnown)
        }
        assertFalse(MgmWeatherCodes.map("XYZ").isKnown)
        assertTrue(MgmWeatherCodes.map(null).isKnown)
    }

    @Test
    fun `MGM rain amount overrides a dry code`() {
        // Kod "çok bulutlu" ama son 10 dakikada 0,4 mm: yağmur (2,4 mm/sa).
        val cloudyButRaining = MgmWeatherCodes.map("CB", 0.4, 18.0)
        assertTrue(cloudyButRaining.isWet)
        assertEquals(61, cloudyButRaining.wmoCode)
        // Kod eksik, sağanak şiddetinde yağış (1,5 mm / 10 dk = 9 mm/sa).
        assertEquals(65, MgmWeatherCodes.map(null, 1.5, 18.0).wmoCode)
        // Soğukta kar.
        assertEquals(73, MgmWeatherCodes.map("HHY", 0.3, -1.0).wmoCode)
        // Kod zaten gök gürültülü: kod korunur.
        assertEquals(95, MgmWeatherCodes.map("GSY", 0.4, 18.0).wmoCode)
        // Eser miktar yağış (0,1 mm altı) durumu değiştirmez.
        assertFalse(MgmWeatherCodes.map("CB", 0.05, 18.0).isWet)
    }

    @Test
    fun `METAR weather`() {
        val storm = MetarWeatherCodes.map("-TSRA", "SCT")
        assertEquals(95, storm.wmoCode)
        assertTrue(storm.isWet && storm.hasThunder)
        val recent = MetarWeatherCodes.map("RETSRA", "BKN")
        assertEquals(3, recent.wmoCode)
        assertFalse(recent.isWet || recent.hasThunder)
        assertEquals(80, MetarWeatherCodes.map("-SHRA", "BKN").wmoCode)
        assertEquals(65, MetarWeatherCodes.map("+RA", "OVC").wmoCode)
        assertEquals(45, MetarWeatherCodes.map("FG", null).wmoCode)
        assertEquals(0, MetarWeatherCodes.map(null, "CAVOK").wmoCode)
        assertFalse(MetarWeatherCodes.map("VCSH", "SCT").isWet)
    }
}
