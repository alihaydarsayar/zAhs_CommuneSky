package com.alihaydarsayar.communesky.widget.config

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Widget ayar ekranının renkleri: düz ve nötr. Bu ekranda gökyüzü arka planı ve cam kartlar yok;
 * içerik hiçbir görselin üstünde durmaz. Renkli vurgu kullanılmaz: renk sadece widget'ın kendisinde.
 */
data class ConfigColors(
    val ground: Color,
    val card: Color,
    val box: Color,
    val text: Color,
    val line: Color,
    val isDark: Boolean,
) {
    val secondary: Color get() = text.copy(alpha = 0.56f)

    /** Ana düğme: koyuda sıcak beyaz üstünde siyah, açıkta tersi. */
    val button: Color get() = text
    val onButton: Color get() = if (isDark) Color(0xFF0E0E10) else Color(0xFFFFFFFF)

    companion object {
        val Dark = ConfigColors(Color(0xFF0E0E10), Color(0xFF1A1A1D), Color(0xFF242428), Color(0xFFF5F3EF), Color.White.copy(alpha = 0.07f), true)
        val Light = ConfigColors(Color(0xFFF3F2EF), Color(0xFFFFFFFF), Color(0xFFF0EFEB), Color(0xFF141416), Color.Black.copy(alpha = 0.07f), false)
    }
}

val LocalConfigColors = staticCompositionLocalOf { ConfigColors.Dark }

val C: ConfigColors
    @Composable @ReadOnlyComposable get() = LocalConfigColors.current

@Composable
fun configColors(): ConfigColors = if (isSystemInDarkTheme()) ConfigColors.Dark else ConfigColors.Light

val CardShape = RoundedCornerShape(22.dp)
val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
val StageShape = RoundedCornerShape(30.dp)

/** Geçişler kısa ve sade. */
const val MOTION_MS = 200

@Composable
fun CfgLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = C.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = C.secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CfgNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.secondary, style = MaterialTheme.typography.bodySmall, modifier = modifier.padding(top = 8.dp))
}

/** Bölmeli düğme: tek seçim. Seçili bölme kart renginde, yazısı kalın. */
@Composable
fun CfgSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.box.copy(alpha = if (C.isDark) 0.6f else 1f))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(if (selected) (if (C.isDark) Color(0xFF34343A) else Color.White) else Color.Transparent, tween(MOTION_MS), label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) C.text else C.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
fun CfgSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = C.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.onButton,
                checkedTrackColor = C.text,
                checkedBorderColor = C.text,
                uncheckedThumbColor = C.secondary,
                uncheckedTrackColor = C.box,
                uncheckedBorderColor = C.line,
            ),
        )
    }
}

@Composable
fun CfgRadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = C.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) C.text else C.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(C.text))
        }
    }
}

@Composable
fun CfgSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, description: String, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        colors = SliderDefaults.colors(
            thumbColor = C.text,
            activeTrackColor = C.text,
            inactiveTrackColor = C.box,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** Seçilebilir kutucuk: seçiliyken yazı renginde 2,5 dp dış çizgi. */
@Composable
fun CfgTile(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(2.5.dp, if (selected) C.text else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) C.text else C.secondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Ana ekrandaki ayar satırı: ikon kutusu, ad, o anki değerin özeti ve ok. */
@Composable
fun CfgRow(@DrawableRes icon: Int, title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier, dots: List<Color> = emptyList()) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = summary }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.box), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = C.text, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = C.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (dots.isNotEmpty()) {
                Dots(dots)
                Spacer(Modifier.width(10.dp))
            }
            Text(summary, color = C.secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(com.alihaydarsayar.communesky.R.drawable.ic_chevron_right), contentDescription = null, tint = C.secondary, modifier = Modifier.size(20.dp))
    }
}

/** Üst üste binen küçük renk noktaları (temanın renkleri). */
@Composable
fun Dots(colors: List<Color>, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
        colors.forEach { color ->
            Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, C.card, CircleShape))
        }
    }
}

@Composable
fun CfgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = true) {
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (filled) C.button.copy(alpha = if (enabled) 1f else 0.5f) else C.box)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) C.onButton else C.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CfgDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}
