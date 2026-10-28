package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/*
 * Hojas de la sesión en vivo del Refinamiento 02. Reproducen la hoja del prototipo (`styles.css`: .sheet, .sheet-header, .menu-row,
 * .choice-row, .segmented, .form-row, .input-label, .toggle, .inline-note, .section-label, .v2-history-note, .primary/.secondary):
 * velo oscuro, panel #141513 con radio 28, asa de 33 × 3, título en Syne 21 con ✕, contenido que puede desplazarse y pie con «Listo».
 * Solo para la sesión en vivo: la hoja general de la app (`KpknSheet`) no cambia.
 */

internal object LiveSheetTokens {
    val Line = Color(0x12FFFFFF)
    val Panel = Color(0xF5141513)
    val Veil = Color(0x99000000)
    val FieldFill = Color(0x0DFFFFFF)
    val FieldBorder = Color(0x1AFFFFFF)
    val ChipFill = Color(0x0DFFFFFF)
    val Danger = Color(0xFFFF9B92)
    val Muted = Color(0xFFB9B5AE)
    val Faint = WizardColors.textFaint
}

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Línea superior de 1 dp, como el `border-top: 1px solid var(--line)` de las filas del prototipo. */
internal fun Modifier.liveSheetTopLine(): Modifier = drawBehind {
    drawLine(LiveSheetTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

/**
 * Hoja del prototipo 02. [footer] por defecto es «Listo»; pasa `null` para las hojas sin pie (menús, opciones). El contenido
 * desplaza si no cabe; el pie queda fijo.
 */
@Composable
internal fun LiveSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    tag: String = "live-sheet",
    stableHeightFraction: Float? = null,
    footer: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = { close -> LiveSheetPrimary("Listo", close) },
    showClose: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    LiveSheetWindow {
    HeaderSheet(onDismiss = onDismiss, modifier = modifier, stableHeightFraction = stableHeightFraction, showDragHandle = false,
        scrim = LiveSheetTokens.Veil, panelTint = LiveSheetTokens.Panel) { close ->
        Column(Modifier.fillMaxWidth().testTag(tag)) {
            Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 33.dp, height = 3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x30FFFFFF)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    eyebrow?.let {
                        Text(it, style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                            lineHeight = 13.sp, letterSpacing = 1.6.sp), color = LiveSheetTokens.Faint)
                    }
                    Text(title, modifier = Modifier.padding(vertical = 6.dp).testTag("$tag-title"),
                        style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                            lineHeight = 24.sp, letterSpacing = (-0.4).sp), color = WizardColors.text)
                }
                if (showClose) Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = close)
                    .semantics { contentDescription = "Cerrar panel" }, contentAlignment = Alignment.Center) {
                    Icon(KpknLineIcons.Close, null, Modifier.size(18.dp), tint = LiveSheetTokens.Muted)
                }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 15.dp).testTag("$tag-body")) {
                content(close)
            }
            footer?.let { foot ->
                Column(Modifier.fillMaxWidth().liveSheetTopLine().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp)) {
                    foot(close)
                }
            }
        }
    }
    }
}

/**
 * Con anfitrión de capas la hoja se pinta en él (encima de toda la sesión). Sin anfitrión (pruebas, superficies sueltas) se abre en una
 * ventana propia a pantalla completa: nunca dentro de la fila o la tarjeta desde la que se abrió.
 */
@Composable
private fun LiveSheetWindow(content: @Composable () -> Unit) {
    if (com.example.kpkn.ui.components.LocalKpknOverlayHost.current != null) {
        content()
    } else {
        androidx.compose.ui.window.Popup(properties = androidx.compose.ui.window.PopupProperties(focusable = true, dismissOnBackPress = false,
            dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

private val Body12 = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp, lineHeight = 16.sp)
private val Body11 = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.sp, lineHeight = 17.sp)

/** `.sheet-subtitle`: 11 sp gris tenue, interlineado 1,55. */
@Composable
internal fun LiveSheetSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 13.dp), style = Body11, color = LiveSheetTokens.Faint)
}

/** `.section-label`: 10 sp, mayúsculas, espaciado 1,2. */
@Composable
internal fun LiveSheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(top = 19.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp),
        color = LiveSheetTokens.Faint)
}

/** `.menu-row`: fila de 48 dp con filete superior, icono, texto en gris y ›. */
@Composable
internal fun LiveSheetMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = KpknLineIcons.ChevronRight,
    small: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
    enabled: Boolean = true,
) {
    val color = if (danger) LiveSheetTokens.Danger else LiveSheetTokens.Muted
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).liveSheetTopLine().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { Icon(it, null, Modifier.size(17.dp), tint = color.copy(alpha = if (enabled) 1f else .4f)) }
        Text(label, Modifier.weight(1f), style = Body12, color = color.copy(alpha = if (enabled) 1f else .4f))
        small?.let { Text(it, style = Body11.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
        if (chevron) Icon(KpknLineIcons.ChevronRight, null, Modifier.size(13.dp), tint = LiveSheetTokens.Faint)
    }
}

/** `.choice-row`: opciones en píldora de 39 dp; la elegida, en tinta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> LiveSheetChoiceRow(
    options: List<Pair<T, String>>,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "live-sheet-choice",
) {
    FlowRow(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEachIndexed { index, (value, label) ->
            val on = selected(value)
            Box(Modifier.heightIn(min = 39.dp).clip(RoundedCornerShape(12.dp))
                .background(if (on) WizardColors.text else LiveSheetTokens.ChipFill)
                .border(1.dp, if (on) WizardColors.text else LiveSheetTokens.Line, RoundedCornerShape(12.dp))
                .clickable(role = Role.RadioButton) { onSelect(value) }
                .semantics { this.selected = on }
                .testTag("$tagPrefix-$index")
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = Body11.copy(fontWeight = FontWeight.Medium), color = if (on) Color(0xFF111111) else WizardColors.text)
            }
        }
    }
}

/** `.segmented`: pestañas subrayadas a todo el ancho. */
@Composable
internal fun <T> LiveSheetSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 7.dp, bottom = 13.dp).drawBehind {
        drawLine(LiveSheetTokens.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }.padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clickable(role = Role.Tab) { onSelect(value) }
                .semantics { this.selected = on }
                .drawBehind { if (on) drawLine(WizardColors.text, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
                contentAlignment = Alignment.Center) {
                Text(label, style = Body11.copy(fontWeight = FontWeight.SemiBold), color = if (on) WizardColors.text else LiveSheetTokens.Faint)
            }
        }
    }
}

/** Campo numérico o de texto de las hojas, con el relleno y el borde del prototipo. */
@Composable
internal fun LiveSheetField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    textAlign: TextAlign = TextAlign.Start,
    fontSize: Dp = 15.dp,
    singleLine: Boolean = true,
    minHeight: Dp = 44.dp,
    placeholder: String? = null,
    tag: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = fontSize.value.sp, color = WizardColors.text, textAlign = textAlign),
        cursorBrush = SolidColor(WizardColors.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.heightIn(min = minHeight).semantics { contentDescription = description }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        decorationBox = { inner ->
            Box(Modifier.clip(RoundedCornerShape(11.dp)).background(LiveSheetTokens.FieldFill)
                .border(1.dp, LiveSheetTokens.FieldBorder, RoundedCornerShape(11.dp)).padding(11.dp),
                contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = TextStyle(fontFamily = WizardFonts.body, fontSize = fontSize.value.sp, textAlign = textAlign),
                        color = Color(0xFF85817B))
                }
                inner()
            }
        },
    )
}

/** `.form-row`: etiqueta a la izquierda y campo corto a la derecha, en una fila de 57 dp con filete. */
@Composable
internal fun LiveSheetFormRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    tag: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 57.dp).liveSheetTopLine(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = Body12, color = WizardColors.text)
        LiveSheetField(value, onValueChange, label, Modifier.widthIn(min = 64.dp, max = 95.dp), keyboardType = keyboardType,
            textAlign = TextAlign.End, fontSize = 16.dp, tag = tag)
    }
}

/** `.form-row` con el conmutador del prototipo (38 × 23). */
@Composable
internal fun LiveSheetToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 57.dp).liveSheetTopLine()
        .clickable(role = Role.Switch) { onToggle(!checked) }
        .semantics { toggleableState = ToggleableState(checked); stateDescription = if (checked) "Activado" else "Desactivado" }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Body12, color = WizardColors.text)
        Box(Modifier.size(width = 38.dp, height = 23.dp).clip(RoundedCornerShape(13.dp))
            .background(if (checked) Color(0x38F2EEE6) else Color(0x18FFFFFF)).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(17.dp).clip(CircleShape).background(WizardColors.text))
        }
    }
}

/** `.input-label`: etiqueta pequeña encima de un campo a todo el ancho. */
@Composable
internal fun LiveSheetLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
    placeholder: String? = null,
    tag: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, lineHeight = 15.sp), color = LiveSheetTokens.Muted)
        LiveSheetField(value, onValueChange, label, Modifier.fillMaxWidth(), keyboardType = keyboardType,
            fontSize = if (multiline) 12.dp else 15.dp, singleLine = !multiline, minHeight = if (multiline) 66.dp else 44.dp,
            placeholder = placeholder, tag = tag)
    }
}

/** `.inline-note`: texto con un filete izquierdo. */
@Composable
internal fun LiveSheetInlineNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(vertical = 15.dp).drawBehind {
        drawLine(Color(0x37FFFFFF), Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
    }.padding(start = 12.dp), style = Body11.copy(lineHeight = 19.sp), color = LiveSheetTokens.Muted)
}

/** `.v2-history-note`: dato destacado con filete izquierdo (etiqueta pequeña, valor en Syne 17 y texto). */
@Composable
internal fun LiveSheetHistoryNote(value: String, modifier: Modifier = Modifier, label: String? = null, body: String? = null,
    action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp).drawBehind {
        drawLine(Color(0x2DFFFFFF), Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
    }.padding(horizontal = 13.dp, vertical = 9.dp)) {
        label?.let { Text(it, style = Body11.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint) }
        Text(value, style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp),
            color = WizardColors.text)
        body?.let { Text(it, Modifier.padding(top = 4.dp), style = Body11.copy(fontSize = 12.sp, lineHeight = 19.sp), color = LiveSheetTokens.Muted) }
        action?.invoke()
    }
}

/** `.v2-short-copy`: texto breve en gris. */
@Composable
internal fun LiveSheetShortCopy(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(text, modifier.padding(vertical = 12.dp), style = Body11.copy(lineHeight = 18.sp),
        color = if (error) Color(0xFFFFB5AD) else LiveSheetTokens.Muted)
}

/** `.primary.full`: píldora de tinta de 46 dp. */
@Composable
internal fun LiveSheetPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: ImageVector? = null, tag: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp))
        .background(WizardColors.text.copy(alpha = if (enabled) 1f else .35f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, Modifier.size(16.dp).padding(end = 0.dp), tint = Color(0xFF0B0B0B)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = Body12.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF0B0B0B))
    }
}

/** `.secondary.full`: píldora con borde. */
@Composable
internal fun LiveSheetSecondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: ImageVector? = null, tag: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp))
        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(23.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = WizardColors.text.copy(alpha = if (enabled) 1f else .4f)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = Body12.copy(fontWeight = FontWeight.SemiBold), color = WizardColors.text.copy(alpha = if (enabled) 1f else .4f))
    }
}

/** `.text-action.wide-text`: acción de texto centrada. */
@Composable
internal fun LiveSheetTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, wide: Boolean = true) {
    Box(modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier).heightIn(min = 39.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = if (wide) Alignment.Center else Alignment.CenterStart) {
        Text(text, style = Body11, color = if (danger) LiveSheetTokens.Danger else LiveSheetTokens.Muted)
    }
}

/** `.v2-options-grid`: rejilla de dos columnas con icono y texto, sin ›. */
@Composable
internal fun LiveSheetOptionsGrid(items: List<Triple<ImageVector, String, () -> Unit>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { (icon, label, onClick) ->
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).liveSheetTopLine().clickable(role = Role.Button, onClick = onClick)
                        .semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, Modifier.size(20.dp), tint = LiveSheetTokens.Muted)
                        Text(label, style = Body12, color = LiveSheetTokens.Muted)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** `.number-grid`: rejilla de seis columnas de botones de 43 dp. */
@Composable
internal fun <T> LiveSheetNumberGrid(values: List<Pair<T, String>>, selected: (T) -> Boolean, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, columns: Int = 6, tagPrefix: String = "live-sheet-number",
    marked: (T) -> Boolean = { false }, markLabel: String = "plan") {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (index, entry) ->
                    val (value, label) = entry
                    val on = selected(value)
                    val mark = marked(value)
                    Box(Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (on) WizardColors.text else Color(0x0FFFFFFF))
                        .then(if (mark && !on) Modifier.border(1.dp, Color(0x66F2EEE6), RoundedCornerShape(11.dp)) else Modifier)
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .semantics { this.selected = on; if (mark) stateDescription = markLabel }.testTag("$tagPrefix-$index"),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = Body12.copy(fontSize = 13.sp), color = if (on) Color(0xFF111111) else WizardColors.text)
                            if (mark) Text(markLabel, style = Body11.copy(fontSize = 8.sp, lineHeight = 9.sp),
                                color = if (on) Color(0xFF111111) else LiveSheetTokens.Faint)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Filete horizontal de las hojas. */
@Composable
internal fun LiveSheetDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = LiveSheetTokens.Line)
