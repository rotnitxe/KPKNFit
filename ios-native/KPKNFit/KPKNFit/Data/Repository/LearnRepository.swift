import Foundation

public final class LearnRepository {

    static let shared = LearnRepository()

    private let db = KpknDatabase.instance()

    private var _progress: [String: CourseProgress] = [:]
    private var _badges: [LearnBadge] = []

    private var prefs: UserDefaults?
    private var initialized = false
    private let progressLock = NSLock()
    private let badgesLock = NSLock()

    public var progress: [String: CourseProgress] {
        progressLock.lock()
        defer { progressLock.unlock() }
        return _progress
    }

    public var badges: [LearnBadge] {
        badgesLock.lock()
        defer { badgesLock.unlock() }
        return _badges
    }

    private init() {}

    public static func getInstance() -> LearnRepository { shared }

    public func initialize() {
        if initialized { return }
        prefs = UserDefaults(suiteName: "kpkn.learn")
        initialized = true
        Task { [weak self] in
            self?.loadState()
        }
    }

    public func getModules() -> [LearnModule] { LEARN_MODULES }

    public func getModule(id: String) -> LearnModule? {
        LEARN_MODULES.first { $0.id == id }
    }

    public func getProgress(moduleId: String) -> CourseProgress {
        progressLock.lock()
        defer { progressLock.unlock() }
        return _progress[moduleId] ?? CourseProgress(moduleId: moduleId)
    }

    public func updateSubmoduleCompletion(moduleId: String, submoduleIndex: Int, quizScore: Int) {
        progressLock.lock()
        let current = _progress[moduleId] ?? CourseProgress(moduleId: moduleId)
        var updatedScores = current.submoduleQuizScores
        let module = LEARN_MODULES.first { $0.id == moduleId }
        if module != nil, module!.submodules.indices.contains(submoduleIndex) {
            updatedScores[module!.submodules[submoduleIndex].id] = quizScore
        }
        let updated = current.copy(
            submoduleIndex: max(current.submoduleIndex, submoduleIndex),
            submoduleQuizScores: updatedScores
        )
        _progress[moduleId] = updated
        progressLock.unlock()
        persistState()
    }

    public func updateFinalQuizScore(moduleId: String, score: Int) {
        progressLock.lock()
        let current = _progress[moduleId] ?? CourseProgress(moduleId: moduleId)
        let module = LEARN_MODULES.first { $0.id == moduleId }
        let totalQuestions = max(module?.finalQuiz.count ?? 1, 1)
        let percentage = (score * 100) / totalQuestions

        var newBadge: LearnBadge? = nil
        if percentage >= 80 {
            let tier: BadgeTier = if percentage >= 95 {
                .ORO
            } else if percentage >= 85 {
                .PLATA
            } else {
                .BRONCE
            }
            newBadge = LearnBadge(
                courseId: moduleId,
                courseName: module?.title ?? "",
                tier: tier,
                earnedAt: IsoDateFormatter.nowString(),
                score: percentage,
                isSpecial: module?.isSpecial ?? false
            )
        }

        let updated = current.copy(
            finalQuizScore: score,
            badge: newBadge,
            completedAt: percentage >= 80 ? IsoDateFormatter.nowString() : nil
        )
        _progress[moduleId] = updated
        progressLock.unlock()

        if let badge = newBadge {
            badgesLock.lock()
            let filtered = _badges.filter { $0.courseId != moduleId }
            _badges = (filtered + [badge]).sorted { $0.earnedAt > $1.earnedAt }
            badgesLock.unlock()
        }

        persistState()
    }

    public func markDisclaimerShown(moduleId: String) {
        progressLock.lock()
        let current = _progress[moduleId] ?? CourseProgress(moduleId: moduleId)
        _progress[moduleId] = current.copy(disclaimerShown: true)
        progressLock.unlock()
        persistState()
    }

    public func hasSpecialBadge() -> Bool {
        badgesLock.lock()
        defer { badgesLock.unlock() }
        return _badges.contains { $0.isSpecial }
    }

    public func completedCoursesCount() -> Int {
        progressLock.lock()
        defer { progressLock.unlock() }
        return _progress.values.filter { $0.isCompleted }.count
    }

    private func loadState() {
        guard let prefs = prefs else { return }
        if let progressJson = prefs.string(forKey: "progress"),
           let data = progressJson.data(using: .utf8) {
            if let decoded = try? JSONDecoder().decode([String: CourseProgress].self, from: data) {
                progressLock.lock()
                _progress = decoded
                progressLock.unlock()
            }
        }
        if let badgesJson = prefs.string(forKey: "badges"),
           let data = badgesJson.data(using: .utf8) {
            if let decoded = try? JSONDecoder().decode([LearnBadge].self, from: data) {
                badgesLock.lock()
                _badges = decoded
                badgesLock.unlock()
            }
        }
    }

    private func persistState() {
        Task { [weak self] in
            guard let self = self else { return }
            self.progressLock.lock()
            let progressDict = self._progress
            self.progressLock.unlock()
            self.badgesLock.lock()
            let badgesList = self._badges
            self.badgesLock.unlock()
            guard let prefs = self.prefs else { return }
            if let progressData = try? JSONEncoder().encode(progressDict) {
                prefs.set(String(decoding: progressData, as: UTF8.self), forKey: "progress")
            }
            if let badgesData = try? JSONEncoder().encode(badgesList) {
                prefs.set(String(decoding: badgesData, as: UTF8.self), forKey: "badges")
            }
        }
    }
}
 
private struct IsoDateFormatter {
    static func nowString() -> String {
        let formatter = ISO8601DateFormatter()
        return formatter.string(from: Date())
    }
}
