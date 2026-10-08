package com.example.kpkn.screens.onboarding.design.entreno.layout

import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.text.SpanishPlurals

/*
 * Modelos, marcas de prueba y textos del tablero de la semana («Así queda tu semana»). Es un paquete visual puro:
 * recibe datos ya resueltos y avisa de lo que la persona hace; quien lo usa decide qué escribe y qué recalcula.
 */

/**
 * Una sesión del programa tal como la muestra el tablero: un título corto, su foco («Pecho y espalda»), cuánto dura,
 * cuántos ejercicios tiene y si es la principal (la que cae el día de más energía).
 *
 * [place] es el lugar donde se entrena la sesión cuando el programa reparte sus sesiones entre varios lugares (null = no
 * hace falta decirlo). Quien arma las sesiones puede no rellenarlo y escribirlo al final del [focus] («Pecho y espalda · En
 * casa»), como hace el paso WEEK_LAYOUT: [shownPlace] lee las dos formas.
 */
data class WeekLayoutSession(
    val id: String,
    val title: String,
    val focus: String,
    val minutes: Int,
    val exerciseCount: Int,
    val isMain: Boolean,
    val place: TrainingPlace? = null,
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

/** La lista de los siete días (todas las filas y las sesiones): es lo que recibe los gestos de arrastrar y tocar. */
internal const val WEEK_LAYOUT_LIST_TAG = "setup-layout-list"
internal const val WEEK_LAYOUT_STATUS_TAG = "setup-layout-status"
internal const val WEEK_LAYOUT_RAIL_TAG = "setup-layout-splits"
internal const val WEEK_LAYOUT_ADAPT_TAG = "setup-layout-adapt"
internal const val WEEK_LAYOUT_RESET_TAG = "setup-layout-reset"
internal const val WEEK_LAYOUT_ADAPTING_TAG = "setup-layout-adapting"

/** Marca de prueba de la ficha de una sesión: `setup-layout-session-<id>`. */
internal fun weekLayoutSessionTag(id: String): String = "setup-layout-session-$id"

/** Marca de prueba de la fila de un día (1 = lunes … 7 = domingo): `setup-layout-slot-<día>`. */
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
    const val HINT_IDLE = "Arrastra una sesión por su asa, o tócala y elige un día."
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

/** Nombre completo de un día (1 = lunes … 7 = domingo); un día fuera de rango cuenta como lunes. */
internal fun weekDayName(day: Int): String = DAY_NAMES[safeDayIndex(day)]

/** Nombre corto: «Lun», «Mié»… */
internal fun weekDayShort(day: Int): String = DAY_SHORT[safeDayIndex(day)]

private fun safeDayIndex(day: Int): Int = if (day in 1..7) day - 1 else 0

/** «60 min». */
internal fun sessionMinutesText(minutes: Int): String = "$minutes min"

/** «6 ejercicios» / «1 ejercicio». */
internal fun sessionExercisesText(count: Int): String = SpanishPlurals.exercises(count)

/** «60 min · 6 ejercicios»: lo que hay de cada cantidad (sin minutos o sin ejercicios, solo la otra). */
internal fun sessionDetailText(session: WeekLayoutSession): String = listOfNotNull(
    session.minutes.takeIf { it > 0 }?.let(::sessionMinutesText),
    session.exerciseCount.takeIf { it > 0 }?.let(::sessionExercisesText),
).joinToString(" · ")

/**
 * El lugar de la sesión: el que trae [WeekLayoutSession.place] o, si no, el que lleva escrito al final de su foco
 * («Pecho y espalda · En casa»). Null si no hay lugar que decir.
 */
internal fun WeekLayoutSession.shownPlace(): TrainingPlace? {
    place?.let { return it }
    val candidates = listOf(focus.trim(), focus.substringAfterLast(PLACE_SEPARATOR, "").trim())
    return candidates.firstNotNullOfOrNull { text ->
        TrainingPlace.entries.firstOrNull { it.label.equals(text, ignoreCase = true) }
    }
}

/** Lo que separa el foco de su lugar en el texto que arma el paso WEEK_LAYOUT. */
private const val PLACE_SEPARATOR = " · "

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
