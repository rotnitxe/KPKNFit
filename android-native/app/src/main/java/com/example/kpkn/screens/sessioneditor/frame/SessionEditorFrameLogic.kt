package com.example.kpkn.screens.sessioneditor.frame

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant

/** Tono del anillo de tiempo de la barra: `ok` por debajo del 85 %, Energía hasta el 108 % y `danger` por encima (E21 del original). */
internal enum class FrameRingTone { NEUTRAL, OK, ENERGIA, DANGER }

/**
 * Tono del anillo: [estimatedMinutes] frente a [limitMinutes]. Sin límite, neutro. Las comparaciones van en enteros (×100) para que
 * el 85 % y el 108 % caigan exactos: 85 de 100 ya es Energía; 108 de 100 todavía, y 109 de 100 ya es `danger`.
 */
internal fun frameTimeRingTone(estimatedMinutes: Int, limitMinutes: Int?): FrameRingTone {
    if (limitMinutes == null || limitMinutes <= 0) return FrameRingTone.NEUTRAL
    val scaled = estimatedMinutes * 100L
    return when {
        scaled > limitMinutes * 108L -> FrameRingTone.DANGER
        scaled >= limitMinutes * 85L -> FrameRingTone.ENERGIA
        else -> FrameRingTone.OK
    }
}

/** Parte del anillo que se pinta: estimado frente al límite, entre 0 y 1. Sin límite, nada. */
internal fun frameTimeRingFraction(estimatedMinutes: Int, limitMinutes: Int?): Float {
    if (limitMinutes == null || limitMinutes <= 0) return 0f
    return (estimatedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
}

/** Orden de los días de la semana empezando por [weekStartDay] (1 = lunes), como el navegador del original. */
internal fun frameWeekOrder(weekStartDay: Int): List<Int> {
    val safeStart = weekStartDay.coerceIn(1, 7)
    val base = listOf(1, 2, 3, 4, 5, 6, 7)
    val offset = safeStart - 1
    return base.drop(offset) + base.take(offset)
}

/** Día vecino con sesión: el primero en la dirección [step] (+1 siguiente, −1 anterior) saltando los días vacíos. Null si no hay. */
internal fun neighbourSessionDay(orderedDays: List<Int>, currentDay: Int?, sessionDays: Set<Int>, step: Int): Int? {
    val index = orderedDays.indexOf(currentDay ?: return null)
    if (index < 0 || step == 0) return null
    var i = index + step
    while (i in orderedDays.indices) {
        if (orderedDays[i] in sessionDays) return orderedDays[i]
        i += step
    }
    return null
}

/** Posición de un día en el orden de la semana; −1 si no hay día o no está en el orden. */
internal fun frameDayIndex(orderedDays: List<Int>, day: Int?): Int = day?.let { orderedDays.indexOf(it) } ?: -1

/** Día contiguo en el orden de la semana (sin saltar vacíos), para el gesto de deslizar. Null en los extremos. */
internal fun adjacentWeekDay(orderedDays: List<Int>, currentDay: Int?, step: Int): Int? {
    val index = orderedDays.indexOf(currentDay ?: return null)
    return orderedDays.getOrNull(index + step)?.takeIf { index >= 0 }
}

/** Sesión que abre un día: la principal si la hay, si no la primera. Null si el día no tiene sesiones. */
internal fun primarySessionOnDay(sessions: List<Session>, day: Int): Session? {
    val onDay = sessions.filter { it.dayOfWeek == day }
    return onDay.firstOrNull { it.isMainSession } ?: onDay.firstOrNull()
}

/** Texto de la píldora del centro: «Jue · Pierna A». Sin día, solo el nombre. */
internal fun frameDayLabel(day: Int?, sessionName: String): String {
    val name = sessionName.ifBlank { "Sesión" }
    return if (day == null) name else "${com.example.kpkn.screens.sessioneditor.sessionEditorDayLabelShort(day)} · $name"
}

/** Nombre de una variante en el menú: A es «Original»; B, C y D toman el nombre de su sesión. */
internal fun frameVariantName(session: Session, variant: WeekVariant): String = when (variant) {
    WeekVariant.A -> "Original"
    WeekVariant.B -> session.sessionB?.name ?: "Derivada"
    WeekVariant.C -> session.sessionC?.name ?: "Derivada"
    WeekVariant.D -> session.sessionD?.name ?: "Derivada"
}

/** La primera variante libre (B, C o D) para crear una derivada; null si ya están todas. */
internal fun nextFreeVariant(available: List<WeekVariant>): WeekVariant? =
    listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D).firstOrNull { it !in available }

/**
 * Línea de resumen de la cabecera: «7 ejercicios · 22 series · ≈ 54 min». Sin ejercicios, lo dice. Sin minutos, no los nombra. Las
 * unidades van con espacio duro, como manda la piel.
 */
internal fun sessionFrameSummary(exercises: Int, sets: Int, minutes: Int?): String {
    if (exercises <= 0) return "Sin ejercicios"
    val exerciseWord = if (exercises == 1) "ejercicio" else "ejercicios"
    val setWord = if (sets == 1) "serie" else "series"
    val parts = mutableListOf("$exercises $exerciseWord", "$sets $setWord")
    if (minutes != null && minutes > 0) parts += "≈ $minutes min"
    return parts.joinToString(" · ")
}
