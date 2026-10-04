package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.KPKN_OWN_PLAN_DISCLAIMER
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
        // L-05/L-06 (B.S6): el peso muerto se entrena pesado en todo el plan y el pico llega a ≥ 85 % del 1RM efectivo
        // (95 y 100 % del TM al 90 % = 85,5 y 90 % del 1RM; antes 88-93 % del TM = 79-84 % del 1RM).
        // Base: DL 3×4 al 72-75 %. Intensificación: DL 3×3 al 80-86 %. Peak: w9 DL 3×2@94 y w10 DL 2×1@98. Taper: w11 DL 2×2@80.
        // Taper (tabla B.S6): sentadilla y banca pesada al 85 % del TM (76,5 % del 1RM; antes 80 y 78 %). Efecto conocido: con el % crudo el
        // lunes de la semana 11 cuenta como «pesada» (85 % o más) y la última pesada queda a 4 días del test (hallazgo SOFT de BLOCK, objetivo
        // 7-10 días; C7 del contrato lo inventaría porque con el %1RM efectivo no hay hallazgo). Bajar ese taper al 80 % lo elimina.
        val weeks = buildList {
            repeat(4) { i ->
                val w = i + 1
                val squat = 70.0 + i * 2.0
                add(week(w, 0, "Base", BlockGoal.ACCUMULATION, squat, 72.0 + i, 70.0, 72.0))
            }
            repeat(4) { i ->
                val w = 5 + i
                add(
                    week(
                        w, 1, "Intensificación", BlockGoal.INTENSIFICATION, 78.0 + i * 2, 80.0 + i * 2, 75.0, 80.0 + i,
                        dlReps = 3,
                    ),
                )
            }
            add(week(9, 2, "Peak", BlockGoal.PEAK, 95.0, 94.0, 78.0, 95.0, t1Sets = 3, t1Reps = 2, dlSets = 3, dlReps = 2, dropAccessories = true))
            add(week(10, 2, "Peak", BlockGoal.PEAK, 100.0, 98.0, 75.0, 100.0, t1Sets = 2, t1Reps = 1, dlSets = 2, dlReps = 1, dropAccessories = true))
            add(week(11, 3, "Taper", BlockGoal.TAPER, 85.0, 80.0, 65.0, 85.0, t1Sets = 2, t1Reps = 2, dlSets = 2, dlReps = 2, dropAccessories = true))
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
        dlSets: Int = 3,
        dlReps: Int = 4,
        dropAccessories: Boolean = false,
    ) = weekRecipe(
        weekNumber = number,
        blockIndex = block,
        blockName = name,
        blockGoal = goal,
        // L-06 (B.S6): sentadilla el lunes, banca de volumen el martes, peso muerto el jueves y banca pesada el viernes.
        // W3 (dos T1 axiales pesados en días seguidos) impedía el peso muerto pesado el martes, el día después de la sentadilla.
        days = listOf(
            DayArchetypes.plSquat(squatPct, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 1, label = "Sentadilla/Banca"),
            DayArchetypes.plBenchVolume(benchVolPct, weekday = 2, label = "Banca Volumen"),
            DayArchetypes.plDeadlift(dlPct, t1Sets = dlSets, t1Reps = dlReps, weekday = 4, label = "Peso Muerto"),
            DayArchetypes.plBenchHeavy(benchHeavyPct, t1Sets = t1Sets, t1Reps = t1Reps, weekday = 5, label = "Banca pesada"),
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
            ProtocolBlock("Peak", 2, "Peak", 85, 100, 0.65),
            ProtocolBlock("Taper", 1, "Taper", 70, 90, 0.35),
        ),
        defaultSplit = "pl_classic_4",
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
        kind = ProtocolKind.FIXED_PROGRAM,
        source = ProtocolSource(
            definitionId = "kpkn-native-sbd-4",
            revision = "2026-09-13",
            primaryReference = "KPKN Native SBD v4",
            primaryUrl = null, // plan propio: no hay una página de fuente que enlazar
            variant = "4-day SBD",
            version = "v5",
            reviewedAt = "2026-09-13",
            catalogRevision = "v2-approved-2026-08-12-a",
            approvedBy = "KPKN Editorial",
            disclaimer = "$KPKN_OWN_PLAN_DISCLAIMER No afiliado a federaciones de powerlifting.",
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
