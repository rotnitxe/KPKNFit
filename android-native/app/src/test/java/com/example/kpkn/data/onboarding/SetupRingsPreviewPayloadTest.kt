package com.example.kpkn.data.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.onboarding.RingsCalibration
import com.example.kpkn.domain.onboarding.SetupRingsInput
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.domain.onboarding.SetupRingsResponseMapping
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Contrato de la vista previa de RINGS: usa el MISMO staging que el commit y la
 * fecha derivada del instante inyectado (no del reloj de pared), de modo que
 * preview y commit no divergen cerca de medianoche.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupRingsPreviewPayloadTest {
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
    fun previewDateComesFromTheInjectedInstantInsteadOfTheWallClock() {
        val nowMs = Instant.parse("2000-01-01T12:00:00Z").toEpochMilli()
        val laterMs = nowMs + 3L * 24L * 3_600_000L

        // Cambiar el instante cambia el día: si se usara LocalDate.now() ambos
        // serían idénticos (y arbitrarios).
        assertNotEquals(previewWellbeingDate(nowMs), previewWellbeingDate(laterMs))
        assertEquals(
            Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            previewWellbeingDate(nowMs),
        )
    }

    @Test
    fun mapperToPreviewPayloadProducesExactlyWhatTheCommitPersists() = runBlocking {
        val nowMs = Instant.parse("2000-01-01T12:00:00Z").toEpochMilli()
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2),
            nowMs = nowMs,
        )
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)

        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        // Payload REAL que consume el cálculo de la vista previa.
        val incoming = requireNotNull(stagePreviewWellbeing("commit-preview", staged, nowMs))
        assertEquals(previewWellbeingDate(nowMs), incoming.date)

        // Fila ya existente del día con un canal manual ajeno a este check-in.
        val existing = DailyWellbeingLog(
            id = "daily-check-in",
            date = incoming.date,
            manualNeuralBattery = 50,
            capturedFields = setOf("energy"),
        )
        db.augeDao().upsertWellbeing(existing.toEntity())
        // Mismo merge por campos que ejecuta la vista previa antes de pintar.
        val previewMerge = mergeSetupWellbeing(existing, incoming)

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-preview", null, Settings(), null, null, false, false,
                initialWellbeing = incoming),
        )

        val saved = db.augeDao().getWellbeingForDate(incoming.date)?.toWellbeingLog()
        // El id viejo se conserva; solo se escribe lo declarado.
        assertEquals("daily-check-in", saved?.id)
        assertEquals(75, saved?.manualMuscularBattery)
        assertEquals(50, saved?.manualNeuralBattery)
        assertEquals(setOf("energy", "muscular"), saved?.capturedFields)
        // Vista previa y commit producen exactamente el mismo resultado.
        assertEquals(previewMerge, saved)
    }
}
