package com.example.kpkn.domain.cardio

data class CardioPaceBands(
    val easySecondsPerKm: Int,
    val tempoSecondsPerKm: Int,
    val fiveKSecondsPerKm: Int,
)

/** Bandas NRC-lite a partir de un ritmo ancla (mejor ritmo con distancia ≥ 1 km). */
object CardioPaceBandEngine {
    fun fromAnchorPace(bestPaceSecondsPerKm: Int): CardioPaceBands {
        val fiveK = bestPaceSecondsPerKm.coerceAtLeast(150)
        return CardioPaceBands(
            easySecondsPerKm = (fiveK * 1.25).toInt().coerceAtLeast(fiveK + 20),
            tempoSecondsPerKm = (fiveK * 1.08).toInt().coerceAtLeast(fiveK + 5),
            fiveKSecondsPerKm = fiveK,
        )
    }
}
