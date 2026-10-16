package com.example.kpkn.screens.workout

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.screens.home.LocalRingMuscleAdjustSave
import com.example.kpkn.screens.home.RingDetailSheet
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.rings.RingsResultBody
import com.example.kpkn.screens.workout.components.ReadinessReportedDiscomfort
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.components.rings.*
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Solo capturas nativas de los composables de producción para feedback visual en el ZFlip.
 * Datos de ejemplo adaptados de RingAdjustRenderSheetTest y HomeSamples.ringsSample;
 * no se lee/escribe estado de usuario ni se añaden controles ficticios a las capturas.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RedesignReviewRingsCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private enum class Scene { READINESS, ENERGY, MUSCLES, STRUCTURE, WIZARD, HOME_DETAIL }
    private var scene by mutableStateOf(Scene.READINESS)

    private val bodyValues = linkedMapOf(
        "Pectorales" to 92, "Deltoides" to 88, "Bíceps" to 95, "Abdomen" to 90, "Core" to 72,
        "Cuádriceps" to 54, "Pantorrillas" to 76, "Trapecio" to 90, "Dorsales" to 85,
        "Tríceps" to 93, "Erectores Espinales" to 68, "Glúteos" to 60, "Isquiosurales" to 58,
    )
    private val coverage = RingsCoverage(
        muscular = RingsChannelCoverage(RecoveryChannelId.MUSCULAR, RingsCoverageSource.REAL_HISTORY, 20, 64),
        system = RingsChannelCoverage(RecoveryChannelId.SYSTEM, RingsCoverageSource.REAL_HISTORY, 20, 72),
        structure = RingsChannelCoverage(RecoveryChannelId.STRUCTURE, RingsCoverageSource.REAL_HISTORY, 20, 80),
    )
    private val snapshot = AugeSnapshot(
        batteries = GlobalBatteries(muscular = 64, cnc = 72, spinal = 80),
        perMuscle = mapOf(
            "Pectorales" to MuscleRecoveryStatus("Pectorales", 58, 12, 20, 9, RecoveryStatus.RECOVERING),
            "Dorsales" to MuscleRecoveryStatus("Dorsales", 66, 6, 30, 12, RecoveryStatus.RECOVERING),
            "Cuádriceps" to MuscleRecoveryStatus("Cuádriceps", 81, 0, 40, 8, RecoveryStatus.OPTIMAL),
            "Isquiotibiales" to MuscleRecoveryStatus("Isquiotibiales", 74, 4, 26, 6, RecoveryStatus.OPTIMAL),
        ),
        dashboard = RecoveryDashboard(
            overallScore = 72, headline = "Buen estado para entrenar", summary = "", recommendation = "", confidenceLabel = "Media",
            channels = listOf(
                RecoveryChannelSnapshot(RecoveryChannelId.MUSCULAR, "Músculos", "Músculos", 64, RecoveryBands.band(64),
                    "", "Volumen normal y buena ejecución.", listOf("Sentadilla pesada ayer", "Volumen alto en piernas")),
                RecoveryChannelSnapshot(RecoveryChannelId.SYSTEM, "Energía", "Energía", 72, RecoveryBands.band(72),
                    "", "Empuja normal, sin ir al límite.", listOf("Dos sesiones intensas esta semana")),
                RecoveryChannelSnapshot(RecoveryChannelId.STRUCTURE, "Columna", "Columna", 80, RecoveryBands.band(80),
                    "", "Carga estructural normal con buena técnica.", listOf("Peso muerto el jueves")),
            ),
            dataLabel = "Historial real",
        ),
        isLoading = false,
        hoursToNormal = mapOf(RecoveryChannelId.MUSCULAR to 14, RecoveryChannelId.SYSTEM to 0),
        personalBaseline = mapOf(RecoveryChannelId.MUSCULAR to 78..92, RecoveryChannelId.STRUCTURE to 70..86),
        sparkline = mapOf(RecoveryChannelId.MUSCULAR to listOf(62,70,66,74,64)),
        coverage = coverage,
    )

    @Test fun captureRingsReadinessAdjustmentsWizardAndHomeDetail() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true,
                LocalDensity provides Density(density.density, 1f),
            ) {
                KPKNTheme {
                    val haze = remember { HazeState() }
                    val host = remember { KpknOverlayHostController(tourSeen = object : TourSeenStore {
                        override fun seenIds() = emptySet<String>()
                        override fun markSeen(tourId: String) = Unit
                    }) }
                    CompositionLocalProvider(
                        LocalKpknOverlayHost provides host,
                        LocalHazeState provides haze,
                        LocalRingMuscleAdjustSave provides { _ -> },
                    ) {
                        Box(Modifier.fillMaxSize().background(KpknSurface.canvas).testTag("review-rings-frame")) {
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(KpknSurface.canvas))
                            key(scene) {
                                when(scene) {
                                    Scene.READINESS -> WorkoutReadinessSheet(
                                        showReadinessSheet = true, gender = null,
                                        sessionMuscleStartingBatteries = bodyValues.filterKeys { it in setOf("Cuádriceps","Glúteos","Isquiosurales","Pantorrillas") },
                                        readinessNeuralStart = 72, readinessMuscularStart = 64, readinessSpinalStart = 80,
                                        hazeState = haze, onSave = { _,_,_,_,_,_,_,_,_ -> }, onDismiss = {},
                                        augeSnapshot = snapshot, voiceSessionEnabled = true,
                                        voiceCaptureMode = VoiceCaptureMode.HANDS_FREE, forcedGleamProgress = 0.35f,
                                        reportedDiscomforts = listOf(ReadinessReportedDiscomfort("knee_patellar","lunes"),ReadinessReportedDiscomfort("lumbar","viernes")),
                                        suggestionPillars = listOf("Cuádriceps","Glúteos","Isquiosurales","Pantorrillas","Erectores Espinales"),
                                        initialDiscomforts = listOf("lumbar"),
                                    )
                                    Scene.ENERGY -> Adjustment(RingChannel.ENERGIA, haze)
                                    Scene.MUSCLES -> Adjustment(RingChannel.MUSCULOS, haze)
                                    Scene.STRUCTURE -> Adjustment(RingChannel.COLUMNA, haze)
                                    Scene.WIZARD -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                                        RingsResultBody(
                                            coverage = coverage, muscleEstimates = bodyValues,
                                            answers = SetupRingsAnswers(manualRings = mapOf("energia" to 72,"columna" to 80)),
                                            error = null, loading = false, today = LocalDate.of(2026,10,16),
                                            onCommitChannel = { _,_ -> }, onCommitMuscle = { _,_ -> }, onResetMuscles = {},
                                            onResetMuscle = {}, onRetry = {},
                                        )
                                    }
                                    Scene.HOME_DETAIL -> RingDetailSheet(
                                        channel = RecoveryChannelId.MUSCULAR, snapshot = snapshot, showModelUpdateNotice = false,
                                        onDismiss = {}, onDismissAdvisory = {}, onModelNoticeShown = {}, perMuscle = snapshot.perMuscle,
                                    )
                                }
                            }
                            KpknOverlayHostContent(host)
                        }
                    }
                }
            }
        }
        capture("01-readiness-compacto-luminoso", expectedTag = "readiness-confirm")
        show(Scene.ENERGY)
        capture("02-ajuste-oficial-energia-g5", expectedTag = "ring-adjust-ring")
        show(Scene.MUSCLES)
        capture("03-ajuste-oficial-muscular-frente-g6", expectedTag = "ring-adjust-body")
        rule.onNodeWithText("Espalda").performClick()
        capture("04-ajuste-oficial-muscular-espalda-g6", expectedTag = "ring-adjust-body")
        show(Scene.STRUCTURE)
        capture("05-ajuste-oficial-columna-g5", expectedTag = "ring-adjust-ring")
        show(Scene.WIZARD)
        capture("06-wizard-rings-resultado", expectedTag = "rings-result-lines", cropTag = "rings-result-lines")
        show(Scene.HOME_DETAIL)
        capture("07-home-detalle-musculos", expectedTag = "home-ring-sheet")
    }

    @Composable private fun Adjustment(channel: RingChannel, haze: HazeState) {
        var energy by remember { mutableIntStateOf(72) }
        var spine by remember { mutableIntStateOf(64) }
        val muscles = remember { mutableStateMapOf<String,Int>().apply { putAll(bodyValues) } }
        val before = bodyValues + ("Cuádriceps" to 66)
        RingAdjustSheet(
            initialChannel = channel, onDismiss = {}, hazeState = haze,
            energy = RingAdjustLevel(energy,88), spine = RingAdjustLevel(spine,82),
            muscles = RingAdjustMuscles(muscles.toMap(),before),
            onEnergyChange = { energy = it }, onSpineChange = { spine = it }, onMuscleChange = { id,value -> muscles[id] = value },
            onEnergyReset = { energy = 88 }, onSpineReset = { spine = 82 }, onMusclesReset = { muscles.clear();muscles.putAll(before) },
        )
    }

    private fun show(next: Scene) {
        rule.runOnIdle { scene = next }
        rule.waitForIdle()
    }

    private fun capture(name: String, expectedTag: String, cropTag: String = "review-rings-frame") {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag(expectedTag, useUnmergedTree = true).assertExists()
        val image = rule.onNodeWithTag(cropTag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null),"review-rings").apply { check(isDirectory || mkdirs()) }
        File(directory,"$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
}
