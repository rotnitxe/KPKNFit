package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/** Un ejercicio que cambia de grupo (parte de la sesión) al aplicar un orden. `null` = suelto, sin parte. */
data class ReorderGroupChange(
    val exerciseId: String,
    val fromGroup: String?,
    val toGroup: String?,
)

/** Resultado de ordenar: la sesión ya con el orden pedido y los ejercicios que, para lograrlo, cambian de grupo. */
data class ReorderPlan(
    val session: Session,
    val groupChanges: List<ReorderGroupChange>,
)

/**
 * Reordenar los ejercicios de una sesión en vivo (LV4, 10.1). Una sola fuente de verdad para el cajón de «Reordenar», el arrastre del
 * modo dios, «Mover al final» del relator, el deshacer del plan y la copia del cambio al programa.
 *
 * El orden que ve la persona es la lista plana `Session.allExercises()`: primero los ejercicios sueltos, después cada parte (grupo) en su
 * sitio y, al final, las partes de cardio (al principio si `cardioFirst`). Esa secuencia de «contenedores» es fija; ordenar es repartir
 * los ejercicios entre ellos. Reglas:
 * - El orden pedido SE APLICA: el orden plano de los ejercicios pedidos acaba siendo el pedido. Si para lograrlo un ejercicio tiene que
 *   pasar de un grupo a otro, pasa (el menor número posible de ejercicios) y queda en [ReorderPlan.groupChanges].
 * - Nada se pierde: un ejercicio que no viene en el pedido (omitido, por ejemplo) se conserva en su grupo.
 * - El cardio no se mezcla con la fuerza: cada zona conserva su sitio.
 * - Una superserie es una unidad: sus miembros quedan juntos, en el sitio de su primer miembro y en el orden en que se pidieron.
 */
object LiveSessionReorder {

    /**
     * Ordena [session] según [orderedExerciseIds] (los ejercicios que la persona ve, en el orden que quiere).
     *
     * [homePartNames] (opcional) dice a qué parte pertenece cada ejercicio, por nombre, cuando no es la que tiene ahora (el deshacer del
     * plan devuelve cada ejercicio a la parte que tenía al empezar). Sin él, cada ejercicio parte de su grupo actual.
     */
    fun plan(
        session: Session,
        orderedExerciseIds: List<String>,
        homePartNames: Map<String, String>? = null,
    ): ReorderPlan {
        val all = session.allExercises()
        val byId = LinkedHashMap<String, Exercise>()
        all.forEach { byId.putIfAbsent(it.id, it) }
        // Una sesión con ids repetidos no se toca: reordenarla perdería ejercicios.
        if (byId.size != all.size) return ReorderPlan(session, emptyList())
        val requestedRaw = orderedExerciseIds.distinct().filter { it in byId }
        if (requestedRaw.isEmpty()) return ReorderPlan(session, emptyList())

        val slots = containerSequence(session)
        val looseSlot = slots.indexOfFirst { it.partPosition == null }
        val currentSlot = HashMap<String, Int>()
        session.exercises.forEach { currentSlot[it.id] = looseSlot }
        session.parts.forEachIndexed { position, part ->
            val slot = slots.indexOfFirst { it.partPosition == position }
            part.exercises.forEach { currentSlot[it.id] = slot }
        }
        fun homeSlot(id: String): Int {
            val current = currentSlot.getValue(id)
            val name = homePartNames?.get(id) ?: return current
            if (name.isBlank()) return if (slots[current].cardioZone) current else looseSlot
            val byName = slots.indexOfFirst { it.partPosition != null && it.name == name }
            return if (byName >= 0) byName else current
        }

        // 1) Cada zona en su sitio: la fuerza antes que el cardio (al revés con `cardioFirst`).
        val strength = requestedRaw.filter { !slots[homeSlot(it)].cardioZone }
        val cardio = requestedRaw.filter { slots[homeSlot(it)].cardioZone }
        val zoned = if (session.cardioFirst) cardio + strength else strength + cardio
        // 2) Las superseries, juntas.
        val requested = keepSupersetsTogether(zoned, byId)

        // 3) Los ejercicios que no vienen en el pedido van detrás del que tenían delante.
        val requestedSet = requested.toSet()
        val followers = HashMap<String, MutableList<String>>()
        val leadingHidden = ArrayList<String>()
        var lastVisible: String? = null
        for (exercise in all) {
            if (exercise.id in requestedSet) {
                lastVisible = exercise.id
            } else {
                val anchor = lastVisible
                if (anchor == null) leadingHidden.add(exercise.id) else followers.getOrPut(anchor) { ArrayList() }.add(exercise.id)
            }
        }

        // 4) A qué contenedor va cada ejercicio pedido (por zona: la fuerza y el cardio no se cruzan).
        val assigned = HashMap<String, Int>()
        for (zone in listOf(requested.filter { !slots[homeSlot(it)].cardioZone }, requested.filter { slots[homeSlot(it)].cardioZone })) {
            val homes = IntArray(zone.size) { homeSlot(zone[it]) }
            val result = assignSlots(homes)
            for (position in zone.indices) assigned[zone[position]] = result[position]
        }

        // 5) Reparto en listas, en el orden pedido; los omitidos siguen en su contenedor.
        val lists = List(slots.size) { ArrayList<Exercise>() }
        val fullOrder = ArrayList<String>(all.size)
        fullOrder.addAll(leadingHidden)
        for (id in requested) {
            fullOrder.add(id)
            followers[id]?.let { fullOrder.addAll(it) }
        }
        for (id in fullOrder) lists[assigned[id] ?: currentSlot.getValue(id)].add(byId.getValue(id))

        val reorderedParts = session.parts.mapIndexed { position, part ->
            val slot = slots.indexOfFirst { it.partPosition == position }
            if (lists[slot] == part.exercises) part else part.copy(exercises = lists[slot].toList())
        }
        val reorderedLoose = if (lists[looseSlot] == session.exercises) session.exercises else lists[looseSlot].toList()

        // 6) El orden de los miembros de una superserie sigue al que se pidió, si la persona lo cambió.
        val requestedMembers = requested.groupBy { byId.getValue(it).supersetGroupRefOrLegacyId() }
        val reorderedGroups = session.supersetGroups.map { group ->
            val members = requestedMembers[group.id].orEmpty()
            if (members.size < 2) return@map group
            val memberSet = members.toSet()
            val originalRelative = all.map { it.id }.filter { it in memberSet }
            if (originalRelative == members) group
            else group.copy(exerciseOrder = members + group.exerciseOrder.filter { it !in memberSet })
        }

        val updated = session.copy(exercises = reorderedLoose, parts = reorderedParts, supersetGroups = reorderedGroups)
        val changes = requested.mapNotNull { id ->
            val from = currentSlot.getValue(id)
            val to = assigned.getValue(id)
            if (from == to) null else ReorderGroupChange(id, slots[from].name, slots[to].name)
        }
        return ReorderPlan(updated, changes)
    }

    /**
     * Mueve una «unidad» de la lista del cajón de reordenar. Una superserie (miembros seguidos) es una unidad: al llegar al borde de su
     * bloque, la fila salta TODO el bloque por encima o por debajo del vecino; dentro del bloque cambia el orden de sus miembros. Un
     * ejercicio suelto salta un vecino entero (suelto o bloque). [delta] es -1 (subir) o 1 (bajar).
     */
    fun moveUnit(
        order: List<String>,
        groupOf: (String) -> String?,
        fromIndex: Int,
        delta: Int,
    ): List<String> {
        if (fromIndex !in order.indices || delta == 0) return order
        val direction = if (delta < 0) -1 else 1
        // Las unidades: ejercicios sueltos o rachas seguidas de la misma superserie.
        val units = ArrayList<MutableList<String>>()
        for (id in order) {
            val group = groupOf(id)
            val last = units.lastOrNull()
            if (group != null && last != null && groupOf(last.first()) == group) last.add(id) else units.add(mutableListOf(id))
        }
        var unitIndex = 0
        var offset = fromIndex
        while (offset >= units[unitIndex].size) {
            offset -= units[unitIndex].size
            unitIndex++
        }
        val unit = units[unitIndex]
        if (unit.size > 1) {
            val target = offset + direction
            if (target in unit.indices) {
                val moved = unit.removeAt(offset)
                unit.add(target, moved)
                return units.flatten()
            }
        }
        val neighbor = unitIndex + direction
        if (neighbor !in units.indices) return order
        units.removeAt(unitIndex)
        units.add(neighbor, unit)
        return units.flatten()
    }

    private class Slot(val partPosition: Int?, val name: String?, val cardioZone: Boolean)

    /** La secuencia fija de contenedores de `allExercises()`: sueltos, partes de fuerza y partes de cardio (cardio primero si `cardioFirst`). */
    private fun containerSequence(session: Session): List<Slot> {
        val strength = ArrayList<Slot>()
        strength.add(Slot(partPosition = null, name = null, cardioZone = false))
        val cardio = ArrayList<Slot>()
        session.parts.forEachIndexed { position, part ->
            if (part.isCardioPart()) cardio.add(Slot(position, part.name, cardioZone = true))
            else strength.add(Slot(position, part.name, cardioZone = false))
        }
        return if (session.cardioFirst) cardio + strength else strength + cardio
    }

    private fun keepSupersetsTogether(order: List<String>, byId: Map<String, Exercise>): List<String> {
        fun groupOf(id: String) = byId.getValue(id).supersetGroupRefOrLegacyId()
        if (order.none { groupOf(it) != null }) return order
        val emitted = HashSet<String>()
        val result = ArrayList<String>(order.size)
        for (id in order) {
            val group = groupOf(id)
            if (group == null) {
                result.add(id)
            } else if (emitted.add(group)) {
                result.addAll(order.filter { groupOf(it) == group })
            }
        }
        return result
    }

    /**
     * Contenedor de cada ejercicio de una zona, en el orden pedido, para que la secuencia no baje nunca (los grupos conservan su sitio).
     * Primero la regla de siempre: un ejercicio solo entre dos del mismo grupo se une a ese grupo. Si el reparto sigue sin ser
     * representable, se conserva la racha más larga de ejercicios que no cambian de grupo y los demás se unen al grupo del que tienen
     * delante (el primero, al del que tiene detrás).
     */
    private fun assignSlots(homes: IntArray): IntArray {
        val size = homes.size
        if (size == 0) return homes
        val sandwiched = homes.copyOf()
        val runs = ArrayList<IntRange>()
        var start = 0
        while (start < size) {
            var end = start
            while (end + 1 < size && homes[end + 1] == homes[start]) end++
            runs.add(start..end)
            start = end + 1
        }
        runs.forEachIndexed { runIndex, run ->
            if (run.first != run.last) return@forEachIndexed
            val before = runs.getOrNull(runIndex - 1) ?: return@forEachIndexed
            val after = runs.getOrNull(runIndex + 1) ?: return@forEachIndexed
            val neighborGroup = homes[before.first]
            if (neighborGroup == homes[after.first] && neighborGroup != homes[run.first]) sandwiched[run.first] = neighborGroup
        }
        if ((1 until size).all { sandwiched[it - 1] <= sandwiched[it] }) return sandwiched

        val length = IntArray(size) { 1 }
        val previous = IntArray(size) { -1 }
        for (i in 0 until size) {
            for (j in 0 until i) {
                if (sandwiched[j] <= sandwiched[i] && length[j] + 1 > length[i]) {
                    length[i] = length[j] + 1
                    previous[i] = j
                }
            }
        }
        var tail = 0
        for (i in 0 until size) if (length[i] >= length[tail]) tail = i
        val kept = BooleanArray(size)
        var cursor = tail
        while (cursor >= 0) {
            kept[cursor] = true
            cursor = previous[cursor]
        }
        val firstKept = (0 until size).first { kept[it] }
        val result = IntArray(size)
        var lastKept = sandwiched[firstKept]
        for (i in 0 until size) {
            if (kept[i]) lastKept = sandwiched[i]
            result[i] = lastKept
        }
        return result
    }
}
