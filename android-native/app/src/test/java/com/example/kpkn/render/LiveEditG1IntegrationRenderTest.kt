package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.workout.LIVE_EDIT_CONFIRM_TAG
import com.example.kpkn.screens.workout.LIVE_EDIT_DELETE_TAG
import com.example.kpkn.screens.workout.LIVE_EDIT_MODE_TAG
import com.example.kpkn.screens.workout.WorkoutEditSeriesSheet
import com.example.kpkn.screens.workout.WorkoutStructureSheetsState
import com.example.kpkn.screens.workout.liveEditSetChipTag
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Medidas reales de G1, con hoja y OverlayHost nativos. Los PNG son opcionales; las medidas siempre se verifican. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveEditG1IntegrationRenderTest {
    @get:Rule val rule = createComposeRule()
    private var hostView: View? = null

    private fun scene(height: Int, fontScale: Float, amrap: Boolean = false) {
        val exercise = Exercise(id = "g1", name = "Press militar", trainingMode = TrainingMode.RM,
            reference1RM = 125.0, referenceTrainingMax = 100.0, restTime = 150,
            sets = (1..4).map { ExerciseSet(id = "s$it", targetPercentageRM = 80.0, weight = 80.0,
                loadBasis = LoadBasis.PERCENT_TM,
                purpose = if (amrap && it == 1) SetPurpose.CALIBRATOR else SetPurpose.NONE,
                amrapMinimumReps = if (amrap && it == 1) 5 else null) })
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        rule.setContent {
            hostView = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalKpknOverlayHost provides overlays) {
                KPKNTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("g1-scene")) {
                        WorkoutEditSeriesSheet(
                            state = remember { WorkoutStructureSheetsState().apply { editSheetExerciseId = exercise.id } },
                            modeSession = Session(id = "session", name = "Fuerza", exercises = listOf(exercise)),
                            sessionAccentColor = Color(0xFFF49A6E), bottomHazeState = remember { HazeState() },
                            initialSetIndex = 0, onSaveDefinition = { _, _ -> },
                        )
                        KpknOverlayHostContent(overlays)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithTag("g1-scene", useUnmergedTree = true).assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(height.dp)
        rule.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(LIVE_EDIT_MODE_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(liveEditSetChipTag(0)).assertIsDisplayed().assertHeightIsEqualTo(56.dp)
        save("G1-${if (amrap) "AMRAP" else "RM-TM"}-${height}-${(fontScale * 100).toInt()}")
        // Desplaza físicamente el viewport: el scroll semántico deja recortado el foco anidado en este host nativo.
        val viewport = rule.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag(LIVE_EDIT_DELETE_TAG)), useUnmergedTree = true)
        repeat(4) {
            viewport.performTouchInput {
                swipe(start = Offset(this.width * 0.95f, this.height * 0.85f), end = Offset(this.width * 0.95f, this.height * 0.15f), durationMillis = 400)
            }
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
        }
        rule.onNodeWithTag(LIVE_EDIT_DELETE_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG).assertIsDisplayed()
        save("G1-${if (amrap) "AMRAP" else "RM-TM"}-${height}-${(fontScale * 100).toInt()}-acciones")
    }

    private fun save(name: String) {
        val directory = System.getenv("KPKN_RENDER_DIR") ?: return
        val host = checkNotNull(hostView)
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(bitmap)) }
        val folder = File(directory).apply { mkdirs() }
        FileOutputStream(File(folder, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun rmTm880al100() = scene(880, 1f)
    @Test fun rmTm880al130() = scene(880, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun rmTm740al100() = scene(740, 1f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun rmTm740al130() = scene(740, 1.3f)
    @Test fun amrap880al130() = scene(880, 1.3f, amrap = true)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun amrap740al130() = scene(740, 1.3f, amrap = true)
}
