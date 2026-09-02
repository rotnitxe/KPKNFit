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
