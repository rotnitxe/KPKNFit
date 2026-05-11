package com.example.kpkn.data.sessions

import com.example.kpkn.data.models.*
import com.example.kpkn.data.splits.Difficulty

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun ex(
    id: String,
    name: String,
    exerciseDbId: String,
    sets: List<ExerciseSet>,
    restTime: Int? = 120,
    trainingMode: TrainingMode = TrainingMode.REPS,
    intensityMode: IntensityMode = IntensityMode.RPE,
    damageProfile: DamageProfile? = null,
): Exercise = Exercise(
    id = id,
    name = name,
    exerciseDbId = exerciseDbId,
    exerciseId = exerciseDbId,
    canonicalExerciseId = exerciseDbId,
    exerciseFamilyId = exerciseDbId.lowercase(),
    sets = sets,
    restTime = restTime,
    trainingMode = trainingMode,
    damageProfile = damageProfile,
)

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

private fun nRirSets(prefix: String, count: Int, reps: Int, rir: Int): List<ExerciseSet> =
    (1..count).map { rirSet("$prefix-s$it", reps, rir) }

private fun part(id: String, name: String, color: String, exercises: List<Exercise>) =
    SessionPart(id = id, name = name, color = color, exercises = exercises)

// ─── System templates ─────────────────────────────────────────────────────────

val SESSION_TEMPLATES_SYSTEM: List<SessionTemplate> = listOf(

    // ── 1. Push Day ──────────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-push-ppl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Push Day · PPL",
        description = "Día de Empuje clásico: Pecho, Hombros y Tríceps con enfoque en hipertrofia. " +
                "Multi-articulares primero, aislamientos al final.",
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
        estimatedDurationMinutes = 65,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 10,
        session = Session(
            id = "tpl-push-ppl",
            name = "Push Day · PPL",
            parts = listOf(
                part("p-push-1", "Pecho", "#1B4965", listOf(
                    ex("p1-ex1", "Press de Banca con Barra", "tren_superior_press_banca_plano_barra",
                        nSets("p1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("p1-ex2", "Press Inclinado con Mancuernas", "tren_superior_press_banca_inclinado_mancuernas",
                        nSets("p1e2", 3, 10, 7.5), restTime = 120, damageProfile = DamageProfile.STRETCH),
                    ex("p1-ex3", "Pec Deck / Aperturas en Polea", "tren_superior_aperturas_pec_deck",
                        nSets("p1e3", 3, 15, 8.0), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-push-2", "Hombros", "#4A1942", listOf(
                    ex("p2-ex1", "Press Militar con Mancuernas", "tren_superior_press_hombros_sentado_mancuernas",
                        nSets("p2e1", 4, 10, 7.5), restTime = 120),
                    ex("p2-ex2", "Elevaciones Laterales", "tren_superior_elevaciones_laterales_mancuernas",
                        nSets("p2e2", 4, 15, 8.5), restTime = 75),
                )),
                part("p-push-3", "Tríceps", "#1F3A2E", listOf(
                    ex("p3-ex1", "Extensiones en Polea Alta (Cuerda)", "tren_superior_extension_triceps_polea_cuerda",
                        nSets("p3e1", 3, 15, 8.0), restTime = 90),
                    ex("p3-ex2", "Press Francés / Skull Crusher", "tren_superior_press_frances_barra_ez",
                        nSets("p3e2", 3, 12, 7.5), restTime = 90, damageProfile = DamageProfile.STRETCH),
                )),
            ),
        ),
    ),

    // ── 2. Pull Day ───────────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-pull-ppl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pull Day · PPL",
        description = "Día de Tirón con énfasis en Espalda y Bíceps. " +
                "Tracción vertical, horizontal y trabajo de bíceps.",
        emoji = "🫷",
        tags = listOf(
            SessionTemplateTag.TIRON,
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.BRAZOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 65,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Espalda · Bíceps · Romboides",
        sortOrder = 20,
        session = Session(
            id = "tpl-pull-ppl",
            name = "Pull Day · PPL",
            parts = listOf(
                part("p-pull-1", "Espalda vertical", "#0F3D5E", listOf(
                    ex("pu1-ex1", "Dominadas / Jalones al Pecho", "tren_superior_dominadas_pronas",
                        nSets("pu1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("pu1-ex2", "Jalón al Pecho en Máquina", "tren_superior_jalon_pecho_prono",
                        nSets("pu1e2", 3, 12, 8.0), restTime = 90),
                )),
                part("p-pull-2", "Espalda horizontal", "#244B3C", listOf(
                    ex("pu2-ex1", "Remo con Barra / Remo Pendlay", "tren_superior_remo_inclinado_prono_barra",
                        nSets("pu2e1", 4, 8, 8.0), restTime = 150),
                    ex("pu2-ex2", "Remo en Polea Baja", "tren_superior_remo_sentado_polea_baja",
                        nSets("pu2e2", 3, 12, 7.5), restTime = 90),
                    ex("pu2-ex3", "Facepull en Polea", "tren_superior_face_pull_polea",
                        nRirSets("pu2e3", 3, 20, 2), restTime = 60),
                )),
                part("p-pull-3", "Bíceps", "#5B2A86", listOf(
                    ex("pu3-ex1", "Curl con Barra EZ", "tren_superior_curl_biceps_barra_ez",
                        nSets("pu3e1", 3, 10, 8.0), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("pu3-ex2", "Curl Martillo con Mancuernas", "tren_superior_curl_martillo_mancuernas",
                        nSets("pu3e2", 3, 12, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 3. Leg Day (Cuádriceps dominante) ─────────────────────────────────────
    SessionTemplate(
        id = "sys-legs-quad",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day · Cuádriceps",
        description = "Día de Pierna con énfasis en Cuádriceps y Glúteos. " +
                "Sentadilla como bloque central, complementado con prensa y aislamientos.",
        emoji = "🦵",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.CUADRICEPS,
            SessionTemplateTag.GLUTEOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 70,
        exerciseCount = 6,
        partCount = 2,
        muscleGroupsSummary = "Cuádriceps · Glúteos · Isquios",
        sortOrder = 30,
        session = Session(
            id = "tpl-legs-quad",
            name = "Leg Day · Cuádriceps",
            parts = listOf(
                part("p-lq-1", "Dominante Cuádriceps", "#7F1D1D", listOf(
                    ex("lq1-ex1", "Sentadilla con Barra (Back Squat)", "tren_inferior_sentadilla_barra_alta",
                        nSets("lq1e1", 4, 6, 8.0), restTime = 180, damageProfile = DamageProfile.STRETCH),
                    ex("lq1-ex2", "Prensa de Piernas", "tren_inferior_prensa_45",
                        nSets("lq1e2", 3, 12, 8.0), restTime = 120),
                    ex("lq1-ex3", "Extensión de Cuádriceps en Máquina", "tren_inferior_extension_cuadriceps",
                        nRirSets("lq1e3", 3, 15, 2), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-lq-2", "Glúteos e Isquios", "#4A1942", listOf(
                    ex("lq2-ex1", "Hip Thrust con Barra", "tren_inferior_hip_thrust_barra",
                        nSets("lq2e1", 4, 12, 8.0), restTime = 120, damageProfile = DamageProfile.SQUEEZE),
                    ex("lq2-ex2", "Curl de Isquiotibiales en Máquina", "tren_inferior_curl_femoral_tumbado",
                        nRirSets("lq2e2", 3, 12, 2), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("lq2-ex3", "Elevación de Pantorrillas de Pie", "ultimo_elevacion_gemelos_burro",
                        nSets("lq2e3", 4, 20, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 4. Leg Day (Isquios / Peso Muerto dominante) ──────────────────────────
    SessionTemplate(
        id = "sys-legs-hinge",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day · Bisagra",
        description = "Día de Pierna centrado en bisagra de cadera: Peso Muerto Rumano, " +
                "hip hinge y trabajos de Isquiotibiales.",
        emoji = "🔩",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.GLUTEOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 65,
        exerciseCount = 5,
        partCount = 2,
        muscleGroupsSummary = "Isquios · Glúteos · Lumbares",
        sortOrder = 40,
        session = Session(
            id = "tpl-legs-hinge",
            name = "Leg Day · Bisagra",
            parts = listOf(
                part("p-lh-1", "Bisagra de Cadera", "#244B3C", listOf(
                    ex("lh1-ex1", "Peso Muerto Rumano (RDL)", "tren_inferior_peso_muerto_rumano",
                        nSets("lh1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("lh1-ex2", "Peso Muerto con Piernas Rígidas", "tren_inferior_peso_muerto_piernas_rigidas",
                        nSets("lh1e2", 3, 10, 7.5), restTime = 120),
                )),
                part("p-lh-2", "Glúteos e Isquios", "#1B4965", listOf(
                    ex("lh2-ex1", "Hip Thrust con Barra", "tren_inferior_hip_thrust_barra",
                        nSets("lh2e1", 4, 12, 8.0), restTime = 120, damageProfile = DamageProfile.SQUEEZE),
                    ex("lh2-ex2", "Curl de Isquiotibiales (Nordic o Máquina)", "tren_inferior_curl_femoral_tumbado",
                        nRirSets("lh2e2", 3, 10, 2), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("lh2-ex3", "Abducción de Cadera en Máquina", "tren_inferior_abduccion_cadera_maquina",
                        nRirSets("lh2e3", 3, 20, 2), restTime = 60, damageProfile = DamageProfile.SQUEEZE),
                )),
            ),
        ),
    ),

    // ── 5. Upper Body (Torso Completo A) ──────────────────────────────────────
    SessionTemplate(
        id = "sys-upper-a",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Torso A · Upper/Lower",
        description = "Sesión de Torso para un split Upper/Lower. Énfasis en " +
                "Pecho y Espalda con trabajo accesorio de Hombros y Brazos.",
        emoji = "💪",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.ESPALDA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 70,
        exerciseCount = 8,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Bíceps · Tríceps",
        sortOrder = 50,
        session = Session(
            id = "tpl-upper-a",
            name = "Torso A",
            parts = listOf(
                part("p-ua-1", "Pecho + Espalda", "#1B4965", listOf(
                    ex("ua1-ex1", "Press de Banca con Barra", "tren_superior_press_banca_plano_barra",
                        nSets("ua1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ua1-ex2", "Remo con Barra", "tren_superior_remo_inclinado_prono_barra",
                        nSets("ua1e2", 4, 8, 8.0), restTime = 150),
                    ex("ua1-ex3", "Press Inclinado con Mancuernas", "tren_superior_press_banca_inclinado_mancuernas",
                        nSets("ua1e3", 3, 10, 7.5), restTime = 120),
                    ex("ua1-ex4", "Jalón al Pecho", "tren_superior_jalon_pecho_prono",
                        nSets("ua1e4", 3, 12, 7.5), restTime = 90),
                )),
                part("p-ua-2", "Hombros + Brazos", "#5B2A86", listOf(
                    ex("ua2-ex1", "Press Militar con Mancuernas", "tren_superior_press_hombros_sentado_mancuernas",
                        nSets("ua2e1", 3, 10, 7.5), restTime = 90),
                    ex("ua2-ex2", "Elevaciones Laterales", "tren_superior_elevaciones_laterales_mancuernas",
                        nSets("ua2e2", 3, 15, 8.5), restTime = 60),
                    ex("ua2-ex3", "Curl con Barra EZ", "tren_superior_curl_biceps_barra_ez",
                        nSets("ua2e3", 3, 10, 8.0), restTime = 75, damageProfile = DamageProfile.STRETCH),
                    ex("ua2-ex4", "Extensiones en Polea Alta", "tren_superior_extension_triceps_polea_cuerda",
                        nSets("ua2e4", 3, 15, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 6. Full Body Base ─────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-fullbody-base",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Full Body · Base",
        description = "Sesión de Cuerpo Completo equilibrada. " +
                "Un movimiento por patrón motor principal: empuje, tirón y dominante de pierna.",
        emoji = "🏗️",
        tags = listOf(
            SessionTemplateTag.CUERPO_COMPLETO,
            SessionTemplateTag.FUERZA,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.PRINCIPIANTE,
        estimatedDurationMinutes = 55,
        exerciseCount = 6,
        partCount = 3,
        muscleGroupsSummary = "Cuerpo Completo",
        sortOrder = 60,
        session = Session(
            id = "tpl-fullbody-base",
            name = "Full Body · Base",
            parts = listOf(
                part("p-fb-1", "Pierna", "#7F1D1D", listOf(
                    ex("fb1-ex1", "Sentadilla / Prensa de Piernas", "tren_inferior_sentadilla_barra_alta",
                        nSets("fb1e1", 3, 8, 7.5), restTime = 150),
                    ex("fb1-ex2", "Hip Thrust / Peso Muerto Rumano", "tren_inferior_hip_thrust_barra",
                        nSets("fb1e2", 3, 10, 7.5), restTime = 120),
                )),
                part("p-fb-2", "Empuje", "#1B4965", listOf(
                    ex("fb2-ex1", "Press de Banca / Press Inclinado", "tren_superior_press_banca_plano_barra",
                        nSets("fb2e1", 3, 8, 7.5), restTime = 120, damageProfile = DamageProfile.STRETCH),
                    ex("fb2-ex2", "Press Militar / Press Hombros", "tren_superior_press_militar_pie_barra",
                        nSets("fb2e2", 3, 10, 7.5), restTime = 90),
                )),
                part("p-fb-3", "Tirón", "#244B3C", listOf(
                    ex("fb3-ex1", "Remo con Barra / Jalón al Pecho", "tren_superior_remo_inclinado_prono_barra",
                        nSets("fb3e1", 3, 8, 7.5), restTime = 120),
                    ex("fb3-ex2", "Curl Bíceps", "tren_superior_curl_biceps_barra_recta",
                        nSets("fb3e2", 3, 12, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 7. SBD Powerlifting Day ───────────────────────────────────────────────
    SessionTemplate(
        id = "sys-sbd-pl",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "SBD · Powerlifting",
        description = "Sesión de Squat–Bench–Deadlift con intensidad moderada-alta. " +
                "Ideal como día de práctica técnica o acumulación de volumen específico.",
        emoji = "🏋️",
        tags = listOf(
            SessionTemplateTag.POWERLIFTING,
            SessionTemplateTag.SENTADILLA,
            SessionTemplateTag.BANCA,
            SessionTemplateTag.PESO_MUERTO,
            SessionTemplateTag.FUERZA,
        ),
        difficulty = Difficulty.AVANZADO,
        estimatedDurationMinutes = 90,
        exerciseCount = 6,
        partCount = 3,
        muscleGroupsSummary = "Sentadilla · Banca · Peso Muerto",
        sortOrder = 70,
        session = Session(
            id = "tpl-sbd-pl",
            name = "SBD Powerlifting",
            parts = listOf(
                part("p-sbd-1", "Sentadilla", "#7F1D1D", listOf(
                    ex("sbd1-ex1", "Sentadilla Competitiva (RPE)", "tren_inferior_sentadilla_barra_alta",
                        nSets("sbd1e1", 4, 3, 8.0), restTime = 210),
                    ex("sbd1-ex2", "Sentadilla Variante (Pausa / SSB)", "tren_inferior_sentadilla_barra_alta",
                        nSets("sbd1e2", 3, 5, 7.0), restTime = 180),
                )),
                part("p-sbd-2", "Banca", "#1B4965", listOf(
                    ex("sbd2-ex1", "Press de Banca Competitivo", "tren_superior_press_banca_plano_barra",
                        nSets("sbd2e1", 4, 3, 8.0), restTime = 210),
                    ex("sbd2-ex2", "Press de Banca con Pausa / Close Grip", "tren_superior_press_banca_plano_barra",
                        nSets("sbd2e2", 3, 5, 7.0), restTime = 150),
                )),
                part("p-sbd-3", "Peso Muerto", "#244B3C", listOf(
                    ex("sbd3-ex1", "Peso Muerto Competitivo", "tren_inferior_peso_muerto_convencional",
                        nSets("sbd3e1", 3, 3, 8.0), restTime = 240),
                    ex("sbd3-ex2", "Peso Muerto Rumano (accesorio)", "tren_inferior_peso_muerto_rumano",
                        nSets("sbd3e2", 3, 6, 7.0), restTime = 150),
                )),
            ),
        ),
    ),

    // ── 8. Minimalista Fuerza ─────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-minimalist-strength",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Minimalista · Fuerza",
        description = "Sesión de 4 movimientos fundamentales, baja duración, " +
                "alta intensidad. Ideal cuando el tiempo es limitado.",
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
        session = Session(
            id = "tpl-minimalist",
            name = "Minimalista · Fuerza",
            exercises = listOf(
                ex("min-ex1", "Sentadilla o Prensa", "tren_inferior_sentadilla_barra_alta",
                    nSets("mine1", 3, 5, 8.0), restTime = 180),
                ex("min-ex2", "Press de Banca o Press Hombros", "tren_superior_press_banca_plano_barra",
                    nSets("mine2", 3, 5, 8.0), restTime = 150),
                ex("min-ex3", "Peso Muerto o Remo", "tren_inferior_peso_muerto_rumano",
                    nSets("mine3", 3, 5, 8.0), restTime = 180),
                ex("min-ex4", "Dominadas o Jalón al Pecho", "tren_superior_dominadas_pronas",
                    nSets("mine4", 3, 6, 7.5), restTime = 120),
            ),
        ),
    ),

    // ── 9. Push Day (Específico) ──────────────────────────────────────────────
    SessionTemplate(
        id = "sys-push-specific",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Push Day · Específico",
        description = "Empuje enfocado: Pecho, Hombro Anterior y Tríceps con mayor volumen directo.",
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
        estimatedDurationMinutes = 70,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 90,
        session = Session(
            id = "tpl-push-specific",
            name = "Push Day · Específico",
            parts = listOf(
                part("p-push-s-1", "Pecho", "#1B4965", listOf(
                    ex("ps1-ex1", "Press de Banca con Barra", "tren_superior_press_banca_plano_barra",
                        nSets("ps1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ps1-ex2", "Press Inclinado con Mancuernas", "tren_superior_press_banca_inclinado_barra",
                        nSets("ps1e2", 3, 10, 7.5), restTime = 120),
                    ex("ps1-ex3", "Aperturas en Polea (Cable Fly)", "tren_superior_crossover_inferior_polea",
                        nSets("ps1e3", 3, 15, 8.0), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-push-s-2", "Hombro", "#4A1942", listOf(
                    ex("ps2-ex1", "Press Militar con Mancuernas", "tren_superior_press_hombros_sentado_mancuernas",
                        nSets("ps2e1", 4, 10, 7.5), restTime = 120),
                    ex("ps2-ex2", "Elevaciones Laterales", "tren_superior_elevaciones_laterales_mancuernas",
                        nSets("ps2e2", 4, 15, 8.5), restTime = 75),
                )),
                part("p-push-s-3", "Tríceps", "#1F3A2E", listOf(
                    ex("ps3-ex1", "Fondos en Paralelas / Dips", "tren_superior_fondos_paralelas",
                        nSets("ps3e1", 3, 10, 8.0), restTime = 90),
                    ex("ps3-ex2", "Extensiones en Polea Alta (Cuerda)", "tren_superior_extension_triceps_polea_cuerda",
                        nSets("ps3e2", 3, 15, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 10. Pull Day (Específico) ─────────────────────────────────────────────
    SessionTemplate(
        id = "sys-pull-specific",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pull Day · Específico",
        description = "Tirón enfocado: Espalda, Deltoides Posterior y Bíceps con mayor volumen directo.",
        emoji = "🫷",
        tags = listOf(
            SessionTemplateTag.TIRON,
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.BRAZOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 70,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Espalda · Bíceps · Romboides",
        sortOrder = 100,
        session = Session(
            id = "tpl-pull-specific",
            name = "Pull Day · Específico",
            parts = listOf(
                part("p-pull-s-1", "Espalda", "#0F3D5E", listOf(
                    ex("psp1-ex1", "Dominadas / Jalón al Pecho", "tren_superior_dominadas_pronas",
                        nSets("psp1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("psp1-ex2", "Remo con Barra", "tren_superior_remo_inclinado_prono_barra",
                        nSets("psp1e2", 4, 8, 8.0), restTime = 150),
                    ex("psp1-ex3", "Facepull en Polea", "tren_superior_face_pull_polea",
                        nRirSets("psp1e3", 3, 20, 2), restTime = 60),
                )),
                part("p-pull-s-2", "Bíceps", "#5B2A86", listOf(
                    ex("psp2-ex1", "Curl con Barra EZ", "tren_superior_curl_biceps_barra_ez",
                        nSets("psp2e1", 3, 10, 8.0), restTime = 90, damageProfile = DamageProfile.STRETCH),
                    ex("psp2-ex2", "Curl Martillo con Mancuernas", "tren_superior_curl_martillo_mancuernas",
                        nSets("psp2e2", 3, 12, 8.0), restTime = 75),
                )),
                part("p-pull-s-3", "Accesorios", "#244B3C", listOf(
                    ex("psp3-ex1", "Remo en Polea Baja", "tren_superior_remo_inclinado_prono_barra",
                        nSets("psp3e1", 3, 12, 7.5), restTime = 90),
                    ex("psp3-ex2", "Curl Concentrado", "tren_superior_curl_concentrado_mancuerna",
                        nSets("psp3e2", 2, 15, 8.0), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 11. Pecho Day ─────────────────────────────────────────────────────────
    SessionTemplate(
        id = "sys-chest-pec",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Pecho Day",
        description = "Sesión dedicada al pecho con énfasis en hipertrofia. Varias angulaciones para desarrollo completo.",
        emoji = "🫁",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.HIPERTROFIA,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 60,
        exerciseCount = 5,
        partCount = 2,
        muscleGroupsSummary = "Pecho · Hombros · Tríceps",
        sortOrder = 110,
        session = Session(
            id = "tpl-chest-pec",
            name = "Pecho Day",
            parts = listOf(
                part("p-ch-1", "Pecho principal", "#1B4965", listOf(
                    ex("ch1-ex1", "Press de Banca con Barra", "tren_superior_press_banca_plano_barra",
                        nSets("ch1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ch1-ex2", "Press Inclinado con Mancuernas", "tren_superior_press_banca_inclinado_barra",
                        nSets("ch1e2", 4, 10, 7.5), restTime = 120),
                    ex("ch1-ex3", "Aperturas en Polea", "tren_superior_crossover_inferior_polea",
                        nSets("ch1e3", 3, 15, 8.0), restTime = 90, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-ch-2", "Pecho completo", "#4A1942", listOf(
                    ex("ch2-ex1", "Press Declinado / Fondos", "tren_superior_press_banca_declinado_barra",
                        nSets("ch2e1", 3, 10, 8.0), restTime = 120),
                    ex("ch2-ex2", "Cruces en Polea Alta", "ultimo_cruces_polea_baja_ascendentes",
                        nSets("ch2e2", 3, 15, 8.0), restTime = 75, damageProfile = DamageProfile.SQUEEZE),
                )),
            ),
        ),
    ),

    // ── 12. Legs Day (Completo) ───────────────────────────────────────────────
    SessionTemplate(
        id = "sys-legs-complete",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Legs Day · Completo",
        description = "Sesión completa de piernas: Cuádriceps, Isquios, Glúteos y Pantorrillas.",
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
        estimatedDurationMinutes = 75,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Pantorrillas",
        sortOrder = 120,
        session = Session(
            id = "tpl-legs-complete",
            name = "Legs Day · Completo",
            parts = listOf(
                part("p-lc-1", "Cuádriceps", "#7F1D1D", listOf(
                    ex("lc1-ex1", "Sentadilla con Barra", "tren_inferior_sentadilla_barra_alta",
                        nSets("lc1e1", 4, 6, 8.0), restTime = 180, damageProfile = DamageProfile.STRETCH),
                    ex("lc1-ex2", "Prensa de Piernas", "tren_inferior_prensa_45",
                        nSets("lc1e2", 3, 12, 8.0), restTime = 120),
                    ex("lc1-ex3", "Extensión de Cuádriceps en Máquina", "tren_inferior_extension_cuadriceps",
                        nRirSets("lc1e3", 3, 15, 2), restTime = 90),
                )),
                part("p-lc-2", "Isquios y Glúteos", "#244B3C", listOf(
                    ex("lc2-ex1", "Peso Muerto Rumano (RDL)", "tren_inferior_peso_muerto_rumano",
                        nSets("lc2e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("lc2-ex2", "Curl de Isquiotibiales en Máquina", "tren_inferior_curl_femoral_tumbado",
                        nRirSets("lc2e2", 3, 12, 2), restTime = 90),
                    ex("lc2-ex3", "Hip Thrust con Barra", "tren_inferior_hip_thrust_barra",
                        nSets("lc2e3", 3, 12, 8.0), restTime = 120, damageProfile = DamageProfile.SQUEEZE),
                )),
                part("p-lc-3", "Pantorrillas", "#1F3A2E", listOf(
                    ex("lc3-ex1", "Elevación de Pantorrillas de Pie", "ultimo_elevacion_gemelos_burro",
                        nSets("lc3e1", 4, 20, 8.5), restTime = 60),
                )),
            ),
        ),
    ),

    // ── 13. Upper Day (Equilibrado) ───────────────────────────────────────────
    SessionTemplate(
        id = "sys-upper-balanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Upper Day · Equilibrado",
        description = "Sesión de torso equilibrada: Pecho, Espalda, Hombros, Bíceps y Tríceps.",
        emoji = "💪",
        tags = listOf(
            SessionTemplateTag.TORSO,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.PECHO,
            SessionTemplateTag.ESPALDA,
            SessionTemplateTag.HOMBROS,
            SessionTemplateTag.BRAZOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 70,
        exerciseCount = 8,
        partCount = 3,
        muscleGroupsSummary = "Pecho · Espalda · Hombros · Bíceps · Tríceps",
        sortOrder = 130,
        session = Session(
            id = "tpl-upper-balanced",
            name = "Upper Day · Equilibrado",
            parts = listOf(
                part("p-ub-1", "Empuje", "#1B4965", listOf(
                    ex("ub1-ex1", "Press de Banca con Barra", "tren_superior_press_banca_plano_barra",
                        nSets("ub1e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("ub1-ex2", "Press Militar con Mancuernas", "tren_superior_press_militar_pie_barra",
                        nSets("ub1e2", 3, 10, 7.5), restTime = 120),
                )),
                part("p-ub-2", "Tirón", "#244B3C", listOf(
                    ex("ub2-ex1", "Remo con Barra", "tren_superior_remo_inclinado_prono_barra",
                        nSets("ub2e1", 4, 8, 8.0), restTime = 150),
                    ex("ub2-ex2", "Jalón al Pecho en Máquina", "tren_superior_jalon_pecho_prono",
                        nSets("ub2e2", 3, 12, 7.5), restTime = 90),
                    ex("ub2-ex3", "Elevaciones Laterales", "tren_superior_elevaciones_laterales_mancuernas",
                        nSets("ub2e3", 3, 15, 8.5), restTime = 60),
                )),
                part("p-ub-3", "Brazos", "#5B2A86", listOf(
                    ex("ub3-ex1", "Curl con Barra EZ", "tren_superior_curl_biceps_barra_ez",
                        nSets("ub3e1", 3, 10, 8.0), restTime = 75, damageProfile = DamageProfile.STRETCH),
                    ex("ub3-ex2", "Extensiones en Polea Alta", "tren_superior_extension_triceps_polea_cuerda",
                        nSets("ub3e2", 3, 15, 8.0), restTime = 75),
                )),
            ),
        ),
    ),

    // ── 14. Lower Day (Equilibrado) ───────────────────────────────────────────
    SessionTemplate(
        id = "sys-lower-balanced",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Lower Day · Equilibrado",
        description = "Sesión de piernas equilibrada: Sentadilla, Bisagra, Cuádriceps e Isquios.",
        emoji = "🦵",
        tags = listOf(
            SessionTemplateTag.PIERNA,
            SessionTemplateTag.HIPERTROFIA,
            SessionTemplateTag.CUADRICEPS,
            SessionTemplateTag.ISQUIOTIBIALES,
            SessionTemplateTag.GLUTEOS,
        ),
        difficulty = Difficulty.INTERMEDIO,
        estimatedDurationMinutes = 70,
        exerciseCount = 7,
        partCount = 3,
        muscleGroupsSummary = "Cuádriceps · Isquios · Glúteos · Pantorrillas",
        sortOrder = 140,
        session = Session(
            id = "tpl-lower-balanced",
            name = "Lower Day · Equilibrado",
            parts = listOf(
                part("p-lb-1", "Sentadilla", "#7F1D1D", listOf(
                    ex("lb1-ex1", "Sentadilla con Barra", "tren_inferior_sentadilla_barra_alta",
                        nSets("lb1e1", 4, 6, 8.0), restTime = 180, damageProfile = DamageProfile.STRETCH),
                    ex("lb1-ex2", "Extensión de Cuádriceps en Máquina", "tren_inferior_extension_cuadriceps",
                        nRirSets("lb1e2", 3, 15, 2), restTime = 90),
                )),
                part("p-lb-2", "Bisagra", "#244B3C", listOf(
                    ex("lb2-ex1", "Peso Muerto Rumano", "tren_inferior_peso_muerto_rumano",
                        nSets("lb2e1", 4, 8, 8.0), restTime = 150, damageProfile = DamageProfile.STRETCH),
                    ex("lb2-ex2", "Curl de Isquiotibiales en Máquina", "tren_inferior_curl_femoral_tumbado",
                        nRirSets("lb2e2", 3, 12, 2), restTime = 90),
                    ex("lb2-ex3", "Hip Thrust con Barra", "tren_inferior_hip_thrust_barra",
                        nSets("lb2e3", 3, 12, 8.0), restTime = 120),
                )),
                part("p-lb-3", "Accesorios", "#1F3A2E", listOf(
                    ex("lb3-ex1", "Prensa de Piernas", "tren_inferior_prensa_45",
                        nSets("lb3e1", 3, 12, 8.0), restTime = 120),
                    ex("lb3-ex2", "Elevación de Pantorrillas", "ultimo_elevacion_gemelos_burro",
                        nSets("lb3e2", 4, 20, 8.5), restTime = 60),
                )),
            ),
        ),
    ),
)
