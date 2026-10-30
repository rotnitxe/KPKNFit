package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/**
 * Instantánea para «Deshacer» un cambio de reparto (`SplitMigrationPlanner.apply` / `undo`). Viaja en el JSON del programa
 * ([Program.lastSplitChange]): no necesita migración de Room.
 *
 * Guarda **solo lo que el cambio escribió**: las semanas cuyo contenido cambió (tal como estaban antes) y el valor anterior
 * de los campos del programa que el cambio toca. Así `undo(apply(p)) == p` exacto, también con calendario, selecciones de
 * reparto y marcas de «Sesión personalizada».
 */
@Serializable
data class SplitChangeSnapshot(
    /** Reparto que se aplicó (id del catálogo o `custom`). */
    val splitId: String,
    /** Nombre llano del reparto aplicado, para el resumen («Torso y pierna, 4 días»). */
    val splitName: String,
    val appliedAtMs: Long = 0L,
    /** Semanas que el cambio reescribió, tal como estaban antes. */
    val weeks: List<SplitSnapshotWeek> = emptyList(),
    /** Huella del contenido (sesiones) de cada semana de [weeks] justo después de aplicar: si cambia, ya hubo ediciones. */
    val appliedFingerprints: List<Int> = emptyList(),
    val startDay: Int? = null,
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val customSplitDescription: String? = null,
    val blockSplitSelections: Map<String, String> = emptyMap(),
    val weekSplitSelections: Map<String, String> = emptyMap(),
    val splitTrialSeen: Boolean = false,
    val schedulePlan: ProgramSchedulePlan? = null,
    val manualSessionOverrides: List<ManualSessionOverride> = emptyList(),
    /** El cambio anterior, para que deshacer devuelva también el estado de [Program.lastSplitChange] que había. */
    val previousChange: SplitChangeSnapshot? = null,
) {
    /** Semanas que el cambio reescribió (para el resumen «Se cambiaron N semanas»). */
    val changedWeekCount: Int get() = weeks.size
}

/** Una semana tal como estaba antes del cambio, con su posición (macrociclo, bloque, mesociclo, semana). */
@Serializable
data class SplitSnapshotWeek(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val week: ProgramWeek,
)
