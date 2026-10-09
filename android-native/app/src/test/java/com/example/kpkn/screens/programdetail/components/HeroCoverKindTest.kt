package com.example.kpkn.screens.programdetail.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroCoverKindTest {

    @Test
    fun kpknCoverWithBundledFileIsTheBundledCover() {
        assertEquals(
            HeroCoverKind.BUNDLED,
            heroCoverKind("kpkn-cover://native:strength-foundation-v2", hasBundledCover = true),
        )
    }

    @Test
    fun kpknCoverWithoutFileFallsBackToTheGradient() {
        assertEquals(
            HeroCoverKind.GRADIENT,
            heroCoverKind("kpkn-cover://native:strength-foundation-v2", hasBundledCover = false),
        )
        assertEquals(HeroCoverKind.GRADIENT, heroCoverKind("kpkn-cover://", hasBundledCover = false))
    }

    @Test
    fun gradientBlankAndMissingValuesAreGradients() {
        assertEquals(HeroCoverKind.GRADIENT, heroCoverKind("gradient://lagoon", hasBundledCover = false))
        assertEquals(HeroCoverKind.GRADIENT, heroCoverKind("", hasBundledCover = false))
        assertEquals(HeroCoverKind.GRADIENT, heroCoverKind(null, hasBundledCover = false))
    }

    @Test
    fun otherValuesAreRemotePhotosLoadedByCoil() {
        assertEquals(HeroCoverKind.REMOTE, heroCoverKind("https://example.com/cover.jpg", hasBundledCover = false))
        assertEquals(
            HeroCoverKind.REMOTE,
            heroCoverKind("content://media/external/images/media/42", hasBundledCover = false),
        )
        assertEquals(
            HeroCoverKind.REMOTE,
            heroCoverKind("file:///storage/emulated/0/Pictures/cover.png", hasBundledCover = false),
        )
    }
}
