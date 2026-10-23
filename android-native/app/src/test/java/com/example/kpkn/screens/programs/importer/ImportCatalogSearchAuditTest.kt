package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportCatalogSearchAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun previousResultsCannotBePickedWhileAnotherQueryIsLoading() {
        val pending = CompletableDeferred<List<ExerciseNameCandidate>>()
        rule.setContent {
            Column {
                ImportCatalogSearch(key = "day", search = { query ->
                    if (query == "press") listOf(ExerciseNameCandidate("press", "Press anterior", 1.0)) else pending.await()
                }, onPick = {})
            }
        }
        rule.onNodeWithTag("import-choice-search").performTextReplacement("press")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Press anterior").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("import-choice-search").performTextReplacement("sentadilla")
        rule.onNodeWithText("Press anterior").assertDoesNotExist()
        pending.complete(emptyList())
    }
}
