package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.kpkn.screens.onboarding.design.entreno.EquipmentArt
import com.example.kpkn.screens.onboarding.design.entreno.FigureDims
import com.example.kpkn.screens.onboarding.design.entreno.FrontPose
import com.example.kpkn.screens.onboarding.design.entreno.Pose
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.fainter
import com.example.kpkn.screens.onboarding.design.entreno.figure
import com.example.kpkn.screens.onboarding.design.entreno.frontFigure
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.plateFace
import java.text.Normalizer
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/*
 * El símbolo de patrón de cada ejercicio principal del detalle: un pictograma pequeño (una figura de palitos haciendo el
 * movimiento) que dice de un vistazo qué tipo de ejercicio es. El detalle solo recibe NOMBRES de ejercicio, así que el
 * patrón se deduce del nombre con reglas sencillas ([exercisePatternOf]); lo que no se reconoce lleva una barra con discos.
 */

/** Los patrones de movimiento con pictograma. */
internal enum class ExercisePattern(val label: String) {
    SQUAT("Sentadilla"),
    HINGE("Bisagra de cadera"),
    PRESS_HORIZONTAL("Empuje horizontal"),
    PRESS_VERTICAL("Empuje vertical"),
    PULL_VERTICAL("Tracción vertical"),
    ROW("Remo"),
    LUNGE("Zancada"),
    ARMS("Brazos"),
    CORE("Core"),
    CARRY("Acarreo"),
    OLYMPIC("Levantamiento olímpico"),
    GENERIC("Ejercicio"),
}

/**
 * Reglas en orden: gana la primera cuyo texto aparezca en el nombre (sin tildes ni mayúsculas). El orden importa:
 * «press militar» es empuje vertical antes de que «press» a secas lo vuelva horizontal, y «sentadilla búlgara» es una zancada.
 */
private val PATTERN_RULES: List<Pair<ExercisePattern, List<String>>> = listOf(
    ExercisePattern.OLYMPIC to listOf("arranque", "dos tiempos", "cargada", "envion", "clean", "snatch", "jerk"),
    ExercisePattern.PULL_VERTICAL to listOf("dominada", "jalon", "pull-up", "pull up", "pullup", "chin-up", "chin up"),
    ExercisePattern.ROW to listOf("remo", "row", "face pull", "tiron"),
    ExercisePattern.HINGE to listOf(
        "peso muerto", "deadlift", "buenos dias", "good morning", "hip thrust", "puente", "rumano", "swing", "hiperextension",
        "extension de cadera",
    ),
    ExercisePattern.PRESS_VERTICAL to listOf(
        "press militar", "press de hombro", "press por encima", "overhead", "militar", "arnold", "push press",
        "elevacion lateral", "elevaciones laterales", "pike", "handstand", "parada de manos",
    ),
    ExercisePattern.PRESS_HORIZONTAL to listOf(
        "banca", "bench", "flexion", "fondo", "apertura", "press de pecho", "press inclinado", "press declinado", "cruce",
        "pec deck", "push-up", "push up", "dips",
    ),
    ExercisePattern.LUNGE to listOf("zancada", "lunge", "estocada", "bulgara", "step-up", "step up", "subida al cajon"),
    ExercisePattern.SQUAT to listOf(
        "sentadilla", "squat", "prensa", "hack", "goblet", "pistol", "zercher", "gemelo", "pantorrilla",
        "extension de cuadriceps", "sissy",
    ),
    ExercisePattern.ARMS to listOf(
        "curl", "biceps", "triceps", "martillo", "frances", "patada", "predicador", "antebrazo", "muneca", "agarre", "grip", "wrist",
    ),
    ExercisePattern.CORE to listOf(
        "plancha", "abdominal", "crunch", "core", "rueda", "elevacion de piernas", "elevaciones de piernas", "hollow", "pallof",
        "dead bug", "bird dog", "escalador", "mountain climber", "ruso", "lenador",
    ),
    ExercisePattern.CARRY to listOf("paseo", "granjero", "carry", "acarreo", "yugo", "maleta", "porteo", "piedra"),
)

private val DIACRITICS = Regex("\\p{Mn}+")

/** Minúsculas y sin tildes: «Sentadilla búlgara» → «sentadilla bulgara». */
internal fun normalizeExerciseName(name: String): String =
    Normalizer.normalize(name.lowercase(Locale.forLanguageTag("es")), Normalizer.Form.NFD).replace(DIACRITICS, "")

/** El patrón de movimiento de un ejercicio, deducido de su nombre; [ExercisePattern.GENERIC] si no se reconoce. */
internal fun exercisePatternOf(name: String): ExercisePattern {
    val n = normalizeExerciseName(name)
    for ((pattern, keys) in PATTERN_RULES) {
        if (keys.any { n.contains(it) }) return pattern
    }
    return ExercisePattern.GENERIC
}

/** El pictograma de un patrón. */
internal fun patternArt(pattern: ExercisePattern): SymbolArt = PATTERN_ARTS.getValue(pattern)

private val PATTERN_ARTS: Map<ExercisePattern, SymbolArt> by lazy {
    ExercisePattern.entries.associateWith { PatternArt(it) }
}

/**
 * El pictograma de [pattern] a [modifier] de tamaño: tinta cálida con el detalle (los discos, las pesas) en [accent].
 * Es estático: la lista de ejercicios no se mueve.
 */
@Composable
internal fun PatternGlyph(pattern: ExercisePattern, accent: Color, modifier: Modifier = Modifier) {
    val art = remember(pattern) { patternArt(pattern) }
    val pen = remember { SymbolPen() }
    Canvas(modifier) {
        val k = min(size.width / art.width, size.height / art.height)
        pen.begin(this, 1f, accent)
        withTransform({ scale(k, k, Offset.Zero) }) {
            art.drawStatic(pen)
            art.drawDynamic(pen, art.restT)
        }
    }
}

// ---------------------------------------------------------------- los pictogramas

/** Grosor de los pictogramas: más gruesos que los símbolos de la lista porque se ven a ≈ 40 dp. */
private const val GLYPH_LINE = SymbolStroke.LINE * 1.25f
private const val GLYPH_BODY = SymbolStroke.BODY * 1.2f
private const val GLYPH_FINE = SymbolStroke.FINE * 1.2f

private const val GROUND_Y = 58f

private val DIMS = FigureDims(thigh = 13f, shin = 13f, torso = 16f, uarm = 8f, farm = 8f, head = 3.6f)

/** Un pictograma. Dibuja con el pincel de la familia (lienzo de 64 × 64) y no se mueve. */
private class PatternArt(private val pattern: ExercisePattern) : EquipmentArt(SymbolPalette.ok, period = 1f, restT = 0f) {
    override fun drawStatic(pen: SymbolPen) {
        with(pen) {
            // El suelo, salvo en los patrones que cuelgan.
            if (pattern != ExercisePattern.PULL_VERTICAL && pattern != ExercisePattern.OLYMPIC) {
                line(6f, GROUND_Y + 1.5f, 58f, GROUND_Y + 1.5f, soft, GLYPH_FINE)
            }
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        with(pen) {
            when (pattern) {
                ExercisePattern.SQUAT -> squat()
                ExercisePattern.HINGE -> hinge()
                ExercisePattern.PRESS_HORIZONTAL -> benchPress()
                ExercisePattern.PRESS_VERTICAL -> overheadPress()
                ExercisePattern.PULL_VERTICAL -> pullUp()
                ExercisePattern.ROW -> row()
                ExercisePattern.LUNGE -> lunge()
                ExercisePattern.ARMS -> curl()
                ExercisePattern.CORE -> plank()
                ExercisePattern.CARRY -> carry()
                ExercisePattern.OLYMPIC -> overheadHold()
                ExercisePattern.GENERIC -> barbell()
            }
        }
    }

    /** Una figura de perfil mirando a la derecha: cabeza sobre el hombro y rodillas hacia delante, codos hacia atrás. */
    private fun pose(hip: Offset, sh: Offset, footN: Offset, footF: Offset, handN: Offset, handF: Offset): Pose {
        val dx = sh.x - hip.x
        val dy = sh.y - hip.y
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val head = Offset(sh.x + dx / len * (DIMS.head + 2.2f), sh.y + dy / len * (DIMS.head + 2.2f))
        return Pose(
            hip = hip,
            kneeN = ik(hip, footN, DIMS.thigh, DIMS.shin, -1f), footN = footN,
            kneeF = ik(hip, footF, DIMS.thigh, DIMS.shin, -1f), footF = footF,
            sh = sh, head = head,
            elbowN = ik(sh, handN, DIMS.uarm, DIMS.farm, 1f), handN = handN,
            elbowF = ik(sh, handF, DIMS.uarm, DIMS.farm, 1f), handF = handF,
        )
    }

    private fun SymbolPen.person(p: Pose) {
        figure(p, DIMS.head, ink, ink.fainter(SymbolPalette.FAR_LIMB), GLYPH_BODY)
    }

    /** Un disco visto de canto (la barra es su eje): el aro del acento y el buje. */
    private fun SymbolPen.plate(c: Offset, r: Float) {
        plateFace(c.x, c.y, r, accent, ink, GLYPH_LINE)
    }

    private fun SymbolPen.squat() {
        val hip = Offset(19f, 41f)
        val sh = Offset(28.6f, 28.6f)
        val p = pose(hip, sh, Offset(31f, GROUND_Y), Offset(26f, GROUND_Y), sh + Offset(3f, 4f), sh + Offset(3.6f, 4.4f))
        plate(Offset(sh.x - 2f, sh.y - 1f), 8f)
        person(p)
    }

    private fun SymbolPen.hinge() {
        val hip = Offset(21f, 34f)
        val sh = Offset(35.2f, 26.8f)
        val hand = Offset(36f, 46f)
        val p = pose(hip, sh, Offset(30f, GROUND_Y), Offset(26f, GROUND_Y), hand, hand + Offset(1.6f, 0f))
        plate(hand, 8f)
        person(p)
    }

    private fun SymbolPen.benchPress() {
        // Banco, figura tumbada y la barra (de canto: un disco) sobre el pecho con los brazos estirados.
        box(6f, 46f, 44f, 51f, 2.4f, ink, GLYPH_LINE)
        line(12f, 51f, 12f, GROUND_Y, ink, GLYPH_LINE)
        line(38f, 51f, 38f, GROUND_Y, ink, GLYPH_LINE)
        ring(10f, 41.6f, 3.6f, ink, GLYPH_BODY * 0.9f)
        line(15f, 43.6f, 33f, 43.6f, ink, GLYPH_BODY)
        poly(Offset(33f, 43.6f), Offset(45f, 40f), Offset(48f, GROUND_Y), ink, GLYPH_BODY)
        line(23f, 43.6f, 23f, 25f, ink, GLYPH_BODY)
        plate(Offset(23f, 25f), 7.4f)
    }

    private fun SymbolPen.overheadPress() {
        val hip = Offset(28f, 41f)
        val sh = Offset(29f, 25.4f)
        val hand = Offset(31f, 10.4f)
        val p = pose(hip, sh, Offset(32f, GROUND_Y), Offset(27f, GROUND_Y), hand, hand + Offset(1.6f, 0f))
        plate(hand, 7.4f)
        person(p)
    }

    private fun SymbolPen.pullUp() {
        line(8f, 6f, 56f, 6f, accent, GLYPH_BODY)
        line(8f, 6f, 8f, 12f, soft, GLYPH_FINE)
        line(56f, 6f, 56f, 12f, soft, GLYPH_FINE)
        val shL = Offset(28.4f, 25f)
        val shR = Offset(35.6f, 25f)
        val handL = Offset(22f, 6f)
        val handR = Offset(42f, 6f)
        val hip = Offset(32f, 40f)
        frontFigure(
            FrontPose(
                head = Offset(32f, 17.4f),
                shL = shL, shR = shR,
                elL = ik(shL, handL, 9.6f, 9.6f, -1f), haL = handL,
                elR = ik(shR, handR, 9.6f, 9.6f, 1f), haR = handR,
                hip = hip,
                knL = Offset(30.8f, 49f), ftL = Offset(30f, 58f),
                knR = Offset(33.2f, 49f), ftR = Offset(34f, 58f),
            ),
            3.5f, ink, GLYPH_BODY,
        )
    }

    private fun SymbolPen.row() {
        val hip = Offset(22f, 36f)
        val sh = Offset(37f, 30.6f)
        val hand = Offset(36f, 46.4f)
        val p = pose(hip, sh, Offset(28f, GROUND_Y), Offset(24f, GROUND_Y), hand, hand + Offset(1.6f, 0f))
        plate(hand + Offset(0f, 1f), 7f)
        person(p)
    }

    private fun SymbolPen.lunge() {
        val hip = Offset(28f, 37f)
        val sh = Offset(28.6f, 21.4f)
        val footN = Offset(41f, GROUND_Y)
        val footF = Offset(14f, GROUND_Y)
        val handN = Offset(28f, 39f)
        val p = pose(hip, sh, footN, footF, handN, handN + Offset(1.4f, 0f))
        person(p)
        // Una mancuerna en cada mano.
        dot(handN.x, handN.y + 3f, 3f, accent)
    }

    private fun SymbolPen.curl() {
        // Figura de perfil de pie con el antebrazo subido y una mancuerna en la mano.
        val hip = Offset(26f, 42f)
        val sh = Offset(26.4f, 26f)
        val hand = Offset(37f, 28.4f)
        val p = pose(hip, sh, Offset(29f, GROUND_Y), Offset(24f, GROUND_Y), hand, hand + Offset(1.4f, 0f))
        person(p)
        box(hand.x - 1.6f, hand.y - 6.4f, hand.x + 1.6f, hand.y + 6.4f, 1.4f, accent, GLYPH_LINE)
        box(hand.x + 5f, hand.y - 4.6f, hand.x + 7.4f, hand.y + 4.6f, 1.2f, accent, GLYPH_LINE)
    }

    private fun SymbolPen.plank() {
        // Plancha sobre los antebrazos: el cuerpo en una recta desde los pies hasta el hombro.
        line(9f, 55.6f, 46f, 40.6f, ink, GLYPH_BODY)
        poly(Offset(46f, 40.6f), Offset(47f, 52f), Offset(58f, 55.4f), ink, GLYPH_BODY)
        ring(53.6f, 36.2f, 3.8f, ink, GLYPH_BODY * 0.9f)
        line(9f, 55.6f, 13f, 56.8f, accent, GLYPH_BODY)
        line(22f, 47.6f, 22f, 55.4f, soft, GLYPH_FINE)
    }

    private fun SymbolPen.carry() {
        val hip = Offset(28f, 40f)
        val sh = Offset(28.4f, 24f)
        val hand = Offset(27f, 41.2f)
        val p = pose(hip, sh, Offset(35f, GROUND_Y), Offset(21f, GROUND_Y), hand, hand + Offset(2f, 0f))
        person(p)
        // Una pesa rusa colgando de la mano.
        ring(hand.x + 0.5f, hand.y + 8f, 5.4f, accent, GLYPH_LINE)
        line(hand.x - 2.8f, hand.y + 3.8f, hand.x + 3.8f, hand.y + 3.8f, ink, GLYPH_LINE)
    }

    private fun SymbolPen.overheadHold() {
        // Figura de frente con la barra sobre la cabeza y los discos a los lados.
        line(6f, 10f, 58f, 10f, ink, GLYPH_BODY)
        box(7f, 3f, 11f, 17f, 1.8f, accent, GLYPH_LINE)
        box(53f, 3f, 57f, 17f, 1.8f, accent, GLYPH_LINE)
        val shL = Offset(28f, 24f)
        val shR = Offset(36f, 24f)
        val handL = Offset(20f, 10f)
        val handR = Offset(44f, 10f)
        val hip = Offset(32f, 40f)
        frontFigure(
            FrontPose(
                head = Offset(32f, 17.4f),
                shL = shL, shR = shR,
                elL = ik(shL, handL, 8.4f, 8.4f, -1f), haL = handL,
                elR = ik(shR, handR, 8.4f, 8.4f, 1f), haR = handR,
                hip = hip,
                knL = Offset(29.6f, 49f), ftL = Offset(27f, 58f),
                knR = Offset(34.4f, 49f), ftR = Offset(37f, 58f),
            ),
            3.4f, ink, GLYPH_BODY,
        )
    }

    private fun SymbolPen.barbell() {
        line(4f, 32f, 60f, 32f, ink, GLYPH_LINE)
        box(9f, 17f, 15f, 47f, 2.2f, accent, GLYPH_LINE)
        box(17f, 22f, 22f, 42f, 2f, accent, GLYPH_LINE)
        box(49f, 17f, 55f, 47f, 2.2f, accent, GLYPH_LINE)
        box(42f, 22f, 47f, 42f, 2f, accent, GLYPH_LINE)
    }
}
