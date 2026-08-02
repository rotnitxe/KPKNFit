import Foundation
import Combine

public final class HomeViewModel: ObservableObject {
    private let repository = ProgramRepository.shared
    private let nutritionRepository = NutritionRepository.shared

    @Published var feedbacks: [PostSessionFeedback] = []
    @Published var userName: String = "Usuario"
    @Published var hasActiveProgram: Bool = false
    @Published var activeProgram: Program? = nil
    @Published var competitionCountdown: CompetitionCountdown? = nil
    @Published var overtrainedMuscles: [String] = []
    @Published var todaySessions: [TodaySessionItem] = []
    @Published var dailyCalorieGoal: Int = 2500
    @Published var dailyProteinGoal: Int = 150
    @Published var dailyCarbGoal: Int = 250
    @Published var dailyFatGoal: Int = 70
    @Published var todayNutritionTotals: HomeNutritionSnapshot = HomeNutritionSnapshot()
    @Published var lastWeight: Double? = nil
    @Published var lastBodyFat: Double? = nil
    @Published var lastMusclePct: Double? = nil
    @Published var heightCm: Double = 170.0
    @Published var starTargetsCount: Int = 0
    @Published var historyCount: Int = 0

    public init() {
        refresh()
    }

    public func loadFeedbacks() {
        Task {
            feedbacks = await AugeRepository.shared.getPostSessionFeedbacks()
            refresh()
        }
    }

    public func refresh() {
        let settings = repository.settings
        userName = settings.username.isEmpty ? "Usuario" : settings.username
        
        let state = repository.activeProgramState
        hasActiveProgram = (state?.status == .ACTIVE)
        let activeId = hasActiveProgram ? state?.programId : nil
        
        let rawActiveProgram = activeId.flatMap { id in repository.programs.first { $0.id == id } }
        
        if let p = rawActiveProgram {
            if feedbacks.isEmpty {
                activeProgram = p
            } else {
                let scaledRecommendations = p.volumeRecommendations.map { rec -> VolumeRecommendation in
                    let adj = VolumeCalculator.calculateVolumeAdjustment(rec.muscleGroup, feedbacks)
                    if adj == 1.0 {
                        return rec
                    } else {
                        return rec.copy(
                            minEffectiveVolume: Int(round(Double(rec.minEffectiveVolume) * adj)),
                            maxAdaptiveVolume: Int(round(Double(rec.maxAdaptiveVolume) * adj)),
                            maxRecoverableVolume: Int(round(Double(rec.maxRecoverableVolume) * adj))
                        )
                    }
                }
                activeProgram = p.copy(volumeRecommendations: scaledRecommendations)
            }
        } else {
            activeProgram = nil
        }

        competitionCountdown = activeProgram.flatMap { buildCompetitionCountdown(program: $0) }
        overtrainedMuscles = calculateOvertrainedMuscles()
        todaySessions = resolveTodaySessions()

        let plan = nutritionRepository.activeNutritionPlan
        let goals = deriveMacroGoals(settings: settings, activePlan: plan)
        dailyCalorieGoal = goals.calorieGoal
        dailyProteinGoal = goals.proteinGoal
        dailyCarbGoal = goals.carbGoal
        dailyFatGoal = goals.fatGoal

        calculateNutritionTotals()

        lastWeight = settings.userVitals.weight ?? nutritionRepository.bodyMeasurements.max(by: { $0.date < $1.date })?.weight
        lastBodyFat = settings.userVitals.bodyFatPercentage ?? nutritionRepository.bodyMeasurements.max(by: { $0.date < $1.date })?.bodyFat
        lastMusclePct = settings.userVitals.muscleMassPercentage ?? nutritionRepository.bodyMeasurements.max(by: { $0.date < $1.date })?.muscleMass
        heightCm = settings.userVitals.height ?? 170.0

        starTargetsCount = calculateStarTargets()
        historyCount = repository.history.count
    }

    public func getGreeting() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        if hour < 12 {
            return "¡Buenos días"
        } else if hour < 19 {
            return "¡Buenas tardes"
        } else {
            return "¡Buenas noches"
        }
    }

    private func calculateNutritionTotals() {
        let todayStr = ISO8601DateFormatter().string(from: Date()).prefix(10) // YYYY-MM-DD
        var calories = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fats = 0.0

        nutritionRepository.nutritionLogs.forEach { log in
            if log.date.prefix(10) == todayStr && log.status != .PLANNED {
                log.foods.forEach { food in
                    calories += food.calories
                    protein += food.protein
                    carbs += food.carbs
                    fats += food.fats
                }
            }
        }

        todayNutritionTotals = HomeNutritionSnapshot(
            calories: calories,
            protein: protein,
            carbs: carbs,
            fats: fats
        )
    }

    public func computeImc(weightKg: Double, heightCm: Double) -> Double? {
        guard weightKg > 0, heightCm > 0 else { return nil }
        let h = heightCm / 100.0
        return Double(Int(weightKg / (h * h) * 10)) / 10.0
    }

    public func computeFfmiInterpretation(weightKg: Double, heightCm: Double, bodyFatPct: Double) -> String? {
        guard weightKg > 0, heightCm > 0, bodyFatPct >= 0 else { return nil }
        let lbm = weightKg * (1.0 - bodyFatPct / 100.0)
        let h = heightCm / 100.0
        let normalizedFfmi = (lbm / (h * h)) + 6.1 * (1.8 - h)
        if normalizedFfmi >= 26 {
            return "Superior/Elite"
        } else if normalizedFfmi >= 22 {
            return "Excelente"
        } else if normalizedFfmi >= 20 {
            return "Promedio"
        } else {
            return "Novato"
        }
    }

    public func computeNormalizedFfmi(weightKg: Double, heightCm: Double, bodyFatPct: Double) -> Double? {
        guard weightKg > 0, heightCm > 0, bodyFatPct >= 0 else { return nil }
        let lbm = weightKg * (1.0 - bodyFatPct / 100.0)
        let h = heightCm / 100.0
        return Double(Int(((lbm / (h * h)) + 6.1 * (1.8 - h)) * 10)) / 10.0
    }

    private func calculateStarTargets() -> Int {
        guard let program = activeProgram else { return 0 }
        var count = 0
        program.macrocycles.forEach { macro in
            macro.blocks.forEach { block in
                block.mesocycles.forEach { meso in
                    meso.weeks.forEach { week in
                        week.sessions.forEach { session in
                            let exercises = session.parts.isEmpty ? session.exercises : session.parts.flatMap { $0.exercises }
                            exercises.forEach { if $0.isStarTarget == true { count += 1 } }
                        }
                    }
                }
            }
        }
        return count
    }

    private func calculateOvertrainedMuscles() -> [String] {
        guard let p = activeProgram else { return [] }
        let logs = repository.history.filter { $0.programId == p.id }
        var overtrainedList: [String] = []
        let exerciseList = catalogExerciseIndex().values.map { $0 }
        
        let weeksCount = max(1, logs.count / 3)
        let completedVolumes = VolumeCalculator.calculateCompletedWeeklyMuscleVolume(
            logs: logs,
            exerciseList: exerciseList,
            weeksCount: weeksCount
        )

        p.volumeRecommendations.forEach { rec in
            let muscle = rec.muscleGroup
            let canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
            let mrv = Double(rec.maxRecoverableVolume)

            let completedSets = completedVolumes.first { $0.muscleName == canonical }?.weeklySets ?? 0.0
            let factorVol = completedSets > mrv

            var factorProg = false
            let normalizedMuscleLower = canonical.lowercased()
            let factorPain = logs.prefix(5).contains { log in
                log.discomforts.contains { d in
                    let dl = d.lowercased()
                    return dl.contains(normalizedMuscleLower) ||
                           (normalizedMuscleLower.contains("hombro") && dl.contains("deltoid")) ||
                           (normalizedMuscleLower.contains("cuádriceps") && dl.contains("rodilla")) ||
                           (normalizedMuscleLower.contains("espalda baja") && dl.contains("lumbar"))
                }
            }

            let factorSystemic = logs.first?.fatigueLevel ?? 0 >= 8

            let muscleLogs = feedbacks.filter { fb in
                fb.muscleFeedback.keys.contains { key in
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key).lowercased() == normalizedMuscleLower
                }
            }
            let recentFbs = Array(muscleLogs.prefix(3))

            var totalDoms = 0.0
            var totalStr = 0.0
            var fbCount = 0
            recentFbs.forEach { fb in
                if let entryKey = fb.muscleFeedback.keys.first(where: { VolumeCalculator.normalizeCanonicalMuscleGroup($0).lowercased() == normalizedMuscleLower }),
                   let entry = fb.muscleFeedback[entryKey] {
                    totalDoms += Double(entry.doms)
                    totalStr += Double(entry.strengthCapacity)
                    fbCount += 1
                }
            }
            let factorLocal = fbCount > 0 && ((totalDoms / Double(fbCount)) >= 3.5 || (totalStr / Double(fbCount)) <= 5.0)

            let primaryExercises = exerciseList.filter { db in
                db.involvedMuscles.contains {
                    $0.role == .PRIMARY &&
                    VolumeCalculator.normalizeCanonicalMuscleGroup($0.muscle).lowercased() == normalizedMuscleLower
                }
            }.map { $0.id.lowercased() }

            var hasWeightDrop = false
            let exercisesWithLogs = logs.flatMap { $0.completedExercises }
                .filter { primaryExercises.contains($0.exerciseDbId?.lowercased() ?? "") }
            
            let grouped = Dictionary(grouping: exercisesWithLogs, by: { $0.exerciseDbId?.lowercased() ?? "" })
            for (_, exLogs) in grouped {
                if exLogs.count >= 2 {
                    let recentWeight = exLogs.first?.sets.first { !$0.skipped }?.weight ?? 0.0
                    let olderWeight = exLogs.last?.sets.first { !$0.skipped }?.weight ?? 0.0
                    if recentWeight < olderWeight && recentWeight > 0.0 {
                        hasWeightDrop = true
                        break
                    }
                }
            }
            if hasWeightDrop {
                factorProg = true
            }

            var activeCount = 0
            if factorVol { activeCount += 1 }
            if factorPain { activeCount += 1 }
            if factorSystemic { activeCount += 1 }
            if factorLocal { activeCount += 1 }
            if factorProg { activeCount += 1 }

            if activeCount >= 3 {
                overtrainedList.append(canonical)
            }
        }

        return overtrainedList
    }

    private func resolveTodaySessions() -> [TodaySessionItem] {
        guard let active = repository.activeProgramState,
              let program = repository.programs.first(where: { $0.id == active.programId }) else {
            return []
        }
        let currentDay = currentDayOfWeek()
        let weekLocation = resolveWeekLocation(program: program, active: active, dayOfWeek: currentDay) ?? WeekLocation(macroIndex: 0, blockIndex: 0, mesocycleIndex: 0, week: program.macrocycles.first?.blocks.first?.mesocycles.first?.weeks.first ?? ProgramWeek(id: "w1", name: "Semana 1"))
        
        let locations = allWeekLocations(program: program)
        var resolvedWeekLocation = weekLocation

        if let currentIndex = locations.firstIndex(where: { $0.week.id == weekLocation.week.id }) {
            var tempIndex = currentIndex
            while tempIndex < locations.count {
                let currentLoc = locations[tempIndex]
                let allCompleted = currentLoc.week.sessions.allSatisfy { session in
                    repository.history.contains { log in
                        log.sessionId == session.id && (log.weekId == currentLoc.week.id || log.date.prefix(10) == ISO8601DateFormatter().string(from: Date()).prefix(10))
                    }
                }
                if allCompleted {
                    tempIndex += 1
                    if tempIndex < locations.count {
                        resolvedWeekLocation = locations[tempIndex]
                    }
                } else {
                    resolvedWeekLocation = currentLoc
                    break
                }
            }
        }

        let sessions = resolvedWeekLocation.week.sessions
        // Session has no dayOfWeek/assignedDays in Swift model — map all sessions as today
        let isTodayMap = sessions.reduce(into: [String: Bool]()) { dict, session in
            dict[session.id] = true
        }

        return sessions.map { session -> TodaySessionItem in
            let matchingLog = repository.history.first { log in
                log.sessionId == session.id && (log.weekId == resolvedWeekLocation.week.id || log.date.prefix(10) == ISO8601DateFormatter().string(from: Date()).prefix(10))
            }
            return TodaySessionItem(
                session: session,
                program: program,
                location: SessionLocation(
                    macroIndex: resolvedWeekLocation.macroIndex,
                    mesoIndex: resolvedWeekLocation.mesocycleIndex,
                    weekId: resolvedWeekLocation.week.id
                ),
                isCompleted: matchingLog != nil,
                dayOfWeek: currentDay,
                log: matchingLog,
                isOngoing: repository.ongoingWorkout?.programId == program.id && repository.ongoingWorkout?.session.id == session.id
            )
        }.sorted { a, b in
            if a.isOngoing != b.isOngoing {
                return a.isOngoing ? true : false
            }
            if a.isCompleted != b.isCompleted {
                return a.isCompleted ? false : true
            }
            if isTodayMap[a.session.id] != isTodayMap[b.session.id] {
                return isTodayMap[a.session.id] == true ? true : false
            }
            if a.dayOfWeek != b.dayOfWeek {
                return a.dayOfWeek < b.dayOfWeek
            }
            return false // Session has no isMainSession in Swift model
        }
    }

    private struct WeekLocation {
        let macroIndex: Int
        let blockIndex: Int
        let mesocycleIndex: Int
        let week: ProgramWeek
    }

    private func allWeekLocations(program: Program) -> [WeekLocation] {
        var locations: [WeekLocation] = []
        var mesoIndex = 0
        program.macrocycles.enumerated().forEach { macroIdx, macro in
            macro.blocks.enumerated().forEach { blockIdx, block in
                block.mesocycles.forEach { meso in
                    meso.weeks.forEach { week in
                        locations.append(WeekLocation(
                            macroIndex: macroIdx,
                            blockIndex: blockIdx,
                            mesocycleIndex: mesoIndex,
                            week: week
                        ))
                    }
                    mesoIndex += 1
                }
            }
        }
        return locations
    }

    private func resolveWeekLocation(
        program: Program,
        active: ActiveProgramState?,
        dayOfWeek: Int
    ) -> WeekLocation? {
        let locations = allWeekLocations(program: program)
        if locations.isEmpty { return nil }

        if let state = active, state.programId == program.id {
            if let exact = locations.first(where: {
                $0.macroIndex == state.currentMacrocycleIndex &&
                $0.blockIndex == state.currentBlockIndex &&
                $0.mesocycleIndex == state.currentMesocycleIndex &&
                $0.week.id == state.currentWeekId
            }) {
                return exact
            }
            if let sameContainer = locations.first(where: {
                $0.macroIndex == state.currentMacrocycleIndex &&
                $0.blockIndex == state.currentBlockIndex &&
                $0.mesocycleIndex == state.currentMesocycleIndex
            }) {
                return sameContainer
            }
        }

        return locations.first { loc in
            !loc.week.sessions.isEmpty
        } ?? locations.first
    }

    private func currentDayOfWeek() -> Int {
        let weekday = Calendar.current.component(.weekday, from: Date())
        if weekday == 1 {
            return 7
        } else {
            return weekday - 1
        }
    }

    private func buildCompetitionCountdown(program: Program) -> CompetitionCountdown? {
        if program.isSimpleTemporalProgram { return nil }
        guard let keyDate = program.keyDates.first(where: { $0.type == .COMPETITION }) else { return nil }
        
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        
        guard let competitionDate = formatter.date(from: keyDate.eventDate ?? keyDate.startDate) else { return nil }
        let weekStart = formatter.date(from: keyDate.startDate)
        let weekEnd = keyDate.endDate.flatMap { formatter.date(from: $0) }
        
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let compDay = calendar.startOfDay(for: competitionDate)
        
        let daysComponents = calendar.dateComponents([.day], from: today, to: compDay)
        let daysUntil = daysComponents.day ?? 0

        let outputFormatter = DateFormatter()
        outputFormatter.locale = Locale(identifier: "es_CL")
        outputFormatter.dateFormat = "d MMM yyyy"

        let dateLabel = outputFormatter.string(from: competitionDate)
        let countdownLabel = formatCountdown(days: daysUntil)

        let weekLabel: String?
        if let start = weekStart, let end = weekEnd {
            weekLabel = "\(outputFormatter.string(from: start)) → \(outputFormatter.string(from: end))"
        } else {
            weekLabel = nil
        }

        return CompetitionCountdown(
            programId: program.id,
            programName: program.name,
            competitionDate: formatter.string(from: competitionDate),
            competitionDateLabel: dateLabel,
            daysUntil: daysUntil,
            countdownLabel: countdownLabel,
            competitionWeekLabel: weekLabel
        )
    }

    private func formatCountdown(days: Int) -> String {
        if days < 0 {
            return "Hace \(abs(days)) días"
        } else if days == 0 {
            return "Hoy"
        } else if days == 1 {
            return "1 día"
        } else if days < 7 {
            return "\(days) días"
        } else {
            let weeks = days / 7
            let rest = days % 7
            if rest == 0 {
                return "\(weeks) semanas"
            } else {
                return "\(weeks) sem \(rest) días"
            }
        }
    }

    public func getRelativeStrengthData() -> RelativeStrengthData {
        let squat = findBest1RM(patterns: ["sentadilla", "squat"])
        let bench = findBest1RM(patterns: ["press banca", "bench press"])
        let deadlift = findBest1RM(patterns: ["peso muerto", "deadlift"])
        let total = squat + bench + deadlift
        let bw = repository.settings.userVitals.weight ?? 0.0
        return RelativeStrengthData(
            squatRM: squat,
            benchRM: bench,
            deadliftRM: deadlift,
            totalKg: total,
            relativeStrength: bw > 0 ? total / bw : 0.0
        )
    }

    private func findBest1RM(patterns: [String]) -> Double {
        var best = 0.0
        repository.history.forEach { log in
            log.completedExercises.forEach { ex in
                if patterns.contains(where: { ex.exerciseName.lowercased().contains($0) }) {
                    ex.sets.forEach { s in
                        let rm = calculateBrzycki1RM(s.weight, reps: s.reps)
                        if rm > best {
                            best = rm
                        }
                    }
                }
            }
        }
        return best
    }

    private func calculateBrzycki1RM(_ weight: Double, reps: Int) -> Double {
        if reps <= 0 { return 0.0 }
        if reps == 1 { return weight }
        return weight / (1.0278 - 0.0278 * Double(reps))
    }

    public func getIpfGlPoints() -> Double {
        // IPF GL calculation not yet ported — returns 0 as placeholder
        return 0.0
    }
}

// MARK: - Supporting Types

public struct RelativeStrengthData {
    public let squatRM: Double
    public let benchRM: Double
    public let deadliftRM: Double
    public let totalKg: Double
    public let relativeStrength: Double
}

public struct HomeNutritionSnapshot {
    public var calories: Double = 0.0
    public var protein: Double = 0.0
    public var carbs: Double = 0.0
    public var fats: Double = 0.0
}

// MARK: - KT Translation Additions

private extension Session {
    func matchesDay(dayOfWeek: Int) -> Bool {
        self.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)
    }
}

extension HomeViewModel {
    public func computeIpfGlPoints() -> Double {
        let strength = getRelativeStrengthData()
        let bw = repository.settings.userVitals.weight ?? 0.0
        guard strength.totalKg > 0, bw > 0 else { return 0.0 }
        let gender: String = {
            switch repository.settings.userVitals.gender {
            case .FEMALE: return "female"
            default: return "male"
            }
        }()
        return calculateIPFGLPoints(
            totalLifted: strength.totalKg,
            bodyWeight: bw,
            gender: gender,
            equipment: .classic
        )
    }
}
