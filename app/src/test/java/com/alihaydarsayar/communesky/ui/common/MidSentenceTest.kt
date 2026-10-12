package com.alihaydarsayar.communesky.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Göreli zaman cümlenin içinde yazılır: Fransızcada "Il y a 1 minute" küçük harfle başlamalı. */
class MidSentenceTest {

    @Test
    fun `french relative time starts with a small letter`() {
        assertEquals("il y a 1 minute", midSentence("Il y a 1 minute", Locale.FRENCH))
    }

    @Test
    fun `turkish and english texts stay as they are`() {
        assertEquals("5 dakika önce", midSentence("5 dakika önce", Locale.forLanguageTag("tr")))
        assertEquals("7 minutes ago", midSentence("7 minutes ago", Locale.ENGLISH))
        // Türkçede büyük İ, noktalı küçük i olur.
        assertEquals("iki dakika önce", midSentence("İki dakika önce", Locale.forLanguageTag("tr")))
    }
}
