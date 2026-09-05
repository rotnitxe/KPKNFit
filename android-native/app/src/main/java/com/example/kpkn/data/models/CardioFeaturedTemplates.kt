package com.example.kpkn.data.models

data class CardioFeaturedTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val hiitId: String? = null,
    val steadyMinutes: Int? = null,
    val steadyRpe: Int? = null,
)

object CardioFeaturedTemplates {
    val all: List<CardioFeaturedTemplate> = listOf(
        CardioFeaturedTemplate("hiit_tabata_20_10", "Tabata", "20/10 ×8 · 12 min", hiitId = "hiit_tabata_20_10"),
        CardioFeaturedTemplate("hiit_30_30", "30/30", "30 s / 30 s ×10", hiitId = "hiit_30_30"),
        CardioFeaturedTemplate("hiit_one_on_one_off", "1'/1'", "1 min on / off ×8", hiitId = "hiit_one_on_one_off"),
        CardioFeaturedTemplate("hiit_micro_sit_alactic", "Micro-SIT", "10 s / 60 s ×8", hiitId = "hiit_micro_sit_alactic"),
        CardioFeaturedTemplate("hiit_wingate_power", "Wingate", "30 s / 4 min ×4", hiitId = "hiit_wingate_power"),
        CardioFeaturedTemplate("hiit_pyramid_1_2_3", "Pirámide", "1-2-3-2-1 min", hiitId = "hiit_pyramid_1_2_3"),
        CardioFeaturedTemplate("steady_z2_30", "Z2 30 min", "Continuo RPE 5", steadyMinutes = 30, steadyRpe = 5),
    )

    fun apply(id: String, current: CardioDetails): CardioDetails {
        val featured = all.firstOrNull { it.id == id } ?: return current
        featured.hiitId?.let { templateId ->
            val template = CardioHiitTemplates.findById(templateId) ?: return current
            val materialized = template.toDetails(current.type, current.intensity)
            val inferred = com.example.kpkn.domain.cardio.CardioRepeatGrammar.inferUniform(materialized.intervalBlocks)
            val workDurations = template.blocks.filter { it.type == CardioBlockType.WORK }.map { it.durationSeconds }.distinct()
            val applied = if (inferred != null && workDurations.size <= 1) {
                com.example.kpkn.domain.cardio.CardioHiitProgramBuilder.buildDetails(template.toConfig(), current.type, current)
            } else {
                materialized.copy(hiit = null)
            }
            return applied.copy(
                requiresGps = current.requiresGps,
                supportsDistance = current.supportsDistance,
                targetPaceSecondsPerKm = current.targetPaceSecondsPerKm,
                targetHrPercent = current.targetHrPercent,
            )
        }
        val minutes = featured.steadyMinutes ?: return current
        val rpe = featured.steadyRpe ?: 5
        return current.copy(
            hiit = null,
            intervalBlocks = emptyList(),
            intervalRounds = 1,
            targetDurationSeconds = minutes * 60,
            intensityLevel = rpe,
            intensity = CardioIntensity.fromLevel(rpe),
        )
    }
}
