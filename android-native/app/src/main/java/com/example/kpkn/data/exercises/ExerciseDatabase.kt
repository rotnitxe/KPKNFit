package com.example.kpkn.data.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole

val EXERCISE_DATABASE: List<ExerciseMuscleInfo> = listOf(
    // Pecho
    ExerciseMuscleInfo(id = "press_banca", name = "Press de Banca", involvedMuscles = listOf(InvolvedMuscle("Pecho", MuscleRole.PRIMARY), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY), InvolvedMuscle("Hombro Anterior", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "press_banca_inc", name = "Press de Banca Inclinado", involvedMuscles = listOf(InvolvedMuscle("Pecho Superior", MuscleRole.PRIMARY), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "press_mancuernas", name = "Press con Mancuernas", involvedMuscles = listOf(InvolvedMuscle("Pecho", MuscleRole.PRIMARY), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "aperturas", name = "Aperturas con Mancuernas", involvedMuscles = listOf(InvolvedMuscle("Pecho", MuscleRole.PRIMARY), InvolvedMuscle("Hombro Anterior", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "fondos_pecho", name = "Fondos en Paralelas", involvedMuscles = listOf(InvolvedMuscle("Pecho", MuscleRole.PRIMARY), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY)), equipment = "Peso Corporal"),
    ExerciseMuscleInfo(id = "cable_cruce", name = "Cruce de Poleas", involvedMuscles = listOf(InvolvedMuscle("Pecho", MuscleRole.PRIMARY)), equipment = "Polea"),

    // Espalda
    ExerciseMuscleInfo(id = "peso_muerto", name = "Peso Muerto", involvedMuscles = listOf(InvolvedMuscle("Espalda Baja", MuscleRole.PRIMARY), InvolvedMuscle("Isquiotibiales", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.SECONDARY), InvolvedMuscle("Trapecios", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "dominadas", name = "Dominadas", involvedMuscles = listOf(InvolvedMuscle("Dorsal", MuscleRole.PRIMARY), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY)), equipment = "Peso Corporal"),
    ExerciseMuscleInfo(id = "jalon_polea", name = "Jalón en Polea", involvedMuscles = listOf(InvolvedMuscle("Dorsal", MuscleRole.PRIMARY), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY)), equipment = "Polea"),
    ExerciseMuscleInfo(id = "remo_barra", name = "Remo con Barra", involvedMuscles = listOf(InvolvedMuscle("Dorsal", MuscleRole.PRIMARY), InvolvedMuscle("Trapecio Medio", MuscleRole.SECONDARY), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "remo_mancuerna", name = "Remo con Mancuerna", involvedMuscles = listOf(InvolvedMuscle("Dorsal", MuscleRole.PRIMARY), InvolvedMuscle("Bíceps", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "remo_cable", name = "Remo en Cable Bajo", involvedMuscles = listOf(InvolvedMuscle("Dorsal", MuscleRole.PRIMARY), InvolvedMuscle("Trapecio Medio", MuscleRole.SECONDARY)), equipment = "Polea"),
    ExerciseMuscleInfo(id = "peso_muerto_rumano", name = "Peso Muerto Rumano", involvedMuscles = listOf(InvolvedMuscle("Isquiotibiales", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.PRIMARY), InvolvedMuscle("Espalda Baja", MuscleRole.SECONDARY)), equipment = "Barra"),

    // Hombros
    ExerciseMuscleInfo(id = "press_militar", name = "Press Militar", involvedMuscles = listOf(InvolvedMuscle("Hombro Anterior", MuscleRole.PRIMARY), InvolvedMuscle("Hombro Lateral", MuscleRole.SECONDARY), InvolvedMuscle("Tríceps", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "press_hombro_mancuernas", name = "Press de Hombro con Mancuernas", involvedMuscles = listOf(InvolvedMuscle("Hombro Anterior", MuscleRole.PRIMARY), InvolvedMuscle("Hombro Lateral", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "elevaciones_laterales", name = "Elevaciones Laterales", involvedMuscles = listOf(InvolvedMuscle("Hombro Lateral", MuscleRole.PRIMARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "elevaciones_frontales", name = "Elevaciones Frontales", involvedMuscles = listOf(InvolvedMuscle("Hombro Anterior", MuscleRole.PRIMARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "vuelos_posteriores", name = "Vuelos Posteriores", involvedMuscles = listOf(InvolvedMuscle("Hombro Posterior", MuscleRole.PRIMARY), InvolvedMuscle("Trapecio Medio", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "face_pull", name = "Face Pull", involvedMuscles = listOf(InvolvedMuscle("Hombro Posterior", MuscleRole.PRIMARY), InvolvedMuscle("Manguito Rotador", MuscleRole.SECONDARY)), equipment = "Polea"),

    // Piernas
    ExerciseMuscleInfo(id = "sentadilla", name = "Sentadilla con Barra", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.PRIMARY), InvolvedMuscle("Isquiotibiales", MuscleRole.SECONDARY), InvolvedMuscle("Espalda Baja", MuscleRole.STABILIZER)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "sentadilla_goblet", name = "Sentadilla Goblet", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.SECONDARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "prensa_pierna", name = "Prensa de Pierna", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.SECONDARY)), equipment = "Máquina"),
    ExerciseMuscleInfo(id = "extension_cuadriceps", name = "Extensión de Cuádriceps", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)), equipment = "Máquina"),
    ExerciseMuscleInfo(id = "curl_isquiotibiales", name = "Curl de Isquiotibiales", involvedMuscles = listOf(InvolvedMuscle("Isquiotibiales", MuscleRole.PRIMARY)), equipment = "Máquina"),
    ExerciseMuscleInfo(id = "hip_thrust", name = "Hip Thrust", involvedMuscles = listOf(InvolvedMuscle("Glúteos", MuscleRole.PRIMARY), InvolvedMuscle("Isquiotibiales", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "zancadas", name = "Zancadas", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY), InvolvedMuscle("Glúteos", MuscleRole.PRIMARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "gemelos_maquina", name = "Elevación de Talones (Máquina)", involvedMuscles = listOf(InvolvedMuscle("Gemelos", MuscleRole.PRIMARY)), equipment = "Máquina"),

    // Brazos
    ExerciseMuscleInfo(id = "curl_barra", name = "Curl de Bíceps con Barra", involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY), InvolvedMuscle("Braquial", MuscleRole.SECONDARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "curl_mancuernas", name = "Curl con Mancuernas", involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)), equipment = "Mancuernas"),
    ExerciseMuscleInfo(id = "curl_predicador", name = "Curl en Banco Predicador", involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "triceps_polea", name = "Extensión de Tríceps en Polea", involvedMuscles = listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY)), equipment = "Polea"),
    ExerciseMuscleInfo(id = "triceps_testa", name = "Press Francés", involvedMuscles = listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY)), equipment = "Barra"),
    ExerciseMuscleInfo(id = "fondos_triceps", name = "Fondos para Tríceps", involvedMuscles = listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY)), equipment = "Peso Corporal"),

    // Core
    ExerciseMuscleInfo(id = "plancha", name = "Plancha", involvedMuscles = listOf(InvolvedMuscle("Core", MuscleRole.PRIMARY)), equipment = "Peso Corporal"),
    ExerciseMuscleInfo(id = "crunch_abdominal", name = "Crunch Abdominal", involvedMuscles = listOf(InvolvedMuscle("Abdomen", MuscleRole.PRIMARY)), equipment = "Peso Corporal"),
    ExerciseMuscleInfo(id = "rueda_abdominal", name = "Rueda Abdominal", involvedMuscles = listOf(InvolvedMuscle("Core", MuscleRole.PRIMARY), InvolvedMuscle("Dorsal", MuscleRole.SECONDARY)), equipment = "Rueda"),
    ExerciseMuscleInfo(id = "rotacion_rusa", name = "Rotación Rusa", involvedMuscles = listOf(InvolvedMuscle("Oblicuos", MuscleRole.PRIMARY)), equipment = "Peso Corporal"),
)
