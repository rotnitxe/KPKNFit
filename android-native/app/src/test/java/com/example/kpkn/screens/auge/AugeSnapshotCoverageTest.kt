package com.example.kpkn.screens.auge

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryChannelSnapshot
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.domain.onboarding.RingsCalibration
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.domain.onboarding.SetupRingsInput
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.domain.onboarding.SetupRingsResponseMapping
import com.example.kpkn.domain.onboarding.declaredCheckInChannels
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Productor real del snapshot: `AugeViewModel.recompute` publica
 * `AugeSnapshot.coverage` con [computeSnapshotCoverage], la MISMA función que
 * estas pruebas invocan con las entradas y el corte temporal del cálculo AUGE.
 * No es una prueba de UI: la aserción de que Home solo lee `snapshot.coverage`
 * es estructural (HomeRingsSection no hace IO ni usa etiquetas globales).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class AugeSnapshotCoverageTest {
    private lateinit var db: KpknDatabase
    private val date: String = LocalDate.now().toString()

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun channel(id: RecoveryChannelId, score: Int, confidence: Int) = RecoveryChannelSnapshot(
        id = id,
        title = id.name,
        shortTitle = id.name,
        score = score,
        band = RecoveryBand.NORMAL,
        description = "",
        action = "",
        confidence = confidence,
    )

    private fun dashboard(channels: List<RecoveryChannelSnapshot> = emptyList()) = RecoveryDashboard(
        overallScore = 50,
        headline = "",
        summary = "",
        recommendation = "",
        confidenceLabel = "—",
        channels = channels,
    )

    @Test
    fun uncalibratedBatteriesAreNeverPublishedAsKnownHundredPercent() {
        // Motor sin calibrar: 100/100/100 con etiqueta global «Sin calibrar».
        val coverage = computeSnapshotCoverage(
            history = emptyList(),
            settings = Settings(),
            wellbeing = null,
            dashboard = dashboard(),
            batteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 100, sourceLabel = "Sin calibrar"),
            nowMs = 1_000L,
        )
        listOf(coverage.muscular, coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertFalse(channel.hasData)
            assertNull(channel.score)
            assertFalse(channel.isEstimated)
        }
        assertEquals(RingsCoverage.NO_DATA, coverage)
    }

    @Test
    fun partialCheckInSavedBySetupCommitIsPublishedAsMuscularOnlyCoverage() = runBlocking {
        // 1. Guarda REAL: check-in parcial (UNKNOWN + única sensación) vía el commit.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-partial-auge", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)),
        )
        val saved = requireNotNull(db.augeDao().getWellbeingForDate(date)?.toWellbeingLog())

        // 2. El PRODUCTOR publica la cobertura con esa misma fila.
        val coverage = computeSnapshotCoverage(
            history = emptyList(),
            settings = Settings(),
            wellbeing = saved,
            dashboard = dashboard(),
            // Etiqueta global «Ajuste manual»: no debe invalidar/contaminar el resto.
            batteries = GlobalBatteries(muscular = 75, cnc = 100, spinal = 100, sourceLabel = "Ajuste manual"),
            nowMs = 1_000L,
        )

        assertEquals(setOf(RecoveryChannelId.MUSCULAR), declaredCheckInChannels(saved))
        assertEquals(RingsCoverageSource.SUBJECTIVE_SENSATION, coverage.muscular.source)
        assertTrue(coverage.muscular.hasData)
        assertTrue(coverage.muscular.isEstimated)
        assertEquals(75, coverage.muscular.score)
        // Canales no tocados por el check-in: sin dato, nunca «conocidos».
        listOf(coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertNull(channel.score)
        }
    }

    @Test
    fun coverageUsesDashboardChannelConfidenceAndPaintedScores() {
        val wellbeing = DailyWellbeingLog(
            id = "manual",
            date = date,
            manualMuscularBattery = 80,
            capturedFields = setOf("muscular"),
        )
        val coverage = computeSnapshotCoverage(
            history = emptyList(),
            settings = Settings(),
            wellbeing = wellbeing,
            dashboard = dashboard(
                listOf(
                    channel(RecoveryChannelId.MUSCULAR, score = 66, confidence = 70),
                    channel(RecoveryChannelId.SYSTEM, score = 100, confidence = 20),
                ),
            ),
            batteries = GlobalBatteries(muscular = 42, cnc = 100, spinal = 90),
            nowMs = 1_000L,
        )

        // El valor publicado es el que Home pinta (score del dashboard), no el crudo.
        assertEquals(66, coverage.muscular.score)
        // Incertidumbre por canal desde la confianza del dashboard.
        assertEquals(30, coverage.muscular.uncertainty)
        assertEquals(RingsCoverageSource.SUBJECTIVE_SENSATION, coverage.muscular.source)
        // Sin evidencia/historial/check-in en ese canal: nada de 100 afirmativo,
        // aunque el dashboard y el motor lo pinten con 100.
        assertEquals(RingsCoverageSource.NO_DATA, coverage.system.source)
        assertNull(coverage.system.score)
        assertEquals(RingsCoverageSource.NO_DATA, coverage.structure.source)
        assertNull(coverage.structure.score)
    }

    @Test
    fun evidenceExpiryBoundaryFollowsTheInjectedEvaluationCut() {
        // Evidencia inicial real (fábrica del dominio) y corte de evaluación
        // inyectado: el productor decide caducidad con `nowMs`, no con el reloj
        // del proceso. Es el umbral exacto que fallaría si cada motor usara su
        // propio reloj.
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = 1_000L,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.MODERATE,
            sensations = InitialRecoverySensations(3, 3, 3),
        )
        val settings = Settings(initialRecoveryEvidence = evidence)
        val batteries = GlobalBatteries(muscular = 80, cnc = 80, spinal = 80)
        val board = dashboard()

        // Un milisegundo antes de caducar: la evidencia inicial sigue aportando.
        val beforeExpiry = computeSnapshotCoverage(
            history = emptyList(),
            settings = settings,
            wellbeing = null,
            dashboard = board,
            batteries = batteries,
            nowMs = evidence.expiresAtMs - 1,
        )
        assertEquals(RingsCoverageSource.INITIAL_ESTIMATE, beforeExpiry.muscular.source)
        assertTrue(beforeExpiry.muscular.hasData)
        assertEquals(80, beforeExpiry.muscular.score)

        // En el corte exacto de caducidad ya no cuenta: sin historial ni check-in
        // no hay dato conocido (score null, nunca un 100 % afirmativo), aunque el
        // motor pinte 80.
        val atExpiry = computeSnapshotCoverage(
            history = emptyList(),
            settings = settings,
            wellbeing = null,
            dashboard = board,
            batteries = batteries,
            nowMs = evidence.expiresAtMs,
        )
        assertEquals(RingsCoverageSource.NO_DATA, atExpiry.muscular.source)
        assertNull(atExpiry.muscular.score)
        assertFalse(atExpiry.hasAnyData)
    }
}
