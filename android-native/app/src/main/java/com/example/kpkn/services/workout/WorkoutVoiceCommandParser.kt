package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object WorkoutVoiceCommandParser {

    private val CONFIRM_KEYWORDS = setOf(
        "si", "sí", "confirmar", "confirmado", "dale", "ok", "okey",
        "listo", "correcto", "eso", "exacto", "aplicar", "aceptar",
        "registrar", "guardar", "bueno", "bien", "perfecto",
    )

    private val CANCEL_KEYWORDS = setOf(
        "no", "cancelar", "corregir", "borrar", "mal",
        "equivocado", "error", "descartar", "anular", "nulo",
        "cancelado", "niego",
    )

    private val SKIP_SET_KEYWORDS = setOf(
        "saltar serie", "omitir serie", "saltar set", "omitir set",
        "pasar serie", "avanzar serie",
    )

    private val SKIP_KEYWORDS = setOf(
        "saltar", "siguiente", "omitir", "pasar", "adelante",
        "siguiente ejercicio", "proximo", "próximo", "avanzar",
        "saltar ejercicio", "omitir ejercicio", "pasar ejercicio",
    )

    private val PREVIOUS_KEYWORDS = setOf(
        "anterior", "volver", "regresar", "atras", "atrás",
        "retroceder", "antes",
    )

    private val SUGGEST_WEIGHT_KEYWORDS = setOf(
        "cuanto peso", "cuánto peso", "carga sugerida", "que peso",
        "qué peso", "cuanto pongo", "cuánto pongo", "peso sugerido",
        "cuanto levanto", "cuánto levanto",
    )

    private val REST_STATUS_KEYWORDS = setOf(
        "cuanto falta", "cuánto falta", "descanso", "timer", "tiempo",
        "cronometro", "cronómetro", "cuanto queda", "cuánto queda",
        "restante", "falta",
    ) + WorkoutVoiceGrammarLexicon.restStatusAliases

    private val WHAT_EXERCISE_KEYWORDS = setOf(
        "que toca", "qué toca", "que ejercicio", "qué ejercicio",
        "donde voy", "dónde voy", "cual sigue", "cuál sigue",
        "en que voy", "en qué voy",
    )

    private val NEXT_EXERCISE_KEYWORDS = setOf(
        "que sigue", "qué sigue", "proximo ejercicio", "próximo ejercicio",
        "despues", "después",
    )

    private val TURN_OFF_VOICE_KEYWORDS = setOf(
        "apagar voz", "silencio", "desactivar voz", "apagar microfono",
        "apagar micrófono", "callar",
    ) + WorkoutVoiceGrammarLexicon.turnOffVoiceAliases

    private val FINISH_SESSION_KEYWORDS = setOf(
        "finalizar sesion", "finalizar entrenamiento", "terminar sesion",
        "terminar entrenamiento", "acabar sesion", "finalizar", "terminar",
    )

    private val CANCEL_SESSION_KEYWORDS = setOf(
        "cancelar sesion", "cancelar entrenamiento", "descartar entrenamiento",
        "descartar sesion", "eliminar entrenamiento",
    )

    private val ADD_SET_KEYWORDS = setOf(
        "anade una serie", "añade una serie", "anadir serie", "añadir serie",
        "agregar serie", "agrega serie", "serie extra", "otra serie",
        "una serie mas", "una serie más", "suma una serie", "sumar serie",
    ) + WorkoutVoiceGrammarLexicon.addSetAliases

    private val ADD_SET_SESSION_ONLY_KEYWORDS = setOf(
        "solo esta", "solo sesion", "solo esta sesion", "esta vez",
        "esta sesion", "temporal", "solo ahora", "no permanente",
    )

    private val ADD_SET_PERMANENT_KEYWORDS = setOf(
        "permanente", "para siempre", "guardar permanente", "siempre",
        "en el programa", "al programa", "definitivo",
    )

    private val SKIP_REST_KEYWORDS = setOf(
        "saltar descanso", "saltar timer", "omitir descanso", "omitir timer",
        "ya estoy", "continuar", "listo",
    ) + WorkoutVoiceGrammarLexicon.skipRestAliases

    private val USE_ADAPTIVE_REST_KEYWORDS = setOf(
        "usar sugerido", "descanso dinamico", "descanso dinámico",
        "usar adaptativo", "usar descanso adaptativo", "usar sugerencia",
    ) + WorkoutVoiceGrammarLexicon.adaptiveRestAliases

    private val UNDO_KEYWORDS = setOf(
        "corregir", "deshacer", "borra eso", "borrar eso", "deshacer serie",
    )

    private val EDIT_LAST_SET_TRIGGERS = setOf(
        "cambialo", "cámbialo", "cambia a", "cambialo a", "cámbialo a",
        "cambia el peso", "en realidad", "eran", "era",
        "sube", "baja", "aumenta", "reduce",
    ) + WorkoutVoiceGrammarLexicon.editLastSetAliases

    private val FATIGUE_KEYWORDS = setOf(
        "estoy fatigado", "estoy cansado", "voy muerto", "voy fatigado",
        "muy fatigado", "demasiado cansado", "no doy mas", "no doy más",
    )

    private val PACE_STATUS_KEYWORDS = setOf(
        "voy atrasado", "como voy de tiempo", "cómo voy de tiempo",
        "voy retrasado", "ritmo de sesion", "ritmo de sesión", "como voy",
    )

    private val WEIGHT_REASON_KEYWORDS = setOf(
        "por que esa carga", "por qué esa carga", "por que ese peso",
        "por qué ese peso", "explica la carga", "motivo del peso",
    )

    private val STOP_SPEAKING_KEYWORDS = setOf("para", "calla", "silencio ya", "basta")

    fun grammarTokensForStage(
        stage: VoicePipelineStage,
        includeFeedback: Boolean = false,
    ): Set<String> {
        val base = mutableSetOf<String>()
        when (stage) {
            VoicePipelineStage.CONFIRM_WAIT -> {
                base += CONFIRM_KEYWORDS
                base += CANCEL_KEYWORDS
                base += STOP_SPEAKING_KEYWORDS
            }
            else -> {
                base += STOP_SPEAKING_KEYWORDS
                base += SKIP_SET_KEYWORDS
                base += SKIP_KEYWORDS
                base += PREVIOUS_KEYWORDS
                base += SUGGEST_WEIGHT_KEYWORDS
                base += REST_STATUS_KEYWORDS
                base += WHAT_EXERCISE_KEYWORDS
                base += NEXT_EXERCISE_KEYWORDS
                base += TURN_OFF_VOICE_KEYWORDS
                base += FINISH_SESSION_KEYWORDS
                base += CANCEL_SESSION_KEYWORDS
                base += ADD_SET_KEYWORDS
                base += SKIP_REST_KEYWORDS
                base += USE_ADAPTIVE_REST_KEYWORDS
                base += UNDO_KEYWORDS
                base += EDIT_LAST_SET_TRIGGERS
                base += FATIGUE_KEYWORDS
                base += PACE_STATUS_KEYWORDS
                base += WEIGHT_REASON_KEYWORDS
                base += ADD_SET_SESSION_ONLY_KEYWORDS
                base += ADD_SET_PERMANENT_KEYWORDS
            }
        }
        if (includeFeedback) {
            base += setOf(
                "guardar", "guardar y terminar", "guardar entrenamiento", "guardar sesion",
                "terminar entrenamiento", "finalizar entrenamiento", "finalizar sesion",
                "calidad", "tecnica", "ejecucion", "intensidad", "rpe", "esfuerzo",
                "fatiga", "molestia", "dolor", "tiron", "hombro", "rodilla", "codo",
                "lumbar", "espalda baja", "muneca", "muñeca", "cadera", "tobillo",
                "sin molestia", "todo bien", "nota", "comentario", "observacion",
                "observación", "neural", "nerviosa", "cns", "espinal", "columna", "espalda",
            )
        }
        if (stage != VoicePipelineStage.CONFIRM_WAIT) {
            base += defaultNumericGrammarTokens()
        }
        // Conservar formas con tilde (modelo español) y también sin tilde.
        return buildSet {
            for (token in base) {
                val trimmed = token.trim()
                if (trimmed.isBlank()) continue
                add(trimmed.lowercase())
                add(normalizeText(trimmed))
            }
        }
    }

    fun defaultNumericGrammarTokens(): Set<String> = buildSet {
        addAll(VOICE_INTEGER_WORDS.keys)
        addAll(setOf("punto", "coma", "medio", "media", "kilo", "kilos", "peso", "carga"))
        addAll(setOf("repeticion", "repetición", "repeticiones", "segundo", "segundos"))
        addAll(setOf(
            "minuto", "minutos", "esfuerzo", "intensidad", "reservas", "reserva", "ritmo", "porcentaje", "por",
            "erre pe e", "erre i erre", "repeticiones en reserva",
        ))
        addAll(setOf("metro", "metros", "kilometro", "kilómetros", "milla", "millas"))
        addAll(setOf("unidad", "unidades", "caloria", "calorías", "vuelta", "vueltas", "etiqueta"))
        addAll(setOf("rom", "rango", "recorrido"))
        addAll(setOf(
            "izquierda", "izquierdo", "derecha", "derecho", "fallo", "falla", "serie fallida",
            "con ayuda", "dropset", "drop set", "rest pause", "pausa descanso", "lastre", "contrapeso", "asistencia", "peso corporal",
            "recamara", "recámara", "descendente", "rpe", "rir",
        ))
        // No añadir "0".."120" ni abreviaturas (rpe/reps/kg): Vosk small-es las descarta.
    }

    fun parseCommand(
        transcript: String,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
        hasPendingConfirmation: Boolean,
        isRestTimerActive: Boolean,
        pendingAddSetPersistence: Boolean = false,
        unitMode: UnitModeV2 = if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
        customUnit: String? = null,
        trackRom: Boolean = false,
        tagNames: Set<String> = emptySet(),
    ): VoiceSessionCommand {
        val lower = normalizeText(transcript)

        if (pendingAddSetPersistence) {
            return parseAddSetPersistence(lower)
        }

        if (hasPendingConfirmation) {
            if (matchesAnyKeyword(lower, CONFIRM_KEYWORDS)) {
                return VoiceSessionCommand.Confirm
            }
            if (matchesAnyKeyword(lower, CANCEL_KEYWORDS)) {
                return VoiceSessionCommand.Cancel
            }
            if (STOP_SPEAKING_KEYWORDS.any { matchesAnyKeyword(lower, setOf(it)) }) {
                return VoiceSessionCommand.StopSpeaking
            }
        }

        if (isRestTimerActive) {
            parseRestAwareCommand(lower)?.let { return it }
        }

        if (isTimeMode && lower in setOf("iniciar", "inicia", "empezar", "empieza", "comenzar", "comienza")) {
            return VoiceSessionCommand.StartTimedSet
        }
        if (isTimeMode && lower in setOf("para", "parar", "detener", "deten")) {
            return VoiceSessionCommand.StopTimedSet
        }
        if (lower in setOf("hecha", "hecho", "completada", "completado", "movilidad hecha", "aproximacion hecha")) {
            return VoiceSessionCommand.CompletePreparationStep
        }

        if (STOP_SPEAKING_KEYWORDS.any { matchesAnyKeyword(lower, setOf(normalizeText(it))) }) {
            return VoiceSessionCommand.StopSpeaking
        }

        parseEditLastSet(lower)?.let { return it }

        if (FATIGUE_KEYWORDS.any { lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.FatigueAdvice
        }
        if (PACE_STATUS_KEYWORDS.any { lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.PaceStatus
        }
        if (WEIGHT_REASON_KEYWORDS.any { lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.SuggestWeightReasoned
        }

        val hasTemporalContext = lower.contains("minuto") || lower.contains("minutos") ||
            lower.contains("segundo") || lower.contains("segundos") ||
            lower.contains("hora") || lower.contains("horas")
        val isSessionLimitIntent = lower.contains("quiero entrenar") ||
            lower.contains("limite de sesion") || lower.contains("limite de entrenamiento")
        if ((lower.contains("maximo") && hasTemporalContext) || isSessionLimitIntent) {
            val minutes = extractNumberFromText(lower)?.toInt()
            if (minutes != null && minutes >= 5) {
                return VoiceSessionCommand.SetSessionTimeLimit(
                    minutes = minutes,
                    persistToProgram = lower.contains("guardar este limite en el programa"),
                )
            }
        }

        if (lower.contains("dejar hasta aca") || lower.contains("dejarlo hasta aca")) {
            return VoiceSessionCommand.LeaveUpToHere
        }
        if (FINISH_SESSION_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.FinishSession
        }

        if (CANCEL_SESSION_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.CancelSession
        }

        if (TURN_OFF_VOICE_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.TurnOffVoice
        }

        if (ADD_SET_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.AddSet
        }

        val parsedTag = parseTagCommand(lower, tagNames)

        if (SKIP_SET_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.SkipSet
        }

        if (SKIP_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.SkipExercise
        }

        if (PREVIOUS_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.PreviousExercise
        }

        Regex("(?:ir|ve|cambiar|continuar)\\s+(?:a|con)\\s+(.+)").find(lower)?.groupValues?.getOrNull(1)
            ?.trim()?.takeIf(String::isNotBlank)?.let { return VoiceSessionCommand.GoToExercise(it) }

        if (SUGGEST_WEIGHT_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.SuggestWeight
        }

        if (REST_STATUS_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.RestStatus
        }

        if (WHAT_EXERCISE_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.WhatExercise
        }

        if (NEXT_EXERCISE_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.NextExercise
        }

        val interpretation = com.example.kpkn.screens.workout.parseWorkoutVoiceTranscript(
            transcript, isTimeMode, isUnilateral, unitMode, customUnit, trackRom,
        )
        if (interpretation != null) {
            return VoiceSessionCommand.RegisterSet(
                interpretation.copy(tagName = parsedTag?.tagName),
            )
        }
        if (parsedTag != null) return parsedTag

        return VoiceSessionCommand.Unknown(transcript)
    }

    private fun parseTagCommand(normalized: String, knownTagNames: Set<String>): VoiceSessionCommand.ApplyTag? {
        val spoken = Regex("""\betiqueta\s+(.+)$""").find(normalized)?.groupValues?.get(1)
            ?.trim(' ', '.', ',')?.take(40) ?: return null
        if (spoken.isBlank()) return null
        return VoiceSessionCommand.ApplyTag(knownTagNames.firstOrNull { normalizeText(it) == spoken } ?: spoken)
    }

    fun parseEditLastSet(normalized: String): VoiceSessionCommand.EditLastSet? {
        val lower = normalizeText(normalized)
        val triggered = EDIT_LAST_SET_TRIGGERS.any { lower.contains(normalizeText(it)) }
        if (!triggered) return null

        var weightKg: Double? = null
        var weightDeltaKg: Double? = null
        var metricValue: Int? = null
        var intensityValue: Double? = null
        var intensityKind: com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind? = null

        val wordAlts = VOICE_INTEGER_WORDS.keys.joinToString("|")
        val numOrWord = """(\d+(?:[.,]\d+)?|$wordAlts)"""

        val absoluteWeight = Regex(
            """(?:cambialo a|cambia a|cambialo|en realidad)\s+$numOrWord(?:\s*(?:kilos?|kg))?""",
        ).find(lower)
        if (absoluteWeight != null) {
            weightKg = parseSpokenInteger(absoluteWeight.groupValues[1])?.toDouble()
                ?: absoluteWeight.groupValues[1].replace(',', '.').toDoubleOrNull()
        }

        val deltaUp = Regex(
            """(?:sube|aumenta)\s+$numOrWord(?:\s*(?:kilos?|kg))?""",
        ).find(lower)
        if (deltaUp != null) {
            weightDeltaKg = parseSpokenInteger(deltaUp.groupValues[1])?.toDouble()
                ?: deltaUp.groupValues[1].replace(',', '.').toDoubleOrNull()
        }
        val deltaDown = Regex(
            """(?:baja|reduce)\s+$numOrWord(?:\s*(?:kilos?|kg))?""",
        ).find(lower)
        if (deltaDown != null) {
            val n = parseSpokenInteger(deltaDown.groupValues[1])?.toDouble()
                ?: deltaDown.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (n != null) weightDeltaKg = -n
        }

        // "eran 9" / "era nueve"
        val repsWere = Regex(
            """(?:eran|era)\s+(\d+|$wordAlts)(?:\s*(?:reps?|repeticiones?))?""",
        ).find(lower)
        if (repsWere != null) {
            metricValue = parseSpokenInteger(repsWere.groupValues[1])
        }

        // Natural multi-field: "82 por 9", "82 x 9", "82 kilos por nueve reps"
        if (metricValue == null) {
            val porReps = Regex(
                """(?:por|x|×)\s+(\d+|$wordAlts)(?:\s*(?:reps?|repeticiones?))?""",
            ).find(lower)
            if (porReps != null) {
                metricValue = parseSpokenInteger(porReps.groupValues[1])
            }
        }
        if (metricValue == null) {
            val bareReps = Regex(
                """(?:^|\s)(\d+|$wordAlts)\s*(?:reps?|repeticiones?)\b""",
            ).find(lower)
            if (bareReps != null) {
                metricValue = parseSpokenInteger(bareReps.groupValues[1])
            }
        }

        val rpeMatch = Regex("""rpe\s+(\d+(?:[.,]\d+)?)""").find(lower)
        if (rpeMatch != null) {
            intensityValue = rpeMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            intensityKind = com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RPE
        }
        val rirMatch = Regex("""rir\s+(\d+)""").find(lower)
        if (rirMatch != null) {
            intensityValue = rirMatch.groupValues[1].toDoubleOrNull()
            intensityKind = com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RIR
        }

        val patch = VoiceSetEditPatch(
            weightKg = weightKg,
            weightDeltaKg = weightDeltaKg,
            metricValue = metricValue,
            intensityValue = intensityValue,
            intensityKind = intensityKind,
        )
        if (!patch.hasAnyField) return null
        return VoiceSessionCommand.EditLastSet(patch)
    }

    /**
     * During an active rest timer: "saltar" means skip rest (not the exercise),
     * plus adaptive / adjust / undo commands.
     */
    fun parseRestAwareCommand(normalized: String): VoiceSessionCommand? {
        val lower = normalized
        if (USE_ADAPTIVE_REST_KEYWORDS.any { lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.UseAdaptiveRest
        }
        parseAdjustRestTime(lower)?.let { return it }
        if (UNDO_KEYWORDS.any { matchesAnyKeyword(lower, setOf(normalizeText(it))) || lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.UndoLastSet
        }
        if (SKIP_REST_KEYWORDS.any { lower.contains(normalizeText(it)) } ||
            SKIP_KEYWORDS.any { lower.contains(it) }
        ) {
            return VoiceSessionCommand.SkipRest
        }
        return null
    }

    fun parseAdjustRestTime(normalized: String): VoiceSessionCommand.AdjustRestTime? {
        val lower = normalized
        val addMatch = Regex(
            """(?:anade|añade|suma|agrega|mas|más)\s+(\d+|""" +
                VOICE_INTEGER_WORDS.keys.joinToString("|") +
                """)\s*(?:segundos?|segs?)?""",
        ).find(lower)
        val removeMatch = Regex(
            """(?:quita|resta|saca|menos)\s+(\d+|""" +
                VOICE_INTEGER_WORDS.keys.joinToString("|") +
                """)\s*(?:segundos?|segs?)?""",
        ).find(lower)

        when {
            addMatch != null -> {
                val n = parseSpokenInteger(addMatch.groupValues[1]) ?: return null
                return VoiceSessionCommand.AdjustRestTime(n)
            }
            removeMatch != null -> {
                val n = parseSpokenInteger(removeMatch.groupValues[1]) ?: return null
                return VoiceSessionCommand.AdjustRestTime(-n)
            }
            lower.contains("mas treinta") || lower.contains("más treinta") ||
                lower.contains("mas 30") || lower.contains("más 30") -> {
                return VoiceSessionCommand.AdjustRestTime(30)
            }
            lower.contains("menos treinta") || lower.contains("menos 30") -> {
                return VoiceSessionCommand.AdjustRestTime(-30)
            }
            lower.contains("mas quince") || lower.contains("más quince") ||
                lower.contains("mas 15") || lower.contains("más 15") -> {
                return VoiceSessionCommand.AdjustRestTime(15)
            }
            lower.contains("menos quince") || lower.contains("menos 15") -> {
                return VoiceSessionCommand.AdjustRestTime(-15)
            }
        }
        return null
    }

    private fun parseSpokenInteger(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        return VOICE_INTEGER_WORDS[normalizeText(token)]
    }

    fun parseAddSetPersistence(normalizedTranscript: String): VoiceSessionCommand {
        val lower = normalizedTranscript
        if (ADD_SET_SESSION_ONLY_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.AddSetSessionOnly
        }
        if (ADD_SET_PERMANENT_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.AddSetPermanent
        }
        return VoiceSessionCommand.Unknown(normalizedTranscript)
    }

    private fun parseWorkoutVoiceTranscript(
        transcript: String,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
    ): WorkoutVoiceInterpretation? {
        val tokens = normalizeWorkoutVoiceTranscript(transcript)
        if (tokens.isEmpty()) return null

        val explicitWeight = tokens.indexOfFirst { it in WEIGHT_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it, preferBackward = true) }
        val explicitReps = tokens.indexOfFirst { it in REP_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber() }
        val explicitSeconds = tokens.indexOfFirst { it in SECOND_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber() }
        val explicitMinutes = tokens.indexOfFirst { it in MINUTE_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it, preferBackward = true)?.toSafeWholeNumber()?.times(60) }
        val explicitRpe = tokens.indexOfFirst { it in RPE_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it) }
        val explicitRir = tokens.indexOfFirst { it in RIR_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it) }
        val explicitPercentRm = tokens.indexOfFirst { it in PERCENT_RM_WORDS }
            .takeIf { it >= 0 }
            ?.let { nearestVoiceNumber(tokens, it) }
        val connectorPair = extractConnectedWeightAndMetric(tokens)

        val side = when {
            !isUnilateral -> null
            tokens.any { it in LEFT_SIDE_WORDS } && tokens.none { it in RIGHT_SIDE_WORDS } -> "left"
            tokens.any { it in RIGHT_SIDE_WORDS } && tokens.none { it in LEFT_SIDE_WORDS } -> "right"
            else -> null
        }
        val reachedFailure = tokens.any { it in FAILURE_WORDS }

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
            else -> null
        }
        val intensityKind = when {
            explicitRpe != null -> WorkoutVoiceIntensityKind.RPE
            explicitRir != null -> WorkoutVoiceIntensityKind.RIR
            explicitPercentRm != null -> WorkoutVoiceIntensityKind.PERCENT_RM
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

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-záéíóúüñ0-9.,% -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Exact token match for single words; substring phrase match for multi-word keywords. */
    private fun matchesAnyKeyword(normalized: String, keywords: Set<String>): Boolean {
        if (normalized.isBlank()) return false
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        return keywords.any { keyword ->
            if (keyword.contains(' ')) {
                normalized.contains(keyword)
            } else {
                tokens.any { it == keyword }
            }
        }
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
            if (token !in CONNECTOR_WORDS) return@forEachIndexed
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

    private val CONNECTOR_WORDS = setOf("x", "por")
    private val WEIGHT_WORDS = setOf("kg", "kilo", "kilos", "peso", "carga", "lastre", "asistencia")
    private val REP_WORDS = setOf("rep", "reps", "repeticion", "repeticiones")
    private val SECOND_WORDS = setOf("seg", "segundo", "segundos")
    private val MINUTE_WORDS = setOf("min", "minuto", "minutos")
    private val RPE_WORDS = setOf("rpe", "esfuerzo", "intensidad")
    private val RIR_WORDS = setOf("rir", "recamara", "recamaras", "reserva", "reservas")
    private val PERCENT_RM_WORDS = setOf("porcentaje", "%", "rm")
    private val FAILURE_WORDS = setOf("fallo", "falla")
    private val LEFT_SIDE_WORDS = setOf("izquierda", "izquierdo", "izq")
    private val RIGHT_SIDE_WORDS = setOf("derecha", "derecho", "der")

    private val VOICE_INTEGER_WORDS = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3,
        "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18,
        "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
        "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100,
    )

    private val VOICE_DECIMAL_DIGITS = mapOf(
        "cero" to '0', "un" to '1', "uno" to '1', "una" to '1',
        "dos" to '2', "tres" to '3', "cuatro" to '4', "cinco" to '5',
        "seis" to '6', "siete" to '7', "ocho" to '8', "nueve" to '9',
    )

    fun parseFeedbackCommand(transcript: String): VoiceSessionCommand.LogFeedback {
        val lower = normalizeText(transcript)

        val saveKeywords = setOf("guardar", "listo", "ok", "guardar feedback", "terminar feedback", "completar")
        val isSaveAction = saveKeywords.any { lower.contains(it) }

        var technicalQuality: Int? = null
        if (lower.contains("calidad") || lower.contains("tecnica") || lower.contains("ejecucion")) {
            technicalQuality = when {
                lower.contains("excelente") || lower.contains("perfecta") -> 10
                lower.contains("muy buena") -> 9
                lower.contains("buena") -> 8
                lower.contains("regular") || lower.contains("mas o menos") -> 6
                lower.contains("mala") || lower.contains("pesima") -> 3
                else -> extractNumberFromText(lower)?.toInt()?.coerceIn(1, 10)
            }
        }

        var perceivedIntensity: Double? = null
        if (lower.contains("intensidad") || lower.contains("rpe") || lower.contains("esfuerzo") || lower.contains("fatiga")) {
            perceivedIntensity = extractNumberFromText(lower)?.coerceIn(1.0, 10.0)
        }

        var discomfortId: String? = null
        var discomfortCandidates: Map<String, String> = emptyMap()
        if (lower.contains("molestia") || lower.contains("dolor") || lower.contains("tiron")) {
            val matches = matchDiscomfortCandidates(lower)
            discomfortId = matches.singleOrNull()?.id
            discomfortCandidates = if (matches.size > 1) matches.associate { it.id to it.label } else emptyMap()
        }

        return VoiceSessionCommand.LogFeedback(
            technicalQuality = technicalQuality,
            discomfortId = discomfortId,
            perceivedIntensity = perceivedIntensity,
            isSaveAction = isSaveAction,
            exerciseSearchName = lower,
            discomfortCandidates = discomfortCandidates,
        )
    }

    fun parseFinalFeedbackCommand(transcript: String): VoiceSessionCommand.LogFinalFeedback {
        val lower = normalizeText(transcript)

        val saveKeywords = setOf("guardar y terminar", "guardar entrenamiento", "guardar sesion", "terminar entrenamiento", "finalizar entrenamiento", "finalizar sesion")
        val isSaveAction = saveKeywords.any { lower.contains(it) }

        var neural: Int? = null
        var spinal: Int? = null
        if (lower.contains("nerviosa") || lower.contains("neural") || lower.contains("cns") || lower.contains("sistema")) {
            neural = extractNumberFromText(lower)?.toInt()?.coerceIn(0, 100)
        }
        if (lower.contains("espinal") || lower.contains("columna") || lower.contains("espalda")) {
            spinal = extractNumberFromText(lower)?.toInt()?.coerceIn(0, 100)
        }

        var discomfortId: String? = null
        if (lower.contains("molestia") || lower.contains("dolor") || lower.contains("tiron")) {
            discomfortId = matchDiscomfortJointId(lower)
        }

        var discomfortNote: String? = null
        val discomfortNoteKeywords = listOf("nota de molestia", "notas de molestia", "detalles de molestia", "detalle de molestia", "detalles de la molestia")
        for (keyword in discomfortNoteKeywords) {
            if (lower.contains(keyword)) {
                val index = lower.indexOf(keyword) + keyword.length
                if (index < lower.length) {
                    discomfortNote = lower.substring(index).trim().removePrefix(":").trim()
                    break
                }
            }
        }

        var sessionNote: String? = null
        val noteKeywords = listOf("nota de sesion", "notas de sesion", "comentario de sesion", "comentarios de sesion", "comentario", "comentarios", "nota", "notas", "observacion", "observaciones")
        if (discomfortNote == null) {
            for (keyword in noteKeywords) {
                if (lower.contains(keyword)) {
                    val index = lower.indexOf(keyword) + keyword.length
                    if (index < lower.length) {
                        sessionNote = lower.substring(index).trim().removePrefix(":").trim()
                        break
                    }
                }
            }
        }

        return VoiceSessionCommand.LogFinalFeedback(
            notes = sessionNote,
            discomfortId = discomfortId,
            additionalDiscomfortNote = discomfortNote,
            neuralBattery = neural,
            spinalBattery = spinal,
            isSaveAction = isSaveAction
        )
    }

    private fun matchDiscomfortJointId(text: String): String? {
        return matchDiscomfortCandidates(text).singleOrNull()?.id
    }

    private fun matchDiscomfortCandidates(text: String) = when {
        text.contains("ninguna") || text.contains("sin molestia") || text.contains("todo bien") ->
            DISCOMFORT_CATALOG.filter { it.id == "none" }
        else -> DISCOMFORT_CATALOG.filter { entry ->
            val haystack = normalizeText("${entry.label} ${entry.description} ${entry.section.label}")
            val relevant = normalizeText(text).split(' ').filter { it.length >= 4 && it !in setOf("dolor", "molestia", "tengo") }
            relevant.any(haystack::contains)
        }.take(3)
    }

    private fun extractNumberFromText(text: String): Double? {
        val match = Regex("\\d+(?:[.,]\\d+)?").find(text)
        if (match != null) {
            return match.value.replace(',', '.').toDoubleOrNull()
        }
        val tokens = text.split(" ")
        for (token in tokens) {
            val normalizedToken = token.trim()
            if (VOICE_INTEGER_WORDS.containsKey(normalizedToken)) {
                return VOICE_INTEGER_WORDS.getValue(normalizedToken).toDouble()
            }
        }
        return null
    }
}
