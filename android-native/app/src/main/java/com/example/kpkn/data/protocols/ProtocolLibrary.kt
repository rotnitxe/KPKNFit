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
    /**
     * A protocol is publishable only when its day-by-day recipe and source contract
     * have been reviewed.  The old library was a useful editorial index, but its
     * generic compiler must never pretend to reproduce a named third-party plan.
     */
    val publicationStatus: ProtocolPublicationStatus = ProtocolPublicationStatus.HIDDEN_UNVERIFIED,
    val kind: ProtocolKind = ProtocolKind.FIXED_PROGRAM,
    val source: ProtocolSource = ProtocolSource(),
    /** Explicit ordered day recipes for KPKN-native protocols. */
    val dayRecipes: List<ProtocolDayRecipe> = emptyList(),
)

@Serializable
enum class ProtocolPublicationStatus {
    VERIFIED,
    KPKN_NATIVE,
    HIDDEN_UNVERIFIED,
}

@Serializable
enum class ProtocolKind {
    FIXED_PROGRAM,
    METHOD,
    AUTOREGULATED_FRAMEWORK,
    SPECIALIZATION,
    WEEKLY_SPLIT,
}

/** Attribution/recipe metadata retained with a publishable protocol definition. */
@Serializable
data class ProtocolSource(
    val definitionId: String? = null,
    val revision: String? = null,
    val primaryReference: String? = null,
    val primaryUrl: String? = null,
    val variant: String? = null,
    val version: String? = null,
    val reviewedAt: String? = null,
    val catalogRevision: String? = null,
    val approvedBy: String? = null,
    val evidenceUrl: String? = null,
)

/**
 * A publishable protocol must say what each training day actually contains.
 * Configuration IDs are resolved by [ProtocolExerciseLibrary], never inferred
 * from a localized day label.
 */
@Serializable
data class ProtocolDayRecipe(
    val dayLabel: String,
    val focus: String,
    val mainLiftConfigurationId: String,
    val accessoryExerciseConfigurationIds: List<String> = emptyList(),
    val mainRestSeconds: Int = 210,
    val accessoryRestSeconds: Int = 120,
)

val Protocol.isVisibleForApplication: Boolean
    get() = publicationStatus != ProtocolPublicationStatus.HIDDEN_UNVERIFIED

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

private val LEGACY_PROTOCOL_INDEX: List<Protocol> = listOf(
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
        defaultSplit = "ul_x4",
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
        defaultSplit = "531_bbb",
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
        defaultSplit = "pl_sbd_x3",
    ),
    Protocol(
        id = "westside-base",
        name = "Westside Conjugate",
        emoji = "\u26A1",
        description = "Sistema conjugado: días Max Effort (≈90%+) y Dynamic Effort (≈50–65% con resistencia acomodante) separados. Para atletas avanzados.",
        author = "Louie Simmons",
        tags = listOf("powerlifting", "avanzado"),
        sessionCategories = listOf("Max Effort", "Dynamic Effort", "Repetition"),
        blocks = listOf(
            ProtocolBlock("Dynamic Effort (DE)", 2, "Acumulación", 50, 65, 1.15),
            ProtocolBlock("Max Effort (ME)", 2, "Realización", 90, 100, 0.55),
            ProtocolBlock("Descarga", 1, "Descarga", 40, 60, 0.4),
        ),
        defaultSplit = "westside_conjugate",
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
        defaultSplit = "texas_method",
    ),
    Protocol(
        id = "texas-method",
        name = "Texas Method",
        emoji = "\uD83E\uDDF1",
        description = "Ondulación semanal clásica: día de volumen, día de recuperación y día de intensidad. Ideal para fuerza intermedia.",
        author = "Glenn Pendlay / Mark Rippetoe",
        tags = listOf("powerlifting", "intermedio", "fuerza"),
        sessionCategories = listOf("Volumen", "Recuperación", "Intensidad"),
        blocks = listOf(
            ProtocolBlock("Base de Volumen", 4, "Acumulación", 70, 82, 1.15),
            ProtocolBlock("Empuje de Intensidad", 3, "Intensificación", 80, 92, 0.9),
            ProtocolBlock("Pico corto", 2, "Realización", 88, 100, 0.6),
            ProtocolBlock("Descarga", 1, "Descarga", 45, 60, 0.4),
        ),
        defaultSplit = "texas_method",
    ),
    Protocol(
        id = "sheiko-4day",
        name = "Sheiko 4 Días",
        emoji = "\uD83C\uDDF7\uD83C\uDDFA",
        description = "Volumen alto y muchísima especificidad en SBD. Ideal para atletas avanzados que toleran mucha práctica técnica.",
        author = "Boris Sheiko",
        tags = listOf("powerlifting", "avanzado", "volumen"),
        sessionCategories = listOf("Competitivo principal", "Variante técnica", "Accesorios mínimos"),
        blocks = listOf(
            ProtocolBlock("Base técnica", 4, "Acumulación", 65, 78, 1.35),
            ProtocolBlock("Carga específica", 4, "Intensificación", 75, 87, 1.0),
            ProtocolBlock("Puesta a punto", 3, "Realización", 85, 96, 0.65),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 60, 0.35),
        ),
        defaultSplit = "sheiko_4day",
    ),
    Protocol(
        id = "sheiko-3day",
        name = "Sheiko Clásico 3 Días",
        emoji = "\uD83E\uDDEA",
        description = "Versión compacta del enfoque Sheiko: tres sesiones largas, altísima práctica técnica y control de fatiga.",
        author = "Boris Sheiko",
        tags = listOf("powerlifting", "intermedio", "avanzado"),
        sessionCategories = listOf("SBD principal", "Trabajo secundario", "Asistencia mínima"),
        blocks = listOf(
            ProtocolBlock("Preparación acumulativa", 4, "Acumulación", 65, 80, 1.25),
            ProtocolBlock("Intensificación técnica", 3, "Intensificación", 78, 88, 0.95),
            ProtocolBlock("Pico", 2, "Realización", 88, 97, 0.55),
            ProtocolBlock("Descarga", 1, "Descarga", 45, 60, 0.35),
        ),
        defaultSplit = "sheiko_3day",
    ),
    Protocol(
        id = "candito-6week",
        name = "Candito 6 Week",
        emoji = "\uD83D\uDCC6",
        description = "Protocolo corto y agresivo de 6 semanas con transición clara desde hipertrofia a potencia y pico.",
        author = "Jonnie Candito",
        tags = listOf("powerlifting", "intermedio", "pico"),
        sessionCategories = listOf("Principal de fuerza", "Secundario explosivo", "Accesorios dirigidos"),
        blocks = listOf(
            ProtocolBlock("Hipertrofia base", 2, "Acumulación", 62, 75, 1.2),
            ProtocolBlock("Fuerza", 2, "Intensificación", 75, 88, 0.95),
            ProtocolBlock("Potencia / Pico", 1, "Realización", 82, 95, 0.7),
            ProtocolBlock("Test / Taper", 1, "Descarga", 40, 65, 0.3),
        ),
        defaultSplit = "pl_classic_4",
    ),
    Protocol(
        id = "smolov-jr",
        name = "Smolov Jr.",
        emoji = "\uD83D\uDCA5",
        description = "Mini ciclo brutal de especialización para un levantamiento. Mucha frecuencia y toneladas de volumen concentrado.",
        author = "Sergey Smolov",
        tags = listOf("powerlifting", "avanzado", "especialización"),
        sessionCategories = listOf("Sesión volumen", "Sesión media", "Sesión pesada", "Sesión pico"),
        blocks = listOf(
            ProtocolBlock("Bloque Smolov Jr.", 3, "Intensificación", 70, 90, 1.5),
            ProtocolBlock("Descarga / Test", 1, "Realización", 50, 100, 0.35),
        ),
        defaultSplit = "smolov_base",
    ),
    Protocol(
        id = "coan-phillipi",
        name = "Coan-Phillipi Deadlift",
        emoji = "\uD83D\uDEA8",
        description = "Especialización clásica para peso muerto con una sesión principal pesada y trabajo de velocidad/volumen complementario.",
        author = "Ed Coan / Mark Philippi",
        tags = listOf("powerlifting", "intermedio", "especialización"),
        sessionCategories = listOf("Deadlift principal", "Speed pulls", "Asistencia posterior"),
        blocks = listOf(
            ProtocolBlock("Acumulación específica", 4, "Acumulación", 70, 82, 1.15),
            ProtocolBlock("Intensificación específica", 4, "Intensificación", 80, 92, 0.85),
            ProtocolBlock("Pico", 2, "Realización", 90, 100, 0.45),
        ),
        defaultSplit = "coan_split",
    ),
    Protocol(
        id = "nsuns-531",
        name = "nSuns 5/3/1 LP",
        emoji = "\u2795",
        description = "Variante de alta frecuencia del 5/3/1: press y sentadilla/peso muerto casi a diario con progresión lineal agresiva.",
        author = "nSuns (comunidad r/Fitness)",
        tags = listOf("powerlifting", "avanzado", "volumen"),
        sessionCategories = listOf("T1 Principal", "T2 Complementario", "Accesorios"),
        blocks = listOf(
            ProtocolBlock("Progresión lineal", 6, "Acumulación", 65, 90, 1.3),
            ProtocolBlock("Consolidación", 3, "Intensificación", 80, 95, 0.9),
            ProtocolBlock("Reset / Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "pl_hf_bench",
    ),
    Protocol(
        id = "sbs-hybrid",
        name = "SBS Hybrid",
        emoji = "\uD83E\uDDEC",
        description = "Enfoque basado en evidencia: bloques ondulados de hipertrofia y fuerza con autorregulación por RPE.",
        author = "Inspirado en Stronger By Science",
        tags = listOf("powerbuilding", "intermedio", "avanzado"),
        sessionCategories = listOf("Fuerza principal", "Hipertrofia dirigida", "Accesorios"),
        blocks = listOf(
            ProtocolBlock("Hipertrofia base", 4, "Acumulación", 62, 78, 1.25),
            ProtocolBlock("Fuerza-hipertrofia", 4, "Intensificación", 75, 88, 0.95),
            ProtocolBlock("Pico de fuerza", 2, "Realización", 85, 95, 0.65),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "phat_hybrid",
    ),
    Protocol(
        id = "phul-base",
        name = "PHUL",
        emoji = "\uD83C\uDFCB\uFE0F\u200D\u2640\uFE0F",
        description = "Mesociclo Power Hypertrophy Upper Lower sobre un split UL (no es un split distinto): dos días de fuerza y dos de hipertrofia por semana. Complementa `ul_x4`.",
        author = "Brandon Campbell",
        tags = listOf("powerbuilding", "intermedio", "split-like"),
        sessionCategories = listOf("Fuerza (Power)", "Hipertrofia", "Accesorios"),
        blocks = listOf(
            ProtocolBlock("Base de fuerza", 5, "Acumulación", 70, 85, 1.1),
            ProtocolBlock("Hipertrofia dirigida", 5, "Intensificación", 65, 80, 1.2),
            ProtocolBlock("Pico combinado", 3, "Realización", 80, 92, 0.75),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "ul_x4",
    ),
    Protocol(
        id = "phat-base",
        name = "PHAT",
        emoji = "\uD83D\uDC18",
        description = "Mesociclo Power Hypertrophy Adaptive Training (split-like): fuerza + hipertrofia por grupo. Complementa splits PPL/Arnold; no duplica el catálogo de splits.",
        author = "Layne Norton",
        tags = listOf("powerbuilding", "avanzado", "split-like"),
        sessionCategories = listOf("Power", "Hipertrofia", "Accesorios"),
        blocks = listOf(
            ProtocolBlock("Adaptación", 4, "Acumulación", 65, 82, 1.3),
            ProtocolBlock("Fuerza dirigida", 4, "Intensificación", 78, 90, 0.9),
            ProtocolBlock("Pico", 2, "Realización", 85, 95, 0.6),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "ppl_arnold",
    ),
    Protocol(
        id = "ppl-hypertrophy",
        name = "PPL Hipertrofia",
        emoji = "\uD83C\uDFAF",
        description = "Push/Pull/Legs clásico de culturismo con doble frecuencia semanal y foco total en volumen muscular (densidad/metabolitos, no peaking de %RM).",
        author = "Clásico de culturismo",
        tags = listOf("culturismo", "hipertrofia", "intermedio", "avanzado"),
        sessionCategories = listOf("Compuesto principal", "Volumen secundario", "Aislamiento"),
        blocks = listOf(
            ProtocolBlock("Volumen base", 5, "Acumulación", 60, 75, 1.3),
            ProtocolBlock("Sobrecarga progresiva", 4, "Intensificación", 70, 82, 1.0),
            // After overload, density/metabolite work is an explicit
            // specificity phase; labelling it a second accumulation block
            // would make the executable phase order regress (1 → 0).
            ProtocolBlock("Densidad / Metabolitos", 3, "Especificidad", 65, 78, 0.85),
            ProtocolBlock("Descarga", 1, "Descarga", 50, 65, 0.4),
        ),
        defaultSplit = "ppl_x6",
    ),
)

private val KPKN_NATIVE_SBD = Protocol(
    id = "kpkn-native-sbd-4",
    name = "KPKN SBD · 4 días",
    emoji = "🏋️",
    description = "Protocolo KPKN nativo de cuatro días: especificidad SBD, volumen base, intensificación, pico y taper con recetas explícitas.",
    author = "KPKN Fit",
    tags = listOf("powerlifting", "sbd", "kpkn-native", "intermedio"),
    sessionCategories = listOf("Principal de competición", "Accesorios específicos"),
    blocks = listOf(
        ProtocolBlock("Base", 4, "Acumulación", 65, 75, 1.20),
        ProtocolBlock("Intensificación", 4, "Intensificación", 75, 87, 0.95),
        ProtocolBlock("Peak", 2, "Peak", 85, 95, 0.65),
        ProtocolBlock("Taper", 1, "Taper", 70, 90, 0.35),
    ),
    defaultSplit = "pl_classic_4",
    publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
    kind = ProtocolKind.FIXED_PROGRAM,
    source = ProtocolSource(
        definitionId = "kpkn-native-sbd-4",
        revision = "2026-08-21",
        primaryReference = "KPKN Native SBD v4",
        primaryUrl = "https://kpkn.fit/protocols/kpkn-native-sbd-4",
        variant = "4-day SBD",
        version = "v4",
        reviewedAt = "2026-08-21",
        catalogRevision = "v4-approved-2026-08-21-a",
        approvedBy = "KPKN Editorial",
    ),
    dayRecipes = listOf(
        ProtocolDayRecipe(
            dayLabel = "Sentadilla/Banca",
            focus = "Sentadilla de competición",
            mainLiftConfigurationId = "low_bar_back_squat__barbell",
            accessoryExerciseConfigurationIds = listOf("bench_press__barbell", "chest_supported_row__dumbbells__wide"),
        ),
        ProtocolDayRecipe(
            dayLabel = "Peso Muerto",
            focus = "Peso muerto de competición",
            mainLiftConfigurationId = "conventional_deadlift__bilateral__barbell",
            accessoryExerciseConfigurationIds = listOf("bench_press__barbell", "romanian_deadlift__bilateral__barbell"),
        ),
        ProtocolDayRecipe(
            dayLabel = "Banca Volumen",
            focus = "Press banca de competición",
            mainLiftConfigurationId = "bench_press__barbell",
            accessoryExerciseConfigurationIds = listOf("triceps_pushdown__bilateral__cable", "chest_supported_row__dumbbells__wide"),
        ),
        ProtocolDayRecipe(
            dayLabel = "Sentadilla/Peso Muerto",
            focus = "Técnica SBD",
            mainLiftConfigurationId = "low_bar_back_squat__barbell",
            accessoryExerciseConfigurationIds = listOf("conventional_deadlift__bilateral__barbell", "bench_press__barbell"),
        ),
    ),
)

/**
 * Historical named plans are deliberately retained as an internal audit index, not
 * an installable product catalogue.  Until each one has a source-backed,
 * day-by-day prescription, exposing our generic synthesis under its name would be
 * misleading to a powerlifter.  A future verified definition must opt in by
 * declaring [ProtocolPublicationStatus.VERIFIED] in its own source file.
 */
val PROTOCOL_LIBRARY: List<Protocol> = listOf(KPKN_NATIVE_SBD) + LEGACY_PROTOCOL_INDEX.map { legacy ->
    legacy.copy(publicationStatus = ProtocolPublicationStatus.HIDDEN_UNVERIFIED)
}
