package com.example.kpkn.render

import com.example.kpkn.data.models.SetPurpose
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorExpanded
import com.example.kpkn.screens.sessioneditor.components.ExerciseModeSheetBody
import com.example.kpkn.screens.sessioneditor.components.ExerciseSetList
import com.example.kpkn.domain.training.generator.Prescriber
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
import java.io.FileOutputStream

/**
 * Hoja de render del ejercicio desplegado (ER1c1): la lista de series cerrada y abierta (RM, unilateral, AMRAP y Tiempo), la preparación
 * (vacía, con 3 aproximaciones y con movilidad), la hoja de los 7 modos y la lista del cajón «Editar series» de la sesión en vivo. Cada
 * escena a 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin esa variable la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi", application = android.app.Application::class)
class SessionExerciseExpandedRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)
    private val accent = Color(0xFFF49A6E)

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    /** Dibuja [content] en una hoja de 360 dp y guarda el PNG al 100 % y al 130 %. Si [openRowTag] existe, abre esa fila antes. */
    private fun renderSheet(name: String, openRowTag: String? = null, content: @Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    content()
                }
            }
        }
        if (openRowTag != null) {
            rule.onNodeWithTag(openRowTag).performClick()
        }
        for ((scale, suffix) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("${name}_$suffix")
        }
    }

    @Composable
    private fun ExpandedExercise(exercise: Exercise) {
        val local = rememberExerciseEditorLocalState(exercise)
        ExerciseEditorExpanded(
            exercise = exercise,
            local = local,
            accentColor = accent,
            coverColor = accent,
            isSupersetExercise = false,
            suppressIndividualRest = false,
            resolved1RM = 100.0,
            predictedMetrics = emptyMap(),
            localPrEstimatedRm = null,
            relationshipAnchorName = null,
            cardioFirst = false,
            showCardioPlacementChip = false,
            onToggleCardioPlacement = null,
            cardioHistory = null,
            onUpdateExercise = {},
            onAddSet = {},
            onUpdateSet = { _, _ -> },
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

    private fun rmExercise() = Exercise(
        id = "press",
        name = "Press militar",
        trainingMode = TrainingMode.RM,
        reference1RM = 100.0,
        restTime = 120,
        sets = listOf(75.0, 80.0, 85.0).mapIndexed { index, percent ->
            ExerciseSet(
                id = "rm$index",
                targetPercentageRM = percent,
                targetRIR = 2,
                intensityMode = IntensityMode.RIR,
                targetReps = 6,
            )
        },
    )

    private fun repsExercise(sets: List<ExerciseSet>) = Exercise(
        id = "press-reps",
        name = "Press inclinado",
        trainingMode = TrainingMode.REPS,
        restTime = 90,
        sets = sets,
    )

    private fun repsSets() = listOf(
        ExerciseSet(id = "r1", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR),
        ExerciseSet(id = "r2", targetReps = 10, targetRIR = 2, intensityMode = IntensityMode.RIR),
        ExerciseSet(id = "r3", targetReps = 10, targetRIR = 1, intensityMode = IntensityMode.RIR),
    )

    private fun bareExercise() = repsExercise(listOf(ExerciseSet(id = "b1", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR)))

    @Test
    fun rmBilateralConRirFilaCerrada() {
        onlyWhenAsked()
        renderSheet("rm_bilateral_cerrada") {
            ExerciseSetList(
                exercise = rmExercise(),
                reference1RM = 100.0,
                trainingMode = TrainingMode.RM,
                customUnit = null,
                predictedMetrics = mapOf("rm0" to 6.0, "rm1" to 5.0, "rm2" to 4.0),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
            )
        }
    }

    @Test
    fun rmBilateralConRirFilaAbierta() {
        onlyWhenAsked()
        renderSheet("rm_bilateral_abierta", openRowTag = "set-row-rm1") {
            ExerciseSetList(
                exercise = rmExercise(),
                reference1RM = 100.0,
                trainingMode = TrainingMode.RM,
                customUnit = null,
                predictedMetrics = mapOf("rm0" to 6.0, "rm1" to 5.0, "rm2" to 4.0),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    /** Sentadilla del generador con rango y RIR (ER2a): la carga guardada, el %RM, el rango y el RIR de cada serie. */
    private fun rmRangoRirExercise() = Exercise(
        id = "sentadilla-rm",
        name = "Sentadilla trasera",
        trainingMode = TrainingMode.RM,
        reference1RM = 120.0,
        restTime = 210,
        sets = listOf(
            Triple("rr0", 75.0, RepRange(6, 8)),
            Triple("rr1", 80.0, RepRange(4, 6)),
            Triple("rr2", 85.0, RepRange(3, 5)),
        ).map { (id, percent, range) ->
            ExerciseSet(
                id = id,
                weight = Prescriber.roundLoad(120.0 * percent / 100.0),
                targetPercentageRM = percent,
                targetReps = range.max,
                targetRepsRange = range,
                targetRIR = 2,
                intensityMode = IntensityMode.SOLO_RM,
                loadBasis = LoadBasis.PERCENT_1RM,
            )
        },
    )

    @Test
    fun rmConRangoYRirFilaCerrada() {
        onlyWhenAsked()
        renderSheet("rm_rango_rir_cerrada") {
            ExerciseSetList(
                exercise = rmRangoRirExercise(),
                reference1RM = 120.0,
                trainingMode = TrainingMode.RM,
                customUnit = null,
                predictedMetrics = mapOf("rr0" to 9.0, "rr1" to 7.0, "rr2" to 5.0),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
            )
        }
    }

    @Test
    fun rmConRangoYRirFilaAbierta() {
        onlyWhenAsked()
        renderSheet("rm_rango_rir_abierta", openRowTag = "set-row-rr1") {
            ExerciseSetList(
                exercise = rmRangoRirExercise(),
                reference1RM = 120.0,
                trainingMode = TrainingMode.RM,
                customUnit = null,
                predictedMetrics = mapOf("rr0" to 9.0, "rr1" to 7.0, "rr2" to 5.0),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    /** Tipo de carga de la serie abierta (ER2a): a letra grande pasa a rejilla 2 × 2 de celdas iguales. */
    @Test
    fun rejillaTipoDeCargaFilaAbierta() {
        onlyWhenAsked()
        renderSheet("rejilla_tipo_carga_abierta", openRowTag = "set-row-r1") {
            ExerciseSetList(
                exercise = repsExercise(repsSets()),
                reference1RM = 100.0,
                trainingMode = TrainingMode.REPS,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    @Test
    fun unilateralFilaAbierta() {
        onlyWhenAsked()
        renderSheet("unilateral_abierta", openRowTag = "set-row-r1:L") {
            val uniSets = repsSets().map { set ->
                set.copy(
                    leftTarget = UnilateralTarget(targetReps = set.targetReps, targetRIR = set.targetRIR, intensityMode = IntensityMode.RIR, weight = 12.0),
                    rightTarget = UnilateralTarget(targetReps = set.targetReps, targetRIR = set.targetRIR, intensityMode = IntensityMode.RIR, weight = 12.0),
                )
            }
            val uni = repsExercise(uniSets).copy(
                isUnilateral = true,
                unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
                restBetweenSidesSeconds = 15,
            )
            ExerciseSetList(
                exercise = uni,
                reference1RM = 100.0,
                trainingMode = TrainingMode.REPS,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    @Test
    fun amrapFilaAbierta() {
        onlyWhenAsked()
        renderSheet("amrap_abierta", openRowTag = "set-row-a1") {
            val amrap = Exercise(
                id = "dominadas",
                name = "Dominadas",
                trainingMode = TrainingMode.AMRAP,
                sets = listOf(
                    ExerciseSet(id = "a1", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                    ExerciseSet(id = "a2", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                ),
            )
            ExerciseSetList(
                exercise = amrap,
                reference1RM = null,
                trainingMode = TrainingMode.AMRAP,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    @Test
    fun tiempoFilaAbierta() {
        onlyWhenAsked()
        renderSheet("tiempo_abierta", openRowTag = "set-row-t1") {
            val timed = Exercise(
                id = "plancha",
                name = "Plancha",
                trainingMode = TrainingMode.TIME,
                sets = listOf(ExerciseSet(id = "t1", targetDuration = 45), ExerciseSet(id = "t2", targetDuration = 60)),
            )
            ExerciseSetList(
                exercise = timed,
                reference1RM = null,
                trainingMode = TrainingMode.TIME,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = {},
            )
        }
    }

    @Test
    fun preparacionVaciaConLasDosPildoras() {
        onlyWhenAsked()
        renderSheet("preparacion_vacia") {
            ExpandedExercise(bareExercise())
        }
    }

    @Test
    fun preparacionConTresAproximaciones() {
        onlyWhenAsked()
        renderSheet("preparacion_3_aproximaciones") {
            ExpandedExercise(
                bareExercise().copy(
                    warmupSets = listOf(
                        WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.5, targetReps = 8, restBetween = 60),
                        WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.6, targetReps = 6, restBetween = 60),
                        WarmupSetDefinition(id = "w3", percentageOfWorkingWeight = 0.7, targetReps = 4, restBetween = 90),
                    ),
                ),
            )
        }
    }

    @Test
    fun preparacionConMovilidad() {
        onlyWhenAsked()
        renderSheet("preparacion_con_movilidad") {
            ExpandedExercise(
                bareExercise().copy(
                    mobilitySeries = listOf(
                        MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45),
                        MobilitySeries(id = "m2", name = "Bisagra de cadera", reps = "10"),
                    ),
                ),
            )
        }
    }

    @Test
    fun hojaDeLosSieteModos() {
        onlyWhenAsked()
        renderSheet("hoja_modos") {
            ExerciseModeSheetBody(current = TrainingMode.REPS, onSelect = {})
        }
    }

    @Test
    fun listaDelCajonEditarSeriesEnVivo() {
        onlyWhenAsked()
        renderSheet("editar_series_en_vivo") {
            ExerciseSetsCarousel(
                exercise = repsExercise(repsSets()),
                reference1RM = 100.0,
                trainingMode = TrainingMode.REPS,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
            )
        }
    }
}
