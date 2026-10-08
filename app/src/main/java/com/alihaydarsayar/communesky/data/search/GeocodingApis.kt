package com.alihaydarsayar.communesky.data.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Photon (komoot): OpenStreetMap verisiyle çalışan, yazarken arama (autocomplete) için yapılmış
 * ücretsiz yer arama servisi. Semt, köy ve mahalleleri de bulur. Sadece "yer" türündeki
 * kayıtları istiyoruz; sokak, okul, dükkân gibi sonuçlar gelmesin.
 */
interface PhotonApi {

    @GET("api/")
    suspend fun search(
        @Query("q") query: String,
        /** Photon sadece default (yerel ad), en, de, fr destekliyor. */
        @Query("lang") language: String,
        @Query("limit") limit: Int = 10,
        @Query("osm_tag") osmTag: String = "place",
        @Query("layer") layers: List<String> = listOf("city", "district", "locality", "county"),
    ): PhotonResponseDto

    companion object {
        const val BASE_URL = "https://photon.komoot.io/"
    }
}

/** Open-Meteo'nun yer arama servisi (GeoNames verisi). Photon'a ek olarak, yedek kaynak. */
interface OpenMeteoGeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("language") language: String,
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json",
    ): GeocodingResponseDto

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}

@Serializable
data class PhotonResponseDto(val features: List<PhotonFeatureDto> = emptyList())

@Serializable
data class PhotonFeatureDto(
    val properties: PhotonPropertiesDto,
    val geometry: PhotonGeometryDto,
)

@Serializable
data class PhotonPropertiesDto(
    val name: String? = null,
    val district: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    @SerialName("countrycode") val countryCode: String? = null,
    /** Yerin türü: city, town, village, suburb, quarter… */
    @SerialName("osm_value") val osmValue: String? = null,
)

/** GeoJSON: koordinatlar [boylam, enlem] sırasında. */
@Serializable
data class PhotonGeometryDto(val coordinates: List<Double>)

@Serializable
data class GeocodingResponseDto(val results: List<GeocodingResultDto> = emptyList())

@Serializable
data class GeocodingResultDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
)
