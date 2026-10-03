package com.example.kpkn.screens.sessioneditor

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.PlanMaterializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AVD-only QA bridge between the editor Room commit and an actual recipe
 * rematerialization. The UI scenario must already be saved on the isolated
 * fixture. This deliberately changes only that fixture Program row so the
 * caller can export a post-rematerialization database snapshot.
 *
 * Invoke once per clean scenario with instrumentation argument:
 *   scenario=APPEND | REPLACE | CREATE
 */
@RunWith(AndroidJUnit4::class)
class SessionEditorTransferMaterializationAvdTest {
    @Test
    fun rematerializeTransferCommittedByEditorAndWriteForOfflineSnapshot(): Unit = runBlocking {
        val scenario = InstrumentationRegistry.getArguments()
            .getString(ARG_SCENARIO)
            ?.uppercase()
            ?: throw AssertionError("Pass instrumentation argument scenario=APPEND|REPLACE|CREATE.")

        assertTrue("Unsupported scenario: $scenario", scenario in SUPPORTED_SCENARIOS)
        val targetDay = if (scenario == SCENARIO_CREATE) TUESDAY else WEDNESDAY
        val expectedExerciseCount = when (scenario) {
            SCENARIO_APPEND -> 9
            SCENARIO_REPLACE, SCENARIO_CREATE -> 5
            else -> error("Unsupported scenario: $scenario")
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ProgramRepository.init(context)
        withTimeout(60_000L) { repository.isReady.first { it } }

        val roomBefore = repository.readPersistedProgramForEditorRecovery(PROGRAM_ID)
            ?: throw AssertionError("El fixture Program no existe en Room: $PROGRAM_ID")
        val roomBeforeLocation = locateWeek(roomBefore, WEEK_ID)
        val sourceRecipe = roomBefore.sourceRecipe
            ?: throw AssertionError("El fixture no contiene receta fuente para PlanMaterializer.")
        assertNotNull(
            "PlanMaterializer debe encontrar una receta efectiva para esta ocurrencia.",
            PlanMaterializer.weekRecipeSourceFor(roomBefore, sourceRecipe, WEEK_ID),
        )
        val roomBeforeTarget = roomBeforeLocation.week.sessions.singleOrNull { it.dayOfWeek == targetDay }
            ?: throw AssertionError("Se esperaba exactamente una sesión destino para el día $targetDay.")
        assertEquals("La transferencia guardada no tiene el conteo del escenario.", expectedExerciseCount, roomBeforeTarget.allExercises().size)
        assertEquals(
            "Los IDs de ejercicios del destino deben ser únicos.",
            roomBeforeTarget.allExercises().size,
            roomBeforeTarget.allExercises().map { it.id }.distinct().size,
        )

        if (scenario != SCENARIO_CREATE) {
            assertEquals("APPEND/REPLACE debe conservar el ID existente de Miércoles.", WEDNESDAY_SESSION_ID, roomBeforeTarget.id)
        } else {
            assertFalse(
                "CREATE debe producir un ID distinto de todos los destinos predefinidos.",
                roomBeforeTarget.id in setOf(MONDAY_SESSION_ID, WEDNESDAY_SESSION_ID, FRIDAY_SESSION_ID),
            )
        }

        val roomBeforeOverride = roomBefore.manualSessionOverrides.singleOrNull {
            it.sessionId == roomBeforeTarget.id &&
                it.weekId == WEEK_ID &&
                it.scope == ManualOverrideScope.SESSION
        } ?: throw AssertionError("Falta el override SESSION específico del destino.")
        val expectedOccurrence = PlanMaterializer.weekOccurrenceOf(roomBeforeLocation.week, roomBeforeLocation.indexWithinMeso)
        assertEquals("Override debe señalar la ocurrencia propia.", expectedOccurrence, roomBeforeOverride.weekOccurrence)
        val expectedRecipeDay = when (targetDay) {
            WEDNESDAY -> WEDNESDAY_RECIPE_DAY_ID
            else -> roomBefore.recipeDayIdForTarget(WEEK_ID, targetDay)
        }
        assertEquals("Override debe retener el recipe-day real del destino.", expectedRecipeDay, roomBeforeOverride.recipeDayId)

        val cachedBefore = repository.getProgramById(PROGRAM_ID)
            ?: throw AssertionError("El repositorio no hidrató el programa del fixture.")
        assertEquals("El cache del repositorio debe corresponder al snapshot leído de Room.", roomBefore, cachedBefore)

        val executionEvidence = repository.executedTrainingEvidence(roomBefore)
        assertFalse(
            "El fixture no puede marcar como ejecutada una semana que el test pretende reconstruir.",
            WEEK_ID in executionEvidence.weekIds,
        )
        val committed = repository.mutateProgramNow(PROGRAM_ID) { current ->
            // Do not use a stale cache to overwrite a different durable operation.
            assertEquals("El RMW debe empezar desde el snapshot del UI commit leído de Room.", roomBefore, current)
            val currentLocation = locateWeek(current, WEEK_ID)
            val currentTarget = currentLocation.week.sessions.singleOrNull { it.dayOfWeek == targetDay }
                ?: throw AssertionError("El destino desapareció antes de rematerializar.")
            assertEquals("El destino cambió antes del commit de rematerialización.", roomBeforeTarget, currentTarget)

            val recipe = current.sourceRecipe
                ?: throw AssertionError("El fixture no contiene receta fuente para PlanMaterializer.")
            val evidence = repository.executedTrainingEvidence(current)
            assertFalse("La semana pasó a ejecutada; no se reconstruye.", WEEK_ID in evidence.weekIds)
            PlanMaterializer.rematerializeWeek(
                program = current,
                weekId = WEEK_ID,
                recipe = recipe,
                weekOccurrence = PlanMaterializer.weekOccurrenceOf(currentLocation.week, currentLocation.indexWithinMeso),
                executedWeekIds = evidence.weekIds,
                executedSessionIds = evidence.sessionIds,
            ).also { rebuilt ->
                val rebuiltTarget = locateWeek(rebuilt, WEEK_ID).week.sessions.singleOrNull { it.dayOfWeek == targetDay }
                    ?: throw AssertionError("PlanMaterializer eliminó el destino editado.")
                assertEquals("SESSION override debe conservar el contenido exacto transferido.", roomBeforeTarget, rebuiltTarget)
                val rebuiltOverride = rebuilt.manualSessionOverrides.singleOrNull {
                    it.sessionId == rebuiltTarget.id &&
                        it.weekId == WEEK_ID &&
                        it.scope == ManualOverrideScope.SESSION
                } ?: throw AssertionError("PlanMaterializer perdió el override SESSION.")
                assertEquals(expectedOccurrence, rebuiltOverride.weekOccurrence)
                assertEquals(expectedRecipeDay, rebuiltOverride.recipeDayId)
            }
        }
        assertTrue("El commit durable del programa rematerializado fue rechazado.", committed)

        val roomAfter = repository.readPersistedProgramForEditorRecovery(PROGRAM_ID)
            ?: throw AssertionError("No se pudo releer desde Room el programa rematerializado.")
        val roomAfterTarget = locateWeek(roomAfter, WEEK_ID).week.sessions.singleOrNull { it.dayOfWeek == targetDay }
            ?: throw AssertionError("Falta destino al reabrir la fila Room.")
        assertEquals("El readback de Room debe conservar el payload de la sesión destino.", roomBeforeTarget, roomAfterTarget)
        val roomAfterOverride = roomAfter.manualSessionOverrides.singleOrNull {
            it.sessionId == roomAfterTarget.id && it.weekId == WEEK_ID && it.scope == ManualOverrideScope.SESSION
        } ?: throw AssertionError("Room perdió el override SESSION del destino.")
        assertEquals("Room conserva la ocurrencia de destino.", expectedOccurrence, roomAfterOverride.weekOccurrence)
        assertEquals("Room conserva el recipe-day del destino.", expectedRecipeDay, roomAfterOverride.recipeDayId)

        Log.i(
            TAG,
            "PASS scenario=$scenario program=$PROGRAM_ID week=$WEEK_ID targetDay=$targetDay " +
                "targetSession=${roomAfterTarget.id} exercises=${roomAfterTarget.allExercises().size} " +
                "weekOccurrence=$expectedOccurrence recipeDay=$expectedRecipeDay durableRoomReadback=true",
        )
    }

    private data class WeekLocation(
        val week: ProgramWeek,
        val indexWithinMeso: Int,
    )

    private fun locateWeek(program: Program, weekId: String): WeekLocation {
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    val index = meso.weeks.indexOfFirst { it.id == weekId }
                    if (index >= 0) return WeekLocation(meso.weeks[index], index)
                }
            }
        }
        throw AssertionError("No se encontró la semana del fixture: $weekId")
    }

    private companion object {
        const val ARG_SCENARIO = "scenario"
        const val SCENARIO_APPEND = "APPEND"
        const val SCENARIO_REPLACE = "REPLACE"
        const val SCENARIO_CREATE = "CREATE"
        const val TUESDAY = 2
        const val WEDNESDAY = 3
        const val PROGRAM_ID = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f"
        const val WEEK_ID = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f-id--2147483646"
        const val MONDAY_SESSION_ID = "rs_37de122c4df863f705db0aaa1116dda6"
        const val WEDNESDAY_SESSION_ID = "rs_c81a24262257d76de6008759667e4ee6"
        const val FRIDAY_SESSION_ID = "rs_bab1ce5c683be30dae6f8df0cd87e347"
        const val WEDNESDAY_RECIPE_DAY_ID = "d2-p3b"
        const val TAG = "EditorTransferMaterializeQA"
        val SUPPORTED_SCENARIOS = setOf(SCENARIO_APPEND, SCENARIO_REPLACE, SCENARIO_CREATE)
    }
}
