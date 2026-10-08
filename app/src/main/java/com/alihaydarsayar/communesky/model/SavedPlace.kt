package com.alihaydarsayar.communesky.model

/** Kullanıcının kaydettiği yer. */
data class SavedPlace(
    val id: Long,
    val name: String,
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    val isHome: Boolean,
    val sortOrder: Int = 0,
)

/** Yer aramasının bir sonucu; henüz kaydedilmemiş. */
data class PlaceSearchResult(
    val name: String,
    /** "Atakum, Samsun, Türkiye" gibi; aynı adlı yerleri ayırt etmek için. */
    val region: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
)
