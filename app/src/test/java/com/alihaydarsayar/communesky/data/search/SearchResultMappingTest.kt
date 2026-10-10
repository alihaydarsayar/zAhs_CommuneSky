package com.alihaydarsayar.communesky.data.search

import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photon ve Open-Meteo cevapları, 8 Ekim 2026'da servislerden alınan gerçek cevapların
 * kısaltılmış halleri. Open-Meteo Atakum'u hiç bulamıyor ve İstanbul'daki Tuzla ile Beşiktaş'ı
 * vermiyordu; Photon hepsini buldu.
 */
class SearchResultMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun photon(body: String) = json.decodeFromString(PhotonResponseDto.serializer(), body)
    private fun openMeteo(body: String) = json.decodeFromString(GeocodingResponseDto.serializer(), body)

    private val atakumPhoton = """
        {"features":[
          {"properties":{"osm_value":"town","type":"city","name":"Atakum","state":"Samsun","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[36.2704652,41.3322588]}},
          {"properties":{"osm_value":"village","name":"Ataun","county":"Gipuzkoa","state":"Euskadi","country":"España","countrycode":"ES"},
           "geometry":{"coordinates":[-2.17,43.01]}}
        ]}
    """

    // Photon'un kendi sırası: önce Bosna'daki Tuzla.
    private val tuzlaPhoton = """
        {"features":[
          {"properties":{"osm_value":"city","name":"Tuzla","county":"Grad Tuzla","state":"Federacija Bosne i Hercegovine","country":"Bosna i Hercegovina","countrycode":"BA"},
           "geometry":{"coordinates":[18.6749337,44.539298]}},
          {"properties":{"osm_value":"town","name":"Tuzla","state":"İstanbul","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[29.3034194,40.8161732]}},
          {"properties":{"osm_value":"village","name":"Tuzla","state":"Mardin","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[40.6319857,37.2901075]}}
        ]}
    """

    // Photon böyle sıralarsa: önce aynı adlı bir köy, sonra İstanbul'daki ilçe.
    private val besiktasPhoton = """
        {"features":[
          {"properties":{"osm_value":"village","name":"Beşiktaş","state":"Ankara","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[32.484181,39.472138]}},
          {"properties":{"osm_value":"town","name":"Beşiktaş","state":"İstanbul","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[29.0083,41.0422]}}
        ]}
    """

    private val kurupelitPhoton = """
        {"features":[
          {"properties":{"osm_value":"quarter","type":"locality","name":"Kurupelit","district":"Körfez","city":"Atakum","state":"Samsun","country":"Türkiye","countrycode":"TR"},
           "geometry":{"coordinates":[36.227516,41.3649529]}}
        ]}
    """

    private val tuzlaOpenMeteo = """
        {"results":[
          {"name":"Tuzla","latitude":36.70074,"longitude":35.08726,"country_code":"TR","country":"Türkiye","admin1":"Adana","admin2":"Karataş İlçesi"},
          {"name":"Tuzla","latitude":39.56678,"longitude":26.16735,"country_code":"TR","country":"Türkiye","admin1":"Çanakkale","admin2":"Ayvacık İlçesi"}
        ]}
    """

    @Test
    fun `Atakum is found with its province`() {
        val results = SearchResultMapping.fromPhoton(photon(atakumPhoton), "Atakum", setOf("tr"))
        val first = results.first()
        assertEquals("Atakum", first.name)
        assertEquals("Samsun, Türkiye", first.region)
        assertEquals(41.3322588, first.latitude, 1e-6)
        assertEquals(36.2704652, first.longitude, 1e-6)
    }

    @Test
    fun `Tuzla in Istanbul comes first for a user in Turkey`() {
        val results = SearchResultMapping.fromPhoton(photon(tuzlaPhoton), "Tuzla", setOf("TR"))
        assertEquals("İstanbul, Türkiye", results[0].region)
        assertEquals("Mardin, Türkiye", results[1].region)
        assertEquals("BA", results[2].countryCode)
    }

    @Test
    fun `places near the user come first even if the phone's country is unknown`() {
        // Kullanıcı Beşiktaş'ta; telefonun ülkesi bilinmiyor (ör. yurt dışı SIM ya da emülatör).
        val results = SearchResultMapping.fromPhoton(
            photon(tuzlaPhoton),
            "Tuzla",
            preferredCountries = setOf("US"),
            anchors = listOf(GeoPoint(41.04, 29.01)),
        )
        assertEquals("İstanbul, Türkiye", results[0].region)
    }

    @Test
    fun `Tuzla keeps Photon order when the country is unknown`() {
        val results = SearchResultMapping.fromPhoton(photon(tuzlaPhoton), "Tuzla")
        assertEquals("BA", results[0].countryCode)
        // Bosna'daki Tuzla'nın ilçesi "Grad Tuzla" kendi adını tekrar ettiği için yazılmaz.
        assertEquals("Federacija Bosne i Hercegovine, Bosna i Hercegovina", results[0].region)
    }

    @Test
    fun `the district comes before villages with the same name`() {
        val results = SearchResultMapping.fromPhoton(photon(besiktasPhoton), "besiktas", setOf("TR"))
        assertEquals("İstanbul, Türkiye", results.first().region)
    }

    @Test
    fun `neighborhood shows its district`() {
        val result = SearchResultMapping.fromPhoton(photon(kurupelitPhoton), "Kurupelit").single()
        assertEquals("Atakum, Samsun, Türkiye", result.region)
    }

    @Test
    fun `Open-Meteo admin suffixes are removed`() {
        val results = SearchResultMapping.fromOpenMeteo(openMeteo(tuzlaOpenMeteo))
        assertEquals("Karataş, Adana, Türkiye", results[0].region)
        assertEquals("Ayvacık, Çanakkale, Türkiye", results[1].region)
    }

    @Test
    fun `merge keeps primary order and adds only new places`() {
        val primary = SearchResultMapping.fromPhoton(photon(tuzlaPhoton), "Tuzla", setOf("TR"))
        val secondary = SearchResultMapping.fromOpenMeteo(openMeteo(tuzlaOpenMeteo)) +
            // Photon'daki İstanbul Tuzla'sının GeoNames'teki hali: birkaç km farklı koordinat.
            PlaceSearchResult("Tuzla", "İstanbul, Türkiye", "TR", 40.83, 29.29)
        val merged = SearchResultMapping.merge(primary, secondary)
        assertEquals(primary, merged.take(primary.size))
        assertEquals(primary.size + 2, merged.size)
        assertEquals(1, merged.count { it.region == "İstanbul, Türkiye" })
    }

    @Test
    fun `merge respects the limit`() {
        val many = (1..20).map { PlaceSearchResult("Yer $it", null, null, it.toDouble(), 0.0) }
        assertEquals(10, SearchResultMapping.merge(many, emptyList()).size)
    }

    @Test
    fun `normalize ignores case and Turkish letters`() {
        assertEquals(SearchResultMapping.normalize("Beşiktaş"), SearchResultMapping.normalize("BEŞİKTAŞ"))
        assertEquals(SearchResultMapping.normalize("Beşiktaş"), SearchResultMapping.normalize("besiktas"))
        assertEquals(SearchResultMapping.normalize("Çarşamba"), SearchResultMapping.normalize("Carsamba"))
    }

    @Test
    fun `results without a name or coordinates are skipped`() {
        val body = """
            {"features":[
              {"properties":{"name":""},"geometry":{"coordinates":[1.0,2.0]}},
              {"properties":{"name":"Yer"},"geometry":{"coordinates":[]}},
              {"properties":{"name":"Moda","state":"İstanbul","country":"Türkiye","countrycode":"TR"},"geometry":{"coordinates":[29.0254706,40.9811687]}}
            ]}
        """
        val results = SearchResultMapping.fromPhoton(photon(body), "Moda")
        assertEquals(listOf("Moda"), results.map { it.name })
        assertTrue(results.all { it.region != null })
    }

    @Test
    fun `empty answers give no results`() {
        assertTrue(SearchResultMapping.fromPhoton(photon("{}"), "x").isEmpty())
        assertTrue(SearchResultMapping.fromOpenMeteo(openMeteo("""{"generationtime_ms":0.4}""")).isEmpty())
        assertNull(SearchResultMapping.fromOpenMeteo(openMeteo("""{"results":[{"name":"A","latitude":1,"longitude":2}]}""")).single().region)
    }
}
