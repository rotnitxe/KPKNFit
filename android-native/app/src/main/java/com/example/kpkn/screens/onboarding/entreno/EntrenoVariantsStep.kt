package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.background
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.kpkn.screens.onboarding.design.entreno.rememberFigClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Lado de la figura de cada levantamiento. */
private val VariantSymbolSize = 72.dp

/** Aire entre la figura y el texto de cada fila. */
private val VariantGap = 16.dp

/**
 * TRAINING_VARIANTS · «Tus tres básicos» (Powerlifting) y «¿Cuáles son tus variantes principales?» (Powerbuilding): una fila por
 * levantamiento, con su figura y, debajo del nombre, las variantes que el material permite, como palabras. Escribe SOLO con
 * `vm.setLiftVariant`.
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
 * Las filas de variantes, sin ViewModel: la prueba observa lo que elige la persona con [onChoose]. Una fila por levantamiento de
 * [LiftVariants.liftsFor] que tenga alguna variante que el material permite; sin ninguna, la fila no sale. La elegida es la
 * guardada o, si no hay (o si el material ya no la permite), la de siempre: se ve elegida pero atenuada hasta que se toque.
 */
@Composable
internal fun LiftVariantRows(
    goal: TrainingGoalProfile?,
    availability: EquipmentAvailability?,
    stored: Map<LiftMark, String>,
    onChoose: (LiftMark, String) -> Unit,
) {
    val reduced = wizardReducedMotion()
    // Solo corre la fila tocada por última vez; el resto se queda en su cuadro de reposo.
    var lastTouched by rememberSaveable { mutableStateOf<String?>(null) }
    val clock = rememberFigClock(active = !reduced && lastTouched != null)
    val rows = LiftVariants.liftsFor(goal).mapNotNull { lift ->
        val options = LiftVariants.optionsFor(goal, lift, availability)
        if (options.isEmpty()) null else lift to options
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (lift, options) ->
            // El filete separa cada fila de la anterior; la primera no lleva ninguno.
            if (index > 0) VariantHairline()
            val effective = LiftVariants.effectiveFor(goal, lift, stored[lift], availability)
            val chosenId = options.firstOrNull { it.configId == effective }?.configId ?: options.first().configId
            LiftVariantRow(
                lift = lift,
                options = options,
                chosenId = chosenId,
                declared = chosenId == stored[lift],
                running = !reduced && lastTouched == lift.name,
                clock = clock,
                onChoose = { configId ->
                    lastTouched = lift.name
                    // Tocar la ya guardada no hace nada; tocar la sugerencia la confirma.
                    if (configId != stored[lift]) onChoose(lift, configId)
                },
            )
        }
    }
}

/** Una fila: la figura a la izquierda y, a la derecha, el nombre y debajo las variantes como palabras. */
@Composable
private fun LiftVariantRow(
    lift: LiftMark,
    options: List<LiftVariantOption>,
    chosenId: String,
    declared: Boolean,
    running: Boolean,
    clock: State<Float>,
    onChoose: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup-variant-${lift.name}")
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiftSymbol(
            lift = lift,
            running = running,
            declared = declared,
            // Sin suelo propio: el filete entre filas hace de base.
            ground = false,
            clock = clock,
            modifier = Modifier.size(VariantSymbolSize),
        )
        Spacer(Modifier.width(VariantGap))
        Column(Modifier.weight(1f)) {
            Text(text = lift.label, style = WizardTypography.controlLabel, color = WizardColors.text)
            VariantWordFlow {
                options.forEachIndexed { index, option ->
                    VariantWord(
                        lift = lift,
                        option = option,
                        chosen = option.configId == chosenId,
                        suggested = !declared,
                        onClick = { onChoose(option.configId) },
                    )
                    if (index < options.lastIndex) {
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
}

/**
 * Una variante como palabra: objetivo de 48 dp con `Role.RadioButton`. La elegida va en tinta y subrayada; si es solo la
 * sugerencia (no se ha tocado), en tinta al 70 %. Las demás, en gris cálido.
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
                suggested -> WizardColors.text.copy(alpha = 0.7f)
                else -> WizardColors.text
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

@Composable
private fun VariantHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
}

/** La nota de bajo la lista: qué pasa con la elección, según el perfil. */
private fun variantsNoteOf(goal: TrainingGoalProfile?): String? = when (goal) {
    TrainingGoalProfile.POWERLIFTING -> "Puedes cambiarlas cuando quieras desde tu programa."
    TrainingGoalProfile.POWERBUILDING -> "Las usamos como ejercicios principales de tus sesiones."
    else -> null
}
