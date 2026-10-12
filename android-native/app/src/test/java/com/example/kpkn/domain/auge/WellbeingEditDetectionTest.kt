package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `detectWellbeingEdits`: qué valores de un guardado de bienestar son ediciones de verdad frente a lo ya guardado.
 * Es lo que decide si un guardado reancla y si enseña a AUGE; las copias que reenvía el chequeo previo no cuentan.
 */
class WellbeingEditDetectionTest {

    private fun v2(battery: Int, anchorMs: Long) = ManualMuscleBatteryOverride(
        battery = battery,
        anchorEpochMs = anchorMs,
        automaticBatteryAtAnchor = 90,
    )

    private fun log(
        neural: Int? = null,
        spinal: Int? = null,
        muscular: Int? = null,
        v1: Map<String, Int> = emptyMap(),
        v2Muscles: Map<String, ManualMuscleBatteryOverride> = emptyMap(),
        anchorMs: Long? = null,
        notes: String? = null,
    ) = DailyWellbeingLog(
        id = "hoy",
        date = "2026-10-09",
        manualNeuralBattery = neural,
        manualSpinalBattery = spinal,
        manualMuscularBattery = muscular,
        manualMuscleBatteries = v1,
        manualMuscleOverridesV2 = v2Muscles,
        manualBatteryAnchorMs = anchorMs,
        notes = notes,
    )

    private val noEdits = WellbeingEdits(neural = null, spinal = null, muscular = null, muscles = emptyMap())

    /** Fila guardada con los tres globales y dos músculos en V1. */
    private fun stored(anchorMs: Long? = 5_000L) = log(
        neural = 60,
        spinal = 55,
        muscular = 70,
        v1 = mapOf("Pectorales" to 50, "Dorsales" to 40),
        anchorMs = anchorMs,
    )

    // ─── Copias ───────────────────────────────────────────────────────────────

    @Test
    fun exactCopyHasNoEdits() {
        val edits = detectWellbeingEdits(stored(), stored())

        assertEquals(noEdits, edits)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun copyWithAnotherAnchorOrNotesStillHasNoEdits() {
        // El ancla y las notas no son ajustes: lo que cuenta son los valores de los canales.
        val incoming = stored(anchorMs = 9_000L).copy(notes = "otra nota")

        assertTrue(detectWellbeingEdits(stored(), incoming).isEmpty())
    }

    @Test
    fun anUninformedGlobalIsNotAnEdit() {
        // Que el entrante no informe un canal no es «borrarlo»: el guardado lo conserva.
        val incoming = log(neural = null, spinal = 55, muscular = 70, v1 = mapOf("Pectorales" to 50, "Dorsales" to 40))

        assertEquals(noEdits, detectWellbeingEdits(stored(), incoming))
    }

    @Test
    fun v1MusclesTheIncomingOmitsAreNotEdits() {
        val incoming = log(neural = 60, spinal = 55, muscular = 70, v1 = mapOf("Pectorales" to 50))

        assertEquals(noEdits, detectWellbeingEdits(stored(), incoming))
    }

    // ─── Globales ─────────────────────────────────────────────────────────────

    @Test
    fun aChangedNeuralIsTheOnlyEdit() {
        val incoming = stored().copy(manualNeuralBattery = 72)

        assertEquals(
            WellbeingEdits(neural = 72, spinal = null, muscular = null, muscles = emptyMap()),
            detectWellbeingEdits(stored(), incoming),
        )
    }

    @Test
    fun aChangedSpinalIsTheOnlyEdit() {
        val incoming = stored().copy(manualSpinalBattery = 40)

        assertEquals(
            WellbeingEdits(neural = null, spinal = 40, muscular = null, muscles = emptyMap()),
            detectWellbeingEdits(stored(), incoming),
        )
    }

    @Test
    fun aChangedMuscularIsTheOnlyEdit() {
        val incoming = stored().copy(manualMuscularBattery = 85)

        assertEquals(
            WellbeingEdits(neural = null, spinal = null, muscular = 85, muscles = emptyMap()),
            detectWellbeingEdits(stored(), incoming),
        )
    }

    @Test
    fun aGlobalInformedWhereNothingWasStoredIsAnEdit() {
        val existing = log(spinal = 55)
        val incoming = log(neural = 60, spinal = 55)

        assertEquals(
            WellbeingEdits(neural = 60, spinal = null, muscular = null, muscles = emptyMap()),
            detectWellbeingEdits(existing, incoming),
        )
    }

    // ─── V1 por pilar ─────────────────────────────────────────────────────────

    @Test
    fun aV1ValueUnderTheDisplayKeyIsTheSameMuscleAsThePillar() {
        // «Pecho» es la clave de pantalla del pilar «Pectorales»: mismo valor = copia.
        val incoming = stored().copy(manualMuscleBatteries = mapOf("Pecho" to 50, "Dorsales" to 40))

        assertEquals(noEdits, detectWellbeingEdits(stored(), incoming))
    }

    @Test
    fun aChangedV1UnderTheDisplayKeyIsTheOnlyEditAndComesOutByPillar() {
        val incoming = stored().copy(manualMuscleBatteries = mapOf("Pecho" to 65, "Dorsales" to 40))

        assertEquals(
            WellbeingEdits(neural = null, spinal = null, muscular = null, muscles = mapOf("Pectorales" to 65)),
            detectWellbeingEdits(stored(), incoming),
        )
    }

    @Test
    fun aStoredDisplayKeyIsComparedByPillarToo() {
        // Las filas antiguas guardan la clave de pantalla; el entrante ya viene por pilar.
        val existing = log(v1 = mapOf("Deltoides Lateral" to 50))

        assertEquals(noEdits, detectWellbeingEdits(existing, log(v1 = mapOf("Deltoides" to 50))))
        assertEquals(
            WellbeingEdits(neural = null, spinal = null, muscular = null, muscles = mapOf("Deltoides" to 60)),
            detectWellbeingEdits(existing, log(v1 = mapOf("Deltoides" to 60))),
        )
    }

    @Test
    fun aMuscleThatWasNotStoredIsAnEditAndTheStoredOnesAreNot() {
        val incoming = stored().copy(
            manualMuscleBatteries = mapOf("Pectorales" to 50, "Dorsales" to 40, "Cuádriceps" to 75),
        )

        assertEquals(
            WellbeingEdits(neural = null, spinal = null, muscular = null, muscles = mapOf("Cuádriceps" to 75)),
            detectWellbeingEdits(stored(), incoming),
        )
    }

    // ─── V2 declaradas ────────────────────────────────────────────────────────

    @Test
    fun declaredV2OverridesAreNotChannelEdits() {
        // Las V2 son declaraciones por músculo con su propia ancla: no cuentan como edición de los canales
        // (el guardado las trata aparte) y el merge sigue dándoles prioridad sobre las guardadas.
        val incoming = stored().copy(
            manualMuscleOverridesV2 = mapOf("Pectorales" to v2(65, 9_000L), "Dorsales" to v2(35, 9_000L)),
        )

        assertEquals(noEdits, detectWellbeingEdits(stored(), incoming))
    }

    // ─── Sin fila guardada ────────────────────────────────────────────────────

    @Test
    fun withoutAStoredRecordEverythingInformedIsAnEdit() {
        val incoming = log(neural = 60, spinal = 55, muscular = 70, v1 = mapOf("Pecho" to 80, "Dorsales" to 40))

        assertEquals(
            WellbeingEdits(
                neural = 60,
                spinal = 55,
                muscular = 70,
                muscles = mapOf("Pectorales" to 80, "Dorsales" to 40),
            ),
            detectWellbeingEdits(null, incoming),
        )
    }

    @Test
    fun withoutAStoredRecordNothingInformedIsNoEdit() {
        assertTrue(detectWellbeingEdits(null, log(notes = "solo una nota")).isEmpty())
    }

    // ─── El merge sigue la misma regla ────────────────────────────────────────

    @Test
    fun mergeTakesTheIncomingChannelsAndAnchorExactlyWhenThereAreEdits() {
        val existing = stored(anchorMs = 5_000L)
        val cases = mapOf(
            "copia exacta" to stored(anchorMs = 9_000L),
            "sin ajustes" to log(anchorMs = 9_000L),
            "solo V2" to log(v2Muscles = mapOf("Pectorales" to v2(65, 9_000L)), anchorMs = 9_000L),
            "Energía distinta" to stored(anchorMs = 9_000L).copy(manualNeuralBattery = 72),
            "Columna distinta" to stored(anchorMs = 9_000L).copy(manualSpinalBattery = 40),
            "Músculos distinto" to stored(anchorMs = 9_000L).copy(manualMuscularBattery = 85),
            "V1 distinta" to stored(anchorMs = 9_000L).copy(manualMuscleBatteries = mapOf("Pecho" to 65)),
            "V1 nueva" to stored(anchorMs = 9_000L).copy(manualMuscleBatteries = mapOf("Cuádriceps" to 75)),
        )

        cases.forEach { (name, incoming) ->
            val edits = detectWellbeingEdits(existing, incoming)
            val merged = mergeWellbeingForSave(existing, incoming)

            val expectedAnchor = if (edits.isEmpty()) existing.manualBatteryAnchorMs else incoming.manualBatteryAnchorMs
            assertEquals("ancla en «$name»", expectedAnchor, merged.manualBatteryAnchorMs)
            val expectedNeural = if (edits.isEmpty()) existing.manualNeuralBattery else incoming.manualNeuralBattery
            assertEquals("Energía en «$name»", expectedNeural, merged.manualNeuralBattery)
        }
        assertFalse(
            "la prueba debe cubrir al menos un caso con ediciones",
            cases.values.all { detectWellbeingEdits(existing, it).isEmpty() },
        )
    }
}
