package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.training.split.SplitGroup
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/**
 * Estado de volumen de un músculo (P4). Es la única decisión de color de la pestaña: las zonas del objetivo y la marca salen de aquí.
 * Sin calibrar no hay zonas, así que no hay estado de volumen y el color es neutro.
 */
enum class ProgramVolumeState {
    /** Sin calibrar: no hay objetivo personalizado con el que comparar. */
    SIN_CALIBRAR,

    /** Por debajo del mínimo efectivo (o sin series). */
    SUBENTRENADO,

    /** Entre el mínimo efectivo y el máximo adaptativo. */
    OPTIMO,

    /** Por encima del máximo adaptativo y hasta el máximo recuperable. */
    ALTO_TOLERABLE,

    /** Sobrecarga funcional marcada por la analítica de fatiga. */
    SOBRECARGA_FUNCIONAL,

    /** Por encima del máximo recuperable, o exceso marcado por la analítica de fatiga. */
    POSIBLE_EXCESO,
}

/**
 * Estado de una fila. El exceso y la sobrecarga marcados por la analítica mandan sobre las zonas: son una alerta real aunque el
 * programa no esté calibrado. Después, sin calibrar; y luego, las zonas del objetivo.
 */
fun programVolumeStateOf(
    sets: Double,
    zones: ProgramVolumeZones?,
    overtrained: Boolean,
    overreaching: Boolean,
): ProgramVolumeState = when {
    overtrained -> ProgramVolumeState.POSIBLE_EXCESO
    overreaching -> ProgramVolumeState.SOBRECARGA_FUNCIONAL
    zones == null -> ProgramVolumeState.SIN_CALIBRAR
    sets <= 0.0 || sets < zones.minEffective -> ProgramVolumeState.SUBENTRENADO
    sets <= zones.maxAdaptive -> ProgramVolumeState.OPTIMO
    sets <= zones.maxRecoverable -> ProgramVolumeState.ALTO_TOLERABLE
    else -> ProgramVolumeState.POSIBLE_EXCESO
}

/**
 * Tabla estado → color de P4: subentrenado = energia; óptimo = hecho; alto tolerable y sobrecarga funcional = músculo; posible exceso =
 * peligro; sin calibrar = gris de texto. Los acentos vienen de la paleta de símbolos y de WizardColors, nunca de colores sueltos.
 */
fun programVolumeStateColor(state: ProgramVolumeState): Color = when (state) {
    ProgramVolumeState.SIN_CALIBRAR -> WizardColors.textMuted
    ProgramVolumeState.SUBENTRENADO -> SymbolPalette.energia
    ProgramVolumeState.OPTIMO -> WizardColors.done
    ProgramVolumeState.ALTO_TOLERABLE -> SymbolPalette.musculo
    ProgramVolumeState.SOBRECARGA_FUNCIONAL -> SymbolPalette.musculo
    ProgramVolumeState.POSIBLE_EXCESO -> WizardColors.danger
}

/**
 * Etiqueta corta de la fila, en tipo frase. En «Rango ideal» solo se distingue la base mínima: estar justo en el mínimo efectivo se dice
 * como tal. «Sobreentreno» es el exceso por zonas; «Posible exceso de volumen», el que marca la analítica.
 */
fun programVolumeStateLabel(
    state: ProgramVolumeState,
    sets: Double,
    zones: ProgramVolumeZones?,
    overtrained: Boolean,
): String = when (state) {
    ProgramVolumeState.SIN_CALIBRAR -> "Sin calibrar"
    ProgramVolumeState.SUBENTRENADO -> "Subentrenado"
    ProgramVolumeState.OPTIMO ->
        if (zones != null && sets == zones.minEffective.toDouble()) "Base mínima" else "Rango ideal"
    ProgramVolumeState.ALTO_TOLERABLE -> "Alto tolerable"
    ProgramVolumeState.SOBRECARGA_FUNCIONAL -> "Sobrecarga funcional"
    ProgramVolumeState.POSIBLE_EXCESO -> if (overtrained) "Posible exceso de volumen" else "Sobreentreno"
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
