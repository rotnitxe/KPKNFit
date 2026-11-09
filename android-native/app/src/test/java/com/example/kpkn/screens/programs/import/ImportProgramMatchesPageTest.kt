package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import android.app.Application
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportProgramMatchesPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val imported = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay(
                        "d1",
                        "Empuje",
                        listOf(
                            ImportedExercise("e1", "Press banca", 3, 8, 8),
                            ImportedExercise("e2", "Remo", 3, 10, 10),
                            ImportedExercise("e3", "Remo", 3, 10, 10),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun confidentMatchesStayCollapsedAndRepeatedDoubtsAreGrouped() {
        assertEquals(
            listOf("Remo" to 2),
            importPendingMatchGroups(imported, mapOf("Press banca" to "cfg-bench"))
                .map { it.rawName to it.occurrences.size },
        )

        rule.setContent {
            ImportProgramMatchesPage(
                imported = imported,
                resolutions = mapOf("Press banca" to "cfg-bench"),
                pendingWarnings = 0,
                catalogUnavailable = false,
                catalogRetrying = false,
                suggest = { emptyList() },
                search = { emptyList() },
                namesOf = { mapOf("cfg-bench" to "Press banca con barra") },
                onEdit = {},
                onOpenCatalog = { _, _, _ -> },
                onOpenWarnings = {},
                onRetryCatalog = {},
                onAddExercise = {},
            )
        }

        rule.onNodeWithTag("import-match-group-Remo").assertExists()
        rule.onNodeWithTag("import-match-resolved-Press banca").assertDoesNotExist()
        rule.onNodeWithTag("import-match-toggle-all").performClick()
        rule.onNodeWithTag("import-match-show-resolved").performClick()
        rule.onNodeWithTag("import-match-resolved-change-Press banca").assertExists()
        rule.onNodeWithTag("import-match-source-Remo").assertExists()
        rule.onNodeWithTag("import-match-separate-Remo").performClick()
        rule.onNodeWithTag("import-match-occurrence-e2").assertExists()
        rule.onNodeWithTag("import-match-occurrence-e3").assertExists()
    }

    @Test
    fun catalogFailureOffersRetryWhileKeepingTheUnresolvedGroupVisible() {
        var retried = false
        rule.setContent {
            ImportProgramMatchesPage(
                imported = imported,
                resolutions = emptyMap(),
                pendingWarnings = 0,
                catalogUnavailable = true,
                catalogRetrying = false,
                suggest = { emptyList() },
                search = { emptyList() },
                namesOf = { emptyMap() },
                onEdit = {},
                onOpenCatalog = { _, _, _ -> },
                onOpenWarnings = {},
                onRetryCatalog = { retried = true },
                onAddExercise = {},
            )
        }

        rule.onNodeWithTag("import-match-retry-catalog").performClick()
        rule.onNodeWithTag("import-match-group-Press banca").assertExists()
        assertEquals(true, retried)
    }

    @Test
    fun sharedCatalogSuggestionsStayCompact() {
        rule.setContent {
            ImportCatalogSheetContent(
                rawName = "Sentadilla",
                suggest = { listOf(ExerciseNameCandidate("cfg-long-name", "Sentadilla trasera con barra alta", 0.95)) },
                search = { emptyList() },
                onSelect = {},
                onOpenFullCatalog = {},
                onClose = {},
            )
        }
        rule.waitForIdle()

        val candidateHeightDp = rule.onNodeWithTag("import-catalog-choice-cfg-long-name")
            .fetchSemanticsNode().boundsInRoot.height / rule.density.density
        assertTrue("La opción del catálogo ocupa $candidateHeightDp dp; debe quedar en una fila compacta.", candidateHeightDp <= 72f)
    }
}
