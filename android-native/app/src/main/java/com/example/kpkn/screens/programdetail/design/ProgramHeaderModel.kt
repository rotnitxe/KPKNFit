package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.programdetail.components.focusModeLabel

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
