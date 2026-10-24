import Foundation

// ─── Enums ────────────────────────────────────────────────────────────────────

public enum Gender: String, Codable {
    case MALE
    case FEMALE
    case OTHER
}

public enum MoodState: String, Codable {
    case HAPPY, NEUTRAL, SAD, ANXIOUS, ENERGETIC
}

public enum IntensityLevel: String, Codable {
    case LOW, MEDIUM, HIGH
}







public enum ArticularBattery: String, Codable {
    case SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL, LUMBAR
}



// ─── Canonical muscular finish contract (Android/backend parity) ─────────────

/// Persisted once, at the exact finish instant.  Optional fields keep legacy
/// JSONL records decodable while the v2 engine rolls out across platforms.






// ─── User Vitals ──────────────────────────────────────────────────────────────

public struct UserVitals: Codable {
    public let age: Int?
    public let weight: Double?
    public let height: Double?
    public let gender: Gender?
    public let bodyFatPercentage: Double?
    public let muscleMassPercentage: Double?
    public let targetWeight: Double?

    public init(
        age: Int? = nil,
        weight: Double? = nil,
        height: Double? = nil,
        gender: Gender? = nil,
        bodyFatPercentage: Double? = nil,
        muscleMassPercentage: Double? = nil,
        targetWeight: Double? = nil
    ) {
        self.age = age
        self.weight = weight
        self.height = height
        self.gender = gender
        self.bodyFatPercentage = bodyFatPercentage
        self.muscleMassPercentage = muscleMassPercentage
        self.targetWeight = targetWeight
    }

    public func copy(weight: Double) -> UserVitals {
        UserVitals(age: age, weight: weight, height: height, gender: gender, bodyFatPercentage: bodyFatPercentage, muscleMassPercentage: muscleMassPercentage, targetWeight: targetWeight)
    }
}

// ─── Wellbeing & Logging ──────────────────────────────────────────────────────

public struct DailyWellbeingLog: Codable {
    public let id: String
    public let date: String
    public let sleepQuality: Int
    public let stressLevel: Int
    public let doms: Int
    public let motivation: Int
    public let sleepHours: Double
    public let moodState: MoodState?
    public let workIntensity: IntensityLevel?
    public let studyIntensity: IntensityLevel?
    public let notes: String?
    public let preWorkoutDiscomforts: [String]

    public init(
        id: String,
        date: String,
        sleepQuality: Int = 3,
        stressLevel: Int = 3,
        doms: Int = 1,
        motivation: Int = 3,
        sleepHours: Double = 7.5,
        moodState: MoodState? = nil,
        workIntensity: IntensityLevel? = nil,
        studyIntensity: IntensityLevel? = nil,
        notes: String? = nil,
        preWorkoutDiscomforts: [String] = []
    ) {
        self.id = id
        self.date = date
        self.sleepQuality = sleepQuality
        self.stressLevel = stressLevel
        self.doms = doms
        self.motivation = motivation
        self.sleepHours = sleepHours
        self.moodState = moodState
        self.workIntensity = workIntensity
        self.studyIntensity = studyIntensity
        self.notes = notes
        self.preWorkoutDiscomforts = preWorkoutDiscomforts
    }
}

// ─── Post-Session Feedback ────────────────────────────────────────────────────

public struct PostSessionFeedback: Codable {
    public let logId: String
    public let date: String
    public let cnsRecovery: Int
    public let muscleFeedback: [String: MuscleFeedbackEntry]
    public let unresolvedDiscomfortIds: [String]

    public init(
        logId: String,
        date: String,
        cnsRecovery: Int = 7,
        muscleFeedback: [String: MuscleFeedbackEntry] = [:],
        unresolvedDiscomfortIds: [String] = []
    ) {
        self.logId = logId
        self.date = date
        self.cnsRecovery = cnsRecovery
        self.muscleFeedback = muscleFeedback
        self.unresolvedDiscomfortIds = unresolvedDiscomfortIds
    }
}

public struct MuscleFeedbackEntry: Codable {
    public let doms: Int
    public let jointPain: Bool
    public let strengthCapacity: Int
    public let notes: String

    public init(
        doms: Int = 1,
        jointPain: Bool = false,
        strengthCapacity: Int = 7,
        notes: String = ""
    ) {
        self.doms = doms
        self.jointPain = jointPain
        self.strengthCapacity = strengthCapacity
        self.notes = notes
    }
}



// ─── Post-Exercise Result ─────────────────────────────────────────────────────

public struct PostExerciseResult {
    public let technicalQuality: Int
    public let mood: Int
    public let discomforts: [String]

    public init(technicalQuality: Int, mood: Int, discomforts: [String]) {
        self.technicalQuality = technicalQuality
        self.mood = mood
        self.discomforts = discomforts
    }
}




// MuscleRecoveryStatus and RecoveryStatus are defined in SessionModels.swift














// ─── Discomfort Catalog ───────────────────────────────────────────────────────

// Redundant DiscomfortSection removed (defined in DiscomfortCatalog.swift)


// ─── Helpers ──────────────────────────────────────────────────────────────────

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}













// ─── Wellbeing & Logging (continued) ──────────────────────────────────────────

public struct SleepLog: Codable {
    public let id: String
    public let date: String
    public let endTime: String
    public let duration: Double

    public init(id: String, date: String, endTime: String, duration: Double) {
        self.id = id
        self.date = date
        self.endTime = endTime
        self.duration = duration
    }
}

















// ─── Mesocycle Stress EMA ──────────────────────────────────────────────────────



















public struct SleepLogExtended: Codable {
    public let id: String
    public let date: String
    public let bedTime: String
    public let wakeTime: String
    public let duration: Double
    public let quality: Int
    public let awakenings: Int
    public let notes: String?

    public init(id: String, date: String, bedTime: String, wakeTime: String, duration: Double, quality: Int = 3, awakenings: Int = 0, notes: String? = nil) {
        self.id = id
        self.date = date
        self.bedTime = bedTime
        self.wakeTime = wakeTime
        self.duration = duration
        self.quality = quality
        self.awakenings = awakenings
        self.notes = notes
    }

    public func toSleepLog() -> SleepLog {
        SleepLog(id: id, date: date, endTime: wakeTime, duration: duration)
    }
}







