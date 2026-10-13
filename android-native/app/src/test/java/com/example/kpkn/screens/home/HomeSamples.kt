package com.example.kpkn.screens.home

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.training.HomeDayKind
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.screens.home.design.HomeHeroArt
import com.example.kpkn.screens.home.design.HomeHeroLine
import com.example.kpkn.screens.home.design.HomeHeroMode
import com.example.kpkn.screens.home.design.HomeHeroState
import com.example.kpkn.screens.home.design.HomeMastheadState
import com.example.kpkn.screens.home.design.HomeMuscleGauge
import com.example.kpkn.screens.home.design.homeDateLine
import com.example.kpkn.screens.home.design.homeHeroFallbackIdentity
import com.example.kpkn.screens.home.design.homeHeroKicker
import com.example.kpkn.screens.home.design.homeHeroNextLine
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import java.time.LocalDate
import kotlin.math.sin

/** Un caso de Home para la hoja de render: lo que pinta la cabecera y «Tu semana». */
internal data class HomeSample(
    val masthead: HomeMastheadState,
    val days: List<HomeWeekDay>,
    val selected: LocalDate,
)

/** Datos de ejemplo de Home para las pruebas y la hoja de render. Fechas fijas: martes 13 de octubre de 2026. */
internal object HomeSamples {

    /** Martes 13 de octubre de 2026: el «hoy» de todos los ejemplos. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    fun session(id: String, name: String, dayOfWeek: Int, main: Boolean = false): Session =
        Session(id = id, name = name, dayOfWeek = dayOfWeek, isMainSession = main)

    /** Una sesión de la semana [weekId]; «hoy» se deduce de su día ISO frente a [today]. */
    fun item(
        program: Program,
        weekId: String,
        session: Session,
        today: LocalDate = this.today,
        done: Boolean = false,
        ongoing: Boolean = false,
    ): TodaySessionItem {
        val day = session.dayOfWeek ?: 1
        return TodaySessionItem(
            session = session,
            program = program,
            location = SessionLocation(macroIndex = 0, mesoIndex = 0, weekId = weekId),
            isCompleted = done,
            dayOfWeek = day,
            isOngoing = ongoing,
            isToday = day == today.dayOfWeek.value,
        )
    }

    /** Sesiones de cada semana del programa de 12: lunes (principal), martes y jueves. */
    private val weekSessions: List<Session> = listOf(
        session("torso-a", "Torso A", 1, main = true),
        session("pierna-a", "Pierna A", 2),
        session("torso-b", "Torso B", 4),
    )

    /** Programa de 12 semanas (la semana 3 es la actual) con el objetivo [goal] en su bloque. */
    fun twelveWeekProgram(
        name: String = "Camino al pico",
        goal: BlockGoal? = BlockGoal.ACCUMULATION,
    ): Program = Program(
        id = "p-12",
        name = name,
        structure = ProgramStructure.COMPLEX,
        startDay = 1,
        macrocycles = listOf(
            Macrocycle(
                id = "mac-1",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "blk-1",
                        name = "Bloque",
                        goal = goal,
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-1",
                                name = "Mesociclo",
                                weeks = (1..12).map { n ->
                                    ProgramWeek(id = "w$n", name = "Semana $n", sessions = weekSessions)
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** Sesiones de la semana 3: lunes hecho, martes (hoy) pendiente y jueves pendiente. */
    fun twelveWeekItems(
        program: Program = twelveWeekProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = weekSessions.map { s ->
        item(program, "w3", s, today = today, done = s.dayOfWeek == 1)
    }

    /** Ciclo simple de una sola semana, con su número de ciclo en 5. */
    fun simpleCyclicProgram(): Program = Program(
        id = "p-cyc",
        name = "Cuerpo completo",
        structure = ProgramStructure.SIMPLE,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        startDay = 1,
        runState = ProgramRunState(runId = "run-cyc", cycleNumber = 5),
        macrocycles = listOf(
            Macrocycle(
                id = "mac-c",
                name = "Ciclo",
                blocks = listOf(
                    Block(
                        id = "blk-c",
                        name = "Ciclo",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-c",
                                name = "Ciclo",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "cyc-w1",
                                        name = "Semana",
                                        sessions = listOf(session("cuerpo", "Cuerpo completo", 2)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** La única sesión del ciclo: martes (hoy), pendiente. */
    fun simpleCyclicItems(
        program: Program = simpleCyclicProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = listOf(item(program, "cyc-w1", session("cuerpo", "Cuerpo completo", 2), today = today))

    /** La muestra completa: cabecera (con o sin programa) y «Tu semana» si hay sesiones. */
    fun sample(
        program: Program?,
        items: List<TodaySessionItem>,
        name: String = "Alex",
        today: LocalDate = this.today,
    ): HomeSample {
        val days = if (program == null || items.isEmpty()) {
            emptyList()
        } else {
            HomeWeekResolver.week(items, program.startDay, today)
        }
        return HomeSample(
            masthead = HomeMastheadState(
                dateLine = homeDateLine(today),
                greeting = "Buenos días",
                name = name,
                initial = name.take(1).uppercase(),
                program = HomeWeekResolver.programLine(program, items, today),
            ),
            days = days,
            selected = today,
        )
    }

    /** Los cuatro casos de la hoja de render: programa de 12 semanas, ciclo simple, sin programa y nombre largo. */
    fun sheetCases(): List<Pair<String, HomeSample>> {
        val longProgram = twelveWeekProgram(name = "Powerlifting a medida con un nombre larguísimo que no cabe en una línea")
        return listOf(
            "prog12" to sample(twelveWeekProgram(), twelveWeekItems()),
            "cyclic" to sample(simpleCyclicProgram(), simpleCyclicItems()),
            "none" to sample(null, emptyList()),
            "long" to sample(longProgram, twelveWeekItems(longProgram), name = "Maximiliano Alejandro"),
        )
    }

    /** Nutrición con dos comidas registradas (desayuno y almuerzo): 1.240 de 2.300 kcal, con los macros a medio camino. */
    fun nutritionTwoMeals(): HomeNutritionState = HomeNutritionState(
        kcal = 1_240,
        kcalGoal = 2_300,
        protein = HomeMacroToday(grams = 92, goalGrams = 160),
        carbs = HomeMacroToday(grams = 130, goalGrams = 250),
        fat = HomeMacroToday(grams = 38, goalGrams = 70),
        mealKcal = mapOf(MealType.BREAKFAST to 420, MealType.LUNCH to 820),
    )

    /** Nutrición sin meta: lo comido, «sin objetivo» y los macros «sin meta». */
    fun nutritionNoGoal(): HomeNutritionState = HomeNutritionState(
        kcal = 980,
        kcalGoal = null,
        protein = HomeMacroToday(grams = 74),
        carbs = HomeMacroToday(grams = 96),
        fat = HomeMacroToday(grams = 33),
        mealKcal = mapOf(MealType.BREAKFAST to 380, MealType.LUNCH to 420, MealType.SNACK to 180),
    )

    /** Nutrición por encima de la meta: 2.450 kcal frente a 2.300, con 150 de más. */
    fun nutritionOverGoal(): HomeNutritionState = HomeNutritionState(
        kcal = 2_450,
        kcalGoal = 2_300,
        protein = HomeMacroToday(grams = 168, goalGrams = 160),
        carbs = HomeMacroToday(grams = 262, goalGrams = 250),
        fat = HomeMacroToday(grams = 74, goalGrams = 70),
        mealKcal = mapOf(
            MealType.BREAKFAST to 500,
            MealType.LUNCH to 900,
            MealType.SNACK to 250,
            MealType.DINNER to 800,
        ),
    )

    /** Días (hace n días) con pesaje en la tendencia de ejemplo: 20 días entre hace 30 días y hoy. */
    private val trendDaysAgo: List<Int> = listOf(30, 28, 27, 25, 23, 22, 20, 18, 17, 15, 13, 12, 10, 9, 7, 6, 4, 3, 1, 0)

    /** Tendencia de ejemplo: de 79,6 a 78,4 kg, con un leve vaivén en medio y los extremos exactos. */
    private fun bodyTrend(): List<HomeBodyPoint> = trendDaysAgo.map { ago ->
        val progress = ago / 30.0
        val wobble = 0.12 * sin(ago * 1.7) * (ago * (30 - ago)) / 225.0
        HomeBodyPoint(today.minusDays(ago.toLong()), 78.4 + 1.2 * progress + wobble)
    }

    /** Cuerpo con tendencia y objetivo de 72 kg: 78,4 kg, −1,2 kg en 30 días y, con altura real, IMC y FFMI. */
    fun bodyWithTrendAndGoal(): HomeBodyState {
        val trend = bodyTrend()
        return HomeBodyState(
            weightKg = trend.last().kg,
            weightChange30d = trend.last().kg - trend.first().kg,
            trend = trend,
            windowEnd = today,
            goalKg = 72.0,
            goalMonth = "abril",
            changeTowardGoal = true,
            bodyFatPct = 18.2,
            musclePct = 40.1,
            bmi = 24.2,
            ffmi = 19.8,
        )
    }

    /** Cuerpo sin pesajes: el estado vacío, con la invitación a registrar. */
    fun bodyNoWeights(): HomeBodyState = HomeBodyState()

    /** Cuerpo sin altura real: hay peso y tendencia, pero no IMC ni FFMI. */
    fun bodyWithoutHeight(): HomeBodyState = bodyWithTrendAndGoal().copy(bmi = null, ffmi = null)

    // ---- Héroe de la sesión del día (I2): la hoja de render y las pruebas. Martes 13 de octubre: día 2 de 4. ----

    /** Programa de fuerza con su plantilla: el arte de su portada sale de K3a. */
    fun heroProgram(): Program = Program(
        id = "gen-strength",
        name = "Fuerza y masa",
        structure = ProgramStructure.COMPLEX,
        structureTemplateId = "generated:strength-muscle",
    )

    /** El arte de la portada de [heroProgram], a la opacidad [opacity]. */
    private fun heroArt(opacity: Float = 1f): HomeHeroArt.Cover =
        HomeHeroArt.Cover(coverIdentityFor(heroProgram().id, heroProgram().structureTemplateId), opacity)

    /** Pierna A de hoy, día 2 de 4 de la semana: el caso TODAY. */
    fun heroToday(): HomeHeroState = HomeHeroState(
        key = "today",
        mode = HomeHeroMode.TODAY,
        kicker = homeHeroKicker(HomeHeroMode.TODAY, today, 2, 4),
        title = "Pierna A",
        lines = listOf(
            HomeHeroLine("Sentadilla trasera", "4 × 5–7 · 100 kg"),
            HomeHeroLine("Prensa", "3 × 10–12 · RIR 2"),
            HomeHeroLine("Curl femoral tumbado", "3 × 12 · RPE 8"),
        ),
        moreCount = 2,
        meta = "≈ 60 min · 5 ejercicios",
        art = heroArt(),
    )

    /** El mismo día con el aviso de carga y la cuenta atrás de la competición. */
    fun heroTodayWithAlerts(): HomeHeroState = heroToday().copy(
        key = "today-alerts",
        advisoryBadge = "Columna: hoy −10 % en Sentadilla trasera",
        countdown = "COMPETICIÓN EN 23 DÍAS",
    )

    /** Torso A ya hecha: 58 min y 22 series del registro. */
    fun heroDone(): HomeHeroState = HomeHeroState(
        key = "done",
        mode = HomeHeroMode.DONE,
        kicker = homeHeroKicker(HomeHeroMode.DONE, today, null, null),
        title = "Torso A",
        lines = listOf(
            HomeHeroLine("Press banca", "4 × 6–8 · RIR 2"),
            HomeHeroLine("Remo con barra", "4 × 8–10 · 70 kg"),
            HomeHeroLine("Elevaciones laterales", "3 × 12–15 · RPE 8"),
        ),
        moreCount = 1,
        meta = "58 min · 22 series",
        art = heroArt(),
    )

    /** Miércoles de descanso: arte al 45 % y «Siguiente: jueves · Torso B». */
    fun heroRest(): HomeHeroState = HomeHeroState(
        key = "rest",
        mode = HomeHeroMode.REST,
        kicker = homeHeroKicker(HomeHeroMode.REST, today.plusDays(1), null, null),
        title = "Descanso",
        meta = homeHeroNextLine(
            listOf(HomeWeekDay(today.plusDays(2), "J", false, HomeDayKind.PLANNED, "Torso B")),
            today.plusDays(1),
        ),
        art = heroArt(opacity = 0.45f),
    )

    /** El jueves 15, día 3 de 4, elegido en «Tu semana»: «Ver en el programa». */
    fun heroOtherDay(): HomeHeroState = HomeHeroState(
        key = "other",
        mode = HomeHeroMode.OTHER_DAY,
        kicker = homeHeroKicker(HomeHeroMode.OTHER_DAY, today.plusDays(2), 3, 4),
        title = "Torso B",
        lines = listOf(
            HomeHeroLine("Dominadas", "4 × 6–8"),
            HomeHeroLine("Remo en polea", "3 × 10 · RIR 1"),
        ),
        meta = "≈ 55 min · 4 ejercicios",
        art = heroArt(),
    )

    /** Sin programa: la identidad fija y tenue, al 35 %. */
    fun heroNoProgram(): HomeHeroState = HomeHeroState(
        key = "no-program",
        mode = HomeHeroMode.NO_PROGRAM,
        kicker = homeHeroKicker(HomeHeroMode.NO_PROGRAM, today, null, null),
        title = "Aún no tienes programa",
        body = "Crea uno con KPKN o importa el tuyo.",
        art = HomeHeroArt.Cover(homeHeroFallbackIdentity(), opacity = 0.35f),
    )

    /** Recuperación de la sesión: tres músculos con dato y uno sin dato (sale «—»). */
    fun heroMuscles(): List<HomeMuscleGauge> = listOf(
        HomeMuscleGauge("Isquios", 78),
        HomeMuscleGauge("Glúteos", 64),
        HomeMuscleGauge("Cuádriceps", 91),
        HomeMuscleGauge("Pantorrilla", null),
    )

    /** Los casos de la hoja del héroe, con el nombre de su PNG y sus músculos (vacíos si el modo no los muestra). */
    fun heroSheetCases(): List<Pair<String, HomeHeroSheetCase>> = listOf(
        "hoy_arte_k3a" to HomeHeroSheetCase(heroToday(), heroMuscles()),
        "hoy_aviso_cuenta_atras" to HomeHeroSheetCase(heroTodayWithAlerts(), heroMuscles()),
        "hecha" to HomeHeroSheetCase(heroDone(), heroMuscles()),
        "descanso" to HomeHeroSheetCase(heroRest(), emptyList()),
        "otro_dia" to HomeHeroSheetCase(heroOtherDay(), heroMuscles()),
        "sin_programa" to HomeHeroSheetCase(heroNoProgram(), emptyList()),
    )
}

/** Un caso de la hoja del héroe: el estado de la sesión del día y los músculos de sus cifras. */
internal data class HomeHeroSheetCase(
    val state: HomeHeroState,
    val muscles: List<HomeMuscleGauge>,
)
