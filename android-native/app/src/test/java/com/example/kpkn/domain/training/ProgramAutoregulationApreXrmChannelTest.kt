package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Flujos APRE desde registros reales, con referencias físicas y sin tratar el XRM como TM/1RM. */
class ProgramAutoregulationApreXrmChannelTest {
    companion object {
        @BeforeClass @JvmStatic fun setUp() = CatalogCompositionTestSupport.install()
    }

    private val recipe = Cal5TestSupport.recipeOf("apre-mann")

    private fun program(mode: AutoregulationMode, id: String): Program =
        Cal5TestSupport.materialize(recipe, mode, Cal5TestSupport.fullProfile(), id)

    private fun recordedLogs(
        program: Program,
        week: ProgramWeek,
        onlyExerciseId: String? = null,
        set4LoadKg: Double = 155.0,
        set4Reps: Int = 17,
        set3Only: Boolean = false,
        runId: String? = null,
    ): List<WorkoutLog> = week.sessions.flatMap { it.withVariants() }.mapNotNull { session ->
        val exercises = session.allExercises().filter {
            (onlyExerciseId == null || it.id == onlyExerciseId) &&
                resolveLiveApreBlock(it, recipe.id, hasApreProgression = true) != null
        }
        if (exercises.isEmpty()) return@mapNotNull null
        val completed = exercises.map { exercise ->
            val base = Cal5TestSupport.exerciseLog(
                program, week, session, exercise, runId = runId,
                weightOf = { index, planned -> if (index == 3) set4LoadKg else planned.weight },
                repsOf = { index, planned -> when (index) {
                    2 -> 12
                    3 -> set4Reps
                    else -> planned.targetReps ?: 5
                } },
            ).completedExercises.single()
            val sets = base.sets.mapIndexed { index, performed ->
                performed.copy(id = exercise.id + "-event-" + index, setIndex = index)
            }
            base.copy(sets = if (set3Only) listOf(sets[2]) else sets)
        }
        Cal5TestSupport.exerciseLog(program, week, session, exercises.first(), runId = runId).copy(
            id = "log-" + program.id + "-" + week.id + "-" + session.id,
            completedExercises = completed,
        )
    }

    private fun Session.withVariants(): List<Session> = buildList {
        add(this@withVariants)
        sessionB?.let { addAll(it.withVariants()) }
        sessionC?.let { addAll(it.withVariants()) }
        sessionD?.let { addAll(it.withVariants()) }
    }

    private fun Program.replacingWeek(replacement: ProgramWeek): Program = copy(
        macrocycles = macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { mesocycle ->
                mesocycle.copy(weeks = mesocycle.weeks.map { week ->
                    if (week.id == replacement.id) replacement else week
                })
            })
        }) },
    )

    private fun Program.reducingToWeek(weekId: String): Program = copy(
        macrocycles = macrocycles.mapNotNull { macro ->
            val blocks = macro.blocks.mapNotNull { block ->
                val mesocycles = block.mesocycles.mapNotNull { mesocycle ->
                    val weeks = mesocycle.weeks.filter { it.id == weekId }
                    mesocycle.copy(weeks = weeks).takeIf { weeks.isNotEmpty() }
                }
                block.copy(mesocycles = mesocycles).takeIf { mesocycles.isNotEmpty() }
            }
            macro.copy(blocks = blocks).takeIf { blocks.isNotEmpty() }
        },
    )

    private fun xrmHistory(program: Program, exerciseId: String, proposal: AutoregulationProposal) =
        ExerciseLoadReferenceStore.history(program, exerciseId, proposal.apreXrm!!.reference)
            .filter { it.metadata?.sourceId == proposal.apreXrm!!.reference.metadata?.sourceId }

    @Test
    fun collector_prefers_a_current_material_capture_and_rejects_one_from_different_reps() {
        val plan = program(AutoregulationMode.AUTO, "apre-material-increment")
        val week = Cal5TestSupport.weeksOf(plan).first()
        val exercise = Cal5TestSupport.exerciseOf(week.sessions.first(), CatalogIds.SQ_LOW)
        val logs = recordedLogs(plan, week, onlyExerciseId = exercise.id)
        val sourceLog = logs.single()
        val sourceExercise = sourceLog.completedExercises.single()
        val sourceS4 = sourceExercise.sets.single { it.setIndex == 3 }
        val capture = captureApreXrm(
            exercise, 3, sourceS4, sourceProtocolId = recipe.id, hasApreProgression = true,
            incrementKg = 5.0, capturedAtMs = 123L, sourceProgramId = plan.id,
            contextOverride = plannedApreContext(exercise, exercise.sets[3], sourceS4.side, sourceS4),
        )!!
        val capturedLogs = logs.map { log ->
            log.copy(completedExercises = log.completedExercises.map { completedExercise ->
                completedExercise.copy(sets = completedExercise.sets.map {
                    if (it.id == sourceS4.id) it.copy(apreXrm = capture) else it
                })
            })
        }

        val recipeFallback = ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, week, logs).single()
        val proposal = ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, week, capturedLogs).single()
        assertEquals(162.5, recipeFallback.apreXrm!!.reference.capturedLoadKg!!, 1e-9)
        assertEquals(5.0, proposal.apreXrm!!.incrementKg, 0.0)
        assertEquals(160.0, proposal.apreXrm!!.reference.capturedLoadKg!!, 1e-9)

        val staleLogs = capturedLogs.map { log ->
            log.copy(completedExercises = log.completedExercises.map { completedExercise ->
                completedExercise.copy(sets = completedExercise.sets.map {
                    if (it.id == sourceS4.id) it.copy(reps = 18, apreXrm = capture) else it
                })
            })
        }
        assertTrue(ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, week, staleLogs).isEmpty())
    }

    @Test
    fun real_s4_without_source_recipe_applies_physical_xrm_and_rematerializes_from_pool() {
        val manual = program(AutoregulationMode.AUTO, "apre-manual").copy(
            sourceRecipe = null, sourceProtocolId = null, powerliftingProfile = null,
        )
        val weeks = Cal5TestSupport.weeksOf(manual)
        val sourceWeek = weeks.first()
        val sourceSquat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        val proposals = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            manual, sourceWeek, recordedLogs(manual, sourceWeek),
        )
        val squatProposal = proposals.single { it.apreXrm!!.sourceExerciseId == sourceSquat.id }
        assertEquals(AutoregulationProposalKind.ADJUST_XRM, squatProposal.kind)
        assertEquals(162.5, squatProposal.apreXrm!!.reference.capturedLoadKg!!, 1e-6)
        assertEquals(manual.id, squatProposal.apreXrm!!.reference.sourceProgramId)
        assertNull("sin fecha en el log, la captura conserva fecha desconocida",
            squatProposal.apreXrm!!.reference.capturedAtMs)

        val targetWeek = weeks[1]
        val applied = ProgramAutoregulationEngine.apply(
            program = manual, nextWeekId = targetWeek.id, proposals = proposals, recipe = null,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 100L,
        )
        assertTrue(applied.applied)
        assertNull("XRM no crea perfil TM/1RM", applied.program.powerliftingProfile)
        val updatedTarget = Cal5TestSupport.weeksOf(applied.program)[1]
        val targetSquat = Cal5TestSupport.exerciseOf(updatedTarget.sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, targetSquat.sets[2].weight!!, 1e-6)
        assertEquals(162.5, targetSquat.sets[3].weight!!, 1e-6)
        assertEquals(81.25, targetSquat.sets[0].weight!!, 1e-6)
        assertEquals(1, xrmHistory(applied.program, sourceSquat.id, squatProposal).size)
        assertEquals(1, xrmHistory(applied.program, targetSquat.id, squatProposal).size)

        val rematerialized = PlanMaterializer.rematerializeWeek(
            program = applied.program, weekId = updatedTarget.id, recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata, idProvider = Cal5TestSupport.SeqIds(),
            executedWeekIds = setOf(sourceWeek.id),
        )
        val rematerializedSquat =
            Cal5TestSupport.exerciseOf(Cal5TestSupport.weeksOf(rematerialized)[1].sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, rematerializedSquat.sets[3].weight!!, 1e-6)
    }

    @Test
    fun replaced_catalog_configuration_with_same_occurrence_ids_does_not_relabel_apre_capture() {
        val plan = program(AutoregulationMode.AUTO, "apre-variant-replaced").copy(
            sourceRecipe = null, sourceProtocolId = null,
        )
        val originalWeek = Cal5TestSupport.weeksOf(plan).first()
        val originalSquat = Cal5TestSupport.exerciseOf(originalWeek.sessions.first(), CatalogIds.SQ_LOW)
        val canonicalExerciseId = originalSquat.exerciseDbId ?: "canonical-squat"
        val sourceWeek = originalWeek.copy(sessions = originalWeek.sessions.map { session ->
            fun withCanonical(exercise: com.example.kpkn.data.models.Exercise) =
                if (exercise.id == originalSquat.id) exercise.copy(exerciseDbId = canonicalExerciseId) else exercise
            session.copy(
                exercises = session.exercises.map(::withCanonical),
                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::withCanonical)) },
            )
        })
        val sourceSquat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        val logsWithoutSnapshot = recordedLogs(plan, sourceWeek, sourceSquat.id)
        val captureFromA = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            plan, sourceWeek, logsWithoutSnapshot,
        ).single().apreXrm!!
        val logsWithSnapshot = logsWithoutSnapshot.map { log ->
            log.copy(completedExercises = log.completedExercises.map { completed ->
                if (completed.exerciseId != sourceSquat.id) completed else completed.copy(
                    sets = completed.sets.mapIndexed { index, set ->
                        if (index == 3) set.copy(apreXrm = captureFromA) else set
                    },
                )
            })
        }
        val replacementConfiguration = (sourceSquat.catalogConfigurationId ?: "config") + "-replaced"
        val replacedWeek = sourceWeek.copy(sessions = sourceWeek.sessions.map { session ->
            fun replace(exercise: com.example.kpkn.data.models.Exercise) =
                if (exercise.id == sourceSquat.id) exercise.copy(catalogConfigurationId = replacementConfiguration)
                else exercise
            session.copy(
                exercises = session.exercises.map(::replace),
                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::replace)) },
            )
        })
        val replacement = Cal5TestSupport.exerciseOf(
            replacedWeek.sessions.first(), replacementConfiguration,
        )
        assertEquals(sourceSquat.id, replacement.id)
        assertEquals(sourceSquat.recipeSlotId, replacement.recipeSlotId)
        assertEquals(canonicalExerciseId, sourceSquat.exerciseDbId)
        assertEquals(canonicalExerciseId, replacement.exerciseDbId)
        assertEquals(sourceSquat.sets.map { it.id }, replacement.sets.map { it.id })

        assertTrue(
            "el log sin snapshot conserva la configuración A y no debe producir XRM para B",
            ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, replacedWeek, logsWithoutSnapshot).isEmpty(),
        )
        assertTrue(
            "un snapshot S4 de A tampoco puede recapturarse con el contexto B",
            ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, replacedWeek, logsWithSnapshot).isEmpty(),
        )
    }

    @Test
    fun apre_collection_and_application_keep_session_variant_identity() {
        val plan = program(AutoregulationMode.AUTO, "apre-session-variant").copy(
            sourceRecipe = null, sourceProtocolId = null,
        )
        val sourceWeek = Cal5TestSupport.weeksOf(plan).first()
        val sourceBase = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        fun variant(exercise: com.example.kpkn.data.models.Exercise, suffix: String) = exercise.copy(
            id = "${exercise.id}-$suffix",
            sets = exercise.sets.map { set -> set.copy(id = "${set.id}-$suffix") },
        ).let { changed ->
            changed.copy(apreBlock = changed.apreBlock?.copy(setIds = changed.sets.map { it.id }))
        }
        val sourceB = variant(sourceBase, "source-b").copy(
            recipeDayId = "apre-day",
            recipeSlotId = "apre-squat-slot",
        )
        val sourceBSession = Session(id = "${sourceWeek.sessions.first().id}-source-b", name = "Día B", exercises = listOf(sourceB))
        val sourceWeekWithB = sourceWeek.copy(sessions = sourceWeek.sessions.map { session ->
            if (session.id == sourceWeek.sessions.first().id) session.copy(sessionB = sourceBSession) else session
        })
        // WorkoutFinishController almacena la variante activa dentro de la sesión base A.
        val sourceLogsB = recordedLogs(plan, sourceWeekWithB, onlyExerciseId = sourceB.id)
            .map { it.copy(sessionId = sourceWeekWithB.sessions.first().id) }
        val sourceHits = ProgramAutoregulationEngine.collectAmrapHits(sourceWeekWithB, sourceLogsB)
            .filter { it.isApreBlock && it.apreXrmCapture != null }
        assertEquals(listOf(sourceB.id), sourceHits.map { it.sourceExerciseId })
        val proposal = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            plan, sourceWeekWithB, sourceLogsB,
        ).single()
        assertEquals(sourceB.id, proposal.apreXrm!!.sourceExerciseId)

        val targetWeek = Cal5TestSupport.weeksOf(plan)[1]
        val targetBase = Cal5TestSupport.exerciseOf(targetWeek.sessions.first(), CatalogIds.SQ_LOW)
        val targetB = variant(targetBase, "target-b").copy(
            recipeDayId = sourceB.recipeDayId,
            recipeSlotId = sourceB.recipeSlotId,
        )
        val targetBSession = Session(id = "${targetWeek.sessions.first().id}-target-b", name = "Día B", exercises = listOf(targetB))
        val targetOuter = targetWeek.sessions.first()
        val isolatedA = targetWeek.copy(sessions = targetWeek.sessions.map { session ->
            if (session.id != targetOuter.id) session else session.copy(
                exercises = session.exercises.map { exercise ->
                    if (exercise.id == targetBase.id) exercise.copy(recipeSlotId = "${exercise.recipeSlotId}-A") else exercise
                },
                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map { exercise ->
                    if (exercise.id == targetBase.id) exercise.copy(recipeSlotId = "${exercise.recipeSlotId}-A") else exercise
                }) },
                sessionB = targetBSession,
            )
        })
        val withVariant = plan.replacingWeek(isolatedA)

        val applied = ProgramAutoregulationEngine.apply(
            program = withVariant,
            nextWeekId = targetWeek.id,
            proposals = listOf(proposal),
            recipe = null,
            executedWeekIds = setOf(sourceWeek.id),
            nowMs = 150L,
        )
        val resultWeek = Cal5TestSupport.weeksOf(applied.program).first { it.id == targetWeek.id }
        val resultB = resultWeek.sessions.first { it.sessionB?.id == targetBSession.id }.sessionB!!.exercises.single()
        val resultA = Cal5TestSupport.exerciseOf(resultWeek.sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, resultB.sets[3].weight!!, 1e-6)
        assertEquals(targetBase.sets[3].weight, resultA.sets[3].weight)
        assertTrue(ExerciseLoadReferenceStore.history(applied.program, targetB.id, proposal.apreXrm!!.reference).isNotEmpty())

        val protected = ProgramAutoregulationEngine.apply(
            program = withVariant,
            nextWeekId = targetWeek.id,
            proposals = listOf(proposal),
            recipe = null,
            executedWeekIds = setOf(sourceWeek.id),
            executedSessionIds = setOf(targetBSession.id),
            nowMs = 151L,
        )
        val protectedWeek = Cal5TestSupport.weeksOf(protected.program).first { it.id == targetWeek.id }
        val protectedB = protectedWeek.sessions.first { it.sessionB?.id == targetBSession.id }.sessionB!!.exercises.single()
        assertEquals(targetB.sets[3].weight, protectedB.sets[3].weight)
    }

    @Test
    fun closing_a_single_week_cycle_uses_cycle_one_s4_for_the_reused_week_in_cycle_two() {
        fun close(mode: AutoregulationMode): Pair<com.example.kpkn.domain.training.ProgressAdvanceResult, Double?> {
            val base = program(mode, "apre-cycle-close-${mode.name.lowercase()}").copy(
                sourceRecipe = null,
                sourceProtocolId = null,
                structure = com.example.kpkn.data.models.ProgramStructure.SIMPLE,
                simpleProgramKind = com.example.kpkn.data.models.SimpleProgramKind.CYCLIC,
            )
            val templateWeek = Cal5TestSupport.weeksOf(base).first()
            val cyclic = base.reducingToWeek(templateWeek.id)
            val week = Cal5TestSupport.weeksOf(cyclic).single()
            val runId = "run-close-${mode.name.lowercase()}"
            val instanceId = ProgramProgressEngine.instanceIdFor(1, week.id)
            val running = cyclic.copy(runState = ProgramRunState(
                runId = runId,
                cycleNumber = 1,
                weekInstanceId = instanceId,
                weekId = week.id,
            ))
            val emptyLogs = Cal5TestSupport.emptyLogs(running, listOf(week), cycle = 1, runId = runId)
            val squat = Cal5TestSupport.exerciseOf(week.sessions.first(), CatalogIds.SQ_LOW)
            val actualS4 = recordedLogs(running, week, onlyExerciseId = squat.id, runId = runId)
            val allLogs = emptyLogs.map { blank ->
                actualS4.firstOrNull { it.sessionId == blank.sessionId } ?: blank
            }
            val startingLoad = Cal5TestSupport.exerciseOf(week.sessions.first(), CatalogIds.SQ_LOW).sets[3].weight
            val result = ProgramProgressEngine.advanceAfterSessionComplete(
                program = running,
                activeState = null,
                completedSession = week.sessions.last(),
                weekInstanceId = instanceId,
                logs = allLogs,
            )
            return result to startingLoad
        }

        val (auto, _) = close(AutoregulationMode.AUTO)
        assertTrue(auto.advancedCycle)
        assertEquals(2, auto.program.runState!!.cycleNumber)
        assertEquals(1, auto.autoregulationProposals.count { it.kind == AutoregulationProposalKind.ADJUST_XRM })
        val reusedWeek = Cal5TestSupport.weeksOf(auto.program).single()
        val appliedSquat = Cal5TestSupport.exerciseOf(reusedWeek.sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, appliedSquat.sets[3].weight!!, 1e-6)
        assertTrue(auto.program.runState!!.autoregulationAudit.any {
            it.kinds.contains(AutoregulationProposalKind.ADJUST_XRM) &&
                it.resolution == PendingActionResolutionStatus.APPLIED
        })

        val (propose, proposeStartingLoad) = close(AutoregulationMode.PROPOSE)
        assertEquals(2, propose.program.runState!!.cycleNumber)
        assertEquals(AutoregulationProposalKind.ADJUST_XRM, propose.program.runState!!.pendingAction!!.proposals.single().kind)
        assertTrue(propose.program.exerciseLoadReferences.isEmpty())
        val proposedWeek = Cal5TestSupport.weeksOf(propose.program).single()
        assertEquals(
            proposeStartingLoad,
            Cal5TestSupport.exerciseOf(proposedWeek.sessions.first(), CatalogIds.SQ_LOW).sets[3].weight,
        )

        val (off, _) = close(AutoregulationMode.OFF)
        assertTrue(off.advancedCycle)
        assertTrue(off.autoregulationProposals.isEmpty())
        assertNull(off.program.runState!!.pendingAction)
        assertTrue(off.program.exerciseLoadReferences.isEmpty())
    }

    @Test
    fun propose_partial_resolution_updates_message_and_terminal_statuses() {
        val plan = program(AutoregulationMode.PROPOSE, "apre-partial")
        val weeks = Cal5TestSupport.weeksOf(plan)
        val sourceWeek = weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            plan, sourceWeek, recordedLogs(plan, sourceWeek), recipe, WeeklyAutoregulationSignals(),
        )
        assertTrue(proposals.size >= 2)
        val pendingResult = ProgramAutoregulationEngine.apply(
            program = plan, nextWeekId = weeks[1].id, proposals = proposals, recipe = recipe,
            executedWeekIds = setOf(sourceWeek.id), metadata = CatalogCompositionTestSupport.metadata, nowMs = 200L,
        )
        assertFalse(pendingResult.applied)
        val pending = pendingResult.program.runState!!.pendingAction!!
        assertEquals(proposals.size, pending.proposals.size)
        assertTrue(pending.message.contains("APRE"))
        assertTrue(pendingResult.program.exerciseLoadReferences.isEmpty())

        val accepted = proposals.first()
        val remaining = proposals.drop(1)
        val afterAccept = ProgramAutoregulationEngine.resolvePending(
            pendingResult.program, accept = true, only = accepted,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 201L,
        )
        assertEquals(remaining, afterAccept.runState!!.pendingAction!!.proposals)
        assertEquals(remaining.joinToString(" · ") { it.explanation }, afterAccept.runState!!.pendingAction!!.message)
        assertTrue(afterAccept.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.APPLIED
        })

        val rejected = remaining.first()
        val afterReject = ProgramAutoregulationEngine.resolvePending(
            afterAccept, accept = false, only = rejected, nowMs = 202L,
        )
        val stillPending = remaining.drop(1)
        assertEquals(stillPending, afterReject.runState!!.pendingAction?.proposals.orEmpty())
        if (stillPending.isNotEmpty()) assertEquals(
            stillPending.joinToString(" · ") { it.explanation }, afterReject.runState!!.pendingAction!!.message,
        )
        assertTrue(afterReject.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.REJECTED
        })

        val expired = ProgramAutoregulationEngine.expirePending(
            pendingResult.program, "Caducada por avance", nowMs = 203L,
        )
        assertNull(expired.runState!!.pendingAction)
        assertTrue(expired.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.EXPIRED
        })
    }

    @Test
    fun correction_reversion_appends_latest_revision_and_retry_is_idempotent() {
        val base = program(AutoregulationMode.AUTO, "apre-correction").copy(
            sourceRecipe = null, sourceProtocolId = null,
        )
        val weeks = Cal5TestSupport.weeksOf(base)
        val sourceWeek = weeks.first()
        val targetWeekId = weeks[1].id
        val squat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        fun proposal(load: Double, reps: Int) = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            base, sourceWeek, recordedLogs(base, sourceWeek, squat.id, load, reps),
        ).single()

        val first = proposal(155.0, 17)
        val corrected = proposal(155.0, 10)
        val reverted = proposal(155.0, 17)
        var working = ProgramAutoregulationEngine.apply(
            base, targetWeekId, listOf(first), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 300L,
        ).program
        working = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(corrected), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 301L,
        ).program
        working = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(reverted), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 302L,
        ).program
        val history = xrmHistory(working, squat.id, first)
        assertEquals(listOf(0, 1, 2), history.map { it.metadata!!.revision })
        assertEquals(162.5, history.last().capturedLoadKg!!, 1e-6)
        val auditBeforeRetry = working.runState!!.autoregulationAudit
        val retry = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(reverted), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 303L,
        )
        assertEquals(working, retry.program)
        assertEquals(auditBeforeRetry, retry.program.runState!!.autoregulationAudit)
        assertEquals(3, xrmHistory(retry.program, squat.id, reverted).size)
    }

    @Test
    fun marked_apre_sets_and_stale_signals_never_fall_through_to_tm_in_a_mixed_recipe() {
        val plan = program(AutoregulationMode.AUTO, "apre-mixed")
        val week = Cal5TestSupport.weeksOf(plan).first()
        val squat = Cal5TestSupport.exerciseOf(week.sessions.first(), CatalogIds.SQ_LOW)
        val logs = recordedLogs(plan, week)
        val genuine = ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, week, logs)
            .single { it.apreXrm!!.sourceExerciseId == squat.id }
        val otherRule = recipe.copy(progression = ProgressionRule.AmrapDrivenTm())
        val staleSignal = AmrapHit(
            liftSlot = LiftSlot.SQUAT, percent = 90.0, prescribedReps = 10,
            actualReps = 30, weightKg = 200.0, configurationId = CatalogIds.SQ_LOW,
        )
        val signals = WeeklyAutoregulationSignals(amrapHits = listOf(
            staleSignal, staleSignal.copy(apreXrmCapture = genuine.apreXrm, sourceExerciseId = squat.id),
        ))
        val evaluated = ProgramAutoregulationEngine.evaluate(plan, week, logs, otherRule, signals)
        assertEquals(3, evaluated.size)
        assertTrue(evaluated.all { it.kind == AutoregulationProposalKind.ADJUST_XRM })
        assertTrue(evaluated.none { it.kind == AutoregulationProposalKind.ADJUST_TM })

        val onlyS3 = ProgramAutoregulationEngine.evaluate(
            plan, week, recordedLogs(plan, week, set3Only = true), otherRule, signals,
        )
        assertTrue("la S3 mantiene condición, no es XRM ni TM", onlyS3.isEmpty())
    }

    @Test
    fun auto_applies_xrm_and_keeps_mixed_tm_proposal_pending_without_mutating_profile() {
        val plan = program(AutoregulationMode.AUTO, "apre-mixed-auto")
        val weeks = Cal5TestSupport.weeksOf(plan)
        val sourceWeek = weeks.first()
        val squat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        val xrm = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            plan.copy(sourceRecipe = null, sourceProtocolId = null),
            sourceWeek, recordedLogs(plan, sourceWeek, squat.id),
        ).single()
        val tm = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM, liftSlot = LiftSlot.SQUAT.name,
            kgDelta = 2.5, explanation = "Ajustar TM de sentadilla",
        )
        val result = ProgramAutoregulationEngine.apply(
            program = plan, nextWeekId = weeks[1].id, proposals = listOf(xrm, tm), recipe = null,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 400L,
        )
        assertTrue(result.applied)
        assertEquals(plan.powerliftingProfile, result.program.powerliftingProfile)
        assertEquals(listOf(tm), result.program.runState!!.pendingAction!!.proposals)
        assertEquals(tm.explanation, result.program.runState!!.pendingAction!!.message)
        assertTrue(result.program.exerciseLoadReferences.isNotEmpty())
    }
}
