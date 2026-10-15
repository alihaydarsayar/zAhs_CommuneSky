package com.alihaydarsayar.communesky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.HomeAwaySettings
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.NotificationSettings
import com.alihaydarsayar.communesky.model.TemperatureUnit
import com.alihaydarsayar.communesky.model.ThemeMode
import com.alihaydarsayar.communesky.model.WearSettings
import com.alihaydarsayar.communesky.model.WindUnit
import com.alihaydarsayar.communesky.model.today.Clothing
import com.alihaydarsayar.communesky.model.today.Sensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ayarlar DataStore'da saklanır (küçük anahtar–değer dosyası, eşzamansız ve güvenli).
 * Dil ayarı burada değil: onu Android'in kendisi (uygulama dili) saklıyor, bkz. AppLanguage.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                temperatureUnit = prefs[TEMPERATURE_UNIT].toEnum(TemperatureUnit.Celsius),
                windUnit = prefs[WIND_UNIT].toEnum(WindUnit.KilometersPerHour),
                themeMode = prefs[THEME_MODE].toEnum(ThemeMode.System),
                notifications = notifications(prefs),
                wear = WearSettings(
                    sensitivity = prefs[WEAR_SENSITIVITY].toEnum(Sensitivity.Normal),
                    feedback = (prefs[WEAR_FEEDBACK] ?: 0.0).coerceIn(-Clothing.FEEDBACK_LIMIT, Clothing.FEEDBACK_LIMIT),
                ),
                homeAway = HomeAwaySettings(
                    nearbyRadiusKm = (prefs[NEARBY_RADIUS_KM] ?: HomeDetection.DEFAULT_NEARBY_KM)
                        .coerceIn(HomeDetection.MIN_NEARBY_KM, HomeDetection.MAX_NEARBY_KM),
                    showTemperatureDifference = prefs[AWAY_TEMPERATURE_DIFFERENCE] ?: true,
                    showHomeRain = prefs[AWAY_HOME_RAIN] ?: true,
                    showDistance = prefs[AWAY_DISTANCE] ?: true,
                    showBothWhenNearby = prefs[NEARBY_SHOW_BOTH] ?: false,
                ),
            )
        }
        .distinctUntilChanged()

    /** Ana ekranda en son bakılan yer; uygulama açılınca oradan başlanır. */
    val selectedPlaceId: Flow<Long?> = dataStore.data.map { it[SELECTED_PLACE_ID] }.distinctUntilChanged()

    suspend fun setTemperatureUnit(unit: TemperatureUnit) = set(TEMPERATURE_UNIT, unit.name)

    suspend fun setWindUnit(unit: WindUnit) = set(WIND_UNIT, unit.name)

    suspend fun setThemeMode(mode: ThemeMode) = set(THEME_MODE, mode.name)

    /** Bildirim ayarlarının hepsini birlikte yazar. */
    suspend fun setNotifications(settings: NotificationSettings) {
        dataStore.edit {
            it[ALERT_NOTIFICATIONS] = settings.alerts
            it[ALERT_THRESHOLD] = settings.alertThreshold.name
            it[ALERT_PLACES] = settings.alertPlaces.name
            it[RED_SOUND] = settings.redSound
            it[ORANGE_SOUND] = settings.orangeSound
            it[YELLOW_SOUND] = settings.yellowSound
            it[MORNING_SUMMARY] = settings.morning
            it[MORNING_SUMMARY_MINUTES] = settings.morningMinutes.coerceIn(0, DAY_MINUTES - 1)
            it[MORNING_SOUND] = settings.morningSound
            it[EVENING_SUMMARY] = settings.evening
            it[EVENING_SUMMARY_MINUTES] = settings.eveningMinutes.coerceIn(0, DAY_MINUTES - 1)
            it[EVENING_SOUND] = settings.eveningSound
            it[AIR_NOTIFICATIONS] = settings.airQuality
            it[AIR_SOUND] = settings.airSound
            it[QUIET_HOURS] = settings.quietHours
            it[QUIET_START] = settings.quietStart.coerceIn(0, DAY_MINUTES - 1)
            it[QUIET_END] = settings.quietEnd.coerceIn(0, DAY_MINUTES - 1)
            it[QUIET_RED_SOUNDS] = settings.quietRedSounds
            it[PROMPT_DISMISSED] = settings.promptDismissed
        }
    }

    /**
     * 1.5.2'deki bir kerelik geçiş: bildirimler artık varsayılan kapalı (bkz. NotificationMigration). Yeni
     * kurulumda ve izni olmayan güncellemelerde uyarılar kapanır.
     */
    suspend fun migrateNotifications(permitted: Boolean, freshInstall: Boolean) {
        dataStore.edit {
            if (it[NOTIFICATIONS_MIGRATED] == true) return@edit
            it[ALERT_NOTIFICATIONS] = com.alihaydarsayar.communesky.model.NotificationMigration.alertsAfterUpdate(it[ALERT_NOTIFICATIONS], permitted, freshInstall)
            it[NOTIFICATIONS_MIGRATED] = true
        }
    }

    private fun notifications(prefs: Preferences): NotificationSettings {
        val defaults = NotificationSettings()
        return NotificationSettings(
            alerts = prefs[ALERT_NOTIFICATIONS] ?: defaults.alerts,
            alertThreshold = prefs[ALERT_THRESHOLD].toEnum(defaults.alertThreshold),
            alertPlaces = prefs[ALERT_PLACES].toEnum(defaults.alertPlaces),
            redSound = prefs[RED_SOUND] ?: defaults.redSound,
            orangeSound = prefs[ORANGE_SOUND] ?: defaults.orangeSound,
            yellowSound = prefs[YELLOW_SOUND] ?: defaults.yellowSound,
            morning = prefs[MORNING_SUMMARY] ?: defaults.morning,
            morningMinutes = (prefs[MORNING_SUMMARY_MINUTES] ?: defaults.morningMinutes).coerceIn(0, DAY_MINUTES - 1),
            morningSound = prefs[MORNING_SOUND] ?: defaults.morningSound,
            evening = prefs[EVENING_SUMMARY] ?: defaults.evening,
            eveningMinutes = (prefs[EVENING_SUMMARY_MINUTES] ?: defaults.eveningMinutes).coerceIn(0, DAY_MINUTES - 1),
            eveningSound = prefs[EVENING_SOUND] ?: defaults.eveningSound,
            airQuality = prefs[AIR_NOTIFICATIONS] ?: defaults.airQuality,
            airSound = prefs[AIR_SOUND] ?: defaults.airSound,
            quietHours = prefs[QUIET_HOURS] ?: defaults.quietHours,
            quietStart = (prefs[QUIET_START] ?: defaults.quietStart).coerceIn(0, DAY_MINUTES - 1),
            quietEnd = (prefs[QUIET_END] ?: defaults.quietEnd).coerceIn(0, DAY_MINUTES - 1),
            quietRedSounds = prefs[QUIET_RED_SOUNDS] ?: defaults.quietRedSounds,
            promptDismissed = prefs[PROMPT_DISMISSED] ?: defaults.promptDismissed,
        )
    }

    suspend fun setWearSensitivity(sensitivity: Sensitivity) = set(WEAR_SENSITIVITY, sensitivity.name)

    /** "Üşüdüm" ([feltCold]) ya da "Bunaldım" düğmesi: öneri eşiği yarım derece kayar. */
    suspend fun addWearFeedback(feltCold: Boolean) {
        dataStore.edit { it[WEAR_FEEDBACK] = Clothing.afterFeedback(it[WEAR_FEEDBACK] ?: 0.0, feltCold) }
    }

    /** "Sıfırla": düğmelerle yapılan kaydırma silinir (Çabuk üşürüm / Sıcaklarım seçimi kalır). */
    suspend fun resetWearFeedback() {
        dataStore.edit { it.remove(WEAR_FEEDBACK) }
    }

    suspend fun setHomeAway(settings: HomeAwaySettings) {
        dataStore.edit {
            it[NEARBY_RADIUS_KM] = settings.nearbyRadiusKm
                .coerceIn(HomeDetection.MIN_NEARBY_KM, HomeDetection.MAX_NEARBY_KM)
            it[AWAY_TEMPERATURE_DIFFERENCE] = settings.showTemperatureDifference
            it[AWAY_HOME_RAIN] = settings.showHomeRain
            it[AWAY_DISTANCE] = settings.showDistance
            it[NEARBY_SHOW_BOTH] = settings.showBothWhenNearby
        }
    }

    suspend fun setSelectedPlaceId(id: Long) {
        dataStore.edit { it[SELECTED_PLACE_ID] = id }
    }

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_PLACE_ID = longPreferencesKey("selected_place_id")
        val NEARBY_RADIUS_KM = intPreferencesKey("nearby_radius_km")
        val AWAY_TEMPERATURE_DIFFERENCE = booleanPreferencesKey("away_temperature_difference")
        val AWAY_HOME_RAIN = booleanPreferencesKey("away_home_rain")
        val AWAY_DISTANCE = booleanPreferencesKey("away_distance")
        val NEARBY_SHOW_BOTH = booleanPreferencesKey("nearby_show_both")
        val ALERT_NOTIFICATIONS = booleanPreferencesKey("alert_notifications")
        val WEAR_SENSITIVITY = stringPreferencesKey("wear_sensitivity")
        val AIR_NOTIFICATIONS = booleanPreferencesKey("air_quality_notifications")
        val MORNING_SUMMARY = booleanPreferencesKey("morning_summary")
        val MORNING_SUMMARY_MINUTES = intPreferencesKey("morning_summary_minutes")
        val ALERT_THRESHOLD = stringPreferencesKey("alert_threshold")
        val ALERT_PLACES = stringPreferencesKey("alert_places")
        val RED_SOUND = booleanPreferencesKey("notify_red_sound")
        val ORANGE_SOUND = booleanPreferencesKey("notify_orange_sound")
        val YELLOW_SOUND = booleanPreferencesKey("notify_yellow_sound")
        val MORNING_SOUND = booleanPreferencesKey("morning_summary_sound")
        val EVENING_SUMMARY = booleanPreferencesKey("evening_summary")
        val EVENING_SUMMARY_MINUTES = intPreferencesKey("evening_summary_minutes")
        val EVENING_SOUND = booleanPreferencesKey("evening_summary_sound")
        val AIR_SOUND = booleanPreferencesKey("air_quality_sound")
        val QUIET_HOURS = booleanPreferencesKey("quiet_hours")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val QUIET_RED_SOUNDS = booleanPreferencesKey("quiet_red_sounds")
        val PROMPT_DISMISSED = booleanPreferencesKey("notification_prompt_dismissed")
        val NOTIFICATIONS_MIGRATED = booleanPreferencesKey("notifications_migrated_152")
        const val DAY_MINUTES = 24 * 60
        val WEAR_FEEDBACK = doublePreferencesKey("wear_feedback")
    }
}

// Enum adı değişir ya da dosya bozulursa çökmek yerine varsayılan değeri kullan.
private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
