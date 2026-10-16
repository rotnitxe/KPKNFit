package com.example.kpkn.screens.workout

import java.time.LocalDate

/** Día abreviado de lunes (1) a domingo (7): minúscula y sin punto, como en el resto de la app. */
private val HISTORY_DIAS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/** Mes abreviado: minúscula y sin punto, como en el resto de la app. */
private val HISTORY_MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

/**
 * Fecha de una sesión del historial (LR1d): «hoy», «ayer», «mar 14 oct» si es de este año y «14 oct 2025» si es de otro.
 * Pura: recibe el día de hoy para poder probarla. Si la fecha no se puede leer, devuelve el texto tal cual.
 */
internal fun formatHistoryDate(isoDate: String, today: LocalDate): String {
    val date = runCatching { LocalDate.parse(isoDate.take(10)) }.getOrNull() ?: return isoDate
    val mes = HISTORY_MESES[date.monthValue - 1]
    return when {
        date == today -> "hoy"
        date == today.minusDays(1) -> "ayer"
        date.year == today.year -> "${HISTORY_DIAS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} $mes"
        else -> "${date.dayOfMonth} $mes ${date.year}"
    }
}
