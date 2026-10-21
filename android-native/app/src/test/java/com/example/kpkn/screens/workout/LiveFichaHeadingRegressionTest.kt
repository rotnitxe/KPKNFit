package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailModel
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.kpkn.ui.theme.KPKNTheme

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
}
