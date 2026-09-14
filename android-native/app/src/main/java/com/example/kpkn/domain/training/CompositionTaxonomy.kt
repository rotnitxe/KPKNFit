package com.example.kpkn.domain.training

enum class PatternFamily {
    SQUAT,
    HINGE,
    HIP_EXTENSION,
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    KNEE_EXTENSION,
    KNEE_FLEXION,
    ELBOW_EXTENSION,
    ELBOW_FLEXION,
    SHOULDER_ABDUCTION,
    SHOULDER_FLEXION,
    CORE,
    CALF,
    GRIP,
    NECK,
    SHRUG,
    OTHER,
}

data class ExerciseCompositionMetadata(
    val configurationId: String,
    val displayName: String,
    val movementPatternId: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val axialLoadFactor: Double,
    val replacementGroup: String?,
    val laterality: String?,
    val performanceProfileId: String?,
    val articulationType: String?,
)

fun interface ExerciseCompositionMetadataProvider {
    fun metadata(configurationId: String): ExerciseCompositionMetadata?
}

object CompositionTaxonomy {
    private val families: Map<String, PatternFamily> = mapOf(
        "knee_dominant" to PatternFamily.SQUAT,
        "knee_hip_dominant" to PatternFamily.SQUAT,
        "knee_dominant_asymmetric" to PatternFamily.SQUAT,
        "knee_dominant_lengthened" to PatternFamily.SQUAT,
        "lateral_knee_dominant" to PatternFamily.SQUAT,
        "unilateral_knee_dominant" to PatternFamily.SQUAT,
        "unilateral_knee_dominant_asymmetric" to PatternFamily.SQUAT,
        "deadlift" to PatternFamily.HINGE,
        "hip_hinge" to PatternFamily.HINGE,
        "hip_hinge_deficit" to PatternFamily.HINGE,
        "hip_hinge_explosive" to PatternFamily.HINGE,
        "hip_hinge_lengthened" to PatternFamily.HINGE,
        "romanian_deadlift" to PatternFamily.HINGE,
        "romanian_deadlift_deficit" to PatternFamily.HINGE,
        "unilateral_hip_dominant" to PatternFamily.HINGE,
        "hip_extension" to PatternFamily.HIP_EXTENSION,
        "hip_extension_abduction" to PatternFamily.HIP_EXTENSION,
        "hip_extension_external_rotation" to PatternFamily.HIP_EXTENSION,
        "horizontal_push" to PatternFamily.HORIZONTAL_PUSH,
        "diagonal_push" to PatternFamily.VERTICAL_PUSH,
        "vertical_push" to PatternFamily.VERTICAL_PUSH,
        "horizontal_pull" to PatternFamily.HORIZONTAL_PULL,
        "horizontal_abduction" to PatternFamily.HORIZONTAL_PULL,
        "scapular_depression" to PatternFamily.HORIZONTAL_PULL,
        "vertical_pull" to PatternFamily.VERTICAL_PULL,
        "vertical_pull_abduction" to PatternFamily.VERTICAL_PULL,
        "knee_extension" to PatternFamily.KNEE_EXTENSION,
        "knee_flexion" to PatternFamily.KNEE_FLEXION,
        "knee_hip_flexion" to PatternFamily.KNEE_FLEXION,
        "knee_hip_extension" to PatternFamily.KNEE_FLEXION,
        "eccentric_knee_flexion" to PatternFamily.KNEE_FLEXION,
        "elbow_extension" to PatternFamily.ELBOW_EXTENSION,
        "elbow_flexion" to PatternFamily.ELBOW_FLEXION,
        "biarticular_lengthened" to PatternFamily.ELBOW_FLEXION,
        "shoulder_abduction_full_rom" to PatternFamily.SHOULDER_ABDUCTION,
        "shoulder_abduction_diagonal" to PatternFamily.SHOULDER_ABDUCTION,
        "hip_abduction" to PatternFamily.HIP_EXTENSION,
        "hip_abduction_extension" to PatternFamily.HIP_EXTENSION,
        "hip_abduction_external_rotation" to PatternFamily.HIP_EXTENSION,
        "hip_abduction_stability" to PatternFamily.HIP_EXTENSION,
        "hip_adduction" to PatternFamily.HIP_EXTENSION,
        "hip_adduction_dynamic" to PatternFamily.HIP_EXTENSION,
        "hip_flexion" to PatternFamily.CORE,
        "shoulder_flexion" to PatternFamily.SHOULDER_FLEXION,
        "anti_extension_isometric" to PatternFamily.CORE,
        "anti_extension_pelvic_control" to PatternFamily.CORE,
        "anti_extension_trunk" to PatternFamily.CORE,
        "anti_rotation_trunk" to PatternFamily.CORE,
        "spinal_flexion" to PatternFamily.CORE,
        "trunk_flexion" to PatternFamily.CORE,
        "trunk_rotation" to PatternFamily.CORE,
        "lateral_trunk_flexion" to PatternFamily.CORE,
        "spinal_extension" to PatternFamily.HINGE,
        "plantar_flexion" to PatternFamily.CALF,
        "ankle_dorsiflexion" to PatternFamily.CALF,
        "isometric_grip" to PatternFamily.GRIP,
        "pinch_grip" to PatternFamily.GRIP,
        "wrist_extension" to PatternFamily.GRIP,
        "wrist_flexion" to PatternFamily.GRIP,
        "wrist_flexion_extension" to PatternFamily.GRIP,
        "neck_extension" to PatternFamily.NECK,
        "neck_flexion" to PatternFamily.NECK,
        "neck_lateral_flexion" to PatternFamily.NECK,
        "scapular_elevation" to PatternFamily.SHRUG,
    )

    fun familyOf(movementPatternId: String?): PatternFamily? {
        if (movementPatternId.isNullOrBlank()) return null
        return families[movementPatternId]
    }

    fun knownPatternIds(): Set<String> = families.keys

    fun dominantMuscle(primaryMuscles: List<String>): String? = primaryMuscles.firstOrNull()

    fun muscleGroup(
        primaryMuscle: String?,
        movementPatternId: String?,
        configurationId: String? = null,
    ): com.example.kpkn.data.programs.KpknMuscleGroup? {
        val id = configurationId.orEmpty().lowercase()
        if (id.contains("face_pull") || id.contains("rear_delt") || id.contains("pull_apart")) {
            return com.example.kpkn.data.programs.KpknMuscleGroup.DELT_REAR
        }
        val pattern = familyOf(movementPatternId)
        return when (primaryMuscle) {
            "pectoralis" -> com.example.kpkn.data.programs.KpknMuscleGroup.CHEST
            "latissimus_dorsi" -> com.example.kpkn.data.programs.KpknMuscleGroup.BACK_LATS
            "trapezius", "rhomboids" -> com.example.kpkn.data.programs.KpknMuscleGroup.BACK_UPPER
            "quadriceps" -> com.example.kpkn.data.programs.KpknMuscleGroup.QUADS
            "hamstrings" -> com.example.kpkn.data.programs.KpknMuscleGroup.HAMS
            "gluteus_maximus", "gluteus_medius" -> com.example.kpkn.data.programs.KpknMuscleGroup.GLUTES
            "erector_spinae" -> com.example.kpkn.data.programs.KpknMuscleGroup.ERECTORS
            "deltoid" -> when (pattern) {
                PatternFamily.VERTICAL_PUSH, PatternFamily.SHOULDER_FLEXION ->
                    com.example.kpkn.data.programs.KpknMuscleGroup.DELT_FRONT
                PatternFamily.HORIZONTAL_PULL -> com.example.kpkn.data.programs.KpknMuscleGroup.DELT_REAR
                else -> com.example.kpkn.data.programs.KpknMuscleGroup.DELT_LATERAL
            }
            "biceps" -> com.example.kpkn.data.programs.KpknMuscleGroup.BICEPS
            "triceps" -> com.example.kpkn.data.programs.KpknMuscleGroup.TRICEPS
            "calves", "tibialis_anterior" -> com.example.kpkn.data.programs.KpknMuscleGroup.CALVES
            "core", "abdominals" -> com.example.kpkn.data.programs.KpknMuscleGroup.CORE
            "forearm" -> com.example.kpkn.data.programs.KpknMuscleGroup.FOREARMS
            "neck" -> com.example.kpkn.data.programs.KpknMuscleGroup.NECK
            "adductors" -> com.example.kpkn.data.programs.KpknMuscleGroup.ADDUCTORS
            else -> null
        }
    }

    fun isIsolation(
        family: PatternFamily?,
        articulationType: String?,
        configurationId: String? = null,
    ): Boolean {
        val id = configurationId.orEmpty().lowercase()
        if (id.contains("fly") || id.contains("apertura") || id.contains("face_pull") ||
            id.contains("pull_apart") || id.contains("rear_delt") || id.contains("lateral_raise") ||
            id.contains("pullover") || id.contains("pushdown") || id.contains("leg_curl") ||
            id.contains("extension_cuadriceps") || id.contains("crunch") || id.contains("pallof") ||
            id.contains("pull_thru") || id.contains("pull-through") || id.contains("pull_through") ||
            id.contains("shrug") ||
            id.contains("encogimiento") ||
            id.contains("patada") ||
            id.contains("kickback") ||
            id.contains("abduction") ||
            id.contains("adduction") ||
            id.contains("frog") ||
            id.contains("sissy") ||
            id.contains("puente_gluteos") ||
            id.contains("hiperextension") ||
            id.contains("reverse_hyper") ||
            id.contains("escapular") ||
            id.contains("scapular") ||
            id.contains("glute_ham_raise")
        ) {
            return true
        }
        if (family in setOf(
                PatternFamily.KNEE_EXTENSION,
                PatternFamily.KNEE_FLEXION,
                PatternFamily.ELBOW_EXTENSION,
                PatternFamily.ELBOW_FLEXION,
                PatternFamily.SHOULDER_ABDUCTION,
                PatternFamily.SHOULDER_FLEXION,
                PatternFamily.CORE,
                PatternFamily.CALF,
                PatternFamily.GRIP,
                PatternFamily.NECK,
                PatternFamily.SHRUG,
            )
        ) {
            return true
        }
        // AISLADO del catálogo no convierte un compuesto de patrón (hip thrust,
        // GHR, press, remo) en aislamiento. Solo aplica a familias no compuestas.
        val compoundFamilies = setOf(
            PatternFamily.SQUAT,
            PatternFamily.HINGE,
            PatternFamily.HIP_EXTENSION,
            PatternFamily.HORIZONTAL_PUSH,
            PatternFamily.VERTICAL_PUSH,
            PatternFamily.HORIZONTAL_PULL,
            PatternFamily.VERTICAL_PULL,
        )
        return articulationType.equals("AISLADO", ignoreCase = true) && family !in compoundFamilies
    }

    fun isFinisherFamily(family: PatternFamily?): Boolean =
        family in setOf(
            PatternFamily.CORE,
            PatternFamily.CALF,
            PatternFamily.GRIP,
            PatternFamily.NECK,
            PatternFamily.SHRUG,
        )
}
