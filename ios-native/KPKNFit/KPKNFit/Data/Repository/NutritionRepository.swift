import Foundation

internal final class NutritionRepository {
    static let shared = NutritionRepository()

    var nutritionLogs: [NutritionLog] = []
    var nutritionPlans: [NutritionPlan] = []
    var activeNutritionPlanId: String?
    var bodyMeasurements: [BodyMeasurementEntry] = []

    var activeNutritionPlan: NutritionPlan? {
        nutritionPlans.first { $0.id == activeNutritionPlanId }
    }

    private init() {}
}
