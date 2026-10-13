package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * Kısa vade notu: Türkiye'de 15 dakikalık yağış saatlikten türetildiği için not dakika ve saat vermez;
 * Liverpool ve Limerick'te eskisi gibi kesin konuşur.
 */
class NowcastPrecisionTest {

    private val besiktas = City("Beşiktaş", 41.0422, 29.0083)
    private val liverpool = City("Liverpool", 53.4084, -2.9916)
    private val limerick = City("Limerick", 52.6638, -8.6267)
    private val instant = Instant.parse("2026-10-13T16:33:00Z")

    /** Şu an kuru; [wetFrom]. dilimden (15 dk) itibaren yağış. Saatler yerin kendi saatiyle 19:30'dan başlar. */
    private fun snapshot(city: City, wetFrom: Int? = 3, code: Int = 3, observation: ObservationUse? = null): WeatherSnapshot {
        val start = LocalDateTime.of(2026, 10, 13, 19, 30)
        val slices = (0 until 12).map { i ->
            val from = start.plusMinutes(15L * i)
            val wet = wetFrom != null && i >= wetFrom
            PrecipitationSlice(from, from.plusMinutes(15), if (wet) 0.6 else 0.0, isWet = wet)
        }
        return WeatherSnapshot(
            placeId = 5,
            city = city,
            isCurrentLocation = false,
            forecast = Forecast(
                current = CurrentWeather(
                    time = start, temperature = 16.0, apparentTemperature = 15.0, humidity = 80, dewPoint = 12.0, windSpeed = 10.0,
                    windDirection = 30, weatherCode = code, isDay = false, pressure = 1014.0, uvIndex = 0.0, visibility = 10_000.0,
                ),
                hourly = emptyList(),
                daily = emptyList(),
                utcOffsetSeconds = 10_800,
                minutely = slices,
            ),
            fetchedAt = instant,
            observation = observation,
        )
    }

    private val now = LocalDateTime.of(2026, 10, 13, 19, 33)

    private fun station(wet: Boolean, km: Double = 2.0) = Observation(
        source = ObservationSource.Mgm,
        stationName = "Örnek",
        latitude = besiktas.latitude + km / 111.19,
        longitude = besiktas.longitude,
        observedAtMillis = instant.toEpochMilli(),
        temperature = 16.0,
        weatherCode = if (wet) 61 else 3,
        isWet = wet,
    )

    @Test
    fun `in Türkiye the note speaks roughly, elsewhere it keeps its minutes`() {
        assertTrue(NowcastPrecision.isRough(snapshot(besiktas)))
        assertFalse(NowcastPrecision.isRough(snapshot(liverpool)))
        assertFalse(NowcastPrecision.isRough(snapshot(limerick)))
        // Fransa (Paris) da değişmez.
        assertFalse(NowcastPrecision.isRough(snapshot(City("Paris", 48.8566, 2.3522))))
    }

    @Test
    fun `the home screen note gives no minutes in Beşiktaş`() {
        val outlook = snapshot(besiktas).rainOutlook(now)!!
        assertEquals(RainOutlook.Kind.Starting, outlook.kind)
        assertTrue(outlook.isRough)
        // Model 20:15 diyor (42 dk sonra): "1 saat içinde".
        assertEquals(RoughSpan.WithinAnHour, outlook.roughSpan)

        val liverpoolOutlook = snapshot(liverpool).rainOutlook(now)!!
        assertFalse(liverpoolOutlook.isRough)
        assertEquals(45, liverpoolOutlook.minutes)
    }

    @Test
    fun `spans`() {
        assertEquals(RoughSpan.Soon, NowcastPrecision.span(0))
        assertEquals(RoughSpan.Soon, NowcastPrecision.span(30))
        assertEquals(RoughSpan.WithinAnHour, NowcastPrecision.span(31))
        assertEquals(RoughSpan.WithinAnHour, NowcastPrecision.span(60))
        assertEquals(RoughSpan.WithinTwoHours, NowcastPrecision.span(61))
        assertEquals(RoughSpan.WithinTwoHours, NowcastPrecision.span(120))
    }

    @Test
    fun `when stations around measure rain the exact wording stays`() {
        // 10–40 km'deki istasyon yağış ölçüyor (uyarı notu).
        val advisory = ObservationUse.Advisory(station(wet = true, km = 20.0), 20.0)
        assertFalse(NowcastPrecision.isRough(snapshot(besiktas, observation = advisory)))

        // İstasyon ağı: yerde kuru, çevredeki bir istasyon yağışlı.
        val network = StationNetwork.Result(
            temperature = 16.0, isRaining = false, hasThunder = false, stationCount = 3, temperatureStations = 3,
            nearest = station(wet = false), nearestKm = 2.0, weatherCode = 3, nearbyWet = station(wet = true, km = 9.0),
        )
        assertFalse(NowcastPrecision.isRough(snapshot(besiktas, observation = ObservationUse.Override(station(wet = false), 2.0, network))))

        // Yakın istasyon kuru ölçüyor ve çevrede yağış yok: yuvarlak dil.
        assertTrue(NowcastPrecision.isRough(snapshot(besiktas, observation = ObservationUse.Override(station(wet = false), 2.0))))
        assertTrue(NowcastPrecision.isRough(snapshot(besiktas, observation = ObservationUse.Override(station(wet = false), 2.0, network.copy(nearbyWet = null)))))
    }

    @Test
    fun `the widget's rain start is rounded to the hour`() {
        fun start(hour: Int, minute: Int) = RainStart(LocalDateTime.of(2026, 10, 13, hour, minute), isSnow = false, isNow = false)

        // 20:15 → "20:00 civarı".
        val rounded = NowcastPrecision.roughen(start(20, 15), now)
        assertEquals(LocalDateTime.of(2026, 10, 13, 20, 0), rounded.time)
        assertTrue(rounded.isRough)
        assertFalse(rounded.isSoon)
        // 21:45 → 22:00.
        assertEquals(LocalDateTime.of(2026, 10, 13, 22, 0), NowcastPrecision.roughen(start(21, 45), now).time)
        // 19:45 (12 dk sonra): "birazdan"; saat olarak bir sonraki saat başı.
        val soon = NowcastPrecision.roughen(start(19, 45), now)
        assertTrue(soon.isSoon)
        assertEquals(LocalDateTime.of(2026, 10, 13, 20, 0), soon.time)
        // Şu an yağıyorsa dokunulmaz.
        val raining = RainStart(now, isSnow = false, isNow = true)
        assertEquals(raining, NowcastPrecision.roughen(raining, now))
    }

    @Test
    fun `the rounded hour is never in the past`() {
        // 19:50'de, 20:25 için: 20:00'ye yuvarlanır (gelecekte). 20:05'te, 20:40 için: 21:00.
        val at1950 = LocalDateTime.of(2026, 10, 13, 19, 50)
        val later = NowcastPrecision.roughen(RainStart(LocalDateTime.of(2026, 10, 13, 20, 25), false, false), at1950)
        assertEquals(LocalDateTime.of(2026, 10, 13, 20, 0), later.time)
        val at2005 = LocalDateTime.of(2026, 10, 13, 20, 5)
        val next = NowcastPrecision.roughen(RainStart(LocalDateTime.of(2026, 10, 13, 20, 40), false, false), at2005)
        assertEquals(LocalDateTime.of(2026, 10, 13, 21, 0), next.time)
        // 20:05'te, 20:10 için: yuvarlanmış saat (20:00) geçmişte kalırdı; "birazdan" ve 21:00.
        val past = NowcastPrecision.roughen(RainStart(LocalDateTime.of(2026, 10, 13, 20, 10), false, false), at2005)
        assertTrue(past.isSoon)
        assertEquals(LocalDateTime.of(2026, 10, 13, 21, 0), past.time)
    }
}
