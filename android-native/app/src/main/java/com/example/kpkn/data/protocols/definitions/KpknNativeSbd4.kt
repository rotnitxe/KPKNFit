package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolFidelitySpec
import com.example.kpkn.data.protocols.ProtocolKind
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.protocols.ProtocolSource
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.weekRecipe

object KpknNativeSbd4 {
    fun recipe(): TrainingPlanRecipe {
        val weeks = buildList {
            repeat(4) { i ->
                val w = i + 1
                val squat = 70.0 + i * 2.0
                add(week(w, 0, "Base", BlockGoal.ACCUMULATION, squat, 68.0 + i, 70.0, 72.0))
            }
            repeat(4) { i ->
                val w = 5 + i
                add(week(w, 1, "Intensificación", BlockGoal.INTENSIFICATION, 78.0 + i * 2, 72.0 + i, 75.0, 80.0 + i))
            }
            add(week(9, 2, "Peak", BlockGoal.PEAK, 88.0, 75.0, 78.0, 90.0, t1Sets = 3, t1Reps = 2, dropAccessories = true))
            add(week(10, 2, "Peak", BlockGoal.PEAK, 92.0, 70.0, 75.0, 93.0, t1Sets = 2, t1Reps = 1, dropAccessories = true))
            add(week(11, 3, "Taper", BlockGoal.TAPER, 80.0, 60.0, 65.0, 78.0, t1Sets = 2, t1Reps = 2, dropAccessories = true))
        }
        return TrainingPlanRecipe(
            id = "kpkn-native-sbd-4",
            weeks = weeks,
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
            claimedDaysPerWeek = 4,
            claimedLevel = "intermedio",
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.WEEKLY_REVIEW)),
        )
    }

    private fun week(
        number: Int,
        block: Int,
        name: String,
        goal: BlockGoal,
        squatPct: Double,
        dlPct: Double,
        benchVolPct: Double,
        benchHeavyPct: Double,
        t1Sets: Int = 4,
        t1Reps: Int = 4,
        dropAccessories: Boolean = false,
    ) = weekRecipe(
        weekNumber = number,
        blockIndex = block,
        blockName = name,
        blockGoal = goal,
        days = listOf(
            DayArchetypes.plSquat(squatPct, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 1, label = "Sentadilla/Banca"),
            DayArchetypes.plDeadlift(dlPct, t1Sets = t1Sets.coerceAtMost(3), t1Reps = t1Reps.coerceAtLeast(2), weekday = 2, label = "Peso Muerto"),
            DayArchetypes.plBenchHeavy(benchHeavyPct, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 4, label = "Banca pesada"),
            DayArchetypes.plBenchVolume(benchVolPct, weekday = 5, label = "Banca Volumen"),
        ).map { day -> if (dropAccessories) day.dropT3(2) else day },
    )

    val definition: Protocol = Protocol(
        id = "kpkn-native-sbd-4",
        name = "KPKN SBD · 4 días",
        emoji = "🏋️",
        description = "Protocolo KPKN nativo de 4 días y 11 semanas: sentadilla, banca y peso muerto con arquetipos profesionales, cero exenciones.",
        author = "KPKN Fit",
        tags = listOf("powerlifting", "sbd", "kpkn-native", "intermedio", "4 días", "11 semanas"),
        sessionCategories = listOf("Principal", "Suplementario", "Accesorios"),
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
            revision = "2026-09-13",
            primaryReference = "KPKN Native SBD v4",
            primaryUrl = "https://kpkn.fit/protocols/kpkn-native-sbd-4",
            variant = "4-day SBD",
            version = "v5",
            reviewedAt = "2026-09-13",
            catalogRevision = "v2-approved-2026-08-12-a",
            approvedBy = "KPKN Editorial",
            disclaimer = "Protocolo nativo KPKN. No afiliado a federaciones de powerlifting.",
        ),
        recipe = recipe(),
        fidelitySpec = ProtocolFidelitySpec(
            expectedWeeks = 11,
            expectedDaysPerWeek = 4,
            requiresPercent = true,
            claimedLevel = "intermedio",
        ),
        dayRecipes = emptyList(),
    )
}
