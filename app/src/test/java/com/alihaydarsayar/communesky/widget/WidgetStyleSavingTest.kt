package com.alihaydarsayar.communesky.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * "Widget'ı kaydediyorum, tekrar açınca ayarlar korunmuyor" şikâyetinin testleri: her ayar kayıttan
 * aynen geri gelir; stil ya da arka plan değişince kullanıcının seçtikleri sıfırlanmaz; silinip
 * yeniden eklenen widget aynı türün son ayarıyla açılır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetStyleSavingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "widgets.preferences_pb") }

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    /** Her alanı varsayılandan farklı bir stil: bir alan kayıtta kaybolursa test bunu yakalar. */
    private val everything = WidgetStyle(
        style = WidgetStyleId.ClockField.name,
        background = BackgroundKind.Glass,
        transparency = 35,
        colorTheme = ColorTheme.Custom,
        textColor = TextColorMode.Dark,
        corners = CornerSize.Large,
        textSize = TextSize.Large,
        contents = setOf(WidgetContent.Weather, WidgetContent.RainAlert),
        touched = setOf(StyleField.Background, StyleField.Transparency, StyleField.ColorTheme),
        weight = WeightChoice.Bold,
        infoRow = InfoRow.Pill,
        legibility = Legibility.Scrim,
        accent = 0xFF5B9DFF.toInt(),
        customBackground = 0xFF102A43.toInt(),
        customText = 0xFFF2F3F5.toInt(),
        customSecondary = 0xFF8B8D98.toInt(),
        clockFont = ClockFont.Digits,
        hourColor = 0xFFE5484D.toInt(),
        minuteColor = 0xFF78C8BE.toInt(),
        lineColor = 0xFFFDE68A.toInt(),
        pillColor = 0xFF111214.toInt(),
        secondEffect = SecondEffect.Line,
        secondHand = false,
        secondSymbol = SecondSymbol.Circle,
        dialColor = 0xFFF4F2EC.toInt(),
        numeralColor = 0xFF111214.toInt(),
        handColor = 0xFF46A758.toInt(),
        highlighted = setOf(8, 13),
        ringCool = 0xFF6E56CF.toInt(),
        ringWarm = 0xFFD6409F.toInt(),
        ringRain = 0xFF46A758.toInt(),
        windowColor = 0xFF0F2233.toInt(),
        logo = false,
        showDate = false,
    )

    @Test
    fun `every setting differs from the default in the round trip sample`() {
        // Yeni bir alan eklenip buraya yazılmazsa bu test hatırlatır.
        val defaults = WidgetStyle()
        val names = listOf(
            "style", "background", "transparency", "colorTheme", "textColor", "corners", "textSize", "contents", "touched",
            "weight", "infoRow", "legibility", "accent", "customBackground", "customText", "customSecondary", "clockFont", "hourColor",
            "minuteColor", "lineColor", "pillColor", "secondEffect", "secondHand", "secondSymbol", "dialColor", "numeralColor",
            "handColor", "highlighted", "ringCool", "ringWarm", "ringRain", "windowColor", "logo", "showDate",
        )
        val a = everything.toString().removePrefix("WidgetStyle(").removeSuffix(")")
        val b = defaults.toString().removePrefix("WidgetStyle(").removeSuffix(")")
        for (name in names) {
            assertTrue("$name alanı yok", a.contains("$name="))
            val valueA = Regex("(?:^|, )$name=(.*?)(?:, [a-zA-Z]+=|$)").find(a)!!.groupValues[1]
            val valueB = Regex("(?:^|, )$name=(.*?)(?:, [a-zA-Z]+=|$)").find(b)!!.groupValues[1]
            assertFalse("$name varsayılan değerde kalmış", valueA == valueB)
        }
        assertEquals("Stilde testte olmayan bir alan var", names.size, Regex("(?:^|, )[a-zA-Z]+=").findAll(b).count())
    }

    @Test
    fun `every setting survives saving and reading back`() = runTest {
        val store = WidgetConfigStore(dataStore())
        val saved = WidgetConfig(WidgetPlace.Saved(7), everything)
        store.set(11, saved, WidgetKind.Clock)
        assertEquals(saved, store.get(11, WidgetKind.Clock))
        // Uygulama kapanıp açılınca: aynı dosyadan yeni bir depo.
        assertEquals(saved, WidgetConfigStore(dataStore()).let { it.decode(it.encode(saved), WidgetKind.Clock, "test") })
    }

    @Test
    fun `a default style also survives the round trip`() = runTest {
        val store = WidgetConfigStore(dataStore())
        for (style in WidgetStyleId.entries) {
            val config = WidgetConfig(WidgetPlace.Smart, style.defaultStyle())
            assertEquals(style.name, config, store.decode(store.encode(config), style.kind, "test"))
        }
    }

    @Test
    fun `changing the style keeps everything the user chose`() {
        val chosen = WidgetStyleId.ClockBig.defaultStyle()
            .withBackground(BackgroundKind.Glass)
            .withTransparency(40)
            .withColorTheme(ColorTheme.Ocean)
            .copy(
                textColor = TextColorMode.Dark,
                corners = CornerSize.Large,
                textSize = TextSize.Large,
                contents = setOf(WidgetContent.Weather),
                weight = WeightChoice.Bold,
                accent = 0xFF5B9DFF.toInt(),
                hourColor = 0xFFE5484D.toInt(),
                legibility = Legibility.Scrim,
            )
        val switched = chosen.withStyle(WidgetStyleId.ClockRadial)
        assertEquals(chosen.copy(style = WidgetStyleId.ClockRadial.name), switched)
        assertEquals(WidgetStyleId.ClockRadial, switched.styleId(WidgetKind.Clock))
    }

    @Test
    fun `a style suggests its look only for settings the user never touched`() {
        val untouched = WidgetStyleId.ClockRadial.defaultStyle()
        val big = untouched.withStyle(WidgetStyleId.ClockBig)
        // Büyük stil arka plansız önerilir; kullanıcı hiçbirine dokunmadı.
        assertEquals(BackgroundKind.Transparent, big.background)
        assertEquals(100, big.transparency)
        // Sadece renk temasına dokunan kullanıcı: tema kalır, arka plan stilin önerisi olur.
        val themed = untouched.withColorTheme(ColorTheme.Forest).withStyle(WidgetStyleId.ClockBig)
        assertEquals(ColorTheme.Forest, themed.colorTheme)
        assertEquals(BackgroundKind.Transparent, themed.background)
        // Geri dönünce de kullanıcının teması durur.
        assertEquals(ColorTheme.Forest, themed.withStyle(WidgetStyleId.ClockRadial).colorTheme)
        assertEquals(BackgroundKind.Solid, themed.withStyle(WidgetStyleId.ClockRadial).background)
    }

    @Test
    fun `changing the background keeps a transparency the user set`() {
        val style = WidgetStyleId.HomeSmart.defaultStyle().withTransparency(30)
        assertEquals(30, style.withBackground(BackgroundKind.Glass).transparency)
        assertEquals(30, style.withBackground(BackgroundKind.Transparent).transparency)
        assertEquals(30, style.withBackground(BackgroundKind.Glass).withBackground(BackgroundKind.Sky).transparency)
    }

    @Test
    fun `changing the background suggests a transparency when the user never set one`() {
        val style = WidgetStyleId.HomeSmart.defaultStyle()
        assertEquals(65, style.withBackground(BackgroundKind.Glass).transparency)
        assertEquals(100, style.withBackground(BackgroundKind.Transparent).transparency)
        assertEquals(0, style.withBackground(BackgroundKind.Transparent).withBackground(BackgroundKind.Solid).transparency)
    }

    @Test
    fun `glass is never more than 85 percent transparent`() {
        val style = WidgetStyleId.HomeSmart.defaultStyle().withTransparency(100)
        assertEquals(100, style.transparency)
        assertEquals(85, style.withBackground(BackgroundKind.Glass).transparency)
        assertEquals(85, WidgetStyle(background = BackgroundKind.Glass, transparency = 100).effectiveTransparency)
        assertEquals(85, WidgetStyle(background = BackgroundKind.Glass).withTransparency(95).transparency)
    }

    @Test
    fun `a new widget opens with the last settings saved for its kind`() = runTest {
        val store = WidgetConfigStore(dataStore())
        // Hiç kayıt yokken: varsayılan.
        assertNull(store.template(WidgetKind.Clock))
        assertEquals(WidgetConfig.default(WidgetKind.Clock), store.initial(1, WidgetKind.Clock))

        val saved = WidgetConfig(WidgetPlace.Home, everything)
        store.set(1, saved, WidgetKind.Clock)
        // Launcher "düzenle" göstermiyor: kullanıcı widget'ı silip yeniden ekliyor, yeni kimlik geliyor.
        store.remove(intArrayOf(1))
        assertFalse(store.exists(2))
        assertEquals(saved, store.initial(2, WidgetKind.Clock))
        // Başka türün şablonu etkilenmez.
        assertEquals(WidgetConfig.default(WidgetKind.Small), store.initial(3, WidgetKind.Small))
        // Kayıtlı bir widget kendi ayarıyla açılır, şablonla değil.
        val own = WidgetConfig(WidgetPlace.Device, WidgetStyleId.ClockBig.defaultStyle())
        store.set(4, own)
        assertEquals(own, store.initial(4, WidgetKind.Clock))
        assertEquals(saved, store.template(WidgetKind.Clock))
    }

    @Test
    fun `a broken record falls back to the default instead of crashing`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[stringPreferencesKey("widget_9_config")] = "{not json"
            it[stringPreferencesKey("template_Clock")] = "{\"place\":5}"
        }
        val store = WidgetConfigStore(dataStore)
        assertEquals(WidgetConfig.default(WidgetKind.Clock), store.get(9, WidgetKind.Clock))
        assertNull(store.template(WidgetKind.Clock))
        assertNull(store.decode("", WidgetKind.Clock, "test"))
    }

    @Test
    fun `unknown values in a record do not lose the rest`() = runTest {
        val store = WidgetConfigStore(dataStore())
        // İleride kaldırılan bir seçenek ya da bilinmeyen bir alan: geri kalan ayarlar okunur.
        val raw = """{"place":"home","version":2,"style":{"style":"ClockBig","background":"Glass","transparency":40,"colorTheme":"Neon","corners":"Large","future":true}}"""
        val config = store.decode(raw, WidgetKind.Clock, "test")!!
        assertEquals(WidgetPlace.Home, config.place)
        assertEquals(BackgroundKind.Glass, config.style.background)
        assertEquals(40, config.style.transparency)
        assertEquals(CornerSize.Large, config.style.corners)
        assertEquals(WidgetStyle().colorTheme, config.style.colorTheme)
    }

    @Test
    fun `settings saved by version 1_3 keep their look and their choices`() = runTest {
        val store = WidgetConfigStore(dataStore())
        // 1.3'ün yazdığı biçim: sürüm alanı yok, varsayılan değerler yazılmamış (Gökyüzü, %0).
        val untouched = store.decode("""{"place":"smart","style":{"style":"ClockBig","background":"Transparent","transparency":100}}""", WidgetKind.Clock, "test")!!
        assertEquals(BackgroundKind.Transparent, untouched.style.background)
        assertEquals(ColorTheme.Sky, untouched.style.colorTheme)
        assertEquals(emptySet<StyleField>(), untouched.style.touched)

        val chosen = store.decode("""{"place":"smart","style":{"style":"ClockBig","background":"Glass","transparency":30,"colorTheme":"Ocean"}}""", WidgetKind.Clock, "test")!!
        assertEquals(setOf(StyleField.Background, StyleField.Transparency, StyleField.ColorTheme), chosen.style.touched)
        // Stil değişince bu seçimler artık korunur.
        val switched = chosen.style.withStyle(WidgetStyleId.ClockSide)
        assertEquals(BackgroundKind.Glass, switched.background)
        assertEquals(30, switched.transparency)
        assertEquals(ColorTheme.Ocean, switched.colorTheme)

        // 1.3'te hiç alan yazılmamış bir kayıt (Ev ve konum, gökyüzü): görünüm aynı kalır.
        val sky = store.decode("""{"place":"smart","style":{"style":"HomeSmart"}}""", WidgetKind.HomeLocation, "test")!!
        assertEquals(BackgroundKind.Sky, sky.style.background)
        assertEquals(ColorTheme.Sky, sky.style.colorTheme)
        assertEquals(0, sky.style.transparency)
    }

    @Test
    fun `the seconds effect falls back to a single line when the background is see-through`() {
        val solid = WidgetStyleId.ClockRadial.defaultStyle()
        assertEquals(RadialSeconds.StencilTail, radialSeconds(solid, sdk = 34))
        assertEquals(RadialSeconds.StencilLine, radialSeconds(solid.copy(secondEffect = SecondEffect.Line), sdk = 34))
        assertEquals(RadialSeconds.None, radialSeconds(solid.copy(secondEffect = SecondEffect.Off), sdk = 34))
        // Cam, saydam ve yarı saydam arka plan: şablon kullanılamaz, tek çizgi otomatik gelir.
        assertEquals(RadialSeconds.StripLine, radialSeconds(solid.withBackground(BackgroundKind.Glass), sdk = 34))
        assertEquals(RadialSeconds.StripLine, radialSeconds(solid.withBackground(BackgroundKind.Transparent), sdk = 34))
        assertEquals(RadialSeconds.StripLine, radialSeconds(solid.withTransparency(20), sdk = 34))
        assertEquals(RadialSeconds.None, radialSeconds(solid.withBackground(BackgroundKind.Glass).copy(secondEffect = SecondEffect.Off), sdk = 34))
        // Android 12 öncesinde saniye kolu yok: çizgiler sabit.
        assertEquals(RadialSeconds.None, radialSeconds(solid, sdk = 30))
    }

    @Test
    fun `the SS clock starts classic and its ready-made looks set all four colors`() {
        val style = WidgetStyleId.ClockSS.defaultStyle()
        assertEquals(SsClock.Preset.Classic, SsClock.preset(style))
        assertEquals(0xFFD93A3F.toInt(), SsClock.look(style).accent)
        assertTrue(style.logo && style.showDate && style.secondHand)

        val night = SsClock.apply(style, SsClock.Preset.NightBlue)
        assertEquals(SsClock.Preset.NightBlue, SsClock.preset(night))
        assertEquals(SsClock.Look(0xFF0F2233.toInt(), 0xFFEDE6D6.toInt(), 0xFFE8B04B.toInt(), 0xFF0F2233.toInt()), SsClock.look(night))
        // Kullanıcı bir rengi değiştirince artık hazır temalardan biri değildir, ama diğer renkler durur.
        val custom = night.copy(accent = 0xFF30C85A.toInt())
        assertNull(SsClock.preset(custom))
        assertEquals(0xFF0F2233.toInt(), SsClock.look(custom).dial)
        // Stil değişip geri gelince renkler korunur.
        assertEquals(custom, custom.withStyle(WidgetStyleId.ClockAnalog).withStyle(WidgetStyleId.ClockSS))
    }

    @Test
    fun `the SS clock switches each weather part on its own`() {
        val parts = WidgetStyleId.ClockSS.contents
        assertEquals(
            listOf(WidgetContent.WeatherIcon, WidgetContent.Temperature, WidgetContent.Condition, WidgetContent.HighLow, WidgetContent.PlaceName, WidgetContent.RainAlert),
            parts,
        )
        val style = WidgetStyleId.ClockSS.defaultStyle()
        assertTrue(parts.all { style.shows(WidgetKind.Clock, it) })
        // Diğer saat stillerinde bu iki anahtar görünmez.
        assertFalse(WidgetContent.WeatherIcon in WidgetStyleId.ClockBig.contents)
        assertFalse(WidgetContent.Temperature in WidgetStyleId.ClockBig.contents)
        assertTrue(WidgetContent.Weather in WidgetStyleId.ClockBig.contents)
    }
}
