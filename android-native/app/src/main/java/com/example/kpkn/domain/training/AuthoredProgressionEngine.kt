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
    )

    val autoregulation: Set<KClass<out ProgressionRule>> = setOf(
        ProgressionRule.AmrapDrivenTm::class,
        ProgressionRule.RepTargetDrivenTm::class,
        ProgressionRule.TopSetPr::class,
        ProgressionRule.RepMaxAutoregulated::class,
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
    fun cycleIncrementKg(rule: ProgressionRule, liftSlot: LiftSlot): Double? {
        if (rule !is ProgressionRule.CycleIncrement) return null
        return when (liftSlot) {
            LiftSlot.BENCH, LiftSlot.OVERHEAD -> rule.upperKg
            LiftSlot.SQUAT, LiftSlot.DEADLIFT -> rule.lowerKg
        }
    }

    /** true si la regla sube el TM al cerrar el ciclo del programa (null: programa sin receta). */
    fun appliesAtCycleClose(rule: ProgressionRule?): Boolean =
        rule is ProgressionRule.CycleIncrement && rule.scope == IncrementScope.CYCLE

    /** true si la regla sube el TM al entrar en el bloque siguiente (null: programa sin receta). */
    fun appliesAtBlockClose(rule: ProgressionRule?): Boolean =
        rule is ProgressionRule.CycleIncrement && rule.scope == IncrementScope.BLOCK

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

    /** Cambio de TM de un levantamiento al aplicar una subida. */
    data class TmChange(val liftSlot: LiftSlot, val beforeKg: Double, val afterKg: Double)

    /**
     * Subida del método que NO se aplica este cierre porque el levantamiento hizo un AMRAP corto en el
     * ciclo o bloque que termina: su TM se mantiene en [tmKg].
     */
    data class TmHold(val liftSlot: LiftSlot, val tmKg: Double)

    /**
     * Cambios de TM que produce la regla de [recipe] sobre [profile]: un levantamiento de
     * `recipe.liftSlots` con TM (guardado, o derivado de su 1RM) y con subida positiva. Solo
     * devuelve los que cambian, en el orden de [LiftSlot]. El paso de redondeo [stepKg] se acota
     * al incremento de cada levantamiento. Los levantamientos de [excludedLifts] (un AMRAP corto
     * en el ciclo o bloque que se cierra) no suben: quedan en [tmHolds].
     */
    fun tmChanges(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double = DEFAULT_ROUNDING_STEP_KG,
        excludedLifts: Set<LiftSlot> = emptySet(),
    ): List<TmChange> = methodChanges(profile, recipe, stepKg).filterNot { it.liftSlot in excludedLifts }

    /**
     * Los levantamientos de [excludedLifts] a los que el método les habría subido el TM y se les
     * congela. Un levantamiento sin subida (sin TM o con incremento cero) no aparece: no hay nada que
     * explicar. En el orden de [LiftSlot].
     */
    fun tmHolds(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double = DEFAULT_ROUNDING_STEP_KG,
        excludedLifts: Set<LiftSlot> = emptySet(),
    ): List<TmHold> =
        if (excludedLifts.isEmpty()) {
            emptyList()
        } else {
            methodChanges(profile, recipe, stepKg)
                .filter { it.liftSlot in excludedLifts }
                .map { TmHold(it.liftSlot, it.beforeKg) }
        }

    /** La subida completa del método, sin excluir ningún levantamiento. */
    private fun methodChanges(
        profile: PowerliftingProfile,
        recipe: TrainingPlanRecipe,
        stepKg: Double,
    ): List<TmChange> = LiftSlot.entries
        .filter { it in recipe.liftSlots }
        .mapNotNull { lift ->
            val increment = cycleIncrementKg(recipe.progression, lift)?.takeIf { it > 0.0 }
                ?: return@mapNotNull null
            val current = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent)
                ?: return@mapNotNull null
            // El paso de redondeo nunca supera el incremento del método: con discos de 2,5 kg (paso
            // de 5 kg) una subida de 2,5 kg saltaría 5.
            val next = incrementedTm(current, increment, minOf(stepKg, increment))
            if (abs(next - current) < TM_EPSILON) null else TmChange(lift, current, next)
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
     */
    fun applyAtCycleClose(
        program: Program,
        newCycle: Int,
        firstWeekOccurrence: Int,
        metadata: ExerciseCompositionMetadataProvider?,
        inventory: EquipmentInventory?,
        nowMs: Long = System.currentTimeMillis(),
        excludedLifts: Set<LiftSlot> = emptySet(),
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
        )
    }

    /**
     * Sube el TM al entrar en [enteredBlockId] si la receta declara
     * `CycleIncrement(scope = BLOCK)`. [program] ya tiene el cursor en el bloque nuevo. Se
     * rematerializan las semanas de ese bloque y de los siguientes que vienen de la receta;
     * [protectedSessionIds] (las sesiones que ya tienen registros) no se reconstruyen. Mismas
     * garantías que [applyAtCycleClose]. Entrar al primer bloque no es un cierre de bloque, y entrar
     * en un bloque que no es de la receta (la «Descarga (auto)» de AUGE) tampoco: no hace nada.
     *
     * Nota: `resolvePendingDeload(reject)` y `advanceAfterPendingAction` entran al bloque sin pasar
     * por aquí; hay que cubrirlos antes de activar BLOCK en Juggernaut (B.S6).
     *
     * @param excludedLifts levantamientos con un AMRAP corto en el bloque que se cierra (B.S4): no
     *   suben al entrar en el bloque nuevo y el aviso lo dice.
     */
    fun applyAtBlockClose(
        program: Program,
        enteredBlockId: String,
        metadata: ExerciseCompositionMetadataProvider?,
        inventory: EquipmentInventory?,
        protectedSessionIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
        excludedLifts: Set<LiftSlot> = emptySet(),
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
    ): Program {
        val existing = PlanMaterializer.effectiveWeekRecipeFor(program, markerOccurrence, markerCycle)
        if (existing?.appliedProposals?.any { it.proposalId == proposalId } == true) return program
        // Sin semanas de la receta que reconstruir no hay nada a lo que aplicar el TM nuevo.
        if (weekIds.isEmpty()) return program
        val profile = program.powerliftingProfile ?: return program
        val stepKg = roundingStepKg(inventory)
        val changes = tmChanges(profile, recipe, stepKg, excludedLifts)
        val holds = tmHolds(profile, recipe, stepKg, excludedLifts)
        if (changes.isEmpty() && holds.isEmpty()) return program
        // Todo lo que el método subiría se congela por AMRAP corto: no hay TM que cambiar ni semanas que
        // reconstruir, pero el atleta debe saber por qué no sube y la guarda deja el cierre registrado.
        if (changes.isEmpty()) return holdOnly(program, proposalId, appliedKind, label, markerOccurrence, markerCycle, holds, nowMs)

        val updatedProfile = changes.fold(profile) { acc, change -> acc.withTm(change.liftSlot, change.afterKg) }
        val withProfile = program.copy(powerliftingProfile = updatedProfile)

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
    ): Program {
        val text = noticeText(label, emptyList(), preservedSessions = 0, pendingMaterialization = false, held = holds)
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
    ): String = buildString {
        append(label).append(':')
        if (changes.isNotEmpty()) {
            append(" TM ")
            append(
                changes.joinToString(", ") { change ->
                    "${liftName(change.liftSlot)} ${NativeProgressionText.formatKg(change.beforeKg)} → " +
                        "${NativeProgressionText.formatKg(change.afterKg)} kg"
                },
            )
        }
        if (held.isNotEmpty()) {
            append(if (changes.isEmpty()) " " else "; ")
            append(heldClause(held))
        }
        append('.')
        if (preservedSessions > 0) {
            append(' ')
            append(SpanishPlurals.sessions(preservedSessions))
            append(" con ajustes manuales ")
            append(SpanishPlurals.choose(preservedSessions, "se conservó", "se conservaron"))
            append(" sin cambios.")
        }
        if (pendingMaterialization) append(" Las cargas nuevas se aplican al pulsar RE-MATERIALIZAR.")
    }

    /** «banca se mantiene en 108 kg (AMRAP corto)»; con varios, «…, … y …». */
    private fun heldClause(held: List<TmHold>): String {
        val parts = held.map { hold ->
            "${liftName(hold.liftSlot)} se mantiene en ${NativeProgressionText.formatKg(hold.tmKg)} kg (AMRAP corto)"
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
