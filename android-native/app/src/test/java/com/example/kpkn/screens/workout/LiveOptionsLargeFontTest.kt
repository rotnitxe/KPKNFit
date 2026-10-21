package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.workout.components.ExerciseOptionsCardioContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsSheet
import com.example.kpkn.screens.workout.components.ExerciseOptionsStrengthContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsSupersetContent
import com.example.kpkn.screens.workout.components.SeriesOptionsSheet
import com.example.kpkn.screens.workout.components.SupersetMemberOption
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object NoToursForLargeFont : TourSeenStore {
    override fun seenIds(): Set<String> = emptySet()
    override fun markSeen(tourId: String) = Unit
}

/**
 * Las hojas de la sesión en vivo con la letra al 130 % (LV_COMUN): ningún rótulo de una sola palabra se parte a mitad de palabra, ninguno
 * se corta con elipsis y nada se sale de la pantalla. Se mide en el texto ya maquetado, no en una captura.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveOptionsLargeFontTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    val host = remember { KpknOverlayHostController(tourSeen = NoToursForLargeFont) }
                    CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            if (host.entries.isNotEmpty()) {
                                Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    /**
     * El rótulo cabe entero: ninguna de sus líneas se corta con elipsis y no ocupa más de [maxLines] (una palabra partida a mitad pasa a
     * dos líneas). Se mira el texto ya maquetado; `hasVisualOverflow` no sirve aquí porque se enciende con cualquier texto de una caja ajustada.
     */
    private fun assertWhole(text: String, maxLines: Int = 1) {
        val layout = layoutOf(text)
        val ellipsized = (0 until layout.lineCount).any { line -> layout.isLineEllipsized(line) }
        assertFalse("«$text» no debe cortarse con elipsis", ellipsized)
        assertTrue("«$text» debe ocupar como mucho $maxLines línea(s) y ocupa ${layout.lineCount}", layout.lineCount <= maxLines)
    }

    private fun assertInsideScreen(text: String) {
        val bounds = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("«$text» queda dentro de la pantalla", bounds.left >= root.left - 0.5f && bounds.right <= root.right + 0.5f)
    }

    private fun topOf(text: String): Float = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    // ── C1 · Opciones de la serie ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun showSeriesOptions(fontScale: Float) = show(fontScale) {
        SeriesOptionsSheet(
            exercise = liveExercise("a", sets = 3),
            target = SeriesTypeTarget(exerciseId = "a", fromSetIdx = 1, toSetIdx = 1),
            completedSets = mapOf("a_0" to CompletedSet(id = "c", weight = 40.0, reps = 8)),
            omittedSetKeys = emptySet(),
            onDismiss = {},
            onApply = { _, _ -> },
            onEdit = {},
            onAddSet = {},
            onSkipSet = {},
            onDeleteSet = {},
        )
    }

    @Test
    fun theSeriesOptionsKeepEveryWordWholeAtOneHundredAndThirtyPercent() {
        showSeriesOptions(1.3f)

        listOf("Editar", "Añadir", "Saltar", "Borrar", "Normal", "Drop-set", "Rest-pause", "Solo S2", "Cancelar", "Aplicar").forEach { assertWhole(it) }
        assertWhole("Desde S2 hasta el final")
        listOf("Drop-set", "Rest-pause", "Desde S2 hasta el final").forEach { assertInsideScreen(it) }
    }

    @Test
    fun atNormalSizeTheThreeTypePillsShareOneLine() {
        showSeriesOptions(1f)

        assertEquals(topOf("Normal"), topOf("Drop-set"), 1f)
        assertEquals(topOf("Normal"), topOf("Rest-pause"), 1f)
    }

    // ── C2 · Opciones del ejercicio ───────────────────────────────────────────────────────────────────────────────────────────

    private fun showStrengthOptions(fontScale: Float) = show(fontScale) {
        ExerciseOptionsSheet(title = "Sentadilla trasera con barra alta", subtitle = "3 × 6 · RIR 2 · 1/3 hecha", onDismiss = {}) {
            ExerciseOptionsStrengthContent(
                onReplace = {}, onReorder = {}, onCreateSuperset = {}, onHistory = {}, onUltraFast = {}, onNotes = {},
                onTags = {}, onEdit = {}, onAddOther = {}, onSkip = {}, onRemove = {},
            )
        }
    }

    @Test
    fun theStrengthOptionsKeepEveryLabelWholeAtOneHundredAndThirtyPercent() {
        showStrengthOptions(1.3f)

        listOf("Reemplazar", "Reordenar", "Historial", "Ultrarrápido", "Notas", "Etiquetas", "Editar").forEach { assertWhole(it) }
        listOf("Crear superserie", "Agregar ejercicio").forEach { assertWhole(it, maxLines = 2) }
        assertWhole("Saltar ejercicio")
        assertWhole("Quitar de la sesión")
    }

    @Test
    fun withLargeLetterTheTilesGoTwoPerRowAndTheFooterPillsStack() {
        showStrengthOptions(1.3f)

        // Dos columnas: «Reordenar» va al lado de «Reemplazar» y «Crear superserie» baja a la fila siguiente.
        assertEquals(topOf("Reemplazar"), topOf("Reordenar"), 1f)
        assertTrue(topOf("Crear superserie") > topOf("Reemplazar"))
        // Las dos píldoras del pie, una sobre otra.
        assertTrue(topOf("Quitar de la sesión") > topOf("Saltar ejercicio"))
    }

    @Test
    fun atNormalSizeTheTilesGoThreePerRowAndTheFooterPillsShareALine() {
        showStrengthOptions(1f)

        assertEquals(topOf("Reemplazar"), topOf("Crear superserie"), 1f)
        assertEquals(topOf("Saltar ejercicio"), topOf("Quitar de la sesión"), 1f)
    }

    @Test
    fun theCardioAndSupersetOptionsAlsoKeepEveryLabelWhole() {
        show(1.3f) {
            ExerciseOptionsSheet(title = "Cinta", subtitle = "Espacio cardio · Estático · 20 min", onDismiss = {}) {
                ExerciseOptionsCardioContent(onEdit = {}, onReplace = {}, onSkip = {}, onRemove = {})
            }
        }
        assertWhole("Reemplazar")
        assertWhole("Quitar de la sesión")
        // «20 min» no se separa: la unidad viaja con su cifra (si el subtítulo pasa a dos líneas, «min» no queda solo en la segunda).
        val subtitle = layoutOf("Espacio cardio · Estático · 20 min")
        val text = subtitle.layoutInput.text.text
        val lastLine = text.substring(subtitle.getLineStart(subtitle.lineCount - 1)).trim()
        assertTrue("«20 min» viaja entero: «$lastLine»", !lastLine.contains("min") || lastLine.contains("20"))
    }

    @Test
    fun theSupersetOptionsKeepTheCatalogLabelWhole() {
        show(1.3f) {
            ExerciseOptionsSheet(title = "Superserie", subtitle = "3 rondas · 2 ejercicios", onDismiss = {}) {
                ExerciseOptionsSupersetContent(
                    members = listOf(SupersetMemberOption("m1", "Peso muerto convencional"), SupersetMemberOption("m2", "Press de banca")),
                    onGo = {}, onRounds = {}, onAddFromSession = {}, onAddFromCatalog = {}, onOpenMember = {}, onDissolve = {},
                )
            }
        }

        assertWhole("Agregar del catálogo", maxLines = 2)
        assertWhole("Ir a la superserie", maxLines = 2)
        assertWhole("Disolver superserie")
    }

    // ── C3 · Orden de la sesión ───────────────────────────────────────────────────────────────────────────────────────────────

    private val reorderExercises: List<Exercise> = listOf("a", "b", "c", "d").map { liveExercise(it, sets = 2, name = "Ejercicio ${it.uppercase()}") }

    @Test
    fun theBatchActionsOfTheOrderSheetAreAllVisibleAtOneHundredAndThirtyPercent() {
        val state = WorkoutStructureSheetsState().apply {
            showReorderSheet = true
            reorderSheetExerciseIds = reorderExercises.map { it.id }
        }
        show(1.3f) {
            WorkoutReorderSheets(
                state = state,
                visibleExercises = reorderExercises,
                originalExercisePartMap = reorderExercises.associate { it.id to "Sesión principal" },
                onApplyReorder = { _, _, _ -> },
                session = Session(id = "s", name = "Sesión", exercises = reorderExercises),
                completedSets = emptyMap(),
                omittedSetKeys = emptySet(),
                onCreateSuperset = {},
                onSkipExercises = {},
            )
        }
        rule.onNodeWithText("Seleccionar").performClick()
        rule.waitForIdle()

        listOf("Superserie", "Omitir", "Quitar", "Ultrarrápido").forEach {
            assertWhole(it)
            assertInsideScreen(it)
        }
        // De dos en dos: «Quitar» y «Ultrarrápido» en la segunda fila.
        assertEquals(topOf("Superserie"), topOf("Omitir"), 1f)
        assertEquals(topOf("Quitar"), topOf("Ultrarrápido"), 1f)
        assertTrue(topOf("Quitar") > topOf("Superserie"))
    }
}
