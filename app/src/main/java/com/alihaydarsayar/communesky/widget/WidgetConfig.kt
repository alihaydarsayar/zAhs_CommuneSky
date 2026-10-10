package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.util.Log
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
enum class BackgroundKind(@param:StringRes val labelRes: Int, val defaultTransparency: Int, val maxTransparency: Int = 100) {
    /** Renk temasının gradyanı; Gökyüzü temasında hava ve saate göre değişir. */
    Sky(R.string.widget_background_sky, 0),

    /** Buzlu cam: duvar kâğıdı hafifçe görünür. Saydamlık en fazla %85: cam hiç kaybolmaz. */
    Glass(R.string.widget_background_glass, 65, maxTransparency = 85),

    /** Arka plan yok; saydamlık azaltılırsa hafif bir koyu katman gelir. */
    Transparent(R.string.widget_background_transparent, 100),

    /** Renk temasının tek rengi. */
    Solid(R.string.widget_background_solid, 0),
}

/** Renk teması: gradyanın, düz rengin ve vurguların renkleri. */
@Serializable
enum class ColorTheme(@param:StringRes val labelRes: Int) {
    /** "Gece": 1.4'ün ortak görünümü, koyu zemin (#121316), açık yazı. */
    Dark(R.string.widget_theme_dark),

    /** "Kâğıt": açık, sıcak zemin (#F2EEE6), koyu yazı. */
    Paper(R.string.widget_theme_paper),

    /** "Mercan": koyu zemin, mercan kırmızısı vurgu ve saat rakamları. */
    Coral(R.string.widget_theme_coral),

    /** Hava ve günün saatine göre değişen renkler. */
    Sky(R.string.widget_theme_sky),

    /** Android 12+ Material You: duvar kâğıdından gelen renkler. */
    Wallpaper(R.string.widget_theme_wallpaper),
    Sunset(R.string.widget_theme_sunset),
    Ocean(R.string.widget_theme_ocean),
    Forest(R.string.widget_theme_forest),
    Night(R.string.widget_theme_night),
    Pastel(R.string.widget_theme_pastel),

    /** Zemin ve yazı renklerini kullanıcı seçer. */
    Custom(R.string.widget_theme_custom),
    ;

    companion object {
        /** Ayar ekranında gösterilen hazır temalar. Diğerleri eski sürümlerden kalan kayıtlar için durur. */
        val featured: List<ColorTheme> get() = listOf(Dark, Paper, Coral, Forest, Wallpaper, Custom)

        /** "Gökyüzü" arka planında hava ve saate göre değişen gökyüzü renklerini kullanan temalar. */
        val skyDriven: Set<ColorTheme> get() = setOf(Dark, Paper, Coral, Custom, Sky)
    }
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

@Serializable
enum class WeightChoice(@param:StringRes val labelRes: Int) {
    Thin(R.string.widget_weight_thin),
    Normal(R.string.widget_weight_normal),
    Bold(R.string.widget_weight_bold),
}

/** Yağmur uyarısı gibi bilgi satırları düz mü, hap içinde mi? */
@Serializable
enum class InfoRow(@param:StringRes val labelRes: Int) {
    Plain(R.string.widget_info_plain),
    Pill(R.string.widget_info_pill),
}

/**
 * "Yazının arkası": yazı duvar kâğıdının üstündeyken (cam ve saydam) okunurluğu koruma yolu.
 * İnce gölge: %30 siyah, 2 dp yayılma, 1 dp aşağı. Belirgin gölge: %45 siyah, 3 dp yayılma.
 * Perde: yazının arkasında kenarları yumuşak, yarı saydam katman.
 */
@Serializable
enum class Legibility(@param:StringRes val labelRes: Int) {
    Off(R.string.widget_legibility_off),
    Shadow(R.string.widget_legibility_shadow),
    Strong(R.string.widget_legibility_strong),
    Scrim(R.string.widget_legibility_scrim),
}

/**
 * Saat rakamlarının yazı tipi. Ana ekrandaki widget'larda yalnızca sistemin yazı tipleri
 * kullanılabilir (Android, widget'ı çizen launcher'a uygulamanın kendi yazı tipi dosyalarını
 * yükletmez); bu yüzden seçenekler sistemin normal ve dar rakamlarıdır.
 */
@Serializable
enum class ClockFont(@param:StringRes val labelRes: Int, @param:StringRes val shortLabelRes: Int) {
    System(R.string.widget_font_system, R.string.widget_clock_font_short_system),

    /** Dar ve kalın rakamlar. */
    Digits(R.string.widget_clock_font_digits, R.string.widget_clock_font_short_digits),

    /** Saat dar ve kalın, dakika içi boş (dolu zeminde) ya da dar ve ince. */
    DigitsOutline(R.string.widget_clock_font_digits_outline, R.string.widget_clock_font_short_outline),
}

/** Işınsal saatte çizgilerin saniyeyle birlikte parlaması. */
@Serializable
enum class SecondEffect(@param:StringRes val labelRes: Int) {
    Off(R.string.widget_second_off),
    Line(R.string.widget_second_line),
    Tail(R.string.widget_second_tail),
}

/** Analog saatte saniye kolunun kuyruğundaki simge. */
@Serializable
enum class SecondSymbol(@param:StringRes val labelRes: Int) {
    None(R.string.widget_symbol_none),
    Star(R.string.widget_symbol_star),
    Circle(R.string.widget_symbol_circle),
}

/** Stilin önerdiği, ama kullanıcı elle değiştirince artık stile bağlı olmayan ayarlar. */
@Serializable
enum class StyleField { Background, Transparency, ColorTheme }

/**
 * Bütün widget'ların ortak kişiselleştirme modeli. Her widget aynı yapıyı kullanır; içerik
 * anahtarlarından hangilerinin anlamlı olduğu türe bağlıdır ([WidgetKind.contents]).
 *
 * Renkler ARGB tam sayı olarak saklanır; null "varsayılan" demektir.
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
    /** Kullanıcının elle değiştirdiği alanlar; stil değişince bunlar korunur, diğerleri stilin önerisini alır. */
    val touched: Set<StyleField> = emptySet(),
    val weight: WeightChoice = WeightChoice.Normal,
    val infoRow: InfoRow = InfoRow.Plain,
    val legibility: Legibility = Legibility.Shadow,
    /** Vurgu rengi: saniye çizgisi, gün adı, "Bugün", "Şimdi" ve uyarılar. */
    val accent: Int? = null,
    /** "Özel" renk temasının renkleri. */
    val customBackground: Int? = null,
    val customText: Int? = null,
    val customSecondary: Int? = null,
    /** Saat yazı tipi; null ise stilin kendi yazı tipi. */
    val clockFont: ClockFont? = null,
    val hourColor: Int? = null,
    val minuteColor: Int? = null,
    /** Işınsal saat: çizgi ve hava hapı renkleri, saniye efekti. */
    val lineColor: Int? = null,
    val pillColor: Int? = null,
    val secondEffect: SecondEffect = SecondEffect.Tail,
    /** Analog saatler: saniye kolu, kuyruğundaki simge ve renkler. */
    val secondHand: Boolean = true,
    val secondSymbol: SecondSymbol = SecondSymbol.Star,
    val dialColor: Int? = null,
    val numeralColor: Int? = null,
    val handColor: Int? = null,
    /** Saha kadranında vurgu renginde gösterilen rakamlar (1–24). */
    val highlighted: Set<Int> = emptySet(),
    /** Hava halkası: serin, sıcak ve yağışlı saatlerin renkleri. */
    val ringCool: Int? = null,
    val ringWarm: Int? = null,
    val ringRain: Int? = null,
    /** SS saati: tarih penceresinin rengi, mühür (logo) ve tarih penceresi. */
    val windowColor: Int? = null,
    val logo: Boolean = true,
    val showDate: Boolean = true,
) {
    fun styleId(kind: WidgetKind): WidgetStyleId =
        WidgetStyleId.entries.firstOrNull { it.name == style && it.kind == kind } ?: kind.defaultStyle

    fun shows(kind: WidgetKind, content: WidgetContent): Boolean =
        content in (contents ?: kind.defaultContents)

    /** Saydamlık, arka planın izin verdiği aralıkta (camda en fazla %85). */
    val effectiveTransparency: Int get() = transparency.coerceIn(0, background.maxTransparency)

    /**
     * Stili değiştirir. Kullanıcının seçtiği her şey korunur; stilin önerdiği arka plan, saydamlık
     * ve renk teması yalnızca kullanıcı o ayara hiç dokunmadıysa uygulanır.
     */
    fun withStyle(id: WidgetStyleId): WidgetStyle {
        val newBackground = if (StyleField.Background in touched) background else id.background
        return copy(
            style = id.name,
            background = newBackground,
            transparency = (if (StyleField.Transparency in touched) transparency else newBackground.defaultTransparency)
                .coerceIn(0, newBackground.maxTransparency),
            colorTheme = if (StyleField.ColorTheme in touched) colorTheme else id.colorTheme,
        )
    }

    /** Arka plan türünü değiştirir; saydamlık elle ayarlandıysa korunur, yoksa türün önerisi gelir. */
    fun withBackground(kind: BackgroundKind): WidgetStyle = copy(
        background = kind,
        transparency = (if (StyleField.Transparency in touched) transparency else kind.defaultTransparency)
            .coerceIn(0, kind.maxTransparency),
        touched = touched + StyleField.Background,
    )

    fun withTransparency(value: Int): WidgetStyle = copy(
        transparency = value.coerceIn(0, background.maxTransparency),
        touched = touched + StyleField.Transparency,
    )

    fun withColorTheme(theme: ColorTheme): WidgetStyle = copy(colorTheme = theme, touched = touched + StyleField.ColorTheme)

    /**
     * 1.3'ten kalan kayıtlarda [touched] yoktur: o sürümde stilin önerdiği görünümden farklı olan
     * alanlar kullanıcının seçimi sayılır.
     */
    fun migratedFrom13(kind: WidgetKind): WidgetStyle {
        val (legacyBackground, legacyTheme) = LegacyLooks.of(styleId(kind))
        return copy(
            touched = buildSet {
                if (background != legacyBackground) add(StyleField.Background)
                if (transparency != legacyBackground.defaultTransparency) add(StyleField.Transparency)
                if (colorTheme != legacyTheme) add(StyleField.ColorTheme)
            },
        )
    }
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
        // Varsayılanlar da yazılır: ileride bir varsayılan değişirse eski kayıtlar etkilenmez.
        encodeDefaults = true
    }

    suspend fun get(appWidgetId: Int, kind: WidgetKind): WidgetConfig = observe(appWidgetId, kind).first()

    /** Bu widget için kaydedilmiş bir ayar var mı? (Yoksa ekleme ekranı "Widget'ı ekle" der.) */
    suspend fun exists(appWidgetId: Int): Boolean = dataStore.data.first().let {
        it[configKey(appWidgetId)] != null || it[legacyPlaceKey(appWidgetId)] != null
    }

    /**
     * Ekleme ekranının açılış ayarı: widget'ın kendi kaydı; yoksa aynı türden en son kaydedilen
     * ayar (şablon); o da yoksa varsayılan. "Widget'ı düzenle" göstermeyen launcher'larda widget
     * silinip yeniden eklenince ayarlar böylece geri gelir.
     */
    suspend fun initial(appWidgetId: Int, kind: WidgetKind): WidgetConfig {
        val prefs = dataStore.data.first()
        if (prefs[configKey(appWidgetId)] != null || prefs[legacyPlaceKey(appWidgetId)] != null) return read(prefs, appWidgetId, kind)
        return template(kind) ?: WidgetConfig.default(kind)
    }

    /** Bu türde en son kaydedilen ayar; hiç kaydedilmediyse null. */
    suspend fun template(kind: WidgetKind): WidgetConfig? =
        dataStore.data.first()[templateKey(kind)]?.let { decode(it, kind, "template ${kind.name}") }

    /** Ayar değiştikçe yeni değeri yayar; widget bunu dinleyerek hemen yeniden çizilir. */
    fun observe(appWidgetId: Int, kind: WidgetKind): Flow<WidgetConfig> = dataStore.data
        .map { prefs -> read(prefs, appWidgetId, kind) }
        .distinctUntilChanged()

    /**
     * Ayarı kaydeder; dönünce yazma diske ulaşmıştır. [kind] verilirse aynı ayar o türün şablonu
     * olarak da saklanır.
     */
    suspend fun set(appWidgetId: Int, config: WidgetConfig, kind: WidgetKind? = null) {
        val encoded = encode(config)
        dataStore.edit {
            it[configKey(appWidgetId)] = encoded
            if (kind != null) it[templateKey(kind)] = encoded
            it.remove(legacyPlaceKey(appWidgetId))
            it.remove(legacyBackgroundKey(appWidgetId))
        }
    }

    /** Widget ana ekrandan kaldırılınca ayarı da silinir (türün şablonu kalır). */
    suspend fun remove(appWidgetIds: IntArray) {
        dataStore.edit { prefs ->
            appWidgetIds.forEach {
                prefs.remove(configKey(it))
                prefs.remove(legacyPlaceKey(it))
                prefs.remove(legacyBackgroundKey(it))
            }
        }
    }

    /** Renk seçicide son kullanılan renkler (en yenisi başta); bütün widget'lar için ortak. */
    suspend fun recentColors(): List<Int> =
        dataStore.data.first()[RECENT_COLORS_KEY].orEmpty().split(',').mapNotNull { it.toIntOrNull() }.take(RECENT_COLORS)

    suspend fun setRecentColors(colors: List<Int>) {
        dataStore.edit { it[RECENT_COLORS_KEY] = colors.take(RECENT_COLORS).joinToString(",") }
    }

    /** Önizlemeler en son hangi sürümde yayınlandı (Android 15+ seçici önizlemeleri). */
    suspend fun previewsPublishedVersion(): Int? = dataStore.data.first()[PREVIEWS_VERSION]

    suspend fun setPreviewsPublishedVersion(version: Int) {
        dataStore.edit { it[PREVIEWS_VERSION] = version }
    }

    private fun read(prefs: Preferences, id: Int, kind: WidgetKind): WidgetConfig {
        prefs[configKey(id)]?.let { raw -> decode(raw, kind, "widget $id")?.let { return it } }
        // 1.2'den kalan ayar: yer ve üç arka plandan biri. Görünüm korunur, yeni seçenekler varsayılan.
        // Hiç kaydı olmayan widget (launcher ayar ekranını açmadıysa): aynı türün son ayarı, yoksa varsayılan.
        val legacyPlace = prefs[legacyPlaceKey(id)]
            ?: return prefs[templateKey(kind)]?.let { decode(it, kind, "template ${kind.name}") } ?: WidgetConfig.default(kind)
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
                touched = setOf(StyleField.Background, StyleField.ColorTheme),
            ),
        )
    }

    internal fun encode(config: WidgetConfig): String =
        json.encodeToString(StoredConfig.serializer(), StoredConfig(config.place.encode(), config.style, CURRENT_VERSION))

    /** Bozuk kayıt sessizce yutulmaz: loglanır ve null döner (çağıran varsayılana düşer). */
    internal fun decode(raw: String, kind: WidgetKind, what: String): WidgetConfig? =
        runCatching { json.decodeFromString(StoredConfig.serializer(), raw) }
            .onFailure { Log.w(TAG, "Widget ayarı okunamadı ($what), varsayılana dönülüyor: $raw", it) }
            .getOrNull()
            ?.let { stored ->
                val style = if (stored.version < CURRENT_VERSION) {
                    Log.i(TAG, "Eski biçimdeki widget ayarı yükseltildi ($what)")
                    stored.style.migratedFrom13(kind)
                } else {
                    stored.style
                }
                WidgetConfig(WidgetPlace.decode(stored.place), style)
            }

    /** [version]: 1 = 1.3 (alan yoksa), 2 = 1.4 (kullanıcının dokunduğu alanlar ayrı tutulur). */
    @Serializable
    internal data class StoredConfig(val place: String, val style: WidgetStyle, val version: Int = 1)

    private fun configKey(id: Int) = stringPreferencesKey("widget_${id}_config")
    private fun templateKey(kind: WidgetKind) = stringPreferencesKey("template_${kind.name}")
    private fun legacyPlaceKey(id: Int) = stringPreferencesKey("widget_${id}_place")
    private fun legacyBackgroundKey(id: Int) = stringPreferencesKey("widget_${id}_background")

    companion object {
        private const val TAG = "WidgetConfig"
        private const val CURRENT_VERSION = 2

        /** Renk seçicide hatırlanan son renk sayısı. */
        const val RECENT_COLORS = 6
        private val RECENT_COLORS_KEY = stringPreferencesKey("recent_colors")
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
