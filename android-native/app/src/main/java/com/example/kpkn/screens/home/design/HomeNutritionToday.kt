package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.home.HomeNutritionState
import com.example.kpkn.screens.nutrition.design.NutritionMacroLine
import com.example.kpkn.screens.nutrition.design.NutritionMacroLines
import com.example.kpkn.screens.nutrition.design.NutritionThinRing
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.nutrition.design.mealIcon
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Clave de la memoria de la primera vez: el anillo y la cifra de «Nutrición de hoy». */
private const val NUTRITION_FIGURE_KEY = "home-nutrition"

/** Etiqueta de prueba del contenedor del anillo de «Nutrición de hoy» (solo se pinta con meta de kcal). */
internal const val HOME_NUTRITION_RINGS_TAG = "home-nutrition-rings"

/** Las cuatro comidas de «Nutrición de hoy», en su orden y con su nombre de [MealSlots]. */
internal val HomeMeals: List<Pair<MealType, String>> = MealSlots.mealOrder.map { it to MealSlots.mealLabel(it) }

/** Diámetro del anillo fino de «Nutrición de hoy»: 96 dp (`npRing(k, 2300, 96)`). */
private val NutritionRingSize = 96.dp

/** Aire entre el anillo y las filas de macros (`.np-day{gap:18px}`). */
private val NutritionRingGap = 18.dp

/** Curva del rebote del lienzo (`.np-ring.bump`): cubic-bezier(.3, 1.6, .5, 1), con sobrepaso. */
private val HomeBumpEasing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

/**
 * «Nutrición de hoy» (Arreglos v1, `npHome`): una tarjeta plana (`surface1`, radio 24, sin borde) con la cabecera «NUTRICIÓN DE HOY» y
 * «objetivo 2.300», el anillo fino monocromo de 96 dp con la cifra dentro y «de 2.300» debajo, las filas de proteína, carbos y grasa
 * con su barra fina y, abajo, el botón plano «＋ Registrar comida» a todo el ancho ([onAddMeal] con null = la comida que toca por la
 * hora). Sin meta, en lugar del anillo, la cifra a todo el ancho, «Aún no tienes objetivos.» y «Definir objetivos». Debajo de la
 * tarjeta, las cuatro comidas (tocar una abre su registro, [onAddMeal] con su tipo). Tocar el anillo y las filas abre el día
 * ([onOpenDay]). Sin nutrición no se pinta: lo decide quien la llama.
 */
@Composable
fun HomeNutritionToday(
    state: HomeNutritionState,
    onAddMeal: (MealType?) -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Abre el editor del plan de nutrición: la acción «Definir objetivos», sin meta de kcal. */
    onDefineGoals: () -> Unit = {},
    /** f-home-done: cambia cada vez que se guarda una comida desde Inicio; 0 = nada guardado aún. */
    savedPulse: Int = 0,
) {
    val reduced = wizardReducedMotion()
    // f-home-done: al guardar desde Inicio, la cifra cuenta hasta el nuevo total y el anillo rebota (lienzo `.np-ring.bump`: escala
    // 0,94 → 1,06 en el 45 % → 1, 700 ms con cubic-bezier(.3, 1.6, .5, 1) por tramo). Con reducir movimiento no rebota.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(savedPulse) {
        if (savedPulse == 0 || reduced) return@LaunchedEffect
        bump.snapTo(0.94f)
        bump.animateTo(1.06f, tween(durationMillis = 315, easing = HomeBumpEasing))
        bump.animateTo(1f, tween(durationMillis = 385, easing = HomeBumpEasing))
    }
    // El anillo y la cifra se animan solo la primera vez que se ven en el proceso.
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
    val goal = state.kcalGoal
    val largeText = LocalDensity.current.fontScale > HomeLargeTextScale
    val macros = nutritionMacroLines(state)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = HomeModuleMargin),
        verticalArrangement = Arrangement.spacedBy(HomeModuleGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LoggerColors.surface1, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            // `.np-mh`: la etiqueta a la izquierda y «objetivo 2.300» a la derecha.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "NUTRICIÓN DE HOY", style = KpknType.eyebrow, color = WizardColors.textMuted)
                if (goal != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(text = "objetivo ${formatKcalEs(goal)}", style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1)
                }
            }
            if (goal == null) {
                // Sin meta: la cifra a todo el ancho (fuera de la zona de la acción), cómo definirla y las cifras de los macros.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay),
                ) {
                    NutritionFigure(kcalShown = kcalShown, kcal = state.kcal, style = KpknType.metricXL)
                }
                Text(text = "Aún no tienes objetivos.", style = KpknType.body, color = WizardColors.textMuted)
                KpknTextAction(text = "Definir objetivos", onClick = onDefineGoals, icon = KpknLineIcons.ChevronRight)
                NutritionMacroLines(lines = macros, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } else {
                val ring: @Composable () -> Unit = {
                    // El contenedor lleva el rebote y la etiqueta de prueba; el anillo pone su propia descripción.
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                            .testTag(HOME_NUTRITION_RINGS_TAG),
                    ) {
                        NutritionThinRing(
                            kcal = state.kcal,
                            goal = goal,
                            size = NutritionRingSize,
                            appeared = appeared,
                            reducedMotion = reduced,
                        )
                    }
                }
                val dayModifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay)
                if (largeText) {
                    // A escala de letra grande las filas no caben junto al anillo: el anillo va arriba y las filas a todo el ancho.
                    Column(modifier = dayModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        ring()
                        NutritionMacroLines(lines = macros, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                    }
                } else {
                    Row(
                        modifier = dayModifier,
                        horizontalArrangement = Arrangement.spacedBy(NutritionRingGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ring()
                        NutritionMacroLines(lines = macros, modifier = Modifier.weight(1f))
                    }
                }
            }
            HomeRegisterMealButton(onClick = { onAddMeal(null) }, modifier = Modifier.padding(top = 16.dp))
        }
        // Las cuatro comidas del día, bajo la tarjeta: tocar una abre el registro en esa comida.
        KpknModule(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            if (largeText) {
                // A escala grande las cuatro comidas pasan a dos filas de dos: cada una conserva su celda completa.
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(modifier = Modifier.fillMaxWidth()) {
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
}

/**
 * `.np-add`: botón plano a todo el ancho de 46 dp, radio 23, fondo `surface2`, con el más y «Registrar comida» (Inter SemiBold 14,
 * tinta). Abre el registro completo con la comida que toca por la hora.
 */
@Composable
private fun HomeRegisterMealButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(LoggerColors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-nutrition-add"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = LoggerColors.ink,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Registrar comida",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
            color = LoggerColors.ink,
            maxLines = 1,
        )
    }
}

/** Las tres filas de macros de hoy, con los nombres del lienzo («Proteína», «Carbos», «Grasa») y sin color por macro. */
private fun nutritionMacroLines(state: HomeNutritionState): List<NutritionMacroLine> = listOf(
    NutritionMacroLine("Proteína", state.protein.grams, state.protein.goalGrams),
    NutritionMacroLine("Carbos", state.carbs.grams, state.carbs.goalGrams),
    NutritionMacroLine("Grasa", state.fat.grams, state.fat.goalGrams),
)

/** La cifra de kcal de hoy sin meta. Cuenta hacia arriba; su semántica da el valor final, no el de cada cuadro. */
@Composable
private fun NutritionFigure(kcalShown: Int, kcal: Int, style: TextStyle) {
    Box(
        Modifier.clearAndSetSemantics {
            contentDescription = "${formatKcalEs(kcal)} kilocalorías comidas hoy"
        },
    ) {
        KpknMetric(value = formatKcalEs(kcalShown), unit = "kcal", style = style)
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
