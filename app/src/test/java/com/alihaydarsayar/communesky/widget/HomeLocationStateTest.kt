package com.alihaydarsayar.communesky.widget

import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.HomeProximity
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * Evde / Yakında / Uzakta kararı: "Evde" sınırı (1 km + en fazla 1 km hata payı, yakınlık
 * mesafesinin yarısını geçmez), sınır mesafeleri, ev ya da konum yokken.
 */
class HomeLocationStateTest {

    private val home = GeoPoint(41.0, 29.0)

    /** [home]'un [km] kilometre kuzeyindeki nokta (1° enlem ≈ 111,195 km). */
    private fun north(km: Double) = GeoPoint(home.latitude + km / 111.19508, home.longitude)

    // --- "Evde" sınırı: 1 km + en fazla 1 km hata payı, yakınlık mesafesinin yarısını geçmez ---

    @Test
    fun `accuracy adds at most 1 km to the home limit`() {
        // Yakınlık mesafesi 5 km: yarısı 2,5 km, yani sadece hata payı sınırı belirler.
        assertEquals(1.3, HomeDetection.homeLimitKm(0.3, 5), 1e-9)
        assertEquals(2.0, HomeDetection.homeLimitKm(2.0, 5), 1e-9)
        assertEquals(2.0, HomeDetection.homeLimitKm(5.0, 5), 1e-9)
    }

    @Test
    fun `home limit never exceeds half the nearby distance`() {
        // Yakınlık mesafesi 2 km: evde sınırı en fazla 1 km, hata payı ne olursa olsun.
        assertEquals(1.0, HomeDetection.homeLimitKm(0.3, 2), 1e-9)
        assertEquals(1.0, HomeDetection.homeLimitKm(2.0, 2), 1e-9)
        assertEquals(1.0, HomeDetection.homeLimitKm(5.0, 2), 1e-9)
    }

    @Test
    fun `nearby is possible at a 5 km nearby distance with any accuracy`() {
        for (accuracy in listOf(300f, 2_000f, 5_000f)) {
            val limit = if (accuracy == 300f) 1.3 else 2.0
            assertEquals("$accuracy m", HomeProximity.AtHome, HomeDetection.classify(north(limit - 0.05), accuracy, home, 5))
            assertEquals("$accuracy m", HomeProximity.Nearby, HomeDetection.classify(north(limit + 0.05), accuracy, home, 5))
            assertEquals("$accuracy m", HomeProximity.Nearby, HomeDetection.classify(north(4.9), accuracy, home, 5))
            assertEquals("$accuracy m", HomeProximity.Away, HomeDetection.classify(north(5.1), accuracy, home, 5))
        }
    }

    @Test
    fun `nearby is possible at a 2 km nearby distance with any accuracy`() {
        for (accuracy in listOf(300f, 2_000f, 5_000f)) {
            assertEquals("$accuracy m", HomeProximity.AtHome, HomeDetection.classify(north(0.95), accuracy, home, 2))
            assertEquals("$accuracy m", HomeProximity.Nearby, HomeDetection.classify(north(1.05), accuracy, home, 2))
            assertEquals("$accuracy m", HomeProximity.Nearby, HomeDetection.classify(north(1.95), accuracy, home, 2))
            assertEquals("$accuracy m", HomeProximity.Away, HomeDetection.classify(north(2.05), accuracy, home, 2))
        }
    }

    @Test
    fun `Tuzla Merkez 3 km away is nearby even with a coarse location`() {
        assertEquals(HomeProximity.Nearby, HomeDetection.classify(north(3.0), accuracyMeters = 5_000f, home = home))
        assertEquals(HomeProximity.Nearby, HomeDetection.classify(north(3.0), accuracyMeters = null, home = home))
    }

    // --- Sınır mesafeleri ---

    @Test
    fun `nearby up to the nearby distance, away beyond it`() {
        assertEquals(HomeProximity.Nearby, HomeDetection.classifyDistance(5.0, 0.1, 5))
        assertEquals(HomeProximity.Away, HomeDetection.classifyDistance(5.01, 0.1, 5))
        assertEquals(HomeProximity.Nearby, HomeDetection.classifyDistance(19.9, 0.1, 20))
        assertEquals(HomeProximity.Away, HomeDetection.classifyDistance(2.5, 0.1, 2))
    }

    @Test
    fun `nearby distance is kept between 2 and 20 km`() {
        assertEquals(HomeProximity.Nearby, HomeDetection.classifyDistance(1.9, 0.0, 0))
        assertEquals(HomeProximity.Away, HomeDetection.classifyDistance(25.0, 0.0, 99))
    }

    @Test
    fun `unknown accuracy counts as coarse, capped at 1 km`() {
        assertEquals(HomeProximity.AtHome, HomeDetection.classify(north(1.95), accuracyMeters = null, home = home))
        assertEquals(HomeProximity.Nearby, HomeDetection.classify(north(2.05), accuracyMeters = null, home = home))
    }

    @Test
    fun `the user's nearby distance decides between nearby and away`() {
        val device = north(9.0)
        assertEquals(HomeProximity.Away, HomeDetection.classify(device, 100f, home, nearbyRadiusKm = 5))
        assertEquals(HomeProximity.Nearby, HomeDetection.classify(device, 100f, home, nearbyRadiusKm = 10))
        assertEquals(HomeProximity.Away, HomeDetection.classify(GeoPoint(41.33, 36.27), 50_000f, home, nearbyRadiusKm = 20))
    }

    // --- Widget durumu ---

    @Test
    fun `widget state follows the three cases`() {
        val homeData = WidgetPlaceData(snapshot(home, isCurrentLocation = false, placeId = 3), isHome = true)

        val atHome = HomeLocationState.of(snapshot(north(0.5), true, accuracy = 300f), homeData, 5)
        assertTrue(atHome is HomeLocationState.AtHome)

        val nearby = HomeLocationState.of(snapshot(north(3.0), true, accuracy = 300f), homeData, 5)
        assertTrue(nearby is HomeLocationState.Nearby)
        assertEquals(3.0, (nearby as HomeLocationState.Nearby).distanceKm, 0.05)

        val awayDevice = snapshot(north(9.0), true, accuracy = 300f)
        val away = HomeLocationState.of(awayDevice, homeData, 5) as HomeLocationState.Away
        assertEquals(awayDevice, away.here.snapshot)
        assertEquals(homeData, away.home)
    }

    @Test
    fun `without home the current place is shown with a set-home hint`() {
        val here = snapshot(north(9.0), isCurrentLocation = true)
        assertEquals(
            HomeLocationState.Single(WidgetPlaceData(here, false), HomeLocationState.Hint.NoHome),
            HomeLocationState.of(here, null, 5),
        )
    }

    @Test
    fun `without location permission home is shown alone`() {
        val homeData = WidgetPlaceData(snapshot(home, isCurrentLocation = false, placeId = 3), isHome = true)
        // İzin yokken cihaz satırı yedek şehirdir; konum sayılmaz.
        val fallbackCity = snapshot(GeoPoint(41.0082, 28.9784), isCurrentLocation = false)
        assertEquals(HomeLocationState.Single(homeData, HomeLocationState.Hint.NoLocation), HomeLocationState.of(fallbackCity, homeData, 5))
        assertEquals(HomeLocationState.Single(homeData, HomeLocationState.Hint.NoLocation), HomeLocationState.of(null, homeData, 5))
    }

    @Test
    fun `without home and permission the fallback city is shown`() {
        val fallbackCity = snapshot(GeoPoint(41.0082, 28.9784), isCurrentLocation = false)
        assertEquals(
            HomeLocationState.Single(WidgetPlaceData(fallbackCity, false), HomeLocationState.Hint.NoHome),
            HomeLocationState.of(fallbackCity, null, 5),
        )
        assertEquals(HomeLocationState.Empty, HomeLocationState.of(null, null, 5))
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
                time = LocalDateTime.of(2026, 10, 9, 12, 0),
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
