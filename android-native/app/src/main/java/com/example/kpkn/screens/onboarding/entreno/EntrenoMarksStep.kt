package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.LiftVariants
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
import com.example.kpkn.screens.onboarding.marksLifts
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Cuánto se espera tras el último cambio de una regla antes de escribir las marcas en el borrador. */
internal const val MARKS_WRITE_DEBOUNCE_MS = 220L

/**
 * TRAINING_MAX · «¿Conoces tus marcas?»: la marca (mejor levantamiento de una repetición, o una serie de 2 a 10 repeticiones) de
 * cada levantamiento que pregunta el objetivo, en una regla deslizante kg/lb. Cada una es opcional («No la sé»); sin marcas el
 * programa sigue siendo válido.
 *
 * Qué levantamientos se preguntan sale de `draft.marksLifts()` (`MarksContext`); las marcas están en `draft.liftMarks`
 * (kg canónicos), las repeticiones en `draft.liftMarkReps` (ausente = 1RM) y la unidad visible en `draft.marksUnit` (`kg` o `lb`).
 * Escribe SOLO con `vm.setLiftMark(mark, kg)` (null = «No la sé»), `vm.setLiftMarkReps(mark, reps)` (null = 1RM) y
 * `vm.setMarksUnit(unit)`.
 *
 * La regla avisa en CADA escalón mientras se arrastra y cada escritura relanza el barrido de programas, así que lo pedido
 * se ve al instante (vive aquí) y se escribe una sola vez cuando el dedo se detiene; si el paso se cierra con algo sin
 * escribir, se escribe al irse. Las repeticiones van con la misma espera.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun EntrenoMarksStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    // Lo pedido y todavía sin escribir: levantamiento → kg (null = «No la sé»).
    val pending = remember { mutableStateMapOf<LiftMark, Double?>() }
    // Las repeticiones pedidas y sin escribir: levantamiento → reps (null = volver al 1RM).
    val pendingReps = remember { mutableStateMapOf<LiftMark, Int?>() }
    val shown = draft.liftMarks.toMutableMap().also { marks ->
        pending.forEach { (lift, kg) -> if (kg == null) marks.remove(lift) else marks[lift] = kg }
    }
    val shownReps = draft.liftMarkReps.toMutableMap().also { reps ->
        pendingReps.forEach { (lift, count) -> if (count == null) reps.remove(lift) else reps[lift] = count }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { pending.toMap() to pendingReps.toMap() }
            .debounce(MARKS_WRITE_DEBOUNCE_MS)
            .collect { (kgs, counts) ->
                // Primero el kg: borrar una marca borra también sus repeticiones, así que las repeticiones van después.
                kgs.forEach { (lift, kg) -> vm.setLiftMark(lift, kg) }
                counts.forEach { (lift, count) -> vm.setLiftMarkReps(lift, count) }
            }
    }
    // El borrador ya trae lo pedido: lo que se ve vuelve a ser suyo.
    LaunchedEffect(draft.liftMarks, draft.liftMarkReps, pending.toMap(), pendingReps.toMap()) {
        pending.keys.filter { lift -> draft.liftMarks[lift] == pending[lift] }.forEach { lift -> pending.remove(lift) }
        pendingReps.keys.filter { lift -> draft.liftMarkReps[lift] == pendingReps[lift] }.forEach { lift -> pendingReps.remove(lift) }
    }
    // Si el paso se cierra con algo sin escribir, no se pierde.
    val currentMarks by rememberUpdatedState(draft.liftMarks)
    val currentReps by rememberUpdatedState(draft.liftMarkReps)
    val flush by rememberUpdatedState {
        pending.forEach { (lift, kg) -> if (currentMarks[lift] != kg) vm.setLiftMark(lift, kg) }
        pendingReps.forEach { (lift, count) -> if (currentReps[lift] != count) vm.setLiftMarkReps(lift, count) }
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        LiftMarksPicker(
            lifts = draft.marksLifts(),
            valuesKg = shown,
            unit = draft.marksUnit,
            onValueKg = { lift, kg ->
                pending[lift] = kg
                // «No la sé» borra también las repeticiones de esa marca, igual que el reductor.
                if (kg == null) pendingReps[lift] = null
            },
            onUnit = vm::setMarksUnit,
            repsByLift = shownReps,
            onReps = { lift, count -> pendingReps[lift] = count },
            variantLabels = variantLabelsOf(draft),
        )
    }
}

/**
 * El nombre de la variante que lleva la fila de cada levantamiento: «Sentadilla · Frontal». Solo en Powerbuilding, donde la
 * variante cambia lo que se mide; en Powerlifting la fila conserva su nombre.
 */
private fun variantLabelsOf(draft: SetupWizardDraft): Map<LiftMark, String> {
    if (draft.goalProfile != TrainingGoalProfile.POWERBUILDING) return emptyMap()
    val availability = draft.trainingOptions.availability
    return draft.marksLifts().mapNotNull { lift ->
        val configId = LiftVariants.effectiveFor(draft.goalProfile, lift, draft.liftVariants[lift], availability) ?: return@mapNotNull null
        LiftVariants.labelOf(configId)?.let { label -> lift to label }
    }.toMap()
}
