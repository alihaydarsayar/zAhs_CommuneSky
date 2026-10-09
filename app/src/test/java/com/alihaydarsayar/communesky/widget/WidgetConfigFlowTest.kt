package com.alihaydarsayar.communesky.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/**
 * Madde 11: Widget ayarı akış olarak okunur; ayar ekranında "Saydam" kaydedilince açık widget
 * oturumu bile yeni arka planı hemen alır.
 */
class WidgetConfigFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store(): WidgetConfigStore {
        val file = File(folder.root, "widgets.preferences_pb")
        return WidgetConfigStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
    }

    @Test
    fun `saving a background reaches an open widget`() = runTest {
        val store = store()
        val seen = mutableListOf<WidgetConfig>()
        val job = launch { store.observe(42).take(3).toList(seen) }
        advanceUntilIdle()
        store.set(42, WidgetConfig(WidgetPlace.Home, WidgetBackground.Translucent))
        advanceUntilIdle()
        store.set(42, WidgetConfig(WidgetPlace.Home, WidgetBackground.Transparent))
        advanceUntilIdle()
        job.join()
        assertEquals(
            listOf(WidgetBackground.Sky, WidgetBackground.Translucent, WidgetBackground.Transparent),
            seen.map { it.background },
        )
    }

    @Test
    fun `each widget keeps its own settings and removal resets them`() = runTest {
        val store = store()
        store.set(1, WidgetConfig(WidgetPlace.Saved(7), WidgetBackground.Transparent))
        store.set(2, WidgetConfig(WidgetPlace.Device, WidgetBackground.Translucent))
        assertEquals(WidgetConfig(WidgetPlace.Saved(7), WidgetBackground.Transparent), store.observe(1).first())
        assertEquals(WidgetBackground.Translucent, store.get(2).background)
        store.remove(intArrayOf(1))
        assertEquals(WidgetConfig(), store.get(1))
    }

    @Test
    fun `widget place follows Home and falls back to my location`() {
        val device = snapshot(0, "Tuzla")
        val atakum = snapshot(3, "Atakum")
        val weather = mapOf(0L to device, 3L to atakum)
        val home = SavedPlace(3, "Atakum", null, 41.33, 36.27, isHome = true)

        assertEquals(atakum, WidgetDataLoader.resolvePlace(WidgetPlace.Home, listOf(home), weather)!!.snapshot)
        assertEquals(device, WidgetDataLoader.resolvePlace(WidgetPlace.Home, listOf(home.copy(isHome = false)), weather)!!.snapshot)
        assertEquals(device, WidgetDataLoader.resolvePlace(WidgetPlace.Saved(99), listOf(home), weather)!!.snapshot)
        assertEquals(true, WidgetDataLoader.resolvePlace(WidgetPlace.Saved(3), listOf(home), weather)!!.isHome)
    }

    private fun snapshot(id: Long, name: String) = WeatherSnapshot(
        placeId = id,
        city = City(name, 41.0, 29.0),
        isCurrentLocation = id == 0L,
        forecast = Forecast(
            current = CurrentWeather(
                time = LocalDateTime.of(2026, 10, 9, 10, 0),
                temperature = 20.0,
                apparentTemperature = 20.0,
                humidity = 60,
                dewPoint = null,
                windSpeed = 5.0,
                windDirection = null,
                weatherCode = 0,
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
    )
}
