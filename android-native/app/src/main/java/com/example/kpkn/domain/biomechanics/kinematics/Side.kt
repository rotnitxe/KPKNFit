package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark

/** Lado del cuerpo del atleta (no de la imagen). */
enum class Side {
    LEFT,
    RIGHT,
    ;

    /** El lado contrario. */
    val opposite: Side get() = if (this == LEFT) RIGHT else LEFT
}

/** Hombro, codo, muñeca, … del lado [this]: atajos para no repetir `if (side == LEFT)` en cada llamada. */
internal fun Side.pick(left: PoseLandmark, right: PoseLandmark): PoseLandmark = if (this == Side.LEFT) left else right

internal fun Side.shoulder(): PoseLandmark = pick(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
internal fun Side.elbow(): PoseLandmark = pick(PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW)
internal fun Side.wrist(): PoseLandmark = pick(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
internal fun Side.pinky(): PoseLandmark = pick(PoseLandmark.LEFT_PINKY, PoseLandmark.RIGHT_PINKY)
internal fun Side.index(): PoseLandmark = pick(PoseLandmark.LEFT_INDEX, PoseLandmark.RIGHT_INDEX)
internal fun Side.thumb(): PoseLandmark = pick(PoseLandmark.LEFT_THUMB, PoseLandmark.RIGHT_THUMB)
internal fun Side.hip(): PoseLandmark = pick(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
internal fun Side.knee(): PoseLandmark = pick(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
internal fun Side.ankle(): PoseLandmark = pick(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)
internal fun Side.heel(): PoseLandmark = pick(PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL)
internal fun Side.toe(): PoseLandmark = pick(PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX)
internal fun Side.ear(): PoseLandmark = pick(PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)
