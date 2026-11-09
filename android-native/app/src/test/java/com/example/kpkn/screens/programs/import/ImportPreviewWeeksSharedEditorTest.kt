package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.training.importer.ProgramImportParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h1400dp-xxhdpi")
class ImportPreviewWeeksSharedEditorTest {
    @get:Rule
    val rule = createComposeRule()

    private fun imported() = ProgramImportParser.parseText(
        "Día 1 — Pierna\nSentadilla 4x6 @120kg\nPrensa 3x10",
        "Mi programa",
    )

    @Test
    fun confirmedExerciseOpensTheExistingSessionEditor() {
        val draft = imported()
        val dayId = draft.weeks.single().days.single().id
        var openedSession: Pair<Int, String>? = null
        rule.setContent {
            ImportPreviewWeeks(
                imported = draft,
                resolutions = mapOf("Sentadilla" to "high_bar_back_squat__barbell"),
                onEdit = {},
                namesOf = { ids -> ids.associateWith { "Sentadilla trasera barra alta" } },
                onOpenCatalog = { _, _ -> error("A confirmed exercise should not reopen catalog matching") },
                onEditSession = { week, day -> openedSession = week to day },
            )
        }

        rule.onNodeWithTag("import-exercise-Sentadilla").performClick()

        assertEquals(1 to dayId, openedSession)
        rule.onAllNodesWithTag("import-edit-panel").assertCountEquals(0)
    }

    @Test
    fun undecidedExerciseOpensTheSharedCatalogInsteadOfAnInlineEditor() {
        val draft = imported()
        val exercise = draft.weeks.single().days.single().exercises.first()
        var chosenForCatalog: Pair<String, String>? = null
        var openedSession = false
        rule.setContent {
            ImportPreviewWeeks(
                imported = draft,
                resolutions = emptyMap(),
                onEdit = {},
                namesOf = { emptyMap() },
                onOpenCatalog = { id, name -> chosenForCatalog = id to name },
                onEditSession = { _, _ -> openedSession = true },
            )
        }

        rule.onNodeWithTag("import-exercise-Sentadilla").performClick()

        assertEquals(exercise.id to exercise.rawName, chosenForCatalog)
        assertEquals(false, openedSession)
        rule.onAllNodesWithTag("import-edit-panel").assertCountEquals(0)
    }
}
