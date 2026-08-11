package com.example.kpkn.domain.nutrition

/**
 * Último nivel de seguridad del pipeline de análisis por descripción (CRI-ANALYSIS).
 *
 * Cuando el análisis automático completo (parser + dataset + resolver) falla, se usa este
 * splitter puro para producir fragmentos de comida que el usuario pueda revisar y guardar.
 * No depende de Room, dataset ni de Android: es puro dominio y por eso es testeable en JVM.
 */
object LastResortSplitter {
    private val SPLIT = Regex("""[;,\n]|\s\+|\s+(?:y|e|con)\s+""", RegexOption.IGNORE_CASE)

    /**
     * Separa la descripción por conectores de lista (",", ";", "+", " con ", " y ", " e ").
     * Si no hay separadores, devuelve el texto completo como un único fragmento (seguro).
     * Fragmentos de menos de 2 caracteres se descartan. Nunca lanza.
     */
    fun split(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.trim().split(SPLIT)
            .map { it.trim().removePrefix("e ").trim() }
            .filter { it.length >= 2 }
        return parts.ifEmpty { listOf(raw.trim()) }
    }
}
