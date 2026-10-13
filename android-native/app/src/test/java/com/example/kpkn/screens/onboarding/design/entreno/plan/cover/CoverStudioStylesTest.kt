package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los diez estilos del motor pintan sin errores, en cartel (3:4) y en banda, con movimiento y sin él, en las tres paletas y con los
 * motivos de su rango (los hitos, las formas del grabado y las cifras de NUMERAL).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoverStudioStylesTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyStylePaintsPosterAndBannerWithMotionAndWithout() {
        val palettes = CoverPaletteKind.entries.size
        rule.setContent {
            Column {
                for (style in CoverStudioStyle.entries) {
                    for (palette in CoverPaletteKind.entries) {
                        val k = style.ordinal * palettes + palette.ordinal
                        val identity = CoverIdentity(
                            style = style,
                            palette = palette,
                            primary = SymbolPalette.musculo,
                            secondary = SymbolPalette.mente,
                            motif = motifFor(style, k),
                            seed = stableHash("prueba-$k"),
                        )
                        CoverStudioArt(
                            identity,
                            Modifier.size(120.dp, 160.dp).testTag("poster-$k"),
                            animate = k % 2 == 0,
                            reduced = false,
                        )
                        CoverStudioArt(
                            identity,
                            Modifier.size(200.dp, 90.dp).testTag("banner-$k"),
                            animate = k % 2 == 1,
                            reduced = true,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        for (k in 0 until CoverStudioStyle.entries.size * palettes) {
            rule.onNodeWithTag("poster-$k").assertExists()
            rule.onNodeWithTag("banner-$k").assertExists()
        }
    }
}
