package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.DaySlotTemplate
import com.example.kpkn.data.protocols.CatalogIds as Id

/** Arquetipos 2.1.1: sesiones profesionales reutilizadas por protocolos, plantillas y F5. */
object DayArchetypes {
    private val kpkn = SlotSource.KPKN_DEFAULT

    fun plSquat(
        t1Percent: Double,
        t1Sets: Int = 4,
        t1Reps: Int = 4,
        t1Rest: Int = 240,
        deficitPercent: Double = 65.0,
        benchPausePercent: Double = 70.0,
        weekday: Int? = 1,
        label: String = "Sentadilla pesada",
        t1Amrap: Boolean = false,
        t1Top: Boolean = false,
        t1Id: String = "sq",
        t1ConfigurationId: String = Id.SQ_LOW,
        t1Technique: TechniqueModifier? = null,
    ): DayRecipe = day(
        label = label,
        archetype = DaySlotTemplate.PL_SQUAT,
        weekday = weekday,
        slots = listOf(
            slot(
                t1Id, SlotRole.T1_MAIN, t1ConfigurationId,
                warmupPercentSets() + repeatPercentSets(t1Sets, t1Reps, t1Percent, t1Rest, amrapLast = t1Amrap),
                restSeconds = t1Rest, liftSlot = LiftSlot.SQUAT, isCompetitionLift = true,
                technique = t1Technique,
            ).let { if (t1Top) it.copy(sets = it.sets.mapIndexed { i, s -> s.copy(isTopSet = !s.isWarmup && i == it.sets.lastIndex) }) else it },
            slot(
                "dl-def", SlotRole.T2_SUPPLEMENTAL, Id.DL_DEF,
                repeatPercentSets(3, 5, deficitPercent, 180),
                restSeconds = 180, liftSlot = LiftSlot.DEADLIFT, source = kpkn,
            ),
            slot(
                "bp-pause", SlotRole.T2_SUPPLEMENTAL, Id.BP_PAUSE,
                repeatPercentSets(3, 6, benchPausePercent, 150),
                restSeconds = 150, liftSlot = LiftSlot.BENCH, source = kpkn,
            ),
            slot("ghr", SlotRole.T3_ACCESSORY, Id.GHR, rpeSets(3, 8, 8.0), restSeconds = 90, source = kpkn),
            slot("pallof", SlotRole.T3_ACCESSORY, Id.PALLOF, rpeSets(3, 10, 7.0), restSeconds = 60, isUnilateral = true, source = kpkn),
        ),
    )

    fun plBenchHeavy(
        t1Percent: Double,
        t1Sets: Int = 4,
        t1Reps: Int = 4,
        weekday: Int? = 4,
        label: String = "Banca pesada",
        t1Amrap: Boolean = false,
        t1ConfigurationId: String = Id.BP,
        t1Technique: TechniqueModifier? = null,
        t2ConfigurationId: String = Id.BP_INC,
    ): DayRecipe = day(
        label = label,
        archetype = DaySlotTemplate.PL_BENCH_HEAVY,
        weekday = weekday,
        slots = listOf(
            slot(
                "bp", SlotRole.T1_MAIN, t1ConfigurationId,
                warmupPercentSets() + repeatPercentSets(t1Sets, t1Reps, t1Percent, 240, amrapLast = t1Amrap),
                restSeconds = 240, liftSlot = LiftSlot.BENCH, isCompetitionLift = true,
                technique = t1Technique,
            ),
            slot("inc", SlotRole.T2_SUPPLEMENTAL, t2ConfigurationId, rpeSets(3, 6, 7.5), restSeconds = 150, supplementalOf = "bp", source = kpkn),
            slot("pendlay", SlotRole.T3_ACCESSORY, Id.PENDLAY, rpeSets(4, 6, 8.0), restSeconds = 120, source = kpkn),
            slot("pull", SlotRole.T3_ACCESSORY, Id.PULLUP, rangeRirSets(3, 6, 8, 2), restSeconds = 120, source = kpkn),
            slot("jm", SlotRole.T3_ACCESSORY, Id.JM, rpeSets(3, 8, 8.0), restSeconds = 90, source = kpkn),
            slot("face", SlotRole.T3_ACCESSORY, Id.FACE, rpeSets(3, 15, 7.0), restSeconds = 60, source = kpkn),
        ),
    )

    fun plDeadlift(
        t1Percent: Double,
        t1Sets: Int = 3,
        t1Reps: Int = 3,
        squatPercent: Double = 68.0,
        weekday: Int? = 2,
        label: String = "Peso muerto",
        t1Amrap: Boolean = false,
        t1Top: Boolean = false,
        t1ConfigurationId: String = Id.DL,
        t1Technique: TechniqueModifier? = null,
        t2ConfigurationId: String = Id.SQ_FRONT,
    ): DayRecipe = day(
        label = label,
        archetype = DaySlotTemplate.PL_DEADLIFT,
        weekday = weekday,
        slots = listOf(
            slot(
                "dl", SlotRole.T1_MAIN, t1ConfigurationId,
                warmupPercentSets() + repeatPercentSets(t1Sets, t1Reps, t1Percent, 240, amrapLast = t1Amrap),
                restSeconds = 240, liftSlot = LiftSlot.DEADLIFT, isCompetitionLift = true,
                technique = t1Technique,
            ).let { if (t1Top) it.copy(sets = it.sets.mapIndexed { i, s -> s.copy(isTopSet = !s.isWarmup && i == it.sets.lastIndex) }) else it },
            slot(
                "sq-light", SlotRole.T2_SUPPLEMENTAL, t2ConfigurationId,
                repeatPercentSets(4, 5, squatPercent, 150),
                restSeconds = 150, liftSlot = LiftSlot.SQUAT, source = kpkn,
            ),
            slot("row", SlotRole.T3_ACCESSORY, Id.ROW, rpeSets(3, 6, 8.0), restSeconds = 120, source = kpkn),
            slot("ghr", SlotRole.T3_ACCESSORY, Id.GHR, rpeSets(3, 8, 8.0), restSeconds = 90, source = kpkn),
            slot("shrug", SlotRole.T3_ACCESSORY, Id.SHRUG, rpeSets(1, 8, 7.0), restSeconds = 60, source = kpkn),
            slot("wheel", SlotRole.T3_ACCESSORY, Id.WHEEL, rpeSets(3, 8, 7.0), restSeconds = 60, source = kpkn),
        ),
    )

    fun plBenchVolume(
        t1Percent: Double = 70.0,
        weekday: Int? = 5,
        label: String = "Banca volumen",
        t1ConfigurationId: String = Id.BP,
        t1Technique: TechniqueModifier? = null,
    ): DayRecipe {
        val t1Rest = if (t1Percent >= 85.0) 240 else 180
        return day(
            label = label,
            archetype = DaySlotTemplate.PL_BENCH_VOLUME,
            weekday = weekday,
            slots = listOf(
                slot(
                    "bp-vol", SlotRole.T1_MAIN, t1ConfigurationId,
                    warmupPercentSets() + repeatPercentSets(4, 6, t1Percent, t1Rest),
                    restSeconds = t1Rest, liftSlot = LiftSlot.BENCH, isCompetitionLift = true,
                    technique = t1Technique,
                ),
                slot("ohp", SlotRole.T2_SUPPLEMENTAL, Id.OHP, rpeSets(3, 8, 7.5), restSeconds = 120, liftSlot = LiftSlot.OVERHEAD, source = kpkn),
                slot("csr", SlotRole.T3_ACCESSORY, Id.CSR, rpeSets(3, 10, 8.0), restSeconds = 90, source = kpkn),
                slot("face", SlotRole.T3_ACCESSORY, Id.FACE, rpeSets(3, 15, 7.0), restSeconds = 60, source = kpkn),
                slot("oh-tri", SlotRole.T3_ACCESSORY, Id.OH_TRI, rpeSets(3, 12, 8.0), restSeconds = 60, source = kpkn),
                slot("curl", SlotRole.T3_ACCESSORY, Id.CURL, rpeSets(3, 10, 8.0), restSeconds = 60, source = kpkn),
            ),
        )
    }

    fun bbPush(rir: Int = 2): DayRecipe = day(
        label = "Empuje",
        archetype = DaySlotTemplate.BB_PUSH,
        weekday = 1,
        slots = listOf(
            slot("press", SlotRole.T1_MAIN, Id.BP, rangeRirSets(3, 6, 8, rir), restSeconds = 180, liftSlot = LiftSlot.BENCH),
            slot("inc", SlotRole.T2_SUPPLEMENTAL, Id.BP_INC_DB, rangeRirSets(3, 8, 10, rir), restSeconds = 120, supplementalOf = "press", source = kpkn),
            slot("pull", SlotRole.T3_ACCESSORY, Id.PULLUP, rangeRirSets(3, 6, 8, rir), restSeconds = 120, source = kpkn),
            slot("lat", SlotRole.T3_ACCESSORY, Id.LATERAL, rangeRirSets(3, 12, 15, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("fly", SlotRole.T3_ACCESSORY, Id.FLY_INC, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("oh-tri", SlotRole.T3_ACCESSORY, Id.OH_TRI, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("pushdown", SlotRole.T3_ACCESSORY, Id.PUSHDOWN, rangeRirSets(1, 12, 15, rir.coerceAtLeast(1)), restSeconds = 45, source = kpkn),
        ),
    )

    fun bbPull(rir: Int = 2): DayRecipe = day(
        label = "Tirón",
        archetype = DaySlotTemplate.BB_PULL,
        weekday = 2,
        slots = listOf(
            slot("pull", SlotRole.T1_MAIN, Id.PULLUP, rangeRirSets(3, 6, 8, rir), restSeconds = 180),
            slot("row", SlotRole.T2_SUPPLEMENTAL, Id.ROW, rangeRirSets(3, 6, 8, rir), restSeconds = 120, source = kpkn),
            slot("rear", SlotRole.T3_ACCESSORY, Id.REAR_DELT, rangeRirSets(3, 12, 15, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("pullover", SlotRole.T3_ACCESSORY, Id.PULLOVER, rangeRirSets(3, 10, 12, rir), restSeconds = 90, source = kpkn),
            slot("bayes", SlotRole.T3_ACCESSORY, Id.BAYESIAN, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("hammer", SlotRole.T3_ACCESSORY, Id.HAMMER, rangeRirSets(2, 10, 12, rir.coerceAtLeast(1)), restSeconds = 45, source = kpkn),
        ),
    )

    fun bbLegs(
        rir: Int = 2,
        label: String = "Pierna",
        weekday: Int? = 3,
        hipDominant: Boolean = false,
    ): DayRecipe = day(
        label = label,
        archetype = DaySlotTemplate.BB_LEGS,
        weekday = weekday,
        slots = if (hipDominant) {
            listOf(
                slot("rdl", SlotRole.T1_MAIN, Id.RDL, rangeRirSets(3, 8, 10, rir), restSeconds = 180, liftSlot = LiftSlot.DEADLIFT),
                slot("sq", SlotRole.T2_SUPPLEMENTAL, Id.SQ_HIGH, rangeRirSets(3, 6, 8, rir), restSeconds = 150, liftSlot = LiftSlot.SQUAT, source = kpkn),
                slot("press", SlotRole.T3_ACCESSORY, Id.PRESS_LEG, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 120, source = kpkn),
                slot("curl", SlotRole.T3_ACCESSORY, Id.CURL_H, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 90, source = kpkn),
                slot("ext", SlotRole.T3_ACCESSORY, Id.LEG_EXT, rangeRirSets(2, 12, 15, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
                slot("calf", SlotRole.T3_ACCESSORY, Id.CALF, rangeRirSets(4, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            )
        } else {
            listOf(
                slot("sq", SlotRole.T1_MAIN, Id.SQ_HIGH, rangeRirSets(3, 6, 8, rir), restSeconds = 180, liftSlot = LiftSlot.SQUAT),
                slot("rdl", SlotRole.T2_SUPPLEMENTAL, Id.RDL, rangeRirSets(3, 8, 10, rir), restSeconds = 150, liftSlot = LiftSlot.DEADLIFT, source = kpkn),
                slot("press", SlotRole.T3_ACCESSORY, Id.PRESS_LEG, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 120, source = kpkn),
                slot("curl", SlotRole.T3_ACCESSORY, Id.CURL_H, rangeRirSets(3, 10, 12, rir.coerceAtLeast(1)), restSeconds = 90, source = kpkn),
                slot("ext", SlotRole.T3_ACCESSORY, Id.LEG_EXT, rangeRirSets(2, 12, 15, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
                slot("calf", SlotRole.T3_ACCESSORY, Id.CALF, rangeRirSets(4, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            )
        },
    )

    fun bbTorso(rir: Int = 2): DayRecipe = day(
        label = "Torso",
        archetype = DaySlotTemplate.BB_TORSO,
        weekday = 1,
        slots = listOf(
            slot("press", SlotRole.T1_MAIN, Id.BP, rangeRirSets(3, 6, 8, rir), restSeconds = 180, liftSlot = LiftSlot.BENCH),
            slot("pull", SlotRole.T2_SUPPLEMENTAL, Id.LAT, rangeRirSets(4, 8, 10, rir), restSeconds = 120, source = kpkn),
            slot("inc", SlotRole.T3_ACCESSORY, Id.BP_INC, rangeRirSets(3, 8, 10, rir), restSeconds = 120, supplementalOf = "press", source = kpkn),
            slot("row", SlotRole.T3_ACCESSORY, Id.CSR, rangeRirSets(2, 8, 10, rir), restSeconds = 90, source = kpkn),
            slot("pullover", SlotRole.T3_ACCESSORY, Id.PULLOVER, rangeRirSets(3, 10, 12, rir), restSeconds = 90, source = kpkn),
            slot("lat", SlotRole.T3_ACCESSORY, Id.SUPER_LAT, rangeRirSets(3, 12, 15, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("tri", SlotRole.T3_ACCESSORY, Id.OH_TRI, rangeRirSets(2, 10, 12, rir.coerceAtLeast(1)), restSeconds = 60, source = kpkn),
            slot("bi", SlotRole.T3_ACCESSORY, Id.BAYESIAN, rangeRirSets(2, 10, 12, rir.coerceAtLeast(1)), restSeconds = 45, source = kpkn),
        ),
    )
}
