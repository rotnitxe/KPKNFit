package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules

/**
 * Los destinos de un arrastre de ejercicio (ED-A · A3). Cada uno lleva su señal de soltado en la lista y su frase en el aviso de arriba:
 * - [REORDER]: dentro del mismo grupo (o de «Sin grupo»), solo cambia de sitio.
 * - [BETWEEN_GROUPS]: de un grupo a otro grupo.
 * - [OUT_OF_GROUP]: sale de su grupo y queda en «Sin grupo».
 * - [INTO_GROUP]: entra en un grupo desde «Sin grupo».
 * - [INTO_SUPERSET]: entra en una superserie ya formada, con su letra y su posición.
 * - [OUT_OF_SUPERSET]: un miembro que se suelta fuera de su superserie.
 * - [INVALID]: fuera de la zona de fuerza o de cardio, o una superserie que ya está llena.
 * - [NONE]: aún no hay destino, o soltarlo ahora lo dejaría donde estaba.
 */
enum class SessionEditorDropKind {
    REORDER,
    BETWEEN_GROUPS,
    OUT_OF_GROUP,
    INTO_GROUP,
    INTO_SUPERSET,
    OUT_OF_SUPERSET,
    INVALID,
    NONE,
}

/**
 * Lo que pasaría si se soltara ahora: [kind], el destino ([targetPartId] nulo = «Sin grupo»; [targetGroupId] si es una superserie) y la
 * frase [hint] del aviso de arriba. [supersetLetter] es la posición del miembro nuevo («A3») cuando el destino es una superserie.
 */
data class SessionEditorDropPlan(
    val kind: SessionEditorDropKind,
    val targetPartId: String?,
    val targetGroupId: String?,
    val targetIndex: Int?,
    val hint: String,
    val supersetLetter: String? = null,
)

/** Máximo de ejercicios de una superserie (el del motor de movimiento). */
internal const val DROP_MAX_SUPERSET_MEMBERS = 4

private const val LOOSE_ID = SessionEditorDragController.LOOSE_PART_ID

/** Nombre de un contenedor en las frases: «Sin grupo» para los sueltos; el nombre del grupo, o «Grupo» si está en blanco. */
internal fun dropContainerLabel(session: Session, partId: String?): String {
    if (partId == null || partId == LOOSE_ID) return "Sin grupo"
    return session.parts.firstOrNull { it.id == partId }?.name?.trim()?.ifBlank { null } ?: "Grupo"
}

/**
 * Qué pasaría si el ejercicio [exerciseId] (que sale de [sourcePartId], con `__loose__` para «Sin grupo») se soltara en [targetPartId] en
 * la posición [targetIndex] de su lista, o sobre la superserie [targetGroupId]. Función pura: la lista lo pinta y las pruebas la miden.
 *
 * [scope] decide si se mueve el bloque entero de una superserie o solo el miembro. [outOfRange] es la zona no válida del controlador.
 */
fun planExerciseDrop(
    session: Session,
    sourcePartId: String?,
    exerciseId: String?,
    scope: ExerciseDragScope,
    targetPartId: String?,
    targetIndex: Int?,
    targetGroupId: String?,
    outOfRange: Boolean,
): SessionEditorDropPlan {
    if (outOfRange) {
        return SessionEditorDropPlan(SessionEditorDropKind.INVALID, null, null, null, "Zona no válida")
    }
    if (exerciseId == null || sourcePartId == null || targetPartId == null || targetIndex == null) {
        return SessionEditorDropPlan(SessionEditorDropKind.NONE, null, null, null, "")
    }
    val sourceList = SessionEditorDragController.exerciseListFor(session, SessionEditorDragController.toNullablePartId(sourcePartId))
    val targetList = SessionEditorDragController.exerciseListFor(session, SessionEditorDragController.toNullablePartId(targetPartId))
    val dragged = sourceList.firstOrNull { it.id == exerciseId }
        ?: return SessionEditorDropPlan(SessionEditorDropKind.NONE, null, null, null, "")
    val draggedGroup = dragged.supersetGroupRefOrLegacyId()
    val movingIds: Set<String> = if (scope == ExerciseDragScope.BLOCK && draggedGroup != null) {
        sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroup }.map { it.id }.toSet()
    } else {
        setOf(exerciseId)
    }
    val resolvedTarget = SessionEditorDragController.toNullablePartId(targetPartId)

    if (targetGroupId != null && scope == ExerciseDragScope.INDIVIDUAL) {
        val members = SupersetRules.orderedMembers(session, targetGroupId).filter { it.id !in movingIds }
        if (draggedGroup == targetGroupId) {
            return SessionEditorDropPlan(
                kind = SessionEditorDropKind.REORDER,
                targetPartId = resolvedTarget,
                targetGroupId = targetGroupId,
                targetIndex = targetIndex,
                hint = "Al soltar cambiará de orden dentro de la superserie.",
            )
        }
        if (members.size + movingIds.size > DROP_MAX_SUPERSET_MEMBERS) {
            return SessionEditorDropPlan(
                kind = SessionEditorDropKind.INVALID,
                targetPartId = resolvedTarget,
                targetGroupId = targetGroupId,
                targetIndex = targetIndex,
                hint = "La superserie ya tiene $DROP_MAX_SUPERSET_MEMBERS ejercicios.",
            )
        }
        val before = members.count { member ->
            val index = targetList.indexOfFirst { it.id == member.id }
            index in 0 until targetIndex
        }
        val letter = "A${before + 1}"
        val neighbour = when {
            before > 0 -> "después de «${members[before - 1].dropLabel()}»"
            members.isNotEmpty() -> "antes de «${members[0].dropLabel()}»"
            else -> ""
        }
        return SessionEditorDropPlan(
            kind = SessionEditorDropKind.INTO_SUPERSET,
            targetPartId = resolvedTarget,
            targetGroupId = targetGroupId,
            targetIndex = targetIndex,
            hint = "Al soltar entrará como $letter en la superserie" + if (neighbour.isEmpty()) "." else ", $neighbour.",
            supersetLetter = letter,
        )
    }

    if (sourcePartId != targetPartId) {
        val from = dropContainerLabel(session, sourcePartId)
        val to = dropContainerLabel(session, targetPartId)
        return when {
            resolvedTarget == null -> SessionEditorDropPlan(
                kind = SessionEditorDropKind.OUT_OF_GROUP,
                targetPartId = null,
                targetGroupId = null,
                targetIndex = targetIndex,
                hint = "Al soltar saldrá de «$from» y quedará sin grupo.",
            )
            SessionEditorDragController.toNullablePartId(sourcePartId) == null -> SessionEditorDropPlan(
                kind = SessionEditorDropKind.INTO_GROUP,
                targetPartId = resolvedTarget,
                targetGroupId = null,
                targetIndex = targetIndex,
                hint = "Al soltar entrará en «$to».",
            )
            else -> SessionEditorDropPlan(
                kind = SessionEditorDropKind.BETWEEN_GROUPS,
                targetPartId = resolvedTarget,
                targetGroupId = null,
                targetIndex = targetIndex,
                hint = "Al soltar pasará de «$from» a «$to».",
            )
        }
    }

    // Mismo contenedor: cambia de sitio dentro de él. Soltar justo antes o justo después de sí mismo lo deja donde estaba.
    val sourceIndex = sourceList.indexOfFirst { it.id == exerciseId }
    val blockSize = movingIds.size.coerceAtLeast(1)
    val container = dropContainerLabel(session, targetPartId)
    if (scope == ExerciseDragScope.INDIVIDUAL && draggedGroup != null) {
        return SessionEditorDropPlan(
            kind = SessionEditorDropKind.OUT_OF_SUPERSET,
            targetPartId = resolvedTarget,
            targetGroupId = null,
            targetIndex = targetIndex,
            hint = "Al soltar saldrá de la superserie y quedará en «$container».",
        )
    }
    if (targetIndex == sourceIndex || targetIndex == sourceIndex + blockSize) {
        return SessionEditorDropPlan(
            kind = SessionEditorDropKind.NONE,
            targetPartId = resolvedTarget,
            targetGroupId = null,
            targetIndex = targetIndex,
            hint = "Al soltar quedará donde estaba.",
        )
    }
    val movingBefore = sourceList.take(targetIndex.coerceIn(0, sourceList.size)).count { it.id in movingIds }
    val position = (targetIndex - movingBefore + 1).coerceAtLeast(1)
    return SessionEditorDropPlan(
        kind = SessionEditorDropKind.REORDER,
        targetPartId = resolvedTarget,
        targetGroupId = null,
        targetIndex = targetIndex,
        hint = "Al soltar quedará en la posición $position de «$container».",
    )
}

private fun Exercise.dropLabel(): String = name.trim().ifBlank { "Ejercicio" }
