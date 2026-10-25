package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.training.split.SplitGroup
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

/**
 * Grupo de reparto de un músculo canónico, con la misma clasificación que el motor de división: lo que es empuje, tirón o pierna.
 * «Deltoides» va en empuje porque la pestaña lo muestra agregado y sus cabezas posteriores son minoría. Core y cuello no entran en el
 * reparto, que solo compara los tres grupos.
 */
fun programVolumeSplitGroup(muscle: String): SplitGroup? = when (muscle) {
    "Pectorales", "Deltoides", "Tríceps" -> SplitGroup.PUSH
    "Dorsales", "Trapecio", "Romboides", "Bíceps", "Antebrazo" -> SplitGroup.PULL
    "Cuádriceps", "Isquiosurales", "Glúteos", "Aductores", "Pantorrillas", "Erectores Espinales" -> SplitGroup.LEGS
    else -> null
}

/** Reparto de series entre empuje, tirón y pierna a partir de las series por músculo. */
fun programVolumeSplit(muscles: List<Pair<String, Double>>): ProgramVolumeSplit {
    var push = 0.0
    var pull = 0.0
    var legs = 0.0
    muscles.forEach { (name, sets) ->
        when (programVolumeSplitGroup(name)) {
            SplitGroup.PUSH -> push += sets
            SplitGroup.PULL -> pull += sets
            SplitGroup.LEGS -> legs += sets
            else -> Unit
        }
    }
    return ProgramVolumeSplit(push = push, pull = pull, legs = legs)
}
