package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

/*
 * Las piezas de las hojas m-* del lienzo v6 (styles.css: `.sheet`, `.sheet h3`, `.sheet .sub`, `.ey`, `.mi`, `.mgroup`, `.ratio`, `.field`,
 * `.chip`, `.seg`, `.act`, `.tl`), con sus medidas. Todas las hojas y menús de Program Detail se arman con ellas: así ninguna hoja vuelve a
 * tener su propia piel. Sin estado.
 */

/** `.sheet h3`: Syne Bold 21 sp con −0,4 sp. */
internal val LienzoSheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.4).sp,
)

/** `.sheet .sub`: Inter 13 sp con 1,45 de interlineado. */
internal val LienzoSubStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)

/** `.ey`: Inter SemiBold 11 sp con 1,2 sp, en mayúsculas. */
internal val LienzoEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp,
)

/** `.mi strong`: Inter SemiBold 14,5 sp. */
internal val LienzoRowTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)

/** `.cap` y `.mi span`: Inter 12 sp. */
internal val LienzoCapStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** `.mgroup`: Inter SemiBold 10,5 sp con 1,3 sp. */
internal val LienzoGroupStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.3.sp,
)

/** `.ratio` y el texto de la hoja: Inter 13 sp. */
internal val LienzoRatioStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

/** El valor de una fila `.ratio` (`b` de peso 500) con cifras tabulares (`.tn`). */
internal val LienzoRatioValueStyle = LienzoRatioStyle.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "'tnum' 1")

/** `.body`: Inter 14 sp con 1,45. */
internal val LienzoBodyStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

/** `.act`: Inter SemiBold 13 sp. */
private val LienzoActStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)

/** `.chip`: Inter Medium 12 sp. */
private val LienzoChipStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

/** `.seg button`: Inter SemiBold 13 sp. */
private val LienzoSegStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)

/** `.field small` (11 sp) y `.field div` (15 sp). */
private val LienzoFieldLabelStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
internal val LienzoFieldValueStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)

/** `.tl strong` (13,5 sp SemiBold) y `.tl span` (12 sp con 1,4). */
private val LienzoTimelineTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)
private val LienzoTimelineSubStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)

/** `--dim` del lienzo: el gris de lo que está fuera de alcance. */
internal val LienzoDim = Color(0xFF5F5C57)

/** La tinta oscura sobre un chip elegido (`#111`). */
private val ChipInk = Color(0xFF111111)

/**
 * Una hoja m-* (`.sheet`): la hoja de vidrio de KPKN con el velo `#000a` detrás y el contenido con 18 dp a los lados y 30 dp abajo. Tocar
 * fuera la cierra salvo con [dismissible] en falso (mientras guarda). El contenido se desplaza si no cabe.
 */
@Composable
internal fun ProgramLienzoSheet(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KpknSheet(onDismissRequest = { if (dismissible) onDismiss() }, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
            content = content,
        )
    }
}

/**
 * La cabecera de una hoja m-*: el [eyebrow] (`.ey`), el título (`.sheet h3`, 6 dp por debajo del eyebrow) y el [sub] (`.sheet .sub`, 12 dp
 * hasta lo siguiente). Lo que va null no se pinta.
 */
@Composable
internal fun LienzoSheetTitle(title: String, eyebrow: String? = null, sub: String? = null) {
    eyebrow?.let { Text(it.uppercase(Locale("es")), style = LienzoEyebrowStyle, color = WizardColors.textMuted) }
    Text(
        text = title,
        style = LienzoSheetTitleStyle,
        color = WizardColors.text,
        modifier = Modifier
            .padding(top = if (eyebrow != null) 8.dp else 2.dp, bottom = 4.dp)
            .semantics { heading() },
    )
    if (sub != null) {
        Text(sub, style = LienzoSubStyle, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 12.dp))
    } else {
        Spacer(Modifier.height(8.dp))
    }
}

/** `.mgroup`: el rótulo de un grupo, 16 dp por encima y 4 dp por debajo. */
@Composable
internal fun LienzoGroup(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = LienzoGroupStyle,
        color = WizardColors.textFaint,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** El filete de 1 dp (`--div`) entre filas. */
@Composable
internal fun LienzoDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

/** El círculo tonal de 38 dp con el icono de 19 dp de una fila `.mi`. [tint] es `muted` o `danger`. */
@Composable
internal fun LienzoRowIcon(icon: ImageVector, tint: Color = WizardColors.textMuted) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.047f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = tint)
    }
}

/**
 * Una fila `.mi`: icono en círculo tonal, título y subtítulo (o nada), 54 dp como mínimo y filete arriba con [divider]. [danger] pinta el
 * título y el icono en `danger`. [enabled] en falso la apaga al 38 %.
 */
@Composable
internal fun LienzoMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    sub: String? = null,
    divider: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (danger) WizardColors.danger else WizardColors.textMuted
    Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)) {
        if (divider) LienzoDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .heightIn(min = 54.dp)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LienzoRowIcon(icon = icon, tint = tint)
            LienzoRowTexts(title = title, sub = sub, titleColor = if (danger) WizardColors.danger else WizardColors.text, modifier = Modifier.weight(1f))
        }
    }
}

/** El título (`.mi strong`) y el subtítulo (`.mi span`, 2 dp debajo) de una fila. */
@Composable
internal fun LienzoRowTexts(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = WizardColors.text,
    titleWeight: FontWeight = FontWeight.SemiBold,
) {
    Column(modifier = modifier) {
        Text(text = title, style = LienzoRowTitleStyle.copy(fontWeight = titleWeight), color = titleColor)
        sub?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(text = it, style = LienzoCapStyle, color = WizardColors.textFaint)
        }
    }
}

/** El radio del lienzo (`radio()`): 20 dp con borde de 2 dp (tinta si está elegido, blanco al 25 % si no) y el punto de 9 dp. */
@Composable
internal fun LienzoRadio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) WizardColors.text else Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(WizardColors.text, CircleShape))
    }
}

/**
 * Una opción con radio (`opt()`): 48 dp como mínimo, el radio y el texto (600 si está elegida, 500 si no) con su subtítulo. La primera de la
 * hoja va sin filete ([divider]).
 */
@Composable
internal fun LienzoOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    sub: String? = null,
    divider: Boolean = true,
    minHeight: Dp = 48.dp,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)) {
        if (divider) LienzoDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .heightIn(min = minHeight)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LienzoRadio(selected = selected)
            LienzoRowTexts(
                title = label,
                sub = sub,
                titleWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Un chip del lienzo (`.chip`): [height] de alto (30 dp; 34 en `chipsWrap`), 11 dp a los lados, `inset` con texto `muted`; elegido, tinta
 * con texto `#111`.
 */
@Composable
internal fun LienzoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(height)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) WizardColors.text else KpknSurface.inset, RoundedCornerShape(15.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = LienzoChipStyle, color = if (selected) ChipInk else WizardColors.textMuted, maxLines = 1)
    }
}

/** `chipsWrap()`: chips de 34 dp que saltan de línea, 6 dp entre ellos. [selected] dice cuáles están elegidos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LienzoChipsWrap(
    options: List<String>,
    selected: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    height: Dp = 34.dp,
    enabled: Boolean = true,
    tagOf: ((Int) -> String)? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            LienzoChip(
                text = label,
                selected = selected(index),
                onClick = { onSelect(index) },
                height = height,
                enabled = enabled,
                tag = tagOf?.invoke(index),
            )
        }
    }
}

/**
 * El segmentado del lienzo (`.seg`): pista `#0d0d0d` con filete blanco al 6 % y 4 dp de margen; cada botón de 40 dp en `faint`; el elegido,
 * fondo `#272727` y tinta.
 */
@Composable
internal fun LienzoSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(track)
            .background(Color(0xFF0D0D0D), track)
            .border(1.dp, Color.White.copy(alpha = 0.063f), track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Color(0xFF272727) else Color.Transparent, RoundedCornerShape(20.dp))
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = LienzoSegStyle,
                    color = if (on) WizardColors.text else WizardColors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Una fila `.ratio`: filete arriba, 9 dp por arriba y por abajo, la etiqueta a la izquierda y [trailing] a la derecha (por defecto, [value]
 * en peso 500 con cifras tabulares).
 */
@Composable
internal fun LienzoRatioRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    labelStyle: TextStyle = LienzoRatioStyle,
    labelColor: Color = WizardColors.text,
    sub: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LienzoDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = labelStyle, color = labelColor)
                sub?.let { Text(it, style = LienzoCapStyle, color = WizardColors.textFaint) }
            }
            if (trailing != null) {
                trailing()
            } else if (value != null) {
                Text(value, style = LienzoRatioValueStyle, color = WizardColors.text, textAlign = TextAlign.End)
            }
        }
    }
}

/** El marco de `.field`: fondo negro al 23 %, filete blanco al 10 % y radio de 16 dp. */
internal fun Modifier.lienzoFieldFrame(): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.227f), shape)
        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
}

/**
 * Un campo `.field` editable: la etiqueta pequeña arriba (`small`) y el texto debajo (15 sp), con 10 × 14 dp de relleno y 8 dp por
 * encima. [placeholder] va en `muted` mientras está vacío. [suffix] («kg») sigue al texto.
 */
@Composable
internal fun LienzoTextField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    suffix: String? = null,
    enabled: Boolean = true,
    error: Boolean = false,
    tag: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    valueStyle: TextStyle = LienzoFieldValueStyle,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    topGap: Dp = 8.dp,
) {
    Column(
        modifier = modifier
            .padding(top = topGap)
            .lienzoFieldFrame()
            .then(if (error) Modifier.border(1.dp, WizardColors.danger, RoundedCornerShape(16.dp)) else Modifier)
            .padding(contentPadding),
    ) {
        label?.let { Text(it, style = LienzoFieldLabelStyle, color = WizardColors.textFaint) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = valueStyle.copy(color = WizardColors.text, textAlign = textAlign),
            cursorBrush = SolidColor(WizardColors.text),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (label != null) 3.dp else 0.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = valueStyle.copy(textAlign = textAlign), color = WizardColors.textMuted)
                        }
                        inner()
                    }
                    if (suffix != null && value.isNotEmpty()) {
                        Text(" $suffix", style = valueStyle, color = WizardColors.text)
                    }
                }
            },
        )
    }
}

/**
 * Una acción de texto `.act`: Inter SemiBold 13 sp, 36 dp como mínimo, el [icon] de 16 dp delante a 4 dp. [faint] la pinta en `muted`.
 * Con [centered] ocupa el ancho y va centrada.
 */
@Composable
internal fun LienzoTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    faint: Boolean = true,
    icon: ImageVector? = null,
    centered: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val color = if (faint) WizardColors.textMuted else WizardColors.text
    Row(
        modifier = modifier
            .then(if (centered) Modifier.fillMaxWidth() else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .defaultMinSize(minHeight = 36.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = LienzoActStyle, color = color)
    }
}

/** La línea de tiempo `.tl`: una raya de 1 dp a 4 dp del borde y, por entrada, un punto de 9 dp con su título y su línea pequeña. */
@Composable
internal fun LienzoTimeline(entries: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val line = Color.White.copy(alpha = 0.094f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 4.5.dp.toPx()
                drawLine(line, Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            },
    ) {
        entries.forEach { (title, sub) ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111111), CircleShape)
                        .border(2.dp, WizardColors.textMuted, CircleShape),
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = LienzoTimelineTitleStyle, color = WizardColors.text)
                    if (sub.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(sub, style = LienzoTimelineSubStyle, color = WizardColors.textFaint)
                    }
                }
            }
        }
    }
}

/**
 * Una hoja corta de confirmación (m-offdate / m-outofrange): título, [sub], la píldora principal y «Cancelar» como acción de texto. Con
 * [busy] la píldora se apaga y la hoja no se cierra.
 */
@Composable
internal fun ProgramConfirmSheet(
    title: String,
    sub: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    eyebrow: String? = null,
    confirmTag: String? = null,
) {
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(title = title, eyebrow = eyebrow, sub = sub)
        ProgramLienzoPill(
            text = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            tag = confirmTag,
        )
        LienzoTextAction(text = "Cancelar", onClick = onDismiss, centered = true, enabled = !busy, modifier = Modifier.padding(top = 6.dp))
    }
}

/** «Pegar» del lienzo (`paste`): la tablilla con su pinza. KpknLineIcons no lo trae. */
internal val LienzoPasteIcon: ImageVector = lineIcon(
    "KpknLine.ProgramPaste",
    listOf("M8 4.5h8a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-10a3 3 0 0 1 3-3z", "M9 4.5h6v3H9z", "M9 12h6", "M9 16h4"),
)

/** «Usar una foto» del lienzo (`eye`). KpknLineIcons no lo trae. */
internal val LienzoEyeIcon: ImageVector = lineIcon(
    "KpknLine.ProgramEye",
    listOf("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "M14.8 12a2.8 2.8 0 1 1-5.6 0 2.8 2.8 0 0 1 5.6 0z"),
)

/** «Cambiar reparto» del lienzo (`dumb`): la mancuerna. KpknLineIcons no la trae. */
internal val LienzoDumbbellIcon: ImageVector = lineIcon(
    "KpknLine.ProgramDumbbell",
    listOf("M3 10v4", "M6 8v8", "M18 8v8", "M21 10v4", "M6 12h12"),
)
