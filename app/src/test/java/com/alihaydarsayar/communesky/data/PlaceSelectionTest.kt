package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSelectionTest {

    private val atakum = SavedPlace(5, "Atakum", "Samsun, Türkiye", 41.33, 36.27, isHome = true)
    private val tuzla = SavedPlace(2, "Tuzla", "İstanbul, Türkiye", 40.82, 29.30, isHome = false)

    @Test
    fun `with permission my location is always first`() {
        assertEquals(listOf(DEVICE_PLACE_ID, 5L, 2L), PlaceSelection.pageIds(true, listOf(atakum, tuzla)))
        assertEquals(listOf(DEVICE_PLACE_ID), PlaceSelection.pageIds(true, emptyList()))
    }

    @Test
    fun `without permission only saved places are shown`() {
        assertEquals(listOf(5L, 2L), PlaceSelection.pageIds(false, listOf(atakum, tuzla)))
    }

    @Test
    fun `without permission and without places the fallback city is shown`() {
        assertEquals(listOf(DEVICE_PLACE_ID), PlaceSelection.pageIds(false, emptyList()))
    }
}
