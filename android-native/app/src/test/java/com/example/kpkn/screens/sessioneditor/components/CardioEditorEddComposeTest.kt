package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-D · C4 a C7: el cardio continuo con sus avisos, el objetivo de trabajo del cardio por series, el circuito de intervalos y el
 * selector de cardio del catálogo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class CardioEditorEddComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Card(details: CardioDetails, onChange: (CardioDetails) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CardioEditorCard(
                details = details,
                accentColor = SymbolPalette.columna,
                onChange = onChange,
            )
        }
    }

    private fun continuo() = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        targetDurationSeconds = 20 * 60,
    )

    private fun series() = CardioDetails(
        type = CardioType.ROW_MACHINE,
        // Sin celda de distancia: «Distancia» es solo el segmento del objetivo de trabajo.
        supportsDistance = false,
        hiit = CardioHiitConfig(
            warmupSeconds = 300,
            workSeconds = 30,
            restSeconds = 60,
            rounds = 8,
            cooldownSeconds = 180,
            targetRpe = 9.0,
        ),
    )

    private fun intervalos() = CardioDetails(
        type = CardioType.TREADMILL,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "b1", type = CardioBlockType.WARMUP, durationSeconds = 300, intensityLevel = 3, speedKmh = 6.0),
            CardioIntervalBlock(id = "b2", type = CardioBlockType.WORK, durationSeconds = 180, intensityLevel = 8, speedKmh = 11.0),
            CardioIntervalBlock(id = "b3", type = CardioBlockType.RECOVER, durationSeconds = 120, intensityLevel = 3, speedKmh = 5.0),
            CardioIntervalBlock(id = "b4", type = CardioBlockType.WORK, durationSeconds = 180, intensityLevel = 9, speedKmh = 12.0),
            CardioIntervalBlock(id = "b5", type = CardioBlockType.COOLDOWN, durationSeconds = 240, intensityLevel = 3, speedKmh = 5.0),
        ),
        intervalRounds = 1,
    )

    // ───────────────────────────── C4 · cardio continuo ─────────────────────────────

    @Test
    fun elCardioContinuoMuestraLosAvisosConLaPantallaEncendida() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("AVISOS Y PANTALLA").assertIsDisplayed()
        rule.onNodeWithText("Beeps").assertExists()
        rule.onNodeWithText("Voz").assertExists()
        rule.onNodeWithText("Vibra").assertExists()
        rule.onNodeWithText("Pantalla encendida").assertExists()
        // Sin protocolo no hay cambio de fase que avisar: los avisos de fase quedan apagados y se explica.
        rule.onNodeWithText("Los avisos de fase son del cardio por series.").assertExists()
    }

    @Test
    fun laPantallaEncendidaSeApagaYSeEnciendeEnElCardioContinuo() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()

        assertTrue(details.keepScreenOn)
        rule.onNodeWithText("Pantalla encendida").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(details.keepScreenOn)

        rule.onNodeWithText("Pantalla encendida").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(details.keepScreenOn)
    }

    @Test
    fun losAvisosDeFaseSoloSeEnciendenEnElCardioPorSeries() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("AVISOS Y PANTALLA").assertExists()
        rule.onAllNodesWithText("Los avisos de fase son del cardio por series.").assertCountEquals(0)
        // Con protocolo, el aviso de fase se puede apagar.
        rule.onNodeWithText("Beeps").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(details.hiit?.beepsEnabled ?: true)
    }

    // ───────────────────────────── C5 · cardio por series ─────────────────────────────

    @Test
    fun elCardioPorSeriesEnseñaElObjetivoDeTrabajo() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("OBJETIVO DE TRABAJO").assertExists()
        rule.onNodeWithText("Tiempo").assertExists()
        rule.onNodeWithText("kcal").assertExists()
        rule.onNodeWithText("Distancia").assertExists()
        rule.onNodeWithText("Con kcal o distancia, cada esfuerzo termina al llegar a la cifra. Por defecto, el tiempo de arriba.").assertExists()
    }

    @Test
    fun elegirElObjetivoDeTrabajoCambiaElTipoDelEsfuerzo() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avanzado").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(HiitWorkTarget.TIME, details.hiit?.workTargetType)
        rule.onNodeWithText("kcal").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(HiitWorkTarget.KCAL, details.hiit?.workTargetType)

        rule.onNodeWithText("Distancia").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(HiitWorkTarget.DISTANCE, details.hiit?.workTargetType)
    }

    // ───────────────────────────── C6 · cardio por intervalos ─────────────────────────────

    @Test
    fun elCircuitoDeIntervalosEnseñaSusBloquesSusRondasYSuModo() {
        var details by mutableStateOf(intervalos())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("MODO INTERVALOS").assertExists()
        // `formatMinutes` usa el espacio duro de la casa entre la cifra y la unidad.
        val diecisiete = CardioPrescriptionFormatter.formatDuration(17 * 60).replace(' ', '\u00A0')
        rule.onNodeWithText("5 bloques · 1 ronda · $diecisiete").assertExists()
        rule.onNodeWithText("PATRÓN DE INTERVALO").assertExists()
        rule.onNodeWithText("PELDAÑOS DEL CIRCUITO (5)").assertExists()
    }

    // ───────────────────────────── C7 · selector de cardio ─────────────────────────────

    @Test
    fun elSelectorDeCardioAgrupaLasMaquinasYTocaUnaLaAnade() {
        var added: CardioCatalogItem? = null
        rule.setContent {
            CardioCatalogSheet(onAdd = { added = it })
        }

        rule.onNodeWithText("CARDIO").assertExists()
        rule.onNodeWithText("Añadir cardio").assertExists()
        rule.onNodeWithText("EN INTERIOR").assertExists()
        rule.onNodeWithText("AL AIRE LIBRE").assertExists()
        rule.onNodeWithText("SPRINT Y POTENCIA").assertExists()
        rule.onNodeWithText("Cinta de correr").assertExists()
        // Las tres máquinas al aire libre llevan su insignia de GPS.
        rule.onAllNodesWithText("GPS en vivo").assertCountEquals(3)

        rule.onNodeWithText("Cinta de correr").performClick()
        rule.waitForIdle()

        assertEquals("cardio_treadmill", added?.id)
    }
}
