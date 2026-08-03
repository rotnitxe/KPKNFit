package com.example.kpkn.data.sessions

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.data.splits.Difficulty
import kotlin.math.roundToInt

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Alineado con [com.example.kpkn.domain.templates.SessionTemplateAudit] (sin depender de domain). */
private const val TEMPLATE_SETUP_SECONDS = 90
private const val TEMPLATE_DEFAULT_REST_SECONDS = 90
private const val TEMPLATE_EXECUTION_SECONDS_PER_SET = 45
private const val TEMPLATE_MIN_DURATION_MINUTES = 10
private const val LOW_VOLUME_MIN_SETS = 8
private const val LOW_VOLUME_MAX_SETS = 12
private const val LOW_VOLUME_TARGET_SETS_PER_EXERCISE = 2
private const val TEMPLATE_CATALOG_REVISION = "v2-approved-2026-08-02-c"

/**
 * Performance profiles are part of the compiled v2 identity.  Keeping this
 * whitelist next to the system templates makes a missing catalog entry fail
 * during template construction instead of silently falling back to a parent.
 */
private val TEMPLATE_PERFORMANCE_PROFILE_BY_CONFIGURATION = mapOf(
    "back_encogimientos__dumbbells" to "back_encogimientos__dumbbells",
    "back_extension_lumbar__default" to "back_extension_lumbar__machine__extension_lumbar",
    "belt_squat__bilateral" to "belt_squat__machine__sentadilla_belt_squat",
    "belt_squat__unilateral" to "belt_squat__machine__sentadilla_belt_squat",
    "bench_press__barbell" to "bench_press__barbell__press_de_banca",
    "bench_press__dumbbells" to "bench_press__dumbbells__press_de_banca",
    "biceps_curl_bayesian__dumbbells__supinated" to "biceps_curl_bayesian__dumbbells__supinated",
    "bulgarian_split_squat__barbell" to "bulgarian_split_squat__barbell__sentadilla_bulgara",
    "bulgarian_split_squat__dumbbells" to "bulgarian_split_squat__dumbbells__sentadilla_bulgara",
    "calf_raise__bilateral__machine" to "calf_raise__bilateral__machine",
    "calf_raise__unilateral__machine" to "calf_raise__unilateral__machine",
    "chest_supported_row__dumbbells__medium" to "chest_supported_row__dumbbells__medium__remo_pecho_apoyado",
    "chest_supported_row__dumbbells__wide" to "chest_supported_row__dumbbells__wide__remo_pecho_apoyado",
    "concentration_curl__dumbbells" to "concentration_curl__dumbbells",
    "conventional_deadlift__bilateral__barbell" to "conventional_deadlift__barbell__peso_muerto",
    "conventional_deadlift__bilateral__hex_bar" to "conventional_deadlift__hex_bar__peso_muerto",
    "conventional_row__cable" to "conventional_row__cable__remo_convencional",
    "conventional_row__machine" to "conventional_row__machine__remo_convencional",
    "copenhagen_plank__default" to "copenhagen_plank__bodyweight__plancha_copenhagen",
    "core_crunch_maquina__default" to "core_crunch_maquina__machine__crunch_abdominal_en_maquina",
    "core_elevacion_piernas__default" to "core_elevacion_piernas__bodyweight__elevacion_piernas",
    "core_lenador_polea__default" to "core_lenador_polea__cable__lenador_en_polea",
    "core_plancha__default" to "core_plancha__bodyweight__plancha",
    "core_press_pallof__default" to "core_press_pallof__cable__press_pallof",
    "crossbody_triceps__cable__bilateral" to "crossbody_triceps__cable__bilateral",
    "crossbody_triceps__cable__unilateral" to "crossbody_triceps__cable__unilateral",
    "curl_isquios_con_sliders__default" to "curl_isquios_con_sliders__sliders__curl_de_isquiosurales_con_sliders",
    "deltoides_face_pull__default" to "deltoides_face_pull__cable__face_pull",
    "flat_chest_fly__dumbbells" to "flat_chest_fly__dumbbells__aperturas",
    "flat_chest_fly__machine" to "flat_chest_fly__machine__aperturas",
    "floor_press__barbell" to "floor_press__barbell__floor_press",
    "floor_press__dumbbells" to "floor_press__dumbbells__floor_press",
    "forearms_curl_muneca_inverso_sentado__ez_bar" to "forearms_extension_muneca__ez_bar",
    "forearms_curl_muneca_sentado__barbell" to "forearms_curl_muneca__barbell",
    "forearms_curl_muneca_sentado__dumbbells" to "forearms_curl_muneca__dumbbells",
    "forearms_enrollamiento_muneca_rodillo__default" to "forearms_enrollamiento_muneca_rodillo__wrist_roller__enrollamiento_de_muneca_con_rodillo_y_cuerda",
    "front_squat__barbell" to "front_squat__barbell__sentadilla_frontal",
    "gironda_row__medium" to "gironda_row__medium__remo_gironda",
    "glutes_hiperextension_45__plate" to "glutes_hiperextension_45__plate",
    "glutes_patada_gluteo__cable" to "glutes_patada_gluteo__cable",
    "high_bar_back_squat__barbell" to "high_bar_back_squat__barbell__sentadilla_trasera",
    "hip_abduction__seated__machine__bilateral" to "hip_abduction__machine__abduccion_de_cadera",
    "hip_abduction__standing__cable__unilateral" to "hip_abduction__cable__abduccion_de_cadera_unilateral",
    "hip_adduction__seated__machine__bilateral" to "hip_adduction__machine__adduccion_de_cadera",
    "hip_adduction__standing__cable__unilateral" to "hip_adduction__cable__adduccion_de_cadera_unilateral",
    "hip_thrust__bilateral__barbell" to "hip_thrust__barbell__hip_thrust",
    "hip_thrust__bilateral__machine" to "hip_thrust__machine__hip_thrust",
    "incline_chest_fly__dumbbells" to "incline_chest_fly__dumbbells__aperturas",
    "lat_pulldown__bilateral__band" to "lat_pulldown__band__jalon_pecho",
    "lat_pulldown__bilateral__cable" to "lat_pulldown__cable__jalon_pecho",
    "lat_pulldown__bilateral__machine" to "lat_pulldown__machine__jalon_pecho",
    "lateral_raise_super_rom__dumbbells" to "lateral_raise_super_rom__dumbbells",
    "lying_leg_curl__bilateral__cable" to "lying_leg_curl__cable__curl_isquios",
    "lying_leg_curl__bilateral__machine" to "lying_leg_curl__machine__curl_isquios",
    "lying_leg_curl__unilateral__machine" to "lying_leg_curl__machine__curl_isquios",
    "lying_pullover__dumbbells" to "lying_pullover__dumbbells__pullover_banca",
    "military_press__barbell" to "military_press__barbell__press_militar",
    "military_press__dumbbells" to "military_press__dumbbells__press_militar",
    "overhead_triceps__barbell" to "overhead_triceps_extension__barbell__extension_de_triceps_overhead",
    "overhead_triceps__cable" to "overhead_triceps_extension__cable__extension_de_triceps_overhead",
    "overhead_triceps__dumbbells" to "overhead_triceps_extension__dumbbells__extension_de_triceps_overhead",
    "overhead_triceps__machine" to "overhead_triceps_extension__machine__extension_de_triceps_overhead",
    "pendlay_row__barbell" to "pendlay_row__barbell__remo_pendlay",
    "pendulum_squat__bilateral" to "pendulum_squat__machine__sentadilla_pendulo",
    "pendulum_squat__unilateral" to "pendulum_squat__machine__sentadilla_pendulo",
    "preacher_curl__barbell" to "preacher_curl__barbell__curl_biceps_predicador",
    "preacher_curl__dumbbells" to "preacher_curl__dumbbells__curl_biceps_predicador",
    "preacher_curl__machine" to "preacher_curl__machine__curl_biceps_predicador",
    "pull_up__pronated__medium" to "pull_up__pronated__medium",
    "pull_up__pronated__wide" to "pull_up__pronated__wide",
    "pullover__bilateral__cable" to "pullover__cable__pullover",
    "pullover__bilateral__machine" to "pullover__machine__pullover",
    "push_up__feet_elevated" to "push_up__bodyweight__flexiones_de_brazos",
    "push_up__flat" to "push_up__bodyweight__flexiones_de_brazos",
    "quads_extension_cuadriceps__machine__bilateral" to "quads_extension_cuadriceps__machine__bilateral",
    "quads_extension_cuadriceps__machine__unilateral" to "quads_extension_cuadriceps__machine__unilateral",
    "quads_prensa_piernas__bilateral" to "quads_prensa_piernas__bilateral__prensa_piernas",
    "quads_sentadilla_copa__default" to "quads_sentadilla_copa__dumbbells__sentadilla_copa",
    "quads_sentadilla_hack__machine" to "quads_sentadilla_hack__machine",
    "romanian_deadlift__bilateral__barbell" to "romanian_deadlift__barbell__peso_muerto_rumano",
    "romanian_deadlift__bilateral__dumbbells" to "romanian_deadlift__dumbbells__peso_muerto_rumano",
    "seal_row__barbell" to "seal_row__barbell__remo_seal",
    "seal_row__dumbbells" to "seal_row__dumbbells__remo_seal",
    "seated_lateral_raise__dumbbells" to "lateral_raise_seated__dumbbells",
    "seated_lateral_raise__machine" to "lateral_raise_seated__machine",
    "seated_leg_curl__unilateral__machine" to "seated_leg_curl__machine__curl_isquios",
    "seated_shoulder_press__barbell" to "seated_shoulder_press__barbell__press_hombros_sentado",
    "seated_shoulder_press__machine" to "seated_shoulder_press__machine__press_hombros_sentado",
    "standing_biceps_curl__barbell" to "standing_biceps_curl__barbell__curl_biceps",
    "standing_biceps_curl__dumbbells" to "standing_biceps_curl__dumbbells__curl_biceps",
    "standing_lateral_raise__cable" to "lateral_raise_standing__cable",
    "standing_lateral_raise__dumbbells" to "lateral_raise_standing__dumbbells",
    "standing_lateral_raise__machine" to "lateral_raise_standing__machine",
    "standing_leg_curl__unilateral__cable" to "standing_leg_curl__cable__curl_isquios",
    "sumo_deadlift__barbell" to "sumo_deadlift__barbell__peso_muerto_sumo",
    "t_bar_row__t_bar__medium" to "t_bar_row__t_bar__medium__remo_barra_t",
    "t_bar_row__t_bar__wide" to "t_bar_row__t_bar__wide__remo_barra_t",
    "tren_superior_cruce_poleas__high" to "tren_superior_cruce_poleas__high",
    "tren_superior_fondos__default" to "tren_superior_fondos__bodyweight__fondos",
    "tren_superior_press_pecho_maquina_convergente__default" to "tren_superior_press_pecho_maquina_convergente__machine__press_de_pecho_en_maquina_convergente",
    "triceps_patada__cable__bilateral" to "triceps_patada__cable__patada_de_triceps",
    "triceps_patada__dumbbells__bilateral" to "triceps_patada__dumbbells__patada_de_triceps",
    "triceps_patada__dumbbells__unilateral" to "triceps_patada__dumbbells__patada_de_triceps",
    "triceps_pushdown__bilateral__cable" to "triceps_pushdown__cable__bilateral",
)

/**
 * Static system templates were authored before the editorial regrouping. The
 * builder normalizes these literals once, so emitted sessions carry only the
 * current parent/configuration identity; the catalog itself has no legacy
 * fallback or alias surface.
 */
private val TEMPLATE_CONFIGURATION_ID_ALIASES = mapOf(
    "lat_pulldown__bilateral__band" to "lat_pulldown__bilateral__band",
    "lat_pulldown__bilateral__machine" to "lat_pulldown__bilateral__machine",
    "lat_pulldown__bilateral__cable" to "lat_pulldown__bilateral__cable",
    "calf_raise__bilateral__machine" to "calf_raise__bilateral__machine",
    "curl_isquios_con_sliders__default" to "curl_isquios_con_sliders__default",
    "conventional_deadlift__bilateral__hex_bar" to "conventional_deadlift__bilateral__hex_bar",
    "conventional_deadlift__bilateral__barbell" to "conventional_deadlift__bilateral__barbell",
    "sumo_deadlift__barbell" to "sumo_deadlift__barbell",
    "overhead_triceps__barbell" to "overhead_triceps__barbell",
    "overhead_triceps__machine" to "overhead_triceps__machine",
    "chest_supported_row__dumbbells__medium" to "chest_supported_row__dumbbells__wide",
    "seal_row__barbell" to "seal_row__barbell",
    "seal_row__dumbbells" to "seal_row__dumbbells",
    "floor_press__barbell" to "floor_press__barbell",
    "floor_press__dumbbells" to "floor_press__dumbbells",
    "bench_press__barbell" to "bench_press__barbell",
    "bench_press__dumbbells" to "bench_press__dumbbells",
)

/**
 * Resolves legacy template literals using the actual variant named by the
 * template.  A flat alias is intentionally not used for these IDs because a
 * single historical key was reused for seated/lying/standing or
 * machine/dumbbell variants.  The result is always a materialized v2
 * configuration; no name is used by the catalog runtime to infer chips.
 */
private fun canonicalTemplateConfigurationId(exerciseDbId: String, name: String): String {
    val normalized = name.lowercase()
    return when (exerciseDbId) {
        "lying_pullover__dumbbells" -> when {
            "máquina" in normalized -> "pullover__bilateral__machine"
            "polea" in normalized -> "pullover__bilateral__cable"
            else -> "lying_pullover__dumbbells"
        }
        "curl_isquios_con_sliders__default" -> when {
            "sentado" in normalized -> "seated_leg_curl__unilateral__machine"
            "tumbado" in normalized -> "lying_leg_curl__unilateral__machine"
            "de pie" in normalized -> "standing_leg_curl__unilateral__cable"
            else -> "curl_isquios_con_sliders__default"
        }
        "standing_biceps_curl__barbell" ->
            if ("mancuern" in normalized) "standing_biceps_curl__dumbbells" else exerciseDbId
        "preacher_curl__barbell" -> when {
            "máquina" in normalized -> "preacher_curl__machine"
            "mancuern" in normalized -> "preacher_curl__dumbbells"
            else -> exerciseDbId
        }
        "incline_biceps_curl__dumbbells" -> "biceps_curl_bayesian__dumbbells__supinated"
        "calf_raise__bilateral__machine" -> when {
            "mancuern" in normalized -> "calf_raise__bilateral__machine"
            "unilateral" in normalized -> "calf_raise__unilateral__machine"
            else -> "calf_raise__bilateral__machine"
        }
        "hip_thrust__bilateral__barbell" -> when {
            "máquina" in normalized -> "hip_thrust__bilateral__machine"
            "unilateral" in normalized -> "hip_thrust__bilateral__barbell"
            "mancuern" in normalized -> "hip_thrust__bilateral__barbell"
            else -> "hip_thrust__bilateral__barbell"
        }
        "quads_extension_cuadriceps__machine__bilateral" ->
            if ("unilateral" in normalized) "quads_extension_cuadriceps__machine__unilateral"
            else "quads_extension_cuadriceps__machine__bilateral"
        "triceps_patada__dumbbells__bilateral" ->
            if ("polea" in normalized) "triceps_patada__cable__bilateral"
            else "triceps_patada__dumbbells__bilateral"
        "seated_shoulder_press__barbell" ->
            if ("máquina" in normalized) "seated_shoulder_press__machine" else "seated_shoulder_press__barbell"
        "forearms_curl_muneca_sentado__barbell" ->
            if ("mancuern" in normalized) "forearms_curl_muneca_sentado__dumbbells" else "forearms_curl_muneca_sentado__barbell"
        "seated_lateral_raise__machine" -> when {
            "mancuern" in normalized -> "seated_lateral_raise__dumbbells"
            "de pie" in normalized -> "standing_lateral_raise__machine"
            else -> exerciseDbId
        }
        "romanian_deadlift__bilateral__barbell" ->
            if ("mancuern" in normalized) "romanian_deadlift__bilateral__dumbbells" else exerciseDbId
        "overhead_triceps__barbell" -> when {
            "polea" in normalized -> "overhead_triceps__cable"
            "mancuern" in normalized -> "overhead_triceps__dumbbells"
            else -> "overhead_triceps__barbell"
        }
        "bulgarian_split_squat__dumbbells" ->
            if ("mancuern" in normalized) "bulgarian_split_squat__dumbbells" else exerciseDbId
        else -> TEMPLATE_CONFIGURATION_ID_ALIASES[exerciseDbId] ?: exerciseDbId
    }
}

private fun canonicalTemplateExerciseName(configurationId: String, rawName: String): String = when (configurationId) {
    "lying_pullover__dumbbells" -> "Pullover con Mancuerna"
    "pullover__bilateral__cable" -> "Pullover en Polea"
    "pullover__bilateral__machine" -> "Pullover en Máquina"
    "standing_biceps_curl__dumbbells" -> "Curl de Bíceps de Pie con Mancuernas"
    "preacher_curl__dumbbells" -> "Curl Predicador con Mancuernas"
    "preacher_curl__machine" -> "Curl Predicador en Máquina"
    "incline_biceps_curl__dumbbells" -> "Curl Bayesian"
    "preacher_curl__barbell" -> "Curl Predicador con Barra"
    "standing_biceps_curl__barbell" -> "Curl de Bíceps de Pie con Barra"
    "romanian_deadlift__bilateral__dumbbells" -> "Peso Muerto Rumano Estilo Sumo con Mancuernas"
    "curl_isquios_con_sliders__default" -> "Curl Femoral con Sliders"
    "standing_leg_curl__unilateral__cable" -> "Curl Femoral de Pie Unilateral en Polea"
    "seated_leg_curl__unilateral__machine" -> "Curl Femoral Sentado Unilateral en Máquina"
    "lying_leg_curl__unilateral__machine" -> "Curl Femoral Tumbado Unilateral en Máquina"
    "hip_thrust__bilateral__barbell" -> "Hip Thrust con Barra Recta"
    "hip_thrust__bilateral__barbell" -> "Hip Thrust con Mancuernas"
    "hip_thrust__bilateral__barbell" -> "Hip Thrust Unilateral con Mancuernas"
    "hip_thrust__bilateral__machine" -> "Hip Thrust en Máquina"
    "triceps_patada__dumbbells__bilateral" -> "Patada de Tríceps con Mancuernas"
    "triceps_patada__cable__bilateral" -> "Patada de Tríceps en Polea"
    "triceps_patada__dumbbells__unilateral" -> "Patada de Tríceps Unilateral con Mancuerna"
    "quads_extension_cuadriceps__machine__bilateral" -> "Extensión de Cuádriceps en Máquina"
    "quads_extension_cuadriceps__machine__unilateral" -> "Extensión de Cuádriceps Unilateral en Máquina"
    "calf_raise__bilateral__machine" -> "Elevación de Talones de Pie en Máquina"
    "calf_raise__unilateral__machine" -> "Elevación de Talones de Pie Unilateral en Máquina"
    "calf_raise__bilateral__machine" -> "Elevación de Talones de Pie con Mancuernas"
    "calf_raise__bilateral__machine" -> "Elevación de Talones Burro en Máquina"
    "calf_raise__bilateral__machine" -> "Elevación de Talones en Prensa"
    "calf_raise__bilateral__machine" -> "Elevación de Talones Sentado en Máquina"
    "seated_shoulder_press__barbell" -> "Press de Hombros Sentado con Barra Recta"
    "seated_shoulder_press__machine" -> "Press de Hombros Sentado en Máquina"
    "forearms_curl_muneca_sentado__barbell" -> "Curl de Muñeca Sentado con Barra Recta"
    "forearms_curl_muneca_sentado__dumbbells" -> "Curl de Muñeca Sentado con Mancuernas"
    "standing_lateral_raise__machine" -> "Elevaciones Laterales de Pie en Máquina"
    "seated_lateral_raise__dumbbells" -> "Elevaciones Laterales Sentado con Mancuernas"
    "seated_lateral_raise__machine" -> "Elevaciones Laterales Sentado en Máquina"
    "overhead_triceps__barbell" -> "Extensión de Tríceps Overhead con Barra"
    "overhead_triceps__dumbbells" -> "Extensión de Tríceps Overhead con Mancuernas"
    "overhead_triceps__cable" -> "Extensión de Tríceps Overhead en Polea"
    "overhead_triceps__machine" -> "Extensión de Tríceps Overhead en Máquina"
    "bulgarian_split_squat__dumbbells" -> "Sentadilla Búlgara con Mancuernas"
    else -> rawName
}

private fun templatePerformanceProfileId(configurationId: String): String =
    TEMPLATE_PERFORMANCE_PROFILE_BY_CONFIGURATION[configurationId]
        ?: error("System template references an unregistered v2 configuration: $configurationId")

private fun ex(
    id: String,
    name: String,
    exerciseDbId: String,
    sets: List<ExerciseSet>,
    restTime: Int? = 120,
    trainingMode: TrainingMode = TrainingMode.REPS,
    _intensityMode: IntensityMode = IntensityMode.RPE,
    damageProfile: DamageProfile? = null,
): Exercise {
    val canonicalConfigurationId = canonicalTemplateConfigurationId(exerciseDbId, name)
    val canonicalName = canonicalTemplateExerciseName(canonicalConfigurationId, name)
    return Exercise(
        id = id,
        name = canonicalName,
        exerciseDbId = canonicalConfigurationId,
        exerciseId = canonicalConfigurationId,
        canonicalExerciseId = canonicalConfigurationId,
        exerciseFamilyId = canonicalConfigurationId.substringBefore("__"),
        sets = sets,
        restTime = restTime,
        trainingMode = trainingMode,
        damageProfile = damageProfile,
        catalogRevision = TEMPLATE_CATALOG_REVISION,
        catalogDefinitionId = canonicalConfigurationId.substringBefore("__"),
        catalogConfigurationId = canonicalConfigurationId,
        performanceProfileId = templatePerformanceProfileId(canonicalConfigurationId),
        occurrenceId = id,
    )
}

private fun rpeSet(id: String, reps: Int, rpe: Double): ExerciseSet = ExerciseSet(
    id = id,
    targetReps = reps,
    targetRPE = rpe,
    intensityMode = IntensityMode.RPE,
)

private fun rirSet(id: String, reps: Int, rir: Int): ExerciseSet = ExerciseSet(
    id = id,
    targetReps = reps,
    targetRIR = rir,
    intensityMode = IntensityMode.RIR,
)

private fun nSets(prefix: String, count: Int, reps: Int, rpe: Double): List<ExerciseSet> =
    (1..count).map { rpeSet("$prefix-s$it", reps, rpe) }

@Suppress("KotlinConstantConditions")
private fun nRirSets(prefix: String, count: Int, reps: Int, rir: Int): List<ExerciseSet> =
    (1..count).map { rirSet("$prefix-s$it", reps, rir) }

private fun part(id: String, name: String, color: String, exercises: List<Exercise>) =
    SessionPart(id = id, name = name, color = color, exercises = exercises)

private fun sessionTotalSets(session: Session): Int =
    session.allExercises().sumOf { it.sets.size }

/**
 * Deriva exerciseCount / partCount / estimatedDurationMinutes del contenido real
 * para evitar metadata duplicada inconsistente.
 *
 * La duración usa la MISMA fórmula que SessionTemplateAudit (setupTime y
 * averageRestSeconds del catálogo por ejercicio), sin depender de domain:
 * así la declarada coincide con la estimada del audit dentro del desvío
 * permitido por el test.
 */
private fun finalizedTemplate(template: SessionTemplate): SessionTemplate {
    val exercises = template.session.allExercises()
    val index = catalogExerciseIndex()
    var durationSeconds = 0
    exercises.forEach { exercise ->
        val info = resolveTemplateCatalogInfo(exercise, index)
        val setCount = exercise.sets.size
        val setup = info?.setupTime?.takeIf { it > 0 } ?: TEMPLATE_SETUP_SECONDS
        val rest = exercise.restTime?.takeIf { it > 0 }
            ?: info?.averageRestSeconds?.takeIf { it > 0 }
            ?: TEMPLATE_DEFAULT_REST_SECONDS
        val execution = setCount * TEMPLATE_EXECUTION_SECONDS_PER_SET
        val restTotal = if (setCount > 1) rest * (setCount - 1) else 0
        durationSeconds += setup + execution + restTotal
    }
    val estimated = (durationSeconds / 60.0)
        .roundToInt()
        .coerceAtLeast(if (exercises.isEmpty()) 0 else TEMPLATE_MIN_DURATION_MINUTES)
    return template.copy(
        exerciseCount = exercises.size,
        partCount = template.session.parts.size,
        // La duración explícita de una variante derivada (p.ej. "Compacta" con
        // 13/11 min) se respeta: coincide con la estimada del audit. Solo se
        // deriva del contenido cuando no hay valor declarado.
        estimatedDurationMinutes = template.estimatedDurationMinutes ?: estimated,
    )
}

/** Resuelve la metadata del catálogo para un ejercicio (igual que el audit). */
private fun resolveTemplateCatalogInfo(
    exercise: Exercise,
    index: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val candidates = listOfNotNull(
        exercise.catalogConfigurationId,
        exercise.exerciseDbId,
        exercise.exerciseId,
        exercise.canonicalExerciseId,
    ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    for (id in candidates) {
        index[id]?.let { return it }
    }
    return null
}

private fun mapSessionExercises(session: Session, transform: (Exercise) -> Exercise): Session =
    session.copy(
        parts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map(transform))
        },
        exercises = session.exercises.map(transform),
    )

private fun dropTrailingExercise(session: Session): Session {
    if (session.parts.isNotEmpty()) {
        for (i in session.parts.indices.reversed()) {
            val part = session.parts[i]
            if (part.exercises.isNotEmpty()) {
                val trimmed = part.copy(exercises = part.exercises.dropLast(1))
                val parts = session.parts.toMutableList()
                if (trimmed.exercises.isEmpty() && session.parts.size > 1) {
                    parts.removeAt(i)
                } else {
                    parts[i] = trimmed
                }
                return session.copy(parts = parts)
            }
        }
    }
    if (session.exercises.isNotEmpty()) {
        return session.copy(exercises = session.exercises.dropLast(1))
    }
    return session
}

// ─── System templates ─────────────────────────────────────────────────────────

private val SESSION_TEMPLATES_BASE: List<SessionTemplate> = listOf(

    // ── 1. Push Day ──────────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-push-ppl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Push Day · PPL",
        description = "Día de Empuje clásico: Pecho, Hombros y Tríceps con enfoque en hipertrofia. Multi-articulares primero, aislamientos al final.",
        emoji = "🫸",
        tags = listOf(
            SessionTemplateTag.EMPUJE,
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.HOMBROS,
            SessionTemplateTag.BRAZOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 22,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 10,
        splitIds = listOf("ppl_ul", "ppl_arnold", "ppl_x3", "ppl_x6"),
        splitDayLabels = listOf("Empuje"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Empuje hipertrofia clásico",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-push-ppl",
            name = "Push Day · PPL",
            parts = listOf(
                part("p-push-1", "Pecho + Hombros", "#1B4965", listOf(
                    ex("p1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("p1e1", 3, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("p1-ex2", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("p1e2", 2, 10, 7.5), restTime = 120, damageProfile = DamageProfile.STRETCH),
                    ex("p1-ex3", "Elevaciones Laterales de Pie", "standing_lateral_raise__cable",
                        sets = nSets("p1e3", 2, 12, 8.5), restTime = 75),
                    ex("p1-ex4", "Aperturas en Máquina Pec Deck", "flat_chest_fly__machine",
                        nSets("p1e4", 2, 12, 8.0), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-push-2", "Tríceps", "#1F3A2E", listOf(
                    ex("p3-ex1", "Extensión de Tríceps en Polea Alta", "triceps_pushdown__bilateral__cable",
                        nSets("p3e1", 2, 12, 8.5), restTime = 90),
                )),
            ),
        ),
    ),

    // ── 2. Pull Day ───────────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-pull-ppl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pull Day · PPL",
        description = "Día de Tirón con énfasis en Espalda y Bíceps. Tracción vertical, horizontal y trabajo de bíceps.",
        emoji = "🫷",
        tags = listOf(
            SessionTemplateTag.TIRON,
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.BRAZOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 22,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Espalda · Bíceps · Romboides",
        sortOrder = 20,
        splitIds = listOf("ppl_ul", "ppl_arnold", "ppl_x3", "ppl_x6"),
        splitDayLabels = listOf("Tirón"),
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Tracciones hipertrofia clásica",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-pull-ppl",
            name = "Pull Day · PPL",
            parts = listOf(
                part("p-pull-1", "Tirón principal", "#0F3D5E", listOf(
                    ex("pu1-ex1", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("pu1e1", 2, 10, 7.5), restTime = 120),
                    ex("pu1-ex2", "Remo con Pecho Apoyado con Mancuernas", "chest_supported_row__dumbbells__medium",
                        nSets("pu1e2", 2, 8, 7.5), restTime = 150),
                    ex("pu1-ex3", "Curl Predicador con Barra EZ", "preacher_curl__barbell",
                        nSets("pu1e3", 2, 10, 8.5), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("pu1-ex4", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("pu1e4", 2, 15, 8.0), restTime = 90),
                    ex("pu1-ex5", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("pu1e5", 2, 12, 8.0), restTime = 90),
                    ex("pu1-ex6", "Curl Martillo de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("pu1e6", 2, 12, 8.0), restTime = 90),
                )),
            ),
        ),
    ),

    // ── 3. Leg Day (Cuádriceps dominante) ─────────────────────────────────────
    SessionTemplate(
        id = "sys-legs-quad",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day · Cuádriceps",
        description = "Día de Pierna con énfasis en Cuádriceps: sentadilla, bisagra, aislamientos, aductores y gemelos en patrón A-B-A-B.",
        emoji = "🦵",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.CUADRICEPS,
            SessionTemplateTag.GLUTEOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 28,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Aductores",
        sortOrder = 30,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_arnold", "phat_hybrid", "arnold_ul", "glute_focus", "ppl_x6"),
        splitDayLabels = listOf("Pierna", "Lower", "Cuádriceps/Glúteo"),
        focusCategory = SessionTemplateFocusCategory.CUADRICEPS,
        shortDescription = "Tren inferior énfasis cuádriceps",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-quad",
            name = "Leg Day · Cuádriceps",
            parts = listOf(
                part("p-lq-1", "Compuestos", "#7F1D1D", listOf(
                    ex("lq1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("lq1e1", 3, 6, 8.0), restTime = 180, damageProfile = DamageProfile.STRETCH),
                    ex("lq1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lq1e2", 2, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                )),
                part("p-lq-2", "Aislamientos", "#1E3A8A", listOf(
                    ex("lq2-ex1", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lq2e1", 2, 12, 8.5), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                    ex("lq2-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lq2e2", 2, 12, 8.5), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("lq2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lq2e3", 2, 12, 8.5), restTime = 75),
                    ex("lq2-ex4", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lq2e4", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 4. Leg Day (Isquios / Peso Muerto dominante) ──────────────────────────
    SessionTemplate(
        id = "sys-legs-hinge",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day · Isquios",
        description = "Día de Pierna con énfasis en Isquiosurales: RDL, prensa ligera, curl, hip thrust, aductores y gemelos.",
        emoji = "🔩",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.GLUTEOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 27,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Isquios · Glúteos · Aductores · Pantorrillas",
        sortOrder = 40,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_arnold", "ppl_x6", "glute_focus"),
        splitDayLabels = listOf("Pierna", "Lower"),
        focusCategory = SessionTemplateFocusCategory.ISQUIOS,
        shortDescription = "Tren inferior énfasis isquiosurales",
        primaryFocusMuscle = "Isquiosurales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-hinge",
            name = "Leg Day · Isquios",
            parts = listOf(
                part("p-lh-1", "Compuestos", "#244B3C", listOf(
                    ex("lh1-ex1", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lh1e1", 3, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("lh1-ex2", "Prensa de Piernas a 45º en Máquina", "quads_prensa_piernas__bilateral",
                        nSets("lh1e2", 2, 10, 7.0), restTime = 120),
                    ex("lh1-ex3", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("lh1e3", 2, 10, 8.0), restTime = 120, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-lh-2", "Aislamientos", "#1B4965", listOf(
                    ex("lh2-ex1", "Curl Femoral de Pie en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lh2e1", 2, 10, 8.5), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("lh2-ex2", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lh2e2", 2, 12, 8.5), restTime = 75),
                    ex("lh2-ex3", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lh2e3", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 5. Upper Body (Torso Completo A) ──────────────────────────────────────
    SessionTemplate(
        id = "sys-upper-a",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Torso A · Upper/Lower",
        description = "Sesión de Torso para un split Upper/Lower. Énfasis en Pecho y Espalda con trabajo accesorio de Hombros y Brazos.",
        emoji = "💪",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.ESPALDA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 30,
        exerciseCount = 8,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Bíceps · Tríceps",
        sortOrder = 50,
        splitIds = listOf("ul_x4", "ppl_ul", "phat_hybrid", "arnold_ul", "beach_body"),
        splitDayLabels = listOf("Torso", "Upper", "Upper Completo"),
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Upper balanceado pecho y espalda",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-a",
            name = "Torso A",
            parts = listOf(
                part("p-ua-1", "Pecho + Espalda", "#1B4965", listOf(
                    ex("ua1-ex1", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("ua1e1", 3, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ua1-ex2", "Remo en Máquina", "conventional_row__machine",
                        nSets("ua1e2", 2, 8, 7.5), restTime = 120),
                    ex("ua1-ex3", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ua1e3", 2, 10, 7.5), restTime = 120),
                    ex("ua1-ex4", "Jalón al Pecho en Polea (Agarre Ancho)", "lat_pulldown__bilateral__cable",
                        nSets("ua1e4", 2, 10, 7.5), restTime = 90),
                )),
                part("p-ua-2", "Hombros + Brazos", "#5B2A86", listOf(
                    ex("ua2-ex2", "Elevaciones Laterales Sentado en Máquina", "seated_lateral_raise__machine",
                        nSets("ua2e2", 2, 12, 8.5), restTime = 60),
                    ex("ua2-ex3", "Curl de Bíceps de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("ua2e3", 2, 10, 8.5), restTime = 75, damageProfile = DamageProfile.STRETCH),
                    ex("ua2-ex4", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("ua2e4", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 6. Full Body Base ─────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-fullbody-base",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Full Body · Base",
        description = "Sesión de Cuerpo Completo equilibrada. Un movimiento por patrón motor principal: empuje, tirón y dominante de pierna.",
        emoji = "🏗️",
        tags = listOf(
            SessionTemplateTag.CUERPO_COMPLETO,
            SessionTemplateTag.FUERZA,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.PRINCIPIANTE,
        estimatedDurationMinutes = 25,
        exerciseCount = 6,
        partCount = 3,
        muscleGroupsSummary = "Cuerpo Completo",
        sortOrder = 60,
        splitIds = listOf("fullbody_x3", "phat_hybrid", "minimalist_x2", "weekend_warrior"),
        splitDayLabels = listOf("Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C", "Cuerpo Completo", "Full Body", "Full Body A", "Full Body B"),
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Cuerpo completo base principiante",
        session = Session(
            id = "tpl-fullbody-base",
            name = "Full Body · Base",
            parts = listOf(
                part("p-fb-1", "Pierna", "#7F1D1D", listOf(
                    ex("fb1-ex1", "Sentadilla Copa con Mancuerna (Goblet Squat)", "quads_sentadilla_copa__default",
                        nSets("fb1e1", 2, 8, 7.0), restTime = 150),
                    ex("fb1-ex2", "Hip Thrust en Máquina", "hip_thrust__bilateral__barbell",
                        nSets("fb1e2", 2, 10, 7.5), restTime = 120),
                )),
                part("p-fb-2", "Empuje", "#1B4965", listOf(
                    ex("fb2-ex1", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("fb2e1", 2, 8, 7.0), restTime = 120, damageProfile = DamageProfile.STRETCH),
                    ex("fb2-ex2", "Aperturas en Máquina Pec Deck", "flat_chest_fly__machine",
                        nSets("fb2e2", 2, 12, 7.5), restTime = 75),
                )),
                part("p-fb-3", "Tirón", "#244B3C", listOf(
                    ex("fb3-ex1", "Jalón al Pecho en Máquina (Agarre Ancho)", "lat_pulldown__bilateral__machine",
                        nSets("fb3e1", 2, 8, 7.0), restTime = 120),
                    ex("fb3-ex2", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("fb3e2", 2, 12, 7.5), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 7. SBD Powerlifting Day ───────────────────────────────────────────────
    SessionTemplate(
        id = "sys-sbd-pl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "SBD · Powerlifting",
        description = "Sesión de Squat–Bench–Deadlift con intensidad moderada-alta. Ideal como día de práctica técnica o acumulación de volumen específico.",
        emoji = "🏋️",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.SENTADILLA,
            SessionTemplateTag.BANCA,
            SessionTemplateTag.PESO_MUERTO,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.AVANZADO,
        estimatedDurationMinutes = 41,
        exerciseCount = 6,
        partCount = 3,
        muscleGroupsSummary = "Sentadilla · Banca · Peso Muerto",
        sortOrder = 70,
        splitIds = listOf("pl_sbd_x3", "sheiko_3day", "korte_3x3"),
        splitDayLabels = listOf("SBD Día 1", "SBD Día 2", "SBD Día 3", "SBD (Volumen)", "SBD (Técnica)", "SBD (Intensidad)", "Sentadilla/Banca", "Peso Muerto/Banca"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "SBD competitivo volumen moderado",
        session = Session(
            id = "tpl-sbd-pl",
            name = "SBD Powerlifting",
            parts = listOf(
                part("p-sbd-1", "Sentadilla", "#7F1D1D", listOf(
                    ex("sbd1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("sbd1e1", 3, 3, 8.0), restTime = 210),
                    ex("sbd1-ex2", "Sentadilla Frontal con Barra Recta", "front_squat__barbell",
                        nSets("sbd1e2", 2, 5, 7.0), restTime = 180),
                )),
                part("p-sbd-2", "Banca", "#1B4965", listOf(
                    ex("sbd2-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("sbd2e1", 3, 3, 8.0), restTime = 210),
                    ex("sbd2-ex2", "Floor Press con Barra", "floor_press__barbell",
                        nSets("sbd2e2", 2, 5, 7.0), restTime = 150),
                )),
                part("p-sbd-3", "Peso Muerto", "#244B3C", listOf(
                    ex("sbd3-ex1", "Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell",
                        nSets("sbd3e1", 2, 3, 8.0), restTime = 240),
                    ex("sbd3-ex2", "Peso Muerto Sumo con Barra Recta", "sumo_deadlift__barbell",
                        nSets("sbd3e2", 2, 6, 7.0), restTime = 150),
                )),
            ),
        ),
    ),

    // ── 8. Minimalista Fuerza ─────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-minimalist-strength",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Minimalista · Fuerza",
        description = "Sesión de 4 movimientos fundamentales, baja duración, alta intensidad. Ideal cuando el tiempo es limitado.",
        emoji = "⚡",
        tags = listOf(
            SessionTemplateTag.CUERPO_COMPLETO,
            SessionTemplateTag.FUERZA,
            SessionTemplateTag.MINIMALISTA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 40,
        exerciseCount = 4,
        partCount = 0,
        muscleGroupsSummary = "Cuerpo Completo",
        sortOrder = 80,
        splitIds = listOf("minimalist_x2"),
        splitDayLabels = listOf("Full Body A", "Full Body B"),
        focusCategory = SessionTemplateFocusCategory.MINIMALISTA,
        shortDescription = "Entrenamiento de fuerza minimalista",
        session = Session(
            id = "tpl-minimalist",
            name = "Minimalista · Fuerza",
            exercises = listOf(
                ex("min-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                    nSets("mine1", 3, 5, 8.0), restTime = 180),
                ex("min-ex2", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                    nSets("mine2", 3, 5, 8.0), restTime = 150),
                ex("min-ex3", "Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell",
                    nSets("mine3", 3, 5, 8.0), restTime = 180),
                ex("min-ex4", "Dominadas Pronas", "pull_up__pronated__medium",
                    nSets("mine4", 3, 6, 7.5), restTime = 120),
            ),
        ),
    ),

    // ── 9. Pecho Day (Hipertrofia) ─────────────────────────────────────────────
    SessionTemplate(
        id = "sys-chest-pec",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho Day",
        description = "Sesión dedicada al pecho con énfasis en hipertrofia. Varias angulaciones para desarrollo completo sin exceder el volumen recomendado.",
        emoji = "🫁",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 19,
        exerciseCount = 5,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 110,
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Pecho"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Pecho completo hipertrofia",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-chest-pec",
            name = "Pecho Day",
            parts = listOf(
                part("p-ch-1", "Pecho principal", "#1B4965", listOf(
                    ex("ch1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("ch1e1", 2, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ch1-ex2", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("ch1e2", 2, 10, 7.5), restTime = 90),
                    ex("ch1-ex3", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("ch1e3", 2, 10, 7.5), restTime = 120, damageProfile = DamageProfile.STRETCH),
                    ex("ch1-ex4", "Cruce de Poleas en Polea Alta", "tren_superior_cruce_poleas__high",
                        nSets("ch1e4", 2, 12, 8.5), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-ch-2", "Tríceps", "#4A1942", listOf(
                    ex("ch2-ex1", "Extensión de Tríceps Overhead en Polea", "overhead_triceps__barbell",
                        nSets("ch2e1", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 10. Legs Day (Completo) ───────────────────────────────────────────────
    SessionTemplate(
        id = "sys-legs-complete",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs Day · Completo",
        description = "Sesión completa de piernas: Cuádriceps, Isquios, Glúteos y Pantorrillas de forma equilibrada.",
        emoji = "🦵",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.CUADRICEPS,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.GLUTEOS,
            SessionTemplateTag.GEMELOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 29,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Pantorrillas",
        sortOrder = 120,
        splitIds = listOf("arnold_ul"),
        splitDayLabels = listOf("Lower"),
        focusCategory = SessionTemplateFocusCategory.PIERNAS,
        shortDescription = "Tren inferior completo",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-complete",
            name = "Legs Day · Completo",
            parts = listOf(
                part("p-lc-1", "Compuestos", "#7F1D1D", listOf(
                    ex("lc1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("lc1e1", 2, 6, 8.0), restTime = 180, damageProfile = DamageProfile.STRETCH),
                    ex("lc1-ex2", "Peso Muerto Rumano Estilo Sumo con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lc1e2", 2, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("lc1-ex3", "Prensa de Piernas Horizontal en Máquina", "quads_prensa_piernas__bilateral",
                        nSets("lc1e3", 2, 10, 7.5), restTime = 120),
                )),
                part("p-lc-2", "Aislamientos", "#244B3C", listOf(
                    ex("lc2-ex1", "Curl Femoral Tumbado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lc2e1", 2, 12, 8.5), restTime = 90),
                    ex("lc2-ex2", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lc2e2", 2, 12, 8.5), restTime = 90),
                    ex("lc2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lc2e3", 2, 12, 8.0), restTime = 75),
                    ex("lc2-ex4", "Elevación de Talones Sentado en Máquina", "calf_raise__bilateral__machine",
                        nSets("lc2e4", 2, 15, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 11. Pecho/Espalda (Nuevo Arquetipo) ─────────────────────────────────────
    SessionTemplate(
        id = "sys-chest-back-arnold",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho / Espalda",
        description = "Sesión clásica del split Arnold. Agrupa antagonistas de torso para un volumen de empuje y tirón altamente efectivo.",
        emoji = "⚖️",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.AVANZADO,
        estimatedDurationMinutes = 23,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Espalda",
        sortOrder = 130,
        splitIds = listOf("ppl_arnold", "arnold_ul", "beach_body"),
        splitDayLabels = listOf("Pecho/Espalda"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Pecho y espalda antagonistas",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-chest-back-arnold",
            name = "Pecho / Espalda",
            parts = listOf(
                part("p-cba-1", "Antagonistas", "#1B4965", listOf(
                    ex("cba1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("cba1e1", 2, 8, 8.0), restTime = 120),
                    ex("cba1-ex2", "Remo Pendlay con Barra Recta", "pendlay_row__barbell",
                        nSets("cba1e2", 2, 8, 8.0), restTime = 120),
                    ex("cba1-ex3", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("cba1e3", 2, 10, 7.5), restTime = 120),
                    ex("cba1-ex4", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("cba1e4", 2, 12, 8.0), restTime = 90),
                    ex("cba1-ex5", "Aperturas Planas con Mancuernas", "flat_chest_fly__dumbbells",
                        nSets("cba1e5", 2, 12, 8.5), restTime = 90),
                    ex("cba1-ex6", "Remo en Polea", "conventional_row__cable",
                        nSets("cba1e6", 2, 12, 7.5), restTime = 90),
                )),
            ),
        ),
    ),

    // ── 12. Hombro/Brazo (Nuevo Arquetipo) ─────────────────────────────────────
    SessionTemplate(
        id = "sys-shoulder-arms-arnold",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Hombro / Brazo",
        description = "Sesión estética de brazos y deltoides dedicados. Máxima congestión y aislamiento muscular.",
        emoji = "💪",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HOMBROS,
            SessionTemplateTag.BRAZOS,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 19,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Hombros · Bíceps · Tríceps",
        sortOrder = 140,
        splitIds = listOf("ppl_arnold", "arnold_ul", "beach_body"),
        splitDayLabels = listOf("Hombro/Brazo", "Hombros/Brazos"),
        focusCategory = SessionTemplateFocusCategory.BRAZOS,
        shortDescription = "Hombros y brazos estética",
        session = Session(
            id = "tpl-shoulder-arms-arnold",
            name = "Hombro / Brazo",
            parts = listOf(
                part("p-saa-1", "Hombros", "#4A1942", listOf(
                    ex("saa1-ex1", "Press de Hombros Sentado con Barra Recta", "seated_shoulder_press__barbell",
                        nSets("saa1e1", 2, 10, 7.5), restTime = 90),
                )),
                part("p-saa-2", "Brazos", "#5B2A86", listOf(
                    ex("saa2-ex1", "Curl Bayesian", "biceps_curl_bayesian__dumbbells__supinated",
                        nSets("saa2e1", 2, 10, 8.5), restTime = 75),
                    ex("saa2-ex2", "Extensión de Tríceps Overhead con Barra EZ", "overhead_triceps__barbell",
                        nSets("saa2e2", 2, 12, 8.5), restTime = 75),
                    ex("saa2-ex3", "Curl Concentrado con Mancuernas", "concentration_curl__dumbbells",
                        nSets("saa2e3", 2, 12, 8.5), restTime = 60),
                    ex("saa2-ex4", "Patada de Tríceps con Mancuerna", "triceps_patada__dumbbells__bilateral",
                        nSets("saa2e4", 2, 12, 8.5), restTime = 75),
                    ex("saa2-ex5", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("saa2e5", 2, 15, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 13. Hombros/Abs (Nuevo Arquetipo) ──────────────────────────────────────
    SessionTemplate(
        id = "sys-shoulders-abs-glute",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Hombros / Abs",
        description = "Sesión complementaria para el split de especialización de glúteos. Deltoides redondos y fortalecimiento del core.",
        emoji = "🛡️",
        tags = listOf(
            SessionTemplateTag.HOMBROS,
            SessionTemplateTag.CORE,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.PRINCIPIANTE,
        estimatedDurationMinutes = 16,
        exerciseCount = 4,
        partCount = 2,
        muscleGroupsSummary = "Hombros · Abdominales · Core",
        sortOrder = 150,
        splitIds = listOf("glute_focus"),
        splitDayLabels = listOf("Hombros/Abs"),
        focusCategory = SessionTemplateFocusCategory.HOMBROS,
        shortDescription = "Accesorios de hombros y core",
        session = Session(
            id = "tpl-shoulders-abs-glute",
            name = "Hombros / Abs",
            parts = listOf(
                part("p-sag-1", "Hombros", "#4A1942", listOf(
                    ex("sag1-ex1", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("sag1e1", 2, 10, 7.0), restTime = 90),
                    ex("sag1-ex2", "Elevaciones Laterales de Pie en Máquina", "seated_lateral_raise__machine",
                        nSets("sag1e2", 2, 15, 7.5), restTime = 60),
                )),
                part("p-sag-2", "Core", "#1B4965", listOf(
                    ex("sag2-ex1", "Press Pallof en Polea", "core_press_pallof__default",
                        nSets("sag2e1", 3, 12, 7.0), restTime = 60),
                    ex("sag2-ex2", "Plancha Frontal Isométrica", "core_plancha__default",
                        nSets("sag2e2", 3, 30, 7.0), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 14. Glúteo/Isquios (Nuevo Arquetipo) ────────────────────────────────────
    SessionTemplate(
        id = "sys-glutes-hamstrings-spec",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Glúteo / Isquios",
        description = "Sesión especializada de glúteos e isquiotibiales. Máximo desarrollo en cadena posterior.",
        emoji = "🍑",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.GLUTEOS,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 15,
        exerciseCount = 4,
        partCount = 2,
        muscleGroupsSummary = "Glúteos · Isquiosurales",
        sortOrder = 160,
        splitIds = listOf("glute_focus"),
        splitDayLabels = listOf("Glúteo/Isquios"),
        focusCategory = SessionTemplateFocusCategory.GLUTEOS,
        shortDescription = "Tren inferior cadena posterior glúteo",
        session = Session(
            id = "tpl-glutes-hamstrings-spec",
            name = "Glúteo / Isquios",
            parts = listOf(
                part("p-ghs-1", "Cadena Posterior Base", "#244B3C", listOf(
                    ex("ghs1-ex1", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("ghs1e1", 2, 10, 8.0), restTime = 120),
                    ex("ghs1-ex2", "Peso Muerto Rumano Estilo Sumo con Mancuernas", "romanian_deadlift__bilateral__barbell",
                        nSets("ghs1e2", 2, 8, 8.0), restTime = 120),
                )),
                part("p-ghs-2", "Aislamiento y Bombeo", "#4A1942", listOf(
                    ex("ghs2-ex1", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("ghs2e1", 2, 12, 8.5), restTime = 90),
                    ex("ghs2-ex2", "Abducción de Cadera de Pie en Polea", "hip_abduction__standing__cable__unilateral",
                        nSets("ghs2e2", 2, 15, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 15. Glúteo Pump (Nuevo Arquetipo) ───────────────────────────────────────
    SessionTemplate(
        id = "sys-glute-pump",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Glúteo Pump",
        description = "Sesión metabólica y focalizada en bombeo de glúteos e isquiosurales, excelente para cierre semanal.",
        emoji = "🔥",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.GLUTEOS,
            SessionTemplateTag.GEMELOS,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 17,
        exerciseCount = 3,
        partCount = 2,
        muscleGroupsSummary = "Glúteos · Pantorrillas",
        sortOrder = 170,
        splitIds = listOf("glute_focus"),
        splitDayLabels = listOf("Glúteo Pump"),
        focusCategory = SessionTemplateFocusCategory.GLUTEOS,
        shortDescription = "Bombeo estético de glúteos",
        primaryFocusMuscle = "Glúteos",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-glute-pump",
            name = "Glúteo Pump",
            parts = listOf(
                part("p-gp-1", "Activación y Rango", "#4A1942", listOf(
                    ex("gp1-ex1", "Hip Thrust Unilateral con Mancuerna", "hip_thrust__bilateral__barbell",
                        nSets("gp1e1", 2, 12, 8.0), restTime = 105),
                    ex("gp1-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("gp1e2", 2, 12, 8.0), restTime = 90),
                    ex("gp1-ex3", "Hiperextensión a 45º", "glutes_hiperextension_45__plate",
                        nSets("gp1e3", 2, 12, 8.0), restTime = 90),
                )),
                part("p-gp-2", "Aislamiento Estético", "#5B2A86", listOf(
                    ex("gp2-ex1", "Patada de Glúteo en Polea", "glutes_patada_gluteo__cable",
                        nSets("gp2e1", 2, 15, 8.5), restTime = 75),
                    ex("gp2-ex2", "Elevación de Talones Burro (Donkey Calf Raise) en Máquina", "calf_raise__bilateral__machine",
                        nSets("gp2e2", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 16. Torso Liviano (Nuevo Arquetipo) ────────────────────────────────────
    SessionTemplate(
        id = "sys-upper-light-glute",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Torso Liviano",
        description = "Sesión de mantenimiento de torso con volumen y fatiga reducidos, ideal para priorizar la recuperación de las piernas.",
        emoji = "🌬️",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.PRINCIPIANTE,
        estimatedDurationMinutes = 23,
        exerciseCount = 5,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Brazos",
        sortOrder = 180,
        splitIds = listOf("glute_focus", "beach_body"),
        splitDayLabels = listOf("Torso Liviano", "Pierna Mantenimiento"),
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Mantenimiento torso fatiga mínima",
        session = Session(
            id = "tpl-upper-light-glute",
            name = "Torso Liviano",
            parts = listOf(
                part("p-ulg-1", "Empuje y Tirón", "#1B4965", listOf(
                    ex("ulg1-ex1", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ulg1e1", 3, 10, 6.5), restTime = 90),
                    ex("ulg1-ex2", "Jalón al Pecho en Máquina (Agarre Cerrado)", "lat_pulldown__bilateral__machine",
                        nSets("ulg1e2", 3, 10, 7.0), restTime = 90),
                )),
                part("p-ulg-2", "Accesorios Estéticos", "#4A1942", listOf(
                    ex("ulg2-ex1", "Elevaciones Laterales Sentado con Mancuernas", "seated_lateral_raise__machine",
                        nSets("ulg2e1", 3, 12, 7.5), restTime = 60),
                    ex("ulg2-ex2", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("ulg2e2", 2, 12, 7.5), restTime = 60),
                    ex("ulg2-ex3", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("ulg2e3", 2, 12, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 17. Banca Volumen (Nuevo Arquetipo) ────────────────────────────────────
    SessionTemplate(
        id = "sys-bench-volume-pl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Banca Volumen",
        description = "Acumulación de volumen específico en press de banca plano y trabajo general de tracciones complementarias.",
        emoji = "📊",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.BANCA,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 30,
        exerciseCount = 4,
        partCount = 2,
        muscleGroupsSummary = "Banca · Espalda · Hombros",
        sortOrder = 190,
        splitIds = listOf("pl_classic_4"),
        splitDayLabels = listOf("Banca Volumen"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Fuerza y volumen de press banca",
        session = Session(
            id = "tpl-bench-volume-pl",
            name = "Banca Volumen",
            parts = listOf(
                part("p-bvp-1", "Banca Principal", "#1B4965", listOf(
                    ex("bvp1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("bvp1e1", 5, 5, 8.0), restTime = 180),
                )),
                part("p-bvp-2", "Accesorios y Tracciones", "#244B3C", listOf(
                    ex("bvp2-ex1", "Remo Seal con Mancuernas", "seal_row__dumbbells",
                        nSets("bvp2e1", 3, 10, 8.0), restTime = 90),
                    ex("bvp2-ex2", "Fondos en Paralelas", "tren_superior_fondos__default",
                        nSets("bvp2e2", 2, 10, 7.5), restTime = 90),
                    ex("bvp2-ex3", "Encogimientos de Hombros con Mancuernas", "back_encogimientos__dumbbells",
                        nSets("bvp2e3", 3, 12, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 18. Sentadilla/Banca (Nuevo Arquetipo) ──────────────────────────────────
    SessionTemplate(
        id = "sys-squat-bench-pl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Sentadilla / Banca",
        description = "Sesión de acumulación dual de sentadilla y banca. El núcleo de la progresión de fuerza clássica.",
        emoji = "⚙️",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.SENTADILLA,
            SessionTemplateTag.BANCA,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 33,
        exerciseCount = 3,
        partCount = 2,
        muscleGroupsSummary = "Sentadilla · Banca · Espalda",
        sortOrder = 200,
        splitIds = listOf("pl_classic_4"),
        splitDayLabels = listOf("Sentadilla/Banca", "Sentadilla/Peso Muerto"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Sentadilla y banca acumulación",
        session = Session(
            id = "tpl-squat-bench-pl",
            name = "Sentadilla / Banca",
            parts = listOf(
                part("p-sbp-1", "Sentadilla & Banca", "#7F1D1D", listOf(
                    ex("sbp1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("sbp1e1", 4, 5, 8.0), restTime = 180),
                    ex("sbp1-ex2", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("sbp1e2", 4, 5, 8.0), restTime = 180),
                )),
                part("p-sbp-2", "Espalda Accesoria", "#244B3C", listOf(
                    ex("sbp2-ex1", "Remo en Barra T", "t_bar_row__t_bar__medium",
                        nSets("sbp2e1", 3, 10, 7.5), restTime = 120),
                )),
            ),
        ),
    ),

    // ── 19. Peso Muerto (Nuevo Arquetipo) ──────────────────────────────────────
    SessionTemplate(
        id = "sys-deadlift-pl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Peso Muerto",
        description = "Día dedicado al Peso Muerto y accesorios de tracción pesados y empujes inclinados.",
        emoji = "🏗️",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.PESO_MUERTO,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 23,
        exerciseCount = 3,
        partCount = 2,
        muscleGroupsSummary = "Peso Muerto · Banca Inclinada",
        sortOrder = 210,
        splitIds = listOf("pl_classic_4"),
        splitDayLabels = listOf("Peso Muerto"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Peso muerto y banca inclinada",
        session = Session(
            id = "tpl-deadlift-pl",
            name = "Peso Muerto",
            parts = listOf(
                part("p-dlp-1", "Peso Muerto", "#244B3C", listOf(
                    ex("dlp1-ex1", "Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell",
                        nSets("dlp1e1", 3, 5, 8.0), restTime = 210),
                )),
                part("p-dlp-2", "Accesorios", "#1B4965", listOf(
                    ex("dlp2-ex1", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("dlp2e1", 3, 8, 7.5), restTime = 120),
                    ex("dlp2-ex2", "Dominadas Neutras", "pull_up__pronated__medium",
                        nSets("dlp2e2", 3, 8, 7.5), restTime = 120),
                )),
            ),
        ),
    ),

    // ── 20. T1 Sentadilla (GZCL) ───────────────────────────────────────────────
    SessionTemplate(
        id = "sys-t1-squat-gzcl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "T1 Sentadilla · GZCL",
        description = "Sesión GZCL: Sentadilla como T1 de alta intensidad, Peso Muerto Rumano como T2 de volumen moderado, y tracción T3.",
        emoji = "👑",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.SENTADILLA,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 23,
        exerciseCount = 3,
        partCount = 0,
        muscleGroupsSummary = "Sentadilla · Isquios · Espalda",
        sortOrder = 220,
        splitIds = listOf("gzcl_method"),
        splitDayLabels = listOf("T1 Sentadilla"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Sentadilla T1 + RDL T2",
        session = Session(
            id = "tpl-t1-squat-gzcl",
            name = "T1 Sentadilla · GZCL",
            exercises = listOf(
                ex("gzs-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                    nSets("gzse1", 3, 3, 8.5), restTime = 180),
                ex("gzs-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                    nSets("gzse2", 3, 8, 7.5), restTime = 120),
                ex("gzs-ex3", "Remo Gironda", "gironda_row__medium",
                    nSets("gzse3", 4, 12, 8.0), restTime = 75),
            ),
        ),
    ),

    // ── 21. T1 Banca (GZCL) ───────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-t1-bench-gzcl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "T1 Banca · GZCL",
        description = "Sesión GZCL: Press de Banca Plano como T1, Press de Banca Inclinado como T2 de volumen y Jalón al pecho como T3.",
        emoji = "👑",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.BANCA,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 24,
        exerciseCount = 3,
        partCount = 0,
        muscleGroupsSummary = "Banca · Pectoral Superior · Espalda",
        sortOrder = 230,
        splitIds = listOf("gzcl_method"),
        splitDayLabels = listOf("T1 Banca"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Press banca T1 + Inclinado T2",
        session = Session(
            id = "tpl-t1-bench-gzcl",
            name = "T1 Banca · GZCL",
            exercises = listOf(
                ex("gzb-ex1", "Press de Banca con Barra", "bench_press__barbell",
                    nSets("gzbe1", 3, 3, 8.5), restTime = 180),
                ex("gzb-ex2", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                    nSets("gzbe2", 3, 8, 7.5), restTime = 120),
                ex("gzb-ex3", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                    nSets("gzbe3", 4, 12, 8.0), restTime = 75),
            ),
        ),
    ),

    // ── 22. T1 Peso Muerto (GZCL) ──────────────────────────────────────────────
    SessionTemplate(
        id = "sys-t1-deadlift-gzcl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "T1 Peso Muerto · GZCL",
        description = "Sesión GZCL: Peso Muerto como T1 pesado, Sentadilla como T2 de acumulación técnica y tracciones unilaterales como T3.",
        emoji = "👑",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.PESO_MUERTO,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 24,
        exerciseCount = 3,
        partCount = 0,
        muscleGroupsSummary = "Peso Muerto · Sentadilla · Dorsales",
        sortOrder = 240,
        splitIds = listOf("gzcl_method"),
        splitDayLabels = listOf("T1 Peso Muerto"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Peso muerto T1 + Sentadilla T2",
        session = Session(
            id = "tpl-t1-deadlift-gzcl",
            name = "T1 Peso Muerto · GZCL",
            exercises = listOf(
                ex("gzd-ex1", "Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell",
                    nSets("gzde1", 3, 3, 8.5), restTime = 210),
                ex("gzd-ex2", "Sentadilla Frontal con Barra Recta", "front_squat__barbell",
                    nSets("gzde2", 3, 8, 7.0), restTime = 150),
                ex("gzd-ex3", "Extensión Lumbar en Máquina", "back_extension_lumbar__default",
                    nSets("gzde3", 3, 12, 7.5), restTime = 75),
            ),
        ),
    ),

    // ── 23. T1 Militar (GZCL) ──────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-t1-military-gzcl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "T1 Militar · GZCL",
        description = "Sesión GZCL: Press Militar como T1 de fuerza de hombros, Press de Banca Plano como T2 e hipertrofia lateral de hombros como T3.",
        emoji = "👑",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.HOMBROS,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 22,
        exerciseCount = 3,
        partCount = 0,
        muscleGroupsSummary = "Militar · Banca · Hombros",
        sortOrder = 250,
        splitIds = listOf("gzcl_method"),
        splitDayLabels = listOf("T1 Militar"),
        focusCategory = SessionTemplateFocusCategory.POWERLIFTING,
        shortDescription = "Press militar T1 + Banca T2",
        session = Session(
            id = "tpl-t1-military-gzcl",
            name = "T1 Militar · GZCL",
            exercises = listOf(
                ex("gzm-ex1", "Press Militar de Pie con Barra Recta", "military_press__barbell",
                    nSets("gzme1", 3, 3, 8.5), restTime = 180),
                ex("gzm-ex2", "Floor Press con Mancuernas", "floor_press__dumbbells",
                    nSets("gzme2", 3, 8, 7.5), restTime = 120),
                ex("gzm-ex3", "Elevaciones Laterales de Pie", "standing_lateral_raise__cable",
                    sets = nSets("gzme3", 4, 12, 8.5), restTime = 60),
            ),
        ),
    ),

    // ── 24. Cadena Anterior (Anterior / Posterior Split) ─────────────────────────
    SessionTemplate(
        id = "sys-ant-chain-ap",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Cadena Anterior",
        description = "Sesión enfocada en la musculatura anterior: Cuádriceps, Pecho, Hombro anterior y empujes generales.",
        emoji = "🛡️",
        tags = listOf(
            SessionTemplateTag.CUERPO_COMPLETO,
            SessionTemplateTag.CUADRICEPS,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 21,
        exerciseCount = 5,
        partCount = 2,
        muscleGroupsSummary = "Cuádriceps · Pecho · Hombros",
        sortOrder = 260,
        splitIds = listOf("ant_post_x4"),
        splitDayLabels = listOf("Cadena Anterior"),
        focusCategory = SessionTemplateFocusCategory.CADENA_ANTERIOR,
        shortDescription = "Cadena anterior cuádriceps y pecho",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-ant-chain-ap",
            name = "Cadena Anterior",
            parts = listOf(
                part("p-aca-1", "Empujes Pierna + Torso", "#7F1D1D", listOf(
                    ex("aca1-ex1", "Sentadilla en Máquina Hack", "quads_sentadilla_hack__machine",
                        nSets("aca1e1", 2, 8, 8.0), restTime = 150),
                    ex("aca1-ex2", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("aca1e2", 2, 8, 8.0), restTime = 120),
                    ex("aca1-ex3", "Prensa de Piernas a 45º en Máquina", "quads_prensa_piernas__bilateral",
                        nSets("aca1e3", 2, 10, 7.5), restTime = 120),
                    ex("aca1-ex4", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("aca1e4", 2, 10, 7.5), restTime = 90),
                    ex("aca1-ex5", "Aperturas en Máquina Pec Deck", "flat_chest_fly__machine",
                        nSets("aca1e5", 2, 12, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 25. Cadena Posterior (Anterior / Posterior Split) ────────────────────────
    SessionTemplate(
        id = "sys-post-chain-ap",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Cadena Posterior",
        description = "Sesión completa para toda la cadena posterior: Peso Muerto, Tracciones de espalda pesadas e Isquiotibiales.",
        emoji = "🏹",
        tags = listOf(
            SessionTemplateTag.CUERPO_COMPLETO,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 24,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Isquios · Espalda · Lumbares",
        sortOrder = 270,
        splitIds = listOf("ant_post_x4"),
        splitDayLabels = listOf("Cadena Posterior"),
        focusCategory = SessionTemplateFocusCategory.CADENA_POSTERIOR,
        shortDescription = "Cadena posterior peso muerto y tracciones",
        primaryFocusMuscle = "Isquiosurales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-post-chain-ap",
            name = "Cadena Posterior",
            parts = listOf(
                part("p-pca-1", "Cadena Posterior Pesada", "#244B3C", listOf(
                    ex("pca1-ex1", "Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell",
                        nSets("pca1e1", 2, 5, 8.0), restTime = 180),
                    ex("pca1-ex2", "Remo Pendlay con Barra Recta", "pendlay_row__barbell",
                        nSets("pca1e2", 2, 8, 8.0), restTime = 120),
                    ex("pca1-ex3", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("pca1e3", 2, 10, 8.0), restTime = 120),
                )),
                part("p-pca-2", "Aislamiento y Tracción", "#0F3D5E", listOf(
                    ex("pca2-ex1", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("pca2e1", 2, 10, 7.5), restTime = 120),
                    ex("pca2-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("pca2e2", 2, 12, 8.5), restTime = 75),
                    ex("pca2-ex3", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("pca2e3", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    )
)

private fun independentTemplate(
    sourceId: String,
    id: String,
    name: String,
    category: SessionTemplateFocusCategory,
    sortOrder: Int,
    shortDescription: String,
    estimatedDurationMinutes: Int? = null,
): SessionTemplate {
    val source = SESSION_TEMPLATES_BASE.first { it.id == sourceId }
    return source.copy(
        id = id,
        name = name,
        description = source.description,
        // Solo el valor explícito; null → finalizedTemplate deriva del contenido
        // (la duración de la fuente completa no aplica a la variante recortada).
        estimatedDurationMinutes = estimatedDurationMinutes,
        sortOrder = sortOrder,
        splitIds = emptyList(),
        splitDayLabels = emptyList(),
        focusCategory = category,
        shortDescription = shortDescription,
        session = source.session.copy(
            id = "tpl-$id",
            name = name,
        ),
    )
}

/**
 * Variante de alta frecuencia: conserva ~8–12 series (2 por ejercicio por defecto),
 * sin recortar a 1 serie. Duración se deriva del contenido vía [finalizedTemplate].
 */
private fun lowVolumeSplitTemplate(
    sourceId: String,
    id: String,
    name: String,
    splitIds: List<String>,
    splitDayLabels: List<String>,
    category: SessionTemplateFocusCategory,
    sortOrder: Int,
    shortDescription: String,
    estimatedDurationMinutes: Int? = null,
): SessionTemplate {
    val source = SESSION_TEMPLATES_BASE.first { it.id == sourceId }
    val maxSets = LOW_VOLUME_MIN_SETS // 8 series: caben en 8–12 y no revientan splits de alta frecuencia

    var session = mapSessionExercises(source.session) { exercise ->
        val kept = exercise.sets.take(LOW_VOLUME_TARGET_SETS_PER_EXERCISE)
        exercise.copy(sets = if (kept.isNotEmpty()) kept else exercise.sets)
    }.copy(id = "tpl-$id", name = name)

    val sourceById = source.session.allExercises().associateBy { it.id }

    // Subir hasta 8 series añadiendo de a una serie (sin saltar a 3 en todos a la vez).
    var growGuard = 0
    while (sessionTotalSets(session) < LOW_VOLUME_MIN_SETS && growGuard < 24) {
        var added = false
        session = mapSessionExercises(session) { exercise ->
            if (added) return@mapSessionExercises exercise
            val original = sourceById[exercise.id] ?: return@mapSessionExercises exercise
            if (exercise.sets.size >= original.sets.size) return@mapSessionExercises exercise
            added = true
            exercise.copy(sets = original.sets.take(exercise.sets.size + 1))
        }
        if (!added) break
        growGuard++
    }

    // Bajar hasta maxSets: primero quitar series de cola, luego ejercicios.
    var guard = 0
    while (sessionTotalSets(session) > maxSets && guard < 32) {
        val exercises = session.allExercises()
        val trimCandidate = exercises.lastOrNull { it.sets.size > 1 }
        session = if (trimCandidate != null) {
            mapSessionExercises(session) { exercise ->
                if (exercise.id == trimCandidate.id) exercise.copy(sets = exercise.sets.dropLast(1))
                else exercise
            }
        } else {
            dropTrailingExercise(session)
        }
        guard++
    }

    return source.copy(
        id = id,
        name = name,
        sortOrder = sortOrder,
        splitIds = splitIds,
        splitDayLabels = splitDayLabels,
        focusCategory = category,
        shortDescription = shortDescription,
        // Solo el valor explícito; null → finalizedTemplate deriva del contenido.
        estimatedDurationMinutes = estimatedDurationMinutes,
        weeklyVolumePolicyId = "high_freq_low",
        session = session,
    )
}

private val SESSION_TEMPLATES_DERIVED_SPLIT: List<SessionTemplate> = listOf(
    lowVolumeSplitTemplate(
        sourceId = "sys-upper-light-glute",
        id = "sys-ul-x6-torso-low",
        name = "Torso · Compacta",
        splitIds = listOf("ul_x6"),
        splitDayLabels = listOf("Torso"),
        category = SessionTemplateFocusCategory.FULL_BODY,
        sortOrder = -100,
        shortDescription = "Torso corto para frecuencia alta",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-legs-quad",
        id = "sys-ul-x6-leg-low",
        name = "Pierna · Compacta",
        splitIds = listOf("ul_x6"),
        splitDayLabels = listOf("Pierna"),
        category = SessionTemplateFocusCategory.PIERNAS,
        sortOrder = -99,
        shortDescription = "Pierna corta para frecuencia alta",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-fullbody-base",
        id = "sys-fullbody-x5-low",
        name = "Full · Compacta",
        splitIds = listOf("fullbody_x5"),
        splitDayLabels = listOf("Cuerpo Completo"),
        category = SessionTemplateFocusCategory.FULL_BODY,
        sortOrder = -98,
        shortDescription = "Full body corto para frecuencia alta",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-ant-chain-ap",
        id = "sys-ant-chain-x6-low",
        name = "Anterior · Compacta",
        splitIds = listOf("ant_post_x6"),
        splitDayLabels = listOf("Cadena Anterior"),
        category = SessionTemplateFocusCategory.CADENA_ANTERIOR,
        sortOrder = -97,
        shortDescription = "Cadena anterior corta para frecuencia alta",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-post-chain-ap",
        id = "sys-post-chain-x6-low",
        name = "Posterior · Compacta",
        splitIds = listOf("ant_post_x6"),
        splitDayLabels = listOf("Cadena Posterior"),
        category = SessionTemplateFocusCategory.CADENA_POSTERIOR,
        sortOrder = -96,
        shortDescription = "Cadena posterior corta para frecuencia alta",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-push-ppl",
        id = "sys-push-arnold-low",
        name = "Empuje · Compacta",
        splitIds = listOf("ppl_arnold"),
        splitDayLabels = listOf("Empuje"),
        category = SessionTemplateFocusCategory.PECHO,
        sortOrder = -95,
        shortDescription = "Empuje controlado para splits de alto volumen",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-pull-ppl",
        id = "sys-pull-arnold-low",
        name = "Tirón · Compacta",
        splitIds = listOf("ppl_arnold"),
        splitDayLabels = listOf("Tirón"),
        category = SessionTemplateFocusCategory.ESPALDA,
        sortOrder = -94,
        shortDescription = "Tirón controlado para splits de alto volumen",
        estimatedDurationMinutes = 13,
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-legs-quad",
        id = "sys-legs-arnold-low",
        name = "Pierna · Compacta (split)",
        splitIds = listOf("ppl_arnold", "arnold_classic_6", "bro_split", "dorian_yates"),
        splitDayLabels = listOf("Pierna", "Piernas"),
        category = SessionTemplateFocusCategory.PIERNAS,
        sortOrder = -93,
        shortDescription = "Pierna controlada para splits de alto volumen",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-chest-back-arnold",
        id = "sys-chest-back-high-volume-low",
        name = "Pecho / Espalda · Compacta",
        splitIds = listOf("ppl_arnold", "arnold_ul", "arnold_classic_6"),
        splitDayLabels = listOf("Pecho/Espalda"),
        category = SessionTemplateFocusCategory.PECHO,
        sortOrder = -92,
        shortDescription = "Antagonistas de torso con volumen controlado",
        estimatedDurationMinutes = 13,
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-shoulder-arms-arnold",
        id = "sys-shoulder-arms-high-volume-low",
        name = "Hombro / Brazo · Compacta",
        splitIds = listOf("ppl_arnold", "arnold_ul", "arnold_classic_6", "dorian_yates"),
        splitDayLabels = listOf("Hombro/Brazo", "Hombros/Brazos", "Hombro/Tríceps"),
        category = SessionTemplateFocusCategory.BRAZOS,
        sortOrder = -91,
        shortDescription = "Deltoides y brazos con volumen controlado",
        estimatedDurationMinutes = 11,
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-shoulders-abs-glute",
        id = "sys-shoulders-bro-low",
        name = "Hombros · Compacta",
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Hombros"),
        category = SessionTemplateFocusCategory.HOMBROS,
        sortOrder = -90,
        shortDescription = "Hombros dedicados con volumen controlado",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-shoulder-arms-arnold",
        id = "sys-arms-bro-low",
        name = "Brazos · Compacta",
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Brazos"),
        category = SessionTemplateFocusCategory.BRAZOS,
        sortOrder = -89,
        shortDescription = "Brazos dedicados con volumen controlado",
        estimatedDurationMinutes = 11,
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-chest-pec",
        id = "sys-chest-bro-low",
        name = "Pecho · Compacta",
        splitIds = listOf("bro_split", "dorian_yates"),
        splitDayLabels = listOf("Pecho", "Pecho/Bíceps"),
        category = SessionTemplateFocusCategory.PECHO,
        sortOrder = -88,
        shortDescription = "Pecho dedicado con volumen controlado",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-pull-ppl",
        id = "sys-back-bro-low",
        name = "Espalda · Compacta",
        splitIds = listOf("bro_split", "dorian_yates"),
        splitDayLabels = listOf("Espalda"),
        category = SessionTemplateFocusCategory.ESPALDA,
        sortOrder = -87,
        shortDescription = "Espalda dedicada con volumen controlado",
        estimatedDurationMinutes = 13,
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-squat-bench-pl",
        id = "sys-russian-bear-squat-bench-low",
        name = "Sentadilla / Banca · Compacta",
        splitIds = listOf("russian_bear"),
        splitDayLabels = listOf("Sentadilla/Banca"),
        category = SessionTemplateFocusCategory.POWERLIFTING,
        sortOrder = -86,
        shortDescription = "Sentadilla y banca con volumen semanal controlado",
    ),
    lowVolumeSplitTemplate(
        sourceId = "sys-deadlift-pl",
        id = "sys-russian-bear-deadlift-press-low",
        name = "Peso Muerto / Press · Compacta",
        splitIds = listOf("russian_bear"),
        splitDayLabels = listOf("Peso Muerto/Press"),
        category = SessionTemplateFocusCategory.POWERLIFTING,
        sortOrder = -85,
        shortDescription = "Bisagra y press con volumen semanal controlado",
    ),
)

private val SESSION_TEMPLATES_INDEPENDENT: List<SessionTemplate> = listOf(
    independentTemplate(
        sourceId = "sys-legs-complete",
        id = "sys-independent-legs",
        name = "Enfoque Piernas",
        category = SessionTemplateFocusCategory.PIERNAS,
        sortOrder = 1_010,
        shortDescription = "Piernas completas sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-shoulder-arms-arnold",
        id = "sys-independent-arms",
        name = "Enfoque Brazos",
        category = SessionTemplateFocusCategory.BRAZOS,
        sortOrder = 1_020,
        shortDescription = "Bíceps y tríceps sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-glutes-hamstrings-spec",
        id = "sys-independent-glutes",
        name = "Enfoque Glúteos",
        category = SessionTemplateFocusCategory.GLUTEOS,
        sortOrder = 1_030,
        shortDescription = "Glúteos e isquios sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-chest-pec",
        id = "sys-independent-chest",
        name = "Enfoque Pecho",
        category = SessionTemplateFocusCategory.PECHO,
        sortOrder = 1_040,
        shortDescription = "Pecho completo sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-pull-ppl",
        id = "sys-independent-back",
        name = "Enfoque Espalda",
        category = SessionTemplateFocusCategory.ESPALDA,
        sortOrder = 1_050,
        shortDescription = "Tracciones de espalda sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-shoulders-abs-glute",
        id = "sys-independent-shoulders",
        name = "Enfoque Hombros",
        category = SessionTemplateFocusCategory.HOMBROS,
        sortOrder = 1_060,
        shortDescription = "Hombros y core sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-fullbody-base",
        id = "sys-independent-fullbody",
        name = "Enfoque Full Body",
        category = SessionTemplateFocusCategory.FULL_BODY,
        sortOrder = 1_070,
        shortDescription = "Cuerpo completo sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-sbd-pl",
        id = "sys-independent-powerlifting",
        name = "Enfoque Powerlifting",
        category = SessionTemplateFocusCategory.POWERLIFTING,
        sortOrder = 1_080,
        shortDescription = "SBD técnico sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-minimalist-strength",
        id = "sys-independent-minimalist",
        name = "Enfoque Minimalista",
        category = SessionTemplateFocusCategory.MINIMALISTA,
        sortOrder = 1_090,
        shortDescription = "Dosis mínima efectiva sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-upper-light-glute",
        id = "sys-independent-recovery",
        name = "Enfoque Recuperación",
        category = SessionTemplateFocusCategory.RECUPERACION,
        sortOrder = 1_100,
        shortDescription = "Sesión liviana para recuperar ritmo",
    ),
    independentTemplate(
        sourceId = "sys-ant-chain-ap",
        id = "sys-independent-anterior-chain",
        name = "Enfoque Cadena Anterior",
        category = SessionTemplateFocusCategory.CADENA_ANTERIOR,
        sortOrder = 1_110,
        shortDescription = "Empujes de cuádriceps y torso sin split",
    ),
    independentTemplate(
        sourceId = "sys-post-chain-ap",
        id = "sys-independent-posterior-chain",
        name = "Enfoque Cadena Posterior",
        category = SessionTemplateFocusCategory.CADENA_POSTERIOR,
        sortOrder = 1_120,
        shortDescription = "Bisagras y tracciones sin split",
    ),
    independentTemplate(
        sourceId = "sys-legs-quad",
        id = "sys-independent-quads",
        name = "Enfoque Cuádriceps",
        category = SessionTemplateFocusCategory.CUADRICEPS,
        sortOrder = 1_130,
        shortDescription = "Cuádriceps sin split asociado",
    ),
    independentTemplate(
        sourceId = "sys-legs-hinge",
        id = "sys-independent-hams",
        name = "Enfoque Isquios",
        category = SessionTemplateFocusCategory.ISQUIOS,
        sortOrder = 1_140,
        shortDescription = "Isquiosurales sin split asociado",
    ),
)

/** Plantillas nuevas F2: focos musculares + variantes principiante/avanzado. */
private val SESSION_TEMPLATES_EXPANDED: List<SessionTemplate> = listOf(

    SessionTemplate(
        id = "sys-calves-focus",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pantorrillas · Enfoque",
        description = "Sesión dedicada a sóleo y gemelos: trabajo de pie, sentado y en prensa para cubrir ambos rangos.",
        emoji = "🦶",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.GEMELOS, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.HYPERFOCUSED),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Pantorrillas · Sóleo",
        sortOrder = 1_200,
        focusCategory = SessionTemplateFocusCategory.PANTORRILLAS,
        shortDescription = "Volumen dedicado de pantorrillas",
        primaryFocusMuscle = "Pantorrillas",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-calves-focus",
            name = "Pantorrillas · Enfoque",
            parts = listOf(
                part("p-cal-1", "Gemelos de pie", "#1F3A2E", listOf(
                    ex("cal1-ex1", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("cale1", 2, 12, 8.5), restTime = 75),
                    ex("cal1-ex2", "Elevación de Talones de Pie con Mancuernas", "calf_raise__bilateral__machine",
                        nSets("cale2", 2, 15, 8.5), restTime = 60),
                )),
                part("p-cal-2", "Sóleo y variantes", "#244B3C", listOf(
                    ex("cal2-ex1", "Elevación de Talones Sentado en Máquina", "calf_raise__bilateral__machine",
                        nSets("cale3", 3, 15, 8.5), restTime = 60),
                    ex("cal2-ex2", "Elevación de Talones en Prensa a 45º", "calf_raise__bilateral__machine",
                        nSets("cale4", 3, 12, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-core-focus",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Core · Abdomen",
        description = "Estabilidad anti-rotación, flexión y control lumbar. Ideal como sesión corta o accesorio semanal.",
        emoji = "🎯",
        tags = listOf(SessionTemplateTag.CORE, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.HYPERFOCUSED),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Abdomen · Core · Oblicuos",
        sortOrder = 1_210,
        focusCategory = SessionTemplateFocusCategory.CORE,
        shortDescription = "Core anti-rotación y flexión",
        primaryFocusMuscle = "Abdomen",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-core-focus",
            name = "Core · Abdomen",
            parts = listOf(
                part("p-core-1", "Anti-rotación", "#1B4965", listOf(
                    ex("co1-ex1", "Press Pallof en Polea", "core_press_pallof__default",
                        nSets("coe1", 3, 12, 7.5), restTime = 60),
                    ex("co1-ex2", "Leñador en Polea (Woodchopper) de Alta a Baja", "core_lenador_polea__default",
                        nSets("coe2", 3, 10, 8.0), restTime = 60),
                )),
                part("p-core-2", "Flexión y control", "#4A1942", listOf(
                    ex("co2-ex1", "Crunch Abdominal en Máquina", "core_crunch_maquina__default",
                        nSets("coe3", 3, 12, 8.0), restTime = 60),
                    ex("co2-ex2", "Elevación de Piernas Colgado en Barra", "core_elevacion_piernas__default",
                        nSets("coe4", 3, 10, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-forearms-focus",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Antebrazos · Grip",
        description = "Flexores, extensores y agarre: curls de muñeca, farmer walks y suspensión isométrica.",
        emoji = "✊",
        tags = listOf(SessionTemplateTag.BRAZOS, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.HYPERFOCUSED),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Antebrazos · Agarre",
        sortOrder = 1_220,
        focusCategory = SessionTemplateFocusCategory.ANTEBRAZOS,
        shortDescription = "Antebrazos y agarre dedicado",
        primaryFocusMuscle = "Antebrazos",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-forearms-focus",
            name = "Antebrazos · Grip",
            parts = listOf(
                part("p-fa-1", "Flexores / extensores", "#5B2A86", listOf(
                    ex("fa1-ex1", "Curl de Muñeca Sentado con Barra Recta", "forearms_curl_muneca_sentado__barbell",
                        nSets("fae1", 3, 15, 8.0), restTime = 60),
                    ex("fa1-ex2", "Extensión de Muñeca con Barra EZ", "forearms_curl_muneca_inverso_sentado__ez_bar",
                        nSets("fae2", 3, 15, 8.0), restTime = 60),
                    ex("fa1-ex3", "Curl de Muñeca Sentado con Mancuernas", "forearms_curl_muneca_sentado__barbell",
                        nSets("fae3", 3, 12, 8.0), restTime = 60),
                )),
                part("p-fa-2", "Agarre", "#1F3A2E", listOf(
                    ex("fa2-ex1", "Enrollamiento de Muñeca con Rodillo y Cuerda", "forearms_enrollamiento_muneca_rodillo__default",
                        nSets("fae4", 3, 10, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-adductors-focus",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Aductores · Enfoque",
        description = "Trabajo dedicado de aductores con máquina, polea y estabilidad tipo Copenhagen.",
        emoji = "🧲",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.HYPERFOCUSED),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Aductores · Estabilidad de cadera",
        sortOrder = 1_230,
        focusCategory = SessionTemplateFocusCategory.ADUCTORES,
        shortDescription = "Aductores y control de cadera",
        primaryFocusMuscle = "Aductores",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-adductors-focus",
            name = "Aductores · Enfoque",
            parts = listOf(
                part("p-ad-1", "Aducción primaria", "#7F1D1D", listOf(
                    ex("ad1-ex1", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("ade1", 3, 12, 8.5), restTime = 75),
                    ex("ad1-ex2", "Aducción de Cadera de Pie en Polea", "hip_adduction__standing__cable__unilateral",
                        nSets("ade2", 3, 12, 8.5), restTime = 60),
                )),
                part("p-ad-2", "Estabilidad", "#244B3C", listOf(
                    ex("ad2-ex1", "Plancha Copenhague", "copenhagen_plank__default",
                        nSets("ade3", 3, 20, 7.5), restTime = 75),
                    ex("ad2-ex2", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("ade4", 2, 15, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-push-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Push · Principiante",
        description = "Empuje estable con máquinas y mancuernas. Intensidad moderada (RPE 6.5–8) para aprender patrones.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.EMPUJE, SessionTemplateTag.PECHO, SessionTemplateTag.HOMBROS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 1_240,
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Empuje principiante con máquinas",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-push-beginner",
            name = "Push · Principiante",
            parts = listOf(
                part("p-pb-1", "Pecho + Hombros", "#1B4965", listOf(
                    ex("pb1-ex1", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("pbe1", 2, 10, 7.0), restTime = 120),
                    ex("pb1-ex2", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("pbe2", 2, 10, 7.0), restTime = 90),
                    ex("pb1-ex3", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("pbe3", 2, 12, 7.0), restTime = 90),
                    ex("pb1-ex4", "Elevaciones Laterales Sentado en Máquina", "seated_lateral_raise__machine",
                        nSets("pbe4", 2, 12, 7.5), restTime = 60),
                    ex("pb1-ex5", "Aperturas en Máquina Pec Deck", "flat_chest_fly__machine",
                        nSets("pbe5", 2, 12, 7.5), restTime = 75),
                    ex("pb1-ex6", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("pbe6", 2, 12, 7.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-push-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Push · Avanzado",
        description = "Mayor volumen de empuje con barra, mancuernas y aislamientos. Sin técnicas especiales no modeladas.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.EMPUJE, SessionTemplateTag.PECHO, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Pecho · Deltoides · Tríceps",
        sortOrder = 1_250,
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Empuje avanzado alto estímulo",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-push-advanced",
            name = "Push · Avanzado",
            parts = listOf(
                part("p-pa-1", "Empuje", "#1B4965", listOf(
                    ex("pa1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("pae1", 3, 6, 8.5), restTime = 180),
                    ex("pa1-ex2", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("pae2", 2, 8, 7.5), restTime = 120),
                    ex("pa1-ex3", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("pae3", 3, 8, 8.5), restTime = 120),
                    ex("pa1-ex4", "Elevaciones Laterales Super ROM con Mancuernas", "lateral_raise_super_rom__dumbbells",
                        nSets("pae4", 2, 12, 8.0), restTime = 60),
                    ex("pa1-ex5", "Aperturas Inclinadas con Mancuernas", "incline_chest_fly__dumbbells",
                        nSets("pae5", 2, 12, 8.0), restTime = 90),
                    ex("pa1-ex6", "Extensión de Tríceps Overhead con Mancuerna", "overhead_triceps__barbell",
                        nSets("pae6", 2, 10, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-pull-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pull · Principiante",
        description = "Tirón guiado: jalones y remos en máquina, curls estables. Ideal para consolidar técnica.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TIRON, SessionTemplateTag.ESPALDA, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Espalda · Bíceps",
        sortOrder = 1_260,
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Tirón principiante guiado",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-pull-beginner",
            name = "Pull · Principiante",
            parts = listOf(
                part("p-plb-1", "Tirón guiado", "#0F3D5E", listOf(
                    ex("plb1-ex1", "Jalón al Pecho en Máquina (Agarre Ancho)", "lat_pulldown__bilateral__machine",
                        nSets("plbe1", 2, 10, 7.0), restTime = 120),
                    ex("plb1-ex2", "Remo en Máquina", "conventional_row__machine",
                        nSets("plbe2", 2, 10, 7.0), restTime = 90),
                    ex("plb1-ex3", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("plbe3", 2, 12, 7.5), restTime = 75),
                    ex("plb1-ex4", "Pullover en Máquina", "lying_pullover__dumbbells",
                        nSets("plbe4", 2, 12, 7.5), restTime = 75),
                    ex("plb1-ex5", "Curl Martillo Predicador con Mancuernas", "preacher_curl__barbell",
                        nSets("plbe5", 2, 12, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-pull-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pull · Avanzado",
        description = "Tirón con dominadas, remos pesados y aislamiento de bíceps. Volumen alto controlado.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TIRON, SessionTemplateTag.ESPALDA, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Dorsales · Remo · Bíceps",
        sortOrder = 1_270,
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Tirón avanzado alto estímulo",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-pull-advanced",
            name = "Pull · Avanzado",
            parts = listOf(
                part("p-pla-1", "Tirón", "#0F3D5E", listOf(
                    ex("pla1-ex1", "Dominadas Pronas", "pull_up__pronated__medium",
                        nSets("plae1", 2, 6, 8.5), restTime = 150),
                    ex("pla1-ex2", "Remo Pendlay con Barra Recta", "pendlay_row__barbell",
                        nSets("plae2", 2, 6, 8.5), restTime = 150),
                    ex("pla1-ex3", "Curl Bayesian con Mancuernas", "biceps_curl_bayesian__dumbbells__supinated",
                        nSets("plae3", 2, 10, 8.5), restTime = 75),
                    ex("pla1-ex4", "Remo en Polea", "conventional_row__cable",
                        nSets("plae4", 2, 10, 8.0), restTime = 90),
                    ex("pla1-ex5", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("plae5", 2, 15, 8.0), restTime = 75),
                    ex("pla1-ex6", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("plae6", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs · Principiante",
        description = "Tren inferior estable: prensa, hack, extensiones y curls en máquina. Intensidad controlada.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.CUADRICEPS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Aductores",
        sortOrder = 1_280,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_x3", "fullbody_x3"),
        splitDayLabels = listOf("Pierna", "Lower"),
        focusCategory = SessionTemplateFocusCategory.PIERNAS,
        shortDescription = "Piernas principiante en máquina",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-legs-beginner",
            name = "Legs · Principiante",
            parts = listOf(
                part("p-lb-1", "Máquinas", "#7F1D1D", listOf(
                    ex("lb1-ex1", "Prensa de Piernas a 45º en Máquina", "quads_prensa_piernas__bilateral",
                        nSets("lbe1", 2, 12, 7.0), restTime = 120),
                    ex("lb1-ex2", "Hip Thrust en Máquina", "hip_thrust__bilateral__barbell",
                        nSets("lbe2", 2, 10, 7.5), restTime = 90),
                    ex("lb1-ex3", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lbe3", 2, 12, 7.5), restTime = 75),
                    ex("lb1-ex4", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lbe4", 2, 12, 7.5), restTime = 75),
                    ex("lb1-ex5", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lbe5", 2, 12, 7.5), restTime = 75),
                    ex("lb1-ex6", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lbe6", 2, 15, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs · Avanzado",
        description = "Sentadilla libre, unilaterales y aislamiento pesado. Volumen alto sin exceder caps por músculo.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.CUADRICEPS, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Pantorrillas",
        sortOrder = 1_290,
        focusCategory = SessionTemplateFocusCategory.PIERNAS,
        shortDescription = "Piernas avanzado alto estímulo",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-advanced",
            name = "Legs · Avanzado",
            parts = listOf(
                part("p-la-1", "Compuestos", "#7F1D1D", listOf(
                    ex("la1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("lae1", 3, 5, 8.5), restTime = 180),
                    ex("la1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lae2", 2, 8, 8.5), restTime = 150),
                    ex("la1-ex3", "Sentadilla Búlgara Frontal con Mancuernas", "bulgarian_split_squat__dumbbells",
                        nSets("lae3", 2, 8, 7.5), restTime = 120),
                )),
                part("p-la-2", "Aislamientos", "#244B3C", listOf(
                    ex("la2-ex1", "Curl Femoral Tumbado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lae4", 2, 10, 8.5), restTime = 90),
                    ex("la2-ex2", "Extensión de Cuádriceps Unilateral en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lae5", 2, 12, 8.5), restTime = 75),
                    ex("la2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lae6", 1, 12, 8.5), restTime = 75),
                    ex("la2-ex4", "Elevación de Talones de Pie Unilateral en Máquina", "calf_raise__bilateral__machine",
                        nSets("lae7", 2, 12, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-fullbody-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Full Body · Avanzado",
        description = "Un patrón motor exigente por bloque: sentadilla, press, tirón y bisagra con accesorios densos.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.CUERPO_COMPLETO, SessionTemplateTag.FUERZA, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Cuerpo completo · Fuerza",
        sortOrder = 1_300,
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Full body avanzado denso",
        session = Session(
            id = "tpl-fullbody-advanced",
            name = "Full Body · Avanzado",
            parts = listOf(
                part("p-fba-1", "Pierna", "#7F1D1D", listOf(
                    ex("fba1-ex1", "Sentadilla Frontal con Barra Recta", "front_squat__barbell",
                        nSets("fbae1", 3, 5, 8.0), restTime = 180),
                    ex("fba1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("fbae2", 3, 6, 8.0), restTime = 150),
                )),
                part("p-fba-2", "Empuje", "#1B4965", listOf(
                    ex("fba2-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("fbae3", 3, 5, 8.0), restTime = 150),
                    ex("fba2-ex2", "Press Militar con Mancuernas", "military_press__dumbbells",
                        nSets("fbae4", 2, 8, 7.5), restTime = 90),
                )),
                part("p-fba-3", "Tirón", "#244B3C", listOf(
                    ex("fba3-ex1", "Dominadas Pronas", "pull_up__pronated__medium",
                        nSets("fbae5", 3, 6, 8.0), restTime = 120),
                    ex("fba3-ex2", "Remo Seal con Barra Recta", "seal_row__barbell",
                        nSets("fbae6", 3, 8, 8.0), restTime = 90),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Torso · Principiante",
        description = "Upper body guiado para Upper/Lower: máquinas de pecho/espalda y accesorios estables.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.PECHO, SessionTemplateTag.ESPALDA, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Brazos",
        sortOrder = 1_310,
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Torso principiante máquinas",
        primaryFocusMuscle = null,
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-upper-beginner",
            name = "Torso · Principiante",
            parts = listOf(
                part("p-ub-1", "Empuje / tirón", "#1B4965", listOf(
                    ex("ub1-ex1", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ube1", 2, 10, 7.0), restTime = 120),
                    ex("ub1-ex2", "Remo en Máquina", "conventional_row__machine",
                        nSets("ube2", 2, 10, 7.0), restTime = 90),
                    ex("ub1-ex3", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ube3", 2, 12, 7.0), restTime = 90),
                    ex("ub1-ex4", "Jalón al Pecho en Máquina (Agarre Cerrado)", "lat_pulldown__bilateral__machine",
                        nSets("ube4", 2, 12, 7.0), restTime = 75),
                )),
                part("p-ub-2", "Accesorios", "#5B2A86", listOf(
                    ex("ub2-ex1", "Elevaciones Laterales de Pie en Máquina", "seated_lateral_raise__machine",
                        nSets("ube5", 2, 12, 7.5), restTime = 60),
                    ex("ub2-ex2", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("ube6", 2, 12, 7.5), restTime = 60),
                    ex("ub2-ex3", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("ube7", 2, 12, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── F1 matriz: Lower SHORT + Glúteo STANDARD + Upper focos ────────────────

    SessionTemplate(
        id = "sys-legs-quad-short",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs · Cuádriceps Short",
        description = "Sesión corta de cuádriceps: prensa, RDL, extensión y curl en ~45 minutos.",
        emoji = "⏱️",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.CUADRICEPS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Cuádriceps · Isquios",
        sortOrder = 1_320,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_x6"),
        splitDayLabels = listOf("Pierna", "Lower"),
        focusCategory = SessionTemplateFocusCategory.CUADRICEPS,
        shortDescription = "Cuádriceps corto estándar",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-quad-short",
            name = "Legs · Cuádriceps Short",
            parts = listOf(
                part("p-lqs-1", "Short lower", "#7F1D1D", listOf(
                    ex("lqs1-ex1", "Prensa de Piernas Horizontal en Máquina", "quads_prensa_piernas__bilateral",
                        nSets("lqse1", 2, 10, 7.5), restTime = 120),
                    ex("lqs1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lqse2", 2, 8, 8.0), restTime = 120),
                    ex("lqs1-ex3", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lqse3", 2, 12, 8.5), restTime = 75),
                    ex("lqs1-ex4", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lqse4", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-glute-short",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs · Glúteos Short",
        description = "Sesión corta de glúteos: hip thrust, RDL, patada y gemelos sin rachas de 3.",
        emoji = "⏱️",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.GLUTEOS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Glúteos · Isquios · Pantorrillas",
        sortOrder = 1_330,
        splitIds = listOf("ul_x4", "ppl_ul", "glute_focus"),
        splitDayLabels = listOf("Pierna", "Lower", "Glúteos"),
        focusCategory = SessionTemplateFocusCategory.GLUTEOS,
        shortDescription = "Glúteos corto estándar",
        primaryFocusMuscle = "Glúteos",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-glute-short",
            name = "Legs · Glúteos Short",
            parts = listOf(
                part("p-lgs-1", "Short glute", "#4A1942", listOf(
                    ex("lgs1-ex1", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("lgse1", 2, 10, 8.0), restTime = 120),
                    ex("lgs1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lgse2", 2, 8, 8.0), restTime = 120),
                    ex("lgs1-ex3", "Patada de Glúteo en Polea", "glutes_patada_gluteo__cable",
                        nSets("lgse3", 2, 15, 8.5), restTime = 75),
                    ex("lgs1-ex4", "Curl Femoral Tumbado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lgse4", 2, 12, 8.5), restTime = 75),
                    ex("lgs1-ex5", "Elevación de Talones Sentado en Máquina", "calf_raise__bilateral__machine",
                        nSets("lgse5", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-ham-short",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs · Isquios Short",
        description = "Sesión corta de isquios: RDL, curl, hip thrust y gemelos.",
        emoji = "⏱️",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.ISQUIOTIBIALES, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Isquios · Glúteos · Pantorrillas",
        sortOrder = 1_340,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_x6"),
        splitDayLabels = listOf("Pierna", "Lower"),
        focusCategory = SessionTemplateFocusCategory.ISQUIOS,
        shortDescription = "Isquios corto estándar",
        primaryFocusMuscle = "Isquiosurales",
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-ham-short",
            name = "Legs · Isquios Short",
            parts = listOf(
                part("p-lhs-1", "Short hams", "#244B3C", listOf(
                    ex("lhs1-ex1", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lhse1", 2, 8, 8.0), restTime = 150),
                    ex("lhs1-ex2", "Hip Thrust en Máquina", "hip_thrust__bilateral__barbell",
                        nSets("lhse2", 2, 10, 8.0), restTime = 120),
                    ex("lhs1-ex3", "Curl Femoral de Pie en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lhse3", 2, 10, 8.5), restTime = 90),
                    ex("lhs1-ex4", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lhse4", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-glute",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day · Glúteos",
        description = "Sesión standard de glúteos: compuestos de cadera primero, aislamiento y cobertura lower.",
        emoji = "🍑",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.GLUTEOS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Glúteos · Isquios · Aductores · Pantorrillas",
        sortOrder = 1_350,
        splitIds = listOf("ul_x4", "ppl_ul", "ppl_arnold", "glute_focus", "ppl_x6"),
        splitDayLabels = listOf("Pierna", "Lower", "Glúteos"),
        focusCategory = SessionTemplateFocusCategory.GLUTEOS,
        shortDescription = "Tren inferior énfasis glúteos",
        primaryFocusMuscle = "Glúteos",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-glute",
            name = "Leg Day · Glúteos",
            parts = listOf(
                part("p-lg-1", "Compuestos glúteo", "#4A1942", listOf(
                    ex("lg1-ex1", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("lge1", 3, 10, 8.0), restTime = 120),
                    ex("lg1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lge2", 2, 8, 8.0), restTime = 150),
                )),
                part("p-lg-2", "Aislamientos", "#244B3C", listOf(
                    ex("lg2-ex1", "Patada de Glúteo en Polea", "glutes_patada_gluteo__cable",
                        nSets("lge3", 2, 12, 8.5), restTime = 75),
                    ex("lg2-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lge4", 2, 12, 8.5), restTime = 90),
                    ex("lg2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lge5", 1, 12, 8.0), restTime = 75),
                    ex("lg2-ex4", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lge6", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-chest-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho · Principiante",
        description = "Foco pecho principiante solo máquina, volumen controlado.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.PECHO, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 1_360,
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Pecho"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Pecho principiante máquinas",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-upper-chest-beginner",
            name = "Pecho · Principiante",
            parts = listOf(
                part("p-ucb-1", "Pecho máquina", "#1B4965", listOf(
                    ex("ucb1-ex1", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ucbe1", 2, 10, 7.0), restTime = 120),
                    ex("ucb1-ex2", "Press de Pecho en Máquina Convergente", "tren_superior_press_pecho_maquina_convergente__default",
                        nSets("ucbe2", 2, 12, 7.0), restTime = 90),
                    ex("ucb1-ex3", "Elevaciones Laterales Sentado en Máquina", "seated_lateral_raise__machine",
                        nSets("ucbe3", 2, 12, 7.5), restTime = 60),
                    ex("ucb1-ex4", "Aperturas en Máquina Pec Deck", "flat_chest_fly__machine",
                        nSets("ucbe4", 2, 12, 7.5), restTime = 75),
                    ex("ucb1-ex5", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("ucbe5", 2, 12, 7.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-chest",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho · Intermedio",
        description = "Foco pecho intermedio con angulaciones y deltoides intercalados.",
        emoji = "🫁",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.PECHO, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 1_370,
        splitIds = listOf("ppl_ul", "ul_x4", "bro_split"),
        splitDayLabels = listOf("Torso", "Upper", "Pecho"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Pecho intermedio hipertrofia",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-chest",
            name = "Pecho · Intermedio",
            parts = listOf(
                part("p-uci-1", "Pecho", "#1B4965", listOf(
                    ex("uci1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("ucie1", 2, 8, 8.0), restTime = 150),
                    ex("uci1-ex2", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("ucie2", 2, 10, 7.5), restTime = 90),
                    ex("uci1-ex3", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("ucie3", 2, 10, 7.5), restTime = 120),
                    ex("uci1-ex4", "Cruce de Poleas en Polea Alta", "tren_superior_cruce_poleas__high",
                        nSets("ucie4", 2, 12, 8.5), restTime = 90),
                    ex("uci1-ex5", "Extensión de Tríceps Overhead en Polea", "overhead_triceps__barbell",
                        nSets("ucie5", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-chest-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho · Avanzado",
        description = "Foco pecho avanzado con volumen ≤8 y RPE compuestos ≤8.5.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.PECHO, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Pecho · Deltoides · Tríceps",
        sortOrder = 1_380,
        splitIds = listOf("bro_split", "ppl_arnold"),
        splitDayLabels = listOf("Pecho", "Pecho/Espalda"),
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Pecho avanzado alto estímulo",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-chest-advanced",
            name = "Pecho · Avanzado",
            parts = listOf(
                part("p-uca-1", "Pecho avanzado", "#1B4965", listOf(
                    ex("uca1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("ucae1", 3, 6, 8.5), restTime = 180),
                    ex("uca1-ex2", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("ucae2", 2, 8, 8.5), restTime = 120),
                    ex("uca1-ex3", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("ucae3", 2, 15, 8.0), restTime = 60),
                    ex("uca1-ex4", "Aperturas Inclinadas con Mancuernas", "incline_chest_fly__dumbbells",
                        nSets("ucae4", 2, 12, 8.0), restTime = 90),
                    ex("uca1-ex5", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("ucae5", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-back-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Espalda · Principiante",
        description = "Foco espalda principiante con jalones y remos en máquina.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.ESPALDA, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Espalda · Bíceps",
        sortOrder = 1_390,
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Espalda"),
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Espalda principiante máquinas",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-upper-back-beginner",
            name = "Espalda · Principiante",
            parts = listOf(
                part("p-ubb-1", "Tirón máquina", "#0F3D5E", listOf(
                    ex("ubb1-ex1", "Jalón al Pecho en Máquina (Agarre Cerrado)", "lat_pulldown__bilateral__machine",
                        nSets("ubbe1", 2, 10, 7.0), restTime = 120),
                    ex("ubb1-ex2", "Remo en Máquina", "conventional_row__machine",
                        nSets("ubbe2", 2, 10, 7.0), restTime = 90),
                    ex("ubb1-ex3", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("ubbe3", 2, 12, 7.5), restTime = 75),
                    ex("ubb1-ex4", "Pullover en Máquina", "lying_pullover__dumbbells",
                        nSets("ubbe4", 2, 12, 7.5), restTime = 75),
                    ex("ubb1-ex5", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("ubbe5", 2, 15, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-back",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Espalda · Intermedio",
        description = "Foco espalda intermedio alternando vertical/horizontal y bíceps.",
        emoji = "🫷",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.ESPALDA, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Dorsales · Remo · Bíceps",
        sortOrder = 1_400,
        splitIds = listOf("ppl_ul", "ul_x4", "bro_split"),
        splitDayLabels = listOf("Torso", "Upper", "Espalda", "Tirón"),
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Espalda intermedio hipertrofia",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-back",
            name = "Espalda · Intermedio",
            parts = listOf(
                part("p-ubi-1", "Espalda", "#0F3D5E", listOf(
                    ex("ubi1-ex1", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("ubie1", 2, 10, 7.5), restTime = 120),
                    ex("ubi1-ex2", "Remo en Máquina", "conventional_row__machine",
                        nSets("ubie2", 2, 10, 7.5), restTime = 90),
                    ex("ubi1-ex3", "Curl de Bíceps de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("ubie3", 2, 10, 8.5), restTime = 75),
                    ex("ubi1-ex4", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("ubie4", 2, 12, 8.0), restTime = 90),
                    ex("ubi1-ex5", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("ubie5", 2, 15, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-back-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Espalda · Avanzado",
        description = "Foco espalda avanzado sin rachas y volumen dorsal ≤8.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.ESPALDA, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Dorsales · Remo · Bíceps",
        sortOrder = 1_410,
        splitIds = listOf("bro_split", "ppl_arnold"),
        splitDayLabels = listOf("Espalda", "Tirón"),
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Espalda avanzado alto estímulo",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-back-advanced",
            name = "Espalda · Avanzado",
            parts = listOf(
                part("p-uba-1", "Espalda avanzada", "#0F3D5E", listOf(
                    ex("uba1-ex1", "Dominadas Pronas", "pull_up__pronated__medium",
                        nSets("ubae1", 2, 6, 8.5), restTime = 150),
                    ex("uba1-ex2", "Remo Seal con Mancuernas", "seal_row__dumbbells",
                        nSets("ubae2", 2, 8, 8.5), restTime = 120),
                    ex("uba1-ex3", "Curl Martillo de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("ubae3", 2, 12, 8.5), restTime = 75),
                    ex("uba1-ex4", "Remo en Polea", "conventional_row__cable",
                        nSets("ubae4", 2, 10, 8.0), restTime = 90),
                    ex("uba1-ex5", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("ubae5", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-delts-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Hombros · Principiante",
        description = "Foco deltoides principiante en máquina.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.HOMBROS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Deltoides · Tríceps",
        sortOrder = 1_420,
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Hombros"),
        focusCategory = SessionTemplateFocusCategory.HOMBROS,
        shortDescription = "Hombros principiante máquinas",
        primaryFocusMuscle = "Deltoides",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-upper-delts-beginner",
            name = "Hombros · Principiante",
            parts = listOf(
                part("p-udb-1", "Delts máquina", "#4A1942", listOf(
                    ex("udb1-ex1", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("udbe1", 2, 10, 7.0), restTime = 90),
                    ex("udb1-ex2", "Elevaciones Laterales de Pie en Máquina", "seated_lateral_raise__machine",
                        nSets("udbe2", 2, 12, 7.5), restTime = 60),
                    ex("udb1-ex3", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("udbe3", 2, 12, 7.5), restTime = 75),
                    ex("udb1-ex4", "Elevaciones Laterales Sentado en Máquina", "seated_lateral_raise__machine",
                        nSets("udbe4", 2, 15, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-delts",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Hombros · Intermedio",
        description = "Foco deltoides intermedio con press, laterales y rear delt.",
        emoji = "🛡️",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.HOMBROS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Deltoides · Tríceps",
        sortOrder = 1_430,
        splitIds = listOf("ppl_ul", "ul_x4", "bro_split"),
        splitDayLabels = listOf("Torso", "Upper", "Hombros"),
        focusCategory = SessionTemplateFocusCategory.HOMBROS,
        shortDescription = "Hombros intermedio hipertrofia",
        primaryFocusMuscle = "Deltoides",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-delts",
            name = "Hombros · Intermedio",
            parts = listOf(
                part("p-udi-1", "Delts", "#4A1942", listOf(
                    ex("udi1-ex1", "Press de Hombros Sentado con Barra Recta", "seated_shoulder_press__barbell",
                        nSets("udie1", 2, 8, 7.5), restTime = 120),
                    ex("udi1-ex2", "Elevaciones Laterales de Pie", "standing_lateral_raise__cable",
                        sets = nSets("udie2", 2, 12, 8.5), restTime = 60),
                    ex("udi1-ex3", "Extensión de Tríceps en Polea Alta", "triceps_pushdown__bilateral__cable",
                        nSets("udie3", 2, 12, 8.5), restTime = 75),
                    ex("udi1-ex4", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("udie4", 2, 15, 8.0), restTime = 75),
                    ex("udi1-ex5", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("udie5", 2, 12, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-delts-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Hombros · Avanzado",
        description = "Foco deltoides avanzado con press pesado y volumen lateral controlado.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.HOMBROS, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Deltoides · Tríceps",
        sortOrder = 1_440,
        splitIds = listOf("bro_split", "ppl_arnold"),
        splitDayLabels = listOf("Hombros", "Hombro/Brazo"),
        focusCategory = SessionTemplateFocusCategory.HOMBROS,
        shortDescription = "Hombros avanzado alto estímulo",
        primaryFocusMuscle = "Deltoides",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-delts-advanced",
            name = "Hombros · Avanzado",
            parts = listOf(
                part("p-uda-1", "Delts avanzado", "#4A1942", listOf(
                    ex("uda1-ex1", "Press Militar de Pie con Barra Recta", "military_press__barbell",
                        nSets("udae1", 3, 5, 8.5), restTime = 150),
                    ex("uda1-ex2", "Elevaciones Laterales Super ROM con Mancuernas", "lateral_raise_super_rom__dumbbells",
                        nSets("udae2", 2, 12, 8.0), restTime = 60),
                    ex("uda1-ex3", "Extensión de Tríceps Overhead con Mancuerna", "overhead_triceps__barbell",
                        nSets("udae3", 2, 10, 8.0), restTime = 75),
                    ex("uda1-ex4", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("udae4", 2, 15, 8.0), restTime = 75),
                    ex("uda1-ex5", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("udae5", 2, 12, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-arms-beginner",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Brazos · Principiante",
        description = "Foco brazos principiante con curls y extensiones en máquina.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.BRAZOS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.PRINCIPIANTE,
        muscleGroupsSummary = "Bíceps · Tríceps",
        sortOrder = 1_450,
        splitIds = listOf("bro_split"),
        splitDayLabels = listOf("Brazos"),
        focusCategory = SessionTemplateFocusCategory.BRAZOS,
        shortDescription = "Brazos principiante máquinas",
        primaryFocusMuscle = "Bíceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MACHINE,
        weeklyVolumePolicyId = "beginner_machine",
        session = Session(
            id = "tpl-upper-arms-beginner",
            name = "Brazos · Principiante",
            parts = listOf(
                part("p-uab-1", "Brazos máquina", "#5B2A86", listOf(
                    ex("uab1-ex1", "Curl Predicador en Máquina", "preacher_curl__barbell",
                        nSets("uabe1", 2, 12, 7.5), restTime = 75),
                    ex("uab1-ex2", "Extensión de Tríceps Overhead en Máquina", "overhead_triceps__machine",
                        nSets("uabe2", 2, 12, 7.5), restTime = 75),
                    ex("uab1-ex3", "Curl Martillo Predicador con Mancuernas", "preacher_curl__barbell",
                        nSets("uabe3", 2, 12, 7.5), restTime = 60),
                    ex("uab1-ex4", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("uabe4", 2, 12, 7.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-arms",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Brazos · Intermedio",
        description = "Foco brazos intermedio alternando bíceps y tríceps.",
        emoji = "💪",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.BRAZOS, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Bíceps · Tríceps",
        sortOrder = 1_460,
        splitIds = listOf("ppl_ul", "ul_x4", "bro_split"),
        splitDayLabels = listOf("Torso", "Upper", "Brazos"),
        focusCategory = SessionTemplateFocusCategory.BRAZOS,
        shortDescription = "Brazos intermedio hipertrofia",
        primaryFocusMuscle = "Bíceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-arms",
            name = "Brazos · Intermedio",
            parts = listOf(
                part("p-uai-1", "Brazos", "#5B2A86", listOf(
                    ex("uai1-ex1", "Curl Bayesian", "biceps_curl_bayesian__dumbbells__supinated",
                        nSets("uaie1", 2, 10, 8.5), restTime = 75),
                    ex("uai1-ex2", "Extensión de Tríceps Overhead con Barra EZ", "overhead_triceps__barbell",
                        nSets("uaie2", 2, 12, 8.5), restTime = 75),
                    ex("uai1-ex3", "Curl Concentrado con Mancuernas", "concentration_curl__dumbbells",
                        nSets("uaie3", 2, 12, 8.5), restTime = 60),
                    ex("uai1-ex4", "Patada de Tríceps con Mancuerna", "triceps_patada__dumbbells__bilateral",
                        nSets("uaie4", 2, 12, 8.5), restTime = 75),
                    ex("uai1-ex5", "Curl Predicador con Barra EZ", "preacher_curl__barbell",
                        nSets("uaie5", 2, 10, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-arms-advanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Brazos · Avanzado",
        description = "Foco brazos avanzado con mayor densidad de aislamientos.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.BRAZOS, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Bíceps · Tríceps",
        sortOrder = 1_470,
        splitIds = listOf("bro_split", "ppl_arnold"),
        splitDayLabels = listOf("Brazos", "Hombro/Brazo"),
        focusCategory = SessionTemplateFocusCategory.BRAZOS,
        shortDescription = "Brazos avanzado alto estímulo",
        primaryFocusMuscle = "Bíceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-arms-advanced",
            name = "Brazos · Avanzado",
            parts = listOf(
                part("p-uaa-1", "Brazos avanzado", "#5B2A86", listOf(
                    ex("uaa1-ex1", "Curl Bayesian con Mancuernas", "biceps_curl_bayesian__dumbbells__supinated",
                        nSets("uaae1", 3, 10, 8.5), restTime = 75),
                    ex("uaa1-ex2", "Extensión de Tríceps Overhead con Mancuerna", "overhead_triceps__barbell",
                        nSets("uaae2", 2, 10, 8.5), restTime = 75),
                    ex("uaa1-ex3", "Curl Martillo de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("uaae3", 2, 12, 8.5), restTime = 60),
                    ex("uaa1-ex4", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("uaae4", 2, 12, 8.5), restTime = 60),
                    ex("uaa1-ex5", "Curl Predicador con Barra EZ", "preacher_curl__barbell",
                        nSets("uaae5", 2, 10, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    // ── Plantillas avanzadas adicionales (especialización / fuerza / deload) ──
    SessionTemplate(
        id = "sys-chest-specialization",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho · Especialización",
        description = "Sesión exigente de pecho con ángulos planos, inclinados y aislamiento de tríceps de apoyo.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.PECHO, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Pecho · Tríceps · Deltoides",
        sortOrder = 1_500,
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Especialización pecho de alto estímulo",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.LONG,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-chest-specialization",
            name = "Pecho · Especialización",
            parts = listOf(
                part("p-cs-1", "Pecho", "#1B4965", listOf(
                    ex("cs1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("cse1", 3, 6, 8.5), restTime = 180),
                    ex("cs1-ex2", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("cse2", 2, 8, 8.5), restTime = 150),
                    ex("cs1-ex3", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("cse3", 2, 15, 8.0), restTime = 60),
                    ex("cs1-ex4", "Cruce de Poleas en Polea Alta", "tren_superior_cruce_poleas__high",
                        nSets("cse4", 2, 12, 8.5), restTime = 90),
                    ex("cs1-ex5", "Extensión de Tríceps Overhead en Polea", "overhead_triceps__barbell",
                        nSets("cse5", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-back-specialization",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Espalda · Especialización",
        description = "Tirón vertical y horizontal con bíceps intercalados para un día de espalda exigente.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.ESPALDA, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Espalda · Bíceps · Deltoides posteriores",
        sortOrder = 1_510,
        focusCategory = SessionTemplateFocusCategory.ESPALDA,
        shortDescription = "Especialización espalda de alto estímulo",
        primaryFocusMuscle = "Dorsales",
        durationClass = SessionTemplateDurationClass.LONG,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-back-specialization",
            name = "Espalda · Especialización",
            parts = listOf(
                part("p-bs-1", "Tirón", "#0F3D5E", listOf(
                    ex("bs1-ex1", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("bse1", 3, 8, 8.5), restTime = 150),
                    ex("bs1-ex2", "Remo con Pecho Apoyado con Mancuernas", "chest_supported_row__dumbbells__medium",
                        nSets("bse2", 2, 8, 8.5), restTime = 150),
                    ex("bs1-ex3", "Curl Predicador con Barra EZ", "preacher_curl__barbell",
                        nSets("bse3", 2, 10, 8.5), restTime = 90),
                    ex("bs1-ex4", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("bse4", 2, 15, 8.0), restTime = 75),
                    ex("bs1-ex5", "Pullover en Polea Alta", "lying_pullover__dumbbells",
                        nSets("bse5", 2, 12, 8.5), restTime = 90),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-legs-specialization",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pierna · Especialización",
        description = "Tren inferior exigente con compuestos, aislamientos y completitud de pantorrillas/aductores.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.CUADRICEPS),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Aductores",
        sortOrder = 1_520,
        focusCategory = SessionTemplateFocusCategory.PIERNAS,
        shortDescription = "Especialización pierna de alto estímulo",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.LONG,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-legs-specialization",
            name = "Pierna · Especialización",
            parts = listOf(
                part("p-ls-1", "Compuestos", "#7F1D1D", listOf(
                    ex("ls1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("lse1", 3, 5, 8.5), restTime = 210),
                    ex("ls1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lse2", 2, 8, 8.5), restTime = 180),
                )),
                part("p-ls-2", "Aislamientos", "#1E3A8A", listOf(
                    ex("ls2-ex1", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lse3", 2, 12, 8.5), restTime = 90),
                    ex("ls2-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lse4", 2, 12, 8.5), restTime = 90),
                    ex("ls2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lse5", 2, 12, 8.5), restTime = 75),
                    ex("ls2-ex4", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lse6", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-upper-volume",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Upper · Volumen",
        description = "Torso completo de alto volumen: empuje, tirón y brazos con densidad controlada.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Brazos",
        sortOrder = 1_530,
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Upper de volumen exigente",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.LONG,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-upper-volume",
            name = "Upper · Volumen",
            parts = listOf(
                part("p-uv-1", "Empuje / Tirón", "#1B4965", listOf(
                    ex("uv1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("uve1", 3, 6, 8.5), restTime = 180),
                    ex("uv1-ex2", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("uve2", 3, 8, 8.5), restTime = 150),
                    ex("uv1-ex3", "Press de Hombros Sentado en Máquina", "seated_shoulder_press__barbell",
                        nSets("uve3", 2, 10, 8.0), restTime = 120),
                    ex("uv1-ex4", "Remo con Pecho Apoyado con Mancuernas", "chest_supported_row__dumbbells__medium",
                        nSets("uve4", 2, 10, 8.0), restTime = 120),
                    ex("uv1-ex5", "Extensión de Tríceps Overhead en Polea", "overhead_triceps__barbell",
                        nSets("uve5", 2, 12, 8.5), restTime = 75),
                    ex("uv1-ex6", "Curl Martillo de Pie con Mancuernas", "standing_biceps_curl__barbell",
                        nSets("uve6", 2, 12, 8.5), restTime = 75),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-lower-strength",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Lower · Fuerza",
        description = "Tren inferior orientado a fuerza: cargas pesadas, pocas repeticiones y accesorios de soporte.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.FUERZA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos",
        sortOrder = 1_540,
        focusCategory = SessionTemplateFocusCategory.PIERNAS,
        shortDescription = "Lower de fuerza exigente",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.FREE,
        weeklyVolumePolicyId = "strength_base",
        session = Session(
            id = "tpl-lower-strength",
            name = "Lower · Fuerza",
            parts = listOf(
                part("p-lf-1", "Fuerza", "#7F1D1D", listOf(
                    ex("lf1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("lfe1", 4, 4, 8.0), restTime = 240),
                    ex("lf1-ex2", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("lfe2", 3, 5, 8.0), restTime = 210),
                )),
                part("p-lf-2", "Soporte", "#1E3A8A", listOf(
                    ex("lf2-ex1", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("lfe3", 2, 10, 8.0), restTime = 90),
                    ex("lf2-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("lfe4", 2, 10, 8.0), restTime = 90),
                    ex("lf2-ex3", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("lfe5", 2, 12, 8.0), restTime = 75),
                    ex("lf2-ex4", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("lfe6", 2, 12, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-deload-active",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Deload · Activo",
        description = "Sesión ligera de recuperación activa: volumen bajo, RPE contenido y cobertura full body.",
        emoji = "🟢",
        tags = listOf(SessionTemplateTag.RECUPERACION, SessionTemplateTag.CUERPO_COMPLETO, SessionTemplateTag.MINIMALISTA),
        difficulty = Difficulty.INTERMEDIO,
        muscleGroupsSummary = "Full body ligero",
        sortOrder = 1_550,
        focusCategory = SessionTemplateFocusCategory.RECUPERACION,
        shortDescription = "Deload útil full body",
        primaryFocusMuscle = null,
        durationClass = SessionTemplateDurationClass.SHORT,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "deload",
        session = Session(
            id = "tpl-deload-active",
            name = "Deload · Activo",
            parts = listOf(
                part("p-da-1", "Full ligero", "#244B3C", listOf(
                    ex("da1-ex1", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("dae1", 2, 8, 6.5), restTime = 120),
                    ex("da1-ex2", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("dae2", 2, 10, 6.5), restTime = 120),
                    ex("da1-ex3", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("dae3", 2, 8, 6.5), restTime = 150),
                    ex("da1-ex4", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("dae4", 2, 12, 6.5), restTime = 60),
                    ex("da1-ex5", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("dae5", 2, 12, 6.5), restTime = 45),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-torso-density",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Torso · Densidad",
        description = "Torso exigente con descansos cortos y contraste empuje/tirón para densidad metabólica.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.TORSO, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.ALTO_VOLUMEN),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Pecho · Espalda · Hombros",
        sortOrder = 1_560,
        focusCategory = SessionTemplateFocusCategory.PECHO,
        shortDescription = "Torso denso y exigente",
        primaryFocusMuscle = "Pectorales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-torso-density",
            name = "Torso · Densidad",
            parts = listOf(
                part("p-td-1", "Densidad", "#1B4965", listOf(
                    ex("td1-ex1", "Press de Banca con Mancuernas", "bench_press__dumbbells",
                        nSets("tde1", 3, 8, 8.5), restTime = 90),
                    ex("td1-ex2", "Remo con Pecho Apoyado con Mancuernas", "chest_supported_row__dumbbells__medium",
                        nSets("tde2", 3, 8, 8.5), restTime = 90),
                    ex("td1-ex3", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("tde3", 2, 15, 8.0), restTime = 45),
                    ex("td1-ex4", "Face Pull en Polea", "deltoides_face_pull__default",
                        nSets("tde4", 2, 15, 8.0), restTime = 45),
                    ex("td1-ex5", "Patada de Tríceps en Polea", "triceps_patada__dumbbells__bilateral",
                        nSets("tde5", 2, 12, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-posterior-emphasis",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Posterior · Énfasis",
        description = "Cadena posterior exigente: bisagra, femoral, glúteo y pantorrillas.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.GLUTEOS),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Isquios · Glúteos · Espalda baja",
        sortOrder = 1_570,
        focusCategory = SessionTemplateFocusCategory.CADENA_POSTERIOR,
        shortDescription = "Énfasis cadena posterior exigente",
        primaryFocusMuscle = "Isquiosurales",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-posterior-emphasis",
            name = "Posterior · Énfasis",
            parts = listOf(
                part("p-pe-1", "Bisagra", "#4A1942", listOf(
                    ex("pe1-ex1", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("pee1", 3, 6, 8.5), restTime = 180),
                    ex("pe1-ex2", "Curl Femoral Sentado en Máquina", "curl_isquios_con_sliders__default",
                        nSets("pee2", 3, 10, 8.5), restTime = 90),
                )),
                part("p-pe-2", "Glúteo y gemelos", "#7F1D1D", listOf(
                    ex("pe2-ex1", "Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell",
                        nSets("pee3", 3, 8, 8.5), restTime = 120),
                    ex("pe2-ex2", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("pee4", 2, 12, 8.0), restTime = 75),
                    ex("pe2-ex3", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("pee5", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-anterior-emphasis",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Anterior · Énfasis",
        description = "Cadena anterior exigente: sentadilla, extensión de cuádriceps y empuje de torso ligero.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.PIERNA, SessionTemplateTag.HIPERTROFIA, SessionTemplateTag.CUADRICEPS),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Cuádriceps · Pecho · Core",
        sortOrder = 1_580,
        focusCategory = SessionTemplateFocusCategory.CADENA_ANTERIOR,
        shortDescription = "Énfasis cadena anterior exigente",
        primaryFocusMuscle = "Cuádriceps",
        durationClass = SessionTemplateDurationClass.STANDARD,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-anterior-emphasis",
            name = "Anterior · Énfasis",
            parts = listOf(
                part("p-ae-1", "Anterior", "#1E3A8A", listOf(
                    ex("ae1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("aee1", 3, 6, 8.5), restTime = 180),
                    ex("ae1-ex2", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("aee3", 2, 8, 8.0), restTime = 150),
                    ex("ae1-ex3", "Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral",
                        nSets("aee2", 3, 12, 8.5), restTime = 90),
                    ex("ae1-ex4", "Aducción de Cadera Sentado en Máquina", "hip_adduction__seated__machine__bilateral",
                        nSets("aee4", 2, 12, 8.0), restTime = 75),
                    ex("ae1-ex5", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("aee5", 2, 15, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    SessionTemplate(
        id = "sys-full-demanding",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Full · Exigente",
        description = "Cuerpo completo exigente con compuestos pesados y accesorios de cobertura.",
        emoji = "🔴",
        tags = listOf(SessionTemplateTag.CUERPO_COMPLETO, SessionTemplateTag.ALTO_VOLUMEN, SessionTemplateTag.HIPERTROFIA),
        difficulty = Difficulty.AVANZADO,
        muscleGroupsSummary = "Full body · Alta demanda",
        sortOrder = 1_590,
        focusCategory = SessionTemplateFocusCategory.FULL_BODY,
        shortDescription = "Full body exigente",
        primaryFocusMuscle = null,
        durationClass = SessionTemplateDurationClass.LONG,
        equipmentBias = SessionTemplateEquipmentBias.MIXED,
        weeklyVolumePolicyId = "hypertrophy_base",
        session = Session(
            id = "tpl-full-demanding",
            name = "Full · Exigente",
            parts = listOf(
                part("p-fd-1", "Full", "#244B3C", listOf(
                    ex("fd1-ex1", "Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell",
                        nSets("fde1", 3, 5, 8.5), restTime = 210),
                    ex("fd1-ex2", "Press de Banca con Barra", "bench_press__barbell",
                        nSets("fde2", 3, 6, 8.0), restTime = 180),
                    ex("fd1-ex3", "Jalón Neutro en Polea", "lat_pulldown__bilateral__cable",
                        nSets("fde3", 3, 8, 8.5), restTime = 150),
                    ex("fd1-ex4", "Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell",
                        nSets("fde4", 2, 8, 8.0), restTime = 150),
                    ex("fd1-ex5", "Elevaciones Laterales de Pie con Mancuernas", "standing_lateral_raise__dumbbells",
                        nSets("fde5", 2, 12, 8.0), restTime = 60),
                    ex("fd1-ex6", "Elevación de Talones de Pie en Máquina", "calf_raise__bilateral__machine",
                        nSets("fde6", 1, 12, 8.5), restTime = 45),
                )),
            ),
        ),
    ),
)

val SESSION_TEMPLATES_SYSTEM: List<SessionTemplate> =
    (SESSION_TEMPLATES_BASE + SESSION_TEMPLATES_DERIVED_SPLIT + SESSION_TEMPLATES_INDEPENDENT + SESSION_TEMPLATES_EXPANDED)
        .map(::finalizedTemplate)
