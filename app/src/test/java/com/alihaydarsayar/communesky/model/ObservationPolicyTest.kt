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
        minutesAgo: Long = 6,
        code: Int? = 95,
        wet: Boolean = true,
        thunder: Boolean = true,
        elevation: Double? = 4.0,
    ) = Observation(
        source = ObservationSource.Mgm,
        stationName = "Samsun Bölge",
        latitude = lat,
        longitude = lon,
        elevationMeters = elevation,
        observedAtMillis = now.minusSeconds(minutesAgo * 60).toEpochMilli(),
        temperature = 18.6,
        humidity = 69,
        windSpeedKmh = 9.4,
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
        windDirection = 246,
        weatherCode = 3,
        isDay = false,
        pressure = 1020.2,
        uvIndex = 0.0,
        visibility = 17780.0,
    )

    @Test
    fun `a near station decides the current weather`() {
        val use = ObservationPolicy.evaluate(obs(), atakumLat, atakumLon, now, modelIsWet = false)
        assertTrue(use is ObservationUse.Override)
        val current = ObservationPolicy.applyTo(modelCurrent, use, modelElevation = 12.0)
        assertEquals(18.6, current.temperature, 0.0)
        assertEquals(19.4, current.apparentTemperature, 0.001)
        assertEquals(95, current.weatherCode)
        assertEquals(69, current.humidity)
        assertEquals(WeatherCondition.Thunderstorm, current.condition)
    }

    @Test
    fun `a far station only warns about storms`() {
        // Çarşamba Havalimanı, Atakum'dan 27 km.
        val far = obs(lat = 41.255, lon = 36.567)
        val use = ObservationPolicy.evaluate(far, atakumLat, atakumLon, now, modelIsWet = false)
        assertTrue(use is ObservationUse.Advisory)
        assertEquals(27.0, use!!.distanceKm, 1.5)
        // Uyarı anlık durumu değiştirmez.
        assertEquals(modelCurrent, ObservationPolicy.applyTo(modelCurrent, use, 12.0))
    }

    @Test
    fun `a far dry station is not shown`() {
        val far = obs(lat = 41.255, lon = 36.567, code = 2, wet = false, thunder = false)
        assertNull(ObservationPolicy.evaluate(far, atakumLat, atakumLon, now, modelIsWet = false))
    }

    @Test
    fun `no warning when the model already says rain`() {
        val far = obs(lat = 41.255, lon = 36.567)
        assertNull(ObservationPolicy.evaluate(far, atakumLat, atakumLon, now, modelIsWet = true))
    }

    @Test
    fun `measurements older than 30 minutes are ignored`() {
        assertNull(ObservationPolicy.evaluate(obs(minutesAgo = 31), atakumLat, atakumLon, now, false))
        assertTrue(ObservationPolicy.evaluate(obs(minutesAgo = 29), atakumLat, atakumLon, now, false) != null)
    }

    @Test
    fun `stations beyond 40 km are ignored`() {
        assertNull(ObservationPolicy.evaluate(obs(lat = 41.0, lon = 36.9), atakumLat, atakumLon, now, false))
    }

    @Test
    fun `a big height difference keeps the model temperature`() {
        val mountain = obs(elevation = 900.0)
        val use = ObservationPolicy.evaluate(mountain, atakumLat, atakumLon, now, false)
        val current = ObservationPolicy.applyTo(modelCurrent, use, modelElevation = 12.0)
        assertEquals(17.0, current.temperature, 0.0)
        assertEquals(95, current.weatherCode)
    }

    @Test
    fun `MGM codes`() {
        assertEquals(95, MgmWeatherCodes.map("GSY").wmoCode)
        assertTrue(MgmWeatherCodes.map("GSY").hasThunder)
        assertEquals(3, MgmWeatherCodes.map("CB").wmoCode)
        assertFalse(MgmWeatherCodes.map("CB").isWet)
        assertEquals(81, MgmWeatherCodes.map("SY").wmoCode)
        assertNull(MgmWeatherCodes.map("R").wmoCode) // rüzgârlı: durumu değiştirmez
        assertNull(MgmWeatherCodes.map(null).wmoCode)
    }

    @Test
    fun `METAR weather`() {
        val storm = MetarWeatherCodes.map("-TSRA", "SCT")
        assertEquals(95, storm.wmoCode)
        assertTrue(storm.isWet && storm.hasThunder)
        // "Az önce" (RE) şimdiyi anlatmaz: bulut örtüsüne bakılır.
        val recent = MetarWeatherCodes.map("RETSRA", "BKN")
        assertEquals(3, recent.wmoCode)
        assertFalse(recent.isWet || recent.hasThunder)
        assertEquals(80, MetarWeatherCodes.map("-SHRA", "BKN").wmoCode)
        assertEquals(65, MetarWeatherCodes.map("+RA", "OVC").wmoCode)
        assertEquals(45, MetarWeatherCodes.map("FG", null).wmoCode)
        assertEquals(0, MetarWeatherCodes.map(null, "CAVOK").wmoCode)
        // Yakındaki sağanak istasyonda yağış sayılmaz.
        assertFalse(MetarWeatherCodes.map("VCSH", "SCT").isWet)
    }
}
