package com.example.kpkn.data.protocols

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolLibraryTest {

    @Test
    fun library_has_unique_ids_and_at_least_one_block_each() {
        assertEquals(PROTOCOL_LIBRARY.size, PROTOCOL_LIBRARY.map { it.id }.distinct().size)
        assertTrue(PROTOCOL_LIBRARY.all { it.blocks.isNotEmpty() })
    }

    @Test
    fun declared_defaultSplit_resolves_to_a_real_split_template() {
        assertTrue("Cada protocolo debe declarar un defaultSplit explícito", PROTOCOL_LIBRARY.all { !it.defaultSplit.isNullOrBlank() })
        PROTOCOL_LIBRARY.mapNotNull { it.defaultSplit }.forEach { splitId ->
            assertTrue("$splitId debe existir en SPLIT_TEMPLATES", SPLIT_TEMPLATES.any { it.id == splitId })
        }
    }

    @Test
    fun f4_expands_library_with_new_powerlifting_powerbuilding_and_bodybuilding_entries() {
        val newIds = setOf("nsuns-531", "sbs-hybrid", "phul-base", "phat-base", "ppl-hypertrophy")
        assertTrue(newIds.all { id -> PROTOCOL_LIBRARY.any { it.id == id } })
        assertTrue(PROTOCOL_LIBRARY.size >= 16)
    }
}
