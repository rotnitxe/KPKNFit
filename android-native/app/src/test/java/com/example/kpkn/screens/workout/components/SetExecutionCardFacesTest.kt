package com.example.kpkn.screens.workout.components

import android.Manifest
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.technique.TechniqueRecorderStageTag
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordSetResult
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las caras de la tarjeta de la serie (LV8, ley 1): el anverso, «Avanzado», «Modo visual» y «Modo visual» expandido encima de todo miden lo
 * mismo, y plegar vuelve a la tarjeta. Con la pieza REAL y un anfitrión de superposiciones como el de la app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class SetExecutionCardFacesTest {

    @get:Rule
    val rule = createComposeRule()

    private val serie = ExerciseSet(
        id = "s-1",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private fun ejercicio(conRom: Boolean = false) = Exercise(
        id = "ej-1",
        name = "Sentadilla trasera",
        trainingMode = TrainingMode.REPS,
        sets = listOf(serie),
        trackRom = conRom,
    )

    private fun mostrar(fontScale: Float = 1f, conRom: Boolean = false) {
        // El permiso de la cámara concedido: la cara no lanza la petición del sistema.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        rule.setContent {
            val anfitrion = rememberKpknOverlayHostController()
            CompositionLocalProvider(
                LocalDensity provides Density(3f, fontScale),
                LocalKpknOverlayHost provides anfitrion,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp).height(880.dp).background(Color.Black)) {
                            Box(Modifier.align(Alignment.TopCenter).width(272.dp).testTag(TARJETA)) {
                                SetInputCardV2(
                                    exercise = ejercicio(conRom),
                                    setIndex = 2,
                                    currentSet = serie,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
                                    previousSessionFirstSetWeight = 100.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = RecordActionHolder(),
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, onResult -> onResult(RecordSetResult.Created("ej-1_2")) },
                                )
                            }
                            KpknOverlayHostContent(anfitrion)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun alturaDeLaTarjeta(): Float = rule.onNodeWithTag(TARJETA).fetchSemanticsNode().boundsInRoot.height / 3f

    private fun comprobarMismaAltura(base: Float, estado: String) {
        val alto = alturaDeLaTarjeta()
        assertTrue("la tarjeta mide $alto dp «$estado» y $base dp en el anverso", abs(alto - base) <= 0.5f)
    }

    @Test
    fun laTarjetaMideLoMismoEnElAnversoAvanzadoYModoVisual() {
        mostrar()
        val base = alturaDeLaTarjeta()

        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("AVANZADO").assertExists()
        comprobarMismaAltura(base, "Avanzado")

        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        rule.waitForIdle()
        comprobarMismaAltura(base, "anverso de vuelta")

        rule.onNodeWithTag("secondary_pill_modo_visual").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaFaceTag).assertExists()
        comprobarMismaAltura(base, "Modo visual")
    }

    @Test
    fun laTarjetaMideLoMismoEnLasCarasConLetraAl130PorCiento() {
        mostrar(fontScale = 1.3f)
        val base = alturaDeLaTarjeta()

        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.waitForIdle()
        comprobarMismaAltura(base, "Avanzado al 130 %")
        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("secondary_pill_modo_visual").performClick()
        rule.waitForIdle()
        comprobarMismaAltura(base, "Modo visual al 130 %")
    }

    @Test
    fun avanzadoConLaCadenaLlenaDeDropsNoAlargaLaTarjeta() {
        mostrar()
        val base = alturaDeLaTarjeta()
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.waitForIdle()

        // Refinamiento 02: la pestaña es «Drop» y cada tramo se añade con el enlace «Añadir tramo» de la cara.
        rule.onNodeWithText("Drop").performClick()
        rule.waitForIdle()
        comprobarMismaAltura(base, "Avanzado con Drop")
        repeat(4) {
            rule.onNodeWithText("Añadir tramo").performClick()
            rule.waitForIdle()
        }
        comprobarMismaAltura(base, "Avanzado con cinco drops")

        rule.onNodeWithText("Rest-pause").performClick()
        rule.waitForIdle()
        comprobarMismaAltura(base, "Avanzado con Rest-pause")
    }

    @Test
    fun expandirLlevaModoVisualEncimaDeTodoSinCambiarLaTarjetaYPlegarVuelve() {
        mostrar()
        val base = alturaDeLaTarjeta()
        rule.onNodeWithTag("secondary_pill_modo_visual").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()

        rule.onNodeWithTag("media_expand").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(MediaExpandedTag).assertExists()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertExists()
        rule.onNodeWithText("Modo visual · serie 3").assertExists()
        comprobarMismaAltura(base, "con Modo visual expandido encima")
        val expandido = rule.onNodeWithTag(MediaExpandedTag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "expandido ocupa casi toda la pantalla de 360 × 880 dp: mide ${expandido.width / 3f} × ${expandido.height / 3f} dp",
            expandido.width / 3f >= 340f && expandido.height / 3f >= 820f,
        )

        rule.onNodeWithTag("media_fold").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertDoesNotExist()
        rule.onNodeWithTag(MediaFaceTag).assertExists()
        comprobarMismaAltura(base, "tras plegar")
    }

    @Test
    fun fichaAbreLaFichaDelCatalogoEncimaDeTodoSinCambiarLaTarjeta() {
        mostrar()
        val base = alturaDeLaTarjeta()
        rule.onNodeWithTag("secondary_pill_modo_visual").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Cerrar definición").assertDoesNotExist()

        rule.onNodeWithContentDescription("Ficha del ejercicio").performClick()
        rule.waitForIdle()

        // La ficha del catálogo (sin el pie «Añadir/Quitar») se abre en su propia hoja, encima de todo, y la tarjeta no se toca.
        rule.onNodeWithContentDescription("Cerrar definición").assertExists()
        comprobarMismaAltura(base, "con la ficha abierta")

        rule.onNodeWithContentDescription("Cerrar definición").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Cerrar definición").assertDoesNotExist()
        comprobarMismaAltura(base, "con la ficha cerrada")
    }

    @Test
    fun laCabeceraDeModoVisualTieneVolverAlbumYExpandirYLaLineaDelAlbum() {
        mostrar()
        rule.onNodeWithTag("secondary_pill_modo_visual").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Serie 3").assertExists()
        rule.onNodeWithText("Expandir").assertExists()
        // Sin esfuerzo marcado, la línea nombra la serie y su carga, sin RIR.
        rule.onNodeWithText("Se guarda en el álbum con la serie 3", substring = true).assertExists()
    }

    @Test
    fun laTarjetaMideLoMismoAlEscribirLaCargaConElTeclado() {
        mostrar()
        val base = alturaDeLaTarjeta()

        // El primer campo editable del anverso es el de la carga: se enfoca (sale el teclado) y se escribe una cifra larga.
        val carga = rule.onAllNodes(hasSetTextAction())[0]
        carga.performClick()
        carga.performTextReplacement("102,5")
        rule.waitForIdle()

        comprobarMismaAltura(base, "con la carga escrita")
    }

    private companion object {
        const val TARJETA = "lv8-caras"
    }
}
