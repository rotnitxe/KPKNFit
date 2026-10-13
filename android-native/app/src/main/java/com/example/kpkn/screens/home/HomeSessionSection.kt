package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.exercises.exerciseCatalogReady
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadAdvisory
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.auge.AxialSessionExercise
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.auge.lookupMuscleValue
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import com.example.kpkn.domain.training.CompetitionHomePhase
import com.example.kpkn.domain.training.HomeCompetitionState
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.screens.home.design.HomeHeroActions
import com.example.kpkn.screens.home.design.HomeHeroArt
import com.example.kpkn.screens.home.design.HomeHeroLine
import com.example.kpkn.screens.home.design.HomeHeroMode
import com.example.kpkn.screens.home.design.HomeHeroState
import com.example.kpkn.screens.home.design.HomeMuscleGauge
import com.example.kpkn.screens.home.design.HomeMuscleGauges
import com.example.kpkn.screens.home.design.HomeSectionGap
import com.example.kpkn.screens.home.design.HomeSessionHero
import com.example.kpkn.screens.home.design.heroDose
import com.example.kpkn.screens.home.design.homeHeroFallbackIdentity
import com.example.kpkn.screens.home.design.homeHeroKicker
import com.example.kpkn.screens.home.design.homeHeroNextLine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.PagerDots
import java.time.LocalDate

/**
 * La sesión del día (Inicio): el héroe a sangre del día elegido en «Tu semana» (I2), con las cifras de sus músculos debajo. Sin
 * día elegido, o con hoy, es la sesión de hoy: la preferida, y un pager si hay varias. Un día fuera de «Tu semana» se trata como
 * hoy. Cada día que no es hoy muestra su sesión (OTHER_DAY) o el descanso.
 */
@Composable
fun HomeSessionSection(
    sessions: List<TodaySessionItem>,
    homeCompetition: HomeCompetitionState? = null,
    hasActiveProgram: Boolean = true,
    @Suppress("UNUSED_PARAMETER") currentDayOfWeek: Int,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    loadAdvisory: LoadAdvisory? = null,
    onStartWorkout: (Session, Program) -> Unit,
    onRegisterCompetition: (String) -> Unit = {},
    /** Programa activo al que pedir la fecha de competición («Sí, sin fecha»); null si no toca (C4c). */
    competitionDatePromptProgramId: String? = null,
    onOpenProgramForCompetitionDate: (String) -> Unit = {},
    onResumeWorkout: () -> Unit,
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onCreateProgram: () -> Unit = {},
    /** Día elegido en «Tu semana»; null = hoy. Si no está en [weekDays], se trata como hoy. */
    selectedDate: LocalDate? = null,
    /** Los días de «Tu semana» (I1): el «Siguiente», el «DÍA n DE m» y el día elegido. */
    weekDays: List<HomeWeekDay> = emptyList(),
    /** Programa activo: su arte cuando esta semana no hay sesiones que lo muestren (el descanso). */
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
    val today = LocalDate.now()
    val day = selectedDate?.takeIf { it != today && weekDays.any { d -> d.date == it } } ?: today

    val pages = remember(
        day, today, sessions, weekDays, hasActiveProgram, homeCompetition, loadAdvisory, activeProgram,
        customExercises, isCatalogReady,
    ) {
        homeHeroPages(day, today, sessions, weekDays, hasActiveProgram, homeCompetition, loadAdvisory, activeProgram)
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
            onOpenProgram = { item?.let { onNavigateToProgram(it.program.id) } },
            onCreateProgram = onCreateProgram,
            onRegister = { homeCompetition?.let { onRegisterCompetition(it.recordId) } },
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
                    parallax = { (pagerState.currentPage + pagerState.currentPageOffsetFraction - index).coerceIn(-1f, 1f) },
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
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(durationMillis = 220)) togetherWith fadeOut(tween(durationMillis = 220))
                    }
                },
                label = "homeHeroDay",
            ) { page ->
                HomeSessionHero(state = page.state, actions = actionsFor(page))
            }
        }
        promptProgramId?.let { CompetitionDatePrompt(programId = it, onOpenProgram = onOpenProgramForCompetitionDate) }
        if (shown.showsMuscles) {
            Spacer(Modifier.height(HomeSectionGap))
            HomeMuscleGauges(
                muscles = shown.muscles.map { HomeMuscleGauge(it, lookupMuscleValue(perMuscle, it)?.recoveryScore) },
                onOpen = onOpenMuscleDetail,
            )
        }
    }
}

/** Tag de prueba de la acción «Añadir la fecha de tu competición» (C4c). */
internal const val COMPETITION_DATE_PROMPT_TAG = "home-competition-date-prompt"

/** La acción «Añadir la fecha de tu competición», como texto bajo el héroe (C4c). */
@Composable
private fun CompetitionDatePrompt(programId: String, onOpenProgram: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WizardSpacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag(COMPETITION_DATE_PROMPT_TAG)
                .clickable(role = Role.Button, onClick = { onOpenProgram(programId) })
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Añadir la fecha de tu competición",
                style = WizardTypography.cta.copy(textDecoration = TextDecoration.Underline),
                color = WizardColors.text,
            )
        }
    }
}

/** Una página del héroe: su estado, la sesión que muestra (null si no hay) y los músculos de sus cifras. */
private data class HomeHeroPage(
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
    loadAdvisory: LoadAdvisory?,
    activeProgram: Program?,
): List<HomeHeroPage> {
    val onToday = day == today
    val countdown = homeCompetition?.takeIf { it.phase == CompetitionHomePhase.UPCOMING }?.let(::heroCountdown)
    val programForArt = activeProgram ?: sessions.firstOrNull()?.program
    val fallbackArt = HomeHeroArt.Cover(homeHeroFallbackIdentity(), opacity = 0.35f)

    val competition = homeCompetition?.takeIf { onToday && it.phase == CompetitionHomePhase.REGISTER }
    if (competition != null) {
        return listOf(
            HomeHeroPage(
                state = HomeHeroState(
                    key = "$day/REGISTER",
                    mode = HomeHeroMode.REGISTER,
                    kicker = homeHeroKicker(HomeHeroMode.REGISTER, day, null, null),
                    title = "¿Cómo te fue en ${competition.title}?",
                    meta = "${competition.countdownLabel} · ${competition.competitionDateLabel}",
                    art = programForArt?.let { HomeHeroArt.Cover(coverIdentityFor(it.id, it.structureTemplateId)) }
                        ?: fallbackArt,
                ),
                item = null,
                muscles = emptyList(),
            ),
        )
    }

    if (!hasActiveProgram) {
        return listOf(
            HomeHeroPage(
                state = HomeHeroState(
                    key = "$day/NO_PROGRAM",
                    mode = HomeHeroMode.NO_PROGRAM,
                    kicker = homeHeroKicker(HomeHeroMode.NO_PROGRAM, day, null, null),
                    title = "Aún no tienes programa",
                    body = "Crea uno con KPKN o importa el tuyo.",
                    art = fallbackArt,
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
                state = HomeHeroState(
                    key = "$day/REST",
                    mode = HomeHeroMode.REST,
                    kicker = homeHeroKicker(HomeHeroMode.REST, day, null, null),
                    title = "Descanso",
                    meta = homeHeroNextLine(weekDays, day),
                    countdown = countdown,
                    art = programForArt
                        ?.let { HomeHeroArt.Cover(coverIdentityFor(it.id, it.structureTemplateId), opacity = 0.45f) }
                        ?: fallbackArt,
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
        sessionHeroPage(item, day, onToday, position, count, countdown, loadAdvisory)
    }
}

/** La página de una sesión: su modo (hoy, en curso, hecha u otro día), sus ejercicios, su meta y sus músculos. */
private fun sessionHeroPage(
    item: TodaySessionItem,
    day: LocalDate,
    onToday: Boolean,
    position: Int?,
    count: Int?,
    countdown: String?,
    loadAdvisory: LoadAdvisory?,
): HomeHeroPage {
    val session = item.session
    val mode = when {
        !onToday -> HomeHeroMode.OTHER_DAY
        item.isOngoing -> HomeHeroMode.ONGOING
        item.isCompleted -> HomeHeroMode.DONE
        else -> HomeHeroMode.TODAY
    }
    val exercises = session.allExercises()
    // El aviso de carga habla de la sesión que toca ahora, así que solo sale en las sesiones de hoy que no están hechas.
    val advisory = loadAdvisory?.takeIf {
        (mode == HomeHeroMode.TODAY || mode == HomeHeroMode.ONGOING) &&
            LoadAdvisoryEngine.rank(it.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.ADJUST)
    }
    val log = item.log
    val meta = if (mode == HomeHeroMode.DONE && log != null) doneMeta(log) else estimateMeta(session, exercises.size)
    val art = session.background?.let { HomeHeroArt.Own(it) }
        ?: HomeHeroArt.Cover(coverIdentityFor(item.program.id, item.program.structureTemplateId))
    return HomeHeroPage(
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
            advisoryBadge = advisory?.let { LoadAdvisoryEngine.badgeLabel(it, axialHintsForSession(session)) },
            countdown = countdown,
            art = art,
        ),
        item = item,
        muscles = getSessionInvolvedMuscles(session),
    )
}

/** La meta de una sesión hecha: duración real y series completadas, del registro. */
private fun doneMeta(log: WorkoutLog): String {
    val sets = log.completedExercises.sumOf { it.sets.size }
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

/** Minutos estimados de una sesión a partir de su tiempo calculado; null si no hay datos para estimarla. */
private fun estimatedSessionMinutes(session: Session): Int? {
    val breakdown = runCatching {
        calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
            sessionWarmup = session.warmup,
            globalMobilitySeries = session.parts
                .filter { it.isMobilityGroup }
                .flatMap { it.mobilitySeries },
        )
    }.getOrNull()
    val partFloorSeconds = session.parts.maxOfOrNull { part ->
        maxOf(
            (part.targetDurationMinutes ?: 0).coerceAtLeast(0),
            (part.mobilityConfig?.totalMinutes ?: 0).coerceAtLeast(0),
        ) * 60
    } ?: 0
    val estimatedSeconds = maxOf(breakdown?.totalSeconds ?: 0, partFloorSeconds)
    if (estimatedSeconds <= 0) return null
    return (estimatedSeconds / 60.0).toInt().coerceAtLeast(1)
}

/** La cuenta atrás de la competición para la etiqueta del héroe: «COMPETICIÓN EN 23 DÍAS» o «COMPETICIÓN MAÑANA». */
private fun heroCountdown(state: HomeCompetitionState): String =
    if (state.daysUntil == 1L) "COMPETICIÓN MAÑANA" else "COMPETICIÓN EN ${state.daysUntil} DÍAS"

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
                .map { involvement -> getAugeMuscleDisplayId(involvement.muscle, involvement.emphasis) }
                .filter { it.isNotBlank() }
                .forEach(muscles::add)
        } else if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            exercise.effectiveMuscles
                .asSequence()
                .map { involvement -> getAugeMuscleDisplayId(involvement.muscle, involvement.emphasis) }
                .filter { it.isNotBlank() }
                .forEach(muscles::add)
        }
    }

    session.exercises.forEach(::collectMuscles)
    session.parts.forEach { part -> part.exercises.forEach(::collectMuscles) }

    return muscles.toList()
}

internal fun axialHintsForSession(session: Session): List<AxialSessionExercise> =
    session.allExercises().mapNotNull { exercise ->
        val info = resolveCatalogExerciseInfo(
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId ?: exercise.id,
            exerciseName = exercise.name,
        ) ?: return@mapNotNull null
        AxialSessionExercise(
            name = exercise.name.ifBlank { info.name },
            axial = info.axialLoadFactor ?: 0.0,
            replacementGroup = info.replacementGroup,
        )
    }

internal fun axialCatalogAlternatives(
    hints: List<AxialSessionExercise>,
): List<AxialSessionExercise> {
    val groups = hints.mapNotNull { it.replacementGroup }.toSet()
    if (groups.isEmpty()) return emptyList()
    return exerciseCatalogSnapshot().mapNotNull { info ->
        val group = info.replacementGroup ?: return@mapNotNull null
        if (group !in groups) return@mapNotNull null
        AxialSessionExercise(
            name = info.name,
            axial = info.axialLoadFactor ?: 0.0,
            replacementGroup = group,
        )
    }
}
