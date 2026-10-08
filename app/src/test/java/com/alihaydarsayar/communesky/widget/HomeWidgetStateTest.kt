package com.alihaydarsayar.communesky.widget

import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class HomeWidgetStateTest {

    private val atakum = GeoPoint(41.3322588, 36.2704652)

    @Test
    fun `at home when within the radius plus the location's error`() {
        // 2,5 km uzakta, hata payı 2 km: 1 + 2 = 3 km içinde → evde.
        val device = GeoPoint(41.3547, 36.2704652)
        assertTrue(HomeDetection.isAtHome(device, accuracyMeters = 2_000f, home = atakum))
    }

    @Test
    fun `not at home when clearly farther than the error`() {
        // 2,5 km uzakta ama konum çok kesin (100 m): evde değil.
        val device = GeoPoint(41.3547, 36.2704652)
        assertFalse(HomeDetection.isAtHome(device, accuracyMeters = 100f, home = atakum))
    }

    @Test
    fun `unknown accuracy assumes coarse location`() {
        val device = GeoPoint(41.3547, 36.2704652) // ~2,5 km
        assertTrue(HomeDetection.isAtHome(device, accuracyMeters = null, home = atakum))
    }

    @Test
    fun `huge accuracy is capped so another city is never home`() {
        // Kadıköy, Atakum'dan ~600 km; hata payı 50 km bile olsa evde sayılmaz.
        assertFalse(HomeDetection.isAtHome(GeoPoint(40.99, 29.02), accuracyMeters = 50_000f, home = atakum))
        // Atakum merkezinden 8 km: hata payı en fazla 5 km sayıldığı için evde değil.
        assertFalse(HomeDetection.isAtHome(GeoPoint(41.4042, 36.2704652), accuracyMeters = 50_000f, home = atakum))
    }

    @Test
    fun `widget shows one line at home and two lines away`() {
        val home = WidgetPlaceData(snapshot(atakum, isCurrentLocation = false, placeId = 3), isHome = true)
        val nearHome = snapshot(GeoPoint(41.34, 36.27), isCurrentLocation = true, accuracy = 1_500f)
        val inIstanbul = snapshot(GeoPoint(40.99, 29.02), isCurrentLocation = true, accuracy = 1_500f)

        assertTrue(HomeWidgetState.of(nearHome, home) is HomeWidgetState.AtHome)
        val away = HomeWidgetState.of(inIstanbul, home) as HomeWidgetState.Away
        assertEquals(inIstanbul, away.here.snapshot)
        assertEquals(home, away.home)
    }

    @Test
    fun `without home or location a single line is shown`() {
        val home = WidgetPlaceData(snapshot(atakum, isCurrentLocation = false, placeId = 3), isHome = true)
        val here = snapshot(GeoPoint(40.99, 29.02), isCurrentLocation = true)
        val fallbackCity = snapshot(GeoPoint(41.0, 28.97), isCurrentLocation = false)

        assertEquals(HomeWidgetState.Single(home, noHome = false), HomeWidgetState.of(null, home))
        // İzin yokken cihaz satırı yedek şehirdir; konum sayılmaz.
        assertEquals(HomeWidgetState.Single(home, noHome = false), HomeWidgetState.of(fallbackCity, home))
        assertEquals(HomeWidgetState.Single(WidgetPlaceData(here, false), noHome = true), HomeWidgetState.of(here, null))
        assertEquals(HomeWidgetState.Empty, HomeWidgetState.of(null, null))
    }

    @Test
    fun `widget place survives a round trip`() {
        for (place in listOf(WidgetPlace.Device, WidgetPlace.Home, WidgetPlace.Saved(42))) {
            assertEquals(place, WidgetPlace.decode(place.encode()))
        }
        assertEquals(WidgetPlace.Device, WidgetPlace.decode(null))
        assertEquals(WidgetPlace.Device, WidgetPlace.decode("place:abc"))
    }

    private fun snapshot(
        point: GeoPoint,
        isCurrentLocation: Boolean,
        placeId: Long = 0,
        accuracy: Float? = null,
    ) = WeatherSnapshot(
        placeId = placeId,
        city = City("Yer", point.latitude, point.longitude),
        isCurrentLocation = isCurrentLocation,
        forecast = Forecast(
            current = CurrentWeather(
                time = LocalDateTime.of(2026, 10, 8, 12, 0),
                temperature = 18.0,
                apparentTemperature = 18.0,
                humidity = 70,
                dewPoint = null,
                windSpeed = 10.0,
                windDirection = null,
                weatherCode = 1,
                isDay = true,
                pressure = null,
                uvIndex = null,
                visibility = null,
            ),
            hourly = emptyList(),
            daily = emptyList(),
            utcOffsetSeconds = 10_800,
        ),
        fetchedAt = Instant.EPOCH,
        accuracyMeters = accuracy,
    )
}
