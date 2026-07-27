package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPrefillBridgeTest {

    @Test
    fun resolveDefaultSplitId_maps_track_labels_to_real_splits() {
        assertEquals("pl_classic_4", SessionPrefillBridge.resolveDefaultSplitId("Powerlifting"))
        assertEquals("ppl_ul", SessionPrefillBridge.resolveDefaultSplitId("Powerbuilding"))
        assertEquals("ppl_x6", SessionPrefillBridge.resolveDefaultSplitId("Culturismo"))
        assertEquals("ul_x4", SessionPrefillBridge.resolveDefaultSplitId(null))

        listOf("pl_classic_4", "ppl_ul", "ppl_x6", "ul_x4").forEach { id ->
            assertTrue("$id debe existir en SPLIT_TEMPLATES", SPLIT_TEMPLATES.any { it.id == id })
        }
    }

    @Test
    fun resolveSplit_prefers_protocol_default_over_program_and_fallback() {
        val program = Program(id = "p", name = "P", selectedSplitId = "fullbody_x3")
        val resolved = SessionPrefillBridge.resolveSplit(program, protocolDefaultSplitId = "ppl_x6", fallbackTrackLabel = "Powerlifting")
        assertEquals("ppl_x6", resolved?.id)
    }

    @Test
    fun resolveSplit_falls_back_to_program_selection_then_track_default() {
        val withSelection = Program(id = "p", name = "P", selectedSplitId = "fullbody_x3")
        assertEquals("fullbody_x3", SessionPrefillBridge.resolveSplit(withSelection)?.id)

        val withoutSelection = Program(id = "p2", name = "P2")
        assertEquals("pl_classic_4", SessionPrefillBridge.resolveSplit(withoutSelection, fallbackTrackLabel = "Powerlifting")?.id)
    }

    @Test
    fun prefillIfEmpty_is_noop_when_program_already_has_sessions() {
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "531-base" }
        val populated = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "P"), protocol)
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }

        val result = SessionPrefillBridge.prefillIfEmpty(populated, split)
        assertEquals(populated.macrocycles, result.macrocycles)
    }

    @Test
    fun prefillIfEmpty_returns_program_unchanged_when_split_is_null() {
        val program = Program(id = "p", name = "P")
        val result = SessionPrefillBridge.prefillIfEmpty(program, null)
        assertEquals(program, result)
        assertNull(result.selectedSplitId)
    }
}
