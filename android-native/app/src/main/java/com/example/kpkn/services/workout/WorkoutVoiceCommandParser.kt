package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import java.text.Normalizer
import java.util.Locale

object WorkoutVoiceCommandParser {

    private val CONFIRM_KEYWORDS = setOf(
        "si", "sí", "confirmar", "confirmado", "dale", "ok", "okey",
        "listo", "correcto", "eso", "exacto", "aplicar", "aceptar",
        "registrar", "guardar", "bueno", "bien", "perfecto",
        "confirmo", "afirmativo", "vale", "guarda", "esa",
    )

    private val CANCEL_KEYWORDS = setOf(
        "no", "cancelar", "corregir", "borrar", "mal",
        "equivocado", "error", "descartar", "anular", "nulo",
        "cancelado", "niego",
        "negativo", "incorrecto", "espera", "cancela", "quita",
        "elimina", "olvida",
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
        "cuanto levanto", "cuánto levanto", "sugerido", "sugerida",
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
        "apagar micrófono", "callar", "apaga la voz", "apaga",
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
        "todo el bloque", "aplicar al bloque", "aplicar a todo el bloque",
        "bloque entero", "en todo el bloque", "todas las sesiones del bloque",
    )

    private val SKIP_REST_KEYWORDS = setOf(
        "saltar descanso", "saltar timer", "omitir descanso", "omitir timer",
        "ya estoy", "continuar", "listo",
        "salta el descanso", "saltar el descanso", "termina el descanso", "ya",
        "sigue", "seguir", "para", "para el timer", "para el descanso",
    ) + WorkoutVoiceGrammarLexicon.skipRestAliases

    private val USE_ADAPTIVE_REST_KEYWORDS = setOf(
        "usar sugerido", "descanso dinamico", "descanso dinámico",
        "usar adaptativo", "usar descanso adaptativo", "usar sugerencia",
        "sugerido",
    ) + WorkoutVoiceGrammarLexicon.adaptiveRestAliases

    private val APPLY_SUGGESTED_LOAD_KEYWORDS = setOf(
        "sugerencia aplicada", "aplica la sugerencia", "aplicar sugerencia",
        "usa la sugerencia", "la sugerida", "el sugerido",
    )

    private val MOVE_UP_KEYWORDS = setOf(
        "sube este ejercicio", "adelanta este ejercicio", "subir este ejercicio",
        "adelantar este ejercicio", "mueve arriba este ejercicio",
    )

    private val MOVE_DOWN_KEYWORDS = setOf(
        "baja este ejercicio", "retrasa este ejercicio", "bajar este ejercicio",
        "retrasar este ejercicio", "mueve abajo este ejercicio",
    )

    private val CREATE_SUPERSET_KEYWORDS = setOf(
        "crea superserie", "crear superserie", "arma superserie",
        "armar superserie", "haz una superserie", "hacer una superserie",
    )

    private val DISSOLVE_SUPERSET_KEYWORDS = setOf(
        "disuelve la superserie", "disolver superserie", "disolver la superserie",
        "quita la superserie", "rompe la superserie",
    )

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
        "cuanto tiempo queda de sesion", "cuánto tiempo queda de sesión",
        "cuanto llevo de sesion", "cuánto llevo de sesión",
    )

    private val DRAINAGE_QUERY_KEYWORDS = setOf(
        "cuanto drenaje llevo", "cuánto drenaje llevo", "drenaje acumulado",
        "como voy de drenaje", "cómo voy de drenaje", "drenaje",
    )

    private val CURRENT_SET_QUERY_KEYWORDS = setOf(
        "que serie voy", "qué serie voy", "en que serie estoy", "en qué serie estoy",
        "cuantas series quedan", "cuántas series quedan",
    )

    private val PENDING_SIDE_QUERY_KEYWORDS = setOf(
        "que lado falta", "qué lado falta", "que lado me falta", "qué lado me falta",
        "cual lado falta", "cuál lado falta",
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
                base += APPLY_SUGGESTED_LOAD_KEYWORDS
                base += MOVE_UP_KEYWORDS
                base += MOVE_DOWN_KEYWORDS
                base += CREATE_SUPERSET_KEYWORDS
                base += DISSOLVE_SUPERSET_KEYWORDS
                base += UNDO_KEYWORDS
                base += EDIT_LAST_SET_TRIGGERS
                base += FATIGUE_KEYWORDS
                base += DRAINAGE_QUERY_KEYWORDS
                base += CURRENT_SET_QUERY_KEYWORDS
                base += setOf("reemplaza", "reemplazar", "reemplazá", "sustituye", "sustituir", "por")
                base += PENDING_SIDE_QUERY_KEYWORDS
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
        addAll(setOf("punto", "coma", "como", "y", "medio", "media", "cuarto", "cuartos", "kilo", "kilos", "peso", "carga"))
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
            "sin peso", "peso del cuerpo", "con el cuerpo", "recamara", "recámara", "descendente", "rpe", "rir",
            "asistida", "asistidas", "ayudada", "ayudadas", "mancuerna", "mancuernas", "barra",
            "solo la barra", "la barra", "barra sola", "barra vacia",
        ))
        addAll(setOf("de", "equis"))
        // Vocabulario verbal de intensidad (Fase 2):
        addAll(setOf(
            "me quedaron", "quedaban", "en reserva", "en recamara", "en recámara",
            "dandolo todo", "dándolo todo", "lo di todo", "di todo", "hasta el fallo",
            "no me quedo nada", "no quedo nada",
            "quede muy cansado", "quede muy cansada", "muy cansado", "muy cansada",
            "sin energia", "sin energía", "agotado", "agotada", "quede agotado", "quede agotada",
        ))
        // No añadir "0".."120" ni abreviaturas (rpe/reps/kg): Vosk small-es las descarta.
    }

    fun clarificationReplyGrammarTokens(): Set<String> = buildSet {
        addAll(setOf(
            "si", "sí", "dale", "ok", "okey", "confirmar", "confirmado",
            "listo", "aplica", "usar", "usa", "bueno", "bien", "vale", "eso",
        ))
        addAll(setOf(
            "no", "nope", "negativo", "cancelar", "cancela", "cambiar",
            "otro", "otra", "borrar", "elimina", "quita", "olvida", "incorrecto",
        ))
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
        // Los mishearings también afectan a los keywords de comandos ("metro corporal",
        // "reir", "la varra"); corregir antes de clasificar evita Unknowns evitables.
        // Solo correcciones deterministas (sin Levenshtein) para no tocar vocabulario
        // propio de comandos ("falta", "lado").
        val correctedTranscript = WorkoutVoiceMishearingCorrections.correctDeterministic(normalizeText(transcript))
        val lower = correctedTranscript

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

        // Una frase de fatiga suelta es consejo; si parece registro de serie, se
        // deja pasar al parseo de la serie ("cincuenta por cinco, quedé muy cansado").
        if (!looksLikeSetRegistration(lower) && FATIGUE_KEYWORDS.any { lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.FatigueAdvice
        }
        if (DRAINAGE_QUERY_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.QueryDrainage
        }
        if (CURRENT_SET_QUERY_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.QueryCurrentSet
        }
        if (PENDING_SIDE_QUERY_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.QueryPendingSide
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
            lower.contains("limite de sesion") || lower.contains("limite de entrenamiento") ||
            lower.contains("pon limite de") || lower.contains("poner limite de") ||
            lower.contains("limite de")
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

        parseReplaceExercise(lower)?.let { return it }

        if (APPLY_SUGGESTED_LOAD_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.ApplySuggestedLoad
        }

        if (MOVE_UP_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.MoveCurrentExercise(direction = -1)
        }
        if (MOVE_DOWN_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.MoveCurrentExercise(direction = 1)
        }
        if (CREATE_SUPERSET_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.CreateSuperset
        }
        if (DISSOLVE_SUPERSET_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.DissolveSuperset
        }

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

        /**
     * "reemplaza X por Y" / "reemplazar el press por curl martillo con polea" /
     * "sustituye X por Y". [targetName] vacío cuando se dice "este/el actual".
     */
    fun parseReplaceExercise(normalized: String): VoiceSessionCommand.ReplaceExercise? {
        val match = Regex("(?:reemplaza|reemplazar|reemplazá|sustituye|sustituir)\\s+(.+?)\\s+por\\s+(.+)")
            .find(normalized) ?: return null
        val rawTarget = match.groupValues[1].trim()
        val replacement = match.groupValues[2].trim()
        if (replacement.isBlank()) return null
        val target = rawTarget
            .removePrefix("el ")
            .removePrefix("al ")
            .removePrefix("ejercicio ")
            .trim()
            .takeUnless { it in setOf("este", "actual", "el actual", "este ejercicio", "el ejercicio actual") }
            .orEmpty()
        return VoiceSessionCommand.ReplaceExercise(targetName = target, replacementPhrase = replacement)
    }

    fun parseEditLastSet(normalized: String): VoiceSessionCommand.EditLastSet? {
        val lower = normalizeText(normalized)
        val wordAlts = VOICE_INTEGER_WORDS.keys.joinToString("|")
        val numOrWord = """(\d+(?:[.,]\d+)?|$wordAlts)"""
        val rangeTrigger = Regex("""\bde\s+$numOrWord\s+a\s+$numOrWord(?:\s*(?:kilos?|kg))?""").containsMatchIn(lower)
        val triggered = rangeTrigger || EDIT_LAST_SET_TRIGGERS.any { lower.contains(normalizeText(it)) }
        if (!triggered) return null

        var weightKg: Double? = null
        var weightDeltaKg: Double? = null
        var metricValue: Int? = null
        var intensityValue: Double? = null
        var intensityKind: com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind? = null

        // "de 101.3 a 123.8 kilos" / "de noventa a cien kilos": edición absoluta al valor de destino.
        val rangeEdit = Regex(
            """\bde\s+$numOrWord\s+a\s+$numOrWord(?:\s*(?:kilos?|kg))?""",
        ).find(lower)
        if (rangeEdit != null) {
            weightKg = parseSpokenInteger(rangeEdit.groupValues[2])?.toDouble()
                ?: rangeEdit.groupValues[2].replace(',', '.').toDoubleOrNull()
        }
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

        // "fue con el lado derecho" / "lado izquierdo"
        val side = when {
            lower.contains("lado izquierdo") || lower.contains("lado izquierda") -> "left"
            lower.contains("lado derecho") || lower.contains("lado derecha") -> "right"
            else -> null
        }

        val patch = VoiceSetEditPatch(
            weightKg = weightKg,
            weightDeltaKg = weightDeltaKg,
            metricValue = metricValue,
            intensityValue = intensityValue,
            intensityKind = intensityKind,
            side = side,
        )
        if (!patch.hasAnyField) return null
        return VoiceSessionCommand.EditLastSet(patch)
    }

    /**
     * During an active rest timer: "saltar" means skip rest (not the exercise),
     * plus adaptive / adjust / undo commands.
     */
    /**
     * Heurística mínima: el transcript parece un registro de serie (peso×reps o
     * número junto a unidades de peso/reps). Evita que frases de fatiga verbales
     * ("quedé muy cansado") roben comandos que incluyen números.
     */
    fun looksLikeSetRegistration(normalized: String): Boolean {
        val lower = normalized
        val hasConnector = Regex(
            """\b(\w+|\d+(?:[.,]\d+)?)\s+(?:por|x)\s+(\w+|\d+(?:[.,]\d+)?)\b""",
        ).containsMatchIn(lower)
        if (hasConnector) return true
        val hasWeightWord = setOf("kg", "kilo", "kilos", "peso", "carga").any { lower.contains(it) }
        val hasRepWord = setOf("rep", "reps", "repeticion", "repeticiones").any { lower.contains(it) }
        if (hasWeightWord && hasRepWord) return true
        val hasNumber = Regex("""\b\d+\b""").containsMatchIn(lower) ||
            VOICE_INTEGER_WORDS.keys.any { Regex("""\b${it}\b""").containsMatchIn(lower) }
        return hasNumber && (hasWeightWord || hasRepWord)
    }

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
