package com.example.kpkn.domain.training.importer

/**
 * Programa leído de un archivo o de texto pegado, antes de emparejar ejercicios y repartir días. [warnings] recoge
 * lo que el analizador no entendió («Hoja «Día 2», fila 4: …») para mostrarlo en la vista previa.
 */
data class ImportedProgramDraft(
    val title: String,
    val weeks: List<ImportedWeek>,
    val warnings: List<String> = emptyList(),
)

/** Una semana del programa, numerada como aparece en el archivo (1 si no hay semanas). */
data class ImportedWeek(
    val index: Int,
    val days: List<ImportedDay>,
)

/** Un día de entreno con sus ejercicios, en el orden del archivo. */
data class ImportedDay(
    val name: String,
    val exercises: List<ImportedExercise>,
)

/**
 * Un ejercicio con su prescripción tal como se leyó. Series y repeticiones son obligatorias; carga (kg), % de 1RM,
 * RPE, RIR, descanso (segundos) y notas son opcionales.
 */
data class ImportedExercise(
    val rawName: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val loadKg: Double? = null,
    val percent1Rm: Double? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val restSeconds: Int? = null,
    val notes: String? = null,
)
