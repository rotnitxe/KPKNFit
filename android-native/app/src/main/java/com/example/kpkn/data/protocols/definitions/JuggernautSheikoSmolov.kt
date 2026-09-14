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
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.dropT3
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.rangeRirSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.rpeSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe

private fun protocol(
    id: String,
    name: String,
    emoji: String,
    description: String,
    author: String,
    tags: List<String>,
    blocks: List<ProtocolBlock>,
    split: String,
    source: com.example.kpkn.data.protocols.ProtocolSource,
    recipe: TrainingPlanRecipe,
    spec: ProtocolFidelitySpec,
    kind: ProtocolKind = ProtocolKind.FIXED_PROGRAM,
    status: ProtocolPublicationStatus = ProtocolPublicationStatus.VERIFIED,
    exemptions: List<RecipeCompositionExemption> = recipe.exemptions,
) = Protocol(
    id = id, name = name, emoji = emoji, description = description, author = author, tags = tags,
    blocks = blocks, defaultSplit = split, publicationStatus = status, kind = kind, source = source,
    recipe = recipe, fidelitySpec = spec, exemptions = exemptions,
)

object JuggernautProtocol {
    private data class Wave(val name: String, val goal: BlockGoal, val weeks: List<List<Pair<Int, Double>>>)

    private val waves = listOf(
        Wave("10s", BlockGoal.ACCUMULATION, listOf(
            listOf(10 to 60.0, 10 to 60.0, 10 to 60.0, 10 to 60.0, 10 to 60.0),
            listOf(5 to 55.0, 5 to 62.5, 10 to 67.5, 10 to 67.5, 10 to 67.5),
            listOf(5 to 50.0, 3 to 60.0, 1 to 70.0, 10 to 75.0),
            listOf(5 to 40.0, 5 to 50.0, 5 to 60.0),
        )),
        Wave("8s", BlockGoal.INTENSIFICATION, listOf(
            listOf(8 to 65.0, 8 to 65.0, 8 to 65.0, 8 to 65.0, 8 to 65.0),
            listOf(3 to 60.0, 3 to 67.5, 8 to 72.5, 8 to 72.5, 8 to 72.5),
            listOf(3 to 55.0, 3 to 65.0, 1 to 75.0, 8 to 80.0),
            listOf(5 to 40.0, 5 to 50.0, 5 to 60.0),
        )),
        Wave("5s", BlockGoal.INTENSIFICATION, listOf(
            listOf(5 to 70.0, 5 to 70.0, 5 to 70.0, 5 to 70.0, 5 to 70.0, 5 to 70.0),
            listOf(2 to 65.0, 2 to 72.5, 5 to 77.5, 5 to 77.5, 5 to 77.5, 5 to 77.5),
            listOf(2 to 60.0, 2 to 70.0, 1 to 80.0, 5 to 85.0),
            listOf(5 to 40.0, 5 to 50.0, 5 to 60.0),
        )),
        Wave("3s", BlockGoal.PEAK, listOf(
            listOf(3 to 75.0, 3 to 75.0, 3 to 75.0, 3 to 75.0, 3 to 75.0, 3 to 75.0, 3 to 75.0),
            listOf(1 to 70.0, 1 to 77.5, 3 to 82.5, 3 to 82.5, 3 to 82.5, 3 to 82.5, 3 to 82.5),
            listOf(1 to 65.0, 1 to 75.0, 1 to 85.0, 3 to 90.0),
            listOf(5 to 40.0, 5 to 50.0, 5 to 60.0),
        )),
    )

    fun recipe(): TrainingPlanRecipe {
        val lifts = listOf(
            Triple("Sentadilla", CatalogIds.SQ_LOW, LiftSlot.SQUAT) to 1,
            Triple("Banca", CatalogIds.BP, LiftSlot.BENCH) to 2,
            Triple("Peso muerto", CatalogIds.DL, LiftSlot.DEADLIFT) to 4,
            Triple("Press", CatalogIds.OHP, LiftSlot.OVERHEAD) to 5,
        )
        val weeks = waves.flatMapIndexed { waveIndex, wave ->
            wave.weeks.mapIndexed { weekInWave, steps ->
                val absolute = waveIndex * 4 + weekInWave + 1
                val amrap = weekInWave == 2
                weekRecipe(absolute, waveIndex, wave.name, wave.goal, lifts.map { (triple, weekday) ->
                    val (label, id, lift) = triple
                    val built = day(
                        label,
                        weekday = weekday,
                        slots = listOf(
                            slot("t1", SlotRole.T1_MAIN, id, percentSets(180, *steps.toTypedArray(), amrapLast = amrap), if (steps.any { it.second >= 85 }) 240 else 180, lift, isCompetitionLift = lift != LiftSlot.OVERHEAD),
                            slot(
                                "t2",
                                SlotRole.T2_SUPPLEMENTAL,
                                when (lift) {
                                    LiftSlot.SQUAT -> CatalogIds.SQ_FRONT
                                    LiftSlot.BENCH -> CatalogIds.BP_INC
                                    LiftSlot.DEADLIFT -> CatalogIds.RDL
                                    LiftSlot.OVERHEAD -> CatalogIds.BP_INC
                                },
                                rpeSets(3, 8, 7.0),
                                120,
                                if (lift == LiftSlot.OVERHEAD) LiftSlot.BENCH else null,
                                supplementalOf = "t1",
                            ),
                        ) + if (lift == LiftSlot.BENCH || lift == LiftSlot.OVERHEAD) {
                            listOf(
                                kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120),
                                kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                                kpknAssist("core", CatalogIds.PALLOF, 3, 10, 60),
                            )
                        } else {
                            listOf(
                                kpknAssist("row", CatalogIds.CSR, 3, 8, 90),
                                kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90),
                                kpknAssist("core", CatalogIds.PALLOF, 3, 10, 60),
                            )
                        },
                    )
                    if (wave.goal == BlockGoal.PEAK) built.dropT3(2) else built
                })
            }
        }
        return TrainingPlanRecipe(id = "juggernaut-2", weeks = weeks, trainingMaxPercent = 0.90, liftSlots = sbdSlots(), progression = ProgressionRule.CycleIncrement(2.5, 5.0), claimedDaysPerWeek = 4, claimedLevel = "avanzado", autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)))
    }

    val definition = protocol(
        "juggernaut-2", "Juggernaut Method 2.0", "🦏",
        "16 semanas = 4 olas × 4 semanas (10s/8s/5s/3s) con AMRAP de realización que ajusta TM.",
        "Chad Wesley Smith", listOf("powerlifting", "avanzado", "4 días", "16 semanas", "AMRAP", "%"),
        waves.map { ProtocolBlock(it.name, 4, it.goal.name.lowercase().replaceFirstChar { c -> c.titlecase() }, 40, 90) },
        "pl_classic_4", attributed("The Juggernaut Method 2.0", "https://www.jtsstrength.com/the-juggernaut-method-2-0/", "Chad Wesley Smith"),
        recipe(), ProtocolFidelitySpec(16, 4, requiresAmrap = true, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("10s" to listOf(60.0))),
    )
}

object RemainingVerifiedProtocols {
    val sheiko = run {
        val exemptions = listOf(
            RecipeCompositionExemption("H2", "Sentadilla/Banca", "Sheiko repite el mismo levantamiento en la sesión"),
            RecipeCompositionExemption("H3", "Sentadilla/Banca", "Volumen técnico 70-80 % por diseño"),
            RecipeCompositionExemption("H4", "Sentadilla/Banca", "Variantes del mismo replacementGroup"),
            RecipeCompositionExemption("H5b", "Peso muerto/Banca", "Días SQ+PM de #30"),
            RecipeCompositionExemption("H6", "Sentadilla/Banca", "25-35 series de práctica técnica"),
            RecipeCompositionExemption("H6", "Peso muerto/Banca", "25-35 series de práctica técnica"),
        )
        fun rm(count: Int, reps: Int, pct: Double) = List(count) {
            SetRecipe(reps = reps, percent = pct, loadBasis = LoadBasis.PERCENT_1RM)
        }
        fun rest(sets: List<SetRecipe>) = if (sets.any { (it.percent ?: 0.0) >= 85 }) 240 else 180
        fun monday(sq: List<SetRecipe>, bp: List<SetRecipe>, sq2: List<SetRecipe>) = day(
            "Sentadilla/Banca", weekday = 1, slots = listOf(
                slot("a", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sq, rest(sq), LiftSlot.SQUAT, isCompetitionLift = true),
                slot("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, bp, 150, LiftSlot.BENCH, isCompetitionLift = true),
                slot("a2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_LOW, sq2, 150, LiftSlot.SQUAT, supplementalOf = "a"),
                kpknAssist("lunge", CatalogIds.LUNGE_F, 2, 8, 90),
                kpknAssist("fly", CatalogIds.FLY, 2, 12, 60),
                kpknAssist("crunch", CatalogIds.CRUNCH, 3, 15, 45),
            ),
        )
        fun wednesday(dl: List<SetRecipe>, bp: List<SetRecipe>, toKnees: Boolean = false) = day(
            "Peso muerto/Banca", weekday = 3, slots = listOf(
                slot(
                    "a", SlotRole.T1_MAIN, CatalogIds.DL, dl, rest(dl), LiftSlot.DEADLIFT,
                    isCompetitionLift = !toKnees,
                    technique = if (toKnees) TechniqueModifier.TO_KNEES else null,
                ),
                slot("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, bp, 150, LiftSlot.BENCH, isCompetitionLift = true),
                slot("gm", SlotRole.T3_ACCESSORY, CatalogIds.GM, rpeSets(3, 8, 7.0), 90),
                kpknAssist("dips", CatalogIds.DIPS, 3, 8, 90),
                kpknAssist("crunch", CatalogIds.CRUNCH, 3, 15, 45),
            ),
        )
        fun friday(sq: List<SetRecipe>, bp: List<SetRecipe>, sq2: List<SetRecipe>) = day(
            "Sentadilla/Banca 2", weekday = 5, slots = listOf(
                slot("a", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sq, rest(sq), LiftSlot.SQUAT, isCompetitionLift = true),
                slot("b", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, bp, 150, LiftSlot.BENCH, isCompetitionLift = true),
                slot("a2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.SQ_LOW, sq2, 150, LiftSlot.SQUAT, supplementalOf = "a"),
                kpknAssist("lunge", CatalogIds.LUNGE_F, 2, 8, 90),
                kpknAssist("fly", CatalogIds.FLY, 2, 12, 60),
                kpknAssist("crunch", CatalogIds.CRUNCH, 3, 15, 45),
            ),
        )
        val weeks = listOf(
            // #29 preparación — tabla pública: BP 50×5, 60×4×2, 70×3×2, 75×3×5; SQ 50×5, 60×5×2, 70×5×5. Tope 80 %.
            Triple(BlockGoal.ACCUMULATION, 0, "#29") to listOf(
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(5, 5, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 70.0) + rm(5, 3, 75.0),
                    rm(2, 5, 60.0),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(4, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(4, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(4, 4, 70.0) to
                        (rm(1, 5, 55.0) + rm(2, 3, 65.0) + rm(3, 3, 75.0) to rm(2, 4, 60.0)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(5, 5, 72.5),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 70.0) + rm(5, 3, 77.5),
                    rm(2, 5, 62.5),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(4, 3, 72.5),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(4, 3, 72.5),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(4, 4, 72.5) to
                        (rm(1, 5, 55.0) + rm(2, 3, 67.5) + rm(3, 3, 77.5) to rm(2, 4, 62.5)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(5, 4, 75.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 72.5) + rm(4, 3, 80.0),
                    rm(2, 4, 65.0),
                ) to Triple(
                    rm(1, 3, 50.0) + rm(2, 3, 60.0) + rm(4, 3, 75.0),
                    rm(1, 5, 50.0) + rm(2, 3, 65.0) + rm(4, 3, 75.0),
                    rm(1, 5, 50.0) + rm(2, 4, 65.0) + rm(4, 3, 75.0) to
                        (rm(1, 4, 55.0) + rm(2, 3, 70.0) + rm(3, 2, 80.0) to rm(2, 4, 65.0)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(4, 5, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(3, 3, 70.0),
                    rm(2, 5, 60.0),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(3, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(3, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(3, 4, 70.0) to
                        (rm(1, 5, 55.0) + rm(2, 3, 65.0) + rm(3, 3, 70.0) to rm(2, 4, 60.0)),
                ),
            ),
            // #30 acumulación — más series a 70-80 %, tope 80 %.
            Triple(BlockGoal.ACCUMULATION, 1, "#30") to listOf(
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(6, 5, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 70.0) + rm(4, 3, 75.0),
                    rm(3, 5, 60.0),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(6, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(5, 3, 72.5),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(5, 4, 72.5) to
                        (rm(1, 5, 55.0) + rm(2, 3, 65.0) + rm(4, 3, 75.0) to rm(3, 4, 60.0)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(6, 4, 75.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 70.0) + rm(4, 3, 77.5),
                    rm(3, 4, 65.0),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(5, 3, 75.0),
                    rm(1, 5, 50.0) + rm(2, 4, 65.0) + rm(5, 3, 75.0),
                    rm(1, 5, 50.0) + rm(2, 4, 65.0) + rm(5, 4, 75.0) to
                        (rm(1, 4, 55.0) + rm(2, 3, 70.0) + rm(4, 3, 77.5) to rm(3, 4, 65.0)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(6, 4, 77.5),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(2, 3, 72.5) + rm(5, 3, 80.0),
                    rm(3, 4, 65.0),
                ) to Triple(
                    rm(1, 3, 50.0) + rm(2, 3, 65.0) + rm(5, 3, 77.5),
                    rm(1, 5, 50.0) + rm(2, 3, 65.0) + rm(5, 3, 77.5),
                    rm(1, 5, 50.0) + rm(2, 4, 65.0) + rm(5, 3, 77.5) to
                        (rm(1, 4, 60.0) + rm(2, 3, 70.0) + rm(4, 2, 80.0) to rm(3, 4, 65.0)),
                ),
                Triple(
                    rm(1, 5, 50.0) + rm(2, 5, 60.0) + rm(4, 5, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(4, 3, 70.0),
                    rm(2, 5, 60.0),
                ) to Triple(
                    rm(1, 4, 50.0) + rm(2, 4, 60.0) + rm(3, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(3, 3, 70.0),
                    rm(1, 5, 50.0) + rm(2, 4, 60.0) + rm(3, 4, 70.0) to
                        (rm(1, 5, 55.0) + rm(2, 3, 65.0) + rm(3, 3, 70.0) to rm(2, 4, 60.0)),
                ),
            ),
            // #31 transmutación — 80-90 %, menos series, 3-2 reps en el tope.
            Triple(BlockGoal.INTENSIFICATION, 2, "#31") to listOf(
                Triple(
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(4, 3, 80.0),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(2, 2, 80.0) + rm(4, 2, 85.0),
                    rm(2, 3, 70.0),
                ) to Triple(
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(4, 2, 80.0),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(4, 2, 82.5),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(4, 3, 80.0) to
                        (rm(1, 3, 65.0) + rm(2, 2, 75.0) + rm(3, 2, 85.0) to rm(2, 3, 70.0)),
                ),
                Triple(
                    rm(1, 3, 60.0) + rm(2, 3, 72.5) + rm(4, 2, 82.5),
                    rm(1, 3, 60.0) + rm(2, 2, 72.5) + rm(2, 2, 82.5) + rm(4, 2, 87.5),
                    rm(2, 2, 72.5),
                ) to Triple(
                    rm(1, 3, 60.0) + rm(2, 2, 72.5) + rm(4, 2, 82.5),
                    rm(1, 3, 60.0) + rm(2, 2, 75.0) + rm(4, 2, 85.0),
                    rm(1, 3, 60.0) + rm(2, 2, 72.5) + rm(4, 2, 82.5) to
                        (rm(1, 3, 65.0) + rm(2, 2, 77.5) + rm(3, 2, 87.5) to rm(2, 2, 72.5)),
                ),
                Triple(
                    rm(1, 2, 65.0) + rm(2, 2, 75.0) + rm(4, 2, 85.0),
                    rm(1, 2, 65.0) + rm(2, 2, 75.0) + rm(2, 1, 85.0) + rm(4, 1, 90.0),
                    rm(2, 2, 75.0),
                ) to Triple(
                    rm(1, 2, 65.0) + rm(2, 2, 75.0) + rm(3, 1, 85.0),
                    rm(1, 2, 65.0) + rm(2, 2, 75.0) + rm(4, 1, 87.5),
                    rm(1, 2, 65.0) + rm(2, 2, 75.0) + rm(4, 2, 85.0) to
                        (rm(1, 2, 70.0) + rm(2, 1, 80.0) + rm(3, 1, 90.0) to rm(2, 2, 75.0)),
                ),
                Triple(
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(3, 3, 80.0),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(3, 2, 80.0),
                    rm(2, 3, 70.0),
                ) to Triple(
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(3, 2, 80.0),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(3, 2, 80.0),
                    rm(1, 3, 60.0) + rm(2, 3, 70.0) + rm(3, 3, 80.0) to
                        (rm(1, 3, 65.0) + rm(2, 2, 75.0) + rm(3, 2, 82.5) to rm(2, 3, 70.0)),
                ),
            ),
            // #32 realización — singles 90-100 %, taper en la última semana. Nunca 3+ reps > 92 %.
            Triple(BlockGoal.PEAK, 3, "#32") to listOf(
                Triple(
                    rm(1, 2, 70.0) + rm(2, 2, 80.0) + rm(3, 1, 90.0),
                    rm(1, 2, 70.0) + rm(2, 1, 80.0) + rm(3, 1, 90.0),
                    rm(2, 2, 75.0),
                ) to Triple(
                    rm(1, 2, 70.0) + rm(2, 1, 80.0) + rm(2, 1, 90.0),
                    rm(1, 2, 70.0) + rm(3, 1, 87.5),
                    rm(1, 2, 70.0) + rm(2, 1, 80.0) + rm(3, 1, 90.0) to
                        (rm(1, 2, 70.0) + rm(3, 1, 90.0) to rm(2, 2, 75.0)),
                ),
                Triple(
                    rm(1, 1, 75.0) + rm(2, 1, 85.0) + rm(3, 1, 92.5),
                    rm(1, 1, 75.0) + rm(2, 1, 85.0) + rm(3, 1, 92.5),
                    rm(2, 1, 80.0),
                ) to Triple(
                    rm(1, 1, 75.0) + rm(2, 1, 85.0) + rm(2, 1, 92.5),
                    rm(1, 1, 75.0) + rm(3, 1, 90.0),
                    rm(1, 1, 75.0) + rm(2, 1, 85.0) + rm(3, 1, 92.5) to
                        (rm(1, 1, 75.0) + rm(3, 1, 92.5) to rm(2, 1, 80.0)),
                ),
                Triple(
                    rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 95.0) + rm(1, 1, 100.0),
                    rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 95.0) + rm(1, 1, 100.0),
                    rm(2, 1, 80.0),
                ) to Triple(
                    rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 95.0),
                    rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 95.0),
                    rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 95.0) + rm(1, 1, 100.0) to
                        (rm(1, 1, 80.0) + rm(1, 1, 90.0) + rm(1, 1, 100.0) to rm(2, 1, 75.0)),
                ),
                Triple(
                    rm(2, 3, 60.0) + rm(2, 2, 70.0),
                    rm(2, 3, 60.0) + rm(2, 2, 70.0),
                    rm(2, 3, 60.0),
                ) to Triple(
                    rm(2, 2, 60.0),
                    rm(2, 3, 60.0),
                    rm(2, 3, 60.0) + rm(2, 2, 70.0) to
                        (rm(2, 3, 60.0) to rm(2, 3, 60.0)),
                ),
            ),
        ).flatMapIndexed { phase, (header, weekTables) ->
            val (goal, block, name) = header
            weekTables.mapIndexed { weekIn, table ->
                val (mon, restOf) = table
                val (sq, bp, sq2) = mon
                val (dl, bpWed, friPack) = restOf
                val (sqFri, bpFriPack) = friPack
                val (bpFri, sqFri2) = bpFriPack
                val w = phase * 4 + weekIn + 1
                weekRecipe(
                    w, block, name, goal,
                    listOf(
                        monday(sq, bp, sq2),
                        wednesday(dl, bpWed, toKnees = phase == 1 && weekIn <= 1),
                        friday(sqFri, bpFri, sqFri2),
                    ),
                )
            }
        }
        protocol(
            "sheiko-29-32", "Sheiko #29–#32", "🇷🇺",
            "16 semanas, 3 días: #29 preparación, #30 volumen, #31 transmutación, #32 realización. El mismo levantamiento aparece dos veces.",
            "Boris Sheiko", listOf("powerlifting", "avanzado", "3 días", "16 semanas", "%"),
            listOf(
                ProtocolBlock("#29", 4, "Acumulación", 50, 80, 1.2),
                ProtocolBlock("#30", 4, "Acumulación", 70, 80, 1.5),
                ProtocolBlock("#31", 4, "Intensificación", 80, 90, 0.9),
                ProtocolBlock("#32", 4, "Peak", 85, 100, 0.5),
            ),
            "sheiko_3day",
            attributed("Sheiko programs #29-32", "https://www.powerliftingtowin.com/sheiko/", "Boris Sheiko"),
            TrainingPlanRecipe(
                "sheiko-29-32", weeks, 1.0, sbdSlots(), ProgressionRule.None, exemptions,
                claimedDaysPerWeek = 3, claimedLevel = "avanzado",
            ),
            ProtocolFidelitySpec(
                16, 3, requiresPercent = true, claimedLevel = "avanzado",
                percentAnchors = mapOf(
                    "w1_sq" to listOf(50.0, 60.0, 70.0),
                    "w1_bp" to listOf(50.0, 60.0, 70.0, 75.0),
                ),
            ),
            exemptions = exemptions,
        )
    }

    val smolov = run {
        val exemptions = listOf(
            RecipeCompositionExemption("H6", "S1", "Especialización 1-2 ejercicios"),
            RecipeCompositionExemption("H6", "S2", "Especialización 1-2 ejercicios"),
            RecipeCompositionExemption("H6", "S3", "Especialización 1-2 ejercicios"),
            RecipeCompositionExemption("H6", "S4", "Especialización 1-2 ejercicios"),
            RecipeCompositionExemption("H6", "Test", "Especialización 1-2 ejercicios"),
            RecipeCompositionExemption("W3", "w", "4 sentadillas pesadas/semana"),
            RecipeCompositionExemption("W6", "w", "Programa de especialización de sentadilla"),
        )
        fun squatDay(label: String, weekday: Int, sets: List<SetRecipe>, box: Boolean = false) = day(
            label, weekday = weekday, slots = listOf(
                slot(
                    "sq", SlotRole.T1_MAIN, if (box) CatalogIds.SQ_BOX else CatalogIds.SQ_LOW, sets,
                    if (sets.any { (it.percent ?: 0.0) >= 85 }) 240 else 180,
                    LiftSlot.SQUAT, isCompetitionLift = !box,
                    technique = if (box) TechniqueModifier.BOX else null,
                ),
                kpknAssist("pull", CatalogIds.LAT, 3, 10, 90),
                kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
            ),
        )
        fun n(count: Int, reps: Int, pct: Double) = List(count) {
            SetRecipe(reps = reps, percent = pct, loadBasis = LoadBasis.PERCENT_1RM)
        }
        fun baseDays() = listOf(
            squatDay("S1", 1, n(4, 9, 70.0)),
            squatDay("S2", 3, n(5, 7, 75.0)),
            squatDay("S3", 4, n(7, 5, 80.0)),
            squatDay("S4", 6, n(10, 3, 85.0)),
        )
        val weeks = buildList {
            add(
                weekRecipe(
                    1, 0, "Intro", BlockGoal.ACCUMULATION,
                    listOf(
                        squatDay("S1", 1, n(4, 9, 65.0)),
                        squatDay("S2", 3, n(5, 7, 70.0)),
                        squatDay("S3", 4, n(7, 5, 75.0)),
                        squatDay("S4", 6, n(10, 3, 80.0)),
                    ),
                ),
            )
            add(
                weekRecipe(
                    2, 0, "Intro", BlockGoal.ACCUMULATION,
                    listOf(
                        squatDay("S1", 1, n(4, 9, 67.0)),
                        squatDay("S2", 3, n(5, 7, 72.0)),
                        squatDay("S3", 4, n(7, 5, 77.0)),
                        squatDay("S4", 6, n(10, 3, 82.0)),
                    ),
                ),
            )
            repeat(3) { i ->
                add(weekRecipe(3 + i, 1, "Base", BlockGoal.ACCUMULATION, baseDays()))
            }
            add(
                weekRecipe(
                    6, 1, "Base", BlockGoal.ACCUMULATION,
                    listOf(
                        squatDay("S1", 1, n(3, 5, 70.0)),
                        squatDay("S2", 3, n(3, 3, 75.0)),
                        squatDay("S3", 4, n(2, 2, 80.0)),
                        squatDay("Test", 6, listOf(SetRecipe(reps = 1, percent = 100.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_1RM))),
                    ),
                ),
            )
            repeat(2) { i ->
                add(
                    weekRecipe(
                        7 + i, 2, "Switching", BlockGoal.INTENSIFICATION,
                        listOf(
                            squatDay("Cajón", 1, n(6, 2, 50.0 + i * 2), box = true),
                            squatDay("Cajón 2", 3, n(6, 2, 55.0 + i * 2), box = true),
                            squatDay("Cajón 3", 4, n(5, 2, 58.0 + i * 2), box = true),
                            squatDay("Cajón 4", 6, n(4, 2, 60.0 + i), box = true),
                        ),
                    ),
                )
            }
            val intense = listOf(
                listOf(
                    n(1, 3, 65.0) + n(1, 4, 75.0) + n(4, 3, 85.0) + n(1, 5, 90.0),
                    n(1, 3, 60.0) + n(1, 3, 70.0) + n(1, 4, 80.0) + n(1, 3, 90.0) + n(5, 2, 85.0),
                    n(1, 4, 65.0) + n(1, 4, 70.0) + n(4, 5, 80.0),
                ),
                listOf(
                    n(1, 3, 70.0) + n(1, 4, 80.0) + n(4, 3, 87.5) + n(1, 2, 92.5),
                    n(1, 3, 65.0) + n(1, 3, 75.0) + n(1, 3, 85.0) + n(1, 2, 92.5) + n(4, 2, 87.5),
                    n(1, 3, 70.0) + n(1, 3, 80.0) + n(4, 3, 85.0),
                ),
                listOf(
                    n(1, 2, 75.0) + n(1, 3, 85.0) + n(4, 2, 90.0) + n(1, 2, 95.0),
                    n(1, 2, 70.0) + n(1, 2, 80.0) + n(1, 2, 90.0) + n(3, 2, 92.5),
                    n(1, 3, 75.0) + n(1, 3, 85.0) + n(3, 3, 90.0),
                ),
                listOf(
                    n(4, 2, 90.0) + n(1, 1, 95.0),
                    n(3, 2, 90.0),
                    n(3, 2, 90.0),
                ),
            )
            intense.forEachIndexed { i, daysSets ->
                add(
                    weekRecipe(
                        9 + i, 3, "Intenso", BlockGoal.PEAK,
                        daysSets.mapIndexed { di, sets -> squatDay("S${di + 1}", listOf(1, 3, 5)[di], sets) },
                    ),
                )
            }
            add(
                weekRecipe(
                    13, 4, "Taper", BlockGoal.TAPER,
                    listOf(
                        squatDay("Opener", 1, n(2, 1, 90.0)),
                        squatDay("Test", 4, listOf(SetRecipe(reps = 1, percent = 100.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_1RM))),
                        squatDay("Movilidad", 6, n(2, 5, 60.0)),
                    ),
                ),
            )
        }
        protocol(
            "smolov", "Smolov", "🔥",
            "13 semanas de especialización de sentadilla: intro, base 4×9@70…, switching, intenso, taper/test.",
            "Sergey Smolov", listOf("powerlifting", "avanzado", "especialización", "13 semanas", "%"),
            listOf(
                ProtocolBlock("Intro", 2, "Acumulación", 60, 80),
                ProtocolBlock("Base", 4, "Acumulación", 70, 85, 1.5),
                ProtocolBlock("Switching", 2, "Intensificación", 50, 65),
                ProtocolBlock("Intenso", 4, "Peak", 85, 95),
                ProtocolBlock("Taper", 1, "Taper", 60, 100),
            ),
            "smolov_base",
            attributed("Smolov squat cycle", "https://www.powerliftingtowin.com/smolov/", "Sergey Smolov"),
            TrainingPlanRecipe(
                "smolov", weeks, 1.0, mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW),
                ProgressionRule.WeeklyKg(mapOf(4 to 10.0, 5 to 15.0)), exemptions,
                claimedDaysPerWeek = 4, claimedLevel = "avanzado",
            ),
            ProtocolFidelitySpec(
                13, 4, requiresPercent = true, claimedLevel = "avanzado",
                percentAnchors = mapOf("base" to listOf(70.0, 75.0, 80.0, 85.0)),
            ),
            exemptions = exemptions,
        )
    }

    val smolovJr = smolov.copy(
        id = "smolov-jr",
        name = "Smolov Jr",
        description = "3 semanas, 4 días: 6×6@70, 7×5@75, 8×4@80, 10×3@85, +2,5–5 kg/sem. Especialización SQ o BP.",
        tags = listOf("powerlifting", "avanzado", "especialización", "3 semanas", "%"),
        blocks = listOf(ProtocolBlock("Smolov Jr", 3, "Intensificación", 70, 90, 1.5)),
        recipe = TrainingPlanRecipe(
            id = "smolov-jr",
            weeks = (1..3).map { w ->
                weekRecipe(
                    w, 0, "Jr", BlockGoal.INTENSIFICATION,
                    listOf(
                        Triple(6, 6, 70.0) to 1,
                        Triple(7, 5, 75.0) to 3,
                        Triple(8, 4, 80.0) to 4,
                        Triple(10, 3, 85.0) to 6,
                    ).map { (scheme, weekday) ->
                        val (sets, reps, load) = scheme
                        day(
                            "Sesión", weekday = weekday, slots = listOf(
                                slot(
                                    "sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW,
                                    List(sets) { SetRecipe(reps = reps, percent = load, loadBasis = LoadBasis.PERCENT_1RM) },
                                    if (load >= 85) 240 else 180, LiftSlot.SQUAT, isCompetitionLift = true,
                                ),
                                kpknAssist("lat", CatalogIds.LAT, 3, 10, 90),
                                kpknAssist("face", CatalogIds.FACE, 3, 15, 60),
                            ),
                        )
                    },
                )
            },
            trainingMaxPercent = 1.0,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW),
            progression = ProgressionRule.WeeklyKg(mapOf(2 to 5.0, 3 to 10.0)),
            exemptions = smolov.exemptions,
            claimedDaysPerWeek = 4,
            claimedLevel = "avanzado",
        ),
        fidelitySpec = ProtocolFidelitySpec(
            3, 4, requiresPercent = true, claimedLevel = "avanzado",
            percentAnchors = mapOf("jr" to listOf(70.0, 75.0, 80.0, 85.0)),
        ),
        exemptions = smolov.exemptions,
    )

    val candito = protocol(
        "candito-6", "Candito 6-Week", "📘",
        "6 semanas upper/lower: semana 1 de acondicionamiento (5 sesiones), hipertrofia, linear max OT, aclimatación, fuerza, descarga/test.",
        "Jonnie Candito", listOf("powerlifting", "intermedio", "4 días", "6 semanas", "%"),
        listOf(ProtocolBlock("Acondicionamiento", 1, "Acumulación", 65, 75), ProtocolBlock("Hipertrofia", 1, "Acumulación", 65, 75), ProtocolBlock("Linear", 1, "Intensificación", 80, 88), ProtocolBlock("Aclimatación", 1, "Intensificación", 85, 95), ProtocolBlock("Fuerza", 1, "Peak", 90, 97), ProtocolBlock("Test", 1, "Taper", 60, 100)),
        "pl_classic_4", attributed("Candito 6 Week Strength Program", "https://www.canditotraininghq.com/", "Jonnie Candito"),
        TrainingPlanRecipe(
            id = "candito-6",
            weeks = buildList {
                add(
                    weekRecipe(
                        1, 0, "Acond", BlockGoal.ACCUMULATION,
                        listOf(
                            DayArchetypes.plSquat(70.0, t1Sets = 4, t1Reps = 6, weekday = 1, label = "Lower A"),
                            DayArchetypes.plBenchHeavy(70.0, t1Sets = 4, t1Reps = 6, weekday = 2, label = "Upper A"),
                            DayArchetypes.plDeadlift(70.0, t1Sets = 4, t1Reps = 8, weekday = 3, label = "Lower B"),
                            DayArchetypes.plBenchVolume(65.0, weekday = 4, label = "Upper B"),
                            DayArchetypes.plSquat(65.0, t1Sets = 3, t1Reps = 6, weekday = 5, label = "Lower C").dropT3(1),
                        ),
                    ),
                )
                listOf(
                    Triple(BlockGoal.ACCUMULATION, "Hiper", Triple(Triple(4, 8, 67.5), Triple(4, 8, 67.5), Triple(3, 8, 65.0))),
                    Triple(BlockGoal.INTENSIFICATION, "Linear", Triple(Triple(5, 3, 85.0), Triple(5, 3, 85.0), Triple(3, 3, 82.5))),
                    Triple(BlockGoal.INTENSIFICATION, "Aclim", Triple(Triple(3, 2, 90.0), Triple(3, 2, 90.0), Triple(2, 2, 87.5))),
                    Triple(BlockGoal.PEAK, "Fuerza", Triple(Triple(2, 2, 95.0), Triple(2, 2, 95.0), Triple(2, 1, 97.5))),
                    Triple(BlockGoal.TAPER, "Test", Triple(Triple(2, 1, 90.0), Triple(2, 1, 90.0), Triple(1, 1, 80.0))),
                ).forEachIndexed { i, (goal, name, lifts) ->
                    val (sq, bp, dl) = lifts
                    val drop = if (goal == BlockGoal.PEAK || goal == BlockGoal.TAPER) 2 else 0
                    add(
                        weekRecipe(i + 2, i + 1, name, goal, listOf(
                            DayArchetypes.plSquat(sq.third, t1Sets = sq.first, t1Reps = sq.second, weekday = 1).dropT3(drop),
                            DayArchetypes.plBenchHeavy(bp.third, t1Sets = bp.first, t1Reps = bp.second, weekday = 2).dropT3(drop),
                            DayArchetypes.plDeadlift(dl.third, t1Sets = dl.first, t1Reps = dl.second, weekday = 4).dropT3(drop),
                            DayArchetypes.plBenchVolume((bp.third - 10).coerceAtLeast(60.0), weekday = 5).dropT3(drop),
                        )),
                    )
                }
            },
            trainingMaxPercent = 1.0, liftSlots = sbdSlots(), claimedDaysPerWeek = 4, claimedLevel = "intermedio",
        ),
        ProtocolFidelitySpec(6, 4, requiresPercent = true, claimedLevel = "intermedio", percentAnchors = mapOf("w1" to listOf(70.0), "w3" to listOf(85.0))),
    )

    val coan = protocol(
        "coan-phillipi-dl", "Coan-Phillipi Deadlift", "💀",
        "10 semanas, 1 día: pesado + velocidad + circuito. Add-on de peso muerto.",
        "Ed Coan / Mark Philippi", listOf("powerlifting", "avanzado", "especialización", "10 semanas", "%"),
        listOf(ProtocolBlock("Pesado", 10, "Intensificación", 60, 100)),
        "coan_split", attributed("Coan-Phillipi deadlift routine", "https://www.powerliftingtowin.com/coan-phillipi-deadlift-routine/", "Ed Coan / Mark Philippi"),
        run {
            val heavy = listOf(75.0, 80.0, 85.0, 90.0, 80.0, 85.0, 90.0, 95.0, 97.5, 100.0)
            val speed = listOf(60.0, 65.0, 70.0, 75.0, 65.0, 70.0, 75.0, 70.0, 70.0, 60.0)
            val weeks = heavy.mapIndexed { i, pct ->
                weekRecipe(i + 1, 0, "DL", if (i >= 8) BlockGoal.PEAK else BlockGoal.INTENSIFICATION, listOf(
                    day("Peso muerto", weekday = 4, priority = SlotPriority.SPEED, slots = listOf(
                        slot("speed", SlotRole.SPEED, CatalogIds.DL, repeatPercentSets(listOf(8, 8, 6, 5, 3, 3, 3, 3, 2, 2)[i], 3, speed[i], 90), 90, LiftSlot.DEADLIFT, technique = TechniqueModifier.SPEED, supplementalOf = "dl", priority = SlotPriority.SPEED),
                        slot("dl", SlotRole.T1_MAIN, CatalogIds.DL, listOf(SetRecipe(reps = if (pct >= 97) 1 else if (i == 4) 3 else 2, percent = pct, isTopSet = pct >= 95, loadBasis = LoadBasis.PERCENT_DESIRED_MAX)).let { if (i == 4) List(3) { SetRecipe(reps = 3, percent = 80.0, loadBasis = LoadBasis.PERCENT_DESIRED_MAX) } else it }, 240, LiftSlot.DEADLIFT, isCompetitionLift = true),
                        slot("sldl", SlotRole.T3_ACCESSORY, CatalogIds.SLDL, rpeSets(2, 8, 8.0), 90),
                        slot("row", SlotRole.T3_ACCESSORY, CatalogIds.ROW, rpeSets(3, 8, 8.0), 90),
                        slot("lat", SlotRole.T3_ACCESSORY, CatalogIds.LAT, rpeSets(3, 10, 8.0), 90),
                        slot("gm", SlotRole.T3_ACCESSORY, CatalogIds.GM, rpeSets(3, 8, 8.0), 90),
                    )),
                ))
            }
            TrainingPlanRecipe("coan-phillipi-dl", weeks, 1.0, mapOf(LiftSlot.DEADLIFT to CatalogIds.DL), ProgressionRule.None, listOf(RecipeCompositionExemption("H2", "Peso muerto", "DL + speed + SLDL + good morning"), RecipeCompositionExemption("H3", "Peso muerto", "Cuatro bisagras por diseño")), claimedDaysPerWeek = 1, claimedLevel = "avanzado")
        },
        ProtocolFidelitySpec(10, 1, requiresPercent = true, claimedLevel = "avanzado", percentAnchors = mapOf("w10" to listOf(100.0))),
        exemptions = listOf(RecipeCompositionExemption("H2", "Peso muerto", "DL + speed + SLDL + good morning"), RecipeCompositionExemption("H3", "Peso muerto", "Cuatro bisagras por diseño")),
    )
}
