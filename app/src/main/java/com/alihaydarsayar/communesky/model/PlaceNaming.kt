package com.alihaydarsayar.communesky.model

import java.util.Locale

/** "Bulunduğum yer"in ekranda görünen adı ve (mahalle gösteriliyorsa) bağlı olduğu ilçe. */
data class PlaceName(val name: String?, val region: String?)

/** Geocoder adresinden yer adı seçer. Saf mantık; test edilir. */
object PlaceNaming {

    private val NeighbourhoodSuffixes = listOf(" Mahallesi", " Mah.", " Mah", " Mh.", " Neighbourhood", " Neighborhood")

    /**
     * Hassas konumda mahalle (subLocality) varsa ad mahalle, ikinci satır ilçe olur: "Merkez",
     * "Beşiktaş". Yaklaşık konumda (kullanıcı "Yaklaşık"ı seçtiyse) mahalle yanlış olabileceği için
     * sadece ilçe adı gösterilir.
     *
     * Türkiye'de Google Geocoder genelde mahalleyi subLocality, ilçeyi subAdminArea ya da
     * locality, ili adminArea olarak verir.
     */
    fun fromAddress(
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        precise: Boolean,
    ): PlaceName {
        val district = listOf(subAdminArea, locality).firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: adminArea?.trim()?.takeIf { it.isNotEmpty() }
        val neighbourhood = subLocality?.let(::cleanNeighbourhood)?.takeIf { it.isNotEmpty() }
        return if (precise && neighbourhood != null && !neighbourhood.equals(district, ignoreCase = true)) {
            PlaceName(name = neighbourhood, region = district)
        } else {
            PlaceName(name = district, region = null)
        }
    }

    /** "Merkez Mahallesi" → "Merkez". */
    fun cleanNeighbourhood(name: String): String {
        var result = name.trim()
        for (suffix in NeighbourhoodSuffixes) {
            if (result.endsWith(suffix, ignoreCase = true)) {
                result = result.dropLast(suffix.length).trim()
                break
            }
        }
        return result.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("tr")) else it.toString() }
    }
}
