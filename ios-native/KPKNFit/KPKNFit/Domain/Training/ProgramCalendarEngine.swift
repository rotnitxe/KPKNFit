import Foundation

enum ProgramEndDateStatus {
    case none, matchesProjected, beforeProjected, afterProjected, invalidManual
}

struct CalendarWeekProjection {
    let weekId: String
    let weekName: String
    let macroIndex: Int
    let blockIndex: Int
    let mesoIndex: Int
    let weekIndex: Int
    let blockId: String
    let blockName: String
    let startDate: Date
    let endDate: Date
    let outsideProgramDays: Set<Int>
    let trainingDayDates: [Int: Date]
    let keyDates: [ProgramKeyDate]

    func contains(_ date: Date) -> Bool {
        date >= startDate && date <= endDate
    }
}

struct ProgramCalendarProjection {
    let enabled: Bool
    let mode: ProgramCalendarizationMode?
    let strictStart: Bool
    let activatedByCompetition: Bool
    let startDate: Date?
    let projectedEndDate: Date?
    let manualEndDate: Date?
    let endDateStatus: ProgramEndDateStatus
    let weeks: [CalendarWeekProjection]

    var effectiveEndDate: Date? { manualEndDate ?? projectedEndDate }

    func weekForDate(_ date: Date) -> CalendarWeekProjection? {
        weeks.first { $0.contains(date) }
    }

    func scheduledDateFor(_ session: Session, weekId: String) -> Date? {
        guard let week = weeks.first(where: { $0.weekId == weekId }) else { return nil }
        guard let day = session.dayOfWeek, (1...7).contains(day) else { return week.startDate }
        return week.trainingDayDates[day]
    }
}

enum ProgramCalendarEngine {
    static func parseIsoDate(_ raw: String?) -> Date? {
        guard let raw = raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter.date(from: raw)
    }

    static func isCalendarized(_ program: Program) -> Bool {
        guard let calendar = program.calendarization else { return false }
        switch calendar.mode {
        case .advancedCompetition:
            return !program.isSimpleTemporalProgram && !(program.timelineStartDate ?? "").isEmpty
        case .simpleDated:
            return program.isSimpleTemporalProgram && program.simpleProgramKind == .calendarized && !(program.timelineStartDate ?? "").isEmpty
        }
    }

    static func defaultCompetitionCalendarization() -> ProgramCalendarization {
        ProgramCalendarization(mode: .advancedCompetition, strictStart: true, activatedByCompetition: true)
    }

    static func defaultSimpleDatedCalendarization() -> ProgramCalendarization {
        ProgramCalendarization(mode: .simpleDated, strictStart: false, activatedByCompetition: false)
    }

    static func project(_ program: Program) -> ProgramCalendarProjection {
        let calendar = program.calendarization
        let start = parseIsoDate(program.timelineStartDate)
        guard let calendar = calendar, let start = start else {
            return ProgramCalendarProjection(
                enabled: false, mode: calendar?.mode,
                strictStart: calendar?.strictStart == true,
                activatedByCompetition: calendar?.activatedByCompetition == true,
                startDate: start, projectedEndDate: nil,
                manualEndDate: parseIsoDate(calendar?.manualEndDate),
                endDateStatus: .none, weeks: []
            )
        }

        var cursor = start
        var globalWeekIndex = 0
        var weeks: [CalendarWeekProjection] = []

        for (macroIndex, macro) in program.macrocycles.enumerated() {
            var globalMesoIndex = 0
            for (blockIndex, block) in macro.blocks.enumerated() {
                for meso in block.mesocycles {
                    let mesoIndex = globalMesoIndex
                    globalMesoIndex += 1
                    for week in meso.weeks {
                        let weekStart = parseIsoDate(week.startDate) ?? cursor
                        let weekEnd = parseIsoDate(week.endDate) ?? projectedWeekEnd(start: weekStart)
                        let outsideDays = outsideDaysFor()
                        let dayDates = trainingDatesFor(start: weekStart, end: weekEnd, outsideDays: outsideDays, explicit: week.trainingDayDates)
                        let marks = program.keyDates.filter { keyDateIntersects($0, weekStart: weekStart, weekEnd: weekEnd) }
                        weeks.append(CalendarWeekProjection(
                            weekId: week.id, weekName: week.name,
                            macroIndex: macroIndex, blockIndex: blockIndex,
                            mesoIndex: mesoIndex, weekIndex: globalWeekIndex,
                            blockId: block.id, blockName: block.name,
                            startDate: weekStart, endDate: weekEnd,
                            outsideProgramDays: outsideDays, trainingDayDates: dayDates, keyDates: marks
                        ))
                        cursor = Calendar.current.date(byAdding: .day, value: 1, to: weekEnd) ?? weekEnd
                        globalWeekIndex += 1
                    }
                }
            }
        }

        let projectedEnd = weeks.last?.endDate
        let manualEnd = parseIsoDate(calendar.manualEndDate)
        let status = endDateStatus(manualEnd: manualEnd, projectedEnd: projectedEnd, rawManual: calendar.manualEndDate)

        return ProgramCalendarProjection(
            enabled: true, mode: calendar.mode, strictStart: calendar.strictStart,
            activatedByCompetition: calendar.activatedByCompetition,
            startDate: start, projectedEndDate: projectedEnd,
            manualEndDate: manualEnd, endDateStatus: status, weeks: weeks
        )
    }

    static func materializeWeekDates(_ program: Program) -> Program {
        let projection = project(program)
        guard projection.enabled, !projection.weeks.isEmpty else { return program }
        let byId = Dictionary(uniqueKeysWithValues: projection.weeks.map { ($0.weekId, $0) })
        return program.copy(macrocycles: program.macrocycles.map { macro in
            macro.copy(blocks: macro.blocks.map { block in
                block.copy(mesocycles: block.mesocycles.map { meso in
                    meso.copy(weeks: meso.weeks.map { week in
                        guard let projected = byId[week.id] else { return week }
                        return week.copy(
                            startDate: ISO8601DateFormatter().string(from: projected.startDate),
                            endDate: ISO8601DateFormatter().string(from: projected.endDate),
                            trainingDayDates: projected.trainingDayDates.mapValues { ISO8601DateFormatter().string(from: $0) }
                        )
                    })
                })
            })
        })
    }

    static func scheduleIssueFor(program: Program, weekId: String?, session: Session, actualDate: Date = Date()) -> ScheduleIssue? {
        let projection = project(program)
        guard projection.enabled else { return nil }
        let planned = weekId.flatMap { projection.scheduledDateFor(session, weekId: $0) }
        let actualWeek = projection.weekForDate(actualDate)
        if actualWeek == nil {
            return .outsideProgram(actual: actualDate, start: projection.startDate, end: projection.effectiveEndDate)
        }
        if let planned = planned, planned != actualDate {
            return .wrongDate(planned: planned, actual: actualDate)
        }
        if planned == nil && weekId != nil {
            return .outsideProgramDay(actual: actualDate)
        }
        return nil
    }

    private static func projectedWeekEnd(start: Date) -> Date {
        Calendar.current.date(byAdding: .day, value: 6, to: start) ?? start
    }

    private static func outsideDaysFor() -> Set<Int> {
        []
    }

    private static func trainingDatesFor(start: Date, end: Date, outsideDays: Set<Int>, explicit: [Int: String]) -> [Int: Date] {
        var result: [Int: Date] = [:]
        for day in 1...7 where !outsideDays.contains(day) {
            if let explicitDate = parseIsoDate(explicit[day]) {
                if explicitDate >= start && explicitDate <= end { result[day] = explicitDate }
            } else if let resolved = dateForDay(start: start, end: end, day: day) {
                result[day] = resolved
            }
        }
        return result
    }

    private static func dateForDay(start: Date, end: Date, day: Int) -> Date? {
        var cursor = start
        while cursor <= end {
            if Calendar.current.component(.weekday, from: cursor) == day { return cursor }
            cursor = Calendar.current.date(byAdding: .day, value: 1, to: cursor) ?? cursor
        }
        return nil
    }

    private static func keyDateIntersects(_ keyDate: ProgramKeyDate, weekStart: Date, weekEnd: Date) -> Bool {
        guard let event = parseIsoDate(keyDate.eventDate) else { return false }
        let start = parseIsoDate(keyDate.startDate) ?? event
        let end = parseIsoDate(keyDate.endDate) ?? start
        return end >= weekStart && start <= weekEnd
    }

    private static func endDateStatus(manualEnd: Date?, projectedEnd: Date?, rawManual: String?) -> ProgramEndDateStatus {
        guard let raw = rawManual, !raw.isEmpty else { return .none }
        guard let manual = manualEnd, let projected = projectedEnd else { return .invalidManual }
        let diff = Calendar.current.dateComponents([.day], from: projected, to: manual).day ?? 0
        if diff == 0 { return .matchesProjected }
        if diff < 0 { return .beforeProjected }
        return .afterProjected
    }
}

enum ScheduleIssue {
    case wrongDate(planned: Date, actual: Date)
    case outsideProgram(actual: Date, start: Date?, end: Date?)
    case outsideProgramDay(actual: Date)
}
