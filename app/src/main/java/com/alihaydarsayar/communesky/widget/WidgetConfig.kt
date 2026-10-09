package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.alihaydarsayar.communesky.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Widget'ın hangi yerin havasını gösterdiği. */
sealed interface WidgetPlace {
    /** Ev ve konum (akıllı): evdeyken ya da yakındayken Ev, uzaktayken bulunduğun yer. */
    data object Smart : WidgetPlace

    /** Cihazın konumu (izin yoksa yedek şehir). */
    data object Device : WidgetPlace

    /** "Ev" olarak işaretlenen yer; Ev değişirse widget da onu izler. Ev yoksa cihaz konumu. */
    data object Home : WidgetPlace

    /** Belirli bir kayıtlı yer; silinirse cihaz konumuna döner. */
    data class Saved(val placeId: Long) : WidgetPlace

    fun encode(): String = when (this) {
        Smart -> "smart"
        Device -> "device"
        Home -> "home"
        is Saved -> "place:$placeId"
    }

    companion object {
        fun decode(value: String?): WidgetPlace = when {
            value == "smart" -> Smart
            value == "home" -> Home
            value?.startsWith("place:") == true ->
                value.removePrefix("place:").toLongOrNull()?.let(::Saved) ?: Device
            else -> Device
        }
    }
}

/** Arka plan türü. Saydamlık kaydırıcısı hepsinde çalışır. */
@Serializable
enum class BackgroundKind(@param:StringRes val labelRes: Int, val defaultTransparency: Int) {
    /** Renk temasının gradyanı; Gökyüzü temasında hava ve saate göre değişir. */
    Sky(R.string.widget_background_sky, 0),

    /** Buzlu cam: duvar kâğıdı hafifçe görünür. */
    Glass(R.string.widget_background_glass, 65),

    /** Arka plan yok; saydamlık azaltılırsa hafif bir koyu katman gelir. */
    Transparent(R.string.widget_background_transparent, 100),

    /** Renk temasının tek rengi. */
    Solid(R.string.widget_background_solid, 0),
}

/** Renk teması: gradyanın, düz rengin ve vurguların renkleri. */
@Serializable
enum class ColorTheme(@param:StringRes val labelRes: Int) {
    /** Hava ve günün saatine göre değişen renkler. */
    Sky(R.string.widget_theme_sky),

    /** Android 12+ Material You: duvar kâğıdından gelen renkler. */
    Wallpaper(R.string.widget_theme_wallpaper),
    Sunset(R.string.widget_theme_sunset),
    Ocean(R.string.widget_theme_ocean),
    Forest(R.string.widget_theme_forest),
    Night(R.string.widget_theme_night),
    Pastel(R.string.widget_theme_pastel),
}

@Serializable
enum class TextColorMode(@param:StringRes val labelRes: Int) {
    /** Arka plana ve duvar kâğıdına göre okunur olan seçilir. */
    Auto(R.string.widget_text_auto),
    Light(R.string.widget_text_light),
    Dark(R.string.widget_text_dark),
}

@Serializable
enum class CornerSize(@param:StringRes val labelRes: Int, val radius: Dp) {
    Small(R.string.widget_corners_small, 16.dp),
    Medium(R.string.widget_corners_medium, 28.dp),
    Large(R.string.widget_corners_large, 40.dp),
}

@Serializable
enum class TextSize(@param:StringRes val labelRes: Int, val scale: Float) {
    Small(R.string.widget_text_size_small, 0.88f),
    Normal(R.string.widget_text_size_normal, 1f),
    Large(R.string.widget_text_size_large, 1.14f),
}

/**
 * Bütün widget'ların ortak kişiselleştirme modeli. Her widget aynı yapıyı kullanır; içerik
 * anahtarlarından hangilerinin anlamlı olduğu türe bağlıdır ([WidgetKind.contents]).
 */
@Serializable
data class WidgetStyle(
    /** [WidgetStyleId] adı; null ya da bilinmiyorsa türün ilk stili. */
    val style: String? = null,
    val background: BackgroundKind = BackgroundKind.Sky,
    /** 0 = tam dolu arka plan, 100 = hiç arka plan yok. */
    val transparency: Int = 0,
    val colorTheme: ColorTheme = ColorTheme.Sky,
    val textColor: TextColorMode = TextColorMode.Auto,
    val corners: CornerSize = CornerSize.Medium,
    val textSize: TextSize = TextSize.Normal,
    /** Açık içerikler; null ise türün varsayılanları. */
    val contents: Set<WidgetContent>? = null,
) {
    fun styleId(kind: WidgetKind): WidgetStyleId =
        WidgetStyleId.entries.firstOrNull { it.name == style && it.kind == kind } ?: kind.defaultStyle

    fun shows(kind: WidgetKind, content: WidgetContent): Boolean =
        content in (contents ?: kind.defaultContents)
}

data class WidgetConfig(
    val place: WidgetPlace = WidgetPlace.Smart,
    val style: WidgetStyle = WidgetStyle(),
) {
    companion object {
        fun default(kind: WidgetKind): WidgetConfig = WidgetConfig(WidgetPlace.Smart, kind.defaultStyle.defaultStyle())
    }
}

/**
 * Her widget'ın ayarı, Android'in verdiği widget kimliğiyle (appWidgetId) saklanır.
 * Uygulamanın Hilt kabına bağlı değil; widget sınıfları ve ayar ekranı doğrudan kullanır.
 */
class WidgetConfigStore internal constructor(private val dataStore: DataStore<Preferences>) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun get(appWidgetId: Int, kind: WidgetKind): WidgetConfig = observe(appWidgetId, kind).first()

    /** Bu widget için kaydedilmiş bir ayar var mı? (Yoksa ekleme ekranı "Widget'ı ekle" der.) */
    suspend fun exists(appWidgetId: Int): Boolean = dataStore.data.first().let {
        it[configKey(appWidgetId)] != null || it[legacyPlaceKey(appWidgetId)] != null
    }

    /** Ayar değiştikçe yeni değeri yayar; widget bunu dinleyerek hemen yeniden çizilir. */
    fun observe(appWidgetId: Int, kind: WidgetKind): Flow<WidgetConfig> = dataStore.data
        .map { prefs -> read(prefs, appWidgetId, kind) }
        .distinctUntilChanged()

    suspend fun set(appWidgetId: Int, config: WidgetConfig) {
        dataStore.edit {
            it[configKey(appWidgetId)] = json.encodeToString(StoredConfig.serializer(), StoredConfig(config.place.encode(), config.style))
            it.remove(legacyPlaceKey(appWidgetId))
            it.remove(legacyBackgroundKey(appWidgetId))
        }
    }

    /** Widget ana ekrandan kaldırılınca ayarı da silinir. */
    suspend fun remove(appWidgetIds: IntArray) {
        dataStore.edit { prefs ->
            appWidgetIds.forEach {
                prefs.remove(configKey(it))
                prefs.remove(legacyPlaceKey(it))
                prefs.remove(legacyBackgroundKey(it))
            }
        }
    }

    /** Önizlemeler en son hangi sürümde yayınlandı (Android 15+ seçici önizlemeleri). */
    suspend fun previewsPublishedVersion(): Int? = dataStore.data.first()[PREVIEWS_VERSION]

    suspend fun setPreviewsPublishedVersion(version: Int) {
        dataStore.edit { it[PREVIEWS_VERSION] = version }
    }

    private fun read(prefs: Preferences, id: Int, kind: WidgetKind): WidgetConfig {
        prefs[configKey(id)]?.let { raw ->
            runCatching { json.decodeFromString(StoredConfig.serializer(), raw) }.getOrNull()?.let {
                return WidgetConfig(WidgetPlace.decode(it.place), it.style)
            }
        }
        // 1.2'den kalan ayar: yer ve üç arka plandan biri. Görünüm korunur, yeni seçenekler varsayılan.
        val legacyPlace = prefs[legacyPlaceKey(id)] ?: return WidgetConfig.default(kind)
        val background = when (prefs[legacyBackgroundKey(id)]) {
            "Translucent" -> BackgroundKind.Glass
            "Transparent" -> BackgroundKind.Transparent
            else -> BackgroundKind.Sky
        }
        return WidgetConfig(
            place = WidgetPlace.decode(legacyPlace),
            style = kind.defaultStyle.defaultStyle().copy(
                background = background,
                transparency = background.defaultTransparency,
                colorTheme = ColorTheme.Sky,
            ),
        )
    }

    @Serializable
    private data class StoredConfig(val place: String, val style: WidgetStyle)

    private fun configKey(id: Int) = stringPreferencesKey("widget_${id}_config")
    private fun legacyPlaceKey(id: Int) = stringPreferencesKey("widget_${id}_place")
    private fun legacyBackgroundKey(id: Int) = stringPreferencesKey("widget_${id}_background")

    companion object {
        private val PREVIEWS_VERSION = intPreferencesKey("previews_published_version")

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
