package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2

/**
 * Presentation labels for the controlled v2 vocabulary.
 *
 * The catalog intentionally stores stable IDs in English so Android, iOS and
 * backend can share one contract. Those IDs must never leak into the Spanish
 * UI. Keep this mapping at the presentation boundary instead of mutating the
 * canonical asset or using an ad-hoc string transformation.
 */
internal fun exerciseCatalogAxisLabel(axis: String): String = when (axis) {
    "implement" -> "Implemento"
    "laterality" -> "Lateralidad"
    "load_position" -> "Posición de carga"
    "posture" -> "Postura"
    "setup" -> "Configuración"
    "stance" -> "Postura de piernas"
    "station" -> "Estación"
    "support_angle" -> "Ángulo y soporte"
    else -> controlledIdLabel(axis)
}

internal fun exerciseCatalogOptionLabel(value: String): String = when (value) {
    "ab_wheel" -> "Rueda abdominal"
    "arana" -> "Araña"
    "barbell" -> "Barra"
    "band" -> "Banda elástica"
    "bayesian" -> "Bayesiano"
    "bilateral" -> "Bilateral"
    "bench" -> "Banco"
    "bodyweight" -> "Peso corporal"
    "cable" -> "Polea"
    "cable_front" -> "Frontal en polea"
    "concentrado" -> "Concentrado"
    "crucifijo" -> "Crucifijo"
    "decline" -> "Declinado"
    "declinado" -> "Declinado"
    "dumbbells" -> "Mancuerna"
    "ez_bar" -> "Barra EZ"
    "flat" -> "Plano"
    "floor" -> "Suelo"
    "front" -> "Frontal"
    "guided" -> "Guiado"
    "incline" -> "Inclinado"
    "inclinado" -> "Inclinado"
    "kettlebell" -> "Kettlebell"
    "machine" -> "Máquina"
    "pec_deck" -> "Pec deck"
    "plate" -> "Disco"
    "posture" -> "Postura"
    "preacher" -> "Predicador"
    "seated" -> "Sentado"
    "sentado_banco_plano" -> "Sentado en banco plano"
    "sides" -> "A los lados"
    "sliders" -> "Deslizadores"
    "standing" -> "De pie"
    "station" -> "Estación"
    "sumo" -> "Sumo"
    "superman" -> "Superman"
    "trx" -> "TRX"
    "unilateral" -> "Unilateral"
    "barbell_back" -> "Barra a la espalda"
    "wrist_roller" -> "Rodillo de muñeca"
    "zercher" -> "Zercher"
    "smith_machine" -> "Máquina Smith"
    "hex_bar" -> "Barra hexagonal"
    "t_bar" -> "Barra T"
    "ghd" -> "Máquina GHD"
    else -> controlledIdLabel(value)
}

/** Translates the compact summary while preserving its editorial order. */
internal fun exerciseCatalogConfigurationSummary(configuration: ExerciseConfigurationV2): String =
    configuration.displaySummary
        .split(" · ")
        .joinToString(" · ") { exerciseCatalogOptionLabel(it) }

internal fun exerciseCatalogMuscleLabel(id: String): String = when (id) {
    "abdominals" -> "Abdomen"
    "adductors" -> "Aductores"
    "biceps" -> "Bíceps"
    "calves" -> "Pantorrillas"
    "core" -> "Core"
    "deltoid" -> "Deltoides"
    "erector_spinae" -> "Erectores espinales"
    "forearm" -> "Antebrazo"
    "gluteus_maximus" -> "Glúteos"
    "hamstrings" -> "Isquiosurales"
    "hip_flexors" -> "Flexores de cadera"
    "latissimus_dorsi" -> "Dorsales"
    "neck" -> "Cuello"
    "pectoralis" -> "Pectorales"
    "quadriceps" -> "Cuádriceps"
    "rhomboids" -> "Romboides"
    "tensor_fasciae_latae" -> "Tensor de la fascia lata"
    "tibialis_anterior" -> "Tibial anterior"
    "trapezius" -> "Trapecio"
    "triceps" -> "Tríceps"
    else -> controlledIdLabel(id)
}

internal fun exerciseCatalogEquipmentLabel(id: String): String = when (id) {
    "ab_wheel" -> "Rueda abdominal"
    "band" -> "Banda elástica"
    "barbell" -> "Barra"
    "bodyweight" -> "Peso corporal"
    "cable" -> "Polea"
    "dumbbells" -> "Mancuerna"
    "ez_bar" -> "Barra EZ"
    "ghd" -> "Máquina GHD"
    "hex_bar" -> "Barra hexagonal"
    "kettlebell" -> "Kettlebell"
    "machine" -> "Máquina"
    "plate" -> "Disco"
    "sliders" -> "Deslizadores"
    "smith_machine" -> "Máquina Smith"
    "t_bar" -> "Barra T"
    "trx" -> "TRX"
    "wrist_roller" -> "Rodillo de muñeca"
    else -> controlledIdLabel(id)
}

internal fun exerciseCatalogMovementLabel(id: String): String = when (id) {
    "ankle_dorsiflexion" -> "Dorsiflexión de tobillo"
    "anti_extension_isometric" -> "Anti-extensión isométrica"
    "anti_extension_pelvic_control" -> "Control pélvico anti-extensión"
    "anti_extension_trunk" -> "Anti-extensión del tronco"
    "anti_rotation_trunk" -> "Anti-rotación del tronco"
    "biarticular_lengthened" -> "Acción biarticular en longitud"
    "deadlift" -> "Peso muerto"
    "diagonal_push" -> "Empuje diagonal"
    "eccentric_knee_flexion" -> "Flexión excéntrica de rodilla"
    "elbow_extension" -> "Extensión de codo"
    "elbow_flexion" -> "Flexión de codo"
    "hip_abduction" -> "Abducción de cadera"
    "hip_abduction_extension" -> "Abducción y extensión de cadera"
    "hip_abduction_external_rotation" -> "Abducción y rotación externa de cadera"
    "hip_abduction_stability" -> "Estabilidad en abducción de cadera"
    "hip_adduction" -> "Aducción de cadera"
    "hip_adduction_dynamic" -> "Aducción dinámica de cadera"
    "hip_adduction_isometric" -> "Aducción isométrica de cadera"
    "hip_extension" -> "Extensión de cadera"
    "hip_extension_abduction" -> "Extensión y abducción de cadera"
    "hip_extension_external_rotation" -> "Extensión y rotación externa de cadera"
    "hip_flexion" -> "Flexión de cadera"
    "hip_hinge" -> "Bisagra de cadera"
    "hip_hinge_deficit" -> "Bisagra de cadera con déficit"
    "hip_hinge_explosive" -> "Bisagra de cadera explosiva"
    "hip_hinge_lengthened" -> "Bisagra de cadera en longitud"
    "horizontal_abduction" -> "Abducción horizontal"
    "horizontal_pull" -> "Tirón horizontal"
    "horizontal_push" -> "Empuje horizontal"
    "isometric_grip" -> "Agarre isométrico"
    "knee_dominant" -> "Dominante de rodilla"
    "knee_dominant_asymmetric" -> "Dominante de rodilla asimétrico"
    "knee_dominant_lengthened" -> "Dominante de rodilla en longitud"
    "knee_extension" -> "Extensión de rodilla"
    "knee_flexion" -> "Flexión de rodilla"
    "knee_hip_dominant" -> "Dominante de rodilla y cadera"
    "knee_hip_extension" -> "Extensión de rodilla y cadera"
    "knee_hip_flexion" -> "Flexión de rodilla y cadera"
    "lateral_knee_dominant" -> "Dominante lateral de rodilla"
    "lateral_trunk_flexion" -> "Flexión lateral del tronco"
    "neck_extension" -> "Extensión cervical"
    "neck_flexion" -> "Flexión cervical"
    "neck_lateral_flexion" -> "Flexión lateral cervical"
    "pinch_grip" -> "Agarre de pinza"
    "plantar_flexion" -> "Flexión plantar"
    "plantar_flexion_seated" -> "Flexión plantar sentado"
    "reverse_hip_extension" -> "Extensión inversa de cadera"
    "romanian_deadlift" -> "Peso muerto rumano"
    "romanian_deadlift_deficit" -> "Peso muerto rumano con déficit"
    "scapular_depression" -> "Depresión escapular"
    "scapular_elevation" -> "Elevación escapular"
    "shoulder_abduction" -> "Abducción de hombro"
    "shoulder_abduction_diagonal" -> "Abducción diagonal de hombro"
    "shoulder_abduction_full_rom" -> "Abducción completa de hombro"
    "shoulder_flexion" -> "Flexión de hombro"
    "spinal_extension" -> "Extensión espinal"
    "spinal_flexion" -> "Flexión espinal"
    "trunk_flexion" -> "Flexión del tronco"
    "trunk_rotation" -> "Rotación del tronco"
    "unilateral_hip_dominant" -> "Dominante unilateral de cadera"
    "unilateral_hip_extension" -> "Extensión unilateral de cadera"
    "unilateral_hip_hinge" -> "Bisagra unilateral de cadera"
    "unilateral_knee_dominant" -> "Dominante unilateral de rodilla"
    "unilateral_knee_dominant_asymmetric" -> "Dominante unilateral asimétrico de rodilla"
    "vertical_pull" -> "Tirón vertical"
    "vertical_pull_abduction" -> "Tirón vertical con abducción"
    "vertical_push" -> "Empuje vertical"
    "wrist_extension" -> "Extensión de muñeca"
    "wrist_flexion" -> "Flexión de muñeca"
    "wrist_flexion_extension" -> "Flexión y extensión de muñeca"
    else -> controlledIdLabel(id)
}

internal fun exerciseCatalogBodyRegionLabel(region: ExerciseBodyRegionV2): String = when (region) {
    ExerciseBodyRegionV2.UPPER -> "Tren superior"
    ExerciseBodyRegionV2.LOWER -> "Tren inferior"
    ExerciseBodyRegionV2.CORE -> "Core"
    ExerciseBodyRegionV2.FULL -> "Cuerpo completo"
}

internal fun exerciseCatalogChainLabel(chain: ExerciseKineticChainV2): String = when (chain) {
    ExerciseKineticChainV2.ANTERIOR -> "Cadena anterior"
    ExerciseKineticChainV2.POSTERIOR -> "Cadena posterior"
    ExerciseKineticChainV2.FULL -> "Cadena completa"
}

internal fun exerciseCatalogLateralityLabel(laterality: ExerciseLateralityV2): String = when (laterality) {
    ExerciseLateralityV2.BILATERAL -> "Bilateral"
    ExerciseLateralityV2.UNILATERAL -> "Unilateral"
    ExerciseLateralityV2.ALTERNATING -> "Alternada"
    ExerciseLateralityV2.NOT_APPLICABLE -> "No aplica"
}

internal fun exerciseCatalogLoadModeLabel(mode: String): String = when (mode) {
    "bodyweight" -> "Peso corporal"
    "bodyweight_with_sliding_resistance" -> "Peso corporal con deslizamiento"
    "continuous_cable" -> "Polea de tensión continua"
    "free_external_load" -> "Carga externa libre"
    "guided_external_load" -> "Carga externa guiada"
    "suspension" -> "Suspensión"
    "variable_band_resistance" -> "Resistencia variable con banda"
    else -> controlledIdLabel(mode)
}

internal fun exerciseCatalogResistanceLabel(profile: String): String = when (profile) {
    "body_angle" -> "Ángulo corporal"
    "continuous_cable" -> "Polea continua"
    "gravity_arc" -> "Arco gravitacional"
    "guided_constant" -> "Guiada constante"
    "variable_band" -> "Banda variable"
    else -> controlledIdLabel(profile)
}

private fun controlledIdLabel(raw: String): String = raw
    .replace('_', ' ')
    .split(' ')
    .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
