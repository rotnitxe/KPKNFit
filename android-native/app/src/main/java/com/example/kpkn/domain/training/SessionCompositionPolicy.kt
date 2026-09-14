package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe

data class CompositionFinding(
    val severity: CompositionSeverity,
    val rule: String,
    val scope: String,
    val message: String,
)

object SessionCompositionPolicy {
    /**
     * Umbrales de programación razonable, no leyes biomecánicas.
     * Ajustar aquí si un protocolo fiel necesita otro criterio global.
     */
    /** T1/T2 axial slots at or above this factor count toward H5a. */
    const val AXIAL_SLOT_THRESHOLD = 0.6

    /** Sets with factor >= this contribute to H5b spinal budget. */
    const val SPINAL_BUDGET_THRESHOLD = 0.5

    /** Max Σ(effective sets × axialLoadFactor) per session. */
    const val SPINAL_BUDGET_MAX = 12.0

    const val MAX_SESSION_MINUTES = 100
    const val MIN_EXERCISES = 3
    const val MAX_EXERCISES = 9
    /** Pico/taper/descarga: mínimo 6. Resto: 10. */
    const val MIN_EFFECTIVE_SETS = 10
    const val MIN_EFFECTIVE_SETS_PEAK = 6
    const val MAX_EFFECTIVE_SETS = 30
    const val MAX_DIRECT_SETS_PER_MUSCLE = 12
    const val HEAVY_PERCENT = 85.0
    const val T1_REST_FLOOR_SECONDS = 180
    const val T1_HEAVY_REST_FLOOR_SECONDS = 240
    const val T2_REST_FLOOR_SECONDS = 120
    const val T3_COMPOUND_REST_FLOOR_SECONDS = 90
    const val T3_ISOLATION_REST_FLOOR_SECONDS = 45
    const val SPEED_REST_FLOOR_SECONDS = 45
    /** H11: ningún set PERCENT_1RM / PERCENT_DESIRED_MAX por encima del 1RM. */
    const val MAX_PERCENT_1RM = 100.0
    /** H11: techo de Training Max (105 % TM ≈ 94,5 % 1RM si TM = 90 %). */
    const val MAX_PERCENT_TM = 105.0
    /** H11: 3+ reps por encima de este %1RM no es un 1RM, es un error de prescripción. */
    const val MAX_PERCENT_1RM_FOR_TRIPLE_PLUS = 92.0
    /** H11: 102,5 % del top set (Madcow viernes) es un triple sobre el 5RM, no un 1RM. */
    const val MAX_PERCENT_OF_TOP_SET = 105.0

    fun minimumRestSeconds(
        role: SlotRole,
        heavy: Boolean,
        isolation: Boolean,
    ): Int = when (role) {
        SlotRole.SPEED -> SPEED_REST_FLOOR_SECONDS
        SlotRole.T1_MAIN -> if (heavy) T1_HEAVY_REST_FLOOR_SECONDS else T1_REST_FLOOR_SECONDS
        SlotRole.T2_SUPPLEMENTAL, SlotRole.TECHNIQUE -> T2_REST_FLOOR_SECONDS
        SlotRole.T3_ACCESSORY -> if (isolation) T3_ISOLATION_REST_FLOOR_SECONDS else T3_COMPOUND_REST_FLOOR_SECONDS
    }

    private val competitionIds = setOf(
        "low_bar_back_squat__barbell",
        "bench_press__barbell",
        "conventional_deadlift__bilateral__barbell",
    )

    fun evaluateRecipe(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        extraExemptions: List<RecipeCompositionExemption> = emptyList(),
    ): List<CompositionFinding> {
        val exemptions = recipe.exemptions + extraExemptions
        val raw = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                raw += evaluateDay(day, week, metadata, recipe.trainingMaxPercent)
            }
            raw += evaluateWeek(week, recipe, metadata)
        }
        raw += evaluateBlocks(recipe)
        return applyExemptions(raw, exemptions)
    }

    fun evaluateDay(
        day: DayRecipe,
        week: WeekRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        trainingMaxPercent: Double = 0.90,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val scope = "w${week.weekNumber}/${day.label}"
        val resolved = day.slots.map { slot ->
            val meta = metadata.metadata(slot.lift.configurationId)
            Triple(slot, meta, meta?.let { CompositionTaxonomy.familyOf(it.movementPatternId) })
        }
        resolved.forEach { (slot, meta, family) ->
            if (meta == null) {
                findings += hard("META", scope, "Metadato insuficiente para ${slot.lift.configurationId}")
            } else if (family == null && !meta.movementPatternId.isNullOrBlank()) {
                findings += hard("TAXONOMY", scope, "movementPatternId ${meta.movementPatternId} sin PatternFamily")
            }
        }
        findings += checkH1(day, resolved, scope)
        findings += checkH2(day, resolved, scope)
        findings += checkH3(resolved, scope)
        findings += checkH4(day, resolved, scope)
        findings += checkH5(resolved, scope)
        findings += checkH6(resolved, week.blockGoal, scope)
        findings += checkH7(resolved, scope)
        findings += checkH8(resolved, week.blockGoal, scope)
        findings += checkH9(resolved, scope)
        findings += checkH10(resolved, scope)
        findings += checkH11(resolved, scope, trainingMaxPercent)
        findings += checkSoft(resolved, scope)
        return findings
    }

    private fun checkH1(
        day: DayRecipe,
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        fun rank(slot: SlotRecipe, family: PatternFamily?, isolation: Boolean): Int {
            val speedFirst = day.priority == SlotPriority.SPEED && slot.role == SlotRole.SPEED
            return when {
                speedFirst -> 0
                slot.role == SlotRole.SPEED -> 1
                slot.role == SlotRole.T1_MAIN -> 2
                slot.role == SlotRole.TECHNIQUE -> 3
                slot.role == SlotRole.T2_SUPPLEMENTAL -> 4
                slot.role == SlotRole.T3_ACCESSORY && !isolation -> 5
                CompositionTaxonomy.isFinisherFamily(family) -> 7
                else -> 6
            }
        }
        val ranks = resolved.map { (slot, meta, family) ->
            rank(slot, family, CompositionTaxonomy.isIsolation(family, meta?.articulationType, slot.lift.configurationId))
        }
        if (ranks.zipWithNext().any { (a, b) -> b < a }) {
            findings += hard("H1", scope, "Orden de roles inválido (SPEED/T1/T2/T3 compuesto/aislamiento/core)")
        }
        resolved.zipWithNext().forEach { (prev, next) ->
            val (prevSlot, prevMeta, prevFamily) = prev
            val (nextSlot, nextMeta, nextFamily) = next
            val prevIso = CompositionTaxonomy.isIsolation(prevFamily, prevMeta?.articulationType, prevSlot.lift.configurationId)
            val nextCompound = !CompositionTaxonomy.isIsolation(nextFamily, nextMeta?.articulationType, nextSlot.lift.configurationId)
            val sameMuscle = prevMeta?.primaryMuscles?.firstOrNull() != null &&
                prevMeta.primaryMuscles.firstOrNull() == nextMeta?.primaryMuscles?.firstOrNull()
            if (prevIso && nextCompound && sameMuscle && nextSlot.technique != TechniqueModifier.PRE_EXHAUST &&
                prevSlot.technique != TechniqueModifier.PRE_EXHAUST
            ) {
                findings += hard("H1", scope, "Aislamiento antes del compuesto del mismo músculo")
            }
        }
        return findings
    }

    private fun checkH2(
        day: DayRecipe,
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val counted = mutableListOf<Pair<String, PatternFamily>>()
        resolved.forEach { (slot, _, family) ->
            if (family == null) return@forEach
            if (slot.role == SlotRole.T3_ACCESSORY &&
                CompositionTaxonomy.isIsolation(family, null, slot.lift.configurationId)
            ) return@forEach
            if (slot.supplementalOf != null) return@forEach
            if (slot.role == SlotRole.SPEED) return@forEach
            counted += slot.id to family
        }
        val byFamily = counted.groupBy { it.second }
        byFamily.forEach { (family, slots) ->
            if (slots.size > 2) {
                return listOf(hard("H2", scope, "Más de 2 compuestos de la familia $family"))
            }
        }
        return emptyList()
    }

    private fun checkH3(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val dominants = resolved.map { (_, meta, _) -> meta?.primaryMuscles?.firstOrNull() }
        dominants.filterNotNull().groupingBy { it }.eachCount().forEach { (muscle, count) ->
            if (count > 3) findings += hard("H3", scope, "Más de 3 ejercicios con dominante $muscle")
        }
        var streak = 1
        for (i in 1 until resolved.size) {
            val prev = resolved[i - 1]
            val cur = resolved[i]
            val prevDom = prev.second?.primaryMuscles?.firstOrNull()
            val curDom = cur.second?.primaryMuscles?.firstOrNull()
            val pairOk = cur.first.supplementalOf == prev.first.id || prev.first.supplementalOf == cur.first.id
            if (prevDom != null && prevDom == curDom && !pairOk) {
                streak++
                if (streak >= 3) {
                    findings += hard("H3", scope, "3+ ejercicios seguidos del músculo $curDom")
                    break
                }
            } else {
                streak = 1
            }
        }
        return findings
    }

    private fun checkH4(
        day: DayRecipe,
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val groups = resolved.mapNotNull { (slot, meta, _) ->
            val group = meta?.replacementGroup?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            slot to group
        }
        val byGroup = groups.groupBy { it.second }
        val findings = mutableListOf<CompositionFinding>()
        byGroup.forEach { (group, slots) ->
            if (slots.size <= 1) return@forEach
            val ids = slots.map { it.first.id }.toSet()
            val paired = slots.all { (slot, _) ->
                slot.supplementalOf != null && slot.supplementalOf in ids ||
                    slots.any { it.first.supplementalOf == slot.id }
            }
            if (!paired) findings += hard("H4", scope, "Más de un ejercicio del replacementGroup $group")
        }
        return findings
    }

    private fun checkH5(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val axialSlots = resolved.filter { (slot, meta, _) ->
            slot.role in setOf(SlotRole.T1_MAIN, SlotRole.T2_SUPPLEMENTAL) &&
                (meta?.axialLoadFactor ?: 0.0) >= AXIAL_SLOT_THRESHOLD
        }
        if (axialSlots.size > 2) {
            findings += hard("H5a", scope, "Más de 2 slots T1/T2 axiales (>= $AXIAL_SLOT_THRESHOLD)")
        }
        val heavy = axialSlots.count { (slot, _, _) ->
            if (slot.supplementalOf != null) return@count false
            slot.sets.any { set ->
                val pct = set.percent ?: 0.0
                set.isTopSet || pct >= HEAVY_PERCENT || set.loadBasis == LoadBasis.REP_MAX
            }
        }
        if (heavy > 1) {
            findings += hard("H5a", scope, "Más de un axial T1/T2 a >= $HEAVY_PERCENT% o top set")
        }
        val budget = resolved.sumOf { (slot, meta, _) ->
            val factor = meta?.axialLoadFactor ?: 0.0
            if (factor < SPINAL_BUDGET_THRESHOLD) 0.0 else {
                val pairFactor = if (slot.supplementalOf != null) 0.5 else 1.0
                slot.workingSets().size * factor * pairFactor
            }
        }
        if (budget > SPINAL_BUDGET_MAX) {
            findings += hard("H5b", scope, "Presupuesto espinal $budget > $SPINAL_BUDGET_MAX")
        }
        return findings
    }

    private fun checkH6(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        goal: BlockGoal,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val n = resolved.size
        if (n < MIN_EXERCISES || n > MAX_EXERCISES) {
            findings += hard("H6", scope, "Sesión de $n ejercicios (permitido $MIN_EXERCISES-$MAX_EXERCISES)")
        }
        val sets = resolved.sumOf { it.first.workingSets().size }
        val minSets = if (goal in setOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER, BlockGoal.DELOAD)) {
            MIN_EFFECTIVE_SETS_PEAK
        } else {
            MIN_EFFECTIVE_SETS
        }
        if (sets < minSets || sets > MAX_EFFECTIVE_SETS) {
            findings += hard("H6", scope, "Sesión de $sets series efectivas (permitido $minSets-$MAX_EFFECTIVE_SETS)")
        }
        val seconds = resolved.sumOf { (slot, _, _) -> slot.workingSets().size * (slot.restSeconds + 45) }
        if (seconds > MAX_SESSION_MINUTES * 60) {
            findings += hard("H6", scope, "Duración estimada ${seconds / 60} min > $MAX_SESSION_MINUTES")
        }
        return findings
    }

    private fun checkH7(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val byMuscle = mutableMapOf<String, Int>()
        resolved.forEach { (slot, meta, _) ->
            if (slot.supplementalOf != null) return@forEach
            val dominant = meta?.primaryMuscles?.firstOrNull() ?: return@forEach
            byMuscle[dominant] = (byMuscle[dominant] ?: 0) + slot.workingSets().size
        }
        return byMuscle.filter { it.value > MAX_DIRECT_SETS_PER_MUSCLE }.map { (muscle, n) ->
            hard("H7", scope, "$n series directas de $muscle > $MAX_DIRECT_SETS_PER_MUSCLE")
        }
    }

    private fun checkH8(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        goal: BlockGoal,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val fuerzaPico = goal == BlockGoal.INTENSIFICATION || goal == BlockGoal.PEAK ||
            goal == BlockGoal.REALIZATION || goal == BlockGoal.SPECIFICITY
        resolved.forEach { (slot, meta, family) ->
            val work = slot.workingSets()
            val maxReps = work.maxOfOrNull { it.reps ?: it.repsMax ?: 0 } ?: 0
            if (slot.role == SlotRole.T1_MAIN && fuerzaPico && maxReps >= 12) {
                findings += hard("H8", scope, "T1 con $maxReps reps en $goal")
            }
            if (slot.role == SlotRole.T1_MAIN && fuerzaPico) {
                work.forEach { set ->
                    val heavy = (set.percent ?: 0.0) >= HEAVY_PERCENT || set.isTopSet
                    val reps = set.reps ?: set.repsMax ?: 0
                    if (heavy && reps > 0 && reps !in 1..6) {
                        findings += hard("H8", scope, "T1 Fuerza/Pico $reps reps (debe 1-6) en series >= $HEAVY_PERCENT% o top set")
                    }
                }
            }
            if (slot.role == SlotRole.T2_SUPPLEMENTAL) {
                work.forEach { set ->
                    val reps = set.reps ?: set.repsMax ?: 0
                    if (reps > 0 && reps !in 3..8) {
                        findings += soft("H8", scope, "T2 con $reps reps (orientación 3-8)")
                    }
                }
            }
            if (slot.role == SlotRole.T3_ACCESSORY) {
                val isolation = CompositionTaxonomy.isIsolation(family, meta?.articulationType, slot.lift.configurationId)
                work.forEach { set ->
                    if (set.percent != null && set.loadBasis in setOf(
                            LoadBasis.PERCENT_TM, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX,
                        )
                    ) {
                        findings += hard("H8", scope, "T3 ${slot.lift.configurationId} usa % en vez de RPE/RIR")
                    }
                    val reps = set.reps ?: set.repsMax ?: 0
                    if (reps > 0) {
                        val range = if (isolation) 8..20 else 6..12
                        if (reps !in range) {
                            findings += soft("H8", scope, "T3 ${slot.lift.configurationId} $reps reps (orientación $range)")
                        }
                    }
                }
            }
            val isComp = slot.isCompetitionLift || slot.lift.configurationId in competitionIds
            if (isComp && slot.role == SlotRole.T3_ACCESSORY && maxReps >= 10) {
                findings += hard("H8", scope, "Levantamiento de competición como accesorio a $maxReps reps")
            }
        }
        return findings
    }

    private fun checkH9(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        resolved.forEach { (slot, meta, family) ->
            val heavy = slot.sets.any { (it.percent ?: 0.0) >= HEAVY_PERCENT }
            val isolation = CompositionTaxonomy.isIsolation(family, meta?.articulationType, slot.lift.configurationId)
            val minRest = minimumRestSeconds(slot.role, heavy, isolation)
            if (slot.restSeconds < minRest) {
                findings += hard("H9", scope, "${slot.id} descanso ${slot.restSeconds}s < $minRest")
            }
        }
        return findings
    }

    private fun checkH10(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val t1Index = resolved.indexOfFirst { it.first.role == SlotRole.T1_MAIN }
        if (t1Index < 0) return findings
        val t1 = resolved[t1Index]
        val t1Family = t1.third
        resolved.take(t1Index).forEach { (slot, meta, family) ->
            if (CompositionTaxonomy.isFinisherFamily(family)) {
                findings += hard("H10", scope, "Core/gemelo/agarre ${slot.lift.configurationId} antes del T1")
            }
            val id = slot.lift.configurationId
            val t1IsAxial = t1Family == PatternFamily.SQUAT || t1Family == PatternFamily.HINGE
            if (t1IsAxial && (
                    family == PatternFamily.HINGE && slot.role != SlotRole.SPEED ||
                        id.contains("good_morning") ||
                        id.contains("back_extension") ||
                        id.contains("romanian")
                    )
            ) {
                findings += hard("H10", scope, "Isquios/lumbar $id antes del T1 axial")
            }
            if (t1Family == PatternFamily.HINGE && family == PatternFamily.HORIZONTAL_PULL &&
                slot.role != SlotRole.T3_ACCESSORY
            ) {
                findings += hard("H10", scope, "Remo pesado antes del peso muerto")
            }
            if (t1Family == PatternFamily.HINGE && family == PatternFamily.GRIP) {
                findings += hard("H10", scope, "Agarre antes del peso muerto")
            }
        }
        return findings
    }

    /**
     * Techo de intensidad. No es exentable: un 3×5 @ 102 % del 1RM es un error
     * de prescripción, no una fidelidad de autor.
     *
     * PERCENT_TM se convierte a %1RM con [trainingMaxPercent] (TM 90 % → 105 % TM ≈ 94,5 % 1RM).
     * PERCENT_OF_TOP_SET es relativo al PR de la semana (Texas/Madcow), no al 1RM.
     */
    private fun checkH11(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
        trainingMaxPercent: Double,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val tmFraction = if (trainingMaxPercent > 0.0) trainingMaxPercent else 0.90
        resolved.forEach { (slot, _, _) ->
            slot.workingSets().forEach { set ->
                val pct = set.percent ?: return@forEach
                val reps = set.reps ?: set.repsMax ?: 0
                val basis = set.loadBasis
                val implied1rm = when (basis) {
                    LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> pct
                    LoadBasis.PERCENT_TM -> pct * tmFraction
                    LoadBasis.PERCENT_OF_TOP_SET, LoadBasis.RPE, LoadBasis.REP_MAX -> null
                }
                when (basis) {
                    LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> {
                        if (pct > MAX_PERCENT_1RM) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % 1RM > $MAX_PERCENT_1RM")
                        } else if (reps >= 3 && pct > MAX_PERCENT_1RM_FOR_TRIPLE_PLUS) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % 1RM > $MAX_PERCENT_1RM_FOR_TRIPLE_PLUS (3+ reps)")
                        }
                    }
                    LoadBasis.PERCENT_TM -> {
                        if (pct > MAX_PERCENT_TM) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % TM > $MAX_PERCENT_TM")
                        }
                        if (implied1rm != null && implied1rm > MAX_PERCENT_1RM) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % TM = ${"%.1f".format(implied1rm)} % 1RM > $MAX_PERCENT_1RM")
                        } else if (implied1rm != null && reps >= 3 && implied1rm > MAX_PERCENT_1RM_FOR_TRIPLE_PLUS) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ ${"%.1f".format(implied1rm)} % 1RM > $MAX_PERCENT_1RM_FOR_TRIPLE_PLUS (3+ reps)")
                        }
                    }
                    LoadBasis.PERCENT_OF_TOP_SET -> {
                        if (pct > MAX_PERCENT_OF_TOP_SET) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % del top set > $MAX_PERCENT_OF_TOP_SET")
                        }
                    }
                    else -> { }
                }
            }
        }
        return findings
    }

    private fun checkSoft(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val push = resolved.filter { it.third in setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH) }
            .sumOf { it.first.workingSets().size }
        val pull = resolved.filter { it.third in setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL) }
            .sumOf { it.first.workingSets().size }
        val hasPress = resolved.any { it.third in setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH) && it.first.role in setOf(SlotRole.T1_MAIN, SlotRole.T2_SUPPLEMENTAL) }
        if (hasPress && pull == 0) {
            findings += soft("S2", scope, "Día de press T1/T2 sin remo ni tirón")
        }
        if (push > 0 && pull > 0) {
            val ratio = push.toDouble() / pull.toDouble()
            if (ratio < 0.75 || ratio > 1.33) {
                findings += soft("S1", scope, "Ratio empuje/tirón $ratio fuera de 0.75-1.33")
            }
        }
        val t1 = resolved.firstOrNull { it.first.role == SlotRole.T1_MAIN }
        val t2 = resolved.firstOrNull { it.first.role == SlotRole.T2_SUPPLEMENTAL }
        if (t1 != null && t2 != null && t1.first.lift.configurationId == t2.first.lift.configurationId && t2.first.technique == null) {
            findings += soft("S3", scope, "T2 usa el mismo ejercicio que T1 sin variante")
        }
        return findings
    }

    private fun evaluateWeek(
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val scope = "w${week.weekNumber}"
        if (week.days.isEmpty()) {
            findings += hard("W6", scope, "Semana sin días de entrenamiento")
            return findings
        }
        val hypertrophy = week.blockGoal in setOf(BlockGoal.ACCUMULATION, BlockGoal.DENSITY)
        val groupsHit = mutableMapOf<KpknMuscleGroup, Int>()
        val volumeSets = mutableMapOf<KpknMuscleGroup, Double>()
        val primarySets = mutableMapOf<KpknMuscleGroup, Double>()
        week.days.forEach { day ->
            day.slots.forEach { slot ->
                val meta = metadata.metadata(slot.lift.configurationId) ?: return@forEach
                meta.primaryMuscles.forEachIndexed { muscleIndex, muscle ->
                    val group = CompositionTaxonomy.muscleGroup(muscle, meta.movementPatternId, meta.configurationId)
                        ?: return@forEach
                    groupsHit[group] = (groupsHit[group] ?: 0) + 1
                    volumeSets[group] = (volumeSets[group] ?: 0.0) + slot.workingSets().size
                    if (muscleIndex == 0) {
                        primarySets[group] = (primarySets[group] ?: 0.0) + slot.workingSets().size
                    }
                }
                meta.secondaryMuscles.forEach { muscle ->
                    val group = CompositionTaxonomy.muscleGroup(muscle, meta.movementPatternId, meta.configurationId)
                        ?: return@forEach
                    volumeSets[group] = (volumeSets[group] ?: 0.0) + slot.workingSets().size * 0.5
                }
            }
        }
        val enoughDaysForHypertrophyLandmarks = recipe.daysPerWeek >= 4 && week.days.size >= 3
        val isPlSbd = recipe.liftSlots.keys.containsAll(
            setOf(
                com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                com.example.kpkn.data.protocols.LiftSlot.BENCH,
                com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
            ),
        )
        val isSpecialization = recipe.liftSlots.size <= 1
        val applyHypertrophyLandmarks = hypertrophy && enoughDaysForHypertrophyLandmarks && !isPlSbd && !isSpecialization
        if (applyHypertrophyLandmarks) {
            listOf(KpknMuscleGroup.CHEST, KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.QUADS).forEach { group ->
                if ((groupsHit[group] ?: 0) < 2) {
                    findings += hard("W1", scope, "Frecuencia < 2 para $group")
                }
            }
        }
        val peak = week.blockGoal in setOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER)
        val specificPeak = setOf(KpknMuscleGroup.QUADS, KpknMuscleGroup.CHEST, KpknMuscleGroup.HAMS, KpknMuscleGroup.ERECTORS)
        volumeSets.forEach { (group, sets) ->
            val landmark = VolumeLandmarks.byGroup[group] ?: return@forEach
            if (applyHypertrophyLandmarks &&
                sets < landmark.mev &&
                group in setOf(KpknMuscleGroup.CHEST, KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.QUADS)
            ) {
                findings += hard("W2", scope, "$group $sets series < MEV ${landmark.mev}")
            }
            if (sets > landmark.mrv && !isPlSbd && !isSpecialization) {
                val finding = CompositionFinding(
                    if (landmark.mev <= 0) CompositionSeverity.SOFT else CompositionSeverity.HARD,
                    "W2",
                    scope,
                    "$group $sets series > MRV ${landmark.mrv}",
                )
                findings += finding
            }
        }
        if (peak) {
            val peakSets = if (isPlSbd) t3PrimarySets(week, metadata) else primarySets
            peakSets.forEach { (group, sets) ->
                val landmark = VolumeLandmarks.byGroup[group] ?: return@forEach
                if (group in specificPeak || landmark.mev <= 0) return@forEach
                if (isPlSbd) {
                    if (sets > landmark.mev) {
                        findings += hard("W2", scope, "$group $sets series > MEV ${landmark.mev} en pico PL (no específico)")
                    }
                } else if (sets > landmark.mev + 4) {
                    findings += hard("W2", scope, "$group fuera de MEV en pico")
                }
            }
        }
        val heavyDays = week.days.mapIndexedNotNull { index, day ->
            val heavyAxial = day.slots.any { slot ->
                val meta = metadata.metadata(slot.lift.configurationId)
                slot.role == SlotRole.T1_MAIN &&
                    (meta?.axialLoadFactor ?: 0.0) >= AXIAL_SLOT_THRESHOLD &&
                    slot.sets.any { (it.percent ?: 0.0) >= HEAVY_PERCENT || it.isTopSet }
            }
            val position = day.weekday ?: (index * 2)
            position.takeIf { heavyAxial }
        }
        heavyDays.zipWithNext().forEach { (a, b) ->
            if (b - a < 2) findings += hard("W3", scope, "T1 axiales >=85% en días consecutivos ($a y $b)")
        }
        val orderedDays = week.days.sortedBy { it.weekday ?: Int.MAX_VALUE }
        orderedDays.zipWithNext().forEach { (prev, next) ->
            val prevDay = prev.weekday
            val nextDay = next.weekday
            val consecutive = when {
                prevDay != null && nextDay != null -> nextDay - prevDay == 1 || (prevDay == 7 && nextDay == 1)
                else -> true
            }
            if (!consecutive) return@forEach
            val prevDl = prev.slots.any {
                it.lift.configurationId.contains("deadlift") && it.role == SlotRole.T1_MAIN &&
                    it.sets.any { set -> (set.percent ?: 0.0) >= HEAVY_PERCENT || set.isTopSet }
            }
            val nextSq = next.slots.any {
                it.lift.configurationId.contains("squat") && it.role == SlotRole.T1_MAIN &&
                    it.sets.any { set -> (set.percent ?: 0.0) >= HEAVY_PERCENT || set.isTopSet }
            }
            if (prevDl && nextSq) findings += hard("W4", scope, "Peso muerto pesado el día anterior a sentadilla pesada")
        }
        val sbd = recipe.liftSlots.keys
        val canAuditSbd = sbd.containsAll(
            setOf(
                com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                com.example.kpkn.data.protocols.LiftSlot.BENCH,
                com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
            ),
        ) && week.days.size >= 3
        if (canAuditSbd) {
            fun hits(slot: com.example.kpkn.data.protocols.LiftSlot) = week.days.count { day ->
                day.slots.any { candidate ->
                    candidate.lift.liftSlot == slot ||
                        (slot == com.example.kpkn.data.protocols.LiftSlot.BENCH && isBenchExposure(candidate))
                }
            }
            val sq = hits(com.example.kpkn.data.protocols.LiftSlot.SQUAT)
            val bp = hits(com.example.kpkn.data.protocols.LiftSlot.BENCH)
            val dl = hits(com.example.kpkn.data.protocols.LiftSlot.DEADLIFT)
            if (sq < 1) findings += hard("W6", scope, "PL: exposición sentadilla $sq < 1")
            val minBench = 2
            if (bp < minBench) findings += hard("W6", scope, "PL: exposición banca $bp < $minBench")
            if (dl < 1) findings += hard("W6", scope, "PL: exposición peso muerto $dl < 1")
        }
        val weekPush = week.days.flatMap { it.slots }.sumOf { slot ->
            val family = CompositionTaxonomy.familyOf(metadata.metadata(slot.lift.configurationId)?.movementPatternId)
            if (family in setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH)) slot.workingSets().size else 0
        }
        val weekPull = week.days.flatMap { it.slots }.sumOf { slot ->
            val family = CompositionTaxonomy.familyOf(metadata.metadata(slot.lift.configurationId)?.movementPatternId)
            if (family in setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL)) slot.workingSets().size else 0
        }
        if (weekPush > 0 && weekPull > 0) {
            val ratio = weekPush.toDouble() / weekPull.toDouble()
            if (ratio < 0.75 || ratio > 1.33) findings += soft("W7", scope, "Empuje/tirón semanal $ratio fuera de 0.75-1.33")
        }
        val coreDays = week.days.count { day ->
            day.slots.any { slot ->
                val family = CompositionTaxonomy.familyOf(metadata.metadata(slot.lift.configurationId)?.movementPatternId)
                family == PatternFamily.CORE || slot.lift.configurationId.contains("core_")
            }
        }
        if (week.days.isNotEmpty() && coreDays * 2 < week.days.size) {
            findings += soft("S4", scope, "Core en $coreDays/${week.days.size} sesiones (< 50%)")
        }
        if (hypertrophy) {
            val hasUnilateral = week.days.any { day ->
                day.slots.any { slot ->
                    slot.isUnilateral || slot.lift.configurationId.contains("unilateral") ||
                        slot.lift.configurationId.contains("lunge") || slot.lift.configurationId.contains("pallof")
                }
            }
            if (!hasUnilateral) {
                findings += soft("S5", scope, "Hipertrofia semanal sin unilateral")
            }
            val stretchTokens = listOf(
                "fly", "seated_leg_curl", "biceps_curl_bayesian", "overhead_triceps",
                "sissy_squat", "lateral_raise_super_rom", "pullover",
            )
            val hasStretch = week.days.any { day ->
                day.slots.any { slot -> stretchTokens.any { token -> slot.lift.configurationId.contains(token) } }
            }
            if (!hasStretch && !isPlSbd && !isSpecialization) {
                findings += soft("S6", scope, "Hipertrofia sin aislamiento en estiramiento cargado")
            }
        }
        return findings
    }

    private fun evaluateBlocks(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val byBlock = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
        val weeklyVolume = byBlock.mapValues { (_, weeks) ->
            weeks.map { week -> week.days.sumOf { day -> day.slots.sumOf { it.workingSets().size } } }.average()
        }
        val accVolume = byBlock.filter { it.value.first().blockGoal == BlockGoal.ACCUMULATION }
            .mapNotNull { weeklyVolume[it.key] }
            .maxOrNull()
        byBlock.forEach { (index, weeks) ->
            val goal = weeks.first().blockGoal
            val t1Work = weeks.flatMap { it.days }.flatMap { it.slots }
                .filter { it.role == SlotRole.T1_MAIN }
                .flatMap { it.workingSets() }
            val t1Percents = t1Work.mapNotNull { it.percent }
            val t1Reps = t1Work.mapNotNull { it.reps ?: it.repsMax }
            val scope = "block$index/${weeks.first().blockName}"
            val volume = weeklyVolume[index] ?: 0.0
            when (goal) {
                BlockGoal.ACCUMULATION -> {
                    if (t1Percents.any { it > 80 }) {
                        findings += soft("BLOCK", scope, "Acumulación con T1 > 80%")
                    }
                    if (t1Reps.any { it !in 4..12 }) {
                        findings += soft("BLOCK", scope, "Acumulación con T1 fuera de 4-12 reps")
                    }
                }
                BlockGoal.INTENSIFICATION -> {
                    if (t1Percents.any { it in 1.0..77.0 }) {
                        findings += soft("BLOCK", scope, "Intensificación con T1 bajo 78%")
                    }
                    if (accVolume != null && accVolume > 0.0) {
                        val drop = (accVolume - volume) / accVolume
                        if (drop < 0.15 || drop > 0.35) {
                            findings += soft("BLOCK", scope, "Intensificación volumen ${"%.0f".format(drop * 100)}% vs acumulación (objetivo 15-30%)")
                        }
                    }
                }
                BlockGoal.PEAK, BlockGoal.REALIZATION -> {
                    if (t1Percents.isNotEmpty() && t1Percents.none { it >= 85 }) {
                        findings += hard("BLOCK", scope, "Pico/realización sin T1 >= 85%")
                    }
                    if (accVolume != null && accVolume > 0.0) {
                        val drop = (accVolume - volume) / accVolume
                        if (drop < 0.35) {
                            findings += soft("BLOCK", scope, "Pico volumen −${"%.0f".format(drop * 100)}% vs acumulación (objetivo 40-60%)")
                        }
                    }
                }
                BlockGoal.TAPER -> {
                    val previous = byBlock[index - 1]?.let { weeklyVolume[index - 1] }
                    if (previous != null && previous > 0.0) {
                        val drop = (previous - volume) / previous
                        if (drop < 0.45) {
                            findings += soft("BLOCK", scope, "Taper volumen −${"%.0f".format(drop * 100)}% vs bloque anterior (objetivo 50-70%)")
                        }
                    }
                    findings += checkTaperLastHeavy(weeks, scope)
                }
                BlockGoal.DELOAD -> {
                    val hot = t1Work.filter { (it.percent ?: 0.0) > 70.0 }
                    if (hot.isNotEmpty()) {
                        findings += hard("BLOCK", scope, "Descarga con T1 > 70%")
                    }
                    val lowRir = weeks.flatMap { it.days }.flatMap { it.slots }.flatMap { it.workingSets() }
                        .mapNotNull { it.rir }
                        .filter { it < 4 }
                    if (lowRir.isNotEmpty()) {
                        findings += hard("BLOCK", scope, "Descarga con RIR < 4")
                    }
                    if (accVolume != null && accVolume > 0.0 && volume > accVolume * 0.6) {
                        findings += soft("BLOCK", scope, "Descarga sin recorte ~×0,5 de series")
                    }
                }
                else -> { }
            }
            val ids = weeks.map { week ->
                week.days.flatMap { day -> day.slots.filter { it.role == SlotRole.T3_ACCESSORY }.map { it.lift.configurationId } }
            }
            if (ids.size >= 2 && ids.distinct().size > 1 && goal != BlockGoal.CUSTOM) {
                val first = ids.first().toSet()
                if (ids.any { it.toSet() != first }) {
                    findings += hard("W5", scope, "Accesorios cambian dentro del bloque")
                }
            }
        }
        return findings
    }

    private fun checkTaperLastHeavy(weeks: List<WeekRecipe>, scope: String): List<CompositionFinding> {
        val ordered = weeks.sortedBy { it.weekNumber }
        val lastWeek = ordered.lastOrNull() ?: return emptyList()
        val testDay = lastWeek.days.maxByOrNull { it.weekday ?: 0 } ?: return emptyList()
        val testWeekday = testDay.weekday ?: 7
        val lastHeavy = ordered.flatMap { week ->
            week.days.map { day -> week to day }
        }.lastOrNull { (week, day) ->
            val isTest = week.weekNumber == lastWeek.weekNumber && day.label == testDay.label
            !isTest && day.slots.any { slot ->
                slot.role == SlotRole.T1_MAIN && slot.workingSets().any { (it.percent ?: 0.0) >= HEAVY_PERCENT || it.isTopSet }
            }
        } ?: return emptyList()
        val heavyWeek = lastHeavy.first.weekNumber
        val heavyDay = lastHeavy.second.weekday ?: 1
        val days = (lastWeek.weekNumber - heavyWeek) * 7 + (testWeekday - heavyDay)
        return if (days in 7..10) {
            emptyList()
        } else {
            listOf(soft("BLOCK", scope, "Última pesada $days días antes del test (objetivo 7-10)"))
        }
    }

    private fun DayRecipe.dayslotsPercents(): List<Double> =
        slots.filter { it.role == SlotRole.T1_MAIN }.flatMap { it.workingSets().mapNotNull { set -> set.percent } }

    private fun SlotRecipe.workingSets(): List<SetRecipe> = sets.filter { !it.isWarmup }

    private fun isBenchExposure(slot: SlotRecipe): Boolean {
        if (slot.lift.liftSlot == com.example.kpkn.data.protocols.LiftSlot.BENCH) return true
        val id = slot.lift.configurationId
        return id.contains("bench_press") ||
            id.contains("press_banca") ||
            id.contains("floor_press") ||
            id.contains("press_spoto")
    }

    private fun t3PrimarySets(
        week: WeekRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): Map<KpknMuscleGroup, Double> {
        val accessory = mutableMapOf<KpknMuscleGroup, Double>()
        week.days.forEach { day ->
            day.slots.filter { it.role == SlotRole.T3_ACCESSORY }.forEach { slot ->
                val meta = metadata.metadata(slot.lift.configurationId) ?: return@forEach
                val muscle = meta.primaryMuscles.firstOrNull() ?: return@forEach
                val group = CompositionTaxonomy.muscleGroup(muscle, meta.movementPatternId, meta.configurationId)
                    ?: return@forEach
                accessory[group] = (accessory[group] ?: 0.0) + slot.workingSets().size
            }
        }
        return accessory
    }

    fun applyExemptions(
        findings: List<CompositionFinding>,
        exemptions: List<RecipeCompositionExemption>,
    ): List<CompositionFinding> = findings.filter { finding ->
        if (finding.rule == "H11") return@filter true
        exemptions.none { exemption ->
            exemption.rule == finding.rule &&
                (exemption.scope == "*" || exemption.scope == finding.scope || finding.scope.startsWith(exemption.scope) || finding.scope.contains(exemption.scope))
        }
    }

    private fun hard(rule: String, scope: String, message: String) =
        CompositionFinding(CompositionSeverity.HARD, rule, scope, message)

    private fun soft(rule: String, scope: String, message: String) =
        CompositionFinding(CompositionSeverity.SOFT, rule, scope, message)
}
