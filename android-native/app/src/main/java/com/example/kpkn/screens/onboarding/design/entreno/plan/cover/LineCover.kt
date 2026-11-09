package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

/** Estilos de las portadas de líneas (lienzo v6). SESION solo se usa en la historia de un entreno. */
internal enum class LineCoverStyle(val label: String) {
    RELIEVE("Relieve"), PERFIL("Perfil"), ONDAS("Ondas"), ORBITAS("Órbitas"), CAMPO("Campo"),
    ESPIRAL("Espiral"), ECO("Eco"), BRUMA("Bruma"), INTERFERENCIA("Interferencia"), ABANICO("Abanico"), SESION("Sesión"),
}

/** Fondos: [bg]→[bg2] degradado, [line] tono de las líneas, [accent] la única línea de acento. Colores ARGB. */
internal enum class LineCoverBackground(val label: String, val bg: Long, val bg2: Long, val line: Long, val accent: Long, val dark: Boolean) {
    CARBON("Carbón", 0xFF161616, 0xFF060606, 0xFFE9E3D8, 0xFFF49A6E, true),
    GRAFITO("Grafito", 0xFF1D2024, 0xFF0B0C0E, 0xFFA3ACB6, 0xFFF2EEE6, true),
    NOCHE("Noche", 0xFF101830, 0xFF04070E, 0xFF93A9DA, 0xFFF7CF73, true),
    MUSGO("Musgo", 0xFF131C17, 0xFF050907, 0xFFA4BCAA, 0xFFF49A6E, true),
    AZUL("Azul", 0xFF0D1C2D, 0xFF06101C, 0xFF9BCBFF, 0xFF49A5F5, true),
    JADE("Jade", 0xFF10241F, 0xFF07110E, 0xFFB0E3D0, 0xFF54C7A0, true),
    VIOLETA("Violeta", 0xFF21192E, 0xFF0D0914, 0xFFD1B9F4, 0xFFAD7BE8, true),
    CORAL("Coral", 0xFF2A1715, 0xFF100908, 0xFFFFC1AD, 0xFFFF8069, true),
    AMBAR("Ámbar", 0xFF251D0D, 0xFF100C05, 0xFFFFDEA0, 0xFFFFB84A, true),
    CIAN("Cian", 0xFF0D2025, 0xFF061014, 0xFFABEDF2, 0xFF5AD3E0, true),
    ROSADO("Rosado", 0xFF291823, 0xFF10090E, 0xFFF7C4DB, 0xFFE783B5, true),
    CREMA("Crema", 0xFFECE6DB, 0xFFDCD3C4, 0xFF1D1B19, 0xFFD9673A, false),
}

/** Lo que dibuja una portada de líneas: estilo, fondo, semilla y datos del programa. [weekLoads] 0..1 por semana (Perfil); [currentWeek] índice 0-based o -1; [weeks] número de semanas (Espiral, Eco). */
internal data class LineCoverSpec(val style: LineCoverStyle, val background: LineCoverBackground, val seed: Int, val weekLoads: List<Float> = emptyList(), val currentWeek: Int = -1, val weeks: Int = 6)

internal const val LINE_COVER_SCHEME = "kpkn-lines://"

internal fun lineCoverValue(style: LineCoverStyle, background: LineCoverBackground): String = LINE_COVER_SCHEME + style.name + "/" + background.name

/** Par (estilo, fondo) de [coverImage], o null si no es `kpkn-lines://ESTILO/FONDO` válido. */
internal fun parseLineCover(coverImage: String?): Pair<LineCoverStyle, LineCoverBackground>? {
    val rest = coverImage?.takeIf { it.startsWith(LINE_COVER_SCHEME) }?.removePrefix(LINE_COVER_SCHEME) ?: return null
    val parts = rest.split('/')
    if (parts.size != 2) return null
    val style = LineCoverStyle.entries.firstOrNull { it.name == parts[0] } ?: return null
    val background = LineCoverBackground.entries.firstOrNull { it.name == parts[1] } ?: return null
    return style to background
}

/** Normaliza series planificadas por semana a 0.3..0.95 (la mayor = 0.95). Lista vacía → vacía; todo cero → 0.5 en cada semana. */
internal fun normalizedWeekLoads(setsPerWeek: List<Int>): List<Float> {
    val max = setsPerWeek.maxOrNull() ?: return emptyList()
    if (max <= 0) return setsPerWeek.map { 0.5f }
    return setsPerWeek.map { 0.3f + 0.65f * (it.coerceAtLeast(0).toFloat() / max) }
}
