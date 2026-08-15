package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.screens.workout.PacingAlertMode
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

    private val SKIP_PREPARATION_KEYWORDS = setOf(
        "saltar aproximaciones", "omitir aproximaciones", "saltar aproximacion", "omitir aproximacion",
        "saltar movilidad", "omitir movilidad", "pasar movilidad", "pasar aproximaciones",
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
    private val START_CARDIO_KEYWORDS = setOf("iniciar cardio", "empieza cardio", "comenzar cardio")
    private val FINISH_CARDIO_KEYWORDS = setOf("finalizar cardio", "terminar cardio", "acabar cardio")

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

    private val START_MOBILITY_TIMER_KEYWORDS = setOf(
        "iniciar movilidad", "inicia movilidad", "empezar movilidad", "empieza movilidad",
        "iniciar timer", "inicia timer", "play movilidad", "arrancar movilidad", "comenzar movilidad",
        "iniciar bloque de movilidad", "empezar tiempo", "iniciar tiempo",
    )
    private val PAUSE_MOBILITY_TIMER_KEYWORDS = setOf(
        "pausar movilidad", "pausa movilidad", "detener movilidad", "pausar timer", "pausa timer",
        "parar timer", "detener timer", "detener tiempo", "pausa tiempo",
    )
    private val RESET_MOBILITY_TIMER_KEYWORDS = setOf(
        "reiniciar timer", "reiniciar movilidad", "reset timer", "resetear timer", "resetear movilidad",
        "reset movilidad",
    )
    private val ADD_COMPLEMENTARY_MOBILITY_KEYWORDS = setOf(
        "agregar movilidad complementaria", "agregar movilidad sugerida", "anadir movilidad complementaria",
        "añadir movilidad complementaria", "mas movilidad", "más movilidad", "agregar ejercicio sugerido",
    )
    private val ADD_WARMUP_SET_KEYWORDS = setOf(
        "agregar serie de aproximacion", "agregar serie de aproximación", "agregar aproximacion",
        "agregar aproximación", "otra aproximacion", "otra aproximación", "anadir aproximacion",
        "añadir aproximacion", "suma aproximacion", "sumar aproximacion", "mas aproximacion",
    )
    private val QUERY_WARMUP_SUGGESTION_KEYWORDS = setOf(
        "cuanto peso aproximo", "cuánto peso aproximo", "que peso aproximo", "qué peso aproximo",
        "carga de aproximacion", "carga de aproximación", "cuanto aproximar", "cuánto aproximar",
        "peso de aproximacion", "peso de aproximación", "peso sugerido de aproximacion",
        "que carga aproximo", "qué carga aproximo",
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

    private val PACING_MODE_KEYWORDS = linkedMapOf(
        PacingAlertMode.STRICT to setOf(
            "modo estricto", "modo de tiempo estricto", "tiempo estricto",
            "activar modo estricto", "activa modo estricto", "activar tiempo estricto",
            "alertas estrictas", "alerta estricta", "avisos por ejercicio",
        ),
        PacingAlertMode.SOFT to setOf(
            "modo suave", "ritmo suave", "activar modo suave", "activa modo suave",
            "alertas suaves", "alerta suave",
        ),
        PacingAlertMode.FINAL to setOf(
            "modo final", "modo aviso final", "solo aviso final", "solo avisos finales",
            "alertas finales", "alerta final",
        ),
        PacingAlertMode.OFF to setOf(
            "sin alertas", "desactivar alertas", "desactiva alertas", "quitar alertas",
            "apagar alertas",
        ),
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
                // Correcciones numéricas durante la confirmación ("no, era 47.5"):
                // sin números en la gramática Vosk no puede emitirlos.
                base += defaultNumericGrammarTokens()
            }
            else -> {
                base += STOP_SPEAKING_KEYWORDS
                base += SKIP_SET_KEYWORDS
                base += SKIP_PREPARATION_KEYWORDS
                base += SKIP_KEYWORDS
                base += PREVIOUS_KEYWORDS
                base += SUGGEST_WEIGHT_KEYWORDS
                base += REST_STATUS_KEYWORDS
                base += WHAT_EXERCISE_KEYWORDS
                base += NEXT_EXERCISE_KEYWORDS
                base += TURN_OFF_VOICE_KEYWORDS
                base += FINISH_SESSION_KEYWORDS
                base += START_CARDIO_KEYWORDS
                base += FINISH_CARDIO_KEYWORDS
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
                base += setOf(
                    "reemplaza", "reemplazar", "reemplazá", "sustituye", "sustituir", "cambia", "cambiar", "cambiá", "por", "en vez de", "en lugar de",
                    "agrega", "agregar", "agregá", "anade", "añade", "anadir", "añadir", "añadí", "suma", "sumar", "sumá",
                    "al final", "después", "despues", "después de este", "despues de este", "ejercicio",
                    "press", "banca", "inclinado", "declinado", "plano", "mancuerna", "mancuernas", "barra", "polea", "poleas",
                    "sentadilla", "peso muerto", "rumano", "dominadas", "fondos", "remo", "curl", "biceps", "bíceps", "triceps", "tríceps",
                    "elevaciones", "laterales", "frontales", "prensa", "extensiones", "hip thrust", "gemelos", "zancadas", "jalon", "jalón",
                    "aperturas", "peck deck", "contractor", "hack", "pullover", "abductores", "aductores", "crunch", "plancha",
                )
                base += PENDING_SIDE_QUERY_KEYWORDS
                base += PACE_STATUS_KEYWORDS
                base += PACING_MODE_KEYWORDS.values.flatten()
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
                // Drill-down por zona: qualifiers y sinónimos hablados (c1/c2).
                "interna", "interno", "externa", "externo", "anterior", "posterior",
                "lateral", "medial", "dentro", "adentro", "fuera", "afuera",
                "adelante", "delantera", "atras", "trasera", "frontal", "frente",
                "alta", "arriba", "baja", "abajo", "costado", "rotula",
                "espalda alta", "mano", "cuello", "cervical", "aquiles", "ingle",
                "aductores", "inguinal", "isquios", "isquiotibiales", "pie", "planta",
                "gluteo", "pelvis",
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

    /**
     * Solo frases de confirmación/cancelación (y stop). No incluye los números
     * que ahora forman parte de la gramática Vosk en CONFIRM_WAIT: usarlo para
     * decidir si un final tardío es un sí/no válido (stale-grace), no una corrección.
     */
    fun confirmOrCancelPhraseTokens(): Set<String> = buildSet {
        for (token in CONFIRM_KEYWORDS + CANCEL_KEYWORDS + STOP_SPEAKING_KEYWORDS) {
            add(token.lowercase())
            add(normalizeText(token))
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
        addAll(setOf("fc", "pulso", "pulsaciones", "bpm", "frecuencia", "cardiaca", "cardíaca"))
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
    allowCardioMetrics: Boolean = false,
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
        if (SKIP_PREPARATION_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.SkipPreparation
        }

        // ─── Control de Temporizador de Movilidad por Voz ───
        if (matchesAnyKeyword(lower, RESET_MOBILITY_TIMER_KEYWORDS)) {
            return VoiceSessionCommand.ResetMobilityTimer
        }
        if (matchesAnyKeyword(lower, PAUSE_MOBILITY_TIMER_KEYWORDS)) {
            return VoiceSessionCommand.PauseMobilityTimer
        }
        if (matchesAnyKeyword(lower, START_MOBILITY_TIMER_KEYWORDS)) {
            return VoiceSessionCommand.StartMobilityTimer
        }
        parseAdjustMobilityTimer(lower)?.let { return it }

        if (matchesAnyKeyword(lower, ADD_COMPLEMENTARY_MOBILITY_KEYWORDS)) {
            return VoiceSessionCommand.AddComplementaryMobilityVoice
        }

        // ─── Control y Consultas de Series de Aproximación ───
        if (matchesAnyKeyword(lower, ADD_WARMUP_SET_KEYWORDS)) {
            return VoiceSessionCommand.AddWarmupSetVoice
        }
        if (matchesAnyKeyword(lower, QUERY_WARMUP_SUGGESTION_KEYWORDS)) {
            return VoiceSessionCommand.QueryWarmupSuggestedWeight
        }
        parseTargetWorkingWeight(lower)?.let { return it }
        parseWarmupReport(lower)?.let { return it }

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
        parsePacingAlertMode(lower)?.let { return it }
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
        if (START_CARDIO_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.StartCardio
        }
        if (FINISH_CARDIO_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.FinishCardio
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

        if (SKIP_PREPARATION_KEYWORDS.any { lower.contains(it) }) {
            return VoiceSessionCommand.SkipPreparation
        }

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
        parseAddExercise(lower)?.let { return it }

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
            transcript = transcript,
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            unitMode = unitMode,
            customUnit = customUnit,
            trackRom = trackRom,
            allowCardioMetrics = allowCardioMetrics,
        )
        if (interpretation != null) {
            return VoiceSessionCommand.RegisterSet(
                interpretation.copy(tagName = parsedTag?.tagName),
            )
        }
        if (parsedTag != null) return parsedTag

        return VoiceSessionCommand.Unknown(transcript)
    }

    private fun parsePacingAlertMode(normalized: String): VoiceSessionCommand.SetPacingAlertMode? {
        val lower = normalizeText(normalized)
        val match = PACING_MODE_KEYWORDS.entries.firstOrNull { (_, phrases) ->
            phrases.any { lower.contains(normalizeText(it)) }
        } ?: return null
        return VoiceSessionCommand.SetPacingAlertMode(match.key)
    }

    private fun parseTagCommand(normalized: String, knownTagNames: Set<String>): VoiceSessionCommand.ApplyTag? {
        val spoken = Regex("""\betiqueta\s+(.+)$""").find(normalized)?.groupValues?.get(1)
            ?.trim(' ', '.', ',')?.take(40) ?: return null
        if (spoken.isBlank()) return null
        return VoiceSessionCommand.ApplyTag(knownTagNames.firstOrNull { normalizeText(it) == spoken } ?: spoken)
    }

    /**
     * "reemplaza X por Y" / "reemplazar por Y" / "cambia este ejercicio por Y" /
     * "sustituye X por Y" / "pon Y en vez de X". [targetName] vacío cuando se refiere al actual.
     */
    fun parseReplaceExercise(normalized: String): VoiceSessionCommand.ReplaceExercise? {
        val lower = normalizeText(normalized)

        // 1. Patrón directo: "reemplaza por Y", "cambia este ejercicio por Y", "sustituye por Y"
        val directPattern = Regex(
            """(?:reemplaza|reemplazar|reemplazá|sustituye|sustituir|cambia|cambiar|cambiá)\s+(?:este\s+ejercicio|el\s+ejercicio\s+actual|este|el\s+actual)?\s*por\s+(.+)""",
        ).find(lower)
        if (directPattern != null) {
            val replacement = directPattern.groupValues[1].trim()
            if (replacement.isNotBlank() && !replacement.startsWith("este") && !replacement.startsWith("el actual")) {
                return VoiceSessionCommand.ReplaceExercise(targetName = "", replacementPhrase = replacement)
            }
        }

        // 2. Patrón de pares: "reemplaza X por Y", "cambia X por Y", "sustituye X por Y"
        val pairPattern = Regex(
            """(?:reemplaza|reemplazar|reemplazá|sustituye|sustituir|cambia|cambiar|cambiá)\s+(.+?)\s+por\s+(.+)""",
        ).find(lower)
        if (pairPattern != null) {
            val rawTarget = pairPattern.groupValues[1].trim()
            val replacement = pairPattern.groupValues[2].trim()
            if (replacement.isNotBlank()) {
                val target = rawTarget
                    .removePrefix("el ")
                    .removePrefix("al ")
                    .removePrefix("ejercicio ")
                    .trim()
                    .takeUnless { it in setOf("este", "actual", "el actual", "este ejercicio", "el ejercicio actual") }
                    .orEmpty()
                return VoiceSessionCommand.ReplaceExercise(targetName = target, replacementPhrase = replacement)
            }
        }

        // 3. Patrón alternativo: "pon Y en vez de X" o "pon Y en lugar de X"
        val insteadPattern = Regex(
            """(?:pon|poner|pone|poné)\s+(.+?)\s+en\s+(?:vez|lugar)\s+de\s+(.+)""",
        ).find(lower)
        if (insteadPattern != null) {
            val replacement = insteadPattern.groupValues[1].trim()
            val rawTarget = insteadPattern.groupValues[2].trim()
            if (replacement.isNotBlank()) {
                val target = rawTarget
                    .removePrefix("el ")
                    .removePrefix("al ")
                    .removePrefix("ejercicio ")
                    .trim()
                    .takeUnless { it in setOf("este", "actual", "el actual", "este ejercicio", "el ejercicio actual") }
                    .orEmpty()
                return VoiceSessionCommand.ReplaceExercise(targetName = target, replacementPhrase = replacement)
            }
        }
        return null
    }

    /**
     * "agrega X" / "añade X al final" / "añadir el ejercicio X" / "agrega X después de este".
     */
    fun parseAddExercise(normalized: String): VoiceSessionCommand.AddExercise? {
        val lower = normalizeText(normalized)

        // Excluir comandos de serie que pertenecen a AddSet o métricas
        if (ADD_SET_KEYWORDS.any { lower.contains(it) } ||
            lower.contains("serie") || lower.contains("set") ||
            lower.contains("repeticion") || lower.contains("repetición")) {
            return null
        }

        // 1. Patrón explícito: "agrega el ejercicio X", "añadir ejercicio X"
        val explicitExPattern = Regex(
            """(?:agrega|agregar|agregá|anade|añade|anadir|añadir|añadí|suma|sumar|sumá)\s+(?:el\s+)?ejercicio\s+(.+)""",
        ).find(lower)
        if (explicitExPattern != null) {
            val rawPhrase = explicitExPattern.groupValues[1].trim()
            if (rawPhrase.isNotBlank()) {
                val atEnd = rawPhrase.endsWith("al final") || rawPhrase.contains("al final")
                val cleanPhrase = rawPhrase
                    .removeSuffix("al final")
                    .removeSuffix("después de este")
                    .removeSuffix("despues de este")
                    .removeSuffix("después")
                    .removeSuffix("despues")
                    .trim()
                if (cleanPhrase.isNotBlank()) {
                    return VoiceSessionCommand.AddExercise(
                        exercisePhrase = cleanPhrase,
                        targetExerciseId = null,
                        atEnd = atEnd,
                    )
                }
            }
        }

        // 2. Patrón con posicionador: "agrega X al final", "añade X después de este"
        val positionedPattern = Regex(
            """(?:agrega|agregar|agregá|anade|añade|anadir|añadir|añadí|suma|sumar|sumá)\s+(.+?)\s+(al\s+final|despues\s+de\s+este|después\s+de\s+este|despues|después)$""",
        ).find(lower)
        if (positionedPattern != null) {
            val rawPhrase = positionedPattern.groupValues[1].trim()
            val position = positionedPattern.groupValues[2].trim()
            val atEnd = position == "al final"
            val cleanPhrase = rawPhrase
                .removePrefix("el ")
                .removePrefix("un ")
                .removePrefix("una ")
                .removePrefix("ejercicio ")
                .trim()
            if (cleanPhrase.isNotBlank() && cleanPhrase.length >= 3) {
                return VoiceSessionCommand.AddExercise(
                    exercisePhrase = cleanPhrase,
                    targetExerciseId = null,
                    atEnd = atEnd,
                )
            }
        }

        // 3. Patrón genérico: "agrega X", "añadir X", "suma X"
        val genericPattern = Regex(
            """^(?:agrega|agregar|agregá|anade|añade|anadir|añadir|añadí|suma|sumar|sumá)\s+(.+)""",
        ).find(lower)
        if (genericPattern != null) {
            val rawPhrase = genericPattern.groupValues[1].trim()
            val cleanPhrase = rawPhrase
                .removePrefix("el ")
                .removePrefix("un ")
                .removePrefix("una ")
                .removePrefix("ejercicio ")
                .trim()
            val nonExerciseKeywords = setOf("peso", "kilo", "kilos", "kg", "rep", "reps", "repeticion", "repeticiones", "serie", "series", "set", "sets", "descanso", "timer", "temporizador", "nota", "comentario", "mas", "más", "menos")
            if (cleanPhrase.isNotBlank() && cleanPhrase.length >= 3 &&
                cleanPhrase.split(' ').none { it in nonExerciseKeywords }) {
                return VoiceSessionCommand.AddExercise(
                    exercisePhrase = cleanPhrase,
                    targetExerciseId = null,
                    atEnd = false,
                )
            }
        }

        return null
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

        // "eran 9" / "era nueve" → reps; "era cien kilos" / "era 47.5" → peso.
        val repsWere = Regex(
            """(?:eran|era)\s+(\d+(?:[.,]\d+)?|$wordAlts)(?:\s*((?:kilos?|kg)))?(?:\s*(?:reps?|repeticiones?))?""",
        ).find(lower)
        if (repsWere != null) {
            val spoken = repsWere.groupValues[1]
            val followedByKilos = repsWere.groupValues[2].isNotBlank()
            val asDouble = spoken.replace(',', '.').toDoubleOrNull() ?: VOICE_INTEGER_WORDS[spoken]?.toDouble()
            when {
                // Decimal explícito ("era 47.5") nunca es reps → corrige el peso.
                asDouble != null && asDouble % 1.0 != 0.0 -> weightKg = asDouble
                followedByKilos -> weightKg = asDouble
                else -> metricValue = asDouble?.toInt()
            }
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
        if (USE_ADAPTIVE_REST_KEYWORDS.any { matchesRestKeyword(lower, normalizeText(it)) }) {
            return VoiceSessionCommand.UseAdaptiveRest
        }
        parseAdjustRestTime(lower)?.let { return it }
        if (UNDO_KEYWORDS.any { matchesAnyKeyword(lower, setOf(normalizeText(it))) || lower.contains(normalizeText(it)) }) {
            return VoiceSessionCommand.UndoLastSet
        }
        if (SKIP_REST_KEYWORDS.any { matchesRestKeyword(lower, normalizeText(it)) } ||
            SKIP_KEYWORDS.any { lower.contains(it) }
        ) {
            return VoiceSessionCommand.SkipRest
        }
        return null
    }

    /**
     * Las keywords de descanso de una sola palabra se matchean por token exacto
     * para que "sigue" no colisione con "siguiente" ni "para" con cualquier frase.
     */
    private fun matchesRestKeyword(lower: String, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        if (' ' in keyword) return lower.contains(keyword)
        return lower.split(' ').contains(keyword)
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

    /** Exact token match for single words; token sequence match for multi-word keywords. */
    private fun matchesAnyKeyword(normalized: String, keywords: Set<String>): Boolean {
        if (normalized.isBlank()) return false
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        return keywords.any { keyword ->
            if (keyword.contains(' ')) {
                val kwTokens = keyword.split(' ').filter { it.isNotBlank() }
                if (kwTokens.isEmpty()) false
                else tokens.windowed(kwTokens.size).any { it == kwTokens }
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

    fun parseFeedbackCommand(transcript: String, bareNumberIsQuality: Boolean = false): VoiceSessionCommand.LogFeedback {
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
        // Número desnudo 1-10 = calidad técnica cuando el prompt de voz está activo (b3).
        // Patrones de serie ("8 por 12") NUNCA se tragan: se detectan y van al parseo normal.
        if (technicalQuality == null && bareNumberIsQuality && isBareQualityNumber(lower)) {
            technicalQuality = extractNumberFromText(lower)?.toInt()?.takeIf { it in 1..10 }
        }
        // Respuestas naturales directas al prompt de calidad ("buena", "regular", "ocho")
        // sin repetir la palabra "calidad": el usuario responde la pregunta del TTS.
        if (technicalQuality == null && bareNumberIsQuality) {
            technicalQuality = when {
                lower.contains("excelente") || lower.contains("perfecta") -> 10
                lower.contains("muy buena") -> 9
                lower.contains("buena") -> 8
                lower.contains("regular") || lower.contains("mas o menos") -> 6
                lower.contains("mala") || lower.contains("pesima") -> 3
                else -> null
            }
        }

        var perceivedIntensity: Double? = null
        if (lower.contains("intensidad") || lower.contains("rpe") || lower.contains("esfuerzo") || lower.contains("fatiga")) {
            perceivedIntensity = extractNumberFromText(lower)?.coerceIn(1.0, 10.0)
        }

        var discomfortId: String? = null
        var discomfortCandidates: Map<String, String> = emptyMap()
        val mentionsDiscomfort = lower.contains("molestia") || lower.contains("dolor") ||
            lower.contains("tiron") || lower.contains("siento")
        val discomfortMatches = when {
            mentionsDiscomfort -> matchDiscomfortCandidates(lower)
            else -> matchDiscomfortConfident(lower)
        }
        if (discomfortMatches.isNotEmpty()) {
            discomfortId = discomfortMatches.singleOrNull()?.id
            discomfortCandidates = if (discomfortMatches.size > 1) {
                discomfortMatches.associate { it.id to it.label }
            } else {
                emptyMap()
            }
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

    /**
     * True cuando la frase parece una serie dictada ("8 por 12", "veinte por diez"):
     * el gate de feedback no debe interceptarla (b1, riesgo conocido del plan).
     */
    fun looksLikeSetPattern(transcript: String): Boolean {
        val lower = normalizeText(transcript)
        if (Regex("\\d+\\s*(kilos?|kg)?\\s+por\\s+\\d+").containsMatchIn(lower)) return true
        if (!lower.contains(" por ")) return false
        val parts = lower.split(" por ")
        return parts.size == 2 && parts.all { extractNumberFromText(it.trim()) != null }
    }

    private fun isBareQualityNumber(lower: String): Boolean {
        if (lower.contains(" por ")) return false
        val tokens = lower.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 2) return false
        val numericTokens = tokens.count { token ->
            token.toDoubleOrNull() != null || VOICE_INTEGER_WORDS.containsKey(token)
        }
        return numericTokens == 1
    }

    fun parseFinalFeedbackCommand(transcript: String): VoiceSessionCommand.LogFinalFeedback {
        val lower = normalizeText(transcript)

        // (P0) Keywords de cierre alineados con el prompt TTS de la finish sheet
        // («Para finalizar, di sesión terminada», WorkoutViewModel.announceWorkoutSessionSummary).
        // «guardar» a pelo cubre las variantes «guardar y terminar/entrenamiento/sesión».
        val saveKeywords = setOf(
            "guardar", "guardar y terminar",
            "sesion terminada", "terminar sesion", "acabar sesion", "finalizar sesion", "guardar sesion",
            "entrenamiento terminado", "terminar entrenamiento", "finalizar entrenamiento", "guardar entrenamiento",
            "finalizar",
        )
        // Guard anti-negación: «no guardar», «todavía no», «aún no»… no disparan el save.
        val negatedFinish = lower.contains("no guardar") || lower.contains("no terminar") ||
            lower.contains("todavia no") || lower.contains("aun no")
        val isSaveAction = !negatedFinish && saveKeywords.any { lower.contains(it) }

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

    /** Matching CON keyword de molestia ("dolor/molestia/tirón/siento"): acepta también hits débiles. */
    private fun matchDiscomfortCandidates(text: String): List<DiscomfortCatalogEntry> =
        matchDiscomfortScored(text, assumeDiscomfortContext = true)

    /** Matching SIN keyword: solo hits de alta precisión (sinónimo hablado o label). */
    private fun matchDiscomfortConfident(text: String): List<DiscomfortCatalogEntry> =
        matchDiscomfortScored(text, assumeDiscomfortContext = false)

    /**
     * Matcher por pesos (c1): sinónimos hablados > label (alto) > description (medio) >
     * section.label (bajo, excluyendo el término genérico de la sección). Las familias
     * (shoulder_*, elbow_*, knee_*, hip_*) se devuelven enteras (refinadas por qualifiers)
     * para habilitar el drill-down por zona (c2).
     */
    private fun matchDiscomfortScored(text: String, assumeDiscomfortContext: Boolean): List<DiscomfortCatalogEntry> {
        val normalized = normalizeText(text)
        if (normalized.contains("ninguna") || normalized.contains("sin molestia") || normalized.contains("todo bien")) {
            return DISCOMFORT_CATALOG.filter { it.id == "none" }
        }
        // 1) Sinónimos hablados: id directo o familia de zona.
        val synonymIds = mutableSetOf<String>()
        for ((spokenKeys, catalogIds) in DISCOMFORT_SPOKEN_SYNONYMS) {
            if (spokenKeys.any { matchesAnyKeyword(normalized, setOf(it)) }) {
                synonymIds += catalogIds
            }
        }
        if (synonymIds.size == 1) {
            return DISCOMFORT_CATALOG.filter { it.id in synonymIds }
        }
        if (synonymIds.size > 1) {
            return refineFamilyByZoneQualifiers(normalized, DISCOMFORT_CATALOG.filter { it.id in synonymIds })
        }
        // 2) Scoring por pesos sobre catálogo (sin "none").
        val relevant = normalized.split(' ').filter { it.length >= 4 && it !in DISCOMFORT_STOPWORDS }
        if (relevant.isEmpty()) return emptyList()
        val scored = DISCOMFORT_CATALOG.filter { it.id != "none" }.mapNotNull { entry ->
            val labelHaystack = normalizeText(entry.label)
            val descriptionHaystack = normalizeText(entry.description)
            val sectionHaystack = normalizeText(entry.section.label)
            val sectionGeneric = DISCOMFORT_SECTION_GENERIC_TOKENS[entry.section].orEmpty()
            var score = 0
            for (token in relevant) {
                when {
                    labelHaystack.contains(token) -> score += DISCOMFORT_SCORE_LABEL
                    descriptionHaystack.contains(token) -> score += DISCOMFORT_SCORE_DESCRIPTION
                    token !in sectionGeneric && sectionHaystack.contains(token) -> score += DISCOMFORT_SCORE_SECTION
                }
            }
            if (score > 0) entry to score else null
        }
        if (scored.isEmpty()) return emptyList()
        val maxScore = scored.maxOf { it.second }
        // Sin contexto explícito solo se acepta un hit de label (precisión alta).
        if (!assumeDiscomfortContext && maxScore < DISCOMFORT_SCORE_LABEL) return emptyList()
        return scored.filter { it.second == maxScore }.take(3).map { it.first }
    }

    /** Familia de zona + qualifier hablado ("por dentro", "anterior") -> una sola entrada. */
    private fun refineFamilyByZoneQualifiers(
        normalized: String,
        family: List<DiscomfortCatalogEntry>,
    ): List<DiscomfortCatalogEntry> {
        val hits = family.filter { entry ->
            DISCOMFORT_ZONE_QUALIFIERS[entry.id].orEmpty().any { matchesAnyKeyword(normalized, setOf(it)) }
        }
        return if (hits.size == 1) hits else family
    }

    /**
     * Resolución extendida del drill-down por zona (c2): dado el mapa de candidatos de
     * [VoicePendingAction.DiscomfortSelection] acepta qualifiers hablados
     * ("interna/externa/anterior/posterior/lateral/medial", "por dentro/fuera"), tokens
     * distintivos del label y, como último recurso, el substring histórico del label.
     */
    fun resolveDiscomfortCandidateId(transcript: String, candidates: Map<String, String>): String? {
        if (candidates.isEmpty()) return null
        val lower = normalizeText(transcript)
        // 1) Qualifier de zona.
        val viaQualifier = candidates.keys.filter { id ->
            DISCOMFORT_ZONE_QUALIFIERS[id].orEmpty().any { matchesAnyKeyword(lower, setOf(it)) }
        }
        if (viaQualifier.size == 1) return viaQualifier.first()
        // 2) Token distintivo del label (presente en un solo candidato).
        val labelTokens = candidates.mapValues { (_, label) ->
            normalizeText(label).split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()
        }
        val viaDistinctiveToken = labelTokens.filter { (id, tokens) ->
            tokens.any { token ->
                labelTokens.none { (otherId, otherTokens) -> otherId != id && token in otherTokens } &&
                    matchesAnyKeyword(lower, setOf(token))
            }
        }.keys
        if (viaDistinctiveToken.size == 1) return viaDistinctiveToken.first()
        // 3) Fallback histórico: substring del label completo.
        return candidates.entries.firstOrNull { lower.contains(normalizeText(it.value)) }?.key
    }

    private const val DISCOMFORT_SCORE_LABEL = 3
    private const val DISCOMFORT_SCORE_DESCRIPTION = 2
    private const val DISCOMFORT_SCORE_SECTION = 1

    private val DISCOMFORT_STOPWORDS = setOf(
        "dolor", "molestia", "tengo", "tiron", "siento", "duele", "mucho", "poco",
    )

    /** Término genérico de cada sección: no puntúa como hit de sección (evita que
     *  "dolor de hombro" arrastre codo/muñeca vía "Hombro y brazos"). */
    private val DISCOMFORT_SECTION_GENERIC_TOKENS: Map<DiscomfortSection, Set<String>> = mapOf(
        DiscomfortSection.SHOULDERS_ARMS to setOf("hombro"),
        DiscomfortSection.SPINE_NECK to setOf("columna", "cuello"),
        DiscomfortSection.HIP_PELVIS to setOf("cadera", "pelvis"),
        DiscomfortSection.KNEE to setOf("rodilla"),
        DiscomfortSection.ANKLE_FOOT to setOf("tobillo", "pie"),
        DiscomfortSection.GENERAL to setOf("general"),
    )

    /** Sinónimos hablados -> ids del catálogo. Las frases específicas van primero. */
    private val DISCOMFORT_SPOKEN_SYNONYMS: List<Pair<Set<String>, Set<String>>> = listOf(
        setOf("espalda baja") to setOf("lumbar"),
        setOf("espalda alta") to setOf("upper_back"),
        setOf("lumbar", "lumbares", "lumbago") to setOf("lumbar"),
        setOf("muneca", "munecas") to setOf("wrist_hand"),
        setOf("mano", "manos") to setOf("wrist_hand"),
        setOf("aquiles") to setOf("achilles"),
        setOf("cuello", "cervical", "cervicales") to setOf("neck_cervical"),
        setOf("ingle", "inguinal", "aductor", "aductores") to setOf("adductor_groin"),
        setOf("isquio", "isquios", "isquiotibial", "isquiotibiales") to setOf("hamstring_proximal"),
        setOf("tobillo", "tobillos") to setOf("ankle"),
        setOf("pie", "pies", "planta") to setOf("plantar_foot"),
        setOf("gluteo", "gluteos", "glutea") to setOf("hip_lateral"),
        setOf("codo", "codos") to setOf("elbow_medial", "elbow_lateral"),
        setOf("rodilla", "rodillas", "rotula") to setOf("knee_patellar", "knee_medial"),
        setOf("hombro", "hombros") to setOf("shoulder_anterior", "shoulder_posterior"),
        setOf("cadera", "caderas") to setOf("hip_front", "hip_lateral"),
        setOf("espalda") to setOf("upper_back", "lumbar"),
    )

    /** Qualifiers hablados por entrada del catálogo para el drill-down por zona (c2). */
    private val DISCOMFORT_ZONE_QUALIFIERS: Map<String, Set<String>> = mapOf(
        "shoulder_anterior" to setOf("anterior", "frontal", "adelante", "delantera", "frente"),
        "shoulder_posterior" to setOf("posterior", "atras", "trasera"),
        "elbow_medial" to setOf("interna", "interno", "medial", "dentro", "adentro"),
        "elbow_lateral" to setOf("externa", "externo", "lateral", "fuera", "afuera"),
        "knee_patellar" to setOf("anterior", "adelante", "delantera", "frente", "rotula"),
        "knee_medial" to setOf(
            "interna", "interno", "medial", "lateral", "externa", "externo",
            "dentro", "adentro", "fuera", "afuera", "costado", "costados",
        ),
        "hip_front" to setOf("anterior", "adelante", "delantera", "frente"),
        "hip_lateral" to setOf("lateral", "externa", "externo", "fuera", "afuera", "costado", "glutea"),
        "upper_back" to setOf("alta", "arriba", "toracica"),
        "lumbar" to setOf("baja", "abajo"),
    )

    fun parseAdjustMobilityTimer(normalized: String): VoiceSessionCommand.AdjustMobilityTimer? {
        val lower = normalized
        val isMobilityTimerContext = lower.contains("movilidad") || lower.contains("timer") || lower.contains("tiempo") || lower.contains("segundo")
        if (!isMobilityTimerContext) return null

        val addMatch = Regex(
            """(?:anade|añade|suma|agrega|mas|más)\s+(\d+|""" +
                VOICE_INTEGER_WORDS.keys.joinToString("|") +
                """)\s*(?:segundos?|segs?)?""",
        ).find(lower) ?: Regex(
            """(\d+|""" +
                VOICE_INTEGER_WORDS.keys.joinToString("|") +
                """)\s*(?:segundos?|segs?)""",
        ).find(lower)

        if (addMatch != null) {
            val raw = addMatch.groupValues[1]
            val seconds = raw.toIntOrNull() ?: VOICE_INTEGER_WORDS[raw] ?: 30
            if (seconds > 0) {
                return VoiceSessionCommand.AdjustMobilityTimer(seconds)
            }
        }
        return null
    }

    fun parseTargetWorkingWeight(normalized: String): VoiceSessionCommand.SetTargetWorkingWeightVoice? {
        val lower = normalized
        val isTargetIntent = lower.contains("primera serie") || lower.contains("serie efectiva") ||
            lower.contains("carga objetivo") || lower.contains("peso objetivo") || lower.contains("primera con")
        if (!isTargetIntent) return null
        val weightMatch = Regex("""(?:con\s+)?(\d+(?:[.,]\d+)?)\s*(?:kilos?|kg)?""").find(lower)
        val weight = weightMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: extractNumberFromText(lower)
        return weight?.takeIf { it > 0 }?.let { VoiceSessionCommand.SetTargetWorkingWeightVoice(it) }
    }

    fun parseWarmupReport(normalized: String): VoiceSessionCommand.RecordWarmupEffortAndLoad? {
        val lower = normalized

        // 1. Never intercept structured working sets (RIR, RPE, tags, drops, failures)
        if (lower.contains("rir") || lower.contains("rpe") || lower.contains("etiqueta") ||
            lower.contains("tag") || lower.contains("fallo") || lower.contains("drop") ||
            lower.contains("cluster")
        ) {
            return null
        }

        // 2. Never intercept pacing, skip or query phrases
        if (lower.contains("ritmo") || lower.contains("alerta") || lower.contains("modo estricto") || lower.contains("modo suave") ||
            lower.contains("saltar") || lower.contains("omitir") || lower.contains("pasar") ||
            lower.contains("agregar") || lower.contains("otra") || lower.contains("cuanto") || lower.contains("cuánto")
        ) {
            return null
        }

        val hasExplicitWarmupToken = lower.contains("aproxima") || lower.contains("calentamiento") || lower.contains("aprox")

        val effort = when {
            setOf("pesado", "pesada", "duro", "dura", "costo", "costó", "muy pesado", "fuerte", "lento", "apenas salio").any { lower.contains(it) } ->
                com.example.kpkn.domain.workout.WarmupEffort.HEAVY
            setOf("liviano", "liviana", "ligero", "ligera", "facil", "fácil", "suave", "volo", "voló", "sin esfuerzo").any { lower.contains(it) } ->
                com.example.kpkn.domain.workout.WarmupEffort.LIGHT
            setOf("normal", "moderado", "moderada", "justo", "buen ritmo", "adecuado").any { lower.contains(it) } ->
                com.example.kpkn.domain.workout.WarmupEffort.NORMAL
            else -> null
        }

        val weightMatch = Regex("""(?:con\s+)?(\d+(?:[.,]\d+)?)\s*(?:kilos?|kg)""").find(lower)
            ?: Regex("""\b(\d+(?:[.,]\d+)?)\s*(?:kilos?|kg)\b""").find(lower)
        val weight = weightMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: if (hasExplicitWarmupToken && (lower.contains("kilo") || lower.contains("kg") || lower.contains("con "))) extractNumberFromText(lower) else null

        val repsMatch = Regex("""(\d+)\s*(?:reps?|repeticiones)""").find(lower)
        val reps = repsMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (hasExplicitWarmupToken) {
            return VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = weight,
                reps = reps,
                effort = effort,
                isCompleted = true,
            )
        }

        // If no explicit warmup token, must have effort combined with (weight or explicit sensation phrase)
        val hasSensationPhrase = lower.contains("se sintio") || lower.contains("se sintió") ||
            lower.contains("estuvo livian") || lower.contains("estuvo pesad") || lower.contains("estuvo duro") ||
            lower.contains("muy livian") || lower.contains("muy pesad") || lower.contains("bastante duro")

        if (effort != null && (weight != null || hasSensationPhrase)) {
            return VoiceSessionCommand.RecordWarmupEffortAndLoad(
                weightKg = weight,
                reps = reps,
                effort = effort,
                isCompleted = true,
            )
        }

        return null
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
