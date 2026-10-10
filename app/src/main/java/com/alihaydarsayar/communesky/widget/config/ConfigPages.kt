package com.alihaydarsayar.communesky.widget.config

import android.graphics.Typeface
import android.os.Build
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.widget.BackgroundKind
import com.alihaydarsayar.communesky.widget.ClockFont
import com.alihaydarsayar.communesky.widget.ColorNames
import com.alihaydarsayar.communesky.widget.ColorTheme
import com.alihaydarsayar.communesky.widget.Contrast
import com.alihaydarsayar.communesky.widget.CornerSize
import com.alihaydarsayar.communesky.widget.InfoRow
import com.alihaydarsayar.communesky.widget.Legibility
import com.alihaydarsayar.communesky.widget.RadialSeconds
import com.alihaydarsayar.communesky.widget.SecondEffect
import com.alihaydarsayar.communesky.widget.SecondSymbol
import com.alihaydarsayar.communesky.widget.SsClock
import com.alihaydarsayar.communesky.widget.TextSize
import com.alihaydarsayar.communesky.widget.WallpaperTone
import com.alihaydarsayar.communesky.widget.WeightChoice
import com.alihaydarsayar.communesky.widget.WidgetColors
import com.alihaydarsayar.communesky.widget.WidgetConfig
import com.alihaydarsayar.communesky.widget.WidgetInk
import com.alihaydarsayar.communesky.widget.WidgetKind
import com.alihaydarsayar.communesky.widget.WidgetPalettes
import com.alihaydarsayar.communesky.widget.WidgetPlace
import com.alihaydarsayar.communesky.widget.WidgetStyle
import com.alihaydarsayar.communesky.widget.WidgetStyleId
import com.alihaydarsayar.communesky.widget.radialSeconds
import kotlin.math.roundToInt

// --- Ana ekrandaki ayar listesi ---------------------------------------------------------------------

/** Tek kart, satırlar: her satır o anki değerin özetini gösterir ve bir sayfa açar. */
@Composable
fun SettingsList(kind: WidgetKind, config: WidgetConfig, places: List<SavedPlace>, sky: SkyTheme, onOpen: (ConfigPage) -> Unit) {
    val context = LocalContext.current
    val style = config.style
    val styleId = style.styleId(kind)
    val colors = remember(style, sky) { WidgetColors.resolve(context, style, sky) }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(C.card)) {
        CfgRow(R.drawable.ic_cfg_background, stringResource(R.string.widget_config_page_background), backgroundSummary(style), { onOpen(ConfigPage.Background) })
        CfgDivider()
        val themeName = if (styleId == WidgetStyleId.ClockSS) {
            SsClock.preset(style)?.let { stringResource(it.labelRes) } ?: stringResource(R.string.widget_theme_custom)
        } else {
            stringResource(style.colorTheme.labelRes)
        }
        val dots = if (styleId == WidgetStyleId.ClockSS) {
            SsClock.look(style).let { listOf(Color(it.dial), Color(it.ink), Color(it.accent)) }
        } else {
            listOf(colors.palette.solid, colors.text, colors.accent)
        }
        CfgRow(R.drawable.ic_cfg_palette, stringResource(R.string.widget_config_colors), themeName, { onOpen(ConfigPage.Colors) }, dots = dots)
        CfgDivider()
        val digits = if (kind == WidgetKind.Clock && !styleId.isAnalog) stringResource((style.clockFont ?: styleId.clockFont).shortLabelRes) + " · " else ""
        CfgRow(R.drawable.ic_cfg_text, stringResource(R.string.widget_config_text), digits + stringResource(style.weight.labelRes), { onOpen(ConfigPage.Text) })
        if (styleId.contents.isNotEmpty() || kind == WidgetKind.Clock) {
            CfgDivider()
            val enabled = style.contents ?: kind.defaultContents
            val shown = styleId.contents.filter { it in enabled }
            val summary = when {
                styleId.contents.isEmpty() -> ""
                shown.isEmpty() -> stringResource(R.string.widget_config_shown_none)
                shown.size <= 2 -> shown.map { stringResource(it.labelRes) }.joinToString(", ")
                else -> stringResource(R.string.widget_config_shown_count, shown.size, styleId.contents.size)
            }
            CfgRow(R.drawable.ic_list, stringResource(R.string.widget_config_shown), summary, { onOpen(ConfigPage.Contents) })
        }
        // Konum: kendi yerlerini bilen stillerde (Ev ve konum, Yerlerim, Saat + ev) gösterilmez.
        if (kind.choosesPlace && styleId != WidgetStyleId.ClockHome) {
            CfgDivider()
            val place = when (val p = config.place) {
                WidgetPlace.Smart -> stringResource(R.string.widget_place_smart_short)
                WidgetPlace.Device -> stringResource(R.string.current_location)
                WidgetPlace.Home -> stringResource(R.string.home_place)
                is WidgetPlace.Saved -> places.firstOrNull { it.id == p.placeId }?.name ?: stringResource(R.string.widget_place_saved_short)
            }
            CfgRow(R.drawable.ic_location, stringResource(R.string.widget_config_location), place, { onOpen(ConfigPage.Place) })
        }
    }
}

@Composable
private fun backgroundSummary(style: WidgetStyle): String {
    val name = stringResource(style.background.labelRes)
    // Tam dolu ya da tam saydamken yüzde yazmaya gerek yok: ad yeterli.
    val value = style.effectiveTransparency
    return if (style.background == BackgroundKind.Transparent || value <= 0 || value >= 100) name else name + " · " + stringResource(R.string.widget_percent, value)
}

@Composable
private fun transparencyWord(value: Int): String = when {
    value <= 0 -> stringResource(R.string.widget_transparency_solid)
    value >= 100 -> stringResource(R.string.widget_transparency_clear)
    else -> stringResource(R.string.widget_percent, value)
}

// --- Arka plan --------------------------------------------------------------------------------------

@Composable
fun BackgroundPage(style: WidgetStyle, sky: SkyTheme, restyle: ((WidgetStyle) -> WidgetStyle) -> Unit) {
    val context = LocalContext.current
    val wallpaper = remember { WallpaperTone.colors(context) ?: listOf(Color(0xFF55606E), Color(0xFF2B3038)) }
    val palette = remember(style.colorTheme, sky, style.customBackground) { WidgetPalettes.of(context, style.colorTheme, sky, style.customBackground) }
    val skyPalette = remember(sky) { WidgetPalettes.sky(sky) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Her kutucuk, duvar kâğıdı üstünde o arka planın küçük örneğidir.
        listOf(BackgroundKind.Solid, BackgroundKind.Sky, BackgroundKind.Glass, BackgroundKind.Transparent).forEach { kind ->
            val label = stringResource(kind.labelRes)
            CfgTile(selected = style.background == kind, label = label, onClick = { restyle { it.withBackground(kind) } }, modifier = Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(wallpaper)), contentAlignment = Alignment.Center) {
                    val fill = when (kind) {
                        BackgroundKind.Solid -> Modifier.background(palette.solid)
                        BackgroundKind.Sky -> Modifier.background(Brush.linearGradient(skyPalette.gradient))
                        BackgroundKind.Glass -> Modifier.background(Color.White.copy(alpha = 0.22f)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        BackgroundKind.Transparent -> Modifier
                    }
                    val onFill = if (kind == BackgroundKind.Solid && palette.solid.luminance() > 0.5f) Color(0xFF141416) else Color.White
                    Box(Modifier.fillMaxWidth().padding(6.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).then(fill), contentAlignment = Alignment.Center) {
                        Text("17:22", color = onFill, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    val transparency = style.effectiveTransparency
    CfgLabel(stringResource(R.string.widget_config_transparency), trailing = transparencyWord(transparency))
    CfgSlider(
        value = transparency.toFloat(),
        onValueChange = { value -> restyle { it.withTransparency((value / 5).roundToInt() * 5) } },
        valueRange = 0f..style.background.maxTransparency.toFloat(),
        description = stringResource(R.string.widget_config_transparency),
    )
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.widget_transparency_solid_short), color = C.secondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.widget_transparency_half), color = C.secondary, style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.widget_transparency_clear), color = C.secondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    if (style.background == BackgroundKind.Glass) CfgNote(stringResource(R.string.widget_config_glass_hint))
    CfgLabel(stringResource(R.string.widget_config_corners))
    CfgSegmented(CornerSize.entries.map { stringResource(it.labelRes) }, style.corners.ordinal, { i -> restyle { it.copy(corners = CornerSize.entries[i]) } })
    // "Yazının arkası": yalnızca yazı duvar kâğıdının üstündeyken (cam ve saydam) anlamlı.
    if (WidgetColors.isOnWallpaper(style)) {
        CfgLabel(stringResource(R.string.widget_config_behind_text), trailing = stringResource(R.string.widget_config_behind_text_hint))
        CfgSegmented(Legibility.entries.map { stringResource(it.labelRes) }, style.legibility.ordinal, { i -> restyle { it.copy(legibility = Legibility.entries[i]) } })
    }
}

// --- Renkler ----------------------------------------------------------------------------------------

/** Bir rengin rolü: adı, o anki değeri, varsayılanı, karşısındaki zemin ve nasıl değiştirileceği. */
class ColorRole(
    val key: String,
    @StringRes val label: Int,
    val value: Int?,
    val default: Color,
    /** Okunurluk bu renge karşı ölçülür (yazı için zemin, zemin için yazı); null ise ölçülmez. */
    val against: Color?,
    val set: (WidgetStyle, Int?) -> WidgetStyle,
) {
    val color: Color get() = value?.let { Color(it) } ?: default
}

/** Bu stilde kullanılan renk rolleri; kullanılmayanlar listede görünmez. */
fun colorRoles(kind: WidgetKind, style: WidgetStyle, colors: WidgetColors): List<ColorRole> {
    val styleId = style.styleId(kind)
    val palette = colors.palette
    // Zemin ya da yazı rengi elle seçilince tema "Özel" olur; temanın o anki renkleri başlangıç değeri kalır.
    fun custom(s: WidgetStyle) = if (s.colorTheme == ColorTheme.Custom) s else s.withColorTheme(ColorTheme.Custom).copy(
        customBackground = s.customBackground ?: palette.solid.toArgb(),
        customText = s.customText ?: colors.text.toArgb(),
    )
    val isCustom = style.colorTheme == ColorTheme.Custom
    return buildList {
        when {
            styleId == WidgetStyleId.ClockSS -> {
                val look = SsClock.look(style)
                val classic = SsClock.Preset.Classic.look
                add(ColorRole("dial", R.string.widget_color_dial, style.dialColor, Color(classic.dial), Color(look.ink)) { s, c -> s.copy(dialColor = c) })
                add(ColorRole("ink", R.string.widget_color_ink_short, style.numeralColor, Color(classic.ink), Color(look.dial)) { s, c -> s.copy(numeralColor = c) })
                add(ColorRole("accent", R.string.widget_color_accent, style.accent, Color(classic.accent), Color(look.dial)) { s, c -> s.copy(accent = c) })
                add(ColorRole("window", R.string.widget_color_window, style.windowColor, Color(classic.window), null) { s, c -> s.copy(windowColor = c) })
            }
            styleId.isAnalog -> {
                val light = styleId == WidgetStyleId.ClockAnalog
                val dial = style.dialColor?.let { Color(it) } ?: if (light) Color(0xFFF4F2EC) else Color(0xFF141414)
                val ink = if (dial.luminance() > 0.45f) WidgetInk.Dark else WidgetInk.Light
                add(ColorRole("dial", R.string.widget_color_dial, style.dialColor, dial, style.numeralColor?.let { Color(it) } ?: ink) { s, c -> s.copy(dialColor = c) })
                add(
                    ColorRole("marks", if (styleId == WidgetStyleId.ClockField) R.string.widget_color_numerals else R.string.widget_color_marks, style.numeralColor, ink, dial) { s, c ->
                        s.copy(numeralColor = c)
                    },
                )
                add(ColorRole("hands", R.string.widget_color_hands, style.handColor, ink, dial) { s, c -> s.copy(handColor = c) })
                add(ColorRole("accent", R.string.widget_color_accent, style.accent, WidgetInk.Accent, dial) { s, c -> s.copy(accent = c) })
                if (styleId == WidgetStyleId.ClockRing) {
                    add(ColorRole("cool", R.string.widget_color_ring_cool, style.ringCool, WidgetInk.Cool, null) { s, c -> s.copy(ringCool = c) })
                    add(ColorRole("warm", R.string.widget_color_ring_warm, style.ringWarm, WidgetInk.Warm, null) { s, c -> s.copy(ringWarm = c) })
                    add(ColorRole("rain", R.string.widget_color_ring_rain, style.ringRain, WidgetInk.Rain, null) { s, c -> s.copy(ringRain = c) })
                }
            }
            else -> {
                val ground = palette.solid
                // Zemin yalnızca düz arka planda tek renktir.
                if (style.background == BackgroundKind.Solid) {
                    add(ColorRole("ground", R.string.widget_color_background, style.customBackground.takeIf { isCustom }, ground, colors.text) { s, c -> custom(s).copy(customBackground = c ?: ground.toArgb()) })
                }
                val against = ground.takeIf { !colors.onWallpaper }
                if (kind == WidgetKind.Clock) {
                    val hour = style.hourColor?.let { Color(it) } ?: palette.hour ?: colors.text
                    add(ColorRole("hours", R.string.widget_color_hours_short, style.hourColor, palette.hour ?: colors.text, against) { s, c -> s.copy(hourColor = c) })
                    add(ColorRole("minutes", R.string.widget_color_minutes_short, style.minuteColor, palette.minute ?: hour, against) { s, c -> s.copy(minuteColor = c) })
                }
                add(ColorRole("text", R.string.widget_color_text, style.customText.takeIf { isCustom }, colors.text, against) { s, c -> custom(s).copy(customText = c) })
                add(ColorRole("accent", R.string.widget_color_accent, style.accent, colors.paletteAccent, against) { s, c -> s.copy(accent = c) })
                if (styleId == WidgetStyleId.ClockRadial) {
                    add(ColorRole("lines", R.string.widget_color_lines, style.lineColor, colors.text, against) { s, c -> s.copy(lineColor = c) })
                    add(ColorRole("pill", R.string.widget_color_pill, style.pillColor, colors.text, null) { s, c -> s.copy(pillColor = c) })
                }
            }
        }
    }
}

@Composable
fun ColorsPage(
    kind: WidgetKind,
    style: WidgetStyle,
    sky: SkyTheme,
    recent: List<Int>,
    restyle: ((WidgetStyle) -> WidgetStyle) -> Unit,
    onPicked: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val styleId = style.styleId(kind)
    val colors = remember(style, sky) { WidgetColors.resolve(context, style, sky) }
    // Hazır renk temaları: her biri o temanın renkleriyle küçük bir "17:22" örneği.
    LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (styleId == WidgetStyleId.ClockSS) {
            items(SsClock.Preset.entries) { preset ->
                val look = preset.look
                ThemeCard(stringResource(preset.labelRes), SsClock.preset(style) == preset, Color(look.dial), Color(look.ink), Color(look.accent)) {
                    restyle { SsClock.apply(it, preset) }
                }
            }
        } else {
            // Eski sürümden kalan bir tema seçiliyse o da listede görünür.
            val themes = (ColorTheme.featured + style.colorTheme).distinct()
            items(themes) { theme ->
                val palette = remember(theme, sky, style.customBackground) { WidgetPalettes.of(context, theme, sky, style.customBackground) }
                val text = if (palette.solidDarkText) WidgetInk.Dark else WidgetInk.Light
                ThemeCard(stringResource(theme.labelRes), style.colorTheme == theme, palette.solid, palette.hour ?: text, palette.minute ?: palette.hour ?: text) {
                    restyle { it.withColorTheme(theme) }
                }
            }
        }
    }
    CfgLabel(stringResource(R.string.widget_config_one_by_one))
    val roles = colorRoles(kind, style, colors)
    var open by remember { mutableStateOf<String?>(null) }
    roles.forEachIndexed { index, role ->
        if (index > 0) CfgDivider()
        RoleRow(
            role = role,
            expanded = open == role.key,
            recent = recent,
            onToggle = { open = if (open == role.key) null else role.key },
            onChange = { color ->
                restyle { role.set(it, color) }
                onPicked(color)
            },
        )
    }
    if (kind == WidgetKind.Clock && !styleId.isAnalog) CfgNote(stringResource(R.string.widget_config_digit_color_hint))
    if (styleId.isAnalog && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) CfgNote(stringResource(R.string.widget_config_hands_old_android))
}

@Composable
private fun ThemeCard(name: String, selected: Boolean, ground: Color, hour: Color, minute: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.box.copy(alpha = if (C.isDark) 0.5f else 1f))
            .border(2.5.dp, if (selected) C.text else Color.Transparent, RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(ground).border(1.dp, C.line, RoundedCornerShape(10.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("17", color = hour, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = condensed)
            Text(":22", color = minute, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = condensed)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = if (selected) C.text else C.secondary, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

private val condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/**
 * Bir renk rolünün satırı: renk dairesi, rolün adı ve rengin adı. Dokununca altında renk ızgarası
 * açılır (son kullanılanlar başta), "Başka renk seç" ve okunurluk durumu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleRow(role: ColorRole, expanded: Boolean, recent: List<Int>, onToggle: () -> Unit, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val color = role.color
    val colorName = ColorNames.name(context, color.toArgb())
    val roleName = stringResource(role.label)
    val description = stringResource(R.string.widget_config_role_description, roleName, colorName)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (expanded) "expanded" else "collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color).border(1.dp, C.line.copy(alpha = 0.25f), CircleShape))
        Spacer(Modifier.width(14.dp))
        Text(roleName, color = C.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(colorName, color = C.secondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(if (expanded) "⌄" else "›", color = C.secondary, style = MaterialTheme.typography.titleMedium)
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(tween(MOTION_MS)) + fadeIn(tween(MOTION_MS)),
        exit = shrinkVertically(tween(MOTION_MS)) + fadeOut(tween(MOTION_MS)),
    ) {
        Column(Modifier.padding(bottom = 14.dp)) {
            // Son kullanılan altı renk başta, sonra 12 hazır renk (tekrar etmeden).
            val swatches = (recent + WidgetInk.Presets).distinct().take(18)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { swatch ->
                    val selected = color.toArgb() == swatch
                    val name = ColorNames.name(context, swatch)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, if (selected) C.text else Color.Transparent, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(1.dp, C.line.copy(alpha = 0.25f), CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(swatch) })
                            .semantics { contentDescription = name },
                    )
                }
            }
            var custom by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.widget_color_other),
                    color = C.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button) { custom = !custom }.padding(vertical = 14.dp),
                )
                if (role.value != null) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.widget_color_reset),
                        color = C.secondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button) { onChange(null) }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Okunurluk: yazı ile zemin arasındaki kontrast 4,5:1'in altındaysa uyarı ve "Düzelt".
                role.against?.let { against ->
                    if (Contrast.isReadable(color.toArgb(), against.toArgb())) {
                        Text(stringResource(R.string.widget_color_readable), color = Color(0xFF3FB27F), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.widget_color_hard_to_read), color = Color(0xFFE08A3C), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        CfgButton(stringResource(R.string.widget_color_fix), onClick = { onChange(if (against.luminance() > 0.4f) 0xFF141416.toInt() else 0xFFFFFFFF.toInt()) }, filled = false)
                    }
                }
            }
            AnimatedVisibility(visible = custom) { OtherColor(color.toArgb(), onChange) }
        }
    }
}

/** "Başka renk seç": ton ve açıklık kaydırıcıları, HEX girişi. */
@Composable
private fun OtherColor(current: Int, onChange: (Int?) -> Unit) {
    val hsl = remember(current) { FloatArray(3).also { ColorUtils.colorToHSL(current, it) } }
    var hex by remember(current) { mutableStateOf(Contrast.toHex(current)) }
    Column {
        Text(stringResource(R.string.widget_color_hue), color = C.secondary, style = MaterialTheme.typography.labelMedium)
        CfgSlider(
            value = hsl[0],
            onValueChange = { hue -> onChange(ColorUtils.HSLToColor(floatArrayOf(hue, hsl[1].coerceAtLeast(0.6f), hsl[2].coerceIn(0.25f, 0.75f)))) },
            valueRange = 0f..359f,
            description = stringResource(R.string.widget_color_hue),
        )
        Text(stringResource(R.string.widget_color_lightness), color = C.secondary, style = MaterialTheme.typography.labelMedium)
        CfgSlider(
            value = hsl[2],
            onValueChange = { lightness -> onChange(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], lightness))) },
            valueRange = 0f..1f,
            description = stringResource(R.string.widget_color_lightness),
        )
        val hexLabel = stringResource(R.string.widget_color_hex)
        BasicTextField(
            value = hex,
            onValueChange = { text ->
                hex = text.take(9)
                Contrast.parseHex(hex)?.let(onChange)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.text),
            cursorBrush = SolidColor(C.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(C.box)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics { contentDescription = hexLabel },
        )
    }
}

// --- Yazı -------------------------------------------------------------------------------------------

@Composable
fun TextPage(kind: WidgetKind, style: WidgetStyle, restyle: ((WidgetStyle) -> WidgetStyle) -> Unit) {
    val styleId = style.styleId(kind)
    if (kind == WidgetKind.Clock && !styleId.isAnalog) {
        // Saat rakamları: her kutucukta o rakamlarla "17:22".
        CfgLabel(stringResource(R.string.widget_config_clock_font))
        val selected = style.clockFont ?: styleId.clockFont
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClockFont.entries.forEach { font ->
                CfgTile(selected = font == selected, label = stringResource(font.shortLabelRes), description = stringResource(font.labelRes), onClick = { restyle { it.copy(clockFont = font) } }, modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(11.dp)).background(C.box), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val hours = if (font == ClockFont.System) FontFamily.SansSerif else condensed
                        val minutes = when (font) {
                            ClockFont.System -> FontFamily.SansSerif
                            ClockFont.Digits -> condensed
                            ClockFont.DigitsOutline -> condensedLight
                        }
                        Text("17", color = C.text, fontSize = 22.sp, fontWeight = if (font == ClockFont.System) FontWeight.Medium else FontWeight.Bold, fontFamily = hours)
                        Text(":22", color = C.text, fontSize = 22.sp, fontWeight = if (font == ClockFont.DigitsOutline) FontWeight.Light else if (font == ClockFont.System) FontWeight.Medium else FontWeight.Bold, fontFamily = minutes)
                    }
                }
            }
        }
    }
    CfgLabel(stringResource(R.string.widget_config_weight))
    CfgSegmented(WeightChoice.entries.map { stringResource(it.labelRes) }, style.weight.ordinal, { i -> restyle { it.copy(weight = WeightChoice.entries[i]) } })
    CfgLabel(stringResource(R.string.widget_config_text_size))
    CfgSegmented(TextSize.entries.map { stringResource(it.labelRes) }, style.textSize.ordinal, { i -> restyle { it.copy(textSize = TextSize.entries[i]) } })
    CfgLabel(stringResource(R.string.widget_config_info_row))
    CfgSegmented(InfoRow.entries.map { stringResource(it.labelRes) }, style.infoRow.ordinal, { i -> restyle { it.copy(infoRow = InfoRow.entries[i]) } })
    CfgNote(stringResource(R.string.widget_config_font_note))
}

private val condensedLight = FontFamily(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL))

// --- Gösterilenler ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentsPage(kind: WidgetKind, style: WidgetStyle, restyle: ((WidgetStyle) -> WidgetStyle) -> Unit) {
    val styleId = style.styleId(kind)
    val isSs = styleId == WidgetStyleId.ClockSS
    val old = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    if (styleId.contents.isNotEmpty()) {
        CfgLabel(stringResource(if (kind == WidgetKind.Clock) R.string.widget_config_group_weather else R.string.widget_config_group_content))
        val enabled = style.contents ?: kind.defaultContents
        styleId.contents.forEach { content ->
            CfgSwitchRow(stringResource(content.labelRes), content in enabled, { on -> restyle { it.copy(contents = if (on) enabled + content else enabled - content) } })
        }
        if (kind == WidgetKind.HomeLocation) CfgNote(stringResource(R.string.widget_config_home_hint))
    }
    if (kind == WidgetKind.Clock && (styleId == WidgetStyleId.ClockRadial || styleId.isAnalog)) {
        CfgLabel(stringResource(R.string.widget_config_group_clock))
        if (isSs) {
            CfgSwitchRow(stringResource(R.string.widget_config_logo), style.logo, { on -> restyle { it.copy(logo = on) } })
            CfgSwitchRow(stringResource(R.string.widget_config_date), style.showDate, { on -> restyle { it.copy(showDate = on) } })
        }
        if (styleId.isAnalog) {
            CfgSwitchRow(
                stringResource(R.string.widget_config_second_hand), style.secondHand, { on -> restyle { it.copy(secondHand = on) } },
                subtitle = if (old) stringResource(R.string.widget_config_second_old_android) else null,
            )
            if (style.secondHand && !isSs) {
                CfgLabel(stringResource(R.string.widget_config_second_symbol))
                CfgSegmented(SecondSymbol.entries.map { stringResource(it.labelRes) }, style.secondSymbol.ordinal, { i -> restyle { it.copy(secondSymbol = SecondSymbol.entries[i]) } })
            }
        }
        if (styleId == WidgetStyleId.ClockRadial) {
            CfgLabel(stringResource(R.string.widget_config_second_effect))
            CfgSegmented(SecondEffect.entries.map { stringResource(it.labelRes) }, style.secondEffect.ordinal, { i -> restyle { it.copy(secondEffect = SecondEffect.entries[i]) } })
            CfgNote(
                stringResource(
                    when {
                        old -> R.string.widget_config_second_old_android
                        style.secondEffect != SecondEffect.Off && radialSeconds(style) == RadialSeconds.StripLine -> R.string.widget_config_second_glass
                        else -> R.string.widget_config_second_hint
                    },
                ),
            )
        }
        if (styleId == WidgetStyleId.ClockField) {
            // Vurgu renginde gösterilecek rakamlar: 1–24, dokununca açılır/kapanır.
            CfgLabel(stringResource(R.string.widget_config_highlighted))
            val accent = style.accent?.let { Color(it) } ?: WidgetInk.Accent
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..24).forEach { n ->
                    val on = n in style.highlighted
                    Box(
                        Modifier
                            .size(48.dp)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else C.box)
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = { restyle { s -> s.copy(highlighted = if (on) s.highlighted - n else s.highlighted + n) } }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(n.toString(), color = if (on) Color.White else C.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// --- Konum ------------------------------------------------------------------------------------------

@Composable
fun PlacePage(config: WidgetConfig, places: List<SavedPlace>, deviceName: String?, onPlace: (WidgetPlace) -> Unit) {
    val home = places.firstOrNull { it.isHome }
    Spacer(Modifier.height(6.dp))
    CfgRadioRow(stringResource(R.string.widget_place_smart), config.place == WidgetPlace.Smart, { onPlace(WidgetPlace.Smart) }, subtitle = stringResource(R.string.widget_place_smart_hint))
    CfgDivider()
    CfgRadioRow(stringResource(R.string.current_location), config.place == WidgetPlace.Device, { onPlace(WidgetPlace.Device) }, subtitle = deviceName)
    CfgDivider()
    CfgRadioRow(stringResource(R.string.home_place), config.place == WidgetPlace.Home, { onPlace(WidgetPlace.Home) }, subtitle = home?.name ?: stringResource(R.string.widget_config_no_home))
    if (places.isNotEmpty()) {
        CfgLabel(stringResource(R.string.widget_config_saved_places))
        places.forEachIndexed { index, place ->
            if (index > 0) CfgDivider()
            CfgRadioRow(place.name, config.place == WidgetPlace.Saved(place.id), { onPlace(WidgetPlace.Saved(place.id)) }, subtitle = place.region)
        }
    }
}
