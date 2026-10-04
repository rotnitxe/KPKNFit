package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.DEFAULT_CARDIO_PART_COLOR
import com.example.kpkn.data.models.EffectiveWeekRecipe
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.ManualSessionOverride
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotLoadReferenceMetadata
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.displayName
import com.example.kpkn.data.protocols.executionCue
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.calculations.PlateCalculator
import com.example.kpkn.domain.exercises.stableRecipeElementId
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.workout.BaseLoadPolicy
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.domain.workout.warmupValidationMessages
import kotlin.math.abs
import kotlin.math.ceil

enum class WarmupLoadStatus {
    /** Carga real disponible respetando el inventario. */
    READY,
    /** Sin referencia de carga: porcentaje pendiente, nunca kg inventados. */
    PENDING_PERCENT,
    /** Peso corporal / asistido: sin % de carga externa ficticia. */
    NOT_APPLICABLE_LOAD_MODE,
    /** Aproximación ya reportada al calibrador. */
    COMPLETED,
    /** Carga repetida por redondeo/inventario: se evita duplicarla. */
    DEDUPED,
}

data class WarmupLoadEntry(
    val definition: WarmupSetDefinition,
    val status: WarmupLoadStatus,
    val requestedKg: Double?,
    val realizedKg: Double?,
    val isExact: Boolean,
)

data class WarmupLoadPlan(
    val entries: List<WarmupLoadEntry>,
    /** Avisos de WarmupRules: son umbrales de advertencia, nunca límites duros. */
    val validationMessages: List<String>,
    /** Nota de WarmupCalibrationEngine (±2,5 % por reporte, tope 5 %). */
    val calibrationNote: String?,
    /**
     * Viabilidad honesta de estas aproximaciones contra el inventario finito
     * (viaja con el plan real ya resuelto, así el consumidor nunca tiene que
     * asumir material ilimitado): UNKNOWN = sin carga de trabajo o sin
     * inventario → porcentaje pendiente, jamás 0 kg.
     * Mide el inventario (alcanzan los discos el objetivo), sin pisar el piso
     * de [BaseLoadPolicy] ni el dedupe de [entries], que ya resuelven la carga
     * efectiva.
     */
    val feasibility: WarmupFeasibility = WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, null, null),
)

object PlanMaterializer {
    /**
     * Contexto de identidad §14.4 + bolsa de referencias de carga del programa
     * (§14.1) para UNA materialización/rematerialización. Las recetas declaran
     * `DayRecipe.id`/`SlotRecipe.id` → los ids se derivan estables de la tupla
     * (programa, receta, versión, ocurrencia, semana, día[, slot]); sin ids
     * declarados (recetas legacy) se conserva el [IdProvider] byte a byte.
     */
    private data class MaterializationScope(
        val programId: String,
        val recipeId: String,
        val contentVersion: Int,
        val weekOccurrence: Int,
        /** `ExerciseLoadReference.exerciseId` → referencias disponibles (§14.1). */
        val referencePool: Map<String, List<PlanLoadReference>>,
    )

    private fun scopeOf(program: Program, recipe: TrainingPlanRecipe, weekOccurrence: Int): MaterializationScope =
        MaterializationScope(
            programId = program.id,
            recipeId = recipe.id,
            contentVersion = recipe.contentVersion,
            weekOccurrence = weekOccurrence,
            referencePool = program.exerciseLoadReferences.associate { entry -> entry.exerciseId to entry.references },
        )

    fun materialize(
        program: Program,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        profile: PowerliftingProfile? = null,
        strict: Boolean = true,
        extraExemptions: List<com.example.kpkn.data.protocols.RecipeCompositionExemption> = emptyList(),
        sourceProtocolId: String? = program.sourceProtocolId,
        options: SetupTrainingOptions = SetupTrainingOptions(),
        /** Ocurrencia de semana (§14.4) que se materializa; parte de la identidad estable. */
        weekOccurrence: Int = 1,
    ): Program {
        require(options.autoregulationMode != AutoregulationMode.AUTO || options.automaticConfirmed) {
            "La autorregulación AUTO requiere confirmación explícita del usuario (SetupTrainingOptions.automaticConfirmed)."
        }
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata, extraExemptions)
        if (hard.isNotEmpty()) {
            val message = hard.joinToString("\n") { "${it.rule} ${it.scope}: ${it.message}" }
            if (strict) error("Receta '${recipe.id}' no pasa composición:\n$message")
        }
        val resolvedProfile = hydrateProfile(program, profile, recipe.trainingMaxPercent)
        // Inicio de semana y días del split: la misma resolución que usa `rematerializeWeek` (R-23).
        val schedule = resolveWeekSchedule(program)
        val startDay = schedule.startDay
        val trainingDays = schedule.trainingDays
        // El plan es nativo SOLO si la receta que se va a materializar es la suya
        // (nunca una receta de autor aplicada encima): la base del autor se preserva.
        val nativeCurate = program.isNativeCuratedRecipe(recipe)
        // Manda la elección persistida por el usuario (Program.planWarmupConfig);
        // options solo aporta configuración cuando el programa todavía no guarda nada.
        val planWarmupSteps = effectivePlanWarmupSteps(program, options)
        val byBlock = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
        val scope = scopeOf(program, recipe, weekOccurrence)
        val blocks = byBlock.map { (_, weeks) ->
            val head = weeks.first()
            val mesoGoal = when (head.blockGoal) {
                com.example.kpkn.data.models.BlockGoal.ACCUMULATION, com.example.kpkn.data.models.BlockGoal.DENSITY -> MesocycleGoal.ACCUMULATION
                com.example.kpkn.data.models.BlockGoal.INTENSIFICATION, com.example.kpkn.data.models.BlockGoal.SPECIFICITY -> MesocycleGoal.INTENSIFICATION
                com.example.kpkn.data.models.BlockGoal.REALIZATION, com.example.kpkn.data.models.BlockGoal.PEAK -> MesocycleGoal.REALIZATION
                com.example.kpkn.data.models.BlockGoal.DELOAD, com.example.kpkn.data.models.BlockGoal.TAPER -> MesocycleGoal.DELOAD
                com.example.kpkn.data.models.BlockGoal.CUSTOM -> MesocycleGoal.CUSTOM
            }
            val scheme = when (head.blockGoal) {
                com.example.kpkn.data.models.BlockGoal.TAPER, com.example.kpkn.data.models.BlockGoal.DELOAD -> BlockProgressionScheme.PERCENT_RM
                com.example.kpkn.data.models.BlockGoal.PEAK, com.example.kpkn.data.models.BlockGoal.REALIZATION -> BlockProgressionScheme.RPE_CAP
                else -> BlockProgressionScheme.PERCENT_RM
            }
            Block(
                id = idProvider.newId(),
                name = head.blockName.ifBlank { "Bloque ${head.blockIndex + 1}" },
                goal = head.blockGoal,
                progressionScheme = scheme,
                sourceDefinitionId = recipe.id,
                // Origen real del contenido: si se materializa la receta propia de un
                // plan nativo, el bloque sigue curado por el motor nativo (así una
                // rematerialización posterior vuelve a reconocerlo); cualquier receta
                // ajena se atribuye a ella misma, nunca como nativa.
                prescriptionOrigin = if (nativeCurate) KPKN_NATIVE_CURATED_ORIGIN else recipe.id,
                mesocycles = listOf(
                    Mesocycle(
                        id = idProvider.newId(),
                        name = head.blockName.ifBlank { "Mesociclo" },
                        goal = mesoGoal,
                        weeks = weeks.sortedBy { it.weekNumber }.map { week ->
                            materializeWeek(
                                week,
                                recipe,
                                metadata,
                                idProvider,
                                resolvedProfile,
                                trainingDays,
                                startDay,
                                planWarmupSteps,
                                nativeCurate,
                                scope,
                            )
                        },
                    ),
                ),
            )
        }
        val structure = if (blocks.size > 1) ProgramStructure.COMPLEX else ProgramStructure.SIMPLE
        val simpleKind = when {
            structure != ProgramStructure.SIMPLE -> program.simpleProgramKind
            recipe.repeats -> SimpleProgramKind.CYCLIC
            else -> SimpleProgramKind.LINEAR
        }
        val trainingDaySet = trainingDays?.toSet().orEmpty()
        return program.copy(
            structure = structure,
            simpleProgramKind = simpleKind,
            structureTemplateId = recipe.id,
            powerliftingProfile = resolvedProfile ?: program.powerliftingProfile,
            sourceRecipe = recipe,
            sourceProtocolId = sourceProtocolId,
            // El modo elegido por el usuario (OFF/PROPOSE/AUTO) sobrevive a la
            // materialización: nunca se fuerza PROPOSE aquí.
            autoregulationMode = program.autoregulationMode,
            // La elección de calentamientos se guarda en el JSON del programa para
            // que la rematerialización no dependa de la configuración del llamante:
            // lo ya persistido manda; si es la primera vez que llega una elección
            // explícita, queda registrada aquí (null = preset del plan).
            planWarmupConfig = program.planWarmupConfig ?: options.warmup,
            runState = null,
            loops = emptyList(),
            loopState = null,
            loopOccurrences = emptyList(),
            events = emptyList(),
            calendarBreaks = emptyList(),
            pausedCyclicSnapshot = if (structure == ProgramStructure.SIMPLE) null else program.pausedCyclicSnapshot,
            schedulePlan = if (trainingDaySet.isEmpty()) program.schedulePlan else program.resolvedSchedulePlan().copy(
                trainingDays = trainingDaySet,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = idProvider.newId(),
                    name = program.name,
                    blocks = blocks,
                ),
            ),
        ).let { ProgramPersistNormalizer.forRoomStorage(it) }
    }

    fun hydrateProfile(
        program: Program,
        profile: PowerliftingProfile?,
        trainingMaxPercent: Double,
    ): PowerliftingProfile? {
        val goals = program.goals
        val base = profile ?: program.powerliftingProfile
        if (base == null && goals == null) return null
        val merged = (base ?: PowerliftingProfile()).copy(
            squat1RM = base?.squat1RM ?: goals?.squat1RM,
            bench1RM = base?.bench1RM ?: goals?.bench1RM,
            deadlift1RM = base?.deadlift1RM ?: goals?.deadlift1RM,
        )
        return TrainingMaxResolver.hydrateProfile(merged, trainingMaxPercent)
    }

    /** Inicio de semana y días de entrenamiento con los que se (re)materializa un programa. */
    private data class WeekSchedule(val startDay: Int, val trainingDays: List<Int>?)

    /**
     * Calendario de una (re)materialización: el inicio de semana del programa y los días de su
     * split. Lo comparten [materialize] y [rematerializeWeek] para que reconstruir una semana nunca
     * rote los días (R-23).
     */
    private fun resolveWeekSchedule(program: Program): WeekSchedule {
        val startDay = program.resolvedSchedulePlan().weekStartDay ?: program.startDay ?: 1
        val splitPattern = program.selectedSplitId?.let { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id }?.pattern }
        val trainingDays = splitPattern?.let { SplitApplicationEngine.patternToTrainingDays(it, startDay) }
            ?.map { it.dayOfWeek }
        return WeekSchedule(startDay, trainingDays)
    }

    fun rematerializeWeek(
        program: Program,
        weekId: String,
        recipe: TrainingPlanRecipe = program.sourceRecipe ?: error("El programa no tiene receta fuente"),
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        intensityScale: Double = 1.0,
        volumeFactor: Double = 1.0,
        executedWeekIds: Set<String> = emptySet(),
        options: SetupTrainingOptions = SetupTrainingOptions(),
        /** Ocurrencia de semana (§14.4) que se reconstruye; parte de la identidad estable. */
        weekOccurrence: Int = 1,
        /**
         * Evidencia a nivel de SESIÓN (§14.5): sesiones realmente iniciadas o
         * registradas. Se conservan intactas (contenido, ids, registros) mientras
         * el resto de la semana se reconstruye; `executedWeekIds` sigue
         * rechazando la semana entera cuando el caller la declara entrenada.
         */
        executedSessionIds: Set<String> = emptySet(),
    ): Program {
        require(options.autoregulationMode != AutoregulationMode.AUTO || options.automaticConfirmed) {
            "La autorregulación AUTO requiere confirmación explícita del usuario (SetupTrainingOptions.automaticConfirmed)."
        }
        if (weekId in executedWeekIds) return program
        val profile = program.powerliftingProfile
        // La receta propia del programa nativo es la única que recibe la política
        // de aproximaciones de la ruta nativa; cualquier otra receta conserva su
        // base (y la de autor, intacta).
        val nativeCurate = program.isNativeCuratedRecipe(recipe)
        // Receta ajena al programa (otro id que su receta fuente): su base sustituye a la
        // anterior y las sesiones pendientes de la receta previa no se mezclan con ella.
        val foreignRecipe = program.sourceRecipe?.let { it.id != recipe.id } == true
        // Nunca se reintroduce el preset sobre la elección persistida del usuario:
        // vacío = sin aproximaciones, lista = pasos propios, null = preset.
        val planWarmupSteps = effectivePlanWarmupSteps(program, options)
        // R-23: el calendario se resuelve igual que en `materialize` (inicio de semana del
        // programa y días del split); con `startDay = 1` y sin días, reconstruir rotaba los días.
        val schedule = resolveWeekSchedule(program)
        return program.copy(
            // La elección de calentamientos persiste en el JSON del programa (la
            // rematerialización no depende de la configuración del llamante).
            planWarmupConfig = program.planWarmupConfig ?: options.warmup,
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.mapIndexed { weekIndex, week ->
                                        if (week.id != weekId) week
                                        else {
                                            // Orden §14.4: receta snapshot → override de ocurrencia
                                            // aprobado → manual overrides → warmups/orden → cargas.
                                            val source = weekRecipeSourceFor(program, recipe, weekId)?.weekRecipe
                                                ?: recipe.weeks.firstOrNull { it.weekNumber == week.progressionIndex }
                                                ?: recipe.weeks.getOrNull(weekIndex)
                                                ?: return@mapIndexed week
                                            val scaled = scaleWeekRecipe(source, intensityScale, volumeFactor)
                                            val scope = scopeOf(program, recipe, weekOccurrence)
                                            val rebuilt = materializeWeek(
                                                scaled, recipe, metadata, idProvider, profile,
                                                schedule.trainingDays, schedule.startDay,
                                                planWarmupSteps, nativeCurate, scope,
                                            )
                                            rebuilt.copy(
                                                id = week.id,
                                                name = week.name,
                                                description = week.description,
                                                progressionIndex = week.progressionIndex,
                                                variant = week.variant,
                                                startDate = week.startDate,
                                                endDate = week.endDate,
                                                trainingDayDates = week.trainingDayDates,
                                                sessions = mergePreservedSessions(
                                                    rebuilt = rebuilt.sessions,
                                                    existing = week.sessions,
                                                    preserved = preservedSessionIds(program, week, executedSessionIds),
                                                    sealDurations = nativeCurate,
                                                    foreignRecipe = foreignRecipe,
                                                ),
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

    /**
     * Escala de propuestas aceptadas sobre UNA semana de receta (§14.4):
     * - volumen solo toca slots de resistencia ordinaria: SPEED conserva su
     *   número de series y sus propias políticas;
     * - intensidad multiplica el porcentaje (base de carga) cuando existe;
     * - el cardio (`DayRecipe.cardioBlocks`) queda intacto: duración,
     *   intensidad y modalidad no las cambia una propuesta de volumen/esfuerzo.
     */
    fun scaleWeekRecipe(source: WeekRecipe, intensityScale: Double, volumeFactor: Double): WeekRecipe {
        if (intensityScale == 1.0 && volumeFactor == 1.0) return source
        return source.copy(
            days = source.days.map { day ->
                day.copy(
                    slots = day.slots.map { slot ->
                        val affectsVolume = volumeFactor < 1.0 && slot.role != SlotRole.SPEED && slot.sets.size > 1
                        val dropped = if (affectsVolume) {
                            slot.sets.dropLast((slot.sets.size * (1.0 - volumeFactor)).toInt().coerceAtLeast(0))
                        } else slot.sets
                        slot.copy(
                            sets = dropped.map { set ->
                                set.copy(percent = set.percent?.times(intensityScale))
                            }.ifEmpty { slot.sets.take(1) },
                        )
                    },
                )
            },
        )
    }

    /** Ocurrencia 1-based de una semana dentro de su ciclo (identidad §14.4). */
    fun weekOccurrenceOf(week: ProgramWeek, indexWithinMeso: Int): Int = week.progressionIndex ?: (indexWithinMeso + 1)

    /** Resolución de identidad y receta de UNA semana materializada (§14.4). */
    data class WeekRecipeSource(
        val week: ProgramWeek,
        val weekOccurrence: Int,
        val cycleNumber: Int,
        /** Override de ocurrencia aprobado → receta snapshot de la receta fuente. */
        val weekRecipe: WeekRecipe,
    )

    /**
     * Receta efectiva que debe reconstruir una semana (§14.4): primero el
     * `EffectiveWeekRecipe` aprobado para su ocurrencia/ciclo, después la receta
     * snapshot del programa. Devuelve null si la semana no está materializada o
     * la receta no la contiene.
     */
    fun weekRecipeSourceFor(program: Program, recipe: TrainingPlanRecipe, weekId: String): WeekRecipeSource? {
        val cycle = program.runState?.cycleNumber ?: 1
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    val index = meso.weeks.indexOfFirst { it.id == weekId }
                    if (index < 0) return@forEach
                    val week = meso.weeks[index]
                    val occurrence = weekOccurrenceOf(week, index)
                    val source = effectiveWeekRecipeFor(program, occurrence, cycle)?.weekRecipe
                        ?: recipe.weeks.firstOrNull { it.weekNumber == week.progressionIndex }
                        ?: recipe.weeks.getOrNull(index)
                        ?: return null
                    return WeekRecipeSource(week, occurrence, cycle, source)
                }
            }
        }
        return null
    }

    /** Receta efectiva aprobada para esta ocurrencia/ciclo (§14.1/§14.4), si existe. */
    fun effectiveWeekRecipeFor(program: Program, weekOccurrence: Int, cycleNumber: Int): EffectiveWeekRecipe? =
        program.effectiveWeekRecipes.firstOrNull {
            it.weekOccurrence == weekOccurrence && it.cycleNumber == cycleNumber
        }

    /**
     * Registra (o actualiza) la receta efectiva aprobada de UNA ocurrencia junto
     * a las sesiones (§14.4): nunca se deja la receta efectiva solo en una
     * variable local. La upsert es idempotente por (ocurrencia, ciclo): repetir
     * el cierre de ciclo no duplica entradas ni propuestas.
     */
    fun withEffectiveWeekRecipe(
        program: Program,
        weekOccurrence: Int,
        cycleNumber: Int,
        weekRecipe: WeekRecipe?,
        applied: List<AppliedRecipeProposal>,
    ): Program {
        if (weekRecipe == null && applied.isEmpty()) return program
        val existing = program.effectiveWeekRecipes.firstOrNull {
            it.weekOccurrence == weekOccurrence && it.cycleNumber == cycleNumber
        }
        val mergedApplied = (existing?.appliedProposals.orEmpty() + applied).distinctBy { it.proposalId }
        val next = EffectiveWeekRecipe(
            weekOccurrence = weekOccurrence,
            cycleNumber = cycleNumber,
            version = ((existing?.version ?: 0) + 1).coerceAtLeast(1),
            weekRecipe = weekRecipe ?: existing?.weekRecipe,
            changes = existing?.changes.orEmpty(),
            appliedProposals = mergedApplied,
        )
        return program.copy(
            effectiveWeekRecipes = program.effectiveWeekRecipes.filterNot {
                it.weekOccurrence == weekOccurrence && it.cycleNumber == cycleNumber
            } + next,
        )
    }

    /**
     * §14.5: sesiones de esta semana que NO se reconstruyen: entrenadas
     * (evidencia real de registro) o congeladas por edición manual.
     */
    fun preservedSessionIds(program: Program, week: ProgramWeek, executedSessionIds: Set<String>): Set<String> {
        val frozen = program.manualSessionOverrides.map { it.sessionId }.toSet()
        return (frozen + executedSessionIds).filter { id -> week.sessions.any { it.id == id } }.toSet()
    }

    /**
     * Reconstrucción selectiva (§14.5): se reconstruyen las sesiones pendientes
     * y se conservan intactas (contenido E ids) las protegidas. Una sesión
     * reconstruida de una receta legacy (sin ids declarados) ADOPTA la identidad
     * de la sesión que ocupaba su día: reconstruir la misma ocurrencia nunca
     * remapea sesiones, ejercicios, sets ni calentamientos a UUIDs nuevos. Las
     * sesiones sin día de receta equivalente (creadas por el usuario) se
     * conservan: reconstruir no borra contenido. En cambio, una sesión
     * pendiente (ni entrenada ni congelada) que SÍ deriva de un día de receta
     * pero cuyo día la receta reconstruida ya no contiene es residuo de otra
     * receta o de otra forma de semana (p. ej. las sesiones del plan nativo al
     * rematerializar con una receta de autor): se descarta para no mezclar su
     * contenido con la base del autor (§14.4: la receta manda sobre lo pendiente).
     *
     * Con una receta AJENA al programa ([foreignRecipe]: su id no es el de la
     * receta fuente) la base de la semana pasa a ser esa receta y toda sesión
     * pendiente sin contraparte es residuo de la receta anterior, también la de
     * recetas legacy sin ids de día declarados (p. ej. la semana repetible de un
     * plan nativo histórico), que no se puede distinguir por identidad. Lo
     * protegido sigue a salvo: [preserved] incluye lo entrenado y TODA sesión que
     * el editor guarda o crea (cada guardado la marca con `ManualSessionOverride`
     * en la misma mutación, §14.5). Con la receta propia no hay residuo posible
     * y las sesiones sin contraparte (p. ej. creadas antes de existir la marca)
     * se conservan.
     *
     * [sealDurations]: los planes propios sellan en cada sesión `targetDurationMinutes`
     * con el estimador común (§12.2, `SessionDurationEstimator`); reconstruir su
     * receta vuelve a sellar con ese mismo estimador sobre el contenido
     * reconstruido (receta sin cambios → el mismo minuto que el generador).
     */
    internal fun mergePreservedSessions(
        rebuilt: List<Session>,
        existing: List<Session>,
        preserved: Set<String>,
        sealDurations: Boolean = false,
        foreignRecipe: Boolean = false,
    ): List<Session> {
        val existingById = existing.associateBy { it.id }
        val claimed = mutableSetOf<String>()
        val merged = rebuilt.mapIndexed { index, fresh ->
            val sameId = existingById[fresh.id]
            if (sameId != null) {
                claimed += sameId.id
                if (sameId.id in preserved) sameId else resealDuration(sameId, fresh, sealDurations)
            } else {
                val candidate = existing.firstOrNull { it.id !in claimed && it.dayOfWeek == fresh.dayOfWeek }
                    ?: existing.getOrNull(index)?.takeIf { it.id !in claimed }
                when {
                    candidate == null -> fresh
                    candidate.id in preserved -> {
                        claimed += candidate.id
                        candidate
                    }
                    else -> {
                        claimed += candidate.id
                        resealDuration(candidate, adoptSessionIdentity(candidate, fresh), sealDurations)
                    }
                }
            }
        }
        // Las protegidas (entrenadas/congeladas) se conservan siempre, igual que
        // las que no derivan de ninguna receta; una pendiente derivada de un día
        // de receta sin contraparte en la receta reconstruida se descarta, y con
        // una receta ajena se descarta toda pendiente sin contraparte.
        return merged + existing.filter { session ->
            session.id !in claimed &&
                (session.id in preserved || !(foreignRecipe || isRecipeDerived(session)))
        }
    }

    /**
     * Sello de duración (§12.2) de una sesión reconstruida: si la sesión que
     * sustituye ya llevaba sello y el plan es propio, el sello es el minuto que
     * mide el estimador común sobre el contenido reconstruido (el mismo cálculo y
     * las mismas entradas que el generador, sin duplicar la fórmula). Sin sello
     * previo (planes de autor, plantillas, programas anteriores) o sin plan propio
     * no se inventa un límite que el plan nunca tuvo: el comportamiento no cambia.
     */
    private fun resealDuration(previous: Session, rebuilt: Session, sealDurations: Boolean): Session {
        if (!sealDurations || previous.targetDurationMinutes == null || rebuilt.targetDurationMinutes != null) {
            return rebuilt
        }
        return rebuilt.copy(targetDurationMinutes = SessionDurationEstimator.estimate(rebuilt).totalMinutes)
    }

    private const val STABLE_SESSION_ID_PREFIX = "rs_"

    /**
     * true si la sesión se materializó desde un día de receta con id declarado
     * (§14.4): id estable `rs_…` o ejercicios que conservan su `recipeDayId`.
     * Las sesiones creadas por el usuario (id propio, sin día de receta) no lo son.
     */
    private fun isRecipeDerived(session: Session): Boolean =
        session.id.startsWith(STABLE_SESSION_ID_PREFIX) || session.allExercises().any { it.recipeDayId != null }

    /** Clave de emparejamiento para adoptar identidad posicional de ejercicios. */
    private fun exerciseMatchKey(exercise: Exercise): String =
        (exercise.catalogConfigurationId ?: exercise.exerciseId ?: exercise.name) + "|" + (exercise.recipeSlotId ?: "")

    /**
     * Conserva el CONTENIDO reconstruido pero la IDENTIDAD persistida de la
     * sesión anterior: sesión, partes, ejercicios, ocurrencias, sets y
     * calentamientos se emparejan por configuración/orden (recetas legacy).
     */
    internal fun adoptSessionIdentity(old: Session, new: Session): Session {
        val queue = LinkedHashMap<String, ArrayDeque<Exercise>>()
        old.allExercises().forEach { exercise ->
            queue.getOrPut(exerciseMatchKey(exercise)) { ArrayDeque() }.addLast(exercise)
        }
        fun adopt(exercise: Exercise): Exercise {
            val previous = queue[exerciseMatchKey(exercise)]?.removeFirstOrNull() ?: return exercise
            val sets = exercise.sets.mapIndexed { index, set ->
                previous.sets.getOrNull(index)?.let { set.copy(id = it.id) } ?: set
            }
            val warmups = exercise.warmupSets.mapIndexed { index, warmup ->
                previous.warmupSets.getOrNull(index)?.let { warmup.copy(id = it.id) } ?: warmup
            }
            return exercise.copy(
                id = previous.id,
                occurrenceId = previous.occurrenceId,
                sets = sets,
                warmupSets = warmups,
            )
        }
        return new.copy(
            id = old.id,
            parts = new.parts.mapIndexed { index, part ->
                part.copy(
                    id = old.parts.getOrNull(index)?.id ?: part.id,
                    exercises = part.exercises.map(::adopt),
                )
            },
            exercises = new.exercises.map(::adopt),
        )
    }

    /**
     * «Restaurar esta sesión desde el plan» (§14.5): quita SOLO la marca de la
     * sesión/ocurrencia elegida para que la siguiente reconstrucción la vuelva a
     * derivar de la receta. No borra logs ni historial; el resto de los
     * overrides queda intacto.
     *
     * @param scope alcance a quitar. `null` (valor por defecto) quita TODOS los
     *   alcances de esa sesión, el comportamiento histórico de los llamadores
     *   existentes; un alcance concreto deja intactas las marcas de los demás
     *   alcances (p. ej. una edición de plantilla para futuras ocurrencias).
     */
    fun removeManualSessionOverride(
        program: Program,
        sessionId: String,
        scope: ManualOverrideScope? = null,
    ): Program =
        program.copy(
            manualSessionOverrides = program.manualSessionOverrides.filterNot {
                it.sessionId == sessionId && (scope == null || it.scope == scope)
            },
        )

    /** Motivo por el que una sesión NO se restauró desde la receta (el programa no cambia). */
    enum class SessionRestoreRejection {
        /** La sesión no tiene ninguna marca (del alcance pedido) que restaurar. */
        NO_OVERRIDE,
        /** El programa no conserva su receta fuente. */
        NO_SOURCE_RECIPE,
        /** La sesión no está en ninguna semana del programa. */
        SESSION_NOT_FOUND,
        /** La sesión ya se inició o tiene registros: su prescripción histórica se conserva. */
        EXECUTED,
        /** La receta ya no contiene la semana de esta ocurrencia. */
        WEEK_NOT_IN_RECIPE,
        /**
         * Ningún día de la receta corresponde a esta sesión (creada por el
         * usuario, transferida a un día sin día de receta o movida de forma que
         * su identidad no se puede asegurar): reconstruir no la tocaría o la
         * confundiría con otro día, así que se conserva la edición.
         */
        NO_RECIPE_COUNTERPART,
    }

    sealed class SessionRestoreResult {
        data class Restored(val program: Program) : SessionRestoreResult()
        data class Rejected(val reason: SessionRestoreRejection) : SessionRestoreResult()
    }

    /**
     * «Restaurar esta sesión desde el plan» (§14.5): vuelve a derivar de la
     * receta SOLO la sesión elegida, en SU ocurrencia de semana, conservando
     * intactas las sesiones vecinas (contenido e ids), los logs y el resto de
     * overrides. Nunca devuelve un éxito falso: si la sesión no tiene una
     * contraparte inequívoca en la receta el resultado es
     * [SessionRestoreResult.Rejected] y la edición del usuario sigue marcada.
     *
     * @param executedSessionIds sesiones iniciadas/registradas (evidencia real
     *   de entrenamiento, ver `ProgramRepository.executedTrainingEvidence`).
     * @param scope alcance cuya marca se quita. `null` = [ManualOverrideScope.SESSION]
     *   si existe; si no, [ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES]. Las
     *   marcas de los demás alcances de esa misma sesión sobreviven (solo se
     *   levantan durante la reconstrucción, para que esta realmente ocurra).
     */
    fun restoreSessionFromRecipe(
        program: Program,
        sessionId: String,
        executedSessionIds: Set<String>,
        scope: ManualOverrideScope? = null,
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
    ): SessionRestoreResult {
        val overrides = program.manualSessionOverrides.filter { it.sessionId == sessionId }
        if (overrides.isEmpty()) return SessionRestoreResult.Rejected(SessionRestoreRejection.NO_OVERRIDE)
        val recipe = program.sourceRecipe
            ?: return SessionRestoreResult.Rejected(SessionRestoreRejection.NO_SOURCE_RECIPE)
        val (week, weekOccurrence) = program.macrocycles.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { block -> block.mesocycles.asSequence() }
            .flatMap { meso ->
                meso.weeks.asSequence().mapIndexed { index, candidate ->
                    candidate to weekOccurrenceOf(candidate, index)
                }
            }
            .firstOrNull { (candidate, _) -> candidate.sessions.any { it.id == sessionId } }
            ?: return SessionRestoreResult.Rejected(SessionRestoreRejection.SESSION_NOT_FOUND)
        if (sessionId in executedSessionIds) {
            return SessionRestoreResult.Rejected(SessionRestoreRejection.EXECUTED)
        }
        val chosenScope = scope ?: if (overrides.any { it.scope == ManualOverrideScope.SESSION }) {
            ManualOverrideScope.SESSION
        } else {
            ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES
        }
        val chosen = overrides.filter { it.scope == chosenScope }
        if (chosen.isEmpty()) return SessionRestoreResult.Rejected(SessionRestoreRejection.NO_OVERRIDE)
        val kept = overrides.filter { it.scope != chosenScope }
        val recipeDayCount = weekRecipeSourceFor(program, recipe, week.id)?.weekRecipe?.days?.size
            ?: return SessionRestoreResult.Rejected(SessionRestoreRejection.WEEK_NOT_IN_RECIPE)

        val before = week.sessions.first { it.id == sessionId }
        // Las demás sesiones de la semana se tratan como instantáneas preservadas:
        // esta acción no puede reescribirlas.
        val siblings = week.sessions.map { it.id }.filterNot { it == sessionId }.toSet()
        val rebuilt = rematerializeWeek(
            // Todas las marcas de ESTA sesión se levantan solo para reconstruir;
            // las de los alcances no elegidos se reponen abajo.
            program = removeManualSessionOverride(program, sessionId),
            weekId = week.id,
            recipe = recipe,
            metadata = metadata,
            idProvider = idProvider,
            executedWeekIds = emptySet(),
            weekOccurrence = weekOccurrence,
            executedSessionIds = executedSessionIds + siblings,
        )
        val rebuiltWeek = rebuilt.macrocycles.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }
            .flatMap { it.weeks.asSequence() }
            .firstOrNull { it.id == week.id }
            ?: return SessionRestoreResult.Rejected(SessionRestoreRejection.WEEK_NOT_IN_RECIPE)
        // [mergePreservedSessions] coloca primero, en el orden de la receta, las
        // sesiones que reconstruyó (una por día de receta) y después las que no
        // tuvieron contraparte: si la sesión quedó después de los días de la
        // receta, reconstruir no la tocó.
        val position = rebuiltWeek.sessions.indexOfFirst { it.id == sessionId }
        if (position < 0 || position >= recipeDayCount) {
            return SessionRestoreResult.Rejected(SessionRestoreRejection.NO_RECIPE_COUNTERPART)
        }
        // La contraparte debe ser el MISMO día de receta del que derivaba la
        // sesión (override -> ejercicios previos); un emparejamiento por weekday o
        // posición que cayera en otro día se rechaza en vez de mezclar identidades.
        val expectedDay = (chosen + kept).firstNotNullOfOrNull { it.recipeDayId }
            ?: before.allExercises().firstNotNullOfOrNull { it.recipeDayId }
        val actualDay = rebuiltWeek.sessions[position].allExercises().firstNotNullOfOrNull { it.recipeDayId }
        if (actualDay != expectedDay) {
            return SessionRestoreResult.Rejected(SessionRestoreRejection.NO_RECIPE_COUNTERPART)
        }
        return SessionRestoreResult.Restored(
            rebuilt.copy(manualSessionOverrides = rebuilt.manualSessionOverrides + kept),
        )
    }

    /**
     * Marca (idempotente) una sesión como editada manualmente (§14.5). La marca
     * declara «Sesión personalizada» y su alcance; el contenido autoritativo es
     * la sesión guardada en la programación.
     */
    fun withManualSessionOverride(
        program: Program,
        sessionId: String,
        weekId: String?,
        weekOccurrence: Int?,
        recipeDayId: String?,
        scope: ManualOverrideScope = ManualOverrideScope.SESSION,
        reason: String = "Edición manual",
        nowMs: Long = System.currentTimeMillis(),
    ): Program {
        val existing = program.manualSessionOverrides.firstOrNull {
            it.sessionId == sessionId && it.scope == scope
        }
        if (existing != null) {
            if (existing.weekId == weekId && existing.recipeDayId == recipeDayId) return program
            return program.copy(
                manualSessionOverrides = program.manualSessionOverrides.map {
                    if (it === existing) it.copy(weekId = it.weekId ?: weekId, recipeDayId = it.recipeDayId ?: recipeDayId) else it
                },
            )
        }
        return program.copy(
            manualSessionOverrides = program.manualSessionOverrides + ManualSessionOverride(
                sessionId = sessionId,
                weekId = weekId,
                weekOccurrence = weekOccurrence,
                cycleNumber = program.runState?.cycleNumber,
                recipeDayId = recipeDayId,
                scope = scope,
                reason = reason,
                createdAtMs = nowMs,
            ),
        )
    }

    private fun materializeWeek(
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        trainingDays: List<Int>?,
        startDay: Int,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
        scope: MaterializationScope,
    ): ProgramWeek {
        val sessions = week.days.mapIndexed { index, day ->
            val dayOfWeek = rotateWeekday(day.weekday, startDay)
                ?: trainingDays?.getOrNull(index)
                ?: ((startDay - 1 + index).mod(7) + 1)
            materializeDay(day, dayOfWeek, week, recipe, metadata, idProvider, profile, planWarmupSteps, nativeCurate, scope)
        }
        return ProgramWeek(
            id = idProvider.newId(),
            name = week.weekName.ifBlank { "Semana ${week.weekNumber}" },
            sessions = sessions,
            progressionIndex = week.weekNumber,
            executionKind = week.kind,
        )
    }

    private fun materializeDay(
        day: DayRecipe,
        dayOfWeek: Int,
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
        scope: MaterializationScope,
    ): Session {
        // Identidad estable §14.4: solo con `DayRecipe.id` declarado. Las
        // recetas legacy (id de día null) siguen con el [IdProvider] y su
        // orden de llamadas queda byte a byte intacto.
        val dayId = day.id
        val stableSessionId = dayId?.let {
            stableRecipeElementId(
                programId = scope.programId,
                recipeId = scope.recipeId,
                contentVersion = scope.contentVersion,
                weekOccurrence = scope.weekOccurrence,
                weekNumber = week.weekNumber,
                dayId = it,
            )
        }
        val assignedWarmups = assignWarmups(day, metadata, idProvider, planWarmupSteps, nativeCurate, stableSessionId)
        val seenSlotIds = mutableSetOf<String>()
        val exercises = day.slots.mapIndexed { index, slot ->
            // Dos slots con el mismo id no comparten ejercicio: caen al
            // IdProvider para que `allExercises()` nunca tenga duplicados.
            val stableExerciseId = if (dayId != null && slot.id.isNotBlank() && seenSlotIds.add(slot.id)) {
                stableRecipeElementId(
                    programId = scope.programId,
                    recipeId = scope.recipeId,
                    contentVersion = scope.contentVersion,
                    weekOccurrence = scope.weekOccurrence,
                    weekNumber = week.weekNumber,
                    dayId = dayId,
                    slotId = slot.id,
                )
            } else {
                null
            }
            materializeSlot(
                slot = slot,
                week = week,
                recipe = recipe,
                metadata = metadata,
                idProvider = idProvider,
                profile = profile,
                warmupSets = assignedWarmups[index],
                stableExerciseId = stableExerciseId,
                recipeDayId = dayId,
                referencePool = scope.referencePool,
            )
        }
        val parts = groupParts(day, exercises, idProvider, stableSessionId)
        val cardioPart = cardioPartOf(day, week, scope, idProvider, stableSessionId)
        val cardioFirst = cardioPart != null && day.cardioBlocks.any {
            it.position == RecipeCardioPosition.BEFORE_STRENGTH || it.position == RecipeCardioPosition.ONLY
        }
        val orderedParts = when {
            cardioPart == null -> parts
            cardioFirst -> listOf(cardioPart) + parts
            else -> parts + cardioPart
        }
        return Session(
            id = stableSessionId ?: idProvider.newId(),
            name = day.label,
            scheduleLabel = day.label,
            dayOfWeek = dayOfWeek,
            assignedDays = listOf(dayOfWeek),
            exercises = emptyList(),
            parts = orderedParts,
            isMainSession = day.slots.any { it.role == SlotRole.T1_MAIN },
            origin = SessionOrigin.USER_DRAFT,
            requirement = SessionRequirement.REQUIRED,
            cardioFirst = cardioFirst,
        )
    }

    /**
     * Bloque de cardio del día (§14.1): un [SessionPart] real de cardio con
     * los minutos totales del día. No exige material de fuerza ni identidad de
     * catálogo: reutiliza [CardioDetails] tal cual (misma ruta que
     * `SimpleCyclePersonalizer`), con identidad estable por (día, bloque).
     */
    private fun cardioPartOf(
        day: DayRecipe,
        week: WeekRecipe,
        scope: MaterializationScope,
        idProvider: IdProvider,
        stableSessionId: String?,
    ): SessionPart? {
        if (day.cardioBlocks.isEmpty()) return null
        val exercises = day.cardioBlocks.mapIndexed { index, block ->
            val stableExerciseId = if (day.id != null) {
                stableRecipeElementId(
                    programId = scope.programId,
                    recipeId = scope.recipeId,
                    contentVersion = scope.contentVersion,
                    weekOccurrence = scope.weekOccurrence,
                    weekNumber = week.weekNumber,
                    dayId = day.id,
                    slotId = "cardio:${block.id}",
                )
            } else {
                null
            }
            Exercise(
                id = stableExerciseId ?: idProvider.newId(),
                name = block.details.type.name.lowercase().replace('_', ' '),
                cardioDetails = block.details,
                recipeDayId = day.id,
                recipeSlotId = block.id.takeIf { day.id != null },
            )
        }
        val totalSeconds = day.cardioBlocks.sumOf { it.details.effectiveDurationSeconds() }
        return SessionPart(
            id = stableSessionId?.let { "$it#part:cardio" } ?: idProvider.newId(),
            name = "Cardio",
            exercises = exercises,
            color = DEFAULT_CARDIO_PART_COLOR,
            targetDurationMinutes = ceil(totalSeconds / 60.0).toInt(),
            isCardioGroup = true,
        )
    }

    private fun groupParts(
        day: DayRecipe,
        exercises: List<Exercise>,
        idProvider: IdProvider,
        stableSessionId: String?,
    ): List<SessionPart> {
        val parts = mutableListOf<SessionPart>()
        day.slots.zip(exercises).forEach { (slot, exercise) ->
            val name = when (slot.role) {
                SlotRole.SPEED -> "Velocidad"
                SlotRole.T1_MAIN -> "Principal"
                SlotRole.TECHNIQUE -> "Técnica"
                SlotRole.T2_SUPPLEMENTAL -> "Suplementario"
                SlotRole.T3_ACCESSORY -> "Accesorios"
            }
            val previous = parts.lastOrNull()
            if (previous?.name == name) {
                parts[parts.lastIndex] = previous.copy(exercises = previous.exercises + exercise)
            } else {
                val partId = stableSessionId?.let { "$it#part:${parts.size}" } ?: idProvider.newId()
                parts += SessionPart(id = partId, name = name, exercises = listOf(exercise))
            }
        }
        return parts
    }

    private fun materializeSlot(
        slot: SlotRecipe,
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        warmupSets: List<WarmupSetDefinition>,
        stableExerciseId: String? = null,
        recipeDayId: String? = null,
        referencePool: Map<String, List<PlanLoadReference>> = emptyMap(),
    ): Exercise {
        val meta = metadata.metadata(slot.lift.configurationId)
            ?: error("Configuración '${slot.lift.configurationId}' sin metadata del catálogo")
        // El nombre es el canonicalName verbatim del catálogo; la técnica del
        // slot viaja en variantName/techniqueModifier/relationshipNotes y se
        // muestra como chip en exerciseDisplayParts, nunca como texto del nombre.
        val display = meta.displayName
        require(display.isNotBlank()) { "Nombre vacío para '${slot.lift.configurationId}'" }
        val tm = slot.lift.liftSlot?.let { TrainingMaxResolver.trainingMax(profile, it, recipe.trainingMaxPercent) }
        val oneRm = slot.lift.liftSlot?.let { TrainingMaxResolver.oneRm(profile, it) }
        val working = slot.sets.filter { !it.isWarmup }
        val warmups = slot.sets.filter { it.isWarmup }
        val usesPercent = working.any { it.percent != null } || warmups.any { it.percent != null }
        val legacyReference = when (working.firstOrNull()?.loadBasis ?: slot.sets.firstOrNull()?.loadBasis) {
            LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> oneRm
            else -> tm
        }
        // §14.1: una referencia declarada en el set prevalece sobre la
        // resolución legacy por LiftSlot (que es de otra variante). La bolsa
        // del programa solo se consulta para ejercientes con identidad estable
        // y MISMA configuración/convención; los capturados ganan a los pendientes.
        val explicit = working.firstNotNullOfOrNull { it.reference }
            ?: slot.sets.firstNotNullOfOrNull { it.reference }
             ?: resolvedPoolReference(
                 pool = referencePool,
                 stableExerciseId = stableExerciseId,
                 configurationId = slot.lift.configurationId,
                 explicitMetadata = slot.explicitReference,
                 allowBilateralReference = !slot.isUnilateral,
             )
        val declaredLoadConvention = slot.explicitReference?.quantityConvention
            ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
            ?: working.firstNotNullOfOrNull { set ->
                set.reference?.quantityConvention?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
            }
            ?: explicit?.quantityConvention?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
        val sideReferenceCandidates = if (slot.isUnilateral) {
            stableExerciseId?.let { referencePool[it].orEmpty() }.orEmpty()
                .filter {
                    it.configurationId == slot.lift.configurationId &&
                        it.side in setOf("left", "right") &&
                        it.kind in setOf(PlanLoadReferenceKind.OBSERVED_WORKING_SET, PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL)
                }
        } else {
            emptyList()
        }
        val sideReferences = when {
            declaredLoadConvention != null -> sideReferenceCandidates.filter { it.quantityConvention == declaredLoadConvention }
            sideReferenceCandidates.map { it.quantityConvention }.distinct().size <= 1 -> sideReferenceCandidates
            else -> emptyList()
        }
        val baseLoadKg = if (explicit != null) {
            referenceBaseKg(explicit)
        } else {
            legacyReference
        }
        val directWorkingLoadKg = explicit
            ?.takeIf { reference ->
                reference.configurationId == slot.lift.configurationId &&
                    reference.kind in setOf(PlanLoadReferenceKind.OBSERVED_WORKING_SET, PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL) &&
                    reference.state == PlanLoadReferenceState.CAPTURED &&
                    (reference.side.isNullOrBlank() || (reference.side == "bilateral" && !slot.isUnilateral))
            }
            ?.capturedLoadKg
            ?.takeIf { it > 0.0 }
        val sets = working.mapIndexed { index, set ->
            val materialized = materializeSet(
                set = set,
                slot = slot,
                week = week,
                tm = baseLoadKg,
                idProvider = idProvider,
                stableSetId = stableExerciseId?.let { "$it#set:$index" },
                progression = recipe.progression,
            )
            if (set.percent == null && directWorkingLoadKg != null) {
                materialized.copy(weight = directWorkingLoadKg)
            } else {
                materialized
            }
        }
        val manualLoadSides = explicit
            ?.takeIf { it.state == PlanLoadReferenceState.PENDING }
            ?.side
            ?.takeIf { it.isNotBlank() }
        val materializedSets = if (manualLoadSides == null) {
            sets
        } else {
            sets.map { set ->
                if (set.isCalibrator || set.isTopSet || set.isDropSet || set.isRestPause || set.isAmrap) set
                else set.copy(manualLoadRequiredSides = set.manualLoadRequiredSides + manualLoadSides)
            }
        }.mapIndexed { index, set ->
            var resolved = set
            sideReferences.groupBy { it.side.orEmpty() }.forEach { (side, references) ->
                val reference = references.lastOrNull { it.state == PlanLoadReferenceState.CAPTURED }
                    ?: references.lastOrNull()
                    ?: return@forEach
                if (reference.state == PlanLoadReferenceState.CAPTURED && working.getOrNull(index)?.percent != null) {
                    return@forEach
                }
                val target = if (side == "left") resolved.leftTarget else resolved.rightTarget
                val baseTarget = target ?: UnilateralTarget(
                    weight = resolved.weight,
                    targetReps = resolved.targetReps,
                    targetRepsRange = resolved.targetRepsRange,
                    targetDuration = resolved.targetDuration,
                    targetRPE = resolved.targetRPE,
                    targetRIR = resolved.targetRIR,
                    intensityMode = resolved.intensityMode,
                )
                val nextTarget = baseTarget.copy(
                    weight = if (reference.state == PlanLoadReferenceState.CAPTURED) {
                        reference.capturedLoadKg?.takeIf { it > 0.0 }
                    } else {
                        null
                    },
                )
                resolved = resolved.copy(
                    leftTarget = if (side == "left") nextTarget else resolved.leftTarget,
                    rightTarget = if (side == "right") nextTarget else resolved.rightTarget,
                    manualLoadRequiredSides = if (reference.state == PlanLoadReferenceState.PENDING) {
                        resolved.manualLoadRequiredSides + side
                    } else {
                        resolved.manualLoadRequiredSides - side
                    },
                )
            }
            resolved
        }
        // §12.4: solo F/H/I de un plan con progresión propia llevan carga gestionada.
        val nativeProgressionManaged = recipe.nativeProgression?.strategy
            ?.takeIf { it != NativeProgressionStrategy.NONE } != null &&
            slot.intent in setOf(SlotIntent.F, SlotIntent.H, SlotIntent.I)
        // F-03: la convención de cantidad de un slot nativo sale del equipo del
        // catálogo (barra/máquina → TOTAL_EXTERNAL, mancuerna/kettlebell →
        // PER_IMPLEMENT), no de subcadenas del id. Una referencia declarada o
        // capturada en la bolsa sigue ganando; peso corporal/banda quedan sin
        // convención porque no tienen carga externa inequívoca.
        val nativeCatalogConvention = if (nativeProgressionManaged) {
            NativeLoadConventions.forEquipment(meta.equipmentId)
        } else {
            LoadQuantityConvention.UNSPECIFIED
        }
        val cues = buildList {
            addAll(meta?.let { emptyList() } ?: emptyList())
            slot.technique?.let { add(it.executionCue()) }
        }
        return Exercise(
            id = stableExerciseId ?: idProvider.newId(),
            name = display,
            exerciseDbId = slot.lift.configurationId,
            exerciseId = slot.lift.configurationId,
            canonicalExerciseId = slot.lift.configurationId,
            exerciseFamilyId = slot.lift.configurationId.substringBefore("__"),
            relativeToCanonicalExerciseId = slot.lift.configurationId.takeIf { slot.technique != null },
            relationshipType = ExerciseRelationshipType.TECHNIQUE.takeIf { slot.technique != null },
            relationshipNotes = slot.technique?.displayName(),
            sets = materializedSets,
            restTime = slot.restSeconds,
            trainingMode = if (usesPercent) TrainingMode.RM else TrainingMode.REPS,
            // `reference1RM` sigue significando 1RM: sin referencia declarada el
            // valor legacy; con referencia declarada SOLO si es 1RM capturado.
            // Una carga de trabajo de 3-5 reps JAMÁS acá (§14.2).
            reference1RM = when {
                explicit == null -> legacyReference
                explicit.kind == PlanLoadReferenceKind.EXERCISE_1RM &&
                    explicit.state == PlanLoadReferenceState.CAPTURED -> explicit.capturedLoadKg?.takeIf { it > 0.0 }
                else -> null
            },
            variantName = slot.technique?.displayName(),
            isUnilateral = slot.isUnilateral,
            isCompetitionLift = slot.isCompetitionLift,
            executionCues = cues,
            catalogRevision = "v2-approved-2026-09-29-a",
            catalogDefinitionId = slot.lift.configurationId.substringBefore("__"),
            catalogConfigurationId = slot.lift.configurationId,
            performanceProfileId = meta.performanceProfileId,
            occurrenceId = stableExerciseId?.let { "$it#occ" } ?: idProvider.newId(),
            slotRole = slot.role,
            techniqueModifier = slot.technique,
            warmupSets = warmupSets,
            loadReference = explicit,
            loadQuantityConvention = explicit?.quantityConvention
                ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                ?: slot.explicitReference?.quantityConvention
                    ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                ?: sideReferences.map { it.quantityConvention }.distinct().singleOrNull()
                    ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                ?: nativeCatalogConvention,
            recipeDayId = recipeDayId,
            recipeSlotId = slot.id.takeIf { recipeDayId != null },
            nativeProgressionManaged = nativeProgressionManaged,
        )
    }

    /**
     * Referencia de carga capturada para este ejercicio (§14.1): solo con
     * identidad estable (la clave de la bolsa es `Exercise.id`) y solo de la
     * MISMA configuración; si el slot declara convención, la misma convención.
     * Un snapshot CAPTURED gana al PENDING.
     */
    private fun resolvedPoolReference(
        pool: Map<String, List<PlanLoadReference>>,
        stableExerciseId: String?,
        configurationId: String,
        explicitMetadata: SlotLoadReferenceMetadata?,
        allowBilateralReference: Boolean,
    ): PlanLoadReference? {
        if (stableExerciseId == null || pool.isEmpty()) return null
        val declaredConvention = explicitMetadata?.quantityConvention
        val candidates = pool[stableExerciseId].orEmpty().filter { ref ->
            ref.configurationId == configurationId &&
                (ref.side.isNullOrBlank() || (allowBilateralReference && ref.side == "bilateral")) &&
                (declaredConvention == null ||
                    declaredConvention == LoadQuantityConvention.UNSPECIFIED ||
                    ref.quantityConvention == declaredConvention)
        }
        val captured = candidates.firstOrNull {
            it.state == PlanLoadReferenceState.CAPTURED && it.capturedLoadKg != null
        }
        return captured ?: candidates.firstOrNull { it.state == PlanLoadReferenceState.PENDING }
    }

    /**
     * Base de carga visible de una referencia declarada: el snapshot capturado.
     * Sin capturar o en convención de lastre/asistencia → null = pendiente
     * (peso null, nunca 0 kg ni NaN; §14.2).
     */
    private fun referenceBaseKg(reference: PlanLoadReference): Double? {
        if (reference.state != PlanLoadReferenceState.CAPTURED) return null
        if (reference.kind == PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL) return null
        return reference.capturedLoadKg?.takeIf { it > 0.0 }
    }

    private fun materializeSet(
        set: SetRecipe,
        slot: SlotRecipe,
        week: WeekRecipe,
        tm: Double?,
        idProvider: IdProvider,
        stableSetId: String? = null,
        progression: ProgressionRule = ProgressionRule.None,
    ): ExerciseSet {
        // Porcentaje respecto del TM; la regla vive en PercentResolver para que la
        // política de composición (H11/H11b) mida exactamente lo mismo que se materializa.
        val rawPercent = PercentResolver.resolve(set, slot, week)
        val baseWeight = rawPercent?.let { TrainingMaxResolver.loadKg(it, tm) }
        // WeeklyKg (B.S3): el kilo semanal del método se suma a la carga resuelta y el
        // porcentaje mostrado se mantiene coherente con ese kg (pct + kg ÷ base × 100). Sin
        // base de carga el peso queda null y el porcentaje, el de la receta.
        val weeklyOffsetKg = baseWeight?.let {
            AuthoredProgressionEngine.weeklyOffsetKg(progression, week.weekNumber, slot.lift.liftSlot, slot.role)
        }
        val weight = if (baseWeight != null && weeklyOffsetKg != null) baseWeight + weeklyOffsetKg else baseWeight
        val percent = if (rawPercent != null && weeklyOffsetKg != null && tm != null) {
            rawPercent + weeklyOffsetKg / tm * 100.0
        } else {
            rawPercent
        }
        val range = if (set.repsMin != null && set.repsMax != null) RepRange(set.repsMin, set.repsMax) else null
        val mode = when {
            set.amrap -> IntensityMode.AMRAP
            percent != null -> IntensityMode.SOLO_RM
            set.rir != null -> IntensityMode.RIR
            set.rpe != null -> IntensityMode.RPE
            else -> IntensityMode.RPE
        }
        return ExerciseSet(
            id = stableSetId ?: idProvider.newId(),
            targetReps = set.reps,
            targetRepsRange = range,
            targetRPE = set.rpe,
            targetRIR = set.rir,
            intensityMode = mode,
            targetPercentageRM = percent,
            weight = weight,
            isAmrap = set.amrap,
            restAfterSeconds = slot.restSeconds,
            isTopSet = set.isTopSet,
            loadBasis = set.loadBasis,
        )
    }

    /** Receta weekday 1-7 relativa al lunes; [startDay] rota el microciclo sin reordenar días. */
    private fun rotateWeekday(recipeDay: Int?, startDay: Int): Int? {
        if (recipeDay == null) return null
        val safeRecipe = recipeDay.coerceIn(1, 7)
        val safeStart = startDay.coerceIn(1, 7)
        return ((safeStart - 1) + (safeRecipe - 1)).mod(7) + 1
    }

    private const val WARMUP_EQUIVALENT_POINTS = 5.0
    private const val UNKNOWN_PATTERN_BUCKET = "__unknown_pattern__"
    /** Tolerancia de la revalidación piso↔material (kg). */
    private const val LOAD_EPSILON = 0.01

    /**
     * Aproximaciones por slot: las que trae la receta (autor, intactas) más el
     * preset del plan (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de trabajo)
     * solo en el primer compuesto de cada patrón de movimiento del día. El
     * patrón se identifica con la composición real del catálogo
     * ([CompositionTaxonomy]), no solo por [SlotRole.T1_MAIN]: los programas
     * nativos no siempre etiquetan así.
     *
     * El preset exige series con porcentaje ([usesPercent]) salvo en semanas
     * curadas nativas ([nativeCurate], serie RIR de la ruta nativa) y sus
     * rematerializaciones, que aplican la misma política de aproximaciones para
     * no divergir del motor.
     *
     * Anti-redundancia: un paso del preset equivalente (±5 puntos porcentuales)
     * a una aproximación ya presente no se duplica. El resultado queda ordenado
     * por porcentaje ascendente; el usuario puede editar reps/% o quitar
     * cualquier aproximación después.
     */
    private fun assignWarmups(
        day: DayRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        planWarmupSteps: List<SetRecipe>,
        nativeCurate: Boolean,
        stableSessionId: String? = null,
    ): List<List<WarmupSetDefinition>> {
        val claimedPatterns = mutableSetOf<String>()
        val out = mutableListOf<List<WarmupSetDefinition>>()
        day.slots.forEachIndexed { slotIndex, slot ->
            // Identidad estable §14.4: mismo (sesión, slot, paso) → mismo id en
            // cada reconstrucción de la ocurrencia. Legacy sigue con el IdProvider.
            fun stableId(prefix: String, step: Int): String? =
                stableSessionId?.let { "$it#$prefix:$slotIndex:$step" }
            val meta = metadata.metadata(slot.lift.configurationId)
            val family = CompositionTaxonomy.familyOf(meta?.movementPatternId)
            val compound = meta != null &&
                !CompositionTaxonomy.isIsolation(family, meta.articulationType, meta.configurationId)
            val patternBucket = family?.name ?: UNKNOWN_PATTERN_BUCKET
            val isFirstCompoundOfPattern = when {
                family != null && compound -> claimedPatterns.add(patternBucket)
                // Sin patrón en el catálogo se conserva el fallback legacy por rol.
                family == null && slot.role == SlotRole.T1_MAIN -> claimedPatterns.add(patternBucket)
                else -> false
            }
            val recipeWarmups = slot.sets.filter { it.isWarmup }.mapIndexed { step, set ->
                WarmupSetDefinition(
                    stableId("w", step) ?: idProvider.newId(),
                    set.percent ?: 40.0,
                    set.reps ?: 5,
                    restBetween = 60,
                )
            }
            val usesPercent = slot.sets.any { it.percent != null }
            // Los calentamientos específicos de protocolo se conservan íntegros salvo
            // edición explícita del usuario: el preset del plan NO se suma sobre ellos.
            // El preset se aplica solo al primer compuesto de cada patrón cuando la
            // receta no trae aproximaciones propias.
            val presetWarmups = if (recipeWarmups.isEmpty() && isFirstCompoundOfPattern && (usesPercent || nativeCurate)) {
                planWarmupSteps.mapIndexedNotNull { step, s ->
                    val percent = s.percent ?: return@mapIndexedNotNull null
                    WarmupSetDefinition(
                        id = stableId("wp", step) ?: idProvider.newId(),
                        percentageOfWorkingWeight = percent,
                        targetReps = s.reps ?: 5,
                        restBetween = when {
                            percent >= 70.0 -> 120
                            percent >= 50.0 -> 90
                            else -> 60
                        },
                    )
                }
            } else {
                emptyList()
            }
            out.add(
                (recipeWarmups + presetWarmups)
                    .sortedBy { WarmupCalibrationEngine.normalizePercentage(it.percentageOfWorkingWeight) },
            )
        }
        return out
    }

    /**
     * Pasos de calentamiento efectivos de una materialización/rematerialización:
     * manda la elección persistida en el programa ([Program.planWarmupConfig],
     * la decisión real del usuario: null = preset del plan, vacío = sin
     * aproximaciones, lista = pasos propios) para que una rematerialización nunca
     * reintroduzca el preset sobre una elección ya guardada. Solo cuando el
     * programa todavía no guarda nada se usa la configuración de esta llamada
     * ([options]), que por defecto es el preset 40 % × 8 / 60 % × 5 / 80 % × 3
     * sobre la carga de trabajo. Los pasos siempre salen normalizados (orden
     * ascendente, sin duplicados ±5 puntos porcentuales, 0 < % ≤ 100).
     */
    private fun effectivePlanWarmupSteps(program: Program, options: SetupTrainingOptions): List<SetRecipe> {
        val persisted = program.planWarmupConfig
        return if (persisted != null) normalizedWarmupSteps(persisted) else options.resolvedWarmupSteps()
    }

    /**
     * Contrato público de la bolsa de prioridades de orden en la ruta de
     * materialización: esta ruta NUNCA reordena recetas de autor ni altera su
     * estructura, así que [OrderPrioritiesCapabilities.applied] solo puede ser
     * true cuando la bolsa pedida coincide con la que el generador nativo aplicó
     * al persistir el programa. La UI debe consultar este resultado antes de
     * afirmar que la bolsa quedó aplicada; ver [OrderPrioritiesContract].
     */
    fun orderPrioritiesCapabilities(
        program: Program,
        recipe: TrainingPlanRecipe? = program.sourceRecipe,
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): OrderPrioritiesCapabilities = OrderPrioritiesContract.capabilitiesOf(program, recipe, options)

    /**
     * Realiza las cargas de aproximación contra el inventario real:
     * - Los porcentajes son sobre la carga de trabajo ([workingLoadKg]), nunca
     *   sobre el 1RM; con calibración de WarmupCalibrationEngine (±2,5 %, tope 5 %).
     * - Sin referencia de carga → porcentajes pendientes
     *   ([WarmupLoadStatus.PENDING_PERCENT]), nunca kilogramos inventados.
     * - Peso corporal / asistido → sin % de carga externa ficticia
     *   ([WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE]).
     * - La carga se ajusta con [PlateCalculator] (discos con cantidades) o al
     *   rango real de la máquina; no se asumen incrementos universales de 0,5 kg.
     * - [configurationId] verifica que la máquina declarada ES la de este
     *   ejercicio (leg curl ≠ leg press): si no coincide, la carga queda
     *   [WarmupLoadStatus.PENDING_PERCENT], nunca kilogramos de otra máquina.
     * - [equipmentKind] elige el motor de material del ejercicio: discos
     *   (barra/por defecto), mancuerna por pareja, kettlebell; sin rango
     *   exacto/estación para máquina, cable o Smith →
     *   [WarmupLoadStatus.PENDING_PERCENT]. Nunca se usa el resolvedor de
     *   discos para todo tipo de material.
     * - [BaseLoadPolicy] impone el piso de carga base por etiqueta activa.
     * - Las cargas repetidas por redondeo se colapsan ([WarmupLoadStatus.DEDUPED]).
     * - [WarmupLoadPlan.feasibility] resume la viabilidad honesta contra el
     *   inventario declarado (o la máquina): sin carga de trabajo o sin
     *   inventario → [WarmupFeasibilityStatus.UNKNOWN], nunca ilimitado.
     */
    fun realizeWarmupLoads(
        warmups: List<WarmupSetDefinition>,
        workingLoadKg: Double?,
        inventory: EquipmentInventory,
        loadMode: LoadModeV2 = LoadModeV2.LOAD,
        taggedProfile: WorkoutContextProfile? = null,
        activeTagId: String? = null,
        machine: MachineLoadRange? = null,
        configurationId: String? = null,
        equipmentKind: String? = null,
        effortReports: List<WarmupEffortReport> = emptyList(),
        effectiveSetCount: Int = 3,
        /**
         * Colapso de cargas repetidas (prescripción). La vista de
         * calentamientos lo desactiva: cada paso muestra su propio kg alcanzable
         * aunque coincida con otro, en vez de quedar «pendiente».
         */
        deduplicate: Boolean = true,
    ): WarmupLoadPlan {
        // Matching configurationId ↔ ejercicio: si la máquina declarada no es la
        // de esta configuración (leg curl ≠ leg press) no se aplica y las cargas
        // quedan pendientes, nunca kg inventados con la máquina equivocada.
        val matchedMachine = machineRangeFor(machine, configurationId)
        val machineMismatch = machine != null && matchedMachine == null
        val validationMessages = warmupValidationMessages(warmups, effectiveSetCount)
        if (loadMode == LoadModeV2.BODYWEIGHT || loadMode == LoadModeV2.ASSISTED) {
            return WarmupLoadPlan(
                entries = warmups.map {
                    WarmupLoadEntry(it, WarmupLoadStatus.NOT_APPLICABLE_LOAD_MODE, null, null, isExact = false)
                },
                validationMessages = validationMessages,
                calibrationNote = null,
                // Sin carga externa no se proclama viabilidad de cargas: se queda en
                // «pendiente de porcentaje» (UNKNOWN), nunca kilogramos inventados.
                feasibility = WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, workingLoadKg, inventory),
            )
        }
        val calibration = WarmupCalibrationEngine.calibrateWorkingLoad(
            programmedPercentages = warmups.map { it.percentageOfWorkingWeight },
            workingLoadKg = workingLoadKg,
            reports = effortReports,
        )
        val lastReportedIndex = effortReports.maxOfOrNull { it.warmupIndex } ?: -1
        val resolved = warmups.mapIndexed { index, definition ->
            when {
                index <= lastReportedIndex ->
                    WarmupLoadEntry(definition, WarmupLoadStatus.COMPLETED, null, null, isExact = false)
                // Máquina declarada que no corresponde a esta configuración:
                // porcentaje pendiente, nunca kilogramos de la máquina equivocada.
                machineMismatch ->
                    WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                else -> {
                    val requested = calibration.remainingWarmupLoadsKg.getOrNull(index)
                    if (requested == null || requested <= 0.0) {
                        WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                    } else {
                        // Carga alcanzable según el material REAL del ejercicio
                        // (discos, mancuerna por pareja, kettlebell, máquina
                        // exacta o estación): si no hay material acreditado →
                        // pendiente, nunca 0 ni kilogramos inventados.
                        val achieved = reachableWarmupLoad(
                            requestedKg = requested,
                            equipmentKind = equipmentKind,
                            inventory = inventory,
                            machine = matchedMachine,
                        )
                        if (achieved == null || achieved <= 0.0) {
                            WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                        } else {
                            val floor = BaseLoadPolicy.floorForLoadSuggestion(
                                loadMode = loadMode,
                                activeTagId = activeTagId,
                                engineSuggestedKg = achieved,
                                taggedProfileBaseLoadKg = BaseLoadPolicy.resolvedFromProfile(taggedProfile),
                            )
                            val candidate = floor?.suggestedWeight ?: achieved
                            // Revalidación piso + material: si no pueden
                            // satisfacerse a la vez → pendiente explícito, nunca
                            // un kg imposible como READY.
                            val finalizedKg = if (candidate == achieved) {
                                achieved
                            } else {
                                reachableWarmupLoad(candidate, equipmentKind, inventory, matchedMachine)
                                    ?.takeIf { it >= candidate - LOAD_EPSILON }
                            }
                            if (finalizedKg == null || finalizedKg <= 0.0) {
                                WarmupLoadEntry(definition, WarmupLoadStatus.PENDING_PERCENT, null, null, isExact = false)
                            } else {
                                WarmupLoadEntry(
                                    definition = definition,
                                    status = WarmupLoadStatus.READY,
                                    requestedKg = requested,
                                    realizedKg = finalizedKg,
                                    isExact = abs(finalizedKg - requested) < LOAD_EPSILON,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Evitar cargas repetidas por redondeo: gana la aproximación más liviana.
        // Solo para prescripción; la ruta de visualización pide cada kg alcanzable.
        val entries = if (!deduplicate) resolved else {
            val seen = mutableListOf<Double>()
            resolved.map { entry ->
                val realized = entry.realizedKg
                if (entry.status != WarmupLoadStatus.READY || realized == null) {
                    entry
                } else if (seen.any { abs(it - realized) < 0.001 }) {
                    entry.copy(status = WarmupLoadStatus.DEDUPED, realizedKg = null, isExact = false)
                } else {
                    seen += realized
                    entry
                }
            }
        }
        return WarmupLoadPlan(
            entries = entries,
            validationMessages = validationMessages,
            calibrationNote = calibration.note,
            // Viabilidad real contra el inventario hardware (o la máquina) con la
            // misma carga de trabajo ya usada aquí: si no hay carga de trabajo la
            // respuesta es UNKNOWN y las entries quedan en PENDING_PERCENT, nunca
            // un 0 kg ni un stock ilimitado inventado.
            feasibility = WarmupFeasibilityChecker.of(
                workingLoadKg = workingLoadKg,
                inventory = inventory,
                warmups = warmups,
                machine = machine,
                configurationId = configurationId,
                equipmentKind = equipmentKind,
            ),
        )
    }
}
