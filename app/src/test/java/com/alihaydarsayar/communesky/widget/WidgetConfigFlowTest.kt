package com.alihaydarsayar.communesky.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/**
 * Widget ayarı akış olarak okunur; ekleme ekranında kaydedilince açık widget oturumu bile yeni
 * görünümü hemen alır. 1.2'den kalan ayarlar (yer + arka plan) korunur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "widgets.preferences_pb") }

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun `saving a new look reaches an open widget`() = runTest {
        val store = WidgetConfigStore(dataStore())
        val seen = mutableListOf<WidgetConfig>()
        val job = launch { store.observe(42, WidgetKind.Small).take(3).toList(seen) }
        advanceUntilIdle()
        val glass = WidgetStyleId.SmallClassic.defaultStyle()
        store.set(42, WidgetConfig(WidgetPlace.Home, glass.copy(transparency = 30)))
        advanceUntilIdle()
        store.set(42, WidgetConfig(WidgetPlace.Home, glass.copy(colorTheme = ColorTheme.Ocean, textSize = TextSize.Large)))
        advanceUntilIdle()
        job.join()
        assertEquals(WidgetConfig.default(WidgetKind.Small), seen[0])
        assertEquals(30, seen[1].style.transparency)
        assertEquals(ColorTheme.Ocean, seen[2].style.colorTheme)
        assertEquals(TextSize.Large, seen[2].style.textSize)
    }

    @Test
    fun `each widget keeps its own settings and removal resets them`() = runTest {
        val store = WidgetConfigStore(dataStore())
        val clock = WidgetConfig(WidgetPlace.Saved(7), WidgetStyleId.ClockAnalog.defaultStyle().copy(contents = setOf(WidgetContent.Weather)))
        store.set(1, clock)
        store.set(2, WidgetConfig(WidgetPlace.Device, WidgetStyleId.HourlyRow.defaultStyle()))
        assertEquals(clock, store.observe(1, WidgetKind.Clock).first())
        assertEquals(WidgetStyleId.HourlyRow, store.get(2, WidgetKind.Hourly).style.styleId(WidgetKind.Hourly))
        assertTrue(store.exists(1))
        store.remove(intArrayOf(1))
        assertFalse(store.exists(1))
        assertEquals(WidgetConfig.default(WidgetKind.Clock), store.get(1, WidgetKind.Clock))
    }

    @Test
    fun `settings from version 1_2 keep their place and background`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[stringPreferencesKey("widget_5_place")] = "place:9"
            it[stringPreferencesKey("widget_5_background")] = "Translucent"
        }
        val config = WidgetConfigStore(dataStore).get(5, WidgetKind.Small)
        assertEquals(WidgetPlace.Saved(9), config.place)
        assertEquals(BackgroundKind.Glass, config.style.background)
    }

    @Test
    fun `a style of another widget falls back to the first style`() {
        assertEquals(WidgetStyleId.HourlyCurve, WidgetStyle(style = WidgetStyleId.ClockBig.name).styleId(WidgetKind.Hourly))
        assertEquals(WidgetStyleId.ClockBig, WidgetStyle(style = WidgetStyleId.ClockBig.name).styleId(WidgetKind.Clock))
    }

    @Test
    fun `there are 9 widgets with more than 30 styles`() {
        assertEquals(9, WidgetKind.entries.size)
        assertTrue(WidgetStyleId.entries.size > 30)
        WidgetKind.entries.forEach { assertTrue("${it.name} stilsiz", it.styles.isNotEmpty()) }
    }

    @Test
    fun `widget place survives a round trip`() {
        for (place in listOf(WidgetPlace.Smart, WidgetPlace.Device, WidgetPlace.Home, WidgetPlace.Saved(42))) {
            assertEquals(place, WidgetPlace.decode(place.encode()))
        }
        assertEquals(WidgetPlace.Device, WidgetPlace.decode(null))
        assertEquals(WidgetPlace.Device, WidgetPlace.decode("place:abc"))
    }

    @Test
    fun `widget place follows Home and falls back to my location`() {
        val device = snapshot(0, "Tuzla", 40.83)
        val atakum = snapshot(3, "Atakum", 41.33)
        val weather = mapOf(0L to device, 3L to atakum)
        val home = SavedPlace(3, "Atakum", null, 41.33, 29.0, isHome = true)
        fun input(places: List<SavedPlace>) = WidgetInput(AppSettings(), places, weather)

        assertEquals(atakum, WidgetInput.resolvePlace(WidgetPlace.Home, input(listOf(home)))!!.snapshot)
        assertEquals(device, WidgetInput.resolvePlace(WidgetPlace.Home, input(listOf(home.copy(isHome = false))))!!.snapshot)
        assertEquals(device, WidgetInput.resolvePlace(WidgetPlace.Saved(99), input(listOf(home)))!!.snapshot)
        assertEquals(true, WidgetInput.resolvePlace(WidgetPlace.Saved(3), input(listOf(home)))!!.isHome)
        // Akıllı: Tuzla Atakum'dan ~55 km uzakta → bulunduğun yer.
        assertEquals(device, WidgetInput.resolvePlace(WidgetPlace.Smart, input(listOf(home)))!!.snapshot)
    }

    private fun snapshot(id: Long, name: String, latitude: Double) = WeatherSnapshot(
        placeId = id,
        city = City(name, latitude, 29.0),
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
        accuracyMeters = 500f,
    )
}
