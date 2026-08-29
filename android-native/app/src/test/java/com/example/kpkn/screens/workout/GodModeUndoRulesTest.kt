package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class GodModeUndoRulesTest {
    @Test
    fun revertAtIndex_truncatesThatActionAndEverythingAfter() {
        val stack = listOf(
            GodModeUndoSnapshot(label = "Omitir serie"),
            GodModeUndoSnapshot(label = "Reordenar ejercicios"),
            GodModeUndoSnapshot(label = "Eliminar ejercicio"),
        )
        assertEquals(
            listOf("Omitir serie"),
            godModeUndoStackAfterRevert(stack, 1).map { it.label },
        )
        assertEquals(emptyList<String>(), godModeUndoStackAfterRevert(stack, 0).map { it.label })
        assertEquals(
            listOf("Omitir serie", "Reordenar ejercicios"),
            godModeUndoStackAfterRevert(stack, 2).map { it.label },
        )
        assertEquals(stack, godModeUndoStackAfterRevert(stack, 9))
    }
}
