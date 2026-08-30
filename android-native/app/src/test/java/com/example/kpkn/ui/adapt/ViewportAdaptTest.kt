package com.example.kpkn.ui.adapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportAdaptTest {

    @Test
    fun reference_phone_is_identity_scale() {
        val adapt = ViewportAdaptMath.compute(
            widthDp = ViewportAdaptMath.REF_WIDTH_DP,
            heightDp = ViewportAdaptMath.REF_HEIGHT_DP,
        )
        assertEquals(1f, adapt.uniformScale, 0.01f)
        assertFalse(adapt.shouldReflow)
        assertEquals(ViewportDensityClass.Comfortable, adapt.densityClass)
    }

    @Test
    fun flip_inner_scales_from_width_and_does_not_reflow() {
        val adapt = ViewportAdaptMath.compute(widthDp = 360f, heightDp = 900f)
        val expected = 360f / ViewportAdaptMath.REF_WIDTH_DP
        assertEquals(expected, adapt.uniformScale, 0.01f)
        assertTrue(adapt.uniformScale < 1f)
        assertTrue(adapt.uniformScale > ViewportAdaptMath.MIN_SCALE)
        assertFalse(adapt.shouldReflow)
        assertEquals(ViewportDensityClass.Compact, adapt.densityClass)
    }

    @Test
    fun flip_cover_hits_floor_and_reflows() {
        val adapt = ViewportAdaptMath.compute(widthDp = 280f, heightDp = 520f)
        assertEquals(ViewportAdaptMath.MIN_SCALE, adapt.uniformScale, 0.01f)
        assertTrue(adapt.shouldReflow)
        assertEquals(ViewportDensityClass.Compact, adapt.densityClass)
    }

    @Test
    fun short_phone_scales_from_height() {
        val adapt = ViewportAdaptMath.compute(widthDp = 411f, heightDp = 640f)
        val expected = (640f / ViewportAdaptMath.REF_HEIGHT_DP)
            .coerceIn(ViewportAdaptMath.MIN_SCALE, ViewportAdaptMath.MAX_SCALE)
        assertEquals(expected, adapt.uniformScale, 0.01f)
        assertTrue(adapt.shouldReflow)
    }

    @Test
    fun tablet_grows_but_stays_under_ceiling() {
        val adapt = ViewportAdaptMath.compute(widthDp = 600f, heightDp = 960f)
        val raw = minOf(600f / 411f, 960f / 891f)
        assertEquals(raw.coerceIn(0.72f, 1.12f), adapt.uniformScale, 0.01f)
        assertTrue(adapt.uniformScale > 1f)
        assertTrue(adapt.uniformScale <= ViewportAdaptMath.MAX_SCALE)
        assertFalse(adapt.shouldReflow)
        assertEquals(ViewportDensityClass.Comfortable, adapt.densityClass)
    }

    @Test
    fun scale_never_exceeds_ceiling_on_huge_canvas() {
        val adapt = ViewportAdaptMath.compute(widthDp = 1200f, heightDp = 2000f)
        assertEquals(ViewportAdaptMath.MAX_SCALE, adapt.uniformScale, 0.01f)
        assertFalse(adapt.shouldReflow)
    }

    @Test
    fun live_pager_refs_match_card_canvas() {
        val flipInner = ViewportAdaptMath.compute(
            widthDp = 360f,
            heightDp = 600f,
            refWidthDp = ViewportAdaptMath.LIVE_PAGER_REF_WIDTH_DP,
            refHeightDp = ViewportAdaptMath.LIVE_PAGER_REF_HEIGHT_DP,
        )
        assertEquals(360f / 411f, flipInner.uniformScale, 0.01f)
        assertFalse(flipInner.shouldReflow)

        val shortHole = ViewportAdaptMath.compute(
            widthDp = 411f,
            heightDp = 300f,
            refWidthDp = ViewportAdaptMath.LIVE_PAGER_REF_WIDTH_DP,
            refHeightDp = ViewportAdaptMath.LIVE_PAGER_REF_HEIGHT_DP,
        )
        assertEquals(ViewportAdaptMath.MIN_SCALE, shortHole.uniformScale, 0.01f)
        assertTrue(shortHole.shouldReflow)
    }

    @Test
    fun width_and_height_contribute_the_same_factor() {
        val fromWidth = ViewportAdaptMath.compute(widthDp = 328.8f, heightDp = 891f)
        val fromHeight = ViewportAdaptMath.compute(widthDp = 411f, heightDp = 712.8f)
        assertEquals(0.80f, fromWidth.uniformScale, 0.01f)
        assertEquals(0.80f, fromHeight.uniformScale, 0.01f)
    }
}
