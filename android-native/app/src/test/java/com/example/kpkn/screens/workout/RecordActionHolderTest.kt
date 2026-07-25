package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordActionHolderTest {
    @Test
    fun isArmedTracksActionAssignment() {
        val holder = RecordActionHolder()
        assertFalse(holder.isArmed)

        holder.action = {}
        assertTrue(holder.isArmed)

        holder.action = null
        assertFalse(holder.isArmed)
    }
}
