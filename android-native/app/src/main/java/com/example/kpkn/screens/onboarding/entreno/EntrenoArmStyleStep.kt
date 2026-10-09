package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.armArtOf

/** Marca de prueba de un estilo de mesa: `setup-arm-style-HOOK`, … */
internal fun armStyleTag(style: ArmStyle): String = "setup-arm-style-${style.name}"

/**
 * ARM_STYLE · «¿Cuál es tu estilo en la mesa?»: cuatro escenas de elección única en 2 × 2 (top roll, hook, press y «Aún no lo
 * sé»). Tocar el elegido no lo quita. Escribe SOLO con `vm.setArmStyle(style)`.
 */
@Composable
internal fun EntrenoArmStyleStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    ArmStyleContent(
        selected = state.draft.armStyle,
        onChoose = { style -> vm.setArmStyle(style) },
    )
    EntrenoStepNote("Así damos más trabajo a lo que pide tu estilo.")
}

/** Las cuatro escenas sin ViewModel: la prueba observa lo que elige la persona con [onChoose] (solo si cambia la elección). */
@Composable
internal fun ArmStyleContent(selected: ArmStyle?, onChoose: (ArmStyle) -> Unit) {
    val choices = ArmStyle.entries.map { style ->
        StartingPointChoice(
            art = armArtOf(style),
            label = style.label,
            tag = armStyleTag(style),
            selected = style == selected,
            description = style.label,
            role = Role.RadioButton,
            onChoose = { if (style != selected) onChoose(style) },
        )
    }
    StartingPointGrid(
        choices = choices,
        columns = 2,
        canvas = SCENE_CANVAS,
        artScale = SCENE_ART_SCALE,
        labelStyle = WizardTypography.controlLabel,
    )
}
