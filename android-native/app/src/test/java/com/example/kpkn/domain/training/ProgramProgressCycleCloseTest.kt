package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.NativeProgressionSpec
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.1 + AC-G3/AC-G4: al cerrar ciclo la progresión propia se procesa UNA sola
 * vez (sin duplicar ocurrencias ni propuestas en cierres repetidos) y ninguna
 * acción pendiente se descarta en silencio: queda expirada con motivo y el audit
 * append-only sobrevive al cierre.
 */
class ProgramProgressCycleCloseTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun recipe(nativeProgression: Boolean): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "cycle-recipe",
        weeks = listOf(
            weekRecipe(1, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION, emptyList()),
            weekRecipe(2, 0, "Base", com.example.kpkn.data.models.BlockGoal.ACCUMULATION, emptyList()),
        ),
        repeats = true,
        nativeProgression = if (nativeProgression) NativeProgressionSpec() else null,
    )

    private fun baseProgram(nativeProgression: Boolean = true): Program = Program(
        id = "cycle-prog",
        name = "Ciclo",
        structure = ProgramStructure.SIMPLE,
        sourceRecipe = recipe(nativeProgression),
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    com.example.kpkn.data.models.ProgramWeek(
                                        id = "w1", name = "Semana 1",
                                        sessions = listOf(Session(id = "s1", name = "Día 1", dayOfWeek = 1, isMainSession = true)),
                                    ),
                                    com.example.kpkn.data.models.ProgramWeek(
                                        id = "w2", name = "Semana 2",
                                        sessions = listOf(Session(id = "s2", name = "Día 2", dayOfWeek = 3, isMainSession = true)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ).copy(
        runState = ProgramRunState(
            runId = "run-cycle",
            cycleNumber = 1,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
            weekId = "w1",
        ),
    )

    private fun cycle1Logs(): List<WorkoutLog> = listOf(
        WorkoutLog(
            id = "log-1", programId = "cycle-prog", sessionId = "s1", sessionName = "Día 1",
            date = "2026-01-01T10:00:00.000Z", durationMinutes = 45, weekId = "w1",
            cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
        ),
        WorkoutLog(
            id = "log-2", programId = "cycle-prog", sessionId = "s2", sessionName = "Día 2",
            date = "2026-01-03T10:00:00.000Z", durationMinutes = 45, weekId = "w2",
            cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
        ),
    )

    @Test
    fun cycle_close_registers_native_progression_exactly_once() {
        val result = ProgramProgressEngine.completeCycle(baseProgram(), null, 1, cycle1Logs())

        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, "w1"), result.program.runState?.weekInstanceId)

        val continuations = result.program.effectiveWeekRecipes.filter { entry ->
            entry.appliedProposals.any { it.kind == "NATIVE_PROGRESSION" }
        }
        assertEquals("La continuación se registra una sola vez", 1, continuations.size)
        assertEquals(1, continuations.single().appliedProposals.size)
        assertEquals(2, continuations.single().cycleNumber)
        // La continuación se ancla en la primera semana del ciclo nuevo (antes comparaba un valor consigo mismo).
        assertEquals(1, continuations.single().weekOccurrence)
        assertEquals(
            ProgramProgressEngine.instanceIdFor(2, "w1"),
            result.program.runState?.weekInstanceId,
        )
        val occurrenceKeys = result.program.effectiveWeekRecipes.map { it.weekOccurrence to it.cycleNumber }
        assertEquals(occurrenceKeys, occurrenceKeys.distinct())
    }

    @Test
    fun recipe_without_native_progression_registers_nothing_at_cycle_close() {
        val result = ProgramProgressEngine.completeCycle(baseProgram(nativeProgression = false), null, 1, cycle1Logs())
        assertTrue(result.advancedCycle)
        assertTrue(result.program.effectiveWeekRecipes.isEmpty())
    }

    @Test
    fun repeated_cycle_close_does_not_duplicate_occurrence_ids_or_proposals() {
        val first = ProgramProgressEngine.completeCycle(baseProgram(), null, 1, cycle1Logs())
        val second = ProgramProgressEngine.completeCycle(first.program, null, 1, cycle1Logs())

        assertEquals(
            "La receta efectiva no se duplica",
            first.program.effectiveWeekRecipes,
            second.program.effectiveWeekRecipes,
        )
        assertEquals(
            "Las ocurrencias son deterministas",
            first.program.runState?.weekInstanceId,
            second.program.runState?.weekInstanceId,
        )
        assertEquals(
            "El audit no crece con cierres repetidos",
            first.program.runState?.autoregulationAudit,
            second.program.runState?.autoregulationAudit,
        )
        assertEquals(2, second.program.runState?.cycleNumber)
    }

    @Test
    fun cycle_close_expires_pending_autoregulation_with_a_reason_and_keeps_the_audit() {
        val proposal = AutoregulationProposal(
            kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
            percentDelta = -2.5,
            explanation = "Readiness 40",
        )
        val generation = AutoregulationAuditEntry(
            atMs = 1_000L,
            mode = AutoregulationMode.PROPOSE,
            kinds = listOf(AutoregulationProposalKind.SCALE_WEEK_INTENSITY),
            weekId = "w2",
        )
        val program = baseProgram(nativeProgression = false).copy(
            runState = ProgramRunState(
                runId = "run-cycle",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
                weekId = "w1",
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar intensidad",
                    proposals = listOf(proposal),
                    targetWeekId = "w2",
                ),
                autoregulationAudit = listOf(generation),
            ),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertNull("Nada queda pendiente en silencio", result.program.runState?.pendingAction)
        val audit = result.program.runState?.autoregulationAudit.orEmpty()
        assertEquals("La entrada de generación se conserva", 1, audit.count { it.resolution == null })
        val expired = audit.filter { it.resolution == PendingActionResolutionStatus.EXPIRED }
        assertEquals(1, expired.size)
        assertTrue(expired.single().resolutionReason.contains("Caducada"))
        assertEquals(listOf(AutoregulationProposalKind.SCALE_WEEK_INTENSITY), expired.single().kinds)
    }

    private fun pendingNativeProposal(): NativeProgressionProposal = NativeProgressionProposal(
        proposalId = "native-pending-1",
        kind = NativeProgressionProposalKind.INCREASE_LOAD,
        identity = NativeProgressionIdentity(
            recipeId = "cycle-recipe",
            recipeContentVersion = 1,
            recipeDayId = "d1",
            recipeSlotId = "s1",
            configurationId = "bench_press__barbell",
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "",
            slotPurpose = "H",
        ),
        sourceSessionId = "s1",
        sourceLogIds = listOf("log-1", "log-2"),
        explanation = "pendiente",
        createdAtMs = 1L,
    )

    @Test
    fun cycle_close_expires_pending_native_proposals_with_a_reason_and_consumes_their_logs() {
        val program = baseProgram().copy(nativeProgressionProposals = listOf(pendingNativeProposal()))

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertTrue("nada queda pendiente en silencio", result.program.nativeProgressionProposals.isEmpty())
        val resolution = result.program.nativeProgressionAudit.single { it.proposalId == "native-pending-1" }
        assertEquals(NativeProgressionResolutionStatus.EXPIRED, resolution.status)
        assertTrue(resolution.reason.contains("Caducada al cerrar el ciclo 1"))
        assertEquals(listOf("log-1", "log-2"), resolution.sourceLogIds)
        // H-CICLO: la caducidad queda en el registro pero NO se muestra; el atleta recibe UN solo
        // aviso al entrar en el ciclo siguiente, con la duración real del programa.
        assertFalse("la caducidad no se suma al aviso de nuevo bloque", resolution.userFacingNotice)
        val visible = result.program.nativeProgressionAudit.filter { it.userFacingNotice }
        assertEquals(1, visible.size)
        assertEquals(NativeProgressionResolutionStatus.NOTICE, visible.single().status)
        assertEquals("native-cycle-c2", visible.single().proposalId)
        assertTrue(visible.single().reason, visible.single().reason.startsWith("Empiezas un nuevo bloque de 2 semanas con tus últimas cargas."))
        assertTrue("avisa que la propuesta sin responder caducó", visible.single().reason.contains("caducaron"))

        // Cierre repetido: la guarda `native-progression-c2` evita duplicar avisos o reabrir propuestas.
        val again = ProgramProgressEngine.completeCycle(result.program, null, 1, cycle1Logs())
        assertEquals(result.program.nativeProgressionAudit, again.program.nativeProgressionAudit)
        assertTrue(again.program.nativeProgressionProposals.isEmpty())
    }

    @Test
    fun recipe_without_native_progression_keeps_its_native_proposals_untouched_at_cycle_close() {
        // Defensa: sin `nativeProgression` el motor de ciclo no interpreta datos nativos ajenos.
        val program = baseProgram(nativeProgression = false).copy(nativeProgressionProposals = listOf(pendingNativeProposal()))

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs())

        assertTrue(result.advancedCycle)
        assertEquals(program.nativeProgressionProposals, result.program.nativeProgressionProposals)
        assertTrue(result.program.nativeProgressionAudit.isEmpty())
    }

    // ─── B.S3: progresión del método (CycleIncrement) ─────────────────────────────

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    private fun wendlerRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!

    private fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Un registro por sesión de [weeks] en el ciclo [cycle]; los ciclos posteriores al 1 llevan el id del run. */
    private fun logsFor(
        program: Program,
        weeks: List<ProgramWeek>,
        cycle: Int = 1,
        runId: String? = null,
    ): List<WorkoutLog> = weeks.flatMap { week ->
        week.sessions.map { session ->
            WorkoutLog(
                id = "log_${session.id}_c$cycle",
                programId = program.id,
                sessionId = session.id,
                sessionName = session.name,
                date = "2026-01-01T10:00:00.000Z",
                durationMinutes = 45,
                weekId = week.id,
                cycleNumber = cycle,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
                programRunId = runId,
            )
        }
    }

    /** 5/3/1 BBB con 1RM sq 200 / bp 120 / dl 220 (TM 180 / 108 / 198) y las cuatro semanas del ciclo 1 entrenadas. */
    private fun wendlerAtCycleEnd(
        recipe: TrainingPlanRecipe = wendlerRecipe(),
        profile: PowerliftingProfile? = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
    ): Pair<Program, List<WorkoutLog>> {
        val materialized = PlanMaterializer.materialize(
            Program(id = "w531", name = "5/3/1"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = profile,
        )
        val weeks = weeksOf(materialized)
        val last = weeks.last()
        val program = materialized.copy(
            runState = ProgramRunState(
                runId = "run_531",
                cycleNumber = 1,
                weekId = last.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, last.id),
            ),
        )
        return program to logsFor(program, weeks)
    }

    private fun t1Of(week: ProgramWeek, configurationId: String) = week.sessions.flatMap { it.allExercises() }
        .first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == configurationId }

    private fun allWeights(program: Program): List<Double?> =
        weeksOf(program).flatMap { it.sessions }.flatMap { it.allExercises() }.flatMap { it.sets }.map { it.weight }

    private fun userNotices(program: Program) = program.nativeProgressionAudit.filter { it.userFacingNotice }

    private fun appliedProposals(program: Program, proposalId: String) =
        program.effectiveWeekRecipes
            .flatMap { entry -> entry.appliedProposals.map { applied -> entry to applied } }
            .filter { (_, applied) -> applied.proposalId == proposalId }

    private fun Program.mapSession(sessionId: String, transform: (Session) -> Session): Program = copy(
        macrocycles = macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    week.copy(sessions = week.sessions.map { if (it.id == sessionId) transform(it) else it })
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun Session.withEveryWeightAt(kg: Double): Session = copy(
        exercises = exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(weight = kg) }) },
        parts = parts.map { part ->
            part.copy(exercises = part.exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(weight = kg) }) })
        },
    )

    @Test
    fun wendler_cycle_close_raises_the_tm_by_5_2_5_and_5_rebuilds_the_four_weeks_and_notices_once() {
        val (program, logs) = wendlerAtCycleEnd()
        val before = program.powerliftingProfile!!
        assertEquals(180.0, before.squatTM!!, 1e-9)
        assertEquals(108.0, before.benchTM!!, 1e-9)
        assertEquals(198.0, before.deadliftTM!!, 1e-9)

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs)

        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        val after = result.program.powerliftingProfile!!
        // Oráculo del plan: 5/3/1 cierra el ciclo con el TM +5 (sentadilla), +2,5 (banca) y +5 (peso muerto).
        assertEquals(before.squatTM!! + 5.0, after.squatTM!!, 1e-9)
        assertEquals(before.benchTM!! + 2.5, after.benchTM!!, 1e-9)
        assertEquals(before.deadliftTM!! + 5.0, after.deadliftTM!!, 1e-9)
        assertNull("sin 1RM de press militar no hay TM que subir", after.overheadTM)
        assertEquals("los 1RM no se tocan", before.squat1RM, after.squat1RM)

        val beforeWeeks = weeksOf(program)
        val afterWeeks = weeksOf(result.program)
        assertEquals(4, afterWeeks.size)
        assertEquals("las semanas conservan su id", beforeWeeks.map { it.id }, afterWeeks.map { it.id })
        assertEquals(
            "las sesiones conservan su id: los registros del ciclo cerrado siguen valiendo",
            beforeWeeks.flatMap { week -> week.sessions.map { it.id } },
            afterWeeks.flatMap { week -> week.sessions.map { it.id } },
        )
        // Primera serie del T1 de cada semana: 65, 70, 75 y 40 % del TM (antes con 180 kg, ahora con el TM nuevo).
        listOf(65.0, 70.0, 75.0, 40.0).forEachIndexed { index, percent ->
            val label = "semana ${index + 1}"
            assertEquals("antes $label", percent / 100.0 * 180.0, t1Of(beforeWeeks[index], CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
            assertEquals("sentadilla $label", percent / 100.0 * 185.0, t1Of(afterWeeks[index], CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
            assertEquals("banca $label", percent / 100.0 * 110.5, t1Of(afterWeeks[index], CatalogIds.BP).sets.first().weight!!, 1e-6)
            assertEquals("peso muerto $label", percent / 100.0 * 203.0, t1Of(afterWeeks[index], CatalogIds.DL).sets.first().weight!!, 1e-6)
        }
        assertTrue(result.program.macrocycles.flatMap { it.blocks }.none { it.materializationPending })

        // Un único aviso visible, con el TM de cada levantamiento que cambió.
        val notices = userNotices(result.program)
        assertEquals(1, notices.size)
        assertEquals(NativeProgressionResolutionStatus.NOTICE, notices.single().status)
        assertEquals("author-cycle-c2", notices.single().proposalId)
        assertEquals(
            "Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 108 → 110,5 kg, peso muerto 198 → 203 kg.",
            notices.single().reason,
        )

        // La guarda idempotente queda en la receta efectiva del ciclo nuevo.
        val markers = appliedProposals(result.program, "author-cycle-c2")
        assertEquals(1, markers.size)
        assertEquals(2, markers.single().first.cycleNumber)
        assertEquals("AUTHOR_CYCLE_INCREMENT", markers.single().second.kind)
    }

    @Test
    fun repeated_cycle_close_applies_the_author_increment_only_once() {
        val (program, logs) = wendlerAtCycleEnd()
        val first = ProgramProgressEngine.completeCycle(program, null, 1, logs)
        val second = ProgramProgressEngine.completeCycle(first.program, null, 1, logs)

        assertEquals(185.0, second.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(first.program.powerliftingProfile, second.program.powerliftingProfile)
        assertEquals(first.program.effectiveWeekRecipes, second.program.effectiveWeekRecipes)
        assertEquals(first.program.nativeProgressionAudit, second.program.nativeProgressionAudit)
        assertEquals(allWeights(first.program), allWeights(second.program))
        assertEquals(1, appliedProposals(second.program, "author-cycle-c2").size)
        assertEquals(1, userNotices(second.program).size)
    }

    @Test
    fun cycle_close_keeps_the_sessions_with_manual_adjustments_and_says_how_many() {
        val (program, logs) = wendlerAtCycleEnd()
        val week1 = weeksOf(program).first()
        val customized = week1.sessions[0] // sentadilla de la semana 1
        val untouched = week1.sessions[1] // banca de la semana 1
        val edited = PlanMaterializer.withManualSessionOverride(
            program.mapSession(customized.id) { it.withEveryWeightAt(99.0) },
            sessionId = customized.id,
            weekId = week1.id,
            weekOccurrence = 1,
            recipeDayId = null,
        )

        val result = ProgramProgressEngine.completeCycle(edited, null, 1, logs)

        val index = ProgramHierarchyIndex(result.program)
        val kept = index.locateSession(customized.id)!!.session
        assertTrue(
            "la sesión personalizada conserva sus 99 kg",
            kept.allExercises().flatMap { it.sets }.all { it.weight == 99.0 },
        )
        val rebuiltT1 = index.locateSession(untouched.id)!!.session.allExercises().first { it.slotRole == SlotRole.T1_MAIN }
        assertEquals("la sesión sin ajustes toma el TM nuevo", 0.65 * 110.5, rebuiltT1.sets.first().weight!!, 1e-6)
        assertEquals(listOf(customized.id), result.program.manualSessionOverrides.map { it.sessionId })
        assertEquals(
            "Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 108 → 110,5 kg, peso muerto 198 → 203 kg. " +
                "1 sesión con ajustes manuales se conservó sin cambios.",
            userNotices(result.program).single().reason,
        )
    }

    @Test
    fun cycle_close_without_catalog_metadata_flags_the_blocks_pending_and_keeps_the_loads() {
        val (program, logs) = wendlerAtCycleEnd()
        val saved = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        try {
            val result = ProgramProgressEngine.completeCycle(program, null, 1, logs)

            assertTrue(result.advancedCycle)
            // El TM sube y se guarda, pero ninguna carga de las semanas cambia: eso lo hace RE-MATERIALIZAR.
            assertEquals(185.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
            assertEquals(allWeights(program), allWeights(result.program))
            assertTrue(result.program.macrocycles.flatMap { it.blocks }.all { it.materializationPending })
            val notice = userNotices(result.program).single()
            assertEquals("author-cycle-c2", notice.proposalId)
            assertTrue(notice.reason, notice.reason.endsWith("Las cargas nuevas se aplican al pulsar RE-MATERIALIZAR."))
            assertEquals(1, appliedProposals(result.program, "author-cycle-c2").size)
            // El porqué técnico queda en el marcador, no en el aviso del atleta.
            val summary = appliedProposals(result.program, "author-cycle-c2").single().second.summary
            assertTrue(summary, summary.contains("reconstrucción pendiente: sin metadatos del catálogo"))
            assertFalse(notice.reason, notice.reason.contains("metadatos"))

            // El botón RE-MATERIALIZAR reconstruye las semanas con el TM guardado y aplica las cargas nuevas.
            val rebuilt = weeksOf(result.program).fold(result.program) { acc, week ->
                PlanMaterializer.rematerializeWeek(acc, week.id, acc.sourceRecipe!!, CatalogCompositionTestSupport.metadata)
            }
            assertEquals(0.65 * 185.0, t1Of(weeksOf(rebuilt).first(), CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
        } finally {
            CompositionMetadataHolder.current = saved
        }
    }

    @Test
    fun cycle_close_rebuilds_with_the_metadata_it_receives_even_when_the_holder_is_empty() {
        val (program, logs) = wendlerAtCycleEnd()
        val saved = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        try {
            val result = ProgramProgressEngine.completeCycle(
                program, null, 1, logs,
                compositionMetadata = CatalogCompositionTestSupport.metadata,
            )

            assertEquals(0.65 * 185.0, t1Of(weeksOf(result.program).first(), CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
            assertTrue(result.program.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
        } finally {
            CompositionMetadataHolder.current = saved
        }
    }

    @Test
    fun cycle_close_survives_a_catalog_that_cannot_rebuild_the_weeks() {
        val (program, logs) = wendlerAtCycleEnd()

        // Cerrar un ciclo es parte de registrar una sesión: un catálogo que falla nunca puede romperlo.
        val result = ProgramProgressEngine.completeCycle(
            program, null, 1, logs,
            compositionMetadata = ExerciseCompositionMetadataProvider { null },
        )

        assertTrue(result.advancedCycle)
        assertEquals(185.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(allWeights(program), allWeights(result.program))
        assertTrue(result.program.macrocycles.flatMap { it.blocks }.all { it.materializationPending })
        assertEquals(1, userNotices(result.program).size)
        // El motivo del fallo (clase y mensaje) queda en el marcador para diagnosticar; el aviso no lo lleva.
        val summary = appliedProposals(result.program, "author-cycle-c2").single().second.summary
        assertTrue(summary, summary.contains("reconstrucción pendiente: IllegalStateException"))
        assertTrue(summary, summary.contains("sin metadata del catálogo"))
        assertFalse(userNotices(result.program).single().reason.contains("IllegalStateException"))
    }

    @Test
    fun cycle_close_rounds_the_tm_to_the_smallest_load_the_inventory_can_add() {
        val (program, logs) = wendlerAtCycleEnd()
        val inventory = EquipmentInventory(
            plates = listOf(PlateStock(1.25, 2), PlateStock(2.5, 2), PlateStock(5.0, 2), PlateStock(20.0, 2)),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs, inventory = inventory)

        // Discos de 1,25 kg: el paso es 2,5 kg. 185 ya está en la rejilla; 110,5 pasa a 110 y 203 a 202,5.
        val tm = result.program.powerliftingProfile!!
        assertEquals(185.0, tm.squatTM!!, 1e-9)
        assertEquals(110.0, tm.benchTM!!, 1e-9)
        assertEquals(202.5, tm.deadliftTM!!, 1e-9)
        assertEquals(
            "Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 108 → 110 kg, peso muerto 198 → 202,5 kg.",
            userNotices(result.program).single().reason,
        )
    }

    @Test
    fun cycle_close_does_nothing_without_a_load_profile() {
        val (program, logs) = wendlerAtCycleEnd(profile = null)
        assertNull(program.powerliftingProfile)

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs)

        assertTrue(result.advancedCycle)
        assertNull(result.program.powerliftingProfile)
        assertTrue(result.program.effectiveWeekRecipes.isEmpty())
        assertTrue(userNotices(result.program).isEmpty())
    }

    @Test
    fun cycle_close_leaves_the_author_rule_alone_when_the_plan_has_native_progression() {
        val (program, logs) = wendlerAtCycleEnd(wendlerRecipe().copy(nativeProgression = NativeProgressionSpec()))

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs)

        assertTrue(result.advancedCycle)
        assertEquals("manda la progresión nativa: el TM no sube", program.powerliftingProfile, result.program.powerliftingProfile)
        assertTrue(appliedProposals(result.program, "author-cycle-c2").isEmpty())
    }

    @Test
    fun a_block_scoped_rule_does_not_raise_the_tm_when_the_cycle_closes() {
        val (program, logs) = wendlerAtCycleEnd(
            wendlerRecipe().copy(progression = ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK)),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs)

        assertTrue(result.advancedCycle)
        assertEquals(program.powerliftingProfile, result.program.powerliftingProfile)
        assertTrue(appliedProposals(result.program, "author-cycle-c2").isEmpty())
        assertTrue(userNotices(result.program).isEmpty())
    }

    @Test
    fun cycle_close_never_makes_a_tm_step_larger_than_the_method_increment() {
        // Discos de 2,5 kg: el paso del inventario es 5 kg. Con los TM ya en la rejilla, la banca sube 2,5
        // (110 → 112,5) y no 5, y sentadilla y peso muerto suben sus 5 kg.
        val (program, logs) = wendlerAtCycleEnd(
            profile = PowerliftingProfile(
                squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
                squatTM = 180.0, benchTM = 110.0, deadliftTM = 200.0,
            ),
        )
        val inventory = EquipmentInventory(
            plates = listOf(PlateStock(2.5, 2), PlateStock(5.0, 2), PlateStock(20.0, 2)),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, logs, inventory = inventory)

        val tm = result.program.powerliftingProfile!!
        assertEquals(185.0, tm.squatTM!!, 1e-9)
        assertEquals(112.5, tm.benchTM!!, 1e-9)
        assertEquals(205.0, tm.deadliftTM!!, 1e-9)
        assertEquals(
            "Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 110 → 112,5 kg, peso muerto 200 → 205 kg.",
            userNotices(result.program).single().reason,
        )
    }

    @Test
    fun cycle_close_leaves_an_auge_inserted_deload_block_untouched() {
        val (program, _) = wendlerAtCycleEnd()
        val recipeBlockId = program.macrocycles.first().blocks.single().id
        val (withDeload, deloadBlockId) = BlockTransitionEngine.insertDeloadBlockAfter(program, recipeBlockId)!!
        val deloadBefore = withDeload.macrocycles.first().blocks.first { it.id == deloadBlockId }
        assertNull("la descarga de AUGE no viene de la receta", deloadBefore.sourceDefinitionId)
        val logs = logsFor(withDeload, weeksOf(withDeload))

        val result = ProgramProgressEngine.completeCycle(withDeload, null, 1, logs)

        assertTrue(result.advancedCycle)
        // Sin el filtro por receta, `rematerializeWeek` la mapearía a la semana 1 completa.
        val deloadAfter = result.program.macrocycles.first().blocks.first { it.id == deloadBlockId }
        assertEquals("la descarga insertada por AUGE queda intacta", deloadBefore, deloadAfter)
        // El bloque de la receta sí se reconstruye con el TM nuevo.
        val recipeWeeks = result.program.macrocycles.first().blocks.first { it.id == recipeBlockId }
            .mesocycles.single().weeks
        assertEquals(4, recipeWeeks.size)
        assertEquals(185.0, result.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(0.65 * 185.0, t1Of(recipeWeeks.first(), CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
        assertTrue(result.program.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
    }

    @Test
    fun cycle_close_keeps_the_days_the_athlete_moved_by_dragging_a_session() {
        val (program, logs) = wendlerAtCycleEnd()
        val dragged = weeksOf(program).first().sessions[0] // sentadilla de la semana 1: lunes
        assertEquals(1, dragged.dayOfWeek)
        val moved = program.mapSession(dragged.id) { it.copy(dayOfWeek = 3, assignedDays = listOf(3)) }
        assertTrue("arrastrar una sesión no la congela", moved.manualSessionOverrides.isEmpty())

        val result = ProgramProgressEngine.completeCycle(moved, null, 1, logs)

        val after = ProgramHierarchyIndex(result.program).locateSession(dragged.id)!!.session
        assertEquals("el día que eligió el atleta se conserva", 3, after.dayOfWeek)
        assertEquals(listOf(3), after.assignedDays)
        // No estaba congelada: su contenido sí toma el TM nuevo.
        val t1 = after.allExercises().first { it.slotRole == SlotRole.T1_MAIN }
        assertEquals(0.65 * 185.0, t1.sets.first().weight!!, 1e-6)
        // El resto de la semana conserva los días de la receta (lun → mié, mar, jue, vie).
        assertEquals(listOf(3, 2, 4, 5), weeksOf(result.program).first().sessions.map { it.dayOfWeek })
        assertTrue(result.program.manualSessionOverrides.isEmpty())
    }

    @Test
    fun two_consecutive_cycle_closes_raise_the_tm_twice_and_never_carry_a_scaled_week_over() {
        val (program, logs1) = wendlerAtCycleEnd()
        val first = ProgramProgressEngine.completeCycle(program, null, 1, logs1)
        assertEquals(2, first.program.runState?.cycleNumber)

        // Durante el ciclo 2 una propuesta de intensidad aceptada escala la semana 1 (receta efectiva del ciclo 2).
        val recipe = first.program.sourceRecipe!!
        val week1Id = weeksOf(first.program).first().id
        val withScaled = PlanMaterializer.withEffectiveWeekRecipe(
            program = first.program,
            weekOccurrence = 1,
            cycleNumber = 2,
            weekRecipe = PlanMaterializer.scaleWeekRecipe(recipe.weeks.first(), intensityScale = 0.9, volumeFactor = 1.0),
            applied = emptyList(),
        )
        val inCycleTwo = PlanMaterializer.rematerializeWeek(withScaled, week1Id, recipe, CatalogCompositionTestSupport.metadata)
        assertEquals(
            "durante el ciclo 2 la semana 1 sí va escalada",
            0.65 * 0.9 * 185.0,
            t1Of(weeksOf(inCycleTwo).first(), CatalogIds.SQ_LOW).sets.first().weight!!,
            1e-6,
        )

        // Se entrena todo el ciclo 2 y se cierra.
        val logs2 = logsFor(inCycleTwo, weeksOf(inCycleTwo), cycle = 2, runId = "run_531")
        val second = ProgramProgressEngine.completeCycle(inCycleTwo, null, 2, logs1 + logs2)

        assertTrue(second.advancedCycle)
        assertEquals(3, second.program.runState?.cycleNumber)
        val tm = second.program.powerliftingProfile!!
        assertEquals(190.0, tm.squatTM!!, 1e-9)
        assertEquals(113.0, tm.benchTM!!, 1e-9)
        assertEquals(208.0, tm.deadliftTM!!, 1e-9)
        // Dos avisos, uno por cierre; el segundo parte del TM ya subido.
        val notices = userNotices(second.program)
        assertEquals(listOf("author-cycle-c2", "author-cycle-c3"), notices.map { it.proposalId })
        assertEquals(
            "Nuevo ciclo: TM sentadilla 185 → 190 kg, banca 110,5 → 113 kg, peso muerto 203 → 208 kg.",
            notices.last().reason,
        )
        assertEquals(1, appliedProposals(second.program, "author-cycle-c2").size)
        val markers = appliedProposals(second.program, "author-cycle-c3")
        assertEquals(1, markers.size)
        assertEquals(3, markers.single().first.cycleNumber)
        // El ciclo 3 se reconstruye con la receta base: la semana escalada del ciclo 2 no se arrastra.
        assertEquals(0.65 * 190.0, t1Of(weeksOf(second.program).first(), CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
    }

    @Test
    fun a_program_without_a_source_recipe_does_nothing_at_cycle_close() {
        val withoutRecipe = baseProgram(nativeProgression = false).copy(
            sourceRecipe = null,
            powerliftingProfile = PowerliftingProfile(squat1RM = 200.0, squatTM = 180.0),
        )

        val result = ProgramProgressEngine.completeCycle(withoutRecipe, null, 1, cycle1Logs())

        assertTrue(result.advancedCycle)
        assertEquals(withoutRecipe.powerliftingProfile, result.program.powerliftingProfile)
        assertTrue(result.program.effectiveWeekRecipes.isEmpty())
        assertTrue(userNotices(result.program).isEmpty())
    }

    @Test
    fun a_non_repeating_recipe_finishes_without_raising_the_tm() {
        val (program, logs) = wendlerAtCycleEnd(wendlerRecipe().copy(repeats = false))
        assertEquals(SimpleProgramKind.LINEAR, program.simpleProgramKind)
        val lastWeek = weeksOf(program).last()

        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program.copy(
                runState = ProgramRunState(runId = "run_531", weekId = lastWeek.id, weekInstanceId = lastWeek.id),
            ),
            activeState = ActiveProgramState(programId = program.id, status = ProgramStatus.ACTIVE),
            completedSession = lastWeek.sessions.last(),
            weekInstanceId = lastWeek.id,
            logs = logs,
        )

        assertEquals(ProgramRunStatus.COMPLETED, result.program.runState?.status)
        assertEquals("terminar no sube el TM", program.powerliftingProfile, result.program.powerliftingProfile)
        assertTrue(userNotices(result.program).isEmpty())
        assertTrue(result.program.effectiveWeekRecipes.isEmpty())
    }

    // ─── B.S3: CycleIncrement por bloque (olas) ───────────────────────────────────

    /** Dos olas de dos semanas con sentadilla y banca en el T1. */
    private fun waveRecipe(scope: IncrementScope): TrainingPlanRecipe {
        fun waveDay() = day(
            "Día A",
            weekday = 1,
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 70.0, 5 to 70.0), 180, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, percentSets(180, 5 to 70.0, 5 to 70.0), 180, LiftSlot.BENCH, isCompetitionLift = true),
            ),
        )
        return TrainingPlanRecipe(
            id = "waves",
            weeks = listOf(
                weekRecipe(1, 0, "Ola 1", BlockGoal.ACCUMULATION, listOf(waveDay())),
                weekRecipe(2, 0, "Ola 1", BlockGoal.ACCUMULATION, listOf(waveDay())),
                weekRecipe(3, 1, "Ola 2", BlockGoal.INTENSIFICATION, listOf(waveDay())),
                weekRecipe(4, 1, "Ola 2", BlockGoal.INTENSIFICATION, listOf(waveDay())),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP),
            progression = ProgressionRule.CycleIncrement(2.5, 5.0, scope),
        )
    }

    private fun waveProgram(scope: IncrementScope): Program = PlanMaterializer.materialize(
        Program(id = "waves", name = "Olas"),
        waveRecipe(scope),
        CatalogCompositionTestSupport.metadata,
        SeqIds(),
        profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0),
        strict = false,
    )

    /**
     * Completa la semana [completedWeekIndex] (con todas las anteriores entrenadas) y avanza el cursor.
     * [extraLogs] añade registros sueltos al historial (p. ej. una sesión entrenada por adelantado).
     */
    private fun advanceWave(
        program: Program,
        completedWeekIndex: Int,
        extraLogs: List<WorkoutLog> = emptyList(),
    ): ProgramProgressEngine.ProgressAdvanceResult {
        val weeks = weeksOf(program)
        val completed = weeks[completedWeekIndex]
        val withRun = program.copy(
            runState = ProgramRunState(
                runId = "run_waves",
                cycleNumber = 1,
                weekId = completed.id,
                weekInstanceId = completed.id,
            ),
        )
        return ProgramProgressEngine.advanceAfterSessionComplete(
            program = withRun,
            activeState = null,
            completedSession = completed.sessions.last(),
            weekInstanceId = completed.id,
            logs = logsFor(program, weeks.take(completedWeekIndex + 1)) + extraLogs,
        )
    }

    @Test
    fun block_scoped_increment_applies_between_waves_only() {
        val program = waveProgram(IncrementScope.BLOCK)
        assertEquals(ProgramStructure.COMPLEX, program.structure)
        fun squatKg(target: Program, weekIndex: Int) = t1Of(weeksOf(target)[weekIndex], CatalogIds.SQ_LOW).sets.first().weight!!
        // Antes de empezar: el 70 % del TM 180 en las cuatro semanas.
        (0..3).forEach { assertEquals("semana ${it + 1}", 126.0, squatKg(program, it), 1e-6) }

        // Semana 1 de la ola 1: dentro de la ola no sube nada.
        val insideWave = advanceWave(program, 0)
        assertTrue(insideWave.advancedWeek)
        assertEquals(180.0, insideWave.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertTrue(userNotices(insideWave.program).isEmpty())
        assertTrue(appliedProposals(insideWave.program, "author-block-b1").isEmpty())

        // Semana 2, la última de la ola 1: al entrar en la ola 2 el TM sube +5 (sentadilla) y +2,5 (banca).
        val enteringWave2 = advanceWave(insideWave.program, 1)
        assertTrue(enteringWave2.advancedWeek)
        val tm = enteringWave2.program.powerliftingProfile!!
        assertEquals(185.0, tm.squatTM!!, 1e-9)
        assertEquals(110.5, tm.benchTM!!, 1e-9)
        // La ola 1 (ya entrenada) conserva su carga; la ola 2 toma el TM nuevo.
        assertEquals(126.0, squatKg(enteringWave2.program, 0), 1e-6)
        assertEquals(126.0, squatKg(enteringWave2.program, 1), 1e-6)
        assertEquals(0.70 * 185.0, squatKg(enteringWave2.program, 2), 1e-6)
        assertEquals(0.70 * 185.0, squatKg(enteringWave2.program, 3), 1e-6)
        val notice = userNotices(enteringWave2.program).single()
        assertEquals("author-block-b1", notice.proposalId)
        assertEquals("Nuevo bloque: TM sentadilla 180 → 185 kg, banca 108 → 110,5 kg.", notice.reason)
        val markers = appliedProposals(enteringWave2.program, "author-block-b1")
        assertEquals(1, markers.size)
        assertEquals("AUTHOR_BLOCK_INCREMENT", markers.single().second.kind)

        // Idempotente: entrar de nuevo en el mismo bloque no vuelve a subir el TM.
        val wave2Id = program.macrocycles.first().blocks[1].id
        val again = AuthoredProgressionEngine.applyAtBlockClose(
            enteringWave2.program,
            wave2Id,
            CatalogCompositionTestSupport.metadata,
            inventory = null,
        )
        assertEquals(enteringWave2.program, again)

        // Semana 3 (dentro de la ola 2) y semana 4 (fin del programa): no hay más subidas.
        val insideWave2 = advanceWave(enteringWave2.program, 2)
        assertEquals(185.0, insideWave2.program.powerliftingProfile!!.squatTM!!, 1e-9)
        val finished = advanceWave(insideWave2.program, 3)
        assertEquals(185.0, finished.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(1, userNotices(finished.program).size)
    }

    @Test
    fun block_scoped_increment_never_rebuilds_a_session_that_already_has_logs() {
        val program = waveProgram(IncrementScope.BLOCK)
        val weeks = weeksOf(program)
        // La primera semana de la ola 2 se entrenó por adelantado: su sesión ya tiene un registro.
        val trainedEarly = weeks[2].sessions.single()
        val earlyLog = WorkoutLog(
            id = "early-log",
            programId = program.id,
            sessionId = trainedEarly.id,
            sessionName = trainedEarly.name,
            date = "2026-01-01T10:00:00.000Z",
            durationMinutes = 45,
            weekId = weeks[2].id,
            cycleNumber = 1,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, weeks[2].id),
        )

        val enteringWave2 = advanceWave(advanceWave(program, 0).program, 1, extraLogs = listOf(earlyLog))

        fun squatKg(weekIndex: Int) = t1Of(weeksOf(enteringWave2.program)[weekIndex], CatalogIds.SQ_LOW).sets.first().weight!!
        assertEquals(185.0, enteringWave2.program.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals("la sesión ya entrenada conserva su prescripción", 126.0, squatKg(2), 1e-6)
        assertEquals("la que queda por entrenar toma el TM nuevo", 0.70 * 185.0, squatKg(3), 1e-6)
    }

    @Test
    fun a_cycle_scoped_rule_does_not_raise_the_tm_between_blocks() {
        val program = waveProgram(IncrementScope.CYCLE)

        val enteringWave2 = advanceWave(advanceWave(program, 0).program, 1)

        assertTrue(enteringWave2.advancedWeek)
        assertEquals(program.powerliftingProfile, enteringWave2.program.powerliftingProfile)
        assertTrue(userNotices(enteringWave2.program).isEmpty())
        assertNotNull(enteringWave2.program.runState)
    }

    @Test
    fun block_scoped_increment_skips_an_auge_deload_and_rebuilds_only_the_recipe_weeks() {
        val program = waveProgram(IncrementScope.BLOCK)
        val blocks = program.macrocycles.first().blocks
        val (withDeload, deloadBlockId) = BlockTransitionEngine.insertDeloadBlockAfter(program, blocks[0].id)!!
        val deloadBefore = withDeload.macrocycles.first().blocks.first { it.id == deloadBlockId }
        val metadata = CatalogCompositionTestSupport.metadata

        // Entrar en la descarga de AUGE no es entrar en un bloque de la receta: el TM no sube.
        val enteringDeload = AuthoredProgressionEngine.applyAtBlockClose(withDeload, deloadBlockId, metadata, inventory = null)
        assertEquals(withDeload, enteringDeload)

        // Entrar en la ola 2 después de la descarga sí sube el TM, sin tocar la descarga.
        val wave2Id = blocks[1].id
        val enteringWave2 = AuthoredProgressionEngine.applyAtBlockClose(withDeload, wave2Id, metadata, inventory = null)
        assertEquals(185.0, enteringWave2.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(deloadBefore, enteringWave2.macrocycles.first().blocks.first { it.id == deloadBlockId })
        val wave2Weeks = enteringWave2.macrocycles.first().blocks.first { it.id == wave2Id }.mesocycles.flatMap { it.weeks }
        assertEquals(2, wave2Weeks.size)
        wave2Weeks.forEach { week ->
            assertEquals(0.70 * 185.0, t1Of(week, CatalogIds.SQ_LOW).sets.first().weight!!, 1e-6)
        }
        // El índice del bloque cuenta la descarga insertada: ola 1 = 0, descarga = 1, ola 2 = 2.
        assertEquals(1, appliedProposals(enteringWave2, "author-block-b2").size)
    }
}
