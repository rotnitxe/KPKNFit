package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.LiftVariants

/*
 * Variantes de los básicos y marcas con repeticiones («tu punto de partida», punto 10 del plan).
 *
 * - La variante elegida ocupa los huecos PRINCIPALES (MAIN) de su patrón y va primera entre las candidatas, aunque no esté en
 *   el pool del patrón (entonces se crea con los requisitos de su familia). Los huecos secundarios siguen como hoy.
 * - La marca es DE la variante elegida: el 1RM equivalente del levantamiento es esa cifra dividida por el factor de la variante,
 *   y cada candidata que lleva la marca usa ese equivalente por su propio factor. Sin variante, el factor es 1,0 (como antes).
 * - Con repeticiones (un PR «kg × reps»), la marca se convierte antes con la fórmula híbrida.
 * - Con marca de press militar en Fuerza y masa o en Powerbuilding, el press con barra es el empuje vertical principal de la
 *   semana: lo eligen también los huecos secundarios y accesorios de empuje vertical ([markedVerticalPushId]).
 */
internal object VariantPools {

    /** Rango fijo de la variante elegida en su hueco principal: va antes que cualquier otra candidata. */
    private const val MAIN_RANK = -100.0

    /** Variantes que no llevan la marca del levantamiento (mancuernas y búlgaras): `takesMark = false` en `LiftVariants`. */
    private val WITHOUT_MARK = setOf(
        "bulgarian_split_squat__dumbbells", "bulgarian_split_squat__barbell",
        "bench_press__dumbbells", "military_press__dumbbells",
    )

    /**
     * Requisitos de las variantes que no están en ningún pool: la búlgara con barra apoya el pie de atrás en un banco o un cajón
     * pliométrico, igual que la de mancuernas del pool (`MovementPools`). Es el mismo criterio que la oferta de `LiftVariants`.
     */
    private val CREATED_REQUIRES: Map<String, List<String>> = mapOf(
        "bulgarian_split_squat__barbell" to listOf("bench|plyo_box"),
    )

    /** Nivel con el que se ofrece una variante que no está en el pool: las variantes se piden desde el nivel intermedio. */
    private val CREATED_MIN_LEVEL = RoutineLevel.INTERMEDIATE

    /** Patrón del hueco principal de cada levantamiento con variantes; null si el levantamiento no tiene variantes. */
    fun patternOf(lift: LiftMark): RoutinePattern? = when (lift) {
        LiftMark.SQUAT -> RoutinePattern.SQUAT
        LiftMark.DEADLIFT -> RoutinePattern.HINGE
        LiftMark.BENCH -> RoutinePattern.HORIZONTAL_PUSH
        LiftMark.OVERHEAD_PRESS -> RoutinePattern.VERTICAL_PUSH
        LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK -> null
    }

    /** La variante de siempre del levantamiento (la que elige hoy el generador); null si el levantamiento no tiene variantes. */
    fun principalOf(lift: LiftMark): String? = if (patternOf(lift) == null) null else LiftVariants.defaultFor(lift)

    /** La primera entrada que cita cada id en los pools generales y de disciplina. */
    private val entryById: Map<String, PoolEntry> by lazy {
        val map = LinkedHashMap<String, PoolEntry>()
        MovementPools.byPattern.values.forEach { groups -> groups.forEach { group -> group.entries.forEach { map.putIfAbsent(it.id, it) } } }
        DisciplinePools.allEntries.forEach { (_, entry) -> map.putIfAbsent(entry.id, entry) }
        map
    }

    /** Factor de la variante respecto de su levantamiento: el `markFactor` de su entrada; 1,0 si no la hay (barra baja, sumo…). */
    fun factorOf(configId: String): Double = entryById[configId]?.markFactor ?: 1.0

    /**
     * 1RM equivalente del levantamiento [lift] a partir de su marca [kg] (con repeticiones, la fórmula híbrida). Sin variante
     * elegida es la marca tal cual, como siempre; con variante, la marca es de esa variante y se divide por su factor.
     */
    fun equivalentOneRm(ctx: GenContext, lift: LiftMark, kg: Double): Double {
        val reps = ctx.request.markReps[lift] ?: 1
        val base = if (reps > 1) StrengthModel.estimate1RM(kg, reps) else kg
        val chosen = ctx.request.liftVariants[lift] ?: return base
        return base / factorOf(chosen)
    }

    /**
     * El PR «kg × reps» de la carga inteligente: solo en el ejercicio de la variante de la marca (la elegida o, sin elegir, la de
     * siempre) y solo con repeticiones. Con 1RM no hay PR (null, como antes).
     */
    fun prFor(ctx: GenContext, candidate: Candidate, markKg: Double?): PrReference? {
        val lift = candidate.mark ?: return null
        val kg = markKg ?: return null
        val reps = ctx.request.markReps[lift] ?: return null
        if (reps <= 1) return null
        val markedId = ctx.request.liftVariants[lift] ?: principalOf(lift)
        return if (candidate.entry.id == markedId) PrReference(weight = kg, reps = reps) else null
    }

    /**
     * La candidata de la variante elegida para un hueco PRINCIPAL de su patrón, con rango fijo (va primera). Null si no hay variante
     * elegida para ese patrón, si el hueco no es principal o lleva etiqueta de disciplina, si ya se usó en la sesión, si la persona
     * la evita, o si el material o el nivel no la permiten.
     */
    fun mainCandidate(
        ctx: GenContext,
        pattern: RoutinePattern,
        role: ItemRole,
        equipment: DayEquipment,
        use: SessionUse,
        avoided: Set<AvoidGroup>,
    ): Candidate? {
        if (role != ItemRole.MAIN) return null
        val lift = ctx.request.liftVariants.keys.firstOrNull { patternOf(it) == pattern } ?: return null
        val configId = ctx.request.liftVariants.getValue(lift)
        val entry = ctx.catalog.entry(configId) ?: return null
        val pool = entryById[configId]
        val kind = pool?.kind ?: ExKind.HEAVY
        if (!ctx.tierAllowed(entry) || entry.id in use.configs || entry.definitionId in use.definitions) return null
        if (ctx.level.ordinal < (pool?.minLevel ?: CREATED_MIN_LEVEL).ordinal) return null
        if (ExerciseSelector.avoidedGroupOf(avoided, entry.definitionId, kind) != null) return null
        // Lo que pide el generador además del filtro compartido: el mismo criterio que la oferta de `LiftVariants`.
        val requires = pool?.requires ?: CREATED_REQUIRES[configId].orEmpty()
        if (!equipment.allows(entry, requires)) return null
        return Candidate(
            entry = entry,
            label = pool?.variant ?: LiftVariants.labelOf(configId),
            kind = kind,
            basic = pool?.basic ?: false,
            mark = if (configId in WITHOUT_MARK) null else lift,
            markFactor = factorOf(configId),
            reps = pool?.reps,
            seconds = null,
            rank = MAIN_RANK,
            groupId = "variant",
            fromLadder = false,
        )
    }

    /**
     * El empuje vertical PRINCIPAL cuando la persona declara la marca de press militar en Fuerza y masa o en Powerbuilding: el press
     * con barra. Sus huecos de empuje vertical (también los secundarios y accesorios) lo eligen antes que las alternativas con
     * mancuernas o máquina. Null en los demás casos (sin marca, con una variante sin marca o en otros modos): como hoy.
     */
    fun markedVerticalPushId(ctx: GenContext, pattern: RoutinePattern): String? {
        if (pattern != RoutinePattern.VERTICAL_PUSH || (ctx.request.marks[LiftMark.OVERHEAD_PRESS] ?: 0.0) <= 0.0) return null
        if (ctx.mode != RoutineMode.GENERAL_STRENGTH_MUSCLE && ctx.mode != RoutineMode.CUSTOM_POWERBUILDING) return null
        val id = ctx.request.liftVariants[LiftMark.OVERHEAD_PRESS] ?: principalOf(LiftMark.OVERHEAD_PRESS) ?: return null
        return id.takeUnless { it in WITHOUT_MARK }
    }
}
