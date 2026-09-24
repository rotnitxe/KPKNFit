package com.example.kpkn.domain.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.data.onboarding.toEvidencePatchField
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupRingsCheckInPersistenceTest {
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun explicitNoDiscomfortsReplacesExistingDiscomfortsOnFieldMerge() = runBlocking {
        val date = LocalDate.now().toString()
        db.augeDao().upsertWellbeing(
            DailyWellbeingLog("daily-check-in", date, preWorkoutDiscomforts = listOf("rodilla"),
                capturedFields = setOf("discomforts")).toEntity())
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = SetupRingsCheckIn(energy = 2),
            // NONE explícito: lista vacía que sí se guarda.
            discomforts = SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.NONE, emptyList()),
        )

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-none", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)))

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals("daily-check-in", saved?.id)
        assertEquals(emptyList<String>(), saved?.preWorkoutDiscomforts)
        assertEquals(setOf("discomforts", "energy"), saved?.capturedFields)
    }

    @Test
    fun unreportedDiscomfortsDoNotTouchExistingDiscomfortsOnFieldMerge() = runBlocking {
        val date = LocalDate.now().toString()
        db.augeDao().upsertWellbeing(
            DailyWellbeingLog("daily-check-in", date, preWorkoutDiscomforts = listOf("rodilla"),
                capturedFields = setOf("discomforts")).toEntity())
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = SetupRingsCheckIn(energy = 2),
            // No informado: no toca lo existente.
            discomforts = SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.NOT_ANSWERED, emptyList()),
        )
        assertNull(staged.preWorkoutDiscomforts)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-unreported", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)))

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(listOf("rodilla"), saved?.preWorkoutDiscomforts)
    }

    @Test
    fun partialCalibrationCommitsRealCheckInAndKeepsPreviousEvidenceTimestamp() = runBlocking {
        val prior = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = 5_000L,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.MODERATE,
            sensations = InitialRecoverySensations(3, 3, 3),
        )
        db.settingsDao().upsert(Settings(initialRecoveryEvidence = prior).toEntity())

        // Reanudar el alta con entrenamiento desconocido + sensaciones declaradas.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2, energy = 2, structureFeeling = 2,
                capturedAtMs = 5_000L),
            nowMs = 9_999_999L,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        val date = LocalDate.now().toString()
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-partial", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 9_999_999L),
                settingsPatch = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())))

        // La evidencia previa queda sin tocar: ni se renueva su fecha ni se prolonga su caducidad.
        val savedEvidence = db.settingsDao().get()?.toSettings()?.initialRecoveryEvidence
        assertEquals(prior, savedEvidence)
        assertEquals(5_000L, savedEvidence?.capturedAtMs)
        assertEquals(5_000L + 14L * 24L * 60L * 60L * 1_000L, savedEvidence?.expiresAtMs)
        // El check-in real sí se guarda, con la escala 1-5 convertida a porcentaje.
        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(75, saved?.manualMuscularBattery)
        assertEquals(75, saved?.manualNeuralBattery)
        assertEquals(75, saved?.manualSpinalBattery)
        assertEquals(setOf("muscular", "energy", "structure"), saved?.capturedFields)
    }
}
