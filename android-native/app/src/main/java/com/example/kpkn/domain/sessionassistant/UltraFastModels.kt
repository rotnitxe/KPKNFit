package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.RestPauseData

enum class SeriesTechnique {
    NORMAL,
    DROPSET,
    REST_PAUSE,
}

data class UltraFastPreview(
    val beforeSeconds: Int,
    val afterSeconds: Int,
    val savedSeconds: Int,
    val perExercise: List<UltraFastExerciseChange>,
    val supersets: List<UltraFastSupersetChange>,
    /** «Acortar sesión»: objetivo con el que se calculó (null en la vista previa clásica). */
    val goal: ShortenGoal? = null,
    /** Objetivos rápidos que tienen sentido para lo que falta (los minutos alcanzables y «lo mínimo eficaz»). */
    val goalOptions: List<ShortenGoal> = emptyList(),
    /** Una línea por cambio, en el orden de la sesión; incluye las excluidas para poder devolverlas. */
    val items: List<ShortenItemChange> = emptyList(),
    val excludedItemIds: Set<String> = emptySet(),
    /** Ejercicios (cardio accesorio) que la propuesta quita de hoy. */
    val removedExerciseIds: Set<String> = emptySet(),
    /** Lo máximo que se puede ahorrar con lo que falta, sin exclusiones. */
    val maxSavedSeconds: Int = 0,
    /** false si ni aplicando todo se llega al objetivo en minutos. */
    val targetReached: Boolean = true,
)

/**
 * Objetivo de «Acortar sesión»: minutos que se quieren ahorrar; `minutes == null` es «lo mínimo eficaz» (todas las tácticas
 * razonables a la vez). Es lo único que la persona elige: el motor decide el resto.
 */
data class ShortenGoal(val minutes: Int?) {
    val isMinimum: Boolean get() = minutes == null

    companion object {
        val MINIMUM = ShortenGoal(null)
    }
}

enum class ShortenItemKind { STRENGTH, CARDIO, SUPERSET_REST, NEW_SUPERSET }

/** Papel con el que el motor trata un ejercicio: decide la táctica y su orden. */
enum class ShortenRole { HEAVY_COMPOUND, COMPOUND, ISOLATION, CARDIO, SUPERSET }

/** Una línea de la hoja: qué cambia en un ejercicio (o superserie), en lenguaje claro. */
data class ShortenItemChange(
    val itemId: String,
    val kind: ShortenItemKind,
    val role: ShortenRole,
    val title: String,
    /** «3 → 2 series · última en drop-set · descanso 2:00 → 1:30», «20 → 15 min», «Quitar de hoy». Vacío si está excluida. */
    val detail: String,
    val savedSeconds: Int,
    val excluded: Boolean = false,
    val removesExercise: Boolean = false,
)

data class UltraFastExerciseChange(
    val exerciseId: String,
    val exerciseName: String,
    val beforeSets: Int,
    val afterSets: Int,
    val beforeTechnique: String,
    val afterTechnique: String,
    val reason: UltraFastReason,
    val wasDensified: Boolean,
    val wasReduced: Boolean,
)

data class UltraFastSupersetChange(
    val exerciseIdA: String,
    val exerciseIdB: String,
    val nameA: String,
    val nameB: String,
    val machineKey: String,
)

enum class UltraFastReason {
    PROTECTED_BASIC,
    DANGEROUS_COMPLEX,
    ISOLATION_DENSIFIED,
    COMPOUND_REDUCED,
    MANUAL_OVERRIDE_ALLOWED,
    MANUAL_OVERRIDE_BLOCKED,
    SUPERSET_POLEA_SMITH,
}

data class UltraFastApplyResult(
    val transformedExercises: List<Exercise>,
    val supersetGroups: List<com.example.kpkn.data.models.SupersetGroup>,
    val preview: UltraFastPreview,
    /** Ejercicios que salen de la sesión de hoy (no están en [transformedExercises]). */
    val removedExerciseIds: Set<String> = emptySet(),
)

data class SeriesTechniqueTarget(
    val exerciseId: String,
    val fromIdx: Int,
    val toIdx: Int,
    val technique: SeriesTechnique,
)

// UI state kept in WorkoutUiState (serializable snapshot separately if needed)
data class UltraFastUiState(
    val preview: UltraFastPreview? = null,
    val isApplied: Boolean = false,
    val savedSeconds: Int = 0,
    val showPreviewSheet: Boolean = false,
)
