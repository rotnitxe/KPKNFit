package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import com.example.kpkn.screens.onboarding.design.entreno.plan.ellipsizeAtWord
import kotlinx.coroutines.delay

/**
 * Cómo se muestra una página dentro de la página larga.
 *
 *  - [Completed]: ya confirmada, plegada en una fila-resumen que se puede tocar para editarla.
 *  - [Active]: la que se contesta ahora, desplegada del todo.
 *  - [Peek]: la siguiente; solo asoma (etiqueta y título tenues, control desenfocado y
 *    desvaneciéndose hacia abajo), inerte hasta que el check la active.
 */
enum class WizardPageMode { Completed, Active, Peek }

/** Cuánto tarda en componerse el control de un paso que asoma: lo que dura la llegada (el deslizado) y un respiro. */
private const val PeekContentDelayMillis = WizardMotion.SlideMillis + 80L

/** Con «reducir movimiento» no hay deslizado que esperar: basta un respiro para que el cuadro de la confirmación quede libre. */
private const val PeekContentDelayReducedMillis = 120L

/** Lo que tarda en aparecer el control que se compuso tarde. */
private const val PeekContentFadeMillis = 260

/**
 * Una página de la página larga. No es una tarjeta: es un tramo de la misma superficie negra,
 * separado del siguiente por un filete y por aire.
 *
 * La misma pieza pasa de «asoma» a «activa» a «plegada» animando dos números, `focus` (0 asoma,
 * 1 activa) y `collapse` (0 desplegada, 1 plegada). Así el paso siguiente no aparece de golpe: se
 * desenfoca de menos a nada mientras el anterior se pliega y el scroll lo sube hasta su sitio,
 * todo con la misma duración.
 *
 * Solo se compone lo que se ve: una página plegada no compone su paso (una historia larga no
 * cuesta nada) y una activa no compone su fila-resumen.
 *
 * El contrato de pruebas se conserva: la sección activa lleva `setup-step-<ID>` ([stepTag]) y la
 * fila-resumen `setup-summary-<ID>` ([summaryTag]).
 */
@Composable
fun WizardPageItem(
    mode: WizardPageMode,
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
    /**
     * Alto, en píxeles, de la ventana por la que asoma la página siguiente. El host lo calcula para
     * que el asomo llegue hasta el borde inferior de la pantalla en vez de cortarse a media altura.
     */
    peekWindowPx: Int,
    modifier: Modifier = Modifier,
    /**
     * Mantener compuesta la sección aunque la fila ya esté plegada (se usa para la ÚLTIMA confirmada).
     * Volver atrás despliega esa fila: si su paso hubiera que componerlo de cero en ese momento, el
     * primer fotograma del deslizado se perdería en la composición. Va oculta y fuera de TalkBack.
     */
    keepCard: Boolean = false,
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
    val showCard by remember(keepCard) { derivedStateOf { keepCard || collapse.value < 0.999f } }
    val folded by remember { derivedStateOf { collapse.value >= 0.999f } }
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

    // El control del paso que asoma se compone DESPUÉS de la animación de llegada, no en el mismo cuadro en que el paso anterior
    // se confirma: ese cuadro ya lleva el deslizado de la página, el plegado de la fila y el enfoque del paso activo, y componer
    // además un control entero (cuadrículas de decenas de símbolos, listas de objetivos) lo alargaba hasta 250–500 ms en una
    // compilación de depuración. Mientras llega solo se ve la etiqueta, el título y el subtítulo; el control entra con un fundido
    // corto. Si la persona confirma antes, el paso ya es el activo y se compone en el acto (nunca se hace esperar). Una vez
    // compuesto, no vuelve a quitarse.
    var contentReady by remember { mutableStateOf(mode != WizardPageMode.Peek) }
    LaunchedEffect(mode) {
        if (!contentReady) {
            if (mode == WizardPageMode.Peek) {
                delay(if (reducedMotion) PeekContentDelayReducedMillis else PeekContentDelayMillis)
            }
            contentReady = true
        }
    }
    val composeContent = contentReady || mode != WizardPageMode.Peek
    val contentReveal = animateFloatAsState(
        targetValue = if (composeContent) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = PeekContentFadeMillis),
        label = "wizard-page-content",
    )

    val density = LocalDensity.current
    val chipPx = with(density) { WizardSpacing.summaryRowHeightFor(density.fontScale).roundToPx() }

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
                    // Asoma: nítido arriba (etiqueta y título) y cada vez más desvanecido hacia el borde
                    // inferior, donde el contenido se pierde sin un corte.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.20f to Color.Black.copy(alpha = lerp(0.78f, 1f, f)),
                            0.48f to Color.Black.copy(alpha = lerp(0.34f, 1f, f)),
                            0.78f to Color.Black.copy(alpha = lerp(0.08f, 1f, f)),
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
            .pageHeight(collapse = { collapse.value }, focus = { focus.value }, peekPx = peekWindowPx, chipPx = chipPx),
    ) {
        if (showCard) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 1f - smoothStep(collapse.value, 0.7f, 1f) }
                    .onSizeChanged { onNaturalHeight(it.height) }
                    // Plegada y solo mantenida: invisible para TalkBack, no solo transparente.
                    .then(if (folded) Modifier.clearAndSetSemantics { } else Modifier)
                    .then(if (mode == WizardPageMode.Active) Modifier.testTag(stepTag) else Modifier),
            ) {
                WizardSectionBody(
                    eyebrow = eyebrow,
                    title = title,
                    subtitle = subtitle,
                    focus = { focus.value },
                    inert = inert,
                    composeContent = composeContent,
                    contentReveal = { contentReveal.value },
                    content = content,
                )
            }
        }
        if (showChip) {
            WizardSummaryRow(
                label = summaryLabel,
                value = summaryValue,
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
 * Cuerpo de un paso, sin contenedor: etiqueta, título, subtítulo y control directamente sobre la
 * página, con la misma anatomía en todos. Tamaños y pesos salen solo de [WizardTypography].
 *
 * [focus] (0..1) apaga lo que no debe verse nítido todavía: el título y el subtítulo bajan a un
 * tenue, el control se desenfoca y un filete lo separa del paso anterior. El desenfoque se aplica
 * SOLO al control, nunca a la etiqueta, al título ni al contenedor.
 */
@Composable
private fun WizardSectionBody(
    eyebrow: String,
    title: String,
    subtitle: String?,
    focus: () -> Float,
    inert: Boolean,
    composeContent: Boolean,
    contentReveal: () -> Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val maxBlurPx = with(density) { 22.dp.toPx() }
    Box(
        modifier = Modifier
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
                .padding(horizontal = WizardSpacing.gutter),
        ) {
            Spacer(Modifier.height(WizardSpacing.sectionPadTop))
            Text(
                text = eyebrow.uppercase(),
                style = WizardTypography.eyebrow,
                color = WizardColors.textFaint,
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
                        alpha = lerp(0.6f, 1f, f) * contentReveal()
                        val radius = (1f - f) * maxBlurPx
                        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                    },
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
            ) {
                if (composeContent) content()
            }
            Spacer(Modifier.height(WizardSpacing.sectionPadBottom))
        }
        // Filete superior: separa la página que asoma de la activa; desaparece al enfocarse
        // (entonces el separador es el de la fila-resumen que queda encima).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = WizardSpacing.gutter)
                .height(1.dp)
                .graphicsLayer { alpha = 1f - focus() }
                .background(WizardColors.divider),
        )
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
 * Fila-resumen de un paso confirmado: una marca, la etiqueta corta arriba, el valor en hasta dos líneas
 * abajo y un lápiz si se puede volver a editar. Sin tarjeta: una fila de lista con un filete
 * inferior. Misma altura ([WizardSpacing.summaryRowHeightFor]) en todas.
 *
 * El valor nunca acaba a media palabra: si en dos líneas no cabe, se corta en la última palabra entera con «…» (ver
 * [SummaryValue]). TalkBack lo oye entero.
 */
@Composable
fun WizardSummaryRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gutterPx = with(density) { WizardSpacing.gutter.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.summaryRowHeightFor(density.fontScale))
            .drawBehind {
                drawLine(
                    color = WizardColors.divider,
                    start = Offset(gutterPx, size.height - strokePx / 2f),
                    end = Offset(size.width - gutterPx, size.height - strokePx / 2f),
                    strokeWidth = strokePx,
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Editar $label", onClick = onClick)
                } else {
                    Modifier
                },
            )
            // Va DESPUÉS de `clickable`: conserva su rol y su acción y descarta el texto de los hijos, que puede ir cortado.
            .clearAndSetSemantics { contentDescription = "$label: $value" }
            .padding(horizontal = WizardSpacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, WizardColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(13.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = WizardTypography.note,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SummaryValue(value)
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

/**
 * El valor de una fila-resumen: hasta [WizardSpacing.SUMMARY_VALUE_LINES] líneas y, si aun así no cabe, cortado en la última
 * palabra entera que cabe más «…» ([ellipsizeAtWord]). El «…» de Compose parte palabras («Press banc…», «a me…»): aquí el
 * recorte se mide, igual que la descripción del detalle del programa. Solo las filas que no caben pagan la segunda medida.
 */
@Composable
private fun SummaryValue(value: String) {
    val lines = WizardSpacing.SUMMARY_VALUE_LINES
    // Cuántos caracteres del valor caben: baja, midiendo, hasta que entran en las líneas sin partir una palabra.
    var keep by remember(value) { mutableIntStateOf(value.length) }
    val shown = if (keep >= value.length) value else ellipsizeAtWord(value, keep)
    Text(
        text = shown,
        style = WizardTypography.cardTitle,
        color = WizardColors.text,
        maxLines = lines,
        // Lo que sobra no se pinta: el corte limpio lo hace `ellipsizeAtWord`, no el «…» de Compose.
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow) {
                val visibleEnd = layout.getLineEnd(lines - 1, visibleEnd = true)
                // Con el «…» puesto, un corte justo en el borde de la línea lo empuja a la siguiente: se baja un carácter más.
                val next = if (keep >= value.length) visibleEnd else minOf(visibleEnd, keep) - 1
                if (next in 1 until keep) keep = next
            }
        },
    )
}
