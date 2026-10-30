package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitWorkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pulido v1 · cardio del editor: la tarjeta de un cardio abierto (Continuo · Series · Intervalos), sus hojas (avisos, protocolo, selector) y el
 * catálogo de «Añadir cardio». Antes: ED-D · C4 a C7 sobre el panel «Avanzado», que desaparece.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class CardioEditorEddComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Card(details: CardioDetails, onChange: (CardioDetails) -> Unit) {
        Inline {
            // Sin desplazamiento: la ventana de prueba mide 1.800 dp y las hojas en línea llevan el suyo (no se anidan dos).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                CardioEditorCard(details = details, onChange = onChange)
            }
        }
    }

    /** Las hojas se pintan en línea: dentro de `KpknSheet` los toques no llegan en Robolectric. */
    @Composable
    private fun Inline(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalCardioSheetInline provides true) { EditorOverlayTestHost(content) }
    }

    private fun continuo() = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 20 * 60)

    private fun series() = CardioDetails(
        type = CardioType.ROW_MACHINE,
        supportsDistance = false,
        hiit = CardioHiitConfig(warmupSeconds = 300, workSeconds = 30, restSeconds = 60, rounds = 8, cooldownSeconds = 180, targetRpe = 9.0),
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

    /** Las hojas entran con una animación: se deja pasar el reloj de pruebas antes de tocar dentro de ellas (como hacen las demás pruebas de hojas). */
    private fun settle() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
    }

    private fun switchNamed(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and hasText(text)

    // ───────────────────────────── Continuo ─────────────────────────────

    @Test
    fun elCardioContinuoSeDistribuyeEnFormaMagnitudesEsfuerzoYObjetivos() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("FORMA").assertExists()
        rule.onNodeWithText("Plantillas").assertExists()
        rule.onNodeWithText("Duración").assertExists()
        rule.onNodeWithText("Distancia").assertExists()
        rule.onNodeWithText("ESFUERZO (RPE)").assertExists()
        rule.onNodeWithText("OBJETIVOS").assertExists()
        rule.onNodeWithText("Zona de pulso").assertExists()
        rule.onNodeWithText("Avisos y pantalla").assertExists()
        // Lo que el lienzo no tiene se elimina: ni «Avanzado», ni «Añadir series», ni «Añadir cardio» dentro de la tarjeta, ni el chip de posición.
        rule.onAllNodesWithText("Avanzado").assertCountEquals(0)
        rule.onAllNodesWithText("Añadir series").assertCountEquals(0)
        rule.onAllNodesWithText("Añadir cardio").assertCountEquals(0)
        rule.onAllNodesWithText("Al final").assertCountEquals(0)
        // La bici estática no corre ni camina: no hay ritmo objetivo.
        rule.onAllNodesWithText("Ritmo").assertCountEquals(0)
    }

    @Test
    fun elRitmoSoloApareceDondeSeCorreOSeCamina() {
        var details by mutableStateOf(CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 20 * 60))
        rule.setContent { Card(details) { details = it } }
        rule.onNodeWithText("Ritmo").assertExists()
    }

    @Test
    fun elSelectorDeDuracionAplicaUnAtajo() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Duración").performClick()
        settle()
        rule.onNodeWithText("Arrastra la regla, usa − y + o toca la cifra para escribir.").assertExists()
        rule.onNodeWithText("30 min").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        settle()

        assertEquals(30 * 60, details.targetDurationSeconds)
    }

    @Test
    fun elAtajoLibreQuitaElObjetivoDeTiempo() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Duración").performClick()
        settle()
        rule.onNodeWithText("Libre").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        settle()

        assertNull(details.targetDurationSeconds)
    }

    @Test
    fun cancelarElSelectorNoCambiaNada() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Duración").performClick()
        settle()
        rule.onNodeWithText("45 min").performClick()
        rule.onNodeWithText("Cancelar").performClick()
        settle()

        assertEquals(20 * 60, details.targetDurationSeconds)
    }

    @Test
    fun laZonaDeLaHojaSeGuardaComoElPuntoMedioDeSuBanda() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Zona de pulso").performClick()
        settle()
        rule.onNodeWithText("Z3 · Tempo").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        settle()

        assertEquals(75, details.targetHrPercent)
    }

    // ───────────────────────────── Avisos y pantalla ─────────────────────────────

    @Test
    fun enContinuoLosAvisosDeFaseSeExplicanYLaPantallaEncendidaSeCambia() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avisos y pantalla").performClick()
        settle()

        rule.onNodeWithText("Sonidos").assertExists()
        rule.onNodeWithText("Vibración").assertExists()
        rule.onNodeWithText("Los avisos de fase son del cardio por series o por intervalos: en Continuo no hay cambios de fase que avisar.").assertExists()
        assertTrue(details.keepScreenOn)
        rule.onNode(switchNamed("Pantalla encendida")).performClick()
        settle()
        assertFalse(details.keepScreenOn)
    }

    @Test
    fun losAvisosDeFaseSeGuardanPorCardioEnSeries() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avisos y pantalla").performClick()
        settle()
        rule.onNode(switchNamed("Sonidos")).performClick()
        settle()

        assertFalse(details.hiit?.beepsEnabled ?: true)
        assertEquals(false, details.beepsEnabled)
    }

    @Test
    fun losAvisosDeFaseSeGuardanPorCardioTambienEnIntervalos() {
        var details by mutableStateOf(intervalos())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Avisos y pantalla").performClick()
        settle()
        rule.onNode(switchNamed("Voz")).performClick()
        settle()

        assertNull(details.hiit)
        assertEquals(false, details.voiceCuesEnabled)
        assertNull(details.beepsEnabled)
    }

    // ───────────────────────────── Series ─────────────────────────────

    @Test
    fun elCardioPorSeriesTieneCincoFilasYLoRaroVaEnElProtocolo() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Calentamiento").assertExists()
        rule.onNodeWithText("Esfuerzo").assertExists()
        rule.onNodeWithText("Pausa").assertExists()
        rule.onNodeWithText("Rondas").assertExists()
        rule.onNodeWithText("Enfriamiento").assertExists()
        rule.onNodeWithText("Protocolo").assertExists()
        // Se van el chip HIIT/SIT, Omitir/Libre sueltos y la distancia dentro de Series.
        rule.onAllNodesWithText("HIIT").assertCountEquals(0)
        rule.onAllNodesWithText("Omitir").assertCountEquals(0)
        rule.onAllNodesWithText("Distancia").assertCountEquals(0)
    }

    @Test
    fun elegirElObjetivoDeTrabajoEnElProtocoloCambiaElTipoDelEsfuerzo() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Protocolo").performClick()
        settle()
        rule.onNodeWithText("PAUSA ENTRE ESFUERZOS").assertExists()
        rule.onNodeWithText("CADA ESFUERZO TERMINA POR").assertExists()

        assertEquals(HiitWorkTarget.TIME, details.hiit?.workTargetType)
        rule.onNodeWithText("kcal").performClick()
        settle()
        assertEquals(HiitWorkTarget.KCAL, details.hiit?.workTargetType)
        rule.onNodeWithText("Distancia").performClick()
        settle()
        assertEquals(HiitWorkTarget.DISTANCE, details.hiit?.workTargetType)
    }

    @Test
    fun elCalentamientoPuedeSerLibreOOmitirDesdeElSelector() {
        var details by mutableStateOf(series())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Calentamiento").performClick()
        settle()
        rule.onNodeWithText("Libre").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        settle()
        assertTrue(details.hiit?.warmupOpen == true)

        rule.onNodeWithText("Calentamiento").performClick()
        settle()
        rule.onNodeWithText("Omitir").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        settle()
        assertEquals(0, details.hiit?.warmupSeconds)
        assertFalse(details.hiit?.warmupOpen == true)
    }

    // ───────────────────────────── Intervalos ─────────────────────────────

    @Test
    fun elCircuitoDeIntervalosEnseñaLaGraficaLasRondasYElInspector() {
        var details by mutableStateOf(intervalos())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Toca una barra para editarla. Altura = intensidad, ancho = duración.").assertExists()
        rule.onNodeWithText("Rondas del circuito").assertExists()
        rule.onNodeWithText("1/5").assertExists()
        rule.onNodeWithText("Añadir bloque").assertExists()
        // La cinta admite velocidad e inclinación; ni «Peldaños del circuito» ni «Modo intervalos» ni el carrusel de patrones.
        rule.onNodeWithText("Velocidad").assertExists()
        rule.onNodeWithText("Inclinación").assertExists()
        rule.onAllNodesWithText("PELDAÑOS DEL CIRCUITO (5)").assertCountEquals(0)
        rule.onAllNodesWithText("MODO INTERVALOS").assertCountEquals(0)
    }

    @Test
    fun anadirUnBloqueLoElige() {
        var details by mutableStateOf(intervalos())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Añadir bloque").performClick()
        settle()

        assertEquals(6, details.intervalBlocks.size)
        rule.onNodeWithText("6/6").assertExists()
        assertEquals(details.intervalBlocks.sumOf { it.durationSeconds }, details.targetDurationSeconds)
    }

    // ───────────────────────────── Plantillas ─────────────────────────────

    @Test
    fun laHojaDePlantillasAplicaUnaYCierra() {
        var details by mutableStateOf(continuo())
        rule.setContent { Card(details) { details = it } }

        rule.onNodeWithText("Plantillas").performClick()
        settle()
        rule.onNodeWithText("Elige una base y ajústala después.").assertExists()
        rule.onNodeWithText("Tabata").performClick()
        settle()

        assertTrue(details.hasIntervals())
        assertEquals(8, details.hiit?.rounds)
    }

    // ───────────────────────────── Selector de cardio ─────────────────────────────

    @Test
    fun elSelectorDeCardioAgrupaLasMaquinasYTocaUnaLaAnade() {
        var added: CardioCatalogItem? = null
        rule.setContent { Inline { CardioCatalogSheet(onAdd = { added = it }) } }
        settle()

        rule.onNodeWithText("CARDIO").assertExists()
        rule.onNodeWithText("Añadir cardio").assertExists()
        rule.onNodeWithText("EN INTERIOR").assertExists()
        rule.onNodeWithText("Cinta de correr").assertExists()

        rule.onNodeWithText("Cinta de correr").performClick()
        settle()

        assertEquals("cardio_treadmill", added?.id)
    }

    @Test
    fun alCambiarLaModalidadSeDiceQueSeSeleccionaLaNueva() {
        rule.setContent {
            Inline { CardioCatalogSheet(isReplacing = true, currentType = CardioType.ELLIPTICAL, onAdd = {}) }
        }
        settle()
        rule.onNodeWithText("Cambiar ejercicio de cardio").assertExists()
        rule.onNodeWithText("Selecciona la nueva modalidad.").assertExists()
    }
}
