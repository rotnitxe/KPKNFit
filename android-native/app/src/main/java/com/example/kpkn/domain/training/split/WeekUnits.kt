package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.training.CompositionTaxonomy
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.VolumeCalculator

/** Qué es un bloque que se mueve entero de un día a otro. */
internal enum class UnitKind { STRENGTH, CARDIO, MOBILITY }

/** Parte de sesión de la que venía un ejercicio; `null` en [MovableUnit.origins] = ejercicio suelto en la sesión. */
internal data class PartOrigin(val name: String, val color: String?, val isMobilityGroup: Boolean)

/**
 * Lo que el redistribuidor mueve de un día a otro: un ejercicio (con sus series, descansos, aproximaciones y movilidad,
 * que son campos suyos y viajan con él), una **superserie completa** (sus ejercicios nunca se separan), una parte de
 * cardio entera, un bloque de movilidad de una parte o el calentamiento general de una sesión (que viaja con el primer
 * ejercicio de fuerza de su sesión de origen).
 */
internal data class MovableUnit(
    /** Identidad estable dentro de la semana: sirve para repetir el mismo reparto en otra semana del mismo plan. */
    val key: String,
    val kind: UnitKind,
    /** Ejercicios en su orden de origen; vacío en los bloques de cardio/movilidad enteros. */
    val exercises: List<Exercise>,
    val origins: List<PartOrigin?>,
    val superset: SupersetGroup?,
    /** Parte entera (cardio, o movilidad propia de una parte) que viaja como un bloque. */
    val block: SessionPart?,
    /** Un bloque de movilidad que iba antes de la fuerza en su sesión de origen. */
    val blockLeading: Boolean,
    val originSession: Int,
    val originPosition: Int,
    val originCardioFirst: Boolean,
    /** `Session.warmup` de la sesión de origen, si este es su primer ejercicio de fuerza. */
    val warmup: List<WarmupExercise>,
    val traits: List<ExerciseTraits?>,
    /** Identidad de cada ejercicio de fuerza (su definición de catálogo, o su configuración o nombre): dos unidades con la misma son el mismo ejercicio. */
    val exerciseKeys: Set<String>,
    /** Aporte muscular fusionado de los ejercicios de fuerza (para el encaje con cada día); null = sin clasificar. */
    val muscles: Map<KpknMuscleGroup, Double>?,
    val primaryAtoms: Set<KpknMuscleGroup>,
    val group: SplitGroup,
    val pattern: PatternFamily?,
    /** Cadena cinética del primer ejercicio de fuerza (la que mira un día «anterior» o «posterior»). */
    val chain: KineticChain?,
    /** 0 = compuesto principal, 1 = compuesto, 2 = aislamiento, 3 = remate (core, pantorrilla, agarre…), 5 = cardio; -1 = movilidad suelta. */
    val tier: Int,
    val patternRank: Int,
    val isHeavy: Boolean,
    val sets: Int,
    val seconds: Int,
) {
    val exerciseCount: Int get() = if (block != null) block.exercises.size else exercises.size

    /** Cuenta para el mínimo de ejercicios por día (el cardio y la movilidad no son ejercicios de fuerza). */
    val strengthExerciseCount: Int get() = if (kind == UnitKind.STRENGTH) exercises.size else 0

    val isClassified: Boolean get() = muscles != null
}

/** Reúne los ejercicios de una semana en unidades movibles. */
internal object WeekGather {

    /** Tiempo general de calentamiento que el estimador suma una vez por sesión con fuerza (se resta de cada unidad). */
    const val GENERAL_WARMUP_SECONDS = 180

    private val HEAVY_PATTERNS = setOf(
        PatternFamily.SQUAT, PatternFamily.HINGE, PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH,
        PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL,
    )
    private val BARBELL_LIKE = setOf("barbell", "smith_machine", "safety_bar", "hex_bar", "t_bar", "ez_bar")

    private class Entry(val exercise: Exercise, val origin: PartOrigin?, val position: Int)

    fun gather(week: ProgramWeek, resolver: ExerciseTraitResolver): List<MovableUnit> =
        week.sessions.flatMapIndexed { index, session -> gatherSession(session, index, resolver) }

    private fun isCardio(exercise: Exercise) = exercise.cardioDetails != null

    private fun isMobilityOnly(exercise: Exercise) =
        !isCardio(exercise) && exercise.sets.isEmpty() &&
            (exercise.mobilitySeries.isNotEmpty() || exercise.mobilityConfig != null)

    private fun isStrength(exercise: Exercise) = !isCardio(exercise) && !isMobilityOnly(exercise)

    private fun gatherSession(session: Session, sessionIndex: Int, resolver: ExerciseTraitResolver): List<MovableUnit> {
        val partExerciseIds = session.parts.flatMap { it.exercises }.mapTo(hashSetOf<String>()) { it.id }
        val seen = hashSetOf<String>()
        val entries = mutableListOf<Entry>()
        var position = 0
        // Una sesión antigua puede repetir en `exercises` lo que ya está en sus partes: se cuenta una sola vez.
        session.exercises.forEach { exercise ->
            if (exercise.id !in partExerciseIds && seen.add(exercise.id)) entries += Entry(exercise, null, position++)
        }
        val strengthParts = session.parts.filterNot { it.isCardioPart() }
        strengthParts.forEach { part ->
            val origin = PartOrigin(part.name, part.color, part.isMobilityGroup)
            part.exercises.forEach { exercise ->
                if (seen.add(exercise.id)) entries += Entry(exercise, origin, position++)
            }
        }

        val groups = session.allSupersetGroups().associateBy { it.id }
        val leadExerciseId = entries.firstOrNull { isStrength(it.exercise) }?.exercise?.id
            ?: entries.firstOrNull()?.exercise?.id
        val firstExercisePartIndex = session.parts.indexOfFirst { !it.isCardioPart() && it.exercises.isNotEmpty() }
        var warmupPlaced = session.warmup.isEmpty()

        val units = mutableListOf<MovableUnit>()
        val consumed = hashSetOf<String>()
        entries.forEach { entry ->
            if (entry.exercise.id in consumed) return@forEach
            val ref = entry.exercise.supersetGroupRefOrLegacyId()
            val group = ref?.let { groups[it] }
            val members = if (group != null) {
                entries.filter { it.exercise.supersetGroupRefOrLegacyId() == ref }
            } else {
                listOf(entry)
            }
            members.forEach { consumed += it.exercise.id }
            val carriesWarmup = !warmupPlaced && members.any { it.exercise.id == leadExerciseId }
            if (carriesWarmup) warmupPlaced = true
            units += exerciseUnit(
                sessionIndex = sessionIndex,
                members = members,
                group = group,
                warmup = if (carriesWarmup) session.warmup else emptyList(),
                cardioFirst = session.cardioFirst,
                resolver = resolver,
            )
        }

        // Cardio entero y movilidad propia de cada parte: bloques que viajan completos.
        session.parts.forEachIndexed { partIndex, part ->
            if (part.isCardioPart()) {
                units += blockUnit(sessionIndex, partIndex, part, leading = false, cardio = true, cardioFirst = session.cardioFirst)
            } else if (part.mobilitySeries.isNotEmpty() || part.mobilityConfig != null ||
                (part.isMobilityGroup && part.exercises.isEmpty())
            ) {
                val mobility = part.copy(
                    exercises = emptyList(),
                    isMobilityGroup = true,
                    isCardioGroup = false,
                    targetDurationMinutes = null,
                )
                val leading = firstExercisePartIndex < 0 || partIndex < firstExercisePartIndex
                units += blockUnit(sessionIndex, partIndex, mobility, leading = leading, cardio = false, cardioFirst = session.cardioFirst)
            }
        }

        if (!warmupPlaced) {
            // La sesión solo tenía calentamiento general y bloques: viaja con su primera unidad o, sin ninguna, sola.
            if (units.isEmpty()) {
                units += MovableUnit(
                    key = "$sessionIndex:warmup",
                    kind = UnitKind.MOBILITY,
                    exercises = emptyList(), origins = emptyList(), superset = null, block = null, blockLeading = true,
                    originSession = sessionIndex, originPosition = -1, originCardioFirst = session.cardioFirst,
                    warmup = session.warmup, traits = emptyList(), exerciseKeys = emptySet(), muscles = null, primaryAtoms = emptySet(),
                    group = SplitGroup.OTHER, pattern = null, chain = null, tier = -1, patternRank = 9, isHeavy = false, sets = 0,
                    seconds = secondsOf(emptyList(), null, null, session.warmup),
                )
            } else {
                val first = units.first()
                units[0] = first.copy(
                    warmup = session.warmup,
                    seconds = secondsOf(first.exercises, first.superset, first.block, session.warmup),
                )
            }
        }
        return units
    }

    private fun exerciseUnit(
        sessionIndex: Int,
        members: List<Entry>,
        group: SupersetGroup?,
        warmup: List<WarmupExercise>,
        cardioFirst: Boolean,
        resolver: ExerciseTraitResolver,
    ): MovableUnit {
        val exercises = members.map { it.exercise }
        val traits = exercises.map { resolver.traitsOf(it) }
        val kind = when {
            exercises.any { isStrength(it) } -> UnitKind.STRENGTH
            exercises.any { isCardio(it) } -> UnitKind.CARDIO
            else -> UnitKind.MOBILITY
        }
        val strengthTraits = exercises.indices
            .filter { isStrength(exercises[it]) }
            .mapNotNull { traits[it] }
        val muscles = if (kind == UnitKind.STRENGTH && strengthTraits.isNotEmpty()) mergedMuscles(strengthTraits) else null
        val lead = strengthTraits.firstOrNull()
        val minReps = exercises.flatMap { it.sets }
            .mapNotNull { set -> set.targetRepsRange?.max ?: set.targetReps }
            .minOrNull()
        val isHeavy = kind == UnitKind.STRENGTH && strengthTraits.any { t ->
            t.isCompound && t.pattern in HEAVY_PATTERNS &&
                (t.equipmentId in BARBELL_LIKE || t.axialLoad >= 0.5 || (minReps != null && minReps <= 6))
        }
        val roles = exercises.mapNotNull { it.slotRole }
        val tier = tierOf(kind, roles, lead, isHeavy)
        val first = members.first()
        val configurationKey = first.exercise.catalogConfigurationId ?: first.exercise.name
        return MovableUnit(
            key = "$sessionIndex:${first.position}:$configurationKey",
            kind = kind,
            exercises = exercises,
            origins = members.map { it.origin },
            superset = group,
            block = null,
            blockLeading = false,
            originSession = sessionIndex,
            originPosition = first.position,
            originCardioFirst = cardioFirst,
            warmup = warmup,
            traits = traits,
            exerciseKeys = if (kind == UnitKind.STRENGTH) {
                exercises.indices
                    .filter { isStrength(exercises[it]) }
                    .mapTo(linkedSetOf<String>()) { i -> traits[i]?.definitionId ?: exerciseKeyOf(exercises[i]) }
            } else {
                emptySet()
            },
            muscles = muscles,
            primaryAtoms = muscles?.filterValues { it >= ExerciseTraits.PRIMARY }?.keys.orEmpty(),
            group = when {
                kind != UnitKind.STRENGTH -> SplitGroup.OTHER
                lead != null -> lead.group
                else -> SplitGroup.OTHER
            },
            pattern = lead?.pattern,
            chain = lead?.chain,
            tier = tier,
            patternRank = patternRankOf(lead?.pattern),
            isHeavy = isHeavy,
            sets = exercises.sumOf { VolumeCalculator.countEffectiveSets(it.sets) },
            seconds = secondsOf(exercises, group, null, warmup),
        )
    }

    private fun blockUnit(
        sessionIndex: Int,
        partIndex: Int,
        part: SessionPart,
        leading: Boolean,
        cardio: Boolean,
        cardioFirst: Boolean,
    ): MovableUnit = MovableUnit(
        key = "$sessionIndex:block:$partIndex:${part.name}",
        kind = if (cardio) UnitKind.CARDIO else UnitKind.MOBILITY,
        exercises = emptyList(),
        origins = emptyList(),
        superset = null,
        block = part,
        blockLeading = leading,
        originSession = sessionIndex,
        originPosition = 10_000 + partIndex,
        originCardioFirst = cardioFirst,
        warmup = emptyList(),
        traits = emptyList(),
        exerciseKeys = emptySet(),
        muscles = null,
        primaryAtoms = emptySet(),
        group = SplitGroup.OTHER,
        pattern = null,
        chain = null,
        tier = if (cardio) 5 else -1,
        patternRank = 9,
        isHeavy = false,
        sets = 0,
        seconds = secondsOf(emptyList(), null, part, emptyList()),
    )

    /** Identidad de un ejercicio para saber si dos unidades son el mismo: su definición de catálogo, o su configuración, o su nombre. */
    internal fun exerciseKeyOf(exercise: Exercise): String =
        exercise.catalogDefinitionId?.takeIf { it.isNotBlank() }
            ?: exercise.catalogConfigurationId?.takeIf { it.isNotBlank() }
            ?: exercise.exerciseId?.takeIf { it.isNotBlank() }
            ?: SplitText.normalize(exercise.name)

    /** Aporte máximo de cada músculo entre los ejercicios de la unidad (el primario de uno manda sobre el secundario de otro). */
    private fun mergedMuscles(traits: List<ExerciseTraits>): Map<KpknMuscleGroup, Double> {
        val merged = LinkedHashMap<KpknMuscleGroup, Double>()
        traits.forEach { t -> t.muscles.forEach { (atom, weight) -> merged.merge(atom, weight) { a, b -> maxOf(a, b) } } }
        return merged
    }

    private fun tierOf(kind: UnitKind, roles: List<SlotRole>, lead: ExerciseTraits?, isHeavy: Boolean): Int {
        when (kind) {
            UnitKind.CARDIO -> return 5
            UnitKind.MOBILITY -> return -1
            UnitKind.STRENGTH -> Unit
        }
        if (SlotRole.T1_MAIN in roles || SlotRole.SPEED in roles) return 0
        val supplemental = SlotRole.T2_SUPPLEMENTAL in roles || SlotRole.TECHNIQUE in roles
        if (lead == null) return if (supplemental) 1 else 2
        return when {
            isHeavy -> 0
            supplemental -> 1
            lead.isCompound -> 1
            CompositionTaxonomy.isFinisherFamily(lead.pattern) -> 3
            else -> 2
        }
    }

    /** Orden entre compuestos del mismo nivel: pierna grande, bisagra, empujes y luego tirones. */
    private fun patternRankOf(pattern: PatternFamily?): Int = when (pattern) {
        PatternFamily.SQUAT -> 0
        PatternFamily.HINGE -> 1
        PatternFamily.HIP_EXTENSION -> 2
        PatternFamily.HORIZONTAL_PUSH -> 3
        PatternFamily.VERTICAL_PUSH -> 4
        PatternFamily.HORIZONTAL_PULL -> 5
        PatternFamily.VERTICAL_PULL -> 6
        else -> 9
    }

    /**
     * Segundos que aporta la unidad según el estimador común de sesiones, sin el calentamiento general (que el estimador
     * suma una vez por sesión con fuerza y no depende de qué unidades lleve). Así los segundos de las unidades de un día
     * se suman: `estimate(día) = Σ segundos + calentamiento general`.
     */
    fun secondsOf(exercises: List<Exercise>, group: SupersetGroup?, block: SessionPart?, warmup: List<WarmupExercise>): Int {
        val probe = Session(
            id = "probe",
            name = "probe",
            exercises = exercises,
            parts = listOfNotNull(block),
            supersetGroups = listOfNotNull(group),
            warmup = warmup,
        )
        val total = SessionDurationEstimator.estimate(probe).totalSeconds
        val hasResistance = exercises.any { !isCardio(it) && (it.sets.isNotEmpty() || it.warmupSets.isNotEmpty()) }
        return (if (hasResistance) total - GENERAL_WARMUP_SECONDS else total).coerceAtLeast(0)
    }
}
