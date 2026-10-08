package com.alihaydarsayar.communesky.data.search

import com.alihaydarsayar.communesky.model.PlaceSearchResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.Locale

/** Photon hata verir, sınır koyar ya da cevap vermezse aramanın yedeklere düştüğünü doğrular. */
class PlaceSearchFallbackTest {

    private val locale = Locale.forLanguageTag("tr-TR")

    private val atakumPhoton = PhotonResponseDto(
        listOf(
            PhotonFeatureDto(
                PhotonPropertiesDto(name = "Atakum", state = "Samsun", country = "Türkiye", countryCode = "TR", osmValue = "town"),
                PhotonGeometryDto(listOf(36.2704652, 41.3322588)),
            ),
        ),
    )
    private val tuzlaMeteo = GeocodingResponseDto(
        listOf(GeocodingResultDto("Tuzla", 40.8163, 29.3033, "TR", "Türkiye", "İstanbul", "Tuzla")),
    )
    private val tuzlaGeocoder = listOf(PlaceSearchResult("Tuzla", "İstanbul, Türkiye", "TR", 40.82, 29.30))

    private class FakePhoton(var answer: suspend () -> PhotonResponseDto) : PhotonApi {
        var calls = 0
        override suspend fun search(
            query: String,
            language: String,
            limit: Int,
            osmTag: String,
            layers: List<String>,
        ): PhotonResponseDto {
            calls++
            return answer()
        }
    }

    private class FakeMeteo(var answer: suspend () -> GeocodingResponseDto) : OpenMeteoGeocodingApi {
        override suspend fun search(name: String, language: String, count: Int, format: String) = answer()
    }

    private class FakeGeocoder(var answer: List<PlaceSearchResult>?) : FallbackGeocoder {
        var calls = 0
        override suspend fun search(query: String, locale: Locale): List<PlaceSearchResult>? {
            calls++
            return answer
        }
    }

    private fun repository(photon: FakePhoton, meteo: FakeMeteo, geocoder: FakeGeocoder) =
        PlaceSearchRepository({ photon }, { meteo }, geocoder, { setOf("TR") })

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `Photon results come first when everything works`() = runTest {
        val repo = repository(FakePhoton { atakumPhoton }, FakeMeteo { tuzlaMeteo }, FakeGeocoder(null))
        val found = repo.search("Atakum", locale) as SearchOutcome.Found
        assertEquals("Atakum", found.results.first().name)
    }

    @Test
    fun `Photon network error falls back to Open-Meteo`() = runTest {
        val geocoder = FakeGeocoder(tuzlaGeocoder)
        val repo = repository(FakePhoton { throw IOException("no route") }, FakeMeteo { tuzlaMeteo }, geocoder)
        val found = repo.search("Tuzla", locale) as SearchOutcome.Found
        assertEquals(listOf("Tuzla"), found.results.map { it.name })
        assertEquals("İstanbul, Türkiye", found.results.single().region)
        assertEquals(0, geocoder.calls)
    }

    @Test
    fun `Photon rate limit pauses Photon for a while`() = runTest {
        val photon = FakePhoton { throw httpError(429) }
        val repo = repository(photon, FakeMeteo { tuzlaMeteo }, FakeGeocoder(null))
        var now = 1_000_000L
        repo.clock = { now }

        assertTrue(repo.search("Tuzla", locale) is SearchOutcome.Found)
        assertEquals(1, photon.calls)
        // Sınır sonrası 10 dakika Photon'a hiç sorulmaz; Open-Meteo sonuç vermeye devam eder.
        photon.answer = { atakumPhoton }
        now += 5 * 60 * 1000L
        assertEquals("Tuzla", (repo.search("Tuzla", locale) as SearchOutcome.Found).results.single().name)
        assertEquals(1, photon.calls)
        // Süre dolunca yeniden sorulur.
        now += 6 * 60 * 1000L
        repo.search("Atakum", locale)
        assertEquals(2, photon.calls)
    }

    @Test
    fun `Photon timeout does not block Open-Meteo results`() = runTest {
        val repo = repository(FakePhoton { awaitCancellation() }, FakeMeteo { tuzlaMeteo }, FakeGeocoder(null))
        val found = repo.search("Tuzla", locale) as SearchOutcome.Found
        assertEquals("Tuzla", found.results.single().name)
    }

    @Test
    fun `both online services failing falls back to Android Geocoder`() = runTest {
        val geocoder = FakeGeocoder(tuzlaGeocoder)
        val repo = repository(
            FakePhoton { throw httpError(503) },
            FakeMeteo { throw IOException("down") },
            geocoder,
        )
        val found = repo.search("Tuzla", locale) as SearchOutcome.Found
        assertEquals(tuzlaGeocoder, found.results)
        assertEquals(1, geocoder.calls)
    }

    @Test
    fun `no results anywhere is reported as empty, not as an error`() = runTest {
        val repo = repository(
            FakePhoton { PhotonResponseDto() },
            FakeMeteo { GeocodingResponseDto() },
            FakeGeocoder(emptyList()),
        )
        assertEquals(SearchOutcome.Found(emptyList()), repo.search("Xyzqw", locale))
    }

    @Test
    fun `everything failing is reported as an error`() = runTest {
        val repo = repository(
            FakePhoton { throw IOException() },
            FakeMeteo { throw IOException() },
            FakeGeocoder(null),
        )
        assertEquals(SearchOutcome.Failed, repo.search("Tuzla", locale))
    }
}
