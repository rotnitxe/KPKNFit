package com.example.kpkn.screens.programs.importer

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.domain.training.importer.ImportIssue
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportedWeek

/** Final import checks follow the complete Program once the existing session editor becomes canonical. */
internal fun canonicalImportIssues(source: ImportedProgramDraft, program: Program): List<ImportIssue> =
    canonicalImportIssues(source, program, ImportedProgramSourceMap(), emptyMap())

internal fun canonicalImportIssues(
    source: ImportedProgramDraft,
    program: Program,
    sourceMap: ImportedProgramSourceMap,
    resolutions: Map<String, String?>,
): List<ImportIssue> = ImportDraftValidation.checkOccurrences(
    canonicalReviewDraft(source, program, sourceMap, resolutions),
) { it.catalogSelectionId != null }

private fun canonicalReviewDraft(
    source: ImportedProgramDraft,
    program: Program,
    sourceMap: ImportedProgramSourceMap,
    resolutions: Map<String, String?>,
): ImportedProgramDraft {
    val sourceExercises = source.weeks.flatMap { week -> week.days.flatMap { it.exercises } }.associateBy { it.id }
    val sourceByProgramExerciseId = sourceMap.programExerciseIdsBySourceExerciseId.entries
        .groupBy({ it.value }, { sourceExercises[it.key] })
    val weeks = program.macrocycles.flatMap { macro ->
        macro.blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }
    }.mapIndexed { index, week ->
        ImportedWeek(
            index = index + 1,
            days = week.sessions.map { session ->
                ImportedDay(
                    id = session.id,
                    name = session.name,
                    exercises = session.allExercises().map { exercise ->
                        ImportedExercise(
                            id = exercise.id,
                            rawName = exercise.name,
                            sets = exercise.sets.size,
                            repsMin = 1,
                            repsMax = 1,
                            catalogSelectionId = catalogIdentity(exercise) ?: "own".takeIf {
                                sourceByProgramExerciseId[exercise.id].orEmpty().filterNotNull().let { mapped ->
                                    mapped.isNotEmpty() && mapped.all { imported ->
                                        imported.catalogSelectionId == null && resolutions.containsKey(imported.rawName) &&
                                            resolutions[imported.rawName] == null
                                    }
                                }
                            },
                        )
                    },
                )
            },
        )
    }
    return source.copy(weeks = weeks)
}

internal fun catalogIdentity(exercise: Exercise): String? =
    exercise.catalogConfigurationId?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(exercise.exerciseId, exercise.exerciseDbId)
            .firstOrNull { it.startsWith("custom:", ignoreCase = true) }
