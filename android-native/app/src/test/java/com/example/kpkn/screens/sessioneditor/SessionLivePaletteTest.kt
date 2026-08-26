package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.graphics.luminance
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLivePaletteTest {

    @Test
    fun every_cover_preset_has_readable_onAccent_and_non_near_black_accent() {
        sessionBackgroundPresets.forEach { preset ->
            val bg = SessionBackground(type = SessionBackgroundType.COLOR, value = preset.id)
            val palette = resolveSessionLivePalette(bg)
            assertTrue(
                "accent too dark for ${preset.id}: luminance=${palette.accent.luminance()}",
                palette.accent.luminance() >= 0.18f,
            )
            val contrastOk = (palette.onAccent.luminance() - palette.accent.luminance()).let { kotlin.math.abs(it) } >= 0.25f
            assertTrue("weak contrast for ${preset.id}", contrastOk)
        }
    }

    @Test
    fun image_background_uses_stable_celeste_fallback() {
        val bg = SessionBackground(type = SessionBackgroundType.IMAGE, value = "file://cover.jpg")
        val palette = resolveSessionLivePalette(bg)
        assertTrue(palette.accent.luminance() >= 0.25f)
        assertTrue(contentOn(palette.accent).luminance() < 0.5f || palette.accent.luminance() < 0.45f)
    }

    @Test
    fun obsidian_solid_is_not_near_black_cta() {
        val bg = SessionBackground(type = SessionBackgroundType.COLOR, value = "solid://obsidian")
        val palette = resolveSessionLivePalette(bg)
        assertTrue(palette.accent.luminance() >= 0.25f)
        assertTrue(palette.onAccent.luminance() < 0.2f || palette.onAccent.luminance() > 0.8f)
    }

    @Test
    fun contentOn_picks_black_for_light_and_white_for_dark() {
        assertTrue(contentOn(androidx.compose.ui.graphics.Color.White).luminance() < 0.2f)
        assertTrue(contentOn(androidx.compose.ui.graphics.Color.Black).luminance() > 0.8f)
    }
}
