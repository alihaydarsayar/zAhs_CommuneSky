package com.alihaydarsayar.communesky.data.mgm

import com.alihaydarsayar.communesky.data.observation.MgmApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * MGM'nin (Meteoroloji Genel Müdürlüğü) web sitesinin kullandığı açık servisin tahmin, uyarı ve
 * mevsim normali uçları. Anlık ölçüm uçları [MgmApi]'dedir; ikisi aynı sunucuyu kullanır.
 * Servis, isteğin MGM sitesinden geldiğini belirten Origin başlığı olmadan cevap vermiyor.
 */
interface MgmForecastApi {

    /** Koordinatın bağlı olduğu ilçe merkezi: tahmin istasyonları ve uyarılardaki ilçe kodu. */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/merkezler/lokasyon")
    suspend fun center(@Query("enlem") latitude: Double, @Query("boylam") longitude: Double): MgmForecastCenterDto

    /** Bir ilin merkezi (mevsim normalleri yalnızca il merkezleri için verilir). */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/merkezler")
    suspend fun provinceCenters(@Query("il") province: String): List<MgmForecastCenterDto>

    /** Saatlik tahmin (servisin verdiği adımlarla; bugün 3 saatte bir). */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/tahminler/saatlik")
    suspend fun hourly(@Query("istno") stationId: Int): List<MgmHourlyForecastDto>

    /** 5 günlük tahmin. Alanlar gün numarasıyla gelir (tarihGun1, enYuksekGun1…); elle okunur. */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/tahminler/gunluk")
    suspend fun daily(@Query("istno") stationId: Int): List<JsonObject>

    /** Bugün geçerli meteorolojik uyarılar (bütün Türkiye). */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/meteoalarm/today")
    suspend fun alertsToday(): List<MgmAlertDto>

    /** Yarın geçerli meteorolojik uyarılar (bütün Türkiye). */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/meteoalarm/tomorrow")
    suspend fun alertsTomorrow(): List<MgmAlertDto>

    /** O günün uzun yıllar ortalaması (mevsim normali). */
    @Headers("Origin: ${MgmApi.ORIGIN}")
    @GET("web/ucdegerler")
    suspend fun normals(@Query("merkezid") centerId: Int, @Query("ay") month: Int, @Query("gun") day: Int): List<MgmNormalDto>
}

@Serializable
data class MgmForecastCenterDto(
    /** İlçe kodu: uyarılardaki "towns" listeleri bu kodla eşleşir. */
    val merkezId: Int? = null,
    /** Bazı kırsal yerlerde boş. */
    val saatlikTahminIstNo: Int? = null,
    val gunlukTahminIstNo: Int? = null,
    val il: String? = null,
    val ilce: String? = null,
)

@Serializable
data class MgmHourlyForecastDto(
    val tahmin: List<MgmHourDto> = emptyList(),
)

/** -9999 değeri "veri yok" demek. */
@Serializable
data class MgmHourDto(
    val tarih: String? = null,
    val hadise: String? = null,
    val sicaklik: Double? = null,
    val nem: Double? = null,
    val ruzgarYonu: Double? = null,
    val ruzgarHizi: Double? = null,
    val maksimumRuzgarHizi: Double? = null,
)

/** Her renk için ayrı alan: towns.yellow/orange/red (ilçe kodları), weather (olay türleri), text (açıklama). */
@Serializable
data class MgmAlertDto(
    @SerialName("_id") val id: String? = null,
    val alertNo: Long? = null,
    val text: JsonObject? = null,
    val weather: JsonObject? = null,
    val towns: JsonObject? = null,
    val begin: String? = null,
    val end: String? = null,
)

@Serializable
data class MgmNormalDto(
    val ay: Int? = null,
    val gun: Int? = null,
    val maxOrt: Double? = null,
    val minOrt: Double? = null,
)
