import Foundation

public final class CompetitionRepository {

    private let db = KpknDatabase.instance()

    private var _records: [CompetitionRecord] = []
    private let recordsLock = NSLock()

    public var records: [CompetitionRecord] {
        recordsLock.lock()
        defer { recordsLock.unlock() }
        return _records
    }

    private static let recordSorter: (CompetitionRecord, CompetitionRecord) -> Bool = { a, b in
        if a.eventDate ?? "" != b.eventDate ?? "" {
            return (a.eventDate ?? "") > (b.eventDate ?? "")
        }
        return a.updatedAtMs > b.updatedAtMs
    }

    public init() {
        Task {
            await loadPersistedRecords()
        }
    }

    private func loadPersistedRecords() async {
        let entities = await db.competitionRecordDao.getAll()
        let decoded = entities.compactMap { entity -> CompetitionRecord? in
            try? JSONDecoder().decode(CompetitionRecord.self, from: Data(entity.data.utf8))
        }
        recordsLock.lock()
        _records = decoded.sorted(by: Self.recordSorter)
        recordsLock.unlock()
    }

    public func getById(id: String) -> CompetitionRecord? {
        recordsLock.lock()
        defer { recordsLock.unlock() }
        return _records.first { $0.id == id }
    }

    public func getByPlannedSessionId(sessionId: String) -> CompetitionRecord? {
        recordsLock.lock()
        defer { recordsLock.unlock() }
        return _records.first { $0.plannedSessionId == sessionId }
    }

    public func activeCompetitionExerciseIds() -> Set<String> {
        recordsLock.lock()
        defer { recordsLock.unlock() }
        return Self.competitionExerciseIds(_records.filterNotArchived())
    }

    public func upsert(record: CompetitionRecord) {
        let normalized = record.withFreshTimestamps()
        recordsLock.lock()
        let next: [CompetitionRecord]
        if let idx = _records.firstIndex(where: { $0.id == normalized.id }) {
            var updated = _records
            updated[idx] = normalized
            next = updated.sorted(by: Self.recordSorter)
        } else {
            next = (_records + [normalized]).sorted(by: Self.recordSorter)
        }
        _records = next
        recordsLock.unlock()

        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = CompetitionRecordEntity(
                id: normalized.id,
                title: normalized.title,
                eventDate: normalized.eventDate ?? "",
                status: normalized.status.rawValue,
                sportType: normalized.sportType.rawValue,
                plannedSessionId: normalized.plannedSessionId ?? "",
                updatedAtMs: normalized.updatedAtMs,
                data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
            )
            await db.competitionRecordDao.upsert(entity: entity)
        }
        CompetitionReminderManager.schedule(record: normalized)
    }

    public func upsertNow(record: CompetitionRecord) async {
        let normalized = record.withFreshTimestamps()
        recordsLock.lock()
        let next: [CompetitionRecord]
        if let idx = _records.firstIndex(where: { $0.id == normalized.id }) {
            var updated = _records
            updated[idx] = normalized
            next = updated.sorted(by: Self.recordSorter)
        } else {
            next = (_records + [normalized]).sorted(by: Self.recordSorter)
        }
        _records = next
        recordsLock.unlock()

        let data = try? JSONEncoder().encode(normalized)
        let entity = CompetitionRecordEntity(
            id: normalized.id,
            title: normalized.title,
            eventDate: normalized.eventDate ?? "",
            status: normalized.status.rawValue,
            sportType: normalized.sportType.rawValue,
            plannedSessionId: normalized.plannedSessionId ?? "",
            updatedAtMs: normalized.updatedAtMs,
            data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
        )
        await db.competitionRecordDao.upsert(entity: entity)
        CompetitionReminderManager.schedule(record: normalized)
    }

    public func delete(id: String) {
        recordsLock.lock()
        _records = _records.filter { $0.id != id }
        recordsLock.unlock()

        Task {
            await db.competitionRecordDao.delete(id: id)
        }
        CompetitionReminderManager.cancel(recordId: id)
    }

    private static func competitionExerciseIds(_ records: [CompetitionRecord]) -> Set<String> {
        var result: Set<String> = []
        for record in records {
            for block in record.technicalBlocks {
                if let canonicalId = block.canonicalExerciseId, !canonicalId.isEmpty {
                    result.insert(canonicalId)
                } else if let dbId = block.exerciseDbId, !dbId.isEmpty {
                    result.insert(dbId)
                }
            }
        }
        return result
    }
}

private extension CompetitionRecord {
    func copy(
        createdAtMs: Int64? = nil,
        updatedAtMs: Int64? = nil
    ) -> CompetitionRecord {
        CompetitionRecord(
            id: id,
            title: title,
            eventDate: eventDate,
            startTime: startTime,
            sportType: sportType,
            recordMode: recordMode,
            status: status,
            location: location,
            federation: federation,
            category: category,
            bodyweightKg: bodyweightKg,
            resultSummary: resultSummary,
            placement: placement,
            medal: medal,
            notes: notes,
            plannedProgramId: plannedProgramId,
            plannedSessionId: plannedSessionId,
            plannedWeekId: plannedWeekId,
            keyDateId: keyDateId,
            reminderOneWeekEnabled: reminderOneWeekEnabled,
            reminder48hEnabled: reminder48hEnabled,
            reminderStartEnabled: reminderStartEnabled,
            technicalBlocks: technicalBlocks,
            journal: journal,
            photos: photos,
            customMetrics: customMetrics,
            powerliftingDetails: powerliftingDetails,
            bodybuildingDetails: bodybuildingDetails,
            createdAtMs: createdAtMs ?? self.createdAtMs,
            updatedAtMs: updatedAtMs ?? self.updatedAtMs
        )
    }

    func withFreshTimestamps() -> CompetitionRecord {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return copy(
            createdAtMs: createdAtMs > 0 ? createdAtMs : now,
            updatedAtMs: now
        )
    }
}

private extension Array where Element == CompetitionRecord {
    func filterNotArchived() -> [CompetitionRecord] {
        filter { $0.status != .ARCHIVED }
    }
}
