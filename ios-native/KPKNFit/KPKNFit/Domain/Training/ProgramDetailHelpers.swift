import Foundation

// MARK: - Data Types

public struct RoadmapBlock {
    public let id: String
    public let name: String
    public let description: String?
    public let macroIndex: Int
    public let blockIndex: Int
    public let totalWeeks: Int
    public let dateRangeLabel: String?

    public init(id: String, name: String, description: String? = nil, macroIndex: Int, blockIndex: Int, totalWeeks: Int, dateRangeLabel: String? = nil) {
        self.id = id
        self.name = name
        self.description = description
        self.macroIndex = macroIndex
        self.blockIndex = blockIndex
        self.totalWeeks = totalWeeks
        self.dateRangeLabel = dateRangeLabel
    }
}

public struct WeekWithMeta {
    public let id: String
    public let name: String
    public let description: String?
    public let sessions: [Session]
    public let mesoGoal: MesocycleGoal
    public let mesoIndex: Int
    public let variant: WeekVariant?
    public let isLoopWeek: Bool
    public let loopId: String?
    public let dateRangeLabel: String?
    public let keyDateLabel: String?
    public let keyDateType: KeyDateType?
    public let outsideProgramDays: Set<Int>
    public let trainingDayDates: [Int: String]

    public init(
        id: String, name: String, description: String? = nil, sessions: [Session],
        mesoGoal: MesocycleGoal, mesoIndex: Int, variant: WeekVariant? = nil,
        isLoopWeek: Bool = false, loopId: String? = nil,
        dateRangeLabel: String? = nil, keyDateLabel: String? = nil,
        keyDateType: KeyDateType? = nil,
        outsideProgramDays: Set<Int> = [], trainingDayDates: [Int: String] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.sessions = sessions
        self.mesoGoal = mesoGoal
        self.mesoIndex = mesoIndex
        self.variant = variant
        self.isLoopWeek = isLoopWeek
        self.loopId = loopId
        self.dateRangeLabel = dateRangeLabel
        self.keyDateLabel = keyDateLabel
        self.keyDateType = keyDateType
        self.outsideProgramDays = outsideProgramDays
        self.trainingDayDates = trainingDayDates
    }
}

public struct DiscomfortEntry {
    public let name: String
    public let count: Int

    public init(name: String, count: Int) {
        self.name = name
        self.count = count
    }
}

public struct ExerciseDiscomfortAssociationEntry {
    public let exerciseDbId: String?
    public let exerciseName: String
    public let discomfortLabel: String
    public let count: Int

    public init(exerciseDbId: String?, exerciseName: String, discomfortLabel: String, count: Int) {
        self.exerciseDbId = exerciseDbId
        self.exerciseName = exerciseName
        self.discomfortLabel = discomfortLabel
        self.count = count
    }
}

public struct WeekAdherence {
    public let weekName: String
    public let pct: Int

    public init(weekName: String, pct: Int) {
        self.weekName = weekName
        self.pct = pct
    }
}

public struct RoadmapLoopMarker {
    public let id: String
    public let label: String
    public let title: String
    public let repeatEveryCycles: Int

    public init(id: String, label: String, title: String, repeatEveryCycles: Int) {
        self.id = id
        self.label = label
        self.title = title
        self.repeatEveryCycles = repeatEveryCycles
    }
}

// MARK: - ProgramDetailHelpers

public enum ProgramDetailHelpers {

    public static func isSimpleProgram(_ program: Program) -> Bool {
        program.isSimpleTemporalProgram
    }

    public static func buildRoadmapBlocks(_ program: Program) -> [RoadmapBlock] {
        let blockDateRanges = buildBlockDateRanges(program)
        return program.macrocycles.enumerated().flatMap { macroIdx, macro in
            macro.blocks.enumerated().map { blockIdx, block in
                RoadmapBlock(
                    id: block.id,
                    name: block.name,
                    description: block.description,
                    macroIndex: macroIdx,
                    blockIndex: blockIdx,
                    totalWeeks: block.mesocycles.reduce(0) { $0 + $1.weeks.count },
                    dateRangeLabel: blockDateRanges[block.id]
                )
            }
        }
    }

    public static func findActiveBlockId(
        activeProgramState: ActiveProgramState?,
        programId: String,
        roadmapBlocks: [RoadmapBlock]
    ) -> String? {
        guard let active = activeProgramState, active.programId == programId else { return nil }
        return roadmapBlocks.first { $0.macroIndex == active.currentMacrocycleIndex && $0.blockIndex == active.currentBlockIndex }?.id
    }

    public static func getWeeksForBlock(
        selectedBlockId: String?,
        roadmapBlocks: [RoadmapBlock],
        program: Program
    ) -> [WeekWithMeta] {
        guard let blockId = selectedBlockId,
              let block = roadmapBlocks.first(where: { $0.id == blockId }),
              let macro = program.macrocycles[safe: block.macroIndex]
        else { return [] }

        let weekDateMeta = buildWeekDateMeta(program)

        var mesoOffset = 0
        for b in macro.blocks {
            if b.id == block.id { break }
            mesoOffset += b.mesocycles.count
        }

        guard let actualBlock = macro.blocks[safe: block.blockIndex] else { return [] }

        return actualBlock.mesocycles.enumerated().flatMap { localMesoIdx, meso in
            meso.weeks.map { week in
                let meta = weekDateMeta[week.id]
                return WeekWithMeta(
                    id: week.id,
                    name: week.name,
                    description: week.description,
                    sessions: week.sessions,
                    mesoGoal: meso.goal,
                    mesoIndex: mesoOffset + localMesoIdx,
                    variant: week.variant,
                    isLoopWeek: week.isLoopWeek ?? false,
                    loopId: week.loopId,
                    dateRangeLabel: meta?.dateRangeLabel,
                    keyDateLabel: meta?.keyDateLabel,
                    keyDateType: meta?.keyDateType,
                    outsideProgramDays: meta?.outsideProgramDays ?? [],
                    trainingDayDates: meta?.trainingDayDates ?? [:]
                )
            }
        }
    }

    public static func getDisplayedSessions(
        selectedWeekId: String?,
        weeks: [WeekWithMeta]
    ) -> [Session] {
        guard let weekId = selectedWeekId else { return [] }
        return weeks.first { $0.id == weekId }?.sessions ?? []
    }

    public static func buildSimpleRoadmapLoopMarkers(_ program: Program) -> [RoadmapLoopMarker] {
        guard program.isSimpleTemporalProgram else { return [] }

        let loopMarkers = program.loops.map { loop in
            RoadmapLoopMarker(
                id: loop.id,
                label: loopTypeLabel(loop.type),
                title: loop.title,
                repeatEveryCycles: max(loop.repeatEveryXLoops, 1)
            )
        }

        let legacyMarkers = program.events
            .filter { $0.repeatEveryXCycles != nil }
            .map { event in
                RoadmapLoopMarker(
                    id: event.id ?? event.title,
                    label: eventTypeLabel(event.type),
                    title: event.title,
                    repeatEveryCycles: max(event.repeatEveryXCycles ?? 1, 1)
                )
            }

        return (loopMarkers + legacyMarkers)
            .reduce(into: [String: RoadmapLoopMarker]()) { dict, marker in
                if dict[marker.id] == nil { dict[marker.id] = marker }
            }
            .values
            .sorted { $0.repeatEveryCycles < $1.repeatEveryCycles || ($0.repeatEveryCycles == $1.repeatEveryCycles && $0.title < $1.title) }
    }

    private static func loopTypeLabel(_ type: LoopType) -> String {
        switch type {
        case .oneRMTest: return "1RM"
        case .deload: return "Deload"
        case .competition: return "Comp"
        case .custom: return "Loop"
        }
    }

    private static func eventTypeLabel(_ type: String) -> String {
        switch type.trimmingCharacters(in: .whitespaces).lowercased() {
        case "one_rm_test", "1rm", "test": return "1RM"
        case "deload", "descarga": return "Deload"
        case "competition", "competicion", "competición": return "Comp"
        default: return "Evento"
        }
    }

    // MARK: - Public Computation Helpers

    public static func computeProgramDiscomforts(
        history: [WorkoutLog],
        programId: String
    ) -> [DiscomfortEntry] {
        let filtered = history.filter { $0.programId == programId }
        var counts: [String: Int] = [:]
        for log in filtered {
            for d in log.discomforts {
                counts[d, default: 0] += 1
            }
        }
        return counts.map { DiscomfortEntry(name: $0.key, count: $0.value) }
            .sorted { $0.count > $1.count }
    }

    public static func computeExerciseDiscomfortAssociations(
        history: [WorkoutLog],
        programId: String
    ) -> [ExerciseDiscomfortAssociationEntry] {
        let filtered = history.filter { $0.programId == programId }
        var counts: [Triple: Int] = [:]
        for log in filtered {
            for report in log.postExerciseReports {
                for discomfortId in report.discomfortIds where discomfortId != "none" {
                    let key = Triple(
                        first: report.exerciseDbId,
                        second: report.exerciseName,
                        third: discomfortLabel(id: discomfortId)
                    )
                    counts[key, default: 0] += 1
                }
            }
        }
        return counts.map { (key, count) in
            ExerciseDiscomfortAssociationEntry(
                exerciseDbId: key.first,
                exerciseName: key.second,
                discomfortLabel: key.third,
                count: count
            )
        }.sorted { $0.count > $1.count }
    }

    public static func computeProgramLogs(
        history: [WorkoutLog],
        programId: String
    ) -> [WorkoutLog] {
        history.filter { $0.programId == programId }
            .sorted { $0.date > $1.date }
    }

    public static func computeTotalAdherence(programLogs: [WorkoutLog], program: Program) -> Int {
        let completedIds = Set(programLogs.map { $0.sessionId })
        let allSessions = program.macrocycles.flatMap { macro in
            macro.blocks.flatMap { block in
                block.mesocycles.flatMap { meso in
                    meso.weeks.flatMap { week in week.sessions }
                }
            }
        }
        guard !allSessions.isEmpty else { return 0 }
        let completed = allSessions.filter { completedIds.contains($0.id) }.count
        return Int((Double(completed) / Double(allSessions.count)) * 100.0)
    }

    public static func computeWeeklyAdherence(
        currentWeeks: [WeekWithMeta],
        programLogs: [WorkoutLog]
    ) -> [WeekAdherence] {
        currentWeeks.enumerated().map { idx, week in
            let weekSessionIds = Set(week.sessions.map { $0.id })
            let logs = programLogs.filter { weekSessionIds.contains($0.sessionId) }
            let completed = Set(logs.map { $0.sessionId }).count
            let planned = week.sessions.count
            return WeekAdherence(
                weekName: "Semana \(idx + 1)",
                pct: planned > 0 ? Int((Double(completed) / Double(planned)) * 100.0) : 0
            )
        }
    }

    public static func computeCurrentWeekIndex(
        activeProgramState: ActiveProgramState?,
        program: Program
    ) -> Int {
        guard let state = activeProgramState, state.programId == program.id else { return 0 }
        var weekIdx = 0
        for macro in program.macrocycles {
            for block in macro.blocks {
                for meso in block.mesocycles {
                    for week in meso.weeks {
                        if week.id == state.currentWeekId { return weekIdx }
                        weekIdx += 1
                    }
                }
            }
        }
        return 0
    }

    public static func getTotalWeeks(_ program: Program) -> Int {
        program.macrocycles.reduce(0) { macroAcc, macro in
            macroAcc + macro.blocks.reduce(0) { blockAcc, block in
                blockAcc + block.mesocycles.reduce(0) { mesoAcc, meso in
                    mesoAcc + meso.weeks.count
                }
            }
        }
    }

    // MARK: - Private Calendar Helpers

    private struct WeekDateMeta {
        let dateRangeLabel: String?
        let keyDateLabel: String?
        let keyDateType: KeyDateType?
        let outsideProgramDays: Set<Int>
        let trainingDayDates: [Int: String]

        init(dateRangeLabel: String? = nil, keyDateLabel: String? = nil, keyDateType: KeyDateType? = nil,
             outsideProgramDays: Set<Int> = [], trainingDayDates: [Int: String] = [:]) {
            self.dateRangeLabel = dateRangeLabel
            self.keyDateLabel = keyDateLabel
            self.keyDateType = keyDateType
            self.outsideProgramDays = outsideProgramDays
            self.trainingDayDates = trainingDayDates
        }
    }

    private static func buildBlockDateRanges(_ program: Program) -> [String: String] {
        let projection = ProgramCalendarEngine.project(program)
        if projection.enabled {
            let grouped = Dictionary(grouping: projection.weeks, by: \.blockId)
            return grouped.mapValues { weeks in
                formatDateRange(weeks.first!.startDate, weeks.last!.endDate)
            }
        }

        guard var cursor = parseProgramDate(program.timelineStartDate) else { return [:] }
        var ranges: [String: String] = [:]

        for macro in program.macrocycles {
            for block in macro.blocks {
                let weeks = block.mesocycles.flatMap { $0.weeks }
                guard !weeks.isEmpty else { continue }

                let explicitStarts = weeks.compactMap { parseProgramDate($0.startDate) }
                let explicitEnds = weeks.compactMap { parseProgramDate($0.endDate) }
                let start = explicitStarts.min() ?? cursor
                let end = explicitEnds.max() ?? Calendar.current.date(byAdding: .day, value: weeks.count * 7 - 1, to: start)!
                ranges[block.id] = formatDateRange(start, end)
                cursor = Calendar.current.date(byAdding: .day, value: 1, to: end)!
            }
        }
        return ranges
    }

    private static func buildWeekDateMeta(_ program: Program) -> [String: WeekDateMeta] {
        let projection = ProgramCalendarEngine.project(program)
        if projection.enabled {
            return Dictionary(uniqueKeysWithValues: projection.weeks.map { week in
                let keyDate = week.keyDates.first
                return (week.weekId, WeekDateMeta(
                    dateRangeLabel: formatDateRange(week.startDate, week.endDate),
                    keyDateLabel: keyDate?.roadmapLabel(),
                    keyDateType: keyDate?.type,
                    outsideProgramDays: week.outsideProgramDays,
                    trainingDayDates: week.trainingDayDates.mapValues { programCalendarDateFormatter.string(from: $0) }
                ))
            })
        }

        guard var cursor = parseProgramDate(program.timelineStartDate) else { return [:] }
        var meta: [String: WeekDateMeta] = [:]

        for macro in program.macrocycles {
            for block in macro.blocks {
                for meso in block.mesocycles {
                    for week in meso.weeks {
                        let weekStart = parseProgramDate(week.startDate) ?? cursor
                        let weekEnd = parseProgramDate(week.endDate) ?? Calendar.current.date(byAdding: .day, value: 6, to: weekStart)!
                        let keyDate = program.keyDates.first { $0.intersectsWeek(weekStart, weekEnd) }
                        meta[week.id] = WeekDateMeta(
                            dateRangeLabel: formatDateRange(weekStart, weekEnd),
                            keyDateLabel: keyDate?.roadmapLabel(),
                            keyDateType: keyDate?.type,
                            trainingDayDates: week.trainingDayDates
                        )
                        cursor = Calendar.current.date(byAdding: .day, value: 1, to: weekEnd)!
                    }
                }
            }
        }
        return meta
    }

    private static func parseProgramDate(_ raw: String?) -> Date? {
        ProgramCalendarEngine.parseIsoDate(raw)
    }

    private static func formatDateRange(_ start: Date, _ end: Date) -> String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "es_CL")
        fmt.dateFormat = "d MMM"
        return "\(fmt.string(from: start))-\(fmt.string(from: end))"
    }

    private static let programCalendarDateFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "es_CL")
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt
    }()
}

// MARK: - ProgramKeyDate Helpers

extension ProgramKeyDate {
    fileprivate func intersectsWeek(_ weekStart: Date, _ weekEnd: Date) -> Bool {
        guard let start = ProgramDetailHelpers.parseProgramDate(startDate)
                ?? ProgramDetailHelpers.parseProgramDate(eventDate)
        else { return false }
        let end = ProgramDetailHelpers.parseProgramDate(endDate) ?? start
        return end >= weekStart && start <= weekEnd
    }

    fileprivate func roadmapLabel() -> String {
        switch type {
        case .COMPETITION: return "Comp"
        case .EXAMS: return "Examen"
        case .VACATION: return "Libre"
        case .TRAVEL: return "Viaje"
        case .CUSTOM: return title.prefix(8).isEmpty ? "Clave" : String(title.prefix(8))
        }
    }
}

// MARK: - Internal Triple Helper

private struct Triple: Hashable {
    let first: String?
    let second: String
    let third: String
}

// MARK: - Safe Collection Access

extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
