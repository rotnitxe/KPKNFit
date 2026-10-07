package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.lerpF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Las tres escenas de «¿Dónde entrenas?»: Gimnasio, En casa y En espacios públicos. Cada una es una función pura
 * de `t` sobre un lienzo de 120 × 170 unidades, de línea fina (≈ 2,2 de trazo) y con UN acento de módulo:
 *
 *  - Gimnasio · músculo: una figura de perfil levanta una barra (se ve de frente: los discos son aros) junto a un
 *    estante de discos.
 *  - En casa · energía: una casa con ventana, una esterilla, mancuernas y la figura haciendo flexiones.
 *  - En espacios públicos · ok: un parque de calistenia con árbol, barras paralelas y la figura haciendo dominadas.
 *
 * La parte móvil (la figura y lo que levanta) está en `drawDynamic`; el decorado, en `drawStatic`.
 */

/** La escena de un lugar. */
internal fun placeArt(place: TrainingPlace): SymbolArt = when (place) {
    TrainingPlace.GYM -> GymScene
    TrainingPlace.HOME -> HomeScene
    TrainingPlace.PUBLIC -> ParkScene
}

/** Alturas a las que el rack tiene agujeros para los ganchos (marcas tenues en los montantes). */
private val RACK_HOLES = floatArrayOf(48f, 60f, 86f, 98f, 124f, 136f)

// ---------------------------------------------------------------- Gimnasio

/** Peso muerto de perfil delante de un rack con una barra cargada. Acento: músculo. */
internal object GymScene : SceneArt(accent = SymbolPalette.musculo, period = 4.2f, restT = 1.0f) {
    /** Suelo de la tarima, donde apoyan los pies, y suelo de la sala. */
    private const val FLOOR = 147f
    private const val GROUND = 152f
    internal const val BAR_X = 42f
    private const val PLATE_R = 13.5f
    private const val ARM_REACH = 36.2f
    private const val LIFT_TRAVEL = 36f

    internal val dims = FigureDims(thigh = 25.2f, shin = 25.2f, torso = 36f, uarm = 19.2f, farm = 18f, head = 7.6f)

    /** Avance de la elevación: 0 = la barra en el suelo, 1 = erguido con la barra a los muslos. */
    internal fun lift(t: Float): Float = riseHoldFall(t, 0.35f, 1.65f, 2.2f, 3.6f)

    /** Altura (centro) de los discos para un avance [p]. */
    internal fun barY(p: Float): Float = lerpF(FLOOR - PLATE_R, FLOOR - PLATE_R - LIFT_TRAVEL, p)

    /** Pose del levantador para un avance [p] (0 = arranque con la espalda inclinada, 1 = erguido). */
    internal fun pose(p: Float): Pose {
        val by = barY(p)
        // Al arrancar los hombros van por delante de la barra; al terminar, un poco por detrás: así el brazo se separa del tronco.
        val sh = Offset(BAR_X + lerpF(2.4f, -5f, p), by - ARM_REACH)
        val lean = lerpF(52f, -1f, p) * DEG
        val hip = sh - Offset(sin(lean), -cos(lean)) * dims.torso
        val footN = Offset(BAR_X - 1f, FLOOR - FOOT_LIFT)
        val footF = Offset(BAR_X - 6f, FLOOR - FOOT_LIFT)
        val handN = Offset(BAR_X, by)
        val handF = Offset(BAR_X + 2.4f, by)
        val headDir = Offset(sin(lean * 0.55f), -cos(lean * 0.55f))
        return Pose(
            hip = hip,
            kneeN = ik(hip, footN, dims.thigh, dims.shin, -1f), footN = footN,
            kneeF = ik(hip, footF, dims.thigh, dims.shin, -1f), footF = footF,
            sh = sh,
            head = sh + headDir * (4.5f + dims.head),
            elbowN = ik(sh, handN, dims.uarm, dims.farm, 1f), handN = handN,
            elbowF = ik(sh, handF, dims.uarm, dims.farm, 1f), handF = handF,
        )
    }

    /** Los pies descansan un pelo sobre la tarima para que su trazo no se funda con el filo. */
    private const val FOOT_LIFT = 1.2f

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        // Suelo y tarima.
        line(6f, GROUND, 114f, GROUND, soft, SymbolStroke.FINE)
        box(8f, FLOOR, 72f, GROUND, 1.6f, soft, SymbolStroke.FINE)
        // Rack: montantes, travesaño, pies, ganchos en J, topes de seguridad y agujeros.
        line(75f, 36f, 75f, GROUND, ink)
        line(103f, 36f, 103f, GROUND, ink)
        line(75f, 36f, 103f, 36f, ink)
        line(68f, GROUND, 82f, GROUND, ink)
        line(96f, GROUND, 110f, GROUND, ink)
        poly3(75f, 75f, 81.5f, 75f, 81.5f, 71.6f, ink)
        poly3(103f, 75f, 96.5f, 75f, 96.5f, 71.6f, ink)
        line(75f, 112f, 84f, 112f, soft)
        line(103f, 112f, 94f, 112f, soft)
        for (y in RACK_HOLES) {
            dot(75f, y, 1f, soft)
            dot(103f, y, 1f, soft)
        }
        // La barra cargada descansa en los ganchos (de frente: los discos son rectángulos altos).
        line(56f, 70.4f, 118.5f, 70.4f, ink)
        box(60.5f, 55.4f, 65.5f, 85.4f, 2f, accent)
        box(67f, 60.4f, 71.5f, 80.4f, 1.8f, accent)
        box(106.5f, 60.4f, 111f, 80.4f, 1.8f, accent)
        box(112.5f, 55.4f, 117.5f, 85.4f, 2f, accent)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val p = lift(t)
        val pose = pose(p)
        val by = barY(p)
        // El disco va detrás del cuerpo: la barra se ve de frente y el levantador la sube por delante de las espinillas.
        plateFace(BAR_X, by, PLATE_R, accent, ink)
        figure(pose, dims.head, ink, ink.fainter(SymbolPalette.FAR_LIMB))
        // Pies planos sobre la tarima.
        line(pose.footN.x, pose.footN.y, pose.footN.x + 8f, pose.footN.y, ink, SymbolStroke.BODY)
        line(pose.footF.x, pose.footF.y, pose.footF.x + 8f, pose.footF.y, ink.fainter(SymbolPalette.FAR_LIMB), SymbolStroke.BODY)
        dot(BAR_X, by, 2.4f, ink)
    }
}

// ---------------------------------------------------------------- En casa

/** Flexiones sobre la esterilla, dentro de una casa con ventana. Acento: energía. */
internal object HomeScene : SceneArt(accent = SymbolPalette.energia, period = 2.8f, restT = 0.6f) {
    private const val GROUND = 152f
    private const val MAT_TOP = 146.5f
    private const val TOE_X = 32f

    internal val dims = FigureDims(thigh = 17.6f, shin = 17.6f, torso = 25.3f, uarm = 13.75f, farm = 13.2f, head = 5.7f)
    private val bodyLen = dims.thigh + dims.shin + dims.torso
    private val armLen = dims.uarm + dims.farm

    /** Hombros arriba (brazos casi rectos) y la x fija de las manos: cada brazo llega justo debajo del hombro. */
    private val topDy = armLen - 0.4f
    private val handX = TOE_X + sqrt(bodyLen * bodyLen - topDy * topDy)

    /** Avance de la flexión: 0 = brazos extendidos, 1 = pecho abajo. */
    internal fun down(t: Float): Float = riseHoldFall(t, 0.2f, 1.0f, 1.2f, 2.1f)

    internal fun pose(p: Float): Pose {
        val sy = MAT_TOP - lerpF(topDy, 9.5f, p)
        val dy = MAT_TOP - sy
        val sh = Offset(TOE_X + sqrt(bodyLen * bodyLen - dy * dy), sy)
        val toe = Offset(TOE_X, MAT_TOP)
        val dirB = (sh - toe) / bodyLen
        val hip = toe + dirB * (dims.thigh + dims.shin)
        val handN = Offset(handX, MAT_TOP)
        val handF = Offset(handX + 3.5f, MAT_TOP)
        return Pose(
            hip = hip,
            kneeN = toe + dirB * dims.thigh, footN = toe,
            kneeF = toe + Offset(-2.6f, -0.9f) + dirB * dims.thigh, footF = toe + Offset(-2.6f, -0.9f),
            sh = sh,
            head = sh + dirB * (3.5f + dims.head) + Offset(0.6f, -1.4f),
            elbowN = ik(sh, handN, dims.uarm, dims.farm, 1f), handN = handN,
            elbowF = ik(sh, handF, dims.uarm, dims.farm, 1f), handF = handF,
        )
    }

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        // La casa: tejado con chimenea, dos paredes y el suelo.
        poly3(2f, 62f, 60f, 16f, 118f, 62f, ink)
        poly4(92f, 41f, 92f, 27f, 100f, 27f, 100f, 47f, ink, close = false)
        line(10f, 55.7f, 10f, GROUND, ink)
        line(110f, 55.7f, 110f, GROUND, ink)
        line(2f, GROUND, 118f, GROUND, ink)
        // Ventana.
        box(74f, 70f, 100f, 96f, 2f, accent)
        line(87f, 70f, 87f, 96f, accent, SymbolStroke.FINE)
        line(74f, 83f, 100f, 83f, accent, SymbolStroke.FINE)
        // Esterilla y mancuernas.
        box(28f, MAT_TOP, 106f, GROUND, 2.6f, accent)
        dumbbell(20f, GROUND - 5f, 7.5f, 0f, accent, ink, SymbolStroke.FINE)
        dumbbell(20f, GROUND - 14.3f, 7.5f, 0f, accent, ink, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        figure(pose(down(t)), dims.head, ink, ink.fainter(SymbolPalette.FAR_LIMB))
    }
}

// ---------------------------------------------------------------- En espacios públicos

/** Parque de calistenia: barras paralelas, barra alta con la figura haciendo dominadas y un árbol. Acento: ok. */
internal object ParkScene : SceneArt(accent = SymbolPalette.ok, period = 3.8f, restT = 0.95f) {
    private const val GROUND = 152f
    private const val BAR_Y = 34f
    private const val CX = 67f

    internal val dims = FigureDims(thigh = 17f, shin = 17f, torso = 25f, uarm = 14.5f, farm = 13.5f, head = 5.8f)

    /** Avance de la dominada: 0 = colgado, 1 = barbilla sobre la barra. */
    internal fun pull(t: Float): Float = riseHoldFall(t, 0.4f, 1.5f, 1.8f, 3.0f)

    internal fun pose(p: Float, sway: Float): FrontPose {
        val sy = lerpF(60.5f, 39.5f, p)
        val handL = Offset(CX - 9.5f, BAR_Y)
        val handR = Offset(CX + 9.5f, BAR_Y)
        val shL = Offset(CX - 4.5f, sy)
        val shR = Offset(CX + 4.5f, sy)
        val hip = Offset(CX, sy + dims.torso)
        val legY = hip.y + 32f
        val ftL = Offset(CX - 3.4f + sway, legY)
        val ftR = Offset(CX + 3.4f + sway, legY)
        return FrontPose(
            head = Offset(CX, sy - 10.6f),
            shL = shL, shR = shR,
            elL = ik(shL, handL, dims.uarm, dims.farm, -1f), haL = handL,
            elR = ik(shR, handR, dims.uarm, dims.farm, 1f), haR = handR,
            hip = hip,
            knL = Offset((hip.x + ftL.x) / 2f - 1.2f, (hip.y + ftL.y) / 2f),
            ftL = ftL,
            knR = Offset((hip.x + ftR.x) / 2f + 1.2f, (hip.y + ftR.y) / 2f),
            ftR = ftR,
        )
    }

    override fun drawStatic(pen: SymbolPen) = with(pen) {
        line(2f, GROUND, 118f, GROUND, soft, SymbolStroke.FINE)
        // Barras paralelas (de perfil): la cercana y, detrás y más alta, la lejana.
        line(11f, 97f, 39f, 97f, soft)
        line(15f, 97f, 15f, GROUND - 6f, soft, SymbolStroke.FINE)
        line(36f, 97f, 36f, GROUND - 6f, soft, SymbolStroke.FINE)
        line(3f, 104f, 31f, 104f, ink)
        line(7f, 104f, 7f, GROUND, ink)
        line(27f, 104f, 27f, GROUND, ink)
        // Barra alta.
        line(44f, BAR_Y, 44f, GROUND, ink)
        line(90f, BAR_Y, 90f, GROUND, ink)
        line(40f, BAR_Y, 94f, BAR_Y, accent, SymbolStroke.LINE + 0.6f)
        // Árbol: tronco, copa y la prolongación del tronco dentro de la copa.
        line(107f, GROUND, 107f, 113f, ink, 3f)
        oval(107f, 98f, 11f, 14f, accent)
        line(107f, 113f, 107f, 100f, soft, SymbolStroke.FINE)
        dot(102.5f, 94f, 1.1f, soft)
        dot(111.5f, 92f, 1.1f, soft)
        dot(108f, 88f, 1.1f, soft)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = with(pen) {
        val sway = 1.6f * sin(2f * PI.toFloat() * t / period) * (1f - 0.6f * pull(t))
        frontFigure(pose(pull(t), sway), dims.head, ink)
    }
}

// ---------------------------------------------------------------- figura de frente

/** Articulaciones de una figura vista de frente (dominadas, fondos, saltos). */
internal class FrontPose(
    val head: Offset,
    val shL: Offset, val shR: Offset,
    val elL: Offset, val haL: Offset,
    val elR: Offset, val haR: Offset,
    val hip: Offset,
    val knL: Offset, val ftL: Offset,
    val knR: Offset, val ftR: Offset,
)

/** Figura de frente: hombros, tronco, brazos, piernas y cabeza (aro). */
internal fun SymbolPen.frontFigure(p: FrontPose, headR: Float, color: Color, w: Float = SymbolStroke.BODY) {
    val neck = Offset((p.shL.x + p.shR.x) / 2f, (p.shL.y + p.shR.y) / 2f)
    line(p.shL, p.shR, color, w)
    line(neck, p.hip, color, w)
    poly(p.shL, p.elL, p.haL, color, w)
    poly(p.shR, p.elR, p.haR, color, w)
    poly(p.hip, p.knL, p.ftL, color, w)
    poly(p.hip, p.knR, p.ftR, color, w)
    ring(p.head.x, p.head.y, headR, color, w * 0.9f)
}
