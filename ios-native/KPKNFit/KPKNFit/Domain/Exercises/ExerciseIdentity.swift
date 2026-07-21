import Foundation

private let exerciseIdentityStripRegex = try! NSRegularExpression(pattern: "\\p{Mn}+")
private let exerciseIdentitySeparatorRegex = try! NSRegularExpression(pattern: "[^\\p{L}\\p{Nd}]+")

func normalizeExerciseIdentityToken(value: String) -> String {
    if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "" }
    var text = value.trimmingCharacters(in: .whitespacesAndNewlines)
    text = text.folding(options: .caseInsensitive, locale: .current)
    let nsRange = NSRange(text.startIndex..., in: text)
    text = exerciseIdentityStripRegex.stringByReplacingMatches(in: text, range: nsRange, withTemplate: "")
    text = text.lowercased()
    let nsRange2 = NSRange(text.startIndex..., in: text)
    text = exerciseIdentitySeparatorRegex.stringByReplacingMatches(in: text, range: nsRange2, withTemplate: " ")
    return text.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func normalizeCanonicalId(value: String?) -> String? {
    guard let v = value else { return nil }
    let trimmed = v.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    return trimmed.isEmpty ? nil : trimmed
}

func resolveCanonicalExerciseId(
    explicitCanonicalId: String?,
    exerciseDbId: String?,
    exerciseId: String?,
    exerciseName: String,
    fallbackId: String? = nil
) -> String {
    if let id = normalizeCanonicalId(value: explicitCanonicalId) { return id }

    if let id = resolveExerciseId(exerciseDbId ?? exerciseId) { return id }

    if let id = normalizeCanonicalId(value: exerciseDbId) { return id }

    let normalizedName = normalizeExerciseIdentityToken(value: exerciseName)
    if !normalizedName.isEmpty {
        return "custom:\(normalizedName)"
    }

    if let id = normalizeCanonicalId(value: exerciseId) { return "legacy:\(id)" }
    if let id = normalizeCanonicalId(value: fallbackId) { return "local:\(id)" }
    return "unknown"
}

private func normalizeRelationAnchorId(value: String?) -> String? {
    guard let v = value else { return nil }
    let trimmed = v.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    return trimmed.isEmpty ? nil : trimmed
}

extension Exercise {
    func resolvedCanonicalExerciseId() -> String {
        resolveCanonicalExerciseId(
            explicitCanonicalId: canonicalExerciseId,
            exerciseDbId: exerciseDbId,
            exerciseId: exerciseId,
            exerciseName: name,
            fallbackId: id
        )
    }

    func resolvedExerciseFamilyId() -> String {
        normalizeRelationAnchorId(value: exerciseFamilyId) ?? resolvedCanonicalExerciseId()
    }

    func resolvedRelationAnchorId() -> String {
        normalizeRelationAnchorId(value: relativeToCanonicalExerciseId) ?? resolvedCanonicalExerciseId()
    }

    func analyticsExerciseKey() -> String { "exercise:\(resolvedCanonicalExerciseId())" }

    func analyticsAnchorKey() -> String { "anchor:\(resolvedRelationAnchorId())" }

    func normalizedIdentityFields() -> Exercise {
        let canonicalId = resolvedCanonicalExerciseId()
        let relationAnchor = normalizeRelationAnchorId(value: relativeToCanonicalExerciseId)
            .flatMap { $0 != canonicalId ? $0 : nil }
        let notes = relationshipNotes?.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNotes = (notes != nil && !notes!.isEmpty) ? notes! : nil
        return copy(
            canonicalExerciseId: canonicalId,
            exerciseFamilyId: normalizeRelationAnchorId(value: exerciseFamilyId) ?? canonicalId,
            relativeToCanonicalExerciseId: relationAnchor,
            relationshipNotes: cleanNotes
        )
    }

    func replacedWithCatalogExercise(info: ExerciseMuscleInfo) -> Exercise {
        let setup = info.setupDetails.map {
            ExerciseSetupDetails(seatPosition: $0.seatPosition, pinPosition: $0.pinPosition, equipmentNotes: $0.equipmentNotes)
        }
        let canonicalId = resolveCanonicalExerciseId(
            explicitCanonicalId: info.id,
            exerciseDbId: info.id,
            exerciseId: info.id,
            exerciseName: info.name,
            fallbackId: id
        )
        let defaultLoadMode = defaultReplacementLoadMode(info: info)
        return copy(
            name: info.name,
            exerciseDbId: info.id,
            exerciseId: info.id,
            canonicalExerciseId: canonicalId,
            exerciseFamilyId: canonicalId,
            relativeToCanonicalExerciseId: nil,
            relationshipType: nil,
            relationshipNotes: nil,
            sets: sets.map { $0.resetForCatalogReplacement(defaultLoadMode: defaultLoadMode) },
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
            setupCues: info.setupCues ?? [],
            executionCues: info.executionCues ?? [],
            contextProfilesV3: [],
            defaultContextProfileIdV3: nil
        ).normalizedIdentityFields()
    }
}

extension CompletedExercise {
    func resolvedCanonicalExerciseId() -> String {
        resolveCanonicalExerciseId(
            explicitCanonicalId: canonicalExerciseId,
            exerciseDbId: exerciseDbId,
            exerciseId: exerciseId,
            exerciseName: exerciseName,
            fallbackId: exerciseId
        )
    }

    func resolvedRelationAnchorId() -> String {
        normalizeRelationAnchorId(value: relativeToCanonicalExerciseId) ?? resolvedCanonicalExerciseId()
    }

    func analyticsExerciseKey() -> String { "exercise:\(resolvedCanonicalExerciseId())" }

    func analyticsAnchorKey() -> String { "anchor:\(resolvedRelationAnchorId())" }

    func normalizedIdentityFields() -> CompletedExercise {
        let canonicalId = resolvedCanonicalExerciseId()
        let relationAnchor = normalizeRelationAnchorId(value: relativeToCanonicalExerciseId)
            .flatMap { $0 != canonicalId ? $0 : nil }
        return copy(
            canonicalExerciseId: canonicalId,
            relativeToCanonicalExerciseId: relationAnchor
        )
    }
}

extension ExerciseDiscomfortReport {
    func normalizedIdentityFields() -> ExerciseDiscomfortReport {
        let canonicalId = resolveCanonicalExerciseId(
            explicitCanonicalId: canonicalExerciseId,
            exerciseDbId: exerciseDbId,
            exerciseId: exerciseId,
            exerciseName: exerciseName,
            fallbackId: exerciseId
        )
        return copy(canonicalExerciseId: canonicalId)
    }
}

extension Session {
    func normalizedIdentityFields() -> Session {
        copy(
            exercises: exercises.map { $0.normalizedIdentityFields() },
            parts: parts.map { part in
                SessionPart(
                    id: part.id, name: part.name,
                    exercises: part.exercises.map { $0.normalizedIdentityFields() },
                    color: part.color
                )
            },
            sessionB: sessionB?.normalizedIdentityFields(),
            sessionC: sessionC?.normalizedIdentityFields(),
            sessionD: sessionD?.normalizedIdentityFields(),
            trainingBackup: trainingBackup.map { backup in
                backup.copy(
                    exercises: backup.exercises.map { $0.normalizedIdentityFields() },
                    parts: backup.parts.map { bp in
                        SessionPart(id: bp.id, name: bp.name, exercises: bp.exercises.map { $0.normalizedIdentityFields() }, color: bp.color)
                    }
                )
            }
        )
    }
}

extension Program {
    func normalizedIdentityFields() -> Program {
        copy(
            macrocycles: macrocycles.map { macro in
                macro.copy(
                    blocks: macro.blocks.map { block in
                        block.copy(
                            mesocycles: block.mesocycles.map { meso in
                                meso.copy(
                                    weeks: meso.weeks.map { week in
                                        week.copy(
                                            sessions: week.sessions.map { $0.normalizedIdentityFields() }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}

extension WorkoutLog {
    func normalizedIdentityFields() -> WorkoutLog {
        copy(
            completedExercises: completedExercises.map { $0.normalizedIdentityFields() },
            postExerciseReports: postExerciseReports.map { $0.normalizedIdentityFields() }
        )
    }
}

extension OngoingWorkoutState {
    func normalizedIdentityFields() -> OngoingWorkoutState {
        copy(session: session.normalizedIdentityFields())
    }
}

extension ExerciseRelationshipType {
    func displayLabel() -> String {
        switch self {
        case .VARIATION: return "Variacion"
        case .ASSISTANCE: return "Asistencia"
        case .OVERLOAD: return "Sobrecarga"
        case .TECHNIQUE: return "Tecnica"
        }
    }
}

private func defaultReplacementLoadMode(info: ExerciseMuscleInfo) -> LoadModeV2 {
    let equipment = info.equipment.map { normalizeExerciseIdentityToken(value: $0) } ?? ""
    let name = normalizeExerciseIdentityToken(value: info.name)
    if equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") {
        return .BODYWEIGHT
    }
    if equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") {
        return .ASSISTED
    }
    return .LOAD
}

extension ExerciseSet {
    func resetForCatalogReplacement(defaultLoadMode: LoadModeV2) -> ExerciseSet {
        copy(
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
            unitModeV2: targetDuration != nil ? .TIME : .REPS,
            plannedTargetV2: nil,
            tagId: nil,
            setupId: nil,
            contextKeyV2: nil,
            contextProfileIdV3: nil,
            defaultTagIdV3: nil,
            defaultSetupProfileIdV3: nil,
            leftTarget: leftTarget.map { $0.copy(resetWeight: true) },
            rightTarget: rightTarget.map { $0.copy(resetWeight: true) },
            plannedIntensityTechniques: []
        )
    }
}

// ─── Extensions for copy method on Exercise Identity structs ─────────────────

extension CompletedExercise {
    public func copy(
        canonicalExerciseId: String? = nil,
        relativeToCanonicalExerciseId: String?? = nil
    ) -> CompletedExercise {
        CompletedExercise(
            exerciseId: exerciseId,
            exerciseName: exerciseName,
            exerciseDbId: exerciseDbId,
            canonicalExerciseId: canonicalExerciseId ?? self.canonicalExerciseId,
            relativeToCanonicalExerciseId: (relativeToCanonicalExerciseId != nil) ? relativeToCanonicalExerciseId! : self.relativeToCanonicalExerciseId,
            sets: sets,
            restTime: restTime,
            supersetId: supersetId,
            supersetExerciseCount: supersetExerciseCount,
            supersetRounds: supersetRounds,
            supersetRestBetween: supersetRestBetween,
            supersetRestAfter: supersetRestAfter
        )
    }
}

extension ExerciseDiscomfortReport {
    public func copy(
        canonicalExerciseId: String? = nil
    ) -> ExerciseDiscomfortReport {
        ExerciseDiscomfortReport(
            exerciseId: exerciseId,
            exerciseDbId: exerciseDbId,
            canonicalExerciseId: canonicalExerciseId ?? self.canonicalExerciseId,
            exerciseName: exerciseName,
            technicalQuality: technicalQuality,
            discomfortIds: discomfortIds,
            notes: notes,
            perceivedIntensityRpe: perceivedIntensityRpe,
            perceivedFailure: perceivedFailure
        )
    }
}

extension Session {
    public func copy(
        exercises: [Exercise]? = nil,
        parts: [SessionPart]? = nil,
        sessionB: Session?? = nil,
        sessionC: Session?? = nil,
        sessionD: Session?? = nil,
        trainingBackup: TrainingBackup?? = nil
    ) -> Session {
        Session(
            id: id,
            name: name,
            description: description,
            exercises: exercises ?? self.exercises,
            warmup: warmup,
            parts: parts ?? self.parts,
            background: background,
            coverStyle: coverStyle,
            dayOfWeek: dayOfWeek,
            scheduleLabel: scheduleLabel,
            assignedDays: assignedDays,
            sessionB: (sessionB != nil) ? sessionB! : self.sessionB,
            sessionC: (sessionC != nil) ? sessionC! : self.sessionC,
            sessionD: (sessionD != nil) ? sessionD! : self.sessionD,
            isMeetDay: isMeetDay,
            isCompetitionSession: isCompetitionSession,
            isMainSession: isMainSession,
            focus: focus,
            microProgram: microProgram,
            meetBodyweight: meetBodyweight,
            meetResults: meetResults,
            competitionDetails: competitionDetails,
            competitionRecordId: competitionRecordId,
            competitionKeyDateId: competitionKeyDateId,
            competitionSportType: competitionSportType,
            competitionRecordMode: competitionRecordMode,
            trainingBackup: (trainingBackup != nil) ? trainingBackup! : self.trainingBackup,
            supersetGroups: supersetGroups,
            lastModifiedAtMs: lastModifiedAtMs,
            targetDurationMinutes: targetDurationMinutes,
            volumeAdvances: volumeAdvances
        )
    }
}

extension TrainingBackup {
    public func copy(
        exercises: [Exercise]? = nil,
        parts: [SessionPart]? = nil
    ) -> TrainingBackup {
        TrainingBackup(
            exercises: exercises ?? self.exercises,
            parts: parts ?? self.parts,
            warmup: warmup,
            savedAtMs: savedAtMs
        )
    }
}

extension ProgramWeek {
    public func copy(
        sessions: [Session]? = nil
    ) -> ProgramWeek {
        ProgramWeek(
            id: id,
            name: name,
            description: description,
            sessions: sessions ?? self.sessions,
            variant: variant,
            isLoopWeek: isLoopWeek,
            loopId: loopId,
            startDate: startDate,
            endDate: endDate,
            trainingDayDates: trainingDayDates
        )
    }
}

extension WorkoutLog {
    public func copy(
        completedExercises: [CompletedExercise]? = nil,
        postExerciseReports: [ExerciseDiscomfortReport]? = nil
    ) -> WorkoutLog {
        WorkoutLog(
            id: id,
            programId: programId,
            sessionId: sessionId,
            sessionName: sessionName,
            date: date,
            durationMinutes: durationMinutes,
            completedExercises: completedExercises ?? self.completedExercises,
            fatigueLevel: fatigueLevel,
            discomforts: discomforts,
            notes: notes,
            totalVolume: totalVolume,
            sessionStressScore: sessionStressScore,
            energySummary: energySummary,
            weekId: weekId,
            macroIndex: macroIndex,
            mesoIndex: mesoIndex,
            clarityRating: clarityRating,
            environmentTags: environmentTags,
            stillPresentDiscomfortIds: stillPresentDiscomfortIds,
            planDeviations: planDeviations,
            exerciseTags: exerciseTags,
            contextualPerformanceStateV2: contextualPerformanceStateV2,
            globalPerformanceStateV3: globalPerformanceStateV3,
            contextProfilesV3: contextProfilesV3,
            replacementDecisionsV2: replacementDecisionsV2,
            postExerciseReports: postExerciseReports ?? self.postExerciseReports,
            omittedExercises: omittedExercises,
            scheduledDate: scheduledDate,
            actualDate: actualDate,
            scheduleDeltaDays: scheduleDeltaDays
        )
    }
}

extension OngoingWorkoutState {
    public func copy(session: Session? = nil) -> OngoingWorkoutState {
        OngoingWorkoutState(
            programId: programId,
            session: session ?? self.session,
            isPaused: isPaused,
            startTime: startTime,
            activeExerciseId: activeExerciseId,
            activeSetId: activeSetId,
            activeSetIndex: activeSetIndex,
            activeExerciseIndex: activeExerciseIndex,
            activeStepKey: activeStepKey,
            activeMode: activeMode,
            completedSets: completedSets,
            dynamicWeights: dynamicWeights,
            loadSuggestionReasons: loadSuggestionReasons,
            setDrafts: setDrafts,
            manualLoadOverrides: manualLoadOverrides,
            editingSetKey: editingSetKey,
            isCarpeDiem: isCarpeDiem,
            macroIndex: macroIndex,
            mesoIndex: mesoIndex,
            weekId: weekId,
            exerciseTags: exerciseTags,
            activeTags: activeTags,
            activeSubTags: activeSubTags,
            userCreatedTags: userCreatedTags,
            contextProfilesV3: contextProfilesV3,
            activeContextProfileByExerciseId: activeContextProfileByExerciseId,
            skippedExerciseIds: skippedExerciseIds,
            warmupCompletedExerciseIds: warmupCompletedExerciseIds,
            mobilityCompletedExerciseIds: mobilityCompletedExerciseIds,
            readinessNeuralOverride: readinessNeuralOverride,
            readinessMuscularOverride: readinessMuscularOverride,
            readinessSpinalOverride: readinessSpinalOverride,
            readinessMuscleOverrides: readinessMuscleOverrides,
            restModalState: restModalState,
            persistedLoadModeBySet: persistedLoadModeBySet,
            persistedLoadModeByExercise: persistedLoadModeByExercise,
            customTargetDurationMinutes: customTargetDurationMinutes
        )
    }
}

extension UnilateralTarget {
    public func copy(
        resetWeight: Bool = false,
        weight: Double? = nil,
        targetReps: Int? = nil,
        targetDuration: Int? = nil,
        targetValue: Double? = nil,
        targetRPE: Double? = nil,
        targetRIR: Int? = nil,
        intensityMode: IntensityMode? = nil
    ) -> UnilateralTarget {
        UnilateralTarget(
            weight: resetWeight ? nil : (weight ?? self.weight),
            targetReps: targetReps ?? self.targetReps,
            targetDuration: targetDuration ?? self.targetDuration,
            targetValue: targetValue ?? self.targetValue,
            targetRPE: targetRPE ?? self.targetRPE,
            targetRIR: targetRIR ?? self.targetRIR,
            intensityMode: intensityMode ?? self.intensityMode
        )
    }
}

