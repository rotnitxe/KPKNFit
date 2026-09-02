package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TechniqueType

/**
 * Two dropset / rest-pause logics that must not be mixed:
 *
 * - [STACKED_ON_SET]: intensity technique *inside* a working set (editor chips,
 *   card reverse). Mini-series live in `CompletedSet.dropSets` / `restPauses`.
 *   Between-set rest stays blocked until the technique is closed.
 * - [VOLUME_REPLACED]: the set *is* the dropset/RP used to save volume or time
 *   (`applyMarkedSeriesTechnique`, relator CONVERT, UltraFast densify). Rest is
 *   0 (dropset chain) or 15s (RP). No in-card guided flow.
 */
enum class SetTechniqueScope {
    NONE,
    STACKED_ON_SET,
    VOLUME_REPLACED,
}

fun ExerciseSet.techniqueScope(): SetTechniqueScope {
    val dropTech = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    val rpTech = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
    val betweenMarked = dropTech?.params?.get("betweenMarked") == "true" ||
        rpTech?.params?.get("betweenMarked") == "true"
    if (betweenMarked) return SetTechniqueScope.VOLUME_REPLACED

    val flagged = isDropSet || isRestPause || dropTech != null || rpTech != null
    if (!flagged) return SetTechniqueScope.NONE

    val densified = (isDropSet && dropSets.isNotEmpty()) || (isRestPause && restPauses.isNotEmpty())
    if (densified) return SetTechniqueScope.VOLUME_REPLACED

    return SetTechniqueScope.STACKED_ON_SET
}

fun ExerciseSet.isStackedIntensityTechnique(): Boolean =
    techniqueScope() == SetTechniqueScope.STACKED_ON_SET

fun ExerciseSet.isVolumeReplacedTechnique(): Boolean =
    techniqueScope() == SetTechniqueScope.VOLUME_REPLACED

fun ExerciseSet.volumeReplacedLabel(): String? {
    if (!isVolumeReplacedTechnique()) return null
    val drop = isDropSet || plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET }
    return if (drop) "DROPSET" else "REST PAUSE"
}
