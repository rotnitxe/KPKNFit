package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.cardioArtOf

/** Marca de prueba de una base de cardio: `setup-cardio-base-MODERATE`, … */
internal fun cardioBaseTag(base: CardioBase): String = "setup-cardio-base-${base.name}"

/**
 * CARDIO_BASE · «¿Cómo está tu cardio hoy?»: cuatro símbolos de elección única (de menos a más: caminar, trote, carrera y series
 * rápidas). Es una fila de cuatro cuando cabe y 2 × 2 si no. Tocar el elegido no lo quita. Escribe SOLO con
 * `vm.setCardioBase(base)`.
 */
@Composable
internal fun EntrenoCardioBaseStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    CardioBaseContent(
        selected = state.draft.cardioBase,
        onChoose = { base -> vm.setCardioBase(base) },
    )
    EntrenoStepNote("Con esto ajustamos la intensidad de tus sesiones de cardio.")
}

/** Las cuatro opciones sin ViewModel: la prueba observa lo que elige la persona con [onChoose] (solo si cambia la elección). */
@Composable
internal fun CardioBaseContent(selected: CardioBase?, onChoose: (CardioBase) -> Unit) {
    val choices = CardioBase.entries.map { base ->
        StartingPointChoice(
            art = cardioArtOf(base),
            label = base.label,
            tag = cardioBaseTag(base),
            selected = base == selected,
            description = base.label,
            role = Role.RadioButton,
            onChoose = { if (base != selected) onChoose(base) },
        )
    }
    StartingPointGrid(
        choices = choices,
        columns = 2,
        wideColumns = 4,
        wideFrom = 348.dp,
        canvas = SCENE_CANVAS,
        artScale = SCENE_ART_SCALE,
        labelStyle = WizardTypography.controlLabel,
    )
}
