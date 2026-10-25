package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34],qualifiers = "w360dp-h900dp-xxhdpi",application = android.app.Application::class)
class ProgramSwapSheetTest {
    @get:Rule val rule = createComposeRule()
    private val option = SwapOption("target","Banca pausa","Barra","horizontal_push",SwapKind.EQUIVALENT)
    @Test fun choosing_a_variant_and_scope_prepares_a_review_and_does_not_apply_until_confirmed() {
        var scopeChosen: SwapScope? = null
        var confirmed = 0
        rule.setContent {
            var detail by remember { mutableStateOf<String?>(null) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
              ProgramSwapSheet("Banca",listOf(option),true,detail,false,
                onPreview = { id,scope,equipment ->
                    assertEquals("target",id); assertTrue(equipment); scopeChosen = scope; detail = "Cambio propuesto en 3 sesiones." },
                onConfirm = { confirmed++ },onEdit = { detail = null },onDismiss = {})
            }
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithTag("swap-option:target").performScrollTo().performClick()
        rule.onNodeWithText("Todo el programa").performScrollTo().performClick()
        rule.onNodeWithText("Revisar cambio").assertDoesNotExist()
        rule.onNodeWithTag("swap-equipment-confirmation").performScrollTo().performClick()
        rule.onNodeWithText("Revisar cambio").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(SwapScope.WHOLE_PROGRAM,scopeChosen); assertEquals(0,confirmed) }
        rule.onNodeWithText("Confirmar cambio").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,confirmed) }
    }
    @Test fun going_back_keeps_the_program_unchanged() {
        var dismissed = 0
        rule.setContent { ProgramSwapSheet("Banca",listOf(option),false,null,false,
            { _,_,_ -> fail("sin revisar") },{ fail("sin confirmar") },{},{ dismissed++ }) }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Volver").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,dismissed) }
    }
}
