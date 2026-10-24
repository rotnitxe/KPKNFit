package com.example.kpkn.domain.training

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.domain.training.NativeProgressionTestSupport.completedLog
import com.example.kpkn.domain.training.NativeProgressionTestSupport.exercisesOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.managedOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.sessionOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.weeksOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * T4 sobre el runtime real: la propuesta de carga lleva el par (repeticiones, kilos) sobre el material
 * declarado; la regla de CUÁNDO subir no cambia; sin material, el texto genérico de siempre; y aceptar
 * escribe la carga y el objetivo de repeticiones (el rango del plan, que manda la regla, queda intacto).
 */
class NativeProgressionLoadTargetTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private const val GOBLET = "quads_sentadilla_copa__default"
        private const val BARBELL_BENCH = "bench_press__barbell"
        private const val CABLE_LAT = "lat_pulldown__bilateral__cable"
    }

    private fun recipeOf(id: String, configurationId: String, weeks: Int = 4): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = (1..weeks).map { number ->
            WeekRecipe(
                weekNumber = number,
                blockIndex = 0,
                weekName = "Semana $number",
                days = listOf(
                    DayRecipe(
                        id = "day-1",
                        label = "Día 1",
                        slots = listOf(
                            SlotRecipe(
                                id = "slot-1",
                                role = SlotRole.T3_ACCESSORY,
                                lift = LiftRef(configurationId = configurationId),
                                sets = List(2) { SetRecipe(repsMin = 8, repsMax = 12, rir = 2, loadBasis = LoadBasis.RPE) },
                                restSeconds = 90,
                                intent = SlotIntent.H,
                            ),
                        ),
                    ),
                ),
            )
        },
        nativeProgression = NativeProgressionSpec(NativeProgressionStrategy.REP_RANGE_THEN_LOAD, exposuresBeforeProposal = 2),
    )

    private fun materialized(configurationId: String, weeks: Int = 4): Program = PlanMaterializer.materialize(
        program = Program(id = "t4-$configurationId", name = "T4 $configurationId"),
        recipe = recipeOf("t4-$configurationId", configurationId, weeks),
        metadata = CatalogCompositionTestSupport.metadata,
        strict = false,
    )

    private fun twoExposures(program: Program, loadKg: Double, reps: Int = 12, count: Int = 2): List<WorkoutLog> = (1..count).map { week ->
        completedLog(
            program = program,
            sessionId = sessionOf(program, week).id,
            logId = "t4-$week",
            dayOffset = week,
            loadKg = { loadKg },
            repsOf = { reps },
        )
    }

    private fun observe(program: Program, logs: List<WorkoutLog>, inventory: EquipmentInventory?): Program =
        NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = logs,
            inventory = inventory,
            curatedConfigurations = emptySet(),
            nowMs = 500L,
        )

    private val everyTwoKilos = EquipmentInventory(dumbbells = (1..20).map { DumbbellPairStock(it * 2.0) })

    @Test
    fun `mancuernas de 2 en 2, 20 kg por 12, propone 22 kg con 8 repeticiones y nunca 12`() {
        val program = materialized(GOBLET)
        val proposal = observe(program, twoExposures(program, 20.0), everyTwoKilos).nativeProgressionProposals.single()

        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, proposal.kind)
        assertEquals(22.0, proposal.targetLoadKg!!, 0.0001)
        assertEquals(8, proposal.targetReps)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. " +
                "Propuesta: subir de 20 a 22 kg por mancuerna y buscar 8 repeticiones con 2 en reserva.",
            proposal.explanation,
        )
        assertFalse(proposal.explanation.contains("22 kg por mancuerna y buscar 12"))
    }

    @Test
    fun `el salto de mancuernas que no cabe en el rango se queda en un aviso con kilos y repeticiones`() {
        val program = materialized(GOBLET)
        val stock = EquipmentInventory(dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(14.0), DumbbellPairStock(16.0)))
        val observed = observe(program, twoExposures(program, 12.0), stock)

        assertTrue("no se propone cambiar la carga", observed.nativeProgressionProposals.isEmpty())
        val notice = observed.nativeProgressionAudit.single()
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, notice.status)
        assertEquals(NativeProgressionProposalKind.INCREASE_LOAD, notice.kind)
        assertTrue(notice.userFacingNotice)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series, pero el siguiente peso superior que puedes montar " +
                "(14 kg por mancuerna) es un salto demasiado grande. " +
                "Mantén 12 kg por mancuerna y sigue con 12 repeticiones, con 2 en reserva; no se propone un cambio.",
            notice.reason,
        )
    }

    @Test
    fun `el aviso de mantener no se apila igual cada dos sesiones y vuelve a avisar pasada la semana`() {
        val program = materialized(GOBLET, weeks = 6)
        val stock = EquipmentInventory(dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(14.0), DumbbellPairStock(16.0)))
        val first = observe(program, twoExposures(program, 12.0), stock)
        assertEquals(1, first.nativeProgressionAudit.size)

        val fourLogs = twoExposures(program, 12.0, count = 4)
        val sameWeek = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = first, logs = fourLogs, inventory = stock, curatedConfigurations = emptySet(), nowMs = 600L,
        )
        assertEquals("mismo aviso dentro de la semana: no se apila", 1, sameWeek.nativeProgressionAudit.size)

        val aWeekLater = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = first, logs = fourLogs, inventory = stock, curatedConfigurations = emptySet(),
            nowMs = 500L + 8L * 24L * 60L * 60L * 1000L,
        )
        assertEquals("pasada la semana, avisa de nuevo", 2, aWeekLater.nativeProgressionAudit.size)
    }

    @Test
    fun `sin material declarado el comportamiento y el texto son los genericos de siempre`() {
        val program = materialized(GOBLET)
        val proposal = observe(program, twoExposures(program, 20.0), inventory = null).nativeProgressionProposals.single()

        assertNull(proposal.targetLoadKg)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: en el próximo entrenamiento " +
                "elige una carga un poco mayor y deja las mismas repeticiones en reserva.",
            proposal.explanation,
        )
    }

    @Test
    fun `con el tope del material sigue el aviso de tope de siempre`() {
        val program = materialized(GOBLET)
        val top = EquipmentInventory(dumbbells = listOf(DumbbellPairStock(12.0), DumbbellPairStock(16.0)))
        val observed = observe(program, twoExposures(program, 16.0), top)

        assertTrue(observed.nativeProgressionProposals.isEmpty())
        assertTrue(observed.nativeProgressionAudit.single().reason.contains("no tiene un par de mancuernas más pesado"))
    }

    @Test
    fun `barra con discos limitados, solo propone cargas formables`() {
        val program = materialized(BARBELL_BENCH)
        // Solo discos de 5 kg (5 por lado): el único paso formable sobre 60 kg es de 10 kg en total.
        val limited = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(5.0, 5)))
        val observed = observe(program, twoExposures(program, 60.0), limited)

        // 60 -> 70 kg (10 kg de salto) deja ~5 repeticiones con 2 en reserva (rango 8 a 12): no cabe y se mantiene.
        assertTrue(observed.nativeProgressionProposals.isEmpty())
        val notice = observed.nativeProgressionAudit.single()
        assertTrue(notice.reason, notice.reason.contains("Mantén 60 kg"))
        assertTrue(notice.reason, notice.reason.contains("(70 kg en total)"))
    }

    @Test
    fun `maquina de 5 en 5 kg, 50 kg por 12, propone 55 kg con 8 repeticiones sin salirse del rango de la maquina`() {
        val program = materialized(CABLE_LAT)
        val machine = EquipmentInventory(
            machines = listOf(MachineLoadRange(configurationId = CABLE_LAT, minLoadKg = 5.0, maxLoadKg = 55.0, incrementKg = 5.0, baseLoadKg = 0.0)),
        )
        val proposal = observe(program, twoExposures(program, 50.0), machine).nativeProgressionProposals.single()

        assertEquals(55.0, proposal.targetLoadKg!!, 0.0001)
        assertTrue(proposal.explanation, proposal.explanation.endsWith("subir de 50 a 55 kg y buscar 8 repeticiones con 2 en reserva."))

        // En el tope de la máquina (55 kg) no hay carga mayor: aviso de tope, como siempre.
        val atTop = observe(program, twoExposures(program, 55.0), machine)
        assertTrue(atTop.nativeProgressionProposals.isEmpty())
        assertTrue(atTop.nativeProgressionAudit.single().reason.contains("llega como máximo a 55 kg"))
    }

    @Test
    fun `dos observaciones con los mismos datos dan la misma propuesta`() {
        val program = materialized(GOBLET)
        val logs = twoExposures(program, 20.0)
        val first = observe(program, logs, everyTwoKilos).nativeProgressionProposals
        val second = observe(program, logs, everyTwoKilos).nativeProgressionProposals
        assertEquals(first, second)
    }

    @Test
    fun `aceptar una propuesta con par guarda carga y repeticiones sin cambiar el rango del plan`() {
        val program = materialized(GOBLET)
        val logs = twoExposures(program, 20.0)
        val observed = observe(program, logs, everyTwoKilos)
        val proposal = observed.nativeProgressionProposals.single()
        val restored = Json.decodeFromString<NativeProgressionProposal>(Json.encodeToString(proposal))
        assertEquals(proposal, restored)
        val rangeBefore = managedOf(program, sessionOf(program, 3).id).single().sets.map { it.targetRepsRange }

        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed.copy(nativeProgressionProposals = listOf(restored)),
            proposalId = restored.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 600L,
        )
        val future = exercisesOf(accepted, sessionOf(accepted, 3).id).single { it.nativeProgressionManaged }
        assertEquals(listOf(22.0, 22.0), future.sets.map { it.weight })
        assertEquals(listOf(8, 8), future.sets.map { it.targetReps })
        for (week in 1..2) {
            assertEquals(sessionOf(program, week), sessionOf(accepted, week))
        }
        assertEquals("el rango del plan no cambia: manda la regla de cuándo subir", rangeBefore, future.sets.map { it.targetRepsRange })

        val again = NativeWorkoutProgressionRuntime.resolveProposal(
            program = accepted,
            proposalId = proposal.proposalId,
            accept = true,
            logs = logs,
            ongoingSessionIds = emptySet(),
            curatedExercises = emptyMap(),
            nowMs = 601L,
        )
        assertEquals("aceptar dos veces no cambia nada", accepted, again)
    }

    @Test
    fun `rechazar el par conserva todas las prescripciones y una propuesta antigua no exige repeticiones`() {
        val program = materialized(GOBLET)
        val logs = twoExposures(program, 20.0)
        val observed = observe(program, logs, everyTwoKilos)
        val proposal = observed.nativeProgressionProposals.single()
        val rejected = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed, proposalId = proposal.proposalId, accept = false,
            logs = logs, ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 600L,
        )
        assertEquals(program.macrocycles, rejected.macrocycles)
        assertEquals(NativeProgressionResolutionStatus.REJECTED, rejected.nativeProgressionAudit.single().status)
        val oldJson = Json.encodeToString(proposal.copy(targetReps = null))
        assertFalse(oldJson.contains("targetReps"))
        val legacy = Json.decodeFromString<NativeProgressionProposal>(oldJson)
        assertNull(legacy.targetReps)
        val legacyAccepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed.copy(nativeProgressionProposals = listOf(legacy)),
            proposalId = legacy.proposalId, accept = true, logs = logs,
            ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 600L,
        )
        val before = managedOf(program, sessionOf(program, 3).id).single().sets
        val after = managedOf(legacyAccepted, sessionOf(legacyAccepted, 3).id).single().sets
        assertEquals(before.map { it.targetReps }, after.map { it.targetReps })
        assertEquals(before.map { it.targetRepsRange }, after.map { it.targetRepsRange })
    }

    private fun rebuildThirdWeek(program: Program): Program = PlanMaterializer.rematerializeWeek(
        program = Json.decodeFromString<Program>(Json.encodeToString(program)),
        weekId = weeksOf(program)[2].id,
        metadata = CatalogCompositionTestSupport.metadata,
    )

    @Test
    fun `el par aceptado sobrevive al JSON y a regenerar la semana sin cambiar el rango ni el pasado`() {
        val program = materialized(GOBLET)
        val logs = twoExposures(program, 20.0)
        val observed = observe(program, logs, everyTwoKilos)
        val proposal = observed.nativeProgressionProposals.single()
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed, proposalId = proposal.proposalId, accept = true,
            logs = logs, ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 600L,
        )
        val rebuilt = rebuildThirdWeek(accepted)
        val before = managedOf(accepted, sessionOf(accepted, 3).id).single()
        val after = managedOf(rebuilt, sessionOf(rebuilt, 3).id).single()
        assertEquals(listOf(22.0, 22.0), after.sets.map { it.weight })
        assertEquals(listOf(8, 8), after.sets.map { it.targetReps })
        assertEquals(before.sets.map { it.targetRepsRange }, after.sets.map { it.targetRepsRange })
        for (week in 1..2) assertEquals(sessionOf(accepted, week), sessionOf(rebuilt, week))
        assertEquals(accepted.nativeProgressionAudit, rebuilt.nativeProgressionAudit)

        // Las referencias antiguas, y los objetivos incompatibles con un rango nuevo, conservan la receta.
        for (target in listOf(null, 99)) {
            val legacy = accepted.copy(exerciseLoadReferences = accepted.exerciseLoadReferences.map { entry ->
                entry.copy(references = entry.references.map { it.copy(capturedTargetReps = target) })
            })
            val legacyExercise = managedOf(rebuildThirdWeek(legacy), sessionOf(legacy, 3).id).single()
            val original = managedOf(program, sessionOf(program, 3).id).single()
            assertEquals(original.sets.map { it.targetReps }, legacyExercise.sets.map { it.targetReps })
            assertEquals(listOf(22.0, 22.0), legacyExercise.sets.map { it.weight })
        }
    }

    @Test
    fun `aceptar y regenerar el par de un lado conserva los objetivos del otro lado`() {
        val recipe = recipeOf("t4-lateral", GOBLET).let { source ->
            source.copy(weeks = source.weeks.map { week -> week.copy(days = week.days.map { day ->
                day.copy(slots = day.slots.map { it.copy(isUnilateral = true) })
            }) })
        }
        val program = PlanMaterializer.materialize(
            program = Program(id = "t4-lateral", name = "T4 lateral"), recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata, strict = false,
        )
        val logs = twoExposures(program, 20.0).map { log ->
            log.copy(completedExercises = log.completedExercises.map { exercise ->
                exercise.copy(sets = exercise.sets.flatMap { set ->
                    listOf("left", "right").map { side ->
                        set.copy(id = "${set.id}-$side", side = side, recordedPayloadV3 = set.recordedPayloadV3!!.copy(side = side))
                    }
                })
            })
        }
        val initial = observe(program, logs, everyTwoKilos)
        // El otro lado ya tiene un par aceptado: debe sobrevivir a aceptar el izquierdo y regenerar.
        val rightProposal = initial.nativeProgressionProposals.single { it.identity.side == "right" }
        val observed = NativeWorkoutProgressionRuntime.resolveProposal(
            program = initial, proposalId = rightProposal.proposalId, accept = true,
            logs = logs, ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 550L,
        )
        val proposal = observed.nativeProgressionProposals.single { it.identity.side == "left" }
        val before = managedOf(observed, sessionOf(observed, 3).id).single()
        assertEquals(listOf(22.0, 22.0), before.sets.map { it.rightTarget?.weight })
        assertEquals(listOf(8, 8), before.sets.map { it.rightTarget?.targetReps })
        val accepted = NativeWorkoutProgressionRuntime.resolveProposal(
            program = observed, proposalId = proposal.proposalId, accept = true,
            logs = logs, ongoingSessionIds = emptySet(), curatedExercises = emptyMap(), nowMs = 600L,
        )
        val acceptedExercise = managedOf(accepted, sessionOf(accepted, 3).id).single()
        assertEquals(before.sets.map { it.rightTarget }, acceptedExercise.sets.map { it.rightTarget })
        val rebuilt = rebuildThirdWeek(accepted)
        val after = managedOf(rebuilt, sessionOf(rebuilt, 3).id).single()
        assertEquals(listOf(22.0, 22.0), after.sets.map { it.leftTarget?.weight })
        assertEquals(listOf(8, 8), after.sets.map { it.leftTarget?.targetReps })
        assertEquals(before.sets.map { it.rightTarget }, after.sets.map { it.rightTarget })
        assertEquals(before.sets.map { it.targetRepsRange }, after.sets.map { it.targetRepsRange })
        for (week in 1..2) assertEquals(sessionOf(accepted, week), sessionOf(rebuilt, week))
    }

    @Test
    fun `una bajada con par propone una carga montable mas ligera con sus repeticiones`() {
        val program = materialized(BARBELL_BENCH)
        val plates = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(10.0, 2), PlateStock(5.0, 2), PlateStock(2.5, 2), PlateStock(1.25, 2)),
        )
        val observed = observe(program, twoExposures(program, 62.5, reps = 5), plates)

        val proposal = observed.nativeProgressionProposals.single()
        assertEquals(NativeProgressionProposalKind.REDUCE_LOAD, proposal.kind)
        assertEquals(55.0, proposal.targetLoadKg!!, 0.0001)
        assertTrue(proposal.explanation, proposal.explanation.endsWith("Propuesta: bajar de 62,5 a 55 kg en total y buscar 9 repeticiones con 2 en reserva."))
    }
}
