package com.example.kpkn.domain.training.insights

/**
 * Referencias de volumen semanal por músculo.
 *
 * Es una **heurística de programación**, no una medición fisiológica ni un umbral clínico: sirve
 * para avisar de que un músculo se salió de la referencia que el propio atleta usa, no para
 * calificar la sesión. Por eso el texto siempre dice «referencia».
 */
enum class VolumeZone { BELOW_MEV, IN_RANGE, ABOVE_MRV }

data class MuscleLandmarks(
    val name: String,
    val mev: Double,
    val mavMin: Double,
    val mavMax: Double,
    val mrv: Double,
)

object VolumeLandmarks {
    /** Identidad de la tabla; un cambio de números sube la versión. */
    const val version: String = "rp-heuristic-v1"

    const val provenance: String =
        "Heurística de programación por músculo. No es una medición fisiológica ni un umbral clínico."

    val all: List<MuscleLandmarks> = listOf(
        MuscleLandmarks("Cuello", 2.0, 4.0, 8.0, 12.0),
        MuscleLandmarks("Trapecio", 4.0, 12.0, 20.0, 26.0),
        MuscleLandmarks("Deltoides", 8.0, 16.0, 22.0, 26.0),
        MuscleLandmarks("Pectorales", 8.0, 12.0, 20.0, 22.0),
        MuscleLandmarks("Bíceps", 8.0, 14.0, 20.0, 26.0),
        MuscleLandmarks("Tríceps", 6.0, 10.0, 14.0, 18.0),
        MuscleLandmarks("Antebrazo", 2.0, 6.0, 10.0, 14.0),
        MuscleLandmarks("Dorsales", 10.0, 14.0, 22.0, 25.0),
        MuscleLandmarks("Erectores Espinales", 4.0, 8.0, 14.0, 16.0),
        MuscleLandmarks("Core", 4.0, 10.0, 16.0, 20.0),
        MuscleLandmarks("Abdomen", 6.0, 16.0, 20.0, 25.0),
        MuscleLandmarks("Glúteos", 4.0, 8.0, 16.0, 20.0),
        MuscleLandmarks("Aductores", 4.0, 8.0, 12.0, 16.0),
        MuscleLandmarks("Cuádriceps", 8.0, 12.0, 18.0, 20.0),
        MuscleLandmarks("Isquiosurales", 6.0, 10.0, 16.0, 20.0),
        MuscleLandmarks("Pantorrillas", 8.0, 12.0, 20.0, 20.0),
        MuscleLandmarks("Romboides", 4.0, 8.0, 14.0, 18.0),
    )

    private val byName: Map<String, MuscleLandmarks> = all.associateBy { normalize(it.name) }

    fun forMuscle(muscle: String): MuscleLandmarks? = byName[normalize(muscle)]

    fun zoneFor(muscle: String, weeklySets: Double): VolumeZone {
        val landmarks = forMuscle(muscle) ?: return VolumeZone.IN_RANGE
        return when {
            weeklySets > landmarks.mrv -> VolumeZone.ABOVE_MRV
            weeklySets < landmarks.mev -> VolumeZone.BELOW_MEV
            else -> VolumeZone.IN_RANGE
        }
    }

    /** Línea de referencia que se dibuja junto a la barra; el MRV del músculo. */
    fun referenceFor(muscle: String): Double? = forMuscle(muscle)?.mrv

    private fun normalize(value: String): String = value.trim().lowercase()
}
