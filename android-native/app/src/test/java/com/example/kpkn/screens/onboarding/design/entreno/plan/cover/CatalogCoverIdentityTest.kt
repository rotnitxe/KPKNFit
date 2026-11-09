package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCoverIdentityTest {
    @Test
    fun catalogAndTemplateSurfacesResolveTheSameStableIdentityAsOnboardingAndPrograms() {
        val entries = PersonalizedPlanCatalog.listedEntries()
        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            val first = catalogCoverIdentityFor(entry)
            val second = catalogCoverIdentityFor(entry.copy())
            assertEquals("stable cover for ${entry.id}", first, second)
            assertNotNull("onboarding identity for ${entry.id}", assignedCoverIdentity(entry.id))
            assertEquals("activated program identity for ${entry.id}", coverIdentityFor(entry.id, null), first)
        }
    }
}
