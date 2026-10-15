package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tarih, uygulama dili telefonun dilinden farklıysa normal metin olarak yazılır: Android'in saat
 * bileşeni tarihi ana ekran uygulamasının (telefonun) diliyle yazar.
 */
class WidgetLanguageTest {

    @Test
    fun `the date is plain text only when the app language differs from the phone`() {
        // Uygulama Türkçe, telefon İngilizce (ve tersi).
        assertTrue(WidgetLanguage.differs(Locale.forLanguageTag("tr"), Locale.forLanguageTag("en-US")))
        assertTrue(WidgetLanguage.differs(Locale.forLanguageTag("en"), Locale.forLanguageTag("tr-TR")))
        // Aynı dil: bölge farkı önemli değil; tarih sistemin bileşeniyle yazılır ve uygulama hiç uyanmaz.
        assertFalse(WidgetLanguage.differs(Locale.forLanguageTag("tr"), Locale.forLanguageTag("tr-TR")))
        assertFalse(WidgetLanguage.differs(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("en-US")))
    }

    @Test
    fun `the clock is 24-hour by default in Turkish and follows the phone in other languages`() {
        // Türkçede "9öö", "12ös" yazılmasın: kullanıcı seçmediyse 24 saat.
        org.junit.Assert.assertEquals(ClockFormat.H24, WidgetLanguage.defaultClockFormat("tr"))
        org.junit.Assert.assertEquals(ClockFormat.System, WidgetLanguage.defaultClockFormat("en"))
        org.junit.Assert.assertEquals(ClockFormat.System, WidgetLanguage.defaultClockFormat("de"))
    }

    /** Tercih dosyasını sayan sahte bağlam: ekranın bağlamı dosyaya her gidişte "bir okuma" sayılır. */
    private class CountingContext(private val values: Map<String, String>, private val app: Context? = null) : ContextWrapper(null) {
        var calls = 0

        override fun getApplicationContext(): Context = app ?: this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            calls++
            return FakePrefs(values)
        }
    }

    private class FakePrefs(private val values: Map<String, String>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    }

    private class FakeEditor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun commit() = true
        override fun apply() = Unit
    }

    @Test
    fun `changing the language or the clock format tells running widgets to redraw`() {
        // Ana ekrandaki widget oturumu dakikalarca canlı kalır; dili oturum başında bir kez okursa ayardan sonra eski dilde
        // kalıyordu. Oturum bu sayacı izler.
        WidgetLanguage.resetForTest()
        val app = CountingContext(emptyMap())
        val before = WidgetLanguage.version.value
        WidgetLanguage.remember(app, "tr")
        assertEquals(before + 1, WidgetLanguage.version.value)
        WidgetLanguage.setClockFormat(app, ClockFormat.H24)
        assertEquals(before + 2, WidgetLanguage.version.value)
        assertEquals("tr", WidgetLanguage.tag(app))
    }

    @Test
    fun `a redraw request reaches running widgets too`() {
        val before = WidgetLanguage.version.value
        WidgetLanguage.requestRedraw()
        assertEquals(before + 1, WidgetLanguage.version.value)
    }

    @Test
    fun `after the start-up warm-up a screen context never goes to the preferences file`() {
        // Ana iş parçacığında ilk ekran çizilirken dil ve saat biçimi sorulur; tercih dosyası açılışta arka planda okunmuş
        // olmalı, ekranın kendi bağlamı dosyaya gitmemeli (bağlamın ilk dosya yolu sorgusu bile diskten okur).
        WidgetLanguage.resetForTest()
        val values = mapOf("tag" to "tr", "clock_format" to "H24")
        val app = CountingContext(values)
        WidgetLanguage.warmUp(app)
        val screen = CountingContext(values, app)
        assertEquals("tr", WidgetLanguage.tag(screen))
        assertTrue(WidgetLanguage.is24Hour(screen))
        assertEquals("ekranın bağlamı tercih dosyasına gitti", 0, screen.calls)
    }
}
