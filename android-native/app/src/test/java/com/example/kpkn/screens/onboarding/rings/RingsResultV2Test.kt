package com.example.kpkn.screens.onboarding.rings

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupRingsCheckIn
import com.example.kpkn.domain.onboarding.SetupRingsResponseMapping
import com.example.kpkn.domain.onboarding.StagedRingsCheckIn
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/** Ajustes 0–100 de RINGS: lo que la persona ve se guarda tal cual, la Columna se invierte como en Inicio y las líneas explican. */
class RingsResultV2Test {

    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `un ajuste de 80 se guarda como 80 y no como 0`() {
        val staged = SetupRingsResponseMapping.withRingAdjustments(
            base = StagedRingsCheckIn(),
            energia = 80,
            columnaBattery = null,
            musculos = mapOf("Pectorales" to 80),
        )
        assertEquals(80, staged.manualNeuralBattery)
        assertEquals(80, staged.manualMuscleBatteries["Pectorales"])
        assertTrue("energy" in staged.capturedFields)
        assertTrue("muscle_batteries" in staged.capturedFields)
    }

    @Test
    fun `la Columna invertida, recalculada como en Inicio, vuelve al valor elegido con un margen de 1`() {
        for (floor in listOf(0, 35, 60, 100)) for (guard in listOf(20, 55, 90)) {
            // El blend de Inicio nunca pasa de 50 + (suelo + guardián) / 4: por encima de eso no hay columna que elegir.
            val reachable = minOf(100, 50 + (0.25 * floor + 0.25 * guard).toInt())
            for (desired in 0..reachable step 5) {
                val raw = AugeRecoveryEngine.invertStructureRingScore(desired, floor, listOf(guard))
                val shown = AugeRecoveryEngine.structureRingScore(raw, floor, listOf(guard))
                assertTrue("elegido $desired con suelo $floor y guardián $guard: Inicio muestra $shown", abs(shown - desired) <= 1)
            }
        }
    }

    @Test
    fun `por encima de lo alcanzable, Inicio muestra el máximo del blend y no el valor elegido`() {
        // Suelo articular 0 y guardián 100: el blend no pasa de 75. La torre muestra ese 75, nunca un 90 que no existe.
        val raw = AugeRecoveryEngine.invertStructureRingScore(90, 0, listOf(100))
        assertEquals(100, raw)
        assertEquals(75, AugeRecoveryEngine.structureRingScore(raw, 0, listOf(100)))
    }

    @Test
    fun `sin tocar nada no se escribe ningún ajuste`() {
        assertTrue(SetupRingsAnswers().manualRings.isEmpty())
        assertTrue(SetupRingsAnswers().manualMusclesV2.isEmpty())
        val legacy = SetupRingsResponseMapping.stageCheckIn(checkIn = SetupRingsCheckIn())
        assertTrue(legacy.isEmpty)
        val staged = SetupRingsResponseMapping.withRingAdjustments(legacy, energia = null, columnaBattery = null, musculos = emptyMap())
        assertTrue(staged.isEmpty)
        assertNull(staged.manualNeuralBattery)
        assertNull(staged.manualSpinalBattery)
    }

    @Test
    fun `la vista previa da la misma Columna que Inicio para el mismo estado`() {
        val batteries = GlobalBatteries(muscular = 70, cnc = 60, spinal = 55)
        val perMuscle = listOf(
            muscle("Dorsales", 40),
            muscle("Erectores Espinales", 80),
            muscle("Trapecio", 65),
            muscle("Pectorales", 90),
        ).associateBy { it.muscleName }
        val articular = mapOf(
            ArticularBattery.SHOULDER to ArticularBatteryState(recoveryScore = 50),
            ArticularBattery.LUMBAR to ArticularBatteryState(recoveryScore = 70),
        )
        // Lo que hace la vista previa del alta: el blend con los mismos suelo y guardianes que lee Inicio.
        val (floor, guards) = AugeRecoveryEngine.structureInputs(perMuscle, articular)
        val preview = AugeRecoveryEngine.structureRingScore(batteries.spinal, floor, guards)
        val inicio = AugeRecoveryEngine.calculateRecoveryDashboard(batteries, perMuscle, articular, wellbeing = null)
            .channels.first { it.id == RecoveryChannelId.STRUCTURE }.score
        assertEquals(inicio, preview)
    }

    @Test
    fun `sin datos de columna la línea dice que queda sin calibrar`() {
        val lines = ringsExplanations(SetupRingsAnswers(), coverage(muscular = 60, system = 70, structure = null), today)
        assertEquals("Sin datos de tu columna: queda sin calibrar.", lines[RecoveryChannelId.STRUCTURE])
    }

    @Test
    fun `un ajuste de la persona manda sobre la explicación`() {
        val answers = SetupRingsAnswers(manualRings = mapOf("columna" to 60))
        val lines = ringsExplanations(answers, coverage(muscular = 60, system = 70, structure = 60), today)
        assertEquals("Ajustado por ti.", lines[RecoveryChannelId.STRUCTURE])
    }

    @Test
    fun `sin entrenos declarados la línea dice que llegas descansado`() {
        val answers = SetupRingsAnswers(sessionDaysState = RingsSessionDaysState.DECLARED)
        val lines = ringsExplanations(answers, coverage(muscular = 60, system = 70, structure = 60), today)
        assertEquals("No entrenaste esta semana: llegas descansado.", lines[RecoveryChannelId.MUSCULAR])
    }

    @Test
    fun `la línea de músculos cuenta días y series desde el día más antiguo de la ventana`() {
        val answers = SetupRingsAnswers(
            sessionDaysState = RingsSessionDaysState.DECLARED,
            trainedDayOffsets = setOf(0, 2),
            groupSets = mapOf(RingsMuscleGroup.LEGS to 8, RingsMuscleGroup.BACK to 6),
        )
        val line = ringsExplanations(answers, coverage(muscular = 60, system = 70, structure = 60), today)[RecoveryChannelId.MUSCULAR]
        assertTrue("línea: $line", line?.startsWith("Por tus 2 días de entreno y 14 series desde el ") == true)
    }

    @Test
    fun `sin cobertura no hay nada que explicar`() {
        assertTrue(ringsExplanations(SetupRingsAnswers(), coverage = null, today = today).isEmpty())
    }

    @Test
    fun `los grupos del resultado cubren los 13 pilares una sola vez`() {
        val pillars = RINGS_MUSCLE_GROUPS.flatMap { it.second }
        assertEquals(InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.sorted(), pillars.sorted())
    }

    private fun muscle(name: String, score: Int) = MuscleRecoveryStatus(name, score, 0, 0, 0, RecoveryStatus.OPTIMAL)

    private fun coverage(muscular: Int?, system: Int?, structure: Int?): RingsCoverage = RingsCoverage(
        muscular = channel(RecoveryChannelId.MUSCULAR, muscular),
        system = channel(RecoveryChannelId.SYSTEM, system),
        structure = channel(RecoveryChannelId.STRUCTURE, structure),
    )

    private fun channel(id: RecoveryChannelId, score: Int?) = RingsChannelCoverage(
        channel = id,
        source = if (score == null) RingsCoverageSource.NO_DATA else RingsCoverageSource.INITIAL_ESTIMATE,
        uncertainty = if (score == null) 100 else 40,
        score = score,
    )
}
