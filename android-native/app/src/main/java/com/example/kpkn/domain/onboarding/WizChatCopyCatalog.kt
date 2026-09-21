package com.example.kpkn.domain.onboarding

object WizChatCopyCatalog {
    const val SCRIPT_VERSION = 1

    private val acknowledgements = mapOf(
        WizChatStage.PROFILE to listOf("Listo, usaré ese dato.", "Perfecto, queda registrado.", "Gracias; seguimos con eso."),
        WizChatStage.TRAINING to listOf("Bien, lo tendré en cuenta.", "Eso define mejor tu semana.", "Listo, revisemos qué encaja."),
        WizChatStage.NUTRITION to listOf("Gracias. Usaré esa referencia.", "Bien, ya podemos calcularlo.", "Queda claro; seguimos con eso."),
        WizChatStage.RINGS to listOf("Entendido. Lo tomaré como referencia inicial.", "Gracias por situarlo.", "Listo; veamos el resultado."),
        WizChatStage.REVIEW to listOf("La configuración está lista para revisar."),
    )

    fun acknowledgement(stage: WizChatStage, variantId: String?): String {
        val options = acknowledgements[stage].orEmpty()
        if (options.isEmpty()) return "Listo, queda registrado."
        val index = variantId?.takeLast(2)?.toIntOrNull(16)?.mod(options.size) ?: 0
        return options[index]
    }

    fun progressLabel(stage: WizChatStage): String = when (stage) {
        WizChatStage.PROFILE -> "Tus datos"
        WizChatStage.TRAINING -> "Tu entrenamiento"
        WizChatStage.NUTRITION -> "Tu alimentación"
        WizChatStage.RINGS -> "Tu punto de partida — RINGS"
        WizChatStage.REVIEW -> "Resumen y activación"
    }
}
