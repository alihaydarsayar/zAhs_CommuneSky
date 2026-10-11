package com.alihaydarsayar.communesky.widget.config

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
 * Ana ekranda widget [frame] boyutunda durur ve içeriği de launcher'ın bildirdiği o boyuta ([draw])
 * göre çizilir. Önizleme de aynısını yapar; böylece kaydedilen görünüm ile ana ekrandaki görünüm aynı olur.
 */
data class PreviewSize(val draw: DpSize, val frame: DpSize)

object WidgetSizes {

    /**
     * Ana ekrandaki widget'ın çizileceği boyut (dikey ekran): launcher bildirmediyse null. Ana ekranla
     * aynı kural: Android 12+'da launcher'ın verdiği ölçü listesinden (OPTION_APPWIDGET_SIZES) dikey
     * ekranınki, yoksa en küçük genişlik × en büyük yükseklik. Glance de widget'ı bu ölçüyle çizer;
     * önizleme başka bir ölçü kullanırsa kaydedilen widget önizlemeden farklı görünür.
     */
    fun actual(context: Context, appWidgetId: Int): DpSize? {
        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            portrait(sizes?.map { DpSize(it.width.dp, it.height.dp) }.orEmpty())?.let { return it }
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
    }

    /** Ölçü listesinden dikey ekranınki: en dar olan (yatayda widget daha geniş ve daha alçaktır). Saf mantık; test edilir. */
    fun portrait(sizes: List<DpSize>): DpSize? =
        sizes.filter { it.width.value > 0f && it.height.value > 0f }.minByOrNull { it.width.value }

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

    /** Bütün widget'lar gerçek boyutlarıyla çizilir (bkz. CommuneSkyWidget.sizeMode). */
    @Suppress("UNUSED_PARAMETER")
    fun of(kind: WidgetKind, frame: DpSize): PreviewSize = PreviewSize(draw = frame, frame = frame)
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
    /** Çizimden önceki bekleme. Görünmeyen sayfalar daha geç çizilir: önce ekrandaki önizleme gelir. */
    delayMs: Long = 80,
    /** Canlı önizleme ekrana kurulunca çağrılır. */
    onApplied: (() -> Unit)? = null,
    /**
     * Canlı önizlemenin, ekranda göründüğü hâliyle (aynı piksellerle) alınmış resmi. Stil kaydırılırken
     * sayfa bu hazır resimle gösterilir; canlı önizlemeye geçerken görünüm değişmez.
     */
    onSnapshot: ((ImageBitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    val layer = rememberGraphicsLayer()
    var applied by remember { mutableIntStateOf(0) }
    val notifyApplied = rememberUpdatedState(onApplied)
    val notifySnapshot = rememberUpdatedState(onSnapshot)
    // Önizleme kurulduktan sonra birkaç kare beklenir (yerleşim ve çizim bitsin), sonra resmi alınır.
    LaunchedEffect(applied) {
        if (applied == 0) return@LaunchedEffect
        withFrameNanos { }
        notifyApplied.value?.invoke()
        if (notifySnapshot.value == null) return@LaunchedEffect
        // Ekranın dışındaki sayfa henüz çizilmemiş olabilir: boş resim saklanmaz, biraz sonra yeniden denenir.
        repeat(8) {
            delay(200)
            withFrameNanos { }
            val image = runCatching { layer.toImageBitmap() }.getOrNull()
            if (image != null && !image.isBlank()) {
                notifySnapshot.value?.invoke(image)
                return@LaunchedEffect
            }
        }
    }
    // Ayar değişmedikçe yeniden çizilmez; art arda gelen değişiklikler (kaydırıcı) tek çizimde birleşir.
    LaunchedEffect(kind, config, size, wallpaperDarkText) {
        delay(delayMs)
        // Bileşim arka planda: stil kaydırılırken ekran takılmasın.
        views = withContext(Dispatchers.Default) {
            runCatching {
                kind.widget(config, input).apply { previewWallpaperDarkText = wallpaperDarkText }.compose(context, size = size.draw)
            }.onFailure { android.util.Log.w("WidgetPreview", "Önizleme hazırlanamadı", it) }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(size.frame.width * scale, size.frame.height * scale)
            // Çizilen içerik bir katmana da kaydedilir: hazır resim bu katmandan, ekrandaki pikselleriyle alınır.
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { frame ->
                val remote = views ?: return@AndroidView
                // Aynı çizim yeniden kurulmaz.
                if (frame.tag === remote) return@AndroidView
                frame.tag = remote
                // Uygulama bağlamıyla: AppCompat ekranlarının yazı bileşenlerini değiştiren şişiricisi
                // widget düzenlerini bozmasın (launcher da düz bir bağlamla çizer).
                val app = frame.context.applicationContext
                // Görünüm bir sonraki mesajda kurulur: bu blok, sayfa kaydırılırken yerleşim geçişinin içinde
                // çalışabiliyor; o anda eklenen görünümün yerleşim isteği yutulur ve önizleme boş kalır.
                // Yeni görünüm kurulunca eskisinin yerini alır; arada boş kare görünmez.
                frame.post {
                    if (frame.tag !== remote) return@post
                    runCatching { remote.apply(app, frame) }
                        .onFailure { android.util.Log.w("WidgetPreview", "Önizleme çizilemedi", it) }
                        .getOrNull()?.let { view ->
                            frame.removeAllViews()
                            frame.addView(view)
                            frame.requestLayout()
                            applied++
                        }
                }
            },
            // Sayfa ekrandan çıkıp yeniden kullanılırken görünüm sıfırlanır: eski çizim silinir ve bir sonraki
            // güncelleme yeni sayfanın çizimini kurar.
            onReset = { frame ->
                frame.removeAllViews()
                frame.tag = null
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

/** Resim tamamen saydam mı? (Henüz çizilmemiş bir önizlemenin resmi.) Seyrek bir ızgarada bakılır. */
private fun ImageBitmap.isBlank(): Boolean {
    val bitmap = asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it } ?: return true
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) if (bitmap.getPixel(x, y) ushr 24 != 0) return false
    return true
}
