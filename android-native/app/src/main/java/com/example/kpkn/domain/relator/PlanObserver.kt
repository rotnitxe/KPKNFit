package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.displayName
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.domain.training.AmrapProgramPreview
import com.example.kpkn.domain.training.ProgramAutoregulationEngine

object PlanObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        if (context.phase == RelatorSessionPhase.MOBILITY) return emptyList()
        val lines = buildList {
            slotLine(context)?.let(::add)
            schemeLine(context)?.let(::add)
            amrapLine(context)?.let(::add)
            topSetLine(context)?.let(::add)
            techniqueLine(context)?.let(::add)
            weekLine(context)?.let(::add)
            competitionLine(context)?.let(::add)
            protocolLine(context)?.let(::add)
            autoregLine(context)?.let(::add)
        }.distinct().ifEmpty { setLine(context) }
        val actions = buildList {
            if (shouldOfferLoadAdjust(context)) {
                val pct = if ((context.readiness.dailyScore ?: 100) < 50) -10.0 else -5.0
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.ADJUST_LOAD,
                        label = "Ajusta carga",
                        span = "Ajusta carga",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                        loadDeltaPercent = pct,
                    ),
                )
            }
            context.suggestedWeightKg?.takeIf { it > 0.0 }?.let { kg ->
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.APPLY_SUGGESTED_LOAD,
                        label = "Usa ${formatRelatorKg(kg)}\u00A0kg",
                        span = "Usa ${formatRelatorKg(kg)}\u00A0kg",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                        weightKg = kg,
                    ),
                )
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.PLAN,
                priority = 52,
                relevance = if (context.plan.sourceProtocolId != null || context.slotRole != null) 0.85 else 0.35,
                lines = realVariants(lines),
                fingerprintBase = "PLAN:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 2,
            ),
        )
    }

    private fun slotLine(context: RelatorContext): String? {
        val role = context.slotRole ?: return null
        val label = when (role) {
            SlotRole.T1_MAIN -> "T1 principal"
            SlotRole.T2_SUPPLEMENTAL -> "T2 suplementario"
            SlotRole.T3_ACCESSORY -> "T3 accesorio"
            SlotRole.SPEED -> "velocidad"
            SlotRole.TECHNIQUE -> "técnica"
        }
        val sets = context.setCount.coerceAtLeast(1)
        val reps = context.targetReps?.toString() ?: "—"
        val kg = (context.prescribedWeightKg ?: context.suggestedWeightKg)?.let { "≈ ${relatorKg(it)}" }
        val scheme = listOfNotNull("$sets series de $reps", kg).joinToString(", ")
        return "$label de {ex}: $scheme."
    }

    private fun schemeLine(context: RelatorContext): String? {
        val pct = context.targetPercentageRm ?: return null
        val kg = context.prescribedWeightKg ?: context.suggestedWeightKg
        val basis = when (context.loadBasis) {
            LoadBasis.PERCENT_TM -> "TM"
            LoadBasis.PERCENT_1RM -> "1RM"
            LoadBasis.PERCENT_DESIRED_MAX -> "máx. deseado"
            LoadBasis.PERCENT_OF_TOP_SET -> "top set"
            else -> null
        }
        val kgPart = kg?.let { " ≈ ${relatorKg(it)}" }.orEmpty()
        val basisPart = basis?.let { " del $it" }.orEmpty()
        return "{ex} va al ${relatorPercent(pct)}$basisPart$kgPart."
    }

    private const val GENERIC_AMRAP_LINE = "AMRAP de {ex}: deja 1 limpia; no caces el fallo si no es el test."

    /** Conserva las reglas conocidas y usa el mismo preview tipado del plan para las reglas nuevas. */
    private fun amrapLine(context: RelatorContext): String? {
        context.plan.progressionContext?.let { progression ->
            AmrapProgramPreview.relatorNote(
                recipe = progression.recipe,
                mode = context.plan.autoregulationMode,
                profile = progression.profile,
                liftSlot = progression.liftSlot,
                slotRole = progression.slotRole ?: context.slotRole,
                plannedVolumeReps = progression.plannedVolumeReps,
                plannedMinimumReps = progression.plannedMinimumReps,
                purpose = progression.purpose,
                hit = progression.hit,
                tierCheck = progression.tierCheck,
                frozenCyclesBefore = progression.frozenCyclesBefore,
                isApreBlock = progression.isApreBlock,
                isLastAmrapOfExercise = progression.isLastAmrapOfExercise,
            )?.let { return it.text }
        }
        if (!context.isAmrap) return null
        return when (val rule = context.plan.progression) {
            is ProgressionRule.AmrapDrivenTm -> amrapTableLine(rule, context)
            is ProgressionRule.RepTargetDrivenTm -> repTargetLine(rule)
            else -> GENERIC_AMRAP_LINE
        }
    }

    /**
     * `AmrapDrivenTm`: los kilos de la tabla de la receta para 4 o 5 reps y para 6 o más. Solo un AMRAP desde el
     * 85 % del TM mueve el TM (`ProgramAutoregulationEngine`): en uno más ligero, o de porcentaje
     * desconocido, no se promete ninguna subida.
     */
    private fun amrapTableLine(rule: ProgressionRule.AmrapDrivenTm, context: RelatorContext): String {
        val percent = context.targetPercentageRm
        if (percent == null || percent < ProgramAutoregulationEngine.AMRAP_TM_MIN_PERCENT) return GENERIC_AMRAP_LINE
        val raise = when {
            rule.fourToFiveKg > 0.0 && rule.sixPlusKg > 0.0 ->
                "TM +${kgText(rule.fourToFiveKg)}\u00A0kg con 4-5 reps; +${kgText(rule.sixPlusKg)}\u00A0kg con 6+"
            rule.sixPlusKg > 0.0 -> "TM +${kgText(rule.sixPlusKg)}\u00A0kg con 6+ reps"
            rule.fourToFiveKg > 0.0 -> "TM +${kgText(rule.fourToFiveKg)}\u00A0kg con 4-5 reps"
            else -> return GENERIC_AMRAP_LINE
        }
        return "AMRAP de {ex}: 1 limpia en reserva salvo PR. $raise."
    }

    /** `RepTargetDrivenTm`: el porcentaje por repetición sobre el objetivo y por repetición que falta. */
    private fun repTargetLine(rule: ProgressionRule.RepTargetDrivenTm): String {
        if (rule.extraRepPercent <= 0.0) return GENERIC_AMRAP_LINE
        val miss = if (rule.missedRepPercent > 0.0) {
            " Si faltan ${rule.missThreshold} o más, baja ${kgText(rule.missedRepPercent)} % por rep."
        } else {
            ""
        }
        return "AMRAP de {ex}: cada rep extra sube el TM ${kgText(rule.extraRepPercent)} %.$miss"
    }

    /** `62.5` → «62,5», `5.0` → «5»: coma decimal y sin ceros sobrantes. */
    private fun kgText(value: Double): String = NativeProgressionText.formatKg(value).replace('.', ',')

    private fun topSetLine(context: RelatorContext): String? {
        if (!context.isTopSet) return null
        val kg = (context.prescribedWeightKg ?: context.suggestedWeightKg)?.let { relatorKg(it) }
        return if (kg != null) {
            "Top set de {ex} a $kg. Calidad antes que ego."
        } else {
            "Esta es la serie pesada de {ex}. Una limpia vale más que forzar."
        }
    }

    private fun techniqueLine(context: RelatorContext): String? {
        val modifier = context.techniqueModifier ?: return null
        return when (modifier) {
            TechniqueModifier.PAUSE_2S -> "Pausa 2 s abajo en {ex}. Cuenta quieto antes de subir."
            TechniqueModifier.TEMPO_3_0_3 -> "Tempo 3-0-3 en {ex}: baja 3, sin rebote, sube 3."
            else -> "${modifier.displayName()} en {ex}. Ese detalle es el estímulo."
        }
    }

    private fun weekLine(context: RelatorContext): String? {
        val week = context.plan.weekIndexInBlock ?: return null
        val total = context.plan.weeksInBlock ?: return null
        val goal = context.plan.blockGoal?.label?.lowercase() ?: context.plan.blockName?.lowercase()
        val goalPart = goal?.let { " de $it" }.orEmpty()
        return "Semana $week de $total$goalPart. {ex} sigue esa receta."
    }

    private fun competitionLine(context: RelatorContext): String? {
        if (!context.isCompetitionLift) return null
        return "Competición: cuida la plataforma en {ex}. Pausas y jueces importan."
    }

    private fun protocolLine(context: RelatorContext): String? {
        val id = context.plan.sourceProtocolId?.lowercase() ?: return null
        val pct = context.targetPercentageRm?.let { relatorPercent(it) }
        return when {
            id.contains("smolov") && pct != null ->
                "Smolov: {ex} al $pct 1RM. El volumen es el estímulo."
            id.contains("westside") || context.loadBasis == LoadBasis.REP_MAX ->
                "Westside ME: {ex} busca un RM limpio. Cero reps sucias."
            id.contains("531") && context.isAmrap ->
                "5/3/1: AMRAP de {ex}. Reserva 1 limpia salvo que vayas a PR."
            else -> null
        }
    }

    private fun autoregLine(context: RelatorContext): String? {
        return when (context.plan.autoregulationMode) {
            AutoregulationMode.PROPOSE -> "Autorregulación propone: mira la carga de {ex} y confirma."
            AutoregulationMode.AUTO -> "Autorregulación automática: {ex} ya viene ajustado a tu disponibilidad."
            AutoregulationMode.OFF -> null
        }
    }

    private fun shouldOfferLoadAdjust(context: RelatorContext): Boolean {
        val mode = context.plan.autoregulationMode
        if (mode != AutoregulationMode.PROPOSE && mode != AutoregulationMode.AUTO) return false
        val score = context.readiness.dailyScore ?: context.readiness.exerciseScore ?: return false
        return score < 60
    }

    /**
     * Sin estructura de receta, el plan dice la serie que toca: «Serie 2 de 4: 95 kg × 6 a RIR 2.» (LR1h). Siempre hay una
     * línea, aunque no haya carga ni reps, para que el candidato exista.
     */
    private fun setLine(context: RelatorContext): List<String> {
        val number = context.setIndex + 1
        val total = context.setCount.takeIf { it > 1 && it >= number }
        val head = if (total != null) "Serie $number de $total" else "Serie $number"
        val kg = (context.prescribedWeightKg ?: context.suggestedWeightKg)?.takeIf { it > 0.0 }
        val reps = context.targetReps?.takeIf { it > 0 }
        val mark = when {
            kg != null && reps != null -> relatorSetMark(kg, reps)
            kg != null -> relatorKg(kg)
            reps != null -> "$reps reps"
            else -> null
        }
        val rir = context.targetRir?.let { " a RIR $it" }.orEmpty()
        return listOf(if (mark != null) "$head: $mark$rir." else "$head$rir.")
    }
}

internal object RelatorActionKind {
    const val APPLY_SUGGESTED_LOAD = "APPLY_SUGGESTED_LOAD"
    const val ADJUST_LOAD = "ADJUST_LOAD"
    const val START_REST = "START_REST"
    const val EXTEND_REST = "EXTEND_REST"
    const val SKIP_REMAINING_WARMUPS = "SKIP_REMAINING_WARMUPS"
    const val OPEN_REPLACE = "OPEN_REPLACE"
    const val OPEN_READINESS = "OPEN_READINESS"
    const val OPEN_TECHNIQUE = "OPEN_TECHNIQUE"
    const val OPEN_HISTORY = "OPEN_HISTORY"
    const val CAPTURE_MEDIA = "CAPTURE_MEDIA"
    const val OPEN_ALBUM = "OPEN_ALBUM"
}

/**
 * Las frases reales de un observador, sin vacías ni repetidas (LR1h). Nunca se completan con relleno: un observador con
 * pocas frases tiene pocas variantes, y el selector rota entre ellas.
 */
internal fun realVariants(lines: List<String>): List<String> =
    lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
