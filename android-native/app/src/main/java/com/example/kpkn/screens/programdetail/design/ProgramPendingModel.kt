package com.example.kpkn.screens.programdetail.design

/*
 * Lo que espera una decisión del usuario en Program Detail: la hoja «Pendiente» (lienzo v6, p-pending) y su campana. Kotlin puro, sin
 * Compose: la prueba decide el orden, la fila destacada y qué va en «Más adelante».
 */

/**
 * Tipos de pendiente. [priority] ordena (0 va primero: bloqueantes, propuestas del motor, preguntas y, al final, lo que llega más adelante).
 * [later] es lo de «Más adelante»: se enseña, pero no cuenta en la campana. [proposal] son las propuestas del motor: la primera va destacada,
 * con «Aplicar» y «Mantener carga».
 */
internal enum class ProgramPendingKind(val priority: Int, val later: Boolean = false, val proposal: Boolean = false) {
    ONE_RM_TEST(0),
    MISSED_SESSIONS(0),
    MISSING_TM(0),
    PROGRESSION(1, proposal = true),
    AUTOREGULATION(1, proposal = true),
    BLOCK_REVIEW(1),
    BLOCK_CHANGE(1),
    WEEKLY_CHECKIN(2),
    CONTINUATION(2),
    PROGRAM_END_SOON(3, later = true),
}

/**
 * Una cosa pendiente: una sola fila por tipo (las propuestas del mismo tipo se juntan en la misma fila). [onOpen] abre directamente la hoja
 * que la resuelve. [primaryLabel] y [secondaryLabel] son las acciones de la fila destacada: cada una se pinta solo si tiene texto y acción.
 */
internal data class ProgramPendingItem(
    val kind: ProgramPendingKind,
    val title: String,
    val detail: String,
    val onOpen: () -> Unit,
    val primaryLabel: String? = null,
    val onPrimary: (() -> Unit)? = null,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
)

/** Por prioridad y, a igual prioridad, en el orden de entrada. */
internal fun orderedPending(items: List<ProgramPendingItem>): List<ProgramPendingItem> = items.sortedBy { it.kind.priority }

/** La fila destacada: la primera propuesta del motor que se puede resolver desde la hoja; null si no hay ninguna. */
internal fun featuredPending(items: List<ProgramPendingItem>): ProgramPendingItem? =
    orderedPending(items).firstOrNull { it.kind.proposal && it.primaryLabel != null && it.onPrimary != null }

/** Las filas sin destacar de ahora, en orden. */
internal fun pendingNowRows(items: List<ProgramPendingItem>): List<ProgramPendingItem> {
    val featured = featuredPending(items)
    return orderedPending(items).filter { it !== featured && !it.kind.later }
}

/** Las filas de «Más adelante», en orden. */
internal fun pendingLaterRows(items: List<ProgramPendingItem>): List<ProgramPendingItem> =
    orderedPending(items).filter { it.kind.later }

/** Lo que cuenta la campana y «Pendiente · N»: todo salvo «Más adelante». */
internal fun pendingDecisionCount(items: List<ProgramPendingItem>): Int = items.count { !it.kind.later }

/** «3 cosas esperan tu decisión.»; en singular, «1 cosa espera tu decisión.». */
internal fun pendingSubtitle(count: Int): String =
    if (count == 1) "1 cosa espera tu decisión." else "$count cosas esperan tu decisión."

/** El detalle de una fila que junta varias del mismo tipo: «… · y 2 más». Con una sola, el detalle tal cual. */
internal fun pendingDetailWithMore(detail: String, extra: Int): String = when {
    extra <= 0 -> detail
    detail.isBlank() -> "Y $extra más"
    else -> "$detail · y $extra más"
}

/** «Falta el TM de press militar» / «Faltan los TM de press militar y remo» / «Faltan 3 TM». */
internal fun missingTmTitle(names: List<String>): String {
    val lower = names.map { name -> name.replaceFirstChar { it.lowercase(java.util.Locale("es")) } }
    return when (lower.size) {
        0 -> "Faltan tus máximos de entrenamiento"
        1 -> "Falta el TM de ${lower[0]}"
        2 -> "Faltan los TM de ${lower[0]} y ${lower[1]}"
        else -> "Faltan ${lower.size} TM"
    }
}

/** «Sin él, sus cargas no se calculan»; con varios, «Sin ellos, …». */
internal fun missingTmDetail(count: Int): String =
    if (count == 1) "Sin él, sus cargas no se calculan" else "Sin ellos, sus cargas no se calculan"

/**
 * «Fin del programa en 2 semanas» / «… en 1 semana», con [weekNumber] la semana en curso contada desde 1; null si quedan 0 o más de 2
 * semanas después de la actual.
 */
internal fun programEndSoonTitle(weekNumber: Int?, weekCount: Int?): String? {
    if (weekNumber == null || weekCount == null) return null
    val remaining = weekCount - weekNumber
    return when (remaining) {
        1 -> "Fin del programa en 1 semana"
        2 -> "Fin del programa en 2 semanas"
        else -> null
    }
}

/** «2 sesiones sin registrar»; en singular, «1 sesión sin registrar». */
internal fun missedSessionsTitle(count: Int): String =
    if (count == 1) "1 sesión sin registrar" else "$count sesiones sin registrar"
