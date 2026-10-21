package com.example.kpkn.ui.design

/**
 * Columnas de la tabla de series para cada modo (SE1). Las etiquetas son exactas. Con 288 dp útiles (360 dp menos márgenes y el relleno
 * del módulo), la primera columna ocupa 48 dp y cada celda de dato queda en unos 48 dp, el mínimo táctil. El descanso pesa 1,3 porque su
 * cabecera es la más larga: a 100 % cabe sin desbordarse y a 130 % solo se sale un poco por los lados.
 */
object KpknSetColumns {
    private val kg = KpknSetColumn(key = "kg", label = "kg", spoken = "kilos")
    private val reps = KpknSetColumn(key = "reps", label = "Reps", spoken = "repeticiones")
    private val rest = KpknSetColumn(key = "rest", label = "Descanso", weight = 1.3f)

    /** Modo RIR: kg, Reps, RIR y descanso. */
    val rir: List<KpknSetColumn> = listOf(
        kg,
        reps,
        KpknSetColumn(key = "rir", label = "RIR", spoken = "repeticiones en reserva"),
        rest,
    )

    /** Modo RPE: kg, Reps, RPE y descanso. */
    val rpe: List<KpknSetColumn> = listOf(
        kg,
        reps,
        KpknSetColumn(key = "rpe", label = "RPE", spoken = "esfuerzo percibido"),
        rest,
    )

    /** Modo % del 1RM: porcentaje, Reps y descanso. */
    val percent: List<KpknSetColumn> = listOf(
        KpknSetColumn(key = "percent", label = "% 1RM", weight = 1.2f, spoken = "porcentaje de una repetición máxima"),
        reps,
        rest,
    )

    /** Modo tiempo: kg, tiempo y descanso. */
    val time: List<KpknSetColumn> = listOf(
        kg,
        KpknSetColumn(key = "time", label = "Tiempo"),
        rest,
    )

    /** Modo distancia: distancia, tiempo y descanso. */
    val distance: List<KpknSetColumn> = listOf(
        KpknSetColumn(key = "distance", label = "Distancia", weight = 1.4f),
        KpknSetColumn(key = "time", label = "Tiempo"),
        rest,
    )
}
