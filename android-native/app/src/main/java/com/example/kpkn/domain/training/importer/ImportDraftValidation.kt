package com.example.kpkn.domain.training.importer

/** Qué hay que revisar antes de guardar el programa importado. */
enum class ImportIssueKind {
    /** No hay ninguna semana. */
    NO_WEEKS,

    /** Una semana sin ningún ejercicio. */
    EMPTY_WEEK,

    /** Un día sin ejercicios. */
    EMPTY_DAY,

    /** Un nombre sin decidir: ni «Elegir» ni «Propio». Sale una vez por nombre distinto. */
    UNDECIDED_EXERCISE,

    /** Avisos sin revisar; [ImportIssue.count] es cuántos. */
    PENDING_WARNINGS,
}

/**
 * Un problema de la revisión final, con su sitio cuando lo tiene. [count] solo importa en [ImportIssueKind.PENDING_WARNINGS];
 * en el resto vale 1.
 */
data class ImportIssue(
    val kind: ImportIssueKind,
    val weekIndex: Int? = null,
    val dayId: String? = null,
    val exerciseId: String? = null,
    val rawName: String? = null,
    val count: Int = 1,
)

/** Revisión final del borrador antes de guardarlo. Pura: no cambia nada. */
object ImportDraftValidation {

    /**
     * Lista lo que impide guardar tal cual. [isDecided] dice si un nombre ya tiene decisión («Elegir» o «Propio»).
     */
    fun check(draft: ImportedProgramDraft, isDecided: (rawName: String) -> Boolean): List<ImportIssue> {
        val issues = mutableListOf<ImportIssue>()
        if (draft.weeks.isEmpty()) issues += ImportIssue(ImportIssueKind.NO_WEEKS)
        for (week in draft.weeks) {
            if (week.days.all { it.exercises.isEmpty() }) {
                issues += ImportIssue(ImportIssueKind.EMPTY_WEEK, weekIndex = week.index)
            }
            for (day in week.days) {
                if (day.exercises.isEmpty()) {
                    issues += ImportIssue(ImportIssueKind.EMPTY_DAY, weekIndex = week.index, dayId = day.id)
                }
            }
        }
        val seen = mutableSetOf<String>()
        for (week in draft.weeks) {
            for (day in week.days) {
                for (exercise in day.exercises) {
                    if (seen.add(exercise.rawName) && !isDecided(exercise.rawName)) {
                        issues += ImportIssue(
                            kind = ImportIssueKind.UNDECIDED_EXERCISE,
                            weekIndex = week.index,
                            dayId = day.id,
                            exerciseId = exercise.id,
                            rawName = exercise.rawName,
                        )
                    }
                }
            }
        }
        val pending = draft.warnings.count { it.id !in draft.reviewedWarnings }
        if (pending > 0) issues += ImportIssue(ImportIssueKind.PENDING_WARNINGS, count = pending)
        return issues
    }

    /** Listo para guardar: sin ningún problema pendiente. */
    fun isReady(issues: List<ImportIssue>): Boolean = issues.isEmpty()
}
