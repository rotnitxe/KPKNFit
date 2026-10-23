package com.example.kpkn.screens.workout.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** V7 real en su marco312×425; viewport físico distinto en740/880 y clicks nativos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-mdpi")
class ApreCardUiTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf(apreDurableState())
    private var host: View? = null
    private var continued = 0
    private var edited = 0
    private fun seed(variant: ApreVariant) {
        val sets = Apre.block(200.0,variant).map { row -> ExerciseSet("v7-${row.ordinal}",
            weight = 150.0,targetReps = row.targetReps,purpose = row.purpose,
            amrapMinimumReps = row.minimumReps,loadModeV2 = LoadModeV2.LOAD) }
        val exercise = Exercise("apre","Press banca",sets = sets,apreBlock = ApreBlockReference(variant.xrmReps,sets.map { it.id }))
        val completed = CompletedSet("s3",weight = 150.0,originalLoad = 150.0,reps = 12,
            purpose = SetPurpose.AMRAP,amrapMinimumReps = variant.xrmReps,setIndex = 2,loadMode = LoadModeV2.LOAD)
        val base = apreDurableState().copy(session = Session("apre-session","Fuerza A",exercises = listOf(exercise)),
            completedSets = mapOf(workoutSetKey(exercise.id,2) to completed))
        state.value = base.copy(calibrationDecisions = listOf(buildRecordedApreCalibration(base,exercise,2,completed,1_000)!!))
    }
    private fun mount(font: Float) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font),LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("apre-scene")) {
                        Box(Modifier.align(Alignment.Center).width(312.dp)) {
                            LiveSessionCardFrame {
                                val ui = state.value
                                val exercise = ui.session!!.exercises.first()
                                LiveCalibrationResult(ui,exercise,ui.completedSets.values.single(),ui.calibrationDecisions.last(),
                                    LiveAmrapProgramPreview(AutoregulationMode.OFF,false),
                                    onResolve = { id,status -> state.value = resolveCalibration(state.value,id,status,2_000) },
                                    onContinue = { continued++ },onEdit = { edited++ })
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun frame(height: Int) {
        val screen = rule.onNodeWithTag("apre-scene").getUnclippedBoundsInRoot()
        assertEquals(360f,screen.width.value,.5f); assertEquals(height.toFloat(),screen.height.value,.5f)
        val card = rule.onNodeWithTag("live-card-frame").getUnclippedBoundsInRoot()
        assertEquals(312f,card.width.value,.5f); assertEquals(425f,card.height.value,.5f)
        listOf("calibration-apply","calibration-keep","calibration-revert","calibration-continue","calibration-edit").forEach { tag ->
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().forEach {
                val node = rule.onNodeWithTag(tag).assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue("$tag objetivo48: $bounds",bounds.height.value >= 48f && bounds.width.value >= 48f)
                assertTrue("$tag dentro425: $bounds/$card",bounds.top >= card.top && bounds.bottom <= card.bottom && bounds.left >= card.left && bounds.right <= card.right)
            }
        }
    }
    private fun completeText(node: SemanticsNodeInteraction) {
        node.performScrollTo().assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertFalse(layout.didOverflowHeight)
        assertTrue("texto visible completo",layout.getLineBottom(layout.lineCount-1) <= node.fetchSemanticsNode().boundsInRoot.height+1f)
        assertEquals(layout.layoutInput.text.length,layout.getLineEnd(layout.lineCount-1,visibleEnd = true))
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width+1f)
        }
        println("APRE texto=${layout.layoutInput.text} size=${layout.size} paragraph=${layout.multiParagraph.width}")
    }
    private fun capture(name: String) {
        val dir = System.getenv("KPKN_RENDER_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val view = checkNotNull(host)
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(dir,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
    }
    private fun verify(height: Int,font: Float) {
        seed(ApreVariant.APRE_10); mount(font)
        ApreVariant.entries.forEach { variant ->
            rule.runOnIdle { seed(variant) }
            rule.waitForIdle(); frame(height)
            rule.onNodeWithText("APRE ${variant.xrmReps} · S3").assertIsDisplayed()
            val record = state.value.calibrationDecisions.single()
            completeText(rule.onNodeWithTag("apre-set4-result"))
            completeText(rule.onNodeWithText(record.decision.reason))
            rule.onNodeWithText("Sin referencia previa").assertDoesNotExist()
            rule.onNodeWithText("Capacidad",substring = true).assertDoesNotExist()
            capture("apre-v7-${variant.xrmReps}-$height-${(font*100).toInt()}-proposed")
            rule.onNodeWithTag("calibration-apply").performTouchInput { click() }
            assertEquals(CalibrationStatus.APPLIED,state.value.calibrationDecisions.single().status)
            frame(height)
            rule.onNodeWithTag("calibration-revert").performTouchInput { click() }
            assertEquals(CalibrationStatus.REVERTED,state.value.calibrationDecisions.single().status)
            rule.onNodeWithTag("calibration-continue").performTouchInput { click() }
            rule.onNodeWithTag("calibration-edit").performTouchInput { click() }
            rule.runOnIdle { seed(variant) }
            rule.onNodeWithTag("calibration-keep").performTouchInput { click() }
            assertEquals(CalibrationStatus.KEPT,state.value.calibrationDecisions.single().status)
            frame(height)
        }
        assertEquals(3,continued); assertEquals(3,edited)
    }
    @Test fun v7At880Font100() = verify(880,1f)
    @Test fun v7At880Font130() = verify(880,1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun v7At740Font100() = verify(740,1f)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun v7At740Font130() = verify(740,1.3f)
}
