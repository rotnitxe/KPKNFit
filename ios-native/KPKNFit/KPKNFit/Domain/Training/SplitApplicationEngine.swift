import Foundation

// MARK: - Enums

public enum SplitTemporalScope { case CURRENT_WEEK, SELECTED_WEEKS, CURRENT_BLOCK, WHOLE_PROGRAM }
public enum AdvancedSplitMode { case GLOBAL, PER_BLOCK }
public enum SessionMigrationMode { case MIGRATE, CLEAN }
public enum StartDaySessionMode { case KEEP_DAYS, KEEP_SPLIT_ORDER }
public enum StartDayTemporalScope { case ALL_WEEKS, FROM_SELECTED_WEEK }

// MARK: - Supporting Types

public struct SplitBlockOption {
    public let id: String
    public let name: String
    public let macroName: String
    public init(id: String, name: String, macroName: String) {
        self.id = id; self.name = name; self.macroName = macroName
    }
}

public struct SplitWeekOption {
    public let id: String
    public let name: String
    public let blockId: String
    public let blockName: String
    public let macroName: String
    public let sessions: [Session]
    public init(id: String, name: String, blockId: String, blockName: String, macroName: String, sessions: [Session]) {
        self.id = id; self.name = name; self.blockId = blockId; self.blockName = blockName; self.macroName = macroName; self.sessions = sessions
    }
}

public struct SplitPatternDay {
    public let label: String
    public let dayOfWeek: Int
    public init(label: String, dayOfWeek: Int) {
        self.label = label; self.dayOfWeek = dayOfWeek
    }
}

public struct SplitImpactSummary {
    public let affectedWeeks: Int
    public let affectedSessions: Int
    public let willReplaceSessions: Bool

    public var isLargeDestructiveChange: Bool {
        willReplaceSessions && (affectedWeeks > 1 || affectedSessions > 4)
    }
}

public struct SplitApplicationRequest {
    public let program: Program
    public let selectedSplit: SplitTemplate
    public let selectedBlockId: String?
    public let selectedWeekId: String?
    public let startDay: Int
    public let temporalScope: SplitTemporalScope
    public let selectedWeekIds: Set<String>
    public let advancedMode: AdvancedSplitMode
    public let migrationMode: SessionMigrationMode
    public let perBlockSelections: [String: String]

    public init(
        program: Program,
        selectedSplit: SplitTemplate,
        selectedBlockId: String? = nil,
        selectedWeekId: String? = nil,
        startDay: Int,
        temporalScope: SplitTemporalScope,
        selectedWeekIds: Set<String> = [],
        advancedMode: AdvancedSplitMode = .GLOBAL,
        migrationMode: SessionMigrationMode = .MIGRATE,
        perBlockSelections: [String: String] = [:]
    ) {
        self.program = program
        self.selectedSplit = selectedSplit
        self.selectedBlockId = selectedBlockId
        self.selectedWeekId = selectedWeekId
        self.startDay = startDay
        self.temporalScope = temporalScope
        self.selectedWeekIds = selectedWeekIds
        self.advancedMode = advancedMode
        self.migrationMode = migrationMode
        self.perBlockSelections = perBlockSelections
    }
}

// MARK: - Engine

public enum SplitApplicationEngine {

    public static func buildBlockOptions(program: Program) -> [SplitBlockOption] {
        program.macrocycles.flatMap { macro in
            macro.blocks.map { block in
                SplitBlockOption(id: block.id, name: block.name, macroName: macro.name)
            }
        }
    }

    public static func buildWeekOptions(program: Program) -> [SplitWeekOption] {
        program.macrocycles.flatMap { macro in
            macro.blocks.flatMap { block in
                block.mesocycles.flatMap { meso in
                    meso.weeks.map { week in
                        SplitWeekOption(
                            id: week.id,
                            name: week.name,
                            blockId: block.id,
                            blockName: block.name,
                            macroName: macro.name,
                            sessions: week.sessions
                        )
                    }
                }
            }
        }
    }

    public static func impactSummary(request: SplitApplicationRequest) -> SplitImpactSummary {
        var affectedWeeks = 0
        var affectedSessions = 0
        request.program.forEachWeek { blockId, week in
            if shouldApplyToWeek(request: request, blockId: blockId, weekId: week.id) {
                affectedWeeks += 1
                affectedSessions += week.sessions.count
            }
        }
        return SplitImpactSummary(
            affectedWeeks: affectedWeeks,
            affectedSessions: affectedSessions,
            willReplaceSessions: request.migrationMode == .CLEAN && affectedSessions > 0
        )
    }

    public static func hasSessionsInTarget(request: SplitApplicationRequest) -> Bool {
        impactSummary(request: request).affectedSessions > 0
    }

    public static func apply(request: SplitApplicationRequest) -> Program {
        let blockAssignments = request.advancedMode == .PER_BLOCK ? request.perBlockSelections : [:]
        let selectedSplit = request.selectedSplit

        return request.program.copy(
            startDay: request.startDay,
            selectedSplitId: request.advancedMode == .GLOBAL ? selectedSplit.id : request.program.selectedSplitId,
            customSplitPattern: request.advancedMode == .GLOBAL ? selectedSplit.pattern : request.program.customSplitPattern,
            customSplitName: selectedSplit.id == "custom" ? selectedSplit.name : request.program.customSplitName,
            customSplitDescription: selectedSplit.id == "custom" ? selectedSplit.description : request.program.customSplitDescription,
            blockSplitSelections: request.advancedMode == .PER_BLOCK ? blockAssignments : [:],
            splitTrialSeen: false,
            macrocycles: request.program.macrocycles.map { macro in
                Macrocycle(
                    id: macro.id,
                    name: macro.name,
                    blocks: macro.blocks.map { block in
                        let blockSplit: SplitTemplate
                        if request.advancedMode == .PER_BLOCK {
                            blockSplit = SPLIT_TEMPLATES.first { $0.id == blockAssignments[block.id] } ?? selectedSplit
                        } else {
                            blockSplit = selectedSplit
                        }
                        return Block(
                            id: block.id,
                            name: block.name,
                            description: block.description,
                            mesocycles: block.mesocycles.map { meso in
                                Mesocycle(
                                    id: meso.id,
                                    name: meso.name,
                                    goal: meso.goal,
                                    customGoal: meso.customGoal,
                                    weeks: meso.weeks.map { week in
                                        if !shouldApplyToWeek(request: request, blockId: block.id, weekId: week.id) {
                                            return week
                                        }
                                        return ProgramWeek(
                                            id: week.id,
                                            name: week.name,
                                            description: week.description,
                                            sessions: buildSessionsForSplit(
                                                pattern: blockSplit.pattern,
                                                sessionDescriptions: blockSplit.sessionDescriptions,
                                                startDay: request.startDay,
                                                existingSessions: week.sessions,
                                                migrationMode: request.migrationMode
                                            ),
                                            variant: week.variant
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

    public static func buildSessionsForSplit(
        pattern: [String],
        sessionDescriptions: [String: String] = [:],
        startDay: Int,
        existingSessions: [Session],
        migrationMode: SessionMigrationMode
    ) -> [Session] {
        let trainingDays = patternToTrainingDays(pattern: pattern, startDay: startDay)
        if trainingDays.isEmpty { return [] }

        if existingSessions.isEmpty || migrationMode == .CLEAN {
            return normalizeMainSessions(
                trainingDays.map { day in
                    Session(
                        id: UUID().uuidString,
                        name: day.label,
                        description: splitSessionDescription(label: day.label, customDescriptions: sessionDescriptions),
                        exercises: [],
                        parts: [],
                        dayOfWeek: day.dayOfWeek,
                        assignedDays: [day.dayOfWeek],
                        scheduleLabel: day.label,
                        isMainSession: true
                    )
                }
            )
        }

        var unassignedDays = Array(trainingDays)
        var reassigned: [Session] = existingSessions.map { session in
            let target = bestTrainingDayForSession(session: session, trainingDays: unassignedDays.isEmpty ? trainingDays : unassignedDays)
            unassignedDays.removeAll { $0.dayOfWeek == target.dayOfWeek && $0.label == target.label }
            return session.copy(
                dayOfWeek: target.dayOfWeek,
                description: (session.description.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }) ?? splitSessionDescription(label: target.label, customDescriptions: sessionDescriptions),
                assignedDays: [target.dayOfWeek],
                scheduleLabel: target.label
            )
        }

        let coveredDays = Set(reassigned.compactMap { $0.dayOfWeek })
        for missingDay in trainingDays where !coveredDays.contains(missingDay.dayOfWeek) {
            reassigned.append(
                Session(
                    id: UUID().uuidString,
                    name: missingDay.label,
                    description: splitSessionDescription(label: missingDay.label, customDescriptions: sessionDescriptions),
                    exercises: [],
                    parts: [],
                    dayOfWeek: missingDay.dayOfWeek,
                    assignedDays: [missingDay.dayOfWeek],
                    scheduleLabel: missingDay.label,
                    isMainSession: false
                )
            )
        }

        return normalizeMainSessions(reassigned)
    }

    public static func copySessionsWithNewIds(sessions: [Session]) -> [Session] {
        normalizeMainSessions(sessions.map { $0.deepCopyWithNewIds() })
    }

    public static func applyStartDayChange(
        program: Program,
        selectedWeekId: String?,
        newStartDay: Int,
        temporalScope: StartDayTemporalScope,
        sessionMode: StartDaySessionMode
    ) -> Program {
        let oldStartDay = program.startDay
        let targetIds: Set<String>
        switch temporalScope {
        case .ALL_WEEKS:
            targetIds = Set(buildWeekOptions(program: program).map { $0.id })
        case .FROM_SELECTED_WEEK:
            targetIds = weekIdsFrom(program: program, selectedWeekId: selectedWeekId)
        }

        return program.copy(
            startDay: newStartDay,
            macrocycles: program.macrocycles.map { macro in
                Macrocycle(
                    id: macro.id,
                    name: macro.name,
                    blocks: macro.blocks.map { block in
                        Block(
                            id: block.id,
                            name: block.name,
                            description: block.description,
                            mesocycles: block.mesocycles.map { meso in
                                Mesocycle(
                                    id: meso.id,
                                    name: meso.name,
                                    goal: meso.goal,
                                    customGoal: meso.customGoal,
                                    weeks: meso.weeks.map { week in
                                        if !targetIds.contains(week.id) || sessionMode == .KEEP_DAYS {
                                            return week
                                        }
                                        return ProgramWeek(
                                            id: week.id,
                                            name: week.name,
                                            description: week.description,
                                            sessions: normalizeMainSessions(
                                                week.sessions.map { session in
                                                    let shiftedDay = shiftDayFromStart(day: session.dayOfWeek ?? oldStartDay, oldStartDay: oldStartDay, newStartDay: newStartDay)
                                                    return session.copy(dayOfWeek: shiftedDay, assignedDays: [shiftedDay])
                                                }
                                            ),
                                            variant: week.variant
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

    public static func patternToTrainingDays(pattern: [String], startDay: Int) -> [SplitPatternDay] {
        let orderedDays = [1, 2, 3, 4, 5, 6, 7]
        let offset = min(max(startDay - 1, 0), 6)
        let rotated = Array(orderedDays.dropFirst(offset)) + Array(orderedDays.prefix(offset))

        return pattern.enumerated().compactMap { index, label in
            if label.caseInsensitiveCompare("Descanso") == .orderedSame { return nil }
            return SplitPatternDay(label: label, dayOfWeek: rotated[index % rotated.count])
        }
    }

    public static func normalizeMainSessions(_ sessions: [Session]) -> [Session] {
        var mainByDay: [Int: String] = [:]
        var fallbackByDay: [Int: String] = [:]

        for session in sessions {
            let day = session.dayOfWeek ?? 1
            if fallbackByDay[day] == nil { fallbackByDay[day] = session.id }
            if session.isMainSession && mainByDay[day] == nil { mainByDay[day] = session.id }
        }

        for (day, sessionId) in fallbackByDay {
            if mainByDay[day] == nil { mainByDay[day] = sessionId }
        }

        return sessions.map { session in
            let day = session.dayOfWeek ?? 1
            return session.copy(isMainSession: mainByDay[day] == session.id)
        }
    }

    // MARK: - Private Helpers

    private static func shouldApplyToWeek(request: SplitApplicationRequest, blockId: String, weekId: String) -> Bool {
        switch request.temporalScope {
        case .CURRENT_WEEK:   return weekId == request.selectedWeekId
        case .SELECTED_WEEKS: return request.selectedWeekIds.contains(weekId)
        case .CURRENT_BLOCK:  return blockId == request.selectedBlockId
        case .WHOLE_PROGRAM:  return true
        }
    }

    private static func bestTrainingDayForSession(session: Session, trainingDays: [SplitPatternDay]) -> SplitPatternDay {
        let sessionMuscles = collectSessionMuscles(session: session)
        let sessionText = (session.name.lowercased() + " " + (session.description ?? "").lowercased())

        if sessionMuscles.isEmpty && sessionText.trimmingCharacters(in: .whitespaces).isEmpty {
            return trainingDays[0]
        }

        let scored = trainingDays.map { day -> (SplitPatternDay, Int) in
            (day, scoreSplitDay(label: day.label, sessionMuscles: sessionMuscles, sessionText: sessionText))
        }
        let bestScore = scored.map { $0.1 }.max() ?? 0
        if bestScore <= 0 { return trainingDays[0] }
        return scored.first { $0.1 == bestScore }!.0
    }

    private static func collectSessionMuscles(session: Session) -> Set<String> {
        var muscles = Set<String>()

        func collectFromExercises(_ exercises: [Exercise]) {
            for exercise in exercises {
                let info = exercise.exerciseDbId?.lowercased().flatMap { EXERCISE_DATABASE_BY_ID[$0] }
                for involved in SessionMuscleFilter.relevantMusclesFor(info) {
                    muscles.insert(
                        normalizeCanonicalMuscle(
                            VolumeCalculator.normalizeMuscleGroup(involved.muscle, emphasis: involved.emphasis)
                        )
                    )
                }
            }
        }

        collectFromExercises(session.exercises)
        for part in session.parts { collectFromExercises(part.exercises) }
        for nested in [session.sessionB, session.sessionC, session.sessionD].compactMap({ $0 }) {
            muscles.formUnion(collectSessionMuscles(session: nested))
        }
        return muscles
    }

    private static func normalizeCanonicalMuscle(_ muscle: String) -> String {
        let lower = muscle.lowercased()
        switch lower {
        case "cuadriceps", "cuádriceps": return "Cuádriceps"
        case "gluteos", "glúteos":       return "Glúteos"
        case "biceps", "bíceps":         return "Bíceps"
        case "triceps", "tríceps":       return "Tríceps"
        default:                         return muscle
        }
    }

    private static func scoreSplitDay(label: String, sessionMuscles: Set<String>, sessionText: String) -> Int {
        let keywords = splitKeywords(label: label)
        let textBonus = keywords.filter { sessionText.contains($0.lowercased()) }.count * 3
        let muscleBonus = sessionMuscles.filter { muscle in
            keywords.contains { keyword in
                muscle.lowercased().contains(keyword.lowercased()) ||
                keyword.lowercased().contains(muscle.lowercased())
            }
        }.count * 4
        let genericBonus: Int
        if label.localizedCaseInsensitiveContains("Torso") && sessionMuscles.contains(where: { upperBodyKeywords.contains($0) }) {
            genericBonus = 5
        } else if label.localizedCaseInsensitiveContains("Full") && !sessionMuscles.isEmpty {
            genericBonus = 4
        } else if label.localizedCaseInsensitiveContains("Pierna") && sessionMuscles.contains(where: { lowerBodyKeywords.contains($0) }) {
            genericBonus = 5
        } else {
            genericBonus = 0
        }
        return textBonus + muscleBonus + genericBonus
    }

    private static func splitKeywords(label: String) -> Set<String> {
        let lower = label.lowercased()
        var keywords = Set<String>()

        if lower.contains("empuje") || lower.contains("push") {
            keywords.formUnion(["Pectorales", "Tríceps", "Deltoides"])
        }
        if lower.contains("tirón") || lower.contains("tiron") || lower.contains("pull") || lower.contains("tracción") || lower.contains("traccion") {
            keywords.formUnion(["Dorsales", "Trapecio", "Bíceps", "Deltoides Posterior"])
        }
        if lower.contains("pierna") || lower.contains("lower") {
            keywords.formUnion(["Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas"])
        }
        if lower.contains("torso") || lower.contains("upper") {
            keywords.formUnion(upperBodyKeywords)
        }
        if lower.contains("full") || lower.contains("cuerpo completo") || lower.contains("sbd") {
            keywords.formUnion(upperBodyKeywords + lowerBodyKeywords)
        }
        if lower.contains("pecho") || lower.contains("banca") { keywords.insert("Pectorales") }
        if lower.contains("espalda") { keywords.formUnion(["Dorsales", "Trapecio", "Erectores Espinales"]) }
        if lower.contains("hombro") { keywords.formUnion(["Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior"]) }
        if lower.contains("brazo") { keywords.formUnion(["Bíceps", "Tríceps"]) }
        if lower.contains("cuádriceps") || lower.contains("cuadriceps") { keywords.insert("Cuádriceps") }
        if lower.contains("isquios") || lower.contains("femoral") { keywords.insert("Isquiosurales") }
        if lower.contains("glúteo") || lower.contains("gluteo") { keywords.insert("Glúteos") }
        if lower.contains("peso muerto") || lower.contains("deadlift") { keywords.formUnion(["Isquiosurales", "Glúteos", "Erectores Espinales", "Trapecio"]) }
        if lower.contains("sentadilla") || lower.contains("squat") { keywords.formUnion(["Cuádriceps", "Glúteos"]) }
        if keywords.isEmpty { keywords.insert(label.prefix(1).uppercased() + label.dropFirst()) }

        return keywords
    }

    private static func weekIdsFrom(program: Program, selectedWeekId: String?) -> Set<String> {
        guard let selectedWeekId = selectedWeekId else { return [] }
        let weeks = buildWeekOptions(program: program)
        guard let selectedIndex = weeks.firstIndex(where: { $0.id == selectedWeekId }) else { return [] }
        return Set(weeks.dropFirst(selectedIndex).map { $0.id })
    }

    private static func splitSessionDescription(label: String, customDescriptions: [String: String]) -> String {
        if let custom = customDescriptions[label] { return custom }
        let lower = label.lowercased()

        if lower.contains("descanso") { return "Día reservado para recuperación; no debería generar una sesión principal." }
        if lower.contains("empuje") || lower.contains("push") { return "Sesión enfocada en patrones de empuje: pecho, hombro anterior/lateral y tríceps." }
        if lower.contains("tirón") || lower.contains("tiron") || lower.contains("pull") { return "Sesión enfocada en tracción: espalda, deltoide posterior y bíceps." }
        if lower.contains("pierna") || lower.contains("lower") { return "Sesión de tren inferior: cuádriceps, isquiosurales, glúteos y pantorrillas según prioridad." }
        if lower.contains("torso") || lower.contains("upper") { return "Sesión de tren superior con empujes y tracciones balanceadas." }
        if lower.contains("cuerpo completo") || lower.contains("full body") { return "Sesión full body para distribuir volumen entre tren superior e inferior." }
        if lower.contains("pecho") && lower.contains("espalda") { return "Sesión antagonista para pecho y espalda, útil para alto volumen de torso." }
        if lower.contains("hombro") && lower.contains("brazo") { return "Sesión de especialización para deltoides, bíceps y tríceps." }
        if lower.contains("sentadilla") || lower.contains("squat") { return "Sesión con prioridad en sentadilla y accesorios compatibles." }
        if lower.contains("peso muerto") || lower.contains("deadlift") { return "Sesión con prioridad en peso muerto, cadena posterior y accesorios compatibles." }
        if lower.contains("banca") || lower.contains("bench") { return "Sesión con prioridad en press banca y musculatura de soporte." }
        if lower.contains("pesado") || lower.contains("max") { return "Día de mayor intensidad; mantén el volumen accesorio controlado." }
        if lower.contains("liviano") || lower.contains("recuperación") || lower.contains("recuperacion") { return "Día técnico o liviano para practicar patrones sin acumular demasiada fatiga." }
        if lower.contains("moderado") || lower.contains("volumen") { return "Día de volumen moderado para acumular trabajo sin llegar al máximo esfuerzo." }
        if lower.contains("accesorios") || lower.contains("hipertrofia") { return "Día accesorio para reforzar puntos débiles y completar volumen muscular." }
        return "Sesión creada desde el split \(label); ajusta ejercicios, volumen e intensidad según el objetivo de la semana."
    }

    private static func shiftDayFromStart(day: Int, oldStartDay: Int, newStartDay: Int) -> Int {
        let oldOrder = rotateDays(startDay: oldStartDay)
        let newOrder = rotateDays(startDay: newStartDay)
        let index = oldOrder.firstIndex(of: day) ?? 0
        return newOrder[index]
    }

    private static func rotateDays(startDay: Int) -> [Int] {
        let safe = min(max(startDay, 1), 7)
        return Array(safe...7) + Array(1..<safe)
    }

    private static let upperBodyKeywords: Set<String> = [
        "Pectorales", "Dorsales", "Trapecio", "Bíceps", "Tríceps",
        "Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior"
    ]

    private static let lowerBodyKeywords: Set<String> = [
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"
    ]
}
