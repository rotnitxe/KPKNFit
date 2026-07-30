package com.example.kpkn.services.workout

/**
 * Formas compatibles con el vocabulario small-es que también deben ser aceptadas
 * por el parser. Evita que la gramática invente frases semánticamente huérfanas.
 */
internal object WorkoutVoiceGrammarLexicon {
    val wordExpansions: Map<String, List<String>> = mapOf(
        "rpe" to listOf("esfuerzo", "intensidad"),
        "rir" to listOf("reservas", "ritmo"),
        "rm" to listOf("maximo", "máximo"),
        "rep" to listOf("repeticion", "repetición", "repeticiones"),
        "reps" to listOf("repeticiones"),
        "cns" to listOf("nerviosa", "neural"),
        "kg" to listOf("kilo", "kilos"),
        "seg" to listOf("segundo", "segundos"),
        "min" to listOf("minuto", "minutos"),
        "izq" to listOf("izquierda", "izquierdo"),
        "der" to listOf("derecha", "derecho"),
        "timer" to listOf("temporizador", "tiempo"),
        "cronometro" to listOf("temporizador", "tiempo"),
        "cronómetro" to listOf("temporizador", "tiempo"),
        "microfono" to listOf("micro", "voz"),
        "micrófono" to listOf("micro", "voz"),
        "anade" to listOf("agrega", "suma", "añade"),
        "añade" to listOf("agrega", "suma"),
        "anadir" to listOf("agregar", "sumar", "añadir"),
        "añadir" to listOf("agregar", "sumar"),
        "dinamico" to listOf("adaptativo"),
        "dinámico" to listOf("adaptativo"),
        "cambialo" to listOf("cambia", "cambiar"),
        "cámbialo" to listOf("cambia", "cambiar"),
        "abduccion" to listOf("separar", "abrir"),
        "abducción" to listOf("separar", "abrir"),
        "aduccion" to listOf("juntar", "cerrar"),
        "aducción" to listOf("juntar", "cerrar"),
    )

    val restStatusAliases = setOf("temporizador")
    val turnOffVoiceAliases = setOf("apagar micro")
    val addSetAliases = setOf("agrega una serie")
    val skipRestAliases = setOf("saltar temporizador", "omitir temporizador")
    val adaptiveRestAliases = setOf("descanso adaptativo")
    val editLastSetAliases = setOf("cambia", "cambiar")

    fun expandWord(word: String): List<String> = wordExpansions[word].orEmpty()
}
