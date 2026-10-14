package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import java.text.Normalizer

/** Patrón de descanso tal como lo escribe el motor de repartos. */
internal const val SPLIT_REST_LABEL = "Descanso"

/** Forma comparable de un patrón de descanso: vacío o «Descanso» cuentan igual. */
private const val REST_KEY = "descanso"

/** Letras de la semana, lunes primero (1 = lunes, como en el resto de la app). */
internal val SPLIT_WEEKDAY_LETTERS: List<String> = listOf("L", "M", "X", "J", "V", "S", "D")

/** Nombres de los días en español, lunes primero. */
internal val SPLIT_WEEKDAY_NAMES: List<String> = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/** Rol de un día en el diagrama de la semana (P5): decide su inicial y su color. El descanso no lleva ninguno. */
internal enum class SplitDayRole { REST, PUSH, PULL, LEGS, TORSO, FULL_BODY, OTHER }

/** Rol e inicial de un día: la inicial va dentro de su casilla y queda vacía en el descanso. */
internal data class SplitDayMark(val role: SplitDayRole, val initial: String)

/**
 * Clasifica el patrón de un día. Las reglas se aplican en orden: descanso (vacío o «Descanso»), empuje, tirón, pierna, torso o
 * cuerpo completo, y el resto. No distingue tildes ni mayúsculas. «Ti» y «Pi» existen porque «T» (torso) y «P» (pecho) ya están tomadas.
 */
internal fun splitDayMark(label: String): SplitDayMark {
    val key = canonicalSplitLabel(label)
    return when {
        key == REST_KEY -> SplitDayMark(SplitDayRole.REST, "")
        "empuje" in key || "push" in key -> SplitDayMark(SplitDayRole.PUSH, "E")
        "tiron" in key || "pull" in key -> SplitDayMark(SplitDayRole.PULL, "Ti")
        "pierna" in key || "legs" in key -> SplitDayMark(SplitDayRole.LEGS, "Pi")
        "torso" in key -> SplitDayMark(SplitDayRole.TORSO, "T")
        "cuerpo completo" in key || "full body" in key -> SplitDayMark(SplitDayRole.FULL_BODY, "CC")
        else -> SplitDayMark(SplitDayRole.OTHER, initialOf(label))
    }
}

/**
 * Color de la casilla de un rol: empuje, `musculo`; tirón, `columna`; pierna, `energia`; torso, `mente`. Cuerpo completo y el resto van
 * en tinta (cuerpo completo no comparte lila con torso); el descanso no tiene color.
 */
internal fun splitDayAccent(role: SplitDayRole): Color? = when (role) {
    SplitDayRole.REST -> null
    SplitDayRole.PUSH -> SymbolPalette.musculo
    SplitDayRole.PULL -> SymbolPalette.columna
    SplitDayRole.LEGS -> SymbolPalette.energia
    SplitDayRole.TORSO -> SymbolPalette.mente
    SplitDayRole.FULL_BODY -> WizardColors.text
    SplitDayRole.OTHER -> WizardColors.text
}

/** Día de la semana (1..7) de la casilla [position] (0..6) de una semana que empieza en [startDay]. */
internal fun splitWeekdayAt(startDay: Int, position: Int): Int = ((startDay.coerceIn(1, 7) - 1 + position) % 7) + 1

/** Patrón de la casilla [position]: el que trae la plantilla o, si es más corta, el descanso. */
internal fun splitLabelAt(pattern: List<String>, position: Int): String =
    pattern.getOrNull(position)?.takeIf { it.isNotBlank() } ?: SPLIT_REST_LABEL

/**
 * Descripción accesible de la semana, en el orden del programa: «Lunes: torso, Martes: pierna, Miércoles: descanso…». La leen los
 * lectores de pantalla en lugar de las siete casillas.
 */
internal fun splitWeekDescription(pattern: List<String>, startDay: Int): String =
    (0 until 7).joinToString(separator = ", ") { position ->
        val day = SPLIT_WEEKDAY_NAMES[splitWeekdayAt(startDay, position) - 1]
        "$day: ${splitLabelAt(pattern, position).lowercase()}"
    }

/** Días de entreno de un patrón: todos los que no son descanso. */
internal fun splitTrainingDays(pattern: List<String>): Int = pattern.count { splitDayMark(it).role != SplitDayRole.REST }

/** «1 día» o «4 días». */
internal fun splitDaysLabel(count: Int): String = if (count == 1) "1 día" else "$count días"

/** Posiciones de la semana (0..6) en que dos patrones no coinciden: lo que cambia al pasar de [from] a [to]. */
internal fun splitChangedPositions(from: List<String>, to: List<String>): List<Int> =
    (0 until 7).filter { position ->
        canonicalSplitLabel(splitLabelAt(from, position)) != canonicalSplitLabel(splitLabelAt(to, position))
    }

/** Forma comparable de un patrón: sin tildes, en minúsculas y con los espacios juntos. El descanso vacío cuenta como «descanso». */
internal fun canonicalSplitLabel(label: String): String {
    val plain = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
    return if (plain.isEmpty() || plain == REST_KEY) REST_KEY else plain
}

/** Primera letra del patrón en mayúscula, para la inicial de los días que no tienen rol propio. */
private fun initialOf(label: String): String =
    label.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()
