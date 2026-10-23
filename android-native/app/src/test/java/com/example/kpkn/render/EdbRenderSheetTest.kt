package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.RestSheet
import com.example.kpkn.screens.sessioneditor.components.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Viewport inicial de 360 × 880 dp, a 100 y 130 %. El contenido se mide completo y el viewport recorta el excedente. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class EdbRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun onlyWhenAsked() = assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
    }
    @get:Rule val rule = createComposeRule()
    private var view: View? = null
    private var scale by mutableStateOf(1f)
    private val accent = Color(0xFFF49A6E)
    private fun render(name: String, open: Boolean = false, action: String? = null, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                EditorOverlayTestHost {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true).padding(16.dp)) { content() }
                    }
                }
            }
        }
        if (open) rule.onNodeWithTag("set-row-s1").performClick()
        if (action != null) rule.onNodeWithContentDescription(action).performClick()
        listOf(1f to "100", 1.3f to "130").forEach { (fontScale, suffix) ->
            rule.runOnUiThread { scale = fontScale }
            rule.mainClock.advanceTimeBy(3_000)
            rule.waitForIdle()
            if (open) {
                val header = rule.onNodeWithText("Serie 1 de 3")
                println("EDB $name $suffix header=${header.fetchSemanticsNode().boundsInRoot} root=${rule.onRoot().fetchSemanticsNode().boundsInRoot}")
                header.assertIsDisplayed()
            }
            val host = view!!
            rule.runOnUiThread { host.invalidate(); host.requestLayout() }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { host.draw(Canvas(bitmap)) }
            val target = File(System.getenv("KPKN_RENDER_DIR")!!, "${name}_$suffix.png")
            target.parentFile!!.mkdirs()
            val viewport = Bitmap.createScaledBitmap(bitmap, 360, 880, true)
            target.outputStream().use { viewport.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (viewport !== bitmap) viewport.recycle()
            bitmap.recycle()
        }
    }
    private fun exercise(mode: TrainingMode = TrainingMode.REPS, purpose: SetPurpose = SetPurpose.NONE) = Exercise(
        id = "press", name = "Press banca", trainingMode = mode, reference1RM = 120.0, restTime = 120,
        customUnit = if (mode == TrainingMode.CUSTOM) "brazadas" else null,
        sets = List(3) { i -> ExerciseSet(id = "s${i + 1}", targetReps = 8, weight = if (mode == TrainingMode.RM) 95.0 else 60.0,
            targetRIR = if (mode == TrainingMode.REPS) 2 else null,
            targetRPE = if (mode == TrainingMode.SOLO_RPE) 8.0 else null,
            targetDuration = if (mode == TrainingMode.TIME) 90 else null,
            plannedTargetV2 = if (mode == TrainingMode.DISTANCE) 100.0 else if (mode == TrainingMode.CUSTOM) 20.0 else null,
            purpose = if (i == 0) purpose else SetPurpose.NONE, amrapMinimumReps = if (i == 0 && purpose.isAmrap) 8 else null,
            targetPercentageRM = if (mode == TrainingMode.RM) 80.0 else null, loadBasis = if (mode == TrainingMode.RM) LoadBasis.PERCENT_1RM else null,
            intensityMode = if (mode == TrainingMode.REPS) IntensityMode.RIR else null) })

    @Composable private fun ListBody(exercise: Exercise) = ExerciseSetList(exercise = exercise,
        reference1RM = exercise.reference1RM, trainingMode = exercise.trainingMode, customUnit = exercise.customUnit,
        predictedMetrics = emptyMap(), accentColor = accent, backgroundAccentColor = accent,
        onAddSet = {}, onUpdateSet = { _, _ -> }, onRemoveSet = {}, onMoveSet = { _, _ -> }, onTransformExercise = {})

    @Test fun b2() = render("B2-foco", true) { ListBody(exercise()) }
    @Test fun b3() = render("B3-rm", true) { ListBody(exercise(TrainingMode.RM)) }
    @Test fun b3b() = render("B3b-calibrador", true) { ListBody(exercise(TrainingMode.AMRAP, SetPurpose.CALIBRATOR)) }
    @Test fun b3c() = render("B3c-rpe", true) { ListBody(exercise(TrainingMode.SOLO_RPE)) }
    @Test fun b3d() = render("B3d-tiempo", true) { ListBody(exercise(TrainingMode.TIME)) }
    @Test fun b3e() = render("B3e-distancia", true) { ListBody(exercise(TrainingMode.DISTANCE)) }
    @Test fun b3f() = render("B3f-personalizado", true) { ListBody(exercise(TrainingMode.CUSTOM)) }
    @Test fun b4() = render("B4-modos") { ExerciseModeSheet(current = TrainingMode.REPS, onSelect = {}, onDismiss = {}) }
    @Test fun b5() = render("B5-lateralidad") { LateralitySheet(exercise = exercise().copy(isUnilateral = true), onUpdateExercise = {}, onDismiss = {}) }
    @Test fun b7() = render("B7-descanso") { RestSheet("Entre series", 120, 15, accent, {}, { _, _ -> }) }
    @Test fun b8() = render("B8-opciones", action = "Más opciones del ejercicio") {
        val exercise = exercise()
        ExerciseOptionsBar(exercise = exercise, local = rememberExerciseEditorLocalState(exercise), accentColor = accent,
            isSupersetExercise = false, suppressIndividualRest = false, relationshipAnchorName = null,
            onUpdateExercise = {}, onOpenSuperset = {}, onOpenRelationshipPicker = {}, onClearRelationship = {},
            onOpenCustomUnit = {}, onOpenSmartLoad = {}, onOpenGoal = {}, onOpenRest = {})
    }
    @Test fun bloqueApre() = render("B-APRE") { ApreBlockSheet(exercise(), {}, {}) }
    @Test fun b9() = render("B9-confirmacion") { EditorConfirmationSheet("Aplicar a todas", "Dos series cambiarán para quedar iguales que esta.", "Aplicar", {}, onDismiss = {}) }
}
