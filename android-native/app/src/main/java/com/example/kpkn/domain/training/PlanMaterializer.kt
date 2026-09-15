package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
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
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.displayName
import com.example.kpkn.data.protocols.executionCue
import com.example.kpkn.data.splits.SPLIT_TEMPLATES

object PlanMaterializer {
    fun materialize(
        program: Program,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        profile: PowerliftingProfile? = null,
        strict: Boolean = true,
        extraExemptions: List<com.example.kpkn.data.protocols.RecipeCompositionExemption> = emptyList(),
        sourceProtocolId: String? = program.sourceProtocolId,
    ): Program {
        val hard = ProgramRecipeValidator.hardFindings(recipe, metadata, extraExemptions)
        if (hard.isNotEmpty()) {
            val message = hard.joinToString("\n") { "${it.rule} ${it.scope}: ${it.message}" }
            if (strict) error("Receta '${recipe.id}' no pasa composición:\n$message")
        }
        val resolvedProfile = hydrateProfile(program, profile, recipe.trainingMaxPercent)
        val startDay = program.resolvedSchedulePlan().weekStartDay ?: program.startDay ?: 1
        val splitId = program.selectedSplitId
        val splitPattern = splitId?.let { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id }?.pattern }
        val trainingDays = splitPattern?.let { SplitApplicationEngine.patternToTrainingDays(it, startDay) }
            ?.map { it.dayOfWeek }
        val byBlock = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
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
                prescriptionOrigin = recipe.id,
                mesocycles = listOf(
                    Mesocycle(
                        id = idProvider.newId(),
                        name = head.blockName.ifBlank { "Mesociclo" },
                        goal = mesoGoal,
                        weeks = weeks.sortedBy { it.weekNumber }.map { week ->
                            materializeWeek(week, recipe, metadata, idProvider, resolvedProfile, trainingDays, startDay)
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
            autoregulationMode = if (program.autoregulationMode == AutoregulationMode.OFF) {
                AutoregulationMode.PROPOSE
            } else {
                program.autoregulationMode
            },
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
        ).alignTemporalMetadata()
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

    fun rematerializeWeek(
        program: Program,
        weekId: String,
        recipe: TrainingPlanRecipe = program.sourceRecipe ?: error("El programa no tiene receta fuente"),
        metadata: ExerciseCompositionMetadataProvider = CompositionMetadataHolder.resolve(),
        idProvider: IdProvider = UuidIdProvider,
        intensityScale: Double = 1.0,
        volumeFactor: Double = 1.0,
        executedWeekIds: Set<String> = emptySet(),
    ): Program {
        if (weekId in executedWeekIds) return program
        val profile = program.powerliftingProfile
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else {
                                            val source = recipe.weeks.firstOrNull { it.weekNumber == week.progressionIndex }
                                                ?: recipe.weeks.getOrNull(meso.weeks.indexOf(week))
                                                ?: return@map week
                                            val scaled = source.copy(
                                                days = source.days.map { day ->
                                                    day.copy(
                                                        slots = day.slots.map { slot ->
                                                            val dropped = if (volumeFactor < 1.0 && slot.sets.size > 1) {
                                                                slot.sets.dropLast((slot.sets.size * (1.0 - volumeFactor)).toInt().coerceAtLeast(0))
                                                            } else slot.sets
                                                            slot.copy(
                                                                sets = dropped.map { set ->
                                                                    set.copy(
                                                                        percent = set.percent?.times(intensityScale),
                                                                    )
                                                                }.ifEmpty { slot.sets.take(1) },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                            materializeWeek(scaled, recipe, metadata, idProvider, profile, null, 1).copy(
                                                id = week.id,
                                                name = week.name,
                                                progressionIndex = week.progressionIndex,
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

    private fun materializeWeek(
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
        trainingDays: List<Int>?,
        startDay: Int,
    ): ProgramWeek {
        val sessions = week.days.mapIndexed { index, day ->
            val dayOfWeek = rotateWeekday(day.weekday, startDay)
                ?: trainingDays?.getOrNull(index)
                ?: ((startDay - 1 + index).mod(7) + 1)
            materializeDay(day, dayOfWeek, week, recipe, metadata, idProvider, profile)
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
    ): Session {
        val exercises = day.slots.map { slot ->
            materializeSlot(slot, week, recipe, metadata, idProvider, profile)
        }
        val parts = groupParts(day, exercises, idProvider)
        return Session(
            id = idProvider.newId(),
            name = day.label,
            scheduleLabel = day.label,
            dayOfWeek = dayOfWeek,
            assignedDays = listOf(dayOfWeek),
            exercises = exercises,
            parts = parts,
            isMainSession = day.slots.any { it.role == SlotRole.T1_MAIN },
            origin = SessionOrigin.USER_DRAFT,
            requirement = SessionRequirement.REQUIRED,
        )
    }

    private fun groupParts(day: DayRecipe, exercises: List<Exercise>, idProvider: IdProvider): List<SessionPart> {
        val buckets = linkedMapOf<String, MutableList<Exercise>>()
        day.slots.zip(exercises).forEach { (slot, exercise) ->
            val name = when (slot.role) {
                SlotRole.SPEED -> "Velocidad"
                SlotRole.T1_MAIN -> "Principal"
                SlotRole.TECHNIQUE -> "Técnica"
                SlotRole.T2_SUPPLEMENTAL -> "Suplementario"
                SlotRole.T3_ACCESSORY -> "Accesorios"
            }
            buckets.getOrPut(name) { mutableListOf() }.add(exercise)
        }
        return buckets.map { (name, items) ->
            SessionPart(id = idProvider.newId(), name = name, exercises = items)
        }
    }

    private fun materializeSlot(
        slot: SlotRecipe,
        week: WeekRecipe,
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        idProvider: IdProvider,
        profile: PowerliftingProfile?,
    ): Exercise {
        val meta = metadata.metadata(slot.lift.configurationId)
        val display = listOfNotNull(meta?.displayName, slot.technique?.displayName()).joinToString(" · ").ifBlank {
            slot.lift.configurationId
        }
        val tm = slot.lift.liftSlot?.let { TrainingMaxResolver.trainingMax(profile, it, recipe.trainingMaxPercent) }
        val oneRm = slot.lift.liftSlot?.let { TrainingMaxResolver.oneRm(profile, it) }
        val working = slot.sets.filter { !it.isWarmup }
        val warmups = slot.sets.filter { it.isWarmup }
        val usesPercent = working.any { it.percent != null } || warmups.any { it.percent != null }
        val reference = when (working.firstOrNull()?.loadBasis ?: slot.sets.firstOrNull()?.loadBasis) {
            LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> oneRm
            else -> tm
        }
        val sets = working.map { set -> materializeSet(set, slot, week, reference, idProvider) }
        val cues = buildList {
            addAll(meta?.let { emptyList() } ?: emptyList())
            slot.technique?.let { add(it.executionCue()) }
        }
        return Exercise(
            id = idProvider.newId(),
            name = display,
            exerciseDbId = slot.lift.configurationId,
            exerciseId = slot.lift.configurationId,
            canonicalExerciseId = slot.lift.configurationId,
            exerciseFamilyId = slot.lift.configurationId.substringBefore("__"),
            relativeToCanonicalExerciseId = slot.lift.configurationId.takeIf { slot.technique != null },
            relationshipType = ExerciseRelationshipType.TECHNIQUE.takeIf { slot.technique != null },
            relationshipNotes = slot.technique?.displayName(),
            sets = sets,
            restTime = slot.restSeconds,
            trainingMode = if (usesPercent) TrainingMode.RM else TrainingMode.REPS,
            reference1RM = reference,
            variantName = slot.technique?.displayName(),
            isUnilateral = slot.isUnilateral,
            isCompetitionLift = slot.isCompetitionLift,
            executionCues = cues,
            catalogRevision = "v2-approved-2026-08-12-a",
            catalogDefinitionId = slot.lift.configurationId.substringBefore("__"),
            catalogConfigurationId = slot.lift.configurationId,
            performanceProfileId = meta?.performanceProfileId ?: slot.lift.configurationId,
            occurrenceId = idProvider.newId(),
            slotRole = slot.role,
            techniqueModifier = slot.technique,
            warmupSets = if (warmups.isNotEmpty()) {
                warmups.map { set ->
                    WarmupSetDefinition(
                        idProvider.newId(),
                        set.percent ?: 40.0,
                        set.reps ?: 5,
                        restBetween = 60,
                    )
                }
            } else if (slot.role == SlotRole.T1_MAIN && usesPercent) {
                listOf(
                    WarmupSetDefinition(idProvider.newId(), 40.0, 5, restBetween = 60),
                    WarmupSetDefinition(idProvider.newId(), 55.0, 3, restBetween = 90),
                    WarmupSetDefinition(idProvider.newId(), 65.0, 1, restBetween = 120),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun materializeSet(
        set: SetRecipe,
        slot: SlotRecipe,
        week: WeekRecipe,
        tm: Double?,
        idProvider: IdProvider,
    ): ExerciseSet {
        val percent = resolvePercent(set, slot, week)
        val weight = percent?.let { TrainingMaxResolver.loadKg(it, tm) }
        val range = if (set.repsMin != null && set.repsMax != null) RepRange(set.repsMin, set.repsMax) else null
        val mode = when {
            set.amrap -> IntensityMode.AMRAP
            percent != null -> IntensityMode.SOLO_RM
            set.rir != null -> IntensityMode.RIR
            set.rpe != null -> IntensityMode.RPE
            else -> IntensityMode.RPE
        }
        return ExerciseSet(
            id = idProvider.newId(),
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

    private fun resolvePercent(set: SetRecipe, slot: SlotRecipe, week: WeekRecipe): Double? {
        val raw = set.percent ?: return null
        if (set.loadBasis != LoadBasis.PERCENT_OF_TOP_SET) return raw
        if (set.isTopSet) return raw
        val sameLift = week.days.flatMap { day ->
            day.slots.filter { it.lift.liftSlot != null && it.lift.liftSlot == slot.lift.liftSlot }
        }
        val top = sameLift.flatMap { it.sets }.firstOrNull { it.isTopSet }?.percent ?: 100.0
        val isVolume = slot.sets.none { it.isTopSet } && slot.sets.count { !it.isWarmup } >= 5
        if (isVolume) return raw / 100.0 * top
        val volumeSlot = sameLift.firstOrNull { candidate ->
            candidate.sets.none { it.isTopSet } && candidate.sets.count { !it.isWarmup } >= 5
        }
        val volumeFactor = volumeSlot?.sets?.firstOrNull { !it.isWarmup }?.percent ?: 90.0
        val volumeResolved = volumeFactor / 100.0 * top
        return raw / 100.0 * volumeResolved
    }

    /** Receta weekday 1-7 relativa al lunes; [startDay] rota el microciclo sin reordenar días. */
    private fun rotateWeekday(recipeDay: Int?, startDay: Int): Int? {
        if (recipeDay == null) return null
        val safeRecipe = recipeDay.coerceIn(1, 7)
        val safeStart = startDay.coerceIn(1, 7)
        return ((safeStart - 1) + (safeRecipe - 1)).mod(7) + 1
    }
}
