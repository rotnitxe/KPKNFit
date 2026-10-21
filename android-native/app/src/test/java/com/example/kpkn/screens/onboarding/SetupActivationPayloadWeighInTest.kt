package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.BodyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Activación de los pesajes: el % de grasa y de músculo viajan con su pesaje (mismo instante, misma sesión). */
class SetupActivationPayloadWeighInTest {

    private val now = ZonedDateTime.of(2026, 10, 12, 20, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `un pesaje con grasa y musculo da tres filas con el mismo instante y la misma sesion`() {
        val draft = SetupWizardDraft(
            commitId = "c-1",
            historicalWeighIns = listOf(
                SetupWeighIn(id = "w1", dateIso = "2024-03-10", weightKg = 80.0, bodyFatPercent = 18.0, muscleMassPercent = 38.0),
            ),
        )
        val rows = SetupActivationPayload.bodyObservations(draft, nowEpochMs = now)
        assertEquals(3, rows.size)
        val weight = rows.single { it.id == "c-1/weighin/2024-03-10" }
        val fat = rows.single { it.id == "c-1/weighin/2024-03-10/fat" }
        val muscle = rows.single { it.id == "c-1/weighin/2024-03-10/muscle" }
        assertEquals(BodyMetric.WEIGHT, weight.metric)
        assertEquals(BodyMetric.BODY_FAT_PERCENT, fat.metric)
        assertEquals(18.0, fat.valueSi, 1e-9)
        assertEquals(BodyMetric.MUSCLE_MASS_PERCENT, muscle.metric)
        assertEquals(38.0, muscle.valueSi, 1e-9)
        assertEquals(weight.timestampEpochMs, fat.timestampEpochMs)
        assertEquals(weight.timestampEpochMs, muscle.timestampEpochMs)
        assertEquals(weight.sessionId, fat.sessionId)
        assertEquals(weight.sessionId, muscle.sessionId)
    }

    @Test
    fun `un pesaje sin porcentajes da solo la fila del peso`() {
        val draft = SetupWizardDraft(
            commitId = "c-1",
            historicalWeighIns = listOf(SetupWeighIn(id = "w2", dateIso = "2024-03-11", weightKg = 79.5)),
        )
        val rows = SetupActivationPayload.bodyObservations(draft, nowEpochMs = now)
        assertEquals(listOf("c-1/weighin/2024-03-11"), rows.map { it.id })
    }

    @Test
    fun `un porcentaje fuera de cero a cien rechaza el alta`() {
        val draft = SetupWizardDraft(
            commitId = "c-1",
            historicalWeighIns = listOf(SetupWeighIn(id = "w3", dateIso = "2024-03-12", weightKg = 80.0, bodyFatPercent = 150.0)),
        )
        val thrown = runCatching { SetupActivationPayload.bodyObservations(draft, nowEpochMs = now) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}
