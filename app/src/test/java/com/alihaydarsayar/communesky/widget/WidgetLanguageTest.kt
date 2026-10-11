package com.alihaydarsayar.communesky.widget

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
}
