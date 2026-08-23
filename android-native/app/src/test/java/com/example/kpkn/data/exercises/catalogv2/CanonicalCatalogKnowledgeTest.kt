package com.example.kpkn.data.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalCatalogKnowledgeTest {
    @Test
    fun explicitCatalogMappingsExposeOnlyCanonicalIntro() {
        val muscle = canonicalMuscleKnowledge("deltoid")
        val joint = canonicalJointKnowledge("codo")
        val pattern = canonicalPatternKnowledge("horizontal_push")

        assertNotNull(muscle)
        assertEquals("Deltoides", muscle?.name)
        assertNotNull(joint)
        assertEquals("Articulación del Codo", joint?.name)
        assertNotNull(pattern)
        assertEquals("Empuje Horizontal", pattern?.name)
        assertNotNull(muscle?.description)
    }

    @Test
    fun ambiguousOrUnknownIdsFailClosed() {
        assertNull(canonicalMuscleKnowledge("Deltoides"))
        assertNull(canonicalJointKnowledge("shoulder-ish"))
        assertNull(canonicalPatternKnowledge("press-con-este-nombre"))
    }
}
