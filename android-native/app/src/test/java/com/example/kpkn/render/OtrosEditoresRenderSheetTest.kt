package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.DetailSheet
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.programs.importer.ImportEditOp
import com.example.kpkn.screens.programs.importer.ImportProgramScreen
import com.example.kpkn.screens.programs.importer.ImportProgramViewModel
import com.example.kpkn.screens.workout.WorkoutEditSeriesSheet
import com.example.kpkn.screens.workout.WorkoutStructureSheetsState
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.entitlements.LocalPlan
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/**
 * HOJAS DE REVISIÓN de los cuatro editores del paquete G de screens-v2 (no comprueba nada: escribe PNG). Solo corre con
 * `KPKN_RENDER_DIR`; sin esa variable se salta. Cada tablero a 360 × 880 y a 360 × 740 dp con los gráficos nativos:
 * G1 «Editar series» en vivo, G2 «Editar cardio» en vivo, G3 el importador con el panel abierto y la isla, y G4 «Cambiar» del asistente.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class OtrosEditoresRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private val Muscle = Color(0xFFF49A6E)
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private val viewModelStore = ViewModelStore()

    /** Toque por la acción semántica: no depende de lo que haya encima del nodo en la ventana. */
    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag("g-scene").fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(hostView)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0)
        val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(
            full, left, top,
            bounds.width.toInt().coerceAtMost(full.width - left),
            bounds.height.toInt().coerceAtMost(full.height - top),
        )
        FileOutputStream(File(folder, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle()
        full.recycle()
    }

    /** La sesión en vivo, oscurecida, detrás de una hoja (como en los tableros G1 y G2). */
    @androidx.compose.runtime.Composable
    private fun SesionDeDetras(title: String, subtitle: String) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("PIERNA A · 32 MIN", color = WizardColors.textMuted.copy(alpha = 0.55f))
            Text(title, color = WizardColors.text.copy(alpha = 0.4f), modifier = Modifier.padding(top = 10.dp))
            Text(subtitle, color = WizardColors.textMuted.copy(alpha = 0.4f))
        }
    }

    private fun scene(height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalKpknOverlayHost provides null) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("g-scene")) { content() }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    // ───────────────────────────── G1 ─────────────────────────────

    private fun peso(id: String, reps: Int, kg: Double, rir: Int) =
        ExerciseSet(id = id, targetReps = reps, weight = kg, targetRIR = rir, intensityMode = IntensityMode.RIR)

    private val pesoMuertoRumano = Exercise(
        id = "rdl",
        name = "Peso muerto rumano",
        trainingMode = TrainingMode.REPS,
        restTime = 150,
        reference1RM = 140.0,
        sets = listOf(peso("s1", 8, 100.0, 2), peso("s2", 8, 100.0, 2), peso("s3", 8, 100.0, 2), peso("s4", 6, 105.0, 1)),
    )

    private fun g1(height: Int) = scene(height) {
        SesionDeDetras("Peso muerto rumano", "Serie 2 de 4 · 8 × 100 kg · RIR 2")
        WorkoutEditSeriesSheet(
            state = remember { WorkoutStructureSheetsState().apply { editSheetExerciseId = pesoMuertoRumano.id } },
            modeSession = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(pesoMuertoRumano)),
            sessionAccentColor = Muscle,
            bottomHazeState = remember { HazeState() },
            initialSetIndex = 1,
            onSaveDefinition = { _, _ -> },
        )
    }

    @Test fun g1_880() { assumeRender(); g1(880); save("G1-editar-series-880") }
    @Test fun g1_740() { assumeRender(); g1(740); save("G1-editar-series-740") }

    // ───────────────────────────── G2 ─────────────────────────────

    private val biciEstatica = Exercise(
        id = "bike",
        name = "Bici estática",
        cardioDetails = CardioRepeatGrammar.applyUniform(
            CardioDetails(type = CardioType.BIKE_STATIONARY, intensityLevel = 8),
            CardioUniformRepeat(warmupSeconds = 300, workSeconds = 60, restSeconds = 90, rounds = 8, cooldownSeconds = 300, targetRpe = 8.0),
        ),
    )

    private fun g2(height: Int) = scene(height) {
        SesionDeDetras("Bici estática", "8 × 1:00 / 1:30 · RPE 8")
        WorkoutEditSeriesSheet(
            state = remember { WorkoutStructureSheetsState().apply { editSheetExerciseId = biciEstatica.id } },
            modeSession = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(biciEstatica)),
            sessionAccentColor = Color(0xFF8FB2FF),
            bottomHazeState = remember { HazeState() },
            onSaveDefinition = { _, _ -> },
        )
    }

    @Test fun g2_880() { assumeRender(); g2(880); save("G2-editar-cardio-880") }
    @Test fun g2_740() { assumeRender(); g2(740); save("G2-editar-cardio-740") }

    // ───────────────────────────── G3 ─────────────────────────────

    private fun g3(height: Int) {
        val app: Application = ApplicationProvider.getApplicationContext()
        val vm = ImportProgramViewModel(app).also { viewModelStore.put("g3-$height", it) }
        vm.importFromText(
            "Día 1 — Pierna\nSentadilla trasera con barra alta 4x6-8 @100 RPE8\nPeso muerto rumano 4x6-8\nZancada con mancuernas 3x10\nDía 2 — Empuje\nPress banca 4x8 @80kg",
        )
        var tries = 0
        while (vm.state.value.imported == null || vm.state.value.busy) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
            check(++tries < 600) { "la importación no terminó" }
        }
        val imported = checkNotNull(vm.state.value.imported)
        val names = imported.weeks.flatMap { w -> w.days.flatMap { d -> d.exercises } }
        names.map { it.rawName }.distinct().forEach { vm.resolve(it, "cfg-${it.hashCode()}") }
        val squat = names.first { it.rawName.startsWith("Sentadilla") }
        vm.edit(ImportEditOp.Update(squat.id, ImportExercisePatch(rpe = Change.Set(8.0)), allWeeks = false))
        vm.open(squat.id)
        scene(height) {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
    }

    @Test fun g3_880() { assumeRender(); g3(880); save("G3-importador-880") }
    @Test fun g3_740() { assumeRender(); g3(740); save("G3-importador-740") }

    @Test
    fun g3_addSheet_880() {
        assumeRender()
        g3(880)
        tap("import-add-exercise")
        save("G3-importador-anadir-880")
    }

    // ───────────────────────────── G4 ─────────────────────────────

    private val card = PlanCardModel(
        id = "fuerza", title = "Fuerza y masa", kicker = "4 SEMANAS · PERIODIZADO",
        blurb = "Un programa de fuerza con tres días a la semana.", profile = TrainingGoalProfile.STRENGTH_MUSCLE,
        daysLabel = "3 días", minutesLabel = "~60 min", levelLabel = "Intermedio", coverSeed = 3,
    )
    private val model = PlanDetailModel(
        card = card,
        description = "Tres días a la semana, con la fuerza en los básicos.",
        mainExercises = listOf("Sentadilla", "Press banca"),
        blocks = listOf(PlanBlockModel("Volumen", "Semanas 1–2", "Más series y repeticiones.")),
        week = listOf(PlanDayModel(1, "Pierna A", 75, 6, true)),
        reasons = emptyList(),
        notes = emptyList(),
        attribution = null,
    )
    private val lunes = mapOf(
        1 to listOf(
            SetupPlanRevealExercise(instanceId = "lun-1", name = "Sentadilla trasera con barra alta", setsLabel = "4 × 4–6", swappable = true),
            SetupPlanRevealExercise(instanceId = "lun-2", name = "Peso muerto rumano", setsLabel = "4 × 6–8", swappable = true),
            SetupPlanRevealExercise(instanceId = "lun-3", name = "Zancada con mancuernas", setsLabel = "3 × 10 por lado", swappable = true, swappedConfigId = "cfg-z"),
        ),
    )
    private val alternativas = listOf(
        SwapOption("cfg-a", "Sentadilla frontal", "Barra", "knee_dominant", SwapKind.EQUIVALENT),
        SwapOption("cfg-b", "Sentadilla con barra de seguridad", "Safety bar", "knee_dominant", SwapKind.EQUIVALENT),
        SwapOption("cfg-c", "Sentadilla en Smith", "Smith", "knee_dominant", SwapKind.SAME_PATTERN),
        SwapOption("cfg-d", "Sentadilla goblet", "Mancuerna", "knee_dominant", SwapKind.SAME_PATTERN),
        SwapOption("cfg-e", "Prensa de piernas", "Máquina", "knee_dominant", SwapKind.SAME_PATTERN),
    )

    private fun g4(height: Int) {
        scene(height) {
            DetailSheet(
                model = model,
                selected = true,
                reduced = true,
                blur = false,
                shown = { 1f },
                onChoose = {},
                onClose = {},
                exercises = lunes,
                swapOptionsOf = { alternativas },
                onSwap = { _, _ -> },
                onUndoSwap = {},
            )
        }
        tap("setup-plan-day-1")
        tap("setup-plan-swap-lun-1")
    }

    @Test fun g4_880() { assumeRender(); g4(880); save("G4-cambiar-880") }
    @Test fun g4_740() { assumeRender(); g4(740); save("G4-cambiar-740") }

    private fun assumeRender() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", !System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
}
