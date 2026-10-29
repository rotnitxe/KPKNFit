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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerIconButton
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.home.HomeMacroToday
import com.example.kpkn.screens.home.HomeNutritionState
import com.example.kpkn.screens.nutrition.design.mealIcon
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

/** F-D5: el «+» de «Nutrición de hoy» solo queda sin plan de nutrición (sin meta de kcal); con plan registra la barra «¿Qué comiste?». */
internal fun homeNutritionShowsAddAction(kcalGoal: Int?): Boolean = kcalGoal == null

/** F-D5: la barra «¿Qué comiste?» de Inicio solo aparece con la nutrición activa y un plan (meta de kcal). */
internal fun homeShowsQuickLogger(showNutrition: Boolean, kcalGoal: Int?): Boolean = showNutrition && kcalGoal != null && kcalGoal > 0

/** Clave de la memoria de la primera vez: los anillos y la cifra de «Nutrición de hoy». */
private const val NUTRITION_FIGURE_KEY = "home-nutrition"

/** Etiqueta de prueba del contenedor de los anillos de «Nutrición de hoy» (solo se pintan con meta de kcal). */
internal const val HOME_NUTRITION_RINGS_TAG = "home-nutrition-rings"

/** Las cuatro comidas de «Nutrición de hoy», en su orden y con su nombre de [MealSlots]. */
internal val HomeMeals: List<Pair<MealType, String>> = MealSlots.mealOrder.map { it to MealSlots.mealLabel(it) }

/** Diámetro de los anillos de macros de «Nutrición de hoy»: 112 dp, a la izquierda de la cifra (Home v1.1, I12). */
private val NutritionRingSize = 112.dp

/** Aire entre los anillos y la columna de la cifra con su lectura. */
private val NutritionRingGap = 16.dp

/**
 * «Nutrición de hoy»: un módulo con su cabecera (el «+» registra una comida). Con meta, los anillos de macros a la izquierda y, a la
 * derecha, las kcal con su lectura (I12); si «12.345 kcal» no cabe junto a los anillos, la cifra va a todo el ancho y los anillos debajo.
 * Sin meta, la cifra a todo el ancho, «Aún no tienes objetivos.» y «Definir objetivos». Debajo, la leyenda de macros y las cuatro
 * comidas. Tocar la cifra de kcal y los anillos abre el día ([onOpenDay]); tocar una comida abre su registro ([onAddMeal] con su tipo), y
 * el «+» lo abre con la comida de la hora (null). Sin nutrición no se pinta: lo decide quien la llama.
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
            // Registros v4 (F-D5): con plan, la barra «¿Qué comiste?» de Inicio sustituye al «+»; sin plan, Inicio queda como estaba.
            action = if (homeNutritionShowsAddAction(goal)) {
                {
                    KpknIconButton(
                        icon = KpknLineIcons.Plus,
                        contentDescription = "Registrar comida",
                        onClick = { onAddMeal(null) },
                        modifier = Modifier.testTag("home-nutrition-add"),
                    )
                }
            } else null,
        )
        if (goal == null) {
            // Sin meta: la cifra a todo el ancho, como en I8, y la acción para definirla (fuera de la zona clicable).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay),
            ) {
                NutritionFigure(kcalShown = kcalShown, kcal = state.kcal, style = KpknType.metricXL)
            }
            Text(text = "Aún no tienes objetivos.", style = KpknType.body, color = WizardColors.textMuted)
            KpknTextAction(text = "Definir objetivos", onClick = onDefineGoals, icon = KpknLineIcons.ChevronRight)
        } else {
            // Con meta: los anillos a la izquierda y la cifra con su lectura a la derecha (I12). Si «12.345 kcal» no cabe junto a
            // los anillos a la escala de letra, la cifra pasa a todo el ancho y los anillos van debajo.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button, onClickLabel = "Ver el día de hoy", onClick = onOpenDay),
            ) {
                val ringsAndGapPx = with(density) { (NutritionRingSize + NutritionRingGap).roundToPx() }
                val sideBySide = remember(constraints.maxWidth, density, measurer) {
                    nutritionFigureFits(measurer, density, availablePx = constraints.maxWidth - ringsAndGapPx)
                }
                if (sideBySide) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NutritionRings(state = state, appeared = appeared, reducedMotion = reduced)
                        Spacer(Modifier.width(NutritionRingGap))
                        Column(modifier = Modifier.weight(1f)) {
                            NutritionFigure(kcalShown = kcalShown, kcal = state.kcal, style = KpknType.metricL)
                            NutritionGoalLines(state = state, goal = goal)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NutritionFigure(kcalShown = kcalShown, kcal = state.kcal, style = KpknType.metricXL)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NutritionRings(state = state, appeared = appeared, reducedMotion = reduced)
                            Spacer(Modifier.width(NutritionRingGap))
                            Column(modifier = Modifier.weight(1f)) {
                                NutritionGoalLines(state = state, goal = goal)
                            }
                        }
                    }
                }
            }
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

/** La cifra de kcal de hoy. Cuenta hacia arriba; su semántica da el valor final, no el de cada cuadro. */
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

/** Los anillos de macros con meta, con la etiqueta de prueba de su contenedor. */
@Composable
private fun NutritionRings(state: HomeNutritionState, appeared: Boolean, reducedMotion: Boolean) {
    // El contenedor lleva la etiqueta de prueba: los anillos ya ponen la suya («setup-nutrition-rings») y la pisaría.
    Box(Modifier.size(NutritionRingSize).testTag(HOME_NUTRITION_RINGS_TAG)) {
        WizardMacroRings(
            kcal = state.kcal,
            fractions = nutritionRingFractions(state),
            caption = "kcal",
            description = nutritionDescription(state),
            appeared = appeared,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize(),
            // Sin cifra central: la kcal grande va arriba; así no se repite ni pisa los arcos.
            showCenter = false,
        )
    }
}

/** Lo que se lee junto a la cifra con meta: «de 2.043 kcal» y lo que queda, en `danger` si se ha pasado. */
@Composable
private fun NutritionGoalLines(state: HomeNutritionState, goal: Int) {
    Text(text = "de ${formatKcalEs(goal)} kcal", style = KpknType.caption, color = WizardColors.textMuted)
    Text(
        text = kcalRemainingText(eaten = state.kcal, goal = goal),
        style = KpknType.caption,
        color = if (state.kcal > goal) WizardColors.danger else WizardColors.textMuted,
    )
}

/** Lo que queda de la meta: «te quedan 1.045», «meta cubierta» o «te pasaste 120». */
private fun kcalRemainingText(eaten: Int, goal: Int): String = when {
    eaten > goal -> "te pasaste ${formatKcalEs(eaten - goal)}"
    eaten == goal -> "meta cubierta"
    else -> "te quedan ${formatKcalEs(goal - eaten)}"
}

/**
 * ¿Cabe la cifra más ancha que se prevé («12.345 kcal») junto a los anillos? Se mide con el mismo tipo y la misma escala de letra que
 * se pintan; [availablePx] es el ancho que queda para la columna de la cifra.
 */
private fun nutritionFigureFits(measurer: TextMeasurer, density: Density, availablePx: Int): Boolean {
    val value = measurer.measure(text = formatKcalEs(12345), style = KpknType.metricL, maxLines = 1).size.width
    val unit = measurer.measure(text = "kcal", style = KpknType.unit, maxLines = 1).size.width
    val gapPx = with(density) { 3.dp.roundToPx() }
    return value + gapPx + unit <= availablePx
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

/**
 * La barra «¿Qué comiste?» de Inicio (lienzo `qbar`, f-home y f-home-type): píldora de vidrio de 56 dp con el texto (crece hasta tres
 * líneas), el micrófono «Dictar» y, con algo escrito, «Analizar». Va sobre la barra de navegación; quien la llama decide si se pinta
 * ([homeShowsQuickLogger]).
 */
@Composable
internal fun HomeQuickLoggerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDictate: () -> Unit,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 20.sp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .kpknGlassOrFallback(LocalHazeState.current, RoundedCornerShape(28.dp), variant = KpknGlassVariant.ISLAND)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = style.copy(color = LoggerColors.ink),
            cursorBrush = SolidColor(LoggerColors.ink),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAnalyze() }),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "¿Qué comiste?" },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(text = "¿Qué comiste? Escríbelo aquí", style = style, color = LoggerColors.dim, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        LoggerIconButton(
            icon = KpknLineIcons.Mic,
            contentDescription = if (listening) "Dejar de dictar" else "Dictar",
            onClick = onDictate,
            fill = true,
            tint = if (listening) LoggerColors.mus else LoggerColors.muted,
        )
        if (text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LoggerColors.ink)
                    .clickable(role = Role.Button, onClick = onAnalyze)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Analizar", style = style.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF111111))
            }
        }
    }
}
