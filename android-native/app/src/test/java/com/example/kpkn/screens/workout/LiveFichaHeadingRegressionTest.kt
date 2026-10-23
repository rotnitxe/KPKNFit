package com.example.kpkn.screens.workout

import android.app.Application

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.ExerciseHistorySummary
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailModel
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContext
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContent
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import com.example.kpkn.screens.sessioneditor.components.selectedCatalogImageVariantIndex
import com.example.kpkn.screens.sessioneditor.components.sessionExerciseDetailModel
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
        val screenDensity = setHistoryFicha(fontScale = 1.3f)
        expandDescriptionAndReturnToTop(screenDensity)
        val history = rule.onNodeWithText("Tu historial", useUnmergedTree = true)
        val fixedHeading = rule.onNode(
            hasText("Press Militar") and hasAnyAncestor(hasTestTag("exercise-ficha-fixed-heading")),
            useUnmergedTree = true,
        )

        val scrollable = rule.onNodeWithTag("exercise-ficha-scroll")
        // Cruza desde el primer estado con título fijo visible (scroll 280 dp) hasta la franja del encabezado.
        for (step in 1..14) {
            scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, screenDensity.density * 20f)
            }
        }
        rule.waitForIdle()
        fixedHeading.assertExists()
        history.assertIsDisplayed()
        val initialHistoryTop = history.fetchSemanticsNode().boundsInRoot.top
        var reachedHeadingBand = false
        var finalHistoryTop = initialHistoryTop
        for (step in 1..80) {
            scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, screenDensity.density * 4f)
            }
            rule.waitForIdle()
            // Un rectángulo vacío significa que el nodo salió del viewport; no puede contar como cruce correcto.
            history.assertIsDisplayed()
            val headingBounds = fixedHeading.fetchSemanticsNode().boundsInRoot
            val historyBounds = history.fetchSemanticsNode().boundsInRoot
            finalHistoryTop = historyBounds.top
            assertTrue(
                "En el paso $step el historial sigue visible bajo el título: heading=$headingBounds history=$historyBounds",
                historyBounds.width > 0f && historyBounds.height > 0f,
            )
            assertTrue(
                "En el paso $step el historial no debe cruzar el título: heading=$headingBounds history=$historyBounds",
                headingBounds.bottom <= historyBounds.top,
            )
            if (historyBounds.top - headingBounds.bottom <= 16f * screenDensity.density) {
                reachedHeadingBand = true
                break
            }
        }
        assertTrue("El scroll acercó el historial al título fijo", reachedHeadingBand)
        assertTrue(
            "El historial recorrió la ficha y sigue visible bajo el título fijo",
            finalHistoryTop < initialHistoryTop - 20f * screenDensity.density,
        )
        scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, screenDensity.density * 32f)
        }
        rule.waitForIdle()
        history.assertIsDisplayed()
        val headingAfterExtraScroll = fixedHeading.fetchSemanticsNode().boundsInRoot
        val historyAfterExtraScroll = history.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Tras 32 dp extra, el historial mantiene un área visible: $historyAfterExtraScroll",
            historyAfterExtraScroll.width > 0f && historyAfterExtraScroll.height > 0f,
        )
        assertTrue(
            "Tras 32 dp extra, el historial aún empieza debajo del título: " +
                "heading=$headingAfterExtraScroll history=$historyAfterExtraScroll",
            headingAfterExtraScroll.bottom <= historyAfterExtraScroll.top,
        )
        assertTrue(
            "Tras 32 dp extra, alguna parte del historial sigue en el viewport",
            historyAfterExtraScroll.bottom > headingAfterExtraScroll.bottom,
        )
        history.assertIsDisplayed()
    }

    private fun assertHistoryBelowHeading(fontScale: Float) {
        val screenDensity = setHistoryFicha(fontScale = fontScale)
        expandDescriptionAndReturnToTop(screenDensity)
        val scrollable = rule.onNodeWithTag("exercise-ficha-scroll")
        repeat(16) {
            scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, screenDensity.density * 20f)
            }
        }
        rule.waitForIdle()
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

    private fun setHistoryFicha(fontScale: Float): Density {
        val catalog = CatalogCompositionTestSupport.catalog
        val definition = catalog.families.asSequence()
            .flatMap { it.definitions.asSequence() }
            .first { it.canonicalName == "Press Militar" }
        val configuration = definition.configurations.first { it.id == definition.defaultConfigurationId }
        val exercise = Exercise(
            id = "press-militar-heading-regression",
            name = definition.canonicalName,
            catalogRevision = catalog.catalogRevision,
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            performanceProfileId = configuration.profile.performanceProfileId,
        )
        val legacyInfo = catalog.toLegacySelection(
            ExerciseSelectionV2(definition.id, configuration.id, catalog.catalogRevision),
        )
        val resolved = checkNotNull(resolveCatalogExerciseV2(exercise, catalog, legacyInfo))
        val model = sessionExerciseDetailModel(resolved).copy(
            context = ExerciseFichaContext.CATALOG,
            historySummary = ExerciseHistorySummary(bestSet = null, estimatedRmKg = null, lastDate = LocalDate.now()),
        )
        assertTrue("El fixture usa una imagen aprobada de Press Militar", model.imageVariants.isNotEmpty())
        assertNotNull(
            "La configuración real conserva una portada visible",
            selectedCatalogImageVariantIndex(model.imageVariants, model.selectedImplementation),
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
                    )
                }
            }
        }
        return screenDensity
    }

    private fun expandDescriptionAndReturnToTop(screenDensity: Density) {
        val scrollable = rule.onNodeWithTag("exercise-ficha-scroll")
        val readMore = rule.onNodeWithText("Leer más", useUnmergedTree = true)
        readMore.assertExists()
        readMore.performScrollTo()
        readMore.performClick()
        rule.onNodeWithText("Leer menos", useUnmergedTree = true).assertExists()
        scrollable.performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, -screenDensity.density * 10_000f)
        }
        rule.waitForIdle()
    }
}
