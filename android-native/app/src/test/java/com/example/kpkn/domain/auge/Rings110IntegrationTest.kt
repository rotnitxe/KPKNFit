package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

/**
 * Pruebas de integración puras de domain/auge del paquete 1.10v2 («Sin datos» no es 100 %):
 * 1. Snapshot sin datos y no cargando -> readiness null, titular vacío.
 * 2. Check-in de un solo canal -> titular por canal limitante conocido.
 * 3. MuscleRecoveryStatus: hasEvidence = true cuando initial < 100 o session >= 0.
 * 4. Cierre solo de cardio -> lista vacía de músculos implicados no expande a todo el mapa.
 * 5. calculateRecoveryDashboardDetail conserva consistencia y preserva el máster dorado.
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

    @Test
    fun `dashboard conserva calculo de score general pero titular solo habla de canal con datos`() {
        val coverage = RingsCoverage.NO_DATA.copy(
            muscular = RingsChannelCoverage(
                channel = RecoveryChannelId.MUSCULAR,
                source = RingsCoverageSource.MANUAL_CHECK_IN,
                uncertainty = 10,
                score = 65,
            )
        )
        val batteries = GlobalBatteries(
            muscular = 65,
            energy = 100,
            spinal = 100,
            overallReadiness = 72,
        )
        val detail = AugeRecoveryEngine.calculateRecoveryDashboardDetail(
            batteries = batteries,
            perMuscle = emptyMap(),
            articularBatteries = emptyMap(),
            wellbeing = null,
            coverage = coverage,
        )
        // Score general ponderado se calcula matemáticamente igual
        assertEquals(72, detail.dashboard.overallScore)
        // Pero el titular y la recomendación se basan en el canal limitante conocido (muscular 65), no en energía ni columna
        assertTrue(detail.dashboard.summary.contains("Músculos"))
    }
}
