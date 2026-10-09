package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Widget'ın hangi yerin havasını gösterdiği. */
sealed interface WidgetPlace {
    /** Cihazın konumu (izin yoksa yedek şehir). */
    data object Device : WidgetPlace

    /** "Ev" olarak işaretlenen yer; Ev değişirse widget da onu izler. Ev yoksa cihaz konumu. */
    data object Home : WidgetPlace

    /** Belirli bir kayıtlı yer; silinirse cihaz konumuna döner. */
    data class Saved(val placeId: Long) : WidgetPlace

    fun encode(): String = when (this) {
        Device -> "device"
        Home -> "home"
        is Saved -> "place:$placeId"
    }

    companion object {
        fun decode(value: String?): WidgetPlace = when {
            value == "home" -> Home
            value?.startsWith("place:") == true ->
                value.removePrefix("place:").toLongOrNull()?.let(::Saved) ?: Device
            else -> Device
        }
    }
}

enum class WidgetBackground {
    /** Havaya göre değişen gökyüzü gradyanı. */
    Sky,

    /** Koyu, yarı saydam cam. */
    Translucent,

    /** Arka plan yok; yazılar gölgeyle her duvar kâğıdında okunur. */
    Transparent,
}

data class WidgetConfig(
    val place: WidgetPlace = WidgetPlace.Device,
    val background: WidgetBackground = WidgetBackground.Sky,
)

/**
 * Her widget'ın ayarı, Android'in verdiği widget kimliğiyle (appWidgetId) saklanır.
 * Uygulamanın Hilt kabına bağlı değil; widget sınıfları ve ayar ekranı doğrudan kullanır.
 */
class WidgetConfigStore internal constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun get(appWidgetId: Int): WidgetConfig = observe(appWidgetId).first()

    /** Ayar değiştikçe yeni değeri yayar; widget bunu dinleyerek hemen yeniden çizilir. */
    fun observe(appWidgetId: Int): Flow<WidgetConfig> = dataStore.data
        .map { prefs ->
            WidgetConfig(
                place = WidgetPlace.decode(prefs[placeKey(appWidgetId)]),
                background = WidgetBackground.entries.firstOrNull { it.name == prefs[backgroundKey(appWidgetId)] }
                    ?: WidgetBackground.Sky,
            )
        }
        .distinctUntilChanged()

    suspend fun set(appWidgetId: Int, config: WidgetConfig) {
        dataStore.edit {
            it[placeKey(appWidgetId)] = config.place.encode()
            it[backgroundKey(appWidgetId)] = config.background.name
        }
    }

    /** Widget ana ekrandan kaldırılınca ayarı da silinir. */
    suspend fun remove(appWidgetIds: IntArray) {
        dataStore.edit { prefs ->
            appWidgetIds.forEach {
                prefs.remove(placeKey(it))
                prefs.remove(backgroundKey(it))
            }
        }
    }

    private fun placeKey(id: Int) = stringPreferencesKey("widget_${id}_place")
    private fun backgroundKey(id: Int) = stringPreferencesKey("widget_${id}_background")

    companion object {
        @Volatile private var instance: WidgetConfigStore? = null

        // DataStore aynı dosya için tek kopya olmalı.
        fun get(context: Context): WidgetConfigStore = instance ?: synchronized(this) {
            instance ?: WidgetConfigStore(
                PreferenceDataStoreFactory.create {
                    context.applicationContext.preferencesDataStoreFile("widgets")
                },
            ).also { instance = it }
        }
    }
}
