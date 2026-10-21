package com.example.kpkn.domain.biomechanics.bar

/**
 * Publication provenance, not an image classifier. Hough/NCC/temporal persistence do not
 * establish that a circle is the intended plate. Automatic seeds remain review proposals.
 * Confirmation records the user's selection, not continued identity or physical calibration.
 * Consumers must still require their own coverage/visibility/geometry gates for a selected seed.
 * Legacy tracks without selection provenance abstain until the user selects a seed.
 */
object PlatePublicationPolicy {
    fun canUsePlate(bar: BarTrack): Boolean = bar.seed?.userConfirmed == true &&
        bar.trackingContinuity == PlateTrackingContinuity.UNINTERRUPTED && bar.continuityBreakUs == null

    /** Diameter changes do not change the selected pixel object. A moved/resized seed requires tracking again. */
    fun matchesSelection(original: PlateSeed?, requested: PlateSeed): Boolean =
        original != null && requested.cx.isFinite() && requested.cy.isFinite() &&
            requested.r.isFinite() && requested.r > 0f &&
            original.tUs == requested.tUs && original.cx == requested.cx &&
            original.cy == requested.cy && original.r == requested.r
}
