package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.definitions.NativeProfileKind

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

    /** Reglas de seguridad de intensidad: ninguna exención las silencia. */
    private val NON_EXEMPTABLE_RULES = setOf("H11", "H11b")

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
    ): List<CompositionFinding> =
        applyExemptions(evaluateRecipeRaw(recipe, metadata), recipe.exemptions + extraExemptions)

    /**
     * Hallazgos de la receta **sin** filtrar por exenciones. Los tests lo usan para
     * inventariar H11/H11b y para comprobar que migrar el ámbito de una exención
     * no pierde ni amplía lo que silencia.
     *
     * Al final suma el contrato de receta válida ([RecipeContractPolicy], B.S2). Por ahora en
     * modo inventario: todos sus hallazgos son SOFT, así que `hardFindings` no cambia, y
     * [evaluateRecipe] los filtra con las exenciones igual que al resto.
     */
    internal fun evaluateRecipeRaw(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): List<CompositionFinding> {
        val raw = mutableListOf<CompositionFinding>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                raw += evaluateDay(day, week, metadata, recipe.trainingMaxPercent, recipe.compositionProfile)
            }
            raw += evaluateWeek(week, recipe, metadata)
        }
        raw += evaluateBlocks(recipe)
        raw += RecipeContractPolicy.evaluate(recipe, metadata)
        return raw
    }

    /**
     * Evaluación de un día. [compositionProfile] llega desde la receta (§14.3):
     * `LEGACY_STANDARD`/`AUTHORED_EXACT` conservan H1..H11 intactos y
     * `NATIVE_COMPACT`/`MIXED_CARDIO` despachan H6 a los mínimos declarados
     * ([DayMinimumDose]) y a la validación real de cardio por
     * [DayRecipe.sessionKind].
     */
    fun evaluateDay(
        day: DayRecipe,
        week: WeekRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        trainingMaxPercent: Double = 0.90,
        compositionProfile: RecipeCompositionProfile = RecipeCompositionProfile.LEGACY_STANDARD,
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
        findings += checkH6(resolved, week.blockGoal, scope, compositionProfile, day)
        findings += checkH7(resolved, scope)
        findings += checkH8(resolved, week.blockGoal, scope)
        findings += checkH9(resolved, scope)
        findings += checkH10(resolved, scope)
        findings += checkH11(resolved, week, scope, trainingMaxPercent)
        findings += checkH11b(resolved, week, scope, trainingMaxPercent)
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
        compositionProfile: RecipeCompositionProfile = RecipeCompositionProfile.LEGACY_STANDARD,
        day: DayRecipe? = null,
    ): List<CompositionFinding> {
        if (compositionProfile == RecipeCompositionProfile.NATIVE_COMPACT ||
            compositionProfile == RecipeCompositionProfile.MIXED_CARDIO
        ) {
            return nativeCheckH6(resolved, goal, scope, requireNotNull(day) { "los perfiles propios validan por día" })
        }
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

    /**
     * H6 por propósito (§14.3): `NATIVE_COMPACT` usa los mínimos declarados en
     * [com.example.kpkn.data.protocols.DayMinimumDose] (2 configuraciones
     * distintas / 4 series de resistencia ordinaria por día STRENGTH; techos 9
     * ejercicios / 30 series / 100 min) y despacha por
     * [DayRecipe.sessionKind] en `MIXED_CARDIO`. SPEED y calentamientos no
     * rellenan el mínimo; la duración preliminar es SOFT porque el cálculo
     * final lo hace el estimador común sobre la sesión materializada (§12.2/§14.3).
     */
    private fun nativeCheckH6(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        goal: BlockGoal,
        scope: String,
        day: DayRecipe,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val n = resolved.size
        if (n > MAX_EXERCISES) {
            findings += hard("H6", scope, "Sesión de $n ejercicios (máximo $MAX_EXERCISES)")
        }
        val totalSets = resolved.sumOf { it.first.workingSets().size }
        if (totalSets > MAX_EFFECTIVE_SETS) {
            findings += hard("H6", scope, "Sesión de $totalSets series (máximo $MAX_EFFECTIVE_SETS)")
        }
        val seconds = resolved.sumOf { (slot, _, _) -> slot.workingSets().size * (slot.restSeconds + 45) }
        if (seconds > MAX_SESSION_MINUTES * 60) {
            findings += soft("H6", scope, "Duración aproximada ${seconds / 60} min > $MAX_SESSION_MINUTES (el presupuesto real se valida con el estimador común)")
        }

        val cardioBlocks = day.cardioBlocks
        val cardioValid = cardioBlocks.isNotEmpty() &&
            cardioBlocks.all { it.details.effectiveDurationSeconds() >= MIN_CARDIO_BLOCK_SECONDS }

        // Resistencia ORDINARIA: sin SPEED ni calentamientos (§14.3).
        val ordinary = resolved.filter { (slot, _, _) -> slot.role != SlotRole.SPEED }
        val distinctConfigurations = ordinary.map { (slot, _, _) -> slot.lift.configurationId }.distinct().size
        val resistanceSets = ordinary.sumOf { (slot, _, _) -> slot.workingSets().size }
        val dose = day.minimumDose
        val essentials = dose?.essentialSlotIds.orEmpty()
        val presentIds = resolved.map { (slot, _, _) -> slot.id }.toSet()
        val essentialSetsById = resolved.associate { (slot, _, _) -> slot.id to slot.workingSets().size }
        val deload = goal == BlockGoal.DELOAD

        fun missingEssentials(): List<String> =
            essentials.filter { id -> id !in presentIds || (essentialSetsById[id] ?: 0) < 1 }

        when (day.sessionKind) {
            RecipeSessionKind.CARDIO -> {
                if (day.slots.isNotEmpty()) {
                    findings += hard(
                        "H6",
                        scope,
                        "Día solo cardio con ${day.slots.size} slots de resistencia (debe ser 0)",
                    )
                }
                if (!cardioValid) {
                    findings += hard("H6", scope, "Día solo cardio sin bloque de cardio real de al menos 10 min")
                }
            }
            RecipeSessionKind.CARDIO_ACCESSORY -> {
                if (!cardioValid) {
                    findings += hard("H6", scope, "Día cardio+accesorios sin bloque de cardio real de al menos 10 min")
                }
                val missing = missingEssentials()
                if (missing.isNotEmpty()) {
                    findings += hard("H6", scope, "Accesorios esenciales ausentes o sin series: $missing")
                }
                // 1 ejercicio / 1–2 series es válido aquí: no se exige el suelo STRENGTH.
            }
            RecipeSessionKind.STRENGTH, RecipeSessionKind.STRENGTH_CARDIO -> {
                if (day.sessionKind == RecipeSessionKind.STRENGTH_CARDIO && !cardioValid) {
                    findings += hard("H6", scope, "Día resistencia+cardio sin bloque de cardio real de al menos 10 min")
                }
                val missing = missingEssentials()
                if (missing.isNotEmpty()) {
                    findings += hard("H6", scope, "Slots esenciales ausentes o sin series: $missing")
                }
                if (!deload) {
                    val minDistinct = dose?.minDistinctConfigurations ?: 2
                    val minSets = dose?.minResistanceSets ?: 4
                    if (distinctConfigurations < minDistinct) {
                        findings += hard(
                            "H6",
                            scope,
                            "Día con $distinctConfigurations configuraciones distintas (mínimo $minDistinct)",
                        )
                    }
                    if (resistanceSets < minSets) {
                        findings += hard("H6", scope, "Día con $resistanceSets series de resistencia (mínimo $minSets)")
                    }
                } else if (resolved.any { (slot, _, _) -> slot.workingSets().isEmpty() }) {
                    findings += hard("H6", scope, "Descarga con algún slot sin series (mínimo 1 por slot)")
                }
            }
        }
        return findings
    }

    /** Bloques de cardio reales: ≥10 min por bloque (§12.3/§14.3). */
    const val MIN_CARDIO_BLOCK_SECONDS = 600

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
     * PERCENT_OF_TOP_SET se mide dos veces: el valor crudo contra su techo propio (es relativo
     * al PR de la semana, no al 1RM) y los kg resueltos, es decir, el porcentaje que resuelve
     * [PercentResolver] convertido a %1RM con el mismo par de umbrales que PERCENT_TM.
     */
    private fun checkH11(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        week: WeekRecipe,
        scope: String,
        trainingMaxPercent: Double,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        resolved.forEach { (slot, _, _) ->
            slot.workingSets().forEach { set ->
                val pct = set.percent ?: return@forEach
                val reps = set.reps ?: set.repsMax ?: 0
                val basis = set.loadBasis
                val implied1rm = PercentBasis.effective1RmPercent(set, slot, week, trainingMaxPercent)
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
                        if (implied1rm != null && implied1rm > MAX_PERCENT_1RM) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % del top set = ${"%.1f".format(implied1rm)} % 1RM > $MAX_PERCENT_1RM")
                        } else if (implied1rm != null && reps >= 3 && implied1rm > MAX_PERCENT_1RM_FOR_TRIPLE_PLUS) {
                            findings += hard("H11", scope, "${slot.id} $reps reps @ $pct % del top set = ${"%.1f".format(implied1rm)} % 1RM > $MAX_PERCENT_1RM_FOR_TRIPLE_PLUS (3+ reps)")
                        }
                    }
                    else -> { }
                }
            }
        }
        return findings
    }

    /**
     * H11b (Epley). Un set de trabajo no puede pedir más repeticiones de las que caben al
     * %1RM efectivo: `reps > floor(30 × (100 ÷ p − 1)) + 1` es un 5×90 %, no una fidelidad de
     * autor. Igual que H11, no es exentable.
     *
     * Quedan fuera los AMRAP (el autor pide «tantas como salgan») y `REP_MAX`; en un rango de
     * repeticiones manda la cota inferior, porque es lo mínimo que se promete hacer. Los sets
     * sin base de 1RM (RPE o sin porcentaje) no se miden.
     */
    private fun checkH11b(
        resolved: List<Triple<SlotRecipe, ExerciseCompositionMetadata?, PatternFamily?>>,
        week: WeekRecipe,
        scope: String,
        trainingMaxPercent: Double,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        resolved.forEach { (slot, _, _) ->
            slot.workingSets().forEach { set ->
                if (set.amrap || set.loadBasis == LoadBasis.REP_MAX) return@forEach
                val p = PercentBasis.effective1RmPercent(set, slot, week, trainingMaxPercent) ?: return@forEach
                // Por encima del 100 % del 1RM ya salta H11: no se duplica aquí con un «caben como máximo 0».
                if (p > MAX_PERCENT_1RM) return@forEach
                val reps = set.reps ?: set.repsMin ?: return@forEach
                val maxReps = PercentBasis.maxRepsByEpley(p)
                if (reps > maxReps) {
                    findings += hard("H11b", scope, "${slot.id} $reps reps @ ${"%.1f".format(p)} % 1RM: por Epley caben como máximo $maxReps")
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
        // D5 del plan 2026-09-16: hallazgo SOFT que reporta slots deliberados
        // con la misma configuración y la misma técnica en un día, sin
        // bloquear. La fidelidad de protocolo es ley: variantes con técnica
        // distinta (PHAT speed vs T1) quedan exentas porque producen nombres
        // visibles distintos.
        val seenSlots = mutableSetOf<Pair<String, String?>>()
        resolved.forEach { (slot, _, _) ->
            val key = slot.lift.configurationId.trim().lowercase() to slot.technique?.name
            if (key.first.isNotBlank() && !seenSlots.add(key)) {
                findings += soft(
                    "S_duplicate_slot",
                    scope,
                    "Slot duplicado: '${slot.lift.configurationId}' con la misma técnica en el mismo día",
                )
            }
        }
        return findings
    }

    /** Conteo de una semana de receta por grupo muscular: frecuencia, series y series del músculo dominante. */
    private class WeekMuscleTally(
        val groupsHit: Map<KpknMuscleGroup, Int>,
        val volumeSets: Map<KpknMuscleGroup, Double>,
        val primarySets: Map<KpknMuscleGroup, Double>,
    )

    private fun tallyWeek(week: WeekRecipe, metadata: ExerciseCompositionMetadataProvider): WeekMuscleTally {
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
        return WeekMuscleTally(groupsHit, volumeSets, primarySets)
    }

    /**
     * Series semanales por grupo muscular de UNA semana de receta: 1,0 por serie de trabajo en cada
     * músculo primario y 0,5 en cada secundario. Es el contador único de W2: la política lo usa para
     * clasificar el volumen (normal, «volumen alto» o rechazo) y el ajustador de los planes propios lo
     * reutiliza para decidir si un plan cabe en la banda, así que ambos miden con la misma regla.
     */
    fun weeklyGroupSets(
        week: WeekRecipe,
        metadata: ExerciseCompositionMetadataProvider,
    ): Map<KpknMuscleGroup, Double> = tallyWeek(week, metadata).volumeSets

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
        // §14.3: el perfil de composición llega a la validación de semana, no
        // solo a H6. LEGACY/AUTHORED conservan W1..W7/BLOCK actuales; los
        // planes propios sustituyen los hitos genéricos por los suelos de
        // §§11–12 y mantienen el techo MRV con contabilidad real.
        val profile = recipe.compositionProfile
        val nativeKind = if (profile == RecipeCompositionProfile.NATIVE_COMPACT ||
            profile == RecipeCompositionProfile.MIXED_CARDIO
        ) {
            NativeProfileKind.fromEntryId(recipe.id)
        } else {
            null
        }
        val hypertrophy = week.blockGoal in setOf(BlockGoal.ACCUMULATION, BlockGoal.DENSITY)
        // Contador único de series por grupo (también lo usa el ajustador de planes propios).
        val tally = tallyWeek(week, metadata)
        val groupsHit = tally.groupsHit
        val volumeSets = tally.volumeSets
        val primarySets = tally.primarySets
        val enoughDaysForHypertrophyLandmarks = recipe.daysPerWeek >= 4 && week.days.size >= 3
        val isPlSbd = recipe.liftSlots.keys.containsAll(
            setOf(
                com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                com.example.kpkn.data.protocols.LiftSlot.BENCH,
                com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
            ),
        )
        val isSpecialization = recipe.liftSlots.size <= 1
        val applyHypertrophyLandmarks = hypertrophy && enoughDaysForHypertrophyLandmarks &&
            !isPlSbd && !isSpecialization && nativeKind == null
        if (applyHypertrophyLandmarks) {
            listOf(KpknMuscleGroup.CHEST, KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.QUADS).forEach { group ->
                if ((groupsHit[group] ?: 0) < 2) {
                    findings += hard("W1", scope, "Frecuencia < 2 para $group")
                }
            }
        }
        val peak = week.blockGoal in setOf(BlockGoal.PEAK, BlockGoal.REALIZATION, BlockGoal.TAPER)
        val specificPeak = setOf(KpknMuscleGroup.QUADS, KpknMuscleGroup.CHEST, KpknMuscleGroup.HAMS, KpknMuscleGroup.ERECTORS)
        // MRV superior: para los planes propios el mapa `liftSlots` NUNCA
        // desactiva el techo (§14.3), ni siquiera con el mapa vacío.
        val mrvApplies = nativeKind != null || (!isPlSbd && !isSpecialization)
        volumeSets.forEach { (group, sets) ->
            val landmark = VolumeLandmarks.byGroup[group] ?: return@forEach
            if (applyHypertrophyLandmarks &&
                sets < landmark.mev &&
                group in setOf(KpknMuscleGroup.CHEST, KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.QUADS)
            ) {
                findings += hard("W2", scope, "$group $sets series < MEV ${landmark.mev}")
            }
            if (sets > landmark.mrv && mrvApplies) {
                // B-02: solo en planes propios y solo en glúteos, entre el límite (MRV) y el
                // techo blando el exceso es «volumen alto» (SOFT, permitido con aviso); por
                // encima del techo sigue siendo HARD. El texto «> MRV» se conserva en ambos
                // casos: el ajustador filtra por él los excesos que corrige por su cuenta.
                val inSoftBand = nativeKind != null &&
                    VolumeSoftBand.bandOf(group, sets, landmark.mrv) == VolumeBand.HIGH_VOLUME
                val finding = CompositionFinding(
                    if (!inSoftBand && (nativeKind != null || landmark.mev > 0)) {
                        CompositionSeverity.HARD
                    } else {
                        CompositionSeverity.SOFT
                    },
                    "W2",
                    scope,
                    if (inSoftBand) {
                        "$group $sets series > MRV ${landmark.mrv} " +
                            "(volumen alto, tolerancia blanda hasta ${VolumeSoftBand.softCeiling(group, landmark.mrv)})"
                    } else {
                        "$group $sets series > MRV ${landmark.mrv}"
                    },
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
        if (nativeKind != null) {
            findings += nativeWeeklyChecks(week, recipe, nativeKind, scope, metadata)
        } else {
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

    /**
     * Suelos semanales de §11–§12 para los cuatro planes propios (§14.3):
     * sustituyen a W1/W2 genéricos. Los checks de identidad (claimedDays, días
     * únicos, dosis semanal) se aplican siempre; los suelos de dosis son la
     * única reducción planificada en la descarga y ahí quedan exentos (§12.3),
     * etiquetados como descarga.
     */
    private fun nativeWeeklyChecks(
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        kind: NativeProfileKind,
        scope: String,
        metadata: ExerciseCompositionMetadataProvider,
    ): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        recipe.claimedDaysPerWeek?.let { claimed ->
            if (claimed != week.days.size) {
                findings += hard("W6", scope, "claimedDaysPerWeek=$claimed pero la semana tiene ${week.days.size} días")
            }
        }
        val weekdays = week.days.mapNotNull { it.weekday }
        if (weekdays.size == week.days.size && weekdays.distinct().size != weekdays.size) {
            findings += hard("W6", scope, "Días de la semana repetidos: $weekdays")
        }
        val deload = week.kind == com.example.kpkn.data.models.WeekExecutionKind.DELOAD ||
            week.blockGoal == BlockGoal.DELOAD
        if (deload) return findings

        fun working(slot: SlotRecipe) = slot.workingSets().size
        val allSlots = week.days.flatMap { it.slots }
        val fSlots = allSlots.filter { it.intent == SlotIntent.F }
        val fvSlots = allSlots.filter { it.intent == SlotIntent.FV }
        val hSlots = allSlots.filter { it.intent == SlotIntent.H }
        val pSlots = allSlots.filter { it.intent == SlotIntent.P }

        // F conserva ≥2 series por aparición; Fv puede bajar a 1 en una
        // exposición adicional cuando el fitter lo necesita (§12.3.3).
        fSlots.forEach { slot ->
            if (working(slot) < 2) {
                findings += hard("W6", scope, "F principal ${slot.lift.configurationId} con ${working(slot)} series < 2")
            }
        }
        fvSlots.forEach { slot ->
            val setCount = working(slot)
            if (setCount < 1) {
                findings += hard("W6", scope, "Fv ${slot.lift.configurationId} con ${working(slot)} series < 1")
            } else if (setCount == 1) {
                val slotDay = week.days.indexOfFirst { day -> day.slots.any { it === slot } }
                val hasOtherExposure = week.days.withIndex().any { (dayIndex, day) ->
                    dayIndex != slotDay && day.slots.any { other ->
                        other.id == slot.id &&
                            other.intent in setOf(SlotIntent.F, SlotIntent.FV, SlotIntent.H) &&
                            working(other) > 0
                    }
                }
                if (!hasOtherExposure) {
                    findings += hard("W6", scope, "Fv ${slot.lift.configurationId} baja a 1 serie sin otra exposición semanal")
                }
            }
        }

        when (kind) {
            NativeProfileKind.STRENGTH -> {
                fun daysWith(predicate: (SlotRecipe) -> Boolean) = week.days.count { day -> day.slots.any(predicate) }
                val sq = daysWith { it.lift.liftSlot == com.example.kpkn.data.protocols.LiftSlot.SQUAT }
                val bp = daysWith {
                    it.lift.liftSlot == com.example.kpkn.data.protocols.LiftSlot.BENCH || isBenchExposure(it)
                }
                val dl = daysWith { it.lift.liftSlot == com.example.kpkn.data.protocols.LiftSlot.DEADLIFT }
                // §14.3: banca ≥2 exposiciones con ≥2 días; ≥1 con 1 día.
                val minBench = if (week.days.size >= 2) 2 else 1
                if (sq < 1) findings += hard("W6", scope, "Fuerza: exposición de sentadilla $sq < 1")
                if (bp < minBench) findings += hard("W6", scope, "Fuerza: exposición de banca $bp < $minBench")
                if (dl < 1) findings += hard("W6", scope, "Fuerza: exposición de peso muerto $dl < 1")
            }
            NativeProfileKind.POWERBUILDING -> {
                val fAppearances = allSlots.count { it.intent == SlotIntent.F }
                if (fAppearances < 2) {
                    findings += hard("W6", scope, "Fuerza/músculo: $fAppearances apariciones de F en la semana < 2")
                }
                val hSets = hSlots.sumOf { working(it) }
                if (hSets < 4) {
                    findings += hard("W6", scope, "Fuerza/músculo: $hSets series H en la semana < 4")
                }
            }
            NativeProfileKind.MUSCLE -> {
                fun setsIn(families: Set<PatternFamily>) = allSlots.filter { slot ->
                    val meta = metadata.metadata(slot.lift.configurationId) ?: return@filter false
                    CompositionTaxonomy.familyOf(meta.movementPatternId) in families
                }.sumOf { working(it) }
                val squat = setsIn(setOf(PatternFamily.SQUAT))
                if (squat < 2) findings += hard("W6", scope, "Músculo: patrón S/U con $squat series < 2")
                val hip = setsIn(setOf(PatternFamily.HINGE, PatternFamily.HIP_EXTENSION))
                if (hip < 2) findings += hard("W6", scope, "Músculo: extensión de cadera con $hip series < 2")
                val push = setsIn(setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH))
                if (push < 2) findings += hard("W6", scope, "Músculo: empuje con $push series < 2")
                val pullFamilies = setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL)
                val pullSlots = allSlots.count { slot ->
                    val meta = metadata.metadata(slot.lift.configurationId) ?: return@count false
                    CompositionTaxonomy.familyOf(meta.movementPatternId) in pullFamilies
                }
                val pull = setsIn(pullFamilies)
                // «tirón disponible»: la especialización corporal sin tirón
                // declara el límite y no recibe series ficticias (§13.3).
                if (pullSlots > 0 && pull < 2) {
                    findings += hard("W6", scope, "Músculo: tirón disponible con $pull series < 2")
                }
            }
            NativeProfileKind.COMPLETE_ATHLETE -> {
                val pDays = week.days.count { day -> day.slots.any { it.intent == SlotIntent.P } }
                val resistanceDays = week.days.count { day ->
                    day.slots.any { it.intent == SlotIntent.F || it.intent == SlotIntent.FV }
                }
                // §11.4: 1 día = una exposición global de base; ≥2 días = dos
                // exposiciones de potencia y resistencia en días distintos.
                val minExposures = if (week.days.size >= 2) 2 else 1
                if (pDays < minExposures) {
                    findings += hard("W6", scope, "Atleta: $pDays exposiciones de potencia < $minExposures")
                }
                if (resistanceDays < minExposures) {
                    findings += hard("W6", scope, "$resistanceDays exposiciones de resistencia < $minExposures")
                }
                pSlots.forEach { slot ->
                    val sets = slot.workingSets()
                    val repsOk = sets.all { (it.reps ?: 0) == 3 }
                    if (sets.size < 2 || !repsOk) {
                        findings += hard("W6", scope, "Atleta: potencia ${slot.lift.configurationId} con ${sets.size}×${sets.map { it.reps }} (mínimo 2×3)")
                    }
                }
                val hSets = hSlots.sumOf { working(it) }
                if (hSets < 4) findings += hard("W6", scope, "Atleta: $hSets series H en la semana < 4")
                val cardioBlocks = week.days.flatMap { it.cardioBlocks }
                cardioBlocks.forEach { block ->
                    if (block.details.effectiveDurationSeconds() < MIN_CARDIO_BLOCK_SECONDS) {
                        findings += hard("W6", scope, "Atleta: bloque de cardio de ${block.details.effectiveDurationSeconds()}s < 10 min")
                    }
                }
                val minimumCardioBlocks = if (week.days.size == 1 || week.days.size == 3) 1 else 2
                if (cardioBlocks.size < minimumCardioBlocks) {
                    findings += hard(
                        "W6",
                        scope,
                        "Atleta: ${cardioBlocks.size} bloques de cardio < $minimumCardioBlocks requeridos para ${week.days.size} días",
                    )
                }
                if (week.days.size == 3 && week.days.none {
                        it.sessionKind == RecipeSessionKind.CARDIO ||
                            it.sessionKind == RecipeSessionKind.CARDIO_ACCESSORY
                    }
                ) {
                    findings += hard("W6", scope, "Atleta con 3 días requiere un bloque de cardio dedicado")
                }
            }
        }
        return findings
    }

    private fun evaluateBlocks(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val nativeKind = if (
            recipe.compositionProfile in setOf(
                RecipeCompositionProfile.NATIVE_COMPACT,
                RecipeCompositionProfile.MIXED_CARDIO,
            )
        ) {
            NativeProfileKind.fromEntryId(recipe.id)
        } else {
            null
        }
        if (nativeKind != null) findings += checkNativeBlockSemantics(recipe)
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

    /**
     * Los cuatro planes propios tienen cinco semanas de acumulación y una
     * descarga en un bloque distinto (§12.1/§14.3). Este guard se limita a
     * NATIVE_COMPACT/MIXED_CARDIO con ID nativo: legacy y AUTHORED_EXACT
     * conservan íntegramente sus reglas de bloque previas.
     */
    private fun checkNativeBlockSemantics(recipe: TrainingPlanRecipe): List<CompositionFinding> {
        val findings = mutableListOf<CompositionFinding>()
        val weeks = recipe.weeks.sortedBy { it.weekNumber }
        if (weeks.map { it.weekNumber } != (1..6).toList()) {
            findings += hard("BLOCK", "native", "El plan propio requiere exactamente semanas 1–6")
            return findings
        }
        weeks.take(5).forEach { week ->
            if (week.blockIndex != 0 || week.blockGoal != BlockGoal.ACCUMULATION ||
                week.kind != com.example.kpkn.data.models.WeekExecutionKind.TRAINING
            ) {
                findings += hard(
                    "BLOCK",
                    "w${week.weekNumber}",
                    "Las semanas 1–5 deben ser ACCUMULATION/TRAINING en blockIndex 0",
                )
            }
        }
        val deload = weeks.last()
        if (deload.blockIndex != 1 || deload.blockGoal != BlockGoal.DELOAD ||
            deload.kind != com.example.kpkn.data.models.WeekExecutionKind.DELOAD
        ) {
            findings += hard(
                "BLOCK",
                "w6",
                "La semana 6 debe ser DELOAD en blockIndex 1",
            )
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

    /**
     * Quita los hallazgos que una exención declara. H11 y H11b nunca se filtran: son errores
     * de prescripción, no fidelidad de autor.
     *
     * El ámbito de la exención es `"*"` (todo) o un glob ANCLADO en los dos extremos: el asterisco
     * casa con cualquier secuencia de caracteres (la barra incluida) y el resto es literal. Ya no
     * hay `contains` ni `startsWith`: un ámbito sin asterisco solo casa con ese texto exacto.
     *
     * La política emite seis formas de ámbito:
     * - `w{n}/{día}`: hallazgo de un día (reglas H, META, TAXONOMY y las S de día).
     * - `w{n}/{día}/{slot}`: hallazgo de un slot concreto de un día; `{slot}` es el `id` del
     *   `SlotRecipe`. Lo emite el contrato de receta válida ([RecipeContractPolicy]) en C1, C3, C8 y
     *   C9, para poder exentar un slot sin silenciar el resto del día (p. ej. el slot `t1` del día
     *   «Banca/OHP» en cualquier semana: `w*` seguido de la barra, `Banca/OHP`, la barra y `t1`).
     *   Un glob de día (`w*`, la barra y `Banca/OHP`) NO casa los hallazgos por slot (el glob está
     *   anclado): hay que añadirle la barra y un asterisco final (todos los slots del día) o
     *   escribir el slot concreto.
     * - `w{n}`: hallazgo de una semana (reglas W salvo W5, las S de semana y los BLOCK de semana
     *   del plan propio).
     * - `block{i}/{bloque}`: hallazgo de un bloque (BLOCK y W5).
     * - `native`: literal, sin números. Es el BLOCK global «El plan propio requiere exactamente
     *   semanas 1–6» de `checkNativeBlockSemantics`: cuelga de la receta entera y no de una semana,
     *   un día o un bloque. `w*` y los ámbitos de bloque no lo casan: lo silencian `"*"` o el
     *   literal `native`.
     * - `recipe`: literal. Lo usa el contrato de receta válida ([RecipeContractPolicy]) para lo que
     *   cuelga de la receta entera (C5 y C6); el resto de sus reglas usa las formas de arriba
     *   (C1, C3, C8 y C9 por slot; C2 por día; C4 y C10 por semana; C7 por día, semana o bloque).
     *   Igual que `native`, lo silencian `"*"` o el literal `recipe`.
     *
     * Como el asterisco casa también la barra, `w*` silencia a la vez los hallazgos de semana, de
     * día y de slot (todos empiezan por `w`). Para un solo día de cualquier semana se escribe `w*`
     * seguido de la barra y la etiqueta del día (p. ej. la del día «Test»), y para un bloque
     * `block*` seguido de la barra y su nombre (p. ej. «Conjugate»).
     */
    fun applyExemptions(
        findings: List<CompositionFinding>,
        exemptions: List<RecipeCompositionExemption>,
    ): List<CompositionFinding> {
        val matchers = exemptions.map { it.rule to scopeMatcher(it.scope) }
        return findings.filter { finding ->
            if (finding.rule in NON_EXEMPTABLE_RULES) return@filter true
            matchers.none { (rule, matches) -> rule == finding.rule && matches(finding.scope) }
        }
    }

    /** `true` si el ámbito de una exención ([pattern]) casa con el ámbito de un hallazgo ([scope]). */
    internal fun scopeMatches(pattern: String, scope: String): Boolean = scopeMatcher(pattern)(scope)

    private fun scopeMatcher(pattern: String): (String) -> Boolean {
        if (pattern == "*") return { true }
        val regex = Regex(
            "^" + pattern.split("*").joinToString(".*") { Regex.escape(it) } + "$",
            RegexOption.DOT_MATCHES_ALL,
        )
        return { scope -> regex.matches(scope) }
    }

    private fun hard(rule: String, scope: String, message: String) =
        CompositionFinding(CompositionSeverity.HARD, rule, scope, message)

    private fun soft(rule: String, scope: String, message: String) =
        CompositionFinding(CompositionSeverity.SOFT, rule, scope, message)
}
