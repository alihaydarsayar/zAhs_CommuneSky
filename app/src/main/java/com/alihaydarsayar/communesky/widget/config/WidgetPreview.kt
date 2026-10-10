package com.alihaydarsayar.communesky.widget.config

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetKind
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Önizlemenin, ana ekrandaki widget'la aynı boyutta çizilmesi için boyut hesapları.
 *
 * Ana ekranda widget [frame] boyutunda durur, ama içeriği Glance'in o boyuta en uygun bulduğu
 * basamağa ([draw]) göre çizilir. Önizleme de aynısını yapar; böylece kaydedilen görünüm ile ana
 * ekrandaki görünüm aynı olur.
 */
data class PreviewSize(val draw: DpSize, val frame: DpSize)

object WidgetSizes {

    /**
     * Boyut basamaklarından [actual] boyuta sığan en yakını; hiçbiri sığmıyorsa en küçüğü
     * (Android'in ve Glance'in kullandığı kural). Saf mantık; test edilir.
     */
    fun responsive(sizes: Set<DpSize>, actual: DpSize): DpSize {
        fun fits(size: DpSize) =
            ceil(actual.width.value) + 1 > size.width.value && ceil(actual.height.value) + 1 > size.height.value
        fun distance(size: DpSize): Float {
            val dw = actual.width.value - size.width.value
            val dh = actual.height.value - size.height.value
            return dw * dw + dh * dh
        }
        return sizes.filter(::fits).minByOrNull(::distance) ?: sizes.minBy { it.width.value * it.height.value }
    }

    /** Ana ekrandaki widget'ın gerçek boyutu (dikey ekran): launcher bildirmediyse null. */
    fun actual(context: Context, appWidgetId: Int): DpSize? {
        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull() ?: return null
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
    }

    /** Yeni eklenen widget'ın seçicideki hedef hücre boyutu (Android 12+), öncesinde en küçük boyutu. */
    fun target(context: Context, info: AppWidgetProviderInfo?): DpSize? {
        info ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            // Android'in dikey telefon için verdiği yaklaşık ölçü: (73n − 16) × (118m − 16) dp.
            return DpSize((73 * info.targetCellWidth - 16).dp, (118 * info.targetCellHeight - 16).dp)
        }
        val density = context.resources.displayMetrics.density
        return DpSize((info.minWidth / density).dp, (info.minHeight / density).dp)
    }

    /** Bu widget'ın önizlemesi hangi boyutta çizilmeli? */
    fun preview(context: Context, kind: WidgetKind, appWidgetId: Int, fallback: DpSize): PreviewSize {
        val info = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) }.getOrNull()
        val frame = actual(context, appWidgetId) ?: target(context, info) ?: fallback
        return of(kind, frame)
    }

    /** Saat widget'ı gerçek boyutuyla, diğerleri boyut basamağıyla çizilir (bkz. CommuneSkyWidget.sizeMode). */
    fun of(kind: WidgetKind, frame: DpSize): PreviewSize =
        PreviewSize(draw = if (kind == WidgetKind.Clock) frame else responsive(kind.sizes, frame), frame = frame)
}

/**
 * Widget'ı, ana ekrandakiyle aynı kodla (Glance → RemoteViews) çizer ve [scale] ile küçültür.
 * Ayar her değiştiğinde kısa bir beklemeden sonra yeniden çizilir.
 */
@OptIn(ExperimentalGlanceApi::class)
@Composable
fun WidgetRender(
    kind: WidgetKind,
    config: WidgetConfig,
    size: PreviewSize,
    scale: Float,
    input: WidgetInput,
    /** Önizleme hangi zeminde duruyor: true açık (koyu yazı), false koyu, null telefonun duvar kâğıdı. */
    wallpaperDarkText: Boolean? = null,
) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    // Ayar değişmedikçe yeniden çizilmez; art arda gelen değişiklikler (kaydırıcı) tek çizimde birleşir.
    LaunchedEffect(kind, config, size, wallpaperDarkText) {
        delay(80)
        views = runCatching {
            kind.widget(config, input).apply { previewWallpaperDarkText = wallpaperDarkText }.compose(context, size = size.draw)
        }.getOrNull()
    }
    Box(Modifier.size(size.frame.width * scale, size.frame.height * scale), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { frame ->
                val remote = views ?: return@AndroidView
                frame.removeAllViews()
                // Uygulama bağlamıyla: AppCompat ekranlarının yazı bileşenlerini değiştiren şişiricisi
                // widget düzenlerini bozmasın (launcher da düz bir bağlamla çizer).
                runCatching { remote.apply(frame.context.applicationContext, frame) }
                    .onFailure { android.util.Log.w("WidgetPreview", "Önizleme çizilemedi", it) }
                    .getOrNull()?.let(frame::addView)
            },
            modifier = Modifier
                .requiredSize(size.frame)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
fun WidgetRender(kind: WidgetKind, config: WidgetConfig, size: DpSize, scale: Float, input: WidgetInput) =
    WidgetRender(kind, config, PreviewSize(size, size), scale, input)
