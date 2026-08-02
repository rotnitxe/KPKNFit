import Foundation

// Redundant Exercise, CompletedExercise, and Session identity extensions removed (defined in ExerciseIdentity.swift and Session.swift)


// Redundant catalogExerciseIndex() and WorkoutContextRecurrenceEngine removed (defined in ExerciseDatabase.swift and WorkoutContextRecurrenceEngine.swift)


// Redundant SupersetRules stub removed (defined in SupersetRules.swift)


// ─── WorkoutSetDraft copy ────────────────────────────────────────────────────

extension WorkoutSetDraft {
    func copy(
        weightText: String? = nil,
        valueText: String? = nil,
        intensityText: String? = nil,
        selectedSide: String? = nil,
        reachedFailure: Bool? = nil,
        voiceFields: Set<WorkoutVoiceField>? = nil,
        isDirty: Bool? = nil,
        updatedAtMs: UInt64? = nil
    ) -> WorkoutSetDraft {
        var d = self
        if let w = weightText { d.weightText = w }
        if let v = valueText { d.valueText = v }
        if let i = intensityText { d.intensityText = i }
        if let s = selectedSide { d.selectedSide = s }
        if let r = reachedFailure { d.reachedFailure = r }
        if let vf = voiceFields { d.voiceFields = vf }
        if let dr = isDirty { d.isDirty = dr }
        if let u = updatedAtMs { d.updatedAtMs = u }
        return d
    }
}

// ─── WorkoutRestModalState copy ──────────────────────────────────────────────

extension WorkoutRestModalState {
    func copy(
        exerciseId: String? = nil,
        exerciseName: String? = nil,
        kind: RestTimerKind? = nil,
        plannedSeconds: Int? = nil,
        suggestedSeconds: Int? = nil,
        activeSeconds: Int? = nil,
        endsAtMs: UInt64? = nil,
        isManualOverride: Bool? = nil,
        notificationsEnabled: Bool? = nil,
        exactAlarmGranted: Bool? = nil,
        soundReady: Bool? = nil,
        skipCurrentExerciseOnFinish: Bool? = nil
    ) -> WorkoutRestModalState {
        var s = self
        if let e = exerciseId { s.exerciseId = e }
        if let e = exerciseName { s.exerciseName = e }
        if let k = kind { s.kind = k }
        if let p = plannedSeconds { s.plannedSeconds = p }
        if let su = suggestedSeconds { s.suggestedSeconds = su }
        if let a = activeSeconds { s.activeSeconds = a }
        if let en = endsAtMs { s.endsAtMs = en }
        if let m = isManualOverride { s.isManualOverride = m }
        if let n = notificationsEnabled { s.notificationsEnabled = n }
        if let ex = exactAlarmGranted { s.exactAlarmGranted = ex }
        if let so = soundReady { s.soundReady = so }
        if let sk = skipCurrentExerciseOnFinish { s.skipCurrentExerciseOnFinish = sk }
        return s
    }

    func withSkipCurrentExerciseOnFinish(_ skip: Bool) -> WorkoutRestModalState {
        var s = self
        s.skipCurrentExerciseOnFinish = skip
        return s
    }
}

// WorkoutContextProfile tagId extension removed

// ─── Session replaceExerciseById ─────────────────────────────────────────────

extension Session {
    func replaceExerciseById(_ exerciseId: String, update: (Exercise) -> Exercise) -> Session {
        let newExercises = exercises.map { $0.id == exerciseId ? update($0) : $0 }
        let newParts = parts.map { part in
            part.withExercises(part.exercises.map { $0.id == exerciseId ? update($0) : $0 })
        }
        return copy(exercises: newExercises, parts: newParts)
    }
}

// ─── PostExerciseFeedbackTarget missingExerciseIds ──────────────────────────

extension PostExerciseFeedbackTarget {
    func missingExerciseIds(_ state: WorkoutUiState) -> [String] {
        let targetIds: [String]
        switch self {
        case .single(let id): targetIds = [id]
        case .supersetGroup(_, let ids): targetIds = ids
        }
        return targetIds.filter { !state.postExerciseFeedbackByExerciseId.keys.contains($0) }
    }
}

// ─── Missing Domain Engine Stubs ─────────────────────────────────────────────

struct WorkoutEditingRules {
    static func normalizeLiveEditedSet(_ mode: TrainingMode, _ set: ExerciseSet) -> ExerciseSet { set }
    static func normalizeLiveEditedExercise(_ exercise: Exercise) -> Exercise { exercise }
    static func canPersistLiveStructuralChanges(_ program: Program) -> Bool { false }
    static func replacementPersistenceOptions(_ program: Program) -> [ReplacementPersistenceScopeV2] {
        if canPersistLiveStructuralChanges(program) {
            return [.SESSION_ONLY, .PERMANENT]
        } else {
            return [.SESSION_ONLY, .MESOCYCLE_MATCHING]
        }
    }
    
    static func buildEditingState(
        completedSets: [String: CompletedSet],
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = nil
    ) -> WorkoutEditingState? {
        guard let exercise = exercise else { return nil }
        let maxIdx = max(0, exercise.sets.count - 1)
        let safeSetIdx = min(max(0, setIdx), maxIdx)
        let isUnilateral = exercise.isEffectivelyUnilateral()
        
        let key = workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: nil)
        let leftKey = workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: "left")
        let rightKey = workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: "right")
        
        let isDone = completedSets[key] != nil || (isUnilateral && completedSets[leftKey] != nil && completedSets[rightKey] != nil)
        if !isDone { return nil }

        let resolvedSide: String?
        if !isUnilateral {
            resolvedSide = nil
        } else if let pref = preferredSide, completedSets[workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: pref)] != nil {
            resolvedSide = pref
        } else if completedSets[leftKey] != nil {
            resolvedSide = "left"
        } else if completedSets[rightKey] != nil {
            resolvedSide = "right"
        } else {
            resolvedSide = nil
        }

        return WorkoutEditingState(
            setKey: workoutSetKey(exerciseId: exercise.id, setIdx: safeSetIdx, side: resolvedSide),
            exerciseId: exercise.id,
            setIdx: safeSetIdx,
            side: resolvedSide
        )
    }
}

struct WorkoutStepRules {
    static func warmupStepKey(_ exerciseId: String, _ setId: String) -> String {
        return "\(exerciseId)_warmup_\(setId)"
    }

    static func mobilityStepKey(_ exerciseId: String, _ mobilityId: String) -> String {
        return "\(exerciseId)_\(mobilityId)"
    }

    static func workingStepKey(_ exerciseId: String, setIdx: Int, side: String? = nil) -> String {
        if let s = side {
            let firstChar = String(s.prefix(1)).uppercased()
            return "\(exerciseId)_\(setIdx)_\(firstChar)"
        }
        return "\(exerciseId)_\(setIdx)"
    }

    static func workingSetStepKey(_ exerciseId: String, _ setIdx: Int) -> String {
        return workingStepKey(exerciseId, setIdx: setIdx, side: nil)
    }

    static func buildSteps(session: Session, visibleExercises: [Exercise]) -> [WorkoutStep] {
        var steps: [WorkoutStep] = []
        var emittedSupersets = Set<String>()

        for exercise in visibleExercises {
            if let groupId = exercise.supersetGroupRefOrLegacyId() {
                if !emittedSupersets.contains(groupId) {
                    emittedSupersets.insert(groupId)
                    appendSupersetSteps(session: session, visibleExercises: visibleExercises, groupId: groupId, steps: &steps)
                }
            } else {
                appendExerciseSteps(exercise: exercise, groupId: nil, steps: &steps)
            }
        }

        return steps
    }

    private static func appendSupersetSteps(
        session: Session,
        visibleExercises: [Exercise],
        groupId: String,
        steps: inout [WorkoutStep]
    ) {
        let visibleIds = Set(visibleExercises.map { $0.id })
        let members = SupersetRules.orderedMembers(session, groupId)
            .filter { visibleIds.contains($0.id) }
        if members.isEmpty { return }

        for exercise in members {
            for mobility in exercise.mobilitySeries {
                steps.append(WorkoutStep(
                    type: .MOBILITY,
                    exerciseId: exercise.id,
                    exerciseName: "Movilidad de superserie",
                    stepKey: mobilityStepKey(exercise.id, mobility.id),
                    setIndex: nil,
                    warmupSetId: nil,
                    mobilitySeriesId: mobility.id,
                    side: nil,
                    supersetGroupId: groupId,
                    supersetRoundIndex: nil,
                    mobilitySeries: members.flatMap { $0.mobilitySeries },
                    restAfterKind: .STANDARD
                ))
            }
        }

        for exercise in members {
            appendWarmupSteps(exercise: exercise, groupId: groupId, steps: &steps)
        }

        let rounds = SupersetRules.roundCount(session, groupId)
        for roundIdx in 0..<rounds {
            for (memberIdx, exercise) in members.enumerated() {
                if roundIdx >= exercise.sets.count { continue }
                
                let remainingMembers = members.suffix(from: memberIdx + 1)
                let isLastMemberWithSet = !remainingMembers.contains(where: { roundIdx < $0.sets.count })
                
                appendWorkingSetSteps(
                    exercise: exercise,
                    setIndex: roundIdx,
                    groupId: groupId,
                    roundIndex: roundIdx,
                    restAfterKind: isLastMemberWithSet ? .SUPERSET_ROUND : .SUPERSET_INTRA,
                    steps: &steps
                )
            }
        }
    }

    private static func appendExerciseSteps(
        exercise: Exercise,
        groupId: String?,
        steps: inout [WorkoutStep]
    ) {
        appendPreparationSteps(exercise: exercise, groupId: groupId, steps: &steps)
        for setIndex in 0..<exercise.sets.count {
            appendWorkingSetSteps(
                exercise: exercise,
                setIndex: setIndex,
                groupId: groupId,
                roundIndex: nil,
                restAfterKind: .STANDARD,
                steps: &steps
            )
        }
    }

    private static func appendPreparationSteps(
        exercise: Exercise,
        groupId: String?,
        steps: inout [WorkoutStep]
    ) {
        for mobility in exercise.mobilitySeries {
            steps.append(WorkoutStep(
                type: .MOBILITY,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: mobilityStepKey(exercise.id, mobility.id),
                setIndex: nil,
                warmupSetId: nil,
                mobilitySeriesId: mobility.id,
                side: nil,
                supersetGroupId: groupId,
                supersetRoundIndex: nil,
                mobilitySeries: [mobility],
                restAfterKind: .STANDARD
            ))
        }
        appendWarmupSteps(exercise: exercise, groupId: groupId, steps: &steps)
    }

    private static func appendWarmupSteps(
        exercise: Exercise,
        groupId: String?,
        steps: inout [WorkoutStep]
    ) {
        for (index, warmupSet) in exercise.warmupSets.enumerated() {
            steps.append(WorkoutStep(
                type: .WARMUP,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: warmupStepKey(exercise.id, warmupSet.id),
                setIndex: index,
                warmupSetId: warmupSet.id,
                mobilitySeriesId: nil,
                side: nil,
                supersetGroupId: groupId,
                supersetRoundIndex: nil,
                mobilitySeries: [],
                restAfterKind: .WARMUP
            ))
        }
    }

    private static func appendWorkingSetSteps(
        exercise: Exercise,
        setIndex: Int,
        groupId: String?,
        roundIndex: Int?,
        restAfterKind: RestTimerKind,
        steps: inout [WorkoutStep]
    ) {
        if !exercise.isEffectivelyUnilateral() {
            steps.append(WorkoutStep(
                type: .WORKING_SET,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: workingStepKey(exercise.id, setIdx: setIndex),
                setIndex: setIndex,
                warmupSetId: nil,
                mobilitySeriesId: nil,
                side: nil,
                supersetGroupId: groupId,
                supersetRoundIndex: roundIndex,
                mobilitySeries: [],
                restAfterKind: restAfterKind
            ))
            return
        }

        let hasLeftOnly = setIndex < exercise.sets.count && exercise.sets[setIndex].leftTarget != nil && exercise.sets[setIndex].rightTarget == nil
        let hasRightOnly = setIndex < exercise.sets.count && exercise.sets[setIndex].rightTarget != nil && exercise.sets[setIndex].leftTarget == nil
        
        let sides: [String]
        if hasLeftOnly {
            sides = ["left"]
        } else if hasRightOnly {
            sides = ["right"]
        } else if exercise.unilateralSideOrder == .LEFT_RIGHT {
            sides = ["left", "right"]
        } else {
            sides = ["right", "left"]
        }

        for (sideIdx, side) in sides.enumerated() {
            steps.append(WorkoutStep(
                type: .WORKING_SET,
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                stepKey: workingStepKey(exercise.id, setIdx: setIndex, side: side),
                setIndex: setIndex,
                warmupSetId: nil,
                mobilitySeriesId: nil,
                side: side,
                supersetGroupId: groupId,
                supersetRoundIndex: roundIndex,
                mobilitySeries: [],
                restAfterKind: (sideIdx == 0 && (exercise.restBetweenSidesSeconds ?? 0) > 0) ? .BETWEEN_SIDES : restAfterKind
            ))
        }
    }
}

// Redundant TrainingEnergyEngine, ExerciseReadinessEngine, and AugeFatigueEngine stubs removed (defined in their respective Domain engine files)


// ─── Voice Helper Functions ───────────────────────────────────────────────────

func workoutVoiceIntensityText(_ interpretation: WorkoutVoiceInterpretation, _ baseIntensityMode: IntensityMode?) -> String {
    if let v = interpretation.intensityValue { return String(v) }
    return ""
}

func workoutVoiceAppliedMessage(_ interpretation: WorkoutVoiceInterpretation, _ isTimeMode: Bool) -> String {
    "Aplicado"
}

// ─── Equatable Conformances ───────────────────────────────────────────────────

extension Program: Equatable {
    public static func == (lhs: Program, rhs: Program) -> Bool {
        lhs.id == rhs.id
    }
}

extension Session: Equatable {
    public static func == (lhs: Session, rhs: Session) -> Bool {
        lhs.id == rhs.id
    }
}

extension Exercise: Equatable {
    public static func == (lhs: Exercise, rhs: Exercise) -> Bool {
        lhs.id == rhs.id
    }
}

extension WorkoutLog: Hashable, Equatable {
    public static func == (lhs: WorkoutLog, rhs: WorkoutLog) -> Bool {
        lhs.id == rhs.id
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

// Redundant CompletedSet effectiveRepEquivalent removed (defined in WorkoutLog.swift)


// ─── WorkoutUiState copy ─────────────────────────────────────────────────────

extension WorkoutUiState {
    func copy(
        session: Session? = nil,
        activeMode: WeekVariant? = nil,
        skippedExerciseIds: Set<String>? = nil
    ) -> WorkoutUiState {
        var s = self
        if let ses = session { s.session = ses }
        if let mode = activeMode { s.activeMode = mode }
        if let skips = skippedExerciseIds { s.skippedExerciseIds = skips }
        return s
    }
}

// ─── Exercise & ExerciseSet Copy Extensions ────────────────────────────────────

extension Exercise {
    public func copy(
        id: String? = nil,
        name: String? = nil,
        exerciseDbId: String? = nil,
        exerciseId: String? = nil,
        canonicalExerciseId: String? = nil,
        exerciseFamilyId: String? = nil,
        relativeToCanonicalExerciseId: String? = nil,
        relationshipType: ExerciseRelationshipType? = nil,
        relationshipNotes: String? = nil,
        sets: [ExerciseSet]? = nil,
        warmupSets: [WarmupSetDefinition]? = nil,
        restTime: Int? = nil,
        isFavorite: Bool? = nil,
        trainingMode: TrainingMode? = nil,
        customUnit: String? = nil,
        reference1RM: Double? = nil,
        targetSessionGoal: String? = nil,
        isStarTarget: Bool? = nil,
        trackHeartRate: Bool? = nil,
        trackRom: Bool? = nil,
        setupDetails: ExerciseSetupDetails? = nil,
        supersetId: String? = nil,
        supersetRestBetween: Int? = nil,
        supersetRestAfter: Int? = nil,
        supersetGroupRef: String? = nil,
        variantName: String? = nil,
        selectedExecutionOption: String? = nil,
        selectedMovementPattern: String? = nil,
        prFor1RM: PrReference? = nil,
        consolidatedWeight: ConsolidatedWeight? = nil,
        brandEquivalencies: [BrandEquivalency]? = nil,
        isUnilateral: Bool? = nil,
        unilateralMode: UnilateralMode? = nil,
        unilateralSideOrder: UnilateralSideOrder? = nil,
        unilateralIntensityMode: UnilateralIntensityMode? = nil,
        restBetweenSidesSeconds: Int? = nil,
        isCalibratorAmrap: Bool? = nil,
        goal1RM: Double? = nil,
        goalPr: PrReference? = nil,
        calculated1RM: Double? = nil,
        damageProfile: DamageProfile? = nil,
        isCompetitionLift: Bool? = nil,
        setupCues: [String]? = nil,
        executionCues: [String]? = nil,
        contextProfilesV3: [WorkoutContextProfile]? = nil,
        defaultContextProfileIdV3: String? = nil,
        mobilitySeries: [MobilitySeries]? = nil,
        timeStrategy: TimeStrategy? = nil,
        targetDurationMinutes: Int? = nil
    ) -> Exercise {
        Exercise(
            id: id ?? self.id,
            name: name ?? self.name,
            exerciseDbId: exerciseDbId ?? self.exerciseDbId,
            exerciseId: exerciseId ?? self.exerciseId,
            canonicalExerciseId: canonicalExerciseId ?? self.canonicalExerciseId,
            exerciseFamilyId: exerciseFamilyId ?? self.exerciseFamilyId,
            relativeToCanonicalExerciseId: relativeToCanonicalExerciseId ?? self.relativeToCanonicalExerciseId,
            relationshipType: relationshipType ?? self.relationshipType,
            relationshipNotes: relationshipNotes ?? self.relationshipNotes,
            sets: sets ?? self.sets,
            warmupSets: warmupSets ?? self.warmupSets,
            restTime: restTime ?? self.restTime,
            isFavorite: isFavorite ?? self.isFavorite,
            trainingMode: trainingMode ?? self.trainingMode,
            customUnit: customUnit ?? self.customUnit,
            reference1RM: reference1RM ?? self.reference1RM,
            targetSessionGoal: targetSessionGoal ?? self.targetSessionGoal,
            isStarTarget: isStarTarget ?? self.isStarTarget,
            trackHeartRate: trackHeartRate ?? self.trackHeartRate,
            trackRom: trackRom ?? self.trackRom,
            setupDetails: setupDetails ?? self.setupDetails,
            supersetId: supersetId ?? self.supersetId,
            supersetRestBetween: supersetRestBetween ?? self.supersetRestBetween,
            supersetRestAfter: supersetRestAfter ?? self.supersetRestAfter,
            supersetGroupRef: supersetGroupRef ?? self.supersetGroupRef,
            variantName: variantName ?? self.variantName,
            selectedExecutionOption: selectedExecutionOption ?? self.selectedExecutionOption,
            selectedMovementPattern: selectedMovementPattern ?? self.selectedMovementPattern,
            prFor1RM: prFor1RM ?? self.prFor1RM,
            consolidatedWeight: consolidatedWeight ?? self.consolidatedWeight,
            brandEquivalencies: brandEquivalencies ?? self.brandEquivalencies,
            isUnilateral: isUnilateral ?? self.isUnilateral,
            unilateralMode: unilateralMode ?? self.unilateralMode,
            unilateralSideOrder: unilateralSideOrder ?? self.unilateralSideOrder,
            unilateralIntensityMode: unilateralIntensityMode ?? self.unilateralIntensityMode,
            restBetweenSidesSeconds: restBetweenSidesSeconds ?? self.restBetweenSidesSeconds,
            isCalibratorAmrap: isCalibratorAmrap ?? self.isCalibratorAmrap,
            goal1RM: goal1RM ?? self.goal1RM,
            goalPr: goalPr ?? self.goalPr,
            calculated1RM: calculated1RM ?? self.calculated1RM,
            damageProfile: damageProfile ?? self.damageProfile,
            isCompetitionLift: isCompetitionLift ?? self.isCompetitionLift,
            setupCues: setupCues ?? self.setupCues,
            executionCues: executionCues ?? self.executionCues,
            contextProfilesV3: contextProfilesV3 ?? self.contextProfilesV3,
            defaultContextProfileIdV3: defaultContextProfileIdV3 ?? self.defaultContextProfileIdV3,
            mobilitySeries: mobilitySeries ?? self.mobilitySeries,
            timeStrategy: timeStrategy ?? self.timeStrategy,
            targetDurationMinutes: targetDurationMinutes ?? self.targetDurationMinutes
        )
    }

    func replacedWithCatalogExercise(_ info: ExerciseMuscleInfo) -> Exercise {
        let setup = info.setupDetails.map {
            ExerciseSetupDetails(
                setupId: nil,
                notes: nil,
                seatPosition: $0.seatPosition,
                pinPosition: $0.pinPosition,
                equipmentNotes: $0.equipmentNotes,
                barWeightKg: nil
            )
        }
        
        let canonicalId = info.id.lowercased().folding(options: .diacriticInsensitive, locale: .current)
        
        let equipment = info.equipment?.lowercased() ?? ""
        let nameLower = info.name.lowercased()
        let defaultLoadMode: LoadModeV2
        if equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") {
            defaultLoadMode = .BODYWEIGHT
        } else if equipment.contains("asist") || nameLower.contains("asist") || equipment.contains("assisted") || nameLower.contains("assisted") {
            defaultLoadMode = .ASSISTED
        } else {
            defaultLoadMode = .LOAD
        }
        
        let newSets = sets.map { set -> ExerciseSet in
            set.copy(
                intensityMode: nil,
                targetPercentageRM: nil,
                weight: nil,
                completedReps: nil,
                completedDuration: nil,
                completedRPE: nil,
                completedRIR: nil,
                machineBrand: nil,
                dropSets: [],
                restPauses: [],
                technicalWeight: nil,
                consolidatedWeight: nil,
                attemptResult: nil,
                judgingLights: [],
                technicalQuality: nil,
                discomfortIds: [],
                refereeNotes: nil,
                loadModeV2: defaultLoadMode,
                unitModeV2: set.targetDuration != nil ? .TIME : .REPS,
                plannedTargetV2: nil,
                tagId: nil,
                setupId: nil,
                contextKeyV2: nil,
                contextProfileIdV3: nil,
                defaultTagIdV3: nil,
                defaultSetupProfileIdV3: nil,
                leftTarget: set.leftTarget.map { UnilateralTarget(targetReps: $0.targetReps, targetWeight: nil) },
                rightTarget: set.rightTarget.map { UnilateralTarget(targetReps: $0.targetReps, targetWeight: nil) },
                plannedIntensityTechniques: []
            )
        }
        
        return copy(
            name: info.name,
            exerciseDbId: info.id,
            exerciseId: info.id,
            canonicalExerciseId: canonicalId,
            exerciseFamilyId: canonicalId,
            relativeToCanonicalExerciseId: nil,
            relationshipType: nil,
            relationshipNotes: nil,
            sets: newSets,
            warmupSets: [],
            trainingMode: .REPS,
            reference1RM: nil,
            targetSessionGoal: nil,
            isStarTarget: false,
            setupDetails: setup,
            variantName: nil,
            selectedExecutionOption: info.executionOptions?.first,
            selectedMovementPattern: info.movementPattern,
            prFor1RM: nil,
            consolidatedWeight: nil,
            brandEquivalencies: [],
            goal1RM: nil,
            calculated1RM: nil,
            setupCues: info.setupCues,
            executionCues: info.executionCues,
            contextProfilesV3: [],
            defaultContextProfileIdV3: nil,
            mobilitySeries: [],
            timeStrategy: nil,
            targetDurationMinutes: nil
        )
    }
}

extension ExerciseSet {
    public func copy(
        id: String? = nil,
        targetReps: Int? = nil,
        targetDuration: Int? = nil,
        targetRPE: Double? = nil,
        targetRIR: Int? = nil,
        intensityMode: IntensityMode? = nil,
        targetPercentageRM: Double? = nil,
        weight: Double? = nil,
        advancedTechnique: String? = nil,
        completedReps: Int? = nil,
        completedDuration: Int? = nil,
        completedRPE: Double? = nil,
        completedRIR: Int? = nil,
        isFailure: Bool? = nil,
        isAmrap: Bool? = nil,
        isCalibrator: Bool? = nil,
        isIneffective: Bool? = nil,
        isPartial: Bool? = nil,
        partialReps: Int? = nil,
        isDropSet: Bool? = nil,
        isRestPause: Bool? = nil,
        machineBrand: String? = nil,
        isChangeOfPlans: Bool? = nil,
        dropSets: [DropSetData]? = nil,
        restPauses: [RestPauseData]? = nil,
        performanceMode: PerformanceMode? = nil,
        technicalWeight: Double? = nil,
        consolidatedWeight: Double? = nil,
        attemptResult: AttemptResult? = nil,
        judgingLights: [Bool?]? = nil,
        technicalQuality: Int? = nil,
        discomfortIds: [String]? = nil,
        refereeNotes: String? = nil,
        loadModeV2: LoadModeV2? = nil,
        unitModeV2: UnitModeV2? = nil,
        plannedTargetV2: Double? = nil,
        tagId: String? = nil,
        setupId: String? = nil,
        contextKeyV2: String? = nil,
        contextProfileIdV3: String? = nil,
        defaultTagIdV3: String? = nil,
        defaultSetupProfileIdV3: String? = nil,
        timeProgressionStrategyV3: TimeProgressionStrategyV3? = nil,
        leftTarget: UnilateralTarget? = nil,
        rightTarget: UnilateralTarget? = nil,
        restBetweenSides: Int? = nil,
        plannedIntensityTechniques: [PlannedTechnique]? = nil
    ) -> ExerciseSet {
        ExerciseSet(
            id: id ?? self.id,
            targetReps: targetReps ?? self.targetReps,
            targetDuration: targetDuration ?? self.targetDuration,
            targetRPE: targetRPE ?? self.targetRPE,
            targetRIR: targetRIR ?? self.targetRIR,
            intensityMode: intensityMode ?? self.intensityMode,
            targetPercentageRM: targetPercentageRM ?? self.targetPercentageRM,
            weight: weight ?? self.weight,
            advancedTechnique: advancedTechnique ?? self.advancedTechnique,
            completedReps: completedReps ?? self.completedReps,
            completedDuration: completedDuration ?? self.completedDuration,
            completedRPE: completedRPE ?? self.completedRPE,
            completedRIR: completedRIR ?? self.completedRIR,
            isFailure: isFailure ?? self.isFailure,
            isAmrap: isAmrap ?? self.isAmrap,
            isCalibrator: isCalibrator ?? self.isCalibrator,
            isIneffective: isIneffective ?? self.isIneffective,
            isPartial: isPartial ?? self.isPartial,
            partialReps: partialReps ?? self.partialReps,
            isDropSet: isDropSet ?? self.isDropSet,
            isRestPause: isRestPause ?? self.isRestPause,
            machineBrand: machineBrand ?? self.machineBrand,
            isChangeOfPlans: isChangeOfPlans ?? self.isChangeOfPlans,
            dropSets: dropSets ?? self.dropSets,
            restPauses: restPauses ?? self.restPauses,
            performanceMode: performanceMode ?? self.performanceMode,
            technicalWeight: technicalWeight ?? self.technicalWeight,
            consolidatedWeight: consolidatedWeight ?? self.consolidatedWeight,
            attemptResult: attemptResult ?? self.attemptResult,
            judgingLights: judgingLights ?? self.judgingLights,
            technicalQuality: technicalQuality ?? self.technicalQuality,
            discomfortIds: discomfortIds ?? self.discomfortIds,
            refereeNotes: refereeNotes ?? self.refereeNotes,
            loadModeV2: loadModeV2 ?? self.loadModeV2,
            unitModeV2: unitModeV2 ?? self.unitModeV2,
            plannedTargetV2: plannedTargetV2 ?? self.plannedTargetV2,
            tagId: tagId ?? self.tagId,
            setupId: setupId ?? self.setupId,
            contextKeyV2: contextKeyV2 ?? self.contextKeyV2,
            contextProfileIdV3: contextProfileIdV3 ?? self.contextProfileIdV3,
            defaultTagIdV3: defaultTagIdV3 ?? self.defaultTagIdV3,
            defaultSetupProfileIdV3: defaultSetupProfileIdV3 ?? self.defaultSetupProfileIdV3,
            timeProgressionStrategyV3: timeProgressionStrategyV3 ?? self.timeProgressionStrategyV3,
            leftTarget: leftTarget ?? self.leftTarget,
            rightTarget: rightTarget ?? self.rightTarget,
            restBetweenSides: restBetweenSides ?? self.restBetweenSides,
            plannedIntensityTechniques: plannedIntensityTechniques ?? self.plannedIntensityTechniques
        )
    }
}

extension WorkoutTag {
    func copy(
        name: String? = nil,
        subTags: [WorkoutSubTag]? = nil
    ) -> WorkoutTag {
        WorkoutTag(
            id: self.id,
            name: name ?? self.name,
            exerciseKey: self.exerciseKey,
            subTags: subTags ?? self.subTags,
            createdAtIso: self.createdAtIso,
            lastUsedAtIso: self.lastUsedAtIso,
            usageCount: self.usageCount
        )
    }
}

// ─── Calculations & Helper Functions ──────────────────────────────────────────

func calculateHybrid1RM(_ weight: Double, reps: Int, isAmrap: Bool = false) -> Double {
    if weight <= 0 || reps <= 0 { return 0.0 }
    if reps == 1 { return weight }
    let r = min(reps, 50)
    let e1rm: Double
    if r <= 10 {
        e1rm = weight * (36.0 / (37.0 - Double(r)))
    } else if r <= 20 {
        e1rm = weight * (1.0 + Double(r) / 30.0)
    } else {
        e1rm = weight * (1.0 + 20.0 / 30.0) * pow(1.0 + Double(r - 20) / 80.0, 0.9)
    }
    let adjusted = (isAmrap && reps > 3) ? e1rm * 1.025 : e1rm
    return (adjusted * 10.0).rounded() / 10.0
}

func calculateSuggestedLoad(_ exercise: Exercise, set: ExerciseSet) -> Double? {
    return calculateSuggestedLoad(exercise, set)
}


// ─── SessionPart copy ────────────────────────────────────────────────────────

extension SessionPart {
    func copy(
        id: String? = nil,
        name: String? = nil,
        exercises: [Exercise]? = nil,
        color: String? = nil,
        targetDurationMinutes: Int? = nil
    ) -> SessionPart {
        SessionPart(
            id: id ?? self.id,
            name: name ?? self.name,
            exercises: exercises ?? self.exercises,
            color: color ?? self.color,
            targetDurationMinutes: targetDurationMinutes ?? self.targetDurationMinutes
        )
    }
}

// ─── OngoingWorkoutState copy ──────────────────────────────────────────────────

extension OngoingWorkoutState {
    func copy(
        programId: String? = nil,
        session: Session? = nil,
        isPaused: Bool? = nil,
        startTime: Int64? = nil,
        activeExerciseId: String? = nil,
        activeSetId: String? = nil,
        activeSetIndex: Int? = nil,
        activeExerciseIndex: Int? = nil,
        activeStepKey: String? = nil,
        activeMode: WeekVariant? = nil,
        completedSets: [String: CompletedSet]? = nil,
        dynamicWeights: [String: Double]? = nil,
        loadSuggestionReasons: [String: String]? = nil,
        setDrafts: [String: WorkoutSetDraft]? = nil,
        manualLoadOverrides: [String: Double]? = nil,
        editingSetKey: String? = nil,
        isCarpeDiem: Bool? = nil,
        macroIndex: Int? = nil,
        mesoIndex: Int? = nil,
        weekId: String? = nil,
        exerciseTags: [String: String]? = nil,
        activeTags: [String: [String]]? = nil,
        activeSubTags: [String: [String]]? = nil,
        userCreatedTags: [String: [WorkoutTag]]? = nil,
        contextProfilesV3: [String: WorkoutContextProfile]? = nil,
        activeContextProfileByExerciseId: [String: String]? = nil,
        skippedExerciseIds: Set<String>? = nil,
        warmupCompletedExerciseIds: Set<String>? = nil,
        mobilityCompletedExerciseIds: Set<String>? = nil,
        readinessNeuralOverride: Int? = nil,
        readinessMuscularOverride: Int? = nil,
        readinessSpinalOverride: Int? = nil,
        readinessMuscleOverrides: [String: Int]? = nil,
        restModalState: WorkoutRestModalState? = nil,
        persistedLoadModeBySet: [String: LoadModeV2]? = nil,
        persistedLoadModeByExercise: [String: LoadModeV2]? = nil,
        customTargetDurationMinutes: Int? = nil
    ) -> OngoingWorkoutState {
        OngoingWorkoutState(
            programId: programId ?? self.programId,
            session: session ?? self.session,
            isPaused: isPaused ?? self.isPaused,
            startTime: startTime ?? self.startTime,
            activeExerciseId: activeExerciseId ?? self.activeExerciseId,
            activeSetId: activeSetId ?? self.activeSetId,
            activeSetIndex: activeSetIndex ?? self.activeSetIndex,
            activeExerciseIndex: activeExerciseIndex ?? self.activeExerciseIndex,
            activeStepKey: activeStepKey ?? self.activeStepKey,
            activeMode: activeMode ?? self.activeMode,
            completedSets: completedSets ?? self.completedSets,
            dynamicWeights: dynamicWeights ?? self.dynamicWeights,
            loadSuggestionReasons: loadSuggestionReasons ?? self.loadSuggestionReasons,
            setDrafts: setDrafts ?? self.setDrafts,
            manualLoadOverrides: manualLoadOverrides ?? self.manualLoadOverrides,
            editingSetKey: editingSetKey ?? self.editingSetKey,
            isCarpeDiem: isCarpeDiem ?? self.isCarpeDiem,
            macroIndex: macroIndex ?? self.macroIndex,
            mesoIndex: mesoIndex ?? self.mesoIndex,
            weekId: weekId ?? self.weekId,
            exerciseTags: exerciseTags ?? self.exerciseTags,
            activeTags: activeTags ?? self.activeTags,
            activeSubTags: activeSubTags ?? self.activeSubTags,
            userCreatedTags: userCreatedTags ?? self.userCreatedTags,
            contextProfilesV3: contextProfilesV3 ?? self.contextProfilesV3,
            activeContextProfileByExerciseId: activeContextProfileByExerciseId ?? self.activeContextProfileByExerciseId,
            skippedExerciseIds: skippedExerciseIds ?? self.skippedExerciseIds,
            warmupCompletedExerciseIds: warmupCompletedExerciseIds ?? self.warmupCompletedExerciseIds,
            mobilityCompletedExerciseIds: mobilityCompletedExerciseIds ?? self.mobilityCompletedExerciseIds,
            readinessNeuralOverride: readinessNeuralOverride ?? self.readinessNeuralOverride,
            readinessMuscularOverride: readinessMuscularOverride ?? self.readinessMuscularOverride,
            readinessSpinalOverride: readinessSpinalOverride ?? self.readinessSpinalOverride,
            readinessMuscleOverrides: readinessMuscleOverrides ?? self.readinessMuscleOverrides,
            restModalState: restModalState ?? self.restModalState,
            persistedLoadModeBySet: persistedLoadModeBySet ?? self.persistedLoadModeBySet,
            persistedLoadModeByExercise: persistedLoadModeByExercise ?? self.persistedLoadModeByExercise,
            customTargetDurationMinutes: customTargetDurationMinutes ?? self.customTargetDurationMinutes
        )
    }
}

// ─── HomologatedPerformanceResult Extension ──────────────────────────────────

extension HomologatedPerformanceResult {
    func copy(
        ermRangeMin: Double? = nil,
        ermRangeMax: Double? = nil
    ) -> HomologatedPerformanceResult {
        HomologatedPerformanceResult(
            contextKey: self.contextKey,
            globalKey: self.globalKey,
            loadMode: self.loadMode,
            unitMode: self.unitMode,
            plannedTarget: self.plannedTarget,
            actualValue: self.actualValue,
            actualIntensity: self.actualIntensity,
            debt: self.debt,
            failedSet: self.failedSet,
            reachedFailure: self.reachedFailure,
            amrapOverride: self.amrapOverride,
            techniques: self.techniques,
            metricType: self.metricType,
            metricValue: self.metricValue,
            estimatedRm: self.estimatedRm,
            trm: self.trm,
            localPerformanceIndex: self.localPerformanceIndex,
            globalPerformanceIndex: self.globalPerformanceIndex,
            contextPercentile: self.contextPercentile,
            globalPercentile: self.globalPercentile,
            contextEwma: self.contextEwma,
            contextStdDev: self.contextStdDev,
            globalEwma: self.globalEwma,
            globalStdDev: self.globalStdDev,
            isContextPr: self.isContextPr,
            isGlobalPr: self.isGlobalPr,
            historyColor: self.historyColor,
            difficultySignal: self.difficultySignal,
            suggestedNextLoad: self.suggestedNextLoad,
            suggestedTargetSeconds: self.suggestedTargetSeconds,
            suggestionReason: self.suggestionReason,
            augeEquivalentLoad: self.augeEquivalentLoad,
            augeEquivalentReps: self.augeEquivalentReps,
            ermRangeMin: ermRangeMin ?? self.ermRangeMin,
            ermRangeMax: ermRangeMax ?? self.ermRangeMax,
            suggestedLoadMode: self.suggestedLoadMode
        )
    }
}

// ─── Array distinct Extension ──────────────────────────────────────────────────

extension Array where Element: Hashable {
    func distinct() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

// ─── Session sanitizeSessionLoadModes Extension ────────────────────────────────

extension Session {
    func sanitizeSessionLoadModes() -> Session {
        let transform: (Exercise) -> Exercise = { exercise in
            let defaultMode = exercise.inferDefaultLoadModeFromCatalog()
            return exercise.copy(sets: exercise.sets.map { set in
                set.loadModeV2 != nil ? set : set.copy(loadModeV2: defaultMode)
            })
        }
        return self.copy(
            exercises: self.exercises.map(transform),
            parts: self.parts.map { $0.copy(exercises: $0.exercises.map(transform)) },
            sessionB: self.sessionB?.sanitizeSessionLoadModes(),
            sessionC: self.sessionC?.sanitizeSessionLoadModes(),
            sessionD: self.sessionD?.sanitizeSessionLoadModes()
        )
    }
}

extension Exercise {
    func inferDefaultLoadModeFromCatalog() -> LoadModeV2 {
        var resolvedInfo: ExerciseMuscleInfo? = nil
        let lowerId = self.id.lowercased()
        if let info = catalogExerciseIndex()[lowerId] {
            resolvedInfo = info
        } else if let dbId = self.exerciseDbId?.lowercased(), let info = catalogExerciseIndex()[dbId] {
            resolvedInfo = info
        } else if let exId = self.exerciseId?.lowercased(), let info = catalogExerciseIndex()[exId] {
            resolvedInfo = info
        }
        guard let info = resolvedInfo else { return .LOAD }
        let equipment = info.equipment?.lowercased() ?? ""
        let name = self.name.lowercased()
        if equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") { return .BODYWEIGHT }
        if equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") { return .ASSISTED }
        return .LOAD
    }
}

// ─── calculateGeneralizedCapacity ─────────────────────────────────────────────

func calculateGeneralizedCapacity(_ load: Double, _ metric: Double) -> Double {
    if load <= 0 || metric <= 0 { return 0.0 }
    if metric <= 1.0 { return load }
    let normalizedMetric = min(300.0, metric)
    let capacity: Double
    if normalizedMetric <= 10.0 {
        capacity = load * (36.0 / (37.0 - normalizedMetric))
    } else if normalizedMetric <= 20.0 {
        capacity = load * (1.0 + normalizedMetric / 30.0)
    } else {
        capacity = load * (1.0 + 20.0 / 30.0) * pow(1.0 + (normalizedMetric - 20.0) / 80.0, 0.9)
    }
    return round(capacity * 10.0) / 10.0
}
