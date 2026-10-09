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
    const val HOME_RINGS = "home.rings"
    const val HOME_PROGRAMAS = "home.programas"
    const val HOME_BARRA = "home.barra"
    const val TRAINING_SEMANA = "training.semana"
    const val TRAINING_SESION = "training.sesion"
    const val TRAINING_ESTRUCTURA = "training.estructura"
    const val TRAINING_EMPEZAR = "training.empezar"
    const val WORKOUT_SERIE = "workout.serie"
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
 * Un paso TRY solo va sobre una acción inocua (abrir o desplegar): nunca borra, guarda ni empieza un entreno.
 * Si un recorrido ya se vio, [TourController.start] no arranca: esa decisión no se duplica aquí.
 */
object TourScripts {
    /** Inicio. En plan Free no hay torre de RINGS: el paso se omite en vez de esperar su ancla. */
    fun home(conTorre: Boolean): List<TourStep> = listOfNotNull(
        TourStep(
            anchorId = TourAnchors.HOME_SESION,
            title = "Sesión de hoy",
            body = "Tu sesión de hoy. Toca «Músculos involucrados» para ver qué trabaja.",
            kind = TourStepKind.TRY,
        ),
        TourStep(
            anchorId = TourAnchors.HOME_RINGS,
            title = "Tus RINGS",
            body = "Tus RINGS: músculos, energía y columna. Toca uno para ver el detalle.",
            kind = TourStepKind.TRY,
        ).takeIf { conTorre },
        TourStep(
            anchorId = TourAnchors.HOME_PROGRAMAS,
            title = "Tus programas",
            body = "Aquí están tus programas. Puedes cambiar de programa cuando quieras.",
        ),
        TourStep(
            anchorId = TourAnchors.HOME_BARRA,
            title = "Barra inferior",
            body = "Entreno, Nutrición, Cuerpo y Perfil: todo a un toque.",
        ),
    )

    /**
     * Primera sesión en curso. Sin pasos de descanso ni de final: el descanso solo aparece tras registrar una serie
     * (el botón de registro queda fuera del recorte) y el final se hace desde el diálogo de salida.
     */
    val firstWorkout: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.WORKOUT_SERIE,
            title = "Serie",
            body = "Anota el peso y las repeticiones y registra la serie.",
        ),
        TourStep(
            anchorId = TourAnchors.WORKOUT_SIGUIENTE,
            title = "Siguiente ejercicio",
            body = "Desliza o toca para pasar al siguiente ejercicio.",
        ),
    )

    /**
     * Nutrición. No hay señal de carga en su ViewModel: el recorrido arranca al montar la pantalla.
     * «Tu plan» solo existe con plan activo; sin él, ese paso se salta solo.
     */
    val nutrition: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.NUTRITION_REGISTRO,
            title = "Registro",
            body = "Escribe lo que comiste con tus palabras: KPKN lo entiende.",
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_ANILLOS,
            title = "Anillos de macros",
            body = "Tus calorías y macros del día, de un vistazo.",
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_PLAN,
            title = "Tu plan",
            body = "Tu plan de alimentación. Puedes ajustarlo cuando quieras.",
        ),
        TourStep(
            anchorId = TourAnchors.NUTRITION_HISTORIAL,
            title = "Historial",
            body = "Aquí ves lo que has comido otros días.",
        ),
    )

    /** Cuerpo. Sin señal de carga: arranca al montar la pantalla. La tendencia y el objetivo solo aparecen con datos. */
    val body: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.BODY_PESAJES,
            title = "Pesajes",
            body = "Añade tus pesajes: con unos pocos ves tu tendencia.",
        ),
        TourStep(
            anchorId = TourAnchors.BODY_TENDENCIA,
            title = "Tendencia y objetivo",
            body = "Tu tendencia, y tu objetivo si lo tienes.",
        ),
    )

    /**
     * Perfil. El recorrido vive en esta pantalla: «Repetir el recorrido» está dentro de Ajustes,
     * así que el paso apunta al icono de ajustes y dice dónde está.
     */
    val profile: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.PROFILE_AJUSTES,
            title = "Ajustes",
            body = "Si quieres volver a ver esta guía, está en «Repetir el recorrido».",
        ),
        TourStep(
            anchorId = TourAnchors.PROFILE_COMPETICION,
            title = "Competiciones",
            body = "Tus competiciones, las próximas y las pasadas.",
        ),
    )

    /** Recorrido de cada id, con la torre de RINGS activa (para pruebas de textos y anclas). */
    fun todos(): Map<String, List<TourStep>> = mapOf(
        TourIds.HOME to home(conTorre = true),
        TourIds.TRAINING to training,
        TourIds.FIRST_WORKOUT to firstWorkout,
        TourIds.NUTRITION to nutrition,
        TourIds.BODY to body,
        TourIds.PROFILE to profile,
    )

    /** Detalle del programa (Entreno). */
    val training: List<TourStep> = listOf(
        TourStep(
            anchorId = TourAnchors.TRAINING_SEMANA,
            title = "Semana",
            body = "Tu semana empieza en tu día con más energía.",
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_SESION,
            title = "Una sesión",
            body = "Toca los ejercicios para verlos. Con editar, cambias el que quieras.",
            kind = TourStepKind.TRY,
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_ESTRUCTURA,
            title = "Estructura",
            body = "Así avanza tu programa, bloque a bloque.",
        ),
        TourStep(
            anchorId = TourAnchors.TRAINING_EMPEZAR,
            title = "Empezar sesión",
            body = "Cuando estés listo, empieza aquí.",
        ),
    )
}
