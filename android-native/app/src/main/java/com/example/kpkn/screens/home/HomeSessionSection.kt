package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.exercises.exerciseCatalogReady
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.SessionMuscleFilter
import com.example.kpkn.domain.exercises.muscleDisplayLabel
import com.example.kpkn.domain.training.CompetitionHomePhase
import com.example.kpkn.domain.training.HomeCompetitionState
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.home.design.HomeHeroActions
import com.example.kpkn.screens.home.design.HomeHeroCountdown
import com.example.kpkn.screens.home.design.HomeHeroLine
import com.example.kpkn.screens.home.design.HomeHeroMode
import com.example.kpkn.screens.home.design.HomeHeroState
import com.example.kpkn.screens.home.design.HomeSessionMuscle
import com.example.kpkn.screens.home.design.HomeSessionHero
import com.example.kpkn.screens.home.design.heroDose
import com.example.kpkn.screens.home.design.homeHeroKicker
import com.example.kpkn.screens.home.design.homeHeroNextLine
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.design.programNextSessionOf
import com.example.kpkn.ui.components.PagerDots
import com.example.kpkn.ui.design.KpknTextAction
import java.time.LocalDate

/**
 * La sesión del día (Inicio), dentro del módulo «Esta semana» (I10a): el héroe compacto del día elegido en «Tu semana», con los
 * músculos como fila de chips dentro del propio héroe. Sin día elegido, o con hoy, es la sesión de hoy: la preferida, y un pager si
 * hay varias. Un día fuera de «Tu semana» se trata como hoy. Cada día que no es hoy muestra su sesión (OTHER_DAY) o el descanso.
 */
@Composable
fun HomeSessionSection(
    sessions: List<TodaySessionItem>,
    homeCompetition: HomeCompetitionState? = null,
    hasActiveProgram: Boolean = true,
    onStartWorkout: (Session, Program) -> Unit,
    onRegisterCompetition: (String) -> Unit = {},
    /** El día de la competencia: abre su modo competición por id (Registros v4, c-today). */
    onOpenCompetitionToday: (String) -> Unit = {},
    /** La cuenta atrás de la competencia abre la página «Competencias». */
    onOpenCompetitions: () -> Unit = {},
    /** Programa activo al que pedir la fecha de competición («Sí, sin fecha»); null si no toca (C4c). */
    competitionDatePromptProgramId: String? = null,
    onOpenProgramForCompetitionDate: (String) -> Unit = {},
    onResumeWorkout: () -> Unit,
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onCreateProgram: () -> Unit = {},
    /** Lleva a elegir programa (la acción «Elige tu programa» cuando no hay programa activo). */
    onChooseProgram: () -> Unit = {},
    /** Día elegido en «Tu semana»; null = hoy. Si no está en [weekDays], se trata como hoy. */
    selectedDate: LocalDate? = null,
    /** Los días de «Tu semana» (I1): el «Siguiente», el «DÍA n DE m» y el día elegido. */
    weekDays: List<HomeWeekDay> = emptyList(),
    /** Programa activo: el que abre «Ver programa» en el descanso, cuando no hay sesión que abrir. */
    activeProgram: Program? = null,
    /** Abre el programa (la acción «Ver en el programa» de un día que no es hoy). */
    onNavigateToProgram: (String) -> Unit = {},
    /** Abre el detalle de Músculos desde las cifras (lo cablea I3). */
    onOpenMuscleDetail: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val isCatalogReady by exerciseCatalogReady.collectAsStateWithLifecycle()
    val reduced = wizardReducedMotion()
    val liftPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val today = LocalDate.now()
    val day = selectedDate?.takeIf { it != today && weekDays.any { d -> d.date == it } } ?: today

    val pages = remember(
        day, today, sessions, weekDays, hasActiveProgram, homeCompetition, activeProgram,
        customExercises, isCatalogReady,
    ) {
        homeHeroPages(day, today, sessions, weekDays, hasActiveProgram, homeCompetition, activeProgram)
    }
    val pagerState = rememberPagerState(initialPage = preferredHeroPage(pages)) { pages.size }
    // Al cambiar de día, el pager vuelve a la sesión preferida de ese día.
    LaunchedEffect(day) {
        val target = preferredHeroPage(pages)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    val multi = pages.size > 1
    val shown = pages[if (multi) pagerState.currentPage.coerceIn(0, pages.lastIndex) else 0]
    // La fecha de competición se pide en el día de hoy y de los que entrenan; no en la competición ni sin programa.
    val promptProgramId = competitionDatePromptProgramId?.takeIf {
        shown.state.mode != HomeHeroMode.REGISTER && shown.state.mode != HomeHeroMode.NO_PROGRAM
    }

    fun actionsFor(page: HomeHeroPage): HomeHeroActions {
        val item = page.item
        return HomeHeroActions(
            onStart = { item?.let { onStartWorkout(it.session, it.program) } },
            onResume = onResumeWorkout,
            onEdit = { item?.let { onEditSession(it.session, it.program) } },
            // Con sesión, abre su programa; en el descanso (sin sesión), el programa activo. Sin programa, no hay botón.
            onOpenProgram = (item?.program ?: activeProgram)?.let { program -> { onNavigateToProgram(program.id) } },
            onChooseProgram = onChooseProgram,
            onCreateProgram = onCreateProgram,
            onRegister = { homeCompetition?.let { onRegisterCompetition(it.recordId) } },
            // Registros v4 (C-D2): el día de la competencia el héroe abre su modo competición (c-today), no el programa.
            onOpenCompetition = homeCompetition
                ?.takeIf { it.phase == CompetitionHomePhase.MEET_DAY }
                ?.let { competition -> { if (competition.recordId.isBlank()) onOpenCompetitions() else onOpenCompetitionToday(competition.recordId) } },
            onOpenCountdown = onOpenCompetitions,
        )
    }

    Column(modifier.fillMaxWidth()) {
        if (multi) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                val page = pages[index]
                HomeSessionHero(
                    state = page.state,
                    actions = actionsFor(page),
                    muscles = gaugesFor(page),
                    onOpenMuscles = onOpenMuscleDetail,
                )
            }
            PagerDots(
                count = pages.size,
                current = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        } else {
            AnimatedContent(
                targetState = pages.first(),
                contentKey = { it.state.key },
                transitionSpec = {
                    when {
                        reduced -> EnterTransition.None togetherWith ExitTransition.None
                        // Mismo día: solo fundido. Otro día: la sesión nueva entra desde su lado, 12 dp, y se funde.
                        initialState.date == targetState.date ->
                            fadeIn(tween(durationMillis = 220)) togetherWith fadeOut(tween(durationMillis = 220))
                        else -> {
                            val side = if (targetState.date > initialState.date) 1 else -1
                            (fadeIn(tween(durationMillis = 220)) + slideInHorizontally(tween(durationMillis = 220)) { side * liftPx })
                                .togetherWith(fadeOut(tween(durationMillis = 220)))
                        }
                    }
                },
                label = "homeHeroDay",
            ) { page ->
                HomeSessionHero(
                    state = page.state,
                    actions = actionsFor(page),
                    muscles = gaugesFor(page),
                    onOpenMuscles = onOpenMuscleDetail,
                )
            }
        }
        promptProgramId?.let { CompetitionDatePrompt(programId = it, onOpenProgram = onOpenProgramForCompetitionDate) }
    }
}

/** Tag de prueba de la acción «Añadir la fecha de tu competición» (C4c). */
internal const val COMPETITION_DATE_PROMPT_TAG = "home-competition-date-prompt"

/** La acción «Añadir la fecha de tu competición», al pie del módulo: un [KpknTextAction] (C4c). */
@Composable
private fun CompetitionDatePrompt(programId: String, onOpenProgram: (String) -> Unit) {
    Box(modifier = Modifier.padding(top = 8.dp)) {
        KpknTextAction(
            text = "Añadir la fecha de tu competición",
            onClick = { onOpenProgram(programId) },
            modifier = Modifier.testTag(COMPETITION_DATE_PROMPT_TAG),
        )
    }
}

/** Una página del héroe: su día, su estado, la sesión que muestra (null si no hay) y los músculos de sus cifras. */
private data class HomeHeroPage(
    val date: LocalDate,
    val state: HomeHeroState,
    val item: TodaySessionItem?,
    val muscles: List<String>,
) {
    /** Las cifras de músculos salen si la sesión es de hoy o de otro día que se ve, y tiene músculos. */
    val showsMuscles: Boolean
        get() = muscles.isNotEmpty() && state.mode in setOf(
            HomeHeroMode.TODAY,
            HomeHeroMode.ONGOING,
            HomeHeroMode.DONE,
            HomeHeroMode.OTHER_DAY,
        )
}

/** Authored muscles of the scheduled session, without recovery percentages or implied readiness. */
private fun gaugesFor(page: HomeHeroPage): List<HomeSessionMuscle> =
    if (page.showsMuscles) page.muscles.map { HomeSessionMuscle(it) } else emptyList()

/** La página preferida: la sesión en curso, si no la que toca hoy, si no la primera. */
private fun preferredHeroPage(pages: List<HomeHeroPage>): Int =
    pages.indexOfFirst { it.state.mode == HomeHeroMode.ONGOING }.takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.state.mode == HomeHeroMode.TODAY }.takeIf { it >= 0 }
        ?: 0

/**
 * Las páginas del héroe para [day]: el competidor de hoy (REGISTER), la falta de programa, el descanso o las sesiones del día.
 * Hoy puede tener varias sesiones (pager); otro día, una sola: la principal.
 */
private fun homeHeroPages(
    day: LocalDate,
    today: LocalDate,
    sessions: List<TodaySessionItem>,
    weekDays: List<HomeWeekDay>,
    hasActiveProgram: Boolean,
    homeCompetition: HomeCompetitionState?,
    activeProgram: Program?,
): List<HomeHeroPage> {
    val onToday = day == today
    val countdown = homeCompetition?.takeIf { it.phase == CompetitionHomePhase.UPCOMING }?.let(::heroCountdown)

    val competition = homeCompetition?.takeIf { onToday && it.phase in setOf(CompetitionHomePhase.REGISTER,CompetitionHomePhase.MEET_DAY) }
    if (competition != null) {
        return listOf(
            HomeHeroPage(
                date = day,
                state = HomeHeroState(
                    key = "$day/REGISTER",
                    mode = HomeHeroMode.REGISTER,
                    kicker = if (competition.phase == CompetitionHomePhase.MEET_DAY) "Competición hoy" else homeHeroKicker(HomeHeroMode.REGISTER, day, null, null),
                    title = if (competition.phase == CompetitionHomePhase.MEET_DAY) "Hoy compites · ${competition.title}" else "¿Cómo te fue en ${competition.title}?",
                    body = null,
                    meta = "${competition.countdownLabel} · ${competition.competitionDateLabel}",
                ),
                item = null,
                muscles = emptyList(),
            ),
        )
    }

    if (!hasActiveProgram) {
        return listOf(
            HomeHeroPage(
                date = day,
                state = HomeHeroState(
                    key = "$day/NO_PROGRAM",
                    mode = HomeHeroMode.NO_PROGRAM,
                    kicker = homeHeroKicker(HomeHeroMode.NO_PROGRAM, day, null, null),
                    title = "Aún no tienes programa",
                    body = "Crea uno con KPKN o importa el tuyo.",
                ),
                item = null,
                muscles = emptyList(),
            ),
        )
    }

    val dayItems = sessions.filter { it.dayOfWeek == day.dayOfWeek.value }
    if (dayItems.isEmpty()) {
        return listOf(
            HomeHeroPage(
                date = day,
                state = HomeHeroState(
                    key = "$day/REST",
                    mode = HomeHeroMode.REST,
                    kicker = homeHeroKicker(HomeHeroMode.REST, day, null, null),
                    title = "Descanso",
                    body = homeHeroNextLine(
                        weekDays,
                        HomeWeekResolver.nextSession(activeProgram, sessions, weekDays, day),
                        // Sin sesión datada, la del programa en su semana: la misma función que Program Detail.
                        programNextSessionOf(
                            sessions = weekDays.mapNotNull { d -> d.sessionName?.let { d.date.dayOfWeek.value to it } },
                            today = day.dayOfWeek.value,
                        ),
                    ),
                    countdown = countdown,
                ),
                item = null,
                muscles = emptyList(),
            ),
        )
    }

    // «DÍA n DE m»: la posición del día entre los que tienen sesión en «Tu semana».
    val sessionDays = weekDays.filter { it.sessionName != null }
    val position = sessionDays.indexOfFirst { it.date == day }.takeIf { it >= 0 }?.plus(1)
    val count = sessionDays.size.takeIf { it > 0 }
    val picked = if (onToday) {
        dayItems
    } else {
        listOf(dayItems.firstOrNull { it.session.isMainSession } ?: dayItems.first())
    }
    return picked.map { item ->
        sessionHeroPage(item, day, onToday, position, count, countdown)
    }
}

/** La página de una sesión: su modo (hoy, en curso, hecha u otro día), sus ejercicios, su meta y sus músculos. */
private fun sessionHeroPage(
    item: TodaySessionItem,
    day: LocalDate,
    onToday: Boolean,
    position: Int?,
    count: Int?,
    countdown: HomeHeroCountdown?,
): HomeHeroPage {
    val session = item.session
    val mode = when {
        !onToday -> HomeHeroMode.OTHER_DAY
        item.isOngoing -> HomeHeroMode.ONGOING
        item.isCompleted -> HomeHeroMode.DONE
        else -> HomeHeroMode.TODAY
    }
    val exercises = session.allExercises()
    val log = item.log
    val meta = if (mode == HomeHeroMode.DONE && log != null) doneMeta(log) else estimateMeta(session, exercises.size)
    return HomeHeroPage(
        date = day,
        state = HomeHeroState(
            key = "$day/${mode.name}/${session.id}",
            mode = mode,
            kicker = homeHeroKicker(mode, day, position, count),
            title = session.name.ifBlank { "Sesión" },
            lines = exercises.take(3).map { exercise ->
                HomeHeroLine(name = exercise.displayNameWithSelectedChips(), dose = heroDose(exercise).orEmpty())
            },
            moreCount = (exercises.size - 3).coerceAtLeast(0),
            meta = meta,
            advisoryBadge = null,
            countdown = countdown,
        ),
        item = item,
        muscles = getSessionInvolvedMuscles(session),
    )
}

/** La meta de una sesión hecha: duración real y series completadas, del registro. */
private fun doneMeta(log: WorkoutLog): String {
    val sets = com.example.kpkn.screens.workout.logicalSetCountFromCompleted(log.completedExercises)
    return listOfNotNull(
        "${log.durationMinutes} min",
        sets.takeIf { it > 0 }?.let { if (it == 1) "1 serie" else "$it series" },
    ).joinToString(" · ")
}

/** La meta de una sesión por hacer: la duración estimada («≈ 60 min») y el número de ejercicios. */
private fun estimateMeta(session: Session, exerciseCount: Int): String? {
    val minutes = estimatedSessionMinutes(session)
    return listOfNotNull(
        minutes?.let { "≈ $it min" },
        exerciseCount.takeIf { it > 0 }?.let { if (it == 1) "1 ejercicio" else "$it ejercicios" },
    ).joinToString(" · ").ifEmpty { null }
}

/**
 * Minutos estimados de una sesión con el estimador común (`SessionDurationEstimator`, SE12): los mismos que el editor y el programa.
 * Null si la sesión no tiene nada que medir.
 */
internal fun estimatedSessionMinutes(session: Session): Int? {
    val estimate = runCatching { SessionDurationEstimator.estimate(session) }.getOrNull() ?: return null
    return estimate.totalMinutes.takeIf { estimate.totalSeconds > 0 }
}

/** La cuenta atrás de la competición para el héroe: «23 días para tu competición» o «Mañana es tu competición». */
private fun heroCountdown(state: HomeCompetitionState): HomeHeroCountdown =
    if (state.daysUntil == 1L) {
        HomeHeroCountdown(value = "Mañana", unit = null, label = "es tu competición")
    } else {
        HomeHeroCountdown(value = "${state.daysUntil}", unit = "días", label = "para tu competición")
    }

/** Los músculos que trabaja una sesión, con el nombre que muestra Auge. */
private fun getSessionInvolvedMuscles(session: Session): List<String> {
    val muscles = linkedSetOf<String>()

    fun collectMuscles(exercise: Exercise) {
        val info = resolveCatalogExerciseInfo(
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        )

        if (info != null) {
            SessionMuscleFilter.relevantMusclesFor(info)
                .asSequence()
                .map { involvement -> muscleDisplayLabel(involvement.muscle, involvement.emphasis) }
                .filter { it.isNotBlank() }
                .forEach(muscles::add)
        } else if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            exercise.effectiveMuscles
                .asSequence()
                .map { involvement -> muscleDisplayLabel(involvement.muscle, involvement.emphasis) }
                .filter { it.isNotBlank() }
                .forEach(muscles::add)
        }
    }

    session.exercises.forEach(::collectMuscles)
    session.parts.forEach { part -> part.exercises.forEach(::collectMuscles) }

    return muscles.toList()
}

