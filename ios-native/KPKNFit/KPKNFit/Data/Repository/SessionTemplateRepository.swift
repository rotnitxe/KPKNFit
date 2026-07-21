import Foundation

public final class SessionTemplateRepository {

    private let db = KpknDatabase.instance()

    private var _userTemplates: [SessionTemplate] = []
    private let templatesLock = NSLock()

    public var userTemplates: [SessionTemplate] {
        templatesLock.lock()
        defer { templatesLock.unlock() }
        return _userTemplates
    }

    public var allTemplates: [SessionTemplate] {
        templatesLock.lock()
        defer { templatesLock.unlock() }
        let activeUserTemplates = _userTemplates.filter { !$0.isArchived }
            .sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
        return SESSION_TEMPLATES_SYSTEM + activeUserTemplates
    }

    public static let shared = SessionTemplateRepository()

    public static func getInstance() -> SessionTemplateRepository {
        return shared
    }

    private init() {
        Task {
            await loadPersistedTemplates()
        }
    }

    private func loadPersistedTemplates() async {
        let entities = await db.sessionTemplateDao.getAll()
        let decoded = entities.compactMap { entity -> SessionTemplate? in
            try? JSONDecoder().decode(SessionTemplate.self, from: Data(entity.data.utf8))
        }
        templatesLock.lock()
        _userTemplates = decoded
        templatesLock.unlock()
    }

    public func getById(id: String) -> SessionTemplate? {
        if let systemTemplate = SESSION_TEMPLATES_SYSTEM.first(where: { $0.id == id }) {
            return systemTemplate
        }
        templatesLock.lock()
        defer { templatesLock.unlock() }
        return _userTemplates.first { $0.id == id }
    }

    public func saveUserTemplate(template: SessionTemplate) {
        guard template.sourceType == .user else { return }
        templatesLock.lock()
        if let idx = _userTemplates.firstIndex(where: { $0.id == template.id }) {
            _userTemplates[idx] = template
        } else {
            _userTemplates = [template] + _userTemplates
        }
        templatesLock.unlock()

        Task {
            let data = try? JSONEncoder().encode(template)
            let entity = SessionTemplateEntity(
                id: template.id,
                sourceType: template.sourceType.rawValue,
                name: template.name,
                sortOrder: 0,
                isArchived: template.isArchived ? 1 : 0,
                createdAt: template.createdAt ?? "",
                data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}"
            )
            try? await db.sessionTemplateDao.upsert(entity: entity)
        }
    }

    public func deleteUserTemplate(id: String) {
        if SESSION_TEMPLATES_SYSTEM.contains(where: { $0.id == id }) { return }
        templatesLock.lock()
        _userTemplates = _userTemplates.filter { $0.id != id }
        templatesLock.unlock()

        Task {
            try? await db.sessionTemplateDao.delete(id: id)
        }
    }

    public func archiveUserTemplate(id: String) {
        guard let template = _userTemplates.first(where: { $0.id == id }) else { return }
        saveUserTemplate(template: template.copy(isArchived: true, updatedAt: IsoDateFormatter.nowString()))
    }

    public func restoreUserTemplate(id: String) {
        guard let template = _userTemplates.first(where: { $0.id == id }) else { return }
        saveUserTemplate(template: template.copy(isArchived: false, updatedAt: IsoDateFormatter.nowString()))
    }
}

private struct IsoDateFormatter {
    static func nowString() -> String {
        let formatter = ISO8601DateFormatter()
        return formatter.string(from: Date())
    }
}

// ─── Extensions for copy method on SessionTemplate ───────────────────────────

extension SessionTemplate {
    public func copy(
        isArchived: Bool? = nil,
        updatedAt: String? = nil
    ) -> SessionTemplate {
        SessionTemplate(
            id: id,
            sourceType: sourceType,
            name: name,
            description: description,
            emoji: emoji,
            tags: tags,
            difficulty: difficulty,
            estimatedDurationMinutes: estimatedDurationMinutes,
            exerciseCount: exerciseCount,
            partCount: partCount,
            muscleGroupsSummary: muscleGroupsSummary,
            session: session,
            sortOrder: sortOrder,
            isArchived: isArchived ?? self.isArchived,
            createdAt: createdAt,
            updatedAt: updatedAt ?? self.updatedAt,
            splitIds: splitIds,
            splitDayLabels: splitDayLabels,
            focusCategory: focusCategory,
            shortDescription: shortDescription,
            weeklyVolumePolicyId: weeklyVolumePolicyId
        )
    }
}
