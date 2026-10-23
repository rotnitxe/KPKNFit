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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
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
 * El reverso «Avanzado» con Compose sobre Robolectric (LR0, LV8): el segmentado «Normal · Drop-set · Rest-pause», la cadena de la serie
 * con solo el eslabón activo abierto, el registro de la técnica al marcar todos los eslabones y el fallo y la nota que se conservan.
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

    private fun mostrarReversoConDrops() {
        mostrarReverso()
        rule.onNodeWithText("Drop-set").performClick()
    }

    @Test
    fun marcarElDropComoHechoRegistraLaTecnicaEnLaSerie() {
        mostrarReversoConDrops()

        rule.onNodeWithTag(TechniqueDoneToggleTag).performClick()
        rule.waitForIdle()

        val (drops, pauses) = checkNotNull(saved) { "al marcar el drop como hecho no se registró la técnica" }
        assertEquals(1, drops.size)
        assertTrue(drops.first().weight > 0.0)
        assertEquals(3, drops.first().reps)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun sinMarcarNingunDropNoSeRegistraNada() {
        mostrarReversoConDrops()

        rule.waitForIdle()

        assertNull(saved)
    }

    @Test
    fun elSegmentadoTieneNormalDropSetYRestPauseYNoMyoReps() {
        mostrarReverso()

        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithText("Drop-set").assertExists()
        rule.onNodeWithText("Rest-pause").assertExists()
        rule.onNodeWithText("Myo-reps").assertDoesNotExist()
        rule.onNodeWithText("Serie 3").assertExists()
        rule.onNodeWithText("AVANZADO").assertExists()
    }

    @Test
    fun laCadenaEmpiezaConLaPrincipalYUnDropYPermiteAnadirHastaElMaximo() {
        mostrarReversoConDrops()
        rule.onAllNodesWithTag(TechniqueLinkTag).assertCountEquals(2)

        repeat(4) { rule.onNodeWithContentDescription("Añadir drop").performClick() }

        rule.onAllNodesWithTag(TechniqueLinkTag).assertCountEquals(6)
        rule.onNodeWithContentDescription("Añadir drop").assertDoesNotExist()
    }

    @Test
    fun soloElEslabonActivoEstaAbiertoYElNuevoPasaAserElActivo() {
        mostrarReversoConDrops()
        repeat(2) { rule.onNodeWithContentDescription("Añadir drop").performClick() }
        rule.waitForIdle()

        rule.onAllNodesWithTag(TechniqueDoneToggleTag).assertCountEquals(1)
        // «Drop 3» sale en su eslabón y en el título del eslabón abierto; «Drop 1», solo en su eslabón.
        rule.onAllNodesWithText("Drop 3").assertCountEquals(2)
        rule.onAllNodesWithText("Drop 1").assertCountEquals(1)

        rule.onAllNodesWithText("Drop 1").onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Drop 1").assertCountEquals(2)
        rule.onAllNodesWithText("Drop 3").assertCountEquals(1)
    }

    @Test
    fun laSugerenciaDelDropSoloSaleSiLaAppLaCalcula() {
        mostrarReversoConDrops()
        val sugerido = suggestedDropLoadsForMainSet(100.0, 8, 1).first()
        rule.onNodeWithText("Sugerido ${NativeProgressionText.formatKg(sugerido)} kg").assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun conLaLetraGrandeElChipSugeridoPasaALaCargaSolaYSuDescripcionSigueCompleta() {
        val sugerido = NativeProgressionText.formatKg(suggestedDropLoadsForMainSet(100.0, 8, 1).first())
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

        rule.onNodeWithContentDescription("Sugerido $sugerido kg").assertExists()
        rule.onNodeWithText("$sugerido kg").assertExists()
        rule.onNodeWithText("Sugerido $sugerido kg").assertDoesNotExist()
    }

    @Test
    fun sinCargaPrincipalNoHayChipSugerido() {
        mostrarReverso(mainWeight = 0.0)
        rule.onNodeWithText("Drop-set").performClick()

        rule.onAllNodesWithText("Sugerido", substring = true).assertCountEquals(0)
    }

    @Test
    fun elRestPauseMuestraLasPausasYSuExplicacion() {
        mostrarReverso()
        rule.onNodeWithText("Rest-pause").performClick()

        rule.onAllNodesWithTag(TechniqueLinkTag).assertCountEquals(1 + RestPausePlanDefaults.DefaultCount)
        rule.onNodeWithText("ASÍ SE VE UN REST-PAUSE").assertExists()
        // «Pausa 1» sale en su eslabón y en el título del eslabón abierto.
        rule.onAllNodesWithText("Pausa 1").assertCountEquals(2)
    }

    @Test
    fun elRestPauseSeRegistraCuandoTodasLasPausasEstanHechas() {
        mostrarReverso()
        rule.onNodeWithText("Rest-pause").performClick()

        for (indice in 0 until RestPausePlanDefaults.DefaultCount) {
            if (indice > 0) rule.onAllNodesWithText("Pausa ${indice + 1}").onFirst().performClick()
            rule.waitForIdle()
            assertNull("hasta marcar la última no se registra: pausa ${indice + 1}", saved)
            rule.onNodeWithTag(TechniqueDoneToggleTag).performClick()
            rule.waitForIdle()
        }

        val (drops, pauses) = checkNotNull(saved) { "con todas las pausas hechas debe registrarse" }
        assertTrue(drops.isEmpty())
        assertEquals(RestPausePlanDefaults.DefaultCount, pauses.size)
        assertEquals(RestPausePlanDefaults.Reps, pauses.first().reps)
        assertEquals(RestPausePlanDefaults.PauseSeconds, pauses.first().restTime)
    }

    @Test
    fun elErrorDeEjecucionYLaNotaSeConservan() {
        mostrarReverso()

        rule.onNodeWithText("NOTA DE LA SERIE").assertExists()
        rule.onNodeWithText("Error de ejecución").performClick()

        assertEquals(true, fallida)
    }

    @Test
    fun elReversoNoCreceAunqueLaCadenaTengaCincoDropsYSeDesplazaPorDentro() {
        mostrarReverso(alto = 380)
        rule.onNodeWithText("Drop-set").performClick()
        repeat(4) { rule.onNodeWithContentDescription("Añadir drop").performClick() }
        rule.waitForIdle()

        val alto = rule.onNodeWithTag("reverso").fetchSemanticsNode().boundsInRoot.height
        assertEquals("la tarjeta no cambia de tamaño: el reverso mide $alto px", 380f, alto, 1f)
        rule.onNodeWithText("Error de ejecución").performScrollTo().assertIsDisplayed()
    }
}
