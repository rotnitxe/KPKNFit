package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.domain.onboarding.declaredCheckInChannels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Procedencia de la fila al guardar el chequeo previo (paquete 1.2b, segunda mitad de P-05).
 *
 * `mergeWellbeingForSave` tomaba `source` del entrante: el chequeo previo al entreno vuelve `DAILY_CHECK_IN` una fila que
 * nació en el alta (`ONBOARDING_INITIAL`), y `declaredCheckInChannels` usa esa procedencia como respaldo para las filas sin
 * `capturedFields`: lo que el alta declaró dejaba de contar como declarado. Regla: una fila que nació en el alta conserva su
 * procedencia; en cualquier otro caso vale la del entrante. Fechas fijas: ningún caso usa reloj.
 */
class WellbeingSourceMergeTest {

    private fun row(
        source: WellbeingSource,
        neural: Int? = null,
        v1: Map<String, Int> = emptyMap(),
        captured: Set<String> = emptySet(),
        anchorMs: Long? = null,
    ) = DailyWellbeingLog(
        id = "hoy",
        date = "2026-10-12",
        manualNeuralBattery = neural,
        manualMuscleBatteries = v1,
        manualBatteryAnchorMs = anchorMs,
        source = source,
        capturedFields = captured,
    )

    /** La fila del alta tal como llega a un registro antiguo: Energía y un músculo declarados, sin `capturedFields`. */
    private fun onboardingRow() = row(
        source = WellbeingSource.ONBOARDING_INITIAL,
        neural = 70,
        v1 = mapOf("Pectorales" to 80),
        anchorMs = 5_000L,
    )

    /** Lo que reenvía el chequeo previo: la fila de hoy copiada tal cual, ya como chequeo diario. */
    private fun preWorkoutCopyOf(stored: DailyWellbeingLog) = stored.copy(
        source = WellbeingSource.DAILY_CHECK_IN,
        manualBatteryAnchorMs = null,
        capturedFields = emptySet(),
    )

    @Test
    fun anOnboardingRow_keepsItsSource_whenThePreWorkoutCheckInRewritesIt() {
        val existing = onboardingRow()

        val merged = mergeWellbeingForSave(existing, preWorkoutCopyOf(existing))

        assertEquals(WellbeingSource.ONBOARDING_INITIAL, merged.source)
    }

    @Test
    fun anOnboardingRow_keepsItsSource_whateverTheIncomingSays() {
        val existing = onboardingRow()

        WellbeingSource.entries.forEach { incomingSource ->
            val merged = mergeWellbeingForSave(existing, preWorkoutCopyOf(existing).copy(source = incomingSource))

            assertEquals("entrante $incomingSource", WellbeingSource.ONBOARDING_INITIAL, merged.source)
        }
    }

    @Test
    fun theDeclaredChannelsOfTheOnboardingSurviveThePreWorkoutSave() {
        val existing = onboardingRow()
        assertEquals(
            "precondición: sin `capturedFields`, el alta se identifica por su procedencia",
            setOf(RecoveryChannelId.SYSTEM, RecoveryChannelId.MUSCULAR),
            declaredCheckInChannels(existing),
        )

        val merged = mergeWellbeingForSave(existing, preWorkoutCopyOf(existing))

        assertEquals(
            "lo que el alta declaró dejó de contar como declarado tras el chequeo previo",
            setOf(RecoveryChannelId.SYSTEM, RecoveryChannelId.MUSCULAR),
            declaredCheckInChannels(merged),
        )
    }

    @Test
    fun anyOtherStoredSource_followsTheIncoming() {
        val stored = listOf(WellbeingSource.DAILY_CHECK_IN, WellbeingSource.POST_SESSION, WellbeingSource.IMPORTED)

        stored.forEach { storedSource ->
            WellbeingSource.entries.forEach { incomingSource ->
                val merged = mergeWellbeingForSave(row(storedSource), row(incomingSource))

                assertEquals("guardada $storedSource, entrante $incomingSource", incomingSource, merged.source)
            }
        }
    }

    @Test
    fun withoutAStoredRow_theIncomingSourceIsKept() {
        WellbeingSource.entries.forEach { incomingSource ->
            assertEquals(incomingSource, mergeWellbeingForSave(null, row(incomingSource)).source)
        }
    }
}
