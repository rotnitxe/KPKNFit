import Foundation
import Combine

// Bridge between internal AppSettings (from ProgramRepository) and Settings (used by AugeRecoveryEngine)
private extension AppSettings {
    func toSettings() -> Settings {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(self),
              let s = try? JSONDecoder().decode(Settings.self, from: data) else {
            return Settings()
        }
        return s
    }
}

@MainActor
final class AugeViewModel: ObservableObject {

    private let augeRepo = AugeRepository.shared
    private let programRepo = ProgramRepository.shared
    private let exerciseDb = catalogExerciseIndex()

    private var recoveryTimerTask: Task<Void, Never>?

    private let initialDashboard = RecoveryDashboard(
        overallScore: 50,
        headline: "Calculando...",
        summary: "Cargando datos de recuperación.",
        recommendation: "Espera un momento mientras cargamos tu estado.",
        confidenceLabel: "—",
        channels: [
            RecoveryChannelSnapshot(id: .MUSCULAR, title: "Músculos", shortTitle: "Mús.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu estado muscular.", confidence: 0),
            RecoveryChannelSnapshot(id: .SYSTEM, title: "Energía", shortTitle: "En.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu energía.", confidence: 0),
            RecoveryChannelSnapshot(id: .STRUCTURE, title: "Columna", shortTitle: "Col.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu columna.", confidence: 0),
        ]
    )

    @Published var snapshot: AugeSnapshot = AugeSnapshot(
        batteries: GlobalBatteries(muscular: 50, cnc: 50, spinal: 50),
        perMuscle: [:],
        readiness: nil,
        dashboard: RecoveryDashboard(
            overallScore: 50, headline: "Calculando...",
            summary: "Cargando datos de recuperación.",
            recommendation: "Espera un momento mientras cargamos tu estado.",
            confidenceLabel: "—",
            channels: [
                RecoveryChannelSnapshot(id: .MUSCULAR, title: "Músculos", shortTitle: "Mús.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu estado muscular.", confidence: 0),
                RecoveryChannelSnapshot(id: .SYSTEM, title: "Energía", shortTitle: "En.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu energía.", confidence: 0),
                RecoveryChannelSnapshot(id: .STRUCTURE, title: "Columna", shortTitle: "Col.", score: 50, band: .MODERATE, description: "Calculando...", action: "Espera mientras cargamos tu columna.", confidence: 0),
            ]
        ),
        articular: [:],
        shouldSuggestAutoDeload: false,
        cumulativeFatigue: 0.0,
        autoDeloadMessage: nil,
        isLoading: true
    )

    @Published var pendingQuestionnaire: PendingQuestionnaire?

    static let shared = AugeViewModel()
    private init() {
        Task { await initialLoad() }
    }

    private func initialLoad() async {
        await recompute()
        startRecoveryTimer()
    }

    private func startRecoveryTimer() {
        recoveryTimerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 300_000_000_000)
                await self?.refresh()
            }
        }
    }

    deinit {
        recoveryTimerTask?.cancel()
    }

    // ─── Core recompute ───────────────────────────────────────────────────

    private func recompute() async {
        snapshot = AugeSnapshot(
            batteries: snapshot.batteries,
            perMuscle: snapshot.perMuscle,
            readiness: snapshot.readiness,
            dashboard: snapshot.dashboard,
            articular: snapshot.articular,
            shouldSuggestAutoDeload: snapshot.shouldSuggestAutoDeload,
            cumulativeFatigue: snapshot.cumulativeFatigue,
            autoDeloadMessage: snapshot.autoDeloadMessage,
            isLoading: true
        )

        let history = programRepo.history
        let appSettings = programRepo.settings
        let settings = appSettings.toSettings()

        async let todayWellbeing = augeRepo.getTodayWellbeing()
        async let overrideWellbeing = augeRepo.getActiveWellbeingWithManualOverrides()
        async let feedbacks = augeRepo.getPostSessionFeedbacks()
        async let sleepLogs = augeRepo.getLastNSleepLogs(n: 7)
        let nutritionLogs: [NutritionLog] = []
        async let adaptiveCache = augeRepo.getAdaptiveCache()

        let wellbeing = await {
            let tw = await todayWellbeing
            let ow = await overrideWellbeing
            if let ow = ow,
               tw?.manualNeuralBattery == nil,
               tw?.manualSpinalBattery == nil,
               tw?.manualMuscularBattery == nil {
                return ow
            }
            return tw
        }()

        let resolvedFeedbacks = await feedbacks
        let resolvedSleepLogs = await sleepLogs
        let resolvedCache = await adaptiveCache

        let muscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, sleepLogs: resolvedSleepLogs,
            nutritionLogs: nutritionLogs, feedbacks: resolvedFeedbacks,
            adaptiveCache: resolvedCache
        )
        let articular = AugeTtcEngine.calculateArticularBatteries(
            history: history, exerciseDb: exerciseDb,
            feedbacks: resolvedFeedbacks, wellbeing: wellbeing
        )
        let bat = AugeRecoveryEngine.calculateGlobalBatteries(
            history: history, wellbeing: wellbeing, settings: settings,
            exerciseDb: exerciseDb, sleepLogs: resolvedSleepLogs,
            nutritionLogs: nutritionLogs, feedbacks: resolvedFeedbacks,
            adaptiveCache: resolvedCache, precomputedMuscles: muscles,
            articularBatteries: articular
        )
        let dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries: bat, perMuscle: muscles,
            articularBatteries: articular, wellbeing: wellbeing,
            sleepLogs: resolvedSleepLogs, recentSessionCount: history.count
        )
        let verdict = AugeRecoveryEngine.calculateDailyReadiness(
            dashboard: dashboard, wellbeing: wellbeing
        )
        let pending = AugeRecoveryEngine.checkPendingSurveys(
            history: history, feedbacks: resolvedFeedbacks
        )

        let twoWeeksAgoMs = Date().timeIntervalSince1970 * 1000 - 14 * 24 * 3600 * 1000
        let cumFatigue = history
            .filter { log in Double(AugeUtils.logDateMs(log: log)) >= twoWeeksAgoMs }
            .reduce(0.0) { acc, log in
                acc + AugeFatigueEngine.calculateCompletedSessionStress(
                    completedExercises: log.completedExercises,
                    exerciseDb: exerciseDb, settings: appSettings,
                    adaptiveCache: resolvedCache
                )
            }

        let resolvedPending = pending ?? await augeRepo.getPendingQuestionnaire()
        exposePendingIfDue(q: resolvedPending)

        let readinessScore = verdict?.score ?? dashboard.overallScore
        let shouldSuggest = AugeFatigueEngine.shouldSuggestAutoDeload(
            cumulativeFatigue: cumFatigue, readinessScore: readinessScore,
            settings: appSettings
        )
        let deloadMsg = shouldSuggest
            ? "Fatiga alta detectada: considera una semana de descarga para recuperar mejor."
            : nil

        snapshot = AugeSnapshot(
            batteries: bat,
            perMuscle: muscles,
            readiness: verdict,
            dashboard: dashboard,
            articular: articular,
            shouldSuggestAutoDeload: shouldSuggest,
            cumulativeFatigue: cumFatigue,
            autoDeloadMessage: deloadMsg,
            isLoading: false
        )
    }

    private func exposePendingIfDue(q: PendingQuestionnaire?) {
        guard let q = q else {
            if pendingQuestionnaire != nil { pendingQuestionnaire = nil }
            return
        }
        let remaining = q.scheduledTimeMs - Int64(Date().timeIntervalSince1970 * 1000)
        if remaining <= 0 {
            if pendingQuestionnaire?.logId != q.logId { pendingQuestionnaire = q }
        } else {
            pendingQuestionnaire = nil
            Task {
                let clamped = min(remaining, 24 * 60 * 60 * 1000)
                try? await Task.sleep(nanoseconds: UInt64(clamped) * 1_000_000)
                let stillPending = await augeRepo.getPendingQuestionnaire()
                if let sp = stillPending,
                   Date().timeIntervalSince1970 * 1000 >= Double(sp.scheduledTimeMs) {
                    pendingQuestionnaire = sp
                }
            }
        }
    }

    // ─── Public actions ───────────────────────────────────────────────────

    func saveWellbeing(log: DailyWellbeingLog) {
        Task {
            let anchored: DailyWellbeingLog
            if log.manualNeuralBattery != nil || log.manualSpinalBattery != nil || !log.manualMuscleBatteries.isEmpty {
                anchored = DailyWellbeingLog(
                    id: log.id, date: log.date,
                    sleepQuality: log.sleepQuality, stressLevel: log.stressLevel,
                    doms: log.doms, motivation: log.motivation,
                    sleepHours: log.sleepHours, moodState: log.moodState,
                    workIntensity: log.workIntensity, studyIntensity: log.studyIntensity,
                    manualMuscularBattery: log.manualMuscularBattery,
                    manualNeuralBattery: log.manualNeuralBattery,
                    manualSpinalBattery: log.manualSpinalBattery,
                    manualMuscleBatteries: log.manualMuscleBatteries,
                    manualBatteryAnchorMs: Int64(Date().timeIntervalSince1970 * 1000),
                    notes: log.notes,
                    preWorkoutDiscomforts: log.preWorkoutDiscomforts
                )
            } else {
                anchored = log
            }
            await augeRepo.saveWellbeingLog(log: anchored)

            if anchored.manualNeuralBattery != nil || anchored.manualSpinalBattery != nil || !anchored.manualMuscleBatteries.isEmpty {
                let perMuscleInt = snapshot.perMuscle.mapValues { $0.recoveryScore }
                await learnFromManualAdjustment(
                    manualNeural: anchored.manualNeuralBattery,
                    manualSpinal: anchored.manualSpinalBattery,
                    manualMuscleBatteries: anchored.manualMuscleBatteries,
                    sessionCnsDrain: 0, sessionSpinalDrain: 0, sessionMuscleDrain: 0,
                    predictedNeuralBattery: snapshot.ringScore(id: .SYSTEM),
                    predictedSpinalBattery: snapshot.ringScore(id: .STRUCTURE),
                    predictedMuscleBatteries: perMuscleInt,
                    wellbeing: anchored
                )
            }
            await recompute()
        }
    }

    func savePostSessionFeedback(fb: PostSessionFeedback) {
        Task {
            await augeRepo.savePostSessionFeedback(fb: fb)
            await augeRepo.clearPendingQuestionnaire()
            pendingQuestionnaire = nil
            await recompute()
        }
    }

    func schedulePendingQuestionnaire(q: PendingQuestionnaire) {
        Task {
            await augeRepo.setPendingQuestionnaire(q: q)
            exposePendingIfDue(q: q)
        }
    }

    func dismissPendingQuestionnaire() {
        Task {
            await augeRepo.clearPendingQuestionnaire()
            pendingQuestionnaire = nil
        }
    }

    func refresh() {
        Task { await recompute() }
    }

    func applyManualBatteries(
        neural: Int, muscular: Int? = nil, spinal: Int,
        perMuscle: [String: Int],
        manualBatteryAnchorMs: Int64? = nil,
        sessionCnsDrain: Double = 0, sessionSpinalDrain: Double = 0, sessionMuscleDrain: Double = 0,
        predictedNeuralBattery: Int? = nil, predictedSpinalBattery: Int? = nil,
        predictedMuscleBatteries: [String: Int] = [:]
    ) {
        Task {
            let base = await augeRepo.getTodayWellbeing()
            let derivedMuscular: Int? = {
                if let m = muscular { return m }
                let vals = perMuscle.values
                return vals.isEmpty ? nil : max(0, min(100, Int(vals.reduce(0, +)) / vals.count))
            }()
            let dateStr: String = {
                let f = ISO8601DateFormatter()
                f.formatOptions = [.withFullDate]
                return f.string(from: Date())
            }()
            let updated = DailyWellbeingLog(
                id: base?.id ?? UUID().uuidString,
                date: dateStr,
                sleepQuality: base?.sleepQuality ?? 3,
                stressLevel: base?.stressLevel ?? 3,
                doms: base?.doms ?? 1,
                motivation: base?.motivation ?? 3,
                sleepHours: base?.sleepHours ?? 7.5,
                moodState: base?.moodState,
                workIntensity: base?.workIntensity,
                studyIntensity: base?.studyIntensity,
                manualMuscularBattery: derivedMuscular,
                manualNeuralBattery: max(0, min(100, neural)),
                manualSpinalBattery: max(0, min(100, spinal)),
                manualMuscleBatteries: perMuscle.mapValues { max(0, min(100, $0)) },
                manualBatteryAnchorMs: manualBatteryAnchorMs ?? Int64(Date().timeIntervalSince1970 * 1000),
                notes: base?.notes
            )
            await augeRepo.saveWellbeingLog(log: updated)

            await learnFromManualAdjustment(
                manualNeural: neural, manualSpinal: spinal,
                manualMuscleBatteries: perMuscle,
                sessionCnsDrain: sessionCnsDrain, sessionSpinalDrain: sessionSpinalDrain,
                sessionMuscleDrain: sessionMuscleDrain,
                predictedNeuralBattery: predictedNeuralBattery,
                predictedSpinalBattery: predictedSpinalBattery,
                predictedMuscleBatteries: predictedMuscleBatteries,
                wellbeing: updated
            )
            await recompute()
        }
    }

    private func learnFromManualAdjustment(
        manualNeural: Int?, manualSpinal: Int?,
        manualMuscleBatteries: [String: Int],
        sessionCnsDrain: Double, sessionSpinalDrain: Double, sessionMuscleDrain: Double,
        predictedNeuralBattery: Int?, predictedSpinalBattery: Int?,
        predictedMuscleBatteries: [String: Int],
        wellbeing: DailyWellbeingLog
    ) async {
        let hasSystem = (manualNeural != nil && predictedNeuralBattery != nil)
            || (manualSpinal != nil && predictedSpinalBattery != nil)
        let hasMuscle = !manualMuscleBatteries.isEmpty
        guard hasSystem || hasMuscle else { return }

        let appSettings = programRepo.settings
        let settings = appSettings.toSettings()
        var cache = await augeRepo.getAdaptiveCache()
        var obsCount = 0

        if let mn = manualNeural, let pn = predictedNeuralBattery {
            let adj = max(-50, min(50, mn - pn))
            let (newCns, _) = AugeAdaptiveEngine.updateSystemLearningDeltas(
                currentCnsDelta: cache.cnsLearningDelta,
                currentSpinalDelta: cache.spinalLearningDelta,
                systemAdjustment: adj, structureAdjustment: nil,
                totalObservations: cache.totalObservations
            )
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: cache.muscleDeltas,
                cnsLearningDelta: newCns,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: cache.muscleDrainMultipliers,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            obsCount += 1
        }

        if let ms = manualSpinal, let ps = predictedSpinalBattery {
            let adj = max(-50, min(50, ms - ps))
            let (_, newSpinal) = AugeAdaptiveEngine.updateSystemLearningDeltas(
                currentCnsDelta: cache.cnsLearningDelta,
                currentSpinalDelta: cache.spinalLearningDelta,
                systemAdjustment: nil, structureAdjustment: adj,
                totalObservations: cache.totalObservations
            )
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: cache.muscleDeltas,
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: newSpinal,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: cache.muscleDrainMultipliers,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            obsCount += 1
        }

        let history = programRepo.history
        let lastSession = history.max(by: { AugeUtils.logDateMs(log: $0) < AugeUtils.logDateMs(log: $1) })

        let preWorkoutNeural = (predictedNeuralBattery ?? 100) + Int(sessionCnsDrain)
        let preWorkoutSpinal = (predictedSpinalBattery ?? 100) + Int(sessionSpinalDrain)

        if sessionCnsDrain > 0 || sessionSpinalDrain > 0 || sessionMuscleDrain > 0 {
            let (newCnsM, newSpinM, newMuscM) = AugeAdaptiveEngine.updateDrainMultipliers(
                currentCnsMult: cache.cnsDrainMultiplier,
                currentSpinalMult: cache.spinalDrainMultiplier,
                currentMuscleMults: cache.muscleDrainMultipliers,
                manualNeural: manualNeural, manualSpinal: manualSpinal,
                manualMuscleBatteries: manualMuscleBatteries,
                predictedNeural: predictedNeuralBattery,
                predictedSpinal: predictedSpinalBattery,
                predictedMuscleBatteries: predictedMuscleBatteries,
                preWorkoutNeural: preWorkoutNeural, preWorkoutSpinal: preWorkoutSpinal,
                preWorkoutMuscleBatteries: [:],
                totalObservations: cache.totalObservations
            )
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: cache.muscleDeltas,
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: newCnsM,
                spinalDrainMultiplier: newSpinM,
                muscleDrainMultipliers: newMuscM,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            obsCount += 1
        }

        let nowMs = Date().timeIntervalSince1970 * 1000
        let derivedHoursSince: Double = {
            guard let last = lastSession else { return 24.0 }
            return max(0.5, (nowMs - Double(AugeUtils.logDateMs(log: last))) / 3_600_000.0)
        }()

        let cnsObs: RecoveryLearningObservation? = {
            guard let mn = manualNeural, let pn = predictedNeuralBattery, pn != mn else { return nil }
            return RecoveryLearningObservation(
                muscle: "cns", predictedBattery: pn, actualBattery: mn,
                sessionStress: sessionCnsDrain > 0 ? sessionCnsDrain : 20,
                hoursSinceSession: sessionCnsDrain > 0 ? 0.25 : derivedHoursSince,
                sleepQuality: wellbeing.sleepQuality,
                stressLevel: wellbeing.stressLevel
            )
        }()

        let spinalObs: RecoveryLearningObservation? = {
            guard let ms = manualSpinal, let ps = predictedSpinalBattery, ps != ms else { return nil }
            return RecoveryLearningObservation(
                muscle: "spinal", predictedBattery: ps, actualBattery: ms,
                sessionStress: sessionSpinalDrain > 0 ? sessionSpinalDrain : 20,
                hoursSinceSession: sessionSpinalDrain > 0 ? 0.25 : derivedHoursSince,
                sleepQuality: wellbeing.sleepQuality,
                stressLevel: wellbeing.stressLevel
            )
        }()

        if cnsObs != nil || spinalObs != nil {
            let (newCnsTau, newSpinTau) = AugeAdaptiveEngine.updateSystemRecoveryHours(
                currentCnsTau: cache.cnsRecoveryHours,
                currentSpinalTau: cache.spinalRecoveryHours,
                cnsObservation: cnsObs, spinalObservation: spinalObs,
                totalObservations: cache.totalObservations
            )
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: cache.muscleDeltas,
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: newCnsTau,
                spinalRecoveryHours: newSpinTau,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: cache.muscleDrainMultipliers,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            if cnsObs != nil { obsCount += 1 }
            if spinalObs != nil { obsCount += 1 }
        }

        for (muscle, manualBat) in manualMuscleBatteries {
            let predicted = predictedMuscleBatteries[muscle]
                ?? predictedMuscleBatteries.first(where: { $0.key.caseInsensitiveCompare(muscle) == .orderedSame })?.value
                ?? 100
            guard predicted != manualBat else { continue }
            let obs = RecoveryLearningObservation(
                muscle: muscle, predictedBattery: predicted,
                actualBattery: max(0, min(100, manualBat)),
                sessionStress: sessionMuscleDrain > 0 ? sessionMuscleDrain : 20,
                hoursSinceSession: sessionMuscleDrain > 0 ? 0.25 : derivedHoursSince,
                sleepQuality: wellbeing.sleepQuality,
                stressLevel: wellbeing.stressLevel
            )
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: AugeAdaptiveEngine.updatePersonalizedRecoveryHours(
                    current: cache.personalizedRecoveryHours,
                    observation: obs,
                    totalObservations: cache.totalObservations
                ),
                muscleDeltas: cache.muscleDeltas,
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: cache.muscleDrainMultipliers,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            obsCount += 1
        }

        if !predictedMuscleBatteries.isEmpty && !manualMuscleBatteries.isEmpty {
            cache = AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: AugeAdaptiveEngine.updateMuscleDeltas(
                    current: cache.muscleDeltas,
                    manualMuscleBatteries: manualMuscleBatteries,
                    predictedMuscleBatteries: predictedMuscleBatteries,
                    totalObservations: cache.totalObservations
                ),
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: cache.muscleDrainMultipliers,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
            let hasDiff = manualMuscleBatteries.contains { (k, v) in (predictedMuscleBatteries[k] ?? 100) != v }
            if hasDiff { obsCount += 1 }
        }

        if obsCount == 0 { obsCount = 1 }
        cache = AugeAdaptiveCache(
            personalizedRecoveryHours: cache.personalizedRecoveryHours,
            muscleDeltas: cache.muscleDeltas,
            cnsLearningDelta: cache.cnsLearningDelta,
            spinalLearningDelta: cache.spinalLearningDelta,
            cnsRecoveryHours: cache.cnsRecoveryHours,
            spinalRecoveryHours: cache.spinalRecoveryHours,
            cnsDrainMultiplier: cache.cnsDrainMultiplier,
            spinalDrainMultiplier: cache.spinalDrainMultiplier,
            muscleDrainMultipliers: cache.muscleDrainMultipliers,
            totalObservations: cache.totalObservations + obsCount,
            lastUpdatedMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        await augeRepo.saveAdaptiveCache(cache: cache)
    }
}
