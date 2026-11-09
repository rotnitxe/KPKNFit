package com.example.kpkn.screens.nutrition.design.logger

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1400dp-mdpi")
class LoggerIdentitySheetTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun candidates_maintain_their_query_context_and_onUse_receives_it() {
        var usedId: String? = null
        var usedQuery: String? = null

        val pureCandidate = LoggerCandidate(
            id = "pure-id",
            name = "Puré de papas natural",
            caption = "200 g · 180 kcal",
            likely = true,
            queryContext = "puré de papas",
        )

        rule.setContent {
            LoggerIdentitySheetBody(
                typed = "puré de papas",
                candidates = listOf(pureCandidate),
                onUse = { id, query ->
                    usedId = id
                    usedQuery = query
                },
                searchQuery = "",
                onSearchQueryChange = {},
                searchResults = null,
            )
        }

        rule.onNodeWithText("Usar este").performScrollTo().assertIsEnabled().performClick()

        assertEquals("pure-id", usedId)
        assertEquals("puré de papas", usedQuery)
    }

    @Test
    fun typing_search_query_invalidates_selection_and_hides_old_candidates_while_searching() {
        var query by mutableStateOf("")
        var results by mutableStateOf<List<LoggerCandidate>?>(null)
        var usedId: String? = null
        var usedQuery: String? = null

        val pureCandidate = LoggerCandidate(
            id = "pure-id",
            name = "Puré de papas natural",
            caption = "200 g · 180 kcal",
            likely = true,
            queryContext = "puré de papas",
        )

        rule.setContent {
            LoggerIdentitySheetBody(
                typed = "puré de papas",
                candidates = listOf(pureCandidate),
                onUse = { id, q ->
                    usedId = id
                    usedQuery = q
                },
                searchQuery = query,
                onSearchQueryChange = { query = it },
                searchResults = results,
                initiallySearching = true,
            )
        }

        // Before typing: candidate is shown and selected
        rule.onNodeWithText("Puré de papas natural").assertExists()

        // User types a new query
        query = "pulpa de cerdo"
        rule.waitForIdle()

        // Old candidate must NOT be offered as valid result of "pulpa de cerdo" while search is pending
        rule.onNodeWithText("Puré de papas natural").assertDoesNotExist()
        rule.onNodeWithText("Buscando…").assertExists()
        // Selection must be invalidated and "Usar este" must be disabled
        rule.onNodeWithText("Usar este").assertIsNotEnabled()

        // Now search results arrive for "pulpa de cerdo"
        val cerdoCandidate = LoggerCandidate(
            id = "cerdo-id",
            name = "Pulpa de cerdo cocida",
            caption = "150 g · 240 kcal",
            likely = false,
            queryContext = "pulpa de cerdo",
        )
        results = listOf(cerdoCandidate)
        rule.waitForIdle()

        rule.onNodeWithText("Pulpa de cerdo cocida").performScrollTo().assertExists().performClick()
        rule.onNodeWithText("Usar este").performScrollTo().assertIsEnabled().performClick()

        assertEquals("cerdo-id", usedId)
        assertEquals("pulpa de cerdo", usedQuery)
    }

    @Test
    fun clearing_query_restores_original_candidates_and_context() {
        var query by mutableStateOf("algo")
        var results by mutableStateOf<List<LoggerCandidate>?>(emptyList())
        var usedId: String? = null
        var usedQuery: String? = null

        val pureCandidate = LoggerCandidate(
            id = "pure-id",
            name = "Puré de papas preparado",
            caption = "200 g · 210 kcal",
            likely = false,
            queryContext = "puré de papas",
        )

        rule.setContent {
            LoggerIdentitySheetBody(
                typed = "puré de papas",
                candidates = listOf(pureCandidate),
                onUse = { id, q ->
                    usedId = id
                    usedQuery = q
                },
                searchQuery = query,
                onSearchQueryChange = { query = it },
                searchResults = results,
                initiallySearching = true,
            )
        }

        // With query = "algo" and empty results, shows "No se encontraron alimentos"
        rule.onNodeWithText("No se encontraron alimentos").assertExists()
        rule.onNodeWithText("Puré de papas preparado").assertDoesNotExist()

        // Clear query
        query = ""
        results = null
        rule.waitForIdle()

        // Returns to original candidates
        rule.onNodeWithText("Puré de papas preparado").performScrollTo().assertExists().performClick()
        rule.onNodeWithText("Usar este").performScrollTo().assertIsEnabled().performClick()

        assertEquals("pure-id", usedId)
        assertEquals("puré de papas", usedQuery)
    }
}
