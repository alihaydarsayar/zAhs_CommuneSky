package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Şu anki konumumu ev olarak kaydet": aynı yer ikinci kez eklenmez. Örnek yerler: Beşiktaş, Liverpool. */
class SavedPlaceAtTest {

    private val besiktas = SavedPlace(1, "Beşiktaş", "İstanbul", 41.0430, 29.0090, isHome = false)
    private val liverpool = SavedPlace(2, "Liverpool", "England", 53.4084, -2.9916, isHome = false)

    @Test
    fun `a saved place within a kilometre is reused`() {
        // ~500 m kuzeyde.
        assertEquals(besiktas, HomeDetection.savedPlaceAt(listOf(liverpool, besiktas), GeoPoint(41.0475, 29.0090)))
    }

    @Test
    fun `a farther place is not reused`() {
        // ~2 km kuzeyde.
        assertNull(HomeDetection.savedPlaceAt(listOf(liverpool, besiktas), GeoPoint(41.0610, 29.0090)))
        assertNull(HomeDetection.savedPlaceAt(emptyList(), GeoPoint(41.0430, 29.0090)))
    }
}
