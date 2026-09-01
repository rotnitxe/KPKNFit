package com.example.kpkn.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeRingsLayoutTest {

    @Test
    fun host_height_keeps_radius_width_bound() {
        val width = 336f
        val bloom = 8f
        val height = augeRingsHostHeightDp(width, bloom)
        val layout = augeRingsLayout(width, height, bloom)
        assertEquals(width / AugeRingsRadiusWidthDivisor, layout.radius, 0.01f)
    }

    @Test
    fun cluster_plus_bloom_fits_inside_host() {
        val width = 336f
        val bloom = 8f
        val height = augeRingsHostHeightDp(width, bloom)
        assertTrue(augeRingsClusterFits(width, height, bloom))
    }

    @Test
    fun unfitted_196_host_would_clip_width_bound_rings() {
        val width = 336f
        val height = 196f
        val radius = width / AugeRingsRadiusWidthDivisor
        val dy = radius * AugeRingsStaggerDy
        val halfBloom = 4f
        val top = height / 2f - dy - radius - halfBloom
        val bottom = height / 2f + dy + radius + halfBloom
        assertTrue(top < 0f)
        assertTrue(bottom > height)
    }

    @Test
    fun growing_host_does_not_shrink_rings_below_width_radius() {
        val width = 360f
        val bloom = 8f
        val tight = augeRingsLayout(width, 196f, bloom)
        val fitted = augeRingsLayout(width, augeRingsHostHeightDp(width, bloom), bloom)
        assertTrue(fitted.radius + 0.01f >= tight.radius)
        assertEquals(width / AugeRingsRadiusWidthDivisor, fitted.radius, 0.01f)
    }
}
