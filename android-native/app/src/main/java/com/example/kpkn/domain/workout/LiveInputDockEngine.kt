package com.example.kpkn.domain.workout

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Modo de interacción dentro del dock de entrada en vivo.
 */
enum class LiveInputDockMode {
    LENS,
    KEYPAD,
}

/**
 * Tipo semántico del campo activo para adaptar pasos, teclado y formateo.
 */
enum class LiveInputFieldKind {
    WEIGHT,
    REPS,
    TIME_MM_SS,
    QUANTITY,
}

/**
 * Configuración de incremento de carga para el modo Deslizar.
 */
data class LoadStepConfig(
    val stepKg: Double,
    val isCustom: Boolean = false,
)

/**
 * Estado inmutable del borrador numérico del modo Teclado.
 *
 * @param text Representación textual formateada del valor activo (e.g. "80", "10,5", "1:30").
 * @param pendingReplace Si es verdadero, el próximo dígito reemplazará el valor completo en vez de anexarse.
 */
data class LiveKeypadDraft(
    val text: String,
    val pendingReplace: Boolean = false,
) {
    fun onDigit(digit: Char): LiveKeypadDraft {
        if (!digit.isDigit()) return this
        if (pendingReplace) {
            return LiveKeypadDraft(text = digit.toString(), pendingReplace = false)
        }
        if (text.length >= LiveInputDockEngine.MAX_KEYPAD_LENGTH) return this
        if (',' in text && text.substringAfter(',').length >= 2) return this
        if ('.' in text && text.substringAfter('.').length >= 2) return this
        if (':' in text && text.substringAfter(':').length >= 2) return this
        return LiveKeypadDraft(text = text + digit, pendingReplace = false)
    }

    fun onSeparator(allowDecimal: Boolean, allowColon: Boolean): LiveKeypadDraft {
        if (!allowDecimal && !allowColon) return this
        val sep = if (allowColon) ':' else ','
        if (pendingReplace) {
            return LiveKeypadDraft(text = "0$sep", pendingReplace = false)
        }
        val hasCommaOrDot = ',' in text || '.' in text
        val hasColon = ':' in text
        if (allowColon) {
            if (hasColon) return this
            val base = if (text.isBlank()) "0" else text
            return LiveKeypadDraft(text = "$base:", pendingReplace = false)
        }
        if (hasCommaOrDot) return this
        val base = if (text.isBlank()) "0" else text
        return LiveKeypadDraft(text = "$base,", pendingReplace = false)
    }

    fun onBackspace(): LiveKeypadDraft {
        if (pendingReplace) {
            return LiveKeypadDraft(text = "", pendingReplace = false)
        }
        return LiveKeypadDraft(text = text.dropLast(1), pendingReplace = false)
    }

    fun onLongPressClear(): LiveKeypadDraft = LiveKeypadDraft(text = "", pendingReplace = false)

    fun onStep(
        delta: Double,
        min: Double = 0.0,
        max: Double = 500.0,
        allowDecimal: Boolean,
        allowColon: Boolean,
    ): LiveKeypadDraft {
        val current = LiveInputDockEngine.parseDockNumber(text, allowColon) ?: 0.0
        val next = (current + delta).coerceIn(min, max)
        val formatted = LiveInputDockEngine.formatDockNumber(next, allowDecimal, allowColon)
        return LiveKeypadDraft(text = formatted, pendingReplace = false)
    }
}

/**
 * Motor de dominio puro para el dock de entrada adaptativo (Deslizar / Teclado).
 * Cero dependencias de android.* o Compose.
 */
object LiveInputDockEngine {
    const val MAX_KEYPAD_LENGTH = 7
    const val MIN_STEP_KG = 0.1
    const val MAX_STEP_KG = 25.0

    val PRESET_LOAD_STEPS_KG: List<Double> = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0)

    /**
     * Limita y normaliza el incremento de carga (0.1 a 25.0 kg, max 2 decimales).
     */
    fun clampStepKg(stepKg: Double): Double {
        if (!stepKg.isFinite()) return 2.5
        val clamped = stepKg.coerceIn(MIN_STEP_KG, MAX_STEP_KG)
        return (round(clamped * 100.0) / 100.0)
    }

    /**
     * Sanitiza entrada de texto para paso custom ("1,5" o "1.5" -> 1.5).
     */
    fun sanitizeCustomStepKg(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        val parsed = normalized.toDoubleOrNull() ?: return null
        if (!parsed.isFinite()) return null
        if (parsed < MIN_STEP_KG) return MIN_STEP_KG
        if (parsed > MAX_STEP_KG) return MAX_STEP_KG
        return (round(parsed * 100.0) / 100.0)
    }

    /**
     * Redondea un valor numérico al múltiplo exacto de [step] más cercano evitando ruido flotante.
     */
    fun snapValueToStep(
        value: Double,
        step: Double,
        min: Double = 0.0,
        max: Double = 500.0,
    ): Double {
        if (step <= 0.0 || !step.isFinite()) return value.coerceIn(min, max)
        val clamped = value.coerceIn(min, max)
        val rawTicks = (clamped - min) / step
        val snapped = round(rawTicks) * step + min
        val bounded = snapped.coerceIn(min, max)
        return BigDecimal.valueOf(bounded)
            .setScale(3, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toDouble()
    }

    /**
     * Calcula los 4 deltas de salto rápido para el modo Deslizar según el tipo de campo.
     * Retorna lista de 4 elementos: [-2×step, -step, +step, +2×step].
     */
    fun quickJumpDeltas(kind: LiveInputFieldKind, stepKg: Double): List<Double> {
        val safeStep = if (stepKg > 0.0 && stepKg.isFinite()) stepKg else 2.5
        return when (kind) {
            LiveInputFieldKind.WEIGHT -> listOf(
                -round(safeStep * 200.0) / 100.0,
                -round(safeStep * 100.0) / 100.0,
                round(safeStep * 100.0) / 100.0,
                round(safeStep * 200.0) / 100.0,
            )
            LiveInputFieldKind.TIME_MM_SS -> listOf(-15.0, -5.0, 5.0, 15.0)
            LiveInputFieldKind.REPS -> listOf(-5.0, -1.0, 1.0, 5.0)
            LiveInputFieldKind.QUANTITY -> {
                if (safeStep == 1.0) listOf(-5.0, -1.0, 1.0, 5.0)
                else listOf(-safeStep * 2.0, -safeStep, safeStep, safeStep * 2.0)
            }
        }
    }

    /**
     * Formatea un delta numérico con signo (+ / −) y coma decimal española.
     */
    fun formatQuickDeltaPill(delta: Double, kind: LiveInputFieldKind): String {
        val sign = if (delta > 0) "+" else "−"
        val mag = abs(delta)
        if (kind == LiveInputFieldKind.TIME_MM_SS) {
            return "$sign${mag.toInt()}s"
        }
        val formattedMag = if (mag % 1.0 == 0.0) {
            mag.toInt().toString()
        } else {
            BigDecimal.valueOf(mag)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
                .replace('.', ',')
        }
        return "$sign$formattedMag"
    }

    /**
     * Formatea un valor numérico para presentación en el dock.
     */
    fun formatDockNumber(value: Double, allowDecimal: Boolean, allowColon: Boolean): String {
        if (!value.isFinite()) return "0"
        if (allowColon) {
            val totalSec = round(value).toInt().coerceAtLeast(0)
            val mins = totalSec / 60
            val secs = totalSec % 60
            return "$mins:${secs.toString().padStart(2, '0')}"
        }
        if (!allowDecimal) {
            return round(value).toInt().toString()
        }
        val rounded = BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return rounded.toPlainString().replace('.', ',')
    }

    /**
     * Parsea un texto ingresado o staged a Double numérico.
     */
    fun parseDockNumber(raw: String, allowColon: Boolean): Double? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "—") return null
        if (allowColon && ':' in trimmed) {
            val parts = trimmed.split(':')
            return when (parts.size) {
                1 -> parts[0].replace(',', '.').toDoubleOrNull()
                2 -> {
                    val mins = parts[0].toIntOrNull() ?: return null
                    val secs = parts[1].toIntOrNull() ?: return null
                    if (mins >= 0 && secs in 0..59) (mins * 60 + secs).toDouble() else null
                }
                else -> null
            }
        }
        return trimmed.replace(',', '.').toDoubleOrNull()
    }
}
