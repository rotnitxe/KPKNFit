package com.example.kpkn.screens.home

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba la derivación pura del overlay de bienvenida ([onboardingStateFrom]).
 * Cubre los 8 casos del reporte del Auditor (2026-08-18): gate anti-flash
 * (repository.isReady), onboarding completado, primer uso, dismiss de sesión,
 * plan activo, flags de tareas y username persistido.
 */
class OnboardingStateDerivationTest {

    @Test
    fun `ready=false oculta el overlay (anti-flash, usuarios completados)`() {
        val state = onboardingStateFrom(Settings(), null, ready = false, dismissed = false)
        assertFalse(state.show)
    }

    @Test
    fun `onboardingCompleted=true con ready=true oculta el overlay`() {
        val state = onboardingStateFrom(
            Settings(onboardingCompleted = true),
            null,
            ready = true,
            dismissed = false,
        )
        assertFalse(state.show)
    }

    @Test
    fun `primer uso con ready=true muestra el overlay con tareas pendientes y nombre por defecto`() {
        val state = onboardingStateFrom(Settings(), null, ready = true, dismissed = false)
        assertTrue(state.show)
        assertFalse(state.programDone)
        assertFalse(state.nutritionDone)
        assertEquals("Usuario", state.displayName)
    }

    @Test
    fun `dismissed=true oculta el overlay`() {
        val state = onboardingStateFrom(Settings(), null, ready = true, dismissed = true)
        assertFalse(state.show)
    }

    @Test
    fun `plan activo marca nutricion como hecha`() {
        val state = onboardingStateFrom(Settings(), "p1", ready = true, dismissed = false)
        assertTrue(state.nutritionDone)
    }

    @Test
    fun `flag onboardingNutritionDone marca nutricion como hecha`() {
        val state = onboardingStateFrom(
            Settings(onboardingNutritionDone = true),
            null,
            ready = true,
            dismissed = false,
        )
        assertTrue(state.nutritionDone)
    }

    @Test
    fun `programa hecho y plan activo completan todas las tareas`() {
        val state = onboardingStateFrom(
            Settings(onboardingProgramDone = true, onboardingNameDone = true),
            "p1",
            ready = true,
            dismissed = false,
        )
        assertTrue(state.allTasksDone)
    }

    @Test
    fun `username persistido se usa como displayName`() {
        val state = onboardingStateFrom(
            Settings(username = "Valen"),
            null,
            ready = true,
            dismissed = false,
        )
        assertEquals("Valen", state.displayName)
    }

    @Test
    fun `un plan con el flag isActive viejo pero sin id activo no se usa como plan activo`() {
        val stale = NutritionPlan(id = "p-viejo", name = "Viejo", isActive = true, direction = PlanDirection.DEFICIT)

        val state = onboardingStateFrom(
            Settings(),
            null,
            ready = true,
            dismissed = false,
            nutritionPlans = listOf(stale),
        )

        // Sin reserva por el flag del plan: solo manda el id del estado activo.
        assertNull(state.nutritionGoalLabel)
    }

    @Test
    fun `el plan activo es el del id aunque otro plan conserve isActive en true`() {
        val stale = NutritionPlan(id = "p-viejo", name = "Viejo", isActive = true, direction = PlanDirection.DEFICIT)
        val current = NutritionPlan(id = "p-nuevo", name = "Nuevo", isActive = false, direction = PlanDirection.MAINTENANCE)

        val state = onboardingStateFrom(
            Settings(),
            "p-nuevo",
            ready = true,
            dismissed = false,
            nutritionPlans = listOf(stale, current),
        )

        assertEquals("Mantenimiento", state.nutritionGoalLabel)
    }
}
