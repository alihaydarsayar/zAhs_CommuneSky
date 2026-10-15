package com.alihaydarsayar.communesky.model

import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.Sensitivity

/** Kullanıcı ayarları. Hava verisi hep °C ve km/sa olarak saklanır; birim sadece gösterirken çevrilir. */
data class AppSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val windUnit: WindUnit = WindUnit.KilometersPerHour,
    val themeMode: ThemeMode = ThemeMode.System,
    val homeAway: HomeAwaySettings = HomeAwaySettings(),
    val wear: WearSettings = WearSettings(),
    /** Bildirimler: uyarılar, sabah ve akşam özeti, hava kalitesi, sessiz saatler. Hepsi varsayılan kapalı. */
    val notifications: NotificationSettings = NotificationSettings(),
)

/** "Ne giysem" için kişisel ayar; telefonda kalır. */
data class WearSettings(
    val sensitivity: Sensitivity = Sensitivity.Normal,
    /** "Üşüdüm" (+) / "Bunaldım" (−) düğmelerinin toplamı (°C, en çok ±4). */
    val feedback: Double = 0.0,
) {
    /** Eşiklerin toplam kayması (°C). */
    val shift: Double get() = Clothing.personalShift(sensitivity, feedback)
}

/**
 * Ayarlar > Ev ve konum. Evde/yakında/uzakta kararı ve widget'ın uzaktayken neleri göstereceği.
 * Hepsi cihazda kalır.
 */
data class HomeAwaySettings(
    /** Bu mesafe içinde "evin yakınında" sayılır (km, 2–20). */
    val nearbyRadiusKm: Int = HomeDetection.DEFAULT_NEARBY_KM,
    val showTemperatureDifference: Boolean = true,
    val showHomeRain: Boolean = true,
    val showDistance: Boolean = true,
    /** Yakındayken de iki yer yan yana gösterilsin. */
    val showBothWhenNearby: Boolean = false,
)

enum class TemperatureUnit {
    Celsius,
    Fahrenheit;

    fun fromCelsius(celsius: Double): Double = when (this) {
        Celsius -> celsius
        Fahrenheit -> celsius * 9.0 / 5.0 + 32.0
    }
}

enum class WindUnit {
    KilometersPerHour,
    MetersPerSecond,
    MilesPerHour,
    Knots;

    fun fromKmh(kmh: Double): Double = when (this) {
        KilometersPerHour -> kmh
        MetersPerSecond -> kmh / 3.6
        MilesPerHour -> kmh / 1.609344
        Knots -> kmh / 1.852
    }
}

/** Koyu tema, parlak gündüz gökyüzlerini hafifçe karartır; uygulamanın geri kalanı hep gökyüzü renginde. */
enum class ThemeMode { System, Light, Dark }
