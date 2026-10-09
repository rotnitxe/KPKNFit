package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.domain.onboarding.ProgramSourceOptions
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.programSourceArtOf
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Marca de prueba de una opción del programa: `setup-program-source-LATER`, … */
internal fun programSourceTag(source: ProgramSource): String = "setup-program-source-${source.name}"

/** La nota bajo los símbolos según la elección; sin elección, no hay nota. */
internal fun programSourceNote(source: ProgramSource?): String? = when (source) {
    ProgramSource.RECOMMEND -> "Seguimos con unas preguntas y te proponemos programas para tu disciplina."
    ProgramSource.LATER -> "Podrás crear tu programa desde Programas cuando quieras."
    ProgramSource.IMPORT -> "Excel (.xlsx), CSV o texto pegado."
    null -> null
}

/**
 * PROGRAM_SOURCE · «¿Cómo quieres tu programa?»: tres símbolos de elección única en una fila (que KPKN me recomiende uno, lo
 * armaré yo más adelante, importar el mío). Solo muestra «Importar» con la importación disponible. Escribe SOLO con
 * `vm.setProgramSource(source)`.
 */
@Composable
internal fun EntrenoProgramSourceStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    ProgramSourceContent(
        selected = state.draft.programSource,
        onChoose = { source -> vm.setProgramSource(source) },
    )
    EntrenoStepNote(programSourceNote(state.draft.programSource))
}

/**
 * Las opciones sin ViewModel: la prueba observa lo que elige la persona con [onChoose] (solo si cambia la elección).
 * [importEnabled] decide si la importación se ofrece; en producción es `ProgramSourceOptions.IMPORT_ENABLED`.
 */
@Composable
internal fun ProgramSourceContent(
    selected: ProgramSource?,
    onChoose: (ProgramSource) -> Unit,
    importEnabled: Boolean = ProgramSourceOptions.IMPORT_ENABLED,
) {
    val reduced = wizardReducedMotion()
    val choices = ProgramSource.entries
        .filter { source -> source != ProgramSource.IMPORT || importEnabled }
        .map { source ->
            StartingPointChoice(
                art = programSourceArtOf(source, reduced),
                label = source.label,
                tag = programSourceTag(source),
                selected = source == selected,
                description = source.label,
                role = Role.RadioButton,
                onChoose = { if (source != selected) onChoose(source) },
            )
        }
    StartingPointGrid(
        choices = choices,
        columns = choices.size,
        canvas = SCENE_CANVAS,
        artScale = SCENE_ART_SCALE,
        labelStyle = WizardTypography.controlLabel,
    )
}
