package com.example.kpkn.domain.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-level contract for quarantine handling: corrupt payloads must be
 * reported once and not re-hydrated as valid programs.
 */
class ProgramQuarantineContractTest {

    @Test
    fun loadProgramsSafely_isolates_null_decodes() {
        val result = ProgramMigrationEngine.loadProgramsSafely(
            listOf(
                "ok" to com.example.kpkn.data.models.Program(id = "ok", name = "OK"),
                "bad" to null,
            ),
        )
        assertEquals(1, result.programs.size)
        assertEquals("ok", result.programs.first().id)
        assertEquals(listOf("bad"), result.corruptedIds)
    }

    @Test
    fun loadProgramsSafely_does_not_promote_corrupt_ids_into_valid_list() {
        val result = ProgramMigrationEngine.loadProgramsSafely(
            listOf("x" to null, "y" to null),
        )
        assertTrue(result.programs.isEmpty())
        assertEquals(2, result.corruptedIds.size)
        assertTrue(result.corruptedIds.containsAll(listOf("x", "y")))
    }
}
