package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscomfortSuggestionEngineTest {

    @Test
    fun `empty muscles produce no suggestions`() {
        assertTrue(DiscomfortSuggestionEngine.suggestForMuscles(emptyList()).isEmpty())
    }

    @Test
    fun `pectoral session suggests shoulder anterior and not knee`() {
        val suggestions = DiscomfortSuggestionEngine.suggestForMuscles(listOf("Pectorales"))
        val ids = suggestions.map { it.id }
        assertTrue("shoulder_anterior".let(ids::contains))
        assertTrue("knee_patellar".let { it !in ids })
    }

    @Test
    fun `leg session suggests knee and hip discomforts`() {
        val suggestions = DiscomfortSuggestionEngine.suggestForMuscles(
            listOf("Cuádriceps", "Isquiosurales"),
        )
        val ids = suggestions.map { it.id }
        assertTrue("knee_patellar".let(ids::contains))
        assertTrue("knee_medial".let(ids::contains))
        assertTrue("hamstring_proximal".let(ids::contains))
        assertTrue("shoulder_anterior".let { it !in ids })
    }

    @Test
    fun `display head matches pillar of related muscle`() {
        // "Deltoides Anterior" (specific head) debe sugerir shoulder_anterior (pillar Deltoides)
        val suggestions = DiscomfortSuggestionEngine.suggestForMuscles(listOf("Deltoides Anterior"))
        assertTrue(suggestions.map { it.id }.contains("shoulder_anterior"))
    }

    @Test
    fun `search finds by label`() {
        val results = DiscomfortSuggestionEngine.search("lumbar")
        assertTrue(results.map { it.id }.contains("lumbar"))
    }

    @Test
    fun `search finds by description and is case insensitive`() {
        assertTrue(DiscomfortSuggestionEngine.search("AQUILES").map { it.id }.contains("achilles"))
        assertTrue(DiscomfortSuggestionEngine.search("rótula").map { it.id }.contains("knee_patellar"))
    }

    @Test
    fun `search with multiple terms narrows results`() {
        val results = DiscomfortSuggestionEngine.search("codo cara interna")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.label.contains("codo", ignoreCase = true) })
    }

    @Test
    fun `blank search returns empty`() {
        assertTrue(DiscomfortSuggestionEngine.search("   ").isEmpty())
    }

    @Test
    fun `suggestions never include none`() {
        val suggestions = DiscomfortSuggestionEngine.suggestForMuscles(listOf("Pectorales", "Cuádriceps"))
        assertTrue(suggestions.none { it.id == "none" })
    }
}
