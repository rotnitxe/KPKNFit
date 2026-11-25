package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.LimitingFactorCategory
import com.example.kpkn.domain.exercises.catalogv2.AprendeOntology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitingFactorCatalogTest {

    @Test
    fun `grip limiting factor maps to movement work and links forearm muscles`() {
        val report = LimitingFactorCatalog.resolveReport("grip", primaryMuscles = listOf("latissimus_dorsi"))
        assertEquals(LimitingFactorCategory.MOVEMENT_WORK, report.category)
        assertTrue("forearm" in report.linkedMuscleIds)
        assertFalse(report.autoLinkedCore)
    }

    @Test
    fun `back limiting factor on non-back-primary exercise automatically includes core`() {
        val onSquat = LimitingFactorCatalog.resolveReport("espalda", primaryMuscles = listOf("quadriceps", "gluteus_maximus"))
        assertTrue("core" in onSquat.linkedMuscleIds)
        assertTrue(onSquat.autoLinkedCore)

        val lumbarOnLegPress = LimitingFactorCatalog.resolveReport("columna-lumbar", primaryMuscles = listOf("quadriceps"))
        assertTrue("core" in lumbarOnLegPress.linkedMuscleIds)
        assertTrue(lumbarOnLegPress.autoLinkedCore)

        val onPulldown = LimitingFactorCatalog.resolveReport("espalda", primaryMuscles = listOf("latissimus_dorsi"))
        assertFalse(onPulldown.autoLinkedCore)
    }

    @Test
    fun `cardio and ontology joints are supported cleanly`() {
        val cardio = LimitingFactorCatalog.resolveReport("cardio")
        assertEquals(LimitingFactorCategory.CARDIO, cardio.category)

        val knee = LimitingFactorCatalog.resolveReport("rodilla")
        assertEquals(LimitingFactorCategory.JOINT, knee.category)
        assertTrue("rodilla" in AprendeOntology.wikiLabJointIds)
        assertEquals(listOf("rodilla"), knee.linkedJointIds)
    }
}
