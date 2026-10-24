package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.programdetail.design.structure.SIMPLE_WEEK_NAME
import kotlin.math.roundToInt

/** Nombre de dominio de la semana de un programa simple («Semana repetible»): el dominio no cambia, solo lo que se pinta. */
private const val REPEATING_WEEK_DOMAIN_NAME = "Semana repetible"

/**
 * El alcance del volumen tal como se pinta: en un programa simple, «Semana que se repite» en vez de «Semana repetible». Solo cambia el
 * texto; el cálculo del alcance es el de siempre.
 */
internal fun volumeScopeLabelOf(label: String, simpleProgram: Boolean): String =
    if (simpleProgram) label.replace(REPEATING_WEEK_DOMAIN_NAME, SIMPLE_WEEK_NAME) else label

/**
 * Datos de la pestaña Volumen (P4). `VolumeView` los calcula con la lógica de siempre y las piezas de `design/` solo los pintan, así
 * que ninguna pieza toca el dominio.
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
data class ProgramVolumeSubMuscle(val name: String, val sets: Double, val exercises: List<ProgramVolumeLine>)

/** Desglose de la fila desplegada: los ejercicios que aportan volumen y, si el músculo tiene cabezas, sus cabezas. */
data class ProgramVolumeBreakdown(val exercises: List<ProgramVolumeLine>, val subMuscles: List<ProgramVolumeSubMuscle>)

/**
 * El alcance del volumen tal como se pinta: el nombre del alcance y su detalle («Promedio semanal de 12 semanas.»), los tipos de
 * alcance que hay (Semana, Bloque, Programa) con el elegido, y si el selector puede retroceder o avanzar dentro de su tipo.
 */
data class ProgramVolumeScope(
    val label: String,
    val detail: String,
    val kinds: List<String>,
    val selectedKind: Int,
    val canPrevious: Boolean,
    val canNext: Boolean,
)

/** Series de empuje, tirón y pierna del alcance. */
data class ProgramVolumeSplit(val push: Double, val pull: Double, val legs: Double)

/** Molestia del programa y las veces que se ha registrado. */
data class ProgramVolumeDiscomfort(val name: String, val count: Int)

/** Molestia que se repite con un ejercicio concreto. */
data class ProgramVolumeAssociation(val exercise: String, val discomfort: String, val count: Int)

/**
 * Series en texto, con coma decimal solo si la cifra no es entera: «14», «13,5». Una cifra sin decimales no lleva «,0» y no
 * parece más precisa de lo que es.
 */
fun programVolumeSeries(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else formatDecimalEs(value, 1)

/** Porcentaje entero de [part] sobre [total]; 0 si no hay total, para no inventar un reparto. */
fun programVolumeShare(part: Double, total: Double): Int =
    if (total <= 0.0) 0 else (part / total * 100.0).roundToInt()
