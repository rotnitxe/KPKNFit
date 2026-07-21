import Foundation

struct LoopProjection {
    let loop: Loop
    let cycle: Int
    let isPostponed: Bool
    let isCancelled: Bool
    let daysUntil: Int
    let weekInCycle: Int
}

enum LoopEngine {

    static func materializeLoopWeeks(_ program: Program) -> Program {
        guard program.isSimpleTemporalProgram, !program.loops.isEmpty else { return program }
        guard let firstMacro = program.macrocycles.first,
              let firstBlock = firstMacro.blocks.first,
              let firstMeso = firstBlock.mesocycles.first else { return program }

        let existingLoopIds = Set(program.loops.map { $0.id })
        let existingWeeksByLoop = Dictionary(
            uniqueKeysWithValues: firstBlock.mesocycles
                .flatMap { $0.weeks }
                .filter { $0.isLoopWeek && $0.loopId != nil }
                .map { ($0.loopId!, $0) }
        )

        let loopWeeks = program.loops.map { loop -> ProgramWeek in
            let existing = existingWeeksByLoop[loop.id]
            return ProgramWeek(
                id: existing?.id ?? "loop_week_\(loop.id)",
                name: loop.title.isEmpty ? getLoopTypeLabel(loop.type) : loop.title,
                description: "Loop \(getLoopTypeLabel(loop.type)) · cada \(max(loop.repeatEveryXLoops, 1)) ciclos",
                sessions: existing?.sessions ?? loop.sessions,
                isLoopWeek: true,
                loopId: loop.id
            )
        }

        return program.copy(
            macrocycles: program.macrocycles.enumerated().map { macroIndex, macro in
                guard macroIndex == 0 else { return macro }
                return macro.copy(
                    blocks: macro.blocks.enumerated().map { blockIndex, block in
                        guard blockIndex == 0 else { return block }
                        return block.copy(
                            mesocycles: block.mesocycles.enumerated().map { mesoIndex, meso in
                                if mesoIndex != 0 {
                                    return meso.copy(weeks: meso.weeks.filter { !($0.isLoopWeek && existingLoopIds.contains($0.loopId ?? "")) })
                                } else {
                                    let normalWeeks = meso.weeks.filter { !$0.isLoopWeek }
                                    return meso.copy(weeks: normalWeeks + loopWeeks)
                                }
                            }
                        )
                    }
                )
            }
        )
    }

    static func upsertLoop(_ program: Program, loop: Loop) -> Program {
        let exists = program.loops.contains { $0.id == loop.id }
        let nextLoops = exists ? program.loops.map { $0.id == loop.id ? loop : $0 } : program.loops + [loop]
        return materializeLoopWeeks(program.copy(loops: nextLoops))
    }

    static func deleteLoop(_ program: Program, loopId: String) -> Program {
        let nextState = program.loopState?.copy(
            cancelled: program.loopState?.cancelled.filter { $0 != loopId } ?? [],
            postponed: program.loopState?.postponed.filter { $0.loopId != loopId } ?? []
        )
        return program.copy(
            loops: program.loops.filter { $0.id != loopId },
            loopState: nextState,
            macrocycles: program.macrocycles.map { macro in
                macro.copy(blocks: macro.blocks.map { block in
                    block.copy(mesocycles: block.mesocycles.map { meso in
                        meso.copy(weeks: meso.weeks.filter { !($0.isLoopWeek && $0.loopId == loopId) })
                    })
                })
            }
        )
    }

    static func getCycleLength(_ program: Program) -> Int {
        guard let block = program.macrocycles.first?.blocks.first else { return 1 }
        return block.mesocycles.reduce(0) { $0 + $1.weeks.filter { !$0.isLoopWeek }.count } > 0
            ? block.mesocycles.reduce(0) { $0 + $1.weeks.filter { !$0.isLoopWeek }.count }
            : 1
    }

    static func getCurrentCycle(_ program: Program) -> Int {
        program.loopState?.currentCycle ?? 0
    }

    static func getDaysIntoCycle(_ program: Program, daysSinceStart: Int) -> Int {
        let cycleLength = getCycleLength(program)
        let cycleDays = cycleLength * (program.weekDays ?? 7)
        return cycleDays > 0 ? daysSinceStart % cycleDays : 0
    }

    static func projectLoops(_ program: Program, fromCycle: Int, lookAheadCycles: Int = 12) -> [LoopProjection] {
        let loops = program.loops
        guard !loops.isEmpty else { return [] }

        let cycleLength = getCycleLength(program)
        let cycleDays = cycleLength * (program.weekDays ?? 7)
        let postponed = program.loopState?.postponed ?? []
        let cancelled = Set(program.loopState?.cancelled ?? [])
        var projections: [LoopProjection] = []

        for cycle in fromCycle..<(fromCycle + lookAheadCycles) {
            for loop in loops {
                guard !cancelled.contains(loop.id) else { continue }
                let isActive = cycle > 0 && cycle % loop.repeatEveryXLoops == 0
                let postponement = postponed.first { $0.loopId == loop.id && $0.fromCycle == cycle }

                if isActive && postponement == nil {
                    projections.append(LoopProjection(loop: loop, cycle: cycle, isPostponed: false, isCancelled: false, daysUntil: (cycle - fromCycle) * cycleDays, weekInCycle: cycleLength))
                }
                let deferredHere = postponed.first { $0.loopId == loop.id && $0.toCycle == cycle }
                if deferredHere != nil {
                    projections.append(LoopProjection(loop: loop, cycle: cycle, isPostponed: true, isCancelled: false, daysUntil: (cycle - fromCycle) * cycleDays, weekInCycle: cycleLength))
                }
            }
        }
        return projections.sorted { $0.cycle < $1.cycle || ($0.cycle == $1.cycle && ($0.loop.priority ?? 0) > ($1.loop.priority ?? 0)) }
    }

    static func detectLoopCollisions(_ projections: [LoopProjection]) -> [Int: [LoopProjection]] {
        Dictionary(grouping: projections, by: \.cycle).filter { $0.value.count > 1 }
    }

    static func postponeLoop(_ program: Program, loopId: String, fromCycle: Int) -> Program {
        let state = program.loopState ?? LoopState()
        return program.copy(loopState: state.copy(
            postponed: state.postponed + [PostponedLoop(loopId: loopId, fromCycle: fromCycle, toCycle: fromCycle + 1)]
        ))
    }

    static func cancelLoop(_ program: Program, loopId: String) -> Program {
        let state = program.loopState ?? LoopState()
        guard !state.cancelled.contains(loopId) else { return program }
        return program.copy(loopState: state.copy(cancelled: state.cancelled + [loopId]))
    }

    static func reactivateLoop(_ program: Program, loopId: String) -> Program {
        guard let state = program.loopState else { return program }
        return program.copy(loopState: state.copy(cancelled: state.cancelled.filter { $0 != loopId }))
    }

    static func advanceCycle(_ program: Program) -> Program {
        let state = program.loopState ?? LoopState()
        let newCycle = state.currentCycle + 1
        return program.copy(loopState: state.copy(
            currentCycle: newCycle,
            postponed: state.postponed.filter { $0.toCycle > newCycle }
        ))
    }

    static func migrateEventsToLoops(_ program: Program) -> Program {
        let legacyEvents = program.events.filter { $0.repeatEveryXCycles != nil }
        guard !legacyEvents.isEmpty else { return program }

        var existingLoops = program.loops
        var remainingEvents = program.events

        for event in legacyEvents {
            if existingLoops.contains(where: { $0.title == event.title }) { continue }
            existingLoops.append(Loop(
                id: event.id ?? UUID().uuidString,
                title: event.title,
                type: LoopType(rawValue: (event.type ?? "custom").uppercased()) ?? .custom,
                repeatEveryXLoops: event.repeatEveryXCycles!,
                durationType: .week,
                sessions: event.sessions
            ))
            remainingEvents.removeAll { $0.id == event.id }
        }

        return materializeLoopWeeks(program.copy(loops: existingLoops, events: remainingEvents))
    }

    static func formatLoopCountdown(_ daysUntil: Int) -> String {
        if daysUntil <= 0 { return "Ahora" }
        if daysUntil == 1 { return "1 día" }
        if daysUntil < 7 { return "\(daysUntil) días" }
        let weeks = daysUntil / 7
        let days = daysUntil % 7
        return days == 0 ? "\(weeks) sem" : "\(weeks)s \(days)d"
    }

    static func getLoopTypeEmoji(_ type: LoopType) -> String {
        switch type {
        case .oneRMTest: return "🏋️"
        case .deload: return "🧈"
        case .competition: return "🏆"
        case .custom: return "⚡"
        }
    }

    static func getLoopTypeLabel(_ type: LoopType) -> String {
        switch type {
        case .oneRMTest: return "Test 1RM"
        case .deload: return "Descarga"
        case .competition: return "Competición"
        case .custom: return "Personalizado"
        }
    }
}
