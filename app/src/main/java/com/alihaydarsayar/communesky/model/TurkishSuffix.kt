package com.alihaydarsayar.communesky.model

import java.util.Locale

/**
 * Türkçede bulunma eki (-de/-da/-te/-ta) son sesli harfe ve son sese göre değişir:
 * "20:00'de" (yirmi), "20:30'da" (otuz), "21:15'te" (on beş), "Pendik'te", "Merkez'de".
 * Saatlerde ek, sayının okunuşundaki son kelimeye göre seçilir.
 */
object TurkishSuffix {

    private val frontVowels = setOf('e', 'i', 'ö', 'ü')
    private val backVowels = setOf('a', 'ı', 'o', 'u')
    private val vowels = frontVowels + backVowels + setOf('â', 'î', 'û')

    /** Fıstıkçı şahap: bu seslerden sonra ek sertleşir (-te/-ta). */
    private val hardConsonants = setOf('f', 's', 't', 'k', 'ç', 'ş', 'h', 'p')

    private val turkish = Locale.forLanguageTag("tr")

    /** "Tuzla Merkez" → "Tuzla Merkez'de", "Pendik" → "Pendik'te", "Beşiktaş" → "Beşiktaş'ta". */
    fun locative(name: String): String {
        val word = name.trim()
        val lower = word.lowercase(turkish)
        val lastVowel = lower.lastOrNull { it in vowels }
        val vowel = if (lastVowel == null || lastVowel in frontVowels || lastVowel == 'î') 'e' else 'a'
        val last = lower.lastOrNull { it.isLetter() }
        val consonant = if (last != null && last in hardConsonants) 't' else 'd'
        return "$word'$consonant$vowel"
    }

    /**
     * Ekranda yazılan saatin ("20:00", "21:15") bulunma eki: "20:00'de", "21:15'te".
     * Dakika 00 ise saat okunur ("yirmide"), değilse dakika ("on beşte").
     */
    fun locativeTime(text: String): String {
        val numbers = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return text
        val spoken = if (numbers.size >= 2 && numbers.last() == 0) numbers[numbers.size - 2] else numbers.last()
        return "$text'${numberLocative(spoken)}"
    }

    /** 0–99 arası bir sayının okunuşuna göre bulunma eki. */
    fun numberLocative(number: Int): String {
        val n = number % 100
        if (n == 0) return "da" // sıfır
        val ones = n % 10
        val tens = n / 10
        return if (ones != 0) {
            when (ones) {
                1, 2, 7, 8 -> "de" // bir, iki, yedi, sekiz
                3, 4, 5 -> "te" // üç, dört, beş
                else -> "da" // altı, dokuz
            }
        } else {
            when (tens) {
                2, 5, 8 -> "de" // yirmi, elli, seksen
                4, 6 -> "ta" // kırk, altmış
                7 -> "te" // yetmiş
                else -> "da" // on, otuz, doksan
            }
        }
    }
}
