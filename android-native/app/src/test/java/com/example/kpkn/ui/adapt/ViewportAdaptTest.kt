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

    @Test
    fun live_policy_identity_on_design_canvas() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 411f, heightDp = 891f)
        assertEquals(0f, policy.tNarrow, 0.01f)
        assertEquals(0f, policy.tShort, 0.01f)
        assertEquals(1f, policy.headerMul, 0.01f)
        assertEquals(1f, policy.cardWidthMul, 0.01f)
        assertEquals(1f, policy.cardHeightMul, 0.01f)
        assertEquals(0f, policy.cardPageFillX, 0.01f)
        assertEquals(LiveViewportPolicyMath.NUDGE_CANONICAL, policy.topNudgeFraction, 0.001f)
        assertEquals(0.80f, LiveViewportPolicyMath.headerScale(1f, policy.headerMul), 0.01f)
    }

    @Test
    fun live_policy_360_19_5_9_does_not_get_flip_boost() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 360f, heightDp = 780f)
        assertEquals(0f, policy.tNarrow, 0.05f)
        assertTrue(policy.headerMul < 1.05f)
        assertTrue(policy.cardWidthMul < 1.05f)
        assertEquals(1f, policy.cardHeightMul, 0.03f)
    }

    @Test
    fun live_policy_flip_inner_hits_narrow_cap() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 360f, heightDp = 880f)
        assertEquals(1f, policy.tNarrow, 0.05f)
        assertEquals(0f, policy.tShort, 0.01f)
        assertEquals(1.20f, policy.headerMul, 0.02f)
        assertEquals(1.303f, policy.cardWidthMul, 0.02f)
        assertEquals(1.728f, policy.cardHeightMul, 0.02f)
        assertEquals(1f, policy.cardPageFillX, 0.05f)
        assertEquals(LiveViewportPolicyMath.NUDGE_NARROW, policy.topNudgeFraction, 0.01f)
        val adapt = ViewportAdaptMath.compute(widthDp = 360f, heightDp = 880f)
        assertEquals(
            LiveViewportPolicyMath.HEADER_COMPACT_SCALE * adapt.uniformScale * 1.20f,
            LiveViewportPolicyMath.headerScale(adapt.uniformScale, policy.headerMul),
            0.02f,
        )
    }

    @Test
    fun live_policy_pixel8_is_soft_narrow_boost() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 411f, heightDp = 915f)
        assertTrue(policy.tNarrow > 0f)
        assertTrue(policy.tNarrow < 0.5f)
        assertTrue(policy.headerMul > 1f)
        assertTrue(policy.headerMul < 1.12f)
        assertEquals(0f, policy.tShort, 0.01f)
    }

    @Test
    fun live_policy_16_9_widens_cards_and_compacts_header() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 411f, heightDp = 731f)
        assertEquals(0f, policy.tNarrow, 0.01f)
        assertTrue(policy.tShort > 0.7f)
        assertTrue(policy.headerMul < 1f)
        assertTrue(policy.cardWidthMul > 1.05f)
        assertEquals(1f, policy.cardHeightMul, 0.01f)
        assertTrue(policy.topNudgeFraction < 0.08f)
    }

    @Test
    fun live_policy_square_cover_uses_width_not_height_boost() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 377f, heightDp = 392f)
        assertEquals(0f, policy.tNarrow, 0.01f)
        assertEquals(1f, policy.tShort, 0.01f)
        assertEquals(0.88f, policy.headerMul, 0.01f)
        assertEquals(1.257f, policy.cardWidthMul, 0.01f)
        assertEquals(1f, policy.cardHeightMul, 0.01f)
        assertEquals(0f, policy.cardPageFillX, 0.01f)
        assertEquals(LiveViewportPolicyMath.NUDGE_SHORT, policy.topNudgeFraction, 0.01f)
        assertEquals(LiveViewportPolicyMath.PEEK_FLOOR_SHORT, policy.minPeekFraction, 0.01f)
    }

    @Test
    fun live_policy_landscape_matches_short_cap() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 891f, heightDp = 411f)
        assertEquals(1f, policy.tShort, 0.01f)
        assertEquals(0f, policy.tNarrow, 0.01f)
        assertEquals(0.88f, policy.headerMul, 0.01f)
        assertEquals(1f, policy.cardHeightMul, 0.01f)
        assertTrue(policy.cardWidthMul > 1f)
    }

    @Test
    fun live_policy_fold_strip_caps_at_flip_boost() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 320f, heightDp = 900f)
        assertEquals(1f, policy.tNarrow, 0.01f)
        assertEquals(1.20f, policy.headerMul, 0.01f)
        assertEquals(1.728f, policy.cardHeightMul, 0.01f)
    }

    @Test
    fun live_policy_god_mode_is_identity() {
        val policy = LiveViewportPolicyMath.compute(
            widthDp = 360f,
            heightDp = 880f,
            godModeActive = true,
        )
        assertEquals(1f, policy.headerMul, 0.001f)
        assertEquals(1f, policy.cardWidthMul, 0.001f)
        assertEquals(1f, policy.cardHeightMul, 0.001f)
    }

    @Test
    fun live_policy_height_mul_clamps_boost_but_never_flattens_below_identity() {
        val tight = LiveViewportPolicyMath.compute(
            widthDp = 360f,
            heightDp = 880f,
            availablePagerHeightDp = 300f,
            cardScale = 1f,
            livePagerBaseHeightDp = 480f,
        )
        assertEquals(1f, tight.cardHeightMul, 0.02f)

        val roomy = LiveViewportPolicyMath.compute(
            widthDp = 360f,
            heightDp = 880f,
            availablePagerHeightDp = 2000f,
            cardScale = 1f,
            livePagerBaseHeightDp = 480f,
        )
        assertEquals(1.728f, roomy.cardHeightMul, 0.02f)
    }

    @Test
    fun clamp_does_not_cancel_extra_y_growth_on_design_phone() {
        val identityUnclamped = 1f
        val grow = 1.25f
        val clamped = LiveViewportPolicyMath.clampCardHeightMul(
            unclamped = identityUnclamped,
            livePagerBaseHeightDp = 480f,
            cardScale = (1.20f * 0.85f * 1.25f) * grow,
            topNudgeFraction = LiveViewportPolicyMath.NUDGE_CANONICAL,
            availablePagerHeightDp = 400f,
        )
        assertEquals(1f, clamped, 0.001f)
    }

    @Test
    fun live_peek_identity_matches_legacy_formula() {
        val cardScale = 1.20f * 0.85f
        val legacy = (0.22f * cardScale - (cardScale - 1f) / 2f).coerceIn(0.14f, 0.26f)
        val peek = LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 411f,
            cardScale = cardScale,
            shouldReflow = false,
            cardWidthMul = 1f,
            minPeekFraction = LiveViewportPolicyMath.PEEK_FLOOR,
        )
        assertEquals(legacy, peek, 0.001f)
    }

    @Test
    fun live_peek_width_mul_keeps_page_inside_viewport() {
        val policy = LiveViewportPolicyMath.compute(widthDp = 360f, heightDp = 880f)
        val cardScale = 1.20f * 0.85f * (360f / 411f)
        val peek = LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 360f,
            cardScale = cardScale,
            shouldReflow = false,
            cardWidthMul = policy.cardWidthMul,
            minPeekFraction = policy.minPeekFraction,
        )
        val pageFrac = 1f - 2f * peek
        assertTrue(pageFrac in 0f..1f)
        assertTrue(peek >= policy.minPeekFraction - 0.001f)
        assertTrue(peek <= LiveViewportPolicyMath.PEEK_CEILING)
        val unboosted = LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 360f,
            cardScale = cardScale,
            shouldReflow = false,
            cardWidthMul = 1f,
            minPeekFraction = policy.minPeekFraction,
        )
        assertTrue(peek < unboosted)
    }

    @Test
    fun live_peek_scale_below_one_does_not_hit_ceiling_on_flip() {
        val cardScale = 1.20f * 0.85f * (360f / 411f)
        val policy = LiveViewportPolicyMath.compute(widthDp = 360f, heightDp = 880f)
        val peek = LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 340f,
            cardScale = cardScale,
            shouldReflow = false,
            cardWidthMul = policy.cardWidthMul,
            minPeekFraction = policy.minPeekFraction,
        )
        assertTrue(peek < LiveViewportPolicyMath.PEEK_CEILING)
    }

    @Test
    fun live_pager_scale_x_fills_page_only_when_adapt_is_below_one() {
        assertEquals(1f, LiveViewportPolicyMath.livePagerScaleX(0.88f, 1f), 0.01f)
        assertEquals(0.88f, LiveViewportPolicyMath.livePagerScaleX(0.88f, 0f), 0.01f)
        assertEquals(1.02f, LiveViewportPolicyMath.livePagerScaleX(1.02f, 1f), 0.01f)
    }

    @Test
    fun live_peek_does_not_inflate_when_card_scale_is_below_one() {
        val peek = LiveViewportPolicyMath.livePagerPeekFraction(
            availableWidthDp = 360f,
            cardScale = 0.88f,
            shouldReflow = false,
            cardWidthMul = 1f,
            minPeekFraction = 0.12f,
        )
        assertEquals(0.22f, peek, 0.01f)
    }
}
