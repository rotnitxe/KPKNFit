package com.example.kpkn.screens.workout

import androidx.compose.runtime.saveable.Saver
import com.example.kpkn.data.models.Exercise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val editSeriesJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Android-saveable local draft; survives recreation before Confirm changes. */
internal val WorkoutEditSeriesDraftSaver = Saver<Exercise, String>(
    save = { editSeriesJson.encodeToString(it) },
    restore = { runCatching { editSeriesJson.decodeFromString<Exercise>(it) }.getOrNull() },
)

internal val WorkoutStructureSheetsStateSaver = Saver<WorkoutStructureSheetsState, String>(
    save = { it.editSheetExerciseId.orEmpty() },
    restore = { exerciseId -> WorkoutStructureSheetsState().apply { editSheetExerciseId = exerciseId.takeIf { it.isNotBlank() } } },
)
