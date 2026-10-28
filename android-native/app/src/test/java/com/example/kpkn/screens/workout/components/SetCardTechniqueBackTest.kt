package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.toTrimmedNumberString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El reverso «Avanzado» con Compose sobre Robolectric (Refinamiento 02): el conmutador «Normal · Drop · Rest-pause · Myo», la cadena de la
 * serie en su hoja con solo el tramo activo abierto en el reverso, la técnica que se guarda en la serie al pulsar «Volver al registro», y el
 * error de ejecución y la nota que se conservan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class SetCardTechniqueBackTest {

    @get:Rule
    val rule = createComposeRule()

    private var saved: Pair<List<DropSetData>, List<RestPauseData>>? = null
    private var fallida: Boolean? = null

    /** Muestra el reverso de una serie de 8 reps con [mainWeight] kg; [alto] lo encierra en una caja de esa altura (la de la tarjeta). */
    private fun mostrarReverso(mainWeight: Double = 100.0, alto: Int? = null) {
        rule.setContent {
            MaterialTheme {
                Box(if (alto != null) Modifier.height(alto.dp) else Modifier) {
                    SetCardTechniqueBack(
                        currentSet = ExerciseSet(id = "set-1", targetReps = 8, weight = mainWeight),
                        coverWeightLabel = "100 kg",
                        coverValueLabel = "8",
                        coverIntensityLabel = null,
                        mainWeight = mainWeight,
                        mainReps = 8,
                        sessionAccentColor = Color.White,
                        isFailedSet = false,
                        onFailedSetChange = { fallida = it },
                        onFlipBack = {},
                        onCompleteTechniques = { drops, pauses -> saved = drops to pauses },
                        setNumber = 3,
                        modifier = Modifier.testTag("reverso"),
                    )
                }
            }
        }
    }

    /** Elige la técnica «Drop» en el conmutador; el reverso queda en la serie principal hasta abrir un tramo. */
    private fun mostrarReversoConDrops() {
        mostrarReverso()
        rule.onNodeWithTag("technique-tab-1").performClick()
    }

    /** Pasa de la serie principal al primer tramo del conmutador, con sus campos. */
    private fun abrirElPrimerTramo() {
        rule.onNodeWithContentDescription("Tramo siguiente").performClick()
    }

    /** El valor escrito en un campo del reverso, por su descripción accesible. */
    private fun valorDelCampo(descripcion: String): String =
        rule.onNodeWithContentDescription(descripcion).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** Las filas de la hoja «Cadena de la serie» (cada tramo lleva «·» en su texto). */
    private fun filasDeLaCadena() =
        rule.onAllNodes(hasAnyAncestor(hasTestTag("technique-chain-sheet-body")) and hasText("·", substring = true), useUnmergedTree = true)

    @Test
    fun elDropSeGuardaEnLaSerieAlVolverAlRegistro() {
        mostrarReversoConDrops()

        rule.onNodeWithTag("technique-back-to-record").performClick()
        rule.waitForIdle()

        val (drops, pauses) = checkNotNull(saved) { "al volver al registro con un drop no se registró la técnica" }
        assertEquals(1, drops.size)
        assertTrue(drops.first().weight > 0.0)
        assertEquals(3, drops.first().reps)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun elegirUnDropSinVolverAlRegistroNoRegistraNada() {
        mostrarReversoConDrops()

        rule.waitForIdle()

        assertNull(saved)
    }

    @Test
    fun elSegmentadoConservaLasTecnicasYAnadeElPresetMyoReps() {
        mostrarReverso()

        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithText("Drop").assertExists()
        rule.onNodeWithText("Rest-pause").assertExists()
        rule.onNodeWithText("Myo").assertExists()
        rule.onNodeWithText("Serie 3").assertExists()
        rule.onNodeWithText("AVANZADO").assertExists()
    }

    @Test
    fun laCadenaEmpiezaConLaPrincipalYUnDropYPermiteAnadirHastaElMaximo() {
        mostrarReversoConDrops()
        rule.onNodeWithText("2 tramos").assertExists()

        rule.onNodeWithTag(TechniqueChainTag).performClick()
        rule.waitForIdle()
        filasDeLaCadena().assertCountEquals(1)
        rule.onNodeWithContentDescription("Cerrar panel").performClick()
        rule.waitForIdle()

        repeat(4) { rule.onNodeWithText("Añadir tramo").performClick() }

        rule.onNodeWithText("6 tramos").assertExists()
        rule.onAllNodes(hasText("Añadir tramo") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun soloElEslabonActivoEstaAbiertoYElNuevoPasaAserElActivo() {
        mostrarReversoConDrops()
        abrirElPrimerTramo()
        repeat(2) { rule.onNodeWithText("Añadir tramo").performClick() }
        rule.waitForIdle()

        // Solo el tramo activo enseña sus campos, y el recién añadido (DROP 3) pasa a serlo.
        rule.onNodeWithText("DROP 3").assertExists()
        rule.onAllNodesWithContentDescription("Carga · kg").assertCountEquals(1)
        rule.onNodeWithContentDescription("Tramo anterior").performClick()
        rule.onNodeWithText("DROP 2").assertExists()
        rule.onNodeWithContentDescription("Tramo anterior").performClick()
        rule.onNodeWithText("DROP 1").assertExists()
    }

    @Test
    fun laSugerenciaDelDropSoloSaleSiLaAppLaCalcula() {
        mostrarReversoConDrops()
        abrirElPrimerTramo()

        // La carga del drop llega escrita con la sugerencia que calcula la app; ya no hay un chip aparte.
        val sugerido = suggestedDropLoadsForMainSet(100.0, 8, 1).first().toTrimmedNumberString()
        assertEquals(sugerido, valorDelCampo("Carga · kg"))
        rule.onAllNodesWithText("Sugerido", substring = true).assertCountEquals(0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun conLaLetraGrandeLaCargaDelDropSeLeeEntera() {
        val sugerido = suggestedDropLoadsForMainSet(100.0, 8, 1).first().toTrimmedNumberString()
        rule.setContent {
            // El ancho de la tarjeta de la serie (272 dp) con la letra al 130 %.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                MaterialTheme {
                    Box(Modifier.width(272.dp).height(385.dp)) {
                        SetCardTechniqueBack(
                            currentSet = ExerciseSet(id = "set-1", targetReps = 8, weight = 100.0),
                            coverWeightLabel = "100 kg",
                            coverValueLabel = "8",
                            coverIntensityLabel = null,
                            mainWeight = 100.0,
                            mainReps = 8,
                            sessionAccentColor = Color.White,
                            isFailedSet = false,
                            onFailedSetChange = {},
                            onFlipBack = {},
                            onCompleteTechniques = { _, _ -> },
                            initialDropEnabled = true,
                            setNumber = 3,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        abrirElPrimerTramo()

        rule.onNodeWithText("DROP 1").assertExists()
        rule.onNodeWithText("Carga · kg").assertExists()
        rule.onNodeWithText("$sugerido × 3").assertExists()
        assertEquals(sugerido, valorDelCampo("Carga · kg"))
    }

    @Test
    fun sinCargaPrincipalLaCargaDelDropQuedaVacia() {
        mostrarReverso(mainWeight = 0.0)
        rule.onNodeWithTag("technique-tab-1").performClick()
        abrirElPrimerTramo()

        assertEquals("", valorDelCampo("Carga · kg"))
        rule.onAllNodesWithText("Sugerido", substring = true).assertCountEquals(0)
    }

    @Test
    fun elRestPauseMuestraSusTramosYSuCampoDePausa() {
        mostrarReverso()
        rule.onNodeWithTag("technique-tab-2").performClick()

        rule.onNodeWithText("${1 + RestPausePlanDefaults.DefaultCount} tramos").assertExists()
        rule.onNodeWithText("PRINCIPAL").assertExists()
        abrirElPrimerTramo()
        rule.onNodeWithText("TRAMO 1").assertExists()
        assertEquals(RestPausePlanDefaults.PauseSeconds.toString(), valorDelCampo("Pausa · s"))
        assertEquals(RestPausePlanDefaults.Reps.toString(), valorDelCampo("Repeticiones"))
    }

    @Test
    fun elRestPauseSeGuardaEnLaSerieAlVolverAlRegistro() {
        mostrarReverso()
        rule.onNodeWithTag("technique-tab-2").performClick()
        rule.waitForIdle()
        assertNull("sin volver al registro no se registra la pausa", saved)

        rule.onNodeWithTag("technique-back-to-record").performClick()
        rule.waitForIdle()

        val (drops, pauses) = checkNotNull(saved) { "al volver al registro con el rest-pause no se registró la técnica" }
        assertTrue(drops.isEmpty())
        assertEquals(RestPausePlanDefaults.DefaultCount, pauses.size)
        assertEquals(RestPausePlanDefaults.Reps, pauses.first().reps)
        assertEquals(RestPausePlanDefaults.PauseSeconds, pauses.first().restTime)
    }

    @Test
    fun elErrorDeEjecucionYLaNotaSeConservan() {
        mostrarReverso()

        rule.onNodeWithText("Añadir nota").performClick()
        rule.onNodeWithTag("technique-note-sheet", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Observación").assertExists()
        rule.onNodeWithContentDescription("Cerrar panel").performClick()
        // Las hojas salen con una animación corta: se espera a que la nota se haya ido antes de abrir la del error.
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        rule.onNodeWithText("Error de ejecución").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        // En esta caja de prueba de 380 dp la hoja deja el botón fuera de la ventana: se pulsa con su acción (la que usa TalkBack).
        rule.onNodeWithTag("technique-mark-failed", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()

        assertEquals(true, fallida)
    }

    @Test
    fun elReversoNoCreceAunqueLaCadenaTengaCincoDrops() {
        mostrarReverso(alto = 380)
        rule.onNodeWithTag("technique-tab-1").performClick()
        repeat(4) { rule.onNodeWithText("Añadir tramo").performClick() }
        rule.waitForIdle()

        val alto = rule.onNodeWithTag("reverso").fetchSemanticsNode().boundsInRoot.height
        assertEquals("la tarjeta no cambia de tamaño: el reverso mide $alto px", 380f, alto, 1f)
        rule.onNodeWithText("Error de ejecución").assertIsDisplayed()
    }
}
