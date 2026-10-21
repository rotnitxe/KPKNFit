package com.example.kpkn.domain.biomechanics.pose

/** Los 33 puntos de MediaPipe Pose. [mp] es el índice del modelo (coincide con el orden de declaración). */
enum class PoseLandmark(val mp: Int) {
    NOSE(0),
    LEFT_EYE_INNER(1),
    LEFT_EYE(2),
    LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4),
    RIGHT_EYE(5),
    RIGHT_EYE_OUTER(6),
    LEFT_EAR(7),
    RIGHT_EAR(8),
    MOUTH_LEFT(9),
    MOUTH_RIGHT(10),
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_PINKY(17),
    RIGHT_PINKY(18),
    LEFT_INDEX(19),
    RIGHT_INDEX(20),
    LEFT_THUMB(21),
    RIGHT_THUMB(22),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    LEFT_HEEL(29),
    RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31),
    RIGHT_FOOT_INDEX(32),
    ;

    companion object {
        /** 27 puntos de imagen guardados: 0 y 7..32 (los ojos no se usan). */
        val KEPT_IMAGE: List<PoseLandmark> = entries.filter { it.mp == 0 || it.mp >= 7 }

        /** 12 puntos «world» guardados: 11..16 y 23..28. */
        val KEPT_WORLD: List<PoseLandmark> = entries.filter { it.mp in 11..16 || it.mp in 23..28 }

        /** Punto del modelo con el índice [mp], o `null` si no existe. */
        fun fromMp(mp: Int): PoseLandmark? = entries.getOrNull(mp)?.takeIf { it.mp == mp }

        // Tablas mp → ranura en los arrays de un fotograma (−1 = no se guarda). Se calculan una vez.
        private val imageSlotByMp = IntArray(33) { -1 }.also { t ->
            KEPT_IMAGE.forEachIndexed { slot, lm -> t[lm.mp] = slot }
        }
        private val worldSlotByMp = IntArray(33) { -1 }.also { t ->
            KEPT_WORLD.forEachIndexed { slot, lm -> t[lm.mp] = slot }
        }

        /** Ranura (0..26) del punto en los arrays de imagen, o −1 si no se guarda. */
        internal fun imageSlot(lm: PoseLandmark): Int = imageSlotByMp[lm.mp]

        /** Ranura (0..11) del punto en los arrays «world», o −1 si no se guarda. */
        internal fun worldSlot(lm: PoseLandmark): Int = worldSlotByMp[lm.mp]
    }
}
