package com.example.kpkn.data.protocols

import kotlinx.serialization.Serializable

@Serializable
data class Protocol(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val author: String,
    val tags: List<String> = emptyList(),
    val sessionCategories: List<String> = emptyList(),
    val blocks: List<ProtocolBlock> = emptyList(),
    val defaultSplit: String? = null,
)

@Serializable
data class ProtocolBlock(
    val name: String,
    val weeks: Int,
    val goal: String,
    val intensityMin: Int = 50,
    val intensityMax: Int = 100,
    val volumeModifier: Double? = null,
) {
    val intensityRange get() = IntRange(intensityMin, intensityMax)
}

val PROTOCOL_LIBRARY: List<Protocol> = listOf(
    Protocol(
        id = "gzcl-base",
        name = "GZCL Method",
        emoji = "\uD83C\uDFD7\uFE0F",
        description = "Sistema de 3 tiers (T1, T2, T3) con progresión por volumen e intensidad. Ideal para intermedios.",
        author = "Cody Lefever",
        tags = listOf("powerlifting", "powerbuilding", "intermedio"),
        sessionCategories = listOf("Tier 1 (Comp)", "Tier 2 (Supl)", "Tier 3 (Acc)"),
        blocks = listOf(
            ProtocolBlock("Acumulación", 4, "Acumulación", 65, 80, 1.2),
            ProtocolBlock("Intensificación", 3, "Intensificación", 80, 90, 0.8),
            ProtocolBlock("Pico", 2, "Realización", 90, 100, 0.5),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "upper-lower",
    ),
    Protocol(
        id = "531-base",
        name = "5/3/1 Wendler",
        emoji = "5\uFE0F\u20E3",
        description = "Progresión mensual simple: 5+, 3+, 1+, descarga. Comprobado y sostenible a largo plazo.",
        author = "Jim Wendler",
        tags = listOf("powerlifting", "principiante", "intermedio"),
        sessionCategories = listOf("Movimiento principal", "Suplementario (BBB/FSL)", "Asistencia"),
        blocks = listOf(
            ProtocolBlock("Semana 5s", 1, "Acumulación", 65, 85),
            ProtocolBlock("Semana 3s", 1, "Intensificación", 70, 90),
            ProtocolBlock("Semana 1s", 1, "Realización", 75, 95),
            ProtocolBlock("Descarga", 1, "Descarga", 40, 60),
        ),
        defaultSplit = "4-day",
    ),
    Protocol(
        id = "juggernaut-base",
        name = "Juggernaut Method",
        emoji = "\uD83E\uDD81",
        description = "Ondulación por bloques con fases de 10s, 8s, 5s y 3s. Combina volumen con fuerza máxima.",
        author = "Chad Wesley Smith",
        tags = listOf("powerlifting", "powerbuilding", "avanzado"),
        sessionCategories = listOf("Movimiento Juggernaut", "Suplementario", "Accesorios"),
        blocks = listOf(
            ProtocolBlock("Fase 10s", 4, "Acumulación", 60, 75, 1.4),
            ProtocolBlock("Fase 8s", 4, "Acumulación", 65, 80, 1.2),
            ProtocolBlock("Fase 5s", 4, "Intensificación", 75, 87, 0.9),
            ProtocolBlock("Fase 3s", 4, "Realización", 85, 95, 0.6),
        ),
    ),
    Protocol(
        id = "westside-base",
        name = "Westside Conjugate",
        emoji = "\u26A1",
        description = "Sistema conjugado: día de esfuerzo máximo + día dinámico. Para atletas avanzados.",
        author = "Louie Simmons",
        tags = listOf("powerlifting", "avanzado"),
        sessionCategories = listOf("Max Effort", "Dynamic Effort", "Repetition"),
        blocks = listOf(
            ProtocolBlock("Rotación ME/DE", 3, "Custom", 50, 100),
            ProtocolBlock("Descarga", 1, "Descarga", 40, 60),
        ),
        defaultSplit = "4-day",
    ),
    Protocol(
        id = "rts-base",
        name = "RTS / Emerging Strategies",
        emoji = "\uD83D\uDCCA",
        description = "Programación autoregulada basada en RPE. Ajuste de volumen e intensidad basado en fatiga.",
        author = "Mike Tuchscherer",
        tags = listOf("powerlifting", "avanzado"),
        sessionCategories = listOf("Competitivo", "Suplementario", "Desarrollo General"),
        blocks = listOf(
            ProtocolBlock("Desarrollo", 4, "Acumulación", 70, 82),
            ProtocolBlock("Pivote", 2, "Intensificación", 82, 92),
            ProtocolBlock("Pico", 2, "Realización", 90, 100),
        ),
    ),
)
