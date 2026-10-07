package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupEquationSexValues
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardHeightScale
import com.example.kpkn.screens.onboarding.design.WizardHeightUnit
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.formatKcalEs

/**
 * Textos de las filas-resumen de la página larga del alta: cada página ya
 * confirmada se pliega en una fila con una etiqueta corta y el valor de una
 * línea («Altura y peso · 172 cm · 70,1 kg»).
 *
 * Lógica pura (sin `android.*` ni Compose), solo lectura sobre el borrador y las
 * vistas previas del estado: la UI pinta lo que devuelve [setupStepSummary] y
 * nunca compone estos textos por su cuenta.
 *
 * Reglas:
 * - [SetupStepSummary.label]: nombre corto en español, sentence case, como máximo
 *   [SETUP_SUMMARY_LABEL_MAX] caracteres. Los hitos usan el título del bloque.
 * - [SetupStepSummary.value]: una línea, nunca vacía, como máximo
 *   [SETUP_SUMMARY_VALUE_MAX] caracteres (recorta con «…»). Es lo que la persona
 *   eligió: el texto, la etiqueta de la opción del catálogo ([SetupStepDefinitions]),
 *   hasta dos etiquetas más « +N» en las múltiples, o «N unidad» en las numéricas.
 * - Sin dato: «Omitido» si el paso admite omitirse y se omitió; «Sin responder» en
 *   otro caso. Nunca se inventa un valor ni se muestra un valor por defecto del
 *   borrador como si lo hubiera declarado la persona (ver [summaryDeclared]).
 * - Alturas y pesos con la unidad del borrador y los formateadores del diseño
 *   ([WizardHeightScale], [WizardWeightScale]); la grasa corporal reutiliza
 *   [bodyFatReviewValue], así que dice lo mismo que la revisión final.
 * - Los pasos de vista previa o resultado resumen lo que el estado ya calculó
 *   (nombre del plan, primera semana, referencias, RINGS) y, sin estado, dicen
 *   «Listo».
 */
internal data class SetupStepSummary(val label: String, val value: String)

/** Tope de caracteres de [SetupStepSummary.label]. */
internal const val SETUP_SUMMARY_LABEL_MAX = 24

/** Tope de caracteres de [SetupStepSummary.value]. */
internal const val SETUP_SUMMARY_VALUE_MAX = 44

/**
 * Resumen de una PÁGINA del wizard. NAME cubre alias+edad y HEIGHT cubre altura+peso
 * (`wizardPresentationSteps` en SetupWizardSteps.kt fusiona esos pares); AGE y WEIGHT
 * tienen su propio resumen por si la ruta no trae el paso que los fusiona.
 *
 * Existe para todos los [SetupStepId], también los legacy (identidad de género, material
 * en casa, sexo de cálculo, inicio de RINGS, inventarios): nunca lanza.
 */
internal fun setupStepSummary(page: SetupStepId, state: SetupWizardState): SetupStepSummary {
    val value = if (SetupStepGraph.isMilestone(page)) SUMMARY_COMPLETED else summaryValue(page, state)
    return SetupStepSummary(
        label = summaryFit(summaryLabel(page), SETUP_SUMMARY_LABEL_MAX),
        value = summaryFit(value, SETUP_SUMMARY_VALUE_MAX).ifEmpty { SUMMARY_NOT_ANSWERED },
    )
}

// ─── Textos fijos ────────────────────────────────────────────────────────────

private const val SUMMARY_COMPLETED = "Completado"
private const val SUMMARY_SKIPPED = "Omitido"
private const val SUMMARY_NOT_ANSWERED = "Sin responder"
private const val SUMMARY_READY = "Listo"
private const val SUMMARY_ACTIVATED = "Activado"
private const val SUMMARY_UNCALIBRATED = "Sin calibrar"
private const val SUMMARY_NO_PRIORITY = "Sin preferencia"
private const val SUMMARY_NO_MARKS = "Sin marcas"
private const val SUMMARY_NO_CAPABILITY = "Aún ninguno"
private const val SUMMARY_CHOSEN_PLAN = "Tu programa elegido"
private const val SUMMARY_PROFESSIONAL = "Pauta indicada por un profesional"
private const val SUMMARY_BLOCKED_EQUATION = "Faltan datos de la ecuación"

/** Nombres cortos de los días, de lunes (1) a domingo (7), para listar los días de entreno. */
private val SUMMARY_WEEKDAYS_SHORT = listOf("", "lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/** Tendencia declarada del peso (el valor estable que guarda el paso de historia). */
private val SUMMARY_TRENDS = mapOf(
    "rising" to "Subiendo",
    "stable" to "Estable",
    "falling" to "Bajando",
)

/** Máximo de etiquetas que se nombran en una respuesta múltiple; el resto va como « +N». */
private const val SUMMARY_MAX_LABELS = 2

private val SUMMARY_WHITESPACE = Regex("\\s+")

// ─── Etiquetas ───────────────────────────────────────────────────────────────

/**
 * Etiqueta corta de la página. Sin `else`: un paso nuevo del enum obliga a darle
 * etiqueta aquí en lugar de caer en un texto genérico.
 */
@Suppress("DEPRECATION")
private fun summaryLabel(page: SetupStepId): String = when (page) {
    SetupStepId.NAME -> "Alias y edad"
    SetupStepId.AGE -> "Edad"
    SetupStepId.HEIGHT -> "Altura y peso"
    SetupStepId.WEIGHT -> "Peso"
    SetupStepId.EQUATION_SEX -> "Género"
    SetupStepId.BODY_FAT -> "Grasa corporal"

    SetupStepId.EXPERIENCE -> "Experiencia"
    SetupStepId.EQUIPMENT -> "Dónde entrenas"
    SetupStepId.AVAILABILITY -> "Material"
    SetupStepId.GOAL -> "Objetivo"
    SetupStepId.FRESH_DAY -> "Día con más energía"
    SetupStepId.WEEKDAYS -> "Días de entreno"
    SetupStepId.SESSION_TIME -> "Tiempo por sesión"
    SetupStepId.CARDIO_TYPE -> "Tipo de cardio"
    SetupStepId.CARDIO_TIME -> "Minutos de cardio"
    SetupStepId.VOLUME_TECHNIQUE -> "Técnica"
    SetupStepId.VOLUME_CONSISTENCY -> "Constancia"
    SetupStepId.VOLUME_STRENGTH -> "Fuerza actual"
    SetupStepId.VOLUME_MOBILITY -> "Movilidad"
    SetupStepId.CAPABILITIES -> "Ejercicios que te salen"
    SetupStepId.PRIORITIES -> "Músculos a mejorar"
    SetupStepId.TRAINING_MAX -> "Marcas"
    SetupStepId.PLAN -> "Programa"
    SetupStepId.WEEK_LAYOUT -> "Tu semana"
    SetupStepId.INVENTORY_BARBELL -> "Barra y rack"
    SetupStepId.INVENTORY_PLATES -> "Discos"
    SetupStepId.INVENTORY_DUMBBELLS -> "Mancuernas"
    SetupStepId.INVENTORY_KETTLEBELLS -> "Kettlebells"
    SetupStepId.INVENTORY_MACHINES -> "Máquinas y poleas"
    // Pasos retirados de la ruta: solo se leen en borradores antiguos.
    SetupStepId.ROUTE -> "Cómo empezar"
    SetupStepId.STYLE -> "Prefieres ganar"
    SetupStepId.DAYS -> "Días por semana"
    SetupStepId.SPLIT -> "Reparto de entreno"
    SetupStepId.TRAINING_MARKS -> "Marcas guardadas"
    SetupStepId.AUTOREGULATION -> "Autorregulación"
    SetupStepId.AUTOREGULATION_CONFIRM -> "Ajuste automático"
    SetupStepId.WARMUPS -> "Calentamientos"
    SetupStepId.TRAINING_REVIEW -> "Revisión del programa"

    SetupStepId.NUTRITION_START -> "Modo de nutrición"
    SetupStepId.NUTRITION_ELIGIBILITY -> "Condiciones de salud"
    SetupStepId.NUTRITION_DIRECTION -> "Objetivo nutricional"
    SetupStepId.NUTRITION_RHYTHM -> "Ritmo de cambio"
    SetupStepId.NUTRITION_TARGET -> "Peso objetivo"
    SetupStepId.NUTRITION_HISTORY_CONTEXT -> "Evolución del peso"
    SetupStepId.NUTRITION_ACTIVITY -> "Actividad diaria"
    SetupStepId.NUTRITION_MANUAL_CALORIES -> "Calorías y proteína"
    SetupStepId.NUTRITION_MANUAL_CARBS_FAT -> "Hidratos y grasas"
    SetupStepId.NUTRITION_DISTRIBUTION -> "Reparto de calorías"
    SetupStepId.NUTRITION_WEIGH_INS -> "Pesajes"
    SetupStepId.NUTRITION_RESULT -> "Plan de alimentación"

    SetupStepId.RINGS_RECENT -> "Entreno reciente"
    SetupStepId.RINGS_SESSIONS -> "Sesiones en 7 días"
    SetupStepId.RINGS_RECENCY -> "Última sesión"
    SetupStepId.RINGS_ACTIVITY -> "Tipo de sesiones"
    SetupStepId.RINGS_INTENSITY -> "Intensidad"
    SetupStepId.RINGS_AXIAL -> "Cargas en la espalda"
    SetupStepId.RINGS_MUSCLE_FEELING -> "Sensación muscular"
    SetupStepId.RINGS_ENERGY_FEELING -> "Energía"
    SetupStepId.RINGS_STRUCTURE_FEELING -> "Columna"
    SetupStepId.RINGS_DISCOMFORT -> "Molestias"
    SetupStepId.RINGS_RESULT -> "Tus RINGS"

    SetupStepId.REVIEW_ACTIVATE -> "Revisión y activación"

    // Los hitos llevan el título del bloque que cierran.
    SetupStepId.MILESTONE_BASICS,
    SetupStepId.MILESTONE_TRAINING,
    SetupStepId.MILESTONE_NUTRITION,
    SetupStepId.MILESTONE_RINGS,
    -> SetupStepDefinitions.blockTitle(SetupStepGraph.blockOf(page))

    // Legacy: solo lectura de borradores antiguos, fuera de la ruta productiva.
    SetupStepId.GENDER -> "Identidad de género"
    SetupStepId.HOME_EQUIPMENT -> "Material en casa"
    SetupStepId.NUTRITION_SEX -> "Sexo de cálculo"
    SetupStepId.RINGS_START -> "Inicio de Rings"
}

// ─── Valores ─────────────────────────────────────────────────────────────────

/**
 * Valor de la página, sin recortar. Sin `else`, igual que [summaryLabel]. Los hitos
 * no llegan aquí ([setupStepSummary] los resuelve antes) pero el `when` los cubre.
 */
@Suppress("DEPRECATION")
private fun summaryValue(page: SetupStepId, state: SetupWizardState): String {
    val draft = state.draft
    return when (page) {
        // ── Datos básicos ────────────────────────────────────────────────────
        SetupStepId.NAME -> summaryAliasAndAge(draft)
        SetupStepId.AGE -> draft.ageYears?.let { years -> summaryQuantity(page, years) } ?: draft.summaryMissing(page)
        SetupStepId.HEIGHT -> summaryHeightAndWeight(draft) ?: draft.summaryMissing(page)
        SetupStepId.WEIGHT -> draft.weightKg?.takeIf { it.isFinite() }?.let { kg -> draft.summaryWeight(kg) }
            ?: draft.summaryMissing(page)
        SetupStepId.EQUATION_SEX -> draft.summaryChoice(page)
        SetupStepId.BODY_FAT -> bodyFatReviewValue(draft) ?: draft.summaryMissing(page)

        // ── Entreno ──────────────────────────────────────────────────────────
        SetupStepId.EXPERIENCE,
        SetupStepId.VOLUME_TECHNIQUE,
        SetupStepId.VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH,
        SetupStepId.VOLUME_MOBILITY,
        SetupStepId.CARDIO_TYPE,
        SetupStepId.CARDIO_TIME,
        -> draft.summaryChoice(page)
        SetupStepId.EQUIPMENT -> summaryPlaces(draft)
        // El material que un lugar siembra de entrada no es una respuesta hasta que se confirma el paso.
        SetupStepId.AVAILABILITY -> draft.summaryChoice(page, draft.summaryDeclared(page))
        // El perfil elegido (diez) manda; el objetivo legacy (Salud, Fuerza + cardio) usa la etiqueta del dominio.
        SetupStepId.GOAL -> draft.goalProfile?.label ?: draft.goal?.label ?: draft.summaryMissing(page)
        SetupStepId.FRESH_DAY -> draft.freshestDay?.let { day -> summaryWeekdayName(day) } ?: draft.summaryMissing(page)
        SetupStepId.WEEKDAYS -> summaryWeekdays(draft)
        SetupStepId.SESSION_TIME -> draft.minutesPerSession?.let { minutes -> summaryQuantity(page, minutes) }
            ?: draft.summaryMissing(page)
        SetupStepId.CAPABILITIES -> summaryCapabilities(draft)
        SetupStepId.PRIORITIES -> summaryPriorities(draft)
        SetupStepId.TRAINING_MAX -> summaryMarks(draft)
        SetupStepId.PLAN -> summaryPlan(state)
        SetupStepId.WEEK_LAYOUT -> summaryWeekLayout(state)
        // Pasos retirados de la ruta: solo se leen en borradores antiguos.
        SetupStepId.ROUTE,
        SetupStepId.STYLE,
        SetupStepId.DAYS,
        SetupStepId.SPLIT,
        SetupStepId.TRAINING_MARKS,
        SetupStepId.AUTOREGULATION,
        SetupStepId.AUTOREGULATION_CONFIRM,
        SetupStepId.WARMUPS,
        SetupStepId.TRAINING_REVIEW,
        -> draft.summaryChoice(page)

        // ── Nutrición ────────────────────────────────────────────────────────
        SetupStepId.NUTRITION_START -> summaryNutritionStart(draft)
        SetupStepId.NUTRITION_ELIGIBILITY, SetupStepId.NUTRITION_DIRECTION -> draft.summaryChoice(page)
        SetupStepId.NUTRITION_RHYTHM -> draft.summaryChoice(
            page,
            draft.summaryDeclared(page, typed = draft.nutritionDraft?.pacePreset?.name?.lowercase()),
        )
        SetupStepId.NUTRITION_TARGET -> summaryTargetWeight(draft)
        SetupStepId.NUTRITION_HISTORY_CONTEXT -> summaryWeightHistory(draft)
        SetupStepId.NUTRITION_ACTIVITY -> draft.summaryChoice(
            page,
            draft.summaryDeclared(page, typed = draft.nutritionDraft?.activity?.name),
        )
        SetupStepId.NUTRITION_MANUAL_CALORIES -> summaryManualCalories(draft)
        SetupStepId.NUTRITION_MANUAL_CARBS_FAT -> summaryManualCarbsAndFat(draft)
        SetupStepId.NUTRITION_DISTRIBUTION -> draft.summaryChoice(page, draft.summaryDeclared(page))
        SetupStepId.NUTRITION_WEIGH_INS -> draft.historicalWeighIns.size.takeIf { it > 0 }
            ?.let { count -> SpanishPlurals.withNoun(count, "pesaje", "pesajes") }
            ?: draft.summaryMissing(page)
        SetupStepId.NUTRITION_RESULT -> summaryNutritionResult(state)

        // ── RINGS ────────────────────────────────────────────────────────────
        SetupStepId.RINGS_RECENT,
        SetupStepId.RINGS_SESSIONS,
        SetupStepId.RINGS_RECENCY,
        SetupStepId.RINGS_ACTIVITY,
        SetupStepId.RINGS_INTENSITY,
        SetupStepId.RINGS_DISCOMFORT,
        -> draft.summaryChoice(page)
        SetupStepId.RINGS_AXIAL -> draft.summaryChoice(
            page,
            draft.selectedValues(page).ifEmpty { setOfNotNull(draft.summaryAxialAnswer()) },
        )
        SetupStepId.RINGS_MUSCLE_FEELING -> draft.summaryFeeling(page, draft.ringsAnswers?.muscleFeeling)
        SetupStepId.RINGS_ENERGY_FEELING -> draft.summaryFeeling(page, draft.ringsAnswers?.energy)
        SetupStepId.RINGS_STRUCTURE_FEELING -> draft.summaryFeeling(page, draft.ringsAnswers?.structureFeeling)
        SetupStepId.RINGS_RESULT -> summaryRingsResult(state)

        // ── Revisión y activación ────────────────────────────────────────────
        SetupStepId.REVIEW_ACTIVATE -> if (state.receiptId != null) SUMMARY_ACTIVATED else SUMMARY_READY

        SetupStepId.MILESTONE_BASICS,
        SetupStepId.MILESTONE_TRAINING,
        SetupStepId.MILESTONE_NUTRITION,
        SetupStepId.MILESTONE_RINGS,
        -> SUMMARY_COMPLETED

        // ── Legacy ───────────────────────────────────────────────────────────
        SetupStepId.GENDER, SetupStepId.HOME_EQUIPMENT, SetupStepId.NUTRITION_SEX -> draft.summaryChoice(page)
        SetupStepId.RINGS_START -> draft.ringsAnswers?.startAction?.takeIf { it.isNotBlank() }
            ?: draft.summaryMissing(page)
        SetupStepId.INVENTORY_BARBELL -> summaryInventory(draft, page) { it.barbellWeightKg != null }
        SetupStepId.INVENTORY_PLATES -> summaryInventory(draft, page) { it.plates.isNotEmpty() }
        SetupStepId.INVENTORY_DUMBBELLS -> summaryInventory(draft, page) { it.dumbbells.isNotEmpty() }
        SetupStepId.INVENTORY_KETTLEBELLS -> summaryInventory(draft, page) { it.kettlebells.isNotEmpty() }
        SetupStepId.INVENTORY_MACHINES -> summaryInventory(draft, page) { it.machines.isNotEmpty() }
    }
}

// ─── Datos básicos ───────────────────────────────────────────────────────────

/**
 * «Matías · 36 años»; con solo el alias, el alias; con solo la edad, la edad. Si el
 * alias no cabe se recorta el alias, nunca la edad.
 */
private fun summaryAliasAndAge(draft: SetupWizardDraft): String {
    val alias = draft.name.replace(SUMMARY_WHITESPACE, " ").trim()
    val age = draft.ageYears?.let { years -> summaryQuantity(SetupStepId.AGE, years) }
    return when {
        alias.isNotEmpty() && age != null -> {
            val suffix = " · $age"
            summaryFit(alias, SETUP_SUMMARY_VALUE_MAX - suffix.length) + suffix
        }
        alias.isNotEmpty() -> alias
        age != null -> age
        else -> draft.summaryMissing(SetupStepId.NAME)
    }
}

/** «172 cm · 70,1 kg» o «5′ 8″ · 154,5 lb» según las unidades del borrador; solo lo que exista. */
private fun summaryHeightAndWeight(draft: SetupWizardDraft): String? {
    val height = draft.heightCm?.takeIf { it.isFinite() }?.let { cm ->
        val unit = draft.heightUnit.toSummaryHeightUnit()
        val text = WizardHeightScale.format(WizardHeightScale.clampCm(cm), unit)
        if (unit == WizardHeightUnit.CM) "$text cm" else text
    }
    val weight = draft.weightKg?.takeIf { it.isFinite() }?.let { kg -> draft.summaryWeight(kg) }
    return listOfNotNull(height, weight).joinToString(" · ").ifEmpty { null }
}

/** Peso canónico (kg) en la unidad visible del borrador. */
private fun SetupWizardDraft.summaryWeight(kg: Double): String =
    WizardWeightScale.formatWithUnit(kg, if (weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG)

/**
 * `draft.heightUnit` es texto persistido (`cm`/`ft`) y la UI tolera estos alias
 * (misma lectura que `SetupBasicSteps`, donde es privada).
 */
private fun String.toSummaryHeightUnit(): WizardHeightUnit = when (lowercase()) {
    "ft", "ft_in", "ftin", "in", "imperial" -> WizardHeightUnit.FT_IN
    else -> WizardHeightUnit.CM
}

/** «36 años», «60 min»: el número con la unidad del catálogo. */
private fun summaryQuantity(step: SetupStepId, value: Int): String =
    SetupStepDefinitions.of(step)?.unit?.let { unit -> "$value $unit" } ?: value.toString()

// ─── Elecciones ──────────────────────────────────────────────────────────────

/**
 * Respuesta de un paso de opciones: las etiquetas del catálogo de lo elegido (hasta
 * [SUMMARY_MAX_LABELS] y « +N»). Por defecto lee la selección canónica del borrador
 * ([selectedValues]); sin ninguna, «Omitido» o «Sin responder».
 */
private fun SetupWizardDraft.summaryChoice(
    step: SetupStepId,
    values: Set<String> = selectedValues(step),
): String {
    if (values.isEmpty()) return summaryMissing(step)
    return summaryJoin(summaryLabels(step, values))
}

/**
 * Selección del paso SOLO si es una declaración: lo que la persona tocó
 * (`stepSelections`) o lo que un paso ya confirmado guarda en su campo tipado.
 *
 * Es para los pasos cuyo campo tipado nace con un valor por defecto (modo de nutrición,
 * ritmo, actividad, reparto) o sembrado por otra respuesta (material por entorno): sin
 * esta guarda, un paso sin responder diría «Calcula mis referencias» o «Medio» como si
 * lo hubiera elegido la persona. [typed] cubre los campos que [selectedValues] no proyecta.
 */
private fun SetupWizardDraft.summaryDeclared(step: SetupStepId, typed: String? = null): Set<String> {
    val stored = stepSelections[step].orEmpty()
    if (stored.isNotEmpty()) return stored.toSet()
    if (step !in stepProgress.answers) return emptySet()
    return selectedValues(step).ifEmpty { setOfNotNull(typed) }
}

/**
 * Sensación de RINGS. [selectedValues] convierte «paso omitido» en «No lo sé» (para pintar
 * la tarjeta); en el resumen se distingue: sin nivel, sin tarjeta tocada y con el paso ya
 * registrado es que se omitió.
 */
private fun SetupWizardDraft.summaryFeeling(step: SetupStepId, level: Int?): String =
    if (level == null && stepSelections[step].isNullOrEmpty() && step in stepProgress.answers) {
        SUMMARY_SKIPPED
    } else {
        summaryChoice(step)
    }

/** Respuesta de «¿cargas pesadas para la espalda?» desde el tipado, para borradores sin selección guardada. */
private fun SetupWizardDraft.summaryAxialAnswer(): String? {
    val axial = ringsAnswers?.axialExposure ?: return null
    if (axial.state != InitialRecoveryResponseState.DECLARED) return null
    return if (axial.sessions == 1) "yes" else "no"
}

/** Etiquetas del catálogo en el orden del catálogo; un valor que ya no existe se muestra legible, nunca crudo. */
private fun summaryLabels(step: SetupStepId, values: Set<String>): List<String> {
    val options = SetupStepDefinitions.options(step)
    val known = options.filter { option -> option.value in values }.map { option -> summaryOptionLabel(step, option.value) }
    val extras = values.filter { value -> options.none { option -> option.value == value } }
        .sorted()
        .map { value -> summaryOptionLabel(step, value) }
    return known + extras
}

/**
 * Valor corto de las tres respuestas hormonales del paso de género, para la fila-resumen y la revisión
 * (cabe de sobra en [SETUP_SUMMARY_VALUE_MAX]). Dicen el contexto que la persona contó, sin nombrar
 * una identidad. Null para cualquier otro valor: los glifos y «No lo sé» usan la etiqueta del catálogo.
 */
internal fun equationSexHormonalSummary(value: String): String? = when (value) {
    SetupEquationSexValues.HORMONES_ESTROGEN -> "Estrógenos predominantes"
    SetupEquationSexValues.HORMONES_ANDROGEN -> "Andrógenos predominantes"
    SetupEquationSexValues.HORMONES_MIXED -> "Equilibrio hormonal"
    else -> null
}

private fun summaryOptionLabel(step: SetupStepId, value: String): String {
    if (step == SetupStepId.RINGS_DISCOMFORT && value == "omit") return SUMMARY_SKIPPED
    if (step == SetupStepId.EQUATION_SEX) equationSexHormonalSummary(value)?.let { return it }
    val number = value.toIntOrNull()
    if (number != null) {
        when (step) {
            SetupStepId.RINGS_SESSIONS -> return SpanishPlurals.sessions(number)
            SetupStepId.DAYS -> return SpanishPlurals.days(number)
            SetupStepId.CARDIO_TIME -> return "$number min"
            else -> Unit
        }
    }
    SetupStepDefinitions.of(step)?.option(value)?.let { option -> return option.label }
    if (number != null && step == SetupStepId.RINGS_RECENCY) return "Hace $number días"
    return summaryHumanize(value)
}

/** «bike_stationary» → «Bike stationary»: último recurso para un valor que el catálogo no conoce. */
private fun summaryHumanize(value: String): String =
    value.trim().replace('_', ' ').lowercase().replaceFirstChar { char -> char.titlecase() }

/** Hasta [SUMMARY_MAX_LABELS] elementos separados por «, » y « +N» con el resto. */
private fun summaryJoin(items: List<String>): String {
    val shown = items.take(SUMMARY_MAX_LABELS).joinToString(", ")
    val rest = items.size - SUMMARY_MAX_LABELS
    return if (rest > 0) "$shown +$rest" else shown
}

/** «Omitido» si el paso admite omitirse y ya quedó registrado sin dato; «Sin responder» en otro caso. */
private fun SetupWizardDraft.summaryMissing(step: SetupStepId): String =
    if (SetupStepDefinitions.of(step)?.allowSkip == true && step in stepProgress.answers) {
        SUMMARY_SKIPPED
    } else {
        SUMMARY_NOT_ANSWERED
    }

// ─── Entreno ─────────────────────────────────────────────────────────────────

/**
 * «Gimnasio y casa», «Casa»: los lugares elegidos en el orden del contrato, el primero con mayúscula; null sin
 * lugares. Lo comparten la fila-resumen y la revisión final.
 */
internal fun placesSummaryText(places: Set<TrainingPlace>): String? {
    val ordered = TrainingPlace.entries.filter { it in places }
    if (ordered.isEmpty()) return null
    val names = ordered.map { place ->
        when (place) {
            TrainingPlace.GYM -> "gimnasio"
            TrainingPlace.HOME -> "casa"
            TrainingPlace.PUBLIC -> "espacios públicos"
        }
    }
    val joined = if (names.size == 1) names.first() else names.dropLast(1).joinToString(", ") + " y " + names.last()
    return joined.replaceFirstChar { it.titlecase() }
}

private fun summaryPlaces(draft: SetupWizardDraft): String =
    placesSummaryText(draft.trainingPlaces) ?: draft.summaryMissing(SetupStepId.EQUIPMENT)

/** Nombre completo del día (1 = lunes … 7 = domingo). */
private fun summaryWeekdayName(day: Int): String =
    SetupStepDefinitions.of(SetupStepId.WEEKDAYS)?.option(day.toString())?.label ?: "Día $day"

/** «3 días · lun, mié, vie»; con los siete, «Todos los días»; null sin días. Lo comparten la fila-resumen y la revisión. */
internal fun weekdaysSummaryText(selected: Set<Int>): String? {
    val days = selected.filter { it in 1..7 }.sorted()
    if (days.isEmpty()) return null
    if (days.size == 7) return "Todos los días"
    return SpanishPlurals.days(days.size) + " · " + days.joinToString(", ") { SUMMARY_WEEKDAYS_SHORT[it] }
}

private fun summaryWeekdays(draft: SetupWizardDraft): String =
    weekdaysSummaryText(draft.selectedWeekdays) ?: draft.summaryMissing(SetupStepId.WEEKDAYS)

/** Los ejercicios que ya salen («Dominadas, Flexiones +1»); si todos están en «Aún no», «Aún ninguno». */
private fun summaryCapabilities(draft: SetupWizardDraft): String {
    if (draft.capabilities.isEmpty()) return draft.summaryMissing(SetupStepId.CAPABILITIES)
    val known = draft.capabilities.filterValues { level -> level != CapabilityLevel.NONE }.keys
        .sortedBy { skill -> skill.ordinal }
        .map { skill -> skill.label }
    return if (known.isEmpty()) SUMMARY_NO_CAPABILITY else summaryJoin(known)
}

/** Los músculos que se quieren mejorar más («Pecho, Espalda +1»); la bolsa vacía confirmada es «Sin preferencia». */
private fun summaryPriorities(draft: SetupWizardDraft): String {
    val step = SetupStepId.PRIORITIES
    val bag = draft.trainingOptions.orderPriorities.filterValues { points -> points > 0 }
    if (bag.isEmpty()) {
        return if (step in draft.stepProgress.answers) SUMMARY_NO_PRIORITY else SUMMARY_NOT_ANSWERED
    }
    val muscles = bag.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { entry -> entry.value }.thenBy { entry -> entry.key })
        .map { entry -> MuscleSymbols.fromCanonical(entry.key)?.label ?: summaryOptionLabel(step, entry.key) }
    return summaryJoin(muscles)
}

/** El programa elegido por su nombre editorial (nunca el id); «más adelante» y «desde cero» se dicen tal cual. */
private fun summaryPlan(state: SetupWizardState): String {
    val draft = state.draft
    if (draft.programRoute == SetupProgramRoute.LATER) return DEFER_PROGRAM_REVIEW_VALUE
    val planId = draft.selectedCatalogId
    if (planId != null) {
        return planReviewValue(PersonalizedPlanCatalog.find(planId), state.programPreview?.name.orEmpty())
            ?: SUMMARY_CHOSEN_PLAN
    }
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return SetupTrainingPath.FROM_SCRATCH.label
    return draft.summaryMissing(SetupStepId.PLAN)
}

/** La semana armada: las sesiones de la primera semana del programa preparado; sin preparar, «Lista». */
private fun summaryWeekLayout(state: SetupWizardState): String {
    val program = state.programPreview ?: return SUMMARY_READY
    val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        .firstOrNull()?.sessions.orEmpty()
    if (sessions.isEmpty()) return SUMMARY_READY
    return SpanishPlurals.sessions(sessions.size) + " por semana"
}

/** «Sentadilla 100 kg, Press banca 80 kg +1»: solo las marcas declaradas, en la unidad en que se declararon. */
private fun summaryMarks(draft: SetupWizardDraft): String {
    val unit = if (draft.marksUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG
    val marks = draft.liftMarks.entries.sortedBy { entry -> entry.key.ordinal }
        .map { (lift, kg) -> "${lift.label} ${WizardWeightScale.formatWithUnit(kg, unit)}" }
    if (marks.isNotEmpty()) return summaryJoin(marks)
    return if (SetupStepId.TRAINING_MAX in draft.stepProgress.answers) SUMMARY_NO_MARKS else SUMMARY_NOT_ANSWERED
}

/** Material de un inventario legacy: solo dice si se declaró (los pasos INVENTORY_* ya no están en la ruta). */
private fun summaryInventory(
    draft: SetupWizardDraft,
    step: SetupStepId,
    declared: (com.example.kpkn.data.models.EquipmentInventory) -> Boolean,
): String {
    val inventory = draft.trainingOptions.inventory
    return if (inventory != null && declared(inventory)) "Declarado" else draft.summaryMissing(step)
}

// ─── Nutrición ───────────────────────────────────────────────────────────────

private fun summaryNutritionStart(draft: SetupWizardDraft): String {
    if (draft.stepContext().nutritionProfessional) return SUMMARY_PROFESSIONAL
    val step = SetupStepId.NUTRITION_START
    return draft.summaryChoice(step, draft.summaryDeclared(step))
}

/** El peso meta con su unidad: la que usa el motor para leerlo (la del borrador nutricional). */
private fun summaryTargetWeight(draft: SetupWizardDraft): String {
    val step = SetupStepId.NUTRITION_TARGET
    val nutrition = draft.nutritionDraft
    val raw = nutrition?.targetWeightText?.takeIf { it.isNotBlank() } ?: draft.inputTexts[step.name]
    val target = raw?.let { text -> parseLocalizedNumber(text) }?.takeIf { it > 0.0 }
        ?: return draft.summaryMissing(step)
    val unit = if ((nutrition?.weightUnit ?: draft.weightUnit) == "lb") WizardMassUnit.LB.code else WizardMassUnit.KG.code
    return "${WizardWeightScale.format(target)} $unit"
}

/** Tendencia y máximo anterior declarados («Estable · máximo 82 kg»); es contexto, no pesajes. */
private fun summaryWeightHistory(draft: SetupWizardDraft): String {
    val trend = draft.weightTrend?.takeIf { it.isNotBlank() }?.let { value -> SUMMARY_TRENDS[value] ?: summaryHumanize(value) }
    val maximum = draft.previousMaximumWeightKg?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { kg -> "máximo ${WizardWeightScale.formatWithUnit(kg, WizardMassUnit.KG)}" }
    return listOfNotNull(trend, maximum).joinToString(" · ").ifEmpty { draft.summaryMissing(SetupStepId.NUTRITION_HISTORY_CONTEXT) }
}

/** «2400 kcal · Proteína 160 g»: solo los valores que la persona escribió. */
private fun summaryManualCalories(draft: SetupWizardDraft): String {
    val nutrition = draft.nutritionDraft
    val calories = summaryMacro(nutrition?.manualCalorieTargetText)?.let { "$it kcal" }
    val protein = summaryMacro(nutrition?.manualProteinText)?.let { "Proteína $it g" }
    return listOfNotNull(calories, protein).joinToString(" · ")
        .ifEmpty { draft.summaryMissing(SetupStepId.NUTRITION_MANUAL_CALORIES) }
}

/** «Hidratos 250 g · Grasas 70 g»: solo los valores que la persona escribió. */
private fun summaryManualCarbsAndFat(draft: SetupWizardDraft): String {
    val nutrition = draft.nutritionDraft
    val carbs = summaryMacro(nutrition?.manualCarbsText)?.let { "Hidratos $it g" }
    val fat = summaryMacro(nutrition?.manualFatText)?.let { "Grasas $it g" }
    return listOfNotNull(carbs, fat).joinToString(" · ")
        .ifEmpty { draft.summaryMissing(SetupStepId.NUTRITION_MANUAL_CARBS_FAT) }
}

/** Número escrito (con coma o punto) listo para mostrar, o null si está vacío o no es un número válido. */
private fun summaryMacro(raw: String?): String? =
    raw?.takeIf { it.isNotBlank() }?.let { text -> parseLocalizedNumber(text) }
        ?.takeIf { it >= 0.0 }
        ?.let { value -> WizardWeightScale.format(value) }

/**
 * Lo que la preparación ya calculó, como base diaria media (nunca «hoy»): las kcal y los macros del
 * plan en una línea corta («2.300 kcal · P 160 · H 250 · G 70»); en solo registro o con pauta profesional,
 * el modo. Sin plan calculado, «Listo»; con la ecuación bloqueada, lo dice.
 */
private fun summaryNutritionResult(state: SetupWizardState): String {
    val context = state.draft.stepContext()
    val preparation = state.nutritionPreparation
    val plan = preparation?.plan ?: state.nutritionPlanPreview
    return when {
        context.nutritionStartChoice == "tracking_only" ->
            summaryOptionLabel(SetupStepId.NUTRITION_START, "tracking_only")
        context.nutritionProfessional -> SUMMARY_PROFESSIONAL
        plan != null && plan.calorieTarget > 0 -> summaryPlanLine(plan)
        preparation?.status == NutritionPlanPreparationStatus.BLOCKED_EQUATION -> SUMMARY_BLOCKED_EQUATION
        else -> SUMMARY_READY
    }
}

/** «2.300 kcal · P 160 · H 250 · G 70»; un plan sin desglose de macros dice solo las kcal. */
private fun summaryPlanLine(plan: NutritionPlan): String {
    val kcal = "${formatKcalEs(plan.calorieTarget)} kcal"
    if (plan.proteinGoal <= 0 && plan.carbGoal <= 0 && plan.fatGoal <= 0) return kcal
    return "$kcal · P ${plan.proteinGoal} · H ${plan.carbGoal} · G ${plan.fatGoal}"
}

// ─── RINGS ───────────────────────────────────────────────────────────────────

/**
 * Cobertura por canal que publicó la vista previa: solo los canales con dato («Músculos 82 · Energía 75»).
 * Con la vista previa sin dato en ningún canal, «Sin calibrar» (no afirma un porcentaje); sin vista previa, «Listo».
 */
private fun summaryRingsResult(state: SetupWizardState): String {
    val coverage = state.ringsCoveragePreview ?: return SUMMARY_READY
    if (!coverage.hasAnyData) return SUMMARY_UNCALIBRATED
    return listOfNotNull(
        coverage.muscular.score?.let { score -> "Músculos $score" },
        coverage.system.score?.let { score -> "Energía $score" },
        coverage.structure.score?.let { score -> "Columna $score" },
    ).joinToString(" · ")
}

// ─── Una línea ───────────────────────────────────────────────────────────────

/**
 * Una sola línea de como máximo [max] caracteres: colapsa espacios y saltos y, si no cabe,
 * recorta y termina en «…». No parte un par suplente (emoji) por la mitad.
 */
private fun summaryFit(text: String, max: Int): String {
    val line = text.replace(SUMMARY_WHITESPACE, " ").trim()
    if (line.length <= max) return line
    var end = (max - 1).coerceAtLeast(0)
    if (end > 0 && line[end - 1].isHighSurrogate()) end -= 1
    return line.take(end).trimEnd() + "…"
}
