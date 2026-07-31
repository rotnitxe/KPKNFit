package com.example.kpkn.screens.workout

import android.content.Context
import com.example.kpkn.data.voice.VoiceNutritionRecognizer
import com.example.kpkn.data.voice.VoiceState
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.UnitModeV2
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
    FAILED_SET,
    HELPED_REPS,
    LOAD_MODE,
    DROP_SET,
    REST_PAUSE,
    ROM,
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
    val metricDecimalValue: Double? = null,
    val intensityValue: Double? = null,
    val intensityKind: WorkoutVoiceIntensityKind? = null,
    val side: String? = null,
    val reachedFailure: Boolean = false,
    val isFailedSet: Boolean = false,
    val failureReason: String? = null,
    /** Repeticiones realizadas con ayuda externa de una persona. */
    val helpedReps: Int? = null,
    val loadModeOverride: LoadModeV2? = null,
    val dropSets: List<DropSetData> = emptyList(),
    val restPauses: List<RestPauseData> = emptyList(),
    val ambiguousIntensityValue: Double? = null,
    val timerElapsedSeconds: Int? = null,
    val incompleteTechnique: String? = null,
    val romPercent: Int? = null,
    val tagName: String? = null,
    val fields: Set<WorkoutVoiceField> = emptySet(),
) {
    val resolvedMetricValue: Double? get() = metricDecimalValue ?: metricValue?.toDouble()
}

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
    unitMode: UnitModeV2 = if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
    customUnit: String? = null,
    trackRom: Boolean = false,
): WorkoutVoiceInterpretation? {
    val tokens = normalizeWorkoutVoiceTranscript(transcript)
    if (tokens.isEmpty()) return null

    val explicitWeight = tokens.indexOfFirst { it in WEIGHT_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val techniqueStart = tokens.indexOfFirst { it in TECHNIQUE_KEYWORDS }.takeIf { it >= 0 } ?: tokens.size
    val mainTokens = tokens.take(techniqueStart)
    val explicitReps = mainTokens.indexOfFirst { it in REP_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(mainTokens, it, preferBackward = true)?.toSafeWholeNumber() }
    val explicitSeconds = tokens.indexOfFirst { it in SECOND_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber() }
    val explicitMinutes = tokens.indexOfFirst { it in MINUTE_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber()?.times(60) }
    val explicitDistance = tokens.indexOfFirst { it in DISTANCE_KEYWORDS }.takeIf { it >= 0 }?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val customUnitKeywords = normalizeWorkoutVoiceTranscript(customUnit.orEmpty()).toSet() + CUSTOM_UNIT_KEYWORDS
    val explicitCustom = tokens.indexOfFirst { it in customUnitKeywords }.takeIf { it >= 0 }?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
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
    val explicitRom = if (trackRom) tokens.indexOfFirst { it in ROM_KEYWORDS }.takeIf { it >= 0 }
        ?.let { nearestVoiceNumber(tokens, it)?.toSafeWholeNumber() }?.takeIf { it in 0..100 } else null
    val connectorPair = extractConnectedWeightAndMetric(tokens)

    val side = when {
        !isUnilateral -> null
        tokens.any { it in LEFT_SIDE_KEYWORDS } && tokens.none { it in RIGHT_SIDE_KEYWORDS } -> "left"
        tokens.any { it in RIGHT_SIDE_KEYWORDS } && tokens.none { it in LEFT_SIDE_KEYWORDS } -> "right"
        else -> null
    }
    val normalizedText = tokens.joinToString(" ")
    val isFailedSet = FAILED_SET_PHRASES.any(normalizedText::contains)
    val reachedFailure = !isFailedSet && (
        FAILURE_PHRASES.any(normalizedText::contains) || tokens.any { it in FAILURE_KEYWORDS }
    )
    val helpedReps = extractNumberBeforePhrase(tokens, listOf("con", "ayuda"))?.toSafeWholeNumber()
    val loadModeOverride = when {
        tokens.any { it in LASTRE_KEYWORDS } -> LoadModeV2.LASTRE
        tokens.any { it in ASSISTED_LOAD_KEYWORDS } -> LoadModeV2.ASSISTED
        BODYWEIGHT_PHRASES.any(normalizedText::contains) -> LoadModeV2.BODYWEIGHT
        tokens.any { it in NORMAL_LOAD_KEYWORDS } -> LoadModeV2.LOAD
        else -> null
    }
    val dropSets = extractDropSets(tokens)
    val restPauses = extractRestPauses(tokens)
    val incompleteTechnique = when {
        tokens.any { it in DROP_SET_KEYWORDS } && dropSets.isEmpty() -> "dropset"
        tokens.any { it in REST_PAUSE_KEYWORDS } && restPauses.isEmpty() -> "restpause"
        else -> null
    }

    val effectiveUnitMode = if (isTimeMode) UnitModeV2.TIME else unitMode
    val weightKg = if (effectiveUnitMode != UnitModeV2.REPS) explicitWeight else explicitWeight ?: connectorPair?.first
    val metricDecimalValue = when (effectiveUnitMode) {
        UnitModeV2.TIME -> (explicitSeconds ?: explicitMinutes)?.toDouble() ?: connectorPair?.second
        UnitModeV2.REPS -> explicitReps?.toDouble() ?: connectorPair?.second
        UnitModeV2.DISTANCE -> explicitDistance ?: connectorPair?.second
        UnitModeV2.CUSTOM -> explicitCustom ?: connectorPair?.second
    }
    val metricValue = metricDecimalValue?.toSafeWholeNumber()
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
    val orphanIntensity = if (intensityValue == null && weightKg != null && metricDecimalValue != null) {
        trailingVoiceNumber(tokens)?.takeIf { it in 0.0..10.0 }
    } else null

    val fields = buildSet {
        if (weightKg != null) add(WorkoutVoiceField.WEIGHT)
        if (metricDecimalValue != null) add(WorkoutVoiceField.VALUE)
        if (intensityValue != null) add(WorkoutVoiceField.INTENSITY)
        if (side != null) add(WorkoutVoiceField.SIDE)
        if (reachedFailure) add(WorkoutVoiceField.FAILURE)
        if (isFailedSet) add(WorkoutVoiceField.FAILED_SET)
        if (helpedReps != null) add(WorkoutVoiceField.HELPED_REPS)
        if (loadModeOverride != null) add(WorkoutVoiceField.LOAD_MODE)
        if (dropSets.isNotEmpty()) add(WorkoutVoiceField.DROP_SET)
        if (restPauses.isNotEmpty()) add(WorkoutVoiceField.REST_PAUSE)
        if (explicitRom != null) add(WorkoutVoiceField.ROM)
    }

    if (fields.isEmpty()) return null
    return WorkoutVoiceInterpretation(
        transcript = transcript.trim(),
        weightKg = weightKg,
        metricValue = metricValue,
        metricDecimalValue = metricDecimalValue,
        intensityValue = intensityValue,
        intensityKind = intensityKind,
        side = side,
        reachedFailure = reachedFailure,
        isFailedSet = isFailedSet,
        helpedReps = helpedReps?.coerceAtMost(metricValue ?: helpedReps),
        loadModeOverride = loadModeOverride,
        dropSets = dropSets,
        restPauses = restPauses,
        ambiguousIntensityValue = orphanIntensity,
        incompleteTechnique = incompleteTechnique,
        romPercent = explicitRom,
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
    if (interpretation.isFailedSet) add("Serie fallida")
    interpretation.helpedReps?.let { add("$it con ayuda") }
    interpretation.dropSets.forEach { add("Dropset ${it.weight.toTrimmedNumberString()} kg, ${it.reps} reps") }
    interpretation.restPauses.forEach { add("Rest-pause ${it.restTime} s, ${it.reps} reps") }
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
        .replace(Regex("\\berre\\s+pe\\s+e\\b"), "rpe")
        .replace(Regex("\\berre\\s+i\\s+erre\\b"), "rir")
        .replace("repeticiones en reserva", "rir")
        .replace("×", " x ")
        .replace(Regex("[^a-z0-9.,% ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized.split(' ').filter { it.isNotBlank() }
}

private fun extractConnectedWeightAndMetric(tokens: List<String>): Pair<Double, Double>? {
    tokens.forEachIndexed { index, token ->
        if (token !in CONNECTOR_KEYWORDS) return@forEachIndexed
        val left = readVoiceNumberBackward(tokens, index - 1)?.first
        val right = readVoiceNumberForward(tokens, index + 1)?.first
        if (left != null && right != null) {
            return left to right
        }
    }
    return null
}

private fun extractNumberBeforePhrase(tokens: List<String>, phrase: List<String>): Double? {
    val index = tokens.indices.firstOrNull { start ->
        start + phrase.size <= tokens.size && tokens.subList(start, start + phrase.size) == phrase
    } ?: return null
    return readVoiceNumberBackward(tokens, index - 1)?.first
}

private fun trailingVoiceNumber(tokens: List<String>): Double? =
    readVoiceNumberBackward(tokens, tokens.lastIndex)?.first

private fun extractDropSets(tokens: List<String>): List<DropSetData> {
    val start = tokens.indexOfFirst { it in DROP_SET_KEYWORDS }
    if (start < 0) return emptyList()
    val segment = tokens.drop(start + 1)
    val weightIndex = segment.indexOfFirst { it in WEIGHT_KEYWORDS }
    val repsIndex = segment.indexOfFirst { it in REP_KEYWORDS }
    val weight = weightIndex.takeIf { it >= 0 }?.let { nearestVoiceNumber(segment, it, true) }
    val reps = repsIndex.takeIf { it >= 0 }?.let { nearestVoiceNumber(segment, it, true)?.toSafeWholeNumber() }
    return if (weight != null && reps != null) listOf(DropSetData(weight, reps)) else emptyList()
}

private fun extractRestPauses(tokens: List<String>): List<RestPauseData> {
    val start = tokens.indexOfFirst { it in REST_PAUSE_KEYWORDS }
    if (start < 0) return emptyList()
    val segment = tokens.drop(start + 1)
    val restIndex = segment.indexOfFirst { it in SECOND_KEYWORDS }
    val repsIndex = segment.indexOfFirst { it in REP_KEYWORDS }
    val rest = restIndex.takeIf { it >= 0 }?.let { nearestVoiceNumber(segment, it, true)?.toSafeWholeNumber() }
    val reps = repsIndex.takeIf { it >= 0 }?.let { nearestVoiceNumber(segment, it, true)?.toSafeWholeNumber() }
    return if (rest != null && reps != null) listOf(RestPauseData(rest, reps)) else emptyList()
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
private val RPE_KEYWORDS = setOf("rpe", "esfuerzo", "intensidad")
private val RIR_KEYWORDS = setOf("rir", "recamara", "recamaras", "reserva", "reservas", "ritmo")
private val PERCENT_RM_KEYWORDS = setOf("porcentaje", "%", "rm")
private val FAILURE_KEYWORDS = setOf("fallo", "falla")
private val FAILURE_PHRASES = setOf("al fallo", "llegue al fallo", "llegar al fallo")
private val FAILED_SET_PHRASES = setOf("serie fallida", "no pude completarla", "falle el intento", "intento fallido")
private val DROP_SET_KEYWORDS = setOf("dropset", "drop-set", "descendente")
private val REST_PAUSE_KEYWORDS = setOf("rest-pause", "restpause", "pausa-descanso")
private val TECHNIQUE_KEYWORDS = DROP_SET_KEYWORDS + REST_PAUSE_KEYWORDS
private val LASTRE_KEYWORDS = setOf("lastre", "lastrado", "lastrada")
private val ASSISTED_LOAD_KEYWORDS = setOf("asistencia", "contrapeso")
private val NORMAL_LOAD_KEYWORDS = setOf("carga")
private val BODYWEIGHT_PHRASES = setOf("peso corporal", "sin carga")
private val FAILURE_DISTANCE_KEYWORDS = setOf("recamara", "recamaras", "reserva", "reservas")
private val DISTANCE_KEYWORDS = setOf("metro", "metros", "kilometro", "kilometros", "km", "milla", "millas")
private val CUSTOM_UNIT_KEYWORDS = setOf("unidad", "unidades", "caloria", "calorias", "vuelta", "vueltas", "piso", "pisos", "nivel", "niveles", "paso", "pasos")
private val ROM_KEYWORDS = setOf("rom", "rango", "recorrido")
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

internal suspend fun parseWorkoutVoiceTranscriptAsync(
    transcript: String,
    isTimeMode: Boolean,
    isUnilateral: Boolean,
    unitMode: UnitModeV2 = if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
    customUnit: String? = null,
    trackRom: Boolean = false,
): WorkoutVoiceInterpretation? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    parseWorkoutVoiceTranscript(transcript, isTimeMode, isUnilateral, unitMode, customUnit, trackRom)
}
