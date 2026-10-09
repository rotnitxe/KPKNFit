package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.rings.RingsFeelingsStep
import com.example.kpkn.screens.onboarding.rings.RingsGroupSetsStep
import com.example.kpkn.screens.onboarding.rings.RingsIntensityStep
import com.example.kpkn.screens.onboarding.rings.RingsIntroStep
import com.example.kpkn.screens.onboarding.rings.RingsMainLiftsStep
import com.example.kpkn.screens.onboarding.rings.RingsResultStep
import com.example.kpkn.screens.onboarding.rings.RingsSessionDaysStep

/**
 * Cuerpo de los pasos del bloque RINGS.
 *
 * La cabecera (pregunta, subtítulo, progreso y CTA) la pinta la raíz del
 * wizard; aquí solo se dibuja el control, siempre con las opciones del catálogo
 * `SetupStepDefinitions`.
 *
 * Reglas de honestidad del bloque:
 * - «Sí / No / No lo sé» son estados distintos: desconocer el historial nunca
 *   equivale a no haber entrenado ni fabrica sesiones.
 * - Las sensaciones (músculos, energía, columna) se preguntan siempre y su
 *   «No lo sé» no inventa un nivel 0.
 * - El resultado expone la cobertura por canal; sin datos dice «Sin calibrar»
 *   y jamás un 100 % afirmativo.
 *
 * Cada interacción escribe el dato de dominio con `updateStep` (limpieza de la
 * evidencia al responder «No»/«No lo sé» y la exposición axial completa) y
 * después la respuesta del paso con el setter, cuya proyección
 * (`SetupStepAnswers.withStep*`) va al final y por eso manda sobre los campos
 * que comparte. Ninguna función mueve el cursor: solo el CTA confirma.
 */
@Composable
fun SetupRingsStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.RINGS_INTRO -> RingsIntroStep()
        SetupStepId.RINGS_SESSION_DAYS -> RingsSessionDaysStep(state, vm)
        SetupStepId.RINGS_MAIN_LIFTS -> RingsMainLiftsStep(state, vm)
        SetupStepId.RINGS_GROUP_SETS -> RingsGroupSetsStep(state, vm)
        SetupStepId.RINGS_INTENSITY -> RingsIntensityStep(state, vm)
        SetupStepId.RINGS_FEELINGS -> RingsFeelingsStep(state, vm)
        SetupStepId.RINGS_DISCOMFORT -> RingsDiscomfortContent(state, vm)
        SetupStepId.RINGS_RESULT -> RingsResultStep(state, vm)
        // MILESTONE_RINGS lo pinta la raíz y RINGS_START es legacy-only (fuera
        // de la ruta productiva): aquí no se dibuja nada. Los pasos retirados
        // (RINGS_RECENT, RINGS_SESSIONS, RINGS_RECENCY, RINGS_ACTIVITY, RINGS_AXIAL
        // y las sensaciones sueltas) ya no están en ninguna ruta.
        else -> Unit
    }
}

// ─── RINGS_DISCOMFORT ───────────────────────────────────────────────────────

@Composable
private fun RingsDiscomfortContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.RINGS_DISCOMFORT
    val answers = state.draft.ringsAnswers
    val fallback = when (answers?.discomfortState) {
        SetupDiscomfortState.OMITTED -> setOf("omit")
        SetupDiscomfortState.NONE -> setOf("none")
        SetupDiscomfortState.DECLARED -> answers?.discomfortIds.orEmpty().toSet()
        else -> emptySet()
    }
    val selected = state.draft.setupSelectionOf(step, *fallback.toTypedArray())
    SetupBodyMultiChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "none" to "Ninguna molestia en este momento.",
            "omit" to "Prefieres no responder a esta pregunta.",
        ),
        // El toggle se resuelve dentro del mutex del VM sobre `selectedValues`
        // (que cae en `typedSelections` y por eso conserva molestias manuales
        // fuera del catálogo); `withStepChoices` proyecta `discomfortState` e
        // `discomfortIds` y `validateStep(DISCOMFORT)` lee ese estado.
        onSelect = { value -> vm.toggleStepChoice(step, value) },
    )
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "Prefiero omitir las molestias",
        visible = selected.isEmpty(),
    )
}
