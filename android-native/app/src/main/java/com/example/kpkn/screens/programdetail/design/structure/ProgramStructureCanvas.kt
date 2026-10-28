package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale

/*
 * Piezas del lienzo v6 para la pestaña Estructura y sus hojas (docs/design/program-detail-v1/src/styles.css). Cada medida es la del CSS,
 * de px a dp/sp uno a uno: `.mod`, `.mod-h`, `.ey`, `.act`, `.group-l`, `.cap`, `.body`, `.blk`, `.wkc`, `.tline`, `.mi`, `.mgroup`,
 * `.field`, `.chip`, `.pill` y la cabecera de `.sheet`. Sin estado: lo que muestran llega ya calculado.
 */

/** Tinta, gris medio y gris tenue del lienzo (`--ink`, `--muted`, `--faint`): los mismos de la app. */
internal val StructureInk = WizardColors.text
internal val StructureMuted = WizardColors.textMuted
internal val StructureFaint = WizardColors.textFaint

/** `--module` y su filete `#ffffff12`. */
private val StructureModuleFill = Color(0xFF111111)
private val StructureModuleEdge = Color(0x12FFFFFF)

/** `--inset`: fondo de los círculos de semana y de las chips. */
internal val StructureInset = Color(0xFF1A1A1A)

/** `--div`: filete entre filas (`#ffffff0f`). */
internal val StructureDivider = Color(0x0FFFFFFF)

/** Texto negro sobre tinta (`#111`). */
internal val StructureOnInk = Color(0xFF111111)

/**
 * Color de cada fase en el lienzo (`--ph-acc`, `--ph-int`, `--ph-rea`, `--ph-del`, `--ph-com`). Realización es `--col` y descarga, `#7fcfa4`.
 */
internal fun structurePhaseColor(phase: ProgramPhase?): Color = when (phase) {
    ProgramPhase.ACCUMULATION -> Color(0xFFF49A6E)
    ProgramPhase.INTENSIFICATION -> Color(0xFFF7CF73)
    ProgramPhase.REALIZATION -> Color(0xFF8FB2FF)
    ProgramPhase.DELOAD -> Color(0xFF7FCFA4)
    ProgramPhase.COMPETITION -> Color(0xFFFF9B92)
    null -> StructureFaint
}

// ── Tipografía del lienzo ────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.ey`: Inter 600 11 / 1,2 de espaciado, en mayúsculas. */
internal val StructureEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp,
)

/** `.group-l`: Inter 600 11 / 1,3 de espaciado, en mayúsculas. */
internal val StructureGroupStyle = StructureEyebrowStyle.copy(letterSpacing = 1.3.sp)

/** `.act`: Inter 600 13. */
internal val StructureActionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

/** `.cap`: Inter 12 / 1,35. */
internal val StructureCaptionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** `.body`: Inter 14 / 1,45. */
internal val StructureBodyStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/** `.headline` a 17 px (Semana base): Syne 700 17 / 1,25. */
internal val StructureHeadlineStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    lineHeight = 21.sp,
    letterSpacing = (-0.3).sp,
)

/** `.blk-h strong`: Syne 700 16. */
internal val StructureBlockNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
)

/** `.wkc`: Syne 700 13, con cifras tabulares. */
internal val StructureWeekNumberStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** `.tline div`: Inter 600 10,5 / 0,3 de espaciado. */
internal val StructureTimelineStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 13.sp,
    letterSpacing = 0.3.sp,
)

/** `.mi strong`: Inter 600 14,5. */
internal val StructureRowTitleStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.5.sp,
    lineHeight = 19.sp,
)

/** `.sheet h3`: Syne 700 21 / 1,15, espaciado −0,4. */
internal val StructureSheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.4).sp,
)

/** `.sheet .sub`: Inter 13 / 1,45. */
internal val StructureSheetSubStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

/** `.mgroup`: Inter 600 10,5 / 1,3 de espaciado, en mayúsculas. */
internal val StructureSheetGroupStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.3.sp,
)

/** `.field small`: Inter 11. */
private val StructureFieldLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

/** `.field div`: Inter 15. */
internal val StructureFieldValueStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

/** `.chip`: Inter 500 12. */
private val StructureChipStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** `.pill`: Inter 600 15; `.pill.sm`: 13. */
private val StructurePillStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)
private val StructureSmallPillStyle = StructurePillStyle.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** Las cifras tabulares (`.tn`). */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "'tnum' 1")

private val SpanishLocale = Locale.forLanguageTag("es")

// ── Página ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.mod` (relleno 16 · 18 · 18) o `.mod.tight` (12 · 14), radio 24 y filete de 1 dp. Sin margen: lo pone la página. */
@Composable
internal fun StructureModule(
    modifier: Modifier = Modifier,
    tight: Boolean = false,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StructureModuleFill, shape)
            .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.08f), shape) else Modifier)
            .border(1.dp, accent?.copy(alpha = 0.24f) ?: StructureModuleEdge, shape)
            .padding(
                if (tight) PaddingValues(horizontal = 14.dp, vertical = 12.dp) else PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
            ),
        content = content,
    )
}

/** `.mod-h`: el eyebrow a la izquierda y la acción a la derecha, centrados; 28 dp de alto como mínimo. */
@Composable
internal fun StructureModuleHeader(label: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(SpanishLocale),
            style = StructureEyebrowStyle,
            color = StructureMuted,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            action()
        }
    }
}

/**
 * `.act`: acción de texto Inter 600 13, 36 dp de alto, con [leadingIcon] de 16 dp delante (4 dp de hueco) o [trailingIcon] detrás. Con
 * [faint] va en `--muted` (`.act.faint`); si no, en tinta, o en [color] si se da.
 */
@Composable
internal fun StructureAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    faint: Boolean = false,
    color: Color? = null,
) {
    val ink = color ?: if (faint) StructureMuted else StructureInk
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        leadingIcon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink) }
        Text(text = text, style = StructureActionStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailingIcon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink) }
    }
}

/** `.group-l`: etiqueta de grupo de la página, con márgenes 22 · 4 · 10. */
@Composable
internal fun StructureGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(SpanishLocale),
        style = StructureGroupStyle,
        color = StructureFaint,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 22.dp, end = 4.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

/**
 * `.mi`: fila de 54 dp como mínimo con el icono en su círculo de 38 dp (`#ffffff0c`), el título (14,5) y el detalle (12, `--faint`). Un
 * filete de `--div` la separa de la anterior salvo en la primera ([divider] = false). Con [danger], título e icono en `--danger`; con
 * [trailing], una acción a la derecha (las hojas sin escena del lienzo).
 */
@Composable
internal fun StructureMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    divider: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleInk = if (danger) WizardColors.danger else StructureInk
    val iconInk = if (danger) WizardColors.danger else StructureMuted
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(StructureDivider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0x0CFFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = iconInk)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = StructureRowTitleStyle, color = titleInk)
                detail?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(text = it, style = StructureCaptionStyle.copy(lineHeight = 16.sp), color = StructureFaint)
                }
            }
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
    }
}

/**
 * `.wkc`: círculo de 40 dp en `--inset` con el número o la letra en Syne 700 13. [current] lleva anillo de tinta de 2 dp (`.now`), [done]
 * un punto verde de 5 dp que asoma 5 dp por debajo (`.done:after`) y [filled] el relleno de tinta con texto negro (días de entreno).
 */
@Composable
internal fun StructureWeekCircle(
    text: String,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    done: Boolean = false,
    filled: Boolean = false,
) {
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (filled) StructureInk else StructureInset, CircleShape)
                .then(if (current) Modifier.border(2.dp, StructureInk, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = StructureWeekNumberStyle,
                color = when {
                    filled -> StructureOnInk
                    current -> StructureInk
                    else -> StructureMuted
                },
                maxLines = 1,
            )
        }
        if (done) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(WizardColors.done, CircleShape),
            )
        }
    }
}

/** `.wkc.add`: círculo «+» de 40 dp con borde discontinuo (`#ffffff30`) y sin relleno. */
@Composable
internal fun StructureAddWeekCircle(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0x30FFFFFF), CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = KpknLineIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp), tint = StructureMuted)
    }
}

/** Fila de letras L–D en círculos `.wkc`, rellenos los días de [trainingDays] (1 = lunes). Hueco de 6 dp. */
@Composable
internal fun StructureWeekdayRow(trainingDays: Set<Int>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_LETTERS.forEachIndexed { index, letter ->
            StructureWeekCircle(text = letter, filled = (index + 1) in trainingDays)
        }
    }
}

/** Las letras de los días del lienzo, de lunes a domingo. */
internal val WEEKDAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

/**
 * `.pill.sec.sm`: píldora secundaria pequeña, 40 dp de alto, borde de 1 dp en tinta al 24 % (`#f2eee63d`), Inter 600 13 y el [icon] de 16 dp
 * delante con 8 dp de hueco.
 */
@Composable
internal fun StructureSecondarySmallPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x3DF2EEE6), RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp), tint = StructureInk) }
        Text(text = text, style = StructureSmallPillStyle, color = StructureInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.pill.pri`: píldora principal de 52 dp, relleno de tinta, texto `#0c0c0c` Inter 600 15. Apagada, al 38 %. */
@Composable
internal fun StructurePrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) StructureInk else WizardColors.ctaDisabled, RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = StructurePillStyle,
            color = if (enabled) Color(0xFF0C0C0C) else WizardColors.ctaDisabledContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Hojas ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** Relleno lateral y final del contenido de una hoja del lienzo (`.sheet`: 18 a los lados y 30 abajo). */
internal val StructureSheetPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 30.dp)

/** `.sheet h3` y `.sheet .sub`: el título y, si hay, el texto bajo él con 12 dp de margen inferior. */
@Composable
internal fun StructureSheetHeader(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = StructureSheetTitleStyle,
        color = StructureInk,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { heading() },
    )
    if (subtitle != null) {
        Text(text = subtitle, style = StructureSheetSubStyle, color = StructureMuted, modifier = Modifier.padding(bottom = 12.dp))
    }
}

/** `.mgroup`: etiqueta de grupo dentro de una hoja, con márgenes 16 · 2 · 4. */
@Composable
internal fun StructureSheetGroup(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(SpanishLocale),
        style = StructureSheetGroupStyle,
        color = StructureFaint,
        modifier = modifier.padding(start = 2.dp, top = 16.dp, end = 2.dp, bottom = 4.dp),
    )
}

/** Forma, relleno (`#0000003a`) y filete (`#ffffff1a`) de `.field`. */
private val FieldShape = RoundedCornerShape(16.dp)
private val FieldFill = Color(0x3A000000)
private val FieldEdge = Color(0x1AFFFFFF)

/**
 * `.field` de solo lectura: la etiqueta pequeña (11, `--faint`) y el valor (15) debajo, con 3 dp de hueco; [trailing] a la derecha. Con
 * [onClick] es un botón. 8 dp de margen superior.
 */
@Composable
internal fun StructureField(
    label: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(FieldShape)
            .background(FieldFill, FieldShape)
            .border(1.dp, FieldEdge, FieldShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label != null) {
                Text(text = label, style = StructureFieldLabelStyle, color = StructureFaint)
                Spacer(Modifier.height(3.dp))
            }
            value()
        }
        trailing?.invoke()
    }
}

/** `.field` con texto que se escribe: la etiqueta encima y el valor en Inter 15; el marcador va en `--faint`. */
@Composable
internal fun StructureTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
) {
    StructureField(label = label, modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            textStyle = StructureFieldValueStyle.copy(color = if (enabled) StructureInk else StructureFaint),
            cursorBrush = SolidColor(StructureInk),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = StructureFieldValueStyle, color = StructureFaint)
                    }
                    inner()
                }
            },
        )
    }
}

/** `.chip`: 30 dp de alto, radio 15, relleno 11; elegida (`.on`) en tinta con texto `#111`. */
@Composable
internal fun StructureChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) StructureInk else StructureInset, RoundedCornerShape(15.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = StructureChipStyle, color = if (selected) StructureOnInk else StructureMuted, maxLines = 1)
    }
}

/** Acción de borrar de una hoja: `.act` en `--danger`, centrada y a todo el ancho, con 4 dp de margen superior. */
@Composable
internal fun StructureDangerAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StructureAction(
        text = text,
        onClick = onClick,
        color = WizardColors.danger,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/**
 * Interruptor del lienzo (e-library): 44 × 26 dp, radio 13. Apagado, `#333` con el botón `#888` a la izquierda; encendido, tinta con el
 * botón negro a la derecha.
 */
@Composable
internal fun StructureSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) StructureInk else Color(0xFF333333), RoundedCornerShape(13.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) StructureOnInk else Color(0xFF888888), CircleShape),
        )
    }
}

/** Texto centrado de una nota del lienzo (`.cap`) en [color]. */
@Composable
internal fun StructureCaption(text: String, modifier: Modifier = Modifier, color: Color = StructureFaint, center: Boolean = false) {
    Text(
        text = text,
        style = StructureCaptionStyle,
        color = color,
        textAlign = if (center) TextAlign.Center else null,
        modifier = modifier,
    )
}

/** Iconos del lienzo que el set KPKN Line no trae: «copy» de app.js, con el trazo del set (1,75, extremos redondeados). */
internal object StructureIcons {
    val Copy: ImageVector by lazy {
        ImageVector.Builder(name = "Structure.Copy", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                listOf(
                    "M11 8h6a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-6a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3z",
                    "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2",
                ).forEach { data ->
                    addPath(
                        pathData = PathParser().parsePathString(data).toNodes(),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.75f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
    }
}
