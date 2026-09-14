package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.displayName

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
        }.distinct().ifEmpty { fallbackLines(context) }
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
                        label = "Usa ${formatRelatorKg(kg)} kg",
                        span = "Usa ${formatRelatorKg(kg)} kg",
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
                lines = padVariants(lines),
                fingerprintBase = "PLAN:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 2,
                openers = listOf("Plan", "Hoy", "Carga"),
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
        val pct = context.targetPercentageRm?.let { "${formatRelatorKg(it)} %" } 
        val kg = (context.prescribedWeightKg ?: context.suggestedWeightKg)?.let { "≈ ${formatRelatorKg(it)} kg" }
        val scheme = listOfNotNull("$sets×$reps", pct, kg).joinToString(" ")
        val serie = context.setIndex + 1
        val done = context.progress.completedWorkingSets
        return "$label de {ex}, $scheme (serie $serie/$sets, $done hechas)."
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
        val kgPart = kg?.let { " ≈ ${formatRelatorKg(it)} kg" }.orEmpty()
        val basisPart = basis?.let { " del $it" }.orEmpty()
        return "{ex} va al ${formatRelatorKg(pct)} %$basisPart$kgPart."
    }

    private fun amrapLine(context: RelatorContext): String? {
        if (!context.isAmrap) return null
        return when (context.plan.progression) {
            is ProgressionRule.AmrapDrivenTm ->
                "AMRAP de {ex}: para con 1 limpia en reserva salvo PR; ≥5 reps y la TM sube 2,5 kg."
            else ->
                "AMRAP de {ex}: deja 1 limpia; no caces el fallo si no es el test."
        }
    }

    private fun topSetLine(context: RelatorContext): String? {
        if (!context.isTopSet) return null
        val kg = (context.prescribedWeightKg ?: context.suggestedWeightKg)?.let { formatRelatorKg(it) }
        return if (kg != null) {
            "Top set de {ex} a $kg kg. Calidad sobre ego."
        } else {
            "Esta es la serie pesada de {ex}. Una limpia vale más que forzar."
        }
    }

    private fun techniqueLine(context: RelatorContext): String? {
        val modifier = context.techniqueModifier ?: return null
        return when (modifier) {
            TechniqueModifier.PAUSE_2S -> "Pausa 2 s abajo en {ex}. Cuenta quieto antes de subir."
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
        return "Lift de competición: cues de plataforma en {ex}. Pausas, marcas y jueces importan."
    }

    private fun protocolLine(context: RelatorContext): String? {
        val id = context.plan.sourceProtocolId?.lowercase() ?: return null
        val pct = context.targetPercentageRm?.let { formatRelatorKg(it) }
        return when {
            id.contains("smolov") && pct != null ->
                "Smolov: {ex} al $pct % 1RM. El volumen es el estímulo."
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
            AutoregulationMode.AUTO -> "Autorregulación auto: {ex} ya viene ajustado al readiness."
            AutoregulationMode.OFF -> null
        }
    }

    private fun shouldOfferLoadAdjust(context: RelatorContext): Boolean {
        val mode = context.plan.autoregulationMode
        if (mode != AutoregulationMode.PROPOSE && mode != AutoregulationMode.AUTO) return false
        val score = context.readiness.dailyScore ?: context.readiness.exerciseScore ?: return false
        return score < 60
    }

    private fun fallbackLines(context: RelatorContext): List<String> {
        val protocol = context.plan.sourceProtocolName ?: context.plan.sourceProtocolId
        val protocolBit = protocol?.let { " ($it)" }.orEmpty()
        return listOf(
            "El plan$protocolBit marca esta serie de {ex}.",
            "Sigue la receta de {ex}; hoy no improvisamos el estímulo.",
            "{ex} entra como va escrito. Cambia solo si el cuerpo pide recorte.",
            "Esta pieza de {ex} es del bloque, no un extra suelto.",
            "Cumple el esquema de {ex}; el volumen ya está pensado.",
            "El protocolo$protocolBit cuenta con esta serie de {ex}.",
        )
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

internal fun padVariants(lines: List<String>, min: Int = 6): List<String> {
    val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.size >= min) return cleaned
    val extras = listOf(
        "Mantén el recetario: {ex} cuenta tal cual.",
        "No adelantes el plan de {ex}; esta serie tiene trabajo.",
        "Si dudas, baja 2,5 kg en {ex} y conserva el gesto.",
        "{ex} sigue en el programa. Ejecuta limpio.",
        "El estímulo de {ex} está en cumplir, no en inventar.",
        "Respeta series y descanso de {ex}; el bloque se sostiene así.",
    )
    return (cleaned + extras).distinct().take(min.coerceAtLeast(cleaned.size))
}
