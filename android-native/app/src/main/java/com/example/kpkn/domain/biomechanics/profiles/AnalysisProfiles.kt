package com.example.kpkn.domain.biomechanics.profiles

import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind.FRONT
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind.LYING
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind.OBLIQUE
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind.SIDE

/**
 * La tabla de los 24 perfiles (§5 y briefs VT2a y VT2b), en el orden de [AnalysisProfileId]. Son los perfiles BASE:
 * `usesPlate` en `false`, `mvtMps` en `null` y `framing.needsPlate` en `false`. El [AnalysisProfileResolver] los
 * ajusta al material (disco) y a la definición (MVT y, en unas pocas, el arranque).
 *
 * Reglas comunes: el encuadre siempre pide cara a la vista; los pies, salvo en los perfiles «sin pies» (máquinas,
 * poleas, banca y mancuernas sentadas o tumbadas). Las amplitudes mínimas van en la unidad de la señal (§6).
 */
object AnalysisProfiles {
    /** Los 24 perfiles, en el orden de [AnalysisProfileId]. */
    val all: List<AnalysisProfile> = buildTable()

    private val byId: Map<AnalysisProfileId, AnalysisProfile> = all.associateBy { it.id }

    /** El perfil base de [id]. */
    fun of(id: AnalysisProfileId): AnalysisProfile = byId.getValue(id)
}

private fun views(vararg kinds: ViewKind): Set<ViewKind> = linkedSetOf(*kinds)

/** Encuadre base: la cara siempre; el disco lo enciende el resolver (SQUAT, DEADLIFT, BENCH y OHP) según el material. */
private fun framing(preferred: Set<ViewKind>, feet: Boolean = true): FramingSpec =
    FramingSpec(preferredViews = preferred, needsPlate = false, needsFeet = feet, needsFace = true)

private fun buildTable(): List<AnalysisProfile> = listOf(
    // Sentadilla: knee_dominant, knee_hip_dominant, belt_squat. Cuenta de lado, de frente y oblicuo; mide de lado.
    AnalysisProfile(
        id = AnalysisProfileId.SQUAT, tier = ProfileTier.A, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, FRONT, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.DROP_HIP_MID, plateSignal = CountSignal.PLATE_DROP, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.PLATE_THEN_SHOULDER, perSide = false,
        depthRule = DepthRule.SQUAT_PARALLEL, lockoutRule = null, pathRule = PathRule.MIDFOOT_VERTICAL,
        mvtMps = null, minAmplitude = 0.08, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.10,
    ),
    // Zancada y sentadilla unilateral (búlgara, pistola): rodilla delantera.
    AnalysisProfile(
        id = AnalysisProfileId.LUNGE, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.DROP_HIP_MID, plateSignal = CountSignal.PLATE_DROP, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.PLATE_THEN_SHOULDER, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.06, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.08,
    ),
    // Subida al cajón: arranca subiendo.
    AnalysisProfile(
        id = AnalysisProfileId.STEP_UP, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.RISE_HIP_MID, plateSignal = null, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.08, framing = framing(views(SIDE)),
    ),
    // Prensa, hack, V-squat y péndulo: manda el ángulo de rodilla. Sin punto de barra: BarPointRule no tiene «tobillo»
    // (prensa) y en el hack el hombro sigue al trineo, así que la velocidad sale del ángulo (°/s).
    AnalysisProfile(
        id = AnalysisProfileId.MACHINE_SQUAT, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE), metricViews = views(SIDE),
        poseSignal = CountSignal.KNEE_FLEXION, plateSignal = null, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 30.0, framing = framing(views(SIDE), feet = false),
    ),
    // Peso muerto convencional y sumo (también hexagonal y mancuernas) y su variante con déficit: arranca subiendo.
    AnalysisProfile(
        id = AnalysisProfileId.DEADLIFT, tier = ProfileTier.A, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.RISE_SHOULDER_NEAR, plateSignal = CountSignal.PLATE_RISE, romAngle = RomAngle.HIP,
        barPoint = BarPointRule.PLATE_THEN_HAND, perSide = false,
        depthRule = null, lockoutRule = LockoutRule.DEADLIFT, pathRule = PathRule.MIDFOOT_VERTICAL,
        mvtMps = null, minAmplitude = 0.10, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.10,
    ),
    // Rumano, buenos días, pull-through: la cadera manda; arranca bajando.
    AnalysisProfile(
        id = AnalysisProfileId.HINGE, tier = ProfileTier.A, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.HIP_FLEXION, plateSignal = CountSignal.PLATE_DROP, romAngle = RomAngle.HIP,
        barPoint = BarPointRule.PLATE_THEN_HAND, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.MIDFOOT_VERTICAL,
        mvtMps = null, minAmplitude = 30.0, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.08,
    ),
    // Hip thrust, puentes y frog pumps: tumbado o de lado.
    AnalysisProfile(
        id = AnalysisProfileId.HIP_THRUST, tier = ProfileTier.A, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, LYING), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.HIP_ANGLE, plateSignal = CountSignal.PLATE_RISE, romAngle = RomAngle.HIP,
        barPoint = BarPointRule.PLATE_THEN_HIP, perSide = false,
        depthRule = null, lockoutRule = LockoutRule.HIP_THRUST, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 25.0, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.05,
    ),
    // Hiperextensión a 45° y lumbar en máquina.
    AnalysisProfile(
        id = AnalysisProfileId.BACK_EXTENSION, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, LYING, OBLIQUE), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.HIP_FLEXION, plateSignal = null, romAngle = RomAngle.HIP,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 25.0, framing = framing(views(SIDE)),
    ),
    // Press horizontales (banca, inclinado, declinado, suelo…): tumbado, sin pies; con barra, el disco manda.
    AnalysisProfile(
        id = AnalysisProfileId.BENCH, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, LYING), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.DROP_HAND_NEAR, plateSignal = CountSignal.PLATE_DROP, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.PLATE_THEN_HAND, perSide = false,
        depthRule = DepthRule.BENCH_RELATIVE, lockoutRule = null, pathRule = PathRule.CONSISTENCY,
        mvtMps = null, minAmplitude = 0.05, framing = framing(views(SIDE), feet = false),
        plateMinAmplitude = 0.05,
    ),
    // Fondos en paralelas y entre bancos.
    AnalysisProfile(
        id = AnalysisProfileId.DIP, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.DROP_SHOULDER_NEAR, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = DepthRule.DIP_SHOULDER, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.05, framing = framing(views(SIDE)),
    ),
    // Flexiones (rodillas, diamante, arquero, pike).
    AnalysisProfile(
        id = AnalysisProfileId.PUSH_UP, tier = ProfileTier.A, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, LYING), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.DROP_SHOULDER_NEAR, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = DepthRule.PUSH_UP_ELBOW, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.05, framing = framing(views(SIDE)),
    ),
    // Press vertical (militar, sentado, Arnold, Z): con mancuernas también vale de frente; arranca subiendo.
    AnalysisProfile(
        id = AnalysisProfileId.OHP, tier = ProfileTier.A, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, FRONT, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.ELBOW_ANGLE, plateSignal = CountSignal.PLATE_RISE, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.PLATE_THEN_HAND, perSide = false,
        depthRule = null, lockoutRule = LockoutRule.OHP, pathRule = PathRule.MIDFOOT_VERTICAL,
        mvtMps = null, minAmplitude = 40.0, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.10,
    ),
    // Dominadas, jalones y remo al cuello: la distancia hombro-muñeca; de frente o de lado, nunca de espaldas.
    AnalysisProfile(
        id = AnalysisProfileId.PULL_VERTICAL, tier = ProfileTier.A, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(FRONT, SIDE, OBLIQUE), metricViews = views(FRONT, SIDE),
        poseSignal = CountSignal.SHOULDER_TO_WRIST_CLOSE, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.SHOULDER_MID, perSide = false,
        depthRule = DepthRule.CHIN_OVER_BAR, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.25, framing = framing(views(FRONT, SIDE), feet = false),
    ),
    // Remos (barra, mancuerna, polea, T, invertido, face pull): el renegado y el gorila se cuentan por lado.
    AnalysisProfile(
        id = AnalysisProfileId.ROW, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.SHOULDER_TO_WRIST_CLOSE, plateSignal = CountSignal.PLATE_RISE, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.PLATE_THEN_HAND, perSide = true,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.20, framing = framing(views(SIDE)),
        plateMinAmplitude = 0.05,
    ),
    // Curl de bíceps: por lado, así que también se cuenta de frente.
    AnalysisProfile(
        id = AnalysisProfileId.CURL, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, FRONT, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.ELBOW_FLEXION, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.HAND, perSide = true,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 40.0, framing = framing(views(SIDE), feet = false),
    ),
    // Tríceps con el reposo en extensión (francés, skull crusher, JM, por encima de la cabeza): arranca bajando.
    AnalysisProfile(
        id = AnalysisProfileId.TRICEPS_EXTENDED_REST, tier = ProfileTier.B, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE, LYING), metricViews = views(SIDE),
        poseSignal = CountSignal.ELBOW_FLEXION, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.HAND, perSide = true,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 35.0, framing = framing(views(SIDE), feet = false),
    ),
    // Tríceps con el reposo flexionado (polea, patada, katana, cruzado, máquina): arranca extendiendo.
    AnalysisProfile(
        id = AnalysisProfileId.TRICEPS_FLEXED_REST, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, OBLIQUE), metricViews = views(SIDE),
        poseSignal = CountSignal.ELBOW_ANGLE, plateSignal = null, romAngle = RomAngle.ELBOW,
        barPoint = BarPointRule.HAND, perSide = true,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 35.0, framing = framing(views(SIDE), feet = false),
    ),
    // Elevaciones (lateral, frontal, posterior): β solo depende de la vertical, así que vale en cualquier vista.
    AnalysisProfile(
        id = AnalysisProfileId.RAISE, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(FRONT, SIDE, OBLIQUE), metricViews = views(FRONT, SIDE),
        poseSignal = CountSignal.ARM_ELEVATION, plateSignal = null, romAngle = RomAngle.ARM_ELEVATION,
        barPoint = BarPointRule.NONE, perSide = true,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 35.0, framing = framing(views(FRONT), feet = false),
    ),
    // Extensión de cuádriceps en máquina o polea.
    AnalysisProfile(
        id = AnalysisProfileId.LEG_EXTENSION, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE), metricViews = views(SIDE),
        poseSignal = CountSignal.KNEE_EXTENSION_ANGLE, plateSignal = null, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 35.0, framing = framing(views(SIDE), feet = false),
    ),
    // Curl femoral (tumbado, sentado, de pie, con balón o sliders).
    AnalysisProfile(
        id = AnalysisProfileId.LEG_CURL, tier = ProfileTier.B, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE, LYING), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.KNEE_FLEXION, plateSignal = null, romAngle = RomAngle.KNEE,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 35.0, framing = framing(views(SIDE), feet = false),
    ),
    // Gemelos de pie: poca amplitud (BH) y sin ROM angular; ver los pies es lo que importa.
    AnalysisProfile(
        id = AnalysisProfileId.CALF, tier = ProfileTier.C, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE), metricViews = views(SIDE),
        poseSignal = CountSignal.RISE_TRUNK_MID, plateSignal = null, romAngle = null,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.015, framing = framing(views(SIDE)),
    ),
    // Encogimientos: de frente, con la oreja como referencia de los hombros.
    AnalysisProfile(
        id = AnalysisProfileId.SHRUG, tier = ProfileTier.C, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(FRONT, SIDE), metricViews = views(FRONT),
        poseSignal = CountSignal.SHRUG_SIGNAL, plateSignal = null, romAngle = null,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.02, framing = framing(views(FRONT), feet = false),
    ),
    // Aperturas con mancuernas (plano, inclinado, declinado): la muñeca baja en la excéntrica.
    AnalysisProfile(
        id = AnalysisProfileId.FLY, tier = ProfileTier.C, startPhase = StartPhase.ECCENTRIC_FIRST,
        countViews = views(SIDE, LYING), metricViews = views(SIDE, LYING),
        poseSignal = CountSignal.DROP_WRIST_NEAR, plateSignal = null, romAngle = null,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.05, framing = framing(views(SIDE), feet = false),
    ),
    // Gemelos sentado: el tronco no se mueve, pero las rodillas suben con los talones; manda la rodilla cercana.
    AnalysisProfile(
        id = AnalysisProfileId.CALF_SEATED, tier = ProfileTier.C, startPhase = StartPhase.CONCENTRIC_FIRST,
        countViews = views(SIDE), metricViews = views(SIDE),
        poseSignal = CountSignal.RISE_KNEE_NEAR, plateSignal = null, romAngle = null,
        barPoint = BarPointRule.NONE, perSide = false,
        depthRule = null, lockoutRule = null, pathRule = PathRule.NONE,
        mvtMps = null, minAmplitude = 0.015, framing = framing(views(SIDE)),
    ),
)
