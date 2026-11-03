package com.example.kpkn.screens.workout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/*
 * ROADMAP DE LA SESIÓN EN VIVO · modelo puro (Pulido v1, paquete `lv`, escenas `lv-road-*`).
 * Todo lo que decide el roadmap sin pintar nada: qué variante cabe (A «Calma» 112 dp o B «Con ruta visible» 132 dp), cómo se agrupan los
 * ejercicios en segmentos de la tira, a qué segmento pertenece un dedo, de dónde viene el texto nuevo de la banda 1 y qué dice cada banda.
 */

/** A «Calma»: dos bandas. B «Con ruta visible»: dos bandas y la tira de ruta entre ellas. */
internal enum class LiveRoadmapVariant(val height: Dp, val band1: Dp) {
    A(112.dp, 32.dp),
    B(132.dp, 30.dp),
}

/** Desde este alto de ventana (en dp) cabe la variante B; por debajo, A. Es la misma medida que usa la tarjeta alta ([LiveCardTallScreenHeightDp]). */
internal const val LiveRoadmapTallScreenHeightDp = LiveCardTallScreenHeightDp

/**
 * La variante que cabe en una ventana de [windowHeightDp] dp de alto (`Configuration.screenHeightDp`): B desde 820 dp, A en las demás.
 * El Flip5 mide 880 dp de alto y sale B.
 */
internal fun liveRoadmapVariantFor(windowHeightDp: Int): LiveRoadmapVariant =
    if (windowHeightDp >= LiveRoadmapTallScreenHeightDp) LiveRoadmapVariant.B else LiveRoadmapVariant.A

/** Un ejercicio o una superserie de la ruta: lo que es un segmento de la tira y una fila de la «Ruta». */
internal data class LiveRouteGroup(val id: String?, val members: List<Exercise>) {
    val superset: Boolean get() = id != null && members.size > 1
    val cardio: Boolean get() = members.first().isCardio
}

/** Los ejercicios agrupados en superseries (un grupo por superserie, en el orden del primer miembro). */
internal fun liveRouteGroups(exercises: List<Exercise>, supersetGroups: List<SupersetGroup>): List<LiveRouteGroup> {
    val groupByMember = buildMap {
        supersetGroups.forEach { group -> group.exerciseOrder.forEach { put(it, group.id) } }
        exercises.forEach { ex -> ex.supersetGroupRefOrLegacyId()?.let { put(ex.id, it) } }
    }
    val emitted = mutableSetOf<String>()
    return exercises.mapNotNull { exercise ->
        val id = groupByMember[exercise.id]
        when {
            id == null -> LiveRouteGroup(null, listOf(exercise))
            emitted.add(id) -> LiveRouteGroup(id, exercises.filter { groupByMember[it.id] == id })
            else -> null
        }
    }
}

/** Un segmento de la tira (B): ancho = series (el cardio pesa 2), relleno = lo registrado. */
internal data class LiveRoadmapSegment(
    val index: Int,
    val group: LiveRouteGroup,
    val weight: Int,
    val sets: Int,
    val done: Int,
    val current: Boolean,
) {
    val cardio: Boolean get() = group.cardio
    val fill: Float get() = if (sets > 0) (done.toFloat() / sets).coerceIn(0f, 1f) else 0f
    /** Nombre que dice la banda 1 mientras este segmento es el provisional del arrastre. */
    fun name(display: (Exercise) -> String): String = display(group.members.first())
    fun description(display: (Exercise) -> String): String {
        val names = group.members.joinToString(" + ", transform = display)
        return names + (if (cardio) ", cardio" else ", $done de $sets series") + (if (current) ", actual" else "")
    }
}

internal fun liveRoadmapSegments(
    groups: List<LiveRouteGroup>,
    currentExerciseId: String?,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
): List<LiveRoadmapSegment> = groups.mapIndexed { index, group ->
    val sets = WorkoutStepRules.totalRoadmapSlotsForExercises(group.members, omittedSetKeys)
    val done = WorkoutStepRules.completedRoadmapSlotsForExercises(group.members, completedSets, omittedSetKeys)
    val weight = group.members.fold(0) { acc, member -> acc + if (member.isCardio) 2 else WorkoutStepRules.totalRoadmapSlotsForExercise(member, omittedSetKeys) }
    LiveRoadmapSegment(index, group, weight.coerceAtLeast(1), sets, done, group.members.any { it.id == currentExerciseId })
}

/**
 * El segmento que corresponde a una coordenada x del dedo: el que la contiene o, fuera de todos, el más cercano. [bounds] = (izquierda, derecha)
 * de cada segmento en la misma escala que [x]. Devuelve -1 si no hay segmentos.
 */
internal fun liveRoadmapSegmentAt(x: Float, bounds: List<Pair<Float, Float>>): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    bounds.forEachIndexed { i, (left, right) ->
        val d = if (x in left..right) 0f else minOf(kotlin.math.abs(x - left), kotlin.math.abs(x - right))
        if (d < bestDistance) { bestDistance = d; best = i }
    }
    return best
}

/** Zona de suelta de la tira: la tira más 16 dp a cada lado y 22 dp por encima y por debajo de su centro (44 dp de alto). */
internal fun liveRoadmapReleaseInside(xDp: Float, yDp: Float, leftDp: Float, rightDp: Float, centerYDp: Float): Boolean =
    xDp >= leftDp - 16f && xDp <= rightDp + 16f && kotlin.math.abs(yDp - centerYDp) <= 22f

/** Hacia dónde entra el texto nuevo de la banda 1 (`lv2-sl`: desde la izquierda, `lv2-sr`: desde la derecha, `lv2-su`: desde abajo). */
internal enum class LiveRoadmapSlide { Left, Right, Up }

/** Dónde está el roadmap: ejercicio, serie y qué ocupa la banda 1. */
internal data class LiveRoadmapPosition(val exerciseId: String?, val setNumber: Int, val phase: LiveRoadmapPhase)

internal enum class LiveRoadmapPhase { Set, Prep, Rest }

/**
 * El desplazamiento con que entra el texto de la banda 1 al pasar de [from] a [to] (sin animación de salida): ‹ entra por la izquierda,
 * › por la derecha, y un cambio de ejercicio, la entrada o la salida del descanso, de abajo. `null` = nada cambió.
 */
internal fun liveRoadmapSlide(from: LiveRoadmapPosition?, to: LiveRoadmapPosition): LiveRoadmapSlide? = when {
    from == null || from == to -> null
    from.phase == LiveRoadmapPhase.Rest && to.phase != LiveRoadmapPhase.Rest -> LiveRoadmapSlide.Right
    to.phase == LiveRoadmapPhase.Rest -> LiveRoadmapSlide.Up
    from.exerciseId != to.exerciseId -> LiveRoadmapSlide.Up
    from.phase != to.phase -> if (to.phase == LiveRoadmapPhase.Prep) LiveRoadmapSlide.Left else LiveRoadmapSlide.Right
    to.setNumber < from.setNumber -> LiveRoadmapSlide.Left
    else -> LiveRoadmapSlide.Right
}

/** El número de la serie de un paso del roadmap («S2», «S2 L», «2»): 2. `null` si el paso no es una serie. */
internal fun liveRoadmapSetNumber(label: String): Int? = Regex("\\d+").find(label)?.value?.toIntOrNull()

/** Lado de un paso unilateral: « izq.» o « der.» (vacío si el paso no tiene lado). */
internal fun liveRoadmapSideSuffix(label: String): String = when {
    label.endsWith(" L") -> " izq."
    label.endsWith(" R") -> " der."
    else -> ""
}

/**
 * «Serie 2 de 3» (con lado en los unilaterales: «Serie 2 izq. de 3»). En preparación, «Aproximación 1 de 2» o «Movilidad 1 de 3»; la versión
 * corta («Aprox. 1/2») es la de letra grande.
 */
internal fun liveRoadmapLead(phase: LiveRoadmapPhase, step: LiveSessionLineStep?, exercise: Exercise?, restLabel: String?): String = when (phase) {
    LiveRoadmapPhase.Rest -> "Descanso" + (restLabel?.let { " $it" } ?: "")
    LiveRoadmapPhase.Prep -> {
        val mobility = step?.description == "Movilidad"
        val total = if (mobility) exercise?.mobilitySeries?.size else exercise?.warmupSets?.size
        val label = if (mobility) "Movilidad" else "Aproximación"
        if (total == null || total <= 0) label else "$label ${liveRoadmapPrepIndex(step?.preparationProgress, total)} de $total"
    }
    LiveRoadmapPhase.Set -> {
        val total = exercise?.sets?.count { !it.isEmptySlot } ?: 0
        val number = step?.let { liveRoadmapSetNumber(it.label) } ?: 1
        "Serie $number${step?.let { liveRoadmapSideSuffix(it.label) }.orEmpty()}" + if (total > 0) " de $total" else ""
    }
}

internal fun liveRoadmapLeadShort(phase: LiveRoadmapPhase, step: LiveSessionLineStep?, exercise: Exercise?, restLabel: String?): String {
    if (phase != LiveRoadmapPhase.Prep) return liveRoadmapLead(phase, step, exercise, restLabel)
    val mobility = step?.description == "Movilidad"
    val total = if (mobility) exercise?.mobilitySeries?.size else exercise?.warmupSets?.size
    val label = if (mobility) "Mov." else "Aprox."
    return if (total == null || total <= 0) label else "$label ${liveRoadmapPrepIndex(step?.preparationProgress, total)}/$total"
}

/** La aproximación (o movimiento) de turno: las ya hechas + 1, sin pasar del total. */
internal fun liveRoadmapPrepIndex(progress: Float?, total: Int): Int =
    (((progress ?: 0f) * total).let { Math.round(it) } + 1).coerceIn(1, total)

/** ¿El paso es una serie (y no movilidad, aproximación, descanso ni la insignia de una ronda)? */
internal fun liveRoadmapIsSetStep(step: LiveSessionLineStep): Boolean =
    step.description != "Movilidad" && step.description != "Aproximación" && step.description != "Descanso" && !step.key.startsWith("round-")

/** El número de la serie en curso de la tarjeta (la que resalta la «Ruta»), o `null` si el paso activo no es una serie. */
internal fun liveRoadmapActiveSetNumber(snapshot: LiveSetStepperSnapshot?): Int? = snapshot?.let { state ->
    liveSessionLineSteps(state, state.activePageIndex).firstOrNull { it.active && liveRoadmapIsSetStep(it) }
        ?.let { liveRoadmapSetNumber(it.label) }
}
