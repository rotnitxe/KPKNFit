import Foundation

public struct AugeAdaptiveEngine {

    private static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
        return min(hi, max(lo, v))
    }

    /// Derives the implied recovery time tau from prediction-vs-reality.
    /// Ported from backend/engines/adaptive_engine.py:_derive_implied_recovery_time()
    private static func deriveImpliedRecoveryTime(obs: RecoveryLearningObservation) -> Double? {
        if obs.hoursSinceSession < 6.0 { return nil }

        if obs.sessionStress <= 0 {
            return obs.actualBattery >= 95 ? 12.0 : nil
        }

        let actualDepletion = max(1.0, 100.0 - Double(obs.actualBattery))
        let initialDepletion = max(actualDepletion, obs.sessionStress)

        let remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)

        let effectiveHours: Double
        switch obs.muscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines) {
        case "cns":
            effectiveHours = obs.hoursSinceSession
        case "spinal":
            effectiveHours = AugeUtils.getSpinalRecoveryHours(obs.hoursSinceSession)
        default:
            effectiveHours = AugeUtils.getSigmoidalHours(obs.hoursSinceSession)
        }

        let k = -log(remainingFraction) / effectiveHours
        if k <= 0 { return nil }

        let impliedTau = 2.9957 / k
        return clamp(impliedTau, 6.0, 200.0)
    }

    /// Updates the per-muscle personalized recovery hours using a simplified
    /// Bayesian-like exponential moving average.
    public static func updatePersonalizedRecoveryHours(
        current: [String: Double],
        observation: RecoveryLearningObservation,
        totalObservations: Int
    ) -> [String: Double] {
        guard let impliedTau = deriveImpliedRecoveryTime(obs: observation) else { return current }

        let muscleKey = observation.muscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let currentTau = current[muscleKey] ?? defaultRecoveryHours(muscle: muscleKey)

        let samples = Double(max(1, totalObservations + 1))
        let alpha = max(0.05, min(0.5, 1.5 / samples))
        let newTau = currentTau * (1.0 - alpha) + impliedTau * alpha

        var updated = current
        updated[muscleKey] = clamp(newTau, 12.0, 144.0)
        return updated
    }

    /// Learns system-level biases (CNS/spinal) from the difference between
    /// manual ring adjustments and predicted drains.
    public static func updateSystemLearningDeltas(
        currentCnsDelta: Double,
        currentSpinalDelta: Double,
        systemAdjustment: Int?,
        structureAdjustment: Int?,
        totalObservations: Int
    ) -> (Double, Double) {
        let samples = Double(max(1, totalObservations + 1))
        let alpha = max(0.05, min(0.5, 1.5 / samples))

        let newCns: Double
        if let systemAdj = systemAdjustment {
            newCns = currentCnsDelta * (1.0 - alpha) + Double(systemAdj) * alpha
        } else {
            newCns = currentCnsDelta
        }

        let newSpinal: Double
        if let structAdj = structureAdjustment {
            newSpinal = currentSpinalDelta * (1.0 - alpha) + Double(structAdj) * alpha
        } else {
            newSpinal = currentSpinalDelta
        }

        return (
            clamp(newCns, -50.0, 50.0),
            clamp(newSpinal, -50.0, 50.0)
        )
    }

    /// Learns the exact recovery Tau (hours) for systemic batteries based on the implied 
    /// recovery time from a user's calibration, exactly how it's done for muscles.
    public static func updateSystemRecoveryHours(
        currentCnsTau: Double?,
        currentSpinalTau: Double?,
        cnsObservation: RecoveryLearningObservation?,
        spinalObservation: RecoveryLearningObservation?,
        totalObservations: Int
    ) -> (Double?, Double?) {
        let samples = Double(max(1, totalObservations + 1))
        let alpha = max(0.05, min(0.5, 1.5 / samples))

        var newCnsTau = currentCnsTau
        if let cnsObs = cnsObservation {
            if let impliedCns = deriveImpliedRecoveryTime(obs: cnsObs) {
                let base = currentCnsTau ?? 36.0
                newCnsTau = base * (1.0 - alpha) + impliedCns * alpha
            }
        }

        var newSpinalTau = currentSpinalTau
        if let spinalObs = spinalObservation {
            if let impliedSpinal = deriveImpliedRecoveryTime(obs: spinalObs) {
                let base = currentSpinalTau ?? 52.0
                newSpinalTau = base * (1.0 - alpha) + impliedSpinal * alpha
            }
        }

        return (
            newCnsTau.map { clamp($0, 12.0, 144.0) },
            newSpinalTau.map { clamp($0, 18.0, 144.0) }
        )
    }

    /// Updates drain resistance multipliers using the relationship between predicted drain and actual drain.
    public static func updateDrainMultipliers(
        currentCnsMult: Double,
        currentSpinalMult: Double,
        currentMuscleMults: [String: Double],
        manualNeural: Int?,
        manualSpinal: Int?,
        manualMuscleBatteries: [String: Int],
        predictedNeural: Int?,
        predictedSpinal: Int?,
        predictedMuscleBatteries: [String: Int],
        preWorkoutNeural: Int,
        preWorkoutSpinal: Int,
        preWorkoutMuscleBatteries: [String: Int],
        totalObservations: Int
    ) -> (Double, Double, [String: Double]) {
        let samples = Double(max(1, totalObservations + 1))
        let alpha = max(0.05, min(0.5, 1.5 / samples))

        let newCnsMult: Double
        if let manualN = manualNeural, let predictedN = predictedNeural, preWorkoutNeural > predictedN {
            let predictedDrain = max(1.0, Double(preWorkoutNeural - predictedN))
            let actualDrain = max(1.0, Double(preWorkoutNeural - manualN))
            let ratio = min(2.5, max(0.2, actualDrain / predictedDrain))
            newCnsMult = currentCnsMult * (1.0 - alpha) + ratio * alpha
        } else {
            newCnsMult = currentCnsMult
        }

        let newSpinalMult: Double
        if let manualS = manualSpinal, let predictedS = predictedSpinal, preWorkoutSpinal > predictedS {
            let predictedDrain = max(1.0, Double(preWorkoutSpinal - predictedS))
            let actualDrain = max(1.0, Double(preWorkoutSpinal - manualS))
            let ratio = min(2.5, max(0.2, actualDrain / predictedDrain))
            newSpinalMult = currentSpinalMult * (1.0 - alpha) + ratio * alpha
        } else {
            newSpinalMult = currentSpinalMult
        }

        var updatedMuscleMults = currentMuscleMults
        for (muscle, manualValue) in manualMuscleBatteries {
            let muscleKey = muscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let predicted = predictedMuscleBatteries[muscle] ?? predictedMuscleBatteries[muscleKey]
            let preWorkout = preWorkoutMuscleBatteries[muscle] ?? preWorkoutMuscleBatteries[muscleKey] ?? 100
            
            if let pred = predicted, preWorkout > pred {
                let predictedDrain = max(1.0, Double(preWorkout - pred))
                let actualDrain = max(1.0, Double(preWorkout - manualValue))
                let ratio = min(2.5, max(0.2, actualDrain / predictedDrain))
                let currentMult = currentMuscleMults[muscleKey] ?? 1.0
                updatedMuscleMults[muscleKey] = clamp(currentMult * (1.0 - alpha) + ratio * alpha, 0.2, 2.5)
            }
        }

        return (
            clamp(newCnsMult, 0.2, 2.5),
            clamp(newSpinalMult, 0.2, 2.5),
            updatedMuscleMults
        )
    }

    /// Computes per-muscle delta (battery calibration offset) based on
    /// difference between manual muscle battery and predicted.
    public static func updateMuscleDeltas(
        current: [String: Double],
        manualMuscleBatteries: [String: Int],
        predictedMuscleBatteries: [String: Int],
        totalObservations: Int
    ) -> [String: Double] {
        let samples = Double(max(1, totalObservations + 1))
        let alpha = max(0.05, min(0.5, 1.5 / samples))
        var result = current

        for (muscle, manualValue) in manualMuscleBatteries {
            let muscleKey = muscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
            let predicted = predictedMuscleBatteries[muscle] ?? predictedMuscleBatteries[muscleKey] ?? manualValue
            let currentDelta = current[muscleKey] ?? 0.0
            let signal = Double(manualValue - predicted)
            let newDelta = currentDelta * (1.0 - alpha) + signal * alpha
            result[muscleKey] = clamp(newDelta, -50.0, 50.0)
        }

        return result
    }

    /// Population default recovery hours per muscle (same as Python backend).
    public static func defaultRecoveryHours(muscle: String) -> Double {
        let key = muscle.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = key.replacingOccurrences(of: "á", with: "a")
                            .replacingOccurrences(of: "é", with: "e")
                            .replacingOccurrences(of: "í", with: "i")
                            .replacingOccurrences(of: "ó", with: "o")
                            .replacingOccurrences(of: "ú", with: "u")

        switch normalized {
        case "biceps": return 24.0
        case "triceps": return 24.0
        case "deltoides", "deltoides anterior", "deltoides lateral", "deltoides posterior": return 24.0
        case "pantorrillas": return 24.0
        case "abdomen": return 24.0
        case "antebrazo": return 24.0
        case "pectorales": return 48.0
        case "dorsales": return 48.0
        case "hombros": return 48.0
        case "trapecio": return 48.0
        case "aductores": return 48.0
        case "core": return 48.0
        case "cuadriceps": return 72.0
        case "gluteos": return 72.0
        case "isquiosurales": return 96.0
        case "erectores espinales": return 96.0
        case "cuello": return 48.0
        default: return 48.0
        }
    }
}
