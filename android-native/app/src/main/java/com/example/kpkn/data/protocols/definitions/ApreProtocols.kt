package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolFidelitySpec
import com.example.kpkn.data.protocols.ProtocolKind
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.attributed
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe

/**
 * APRE 3 / 6 / 10 (CAL6): Autoregulatory Progressive Resistance Exercise, de Bryan Mann (RES1 §2, fuente primaria: *Understanding APRE*,
 * Training & Conditioning, 2017; Mann et al., J Strength Cond Res 2010).
 *
 * Cada levantamiento hace cuatro series: 50 % y 75 % de su XRM (el máximo de repeticiones de la fase) y dos series al máximo con el XRM.
 * Las repeticiones de la serie 3 fijan la carga de la serie 4 de esa sesión y las de la serie 4, el XRM de la semana siguiente, con la
 * tabla de Mann (`Apre`, dominio). Las repeticiones con mala técnica no cuentan. Mann periodiza las fases: hipertrofia con APRE 10, fuerza
 * con APRE 6 y fuerza máxima y potencia con APRE 3; aquí son tres bloques de cuatro semanas.
 *
 * El XRM sale del 1RM con la equivalencia de Mann (10RM = 75 %, 6RM = 83 %, 3RM = 92 %). El TM de la receta es el 92 % del 1RM, es decir el
 * 3RM, y las series van en porcentaje de ese TM: así el XRM de cada fase es 81,5 %, 90,2 % y 100 % del TM. La regla `ApreXrm` propone el XRM
 * nuevo como un cambio del TM, que respeta OFF, PROPONER y AUTO, y la semana siguiente parte de él.
 */
object ApreProtocols {
    private const val MANN_URL = "https://training-conditioning.com/article/understanding-apre-part-1/"

    /** El TM de la receta: el 92 % del 1RM (el 3RM de Mann). */
    private const val TM_PERCENT = 0.92

    private data class Phase(
        val index: Int,
        val name: String,
        val goal: BlockGoal,
        val xrmReps: Int,
        val oneRmFraction: Double,
        val set1Reps: Int,
        val set2Reps: Int,
    )

    private val phases = listOf(
        Phase(0, "APRE 10", BlockGoal.ACCUMULATION, xrmReps = 10, oneRmFraction = 0.75, set1Reps = 12, set2Reps = 10),
        Phase(1, "APRE 6", BlockGoal.INTENSIFICATION, xrmReps = 6, oneRmFraction = 0.83, set1Reps = 10, set2Reps = 6),
        Phase(2, "APRE 3", BlockGoal.INTENSIFICATION, xrmReps = 3, oneRmFraction = 0.92, set1Reps = 6, set2Reps = 3),
    )

    /** Las cuatro series de una fase, en porcentaje del TM: 50 % y 75 % del XRM y dos series al máximo con el XRM (AMRAP, mínimo = el XRM). */
    private fun apreSets(phase: Phase): List<SetRecipe> {
        val xrmPercent = phase.oneRmFraction / TM_PERCENT * 100.0
        return listOf(
            SetRecipe(reps = phase.set1Reps, percent = xrmPercent * 0.50),
            SetRecipe(reps = phase.set2Reps, percent = xrmPercent * 0.75),
            SetRecipe(reps = phase.xrmReps, percent = xrmPercent, amrap = true),
            SetRecipe(reps = phase.xrmReps, percent = xrmPercent, amrap = true),
        )
    }

    private fun apreDay(label: String, weekday: Int, lift: LiftSlot, configurationId: String, phase: Phase, accessories: List<SlotRecipe>): DayRecipe =
        day(
            label,
            weekday = weekday,
            slots = listOf(
                slot("t1", SlotRole.T1_MAIN, configurationId, apreSets(phase), 240, lift, isCompetitionLift = true),
            ) + accessories,
        )

    fun recipe(): TrainingPlanRecipe {
        val weeks = (1..12).map { w ->
            val phase = phases[(w - 1) / 4]
            weekRecipe(
                w, phase.index, phase.name, phase.goal,
                listOf(
                    apreDay(
                        "Sentadilla", 1, LiftSlot.SQUAT, CatalogIds.SQ_LOW, phase,
                        listOf(kpknAssist("ghr", CatalogIds.GHR, 3, 8, 90), kpknAssist("pallof", CatalogIds.PALLOF, 3, 10, 60)),
                    ),
                    apreDay(
                        "Banca", 3, LiftSlot.BENCH, CatalogIds.BP, phase,
                        listOf(kpknAssist("row", CatalogIds.PENDLAY, 3, 8, 120), kpknAssist("face", CatalogIds.FACE, 3, 15, 60)),
                    ),
                    // W6 del contrato: en powerlifting la banca se trabaja en dos días distintos; el segundo es un press inclinado ligero que
                    // sugiere KPKN (Mann no prescribe accesorios).
                    apreDay(
                        "Peso muerto", 5, LiftSlot.DEADLIFT, CatalogIds.DL, phase,
                        listOf(
                            kpknAssist("inc", CatalogIds.BP_INC, 3, 8, 90),
                            kpknAssist("row", CatalogIds.ROW, 3, 8, 120),
                            kpknAssist("wheel", CatalogIds.WHEEL, 3, 8, 60),
                        ),
                    ),
                ),
                weekName = "Semana $w",
            )
        }
        return TrainingPlanRecipe(
            id = "apre-mann",
            weeks = weeks,
            trainingMaxPercent = TM_PERCENT,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
            progression = ProgressionRule.ApreXrm(incrementKg = 2.5),
            // Mann no programa semanas de descarga: cada semana la serie 4 reajusta el XRM a cómo estás.
            exemptions = listOf(
                RecipeCompositionExemption(
                    "C5_DELOAD_REQUIRED", "recipe",
                    "APRE de Mann son 12 semanas sin descarga programada: el XRM de cada semana sale de las repeticiones de la serie 4 de la anterior y " +
                        "así la carga se ajusta a cómo está la persona; el método no programa una semana de descarga aparte",
                    MANN_URL,
                ),
            ),
            autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
            claimedDaysPerWeek = 3,
            claimedLevel = "intermedio",
            contentVersion = 1,
        )
    }

    val definition = Protocol(
        id = "apre-mann",
        name = "APRE de Mann",
        emoji = "🎯",
        description = "12 semanas, 3 días: sentadilla, banca y peso muerto con cuatro series (50 % y 75 % del XRM y dos al máximo). Las repeticiones de la " +
            "serie 3 fijan la carga de la serie 4 y las de la serie 4, el XRM de la semana siguiente, con la tabla de Mann; las repeticiones con mala " +
            "técnica no cuentan. Tres fases de cuatro semanas: APRE 10 (10RM = 75 % del 1RM), APRE 6 (6RM = 83 %) y APRE 3 (3RM = 92 %).",
        author = "Bryan Mann",
        tags = listOf("powerlifting", "intermedio", "3 días", "12 semanas", "AMRAP", "%"),
        blocks = listOf(
            ProtocolBlock("APRE 10", 4, "Acumulación", 40, 75),
            ProtocolBlock("APRE 6", 4, "Intensificación", 40, 83),
            ProtocolBlock("APRE 3", 4, "Intensificación", 45, 92),
        ),
        defaultSplit = "pl_sbd_x3",
        publicationStatus = ProtocolPublicationStatus.VERIFIED,
        kind = ProtocolKind.AUTOREGULATED_FRAMEWORK,
        source = attributed("Understanding APRE (Training & Conditioning)", MANN_URL, "Bryan Mann"),
        recipe = recipe(),
        // En APRE 3 el XRM (el 3RM de Mann) es el TM de la receta: las series van al 50, 75 y 100 % del XRM.
        fidelitySpec = ProtocolFidelitySpec(
            12, 3, requiresAmrap = true, requiresPercent = true, claimedLevel = "intermedio",
            percentAnchors = mapOf("apre3" to listOf(50.0, 75.0, 100.0)),
        ),
    )
}
