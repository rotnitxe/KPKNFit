package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.training.NativeProgressionTestSupport.completedLog
import com.example.kpkn.domain.training.NativeProgressionTestSupport.managedOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.sessionOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.weeksOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.4 / §12.1 / §14.4 sobre planes propios REALES (`SimpleCyclePersonalizer` +
 * `PlanMaterializer`), sin marcadores sembrados a mano: primera carga manual (F-01),
 * ciclo 2 con ids de sesión reutilizados y arrastre idempotente (F-02), y convención
 * de carga derivada del catálogo (F-03).
 */
class NativeProgressionRealPlanTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private val BARBELL = setOf("bodyweight", "barbell", "rack", "bench")
        private val DUMBBELLS = setOf("bodyweight", "dumbbells")

        /** Discos que permiten formar 62,5 kg desde 60 kg con la barra de 20 kg (21,25 kg por lado). */
        private val FULL_PLATES = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(
                PlateStock(25.0, 2),
                PlateStock(10.0, 2),
                PlateStock(5.0, 2),
                PlateStock(2.5, 2),
                PlateStock(1.25, 2),
            ),
        )
    }

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
    )

    private fun generate(
        programId: String,
        entryId: String,
        days: Int,
        equipment: Set<String>,
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
    ): Program {
        val result = personalizer().personalize(
            programId,
            PersonalizerInput(
                catalogEntryId = entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                equipment = equipment,
                level = level,
                availableMinutes = 100,
                cardio = null,
                volumeRecommendations = emptyList(),
            ),
        )
        return requireNotNull(result.program) {
            "el plan propio no se generó: ${result.report.limitations} (${result.report.reasonCode})"
        }
    }

    /** Fuerza KPKN de un día: seis sesiones (una por semana), S/B/D en F y remo en H, todo con barra. */
    private fun strengthPlan(programId: String = "real-strength"): Program =
        generate(programId, NativeProfileKind.STRENGTH.entryId, days = 1, equipment = BARBELL)

    private fun observe(
        program: Program,
        logs: List<WorkoutLog>,
        completedLogId: String? = null,
        inventory: EquipmentInventory? = null,
    ): Program = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
        program = program,
        logs = logs,
        inventory = inventory,
        curatedConfigurations = emptySet(),
        completedLogId = completedLogId,
        nowMs = 1_000L,
    )

    /** Juega el ciclo 1 completo (semana a semana, como `finalizeWorkout`) y devuelve programa y logs. */
    private fun playCycleOne(start: Program, loadByWeek: (Int) -> Double): Pair<Program, List<WorkoutLog>> {
        var program = start
        val logs = mutableListOf<WorkoutLog>()
        (1..6).forEach { week ->
            val log = completedLog(
                program = program,
                sessionId = sessionOf(program, week).id,
                logId = "real-c1-$week",
                dayOffset = week,
                loadKg = { loadByWeek(week) },
            )
            logs += log
            program = observe(program, logs.toList(), completedLogId = log.id)
        }
        return program to logs
    }

    private fun equipmentOf(exercise: Exercise): String? =
        exercise.catalogConfigurationId?.let { CatalogCompositionTestSupport.metadata.metadata(it)?.equipmentId }

    // ─── F-01 ───────────────────────────────────────────────────────────────

    @Test
    fun freshGeneratedOwnPlan_manualFirstLoad_isCapturedForFutureSessions() {
        val program = strengthPlan()
        assertEquals("seis semanas, una sesión por semana", 6, weeksOf(program).size)
        assertTrue(weeksOf(program).all { it.sessions.size == 1 })
        val week1 = managedOf(program, sessionOf(program, 1).id)
        assertTrue("el plan Fuerza trae slots F/H gestionados", week1.size >= 2)
        // Plan recién generado: ningún peso resuelto ni marcador de carga manual previo.
        weeksOf(program).flatMap { it.sessions }.forEach { session ->
            managedOf(program, session.id).forEach { exercise ->
                assertTrue(exercise.sets.all { it.weight == null && it.manualLoadRequiredSides.isEmpty() })
            }
        }

        val first = completedLog(program, sessionOf(program, 1).id, "fresh-1", dayOffset = 1, loadKg = { 60.0 })
        val observed = observe(program, listOf(first), completedLogId = first.id)

        (2..6).forEach { week ->
            val managed = managedOf(observed, sessionOf(observed, week).id)
            assertEquals(week1.size, managed.size)
            managed.forEach { exercise ->
                assertTrue("semana $week ${exercise.name}: la carga elegida a mano se mantiene", exercise.sets.all { it.weight == 60.0 })
                assertTrue(exercise.sets.all { it.manualLoadRequiredSides.isEmpty() })
                assertEquals(PlanLoadReferenceState.CAPTURED, exercise.loadReference?.state)
                assertEquals(60.0, exercise.loadReference?.capturedLoadKg ?: Double.NaN, 0.0001)
            }
        }
        managedOf(observed, sessionOf(observed, 1).id).forEach { exercise ->
            assertTrue("la sesión entrenada no se reescribe", exercise.sets.all { it.weight == null })
        }
        val pooled = observed.exerciseLoadReferences.map { it.exerciseId }.toSet()
        (2..6).forEach { week ->
            managedOf(observed, sessionOf(observed, week).id).forEach { assertTrue(it.id in pooled) }
        }

        // Una carga ya resuelta NO se pisa por una elección posterior distinta: mantener carga.
        val second = completedLog(observed, sessionOf(observed, 2).id, "fresh-2", dayOffset = 2, loadKg = { 70.0 })
        val afterSecond = observe(observed, listOf(first, second), completedLogId = second.id)
        (3..6).forEach { week ->
            managedOf(afterSecond, sessionOf(afterSecond, week).id).forEach { exercise ->
                assertTrue(exercise.sets.all { it.weight == 60.0 })
            }
        }
        assertTrue("60 y 70 kg no son evidencia comparable", afterSecond.nativeProgressionProposals.isEmpty())
    }

    // ─── F-02 ───────────────────────────────────────────────────────────────

    @Test
    fun cycleTwo_exposures_applyToCycleTwoSessions_andPendingProposalsExpireAtCycleClose() {
        val (cycleOne, logs) = playCycleOne(strengthPlan(), loadByWeek = { week -> if (week == 6) 50.0 else 60.0 })
        val managedCount = managedOf(cycleOne, sessionOf(cycleOne, 1).id).size
        assertTrue(managedCount >= 2)
        assertEquals("una propuesta pendiente por identidad al final del ciclo 1", managedCount, cycleOne.nativeProgressionProposals.size)
        assertTrue(cycleOne.nativeProgressionProposals.all { it.kind == NativeProgressionProposalKind.INCREASE_LOAD })

        // Cierre del ciclo 1: las pendientes caducan con motivo y las últimas referencias pasan al ciclo 2.
        val closed = ProgramProgressEngine.completeCycle(cycleOne, null, 1, logs)
        assertTrue(closed.advancedCycle)
        val cycleTwo = closed.program
        assertEquals(2, cycleTwo.runState?.cycleNumber)
        assertTrue("nada queda pendiente en silencio", cycleTwo.nativeProgressionProposals.isEmpty())
        val expired = cycleTwo.nativeProgressionAudit.filter { it.status == NativeProgressionResolutionStatus.EXPIRED }
        assertEquals(managedCount, expired.size)
        assertTrue(expired.all { it.reason.contains("Caducada al cerrar el ciclo 1") })
        (1..6).forEach { week ->
            managedOf(cycleTwo, sessionOf(cycleTwo, week).id).forEach { exercise ->
                assertTrue(
                    "semana $week ${exercise.name}: conserva la última referencia de trabajo (60 kg), no la de descarga (50 kg)",
                    exercise.sets.all { it.weight == 60.0 },
                )
            }
        }

        // Ciclo 2: las mismas sesiones vuelven a ser elegibles. El log lleva cycleNumber = 1 como en producción.
        val cycleTwoFirst = completedLog(
            program = cycleTwo,
            sessionId = sessionOf(cycleTwo, 1).id,
            logId = "real-c2-11",
            dayOffset = 21,
            cycle = 2,
            loadKg = { 60.0 },
        )
        assertEquals(2, NativeWorkoutProgressionRuntime.logCycle(cycleTwoFirst))
        val allLogs = logs + cycleTwoFirst
        val proposed = observe(cycleTwo, allLogs, completedLogId = cycleTwoFirst.id, inventory = FULL_PLATES)

        assertEquals("el ciclo 2 produce propuestas aplicables", managedCount, proposed.nativeProgressionProposals.size)
        assertTrue(proposed.nativeProgressionProposals.all { it.kind == NativeProgressionProposalKind.INCREASE_LOAD })
        assertTrue("60 kg + el menor paso formable con los discos declarados", proposed.nativeProgressionProposals.all { it.targetLoadKg == 62.5 })
        assertTrue(
            "ninguna propuesta del ciclo 2 se convierte en aviso «sin prescripción futura»",
            proposed.nativeProgressionAudit.none { it.reason == NativeProgressionText.noFutureSessionNotice() },
        )

        var accepted = proposed
        proposed.nativeProgressionProposals.forEach { proposal ->
            accepted = NativeWorkoutProgressionRuntime.resolveProposal(
                program = accepted,
                proposalId = proposal.proposalId,
                accept = true,
                logs = allLogs,
                ongoingSessionIds = emptySet(),
                curatedExercises = emptyMap(),
                nowMs = 2_000L,
            )
        }
        assertTrue(accepted.nativeProgressionProposals.isEmpty())
        assertEquals(
            managedCount,
            accepted.nativeProgressionAudit.count { it.status == NativeProgressionResolutionStatus.APPLIED },
        )
        managedOf(accepted, sessionOf(accepted, 1).id).forEach { exercise ->
            assertTrue("la sesión ya entrenada en el ciclo 2 conserva su carga", exercise.sets.all { it.weight == 60.0 })
        }
        (2..5).forEach { week ->
            managedOf(accepted, sessionOf(accepted, week).id).forEach { exercise ->
                assertTrue(
                    "semana $week ${exercise.name}: la propuesta llega a las sesiones sin entrenar del ciclo 2",
                    exercise.sets.all { it.weight == 62.5 },
                )
            }
        }
        // H-DESCARGA: la semana 6 es de descarga; la subida aceptada nunca cae ahí.
        managedOf(accepted, sessionOf(accepted, 6).id).forEach { exercise ->
            assertTrue(
                "semana 6 ${exercise.name}: la descarga conserva su carga de referencia",
                exercise.sets.all { it.weight == 60.0 },
            )
        }
    }

    @Test
    fun carryForward_isIdempotent_andKeepsLastReferences() {
        // La subida voluntaria de la semana 3 (62,5 kg) es la última referencia; la descarga (50 kg) no cuenta.
        val (cycleOne, logs) = playCycleOne(
            strengthPlan("real-carry"),
            loadByWeek = { week ->
                when (week) {
                    1, 2 -> 60.0
                    6 -> 50.0
                    else -> 62.5
                }
            },
        )

        val once = NativeWorkoutProgressionRuntime.carryForwardToNextCycle(cycleOne, logs, closedCycle = 1, nowMs = 99L)
        val twice = NativeWorkoutProgressionRuntime.carryForwardToNextCycle(once, logs, closedCycle = 1, nowMs = 123L)
        assertEquals("repetir el arrastre no cambia nada", once, twice)
        assertTrue(once.nativeProgressionProposals.isEmpty())

        val pool = once.exerciseLoadReferences.associateBy { it.exerciseId }
        (1..6).forEach { week ->
            managedOf(once, sessionOf(once, week).id).forEach { exercise ->
                assertTrue("semana $week ${exercise.name}", exercise.sets.all { it.weight == 62.5 && it.manualLoadRequiredSides.isEmpty() })
                assertEquals(PlanLoadReferenceState.CAPTURED, exercise.loadReference?.state)
                val reference = pool.getValue(exercise.id).references.single()
                assertEquals(PlanLoadReferenceState.CAPTURED, reference.state)
                assertEquals(62.5, reference.capturedLoadKg ?: Double.NaN, 0.0001)
            }
        }
        // Los logs son inmutables y los ids de sesión/ejercicio no cambian.
        assertEquals(
            weeksOf(cycleOne).flatMap { it.sessions }.map { it.id },
            weeksOf(once).flatMap { it.sessions }.map { it.id },
        )
        assertEquals(
            weeksOf(cycleOne).flatMap { it.sessions }.flatMap { it.allExercises() }.map { it.id },
            weeksOf(once).flatMap { it.sessions }.flatMap { it.allExercises() }.map { it.id },
        )

        // A través del cierre de ciclo: la marca `native-progression-c2` se registra una sola vez.
        val closed = ProgramProgressEngine.completeCycle(cycleOne, null, 1, logs)
        val closedAgain = ProgramProgressEngine.completeCycle(closed.program, null, 1, logs)
        assertEquals(
            1,
            closed.program.effectiveWeekRecipes.count { entry -> entry.appliedProposals.any { it.kind == "NATIVE_PROGRESSION" } },
        )
        assertEquals(closed.program.effectiveWeekRecipes, closedAgain.program.effectiveWeekRecipes)
        assertEquals(closed.program.macrocycles, closedAgain.program.macrocycles)
        assertEquals(closed.program.exerciseLoadReferences, closedAgain.program.exerciseLoadReferences)
        assertEquals(closed.program.nativeProgressionAudit, closedAgain.program.nativeProgressionAudit)
    }

    // ─── F-03 ───────────────────────────────────────────────────────────────

    @Test
    fun strengthPlan_resolvesTotalExternalForEveryBarbellSlot_fromTheCatalog() {
        val program = strengthPlan("real-convention")
        weeksOf(program).flatMap { it.sessions }.forEach { session ->
            val managed = managedOf(program, session.id)
            assertTrue(managed.isNotEmpty())
            managed.forEach { exercise ->
                assertEquals("barbell", equipmentOf(exercise))
                assertEquals(
                    "${exercise.catalogConfigurationId} debe usar kg totales de barra",
                    LoadQuantityConvention.TOTAL_EXTERNAL,
                    exercise.loadQuantityConvention,
                )
            }
        }
    }

    @Test
    fun dumbbellMusclePlan_derivesPerImplementFromTheCatalog_withoutHandPickedConventions() {
        val program = generate("real-dumbbell", NativeProfileKind.MUSCLE.entryId, days = 4, equipment = DUMBBELLS)
        val managed = weeksOf(program).flatMap { it.sessions }.flatMap { it.allExercises() }.filter { it.nativeProgressionManaged }
        assertTrue(managed.isNotEmpty())
        managed.forEach { exercise ->
            assertEquals(
                "${exercise.catalogConfigurationId}: la convención sale del equipo del catálogo",
                NativeLoadConventions.forEquipment(equipmentOf(exercise)),
                exercise.loadQuantityConvention,
            )
        }
        assertTrue(
            "con mancuernas hay al menos un slot por implemento",
            managed.any { it.loadQuantityConvention == LoadQuantityConvention.PER_IMPLEMENT },
        )
        assertTrue(
            "lo no gestionado (core) no recibe convención",
            weeksOf(program).flatMap { it.sessions }.flatMap { it.allExercises() }
                .filter { !it.nativeProgressionManaged }
                .all { it.loadQuantityConvention == LoadQuantityConvention.UNSPECIFIED },
        )
    }

    // ─── F-06 sobre un plan real mixto ──────────────────────────────────────

    @Test
    fun mixedRealPlan_bodyweightSlotProgressesByIdentity_notByTheRecipeStrategy() {
        val program = generate("real-mixed", NativeProfileKind.MUSCLE.entryId, days = 4, equipment = DUMBBELLS)
        assertNotNull(program.sourceRecipe?.nativeProgression)
        val firstWeekSessions = weeksOf(program).first().sessions
        val bodyweightSlot = firstWeekSessions.asSequence()
            .flatMap { session -> managedOf(program, session.id).asSequence().map { session to it } }
            .firstOrNull { (_, exercise) ->
                equipmentOf(exercise) == "bodyweight" &&
                    !exercise.isUnilateral && exercise.unilateralMode == UnilateralMode.BILATERAL
            }
        assumeTrue("el plan generado no trae un slot corporal gestionado dentro de un plan de carga", bodyweightSlot != null)
        val (session1, bodyweightExercise) = bodyweightSlot!!
        assertEquals(
            "la receta es de carga: el slot corporal es el caso mixto",
            com.example.kpkn.data.protocols.NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
            program.sourceRecipe?.nativeProgression?.strategy,
        )
        val dayId = bodyweightExercise.recipeDayId
        val session2 = weeksOf(program)[1].sessions.first { session -> session.allExercises().any { it.recipeDayId == dayId } }

        fun loadFor(exercise: Exercise): Double? = if (equipmentOf(exercise) == "bodyweight") null else 10.0
        val logs = listOf(
            completedLog(program, session1.id, "mixed-real-1", dayOffset = 1, loadKg = ::loadFor),
            completedLog(program, session2.id, "mixed-real-2", dayOffset = 8, loadKg = ::loadFor),
        )
        val observed = observe(program, logs)

        assertTrue(
            "un slot corporal nunca recibe un incremento de carga",
            observed.nativeProgressionProposals.none {
                it.identity.loadMode == LoadModeV2.BODYWEIGHT && it.kind == NativeProgressionProposalKind.INCREASE_LOAD
            },
        )
        val bodyweightDecisions = observed.nativeProgressionProposals.map { it.identity } +
            observed.nativeProgressionAudit.mapNotNull { it.identity }
        assertTrue(
            "el slot corporal produce una decisión de variante (propuesta o aviso), no silencio",
            bodyweightDecisions.any { it.loadMode == LoadModeV2.BODYWEIGHT },
        )
    }
}
