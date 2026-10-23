package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.repository.CalibrationDecisionRepository
import com.example.kpkn.domain.calibration.*
import com.example.kpkn.domain.calculations.*
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.expectedSidesForSetIndex
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import java.util.UUID

/** Keep execution/log identity on the base session, but resolve prescriptions from the active variant. */
private fun calibrationExercises(state: WorkoutUiState): List<Exercise> {
    val base = state.session ?: return emptyList()
    val active = when (state.activeMode) {
        WeekVariant.A -> base
        WeekVariant.B -> base.sessionB ?: base
        WeekVariant.C -> base.sessionC ?: base
        WeekVariant.D -> base.sessionD ?: base
    }
    return active.allExercises()
}

internal fun calibrationExecutionId(state: WorkoutUiState): String = CalibrationDecisionRepository.executionId(
    state.programId, state.session?.id.orEmpty(), state.startTimeMs)

internal fun calibrationSetId(set: ExerciseSet, side: String?): String =
    if (side == null) set.id else "${set.id.length}:${set.id}:$side"

private fun calibrationPrescription(set: ExerciseSet, side: String?): ExerciseSet {
    val target = when(side) { "left" -> set.leftTarget; "right" -> set.rightTarget; else -> null } ?: return set
    return set.copy(weight = target.weight ?: set.weight,
        targetReps = target.targetReps ?: set.targetReps.takeIf { target.targetRepsRange == null },
        targetRepsRange = target.targetRepsRange ?: set.targetRepsRange.takeIf { target.targetReps == null },
        targetRIR = target.targetRIR ?: set.targetRIR.takeIf { target.targetRPE == null },
        targetRPE = target.targetRPE ?: set.targetRPE.takeIf { target.targetRIR == null },
        plannedTargetV2 = target.targetValue ?: set.plannedTargetV2,
        intensityMode = target.intensityMode ?: set.intensityMode,
        isFailure = when(target.intensityMode) { null -> set.isFailure; IntensityMode.FAILURE -> true; else -> false })
}

internal fun calibrationContextForSet(
    state: WorkoutUiState, exercise: Exercise, index: Int, side: String?,
    recorded: CompletedSet? = null,
): CalibrationContext {
    val set = exercise.sets[index]
    val profile = state.contextProfilesV3[state.activeContextProfileByExerciseId[exercise.id]]
    val tags = state.userCreatedTags.values.flatten()
    val activeTag = tags.firstOrNull { it.id in state.activeTagsByExercise[exercise.id].orEmpty() }
        ?: tags.firstOrNull { WorkoutTagResolver.namesMatch(it.name, state.exerciseTags[exercise.id]) }
    val tag = recorded?.tagId ?: activeTag?.id ?: profile?.tagId ?: state.exerciseTags[exercise.id]
    val subTags = state.activeSubTagsByExercise[exercise.id].orEmpty()
    val tech = tags.flatMap { it.subTags }.filter {
        it.id in subTags && it.category in setOf(SubTagCategory.TECNICA, SubTagCategory.MARCA)
    }.map { it.name.lowercase().replace(" ", "_") }.sorted().joinToString("+").ifBlank { null }
    val key = workoutSetKey(exercise.id, index, side)
    val mode = recorded?.loadMode ?: state.setDrafts[key]?.loadMode
        ?: resolvePersistedLoadModeForSet(exercise.id, index, tag,
            state.persistedLoadModeBySet, state.persistedLoadModeByExercise)
        ?: set.loadModeV2 ?: profile?.loadMode ?: inferDefaultLoadModeFromCatalog(exercise)
    val performanceKey = recorded?.homologatedResultV3?.contextKey ?: recorded?.setOutcomeV2?.contextKey
        ?: buildWorkoutContextKey(exercise.resolvedCanonicalExerciseId(),
            recorded?.machineBrand ?: profile?.machineBrand ?: set.machineBrand, tag, mode,
            set.unitModeV2 ?: when(exercise.trainingMode) {
                TrainingMode.TIME -> UnitModeV2.TIME; TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                TrainingMode.CUSTOM -> UnitModeV2.CUSTOM; else -> UnitModeV2.REPS
            }, tech)
    return CalibrationContext(
        exercise.catalogConfigurationId ?: exercise.resolvedCanonicalExerciseId(), performanceKey, side,
        recorded?.setupProfileId ?: profile?.setupProfileId ?: set.defaultSetupProfileIdV3 ?: set.setupId,
        mode, set.loadQuantityConvention.takeUnless { it == LoadQuantityConvention.UNSPECIFIED }
            ?: exercise.loadQuantityConvention,
    )
}

internal fun calibrationIncrement(exercise: Exercise, set: ExerciseSet, inventory: EquipmentInventory? = null): Double {
    inventory?.machines?.singleOrNull { it.configurationId != null && it.configurationId == exercise.catalogConfigurationId }
        ?.incrementKg?.takeIf { it.isFinite() && it > 0 }?.let { return it }
    val equipment = listOfNotNull(exercise.setupDetails?.equipmentNotes, set.machineBrand,
        exercise.contextProfilesV3.firstOrNull { it.id == set.contextProfileIdV3 }?.machineBrand,
        exercise.name).joinToString(" ").lowercase()
    return when {
        "mancuerna" in equipment || "dumbbell" in equipment -> 1.0
        listOf("polea", "maquina", "máquina", "machine").any { it in equipment } -> 5.0
        else -> 2.5
    }
}

internal fun manuallyFixedCalibrationSet(state: WorkoutUiState, exercise: Exercise, index: Int, side: String?): Boolean {
    val key = workoutSetKey(exercise.id, index, side)
    val baseKey = workoutSetKey(exercise.id, index)
    val set = exercise.sets[index]
    val draft = state.setDrafts[key] ?: state.setDrafts[baseKey]
    return key in state.manualLoadOverrides || baseKey in state.manualLoadOverrides ||
        (if (side.isNullOrBlank()) set.manualLoadRequiredSides.isNotEmpty()
            else side in set.manualLoadRequiredSides || "bilateral" in set.manualLoadRequiredSides) ||
        (draft?.weightManuallyFixed == true || (draft?.weightManuallyFixed == null &&
            draft?.isDirty == true && draft.weightText?.replace(',', '.')?.toDoubleOrNull() != null))
}

internal fun pendingCalibrationSets(
    state: WorkoutUiState, exercise: Exercise, sourceIndex: Int, context: CalibrationContext,
): List<PendingCalibrationSet> = exercise.sets.mapIndexedNotNull { index, set ->
    if (index <= sourceIndex || set.isEmptySlot) return@mapIndexedNotNull null
    val side = context.side
    // Live recording uses left/right; the shared prescription rules expose L/R.
    val prescribedSide = when (side) { "left" -> "L"; "right" -> "R"; else -> side }
    if (side != null && prescribedSide !in exercise.expectedSidesForSetIndex(index)) return@mapIndexedNotNull null
    val key = workoutSetKey(exercise.id, index, side)
    val prescription = calibrationPrescription(set, side)
    val currentContext = calibrationContextForSet(state, exercise, index, side)
    val savedBase = state.calibrationDecisions.asReversed().firstNotNullOfOrNull { record ->
        if (record.decision.exerciseId == exercise.id && record.decision.context == currentContext)
            record.pendingPrescriptions[calibrationSetId(set, side)]?.beforeKg
                ?: record.decision.affectedSets.firstOrNull { it.setId == calibrationSetId(set, side) }?.beforeKg
        else null
    }
    val base = prescription.weight ?: savedBase ?: calculateSuggestedLoad(exercise, prescription)
        ?: state.loadSuggestions[key]?.originalWeight ?: 0.0
    PendingCalibrationSet(calibrationSetId(set, side), exercise.id,
        currentContext, base, base,
        targetReps = prescription.plannedRepAnchor() ?: prescription.plannedTargetV2?.toInt(),
        targetRir = prescription.targetRIR ?: prescription.targetRPE?.let(::rpeToRir) ?: if (prescription.isFailure) 0 else 2,
        isCompleted = key in state.completedSets || workoutSetKey(exercise.id, index) in state.completedSets || key in state.omittedSetKeys,
        isManuallyFixed = manuallyFixedCalibrationSet(state, exercise, index, side))
}

/** Recalculate contextual e1RM from raw full reps; EWMA of volume/old cached scores is never a reference. */
internal fun calibrationHistoryReference(history: List<WorkoutLog>, context: CalibrationContext): HistoricalCalibrationReference? {
    val capacities = history.sortedBy { it.date }.flatMap { log -> log.completedExercises.flatMap { it.sets } }
        .filter { it.contextKey == context.key && it.loadMode == context.loadMode &&
            !it.skipped && !it.isWarmup && !it.isFailedSet && it.reps > 0 }
        .mapNotNull { set ->
            val external = set.originalLoad ?: set.recordedPayloadV3?.externalLoad
                ?: set.weight.takeUnless { context.loadMode == LoadModeV2.LASTRE }
            external?.let { calculateHybrid1RM(it, set.reps) }
        }
        .filter { it.isFinite() && it > 0.0 }
    val ewma = WorkoutPerformanceHomologationEngine.capacityHistoryEwma(capacities)
    return ewma?.let { HistoricalCalibrationReference(context, it) }
}

internal fun buildRecordedCalibration(
    state: WorkoutUiState, exercise: Exercise, index: Int, completed: CompletedSet,
    history: List<WorkoutLog>, accepted: List<AcceptedCalibrationReference>, now: Long,
    inventory: EquipmentInventory? = null,
): CalibrationDecisionRecord {
    val context = calibrationContextForSet(state, exercise, index, completed.side, completed)
    val source = RecordedCalibrationSet(completed.id, exercise.id, context,
        completed.originalLoad ?: completed.recordedPayloadV3?.externalLoad
            ?: completed.weight.takeUnless { context.loadMode == LoadModeV2.LASTRE } ?: 0.0,
        completed.reps, completed.partialReps ?: 0,
        completed.rir ?: completed.rpe?.let(::rpeToRir), completed.isFailure)
    // The unscoped declared fields belong to the planned base context. A live tag/brand/side
    // change cannot relabel them as a declaration for that new context.
    val declaredContext = calibrationContextForSet(WorkoutUiState(), exercise, index, null)
    val declarationMatches = context == declaredContext
    val references = CalibrationReferenceCandidates(accepted,
        calibrationHistoryReference(history, context),
        exercise.reference1RM?.takeIf { declarationMatches }?.let { DeclaredCalibrationOneRm(context, it) },
        exercise.prFor1RM?.takeIf { declarationMatches }?.let { DeclaredCalibrationPr(context, it.weight, it.reps) })
    val pending = pendingCalibrationSets(state, exercise, index, context)
    val decision = CalibrationEngine.calibrate(source,
        pending, references, now,
        scope = exercise.sets[index].calibrationScope ?: CalibrationScope.SESSION,
        equipment = CalibrationEquipment(calibrationIncrement(exercise, exercise.sets[index], inventory)))
    return CalibrationDecisionRecord(UUID.randomUUID().toString(), completed.id, calibrationExecutionId(state),
        state.programId.takeIf { it.isNotBlank() }, exercise.name, decision, createdAt = now,
        loadIncrementKg = calibrationIncrement(exercise, exercise.sets[index], inventory),
        pendingPrescriptions = pending.filter { it.context == context && !it.isCompleted && !it.isManuallyFixed }
            .associate { it.setId to CalibrationPendingPrescription(it.baseLoadKg, it.targetReps, it.targetRir) })
}

/** Every edited source replaces its previous decision. Other sources/exercises retain their state. */
internal fun replaceRecordedCalibration(
    records: List<CalibrationDecisionRecord>, sourceSetId: String, next: CalibrationDecisionRecord?, now: Long,
): List<CalibrationDecisionRecord> = records.map {
    if (it.sourceSetId == sourceSetId && it.status != CalibrationStatus.SUPERSEDED)
        it.copy(status = CalibrationStatus.SUPERSEDED, resolvedAt = now) else it
} + listOfNotNull(next)

/** S4 snapshots also need rebuilding when S3 was skipped or never registered. */
internal fun needsCorrectedCalibrationRefresh(state: WorkoutUiState): Boolean =
    state.calibrationDecisions.isNotEmpty() || state.completedSets.values.any { it.apreXrm != null } ||
        calibrationExercises(state).any { liveApreBlock(state, it) != null }

/** Voice/video corrections and undo use the same replacement contract as registering again. */
internal suspend fun refreshCorrectedCalibrations(
    previous: WorkoutUiState, next: WorkoutUiState, history: List<WorkoutLog>,
    inventory: EquipmentInventory? = null, now: Long = System.currentTimeMillis(),
    accepted: suspend (String) -> List<AcceptedCalibrationReference>,
): WorkoutUiState {
    var result = next
    previous.calibrationDecisions.filter { it.status != CalibrationStatus.SUPERSEDED }
        .distinctBy { it.sourceSetId }.forEach { record ->
            val old = previous.completedSets.values.firstOrNull { it.id == record.sourceSetId }
            val entry = next.completedSets.entries.firstOrNull { it.value.id == record.sourceSetId }
            val updated = entry?.value
            fun signature(set: CompletedSet?): List<Any?>? = set?.let { listOf(it.weight, it.originalLoad, it.reps,
                it.partialReps, it.rir, it.rpe, it.isFailure, it.isFailedSet, it.loadMode, it.contextKey, it.side, it.purpose, it.skipped) }
            if (signature(old) != signature(updated)) {
                val parsed = entry?.key?.let(::parseCompletedSetKey)
                val exercise = calibrationExercises(next).firstOrNull { it.id == parsed?.exerciseId }
                val replacement = if (exercise != null && parsed != null && updated != null && !updated.skipped &&
                    updated.performedPurpose()?.isAmrap == true) {
                    buildRecordedApreCalibration(result, exercise, parsed.setIdx, updated, now, inventory)
                        ?: if (updated.performedPurpose() == SetPurpose.CALIBRATOR)
                            buildRecordedCalibration(result, exercise, parsed.setIdx, updated, history, accepted(updated.id), now, inventory)
                        else null
                } else null
                result = result.copy(calibrationDecisions = replaceRecordedCalibration(result.calibrationDecisions,
                    record.sourceSetId, replacement, now))
            }
        }
    val corrected = result.completedSets.mapValues { (key, completed) ->
        if (previous.completedSets[key] == completed) completed else {
            val parsed = parseCompletedSetKey(key)
            val exercise = calibrationExercises(result).firstOrNull { it.id == parsed?.exerciseId }
            completed.copy(apreXrm = if (parsed != null && exercise != null && parsed.setIdx in exercise.sets.indices)
                buildRecordedApreXrm(result, exercise, parsed.setIdx, completed, now, inventory) else null)
        }
    }
    return result.copy(completedSets = corrected)
}

internal fun resolveCalibration(
    state: WorkoutUiState, id: String, action: CalibrationStatus, now: Long,
): WorkoutUiState {
    val record = state.calibrationDecisions.firstOrNull { it.id == id } ?: return state
    if (action == CalibrationStatus.REVERTED && record.status != CalibrationStatus.APPLIED) return state
    if (action in setOf(CalibrationStatus.APPLIED, CalibrationStatus.KEPT) && record.status != CalibrationStatus.PROPOSED) return state
    if (action !in setOf(CalibrationStatus.APPLIED, CalibrationStatus.KEPT, CalibrationStatus.REVERTED)) return state
    val d = record.decision
    if (record.apre != null && action == CalibrationStatus.APPLIED) {
        val plannedExercise = calibrationExercises(state).firstOrNull { it.id == d.exerciseId } ?: return state
        val block = liveApreBlock(state, plannedExercise) ?: return state
        if (block.variant.xrmReps != record.apre.xrmReps ||
            plannedExercise.sets[block.thirdIndex].id != record.apre.sourcePlannedSetId ||
            plannedExercise.sets[block.fourthIndex].id != record.apre.targetPlannedSetId) return state
    }
    if (action == CalibrationStatus.APPLIED && d.type == CalibrationDecisionType.NOT_APPLIED) return state
    val exercise = calibrationExercises(state).firstOrNull { it.id == d.exerciseId } ?: return state
    val sourceIndex = exercise.sets.indexOfFirst { set ->
        state.completedSets.any { (key, done) -> done.id == record.sourceSetId &&
            parseCompletedSetKey(key)?.let { it.exerciseId == exercise.id && exercise.sets.getOrNull(it.setIdx)?.id == set.id } == true }
    }
    if (sourceIndex < 0) return state
    val pending = pendingCalibrationSets(state, exercise, sourceIndex, d.context)
    val eligibleIds = pending.filter { !it.isCompleted && !it.isManuallyFixed && it.context == d.context }
        .filter { set -> record.pendingPrescriptions[set.setId]?.let {
            it.targetReps == set.targetReps && it.targetRir == set.targetRir
        } ?: true }
        .filter { set -> d.affectedSets.any { it.setId == set.setId && it.beforeKg == set.baseLoadKg } }
        .map { it.setId }.toSet()
    val next = record.copy(status = action, resolvedAt = now,
        appliedSetIds = if (action == CalibrationStatus.APPLIED) eligibleIds else record.appliedSetIds)
    return state.copy(calibrationDecisions = state.calibrationDecisions.map { if (it.id == id) next else it })
}

internal data class CalibrationSetLoad(val weight: Double, val calibrated: Boolean, val originalWeight: Double)

/** Before/after are authoritative assignments, including after Keep/Undo; no factor is applied here. */
internal fun calibrationLoadForSet(state: WorkoutUiState, exercise: Exercise, index: Int, side: String?): CalibrationSetLoad? {
    if (state.calibrationDecisions.isEmpty()) return null
    val set = exercise.sets.getOrNull(index) ?: return null
    val completed = state.completedSets[workoutSetKey(exercise.id, index, side)]
    if (completed == null && manuallyFixedCalibrationSet(state, exercise, index, side)) return null
    val context = calibrationContextForSet(state, exercise, index, side)
    val setId = calibrationSetId(set, side)
    val records = state.calibrationDecisions.filter { r ->
        r.decision.exerciseId == exercise.id && r.decision.context == context &&
            (setId in r.pendingPrescriptions || r.decision.affectedSets.any { it.setId == setId })
    }
    val record = records.lastOrNull { it.status != CalibrationStatus.SUPERSEDED } ?: records.lastOrNull() ?: return null
    record.apre?.let { apre ->
        val block = liveApreBlock(state, exercise) ?: return null
        if (block.variant.xrmReps != apre.xrmReps || exercise.sets[block.thirdIndex].id != apre.sourcePlannedSetId ||
            exercise.sets[block.fourthIndex].id != apre.targetPlannedSetId) return null
    }
    val prescription = record.pendingPrescriptions[setId]
    val change = record.decision.affectedSets.firstOrNull { it.setId == setId }
        ?: prescription?.let { CalibrationLoadChange(setId, it.beforeKg, it.beforeKg) } ?: return null
    // If the prescription changed since the proposal, it is no longer compatible.
    val planned = calibrationPrescription(set, side)
    val base = planned.weight ?: prescription?.beforeKg ?: calculateSuggestedLoad(exercise, planned) ?: change.beforeKg
    if (base != change.beforeKg) return null
    val pending = pendingCalibrationSets(state, exercise, -1, context).firstOrNull { it.setId == setId }
    if (prescription != null && pending != null &&
        (prescription.targetReps != pending.targetReps || prescription.targetRir != pending.targetRir)) return null
    val applied = record.status == CalibrationStatus.APPLIED && record.decision.scope == CalibrationScope.SESSION &&
        setId in record.appliedSetIds &&
        (completed == null || (completed.originalLoad ?: completed.weight) == change.afterKg)
    val actual = completed?.let { it.originalLoad ?: it.recordedPayloadV3?.externalLoad ?: it.weight }
    val wasRecordedWithAdjustment = completed != null && setId in record.appliedSetIds && actual == change.afterKg
    return CalibrationSetLoad(actual ?: if (applied) change.afterKg else change.beforeKg,
        applied || wasRecordedWithAdjustment, change.beforeKg)
}

internal fun calibrationRouteLoads(state: WorkoutUiState): Map<String, Double> = buildMap {
    if (state.calibrationDecisions.isEmpty()) return@buildMap
    calibrationExercises(state).forEach { exercise ->
        exercise.sets.indices.forEach { index ->
            listOf(null, "left", "right").forEach { side ->
                calibrationLoadForSet(state, exercise, index, side)?.takeIf { it.calibrated }?.let {
                    put(workoutSetKey(exercise.id, index, side), it.weight)
                }
            }
        }
    }
}

internal fun acceptedCalibrationLoad(state: WorkoutUiState, exercise: Exercise, index: Int, side: String?, now: Long,
    inventory: EquipmentInventory? = null): Double? {
    if (state.acceptedCalibrationReferences.isEmpty()) return null
    val set = exercise.sets.getOrNull(index) ?: return null
    // The authored TM recipe continues through the existing program proposal channel.
    if (set.loadBasis == LoadBasis.PERCENT_TM || exercise.referenceTrainingMax != null ||
        exercise.trainingMode == TrainingMode.SOLO_RPE ||
        manuallyFixedCalibrationSet(state, exercise, index, side)) return null
    val context = calibrationContextForSet(state, exercise, index, side)
    val reference = ReferenceResolver.resolve(context, CalibrationReferenceCandidates(
        acceptedCalibrations = state.acceptedCalibrationReferences), now)
    val capacity = reference.capacityKg ?: return null
    val prescription = calibrationPrescription(set, side)
    val load = prescription.targetPercentageRM?.takeIf { it.isFinite() && it > 0 }?.let { capacity * it / 100.0 }
        ?: calculateWeightFrom1RMAndIntensity(capacity, prescription)
    return load?.let {
        LoadSuggestionEngine.roundLoad(it, calibrationIncrement(exercise, set, inventory))
    }
}

internal fun calibrationSummaryLines(records: List<CalibrationDecisionRecord>): List<String> = records
    .filter { it.status != CalibrationStatus.SUPERSEDED }.map { r ->
        val resolution = when (r.status) {
            CalibrationStatus.PROPOSED -> if (r.decision.type == CalibrationDecisionType.NOT_APPLIED) "No aplicada: ${r.decision.reason}" else "Propuesta sin aplicar"
            CalibrationStatus.APPLIED -> if (r.decision.scope == CalibrationScope.NEXT_SESSIONS) "Propuesta guardada para la próxima sesión de este ejercicio" else "Aplicada · ${r.appliedSetIds.size} series calibradas"
            CalibrationStatus.KEPT -> "Conservaste las cargas del plan"
            CalibrationStatus.REVERTED -> "Revertida · cargas pendientes restauradas"
            CalibrationStatus.SUPERSEDED -> "Sustituida"
        }
        val summary = r.apre?.let { when (r.status) {
            CalibrationStatus.PROPOSED -> if (r.decision.type == CalibrationDecisionType.NOT_APPLIED)
                "APRE · ${r.decision.reason}" else "APRE · ajuste de S4 pendiente"
            CalibrationStatus.APPLIED -> if (r.appliedSetIds.isEmpty()) "APRE · S4 se conserva"
                else "APRE · S4 ajustada con la tabla de Mann"
            CalibrationStatus.KEPT -> "APRE · conservaste la carga de S4"
            CalibrationStatus.REVERTED -> "APRE · carga pendiente de S4 restaurada"
            CalibrationStatus.SUPERSEDED -> "APRE · sustituida"
        } } ?: resolution
        "${r.exerciseName} · $summary"
    }
