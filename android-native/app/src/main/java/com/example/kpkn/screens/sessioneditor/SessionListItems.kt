package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.sessionModuleEdge

internal fun SessionPart.isUncategorizedPart(): Boolean {
    val normalized = name.trim().lowercase()
    return normalized in setOf("sin categoría", "sin categoria", "sin grupo")
}

/**
 * Flattened list model for the session editor [LazyColumn].
 * Each exercise or superset block is its own lazy item for proper virtualization.
 *
 * Los ítems de un módulo (grupo, espacio de cardio o ejercicios sueltos) llevan su [SessionModuleEdge]: el primero pone el borde de arriba
 * y el último el de abajo. Lo reparte [buildSessionListItems]. Los demás ítems van sobre el negro y no lo llevan.
 */
sealed class SessionListItem {
    abstract val stableKey: String

    data object Hero : SessionListItem() {
        override val stableKey: String = "hero"
    }

    data class LooseSuperset(
        val groupId: String,
        val memberIds: List<String>,
        val indexInLoose: Int,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "loose-superset-$groupId"
    }

    data class LooseExercise(
        val exerciseId: String,
        val indexInLoose: Int,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "loose-exercise-$exerciseId"
    }

    data class PartHeader(
        val partId: String,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "part-header-$partId"
    }

    data class PartSuperset(
        val partId: String,
        val groupId: String,
        val memberIds: List<String>,
        val indexInPart: Int,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "part-$partId-superset-$groupId"
    }

    data class PartExercise(
        val partId: String,
        val exerciseId: String,
        val indexInPart: Int,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "part-$partId-exercise-$exerciseId"
    }

    /** Footer button to add an exercise into an expanded group/part. */
    data class PartAddExercise(
        val partId: String,
        val edge: SessionModuleEdge = SessionModuleEdge.ONLY,
    ) : SessionListItem() {
        override val stableKey: String = "part-add-$partId"
    }

    data object StrengthAddActions : SessionListItem() {
        override val stableKey: String = "strength-add-actions"
    }

    /** Pie del espacio de cardio. Su borde lo decide quien pinta el módulo (sin cardios, va solo). */
    data object CardioAddActions : SessionListItem() {
        override val stableKey: String = "cardio-add-actions"
    }

    /** Zero-height gap after loose exercises for the end-of-loose drop indicator (N9). */
    data object LooseEndGap : SessionListItem() {
        override val stableKey: String = "loose-end-gap"
    }

    /** Rótulo «SIN GRUPO» encima de los ejercicios sueltos, solo cuando la sesión tiene grupos (ER2b). */
    data object LooseGroupLabel : SessionListItem() {
        override val stableKey: String = "loose-group-label"
    }

    data class CardioDivider(
        val showCardioFirst: Boolean = false,
        val canMove: Boolean = false,
    ) : SessionListItem() {
        override val stableKey: String = "cardio-divider"
    }

    data class StrengthDivider(
        val showCardioFirst: Boolean = false,
        val canMove: Boolean = false,
    ) : SessionListItem() {
        override val stableKey: String = "strength-divider"
    }

    /**
     * La colocación del cardio, «Al inicio / Al final» (ED-A · A2, ED-D): existe siempre que la sesión tiene Fuerza y Cardio, con o sin
     * ejercicios. Es el único control de colocación de la lista: los rótulos FUERZA/CARDIO no ofrecen «Mover…» (añadido del
     * orquestador de ED-D).
     */
    data object CardioPlacement : SessionListItem() {
        override val stableKey: String = "cardio-placement"
    }
}

/**
 * La sesión tiene espacio de fuerza con algo dentro: ejercicios sueltos o algún grupo de fuerza. Una sesión solo de cardio no (ED-A · A2b):
 * sin fuerza no hay «Al inicio / Al final» que elegir, ni en la lista ni en el cardio desplegado.
 */
internal fun Session.hasStrengthContent(): Boolean =
    exercises.isNotEmpty() || parts.any { !it.isCardioPart() && !it.isUncategorizedPart() }

fun buildSessionListItems(
    session: Session,
    collapsedPartIds: Set<String> = emptySet(),
    showStrengthDivider: Boolean = false,
    /** Preferencia explícita inicio/final; necesaria cuando solo hay part cardio. */
    cardioAtStart: Boolean? = null,
    /** El cardio se eligió en la sesión vacía y todavía no tiene ningún cardio: su espacio existe aunque esté vacío (ED-A · A2). */
    cardioSpaceCommitted: Boolean = false,
): List<SessionListItem> {
    val items = mutableListOf<SessionListItem>()
    items += SessionListItem.Hero

    val groupedParts = session.parts.filterNot { it.isUncategorizedPart() }
    val strengthParts = groupedParts.filterNot { it.isCardioPart() }
    val cardioParts = groupedParts.filter { it.isCardioPart() }
    val hasStrengthContent = session.exercises.isNotEmpty() || strengthParts.isNotEmpty()
    // Fuerza existe si tiene contenido o se eligió en la sesión vacía; el cardio, si tiene su parte o se eligió.
    val hasStrength = hasStrengthContent || showStrengthDivider
    val hasCardio = cardioParts.isNotEmpty() || cardioSpaceCommitted
    val hasBothSpaces = hasStrength && hasCardio
    // Solo cardio (ED-A · A2b): sin colocación ni espacio de fuerza. La opción de Fuerza queda debajo, como pendiente.
    val cardioOnly = hasCardio && !hasStrength

    val showCardioFirst = when {
        !hasCardio -> false
        cardioOnly -> true
        cardioAtStart != null -> cardioAtStart
        session.cardioFirst -> true
        strengthParts.isNotEmpty() && groupedParts.firstOrNull()?.isCardioPart() == true -> true
        else -> false
    }
    val emitStrengthDivider = !cardioOnly && (showStrengthDivider || showCardioFirst || hasCardio)

    // Un solo control para la colocación (ED-D · Añadido del orquestador): el selector «Al inicio / Al final» está siempre a la vista
    // cuando la sesión tiene Fuerza y Cardio, y los rótulos FUERZA/CARDIO no ofrecen «Mover…». La función no se pierde: se mueve con
    // el selector, y el chip del cardio desplegado sigue funcionando igual.
    if (hasBothSpaces) items += SessionListItem.CardioPlacement

    if (showCardioFirst) {
        // --- 1. BLOQUE CARDIO ARRIBA ---
        if (!cardioOnly) items += SessionListItem.CardioDivider(showCardioFirst = true, canMove = false)
        cardioParts.forEach { part ->
            appendCardioPartExercises(items, part)
        }
        items += SessionListItem.CardioAddActions

        // --- 2. BLOQUE FUERZA ABAJO ---
        if (emitStrengthDivider) items += SessionListItem.StrengthDivider(showCardioFirst = true, canMove = false)
        if (session.exercises.isNotEmpty()) {
            if (strengthParts.isNotEmpty()) items += SessionListItem.LooseGroupLabel
            appendExerciseItems(
                items = items,
                exercises = session.exercises,
                session = session,
                partId = null,
            )
            items += SessionListItem.LooseEndGap
        } else if (strengthParts.isNotEmpty()) {
            items += SessionListItem.LooseEndGap
        }
        strengthParts.forEach { part ->
            appendPartItems(items, part, session, collapsedPartIds)
        }
        items += SessionListItem.StrengthAddActions
    } else {
        // --- 1. BLOQUE FUERZA ARRIBA ---
        if (emitStrengthDivider) {
            items += SessionListItem.StrengthDivider(showCardioFirst = false, canMove = false)
        }
        if (session.exercises.isNotEmpty()) {
            if (strengthParts.isNotEmpty()) items += SessionListItem.LooseGroupLabel
            appendExerciseItems(
                items = items,
                exercises = session.exercises,
                session = session,
                partId = null,
            )
            items += SessionListItem.LooseEndGap
        } else if (strengthParts.isNotEmpty()) {
            // Empty loose with groups: gap before first header for end-of-loose / synthetic zone cue.
            items += SessionListItem.LooseEndGap
        }
        strengthParts.forEach { part ->
            appendPartItems(items, part, session, collapsedPartIds)
        }
        items += SessionListItem.StrengthAddActions

        // --- 2. BLOQUE CARDIO ABAJO (si existe) ---
        if (hasCardio) {
            items += SessionListItem.CardioDivider(showCardioFirst = false, canMove = false)
            cardioParts.forEach { part ->
                appendCardioPartExercises(items, part)
            }
            items += SessionListItem.CardioAddActions
        }
    }

    return items.withModuleEdges(session)
}

private fun appendCardioPartExercises(
    items: MutableList<SessionListItem>,
    part: SessionPart,
) {
    part.exercises.forEachIndexed { index, exercise ->
        items += SessionListItem.PartExercise(part.id, exercise.id, index)
    }
}

private fun appendPartItems(
    items: MutableList<SessionListItem>,
    part: SessionPart,
    session: Session,
    collapsedPartIds: Set<String>,
) {
    items += SessionListItem.PartHeader(part.id)
    if (part.id !in collapsedPartIds) {
        appendExerciseItems(
            items = items,
            exercises = part.exercises,
            session = session,
            partId = part.id,
        )
        items += SessionListItem.PartAddExercise(part.id)
    }
}

private fun appendExerciseItems(
    items: MutableList<SessionListItem>,
    exercises: List<Exercise>,
    session: Session,
    partId: String?,
) {
    exercises.forEachIndexed { index, exercise ->
        val supersetGroupId = exercise.supersetGroupRefOrLegacyId()
        if (supersetGroupId != null) {
            val isFirstMember = exercises.firstOrNull {
                it.supersetGroupRefOrLegacyId() == supersetGroupId
            }?.id == exercise.id
            if (!isFirstMember) return@forEachIndexed
            val supersetGroup = session.allSupersetGroups().firstOrNull { it.id == supersetGroupId }
            val members = SupersetRules.orderedMembers(session, supersetGroupId)
                .filter { member -> exercises.any { it.id == member.id } }
            if (supersetGroup != null && members.size >= 2) {
                val memberIds = members.map { it.id }
                if (partId == null) {
                    items += SessionListItem.LooseSuperset(supersetGroupId, memberIds, index)
                } else {
                    items += SessionListItem.PartSuperset(partId, supersetGroupId, memberIds, index)
                }
                return@forEachIndexed
            }
        }
        if (partId == null) {
            items += SessionListItem.LooseExercise(exercise.id, index)
        } else {
            items += SessionListItem.PartExercise(partId, exercise.id, index)
        }
    }
}

/**
 * Reparte los bordes de cada módulo entre sus ítems consecutivos. Un módulo es un grupo de fuerza, el espacio de cardio (sus cardios y su
 * pie) o los ejercicios sueltos. Los divisores, los huecos y las acciones de fuerza van sobre el negro y no tienen borde.
 */
private fun List<SessionListItem>.withModuleEdges(session: Session): List<SessionListItem> {
    val keys = map { moduleKeyOf(it, session) }
    return mapIndexed { index, item ->
        val key = keys[index] ?: return@mapIndexed item
        val edge = sessionModuleEdge(
            first = index == 0 || keys[index - 1] != key,
            last = index == lastIndex || keys[index + 1] != key,
        )
        when (item) {
            is SessionListItem.LooseExercise -> item.copy(edge = edge)
            is SessionListItem.LooseSuperset -> item.copy(edge = edge)
            is SessionListItem.PartHeader -> item.copy(edge = edge)
            is SessionListItem.PartExercise -> item.copy(edge = edge)
            is SessionListItem.PartSuperset -> item.copy(edge = edge)
            is SessionListItem.PartAddExercise -> item.copy(edge = edge)
            else -> item
        }
    }
}

/** Clave del módulo al que pertenece un ítem, o null si el ítem va sobre el negro. */
private fun moduleKeyOf(item: SessionListItem, session: Session): String? = when (item) {
    is SessionListItem.LooseExercise, is SessionListItem.LooseSuperset -> LOOSE_MODULE_KEY
    is SessionListItem.PartHeader -> "part:${item.partId}"
    is SessionListItem.PartAddExercise -> "part:${item.partId}"
    is SessionListItem.PartExercise -> if (isCardioPartId(session, item.partId)) CARDIO_MODULE_KEY else "part:${item.partId}"
    is SessionListItem.PartSuperset -> if (isCardioPartId(session, item.partId)) CARDIO_MODULE_KEY else "part:${item.partId}"
    SessionListItem.CardioAddActions -> CARDIO_MODULE_KEY
    else -> null
}

private const val LOOSE_MODULE_KEY = "loose"
private const val CARDIO_MODULE_KEY = "cardio"

private fun isCardioPartId(session: Session, partId: String): Boolean =
    session.parts.firstOrNull { it.id == partId }?.isCardioPart() == true

fun findListIndexForExercise(items: List<SessionListItem>, exerciseId: String): Int {
    return items.indexOfFirst { item ->
        when (item) {
            is SessionListItem.LooseExercise -> item.exerciseId == exerciseId
            is SessionListItem.LooseSuperset -> exerciseId in item.memberIds
            is SessionListItem.PartExercise -> item.exerciseId == exerciseId
            is SessionListItem.PartSuperset -> exerciseId in item.memberIds
            else -> false
        }
    }
}

/**
 * Maps an exercise id to the LazyColumn index used by SessionEditorScreen:
 * [0]=Hero, [1..n]=scrollable content.
 */
fun lazyColumnIndexForExercise(
    scrollableItems: List<SessionListItem>,
    exerciseId: String,
): Int {
    val contentIndex = findListIndexForExercise(scrollableItems, exerciseId)
    return if (contentIndex >= 0) contentIndex + 1 else -1
}
