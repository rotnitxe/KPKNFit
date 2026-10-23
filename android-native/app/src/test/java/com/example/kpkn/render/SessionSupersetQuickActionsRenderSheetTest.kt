package com.example.kpkn.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.SupersetExerciseConfigOverlay
import com.example.kpkn.screens.sessioneditor.components.SupersetRoundsCarousel
import com.example.kpkn.screens.sessioneditor.components.sheets.ExerciseQuickActionsSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.MobilityPickerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetManagerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetMemberPickerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.TemplatesSheet
import com.example.kpkn.ui.design.KpknSurface
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
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
 * HOJAS DE DIBUJO de ER1c2 (no comprueban nada: escriben PNG). Solo corren con `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se
 * saltan. A 360 dp, al 100 % y al 130 % de letra: las acciones rápidas, crear superserie con 0, 2 y 4 marcados, el editor de rondas
 * cerrado y con una ronda abierta, la configuración de un miembro, la gestión de la superserie, las plantillas y el catálogo de movilidad.
 * La ventana de captura mide 360 × 880 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class SessionSupersetQuickActionsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose del host, para dibujarla a un bitmap (igual que en [ExerciseInfoRenderSheetTest]). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Before
    fun preloadCatalog() {
        runBlocking { CatalogV2ProcessCache.getOrLoad(ApplicationProvider.getApplicationContext<Context>()) }
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        FileOutputStream(File(dir, "$name.png")).use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** La hoja a [scale] de letra, sobre el negro del lienzo y con 16 dp de margen. */
    private fun host(scale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KpknSurface.canvas)
                        .padding(16.dp),
                ) {
                    content()
                }
            }
        }
    }

    private fun exercise(
        id: String,
        name: String,
        definition: String? = null,
        configuration: String? = null,
        sets: Int = 3,
        reps: IntRange = 8..10,
    ) = Exercise(
        id = id,
        name = name,
        exerciseDbId = definition,
        catalogDefinitionId = definition,
        catalogConfigurationId = configuration,
        occurrenceId = "occ-$id",
        sets = List(sets) { index -> ExerciseSet(id = "$id-s$index", targetRepsRange = RepRange(reps.first, reps.last)) },
    )

    private val bench = exercise("press", "Press de banca plano", "bench_press", "bench_press__barbell", sets = 4, reps = 6..8)
    private val squat = exercise("sentadilla", "Sentadilla frontal", "front_squat", "front_squat__barbell", sets = 4, reps = 5..6)
    private val row = exercise("remo", "Remo con barra", sets = 3, reps = 8..10)
    private val curl = exercise("curl", "Curl de bíceps con mancuerna", sets = 3, reps = 10..12)
    private val fondos = exercise("fondos", "Fondos en paralelas", sets = 3, reps = 8..10)

    private val parts = listOf(
        SessionPart(id = "empuje", name = "Empuje", exercises = listOf(bench, fondos)),
        SessionPart(id = "tiron", name = "Tirón", exercises = listOf(row, curl)),
        SessionPart(id = "pierna", name = "Pierna", exercises = listOf(squat)),
    )

    private val group = SupersetGroup(
        id = "grupo",
        exerciseOrder = listOf("press", "remo"),
        restBetweenExercises = 60,
        restAfterSuperset = 120,
        rounds = 3,
    )

    private fun pickerAt(draftIds: List<String>) = host(1f) {
        SupersetMemberPickerSheet(
            draft = SupersetDraft(exerciseIds = draftIds),
            sessionExercises = parts.flatMap { it.exercises },
            sessionParts = parts,
            onUpdateDraft = {},
            onConfirm = {},
            onOpenCatalog = {},
            onDismiss = {},
        )
    }

    // Cada captura es su propio test: Compose solo admite un setContent por test.

    @Test
    fun renderQuickActionsAt100() {
        onlyWhenAsked()
        host(1f) {
            ExerciseQuickActionsSheet(
                exercise = bench,
                catalog = emptyList(),
                workoutLogs = emptyList(),
                onOpenExerciseDetail = {},
                onOpenPicker = {},
                onOpenWarmup = {},
                onOpenMobility = {},
                onDelete = {},
                onManageSuperset = {},
                onDuplicate = {},
            )
        }
        rule.waitForIdle()
        save("quick_actions_s100")
    }

    @Test
    fun renderQuickActionsAt130() {
        onlyWhenAsked()
        host(1.3f) {
            ExerciseQuickActionsSheet(
                exercise = bench,
                catalog = emptyList(),
                workoutLogs = emptyList(),
                onOpenExerciseDetail = {},
                onOpenPicker = {},
                onOpenWarmup = {},
                onOpenMobility = {},
                onDelete = {},
                onManageSuperset = {},
                onDuplicate = {},
            )
        }
        rule.waitForIdle()
        save("quick_actions_s130")
    }

    @Test
    fun renderSupersetPickerWithNoneCheckedAt100() {
        onlyWhenAsked()
        pickerAt(emptyList())
        rule.waitForIdle()
        save("superset_picker_0_s100")
    }

    @Test
    fun renderSupersetPickerWithTwoCheckedAt100() {
        onlyWhenAsked()
        pickerAt(listOf("press", "remo"))
        rule.waitForIdle()
        save("superset_picker_2_s100")
    }

    @Test
    fun renderSupersetPickerWithFourCheckedAt100() {
        onlyWhenAsked()
        pickerAt(listOf("press", "remo", "curl", "fondos"))
        rule.waitForIdle()
        save("superset_picker_4_s100")
    }

    @Test
    fun renderSupersetPickerWithTwoCheckedAt130() {
        onlyWhenAsked()
        host(1.3f) {
            SupersetMemberPickerSheet(
                draft = SupersetDraft(exerciseIds = listOf("press", "remo")),
                sessionExercises = parts.flatMap { it.exercises },
                sessionParts = parts,
                onUpdateDraft = {},
                onConfirm = {},
                onOpenCatalog = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
        save("superset_picker_2_s130")
    }

    @Test
    fun renderSupersetRoundsClosedAt100() {
        onlyWhenAsked()
        host(1f) {
            SupersetRoundsCarousel(
                group = group,
                exercises = listOf(bench, row),
                rounds = 3,
                accentColor = Color(0xFFF49A6E),
                onUpdateRoundRest = { _, _, _ -> },
                onUpdateSet = { _, _, _ -> },
                onRemoveSet = { _, _ -> },
                onMoveSet = { _, _, _ -> },
                onRestoreSet = { _, _ -> },
                onAddRound = {},
                onRemoveRound = {},
            )
        }
        rule.waitForIdle()
        save("superset_rounds_closed_s100")
    }

    @Test
    fun renderSupersetRoundOpenAt130() {
        onlyWhenAsked()
        host(1.3f) {
            SupersetRoundsCarousel(
                group = group,
                exercises = listOf(bench, row),
                rounds = 3,
                accentColor = Color(0xFFF49A6E),
                onUpdateRoundRest = { _, _, _ -> },
                onUpdateSet = { _, _, _ -> },
                onRemoveSet = { _, _ -> },
                onMoveSet = { _, _, _ -> },
                onRestoreSet = { _, _ -> },
                onAddRound = {},
                onRemoveRound = {},
            )
        }
        rule.onNodeWithText("Ronda 1").performClick()
        rule.waitForIdle()
        save("superset_rounds_open_s130")
    }

    @Test
    fun renderSupersetMemberConfigAt100() {
        onlyWhenAsked()
        host(1f) {
            SupersetExerciseConfigOverlay(
                exercise = bench,
                accentColor = Color(0xFFF49A6E),
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onUpdateSet = { _, _ -> },
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
        save("superset_member_config_s100")
    }

    @Test
    fun renderSupersetManagerAt100() {
        onlyWhenAsked()
        host(1f) {
            SupersetManagerSheet(
                exercises = listOf(bench, row),
                partId = "empuje",
                supersetId = "grupo",
                restBetweenSeconds = 60,
                restAfterSeconds = 120,
                onUpdateRestBetween = { _, _, _ -> },
                onUpdateRestAfter = { _, _, _ -> },
                onRemove = { _, _ -> },
                onDismiss = {},
            )
        }
        rule.waitForIdle()
        save("superset_manager_s100")
    }

    @Test
    fun renderSessionTemplatesAt100() {
        onlyWhenAsked()
        host(1f) {
            TemplatesSheet(
                templates = SESSION_TEMPLATES_SYSTEM.take(5),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
        save("session_templates_s100")
    }

    @Test
    fun renderMobilityCatalogAt100() {
        onlyWhenAsked()
        host(1f) {
            MobilityPickerSheet(
                selectedMobilityIds = emptySet(),
                onAdd = {},
                onRemove = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
        save("mobility_catalog_s100")
    }
}
