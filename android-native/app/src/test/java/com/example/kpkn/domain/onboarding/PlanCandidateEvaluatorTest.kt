package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.programs.AdaptationPolicy
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.domain.training.SessionDurationEstimator
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T-005 / §15.2 — la evaluación ÚNICA de candidatos: orden de etapas, motivos
 * cerrados, primer rechazo con detalles, propagación de cancelación y errores
 * inesperados como INTERNAL_MATERIALIZATION (nunca `catch (Exception) { false }`).
 */
class PlanCandidateEvaluatorTest {

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun request(
        goal: PlanGoalProfile = PlanGoalProfile.MUSCLE,
        days: Int = 3,
        minutes: Int = 60,
        cardioMinutes: Int? = 15,
        requiresCardio: Boolean = false,
        reference: TrainingReference? = null,
    ) = PlanCandidateRequest(
        inputKey = "input-key",
        goalProfile = goal,
        level = CatalogLevel.INTERMEDIATE,
        focus = TrainingFocus.FULL_BODY,
        reference = reference,
        daysPerWeek = days,
        weekdays = setOf(1, 3, 5),
        minutesPerSession = minutes,
        effectiveEquipment = setOf("general_gym", "bodyweight"),
        cardioMinutes = cardioMinutes,
        requiresCardio = requiresCardio,
        selectedSplitId = null,
        planCatalogRevision = "rev-1",
        exerciseCatalogRevision = "ex-rev-1",
    )

    private fun entry(
        id: String = "native:test",
        frequencies: IntRange = 2..6,
        references: Set<TrainingReference> = setOf(TrainingReference.HYPERTROPHY),
        recipe: TrainingPlanRecipe? = null,
        schedulesCardio: Boolean = false,
        focus: TrainingFocus = TrainingFocus.FULL_BODY,
        capabilities: Set<TrainingCapability> = emptySet(),
    ): CatalogEntry = CatalogEntry(
        id = id,
        source = CatalogSource.NATIVE,
        sourceId = id.substringAfter(":"),
        title = "Plan",
        technicalSubtitle = "Semana cíclica",
        description = "Descripción",
        requiredEquipment = setOf("general_gym"),
        supportedFrequencies = frequencies,
        level = CatalogLevel.INTERMEDIATE,
        duration = CatalogDuration.REPEATING_WEEK,
        supportedFocuses = setOf(focus),
        adaptation = AdaptationPolicy.CURATED_WEEKLY,
        publication = PublicationState.PUBLISHED,
        references = references,
        recipe = recipe,
        capabilities = capabilities,
    ).let { original ->
        // `schedulesCardio` solo lo cumple el nativo strength-cardio (§15.1).
        if (!schedulesCardio) original
        else original.copy(sourceId = "strength-cardio")
    }

    private fun program(
        id: String = "plan-1",
        sessions: Int = 1,
        exercisesPerSession: Int = 2,
        restSeconds: Int = 90,
        loadMode: LoadModeV2? = LoadModeV2.LOAD,
        weight: Double? = 20.0,
    ): Program {
        val exercise = Exercise(
            id = "$id-ex",
            name = "Press de banca",
            sets = (1..3).map { index ->
                ExerciseSet(id = "$id-set-$index", targetReps = 8, weight = weight, loadModeV2 = loadMode)
            },
            restTime = restSeconds,
        )
        val weekSessions = (1..sessions).map { day ->
            Session(
                id = "$id-session-$day",
                name = "Día $day",
                exercises = (1..exercisesPerSession).map { exercise.copy(id = "$id-ex-$day") },
                dayOfWeek = day,
                assignedDays = listOf(day),
            )
        }
        val week = ProgramWeek("$id-week", "Semana", sessions = weekSessions)
        val mesocycle = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
        val block = Block("$id-block", "Bloque", mesocycles = listOf(mesocycle))
        val macrocycle = Macrocycle("$id-macro", "Macro", blocks = listOf(block))
        return Program(
            id = id,
            name = "Plan",
            startDay = 1,
            weekDays = sessions,
            macrocycles = listOf(macrocycle),
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = weekSessions.mapNotNull { it.dayOfWeek }.toSet()),
        )
    }

    private fun programWithSessions(id: String, sessions: List<Session>): Program {
        val week = ProgramWeek("$id-week", "Semana", sessions = sessions)
        val mesocycle = Mesocycle("$id-meso", "Meso", weeks = listOf(week))
        val block = Block("$id-block", "Bloque", mesocycles = listOf(mesocycle))
        return Program(
            id = id,
            name = "Plan",
            startDay = sessions.mapNotNull { it.dayOfWeek }.minOrNull() ?: 1,
            weekDays = sessions.size,
            macrocycles = listOf(Macrocycle("$id-macro", "Macro", blocks = listOf(block))),
            schedulePlan = ProgramSchedulePlan(
                weekStartDay = sessions.mapNotNull { it.dayOfWeek }.minOrNull() ?: 1,
                trainingDays = sessions.mapNotNull { it.dayOfWeek }.toSet(),
            ),
        )
    }

    private fun recipe(
        intents: List<SlotIntent> = listOf(SlotIntent.F, SlotIntent.H),
        speed: Boolean = false,
        cardio: Boolean = false,
    ): TrainingPlanRecipe {
        val slots = intents.mapIndexed { index, intent ->
            SlotRecipe(
                id = "slot-$index",
                role = if (intent == SlotIntent.P) SlotRole.SPEED else SlotRole.T1_MAIN,
                lift = LiftRef("press__barbell"),
                sets = emptyList(),
                restSeconds = 90,
                intent = intent,
            )
        } + if (speed) {
            listOf(
                SlotRecipe(
                    id = "slot-speed",
                    role = SlotRole.SPEED,
                    lift = LiftRef("row__barbell"),
                    sets = emptyList(),
                    restSeconds = 90,
                    intent = SlotIntent.P,
                ),
            )
        } else emptyList()
        val day = DayRecipe(
            label = "Día 1",
            slots = slots,
            cardioBlocks = if (cardio) {
                listOf(RecipeCardioBlock(id = "cardio-1", details = CardioDetails(type = CardioType.WALK)))
            } else emptyList(),
            sessionKind = if (cardio) com.example.kpkn.data.protocols.RecipeSessionKind.STRENGTH_CARDIO
            else com.example.kpkn.data.protocols.RecipeSessionKind.STRENGTH,
        )
        return TrainingPlanRecipe(id = "recipe-1", weeks = listOf(WeekRecipe(weekNumber = 1, blockIndex = 0, days = listOf(day))))
    }

    private fun snapshot(vararg entries: CatalogEntry, exerciseRevision: String? = "ex-rev-1") =
        PlanCatalogSnapshot(entries = entries.toList(), planRevision = "rev-1", exerciseCatalogRevision = exerciseRevision)

    private fun engineOf(outcome: PlanMaterializationOutcome) = PlanMaterializationPort { _, _ -> outcome }

    // ─── Catálogo ───────────────────────────────────────────────────────────

    @Test
    fun catalogLoadingWhenTheExerciseCatalogRevisionIsNotDecodedYet() = runBlockingTest {
        val result = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry(), exerciseRevision = null),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(program())),
        )
        assertTrue("esperaba CatalogLoading, llegó $result", result is PlanCandidateEvaluation.CatalogLoading)
    }

    @Test
    fun catalogSnapshotMustMatchBothRequestRevisionsBeforeMaterializing() = runBlockingTest {
        var materialized = false
        val engine = PlanMaterializationPort { _, _ ->
            materialized = true
            PlanMaterializationOutcome(program())
        }

        val stalePlan = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry()).copy(planRevision = "rev-2"),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue("revisión de planes diferente requiere reintento", stalePlan is PlanCandidateEvaluation.CatalogLoading)

        val staleExercise = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry(), exerciseRevision = "ex-rev-2"),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue("revisión de ejercicios diferente requiere reintento", staleExercise is PlanCandidateEvaluation.CatalogLoading)
        assertFalse("nunca se materializa contra snapshots obsoletos", materialized)
    }

    @Test
    fun unknownPlanIdIsRejectedAsCatalogNotReadyForThatCandidate() = runBlockingTest {
        val result = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry()),
            entryId = "native:otro",
            engine = engineOf(PlanMaterializationOutcome(program())),
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanRejectionReason.CATALOG_NOT_READY, result.reasonCode)
        assertEquals("native:otro", result.planId)
    }

    // ─── Orden de etapas (§15.2) ────────────────────────────────────────────

    @Test
    fun profileCapabilityIsCheckedBeforeFrequencyAndListsEveryMissingComponent() = runBlockingTest {
        // Atleta completo: receta SIN potencia ni cardio → primer rechazo en PERFIL.
        val result = PlanCandidateEvaluator.evaluate(
            request(goal = PlanGoalProfile.COMPLETE_ATHLETE, days = 9),
            snapshot(entry(frequencies = 2..6, recipe = recipe())),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(program())),
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.PROFILE, result.stage)
        assertEquals(PlanRejectionReason.PROFILE_MISMATCH, result.reasonCode)
        assertEquals(listOf("power", "cardio"), result.missingCapabilities)
    }

    @Test
    fun frequencyRejectsWhenTheDaysAreOutsideTheSupportedRange() = runBlockingTest {
        val result = PlanCandidateEvaluator.evaluate(
            request(goal = PlanGoalProfile.MUSCLE, days = 6),
            snapshot(entry(frequencies = 2..3)),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(program())),
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, result.stage)
        assertEquals(PlanRejectionReason.FREQUENCY, result.reasonCode)
        assertTrue(result.details!!.contains("2..3"))
    }

    @Test
    fun engineMaterializationFailuresKeepTheirTypedStageAndReason() = runBlockingTest {
        val engine = PlanMaterializationPort { _, _ ->
            throw PlanMaterializationException(
                PlanEvaluationStage.MATERIAL,
                PlanRejectionReason.APPARATUS_UNKNOWN,
                "Falta confirmar hack squat",
            )
        }
        val result = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry()),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.MATERIAL, result.stage)
        assertEquals(PlanRejectionReason.APPARATUS_UNKNOWN, result.reasonCode)
        assertEquals("Falta confirmar hack squat", result.details)
        assertTrue("sin lista en la excepción no se inventan requisitos", result.missingRequirements.isEmpty())
    }

    /** Paquete A · B1: los requisitos de material de la excepción tipada llegan al rechazo sin tocar el texto. */
    @Test
    fun typedMaterialFailuresCarryTheirMissingRequirementsIntoTheRejection() = runBlockingTest {
        listOf(
            PlanRejectionReason.APPARATUS_UNKNOWN to listOf("rack", "bench"),
            PlanRejectionReason.APPARATUS_ABSENT to listOf("barbell"),
        ).forEach { (reason, requirements) ->
            val engine = PlanMaterializationPort { _, _ ->
                throw PlanMaterializationException(
                    PlanEvaluationStage.MATERIAL,
                    reason,
                    "mensaje de usuario sin tokens",
                    missingRequirements = requirements,
                )
            }
            val result = PlanCandidateEvaluator.evaluate(
                request(),
                snapshot(entry()),
                entryId = "native:test",
                engine = engine,
            )
            assertTrue(result is PlanCandidateEvaluation.Rejected)
            result as PlanCandidateEvaluation.Rejected
            assertEquals(reason, result.reasonCode)
            assertEquals(PlanEvaluationStage.MATERIAL, result.stage)
            assertEquals(requirements, result.missingRequirements)
            assertEquals("mensaje de usuario sin tokens", result.details)
        }
    }

    /**
     * Paquete A · E2: el reparto elegido (`request.selectedSplitId`) lo resuelve el motor con el calendario real del plan;
     * su rechazo llega por el fallo tipado y el evaluador lo publica como SPLIT en la etapa de frecuencia y reparto,
     * con el texto del motor en `details` y sin inventar minutos ni requisitos de material.
     */
    @Test
    fun aSplitRejectionFromTheEngineIsPublishedAsSplitInTheFrequencyAndSplitStage() = runBlockingTest {
        val message = "El reparto 'Upper / Lower x4' no coincide con el calendario de este plan con 3 días."
        val engine = PlanMaterializationPort { _, _ ->
            throw PlanMaterializationException(PlanEvaluationStage.FREQUENCY_SPLIT, PlanRejectionReason.SPLIT, message)
        }
        val result = PlanCandidateEvaluator.evaluate(
            request().copy(selectedSplitId = "ul_x4"),
            snapshot(entry()),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue("esperaba Rejected, llegó $result", result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals("native:test", result.planId)
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, result.stage)
        assertEquals(PlanRejectionReason.SPLIT, result.reasonCode)
        assertEquals(message, result.details)
        assertTrue("un rechazo de reparto no inventa minutos", result.requiredMinutes == null)
        assertTrue(result.missingRequirements.isEmpty())
    }

    @Test
    fun theFrequencyIsCheckedBeforeTheSplitAndTheEngineIsNotAskedAboutAnUnsupportedFrequency() = runBlockingTest {
        var asked = false
        val engine = PlanMaterializationPort { _, _ ->
            asked = true
            throw PlanMaterializationException(PlanEvaluationStage.FREQUENCY_SPLIT, PlanRejectionReason.SPLIT, "reparto")
        }
        val result = PlanCandidateEvaluator.evaluate(
            request(days = 6).copy(selectedSplitId = "ppl_x6"),
            snapshot(entry(frequencies = 2..3)),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, result.stage)
        assertEquals("la frecuencia manda sobre el reparto", PlanRejectionReason.FREQUENCY, result.reasonCode)
        assertFalse("no se pregunta al motor por una frecuencia que el plan no admite", asked)
    }

    @Test
    fun unexpectedEngineErrorsBecomeInternalMaterializationWithStageAndId() = runBlockingTest {
        val engine = PlanMaterializationPort { _, _ -> throw IllegalStateException("boom interno") }
        val result = PlanCandidateEvaluator.evaluate(
            request(),
            snapshot(entry()),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.MATERIALIZATION, result.stage)
        assertEquals(PlanRejectionReason.INTERNAL_MATERIALIZATION, result.reasonCode)
        assertTrue("conserva clase y causa: ${result.details}", result.details!!.contains("boom interno"))
        assertTrue(result.details!!.contains("IllegalStateException"))
        assertTrue(result.details!!.contains("native:test"))
    }

    @Test
    fun cancellationIsPropagatedAndNeverBecomesAProductRejection() = runBlockingTest {
        val engine = PlanMaterializationPort { _, _ -> throw CancellationException("cancelado") }
        try {
            PlanCandidateEvaluator.evaluate(
                request(),
                snapshot(entry()),
                entryId = "native:test",
                engine = engine,
            )
            fail("la cancelación debe propagarse, no convertirse en rechazo")
        } catch (expected: CancellationException) {
            assertEquals("cancelado", expected.message)
        }
    }

    // ─── Ready / duración / cobertura ───────────────────────────────────────

    @Test
    fun readyCarriesThePreparedProgramDurationLoadsAndInputKey() = runBlockingTest {
        val prepared = program(weight = null)
        val preparedSession = prepared.macrocycles.single().blocks.single()
            .mesocycles.single().weeks.single().sessions.single()
        val expectedDuration = SessionDurationEstimator.estimate(preparedSession)
        val result = PlanCandidateEvaluator.evaluate(
            request(minutes = 60),
            snapshot(entry(recipe = recipe())),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(prepared)),
        )
        assertTrue(result is PlanCandidateEvaluation.Ready)
        result as PlanCandidateEvaluation.Ready
        assertEquals(prepared, result.preparedPlan)
        assertEquals("input-key", result.inputKey)
        assertTrue(result.durationBreakdown.sessionMinutes.isNotEmpty())
        assertTrue(result.durationBreakdown.fitsInto(60))
        assertEquals(listOf(expectedDuration.totalMinutes), result.durationBreakdown.sessionMinutes)
        assertEquals(listOf(expectedDuration), result.durationBreakdown.sessionBreakdowns)
        // Carga de entrenamiento pendiente permitida (RIR) — nunca falta de receta.
        assertEquals(listOf("Press de banca"), result.unresolvedWorkoutLoads)
        assertEquals("recipe-1", result.recipeSnapshot?.id)
    }

    @Test
    fun sessionOverTheChosenBudgetIsRejectedWithTheRequiredMinutes() = runBlockingTest {
        val longSession = program(sessions = 1, restSeconds = 300)
        val materializedSession = longSession.macrocycles.single().blocks.single()
            .mesocycles.single().weeks.single().sessions.single()
        val expectedMinutes = SessionDurationEstimator.estimate(materializedSession).maxSessionMinutes
        val budgetMinutes = (expectedMinutes - 1).coerceAtLeast(1)
        val result = PlanCandidateEvaluator.evaluate(
            request(minutes = budgetMinutes),
            snapshot(entry()),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(longSession)),
        )
        assertTrue("shared estimate=${expectedMinutes} min; evaluation=$result", result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.SESSION_DURATION, result.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, result.reasonCode)
        assertEquals(expectedMinutes, result.requiredMinutes)
        assertEquals(expectedMinutes, PlanCandidateEvaluator.sessionMinutesOf(longSession).single())
        assertTrue(
            "la causa usa el estimador compartido: ${result.details}",
            result.details!!.contains("$expectedMinutes min") && result.details!!.contains("$budgetMinutes min"),
        )
    }

    @Test
    fun cardioOnlyStructuredPartUsesSharedDurationAndReportsTenMinuteBlockAsSevenWorkMinutes() = runBlockingTest {
        val cardio = Exercise(
            id = "cardio-only",
            name = "Caminata",
            cardioDetails = CardioDetails(
                type = CardioType.WALK,
                intensity = CardioIntensity.MEDIA,
                targetDurationSeconds = 10 * 60,
            ),
        )
        val cardioSession = Session(
            id = "cardio-session",
            name = "Cardio",
            parts = listOf(
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    exercises = listOf(cardio),
                    targetDurationMinutes = 10,
                    isCardioGroup = true,
                ),
            ),
            dayOfWeek = 1,
        )
        // Legacy exercise list deliberately empty: the executable cardio lives only in parts.
        assertTrue(cardioSession.exercises.isEmpty())
        val lightStrength = Exercise(
            id = "strength",
            name = "Press",
            sets = listOf(ExerciseSet(id = "strength-set", targetReps = 8)),
        )
        val strengthSession = Session(
            id = "strength-session",
            name = "Fuerza",
            exercises = listOf(lightStrength),
            dayOfWeek = 3,
        )
        val prepared = programWithSessions("cardio-plan", listOf(cardioSession, strengthSession))
        val expectedCardio = SessionDurationEstimator.estimate(cardioSession)
        val expectedMinutes = maxOf(
            expectedCardio.maxSessionMinutes,
            SessionDurationEstimator.estimate(strengthSession).maxSessionMinutes,
        )
        val completeCapabilities = setOf(
            TrainingCapability.STRENGTH,
            TrainingCapability.HYPERTROPHY,
            TrainingCapability.POWER,
            TrainingCapability.CARDIO,
        )
        val candidate = entry(capabilities = completeCapabilities)

        assertEquals(600, expectedCardio.cardioSeconds)
        assertEquals(420, expectedCardio.cardioWorkSeconds)
        assertEquals(7, expectedCardio.cardioWorkSeconds / 60)
        assertEquals(CardioIntensity.MEDIA, cardio.cardioDetails?.intensity)
        assertEquals(expectedCardio, PlanCandidateEvaluator.sessionBreakdownsOf(prepared).first())
        assertEquals(expectedMinutes, PlanCandidateEvaluator.sessionMinutesOf(prepared).maxOrNull())

        val result = PlanCandidateEvaluator.evaluate(
            request(goal = PlanGoalProfile.COMPLETE_ATHLETE, minutes = 10, requiresCardio = true),
            snapshot(candidate),
            entryId = candidate.id,
            engine = engineOf(PlanMaterializationOutcome(prepared)),
        )

        assertTrue("cardio que vive en parts debe alcanzar la puerta de duración: $result", result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.SESSION_DURATION, result.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, result.reasonCode)
        assertEquals(expectedMinutes, result.requiredMinutes)
        assertTrue(result.details!!.contains("$expectedMinutes min"))
    }

    @Test
    fun mixedCardioAndStrengthDurationAndRequiredMinutesMatchTheSharedEstimator() = runBlockingTest {
        val bench = Exercise(
            id = "bench",
            name = "Press banca",
            restTime = 90,
            sets = List(2) { ExerciseSet(id = "bench-set-$it", targetReps = 8) },
        )
        val cardio = Exercise(
            id = "bike",
            name = "Bicicleta",
            cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 10 * 60),
        )
        val mixedSession = Session(
            id = "mixed-session",
            name = "Fuerza y cardio",
            exercises = listOf(bench),
            parts = listOf(
                SessionPart(id = "strength-part", name = "Fuerza", exercises = listOf(bench)),
                SessionPart(
                    id = "cardio-part",
                    name = "Cardio",
                    exercises = listOf(cardio),
                    targetDurationMinutes = 10,
                    isCardioGroup = true,
                ),
            ),
            dayOfWeek = 1,
        )
        val prepared = programWithSessions("mixed-plan", listOf(mixedSession))
        val expected = SessionDurationEstimator.estimate(mixedSession)
        val candidate = entry(
            capabilities = setOf(
                TrainingCapability.STRENGTH,
                TrainingCapability.HYPERTROPHY,
                TrainingCapability.POWER,
                TrainingCapability.CARDIO,
            ),
        )

        assertEquals(18, expected.totalMinutes)
        assertEquals(expected.totalMinutes, PlanCandidateEvaluator.sessionMinutesOf(prepared).single())
        assertEquals(expected, PlanCandidateEvaluator.sessionBreakdownsOf(prepared).single())

        val result = PlanCandidateEvaluator.evaluate(
            request(goal = PlanGoalProfile.COMPLETE_ATHLETE, minutes = 17, requiresCardio = true),
            snapshot(candidate),
            entryId = candidate.id,
            engine = engineOf(PlanMaterializationOutcome(prepared)),
        )

        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.SESSION_DURATION, result.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, result.reasonCode)
        assertEquals(expected.maxSessionMinutes, result.requiredMinutes)
        assertTrue(result.details!!.contains("${expected.totalMinutes} min"))
    }

    @Test
    fun warmupAndApproachSetsAffectEvaluatorBudgetExactlyAsTheSharedEstimator() = runBlockingTest {
        val session = Session(
            id = "warmups",
            name = "Aproximaciones",
            warmup = listOf(WarmupExercise(id = "mobility", name = "Movilidad", duration = 60)),
            exercises = listOf(
                Exercise(
                    id = "bench",
                    name = "Press banca",
                    restTime = 90,
                    sets = List(2) { ExerciseSet(id = "bench-set-$it", targetReps = 8) },
                    warmupSets = listOf(
                        WarmupSetDefinition("approach-1", percentageOfWorkingWeight = 40.0, targetReps = 5, restBetween = 60),
                        WarmupSetDefinition("approach-2", percentageOfWorkingWeight = 60.0, targetReps = 3, restBetween = 90),
                    ),
                ),
            ),
            dayOfWeek = 1,
        )
        val prepared = programWithSessions("warmup-plan", listOf(session))
        val expected = SessionDurationEstimator.estimate(session)

        assertEquals(465, expected.warmupSeconds)
        assertEquals(12, expected.totalMinutes)
        assertEquals(expected, PlanCandidateEvaluator.sessionBreakdownsOf(prepared).single())

        val result = PlanCandidateEvaluator.evaluate(
            request(minutes = 11),
            snapshot(entry()),
            entryId = "native:test",
            engine = engineOf(PlanMaterializationOutcome(prepared)),
        )

        assertTrue(result is PlanCandidateEvaluation.Rejected)
        result as PlanCandidateEvaluation.Rejected
        assertEquals(PlanEvaluationStage.SESSION_DURATION, result.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, result.reasonCode)
        assertEquals(expected.maxSessionMinutes, result.requiredMinutes)
        assertTrue(result.details!!.contains("${expected.totalMinutes} min"))
    }

    @Test
    fun coverageRequiresAllFourComponentsForTheCompleteAthlete() {
        val complete = PlanCandidateEvaluator.coverageOf(
            entry(recipe = recipe(intents = listOf(SlotIntent.F, SlotIntent.H), speed = true, cardio = true)),
            recipe(intents = listOf(SlotIntent.F, SlotIntent.H), speed = true, cardio = true),
        )
        assertTrue(complete.completeAthlete)
        assertTrue(complete.missingCompleteAthleteCapabilities().isEmpty())

        // §15.1: `schedulesCardio` SOLO (el nativo actual sin potencia) no basta.
        val cardioOnly = PlanCandidateEvaluator.coverageOf(entry(schedulesCardio = true), null)
        assertTrue(cardioOnly.hasCardio)
        assertFalse(cardioOnly.completeAthlete)
        assertTrue("power" in cardioOnly.missingCompleteAthleteCapabilities())
        assertFalse("cardio" in cardioOnly.missingCompleteAthleteCapabilities())
    }

    @Test
    fun explicitCatalogCapabilitiesBackGeneratedEntriesWithoutRecipes() = runBlockingTest {
        val capabilities = setOf(
            TrainingCapability.STRENGTH,
            TrainingCapability.HYPERTROPHY,
            TrainingCapability.POWER,
            TrainingCapability.CARDIO,
        )
        val catalogEntry = entry(references = emptySet(), capabilities = capabilities)

        val coverage = PlanCandidateEvaluator.coverageOf(catalogEntry, null)
        assertTrue("capabilities explícitas describen la ficha nativa generada", coverage.completeAthlete)

        val result = PlanCandidateEvaluator.evaluate(
            request(goal = PlanGoalProfile.COMPLETE_ATHLETE),
            snapshot(catalogEntry),
            entryId = catalogEntry.id,
            engine = engineOf(PlanMaterializationOutcome(program())),
        )
        assertTrue("el candidato nativo completo no se rechaza antes de materializar: $result",
            result is PlanCandidateEvaluation.Ready)
    }

    @Test
    fun requiresCardioWithoutPreferencesIsRejectedBeforeMaterializing() = runBlockingTest {
        var consulted = false
        val engine = PlanMaterializationPort { _, _ ->
            consulted = true
            PlanMaterializationOutcome(program())
        }
        val result = PlanCandidateEvaluator.evaluate(
            request(requiresCardio = true, cardioMinutes = null),
            snapshot(entry()),
            entryId = "native:test",
            engine = engine,
        )
        assertTrue(result is PlanCandidateEvaluation.Rejected)
        assertFalse("sin preferencias de cardio no se materializa", consulted)
    }

    private fun runBlockingTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
