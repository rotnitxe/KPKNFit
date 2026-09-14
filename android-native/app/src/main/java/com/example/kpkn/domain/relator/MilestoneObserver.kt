package com.example.kpkn.domain.relator

object MilestoneObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val m = context.milestone
        if (!m.prJustNow && !m.sessionVolumeRecord && m.bestTagName == null) return emptyList()
        val rm = m.estimatedRmKg?.let { formatRelatorKg(it) }
        val lines = buildList {
            if (m.prJustNow && m.isStar) {
                add("PR estrella en {ex}${rm?.let { " (~$it kg e1RM)" }.orEmpty()}.")
                add("Marca de campeonato en {ex}. Guárdala.")
            } else if (m.prJustNow) {
                add("PR homologado en {ex}${rm?.let { " (~$it kg)" }.orEmpty()}.")
                add("Nuevo techo de {ex}. Eso cuenta.")
            }
            m.goal1RmKg?.let { goal ->
                rm?.let { add("e1RM $it kg vs objetivo ${formatRelatorKg(goal)} kg en {ex}.") }
            }
            if (m.sessionVolumeRecord) add("Récord de volumen de la sesión. {ex} lo empujó.")
            m.bestTagName?.let { add("Mejor curva con la etiqueta $it en {ex}.") }
        }
        val actions = if (m.prJustNow) {
            listOf(
                RelatorActionSpec(
                    kind = RelatorActionKind.CAPTURE_MEDIA,
                    label = "Foto/vídeo PR",
                    span = "Foto/vídeo PR",
                    exerciseId = context.exerciseId,
                    setIndex = context.setIndex,
                ),
            )
        } else {
            emptyList()
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.MILESTONE,
                priority = 86,
                relevance = 1.0,
                lines = padVariants(lines),
                fingerprintBase = "MILESTONE:${context.progress.completedWorkingSets}",
                actions = actions,
                isReaction = m.prJustNow,
                openers = listOf("Dato", "Hoy"),
            ),
        )
    }
}
