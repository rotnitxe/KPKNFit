package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.programs.CatalogDuration

/*
 * Etiquetas en español neutro de los objetivos y esquemas de la pestaña Estructura (P3b). Funciones puras y exhaustivas: ningún nombre
 * de enum llega a la pantalla, y un valor nuevo obliga a escribir su texto.
 */

/** Objetivos de bloque, en el orden de su fila de chips. */
internal val BLOCK_GOAL_CHOICES: List<BlockGoal> = listOf(
    BlockGoal.ACCUMULATION,
    BlockGoal.INTENSIFICATION,
    BlockGoal.SPECIFICITY,
    BlockGoal.REALIZATION,
    BlockGoal.PEAK,
    BlockGoal.DELOAD,
    BlockGoal.TAPER,
    BlockGoal.DENSITY,
    BlockGoal.CUSTOM,
)

/** Esquemas de progresión dentro del bloque, en el orden de sus filas de radio. */
internal val BLOCK_SCHEME_CHOICES: List<BlockProgressionScheme> = listOf(
    BlockProgressionScheme.NONE,
    BlockProgressionScheme.LINEAR_LOAD,
    BlockProgressionScheme.UNDULATING,
    BlockProgressionScheme.PERCENT_RM,
    BlockProgressionScheme.RPE_CAP,
)

/** Objetivos de mesociclo, en el orden de su fila de chips. */
internal val MESOCYCLE_GOAL_CHOICES: List<MesocycleGoal> = listOf(
    MesocycleGoal.ACCUMULATION,
    MesocycleGoal.INTENSIFICATION,
    MesocycleGoal.REALIZATION,
    MesocycleGoal.DELOAD,
    MesocycleGoal.CUSTOM,
)

/** Nombre de un objetivo de bloque («Puesta a punto» para el taper). */
internal fun blockGoalLabel(goal: BlockGoal): String = when (goal) {
    BlockGoal.ACCUMULATION -> "Acumulación"
    BlockGoal.INTENSIFICATION -> "Intensificación"
    BlockGoal.SPECIFICITY -> "Especificidad"
    BlockGoal.REALIZATION -> "Realización"
    BlockGoal.PEAK -> "Pico"
    BlockGoal.DELOAD -> "Descarga"
    BlockGoal.TAPER -> "Puesta a punto"
    BlockGoal.DENSITY -> "Densidad"
    BlockGoal.CUSTOM -> "Personalizado"
}

/** Nombre de un esquema de progresión de bloque. */
internal fun blockProgressionLabel(scheme: BlockProgressionScheme): String = when (scheme) {
    BlockProgressionScheme.NONE -> "Sin progresión"
    BlockProgressionScheme.LINEAR_LOAD -> "Carga lineal"
    BlockProgressionScheme.UNDULATING -> "Ondulante"
    BlockProgressionScheme.PERCENT_RM -> "Porcentaje de 1RM"
    BlockProgressionScheme.RPE_CAP -> "Tope de RPE"
}

/** Nombre de un objetivo de mesociclo. */
internal fun mesocycleGoalLabel(goal: MesocycleGoal): String = when (goal) {
    MesocycleGoal.ACCUMULATION -> "Acumulación"
    MesocycleGoal.INTENSIFICATION -> "Intensificación"
    MesocycleGoal.REALIZATION -> "Realización"
    MesocycleGoal.DELOAD -> "Descarga"
    MesocycleGoal.CUSTOM -> "Personalizado"
}

/** Cómo se nombra cada duración en la biblioteca de Programas y su filtro. Es la única fuente. */
internal fun catalogDurationLabel(duration: CatalogDuration): String = when (duration) {
    CatalogDuration.REPEATING_WEEK -> "Semana que se repite"
    CatalogDuration.REPEATING_CYCLE -> "Ciclo que se repite"
    CatalogDuration.FINITE_CYCLE -> "Ciclo que termina"
}
