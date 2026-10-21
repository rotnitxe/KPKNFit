package com.example.kpkn.domain.biomechanics.metrics

/**
 * Avisos del análisis (§3.5): hechos de la traza, de la vista o del conteo que bajan la confianza o explican por qué
 * falta una cifra. Se guardan por nombre en el informe: no reordenar ni renombrar.
 */
enum class AnalysisFlag {
    /** La 2.ª pose aparece en más del 10 % de los fotogramas con pose: puede que se haya seguido a otra persona. */
    MULTI_PERSON,

    /** El segmentador dio la vuelta a la señal (el reposo estaba arriba): el arranque del perfil no cuadraba con el vídeo. */
    POLARITY_FLIPPED,

    /** Ejercicio unilateral: el lado que trabaja es el lejano a la cámara (visibilidad débil). Confianza máx. MEDIA. */
    FAR_SIDE_WORKING,

    /** Traza v1 (ML Kit a ~10 Hz, sin aspecto fiable): solo sirve para contar. */
    LEGACY_TRACK,

    /** Cadencia variable (VFR): P95/P5 de ΔPTS > 1,3. */
    VFR,

    /** fs < 20 Hz: sin fases ni velocidad. */
    LOW_FPS,

    /** Vista oblicua (ψ entre 25° y 65°). */
    OBLIQUE_VIEW,

    /** De espaldas: no se ve la cara. */
    BACK_VIEW,

    /** Disco visto como elipse (axisRatio < 0,85): la cámara no es perpendicular a la barra y la escala por disco se corrige (VT3b). */
    PLATE_ELLIPSE,

    /** El disco encontrado se descartó (no cuadra con el atleta). */
    PLATE_REJECTED,

    /** Paralaje del disco suprimido por no poder corregirlo (VT3b). */
    PARALLAX_SUPPRESSED,

    /** Perfil por lado con solo el lado cercano válido: no se sabe qué brazo o pierna trabaja. Confianza máx. MEDIA. */
    SIDE_UNKNOWN,

    /** Se corrigió la inclinación del móvil (|roll| > 2°). */
    ROLL_CORRECTED,

    /** El vídeo es HDR (la luma del disco puede variar). */
    HDR_SOURCE,

    /**
     * La escala del disco no cuadra con el atleta (VT6a, §8 punto 6): el muslo medido en la imagen con la escala del disco, llevada al
     * plano del atleta (× Z_s/Z_p), difiere más de un 25 % del muslo de los puntos «world». Puede ser un diámetro mal declarado o
     * un disco mal seguido. Es solo un aviso: la escala se sigue usando.
     */
    PLATE_SCALE_MISMATCH,

    /**
     * El vídeo se decodificó por el respaldo (`MediaMetadataRetriever`, VT4c): más lento y con tiempos que salen del índice, no del
     * contenedor. El pipeline limita la confianza del conteo a MEDIA. Lo añade el data layer, no el motor.
     */
    DECODE_FALLBACK,
}
