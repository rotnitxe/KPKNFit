package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.training.ExerciseVolumeContributor
import com.example.kpkn.domain.training.MuscleVolumeBreakdown
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import kotlin.math.roundToInt

/**
 * Datos de la pestaña Volumen. `VolumeView` entrega el cálculo común del alcance; estas piezas solo lo presentan y redondean.
 */

/** Zonas del objetivo personalizado de un músculo, en series semanales: mínimo efectivo, máximo adaptativo y máximo recuperable. */
data class ProgramVolumeZones(
    val minEffective: Int,
    val maxAdaptive: Int,
    val maxRecoverable: Int,
)

/**
 * Una fila de la pestaña: [sets] son las series planificadas del alcance (o su promedio semanal), [real] las hechas si hay registros.
 * [zones] es null cuando el programa no está calibrado: entonces no hay estado de volumen, solo la marca.
 * [scaleMax] es la escala de la barra; la fija quien calcula la fila.
 */
data class ProgramVolumeMuscle(
    val name: String,
    val sets: Double,
    val zones: ProgramVolumeZones?,
    val real: Double?,
    val scaleMax: Double,
) {
    /** Estado de volumen de la fila: decide su color y su etiqueta. */
    val state: ProgramVolumeState
        get() = programVolumeStateOf(sets, zones)
}

/** Una línea del desglose: un ejercicio, o un ejercicio dentro de una cabeza muscular, con sus series semanales. */
data class ProgramVolumeLine(val name: String, val sets: Double)

/** Una cabeza de un músculo (deltoides anterior, glúteo medio...) con los ejercicios que la cargan. */
data class ProgramVolumeSubMuscle(
    val name: String,
    val sets: Double,
    val exercises: List<ProgramVolumeLine>,
    val role: MuscleRole? = null,
)

/** Reparte el redondeo a décimas para que las líneas visibles sumen la cifra de la barra. */
internal fun programVolumeLines(contributors: List<ExerciseVolumeContributor>): List<ProgramVolumeLine> {
    val tenths = contributors.map { kotlin.math.floor(it.sets * 10.0).toInt() }.toMutableList()
    val target = (contributors.sumOf { it.sets } * 10.0).roundToInt()
    val order = contributors.indices.sortedByDescending { contributors[it].sets * 10.0 - tenths[it] }
    order.take((target - tenths.sum()).coerceAtLeast(0)).forEach { tenths[it]++ }
    return contributors.mapIndexed { index, contributor -> ProgramVolumeLine(contributor.exerciseName, tenths[index] / 10.0) }
}

/** Mantiene las porciones sin datos y conserva el rol de cada exposición calculada. */
internal fun programVolumeBreakdown(muscle: String, entry: MuscleVolumeBreakdown?): ProgramVolumeBreakdown {
    val portions = entry?.portions.orEmpty().map {
        ProgramVolumeSubMuscle(it.name, it.sets, programVolumeLines(it.exercises), it.role)
    }
    val names = when (muscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio", "Glúteo Menor")
        "Pectorales" -> listOf("Pectoral Superior", "Pectoral Medio", "Pectoral Inferior")
        "Trapecio" -> listOf("Trapecio Superior", "Trapecio Medio", "Trapecio Inferior")
        "Pantorrillas" -> listOf("Gastrocnemio", "Sóleo")
        else -> emptyList()
    }
    val empty = names.filter { name -> portions.none { it.name == name } }
        .map { ProgramVolumeSubMuscle(it, 0.0, emptyList()) }
    return ProgramVolumeBreakdown(programVolumeLines(entry?.exercises.orEmpty()), (portions + empty).sortedBy { names.indexOf(it.name) })
}

/** Desglose de la fila desplegada: los ejercicios que aportan volumen y, si el músculo tiene cabezas, sus cabezas. */
data class ProgramVolumeBreakdown(val exercises: List<ProgramVolumeLine>, val subMuscles: List<ProgramVolumeSubMuscle>)

/**
 * Series en texto, con coma decimal solo si la cifra no es entera: «14», «13,5». Una cifra sin decimales no lleva «,0» y no
 * parece más precisa de lo que es.
 */
fun programVolumeSeries(value: Double): String =
    formatDecimalEs(value, 1).removeSuffix(",0")
