package com.example.kpkn.screens.programs

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.calculations.estimateSessionDurationMinutes
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.SetupPlanReveals
import com.example.kpkn.screens.onboarding.design.entreno.plan.MAX_MAIN_EXERCISES
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaContent
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaCopy
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaExercise
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaLine
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaSession
import com.example.kpkn.screens.onboarding.design.entreno.plan.approxMinutesLabel
import com.example.kpkn.screens.onboarding.design.entreno.plan.kpknImageOfConfiguration
import com.example.kpkn.screens.onboarding.design.entreno.plan.planFichaWeek
import kotlin.math.roundToInt

/**
 * La ficha de un plan del catálogo (escenas p-catalog y p-catalog-week del lienzo Arreglos v1): de [PlanInfoModel] (lo que contaba
 * la hoja de texto «Cómo funciona») y de la receta de la entrada, a la hoja del asistente. Función pura: los nombres de ejercicio y
 * la imagen de KPKN llegan por funciones.
 *
 * - Portada: «Plan del catálogo», el nombre, «5 días · ~75 min · Avanzado» y el color del objetivo de la entrada.
 * - Descripción = «Qué haces»; chips = enfoque, tipo de plan, material y alcance.
 * - «Ejercicios principales» (máx. 6) con la imagen de KPKN o sin miniatura; «Estructura» de las semanas de la receta.
 * - «Tu semana» de lunes a domingo con la semana 1 de la receta: cada día se despliega con «3 × 3–5 · RIR 2».
 * - «Ten en cuenta» = notas del método; la atribución = la fuente (o la procedencia si no hay fuente) y su edición.
 * - Sin «Por qué este programa»: el catálogo no trae razones y no se inventan.
 */
internal object PlanInfoFicha {

    private const val NAME_SEPARATOR = " · "
    private const val TIMES = "×"
    private const val EN_DASH = "–"

    private val weekdayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    /**
     * @param names nombre visible de un ejercicio por su configuración (solo si la hoja no trae ya el nombre del día).
     * @param imageOf la imagen de KPKN de una configuración; null = esa fila va sin miniatura.
     * @param primaryLabel el botón del pie fijo; null = ficha de solo lectura, sin pie.
     */
    fun build(
        entry: CatalogEntry,
        info: PlanInfoModel,
        names: (String) -> String?,
        imageOf: (String) -> Int? = ::kpknImageOfConfiguration,
        primaryLabel: String? = info.primaryActionLabel,
        secondaryLabel: String? = null,
    ): PlanFicha {
        val recipe = entry.recipe ?: entry.template?.recipe
        val recipeDays = recipe?.let { recipeDaysOf(it, info, names) }.orEmpty()
        val sessionsByDay: Map<Int, List<PlanFichaSession>> = if (recipe != null) {
            recipeDays.groupBy({ it.weekday }) { it.session }
        } else {
            readyDaysOf(info.typicalWeek)
        }
        val days = if (sessionsByDay.isEmpty()) emptyList() else planFichaWeek(sessionsByDay)
        val trainingDays = days.count { !it.rest }
        val longest = recipeDays.maxOfOrNull { it.session.minutes }?.takeIf { it > 0 }
        val card = PlanCardModel(
            id = entry.id,
            title = info.displayName,
            kicker = PlanFichaCopy.EYEBROW_CATALOG,
            blurb = info.summary,
            profile = goalProfileOf(entry),
            daysLabel = if (trainingDays > 0) SpanishPlurals.days(trainingDays) else daysRangeLabel(entry.supportedFrequencies),
            minutesLabel = longest?.let(::approxMinutesLabel).orEmpty(),
            levelLabel = info.levelLabel,
            coverSeed = Math.floorMod(entry.id.hashCode(), 9973),
        )
        val detail = PlanDetailModel(
            card = card,
            description = info.summary,
            mainExercises = emptyList(),
            blocks = blocksOf(entry, recipe),
            week = emptyList(),
            reasons = emptyList(),
            notes = info.notes,
            attribution = attributionOf(info),
            sourceUrl = info.source?.url?.takeIf { it.isNotBlank() },
        )
        val content = PlanFichaContent(
            chips = listOfNotNull(goalLabelOf(entry), info.kindLabel, materialChip(info.material), info.scopeLabel.takeIf { it.isNotBlank() }),
            exercises = mainExercisesOf(recipeDays, imageOf),
            days = days,
            openDay = days.firstOrNull { !it.rest }?.day,
            weekNote = (info.typicalWeek as? TypicalWeek.Generated)?.text,
            primaryLabel = primaryLabel,
            secondaryLabel = secondaryLabel,
        )
        return PlanFicha(detail, content)
    }

    // ─── La semana 1 de la receta ────────────────────────────────────────────

    private class NamedSlot(val slot: SlotRecipe, val name: String, val prescription: String)

    private class RecipeDay(val weekday: Int, val order: Int, val session: PlanFichaSession, val slots: List<NamedSlot>)

    private fun workingSlots(day: DayRecipe): List<SlotRecipe> = day.slots.filter { slot -> slot.sets.any { !it.isWarmup } }

    /**
     * Los días de la semana 1 que enseñan algo (los mismos que la hoja de texto, en su orden), cada uno en su día de la semana: el de
     * la receta o, si no lo declara, el que le da el materializador con inicio en lunes (el primero, lunes; el segundo, martes…).
     * Los nombres salen de la hoja de texto cuando la cuadra (ya distinguen dos ejercicios del mismo nombre por su material).
     */
    private fun recipeDaysOf(recipe: TrainingPlanRecipe, info: PlanInfoModel, names: (String) -> String?): List<RecipeDay> {
        val week = recipe.weeks.firstOrNull { it.weekNumber == 1 } ?: recipe.weeks.firstOrNull() ?: return emptyList()
        val shown = week.days.withIndex().filter { (_, day) -> workingSlots(day).isNotEmpty() || day.cardioBlocks.isNotEmpty() }
        val sheetDays = (info.typicalWeek as? TypicalWeek.Days)?.days?.takeIf { it.size == shown.size }
        return shown.mapIndexed { position, (index, day) ->
            val sheetDay = sheetDays?.get(position)
            val slots = workingSlots(day)
            val before = day.cardioBlocks.filter { it.position == RecipeCardioPosition.BEFORE_STRENGTH }
            val after = day.cardioBlocks.filter { it.position != RecipeCardioPosition.BEFORE_STRENGTH }
            val sheetNames = sheetDay?.lines
                ?.takeIf { it.size == before.size + slots.size + after.size }
                ?.drop(before.size)
                ?.take(slots.size)
                ?.map { line -> line.substringBefore(NAME_SEPARATOR).trim() }
            val named = slots.mapIndexed { i, slot ->
                val name = sheetNames?.getOrNull(i)?.takeIf { it.isNotEmpty() }
                    ?: names(slot.lift.configurationId)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: PlanInfoModelBuilder.UNKNOWN_EXERCISE
                NamedSlot(slot, name, shortPrescription(slot.sets, slot.authoredSetRange))
            }
            val rests = slots.flatMap { slot -> slot.sets.filterNot { it.isWarmup }.map { slot.restSeconds } }
            val strengthMinutes = if (rests.isEmpty()) 0 else estimateSessionDurationMinutes(rests.size, rests.average().roundToInt())
            val cardioMinutes = day.cardioBlocks.sumOf { (it.details.effectiveDurationSeconds() + 59) / 60 }
            RecipeDay(
                weekday = day.weekday?.coerceIn(1, 7) ?: (index % 7 + 1),
                order = position,
                session = PlanFichaSession(
                    title = sheetDay?.label ?: day.label.ifBlank { "Día ${index + 1}" },
                    lines = before.map(::cardioLine) + named.map { PlanFichaLine(it.name, it.prescription) } + after.map(::cardioLine),
                    minutes = strengthMinutes + cardioMinutes,
                    glyphOf = named.firstOrNull()?.name,
                ),
                slots = named,
            )
        }
    }

    private fun cardioLine(block: RecipeCardioBlock): PlanFichaLine {
        val minutes = (block.details.effectiveDurationSeconds() + 59) / 60
        return PlanFichaLine("Cardio: ${CardioPrescriptionFormatter.typeLabel(block.details.type)}", if (minutes > 0) "$minutes min" else "")
    }

    /**
     * La semana real de un candidato listo (planes sin receta): cada día de la hoja en su día de la semana si su rótulo empieza por él
     * («Lunes · Torso»); si no, en orden desde el lunes. Cada línea «Nombre · series» se parte en nombre y prescripción.
     */
    private fun readyDaysOf(week: TypicalWeek): Map<Int, List<PlanFichaSession>> {
        val days = (week as? TypicalWeek.Days)?.days ?: return emptyMap()
        return days.mapIndexed { position, day ->
            val weekday = weekdayNames.indexOfFirst { day.label.startsWith(it) }.takeIf { it >= 0 }?.plus(1)
            val title = if (weekday != null) {
                day.label.removePrefix(weekdayNames[weekday - 1]).removePrefix(NAME_SEPARATOR).trim().ifEmpty { day.label }
            } else {
                day.label
            }
            val lines = day.lines.map { line ->
                PlanFichaLine(line.substringBefore(NAME_SEPARATOR).trim(), line.substringAfter(NAME_SEPARATOR, "").trim())
            }
            (weekday ?: (position % 7 + 1)) to PlanFichaSession(title, lines, minutes = 0, glyphOf = lines.firstOrNull()?.name)
        }.groupBy({ it.first }) { it.second }
    }

    /**
     * Los ejercicios principales: primero los principales de la receta (T1), luego los complementarios y el resto, cada grupo en el
     * orden de la semana; sin repetir configuración y como mucho seis. La línea pequeña dice el día y la prescripción.
     */
    private fun mainExercisesOf(days: List<RecipeDay>, imageOf: (String) -> Int?): List<PlanFichaExercise> =
        days.flatMap { day -> day.slots.map { day to it } }
            .sortedWith(compareBy({ it.second.slot.role.ordinal }, { it.first.order }))
            .distinctBy { it.second.slot.lift.configurationId.trim().lowercase() }
            .take(MAX_MAIN_EXERCISES)
            .map { (day, named) ->
                PlanFichaExercise(
                    name = named.name,
                    detail = listOf(day.session.title, named.prescription).filter { it.isNotBlank() }.joinToString(NAME_SEPARATOR),
                    imageResId = imageOf(named.slot.lift.configurationId),
                )
            }

    // ─── Prescripción corta: «3 × 3–5 · RIR 2» ───────────────────────────────

    /**
     * Las series de trabajo en corto: series × repeticiones (agrupando las iguales y consecutivas; con el rango del autor si todas
     * son iguales) y, si todas lo comparten, el esfuerzo («RIR 2», «RPE 8») o el porcentaje («75 %»). Vacío sin series de trabajo.
     */
    fun shortPrescription(sets: List<SetRecipe>, authoredRange: AuthoredSetRange? = null): String {
        val working = sets.filterNot { it.isWarmup }
        if (working.isEmpty()) return ""
        val groups = ArrayList<Pair<String?, Int>>()
        for (set in working) {
            val reps = repsText(set)
            val last = groups.lastOrNull()
            if (last != null && last.first == reps) groups[groups.lastIndex] = reps to (last.second + 1) else groups.add(reps to 1)
        }
        val counts = groups.joinToString(", ") { (reps, count) ->
            val countText = if (groups.size == 1 && authoredRange != null) rangeText(authoredRange) else count.toString()
            when {
                reps != null -> "$countText $TIMES $reps"
                countText == "1" -> "1 serie"
                else -> "$countText series"
            }
        }
        val effort = sharedEffort(working)
        return if (effort != null) counts + NAME_SEPARATOR + effort else counts
    }

    private fun repsText(set: SetRecipe): String? {
        val low = set.repsMin ?: set.reps
        val high = set.repsMax ?: set.reps
        val text = when {
            low != null && high != null -> if (high > low) "$low$EN_DASH$high" else "$low"
            low != null -> "$low+"
            high != null -> "$high"
            else -> null
        }
        return if (set.amrap && text != null && !text.endsWith("+")) "$text+" else text
    }

    private fun rangeText(range: AuthoredSetRange): String = if (range.max > range.min) "${range.min}$EN_DASH${range.max}" else "${range.min}"

    private fun sharedEffort(sets: List<SetRecipe>): String? {
        sets.map { it.rir }.distinct().singleOrNull()?.let { return "RIR $it" }
        sets.map { it.rpe }.distinct().singleOrNull()?.let { return "RPE ${PlanInfoModelBuilder.formatNumber(it)}" }
        sets.map { it.percent }.distinct().singleOrNull()?.let { return "${PlanInfoModelBuilder.formatNumber(it)} %" }
        return null
    }

    // ─── Estructura ──────────────────────────────────────────────────────────

    /**
     * La estructura de la receta: un tramo por objetivo de bloque (o «Descarga») con sus semanas; con varios bloques, el nombre del
     * bloque como frase. Una receta de una semana es «Semana que se repite» (también un plan sin receta de semana que se repite).
     */
    private fun blocksOf(entry: CatalogEntry, recipe: TrainingPlanRecipe?): List<PlanBlockModel> {
        val repeating = PlanBlockModel(SetupPlanReveals.REPEATING_WEEK_LABEL, SetupPlanReveals.REPEATING_WEEK_SPAN, "")
        if (recipe == null) return if (entry.duration == CatalogDuration.REPEATING_WEEK) listOf(repeating) else emptyList()
        val weeks = recipe.weeks.sortedBy { it.weekNumber }
        if (weeks.size <= 1) return if (entry.duration == CatalogDuration.FINITE_CYCLE) emptyList() else listOf(repeating)
        val manyBlocks = recipe.distinctBlockCount > 1
        val segments = mutableListOf<Triple<Pair<Int, String>, IntRange, String>>()
        weeks.forEachIndexed { i, week ->
            val number = i + 1
            val label = when {
                week.kind == WeekExecutionKind.DELOAD -> BlockGoal.DELOAD.label
                week.blockGoal == BlockGoal.CUSTOM -> week.blockName.trim().ifEmpty { "Bloque ${week.blockIndex + 1}" }
                else -> week.blockGoal.label
            }
            val key = week.blockIndex to label
            val last = segments.lastOrNull()
            if (last != null && last.first == key) {
                segments[segments.lastIndex] = Triple(key, last.second.first..number, last.third)
            } else {
                val detail = if (manyBlocks) week.blockName.trim().takeUnless { it == label }.orEmpty() else ""
                segments += Triple(key, number..number, detail)
            }
        }
        return segments.map { (key, range, detail) -> PlanBlockModel(key.second, SetupPlanReveals.weeksLabel(range), detail) }
    }

    // ─── Portada, chips y atribución ─────────────────────────────────────────

    /**
     * El objetivo de la entrada, del que salen el color y la ilustración de la portada: powerbuilding (o fuerza e hipertrofia a la
     * vez), powerlifting, culturismo, o fuerza y cardio en los planes que programan cardio. Sin ninguno, la portada neutra.
     */
    fun goalProfileOf(entry: CatalogEntry): TrainingGoalProfile? {
        val strength = TrainingReference.POWERLIFTING in entry.references
        val muscle = TrainingReference.HYPERTROPHY in entry.references
        return when {
            TrainingReference.POWERBUILDING in entry.references || (strength && muscle) -> TrainingGoalProfile.POWERBUILDING
            strength -> TrainingGoalProfile.POWERLIFTING
            muscle -> TrainingGoalProfile.BODYBUILDING
            entry.schedulesCardio -> TrainingGoalProfile.STRENGTH_CARDIO
            else -> null
        }
    }

    /** El enfoque con las palabras de la tarjeta del catálogo: «Fuerza y músculo», «Fuerza» o «Músculo». */
    fun goalLabelOf(entry: CatalogEntry): String? {
        val strength = TrainingReference.POWERLIFTING in entry.references
        val muscle = TrainingReference.HYPERTROPHY in entry.references
        return when {
            TrainingReference.POWERBUILDING in entry.references || (strength && muscle) -> "Fuerza y músculo"
            strength -> "Fuerza"
            muscle -> "Músculo"
            else -> null
        }
    }

    /**
     * El material en un solo chip corto, como los del lienzo: hasta tres piezas, la lista («Barra, banco, rack de sentadilla»); cuatro o
     * cinco, las dos primeras y cuántas faltan («Barra, banco y 3 más»); seis o más, «Gimnasio completo».
     */
    fun materialChip(material: List<String>): String? {
        if (material.isEmpty()) return null
        val names = listOf(material.first()) + material.drop(1).map { label -> label.replaceFirstChar { it.lowercase() } }
        return when {
            names.size <= 3 -> names.joinToString(", ")
            names.size >= MATERIAL_FULL_GYM_FROM -> "Gimnasio completo"
            else -> "${names[0]}, ${names[1]} y ${names.size - 2} más"
        }
    }

    /** Desde cuántas piezas de material el plan pide un gimnasio completo. */
    private const val MATERIAL_FULL_GYM_FROM = 6

    private fun daysRangeLabel(range: IntRange): String =
        if (range.first == range.last) SpanishPlurals.days(range.first) else "${range.first}$EN_DASH${range.last} días"

    /** La fuente («Layne Norton · Biolayne, 2016.») o, sin ella, la procedencia; debajo, la edición consultada. */
    fun attributionOf(info: PlanInfoModel): String? = listOfNotNull(
        info.source?.attributionLine?.takeIf { it.isNotBlank() } ?: info.provenanceLabel.takeIf { it.isNotBlank() },
        info.source?.editionLine?.takeIf { it.isNotBlank() },
    ).joinToString("\n").ifBlank { null }
}
