package com.example.kpkn.data.models

data class MobilityExercise(
    val id: String,
    val name: String,
    val discomfortIds: List<String>,
    val description: String,
    val durationSeconds: Int = 60,
    val bodyRegion: String,
    val category: String = "Movilidad",
    val instructions: String? = null,
)

object MobilityExerciseCatalog {

    private val catalog: Map<String, List<MobilityExercise>> = mapOf(
        "shoulder_anterior" to listOf(
            MobilityExercise("mob_shoulder_band_rotation", "Rotación externa con banda", listOf("shoulder_anterior"), "Rotación controlada de hombro con banda de resistencia", 60, "shoulder"),
            MobilityExercise("mob_wall_slides", "Wall slides", listOf("shoulder_anterior"), "Deslizamientos de brazos contra la pared", 45, "shoulder"),
            MobilityExercise("mob_stick_dislocates", "Dislocaciones con palo", listOf("shoulder_anterior"), "Rotación completa de hombros con palo o banda", 60, "shoulder"),
        ),
        "shoulder_posterior" to listOf(
            MobilityExercise("mob_crossbody_stretch", "Estiramiento cruzado de hombro", listOf("shoulder_posterior"), "Llevar el brazo cruzado sobre el pecho", 30, "shoulder"),
            MobilityExercise("mob_sleeper_stretch", "Sleeper stretch", listOf("shoulder_posterior"), "Estiramiento acostado de lado para rotadores posteriores", 45, "shoulder"),
            MobilityExercise("mob_thread_needle", "Thread the needle", listOf("shoulder_posterior"), "Rotación torácica en cuatro puntos", 45, "upper"),
        ),
        "elbow_medial" to listOf(
            MobilityExercise("mob_wrist_flexor_stretch", "Estiramiento de flexores de muñeca", listOf("elbow_medial"), "Extensión pasiva de muñeca con codo extendido", 30, "elbow"),
            MobilityExercise("mob_pronation_supination", "Pronación/Supinación con mazo", listOf("elbow_medial"), "Rotación controlada de antebrazo con martillo o mancuerna ligera", 45, "elbow"),
            MobilityExercise("mob_forearm_roller", "Rodillo de antebrazo", listOf("elbow_medial"), "Auto-masaje de antebrazo con rodillo", 45, "elbow"),
        ),
        "elbow_lateral" to listOf(
            MobilityExercise("mob_wrist_extensor_stretch", "Estiramiento de extensores de muñeca", listOf("elbow_lateral"), "Flexión pasiva de muñeca con codo extendido", 30, "elbow"),
            MobilityExercise("mob_finger_extension", "Extensión activa de dedos con banda", listOf("elbow_lateral"), "Apertura y cierre controlado con banda alrededor de los dedos", 45, "elbow"),
            MobilityExercise("mob_forearm_supination", "Supinación de antebrazo sentado", listOf("elbow_lateral"), "Apoyar antebrazo en muslo y rotar palma hacia arriba con peso ligero", 45, "elbow"),
        ),
        "wrist_hand" to listOf(
            MobilityExercise("mob_wrist_circles", "Círculos de muñeca", listOf("wrist_hand"), "Rotaciones completas de muñeca en ambas direcciones", 30, "wrist"),
            MobilityExercise("mob_finger_spread", "Apertura y cierre de dedos", listOf("wrist_hand"), "Separar y juntar dedos repetidamente con tensión controlada", 30, "wrist"),
            MobilityExercise("mob_wrist_mobilization", "Movilización de muñeca en cuatro direcciones", listOf("wrist_hand"), "Flexión, extensión, desviación radial y cubital asistida", 45, "wrist"),
        ),
        "neck_cervical" to listOf(
            MobilityExercise("mob_neck_retraction", "Retracción cervical (chin tucks)", listOf("neck_cervical"), "Llevar mentón hacia atrás manteniendo cabeza nivelada", 30, "neck"),
            MobilityExercise("mob_neck_lateral_flexion", "Flexión lateral de cuello", listOf("neck_cervical"), "Inclinación lateral controlada de cuello", 30, "neck"),
            MobilityExercise("mob_upper_trap_stretch", "Estiramiento de trapecio superior", listOf("neck_cervical"), "Estiramiento sentado del trapecio superior", 30, "neck"),
        ),
        "upper_back" to listOf(
            MobilityExercise("mob_thoracic_extension", "Extensión torácica sobre foam roller", listOf("upper_back"), "Acostado sobre foam roller a nivel torácico, extender columna", 60, "upper"),
            MobilityExercise("mob_open_book", "Open book stretch", listOf("upper_back"), "Acostado de lado, rotar torso abriendo el brazo superior", 45, "upper"),
            MobilityExercise("mob_scapular_retraction", "Retracción escapular con banda", listOf("upper_back"), "Juntar omóplatos contra resistencia de banda", 45, "upper"),
        ),
        "lumbar" to listOf(
            MobilityExercise("mob_cat_cow", "Cat-Cow", listOf("lumbar"), "Flexión y extensión alternada de columna en cuatro puntos", 60, "spine"),
            MobilityExercise("mob_pelvic_tilts", "Pelvic tilts", listOf("lumbar"), "Basculaciones pélvicas acostado boca arriba", 45, "spine"),
            MobilityExercise("mob_child_pose_rotation", "Child's pose con rotación", listOf("lumbar"), "Posición del niño con alcance rotacional lateral", 60, "spine"),
        ),
        "hip_front" to listOf(
            MobilityExercise("mob_couch_stretch", "Couch stretch", listOf("hip_front"), "Estiramiento de cadera anterior contra pared/sillón", 60, "hip"),
            MobilityExercise("mob_hip_flexor_lunge", "Estocada con estiramiento de psoas", listOf("hip_front"), "Posición de estocada con extensión de cadera", 45, "hip"),
            MobilityExercise("mob_90_90_hip", "90/90 Hip switch", listOf("hip_front"), "Rotación interna/externa de cadera en posición 90/90", 60, "hip"),
        ),
        "hip_lateral" to listOf(
            MobilityExercise("mob_clamshell", "Clamshell", listOf("hip_lateral"), "Apertura lateral de rodillas acostado de lado", 45, "hip"),
            MobilityExercise("mob_fire_hydrant", "Fire hydrant", listOf("hip_lateral"), "Elevación lateral de rodilla en cuatro puntos", 45, "hip"),
            MobilityExercise("mob_hip_circle_standing", "Círculos de cadera de pie", listOf("hip_lateral"), "Rotaciones completas de cadera en posición de pie", 45, "hip"),
        ),
        "adductor_groin" to listOf(
            MobilityExercise("mob_frog_stretch", "Frog stretch", listOf("adductor_groin"), "Rodillas abiertas en cuatro puntos, llevar caderas hacia atrás", 60, "hip"),
            MobilityExercise("mob_side_lunge_adductor", "Estocada lateral con enfoque en aductores", listOf("adductor_groin"), "Paso lateral profundo manteniendo torso erguido", 45, "hip"),
            MobilityExercise("mob_butterfly_stretch", "Mariposa", listOf("adductor_groin"), "Plantas de pies juntas, presionar rodillas hacia el suelo", 45, "hip"),
        ),
        "hamstring_proximal" to listOf(
            MobilityExercise("mob_straight_leg_raise", "Elevación de pierna recta con banda", listOf("hamstring_proximal"), "Acostado boca arriba, elevar pierna extendida contra banda", 45, "leg"),
            MobilityExercise("mob_standing_hamstring_stretch", "Estiramiento de isquiotibiales de pie", listOf("hamstring_proximal"), "Pierna elevada sobre apoyo, inclinar torso hacia adelante", 45, "leg"),
            MobilityExercise("mob_slider_hamstring", "Deslizamiento de talón (Sliders)", listOf("hamstring_proximal"), "Acostado boca arriba, deslizar talón hacia glúteo y extender", 45, "leg"),
        ),
        "knee_patellar" to listOf(
            MobilityExercise("mob_wall_sit", "Sentadilla isométrica en pared", listOf("knee_patellar"), "Posición de sentadilla contra pared manteniendo 90°", 45, "knee"),
            MobilityExercise("mob_step_ups_control", "Step-ups controlados", listOf("knee_patellar"), "Subir y bajar de un step con control excéntrico", 60, "knee"),
            MobilityExercise("mob_ankle_dorsiflexion", "Movilidad de tobillo en pared", listOf("knee_patellar"), "Dorsiflexión de tobillo contra pared", 30, "knee"),
        ),
        "knee_medial" to listOf(
            MobilityExercise("mob_banded_patellar_glide", "Deslizamiento rotuliano asistido con banda", listOf("knee_medial"), "Tracción suave de rótula con banda elástica", 45, "knee"),
            MobilityExercise("mob_terminal_knee_extension", "Extensión terminal de rodilla con banda", listOf("knee_medial"), "Extensión completa de rodilla contra resistencia ligera", 45, "knee"),
            MobilityExercise("mob_heel_slides", "Deslizamiento de talón acostado", listOf("knee_medial"), "Acostado, deslizar talón flexionando y extendiendo rodilla", 45, "knee"),
        ),
        "achilles" to listOf(
            MobilityExercise("mob_eccentric_heel_drop", "Drop excéntrico de talón", listOf("achilles"), "Elevarse en puntas y bajar lentamente en escalón", 45, "ankle"),
            MobilityExercise("mob_calf_stretch_wall", "Estiramiento de gemelo contra pared", listOf("achilles"), "Pierna extendida atrás, talón en suelo, inclinarse hacia adelante", 45, "ankle"),
            MobilityExercise("mob_soleus_stretch", "Estiramiento de sóleo", listOf("achilles"), "Pierna flexionada atrás, bajar talón contra el suelo", 45, "ankle"),
        ),
        "ankle" to listOf(
            MobilityExercise("mob_ankle_alphabet", "Alfabeto con tobillo", listOf("ankle"), "Dibujar el alfabeto con el pie suspendido", 45, "ankle"),
            MobilityExercise("mob_ankle_circles", "Círculos de tobillo", listOf("ankle"), "Rotaciones completas de tobillo en ambas direcciones", 30, "ankle"),
            MobilityExercise("mob_ankle_band_dorsiflexion", "Dorsiflexión asistida con banda", listOf("ankle"), "Band around ankle, pull foot into dorsiflexion", 45, "ankle"),
        ),
        "plantar_foot" to listOf(
            MobilityExercise("mob_tennis_ball_roll", "Masaje con pelota de tenis", listOf("plantar_foot"), "Rodar pelota de tenis bajo el pie ejerciendo presión gradual", 60, "foot"),
            MobilityExercise("mob_toe_spread", "Separación activa de dedos", listOf("plantar_foot"), "Separar y estirar los dedos del pie activamente", 30, "foot"),
            MobilityExercise("mob_towel_curl", "Recoger toalla con dedos", listOf("plantar_foot"), "Usar los dedos del pie para arrugar y recoger una toalla", 45, "foot"),
        ),
    )

    fun getMobilityForDiscomfort(discomfortId: String): List<MobilityExercise> {
        return catalog[discomfortId] ?: emptyList()
    }

    fun getMobilityForDiscomforts(discomfortIds: List<String>): List<MobilityExercise> {
        return discomfortIds.flatMap { getMobilityForDiscomfort(it) }.distinctBy { it.id }
    }

    fun getAllMobilityExercises(): List<MobilityExercise> {
        return catalog.values.flatten().distinctBy { it.id }
    }

    fun searchMobilityByName(query: String): List<MobilityExercise> {
        val normalized = query.trim().lowercase()
        return getAllMobilityExercises().filter { it.name.lowercase().contains(normalized) }
    }
}
