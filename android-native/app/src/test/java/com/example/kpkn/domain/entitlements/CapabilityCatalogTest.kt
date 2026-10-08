package com.example.kpkn.domain.entitlements

import org.junit.Assert.*
import org.junit.Test

class CapabilityCatalogTest {
    @Test fun freeMatchesTheApprovedCommercialPolicy() {
        assertTrue(CapabilityCatalog.permits(Capability.VOICE, PlanLevel.FREE))
        assertTrue(CapabilityCatalog.permits(Capability.DATA_SYNC, PlanLevel.FREE))
        assertTrue(CapabilityCatalog.permits(Capability.ENERGY_RING, PlanLevel.FREE))
        assertFalse(CapabilityCatalog.permits(Capability.MUSCLE_RING, PlanLevel.FREE))
        assertFalse(CapabilityCatalog.permits(Capability.SPINAL_RING, PlanLevel.FREE))
        assertFalse(CapabilityCatalog.permits(Capability.NUTRITION, PlanLevel.FREE))
        assertNull(CapabilityCatalog.validateProgramStructure(PlanLevel.FREE, 1, listOf(5)))
        assertEquals("free_weekly_session_limit", CapabilityCatalog.validateProgramStructure(PlanLevel.FREE, 1, listOf(6)))
        assertEquals("advanced_program_requires_pro", CapabilityCatalog.validateProgramStructure(PlanLevel.FREE, 2, listOf(3, 3)))
    }
    @Test fun expiryCannotRemoveAccessToOwnedData() {
        listOf(Capability.READ_HISTORY, Capability.EXPORT_DATA, Capability.DELETE_DATA).forEach {
            assertTrue(CapabilityCatalog.permits(it, PlanLevel.FREE))
        }
    }
}
