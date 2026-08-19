package com.example.kpkn.data.models

/** Relative patterns used by the treadmill-style interval authoring panel. */
enum class CardioIntervalPattern {
    PYRAMID,
    PYRAMID_INVERSE,
    LADDER,
    EVEN_1_1,
    RATIO_2_1,
    FARTLEK,
    CUSTOM,
}

data class CardioIntervalUnit(
    val durationWeight: Int,
    val intensityLevel: Int,
    val type: CardioBlockType = CardioBlockType.WORK,
)

data class CardioIntervalPatternSpec(
    val pattern: CardioIntervalPattern,
    val label: String,
    val description: String,
    val units: List<CardioIntervalUnit>,
)

object CardioIntervalPrograms {
    private fun work(level: Int, weight: Int = 1) = CardioIntervalUnit(weight, level, CardioBlockType.WORK)
    private fun recover(level: Int = 3, weight: Int = 1) = CardioIntervalUnit(weight, level, CardioBlockType.RECOVER)

    val specs: List<CardioIntervalPatternSpec> = listOf(
        CardioIntervalPatternSpec(
            CardioIntervalPattern.PYRAMID,
            "Pirámide",
            "Sube 1→5 y vuelve a bajar.",
            (1..5).map(::work) + (4 downTo 1).map(::work),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.PYRAMID_INVERSE,
            "Pirámide inversa",
            "Pico al inicio y descarga progresiva.",
            (5 downTo 1).map(::work) + (2..5).map(::work),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.LADDER,
            "Escalera",
            "Sube y mantiene el nivel alto.",
            listOf(work(2, 2), work(3, 3), work(4, 4), work(5, 5), work(5, 5)),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.EVEN_1_1,
            "1:1 constante",
            "Trabajo y recuperación iguales.",
            listOf(work(7), recover()),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.RATIO_2_1,
            "2:1",
            "Dos partes de trabajo por una de recuperación.",
            listOf(work(8, 2), recover()),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.FARTLEK,
            "Fartlek",
            "Libre estructurado con cambios de ritmo.",
            listOf(work(5, 2), recover(3), work(8), recover(4), work(6, 2), work(9)),
        ),
        CardioIntervalPatternSpec(
            CardioIntervalPattern.CUSTOM,
            "Personalizado",
            "Bloques manuales editables.",
            listOf(work(6)),
        ),
    )

    fun spec(pattern: CardioIntervalPattern): CardioIntervalPatternSpec =
        specs.first { it.pattern == pattern }
}
