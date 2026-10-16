package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.components.SetCardTechniqueBack
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

private const val CARD_SHEET_TAG = "lv8-card-sheet"
private const val CARD_DENSITY = 3f

/** Alto en dp de la tarjeta de la serie (anverso, «Avanzado» y «Modo visual»), medido por `SetExecutionCardEffortTest` al 100 % y al 130 % de letra. */
private const val CARD_HEIGHT_100 = 384.67f
private const val CARD_HEIGHT_130 = 401.67f

/**
 * HOJA DE RENDER de la tarjeta de la serie de la ronda LV8: el slider de esfuerzo con fuego (en el mínimo, en el plan y en el fallo, con
 * dos fotogramas del fuego), el aviso de esfuerzo obligatorio, la tarjeta con ROM, el panel guiado de drops y el aviso del peso corporal
 * (que van ENCIMA de la tarjeta) y el ejercicio por tiempo. Dibuja la pieza REAL (`SetInputCardV2`) a 360 dp, al 100 % y al 130 % de
 * letra. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveCardRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private val holder = RecordActionHolder()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val serieRir = ExerciseSet(
        id = "pm-1",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private val serieSinObjetivo = ExerciseSet(
        id = "pm-2",
        targetReps = 6,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    /** Un drop-set manual de dos drops: con el esfuerzo marcado, el ✓ abre el panel guiado encima de la tarjeta. */
    private val serieConDrops = serieRir.copy(
        id = "pm-3",
        isDropSet = true,
        plannedIntensityTechniques = listOf(
            PlannedTechnique(id = "t-drop", type = TechniqueType.DROP_SET, params = mapOf("count" to "2")),
        ),
    )

    private val serieAsistida = serieRir.copy(id = "pm-4", weight = 20.0, loadModeV2 = LoadModeV2.ASSISTED)

    private val serieTiempo = ExerciseSet(
        id = "pm-5",
        targetDuration = 45,
        weight = 20.0,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.TIME,
    )

    private fun ejercicio(serie: ExerciseSet, conRom: Boolean = false, modo: TrainingMode = TrainingMode.REPS) = Exercise(
        id = "pm",
        name = "Peso Muerto Convencional",
        trainingMode = modo,
        sets = listOf(serie),
        trackRom = conRom,
    )

    @Test fun esfuerzoMinimo100() { onlyWhenAsked(); dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_minimo_100") }
    @Test fun esfuerzoMinimo130() { onlyWhenAsked(); dibujar(serieRir, fontScale = 1.3f, nombre = "esfuerzo_minimo_130") }

    @Test
    fun esfuerzoEnElPlan100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_plan_100", borrador = WorkoutSetDraft(intensityText = "2"), fuego = 0.6f)
    }

    @Test
    fun esfuerzoEnElPlan130() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1.3f, nombre = "esfuerzo_plan_130", borrador = WorkoutSetDraft(intensityText = "2"), fuego = 0.6f)
    }

    @Test
    fun esfuerzoEnRir4EsGris100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_rir4_100", borrador = WorkoutSetDraft(intensityText = "4"))
    }

    @Test
    fun esfuerzoEnRir1100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_rir1_100", borrador = WorkoutSetDraft(intensityText = "1"), fuego = 0.6f)
    }

    @Test
    fun esfuerzoEnElFalloFotograma1_100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_fallo_f1_100", borrador = WorkoutSetDraft(reachedFailure = true), fuego = 0.0f)
    }

    @Test
    fun esfuerzoEnElFalloFotograma2_100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_fallo_f2_100", borrador = WorkoutSetDraft(reachedFailure = true), fuego = 0.45f)
    }

    @Test
    fun esfuerzoEnElFalloFotograma1_130() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1.3f, nombre = "esfuerzo_fallo_f1_130", borrador = WorkoutSetDraft(reachedFailure = true), fuego = 0.0f)
    }

    @Test
    fun esfuerzoEnElFalloFotograma2_130() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1.3f, nombre = "esfuerzo_fallo_f2_130", borrador = WorkoutSetDraft(reachedFailure = true), fuego = 0.45f)
    }

    @Test
    fun esfuerzoSinObjetivoEnRpe100() {
        onlyWhenAsked()
        dibujar(serieSinObjetivo, fontScale = 1f, nombre = "esfuerzo_sin_objetivo_100", borrador = WorkoutSetDraft(intensityText = "7,5"))
    }

    @Test
    fun esfuerzoObligatorio100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "esfuerzo_obligatorio_100", pulsarCheck = true)
    }

    @Test
    fun esfuerzoObligatorio130() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1.3f, nombre = "esfuerzo_obligatorio_130", pulsarCheck = true)
    }

    @Test
    fun tarjetaConRom100() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1f, nombre = "tarjeta_rom_100", conRom = true, borrador = WorkoutSetDraft(intensityText = "2"))
    }

    @Test
    fun tarjetaConRom130() {
        onlyWhenAsked()
        dibujar(serieRir, fontScale = 1.3f, nombre = "tarjeta_rom_130", conRom = true, borrador = WorkoutSetDraft(intensityText = "2"))
    }

    // ── Fase B: «Avanzado» (A2) a la altura de la tarjeta de la serie ───────────────────────────────────

    @Test fun avanzadoDrop1_100() { onlyWhenAsked(); dibujarAvanzado(1f, "avanzado_drop1_100") }
    @Test fun avanzadoDrop1_130() { onlyWhenAsked(); dibujarAvanzado(1.3f, "avanzado_drop1_130") }
    @Test fun avanzadoRestPause100() { onlyWhenAsked(); dibujarAvanzado(1f, "avanzado_restpause_100", tecnica = "Rest-pause") }
    @Test fun avanzadoRestPause130() { onlyWhenAsked(); dibujarAvanzado(1.3f, "avanzado_restpause_130", tecnica = "Rest-pause") }
    @Test fun avanzadoNormal100() { onlyWhenAsked(); dibujarAvanzado(1f, "avanzado_normal_100", tecnica = "Normal") }
    @Test fun avanzadoCincoDrops100() { onlyWhenAsked(); dibujarAvanzado(1f, "avanzado_cinco_drops_100", sumarDrops = 4) }

    /** El reverso «Avanzado» con la altura de la tarjeta; [tecnica] es el segmento que se elige y [sumarDrops] los «+» que se pulsan. */
    private fun dibujarAvanzado(fontScale: Float, nombre: String, tecnica: String? = null, sumarDrops: Int = 0) {
        val alto = if (fontScale > 1.15f) CARD_HEIGHT_130 else CARD_HEIGHT_100
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(CARD_DENSITY, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .padding(vertical = 16.dp)
                                .testTag(CARD_SHEET_TAG),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.width(272.dp).height(alto.dp)) {
                                SetCardTechniqueBack(
                                    currentSet = serieRir,
                                    coverWeightLabel = "95 kg",
                                    coverValueLabel = "6",
                                    coverIntensityLabel = null,
                                    mainWeight = 95.0,
                                    mainReps = 6,
                                    sessionAccentColor = Color(0xFFF49A6E),
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
            }
        }
        rule.waitForIdle()
        tecnica?.let {
            rule.onNodeWithText(it).performClick()
            rule.waitForIdle()
        }
        repeat(sumarDrops) {
            rule.onNodeWithContentDescription("Añadir drop").performClick()
            rule.waitForIdle()
        }
        guardar(CARD_SHEET_TAG, nombre)
    }

    @Test
    fun panelGuiadoDeDrops100() {
        onlyWhenAsked()
        dibujar(serieConDrops, fontScale = 1f, nombre = "panel_guiado_drops_100", borrador = WorkoutSetDraft(intensityText = "2"), pulsarCheck = true)
    }

    @Test
    fun panelGuiadoDeDrops130() {
        onlyWhenAsked()
        dibujar(serieConDrops, fontScale = 1.3f, nombre = "panel_guiado_drops_130", borrador = WorkoutSetDraft(intensityText = "2"), pulsarCheck = true)
    }

    @Test
    fun asistidoSinPesoCorporal100() {
        onlyWhenAsked()
        dibujar(serieAsistida, fontScale = 1f, nombre = "asistido_sin_peso_100", borrador = WorkoutSetDraft(intensityText = "2"), pesoCorporal = null)
    }

    @Test
    fun tarjetaPorTiempo100() {
        onlyWhenAsked()
        dibujar(serieTiempo, fontScale = 1f, nombre = "tarjeta_tiempo_100", borrador = WorkoutSetDraft(intensityText = "2"), modo = TrainingMode.TIME)
    }

    private fun dibujar(
        serie: ExerciseSet,
        fontScale: Float,
        nombre: String,
        borrador: WorkoutSetDraft? = null,
        conRom: Boolean = false,
        fuego: Float? = null,
        pulsarCheck: Boolean = false,
        pesoCorporal: Double? = 80.0,
        modo: TrainingMode = TrainingMode.REPS,
    ) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(CARD_DENSITY, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        // En el teléfono la tarjeta mide unos 272 dp y a sus lados asoman las vecinas.
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .padding(vertical = 16.dp)
                                .testTag(CARD_SHEET_TAG),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.width(272.dp)) {
                                SetInputCardV2(
                                    exercise = ejercicio(serie, conRom, modo),
                                    setIndex = 0,
                                    currentSet = serie,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
                                    previousSessionFirstSetWeight = 122.5,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = pesoCorporal,
                                    recordActionHolder = holder,
                                    recordActionPageKey = "lv8-render",
                                    initialDraft = borrador,
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                    effortFireSecondsOverride = fuego,
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        if (pulsarCheck) {
            // El aviso se retira solo a los 3 s: con el reloj parado se avanza lo justo para ver el aviso entero (el fundido dura 160 ms).
            rule.mainClock.autoAdvance = false
            rule.runOnUiThread { holder.actionForPage("lv8-render")?.invoke() }
            // Con el reloj parado `waitForIdle` aplica el cambio sin correr el tiempo; luego se avanza lo justo y se deja asentar el dibujo.
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
        }
        guardar(CARD_SHEET_TAG, nombre)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
