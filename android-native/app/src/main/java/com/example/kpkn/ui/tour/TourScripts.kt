package com.example.kpkn.ui.tour

/** Ids de los recorridos. Se guardan en `Settings.tourSeen` cuando se terminan o se saltan. */
object TourIds {
    const val HOME = "tour-home"
    const val TRAINING = "tour-training"
    const val FIRST_WORKOUT = "tour-first-workout"
    const val NUTRITION = "tour-nutrition"
    const val BODY = "tour-body"
    const val PROFILE = "tour-profile"
}

/** Ids de las anclas (`Modifier.tourAnchor`). Cada uno va en un único elemento visible de su pantalla. */
object TourAnchors {
    const val HOME_SESION = "home.sesion"
    const val HOME_MUSCLES_BTN = "home.muscles.btn"
    const val HOME_PROGRAMAS = "home.programas"
    const val HOME_BARRA = "home.barra"
    const val HOME_PERFIL = "home.perfil"
    const val TRAINING_ESTRUCTURA = "training.estructura"
    const val TRAINING_SEMANA = "training.semana"
    const val TRAINING_SESION = "training.sesion"
    const val TRAINING_EX1 = "training.ex1"
    const val TRAINING_EMPEZAR = "training.empezar"
    const val WORKOUT_SERIE = "workout.serie"
    const val WORKOUT_LOG_BTN = "workout.log.btn"
    const val WORKOUT_DESCANSO = "workout.descanso"
    const val WORKOUT_SIGUIENTE = "workout.siguiente"
    const val NUTRITION_REGISTRO = "nutrition.registro"
    const val NUTRITION_ANILLOS = "nutrition.anillos"
    const val NUTRITION_PLAN = "nutrition.plan"
    const val NUTRITION_HISTORIAL = "nutrition.historial"
    const val BODY_PESAJES = "body.pesajes"
    const val BODY_TENDENCIA = "body.tendencia"
    const val PROFILE_AJUSTES = "profile.ajustes"
    const val PROFILE_COMPETICION = "profile.competicion"
}

/**
 * Pasos de cada recorrido. Títulos y textos van en 90 caracteres como mucho (`TourCopyRulesTest`).
 *
 * Un paso TRY solo va sobre una acción inocua (abrir o desplegar): nunca borra ni empieza un entreno.
 * Si un recorrido ya se vio, [TourController.start] no arranca: esa decisión no se duplica aquí.
 */
object TourScripts {
    fun home(): List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.HOME_SESION,
            tryAnchorId = TourAnchors.HOME_MUSCLES_BTN,
            eyebrow = "INICIO · 1 DE 4",
            title = "Sesión de hoy",
            body = "Toca «Músculos involucrados» para ver qué trabajas hoy.",
            kind = TourStepKind.TRY,
            tryHint = "Toca el botón resaltado",
            cutoutCornerDp = 22f,
        ),
        TourStep(
            anchorId = TourAnchors.HOME_PROGRAMAS,
            eyebrow = "INICIO · 2 DE 4",
            title = "Tus programas",
            body = "Desliza para cambiar de rutina, ver semanas o crear un programa nuevo.",
            cutoutCornerDp = 22f,
        ),
        TourStep(
            anchorId = TourAnchors.HOME_BARRA,
            eyebrow = "INICIO · 3 DE 4",
            title = "Barra inferior",
            body = "Inicio, Entreno, Nutrición y Cuerpo: las cuatro áreas principales a un toque.",
            cutoutCornerDp = 32f,
        ),
        TourStep(
            anchorId = TourAnchors.HOME_PERFIL,
            eyebrow = "INICIO · 4 DE 4",
            title = "Tu perfil y ajustes",
            body = "Aquí arriba viven tu perfil, progreso global, competiciones y ajustes.",
            cutoutCornerDp = 99f,
        ),
    )

    /** Detalle del programa (Entreno), en orden visual descendente. */
    val training: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.TRAINING_ESTRUCTURA,
            eyebrow = "ENTRENO · 1 DE 4",
            title = "Vista del programa",
            body = "Alterna entre la semana actual, tu progreso por músculo y la estructura.",
            cutoutCornerDp = 26f,
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_SEMANA,
            eyebrow = "ENTRENO · 2 DE 4",
            title = "Días de la semana",
            body = "Cambia de día para revisar cada sesión; las completadas quedan en verde.",
            cutoutCornerDp = 22f,
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_SESION,
            tryAnchorId = TourAnchors.TRAINING_EX1,
            eyebrow = "ENTRENO · 3 DE 4",
            title = "Explora los ejercicios",
            body = "Toca un ejercicio para ver sus series o edítalo cuando quieras.",
            kind = TourStepKind.TRY,
            tryHint = "Toca el primer ejercicio",
            cutoutCornerDp = 20f,
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_EMPEZAR,
            eyebrow = "ENTRENO · 4 DE 4",
            title = "Empezar sesión",
            body = "Pulsa aquí cuando estés listo para iniciar el entrenamiento en vivo.",
            cutoutCornerDp = 28f,
        ),
    )

    /** Primera sesión en curso: serie → descanso automático → selector de siguiente ejercicio. */
    val firstWorkout: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.WORKOUT_SERIE,
            tryAnchorId = TourAnchors.WORKOUT_LOG_BTN,
            eyebrow = "SESIÓN · 1 DE 3",
            title = "Registra tu primera serie",
            body = "Ajusta peso y reps y pulsa «Registrar serie» para abrir el descanso.",
            kind = TourStepKind.TRY,
            tryHint = "Pulsa «Registrar serie»",
            cutoutCornerDp = 26f,
        ),
        TourStep(
            anchorId = TourAnchors.WORKOUT_DESCANSO,
            eyebrow = "SESIÓN · 2 DE 3",
            title = "Descanso automático",
            body = "Tu temporizador arranca solo tras cada serie; suma +15 s o sáltalo.",
            cutoutCornerDp = 22f,
        ),
        TourStep(
            anchorId = TourAnchors.WORKOUT_SIGUIENTE,
            eyebrow = "SESIÓN · 3 DE 3",
            title = "Siguiente ejercicio",
            body = "Toca el selector inferior o desliza entre tarjetas para cambiar.",
            cutoutCornerDp = 28f,
        ),
    )

    /** Nutrición: copy adaptativo según si el usuario ya tiene un plan activo. */
    fun nutrition(hasActivePlan: Boolean): List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.NUTRITION_REGISTRO,
            eyebrow = "NUTRICIÓN · 1 DE 4",
            title = "Registro inteligente",
            body = "Escribe o dicta lo que comiste con tus palabras: KPKN calcula macros.",
            cutoutCornerDp = 32f,
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_ANILLOS,
            eyebrow = "NUTRICIÓN · 2 DE 4",
            title = "Anillos de macros",
            body = "Controla calorías, proteína, carbohidratos y grasas del día.",
            cutoutCornerDp = 24f,
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_PLAN,
            eyebrow = "NUTRICIÓN · 3 DE 4",
            title = if (hasActivePlan) "Tu plan calórico" else "Crea tu plan cuando quieras",
            body = if (hasActivePlan) {
                "Ajusta tus metas calóricas o reparto de macros desde este acceso."
            } else {
                "Aún no tienes meta: pulsa «Crear plan» para definir tus calorías objetivo."
            },
            cutoutCornerDp = 24f,
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_HISTORIAL,
            eyebrow = "NUTRICIÓN · 4 DE 4",
            title = "Historial semanal",
            body = "Revisa tu adherencia diaria y recupera comidas de días anteriores.",
            cutoutCornerDp = 24f,
        ),
    )

    /** Cuerpo: copy adaptativo según si ya existen pesajes registrados. */
    fun body(hasEntries: Boolean): List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.BODY_PESAJES,
            eyebrow = "CUERPO · 1 DE 2",
            title = "Añade tus pesajes",
            body = "Registra peso o medidas desde aquí en dos toques.",
            cutoutCornerDp = 32f,
        ),
        TourStep(
            anchorId = TourAnchors.BODY_TENDENCIA,
            eyebrow = "CUERPO · 2 DE 2",
            title = if (hasEntries) "Tendencia y meta" else "Tu curva arranca aquí",
            body = if (hasEntries) {
                "La media móvil filtra fluctuaciones y la compara con tu objetivo."
            } else {
                "Con al menos 3 pesajes verás tu línea de tendencia real sin ruido diario."
            },
            cutoutCornerDp = 24f,
        ),
    )

    /** Perfil: acceso a «Repetir el recorrido» en Ajustes y Competiciones. */
    val profile: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.PROFILE_AJUSTES,
            eyebrow = "PERFIL · 1 DE 2",
            title = "Ajustes y guía",
            body = "Cambia unidades, respaldos y toca «Repetir el recorrido» aquí.",
            cutoutCornerDp = 99f,
        ),
        TourStep(
            anchorId = TourAnchors.PROFILE_COMPETICION,
            eyebrow = "PERFIL · 2 DE 2",
            title = "Competiciones",
            body = "Guarda fechas de tarima y marcas pasadas con cuenta atrás en Inicio.",
            cutoutCornerDp = 24f,
        ),
    )

    /** Recorrido de cada id (para pruebas de textos y anclas). */
    fun todos(
        hasActivePlan: Boolean = true,
        hasEntries: Boolean = true,
    ): Map<String, List<TourStep>> = mapOf(
        TourIds.HOME to home(),
        TourIds.TRAINING to training,
        TourIds.FIRST_WORKOUT to firstWorkout,
        TourIds.NUTRITION to nutrition(hasActivePlan = hasActivePlan),
        TourIds.BODY to body(hasEntries = hasEntries),
        TourIds.PROFILE to profile,
    )
}
