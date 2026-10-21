package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.expenditure.nutritionLogDay
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reutilización del diario (N4): copiar una comida o un día a otras fechas, repetir el día anterior, plantillas y entrada rápida.
 * Pura: sin Android, sin Room y sin reloj (la fecha de «hoy» llega siempre de fuera).
 *
 * Principios:
 * - **Copia de la instantánea.** Una copia conserva cantidades, porciones y nutrientes tal como estaban guardados; nunca vuelve a
 *   buscar el alimento en el catálogo (si ya no existe, la copia sigue siendo la misma).
 * - **Ids derivados, no aleatorios.** El id de la comida copiada y el de cada alimento salen de (comida de origen, fecha destino):
 *   repetir la misma copia produce los mismos ids y el guardado (un upsert por id) no duplica nada. Ver [copyMealId].
 * - **Futuro = planificado.** Una copia a una fecha posterior a «hoy» es `PLANNED`: no cuenta como ingesta ni entra en el estimador
 *   de gasto (`buildDayObservations` ya descarta lo planificado). Ver [statusFor].
 */
object MealReuse {

    /** Máximo de fechas destino por operación (una vez y media un mes: un bloque de preparación de comidas, no un año entero). */
    const val MAX_TARGET_DATES = 31

    /** Cuánto se aleja una fecha destino de «hoy», hacia atrás o hacia delante, para ofrecerla (un año). */
    const val DATE_WINDOW_DAYS = 365L

    const val MAX_TEMPLATE_NAME = 60

    /** La marca que `duplicateLog` añade a la nota de una copia. Va una sola vez y al final. */
    const val DUPLICATE_MARK = "(duplicado)"

    /** Sufijo horario de un registro nuevo (mediodía): el día se decide por el prefijo `yyyy-MM-dd`, no por la hora. */
    const val DEFAULT_TIME_SUFFIX = "T12:00:00.000Z"

    // ─── Ids y claves deterministas ──────────────────────────────────────────

    /** La clave de idempotencia de una copia: de qué comida sale y a qué fecha va. Dos toques con la misma clave son una sola copia. */
    fun copyKey(sourceLogId: String, targetDate: LocalDate): String = "copia|$sourceLogId|$targetDate"

    /** Id de la comida copiada: determinista (mismo origen y mismo destino, mismo id). */
    fun copyMealId(sourceLogId: String, targetDate: LocalDate): String = derivedId("comida|" + copyKey(sourceLogId, targetDate))

    /** Id de un alimento copiado: depende de la comida, la fecha, su posición y el id del alimento de origen. */
    fun copyFoodId(sourceLogId: String, targetDate: LocalDate, index: Int, sourceFoodId: String): String =
        derivedId("alimento|" + copyKey(sourceLogId, targetDate) + "|$index|$sourceFoodId")

    /** Id de un alimento dentro de una plantilla: derivado del id de la plantilla y de su posición. */
    fun templateFoodId(templateId: String, index: Int): String = derivedId("plantilla|$templateId|$index")

    private fun derivedId(seed: String): String =
        UUID.nameUUIDFromBytes(("kpkn-reuse|$seed").toByteArray(Charsets.UTF_8)).toString()

    // ─── Estado y hora de una copia ──────────────────────────────────────────

    /** `PLANNED` si la fecha destino es posterior a [today]; `CONSUMED` si es hoy o pasada. */
    fun statusFor(target: LocalDate, today: LocalDate): NutritionStatus =
        if (target.isAfter(today)) NutritionStatus.PLANNED else NutritionStatus.CONSUMED

    private val TIME_SUFFIX = Regex("^T([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:?\\d{2})?$")

    /** La parte horaria del registro de origen (`T08:30:00.000Z`) si es válida; si no, el mediodía de siempre. */
    fun timeSuffix(sourceDate: String): String =
        sourceDate.drop(DAY_LENGTH).takeIf { TIME_SUFFIX.matches(it) } ?: DEFAULT_TIME_SUFFIX

    private const val DAY_LENGTH = 10

    private val TRAILING_MARKS = Regex("(\\s*\\(duplicado\\))+\\s*$", RegexOption.IGNORE_CASE)

    /**
     * La nota de una copia. Sin nota (o en blanco) no se inventa una. Con nota, termina en « (duplicado)» UNA vez: si la de origen ya
     * traía la marca (una copia de una copia) no se acumula, y aplicar la regla dos veces da lo mismo que una.
     */
    fun copyNote(original: String?): String? {
        if (original.isNullOrBlank()) return null
        val base = original.replace(TRAILING_MARKS, "").trim()
        return if (base.isEmpty()) DUPLICATE_MARK else "$base $DUPLICATE_MARK"
    }

    // ─── Validez ─────────────────────────────────────────────────────────────

    /** Lo mismo que exige el guardado durable: sin alimentos no hay comida, y cantidad y nutrientes han de ser finitos (cantidad > 0). */
    fun isCopyable(log: NutritionLog): Boolean =
        log.foods.isNotEmpty() && log.foods.all { isValidFood(it) }

    private fun isValidFood(food: LoggedFood): Boolean =
        food.amount.isFinite() && food.amount > 0.0 &&
            listOf(food.calories, food.protein, food.carbs, food.fats).all { it.isFinite() && it >= 0.0 }

    fun isDateInRange(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today.minusDays(DATE_WINDOW_DAYS)) && !date.isAfter(today.plusDays(DATE_WINDOW_DAYS))

    // ─── Copiar una comida ───────────────────────────────────────────────────

    /**
     * La copia de [source] en [target]: ids nuevos y derivados, el mismo tipo de comida, los alimentos TAL CUAL estaban (solo cambia su
     * id), la hora del original si es válida, la nota sin acumular y el estado que toca por la fecha ([statusFor]).
     */
    fun copyLog(source: NutritionLog, target: LocalDate, today: LocalDate): NutritionLog = NutritionLog(
        id = copyMealId(source.id, target),
        date = target.toString() + timeSuffix(source.date),
        mealType = source.mealType,
        foods = source.foods.mapIndexed { index, food -> food.copy(id = copyFoodId(source.id, target, index, food.id)) },
        notes = copyNote(source.notes),
        status = statusFor(target, today),
    )

    // ─── Plan de copia ───────────────────────────────────────────────────────

    enum class CopyPlanError { NO_SOURCES, NO_DATES, TOO_MANY_DATES, DATE_OUT_OF_RANGE }

    enum class CopySkipReason { SAME_DAY, INVALID_FOODS }

    /** Una copia concreta que se va a guardar. [key] es su clave de idempotencia ([copyKey]). */
    data class PlannedCopy(val key: String, val sourceLogId: String, val targetDate: LocalDate, val log: NutritionLog)

    data class SkippedCopy(val sourceLogId: String, val targetDate: LocalDate, val reason: CopySkipReason)

    /** Lo que se va a hacer: las copias, lo que se descarta (con su razón) o, si la petición no vale, el [error] y ninguna copia. */
    data class CopyPlan(
        val copies: List<PlannedCopy> = emptyList(),
        val skipped: List<SkippedCopy> = emptyList(),
        val error: CopyPlanError? = null,
    ) {
        val isRunnable: Boolean get() = error == null && copies.isNotEmpty()
    }

    /**
     * Arma las copias de [sources] a cada fecha de [targetDates] (sin repetir, de la más cercana a la más lejana). Una comida no se copia
     * al mismo día en el que ya está ([CopySkipReason.SAME_DAY]) ni si no se puede guardar ([CopySkipReason.INVALID_FOODS]).
     */
    fun planCopy(sources: List<NutritionLog>, targetDates: Collection<LocalDate>, today: LocalDate): CopyPlan {
        val dates = targetDates.distinct().sorted()
        when {
            sources.isEmpty() -> return CopyPlan(error = CopyPlanError.NO_SOURCES)
            dates.isEmpty() -> return CopyPlan(error = CopyPlanError.NO_DATES)
            dates.size > MAX_TARGET_DATES -> return CopyPlan(error = CopyPlanError.TOO_MANY_DATES)
            dates.any { !isDateInRange(it, today) } -> return CopyPlan(error = CopyPlanError.DATE_OUT_OF_RANGE)
        }
        val orderedSources = sources.withIndex()
            .sortedBy { (index, log) -> MealSlots.mealOrder.indexOf(log.mealType) * 10_000 + index }
            .map { it.value }
        val copies = mutableListOf<PlannedCopy>()
        val skipped = mutableListOf<SkippedCopy>()
        for (date in dates) {
            for (source in orderedSources) {
                when {
                    nutritionLogDay(source) == date -> skipped += SkippedCopy(source.id, date, CopySkipReason.SAME_DAY)
                    !isCopyable(source) -> skipped += SkippedCopy(source.id, date, CopySkipReason.INVALID_FOODS)
                    else -> copies += PlannedCopy(copyKey(source.id, date), source.id, date, copyLog(source, date, today))
                }
            }
        }
        return CopyPlan(copies, skipped)
    }

    /** El resultado de ejecutar un plan: lo guardado, lo que ya estaba (mismo id) y lo que falló. */
    data class CopyReport(
        val saved: List<PlannedCopy> = emptyList(),
        val alreadyThere: List<PlannedCopy> = emptyList(),
        val failed: List<PlannedCopy> = emptyList(),
        val skipped: List<SkippedCopy> = emptyList(),
    )

    /**
     * Ejecuta [plan] copia a copia con [save] (el guardado durable). Una copia cuyo id ya está en [existingIds] NO se vuelve a escribir:
     * un doble toque o un reintento no duplica ni pisa una copia que la persona ya editó. Un fallo no corta las demás; el informe dice qué
     * se guardó y qué no. La cancelación de la corrutina sí se propaga.
     */
    suspend fun runPlan(plan: CopyPlan, existingIds: Set<String>, save: suspend (NutritionLog) -> Unit): CopyReport {
        val saved = mutableListOf<PlannedCopy>()
        val already = mutableListOf<PlannedCopy>()
        val failed = mutableListOf<PlannedCopy>()
        if (plan.error != null) return CopyReport(skipped = plan.skipped)
        for (copy in plan.copies) {
            if (copy.log.id in existingIds) {
                already += copy
                continue
            }
            try {
                save(copy.log)
                saved += copy
            } catch (error: Exception) {
                // Room también puede cancelar su propio scope al cerrarse: eso es un fallo de guardado, no una cancelación de quien llama.
                if (error is CancellationException) currentCoroutineContext().ensureActive()
                failed += copy
            }
        }
        return CopyReport(saved, already, failed, plan.skipped)
    }

    /** Un aviso para la persona: el texto y si es un fallo (rojo) o una confirmación. */
    data class ReuseMessage(val text: String, val isError: Boolean)

    /** Qué decir tras ejecutar un plan: cuántas copias, cuáles faltan si algo falló y que las futuras quedan planificadas. */
    fun reportMessage(report: CopyReport): ReuseMessage {
        val attempted = report.saved.size + report.alreadyThere.size + report.failed.size
        if (report.failed.isNotEmpty()) {
            val done = report.saved.size + report.alreadyThere.size
            val listed = report.failed.take(MAX_LISTED_FAILURES).joinToString(", ") { describe(it) }
            val more = report.failed.size - MAX_LISTED_FAILURES
            val tail = if (more > 0) " y $more más" else ""
            return ReuseMessage(
                "Se guardaron $done de $attempted copias. No se pudieron guardar: $listed$tail. Inténtalo de nuevo: lo ya copiado no se duplica.",
                isError = true,
            )
        }
        if (attempted == 0) return ReuseMessage("No había nada que copiar.", isError = false)
        if (report.saved.isEmpty()) return ReuseMessage("Ya estaba copiado: no se duplicó nada.", isError = false)
        val planned = report.saved.count { it.log.status == NutritionStatus.PLANNED }
        val text = buildString {
            append(copiesText(report.saved.size)).append(if (report.saved.size == 1) " guardada." else " guardadas.")
            if (planned > 0) append(" Las de fechas futuras quedan planificadas.")
            if (report.alreadyThere.isNotEmpty()) append(" ").append(alreadyText(report.alreadyThere.size))
        }
        return ReuseMessage(text, isError = false)
    }

    private const val MAX_LISTED_FAILURES = 3

    private fun describe(copy: PlannedCopy): String =
        "${shortDateEs(copy.targetDate)} (${MealSlots.mealLabel(copy.log.mealType)})"

    private fun alreadyText(count: Int): String = if (count == 1) "1 ya estaba." else "$count ya estaban."

    /** «1 copia» / «3 copias». */
    fun copiesText(count: Int): String = if (count == 1) "1 copia" else "$count copias"

    /** El texto de la vista previa de la hoja «Copiar a…»: «3 copias de Almuerzo» o «6 copias (2 comidas × 3 fechas)». */
    fun copyPreview(sources: List<NutritionLog>, dateCount: Int): String {
        if (sources.isEmpty() || dateCount <= 0) return "Elige al menos una fecha."
        val total = sources.size * dateCount
        return if (sources.size == 1) {
            "${copiesText(total)} de ${MealSlots.mealLabel(sources.single().mealType)}"
        } else {
            "${copiesText(total)} (${sources.size} comidas × ${if (dateCount == 1) "1 fecha" else "$dateCount fechas"})"
        }
    }

    // ─── Selección de fechas (hoja «Copiar a…») ──────────────────────────────

    enum class CopyPreset { TODAY, TOMORROW, NEXT_3_DAYS, NEXT_7_DAYS }

    /** Las fechas elegidas y, si el último toque no se pudo aplicar, el aviso que se le enseña a la persona. */
    data class DateSelection(val dates: Set<LocalDate> = emptySet(), val notice: String? = null) {
        val sorted: List<LocalDate> get() = dates.sorted()
    }

    /** Al abrir la hoja: «mañana» ya elegido (salvo que sea el día de origen). */
    fun initialSelection(today: LocalDate, blocked: Set<LocalDate> = emptySet()): DateSelection {
        val tomorrow = today.plusDays(1)
        return if (tomorrow in blocked) DateSelection() else DateSelection(setOf(tomorrow))
    }

    fun presetDates(preset: CopyPreset, today: LocalDate): List<LocalDate> = when (preset) {
        CopyPreset.TODAY -> listOf(today)
        CopyPreset.TOMORROW -> listOf(today.plusDays(1))
        CopyPreset.NEXT_3_DAYS -> (1L..3L).map { today.plusDays(it) }
        CopyPreset.NEXT_7_DAYS -> (1L..7L).map { today.plusDays(it) }
    }

    /** Marca o desmarca una fecha. [blocked] son las fechas que no valen (el día de origen). Un toque que no se puede aplicar deja un aviso. */
    fun toggleDate(selection: DateSelection, date: LocalDate, today: LocalDate, blocked: Set<LocalDate> = emptySet()): DateSelection = when {
        date in blocked -> selection.copy(notice = "Ese es el día de origen: elige otro.")
        !isDateInRange(date, today) -> selection.copy(notice = "Esa fecha queda a más de un año de hoy.")
        date in selection.dates -> DateSelection(selection.dates - date)
        selection.dates.size >= MAX_TARGET_DATES -> selection.copy(notice = limitNotice())
        else -> DateSelection(selection.dates + date)
    }

    /** Un atajo («Hoy», «Mañana», «Próximos 3 días»…): si sus fechas ya están todas elegidas las quita; si no, las añade. */
    fun togglePreset(selection: DateSelection, preset: CopyPreset, today: LocalDate, blocked: Set<LocalDate> = emptySet()): DateSelection {
        val wanted = presetDates(preset, today).filter { it !in blocked }
        if (wanted.isEmpty()) return selection.copy(notice = "Ese atajo coincide con el día de origen: elige otra fecha.")
        if (wanted.all { it in selection.dates }) return DateSelection(selection.dates - wanted.toSet())
        val merged = selection.dates + wanted
        return if (merged.size > MAX_TARGET_DATES) selection.copy(notice = limitNotice()) else DateSelection(merged)
    }

    private fun limitNotice() = "Máximo $MAX_TARGET_DATES fechas por vez."

    /** El atajo está activo si todas sus fechas (menos las bloqueadas) están elegidas. */
    fun isPresetActive(selection: DateSelection, preset: CopyPreset, today: LocalDate, blocked: Set<LocalDate> = emptySet()): Boolean {
        val wanted = presetDates(preset, today).filter { it !in blocked }
        return wanted.isNotEmpty() && wanted.all { it in selection.dates }
    }

    // ─── Repetir el día anterior ─────────────────────────────────────────────

    sealed interface RepeatDecision {
        /** El día anterior no tiene comidas que copiar. */
        data class NothingToRepeat(val sourceDate: LocalDate) : RepeatDecision

        /** El día de destino está vacío: se copia sin preguntar. */
        data class Ready(val plan: CopyPlan) : RepeatDecision

        /** El día de destino ya tiene [existingMeals] comidas: hay que preguntar «Añadir» o «Cancelar». Nunca se reemplaza. */
        data class NeedsConfirmation(val plan: CopyPlan, val existingMeals: Int) : RepeatDecision
    }

    /** El día cuyas comidas trae «Repetir ayer» al día [target]: el anterior. */
    fun repeatSourceDate(target: LocalDate): LocalDate = target.minusDays(1)

    /** «Repetir ayer» en el día de hoy; en cualquier otro, «Repetir el día anterior» (el de origen es el anterior al que miras). */
    fun repeatLabel(target: LocalDate, today: LocalDate): String =
        if (target == today) "Repetir ayer" else "Repetir el día anterior"

    /**
     * Decide qué hacer al pulsar «Repetir ayer» en el día [target], mirando TODOS los registros ([logs]): las comidas consumidas del día
     * anterior son lo que se copia; si [target] ya tiene comidas (consumidas o planificadas) se pide confirmación y, pase lo que pase, lo
     * que ya hay se queda: copiar solo añade.
     */
    fun decideRepeat(logs: List<NutritionLog>, target: LocalDate, today: LocalDate): RepeatDecision {
        val sourceDate = repeatSourceDate(target)
        val sources = logs.filter { it.status != NutritionStatus.PLANNED && nutritionLogDay(it) == sourceDate }
        val plan = planCopy(sources, listOf(target), today)
        if (!plan.isRunnable) return RepeatDecision.NothingToRepeat(sourceDate)
        val existing = logs.count { nutritionLogDay(it) == target }
        return if (existing == 0) RepeatDecision.Ready(plan) else RepeatDecision.NeedsConfirmation(plan, existing)
    }

    // ─── Plantillas ──────────────────────────────────────────────────────────

    sealed interface TemplateNameCheck {
        data class Valid(val name: String) : TemplateNameCheck
        data class Invalid(val message: String) : TemplateNameCheck
    }

    fun validateTemplateName(raw: String): TemplateNameCheck {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            name.isEmpty() -> TemplateNameCheck.Invalid("Escribe un nombre.")
            name.length > MAX_TEMPLATE_NAME -> TemplateNameCheck.Invalid("Máximo $MAX_TEMPLATE_NAME caracteres.")
            else -> TemplateNameCheck.Valid(name)
        }
    }

    private val MONTHS_ES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** «21 oct». */
    fun shortDateEs(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_ES[date.monthValue - 1]}"

    /** El nombre que se ofrece al guardar una comida como plantilla: «Almuerzo · 21 oct». */
    fun defaultTemplateName(log: NutritionLog): String {
        val label = MealSlots.mealLabel(log.mealType)
        val day = nutritionLogDay(log) ?: return label
        return "$label · ${shortDateEs(day)}"
    }

    /**
     * La plantilla que guarda la comida [log] con el nombre [name] (ya validado) y el id [id]: los alimentos son su instantánea (solo cambia
     * su id) y los totales salen de ellos. Null si la comida no se puede guardar ([isCopyable]). Con el mismo [id], guardar de nuevo
     * reemplaza la plantilla en vez de duplicarla.
     */
    fun templateFromLog(log: NutritionLog, name: String, id: String, createdAt: String): MealTemplate? {
        if (!isCopyable(log)) return null
        val foods = log.foods.mapIndexed { index, food -> food.copy(id = templateFoodId(id, index)) }
        val totals = computeFoodTotals(foods)
        return MealTemplate(
            id = id,
            name = name,
            description = foods.joinToString(", ") { it.foodName }.take(MAX_DESCRIPTION),
            foods = foods,
            totalCalories = totals.calories,
            totalProtein = totals.protein,
            totalCarbs = totals.carbs,
            totalFats = totals.fats,
            createdAt = createdAt,
        )
    }

    private const val MAX_DESCRIPTION = 160

    /** La plantilla con otro nombre, o null si el nombre no vale. Lo demás no cambia. */
    fun renameTemplate(template: MealTemplate, rawName: String): MealTemplate? =
        (validateTemplateName(rawName) as? TemplateNameCheck.Valid)?.let { template.copy(name = it.name) }

    /** Una fila de la lista de plantillas. Los totales salen de los alimentos guardados (los mismos números que una fila del diario). */
    data class TemplateSummary(
        val id: String,
        val name: String,
        val kcal: Int,
        val protein: Int,
        val carbs: Int,
        val fats: Int,
        val foodCount: Int,
    )

    fun summarize(template: MealTemplate): TemplateSummary {
        val totals = computeFoodTotals(template.foods)
        return TemplateSummary(
            id = template.id,
            name = template.name.ifBlank { "Plantilla" },
            kcal = round(totals.calories).toInt(),
            protein = round(totals.protein).toInt(),
            carbs = round(totals.carbs).toInt(),
            fats = round(totals.fats).toInt(),
            foodCount = template.foods.size,
        )
    }

    /** Las plantillas que se pueden usar (con alimentos), la más reciente primero y, a igual fecha, por nombre. */
    fun usableTemplates(templates: List<MealTemplate>): List<MealTemplate> =
        templates.filter { it.foods.isNotEmpty() }
            .sortedWith(compareByDescending<MealTemplate> { it.createdAt }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })

    /**
     * Los alimentos de [template] como borrador editable: cada uno con un id NUEVO (nada del borrador comparte id con la plantilla) y su
     * instantánea intacta. Los que ya no se podrían guardar se descartan.
     */
    fun templateDraftFoods(template: MealTemplate, newId: () -> String = { UUID.randomUUID().toString() }): List<LoggedFood> =
        template.foods.filter { isValidFood(it) }.map { it.copy(id = newId()) }

    // ─── Micronutrientes y entrada rápida ────────────────────────────────────

    /**
     * Si los micronutrientes de [food] (fibra, azúcar, sodio, potasio y la lista) son datos y no ceros por defecto. Una entrada rápida
     * solo trae kcal y macros: sus micronutrientes son DESCONOCIDOS, así que la lógica de cobertura no debe contarlos como 0.
     */
    fun micronutrientsKnown(food: LoggedFood): Boolean = !food.isQuickEntry

    /** El nombre que lleva la entrada rápida cuando la persona no escribe uno. */
    const val QUICK_ENTRY_NAME = "Entrada rápida"
}

// ─── Entrada rápida (formulario, validación y alimento) ──────────────────────────────────────────

/** Lo que hay escrito en la entrada rápida, tal cual (texto). Los números admiten coma o punto. */
data class QuickEntryForm(
    val name: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fats: String = "",
)

enum class QuickEntryField { NAME, KCAL, PROTEIN, CARBS, FATS }

/**
 * El resultado de validar la entrada rápida. [errors] bloquean el guardado; [warning] avisa pero no bloquea (kcal que no cuadran con los
 * macros). [food] solo existe si no hay errores.
 */
data class QuickEntryValidation(
    val errors: Map<QuickEntryField, String>,
    val warning: String?,
    val food: LoggedFood?,
) {
    val isValid: Boolean get() = errors.isEmpty() && food != null
}

/** Validación y conversión de la entrada rápida a un alimento marcado como tal ([LoggedFood.isQuickEntry]). Pura. */
object QuickEntry {
    const val MAX_NAME = 60
    const val MAX_KCAL = 10_000.0
    const val MAX_MACRO_G = 1_000.0

    /** Margen con el que las kcal escritas «cuadran» con 4·P + 4·C + 9·G: el mayor de 30 kcal o el 15 % (alcohol, fibra y redondeos). */
    private const val ENERGY_TOLERANCE_KCAL = 30.0
    private const val ENERGY_TOLERANCE_FRACTION = 0.15

    private val NUMBER = Regex("^(\\d+([.,]\\d*)?|[.,]\\d+)$")

    /** El número escrito (coma o punto), o null si no es un número simple sin signo ni letras. */
    fun parseNumber(text: String): Double? {
        val trimmed = text.trim()
        if (!NUMBER.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Valida [form]. Las kcal son obligatorias y mayores que 0; proteína, carbohidratos y grasas son opcionales (en blanco = 0 en los
     * totales). Si hay macros y no cuadran con las kcal, avisa sin bloquear. El alimento lleva el id [foodId]: la misma entrada guardada
     * dos veces es un solo alimento.
     */
    fun validate(form: QuickEntryForm, foodId: String): QuickEntryValidation {
        val errors = linkedMapOf<QuickEntryField, String>()
        val name = form.name.trim().replace(Regex("\\s+"), " ")
        if (name.length > MAX_NAME) errors[QuickEntryField.NAME] = "Máximo $MAX_NAME caracteres."

        var kcal: Double? = null
        when {
            form.kcal.isBlank() -> errors[QuickEntryField.KCAL] = "Escribe las kcal."
            else -> {
                val value = parseNumber(form.kcal)
                when {
                    value == null -> errors[QuickEntryField.KCAL] = "Escribe un número, sin signo ni letras."
                    value <= 0.0 -> errors[QuickEntryField.KCAL] = "Las kcal tienen que ser mayores que 0."
                    value > MAX_KCAL -> errors[QuickEntryField.KCAL] = "Máximo ${MAX_KCAL.toInt()} kcal."
                    else -> kcal = value
                }
            }
        }
        val protein = optionalMacro(form.protein, QuickEntryField.PROTEIN, errors)
        val carbs = optionalMacro(form.carbs, QuickEntryField.CARBS, errors)
        val fats = optionalMacro(form.fats, QuickEntryField.FATS, errors)

        var warning: String? = null
        val anyMacro = form.protein.isNotBlank() || form.carbs.isNotBlank() || form.fats.isNotBlank()
        if (kcal != null && anyMacro && errors.isEmpty()) {
            val expected = 4.0 * (protein ?: 0.0) + 4.0 * (carbs ?: 0.0) + 9.0 * (fats ?: 0.0)
            val tolerance = max(ENERGY_TOLERANCE_KCAL, ENERGY_TOLERANCE_FRACTION * max(kcal, expected))
            if (abs(kcal - expected) > tolerance) {
                warning = "Las kcal no cuadran con los macros (4·P + 4·C + 9·G ≈ ${round(expected).toInt()} kcal). Puedes guardarlo igual."
            }
        }
        if (errors.isNotEmpty() || kcal == null) return QuickEntryValidation(errors, warning, null)
        val food = LoggedFood(
            id = foodId,
            foodName = name.ifEmpty { MealReuse.QUICK_ENTRY_NAME },
            amount = 1.0,
            unit = "porción",
            calories = kcal,
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fats = fats ?: 0.0,
            quantity = 1.0,
            // Procedencia «escrito por la persona»: al reabrirla para editar, la tarjeta no se degrada a estimación.
            evidenceJson = "{\"source\":\"MANUAL\"}",
            isQuickEntry = true,
        )
        return QuickEntryValidation(errors, warning, food)
    }

    private fun optionalMacro(text: String, field: QuickEntryField, errors: MutableMap<QuickEntryField, String>): Double? {
        if (text.isBlank()) return null
        val value = parseNumber(text)
        when {
            value == null -> errors[field] = "Escribe un número, sin signo ni letras."
            value > MAX_MACRO_G -> errors[field] = "Máximo ${MAX_MACRO_G.toInt()} g."
            else -> return value
        }
        return null
    }
}
