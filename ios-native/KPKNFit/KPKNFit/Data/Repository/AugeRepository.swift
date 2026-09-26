import Foundation

public final class AugeRepository {

    private init() {}

    public static let shared = AugeRepository()

    private let db = KpknDatabase.instance()

    public static func getInstance() -> AugeRepository { shared }

    public func getWellbeingLogs() async -> [DailyWellbeingLog] {
        let entities = await db.augeDao.getAllWellbeing()
        return entities.compactMap { entity in
            try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(entity.data.utf8))
        }
    }

    public func saveWellbeingLog(log: DailyWellbeingLog) async {
        // Paridad Android (AugeRepository.saveWellbeingLog): se reutiliza el id
        // de la fila ya existente de esa fecha para no duplicar el check-in del
        // día (Android lo exige por la restricción unique(date)).
        let existing = await db.augeDao.getWellbeingForDate(date: log.date)
        var resolved = log
        if let existing,
           let previous = try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(existing.data.utf8)),
           previous.id != log.id {
            resolved = DailyWellbeingLog(
                id: previous.id,
                date: log.date,
                sleepQuality: log.sleepQuality,
                stressLevel: log.stressLevel,
                doms: log.doms,
                motivation: log.motivation,
                sleepHours: log.sleepHours,
                moodState: log.moodState,
                workIntensity: log.workIntensity,
                studyIntensity: log.studyIntensity,
                manualMuscularBattery: log.manualMuscularBattery,
                manualNeuralBattery: log.manualNeuralBattery,
                manualSpinalBattery: log.manualSpinalBattery,
                manualMuscleBatteries: log.manualMuscleBatteries,
                manualMuscleOverridesV2: log.manualMuscleOverridesV2,
                manualBatteryAnchorMs: log.manualBatteryAnchorMs,
                notes: log.notes,
                preWorkoutDiscomforts: log.preWorkoutDiscomforts
            )
        }
        let data = try? JSONEncoder().encode(resolved)
        let entity = WellbeingEntity(id: resolved.id, date: resolved.date, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        await db.augeDao.upsertWellbeing(entity: entity)
    }

    public func getTodayWellbeing() async -> DailyWellbeingLog? {
        let today = IsoDateFormatter.todayString()
        let entity = await db.augeDao.getWellbeingForDate(date: today)
        guard let data = entity?.data else { return nil }
        return try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(data.utf8))
    }

    /// Equivalente de Android `AugeRepository.getActiveWellbeingWithManualOverrides`:
    /// rango ayer→hoy; la fila de ayer solo cuenta si su ancla manual sigue
    /// dentro de ~18 h, y se reconocen TODAS las fuentes manuales (los tres
    /// anillos y los overrides per-muscle V1/V2). Antes iOS usaba un rango de
    /// 3 días, ignoraba los overrides per-muscle y no aplicaba la ventana de
    /// 18 h, de modo que un ajuste caducado seguía mandando.
    public func getActiveWellbeingWithManualOverrides() async -> DailyWellbeingLog? {
        let today = IsoDateFormatter.todayString()
        let yesterday = IsoDateFormatter.dateString(daysAgo: 1)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let logs = await db.augeDao.getWellbeingInRange(from: yesterday, to: today)
        let decoded = logs.compactMap { entity -> DailyWellbeingLog? in
            try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(entity.data.utf8))
        }
        return decoded.first { Self.isActiveManualOverride($0, nowMs: nowMs, today: today) }
    }

    /// ¿Esta fila aporta un ajuste manual ACTIVO hoy? Espejo de la condición
    /// Android: sin fuentes manuales → no; fila de hoy → sí; fila anterior →
    /// solo si `manualBatteryAnchorMs` está dentro de las últimas 18 h (un
    /// anchor ausente/expirado no cuenta, nunca se inventa).
    public static func isActiveManualOverride(_ log: DailyWellbeingLog, nowMs: Int64, today: String) -> Bool {
        let hasManual = log.manualNeuralBattery != nil
            || log.manualMuscularBattery != nil
            || log.manualSpinalBattery != nil
            || !log.manualMuscleBatteries.isEmpty
            || !(log.manualMuscleOverridesV2 ?? [:]).isEmpty
        guard hasManual else { return false }
        if log.date == today { return true }
        return (log.manualBatteryAnchorMs ?? 0) >= nowMs - 18 * 3_600_000
    }

    public func saveSleepLog(log: SleepLog) async {
        let data = try? JSONEncoder().encode(log)
        let entity = SleepLogEntity(id: log.id, date: log.date, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        await db.augeDao.upsertSleepLog(entity: entity)
    }

    public func getLastNSleepLogs(n: Int) async -> [SleepLog] {
        let entities = await db.augeDao.getLastNSleepLogs(n: n)
        return entities.compactMap { entity in
            try? JSONDecoder().decode(SleepLog.self, from: Data(entity.data.utf8))
        }
    }

    public func saveSleepLogExtended(log: SleepLogExtended) async {
        let extendedData = try? JSONEncoder().encode(log)
        let extendedEntity = SleepLogExtendedEntity(
            id: log.id,
            date: log.date,
            data: extendedData.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
        )
        await db.augeDao.upsertSleepLogExtended(entity: extendedEntity)
        let basicLog = log.toSleepLog()
        let basicData = try? JSONEncoder().encode(basicLog)
        let basicEntity = SleepLogEntity(id: basicLog.id, date: basicLog.date, data: basicData.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        await db.augeDao.upsertSleepLog(entity: basicEntity)
    }

    public func getLastNSleepLogsExtended(n: Int) async -> [SleepLogExtended] {
        let entities = await db.augeDao.getLastNSleepLogsExtended(n: n)
        return entities.compactMap { entity in
            try? JSONDecoder().decode(SleepLogExtended.self, from: Data(entity.data.utf8))
        }
    }

    public func getAllSleepLogsExtended() async -> [SleepLogExtended] {
        let entities = await db.augeDao.getAllSleepLogsExtended()
        return entities.compactMap { entity in
            try? JSONDecoder().decode(SleepLogExtended.self, from: Data(entity.data.utf8))
        }
    }

    public func deleteSleepLogExtended(id: String) async {
        await db.augeDao.deleteSleepLogExtended(id: id)
    }

    public func getPostSessionFeedbacks() async -> [PostSessionFeedback] {
        let entities = await db.augeDao.getAllFeedback()
        return entities.compactMap { entity in
            try? JSONDecoder().decode(PostSessionFeedback.self, from: Data(entity.data.utf8))
        }
    }

    public func savePostSessionFeedback(fb: PostSessionFeedback) async {
        let data = try? JSONEncoder().encode(fb)
        let entity = PostSessionFeedbackEntity(logId: fb.logId, date: fb.date, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        await db.augeDao.upsertFeedback(entity: entity)
    }

    public func getFeedbackForLog(logId: String) async -> PostSessionFeedback? {
        let entity = await db.augeDao.getFeedbackForLog(logId: logId)
        guard let data = entity?.data else { return nil }
        return try? JSONDecoder().decode(PostSessionFeedback.self, from: Data(data.utf8))
    }

    public func getPendingQuestionnaire() async -> PendingQuestionnaire? {
        let entity = await db.augeDao.getPendingQuestionnaire()
        guard let data = entity?.data else { return nil }
        return try? JSONDecoder().decode(PendingQuestionnaire.self, from: Data(data.utf8))
    }

    public func setPendingQuestionnaire(q: PendingQuestionnaire) async {
        let data = try? JSONEncoder().encode(q)
        let entity = PendingQuestionnaireEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
        await db.augeDao.upsertPendingQuestionnaire(entity: entity)
    }

    public func clearPendingQuestionnaire() async {
        await db.augeDao.clearPendingQuestionnaire()
    }

    public func getAdaptiveCache() async -> AugeAdaptiveCache {
        let entity = await db.augeDao.getAdaptiveCache()
        guard let data = entity?.data, let cache = try? JSONDecoder().decode(AugeAdaptiveCache.self, from: Data(data.utf8)) else {
            return AugeAdaptiveCache()
        }
        guard (cache.muscularBiasVersion ?? 0) >= 2 else {
            // The previous model mixed global and local muscular learning.
            // Preserve neural/spinal history but fail closed for old muscle
            // deltas/multipliers until fresh v2 finish snapshots exist.
            return AugeAdaptiveCache(
                personalizedRecoveryHours: cache.personalizedRecoveryHours,
                muscleDeltas: [:],
                cnsLearningDelta: cache.cnsLearningDelta,
                spinalLearningDelta: cache.spinalLearningDelta,
                cnsRecoveryHours: cache.cnsRecoveryHours,
                spinalRecoveryHours: cache.spinalRecoveryHours,
                cnsDrainMultiplier: cache.cnsDrainMultiplier,
                spinalDrainMultiplier: cache.spinalDrainMultiplier,
                muscleDrainMultipliers: [:],
                muscularBiasVersion: 2,
                totalObservations: cache.totalObservations,
                lastUpdatedMs: cache.lastUpdatedMs
            )
        }
        return cache
    }

    public func saveAdaptiveCache(cache: AugeAdaptiveCache) async {
        let data = try? JSONEncoder().encode(cache)
        let entity = AugeAdaptiveCacheEntity(rowId: 1, data: data.map { String(decoding: $0, as: UTF8.self) })
        await db.augeDao.upsertAdaptiveCache(entity: entity)
    }
}

private struct IsoDateFormatter {
    static func todayString() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter.string(from: Date())
    }

    static func dateString(daysAgo: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: -daysAgo, to: Date()) ?? Date()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter.string(from: date)
    }
}
