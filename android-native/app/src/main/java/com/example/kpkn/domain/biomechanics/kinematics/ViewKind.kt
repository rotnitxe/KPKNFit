package com.example.kpkn.domain.biomechanics.kinematics

/** Vista del atleta respecto de la cámara (§4, punto 12). */
enum class ViewKind {
    /** ψ ≥ 65°: de lado; los segmentos sagitales se miden con error ≤ 3°. */
    SIDE,

    /** ψ ≤ 25°: de frente. */
    FRONT,

    /** ψ entre 25° y 65°. */
    OBLIQUE,

    /** De espaldas: ni la nariz ni las orejas se ven. */
    BACK,

    /** Tumbado (banca, suelo): |τ| > 60° en más de la mitad de los fotogramas. */
    LYING,

    /** No hay datos suficientes para decidir. */
    UNKNOWN,
}
