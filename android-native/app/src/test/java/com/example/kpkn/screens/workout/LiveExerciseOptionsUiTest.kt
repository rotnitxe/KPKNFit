package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.workout.components.ExerciseOptionsCardioContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsSheet
import com.example.kpkn.screens.workout.components.ExerciseOptionsStrengthContent
import com.example.kpkn.screens.workout.components.ExerciseOptionsSupersetContent
import com.example.kpkn.screens.workout.components.SupersetMemberOption
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object NoToursForExerciseSheets : TourSeenStore {
    override fun seenIds(): Set<String> = emptySet()
    override fun markSeen(tourId: String) = Unit
}

/**
 * Las opciones del ejercicio (LV11, A2; prototipo C2) con las piezas reales: fuerza, superserie y cardio; cada opción llama a SU
 * función y «Quitar de la sesión» se apaga si es el único ejercicio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveExerciseOptionsUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                val host = remember { KpknOverlayHostController(tourSeen = NoToursForExerciseSheets) }
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
        rule.waitForIdle()
    }

    private fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    // ── C2 · Opciones del ejercicio ────────────────────────────────────────────────────────────────────────────────────────────

    private class ExerciseCalls {
        val taps = mutableListOf<String>()
        fun tap(name: String): () -> Unit = { taps += name }
    }

    @Test
    fun everyOptionOfAStrengthExerciseCallsItsOwnFunction() {
        val calls = ExerciseCalls()
        show {
            ExerciseOptionsSheet(title = "Sentadilla trasera", subtitle = "4 × 4–6 · 1/4 hecha", onDismiss = {}) {
                ExerciseOptionsStrengthContent(
                    onReplace = calls.tap("Reemplazar"),
                    onReorder = calls.tap("Orden"),
                    onCreateSuperset = calls.tap("Crear superserie"),
                    onHistory = calls.tap("Historial"),
                    onUltraFast = calls.tap("Aplicar técnica"),
                    onNotes = calls.tap("Notas"),
                    onTags = calls.tap("Etiquetas"),
                    onEdit = calls.tap("Series"),
                    onAddOther = calls.tap("Agregar ejercicio"),
                    onSkip = calls.tap("Saltar ejercicio"),
                    onRemove = calls.tap("Quitar de la sesión"),
                )
            }
        }
        val labels = listOf(
            "Etiquetas", "Historial", "Reemplazar", "Series", "Orden", "Crear superserie", "Aplicar técnica", "Notas", "Agregar ejercicio",
            "Saltar ejercicio", "Quitar de la sesión",
        )

        labels.forEach { click(it) }

        assertEquals("cada opción hace lo suyo, en el orden pulsado", labels, calls.taps)
        rule.onNodeWithText("Sentadilla trasera").assertExists()
        rule.onNodeWithText("4 × 4–6 · 1/4 hecha").assertExists()
    }

    @Test
    fun removingFromTheSessionIsDisabledWhenItIsTheOnlyExercise() {
        show {
            ExerciseOptionsSheet(title = "Única", subtitle = null, onDismiss = {}) {
                ExerciseOptionsStrengthContent(
                    onReplace = {}, onReorder = {}, onCreateSuperset = {}, onHistory = {}, onUltraFast = {}, onNotes = {},
                    onTags = {}, onEdit = {}, onAddOther = {}, onSkip = {}, onRemove = {}, canRemove = false,
                )
            }
        }

        rule.onNodeWithText("Quitar de la sesión").assertIsNotEnabled()
        rule.onNodeWithText("Saltar ejercicio").assertIsEnabled()
    }

    @Test
    fun everyOptionOfASupersetCallsItsOwnFunctionAndEachMemberOpensItsOwnMenu() {
        val calls = ExerciseCalls()
        val opened = mutableListOf<String>()
        show {
            ExerciseOptionsSheet(title = "Superserie", subtitle = "3 rondas · 2 ejercicios", onDismiss = {}) {
                ExerciseOptionsSupersetContent(
                    members = listOf(SupersetMemberOption("m1", "Peso muerto rumano"), SupersetMemberOption("m2", "Curl femoral")),
                    onGo = calls.tap("Ir a la superserie"),
                    onRounds = calls.tap("Rondas"),
                    onAddFromSession = calls.tap("Agregar de sesión"),
                    onAddFromCatalog = calls.tap("Agregar del catálogo"),
                    onOpenMember = { opened += it },
                    onDissolve = calls.tap("Disolver superserie"),
                )
            }
        }
        val labels = listOf("Ir a la superserie", "Rondas", "Agregar de sesión", "Agregar del catálogo", "Disolver superserie")

        labels.forEach { click(it) }
        click("Curl femoral")

        assertEquals(labels, calls.taps)
        assertEquals(listOf("m2"), opened)
        rule.onNodeWithText("Peso muerto rumano").assertExists()
    }

    @Test
    fun everyOptionOfACardioExerciseCallsItsOwnFunction() {
        val calls = ExerciseCalls()
        show {
            ExerciseOptionsSheet(title = "Cinta", subtitle = "Espacio cardio · Estático · 20 min", onDismiss = {}) {
                ExerciseOptionsCardioContent(
                    onEdit = calls.tap("Editar cardio"),
                    onReplace = calls.tap("Reemplazar"),
                    onSkip = calls.tap("Saltar ejercicio"),
                    onRemove = calls.tap("Quitar de la sesión"),
                )
            }
        }
        val labels = listOf("Editar cardio", "Reemplazar", "Saltar ejercicio", "Quitar de la sesión")

        labels.forEach { click(it) }

        assertEquals(labels, calls.taps)
    }
}
