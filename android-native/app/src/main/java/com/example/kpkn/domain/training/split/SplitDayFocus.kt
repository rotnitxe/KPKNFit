package com.example.kpkn.domain.training.split

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.splits.SplitDayDefinition
import java.util.EnumMap

/**
 * Qué músculos acoge un día de un reparto. Sale de su etiqueta («Torso», «Empuje», «Pecho/Espalda», «Cadena anterior»…)
 * y de sus `foci` (SQUAT, BENCH, DEADLIFT en los repartos de powerlifting).
 *
 * [weights] da, por músculo canónico, cuánto le gusta ese músculo al día (0 = no entra, 1 = es su foco). Un día cuya
 * etiqueta no dice nada («Día Volumen (5x5)», «Sesión 1») acoge todo ([isGeneric]).
 */
internal data class DayFocus(
    val label: String,
    val weights: Map<KpknMuscleGroup, Double>,
    /** Grupos (empuje, tirón, pierna) que el día acoge por completo: sirve para exigir que un día de torso lleve de ambos. */
    val acceptedGroups: Set<SplitGroup>,
    val foci: List<String>,
    /** Cadena cinética que pide el día («anterior» o «posterior»); null si la etiqueta no habla de cadenas. */
    val chain: KineticChain?,
    /**
     * Encaje mínimo que el día admite de cualquier músculo cuando su etiqueta lo dice («Torso/Full Body», «Sentadilla/
     * Accesorios»); 0 si no dice nada. Un ejercicio con ese encaje es elegible aunque haya un día donde encaje mejor.
     */
    val floor: Double,
    /** Día «liviano» o de recuperación por su etiqueta (no cambia el reparto de músculos). */
    val isLight: Boolean,
    val isGeneric: Boolean,
) {
    fun weight(atom: KpknMuscleGroup): Double = weights[atom] ?: 0.0
}

/** Traduce la etiqueta y los `foci` de un día de reparto a un [DayFocus]. Tablas puras; mismo resultado siempre. */
internal object DayFocusParser {

    private fun table(vararg pairs: Pair<KpknMuscleGroup, Double>): Map<KpknMuscleGroup, Double> = pairs.toMap()

    private val PUSH = table(
        KpknMuscleGroup.CHEST to 1.0, KpknMuscleGroup.DELT_FRONT to 1.0, KpknMuscleGroup.DELT_LATERAL to 1.0,
        KpknMuscleGroup.TRICEPS to 1.0, KpknMuscleGroup.CORE to 0.5,
    )
    private val PULL = table(
        KpknMuscleGroup.BACK_LATS to 1.0, KpknMuscleGroup.BACK_UPPER to 1.0, KpknMuscleGroup.DELT_REAR to 1.0,
        KpknMuscleGroup.BICEPS to 1.0, KpknMuscleGroup.FOREARMS to 0.8, KpknMuscleGroup.ERECTORS to 0.3,
        KpknMuscleGroup.CORE to 0.5,
    )
    private val LEGS = table(
        KpknMuscleGroup.QUADS to 1.0, KpknMuscleGroup.HAMS to 1.0, KpknMuscleGroup.GLUTES to 1.0,
        KpknMuscleGroup.CALVES to 1.0, KpknMuscleGroup.ADDUCTORS to 1.0, KpknMuscleGroup.ERECTORS to 0.6,
        KpknMuscleGroup.CORE to 0.5,
    )
    private val UPPER = mergeMax(PUSH, PULL, table(KpknMuscleGroup.NECK to 0.5))
    private val ANTERIOR = table(
        KpknMuscleGroup.CHEST to 1.0, KpknMuscleGroup.DELT_FRONT to 1.0, KpknMuscleGroup.DELT_LATERAL to 0.6,
        KpknMuscleGroup.QUADS to 1.0, KpknMuscleGroup.CORE to 1.0, KpknMuscleGroup.BICEPS to 1.0,
        KpknMuscleGroup.TRICEPS to 0.4, KpknMuscleGroup.NECK to 0.5,
    )
    private val POSTERIOR = table(
        KpknMuscleGroup.BACK_LATS to 1.0, KpknMuscleGroup.BACK_UPPER to 1.0, KpknMuscleGroup.DELT_REAR to 1.0,
        KpknMuscleGroup.HAMS to 1.0, KpknMuscleGroup.GLUTES to 1.0, KpknMuscleGroup.CALVES to 1.0,
        KpknMuscleGroup.ERECTORS to 1.0, KpknMuscleGroup.ADDUCTORS to 1.0, KpknMuscleGroup.TRICEPS to 1.0,
        KpknMuscleGroup.BICEPS to 0.4, KpknMuscleGroup.FOREARMS to 0.8,
    )
    private val CHEST_DAY = table(
        KpknMuscleGroup.CHEST to 1.0, KpknMuscleGroup.TRICEPS to 0.5, KpknMuscleGroup.DELT_FRONT to 0.6,
    )
    private val BACK_DAY = table(
        KpknMuscleGroup.BACK_LATS to 1.0, KpknMuscleGroup.BACK_UPPER to 1.0, KpknMuscleGroup.DELT_REAR to 0.7,
        KpknMuscleGroup.BICEPS to 0.5, KpknMuscleGroup.ERECTORS to 0.5, KpknMuscleGroup.FOREARMS to 0.4,
    )
    private val SHOULDER_DAY = table(
        KpknMuscleGroup.DELT_FRONT to 1.0, KpknMuscleGroup.DELT_LATERAL to 1.0, KpknMuscleGroup.DELT_REAR to 0.9,
        KpknMuscleGroup.BACK_UPPER to 0.4,
    )
    private val ARMS_DAY = table(
        KpknMuscleGroup.BICEPS to 1.0, KpknMuscleGroup.TRICEPS to 1.0, KpknMuscleGroup.FOREARMS to 1.0,
    )
    private val ABS_DAY = table(KpknMuscleGroup.CORE to 1.0)
    private val GLUTE_DAY = table(KpknMuscleGroup.GLUTES to 1.0, KpknMuscleGroup.HAMS to 0.5, KpknMuscleGroup.ADDUCTORS to 0.5)
    private val QUAD_DAY = table(KpknMuscleGroup.QUADS to 1.0, KpknMuscleGroup.GLUTES to 0.5, KpknMuscleGroup.CALVES to 0.4)
    private val HAM_DAY = table(KpknMuscleGroup.HAMS to 1.0, KpknMuscleGroup.GLUTES to 0.6, KpknMuscleGroup.CALVES to 0.4)

    // Levantamientos de powerlifting: no pide solo el ejercicio, también los músculos que lo apoyan.
    private val SQUAT_LIFT = table(
        KpknMuscleGroup.QUADS to 1.0, KpknMuscleGroup.GLUTES to 0.9, KpknMuscleGroup.ADDUCTORS to 0.6,
        KpknMuscleGroup.ERECTORS to 0.5, KpknMuscleGroup.CORE to 0.5, KpknMuscleGroup.HAMS to 0.4,
        KpknMuscleGroup.CALVES to 0.3,
    )
    private val BENCH_LIFT = table(
        KpknMuscleGroup.CHEST to 1.0, KpknMuscleGroup.TRICEPS to 0.9, KpknMuscleGroup.DELT_FRONT to 0.9,
        KpknMuscleGroup.DELT_LATERAL to 0.4, KpknMuscleGroup.CORE to 0.3,
    )
    private val DEADLIFT_LIFT = table(
        KpknMuscleGroup.HAMS to 1.0, KpknMuscleGroup.GLUTES to 1.0, KpknMuscleGroup.ERECTORS to 1.0,
        KpknMuscleGroup.BACK_UPPER to 0.8, KpknMuscleGroup.FOREARMS to 0.7, KpknMuscleGroup.BACK_LATS to 0.6,
        KpknMuscleGroup.CORE to 0.5, KpknMuscleGroup.QUADS to 0.4,
    )
    private val OVERHEAD_LIFT = table(
        KpknMuscleGroup.DELT_FRONT to 1.0, KpknMuscleGroup.TRICEPS to 0.8, KpknMuscleGroup.DELT_LATERAL to 0.8,
        KpknMuscleGroup.CHEST to 0.4, KpknMuscleGroup.CORE to 0.4,
    )

    private val ALL = KpknMuscleGroup.entries.associateWith { 1.0 }

    private const val FULL_FLOOR = 0.85
    private const val ACCESSORY_FLOOR = 0.6
    private const val GROUP_ACCEPTANCE = 0.75

    fun parse(definition: SplitDayDefinition): DayFocus = parse(definition.label, definition.foci)

    fun parse(label: String, foci: List<String>): DayFocus {
        val normalized = SplitText.normalize(label + " " + foci.joinToString(" "))
        val words = normalized.split(' ').filter { it.isNotEmpty() }

        fun has(vararg stems: String) = words.any { word -> stems.any { stem -> word == stem || word.startsWith(stem) } }
        fun hasExact(vararg exact: String) = words.any { it in exact }

        val parts = mutableListOf<Map<KpknMuscleGroup, Double>>()
        if (has("empuje", "push")) parts += PUSH
        if (has("tiron", "pull", "traccion", "jalon")) parts += PULL
        if (has("pierna", "lower", "inferior")) parts += LEGS
        if (has("torso", "upper", "superior")) parts += UPPER
        if (has("anterior")) parts += ANTERIOR
        if (has("posterior")) parts += POSTERIOR
        if (has("pecho")) parts += CHEST_DAY
        if (has("espalda")) parts += BACK_DAY
        if (has("hombro")) parts += SHOULDER_DAY
        if (has("brazo")) parts += ARMS_DAY
        if (hasExact("abs", "core") || has("abdomen", "abdominal")) parts += ABS_DAY
        if (has("gluteo")) parts += GLUTE_DAY
        if (has("cuadriceps")) parts += QUAD_DAY
        if (has("isquio", "femoral")) parts += HAM_DAY
        if (has("sentadilla", "squat")) parts += SQUAT_LIFT
        if (normalized.contains("peso muerto") || has("deadlift") || hasExact("dl")) parts += DEADLIFT_LIFT
        if (has("banca", "bench")) parts += BENCH_LIFT
        if (has("militar") || hasExact("ohp")) parts += OVERHEAD_LIFT
        if (hasExact("sbd")) parts += listOf(SQUAT_LIFT, BENCH_LIFT, DEADLIFT_LIFT)

        val full = has("full", "fullbody") || (has("cuerpo") && has("completo"))
        val accessories = has("accesorio")
        val isLight = has("liviano", "light", "recuperacion", "recovery", "mantenimiento", "pump", "tecnica")

        val specific = mergeMax(*parts.toTypedArray())
        val generic = specific.isEmpty() && !full && !accessories
        val floor = when {
            specific.isEmpty() -> 0.0
            full -> FULL_FLOOR
            accessories -> ACCESSORY_FLOOR
            else -> 0.0
        }
        // Sin músculos nombrados (una etiqueta vacía, «Cuerpo Completo», «Accesorios») el día acoge todo.
        val weights: Map<KpknMuscleGroup, Double> = when {
            specific.isEmpty() -> ALL
            floor <= 0.0 -> specific
            else -> KpknMuscleGroup.entries.associateWith { atom -> maxOf(specific[atom] ?: 0.0, floor) }
        }
        val wantsAnterior = has("anterior")
        val wantsPosterior = has("posterior")
        return DayFocus(
            label = label,
            weights = weights,
            acceptedGroups = acceptedGroupsOf(weights),
            foci = foci,
            chain = when {
                wantsAnterior && !wantsPosterior -> KineticChain.ANTERIOR
                wantsPosterior && !wantsAnterior -> KineticChain.POSTERIOR
                else -> null
            },
            floor = floor,
            isLight = isLight,
            isGeneric = generic,
        )
    }

    private fun acceptedGroupsOf(weights: Map<KpknMuscleGroup, Double>): Set<SplitGroup> {
        fun mean(vararg atoms: KpknMuscleGroup) = atoms.sumOf { weights[it] ?: 0.0 } / atoms.size
        val accepted = linkedSetOf<SplitGroup>()
        if (mean(KpknMuscleGroup.CHEST, KpknMuscleGroup.DELT_FRONT, KpknMuscleGroup.TRICEPS) >= GROUP_ACCEPTANCE) {
            accepted += SplitGroup.PUSH
        }
        if (mean(KpknMuscleGroup.BACK_LATS, KpknMuscleGroup.BACK_UPPER, KpknMuscleGroup.BICEPS) >= GROUP_ACCEPTANCE) {
            accepted += SplitGroup.PULL
        }
        if (mean(KpknMuscleGroup.QUADS, KpknMuscleGroup.HAMS, KpknMuscleGroup.GLUTES) >= GROUP_ACCEPTANCE) {
            accepted += SplitGroup.LEGS
        }
        return accepted
    }

    private fun mergeMax(vararg tables: Map<KpknMuscleGroup, Double>): Map<KpknMuscleGroup, Double> {
        val merged = EnumMap<KpknMuscleGroup, Double>(KpknMuscleGroup::class.java)
        tables.forEach { table -> table.forEach { (atom, weight) -> merged.merge(atom, weight) { a, b -> maxOf(a, b) } } }
        return merged
    }
}

/** Qué tanto encaja un ejercicio con un día: 0 (no tiene nada que ver) a 1 (es justo lo que el día pide). */
internal object SplitAffinity {

    /** Afinidad de un ejercicio sin músculos conocidos: no tiene día natural, cualquiera le sirve. */
    const val NEUTRAL = 0.5

    /** Cuánto pesan los músculos primarios frente a los secundarios. */
    private const val PRIMARY_SHARE = 0.8

    /**
     * Encaje de un ejercicio con músculos [muscles] y cadena cinética [chain] en el día [focus]. Un día «anterior» o
     * «posterior» también mira la cadena del catálogo (una sentadilla es de cadena anterior aunque trabaje el glúteo): el
     * encaje es el mayor entre el de los músculos y el de la cadena.
     */
    fun of(muscles: Map<KpknMuscleGroup, Double>, focus: DayFocus, chain: KineticChain? = null): Double {
        if (muscles.isEmpty()) return NEUTRAL
        val byMuscles = byMuscles(muscles, focus)
        val wanted = focus.chain ?: return byMuscles
        val byChain = when (chain) {
            null -> 0.0
            KineticChain.FULL -> 0.5
            wanted -> 1.0
            else -> 0.0
        }
        return maxOf(byMuscles, byChain)
    }

    private fun byMuscles(muscles: Map<KpknMuscleGroup, Double>, focus: DayFocus): Double {
        val primary = muscles.filterValues { it >= ExerciseTraits.PRIMARY }
        val secondary = muscles.filterValues { it < ExerciseTraits.PRIMARY }
        if (primary.isEmpty()) return weightedFit(secondary, focus)
        val primaryFit = weightedFit(primary, focus)
        if (secondary.isEmpty()) return primaryFit
        return PRIMARY_SHARE * primaryFit + (1.0 - PRIMARY_SHARE) * weightedFit(secondary, focus)
    }

    private fun weightedFit(group: Map<KpknMuscleGroup, Double>, focus: DayFocus): Double {
        val total = group.values.sum()
        if (total <= 0.0) return 0.0
        return group.entries.sumOf { (atom, contribution) -> contribution * focus.weight(atom) } / total
    }
}
