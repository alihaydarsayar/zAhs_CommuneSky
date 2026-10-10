package com.alihaydarsayar.communesky.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetConfigStore
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import com.alihaydarsayar.communesky.widget.WidgetInput
import com.alihaydarsayar.communesky.widget.WidgetKind
import com.alihaydarsayar.communesky.widget.WidgetSamples
import com.alihaydarsayar.communesky.widget.config.PreviewSize
import com.alihaydarsayar.communesky.widget.config.WidgetConfigActivity
import com.alihaydarsayar.communesky.widget.config.WidgetRender
import com.alihaydarsayar.communesky.widget.config.WidgetSizes
import kotlinx.coroutines.flow.first
import kotlin.math.min

/** Ana ekrandaki bir widget: kimliği, türü, kayıtlı ayarı ve boyutu. */
private data class PlacedWidget(val id: Int, val kind: WidgetKind, val config: WidgetConfig, val size: PreviewSize)

private data class MyWidgets(val widgets: List<PlacedWidget>, val input: WidgetInput)

/**
 * Ayarlar > Widget'larım: ana ekrandaki widget'ları küçük önizlemeleriyle listeler; dokununca o
 * widget'ın ayar ekranı açılır. Bazı launcher'lar "Widget'ı düzenle" seçeneğini göstermez; widget
 * böylece silinip yeniden eklenmeden düzenlenebilir.
 */
@Composable
fun MyWidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<MyWidgets?>(null) }
    var refresh by remember { mutableStateOf(0) }
    // Ayar ekranından dönünce liste ve önizlemeler yenilenir.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    androidx.compose.runtime.LaunchedEffect(refresh) { state = load(context) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.my_widgets_title), onBack)
        val current = state ?: return@Column
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (current.widgets.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.my_widgets_empty), color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.my_widgets_empty_hint), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.my_widgets_hint),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(current.widgets, key = { it.id }) { widget ->
                GlassCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            context.startActivity(
                                Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id),
                            )
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(132.dp, 84.dp), contentAlignment = Alignment.Center) {
                            val scale = min(132f / widget.size.frame.width.value, 84f / widget.size.frame.height.value)
                            WidgetRender(widget.kind, widget.config, widget.size, scale, current.input)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(widget.kind.nameRes), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(widget.config.style.styleId(widget.kind).nameRes),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private suspend fun load(context: Context): MyWidgets {
    val manager = AppWidgetManager.getInstance(context)
    val store = WidgetConfigStore.get(context)
    val widgets = WidgetKind.entries.flatMap { kind ->
        manager.getAppWidgetIds(ComponentName(context, kind.receiverClass)).map { id ->
            val config = store.get(id, kind)
            PlacedWidget(id, kind, config, WidgetSizes.preview(context, kind, id, config.style.styleId(kind).previewSize))
        }
    }
    val input = runCatching { WidgetDataLoader(context).input.first() }.getOrNull()?.takeIf { it.weather.isNotEmpty() }
        ?: WidgetSamples.input(context)
    return MyWidgets(widgets, input)
}
