package com.example.kpkn.domain.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.data.onboarding.mergeSetupWellbeing
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.data.onboarding.toEvidencePatchField
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupRingsMergeTest {
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

    @Test
    fun fieldMergePreservesGlobalMuscularNeuralSpinalMusclesAndExplicitDiscomforts() = runBlocking {
        val v2 = ManualMuscleBatteryOverride(60, 1_000L, null, 88)
        db.augeDao().upsertWellbeing(
            DailyWellbeingLog(
                id = "daily-check-in", date = date,
                manualMuscularBattery = 100,
                manualNeuralBattery = 50,
                manualSpinalBattery = 80,
                manualMuscleBatteries = mapOf("espalda" to 40),
                manualMuscleOverridesV2 = mapOf("espalda" to v2),
                preWorkoutDiscomforts = listOf("rodilla", "hombro"),
                capturedFields = setOf("muscular", "energy", "structure", "muscle_batteries", "discomforts"),
            ).toEntity(),
        )
        // Síntomas parciales: muscular (de rodilla fresca) + molestias = NONE explícito.
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = SetupRingsCheckIn(muscular = 3),
            discomforts = SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.NONE, emptyList()),
        )
        assertTrue(staged.capturedFields.contains("muscular"))
        assertTrue(staged.capturedFields.contains("discomforts"))
        // Nivel 3 → 50; la sensación muscular global se conserva por canal existente.
        assertEquals(50, staged.manualMuscularBattery)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-merge", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 2_000L)),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals("daily-check-in", saved?.id)
        // Canal muscular global actualizado sin perder el resto de canales.
        assertEquals(50, saved?.manualMuscularBattery)
        assertEquals(50, saved?.manualNeuralBattery)
        assertEquals(80, saved?.manualSpinalBattery)
        assertEquals(mapOf("espalda" to 40), saved?.manualMuscleBatteries)
        assertEquals(mapOf("espalda" to v2), saved?.manualMuscleOverridesV2)
        // NONE explícito reemplaza las molestias previas; campos conservados en unión.
        assertEquals(emptyList<String>(), saved?.preWorkoutDiscomforts)
        assertEquals(
            setOf("muscular", "energy", "structure", "muscle_batteries", "discomforts"),
            saved?.capturedFields,
        )
    }

    @Test
    fun explicitZeroBatterySurvivesFieldMerge() = runBlocking {
        db.augeDao().upsertWellbeing(
            DailyWellbeingLog("daily-check-in", date, manualSpinalBattery = 60,
                capturedFields = setOf("structure")).toEntity(),
        )
        // Nivel 5 → batería 0: un cero explícito, no «no informado».
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = SetupRingsCheckIn(structure = 5))
        assertEquals(0, staged.manualSpinalBattery)
        assertTrue(staged.capturedFields.contains("structure"))

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-zero", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(0, saved?.manualSpinalBattery)
        assertEquals("daily-check-in", saved?.id)
    }

    @Test
    fun unreportedChannelsDoNotTouchExistingManualValues() = runBlocking {
        db.augeDao().upsertWellbeing(
            DailyWellbeingLog(
                id = "daily-check-in", date = date,
                manualMuscularBattery = 25,
                manualNeuralBattery = 75,
                manualSpinalBattery = 55,
                capturedFields = setOf("muscular", "energy", "structure"),
            ).toEntity(),
        )
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = SetupRingsCheckIn(muscular = 2))

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-partial", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(75, saved?.manualMuscularBattery)
        assertEquals(75, saved?.manualNeuralBattery)
        assertEquals(55, saved?.manualSpinalBattery)
        assertEquals(setOf("muscular", "energy", "structure"), saved?.capturedFields)
    }

    @Test
    fun partialCheckInWithoutMuscleScopePreservesGlobalMuscleFeeling() = runBlocking {
        // Sin evidence histórica y sin muscle scope: sigue siendo check-in parcial real.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2, energy = 4, structureFeeling = 3),
            nowMs = 1_000L,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        assertNull(mapping.evidence)
        assertEquals(SetupRingsCheckIn(muscular = 2, energy = 4, structure = 3), mapping.previewCheckIn())

        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        assertEquals(75, staged.manualMuscularBattery)
        assertEquals(setOf("muscular", "energy", "structure"), staged.capturedFields)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-partial-global", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L),
                settingsPatch = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(75, saved?.manualMuscularBattery)
        assertEquals(25, saved?.manualNeuralBattery)
        assertEquals(50, saved?.manualSpinalBattery)
        assertNull(db.settingsDao().get()?.toSettings()?.initialRecoveryEvidence)
    }

    @Test
    fun unknownMuscleFeelingOnlyFlowsFromMapperThroughSharedMergeForCommitAndPreview() = runBlocking {
        val existing = DailyWellbeingLog(
            id = "daily-check-in", date = date,
            manualMuscularBattery = 100,
            manualNeuralBattery = 50,
            manualSpinalBattery = 80,
            capturedFields = setOf("muscular", "energy", "structure"),
        )
        db.augeDao().upsertWellbeing(existing.toEntity())
        // Payload REAL del mapper: UNKNOWN + único muscleFeeling (no se fabrica
        // el check-in a mano; sin energy/structure ni evidencia histórica).
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        assertEquals(SetupRingsCheckIn(muscular = 2), mapping.previewCheckIn())
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        assertEquals(75, staged.manualMuscularBattery)
        assertEquals(setOf("muscular"), staged.capturedFields)
        val incoming = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)

        // La vista previa consume exactamente este mismo merge compartido con
        // la fila de hoy como base: el canal muscular global debe coincidir.
        val previewMerge = mergeSetupWellbeing(existing, incoming)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-unknown-muscle", null, Settings(), null, null, false, false,
                initialWellbeing = incoming),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals("daily-check-in", saved?.id)
        // Batería muscular global proveniente del mapper (nivel 2 → 75); los
        // canales no informados por el check-in parcial quedan intactos.
        assertEquals(75, saved?.manualMuscularBattery)
        assertEquals(50, saved?.manualNeuralBattery)
        assertEquals(80, saved?.manualSpinalBattery)
        // Preview y commit producen el mismo resultado sobre la misma base.
        assertEquals(saved, previewMerge)
    }

    @Test
    fun ringsCommitDoesNotSynthesizeSleepStressOrExtraDates() = runBlocking {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 3),
            nowMs = 1_000L,
        )
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-no-synth", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L)),
        )

        // Una sola fila, la de HOY: no se fabrican logs de sueño/estrés ni otras fechas.
        val all = db.augeDao().getAllWellbeing()
        assertEquals(1, all.size)
        assertEquals(date, all.single().date)
        val saved = all.single().toWellbeingLog()
        assertNotNull("La fila del check-in debe persistirse", saved)
        val persisted = checkNotNull(saved) { "La fila del check-in no se persistió" }
        assertNull(persisted.sleepHours)
        assertEquals(3, persisted.stressLevel)
        assertEquals(setOf("muscular"), persisted.capturedFields)
    }

    @Test
    fun declaredAbsentWithPartialFeelingsSavesEvidenceAndOnlyDeclaredChannels() = runBlocking {
        // Historia conocida (sin entrenamiento reciente) + UNA sensación: la evidencia
        // se fabrica con la sensación ausente en `null`, jamás con un valor por defecto.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTraining = false, muscleFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)

        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        assertEquals(setOf("muscular"), staged.capturedFields)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-absent-partial", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L),
                settingsPatch = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())),
        )

        val evidence = db.settingsDao().get()?.toSettings()?.initialRecoveryEvidence
        assertNotNull(evidence)
        assertEquals(0, evidence?.sessions)
        assertEquals(InitialRecoverySensations(muscular = 2, energy = null, structure = null), evidence?.sensations)

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals("onboarding-check-in", saved?.id)
        assertEquals(75, saved?.manualMuscularBattery)
        assertNull(saved?.manualNeuralBattery)
        assertNull(saved?.manualSpinalBattery)
        assertEquals(setOf("muscular"), saved?.capturedFields)
    }

    @Test
    fun knownRecentTrainingWithPartialFeelingsSavesEvidenceAndOnlyDeclaredChannels() = runBlocking {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(
                recentTraining = true,
                sessions = 4,
                recencyDays = 2,
                activityType = InitialRecoveryActivityType.MIXED,
                intensity = InitialRecoveryIntensity.MODERATE,
                muscleFeeling = 3,
                structureFeeling = 4,
            ),
            nowMs = 2_000L,
        )
        assertEquals(RingsCompletion.VALID, mapping.completion)
        val evidence = requireNotNull(mapping.evidence)
        assertEquals(4, evidence.sessions)
        assertEquals(InitialRecoverySensations(muscular = 3, energy = null, structure = 4), evidence.sensations)

        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-known-partial", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 2_000L),
                settingsPatch = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())),
        )

        val saved = db.augeDao().getWellbeingForDate(date)?.toWellbeingLog()
        assertEquals(50, saved?.manualMuscularBattery)
        assertEquals(25, saved?.manualSpinalBattery)
        assertNull(saved?.manualNeuralBattery)
        assertEquals(setOf("muscular", "structure"), saved?.capturedFields)
        val savedEvidence = db.settingsDao().get()?.toSettings()?.initialRecoveryEvidence
        assertEquals(InitialRecoverySensations(muscular = 3, energy = null, structure = 4), savedEvidence?.sensations)
    }

    @Test
    fun fullEvidenceKeepsDeclaredCheckInAndEngineAppliesOneSourcePerChannel() = runBlocking {
        // Contrato FULL: las sensaciones declaradas se conservan en la evidencia
        // Y en la fila de check-in (preview y commit usan el mismo staging), sin
        // que el motor sume las dos fuentes.
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTraining = false, muscleFeeling = 2, energy = 2, structureFeeling = 1),
            nowMs = 1_000L,
        )
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        assertEquals(setOf("muscular", "energy", "structure"), staged.capturedFields)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-full-single-source", null, Settings(), null, null, false, false,
                initialWellbeing = staged.toWellbeingLog("onboarding-check-in", date, 1_000L),
                settingsPatch = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())),
        )

        val persisted = requireNotNull(db.settingsDao().get()?.toSettings())
        val row = requireNotNull(db.augeDao().getWellbeingForDate(date)?.toWellbeingLog())
        val evidence = requireNotNull(persisted.initialRecoveryEvidence)

        // 1) La evidencia conserva TODAS las sensaciones declaradas...
        assertEquals(InitialRecoverySensations(muscular = 2, energy = 2, structure = 1), evidence.sensations)
        assertEquals(96, evidence.muscularScore)
        // 2) ...y la fila también (procedencia por canal para Home, que es lo que
        //    sigue existiendo cuando la evidencia caduca o se quita).
        assertEquals(setOf("muscular", "energy", "structure"), row.capturedFields)
        assertEquals(75, row.manualMuscularBattery)

        // 3) El motor aplica UNA fuente por canal: manda el check-in del día (75)
        //    y NO se suma la estimación de la evidencia (96) → sin doble fatiga.
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = row,
            settings = persisted,
            nowOverrideMs = 1_000L,
        )
        assertEquals(75, batteries.muscular)
        assertNotEquals(evidence.muscularScore, batteries.muscular)
    }
}