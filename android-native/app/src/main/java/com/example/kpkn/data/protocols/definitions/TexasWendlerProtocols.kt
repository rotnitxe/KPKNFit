package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolFidelitySpec
import com.example.kpkn.data.protocols.ProtocolKind
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.programs.DaySlotTemplate

internal fun sbdSlots() = mapOf(
    LiftSlot.SQUAT to CatalogIds.SQ_LOW,
    LiftSlot.BENCH to CatalogIds.BP,
    LiftSlot.DEADLIFT to CatalogIds.DL,
    LiftSlot.OVERHEAD to CatalogIds.OHP,
)

internal fun kpknAssist(id: String, configurationId: String, sets: Int, reps: Int, rest: Int = 90) =
    slot(id, SlotRole.T3_ACCESSORY, configurationId, rpeSets(sets, reps, 8.0), rest, source = SlotSource.KPKN_DEFAULT)

object TexasMethodProtocols {
    private fun volumeMonday() = day(
        "Volumen 5x5",
        weekday = 1,
        slots = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(5, 5, 90.0, 240, basis = LoadBasis.PERCENT_OF_TOP_SET), 240, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, repeatPercentSets(5, 5, 90.0, 180, basis = LoadBasis.PERCENT_OF_TOP_SET), 180, LiftSlot.BENCH, isCompetitionLift = true),
            slot("dl", SlotRole.T2_SUPPLEMENTAL, CatalogIds.DL, repeatPercentSets(1, 5, 70.0, 240), 240, LiftSlot.DEADLIFT, isCompetitionLift = true),
            kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
            kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60),
        ),
    )

    private fun recoveryWednesday() = day(
        "Recuperación",
        weekday = 3,
        slots = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, repeatPercentSets(2, 5, 80.0, 180, basis = LoadBasis.PERCENT_OF_TOP_SET), 180, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("ohp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.OHP, repeatPercentSets(3, 5, 60.0, 150), 150, LiftSlot.OVERHEAD),
            slot("chin", SlotRole.T3_ACCESSORY, CatalogIds.CHIN, listOf(SetRecipe(reps = 8, amrap = true, rpe = 8.0, loadBasis = LoadBasis.RPE), SetRecipe(reps = 8, amrap = true, rpe = 8.0, loadBasis = LoadBasis.RPE), SetRecipe(reps = 8, amrap = true, rpe = 8.0, loadBasis = LoadBasis.RPE)), 120),
            slot("ext", SlotRole.T3_ACCESSORY, CatalogIds.BACK_EXT, rpeSets(3, 12, 7.0), 90, source = SlotSource.KPKN_DEFAULT),
            kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
        ),
    )

    private fun intensityFriday() = day(
        "Intensidad PR",
        weekday = 5,
        slots = listOf(
            slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, listOf(SetRecipe(reps = 5, percent = 100.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)), 240, LiftSlot.SQUAT, isCompetitionLift = true),
            slot("bp", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, listOf(SetRecipe(reps = 5, percent = 100.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_OF_TOP_SET)), 240, LiftSlot.BENCH, isCompetitionLift = true),
            slot("pendlay", SlotRole.T3_ACCESSORY, CatalogIds.PENDLAY, rpeSets(5, 3, 7.0), 120, technique = TechniqueModifier.SPEED, source = SlotSource.KPKN_DEFAULT),
            kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
            kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
        ),
    )

    fun recipe3d(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "texas-method-3d",
        weeks = (1..4).map { w ->
            weekRecipe(
                w, 0, "Texas", BlockGoal.INTENSIFICATION,
                listOf(volumeMonday(), recoveryWednesday(), intensityFriday()),
                weekName = "Semana $w",
            )
        },
        trainingMaxPercent = 1.0,
        liftSlots = sbdSlots(),
        progression = ProgressionRule.TopSetPr,
        claimedDaysPerWeek = 3,
        claimedLevel = "intermedio",
        autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.WEEKLY_REVIEW)),
        repeats = true,
    )

    val threeDay = Protocol(
        id = "texas-method-3d",
        name = "Texas Method",
        emoji = "🤠",
        description = "3 días, 4 semanas: lunes 5×5 @ 90 % del top de viernes, miércoles 2×5 @ 80 % del lunes, viernes 1×5 PR. Power clean no catalogado: el T3 de intensidad es remo Pendlay explosivo (sugerido por KPKN).",
        author = "Mark Rippetoe / Glenn Pendlay",
        tags = listOf("powerlifting", "intermedio", "3 días", "4 semanas", "%"),
        blocks = listOf(ProtocolBlock("Texas", 4, "Intensificación", 70, 90, 1.0)),
        defaultSplit = "texas_method",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.METHOD,
        source = attributed("Practical Programming / Starting Strength", "https://startingstrength.com/article/the_texas_method", "Mark Rippetoe", variant = "3-day"),
        recipe = recipe3d(),
        fidelitySpec = ProtocolFidelitySpec(4, 3, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("volume" to listOf(90.0), "recovery" to listOf(80.0))),
    )
}

object WendlerProtocols {
    private val mainWeeks = listOf(
        listOf(5 to 65.0, 5 to 75.0, 5 to 85.0),
        listOf(3 to 70.0, 3 to 80.0, 3 to 90.0),
        listOf(5 to 75.0, 3 to 85.0, 1 to 95.0),
        listOf(5 to 40.0, 5 to 50.0, 5 to 60.0),
    )

    private fun mainSets(weekInCycle: Int) = percentSets(
        180,
        *mainWeeks[weekInCycle].toTypedArray(),
        amrapLast = weekInCycle < 3,
    )

    private fun bbb(configurationId: String, lift: LiftSlot, firstPercent: Double) = slot(
        "bbb", SlotRole.T2_SUPPLEMENTAL, configurationId,
        repeatPercentSets(5, 10, 50.0, 120),
        120, lift, supplementalOf = "t1",
    )

    private fun fsl(configurationId: String, lift: LiftSlot, firstPercent: Double) = slot(
        "fsl", SlotRole.T2_SUPPLEMENTAL, configurationId,
        repeatPercentSets(5, 5, firstPercent, 120),
        120, lift, supplementalOf = "t1",
    )

    private fun assistanceFor(lift: LiftSlot) = when (lift) {
        LiftSlot.SQUAT, LiftSlot.DEADLIFT -> listOf(
            kpknAssist("row", CatalogIds.ROW_DB, 3, 10, 90),
            kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
        )
        LiftSlot.BENCH -> listOf(
            kpknAssist("pull", CatalogIds.PULLUP, 3, 8, 90),
            kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
        )
        LiftSlot.OVERHEAD -> listOf(
            slot("cg", SlotRole.T3_ACCESSORY, CatalogIds.BP, rpeSets(3, 8, 8.0), 90, LiftSlot.BENCH, technique = TechniqueModifier.CLOSE_GRIP, source = SlotSource.KPKN_DEFAULT),
            kpknAssist("pull", CatalogIds.PULLUP, 3, 8, 90),
            kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
        )
    }

    private fun dayFor(label: String, configurationId: String, lift: LiftSlot, weekInCycle: Int, variant: String, weekday: Int) = day(
        label,
        weekday = weekday,
        slots = listOf(
            slot("t1", SlotRole.T1_MAIN, configurationId, mainSets(weekInCycle), if (mainWeeks[weekInCycle].any { it.second >= 85.0 }) 240 else 180, lift, isCompetitionLift = lift != LiftSlot.OVERHEAD),
            if (variant == "bbb") bbb(configurationId, lift, mainWeeks[weekInCycle].first().second) else fsl(configurationId, lift, mainWeeks[weekInCycle].first().second),
        ) + assistanceFor(lift),
    )

    private fun recipe(id: String, variant: String): TrainingPlanRecipe {
        val lifts = listOf(
            Triple("Sentadilla", CatalogIds.SQ_LOW, LiftSlot.SQUAT) to 1,
            Triple("Banca", CatalogIds.BP, LiftSlot.BENCH) to 2,
            Triple("Peso muerto", CatalogIds.DL, LiftSlot.DEADLIFT) to 4,
            Triple("Press militar", CatalogIds.OHP, LiftSlot.OVERHEAD) to 5,
        )
        val labels = listOf("5s", "3s", "1s", "Descarga")
        val weeks = (1..4).map { w ->
            val cycleWeek = w - 1
            val goal = when (cycleWeek) {
                0 -> BlockGoal.ACCUMULATION
                1 -> BlockGoal.INTENSIFICATION
                2 -> BlockGoal.PEAK
                else -> BlockGoal.DELOAD
            }
            weekRecipe(
                w, 0, "5/3/1", goal,
                lifts.map { (triple, weekday) ->
                    val built = dayFor(triple.first, triple.second, triple.third, cycleWeek, variant, weekday)
                    if (goal == BlockGoal.PEAK || goal == BlockGoal.DELOAD) built.dropT3(2) else built
                },
                weekName = labels[cycleWeek],
            )
        }
        return TrainingPlanRecipe(
            id = id,
            weeks = weeks,
            trainingMaxPercent = 0.90,
            liftSlots = sbdSlots(),
            progression = ProgressionRule.CycleIncrement(2.5, 5.0),
            claimedDaysPerWeek = 4,
            claimedLevel = "intermedio",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
            repeats = true,
        )
    }

    val bbb = Protocol(
        id = "wendler-531-bbb",
        name = "5/3/1 Boring But Big",
        emoji = "5️",
        description = "4 días, 4 semanas: semanas 5s/3s/1s/descarga con AMRAP y BBB 5×10 @ 50 % TM. TM = 90 % 1RM.",
        author = "Jim Wendler",
        tags = listOf("powerlifting", "intermedio", "4 días", "4 semanas", "AMRAP", "%"),
        blocks = listOf(ProtocolBlock("5/3/1", 4, "Intensificación", 40, 95)),
        defaultSplit = "531_bbb",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.METHOD,
        source = attributed("5/3/1: The Simplest and Most Effective Training System", "https://jimwendler.com/blogs/jimwendler-com/101077262-5-3-1-for-a-beginner", "Jim Wendler", variant = "BBB"),
        recipe = recipe("wendler-531-bbb", "bbb"),
        fidelitySpec = ProtocolFidelitySpec(4, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("w1" to listOf(65.0, 75.0, 85.0))),
    )

    val fsl = bbb.copy(
        id = "wendler-531-fsl",
        name = "5/3/1 First Set Last",
        description = "4 días, 4 semanas: mismas olas 5/3/1 con FSL 5×5 al porcentaje del primer set.",
        tags = listOf("powerlifting", "intermedio", "4 días", "4 semanas", "AMRAP", "%"),
        source = attributed("5/3/1 Forever", "https://jimwendler.com/blogs/jimwendler-com/101077262-5-3-1-for-a-beginner", "Jim Wendler", variant = "FSL"),
        recipe = recipe("wendler-531-fsl", "fsl"),
    )
}

object TexasMethodFourDay {
    private fun intensity(id: String, configurationId: String, lift: LiftSlot, weekday: Int, volumeId: String, volumeConfig: String, volumeLift: LiftSlot) = day(
        label = id,
        weekday = weekday,
        slots = listOf(
            slot("t1", SlotRole.T1_MAIN, configurationId, listOf(SetRecipe(reps = 5, percent = 85.0, isTopSet = true)), 240, lift, isCompetitionLift = lift != LiftSlot.OVERHEAD),
            slot("t2", SlotRole.T2_SUPPLEMENTAL, volumeConfig, repeatPercentSets(if (volumeLift == LiftSlot.DEADLIFT) 3 else 5, 5, 70.0, 150), 150, volumeLift, isCompetitionLift = volumeLift != LiftSlot.OVERHEAD, supplementalOf = "t1"),
            kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120),
            kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
            kpknAssist("core", CatalogIds.PALLOF, 3, 10, 60),
        ),
    )

    fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "texas-method-4d",
        weeks = (1..4).map { w ->
            weekRecipe(
                w, 0, "Texas 4d", BlockGoal.INTENSIFICATION,
                listOf(
                    intensity("Banca/OHP", CatalogIds.BP, LiftSlot.BENCH, 1, "ohp-vol", CatalogIds.OHP, LiftSlot.OVERHEAD),
                    intensity("Sentadilla/PM", CatalogIds.SQ_LOW, LiftSlot.SQUAT, 2, "dl-vol", CatalogIds.DL, LiftSlot.DEADLIFT),
                    intensity("OHP/Banca", CatalogIds.OHP, LiftSlot.OVERHEAD, 4, "bp-vol", CatalogIds.BP, LiftSlot.BENCH),
                    intensity("PM/Sentadilla", CatalogIds.DL, LiftSlot.DEADLIFT, 5, "sq-vol", CatalogIds.SQ_HIGH, LiftSlot.SQUAT),
                ),
                weekName = "Semana $w",
            )
        },
        trainingMaxPercent = 1.0,
        liftSlots = sbdSlots(),
        progression = ProgressionRule.TopSetPr,
        claimedDaysPerWeek = 4,
        claimedLevel = "intermedio",
        repeats = true,
    )

    val definition = Protocol(
        id = "texas-method-4d",
        name = "Texas Method 4 días",
        emoji = "🤠",
        description = "4 días PPST: lun banca int + OHP vol, mar sentadilla int + PM vol, jue OHP int + banca vol, vie PM int + sentadilla vol.",
        author = "Andy Baker / Mark Rippetoe",
        tags = listOf("powerlifting", "intermedio", "4 días", "4 semanas", "%"),
        blocks = listOf(ProtocolBlock("Texas 4d", 4, "Intensificación", 70, 90)),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.METHOD,
        source = attributed("Practical Programming 4-day Texas Method", "https://startingstrength.com/article/the_texas_method", "Andy Baker", variant = "4-day"),
        recipe = recipe(),
        fidelitySpec = ProtocolFidelitySpec(4, 4, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("t1" to listOf(85.0))),
    )
}
