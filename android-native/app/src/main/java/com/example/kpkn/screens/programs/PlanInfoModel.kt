package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.GlossaryEntry
import com.example.kpkn.data.programs.PlanGlossary
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PlanTerm
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.REQUIREMENT_BALL
import com.example.kpkn.domain.training.REQUIREMENT_BENCH
import com.example.kpkn.domain.training.REQUIREMENT_BENCH_INCLINE
import com.example.kpkn.domain.training.REQUIREMENT_DIP_BARS
import com.example.kpkn.domain.training.REQUIREMENT_LOW_BAR_SUPPORT
import com.example.kpkn.domain.training.REQUIREMENT_NORDIC_ANCHOR
import com.example.kpkn.domain.training.REQUIREMENT_PULL_UP_BAR
import com.example.kpkn.domain.training.REQUIREMENT_RACK
import com.example.kpkn.domain.training.REQUIREMENT_SUPPORT
import com.example.kpkn.domain.training.supportRequirementsFor
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogEquipmentLabel
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Desde dónde se abre la hoja «Cómo funciona»; decide el botón primario.
 *
 * - [LIBRARY]: biblioteca de planes, el botón lleva a configurar el plan.
 * - [WIZARD]: paso de elección del asistente, el botón elige el plan.
 * - [READ_ONLY]: consulta de un plan ya activado, sin botón.
 */
enum class PlanInfoMode { LIBRARY, WIZARD, READ_ONLY }

/** «Tu semana tipo» de un plan. */
sealed interface TypicalWeek {
    /** Planes que se generan al activarlos (sin receta): un texto en lugar de una semana. */
    data class Generated(val text: String) : TypicalWeek

    /** Semana 1 de la receta (o la semana real de un candidato listo), día por día. */
    data class Days(val days: List<DayLines>, val caption: String? = null) : TypicalWeek

    /** Nada que enseñar (estructuras en blanco): la hoja omite la sección. */
    data object Unavailable : TypicalWeek
}

/** Un día de la semana tipo: su rótulo y una línea por ejercicio (o por bloque de cardio). */
data class DayLines(val label: String, val lines: List<String>)

/**
 * Sección «Fuente» de la hoja. [url] solo existe si es un enlace externo que se puede abrir (nunca
 * `kpkn.fit`); [urlLabel] es el texto del enlace, sin la dirección. [authoredRules] y [kpknDefaults]
 * son los dos plegables de las fuentes de autor (null = no hay nada que plegar).
 */
data class SourceSection(
    val attributionLine: String?,
    val url: String?,
    val urlLabel: String?,
    val editionLine: String?,
    val authoredRules: List<String>?,
    val kpknDefaults: List<String>?,
)

/**
 * Primera semana ya materializada de un candidato `Ready` del asistente, tal como la entrega el
 * asistente: sesiones con sus ejercicios y las series ya redactadas. La hoja solo la pinta en los
 * planes sin receta; construir este objeto desde el candidato es cosa del asistente (C.P5).
 */
data class ReadyWeekSnapshot(val sessions: List<ReadySession>)

data class ReadySession(val label: String, val exercises: List<ReadyExercise>)

/** [series] llega redactada («3 × 8–12 con 2 repeticiones en reserva»); [PlanInfoModelBuilder.formatSets] sirve para ello. */
data class ReadyExercise(val name: String, val series: String)

/**
 * Todo lo que la hoja «Cómo funciona» muestra de un plan, ya redactado en español llano: la hoja
 * ([PlanInfoSheet]) solo lo pinta. Se construye con [PlanInfoModelBuilder.build], una función pura
 * y testeable (sin Android ni Compose).
 */
data class PlanInfoModel(
    val displayName: String,
    val provenanceLabel: String,
    val levelLabel: String,
    /** «Especialización», «Complemento» o «Estructura»; null en los planes completos. */
    val kindLabel: String?,
    val summary: String,
    val typicalWeek: TypicalWeek,
    /** Etiquetas humanas únicas, en orden de aparición; vacío si no se puede afirmar nada. */
    val material: List<String>,
    val source: SourceSection?,
    val notes: List<String>,
    val glossary: List<GlossaryEntry>,
    /** «Configurar este plan» en la biblioteca, «Elegir este plan» en el asistente, null en solo lectura. */
    val primaryActionLabel: String?,
)

/**
 * Constructor puro del [PlanInfoModel] (C.P8). Los nombres de ejercicio y el material llegan por
 * funciones para que el modelo no dependa del catálogo en ejecución.
 */
object PlanInfoModelBuilder {
    const val PRIMARY_LIBRARY = "Configurar este plan"
    const val PRIMARY_WIZARD = "Elegir este plan"
    const val GENERATED_WEEK_TEXT = "Se genera con tus días, tu tiempo y tu material."
    const val BODYWEIGHT_ONLY = "Solo tu peso corporal"
    const val UNKNOWN_EXERCISE = "Ejercicio pendiente de catálogo"

    private const val TIMES = "×"
    private const val EN_DASH = "–"
    private const val NAME_SEPARATOR = " · "

    /**
     * @param names nombre visible de un ejercicio por su configuración (null = no se conoce).
     * @param equipmentOf `equipmentId` del catálogo de una configuración (`barbell`, `dumbbells`…); el modelo
     *   lo traduce a la etiqueta del catálogo.
     * @param readyWeek primera semana real de un candidato `Ready`; solo se usa en los planes sin receta.
     */
    fun build(
        entry: CatalogEntry,
        mode: PlanInfoMode,
        names: (String) -> String?,
        equipmentOf: (String) -> String?,
        readyWeek: ReadyWeekSnapshot? = null,
    ): PlanInfoModel {
        val recipe = entry.recipe ?: entry.template?.recipe
        return PlanInfoModel(
            displayName = entry.displayName,
            provenanceLabel = PlanLabels.provenanceLabel(entry),
            levelLabel = PlanLabels.levelLabel(entry.levels),
            kindLabel = kindLabel(entry.kind),
            summary = entry.summary,
            typicalWeek = typicalWeek(entry, recipe, names, equipmentOf, readyWeek),
            material = material(recipe, equipmentOf),
            source = sourceSection(entry),
            notes = entry.notes,
            glossary = glossaryOf(entry),
            primaryActionLabel = when (mode) {
                PlanInfoMode.LIBRARY -> PRIMARY_LIBRARY
                PlanInfoMode.WIZARD -> PRIMARY_WIZARD
                PlanInfoMode.READ_ONLY -> null
            },
        )
    }

    // ─── Cabecera, notas y glosario ──────────────────────────────────────────

    private fun kindLabel(kind: PlanKind): String? = when (kind) {
        PlanKind.PLAN -> null
        PlanKind.ESPECIALIZACION -> "Especialización"
        PlanKind.COMPLEMENTO -> "Complemento"
        PlanKind.ESTRUCTURA -> "Estructura"
    }

    /** Los términos del plan; si usa el máximo de entrenamiento, también explica el 1RM del que sale. */
    private fun glossaryOf(entry: CatalogEntry): List<GlossaryEntry> {
        val terms = entry.terms
        val withOneRm = if (PlanTerm.TM in terms) terms + PlanTerm.ONE_RM else terms
        return PlanGlossary.entriesFor(withOneRm)
    }

    // ─── Tu semana tipo ──────────────────────────────────────────────────────

    private fun typicalWeek(
        entry: CatalogEntry,
        recipe: TrainingPlanRecipe?,
        names: (String) -> String?,
        equipmentOf: (String) -> String?,
        readyWeek: ReadyWeekSnapshot?,
    ): TypicalWeek {
        if (recipe != null) {
            val week = recipe.weeks.firstOrNull { it.weekNumber == 1 } ?: recipe.weeks.firstOrNull()
            val days = week?.days.orEmpty()
                .mapIndexed { index, day -> dayLines(day, index, names, equipmentOf) }
                .filter { it.lines.isNotEmpty() }
            if (days.isEmpty()) return TypicalWeek.Unavailable
            val caption = if (recipe.weeks.size > 1) "Así es la semana 1 de ${recipe.weeks.size}." else null
            return TypicalWeek.Days(numberRepeatedLabels(days), caption)
        }
        if (entry.source == CatalogSource.NATIVE) {
            val sessions = readyWeek?.sessions.orEmpty()
                .map { session ->
                    DayLines(
                        label = plainDayLabel(session.label),
                        lines = session.exercises.map { exercise ->
                            if (exercise.series.isBlank()) exercise.name else exercise.name + NAME_SEPARATOR + exercise.series
                        },
                    )
                }
                .filter { it.lines.isNotEmpty() }
            return if (sessions.isNotEmpty()) {
                TypicalWeek.Days(numberRepeatedLabels(sessions))
            } else {
                TypicalWeek.Generated(GENERATED_WEEK_TEXT)
            }
        }
        return TypicalWeek.Unavailable
    }

    private fun dayLines(
        day: DayRecipe,
        index: Int,
        names: (String) -> String?,
        equipmentOf: (String) -> String?,
    ): DayLines {
        // Un slot solo con calentamientos no enseña nada. Si dos configuraciones distintas comparten nombre
        // (remo con mancuernas y remo en polea), se distinguen por su material; el mismo ejercicio dos veces no.
        val slots = day.slots.filter { slot -> slot.sets.any { set -> !set.isWarmup } }
        val baseNames = slots.map { slot -> exerciseName(slot, names) }
        val ambiguous = baseNames.indices
            .groupBy { position -> baseNames[position] }
            .filterValues { positions -> positions.map { slots[it].lift.configurationId }.distinct().size > 1 }
            .keys
        val strength = slots.mapIndexed { position, slot ->
            val base = baseNames[position]
            slotLine(slot, if (base in ambiguous) distinguished(base, slot.lift.configurationId, equipmentOf) else base)
        }
        val cardioFirst = day.cardioBlocks.filter { it.position == RecipeCardioPosition.BEFORE_STRENGTH }.map { block -> cardioLine(block) }
        val cardioAfter = day.cardioBlocks.filter { it.position != RecipeCardioPosition.BEFORE_STRENGTH }.map { block -> cardioLine(block) }
        return DayLines(
            label = plainDayLabel(day.label.ifBlank { "Día ${index + 1}" }),
            lines = cardioFirst + strength + cardioAfter,
        )
    }

    private fun exerciseName(slot: SlotRecipe, names: (String) -> String?): String =
        names(slot.lift.configurationId)?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_EXERCISE

    /** El nombre con su material entre paréntesis («Elevación de Talones (gemelo sentado)») para no repetir líneas. */
    private fun distinguished(name: String, configurationId: String, equipmentOf: (String) -> String?): String {
        val detail = implementLabel(configurationId, equipmentOf) ?: return name
        return "$name (${detail.lowercase()})"
    }

    /** La etiqueta que mejor distingue una configuración de otra del mismo nombre: aparato curado o implemento. */
    private fun implementLabel(configurationId: String, equipmentOf: (String) -> String?): String? {
        val key = configurationId.trim().lowercase()
        val curated = EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { key in it.machineConfigurations }?.label
        if (curated != null) return curated
        val equipmentId = equipmentOf(configurationId) ?: return null
        return if (equipmentId == "bodyweight") null else exerciseCatalogEquipmentLabel(equipmentId)
    }

    /** `{nombre} · {series}` más la técnica entre paréntesis; [name] es el nombre que se enseña. */
    private fun slotLine(slot: SlotRecipe, name: String): String {
        val series = formatSets(slot.sets, slot.authoredSetRange)
        val technique = slot.technique?.let { techniquePhrase(it) }?.takeUnless { name.contains(it.keyword, ignoreCase = true) }
        return buildString {
            append(name).append(NAME_SEPARATOR).append(series)
            if (technique != null) append(" (").append(technique.phrase).append(')')
        }
    }

    /** Dos días con el mismo rótulo («Sesión», «Sesión») se numeran («Sesión 1», «Sesión 2»). */
    private fun numberRepeatedLabels(days: List<DayLines>): List<DayLines> {
        val totals = days.groupingBy { it.label }.eachCount()
        val seen = HashMap<String, Int>()
        return days.map { day ->
            if ((totals[day.label] ?: 0) > 1) {
                val number = (seen[day.label] ?: 0) + 1
                seen[day.label] = number
                day.copy(label = "${day.label} $number")
            } else {
                day
            }
        }
    }

    private fun cardioLine(block: RecipeCardioBlock): String {
        val type = CardioPrescriptionFormatter.typeLabel(block.details.type)
        val minutes = (block.details.effectiveDurationSeconds() + 59) / 60
        return if (minutes > 0) "Cardio: $type $minutes min" else "Cardio: $type"
    }

    private val regionWords = mapOf("Upper" to "Torso", "Lower" to "Pierna")
    private val focusWords = mapOf(
        "ME" to "esfuerzo máximo",
        "DE" to "esfuerzo dinámico",
        "Power" to "fuerza",
        "Hypertrophy" to "hipertrofia",
    )
    private val sessionCode = Regex("""^S(\d+)$""")

    /**
     * Los rótulos de día vienen de las recetas y algunos son jerga o inglés (`S1`, `ME Upper`, `Lower A`,
     * `Upper Power`). Aquí se traducen solo los que existen («Sesión 1», «Torso, esfuerzo máximo», «Pierna A»,
     * «Torso, fuerza»); cualquier otro rótulo pasa tal cual.
     */
    private fun plainDayLabel(label: String): String {
        val trimmed = label.trim()
        sessionCode.matchEntire(trimmed)?.let { match -> return "Sesión ${match.groupValues[1]}" }
        val tokens = trimmed.split(' ').filter { it.isNotEmpty() }
        val region = tokens.firstNotNullOfOrNull { token -> regionWords[token] } ?: return trimmed
        val focus = tokens.firstNotNullOfOrNull { token -> focusWords[token] }
        val rest = tokens.filter { token -> token !in regionWords && token !in focusWords }
        if (rest.size > 1 || rest.any { it.length > 2 }) return trimmed
        return region + rest.singleOrNull()?.let { " $it" }.orEmpty() + focus?.let { ", $it" }.orEmpty()
    }

    // ─── Series: «3 × 5 al 90 % de tu TM» ────────────────────────────────────

    /**
     * Redacta las series de trabajo de un slot (los calentamientos no cuentan). Las series idénticas y
     * consecutivas se agrupan («5 × 5»); si [authoredRange] existe y todas las series son iguales, el
     * número de series sale como lo publica el autor («3–4 × 8–12»). Cuando las series difieren, se
     * listan en orden y, si todas comparten la base de carga, esta se dice una sola vez al final:
     * «1 × 5 al 50 %, 1 × 5 al 62,5 % y 1 × 5 al 75 % de tu TM». Cadena vacía si no hay series de trabajo.
     */
    fun formatSets(sets: List<SetRecipe>, authoredRange: AuthoredSetRange? = null): String {
        val working = sets.filterNot { it.isWarmup }
        if (working.isEmpty()) return ""
        return render(groupConsecutive(working.map { recipeSet -> phraseOf(recipeSet) }), authoredRange)
    }

    /** Un porcentaje o número con coma decimal y sin ceros inútiles: 62.5 → «62,5», 90.0 → «90». */
    fun formatNumber(value: Double): String =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    /** Cómo se dice una serie, sin contar cuántas hay. */
    private data class SetPhrase(
        /** «5», «8–12», «3+»; null si la serie no declara repeticiones. */
        val reps: String?,
        /** «al 90 %», «con 2 repeticiones en reserva», «a esfuerzo 8 de 10», «como tu serie más pesada». */
        val lead: String?,
        /** La base de un porcentaje («de tu TM»); null si no hay porcentaje. */
        val base: String?,
        /** El esfuerzo que acompaña a un porcentaje («con 2 repeticiones en reserva»). */
        val extra: String?,
        val isRepMax: Boolean = false,
        val repMaxCount: Int? = null,
    )

    private class PercentPhrase(val lead: String, val base: String?)

    private fun phraseOf(set: SetRecipe): SetPhrase {
        val effort = effortPhrase(set)
        if (set.loadBasis == LoadBasis.REP_MAX && set.reference == null) {
            return SetPhrase(
                reps = null,
                lead = null,
                base = null,
                extra = effort,
                isRepMax = true,
                repMaxCount = set.reps ?: set.repsMax ?: set.repsMin,
            )
        }
        val percent = percentPhrase(set)
        return if (percent != null) {
            SetPhrase(reps = repsText(set), lead = percent.lead, base = percent.base, extra = effort)
        } else {
            SetPhrase(reps = repsText(set), lead = effort, base = null, extra = null)
        }
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

    /** Repeticiones en reserva si las hay (el esfuerzo percibido sale de ellas); si no, el esfuerzo. */
    private fun effortPhrase(set: SetRecipe): String? {
        val rir = set.rir
        val rpe = set.rpe
        return when {
            rir != null && rir <= 0 -> "sin repeticiones en reserva"
            rir != null -> "con ${SpanishPlurals.withNoun(rir, "repetición", "repeticiones")} en reserva"
            rpe != null -> "a esfuerzo ${formatNumber(rpe)} de 10"
            else -> null
        }
    }

    private fun percentPhrase(set: SetRecipe): PercentPhrase? {
        val percent = set.percent ?: return null
        val reference = set.reference
        // El 100 % de la serie más pesada es la propia serie más pesada.
        if (reference == null && set.loadBasis == LoadBasis.PERCENT_OF_TOP_SET && percent >= 100.0) {
            return PercentPhrase(lead = "como tu serie más pesada", base = null)
        }
        val base = referenceBase(reference) ?: loadBasisBase(set.loadBasis) ?: return null
        return PercentPhrase(lead = "al ${formatNumber(percent)} %", base = base)
    }

    /** La referencia explícita de la serie manda sobre la base genérica (PHAT: carga habitual de 3–5 repeticiones). */
    private fun referenceBase(reference: PlanLoadReference?): String? {
        if (reference == null) return null
        return when (reference.kind) {
            PlanLoadReferenceKind.EXERCISE_1RM -> "de tu 1RM"
            PlanLoadReferenceKind.EXERCISE_TM -> "de tu TM"
            PlanLoadReferenceKind.OBSERVED_WORKING_SET -> habitualBase(reference.repMin, reference.repMax)
            PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL -> null
        }
    }

    private fun habitualBase(repMin: Int?, repMax: Int?): String = when {
        repMin != null && repMax != null && repMax > repMin -> "de tu carga habitual de $repMin$EN_DASH$repMax repeticiones"
        repMin != null && repMax != null -> "de tu carga habitual de ${SpanishPlurals.withNoun(repMin, "repetición", "repeticiones")}"
        else -> "de tu carga habitual"
    }

    private fun loadBasisBase(basis: LoadBasis): String? = when (basis) {
        LoadBasis.PERCENT_TM -> "de tu TM"
        LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX -> "de tu 1RM"
        LoadBasis.PERCENT_OF_TOP_SET -> "de tu serie más pesada"
        LoadBasis.RPE, LoadBasis.REP_MAX -> null
    }

    private fun groupConsecutive(phrases: List<SetPhrase>): List<Pair<SetPhrase, Int>> {
        val groups = ArrayList<Pair<SetPhrase, Int>>()
        for (phrase in phrases) {
            val last = groups.lastOrNull()
            if (last != null && last.first == phrase) {
                groups[groups.lastIndex] = last.first to (last.second + 1)
            } else {
                groups.add(phrase to 1)
            }
        }
        return groups
    }

    private fun render(groups: List<Pair<SetPhrase, Int>>, authoredRange: AuthoredSetRange?): String {
        val single = groups.size == 1
        val commonBase = groups.map { it.first.base }.distinct().singleOrNull()
        val factorBase = groups.size > 1 && commonBase != null &&
            groups.all { it.first.base == commonBase && it.first.extra == null && !it.first.isRepMax }
        val parts = groups.map { (phrase, count) ->
            val countText = if (single && authoredRange != null) rangeText(authoredRange) else count.toString()
            renderGroup(phrase, countText, includeBase = !factorBase)
        }
        val joined = when {
            parts.size == 1 -> parts.first()
            groups.any { it.first.extra != null } -> parts.joinToString(NAME_SEPARATOR)
            else -> parts.dropLast(1).joinToString(", ") + " y " + parts.last()
        }
        return if (factorBase) "$joined $commonBase" else joined
    }

    private fun rangeText(range: AuthoredSetRange): String =
        if (range.max > range.min) "${range.min}$EN_DASH${range.max}" else "${range.min}"

    private fun renderGroup(phrase: SetPhrase, countText: String, includeBase: Boolean): String = buildString {
        if (phrase.isRepMax) {
            val reps = phrase.repMaxCount?.let { SpanishPlurals.withNoun(it, "repetición", "repeticiones") } ?: "repeticiones"
            append(countText).append(" $TIMES al máximo de ").append(reps)
        } else {
            val reps = phrase.reps
            if (reps != null) {
                append(countText).append(" $TIMES ").append(reps)
            } else {
                append(countText).append(if (countText == "1") " serie" else " series")
            }
            phrase.lead?.let { append(' ').append(it) }
            if (includeBase) phrase.base?.let { append(' ').append(it) }
        }
        phrase.extra?.let { append(", ").append(it) }
    }

    private class TechniquePhrase(val phrase: String, val keyword: String)

    /** La técnica en palabras para ir entre paréntesis, y la palabra que, si el nombre ya la dice, la hace innecesaria. */
    private fun techniquePhrase(modifier: TechniqueModifier): TechniquePhrase = when (modifier) {
        TechniqueModifier.PAUSE_2S -> TechniquePhrase("pausa de dos segundos", "pausa")
        TechniqueModifier.TEMPO_3_0_3 -> TechniquePhrase("tres segundos bajando y tres subiendo", "tempo")
        TechniqueModifier.CLOSE_GRIP -> TechniquePhrase("agarre cerrado", "cerrado")
        TechniqueModifier.GRIP_SUPINATED -> TechniquePhrase("agarre supino", "supino")
        TechniqueModifier.GRIP_NEUTRAL -> TechniquePhrase("agarre neutro", "neutro")
        TechniqueModifier.GRIP_WIDE -> TechniquePhrase("agarre ancho", "ancho")
        TechniqueModifier.UNILATERAL_EXECUTION -> TechniquePhrase("un lado cada vez", "unilateral")
        TechniqueModifier.BOX -> TechniquePhrase("sobre un cajón", "cajón")
        TechniqueModifier.PIN -> TechniquePhrase("desde los pines", "pines")
        TechniqueModifier.DEFICIT -> TechniquePhrase("con déficit", "déficit")
        TechniqueModifier.SPEED -> TechniquePhrase("a máxima velocidad", "velocidad")
        TechniqueModifier.CHAINS_BANDS -> TechniquePhrase("con cadenas o bandas", "cadena")
        TechniqueModifier.TOUCH_AND_GO -> TechniquePhrase("tocar y subir, sin pausa", "touch")
        TechniqueModifier.DEAD_STOP -> TechniquePhrase("cada repetición desde parado", "parada")
        TechniqueModifier.TO_KNEES -> TechniquePhrase("solo hasta las rodillas", "rodilla")
        TechniqueModifier.BLOCK_PULL -> TechniquePhrase("desde tacos", "tacos")
        TechniqueModifier.PRE_EXHAUST -> TechniquePhrase("aislamiento antes del compuesto", "agot")
    }

    // ─── Material ────────────────────────────────────────────────────────────

    /**
     * El material de todo el plan, no solo de la semana 1: la etiqueta del catálogo para el implemento,
     * el nombre del aparato curado del panel «¿Qué tienes disponible?» cuando la configuración es de una
     * máquina o polea concreta, y los soportes (banco, rack, barra de dominadas…). Sin receta no se afirma
     * nada: el material de los planes generados lo decide el motor con lo que tengas.
     */
    private fun material(recipe: TrainingPlanRecipe?, equipmentOf: (String) -> String?): List<String> {
        if (recipe == null) return emptyList()
        val ids = LinkedHashSet<String>()
        recipe.weeks.forEach { week -> week.days.forEach { day -> day.slots.forEach { slot -> ids.add(slot.lift.configurationId) } } }
        if (ids.isEmpty()) return emptyList()
        val labels = LinkedHashSet<String>()
        var allResolved = true
        for (configurationId in ids) {
            val key = configurationId.trim().lowercase()
            val equipmentId = equipmentOf(configurationId)
            if (equipmentId == null) allResolved = false
            val curated = EFFECTIVE_EQUIPMENT_KEYS.filter { key in it.machineConfigurations }.map { it.label }
            val machineIsCurated = curated.isNotEmpty() && (equipmentId == "machine" || equipmentId == "cable")
            if (equipmentId != null && equipmentId != "bodyweight" && !machineIsCurated) {
                labels.add(exerciseCatalogEquipmentLabel(equipmentId))
            }
            labels.addAll(curated)
            supportRequirementsFor(key).forEach { requirement -> supportLabel(requirement)?.let { labels.add(it) } }
        }
        return when {
            labels.isNotEmpty() -> labels.toList()
            allResolved -> listOf(BODYWEIGHT_ONLY)
            else -> emptyList()
        }
    }

    private fun supportLabel(requirement: String): String? = when (requirement) {
        REQUIREMENT_BENCH -> "Banco"
        REQUIREMENT_BENCH_INCLINE -> "Banco inclinable"
        REQUIREMENT_RACK -> "Rack de sentadilla"
        REQUIREMENT_PULL_UP_BAR -> "Barra de dominadas"
        REQUIREMENT_DIP_BARS -> "Paralelas"
        REQUIREMENT_LOW_BAR_SUPPORT -> "Barra baja estable"
        REQUIREMENT_BALL -> "Balón"
        REQUIREMENT_SUPPORT -> "Apoyo elevado"
        REQUIREMENT_NORDIC_ANCHOR -> "Anclaje para los pies"
        else -> null
    }

    // ─── Fuente ──────────────────────────────────────────────────────────────

    private fun sourceSection(entry: CatalogEntry): SourceSection? {
        val authored = entry.authoredSource
        val attribution = entry.attributionLine?.trim()?.takeIf { it.isNotEmpty() }
        val rawUrl = (entry.sourceUrl ?: authored?.sourceUrl)?.trim()
        val host = externalUrlHost(rawUrl)
        val url = if (host != null) rawUrl else null
        if (attribution == null && url == null && authored == null) return null
        return SourceSection(
            attributionLine = attribution,
            url = url,
            urlLabel = host?.let { "Ver la fuente original ($it)" },
            editionLine = authored?.editionWithConsult,
            authoredRules = authored?.effectiveRules?.takeIf { it.isNotEmpty() },
            kpknDefaults = authored?.kpknDefaults?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * El servidor de un enlace externo abrible (`http` o `https`), o null si no lo es: sin dirección,
     * con otro esquema o apuntando a `kpkn.fit`, que no existe como página de fuente.
     */
    fun externalUrlHost(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        val lower = trimmed.lowercase()
        val afterScheme = when {
            lower.startsWith("https://") -> trimmed.substring("https://".length)
            lower.startsWith("http://") -> trimmed.substring("http://".length)
            else -> return null
        }
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")
        if (host.isEmpty() || !host.contains('.')) return null
        if (host == "kpkn.fit" || host.endsWith(".kpkn.fit")) return null
        return host
    }
}
