import Foundation

// MARK: - SessionTemplate models (mirror data/sessions/SessionTemplateModels.kt)

public enum SessionTemplateTag: String, Codable, CaseIterable {
    case fuerza, hipertrofia, potencia, resistencia, recuperacion
    case torso, pierna, empuje, tiron, cuerpoCompleto = "CUERPO_COMPLETO"
    case pecho, espalda, hombros, brazos, gluteos, cuadriceps, isquiotibiales, gemelos, core
    case powerlifting, sentadilla, pesoMuerto = "PESO_MUERTO", banca
    case minimalista, altoVolumen = "ALTO_VOLUMEN", altaFrecuencia = "ALTA_FRECUENCIA", deportivo
}

public enum SessionTemplateSourceType: String, Codable {
    case system, user
}

public enum SessionTemplateFocusCategory: String, Codable {
    case piernas, brazos, gluteos, pecho, espalda, hombros
    case fullBody = "FULL_BODY"
    case powerlifting, minimalista, recuperacion
}

public struct SessionTemplateTagItem: Codable {
    public let name: String
    public init(name: String) { self.name = name }
}

public struct SessionTemplate: Codable {
    public let id: String
    public let sourceType: SessionTemplateSourceType
    public let name: String
    public let description: String
    public let emoji: String
    public let tags: [SessionTemplateTagItem]
    public let difficulty: String
    public let estimatedDurationMinutes: Int?
    public let exerciseCount: Int
    public let partCount: Int
    public let muscleGroupsSummary: String
    public let session: Session
    public let sortOrder: Int
    public let isArchived: Bool
    public let createdAt: String?
    public let updatedAt: String?
    public let splitIds: [String]
    public let splitDayLabels: [String]
    public let focusCategory: SessionTemplateFocusCategory?
    public let shortDescription: String
    public let weeklyVolumePolicyId: String?

    public init(
        id: String,
        sourceType: SessionTemplateSourceType,
        name: String,
        description: String,
        emoji: String = "💪",
        tags: [SessionTemplateTagItem] = [],
        difficulty: String = "INTERMEDIO",
        estimatedDurationMinutes: Int? = nil,
        exerciseCount: Int = 0,
        partCount: Int = 0,
        muscleGroupsSummary: String = "",
        session: Session,
        sortOrder: Int = 0,
        isArchived: Bool = false,
        createdAt: String? = nil,
        updatedAt: String? = nil,
        splitIds: [String] = [],
        splitDayLabels: [String] = [],
        focusCategory: SessionTemplateFocusCategory? = nil,
        shortDescription: String = "",
        weeklyVolumePolicyId: String? = nil
    ) {
        self.id = id
        self.sourceType = sourceType
        self.name = name
        self.description = description
        self.emoji = emoji
        self.tags = tags
        self.difficulty = difficulty
        self.estimatedDurationMinutes = estimatedDurationMinutes
        self.exerciseCount = exerciseCount
        self.partCount = partCount
        self.muscleGroupsSummary = muscleGroupsSummary
        self.session = session
        self.sortOrder = sortOrder
        self.isArchived = isArchived
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.splitIds = splitIds
        self.splitDayLabels = splitDayLabels
        self.focusCategory = focusCategory
        self.shortDescription = shortDescription
        self.weeklyVolumePolicyId = weeklyVolumePolicyId
    }
}

// Redundant WarmupExercise structure removed (defined in Session.swift)


// MARK: - SessionTemplateEngine

/**
 Pure, stateless engine for applying session templates.

 Key guarantee: every internal ID (Session, SessionPart, Exercise, ExerciseSet,
 WarmupSetDefinition, WarmupExercise) is regenerated with a fresh UUID so that
 applied sessions never share IDs with existing program data, workout logs, or
 other applied templates.

 Superset links *within* the cloned content are preserved by remapping the
 original supersetId strings to new ones.  Cross-session superset links are
 stripped because they cannot be valid after cloning.
 */
enum SessionTemplateEngine {

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     Applies [template] to [targetSession] according to [mode].

     - [.replace]: replaces exercises, parts, warmup, name, and description.
       The session's id and background image are preserved; name and description
       come from the template.
     - [.append]: appends template parts and loose exercises at the end of the
       existing content.
     */
    static func applyTemplate(
        template: SessionTemplate,
        targetSession: Session,
        mode: SessionTemplateApplyMode
    ) -> Session {
        switch mode {
        case .replace: return applyReplace(template: template, target: targetSession)
        case .append:  return applyAppend(template: template, target: targetSession)
        }
    }

    /**
     Returns `true` when [session] contains at least one exercise (loose or
     inside a part), which is used to decide whether to prompt the user for
     a merge mode before applying a template.
     */
    static func sessionHasContent(_ session: Session) -> Bool {
        !session.exercises.isEmpty || session.parts.contains { !$0.exercises.isEmpty }
    }

    /**
     Clones the entire content of [source] – exercises, parts, and warmup –
     with fresh UUIDs.  The returned [Session] has a new id; all other identity
     and metadata fields are copied from [source].
     */
    static func cloneSessionContent(source: Session) -> Session {
        var supersetIdMap: [String: String] = [:]
        var exerciseIdMap: [String: String] = [:]

        let clonedParts = source.parts.map { part in
            part.copy(
                id: UUID().uuidString,
                exercises: part.exercises.map { cloneExercise($0, supersetIdMap: &supersetIdMap, exerciseIdMap: &exerciseIdMap) }
            )
        }

        let clonedExercises = source.exercises.map { cloneExercise($0, supersetIdMap: &supersetIdMap, exerciseIdMap: &exerciseIdMap) }

        let clonedWarmup = source.warmup.map { $0.copy(id: UUID().uuidString) }

        let clonedSupersetGroups = source.allSupersetGroups().compactMap { group -> SupersetGroup? in
            guard let newId = supersetIdMap[group.id] else { return nil }
            let copied = group.copy(
                id: newId,
                exerciseOrder: group.exerciseOrder.compactMap { exerciseIdMap[$0] }
            )
            return copied.exerciseOrder.count >= 2 ? copied : nil
        }

        return source.copy(
            id: UUID().uuidString,
            parts: clonedParts,
            exercises: clonedExercises,
            warmup: clonedWarmup,
            supersetGroups: clonedSupersetGroups
        )
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private static func applyReplace(template: SessionTemplate, target: Session) -> Session {
        let cloned = cloneSessionContent(source: template.session)
        return target.copy(
            name: cloned.name,
            description: cloned.description,
            exercises: cloned.exercises,
            parts: cloned.parts,
            warmup: cloned.warmup
        )
    }

    private static func applyAppend(template: SessionTemplate, target: Session) -> Session {
        let cloned = cloneSessionContent(source: template.session)
        return target.copy(
            name: cloned.name,
            description: cloned.description,
            parts: target.parts + cloned.parts,
            exercises: target.exercises + cloned.exercises,
            warmup: target.warmup + cloned.warmup
        )
    }

    /**
     Clones a single exercise with a fresh id and fresh set/warmupSet ids.

     [supersetIdMap] is shared across a single cloning call so that two
     exercises that were linked in the same superset remain linked in the clone,
     while all superset IDs from outside the cloned scope are stripped.
     */
    private static func cloneExercise(
        _ exercise: Exercise,
        supersetIdMap: inout [String: String],
        exerciseIdMap: inout [String: String]
    ) -> Exercise {
        let newSupersetId = exercise.supersetGroupRefOrLegacyId().map { ref -> String in
            if let existing = supersetIdMap[ref] { return existing }
            let new = UUID().uuidString
            supersetIdMap[ref] = new
            return new
        }
        let newExerciseId = UUID().uuidString
        exerciseIdMap[exercise.id] = newExerciseId
        return exercise.copy(
            id: newExerciseId,
            supersetId: newSupersetId,
            supersetGroupRef: newSupersetId,
            sets: exercise.sets.map { $0.copy(id: UUID().uuidString) },
            warmupSets: exercise.warmupSets.map { $0.copy(id: UUID().uuidString) },
            consolidatedWeight: nil,
            reference1RM: nil,
            calculated1RM: nil,
            prFor1RM: nil
        )
    }
}

// MARK: - Data-class-style copy helpers

fileprivate extension WarmupSetDefinition {
    func copy(id: String) -> WarmupSetDefinition {
        WarmupSetDefinition(
            id: id,
            weight: weight,
            reps: reps,
            durationSeconds: durationSeconds,
            notes: notes
        )
    }
}

fileprivate extension WarmupExercise {
    func copy(id: String) -> WarmupExercise {
        WarmupExercise(
            id: id,
            name: name,
            description: description,
            category: category,
            duration: duration,
            sets: sets,
            reps: reps
        )
    }
}

fileprivate extension SessionPart {
    func copy(id: String, exercises: [Exercise]) -> SessionPart {
        SessionPart(
            id: id,
            name: name,
            exercises: exercises,
            color: color,
            targetDurationMinutes: targetDurationMinutes
        )
    }
}

fileprivate extension SupersetGroup {
    func copy(id: String, exerciseOrder: [String]) -> SupersetGroup {
        SupersetGroup(
            id: id,
            exerciseOrder: exerciseOrder,
            restBetweenExercises: restBetweenExercises,
            restAfterSuperset: restAfterSuperset,
            rounds: rounds,
            visualPlacement: visualPlacement,
            roundRestBetweenExercises: roundRestBetweenExercises,
            roundRestAfterSuperset: roundRestAfterSuperset,
            isOptional: isOptional
        )
    }
}

fileprivate extension Exercise {
    func copy(
        id: String,
        supersetId: String?,
        supersetGroupRef: String?,
        sets: [ExerciseSet],
        warmupSets: [WarmupSetDefinition],
        consolidatedWeight: ConsolidatedWeight?,
        reference1RM: Double?,
        calculated1RM: Double?,
        prFor1RM: PrReference?
    ) -> Exercise {
        Exercise(
            id: id,
            name: name,
            exerciseDbId: exerciseDbId,
            exerciseId: exerciseId,
            canonicalExerciseId: canonicalExerciseId,
            exerciseFamilyId: exerciseFamilyId,
            relativeToCanonicalExerciseId: relativeToCanonicalExerciseId,
            relationshipType: relationshipType,
            relationshipNotes: relationshipNotes,
            sets: sets,
            warmupSets: warmupSets,
            restTime: restTime,
            isFavorite: isFavorite,
            trainingMode: trainingMode,
            customUnit: customUnit,
            reference1RM: reference1RM,
            targetSessionGoal: targetSessionGoal,
            isStarTarget: isStarTarget,
            trackHeartRate: trackHeartRate,
            trackRom: trackRom,
            setupDetails: setupDetails,
            supersetId: supersetId,
            supersetRestBetween: supersetRestBetween,
            supersetRestAfter: supersetRestAfter,
            supersetGroupRef: supersetGroupRef,
            variantName: variantName,
            selectedExecutionOption: selectedExecutionOption,
            selectedMovementPattern: selectedMovementPattern,
            prFor1RM: prFor1RM,
            consolidatedWeight: consolidatedWeight,
            brandEquivalencies: brandEquivalencies,
            isUnilateral: isUnilateral,
            unilateralMode: unilateralMode,
            unilateralSideOrder: unilateralSideOrder,
            unilateralIntensityMode: unilateralIntensityMode,
            restBetweenSidesSeconds: restBetweenSidesSeconds,
            isCalibratorAmrap: isCalibratorAmrap,
            goal1RM: goal1RM,
            goalPr: goalPr,
            calculated1RM: calculated1RM,
            damageProfile: damageProfile,
            isCompetitionLift: isCompetitionLift,
            setupCues: setupCues,
            executionCues: executionCues,
            contextProfilesV3: contextProfilesV3,
            defaultContextProfileIdV3: defaultContextProfileIdV3,
            mobilitySeries: mobilitySeries,
            timeStrategy: timeStrategy,
            targetDurationMinutes: targetDurationMinutes
        )
    }
}
