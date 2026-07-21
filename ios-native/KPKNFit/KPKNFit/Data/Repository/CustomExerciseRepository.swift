import Foundation

public final class CustomExerciseRepository {

    static let shared = CustomExerciseRepository()

    private let db = KpknDatabase.instance()

    private var _customExercises: [ExerciseMuscleInfo] = []
    private let exercisesLock = NSLock()
    private var initialized = false

    public var customExercises: [ExerciseMuscleInfo] {
        exercisesLock.lock()
        defer { exercisesLock.unlock() }
        return _customExercises
    }

    private init() {}

    public static func getInstance() -> CustomExerciseRepository { shared }

    public func initialize() {
        if initialized { return }
        initialized = true
        Task {
            let entities = (try? await self.db.customExerciseDao.getAll()) ?? []
            let decoded = entities.compactMap { entity -> ExerciseMuscleInfo? in
                try? JSONDecoder().decode(ExerciseMuscleInfo.self, from: Data(entity.data.utf8))
            }
            let withCustomFlag = decoded.map { $0.copy(isCustom: true) }
            exercisesLock.lock()
            _customExercises = withCustomFlag
            exercisesLock.unlock()
            setCustomExerciseOverlay(exercises: withCustomFlag)
        }
    }

    public func upsert(exercise: ExerciseMuscleInfo) {
        if !initialized { return }
        let normalized = exercise.copy(isCustom: true)
        exercisesLock.lock()
        let filtered = _customExercises.filter { !$0.id.equalsIgnoreCase(normalized.id) }
        let sorted = (filtered + [normalized]).sorted { $0.name.lowercased() < $1.name.lowercased() }
        _customExercises = sorted
        exercisesLock.unlock()

        upsertCustomExerciseOverlay(exercise: normalized)
        Task {
            let data = try? JSONEncoder().encode(normalized)
            let entity = CustomExerciseEntity(
                id: normalized.id,
                name: normalized.name,
                data: data.map { String(decoding: $0, as: UTF8.self) } ?? "{}",
                createdAt: IsoDateFormatter.nowString(),
                updatedAt: IsoDateFormatter.nowString()
            )
            try? await self.db.customExerciseDao.upsert(entity: entity)
        }
    }

    public func delete(exerciseId: String) {
        if !initialized { return }
        exercisesLock.lock()
        _customExercises = _customExercises.filter { !$0.id.equalsIgnoreCase(exerciseId) }
        exercisesLock.unlock()

        removeCustomExerciseOverlay(exerciseId: exerciseId)
        Task {
            try? await self.db.customExerciseDao.delete(id: exerciseId)
        }
    }
}

private struct IsoDateFormatter {
    static func nowString() -> String {
        let formatter = ISO8601DateFormatter()
        return formatter.string(from: Date())
    }
}

private extension String {
    func equalsIgnoreCase(_ other: String) -> Bool {
        self.lowercased() == other.lowercased()
    }
}
