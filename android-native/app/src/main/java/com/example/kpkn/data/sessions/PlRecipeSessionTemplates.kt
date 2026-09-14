package com.example.kpkn.data.sessions

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.programs.DaySlotTemplate
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.splits.Difficulty

/**
 * Plantillas PL derivadas de [DayArchetypes] y recetas de día (competición,
 * técnica, velocidad, descarga, openers). Van **después** del compilador V3
 * para poder incluir configuraciones que V3 prohíbe (`hex_bar`).
 */
internal fun plRecipeSessionTemplates(): List<SessionTemplate> = listOf(
    fromDayRecipe(
        id = "sys-v3-recipe-pl-squat",
        title = "Powerlifting · Sentadilla de competición",
        description = "Arquetipo PL sentadilla: T1 competición, bisagra ligera, banca técnica, GHR y core.",
        day = DayArchetypes.plSquat(80.0),
        splitIds = listOf("pl_classic_4", "texas_method", "pl_sbd_x3", "madcow_5x5", "smolov_base", "531_bbb", "pl_hf_bench", "cube_method", "westside_conjugate", "nsuns_4day"),
        labels = listOf(
            "Sentadilla pesada",
            "Día Volumen (5x5)",
            "Día Intensidad",
            "Volumen (5x5)",
            "Intensidad (1x3/1x5)",
            "Sesión 1 (4x9)",
            "Sesión 2 (5x7)",
            "Sesión 3 (7x5)",
            "Sesión 4 (10x3)",
            "Sentadilla/Pierna",
            "Sentadilla/Sumo",
            "Sentadilla/Accesorios",
            "ME Lower",
            "Día Pesado",
        ),
        primary = "Cuádriceps",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-bench-heavy",
        title = "Powerlifting · Banca pesada",
        description = "Arquetipo PL banca: competición, variante de press, remo, tirón vertical y tríceps.",
        day = DayArchetypes.plBenchHeavy(80.0),
        splitIds = listOf("pl_classic_4", "nsuns_4day", "531_bbb", "westside_conjugate", "cube_method", "pl_hf_bench"),
        labels = listOf("Banca pesada", "ME Upper", "Press Banca/Pecho", "Banca/OHP", "Banca/Cerrado"),
        primary = "Pectorales",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-deadlift",
        title = "Powerlifting · Peso muerto de competición",
        description = "Arquetipo PL peso muerto: competición, sentadilla ligera, remo, cadena posterior, core y agarre.",
        day = DayArchetypes.plDeadlift(80.0),
        splitIds = listOf("pl_classic_4", "korte_3x3", "sheiko_3day", "531_bbb", "nsuns_4day", "pl_hf_bench", "westside_conjugate"),
        labels = listOf("Peso muerto", "Peso Muerto", "Peso Muerto/Espalda", "Peso muerto/Frontal", "Peso Muerto/Banca", "Variante DL/Banca"),
        primary = "Isquiosurales",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-bench-volume",
        title = "Powerlifting · Banca volumen / técnica",
        description = "Arquetipo PL banca de volumen con pausa, hombro, remo con apoyo y brazos.",
        day = DayArchetypes.plBenchVolume(70.0),
        splitIds = listOf("pl_classic_4", "nsuns_4day", "cube_method", "531_bbb"),
        labels = listOf("Banca volumen", "Día Repeticiones", "Press Militar/Hombro"),
        primary = "Pectorales",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-speed",
        title = "Powerlifting · Velocidad sentadilla",
        description = "Día dinámico: sentadilla a cajón en olas cortas, tirones rápidos y accesorios posteriores.",
        day = speedSquatDay(),
        splitIds = listOf("westside_conjugate", "cube_method"),
        labels = listOf("DE Lower", "Día Explosivo"),
        primary = "Cuádriceps",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-speed-upper",
        title = "Powerlifting · Velocidad banca",
        description = "Día dinámico de empuje: banca con cadenas/olas cortas y cobertura de tirón.",
        day = speedBenchDay(),
        splitIds = listOf("westside_conjugate"),
        labels = listOf("DE Upper"),
        primary = "Pectorales",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-technique",
        title = "Powerlifting · Técnica con pausa",
        description = "Día técnico: banca con pausa, sentadilla ligera y cobertura de tirón.",
        day = DayArchetypes.plBenchVolume(65.0, label = "Técnica con pausa"),
        splitIds = listOf("pl_classic_4", "sheiko_3day", "korte_3x3"),
        labels = listOf("Técnica", "SBD (Técnica)"),
        primary = "Pectorales",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-deload",
        title = "Powerlifting · Descarga",
        description = "Descarga SBD ligera con accesorios de cobertura y core.",
        day = deloadDay(),
        splitIds = listOf("pl_classic_4", "pl_sbd_x3", "531_bbb", "texas_method", "madcow_5x5"),
        labels = listOf("Descarga", "Día Recuperación", "Recuperación (Light)"),
        primary = "Cuádriceps",
        difficulty = Difficulty.INTERMEDIO,
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-hypertrophy",
        title = "Powerlifting · Accesorios de hipertrofia",
        description = "Día de cobertura muscular sugerido por KPKN para el cubo y similares.",
        day = hypertrophyAccessoriesDay(),
        splitIds = listOf("cube_method"),
        labels = listOf("Accesorios Hipertrofia"),
        primary = "Pectorales",
        difficulty = Difficulty.AVANZADO,
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-openers",
        title = "Powerlifting · Openers",
        description = "Singles de opener ~90-92 % TM más cobertura mínima de tirón y core.",
        day = openersDay(),
        splitIds = listOf("pl_classic_4", "pl_sbd_x3"),
        labels = listOf("Openers"),
        primary = "Cuádriceps",
    ),
    fromDayRecipe(
        id = "sys-v3-recipe-pl-independent",
        title = "Powerlifting · Sesión independiente",
        description = "Día de sentadilla de competición sin split asociado, para aplicar como plantilla suelta.",
        day = DayArchetypes.plSquat(75.0),
        splitIds = emptyList(),
        labels = emptyList(),
        primary = "Cuádriceps",
    ),
)

private fun speedSquatDay(): DayRecipe = day(
    label = "Velocidad sentadilla",
    archetype = DaySlotTemplate.PL_SQUAT,
    weekday = 4,
    priority = SlotPriority.SPEED,
    slots = listOf(
        slot(
            "box", SlotRole.SPEED, CatalogIds.SQ_BOX,
            repeatPercentSets(8, 2, 60.0, 45),
            restSeconds = 45, liftSlot = LiftSlot.SQUAT, technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
        ),
        slot(
            "speed-dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL,
            repeatPercentSets(4, 2, 55.0, 90),
            restSeconds = 120, liftSlot = LiftSlot.DEADLIFT, technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
        ),
        slot("ghr", SlotRole.T3_ACCESSORY, CatalogIds.GHR, rpeSets(2, 8, 7.0), restSeconds = 90),
        slot("face", SlotRole.T3_ACCESSORY, CatalogIds.FACE, rpeSets(2, 15, 7.0), restSeconds = 60),
        slot("pallof", SlotRole.T3_ACCESSORY, CatalogIds.PALLOF, rpeSets(2, 10, 7.0), restSeconds = 60, isUnilateral = true),
    ),
)

private fun speedBenchDay(): DayRecipe = day(
    label = "Velocidad banca",
    archetype = DaySlotTemplate.PL_BENCH_HEAVY,
    weekday = 5,
    priority = SlotPriority.SPEED,
    slots = listOf(
        slot(
            "speed-bp", SlotRole.SPEED, CatalogIds.BP,
            repeatPercentSets(6, 3, 55.0, 45),
            restSeconds = 45, liftSlot = LiftSlot.BENCH, technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
        ),
        slot(
            "speed-ohp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.OHP,
            repeatPercentSets(3, 3, 50.0, 90),
            restSeconds = 120, liftSlot = LiftSlot.OVERHEAD, technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
        ),
        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.CSR, rpeSets(3, 10, 7.0), restSeconds = 90),
        slot("face", SlotRole.T3_ACCESSORY, CatalogIds.FACE, rpeSets(2, 15, 7.0), restSeconds = 60),
        slot("tri", SlotRole.T3_ACCESSORY, CatalogIds.OH_TRI, rpeSets(2, 12, 7.0), restSeconds = 60),
    ),
)

private fun deloadDay(): DayRecipe = day(
    label = "Descarga",
    archetype = DaySlotTemplate.PL_SQUAT,
    weekday = 1,
    slots = listOf(
        slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_HIGH, rpeSets(3, 5, 6.0), restSeconds = 180, liftSlot = LiftSlot.SQUAT, isCompetitionLift = true),
        slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, rpeSets(3, 6, 6.0), restSeconds = 150, liftSlot = LiftSlot.BENCH),
        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.CSR, rpeSets(3, 10, 7.0), restSeconds = 90),
        slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL_H, rpeSets(2, 12, 7.0), restSeconds = 60),
        slot("pallof", SlotRole.T3_ACCESSORY, CatalogIds.PALLOF, rpeSets(2, 10, 6.5), restSeconds = 60),
    ),
)

private fun openersDay(): DayRecipe = day(
    label = "Openers",
    archetype = DaySlotTemplate.PL_SQUAT,
    weekday = 1,
    slots = listOf(
        slot(
            "sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW,
            repeatPercentSets(2, 1, 91.0, 240),
            restSeconds = 240, liftSlot = LiftSlot.SQUAT, isCompetitionLift = true,
        ),
        slot(
            "bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP,
            repeatPercentSets(2, 1, 80.0, 180),
            restSeconds = 180, liftSlot = LiftSlot.BENCH, isCompetitionLift = true,
        ),
        slot(
            "dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL,
            repeatPercentSets(1, 1, 70.0, 180),
            restSeconds = 180, liftSlot = LiftSlot.DEADLIFT, isCompetitionLift = true,
        ),
        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(3, 6, 7.0), restSeconds = 90),
        slot("pallof", SlotRole.T3_ACCESSORY, CatalogIds.PALLOF, rpeSets(3, 8, 7.0), restSeconds = 60),
    ),
)

private fun hypertrophyAccessoriesDay(): DayRecipe = day(
    label = "Accesorios Hipertrofia",
    archetype = DaySlotTemplate.BB_TORSO,
    weekday = 3,
    slots = listOf(
        slot("press", SlotRole.T1_MAIN, CatalogIds.BP, rpeSets(3, 8, 7.0), restSeconds = 180, liftSlot = LiftSlot.BENCH),
        slot("row", SlotRole.T2_SUPPLEMENTAL, CatalogIds.CSR, rpeSets(3, 10, 7.0), restSeconds = 120),
        slot("face", SlotRole.T3_ACCESSORY, CatalogIds.FACE, rpeSets(2, 15, 7.0), restSeconds = 60),
        slot("tri", SlotRole.T3_ACCESSORY, CatalogIds.OH_TRI, rpeSets(3, 12, 7.0), restSeconds = 60),
        slot("curl", SlotRole.T3_ACCESSORY, CatalogIds.CURL, rpeSets(3, 10, 7.0), restSeconds = 60),
        slot("pallof", SlotRole.T3_ACCESSORY, CatalogIds.PALLOF, rpeSets(2, 10, 7.0), restSeconds = 60),
    ),
)

private fun fromDayRecipe(
    id: String,
    title: String,
    description: String,
    day: DayRecipe,
    splitIds: List<String>,
    labels: List<String>,
    primary: String,
    difficulty: Difficulty = Difficulty.AVANZADO,
): SessionTemplate {
    val principal = mutableListOf<Exercise>()
    val accessories = mutableListOf<Exercise>()
    day.slots.forEachIndexed { index, slot ->
        val exercise = recipeExercise(id, slot, index)
        if (slot.role == SlotRole.T1_MAIN || slot.role == SlotRole.SPEED) {
            principal += exercise
        } else {
            accessories += exercise
        }
    }
    val parts = buildList {
        if (principal.isNotEmpty()) {
            add(SessionPart(id = "$id-part-0", name = "Trabajo principal", color = "#153B50", exercises = principal))
        }
        if (accessories.isNotEmpty()) {
            add(SessionPart(id = "$id-part-1", name = "Accesorios", color = "#245C4A", exercises = accessories))
        }
    }
    val all = parts.flatMap { it.exercises }
    val session = Session(
        id = "session-$id",
        name = title,
        description = description,
        parts = parts,
    )
    return SessionTemplate(
        id = id,
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = title,
        description = description,
        emoji = "🏋️",
        tags = listOf(SessionTemplateTag.POWERLIFTING, SessionTemplateTag.FUERZA),
        difficulty = difficulty,
        estimatedDurationMinutes = (all.sumOf { it.sets.size * ((it.restTime ?: 90) + 45) } / 60.0).toInt().coerceAtLeast(20),
        exerciseCount = all.size,
        partCount = parts.size,
        muscleGroupsSummary = primary,
        session = session,
        sortOrder = -800 + id.hashCode() % 50,
        splitIds = splitIds,
        splitDayLabels = labels,
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = description,
        primaryFocusMuscle = primary,
        publicationStatus = SessionTemplatePublicationStatus.KPKN_NATIVE,
        autoGenerationEligible = true,
        dayArchetypes = listOfNotNull(day.archetype?.name),
    )
}

private fun recipeExercise(templateId: String, slot: com.example.kpkn.data.protocols.SlotRecipe, index: Int): Exercise {
    val configurationId = slot.lift.configurationId
    val exerciseId = "$templateId-${slot.id}-$index"
    return Exercise(
        id = exerciseId,
        name = systemTemplateDisplayName(configurationId),
        exerciseDbId = configurationId,
        exerciseId = configurationId,
        canonicalExerciseId = configurationId,
        exerciseFamilyId = configurationId.substringBefore("__"),
        sets = slot.sets.filter { !it.isWarmup }.mapIndexed { setIndex, recipe -> recipe.toExerciseSet("$exerciseId-s$setIndex") },
        warmupSets = slot.sets.filter { it.isWarmup }.mapIndexed { setIndex, recipe ->
            WarmupSetDefinition(
                id = "$exerciseId-w$setIndex",
                percentageOfWorkingWeight = recipe.percent ?: 40.0,
                targetReps = recipe.reps ?: 5,
                restBetween = 60,
            )
        },
        restTime = slot.restSeconds,
        trainingMode = TrainingMode.REPS,
        catalogRevision = TEMPLATE_CATALOG_REVISION,
        catalogDefinitionId = systemTemplateDefinitionId(configurationId),
        catalogConfigurationId = configurationId,
        performanceProfileId = systemTemplatePerformanceProfileId(configurationId),
        selectedAspects = null,
        occurrenceId = exerciseId,
        isCompetitionLift = slot.isCompetitionLift,
        variantName = slot.technique?.name,
    )
}

private fun SetRecipe.toExerciseSet(id: String): ExerciseSet = ExerciseSet(
    id = id,
    targetReps = reps ?: repsMin,
    targetRPE = rpe,
    targetRIR = rir,
    targetPercentageRM = percent,
    isAmrap = amrap,
    intensityMode = when {
        rir != null -> IntensityMode.RIR
        rpe != null -> IntensityMode.RPE
        amrap -> IntensityMode.AMRAP
        else -> IntensityMode.LOAD
    },
)
