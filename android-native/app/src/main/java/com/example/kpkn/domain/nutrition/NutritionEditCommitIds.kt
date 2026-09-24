package com.example.kpkn.domain.nutrition

import java.util.UUID

/**
 * Identificadores de idempotencia POR operación de edición del editor
 * nutricional directo.
 *
 * Una «revisión» del borrador identifica una operación de guardado (la revisión
 * 0 es el alta). La primera vez que esa operación se guarda se genera su id y
 * CUALQUIER reintento de la MISMA operación lo reutiliza; una edición posterior
 * usa otro id. Así el commitId del alta no se reutiliza para siempre y cada
 * operación se puede repetir sin duplicar plan, metas derivadas ni snapshots.
 *
 * Puro Kotlin: sin `android.*` y sin estado compartido fuera del editor.
 */
class NutritionEditCommitIds(
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val idsByRevision = mutableMapOf<Int, String>()

    /** Id de la operación de la [revision]; estable para reintentos. */
    fun idFor(revision: Int): String = idsByRevision.getOrPut(revision) { newId() }
}
