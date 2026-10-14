package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.sessioneditor.SessionExerciseEditorBlock

internal fun buildSessionExerciseEditorBlocks(
    session: Session,
    containerExercises: List<Exercise>,
): List<SessionExerciseEditorBlock> {
    return containerExercises.map { exercise -> SessionExerciseEditorBlock.Single(exercise) }
}
