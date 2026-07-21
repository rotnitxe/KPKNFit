import Foundation

// MARK: - Lift Type

enum LiftType {
    case squatLowBar
    case squatHighBar
    case squatFront
    case deadliftConventional
    case deadliftSumo
    case benchPress
    case overheadPress
    case barbellRow
    case lunge
    case hipThrust
    case pullUp
    case dip

    var label: String {
        switch self {
        case .squatLowBar: return "Sentadilla Low-Bar"
        case .squatHighBar: return "Sentadilla High-Bar"
        case .squatFront: return "Sentadilla Frontal"
        case .deadliftConventional: return "Peso Muerto Convencional"
        case .deadliftSumo: return "Peso Muerto Sumo"
        case .benchPress: return "Press de Banca"
        case .overheadPress: return "Press Militar"
        case .barbellRow: return "Remo con Barra"
        case .lunge: return "Estocada"
        case .hipThrust: return "Hip Thrust"
        case .pullUp: return "Dominada"
        case .dip: return "Fondos"
        }
    }

    var bodyPart: String {
        switch self {
        case .squatLowBar, .squatHighBar, .squatFront, .deadliftConventional, .deadliftSumo, .lunge, .hipThrust:
            return "lower"
        case .benchPress, .overheadPress, .barbellRow, .pullUp, .dip:
            return "upper"
        }
    }
}

// MARK: - Lever Type

enum LeverType {
    case firstClass
    case secondClass
    case thirdClass
    case fourthClass

    var label: String {
        switch self {
        case .firstClass: return "Primera clase"
        case .secondClass: return "Segunda clase"
        case .thirdClass: return "Tercera clase"
        case .fourthClass: return "Cuarta clase"
        }
    }

    var description: String {
        switch self {
        case .firstClass:
            return "Fulcrum entre la carga y la fuerza (ej: extensión de cuello, extensión de rodilla). El esfuerzo y la carga están a lados opuestos del fulcrum."
        case .secondClass:
            return "Carga entre el fulcrum y la fuerza (ej: flexión plantar, puntillas). El esfuerzo es siempre menor que la carga."
        case .thirdClass:
            return "Fuerza entre el fulcrum y la carga (ej: flexión de codo, flexión de cadera). El más común en el cuerpo humano. Requiere más fuerza que la carga."
        case .fourthClass:
            return "Variante de tercera clase con palanca compuesta. Común en movimientos articulares complejos."
        }
    }
}

// MARK: - Anthropometry

struct Anthropometry {
    var heightCm: Double = 175.0
    var weightKg: Double = 80.0
    var femurLengthCm: Double = 48.0
    var tibiaLengthCm: Double = 42.0
    var torsoLengthCm: Double = 50.0
    var humerusLengthCm: Double = 34.0
    var forearmLengthCm: Double = 28.0
    var footLengthCm: Double = 27.0
    var armSpanCm: Double = 178.0

    var femurRatio: Double { femurLengthCm / torsoLengthCm }
    var tibiaRatio: Double { tibiaLengthCm / femurLengthCm }
    var armToTorsoRatio: Double { (humerusLengthCm + forearmLengthCm) / torsoLengthCm }

    var anthropometricProfile: String {
        if femurRatio > 1.1 {
            return "Fémur largo — preferencia high-bar/frontal"
        } else if femurRatio < 0.9 {
            return "Fémur corto — ventaja low-bar"
        } else {
            return "Proporción balanceada"
        }
    }
}

// MARK: - Joint Angle

struct JointAngle {
    var joint: String
    var angleDegrees: Double
    var leverType: LeverType
    var momentArm: Double
    var mechanicalAdvantage: Double
    var torqueRatio: Double
}

// MARK: - Biomechanical Solve

struct BiomechanicalSolve {
    var liftType: LiftType
    var jointAngles: [JointAngle]
    var femurAngle: Double
    var torsoAngle: Double
    var hipHeight: Double
    var barPosition: Double
    var estimatedTorque: [String: Double]
    var leverClassification: [String: LeverType]
    var overallDifficulty: Double
}

// MARK: - Biomechanics Engine

enum BiomechanicsEngine {

    private static let gravity = 9.81

    // MARK: - Public API

    static func solve(
        lift: LiftType,
        depth: Double = 0.8,
        anthropometry: Anthropometry = Anthropometry(),
        barWeightKg: Double = 100.0,
        stanceWidth: Double = 0.3,
        barPosition: Double = 0.5
    ) -> BiomechanicalSolve {
        let d = min(max(depth, 0.0), 1.0)
        let w = min(max(stanceWidth, 0.1), 1.0)
        let bp = min(max(barPosition, 0.0), 1.0)

        switch lift {
        case .squatLowBar:
            return solveSquat(depth: d, anthro: anthropometry, weightKg: barWeightKg, stanceWidth: w, barBias: bp * 0.45)
        case .squatHighBar:
            return solveSquat(depth: d, anthro: anthropometry, weightKg: barWeightKg, stanceWidth: w, barBias: 0.45 + bp * 0.4)
        case .squatFront:
            return solveSquat(depth: d, anthro: anthropometry, weightKg: barWeightKg, stanceWidth: w, barBias: 1.0, frontSquat: true)
        case .deadliftConventional:
            return solveDeadlift(depth: d, anthro: anthropometry, weightKg: barWeightKg, wide: false)
        case .deadliftSumo:
            return solveDeadlift(depth: d, anthro: anthropometry, weightKg: barWeightKg, wide: true)
        case .benchPress:
            return solveBench(depth: d, anthro: anthropometry, weightKg: barWeightKg)
        case .overheadPress:
            return solveOverhead(depth: d, anthro: anthropometry, weightKg: barWeightKg)
        case .barbellRow:
            return solveRow(depth: d, anthro: anthropometry, weightKg: barWeightKg)
        case .lunge:
            return solveLunge(depth: d, anthro: anthropometry, weightKg: barWeightKg)
        case .hipThrust:
            return solveHipThrust(depth: d, anthro: anthropometry, weightKg: barWeightKg)
        case .pullUp:
            return solvePullUp(depth: d, anthro: anthropometry, weightKg: anthropometry.weightKg)
        case .dip:
            return solveDip(depth: d, anthro: anthropometry, weightKg: anthropometry.weightKg)
        }
    }

    static func classifyLever(joint: String, movement: String) -> LeverType {
        if joint.contains("codo") && movement.contains("flexión") { return .thirdClass }
        if joint.contains("cadera") && movement.contains("flexión") { return .thirdClass }
        if joint.contains("rodilla") && movement.contains("extensión") { return .firstClass }
        if joint.contains("tobillo") { return .secondClass }
        if joint.contains("hombro") { return .thirdClass }
        if joint.contains("muñeca") { return .thirdClass }
        return .thirdClass
    }

    static func momentArm(limbLengthCm: Double, jointAngleDegrees: Double) -> Double {
        let radians = jointAngleDegrees * .pi / 180.0
        return limbLengthCm * sin(radians)
    }

    static func torque(weightN: Double, momentArmCm: Double) -> Double {
        return weightN * (momentArmCm / 100.0)
    }

    // MARK: - Private Solvers

    private static func solveSquat(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
        stanceWidth: Double,
        barBias: Double,
        frontSquat: Bool = false
    ) -> BiomechanicalSolve {
        let effectiveBarBias = min(max(barBias, 0.0), 1.0)
        let stanceCentered = min(max((stanceWidth - 0.35) / 0.35, -0.7), 1.2)
        let kneeForwardBias = 0.18 + effectiveBarBias * 0.18 - stanceCentered * 0.11 + (frontSquat ? 0.08 : 0.0)
        let hipBackwardBias = 0.20 + (1.0 - effectiveBarBias) * 0.16 + stanceCentered * 0.08

        let femurAngle = min(max(
            28.0 + depth * (56.0 - stanceCentered * 5.0 + (frontSquat ? 4.0 : 0.0)),
            24.0
        ), 92.0)

        let torsoAngle = min(max(
            18.0 + depth * (34.0 - effectiveBarBias * 12.0 + stanceCentered * 4.5) +
                (1.0 - effectiveBarBias) * 6.0 - (frontSquat ? 8.0 : 0.0),
            8.0
        ), 60.0)

        let kneeAngle = min(max(
            femurAngle + 14.0 + kneeForwardBias * 34.0,
            28.0
        ), 122.0)

        let hipAngle = min(max(
            torsoAngle + femurAngle * 0.58 + hipBackwardBias * 18.0,
            32.0
        ), 130.0)

        let ankleAngle = min(max(
            8.0 + femurAngle * 0.22 + effectiveBarBias * 8.0 - stanceCentered * 4.0,
            6.0
        ), 42.0)

        let hipHeight = max(
            anthro.tibiaLengthCm * cos(kneeAngle * 0.42 * .pi / 180.0) +
                anthro.femurLengthCm * cos(femurAngle * .pi / 180.0),
            18.0
        )

        let barHeight = max(
            anthro.torsoLengthCm * cos(torsoAngle * .pi / 180.0) + hipHeight +
                6.0 + effectiveBarBias * 12.0 + (frontSquat ? 6.0 : 0.0),
            hipHeight + 12.0
        )

        let totalWeightN = (weightKg + anthro.weightKg * 0.6) * gravity

        let jointAngles = [
            JointAngle(
                joint: "rodilla",
                angleDegrees: kneeAngle,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: kneeAngle * (0.55 + effectiveBarBias * 0.1)),
                mechanicalAdvantage: 1.0 / (1.0 + kneeAngle / 120.0),
                torqueRatio: kneeAngle / 120.0
            ),
            JointAngle(
                joint: "cadera",
                angleDegrees: hipAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.femurLengthCm, jointAngleDegrees: torsoAngle + hipBackwardBias * 18.0),
                mechanicalAdvantage: 1.0 / (1.0 + hipAngle / 120.0),
                torqueRatio: hipAngle / 130.0
            ),
            JointAngle(
                joint: "tobillo",
                angleDegrees: ankleAngle,
                leverType: .secondClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: ankleAngle + 10.0),
                mechanicalAdvantage: 1.1 + effectiveBarBias * 0.15,
                torqueRatio: ankleAngle / 45.0
            ),
        ]

        let torqueMap: [String: Double] = [
            "rodilla": torque(
                weightN: totalWeightN * (0.92 + effectiveBarBias * 0.16 - stanceCentered * 0.12 + (frontSquat ? 0.12 : 0.0)),
                momentArmCm: jointAngles[0].momentArm
            ),
            "cadera": torque(
                weightN: totalWeightN * (0.95 + (1.0 - effectiveBarBias) * 0.18 + stanceCentered * 0.16 - (frontSquat ? 0.08 : 0.0)),
                momentArmCm: jointAngles[1].momentArm
            ),
            "tobillo": torque(
                weightN: totalWeightN * 0.3,
                momentArmCm: jointAngles[2].momentArm
            ),
        ]

        let leverMap: [String: LeverType] = [
            "rodilla": .firstClass,
            "cadera": .thirdClass,
            "tobillo": .secondClass,
        ]

        let averageTorqueRatio = jointAngles.map { $0.torqueRatio }.reduce(0, +) / Double(jointAngles.count)
        let maxTorqueRatio = min(max(torqueMap.values.max()! / ((weightKg + anthro.weightKg) * 1.8), 0.0), 1.0)
        let difficulty = averageTorqueRatio * 0.55 + maxTorqueRatio * 0.45

        return BiomechanicalSolve(
            liftType: frontSquat ? .squatFront : (effectiveBarBias >= 0.45 ? .squatHighBar : .squatLowBar),
            jointAngles: jointAngles,
            femurAngle: femurAngle,
            torsoAngle: torsoAngle,
            hipHeight: hipHeight,
            barPosition: barHeight,
            estimatedTorque: torqueMap,
            leverClassification: leverMap,
            overallDifficulty: min(max(difficulty, 0.0), 1.0)
        )
    }

    private static func solveDeadlift(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
        wide: Bool
    ) -> BiomechanicalSolve {
        let hipAngle = 30.0 + depth * 80.0
        let kneeAngle = 20.0 + depth * (wide ? 60.0 : 40.0)
        let torsoAngle = 90.0 - hipAngle * 0.7

        let totalWeightN = (weightKg + anthro.weightKg * 0.3) * gravity

        let jointAngles = [
            JointAngle(
                joint: "cadera",
                angleDegrees: hipAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.femurLengthCm, jointAngleDegrees: torsoAngle),
                mechanicalAdvantage: 0.8,
                torqueRatio: hipAngle / 110.0
            ),
            JointAngle(
                joint: "rodilla",
                angleDegrees: kneeAngle,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: kneeAngle),
                mechanicalAdvantage: 1.1,
                torqueRatio: kneeAngle / 80.0
            ),
            JointAngle(
                joint: "columna lumbar",
                angleDegrees: torsoAngle,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.torsoLengthCm * 0.5, jointAngleDegrees: torsoAngle),
                mechanicalAdvantage: 0.7,
                torqueRatio: torsoAngle / 90.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: wide ? .deadliftSumo : .deadliftConventional,
            jointAngles: jointAngles,
            femurAngle: kneeAngle,
            torsoAngle: torsoAngle,
            hipHeight: anthro.tibiaLengthCm + anthro.femurLengthCm * cos(kneeAngle * .pi / 180.0),
            barPosition: 22.0,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(torsoAngle / 90.0 * 0.4 + hipAngle / 110.0 * 0.6, 0.0), 1.0)
        )
    }

    private static func solveBench(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let shoulderAngle = 40.0 + depth * 50.0
        let elbowAngle = 30.0 + depth * 60.0
        let totalWeightN = weightKg * gravity

        let jointAngles = [
            JointAngle(
                joint: "hombro",
                angleDegrees: shoulderAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.humerusLengthCm, jointAngleDegrees: shoulderAngle),
                mechanicalAdvantage: 0.9,
                torqueRatio: shoulderAngle / 90.0
            ),
            JointAngle(
                joint: "codo",
                angleDegrees: elbowAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.forearmLengthCm, jointAngleDegrees: elbowAngle),
                mechanicalAdvantage: 0.85,
                torqueRatio: elbowAngle / 90.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: .benchPress,
            jointAngles: jointAngles,
            femurAngle: 0.0,
            torsoAngle: 0.0,
            hipHeight: 0.0,
            barPosition: 40.0 + anthro.torsoLengthCm * 0.5,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(shoulderAngle / 90.0 * 0.6 + elbowAngle / 90.0 * 0.4, 0.0), 1.0)
        )
    }

    private static func solveOverhead(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let shoulderAngle = 20.0 + depth * 70.0
        let elbowAngle = 10.0 + depth * 40.0
        let totalWeightN = weightKg * gravity

        let jointAngles = [
            JointAngle(
                joint: "hombro",
                angleDegrees: shoulderAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.humerusLengthCm, jointAngleDegrees: shoulderAngle),
                mechanicalAdvantage: 0.8,
                torqueRatio: shoulderAngle / 90.0
            ),
            JointAngle(
                joint: "codo",
                angleDegrees: elbowAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.forearmLengthCm, jointAngleDegrees: elbowAngle),
                mechanicalAdvantage: 0.9,
                torqueRatio: elbowAngle / 50.0
            ),
            JointAngle(
                joint: "columna lumbar",
                angleDegrees: 5.0 + depth * 15.0,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.torsoLengthCm * 0.3, jointAngleDegrees: 10.0),
                mechanicalAdvantage: 1.0,
                torqueRatio: depth * 0.3
            ),
        ]

        return BiomechanicalSolve(
            liftType: .overheadPress,
            jointAngles: jointAngles,
            femurAngle: 0.0,
            torsoAngle: 5.0 + depth * 15.0,
            hipHeight: 0.0,
            barPosition: anthro.torsoLengthCm + anthro.humerusLengthCm + anthro.forearmLengthCm,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(shoulderAngle / 90.0 * 0.5 + depth * 0.5, 0.0), 1.0)
        )
    }

    private static func solveRow(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let torsoAngle = 15.0 + depth * 60.0
        let elbowAngle = 20.0 + depth * 80.0
        let totalWeightN = weightKg * gravity

        let jointAngles = [
            JointAngle(
                joint: "hombro",
                angleDegrees: torsoAngle * 0.5 + elbowAngle * 0.3,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.humerusLengthCm, jointAngleDegrees: torsoAngle),
                mechanicalAdvantage: 0.85,
                torqueRatio: torsoAngle / 90.0
            ),
            JointAngle(
                joint: "codo",
                angleDegrees: elbowAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.forearmLengthCm, jointAngleDegrees: elbowAngle),
                mechanicalAdvantage: 0.9,
                torqueRatio: elbowAngle / 100.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: .barbellRow,
            jointAngles: jointAngles,
            femurAngle: 0.0,
            torsoAngle: torsoAngle,
            hipHeight: 0.0,
            barPosition: 22.0,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(torsoAngle / 75.0 * 0.6 + elbowAngle / 100.0 * 0.4, 0.0), 1.0)
        )
    }

    private static func solveLunge(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let frontKneeAngle = 30.0 + depth * 70.0
        let hipAngle = 20.0 + depth * 50.0
        let totalWeightN = (weightKg + anthro.weightKg * 0.7) * gravity

        let jointAngles = [
            JointAngle(
                joint: "rodilla frontal",
                angleDegrees: frontKneeAngle,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: frontKneeAngle),
                mechanicalAdvantage: 0.8,
                torqueRatio: frontKneeAngle / 100.0
            ),
            JointAngle(
                joint: "cadera frontal",
                angleDegrees: hipAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.femurLengthCm, jointAngleDegrees: hipAngle),
                mechanicalAdvantage: 0.85,
                torqueRatio: hipAngle / 70.0
            ),
            JointAngle(
                joint: "rodilla trasera",
                angleDegrees: frontKneeAngle * 1.2,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: frontKneeAngle * 1.2),
                mechanicalAdvantage: 0.7,
                torqueRatio: frontKneeAngle / 100.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: .lunge,
            jointAngles: jointAngles,
            femurAngle: hipAngle,
            torsoAngle: 5.0,
            hipHeight: anthro.femurLengthCm * cos(frontKneeAngle * .pi / 180.0) + anthro.tibiaLengthCm,
            barPosition: 22.0,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(frontKneeAngle / 100.0 * 0.5 + hipAngle / 70.0 * 0.5, 0.0), 1.0)
        )
    }

    private static func solveHipThrust(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let hipAngle = 10.0 + depth * 100.0
        let totalWeightN = (weightKg + anthro.weightKg * 0.4) * gravity

        let jointAngles = [
            JointAngle(
                joint: "cadera",
                angleDegrees: hipAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.femurLengthCm, jointAngleDegrees: 90.0 - hipAngle),
                mechanicalAdvantage: 1.0,
                torqueRatio: hipAngle / 110.0
            ),
            JointAngle(
                joint: "rodilla",
                angleDegrees: 90.0 - hipAngle * 0.3,
                leverType: .firstClass,
                momentArm: momentArm(limbLengthCm: anthro.tibiaLengthCm, jointAngleDegrees: 30.0),
                mechanicalAdvantage: 0.9,
                torqueRatio: 0.3
            ),
        ]

        return BiomechanicalSolve(
            liftType: .hipThrust,
            jointAngles: jointAngles,
            femurAngle: 90.0 - hipAngle,
            torsoAngle: 90.0 - hipAngle,
            hipHeight: anthro.torsoLengthCm * sin(hipAngle * .pi / 180.0),
            barPosition: 40.0,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(hipAngle / 110.0 * 0.8 + 0.2, 0.0), 1.0)
        )
    }

    private static func solvePullUp(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let shoulderAngle = 10.0 + depth * 70.0
        let elbowAngle = 10.0 + depth * 90.0
        let totalWeightN = weightKg * gravity

        let jointAngles = [
            JointAngle(
                joint: "hombro",
                angleDegrees: shoulderAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.humerusLengthCm, jointAngleDegrees: shoulderAngle),
                mechanicalAdvantage: 0.85,
                torqueRatio: shoulderAngle / 80.0
            ),
            JointAngle(
                joint: "codo",
                angleDegrees: elbowAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.forearmLengthCm, jointAngleDegrees: elbowAngle),
                mechanicalAdvantage: 0.8,
                torqueRatio: elbowAngle / 100.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: .pullUp,
            jointAngles: jointAngles,
            femurAngle: 0.0,
            torsoAngle: 0.0,
            hipHeight: 0.0,
            barPosition: anthro.torsoLengthCm + anthro.humerusLengthCm + anthro.forearmLengthCm + 10.0,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(depth * 0.7 + shoulderAngle / 80.0 * 0.3, 0.0), 1.0)
        )
    }

    private static func solveDip(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double
    ) -> BiomechanicalSolve {
        let shoulderAngle = 0.0 + depth * 40.0
        let elbowAngle = 10.0 + depth * 90.0
        let totalWeightN = weightKg * gravity

        let jointAngles = [
            JointAngle(
                joint: "hombro",
                angleDegrees: shoulderAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.humerusLengthCm, jointAngleDegrees: shoulderAngle),
                mechanicalAdvantage: 0.85,
                torqueRatio: shoulderAngle / 40.0
            ),
            JointAngle(
                joint: "codo",
                angleDegrees: elbowAngle,
                leverType: .thirdClass,
                momentArm: momentArm(limbLengthCm: anthro.forearmLengthCm, jointAngleDegrees: elbowAngle),
                mechanicalAdvantage: 0.8,
                torqueRatio: elbowAngle / 100.0
            ),
        ]

        return BiomechanicalSolve(
            liftType: .dip,
            jointAngles: jointAngles,
            femurAngle: 0.0,
            torsoAngle: shoulderAngle * 0.5,
            hipHeight: 0.0,
            barPosition: anthro.torsoLengthCm * 0.5,
            estimatedTorque: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, torque(weightN: totalWeightN, momentArmCm: $0.momentArm)) }),
            leverClassification: Dictionary(uniqueKeysWithValues: jointAngles.map { ($0.joint, $0.leverType) }),
            overallDifficulty: min(max(depth * 0.6 + elbowAngle / 100.0 * 0.4, 0.0), 1.0)
        )
    }
}
