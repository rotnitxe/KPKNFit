package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolution
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.text.SpanishPlurals
import kotlin.math.abs
import kotlin.math.floor
import kotlin.reflect.KClass

/**
 * Registro de consumidores de la progresión (C6 del contrato de receta): qué reglas de
 * [ProgressionRule] tienen un consumidor REAL en ejecución.
 *
 * - [authored]: las consume [AuthoredProgressionEngine], el motor de progresión de autor.
 *   `CycleIncrement` sube el TM al cerrar el ciclo o el bloque y `WeeklyKg` suma kilos por semana
 *   al materializar la serie.
 * - [autoregulation]: las consume `ProgramAutoregulationEngine` (B.S4) como propuestas `ADJUST_TM`
 *   que respetan OFF, PROPOSE y AUTO: `AmrapDrivenTm` y `RepTargetDrivenTm` por el AMRAP de la
 *   semana, `TopSetPr` por el top set y `RepMaxAutoregulated` por la serie al máximo.
 * - [executable]: la unión de ambas. Una regla que no esté aquí no hace nada al ejecutar el plan.
 *   `WeeklyPercent` sale de las recetas en B.S6 y no tendrá consumidor.
 *
 * `ProgressionConsumerCoverageTest` obliga a clasificar cada regla: una regla nueva, o una receta
 * publicada con una regla sin consumidor que no esté listada como pendiente, rompe esa prueba.
 */
object ProgressionConsumers {
    val authored: Set<KClass<out ProgressionRule>> = setOf(
        ProgressionRule.CycleIncrement::class,
        ProgressionRule.WeeklyKg::class,
        // CAL5: Juggernaut (reps sobre el estándar, al entrar en la ola siguiente) y GZCLP (etapas y TM al cerrar el ciclo).
        ProgressionRule.RepsOverStandardTm::class,
        ProgressionRule.StagedVolume::class,
    )

    val autoregulation: Set<KClass<out ProgressionRule>> = setOf(
        ProgressionRule.AmrapDrivenTm::class,
        ProgressionRule.RepTargetDrivenTm::class,
        ProgressionRule.TopSetPr::class,
        ProgressionRule.RepMaxAutoregulated::class,
        // CAL6: el XRM de la semana siguiente sale de la serie 4 de APRE, como una propuesta de TM.
        ProgressionRule.ApreXrm::class,
    )

    val executable: Set<KClass<out ProgressionRule>> = authored + autoregulation

    fun isExecutable(rule: ProgressionRule): Boolean = rule::class in executable
}

/**
 * Motor de progresión de autor (B.S3): la progresión DEL MÉTODO es contenido de la receta y se
 * aplica siempre, sin pasar por OFF, PROPOSE o AUTO. La progresión POR RENDIMIENTO (`TopSetPr`,
 * AMRAP, `RepMaxAutoregulated`) es otra cosa: va como propuesta `ADJUST_TM` y la gobierna
 * `ProgramAutoregulationEngine`.
 *
 * - `WeeklyKg`: [weeklyOffsetKg] devuelve los kilos que se suman a una serie de trabajo según la
 *   semana de la receta; los aplica `PlanMaterializer.materializeSet`.
 * - `CycleIncrement`: [applyAtCycleClose] y [applyAtBlockClose] suben el TM de cada levantamiento
 *   de `recipe.liftSlots`, rematerializan las semanas que quedan por entrenar y dejan un aviso
 *   único. Cada aplicación es idempotente por `AppliedRecipeProposal` (`author-cycle-c<N>` o
 *   `author-block-b<N>`).
 *
 * Las funciones son puras: no leen reloj ni inventario salvo por parámetro.
 */
object AuthoredProgressionEngine {
    /** Paso de redondeo del TM cuando no hay inventario con discos: medio kilo. */
    const val DEFAULT_ROUNDING_STEP_KG = 0.5

    /** Prefijo del `proposalId` del ciclo: `author-cycle-c<N>`, con N el ciclo NUEVO. */
    const val CYCLE_PROPOSAL_PREFIX = "author-cycle-c"

    /** Prefijo del `proposalId` del bloque: `author-block-b<N>`, con N el índice del bloque al que se entra. */
    const val BLOCK_PROPOSAL_PREFIX = "author-block-b"

    /** `AppliedRecipeProposal.kind` de una subida de TM por ciclo. */
    const val APPLIED_KIND_CYCLE = "AUTHOR_CYCLE_INCREMENT"

    /** `AppliedRecipeProposal.kind` de una subida de TM por bloque. */
    const val APPLIED_KIND_BLOCK = "AUTHOR_BLOCK_INCREMENT"

    private const val TM_EPSILON = 1e-9

    // ─── WeeklyKg ──────────────────────────────────────────────────────────────────

    /**
     * Kilos que `WeeklyKg` suma a una serie de trabajo de la semana [weekNumber] de la receta, o
     * null si no aplica: la regla no es `WeeklyKg`, el slot no declara `liftSlot`, no es la serie
     * principal ([SlotRole.T1_MAIN]) o la semana no está en el mapa.
     *
     * El mapa `semana → kg` no nombra el levantamiento: las recetas con `WeeklyKg` (Smolov y
     * Smolov Jr) especializan un solo levantamiento, la sentadilla, y su trabajo principal es el
     * único slot T1 con `liftSlot`; los accesorios no llevan `liftSlot`.
     */
    fun weeklyOffsetKg(
        rule: ProgressionRule,
        weekNumber: Int,
        liftSlot: LiftSlot?,
        role: SlotRole,
    ): Double? {
        if (rule !is ProgressionRule.WeeklyKg) return null
        if (liftSlot == null || role != SlotRole.T1_MAIN) return null
        return rule.weekToKg[weekNumber]
    }

    // ─── CycleIncrement ────────────────────────────────────────────────────────────

    /**
     * Kilos que `CycleIncrement` suma al TM de [liftSlot]: `upperKg` para banca y press militar,
     * `lowerKg` para sentadilla y peso muerto. Null si la regla no es `CycleIncrement`.
     */
    fun cycleIncrementKg(rule: ProgressionRule, liftSlot: LiftSlot): Double? = when (rule) {
        is ProgressionRule.CycleIncrement -> when (liftSlot) {
            LiftSlot.BENCH, LiftSlot.OVERHEAD -> rule.upperKg
            LiftSlot.SQUAT, LiftSlot.DEADLIFT -> rule.lowerKg
        }
        // GZCLP: el salto del TM cuando el T1 completa su volumen base.
        is ProgressionRule.StagedVolume -> when (liftSlot) {
            LiftSlot.BENCH, LiftSlot.OVERHEAD -> rule.upperKg
            LiftSlot.SQUAT, LiftSlot.DEADLIFT -> rule.lowerKg
        }
        else -> null
    }

    /** true si la regla sube el TM al cerrar el ciclo del programa (null: programa sin receta). */
    fun appliesAtCycleClose(rule: ProgressionRule?): Boolean =
        (rule is ProgressionRule.CycleIncrement && rule.scope == IncrementScope.CYCLE) || rule is ProgressionRule.StagedVolume

    /** true si la regla sube el TM al entrar en el bloque siguiente (null: programa sin receta). */
    fun appliesAtBlockClose(rule: ProgressionRule?): Boolean =
        (rule is ProgressionRule.CycleIncrement && rule.scope == IncrementScope.BLOCK) || rule is ProgressionRule.RepsOverStandardTm

    /**
     * Paso de redondeo del TM: la menor carga que el inventario puede añadir a la barra, el doble
     * del disco más pequeño (uno por lado). Sin discos declarados, [DEFAULT_ROUNDING_STEP_KG].
     */
    fun roundingStepKg(inventory: EquipmentInventory?): Double {
        val smallest = inventory?.plates.orEmpty()
            .filter { it.weightKg > 0.0 && (it.countPerSide ?: 1) > 0 }
            .minOfOrNull { it.weightKg }
        return smallest?.times(2.0) ?: DEFAULT_ROUNDING_STEP_KG
    }

    /**
     * TM siguiente: [currentKg] más [incrementKg], redondeado al múltiplo más cercano de
     * [stepKg] y nunca por debajo del TM actual.
     */
    fun incrementedTm(currentKg: Double, incrementKg: Double, stepKg: Double): Double {
        val target = currentKg + incrementKg
        val rounded = if (stepKg > 0.0) floor(target / stepKg + 0.5) * stepKg else target
        return maxOf(rounded, currentKg)
    }

    /**
     * Cambio de TM de un levantamiento al aplicar una subida (o, en 5/3/1, un reinicio del −10 %). [note] es el porqué para el
     * aviso: «reinicio −10 %», «13 repeticiones con estándar de 5»…
     */
    data class TmChange(val liftSlot: LiftSlot, val beforeKg: Double, val afterKg: Double, val note: String? = null)

    /**
     * Subida del método que NO se aplica este cierre porque el levantamiento hizo un AMRAP corto en el
     * ciclo o bloque que termina: su TM se mantiene en [tmKg]. [note] sustituye al «AMRAP corto» del aviso (Juggernaut:
     * «no llegaste al estándar», GZCLP: «volumen base sin completar»).
     */
    data class TmHold(val liftSlot: LiftSlot, val tmKg: Double, val note: String? = null)

    /**
     * Lo que pasó en el ciclo o bloque que se cierra, leído de los registros por quien llama (la regla del método decide qué
     * parte usa): los AMRAP del cierre (Juggernaut), las rachas de ciclos congelados (5/3/1) y el volumen de los T1 y T2 (GZCLP).
     */
    data class CloseEvidence(
        val amrapHits: List<AmrapHit> = emptyList(),
        /** Ciclos seguidos con AMRAP corto por levantamiento, contando el que se cierra (5/3/1). */
        val frozenStreaks: Map<LiftSlot, Int> = emptyMap(),
        val gzclpChecks: List<ProgramAutoregulationEngine.GzclpTierCheck> = emptyList(),
    )

    /** El resultado completo de la regla del método sobre un cierre: TM que cambian, TM que se mantienen y etapas de GZCLP. */
    data class MethodOutcome(
        val changes: List<TmChange>,
        val holds: List<TmHold>,
        /** GZCLP: etapa del T1 / T2 de cada levantamiento con registros para el ciclo NUEVO. */
        val t1Stages: Map<LiftSlot, GzclpProgression.T1Stage> = emptyMap(),
        val t2Stages: Map<LiftSlot, GzclpProgression.T2Stage> = emptyMap(),
        /** Frases extra para el aviso (reinicio de GZCLP, T3…). */
        val notes: List<String> = emptyList(),
    )

    /**
     * Cambios de TM que produce la regla de [recipe] sobre [profile]: un levantamiento de
     * `recipe.liftSlots` con TM (guardado, o derivado de su 1RM) y con subida positiva. Solo
     * devuelve los que cambian, en el orden de [LiftSlot]. El paso de redondeo [stepKg] se acota
     * al incremento de cada levantamiento. Los levantamientos de [excludedLifts] (un AMRAP corto
     * en el ciclo o bloque que se cierra) no suben: quedan en [tmHolds]. Con [evidence] la regla de cada método usa lo
     * registrado: las rachas de 5/3/1 (reinicio −10 %), las repeticiones de Juggernaut y el volumen de GZCLP.
     */
    fun tmChanges(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double = DEFAULT_ROUNDING_STEP_KG,
        excludedLifts: Set<LiftSlot> = emptySet(),
        evidence: CloseEvidence = CloseEvidence(),
    ): List<TmChange> = evaluate(profile, recipe, stepKg, excludedLifts, evidence).changes

    /**
     * Los levantamientos a los que el método les habría subido el TM y se les congela (un AMRAP corto de [excludedLifts],
     * un volumen sin completar en GZCLP o un estándar sin superar en Juggernaut). Un levantamiento sin subida (sin TM o con
     * incremento cero) no aparece: no hay nada que explicar. En el orden de [LiftSlot].
     */
    fun tmHolds(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double = DEFAULT_ROUNDING_STEP_KG,
        excludedLifts: Set<LiftSlot> = emptySet(),
        evidence: CloseEvidence = CloseEvidence(),
    ): List<TmHold> = evaluate(profile, recipe, stepKg, excludedLifts, evidence).holds

    /** La regla del método de [recipe] aplicada a un cierre: qué cambia, qué se mantiene y, en GZCLP, qué etapa toca. */
    fun evaluate(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double = DEFAULT_ROUNDING_STEP_KG,
        excludedLifts: Set<LiftSlot> = emptySet(),
        evidence: CloseEvidence = CloseEvidence(),
    ): MethodOutcome = when (val rule = recipe.progression) {
        is ProgressionRule.CycleIncrement -> evaluateCycleIncrement(profile, recipe, rule, stepKg, excludedLifts, evidence)
        is ProgressionRule.StagedVolume -> evaluateStaged(profile, recipe, rule, stepKg, evidence)
        is ProgressionRule.RepsOverStandardTm -> evaluateRepsOverStandard(profile, recipe, rule, stepKg, evidence)
        else -> MethodOutcome(emptyList(), emptyList())
    }

    /**
     * `CycleIncrement`: el salto fijo del método por levantamiento. Un AMRAP corto lo congela (sin bajar el TM) y, con
     * `resetAfterFrozenCycles` (5/3/1), tras esa racha de ciclos congelados seguidos el TM se reinicia `resetPercent` %.
     */
    private fun evaluateCycleIncrement(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        rule: ProgressionRule.CycleIncrement,
        stepKg: Double,
        excludedLifts: Set<LiftSlot>,
        evidence: CloseEvidence,
    ): MethodOutcome {
        val changes = mutableListOf<TmChange>()
        val holds = mutableListOf<TmHold>()
        LiftSlot.entries.filter { it in recipe.liftSlots }.forEach { lift ->
            val increment = cycleIncrementKg(rule, lift)?.takeIf { it > 0.0 } ?: return@forEach
            val current = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent) ?: return@forEach
            if (lift in excludedLifts) {
                val streak = evidence.frozenStreaks[lift] ?: 1
                if (Wendler531Progression.resetsAtFrozenStreak(streak, rule.resetAfterFrozenCycles)) {
                    val reset = Wendler531Progression.resetTmKg(current, rule.resetPercent, stepKg)
                    if (abs(reset - current) >= TM_EPSILON) {
                        changes += TmChange(
                            lift, current, reset,
                            note = "reinicio −${NativeProgressionText.formatKg(rule.resetPercent)} % tras ${rule.resetAfterFrozenCycles} ciclos congelados",
                        )
                        return@forEach
                    }
                }
                holds += TmHold(lift, current)
                return@forEach
            }
            // El paso de redondeo nunca supera el incremento del método: con discos de 2,5 kg (paso
            // de 5 kg) una subida de 2,5 kg saltaría 5.
            val next = incrementedTm(current, increment, minOf(stepKg, increment))
            if (abs(next - current) >= TM_EPSILON) changes += TmChange(lift, current, next)
        }
        return MethodOutcome(changes, holds)
    }

    /**
     * `RepsOverStandardTm` (Juggernaut): el TM de cada levantamiento con el AMRAP de realización del bloque que se cierra.
     * Sin AMRAP registrado el TM no cambia ni se avisa; con él, sube `(reps − estándar) × kg` o se mantiene con el aviso.
     */
    private fun evaluateRepsOverStandard(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        rule: ProgressionRule.RepsOverStandardTm,
        stepKg: Double,
        evidence: CloseEvidence,
    ): MethodOutcome {
        val changes = mutableListOf<TmChange>()
        val holds = mutableListOf<TmHold>()
        LiftSlot.entries.filter { it in recipe.liftSlots }.forEach { lift ->
            val current = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent) ?: return@forEach
            val hit = evidence.amrapHits
                .filter { it.liftSlot == lift && it.purpose.isAmrap && it.prescribedReps > 0 }
                .maxByOrNull { it.percent ?: 0.0 }
                ?: return@forEach
            val result = JuggernautProgression.evaluate(rule, hit) ?: return@forEach
            if (result.outcome == JuggernautProgression.Outcome.RAISED && result.deltaKg > 0.0) {
                val next = incrementedTm(current, result.deltaKg, minOf(stepKg, result.deltaKg))
                if (abs(next - current) >= TM_EPSILON) {
                    changes += TmChange(lift, current, next, note = JuggernautProgression.summary(result))
                    return@forEach
                }
            }
            holds += TmHold(lift, current, note = JuggernautProgression.summary(result))
        }
        return MethodOutcome(changes, holds)
    }

    /**
     * `StagedVolume` (GZCLP): el T1 completa su volumen base y el TM sube el salto del método; si no, el TM se mantiene y el
     * T1 pasa a la etapa siguiente con la misma carga (la etapa 3 sin completar es el reinicio: 5RM nuevo × 85 %). El T2 sigue
     * el mismo criterio con sus propias etapas. Sin registros de un levantamiento el TM sube como si lo hubiera completado.
     */
    private fun evaluateStaged(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        rule: ProgressionRule.StagedVolume,
        stepKg: Double,
        evidence: CloseEvidence,
    ): MethodOutcome {
        val changes = mutableListOf<TmChange>()
        val holds = mutableListOf<TmHold>()
        val t1Stages = linkedMapOf<LiftSlot, GzclpProgression.T1Stage>()
        val t2Stages = linkedMapOf<LiftSlot, GzclpProgression.T2Stage>()
        val notes = mutableListOf<String>()
        LiftSlot.entries.filter { it in recipe.liftSlots }.forEach { lift ->
            val t1Checks = evidence.gzclpChecks.filter { it.liftSlot == lift && it.role == SlotRole.T1_MAIN }
            val t2Checks = evidence.gzclpChecks.filter { it.liftSlot == lift && it.role == SlotRole.T2_SUPPLEMENTAL }
            val name = liftName(lift)

            // T2: etapas propias, sin efecto sobre el TM.
            if (t2Checks.isNotEmpty()) {
                val stage = GzclpProgression.t2StageOfBaseVolume(t2Checks.first().plannedReps) ?: GzclpProgression.T2Stage.STAGE_1
                val decision = GzclpProgression.decideT2(stage, t2Checks.all { it.completedBaseVolume })
                t2Stages[lift] = GzclpProgression.T2Stage.entries[decision.toStage - 1]
                when (decision.outcome) {
                    GzclpProgression.TierOutcome.PASSED -> Unit
                    GzclpProgression.TierOutcome.ADVANCE_STAGE ->
                        notes += "T2 de $name sin completar su volumen base: pasa a ${t2Stages.getValue(lift).label}, con la misma carga."
                    GzclpProgression.TierOutcome.RESTART ->
                        notes += "T2 de $name sin completar la etapa 3: reinicia en 3×10."
                }
            }

            val increment = cycleIncrementKg(rule, lift)?.takeIf { it > 0.0 }
            val current = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent)
            if (t1Checks.isEmpty()) {
                if (increment != null && current != null) {
                    val next = incrementedTm(current, increment, minOf(stepKg, increment))
                    if (abs(next - current) >= TM_EPSILON) changes += TmChange(lift, current, next)
                }
                return@forEach
            }
            val stage = GzclpProgression.t1StageOfBaseVolume(t1Checks.first().plannedReps) ?: GzclpProgression.T1Stage.STAGE_1
            val decision = GzclpProgression.decideT1(stage, t1Checks.all { it.completedBaseVolume })
            val nextStage = GzclpProgression.T1Stage.entries[decision.toStage - 1]
            t1Stages[lift] = nextStage
            when (decision.outcome) {
                GzclpProgression.TierOutcome.PASSED -> if (increment != null && current != null) {
                    val next = incrementedTm(current, increment, minOf(stepKg, increment))
                    if (abs(next - current) >= TM_EPSILON) changes += TmChange(lift, current, next)
                }
                GzclpProgression.TierOutcome.ADVANCE_STAGE -> if (current != null) {
                    holds += TmHold(
                        lift, current,
                        note = "volumen base sin completar: pasas de ${stage.label} a ${nextStage.label} con la misma carga",
                    )
                }
                GzclpProgression.TierOutcome.RESTART -> {
                    if (current != null) {
                        holds += TmHold(lift, current, note = "etapa 3 sin completar: toca reiniciar")
                    }
                    notes += "T1 de $name: etapa 3 sin completar. Descansa 2 o 3 días, prueba un 5RM nuevo y empieza otro ciclo de " +
                        "${GzclpProgression.T1Stage.STAGE_1.label} con el 85 % de ese 5RM (pon ese 5RM como tu máximo de entrenamiento)."
                }
            }
        }
        // T3: sube la carga cuando la última serie llega a las repeticiones de la regla (25 en Lefever). Sin TM de accesorio, el
        // cambio es un aviso para el atleta, que es quien tiene la carga.
        evidence.gzclpChecks
            .filter { it.role == SlotRole.T3_ACCESSORY && it.lastSetReps != null && GzclpProgression.t3ShouldRaise(it.lastSetReps, rule.t3RaiseReps) }
            .mapNotNull { it.exerciseName }
            .distinct()
            .forEach { name ->
                notes += "T3 $name: la última serie llegó a ${rule.t3RaiseReps} repeticiones; sube la carga."
            }
        return MethodOutcome(changes, holds, t1Stages, t2Stages, notes)
    }

    // ─── Aplicación al cerrar ciclo o bloque ───────────────────────────────────────

    /**
     * Sube el TM al cerrar el ciclo si la receta declara `CycleIncrement(scope = CYCLE)`.
     * [program] ya tiene el cursor en el ciclo [newCycle] (si no, `weekRecipeSourceFor`
     * arrastraría las semanas escaladas del ciclo cerrado). Todas las semanas base se
     * rematerializan con `executedWeekIds = ∅`: en el ciclo nuevo no hay nada entrenado. Las
     * sesiones con ajustes manuales se conservan.
     *
     * Solo se reconstruyen las semanas de bloques que vienen de la receta
     * (`Block.sourceDefinitionId == recipe.id`): la «Descarga (auto)» que inserta AUGE no es de la
     * receta y `rematerializeWeek` la sustituiría por la semana 1 completa. Los días que el atleta
     * movió arrastrando una sesión se conservan.
     *
     * Sin perfil de cargas, sin TM que subir, sin semanas de la receta o con progresión nativa
     * activa devuelve [program] sin tocar. Sin metadatos del catálogo (o si la reconstrucción
     * falla) el TM sube igual, pero las semanas no se tocan: los bloques quedan con
     * `materializationPending` para que el botón RE-MATERIALIZAR las recalcule.
     *
     * @param firstWeekOccurrence ocurrencia de la primera semana del ciclo nuevo: ancla de la guarda.
     * @param excludedLifts levantamientos con un AMRAP corto en el ciclo que se cierra (B.S4): no
     *   suben este ciclo y el aviso lo dice («banca se mantiene en 108 kg (AMRAP corto)»).
     * @param evidence lo registrado en el ciclo que se cierra (CAL5): las rachas de 5/3/1 y el volumen de GZCLP.
     */
    fun applyAtCycleClose(
        program: Program,
        newCycle: Int,
        firstWeekOccurrence: Int,
        metadata: ExerciseCompositionMetadataProvider?,
        inventory: EquipmentInventory?,
        nowMs: Long = System.currentTimeMillis(),
        excludedLifts: Set<LiftSlot> = emptySet(),
        evidence: CloseEvidence = CloseEvidence(),
    ): Program {
        val recipe = program.sourceRecipe ?: return program
        if (!appliesAtCycleClose(recipe.progression) || usesNativeProgression(recipe)) return program
        val weekIds = ProgramHierarchyIndex(program).orderedWeeks()
            .filter { it.block.sourceDefinitionId == recipe.id }
            .filterNot { it.week.isLoopWeek }
            .map { it.week.id }
        return applyIncrement(
            program = program,
            recipe = recipe,
            proposalId = "$CYCLE_PROPOSAL_PREFIX$newCycle",
            appliedKind = APPLIED_KIND_CYCLE,
            label = "Nuevo ciclo",
            markerOccurrence = firstWeekOccurrence,
            markerCycle = newCycle,
            weekIds = weekIds,
            protectedSessionIds = emptySet(),
            metadata = metadata,
            inventory = inventory,
            nowMs = nowMs,
            excludedLifts = excludedLifts,
            evidence = evidence,
        )
    }

    /**
     * Sube el TM al entrar en [enteredBlockId] si la receta declara
     * `CycleIncrement(scope = BLOCK)` o `RepsOverStandardTm` (Juggernaut). [program] ya tiene el cursor en el bloque nuevo. Se
     * rematerializan las semanas de ese bloque y de los siguientes que vienen de la receta;
     * [protectedSessionIds] (las sesiones que ya tienen registros) no se reconstruyen. Mismas
     * garantías que [applyAtCycleClose]. Entrar al primer bloque no es un cierre de bloque, y entrar
     * en un bloque que no es de la receta (la «Descarga (auto)» de AUGE) tampoco: no hace nada.
     *
     * H7: todas las entradas a un bloque llegan aquí por `ProgramProgressEngine.applyAuthoredBlockEntry`:
     * el avance normal, rechazar la descarga de AUGE y resolver el test de 1RM. Aceptar la descarga entra
     * en la «Descarga (auto)», que esta función ignora por no ser de la receta.
     *
     * @param excludedLifts levantamientos con un AMRAP corto en el bloque que se cierra (B.S4): no
     *   suben al entrar en el bloque nuevo y el aviso lo dice.
     * @param evidence lo registrado en el bloque que se cierra (CAL5): el AMRAP de realización de Juggernaut.
     */
    fun applyAtBlockClose(
        program: Program,
        enteredBlockId: String,
        metadata: ExerciseCompositionMetadataProvider?,
        inventory: EquipmentInventory?,
        protectedSessionIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
        excludedLifts: Set<LiftSlot> = emptySet(),
        evidence: CloseEvidence = CloseEvidence(),
    ): Program {
        val recipe = program.sourceRecipe ?: return program
        if (!appliesAtBlockClose(recipe.progression) || usesNativeProgression(recipe)) return program
        val blocks = program.macrocycles.flatMap { it.blocks }
        val blockIndex = blocks.indexOfFirst { it.id == enteredBlockId }
        if (blockIndex <= 0) return program
        if (blocks[blockIndex].sourceDefinitionId != recipe.id) return program
        val weeksFromBlock = ProgramHierarchyIndex(program).orderedWeeks()
            .dropWhile { it.blockId != enteredBlockId }
            .filter { it.block.sourceDefinitionId == recipe.id }
            .filterNot { it.week.isLoopWeek }
        val firstWeek = weeksFromBlock.firstOrNull()?.week ?: return program
        return applyIncrement(
            program = program,
            recipe = recipe,
            proposalId = "$BLOCK_PROPOSAL_PREFIX$blockIndex",
            appliedKind = APPLIED_KIND_BLOCK,
            label = "Nuevo bloque",
            markerOccurrence = firstWeek.progressionIndex ?: 1,
            markerCycle = program.runState?.cycleNumber ?: 1,
            weekIds = weeksFromBlock.map { it.week.id },
            protectedSessionIds = protectedSessionIds,
            metadata = metadata,
            inventory = inventory,
            nowMs = nowMs,
            excludedLifts = excludedLifts,
            evidence = evidence,
        )
    }

    /** Con progresión nativa activa la regla de autor no se usa (`nativeProgression` manda). */
    private fun usesNativeProgression(recipe: TrainingPlanRecipe): Boolean =
        recipe.nativeProgression?.strategy?.let { it != NativeProgressionStrategy.NONE } == true

    private fun applyIncrement(
        program: Program,
        recipe: TrainingPlanRecipe,
        proposalId: String,
        appliedKind: String,
        label: String,
        markerOccurrence: Int,
        markerCycle: Int,
        weekIds: List<String>,
        protectedSessionIds: Set<String>,
        metadata: ExerciseCompositionMetadataProvider?,
        inventory: EquipmentInventory?,
        nowMs: Long,
        excludedLifts: Set<LiftSlot>,
        evidence: CloseEvidence,
    ): Program {
        val existing = PlanMaterializer.effectiveWeekRecipeFor(program, markerOccurrence, markerCycle)
        if (existing?.appliedProposals?.any { it.proposalId == proposalId } == true) return program
        // Sin semanas de la receta que reconstruir no hay nada a lo que aplicar el TM nuevo.
        if (weekIds.isEmpty()) return program
        val profile = program.powerliftingProfile ?: return program
        val stepKg = roundingStepKg(inventory)
        val outcome = evaluate(profile, recipe, stepKg, excludedLifts, evidence)
        val changes = outcome.changes
        val holds = outcome.holds
        // GZCLP: las etapas del ciclo nuevo solo se escriben si alguna deja la etapa 1 (la receta ya es la etapa 1).
        val stageOverlay = outcome.t1Stages.values.any { it != GzclpProgression.T1Stage.STAGE_1 } ||
            outcome.t2Stages.values.any { it != GzclpProgression.T2Stage.STAGE_1 }
        if (changes.isEmpty() && holds.isEmpty() && !stageOverlay && outcome.notes.isEmpty()) return program
        // Todo lo que el método subiría se congela (AMRAP corto, estándar sin superar…): no hay TM que cambiar ni semanas que
        // reconstruir, pero el atleta debe saber por qué no sube y la guarda deja el cierre registrado.
        if (changes.isEmpty() && !stageOverlay) {
            return holdOnly(program, proposalId, appliedKind, label, markerOccurrence, markerCycle, holds, nowMs, outcome.notes)
        }

        val updatedProfile = changes.fold(profile) { acc, change -> acc.withTm(change.liftSlot, change.afterKg) }
        val withProfile = if (stageOverlay) {
            withStageOverlay(program.copy(powerliftingProfile = updatedProfile), recipe, markerCycle, outcome)
        } else {
            program.copy(powerliftingProfile = updatedProfile)
        }

        val weekIdSet = weekIds.toSet()
        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        val preservedSessions = program.macrocycles.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }
            .flatMap { it.weeks.asSequence() }
            .filter { it.id in weekIdSet }
            .flatMap { it.sessions.asSequence() }
            .count { it.id in frozen && it.id !in protectedSessionIds }

        val rebuild = if (metadata == null) {
            Rebuild.Pending("sin metadatos del catálogo")
        } else {
            rematerialize(withProfile, recipe, metadata, weekIds, protectedSessionIds)
        }
        val materialized = (rebuild as? Rebuild.Done)?.program ?: markMaterializationPending(withProfile, weekIdSet)
        val text = noticeText(
            label,
            changes,
            preservedSessions,
            pendingMaterialization = rebuild is Rebuild.Pending,
            held = holds,
            notes = outcome.notes,
        )
        // El aviso es para el atleta; el porqué técnico de un fallback queda en el marcador.
        val summary = if (rebuild is Rebuild.Pending) "$text [reconstrucción pendiente: ${rebuild.reason}]" else text

        val marked = PlanMaterializer.withEffectiveWeekRecipe(
            program = materialized,
            weekOccurrence = markerOccurrence,
            cycleNumber = markerCycle,
            weekRecipe = null,
            applied = listOf(
                AppliedRecipeProposal(
                    proposalId = proposalId,
                    kind = appliedKind,
                    summary = summary,
                    acceptedAtMs = nowMs,
                ),
            ),
        )
        return withNotice(marked, proposalId, text, nowMs)
    }

    /**
     * Cierre en el que el AMRAP corto congela TODAS las subidas del método: el TM no cambia, así que no
     * se rematerializa nada. Deja el aviso del atleta y la marca idempotente del cierre.
     */
    private fun holdOnly(
        program: Program,
        proposalId: String,
        appliedKind: String,
        label: String,
        markerOccurrence: Int,
        markerCycle: Int,
        holds: List<TmHold>,
        nowMs: Long,
        notes: List<String> = emptyList(),
    ): Program {
        val text = noticeText(label, emptyList(), preservedSessions = 0, pendingMaterialization = false, held = holds, notes = notes)
        val marked = PlanMaterializer.withEffectiveWeekRecipe(
            program = program,
            weekOccurrence = markerOccurrence,
            cycleNumber = markerCycle,
            weekRecipe = null,
            applied = listOf(
                AppliedRecipeProposal(
                    proposalId = proposalId,
                    kind = appliedKind,
                    summary = text,
                    acceptedAtMs = nowMs,
                ),
            ),
        )
        return withNotice(marked, proposalId, text, nowMs)
    }

    /**
     * GZCLP: escribe, para cada semana de la receta en el ciclo [cycle], la receta efectiva con el T1 y el T2 de cada
     * levantamiento en su etapa nueva. La reconstrucción que sigue (`rematerializeWeek`) lee esa receta efectiva en vez de la base.
     */
    private fun withStageOverlay(
        program: Program,
        recipe: TrainingPlanRecipe,
        cycle: Int,
        outcome: MethodOutcome,
    ): Program = recipe.weeks.fold(program) { acc, week ->
        val base = PlanMaterializer.effectiveWeekRecipeFor(acc, week.weekNumber, cycle)?.weekRecipe ?: week
        PlanMaterializer.withEffectiveWeekRecipe(
            program = acc,
            weekOccurrence = week.weekNumber,
            cycleNumber = cycle,
            weekRecipe = GzclpProgression.overlay(base, outcome.t1Stages, outcome.t2Stages),
            applied = emptyList(),
        )
    }

    /** Resultado de reconstruir las semanas tras subir el TM. */
    private sealed interface Rebuild {
        data class Done(val program: Program) : Rebuild

        /** No se pudo reconstruir: las semanas quedan `materializationPending`; [reason] es el porqué técnico. */
        data class Pending(val reason: String) : Rebuild
    }

    /**
     * Reconstruye [weekIds] en orden con `executedWeekIds = ∅` y restaura los días que el atleta
     * había movido arrastrando sesiones (`adoptSessionIdentity` toma el día de la sesión nueva y
     * esas sesiones no se congelan). Si la reconstrucción falla (p. ej. una configuración sin
     * metadatos) devuelve [Rebuild.Pending]: cerrar un ciclo nunca puede romper el registro de la
     * sesión que lo cierra, así que el llamante cae a `materializationPending`.
     */
    private fun rematerialize(
        program: Program,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        weekIds: List<String>,
        protectedSessionIds: Set<String>,
    ): Rebuild = try {
        val rebuilt = weekIds.fold(program) { acc, weekId ->
            PlanMaterializer.rematerializeWeek(
                program = acc,
                weekId = weekId,
                recipe = recipe,
                metadata = metadata,
                executedWeekIds = emptySet(),
                executedSessionIds = protectedSessionIds,
            )
        }
        Rebuild.Done(restoreSessionDays(previous = program, rebuilt = rebuilt, weekIds = weekIds.toSet()))
    } catch (failure: RuntimeException) {
        // El llamante deja la prescripción pendiente de materializar; el TM nuevo ya está guardado.
        Rebuild.Pending("${failure::class.simpleName}: ${failure.message}")
    }

    /**
     * Devuelve a cada sesión de [weekIds] el `dayOfWeek` y los `assignedDays` que tenía en
     * [previous] (mismo id de sesión). Sin personalización por arrastre no cambia nada.
     */
    private fun restoreSessionDays(previous: Program, rebuilt: Program, weekIds: Set<String>): Program {
        val previousById = previous.macrocycles.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }
            .flatMap { it.weeks.asSequence() }
            .filter { it.id in weekIds }
            .flatMap { it.sessions.asSequence() }
            .associateBy { it.id }
        return rebuilt.copy(
            macrocycles = rebuilt.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id !in weekIds) {
                                            week
                                        } else {
                                            week.copy(
                                                sessions = week.sessions.map { session ->
                                                    val before = previousById[session.id]
                                                    if (before == null ||
                                                        (before.dayOfWeek == session.dayOfWeek &&
                                                            before.assignedDays == session.assignedDays)
                                                    ) {
                                                        session
                                                    } else {
                                                        session.copy(
                                                            dayOfWeek = before.dayOfWeek,
                                                            assignedDays = before.assignedDays,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private fun markMaterializationPending(program: Program, weekIds: Set<String>): Program =
        program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        if (block.mesocycles.any { meso -> meso.weeks.any { it.id in weekIds } }) {
                            block.copy(materializationPending = true)
                        } else {
                            block
                        }
                    },
                )
            },
        )

    private fun withNotice(program: Program, noticeId: String, text: String, nowMs: Long): Program {
        if (program.nativeProgressionAudit.any { it.proposalId == noticeId }) return program
        return program.copy(
            nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
                proposalId = noticeId,
                status = NativeProgressionResolutionStatus.NOTICE,
                kind = NativeProgressionProposalKind.INCREASE_LOAD,
                resolvedAtMs = nowMs,
                reason = text,
                userFacingNotice = true,
            ),
        )
    }

    /**
     * «Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 120 → 122,5 kg.» con coma decimal y solo los
     * levantamientos que cambian, más los que se mantienen por un AMRAP corto («; banca se mantiene en
     * 108 kg (AMRAP corto)»), cuántas sesiones con ajustes manuales se conservaron y, si no se pudo
     * reconstruir, cómo aplicar las cargas nuevas.
     */
    internal fun noticeText(
        label: String,
        changes: List<TmChange>,
        preservedSessions: Int,
        pendingMaterialization: Boolean,
        held: List<TmHold> = emptyList(),
        notes: List<String> = emptyList(),
    ): String = buildString {
        append(label).append(':')
        if (changes.isNotEmpty()) {
            append(" TM ")
            append(
                changes.joinToString(", ") { change ->
                    "${liftName(change.liftSlot)} ${NativeProgressionText.formatKg(change.beforeKg)} → " +
                        "${NativeProgressionText.formatKg(change.afterKg)} kg" +
                        (change.note?.let { " ($it)" }.orEmpty())
                },
            )
        }
        if (held.isNotEmpty()) {
            append(if (changes.isEmpty()) " " else "; ")
            append(heldClause(held))
        }
        // Sin cambios ni congelaciones (solo avisos de GZCLP) el aviso arranca directamente con su frase.
        if (changes.isNotEmpty() || held.isNotEmpty()) append('.')
        notes.forEach { note -> append(' ').append(note) }
        if (preservedSessions > 0) {
            append(' ')
            append(SpanishPlurals.sessions(preservedSessions))
            append(" con ajustes manuales ")
            append(SpanishPlurals.choose(preservedSessions, "se conservó", "se conservaron"))
            append(" sin cambios.")
        }
        if (pendingMaterialization) append(" Las cargas nuevas se aplican al pulsar «Re-materializar».")
    }

    /** «banca se mantiene en 108 kg (AMRAP corto)»; con varios, «…, … y …». */
    private fun heldClause(held: List<TmHold>): String {
        val parts = held.map { hold ->
            "${liftName(hold.liftSlot)} se mantiene en ${NativeProgressionText.formatKg(hold.tmKg)} kg (${hold.note ?: "AMRAP corto"})"
        }
        return if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    }

    /** Nombre del levantamiento en español llano; lo comparten los avisos y las propuestas de TM. */
    internal fun liftName(lift: LiftSlot): String = when (lift) {
        LiftSlot.SQUAT -> "sentadilla"
        LiftSlot.BENCH -> "banca"
        LiftSlot.DEADLIFT -> "peso muerto"
        LiftSlot.OVERHEAD -> "press militar"
    }

    private fun PowerliftingProfile.withTm(lift: LiftSlot, tm: Double): PowerliftingProfile = when (lift) {
        LiftSlot.SQUAT -> copy(squatTM = tm)
        LiftSlot.BENCH -> copy(benchTM = tm)
        LiftSlot.DEADLIFT -> copy(deadliftTM = tm)
        LiftSlot.OVERHEAD -> copy(overheadTM = tm)
    }
}
