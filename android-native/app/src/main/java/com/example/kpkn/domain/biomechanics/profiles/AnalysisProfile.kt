package com.example.kpkn.domain.biomechanics.profiles

import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Los 24 perfiles de análisis (§5): cada ejercicio soportado del catálogo se mide con uno. [CALF_SEATED] va al final
 * (gemelos sentado: la señal es la rodilla, no el tronco) y lo elige el resolver; el §5 sigue con los otros 23.
 */
enum class AnalysisProfileId {
    SQUAT, LUNGE, STEP_UP, MACHINE_SQUAT, DEADLIFT, HINGE, HIP_THRUST, BACK_EXTENSION, BENCH, DIP,
    PUSH_UP, OHP, PULL_VERTICAL, ROW, CURL, TRICEPS_EXTENDED_REST, TRICEPS_FLEXED_REST, RAISE, LEG_EXTENSION, LEG_CURL,
    CALF, SHRUG, FLY, CALF_SEATED,
}

/** Nivel de fiabilidad esperado: A, el mejor cubierto; C, el más tosco (gemelos, encogimientos, aperturas). */
enum class ProfileTier { A, B, C }

/** Fase con la que arranca la repetición desde el reposo: la excéntrica (sentadilla, banca) o la concéntrica (peso muerto, curl). */
enum class StartPhase { ECCENTRIC_FIRST, CONCENTRIC_FIRST }

/**
 * Señal r(t) con la que se cuenta (§5), orientada para que el reposo sea el mínimo. Las fórmulas exactas viven en el
 * constructor de la señal (VT2b); aquí solo el sentido. Y hacia abajo, S = alto corporal (BH), n = lado cercano.
 */
enum class CountSignal {
    DROP_HIP_MID,           // +Y de la cadera media (BH)
    RISE_HIP_MID,           // −Y de la cadera media (BH)
    KNEE_FLEXION,           // 180 − θ(H,K,A) (°)
    KNEE_EXTENSION_ANGLE,   // θ(H,K,A) (°)
    HIP_FLEXION,            // 180 − θ(S,H,K) (°)
    HIP_ANGLE,              // θ(S,H,K) (°)
    RISE_SHOULDER_NEAR,     // −Y del hombro cercano (BH)
    DROP_SHOULDER_NEAR,     // +Y del hombro cercano (BH)
    DROP_HAND_NEAR,         // +Y del centro de la mano cercana (BH)
    RISE_HAND,              // −Y del centro de la mano (BH)
    ELBOW_FLEXION,          // 180 − θ(S,E,W) (°)
    ELBOW_ANGLE,            // θ(S,E,W) (°)
    SHOULDER_TO_WRIST_CLOSE, // 1 − d(S,W)/L̂ (razón)
    ARM_ELEVATION,          // β = acos((Y_E − Y_S)/L̂_brazo) (°)
    RISE_TRUNK_MID,         // −Y de ½(S_mid + H_mid) (BH)
    RISE_KNEE_NEAR,         // −Y de la rodilla cercana (BH)
    SHRUG_SIGNAL,           // (Y_oreja_media − Y_hombro_medio) (BH)
    DROP_WRIST_NEAR,        // +Y de la muñeca cercana (BH)
    PLATE_DROP,             // +Y del disco (BH)
    PLATE_RISE,             // −Y del disco (BH)
}

/** Articulación cuyo ángulo da el ROM del perfil. */
enum class RomAngle { KNEE, HIP, ELBOW, ARM_ELEVATION, TRUNK }

/** Punto que representa la barra: el disco si se ve y, si no, el sustituto de pose que dice la regla. */
enum class BarPointRule { PLATE_THEN_SHOULDER, PLATE_THEN_HAND, PLATE_THEN_HIP, HAND, SHOULDER_MID, NONE }

/** Criterio de profundidad o recorrido de cada repetición (§7, «Profundidad y criterios»). */
enum class DepthRule { SQUAT_PARALLEL, CHIN_OVER_BAR, PUSH_UP_ELBOW, DIP_SHOULDER, BENCH_RELATIVE }

/** Criterio de bloqueo al final de la concéntrica (§7, «Bloqueos»). */
enum class LockoutRule { DEADLIFT, HIP_THRUST, OHP }

/** Qué se mide de la trayectoria de la barra (§7, «Trayectoria»). */
enum class PathRule { MIDFOOT_VERTICAL, CONSISTENCY, NONE }

/**
 * Un perfil de análisis: qué señal cuenta, qué se mide y cómo hay que grabar. Los de [AnalysisProfiles] son los BASE;
 * el [AnalysisProfileResolver] los ajusta al material del ejercicio ([usesPlate], `framing.needsPlate`, [mvtMps]) y,
 * en algunas definiciones, a [startPhase].
 *
 * @property countViews vistas en las que se puede contar.
 * @property metricViews vistas en las que se pueden dar ROM y demás métricas absolutas.
 * @property poseSignal señal de conteo con pose.
 * @property plateSignal PLATE_DROP o PLATE_RISE si el disco puede ser la señal principal; `null` si nunca lo es.
 * @property romAngle articulación del ROM (`null`: sin ROM, como en gemelos, encogimientos y aperturas).
 * @property perSide puede contar por lado (alterno).
 * @property mvtMps velocidad mínima umbral para el 1RM por velocidad (m/s); `null` = no hay.
 * @property minAmplitude amplitud mínima de la señal para dar repeticiones: grados, BH o razón según [poseSignal].
 * @property usesPlate el disco es la señal principal: lo fija el resolver según el material.
 * @property plateMinAmplitude amplitud mínima en BH cuando cuenta el disco; solo tiene valor si hay [plateSignal].
 */
data class AnalysisProfile(
    val id: AnalysisProfileId,
    val tier: ProfileTier,
    val startPhase: StartPhase,
    val countViews: Set<ViewKind>,
    val metricViews: Set<ViewKind>,
    val poseSignal: CountSignal,
    val plateSignal: CountSignal?,
    val romAngle: RomAngle?,
    val barPoint: BarPointRule,
    val perSide: Boolean,
    val depthRule: DepthRule?,
    val lockoutRule: LockoutRule?,
    val pathRule: PathRule,
    val mvtMps: Double?,
    val minAmplitude: Double,
    val framing: FramingSpec,
    val usesPlate: Boolean = false,
    val plateMinAmplitude: Double? = null,
)
