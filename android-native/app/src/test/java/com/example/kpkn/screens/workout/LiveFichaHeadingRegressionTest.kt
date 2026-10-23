package com.example.kpkn.screens.workout

import android.app.Application

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.kpkn.domain.exercises.ExerciseHistorySummary
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailModel
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContent
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
/** B1 was the subtitle escaping the title's fade, despite the already-correct pure crossover. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveFichaHeadingRegressionTest {
    @get:Rule val rule = createComposeRule()
    @Test fun subtitleAndContextBelongToTheSameFadingBlockBeforeTheFixedHeadingEnters() {
        val model = CatalogDetailModel(name = "Press de Banca Plano", caption = "Pectorales · Barra · Empuje horizontal",
            contextLine = "Pierna A · Serie 1", description = "Descripción de la ficha.\n".repeat(60),
            imageVariants = emptyList(), selectedImplementation = null, axes = emptyList(), configurationLine = null,
            configurationIncomplete = false, muscleInfo = null, joints = emptyList(), patternId = null, isSelected = false, canSelect = false)
        rule.setContent { KPKNTheme { ExerciseFichaContent(model, onDismiss = {}, onToggleSelection = {}, onSelectOption = { _, _ -> }, onImplementationSettled = {}) } }
        for (text in listOf(model.name, checkNotNull(model.caption), checkNotNull(model.contextLine))) {
            rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("exercise-ficha-large-heading")), true).assertExists()
        }
        rule.onNodeWithText("Leer más").performClick()
        rule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000f) }
        rule.onNodeWithTag("exercise-ficha-fixed-heading", true).assertExists()
        for (scroll in 230..350) {
            val effects = exerciseFichaEffects(scroll.toFloat())
            assertTrue("El bloque completo desaparece antes del título fijo", effects.titleAlpha == 0f || effects.headerAlpha == 0f)
        }
    }

    @Test
    fun fixedHeadingReservesHistoryViewportAt880DpAnd100Percent() = assertHistoryBelowHeading(fontScale = 1f)

    @Test
    fun fixedHeadingReservesHistoryViewportAt880DpAnd130Percent() = assertHistoryBelowHeading(fontScale = 1.3f)

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
    fun fixedHeadingReservesHistoryViewportAt740DpAnd100Percent() = assertHistoryBelowHeading(fontScale = 1f)

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
    fun fixedHeadingReservesHistoryViewportAt740DpAnd130Percent() = assertHistoryBelowHeading(fontScale = 1.3f)

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
    fun fixedHeadingAndHistoryStaySeparateDuringScrollAt740DpAnd130Percent() {
        val screenDensity = setHistoryFicha(fontScale = 1.3f, initialScrollDp = 270)
        val history = rule.onNodeWithText("Tu historial", useUnmergedTree = true)
        val fixedHeading = rule.onNode(
            hasText("Press Militar") and hasAnyAncestor(hasTestTag("exercise-ficha-fixed-heading")),
            useUnmergedTree = true,
        )

        for (step in 1..4) {
            rule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, screenDensity.density * 10f)
            }
            rule.waitForIdle()
            val headingBounds = fixedHeading.fetchSemanticsNode().boundsInRoot
            val historyBounds = history.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "En el paso de scroll $step el historial no debe cruzar el título: heading=$headingBounds history=$historyBounds",
                headingBounds.bottom <= historyBounds.top,
            )
        }
    }

    private fun assertHistoryBelowHeading(fontScale: Float) {
        val screenDensity = setHistoryFicha(fontScale = fontScale, initialScrollDp = 310)
        val heading = rule.onNodeWithTag("exercise-ficha-fixed-heading", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val history = rule.onNodeWithText("Tu historial", useUnmergedTree = true)
        history.assertIsDisplayed()
        val historyBounds = history.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "El texto del historial debe empezar después del encabezado fijo: heading=$heading history=$historyBounds",
            heading.bottom <= historyBounds.top,
        )

        val minTouchTarget = 48f * screenDensity.density
        for (tag in listOf("exercise-ficha-close", "exercise-ficha-select")) {
            val targetBounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag conserva un área táctil de al menos 48 × 48 dp: $targetBounds",
                targetBounds.width >= minTouchTarget && targetBounds.height >= minTouchTarget,
            )
        }
    }

    private fun setHistoryFicha(fontScale: Float, initialScrollDp: Int): Density {
        val model = CatalogDetailModel(
            name = "Press Militar",
            caption = "Deltoides · Barra · Empuje vertical",
            description = "Descripción de la ficha.\n".repeat(60),
            imageVariants = emptyList(),
            selectedImplementation = null,
            axes = emptyList(),
            configurationLine = null,
            configurationIncomplete = false,
            muscleInfo = null,
            joints = emptyList(),
            patternId = null,
            isSelected = false,
            canSelect = false,
            historySummary = ExerciseHistorySummary(bestSet = null, estimatedRmKg = null, lastDate = LocalDate.now()),
        )
        val screenDensity = rule.density
        val scaledDensity = Density(screenDensity.density, fontScale)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                KPKNTheme {
                    ExerciseFichaContent(
                        model,
                        onDismiss = {},
                        onToggleSelection = {},
                        onSelectOption = { _, _ -> },
                        onImplementationSettled = {},
                        initialScrollDp = initialScrollDp,
                    )
                }
            }
        }
        return screenDensity
    }
}
