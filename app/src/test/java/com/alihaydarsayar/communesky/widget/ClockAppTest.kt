package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hangi uygulama "saat uygulaması" sayılır? */
class ClockAppTest {

    @Test
    fun `known clock apps and clock-like names count`() {
        assertTrue(ClockApp.looksLikeClock("com.google.android.deskclock", "Clock"))
        assertTrue(ClockApp.looksLikeClock("com.coloros.alarmclock", "Saat"))
        assertTrue(ClockApp.looksLikeClock("com.example.vendor.clockpackage", "Clock"))
        assertTrue(ClockApp.looksLikeClock("com.example.vendor.app", "Horloge"))
        assertTrue(ClockApp.looksLikeClock("com.example.vendor.app", "Alarm"))
    }

    @Test
    fun `other apps do not`() {
        assertFalse(ClockApp.looksLikeClock("com.alihaydarsayar.communesky", "Saat"))
        assertFalse(ClockApp.looksLikeClock("com.example.weather", "Saatlik hava"))
        assertFalse(ClockApp.looksLikeClock("com.example.world", "World Clock Wallpapers Pro Max"))
        assertFalse(ClockApp.looksLikeClock("com.android.chrome", "Chrome"))
    }
}
