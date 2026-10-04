package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.CatalogClassification
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.training.Calibration
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CatalogProvenance
import com.example.kpkn.domain.training.CoverageFixtures
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.PersonalizationResult
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.TrainingOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * F-A2 (consolidación 2026-10-01): el ViewModel perdía la razón tipada de los
 * nativos v2 (`report.reasonCode` + `maxSessionMinutes`) y la reconstruía leyendo
 * el texto. [NativePlanFailureMapper] es ahora la única traducción, con el mismo
 * mapeo que ya fijaba `PlanGenerationCoverageT006Test`.
 */
class NativePlanFailureMapperTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun report(reasonCode: String?, maxMinutes: Int? = null, vararg limitations: String) = PersonalizationReport(
        executable = false,
        classification = CatalogClassification.SIMPLE,
        limitations = limitations.toList(),
        muscles = emptyList(),
        provenance = CatalogProvenance("native:test", "rev", CatalogSource.NATIVE, "test"),
        reasonCode = reasonCode,
        maxSessionMinutes = maxMinutes,
    )

    @Test
    fun timeBudgetKeepsTheMinimumMinutesTheFitterComputed() {
        val failure = NativePlanFailureMapper.typedFailure(
            report("TIME_BUDGET", 21, "Con las series mínimas este plan necesita 21 min por sesión y elegiste 20."),
        )
        assertNotNull(failure)
        failure!!
        assertEquals(PlanEvaluationStage.SESSION_DURATION, failure.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, failure.reason)
        assertEquals(21, failure.requiredMinutes)
        assertTrue(failure.message.orEmpty().contains("21 min"))
    }

    @Test
    fun everyKnownReasonCodeMapsToItsClosedStageAndReason() {
        val expected = mapOf(
            "APPARATUS_ABSENT" to (PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_ABSENT),
            "APPARATUS_UNKNOWN" to (PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_UNKNOWN),
            "PROFILE_MISMATCH" to (PlanEvaluationStage.PROFILE to PlanRejectionReason.PROFILE_MISMATCH),
            "COMPOSITION" to (PlanEvaluationStage.COMPOSITION to PlanRejectionReason.COMPOSITION),
            // Paquete A · E2: el reparto elegido que el plan no puede cumplir.
            "SPLIT" to (PlanEvaluationStage.FREQUENCY_SPLIT to PlanRejectionReason.SPLIT),
        )
        expected.forEach { (code, pair) ->
            val failure = requireNotNull(NativePlanFailureMapper.typedFailure(report(code, null, "motivo $code")))
            assertEquals(code, pair.first, failure.stage)
            assertEquals(code, pair.second, failure.reason)
            assertNull("$code no inventa minutos", failure.requiredMinutes)
            assertEquals("motivo $code", failure.message)
            assertTrue("$code sin lista en el informe no inventa requisitos", failure.missingRequirements.isEmpty())
        }
    }

    // ─── Paquete A · B1: los requisitos de material viajan con el rechazo ─────────────────────────

    private fun reportWithRequirements(reasonCode: String, requirements: List<String>, message: String) = PersonalizationReport(
        executable = false,
        classification = CatalogClassification.SIMPLE,
        limitations = listOf(message),
        muscles = emptyList(),
        provenance = CatalogProvenance("native:test", "rev", CatalogSource.NATIVE, "test"),
        reasonCode = reasonCode,
        missingRequirements = requirements,
    )

    @Test
    fun apparatusUnknownMapsToTheMaterialStageAndCarriesTheRequirementsTheFitterCouldNotConfirm() {
        val failure = requireNotNull(
            NativePlanFailureMapper.typedFailure(
                reportWithRequirements("APPARATUS_UNKNOWN", listOf("rack", "bench"), "Falta confirmar si tienes rack y banco."),
            ),
        )
        assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
        assertEquals(PlanRejectionReason.APPARATUS_UNKNOWN, failure.reason)
        assertEquals(listOf("rack", "bench"), failure.missingRequirements)
        assertEquals("Falta confirmar si tienes rack y banco.", failure.message)
        assertNull("un motivo de aparato no inventa minutos", failure.requiredMinutes)
        assertTrue(failure.affectedSlots.isEmpty())
    }

    @Test
    fun apparatusAbsentPropagatesTheRequirementsToo() {
        val failure = requireNotNull(
            NativePlanFailureMapper.typedFailure(
                reportWithRequirements("APPARATUS_ABSENT", listOf("barbell"), "Este perfil trabaja con barra. Falta barra y carga."),
            ),
        )
        assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
        assertEquals(PlanRejectionReason.APPARATUS_ABSENT, failure.reason)
        assertEquals(listOf("barbell"), failure.missingRequirements)
    }

    @Test
    fun onlyTheApparatusReasonsCarryRequirements() {
        listOf("TIME_BUDGET", "PROFILE_MISMATCH", "COMPOSITION", "SPLIT").forEach { code ->
            val failure = requireNotNull(
                NativePlanFailureMapper.typedFailure(reportWithRequirements(code, listOf("rack"), "motivo $code")),
            )
            assertTrue("$code no lleva requisitos de aparato", failure.missingRequirements.isEmpty())
        }
    }

    /**
     * Gimnasio sin confirmar (todas las categorías, soportes sin responder) + Fuerza: ya no es «declaraste
     * ausente» sino APPARATUS_UNKNOWN con la lista de lo que falta confirmar, y el mapeador la conserva.
     */
    @Test
    fun theRealFitterTurnsAnUnconfirmedGymIntoAnUnknownApparatusWithItsRequirements() {
        val result = SimpleCyclePersonalizer(
            InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
        ).personalize(
            programId = "b1-strength-unconfirmed-gym",
            input = PersonalizerInput(
                catalogEntryId = NativeProfileKind.STRENGTH.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = 3,
                weekdays = listOf(1, 3, 5),
                equipment = emptySet(),
                level = CatalogLevel.INTERMEDIATE,
                availableMinutes = 90,
            ),
            options = TrainingOptions(availability = EquipmentAvailability(EquipmentCategory.entries.toSet())),
        )

        assertNull(result.program)
        assertEquals("APPARATUS_UNKNOWN", result.report.reasonCode)
        assertEquals(listOf("rack", "bench"), result.report.missingRequirements)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(result.report))
        assertEquals(PlanEvaluationStage.MATERIAL, failure.stage)
        assertEquals(PlanRejectionReason.APPARATUS_UNKNOWN, failure.reason)
        assertEquals(listOf("rack", "bench"), failure.missingRequirements)
    }

    // ─── Paquete A · E2: el reparto elegido llega como motivo cerrado SPLIT ─────────────────────────

    private val confirmedGym = CoverageFixtures.legacyFixtures().first { it.id == "E6" }.availability

    /** Plan propio de Músculo, intermedio, 4 días, 90 min y gimnasio completo confirmado, con el reparto indicado. */
    private fun ownMuscleWithSplit(splitId: String?): PersonalizationResult = personalizer().personalize(
        programId = "e2-muscle-4-${splitId ?: "sin-reparto"}",
        input = PersonalizerInput(
            catalogEntryId = NativeProfileKind.MUSCLE.entryId,
            focus = TrainingFocus.FULL_BODY,
            frequency = 4,
            weekdays = listOf(1, 2, 4, 5),
            equipment = emptySet(),
            level = CatalogLevel.INTERMEDIATE,
            availableMinutes = 90,
            splitId = splitId,
        ),
        options = TrainingOptions(availability = confirmedGym),
    )

    @Test
    fun theRealOwnPlanTurnsAnInvalidSplitIntoATypedSplitRejectionAndStaysReadyWithItsOwnOrNoSplit() {
        val rejected = ownMuscleWithSplit("pl_sbd_x3")
        assertNull(rejected.program)
        assertEquals("SPLIT", rejected.report.reasonCode)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(rejected.report))
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, failure.stage)
        assertEquals(PlanRejectionReason.SPLIT, failure.reason)
        assertNull("un rechazo de reparto no inventa minutos", failure.requiredMinutes)
        assertTrue("ni requisitos de material", failure.missingRequirements.isEmpty())
        val message = failure.message.orEmpty()
        assertTrue("dice el nombre del reparto: $message", "SBD Full Body x3" in message)
        assertFalse("nunca su id: $message", "pl_sbd_x3" in message)

        // Con el reparto equivalente de su calendario o sin reparto el generador entrega programa, sin motivo de rechazo.
        listOf("ul_x4", null).forEach { splitId ->
            val accepted = ownMuscleWithSplit(splitId)
            assertNotNull("$splitId: ${accepted.report.limitations}", accepted.program)
            assertNull("un programa no lleva motivo de rechazo", accepted.report.reasonCode)
        }
    }

    @Test
    fun theHistoricalGeneratorTypesItsInvalidSplitAsSplitWithTheSameMessage() {
        val result = personalizer().personalize(
            "e2-historical-ul-3",
            PersonalizerInput(
                catalogEntryId = "native:machine-muscle",
                focus = TrainingFocus.FULL_BODY,
                frequency = 3,
                weekdays = listOf(1, 3, 5),
                equipment = setOf("machine"),
                level = CatalogLevel.INTERMEDIATE,
                availableMinutes = 90,
                splitId = "ul_x4",
            ),
        )
        assertNull(result.program)
        assertEquals("SPLIT", result.report.reasonCode)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(result.report))
        assertEquals(PlanEvaluationStage.FREQUENCY_SPLIT, failure.stage)
        assertEquals(PlanRejectionReason.SPLIT, failure.reason)
        // El mensaje llano de siempre (lo fija `OnboardingSplitSelectionTest`): nombre del reparto y días, no su id.
        val message = failure.message.orEmpty()
        assertTrue(message, message.contains("Upper / Lower x4"))
        assertTrue(message, message.contains("4 días de entrenamiento"))
        assertTrue(message, message.contains("has elegido 3"))
    }

    @Test
    fun withoutAKnownReasonCodeTheCallerKeepsItsLegacyClassification() {
        assertNull(NativePlanFailureMapper.typedFailure(report(null, null, "Este plan no está curado para esa frecuencia.")))
        assertNull(NativePlanFailureMapper.typedFailure(report("RAZON_FUTURA", 40, "algo nuevo")))
    }

    @Test
    fun aReportWithoutLimitationsStillExplainsTheFailure() {
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(report("COMPOSITION")))
        assertEquals(NativePlanFailureMapper.DEFAULT_MESSAGE, failure.message)
    }

    @Test
    fun theRealFitterRejectionOfBodyweightMuscleInTwentyMinutesBecomesATypedTimeBudget() {
        val catalog = CatalogCompositionTestSupport.catalog
        val personalizer = SimpleCyclePersonalizer(
            InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
        )
        val result = personalizer.personalize(
            programId = "f-a2-muscle-bodyweight-20",
            input = PersonalizerInput(
                catalogEntryId = NativeProfileKind.MUSCLE.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = 1,
                weekdays = listOf(1),
                equipment = emptySet(),
                level = CatalogLevel.BEGINNER,
                availableMinutes = 20,
            ),
            options = TrainingOptions(availability = EquipmentAvailability()),
        )

        assertNull("el fitter no publica éxito parcial a 20 min", result.program)
        assertEquals("TIME_BUDGET", result.report.reasonCode)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(result.report))
        assertEquals(PlanEvaluationStage.SESSION_DURATION, failure.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, failure.reason)
        assertEquals(
            "los minutos del rechazo son los del fitter, no los parseados del texto",
            result.report.maxSessionMinutes,
            failure.requiredMinutes,
        )
        assertTrue(
            "el mínimo real supera el presupuesto: ${failure.requiredMinutes}",
            (failure.requiredMinutes ?: 0) > 20,
        )
    }

    // ─── Generador HISTÓRICO: rechazo temporal tipado (consolidación 2026-10-02, fila F) ──────────

    private val fullGym = EquipmentAvailability(EquipmentCategory.entries.toSet())

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
    )

    /** Músculo, principiante, 3 días: las mismas entradas que la fila F de la matriz T-019. */
    private fun historicalThreeDays(
        entryId: String,
        minutes: Int,
        availability: EquipmentAvailability,
    ): PersonalizationResult = personalizer().personalize(
        programId = "f-row-${entryId.substringAfterLast(':')}-$minutes",
        input = PersonalizerInput(
            catalogEntryId = entryId,
            focus = TrainingFocus.FULL_BODY,
            frequency = 3,
            weekdays = listOf(1, 2, 3),
            equipment = emptySet(),
            level = CatalogLevel.BEGINNER,
            availableMinutes = minutes,
            calibration = Calibration.CONSERVATIVE,
        ),
        options = TrainingOptions(availability = availability),
    )

    /**
     * Fila F de la matriz: con las 11 categorías, 3 días y 20 min el generador histórico no
     * completa una sesión equilibrada (dos compuestos con aproximaciones, 1224 s para el tercero).
     * Antes salía sin razón tipada y la UI lo mostraba como «Falta confirmar material»; ahora es
     * TIME_BUDGET con el mínimo real, que el mapeador convierte en SESSION_DURATION.
     */
    @Test
    fun theHistoricalFullBodyRejectionAtTwentyMinutesIsATypedTimeBudgetWithItsRealMinimum() {
        val rejected = historicalThreeDays("native:full-body", 20, fullGym)

        assertNull("sin éxito parcial a 20 min", rejected.program)
        assertEquals("TIME_BUDGET", rejected.report.reasonCode)
        assertEquals(21, rejected.report.maxSessionMinutes)
        val failure = requireNotNull(NativePlanFailureMapper.typedFailure(rejected.report))
        assertEquals(PlanEvaluationStage.SESSION_DURATION, failure.stage)
        assertEquals(PlanRejectionReason.TIME_BUDGET, failure.reason)
        assertEquals(21, failure.requiredMinutes)
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("21 min"))

        // El mínimo es exacto y sale del mismo generador y estimador: con 21 min hay programa y
        // cada sesión mide, con el estimador común, como mucho 21 min.
        val viable = historicalThreeDays("native:full-body", 21, fullGym)
        val program = requireNotNull(viable.program) { "a 21 min debe haber programa: ${viable.report.limitations}" }
        assertNull("un resultado viable no lleva razón de rechazo", viable.report.reasonCode)
        val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }
        assertTrue(sessions.isNotEmpty())
        sessions.forEach { session ->
            val measured = SessionDurationEstimator.estimate(session).totalMinutes
            assertTrue("${session.id} mide $measured min con 21 min de presupuesto", measured <= 21)
        }
    }

    /**
     * La misma combinación (3 días, 20 min, principiante) con un material que el plan no puede
     * usar (máquinas sin la categoría MÁQUINAS) NO es un problema de tiempo: ningún presupuesto lo
     * arregla, así que no lleva razón tipada y el llamador conserva su clasificación por texto
     * (material), nunca un TIME_BUDGET inventado.
     */
    @Test
    fun theSameCombinationWithMaterialThePlanCannotUseStaysAMaterialRejection() {
        val withoutMachines = EquipmentAvailability(EquipmentCategory.entries.toSet() - EquipmentCategory.MACHINES)
        val rejected = historicalThreeDays("native:machine-muscle", 20, withoutMachines)

        assertNull(rejected.program)
        assertNull("no es un rechazo de tiempo", rejected.report.reasonCode)
        assertNull("no inventa minutos mínimos", rejected.report.maxSessionMinutes)
        assertNull(
            "sin razón cerrada el llamador clasifica por texto (material)",
            NativePlanFailureMapper.typedFailure(rejected.report),
        )
        val text = rejected.report.limitations.joinToString(" ")
        assertTrue(text, text.contains("equipo", ignoreCase = true) || text.contains("material", ignoreCase = true))
    }

    @Test
    fun theMinimumProbeStopsAtTheFirstViableMinuteAfterCheckingTheCeilingOnce() {
        val probed = mutableListOf<Int>()
        val minimum = SimpleCyclePersonalizer.firstViableMinutes(20) { minutes ->
            probed += minutes
            minutes >= 23
        }

        assertEquals(23, minimum)
        assertEquals("techo una vez y luego de menor a mayor, cortando en el primero viable", listOf(100, 21, 22, 23), probed)
    }

    @Test
    fun theMinimumProbeDoesNotSweepWhenEvenTheCeilingIsNotViable() {
        val probed = mutableListOf<Int>()
        val minimum = SimpleCyclePersonalizer.firstViableMinutes(20) { minutes ->
            probed += minutes
            false
        }

        assertNull("si 100 min no bastan el rechazo no es de tiempo", minimum)
        assertEquals("una sola prueba, no ~80 generaciones", listOf(100), probed)
    }

    @Test
    fun theMinimumProbeHandlesTheEdgesOfTheBudgetRange() {
        val probed = mutableListOf<Int>()
        assertNull(
            "con 100 min ya no hay presupuesto mayor que ofrecer",
            SimpleCyclePersonalizer.firstViableMinutes(100) { minutes -> probed += minutes; true },
        )
        assertTrue("ni se prueba nada", probed.isEmpty())

        assertEquals(
            "con 99 min el único mayor es el techo",
            100,
            SimpleCyclePersonalizer.firstViableMinutes(99) { true },
        )
    }

    @Test
    fun theMinimumProbePropagatesCancellationInsteadOfSwallowingIt() {
        try {
            SimpleCyclePersonalizer.firstViableMinutes(20) { minutes ->
                if (minutes == 21) throw CancellationException("cancelado") else minutes >= 100
            }
            fail("la cancelación debe propagarse")
        } catch (expected: CancellationException) {
            assertEquals("cancelado", expected.message)
        }
    }

    // ─── Paquete A · C2: mínimo EXACTO con pocas pruebas (DEC-w2-03) ──────────────────────────────

    /** [SimpleCyclePersonalizer.exactMinimumMinutes] con un registro de las pruebas que hizo. */
    private fun exactMinimum(
        available: Int,
        hint: Int?,
        probed: MutableList<Int>,
        viableAt: (Int) -> Boolean,
    ): Int? = SimpleCyclePersonalizer.exactMinimumMinutes(available, hint) { minutes ->
        probed += minutes
        viableAt(minutes)
    }

    @Test
    fun theExactMinimumIsFoundForEveryThresholdWhateverTheHintAndWithAtMostTenProbes() {
        var worst = 0
        listOf(20, 33, 59, 98).forEach { available ->
            (available + 1..100).forEach { threshold ->
                val hints = listOf(null, threshold, threshold - 1, threshold - 3, threshold + 1, threshold + 7, available + 1, 100, 250)
                hints.forEach { hint ->
                    val probed = mutableListOf<Int>()
                    val minimum = exactMinimum(available, hint, probed) { minutes -> minutes >= threshold }
                    val context = "available=$available threshold=$threshold hint=$hint probes=$probed"
                    assertEquals(context, threshold, minimum)
                    assertTrue(context, probed.size <= 10)
                    assertTrue("$context: solo se prueba entre el presupuesto elegido (excluido) y el techo", probed.all { it in available + 1..100 })
                    worst = maxOf(worst, probed.size)
                }
            }
        }
        println("[C2] peor caso de pruebas del mínimo exacto: $worst")
    }

    @Test
    fun whenTheHintIsAlreadyTheMinimumOnlyItAndItsLowerNeighbourAreProbed() {
        val probed = mutableListOf<Int>()
        assertEquals(31, exactMinimum(20, 31, probed) { minutes -> minutes >= 31 })
        assertEquals("la conjetura y su vecino de abajo", listOf(31, 30), probed)

        val adjacent = mutableListOf<Int>()
        assertEquals(21, exactMinimum(20, 21, adjacent) { minutes -> minutes >= 21 })
        assertEquals("el vecino de abajo es el rechazo que se explica: no se vuelve a probar", listOf(21), adjacent)
    }

    @Test
    fun aMistakenHintStillEndsAtTheExactMinimum() {
        // Conjetura demasiado alta: es viable y su vecino también, así que se bisecciona hacia abajo.
        val high = mutableListOf<Int>()
        assertEquals(30, exactMinimum(20, 45, high) { minutes -> minutes >= 30 })
        assertEquals(listOf(45, 44), high.take(2))

        // Conjetura demasiado baja (no viable): se prueba el techo y se bisecciona entre el presupuesto y el techo.
        val low = mutableListOf<Int>()
        assertEquals(50, exactMinimum(20, 25, low) { minutes -> minutes >= 50 })
        assertEquals(listOf(25, 100), low.take(2))
    }

    @Test
    fun theExactMinimumIsNullWhenEvenTheCeilingIsNotViable() {
        val withHint = mutableListOf<Int>()
        assertNull(exactMinimum(20, 31, withHint) { false })
        assertEquals("la conjetura y el techo, nada más", listOf(31, 100), withHint)

        val withoutHint = mutableListOf<Int>()
        assertNull(exactMinimum(20, null, withoutHint) { false })
        assertEquals(listOf(100), withoutHint)

        val ceilingHint = mutableListOf<Int>()
        assertNull(exactMinimum(20, 100, ceilingHint) { false })
        assertEquals("el techo no se prueba dos veces", listOf(100), ceilingHint)

        val spent = mutableListOf<Int>()
        assertNull(exactMinimum(100, 100, spent) { true })
        assertTrue("con 100 min ya no hay presupuesto mayor que ofrecer", spent.isEmpty())
    }

    @Test
    fun theExactMinimumIsAnExactEdgeEvenWhenViabilityDoesNotGrowWithTheBudget() {
        // Viable en 50..52 y desde 80: la bisección encuentra un borde exacto (no necesariamente el primero), que es lo
        // que exige el contrato de cobertura (viable con n y no viable con n - 1).
        val viable = (50..52).toSet() + (80..100).toSet()
        val probed = mutableListOf<Int>()
        val minimum = requireNotNull(exactMinimum(20, null, probed) { minutes -> minutes in viable })
        assertTrue("$minimum debe ser viable", minimum in viable)
        assertTrue("${minimum - 1} no debe ser viable", (minimum - 1) !in viable)
    }

    @Test
    fun theExactMinimumPropagatesCancellationAndEveryOtherException() {
        try {
            SimpleCyclePersonalizer.exactMinimumMinutes(20, 31) { throw CancellationException("cancelado") }
            fail("la cancelación debe propagarse")
        } catch (expected: CancellationException) {
            assertEquals("cancelado", expected.message)
        }
        try {
            SimpleCyclePersonalizer.exactMinimumMinutes(20, null) { throw IllegalStateException("roto") }
            fail("una excepción cualquiera no se traga")
        } catch (expected: IllegalStateException) {
            assertEquals("roto", expected.message)
        }
    }
}
