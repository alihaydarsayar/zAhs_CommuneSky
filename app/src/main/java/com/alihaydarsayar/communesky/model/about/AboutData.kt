package com.alihaydarsayar.communesky.model.about

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uygulama içi gizlilik politikası metninin blokları. Metin res/raw/privacy_policy.txt (tr, en, fr) içinde, sitedeki
 * sayfayla aynı içerikle durur; ağdan çekilmez. Biçim: "# " başlık, "## " bölüm, "- " madde, öteki satırlar paragraf;
 * **kalın** satır içinde. Saf; test edilir.
 */
sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Section(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data class Bullet(val text: String) : PolicyBlock
}

object PolicyText {

    fun parse(text: String): List<PolicyBlock> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            line.startsWith("## ") -> PolicyBlock.Section(line.removePrefix("## ").trim())
            line.startsWith("# ") -> PolicyBlock.Title(line.removePrefix("# ").trim())
            line.startsWith("- ") -> PolicyBlock.Bullet(line.removePrefix("- ").trim())
            else -> PolicyBlock.Paragraph(line)
        }
    }

    /** Bir satırın kalın ve düz parçaları: (metin, kalın mı). "**" eşlenmemişse kalan düz yazılır. */
    fun spans(text: String): List<Pair<String, Boolean>> {
        val parts = text.split("**")
        if (parts.size % 2 == 0) return listOf(text.replace("**", "") to false)
        return parts.mapIndexedNotNull { index, part -> part.takeIf { it.isNotEmpty() }?.let { it to (index % 2 == 1) } }
    }
}

/** AboutLibraries eklentisinin derleme sırasında ürettiği res/raw/aboutlibraries.json'dan okunan bir kayıt. */
data class OpenSourceItem(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    val website: String?,
    val authors: List<String>,
    val licenses: List<LicenseText>,
) {
    /** Kod kütüphanesi değil, uygulamaya gömülü bir varlık (yazı tipi, simge, çizim). */
    val isAsset: Boolean get() = id.startsWith(ASSET_PREFIX)

    companion object {
        const val ASSET_PREFIX = "asset:"
    }
}

data class LicenseText(val id: String, val name: String, val url: String?, val content: String?)

/** aboutlibraries.json okuyucusu. Kütüphanesiz (kotlinx.serialization); bilinmeyen alanlar atlanır. Saf; test edilir. */
object OpenSourceLicenses {

    @Serializable
    private data class Developer(val name: String? = null)

    @Serializable
    private data class Library(
        val uniqueId: String,
        val name: String? = null,
        val artifactVersion: String? = null,
        val description: String? = null,
        val website: String? = null,
        val developers: List<Developer> = emptyList(),
        val licenses: List<String> = emptyList(),
    )

    @Serializable
    private data class License(val name: String? = null, val url: String? = null, val content: String? = null)

    @Serializable
    private data class Definitions(val libraries: List<Library> = emptyList(), val licenses: Map<String, License> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    /** Önce gömülü varlıklar, sonra kütüphaneler; her grup ada göre sıralı. */
    fun parse(text: String): List<OpenSourceItem> {
        val definitions = runCatching { json.decodeFromString(Definitions.serializer(), text) }.getOrDefault(Definitions())
        return definitions.libraries.map { library ->
            OpenSourceItem(
                id = library.uniqueId,
                name = library.name?.takeIf { it.isNotBlank() } ?: library.uniqueId,
                version = library.artifactVersion,
                description = library.description?.takeIf { it.isNotBlank() },
                website = library.website?.takeIf { it.isNotBlank() },
                authors = library.developers.mapNotNull { it.name?.takeIf(String::isNotBlank) },
                licenses = library.licenses.map { id ->
                    val license = definitions.licenses[id]
                    LicenseText(id, license?.name ?: id, license?.url, license?.content?.takeIf { it.isNotBlank() })
                },
            )
        }.sortedWith(compareBy<OpenSourceItem>({ !it.isAsset }, { it.name.lowercase() }))
    }
}

/**
 * Bir veri kaynağı: kim, ne için, hangi lisans ya da kullanım koşuluyla, kaynağın istediği atıf metni ve bağlantı.
 * [license] ve [attribution] kaynağın kendi sayfasındaki adlar ve metinlerdir; çevrilmez. [terms] kullanım koşulu
 * sayfası. Kaynağın ne için kullanıldığı ve kullanım koşulunun kısa özeti ekranda çevrilmiş metin olarak durur
 * (strings: source_<key>_use, source_<key>_terms); emin olunmayan bir koşul "serbest" diye yazılmaz.
 */
data class DataSource(
    val key: String,
    val name: String,
    val license: String,
    val attribution: String,
    val link: String,
    val terms: String,
)

object DataSources {

    /** Copernicus atfı yılı içerir ([year]: verinin kullanıldığı yıl). */
    fun all(year: Int): List<DataSource> = listOf(
        DataSource(
            "open_meteo", "Open-Meteo", "CC BY 4.0",
            "Weather data by Open-Meteo.com", "https://open-meteo.com/", "https://open-meteo.com/en/licence",
        ),
        DataSource(
            "ecmwf", "ECMWF (IFS, AIFS) via Open-Meteo", "CC BY 4.0",
            "© $year European Centre for Medium-Range Weather Forecasts (ECMWF). This service is based on data and products of ECMWF. " +
                "ECMWF does not accept any liability whatsoever for any error or omission in the data.",
            "https://www.ecmwf.int/", "https://www.ecmwf.int/en/forecasts/datasets/open-data",
        ),
        DataSource(
            "meteo_france", "Météo-France (AROME, ARPEGE) via Open-Meteo", "Licence Ouverte / Etalab 2.0",
            "Source : Météo-France", "https://meteofrance.com/", "https://meteo.data.gouv.fr/",
        ),
        DataSource(
            "met_office", "Met Office (UKV, global) via Open-Meteo", "CC BY-SA 4.0",
            "© Crown copyright, Met Office", "https://www.metoffice.gov.uk/", "https://open-meteo.com/en/licence",
        ),
        DataSource(
            "cams", "Copernicus Atmosphere Monitoring Service (CAMS) via Open-Meteo", "Copernicus licence",
            "Generated using Copernicus Atmosphere Monitoring Service information $year. Neither the European Commission nor ECMWF is responsible for any use that may be made of the information it contains.",
            "https://atmosphere.copernicus.eu/", "https://apps.ecmwf.int/datasets/licences/copernicus/",
        ),
        DataSource(
            "mgm", "Meteoroloji Genel Müdürlüğü (MGM)", "© MGM, Her Hakkı Saklıdır",
            "Kaynak: Meteoroloji Genel Müdürlüğü (MGM)", "https://www.mgm.gov.tr/", "https://www.mgm.gov.tr/site/yasal-uyari.aspx",
        ),
        DataSource(
            "noaa", "NOAA Aviation Weather Center", "U.S. public domain (NOAA / NWS)",
            "Airport reports (METAR): NOAA Aviation Weather Center", "https://aviationweather.gov/", "https://www.weather.gov/disclaimer",
        ),
        DataSource(
            "osm", "OpenStreetMap via Photon (komoot)", "ODbL 1.0",
            "© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright", "https://photon.komoot.io/",
        ),
        DataSource(
            "geonames", "GeoNames via Open-Meteo Geocoding", "CC BY 4.0",
            "Place names: GeoNames (geonames.org)", "https://www.geonames.org/", "https://open-meteo.com/en/licence",
        ),
    )
}
