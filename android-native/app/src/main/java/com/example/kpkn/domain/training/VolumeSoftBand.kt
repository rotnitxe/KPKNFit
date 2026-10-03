package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import kotlin.math.roundToLong

/**
 * B-02 (cierre 2026-10-02): holgura semanal de los glúteos sobre su límite.
 *
 * El límite normal sigue siendo el MRV global de [VolumeLandmarks] (16 series por
 * semana) y NO se modifica. La banda añade 1,5 series (≈ 9 %), así que el techo
 * blando es 17,5:
 *
 * - hasta 16 series: volumen normal;
 * - de 16 a 17,5 series: «volumen alto», permitido con aviso;
 * - más de 17,5 series: se rechaza igual que antes.
 *
 * Solo aplica a los planes propios de KPKN y solo a glúteos. El ajustador sigue
 * apuntando a 16 y usa la banda como último recurso, cuando ya no le queda ninguna
 * palanca; nunca la usa para dejar de recortar antes. Dominio puro: sin `android.*`.
 */
const val GLUTES_SOFT_BAND: Double = 1.5

/** Zona de un volumen semanal respecto a su límite y a su techo blando. */
enum class VolumeBand {
    /** Hasta el límite recomendado: volumen normal. */
    WITHIN_LIMIT,

    /** Por encima del límite y hasta el techo blando: «volumen alto», permitido con aviso. */
    HIGH_VOLUME,

    /** Por encima del techo blando: se rechaza. */
    OVER_CEILING,
}

/**
 * Un músculo cuyo volumen semanal quedó en la banda de «volumen alto» de un plan entregado.
 * [weeklySets] es el máximo semanal del plan; [recommendedSets] el límite normal y
 * [ceilingSets] el techo blando.
 */
data class HighVolumeNotice(
    val muscle: String,
    val weeklySets: Double,
    val recommendedSets: Int,
    val ceilingSets: Double,
) {
    /** Nota en lenguaje llano: «Glúteos 17 series (recomendado 16, tolerancia hasta 17,5)». */
    val message: String
        get() = "$muscle ${VolumeSoftBand.formatSets(weeklySets)} series " +
            "(recomendado $recommendedSets, tolerancia hasta ${VolumeSoftBand.formatSets(ceilingSets)})"
}

object VolumeSoftBand {
    /** Nombre canónico del músculo que admite la banda en el presupuesto del generador. */
    const val GLUTES_MUSCLE = "Glúteos"

    private const val EPSILON = 0.001

    /** Límite normal de series semanales de [group]: el MRV global, que la banda no toca. */
    fun globalLimit(group: KpknMuscleGroup): Int = VolumeLandmarks.byGroup.getValue(group).mrv

    /**
     * Techo blando semanal de [group] cuando su límite efectivo es [limit].
     * Solo glúteos tiene banda, y solo si su límite es el global: una restricción
     * personal más baja no se relaja. Con [band] = 0 el techo es el propio límite.
     */
    fun softCeiling(group: KpknMuscleGroup, limit: Int, band: Double = GLUTES_SOFT_BAND): Double =
        if (group == KpknMuscleGroup.GLUTES && limit >= globalLimit(group)) limit + band else limit.toDouble()

    /** Igual que [softCeiling] para el nombre canónico de un músculo del presupuesto. */
    fun softCeilingFor(muscle: String, limit: Int, band: Double = GLUTES_SOFT_BAND): Double =
        if (muscle == GLUTES_MUSCLE) softCeiling(KpknMuscleGroup.GLUTES, limit, band) else limit.toDouble()

    /** Clasifica [weeklySets] de [group] frente a su [limit] y a su techo blando. */
    fun bandOf(
        group: KpknMuscleGroup,
        weeklySets: Double,
        limit: Int,
        band: Double = GLUTES_SOFT_BAND,
    ): VolumeBand = when {
        weeklySets <= limit -> VolumeBand.WITHIN_LIMIT
        weeklySets <= softCeiling(group, limit, band) -> VolumeBand.HIGH_VOLUME
        else -> VolumeBand.OVER_CEILING
    }

    /**
     * Aviso de «volumen alto» si [weeklySets] del [muscle] pasa su [limit] sin superar su techo
     * blando; null si cabe en el límite, si lo supera de verdad o si el músculo no tiene banda.
     * La comparación admite el ruido decimal de las sumas de series indirectas.
     */
    fun noticeOrNull(
        muscle: String,
        weeklySets: Double,
        limit: Int,
        band: Double = GLUTES_SOFT_BAND,
    ): HighVolumeNotice? {
        val ceiling = softCeilingFor(muscle, limit, band)
        return if (weeklySets > limit + EPSILON && weeklySets <= ceiling + EPSILON) {
            HighVolumeNotice(muscle, weeklySets, limit, ceiling)
        } else {
            null
        }
    }

    /** Series con coma decimal y sin ceros sobrantes: 17 → «17», 17,5 → «17,5». */
    fun formatSets(sets: Double): String {
        val tenths = (sets * 10.0).roundToLong()
        val whole = tenths / 10
        val decimal = tenths % 10
        return if (decimal == 0L) whole.toString() else "$whole,$decimal"
    }
}
