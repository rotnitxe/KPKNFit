package com.example.kpkn.screens.onboarding.design.entreno.layout

import com.example.kpkn.domain.text.SpanishPlurals

/*
 * Modelos, marcas de prueba y textos del tablero de la semana («Así queda tu semana»). Es un paquete visual puro:
 * recibe datos ya resueltos y avisa de lo que la persona hace; quien lo usa decide qué escribe y qué recalcula.
 */

/**
 * Una sesión del programa tal como la muestra el tablero: un título corto, su foco («Pecho y espalda»), cuánto dura,
 * cuántos ejercicios tiene y si es la principal (la que cae el día de más energía).
 */
data class WeekLayoutSession(
    val id: String,
    val title: String,
    val focus: String,
    val minutes: Int,
    val exerciseCount: Int,
    val isMain: Boolean,
)

/**
 * Un reparto al que se puede adaptar el programa («Torso y pierna»). [dayTitles] lleva un título corto por cada día de
 * entreno del reparto, en orden: de ahí sale el patrón de la mini-semana de su símbolo.
 */
data class SplitOption(
    val id: String,
    val name: String,
    val summary: String,
    val dayTitles: List<String>,
)

// ---------------------------------------------------------------- marcas de prueba

internal const val WEEK_LAYOUT_BOARD_TAG = "setup-layout-board"
internal const val WEEK_LAYOUT_STRIP_TAG = "setup-layout-strip"
internal const val WEEK_LAYOUT_STATUS_TAG = "setup-layout-status"
internal const val WEEK_LAYOUT_RAIL_TAG = "setup-layout-splits"
internal const val WEEK_LAYOUT_ADAPT_TAG = "setup-layout-adapt"
internal const val WEEK_LAYOUT_RESET_TAG = "setup-layout-reset"
internal const val WEEK_LAYOUT_ADAPTING_TAG = "setup-layout-adapting"

/** Marca de prueba de la ficha de una sesión: `setup-layout-session-<id>`. */
internal fun weekLayoutSessionTag(id: String): String = "setup-layout-session-$id"

/** Marca de prueba de la ranura de un día (1 = lunes … 7 = domingo): `setup-layout-slot-<día>`. */
internal fun weekLayoutSlotTag(day: Int): String = "setup-layout-slot-$day"

/** Marca de prueba del símbolo de un reparto: `setup-layout-split-<id>`. */
internal fun weekLayoutSplitTag(id: String): String = "setup-layout-split-$id"

// ---------------------------------------------------------------- textos

internal object WeekLayoutCopy {
    const val ADAPT = "Adaptar mi programa a este reparto"
    const val RESET = "Restablecer"
    const val FOOTNOTE = "Puedes cambiar todo esto cuando quieras desde tu programa."
    const val ADAPTING = "Adaptando tu semana…"
    const val SPLITS_LABEL = "Repartos para tu semana"
    const val REST = "Descanso"
    const val HINT_IDLE = "Mantén pulsada una sesión y arrástrala, o tócala y elige un día."
    const val HINT_DRAGGING = "Suéltala sobre un día. Fuera de la semana se cancela."
    const val HINT_EMPTY = "Todavía no hay sesiones que colocar."
    const val CURRENT_SPLIT = "reparto actual"

    /** «Toca el día al que quieres mover «Torso A».» */
    fun hintSelected(title: String): String = "Toca el día al que quieres mover «$title»."

    /** «Suéltala en martes.» */
    fun hintDropOn(day: Int): String = "Suéltala en ${weekDayName(day).lowercase()}."
}

private val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val DAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** El miércoles es «X»: así no se confunde con el martes (la misma convención de los calendarios en español). */
private val DAY_INITIALS = listOf("L", "M", "X", "J", "V", "S", "D")

/** Nombre completo de un día (1 = lunes … 7 = domingo); un día fuera de rango cuenta como lunes. */
internal fun weekDayName(day: Int): String = DAY_NAMES[safeDayIndex(day)]

/** Nombre corto: «Lun», «Mié»… */
internal fun weekDayShort(day: Int): String = DAY_SHORT[safeDayIndex(day)]

/** Inicial de un día: «L M X J V S D». */
internal fun weekDayInitial(day: Int): String = DAY_INITIALS[safeDayIndex(day)]

private fun safeDayIndex(day: Int): Int = if (day in 1..7) day - 1 else 0

/** «60 min». */
internal fun sessionMinutesText(minutes: Int): String = "$minutes min"

/** «6 ejercicios» / «1 ejercicio». */
internal fun sessionExercisesText(count: Int): String = SpanishPlurals.exercises(count)

/**
 * Lo que anuncia TalkBack de una sesión: «Torso A, sesión principal. Pecho y espalda. 60 minutos, 6 ejercicios. Lunes.»
 * El foco solo se lee si aporta algo (no es igual al título) y el día solo si la sesión está colocada.
 */
internal fun sessionDescription(session: WeekLayoutSession, day: Int?): String {
    val parts = mutableListOf<String>()
    parts += if (session.isMain) "${session.title}, sesión principal" else session.title
    if (session.focus.isNotBlank() && !session.focus.equals(session.title, ignoreCase = true)) parts += session.focus
    val amounts = listOfNotNull(
        if (session.minutes > 0) SpanishPlurals.withNoun(session.minutes, "minuto", "minutos") else null,
        if (session.exerciseCount > 0) SpanishPlurals.exercises(session.exerciseCount) else null,
    )
    if (amounts.isNotEmpty()) parts += amounts.joinToString(", ")
    if (day != null) parts += weekDayName(day)
    return parts.joinToString(". ") + "."
}

/**
 * La frase de confirmación cuando cambia la colocación de la semana: «Torso A pasa al martes.» o, en un intercambio,
 * «Torso A pasa al martes y Torso B pasa al lunes.». Null si ninguna sesión cambió de día.
 */
internal fun moveAnnouncement(
    sessions: List<WeekLayoutSession>,
    before: Map<Int, String>,
    after: Map<Int, String>,
): String? {
    val titles = sessions.associate { it.id to it.title }
    val programOrder = sessions.withIndex().associate { it.value.id to it.index }
    val dayBefore = before.entries.associate { it.value to it.key }
    // En el orden del programa (no en el de los días), así la primera sesión se nombra primero.
    val phrases = after.entries
        .filter { (day, id) -> dayBefore[id] != null && dayBefore[id] != day }
        .sortedBy { (_, id) -> programOrder[id] ?: Int.MAX_VALUE }
        .mapNotNull { (day, id) -> titles[id]?.let { "$it pasa al ${weekDayName(day).lowercase()}" } }
    return when (phrases.size) {
        0 -> null
        1 -> phrases[0] + "."
        else -> phrases.dropLast(1).joinToString(", ") + " y " + phrases.last() + "."
    }
}
