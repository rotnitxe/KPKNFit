package com.example.kpkn.data.repository

import com.example.kpkn.data.models.ExerciseMuscleInfo

/**
 * Métricas AUGE extraídas directamente de la base de datos de ejercicios.
 * Nunca contiene valores inventados: si la DB no tiene el campo, el campo
 * correspondiente es null y el caller debe mostrar "—" o saltar el cálculo.
 *
 * @param efc  Costo metabólico/muscular (1–5). Null si no declarado en DB.
 * @param cnc  Costo del Sistema Nervioso Central (1–5). Null si no declarado.
 * @param ssc  Costo espinal/estructural (0–2). Null si no declarado.
 * @param axialLoad Factor de carga axial (0.0–1.0). Null si no declarado o no aplica.
 */
data class AugeDbMetrics(
    val efc: Double,
    val cnc: Double,
    val ssc: Double,
    val axialLoad: Double?,
) {
    /** SNC (Sistema Nervioso Central) — alias semántico de cnc (central nervous cost). */
    val snc: Double get() = cnc
}

/**
 * Repositorio de métricas AUGE. Única API para leer EFC/CNC/SSC en runtime.
 * Retorna null si la DB no tiene datos completos — nunca inventa valores.
 *
 * Uso:
 * ```kotlin
 * val repo = AugeMetricsRepository(exerciseDb.associateBy { it.id.lowercase() })
 * val metrics = repo.metricsFor(exercise.exerciseDbId)
 *     ?: return  // ejercicio sin datos: saltar o mostrar "—"
 * ```
 */
class AugeMetricsRepository(
    private val exerciseIndex: Map<String, ExerciseMuscleInfo>,
) {
    /**
     * Retorna métricas AUGE para el ejercicio dado.
     *
     * @param exerciseDbId ID del ejercicio en la DB (puede ser null si el ejercicio es personalizado).
     * @return [AugeDbMetrics] si la DB tiene efc+cnc+ssc completos, null en caso contrario.
     */
    fun metricsFor(exerciseDbId: String?): AugeDbMetrics? {
        val id = exerciseDbId?.lowercase() ?: return null
        val info = exerciseIndex[id] ?: return null
        val efc = info.efc ?: return null
        val cnc = info.cnc ?: return null
        val ssc = info.ssc ?: return null
        return AugeDbMetrics(
            efc = efc.coerceIn(1.0, 5.0),
            cnc = cnc.coerceIn(1.0, 5.0),
            ssc = ssc.coerceIn(0.0, 2.0),
            axialLoad = info.axialLoadFactor,
        )
    }

    companion object {
        /** Crea un repositorio a partir de la lista de ejercicios de la DB. */
        fun from(exerciseList: List<ExerciseMuscleInfo>): AugeMetricsRepository =
            AugeMetricsRepository(exerciseList.associateBy { it.id.lowercase() })
    }
}
