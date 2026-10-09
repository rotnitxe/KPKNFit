package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.RingsEasyBar
import com.example.kpkn.screens.onboarding.design.entreno.RingsHardBar
import com.example.kpkn.screens.onboarding.design.entreno.RingsModerateBar
import com.example.kpkn.screens.onboarding.design.entreno.RingsVeryHardBar
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Un escalón de la intensidad: su valor de dominio, su etiqueta de pantalla y su barra. */
private class IntensityChoice(val level: InitialRecoveryIntensity, val label: String, val art: SymbolArt)

/** Los cuatro escalones, de menos a más: la barra se dobla más y, en «Muy dura», caen las gotas de esfuerzo. */
private val IntensityScale = listOf(
    IntensityChoice(InitialRecoveryIntensity.EASY, "Suave", RingsEasyBar),
    IntensityChoice(InitialRecoveryIntensity.MODERATE, "Moderada", RingsModerateBar),
    IntensityChoice(InitialRecoveryIntensity.HARD, "Dura", RingsHardBar),
    IntensityChoice(InitialRecoveryIntensity.VERY_HARD, "Muy dura", RingsVeryHardBar),
)

/**
 * Paso `RINGS_INTENSITY`: cómo sentiste la sesión, con una escala de cuatro símbolos de elección única. Escribe el dato con
 * `setStepChoice` (la proyección de `intensityLevel`).
 */
@Composable
internal fun RingsIntensityStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    RingsIntensityContent(
        selected = state.draft.ringsAnswers?.intensityLevel,
        onSelect = { level -> vm.setStepChoice(SetupStepId.RINGS_INTENSITY, level.name) },
        modifier = modifier,
    )
}

/** Cuerpo del paso sin estado propio (lo usa también la hoja de render). */
@Composable
internal fun RingsIntensityContent(
    selected: InitialRecoveryIntensity?,
    onSelect: (InitialRecoveryIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    // Solo corre el reloj con un escalón elegido; el resto de barras se queda en su cuadro estático.
    val clock = rememberSymbolClock(active = !reduced && selected != null)
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalAlignment = Alignment.Top,
    ) {
        IntensityScale.forEach { choice ->
            RingsRadioSymbol(
                art = choice.art,
                description = "Intensidad: ${choice.label}",
                tag = "rings-intensity-${choice.level.name}",
                selected = selected == choice.level,
                clock = clock,
                reducedMotion = reduced,
                onSelect = { if (selected != choice.level) onSelect(choice.level) },
                canvasSize = 64.dp,
                artScale = 0.9f,
                label = choice.label,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
