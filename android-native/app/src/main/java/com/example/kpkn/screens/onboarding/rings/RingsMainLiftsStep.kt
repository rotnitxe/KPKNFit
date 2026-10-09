package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.RingsMainLiftsState
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.RingsNoneArt
import com.example.kpkn.screens.onboarding.design.entreno.RingsUnknownArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCell
import com.example.kpkn.screens.onboarding.design.entreno.cardioArtOf
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.patternArt
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Columnas de la cuadrícula de ejercicios: tres, como las de los símbolos de Entreno. */
private const val LIFT_COLUMNS = 3

/** Zona de dibujo de cada ejercicio y su escala dentro de ella. */
private val LiftCanvas = 64.dp
private const val LiftArtScale = 0.9f

/** Un símbolo de la cuadrícula: su etiqueta del catálogo, su dibujo y si está elegido. */
private class LiftCell(
    val tag: String,
    val label: String,
    val art: SymbolArt,
    val selected: Boolean,
    val onToggle: () -> Unit,
)

/**
 * Paso `RINGS_MAIN_LIFTS`: la cuadrícula de patrones de movimiento (selección múltiple) y, al final, «Ninguno de estos» y «No lo
 * sé», que son exclusivos. Los pictogramas estáticos se iluminan con su acento al elegirlos.
 */
@Composable
internal fun RingsMainLiftsStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    RingsMainLiftsContent(
        answers = state.draft.ringsAnswers,
        onToggle = { pattern -> vm.toggleMainLift(pattern) },
        onNone = { vm.setNoMainLifts() },
        onUnknown = { vm.setMainLiftsUnknown() },
        modifier = modifier,
    )
}

/** Cuerpo del paso sin estado propio (lo usa también la hoja de render). */
@Composable
internal fun RingsMainLiftsContent(
    answers: SetupRingsAnswers?,
    onToggle: (RingsLiftPattern) -> Unit,
    onNone: () -> Unit,
    onUnknown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = answers?.mainLiftsState ?: RingsMainLiftsState.NOT_ANSWERED
    val chosen = if (status == RingsMainLiftsState.DECLARED) answers?.mainLifts.orEmpty() else emptySet()
    val none = status == RingsMainLiftsState.NONE
    val unknown = status == RingsMainLiftsState.UNKNOWN
    val reduced = wizardReducedMotion()
    val clock = rememberSymbolClock(active = !reduced && (chosen.isNotEmpty() || none || unknown))
    // Las etiquetas son las del catálogo del paso: el mismo texto que en el resumen y en la revisión.
    val labels = remember { SetupStepDefinitions.options(SetupStepId.RINGS_MAIN_LIFTS).associate { it.value to it.label } }
    val lifts = RingsLiftPattern.entries.map { pattern ->
        LiftCell(
            tag = "rings-lift-${pattern.name}",
            label = labels[pattern.name].orEmpty(),
            art = liftArt(pattern),
            selected = pattern in chosen,
            onToggle = { onToggle(pattern) },
        )
    }
    val exclusives = listOf(
        LiftCell("rings-lift-none", labels["none"].orEmpty(), RingsNoneArt, none, onNone),
        LiftCell("rings-lift-unknown", labels["unknown"].orEmpty(), RingsUnknownArt, unknown, onUnknown),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        lifts.chunked(LIFT_COLUMNS).forEach { row -> LiftRow(row, clock, reduced) }
        // Los exclusivos van aparte, en su propia fila, bajo un filete.
        RingsQuestionDivider()
        LiftRow(exclusives, clock, reduced)
    }
}

/** Una fila de la cuadrícula: tres columnas del mismo ancho (la fila incompleta se rellena con hueco). */
@Composable
private fun LiftRow(cells: List<LiftCell>, clock: SymbolClock, reduced: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            SymbolCell(
                art = cell.art,
                label = cell.label,
                tag = cell.tag,
                selected = cell.selected,
                clock = clock,
                reducedMotion = reduced,
                onToggle = cell.onToggle,
                canvasModifier = Modifier.size(LiftCanvas),
                labelStyle = WizardTypography.note,
                artScale = LiftArtScale,
                badgeRadius = 9.dp,
                labelGap = 4.dp,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(LIFT_COLUMNS - cells.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** El dibujo de cada ejercicio: su patrón de [patternArt], o el símbolo de carrera y de intervalos de C1c. */
private fun liftArt(pattern: RingsLiftPattern): SymbolArt = when (pattern) {
    RingsLiftPattern.SQUAT -> patternArt(ExercisePattern.SQUAT)
    RingsLiftPattern.DEADLIFT -> patternArt(ExercisePattern.HINGE)
    RingsLiftPattern.HINGE_ROW -> patternArt(ExercisePattern.ROW)
    RingsLiftPattern.BENCH -> patternArt(ExercisePattern.PRESS_HORIZONTAL)
    RingsLiftPattern.OVERHEAD -> patternArt(ExercisePattern.PRESS_VERTICAL)
    RingsLiftPattern.PULL_UP -> patternArt(ExercisePattern.PULL_VERTICAL)
    RingsLiftPattern.LUNGE -> patternArt(ExercisePattern.LUNGE)
    RingsLiftPattern.CARDIO_LONG -> cardioArtOf(CardioBase.HIGH)
    RingsLiftPattern.INTERVALS -> cardioArtOf(CardioBase.INTERVALS)
}
