package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Descriptive comparison with authored program references, never recovery or safety. */
enum class ProgramVolumeState { SIN_REFERENCIA, BAJO_REFERENCIA, EN_REFERENCIA, TRAMO_SUPERIOR, SOBRE_REFERENCIA }

fun programVolumeStateOf(sets: Double, zones: ProgramVolumeZones?): ProgramVolumeState = when {
    zones == null -> ProgramVolumeState.SIN_REFERENCIA
    sets < zones.minEffective -> ProgramVolumeState.BAJO_REFERENCIA
    sets <= zones.maxAdaptive -> ProgramVolumeState.EN_REFERENCIA
    sets <= zones.maxRecoverable -> ProgramVolumeState.TRAMO_SUPERIOR
    else -> ProgramVolumeState.SOBRE_REFERENCIA
}

fun programVolumeStateColor(state: ProgramVolumeState): Color = WizardColors.textMuted

fun programVolumeStateLabel(state: ProgramVolumeState, sets: Double, zones: ProgramVolumeZones?): String = when (state) {
    ProgramVolumeState.SIN_REFERENCIA -> "Sin referencia del programa"
    ProgramVolumeState.BAJO_REFERENCIA -> "Por debajo de la referencia"
    ProgramVolumeState.EN_REFERENCIA -> "Dentro de la referencia del programa"
    ProgramVolumeState.TRAMO_SUPERIOR -> "Tramo superior de la referencia"
    ProgramVolumeState.SOBRE_REFERENCIA -> "Por encima de la referencia"
}
