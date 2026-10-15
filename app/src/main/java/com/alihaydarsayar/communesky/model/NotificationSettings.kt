package com.alihaydarsayar.communesky.model

/** Hangi uyarılar için bildirim gelsin: en düşük seviye. */
enum class AlertThreshold(val lowest: AlertLevel) {
    /** Sarı dahil. */
    Yellow(AlertLevel.Yellow),

    /** Turuncu ve üstü (varsayılan). */
    Orange(AlertLevel.Orange),

    /** Yalnız kırmızı. */
    Red(AlertLevel.Red),
    ;

    fun includes(level: AlertLevel): Boolean = level >= lowest
}

/** Uyarı bildirimi hangi yerler için gelsin. */
enum class AlertPlaces { Current, Home, All }

/**
 * Bildirim ayarları. Bütün türler ilk kurulumda kapalıdır; kullanıcı bir türü açınca bildirim izni istenir.
 * Her türün sesli ya da sessiz gelmesi kullanıcının seçimidir (bkz. NotificationChannels).
 * Saatler gece yarısından itibaren dakikadır.
 */
data class NotificationSettings(
    val alerts: Boolean = false,
    val alertThreshold: AlertThreshold = AlertThreshold.Orange,
    val alertPlaces: AlertPlaces = AlertPlaces.All,
    val redSound: Boolean = true,
    val orangeSound: Boolean = true,
    val yellowSound: Boolean = false,
    val morning: Boolean = false,
    val morningMinutes: Int = 7 * 60 + 30,
    val morningSound: Boolean = false,
    val evening: Boolean = false,
    val eveningMinutes: Int = 21 * 60,
    val eveningSound: Boolean = false,
    val airQuality: Boolean = false,
    val airSound: Boolean = false,
    val quietHours: Boolean = false,
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    /** Kırmızı uyarılar sessiz saatlerde de sesli gelsin. */
    val quietRedSounds: Boolean = true,
    /** Uyarı şeridinin altındaki "bildirim almak ister misin?" satırı kapatıldı. */
    val promptDismissed: Boolean = false,
) {
    val anyOn: Boolean get() = alerts || morning || evening || airQuality
}

/**
 * 1.5.2'de bildirimler varsayılan kapalı oldu. Önceki sürümlerde uyarı bildirimi varsayılan açıktı: güncelleyen
 * kullanıcıda, bildirim izni verilmiş ve uyarılar açıksa ayar korunur; izin hiç verilmediyse kapanır. Yeni
 * kurulumda hep kapalıdır. Saf; test edilir.
 */
object NotificationMigration {
    fun alertsAfterUpdate(stored: Boolean?, permitted: Boolean, freshInstall: Boolean): Boolean =
        !freshInstall && permitted && (stored ?: true)
}
