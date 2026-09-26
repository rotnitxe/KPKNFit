package com.example.kpkn.data.repository

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.decodeWeeklyForecastDocument
import com.example.kpkn.domain.nutrition.encodeWeeklyForecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Coordinador de previsión del calendario: un cambio de PROGRAMA reescribe la
 * previsión del plan activo SIN abrir el editor, con UNA sola escritura por
 * cambio real (las propias escrituras no se retroalimentan) y con reloj
 * inyectable.
 */
class NutritionCalendarForecastCoordinatorTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)
    private val thursday: LocalDate = monday.plusDays(3)

    private val priorTargets = listOf(
        NutritionEditorDayTarget(monday, 2_100, 160, 240, 65),
        NutritionEditorDayTarget(monday.plusDays(1), 1_900, 150, 220, 60),
        NutritionEditorDayTarget(monday.plusDays(2), 2_000, 155, 230, 62),
        NutritionEditorDayTarget(thursday, 2_300, 170, 260, 70),
        NutritionEditorDayTarget(monday.plusDays(4), 2_600, 175, 300, 72),
        NutritionEditorDayTarget(monday.plusDays(5), 2_400, 172, 280, 70),
        NutritionEditorDayTarget(monday.plusDays(6), 1_800, 150, 200, 55),
    )

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private fun fridaySession(weight: Double): Session {
        val set = ExerciseSet(id = "fri_s", weight = weight, targetReps = 8, targetRPE = 8.0)
        return Session(
            id = "fri",
            name = "Viernes",
            dayOfWeek = 5,
            exercises = listOf(
                Exercise(id = "fri_e", name = "Press banca", restTime = 90, sets = listOf(set)),
            ),
        )
    }

    /** Sesión OPCIONAL del sábado (futura): sólo cuenta si se confirma. */
    private fun optionalSaturdaySession(): Session {
        val set = ExerciseSet(id = "opt_sat_s", weight = 100.0, targetReps = 8, targetRPE = 8.0)
        return Session(
            id = "opt-sat",
            name = "Opcional sábado",
            dayOfWeek = 6,
            requirement = SessionRequirement.OPTIONAL,
            exercises = listOf(
                Exercise(id = "opt_sat_e", name = "Press banca", restTime = 90, sets = listOf(set)),
            ),
        )
    }

    /** Programa calendarizado (ancla 2026-09-21) con sesión el viernes y una
     *  opcional el sábado (sin confirmar por defecto: no aporta gasto). */
    private fun program(weight: Double): Program =
        Program(
            id = "prog-cal",
            name = "Calendarizado",
            structure = ProgramStructure.COMPLEX,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-09-21",
                weekStartDay = 1,
                trainingDays = setOf(1, 3, 5),
                mode = ScheduleMode.DATED,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "mac1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    goal = MesocycleGoal.ACCUMULATION,
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(fridaySession(weight), optionalSaturdaySession()),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun planWithForecast(): NutritionPlan = NutritionPlan(
        id = "plan-1",
        name = "Plan",
        calorieTarget = 2_000,
        proteinGoal = 150,
        carbGoal = 250,
        fatGoal = 60,
        calculationOrigin = CalculationOrigin.PLAN,
        calculationSnapshot = NutritionPlanCalculationSnapshot(
            engineVersion = "eer-2023-v1",
            formula = "test",
            calculatedAt = "2026-09-21T00:00:00Z",
            inputs = mapOf(
                WEEKLY_FORECAST_KEY to encodeWeeklyForecast(
                    targets = priorTargets,
                    revision = 1,
                    effectiveDate = monday,
                ),
            ),
        ),
    )

    /**
     * Fachaca en memoria con el MISMO contrato que la real: solo aplica si el
     * plan fuente sigue intacto y publica el plan revisado.
     */
    private class FakeStore(initial: NutritionPlan?) : NutritionForecastStore {
        override val activePlanId = MutableStateFlow(initial?.id)
        override val plans = MutableStateFlow(listOfNotNull(initial))
        override val snapshots = MutableStateFlow(emptyList<DailyGoalSnapshot>())
        var writes = 0
            private set

        override suspend fun storeForecastRevision(source: NutritionPlan, revised: NutritionPlan): Boolean {
            if (plans.value.firstOrNull { it.id == source.id } != source) return false
            writes += 1
            plans.value = plans.value.map { if (it.id == revised.id) revised else it }
            return true
        }
    }

    private fun coordinator(
        store: FakeStore,
        programs: MutableStateFlow<List<Program>>,
        scope: CoroutineScope,
        activeProgramId: MutableStateFlow<String?> = MutableStateFlow("prog-cal"),
        settings: MutableStateFlow<Settings> = MutableStateFlow(Settings(userVitals = UserVitals(weight = 80.0))),
        dayTick: MutableStateFlow<LocalDate> = MutableStateFlow(thursday),
    ): NutritionCalendarForecastCoordinator =
        NutritionCalendarForecastCoordinator(
            store = store,
            activeProgramId = activeProgramId,
            programs = programs,
            settings = settings,
            scope = scope,
            clock = { thursday },
            dayTick = dayTick,
        )

    private suspend fun awaitWrites(store: FakeStore, expected: Int) {
        withTimeout(5_000L) {
            while (store.writes < expected) delay(10L)
        }
    }

    private fun forecastPayloadOf(store: FakeStore): String =
        store.plans.value.first().calculationSnapshot!!.inputs.getValue(WEEKLY_FORECAST_KEY)

    // ─── Programa editado → reforecast persistido SIN abrir el editor ───────

    @Test
    fun `program edit persists a reforecast without opening the editor`() = runBlocking {
        val store = FakeStore(planWithForecast())
        val programs = MutableStateFlow(listOf(program(weight = 100.0)))
        val coordinator = coordinator(store, programs, scope = this)

        // Primera pasada: la previsión se alinea con el gasto real del programa
        // y queda escrita.
        assertTrue(coordinator.reforecastNow())
        assertEquals(1, store.writes)
        val first = decodeWeeklyForecastDocument(forecastPayloadOf(store))!!

        // Sin cambios reales no hay NUEVA escritura (ni bucle consigo mismo).
        assertFalse(coordinator.reforecastNow())
        assertEquals(1, store.writes)

        // Cambio real de prescripción (más carga el viernes): se re-reparte la
        // parte futura del periodo y se persiste la revisión siguiente.
        programs.value = listOf(program(weight = 140.0))
        assertTrue(coordinator.reforecastNow())
        assertEquals(2, store.writes)

        val second = decodeWeeklyForecastDocument(forecastPayloadOf(store))!!
        assertEquals(first.revision + 1, second.revision)
        // Hoy/pasado siguen intactos; solo se movió el futuro.
        val firstByDate = first.days.associateBy { it.date }
        val secondByDate = second.days.associateBy { it.date }
        for (date in listOf(monday, monday.plusDays(1), monday.plusDays(2), thursday)) {
            assertEquals(firstByDate.getValue(date), secondByDate.getValue(date))
        }
        assertTrue(secondByDate.getValue(monday.plusDays(4)) != firstByDate.getValue(monday.plusDays(4)))
        // Presupuesto de la semana intacto: 7 · 2000.
        assertEquals(14_000, second.days.sumOf { it.calorieTargetKcal })
    }

    @Test
    fun `collection reacts once to a program edit and never loops on its own writes`() = runBlocking {
        val store = FakeStore(planWithForecast())
        val programs = MutableStateFlow(listOf(program(weight = 100.0)))
        val scope = CoroutineScope(Dispatchers.Default)
        val coordinator = coordinator(store, programs, scope = scope)
        try {
            coordinator.start()
            awaitWrites(store, 1)

            programs.value = listOf(program(weight = 140.0))
            awaitWrites(store, 2)

            // Las propias escrituras reactivan la colección, pero la firma no
            // cambia: no debe haber una tercera escritura.
            delay(300L)
            assertEquals(2, store.writes)
        } finally {
            coordinator.stop()
            scope.cancel()
        }
    }

    @Test
    fun `switching to another plan with the same calendar and day reforecasts it`() = runBlocking {
        val store = FakeStore(planWithForecast())
        val programs = MutableStateFlow(listOf(program(weight = 100.0)))
        val coordinator = coordinator(store, programs, scope = this)

        assertTrue(coordinator.reforecastNow())
        assertEquals(1, store.writes)

        // Otro plan con el MISMO programa y el MISMO día: la firma incluye los
        // insumos reales del plan (id, base y modo), así que deja de estar
        // bloqueada por la firma anterior y se re-reparte su presupuesto.
        val other = planWithForecast().copy(
            id = "plan-2",
            calorieTarget = 1_800,
            proteinGoal = 140,
            carbGoal = 220,
            fatGoal = 55,
        )
        store.plans.value = listOf(other)
        store.activePlanId.value = "plan-2"

        assertTrue(coordinator.reforecastNow())
        assertEquals(2, store.writes)

        val document = decodeWeeklyForecastDocument(
            store.plans.value.first().calculationSnapshot!!.inputs.getValue(WEEKLY_FORECAST_KEY),
        )!!
        assertEquals(7, document.days.size)
        // Presupuesto del plan NUEVO: 7 · 1800 (los días fijos conservan su
        // evidencia y el futuro se reparte sobre su base).
        assertEquals(7 * 1_800, document.days.sumOf { it.calorieTargetKcal })
        // Revisión propia del plan 2 (bump sobre la que traía).
        assertEquals(2, document.revision)
    }

    @Test
    fun `confirming a future optional reforecasts from tomorrow keeping today and the exact budget`() = runBlocking {
        val store = FakeStore(planWithForecast())
        val programs = MutableStateFlow(listOf(program(100.0)))
        val coordinator = coordinator(store, programs, scope = this)

        assertTrue(coordinator.reforecastNow())
        assertEquals(1, store.writes)
        val before = decodeWeeklyForecastDocument(forecastPayloadOf(store))!!
        val beforeByDate = before.days.associateBy { it.date }

        // Confirmación manual del calendario escrita en el programa (sábado futuro).
        val saturday = monday.plusDays(5)
        programs.value = listOf(
            program(100.0).copy(
                optionalSessionConfirmations = listOf(
                    OptionalSessionConfirmation(dayIso = saturday.toString(), sessionId = "opt-sat"),
                ),
            ),
        )
        assertTrue("La confirmación reactiva el reforecast vía `programs`", coordinator.reforecastNow())
        assertEquals(2, store.writes)
        val confirmed = decodeWeeklyForecastDocument(forecastPayloadOf(store))!!
        val confirmedByDate = confirmed.days.associateBy { it.date }

        // HOY y pasados INMUTABLES (kcal y macros)…
        for (date in listOf(monday, monday.plusDays(1), monday.plusDays(2), thursday)) {
            assertEquals(beforeByDate.getValue(date), confirmedByDate.getValue(date))
        }
        // …el sábado deja de ser descanso y cambia su objetivo…
        assertTrue(
            beforeByDate.getValue(saturday).calorieTargetKcal !=
                confirmedByDate.getValue(saturday).calorieTargetKcal,
        )
        // …presupuesto del periodo EXACTO y revisión avanzada.
        assertEquals(14_000, confirmed.days.sumOf { it.calorieTargetKcal })
        assertEquals(before.revision + 1, confirmed.revision)

        // Quitar la confirmación: sólo esa entrada desaparece y se re-reparte.
        programs.value = listOf(program(100.0))
        assertTrue(coordinator.reforecastNow())
        assertEquals(3, store.writes)
        val undone = decodeWeeklyForecastDocument(forecastPayloadOf(store))!!
        assertEquals(14_000, undone.days.sumOf { it.calorieTargetKcal })
        val undoneByDate = undone.days.associateBy { it.date }
        for (date in listOf(monday, monday.plusDays(1), monday.plusDays(2), thursday)) {
            assertEquals(beforeByDate.getValue(date), undoneByDate.getValue(date))
        }
        // La sesión del viernes (no tocada por la confirmación) sigue presupuestada.
        assertTrue(undoneByDate.getValue(monday.plusDays(4)).calorieTargetKcal > 0)
    }

    @Test
    fun `without an active plan nothing is written`() = runBlocking {
        val store = FakeStore(null)
        val programs = MutableStateFlow(listOf(program(weight = 100.0)))
        val coordinator = coordinator(store, programs, scope = this)
        assertFalse(coordinator.reforecastNow())
        assertEquals(0, store.writes)
    }
}
