package com.alihaydarsayar.communesky.widget

import com.alihaydarsayar.communesky.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

/** Widget'lar yalnızca gösterdikleri şey değişince yeniden çizilir. */
class WidgetDigestTest {

    private val now = Instant.parse("2026-10-10T12:05:00Z")
    private fun input() = WidgetSamples.input("Besiktas", now)
    private fun digest(input: WidgetInput = input(), configs: String = "c", environment: String = "e", at: Instant = now) =
        WidgetDigest.compute(input, configs, environment, at)

    @Test
    fun `the same content gives the same digest`() {
        assertEquals(digest(), digest())
    }

    @Test
    fun `downloading the same weather again does not redraw`() {
        val again = input().let { it.copy(weather = it.weather.mapValues { (_, snapshot) -> snapshot.copy(fetchedAt = now.plusSeconds(120)) }) }
        assertEquals(digest(), digest(again))
    }

    @Test
    fun `new weather redraws`() {
        val base = input()
        val id = base.weather.keys.first()
        val snapshot = base.weather.getValue(id)
        val current = snapshot.forecast.current
        val warmer = snapshot.copy(forecast = snapshot.forecast.copy(current = current.copy(temperature = current.temperature + 1)))
        assertNotEquals(digest(base), digest(base.copy(weather = base.weather + (id to warmer))))
    }

    @Test
    fun `units places widget settings and the phone setup redraw`() {
        val base = input()
        assertNotEquals(digest(base), digest(base.copy(settings = base.settings.copy(temperatureUnit = TemperatureUnit.Fahrenheit))))
        assertNotEquals(digest(base), digest(base.copy(places = base.places.map { it.copy(name = it.name + "x") })))
        assertNotEquals(digest(), digest(configs = "other"))
        assertNotEquals(digest(), digest(environment = "other"))
    }

    @Test
    fun `time based texts are refreshed after a quarter of an hour`() {
        // 12:05 ile 12:14 aynı dilimde; 12:15 yeni dilim.
        assertEquals(digest(), digest(at = now.plusSeconds(9 * 60)))
        assertNotEquals(digest(), digest(at = now.plusSeconds(10 * 60)))
    }
}
