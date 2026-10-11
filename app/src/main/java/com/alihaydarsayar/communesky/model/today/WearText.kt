package com.alihaydarsayar.communesky.model.today

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R
import java.util.Locale

/**
 * Metin kaynağı. Uygulamada Android'in kaynakları ([AndroidStrings]); testlerde strings.xml dosyaları okunur.
 * Böylece cümle kuran kod saf kalır ve iki dilde de test edilir.
 */
interface Strings {
    val locale: Locale

    fun get(@StringRes id: Int, vararg args: Any): String
}

class AndroidStrings(private val context: Context) : Strings {
    override val locale: Locale get() = context.resources.configuration.locales[0]

    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
}

/** "a", "a ve b", "a, b ve c". */
fun Strings.list(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> get(R.string.list_last, items.dropLast(1).joinToString(", "), items.last())
}

/** Cümle ortasına girecek parçanın ilk harfini dile uygun küçültür ("İnce" → "ince"). */
private fun Strings.lowerFirst(text: String): String = text.replaceFirstChar { it.lowercase(locale) }

/** "Ne giysem" metinleri ve ikonları. */
object WearText {
    /** Tam öneri: "Kazak ya da ince mont giy". */
    @StringRes
    fun clause(layer: Layer): Int = when (layer) {
        Layer.Light -> R.string.wear_clause_light
        Layer.TShirt -> R.string.wear_clause_tshirt
        Layer.LongSleeve -> R.string.wear_clause_long_sleeve
        Layer.Sweater -> R.string.wear_clause_sweater
        Layer.Coat -> R.string.wear_clause_coat
        Layer.HeavyCoat -> R.string.wear_clause_heavy_coat
        Layer.FullWinter -> R.string.wear_clause_full_winter
    }

    /** Kısa ad: "Kazak ya da ince mont". */
    @StringRes
    fun name(layer: Layer): Int = when (layer) {
        Layer.Light -> R.string.wear_name_light
        Layer.TShirt -> R.string.wear_name_tshirt
        Layer.LongSleeve -> R.string.wear_name_long_sleeve
        Layer.Sweater -> R.string.wear_name_sweater
        Layer.Coat -> R.string.wear_name_coat
        Layer.HeavyCoat -> R.string.wear_name_heavy_coat
        Layer.FullWinter -> R.string.wear_name_full_winter
    }

    /** Yanına alınıp sonra çıkarılabilecek üst giysi ("ince bir mont"); ince katmanlarda yoktur. */
    @StringRes
    fun carry(layer: Layer): Int? = when (layer) {
        Layer.Light, Layer.TShirt -> null
        Layer.LongSleeve -> R.string.wear_carry_cardigan
        Layer.Sweater -> R.string.wear_carry_light_jacket
        Layer.Coat -> R.string.wear_carry_coat
        Layer.HeavyCoat, Layer.FullWinter -> R.string.wear_carry_heavy_coat
    }

    @StringRes
    fun name(extra: Extra): Int = when (extra) {
        Extra.Umbrella -> R.string.wear_extra_umbrella
        Extra.Windbreaker -> R.string.wear_extra_windbreaker
        Extra.Sunglasses -> R.string.wear_extra_sunglasses
        Extra.Sunscreen -> R.string.wear_extra_sunscreen
        Extra.Hat -> R.string.wear_extra_hat
        Extra.WaterproofShoes -> R.string.wear_extra_waterproof_shoes
    }

    @StringRes
    fun label(part: DayPart): Int = when (part) {
        DayPart.Morning -> R.string.part_morning
        DayPart.Midday -> R.string.part_midday
        DayPart.Evening -> R.string.part_evening
    }

    /** Katmanın ikonları (en çok dört). */
    fun icons(layer: Layer): List<Int> = when (layer) {
        Layer.Light -> listOf(R.drawable.ic_wear_tshirt, R.drawable.ic_wear_shorts)
        Layer.TShirt -> listOf(R.drawable.ic_wear_tshirt)
        Layer.LongSleeve -> listOf(R.drawable.ic_wear_long_sleeve)
        Layer.Sweater -> listOf(R.drawable.ic_wear_sweater)
        Layer.Coat -> listOf(R.drawable.ic_wear_coat)
        Layer.HeavyCoat -> listOf(R.drawable.ic_wear_coat, R.drawable.ic_wear_scarf)
        Layer.FullWinter -> listOf(R.drawable.ic_wear_coat, R.drawable.ic_wear_scarf, R.drawable.ic_wear_beanie, R.drawable.ic_wear_gloves)
    }

    @DrawableRes
    fun icon(extra: Extra): Int = when (extra) {
        Extra.Umbrella -> R.drawable.ic_wear_umbrella
        Extra.Windbreaker -> R.drawable.ic_wear_windbreaker
        Extra.Sunglasses -> R.drawable.ic_wear_sunglasses
        Extra.Sunscreen -> R.drawable.ic_wear_sunscreen
        Extra.Hat -> R.drawable.ic_wear_hat
        Extra.WaterproofShoes -> R.drawable.ic_wear_boots
    }

    /** Satırdaki bütün ikonlar: önce katman, sonra ekler. */
    fun icons(advice: ClothingAdvice): List<Int> = icons(advice.layer) + advice.extras.sorted().map { icon(it) }

    /**
     * Tek cümle: "Kazak ya da ince mont giy; şemsiye al." Sabah ile öğle arasında fark büyükse:
     * "Sabah 11°, öğleden sonra 21°: ince bir mont al, öğlen çıkarırsın." [degrees] sıcaklığı kullanıcının
     * birimine çevirip yuvarlar.
     */
    fun sentence(advice: ClothingAdvice, strings: Strings, degrees: (Double) -> Int): String {
        val main = advice.swing?.let { swing(it, advice.layer, strings, degrees) } ?: strings.get(clause(advice.layer))
        return withExtras(main, advice.extras, strings)
    }

    /** Kısa biçim (widget ve bildirim): "Kazak ya da ince mont; şemsiye al." */
    fun short(advice: ClothingAdvice, strings: Strings): String =
        withExtras(strings.get(name(advice.layer)), advice.extras, strings)

    private fun withExtras(main: String, extras: Set<Extra>, strings: Strings): String {
        if (extras.isEmpty()) return strings.get(R.string.wear_plain, main)
        val take = strings.get(R.string.wear_take, strings.list(extras.sorted().map { strings.get(name(it)) }))
        return strings.get(R.string.wear_with_extras, main, take)
    }

    private fun swing(swing: Swing, dayLayer: Layer, strings: Strings, degrees: (Double) -> Int): String {
        val morning = degrees(swing.morning)
        val midday = degrees(swing.midday)
        val warming = swing.midday > swing.morning
        // Isınıyorsa sabahın üst giysisi öğlen çıkarılır; soğuyorsa öğlenin üst giysisi yanına alınır.
        val carry = carry(if (warming) swing.morningLayer else swing.middayLayer)
            ?.takeIf { swing.morningLayer != swing.middayLayer }
        return when {
            carry == null -> strings.get(R.string.wear_swing_plain, morning, midday, strings.lowerFirst(strings.get(clause(dayLayer))))
            warming -> strings.get(R.string.wear_swing_warming, morning, midday, strings.get(carry))
            else -> strings.get(R.string.wear_swing_cooling, morning, midday, strings.get(carry))
        }
    }
}
