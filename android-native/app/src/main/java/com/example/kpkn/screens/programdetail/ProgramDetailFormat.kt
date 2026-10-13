package com.example.kpkn.screens.programdetail

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Abreviatura del día (1 = lunes … 7 = domingo) para chips y listas compactas. */
internal fun dayLabelShort(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}

/**
 * Fecha ISO (AAAA-MM-DD) recortando los espacios antes de leerla. Null si está vacía o no es una fecha válida.
 * La usan la pantalla y el ViewModel del programa.
 */
internal fun parseIsoDate(raw: String?): LocalDate? {
    val texto = raw?.trim() ?: return null
    if (texto.isEmpty()) return null
    return try {
        LocalDate.parse(texto)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Como [parseIsoDate], pero sin recortar: «2026-10-13 » no es válida. Es lo que aceptan hoy la hoja de fechas clave
 * y el volumen; se conserva así para no cambiar qué fechas se aceptan.
 */
internal fun parseIsoDateExact(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Título de una semana del calendario, por ejemplo «Semana: 10/13». */
internal fun calendarWeekTitle(startDate: LocalDate): String =
    "Semana: ${startDate.format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))}"
