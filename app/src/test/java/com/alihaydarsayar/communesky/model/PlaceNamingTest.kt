package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Madde 10: "Bulunduğum yer" mahalle düzeyinde doğru görünsün; dışarı giden koordinat yuvarlansın. */
class PlaceNamingTest {

    @Test
    fun `precise location shows the neighbourhood and the district`() {
        // Geocoder'ın bir mahalle için verdiği alanlar: mahalle, ilçe, il.
        val name = PlaceNaming.fromAddress(
            subLocality = "Merkez Mahallesi",
            locality = "Beşiktaş",
            subAdminArea = "Beşiktaş",
            adminArea = "İstanbul",
            precise = true,
        )
        assertEquals(PlaceName("Merkez", "Beşiktaş"), name)
    }

    @Test
    fun `approximate location shows only the district`() {
        val name = PlaceNaming.fromAddress("Merkez Mahallesi", "Beşiktaş", "Beşiktaş", "İstanbul", precise = false)
        assertEquals(PlaceName("Beşiktaş", null), name)
    }

    @Test
    fun `missing neighbourhood falls back to the district`() {
        assertEquals(PlaceName("Atakum", null), PlaceNaming.fromAddress(null, "Atakum", "Atakum", "Samsun", precise = true))
        assertEquals(PlaceName("Samsun", null), PlaceNaming.fromAddress(null, null, null, "Samsun", precise = true))
        assertEquals(PlaceName(null, null), PlaceNaming.fromAddress(null, null, null, null, precise = true))
    }

    @Test
    fun `outside Türkiye the town wins over the county or department`() {
        // İngiltere: ilçe değil şehir adı ("Merseyside" bir kontluktur). Fransa: "Hauts-de-Seine" bir departmandır.
        assertEquals(PlaceName("Liverpool", null), PlaceNaming.fromAddress(null, "Liverpool", "Merseyside", "England", precise = true, countryCode = "GB"))
        assertEquals(PlaceName("Nanterre", null), PlaceNaming.fromAddress(null, "Nanterre", "Hauts-de-Seine", "Île-de-France", precise = true, countryCode = "FR"))
        // Şehir adı verilmediyse kontluk/departman, o da yoksa bölge.
        assertEquals(PlaceName("Merseyside", null), PlaceNaming.fromAddress(null, null, "Merseyside", "England", precise = true, countryCode = "GB"))
        assertEquals(PlaceName("England", null), PlaceNaming.fromAddress(null, null, null, "England", precise = true, countryCode = "GB"))
        // Türkiye: ilçe (subAdminArea) önce, il adı değil.
        assertEquals(PlaceName("Beşiktaş", null), PlaceNaming.fromAddress(null, "İstanbul", "Beşiktaş", "İstanbul", precise = true, countryCode = "TR"))
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
        assertEquals(41.04, CoordinatePrivacy.round(41.0422), 0.0)
        assertEquals(29.01, CoordinatePrivacy.round(29.0083), 0.0)
        assertEquals(-0.13, CoordinatePrivacy.round(-0.1276), 0.0)
        // Yuvarlama en fazla ~0,8 km kaydırır.
        assertEquals(0.0, GeoDistance.kilometers(41.0422, 29.0083, 41.04, 29.01), 0.8)
    }
}
