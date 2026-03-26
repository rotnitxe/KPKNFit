package com.example.kpkn.domain.biomechanics

import kotlin.math.*

/**
 * Lift type enum with biomechanical properties.
 * Full expansion with multiple lift types from PWA Palitos Biomecánicos.
 */
enum class LiftType(val label: String, val bodyPart: String) {
    SQUAT_LOW_BAR("Sentadilla Low-Bar", "lower"),
    SQUAT_HIGH_BAR("Sentadilla High-Bar", "lower"),
    SQUAT_FRONT("Sentadilla Frontal", "lower"),
    DEADLIFT_CONVENTIONAL("Peso Muerto Convencional", "lower"),
    DEADLIFT_SUMO("Peso Muerto Sumo", "lower"),
    BENCH_PRESS("Press de Banca", "upper"),
    OVERHEAD_PRESS("Press Militar", "upper"),
    BARBELL_ROW("Remo con Barra", "upper"),
    LUNGE("Estocada", "lower"),
    HIP_THRUST("Hip Thrust", "lower"),
    PULL_UP("Dominada", "upper"),
    DIP("Fondos", "upper"),
}

/**
 * Lever class types for biomechanical analysis.
 */
enum class LeverType(val label: String, val description: String) {
    FIRST_CLASS(
        "Primera clase",
        "Fulcrum entre la carga y la fuerza (ej: extensión de cuello, extensión de rodilla). El esfuerzo y la carga están a lados opuestos del fulcrum."
    ),
    SECOND_CLASS(
        "Segunda clase",
        "Carga entre el fulcrum y la fuerza (ej: flexión plantar, puntillas). El esfuerzo es siempre menor que la carga."
    ),
    THIRD_CLASS(
        "Tercera clase",
        "Fuerza entre el fulcrum y la carga (ej: flexión de codo, flexión de cadera). El más común en el cuerpo humano. Requiere más fuerza que la carga."
    ),
    FOURTH_CLASS(
        "Cuarta clase",
        "Variante de tercera clase con palanca compuesta. Común en movimientos articulares complejos."
    ),
}

/**
 * Anthropometry data for the stickman model.
 */
data class Anthropometry(
    val heightCm: Double = 175.0,
    val weightKg: Double = 80.0,
    val femurLengthCm: Double = 48.0,
    val tibiaLengthCm: Double = 42.0,
    val torsoLengthCm: Double = 50.0,
    val humerusLengthCm: Double = 34.0,
    val forearmLengthCm: Double = 28.0,
    val footLengthCm: Double = 27.0,
    val armSpanCm: Double = 178.0,
) {
    val femurRatio: Double get() = femurLengthCm / torsoLengthCm
    val tibiaRatio: Double get() = tibiaLengthCm / femurLengthCm
    val armToTorsoRatio: Double get() = (humerusLengthCm + forearmLengthCm) / torsoLengthCm

    val anthropometricProfile: String get() = when {
        femurRatio > 1.1 -> "Fémur largo — preferencia high-bar/frontal"
        femurRatio < 0.9 -> "Fémur corto — ventaja low-bar"
        else -> "Proporción balanceada"
    }
}

/**
 * Joint angle calculation result.
 */
data class JointAngle(
    val joint: String,
    val angleDegrees: Double,
    val leverType: LeverType,
    val momentArm: Double, // cm
    val mechanicalAdvantage: Double, // ratio
    val torqueRatio: Double, // relative to max
)

/**
 * Biomechanical solve result for a given lift position.
 */
data class BiomechanicalSolve(
    val liftType: LiftType,
    val jointAngles: List<JointAngle>,
    val femurAngle: Double, // degrees from vertical
    val torsoAngle: Double, // degrees from vertical
    val hipHeight: Double, // cm from ground
    val barPosition: Double, // cm from ground
    val estimatedTorque: Map<String, Double>, // Nm per joint
    val leverClassification: Map<String, LeverType>,
    val overallDifficulty: Double, // 0-1
)

/**
 * Biomechanics engine — pure Kotlin calculations.
 * Migrated and expanded from PWA BiomechanicalStickman.tsx.
 */
object BiomechanicsEngine {

    private const val GRAVITY = 9.81

    /**
     * Calculates joint angles for a given lift at a given depth.
     *
     * @param lift Type of lift
     * @param depth 0.0 = top, 1.0 = full depth
     * @param anthropometry Athlete's body measurements
     * @param barWeightKg Weight on the bar
     * @param stanceWidth Relative stance width (0.0 = narrow, 1.0 = wide)
     * @param barPosition Relative bar position (0.0 = low, 1.0 = high)
     * @return Biomechanical solve
     */
    fun solve(
        lift: LiftType,
        depth: Double = 0.8,
        anthropometry: Anthropometry = Anthropometry(),
        barWeightKg: Double = 100.0,
        stanceWidth: Double = 0.3,
        barPosition: Double = 0.5,
    ): BiomechanicalSolve {
        val d = depth.coerceIn(0.0, 1.0)
        val w = stanceWidth.coerceIn(0.1, 1.0)
        val bp = barPosition.coerceIn(0.0, 1.0)

        return when (lift) {
            LiftType.SQUAT_LOW_BAR -> solveSquat(d, anthropometry, barWeightKg, w, barPosHigh = false)
            LiftType.SQUAT_HIGH_BAR -> solveSquat(d, anthropometry, barWeightKg, w, barPosHigh = true)
            LiftType.SQUAT_FRONT -> solveSquat(d, anthropometry, barWeightKg, w, barPosHigh = true, frontSquat = true)
            LiftType.DEADLIFT_CONVENTIONAL -> solveDeadlift(d, anthropometry, barWeightKg, wide = false)
            LiftType.DEADLIFT_SUMO -> solveDeadlift(d, anthropometry, barWeightKg, wide = true)
            LiftType.BENCH_PRESS -> solveBench(d, anthropometry, barWeightKg)
            LiftType.OVERHEAD_PRESS -> solveOverhead(d, anthropometry, barWeightKg)
            LiftType.BARBELL_ROW -> solveRow(d, anthropometry, barWeightKg)
            LiftType.LUNGE -> solveLunge(d, anthropometry, barWeightKg)
            LiftType.HIP_THRUST -> solveHipThrust(d, anthropometry, barWeightKg)
            LiftType.PULL_UP -> solvePullUp(d, anthropometry, anthropometry.weightKg)
            LiftType.DIP -> solveDip(d, anthropometry, anthropometry.weightKg)
        }
    }

    /**
     * Classifies the lever type for a given joint in a given movement.
     */
    fun classifyLever(joint: String, movement: String): LeverType = when {
        joint.contains("codo") && movement.contains("flexión") -> LeverType.THIRD_CLASS
        joint.contains("cadera") && movement.contains("flexión") -> LeverType.THIRD_CLASS
        joint.contains("rodilla") && movement.contains("extensión") -> LeverType.FIRST_CLASS
        joint.contains("tobillo") -> LeverType.SECOND_CLASS
        joint.contains("hombro") -> LeverType.THIRD_CLASS
        joint.contains("muñeca") -> LeverType.THIRD_CLASS
        else -> LeverType.THIRD_CLASS
    }

    /**
     * Calculates moment arm for a joint given angle and limb length.
     */
    fun momentArm(limbLengthCm: Double, jointAngleDegrees: Double): Double {
        val radians = Math.toRadians(jointAngleDegrees)
        return limbLengthCm * sin(radians)
    }

    /**
     * Calculates torque at a joint.
     */
    fun torque(weightN: Double, momentArmCm: Double): Double {
        return weightN * (momentArmCm / 100.0) // Convert cm to m
    }

    // ─── Private Solvers ───────────────────────────────────────────────

    private fun solveSquat(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
        stanceWidth: Double,
        barPosHigh: Boolean,
        frontSquat: Boolean = false,
    ): BiomechanicalSolve {
        val femurAngle = 30.0 + depth * 60.0 // degrees
        val torsoAngle = if (barPosHigh) {
            15.0 + depth * 30.0
        } else {
            20.0 + depth * 40.0
        }
        val hipHeight = anthro.femurLengthCm * cos(Math.toRadians(femurAngle)) +
                anthro.tibiaLengthCm

        val barHeight = anthro.torsoLengthCm * sin(Math.toRadians(torsoAngle)) + hipHeight +
                if (barPosHigh) 15.0 else 5.0

        val totalWeightN = (weightKg + anthro.weightKg * 0.6) * GRAVITY

        val jointAngles = listOf(
            JointAngle(
                joint = "rodilla",
                angleDegrees = femurAngle + 20.0,
                leverType = LeverType.FIRST_CLASS,
                momentArm = momentArm(anthro.tibiaLengthCm, femurAngle),
                mechanicalAdvantage = 1.0 / (1.0 + femurAngle / 90.0),
                torqueRatio = femurAngle / 90.0,
            ),
            JointAngle(
                joint = "cadera",
                angleDegrees = torsoAngle + femurAngle * 0.5,
                leverType = LeverType.THIRD_CLASS,
                momentArm = momentArm(anthro.femurLengthCm, torsoAngle),
                mechanicalAdvantage = 1.0 / (1.0 + torsoAngle / 90.0),
                torqueRatio = (torsoAngle + femurAngle) / 180.0,
            ),
            JointAngle(
                joint = "tobillo",
                angleDegrees = femurAngle * 0.3 + 10.0,
                leverType = LeverType.SECOND_CLASS,
                momentArm = momentArm(anthro.tibiaLengthCm, 30.0),
                mechanicalAdvantage = 1.2,
                torqueRatio = femurAngle * 0.3 / 90.0,
            ),
        )

        val torqueMap = mapOf(
            "rodilla" to torque(totalWeightN, jointAngles[0].momentArm),
            "cadera" to torque(totalWeightN, jointAngles[1].momentArm),
            "tobillo" to torque(totalWeightN * 0.3, jointAngles[2].momentArm),
        )

        val leverMap = mapOf(
            "rodilla" to LeverType.FIRST_CLASS,
            "cadera" to LeverType.THIRD_CLASS,
            "tobillo" to LeverType.SECOND_CLASS,
        )

        val difficulty = (femurAngle / 90.0 * 0.5 + torsoAngle / 90.0 * 0.5)

        return BiomechanicalSolve(
            liftType = if (frontSquat) LiftType.SQUAT_FRONT else if (barPosHigh) LiftType.SQUAT_HIGH_BAR else LiftType.SQUAT_LOW_BAR,
            jointAngles = jointAngles,
            femurAngle = femurAngle,
            torsoAngle = torsoAngle,
            hipHeight = hipHeight,
            barPosition = barHeight,
            estimatedTorque = torqueMap,
            leverClassification = leverMap,
            overallDifficulty = difficulty.coerceIn(0.0, 1.0),
        )
    }

    private fun solveDeadlift(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
        wide: Boolean,
    ): BiomechanicalSolve {
        val hipAngle = 30.0 + depth * 80.0
        val kneeAngle = 20.0 + depth * (if (wide) 60.0 else 40.0)
        val torsoAngle = 90.0 - hipAngle * 0.7

        val totalWeightN = (weightKg + anthro.weightKg * 0.3) * GRAVITY

        val jointAngles = listOf(
            JointAngle("cadera", hipAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.femurLengthCm, torsoAngle), 0.8, hipAngle / 110.0),
            JointAngle("rodilla", kneeAngle, LeverType.FIRST_CLASS,
                momentArm(anthro.tibiaLengthCm, kneeAngle), 1.1, kneeAngle / 80.0),
            JointAngle("columna lumbar", torsoAngle, LeverType.FIRST_CLASS,
                momentArm(anthro.torsoLengthCm * 0.5, torsoAngle), 0.7, torsoAngle / 90.0),
        )

        return BiomechanicalSolve(
            liftType = if (wide) LiftType.DEADLIFT_SUMO else LiftType.DEADLIFT_CONVENTIONAL,
            jointAngles = jointAngles,
            femurAngle = kneeAngle,
            torsoAngle = torsoAngle,
            hipHeight = anthro.tibiaLengthCm + anthro.femurLengthCm * cos(Math.toRadians(kneeAngle)),
            barPosition = 22.0, // Standard bar height
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (torsoAngle / 90.0 * 0.4 + hipAngle / 110.0 * 0.6).coerceIn(0.0, 1.0),
        )
    }

    private fun solveBench(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val shoulderAngle = 40.0 + depth * 50.0
        val elbowAngle = 30.0 + depth * 60.0
        val totalWeightN = weightKg * GRAVITY

        val jointAngles = listOf(
            JointAngle("hombro", shoulderAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.humerusLengthCm, shoulderAngle), 0.9, shoulderAngle / 90.0),
            JointAngle("codo", elbowAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.forearmLengthCm, elbowAngle), 0.85, elbowAngle / 90.0),
        )

        return BiomechanicalSolve(
            liftType = LiftType.BENCH_PRESS,
            jointAngles = jointAngles,
            femurAngle = 0.0,
            torsoAngle = 0.0,
            hipHeight = 0.0,
            barPosition = 40.0 + anthro.torsoLengthCm * 0.5,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (shoulderAngle / 90.0 * 0.6 + elbowAngle / 90.0 * 0.4).coerceIn(0.0, 1.0),
        )
    }

    private fun solveOverhead(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val shoulderAngle = 20.0 + depth * 70.0
        val elbowAngle = 10.0 + depth * 40.0
        val totalWeightN = weightKg * GRAVITY

        val jointAngles = listOf(
            JointAngle("hombro", shoulderAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.humerusLengthCm, shoulderAngle), 0.8, shoulderAngle / 90.0),
            JointAngle("codo", elbowAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.forearmLengthCm, elbowAngle), 0.9, elbowAngle / 50.0),
            JointAngle("columna lumbar", 5.0 + depth * 15.0, LeverType.FIRST_CLASS,
                momentArm(anthro.torsoLengthCm * 0.3, 10.0), 1.0, depth * 0.3),
        )

        return BiomechanicalSolve(
            liftType = LiftType.OVERHEAD_PRESS,
            jointAngles = jointAngles,
            femurAngle = 0.0,
            torsoAngle = 5.0 + depth * 15.0,
            hipHeight = 0.0,
            barPosition = anthro.torsoLengthCm + anthro.humerusLengthCm + anthro.forearmLengthCm,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (shoulderAngle / 90.0 * 0.5 + depth * 0.5).coerceIn(0.0, 1.0),
        )
    }

    private fun solveRow(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val torsoAngle = 15.0 + depth * 60.0
        val elbowAngle = 20.0 + depth * 80.0
        val totalWeightN = weightKg * GRAVITY

        val jointAngles = listOf(
            JointAngle("hombro", torsoAngle * 0.5 + elbowAngle * 0.3, LeverType.THIRD_CLASS,
                momentArm(anthro.humerusLengthCm, torsoAngle), 0.85, torsoAngle / 90.0),
            JointAngle("codo", elbowAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.forearmLengthCm, elbowAngle), 0.9, elbowAngle / 100.0),
        )

        return BiomechanicalSolve(
            liftType = LiftType.BARBELL_ROW,
            jointAngles = jointAngles,
            femurAngle = 0.0,
            torsoAngle = torsoAngle,
            hipHeight = 0.0,
            barPosition = 22.0,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (torsoAngle / 75.0 * 0.6 + elbowAngle / 100.0 * 0.4).coerceIn(0.0, 1.0),
        )
    }

    private fun solveLunge(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val frontKneeAngle = 30.0 + depth * 70.0
        val hipAngle = 20.0 + depth * 50.0
        val totalWeightN = (weightKg + anthro.weightKg * 0.7) * GRAVITY

        val jointAngles = listOf(
            JointAngle("rodilla frontal", frontKneeAngle, LeverType.FIRST_CLASS,
                momentArm(anthro.tibiaLengthCm, frontKneeAngle), 0.8, frontKneeAngle / 100.0),
            JointAngle("cadera frontal", hipAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.femurLengthCm, hipAngle), 0.85, hipAngle / 70.0),
            JointAngle("rodilla trasera", frontKneeAngle * 1.2, LeverType.FIRST_CLASS,
                momentArm(anthro.tibiaLengthCm, frontKneeAngle * 1.2), 0.7, frontKneeAngle / 100.0),
        )

        return BiomechanicalSolve(
            liftType = LiftType.LUNGE,
            jointAngles = jointAngles,
            femurAngle = hipAngle,
            torsoAngle = 5.0,
            hipHeight = anthro.femurLengthCm * cos(Math.toRadians(frontKneeAngle)) + anthro.tibiaLengthCm,
            barPosition = 22.0,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (frontKneeAngle / 100.0 * 0.5 + hipAngle / 70.0 * 0.5).coerceIn(0.0, 1.0),
        )
    }

    private fun solveHipThrust(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val hipAngle = 10.0 + depth * 100.0
        val totalWeightN = (weightKg + anthro.weightKg * 0.4) * GRAVITY

        val jointAngles = listOf(
            JointAngle("cadera", hipAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.femurLengthCm, 90.0 - hipAngle), 1.0, hipAngle / 110.0),
            JointAngle("rodilla", 90.0 - hipAngle * 0.3, LeverType.FIRST_CLASS,
                momentArm(anthro.tibiaLengthCm, 30.0), 0.9, 0.3),
        )

        return BiomechanicalSolve(
            liftType = LiftType.HIP_THRUST,
            jointAngles = jointAngles,
            femurAngle = 90.0 - hipAngle,
            torsoAngle = 90.0 - hipAngle,
            hipHeight = anthro.torsoLengthCm * sin(Math.toRadians(hipAngle)),
            barPosition = 40.0,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (hipAngle / 110.0 * 0.8 + 0.2).coerceIn(0.0, 1.0),
        )
    }

    private fun solvePullUp(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val shoulderAngle = 10.0 + depth * 70.0
        val elbowAngle = 10.0 + depth * 90.0
        val totalWeightN = weightKg * GRAVITY

        val jointAngles = listOf(
            JointAngle("hombro", shoulderAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.humerusLengthCm, shoulderAngle), 0.85, shoulderAngle / 80.0),
            JointAngle("codo", elbowAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.forearmLengthCm, elbowAngle), 0.8, elbowAngle / 100.0),
        )

        return BiomechanicalSolve(
            liftType = LiftType.PULL_UP,
            jointAngles = jointAngles,
            femurAngle = 0.0,
            torsoAngle = 0.0,
            hipHeight = 0.0,
            barPosition = anthro.torsoLengthCm + anthro.humerusLengthCm + anthro.forearmLengthCm + 10.0,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (depth * 0.7 + shoulderAngle / 80.0 * 0.3).coerceIn(0.0, 1.0),
        )
    }

    private fun solveDip(
        depth: Double,
        anthro: Anthropometry,
        weightKg: Double,
    ): BiomechanicalSolve {
        val shoulderAngle = 0.0 + depth * 40.0
        val elbowAngle = 10.0 + depth * 90.0
        val totalWeightN = weightKg * GRAVITY

        val jointAngles = listOf(
            JointAngle("hombro", shoulderAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.humerusLengthCm, shoulderAngle), 0.85, shoulderAngle / 40.0),
            JointAngle("codo", elbowAngle, LeverType.THIRD_CLASS,
                momentArm(anthro.forearmLengthCm, elbowAngle), 0.8, elbowAngle / 100.0),
        )

        return BiomechanicalSolve(
            liftType = LiftType.DIP,
            jointAngles = jointAngles,
            femurAngle = 0.0,
            torsoAngle = shoulderAngle * 0.5,
            hipHeight = 0.0,
            barPosition = anthro.torsoLengthCm * 0.5,
            estimatedTorque = jointAngles.associate { it.joint to torque(totalWeightN, it.momentArm) },
            leverClassification = jointAngles.associate { it.joint to it.leverType },
            overallDifficulty = (depth * 0.6 + elbowAngle / 100.0 * 0.4).coerceIn(0.0, 1.0),
        )
    }
}
