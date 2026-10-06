package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Cómo se muestra una página dentro de la página larga.
 *
 *  - [Completed]: ya confirmada, plegada en una fila-resumen que se puede tocar para editarla.
 *  - [Active]: la que se contesta ahora, desplegada del todo.
 *  - [Peek]: la siguiente; solo asoma (etiqueta y título nítidos, control desenfocado y
 *    desvaneciéndose), inerte hasta que el check la active.
 */
enum class WizardPageMode { Completed, Active, Peek }

/**
 * Una página de la página larga: la misma pieza pasa de «asoma» a «activa» a «plegada»
 * animando dos números, `focus` (0 asoma, 1 activa) y `collapse` (0 desplegada, 1 plegada).
 * Así el paso siguiente no aparece de golpe: se desenfoca de menos a nada mientras el
 * anterior se pliega y el scroll lo sube hasta su sitio, todo con la misma duración.
 *
 * Solo se compone lo que se ve: una página plegada no compone su paso (una historia larga
 * no cuesta nada) y una activa no compone su fila-resumen.
 *
 * El contrato de pruebas se conserva: la sección activa lleva `setup-step-<ID>` ([stepTag]) y
 * la fila-resumen `setup-summary-<ID>`.
 */
@Composable
fun WizardPageItem(
    mode: WizardPageMode,
    accent: Color,
    eyebrow: String,
    title: String,
    subtitle: String?,
    summaryLabel: String,
    summaryValue: String,
    stepTag: String,
    summaryTag: String,
    onEdit: (() -> Unit)?,
    onNaturalHeight: (Int) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = if (reducedMotion) {
        snap<Float>()
    } else {
        tween(durationMillis = WizardMotion.SlideMillis, easing = FastOutSlowInEasing)
    }
    val collapse = animateFloatAsState(
        targetValue = if (mode == WizardPageMode.Completed) 1f else 0f,
        animationSpec = spec,
        label = "wizard-page-collapse",
    )
    val focus = animateFloatAsState(
        targetValue = if (mode == WizardPageMode.Peek) 0f else 1f,
        animationSpec = spec,
        label = "wizard-page-focus",
    )
    val showCard by remember { derivedStateOf { collapse.value < 0.999f } }
    val showChip by remember { derivedStateOf { collapse.value > 0.001f } }
    val fading by remember { derivedStateOf { focus.value < 0.999f } }
    val folding by remember { derivedStateOf { collapse.value > 0.001f && collapse.value < 0.999f } }
    val inert by remember { derivedStateOf { focus.value < 0.999f && collapse.value < 0.5f } }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appear = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spec,
        label = "wizard-page-appear",
    )

    val density = LocalDensity.current
    val peekPx = with(density) { WizardSpacing.peekHeight.roundToPx() }
    val chipPx = with(density) { WizardSpacing.summaryRowHeight.roundToPx() }

    // Orden de modificadores: el recorte, el fundido y la máscara van POR FUERA de `pageHeight`
    // para que actúen sobre la ventana visible (la altura animada) y no sobre el tamaño natural.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .graphicsLayer {
                alpha = appear.value
                // Las máscaras necesitan una capa fuera de pantalla; solo se paga mientras la página
                // asoma o se pliega.
                compositingStrategy = if (fading || folding) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val f = focus.value
                if (f < 0.999f) {
                    // Asoma: opaco arriba (etiqueta y título) y desvanecido abajo (el control).
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.58f to Color.Black,
                            1f to Color.Black.copy(alpha = f),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                val c = collapse.value
                if (c > 0.001f && c < 0.999f) {
                    // Se pliega como un acordeón: lo de abajo se recorta con un fundido suave, no con un corte seco.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.72f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .pageHeight(collapse = { collapse.value }, focus = { focus.value }, peekPx = peekPx, chipPx = chipPx),
    ) {
        if (showCard) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 1f - smoothStep(collapse.value, 0.7f, 1f) }
                    .onSizeChanged { onNaturalHeight(it.height) }
                    .then(if (mode == WizardPageMode.Active) Modifier.testTag(stepTag) else Modifier),
            ) {
                WizardSectionCard(
                    eyebrow = eyebrow,
                    title = title,
                    subtitle = subtitle,
                    accent = accent,
                    focus = { focus.value },
                    inert = inert,
                    content = content,
                )
            }
        }
        if (showChip) {
            WizardSummaryRow(
                label = summaryLabel,
                value = summaryValue,
                accent = accent,
                onClick = onEdit,
                modifier = Modifier
                    .graphicsLayer { alpha = smoothStep(collapse.value, 0.7f, 1f) }
                    .testTag(summaryTag),
            )
        }
    }
}

/**
 * Altura mostrada = mezcla de tres alturas: la de «asoma» ([peekPx], o la natural si es menor),
 * la natural y la de la fila-resumen. El contenido se mide siempre a su tamaño natural; lo que
 * se anima es la ventana por la que se ve.
 */
private fun Modifier.pageHeight(
    collapse: () -> Float,
    focus: () -> Float,
    peekPx: Int,
    chipPx: Int,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val natural = placeable.height
    val open = lerp(minOf(natural, peekPx), natural, focus())
    val shown = lerp(open, chipPx, collapse()).coerceAtLeast(0)
    layout(placeable.width, shown) { placeable.place(0, 0) }
}

private fun smoothStep(value: Float, from: Float, to: Float): Float =
    ((value - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Sección de cristal de un paso, con la misma anatomía en todos: etiqueta pequeña, título,
 * subtítulo y control. Tamaños y pesos salen solo de [WizardTypography].
 *
 * [focus] (0..1) apaga o enciende lo que no debe verse nítido todavía: título y subtítulo bajan
 * a un tenue y el control se desenfoca. El desenfoque se aplica SOLO al control, nunca a la
 * etiqueta, al título ni al contenedor.
 */
@Composable
fun WizardSectionCard(
    eyebrow: String,
    title: String,
    subtitle: String?,
    accent: Color,
    focus: () -> Float,
    inert: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val maxBlurPx = with(density) { 18.dp.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (inert) {
                    // El paso que asoma no habla para TalkBack: solo anuncia cuál viene después.
                    Modifier.clearAndSetSemantics { contentDescription = "Siguiente: $title" }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wizardGlass(WizardShapes.section)
                .padding(WizardSpacing.sectionPadding),
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = WizardTypography.eyebrow,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = WizardTypography.stepTitle,
                color = WizardColors.text,
                modifier = Modifier
                    .graphicsLayer { alpha = lerp(0.62f, 1f, focus()) }
                    .semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = WizardTypography.stepSubtitle,
                    color = WizardColors.textMuted,
                    modifier = Modifier.graphicsLayer { alpha = lerp(0.5f, 1f, focus()) },
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val f = focus()
                        alpha = lerp(0.6f, 1f, f)
                        val radius = (1f - f) * maxBlurPx
                        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                    },
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
                content = content,
            )
        }
        if (inert) {
            // Un paso que asoma no se toca: se come los toques sin impedir que el dedo arrastre la página.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }
    }
}

/**
 * Fila-resumen de un paso confirmado: etiqueta corta arriba, valor en una línea abajo y un
 * lápiz si se puede volver a editar. Misma altura ([WizardSpacing.summaryRowHeight]) en todos.
 */
@Composable
fun WizardSummaryRow(
    label: String,
    value: String,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.summaryRowHeight)
            .wizardGlass(WizardShapes.summaryRow)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Editar $label", onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = WizardColors.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
