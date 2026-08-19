package com.example.kpkn.data.models

import java.util.UUID

/**
 * Catálogo estático de plantillas HIIT predefinidas para cardio.
 * Patrón análogo a SESSION_TEMPLATES_SYSTEM: compilado, read-only, actualizable por release.
 * Cero impacto Room. Las plantillas de usuario se guardan como sesiones normales con intervalBlocks.
 */
data class HiitTemplate(
    val id: String,
    val name: String,
    val description: String,
    val level: String,
    val applicableTypes: Set<CardioType>? = null,
    val blocks: List<CardioIntervalBlock>,
    val rounds: Int = 1,
    val warmupSeconds: Int = 0,
    val cooldownSeconds: Int = 0,
) {
    /** Builds a ready-to-use CardioDetails for [type] from this template. */
    fun toDetails(type: CardioType, intensity: CardioIntensity = CardioIntensity.MEDIA): CardioDetails {
        val core = blocks.map { it.copy(id = UUID.randomUUID().toString()) }
        val allBlocks = buildList {
            if (warmupSeconds > 0) add(
                CardioIntervalBlock(
                    id = UUID.randomUUID().toString(),
                    type = CardioBlockType.WARMUP,
                    durationSeconds = warmupSeconds,
                    speedKmh = warmupSpeedFor(type),
                ),
            )
            repeat(rounds.coerceIn(1, 99)) {
                core.forEach { b -> add(b.copy(id = UUID.randomUUID().toString())) }
            }
            if (cooldownSeconds > 0) add(
                CardioIntervalBlock(
                    id = UUID.randomUUID().toString(),
                    type = CardioBlockType.COOLDOWN,
                    durationSeconds = cooldownSeconds,
                    speedKmh = cooldownSpeedFor(type),
                ),
            )
        }
        return CardioDetails(
            type = type,
            intensity = intensity,
            targetDurationSeconds = allBlocks.sumOf { it.durationSeconds },
            intervalBlocks = allBlocks,
            intervalRounds = 1,
        )
    }

    private fun warmupSpeedFor(type: CardioType): Double? = when (type) {
        CardioType.TREADMILL -> 6.0
        CardioType.RUN_OUTDOOR -> 6.0
        CardioType.WALK -> 4.5
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> 15.0
        else -> null
    }

    private fun cooldownSpeedFor(type: CardioType): Double? = when (type) {
        CardioType.TREADMILL -> 5.0
        CardioType.RUN_OUTDOOR -> 5.5
        CardioType.WALK -> 4.0
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> 12.0
        else -> null
    }
}

object CardioHiitTemplates {
    val all: List<HiitTemplate> = listOf(
        // 1. Tabata 20/10 ×8 (4 min HIIT) + warmup/cooldown
        HiitTemplate(
            id = "hiit_tabata_20_10",
            name = "Tabata 20/10",
            description = "20 s a tope + 10 s pausa ×8. 4 min de HIIT puro.",
            level = "Avanzado",
            rounds = 8,
            warmupSeconds = 5 * 60,
            cooldownSeconds = 3 * 60,
            blocks = listOf(
                CardioIntervalBlock(id = "tabata_work", type = CardioBlockType.WORK, durationSeconds = 20, speedKmh = 13.0, intensityLevel = 10),
                CardioIntervalBlock(id = "tabata_rec", type = CardioBlockType.RECOVER, durationSeconds = 10, speedKmh = 5.0, intensityLevel = 3),
            ),
        ),
        // 2. 30/30 clásico ×10
        HiitTemplate(
            id = "hiit_30_30",
            name = "30/30 clásico",
            description = "30 s fuerte + 30 s suave ×10. Versátil para cualquier máquina.",
            level = "Intermedio",
            rounds = 10,
            warmupSeconds = 5 * 60,
            cooldownSeconds = 4 * 60,
            blocks = listOf(
                CardioIntervalBlock(id = "3030_work", type = CardioBlockType.WORK, durationSeconds = 30, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "3030_rec", type = CardioBlockType.RECOVER, durationSeconds = 30, speedKmh = 6.0, intensityLevel = 4),
            ),
        ),
        // 3. Pirámide 1-2-3-2-1 con recuperación igual
        HiitTemplate(
            id = "hiit_pyramid_1_2_3",
            name = "Pirámide 1-2-3-2-1",
            description = "Escalera 1/2/3/2/1 min de trabajo con recuperación igual. Potencia aeróbica.",
            level = "Intermedio",
            rounds = 1,
            warmupSeconds = 5 * 60,
            cooldownSeconds = 3 * 60,
            blocks = listOf(
                CardioIntervalBlock(id = "pyr_w1", type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 10.0, intensityLevel = 7),
                CardioIntervalBlock(id = "pyr_r1", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.5, intensityLevel = 4),
                CardioIntervalBlock(id = "pyr_w2", type = CardioBlockType.WORK, durationSeconds = 120, speedKmh = 10.5, intensityLevel = 8),
                CardioIntervalBlock(id = "pyr_r2", type = CardioBlockType.RECOVER, durationSeconds = 120, speedKmh = 5.5, intensityLevel = 4),
                CardioIntervalBlock(id = "pyr_w3", type = CardioBlockType.WORK, durationSeconds = 180, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "pyr_r3", type = CardioBlockType.RECOVER, durationSeconds = 180, speedKmh = 5.5, intensityLevel = 4),
                CardioIntervalBlock(id = "pyr_w4", type = CardioBlockType.WORK, durationSeconds = 120, speedKmh = 10.5, intensityLevel = 8),
                CardioIntervalBlock(id = "pyr_r4", type = CardioBlockType.RECOVER, durationSeconds = 120, speedKmh = 5.5, intensityLevel = 4),
                CardioIntervalBlock(id = "pyr_w5", type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 10.0, intensityLevel = 7),
            ),
        ),
        // 4. Fartlek trotadora (ejemplo del usuario)
        HiitTemplate(
            id = "hiit_fartlek_treadmill",
            name = "Fartlek trotadora",
            description = "Tu ejemplo: trote base + picos de velocidad. Programable en cinta y exterior.",
            level = "Intermedio",
            rounds = 1,
            blocks = listOf(
                CardioIntervalBlock(id = "fartlek_warm", type = CardioBlockType.WARMUP, durationSeconds = 5 * 60, speedKmh = 6.0, intensityLevel = 4),
                CardioIntervalBlock(id = "fartlek_base1", type = CardioBlockType.WORK, durationSeconds = 5 * 60, speedKmh = 8.0, intensityLevel = 6),
                CardioIntervalBlock(id = "fartlek_rec1", type = CardioBlockType.RECOVER, durationSeconds = 2 * 60, speedKmh = 5.0, intensityLevel = 3),
                CardioIntervalBlock(id = "fartlek_peak1", type = CardioBlockType.WORK, durationSeconds = 3 * 60, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "fartlek_base2", type = CardioBlockType.WORK, durationSeconds = 3 * 60, speedKmh = 8.0, intensityLevel = 6),
                CardioIntervalBlock(id = "fartlek_peak2", type = CardioBlockType.WORK, durationSeconds = 2 * 60, speedKmh = 12.0, intensityLevel = 9),
                CardioIntervalBlock(id = "fartlek_cool", type = CardioBlockType.COOLDOWN, durationSeconds = 4 * 60, speedKmh = 5.0, intensityLevel = 3),
            ),
        ),
        // 5. Sprint 8 — 30 s sprint + 90 s suave ×8
        HiitTemplate(
            id = "hiit_sprint_8",
            name = "Sprint 8",
            description = "30 s sprint + 90 s trote/caminata ×8. Clásico de Phil Campbell para cinta y bici.",
            level = "Avanzado",
            rounds = 8,
            warmupSeconds = 5 * 60,
            cooldownSeconds = 3 * 60,
            blocks = listOf(
                CardioIntervalBlock(id = "sprint8_work", type = CardioBlockType.WORK, durationSeconds = 30, speedKmh = 13.5, intensityLevel = 10),
                CardioIntervalBlock(id = "sprint8_rec", type = CardioBlockType.RECOVER, durationSeconds = 90, speedKmh = 5.5, intensityLevel = 4),
            ),
        ),
        // 6. Z2 con picos — base aeróbica con sprints cortos
        HiitTemplate(
            id = "hiit_z2_peaks",
            name = "Z2 con picos",
            description = "Base aeróbica Z2 con picos de 45 s cada 4 min. Ideal para fondo sin castigo.",
            level = "Principiante",
            rounds = 1,
            blocks = listOf(
                CardioIntervalBlock(id = "z2_warm", type = CardioBlockType.WARMUP, durationSeconds = 5 * 60, speedKmh = 6.5, intensityLevel = 5),
                CardioIntervalBlock(id = "z2_base1", type = CardioBlockType.WORK, durationSeconds = 4 * 60, speedKmh = 8.5, intensityLevel = 6),
                CardioIntervalBlock(id = "z2_peak1", type = CardioBlockType.WORK, durationSeconds = 45, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "z2_base2", type = CardioBlockType.WORK, durationSeconds = 4 * 60, speedKmh = 8.5, intensityLevel = 6),
                CardioIntervalBlock(id = "z2_peak2", type = CardioBlockType.WORK, durationSeconds = 45, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "z2_base3", type = CardioBlockType.WORK, durationSeconds = 4 * 60, speedKmh = 8.5, intensityLevel = 6),
                CardioIntervalBlock(id = "z2_peak3", type = CardioBlockType.WORK, durationSeconds = 45, speedKmh = 11.0, intensityLevel = 8),
                CardioIntervalBlock(id = "z2_base4", type = CardioBlockType.WORK, durationSeconds = 4 * 60, speedKmh = 8.5, intensityLevel = 6),
                CardioIntervalBlock(id = "z2_cool", type = CardioBlockType.COOLDOWN, durationSeconds = 3 * 60, speedKmh = 5.0, intensityLevel = 3),
            ),
        ),
    )

    fun findById(id: String): HiitTemplate? = all.firstOrNull { it.id == id }

    fun forType(type: CardioType): List<HiitTemplate> =
        all.filter { it.applicableTypes == null || type in it.applicableTypes }
}
