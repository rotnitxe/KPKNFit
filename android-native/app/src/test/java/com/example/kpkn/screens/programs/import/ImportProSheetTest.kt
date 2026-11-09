package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportProSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun freeWeekChoiceRequiresConfirmationAndOnlyAllowsWeeksWithinTheLimit() {
        var keptWeek: Int? = null

        rule.setContent {
            ImportProSheetContent(
                weeks = listOf(week(1, 6), week(2, 4), week(3, 3)),
                onUpgrade = null,
                onKeepWeek = { keptWeek = it },
                onClose = {},
            )
        }

        rule.onNodeWithTag("import-pro-keep-week").performClick()
        rule.onNodeWithTag("import-free-week-confirm").assertExists()
        assertNull(keptWeek)

        rule.onNodeWithTag("import-free-week-option-1").assertIsNotEnabled()
        rule.onNodeWithTag("import-free-week-option-3").performScrollTo().performClick()
        rule.onNodeWithTag("import-free-week-confirm").performScrollTo().performClick()

        assertEquals(3, keptWeek)
    }

    @Test
    fun freeWeekCutIsHiddenAfterTheCanonicalEditorHasStarted() {
        rule.setContent {
            ImportProSheetContent(
                weeks = listOf(week(1, 4), week(2, 4)),
                onUpgrade = null,
                onKeepWeek = null,
                onClose = {},
            )
        }

        rule.onNodeWithTag("import-pro-keep-week").assertDoesNotExist()
        rule.onNodeWithTag("import-pro-week-cut-unavailable").assertExists()
    }

    private fun week(index: Int, sessionCount: Int) = ImportedWeek(
        index = index,
        days = (1..sessionCount).map { day ->
            ImportedDay(id = "w$index-d$day", name = "Sesión $day", exercises = emptyList())
        },
    )
}
