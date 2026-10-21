package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.LiftVariantOption
import com.example.kpkn.domain.onboarding.LiftVariants
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.LiftSymbol
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.rememberFigClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Lado de la figura de cada levantamiento dentro de su celda. */
private val CellSymbolSize = 88.dp

/** Subrayado de la celda abierta: 24 dp de ancho y 2 dp de alto, bajo el nombre. */
private val UnderlineWidth = 24.dp
private val UnderlineHeight = 2.dp

/** El panel crece en 220 ms y se cierra en 160; cada palabra tarda 180 ms en entrar y 40 ms más que la anterior. */
private const val PANEL_OPEN_MILLIS = 220
private const val PANEL_CLOSE_MILLIS = 160
private const val WORD_IN_MILLIS = 180
private const val WORD_STAGGER_MILLIS = 40

/**
 * TRAINING_VARIANTS · «Tu estilo en sentadilla y peso muerto» (Powerlifting) y «¿Cuáles son tus variantes principales?»
 * (Powerbuilding): un mosaico de levantamientos. Cada celda muestra su figura y la variante elegida; al tocarla se despliegan
 * debajo sus variantes como palabras. Escribe SOLO con `vm.setLiftVariant`.
 */
@Composable
internal fun EntrenoVariantsStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    LiftVariantRows(
        goal = draft.goalProfile,
        availability = draft.trainingOptions.availability,
        stored = draft.liftVariants,
        onChoose = vm::setLiftVariant,
    )
    EntrenoStepNote(text = variantsNoteOf(draft.goalProfile))
}

/**
 * El mosaico de variantes, sin ViewModel: la prueba observa lo que elige la persona con [onChoose]. Una celda por
 * levantamiento de [LiftVariants.liftsFor] que tenga alguna variante que el material permite, en cuadrícula de dos columnas;
 * sin ninguna, la celda no sale. Al entrar todas van cerradas y solo una puede estar abierta. La elegida es la guardada o,
 * si no hay (o si el material ya no la permite), la de siempre: se ve elegida pero atenuada hasta que se toque.
 */
@Composable
internal fun LiftVariantRows(
    goal: TrainingGoalProfile?,
    availability: EquipmentAvailability?,
    stored: Map<LiftMark, String>,
    onChoose: (LiftMark, String) -> Unit,
) {
    val reduced = wizardReducedMotion()
    // Levantamiento abierto, por nombre (sobrevive a un giro de pantalla). Al entrar, ninguno.
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val cells = LiftVariants.liftsFor(goal).mapNotNull { lift ->
        val options = LiftVariants.optionsFor(goal, lift, availability)
        if (options.isEmpty()) return@mapNotNull null
        val effective = LiftVariants.effectiveFor(goal, lift, stored[lift], availability)
        val chosenId = options.firstOrNull { it.configId == effective }?.configId ?: options.first().configId
        VariantLift(lift = lift, options = options, chosenId = chosenId, declared = chosenId == stored[lift])
    }
    val open = cells.firstOrNull { it.lift.name == openName }?.lift
    // El reloj corre solo mientras hay una celda abierta: su figura es la única que hace el gesto.
    val clock = rememberFigClock(active = !reduced && open != null)
    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(2).forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowCells.forEach { cell ->
                    VariantLiftCell(
                        cell = cell,
                        open = open == cell.lift,
                        reduced = reduced,
                        clock = clock,
                        modifier = Modifier.weight(1f),
                        onToggle = { openName = if (open == cell.lift) null else cell.lift.name },
                    )
                }
                // Una celda suelta ocupa su mitad y la otra queda vacía: la cuadrícula no cambia de forma.
                if (rowCells.size == 1) Spacer(Modifier.weight(1f))
            }
            // El panel sale a todo el ancho bajo la fila de la celda abierta; en las demás filas no hay nada.
            val rowOpen = open?.takeIf { lift -> rowCells.any { it.lift == lift } }
            AnimatedContent(
                targetState = rowOpen,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    when {
                        reduced -> EnterTransition.None togetherWith ExitTransition.None
                        initialState == null ->
                            (expandVertically(tween(PANEL_OPEN_MILLIS, easing = FastOutSlowInEasing)) togetherWith ExitTransition.None)
                                .using(SizeTransform(clip = true))
                        targetState == null ->
                            (EnterTransition.None togetherWith shrinkVertically(tween(PANEL_CLOSE_MILLIS)))
                                .using(SizeTransform(clip = true))
                        else ->
                            (fadeIn(tween(PANEL_CLOSE_MILLIS)) togetherWith fadeOut(tween(PANEL_CLOSE_MILLIS)))
                                .using(SizeTransform(clip = true))
                    }
                },
                label = "variantPanel",
            ) { lift ->
                val cell = rowCells.firstOrNull { it.lift == lift }
                if (cell != null) {
                    VariantPanel(
                        cell = cell,
                        reduced = reduced,
                        // Tocar la ya guardada no hace nada; tocar la sugerencia la confirma. El panel se queda abierto.
                        onChoose = { configId -> if (configId != stored[cell.lift]) onChoose(cell.lift, configId) },
                    )
                }
            }
        }
    }
}

/** Un levantamiento de la pantalla: sus variantes que el material permite y la que se ve elegida. */
private class VariantLift(
    val lift: LiftMark,
    val options: List<LiftVariantOption>,
    val chosenId: String,
    /** Ya la eligió la persona (la guardada); si no, es solo la sugerencia de siempre. */
    val declared: Boolean,
) {
    val chosenLabel: String = options.firstOrNull { it.configId == chosenId }?.label ?: lift.label
}

/**
 * La celda de un levantamiento: la figura, el nombre con su chevron, el subrayado de acento si está abierta y, debajo, la
 * variante elegida. Sin caja ni fondo: toda la celda es el objetivo de toque. Solo la abierta hace el gesto de su figura.
 */
@Composable
private fun VariantLiftCell(
    cell: VariantLift,
    open: Boolean,
    reduced: Boolean,
    clock: State<Float>,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val lift = cell.lift
    val chevronAngle by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(PANEL_OPEN_MILLIS, easing = FastOutSlowInEasing),
        label = "variantChevron",
    )
    val underline by animateColorAsState(
        targetValue = if (open) SymbolPalette.musculo else Color.Transparent,
        animationSpec = if (reduced) snap() else tween(PANEL_CLOSE_MILLIS),
        label = "variantUnderline",
    )
    Column(
        modifier = modifier
            .testTag("setup-variant-${lift.name}")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics {
                contentDescription = "${lift.label}, ${cell.chosenLabel}"
                stateDescription = if (open) "Abierto" else "Cerrado"
            }
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiftSymbol(
            lift = lift,
            running = open && !reduced,
            declared = cell.declared,
            // Sin suelo propio: la celda se queda sobre la página negra.
            ground = false,
            clock = clock,
            modifier = Modifier.size(CellSymbolSize),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El nombre y su subrayado van juntos: el subrayado queda bajo el nombre, no bajo el chevron.
            Column(modifier = Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = lift.label,
                    style = WizardTypography.controlLabel,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(UnderlineWidth).height(UnderlineHeight).background(underline))
            }
            Spacer(Modifier.width(4.dp))
            VariantChevron(angle = chevronAngle)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = cell.chosenLabel,
            style = WizardTypography.note,
            // La sugerencia (sin tocar) va atenuada: es una propuesta, no una respuesta.
            color = if (cell.declared) SymbolPalette.musculo else SymbolPalette.musculo.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/** El chevron fino de la celda: apunta abajo cuando está cerrada y gira 180° al abrirse. */
@Composable
private fun VariantChevron(angle: Float) {
    Canvas(Modifier.size(12.dp).graphicsLayer { rotationZ = angle }) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawLine(WizardColors.textMuted, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.5f, h * 0.75f), stroke, StrokeCap.Round)
        drawLine(WizardColors.textMuted, Offset(w * 0.5f, h * 0.75f), Offset(w * 0.9f, h * 0.35f), stroke, StrokeCap.Round)
    }
}

/**
 * El panel de una celda abierta: una línea de contexto y las variantes que el material permite, como palabras. Las palabras
 * entran escalonadas; con movimiento reducido, ya están.
 */
@Composable
private fun VariantPanel(cell: VariantLift, reduced: Boolean, onChoose: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup-variant-panel-${cell.lift.name}")
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text(
            text = "${cell.lift.label}: elige tu variante principal",
            style = WizardTypography.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        VariantWordFlow {
            cell.options.forEachIndexed { index, option ->
                StaggeredIn(index = index, reduced = reduced) {
                    VariantWord(
                        lift = cell.lift,
                        option = option,
                        chosen = option.configId == cell.chosenId,
                        suggested = !cell.declared,
                        onClick = { onChoose(option.configId) },
                    )
                }
                if (index < cell.options.lastIndex) {
                    // Separador decorativo: TalkBack lee las palabras, no el punto.
                    Text(
                        text = "  ·  ",
                        style = WizardTypography.controlLabel,
                        color = WizardColors.textFaint,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/** Una palabra que aparece tras el retraso de su orden (40 ms cada una). Con movimiento reducido, ya está. */
@Composable
private fun StaggeredIn(index: Int, reduced: Boolean, content: @Composable () -> Unit) {
    var arrived by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { arrived = true }
    val wordAlpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = if (reduced) {
            snap()
        } else {
            tween(durationMillis = WORD_IN_MILLIS, delayMillis = index * WORD_STAGGER_MILLIS)
        },
        label = "variantWord",
    )
    Box(Modifier.graphicsLayer { alpha = wordAlpha }) { content() }
}

/**
 * Una variante como palabra: objetivo de 48 dp con `Role.RadioButton`. La elegida va con el acento del levantamiento y
 * subrayada; si es solo la sugerencia (no se ha tocado), atenuada. Las demás, en gris cálido.
 */
@Composable
private fun VariantWord(
    lift: LiftMark,
    option: LiftVariantOption,
    chosen: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag("setup-variant-${lift.name}-${option.configId}")
            .selectable(
                selected = chosen,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${lift.label}: ${option.label}"
                if (chosen && suggested) stateDescription = "Sugerida, sin confirmar"
            }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = option.label,
            style = WizardTypography.controlLabel,
            color = when {
                !chosen -> WizardColors.textFaint
                suggested -> SymbolPalette.musculo.copy(alpha = 0.7f)
                else -> SymbolPalette.musculo
            },
            textDecoration = if (chosen) TextDecoration.Underline else TextDecoration.None,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Las palabras de una variante en filas que nunca parten una palabra. El separador «·» solo se pinta entre dos palabras de la
 * misma fila: si la siguiente pasa a la fila de abajo, el punto no queda colgando al final de la anterior. Los hijos llegan
 * alternados: palabra, separador, palabra… (el último no lleva separador).
 */
@Composable
private fun VariantWordFlow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxWidth)) }
        val xs = IntArray(placeables.size)
        val ys = IntArray(placeables.size)
        val shown = BooleanArray(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var forceWrap = false
        placeables.forEachIndexed { index, placeable ->
            if (index % 2 == 0) {
                // Palabra: pasa a la fila de abajo si no cabe o si el separador anterior ya cortó la fila.
                if (x > 0 && (forceWrap || x + placeable.width > maxWidth)) {
                    x = 0
                    y += rowHeight
                    rowHeight = 0
                }
                forceWrap = false
                xs[index] = x
                ys[index] = y
                shown[index] = true
                x += placeable.width
                rowHeight = maxOf(rowHeight, placeable.height)
            } else {
                // Separador: solo si la palabra siguiente cabe en esta misma fila; si no, la fila se corta aquí.
                val next = placeables.getOrNull(index + 1)
                if (next != null && x + placeable.width + next.width <= maxWidth) {
                    xs[index] = x
                    ys[index] = y + (rowHeight - placeable.height) / 2
                    shown[index] = true
                    x += placeable.width
                } else {
                    forceWrap = next != null
                }
            }
        }
        layout(maxWidth, y + rowHeight) {
            placeables.forEachIndexed { index, placeable ->
                if (shown[index]) placeable.place(xs[index], ys[index])
            }
        }
    }
}

/** La nota de bajo la lista: qué pasa con la elección, según el perfil. */
private fun variantsNoteOf(goal: TrainingGoalProfile?): String? = when (goal) {
    TrainingGoalProfile.POWERLIFTING -> "Puedes cambiarlas cuando quieras desde tu programa."
    TrainingGoalProfile.POWERBUILDING -> "Las usamos como ejercicios principales de tus sesiones."
    else -> null
}
