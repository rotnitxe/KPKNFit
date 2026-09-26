package com.example.kpkn.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PostDischargeRoutingTest {

    @Test
    fun createProgramRoute_completed_onboarding_opens_direct_editor() {
        val route = PostDischargeRouting.createProgramRoute(onboardingCompleted = true)

        assertEquals(KpknRoute.ProgramEditor.create(), route)
        assertTrue(route.startsWith(KpknRoute.ProgramEditor.BASE_ROUTE))
        // El editor directo NUNCA pasa por el wizard de módulo.
        assertTrue(!route.startsWith(KpknRoute.SetupWizard.BASE_ROUTE))
    }

    @Test
    fun createProgramRoute_incomplete_onboarding_keeps_full_wizard() {
        val route = PostDischargeRouting.createProgramRoute(onboardingCompleted = false)

        assertEquals(KpknRoute.SetupWizard.create(), route)
        assertEquals(KpknRoute.SetupWizard.create(mode = "FULL"), route)
        assertTrue(route.startsWith(KpknRoute.SetupWizard.BASE_ROUTE))
    }
}