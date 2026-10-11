package com.alihaydarsayar.communesky.testing

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.today.Strings
import java.io.File
import java.util.Locale

/**
 * Testlerde gerçek metinler: strings.xml dosyası okunur, kaynak kimliği adıyla eşlenir. Böylece cümle kuran
 * kod, telefonda görünecek Türkçe ve İngilizce metinlerle sınanır.
 */
class TestStrings(language: String) : Strings {
    override val locale: Locale = Locale.forLanguageTag(language)

    private val names: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    private val texts: Map<String, String> = read("values") + if (language == "en") emptyMap() else read("values-$language")

    private fun read(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
        return Regex("<string name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>").findAll(file.readText()).associate { match ->
            val text = match.groupValues[2].replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&").replace("&lt;", "<")
            match.groupValues[1] to text
        }
    }

    override fun get(id: Int, vararg args: Any): String {
        val text = texts.getValue(names.getValue(id))
        return if (args.isEmpty()) text.replace("%%", "%") else String.format(locale, text, *args)
    }

    companion object {
        val turkish by lazy { TestStrings("tr") }
        val english by lazy { TestStrings("en") }
    }
}
