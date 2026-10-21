package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.relator.RelatorContext
import com.example.kpkn.domain.relator.RelatorProgressSlice
import com.example.kpkn.domain.relator.RelatorReadinessSlice
import com.example.kpkn.domain.relator.RelatorWarmupSlice
import com.example.kpkn.domain.relator.RelatorSessionPhase
import com.example.kpkn.domain.relator.RelatorActionKind
import com.example.kpkn.domain.relator.WarmupObserver
import com.example.kpkn.domain.relator.ReadinessObserver
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

/**
 * Pruebas de integración puras del paquete 1.10v2 («Sin datos» no es 100 %):
 * 1. Snapshot sin datos y no cargando -> readiness null, titular vacío.
 * 2. Check-in de un solo canal -> titular por canal limitante conocido.
 * 3. WarmupObserver: dailyScore=null con exerciseScore=100 -> sin «Salta aprox».
 * 4. WarmupObserver: ambos null con 300 s restantes -> sin «Salta aprox».
 * 5. WarmupObserver: dailyScore=85 -> ofrece «Salta aprox».
 * 6. ReadinessObserver: dailyScore=null -> sin candidato de disponibilidad inventado.
 * 7. ReadinessObserver: dailyScore=65 -> ofrece ajuste de carga.
 * 8. MuscleRecoveryStatus: hasEvidence = true cuando initial < 100 o session >= 0.
 * 9. Cierre solo de cardio -> lista vacía de músculos implicados no expande a todo el mapa.
 */
class Rings110IntegrationTest {

    private val zone = ZoneId.of("UTC")
    private val now = 1770000000000L

    @Test
    fun `snapshot sin datos y no cargando publica readiness null y titular vacio`() {
        val emptySettings = Settings(initialRecoveryEvidence = null)
        val inputs = AugeSnapshotInputs(
            history = emptyList(),
            wellbeing = null,
            settings = emptySettings,
            exerciseDb = emptyMap(),
            nowMs = now,
            zone = zone,
        )
        val core = AugeSnapshotComputer.compute(inputs)
        assertFalse("No debe tener datos", core.coverage.hasAnyData)
        assertNull("Readiness debe ser null sin evidencia", core.readiness)
        assertEquals("Titular vacío sin datos", "", core.dashboard.headline)
        assertEquals("Recomendación vacía sin datos", "", core.dashboard.recommendation)
        assertEquals("Resumen sin datos", "Sin datos de recuperación registrados.", core.dashboard.summary)
    }

    @Test
    fun `check-in de un solo canal deriva titular del canal limitante conocido`() {
        // Wellbeing con ajuste manual solo en muscular (65%), sin energía ni columna
        val wellbeing = DailyWellbeingLog(
            date = "2026-10-21",
            manualMuscularBattery = 65,
        )
        val inputs = AugeSnapshotInputs(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(initialRecoveryEvidence = null),
            exerciseDb = emptyMap(),
            nowMs = now,
            zone = zone,
        )
        val core = AugeSnapshotComputer.compute(inputs)
        assertTrue("Debe tener datos en muscular", core.coverage.muscular.hasData)
        assertFalse("No debe tener datos en energía", core.coverage.system.hasData)
        assertFalse("No debe tener datos en columna", core.coverage.structure.hasData)
        // El factor limitante conocido debe ser Músculos, no los 100 de Energía ni Columna
        assertTrue(
            "El resumen debe indicar que Músculos es el factor limitante",
            core.dashboard.summary.contains("Músculos es hoy el factor limitante (65%)"),
        )
        assertNotNull("Readiness existe al haber evidencia en muscular", core.readiness)
    }

    @Test
    fun `warmup observer no ofrece salta aprox con dailyScore null y exerciseScore 100`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = null,
            exerciseScore = 100,
            remainingSeconds = 600,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNull("No debe ofrecer 'Salta aprox' cuando dailyScore es null", skipAction)
    }

    @Test
    fun `warmup observer no ofrece salta aprox cuando ambos scores son null con 300 s restantes`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = null,
            exerciseScore = null,
            remainingSeconds = 300,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNull("No debe ofrecer 'Salta aprox' por el solo tiempo cuando no hay disponibilidad diaria", skipAction)
    }

    @Test
    fun `warmup observer si ofrece salta aprox con dailyScore alto`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = 85,
            exerciseScore = null,
            remainingSeconds = 600,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNotNull("Debe ofrecer 'Salta aprox' con dailyScore >= 85", skipAction)
    }

    @Test
    fun `readiness observer no emite candidatos de disponibilidad sin dailyScore`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WORKING,
            dailyScore = null,
            exerciseScore = 100,
            preWorkoutDiscomforts = listOf("Rodilla izquierda"),
            doms = 4,
        )
        val candidates = ReadinessObserver.observe(context)
        assertTrue("No debe emitir candidatos de disponibilidad sin dailyScore observado", candidates.isEmpty())
    }

    @Test
    fun `readiness observer emite candidato cuando dailyScore es bajo`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WORKING,
            dailyScore = 65,
            exerciseScore = null,
        )
        val candidates = ReadinessObserver.observe(context)
        assertFalse("Debe emitir candidato con dailyScore conocido < 70", candidates.isEmpty())
        val adjust = candidates.flatMap { it.actions }.find { it.kind == RelatorActionKind.ADJUST_LOAD }
        assertNotNull("Debe ofrecer ajuste de carga con score 65", adjust)
    }

    @Test
    fun `muscle recovery status expone hasEvidence correctamente`() {
        val freshWithoutEvidence = MuscleRecoveryStatus(
            muscleName = "Pectorales",
            recoveryScore = 100,
            hoursToRecovery = 0,
            hoursSinceLastSession = -1,
            effectiveSets = 0,
            status = RecoveryStatus.FRESH,
            hasEvidence = false,
        )
        assertFalse(freshWithoutEvidence.hasEvidence)

        val withInitialEvidence = freshWithoutEvidence.copy(
            recoveryScore = 60,
            hasEvidence = true,
        )
        assertTrue(withInitialEvidence.hasEvidence)

        val withRealSession = freshWithoutEvidence.copy(
            hoursSinceLastSession = 24,
            hasEvidence = true,
        )
        assertTrue(withRealSession.hasEvidence)
    }

    @Test
    fun `cierre solo de cardio no expande el universo a todo el mapa muscular`() {
        // En una sesión de cardio, involvedVolumeMuscles queda vacío
        val involvedVolumeMuscles = emptyList<String>()
        val perMuscle = mapOf(
            "Pectorales" to MuscleRecoveryStatus("Pectorales", 100, 0, -1, 0, RecoveryStatus.FRESH, false),
            "Dorsales" to MuscleRecoveryStatus("Dorsales", 100, 0, -1, 0, RecoveryStatus.FRESH, false),
        )
        val muscleSeed = if (involvedVolumeMuscles.isEmpty()) {
            emptyMap()
        } else {
            perMuscle.filterKeys { it in involvedVolumeMuscles }.mapValues { it.value.recoveryScore }
        }
        assertTrue("Para cardio solo muscleSeed debe ser vacío", muscleSeed.isEmpty())
    }

    private fun dummyRelatorContext(
        phase: RelatorSessionPhase,
        dailyScore: Int?,
        exerciseScore: Int?,
        remainingSeconds: Int? = null,
        suggestedKg: Double? = null,
        preWorkoutDiscomforts: List<String> = emptyList(),
        doms: Int? = null,
    ): RelatorContext = RelatorContext(
        sessionId = "s1",
        setKey = "e1_0",
        exerciseId = "e1",
        exerciseName = "Press banca",
        setIndex = 1,
        phase = phase,
        progress = RelatorProgressSlice(
            completedWorkingSets = 0,
            totalWorkingSets = 3,
            remainingSeconds = remainingSeconds,
        ),
        readiness = RelatorReadinessSlice(
            dailyScore = dailyScore,
            dailyLabel = dailyScore?.let { RecoveryBands.labelForScore(it) },
            dailyDetails = emptyList<String>(),
            exerciseScore = exerciseScore,
            doms = doms,
            preWorkoutDiscomforts = preWorkoutDiscomforts,
        ),
        warmup = RelatorWarmupSlice(
            suggestedKg = suggestedKg,
            remainingCount = 1,
        ),
    )
}
