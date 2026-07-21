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
        let data = try? JSONEncoder().encode(log)
        let entity = WellbeingEntity(id: log.id, date: log.date, data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}")
        await db.augeDao.upsertWellbeing(entity: entity)
    }

    public func getTodayWellbeing() async -> DailyWellbeingLog? {
        let today = IsoDateFormatter.todayString()
        let entity = await db.augeDao.getWellbeingForDate(date: today)
        guard let data = entity?.data else { return nil }
        return try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(data.utf8))
    }

    public func getActiveWellbeingWithManualOverrides() async -> DailyWellbeingLog? {
        let today = IsoDateFormatter.todayString()
        let fromDate = IsoDateFormatter.dateString(daysAgo: 2)
        let logs = await db.augeDao.getWellbeingInRange(from: fromDate, to: today)
        let decoded = logs.compactMap { entity -> DailyWellbeingLog? in
            try? JSONDecoder().decode(DailyWellbeingLog.self, from: Data(entity.data.utf8))
        }
        return decoded.first { log in
            log.manualNeuralBattery != nil ||
            log.manualMuscularBattery != nil ||
            log.manualSpinalBattery != nil
        }
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
