package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/*
 * Modelos de datos de los componentes del objetivo y del programa (paquete visual puro, sin ViewModel). Quien integra
 * (S-B) los alimenta desde el generador y desde el catálogo de planes; aquí solo se pintan.
 */

/**
 * Una tarjeta de programa: lo que muestra la portada y el carrusel.
 *
 * - [kicker]: qué es o de quién es («Hecho a tu medida», «Jim Wendler», «Versión inicial»).
 * - [blurb]: una o dos frases amables, hasta 110 caracteres.
 * - [profile]: la disciplina que da el acento y la ilustración de la portada (`null` = ilustración neutra).
 * - [badge]: una insignia opcional («Recomendado», «Se adapta a ti»).
 * - [coverSeed]: variación de la portada (ángulo del degradado, posición de la ilustración), para que dos programas de
 *   una misma disciplina no se vean idénticos.
 */
data class PlanCardModel(
    val id: String,
    val title: String,
    val kicker: String,
    val blurb: String,
    val profile: TrainingGoalProfile?,
    val daysLabel: String,
    val minutesLabel: String,
    val levelLabel: String,
    val badge: String? = null,
    val coverSeed: Int = 0,
)

/**
 * Un día de la semana tipo. [day] es el día de la semana (1 = lunes … 7 = domingo); los días que no aparecen en la
 * lista son de descanso. [title] es el título corto («Torso»), [minutes] la duración y [isMain] marca la sesión principal.
 */
data class PlanDayModel(val day: Int, val title: String, val minutes: Int, val exerciseCount: Int, val isMain: Boolean)

/** Un bloque de la estructura del programa («Acumulación», «Semanas 1–4», «Más volumen, cargas moderadas»). */
data class PlanBlockModel(val label: String, val weeksLabel: String, val detail: String)

/**
 * Todo lo que enseña el detalle de un programa.
 *
 * - [mainExercises]: los ejercicios principales (se muestran hasta seis).
 * - [reasons]: «Por qué este programa» (las razones del generador).
 * - [notes]: notas honestas («Versión inicial: …»); [attribution]: de quién es el método, si aplica.
 */
data class PlanDetailModel(
    val card: PlanCardModel,
    val description: String,
    val mainExercises: List<String>,
    val blocks: List<PlanBlockModel>,
    val week: List<PlanDayModel>,
    val reasons: List<String>,
    val notes: List<String>,
    val attribution: String?,
)

/** Cuántos ejercicios principales enseña el detalle como máximo. */
internal const val MAX_MAIN_EXERCISES = 6

// ---------------------------------------------------------------- acentos por disciplina

/**
 * El acento de una disciplina (el mismo en el símbolo de la lista, en la portada y en el detalle): powerlifting y
 * armwrestling, músculo; powerbuilding y strongman, energía; culturismo, mente; calistenia, ok; halterofilia, columna.
 * Los perfiles generales y la ausencia de perfil llevan tinta cálida con un detalle verde (ok).
 */
internal fun goalAccent(profile: TrainingGoalProfile?): Color = when (profile) {
    TrainingGoalProfile.POWERLIFTING -> SymbolPalette.musculo
    TrainingGoalProfile.ARMWRESTLING -> SymbolPalette.musculo
    TrainingGoalProfile.POWERBUILDING -> SymbolPalette.energia
    TrainingGoalProfile.STRONGMAN -> SymbolPalette.energia
    TrainingGoalProfile.BODYBUILDING -> SymbolPalette.mente
    TrainingGoalProfile.CALISTHENICS -> SymbolPalette.ok
    TrainingGoalProfile.WEIGHTLIFTING -> SymbolPalette.columna
    TrainingGoalProfile.STRENGTH_MUSCLE,
    TrainingGoalProfile.STRENGTH_CARDIO,
    TrainingGoalProfile.FUNCTIONAL_HEALTH,
    null,
    -> SymbolPalette.ok
}

/** Marca de prueba de una fila del objetivo: `setup-goal-POWERLIFTING`, … */
internal fun goalProfileTag(profile: TrainingGoalProfile): String = "setup-goal-${profile.name}"

/** Marca de prueba de una tarjeta del carrusel: `setup-plan-card-<id>`. */
internal fun planCardTag(id: String): String = "setup-plan-card-$id"

/** Textos fijos de estos componentes (el resto sale de los modelos). Ver `docs/entreno-v2/COPY.md`. */
internal object PlanCopy {
    const val GOALS_GENERAL = "Generales"
    const val GOALS_SPECIFIC = "Disciplinas"
    const val GOALS_SPECIFIC_NOTE = "Dependen de tu material."
    const val BLOCKED_ACTION = "Cambiar mi material"

    const val PREPARING_GENERAL = "Estamos preparando tu programa personalizado"
    const val PREPARING_DISCIPLINE = "Seleccionando programas para tu disciplina"

    val STAGES_GENERAL = listOf("Tu material", "Tus días", "Tu tiempo", "Tus músculos", "Tus ejercicios")
    val STAGES_DISCIPLINE = listOf("Tu disciplina", "Tu material", "Tus días", "Tu nivel", "Los mejores programas")

    const val SEE_DETAILS = "Ver detalles"
    const val CHOOSE = "Elegir"
    const val CHOSEN = "Elegido"

    const val CHOOSE_PROGRAM = "Elegir este programa"
    const val PROGRAM_CHOSEN = "Programa elegido"
    const val EDIT_FREELY = "Podrás modificarlo libremente después."
    const val CLOSE = "Cerrar"
    const val SEE_MORE = "Ver más"
    const val SEE_LESS = "Ver menos"

    const val SECTION_EXERCISES = "Ejercicios principales"
    const val SECTION_STRUCTURE = "Estructura"
    const val SECTION_WEEK = "Tu semana"
    const val SECTION_REASONS = "Por qué este programa"
    const val SECTION_NOTES = "Ten en cuenta"

    /** Iniciales de los días de la semana, de lunes a domingo (el miércoles es «Mi»: una X suelta no se lee como un día). */
    val WEEKDAY_INITIALS = listOf("L", "M", "Mi", "J", "V", "S", "D")
    val WEEKDAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
}
