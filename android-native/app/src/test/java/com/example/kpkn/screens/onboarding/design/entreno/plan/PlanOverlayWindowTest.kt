package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La ventana común de los overlays: la rama del desenfoque la decide el sistema, y el arnés de depuración (extra `blur=on|off`)
 * puede forzar cada una para verlas en un teléfono que tiene el desenfoque desactivado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanOverlayWindowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun blurSeenWith(override: Boolean?): Boolean? {
        var seen: Boolean? = null
        rule.setContent {
            CompositionLocalProvider(LocalOverlayBlurOverride provides override) {
                BlurOverlayDialog(onDismissRequest = {}, dismissOnBack = false, shown = { 1f }) { blur -> seen = blur }
            }
        }
        rule.waitForIdle()
        return seen
    }

    @Test
    fun theHarnessCanForceTheBlurBranch() {
        assertEquals(true, blurSeenWith(true))
    }

    @Test
    fun theHarnessCanForceTheOpaqueVeilBranch() {
        assertEquals(false, blurSeenWith(false))
    }

    @Test
    fun withoutAnOverrideTheSystemDecidesAndRobolectricHasNoWindowBlur() {
        assertEquals(false, blurSeenWith(null))
    }
}
