package com.example.kpkn.debug

import android.app.Application
import com.example.kpkn.data.backup.AppBootstrap
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.CapabilityRequiredException
import com.example.kpkn.ui.tour.SettingsTourSeenStore
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Resultado de la siembra: lista para mostrar o un motivo por el que no se ha podido sembrar. */
sealed interface DemoSeedResult {
    data class Done(val summary: String) : DemoSeedResult
    data class Missing(val reason: String) : DemoSeedResult
}

/*
 * SOLO DEPURACIÓN. Escribe la demostración con los repositorios reales de la app. Requiere la persona QA ya activada
 * por el arnés del alta. No rehace el alta: solo añade entrenos, comidas, el check-in de hoy y el nombre.
 */
object DemoDataSeeder {

    suspend fun seed(app: Application): DemoSeedResult {
        // Igual que MainActivity: inicializa los repositorios (el proceso puede arrancar directamente aquí).
        try {
            AppBootstrap.prepare(app)
        } catch (error: Throwable) {
            return DemoSeedResult.Missing("El arranque de los datos falló (${error.javaClass.simpleName}). Revisa el log.")
        }
        if (AppBootstrap.state.value != AppBootstrap.State.Ready) {
            return DemoSeedResult.Missing("La app quedó en recuperación de datos; no se siembra nada.")
        }

        val programs = ProgramRepository.getInstance()
        val active = programs.activeProgramState.value
            ?: return DemoSeedResult.Missing("No hay programa activo. Antes lanza el arnés con persona=qa y activate=1500.")
        val program = programs.getProgramById(active.programId)
            ?: return DemoSeedResult.Missing("El programa activo no está en el repositorio.")
        val week = program.allWeeks().firstOrNull { it.id == active.currentWeekId && it.sessions.isNotEmpty() }
            ?: program.allWeeks().firstOrNull { it.sessions.isNotEmpty() }
            ?: return DemoSeedResult.Missing("El programa activo no tiene sesiones.")

        val today = LocalDate.now()
        val workouts = DemoDataPlan.workouts(today, week.id, week.sessions, active)
        workouts.forEach { programs.addWorkoutLog(it) }

        // Las comidas van por la vía con confirmación en disco; si la capacidad de nutrición no está, se omiten y se dice.
        val nutrition = NutritionRepository.getInstance()
        var mealsSaved = 0
        var mealsBlockedBy: String? = null
        for (meal in DemoDataPlan.meals(today)) {
            try {
                nutrition.saveNutritionLog(meal)
                mealsSaved++
            } catch (blocked: CapabilityRequiredException) {
                mealsBlockedBy = blocked.code
            }
        }

        programs.updateSettings {
            it.copy(username = DemoDataPlan.DISPLAY_NAME, onboardingNameDone = true, onboardingCompleted = true)
        }
        // Ningún recorrido debe tapar la grabación: todos quedan vistos en Settings.tourSeen.
        markToursSeen(SettingsTourSeenStore)

        // Los entrenos se escriben en Room en segundo plano: esperamos a que estén antes de devolver «hecho».
        if (!awaitWorkoutRows(app, DemoDataPlan.workoutIds())) {
            return DemoSeedResult.Missing("Room no confirmó los entrenos a tiempo. Relanza la siembra.")
        }

        val meals = mealsBlockedBy?.let { "comidas omitidas ($it)" } ?: "$mealsSaved/2 comidas"
        return DemoSeedResult.Done(
            "${workouts.size} entrenos de «${program.name}», $meals, check-in de hoy y nombre ${DemoDataPlan.DISPLAY_NAME}.",
        )
    }

    /** Da por vistos todos los recorridos (ids de `TourScripts.todos()`) para que ninguno tape la grabación. */
    fun markToursSeen(store: TourSeenStore) {
        TourScripts.todos().keys.forEach(store::markSeen)
    }

    private fun Program.allWeeks(): List<ProgramWeek> =
        macrocycles.flatMap { macro -> macro.blocks.flatMap { block -> block.mesocycles.flatMap { meso -> meso.weeks } } }

    private fun storedWorkoutIds(app: Application): Set<String> =
        KpknDatabase.getInstance(app).openHelper.readableDatabase.query("SELECT id FROM workout_logs").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private suspend fun awaitWorkoutRows(app: Application, ids: Set<String>): Boolean {
        val done = withTimeoutOrNull(15_000L) {
            while (!storedWorkoutIds(app).containsAll(ids)) delay(150L)
            true
        }
        return done == true
    }
}
