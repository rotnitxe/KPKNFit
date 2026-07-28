package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*

fun SessionEditorViewModel.setTargetDuration(minutes: Int?) {
    updateUi { state ->
        val updatedSession = state.session?.copy(targetDurationMinutes = minutes)
        state.copy(
            session = updatedSession,
            targetDurationMinutes = minutes,
            hasUnsavedChanges = true,
        )
    }
    scheduleAutoSave()
    scheduleAugeRecalc()
}

/** Actualiza la duración objetivo de una categoría/parte específica. */
fun SessionEditorViewModel.setPartTargetDuration(partId: String, minutes: Int?) {
    updateCurrentSession { session ->
        session.copy(parts = session.parts.map {
            if (it.id == partId) it.copy(targetDurationMinutes = minutes) else it
        })
    }
}

/** Actualiza la duración objetivo de un ejercicio específico. */
fun SessionEditorViewModel.setExerciseTargetDuration(exerciseId: String, minutes: Int?) {
    updateCurrentSession { session ->
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) it.copy(targetDurationMinutes = minutes) else it
        }
        val updatedParts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map {
                if (it.id == exerciseId) it.copy(targetDurationMinutes = minutes) else it
            })
        }
        session.copy(exercises = updatedExercises, parts = updatedParts)
    }
}

/** Splits the session global budget across parts by set-count weight. */
fun SessionEditorViewModel.distributeTargetDurationAcrossParts() {
    val session = currentUiState.session ?: return
    val total = session.targetDurationMinutes ?: return
    if (total <= 0 || session.parts.isEmpty()) return
    val weights = session.parts.map { part ->
        part.exercises.sumOf { it.sets.size.coerceAtLeast(1) }.coerceAtLeast(1)
    }
    val weightSum = weights.sum().coerceAtLeast(1)
    var allocated = 0
    updateCurrentSession { current ->
        current.copy(
            parts = current.parts.mapIndexed { index, part ->
                val share = if (index == current.parts.lastIndex) {
                    (total - allocated).coerceAtLeast(1)
                } else {
                    ((total.toDouble() * weights[index]) / weightSum).toInt().coerceAtLeast(1)
                        .also { allocated += it }
                }
                part.copy(targetDurationMinutes = share)
            },
        )
    }
}

    // ─── Feature 3: Variantes de sesión ──────────────────────────────────────────

/**
 * Crea una variante derivada de la sesión original. La variante es una copia
 * independiente con su propio nombre, ejercicios, series y descansos.
 * @param variant slot B/C/D donde se almacena (nunca A).
 * @param variantName nombre descriptivo (ej. "Rápida 45min", "Enfoque fuerza").
 */
fun SessionEditorViewModel.createVariant(variant: WeekVariant, variantName: String): Boolean {
    val state = currentUiState
    val base = state.session ?: return false
    if (variant == WeekVariant.A) return false
    val alreadyExists = when (variant) {
        WeekVariant.B -> base.sessionB != null
        WeekVariant.C -> base.sessionC != null
        WeekVariant.D -> base.sessionD != null
        else -> false
    }
    if (alreadyExists) return false
    val copy = base.copy(
        id = java.util.UUID.randomUUID().toString(),
        name = variantName,
        sessionB = null, sessionC = null, sessionD = null,
    )
    val updated = when (variant) {
        WeekVariant.B -> base.copy(sessionB = copy)
        WeekVariant.C -> base.copy(sessionC = copy)
        WeekVariant.D -> base.copy(sessionD = copy)
        else -> base
    }
    updateUi {
        it.copy(
            session = updated,
            activeVariant = variant,
            availableVariants = computeAvailableVariants(updated),
            hasUnsavedChanges = true,
        )
    }
    scheduleAutoSave()
    return true
}

/** Elimina la variante especificada de la sesión principal. */
fun SessionEditorViewModel.deleteVariant(variant: WeekVariant): Boolean {
    if (variant == WeekVariant.A) return false
    val base = currentUiState.session ?: return false
    val updated = when (variant) {
        WeekVariant.B -> base.copy(sessionB = null)
        WeekVariant.C -> base.copy(sessionC = null)
        WeekVariant.D -> base.copy(sessionD = null)
        else -> return false
    }
    updateUi {
        it.copy(
            session = updated,
            activeVariant = WeekVariant.A,
            availableVariants = computeAvailableVariants(updated),
            hasUnsavedChanges = true,
        )
    }
    scheduleAutoSave()
    return true
}

/** Cambia la variante activa en el editor (sólo UI, no persiste nada). */
fun SessionEditorViewModel.switchVariant(variant: WeekVariant) {
    updateUi { it.copy(activeVariant = variant) }
}

/**
 * Guarda los cambios actuales del editor en la variante activa de la sesión base.
 * Llamar antes de switchVariant para no perder cambios.
 */
fun SessionEditorViewModel.commitActiveVariantChanges() {
    val state = currentUiState
    val base = state.session ?: return
    val currentVariantSession = state.activeVariantSession ?: return
    val updated = when (state.activeVariant) {
        WeekVariant.A -> base.copy(
            name = currentVariantSession.name,
            description = currentVariantSession.description,
            exercises = currentVariantSession.exercises,
            parts = currentVariantSession.parts,
            warmup = currentVariantSession.warmup,
            targetDurationMinutes = currentVariantSession.targetDurationMinutes,
        )
        WeekVariant.B -> base.copy(sessionB = currentVariantSession)
        WeekVariant.C -> base.copy(sessionC = currentVariantSession)
        WeekVariant.D -> base.copy(sessionD = currentVariantSession)
    }
    updateUi { it.copy(session = updated, hasUnsavedChanges = true) }
}

internal fun SessionEditorViewModel.computeAvailableVariants(session: Session): List<WeekVariant> = buildList {
    add(WeekVariant.A)
    if (session.sessionB != null) add(WeekVariant.B)
    if (session.sessionC != null) add(WeekVariant.C)
    if (session.sessionD != null) add(WeekVariant.D)
}

