package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.screens.home.HomeMacroToday
import com.example.kpkn.screens.home.HomeNutritionState
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WIZARD_RING_CALORIE_SCALE
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMacroRings
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.theme.MacroColors
import kotlin.math.cos
import kotlin.math.sin

/** Clave de la memoria de la primera vez: los anillos y la cifra de «Nutrición de hoy». */
private const val NUTRITION_FIGURE_KEY = "home-nutrition"

/** Las cuatro comidas de «Nutrición de hoy», en su orden. */
internal val HomeMeals: List<Pair<MealType, String>> = listOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.SNACK to "Merienda",
    MealType.DINNER to "Cena",
)

/**
 * Nutrición de hoy: los anillos de macros del wizard con las kcal, la leyenda de macros y las cuatro comidas como símbolos. Tocar
 * la fila del anillo abre el día ([onOpenDay]); tocar una comida abre el registro en esa comida ([onAddMeal] con su tipo), y el «+»
 * lo abre con la comida de la hora (null). Sin nutrición no se pinta: lo decide quien la llama.
 */
@Composable
fun HomeNutritionToday(
    state: HomeNutritionState,
    onAddMeal: (MealType?) -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    // Anillos y cifra se animan solo la primera vez que se ven en el proceso.
    val firstSeen = remember { HomeFigureMemory.firstTime(NUTRITION_FIGURE_KEY) }
    var appeared by remember { mutableStateOf(!firstSeen) }
    LaunchedEffect(Unit) { appeared = true }
    val countSpec: AnimationSpec<Int> =
        if (reduced) snap() else tween(durationMillis = 900, easing = FastOutSlowInEasing)
    val kcalShown by animateIntAsState(
        targetValue = if (appeared) state.kcal else 0,
        animationSpec = countSpec,
        label = "home-kcal",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionLabel(
            text = "NUTRICIÓN DE HOY",
            action = { HomeAddMealButton(onClick = { onAddMeal(null) }) },
            centerAction = true,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WizardSpacing.gutter)
                .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WizardMacroRings(
                kcal = state.kcal,
                fractions = nutritionRingFractions(state),
                caption = "kcal",
                description = nutritionDescription(state),
                appeared = appeared,
                reducedMotion = reduced,
                modifier = Modifier.size(140.dp),
                // Sin cifra central: la kcal grande va a la derecha; así no se repite ni pisa los arcos.
                showCenter = false,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatKcalEs(kcalShown),
                    style = HomeTypography.kcalFigure,
                    color = WizardColors.text,
                    maxLines = 1,
                    modifier = Modifier.semantics {
                        contentDescription = "${formatKcalEs(state.kcal)} kilocalorías comidas hoy"
                    },
                )
                Text(
                    text = kcalLine(state),
                    style = WizardTypography.caption,
                    color = WizardColors.textMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter)) {
            MacroLegendItem(
                name = "Proteína",
                macro = state.protein,
                color = MacroColors.protein,
                modifier = Modifier.weight(1f),
            )
            MacroLegendItem(
                name = "Hidratos",
                macro = state.carbs,
                color = MacroColors.carbs,
                modifier = Modifier.weight(1f),
            )
            MacroLegendItem(
                name = "Grasas",
                macro = state.fat,
                color = MacroColors.fat,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WizardSpacing.gutter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeMeals.forEach { (type, label) ->
                HomeMealCell(
                    type = type,
                    label = label,
                    kcal = state.mealKcal[type],
                    onClick = { onAddMeal(type) },
                )
            }
        }
    }
}

/** Anillos del wizard: kcal frente a la meta (1 = círculo entero = 1,25 × la meta) y cada macro frente a su meta. */
private fun nutritionRingFractions(state: HomeNutritionState): MacroRingFractions {
    val goal = state.kcalGoal
    return MacroRingFractions(
        calories = if (goal == null) 0f else ((state.kcal.toFloat() / goal) / WIZARD_RING_CALORIE_SCALE).coerceIn(0f, 1f),
        protein = macroShare(state.protein),
        carbs = macroShare(state.carbs),
        fat = macroShare(state.fat),
        // Sin meta no hay escala: ni arco ni marca (no se inventa una referencia).
        referenceMark = if (goal == null) null else 1f / WIZARD_RING_CALORIE_SCALE,
    )
}

private fun macroShare(macro: HomeMacroToday): Float {
    val goal = macro.goalGrams ?: return 0f
    return (macro.grams.toFloat() / goal).coerceIn(0f, 1f)
}

/** Lo que se lee bajo la cifra: lo que queda de la meta, lo que se pasa (sin rojo) o «sin objetivo». */
private fun kcalLine(state: HomeNutritionState): String {
    val goal = state.kcalGoal ?: return "sin objetivo"
    val eaten = state.kcal
    return when {
        eaten > goal -> "${formatKcalEs(eaten)} de ${formatKcalEs(goal)} kcal · ${formatKcalEs(eaten - goal)} por encima"
        eaten == goal -> "de ${formatKcalEs(goal)} kcal · meta cubierta"
        else -> "de ${formatKcalEs(goal)} kcal · te quedan ${formatKcalEs(goal - eaten)}"
    }
}

/** Texto accesible de los anillos: kcal frente a la meta y cada macro. */
private fun nutritionDescription(state: HomeNutritionState): String {
    val kcal = state.kcalGoal?.let { "${formatKcalEs(state.kcal)} kilocalorías de ${formatKcalEs(it)}" }
        ?: "${formatKcalEs(state.kcal)} kilocalorías, sin objetivo"
    return "Nutrición de hoy: $kcal. ${macroPhrase("Proteína", state.protein)}. " +
        "${macroPhrase("Hidratos", state.carbs)}. ${macroPhrase("Grasas", state.fat)}."
}

private fun macroPhrase(name: String, macro: HomeMacroToday): String =
    macro.goalGrams?.let { "$name ${macro.grams} de $it gramos" } ?: "$name ${macro.grams} gramos, sin meta"

/**
 * Un macro de la leyenda: el punto del color del anillo con su nombre y, debajo, «92 / 160 g» (o «92 g · sin meta»). Va en
 * una columna de la fila de tres, así que la cifra puede partirse en dos líneas con la letra grande en lugar de cortarse.
 */
@Composable
private fun MacroLegendItem(name: String, macro: HomeMacroToday, color: Color, modifier: Modifier) {
    val value = macro.goalGrams?.let { "${macro.grams} / $it g" } ?: "${macro.grams} g · sin meta"
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = WizardTypography.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = WizardTypography.caption,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Una comida: símbolo de 52 dp, nombre y kcal registradas o «Añadir». Toda la celda abre el registro en esa comida. Su ancho es el
 * de su texto (o el símbolo, si es más estrecho): así «Desayuno» no se corta con la letra grande.
 */
@Composable
private fun HomeMealCell(
    type: MealType,
    label: String,
    kcal: Int?,
    onClick: () -> Unit,
) {
    val logged = kcal != null
    val description = if (kcal != null) {
        "$label, ${formatKcalEs(kcal)} kilocalorías registradas. Añadir más"
    } else {
        "$label, sin registrar. Añadir"
    }
    val disc = if (logged) {
        Modifier.background(SymbolPalette.energia.copy(alpha = 0.14f), CircleShape)
    } else {
        Modifier.border(1.dp, WizardColors.glassBorder, CircleShape)
    }
    Column(
        modifier = Modifier
            .testTag("home-meal-${type.name}")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(52.dp).then(disc),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(52.dp)) {
                drawMealGlyph(type, if (logged) SymbolPalette.energia else WizardColors.textMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, style = WizardTypography.caption, color = WizardColors.text, maxLines = 1)
        Text(
            text = kcal?.let { "${formatKcalEs(it)} kcal" } ?: "Añadir",
            style = WizardTypography.caption,
            color = if (logged) WizardColors.textMuted else WizardColors.textFaint,
            maxLines = 1,
        )
    }
}

/**
 * Dibujo de línea de cada comida: un lienzo de 40 unidades con el dibujo de 24 centrado. Sol saliente (desayuno), plato
 * (almuerzo), taza con vapor (merienda) y media luna con estrella (cena). Trazos de 1,6 dp con extremos redondos.
 */
private fun DrawScope.drawMealGlyph(type: MealType, color: Color) {
    val k = size.minDimension / 40f
    fun p(x: Float, y: Float) = Offset((x + 8f) * k, (y + 8f) * k)
    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val line = 1.6.dp.toPx()
    when (type) {
        MealType.BREAKFAST -> {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = p(7f, 11f),
                size = Size(10f * k, 10f * k),
                style = stroke,
            )
            drawLine(color, p(3f, 16f), p(21f, 16f), strokeWidth = line, cap = StrokeCap.Round)
            for (angle in listOf(225f, 270f, 315f)) {
                val rad = Math.toRadians(angle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                drawLine(
                    color,
                    p(12f + 7.5f * dx, 16f + 7.5f * dy),
                    p(12f + 9.5f * dx, 16f + 9.5f * dy),
                    strokeWidth = line,
                    cap = StrokeCap.Round,
                )
            }
        }
        MealType.LUNCH -> {
            drawCircle(color, radius = 8f * k, center = p(12f, 12f), style = stroke)
            drawCircle(color, radius = 4.5f * k, center = p(12f, 12f), style = stroke)
        }
        MealType.SNACK -> {
            val cup = Path().apply {
                val a = p(5f, 10f)
                moveTo(a.x, a.y)
                val b = p(6.5f, 17.5f)
                lineTo(b.x, b.y)
                val c = p(15.5f, 17.5f)
                lineTo(c.x, c.y)
                val d = p(17f, 10f)
                lineTo(d.x, d.y)
            }
            drawPath(cup, color, style = stroke)
            drawLine(color, p(5f, 10f), p(17f, 10f), strokeWidth = line, cap = StrokeCap.Round)
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = p(14.5f, 11f),
                size = Size(5f * k, 5f * k),
                style = stroke,
            )
            drawLine(color, p(9.5f, 7f), p(9.5f, 4.5f), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(color, p(13.5f, 7f), p(13.5f, 4.5f), strokeWidth = line, cap = StrokeCap.Round)
        }
        MealType.DINNER -> {
            // Media luna: el contorno del disco grande (12, 12; radio 8) menos el del disco desplazado (16,5; 8,5; radio 6,5). Los
            // dos arcos se cortan en (11,8; 4,0) y (19,7; 14,2); la estrella cae en el hueco que deja el disco desplazado.
            val moon = Path().apply {
                val start = p(19.7f, 14.15f)
                moveTo(start.x, start.y)
                arcTo(Rect(p(4f, 4f), Size(16f * k, 16f * k)), 15.6f, 253f, false)
                arcTo(Rect(p(10f, 2f), Size(13f * k, 13f * k)), 223.8f, -163.3f, false)
                close()
            }
            drawPath(moon, color, style = stroke)
            drawCircle(color, radius = 1.1f * k, center = p(18.5f, 5.5f))
        }
    }
}

/** Botón «+» de la cabecera: 48 dp, con borde fino de tinta al 30 %. Abre el registro con la comida de la hora. */
@Composable
private fun HomeAddMealButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("home-nutrition-add")
            .size(48.dp)
            .border(1.dp, WizardColors.text.copy(alpha = 0.30f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Registrar comida" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.8.dp.toPx()
            val mid = size.width / 2f
            val middle = size.height / 2f
            drawLine(WizardColors.text, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(WizardColors.text, Offset(0f, middle), Offset(size.width, middle), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
