package com.alihaydarsayar.communesky.widget.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.widget.Contrast
import com.alihaydarsayar.communesky.widget.WidgetInk

private const val TRANSITION_MS = 180

/**
 * Tek bir rengin seçimi: satıra dokununca 12 hazır renk ve HEX girişi açılır. [value] null ise
 * [default] kullanılıyor demektir; "Varsayılan" ona geri döner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRow(label: String, value: Int?, default: Color, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = value ?: default.toArgb()
    var hex by remember(current) { mutableStateOf(Contrast.toHex(current)) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (value == null) stringResource(R.string.widget_color_default) else Contrast.toHex(current),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(10.dp))
            Swatch(Color(current), selected = false, size = 26)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(TRANSITION_MS)) + fadeIn(tween(TRANSITION_MS)),
            exit = shrinkVertically(tween(TRANSITION_MS)) + fadeOut(tween(TRANSITION_MS)),
        ) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetInk.Presets.forEach { preset ->
                        val name = Contrast.toHex(preset)
                        Box(
                            Modifier
                                .selectable(selected = value == preset, role = Role.RadioButton, onClick = { onChange(preset) })
                                .semantics { contentDescription = name },
                        ) { Swatch(Color(preset), selected = value == preset, size = 36) }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hexLabel = stringResource(R.string.widget_color_hex)
                    BasicTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text.take(9)
                            Contrast.parseHex(hex)?.let(onChange)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier
                            .width(132.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = if (Contrast.parseHex(hex) == null) 0.5f else 0.14f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .semantics { contentDescription = hexLabel },
                    )
                    Spacer(Modifier.weight(1f))
                    if (value != null) {
                        Text(
                            stringResource(R.string.widget_color_reset),
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { onChange(null) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.35f), CircleShape),
    )
}
