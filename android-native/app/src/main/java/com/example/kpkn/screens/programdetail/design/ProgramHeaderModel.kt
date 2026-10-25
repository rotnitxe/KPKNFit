package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ProgramScopes
import com.example.kpkn.screens.programdetail.components.focusModeLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Textos y decisiones de la cabecera de Program Detail, sin Compose: la prueba en la JVM decide qué dice cada caso.
 */

/** Estado del programa en la cabecera: su eyebrow y el color de su punto. */
internal enum class ProgramHeaderStatus { ACTIVE, PAUSED, DRAFT }

/** Activo si corre, pausado si está pausado y borrador en cualquier otro caso (como antes). */
internal fun programHeaderStatusOf(isActive: Boolean, isPaused: Boolean): ProgramHeaderStatus = when {
    isActive && !isPaused -> ProgramHeaderStatus.ACTIVE
    isPaused -> ProgramHeaderStatus.PAUSED
    else -> ProgramHeaderStatus.DRAFT
}

/** Texto del eyebrow de estado (la pantalla lo pone en mayúsculas). */
internal fun programHeaderStatusLabel(status: ProgramHeaderStatus): String = when (status) {
    ProgramHeaderStatus.ACTIVE -> "Activo"
    ProgramHeaderStatus.PAUSED -> "Pausado"
    ProgramHeaderStatus.DRAFT -> "Borrador"
}

/** La acción principal de la cabecera (KPKN_UI_V2 §6). */
internal sealed interface ProgramHeaderPrimary {
    /** «Empezar <sesión de hoy>». */
    data class Start(val sessionName: String) : ProgramHeaderPrimary

    /** «Continuar sesión»: la sesión de hoy ya está en curso. */
    object Continue : ProgramHeaderPrimary

    /** «Activar programa»: el programa no está activo (si está pausado, activar lo reanuda). */
    object Activate : ProgramHeaderPrimary

    /** Sin acción principal: hoy no toca sesión pendiente. */
    object None : ProgramHeaderPrimary
}

/**
 * Elige la acción principal. [sessionName] es la sesión de hoy que queda por hacer (o en curso) de este programa; null si hoy no
 * queda ninguna. Sin programa activo, siempre «Activar programa».
 */
internal fun programHeaderPrimaryOf(
    isActive: Boolean,
    sessionName: String?,
    sessionOngoing: Boolean,
): ProgramHeaderPrimary = when {
    !isActive -> ProgramHeaderPrimary.Activate
    sessionName == null -> ProgramHeaderPrimary.None
    sessionOngoing -> ProgramHeaderPrimary.Continue
    else -> ProgramHeaderPrimary.Start(sessionName)
}

/** Texto de la acción principal; null si no hay acción. */
internal fun programHeaderPrimaryLabel(primary: ProgramHeaderPrimary): String? = when (primary) {
    is ProgramHeaderPrimary.Start -> "Empezar ${primary.sessionName}"
    ProgramHeaderPrimary.Continue -> "Continuar sesión"
    ProgramHeaderPrimary.Activate -> "Activar programa"
    ProgramHeaderPrimary.None -> null
}

/** Etiqueta del modo en la meta, con el vocabulario del asistente. Null si el modo no se conoce: nunca se inventa uno. */
internal fun programModeLabel(mode: ProgramMode?): String? = mode?.let(::focusModeLabel)

/** Línea de alcance del programa con el vocabulario único: «Una semana que se repite», «8 semanas» o «Por bloques · 3 bloques · 12 semanas». */
internal fun programScopeLine(program: Program): String = ProgramScopes.label(ProgramScopes.of(program))

/** Recordatorio por calendario para rutinas propias repetidas, sin inferir fatiga ni modificar su contenido. */
internal fun repeatingCycleDeloadNotice(program: Program): String? {
    if (program.sourceRecipe != null || program.sourceProtocolId != null || !program.isSimpleProgram ||
        program.simpleProgramKind != SimpleProgramKind.CYCLIC) return null
    val cycle = program.runState?.cycleNumber ?: return null
    return if (cycle > 0 && cycle % 6 == 0)
        "Ciclo $cycle: puedes planificar una semana de descarga con la mitad de las series y 4 repeticiones en reserva."
    else null
}

/**
 * «12 semanas · 4 días por semana · Fuerza». Lo que no se sabe se omite (nada de «—» en la meta). Un programa de una sola semana que
 * se repite dice «Semana que se repite» en vez de «1 semana».
 */
internal fun programMetaLine(weekCount: Int, repeatingWeek: Boolean, daysPerWeek: Int?, mode: ProgramMode?): String =
    listOfNotNull(
        when {
            repeatingWeek -> "Semana que se repite"
            weekCount > 0 -> numberWithUnit(SpanishPlurals.weeks(weekCount))
            else -> null
        },
        daysPerWeek?.takeIf { it > 0 }?.let { numberWithUnit(SpanishPlurals.days(it)) + " por semana" },
        programModeLabel(mode),
    ).joinToString(" · ")

/**
 * Une la cifra con su unidad con un espacio sin corte («12 semanas», «4 días»): la cifra nunca queda al final de una línea sin su
 * palabra.
 */
internal fun numberWithUnit(text: String): String = text.replaceFirst(' ', NBSP)

/** Espacio sin corte entre una cifra y su palabra. */
private const val NBSP = ' '

/**
 * «Semana 3 de 12 · Acumulación»; en un programa de semana repetida, «Semana que se repite · ciclo 5». Null si no se sabe en qué
 * semana va.
 */
internal fun programProgressLabel(
    weekIndex: Int?,
    weekCount: Int?,
    phase: String?,
    repeatingWeek: Boolean,
    cycle: Int?,
): String? {
    if (repeatingWeek) return cycle?.let { "Semana que se repite · ciclo" + NBSP + it }
    if (weekIndex == null || weekCount == null) return null
    val week = "Semana $weekIndex de $weekCount"
    return shortPhaseOf(phase)?.let { "$week · $it" } ?: week
}

/** Fase corta de la línea de Home («Fase de acumulación» → «Acumulación»); null si no es la fase de un bloque. */
internal fun shortPhaseOf(phase: String?): String? =
    phase?.takeIf { it.startsWith(PHASE_PREFIX) }
        ?.removePrefix(PHASE_PREFIX)
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercaseChar() }

private const val PHASE_PREFIX = "Fase de "

/**
 * La sesión que toca después de hoy en la semana que se ve: su día (1 = lunes … 7 = domingo), la etiqueta corta de la píldora («Jueves ·
 * Pierna A») y lo que lee TalkBack («Siguiente sesión: jueves, Pierna A»).
 */
internal data class ProgramNextSession(val dayOfWeek: Int, val label: String, val description: String)

private val SPANISH_LOCALE: Locale = Locale("es")

/**
 * «Jueves · Pierna A»: la sesión que toca después de [today] (1 = lunes … 7 = domingo), dando la vuelta a la semana. La del
 * propio día cuenta solo dentro de siete días. [sessions] son pares (día, nombre); las que no tienen día se ignoran. Null si ninguna.
 */
internal fun programNextSessionOf(sessions: List<Pair<Int, String>>, today: Int): ProgramNextSession? {
    val next = sessions
        .filter { it.first in 1..7 }
        .minByOrNull { daysUntil(it.first, today) }
        ?: return null
    val dayName = DayOfWeek.of(next.first).getDisplayName(TextStyle.FULL, SPANISH_LOCALE).lowercase(SPANISH_LOCALE)
    val name = next.second.trim()
    val capitalDay = dayName.replaceFirstChar { it.uppercase(SPANISH_LOCALE) }
    val label = if (name.isEmpty()) capitalDay else "$capitalDay · $name"
    val description = if (name.isEmpty()) "Siguiente sesión: $dayName" else "Siguiente sesión: $dayName, $name"
    return ProgramNextSession(dayOfWeek = next.first, label = label, description = description)
}

/** Días desde [today] hasta [day] dentro de la semana: 1 para el día siguiente y 7 para el mismo día de la semana siguiente. */
private fun daysUntil(day: Int, today: Int): Int {
    val distance = ((day - today) % 7 + 7) % 7
    return if (distance == 0) 7 else distance
}

/**
 * Subtítulo de la semana de un programa simple, en lugar de su nombre («Semana repetible»): «4 sesiones · ≈ 61 min de media». La media
 * solo cuenta las sesiones con duración; sin ninguna, dice solo las sesiones. Sin sesiones, null.
 */
internal fun repeatingWeekSubtitle(sessionMinutes: List<Int>): String? {
    if (sessionMinutes.isEmpty()) return null
    val sessions = SpanishPlurals.sessions(sessionMinutes.size)
    val timed = sessionMinutes.filter { it > 0 }
    return if (timed.isEmpty()) sessions else "$sessions · ≈ ${timed.average().roundToInt()} min de media"
}
