package com.example.kpkn.domain.training.importer

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Programa leído de un archivo o de texto pegado, antes de emparejar ejercicios y repartir días. [warnings] recoge
 * lo que el analizador no entendió, con su tipo, semana y día, para revisarlo en la vista previa. [reviewedWarnings]
 * guarda los ids de los avisos ya resueltos o ignorados. [nextId] es el siguiente número para los ids que crea el
 * editor (`x<n>`), único dentro del borrador.
 * Serializable: el borrador del alta lo guarda mientras la persona revisa el programa. Los borradores guardados antes
 * de los ids y de los avisos estructurados siguen leyéndose (ver [ImportWarningListSerializer] e `ImportDraftEditor.withIds`).
 */
@Serializable
data class ImportedProgramDraft(
    val title: String,
    val weeks: List<ImportedWeek>,
    @Serializable(with = ImportWarningListSerializer::class)
    val warnings: List<ImportWarning> = emptyList(),
    val reviewedWarnings: Set<String> = emptySet(),
    val nextId: Int = 1,
) {
    /** Solo los textos de los avisos, para la vista previa de hoy. */
    val warningMessages: List<String> get() = warnings.map { it.message }
}

/** Una semana del programa, numerada como aparece en el archivo (1 si no hay semanas). */
@Serializable
data class ImportedWeek(
    val index: Int,
    val days: List<ImportedDay>,
)

/**
 * Un día de entreno con sus ejercicios, en el orden del archivo. El [id] no cambia al mover o renombrar el día; vacío
 * solo en borradores guardados antes de los ids.
 */
@Serializable
data class ImportedDay(
    val id: String = "",
    val name: String,
    val exercises: List<ImportedExercise>,
)

/**
 * Un ejercicio con su prescripción tal como se leyó. Series y repeticiones son obligatorias; carga (kg), % de 1RM,
 * RPE, RIR, descanso (segundos) y notas son opcionales. [sourceText] es el texto original de la fila o línea. El [id]
 * está vacío solo en borradores guardados antes de los ids.
 */
@Serializable
data class ImportedExercise(
    val id: String = "",
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
    val sourceText: String? = null,
)

/** Tipo de aviso del análisis. */
@Serializable
enum class ImportWarningKind {
    /** Una fila que se omitió: sin nombre, sin series, sin repeticiones, fuera de rango o «no se entiende». */
    SKIPPED_ROW,

    /** El ejercicio se conservó, pero se descartó un valor (carga, %, RPE, RIR o descanso). */
    IGNORED_VALUE,

    /** Un día sin ejercicios, omitido. */
    EMPTY_DAY,

    /** No salió ningún ejercicio. */
    NOTHING_FOUND,

    /**
     * Aviso informativo de una hoja entera (omitida por su nombre) o un recuento por hoja: líneas de texto, series de
     * calentamiento o filas de cálculo y de etiquetas. Lleva la ubicación «Hoja «X»» y no lleva día ni ejercicio.
     */
    SHEET_NOTE,
}

/**
 * Aviso del análisis con su sitio. [message] es el texto que ya se mostraba y no cambia. [weekIndex] y [dayId] son la
 * semana y el día abiertos al leer la fila (dayId nulo si no había día o si el día se omitió); [exerciseId] solo
 * existe en un aviso de un ejercicio conservado. [id] es estable dentro del borrador («a1», «a2»…).
 */
@Serializable
data class ImportWarning(
    val id: String,
    val kind: ImportWarningKind,
    val location: String,
    val sourceText: String?,
    val message: String,
    val weekIndex: Int?,
    val dayId: String?,
    val exerciseId: String?,
)

/**
 * Lee la lista de avisos en los dos formatos: objetos ([ImportWarning], el actual) y cadenas de texto (borradores
 * guardados antes de los avisos estructurados). Cada cadena pasa a ser el aviso `a<n>` de su posición, con su texto
 * como mensaje, tipo SKIPPED_ROW y sin semana, día ni ejercicio. Al escribir sale siempre el formato de objetos.
 */
internal object ImportWarningListSerializer :
    JsonTransformingSerializer<List<ImportWarning>>(ListSerializer(ImportWarning.serializer())) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val items = element as? JsonArray ?: return element
        return JsonArray(
            items.mapIndexed { index, item ->
                val text = (item as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapIndexed item
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("a${index + 1}"),
                        "kind" to JsonPrimitive(ImportWarningKind.SKIPPED_ROW.name),
                        "location" to JsonPrimitive(""),
                        "sourceText" to JsonNull,
                        "message" to JsonPrimitive(text),
                        "weekIndex" to JsonNull,
                        "dayId" to JsonNull,
                        "exerciseId" to JsonNull,
                    ),
                )
            },
        )
    }
}
