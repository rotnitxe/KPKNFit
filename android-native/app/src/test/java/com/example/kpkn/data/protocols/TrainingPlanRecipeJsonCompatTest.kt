package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Program
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanRecipeJsonCompatTest {
    private val codec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun legacy_protocol_json_decodes_with_null_recipe() {
        val payload = """
            {"id":"gzcl-base","name":"GZCL","emoji":"x","description":"d","author":"Cody Lefever",
             "blocks":[{"name":"A","weeks":4,"goal":"Acumulación"}]}
        """.trimIndent()
        val protocol = codec.decodeFromString(Protocol.serializer(), payload)
        assertNull(protocol.recipe)
        assertEquals(ProtocolPublicationStatus.HIDDEN_UNVERIFIED, protocol.publicationStatus)
        assertTrue(protocol.exemptions.isEmpty())
    }

    @Test
    fun legacy_program_json_decodes_without_source_recipe() {
        val program = codec.decodeFromString(Program.serializer(), """{"id":"p","name":"Base"}""")
        assertNull(program.sourceRecipe)
        assertEquals(com.example.kpkn.data.models.AutoregulationMode.OFF, program.autoregulationMode)
        assertNull(program.powerliftingProfile)
    }

    @Test
    fun pending_action_legacy_and_new_enum_decode() {
        val legacy = codec.decodeFromString(
            PendingProgramAction.serializer(),
            """{"type":"CONFIRM_DELOAD","message":"descarga"}""",
        )
        assertEquals(PendingProgramActionType.CONFIRM_DELOAD, legacy.type)
        assertTrue(legacy.proposals.isEmpty())
        val next = codec.decodeFromString(
            PendingProgramAction.serializer(),
            """{"type":"CONFIRM_AUTOREGULATION","message":"AUGE","proposals":[{"kind":"ADJUST_TM","explanation":"AMRAP corto"}]}""",
        )
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, next.type)
        assertEquals(1, next.proposals.size)
    }
}
