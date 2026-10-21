package com.example.kpkn.testsupport

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.exercises.catalogv2.toLegacyDefaultCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File

/**
 * Catálogo real aprobado (asset exercise_catalog_v2.json) y las recetas de sesión de AUD1 §2.5, para las pruebas de los motores
 * del editor y de Crear ejercicio (pista MOT). Solo pruebas.
 */
internal object MotCatalog {
    val catalog: ExerciseCatalogV2 by lazy {
        ExerciseCatalogV2Loader.decodeApproved(catalogFile().readText())
    }

    /** Una configuración por defecto por definición, como la instantánea que recibe el creador de ejercicios. */
    val legacyDefaultCatalog: List<ExerciseMuscleInfo> by lazy { catalog.toLegacyDefaultCatalog() }

    /** El índice del editor: instantánea por defecto más todas las configuraciones exactas. */
    val index: Map<String, ExerciseMuscleInfo> by lazy {
        legacyDefaultCatalog.associateBy { it.id.lowercase() } + catalog.toLegacyConfigurationLookup()
    }

    private fun catalogFile(): File = listOf(
        "src/main/assets/exercise_catalog_v2.json",
        "app/src/main/assets/exercise_catalog_v2.json",
        "android-native/app/src/main/assets/exercise_catalog_v2.json",
    ).map(::File).first { it.exists() }
}

/** Una línea de receta: configuración exacta, series × repeticiones, esfuerzo y descanso. */
internal data class MotRecipeLine(
    val configurationId: String,
    val sets: Int,
    val reps: Int,
    val rpe: Double,
    val restSeconds: Int,
    val weight: Double? = null,
)

internal object MotSessions {
    fun exercise(line: MotRecipeLine, ordinal: Int, prefix: String = "ex"): Exercise {
        val info = MotCatalog.index.getValue(line.configurationId.lowercase())
        return Exercise(
            id = "$prefix$ordinal",
            name = info.name,
            exerciseDbId = info.catalogDefinitionId,
            catalogConfigurationId = line.configurationId,
            catalogDefinitionId = info.catalogDefinitionId,
            catalogRevision = info.catalogRevision,
            restTime = line.restSeconds,
            sets = List(line.sets) { i ->
                ExerciseSet(
                    id = "$prefix$ordinal-s$i",
                    targetReps = line.reps,
                    targetRPE = line.rpe,
                    weight = line.weight,
                )
            },
        )
    }

    fun session(
        id: String,
        name: String,
        recipe: List<MotRecipeLine>,
        dayOfWeek: Int? = null,
        targetDurationMinutes: Int? = null,
    ): Session = Session(
        id = id,
        name = name,
        exercises = recipe.mapIndexed { i, line -> exercise(line, i + 1, prefix = "$id-e") },
        dayOfWeek = dayOfWeek,
        targetDurationMinutes = targetDurationMinutes,
    )

    /** Pareja del caso B de AUD1 §1.2: sentadilla trasera alta y peso muerto convencional. */
    fun squatAndDeadlift(setsEach: Int, weightSquat: Double = 100.0, weightDeadlift: Double = 140.0): Session = session(
        id = "mot-squat-deadlift",
        name = "Sentadilla y peso muerto",
        recipe = listOf(
            MotRecipeLine("high_bar_back_squat__barbell", setsEach, 5, 8.0, 180, weightSquat),
            MotRecipeLine("conventional_deadlift__bilateral__barbell", setsEach, 5, 8.0, 180, weightDeadlift),
        ),
    )

    /** CardioFeaturedTemplates.apply("steady_z2_30") sobre bicicleta estática (S12 de AUD1). */
    fun cardioZ2(): Exercise = Exercise(
        id = "mot-cardio-z2",
        name = "Bicicleta estática",
        cardioDetails = CardioFeaturedTemplates.apply("steady_z2_30", CardioDetails(type = CardioType.BIKE_STATIONARY)),
    )

    val S1 = listOf(
        MotRecipeLine("low_bar_back_squat__barbell", 3, 4, 8.0, 210),
        MotRecipeLine("bench_press__barbell", 3, 5, 7.5, 180),
        MotRecipeLine("chest_supported_row__dumbbells__wide", 2, 8, 8.0, 120),
        MotRecipeLine("quads_extension_cuadriceps__machine__bilateral", 2, 12, 8.0, 75),
        MotRecipeLine("triceps_pushdown__bilateral__cable", 2, 10, 8.0, 75),
    )

    val S2 = listOf(
        MotRecipeLine("conventional_deadlift__bilateral__barbell", 3, 3, 8.0, 240),
        MotRecipeLine("bench_press__barbell", 3, 6, 7.5, 180),
        MotRecipeLine("lat_pulldown__bilateral__cable", 2, 8, 8.0, 120),
        MotRecipeLine("hip_thrust__bilateral__machine", 2, 10, 8.0, 90),
        MotRecipeLine("core_press_pallof__default", 2, 12, 7.5, 60),
    )

    val S9 = listOf(
        MotRecipeLine("preacher_curl__machine", 2, 10, 7.0, 75),
        MotRecipeLine("triceps_pushdown__bilateral__cable", 2, 10, 7.5, 75),
        MotRecipeLine("concentration_curl__dumbbells", 2, 12, 7.5, 60),
        MotRecipeLine("crossbody_triceps__cable__bilateral", 2, 12, 7.5, 60),
    )

    val S10 = listOf(
        MotRecipeLine("quads_sentadilla_hack__machine", 2, 8, 7.5, 180),
        MotRecipeLine("bench_press__dumbbells", 3, 8, 7.5, 120),
        MotRecipeLine("quads_prensa_piernas__bilateral", 4, 10, 7.5, 120),
        MotRecipeLine("seated_shoulder_press__barbell", 4, 10, 7.5, 90),
        MotRecipeLine("flat_chest_fly__machine", 3, 12, 7.5, 75),
    )

    val S11 = listOf(
        MotRecipeLine("high_bar_back_squat__barbell", 3, 5, 7.5, 210),
        MotRecipeLine("bench_press__barbell", 3, 6, 7.5, 180),
        MotRecipeLine("lat_pulldown__bilateral__cable", 3, 8, 7.5, 150),
        MotRecipeLine("romanian_deadlift__bilateral__barbell", 2, 8, 7.5, 150),
        MotRecipeLine("standing_lateral_raise__dumbbells", 2, 12, 7.5, 60),
        MotRecipeLine("calf_raise__bilateral__machine", 1, 12, 7.5, 45),
    )
}
