package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset

/**
 * Área táctil de 48 dp: en cada eje de tamaño FIJO menor de 48 (44, 40, 36, 34, 30 del lienzo) el contenido pulsable se mide con 48 y se
 * centra sobre el dibujo, desbordándolo; el elemento sigue ocupando lo mismo en la maqueta. Un eje que se ajusta al contenido o ya mide
 * 48 o más no se toca. El contenido de dentro debe ir centrado en ese eje.
 */
internal fun Modifier.asTouch48(): Modifier = this.layout { measurable, constraints ->
    val min = 48.dp.roundToPx()
    val fixedW = constraints.minWidth == constraints.maxWidth && constraints.maxWidth < min
    val fixedH = constraints.minHeight == constraints.maxHeight && constraints.maxHeight < min
    if (!fixedW && !fixedH) {
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        val p = measurable.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = if (fixedW) min else constraints.minWidth,
                maxWidth = if (fixedW) min else constraints.maxWidth,
                minHeight = if (fixedH) min else constraints.minHeight,
                maxHeight = if (fixedH) min else constraints.maxHeight,
            ),
        )
        val w = if (fixedW) constraints.maxWidth else p.width
        val h = if (fixedH) constraints.maxHeight else p.height
        layout(w, h) { p.place((w - p.width) / 2, (h - p.height) / 2) }
    }
}

/**
 * Pulsable sin onda, con área táctil de 48 dp ([asTouch48]). Los estados `:active` los lleva cada pieza con [source]
 * (`collectIsPressedAsState`).
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.asClick(
    role: Role = Role.Button,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    source: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    Modifier
        .asTouch48()
        .combinedClickable(
            interactionSource = source ?: remember { MutableInteractionSource() },
            indication = null,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/**
 * `margin` negativo del lienzo: [top] y [bottom] cambian lo que ocupa el elemento; [start] negativo lo ensancha por la izquierda
 * (un elemento que se estira ocupa su ancho más ese margen).
 */
internal fun Modifier.negMargin(top: Dp = 0.dp, bottom: Dp = 0.dp, start: Dp = 0.dp): Modifier = this.layout { measurable, constraints ->
    val t = top.roundToPx()
    val b = bottom.roundToPx()
    val s = start.roundToPx()
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth - s else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = if (constraints.hasBoundedWidth) width else 0, maxWidth = width))
    layout(constraints.maxWidth.coerceAtMost(placeable.width), (placeable.height + t + b).coerceAtLeast(0)) { placeable.place(s, t) }
}

/** `.as-mod`: módulo de datos (fondo `#121214`, filete de 1 px, radio 22, relleno 14/16/16). */
@Composable
internal fun AsModule(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 17.dp, top = 15.dp, end = 17.dp, bottom = 17.dp), // 16/14/16 + el filete de 1 px
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp))
            .padding(padding),
    ) { content() }
}

/** `.as-mh`: cabecera de módulo (rótulo en mayúsculas a la izquierda y, si hay, algo a la derecha). */
@Composable
internal fun AsModuleHeader(title: String, bottomGap: Dp = 10.dp, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = bottomGap).heightIn(min = 28.dp), // margin-bottom fuera del min-height
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AsText(title, 11f, color = AsColors.muted, weight = FontWeight.SemiBold, cap = 1.15f, lineHeight = 1.2f, letterSpacing = 1.3f, uppercase = true)
        if (trailing != null) trailing()
    }
}

/** `.as-cap`: nota de 12 con interlineado 1,4 en tinta apagada. */
@Composable
internal fun AsCaption(text: String, modifier: Modifier = Modifier, color: Color = AsColors.faint, align: TextAlign? = null) =
    AsText(text, 12f, modifier, color = color, lineHeight = 1.4f, align = align)

/** `.as-eye`: rótulo pequeño en mayúsculas. */
@Composable
internal fun AsEyebrow(text: String, modifier: Modifier = Modifier) =
    AsText(text, 10.5f, modifier, color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.15f, letterSpacing = 1.3f, uppercase = true)

/** `.as-link`: texto de acción en tinta con flecha opcional. */
@Composable
internal fun AsLink(
    text: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    minHeight: Dp = 36.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(minHeight).asClick(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leading != null) AsIcon(leading, 16.dp, AsColors.ink)
        AsText(text, 13f, color = AsColors.ink, weight = FontWeight.SemiBold, maxLines = 1)
        if (trailing != null) AsIcon(trailing, 14.dp, AsColors.ink)
    }
}

internal enum class AsTone { OK, OVER, NEUTRAL, BAD }

/** `.as-st`: etiqueta de estado (24 de alto, radio 12). */
@Composable
internal fun AsStatusPill(text: String, tone: AsTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        AsTone.OK -> AsColors.tint(AsColors.done, 0x1a) to AsColors.done
        AsTone.OVER -> AsColors.tint(AsColors.energy, 0x1a) to AsColors.energy
        AsTone.NEUTRAL -> AsColors.white(0x0d) to AsColors.muted
        AsTone.BAD -> AsColors.tint(AsColors.danger, 0x1a) to AsColors.danger
    }
    Box(
        modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { AsText(text, 11.5f, color = fg, weight = FontWeight.SemiBold, cap = 1.15f, maxLines = 1) }
}

/** `.as-seg`: control segmentado (34 de alto por opción; `big` = 40 de alto con radio 22). */
@Composable
internal fun AsSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    descriptions: List<String>? = null,
    /** Opciones con un punto de aviso tras la etiqueta (el apartado Límites cuando choca). */
    dots: Set<Int> = emptySet(),
) {
    val outer = if (big) 22.dp else 18.dp
    val inner = if (big) 18.dp else 15.dp
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(outer))
            .background(AsColors.inset)
            .border(1.dp, AsColors.line, RoundedCornerShape(outer))
            .padding(if (big) 5.dp else 4.dp), // relleno de 3 (4) más el filete de 1 px, que en el lienzo suma tamaño
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            // pulido:as-T10 as-seg button — transición de fondo y color de 0,2 s (ease)
            val segBg by asColorState(if (on) AsColors.segOn else AsColors.segOn.copy(alpha = 0f), 200)
            val segFg by asColorState(if (on) AsColors.ink else AsColors.faint, 200)
            Box(
                Modifier
                    .weight(1f)
                    .height(if (big) 40.dp else 34.dp)
                    .clip(RoundedCornerShape(inner))
                    .background(segBg)
                    .semantics { this.selected = on; this.role = Role.Tab; descriptions?.getOrNull(index)?.let { contentDescription = it } }
                    .asClick(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsText(
                        label, 12.5f,
                        color = segFg,
                        weight = FontWeight.SemiBold, cap = 1.12f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (index in dots) Box(Modifier.padding(start = 4.dp).size(7.dp).clip(CircleShape).background(AsColors.energy))
                }
            }
        }
    }
}

/** `.as-chip`: chip de 36 de alto; `on` = tinta; `ghost` = borde discontinuo. */
@Composable
internal fun AsChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // pulido:as-T02 as-chip — fondo y color 0,2 s ease
    val shape = RoundedCornerShape(18.dp)
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) }
    val chipBg by asColorState(if (on) AsColors.ink else if (ghost) AsColors.cell.copy(alpha = 0f) else AsColors.cell, 200)
    val chipFg by asColorState(if (on) AsColors.onInk else AsColors.muted, 200)
    Row(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(chipBg)
            .then(
                if (ghost) Modifier.drawBehind {
                    drawRoundRect(
                        color = AsColors.white(0x30),
                        topLeft = Offset(0.5f, 0.5f),
                        size = Size(size.width - 1f, size.height - 1f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                    )
                } else Modifier,
            )
            .semantics { this.selected = on }
            .asClick(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) AsIcon(leading, 14.dp, chipFg)
        AsText(
            text, 13f,
            color = chipFg,
            weight = if (on) FontWeight.SemiBold else FontWeight.Medium, cap = 1.15f, maxLines = 1,
        )
        if (trailing != null) AsIcon(trailing, 14.dp, chipFg)
    }
}

/** `.as-flow`: filas de chips que saltan de línea (6 de hueco). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AsFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

internal enum class AsButtonKind { PRIMARY, GHOST, DANGER }

/** `.as-btn`: botón de 52 (44 pequeño), píldora de tinta; `off` = apagado. */
@Composable
internal fun AsButton(
    text: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    kind: AsButtonKind = AsButtonKind.PRIMARY,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    suffix: String? = null,
    onClick: () -> Unit,
) {
    val height = if (small) 44.dp else 52.dp
    val shape = RoundedCornerShape(height / 2)
    val (bg, fg, border) = when {
        !enabled -> Triple(AsColors.white(0x0d), AsColors.dim, null)
        kind == AsButtonKind.GHOST -> Triple(AsColors.white(0x0f), AsColors.ink, AsColors.white(0x1f))
        kind == AsButtonKind.DANGER -> Triple(AsColors.tint(AsColors.danger, 0x14), AsColors.danger, AsColors.tint(AsColors.danger, 0x33))
        else -> Triple(AsColors.ink, AsColors.onInk, null)
    }
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .then(if (enabled) Modifier.asClick(onClick = onClick) else Modifier.semantics { this.stateDescription = "No disponible" })
            .padding(horizontal = if (small) 18.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) AsIcon(leading, if (small) 16.dp else 18.dp, fg)
        AsText(text, if (small) 13.5f else 15f, color = fg, weight = FontWeight.SemiBold, cap = 1.15f, maxLines = 1)
        if (suffix != null) AsText(suffix, if (small) 13.5f else 15f, color = fg.copy(alpha = 0.6f), weight = FontWeight.SemiBold, cap = 1.15f, maxLines = 1)
    }
}

/** `.as-txa`: acción de texto de 44 de alto (Descartar, Cancelar). */
@Composable
internal fun AsTextAction(text: String, modifier: Modifier = Modifier, leading: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(44.dp).asClick(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) AsIcon(leading, 16.dp, AsColors.muted)
        AsText(text, 13.5f, color = AsColors.muted, weight = FontWeight.SemiBold, cap = 1.15f, maxLines = 1)
    }
}

/** `.as-ib`: botón de icono de 44 (círculo). */
@Composable
internal fun AsIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = AsColors.muted, size: Dp = 22.dp, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).asClick(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AsIcon(icon, size, tint) }
}

/** `.as-sw`: interruptor de 50 × 30. */
@Composable
internal fun AsSwitch(checked: Boolean, label: String, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    // pulido:as-T14 as-sw — fondo 0,2 s ease · pulido:as-T15 as-sw::after — «left» 0,2 s cubic-bezier(.2,.8,.2,1)
    val trackColor by asColorState(if (checked) AsColors.done else AsColors.white(0x26), 200)
    val thumbX by asFloatState(if (checked) 23f else 3f, 200, AsAnim.Std)
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .semantics { this.role = Role.Switch; this.contentDescription = label; this.stateDescription = if (checked) "Activado" else "Desactivado" }
            .asClick(role = Role.Switch) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset(thumbX.dp.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** `.as-stp`: valor con − y + (cada botón 44 × 40). [muted] = «—» sin valor. */
@Composable
internal fun AsStepper(
    value: String,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(AsColors.cell).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(width = 44.dp, height = 40.dp).clip(RoundedCornerShape(20.dp)).asClick(label = "Menos $label", onClick = onMinus), contentAlignment = Alignment.Center) {
            AsIcon(AsIcons.Minus, 18.dp, AsColors.muted)
        }
        // min-width 3,4ch (cifra de 16, tope de escala 1,2) · sin valor, 9ch de 13 (tope 1,12)
        val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val minWidth = if (muted) (9f * 0.6f * 13f * minOf(fs, 1.12f) / fs).dp else (3.4f * 0.6f * 16f * minOf(fs, 1.2f) / fs).dp
        Box(Modifier.widthIn(min = minWidth), contentAlignment = Alignment.Center) {
            if (muted) AsText(value, 13f, color = AsColors.faint, weight = FontWeight.Medium, cap = 1.12f, align = TextAlign.Center, maxLines = 1)
            // as-A05 as-flash: al cambiar la cifra parpadea del color de músculo a la tinta (0,6 s ease)
            else AsText(value, 16f, color = asFlashColor(value, AsColors.ink), weight = FontWeight.SemiBold, cap = 1.2f, tabular = true, align = TextAlign.Center, maxLines = 1)
        }
        Box(Modifier.size(width = 44.dp, height = 40.dp).clip(RoundedCornerShape(20.dp)).asClick(label = "Más $label", onClick = onPlus), contentAlignment = Alignment.Center) {
            AsIcon(AsIcons.Plus, 18.dp, AsColors.muted)
        }
    }
}

/** `.as-pk`: celda que abre un selector (descanso, tiempo): cifra y flecha. */
@Composable
internal fun AsPicker(text: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AsColors.cell)
            .asClick(label = "$label, $text", onClick = onClick)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // as-A05 as-flash: el valor del selector (descanso, tiempo) también parpadea al cambiar
        AsText(text, 15f, color = asFlashColor(text, AsColors.ink), weight = FontWeight.SemiBold, cap = 1.2f, tabular = true, maxLines = 1)
        AsIcon(AsIcons.Forward, 14.dp, AsColors.dim)
    }
}

/** `.as-empty`: estado vacío (icono en círculo de 56, título Syne 18, texto de 13,5 y, si hay, una acción). */
@Composable
internal fun AsEmpty(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    iconTint: Color = AsColors.muted,
    ringColor: Color = AsColors.white(0x26),
    topPadding: Dp = 22.dp,
    action: (@Composable () -> Unit)? = null,
) {
    // pulido:as-S03 as-empty .g — anillo interior de 1,5 px blanco al 15 %
    Column(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = topPadding, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // as-S03: `inset 0 0 0 1.5px` (borde-interior con su valor exacto; el color lo pone el estado: blanco al 15 %, o verde en «todo en orden»)
        Box(
            Modifier.size(iconSize).clip(CircleShape)
                .kpknInset(KpknShadow.BordeInterior.spec.copyLayer(0) { it.copy(spread = 1.5.dp, color = ringColor) }, CircleShape),
            contentAlignment = Alignment.Center,
        ) { AsIcon(icon, if (iconSize > 50.dp) 24.dp else 24.dp, iconTint) }
        AsText(title, 18f, Modifier.padding(top = 4.dp), weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f, align = TextAlign.Center)
        AsText(body, 13.5f, Modifier.widthIn(max = 243.dp), color = AsColors.muted, lineHeight = 1.5f, align = TextAlign.Center) // max-width: 30ch
        if (action != null) action()
    }
}

/** Un par de líneas de fila: título a la izquierda y control a la derecha (`.as-rr`, 56 de alto mínimo). */
@Composable
internal fun AsRuleRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subTone: AsTone? = null,
    first: Boolean = false,
    right: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (first) Modifier else Modifier.drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AsText(title, 14.5f, weight = FontWeight.Medium)
            if (sub != null) {
                val color = when (subTone) {
                    AsTone.OVER -> AsColors.energy
                    AsTone.OK -> AsColors.done
                    else -> AsColors.faint
                }
                AsText(sub, 12f, Modifier.padding(top = 2.dp), color = color, lineHeight = 1.35f)
            }
        }
        right()
    }
}

/** Línea horizontal de 1 px del lienzo. */
@Composable
internal fun AsDivider(modifier: Modifier = Modifier, color: Color = AsColors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
