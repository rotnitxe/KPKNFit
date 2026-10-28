package com.example.kpkn.domain.nutrition.exam

import kotlinx.serialization.Serializable

/** Una linea de un archivo de la carpeta `cases` (JSONL) del examen de nutricion (docs/nutrition-v2/EXAMEN.md, seccion 2). */
@Serializable
data class ExamCase(
    val id: String,
    val split: String? = null,
    val text: String,
    val mealType: String = "LUNCH",
    val region: String? = null,
    val phenomena: List<String> = emptyList(),
    val style: String? = null,
    val origin: String? = null,
)

/** Macros de una porcion; `null` cuando el sistema no las conoce. */
@Serializable
data class ExamMacros(
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
)

/** Una mencion predicha (seccion 6 del examen). `unit` es un campo extra: la unidad en que el sistema expresa `grams`. */
@Serializable
data class ExamMention(
    val text: String,
    val foodName: String? = null,
    val foodId: String? = null,
    val source: String? = null,
    val heuristic: Boolean = false,
    val excluded: Boolean = false,
    val grams: Double? = null,
    val unit: String? = null,
    val eaten: ExamMacros = ExamMacros(),
    val per100g: ExamMacros? = null,
    val state: String = "UNKNOWN",
    val autoAccepted: Boolean = false,
    val questions: List<String> = emptyList(),
    val nutrientsPresent: List<String> = emptyList(),
)

/** Una linea de `predictions/<sistema>-<fecha>.jsonl`. `lastResort` es un campo extra: la app habria creado marcadores manuales. */
@Serializable
data class ExamPrediction(
    val id: String,
    val system: String,
    val latencyMs: Double,
    val error: String? = null,
    val mentions: List<ExamMention> = emptyList(),
    val lastResort: Boolean = false,
)
