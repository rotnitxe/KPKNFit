package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.screens.home.HomeMacroToday
import com.example.kpkn.screens.home.HomeNutritionState
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WIZARD_RING_CALORIE_SCALE
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMacroRings
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.MacroColors

/** Clave de la memoria de la primera vez: los anillos y la cifra de «Nutrición de hoy». */
private const val NUTRITION_FIGURE_KEY = "home-nutrition"

/** Etiqueta de prueba del contenedor de los anillos de «Nutrición de hoy» (solo se pintan con meta de kcal). */
internal const val HOME_NUTRITION_RINGS_TAG = "home-nutrition-rings"

/** Las cuatro comidas de «Nutrición de hoy», en su orden. */
internal val HomeMeals: List<Pair<MealType, String>> = listOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.SNACK to "Merienda",
    MealType.DINNER to "Cena",
)

/** Diámetro de los anillos de macros de «Nutrición de hoy» (Home v1.1 §5). */
private val NutritionRingSize = 120.dp

/**
 * «Nutrición de hoy»: un módulo con su cabecera (el «+» registra una comida), las kcal contando a todo el ancho, debajo los anillos de
 * macros con la lectura de la meta, la leyenda de macros y las cuatro comidas. Sin meta, «Aún no tienes objetivos.» y «Definir objetivos».
 * Tocar la cifra de kcal y los anillos abre el día ([onOpenDay]); tocar una comida abre su registro ([onAddMeal] con su tipo), y el «+» lo
 * abre con la comida de la hora (null). Sin nutrición no se pinta: lo decide quien la llama.
 */
@Composable
fun HomeNutritionToday(
    state: HomeNutritionState,
    onAddMeal: (MealType?) -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Abre el editor del plan de nutrición: la acción «Definir objetivos», sin meta de kcal. */
    onDefineGoals: () -> Unit = {},
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
    // Con meta hay anillos y lectura de la meta; sin meta no se pintan (saldrían apagados): queda la cifra y cómo definir la meta.
    val goal = state.kcalGoal
    val largeText = LocalDensity.current.fontScale > HomeLargeTextScale

    KpknModule(modifier = modifier.fillMaxWidth().padding(horizontal = HomeModuleMargin)) {
        KpknModuleHeader(
            label = "Nutrición de hoy",
            action = {
                KpknIconButton(
                    icon = KpknLineIcons.Plus,
                    contentDescription = "Registrar comida",
                    onClick = { onAddMeal(null) },
                    modifier = Modifier.testTag("home-nutrition-add"),
                )
            },
        )
        // La cifra de kcal va a todo el ancho: con «kcal» no cabe junto a un anillo de 120 dp.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay),
        ) {
            // La cifra cuenta hacia arriba; su semántica da el valor final, no el de cada cuadro.
            Box(
                Modifier.clearAndSetSemantics {
                    contentDescription = "${formatKcalEs(state.kcal)} kilocalorías comidas hoy"
                },
            ) {
                KpknMetric(value = formatKcalEs(kcalShown), unit = "kcal", style = KpknType.metricXL)
            }
            if (goal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // El contenedor lleva la etiqueta de prueba: los anillos ya ponen la suya («setup-nutrition-rings») y la pisaría.
                    Box(Modifier.size(NutritionRingSize).testTag(HOME_NUTRITION_RINGS_TAG)) {
                        WizardMacroRings(
                            kcal = state.kcal,
                            fractions = nutritionRingFractions(state),
                            caption = "kcal",
                            description = nutritionDescription(state),
                            appeared = appeared,
                            reducedMotion = reduced,
                            modifier = Modifier.fillMaxSize(),
                            // Sin cifra central: la kcal grande va arriba; así no se repite ni pisa los arcos.
                            showCenter = false,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = kcalLine(state, goal),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (goal == null) {
            // Fuera de la zona clicable, para que la acción no anide otro botón.
            Text(text = "Aún no tienes objetivos.", style = KpknType.body, color = WizardColors.textMuted)
            KpknTextAction(text = "Definir objetivos", onClick = onDefineGoals, icon = KpknLineIcons.ChevronRight)
        }
        if (largeText) {
            // A escala grande, tres columnas no caben con metas de tres cifras: la leyenda pasa a tres filas, nombre a la izquierda y cifra a la derecha.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroLegendItem(
                    name = "Proteína",
                    macro = state.protein,
                    color = MacroColors.protein,
                    modifier = Modifier.fillMaxWidth(),
                    stacked = true,
                )
                MacroLegendItem(
                    name = "Hidratos",
                    macro = state.carbs,
                    color = MacroColors.carbs,
                    modifier = Modifier.fillMaxWidth(),
                    stacked = true,
                )
                MacroLegendItem(
                    name = "Grasas",
                    macro = state.fat,
                    color = MacroColors.fat,
                    modifier = Modifier.fillMaxWidth(),
                    stacked = true,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
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
        }
        if (largeText) {
            // A escala grande las cuatro comidas pasan a dos filas de dos: cada una conserva su celda completa.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeMeals.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { (type, label) ->
                            HomeMealCell(
                                type = type,
                                label = label,
                                kcal = state.mealKcal[type],
                                onClick = { onAddMeal(type) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                HomeMeals.forEach { (type, label) ->
                    HomeMealCell(
                        type = type,
                        label = label,
                        kcal = state.mealKcal[type],
                        onClick = { onAddMeal(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
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

/** Lo que se lee bajo la cifra con meta: lo que queda de la meta, lo que se pasa (sin rojo) o «meta cubierta». */
private fun kcalLine(state: HomeNutritionState, goal: Int): String {
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
 * La cifra de un macro: la cantidad en `bodyStrong` tabular y, con meta, «/meta» en `caption`. Sin espacio junto a la barra: así
 * «92 g/160 g» cabe en una columna de 96 dp a 130 % de letra. Si no cupiera, el espacio de cero ancho deja bajar la meta a otra línea.
 */
private fun macroFigure(macro: HomeMacroToday): AnnotatedString = buildAnnotatedString {
    withStyle(
        SpanStyle(
            color = WizardColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
        ),
    ) {
        // Espacio sin corte entre la cifra y su unidad: la unidad nunca queda sola en una línea.
        append("${macro.grams} g")
    }
    macro.goalGrams?.let { goal ->
        withStyle(SpanStyle(color = WizardColors.textFaint, fontSize = 13.sp, fontWeight = FontWeight.Normal)) {
            append("/​$goal g")
        }
    }
}

/**
 * Un macro de la leyenda: el punto de su color con el nombre y la cifra ([macroFigure]). En tres columnas iguales (por defecto), el nombre va
 * en la primera línea y la cifra en la segunda. Con [stacked] (escala grande), el nombre va a la izquierda de la fila y la cifra a la derecha.
 */
@Composable
private fun MacroLegendItem(
    name: String,
    macro: HomeMacroToday,
    color: Color,
    modifier: Modifier,
    stacked: Boolean = false,
) {
    val nameLine: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (stacked) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            nameLine()
            Text(
                text = macroFigure(macro),
                style = KpknType.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(modifier = modifier) {
            nameLine()
            Text(
                text = macroFigure(macro),
                style = KpknType.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Una comida: un círculo `inset` de 44 dp con su icono de línea (en tinta si está registrada, con un check de `done` en la esquina), el
 * nombre y, debajo, las kcal registradas o «Añadir» en `textFaint`. Toda la celda abre el registro en esa comida.
 */
@Composable
private fun HomeMealCell(
    type: MealType,
    label: String,
    kcal: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logged = kcal != null
    val description = if (kcal != null) {
        "$label, ${formatKcalEs(kcal)} kilocalorías registradas. Añadir más"
    } else {
        "$label, sin registrar. Añadir"
    }
    Column(
        modifier = modifier
            .testTag("home-meal-${type.name}")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(KpknSurface.inset, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mealIcon(type),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (logged) WizardColors.text else WizardColors.textFaint,
                )
            }
            if (logged) {
                // La marca de registrada: un check de 14 dp en `done` sobre un hueco del color del módulo, en la esquina superior derecha.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(KpknSurface.module, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = WizardColors.done,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = KpknType.caption, color = WizardColors.text, maxLines = 1)
        Text(
            text = kcal?.let { "${formatKcalEs(it)} kcal" } ?: "Añadir",
            style = KpknType.caption,
            color = if (logged) WizardColors.textMuted else WizardColors.textFaint,
            maxLines = 1,
        )
    }
}

/** Icono de línea de cada comida: amanecer (desayuno), sol (almuerzo), manzana (merienda) y luna (cena). */
private fun mealIcon(type: MealType): ImageVector = when (type) {
    MealType.BREAKFAST -> KpknLineIcons.Sunrise
    MealType.LUNCH -> KpknLineIcons.Sun
    MealType.SNACK -> KpknLineIcons.Apple
    MealType.DINNER -> KpknLineIcons.Moon
}
