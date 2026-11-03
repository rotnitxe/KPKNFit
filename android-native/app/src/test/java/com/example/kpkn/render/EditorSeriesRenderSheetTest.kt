package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorExpanded
import com.example.kpkn.screens.sessioneditor.components.rememberExerciseEditorLocalState
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pulido v1 · EDITOR · pasada A1: hoja de render de las 18 escenas de la configuración de las series del ejercicio abierto
 * (`ed-serie`, `-carga`, `-rango`, `-intensidad`, `-fallo`, `-descanso`, `-acciones`, `-aplicar`, `ed-modo`, `-amrap`, `-rm`, `-solorpe`, `-tiempo`,
 * `-distancia`, `ed-preparacion`, `ed-unilateral`, `ed-opciones`, `ed-descanso-ejercicio`) con los datos de muestra del lienzo.
 *
 * Cada escena se dibuja a 360 × 880 dp al 100 % y al 130 % de letra, con la tarjeta del ejercicio en el mismo sitio que en el lienzo
 * (la fila del ejercicio, la lista, la isla y la barra son de la tanda A2: aquí hay una franja vacía de 68 dp). Los PNG van a la carpeta
 * de `KPKN_RENDER_DIR` como `<id>_100.png` y `<id>_130.png` (`_740` en la clase de 360 × 740). Solo corre con esa variable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
open class EditorSeriesRenderSheetTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
    }

    @get:Rule
    val rule = createComposeRule()

    /** Alto de la ventana en dp y sufijo de los PNG (la clase de 360 × 740 los cambia). */
    protected open val windowHeight = 880
    protected open val suffix = ""

    private var view: View? = null
    private var scale by mutableStateOf(1f)
    private val state = mutableStateOf(Exercise(id = "e1", name = "Peso Muerto Convencional"))
    private val accent = Color(0xFFF49A6E)

    // ───────────────────────────── datos de muestra del lienzo ─────────────────────────────

    private fun rpe8(id: String, kg: Double) = ExerciseSet(
        id = id, targetReps = 5, weight = kg, intensityMode = IntensityMode.RPE, targetRPE = 8.0,
    )

    /** `e1`: Peso muerto convencional, 3 × 5 · RPE 8 · 3:00 (la tercera serie sin intensidad, a 115 kg). */
    private fun deadlift() = Exercise(
        id = "e1", name = "Peso Muerto Convencional", trainingMode = TrainingMode.REPS, restTime = 180,
        reference1RM = 200.0,
        sets = listOf(rpe8("s1", 120.0), rpe8("s2", 120.0), ExerciseSet(id = "s3", targetReps = 5, weight = 115.0)),
    )

    /** `e6`: Plancha Copenhague dinámica, modo Tiempo de 40 s con peso corporal. */
    private fun plank(mode: TrainingMode) = Exercise(
        id = "e6", name = "Plancha Copenhague Dinámica", trainingMode = mode, restTime = 90,
        sets = List(3) { i ->
            ExerciseSet(
                id = "p${i + 1}", loadModeV2 = LoadModeV2.BODYWEIGHT,
                targetDuration = if (mode == TrainingMode.TIME) 40 else null,
                plannedTargetV2 = if (mode == TrainingMode.DISTANCE) 200.0 else null,
            )
        },
    )

    /** `e5`: Patada de glúteo en polea, unilateral, con la tercera serie solo en L. */
    private fun kickback(): Exercise {
        val side = UnilateralTarget(weight = 25.0, targetReps = 5, intensityMode = IntensityMode.RPE, targetRPE = 8.0)
        return Exercise(
            id = "e5", name = "Patada de Glúteo", trainingMode = TrainingMode.REPS, restTime = 180, reference1RM = 100.0,
            isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED, restBetweenSidesSeconds = 15,
            sets = listOf(
                ExerciseSet(id = "k1", targetReps = 5, weight = 25.0, leftTarget = side, rightTarget = side),
                ExerciseSet(id = "k2", targetReps = 5, weight = 25.0, leftTarget = side, rightTarget = side),
                ExerciseSet(id = "k3", targetReps = 5, weight = 25.0, leftTarget = side, rightTarget = null),
            ),
        )
    }

    // ───────────────────────────── marco de la hoja ─────────────────────────────

    @Composable
    private fun ExpandedCard() {
        val exercise = state.value
        val local = rememberExerciseEditorLocalState(exercise)
        ExerciseEditorExpanded(
            exercise = exercise,
            local = local,
            accentColor = accent,
            coverColor = accent,
            isSupersetExercise = false,
            suppressIndividualRest = false,
            resolved1RM = exercise.reference1RM,
            predictedMetrics = emptyMap(),
            localPrEstimatedRm = null,
            relationshipAnchorName = null,
            cardioFirst = false,
            showCardioPlacementChip = false,
            onToggleCardioPlacement = null,
            cardioHistory = null,
            onUpdateExercise = { transform -> state.value = transform(state.value) },
            onAddSet = {},
            onUpdateSet = { id, transform ->
                state.value = state.value.copy(sets = state.value.sets.map { if (it.id == id) transform(it) else it })
            },
            onRemoveSet = {},
            onMoveSet = { _, _ -> },
            onRemoveMobility = {},
            onOpenMobility = {},
            onOpenSuperset = {},
            onOpenRelationshipPicker = {},
            onClearRelationship = {},
            onUpdateRelationshipType = {},
            onUpdateRelationshipNotes = {},
        )
    }

    /**
     * Dibuja la tarjeta abierta con su módulo (#111, radio 24) a [top] dp del borde de arriba (como en el lienzo, que puede ser negativo
     * porque la escena hace scroll), ejecuta [steps] (toques para llegar al estado de la escena) y guarda el PNG a 100 % y a 130 %.
     */
    private fun scene(id: String, top: Float, exercise: Exercise, steps: () -> Unit = {}) {
        state.value = exercise
        rule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                EditorOverlayTestHost {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset(y = (top + 1f).dp)
                                .padding(horizontal = 17.dp),
                        ) {
                            val shape = RoundedCornerShape(24.dp)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111111), shape)
                                    .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
                            ) {
                                // La fila del ejercicio (asa, miniatura, nombre, estrella, flecha) es de la tanda A2.
                                Box(Modifier.fillMaxWidth().height(68.dp))
                                ExpandedCard()
                            }
                        }
                    }
                }
            }
        }
        steps()
        for ((fontScale, tag) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { scale = fontScale }
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
            save("${id}_$tag$suffix")
        }
    }

    private fun save(name: String) {
        val host = view!!
        rule.runOnUiThread { host.invalidate(); host.requestLayout() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(Canvas(bitmap)) }
        val target = File(System.getenv("KPKN_RENDER_DIR")!!, "$name.png")
        target.parentFile!!.mkdirs()
        val viewport = Bitmap.createScaledBitmap(bitmap, 360, windowHeight, true)
        target.outputStream().use { viewport.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (viewport !== bitmap) viewport.recycle()
        bitmap.recycle()
    }

    private fun select(setTag: String) {
        rule.onNodeWithTag(setTag).performClick()
        rule.waitForIdle()
    }

    private fun describe(text: String) {
        rule.onNodeWithContentDescription(text).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    // ───────────────────────────── las 18 escenas ─────────────────────────────

    @Test fun edSerie() = scene("ed-serie", 97.3f, deadlift()) { select("set-row-s2") }

    @Test fun edSerieCarga() = scene("ed-serie-carga", 97.3f, deadlift()) {
        select("set-row-s2")
        describe("Tipo de carga: Carga externa")
    }

    @Test fun edSerieRango() = scene(
        "ed-serie-rango", 97.3f,
        deadlift().let { e ->
            e.copy(sets = e.sets.map {
                if (it.id != "s2") it else it.copy(
                    targetReps = 10, targetRepsRange = RepRange(8, 10), intensityMode = IntensityMode.RIR, targetRPE = null, targetRIR = 2,
                )
            })
        },
    ) { select("set-row-s2") }

    @Test fun edSerieIntensidad() = scene("ed-serie-intensidad", 97.3f, deadlift()) {
        select("set-row-s2")
        describe("Unidad de intensidad: RPE")
    }

    @Test fun edSerieFallo() = scene(
        "ed-serie-fallo", -118.7f,
        deadlift().let { e ->
            e.copy(sets = e.sets.map {
                if (it.id != "s1") it else it.copy(
                    intensityMode = IntensityMode.FAILURE, isFailure = true, targetRPE = null, isDropSet = true,
                    plannedIntensityTechniques = listOf(
                        PlannedTechnique(
                            id = "t1", type = TechniqueType.DROP_SET,
                            params = mapOf("weightPcts" to "-5,-10", "count" to "2", "betweenMarked" to "true"),
                        ),
                    ),
                )
            })
        },
    ) { select("set-row-s1") }

    @Test fun edSerieDescanso() = scene(
        "ed-serie-descanso", -247.7f,
        deadlift().let { e -> e.copy(sets = e.sets.map { if (it.id == "s2") it.copy(restAfterSeconds = 120) else it }) },
    ) {
        select("set-row-s2")
        describe("Descanso 2:00: toca para elegir uno rápido")
    }

    @Test fun edSerieAcciones() = scene("ed-serie-acciones", -248.7f, deadlift()) {
        select("set-row-s2")
        describe("Más acciones de la serie")
    }

    @Test fun edSerieAplicar() = scene("ed-serie-aplicar", -248.7f, deadlift()) {
        select("set-row-s2")
        rule.onNodeWithText("Aplicar a todas").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test fun edModo() = scene("ed-modo", 97.3f, deadlift()) {
        select("set-row-s2")
        describe("Modo del ejercicio: Reps")
    }

    @Test fun edSerieAmrap() = scene(
        "ed-serie-amrap", 97.3f,
        deadlift().let { e ->
            e.copy(
                trainingMode = TrainingMode.AMRAP,
                sets = e.sets.map {
                    it.copy(purpose = SetPurpose.AMRAP, amrapMinimumReps = 4, targetReps = 4, intensityMode = null, targetRPE = null)
                },
            )
        },
    ) { select("set-row-s2") }

    @Test fun edSerieRm() = scene(
        "ed-serie-rm", 97.3f,
        deadlift().let { e ->
            e.copy(
                trainingMode = TrainingMode.RM, reference1RM = 160.0,
                sets = e.sets.map {
                    it.copy(targetPercentageRM = 75.0, loadBasis = LoadBasis.PERCENT_1RM, intensityMode = null, targetRPE = null)
                },
            )
        },
    ) { select("set-row-s2") }

    @Test fun edSerieSoloRpe() = scene(
        "ed-serie-solorpe", 97.3f,
        deadlift().let { e ->
            e.copy(
                trainingMode = TrainingMode.SOLO_RPE,
                sets = e.sets.map { it.copy(weight = null, targetReps = null, targetRPE = 8.0, intensityMode = IntensityMode.RPE) },
            )
        },
    ) { select("set-row-s2") }

    @Test fun edSerieTiempo() = scene("ed-serie-tiempo", 93.6f, plank(TrainingMode.TIME))

    @Test fun edSerieDistancia() = scene("ed-serie-distancia", 93.6f, plank(TrainingMode.DISTANCE))

    @Test fun edPreparacion() = scene(
        "ed-preparacion", 97.3f,
        deadlift().copy(
            warmupSets = listOf(
                WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.4, targetReps = 5, restBetween = 60),
                WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.6, targetReps = 3, restBetween = 60),
            ),
            mobilitySeries = listOf(
                MobilitySeries(id = "m1", name = "Cadera", durationSeconds = 45),
                MobilitySeries(id = "m2", name = "Tobillo", durationSeconds = 60),
            ),
            mobilityConfig = MobilityConfig(mode = MobilityMode.ENFOCADO, totalMinutes = 2),
        ),
    ) {
        select("set-row-s2")
        select("warmup-chip-w1")
    }

    @Test fun edUnilateral() = scene("ed-unilateral", 96f, kickback()) { select("set-row-k1:R") }

    @Test fun edOpciones() = scene("ed-opciones", 97.3f, deadlift()) {
        select("set-row-s2")
        describe("Más opciones del ejercicio")
    }

    @Test fun edDescansoEjercicio() = scene("ed-descanso-ejercicio", 97.3f, deadlift()) {
        select("set-row-s2")
        describe("Descanso del ejercicio")
    }
}

/** La misma hoja a 360 × 740 dp (las escenas densas del lienzo `--size=360x740`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi", application = android.app.Application::class)
class EditorSeriesRenderSheet740Test : EditorSeriesRenderSheetTest() {
    override val windowHeight = 740
    override val suffix = "_740"
}
