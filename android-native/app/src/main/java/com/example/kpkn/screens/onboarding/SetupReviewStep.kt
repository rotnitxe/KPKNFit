package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.HighVolumeNotice
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.VolumeSoftBand
import com.example.kpkn.screens.onboarding.design.WizardGenderMark
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.entreno.toCardModel

/**
 * Revisión y activación (paso REVIEW_ACTIVATE). La página de debajo queda vacía: lo que se ve es el overlay de activación
 * (`ActivationOverlay`, en `design/`), que el anfitrión pinta con el cursor en este paso. Este archivo guarda las lecturas
 * puras del resumen: sin Compose, con pruebas.
 *
 * Reglas de las lecturas:
 * - Los datos se leen del borrador y de las vistas previas. Nada se rellena: lo que no se sabe sale como «Sin declarar» o
 *   «Sin calibrar», nunca como un 100 % derivado.
 * - Entreno: la competición se lee SOLO con `activeCompetition()`, `activeMeetDate()` y `activeMeetName()`.
 * - Nutrición: las kcal son la base diaria (media), nunca el objetivo de hoy; la fecha del objetivo sale de
 *   [nutritionGoalNoteOf], sin calcularla de nuevo.
 * - RINGS: la torre usa la cobertura de la vista previa ([ringsTowerValues]): los mismos valores que «Tus RINGS».
 * - El CTA de activación sigue siendo del Host; esta pantalla no confirma ni avanza.
 */
@Composable
fun SetupReviewStep(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    // Vacía a propósito: el overlay de activación cubre la página.
}

// ─── Lecturas del estado (puras) ─────────────────────────────────────────────

/**
 * El bloque → su primer paso de edición en la ruta (sin hitos). Es el destino de «Editar» de su momento. Función pura para
 * probarla sin montar el ViewModel.
 */
internal fun reviewBlockTargets(route: List<SetupStepId>): Map<SetupWizardBlock, SetupStepId> {
    val targets = LinkedHashMap<SetupWizardBlock, SetupStepId>()
    for (step in route) {
        if (SetupStepGraph.isMilestone(step)) continue
        val block = SetupStepGraph.blockOf(step)
        if (!targets.containsKey(block)) targets[block] = step
    }
    return targets
}

/** El símbolo del sexo elegido: el mismo que dibuja su paso. Null si la respuesta no tiene símbolo («No lo sé», hormonal). */
internal fun reviewGenderMarkOf(draft: SetupWizardDraft): WizardGenderMark? =
    when (draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull()) {
        "female" -> WizardGenderMark.FEMALE
        "male" -> WizardGenderMark.MALE
        "trans_male" -> WizardGenderMark.TRANS_MALE
        "trans_female" -> WizardGenderMark.TRANS_FEMALE
        null -> when (draft.nutritionDraft?.equationSex) {
            EerSex.FEMALE -> WizardGenderMark.FEMALE
            EerSex.MALE -> WizardGenderMark.MALE
            else -> null
        }
        else -> null
    }

/** La respuesta del sexo en palabras para la revisión (la misma que enseñaba su fila). */
internal fun reviewSexLabelOf(draft: SetupWizardDraft): String? =
    draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull()?.let { value ->
        when (value) {
            "female" -> "Mujer"
            "male" -> "Hombre"
            "trans_male" -> "Hombre trans"
            "trans_female" -> "Mujer trans"
            "unknown" -> "No lo sé"
            // Las tres respuestas hormonales dicen lo mismo que la fila-resumen del paso.
            else -> equationSexHormonalSummary(value) ?: value
        }
    } ?: when (draft.nutritionDraft?.equationSex) {
        EerSex.FEMALE -> "Femenino"
        EerSex.MALE -> "Masculino"
        EerSex.AVERAGE -> "Promedio"
        null -> null
    }

/** El porcentaje de grasa que se enseña en la línea de medidas: solo si hay un número con origen; «No lo sé» no lo tiene. */
internal fun reviewBodyFatPercentOf(draft: SetupWizardDraft): Double? = when (draft.bodyFatSource) {
    SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE -> draft.bodyFatPercent
    SetupBodyFatSource.UNKNOWN -> null
    null -> if (draft.bodyFatState() == SetupBodyFatState.ON_FILE) {
        draft.bodyFatPercent ?: draft.importedBodyFatPercent
    } else {
        null
    }
}

/** «176 cm · 78 kg · 18 % de grasa»: lo que se midió, en una línea; lo que falte, fuera. Null si no hay nada. */
internal fun reviewBodyLine(draft: SetupWizardDraft): String? = listOfNotNull(
    draft.heightCm?.let { "${it.toInt()} cm" },
    draft.weightKg?.let { WizardWeightScale.formatWithUnit(it, wizardMassUnitOf(draft)) },
    reviewBodyFatPercentOf(draft)?.let { "${formatPercent(it)} % de grasa" },
).joinToString(" · ").ifEmpty { null }

/**
 * «4 días × 60 min · tu sesión principal el jueves». Null sin sesiones. [minutes] y [mainDay] son opcionales: sin ellos la línea
 * dice solo los días o los días y el tiempo.
 */
internal fun reviewTrainingLine(days: Int, minutes: Int?, mainDay: Int?): String? {
    if (days <= 0) return null
    val head = listOfNotNull(SpanishPlurals.days(days), minutes?.takeIf { it > 0 }?.let { "$it min" }).joinToString(" × ")
    val main = mainDay?.let { WEEKDAY_LABELS.getOrNull(it) }?.takeIf { it.isNotEmpty() }?.lowercase()
    return if (main == null) head else "$head · tu sesión principal el $main"
}

/**
 * La competición que cuenta para el programa: «Compites el 14 de marzo de 2027 · Nacional Open»; sin nombre, sin la parte final.
 * «Sí, sin fecha» dice «Compites, aún sin fecha». Null si no compite (o el paso no está en la ruta).
 */
internal fun reviewCompetitionLine(draft: SetupWizardDraft): String? = when (draft.activeCompetition()) {
    CompetitionAnswer.WITH_DATE -> {
        val date = CompetitionRules.parseMeetDate(draft.activeMeetDate())
        if (date == null) {
            "Compites, aún sin fecha"
        } else {
            val name = draft.activeMeetName().trim()
            "Compites el ${CompetitionRules.dateLabel(date)}" + if (name.isEmpty()) "" else " · $name"
        }
    }
    CompetitionAnswer.NO_DATE -> "Compites, aún sin fecha"
    CompetitionAnswer.NO, null -> null
}

/** Lugares y material en una línea («Gimnasio y casa · Barra y discos, Rack +12»); null mientras no se hayan declarado. */
internal fun reviewPlacesMaterialLine(draft: SetupWizardDraft): String? =
    listOfNotNull(placesSummaryText(draft.trainingPlaces), materialReviewValue(draft)).joinToString(" · ").ifEmpty { null }

/** Una línea de la torre: «Músculos 64 %», o «Columna sin calibrar» si el canal no tiene valor. Nunca un 100 % de relleno. */
internal fun ringLineText(name: String, percent: Int?): String =
    if (percent == null) "$name sin calibrar" else "$name $percent %"

/**
 * La portada del programa: la misma ficha que enseña PLAN (`planReveals`). Sin ella (la lista no llegó a esta sesión), una mínima
 * con el nombre y los días de la semana tipo. Null sin programa previsualizado.
 */
internal fun reviewPlanCard(state: SetupWizardState, planEntry: CatalogEntry?): PlanCardModel? {
    val draft = state.draft
    state.planReveals.firstOrNull { it.planId == draft.selectedCatalogId }?.let { return it.toCardModel() }
    val program = state.programPreview ?: return null
    val days = SetupPlanReveals.weekOf(program).size
    return PlanCardModel(
        id = draft.selectedCatalogId ?: program.name,
        title = planReviewValue(planEntry, program.name) ?: program.name,
        kicker = "",
        blurb = "",
        profile = draft.goalProfile,
        daysLabel = if (days > 0) SpanishPlurals.days(days) else "",
        minutesLabel = "",
        levelLabel = "",
    )
}

// ─── Ayudantes puros del archivo (con pruebas) ───────────────────────────────

/**
 * Copy de la confirmación según el alcance real: solo registro no promete plan.
 */
internal fun activationBody(state: SetupWizardState): String {
    val later = state.draft.programRoute == SetupProgramRoute.LATER
    val trackingOnly = state.draft.stepContext().nutritionStartChoice == "tracking_only"
    return when {
        later && !state.draft.includeNutrition ->
            "No se activa ningún programa. Podrás armar el tuyo más adelante."
        later && trackingOnly ->
            "No se activa ningún programa. Podrás armar el tuyo más adelante. En solo registro no hay plan nutricional que activar."
        later ->
            "El programa lo armarás más adelante. El plan nutricional se activa al confirmar."
        !state.draft.includeNutrition -> "El programa se activa al confirmar."
        trackingOnly ->
            "El programa se activa al confirmar. En solo registro no hay plan nutricional que activar."
        else -> "El programa y el plan nutricional se activan juntos al confirmar."
    }
}

/**
 * Valor de la fila «Grasa corporal». Si quien vuelve no actuó en el paso, se
 * muestra el dato previo de Ajustes de forma honesta («guardada en Ajustes»),
 * sin presentarlo como una respuesta nueva; sin nada: null («Sin declarar»).
 */
internal fun bodyFatReviewValue(draft: SetupWizardDraft): String? = when (val source = draft.bodyFatSource) {
    SetupBodyFatSource.UNKNOWN -> "No lo sé"
    SetupBodyFatSource.MEASURED, SetupBodyFatSource.VISUAL_ESTIMATE ->
        draft.bodyFatPercent?.let { percent ->
            "${formatPercent(percent)} % · " +
                if (source == SetupBodyFatSource.MEASURED) "medido" else "estimación visual"
        }
    null -> if (draft.bodyFatState() == SetupBodyFatState.ON_FILE) {
        (draft.bodyFatPercent ?: draft.importedBodyFatPercent)
            ?.let { "Guardada en Ajustes: ${formatPercent(it)} %" }
    } else {
        null
    }
}

/**
 * B-03 · Aviso de volumen alto: «Volumen alto en Glúteos: 17 series por semana
 * (recomendado 16). Es un exceso pequeño y aceptable.» Solo existe si el plan
 * propio supera el máximo recomendado dentro de la tolerancia aprobada.
 */
internal fun highVolumeReviewText(notice: HighVolumeNotice): String =
    "Volumen alto en ${notice.muscle}: ${VolumeSoftBand.formatSets(notice.weeklySets)} series principales por semana " +
        "(recomendado ${notice.recommendedSets}). Es un exceso pequeño y aceptable."

/**
 * B-03 · Lo que la revisión cuenta del plan: primero los avisos de volumen alto
 * (`report.highVolume`, el máximo semanal) y luego las notas del plan en lenguaje
 * llano. Sin informe (p. ej. PHUL/PHAT, que no lo traen) no se muestra nada.
 */
internal fun planReviewNotes(report: PersonalizationReport?): List<String> =
    report?.let { it.highVolume.map(::highVolumeReviewText) + it.planNotes }.orEmpty()

/** «1 sesión · 3 ejercicios» de la vista previa de la primera semana. */
internal fun previewSessionsSummary(sessions: Int, exercises: Int): String =
    "${SpanishPlurals.sessions(sessions)} · ${SpanishPlurals.exercises(exercises)}"

/** «6 sesiones en 4 semanas» del desplegable semana a semana. */
internal fun allSessionsSummary(sessions: Int, weeks: Int): String =
    "${SpanishPlurals.sessions(sessions)} en ${SpanishPlurals.weeks(weeks)}"

/**
 * C.P7 · Valor de la fila «Plan»: el título editorial del plan elegido del catálogo
 * (sin «Plan de …» ni ids). Sin plan del catálogo (programa desde cero, id que ya no
 * existe) conserva el nombre del programa de la vista previa; sin nombre, null
 * («Sin declarar»).
 */
internal fun planReviewValue(entry: CatalogEntry?, programName: String): String? =
    entry?.displayName ?: programName.ifBlank { null }

/**
 * Entreno v2 · el programa que se activa en una línea: «4 días · ~60 min por sesión · Gimnasio y En casa». Los días salen
 * de la primera semana de entreno del programa previsualizado; los minutos, de su sesión más larga con el estimador común
 * ([longestSessionMinutes]: el mismo número con el que la revisión decide si cabe); el lugar, del de cada sesión
 * (`Session.placeId`) o, si el programa no lo dice, de los lugares declarados. Null sin sesiones.
 */
internal fun programReviewDetail(program: Program, draft: SetupWizardDraft): String? {
    val week = SetupPlanReveals.weekOf(program)
    if (week.isEmpty()) return null
    val places = week.mapNotNull { it.place }.distinct().ifEmpty { draft.trainingPlaces.toList() }
        .sortedBy { it.ordinal }
        .map { it.label }
    return listOfNotNull(
        SpanishPlurals.days(week.size),
        longestSessionMinutes(program)?.takeIf { it > 0 }?.let { "~$it min por sesión" },
        places.takeIf { it.isNotEmpty() }?.let { names ->
            if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " y " + names.last()
        },
    ).joinToString(" · ")
}

/**
 * El material en una línea para la revisión: «Solo peso corporal» o los tres primeros implementos y cuántos más
 * («Barra y discos, Rack, Banco +12»); null mientras no se haya declarado.
 */
internal fun materialReviewValue(draft: SetupWizardDraft): String? {
    if (draft.trainingOptions.availability == null) return null
    val symbols = draft.selectedEquipmentSymbols()
    val implements = symbols.filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }.sortedBy { it.ordinal }
    if (implements.isEmpty()) return EquipmentSymbolId.BODYWEIGHT_ONLY.label
    val shown = implements.take(MATERIAL_REVIEW_NAMES).joinToString(", ") { it.label }
    val rest = implements.size - MATERIAL_REVIEW_NAMES
    return if (rest > 0) "$shown +$rest" else shown
}

private const val MATERIAL_REVIEW_NAMES = 3

/** «3 días · lun, mié, vie · 60 min»: los días elegidos y el tiempo por sesión; null mientras falten los dos. */
internal fun daysAndTimeReviewValue(draft: SetupWizardDraft): String? =
    listOfNotNull(
        weekdaysSummaryText(draft.selectedWeekdays),
        draft.minutesPerSession?.let { minutes -> "$minutes min" },
    ).joinToString(" · ").ifEmpty { null }

/** Nombres de los días (1 = lunes … 7 = domingo; el 0 no se usa). Lo usan la revisión y la tira de días del overlay. */
internal val WEEKDAY_LABELS = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/** Un porcentaje para leer: entero sin decimales, o con una cifra y coma («22,5»). */
internal fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value).replace('.', ',')
