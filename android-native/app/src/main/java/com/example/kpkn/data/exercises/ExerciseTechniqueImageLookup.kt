package com.example.kpkn.data.exercises

import com.example.kpkn.R

data class ExerciseTechniqueImageVariant(
    val implementation: String,
    val imageResId: Int,
    val label: String,
)

object ExerciseTechniqueImageLookup {
    const val FLAT_BENCH_PRESS_DEFINITION_ID = "bench_press"
    const val HIGH_BAR_BACK_SQUAT_DEFINITION_ID = "high_bar_back_squat"
    const val LOW_BAR_BACK_SQUAT_DEFINITION_ID = "low_bar_back_squat"
    const val CONVENTIONAL_DEADLIFT_DEFINITION_ID = "conventional_deadlift"
    const val FRONT_SQUAT_DEFINITION_ID = "front_squat"
    const val GOBLET_SQUAT_DEFINITION_ID = "quads_sentadilla_copa"
    const val BULGARIAN_SPLIT_SQUAT_DEFINITION_ID = "bulgarian_split_squat"
    const val SUMO_SQUAT_DEFINITION_ID = "sumo_squat"
    const val HACK_SQUAT_DEFINITION_ID = "quads_sentadilla_hack"
    const val HACK_SQUAT_INVERTED_DEFINITION_ID = "quads_sentadilla_hack_invertida_maquina"
    const val ANDERSON_SQUAT_DEFINITION_ID = "quads_sentadilla_anderson"
    const val ANDERSON_FRONT_SQUAT_DEFINITION_ID = "quads_sentadilla_anderson_frontal_barra_recta"
    const val BAZUCA_SQUAT_DEFINITION_ID = "quads_sentadilla_bazuca"
    const val SOMERSAULT_SQUAT_DEFINITION_ID = "quads_sentadilla_somersault"
    const val V_SQUAT_DEFINITION_ID = "quads_sentadilla_v_squat"
    const val V_SQUAT_INVERTED_DEFINITION_ID = "quads_sentadilla_v_squat_invertida_maquina"
    const val SISSY_SQUAT_DEFINITION_ID = "sissy_squat"
    const val BULGARIAN_ZERCHER_DEFINITION_ID = "bulgarian_zercher"
    const val PENDULUM_SQUAT_DEFINITION_ID = "pendulum_squat"
    const val BELT_SQUAT_DEFINITION_ID = "belt_squat"
    const val ZERCHER_SQUAT_DEFINITION_ID = "quads_sentadilla_zercher_barra_recta"
    const val SUMO_DEADLIFT_DEFINITION_ID = "sumo_deadlift"
    const val ROMANIAN_DEADLIFT_DEFINITION_ID = "romanian_deadlift"
    const val ROMANIAN_SUMO_DEADLIFT_DEFINITION_ID = "romanian_sumo_deadlift"
    const val STIFF_LEG_DEADLIFT_DEFINITION_ID = "stiff_leg_deadlift"
    const val GOOD_MORNING_DEFINITION_ID = "good_morning"
    const val GOOD_MORNING_SEATED_DEFINITION_ID = "good_morning_seated"
    const val GOOD_MORNING_ZERCHER_DEFINITION_ID = "good_morning_zercher"
    const val ROMANIAN_ZERCHER_DEADLIFT_DEFINITION_ID = "hams_peso_muerto_rumano_zercher"
    const val PULL_THROUGH_DEFINITION_ID = "hams_pull_through"
    const val HIP_THRUST_DEFINITION_ID = "hip_thrust"
    const val CONVENTIONAL_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_convencional_deficit"
    const val SUMO_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_sumo_deficit"
    const val STIFF_LEG_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_piernas_rigidas_deficit"
    const val ROMANIAN_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_rumano_deficit"
    const val ROMANIAN_SUMO_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_rumano_sumo_deficit"
    const val KETTLEBELL_SWING_TWO_HANDS_DEFINITION_ID = "hams_swing_kettlebell_dos_manos"
    const val KETTLEBELL_SWING_ONE_HAND_DEFINITION_ID = "hams_swing_kettlebell_unilateral"

    private val NON_IMPLEMENT_TOKENS = setOf("bilateral", "unilateral", "left", "right")

    fun variants(definitionId: String): List<ExerciseTechniqueImageVariant> = when (definitionId) {
        HIGH_BAR_BACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_trasera_barra_alta, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_trasera_barra_alta_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_sentadilla_trasera_barra_alta_safety_bar, "Barra de Seguridad"),
        )
        LOW_BAR_BACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_trasera_barra_baja, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_trasera_barra_baja_smith, "Máquina Smith"),
        )
        FRONT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_frontal, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_frontal_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_frontal_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_frontal_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_sentadilla_frontal_polea, "Polea"),
        )
        GOBLET_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_copa, "Mancuerna"),
        )
        SUMO_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_sumo, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_sumo_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_sumo_kettlebell, "Kettlebell"),
        )
        BULGARIAN_SPLIT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_bulgara_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_bulgara_barra, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_bulgara_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_bulgara_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_sentadilla_bulgara_polea, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_bulgara_kettlebell, "Kettlebell"),
        )
        BULGARIAN_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell__zercher", R.drawable.exercise_sentadilla_bulgara_zercher, "Barra Zercher"),
        )
        HACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_hack_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_hack_barra, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_hack_smith, "Máquina Smith"),
        )
        HACK_SQUAT_INVERTED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_hack_invertida, "Máquina"),
        )
        ANDERSON_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_anderson, "Barra"),
        )
        ANDERSON_FRONT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_anderson_frontal, "Barra"),
        )
        BAZUCA_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_bazuca, "Barra"),
        )
        SOMERSAULT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_somersault, "Barra"),
        )
        V_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_v_squat, "Máquina"),
        )
        V_SQUAT_INVERTED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_v_squat_invertida, "Máquina"),
        )
        SISSY_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_sissy_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_sissy_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_sissy_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_sentadilla_sissy_disco, "Disco"),
        )
        PENDULUM_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_pendulo, "Máquina Pendular"),
        )
        BELT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_belt_squat, "Máquina Belt Squat"),
        )
        ZERCHER_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_zercher, "Barra Recta"),
        )
        CONVENTIONAL_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_convencional, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_convencional_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_convencional_hex_bar, "Barra Hexagonal"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_convencional_mancuernas, "Mancuernas"),
        )
        SUMO_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_sumo, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_sumo_mancuernas, "Mancuernas"),
        )
        ROMANIAN_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_rumano, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_rumano_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_rumano_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_rumano_hex_bar, "Barra Hexagonal"),
        )
        ROMANIAN_SUMO_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_rumano_sumo, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_rumano_sumo_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_rumano_sumo_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_rumano_sumo_hex_bar, "Barra Hexagonal"),
        )
        STIFF_LEG_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_piernas_rigidas, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_piernas_rigidas_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_piernas_rigidas_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_piernas_rigidas_hex_bar, "Barra Hexagonal"),
        )
        GOOD_MORNING_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_buenos_dias, "Barra"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_buenos_dias_safety_bar, "Barra de Seguridad"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_buenos_dias_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_buenos_dias_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_buenos_dias_polea, "Polea"),
        )
        GOOD_MORNING_SEATED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_buenos_dias_sentado, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_buenos_dias_sentado_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_buenos_dias_sentado_safety_bar, "Barra de Seguridad"),
        )
        GOOD_MORNING_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_buenos_dias_zercher, "Barra Zercher"),
        )
        ROMANIAN_ZERCHER_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_rumano_zercher, "Barra Zercher"),
        )
        PULL_THROUGH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_pull_through, "Polea"),
        )
        HIP_THRUST_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_hip_thrust, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_hip_thrust_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_hip_thrust_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_hip_thrust_banda, "Banda"),
        )
        CONVENTIONAL_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_convencional_deficit, "Déficit"),
        )
        SUMO_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_sumo_deficit, "Déficit"),
        )
        STIFF_LEG_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_piernas_rigidas_deficit, "Déficit"),
        )
        ROMANIAN_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_rumano_deficit, "Déficit"),
        )
        ROMANIAN_SUMO_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_rumano_sumo_deficit, "Déficit"),
        )
        KETTLEBELL_SWING_TWO_HANDS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_swing_kettlebell_dos_manos, "Kettlebell"),
        )
        KETTLEBELL_SWING_ONE_HAND_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_swing_kettlebell_unilateral, "Kettlebell"),
        )
        FLAT_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_press_de_banca_plano, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_press_de_banca_plano_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_press_de_banca_plano_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_press_de_banca_plano_maquina_convergente, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_press_de_banca_plano_polea, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_press_de_banca_plano_kettlebell, "Kettlebell"),
        )
        else -> emptyList()
    }

    fun resolveDefinitionId(
        catalogDefinitionId: String?,
        exerciseDbId: String?,
        exerciseId: String,
        catalogConfigurationId: String? = null,
    ): String? {
        val candidates = listOfNotNull(
            catalogDefinitionId,
            catalogConfigurationId?.substringBefore("__"),
            exerciseDbId?.substringBefore("__"),
            exerciseId.substringBefore("__"),
        )
        for (raw in candidates) {
            val key = raw.trim().lowercase()
            if (key.isBlank()) continue
            if (variants(key).isNotEmpty()) return key
        }
        return null
    }

    fun resolveImageResId(
        catalogDefinitionId: String?,
        exerciseDbId: String?,
        exerciseId: String,
        catalogConfigurationId: String? = null,
        selectedImplementation: String? = null,
    ): Int? {
        val definitionId = resolveDefinitionId(
            catalogDefinitionId = catalogDefinitionId,
            exerciseDbId = exerciseDbId,
            exerciseId = exerciseId,
            catalogConfigurationId = catalogConfigurationId,
        ) ?: return null
        val list = variants(definitionId)
        if (list.isEmpty()) return null
        val impl = implementToken(
            selectedImplementation = selectedImplementation,
            catalogConfigurationId = catalogConfigurationId,
            exerciseDbId = exerciseDbId,
            variants = list,
        )
        return list.firstOrNull { it.implementation == impl }?.imageResId ?: list.first().imageResId
    }

    private fun implementToken(
        selectedImplementation: String?,
        catalogConfigurationId: String?,
        exerciseDbId: String?,
        variants: List<ExerciseTechniqueImageVariant>,
    ): String? {
        val keys = variants.map { it.implementation }.toSet()
        fun usable(raw: String?): String? {
            val token = raw?.trim()?.lowercase().orEmpty()
            if (token.isEmpty() || token in NON_IMPLEMENT_TOKENS) return null
            return token.takeIf { it in keys }
        }
        usable(selectedImplementation)?.let { return it }
        fun partsOf(id: String?): List<String> {
            if (id.isNullOrBlank()) return emptyList()
            return id.split("__").drop(1).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
        val parts = partsOf(catalogConfigurationId) + partsOf(exerciseDbId)
        parts.lastOrNull { it in keys }?.let { return it }
        val joined = parts.filter { it !in NON_IMPLEMENT_TOKENS }.joinToString("__")
        if (joined in keys) return joined
        return parts.lastOrNull { it !in NON_IMPLEMENT_TOKENS }
    }
}
