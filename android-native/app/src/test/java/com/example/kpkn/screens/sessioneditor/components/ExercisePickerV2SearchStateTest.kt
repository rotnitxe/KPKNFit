package com.example.kpkn.screens.sessioneditor.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExercisePickerV2SearchStateTest {

    @Test
    fun stale_debounced_query_cannot_render_results_or_create_prompt() {
        assertFalse(catalogSearchResultsAreCurrent("peso", "peso muerto", true))
        assertFalse(catalogSearchResultsAreCurrent("peso", "peso", false))
        assertTrue(catalogSearchResultsAreCurrent("peso", "peso", true))

        assertFalse(
            shouldShowCatalogCreateSuggestion(
                query = "peso",
                searchSettled = false,
                visibleCatalogResultCount = 0,
                globalCatalogResultCount = 0,
                customResultCount = 0,
            ),
        )
    }

    @Test
    fun create_prompt_requires_no_catalog_or_custom_coincidence() {
        assertFalse(
            shouldShowCatalogCreateSuggestion(
                query = "ejercicio inventado",
                searchSettled = true,
                visibleCatalogResultCount = 0,
                globalCatalogResultCount = 1,
                customResultCount = 0,
            ),
        )
        assertFalse(
            shouldShowCatalogCreateSuggestion(
                query = "ejercicio inventado",
                searchSettled = true,
                visibleCatalogResultCount = 0,
                globalCatalogResultCount = 0,
                customResultCount = 1,
            ),
        )
        assertTrue(
            shouldShowCatalogCreateSuggestion(
                query = "ejercicio inventado",
                searchSettled = true,
                visibleCatalogResultCount = 0,
                globalCatalogResultCount = 0,
                customResultCount = 0,
            ),
        )
    }

    @Test
    fun card_tap_only_toggles_expansion() {
        assertEquals("bench_press", toggleCatalogDefinitionExpansion(null, "bench_press"))
        assertNull(toggleCatalogDefinitionExpansion("bench_press", "bench_press"))
        assertEquals(
            "romanian_deadlift",
            toggleCatalogDefinitionExpansion("bench_press", "romanian_deadlift"),
        )
    }
}
