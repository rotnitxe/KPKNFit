package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calibration.CalibrationDecisionType
import com.example.kpkn.domain.text.SpanishPlurals
import kotlin.math.abs
import kotlin.math.floor

/** Cuándo se nota el efecto de un AMRAP en el programa. */
enum class AmrapEffectTiming { SAME_SESSION, NEXT_WEEK, END_OF_CYCLE, END_OF_BLOCK, NEXT_SESSIONS, NONE }

/** Qué clase de efecto tiene el AMRAP según la regla del programa. */
enum class AmrapEffectKind {
    /** El rendimiento propone (o aplica) un cambio de TM por la vía semanal: nSuns, SBS, J&T y el calibrador de las plantillas. */
    TM_CHANGE,

    /** El AMRAP actúa al cerrar el ciclo (5/3/1, GZCLP). */
    CYCLE_CLOSE,

    /** El AMRAP actúa al entrar en la ola siguiente (Juggernaut). */
    BLOCK_CLOSE,

    /** APRE, serie 3: la carga de la serie 4 de la misma sesión. */
    NEXT_SET_LOAD,

    /** APRE, serie 4: el XRM de la semana siguiente. */
    NEXT_WEEK_XRM,

    /** Solo se registra en el historial y en los récords; el programa no lo usa para cambiar la carga. */
    RECORD_ONLY,

    /** La regla del programa no cambia nada con ESTA serie (nSuns con 1 repetición, una serie demasiado ligera…). */
    NO_CHANGE,
}

/** Quién decide el cambio: la regla del método (siempre se aplica) o el modo de autorregulación del programa (OFF, PROPONER, AUTO). */
enum class AmrapEffectGate { METHOD, OFF, PROPOSE, AUTO }

/**
 * El efecto que provocará un AMRAP según la regla de SU programa (CAL5), para la tarjeta en vivo (CAL3). Pura: sin interfaz.
 *
 * - [headline]: la frase corta de la tarjeta, con la coma decimal («nSuns: TM +5 kg la semana próxima»).
 * - [detail] y [warning]: la explicación y el aviso (por ejemplo, «tu TM quizá esté alto»).
 * - [gate]: con `PROPOSE` el cambio es una propuesta que la persona acepta o no; con `AUTO` se aplica solo; con `OFF` el programa no
 *   propone nada; con `METHOD` es la regla del método (se aplica al cerrar el ciclo o la ola y deja un aviso).
 */
data class AmrapProgramEffect(
    val kind: AmrapEffectKind,
    val timing: AmrapEffectTiming,
    val gate: AmrapEffectGate,
    val headline: String,
    val detail: String? = null,
    val warning: String? = null,
    val liftSlot: LiftSlot? = null,
    val tmDeltaKg: Double? = null,
    val tmDeltaPercent: Double? = null,
    /** El TM resultante cuando se conoce el TM actual. */
    val newTmKg: Double? = null,
    /** APRE, serie 3: la carga de la serie 4. */
    val nextSetLoadKg: Double? = null,
    /** APRE, serie 4: el XRM de la semana siguiente. */
    val nextWeekXrmKg: Double? = null,
    /** 5/3/1: el e1RM de la serie «+» con la cuenta de Wendler. */
    val e1rmKg: Double? = null,
) {
    /** true si el cambio es una propuesta que hay que aceptar. */
    val needsConfirmation: Boolean get() = gate == AmrapEffectGate.PROPOSE && (tmDeltaKg != null || tmDeltaPercent != null)

    /** true si el AMRAP solo se registra: ningún cambio de carga del programa depende de él. */
    val isRecordOnly: Boolean get() = kind == AmrapEffectKind.RECORD_ONLY
}

/**
 * «Qué provocará este AMRAP según su programa». Reutiliza las mismas reglas que ejecutan los motores
 * ([ProgramAutoregulationEngine.amrapTmChange], [JuggernautProgression], [Wendler531Progression], [Apre]…), así que la vista previa y
 * el resultado real no pueden divergir.
 *
 * Firma: [preview] recibe la receta del programa (`program.sourceRecipe`), su modo de autorregulación, su perfil de cargas y el
 * [AmrapHit] de la serie (con la carga, las repeticiones y, para el calibrador, la reserva declarada). [frozenCyclesBefore] son los
 * ciclos seguidos ya congelados de 5/3/1 (`ProgramAutoregulationEngine.frozenCycleStreaks`, sin contar este ciclo): con ellos avisa del
 * reinicio. Para la serie 3 de APRE, la tarjeta pasa `isLastAmrapOfExercise = false`.
 */
object AmrapProgramPreview {

    fun preview(
        recipe: TrainingPlanRecipe?,
        mode: AutoregulationMode,
        profile: PowerliftingProfile?,
        hit: AmrapHit,
        frozenCyclesBefore: Int = 0,
    ): AmrapProgramEffect {
        val lift = hit.liftSlot
        val e1rm = hit.weightKg?.let { Wendler531Progression.e1rmKg(it, hit.actualReps) }?.takeIf { it > 0.0 }
        if (recipe == null) {
            return record(hit, lift, e1rm, "Sin regla de progresión: se guarda en tu historial y tus récords; no cambia tu carga.")
        }
        val name = programName(recipe)
        val prefix = name?.let { "$it: " }.orEmpty()
        val tm = lift?.let { TrainingMaxResolver.trainingMax(profile, it, recipe.trainingMaxPercent) }
        if (hit.isNextSessionsCalibrator) return calibrator(recipe, mode, profile, hit, lift, prefix)
        return when (val rule = recipe.progression) {
            is ProgressionRule.CycleIncrement -> cycleIncrement(rule, hit, lift, tm, e1rm, prefix, frozenCyclesBefore)
            is ProgressionRule.AmrapDrivenTm -> tmByWeek(rule, mode, hit, lift, tm, prefix, "tabla de nSuns")
            is ProgressionRule.RepTargetDrivenTm -> tmByWeek(rule, mode, hit, lift, tm, prefix, "regla de repeticiones")
            is ProgressionRule.RepsOverStandardTm -> juggernaut(rule, hit, lift, tm, prefix)
            is ProgressionRule.StagedVolume -> gzclp(rule, hit, lift, prefix)
            ProgressionRule.RepMaxAutoregulated -> repMax(mode, profile, recipe, hit, lift, tm, prefix)
            is ProgressionRule.ApreXrm -> apre(rule, hit, lift, prefix)
            is ProgressionRule.TopSetPr,
            is ProgressionRule.WeeklyKg,
            is ProgressionRule.WeeklyPercent,
            ProgressionRule.None,
            -> record(
                hit, lift, e1rm,
                "${prefix}AMRAP de registro: se guarda en tu historial y tus récords; no cambia tu carga en este programa.",
            )
        }
    }

    /** El nombre corto del programa para el titular; null si la receta no es de un método con nombre propio. */
    internal fun programName(recipe: TrainingPlanRecipe): String? = when {
        recipe.id.startsWith("nsuns") -> "nSuns"
        recipe.id.startsWith("wendler-531") -> "5/3/1"
        recipe.id == "juggernaut-2" -> "Juggernaut"
        recipe.id == "gzclp" -> "GZCLP"
        recipe.id == "gzcl-jt-2" -> "J&T"
        recipe.id == "kpkn-sbs-rtf" -> "Estilo SBS"
        recipe.id.startsWith("apre") -> "APRE"
        else -> null
    }

    private fun record(hit: AmrapHit, lift: LiftSlot?, e1rm: Double?, headline: String): AmrapProgramEffect = AmrapProgramEffect(
        kind = AmrapEffectKind.RECORD_ONLY,
        timing = AmrapEffectTiming.NONE,
        gate = AmrapEffectGate.METHOD,
        headline = headline,
        detail = e1rm?.let { "e1RM de la serie: ${kg(it)} kg." },
        liftSlot = lift,
        e1rmKg = e1rm,
    )

    // ─── 5/3/1 ────────────────────────────────────────────────────────────────────

    private fun cycleIncrement(
        rule: ProgressionRule.CycleIncrement,
        hit: AmrapHit,
        lift: LiftSlot?,
        tm: Double?,
        e1rm: Double?,
        prefix: String,
        frozenCyclesBefore: Int,
    ): AmrapProgramEffect {
        val short = ProgramAutoregulationEngine.isShortAmrap(hit)
        val record = e1rm?.let { "Récord de repeticiones y e1RM de ${kg(it)} kg (la cuenta de Wendler)." }
        if (!short) {
            return AmrapProgramEffect(
                kind = AmrapEffectKind.CYCLE_CLOSE,
                timing = if (rule.scope == com.example.kpkn.data.protocols.IncrementScope.BLOCK) AmrapEffectTiming.END_OF_BLOCK else AmrapEffectTiming.END_OF_CYCLE,
                gate = AmrapEffectGate.METHOD,
                headline = "${prefix}récord de repeticiones; el TM no se mueve y sube al cerrar el ciclo.",
                detail = record,
                liftSlot = lift,
                e1rmKg = e1rm,
            )
        }
        val resets = Wendler531Progression.resetsAtFrozenStreak(frozenCyclesBefore + 1, rule.resetAfterFrozenCycles)
        return AmrapProgramEffect(
            kind = AmrapEffectKind.CYCLE_CLOSE,
            timing = AmrapEffectTiming.END_OF_CYCLE,
            gate = AmrapEffectGate.METHOD,
            headline = "${prefix}no llegaste a las repeticiones mínimas; el TM se congela al cerrar el ciclo, sin bajarlo.",
            detail = record,
            warning = if (resets) {
                "Serían ${rule.resetAfterFrozenCycles} ciclos congelados seguidos: el TM se reinicia un ${kg(rule.resetPercent)} %" +
                    (tm?.let { " (de ${kg(it)} a ${kg(Wendler531Progression.resetTmKg(it, rule.resetPercent, 0.5))} kg)" }.orEmpty()) + "."
            } else {
                null
            },
            liftSlot = lift,
            e1rmKg = e1rm,
        )
    }

    // ─── nSuns y SBS: cambio de TM por la vía semanal ─────────────────────────────

    private fun tmByWeek(
        rule: ProgressionRule,
        mode: AutoregulationMode,
        hit: AmrapHit,
        lift: LiftSlot?,
        tm: Double?,
        prefix: String,
        ruleLabel: String,
    ): AmrapProgramEffect {
        val gate = gateOf(mode)
        if (lift == null) {
            return noChange(gate, lift, "${prefix}esta serie no tiene un levantamiento con TM: se guarda en tu historial.")
        }
        val change = ProgramAutoregulationEngine.amrapTmChange(rule, hit)
        if (change == null) {
            val why = when {
                rule is ProgressionRule.AmrapDrivenTm && (hit.percent ?: 0.0) < ProgramAutoregulationEngine.AMRAP_TM_MIN_PERCENT ->
                    "con menos del ${kg(ProgramAutoregulationEngine.AMRAP_TM_MIN_PERCENT)} % del TM la serie no mide el TM"
                hit.weightRatio != null && hit.weightRatio < ProgramAutoregulationEngine.LIGHTER_LOAD_RATIO ->
                    "levantaste menos de lo prescrito: no mueve el TM"
                rule is ProgressionRule.AmrapDrivenTm -> "con ${SpanishPlurals.reps(hit.actualReps)} la $ruleLabel no sube el TM"
                else -> "con ${SpanishPlurals.reps(hit.actualReps)} la $ruleLabel no cambia el TM"
            }
            return noChange(gate, lift, "${prefix}el TM no cambia: $why.")
        }
        val delta = change.kgDelta
        val percent = change.percentDelta
        val newTm = tm?.let { current ->
            when {
                delta != null -> roundHalf(current + delta)
                percent != null -> roundHalf(current * (1.0 + percent / 100.0))
                else -> null
            }
        }
        val amount = if (delta != null) "${signed(delta)} kg" else "${signed(percent ?: 0.0)} %"
        val head = when (gate) {
            AmrapEffectGate.OFF -> "${prefix}la autorregulación está desactivada: no se propone ningún cambio de TM."
            else -> "${prefix}TM $amount la semana próxima."
        }
        return AmrapProgramEffect(
            kind = AmrapEffectKind.TM_CHANGE,
            timing = if (gate == AmrapEffectGate.OFF) AmrapEffectTiming.NONE else AmrapEffectTiming.NEXT_WEEK,
            gate = gate,
            headline = head,
            detail = gateDetail(gate, newTm?.let { "TM de ${AuthoredProgressionEngine.liftName(lift)}: ${kg(tm!!)} → ${kg(it)} kg." }),
            liftSlot = lift,
            tmDeltaKg = delta,
            tmDeltaPercent = percent,
            newTmKg = newTm,
        )
    }

    // ─── Juggernaut ───────────────────────────────────────────────────────────────

    private fun juggernaut(
        rule: ProgressionRule.RepsOverStandardTm,
        hit: AmrapHit,
        lift: LiftSlot?,
        tm: Double?,
        prefix: String,
    ): AmrapProgramEffect {
        val result = JuggernautProgression.evaluate(rule, hit)
            ?: return noChange(AmrapEffectGate.METHOD, lift, "${prefix}esta serie no tiene estándar de repeticiones: el TM no cambia.", AmrapEffectTiming.END_OF_BLOCK)
        val raised = result.outcome == JuggernautProgression.Outcome.RAISED && result.deltaKg > 0.0
        val newTm = tm?.let { roundHalf(it + result.deltaKg) }
        val headline = when {
            raised && newTm != null -> "${prefix}TM ${kg(newTm)} kg tras esta ola."
            raised -> "${prefix}TM +${kg(result.deltaKg)} kg tras esta ola."
            else -> "${prefix}el TM no cambia tras esta ola."
        }
        return AmrapProgramEffect(
            kind = AmrapEffectKind.BLOCK_CLOSE,
            timing = AmrapEffectTiming.END_OF_BLOCK,
            gate = AmrapEffectGate.METHOD,
            headline = headline,
            detail = JuggernautProgression.summary(result) + ".",
            warning = if (result.outcome == JuggernautProgression.Outcome.BELOW_STANDARD) {
                "No llegaste al estándar de ${result.standardReps}: tu TM quizá esté alto."
            } else {
                null
            },
            liftSlot = lift,
            tmDeltaKg = result.deltaKg.takeIf { raised },
            newTmKg = newTm.takeIf { raised },
        )
    }

    // ─── GZCLP ────────────────────────────────────────────────────────────────────

    private fun gzclp(rule: ProgressionRule.StagedVolume, hit: AmrapHit, lift: LiftSlot?, prefix: String): AmrapProgramEffect {
        val counted = GzclpProgression.cappedAmrapReps(hit.actualReps, rule.t1AmrapCap)
        val stage = GzclpProgression.T1Stage.entries.firstOrNull { it.reps == hit.prescribedReps }
        val short = ProgramAutoregulationEngine.isShortAmrap(hit)
        val capNote = if (hit.actualReps > rule.t1AmrapCap) {
            "Cuentan ${SpanishPlurals.reps(counted)}: la AMRAP del T1 se topa en ${rule.t1AmrapCap}."
        } else {
            "La AMRAP del T1 no cambia la carga: es margen de volumen."
        }
        return AmrapProgramEffect(
            kind = AmrapEffectKind.CYCLE_CLOSE,
            timing = AmrapEffectTiming.END_OF_CYCLE,
            gate = AmrapEffectGate.METHOD,
            headline = if (short) {
                "${prefix}no llegaste a las repeticiones mínimas: al cerrar el ciclo pasas de etapa con la misma carga."
            } else {
                "${prefix}la AMRAP no cambia la carga; si completas el volumen base, el TM sube al cerrar el ciclo."
            },
            detail = capNote + (stage?.let { " Etapa ${it.number}: ${it.label}." } ?: ""),
            warning = if (stage == GzclpProgression.T1Stage.STAGE_3 && short) {
                "Etapa 3 sin completar: toca probar un 5RM nuevo y reiniciar al ${(GzclpProgression.RESTART_FRACTION_OF_FIVE_RM * 100).toInt()} % de ese 5RM."
            } else {
                null
            },
            liftSlot = lift,
        )
    }

    // ─── J&T 2.0 ──────────────────────────────────────────────────────────────────

    private fun repMax(
        mode: AutoregulationMode,
        profile: PowerliftingProfile?,
        recipe: TrainingPlanRecipe,
        hit: AmrapHit,
        lift: LiftSlot?,
        tm: Double?,
        prefix: String,
    ): AmrapProgramEffect {
        val gate = gateOf(mode)
        val weight = hit.weightKg
        if (lift == null || weight == null || profile == null || tm == null || recipe.trainingMaxPercent <= 0.0 ||
            hit.actualReps !in 1..ProgramAutoregulationEngine.REP_MAX_MAX_REPS
        ) {
            return noChange(gate, lift, "${prefix}el máximo de repeticiones se compara con tu TM al cerrar la semana.")
        }
        val e1rm = calculateHybrid1RM(weight, hit.actualReps)
        val implied = tm / recipe.trainingMaxPercent
        val change = ProgramAutoregulationEngine.repMaxTmChange(e1rm, implied)
            ?: return noChange(gate, lift, "${prefix}el TM no cambia: tu e1RM de ${kg(e1rm)} kg está cerca de los ${kg(implied)} kg que implica tu TM.")
        val percent = change.percentDelta ?: 0.0
        val newTm = roundHalf(tm * (1.0 + percent / 100.0))
        return AmrapProgramEffect(
            kind = AmrapEffectKind.TM_CHANGE,
            timing = if (gate == AmrapEffectGate.OFF) AmrapEffectTiming.NONE else AmrapEffectTiming.NEXT_WEEK,
            gate = gate,
            headline = if (gate == AmrapEffectGate.OFF) {
                "${prefix}la autorregulación está desactivada: no se propone ningún cambio de TM."
            } else {
                "${prefix}TM ${signed(percent)} % la semana próxima."
            },
            detail = gateDetail(gate, "Tu e1RM de ${kg(e1rm)} kg frente a los ${kg(implied)} kg que implica el TM: de ${kg(tm)} a ${kg(newTm)} kg."),
            liftSlot = lift,
            tmDeltaPercent = percent,
            newTmKg = newTm,
            e1rmKg = e1rm,
        )
    }

    // ─── APRE ─────────────────────────────────────────────────────────────────────

    private fun apre(rule: ProgressionRule.ApreXrm, hit: AmrapHit, lift: LiftSlot?, prefix: String): AmrapProgramEffect {
        val variant = Apre.variantOfXrmReps(hit.prescribedReps)
        val weight = hit.weightKg
        if (variant == null || weight == null) {
            return record(hit, lift, null, "${prefix}esta serie no tiene XRM: se guarda en tu historial.")
        }
        val reps = Apre.validReps(hit.actualReps)
        val band = Apre.band(variant, reps)
        val bandText = if (band == Apre.Band.HOLD) "mantener" else "${signed(band.percent)} %"
        return if (hit.isLastAmrapOfExercise) {
            val xrm = Apre.nextWeekXrmKg(weight, reps, variant, rule.incrementKg)
            AmrapProgramEffect(
                kind = AmrapEffectKind.NEXT_WEEK_XRM,
                timing = AmrapEffectTiming.NEXT_WEEK,
                gate = AmrapEffectGate.METHOD,
                headline = "${prefix}tu ${variant.xrmReps}RM de la semana próxima: ${kg(xrm)} kg.",
                detail = "${SpanishPlurals.reps(reps)} válidas con ${kg(weight)} kg (${variant.label}: $bandText).",
                liftSlot = lift,
                nextWeekXrmKg = xrm,
            )
        } else {
            val next = Apre.set4LoadKg(weight, reps, variant, rule.incrementKg)
            AmrapProgramEffect(
                kind = AmrapEffectKind.NEXT_SET_LOAD,
                timing = AmrapEffectTiming.SAME_SESSION,
                gate = AmrapEffectGate.METHOD,
                headline = "${prefix}serie 4 con ${kg(next)} kg.",
                detail = "${SpanishPlurals.reps(reps)} válidas en la serie 3 (${variant.label}: $bandText).",
                liftSlot = lift,
                nextSetLoadKg = next,
            )
        }
    }

    // ─── Calibrador de las plantillas (power-*) ───────────────────────────────────

    private fun calibrator(
        recipe: TrainingPlanRecipe,
        mode: AutoregulationMode,
        profile: PowerliftingProfile?,
        hit: AmrapHit,
        lift: LiftSlot?,
        prefix: String,
    ): AmrapProgramEffect {
        val gate = gateOf(mode)
        val decision = lift?.let { ProgramAutoregulationEngine.calibratorDecision(recipe, profile, hit, it) }
        if (lift == null || decision == null) {
            return noChange(gate, lift, "${prefix}Calibrador: sin un TM con el que comparar, la serie solo se guarda.", AmrapEffectTiming.NEXT_SESSIONS)
        }
        return when (decision.type) {
            CalibrationDecisionType.RAISE, CalibrationDecisionType.LOWER -> {
                val percent = (decision.factor - 1.0) * 100.0
                val tm = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent)
                val newTm = tm?.let { roundHalf(it * decision.factor) }
                AmrapProgramEffect(
                    kind = AmrapEffectKind.TM_CHANGE,
                    timing = if (gate == AmrapEffectGate.OFF) AmrapEffectTiming.NONE else AmrapEffectTiming.NEXT_WEEK,
                    gate = gate,
                    headline = if (gate == AmrapEffectGate.OFF) {
                        "${prefix}Calibrador: la autorregulación está desactivada, no se propone ningún cambio de TM."
                    } else {
                        "${prefix}Calibrador: TM ${signed(percent)} % la semana próxima."
                    },
                    detail = gateDetail(
                        gate,
                        "Capacidad de ${kg(decision.capacityKg ?: 0.0)} kg frente a los ${kg(decision.referenceKg ?: 0.0)} kg que implica tu TM.",
                    ),
                    liftSlot = lift,
                    tmDeltaPercent = percent,
                    newTmKg = newTm,
                )
            }
            CalibrationDecisionType.HOLD, CalibrationDecisionType.FIRST_REFERENCE ->
                noChange(gate, lift, "${prefix}Calibrador: tu capacidad coincide con tu TM; no cambia.", AmrapEffectTiming.NEXT_SESSIONS)
            CalibrationDecisionType.LOW_REPS_DROP, CalibrationDecisionType.NOT_APPLIED ->
                noChange(gate, lift, "${prefix}Calibrador: ${decision.reason}", AmrapEffectTiming.NEXT_SESSIONS)
        }
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────────

    private fun gateOf(mode: AutoregulationMode): AmrapEffectGate = when (mode) {
        AutoregulationMode.OFF -> AmrapEffectGate.OFF
        AutoregulationMode.PROPOSE -> AmrapEffectGate.PROPOSE
        AutoregulationMode.AUTO -> AmrapEffectGate.AUTO
    }

    private fun gateDetail(gate: AmrapEffectGate, change: String?): String? {
        val tail = when (gate) {
            AmrapEffectGate.PROPOSE -> "Te lo propondremos al cerrar la semana: tú decides."
            AmrapEffectGate.AUTO -> "Se aplica solo al cerrar la semana."
            else -> null
        }
        return listOfNotNull(change, tail).joinToString(" ").ifBlank { null }
    }

    private fun noChange(
        gate: AmrapEffectGate,
        lift: LiftSlot?,
        headline: String,
        timing: AmrapEffectTiming = AmrapEffectTiming.NONE,
    ): AmrapProgramEffect = AmrapProgramEffect(
        kind = AmrapEffectKind.NO_CHANGE,
        timing = timing,
        gate = gate,
        headline = headline,
        liftSlot = lift,
    )

    private fun kg(value: Double): String = NativeProgressionText.formatKg(value)

    private fun signed(value: Double): String = if (value >= 0.0) "+" + kg(value) else kg(value)

    private fun roundHalf(value: Double): Double = floor(value / 0.5 + 0.5) * 0.5
}
