package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SessionCloneApplyMode
import com.example.kpkn.screens.sessioneditor.SessionCloneDayOption
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class EditorSheetsD5D8Test {
    @get:Rule val rule = createComposeRule()

    private val session = Session(
        id = "sesion",
        name = "Pierna A",
        exercises = listOf(Exercise(id = "squat", name = "Sentadilla", sets = listOf(ExerciseSet(id = "serie")))),
        targetDurationMinutes = 45,
    )

    @Test fun versionesPideConfirmacionSiHayCambiosSinGuardar() {
        var restored = 0
        val version = SessionDraftSnapshot(
            id = "v1", session = session.copy(name = "Antes"), savedAtMs = 1L,
            reason = "Sesión entrenada", changedFields = listOf("series"),
            exerciseCount = 1, setCount = 1, partCount = 0,
        )
        rule.setContent {
            HistorySheet(
                uiState = SessionEditorUiState(session = session, localDraftHistory = listOf(version), hasUnsavedChanges = true),
                onRestoreSnapshot = { restored++ },
            )
        }
        rule.onNodeWithText("Restaurar").performClick()
        assertEquals(0, restored)
        rule.onNodeWithText("¿Restaurar esta versión?").assertExists()
    }

    @Test fun transferirCopiaAlDestinoSeleccionadoSinMoverLaSesion() {
        val target = SessionCloneDayOption(
            key = "dia-2", macroIndex = 0, mesoIndex = 0, weekId = "semana", dayOfWeek = 3,
            macroName = "Bloque", blockName = "Bloque 1", mesoName = "Mesociclo 1", weekName = "Semana 1",
        )
        var copied: Set<String>? = null
        var mode: SessionCloneApplyMode? = null
        rule.setContent {
            SessionClonerSheet(
                uiState = SessionEditorUiState(session = session, cloneDayOptions = listOf(target), weekId = "semana"),
                onCloneCurrentToTargets = { keys, _, selectedMode -> copied = keys; mode = selectedMode },
                onImportFromSource = { _, _, _ -> },
            )
        }
        rule.onNodeWithText("Miércoles · Semana 1").performClick()
        rule.onNodeWithText("Copiar ahora").performClick()
        assertEquals(setOf("dia-2"), copied)
        assertEquals(SessionCloneApplyMode.APPEND, mode)
    }
}
