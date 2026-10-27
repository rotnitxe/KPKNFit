package com.example.kpkn.screens.programdetail.design

/*
 * Lo que espera una decisión del usuario en Program Detail: la hoja «Pendiente» y su campana. Kotlin puro, sin Compose: la prueba
 * decide el orden y los grupos.
 */

/** Tipos de pendiente. [priority] ordena (0 va primero) y [group] es el rótulo de la hoja donde aparece. */
internal enum class ProgramPendingKind(val priority: Int, val group: String) {
    ONE_RM_TEST(0, "Esta semana"),
    MISSED_SESSIONS(0, "Esta semana"),
    MISSING_TM(0, "Esta semana"),
    PROGRESSION(1, "Esta semana"),
    AUTOREGULATION(1, "Esta semana"),
    BLOCK_REVIEW(1, "Esta semana"),
    WEEKLY_CHECKIN(2, "Semana pasada"),
    CONTINUATION(3, "Más adelante"),
}

/**
 * Una cosa pendiente. [onOpen] abre lo que la resuelve. [primaryLabel] y [secondaryLabel] son acciones que la resuelven desde la
 * propia hoja: cada una se pinta solo si tiene texto y acción.
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

/** Grupos en orden de aparición: (rótulo, ítems). Un grupo sin ítems no aparece. */
internal fun groupedPending(items: List<ProgramPendingItem>): List<Pair<String, List<ProgramPendingItem>>> =
    items.groupBy { it.kind.group }.map { (group, members) -> group to members }
