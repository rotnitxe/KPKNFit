package com.example.kpkn.screens.workout

import android.content.Context
import com.example.kpkn.data.voice.VoiceNutritionRecognizer
import com.example.kpkn.data.voice.VoiceState
import com.example.kpkn.data.models.IntensityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
enum class WorkoutVoiceField {
    WEIGHT,
    VALUE,
    INTENSITY,
    SIDE,
    FAILURE,
}

@Serializable
enum class WorkoutVoiceIntensityKind {
    RPE,
    RIR,
    PERCENT_RM,
}

sealed interface WorkoutVoiceUiState {
    data object Idle : WorkoutVoiceUiState
    data class Listening(
        val exerciseId: String,
        val setIdx: Int,
        val side: String? = null,
        val partialText: String = "",
        val isReady: Boolean = false,
    ) : WorkoutVoiceUiState
    data class Confirmation(
        val exerciseId: String,
        val setIdx: Int,
        val side: String? = null,
        val interpretation: WorkoutVoiceInterpretation,
    ) : WorkoutVoiceUiState
    data class Applied(
        val exerciseId: String,
        val setIdx: Int,
        val side: String? = null,
        val interpretation: WorkoutVoiceInterpretation,
        val message: String,
    ) : WorkoutVoiceUiState
    data class Error(
        val exerciseId: String,
        val setIdx: Int,
        val side: String? = null,
        val message: String,
    ) : WorkoutVoiceUiState
}

data class WorkoutVoiceInterpretation(
    val transcript: String,
    val weightKg: Double? = null,
    val metricValue: Int? = null,
    val intensityValue: Double? = null,
    val intensityKind: WorkoutVoiceIntensityKind? = null,
    val side: String? = null,
    val reachedFailure: Boolean = false,
    val fields: Set<WorkoutVoiceField> = emptySet(),
)

class WorkoutVoiceRecognizer(context: Context) {
    private val delegate = VoiceNutritionRecognizer(context)

    fun recognize(maxDurationMs: Int = 6500): Flow<VoiceState> = delegate.recognize(maxDurationMs)

    companion object {
        fun hasPermission(context: Context): Boolean = VoiceNutritionRecognizer.hasPermission(context)
        fun isAvailable(context: Context): Boolean = VoiceNutritionRecognizer.isAvailable(context)
    }
}

internal fun parseWorkoutVoiceTranscript(
    transcript: String,
    isTimeMode: Boolean,
    isUnilateral: Boolean,
): WorkoutVoiceInterpretation? {
    val tokens = normalizeWorkoutVoiceTranscript(transcript)
    if (tokens.isEmpty()) return null

    val explicitWeight = tokens.indexOfFirst { it in WEIGHT_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val explicitReps = tokens.indexOfFirst { it in REP_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber() }
    val explicitSeconds = tokens.indexOfFirst { it in SECOND_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber() }
    val explicitMinutes = tokens.indexOfFirst { it in MINUTE_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber()?.times(60) }
    val explicitRpe = tokens.indexOfFirst { it in RPE_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it) }
    val explicitRir = tokens.indexOfFirst { it in RIR_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it) }
    val explicitPercentRm = tokens.indexOfFirst { it in PERCENT_RM_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it) }
    val explicitFailureMetric = tokens.indexOfFirst { it in FAILURE_DISTANCE_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val connectorPair = extractConnectedWeightAndMetric(tokens)

    val side = when {
        !isUnilateral -> null
        tokens.any { it in LEFT_SIDE_KEYWORDS } && tokens.none { it in RIGHT_SIDE_KEYWORDS } -> "left"
        tokens.any { it in RIGHT_SIDE_KEYWORDS } && tokens.none { it in LEFT_SIDE_KEYWORDS } -> "right"
        else -> null
    }
    val reachedFailure = tokens.any { it in FAILURE_KEYWORDS }

    val weightKg = if (isTimeMode) {
        explicitWeight
    } else {
        explicitWeight ?: connectorPair?.first
    }
    val metricValue = when {
        isTimeMode -> explicitSeconds ?: explicitMinutes ?: connectorPair?.second
        else -> explicitReps ?: connectorPair?.second
    }
    val intensityValue = when {
        explicitRpe != null -> explicitRpe
        explicitRir != null -> explicitRir
        explicitPercentRm != null -> explicitPercentRm
        explicitFailureMetric != null -> explicitFailureMetric
        else -> null
    }
    val intensityKind = when {
        explicitRpe != null -> WorkoutVoiceIntensityKind.RPE
        explicitRir != null -> WorkoutVoiceIntensityKind.RIR
        explicitPercentRm != null -> WorkoutVoiceIntensityKind.PERCENT_RM
        explicitFailureMetric != null -> WorkoutVoiceIntensityKind.RIR
        else -> null
    }

    val fields = buildSet {
        if (weightKg != null) add(WorkoutVoiceField.WEIGHT)
        if (metricValue != null) add(WorkoutVoiceField.VALUE)
        if (intensityValue != null) add(WorkoutVoiceField.INTENSITY)
        if (side != null) add(WorkoutVoiceField.SIDE)
        if (reachedFailure) add(WorkoutVoiceField.FAILURE)
    }

    if (fields.isEmpty()) return null
    return WorkoutVoiceInterpretation(
        transcript = transcript.trim(),
        weightKg = weightKg,
        metricValue = metricValue,
        intensityValue = intensityValue,
        intensityKind = intensityKind,
        side = side,
        reachedFailure = reachedFailure,
        fields = fields,
    )
}

internal fun workoutVoiceSummary(
    interpretation: WorkoutVoiceInterpretation,
    isTimeMode: Boolean,
): String = buildList {
    interpretation.weightKg?.let { add("${it.toTrimmedNumberString()} kg") }
    interpretation.metricValue?.let { value ->
        add(if (isTimeMode) "$value s" else "$value reps")
    }
    interpretation.intensityValue?.let { value ->
        val label = when (interpretation.intensityKind) {
            WorkoutVoiceIntensityKind.RIR -> "RIR ${value.toTrimmedNumberString()}"
            WorkoutVoiceIntensityKind.PERCENT_RM -> "${value.toTrimmedNumberString()}%RM"
            else -> "RPE ${value.toTrimmedNumberString()}"
        }
        add(label)
    }
    interpretation.side?.let { side ->
        add(if (side == "left") "Izquierda" else "Derecha")
    }
    if (interpretation.reachedFailure) add("Fallo")
}.joinToString(" · ")

internal fun workoutVoiceAppliedMessage(
    interpretation: WorkoutVoiceInterpretation,
    isTimeMode: Boolean,
): String {
    val summary = workoutVoiceSummary(interpretation, isTimeMode)
    return if (summary.isBlank()) {
        "Voz aplicada al borrador."
    } else {
        "Voz aplicada: $summary"
    }
}

internal fun workoutVoiceIntensityText(
    interpretation: WorkoutVoiceInterpretation,
    baseIntensityMode: IntensityMode?,
): String {
    val raw = interpretation.intensityValue ?: return ""
    if (interpretation.reachedFailure) return ""
    val normalized = when (baseIntensityMode) {
        IntensityMode.RIR -> when (interpretation.intensityKind) {
            WorkoutVoiceIntensityKind.RIR -> raw
            else -> (10.0 - raw).coerceAtLeast(0.0)
        }

        IntensityMode.SOLO_RM -> raw
        else -> when (interpretation.intensityKind) {
            WorkoutVoiceIntensityKind.RIR -> (10.0 - raw).coerceAtLeast(0.0)
            else -> raw
        }
    }
    return normalized.toTrimmedNumberString()
}

private fun normalizeWorkoutVoiceTranscript(transcript: String): List<String> {
    val normalized = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("×", " x ")
        .replace(Regex("[^a-z0-9.,% ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized.split(' ').filter { it.isNotBlank() }
}

private fun extractConnectedWeightAndMetric(tokens: List<String>): Pair<Double, Int>? {
    tokens.forEachIndexed { index, token ->
        if (token !in CONNECTOR_KEYWORDS) return@forEachIndexed
        val left = readVoiceNumberBackward(tokens, index - 1)?.first
        val right = readVoiceNumberForward(tokens, index + 1)?.first?.toSafeWholeNumber()
        if (left != null && right != null) {
            return left to right
        }
    }
    return null
}

private fun nearestVoiceNumber(
    tokens: List<String>,
    index: Int,
    preferBackward: Boolean = false,
): Double? = if (preferBackward) {
    readVoiceNumberBackward(tokens, index - 1)?.first ?: readVoiceNumberForward(tokens, index + 1)?.first
} else {
    readVoiceNumberForward(tokens, index + 1)?.first ?: readVoiceNumberBackward(tokens, index - 1)?.first
}

private fun readVoiceNumberForward(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
    if (startIndex !in tokens.indices) return null
    val collected = mutableListOf<String>()
    var index = startIndex
    while (index < tokens.size && tokens[index].isVoiceNumberToken()) {
        collected += tokens[index]
        index += 1
    }
    val value = parseVoiceNumberTokens(collected) ?: return null
    return value to index
}

private fun readVoiceNumberBackward(tokens: List<String>, startIndex: Int): Pair<Double, Int>? {
    if (startIndex !in tokens.indices) return null
    val collected = mutableListOf<String>()
    var index = startIndex
    while (index >= 0 && tokens[index].isVoiceNumberToken()) {
        collected.add(0, tokens[index])
        index -= 1
    }
    val value = parseVoiceNumberTokens(collected) ?: return null
    return value to (index + 1)
}

private fun parseVoiceNumberTokens(tokens: List<String>): Double? {
    if (tokens.isEmpty()) return null
    if (tokens.size == 1 && DIGIT_TOKEN.matches(tokens.first())) {
        return tokens.first().replace(',', '.').toDoubleOrNull()
    }

    val decimalSeparatorIdx = tokens.indexOfFirst { it == "punto" || it == "coma" }
    if (decimalSeparatorIdx >= 0) {
        val whole = parseVoiceInteger(tokens.take(decimalSeparatorIdx)) ?: return null
        val decimals = buildString {
            tokens.drop(decimalSeparatorIdx + 1).forEach { token ->
                val digit = decimalDigitForVoiceToken(token) ?: return null
                append(digit)
            }
        }
        return "$whole.$decimals".toDoubleOrNull()
    }

    return parseVoiceInteger(tokens)
}

private fun parseVoiceInteger(tokens: List<String>): Double? {
    if (tokens.isEmpty()) return null
    var total = 0.0
    var consumed = false
    tokens.forEach { token ->
        when {
            token == "y" -> Unit
            token == "medio" || token == "media" -> {
                total += 0.5
                consumed = true
            }

            DIGIT_TOKEN.matches(token) -> {
                total += token.replace(',', '.').toDoubleOrNull() ?: return null
                consumed = true
            }

            VOICE_INTEGER_WORDS.containsKey(token) -> {
                total += VOICE_INTEGER_WORDS.getValue(token)
                consumed = true
            }

            else -> return null
        }
    }
    return total.takeIf { consumed }
}

private fun decimalDigitForVoiceToken(token: String): Char? = when {
    DIGIT_TOKEN.matches(token) && token.length == 1 -> token.first()
    VOICE_DECIMAL_DIGITS.containsKey(token) -> VOICE_DECIMAL_DIGITS.getValue(token)
    else -> null
}

private fun String.isVoiceNumberToken(): Boolean =
    DIGIT_TOKEN.matches(this) ||
        this in VOICE_INTEGER_WORDS ||
        this in VOICE_DECIMAL_DIGITS ||
        this == "punto" ||
        this == "coma" ||
        this == "y" ||
        this == "medio" ||
        this == "media"

private fun Double.toSafeWholeNumber(): Int? =
    takeIf { abs(it - it.roundToInt().toDouble()) < 0.001 }
        ?.roundToInt()

private val DIGIT_TOKEN = Regex("\\d+(?:[.,]\\d+)?")

private val CONNECTOR_KEYWORDS = setOf("x", "por")
private val WEIGHT_KEYWORDS = setOf("kg", "kilo", "kilos", "peso", "carga", "lastre", "asistencia")
private val REP_KEYWORDS = setOf("rep", "reps", "repeticion", "repeticiones")
private val SECOND_KEYWORDS = setOf("seg", "segundo", "segundos")
private val MINUTE_KEYWORDS = setOf("min", "minuto", "minutos")
private val RPE_KEYWORDS = setOf("rpe")
private val RIR_KEYWORDS = setOf("rir")
private val PERCENT_RM_KEYWORDS = setOf("porcentaje", "%", "rm")
private val FAILURE_KEYWORDS = setOf("fallo", "falla")
private val FAILURE_DISTANCE_KEYWORDS = setOf("recamara", "recamaras", "reserva", "reservas")
private val LEFT_SIDE_KEYWORDS = setOf("izquierda", "izquierdo", "izq")
private val RIGHT_SIDE_KEYWORDS = setOf("derecha", "derecho", "der")

private val VOICE_INTEGER_WORDS = mapOf(
    "cero" to 0,
    "un" to 1,
    "uno" to 1,
    "una" to 1,
    "dos" to 2,
    "tres" to 3,
    "cuatro" to 4,
    "cinco" to 5,
    "seis" to 6,
    "siete" to 7,
    "ocho" to 8,
    "nueve" to 9,
    "diez" to 10,
    "once" to 11,
    "doce" to 12,
    "trece" to 13,
    "catorce" to 14,
    "quince" to 15,
    "dieciseis" to 16,
    "diecisiete" to 17,
    "dieciocho" to 18,
    "diecinueve" to 19,
    "veinte" to 20,
    "veintiuno" to 21,
    "veintidos" to 22,
    "veintitres" to 23,
    "veinticuatro" to 24,
    "veinticinco" to 25,
    "veintiseis" to 26,
    "veintisiete" to 27,
    "veintiocho" to 28,
    "veintinueve" to 29,
    "treinta" to 30,
    "cuarenta" to 40,
    "cincuenta" to 50,
    "sesenta" to 60,
    "setenta" to 70,
    "ochenta" to 80,
    "noventa" to 90,
    "cien" to 100,
    "ciento" to 100,
)

private val VOICE_DECIMAL_DIGITS = mapOf(
    "cero" to '0',
    "un" to '1',
    "uno" to '1',
    "una" to '1',
    "dos" to '2',
    "tres" to '3',
    "cuatro" to '4',
    "cinco" to '5',
    "seis" to '6',
    "siete" to '7',
    "ocho" to '8',
    "nueve" to '9',
)
