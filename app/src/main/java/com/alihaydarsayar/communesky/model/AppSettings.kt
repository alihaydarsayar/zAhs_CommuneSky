package com.alihaydarsayar.communesky.model

/** Kullanıcı ayarları. Hava verisi hep °C ve km/sa olarak saklanır; birim sadece gösterirken çevrilir. */
data class AppSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val windUnit: WindUnit = WindUnit.KilometersPerHour,
    val themeMode: ThemeMode = ThemeMode.System,
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
