package com.alihaydarsayar.communesky.model

/**
 * [name] null ise konumun adı bulunamamıştır; ekran "Konumum" yazar.
 * [region]: ad mahalle düzeyindeyse bağlı olduğu ilçe ("Merkez" → "Beşiktaş"); ikinci satırda gösterilir.
 */
data class City(
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
) {
    companion object {
        val Istanbul = City(name = "İstanbul", latitude = 41.0082, longitude = 28.9784)
    }
}
