package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.training.CoverageFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete A · A.C1 (curaduría de programas, 2026-10-03): reglas de [PlanRepairAdvisor], JVM puro con un
 * evaluador falso programable. Cada prueba fija UNA regla; el comportamiento con el motor real lo comprueba
 * `PlanCoverageContractTest` (que usa el asesor en lugar de las simulaciones que tenía).
 */
class PlanRepairAdvisorTest {

    private val everything = EquipmentAvailability(categories = EquipmentCategory.entries.toSet())

    /** Evaluador falso: registra cada llamada y responde con [answer]. */
    private class Probe(private val answer: (PlanCandidateRequest, EquipmentAvailability) -> PlanCandidateEvaluation) {
        val calls = mutableListOf<Pair<PlanCandidateRequest, EquipmentAvailability>>()

        suspend fun evaluate(request: PlanCandidateRequest, availability: EquipmentAvailability): PlanCandidateEvaluation {
            calls += request to availability
            return answer(request, availability)
        }
    }

    private fun request(
        goal: PlanGoalProfile,
        minutes: Int = 20,
        cardioMinutes: Int? = null,
        splitId: String? = null,
    ) = PlanCandidateRequest(
        inputKey = "base",
        goalProfile = goal,
        level = CatalogLevel.BEGINNER,
        focus = TrainingFocus.FULL_BODY,
        reference = when (goal) {
            PlanGoalProfile.STRENGTH -> TrainingReference.POWERLIFTING
            PlanGoalProfile.MUSCLE -> TrainingReference.HYPERTROPHY
            PlanGoalProfile.STRENGTH_MUSCLE -> TrainingReference.POWERBUILDING
            else -> null
        },
        daysPerWeek = 3,
        weekdays = setOf(1, 3, 5),
        minutesPerSession = minutes,
        effectiveEquipment = emptySet(),
        cardioMinutes = cardioMinutes,
        requiresCardio = goal == PlanGoalProfile.COMPLETE_ATHLETE,
        selectedSplitId = splitId,
        planCatalogRevision = "plan-rev",
        exerciseCatalogRevision = "exercise-rev",
    )

    private fun rejected(
        reason: PlanRejectionReason,
        requiredMinutes: Int? = null,
        missing: List<String> = emptyList(),
    ) = PlanCandidateEvaluation.Rejected(
        planId = "native:test",
        stage = PlanEvaluationStage.MATERIAL,
        reasonCode = reason,
        requiredMinutes = requiredMinutes,
        missingRequirements = missing,
    )

    private fun ready(request: PlanCandidateRequest): PlanCandidateEvaluation =
        CoverageFixtures.stubReady("native:test", request.inputKey)

    private fun suggest(
        request: PlanCandidateRequest,
        rejected: PlanCandidateEvaluation.Rejected,
        availability: EquipmentAvailability = everything,
        probe: Probe,
    ): List<PlanRepair> = runBlocking {
        PlanRepairAdvisor.suggest(request, rejected, availability) { probeRequest, probeAvailability ->
            probe.evaluate(probeRequest, probeAvailability)
        }
    }

    // ─── TIME_BUDGET ───────────────────────────────────────────────────────────────────────────

    @Test
    fun timeBudgetProposesSetMinutesWithTheExactRequiredMinutesWhenThePlanFitsThere() {
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.minutesPerSession >= 28) ready(probeRequest) else rejected(PlanRejectionReason.TIME_BUDGET, 28)
        }
        val repairs = suggest(request(PlanGoalProfile.MUSCLE, minutes = 20), rejected(PlanRejectionReason.TIME_BUDGET, 28), probe = probe)

        assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(28)), repairs)
        assertEquals("se prueba exactamente con los minutos del rechazo", listOf(28), probe.calls.map { it.first.minutesPerSession })
        assertEquals("con el mismo material", everything, probe.calls.single().second)
    }

    @Test
    fun timeBudgetSetMinutesIsNotProposedWhenTheRequiredMinutesDoNotMakeThePlanReady() {
        val probe = Probe { _, _ -> rejected(PlanRejectionReason.TIME_BUDGET, 99) }
        val repairs = suggest(request(PlanGoalProfile.MUSCLE, minutes = 20), rejected(PlanRejectionReason.TIME_BUDGET, 28), probe = probe)

        assertEquals("un objetivo que no es Atleta no tiene otra reparación de tiempo", emptyList<PlanRepair>(), repairs)
    }

    @Test
    fun timeBudgetWithoutAMinuteCountThatTheWizardAcceptsProposesNoSetMinutes() {
        listOf(null, 101, 20, 19).forEach { required ->
            val probe = Probe { probeRequest, _ -> ready(probeRequest) }
            val repairs = suggest(
                request(PlanGoalProfile.MUSCLE, minutes = 20),
                rejected(PlanRejectionReason.TIME_BUDGET, required),
                probe = probe,
            )
            assertEquals("requiredMinutes=$required", emptyList<PlanRepair>(), repairs)
            assertTrue("requiredMinutes=$required: no se prueba un presupuesto que no es válido", probe.calls.isEmpty())
        }
    }

    @Test
    fun theAthleteFallsBackToTheLargestLighterCardioThatFits() {
        val probe = Probe { probeRequest, _ ->
            when {
                probeRequest.minutesPerSession != 45 -> rejected(PlanRejectionReason.TIME_BUDGET, 70)
                probeRequest.cardioMinutes == 30 -> rejected(PlanRejectionReason.TIME_BUDGET, 70)
                (probeRequest.cardioMinutes ?: 0) <= 15 -> ready(probeRequest)
                else -> rejected(PlanRejectionReason.TIME_BUDGET, 70)
            }
        }
        val repairs = suggest(
            request(PlanGoalProfile.COMPLETE_ATHLETE, minutes = 45, cardioMinutes = 30),
            rejected(PlanRejectionReason.TIME_BUDGET, 70),
            probe = probe,
        )

        assertEquals(listOf<PlanRepair>(PlanRepair.SetCardioMinutes(15)), repairs)
        assertEquals(
            "primero los minutos exactos (con los mismos 30 min de cardio) y luego el cardio de mayor a menor",
            listOf(70 to 30, 45 to 20, 45 to 15),
            probe.calls.map { it.first.minutesPerSession to it.first.cardioMinutes },
        )
    }

    @Test
    fun theAthletePrefersTheExactMinutesOverLoweringTheCardio() {
        val probe = Probe { probeRequest, _ -> ready(probeRequest) }
        val repairs = suggest(
            request(PlanGoalProfile.COMPLETE_ATHLETE, minutes = 45, cardioMinutes = 30),
            rejected(PlanRejectionReason.TIME_BUDGET, 70),
            probe = probe,
        )

        assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(70)), repairs)
        assertEquals("ni siquiera se prueba el cardio", 1, probe.calls.size)
    }

    @Test
    fun cardioOnlyGoesDownNeverBelowTheOfferedStepsAndNeverForOtherGoals() {
        // Con 10 min de cardio no hay un escalón menor que ofrecer: ningún SetCardioMinutes.
        val atTheFloor = Probe { probeRequest, _ ->
            if (probeRequest.minutesPerSession == 45) ready(probeRequest) else rejected(PlanRejectionReason.TIME_BUDGET, 70)
        }
        assertEquals(
            emptyList<PlanRepair>(),
            suggest(
                request(PlanGoalProfile.COMPLETE_ATHLETE, minutes = 45, cardioMinutes = 10),
                rejected(PlanRejectionReason.TIME_BUDGET, 70),
                probe = atTheFloor,
            ),
        )
        assertEquals("solo se probó el presupuesto exacto: el cardio no tiene a dónde bajar", 1, atTheFloor.calls.size)

        // Sin minutos de cardio elegidos no hay nada que bajar.
        val noCardio = Probe { probeRequest, _ -> ready(probeRequest) }
        assertEquals(
            emptyList<PlanRepair>(),
            suggest(
                request(PlanGoalProfile.COMPLETE_ATHLETE, minutes = 45, cardioMinutes = null),
                rejected(PlanRejectionReason.TIME_BUDGET, null),
                probe = noCardio,
            ),
        )
        assertTrue(noCardio.calls.isEmpty())

        // Los objetivos que no son Atleta completo nunca reciben una reparación de cardio, aunque el pedido lo lleve.
        listOf(PlanGoalProfile.STRENGTH, PlanGoalProfile.MUSCLE, PlanGoalProfile.STRENGTH_MUSCLE).forEach { goal ->
            val probe = Probe { probeRequest, _ ->
                if (probeRequest.cardioMinutes != 30) ready(probeRequest) else rejected(PlanRejectionReason.TIME_BUDGET, 70)
            }
            assertEquals(
                "$goal",
                emptyList<PlanRepair>(),
                suggest(request(goal, minutes = 45, cardioMinutes = 30), rejected(PlanRejectionReason.TIME_BUDGET, 70), probe = probe),
            )
        }
    }

    // ─── APPARATUS_UNKNOWN ─────────────────────────────────────────────────────────────────────

    @Test
    fun apparatusUnknownConfirmsExactlyTheKeysOfTheRejectionWithTheirCategories() {
        val probe = Probe { probeRequest, availability ->
            val confirmed = availability.presenceOf("squat_rack") == ApparatusPresence.PRESENT &&
                availability.presenceOf("bench_flat") == ApparatusPresence.PRESENT
            if (confirmed) ready(probeRequest) else rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack", "bench"))
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH),
            rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack", "bench")),
            probe = probe,
        )

        assertEquals(
            listOf<PlanRepair>(PlanRepair.ConfirmApparatus(listOf("squat_rack", "bench_flat"), setOf(EquipmentCategory.SUPPORT))),
            repairs,
        )
        val tried = probe.calls.single().second
        assertEquals(ApparatusPresence.PRESENT, tried.presenceOf("squat_rack"))
        assertEquals(ApparatusPresence.PRESENT, tried.presenceOf("bench_flat"))
        assertEquals("solo se confirma lo que el rechazo pidió", ApparatusPresence.UNKNOWN, tried.presenceOf("pullup_bar"))
        assertEquals(setOf("squat_rack", "bench_flat"), tried.supports.keys)
    }

    @Test
    fun apparatusUnknownOnlyConfirmsTheKeyThatWasMissing() {
        val probe = Probe { probeRequest, availability ->
            if (availability.presenceOf("squat_rack") == ApparatusPresence.PRESENT) ready(probeRequest)
            else rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack"))
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH),
            rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack")),
            probe = probe,
        )

        assertEquals(
            listOf<PlanRepair>(PlanRepair.ConfirmApparatus(listOf("squat_rack"), setOf(EquipmentCategory.SUPPORT))),
            repairs,
        )
        assertEquals(ApparatusPresence.UNKNOWN, probe.calls.single().second.presenceOf("bench_flat"))
    }

    @Test
    fun apparatusUnknownChainsASingleSetMinutesWhenOnlyTimeIsLeft() {
        val probe = Probe { probeRequest, availability ->
            when {
                availability.presenceOf("squat_rack") != ApparatusPresence.PRESENT ->
                    rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack"))
                probeRequest.minutesPerSession >= 40 -> ready(probeRequest)
                else -> rejected(PlanRejectionReason.TIME_BUDGET, 40)
            }
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH, minutes = 30),
            rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack")),
            probe = probe,
        )

        assertEquals(
            listOf(
                PlanRepair.ConfirmApparatus(listOf("squat_rack"), setOf(EquipmentCategory.SUPPORT)),
                PlanRepair.SetMinutes(40),
            ),
            repairs,
        )
        assertTrue("los minutos exactos se prueban con el material ya confirmado", probe.calls.last().second.presenceOf("squat_rack") == ApparatusPresence.PRESENT)
    }

    @Test
    fun apparatusUnknownWithoutAConfirmableKeyHasNoRepair() {
        listOf(emptyList<String>(), listOf("barbell"), listOf("dumbbells")).forEach { missing ->
            val probe = Probe { probeRequest, _ -> ready(probeRequest) }
            val repairs = suggest(
                request(PlanGoalProfile.STRENGTH),
                rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = missing),
                probe = probe,
            )
            assertEquals("requisitos=$missing", emptyList<PlanRepair>(), repairs)
            assertTrue("requisitos=$missing: sin llave no se prueba nada", probe.calls.isEmpty())
        }
    }

    @Test
    fun apparatusUnknownIsNotRepairedWhenConfirmingDoesNotMakeThePlanReady() {
        val probe = Probe { _, _ -> rejected(PlanRejectionReason.COMPOSITION) }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH),
            rejected(PlanRejectionReason.APPARATUS_UNKNOWN, missing = listOf("rack", "bench")),
            probe = probe,
        )

        assertEquals(emptyList<PlanRepair>(), repairs)
    }

    // ─── APPARATUS_ABSENT y PROFILE_MISMATCH: SwitchGoal ───────────────────────────────────────

    @Test
    fun strengthWithDumbbellsSwitchesToStrengthMuscleAndTheDestinationIsEvaluatedWithItsOwnTerms() {
        val withDumbbells = EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS))
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.goalProfile == PlanGoalProfile.STRENGTH_MUSCLE) ready(probeRequest)
            else rejected(PlanRejectionReason.APPARATUS_ABSENT)
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH, splitId = "pl_sbd_x3"),
            rejected(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("barbell")),
            availability = withDumbbells,
            probe = probe,
        )

        assertEquals(listOf<PlanRepair>(PlanRepair.SwitchGoal(PlanGoalProfile.STRENGTH_MUSCLE)), repairs)
        val tried = probe.calls.single().first
        assertEquals(TrainingReference.POWERBUILDING, tried.reference)
        assertFalse("el destino no lleva cardio", tried.requiresCardio)
        assertNull(tried.cardioMinutes)
        assertNull("el reparto era del objetivo anterior", tried.selectedSplitId)
        assertEquals("el resto del pedido se conserva", 20, tried.minutesPerSession)
        assertEquals(3, tried.daysPerWeek)
    }

    @Test
    fun strengthWithoutDumbbellsSwitchesToMuscle() {
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.goalProfile == PlanGoalProfile.MUSCLE) ready(probeRequest) else rejected(PlanRejectionReason.APPARATUS_ABSENT)
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH),
            rejected(PlanRejectionReason.APPARATUS_ABSENT, missing = listOf("barbell")),
            availability = EquipmentAvailability(categories = setOf(EquipmentCategory.BAND)),
            probe = probe,
        )

        assertEquals(listOf<PlanRepair>(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE)), repairs)
        assertEquals(TrainingReference.HYPERTROPHY, probe.calls.single().first.reference)
    }

    @Test
    fun strengthMuscleSwitchesToMuscleAndProfileMismatchUsesTheSameTable() {
        listOf(PlanRejectionReason.PROFILE_MISMATCH, PlanRejectionReason.APPARATUS_ABSENT).forEach { reason ->
            val probe = Probe { probeRequest, _ ->
                if (probeRequest.goalProfile == PlanGoalProfile.MUSCLE) ready(probeRequest) else rejected(PlanRejectionReason.COMPOSITION)
            }
            val repairs = suggest(request(PlanGoalProfile.STRENGTH_MUSCLE), rejected(reason), probe = probe)
            assertEquals("$reason", listOf<PlanRepair>(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE)), repairs)
        }
    }

    @Test
    fun muscleAndTheCompleteAthleteHaveNoDestinationAndNothingIsEvaluated() {
        listOf(PlanGoalProfile.MUSCLE, PlanGoalProfile.COMPLETE_ATHLETE).forEach { goal ->
            listOf(PlanRejectionReason.PROFILE_MISMATCH, PlanRejectionReason.APPARATUS_ABSENT).forEach { reason ->
                val probe = Probe { probeRequest, _ -> ready(probeRequest) }
                val repairs = suggest(request(goal, cardioMinutes = if (goal == PlanGoalProfile.COMPLETE_ATHLETE) 10 else null), rejected(reason), probe = probe)
                assertEquals("$goal/$reason", emptyList<PlanRepair>(), repairs)
                assertTrue("$goal/$reason", probe.calls.isEmpty())
            }
        }
    }

    @Test
    fun switchGoalIsOnlyProposedWhenTheDestinationEvaluatesReady() {
        val probe = Probe { _, _ -> rejected(PlanRejectionReason.COMPOSITION) }
        val repairs = suggest(request(PlanGoalProfile.STRENGTH), rejected(PlanRejectionReason.APPARATUS_ABSENT), probe = probe)

        assertEquals(emptyList<PlanRepair>(), repairs)
        assertEquals("se probó el destino y no se propone a ciegas", 1, probe.calls.size)
    }

    @Test
    fun switchGoalChainsTheExactMinutesWhenTheDestinationOnlyFailsOnTime() {
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.minutesPerSession >= 30) ready(probeRequest) else rejected(PlanRejectionReason.TIME_BUDGET, 30)
        }
        val repairs = suggest(
            request(PlanGoalProfile.STRENGTH_MUSCLE, minutes = 20),
            rejected(PlanRejectionReason.PROFILE_MISMATCH),
            probe = probe,
        )

        assertEquals(listOf<PlanRepair>(PlanRepair.SwitchGoal(PlanGoalProfile.MUSCLE, alsoMinutes = 30)), repairs)
        assertEquals(listOf(20, 30), probe.calls.map { it.first.minutesPerSession })
        assertTrue("ambos sondeos son del destino", probe.calls.all { it.first.goalProfile == PlanGoalProfile.MUSCLE })
    }

    @Test
    fun switchGoalWithATimeBudgetThatMoreMinutesDoNotFixHasNoRepair() {
        val probe = Probe { _, _ -> rejected(PlanRejectionReason.TIME_BUDGET, 30) }
        val repairs = suggest(request(PlanGoalProfile.STRENGTH_MUSCLE), rejected(PlanRejectionReason.PROFILE_MISMATCH), probe = probe)

        assertEquals(emptyList<PlanRepair>(), repairs)
    }

    @Test
    fun theAdvisorNeverSwitchesToTheCompleteAthlete() {
        PlanGoalProfile.entries.forEach { goal ->
            PlanRejectionReason.entries.forEach { reason ->
                val probe = Probe { probeRequest, _ -> ready(probeRequest) }
                val repairs = suggest(
                    request(goal, cardioMinutes = if (goal == PlanGoalProfile.COMPLETE_ATHLETE) 30 else null, splitId = "any"),
                    rejected(reason, requiredMinutes = 40, missing = listOf("rack")),
                    availability = EquipmentAvailability(categories = setOf(EquipmentCategory.DUMBBELLS)),
                    probe = probe,
                )
                repairs.forEach { repair ->
                    if (repair is PlanRepair.SwitchGoal) {
                        assertNotEquals("$goal/$reason", PlanGoalProfile.COMPLETE_ATHLETE, repair.goal)
                    }
                }
            }
        }
    }

    // ─── SPLIT ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun splitRejectionClearsTheSplitWhenThatMakesThePlanReady() {
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.selectedSplitId == null) ready(probeRequest) else rejected(PlanRejectionReason.SPLIT)
        }
        val repairs = suggest(request(PlanGoalProfile.MUSCLE, splitId = "ppl_ul"), rejected(PlanRejectionReason.SPLIT), probe = probe)

        assertEquals(listOf<PlanRepair>(PlanRepair.ClearSplit), repairs)
    }

    @Test
    fun splitRejectionHasNoRepairWithoutAChosenSplitOrWhenClearingDoesNotHelp() {
        val withoutSplit = Probe { probeRequest, _ -> ready(probeRequest) }
        assertEquals(
            emptyList<PlanRepair>(),
            suggest(request(PlanGoalProfile.MUSCLE, splitId = null), rejected(PlanRejectionReason.SPLIT), probe = withoutSplit),
        )
        assertTrue("sin reparto elegido no hay nada que quitar", withoutSplit.calls.isEmpty())

        val stillRejected = Probe { _, _ -> rejected(PlanRejectionReason.COMPOSITION) }
        assertEquals(
            emptyList<PlanRepair>(),
            suggest(request(PlanGoalProfile.MUSCLE, splitId = "ppl_ul"), rejected(PlanRejectionReason.SPLIT), probe = stillRejected),
        )
    }

    // ─── Sin reparación de un toque ────────────────────────────────────────────────────────────

    @Test
    fun theOtherReasonsHaveNoOneTapRepairAndNothingIsEvaluated() {
        listOf(
            PlanRejectionReason.CATALOG_NOT_READY,
            PlanRejectionReason.RECIPE_UNAVAILABLE,
            PlanRejectionReason.LEVEL_UNSUITABLE,
            PlanRejectionReason.FREQUENCY,
            PlanRejectionReason.UNRESOLVED_CONFIGURATION,
            PlanRejectionReason.NO_VALID_SUBSTITUTION,
            PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
        ).forEach { reason ->
            val probe = Probe { probeRequest, _ -> ready(probeRequest) }
            val repairs = suggest(request(PlanGoalProfile.MUSCLE, splitId = "x"), rejected(reason, 40, listOf("rack")), probe = probe)
            assertEquals("$reason", emptyList<PlanRepair>(), repairs)
            assertTrue("$reason: no se prueba nada", probe.calls.isEmpty())
        }
    }

    // ─── Detalles del contrato con el evaluador ────────────────────────────────────────────────

    @Test
    fun everyProbeCarriesItsOwnInputKeyAndTheOriginalRequestIsNotTouched() {
        val original = request(PlanGoalProfile.STRENGTH_MUSCLE, minutes = 20)
        val probe = Probe { probeRequest, _ ->
            if (probeRequest.minutesPerSession >= 30) ready(probeRequest) else rejected(PlanRejectionReason.TIME_BUDGET, 30)
        }
        suggest(original, rejected(PlanRejectionReason.PROFILE_MISMATCH), probe = probe)

        val keys = probe.calls.map { it.first.inputKey }
        assertEquals("cada sondeo distinto tiene su huella", keys.size, keys.toSet().size)
        assertTrue(keys.none { it == original.inputKey })
        assertTrue(keys.all { it.startsWith("${original.inputKey}|repair=") })
        assertEquals("el pedido original no cambia", request(PlanGoalProfile.STRENGTH_MUSCLE, minutes = 20), original)
    }

    @Test
    fun theReferenceOfEachGoalIsTheOneTheWizardUses() {
        CoverageFixtures.Profile.entries.forEach { profile ->
            assertEquals(profile.name, profile.reference, PlanRepairAdvisor.referenceOf(profile.planGoal))
        }
        assertNull(PlanRepairAdvisor.referenceOf(PlanGoalProfile.LEGACY_MIXED))
        assertNull(PlanRepairAdvisor.referenceOf(PlanGoalProfile.COMPLETE_ATHLETE))
        assertNull(PlanRepairAdvisor.referenceOf(PlanGoalProfile.LEGACY_HEALTH))
    }

    @Test
    fun confirmApparatusWritesTheCategoriesAndOverwritesAnExplicitAbsence() {
        val availability = EquipmentAvailability(
            categories = setOf(EquipmentCategory.BARBELL),
            supports = mapOf("squat_rack" to ApparatusPresence.ABSENT, "pullup_bar" to ApparatusPresence.PRESENT),
        )
        val repair = PlanRepair.ConfirmApparatus(listOf("squat_rack", "bench_flat"), setOf(EquipmentCategory.SUPPORT))

        val confirmed = repair.applyTo(availability)

        assertEquals(setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT), confirmed.categories)
        assertEquals(ApparatusPresence.PRESENT, confirmed.presenceOf("squat_rack"))
        assertEquals(ApparatusPresence.PRESENT, confirmed.presenceOf("bench_flat"))
        assertEquals("lo que no se pidió se conserva", ApparatusPresence.PRESENT, confirmed.presenceOf("pullup_bar"))
        assertEquals("la disponibilidad original no cambia", ApparatusPresence.ABSENT, availability.presenceOf("squat_rack"))
        assertEquals(
            "una llave que no es del panel no se inventa",
            availability.copy(categories = availability.categories + EquipmentCategory.SUPPORT),
            PlanRepair.ConfirmApparatus(listOf("no_such_key"), setOf(EquipmentCategory.SUPPORT)).applyTo(availability),
        )
    }
}
