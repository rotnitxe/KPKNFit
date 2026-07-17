import Foundation

public struct MuscleVolumeEntry {
    public let muscleId: String
    public let muscleName: String
    public let displayVolume: Double
    public let sets: Int
}

public struct CanonicalMuscleVolumeEntry {
    public let muscleId: String
    public let muscleName: String
    public let weeklySets: Double
}

public struct VolumeCalculator {
    public static func normalizeCanonicalMuscleGroup(_ specificMuscle: String, emphasis: String? = nil) -> String {
        let lower = specificMuscle.lowercased()
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: "_", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            
        if lower.contains("deltoides") || lower.contains("hombro") { return "Deltoides" }
        if (lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral") { return "Bíceps" }
        if lower.contains("tríceps") || lower.contains("triceps") { return "Tríceps" }
        if lower.contains("antebrazo") || lower.contains("braquiorradial") { return "Antebrazo" }
        if lower.contains("trapecio") { return "Trapecio" }
        if lower.contains("romboides") { return "Romboides" }
        if lower.contains("dorsal") || lower.contains("redondo") || lower.contains("espalda") || lower.contains("lat ") || lower.hasSuffix(" lat") || lower == "lat" || lower.hasPrefix("lat ") || lower.contains("lats") { return "Dorsales" }
        if lower.contains("erector") || lower.contains("lumbar") { return "Erectores Espinales" }
        if lower.contains("pectoral") || lower.contains("pecho") { return "Pectorales" }
        if lower.contains("cuádriceps") || lower.contains("cuadriceps") || lower.contains("recto femoral") || lower.contains("vasto") { return "Cuádriceps" }
        if lower.contains("isquio") || lower.contains("femoral") || lower.contains("semitendinoso") || lower.contains("semimembranoso") { return "Isquiosurales" }
        if lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata") || lower.contains("tensor fascia") { return "Glúteos" }
        if lower.contains("psoas") { return "Core" }
        if lower.contains("adductor") || lower.contains("aductor") || lower.contains("pectíneo") || lower.contains("pectineo") { return "Aductores" }
        if lower.contains("gemelo") || lower.contains("pantorrilla") || lower.contains("gastrocnemio") || lower.contains("sóleo") || lower.contains("soleo") { return "Pantorrillas" }
        if lower.contains("cuello") || lower.contains("cervical") { return "Cuello" }
        if lower == "core" || lower.contains("transverso") || lower.contains("serrato") { return "Core" }
        if lower.contains("abdominal") || lower.contains("abdomen") || lower.contains("oblicuo") || lower.contains("recto del abdomen") { return "Abdomen" }
        return specificMuscle.prefix(1).uppercased() + specificMuscle.dropFirst()
    }

    public static func calculateVolumeAdjustment(_ muscle: String, _ feedbackHistory: [PostSessionFeedback]) -> Double {
        if feedbackHistory.isEmpty { return 1.0 }
        let normalizedTarget = normalizeCanonicalMuscleGroup(muscle).lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let muscleLogs = feedbackHistory.filter { log in
            log.muscleFeedback.keys.contains { key in
                normalizeCanonicalMuscleGroup(key).lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalizedTarget
            }
        }
        if muscleLogs.isEmpty { return 1.0 }
        let recent = Array(muscleLogs.sorted { $0.date > $1.date }.prefix(3))
        var totalDoms = 0.0
        var totalStr = 0.0
        var count = 0
        for log in recent {
            if let entryKey = log.muscleFeedback.keys.first(where: { normalizeCanonicalMuscleGroup($0).lowercased().trimmingCharacters(in: .whitespacesAndNewlines) == normalizedTarget }),
               let entry = log.muscleFeedback[entryKey] {
                totalDoms += Double(entry.doms)
                totalStr += Double(entry.strengthCapacity)
                count += 1
            }
        }
        if count == 0 { return 1.0 }
        let avgDoms = totalDoms / Double(count)
        let avgStr = totalStr / Double(count)
        if avgDoms >= 3.5 || avgStr <= 5.0 {
            return 0.85
        } else if avgDoms <= 1.5 && avgStr >= 8.0 {
            return 1.10
        } else {
            return 1.0
        }
    }

    static func calculateCompletedWeeklyMuscleVolume(
        logs: [WorkoutLog],
        exerciseList: [ExerciseMuscleInfo],
        weeksCount: Int = 1
    ) -> [CanonicalMuscleVolumeEntry] {
        return []
    }
}
