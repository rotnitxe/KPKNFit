package com.example.kpkn.data.db

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C5: el plan activo vivía en tres sitios (columna `isActive`, JSON del plan y
 * `nutrition_active_state`). `deactivateAllPlans` solo cambia la columna, así que
 * el JSON de un plan ya desactivado conserva `"isActive":true`. La columna es la
 * única verdad: al decodificar, todos los lectores reciben el valor de la columna.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionPlanActiveStateTest {
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun plan(id: String) = NutritionPlan(id = id, name = "Plan $id", calorieTarget = 2000, isActive = true)

    @Test
    fun activatingASecondPlanLeavesOnlyThatPlanActiveAfterReload() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        coordinator.commit(SetupCommitRequest("commit-a", null, Settings(), null, plan("plan-a"), false, true))
        coordinator.commit(SetupCommitRequest("commit-b", null, Settings(), null, plan("plan-b"), false, true))

        val rows = db.nutritionDao().getAllPlans().associateBy { it.id }
        // Causa raíz: la columna de A ya es 0, pero su JSON sigue diciendo «activo».
        assertFalse(rows.getValue("plan-a").isActive)
        assertTrue(
            "el JSON viejo de A conserva isActive=true (la causa del defecto)",
            dbJson.decodeFromString<NutritionPlan>(rows.getValue("plan-a").data).isActive,
        )

        // «Recargar»: todos los lectores del repositorio pasan por toNutritionPlan().
        val reloaded = db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
        assertEquals(listOf("plan-b"), reloaded.filter { it.isActive }.map { it.id })
        assertEquals("plan-b", db.nutritionDao().getActiveState()?.activePlanId)
    }

    @Test
    fun decodingTakesIsActiveFromTheColumnInBothDirections() {
        val activeJson = dbJson.encodeToString(NutritionPlan(id = "x", name = "X", isActive = true))
        val inactiveJson = dbJson.encodeToString(NutritionPlan(id = "x", name = "X", isActive = false))

        // Columna 0 con JSON «activo» (el caso real tras desactivar): inactivo.
        assertFalse(NutritionPlanEntity("x", "X", isActive = false, data = activeJson).toNutritionPlan().isActive)
        // Columna 1 con JSON «inactivo»: activo. La columna manda siempre.
        assertTrue(NutritionPlanEntity("x", "X", isActive = true, data = inactiveJson).toNutritionPlan().isActive)
    }

    @Test
    fun decodingKeepsEveryOtherPlanField() {
        val original = NutritionPlan(
            id = "full",
            name = "Completo",
            calorieTarget = 2350,
            proteinGoal = 170,
            carbGoal = 240,
            fatGoal = 65,
            isActive = true,
        )
        val decoded = NutritionPlanEntity("full", "Completo", isActive = false, data = dbJson.encodeToString(original))
            .toNutritionPlan()

        assertEquals(original.copy(isActive = false), decoded)
    }

    @Test
    fun trackingOnlyDeactivationDecodesEveryPlanAsInactive() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        coordinator.commit(SetupCommitRequest("commit-a", null, Settings(), null, plan("plan-a"), false, true))
        coordinator.commit(
            SetupCommitRequest(
                commitId = "commit-tracking",
                draftId = null,
                settings = Settings(),
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                nutritionTrackingOnly = true,
            ),
        )

        val reloaded = db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
        assertEquals(listOf("plan-a"), reloaded.map { it.id })
        assertTrue("ningún plan queda activo en solo registro", reloaded.none { it.isActive })
    }
}
