package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.SupersetVisualPlacement
import com.example.kpkn.data.models.TrainingBackup
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateKind
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import java.util.UUID

/** Why a session is copied. It defines what execution state is allowed to survive. */
enum class SessionClonePurpose {
    TEMPLATE_STORAGE,
    TEMPLATE_APPLY,
    WEEK_DUPLICATE,
    PROGRESSION_SEED,
}

/**
 * Pure, canonical session cloner and template merger.
 *
 * Every product path needing fresh session content goes through this object. It
 * owns nested UUID regeneration, superset reference remapping and runtime-result
 * sanitisation so a copy can never look completed in Workout.
 */
object SessionTemplateEngine {

    fun canApplyTemplate(template: SessionTemplate, targetSession: Session): Boolean =
        template.publicationStatus != SessionTemplatePublicationStatus.HIDDEN_UNVERIFIED &&
            when (template.kind) {
                SessionTemplateKind.TRAINING -> !targetSession.isCompetitionMeet
                SessionTemplateKind.MEET_DAY -> targetSession.isCompetitionMeet
            }

    fun applyTemplate(
        template: SessionTemplate,
        targetSession: Session,
        mode: SessionTemplateApplyMode,
    ): Session {
        require(canApplyTemplate(template, targetSession)) {
            "La plantilla está oculta o su tipo no corresponde a la sesión de destino."
        }
        return when (mode) {
            SessionTemplateApplyMode.REPLACE -> applyReplace(template, targetSession)
            SessionTemplateApplyMode.APPEND -> applyAppend(template, targetSession)
        }
    }

    /** Content includes executable strength work and all standalone modalities. */
    fun sessionHasContent(session: Session): Boolean =
        session.exercises.isNotEmpty() ||
            session.warmup.isNotEmpty() ||
            session.targetDurationMinutes?.let { it > 0 } == true ||
            session.parts.any { part ->
                part.exercises.isNotEmpty() ||
                    part.mobilitySeries.isNotEmpty() ||
                    part.mobilityConfig?.totalMinutes?.let { it > 0 } == true ||
                    part.isCardioGroup ||
                    part.targetDurationMinutes?.let { it > 0 } == true
            }

    /**
     * Strict execution predicate used by program validation/progression.
     * [sessionHasContent] remains broad for overwrite confirmation in the
     * editor; cursor transitions require an actual executable prescription.
     */
    fun sessionHasExecutableContent(session: Session): Boolean {
        val exercises = session.allExercises()
        val hasStrength = exercises.any { exercise ->
            exercise.cardioDetails == null &&
                exercise.mobilitySeries.isEmpty() &&
                exercise.mobilityConfig == null &&
                exerciseHasExecutableStrengthPrescription(exercise)
        }
        val hasCardio = exercises.any { exercise ->
            exercise.cardioDetails?.let(::isExecutableCardio) == true
        }
        val hasMobility = exercises.any { exercise ->
            exercise.mobilitySeries.any(::isExecutableMobility) ||
                (exercise.mobilityConfig?.totalMinutes ?: 0) > 0
        } || session.parts.any { part ->
            part.mobilitySeries.any(::isExecutableMobility) ||
                (part.mobilityConfig?.totalMinutes ?: 0) > 0
        }
        val hasWarmup = session.warmup.any(::isExecutableWarmup)
        return hasStrength || hasCardio || hasMobility || hasWarmup
    }

    /**
     * Strict USER-template gate: executable content is not enough when another
     * strength card in the same session is still a placeholder.  Dedicated
     * cardio/mobility parts may omit strength sets, but every non-modality
     * exercise must carry at least one concrete prescription.
     */
    fun sessionHasCompleteExecutableContent(session: Session): Boolean {
        if (!sessionHasExecutableContent(session)) return false
        val modalityPartExerciseIds = session.parts
            .filter { it.isCardioGroup || it.isMobilityGroup }
            .flatMap { it.exercises }
            .map { it.id }
            .toSet()
        val strengthComplete = session.allExercises()
            .filterNot { exercise ->
                exercise.id in modalityPartExerciseIds ||
                    exercise.cardioDetails != null ||
                    exercise.mobilitySeries.isNotEmpty() ||
                    exercise.mobilityConfig != null
            }
            .all(::exerciseHasExecutableStrengthPrescription)
        if (!strengthComplete) return false
        if (session.allExercises().any { exercise ->
                exercise.cardioDetails?.let { !isExecutableCardio(it) } == true ||
                    exercise.mobilitySeries.any { !isExecutableMobility(it) } ||
                    exercise.mobilityConfig?.let { !isExecutableMobilityConfig(it) } == true
            }
        ) return false
        if (session.parts.any { part ->
                part.mobilitySeries.any { !isExecutableMobility(it) } ||
                    part.mobilityConfig?.let { !isExecutableMobilityConfig(it) } == true
            }
        ) return false
        if (session.warmup.any { !isExecutableWarmup(it) }) return false
        if (session.parts.any { part ->
                val cardioPartValid = !part.isCardioGroup ||
                    (part.exercises.isNotEmpty() && part.exercises.all { exercise ->
                        exercise.cardioDetails?.let(::isExecutableCardio) == true
                    })
                val mobilityPartHasPayload = part.mobilitySeries.isNotEmpty() ||
                    isExecutableMobilityConfig(part.mobilityConfig) ||
                    part.exercises.any { exercise ->
                        exercise.mobilitySeries.isNotEmpty() ||
                            isExecutableMobilityConfig(exercise.mobilityConfig)
                    }
                val mobilityPartValid = !part.isMobilityGroup ||
                    (mobilityPartHasPayload &&
                        part.mobilitySeries.all(::isExecutableMobility) &&
                        part.exercises.all { exercise ->
                            exercise.mobilitySeries.isNotEmpty() ||
                                isExecutableMobilityConfig(exercise.mobilityConfig)
                        })
                !cardioPartValid || !mobilityPartValid
            }
        ) return false
        return true
    }

    private fun isExecutableSet(set: ExerciseSet): Boolean {
        if (set.isEmptySlot) return false
        val explicitFailureOrAmrap = set.isFailure || set.isAmrap ||
            set.intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE ||
            set.intensityMode == com.example.kpkn.data.models.IntensityMode.AMRAP
        return (set.effectiveRepRange()?.max ?: 0) > 0 ||
            (set.targetDuration ?: 0) > 0 ||
            set.targetRPE != null ||
            set.targetRIR != null ||
            (set.targetPercentageRM ?: 0.0) > 0.0 ||
            (set.weight ?: 0.0) > 0.0 ||
            (set.plannedTargetV2 ?: 0.0) > 0.0 ||
            isExecutableUnilateralTarget(set.leftTarget) ||
            isExecutableUnilateralTarget(set.rightTarget) ||
            explicitFailureOrAmrap
    }

    /** A strength exercise needs at least one concrete set, not just an editor mode flag. */
    fun exerciseHasExecutableStrengthPrescription(exercise: Exercise): Boolean =
        exercise.sets.any(::isExecutableSet)

    private fun isExecutableUnilateralTarget(target: UnilateralTarget?): Boolean {
        if (target == null) return false
        val explicitFailureOrAmrap = target.intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE ||
            target.intensityMode == com.example.kpkn.data.models.IntensityMode.AMRAP
        return (target.targetRepsRange?.max ?: target.targetReps ?: 0) > 0 ||
            (target.targetDuration ?: 0) > 0 ||
            (target.targetValue ?: 0.0) > 0.0 ||
            (target.weight ?: 0.0) > 0.0 ||
            target.targetRPE != null ||
            target.targetRIR != null ||
            explicitFailureOrAmrap
    }

    fun isExecutableCardio(details: CardioDetails): Boolean {
        val intervalsValid = details.intervalBlocks.isEmpty() ||
            (details.intervalRounds > 0 && details.intervalBlocks.all { it.isValid() })
        val hiitValid = details.hiit?.let {
            it.workSeconds > 0 && it.rounds > 0 && it.sets > 0 &&
                it.targetRpe in 1.0..10.0
        } ?: true
        return details.effectiveDurationSeconds() > 0 && intervalsValid && hiitValid
    }

    fun isExecutableMobility(series: MobilitySeries): Boolean =
        series.sets > 0 && ((series.durationSeconds ?: 0) > 0 || !series.reps.isNullOrBlank())

    fun isExecutableMobilityConfig(config: MobilityConfig?): Boolean =
        config?.totalMinutes?.let { it > 0 } == true

    fun isExecutableWarmup(warmup: WarmupExercise): Boolean =
        (warmup.duration ?: 0) > 0 ||
            ((warmup.sets ?: 0) > 0 && !warmup.reps.isNullOrBlank())

    /** Compatibility entry point for future program/session copies. */
    fun cloneSessionContent(source: Session): Session =
        cloneSessionContent(source, SessionClonePurpose.WEEK_DUPLICATE)

    fun cloneSessionContent(source: Session, purpose: SessionClonePurpose): Session =
        CloneContext(purpose).cloneSession(
            source = source,
            includeNested = purpose != SessionClonePurpose.TEMPLATE_APPLY &&
                purpose != SessionClonePurpose.TEMPLATE_STORAGE,
        )

    /** Creates the payload persisted as a USER blueprint: active session only. */
    fun cloneForTemplateStorage(source: Session): Session =
        cloneSessionContent(source, SessionClonePurpose.TEMPLATE_STORAGE).copy(
            sessionB = null,
            sessionC = null,
            sessionD = null,
            trainingBackup = null,
            isMeetDay = false,
            isCompetitionSession = false,
            meetBodyweight = null,
            meetResults = null,
            competitionDetails = null,
            competitionRecordId = null,
            competitionKeyDateId = null,
            competitionSportType = null,
            competitionRecordMode = null,
            // A USER template is a reusable prescription, never a pending
            // volume-adjustment decision from the source session.
            volumeAdvances = emptyList(),
        )

    private fun applyReplace(template: SessionTemplate, target: Session): Session {
        val cloned = cloneSessionContent(template.session, SessionClonePurpose.TEMPLATE_APPLY)
        // Template content is a blueprint, never an identity: preserve target
        // name, description, schedule, duration and all session metadata.
        return target.copy(
            exercises = cloned.exercises,
            parts = cloned.parts,
            warmup = cloned.warmup,
            supersetGroups = cloned.supersetGroups,
            origin = SessionOrigin.USER_DRAFT,
        )
    }

    private fun applyAppend(template: SessionTemplate, target: Session): Session {
        val cloned = cloneSessionContent(template.session, SessionClonePurpose.TEMPLATE_APPLY)
        return target.copy(
            parts = target.parts + cloned.parts,
            exercises = target.exercises + cloned.exercises,
            warmup = target.warmup + cloned.warmup,
            supersetGroups = target.allSupersetGroups() + cloned.supersetGroups,
            origin = SessionOrigin.USER_DRAFT,
        )
    }

    private class CloneContext(private val purpose: SessionClonePurpose) {
        private fun fresh(): String = UUID.randomUUID().toString()

        fun cloneSession(source: Session, includeNested: Boolean): Session {
            val partIdMap = source.parts.associate { it.id to fresh() }.toMutableMap()
            val exerciseIdMap = mutableMapOf<String, String>()
            // Seed all declared groups before cloning members; visual-only groups
            // and legacy supersetId links then resolve to the same UUID.
            val groupIdMap = source.allSupersetGroups().associate { it.id to fresh() }.toMutableMap()

            val clonedParts = source.parts.map { part ->
                clonePart(part, partIdMap.getValue(part.id), exerciseIdMap, groupIdMap)
            }
            val clonedLooseExercises = source.exercises.map { exercise ->
                cloneExercise(exercise, exerciseIdMap, groupIdMap)
            }
            val clonedGroups = source.allSupersetGroups().mapNotNull { group ->
                cloneGroup(group, partIdMap, exerciseIdMap, groupIdMap)
            }
            return source.copy(
                id = fresh(),
                exercises = clonedLooseExercises,
                warmup = source.warmup.map(::cloneWarmup),
                parts = clonedParts,
                supersetGroups = clonedGroups,
                trainingBackup = if (includeNested) source.trainingBackup?.let(::cloneBackup) else null,
                sessionB = if (includeNested) source.sessionB?.let { cloneSession(it, true) } else null,
                sessionC = if (includeNested) source.sessionC?.let { cloneSession(it, true) } else null,
                sessionD = if (includeNested) source.sessionD?.let { cloneSession(it, true) } else null,
                // WEEK_DUPLICATE/PROGRESSION_SEED copy a plan, never execution
                // outcomes or links to an athlete's meet record.  Apply the
                // same rule recursively through B/C/D via cloneSession above.
                meetBodyweight = if (isExecutionClone()) null else source.meetBodyweight,
                meetResults = if (isExecutionClone()) null else source.meetResults,
                competitionRecordId = if (isExecutionClone()) null else source.competitionRecordId,
                competitionKeyDateId = if (isExecutionClone()) null else source.competitionKeyDateId,
                volumeAdvances = if (isExecutionClone()) emptyList() else source.volumeAdvances,
                lastModifiedAtMs = 0L,
            )
        }

        private fun cloneBackup(source: TrainingBackup): TrainingBackup {
            val partIdMap = source.parts.associate { it.id to fresh() }.toMutableMap()
            val exerciseIdMap = mutableMapOf<String, String>()
            val groupIdMap = mutableMapOf<String, String>()
            return source.copy(
                exercises = source.exercises.map { cloneExercise(it, exerciseIdMap, groupIdMap) },
                parts = source.parts.map { part ->
                    clonePart(part, partIdMap.getValue(part.id), exerciseIdMap, groupIdMap)
                },
                warmup = source.warmup.map(::cloneWarmup),
                savedAtMs = 0L,
            )
        }

        private fun clonePart(
            source: SessionPart,
            newId: String,
            exerciseIdMap: MutableMap<String, String>,
            groupIdMap: MutableMap<String, String>,
        ): SessionPart = source.copy(
            id = newId,
            exercises = source.exercises.map { cloneExercise(it, exerciseIdMap, groupIdMap) },
            mobilitySeries = source.mobilitySeries.map(::cloneMobilitySeries),
        )

        private fun cloneGroup(
            source: SupersetGroup,
            partIdMap: Map<String, String>,
            exerciseIdMap: Map<String, String>,
            groupIdMap: Map<String, String>,
        ): SupersetGroup? {
            val order = source.exerciseOrder.mapNotNull(exerciseIdMap::get)
            if (order.size < 2) return null
            val placement = source.visualPlacement?.let { visual ->
                SupersetVisualPlacement(
                    partId = visual.partId?.let { partIdMap[it] },
                    anchorExerciseId = visual.anchorExerciseId?.let { exerciseIdMap[it] },
                )
            }
            return source.copy(
                id = groupIdMap[source.id] ?: fresh(),
                exerciseOrder = order,
                visualPlacement = placement,
            )
        }

        private fun cloneExercise(
            source: Exercise,
            exerciseIdMap: MutableMap<String, String>,
            groupIdMap: MutableMap<String, String>,
        ): Exercise {
            val newId = fresh()
            exerciseIdMap[source.id] = newId
            val newGroup = source.supersetGroupRefOrLegacyId()?.let { old ->
                groupIdMap.getOrPut(old, ::fresh)
            }
            return source.copy(
                id = newId,
                occurrenceId = fresh(),
                supersetId = newGroup,
                supersetGroupRef = newGroup,
                sets = source.sets.map(::cloneSet),
                warmupSets = source.warmupSets.map(::cloneWarmupSet),
                mobilitySeries = source.mobilitySeries.map(::cloneMobilitySeries),
                cardioDetails = source.cardioDetails?.let(::cloneCardio),
                consolidatedWeight = null,
                calculated1RM = null,
                prFor1RM = null,
                reference1RM = if (isTemplatePurpose()) null else source.reference1RM,
                goal1RM = if (isTemplatePurpose()) null else source.goal1RM,
                goalPr = if (isTemplatePurpose()) null else source.goalPr,
            )
        }

        private fun cloneSet(source: ExerciseSet): ExerciseSet = source.copy(
            id = fresh(),
            // `weight` is an observed/entered execution value in session JSON.
            // It must never make a copied week look pre-filled or completed.
            weight = null,
            completedReps = null,
            completedDuration = null,
            completedRPE = null,
            completedRIR = null,
            isFailure = false,
            isIneffective = false,
            isPartial = false,
            partialReps = null,
            isDropSet = false,
            isRestPause = false,
            isChangeOfPlans = false,
            dropSets = emptyList(),
            restPauses = emptyList(),
            performanceMode = null,
            technicalWeight = null,
            consolidatedWeight = null,
            attemptResult = null,
            judgingLights = emptyList(),
            technicalQuality = null,
            discomfortIds = emptyList(),
            refereeNotes = null,
            leftTarget = source.leftTarget?.copy(weight = null),
            rightTarget = source.rightTarget?.copy(weight = null),
            plannedIntensityTechniques = source.plannedIntensityTechniques.map(::cloneTechnique),
        )

        private fun cloneTechnique(source: PlannedTechnique): PlannedTechnique = source.copy(id = fresh())
        private fun cloneWarmupSet(source: WarmupSetDefinition): WarmupSetDefinition = source.copy(id = fresh())
        private fun cloneWarmup(source: WarmupExercise): WarmupExercise = source.copy(id = fresh())
        private fun cloneMobilitySeries(source: MobilitySeries): MobilitySeries = source.copy(id = fresh())
        private fun cloneCardio(source: CardioDetails): CardioDetails = source.copy(
            intervalBlocks = source.intervalBlocks.map { it.copy(id = fresh()) },
        )

        private fun isTemplatePurpose(): Boolean =
            purpose == SessionClonePurpose.TEMPLATE_APPLY || purpose == SessionClonePurpose.TEMPLATE_STORAGE

        private fun isExecutionClone(): Boolean =
            purpose == SessionClonePurpose.WEEK_DUPLICATE ||
                purpose == SessionClonePurpose.PROGRESSION_SEED
    }
}
