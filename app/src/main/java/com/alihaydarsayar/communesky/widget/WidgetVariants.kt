package com.alihaydarsayar.communesky.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Launcher'ın bir widget için bildirdiği iki ölçü: telefon dikeyken (en dar × en yüksek) ve yatayken (en geniş ×
 * en alçak). Android 12+ widget'ı iki ölçü için de çizdirir ve ana ekran, kendi gerçek boyutuna sığan en büyüğünü
 * gösterir; **hiçbiri sığmıyorsa alanı en küçük olanı** seçer.
 */
data class WidgetBounds(val portrait: DpSize?, val landscape: DpSize?) {
    companion object {
        fun of(options: Bundle?): WidgetBounds {
            if (options == null) return WidgetBounds(null, null)
            fun dp(key: String) = options.getInt(key, 0).takeIf { it > 0 }?.toFloat()
            val minW = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxW = dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minH = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxH = dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            return WidgetBounds(
                portrait = if (minW != null && maxH != null) DpSize(minW.dp, maxH.dp) else null,
                landscape = if (maxW != null && minH != null) DpSize(maxW.dp, minH.dp) else null,
            )
        }

        fun of(context: Context, appWidgetId: Int): WidgetBounds =
            of(runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull())
    }
}

/** Çizilen widget'ın launcher'dan gelen ölçüleri; önizlemede ve testlerde boştur. */
val LocalWidgetBounds = staticCompositionLocalOf { WidgetBounds(null, null) }

/**
 * Hangi ölçüye göre yerleşilecek? Saf mantık; test edilir.
 *
 * Bazı ana ekranlarda dikey ölçü, widget'ın gerçek alanından birkaç dp büyük bildirilir; o zaman hiçbir çizim
 * "sığmaz" ve ana ekran alanı en küçük çizimi gösterir. Yatay ölçünün alanı dikeyinkinden küçükse bu, telefon
 * dikeyken **yatay için hazırlanmış** (daha geniş ve alçak) düzenin gösterilmesi demektir: içerik sağdan ve soldan
 * taşar, kesilir. Bunu önlemek için telefon dikeyken ve yatay ölçünün alanı daha küçükken yatay çizim de dikey
 * ölçüyle yerleştirilir: ana ekran hangisini seçerse seçsin doğru düzen görünür.
 *
 * Telefonlarda ana ekran her zaman dikey sayılır (bkz. [treatAsPortrait]): güncelleme telefon yan tutulurken gelse
 * de dikey düzen çizilir. Bedeli: yatay dönebilen ve yatay ölçüsünün alanı daha küçük olan bir telefon ana ekranında
 * widget yatayda da dikey düzeniyle görünür; tabletlerde ise yatay döndürülünce bir sonraki güncellemeye kadar dikey
 * düzeniyle kalır. Dikey ölçünün alanı daha küçük olan ana ekranlarda (ör. Pixel) hiçbir şey değişmez.
 */
object WidgetVariants {
    /** İki ölçü bundan yakınsa aynı sayılır (launcher'lar aynı ölçüyü 373 ve 374 gibi iki biçimde bildirir). */
    private const val SAME_DP = 3f

    fun layoutSize(variant: DpSize, bounds: WidgetBounds, devicePortrait: Boolean): DpSize {
        val portrait = bounds.portrait ?: return variant
        val landscape = bounds.landscape ?: return variant
        if (!devicePortrait || same(portrait, landscape)) return variant
        val isLandscapeVariant = distance(variant, landscape) < distance(variant, portrait)
        return if (isLandscapeVariant && area(landscape) < area(portrait)) portrait else variant
    }

    private fun area(size: DpSize) = size.width.value * size.height.value
    private fun distance(a: DpSize, b: DpSize) = abs(a.width.value - b.width.value) + abs(a.height.value - b.height.value)
    private fun same(a: DpSize, b: DpSize) = abs(a.width.value - b.width.value) <= SAME_DP && abs(a.height.value - b.height.value) <= SAME_DP

    /** Bundan dar ekranlar telefondur (en kısa kenar, dp); Android'in tablet eşiği. */
    const val TABLET_MIN_DP = 600

    /**
     * Ana ekran dikey mi sayılsın? Telefonlarda ana ekran dikeydir; widget güncellemesi telefon yan tutulurken
     * (ör. video izlerken) gelse bile yerleşim dikey ölçüye göre yapılır, o anki yöne bakılmaz. Tabletlerde ana
     * ekran gerçekten yatay olabildiği için çizim anındaki yöne bakılır. Saf mantık; test edilir.
     */
    fun treatAsPortrait(smallestScreenWidthDp: Int, landscapeNow: Boolean): Boolean =
        smallestScreenWidthDp < TABLET_MIN_DP || !landscapeNow

    /** Yerleşim için: ana ekran dikey mi sayılır (bkz. [treatAsPortrait])? */
    fun isPortrait(context: Context): Boolean {
        val configuration = context.resources.configuration
        return treatAsPortrait(configuration.smallestScreenWidthDp, isLandscapeNow(context))
    }

    /** Ekranın şu anki gerçek yönü (tanılama kaydı için). */
    fun isLandscapeNow(context: Context): Boolean = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/** 1.5.7'ye kadar uygulama widget çizim kaydını cihaza yazıyordu; o dosya artık silinir. */
object LegacyDiagnostics {
    private const val LEGACY_PREFS = "widget_diagnostics"

    fun purgeStored(context: Context) {
        runCatching { context.applicationContext.deleteSharedPreferences(LEGACY_PREFS) }
    }
}
