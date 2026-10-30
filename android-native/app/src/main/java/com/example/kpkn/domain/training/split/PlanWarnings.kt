package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.text.SpanishPlurals

/**
 * Avisos de un plan de reparto (R13). Funciones puras y baratas: se recalculan en cada plan, también tras arrastrar un día.
 *
 * - **Frecuencia** por grupo = sesiones con al menos 2 series ponderadas del grupo ([SplitMuscleGroups.frequency]).
 *   `FREQ_DOWN` por cada grupo principal que baja; `FREQ_UP` agrupa los que suben.
 * - **Días seguidos** (`ADJACENT_HEAVY`): dos sesiones en días consecutivos de la semana (el domingo no enlaza con el lunes)
 *   que cargan, las dos, al menos 3 series ponderadas del mismo grupo grande (pecho, espalda, cuádriceps, isquios, glúteos).
 *   Como en el lienzo, se muestran como mucho dos.
 * - `SHORT_DAY`: un día nuevo con menos de 3 ejercicios de fuerza. `TIME_OVER`: un día nuevo que pasa 1,10 × el tiempo pedido.
 */
object PlanWarnings {

    /** Grupos grandes cuya carga en días seguidos se avisa. */
    private val BIG_GROUPS = listOf("Pecho", "Espalda", "Cuádriceps", "Isquios", "Glúteos")

    /** Series ponderadas que cada una de las dos sesiones seguidas debe cargar para avisar. */
    private const val ADJACENT_SETS = 3.0

    /** Máximo de avisos de días seguidos que se muestran. */
    private const val MAX_ADJACENT = 2

    /** Ejercicios de fuerza mínimos por día nuevo. */
    private const val MIN_STRENGTH = 3

    internal class Input(
        val before: List<Session>,
        val after: List<Session>,
        /** Los días de la semana ya armados (con su sesión, para los avisos de días seguidos, cortos y de tiempo). */
        val days: List<DayPlan>,
        val resolver: ExerciseTraitResolver,
        val beforeWeekdays: Set<Int>,
        val afterWeekdays: Set<Int>,
        val targetMinutes: Int?,
        val misfits: List<String>,
        val unclassified: List<String>,
        val isDeload: Boolean,
        /** Días de entreno que pide el reparto frente a los de la semana de antes. */
        val splitDays: Int,
        val previousDays: Int,
    )

    internal fun evaluate(input: Input): List<PlanWarning> = buildList {
        if (input.splitDays != input.previousDays) {
            val added = (input.afterWeekdays - input.beforeWeekdays).sorted()
            val removed = (input.beforeWeekdays - input.afterWeekdays).sorted()
            add(
                PlanWarning(
                    WarningKind.DAYS_CHANGED,
                    WarningSeverity.ATTENTION,
                    mapOf("added" to added, "removed" to removed, "from" to input.previousDays, "to" to input.splitDays),
                ),
            )
        }
        val freqBefore = SplitMuscleGroups.frequency(input.before, input.resolver)
        val freqAfter = SplitMuscleGroups.frequency(input.after, input.resolver)
        SplitMuscleGroups.MAIN.forEach { group ->
            val from = freqBefore[group] ?: 0
            val to = freqAfter[group] ?: 0
            if (to < from) {
                add(PlanWarning(WarningKind.FREQ_DOWN, WarningSeverity.ATTENTION, mapOf("muscle" to group, "from" to from, "to" to to)))
            }
        }
        val up = SplitMuscleGroups.MAIN.filter { (freqAfter[it] ?: 0) > (freqBefore[it] ?: 0) }
        if (up.isNotEmpty()) add(PlanWarning(WarningKind.FREQ_UP, WarningSeverity.INFO, mapOf("muscles" to up)))

        addAll(adjacentHeavy(input))

        input.days.filter { it.kind == DayKind.OPEN }.forEach { day ->
            val strength = strengthCount(input.after.firstOrNull { it.id == day.sessionId })
            if (strength in 1 until MIN_STRENGTH) {
                add(PlanWarning(WarningKind.SHORT_DAY, WarningSeverity.ATTENTION, mapOf("title" to day.title, "count" to strength)))
            }
        }
        val target = input.targetMinutes
        if (target != null && !input.isDeload) {
            input.days.filter { it.kind == DayKind.OPEN && it.minutes > target * DayTimeFit.TOLERANCE }.forEach { day ->
                add(PlanWarning(WarningKind.TIME_OVER, WarningSeverity.ATTENTION, mapOf("title" to day.title, "minutes" to day.minutes, "target" to target)))
            }
        }
        if (input.unclassified.isNotEmpty()) {
            add(PlanWarning(WarningKind.UNCLASSIFIED, WarningSeverity.INFO, mapOf("names" to input.unclassified.distinct())))
        }
        if (input.misfits.isNotEmpty()) {
            add(PlanWarning(WarningKind.MISFIT, WarningSeverity.INFO, mapOf("names" to input.misfits.distinct())))
        }
    }

    private fun strengthCount(session: Session?): Int =
        session?.allExercises()?.count { it.cardioDetails == null && it.sets.isNotEmpty() } ?: 0

    private fun adjacentHeavy(input: Input): List<PlanWarning> {
        val sorted = input.days.sortedBy { it.weekday }
        val setsBySession = sorted.associate { day ->
            val session = input.after.firstOrNull { it.id == day.sessionId }
            day.sessionId to (session?.let { SplitMuscleGroups.setsBySession(listOf(it), input.resolver).first() } ?: emptyMap())
        }
        val out = mutableListOf<PlanWarning>()
        for (i in 0 until sorted.size - 1) {
            if (out.size >= MAX_ADJACENT) break
            val a = sorted[i]
            val b = sorted[i + 1]
            if (b.weekday - a.weekday != 1) continue
            val group = BIG_GROUPS.firstOrNull { name ->
                (setsBySession[a.sessionId]?.get(name) ?: 0.0) >= ADJACENT_SETS && (setsBySession[b.sessionId]?.get(name) ?: 0.0) >= ADJACENT_SETS
            } ?: continue
            out += PlanWarning(WarningKind.ADJACENT_HEAVY, WarningSeverity.ATTENTION, mapOf("a" to a.title, "b" to b.title, "muscle" to group))
        }
        return out
    }

    /** Frase en español del aviso (la pantalla puede escribir las suyas con [PlanWarning.params]). */
    fun sentence(warning: PlanWarning): String {
        fun <T> param(name: String): T {
            @Suppress("UNCHECKED_CAST")
            return warning.params.getValue(name) as T
        }
        return when (warning.kind) {
            WarningKind.DAYS_CHANGED -> {
                val to = param<Int>("to")
                val from = param<Int>("from")
                val added = param<List<Int>>("added")
                val removed = param<List<Int>>("removed")
                "Este reparto usa ${SpanishPlurals.days(to)} y tu semana tiene $from." +
                    (if (added.isNotEmpty()) " Añadimos ${daysPhrase(added)}." else "") +
                    (if (removed.isNotEmpty()) " Quitamos ${daysPhrase(removed)}." else "") +
                    " Arrastra una sesión si prefieres otro día."
            }
            WarningKind.FREQ_DOWN ->
                "Pierdes frecuencia en ${param<String>("muscle").lowercase()}: de ${param<Int>("from")}× a ${param<Int>("to")}× por semana."
            WarningKind.FREQ_UP ->
                "Ganas frecuencia en ${join(param<List<String>>("muscles").map { it.lowercase() })}: cada uno se entrena más veces por semana con las mismas series."
            WarningKind.ADJACENT_HEAVY ->
                "${param<String>("a")} y ${param<String>("b")} caen en días seguidos y los dos cargan ${param<String>("muscle").lowercase()}: solo 24 h de descanso."
            WarningKind.SHORT_DAY ->
                "${param<String>("title")} queda con ${SpanishPlurals.exercises(param("count"))}: no hay más que encajen con su foco."
            WarningKind.TIME_OVER ->
                "${param<String>("title")} dura unos ${param<Int>("minutes")} min y tu tiempo por sesión es de ${param<Int>("target")} min."
            WarningKind.UNCLASSIFIED ->
                "No se pudo clasificar ${param<List<String>>("names").joinToString(", ")}: quedó en el día más corto."
            WarningKind.MISFIT ->
                "Para llenar todos los días, ${param<List<String>>("names").joinToString(", ")} no encaja del todo con su día."
            WarningKind.LOCKED_KEPT -> {
                val count = param<List<String>>("sessionIds").size
                if (count == 1) "Una sesión ya hecha se queda como está y cuenta en el volumen de la semana."
                else "$count sesiones ya hechas se quedan como están y cuentan en el volumen de la semana."
            }
            WarningKind.SKIPPED_WEEK -> "Una semana no se toca: ${param<String>("reason")}."
            WarningKind.DELOAD_KEPT -> "La semana de descarga conserva su mitad de series."
        }
    }

    private fun daysPhrase(days: List<Int>): String = join(days.map { "el ${SplitDayMarks.WEEKDAY_NAMES[it - 1]}" })

    private fun join(items: List<String>): String =
        if (items.size < 2) items.joinToString("") else items.dropLast(1).joinToString(", ") + " y " + items.last()
}
