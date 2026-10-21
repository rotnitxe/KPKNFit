package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.screens.workout.ExerciseHistoryContent
import com.example.kpkn.screens.workout.ExerciseHistoryEntry
import com.example.kpkn.screens.workout.ExerciseSheetSubtitle
import com.example.kpkn.screens.workout.ExerciseSetupSheetContent
import com.example.kpkn.screens.workout.ExerciseTagSheetContent
import com.example.kpkn.screens.workout.RenameTagResult
import com.example.kpkn.screens.workout.WorkoutDrawer
import com.example.kpkn.screens.workout.WorkoutEditSeriesSheet
import com.example.kpkn.screens.workout.WorkoutReorderSheets
import com.example.kpkn.screens.workout.WorkoutStructureSheetsState
import com.example.kpkn.screens.workout.WorkoutSupersetCreatorSheet
import com.example.kpkn.screens.workout.WorkoutTagManagerModal
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionExerciseThumb
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.screens.workout.TimelineElement
import com.example.kpkn.screens.workout.WorkoutSetCardVisualState
import com.example.kpkn.screens.workout.WorkoutSetPager
import com.example.kpkn.screens.workout.components.ExerciseOptionsCardioContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsIconThumb
import com.example.kpkn.screens.workout.components.ExerciseOptionsSheet
import com.example.kpkn.screens.workout.components.ExerciseOptionsStrengthContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsSupersetContent
import com.example.kpkn.screens.workout.components.SeriesOptionsSheet
import com.example.kpkn.screens.workout.components.SupersetMemberOption
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.screens.workout.exerciseOptionsSubtitle
import com.example.kpkn.screens.workout.supersetOptionsSubtitle
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
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
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

private const val SHEET_TAG = "lr1d-sheet"
private val ACCENT = Color(0xFFF49A6E)

private fun sampleSet(id: String, weight: Double, reps: Int) = ExerciseSet(
    id = id,
    targetReps = reps,
    targetRIR = 2,
    intensityMode = IntensityMode.RIR,
    weight = weight,
    loadModeV2 = LoadModeV2.LOAD,
    unitModeV2 = UnitModeV2.REPS,
)

// Datos de ejemplo de la sesión «Pierna A»: tres ejercicios, con la sentadilla como ejercicio de la acción.
private val SQ = Exercise(
    id = "sq",
    name = "Sentadilla Trasera con Barra Alta",
    trainingMode = TrainingMode.REPS,
    sets = listOf(sampleSet("sq-1", 120.0, 6), sampleSet("sq-2", 120.0, 6), sampleSet("sq-3", 120.0, 6)),
    setupDetails = ExerciseSetupDetails(baseLoadKg = 20.0, seatPosition = "3", pinPosition = "5"),
)
private val PM = Exercise(
    id = "pm",
    name = "Peso Muerto Convencional",
    trainingMode = TrainingMode.REPS,
    sets = listOf(sampleSet("pm-1", 140.0, 5)),
)
private val PRESS = Exercise(
    id = "press",
    name = "Press de banca",
    trainingMode = TrainingMode.REPS,
    sets = listOf(sampleSet("press-1", 80.0, 8)),
)
private val SAMPLE_EXERCISES = listOf(SQ, PM, PRESS)
private val SAMPLE_SESSION = Session(id = "pierna-a", name = "Pierna A", exercises = SAMPLE_EXERCISES)

private val SAMPLE_PROFILE = WorkoutContextProfile(
    id = "perfil-barra",
    exerciseKey = "sq",
    tagId = "barra",
    setupLabel = "Barra alta",
    machineBrand = "Rack 2",
    setupDetails = ExerciseSetupDetails(seatPosition = "3", pinPosition = "5", baseLoadKg = 20.0),
    baseLoadKg = 20.0,
)

/** Tres sesiones recientes del ejercicio: la del tag activo («Barra») va resaltada; la segunda es la del récord. */
private fun sampleHistory(): List<ExerciseHistoryEntry> {
    val hoy = LocalDate.now()
    return listOf(
        ExerciseHistoryEntry(
            date = hoy.minusDays(1).toString(),
            sets = listOf(
                CompletedSet(id = "h1-1", weight = 125.0, reps = 6),
                CompletedSet(id = "h1-2", weight = 125.0, reps = 5),
            ),
            e1rm = 146.2,
            tag = "Barra",
        ),
        ExerciseHistoryEntry(
            date = hoy.minusDays(3).toString(),
            sets = listOf(
                CompletedSet(id = "h2-1", weight = 130.5, reps = 4),
                CompletedSet(id = "h2-2", weight = 122.5, reps = 6),
            ),
            e1rm = 150.1,
            tag = "Barra",
            notes = "Récord de la semana",
            latestHistoryColor = HistoryColorV2.YELLOW,
        ),
        ExerciseHistoryEntry(
            date = hoy.minusDays(6).toString(),
            sets = listOf(CompletedSet(id = "h3-1", weight = 115.0, reps = 8)),
            e1rm = 135.8,
            tag = "Smith",
        ),
    )
}

/**
 * HOJA DE RENDER de las opciones del ejercicio (LR1d). Dibuja las piezas REALES de la sesión con datos de ejemplo, a 360 dp, con la
 * letra al 100 % y al 130 %, y escribe un PNG por pieza en `KPKN_RENDER_DIR`. Sin esa variable se salta. El cajón de opciones, el
 * setup, editar series, las etiquetas, el historial, reordenar y crear superserie van dentro de `WorkoutDrawer` o de los diálogos de
 * KPKN, que se pintan en su sitio (sin ventana propia) gracias a `KpknPortal` sin anfitrión.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveSessionOptionsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que LiveSessionRefineRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    // ── C1 · Opciones de la serie ──

    @Test
    fun opcionesSerie100() {
        onlyWhenAsked()
        draw("opciones_serie_100", 1f) { OpcionesSerie() }
    }

    @Test
    fun opcionesSerie130() {
        onlyWhenAsked()
        draw("opciones_serie_130", 1.3f) { OpcionesSerie() }
    }

    // ── C2 · Opciones del ejercicio ──

    @Test
    fun opcionesFuerza100() {
        onlyWhenAsked()
        draw("opciones_fuerza_100", 1f) { OpcionesFuerza() }
    }

    @Test
    fun opcionesFuerza130() {
        onlyWhenAsked()
        draw("opciones_fuerza_130", 1.3f) { OpcionesFuerza() }
    }

    @Test
    fun opcionesSuperserie100() {
        onlyWhenAsked()
        draw("opciones_superserie_100", 1f) { OpcionesSuperserie() }
    }

    @Test
    fun opcionesSuperserie130() {
        onlyWhenAsked()
        draw("opciones_superserie_130", 1.3f) { OpcionesSuperserie() }
    }

    @Test
    fun opcionesCardio100() {
        onlyWhenAsked()
        draw("opciones_cardio_100", 1f) { OpcionesCardio() }
    }

    @Test
    fun opcionesCardio130() {
        onlyWhenAsked()
        draw("opciones_cardio_130", 1.3f) { OpcionesCardio() }
    }

    @Test
    fun setup100() {
        onlyWhenAsked()
        draw("setup_100", 1f) { SetupDrawer() }
    }

    @Test
    fun setup130() {
        onlyWhenAsked()
        draw("setup_130", 1.3f) { SetupDrawer() }
    }

    @Test
    fun editarSeries100() {
        onlyWhenAsked()
        draw("editar_series_100", 1f) { EditSeriesSheet() }
    }

    @Test
    fun editarSeries130() {
        onlyWhenAsked()
        draw("editar_series_130", 1.3f) { EditSeriesSheet() }
    }

    @Test
    fun etiquetasRapida100() {
        onlyWhenAsked()
        draw("etiquetas_rapida_100", 1f) { QuickTagsDrawer() }
    }

    @Test
    fun etiquetasRapida130() {
        onlyWhenAsked()
        draw("etiquetas_rapida_130", 1.3f) { QuickTagsDrawer() }
    }

    @Test
    fun gestorEtiquetas100() {
        onlyWhenAsked()
        draw("gestor_etiquetas_100", 1f) { TagManager() }
    }

    @Test
    fun gestorEtiquetas130() {
        onlyWhenAsked()
        draw("gestor_etiquetas_130", 1.3f) { TagManager() }
    }

    @Test
    fun historial100() {
        onlyWhenAsked()
        draw("historial_100", 1f) { HistoryDrawer() }
    }

    @Test
    fun historial130() {
        onlyWhenAsked()
        draw("historial_130", 1.3f) { HistoryDrawer() }
    }

    // ── C3 · Orden de la sesión ──

    @Test
    fun reordenar100() {
        onlyWhenAsked()
        draw("reordenar_100", 1f) { ReorderSheet() }
    }

    @Test
    fun reordenar130() {
        onlyWhenAsked()
        draw("reordenar_130", 1.3f) { ReorderSheet() }
    }

    @Test
    fun reordenarSeleccion100() {
        onlyWhenAsked()
        draw("reordenar_seleccion_100", 1f, afterShow = { marcarEnLaHojaDeOrden() }) { ReorderSheet() }
    }

    @Test
    fun reordenarSeleccion130() {
        onlyWhenAsked()
        draw("reordenar_seleccion_130", 1.3f, afterShow = { marcarEnLaHojaDeOrden() }) { ReorderSheet() }
    }

    @Test
    fun superserie100() {
        onlyWhenAsked()
        draw("superserie_100", 1f) { SupersetSheet() }
    }

    @Test
    fun superserie130() {
        onlyWhenAsked()
        draw("superserie_130", 1.3f) { SupersetSheet() }
    }

    @Composable
    private fun SetupDrawer() {
        WorkoutDrawer(title = "Setup", onDismiss = {}, hazeState = HazeState()) {
            ExerciseSheetSubtitle(SQ.name)
            ExerciseSetupSheetContent(
                exercise = SQ,
                currentSet = SQ.sets.first(),
                currentTag = "Barra",
                profiles = listOf(SAMPLE_PROFILE),
                activeProfileId = SAMPLE_PROFILE.id,
                onTagSet = {},
                onSelectProfile = {},
                onSaveProfile = {},
                onUpdateExercise = {},
                onUpdateSet = { _, _ -> },
                onDismiss = {},
                sessionAccentColor = ACCENT,
                userTags = listOf("Barra", "Mancuerna", "Smith"),
                suggestedTag = "Barra",
            )
        }
    }

    @Composable
    private fun EditSeriesSheet() {
        WorkoutEditSeriesSheet(
            state = WorkoutStructureSheetsState().apply { editSheetExerciseId = SQ.id },
            modeSession = SAMPLE_SESSION,
            sessionAccentColor = ACCENT,
            bottomHazeState = HazeState(),
            onSaveDefinition = { _, _ -> },
        )
    }

    @Composable
    private fun QuickTagsDrawer() {
        WorkoutDrawer(title = "Etiquetas", onDismiss = {}, hazeState = HazeState()) {
            ExerciseSheetSubtitle(SQ.name)
            ExerciseTagSheetContent(
                currentTag = "Barra",
                onTagSet = {},
                onDismiss = {},
                userTags = listOf("Barra", "Mancuerna", "Smith"),
                suggestedTag = "Smith",
                profiles = listOf(SAMPLE_PROFILE),
            )
        }
    }

    @Composable
    private fun TagManager() {
        WorkoutTagManagerModal(
            tag = WorkoutTag(
                id = "barra",
                name = "Barra",
                exerciseKey = "sq",
                subTags = listOf(
                    WorkoutSubTag(id = "pin", name = "Pin 5", category = SubTagCategory.SETUP),
                    WorkoutSubTag(id = "rogue", name = "Rogue", category = SubTagCategory.MARCA),
                ),
            ),
            exerciseId = SQ.id,
            onRename = { RenameTagResult.Success },
            onDelete = {},
            onResetHistory = {},
            onAddSubTag = { _, _ -> },
            onRemoveSubTag = {},
            onToggleSubTagActive = {},
            activeSubTagIds = listOf("pin"),
            onDismiss = {},
            history = sampleHistory(),
            machineBrand = "Rogue",
            baseLoadKg = "20",
            setupNotes = "Pin 5",
            onSaveSetup = {},
        )
    }

    @Composable
    private fun HistoryDrawer() {
        WorkoutDrawer(title = "Historial", onDismiss = {}, hazeState = HazeState()) {
            ExerciseHistoryContent(history = sampleHistory(), activeTag = "Barra")
        }
    }

    /** Lo que se ve tras abrir «Seleccionar» y marcar la extensión de cuádriceps y la superserie. */
    private fun marcarEnLaHojaDeOrden() {
        rule.onNodeWithText("Seleccionar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Extensión de cuádriceps").performClick()
        rule.onNodeWithText("Peso muerto rumano + Curl femoral").performClick()
        rule.waitForIdle()
    }

    /** La sesión de la sentadilla de detrás de las hojas de las capturas (C1): cabecera y tarjeta atenuadas. */
    @Composable
    private fun SesionDeDetras() {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text("Sentadilla Trasera con…", style = KpknType.title, color = WizardColors.text.copy(alpha = 0.35f))
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(430.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(KpknSurface.module.copy(alpha = 0.35f)),
            )
        }
    }

    @Composable
    private fun OpcionesSerie() {
        val completed = mapOf(
            "sq_0" to CompletedSet(id = "c0", weight = 120.0, reps = 6, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 2.0),
        )
        Box(Modifier.fillMaxSize()) {
            SesionDeDetras()
            SeriesOptionsSheet(
                exercise = SQ.copy(sets = SQ.sets + sampleSet("sq-4", 120.0, 6)),
                target = SeriesTypeTarget(exerciseId = "sq", fromSetIdx = 1, toSetIdx = 1),
                completedSets = completed,
                omittedSetKeys = emptySet(),
                onDismiss = {},
                onApply = { _, _ -> },
                onEdit = {},
                onAddSet = {},
                onSkipSet = {},
                onDeleteSet = {},
                bottomClearance = WorkoutUiTokens.liveCockpitCompactHeight(1f) + 8.dp,
            )
            // La barra inferior queda por encima del velo, como en la sesión.
            WorkoutRoadmapBar(
                exercises = SAMPLE_EXERCISES,
                currentIdx = 0,
                currentSetIdx = 1,
                completedSets = completed,
                onSelect = {},
                aboveCarousel = {
                    WorkoutSetPager(
                        elements = listOf(
                            TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
                            TimelineElement.BilateralSet(pageIndex = 1, label = "S2", state = WorkoutSetCardVisualState.ACTIVE),
                            TimelineElement.BilateralSet(pageIndex = 2, label = "S3", state = WorkoutSetCardVisualState.FUTURE),
                            TimelineElement.BilateralSet(pageIndex = 3, label = "S4", state = WorkoutSetCardVisualState.FUTURE),
                        ),
                        activeElementIndex = 1,
                        completedCount = 1,
                        totalCount = 4,
                        onSelectPage = {},
                        onAddSet = {},
                        onLongPressPage = {},
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    @Composable
    private fun OpcionesFuerza() {
        val completed = mapOf("sq_0" to CompletedSet(id = "c0", weight = 120.0, reps = 6))
        ExerciseOptionsSheet(
            title = SQ.name,
            subtitle = exerciseOptionsSubtitle(SQ, completed, emptySet()),
            onDismiss = {},
            hazeState = HazeState(),
            thumbnail = { SessionExerciseThumb(SQ, dimension = 52.dp) },
        ) {
            ExerciseOptionsStrengthContent(
                onReplace = {}, onReorder = {}, onCreateSuperset = {}, onHistory = {}, onUltraFast = {}, onNotes = {},
                onTags = {}, onEdit = {}, onAddOther = {}, onSkip = {}, onRemove = {},
            )
        }
    }

    @Composable
    private fun OpcionesSuperserie() {
        ExerciseOptionsSheet(
            title = "Superserie",
            subtitle = supersetOptionsSubtitle(rounds = 3, members = 2),
            onDismiss = {},
            hazeState = HazeState(),
            thumbnail = { ExerciseOptionsIconThumb(KpknLineIcons.Link) },
        ) {
            ExerciseOptionsSupersetContent(
                members = listOf(SupersetMemberOption("pm", PM.name), SupersetMemberOption("press", PRESS.name)),
                onGo = {}, onRounds = {}, onAddFromSession = {}, onAddFromCatalog = {}, onOpenMember = {}, onDissolve = {},
            )
        }
    }

    @Composable
    private fun OpcionesCardio() {
        val cinta = Exercise(
            id = "run",
            name = "Cinta",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
            sets = listOf(ExerciseSet(id = "run-0")),
        )
        ExerciseOptionsSheet(
            title = cinta.name,
            subtitle = exerciseOptionsSubtitle(cinta, emptyMap(), emptySet()),
            onDismiss = {},
            hazeState = HazeState(),
            thumbnail = { SessionExerciseThumb(cinta, dimension = 52.dp) },
        ) {
            ExerciseOptionsCardioContent(onEdit = {}, onReplace = {}, onSkip = {}, onRemove = {})
        }
    }

    /** La sesión del prototipo C3: lo hecho y lo en curso arriba y fijos, una superserie como una sola fila y dos ejercicios por mover. */
    @Composable
    private fun ReorderSheet() {
        val mov = reorderExercise("mov", "Movilidad de cadera", sets = 3)
        val sentadilla = reorderExercise("sq4", "Sentadilla trasera", sets = 4)
        val rdl = reorderExercise("rdl", "Peso muerto rumano", sets = 3, group = "g1")
        val curl = reorderExercise("curl", "Curl femoral", sets = 3, group = "g1")
        val ext = reorderExercise("ext", "Extensión de cuádriceps", sets = 3)
        val talones = reorderExercise("tal", "Elevación de talones", sets = 3)
        val all = listOf(mov, sentadilla, rdl, curl, ext, talones)
        val session = Session(
            id = "pierna-a",
            name = "Pierna A",
            exercises = all,
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("rdl", "curl"), rounds = 3)),
        )
        val completed = (0..2).associate { "mov_$it" to CompletedSet(id = "m$it", weight = 0.0, reps = 10) } +
            mapOf("sq4_0" to CompletedSet(id = "s0", weight = 120.0, reps = 6))
        WorkoutReorderSheets(
            state = remember {
                WorkoutStructureSheetsState().apply {
                    showReorderSheet = true
                    reorderSheetExerciseIds = all.map { it.id }
                }
            },
            visibleExercises = all,
            originalExercisePartMap = all.associate { it.id to "Sesión principal" },
            onApplyReorder = { _, _, _ -> },
            session = session,
            completedSets = completed,
            omittedSetKeys = emptySet(),
            hazeState = HazeState(),
        )
    }

    private fun reorderExercise(id: String, name: String, sets: Int, group: String? = null) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.REPS,
        sets = (0 until sets).map { sampleSet("$id-$it", 100.0, 8) },
        supersetGroupRef = group,
        supersetId = group,
    )

    @Composable
    private fun SupersetSheet() {
        WorkoutSupersetCreatorSheet(
            state = WorkoutStructureSheetsState().apply {
                showWorkoutSupersetCreator = true
                workoutSupersetSelectedExerciseId = SQ.id
            },
            modeSession = SAMPLE_SESSION,
            renderedParts = listOf(SessionPart(id = "default", name = "Sesión principal", exercises = SAMPLE_EXERCISES)),
            visibleExercises = SAMPLE_EXERCISES,
            completedSets = emptyMap(),
            hasCatalogAction = false,
            onCreateSuperset = { _, _, _, _, _ -> },
        )
    }

    /** Dibuja [content] dentro de una caja de 360 × 880 dp con la letra al [fontScale] y la guarda como PNG; [afterShow] toca la escena antes de guardarla. */
    private fun draw(name: String, fontScale: Float, afterShow: (() -> Unit)? = null, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(880.dp)
                                .background(Color.Black)
                                .testTag(SHEET_TAG),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        afterShow?.invoke()
        rule.waitForIdle()
        save(name)
    }

    /** Recorta la hoja a la caja con la etiqueta de prueba y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(SHEET_TAG).fetchSemanticsNode().boundsInRoot
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
