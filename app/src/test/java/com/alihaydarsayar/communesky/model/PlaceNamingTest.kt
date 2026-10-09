package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Madde 10: "Bulunduğum yer" mahalle düzeyinde doğru görünsün; dışarı giden koordinat yuvarlansın. */
class PlaceNamingTest {

    @Test
    fun `precise location in Yayla shows the neighbourhood and the district`() {
        // Google Geocoder'ın Tuzla, Yayla için verdiği alanlar.
        val name = PlaceNaming.fromAddress(
            subLocality = "Yayla Mahallesi",
            locality = "Tuzla",
            subAdminArea = "Tuzla",
            adminArea = "İstanbul",
            precise = true,
        )
        assertEquals(PlaceName("Yayla", "Tuzla"), name)
    }

    @Test
    fun `approximate location shows only the district`() {
        val name = PlaceNaming.fromAddress("Yayla Mahallesi", "Tuzla", "Tuzla", "İstanbul", precise = false)
        assertEquals(PlaceName("Tuzla", null), name)
    }

    @Test
    fun `missing neighbourhood falls back to the district`() {
        assertEquals(PlaceName("Atakum", null), PlaceNaming.fromAddress(null, "Atakum", "Atakum", "Samsun", precise = true))
        assertEquals(PlaceName("Samsun", null), PlaceNaming.fromAddress(null, null, null, "Samsun", precise = true))
        assertEquals(PlaceName(null, null), PlaceNaming.fromAddress(null, null, null, null, precise = true))
    }

    @Test
    fun `neighbourhood equal to the district is not repeated`() {
        assertEquals(PlaceName("Tuzla", null), PlaceNaming.fromAddress("Tuzla", "Tuzla", "Tuzla", "İstanbul", precise = true))
    }

    @Test
    fun `neighbourhood suffixes are removed`() {
        assertEquals("Kurupelit", PlaceNaming.cleanNeighbourhood("Kurupelit Mah."))
        assertEquals("Moda", PlaceNaming.cleanNeighbourhood("Moda Mahallesi"))
        assertEquals("Caferağa", PlaceNaming.cleanNeighbourhood("Caferağa Mh."))
    }

    @Test
    fun `coordinates leaving the phone are rounded to about 1 km`() {
        assertEquals(40.83, CoordinatePrivacy.round(40.8306571), 0.0)
        assertEquals(29.31, CoordinatePrivacy.round(29.3110728), 0.0)
        assertEquals(-0.13, CoordinatePrivacy.round(-0.1276), 0.0)
        // Yuvarlama en fazla ~0,8 km kaydırır.
        assertEquals(0.0, GeoDistance.kilometers(40.8306571, 29.3110728, 40.83, 29.31), 0.8)
    }
}
