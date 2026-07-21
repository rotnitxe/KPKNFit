import Foundation
import CryptoKit

class TelemetryHelper {

    private let telemetry = KpknTelemetry.shared

    private func anonymizeText(_ text: String?) -> String {
        guard let text = text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return ""
        }
        let input = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let data = input.data(using: .utf8) else { return "anonymized" }
        let hash = SHA256.hash(data: data)
        return hash.compactMap { String(format: "%02x", $0) }.joined().prefix(12).description
    }

    func logAppOpen() {
        logKpknEvent(
            eventName: TelemetryEvents.appOpen,
            params: [
                TelemetryParameters.screenName: "main",
                "app_version": (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "",
                "app_version_code": (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? ""
            ]
        )
    }

    func logAppForeground() {
        logKpknEvent(
            eventName: TelemetryEvents.appForeground,
            params: [
                TelemetryParameters.screenName: "foreground"
            ]
        )
    }

    func logAppBackground() {
        logKpknEvent(
            eventName: TelemetryEvents.appBackground,
            params: [
                TelemetryParameters.screenName: "background"
            ]
        )
    }

    func logLoginSuccess(userId: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.loginSuccess,
            params: [
                TelemetryParameters.userId: userId.map { anonymizeText($0) } as Any,
                TelemetryParameters.success: true
            ]
        )
    }

    func logLoginFailed(error: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.loginFailed,
            params: [
                TelemetryParameters.errorMessage: error as Any,
                TelemetryParameters.success: false
            ]
        )
    }

    func logLogout() {
        logKpknEvent(
            eventName: TelemetryEvents.logout,
            params: [
                TelemetryParameters.success: true
            ]
        )
    }

    func logWorkoutStart(workoutId: String, workoutName: String, workoutType: String) {
        logKpknEvent(
            eventName: TelemetryEvents.workoutStart,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.workoutName: workoutName,
                TelemetryParameters.workoutType: workoutType,
                TelemetryParameters.success: true
            ]
        )
    }

    func logWorkoutEnd(workoutId: String, durationMs: Int, exercisesCount: Int, setsCount: Int) {
        let durationSeconds = durationMs / 1000
        logKpknEvent(
            eventName: TelemetryEvents.workoutEnd,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.duration: durationSeconds,
                TelemetryParameters.action: "complete",
                TelemetryParameters.success: true,
                "exercises_count": exercisesCount,
                "sets_count": setsCount
            ]
        )
    }

    func logWorkoutPause(workoutId: String) {
        logKpknEvent(
            eventName: TelemetryEvents.workoutPause,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.action: "pause"
            ]
        )
    }

    func logWorkoutResume(workoutId: String) {
        logKpknEvent(
            eventName: TelemetryEvents.workoutResume,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.action: "resume"
            ]
        )
    }

    func logSetComplete(exerciseId: String, exerciseName: String, setNumber: Int, reps: Int, weight: Double?) {
        let roundedWeight = weight.map { Double(Int(($0 / 2.5).rounded())) * 2.5 }
        logKpknEvent(
            eventName: TelemetryEvents.setComplete,
            params: [
                TelemetryParameters.exerciseId: exerciseId,
                TelemetryParameters.exerciseName: exerciseName,
                TelemetryParameters.setNumber: setNumber,
                TelemetryParameters.reps: reps,
                TelemetryParameters.weight: roundedWeight as Any
            ]
        )
    }

    func logRestStart(workoutId: String, restTimeSeconds: Int) {
        logKpknEvent(
            eventName: TelemetryEvents.restStart,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.restTime: restTimeSeconds
            ]
        )
    }

    func logRestEnd(workoutId: String, actualRestTimeSeconds: Int) {
        logKpknEvent(
            eventName: TelemetryEvents.restEnd,
            params: [
                TelemetryParameters.workoutId: workoutId,
                TelemetryParameters.restTime: actualRestTimeSeconds,
                TelemetryParameters.action: "complete"
            ]
        )
    }

    func logNutritionOpen() {
        logKpknEvent(
            eventName: TelemetryEvents.nutritionOpen,
            params: [
                TelemetryParameters.screenName: "nutrition"
            ]
        )
    }

    func logMealLogStart(mealType: String) {
        logKpknEvent(
            eventName: TelemetryEvents.mealLogStart,
            params: [
                TelemetryParameters.mealType: mealType
            ]
        )
    }

    func logMealLogComplete(mealId: String, mealType: String, totalCalories: Double, protein: Double, carbs: Double, fat: Double) {
        logKpknEvent(
            eventName: TelemetryEvents.mealLogComplete,
            params: [
                TelemetryParameters.mealId: mealId,
                TelemetryParameters.mealType: mealType,
                TelemetryParameters.calories: Int(totalCalories.rounded()),
                TelemetryParameters.protein: Int(protein.rounded()),
                TelemetryParameters.carbs: Int(carbs.rounded()),
                TelemetryParameters.fat: Int(fat.rounded()),
                TelemetryParameters.success: true
            ]
        )
    }

    func logFoodSearch(query: String) {
        logKpknEvent(
            eventName: TelemetryEvents.foodSearch,
            params: [
                TelemetryParameters.action: "search",
                TelemetryParameters.aiInput: anonymizeText(query)
            ]
        )
    }

    func logFoodItemAdd(foodId: String, foodName: String, calories: Double?) {
        logKpknEvent(
            eventName: TelemetryEvents.foodItemAdd,
            params: [
                TelemetryParameters.foodId: foodId,
                TelemetryParameters.foodName: anonymizeText(foodName),
                TelemetryParameters.calories: calories.map { Int($0.rounded()) } as Any
            ]
        )
    }

    func logNutritionPhotoParse() {
        logKpknEvent(
            eventName: TelemetryEvents.nutritionParsePhoto,
            params: [
                TelemetryParameters.nutritionMethod: "photo"
            ]
        )
    }

    func logNutritionParseSuccess(foodItemsCount: Int) {
        logKpknEvent(
            eventName: TelemetryEvents.nutritionParseSuccess,
            params: [
                TelemetryParameters.action: "success",
                TelemetryParameters.success: true,
                "food_items_count": foodItemsCount
            ]
        )
    }

    func logNutritionParseFailed(error: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.nutritionParseFailed,
            params: [
                TelemetryParameters.errorMessage: error as Any,
                TelemetryParameters.success: false
            ]
        )
    }

    func logProgramSelect(programId: String, programName: String, programType: String) {
        logKpknEvent(
            eventName: TelemetryEvents.programSelect,
            params: [
                TelemetryParameters.programId: programId,
                TelemetryParameters.programName: programName,
                TelemetryParameters.programType: programType
            ]
        )
    }

    func logProgramStart(programId: String, week: Int, day: Int) {
        logKpknEvent(
            eventName: TelemetryEvents.programStart,
            params: [
                TelemetryParameters.programId: programId,
                TelemetryParameters.programWeek: week,
                TelemetryParameters.programDay: day,
                TelemetryParameters.action: "start"
            ]
        )
    }

    func logProgramComplete(programId: String, durationMs: Int, totalWorkouts: Int, completedWorkouts: Int) {
        let durationDays = durationMs / (1000 * 60 * 60 * 24)
        logKpknEvent(
            eventName: TelemetryEvents.programComplete,
            params: [
                TelemetryParameters.programId: programId,
                TelemetryParameters.duration: durationDays,
                TelemetryParameters.action: "complete",
                "total_workouts": totalWorkouts,
                "completed_workouts": completedWorkouts,
                TelemetryParameters.success: true
            ]
        )
    }

    func logExerciseSearch(query: String) {
        logKpknEvent(
            eventName: TelemetryEvents.exerciceSearch,
            params: [
                TelemetryParameters.action: "search",
                TelemetryParameters.aiInput: anonymizeText(query)
            ]
        )
    }

    func logExerciseViewDetails(exerciseId: String, exerciseName: String, category: String?) {
        logKpknEvent(
            eventName: TelemetryEvents.exerciceViewDetails,
            params: [
                TelemetryParameters.exerciseId: exerciseId,
                TelemetryParameters.exerciseName: exerciseName,
                TelemetryParameters.exerciseCategory: category as Any
            ]
        )
    }

    func logExerciseAddToWorkout(exerciseId: String, exerciseName: String) {
        logKpknEvent(
            eventName: TelemetryEvents.exerciceAddToWorkout,
            params: [
                TelemetryParameters.exerciseId: exerciseId,
                TelemetryParameters.exerciseName: exerciseName
            ]
        )
    }

    func logNavigation(from: String, to: String) {
        logKpknEvent(
            eventName: TelemetryEvents.navigation,
            params: [
                TelemetryParameters.navigationFrom: from,
                TelemetryParameters.navigationTo: to
            ]
        )
    }

    func logDeepLinkOpen(path: String) {
        logKpknEvent(
            eventName: TelemetryEvents.deepLinkOpen,
            params: [
                TelemetryParameters.deepLinkPath: path
            ]
        )
    }

    func logAIRequest(type: String, model: String, input: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.aiRequest,
            params: [
                TelemetryParameters.aiType: type,
                TelemetryParameters.aiModel: model,
                TelemetryParameters.aiInput: input.map { anonymizeText($0) } as Any
            ]
        )
    }

    func logAISuccess(type: String, model: String, output: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.aiSuccess,
            params: [
                TelemetryParameters.aiType: type,
                TelemetryParameters.aiModel: model,
                TelemetryParameters.aiOutput: output.map { anonymizeText($0) } as Any,
                TelemetryParameters.success: true
            ]
        )
    }

    func logAIFailed(type: String, model: String, error: String? = nil) {
        logKpknEvent(
            eventName: TelemetryEvents.aiFailed,
            params: [
                TelemetryParameters.aiType: type,
                TelemetryParameters.aiModel: model,
                TelemetryParameters.errorMessage: error as Any,
                TelemetryParameters.success: false
            ]
        )
    }

    class PerformanceTrace {
        private let telemetry: KpknTelemetry
        private let name: String
        private let startTime: TimeInterval

        init(telemetry: KpknTelemetry, name: String) {
            self.telemetry = telemetry
            self.name = name
            self.startTime = ProcessInfo.processInfo.systemUptime
        }

        func stop() {
            let duration = ProcessInfo.processInfo.systemUptime - startTime
            telemetry.logEvent(
                eventName: "performance_trace",
                params: [
                    TelemetryParameters.action: name,
                    TelemetryParameters.duration: duration,
                    TelemetryParameters.success: true
                ]
            )
        }
    }

    func startPerformanceTrace(name: String) -> PerformanceTrace {
        PerformanceTrace(telemetry: telemetry, name: name)
    }

    func logGlobalError(_ error: Error, context: String? = nil) {
        telemetry.logException(error, fatal: false)
        telemetry.logEvent(
            eventName: "global_error",
            params: [
                TelemetryParameters.errorMessage: error.localizedDescription,
                TelemetryParameters.errorStackTrace: String("\(error)".prefix(500)),
                TelemetryParameters.action: context as Any
            ]
        )
    }
}
