package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.RecipeCompositionExemption
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
                raw += evaluateDay(day, week, metadata)
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
            if (factor < SPINAL_BUDGET_THRESHOLD) 0.0 else slot.sets.size * factor
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
        val sets = resolved.sumOf { it.first.sets.size }
        val minSets = if (goal in setOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER, BlockGoal.DELOAD)) {
            MIN_EFFECTIVE_SETS_PEAK
        } else {
            MIN_EFFECTIVE_SETS
        }
        if (sets < minSets || sets > MAX_EFFECTIVE_SETS) {
            findings += hard("H6", scope, "Sesión de $sets series efectivas (permitido $minSets-$MAX_EFFECTIVE_SETS)")
        }
        val seconds = resolved.sumOf { (slot, _, _) -> slot.sets.size * (slot.restSeconds + 45) }
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
            byMuscle[dominant] = (byMuscle[dominant] ?: 0) + slot.sets.size
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
        val peakish = goal == BlockGoal.INTENSIFICATION || goal == BlockGoal.PEAK ||
            goal == BlockGoal.REALIZATION || goal == BlockGoal.SPECIFICITY
        resolved.forEach { (slot, _, _) ->
            val maxReps = slot.sets.maxOfOrNull { it.reps ?: it.repsMax ?: 0 } ?: 0
            if (slot.role == SlotRole.T1_MAIN && peakish && maxReps >= 12) {
                findings += hard("H8", scope, "T1 con $maxReps reps en $goal")
            }
            if (slot.role in setOf(SlotRole.T3_ACCESSORY) ) {
                slot.sets.forEach { set ->
                    if (set.percent != null && set.loadBasis in setOf(
                            LoadBasis.PERCENT_TM, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX,
                        )
                    ) {
                        findings += hard("H8", scope, "T3 ${slot.lift.configurationId} usa % en vez de RPE/RIR")
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
            val minRest = when (slot.role) {
                SlotRole.SPEED -> 45
                SlotRole.T1_MAIN -> if (heavy) 240 else 180
                SlotRole.T2_SUPPLEMENTAL, SlotRole.TECHNIQUE -> 120
                SlotRole.T3_ACCESSORY -> if (isolation) 45 else 90
            }
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

    private fun checkSoft(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        scope: String,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val push = resolved.filter { it.third in setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH) }
            .sumOf { it.first.sets.size }
        val pull = resolved.filter { it.third in setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL) }
            .sumOf { it.first.sets.size }
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
        val hypertrophy = week.blockGoal in setOf(BlockGoal.ACCUMULATION, BlockGoal.DENSITY)
        val groupsHit = mutableMapOf<KpknMuscleGroup, Int>()
        val volumeSets = mutableMapOf<KpknMuscleGroup, Double>()
        val primarySets = mutableMapOf<KpknMuscleGroup, Double>()
        week.days.forEach { day ->
            day.slots.forEach { slot ->
                val meta = metadata.metadata(slot.lift.configurationId) ?: return@forEach
                meta.primaryMuscles.forEachIndexed { muscleIndex, muscle ->
                    val group = CompositionTaxonomy.muscleGroup(muscle, meta.movementPatternId) ?: return@forEach
                    groupsHit[group] = (groupsHit[group] ?: 0) + 1
                    volumeSets[group] = (volumeSets[group] ?: 0.0) + slot.sets.size
                    if (muscleIndex == 0) {
                        primarySets[group] = (primarySets[group] ?: 0.0) + slot.sets.size
                    }
                }
                meta.secondaryMuscles.forEach { muscle ->
                    val group = CompositionTaxonomy.muscleGroup(muscle, meta.movementPatternId) ?: return@forEach
                    volumeSets[group] = (volumeSets[group] ?: 0.0) + slot.sets.size * 0.5
                }
            }
        }
        val enoughDaysForHypertrophyLandmarks = recipe.daysPerWeek >= 4 && week.days.size >= 3
        if (hypertrophy && enoughDaysForHypertrophyLandmarks) {
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
            if (hypertrophy && enoughDaysForHypertrophyLandmarks &&
                sets < landmark.mev &&
                group in setOf(KpknMuscleGroup.CHEST, KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.QUADS)
            ) {
                findings += hard("W2", scope, "$group $sets series < MEV ${landmark.mev}")
            }
            if (sets > landmark.mrv) {
                findings += hard("W2", scope, "$group $sets series > MRV ${landmark.mrv}")
            }
        }
        if (peak) {
            primarySets.forEach { (group, sets) ->
                val landmark = VolumeLandmarks.byGroup[group] ?: return@forEach
                if (group in specificPeak || landmark.mev <= 0) return@forEach
                if (sets > landmark.mev + 4) {
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
        week.days.zipWithNext().forEach { (prev, next) ->
            val prevDl = prev.slots.any { it.lift.configurationId.contains("deadlift") && it.role == SlotRole.T1_MAIN && it.sets.any { set -> (set.percent ?: 0.0) >= HEAVY_PERCENT || set.isTopSet } }
            val nextSq = next.slots.any { it.lift.configurationId.contains("squat") && it.role == SlotRole.T1_MAIN && it.sets.any { set -> (set.percent ?: 0.0) >= HEAVY_PERCENT || set.isTopSet } }
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
                day.slots.any { it.lift.liftSlot == slot }
            }
            val sq = hits(com.example.kpkn.data.protocols.LiftSlot.SQUAT)
            val bp = hits(com.example.kpkn.data.protocols.LiftSlot.BENCH)
            val dl = hits(com.example.kpkn.data.protocols.LiftSlot.DEADLIFT)
            if (sq < 1) findings += hard("W6", scope, "PL: exposición sentadilla $sq < 1")
            val minBench = if (recipe.daysPerWeek >= 4) 2 else 1
            if (bp < minBench) findings += hard("W6", scope, "PL: exposición banca $bp < $minBench")
            if (dl < 1) findings += hard("W6", scope, "PL: exposición peso muerto $dl < 1")
        }
        val weekPush = week.days.flatMap { it.slots }.sumOf { slot ->
            val family = CompositionTaxonomy.familyOf(metadata.metadata(slot.lift.configurationId)?.movementPatternId)
            if (family in setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH)) slot.sets.size else 0
        }
        val weekPull = week.days.flatMap { it.slots }.sumOf { slot ->
            val family = CompositionTaxonomy.familyOf(metadata.metadata(slot.lift.configurationId)?.movementPatternId)
            if (family in setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL)) slot.sets.size else 0
        }
        if (weekPush > 0 && weekPull > 0) {
            val ratio = weekPush.toDouble() / weekPull.toDouble()
            if (ratio < 0.75 || ratio > 1.33) findings += soft("W7", scope, "Empuje/tirón semanal $ratio fuera de 0.75-1.33")
        }
        return findings
    }

    private fun evaluateBlocks(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val byBlock = recipe.weeks.groupBy { it.blockIndex }
        byBlock.forEach { (index, weeks) ->
            val goal = weeks.first().blockGoal
            val t1Percents = weeks.flatMap { it.days }.flatMap { it.dayslotsPercents() }
            val scope = "block$index/${weeks.first().blockName}"
            when (goal) {
                BlockGoal.ACCUMULATION -> {
                    if (t1Percents.any { it > 80 }) {
                        findings += soft("BLOCK", scope, "Acumulación con T1 > 80%")
                    }
                }
                BlockGoal.INTENSIFICATION -> {
                    if (t1Percents.any { it in 1.0..77.0 }) {
                        findings += soft("BLOCK", scope, "Intensificación con T1 bajo 78%")
                    }
                }
                BlockGoal.PEAK, BlockGoal.REALIZATION -> {
                    if (t1Percents.none { it >= 85 }) {
                        findings += hard("BLOCK", scope, "Pico/realización sin T1 >= 85%")
                    }
                }
                BlockGoal.TAPER, BlockGoal.DELOAD -> { }
                else -> { }
            }
            val ids = weeks.map { week ->
                week.days.flatMap { day -> day.slots.filter { it.role == SlotRole.T3_ACCESSORY }.map { it.lift.configurationId } }
            }
            if (ids.size >= 2 && ids.distinct().size > 1 && goal != BlockGoal.CUSTOM) {
                // W5: accessories stable inside a block. Allow SPEED/ME rotation via exemption.
                val first = ids.first().toSet()
                if (ids.any { it.toSet() != first }) {
                    findings += hard("W5", scope, "Accesorios cambian dentro del bloque")
                }
            }
        }
        return findings
    }

    private fun DayRecipe.dayslotsPercents(): List<Double> =
        slots.filter { it.role == SlotRole.T1_MAIN }.flatMap { it.sets.mapNotNull { set -> set.percent } }

    fun applyExemptions(
        findings: List<CompositionFinding>,
        exemptions: List<RecipeCompositionExemption>,
    ): List<CompositionFinding> = findings.filter { finding ->
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
