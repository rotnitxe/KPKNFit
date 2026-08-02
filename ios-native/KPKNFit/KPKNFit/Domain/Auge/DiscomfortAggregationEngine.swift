import Foundation

struct SessionDiscomfortSummary {
    let discomfortId: String
    let label: String
    let reportedInExercises: [String]
    let articularVolumeScore: Int
    let relatedArticular: [ArticularBattery]
}

enum DiscomfortAggregationEngine {

    static func computeSessionDiscomfortSummary(
        postExerciseFeedbackByExerciseId: [String: PostExerciseFeedback],
        completedExercises: [CompletedExercise],
        exerciseDb: [String: ExerciseMuscleInfo]
    ) -> [SessionDiscomfortSummary] {
        var articularSetCounts: [ArticularBattery: Int] = [:]

        for exercise in completedExercises {
            let workingSets = exercise.sets.filter { !$0.isWarmup && !$0.skipped }.count
            if workingSets == 0 { continue }

            let dbInfo = (exercise.exerciseDbId ?? exercise.exerciseId)
                .flatMap { exerciseDb[$0.lowercased()] }

            guard let dbInfo = dbInfo else { continue }

            let relatedArticulars = Array(Set(
                dbInfo.involvedMuscles.flatMap { AugeTtcEngine.MUSCLE_TO_ARTICULAR[$0.muscle] ?? [] }
            ))

            for articular in relatedArticulars {
                articularSetCounts[articular, default: 0] += workingSets
            }
        }

        let flatDiscomforts = postExerciseFeedbackByExerciseId
            .filter { _, feedback in feedback.discomfortIds.contains { $0 != "none" } }
            .flatMap { exerciseId, feedback in
                feedback.discomfortIds
                    .filter { $0 != "none" }
                    .map { (discomfortId: $0, exerciseId: exerciseId) }
            }

        let discomfortExerciseMap = Dictionary(grouping: flatDiscomforts, by: { $0.discomfortId })
            .mapValues { items in items.map { $0.exerciseId } }

        var summaries: [SessionDiscomfortSummary] = []
        for (discomfortId, exerciseIds) in discomfortExerciseMap {
            let entry = DISCOMFORT_CATALOG_BY_ID[discomfortId]
            let label = entry?.label ?? discomfortId
            let relatedArticular = entry?.relatedArticular ?? []

            let volumeScore = relatedArticular.reduce(0) { acc, articular in
                acc + (articularSetCounts[articular] ?? 0)
            }

            let exerciseNames = exerciseIds.compactMap { id in
                completedExercises.first { $0.exerciseId == id }?.exerciseName
            }.uniqued()

            summaries.append(SessionDiscomfortSummary(
                discomfortId: discomfortId,
                label: label,
                reportedInExercises: exerciseNames,
                articularVolumeScore: volumeScore,
                relatedArticular: relatedArticular
            ))
        }

        return summaries
            .sorted { $0.articularVolumeScore > $1.articularVolumeScore }
            .prefix(5)
            .map { $0 }
    }
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

private extension Dictionary {
    func grouped<Key: Hashable>(by keyTransform: (Value) -> Key, valueTransform: (Value) -> Value) -> [Key: [Value]] {
        var result: [Key: [Value]] = [:]
        for value in values {
            let key = keyTransform(value)
            result[key, default: []].append(valueTransform(value))
        }
        return result
    }
}
