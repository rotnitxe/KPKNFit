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
import kotlin.math.pow
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
    /** Cardio-only distance captured from phrases such as "cinco kilómetros". */
    val distanceKm: Double? = null,
    /** Cardio-only average heart rate captured from phrases such as "FC 150". */
    val averageHeartRate: Int? = null,
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
    /** "solo la barra": el peso real se resuelve con barWeightKg del ejercicio. */
    val isBarWeightOnly: Boolean = false,
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
    allowCardioMetrics: Boolean = false,
): WorkoutVoiceInterpretation? {
    val rawNormalized = normalizeWorkoutVoiceTranscriptString(transcript)
    val correctedText = com.example.kpkn.services.workout.WorkoutVoiceMishearingCorrections.correct(rawNormalized)
    val tokens = correctedText.split(' ').filter { it.isNotBlank() }
    if (correctedText != rawNormalized) {
        com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.event(
            "transcript_corrected",
            mapOf("from" to rawNormalized, "to" to correctedText),
        )
    }
    if (tokens.isEmpty()) return null

    val customUnitKeywords = normalizeWorkoutVoiceTranscript(customUnit.orEmpty()).toSet() + CUSTOM_UNIT_KEYWORDS
    val effectiveUnitMode = if (isTimeMode) UnitModeV2.TIME else unitMode
    val explicitWeight = tokens.indexOfFirst { it in WEIGHT_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let {
            nearestVoiceNumber(
                tokens, it,
                preferBackward = true,
                allowGymDecimal = true,
                forwardUnitGuard = NON_WEIGHT_UNIT_KEYWORDS + customUnitKeywords,
            )
        }
    val spokenDecimalWeight = if (explicitWeight == null && effectiveUnitMode == UnitModeV2.REPS) {
        extractExplicitDecimalWeight(tokens)
    } else {
        null
    }
    val techniqueStart = tokens.indexOfFirst { it in TECHNIQUE_KEYWORDS }.takeIf { it >= 0 } ?: tokens.size
    val mainTokens = tokens.take(techniqueStart)
    val explicitReps = mainTokens.indexOfFirst { it in REP_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let {
            nearestVoiceNumber(mainTokens, it, preferBackward = true, wholeNumberOnly = true)
                ?.toSafeWholeNumber()
        }
    val explicitSeconds = tokens.indexOfFirst { it in SECOND_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let {
            nearestVoiceNumber(tokens, it, preferBackward = true, wholeNumberOnly = true)
                ?.toSafeWholeNumber()
        }
    val explicitMinutes = tokens.indexOfFirst { it in MINUTE_KEYWORDS }
        .takeIf { it >= 0 }
        ?.let {
            nearestVoiceNumber(tokens, it, preferBackward = true, wholeNumberOnly = true)
                ?.toSafeWholeNumber()
                ?.times(60)
        }
    val distanceKeywordIndex = tokens.indexOfFirst { it in DISTANCE_KEYWORDS }.takeIf { it >= 0 }
    val explicitDistance = distanceKeywordIndex?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val cardioDistanceKm = if (allowCardioMetrics) {
        explicitDistance?.let { value ->
            if (tokens.getOrNull(distanceKeywordIndex ?: -1) in MILE_DISTANCE_KEYWORDS) value * MILES_TO_KM else value
        }
    } else {
        null
    }
    val cardioHeartRate = if (allowCardioMetrics) {
        tokens.indexOfFirst { it in HEART_RATE_KEYWORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it, preferBackward = true, wholeNumberOnly = true) }
            ?.toSafeWholeNumber()
            ?.coerceIn(30, 240)
    } else {
        null
    }
    val explicitCustom = tokens.indexOfFirst { it in customUnitKeywords }.takeIf { it >= 0 }?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
    val normalizedText = tokens.joinToString(" ")
    val barOnlyPhrase = BAR_ONLY_PHRASES.any(normalizedText::contains)
    val barWeightOnly = barOnlyPhrase && explicitWeight == null && spokenDecimalWeight == null
    val mancuernaWeight = equipmentWeight(tokens, MANCUERNA_KEYWORDS)
    val barraWeight = if (barOnlyPhrase) null else equipmentWeight(tokens, BAR_KEYWORDS)
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
        ?.let { nearestVoiceNumber(tokens, it, wholeNumberOnly = true)?.toSafeWholeNumber() }
        ?.takeIf { it in 0..100 } else null
    val connectorPair = extractConnectedWeightAndMetric(tokens)

    val side = when {
        !isUnilateral -> null
        tokens.any { it in LEFT_SIDE_KEYWORDS } && tokens.none { it in RIGHT_SIDE_KEYWORDS } -> "left"
        tokens.any { it in RIGHT_SIDE_KEYWORDS } && tokens.none { it in LEFT_SIDE_KEYWORDS } -> "right"
        else -> null
    }
    val isFailedSet = FAILED_SET_PHRASES.any(normalizedText::contains)
    val reachedFailure = !isFailedSet && (
        FAILURE_PHRASES.any(normalizedText::contains) || tokens.any { it in FAILURE_KEYWORDS }
    )
    val helpedReps = extractNumberBeforePhrase(tokens, listOf("con", "ayuda"))?.toSafeWholeNumber()
        ?: tokens.indexOfFirst { it in ASSISTED_REPS_KEYWORDS }
            .takeIf { it >= 0 }
            ?.let { readVoiceNumberBackward(tokens, it - 1, wholeNumberOnly = true)?.first?.toSafeWholeNumber() }
    val loadModeOverride = when {
        tokens.any { it in LASTRE_KEYWORDS } -> LoadModeV2.LASTRE
        tokens.any { it in ASSISTED_LOAD_KEYWORDS } -> LoadModeV2.ASSISTED
        BODYWEIGHT_PHRASES.any(normalizedText::contains) -> LoadModeV2.BODYWEIGHT
        explicitWeight == 0.0 -> LoadModeV2.BODYWEIGHT
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

    val equipmentWeightKg = mancuernaWeight ?: barraWeight
    val weightKg = if (effectiveUnitMode != UnitModeV2.REPS) {
        explicitWeight ?: equipmentWeightKg
    } else {
        explicitWeight ?: spokenDecimalWeight ?: equipmentWeightKg ?: connectorPair?.first
    }
    val metricDecimalValue = when (effectiveUnitMode) {
        UnitModeV2.TIME -> (explicitSeconds ?: explicitMinutes)?.toDouble() ?: connectorPair?.second
        UnitModeV2.REPS -> explicitReps?.toDouble() ?: connectorPair?.second
        UnitModeV2.DISTANCE -> explicitDistance ?: connectorPair?.second
        UnitModeV2.CUSTOM -> explicitCustom ?: connectorPair?.second
    }
    val metricValue = metricDecimalValue?.toSafeWholeNumber()
    val highExertion = HIGH_EXERTION_PHRASES.any(normalizedText::contains)
    val intensityValue = when {
        explicitRpe != null -> explicitRpe
        explicitRir != null -> explicitRir
        explicitPercentRm != null -> explicitPercentRm
        explicitFailureMetric != null -> explicitFailureMetric
        highExertion -> 9.0
        else -> null
    }
    val intensityKind = when {
        explicitRpe != null || highExertion -> WorkoutVoiceIntensityKind.RPE
        explicitRir != null -> WorkoutVoiceIntensityKind.RIR
        explicitPercentRm != null -> WorkoutVoiceIntensityKind.PERCENT_RM
        explicitFailureMetric != null -> WorkoutVoiceIntensityKind.RIR
        else -> null
    }
    val orphanIntensity = if (intensityValue == null && weightKg != null && metricDecimalValue != null) {
        trailingVoiceNumber(tokens)?.takeIf { it in 0.0..10.0 }
    } else null

    val fields = buildSet {
        if (weightKg != null || barWeightOnly) add(WorkoutVoiceField.WEIGHT)
        if (metricDecimalValue != null) add(WorkoutVoiceField.VALUE)
        if (cardioDistanceKm != null || cardioHeartRate != null) add(WorkoutVoiceField.VALUE)
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
        distanceKm = cardioDistanceKm,
        averageHeartRate = cardioHeartRate,
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
        isBarWeightOnly = barWeightOnly,
    )
}

/**
 * Primer número (palabra compuesta o dígito) del texto normalizado, o null.
 * Usado por la clarificación guiada para extraer la respuesta numérica del usuario.
 */
internal fun extractFirstVoiceNumber(text: String): Double? {
    val normalized = normalizeWorkoutVoiceTranscriptString(text)
    val tokens = normalized.split(' ').filter { it.isNotBlank() }
    val first = tokens.indexOfFirst { it.isVoiceNumberToken() }
    if (first < 0) return null
    val last = tokens.indexOfLast { it.isVoiceNumberToken() }
    return parseVoiceInteger(tokens.subList(first, last + 1))
}

internal fun extractFirstVoiceDecimalNumber(text: String): Double? {
    val normalized = normalizeWorkoutVoiceTranscriptString(text)
    val tokens = normalized.split(' ').filter { it.isNotBlank() }
    val first = tokens.indexOfFirst { it.isVoiceNumberToken() }
    if (first < 0) return null
    val last = tokens.indexOfLast { it.isVoiceNumberToken() }
    return parseVoiceNumberTokens(tokens.subList(first, last + 1))
}

internal fun workoutVoiceSummary(
    interpretation: WorkoutVoiceInterpretation,
    isTimeMode: Boolean,
): String = buildList {
    val bodyWeightOnly = interpretation.loadModeOverride == LoadModeV2.BODYWEIGHT && interpretation.weightKg == 0.0
    if (interpretation.weightKg != null && !bodyWeightOnly) {
        add("${interpretation.weightKg.toTrimmedNumberString()} kg")
    }
    if (bodyWeightOnly) add("Peso corporal")
    interpretation.metricValue?.let { value ->
        add(if (isTimeMode) "$value s" else "$value reps")
    }
    interpretation.distanceKm?.let { add("${it.toTrimmedNumberString()} km") }
    interpretation.averageHeartRate?.let { add("FC $it") }
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

private fun normalizeWorkoutVoiceTranscriptString(transcript: String): String {
    val normalized = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace(Regex("\\berre\\s+pe\\s+e\\b"), "rpe")
        .replace(Regex("\\berre\\s+i\\s+erre\\b"), "rir")
        .replace("repeticiones en reserva", "rir")
        .replace("×", " x ")
        .replace(Regex("\\bequis\\b"), "x")
        // RIR verbal: "me quedaron dos en reserva" / "quedaban tres" / "dos en recámara"
        .replace(Regex("""\bme quedaron (\w+) en (?:reserva|recamara)\b"""), "rir $1")
        .replace(Regex("""\bquedaban (\w+)(?: en (?:reserva|recamara))?\b"""), "rir $1")
        .replace(Regex("""\b(\w+) en (?:reserva|recamara)\b"""), "rir $1")
        .replace(Regex("[^a-z0-9.,% ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized
}

private fun normalizeWorkoutVoiceTranscript(transcript: String): List<String> =
    normalizeWorkoutVoiceTranscriptString(transcript).split(' ').filter { it.isNotBlank() }

/** "mancuernas de quince" -> 15, "barra de veinte" -> 20. */
private fun equipmentWeight(tokens: List<String>, keywords: Set<String>): Double? {
    val index = tokens.indexOfFirst { it in keywords }
    if (index < 0) return null
    val start = index + 1
    return if (tokens.getOrNull(start) == "de") {
        readVoiceNumberForward(tokens, start + 1)?.first
    } else {
        readVoiceNumberForward(tokens, start)?.first
    }
}

/** "cuarenta y siete coma veinticinco" -> 47.25, even without "kilos". */
private fun extractExplicitDecimalWeight(tokens: List<String>): Double? {
    for (index in tokens.indices) {
        if (!isVoiceDecimalSeparatorAt(tokens, index)) continue
        if (index > 0 && tokens[index - 1] in CONNECTOR_KEYWORDS) continue

        val whole = readVoiceNumberBackward(
            tokens,
            index - 1,
            wholeNumberOnly = true,
        )?.first ?: continue
        val fraction = readVoiceDecimalFractionForward(tokens, index + 1) ?: continue
        val decimalDigits = parseVoiceDecimalDigits(fraction.first) ?: continue
        val scale = 10.0.pow(decimalDigits.length)
        return whole + decimalDigits.toDouble() / scale
    }
    return null
}

private fun readVoiceDecimalFractionForward(tokens: List<String>, startIndex: Int): Pair<List<String>, Int>? {
    if (startIndex !in tokens.indices) return null
    val collected = mutableListOf<String>()
    var index = startIndex
    while (index < tokens.size && tokens[index].isVoiceNumberToken()) {
        if (collected.isNotEmpty() && (
                tokens.getOrNull(index + 1) in NON_WEIGHT_UNIT_KEYWORDS ||
                    tokens.getOrNull(index + 1) in CONNECTOR_KEYWORDS
            )
        ) break
        collected += tokens[index]
        index += 1
    }
    return collected.takeIf { it.isNotEmpty() }?.let { it to index }
}

private fun extractConnectedWeightAndMetric(tokens: List<String>): Pair<Double, Double>? {
    tokens.forEachIndexed { index, token ->
        if (token !in CONNECTOR_KEYWORDS) return@forEachIndexed
        val left = readVoiceNumberBackward(tokens, index - 1, allowGymDecimal = true)?.first
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
    return readVoiceNumberBackward(tokens, index - 1, wholeNumberOnly = true)?.first
}

private fun trailingVoiceNumber(tokens: List<String>): Double? =
    readVoiceNumberBackward(tokens, tokens.lastIndex)?.first

private fun extractDropSets(tokens: List<String>): List<DropSetData> {
    val start = tokens.indexOfFirst { it in DROP_SET_KEYWORDS }
    if (start < 0) return emptyList()
    val segment = tokens.drop(start + 1)
    val weightIndex = segment.indexOfFirst { it in WEIGHT_KEYWORDS }
    val repsIndex = segment.indexOfFirst { it in REP_KEYWORDS }
    val weight = weightIndex.takeIf { it >= 0 }?.let {
        nearestVoiceNumber(segment, it, true, allowGymDecimal = true, forwardUnitGuard = NON_WEIGHT_UNIT_KEYWORDS)
    }
    val reps = repsIndex.takeIf { it >= 0 }?.let {
        nearestVoiceNumber(segment, it, true, wholeNumberOnly = true)?.toSafeWholeNumber()
    }
    return if (weight != null && reps != null) listOf(DropSetData(weight, reps)) else emptyList()
}

private fun extractRestPauses(tokens: List<String>): List<RestPauseData> {
    val start = tokens.indexOfFirst { it in REST_PAUSE_KEYWORDS }
    if (start < 0) return emptyList()
    val segment = tokens.drop(start + 1)
    val restIndex = segment.indexOfFirst { it in SECOND_KEYWORDS }
    val repsIndex = segment.indexOfFirst { it in REP_KEYWORDS }
    val rest = restIndex.takeIf { it >= 0 }?.let {
        nearestVoiceNumber(segment, it, true, wholeNumberOnly = true)?.toSafeWholeNumber()
    }
    val reps = repsIndex.takeIf { it >= 0 }?.let {
        nearestVoiceNumber(segment, it, true, wholeNumberOnly = true)?.toSafeWholeNumber()
    }
    return if (rest != null && reps != null) listOf(RestPauseData(rest, reps)) else emptyList()
}

private fun nearestVoiceNumber(
    tokens: List<String>,
    index: Int,
    preferBackward: Boolean = false,
    allowGymDecimal: Boolean = false,
    forwardUnitGuard: Set<String>? = null,
    wholeNumberOnly: Boolean = false,
): Double? {
    val backward = readVoiceNumberBackward(tokens, index - 1, allowGymDecimal, wholeNumberOnly)
    val forward = readVoiceNumberForward(tokens, index + 1, allowGymDecimal, wholeNumberOnly)
    val guardedForward = when {
        forward == null -> null
        forwardUnitGuard == null -> forward
        forward.second >= tokens.size || tokens[forward.second] !in forwardUnitGuard -> forward
        else -> null
    }
    return if (preferBackward) backward?.first ?: guardedForward?.first
    else guardedForward?.first ?: backward?.first
}

private fun readVoiceNumberForward(
    tokens: List<String>,
    startIndex: Int,
    allowGymDecimal: Boolean = false,
    wholeNumberOnly: Boolean = false,
): Pair<Double, Int>? {
    if (startIndex !in tokens.indices) return null
    val collected = mutableListOf<String>()
    var index = startIndex
    while (index < tokens.size && tokens.isVoiceNumberComponentAt(index, allowGymDecimal)) {
        if (wholeNumberOnly && isVoiceDecimalSeparatorAt(tokens, index)) break
        collected += tokens[index]
        index += 1
    }
    val value = parseVoiceNumberTokens(collected, allowGymDecimal) ?: return null
    return value to index
}

private fun readVoiceNumberBackward(
    tokens: List<String>,
    startIndex: Int,
    allowGymDecimal: Boolean = false,
    wholeNumberOnly: Boolean = false,
): Pair<Double, Int>? {
    if (startIndex !in tokens.indices) return null
    val collected = mutableListOf<String>()
    var index = startIndex
    while (index >= 0 && tokens.isVoiceNumberComponentAt(index, allowGymDecimal)) {
        if (wholeNumberOnly && index > 0 && isVoiceDecimalSeparatorAt(tokens, index - 1)) {
            if (collected.isEmpty()) return null
            break
        }
        collected.add(0, tokens[index])
        index -= 1
    }
    val value = parseVoiceNumberTokens(collected, allowGymDecimal) ?: return null
    return value to (index + 1)
}

private fun List<String>.isVoiceNumberComponentAt(index: Int, allowGymDecimal: Boolean): Boolean {
    val token = getOrNull(index) ?: return false
    if (token.isVoiceNumberToken()) return true
    if (allowGymDecimal && token == "con") return true
    return token == "como" && isVoiceDecimalSeparatorAt(this, index)
}

private fun isVoiceDecimalSeparatorAt(tokens: List<String>, index: Int): Boolean {
    val token = tokens.getOrNull(index) ?: return false
    if (token == "coma" || token == "punto") return true
    return token == "como" &&
        tokens.getOrNull(index - 1)?.isVoiceNumberToken() == true &&
        tokens.getOrNull(index + 1)?.isVoiceNumberToken() == true
}

private fun parseVoiceNumberTokens(tokens: List<String>, allowGymDecimal: Boolean = false): Double? {
    if (tokens.isEmpty()) return null
    if (tokens.size == 1 && DIGIT_TOKEN.matches(tokens.first())) {
        return tokens.first().replace(',', '.').toDoubleOrNull()
    }

    val decimalSeparatorIdx = tokens.indexOfFirst {
        it == "punto" || it == "coma" || it == "como" || (allowGymDecimal && it == "con")
    }
    if (decimalSeparatorIdx >= 0) {
        val whole = parseVoiceInteger(tokens.take(decimalSeparatorIdx)) ?: return null
        val decimalDigits = parseVoiceDecimalDigits(tokens.drop(decimalSeparatorIdx + 1)) ?: return null
        val scale = 10.0.pow(decimalDigits.length)
        return whole + decimalDigits.toDouble() / scale
    }

    if (tokens.size == 2) {
        val tens = VOICE_INTEGER_WORDS[tokens[0]]
        val teen = VOICE_INTEGER_WORDS[tokens[1]]
        if (tens != null && tens % 10 == 0 && tens in 30..90 && teen != null && teen in 11..19) {
            return (tens + teen - 10).toDouble()
        }
    }

    return parseVoiceInteger(tokens)
}

/**
 * Dígitos decimales tras "coma"/"punto"/"como" ("veintidos coma cinco" -> "5",
 * "noventa y dos coma veinticinco" -> "25"). Solo se aplica con separador
 * explícito: sin "coma" el número es SIEMPRE entero ("ochenta cinco" -> 85).
 */
private fun parseVoiceDecimalDigits(tokens: List<String>): String? {
    if (tokens.isEmpty()) return null
    if (tokens.all { it in VOICE_DECIMAL_DIGITS }) {
        return tokens.joinToString(separator = "") { VOICE_DECIMAL_DIGITS.getValue(it).toString() }
    }
    if (tokens.all { DIGIT_TOKEN.matches(it) && !it.contains('.') && !it.contains(',') }) {
        return tokens.joinToString(separator = "")
    }
    return parseVoiceInteger(tokens)?.toInt()?.toString()
}


private fun parseVoiceInteger(tokens: List<String>): Double? {
    if (tokens.isEmpty()) return null
    var total = 0.0
    var consumed = false
    tokens.forEachIndexed { index, token ->
        when {
            token == "y" -> Unit
            token == "medio" || token == "media" -> {
                total += 0.5
                consumed = true
            }
            token == "cuarto" -> {
                total += 0.25
                consumed = true
            }
            token == "cuartos" -> {
                val n = tokens.getOrNull(index - 1)?.let(::singleVoiceInteger)
                if (n != null) {
                    total -= n
                    total += n / 4.0
                } else {
                    total += 0.75
                }
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

private fun singleVoiceInteger(token: String): Double? {
    if (DIGIT_TOKEN.matches(token)) return token.replace(',', '.').toDoubleOrNull()
    return VOICE_INTEGER_WORDS[token]?.toDouble()
}

private fun String.isVoiceNumberToken(): Boolean =
    DIGIT_TOKEN.matches(this) ||
        this in VOICE_INTEGER_WORDS ||
        this in VOICE_DECIMAL_DIGITS ||
        this == "punto" ||
        this == "coma" ||
        this == "y" ||
        this == "medio" ||
        this == "media" ||
        this == "cuarto" ||
        this == "cuartos"

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
private val FAILURE_PHRASES = setOf(
    "al fallo", "llegue al fallo", "llegar al fallo",
    // Verbal, alta intensidad máxima (D2a):
    "dandolo todo", "lo di todo", "di todo", "hasta el fallo",
    "no me quedo nada", "no quedo nada",
)
/** Cansancio alto → RPE 9 sin marcar fallo (D2b). */
private val HIGH_EXERTION_PHRASES = setOf(
    "quede muy cansado", "quede muy cansada", "muy cansado", "muy cansada",
    "sin energia", "agotado", "agotada", "quede agotado", "quede agotada",
)
private val FAILED_SET_PHRASES = setOf("serie fallida", "no pude completarla", "falle el intento", "intento fallido")
private val DROP_SET_KEYWORDS = setOf("dropset", "drop-set", "descendente")
private val REST_PAUSE_KEYWORDS = setOf("rest-pause", "restpause", "pausa-descanso")
private val TECHNIQUE_KEYWORDS = DROP_SET_KEYWORDS + REST_PAUSE_KEYWORDS
private val LASTRE_KEYWORDS = setOf("lastre", "lastrado", "lastrada")
private val ASSISTED_LOAD_KEYWORDS = setOf("asistencia", "contrapeso")
private val ASSISTED_REPS_KEYWORDS = setOf("asistida", "asistidas", "ayudada", "ayudadas")
private val MANCUERNA_KEYWORDS = setOf("mancuerna", "mancuernas")
private val BAR_KEYWORDS = setOf("barra")
private val BAR_ONLY_PHRASES = setOf("solo la barra", "la barra", "barra sola", "barra vacia", "barra vacia sin discos")
private val NORMAL_LOAD_KEYWORDS = setOf("carga")
private val BODYWEIGHT_PHRASES = setOf(
    "peso corporal", "sin carga", "sin peso", "peso del cuerpo", "con el cuerpo",
)
private val FAILURE_DISTANCE_KEYWORDS = setOf("recamara", "recamaras", "reserva", "reservas")
private val DISTANCE_KEYWORDS = setOf("metro", "metros", "kilometro", "kilometros", "km", "milla", "millas")
private val MILE_DISTANCE_KEYWORDS = setOf("milla", "millas")
private val HEART_RATE_KEYWORDS = setOf("fc", "pulso", "pulsaciones", "bpm", "frecuencia")
private const val MILES_TO_KM = 1.609344
private val CUSTOM_UNIT_KEYWORDS = setOf("unidad", "unidades", "caloria", "calorias", "vuelta", "vueltas", "piso", "pisos", "nivel", "niveles", "paso", "pasos")
private val ROM_KEYWORDS = setOf("rom", "rango", "recorrido")
private val LEFT_SIDE_KEYWORDS = setOf("izquierda", "izquierdo", "izq")
private val RIGHT_SIDE_KEYWORDS = setOf("derecha", "derecho", "der")

private val NON_WEIGHT_UNIT_KEYWORDS =
    REP_KEYWORDS + SECOND_KEYWORDS + MINUTE_KEYWORDS + DISTANCE_KEYWORDS + CUSTOM_UNIT_KEYWORDS +
        ROM_KEYWORDS + RIR_KEYWORDS + RPE_KEYWORDS + PERCENT_RM_KEYWORDS + FAILURE_KEYWORDS +
        FAILURE_DISTANCE_KEYWORDS

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
