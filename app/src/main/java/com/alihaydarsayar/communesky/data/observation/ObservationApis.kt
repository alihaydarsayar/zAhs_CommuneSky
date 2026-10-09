package com.alihaydarsayar.communesky.data.observation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * MGM'nin (Meteoroloji Genel Müdürlüğü) web sitesinin kullandığı açık servis.
 * Servis, isteğin MGM sitesinden geldiğini belirten Origin başlığı olmadan cevap vermiyor.
 */
interface MgmApi {

    /** Koordinatın bağlı olduğu ilçe merkezi ve o merkezin "son durum" istasyonu. */
    @Headers("Origin: $ORIGIN")
    @GET("web/merkezler/lokasyon")
    suspend fun center(
        @Query("enlem") latitude: Double,
        @Query("boylam") longitude: Double,
    ): MgmCenterDto

    /** İstasyonun adı ve konumu. */
    @Headers("Origin: $ORIGIN")
    @GET("web/istasyonlar")
    suspend fun station(@Query("istno") stationId: Int): List<MgmStationDto>

    /** İstasyonun son ölçümü (genelde 10–15 dakikada bir yenilenir). */
    @Headers("Origin: $ORIGIN")
    @GET("web/sondurumlar")
    suspend fun latest(@Query("istno") stationId: Int): List<MgmObservationDto>

    companion object {
        const val BASE_URL = "https://servis.mgm.gov.tr/"
        const val ORIGIN = "https://www.mgm.gov.tr"
    }
}

/** NOAA Aviation Weather Center: havalimanlarının son ölçümleri (METAR), dünya geneli. */
interface AviationWeatherApi {

    /** [bbox] "güney,batı,kuzey,doğu" biçiminde; kutudaki istasyonların son ölçümü. */
    @GET("api/data/metar")
    suspend fun metars(
        @Query("bbox") bbox: String,
        @Query("format") format: String = "json",
    ): List<MetarDto>

    companion object {
        const val BASE_URL = "https://aviationweather.gov/"
    }
}

@Serializable
data class MgmCenterDto(
    @SerialName("sondurumIstNo") val observationStationId: Int? = null,
    val il: String? = null,
    val ilce: String? = null,
)

@Serializable
data class MgmStationDto(
    @SerialName("istNo") val id: Int,
    @SerialName("istAd") val name: String? = null,
    @SerialName("enlem") val latitude: Double,
    @SerialName("boylam") val longitude: Double,
    @SerialName("yukseklik") val elevation: Double? = null,
)

/** -9999 değeri "ölçüm yok" demek. */
@Serializable
data class MgmObservationDto(
    @SerialName("istNo") val stationId: Int,
    @SerialName("veriZamani") val time: String,
    @SerialName("sicaklik") val temperature: Double? = null,
    @SerialName("nem") val humidity: Double? = null,
    @SerialName("ruzgarHiz") val windSpeedKmh: Double? = null,
    @SerialName("hadiseKodu") val weatherCode: String? = null,
)

@Serializable
data class MetarDto(
    val icaoId: String,
    /** Ölçüm zamanı, Unix saniye. */
    val obsTime: Long,
    val temp: Double? = null,
    val dewp: Double? = null,
    /** Rüzgâr hızı, knot. */
    val wspd: Double? = null,
    val wxString: String? = null,
    val cover: String? = null,
    val lat: Double,
    val lon: Double,
    val elev: Double? = null,
    /** "Samsun-Çarşamba Arpt, SA, TR" */
    val name: String? = null,
)
