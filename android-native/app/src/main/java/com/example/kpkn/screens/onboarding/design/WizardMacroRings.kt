package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.ui.theme.MacroColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * El círculo entero del anillo exterior equivale a este múltiplo del mantenimiento (EER): así el 100 %
 * queda a la vista con una marca y tanto un déficit (el arco no llega) como un superávit (lo pasa) caben.
 */
const val WIZARD_RING_CALORIE_SCALE = 1.25f

private const val RING_MILLIS = 700
private const val COUNT_MILLIS = 500
private val RingStroke = 10.dp
private val RingGap = 3.dp
private val RingMargin = 6.dp

/** Tamaño máximo del número central (Syne ExtraBold): solo lo alcanza si cabe en el hueco de los anillos. */
private val HeroMaxSize = 44.dp

/** Parte del hueco que puede ocupar el ancho del número central: deja holgura contra el anillo interior. */
private const val HERO_FILL = 0.9f

/** El número central nunca baja de esta parte de [HeroMaxSize] (≈ 20 sp). */
private const val HERO_MIN_SCALE = 0.45f

/** Diámetro (dp) del hueco que dejan los cuatro anillos de un conjunto de [diameterDp] de lado. */
fun macroRingsFreeDiameterDp(diameterDp: Float): Float =
    (diameterDp - 2f * (RingMargin.value + 4f * RingStroke.value + 3f * RingGap.value)).coerceAtLeast(0f)

/**
 * Escala (de 0,45 a 1) del número central para que su ancho [textWidthDp] (medido al tamaño máximo) quepa en el
 * hueco de [freeDiameterDp]: 1 si ya cabe con holgura, menos si no. La letra de Syne es ancha: con los anillos
 * en 260 dp un número de cuatro cifras solo cabe a ≈ 30 sp.
 */
fun macroRingsHeroScale(textWidthDp: Float, freeDiameterDp: Float): Float {
    if (textWidthDp <= 0f) return 1f
    return (freeDiameterDp * HERO_FILL / textWidthDp).coerceIn(HERO_MIN_SCALE, 1f)
}

private fun heroStyle(size: TextUnit) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size,
    lineHeight = size * 1.1f,
    letterSpacing = (-0.5).sp,
)

/**
 * Tamaño del número central de [target] (las kcal ya con su punto de millar) en anillos de [diameter]. Se mide
 * con las cifras más anchas (todas «8»): así el tamaño solo depende de cuántas cifras hay y el número no se sale
 * del hueco mientras cuenta de un valor a otro.
 */
@Composable
private fun rememberHeroSize(target: String, diameter: Dp): TextUnit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val widest = target.map { if (it.isDigit()) '8' else it }.joinToString("")
    val widthDp = remember(widest, density) {
        val style = heroStyle(with(density) { HeroMaxSize.toSp() })
        measurer.measure(AnnotatedString(widest), style = style, maxLines = 1, softWrap = false).size.width / density.density
    }
    val scale = macroRingsHeroScale(widthDp, macroRingsFreeDiameterDp(diameter.value))
    return with(density) { (HeroMaxSize * scale).toSp() }
}

/** Lo que dibuja cada anillo, de 0 a 1. Calculado aparte para poder probarlo sin Compose. */
@Immutable
data class MacroRingFractions(
    /** Exterior: kcal del plan frente al mantenimiento (1 = círculo entero = [WIZARD_RING_CALORIE_SCALE] × EER). */
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    /** Posición (0..1 del círculo) del 100 % del mantenimiento; null si no hay EER. */
    val referenceMark: Float?,
)

/**
 * Anillos de [values] (por defecto los del plan): el exterior son las kcal frente al mantenimiento y los
 * tres interiores los gramos de cada macro frente al máximo de su deslizador. Sin EER no hay escala para
 * las calorías: el anillo exterior se queda lleno y sin marca (no se inventa una referencia).
 */
fun macroRingFractions(tuning: PlanTuning, values: PlanValues = tuning.values): MacroRingFractions {
    val eer = tuning.context.knownEerKcal
    val limits = tuning.limits
    fun share(grams: Int, max: Int): Float = if (max <= 0) 0f else (grams.toFloat() / max).coerceIn(0f, 1f)
    return MacroRingFractions(
        calories = if (eer == null) 1f else ((values.kcal / eer).toFloat() / WIZARD_RING_CALORIE_SCALE).coerceIn(0f, 1f),
        protein = share(values.proteinG, limits.protein.maxG),
        carbs = share(values.carbsG, limits.carbs.maxG),
        fat = share(values.fatG, limits.fat.maxG),
        referenceMark = if (eer == null) null else 1f / WIZARD_RING_CALORIE_SCALE,
    )
}

/**
 * Cuatro anillos concéntricos como los de la pantalla Nutrición (trazo de 10 dp, hueco de 3 dp, extremos
 * redondeados, pista al 10 %), con las kcal en grande en el centro. Los arcos barren desde 0 al
 * aparecer ([appeared]) y se animan suave al cambiar; con [reducedMotion] saltan sin animar.
 *
 * Un único `contentDescription` resume kcal y gramos: el número del centro no se lee aparte.
 */
@Composable
fun WizardMacroRings(
    kcal: Int,
    fractions: MacroRingFractions,
    caption: String,
    description: String,
    appeared: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec: AnimationSpec<Float> =
        if (reducedMotion) snap() else tween(durationMillis = RING_MILLIS, easing = FastOutSlowInEasing)
    val calories = animateFloatAsState(if (appeared) fractions.calories else 0f, spec, label = "ring-calories")
    val protein = animateFloatAsState(if (appeared) fractions.protein else 0f, spec, label = "ring-protein")
    val carbs = animateFloatAsState(if (appeared) fractions.carbs else 0f, spec, label = "ring-carbs")
    val fat = animateFloatAsState(if (appeared) fractions.fat else 0f, spec, label = "ring-fat")
    val countSpec: AnimationSpec<Int> =
        if (reducedMotion) snap() else tween(durationMillis = COUNT_MILLIS, easing = FastOutSlowInEasing)
    // Las kcal cuentan desde 0 al aparecer, a la vez que barren los arcos, y entre un valor y otro al cambiar.
    val shownKcal by animateIntAsState(if (appeared) kcal else 0, countSpec, label = "ring-kcal")
    val mark = fractions.referenceMark
    val kcalText = formatKcalEs(shownKcal)

    BoxWithConstraints(
        modifier = modifier
            .testTag("setup-nutrition-rings")
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // El número del centro se fija en dp (no crece con la letra del sistema, que lo metería en el anillo
        // interior) y se achica lo justo para caber en el hueco.
        val heroSize = rememberHeroSize(formatKcalEs(kcal), minOf(maxWidth, maxHeight))
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val gap = RingGap.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f - RingMargin.toPx() - stroke / 2f
            val pitch = stroke + gap

            fun ring(radius: Float, fraction: Float, color: Color) {
                val topLeft = Offset(center.x - radius, center.y - radius)
                val box = Size(radius * 2f, radius * 2f)
                drawArc(
                    color = color.copy(alpha = 0.10f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceAtMost(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = box,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }

            ring(outer, calories.value, MacroColors.calories)
            ring(outer - pitch, protein.value, MacroColors.protein)
            ring(outer - 2f * pitch, carbs.value, MacroColors.carbs)
            ring(outer - 3f * pitch, fat.value, MacroColors.fat)

            // Marca fina en el 100 % del mantenimiento: lo que queda antes es déficit y lo que pasa, superávit.
            if (mark != null) {
                val angle = Math.toRadians((-90f + 360f * mark).toDouble())
                val reach = stroke / 2f + 3.dp.toPx()
                val from = Offset(
                    center.x + ((outer - reach) * cos(angle)).toFloat(),
                    center.y + ((outer - reach) * sin(angle)).toFloat(),
                )
                val to = Offset(
                    center.x + ((outer + reach) * cos(angle)).toFloat(),
                    center.y + ((outer + reach) * sin(angle)).toFloat(),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = from,
                    end = to,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = kcalText,
                style = heroStyle(heroSize),
                color = WizardColors.text,
                maxLines = 1,
            )
            Text(
                text = caption,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        }
    }
}
