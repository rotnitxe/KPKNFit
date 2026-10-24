package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Política de avisos del asistente (MOT · S1–S5, AUD1 §2.7). Una sola ruta de decisión:
 *
 * 1. **Contexto** ([SessionAnalysisContext]): objetivo, fase, papel de cada ejercicio, historial realizado y tiempo disponible.
 * 2. **Protección**: los movimientos principales nunca son el blanco de un ajuste automático; primero van los accesorios.
 * 3. **Verificación**: cada acción se SIMULA sobre un borrador con las mismas funciones que luego la aplican
 *    ([SessionAssistantActions]) y solo se ofrece si cambia lo que anuncia (p. ej. «ahorra tiempo» exige un ahorro real con el
 *    reloj común). El drop-set no es una acción de tiempo: alarga la sesión y no se ofrece como ahorro.
 * 4. **Tono**: describe un dato verificable, su relación con la restricción y una posibilidad. Nada sobre lesiones ni
 *    «recuperación»; un coste alto es un dato, no una orden.
 * 5. **Techo**: como mucho [MAX_NOTICES] avisos (tarjetas y notas juntas), con acciones sin duplicados entre tarjetas.
 *
 * Las restricciones explícitas de la persona (su tiempo, sus límites rígidos) van antes que cualquier estimación.
 */
internal object SessionAdvisor {
    const val MAX_NOTICES = 3
    private const val MAX_CARDS = 3
    private const val MAX_DETAILS_PER_CARD = 3

    /** Pico de coste (puntos) a partir del cual el coste estimado se comunica. Señal interna, no un juicio de recuperación. */
    const val COST_SIGNAL = 60.0

    /** Presupuesto de tiempo (AUD1 §2.7): se propone ajuste si el exceso supera max(120 s, 10 % del tiempo disponible). */
    private const val MIN_TIME_EXCESS_SECONDS = 120
    private const val TIME_EXCESS_FRACTION = 0.10

    private const val REST_CUT_SECONDS = 15
    private const val MIN_USEFUL_SAVING_SECONDS = 15

    /** Desde este porcentaje un cruce entre sesiones se valora como ajuste; por debajo es solo un dato (AUD1 §2.7). */
    private const val OVERLAP_CARD_PERCENT = 25
    private const val OVERLAP_NOTE_PERCENT = 10

    /** Un plan supera a su historial comparable cuando su estrés proyectado excede este múltiplo de la media realizada. */
    private const val DEVIATION_FACTOR = 1.3
    private const val MIN_COST_DROP = 0.5

    data class Advice(
        val cards: List<AssistantSuggestion>,
        val notes: List<String>,
    )

    private data class RuleOutput(
        val card: AssistantSuggestion? = null,
        /** Notas que la regla emite siempre (sin tarjeta). */
        val notes: List<String> = emptyList(),
        /** Texto que sustituye a la tarjeta cuando se queda sin acciones propias (por duplicados o por el techo). */
        val fallbackNote: String? = null,
    )

    fun advise(
        input: SessionAssistantInput,
        session: Session,
        context: SessionAnalysisContext,
        volume: SessionAssistantEngine.VolumeCalculationResult,
    ): Advice {
        val params = AssistantActionParameters(
            exerciseIndex = input.exerciseIndex,
            supersetBetweenRestSeconds = input.supersetBetweenRestSeconds,
            supersetRoundRestSeconds = input.supersetRoundRestSeconds,
            defaultRestSeconds = input.settings.restTimerDefaultSeconds.takeIf { it > 0 } ?: 90,
            newGroupId = { "assistant_superset_preview" },
        )
        val outputs = buildList<RuleOutput> {
            add(limitsRule(input, session, context, params, volume))
            add(timeRule(input, session, context, params))
        }
        return assemble(outputs)
    }

    // ─── Ensamblado: dedupe de acciones y techo de avisos ────────────────────

    private fun assemble(outputs: List<RuleOutput>): Advice {
        val usedActions = mutableSetOf<String>()
        val cards = mutableListOf<AssistantSuggestion>()
        val notes = mutableListOf<String>()
        outputs.forEach { out ->
            out.notes.forEach { if (it !in notes) notes += it }
            val card = out.card ?: return@forEach
            val fresh = card.details.filter { actionKey(it.action) !in usedActions }.take(MAX_DETAILS_PER_CARD)
            if (fresh.isEmpty() || cards.size >= MAX_CARDS) {
                out.fallbackNote?.let { if (it !in notes) notes += it }
                return@forEach
            }
            fresh.forEach { usedActions += actionKey(it.action) }
            cards += card.copy(details = fresh)
        }
        val ordered = cards.sortedBy { it.priority }
        return Advice(
            cards = ordered.take(MAX_NOTICES),
            notes = notes.take((MAX_NOTICES - ordered.size.coerceAtMost(MAX_NOTICES)).coerceAtLeast(0)),
        )
    }

    /** Clave de deduplicación: la misma acción sobre el mismo blanco no aparece en dos tarjetas. */
    private fun actionKey(action: AssistantDetailAction): String = when (action) {
        is AssistantDetailAction.ReduceSet -> "set:${action.exerciseId ?: action.muscle}"
        is AssistantDetailAction.ReduceRest -> "rest:${action.exerciseIds.sorted().joinToString(",")}"
        is AssistantDetailAction.LowerRpe -> "rpe:${action.exerciseId ?: "all"}"
        is AssistantDetailAction.ConvertToSuperset -> "superset:${action.exerciseId}"
        is AssistantDetailAction.ConvertToDropSet -> "drop:${action.exerciseId}"
        AssistantDetailAction.RemoveFailure -> "remove-failure"
    }

    // ─── Límites explícitos («Límite que elegiste») ──────────────────────────

    private fun limitsRule(
        input: SessionAssistantInput,
        session: Session,
        context: SessionAnalysisContext,
        params: AssistantActionParameters,
        volume: SessionAssistantEngine.VolumeCalculationResult,
    ): RuleOutput {
        val limits = input.ruleLimits
        if (!limits.rigidLimits) return RuleOutput()
        val exercises = session.allExercises().distinctBy { it.id }.filter { it.cardioDetails == null }
        val details = mutableListOf<AssistantSuggestionDetail>()
        val reasons = mutableListOf<String>()

        if (limits.maxRPE < 10.0) {
            val offenders = exercises
                .filter { ex -> ex.sets.any { !it.isIneffective && !it.isEmptySlot && it.plannedRpe() > limits.maxRPE } }
                .sortedBy { if (context.roleOf(it.id) == ExerciseRole.ACCESSORY) 0 else 1 }
            offenders.take(MAX_DETAILS_PER_CARD).forEach { ex ->
                // Hasta el límite que la persona fijó (en pasos de 0,5), no un medio punto simbólico.
                val worst = ex.sets.filter { !it.isIneffective && !it.isEmptySlot }.maxOf { it.plannedRpe() }
                val amount = (ceil((worst - limits.maxRPE) / 0.5) * 0.5).coerceAtLeast(0.5)
                val action = AssistantDetailAction.LowerRpe(exerciseId = ex.id, amount = amount)
                val after = SessionAssistantActions.apply(session, action, params)
                if (after != session) {
                    details += AssistantSuggestionDetail(
                        id = "limit_detail_rpe_${ex.id}",
                        label = "Bajar el esfuerzo de ${ex.name} hasta tu límite (RPE ${formatNumber(limits.maxRPE)})",
                        action = action,
                        defaultAccepted = true,
                        effect = SessionAssistantActions.measure(session, after),
                    )
                }
            }
            if (offenders.isNotEmpty()) reasons += "hay series por encima de tu esfuerzo máximo (RPE ${formatNumber(limits.maxRPE)})"
        }

        val overLimit = volume.volumeMap.filter { (_, acc) -> acc.effective > limits.maxVolumePerMuscleSession }.keys
        overLimit.forEach { muscle ->
            if (details.size >= MAX_DETAILS_PER_CARD) return@forEach
            val target = accessoryFor(session, context, params, muscle) ?: return@forEach
            val action = AssistantDetailAction.ReduceSet(exerciseId = target.id)
            val after = SessionAssistantActions.apply(session, action, params)
            if (after != session) {
                details += AssistantSuggestionDetail(
                    id = "limit_detail_volume_${target.id}",
                    label = "Quitar 1 serie de ${target.name}",
                    action = action,
                    defaultAccepted = true,
                    effect = SessionAssistantActions.measure(session, after),
                )
            }
            reasons += "$muscle supera tu límite de series efectivas por sesión"
        }
        if (details.isEmpty()) return RuleOutput()
        val message = "Pusiste límites rígidos y esta sesión los supera: ${reasons.distinct().joinToString("; ")}."
        return RuleOutput(
            card = AssistantSuggestion(
                id = "user_limits",
                type = AssistantActionType.COMPOSITE,
                title = "Dentro de tus límites",
                message = message,
                priority = 1,
                details = details,
                kind = AssistantNoticeKind.USER_LIMIT,
            ),
            fallbackNote = message,
        )
    }

    // ─── Presupuesto de tiempo ───────────────────────────────────────────────

    private fun timeRule(
        input: SessionAssistantInput,
        session: Session,
        context: SessionAnalysisContext,
        params: AssistantActionParameters,
    ): RuleOutput {
        val available = context.availableSeconds ?: return RuleOutput()
        val excess = context.clockSeconds - available
        if (excess <= 0) return RuleOutput()
        val threshold = maxOf(MIN_TIME_EXCESS_SECONDS, (available * TIME_EXCESS_FRACTION).roundToInt())
        val head = "La estimación (${formatMinutes(context.clockSeconds)}) supera tus ${formatMinutes(available)} en ${formatSpan(excess)}."
        if (excess <= threshold) {
            return RuleOutput(notes = listOf("$head Es una diferencia pequeña: no hay ajuste que proponer."))
        }
        val details = timeDetails(session, context, params)
        if (details.isEmpty()) {
            return RuleOutput(
                notes = listOf("$head Ningún ajuste cambia el tiempo sin tocar los movimientos principales o la estructura que elegiste."),
            )
        }
        val accepted = details.filter { it.defaultAccepted }
        val afterDefaults = accepted.fold(session) { acc, detail -> SessionAssistantActions.apply(acc, detail.action, params) }
        val remaining = SessionAssistantActions.durationSeconds(afterDefaults) - available
        val saved = context.clockSeconds - SessionAssistantActions.durationSeconds(afterDefaults)
        val tail = when {
            accepted.isEmpty() -> "Elige qué opciones quieres aplicar."
            remaining <= 0 -> "Con las opciones marcadas la sesión cabría en tu tiempo."
            else -> "Con las opciones marcadas ahorras ${formatSpan(saved)}; la sesión seguiría ${formatSpan(remaining)} por encima del objetivo."
        }
        return RuleOutput(
            card = AssistantSuggestion(
                id = "time_overage",
                type = AssistantActionType.COMPOSITE,
                title = "Ajustar al tiempo que fijaste",
                message = "$head $tail",
                priority = 2,
                details = details,
                kind = AssistantNoticeKind.USER_LIMIT,
            ),
            fallbackNote = head,
        )
    }

    private fun timeDetails(
        session: Session,
        context: SessionAnalysisContext,
        params: AssistantActionParameters,
    ): List<AssistantSuggestionDetail> {
        val exercises = session.allExercises().distinctBy { it.id }
        val accessories = exercises.filter { context.roleOf(it.id) == ExerciseRole.ACCESSORY }
        val details = mutableListOf<AssistantSuggestionDetail>()
        val baseSeconds = SessionAssistantActions.durationSeconds(session)

        // a) Descansos de los accesorios con margen: solo los ejercicios cuyo recorte cambia el reloj (los de una superserie, no).
        val restTargets = accessories.filter { ex ->
            val rest = ex.restTime ?: params.defaultRestSeconds
            rest - REST_CUT_SECONDS >= SessionAssistantActions.MIN_REST_SECONDS &&
                savingOf(session, AssistantDetailAction.ReduceRest(REST_CUT_SECONDS, listOf(ex.id), params.defaultRestSeconds), params, baseSeconds) >= MIN_USEFUL_SAVING_SECONDS
        }
        if (restTargets.isNotEmpty()) {
            val action = AssistantDetailAction.ReduceRest(REST_CUT_SECONDS, restTargets.map { it.id }, params.defaultRestSeconds)
            val effect = SessionAssistantActions.measure(session, SessionAssistantActions.apply(session, action, params))
            if (-effect.deltaSeconds >= MIN_USEFUL_SAVING_SECONDS) {
                val count = restTargets.size
                details += AssistantSuggestionDetail(
                    id = "time_detail_rest",
                    label = "${reduceRestLabel(REST_CUT_SECONDS)} en ${if (count == 1) "1 accesorio" else "$count accesorios"} · ahorra ${formatSpan(-effect.deltaSeconds)}",
                    action = action,
                    defaultAccepted = true,
                    effect = effect,
                )
            }
        }

        // b) La mejor pareja de accesorios para unir en superserie.
        val bestPair = exercises.zipWithNext()
            .filter { (a, b) -> viablePair(a, b, context, params) }
            .mapNotNull { (a, b) ->
                val action = AssistantDetailAction.ConvertToSuperset(exerciseId = a.id)
                val effect = SessionAssistantActions.measure(session, SessionAssistantActions.apply(session, action, params))
                if (-effect.deltaSeconds >= MIN_USEFUL_SAVING_SECONDS) Triple(Pair(a, b), action, effect) else null
            }
            .maxByOrNull { -it.third.deltaSeconds }
        if (bestPair != null) {
            val (pair, action, effect) = bestPair
            details += AssistantSuggestionDetail(
                id = "time_detail_superset_${pair.first.id}",
                label = "Unir ${pair.first.name} con ${pair.second.name} en superserie · ahorra ${formatSpan(-effect.deltaSeconds)}",
                action = action,
                defaultAccepted = false,
                effect = effect,
            )
        }

        // c) Una serie del accesorio con más series que más tiempo ahorre.
        val bestSet = accessories
            .filter { ex -> ex.sets.count { !it.isIneffective && !it.isEmptySlot } >= 2 }
            .mapNotNull { ex ->
                val action = AssistantDetailAction.ReduceSet(exerciseId = ex.id)
                val effect = SessionAssistantActions.measure(session, SessionAssistantActions.apply(session, action, params))
                if (-effect.deltaSeconds >= MIN_USEFUL_SAVING_SECONDS) Triple(ex, action, effect) else null
            }
            .maxByOrNull { -it.third.deltaSeconds }
        if (bestSet != null) {
            val (ex, action, effect) = bestSet
            details += AssistantSuggestionDetail(
                id = "time_detail_reduce_${ex.id}",
                label = "Quitar 1 serie de ${ex.name} · ahorra ${formatSpan(-effect.deltaSeconds)}",
                action = action,
                defaultAccepted = false,
                effect = effect,
            )
        }
        return details
    }

    /** Una pareja es viable si son dos accesorios de fuerza sin grupo previo, sin técnicas y sin músculo primario compartido. */
    private fun viablePair(
        a: Exercise,
        b: Exercise,
        context: SessionAnalysisContext,
        params: AssistantActionParameters,
    ): Boolean {
        if (context.roleOf(a.id) != ExerciseRole.ACCESSORY || context.roleOf(b.id) != ExerciseRole.ACCESSORY) return false
        if (a.cardioDetails != null || b.cardioDetails != null) return false
        if (a.sets.isEmpty() || b.sets.isEmpty()) return false
        if (a.supersetGroupRefOrLegacy() != null || b.supersetGroupRefOrLegacy() != null) return false
        if (a.sets.any { it.isDropSet || it.isRestPause } || b.sets.any { it.isDropSet || it.isRestPause }) return false
        val infoA = SessionAssistantEngine.resolveExerciseInfo(a, params.exerciseIndex) ?: return false
        val infoB = SessionAssistantEngine.resolveExerciseInfo(b, params.exerciseIndex) ?: return false
        // Dos barras a la vez ocupan el mismo rack: no es una pareja práctica.
        if (infoA.equipment == "Barra" && infoB.equipment == "Barra") return false
        val primariesA = primaryMuscles(a, params)
        val primariesB = primaryMuscles(b, params)
        return primariesA.isNotEmpty() && primariesB.isNotEmpty() && primariesA.intersect(primariesB).isEmpty()
    }

    private fun primaryMuscles(exercise: Exercise, params: AssistantActionParameters): Set<String> =
        ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, params.exerciseIndex)
            .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
            .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .toSet()

    private fun Exercise.supersetGroupRefOrLegacy(): String? =
        supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }

    private fun savingOf(
        session: Session,
        action: AssistantDetailAction,
        params: AssistantActionParameters,
        baseSeconds: Int,
    ): Int = baseSeconds - SessionAssistantActions.durationSeconds(SessionAssistantActions.apply(session, action, params))

    // ─── Coste estimado y desviación respecto del historial ─────────────────

    private fun accessoryFor(
        session: Session,
        context: SessionAnalysisContext,
        params: AssistantActionParameters,
        muscle: String,
    ): Exercise? = session.allExercises().distinctBy { it.id }
        .filter { context.roleOf(it.id) == ExerciseRole.ACCESSORY }
        .filter { ex -> ex.sets.count { !it.isIneffective && !it.isEmptySlot } >= 2 }
        .filter { ex ->
            ExerciseMuscleResolver.effectiveMusclesForVolume(ex, params.exerciseIndex).any { m ->
                VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis).equals(muscle, ignoreCase = true) ||
                    m.muscle.equals(muscle, ignoreCase = true)
            }
        }
        .maxByOrNull { ex -> ex.sets.count { !it.isIneffective && !it.isEmptySlot } }

    // ─── Utilidades de texto ─────────────────────────────────────────────────

    private fun ExerciseSet.plannedRpe(): Double {
        if (isFailure || intensityMode == IntensityMode.FAILURE) return 10.0
        targetRPE?.let { return it.coerceIn(1.0, 10.0) }
        targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
        return 8.0
    }

    private fun formatMinutes(seconds: Int): String = "${ceil(seconds.coerceAtLeast(0) / 60.0).toInt().coerceAtLeast(1)} min"

    /** «45 s» por debajo de minuto y medio; si no, «~N min». */
    private fun formatSpan(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return if (s < 90) "$s s" else "${(s / 60.0).roundToInt().coerceAtLeast(2)} min"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value).replace('.', ',')
}
