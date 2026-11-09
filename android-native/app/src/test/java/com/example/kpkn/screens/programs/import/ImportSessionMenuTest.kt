package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.training.importer.ImportedExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportSessionMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = (1..4).map { index -> ImportedExercise("exercise-$index", "Movimiento $index", 3, 8, 10) }

    @Test
    fun duplicateRemoveAndMoveActionsAreAvailableWithoutAParallelSessionEditor() {
        var duplicate = false
        var removed = false
        var movedTo: Int? = null

        rule.setContent {
            ImportSessionMenuContent(
                name = "Pierna",
                dayLabel = "Miércoles",
                weekIndex = 2,
                exerciseCount = 4,
                exercises = exercises,
                defaultSplitName = "Día 2",
                selectedDayOfWeek = 3,
                onClose = {},
                onMove = { movedTo = it },
                onSplitAtExercise = { _, _ -> },
                onDuplicate = { duplicate = true },
                onRemove = { removed = true },
            )
        }

        rule.onNodeWithTag("import-session-menu-edit").assertDoesNotExist()
        rule.onNodeWithTag("import-session-menu-duplicate").performClick()
        rule.onNodeWithTag("import-session-menu-move").performClick()
        rule.onNodeWithTag("import-session-move-3").assertExists().performScrollTo().performClick()
        rule.onNodeWithTag("import-session-menu-remove").performScrollTo().performClick()

        assertTrue(duplicate)
        assertEquals(3, movedTo)
        assertTrue(removed)
    }

    @Test
    fun structuralActionsAreExplainedAndHiddenForLegacyEditedDrafts() {
        rule.setContent {
            ImportSessionMenuContent(
                name = "Pierna",
                dayLabel = "Miércoles",
                weekIndex = 2,
                exerciseCount = 4,
                exercises = exercises,
                defaultSplitName = "Día 2",
                selectedDayOfWeek = 3,
                allowStructureChanges = false,
                onClose = {},
                onMove = {},
                onSplitAtExercise = { _, _ -> },
                onDuplicate = {},
                onRemove = {},
            )
        }

        rule.onNodeWithTag("import-session-menu-edit").assertDoesNotExist()
        rule.onNodeWithTag("import-session-menu-move").performClick()
        rule.onNodeWithTag("import-session-move-3").assertExists()
        rule.onNodeWithTag("import-session-menu-duplicate").assertDoesNotExist()
        rule.onNodeWithTag("import-session-menu-remove").assertDoesNotExist()
        rule.onNodeWithTag("import-session-menu-structure-note").assertExists()
    }

    @Test
    fun splitActionNamesTheNewSessionAndKeepsTheSelectedExerciseBoundary() {
        var split: Pair<String, String>? = null
        rule.setContent {
            ImportSessionMenuContent(
                name = "Día sin asignar",
                dayLabel = "Día 1",
                weekIndex = 1,
                exerciseCount = exercises.size,
                exercises = exercises,
                defaultSplitName = "Día 2",
                selectedDayOfWeek = null,
                onClose = {},
                onMove = {},
                onSplitAtExercise = { exerciseId, name -> split = exerciseId to name },
                onDuplicate = {},
                onRemove = {},
            )
        }

        rule.onNodeWithTag("import-session-menu-split").performScrollTo().performClick()
        rule.onNodeWithTag("import-session-split-exercise-exercise-3").performScrollTo().performClick()
        rule.onNodeWithTag("import-session-split-confirm").performScrollTo().assertIsEnabled().performClick()

        assertEquals("exercise-3" to "Día 2", split)
    }
}
