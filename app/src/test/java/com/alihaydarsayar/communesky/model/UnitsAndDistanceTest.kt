package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsAndDistanceTest {

    @Test
    fun `temperature conversion`() {
        assertEquals(32.0, TemperatureUnit.Fahrenheit.fromCelsius(0.0), 1e-9)
        assertEquals(212.0, TemperatureUnit.Fahrenheit.fromCelsius(100.0), 1e-9)
        assertEquals(-40.0, TemperatureUnit.Fahrenheit.fromCelsius(-40.0), 1e-9)
        assertEquals(21.5, TemperatureUnit.Celsius.fromCelsius(21.5), 1e-9)
    }

    @Test
    fun `wind conversion`() {
        assertEquals(36.0, WindUnit.KilometersPerHour.fromKmh(36.0), 1e-9)
        assertEquals(10.0, WindUnit.MetersPerSecond.fromKmh(36.0), 1e-9)
        assertEquals(10.0, WindUnit.MilesPerHour.fromKmh(16.09344), 1e-9)
        assertEquals(10.0, WindUnit.Knots.fromKmh(18.52), 1e-9)
    }

    @Test
    fun `distance between Besiktas and Liverpool is about 2747 km`() {
        val km = GeoDistance.kilometers(41.0422, 29.0083, 53.4084, -2.9916)
        assertEquals(2747.0, km, 3.0)
    }

    @Test
    fun `distance to the same point is zero`() {
        assertEquals(0.0, GeoDistance.kilometers(41.33, 36.27, 41.33, 36.27), 1e-9)
    }
}
