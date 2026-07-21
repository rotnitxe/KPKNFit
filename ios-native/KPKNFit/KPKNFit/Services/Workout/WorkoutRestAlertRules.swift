import Foundation

internal let WORKOUT_REST_PREALERT_LEAD_SECONDS = 3
internal let WORKOUT_REST_AUDIO_FAILURE_WINDOW_MS: Int64 = 15 * 60 * 1000

internal struct WorkoutRestToneStep {
    let durationMs: Int
    let frequencyHz: Double
    let volume: Double
    let pauseAfterMs: Int64

    init(durationMs: Int, frequencyHz: Double, volume: Double = 0.62, pauseAfterMs: Int64 = 0) {
        self.durationMs = durationMs
        self.frequencyHz = frequencyHz
        self.volume = volume
        self.pauseAfterMs = pauseAfterMs
    }
}

internal func shouldScheduleWorkoutRestPrealert(
    durationSeconds: Int,
    leadSeconds: Int = WORKOUT_REST_PREALERT_LEAD_SECONDS
) -> Bool {
    durationSeconds > leadSeconds
}

internal func workoutRestPrealertTriggerAt(
    endAtMs: Int64,
    leadSeconds: Int = WORKOUT_REST_PREALERT_LEAD_SECONDS
) -> Int64 {
    endAtMs - Int64(leadSeconds) * 1000
}

internal func isRecentWorkoutRestAudioFailure(
    lastFailureAtMs: Int64,
    nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
    windowMs: Int64 = WORKOUT_REST_AUDIO_FAILURE_WINDOW_MS
) -> Bool {
    guard lastFailureAtMs > 0 else { return false }
    let delta = nowMs - lastFailureAtMs
    return delta >= 0 && delta <= windowMs
}

internal func workoutPrealertTonePlan() -> [WorkoutRestToneStep] {
    [
        WorkoutRestToneStep(durationMs: 120, frequencyHz: 587.33, volume: 0.60, pauseAfterMs: 880),
        WorkoutRestToneStep(durationMs: 120, frequencyHz: 587.33, volume: 0.60, pauseAfterMs: 880),
        WorkoutRestToneStep(durationMs: 120, frequencyHz: 587.33, volume: 0.60, pauseAfterMs: 0),
    ]
}

internal func workoutCompletionTonePlan() -> [WorkoutRestToneStep] {
    [
        WorkoutRestToneStep(durationMs: 140, frequencyHz: 587.33, volume: 0.70, pauseAfterMs: 60),
        WorkoutRestToneStep(durationMs: 160, frequencyHz: 698.46, volume: 0.80, pauseAfterMs: 60),
        WorkoutRestToneStep(durationMs: 300, frequencyHz: 880.00, volume: 0.95, pauseAfterMs: 0),
    ]
}

internal func workoutPrealertVibrationPattern() -> [Int64] {
    [0, 100, 900, 100, 900, 100]
}

internal func workoutCompletionVibrationPattern(intensity: HapticIntensity) -> [Int64] {
    switch intensity {
    case .LIGHT:  return [0, 200, 100, 200, 100, 200]
    case .MEDIUM: return [0, 350, 150, 350, 150, 350]
    case .STRONG: return [0, 500, 150, 500, 150, 800]
    }
}

internal func workoutCompletionVibrationAmplitudes(intensity: HapticIntensity) -> [Int] {
    switch intensity {
    case .LIGHT:  return [0, 180, 0, 180]
    case .MEDIUM: return [0, 220, 0, 220]
    case .STRONG: return [0, 255, 0, 255]
    }
}
