package com.example.kpkn.screens.nutrition.design.editor

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.percentsOfEnergy
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WIZARD_RING_CALORIE_SCALE
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.weekdayNameEs
import kotlin.math.abs
import kotlin.math.round

/*
 * Textos y cifras del editor de Nutrición, sin Compose: se prueban en la JVM. Las cifras llevan punto de millar («2.150 kcal»), y cada
 * unidad va pegada a su cifra con un espacio que no se parte, para que una línea no deje «kcal» suelto.
 */

/** Tolerancia entre la energía de los macros y las calorías antes de avisar: el 2 %. */
private const val MacroGapTolerance = 0.02

/** Altura mínima de una barra del reparto, como fracción del gráfico: ningún día desaparece aunque tenga pocas kcal. */
const val EDITOR_MIN_BAR_FRACTION = 0.30f

/** Kcal con su unidad y punto de millar («2.150 kcal»); sin dato, «—». */
fun editorKcalText(kcal: Int?): String = kcal?.let { "${formatKcalEs(it)} kcal" } ?: "—"

/**
 * Lo que cambia al guardar, junto al botón: «2.300 → 2.150 kcal». Sin cambios, «Sin cambios»; un plan nuevo, «Plan nuevo · 2.150 kcal»;
 * si cambian otros datos y las kcal no, «Cambios · 2.150 kcal».
 */
fun editorChangeText(originalKcal: Int?, currentKcal: Int?, dirty: Boolean, isNew: Boolean): String = when {
    !dirty -> "Sin cambios"
    isNew || originalKcal == null -> "Plan nuevo · ${editorKcalText(currentKcal)}"
    originalKcal == currentKcal -> "Cambios · ${editorKcalText(currentKcal)}"
    else -> "${formatKcalEs(originalKcal)} → ${currentKcal?.let { formatKcalEs(it) } ?: "—"} kcal"
}

/** Unidad de la meta de peso a partir del código del borrador («kg» o «lb»). */
fun editorWeightUnit(code: String): WizardMassUnit =
    if (code.equals(WizardMassUnit.LB.code, ignoreCase = true)) WizardMassUnit.LB else WizardMassUnit.KG

/** Meta de peso (kg) en la unidad del borrador, con una décima y coma decimal: «72,6» kg o «160,0» lb. */
fun editorWeightTargetText(kg: Double, unit: WizardMassUnit): String =
    formatDecimalEs(WizardWeightScale.toDisplay(kg, unit), 1)

/** Meta de peso del borrador (texto en su unidad) a kg; null si no es un número positivo. */
fun editorWeightTargetKg(text: String, unitCode: String): Double? =
    parseLocalizedNumber(text)?.let { kilogramsFromInput(it, unitCode) }

/** Calorías escritas, redondeadas a kcal enteras; null si no es un número. */
fun editorCaloriesFromText(text: String): Int? = parseLocalizedNumber(text)?.let { round(it).toInt() }

/** Macros en una línea, como en Home: «P 160 · H 210 · G 70 g»; sin base, guiones. */
fun editorMacrosLine(base: NutritionEditorBase?): String {
    val protein = base?.proteinG?.toString() ?: "—"
    val carbs = base?.carbsG?.toString() ?: "—"
    val fat = base?.fatG?.toString() ?: "—"
    return "P $protein · H $carbs · G $fat g"
}

/**
 * Aviso de la diferencia entre la energía de los macros (4/4/9) y las calorías, solo si pasa del 2 %: «Tus macros suman 2.110 kcal, 40
 * menos que tus calorías.». Sin calorías o sin macros no hay nada que comparar.
 */
fun editorMacroGapLine(base: NutritionEditorBase): String? {
    if (base.caloriesKcal <= 0 || base.proteinG + base.carbsG + base.fatG == 0) return null
    val macroKcal = base.atwaterEnergyKcal
    val gap = macroKcal - base.caloriesKcal
    if (abs(gap) <= base.caloriesKcal * MacroGapTolerance) return null
    val side = if (gap < 0) "menos" else "más"
    return "Tus macros suman ${formatKcalEs(macroKcal)} kcal, ${formatKcalEs(abs(gap))} $side que tus calorías."
}

/** Aviso de que la meta de hoy ya la fijó este plan: lo que cambie vale desde mañana. */
fun editorTodayGoalLine(kcal: Int): String =
    "La meta de hoy ya quedó fijada en $kcal kcal. Lo que cambies en este plan vale desde mañana."

/** Reparto de energía de proteína, hidratos y grasas en porcentaje entero (suman 100); null sin base o sin energía. */
fun editorEnergyPercents(base: NutritionEditorBase?): Triple<Int, Int, Int>? {
    if (base == null) return null
    val (protein, carbs, fat) = percentsOfEnergy(base.proteinG, base.carbsG, base.fatG)
    return if (protein + carbs + fat == 0) null else Triple(protein, carbs, fat)
}

/** Leyenda del reparto: «30 % · 40 % · 30 %». */
fun editorEnergyLegend(percents: Triple<Int, Int, Int>): String =
    "${percents.first} % · ${percents.second} % · ${percents.third} %"

/**
 * Anillos del editor, en vivo. El exterior son las kcal frente al mantenimiento (EER), con la escala de los anillos del wizard; los tres
 * interiores, la parte de la energía que aporta cada macro. No se inventan límites: sin EER no hay marca y el exterior queda lleno, como
 * en el wizard. Sin base, todo vacío.
 */
fun editorRingFractions(base: NutritionEditorBase?, eerKcal: Double?): MacroRingFractions {
    val eer = eerKcal?.takeIf { it > 0.0 }
    val mark = eer?.let { 1f / WIZARD_RING_CALORIE_SCALE }
    if (base == null) return MacroRingFractions(calories = 0f, protein = 0f, carbs = 0f, fat = 0f, referenceMark = mark)
    val calories = if (eer == null) 1f else ((base.caloriesKcal / eer).toFloat() / WIZARD_RING_CALORIE_SCALE).coerceIn(0f, 1f)
    val (protein, carbs, fat) = editorEnergyPercents(base) ?: Triple(0, 0, 0)
    return MacroRingFractions(
        calories = calories,
        protein = protein / 100f,
        carbs = carbs / 100f,
        fat = fat / 100f,
        referenceMark = mark,
    )
}

/** Descripción accesible de los anillos: las kcal y el reparto de energía, o que no hay base. */
fun editorRingsDescription(base: NutritionEditorBase?): String {
    if (base == null) return "Sin base revisada: los anillos están vacíos."
    val legend = editorEnergyPercents(base)?.let { " Reparto de energía en proteína, hidratos y grasas: ${editorEnergyLegend(it)}." }
    return "Calorías: ${editorKcalText(base.caloriesKcal)}.${legend.orEmpty()}"
}

/** Lectura de la dirección elegida. Con procedencia profesional la define el profesional, y sin dirección se pide elegir una. */
fun editorDirectionLine(direction: PlanDirection?): String = when (direction) {
    PlanDirection.DEFICIT -> "Comes un poco menos de lo que gastas para bajar grasa sin perder músculo."
    PlanDirection.MAINTENANCE -> "Comes lo que gastas para sostener tu peso mientras entrenas."
    PlanDirection.SURPLUS -> "Comes un poco más de lo que gastas para ganar músculo poco a poco."
    PlanDirection.PROFESSIONAL -> "La define tu profesional"
    null -> "Elige una dirección para calcular la base."
}

/**
 * Altura de cada barra del reparto: proporcional a sus kcal frente al día más alto, con un mínimo del 30 %. Sin kcal, todas al mínimo.
 */
fun editorBarFractions(kcals: List<Int>): List<Float> {
    val highest = kcals.maxOrNull()?.takeIf { it > 0 } ?: return kcals.map { EDITOR_MIN_BAR_FRACTION }
    return kcals.map { kcal -> (kcal.toFloat() / highest).coerceIn(EDITOR_MIN_BAR_FRACTION, 1f) }
}

/** Línea del día elegido bajo el gráfico: «Miércoles · 2.400 kcal · P 170 · H 260 · G 75 g». */
fun editorDayLine(day: NutritionEditorDayTarget): String {
    val name = weekdayNameEs(day.date.dayOfWeek).replaceFirstChar { it.uppercaseChar() }
    return "$name · ${formatKcalEs(day.calorieTargetKcal)} kcal · " +
        "P ${day.proteinG} · H ${day.carbsG} · G ${day.fatG} g"
}
