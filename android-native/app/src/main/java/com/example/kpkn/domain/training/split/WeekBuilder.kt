package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.SupersetVisualPlacement
import com.example.kpkn.data.models.WarmupExercise

/**
 * Arma las sesiones de una semana a partir de las unidades que le tocan a cada día.
 *
 * Cada día queda así:
 * - los ejercicios van por **nivel**: compuestos principales primero, luego el resto de compuestos, aislamiento y remates
 *   (core, pantorrilla, agarre); dentro de un nivel se alternan empuje y tirón para no cargar un mismo músculo seguido;
 * - cada ejercicio **conserva todo lo suyo** (series, repeticiones, descansos, aproximaciones, movilidad, ids) y la
 *   **superserie** sigue junta; el calentamiento general de una sesión de origen viaja con su primer ejercicio;
 * - la estructura de partes se rehace como la del plan de origen: los ejercicios que estaban en una parte («Principal»,
 *   «Accesorios») vuelven a una parte con ese nombre y los sueltos siguen sueltos; el cardio y la movilidad de una parte
 *   son bloques enteros (cardio al final, o al principio si venía así).
 */
internal object WeekBuilder {

    private class PartRun(
        val name: String,
        val color: String?,
        val isMobilityGroup: Boolean,
        val exercises: MutableList<Exercise>,
    )

    /** Ids de las sesiones nuevas: las de origen, en orden; más días que sesiones → ids derivados de la semana. */
    fun sessionIds(originals: List<Session>, weekId: String, count: Int): List<String> {
        val used = hashSetOf<String>()
        return List(count) { index -> uniqueId(originals.getOrNull(index)?.id ?: "$weekId-d${index + 1}", used) }
    }

    fun build(
        originals: List<Session>,
        weekId: String,
        days: List<DayInfo>,
        unitsByDay: List<List<MovableUnit>>,
        mainDays: Set<Int>,
        description: (DayInfo) -> String,
    ): List<Session> {
        val ids = sessionIds(originals, weekId, days.size)
        return days.mapIndexed { index, day ->
            buildDay(
                id = ids[index],
                day = day,
                units = unitsByDay[index],
                base = originals.getOrNull(index) ?: originals.lastOrNull(),
                isMain = index in mainDays,
                description = description(day),
            )
        }
    }

    /** Orden de los ejercicios de un día (ver la KDoc del objeto). Estable y determinista. */
    fun orderUnits(units: List<MovableUnit>): List<MovableUnit> {
        val base = units.sortedWith(
            compareBy<MovableUnit>({ it.tier }, { it.patternRank }, { it.originSession }, { it.originPosition }),
        )
        val counters = HashMap<Pair<Int, SplitGroup>, Int>()
        val ranked = base.map { unit ->
            val key = unit.tier to unit.group
            val position = counters[key] ?: 0
            counters[key] = position + 1
            unit to position
        }
        return ranked.sortedWith(
            compareBy<Pair<MovableUnit, Int>>(
                { it.first.tier },
                { it.second },
                { it.first.patternRank },
                { it.first.originSession },
                { it.first.originPosition },
            ),
        ).map { it.first }
    }

    private fun buildDay(
        id: String,
        day: DayInfo,
        units: List<MovableUnit>,
        base: Session?,
        isMain: Boolean,
        description: String,
    ): Session {
        val originOrder = compareBy<MovableUnit>({ it.originSession }, { it.originPosition })
        val exerciseUnits = orderUnits(units.filter { it.block == null && it.exercises.isNotEmpty() })
        val leadingBlocks = units.filter { it.kind == UnitKind.MOBILITY && it.block != null && it.blockLeading }.sortedWith(originOrder)
        val trailingBlocks = units.filter { it.kind == UnitKind.MOBILITY && it.block != null && !it.blockLeading }.sortedWith(originOrder)
        val cardioBlocks = units.filter { it.kind == UnitKind.CARDIO && it.block != null }.sortedWith(originOrder)
        val warmupUnits = units.filter { it.warmup.isNotEmpty() }.sortedWith(originOrder)

        val usedExerciseIds = hashSetOf<String>()
        val usedGroupIds = hashSetOf<String>()
        val loose = mutableListOf<Exercise>()
        val runs = mutableListOf<PartRun>()
        val groups = mutableListOf<SupersetGroup>()

        exerciseUnits.forEach { unit ->
            val group = unit.superset
            val newGroupId = group?.let { uniqueId(it.id, usedGroupIds) }
            val idMap = LinkedHashMap<String, String>()
            unit.exercises.forEachIndexed { index, exercise ->
                val newId = uniqueId(exercise.id, usedExerciseIds)
                idMap[exercise.id] = newId
                var adjusted = if (newId == exercise.id) exercise else exercise.copy(id = newId)
                if (group != null && newGroupId != null && newGroupId != group.id) {
                    adjusted = adjusted.copy(
                        supersetId = if (adjusted.supersetId == group.id) newGroupId else adjusted.supersetId,
                        supersetGroupRef = if (adjusted.supersetGroupRef == group.id) newGroupId else adjusted.supersetGroupRef,
                    )
                }
                val origin = unit.origins.getOrNull(index)
                if (origin == null) {
                    loose += adjusted
                } else {
                    val last = runs.lastOrNull()
                    if (last != null && last.name == origin.name && last.isMobilityGroup == origin.isMobilityGroup) {
                        last.exercises += adjusted
                    } else {
                        runs += PartRun(origin.name, origin.color, origin.isMobilityGroup, mutableListOf(adjusted))
                    }
                }
            }
            if (group != null && newGroupId != null) {
                groups += group.copy(
                    id = newGroupId,
                    exerciseOrder = remapOrder(group.exerciseOrder, idMap),
                    visualPlacement = group.visualPlacement?.let { placement ->
                        SupersetVisualPlacement(partId = null, anchorExerciseId = placement.anchorExerciseId?.let { idMap[it] })
                    },
                )
            }
        }

        val usedPartIds = hashSetOf<String>()
        val strengthParts = runs.mapIndexed { index, run ->
            SessionPart(
                id = uniqueId("$id#part:$index", usedPartIds),
                name = run.name,
                exercises = run.exercises.toList(),
                color = run.color,
                isMobilityGroup = run.isMobilityGroup,
            )
        }
        fun blockParts(blocks: List<MovableUnit>): List<SessionPart> =
            blocks.mapNotNull { it.block }.map { part -> part.copy(id = uniqueId(part.id, usedPartIds)) }
        val leadingParts = blockParts(leadingBlocks)
        val trailingParts = blockParts(trailingBlocks)
        val cardioParts = blockParts(cardioBlocks)

        val cardioFirst = cardioBlocks.any { it.originCardioFirst }
        val core = leadingParts + strengthParts + trailingParts
        val parts = if (cardioFirst) cardioParts + core else core + cardioParts

        // El ancla visual de una superserie apunta a la parte nueva que contiene su ejercicio ancla.
        val placedGroups = groups.map { group ->
            val anchor = group.visualPlacement?.anchorExerciseId ?: return@map group
            val partId = strengthParts.firstOrNull { part -> part.exercises.any { it.id == anchor } }?.id
            group.copy(visualPlacement = SupersetVisualPlacement(partId = partId, anchorExerciseId = anchor))
        }

        return Session(
            id = id,
            name = day.title,
            description = description,
            exercises = loose,
            warmup = mergeWarmups(warmupUnits.flatMap { it.warmup }),
            parts = parts,
            background = base?.background,
            coverStyle = base?.coverStyle,
            dayOfWeek = day.weekday,
            scheduleLabel = day.label,
            assignedDays = listOf(day.weekday),
            isMainSession = isMain,
            focus = base?.focus,
            supersetGroups = placedGroups,
            lastModifiedAtMs = base?.lastModifiedAtMs ?: 0L,
            requirement = SessionRequirement.REQUIRED,
            origin = SessionOrigin.USER_DRAFT,
            cardioFirst = cardioFirst,
            persistedRuleDefaults = base?.persistedRuleDefaults,
        )
    }

    private fun remapOrder(order: List<String>, idMap: Map<String, String>): List<String> {
        val mapped = order.mapNotNull { idMap[it] }
        return mapped + idMap.values.filter { it !in mapped }
    }

    /** Une los calentamientos generales de varias sesiones de origen sin repetir los iguales y con ids únicos. */
    private fun mergeWarmups(all: List<WarmupExercise>): List<WarmupExercise> {
        val seenContent = hashSetOf<WarmupExercise>()
        val usedIds = hashSetOf<String>()
        val merged = mutableListOf<WarmupExercise>()
        all.forEach { warmup ->
            if (!seenContent.add(warmup.copy(id = ""))) return@forEach
            val id = uniqueId(warmup.id, usedIds)
            merged += if (id == warmup.id) warmup else warmup.copy(id = id)
        }
        return merged
    }

    /** [id] si aún no se usó; si no, el mismo con un sufijo `~2`, `~3`… Registra el resultado en [used]. */
    fun uniqueId(id: String, used: MutableSet<String>): String {
        if (used.add(id)) return id
        var n = 2
        while (!used.add("$id~$n")) n++
        return "$id~$n"
    }
}
