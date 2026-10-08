package com.alihaydarsayar.communesky.data.search

import com.alihaydarsayar.communesky.model.GeoDistance
import com.alihaydarsayar.communesky.model.GeoPoint
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import java.text.Normalizer
import java.util.Locale

/** Arama servislerinin cevaplarını ortak sonuç biçimine çevirir ve birleştirir. Saf mantık; test edilir. */
object SearchResultMapping {

    /** Aynı adlı iki sonuç bu mesafeden yakınsa aynı yer sayılır (ilçe merkezi ve ilçe sınırı gibi). */
    private const val SAME_PLACE_KM = 8.0

    /** Yakınlık sayılan mesafe: kullanıcının bulunduğu ya da kayıtlı yerlerine bu kadar yakın olanlar öne gelir. */
    private const val NEAR_KM = 300.0

    private fun GeoPoint.isNear(result: PlaceSearchResult) =
        GeoDistance.kilometers(latitude, longitude, result.latitude, result.longitude) < NEAR_KM

    /**
     * Photon sonuçlarını sıralar: önce adı yazılanla birebir eşleşenler, sonra kullanıcının
     * bulunduğu yere ya da kayıtlı yerlerine yakın olanlar, sonra kullanıcının ülkesindekiler, sonra büyük yerler (şehir > ilçe > semt > köy). Böylece "Kadıköy" yazınca
     * aynı adlı köylerden önce İstanbul'daki Kadıköy, "Tuzla" yazınca İstanbul'daki Tuzla gelir.
     */
    fun fromPhoton(
        response: PhotonResponseDto,
        query: String,
        preferredCountries: Set<String> = emptySet(),
        anchors: List<GeoPoint> = emptyList(),
    ): List<PlaceSearchResult> {
        val normalizedQuery = normalize(query)
        val preferred = preferredCountries.map { it.uppercase(Locale.ROOT) }.toSet()
        return response.features
            .mapNotNull { feature ->
                val p = feature.properties
                val name = p.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val coordinates = feature.geometry.coordinates
                if (coordinates.size < 2) return@mapNotNull null
                // İlçe/şehir adı yerin kendi adıyla aynıysa tekrar yazma ("Tuzla, City of Tuzla" olmasın).
                val parent = listOf(p.city, p.county)
                    .firstOrNull { it != null && !it.contains(name, ignoreCase = true) }
                val result = PlaceSearchResult(
                    name = name,
                    region = joinRegion(parent, p.state, p.country),
                    countryCode = p.countryCode,
                    latitude = coordinates[1],
                    longitude = coordinates[0],
                )
                val nameMatch = when {
                    normalize(name) == normalizedQuery -> 0
                    normalize(name).startsWith(normalizedQuery) -> 1
                    else -> 2
                }
                val countryMatch = if (p.countryCode?.uppercase(Locale.ROOT) in preferred) 0 else 1
                val nearMatch = if (anchors.any { it.isNear(result) }) 0 else 1
                Ranked(result, nameMatch, nearMatch, countryMatch, placeRank(p.osmValue))
            }
            // sortedWith kararlıdır: eşit olanlar Photon'un kendi sırasında kalır.
            .sortedWith(compareBy({ it.nameMatch }, { it.nearMatch }, { it.countryMatch }, { it.placeRank }))
            .map { it.result }
    }

    private class Ranked(
        val result: PlaceSearchResult,
        val nameMatch: Int,
        val nearMatch: Int,
        val countryMatch: Int,
        val placeRank: Int,
    )

    private fun placeRank(osmValue: String?): Int = when (osmValue) {
        "city" -> 0
        "town", "municipality" -> 1
        "borough", "suburb", "quarter", "district" -> 2
        "village" -> 3
        else -> 4
    }

    fun fromOpenMeteo(response: GeocodingResponseDto): List<PlaceSearchResult> =
        response.results.map { r ->
            PlaceSearchResult(
                name = r.name,
                region = joinRegion(
                    r.admin2?.cleanAdminName()?.takeIf { !it.equals(r.name, ignoreCase = true) },
                    r.admin1?.cleanAdminName(),
                    r.country,
                ),
                countryCode = r.countryCode,
                latitude = r.latitude,
                longitude = r.longitude,
            )
        }

    /**
     * Önce birincil kaynağın sonuçları (sırası korunur), sonra ikincil kaynaktan sadece
     * listede olmayan yerler. Aynı kaynağın kendi içindeki tekrarları da elenir.
     */
    fun merge(
        primary: List<PlaceSearchResult>,
        secondary: List<PlaceSearchResult>,
        limit: Int = 10,
    ): List<PlaceSearchResult> {
        val merged = mutableListOf<PlaceSearchResult>()
        for (candidate in primary + secondary) {
            if (merged.size >= limit) break
            if (merged.none { it.isSamePlaceAs(candidate) }) merged += candidate
        }
        return merged
    }

    fun PlaceSearchResult.isSamePlaceAs(other: PlaceSearchResult): Boolean =
        normalize(name) == normalize(other.name) &&
            GeoDistance.kilometers(latitude, longitude, other.latitude, other.longitude) < SAME_PLACE_KM

    /** Büyük/küçük harf ve aksan farkını yok sayar: "Kadıköy", "KADIKÖY" ve "Kadikoy" aynı. */
    fun normalize(text: String): String {
        val lower = text.trim().lowercase(Locale.ROOT).replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(DiacriticRegex, "")
    }

    private val DiacriticRegex = Regex("\\p{Mn}+")

    private fun joinRegion(vararg parts: String?): String? =
        parts.filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { null }

    // GeoNames Türkçe adları "Karataş İlçesi", "Samsun İli" biçiminde; ekleri at.
    private fun String.cleanAdminName(): String =
        removeSuffix(" İlçesi").removeSuffix(" İli").removeSuffix(" Province").trim()
}
