package com.example.kpkn.domain.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicy
import com.example.kpkn.domain.auge.InitialRecoveryPolicyInput
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
 * Pipeline de dominio: check-in parcial guardado por el commit del alta →
 * `RingsCoverage.fromEngineInputs` (la función a la que delega el productor).
 * Los canales que el check-in no tocó siguen «sin datos»: nunca se presentan
 * como conocidos ni como 100 % afirmativo. El ensamblado real
 * `AugeViewModel → AugeSnapshot.coverage` se cubre en `AugeSnapshotCoverageTest`.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupRingsHomeCoverageTest {
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

    private fun noEvidenceContribution() = InitialRecoveryEvidencePolicy.resolve(
        InitialRecoveryPolicyInput(evidence = null, nowMs = 1_000L),
    )

    @Test
    fun partialCheckInSaveExposesOnlyItsChannelAndKeepsUntouchedChannelsAsNoData() = runBlocking {
        // 1. Guarda REAL: UNKNOWN + única sensación muscular, vía el commit del alta.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-partial-home", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)),
        )
        val saved = requireNotNull(db.augeDao().getWellbeingForDate(date)?.toWellbeingLog())

        // 2. Cobertura de Home con las mismas entradas del motor (sin historial,
        //    sin evidencia inicial), por CANAL.
        val contribution = noEvidenceContribution()
        val score: (RecoveryChannelId) -> Int = { channel ->
            if (channel == RecoveryChannelId.MUSCULAR) 75 else 100
        }
        val coverage = RingsCoverage.fromEngineInputs(
            historyIsEmpty = true,
            wellbeing = saved,
            contribution = contribution,
            declaredChannels = declaredCheckInChannels(saved),
            score = score,
        )

        assertEquals(setOf(RecoveryChannelId.MUSCULAR), declaredCheckInChannels(saved))
        assertEquals(RingsCoverageSource.SUBJECTIVE_SENSATION, coverage.muscular.source)
        assertTrue(coverage.muscular.hasData)
        assertTrue(coverage.muscular.isEstimated)
        assertEquals(75, coverage.muscular.score)
        // Los canales NO tocados por el check-in no se afirman: sin datos.
        listOf(coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertFalse(channel.hasData)
            assertNull(channel.score)
        }
        assertTrue(coverage.hasAnyData)

        // 3. La vista previa (evaluate con el check-in declarado) llega al MISMO
        //    resultado por canal que el productor del snapshot (fromEngineInputs).
        assertEquals(
            RingsCoverage.evaluate(
                historyIsEmpty = true,
                wellbeing = saved,
                contribution = contribution,
                subjective = mapping.checkIn,
                score = score,
            ),
            coverage,
        )

        // 4. Contraste documental: la etiqueta GLOBAL que antes pintaba Home marca
        //    los tres canales como «Ajuste manual» con el 100 del motor; por eso esa
        //    ruta ya no se usa en la UI (solo queda este contraste como evidencia).
        val legacy = RingsCoverage.fromBatteries(
            GlobalBatteries(muscular = 75, cnc = 100, spinal = 100, sourceLabel = "Ajuste manual"),
        )
        assertEquals(RingsCoverageSource.MANUAL_CHECK_IN, legacy.system.source)
        assertEquals(100, legacy.system.score)
    }

    @Test
    fun onboardingSourceIdentifiesDeclaredChannelEvenWhenCapturedFieldsAreMissing() {
        // Fila del alta cuyo `capturedFields` no nombra el canal (formato antiguo):
        // la provenance ONBOARDING_INITIAL lo identifica igual.
        val row = DailyWellbeingLog(
            id = "legacy-onboarding",
            date = date,
            manualNeuralBattery = 60,
            source = WellbeingSource.ONBOARDING_INITIAL,
            capturedFields = emptySet(),
        )
        assertEquals(setOf(RecoveryChannelId.SYSTEM), declaredCheckInChannels(row))

        val coverage = RingsCoverage.fromEngineInputs(
            historyIsEmpty = true,
            wellbeing = row,
            contribution = noEvidenceContribution(),
            declaredChannels = declaredCheckInChannels(row),
            score = { 100 },
        )
        // La procedencia subjetiva/estimada se preserva más allá de la vista previa.
        assertEquals(RingsCoverageSource.SUBJECTIVE_SENSATION, coverage.system.source)
        assertTrue(coverage.system.isEstimated)
        assertEquals(RingsCoverageSource.NO_DATA, coverage.muscular.source)
        assertEquals(RingsCoverageSource.NO_DATA, coverage.structure.source)

        // Una fila NO proveniente del alta sin campos capturados no inventa canales.
        val plain = DailyWellbeingLog(
            id = "plain",
            date = date,
            manualNeuralBattery = 60,
            source = WellbeingSource.DAILY_CHECK_IN,
            capturedFields = emptySet(),
        )
        assertEquals(emptySet<RecoveryChannelId>(), declaredCheckInChannels(plain))
    }

    @Test
    fun nullWellbeingCoverageNeverAssertsOneHundredPercent() {
        val coverage = RingsCoverage.fromEngineInputs(
            historyIsEmpty = true,
            wellbeing = null,
            contribution = noEvidenceContribution(),
            score = { 100 },
        )
        listOf(coverage.muscular, coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertNull(channel.score)
            assertFalse(channel.isEstimated)
        }
    }
}
