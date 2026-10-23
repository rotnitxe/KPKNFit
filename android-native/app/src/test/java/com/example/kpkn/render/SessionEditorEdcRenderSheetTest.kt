package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.EffectiveSeriesChipRow
import com.example.kpkn.screens.sessioneditor.components.ExercisePreparationSection
import com.example.kpkn.screens.sessioneditor.components.GroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.MobilityDoseEditor
import com.example.kpkn.screens.sessioneditor.components.MobilityGlobalTimeButton
import com.example.kpkn.screens.sessioneditor.components.MobilityPreparationChips
import com.example.kpkn.screens.sessioneditor.components.WarmupCommonRestButton
import com.example.kpkn.screens.sessioneditor.components.WarmupPreparationChips
import com.example.kpkn.screens.sessioneditor.components.mobilityTotalSummary
import com.example.kpkn.screens.sessioneditor.components.sheets.MobilityPickerSheet
import com.example.kpkn.ui.design.KpknType
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO de ED-C: aproximación (H1), movilidad (H2), catálogo (H3), dosis (H4), preparación completa (B6) y
 * movilidad añadida (D9). A 360 × 880 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. Escribe `edc_<escena>[_s130].png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class SessionEditorEdcRenderSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var scene by mutableStateOf("h1_aprox")
    private var fontScale by mutableStateOf(1f)
    private var hostView: View? = null
    private val noop: () -> Unit = {}

    private val scenes = listOf(
        "h1_aprox",
        "h2_movilidad",
        "h3_catalogo",
        "h4_dosis",
        "b6_preparacion",
        "d9_movilidad",
        "d9_grupo",
    )

    private val press = Exercise(
        id = "press",
        name = "Press banca",
        sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 100.0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 100.0),
            ExerciseSet(id = "s3", targetReps = 8, weight = 100.0),
            ExerciseSet(id = "s4", targetReps = 8, weight = 100.0),
        ),
    )

    private val warmups = listOf(
        WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.4, targetReps = 10, restBetween = 60),
        WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.6, targetReps = 8, restBetween = 60),
        WarmupSetDefinition(id = "w3", percentageOfWorkingWeight = 0.8, targetReps = 5, restBetween = 90),
    )

    private val catalog = MobilityExerciseCatalog.getAllMobilityExercises()
    private val mobility = catalog.take(3).mapIndexed { index, exercise ->
        MobilitySeries(
            id = "m${index + 1}",
            catalogConfigurationId = exercise.id,
            name = exercise.name,
            sets = if (index == 2) 2 else 1,
            durationSeconds = if (index == 1) null else 30 + index * 15,
            reps = if (index == 1) "10" else null,
            notes = if (index == 0) "Sin dolor; respiración lenta." else null,
        )
    }

    // ───────────────────────────── escenas ─────────────────────────────

    @Composable
    private fun SceneContent(name: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (name) {
                    "h1_aprox" -> {
                    SceneTitle("H1 · Aproximación en el ejercicio")
                    WarmupPreparationChips(
                        exerciseId = "press",
                        sets = warmups,
                        workingWeightKg = 100.0,
                        capacityKg = 130.0,
                        effectiveSetCount = press.sets.size,
                        onUpdate = { _, _ -> },
                        onRemove = {},
                        onAdd = noop,
                        initiallyExpandedId = "w2",
                    )
                    WarmupCommonRestButton(sets = warmups, onUpdate = { _, _ -> })
                    SceneTitle("Series efectivas · misma ficha")
                    EffectiveSeriesChipRow(
                        rows = listOf(
                            Triple("S1", "100 kg", "8 reps"),
                            Triple("S2", "100 kg", "8 reps"),
                        ),
                    )
                    }
                    "h2_movilidad" -> {
                    SceneTitle("H2 · Movilidad en el ejercicio")
                    MobilityGlobalTimeButton(mobilityConfig = null, onUpdateConfig = {})
                    MobilityPreparationChips(
                        exerciseId = "press",
                        series = mobility,
                        onUpdate = { _, _ -> },
                        onMove = { _, _ -> },
                        onRemove = {},
                        onAdd = noop,
                        initiallyExpandedId = "m1",
                    )
                    }
                    "h3_catalogo" -> {
                    MobilityPickerSheet(
                        selectedMobilityIds = setOf(catalog.first().id),
                        onAdd = {},
                        onRemove = {},
                        onDismiss = noop,
                    )
                    }
                    "h4_dosis" -> {
                    SceneTitle("H4 · Dosis por tiempo")
                    MobilityDoseEditor(
                        movementId = "h4-time",
                        name = "Movilidad de tobillo en pared",
                        series = MobilitySeries(id = "h4-time", name = "Movilidad de tobillo en pared", durationSeconds = 45),
                        onApply = {},
                        onCancel = noop,
                        onRemove = {},
                        showRemove = true,
                    )
                    SceneTitle("H4 · Dosis por repeticiones")
                    MobilityDoseEditor(
                        movementId = "h4-reps",
                        name = "Gato-vaca",
                        series = MobilitySeries(id = "h4-reps", name = "Gato-vaca", sets = 2, reps = "10"),
                        onApply = {},
                        onCancel = noop,
                        onRemove = {},
                        showRemove = true,
                    )
                    }
                    "b6_preparacion" -> {
                    SceneTitle("B6 · Preparación completa")
                    PreparationSummaryCard()
                    }
                    "d9_movilidad" -> {
                    MobilityPickerSheet(
                        selectedMobilityIds = catalog.take(2).map { it.id }.toSet(),
                        onAdd = {},
                        onRemove = {},
                        onDismiss = noop,
                    )
                    }
                    "d9_grupo" -> {
                        SceneTitle("Movilidad de la sesión")
                        GroupEditorCard(part = SessionPart(id = "mobility-part", name = "Preparación", isMobilityGroup = true,
                            mobilitySeries = mobility, mobilityConfig = MobilityConfig(totalMinutes = 6)),
                            collapsed = false, onToggleCollapse = {}, onRename = {}, onRemove = {},
                            isDragging = false, isDropTarget = false, onBoundsChange = {}, onDrag = {}, onDragEnd = {})
                    }
                }
            }
        }
    }

    @Composable
    private fun SceneTitle(text: String) {
        androidx.compose.material3.Text(text, style = KpknType.eyebrow, color = WizardColors.textMuted)
    }

    @Composable
    private fun PreparationSummaryCard() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExercisePreparationSection(
                exercise = press.copy(mobilitySeries = mobility, warmupSets = warmups),
                mobilityBlockExpanded = true,
                warmupBlockExpanded = true,
                onToggleMobilityBlock = noop,
                onToggleWarmupBlock = noop,
                onUpdateExercise = {},
                onRemoveMobility = {},
                onOpenMobility = noop,
                capacityKg = 130.0,
            )
        }
    }

    // ───────────────────────────── captura ─────────────────────────────

    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { drawView().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderEdcScenes() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                SceneContent(scene)
            }
        }
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            for (name in scenes) {
                rule.runOnIdle { scene = name }
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                rule.mainClock.advanceTimeByFrame()
                save("edc_$name$suffix")
            }
        }
    }
}
