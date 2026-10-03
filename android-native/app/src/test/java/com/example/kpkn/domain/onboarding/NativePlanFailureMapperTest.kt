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
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.PersonalizationResult
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.TrainingOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            report("TIME_BUDGET", 21, "Este plan necesita 20 min por sesión y no cabe; el mínimo real es de 21 min."),
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
            "PROFILE_MISMATCH" to (PlanEvaluationStage.PROFILE to PlanRejectionReason.PROFILE_MISMATCH),
            "COMPOSITION" to (PlanEvaluationStage.COMPOSITION to PlanRejectionReason.COMPOSITION),
        )
        expected.forEach { (code, pair) ->
            val failure = requireNotNull(NativePlanFailureMapper.typedFailure(report(code, null, "motivo $code")))
            assertEquals(code, pair.first, failure.stage)
            assertEquals(code, pair.second, failure.reason)
            assertNull("$code no inventa minutos", failure.requiredMinutes)
            assertEquals("motivo $code", failure.message)
        }
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
}
