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
import com.example.kpkn.screens.onboarding.design.WizardNutritionDay
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.weekdayNameEs
import java.time.LocalDate
import kotlin.math.round

/*
 * Textos y cifras del editor de Nutrición, sin Compose: se prueban en la JVM. Las cifras de las frases que lee la prueba
 * instrumentada («Base revisada…», «La meta de hoy…») van sin separador de miles; el resto, con punto de millar («2.150 kcal»).
 */

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

/** Primera línea de la base revisada: la frase que se guarda tal cual, sin cifras. */
const val EDITOR_BASE_REVISED_LEAD = "Base revisada que se guardará tal cual:"

/** Cifras de la base revisada en una línea de lectura: cada cifra va con su unidad y no se parte al ajustar la línea. */
fun editorBaseRevisedFigures(base: NutritionEditorBase): String =
    "${base.caloriesKcal} kcal · P ${base.proteinG} g · H ${base.carbsG} g · G ${base.fatG} g"

/** Línea completa de la base revisada (cifras sin separador de miles: así las lee la prueba instrumentada y el lector de pantalla). */
fun editorBaseRevisedLine(base: NutritionEditorBase): String =
    "$EDITOR_BASE_REVISED_LEAD ${base.caloriesKcal} kcal · P ${base.proteinG} g · H ${base.carbsG} g · G ${base.fatG} g"

/** Total energético Atwater, solo si dice algo distinto de la base revisada. */
fun editorAtwaterLine(base: NutritionEditorBase): String? =
    base.atwaterEnergyKcal.takeIf { it != base.caloriesKcal }?.let { "Total energético mostrado: $it kcal" }

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

/** Días del reparto tal como los dibuja la franja; el de hoy se marca. */
fun editorWeekDays(targets: List<NutritionEditorDayTarget>, today: LocalDate): List<WizardNutritionDay> =
    targets.map { day ->
        WizardNutritionDay(
            date = day.date,
            kcal = day.calorieTargetKcal,
            proteinG = day.proteinG,
            carbsG = day.carbsG,
            fatG = day.fatG,
            isToday = day.date == today,
        )
    }

/** Línea del día elegido en la franja: «Lunes · P 160 · H 210 · G 70 g». */
fun editorDayLine(day: NutritionEditorDayTarget): String {
    val name = weekdayNameEs(day.date.dayOfWeek).replaceFirstChar { it.uppercaseChar() }
    return "$name · P ${day.proteinG} · H ${day.carbsG} · G ${day.fatG} g"
}
