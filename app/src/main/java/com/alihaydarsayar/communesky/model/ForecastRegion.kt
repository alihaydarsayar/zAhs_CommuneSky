package com.alihaydarsayar.communesky.model

/**
 * Bir yerin, yerel tahmin kaynağı açısından bölgesi. Sınırlar kabadır (kutu ve çokgen); amaç ülkeyi tam bilmek
 * değil, o yerde hangi yerel modelin denenmeye değer olduğunu seçmektir. Sınıra yakın bir yer komşu ülkenin
 * modeliyle başlasa bile hakem ([ForecastReferee]) ölçümlere bakıp daha doğru olanı seçer. Saf mantık; test edilir.
 */
enum class ForecastRegion {
    /** Ulusal servis: MGM. */
    Turkey,

    /** Météo-France (AROME / ARPEGE). */
    France,

    /** Met Office (UKV 2 km ve küresel model). */
    UnitedKingdom,

    /** Met Office modeli ile Open-Meteo'nun seçimi hakeme bırakılır. */
    Ireland,
    Other,
    ;

    /** Bu bölgede Open-Meteo'dan ayrıca istenen yerel model; yoksa null. */
    val localModel: ForecastSource?
        get() = when (this) {
            France -> ForecastSource.MeteoFrance
            UnitedKingdom, Ireland -> ForecastSource.UkMetOffice
            Turkey, Other -> null
        }

    /**
     * Hakem karar verene kadar kullanılan kaynak. Fransa ve Birleşik Krallık'ta yerel yüksek çözünürlüklü model;
     * İrlanda'da Open-Meteo'nun seçimi (Met Office modeli aday olarak ölçülür).
     */
    val defaultSource: ForecastSource
        get() = when (this) {
            France -> ForecastSource.MeteoFrance
            UnitedKingdom -> ForecastSource.UkMetOffice
            Turkey, Ireland, Other -> ForecastSource.OpenMeteo
        }

    companion object {
        fun of(latitude: Double, longitude: Double): ForecastRegion = when {
            latitude in 35.8..42.2 && longitude in 25.6..44.9 -> Turkey
            isIreland(latitude, longitude) -> Ireland
            isUnitedKingdom(latitude, longitude) -> UnitedKingdom
            isFrance(latitude, longitude) -> France
            else -> Other
        }

        /** İrlanda adası, Kuzey İrlanda hariç (kaba kutu; Donegal İrlanda'da kalır). */
        private fun isIreland(lat: Double, lon: Double): Boolean {
            if (lat !in 51.3..55.5 || lon !in -10.8..-5.9) return false
            val northernIreland = lat in 54.02..55.3 && lon in -8.0..-5.4 && !(lon < -7.4 && lat > 54.6)
            return !northernIreland
        }

        /** Büyük Britanya, Kuzey İrlanda ve yakın adalar. */
        private fun isUnitedKingdom(lat: Double, lon: Double): Boolean =
            (lat in 49.9..60.9 && lon in -8.2..1.8) && !(lat < 51.0 && lon > 1.45)

        /** Anakara Fransa (kaba çokgen) ve Korsika. */
        private fun isFrance(lat: Double, lon: Double): Boolean {
            if (lat in 41.3..43.1 && lon in 8.5..9.6) return true
            return inside(lat, lon, MAINLAND_FRANCE)
        }

        /** Enlem–boylam köşeleri, saat yönünde: Dunkerque'ten başlayıp doğu sınırı, Akdeniz, Pireneler, Atlantik, Manş. */
        private val MAINLAND_FRANCE = listOf(
            51.1 to 2.55, 50.3 to 4.1, 49.5 to 5.8, 49.2 to 6.7, 49.0 to 8.2, 47.6 to 7.6, 47.4 to 7.0, 46.5 to 6.1,
            46.1 to 6.0, 45.9 to 7.0, 45.1 to 6.7, 44.2 to 7.6, 43.75 to 7.55, 43.0 to 6.2, 43.3 to 4.0, 42.4 to 3.2,
            42.45 to 1.7, 42.7 to 0.6, 43.3 to -1.8, 44.6 to -1.3, 46.1 to -1.5, 47.2 to -2.6, 47.7 to -4.5, 48.5 to -4.9,
            48.9 to -3.0, 48.6 to -1.5, 49.75 to -2.0, 49.4 to 0.0, 50.2 to 1.4, 51.0 to 1.7,
        )

        /** Nokta çokgenin içinde mi (ışın yöntemi)? */
        private fun inside(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>): Boolean {
            var inside = false
            var j = polygon.lastIndex
            for (i in polygon.indices) {
                val (latI, lonI) = polygon[i]
                val (latJ, lonJ) = polygon[j]
                if ((latI > lat) != (latJ > lat) && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI) inside = !inside
                j = i
            }
            return inside
        }
    }
}
