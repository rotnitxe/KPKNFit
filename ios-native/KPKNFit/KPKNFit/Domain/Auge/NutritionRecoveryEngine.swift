import Foundation

enum NutritionRecoveryStatus {
    case deficit
    case maintenance
    case surplus
}

struct NutritionRecoveryResult {
    let recoveryTimeMultiplier: Double
    let status: NutritionRecoveryStatus
    let factors: [String]
}

enum NutritionRecoveryEngine {

    static func computeNutritionRecoveryMultiplier(
        nutritionLogs: [NutritionLog],
        settings: Settings,
        activePlan: NutritionPlan? = nil,
        stressLevel: Int = 3,
        hoursWindow: Int = 48
    ) -> NutritionRecoveryResult {
        var factors: [String] = []
        var multiplier = 1.0

        let nowMs = Date().timeIntervalSince1970 * 1000
        let windowStartMs = nowMs - Double(hoursWindow) * 3600_000.0

        let recentLogs = nutritionLogs.filter { log in
            guard let logDate = log.date.prefix(10).description.parseDate() else { return false }
            return logDate.timeIntervalSince1970 * 1000 > windowStartMs
        }

        let goals = deriveMacroGoals(settings: settings, activePlan: activePlan)
        let calorieGoal = goals.calorieGoal
        let proteinGoal = goals.proteinGoal

        if recentLogs.isEmpty {
            let fallback = settings.calorieGoalObjective
            switch fallback {
            case .DEFICIT:
                return NutritionRecoveryResult(
                    recoveryTimeMultiplier: 1.25,
                    status: .deficit,
                    factors: ["Sin datos recientes; asumiendo déficit según objetivo."]
                )
            case .SURPLUS:
                return NutritionRecoveryResult(
                    recoveryTimeMultiplier: 0.95,
                    status: .surplus,
                    factors: ["Sin datos recientes; asumiendo superávit según objetivo."]
                )
            case .MAINTENANCE:
                return NutritionRecoveryResult(
                    recoveryTimeMultiplier: 1.0,
                    status: .maintenance,
                    factors: []
                )
            }
        }

        var totalCal = 0.0
        var totalProtein = 0.0
        for log in recentLogs {
            for f in log.foods {
                totalCal += f.calories
                totalProtein += f.protein
            }
        }
        let daysInWindow = max(1.0, Double(hoursWindow) / 24.0)
        let avgCalories = totalCal / daysInWindow
        let avgProtein = totalProtein / daysInWindow

        let calRatio = calorieGoal > 0 ? avgCalories / Double(calorieGoal) : 1.0
        let proteinRatio = proteinGoal > 0 ? avgProtein / Double(proteinGoal) : 1.0

        let status: NutritionRecoveryStatus

        if calRatio < 0.9 {
            status = .deficit
            let deficitSeverity = 1.0 - calRatio
            multiplier = 1.0 + deficitSeverity * 1.2
            factors.append("Déficit calórico (~\((Int((1 - calRatio) * 100)))%). Recursos limitados para reparación.")
            if proteinRatio < 0.7 {
                multiplier *= 1.1
                factors.append("Proteína insuficiente agrava el déficit.")
            }
        } else if calRatio <= 1.1 {
            status = .maintenance
            if proteinRatio < 0.8 {
                multiplier = 1.05
                factors.append("Proteína por debajo del objetivo; ligera penalización.")
            } else {
                factors.append("Mantenimiento calórico. Recuperación estándar.")
            }
        } else {
            status = .surplus
            let surplusPct = (calRatio - 1.0) * 100.0

            if proteinRatio < 0.6 {
                multiplier = 1.05
                factors.append("Superávit sin suficiente proteína. La síntesis muscular está limitada.")
            } else if proteinRatio < 0.8 {
                if surplusPct < 15 {
                    multiplier = 0.92
                } else if surplusPct < 25 {
                    multiplier = 0.88
                } else {
                    multiplier = 0.92
                }
                factors.append("Superávit moderado (~\(Int(surplusPct))%) con proteína subóptima. Beneficio limitado.")
            } else {
                if surplusPct < 8 {
                    multiplier = 0.96
                } else if surplusPct < 18 {
                    multiplier = 0.86
                } else if surplusPct < 30 {
                    multiplier = 0.90
                } else {
                    multiplier = 0.96
                }
                if surplusPct < 8 {
                    factors.append("Superávit ligero (~\(Int(surplusPct))%). Pequeña mejora en recuperación.")
                } else if surplusPct < 18 {
                    factors.append("Superávit óptimo (~\(Int(surplusPct))%). Recuperación acelerada.")
                } else if surplusPct < 30 {
                    factors.append("Superávit alto (~\(Int(surplusPct))%). Beneficio decreciente.")
                } else {
                    factors.append("Superávit muy alto (~\(Int(surplusPct))%). Rendimientos decrecientes; no acelera más.")
                }
            }

            if stressLevel >= 4 && multiplier < 1.0 {
                multiplier = min(1.0, multiplier + 0.06)
                factors.append("Estrés elevado reduce parte del beneficio nutricional.")
            }
        }

        return NutritionRecoveryResult(
            recoveryTimeMultiplier: max(0.6, min(multiplier, 1.6)),
            status: status,
            factors: factors
        )
    }
}

private extension String {
    func parseDate() -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: self)
    }
}
