package com.example.kpkn.screens.programs.importer

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val ownExerciseMutex = Mutex()

internal suspend fun findOrCreateImportedExercise(rawName: String): Result<ExerciseMuscleInfo> =
    ownExerciseMutex.withLock {
        val name = rawName.trim()
        val normalized = ExerciseMatchLexicon.normalize(name)
        val existing = CustomExerciseRepository.customExercises.value.firstOrNull {
            it.isCustom && it.id.startsWith("custom:", ignoreCase = true) &&
                ExerciseMatchLexicon.normalize(it.name) == normalized
        }
        if (existing != null) Result.success(existing)
        else withContext(NonCancellable) {
            CustomExerciseRepository.upsertAndAwait(
                ExerciseMuscleInfo(id = "custom:import-${UUID.randomUUID()}", name = name, isCustom = true),
            )
        }
    }

internal suspend fun previewOwnImportExercise(rawName: String): Result<ExerciseMuscleInfo> {
    val name = rawName.trim()
    val key = ExerciseMatchLexicon.normalize(name)
    val id = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))
    return Result.success(ExerciseMuscleInfo(id = "custom:preview-$id", name = name, isCustom = true))
}

internal data class MaterializedImport(
    val draft: ImportedProgramDraft,
    val exercises: List<ExerciseMuscleInfo>,
)

internal suspend fun materializeImportExercises(
    draft: ImportedProgramDraft,
    resolutions: Map<String, String?>,
    materialize: suspend (String) -> Result<ExerciseMuscleInfo> = ::findOrCreateImportedExercise,
): MaterializedImport {
    fun isOwn(exercise: ImportedExercise): Boolean {
        val choice = exercise.catalogSelectionId ?: resolutions[exercise.rawName]
        return choice.equals("own", ignoreCase = true) || (choice == null && resolutions.containsKey(exercise.rawName))
    }

    val own = draft.weeks.flatMap { it.days }.flatMap { it.exercises }.filter(::isOwn)
    val created = linkedMapOf<String, ExerciseMuscleInfo>()
    for (exercise in own.distinctBy { ExerciseMatchLexicon.normalize(it.rawName) }) {
        val result = materialize(exercise.rawName).getOrElse { error ->
            if (error is CancellationException) throw error
            throw IllegalStateException("No pudimos guardar el ejercicio propio «${exercise.rawName}».", error)
        }
        created[ExerciseMatchLexicon.normalize(exercise.rawName)] = result
    }
    val resolvedDraft = if (created.isEmpty()) draft else draft.copy(weeks = draft.weeks.map { week ->
        week.copy(days = week.days.map { day ->
            day.copy(exercises = day.exercises.map { exercise ->
                if (isOwn(exercise)) exercise.copy(
                    catalogSelectionId = created.getValue(ExerciseMatchLexicon.normalize(exercise.rawName)).id,
                ) else exercise
            })
        })
    })
    return MaterializedImport(resolvedDraft, CustomExerciseRepository.customExercises.value + created.values)
}
