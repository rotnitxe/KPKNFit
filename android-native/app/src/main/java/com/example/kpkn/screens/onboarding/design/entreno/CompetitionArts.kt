package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.plan.ProfileJoints
import com.example.kpkn.screens.onboarding.design.entreno.plan.SM_GROUND
import com.example.kpkn.screens.onboarding.design.entreno.plan.STONE_RADIUS
import com.example.kpkn.screens.onboarding.design.entreno.plan.strongParams
import com.example.kpkn.screens.onboarding.design.entreno.plan.strongPose
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg

/*
 * Símbolos de «¿Compites?» (lienzo de 64 × 64, la familia de línea fina de Entreno). Cada disciplina tiene su escena con el
 * acento de módulo músculo: «Sí, tengo fecha» la termina con un destello, «Sí, sin fecha» es la misma escena sin
 * destello, y «No compito» es la figura entrenando sola, sin escenario. Sin elegir se ve el cuadro de reposo; elegido, la
 * escena corre su bucle. Con «reducir movimiento» el cuadro de reposo es el final ([restAt]), sin bucles.
 */

/** Figura del paso: segmentos de 9,5 unidades de muslo y espinilla, torso de 14. */
private val FIG = FigureDims(thigh = 9.5f, shin = 9.5f, torso = 14f, uarm = 8.5f, farm = 8f, head = 3.4f)

/** Figura de «No compito»: ocupa el alto del lienzo como las escenas (muslo y espinilla de 12,8, torso de 19). */
private val COMP_SOLO_FIG = FigureDims(thigh = 12.8f, shin = 12.8f, torso = 19f, uarm = 11.5f, farm = 11f, head = 4.6f)

/** Línea del suelo y de la plataforma común a las escenas. */
private const val COMP_FLOOR_Y = 56f

/** Suelo de la figura de «No compito». */
private const val COMP_SOLO_FLOOR = 61f

/** Cuadro final de una escena: el instante en que ya está todo encendido o colocado, con su destello terminado. */
private fun restAt(reduced: Boolean, finalT: Float): Float = if (reduced) finalT else 0f

/**
 * El símbolo de una opción de «¿Compites?»: la escena de la disciplina con destello ([CompetitionAnswer.WITH_DATE]), sin él
 * ([CompetitionAnswer.NO_DATE]), o la figura sola ([CompetitionAnswer.NO]). Una disciplina sin escena propia usa la de
 * powerlifting (el paso solo se muestra con las disciplinas de competición).
 */
internal fun competitionArtOf(profile: TrainingGoalProfile?, answer: CompetitionAnswer, reduced: Boolean): SymbolArt {
    if (answer == CompetitionAnswer.NO) return CompetitionSoloArt(reduced)
    val flash = answer == CompetitionAnswer.WITH_DATE
    return when (profile) {
        TrainingGoalProfile.WEIGHTLIFTING -> CompetitionWeightliftingArt(flash, reduced)
        TrainingGoalProfile.STRONGMAN -> CompetitionStrongmanArt(flash, reduced)
        TrainingGoalProfile.BODYBUILDING -> CompetitionBodybuildingArt(flash, reduced)
        TrainingGoalProfile.ARMWRESTLING -> CompetitionArmwrestlingArt(flash, reduced)
        TrainingGoalProfile.CALISTHENICS -> CompetitionCalisthenicsArt(flash, reduced)
        else -> CompetitionPowerliftingArt(flash, reduced)
    }
}

// ---------------------------------------------------------------- Powerlifting: jueces y barra cargada

/** Tres luces de jueces que se encienden una a una sobre una barra cargada; con fecha, un destello al final. */
internal class CompetitionPowerliftingArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 4.6f, restT = restAt(reduced, LIGHTS_DONE)) {

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        // Panel de jueces, cuello hasta la barra y la barra con sus discos (dos por lado, el exterior más alto).
        box(14f, 7f, 50f, 21f, 4f, soft, SymbolStroke.FINE)
        line(32f, 21f, 32f, BAR_Y - 6f, soft, SymbolStroke.FINE)
        for (x in JUDGE_X) ring(x, JUDGE_Y, JUDGE_R, soft, SymbolStroke.LINE)
        line(4f, BAR_Y, 60f, BAR_Y, ink, SymbolStroke.LINE)
        box(9f, BAR_Y - 13f, 14f, BAR_Y + 13f, 1.6f, accent, SymbolStroke.BODY)
        box(16f, BAR_Y - 9f, 20f, BAR_Y + 9f, 1.6f, accent, SymbolStroke.BODY)
        box(44f, BAR_Y - 9f, 48f, BAR_Y + 9f, 1.6f, accent, SymbolStroke.BODY)
        box(50f, BAR_Y - 13f, 55f, BAR_Y + 13f, 1.6f, accent, SymbolStroke.BODY)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        for ((i, x) in JUDGE_X.withIndex()) {
            val lit = smooth(seg(t, LIGHT_AT[i], LIGHT_AT[i] + LIGHT_MS))
            if (lit > 0.01f) dot(x, JUDGE_Y, JUDGE_DOT * lit, ink)
        }
        if (flash) {
            val f = riseHoldFall(t, FLASH_A, FLASH_B, FLASH_C, FLASH_D)
            if (f > 0.01f) ring(32f, JUDGE_Y, JUDGE_R + 6f * f, ink.fainter(f), SymbolStroke.FINE)
        }
    }

    private companion object {
        const val BAR_Y = 40f
        const val JUDGE_Y = 14f
        const val JUDGE_R = 4.5f
        const val JUDGE_DOT = 3.4f
        const val LIGHT_MS = 0.4f
        val JUDGE_X = floatArrayOf(21f, 32f, 43f)
        val LIGHT_AT = floatArrayOf(0.5f, 1.2f, 1.9f)
        const val FLASH_A = 2.5f
        const val FLASH_B = 2.8f
        const val FLASH_C = 3.2f
        const val FLASH_D = 3.6f
        /** Las tres luces ya encendidas y el destello acabado: el cuadro final. */
        const val LIGHTS_DONE = 3.7f
    }
}

// ---------------------------------------------------------------- Halterofilia: de la plataforma a la cabeza

/** Perfil de la figura que sube la barra desde el suelo hasta sobre la cabeza y la deja caer; con fecha, un destello arriba. */
internal class CompetitionWeightliftingArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 4.8f, restT = restAt(reduced, OVERHEAD_HOLD)) {

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(4f, COMP_FLOOR_Y, 60f, COMP_FLOOR_Y, soft, SymbolStroke.FINE)
        box(12f, COMP_FLOOR_Y - 3f, 52f, COMP_FLOOR_Y, 1f, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val p = riseHoldFall(t, LIFT_A, LIFT_B, LIFT_C, LIFT_D)
        val pose = liftPose(p)
        val by = barY(p)
        figure(pose, FIG.head, ink, ink.fainter(SymbolPalette.FAR_LIMB))
        line(10f, by, 54f, by, ink, SymbolStroke.LINE)
        box(6f, by - 7f, 11f, by + 7f, 1.4f, accent, SymbolStroke.BODY)
        box(53f, by - 7f, 58f, by + 7f, 1.4f, accent, SymbolStroke.BODY)
        if (flash) {
            val f = riseHoldFall(t, LIFT_B, LIFT_B + 0.3f, LIFT_C - 0.2f, LIFT_C + 0.2f)
            if (f > 0.01f) ring(32f, by, 10f + 5f * f, accent.fainter(f), SymbolStroke.FINE)
        }
    }

    /** Altura de la barra: en el suelo (p = 0) y sobre la cabeza (p = 1). */
    private fun barY(p: Float): Float = lerpF(COMP_FLOOR_Y - 7f, 9f, p)

    /** Figura de perfil con las manos en la barra: agachada con la barra en el suelo, de pie con los brazos estirados. */
    private fun liftPose(p: Float): Pose {
        val hip = Offset(32f, lerpF(44f, 38f, p))
        val sh = Offset(lerpF(35f, 32f, p), hip.y - FIG.torso)
        val by = barY(p)
        val handN = Offset(37f, by)
        val handF = Offset(33f, by)
        val footN = Offset(35f, COMP_FLOOR_Y - 1.2f)
        val footF = Offset(29f, COMP_FLOOR_Y - 1.2f)
        return Pose(
            hip = hip,
            kneeN = ik(hip, footN, FIG.thigh, FIG.shin, -1f), footN = footN,
            kneeF = ik(hip, footF, FIG.thigh, FIG.shin, -1f), footF = footF,
            sh = sh,
            head = sh + Offset(0f, -(FIG.head + 2.5f)),
            elbowN = ik(sh, handN, FIG.uarm, FIG.farm, 1f), handN = handN,
            elbowF = ik(sh, handF, FIG.uarm, FIG.farm, 1f), handF = handF,
        )
    }

    private companion object {
        const val LIFT_A = 0.6f
        const val LIFT_B = 1.8f
        const val LIFT_C = 2.6f
        const val LIFT_D = 3.8f
        /** Fin de la subida: la barra arriba, quieta. */
        const val OVERHEAD_HOLD = 2.2f
    }
}

// ---------------------------------------------------------------- Strongman: la figura carga la piedra y la sube

/**
 * La carga de la piedra de atlas (la de las disciplinas): la figura se agacha, abraza la piedra, se yergue y la deja en la
 * plataforma; después vuelve al suelo. Con fecha, un destello en la plataforma cuando la piedra queda arriba.
 */
internal class CompetitionStrongmanArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 3.6f, restT = restAt(reduced, STRONG_TOP_T)) {

    private val params = FloatArray(11)
    private val joints = ProfileJoints()

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(Offset(1.5f, SM_GROUND), Offset(62.5f, SM_GROUND), soft, SymbolStroke.FINE)
        // La plataforma: caja en tinta media y el canto superior en acento.
        box(PLAT_L, PLAT_TOP, PLAT_R, SM_GROUND, 1f, ink, SymbolStroke.LINE)
        line(Offset(PLAT_L, PLAT_TOP), Offset(PLAT_R, PLAT_TOP), accent, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        // De 0 a 3,0 s la figura carga y sube la piedra; de 3,3 a 3,6 s se funde al cuadro inicial y el bucle cierra.
        val back = smooth(seg(t, FADE_AT, period))
        if (back < 1f) fadedFigure(pen, 1f - back, minOf(t, HOLD_AT))
        if (back > 0f) fadedFigure(pen, back, 0f)
        if (flash) {
            // Destello en la plataforma cuando la piedra queda arriba: la altura sale de la propia pose.
            strongParams(t, params)
            val lift = ((SM_GROUND - STONE_RADIUS - params[1]) / (SM_GROUND - PLAT_TOP)).coerceIn(0f, 1f)
            val landed = smooth(seg(lift, 0.9f, 1f)) * (1f - back)
            if (landed > 0.01f) pen.ring(PLAT_CX, PLAT_TOP, 6f + 5f * landed, pen.accent.fainter(landed), SymbolStroke.FINE)
        }
    }

    /** Dibuja la figura con el grupo a [alpha] de opacidad (el fundido del bucle). */
    private fun fadedFigure(pen: SymbolPen, alpha: Float, at: Float) {
        val before = pen.ga
        pen.ga = before * alpha
        figureAt(pen, at)
        pen.ga = before
    }

    /** La figura y la piedra en [at]: el lado lejano queda tras la piedra y el cercano, delante de ella. */
    private fun figureAt(pen: SymbolPen, at: Float) {
        strongPose(at, params, joints)
        val j = joints
        val stone = Offset(params[0], params[1])
        with(pen) {
            val farInk = ink.fainter(SymbolPalette.FAR_LIMB)
            poly(j.shoulder, j.elbowF, j.handF, farInk, SymbolStroke.BODY)
            poly(j.hip, j.kneeF, j.footF, farInk, SymbolStroke.BODY)
            line(j.hip, j.shoulder, ink, SymbolStroke.BODY)
            poly(j.hip, j.kneeN, j.footN, ink, SymbolStroke.BODY)
            dot(j.head.x, j.head.y, FIG_HEAD_R + 0.3f, Color.Black)
            ring(j.head.x, j.head.y, FIG_HEAD_R, ink, SymbolStroke.BODY * 0.9f)
            // La piedra: disco negro que tapa lo de detrás, contorno en tinta y brillo en acento.
            dot(stone.x, stone.y, STONE_RADIUS, Color.Black)
            ring(stone.x, stone.y, STONE_RADIUS, ink, SymbolStroke.BODY)
            arc(stone.x, stone.y, STONE_RADIUS * 0.62f, 200f, 60f, accent, SymbolStroke.FINE)
            // El brazo cercano va delante de la piedra, con la mano en su borde.
            poly(j.shoulder, j.elbowN, j.handN, ink, SymbolStroke.BODY)
            dot(j.handN.x, j.handN.y, 1.8f, ink)
        }
    }

    private companion object {
        const val PLAT_L = 42f
        const val PLAT_R = 60f
        const val PLAT_TOP = 42f
        const val PLAT_CX = 51f
        /** La figura se yergue hasta 3,0 s; de 3,3 a 3,6 s se funde al cuadro inicial. */
        const val HOLD_AT = 3.0f
        const val FADE_AT = 3.3f
        /** Piedra arriba y figura erguida: el cuadro final con movimiento reducido. */
        const val STRONG_TOP_T = 2.7f
        const val FIG_HEAD_R = 3.3f
    }
}

// ---------------------------------------------------------------- Culturismo: el foco que se abre sobre la figura

/** Foco que se abre sobre una figura de frente que posa: doble bíceps y dorsales. Con fecha, la figura se enciende en acento. */
internal class CompetitionBodybuildingArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 4.4f, restT = restAt(reduced, POSE_DONE)) {

    private val cone = Path()

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        box(10f, STAGE_Y, 54f, STAGE_Y + 4f, 1f, soft, SymbolStroke.FINE)
        dot(32f, LAMP_Y, 1.8f, soft)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val open = smooth(seg(t, OPEN_A, OPEN_B))
        val half = lerpF(3f, 17f, open)
        cone.reset()
        cone.moveTo(32f, LAMP_Y)
        cone.lineTo(32f + half, STAGE_Y)
        cone.lineTo(32f - half, STAGE_Y)
        cone.close()
        fillPath(cone, ink.fainter(0.10f * open))
        line(32f, LAMP_Y, 32f + half, STAGE_Y, soft, SymbolStroke.FINE)
        line(32f, LAMP_Y, 32f - half, STAGE_Y, soft, SymbolStroke.FINE)
        val spread = riseHoldFall(t, SPREAD_A, SPREAD_B, SPREAD_C, SPREAD_D)
        val color = if (flash && spread > 0.5f) accent else ink
        frontFigure(posePose(spread), FIG.head, color)
    }

    /** Doble bíceps (spread = 0) y pose de dorsales con los brazos abiertos (spread = 1). */
    private fun posePose(spread: Float): FrontPose {
        val shY = 26f
        val shL = Offset(lerpF(26f, 24f, spread), shY)
        val shR = Offset(lerpF(38f, 40f, spread), shY)
        val elL = Offset(lerpF(18f, 12f, spread), lerpF(shY + 1f, shY + 7f, spread))
        val haL = Offset(lerpF(17f, 9f, spread), lerpF(shY - 9f, shY + 13f, spread))
        val elR = Offset(lerpF(46f, 52f, spread), lerpF(shY + 1f, shY + 7f, spread))
        val haR = Offset(lerpF(47f, 55f, spread), lerpF(shY - 9f, shY + 13f, spread))
        val hip = Offset(32f, 38f)
        return FrontPose(
            head = Offset(32f, shY - 8f),
            shL = shL, shR = shR,
            elL = elL, haL = haL,
            elR = elR, haR = haR,
            hip = hip,
            knL = Offset(27f, 45f), ftL = Offset(26f, STAGE_Y),
            knR = Offset(37f, 45f), ftR = Offset(38f, STAGE_Y),
        )
    }

    private companion object {
        const val STAGE_Y = 50f
        const val LAMP_Y = 4f
        const val OPEN_A = 0.2f
        const val OPEN_B = 1.2f
        const val SPREAD_A = 1.8f
        const val SPREAD_B = 2.6f
        const val SPREAD_C = 3.0f
        const val SPREAD_D = 3.8f
        /** Pose de doble bíceps con el foco abierto: el cuadro final. */
        const val POSE_DONE = 1.2f
    }
}

// ---------------------------------------------------------------- Armwrestling: dos antebrazos en la mesa

/** Dos antebrazos agarrados sobre la mesa que forcejean; con fecha, la mano cae sobre la mesa con un destello. */
internal class CompetitionArmwrestlingArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 3.6f, restT = restAt(reduced, PIN_DONE)) {

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(4f, TABLE_Y, 60f, TABLE_Y, ink, SymbolStroke.LINE)
        line(10f, TABLE_Y, 10f, COMP_FLOOR_Y, soft, SymbolStroke.FINE)
        line(54f, TABLE_Y, 54f, COMP_FLOOR_Y, soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val tug = 4f * kotlin.math.sin(TAU_F * t / period)
        val pin = if (flash) riseHoldFall(t, PIN_A, PIN_B, PIN_C, PIN_D) else 0f
        val clasp = Offset(32f + tug * (1f - pin), lerpF(CLASP_Y, TABLE_Y - 2f, pin))
        val elbowL = Offset(14f, TABLE_Y)
        val elbowR = Offset(50f, TABLE_Y)
        line(elbowL, clasp, ink, SymbolStroke.BODY)
        line(elbowR, clasp, accent, SymbolStroke.BODY)
        dot(clasp.x, clasp.y, 3.2f, accent)
        if (pin > 0.3f) {
            // Chispas de la mano al caer sobre la mesa.
            val k = (pin - 0.3f) / 0.7f
            for (a in SPARK_DEG) {
                val d = a * DEG
                line(clasp.x + kotlin.math.cos(d) * 6f, clasp.y + kotlin.math.sin(d) * 6f,
                    clasp.x + kotlin.math.cos(d) * (6f + 4f * k), clasp.y + kotlin.math.sin(d) * (6f + 4f * k),
                    soft, SymbolStroke.FINE)
            }
        }
    }

    private companion object {
        const val TABLE_Y = 48f
        const val CLASP_Y = 34f
        const val PIN_A = 2.4f
        const val PIN_B = 2.8f
        const val PIN_C = 3.2f
        const val PIN_D = 3.6f
        const val PIN_DONE = 3.4f
        val SPARK_DEG = floatArrayOf(-150f, -90f, -30f)
        val TAU_F = (2.0 * kotlin.math.PI).toFloat()
    }
}

// ---------------------------------------------------------------- Calistenia: la dominada bajo el foco

/**
 * Una figura de frente que cuelga de la barra y sube a una dominada (la pose del paso de capacidades); el foco se abre a su
 * alrededor mientras sube. Con fecha, la barra destella cuando la figura llega arriba.
 */
internal class CompetitionCalisthenicsArt(private val flash: Boolean, reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 3.6f, restT = restAt(reduced, TOP_T)) {

    private val pose = FigFrontPose()
    private val cone = Path()

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        dot(LAMP_X, LAMP_Y, 1.8f, soft)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val d = riseHoldFall(t, UP_A, UP_B, DOWN_A, DOWN_B)
        val open = smooth(seg(t, OPEN_A, OPEN_B))
        // El foco: un haz tenue desde arriba que se abre alrededor de la figura mientras sube.
        val half = lerpF(4f, 24f, open)
        cone.reset()
        cone.moveTo(LAMP_X, LAMP_Y)
        cone.lineTo(LAMP_X + half, CONE_BASE)
        cone.lineTo(LAMP_X - half, CONE_BASE)
        cone.close()
        fillPath(cone, ink.fainter(0.10f * open))
        // La barra en acento; las manos agarran sus extremos.
        line(
            mp(Offset(CapabilityPoses.PULL_CX - CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y)),
            mp(Offset(CapabilityPoses.PULL_CX + CapabilityPoses.PULL_BAR_HALF, CapabilityPoses.PULL_BAR_Y)),
            accent, SymbolStroke.LINE,
        )
        CapabilityPoses.pullUp(d, pose)
        val shL = mp(pose.shoulderL)
        val shR = mp(pose.shoulderR)
        val hip = mp(pose.hip)
        line(shL, shR, ink, SymbolStroke.BODY)
        line(Offset((shL.x + shR.x) / 2f, (shL.y + shR.y) / 2f), hip, ink, SymbolStroke.BODY)
        poly(shL, mp(pose.elbowL), mp(pose.handL), ink, SymbolStroke.BODY)
        poly(shR, mp(pose.elbowR), mp(pose.handR), ink, SymbolStroke.BODY)
        poly(hip, mp(pose.kneeL), mp(pose.footL), ink.fainter(SymbolPalette.FAR_LIMB), SymbolStroke.BODY)
        poly(hip, mp(pose.kneeR), mp(pose.footR), ink, SymbolStroke.BODY)
        val head = mp(pose.head)
        dot(head.x, head.y, FigGeo.HEAD_R * SCALE + 0.3f, Color.Black)
        ring(head.x, head.y, FigGeo.HEAD_R * SCALE, ink, SymbolStroke.BODY * 0.9f)
        if (flash) {
            val f = riseHoldFall(t, TOP_A, TOP_B, TOP_C, TOP_D)
            if (f > 0.01f) {
                val barC = mp(Offset(CapabilityPoses.PULL_CX, CapabilityPoses.PULL_BAR_Y))
                ring(barC.x, barC.y, 5f + 4f * f, accent.fainter(f), SymbolStroke.FINE)
            }
        }
    }

    /** Pasa un punto de la pose de capacidades (lienzo propio) al lienzo de 64: la barra queda arriba y los pies, abajo. */
    private fun mp(p: Offset): Offset = Offset(p.x * SCALE + SHIFT_X, p.y * SCALE + SHIFT_Y)

    private companion object {
        const val SCALE = 0.96f
        const val SHIFT_X = -6.4f
        const val SHIFT_Y = 1f
        const val LAMP_X = 32f
        const val LAMP_Y = 1.5f
        const val CONE_BASE = 62f
        const val UP_A = 0.4f
        const val UP_B = 1.4f
        const val DOWN_A = 2.0f
        const val DOWN_B = 3.0f
        const val OPEN_A = 0.2f
        const val OPEN_B = 1.2f
        const val TOP_A = 1.2f
        const val TOP_B = 1.5f
        const val TOP_C = 1.9f
        const val TOP_D = 2.3f
        /** Arriba de la subida con el foco abierto y el destello: el cuadro final con movimiento reducido. */
        const val TOP_T = 1.7f
    }
}

// ---------------------------------------------------------------- No compito: la figura sola

/**
 * Una figura entrenando sola, sin escenario: un curl de bíceps con mancuerna. Ocupa el alto del lienzo como las escenas. Tenue:
 * la figura lleva la tinta atenuada y la mancuerna, el acento.
 */
internal class CompetitionSoloArt(reduced: Boolean) :
    EquipmentArt(SymbolPalette.musculo, period = 2.4f, restT = restAt(reduced, 0.6f)) {

    override fun drawStatic(pen: SymbolPen) = Unit

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val curl = pingPong(t / period)
        val pose = curlPose(curl)
        figure(pose, COMP_SOLO_FIG.head, ink, ink.fainter(SymbolPalette.FAR_LIMB))
        dumbbell(pose.handN.x, pose.handN.y, 6f, 0f, accent, ink, SymbolStroke.LINE)
    }

    /** Figura de perfil de pie; el brazo cercano dobla el codo de extendido (curl = 0) a flexionado (curl = 1). */
    private fun curlPose(curl: Float): Pose {
        val hip = Offset(32f, 37f)
        val sh = Offset(32f, 18f)
        val footN = Offset(35f, COMP_SOLO_FLOOR)
        val footF = Offset(29f, COMP_SOLO_FLOOR)
        val elbowN = sh + downDir(0.12f) * COMP_SOLO_FIG.uarm
        val handN = elbowN + downDir(lerpF(0.12f, 2.9f, curl)) * COMP_SOLO_FIG.farm
        val elbowF = sh + Offset(-2f, COMP_SOLO_FIG.uarm)
        val handF = elbowF + Offset(-0.6f, COMP_SOLO_FIG.farm - 0.5f)
        return Pose(
            hip = hip,
            kneeN = ik(hip, footN, COMP_SOLO_FIG.thigh, COMP_SOLO_FIG.shin, -1f), footN = footN,
            kneeF = ik(hip, footF, COMP_SOLO_FIG.thigh, COMP_SOLO_FIG.shin, -1f), footF = footF,
            sh = sh,
            head = sh + Offset(0f, -(COMP_SOLO_FIG.head + 3.5f)),
            elbowN = elbowN, handN = handN,
            elbowF = elbowF, handF = handF,
        )
    }
}
