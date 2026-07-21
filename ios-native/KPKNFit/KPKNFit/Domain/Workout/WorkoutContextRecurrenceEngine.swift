import Foundation

public enum Weekday: String, Codable, CaseIterable {
    case monday, tuesday, wednesday, thursday, friday, saturday, sunday
}

enum WorkoutContextRecurrenceEngine {
    struct RecurrenceResult {
        let tagId: String?
        let profileId: String?
        let confidence: Int
    }

    static func detectDayRecurrence(
        exerciseDbId: String,
        dayOfWeek: Weekday,
        logs: [WorkoutLog]
    ) -> RecurrenceResult {
        let sameDayLogs = logs.filter { log in
            guard let date = log.date.prefix(10).description.parseDate() else { return false }
            return date.weekday == dayOfWeek
        }
        if sameDayLogs.count < 2 {
            return RecurrenceResult(tagId: nil, profileId: nil, confidence: 0)
        }

        struct TagAndSetup: Hashable {
            let tagId: String?
            let setupId: String?
        }

        let patterns: [TagAndSetup] = sameDayLogs.compactMap { log in
            let tag = log.exerciseTags[exerciseDbId]
            let setupId = log.completedExercises
                .first { $0.exerciseDbId == exerciseDbId }?
                .sets
                .first { $0.setupProfileId != nil }?
                .setupProfileId
            if tag != nil || setupId != nil {
                return TagAndSetup(tagId: tag, setupId: setupId)
            }
            return nil
        }

        guard let mostFrequent = Dictionary(grouping: patterns, by: { $0 })
            .max(by: { $0.value.count < $1.value.count }) else {
            return RecurrenceResult(tagId: nil, profileId: nil, confidence: 0)
        }

        let count = mostFrequent.value.count
        if count >= 2 {
            return RecurrenceResult(
                tagId: mostFrequent.key.tagId,
                profileId: mostFrequent.key.setupId,
                confidence: count
            )
        } else {
            return RecurrenceResult(tagId: nil, profileId: nil, confidence: 0)
        }
    }
}

private extension String {
    func parseDate() -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: self)
    }
}

extension Date {
    var weekday: Weekday {
        let cal = Calendar.current
        let component = cal.component(.weekday, from: self)
        switch component {
        case 1: return .sunday
        case 2: return .monday
        case 3: return .tuesday
        case 4: return .wednesday
        case 5: return .thursday
        case 6: return .friday
        case 7: return .saturday
        default: return .monday
        }
    }
}
