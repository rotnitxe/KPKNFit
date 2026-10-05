package com.example.kpkn.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // ─── H8 · «Configurar este plan» desde la biblioteca ─────────────────────────────────────────

    @Test
    fun libraryPlanRoute_completed_onboarding_opens_the_training_only_wizard_with_the_plan() {
        val route = PostDischargeRouting.libraryPlanRoute(onboardingCompleted = true, planId = "native:muscle-foundation-v2")

        // Con el alta hecha: asistente de SOLO entrenamiento, con el plan como intención.
        assertEquals(KpknRoute.SetupWizard.create(mode = "TRAINING_ONLY", planId = "native:muscle-foundation-v2"), route)
        assertTrue(route.startsWith(KpknRoute.SetupWizard.BASE_ROUTE))
        val uri = Uri.parse(route)
        assertEquals("TRAINING_ONLY", uri.getQueryParameter(KpknRoute.SetupWizard.ARG_MODE))
        assertEquals("native:muscle-foundation-v2", uri.getQueryParameter(KpknRoute.SetupWizard.ARG_PLAN_ID))
        // Sin borrador que reanudar: el asistente decide cuál usa.
        assertEquals("", uri.getQueryParameter(KpknRoute.SetupWizard.ARG_DRAFT_ID))
    }

    @Test
    fun libraryPlanRoute_incomplete_onboarding_keeps_the_full_wizard_with_the_plan() {
        val route = PostDischargeRouting.libraryPlanRoute(onboardingCompleted = false, planId = "protocol:texas-method-3d")

        assertEquals(KpknRoute.SetupWizard.create(mode = "FULL", planId = "protocol:texas-method-3d"), route)
        assertEquals(KpknRoute.SetupWizard.create(planId = "protocol:texas-method-3d"), route)
        val uri = Uri.parse(route)
        assertEquals("FULL", uri.getQueryParameter(KpknRoute.SetupWizard.ARG_MODE))
        assertEquals("protocol:texas-method-3d", uri.getQueryParameter(KpknRoute.SetupWizard.ARG_PLAN_ID))
    }

    @Test
    fun libraryPlanRoute_never_goes_through_the_direct_editor_and_does_not_change_createProgramRoute() {
        listOf(true, false).forEach { completed ->
            val route = PostDischargeRouting.libraryPlanRoute(completed, "original:phul-ms-2021-r1")
            // Es el asistente (evalúa material, días y tiempo), nunca el editor directo.
            assertTrue("completed=$completed: $route", route.startsWith(KpknRoute.SetupWizard.BASE_ROUTE))
            assertTrue(!route.startsWith(KpknRoute.ProgramEditor.BASE_ROUTE))
        }
        // Crear un programa en blanco sigue siendo el editor directo con el alta hecha (el guardia no se mueve).
        assertEquals(KpknRoute.ProgramEditor.create(), PostDischargeRouting.createProgramRoute(onboardingCompleted = true))
        // Y las dos entradas son distintas con el alta hecha: una lleva el plan, la otra no.
        assertNotEquals(
            PostDischargeRouting.createProgramRoute(onboardingCompleted = true),
            PostDischargeRouting.libraryPlanRoute(onboardingCompleted = true, planId = "native:muscle-foundation-v2"),
        )
    }

    @Test
    fun libraryPlanRoute_only_differs_between_completed_and_incomplete_in_the_wizard_mode() {
        val planId = "adapted:phat-kpkn-r1"
        val completed = PostDischargeRouting.libraryPlanRoute(onboardingCompleted = true, planId = planId)
        val incomplete = PostDischargeRouting.libraryPlanRoute(onboardingCompleted = false, planId = planId)

        assertEquals(
            "mismo plan y mismo borrador: solo cambia el modo",
            incomplete.replace("mode=FULL", "mode=TRAINING_ONLY"),
            completed,
        )
    }
}
