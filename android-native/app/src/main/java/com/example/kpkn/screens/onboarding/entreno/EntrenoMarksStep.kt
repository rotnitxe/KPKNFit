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
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
import com.example.kpkn.screens.onboarding.marksLifts
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Cuánto se espera tras el último cambio de una regla antes de escribir las marcas en el borrador. */
internal const val MARKS_WRITE_DEBOUNCE_MS = 220L

/**
 * TRAINING_MAX · «¿Conoces tus marcas?»: la marca (mejor levantamiento de una repetición, o una estimación) de cada
 * levantamiento que pregunta el objetivo, en una regla deslizante kg/lb. Cada una es opcional («No la sé»); sin marcas el
 * programa sigue siendo válido.
 *
 * Qué levantamientos se preguntan sale de `draft.marksLifts()` (`MarksContext`); las marcas están en `draft.liftMarks`
 * (kg canónicos) y la unidad visible en `draft.marksUnit` (`kg` o `lb`). Escribe SOLO con `vm.setLiftMark(mark, kg)`
 * (null = «No la sé») y `vm.setMarksUnit(unit)`.
 *
 * La regla avisa en CADA escalón mientras se arrastra y cada escritura relanza el barrido de programas, así que lo pedido
 * se ve al instante (vive aquí) y se escribe una sola vez cuando el dedo se detiene; si el paso se cierra con algo sin
 * escribir, se escribe al irse.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun EntrenoMarksStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    // Lo pedido y todavía sin escribir: levantamiento → kg (null = «No la sé»).
    val pending = remember { mutableStateMapOf<LiftMark, Double?>() }
    val shown = draft.liftMarks.toMutableMap().also { marks ->
        pending.forEach { (lift, kg) -> if (kg == null) marks.remove(lift) else marks[lift] = kg }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { pending.toMap() }
            .debounce(MARKS_WRITE_DEBOUNCE_MS)
            .collect { requested -> requested.forEach { (lift, kg) -> vm.setLiftMark(lift, kg) } }
    }
    // El borrador ya trae lo pedido: lo que se ve vuelve a ser suyo.
    LaunchedEffect(draft.liftMarks, pending.toMap()) {
        pending.keys.filter { lift -> draft.liftMarks[lift] == pending[lift] }.forEach { lift -> pending.remove(lift) }
    }
    // Si el paso se cierra con algo sin escribir, no se pierde.
    val currentMarks by rememberUpdatedState(draft.liftMarks)
    val flush by rememberUpdatedState {
        pending.forEach { (lift, kg) -> if (currentMarks[lift] != kg) vm.setLiftMark(lift, kg) }
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        LiftMarksPicker(
            lifts = draft.marksLifts(),
            valuesKg = shown,
            unit = draft.marksUnit,
            onValueKg = { lift, kg -> pending[lift] = kg },
            onUnit = vm::setMarksUnit,
        )
    }
}
