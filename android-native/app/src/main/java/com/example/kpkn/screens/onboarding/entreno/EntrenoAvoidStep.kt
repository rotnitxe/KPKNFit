package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.AVOID_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.AVOID_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.NoneArt
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.avoidArtOf

/** Marca de prueba de un grupo de «evitar»: `setup-avoid-JUMPS`, … */
internal fun avoidTag(group: AvoidGroup): String = "setup-avoid-${group.name}"

/** Marca de prueba de «Ninguno». */
internal const val AVOID_NONE_TAG = "setup-avoid-NONE"

/**
 * AVOIDED_EXERCISES · «¿Qué ejercicios prefieres evitar?»: una cuadrícula de símbolos de patrón (diez grupos) y, al final,
 * «Ninguno». «Ninguno» es exclusivo: elegirlo vacía el conjunto y elegir otro grupo apaga «Ninguno». Escribe SOLO con
 * `vm.toggleAvoided(group)` y `vm.setAvoided(emptySet())`.
 */
@Composable
internal fun EntrenoAvoidStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    AvoidGroupsContent(
        selected = state.draft.avoidedExercises,
        onToggle = { group -> vm.toggleAvoided(group) },
        onNone = { vm.setAvoided(emptySet()) },
    )
    EntrenoStepNote("Si un ejercicio no tiene sustituto con tu material, te lo diremos.")
}

/**
 * La cuadrícula de «evitar» sin ViewModel: la prueba observa lo que elige la persona con [onToggle] y [onNone]. Un grupo
 * evitado se tacha; «Ninguno» está marcado cuando no hay ninguno evitado.
 */
@Composable
internal fun AvoidGroupsContent(
    selected: Set<AvoidGroup>,
    onToggle: (AvoidGroup) -> Unit,
    onNone: () -> Unit,
) {
    val choices = AvoidGroup.entries.map { group ->
        val avoided = group in selected
        StartingPointChoice(
            art = avoidArtOf(group),
            label = group.label,
            tag = avoidTag(group),
            selected = avoided,
            description = group.label + if (avoided) ", evitar" else ", sin evitar",
            role = Role.Checkbox,
            struck = avoided,
            onChoose = { onToggle(group) },
        )
    } + StartingPointChoice(
        art = NoneArt,
        label = "Ninguno",
        tag = AVOID_NONE_TAG,
        selected = selected.isEmpty(),
        description = "Ninguno",
        role = Role.Checkbox,
        onChoose = onNone,
    )
    StartingPointGrid(
        choices = choices,
        columns = 3,
        wideColumns = 4,
        wideFrom = 600.dp,
        canvas = AVOID_CANVAS,
        artScale = AVOID_ART_SCALE,
        labelStyle = WizardTypography.note,
    )
}
