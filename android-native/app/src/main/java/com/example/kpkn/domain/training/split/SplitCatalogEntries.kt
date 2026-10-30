package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.cardDescription

// ─── Rol, color e inicial de un día ──────────────────────────────────────────────────────────────

/**
 * Rol de un día en la tira de la semana. Decide su color y su inicial; el descanso no lleva ninguno. La pantalla traduce
 * [paletteKey] a su color: `musculo` (empuje, amarillo), `columna` (tirón, azul), `energia` (pierna, coral), `mente`
 * (torso, lila) y `tinta` (cuerpo completo y el resto, crema).
 */
enum class DayRole(val paletteKey: String?) {
    REST(null),
    PUSH("musculo"),
    PULL("columna"),
    LEGS("energia"),
    TORSO("mente"),
    FULL_BODY("tinta"),
    MIX("tinta"),
}

/** Rol e inicial de un día: «E» empuje, «Ti» tirón, «Pi» pierna, «T» torso, «CC» cuerpo completo, «An»/«Po» cadenas. */
data class DayMark(val role: DayRole, val initial: String)

/** Una casilla de la tira de siete días: la letra del día de la semana, su etiqueta y su marca. */
data class StripCell(
    /** Día de la semana (1 = lunes … 7 = domingo). */
    val weekday: Int,
    /** `L M Mi J V S D`. */
    val letter: String,
    /** Etiqueta del reparto en ese día («Torso», «Descanso»). */
    val label: String,
    val mark: DayMark,
)

object SplitDayMarks {

    /** Iniciales de los días, lunes primero (1 = lunes): `L M Mi J V S D`, como el asistente. */
    val WEEKDAY_LETTERS: List<String> = listOf("L", "M", "Mi", "J", "V", "S", "D")

    /** Nombres de los días, lunes primero. */
    val WEEKDAY_NAMES: List<String> = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    fun letterOf(weekday: Int): String = WEEKDAY_LETTERS[(weekday - 1).coerceIn(0, 6)]

    private val REST_MARK = DayMark(DayRole.REST, "")

    /**
     * Rol e inicial de la etiqueta de un día. Las palabras de siempre mandan (empuje, tirón, pierna, torso, cuerpo
     * completo, cadenas); el resto toma el color del grupo que domina su foco («Pecho» y «Hombro/Brazo» son de empuje o de
     * torso, «Glúteo» de pierna) y la inicial de su propia etiqueta.
     */
    fun of(label: String): DayMark {
        val key = SplitText.normalize(label)
        if (key.isEmpty() || key == "descanso") return REST_MARK
        fun has(vararg words: String) = words.any { it in key }
        when {
            has("empuje", "push") -> return DayMark(DayRole.PUSH, "E")
            has("tiron", "pull") -> return DayMark(DayRole.PULL, "Ti")
            has("pierna", "legs") -> return DayMark(DayRole.LEGS, "Pi")
            has("torso", "upper", "superior") -> return DayMark(DayRole.TORSO, "T")
            has("lower", "inferior") -> return DayMark(DayRole.LEGS, "Pi")
            has("anterior", "posterior") -> return DayMark(DayRole.MIX, if ("anterior" in key) "An" else "Po")
            has("cuerpo completo", "full body") -> return DayMark(DayRole.FULL_BODY, "CC")
        }
        val focus = DayFocusParser.parse(label, emptyList())
        fun mean(vararg atoms: KpknMuscleGroup) = atoms.sumOf { focus.weight(it) } / atoms.size
        val push = mean(KpknMuscleGroup.CHEST, KpknMuscleGroup.DELT_FRONT, KpknMuscleGroup.DELT_LATERAL, KpknMuscleGroup.TRICEPS)
        val pull = mean(KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.BACK_UPPER, KpknMuscleGroup.DELT_REAR, KpknMuscleGroup.BICEPS)
        val legs = mean(KpknMuscleGroup.QUADS, KpknMuscleGroup.HAMS, KpknMuscleGroup.GLUTES, KpknMuscleGroup.CALVES)
        val role = when {
            focus.isGeneric -> DayRole.MIX
            legs >= 0.35 && push < 0.4 && pull < 0.4 -> DayRole.LEGS
            push >= 0.5 && pull < 0.4 && legs < 0.4 -> DayRole.PUSH
            pull >= 0.5 && push < 0.4 && legs < 0.4 -> DayRole.PULL
            push >= 0.4 && pull >= 0.4 && legs < 0.25 -> DayRole.TORSO
            else -> DayRole.MIX
        }
        return DayMark(role, initialOf(label))
    }

    private val LEADING_WORD = Regex("^(d[ií]a|sesi[oó]n)\\s+", RegexOption.IGNORE_CASE)
    private val NOT_ALNUM = Regex("[^A-Za-zÁÉÍÓÚáéíóúñÑ0-9]")

    private fun initialOf(label: String): String {
        val raw = NOT_ALNUM.replace(LEADING_WORD.replace(label.trim(), ""), "")
        val two = raw.take(2)
        return if (two.isEmpty()) "" else two.substring(0, 1).uppercase() + two.drop(1)
    }

    /**
     * La tira de siete casillas de un patrón de siete etiquetas (con «Descanso»), empezando en [startDay] (1 = lunes).
     * Las etiquetas que faltan cuentan como descanso.
     */
    fun strip(pattern: List<String>, startDay: Int = 1): List<StripCell> = (0 until 7).map { position ->
        val label = pattern.getOrNull(position)?.takeIf { it.isNotBlank() } ?: SplitCatalogRules.REST_LABEL
        val weekday = ((startDay.coerceIn(1, 7) - 1 + position) % 7) + 1
        StripCell(weekday, letterOf(weekday), label, of(label))
    }
}

// ─── Catálogo ────────────────────────────────────────────────────────────────────────────────────

/** Una tarjeta del catálogo de repartos: todo lo que muestra, ya calculado. */
data class SplitCatalogEntry(
    val id: String,
    /** Nombre llano («Torso y pierna, 4 días»). */
    val name: String,
    /** Nombre original de la plantilla («Upper / Lower x4»), solo para buscar. */
    val originalName: String,
    /** Descripción breve aprobada (una frase). */
    val description: String,
    val tags: List<SplitTag>,
    val level: Difficulty,
    val trainingDays: Int,
    val pattern: List<String>,
    /** Veces por semana que se entrena un músculo (mediana de los nueve grupos principales); null en powerlifting. */
    val timesPerMuscle: Int?,
    val pros: List<String>,
    val cons: List<String>,
    val recommended: Boolean,
    val powerlifting: Boolean,
) {
    /** La tira de la semana desde [startDay]. */
    fun strip(startDay: Int = 1): List<StripCell> = SplitDayMarks.strip(pattern, startDay)

    /** «Principiante», «Intermedio» o «Avanzado». */
    val levelLabel: String
        get() = when (level) {
            Difficulty.PRINCIPIANTE -> "Principiante"
            Difficulty.INTERMEDIO -> "Intermedio"
            Difficulty.AVANZADO -> "Avanzado"
        }
}

/** Filtros del catálogo: días por semana, etiqueta y texto de búsqueda (nombre, descripción, ventajas y precauciones). */
data class SplitCatalogFilter(
    val days: Int? = null,
    val tag: SplitTag? = null,
    val query: String = "",
)

/** «Ahora»: cómo está la semana abierta (su reparto actual y su tira de siete días). */
data class CatalogNow(
    /** Nombre del reparto en uso, o «Personalizado» si la semana no coincide con ninguno del catálogo. */
    val label: String,
    val trainingDays: Int,
    val strip: List<StripCell>,
)

/** Un grupo del catálogo con su título («Encajan con tus 4 días», «Cambian tus días»). */
data class CatalogGroup(val title: String, val entries: List<SplitCatalogEntry>, val changesDays: Boolean)

/** Lo que pinta la pantalla del catálogo: «Ahora» arriba y los repartos agrupados. Sin resultados, [groups] queda vacío. */
data class CatalogView(val now: CatalogNow?, val groups: List<CatalogGroup>) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/** Etiquetas del filtro y su texto, en el orden del lienzo. */
val CATALOG_TAG_LABELS: List<Pair<SplitTag, String>> = listOf(
    SplitTag.RECOMENDADO_KPKN to "Recomendado KPKN",
    SplitTag.ALTA_FRECUENCIA to "Alta frecuencia",
    SplitTag.BAJA_FRECUENCIA to "Baja frecuencia",
    SplitTag.BALANCEADO to "Balanceado",
    SplitTag.ALTO_VOLUMEN to "Alto volumen",
    SplitTag.ALTA_TOLERANCIA to "Alta tolerancia",
    SplitTag.POWERLIFTING to "Powerlifting",
)

/**
 * Datos de las tarjetas del catálogo de repartos (punto 23): descripción, veces por semana por músculo, nivel, tira de
 * días con color e inicial, filtro por días y la agrupación «Encajan con tus N días / Cambian tus días». Puro y
 * determinista; la pantalla solo pinta.
 */
object SplitCatalog {

    /** Los 35 repartos publicados (los que se pueden aplicar), en el orden del catálogo. */
    fun entries(): List<SplitCatalogEntry> = SplitCatalogRules.compatible(daysCount = null, profile = null).map(::entryOf)

    fun entry(splitId: String): SplitCatalogEntry? = entries().firstOrNull { it.id == splitId }

    fun entryOf(split: SplitTemplate): SplitCatalogEntry = SplitCatalogEntry(
        id = split.id,
        name = SplitCatalogRules.displayName(split),
        originalName = split.name,
        description = split.cardDescription,
        tags = split.tags,
        level = split.difficulty,
        trainingDays = SplitCatalogRules.trainingDays(split),
        pattern = split.pattern,
        timesPerMuscle = timesPerMuscle(split),
        pros = split.pros,
        cons = split.cons,
        recommended = SplitTag.RECOMENDADO_KPKN in split.tags,
        powerlifting = SplitTag.POWERLIFTING in split.tags,
    )

    /**
     * Días de entreno en los que el foco del día pide cada grupo (≥ 0,75 del mapa del motor; los días sin músculos
     * nombrados no cuentan). Es la frecuencia del reparto **sin ejercicios**: la de la comparación y de las tarjetas.
     */
    fun frequencyByGroup(split: SplitTemplate): Map<String, Int> {
        val days = SplitCatalogRules.trainingDefinitions(split).map { DayFocusParser.parse(it) }
        return SplitMuscleGroups.ALL.associate { group ->
            group.label to days.count { day -> !day.isGeneric && group.atoms.maxOf { day.weight(it) } >= DAY_ASKS_FOR_GROUP }
        }
    }

    /** Mediana de [frequencyByGroup] en los nueve grupos principales; null en powerlifting (sus días son levantamientos). */
    fun timesPerMuscle(split: SplitTemplate): Int? {
        if (SplitTag.POWERLIFTING in split.tags) return null
        val byGroup = frequencyByGroup(split)
        val values = SplitMuscleGroups.MAIN.map { byGroup.getValue(it) }.sorted()
        return values[values.size / 2]
    }

    /** ¿El reparto pasa el filtro? Busca el texto en nombre llano, nombre original, descripción, ventajas y precauciones. */
    fun matches(entry: SplitCatalogEntry, filter: SplitCatalogFilter): Boolean {
        if (filter.tag != null && filter.tag !in entry.tags) return false
        if (filter.days != null && entry.trainingDays != filter.days) return false
        val query = filter.query.trim().lowercase()
        if (query.isEmpty()) return true
        val haystack = (listOf(entry.name, entry.originalName, entry.description) + entry.pros + entry.cons).joinToString(" ").lowercase()
        return query in haystack
    }

    /**
     * El catálogo para la pantalla. Con [haveDays] (los días de entreno de la semana abierta): primero «Encajan con tus N
     * días» y después «Cambian tus días» (o «Piden otro número de días» si ninguno encaja); sin él, una sola lista.
     * [now] sale de [now] (la semana abierta) y no se muestra mientras se busca.
     */
    fun view(filter: SplitCatalogFilter = SplitCatalogFilter(), haveDays: Int? = null, now: CatalogNow? = null): CatalogView {
        val list = entries().filter { matches(it, filter) }
        val searching = filter.query.isNotBlank()
        val shownNow = if (searching) null else now
        if (list.isEmpty()) return CatalogView(shownNow, emptyList())
        if (haveDays == null || haveDays <= 0) return CatalogView(shownNow, listOf(CatalogGroup("Repartos", list, false)))
        val fit = list.filter { it.trainingDays == haveDays }
        val other = list.filter { it.trainingDays != haveDays }
        val groups = buildList {
            if (fit.isNotEmpty()) add(CatalogGroup("Encajan con tus $haveDays días", fit, false))
            if (other.isNotEmpty()) {
                add(CatalogGroup(if (fit.isNotEmpty()) "Cambian tus días" else "Piden otro número de días", other, true))
            }
        }
        return CatalogView(shownNow, groups)
    }

    /**
     * «Ahora»: el reparto de la semana [weekId] de [program] (la primera de entreno si es null). El nombre es el del
     * reparto elegido para esa semana solo si sus días coinciden con los de la semana; si no, «Personalizado».
     */
    fun now(program: Program, weekId: String?, startDay: Int = 1): CatalogNow? {
        val ref = ProgramWeeks.all(program).firstOrNull { if (weekId != null) it.week.id == weekId else ProgramWeeks.isEffective(it.week) }
            ?: return null
        val sessions = ref.week.sessions
        val byDay = LinkedHashMap<Int, Session>()
        sessions.forEach { session ->
            val day = session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 }
            if (day != null) byDay.putIfAbsent(day, session)
        }
        if (byDay.isEmpty()) return null
        val first = startDay.coerceIn(1, 7)
        val pattern = (0 until 7).map { position ->
            val weekday = ((first - 1 + position) % 7) + 1
            byDay[weekday]?.let { it.scheduleLabel?.takeIf { label -> label.isNotBlank() } ?: it.name } ?: SplitCatalogRules.REST_LABEL
        }
        val chosen = ref.week.id.let { id ->
            program.weekSplitSelections[id] ?: program.selectedSplitId
        }?.let { SplitCatalogRules.displayName(it) }
        val chosenDays = (program.weekSplitSelections[ref.week.id] ?: program.selectedSplitId)
            ?.let { id -> entry(id)?.trainingDays }
        val label = if (chosen != null && chosenDays == byDay.size) chosen else PERSONALIZED
        return CatalogNow(label, byDay.size, SplitDayMarks.strip(pattern, first))
    }

    const val PERSONALIZED = "Personalizado"

    /** Un día «pide» un grupo cuando su mapa de foco le da al menos este peso. */
    private const val DAY_ASKS_FOR_GROUP = 0.75
}
