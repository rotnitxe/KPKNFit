package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.TechniqueModifier

/**
 * Auto-reparación de nombres visibles (D6 del plan 2026-09-16).
 *
 * Función pura: re-deriva [Exercise.name] para todo ejercicio con
 * [Exercise.catalogConfigurationId] resoluble en el índice y sin prefijo
 * `custom:`. Solo reescribe `name`; conserva variantName/técnica e identidad.
 * Sin migración Room: los nombres viven en blobs JSON y se reparan en
 * carga/guardado.
 *
 * El nombre visible siempre se deriva del id de configuración; nunca al revés.
 */
object SessionCatalogNameReconciler {

    fun reconcileExercise(
        exercise: Exercise,
        displayNameIndex: Map<String, String>,
    ): Exercise {
        val remapped = remapLegacyCatalogConfiguration(exercise)
        val configurationId = remapped.catalogConfigurationId?.trim().orEmpty()
        if (configurationId.isBlank()) return remapped
        if (isManualCustom(remapped)) return remapped
        val normalized = configurationId.lowercase()
        val derived = displayNameIndex[configurationId]
            ?: displayNameIndex[normalized]
            ?: return remapped
        if (remapped.name.trim() == derived.trim()) return remapped
        return remapped.copy(name = derived)
    }

    /**
     * Legacy protocol sessions used [CatalogIds.BP] + [TechniqueModifier.PAUSE_2S]
     * instead of the approved [CatalogIds.BP_PAUSE] configuration.
     */
    private fun remapLegacyCatalogConfiguration(exercise: Exercise): Exercise {
        if (exercise.isCompetitionLift) return exercise
        if (exercise.techniqueModifier != TechniqueModifier.PAUSE_2S) return exercise
        val configurationId = exercise.catalogConfigurationId?.trim()?.lowercase().orEmpty()
        if (configurationId != CatalogIds.BP.lowercase()) return exercise
        return exercise.copy(
            name = exercise.name,
            exerciseDbId = CatalogIds.BP_PAUSE,
            exerciseId = CatalogIds.BP_PAUSE,
            canonicalExerciseId = CatalogIds.BP_PAUSE,
            exerciseFamilyId = "paused_bench_press",
            catalogDefinitionId = "paused_bench_press",
            catalogConfigurationId = CatalogIds.BP_PAUSE,
            relativeToCanonicalExerciseId = null,
            relationshipType = null,
            relationshipNotes = null,
            variantName = null,
            techniqueModifier = null,
        )
    }

    fun reconcileSession(
        session: Session,
        displayNameIndex: Map<String, String>,
    ): Session {
        val withNames = if (displayNameIndex.isEmpty()) {
            session
        } else {
            session.copy(
                exercises = session.exercises.map { reconcileExercise(it, displayNameIndex) },
                parts = session.parts.map { part ->
                    part.copy(exercises = part.exercises.map { reconcileExercise(it, displayNameIndex) })
                },
                sessionB = session.sessionB?.let { reconcileSession(it, displayNameIndex) },
                sessionC = session.sessionC?.let { reconcileSession(it, displayNameIndex) },
                sessionD = session.sessionD?.let { reconcileSession(it, displayNameIndex) },
                trainingBackup = session.trainingBackup?.copy(
                    exercises = session.trainingBackup.exercises.map { reconcileExercise(it, displayNameIndex) },
                    parts = session.trainingBackup.parts.map { backupPart ->
                        backupPart.copy(exercises = backupPart.exercises.map { reconcileExercise(it, displayNameIndex) })
                    },
                ),
            )
        }
        return normalizeSessionStructure(withNames)
    }

    /**
     * Protocol materialization used to mirror every slot in [Session.exercises] and
     * again inside [Session.parts]. Drops each loose exercise whose id also exists
     * in a strength part (D3: per-item, not all-or-nothing). Loose exercises with
     * ids NOT present in parts are user drafts and stay untouched.
     */
    fun normalizeSessionStructure(session: Session): Session {
        val collapsed = collapseRedundantLooseExercises(session)
        return collapsed.copy(
            sessionB = collapsed.sessionB?.let(::normalizeSessionStructure),
            sessionC = collapsed.sessionC?.let(::normalizeSessionStructure),
            sessionD = collapsed.sessionD?.let(::normalizeSessionStructure),
            trainingBackup = collapsed.trainingBackup?.let { backup ->
                val backupSession = Session(
                    id = session.id,
                    name = session.name,
                    exercises = backup.exercises,
                    parts = backup.parts,
                )
                val normalizedBackup = collapseRedundantLooseExercises(backupSession)
                backup.copy(
                    exercises = normalizedBackup.exercises,
                    parts = normalizedBackup.parts,
                )
            },
        )
    }

    private fun collapseRedundantLooseExercises(session: Session): Session {
        if (session.exercises.isEmpty()) return session
        val partExerciseIds = session.parts
            .filterNot { it.isCardioPart() }
            .flatMap { it.exercises }
            .map { it.id }
            .toSet()
        if (partExerciseIds.isEmpty()) return session
        val kept = session.exercises.filterNot { it.id in partExerciseIds }
        return if (kept.size == session.exercises.size) session else session.copy(exercises = kept)
    }

    private fun isManualCustom(exercise: Exercise): Boolean =
        listOf(
            exercise.exerciseDbId,
            exercise.exerciseId,
            exercise.canonicalExerciseId,
            exercise.exerciseFamilyId,
        ).any { it?.startsWith("custom:", ignoreCase = true) == true }
}
