package com.alihaydarsayar.communesky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.HomeAwaySettings
import com.alihaydarsayar.communesky.model.HomeDetection
import com.alihaydarsayar.communesky.model.TemperatureUnit
import com.alihaydarsayar.communesky.model.ThemeMode
import com.alihaydarsayar.communesky.model.WindUnit
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
    }
}

// Enum adı değişir ya da dosya bozulursa çökmek yerine varsayılan değeri kullan.
private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
