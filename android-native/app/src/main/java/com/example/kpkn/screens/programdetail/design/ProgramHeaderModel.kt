package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.programdetail.components.focusModeLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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

/** La acción principal de la cabecera (lienzo v6: p-today, p-paused, p-rest). */
internal sealed interface ProgramHeaderPrimary {
    /** «Empezar · <sesión de hoy>». */
    data class Start(val sessionName: String) : ProgramHeaderPrimary

    /** «Continuar · <sesión>»: la sesión de hoy ya está en curso (C-D5: el patrón de «Empezar · Tirón»). */
    data class Continue(val sessionName: String) : ProgramHeaderPrimary

    /** «Reanudar»: el programa está pausado (p-paused). */
    object Resume : ProgramHeaderPrimary

    /** «Activar programa»: el programa es un borrador (no está activo ni pausado). */
    object Activate : ProgramHeaderPrimary

    /** Sin acción principal: hoy no toca sesión pendiente. */
    object None : ProgramHeaderPrimary
}

/**
 * Elige la acción principal. [sessionName] es la sesión de hoy que queda por hacer (o en curso) de este programa; null si hoy no
 * queda ninguna. Pausado, siempre «Reanudar»; sin programa activo, «Activar programa».
 */
internal fun programHeaderPrimaryOf(
    isActive: Boolean,
    sessionName: String?,
    sessionOngoing: Boolean,
    isPaused: Boolean = false,
): ProgramHeaderPrimary = when {
    isPaused -> ProgramHeaderPrimary.Resume
    !isActive -> ProgramHeaderPrimary.Activate
    sessionName == null -> ProgramHeaderPrimary.None
    sessionOngoing -> ProgramHeaderPrimary.Continue(sessionName)
    else -> ProgramHeaderPrimary.Start(sessionName)
}

/** Texto de la acción principal; null si no hay acción. */
internal fun programHeaderPrimaryLabel(primary: ProgramHeaderPrimary): String? = when (primary) {
    is ProgramHeaderPrimary.Start -> "Empezar · ${primary.sessionName}"
    is ProgramHeaderPrimary.Continue -> "Continuar · ${primary.sessionName}"
    ProgramHeaderPrimary.Resume -> "Reanudar"
    ProgramHeaderPrimary.Activate -> "Activar programa"
    ProgramHeaderPrimary.None -> null
}

/** Etiqueta del modo en la meta, con el vocabulario del asistente. Null si el modo no se conoce: nunca se inventa uno. */
internal fun programModeLabel(mode: ProgramMode?): String? = mode?.let(::focusModeLabel)

/**
 * La meta de la cabecera: «Fuerza · 12 semanas · 3 bloques»; en una rutina, «Fuerza · Rutina · 4 días». El modo va primero y lo que no
 * se sabe se omite. Un programa de una sola semana dice «1 semana» y solo cuenta bloques si hay más de uno.
 */
internal fun programHeadlineMeta(
    goalLabel: String?,
    weekCount: Int,
    blockCount: Int,
    isRoutine: Boolean,
    daysPerWeek: Int?,
): String {
    val parts = mutableListOf<String?>(goalLabel?.takeIf { it.isNotBlank() })
    if (isRoutine) {
        parts += "Rutina"
        parts += daysPerWeek?.takeIf { it > 0 }?.let { numberWithUnit(SpanishPlurals.days(it)) }
    } else {
        parts += weekCount.takeIf { it > 0 }?.let { numberWithUnit(SpanishPlurals.weeks(it)) }
        parts += blockCount.takeIf { it > 1 }?.let { numberWithUnit(SpanishPlurals.blocks(it)) }
    }
    return parts.filterNotNull().joinToString(" · ")
}

/**
 * Une la cifra con su unidad con un espacio sin corte («12 semanas», «4 días»): la cifra nunca queda al final de una línea sin su
 * palabra.
 */
internal fun numberWithUnit(text: String): String = text.replaceFirst(' ', NBSP)

/** Espacio sin corte entre una cifra y su palabra. */
private const val NBSP = ' '

/**
 * «Semana 3 de 12 · Acumulación»; en un programa de semana repetida, «Semana 5» (su ciclo). Null si no se sabe en qué semana va.
 */
internal fun programProgressLabel(
    weekIndex: Int?,
    weekCount: Int?,
    phase: String?,
    repeatingWeek: Boolean,
    cycle: Int?,
): String? {
    if (repeatingWeek) return cycle?.let { "Semana $it" }
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
 * La sesión que toca después de hoy en la semana que se ve: su día (1 = lunes … 7 = domingo), la etiqueta de la píldora («Siguiente:
 * jueves · Pierna A», p-rest) y lo que lee TalkBack («Siguiente sesión: jueves, Pierna A»).
 */
internal data class ProgramNextSession(val dayOfWeek: Int, val label: String, val description: String)

private val SPANISH_LOCALE: Locale = Locale("es")

/**
 * «Siguiente: jueves · Pierna A»: la sesión que toca después de [today] (1 = lunes … 7 = domingo), dando la vuelta a la semana. La del
 * propio día cuenta solo dentro de siete días. [sessions] son pares (día, nombre); las que no tienen día se ignoran. Null si ninguna.
 */
internal fun programNextSessionOf(sessions: List<Pair<Int, String>>, today: Int): ProgramNextSession? {
    val next = sessions
        .filter { it.first in 1..7 }
        .minByOrNull { daysUntil(it.first, today) }
        ?: return null
    val dayName = DayOfWeek.of(next.first).getDisplayName(TextStyle.FULL, SPANISH_LOCALE).lowercase(SPANISH_LOCALE)
    val name = next.second.trim()
    val label = if (name.isEmpty()) "Siguiente: $dayName" else "Siguiente: $dayName · $name"
    val description = if (name.isEmpty()) "Siguiente sesión: $dayName" else "Siguiente sesión: $dayName, $name"
    return ProgramNextSession(dayOfWeek = next.first, label = label, description = description)
}

/** Días desde [today] hasta [day] dentro de la semana: 1 para el día siguiente y 7 para el mismo día de la semana siguiente. */
private fun daysUntil(day: Int, today: Int): Int {
    val distance = ((day - today) % 7 + 7) % 7
    return if (distance == 0) 7 else distance
}

/** La línea «Hoy también» bajo la fila de acciones: otra sesión de hoy y su duración estimada. */
internal fun programTodayAlsoLine(sessionName: String, minutes: Int): String = "Hoy también: $sessionName · ≈ $minutes min"

/**
 * La línea bajo el nombre en la hoja ⋯ del programa (m-program): «Activo · semana 4 de 6»; sin total (una rutina), «Activo · semana 7»;
 * sin semana conocida, solo el estado.
 */
internal fun programMenuStatusLine(status: ProgramHeaderStatus, weekIndex: Int?, weekCount: Int?): String {
    val state = programHeaderStatusLabel(status)
    val week = weekIndex?.takeIf { it > 0 } ?: return state
    return if (weekCount != null && weekCount > 1) "$state · semana $week de $weekCount" else "$state · semana $week"
}

/** La fila de pausar de ⋯ (m-program) y sus variantes (C-D2): título y subtítulo según el estado del programa. */
internal data class ProgramPauseRow(val title: String, val detail: String?)

/**
 * «Pausar programa» / «Se guarda en la semana 4» con el programa activo; pausado, «Reanudar programa» / «Sigue en la semana 4»; en
 * borrador, «Activar programa» / «Pasa a ser tu programa activo». Sin semana conocida, el activo y el pausado van sin subtítulo.
 */
internal fun programPauseRow(status: ProgramHeaderStatus, weekIndex: Int?): ProgramPauseRow {
    val week = weekIndex?.takeIf { it > 0 }
    return when (status) {
        ProgramHeaderStatus.ACTIVE -> ProgramPauseRow("Pausar programa", week?.let { "Se guarda en la semana $it" })
        ProgramHeaderStatus.PAUSED -> ProgramPauseRow("Reanudar programa", week?.let { "Sigue en la semana $it" })
        ProgramHeaderStatus.DRAFT -> ProgramPauseRow("Activar programa", "Pasa a ser tu programa activo")
    }
}
