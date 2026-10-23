package com.example.kpkn.domain.workout

/**
 * Base de las aproximaciones (LV-C): el porcentaje de una aproximación es un porcentaje de la carga de TRABAJO de la serie efectiva
 * objetivo, no del 1RM. Solo si esa carga falta se recurre a la capacidad de referencia. Todos los sitios que convierten
 * «50 %» en kilogramos usan esta base, para que lo que muestra la tarjeta y lo que se registra coincidan.
 */
fun warmupBaseLoadKg(workingLoadKg: Double?, referenceCapacityKg: Double?): Double? =
    workingLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        ?: referenceCapacityKg?.takeIf { it.isFinite() && it > 0.0 }

/** Kilogramos sin redondear al material de una aproximación; 0 si no hay ni carga de trabajo ni capacidad. */
fun warmupTargetLoadKg(percentageOfWorkingWeight: Double, workingLoadKg: Double?, referenceCapacityKg: Double?): Double {
    val base = warmupBaseLoadKg(workingLoadKg, referenceCapacityKg) ?: return 0.0
    return base * WarmupCalibrationEngine.normalizePercentage(percentageOfWorkingWeight)
}
