package com.example.kpkn.screens.workout

internal enum class RelatorTissueWindow {
    INTRA,
    DAY,
}

internal data class RelatorTissueHint(
    val muscleLabel: String,
    val sourceExerciseName: String,
    val jointCare: String?,
    val window: RelatorTissueWindow,
    val drainScore: Double,
)

internal data class RelatorPriorExercise(
    val name: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val stabilizerMuscles: List<String> = emptyList(),
    val highIntensity: Boolean = false,
    val drainByMuscle: Map<String, Double> = emptyMap(),
)

internal const val RELATOR_DAY_DRAIN_THRESHOLD = 28.0

internal fun pickRelatorTissueHint(
    todayPrimaryMuscles: List<String>,
    todayStabilizers: List<String>,
    intraSession: List<RelatorPriorExercise>,
    yesterday: List<RelatorPriorExercise>,
): RelatorTissueHint? {
    val intra = intraSession.mapNotNull { prior ->
        bestIntraMatch(todayPrimaryMuscles, prior)
    }.maxByOrNull { it.drainScore }
    if (intra != null) return intra

    return yesterday.mapNotNull { prior ->
        bestDayMatch(todayStabilizers, prior)
    }.maxByOrNull { it.drainScore }
}

private fun bestIntraMatch(
    todayPrimary: List<String>,
    prior: RelatorPriorExercise,
): RelatorTissueHint? {
    val priorPrimary = prior.primaryMuscles.map(::canonicalRelatorMuscle)
    val priorSecondary = prior.secondaryMuscles.map(::canonicalRelatorMuscle)
    return todayPrimary.mapNotNull { raw ->
        val muscle = canonicalRelatorMuscle(raw) ?: return@mapNotNull null
        val asPrimary = priorPrimary.contains(muscle)
        val asSecondary = priorSecondary.contains(muscle)
        if (!asPrimary && !asSecondary) return@mapNotNull null
        val drain = drainFor(prior, muscle) + if (asPrimary) 2.0 else 1.0
        RelatorTissueHint(
            muscleLabel = displayRelatorMuscle(muscle),
            sourceExerciseName = prior.name,
            jointCare = jointCareFor(muscle),
            window = RelatorTissueWindow.INTRA,
            drainScore = drain,
        )
    }.maxByOrNull { it.drainScore }
}

private fun bestDayMatch(
    todayStabilizers: List<String>,
    prior: RelatorPriorExercise,
): RelatorTissueHint? {
    val priorPrimary = prior.primaryMuscles.map(::canonicalRelatorMuscle)
    return todayStabilizers.mapNotNull { raw ->
        val muscle = canonicalRelatorMuscle(raw) ?: return@mapNotNull null
        if (muscle != RelatorMuscleId.LUMBAR) return@mapNotNull null
        val wasPrimary = priorPrimary.contains(muscle)
        val drainPct = drainFor(prior, muscle)
        val qualifies = (wasPrimary && prior.highIntensity) || drainPct >= RELATOR_DAY_DRAIN_THRESHOLD
        if (!qualifies) return@mapNotNull null
        RelatorTissueHint(
            muscleLabel = displayRelatorMuscle(muscle),
            sourceExerciseName = prior.name,
            jointCare = null,
            window = RelatorTissueWindow.DAY,
            drainScore = drainPct + if (wasPrimary) 2.0 else 0.0,
        )
    }.maxByOrNull { it.drainScore }
}

private enum class RelatorMuscleId {
    TRICEPS,
    CHEST,
    LUMBAR,
    SHOULDER,
}

private fun canonicalRelatorMuscle(raw: String): RelatorMuscleId? {
    val key = raw.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("_", " ")
        .trim()
    if (key.isEmpty()) return null
    return when {
        "tricep" in key || "triceps" in key -> RelatorMuscleId.TRICEPS
        "pector" in key || "pecho" in key || "chest" in key || key == "pec" -> RelatorMuscleId.CHEST
        "erector" in key || "lumbar" in key || "espalda baja" in key ||
            "lower back" in key || "spinal" in key -> RelatorMuscleId.LUMBAR
        "deltoid" in key || "hombro" in key || "shoulder" in key -> RelatorMuscleId.SHOULDER
        else -> null
    }
}

private fun displayRelatorMuscle(id: RelatorMuscleId): String = when (id) {
    RelatorMuscleId.TRICEPS -> "tríceps"
    RelatorMuscleId.CHEST -> "pecho"
    RelatorMuscleId.LUMBAR -> "lumbar"
    RelatorMuscleId.SHOULDER -> "hombros"
}

private fun jointCareFor(id: RelatorMuscleId): String? = when (id) {
    RelatorMuscleId.TRICEPS -> "codos"
    RelatorMuscleId.SHOULDER -> "hombros"
    RelatorMuscleId.LUMBAR -> "lumbar"
    else -> null
}

private fun drainFor(prior: RelatorPriorExercise, muscle: RelatorMuscleId): Double {
    prior.drainByMuscle.forEach { (key, value) ->
        if (canonicalRelatorMuscle(key) == muscle) return value
    }
    return 0.0
}
