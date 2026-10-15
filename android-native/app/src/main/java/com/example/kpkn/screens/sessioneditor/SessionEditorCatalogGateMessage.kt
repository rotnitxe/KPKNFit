package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.catalogv2.CatalogV2SelectionGateIssue
import com.example.kpkn.data.models.Session

private const val SESSION_ISSUE_PREFIX = "session:"

/**
 * Lo que lee la persona cuando el cortafuegos del catálogo bloquea el guardado (SE10): qué ejercicio y qué hacer, en tipo
 * frase y sin ids ni códigos. El detalle técnico se va a Log.w desde el VM.
 */
internal fun catalogGateBlockMessage(issues: List<CatalogV2SelectionGateIssue>, session: Session): String {
    val namesById = session.allExercises().associate { it.id to it.name.trim() }
    val exerciseNames = issues
        .filterNot { it.exerciseId.startsWith(SESSION_ISSUE_PREFIX) }
        .map { it.exerciseId }
        .distinct()
        .map { id -> namesById[id]?.takeIf { it.isNotEmpty() } ?: "Ejercicio" }
    val sessionSentences = issues
        .filter { it.exerciseId.startsWith(SESSION_ISSUE_PREFIX) }
        .map { it.code }
        .distinct()
        .map(::sessionIssueSentence)

    return when {
        exerciseNames.isNotEmpty() -> (listOf("No se pudo guardar: ${exerciseSentence(exerciseNames)}") + sessionSentences)
            .joinToString(" ")
        sessionSentences.isNotEmpty() -> "No se pudo guardar. " + sessionSentences.joinToString(" ")
        else -> "No se pudo guardar: revisa los ejercicios de la sesión y vuelve a guardar."
    }
}

/** Una ficha: «Nombre» …; varias: «A» y 2 más …; dos: «A» y «B» … */
private fun exerciseSentence(names: List<String>): String = when (names.size) {
    1 -> "«${names[0]}» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo."
    2 -> "«${names[0]}» y «${names[1]}» no tienen su ficha del catálogo. Cámbialos por uno del catálogo o quítalos."
    else -> "«${names[0]}» y ${names.size - 1} más no tienen su ficha del catálogo. Cámbialos por uno del catálogo o quítalos."
}

private fun sessionIssueSentence(code: String): String = when (code) {
    "mixed_catalog_revisions" -> "Hay ejercicios de versiones distintas del catálogo. Vuelve a elegirlos en el catálogo."
    "duplicate_occurrence_id" -> "Hay un ejercicio repetido en la sesión. Quita la copia y vuelve a guardar."
    else -> "La sesión tiene un dato que el catálogo no acepta. Revisa la sesión y vuelve a guardar."
}
