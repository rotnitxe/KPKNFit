package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilityRules
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * Entreno v2 (Q) · «ningún control muerto»: cada dato que el asistente declara en el bloque Entreno cambia de verdad lo que se
 * activa. Cada caso parte de una persona base, cambia UN solo dato y recorre el asistente REAL de punta a punta (ViewModel,
 * generador de rutinas, semana armada y planificador reales; persistencia y coordinador de altas reales sobre Room en
 * memoria; solo el entorno de lectura de Ajustes es fijo). Del resultado lee el programa guardado en Room y los Ajustes que
 * dejó la activación, y exige que cambie el canal que el dato gobierna:
 *
 *  - el programa: ejercicios, series, días y semana, minutos, aproximación y movilidad, lugar de cada sesión, sesión principal,
 *    cargas y cardio;
 *  - lo que persiste la activación: material, tipo de atleta, bolsa de prioridades, marcas de powerlifting, calibración de
 *    volumen y unidad de peso.
 *
 * Un dato que no cambia ningún canal en ningún caso es un control muerto: el caso falla y el informe lo dice. La tabla «dato
 * declarado → canales que cambian» de cada caso queda en `build/reports/entreno-declaration-effect/table.txt` (el documento
 * `WIZARD_ENTRENO_V2.md` §3 la resume).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EntrenoDeclarationEffectTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var app: Application
    private var vmCounter = 0
    private val failures = ArrayList<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue("el catálogo real no carga en Robolectric: ${warm.exceptionOrNull()?.message}", warm.isSuccess)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        ProgramRepository.closeInstance()
        NutritionRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Lo declarado y lo que se lee de la activación
    // ═════════════════════════════════════════════════════════════════════════════════════════

    /** Todo lo que la persona declara en el bloque Entreno (los valores por defecto son la persona base). */
    private data class Declared(
        val experience: String = "intermediate",
        val places: Set<TrainingPlace> = setOf(TrainingPlace.GYM),
        /** null = lo habitual de los lugares elegidos (se acepta la sugerencia). */
        val material: Set<EquipmentSymbolId>? = null,
        val profile: TrainingGoalProfile = TrainingGoalProfile.STRENGTH_MUSCLE,
        val freshDay: Int = 4,
        val weekdays: Set<Int> = setOf(1, 2, 4, 5),
        /** null = la semana empieza el día con más energía. */
        val weekStart: Int? = null,
        val dayPlaces: Map<Int, TrainingPlace> = emptyMap(),
        val minutes: Int = 60,
        val cardioType: String = "WALK",
        val cardioMinutes: String = "20",
        /** «Tengo bicicleta»: la bicicleta al aire libre de la persona (viaja a todos los lugares). */
        val bike: Boolean = false,
        val technique: String = "2",
        val consistency: String = "2",
        val strength: String = "2",
        val mobility: String = "2",
        val capabilities: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
        val muscles: Set<MuscleSymbol> = emptySet(),
        val marks: Map<LiftMark, Double> = emptyMap(),
        val marksUnit: String = "kg",
        /** Cuántas veces se pide «Otra versión». */
        val versions: Int = 0,
        /** Sesiones movidas en la semana armada: día de origen → día de destino. */
        val moves: List<Pair<Int, Int>> = emptyList(),
        /** Posición, en los repartos que ofrece la semana armada, del reparto al que se adapta el programa. */
        val splitIndex: Int? = null,
        /** El plan del catálogo que se elige en PLAN en lugar del programa «a medida» (null = el «a medida» del perfil). */
        val planId: String? = null,
    )

    /** Qué canales de la activación se comparan. */
    private enum class Channel {
        EXERCISES, SETS, DAYS, WEEK_START, MINUTES, APPROACH, MOBILITY, PLACES, MAIN_DAY, LOADS, CARDIO, SESSION_NAMES,
        PROGRAM_NAME, ORDER_PRIORITIES, POWERLIFTING_PROFILE, ATHLETE_TYPE, EQUIPMENT_AVAILABILITY, VOLUME_PROFILE, WEIGHT_UNIT,
    }

    /** Lo que quedó tras activar: el programa guardado en Room y los Ajustes, más el que se previsualizó. */
    private class Activated(
        val declared: Declared,
        val program: Program,
        val previewed: Program,
        val settings: Settings,
        val activeProgramId: String?,
        val commitId: String,
    ) {
        val sessions: List<Session> = firstWeek(program).sortedBy { it.dayOfWeek ?: 0 }

        fun read(channel: Channel): Any? = when (channel) {
            Channel.EXERCISES -> sessions.map { it.dayOfWeek to it.allExercises().map { e -> e.catalogConfigurationId ?: "cardio:${e.cardioDetails?.type}" } }
            Channel.SETS -> sessions.map { it.dayOfWeek to it.allExercises().map { e -> e.sets.size } }
            Channel.DAYS -> program.resolvedSchedulePlan().trainingDays
            Channel.WEEK_START -> program.resolvedSchedulePlan().weekStartDay to program.startDay
            Channel.MINUTES -> sessions.map { SessionDurationEstimator.estimate(it).totalMinutes }
            Channel.APPROACH -> sessions.map { s -> s.allExercises().map { it.warmupSets.size } }
            Channel.MOBILITY -> sessions.map { s -> s.allExercises().map { it.mobilitySeries.size } }
            Channel.PLACES -> sessions.map { it.dayOfWeek to it.placeId }
            Channel.MAIN_DAY -> sessions.firstOrNull { it.isMainSession }?.dayOfWeek
            Channel.LOADS -> sessions.map { s -> s.allExercises().map { e -> e.reference1RM to e.sets.map { it.targetPercentageRM } } }
            Channel.CARDIO -> sessions.flatMap { s -> s.allExercises().mapNotNull { it.cardioDetails }.map { it.type to it.targetDurationSeconds } }
            Channel.SESSION_NAMES -> sessions.map { it.dayOfWeek to it.name }
            Channel.PROGRAM_NAME -> program.name
            Channel.ORDER_PRIORITIES -> program.planOrderPriorities
            Channel.POWERLIFTING_PROFILE -> program.powerliftingProfile
            Channel.ATHLETE_TYPE -> settings.athleteType
            Channel.EQUIPMENT_AVAILABILITY -> settings.equipmentAvailability
            Channel.VOLUME_PROFILE -> settings.volumeCalibrationProfile?.let { it.responses to it.recommendations }
            Channel.WEIGHT_UNIT -> settings.weightUnit
        }

        /** Los minutos de la sesión más larga (lo que mide la revisión final). */
        val longestMinutes: Int get() = sessions.maxOf { SessionDurationEstimator.estimate(it).totalMinutes }

        fun cardioSeconds(): List<Int> = sessions.flatMap { s -> s.allExercises().mapNotNull { it.cardioDetails?.targetDurationSeconds } }
    }

    private companion object {
        /** El resultado de cada persona ya recorrida: una persona se activa una sola vez por ejecución. */
        val cache = HashMap<Declared, Activated>()

        /** Filas de la tabla «dato → canales» de todos los casos. */
        val table = ArrayList<String>()

        fun firstWeek(program: Program): List<Session> =
            program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.first().sessions
    }

    private val configurations: Map<String, ExerciseConfigurationV2> by lazy {
        runBlocking { CatalogV2ProcessCache.getOrLoad(app) }.catalog.families
            .flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
    }

    /** Los `equipmentId` de los ejercicios del programa (sin el cardio, que no tiene configuración). */
    private fun equipmentOf(activated: Activated): Set<String> = activated.sessions.flatMap { it.allExercises() }
        .mapNotNull { it.catalogConfigurationId }.mapNotNull { configurations[it]?.profile?.equipmentId }.toSet()

    private fun configurationsOf(activated: Activated): List<String> = activated.sessions.flatMap { it.allExercises() }
        .mapNotNull { it.catalogConfigurationId }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Casos: un dato por vez
    // ═════════════════════════════════════════════════════════════════════════════════════════

    /** La persona base: gimnasio, Fuerza y masa muscular, 4 días, 60 min, con las tres marcas del powerlifting. */
    private val base = Declared(marks = mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 90.0, LiftMark.DEADLIFT to 150.0))

    private val noMarks = base.copy(marks = emptyMap())

    @Test
    fun theBasePersonIsActivatedWithEverythingTheyDeclaredAndTheProgramIsDeterministic() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val first = activated(base)
        // Dos activaciones de la misma persona (con ids distintos) dan el mismo programa: ningún id entra en las elecciones.
        val again = activate(base)
        Channel.entries.forEach { channel ->
            assertEquals("$channel cambia entre dos activaciones idénticas", first.read(channel), again.read(channel))
        }
        // Lo previsualizado es lo activado.
        assertEquals(shapeOf(first.previewed), shapeOf(first.program))
        // Lo que persiste la activación.
        assertInvariants(first)
        assertEquals(AthleteType.ENTHUSIAST, first.settings.athleteType)
        assertEquals(
            EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)),
            EquipmentSymbols.selectedFrom(first.settings.equipmentAvailability),
        )
        assertEquals(120.0, first.program.powerliftingProfile?.squat1RM ?: Double.NaN, 0.001)
        assertEquals(90.0, first.program.powerliftingProfile?.bench1RM ?: Double.NaN, 0.001)
        assertEquals(150.0, first.program.powerliftingProfile?.deadlift1RM ?: Double.NaN, 0.001)
        assertEquals(setOf(1, 2, 4, 5), first.program.resolvedSchedulePlan().trainingDays)
        assertEquals(4, first.sessions.single { it.isMainSession }.dayOfWeek)
    }

    @Test
    fun placesAndDayPlacesDecideTheMaterialAndThePlaceOfEverySession() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val home = base.copy(places = setOf(TrainingPlace.HOME))
        change("trainingPlaces", "gimnasio → casa", base, home, Channel.EQUIPMENT_AVAILABILITY, Channel.PLACES, Channel.EXERCISES) { b, v ->
            when {
                v.sessions.any { it.placeId != TrainingPlace.HOME.name } -> "las sesiones no están en casa: ${v.read(Channel.PLACES)}"
                !equipmentOf(v).all { it == "bodyweight" } -> "en casa sin material solo cabe el cuerpo, y hay ${equipmentOf(v)}"
                b.sessions.any { it.placeId != TrainingPlace.GYM.name } -> "la base no está en el gimnasio"
                else -> null
            }
        }
        // Un lugar nuevo trae lo suyo (el parque, su barra de dominadas y sus paralelas) aunque todavía no se le asigne ningún día.
        change("trainingPlaces", "casa → casa y espacios públicos (sin lugar por día)", home, home.copy(places = setOf(TrainingPlace.HOME, TrainingPlace.PUBLIC)), Channel.EQUIPMENT_AVAILABILITY) { _, v ->
            val persisted = EquipmentSymbols.selectedFrom(v.settings.equipmentAvailability)
            if (EquipmentSymbolId.PULL_UP_BAR in persisted && EquipmentSymbolId.PARALLEL_BARS in persisted) null
            else "el parque no trajo su estructura a Ajustes: $persisted"
        }

        val gymAndHome = base.copy(places = setOf(TrainingPlace.GYM, TrainingPlace.HOME))
        val homeOnTwoDays = gymAndHome.copy(dayPlaces = mapOf(2 to TrainingPlace.HOME, 5 to TrainingPlace.HOME))
        change("dayPlaces", "todos los días en el gimnasio → martes y viernes en casa", gymAndHome, homeOnTwoDays, Channel.PLACES, Channel.EXERCISES) { _, v ->
            val byDay = v.sessions.associate { it.dayOfWeek to it.placeId }
            when {
                byDay[2] != TrainingPlace.HOME.name || byDay[5] != TrainingPlace.HOME.name -> "martes y viernes no están en casa: $byDay"
                byDay[1] != TrainingPlace.GYM.name || byDay[4] != TrainingPlace.GYM.name -> "lunes y jueves no están en el gimnasio: $byDay"
                else -> {
                    val atHome = v.sessions.filter { it.placeId == TrainingPlace.HOME.name }.flatMap { it.allExercises() }
                        .mapNotNull { it.catalogConfigurationId }.mapNotNull { configurations[it]?.profile?.equipmentId }.toSet()
                    if (atHome.all { it == "bodyweight" }) null else "las sesiones de casa piden $atHome"
                }
            }
        }
        // El lugar de un día cambia la sesión de ESE día y no la de los demás.
        val onlyFriday = gymAndHome.copy(dayPlaces = mapOf(5 to TrainingPlace.PUBLIC))
        change("dayPlaces", "viernes en espacios públicos", gymAndHome.copy(places = gymAndHome.places + TrainingPlace.PUBLIC), onlyFriday.copy(places = gymAndHome.places + TrainingPlace.PUBLIC), Channel.PLACES, Channel.EXERCISES)
        finish("lugares")
    }

    @Test
    fun everyMaterialSymbolReachesTheActivatedMaterialAndTheProgramWhereTheGeneratorUsesIt() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val home = base.copy(places = setOf(TrainingPlace.HOME), material = emptySet(), marks = emptyMap())
        val offered = EquipmentSymbols.symbolsFor(setOf(TrainingPlace.HOME)).filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }
        val withoutProgramEffect = ArrayList<EquipmentSymbolId>()
        offered.forEach { symbol ->
            val variant = home.copy(material = setOf(symbol))
            val outcome = change("material:${symbol.name}", "casa sin material → casa con «${symbol.label}»", home, variant, Channel.EQUIPMENT_AVAILABILITY) { _, v ->
                val persisted = EquipmentSymbols.selectedFrom(v.settings.equipmentAvailability)
                if (symbol in persisted) null else "Ajustes no guarda «${symbol.label}»: $persisted"
            }
            if (outcome.none { it == Channel.EXERCISES || it == Channel.SETS || it == Channel.MINUTES || it == Channel.CARDIO }) withoutProgramEffect += symbol
        }
        // Quitar un implemento del gimnasio retira de verdad sus ejercicios.
        val gymEquipment = mapOf(
            EquipmentSymbolId.BARBELL to "barbell", EquipmentSymbolId.DUMBBELLS to "dumbbells", EquipmentSymbolId.KETTLEBELL to "kettlebell",
            EquipmentSymbolId.CABLE to "cable", EquipmentSymbolId.MACHINES to "machine", EquipmentSymbolId.SMITH to "smith_machine",
            EquipmentSymbolId.BANDS to "band",
        )
        val gymSeed = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))
        gymEquipment.forEach { (symbol, equipmentId) ->
            val without = noMarks.copy(material = gymSeed - symbol)
            val baseUses = equipmentId in equipmentOf(activated(noMarks))
            change("material:${symbol.name}", "gimnasio completo → sin «${symbol.label}»", noMarks, without, Channel.EQUIPMENT_AVAILABILITY) { _, v ->
                if (equipmentId in equipmentOf(v)) "sin «${symbol.label}» el programa sigue pidiendo $equipmentId" else null
            }
            table += "material:${symbol.name} · la base ${if (baseUses) "usa" else "no usa"} el implemento $equipmentId"
        }
        table += "material sin efecto en el programa de casa (STRENGTH_MUSCLE, 4 días, 60 min): ${withoutProgramEffect.joinToString { it.name }.ifEmpty { "ninguno" }}"
        // Lo que no se nota en un programa de fuerza (el cardio de gimnasio) se nota en los que llevan cardio.
        withoutProgramEffect.toList().forEach { symbol ->
            val probes = listOf(TrainingGoalProfile.STRENGTH_CARDIO, TrainingGoalProfile.FUNCTIONAL_HEALTH)
            val effects = probes.map { profile ->
                val without = home.copy(profile = profile)
                // «Cardio» no cambia el programa por sí solo: abre las máquinas del paso CARDIO_TYPE. Quien lo marca puede elegir la
                // cinta y el generador la prescribe; quien no lo marca no puede y camina.
                val opensMachines = symbol == EquipmentSymbolId.CARDIO
                val with = without.copy(material = setOf(symbol), cardioType = if (opensMachines) "TREADMILL" else without.cardioType)
                change("material:${symbol.name}", "casa sin material → casa con «${symbol.label}» (${profile.name})", without, with, Channel.EQUIPMENT_AVAILABILITY) { _, v ->
                    val types = cardioTypesOf(v)
                    if (!opensMachines || types == setOf(CardioType.TREADMILL)) null
                    else "con «Cardio» y la cinta elegida el cardio es $types"
                }
            }
            if (effects.any { changed -> changed.any { it == Channel.EXERCISES || it == Channel.SETS || it == Channel.MINUTES || it == Channel.CARDIO } }) {
                withoutProgramEffect -= symbol
            }
        }
        table += "material sin efecto en ningún programa probado: ${withoutProgramEffect.joinToString { it.name }.ifEmpty { "ninguno" }}"
        // Todo símbolo del paso de material tiene un consumidor: la cuerda de saltar salió de la cuadrícula (ningún ejercicio la usa) y
        // «Cardio» abre las máquinas del paso CARDIO_TYPE (la cinta elegida se prescribe en los días de gimnasio).
        if (withoutProgramEffect.isNotEmpty()) {
            failures += "material: los símbolos que no cambian ningún programa son ${withoutProgramEffect.joinToString { it.name }}: " +
                "dales consumo o retíralos de la cuadrícula, y actualiza WIZARD_ENTRENO_V2.md §3"
        }
        finish("material")
    }

    @Test
    fun everyGoalProfileGivesItsOwnProgramAndWritesItsAthleteType() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val shapes = HashMap<TrainingGoalProfile, Any?>()
        TrainingGoalProfile.entries.forEach { profile ->
            val variant = noMarks.copy(profile = profile)
            val result = activated(variant)
            val expectedAthlete = GoalProfileMapping.athleteTypeOf(profile)
            if (result.settings.athleteType != expectedAthlete) failures += "goalProfile ${profile.name}: tipo de atleta ${result.settings.athleteType}, esperaba $expectedAthlete"
            if (result.program.name.isBlank()) failures += "goalProfile ${profile.name}: programa sin nombre"
            shapes[profile] = result.read(Channel.EXERCISES) to result.read(Channel.SETS)
            table += "goalProfile ${profile.name}: programa «${result.program.name}», tipo de atleta ${result.settings.athleteType}, " +
                "${result.sessions.sumOf { it.allExercises().size }} ejercicios en ${result.sessions.size} sesiones"
        }
        val profiles = TrainingGoalProfile.entries
        // «Fuerza y masa muscular» y «Powerbuilding» arman el MISMO reparto con las mismas reservas (`CUSTOM_POWERBUILDING` no añade
        // nada a `GENERAL_STRENGTH_MUSCLE`): se distinguen por el nombre, el tipo de atleta y los planes del catálogo que PLAN ofrece.
        val sameByDesign = setOf(TrainingGoalProfile.STRENGTH_MUSCLE, TrainingGoalProfile.POWERBUILDING)
        for (i in profiles.indices) for (j in i + 1 until profiles.size) {
            val pair = setOf(profiles[i], profiles[j])
            val same = shapes[profiles[i]] == shapes[profiles[j]]
            when {
                same && pair == sameByDesign -> table += "goalProfile: ${profiles[i].name} y ${profiles[j].name} dan el mismo reparto y los mismos ejercicios (equivalentes por diseño)"
                same -> failures += "goalProfile: ${profiles[i].name} y ${profiles[j].name} dan el mismo programa (ejercicios y series idénticos)"
            }
        }
        finish("objetivos")
    }

    @Test
    fun experienceAndTheFourCalibrationsChangeTheProgramOrWhatActivationStores() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        listOf("new", "returning", "advanced").forEach { level ->
            change("experience", "intermedio → $level", noMarks, noMarks.copy(experience = level), Channel.EXERCISES)
        }
        // Las cuatro calibraciones se guardan en Ajustes (volumen de AUGE) y mueven el volumen del programa.
        val calibrations = listOf(
            "technique" to { d: Declared, v: String -> d.copy(technique = v) },
            "consistency" to { d: Declared, v: String -> d.copy(consistency = v) },
            "strength" to { d: Declared, v: String -> d.copy(strength = v) },
            "mobility" to { d: Declared, v: String -> d.copy(mobility = v) },
        )
        calibrations.forEach { (name, set) ->
            listOf("1", "3").forEach { answer ->
                change(name, "2 → $answer", noMarks, set(noMarks, answer), Channel.VOLUME_PROFILE)
            }
        }
        finish("experiencia y calibraciones")
    }

    @Test
    fun theWeekTheEnergyDayAndTheTimeShapeTheProgram() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        // Día con más energía: ahí cae la sesión principal y de ahí empieza la semana.
        listOf(1, 5).forEach { fresh ->
            change("freshestDay", "jueves → ${dayName(fresh)}", noMarks, noMarks.copy(freshDay = fresh), Channel.MAIN_DAY, Channel.WEEK_START) { _, v ->
                val main = v.sessions.single { it.isMainSession }.dayOfWeek
                if (main == fresh) null else "la sesión principal cae el día $main y la energía es el $fresh"
            }
        }
        // Un día con más energía en que no se entrena: la principal pasa al primer día de entreno posterior y la semana empieza ahí.
        change("freshestDay", "jueves → miércoles (día sin entreno)", noMarks, noMarks.copy(freshDay = 3), Channel.WEEK_START) { _, v ->
            val main = v.sessions.single { it.isMainSession }.dayOfWeek
            if (main == 4) null else "la sesión principal cae el día $main y se esperaba el jueves"
        }
        // Días de entreno.
        listOf(setOf(2, 4, 6), setOf(1, 2, 3, 4, 5, 6, 7), setOf(3)).forEach { days ->
            change("selectedWeekdays", "lun, mar, jue, vie → ${days.joinToString(",")}", noMarks, noMarks.copy(weekdays = days, freshDay = days.first()), Channel.DAYS) { _, v ->
                val trained = v.sessions.mapNotNull { it.dayOfWeek }.toSet()
                if (trained == days && v.sessions.size == days.size) null else "se entrena $trained y se pidió $days"
            }
        }
        // Inicio de semana.
        change("weekStartDay", "empieza el jueves (el día fuerte) → empieza el lunes", noMarks, noMarks.copy(weekStart = 1), Channel.WEEK_START) { _, v ->
            if (v.program.startDay == 1 && v.program.resolvedSchedulePlan().weekStartDay == 1) null
            else "la semana empieza ${v.program.startDay}/${v.program.resolvedSchedulePlan().weekStartDay} y se pidió el lunes"
        }
        // Minutos por sesión.
        val longest = HashMap<Int, Int>()
        listOf(30, 60, 90, 150).forEach { minutes ->
            val variant = noMarks.copy(minutes = minutes)
            if (minutes != 60) change("minutesPerSession", "60 → $minutes min", noMarks, variant, Channel.MINUTES)
            val result = activated(variant)
            longest[minutes] = result.longestMinutes
            if (result.longestMinutes > minutes + GENERATED_TIME_TOLERANCE_MINUTES) {
                failures += "minutesPerSession $minutes: la sesión más larga mide ${result.longestMinutes} min (tolerancia +$GENERATED_TIME_TOLERANCE_MINUTES)"
            }
        }
        table += "minutesPerSession: sesión más larga por minutos pedidos → $longest"
        if (!(longest.getValue(30) < longest.getValue(60) && longest.getValue(60) < longest.getValue(90) && longest.getValue(90) < longest.getValue(150))) {
            failures += "minutesPerSession: pedir más tiempo no alarga la sesión más larga: $longest"
        }
        finish("semana y tiempo")
    }

    @Test
    fun cardioTypeAndMinutesReachTheCardioBlocksOfTheProgram() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val hybrid = noMarks.copy(profile = TrainingGoalProfile.STRENGTH_CARDIO)
        val hybridActivated = activated(hybrid)
        if (hybridActivated.cardioSeconds().isEmpty()) failures += "cardio: Fuerza y cardio no lleva ningún bloque de cardio"
        change("cardioType", "caminar → correr al aire libre", hybrid, hybrid.copy(cardioType = "RUN_OUTDOOR"), Channel.CARDIO) { _, v ->
            val types = v.sessions.flatMap { s -> s.allExercises().mapNotNull { it.cardioDetails?.type } }.toSet()
            if (types == setOf(com.example.kpkn.data.models.CardioType.RUN_OUTDOOR)) null else "el cardio es $types y se pidió correr"
        }
        change("cardioMinutes", "20 → 30 min", hybrid, hybrid.copy(cardioMinutes = "30"), Channel.CARDIO)
        change("cardioMinutes", "20 → 10 min", hybrid, hybrid.copy(cardioMinutes = "10"), Channel.CARDIO)
        // Los minutos declarados son los del bloque de cardio que cierra cada sesión de fuerza (las sesiones de solo cardio llenan el
        // tiempo de la sesión): sumando todo el cardio de la semana, pedir más minutos da más cardio.
        val weekly = listOf("10", "20", "30").associateWith { minutes -> activated(hybrid.copy(cardioMinutes = minutes)).cardioSeconds().sum() }
        table += "cardioMinutes: segundos de cardio por semana según los minutos pedidos → $weekly"
        if (!(weekly.getValue("10") < weekly.getValue("20") && weekly.getValue("20") < weekly.getValue("30"))) {
            failures += "cardioMinutes: pedir más minutos de cardio no da más cardio a la semana: $weekly"
        }
        finish("cardio")
    }

    private val machineTypes = setOf(CardioType.TREADMILL, CardioType.BIKE_STATIONARY, CardioType.ELLIPTICAL, CardioType.ROW_MACHINE)

    /** Los tipos de cardio de todo el programa activado. */
    private fun cardioTypesOf(activated: Activated): Set<CardioType> =
        activated.sessions.flatMap { s -> s.allExercises().mapNotNull { it.cardioDetails?.type } }.toSet()

    /** Los tipos de cardio de las sesiones de cada lugar (`Session.placeId`). */
    private fun cardioTypesByPlace(activated: Activated): Map<String?, Set<CardioType>> =
        activated.sessions.groupBy({ it.placeId }, { s -> s.allExercises().mapNotNull { it.cardioDetails?.type } })
            .mapValues { (_, types) -> types.flatten().toSet() }

    @Test
    fun theCardioMachinesOfTheStepReachTheGymDaysAndTheParkDaysGetAnotherType() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val hybridGym = noMarks.copy(profile = TrainingGoalProfile.STRENGTH_CARDIO)
        listOf("TREADMILL" to CardioType.TREADMILL, "BIKE_STATIONARY" to CardioType.BIKE_STATIONARY).forEach { (value, type) ->
            change("cardioType", "caminar → $value (gimnasio, símbolo «Cardio»)", hybridGym, hybridGym.copy(cardioType = value), Channel.CARDIO) { _, v ->
                val types = cardioTypesOf(v)
                if (types == setOf(type)) null else "el cardio es $types y se pidió $type"
            }
        }
        // «Lo que haya»: sin preferencia, el generador elige entre las máquinas del día.
        change("cardioType", "caminar → lo que haya (gimnasio)", hybridGym, hybridGym.copy(cardioType = "ANY"), Channel.CARDIO) { _, v ->
            val types = cardioTypesOf(v)
            if (types.isNotEmpty() && types.all { it in machineTypes }) null else "el cardio es $types y «Lo que haya» elige entre las máquinas del gimnasio"
        }
        // Gimnasio y espacios públicos, cada día en su lugar: la cinta en los días de gimnasio y caminar o correr en los del parque.
        val gymAndPark = hybridGym.copy(
            places = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC),
            dayPlaces = mapOf(1 to TrainingPlace.GYM, 2 to TrainingPlace.PUBLIC, 4 to TrainingPlace.GYM, 5 to TrainingPlace.PUBLIC),
            cardioType = "TREADMILL",
        )
        val byPlace = cardioTypesByPlace(activated(gymAndPark))
        val atGym = byPlace[TrainingPlace.GYM.name].orEmpty()
        val atPark = byPlace[TrainingPlace.PUBLIC.name].orEmpty()
        table += "cardioType · gimnasio y espacios públicos con la cinta → gimnasio: $atGym · parque: $atPark"
        if (atGym != setOf(CardioType.TREADMILL)) failures += "cardioType: los días de gimnasio llevan $atGym y se pidió la cinta"
        if (atPark.isEmpty() || atPark.any { it in machineTypes }) failures += "cardioType: los días de parque llevan $atPark y ahí no hay máquinas"
        // «Lo que haya» con los mismos lugares: máquinas en el gimnasio y nada de máquinas en el parque.
        val anyByPlace = cardioTypesByPlace(activated(gymAndPark.copy(cardioType = "ANY")))
        table += "cardioType · gimnasio y espacios públicos con lo que haya → gimnasio: ${anyByPlace[TrainingPlace.GYM.name]} · parque: ${anyByPlace[TrainingPlace.PUBLIC.name]}"
        if (anyByPlace[TrainingPlace.GYM.name].orEmpty().let { it.isEmpty() || !it.all { type -> type in machineTypes } }) {
            failures += "cardioType: «Lo que haya» en el gimnasio no elige máquinas: ${anyByPlace[TrainingPlace.GYM.name]}"
        }
        if (anyByPlace[TrainingPlace.PUBLIC.name].orEmpty().any { it in machineTypes }) {
            failures += "cardioType: «Lo que haya» en el parque elige máquinas: ${anyByPlace[TrainingPlace.PUBLIC.name]}"
        }
        finish("cardio por material")
    }

    @Test
    fun theBikeTheyDeclaredReachesTheCardioOfEveryDayInEveryPlace() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val hybridHome = noMarks.copy(profile = TrainingGoalProfile.STRENGTH_CARDIO, places = setOf(TrainingPlace.HOME), material = emptySet())
        change("outdoor_bike", "casa → casa con bicicleta (bicicleta al aire libre)", hybridHome, hybridHome.copy(bike = true, cardioType = "BIKE_OUTDOOR"), Channel.CARDIO, Channel.EQUIPMENT_AVAILABILITY) { _, v ->
            val types = cardioTypesOf(v)
            if (types == setOf(CardioType.BIKE_OUTDOOR)) null else "el cardio es $types y se pidió la bicicleta al aire libre"
        }
        // La bicicleta es de la persona, no de un lugar: con gimnasio y casa la usan todos los días.
        val gymAndHome = noMarks.copy(
            profile = TrainingGoalProfile.STRENGTH_CARDIO,
            places = setOf(TrainingPlace.GYM, TrainingPlace.HOME),
            dayPlaces = mapOf(2 to TrainingPlace.HOME, 5 to TrainingPlace.HOME),
            bike = true,
            cardioType = "BIKE_OUTDOOR",
        )
        val byPlace = cardioTypesByPlace(activated(gymAndHome))
        table += "outdoor_bike · gimnasio y casa con bicicleta → gimnasio: ${byPlace[TrainingPlace.GYM.name]} · casa: ${byPlace[TrainingPlace.HOME.name]}"
        listOf(TrainingPlace.GYM, TrainingPlace.HOME).forEach { place ->
            if (byPlace[place.name] != setOf(CardioType.BIKE_OUTDOOR)) failures += "outdoor_bike: los días de ${place.name} llevan ${byPlace[place.name]} y la bicicleta viaja con la persona"
        }
        finish("bicicleta")
    }

    @Test
    fun capabilitiesChooseTheRungOfTheBodyweightLadders() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val park = Declared(
            places = setOf(TrainingPlace.PUBLIC),
            profile = TrainingGoalProfile.CALISTHENICS,
            experience = "intermediate",
            weekdays = setOf(1, 3, 5),
            freshDay = 1,
            minutes = 45,
        )
        val skills = CapabilityRules.skillsFor(EquipmentSymbols.seedFor(setOf(TrainingPlace.PUBLIC)))
        assertTrue("el parque pregunta dominadas, flexiones, fondos y sentadilla a una pierna: $skills", skills.size == 4)
        val allSome = skills.associateWith { CapabilityLevel.SOME }
        val none = park.copy(capabilities = skills.associateWith { CapabilityLevel.NONE })
        val many = park.copy(capabilities = skills.associateWith { CapabilityLevel.MANY })
        change("capabilities", "ninguna → todas «varias»", none, many, Channel.EXERCISES)
        skills.forEach { skill ->
            val low = park.copy(capabilities = allSome + (skill to CapabilityLevel.NONE))
            val high = park.copy(capabilities = allSome + (skill to CapabilityLevel.MANY))
            change("capabilities:${skill.name}", "«aún no» → «varias»", low, high, Channel.EXERCISES)
        }
        finish("capacidades")
    }

    @Test
    fun priorityMusclesReorderAndAddVolumeAndLandInTheOrderBag() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        val chestAndBack = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)
        change("priorityMuscles", "sin preferencia → pecho y espalda", noMarks, noMarks.copy(muscles = chestAndBack), Channel.ORDER_PRIORITIES, Channel.EXERCISES) { _, v ->
            if (v.program.planOrderPriorities == MuscleSymbols.orderBagOf(chestAndBack)) null
            else "la bolsa de prioridades guardada es ${v.program.planOrderPriorities}"
        }
        change("priorityMuscles", "pecho y espalda → piernas", noMarks.copy(muscles = chestAndBack), noMarks.copy(muscles = setOf(MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS, MuscleSymbol.GLUTES)), Channel.ORDER_PRIORITIES, Channel.EXERCISES)
        finish("prioridades")
    }

    @Test
    fun liftMarksSetTheLoadsAndFeedThePowerliftingProfile() = runTest(dispatcher.scheduler, timeout = 10.minutes) {
        change("liftMarks", "sin marcas → sentadilla 120 kg", noMarks, noMarks.copy(marks = mapOf(LiftMark.SQUAT to 120.0)), Channel.LOADS, Channel.POWERLIFTING_PROFILE) { _, v ->
            if (v.program.powerliftingProfile?.squat1RM == 120.0) null else "powerliftingProfile=${v.program.powerliftingProfile}"
        }
        change("liftMarks", "sentadilla 120 → 150 kg", noMarks.copy(marks = mapOf(LiftMark.SQUAT to 120.0)), noMarks.copy(marks = mapOf(LiftMark.SQUAT to 150.0)), Channel.LOADS, Channel.POWERLIFTING_PROFILE)
        change("liftMarks", "solo sentadilla → las tres", noMarks.copy(marks = mapOf(LiftMark.SQUAT to 120.0)), base, Channel.POWERLIFTING_PROFILE)
        val weightlifting = noMarks.copy(profile = TrainingGoalProfile.WEIGHTLIFTING, weekdays = setOf(1, 2, 4, 5, 6), freshDay = 1, minutes = 90)
        change("liftMarks:SNATCH", "Halterofilia sin marcas → arranque 60 kg", weightlifting, weightlifting.copy(marks = mapOf(LiftMark.SNATCH to 60.0)), Channel.LOADS)
        change("liftMarks:CLEAN_AND_JERK", "Halterofilia sin marcas → dos tiempos 80 kg", weightlifting, weightlifting.copy(marks = mapOf(LiftMark.CLEAN_AND_JERK to 80.0)), Channel.LOADS)
        // La unidad solo cambia cómo se escribe la marca: el valor canónico sigue en kg.
        val inPounds = activated(base.copy(marksUnit = "lb"))
        val inKilos = activated(base)
        assertEquals("la unidad de las marcas no cambia la marca guardada", inKilos.program.powerliftingProfile, inPounds.program.powerliftingProfile)
        change("marksUnit", "kg → lb", base, base.copy(marksUnit = "lb"), Channel.WEIGHT_UNIT) { _, v ->
            if (v.settings.weightUnit == WeightUnit.LBS) null else "Ajustes sigue en ${v.settings.weightUnit}"
        }
        finish("marcas")
    }

    @Test
    fun anotherVersionTheMovedSessionsAndTheAdaptedSplitReachTheActivatedProgram() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val first = activated(noMarks.copy(versions = 1))
        val second = activated(noMarks.copy(versions = 2))
        change("planVariantSeed", "versión 0 → versión 1", noMarks, noMarks.copy(versions = 1), Channel.EXERCISES)
        change("planVariantSeed", "versión 1 → versión 2", noMarks.copy(versions = 1), noMarks.copy(versions = 2), Channel.EXERCISES)
        if (first.read(Channel.EXERCISES) == second.read(Channel.EXERCISES)) failures += "planVariantSeed: las versiones 1 y 2 son idénticas"

        change("weekLayoutOverrides", "lunes → miércoles", noMarks, noMarks.copy(moves = listOf(1 to 3)), Channel.DAYS) { _, v ->
            val trained = v.sessions.mapNotNull { it.dayOfWeek }.toSet()
            if (trained == setOf(2, 3, 4, 5)) null else "se entrena $trained y se movió el lunes al miércoles"
        }
        change("weekLayoutOverrides", "lunes ↔ viernes (intercambio)", noMarks, noMarks.copy(moves = listOf(1 to 5)), Channel.SESSION_NAMES, Channel.EXERCISES)
        change("adaptedSplitId", "el reparto del programa → el primer reparto de la semana armada", noMarks, noMarks.copy(splitIndex = 0), Channel.SESSION_NAMES, Channel.EXERCISES)
        finish("semana armada")
    }

    /**
     * El otro camino de PLAN: un plan PROPIO del catálogo (no el «a medida»). Lo arma el personalizador nativo, que recibe los días,
     * el tiempo, el nivel, el cardio, la calibración y los músculos, pero NO el día con más energía, el inicio de semana ni las
     * marcas (su carga se aprende de la primera sesión). Aquí se exige lo que sí recibe y se deja en la tabla, con el prefijo
     * `[plan propio]`, lo que no cambia, para que el informe lo diga con evidencia.
     */
    @Test
    fun anOwnCatalogPlanTakesTheDaysTheTimeTheLevelAndTheMusclesAndTheTableSaysWhatItDoesNotTake() = runTest(dispatcher.scheduler, timeout = 20.minutes) {
        val own = noMarks.copy(
            profile = TrainingGoalProfile.POWERLIFTING,
            planId = com.example.kpkn.data.protocols.definitions.NativeProfileKind.STRENGTH.entryId,
            minutes = 90,
            marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 170.0),
        )
        val result = activated(own)
        val limit = timeBudgetWithTolerance(own.minutes)
        if (result.longestMinutes > limit) failures += "[plan propio] minutos: la sesión más larga mide ${result.longestMinutes} min y el tope es $limit"
        change("[plan propio] selectedWeekdays", "lun, mar, jue, vie → lun, mié, vie, sáb", own, own.copy(weekdays = setOf(1, 3, 5, 6)), Channel.DAYS) { _, v ->
            val trained = v.sessions.mapNotNull { it.dayOfWeek }.toSet()
            if (trained == setOf(1, 3, 5, 6)) null else "se entrena $trained"
        }
        // Para un plan del catálogo los minutos son un TOPE (con la tolerancia del 15 %): el plan que cabe no se estira ni se recorta
        // y el que no cabe no se ofrece. Por eso 90 → 60 no cambia nada mientras quepa; la fila dice cuándo deja de caber.
        change("[plan propio] minutesPerSession", "90 → 60 min (tope, no objetivo)", own, own.copy(minutes = 60)) { _, v ->
            val tolerance = timeBudgetWithTolerance(60)
            if (v.longestMinutes <= tolerance) null else "la sesión más larga mide ${v.longestMinutes} min y el tope es $tolerance"
        }
        val at30 = runCatching { activated(own.copy(minutes = 30)) }
        table += "[plan propio] minutesPerSession · con 30 min el plan ${if (at30.isSuccess) "se ofrece" else "no se ofrece (no cabe)"}" +
            (at30.getOrNull()?.let { " y mide ${it.longestMinutes} min" } ?: "")
        change("[plan propio] priorityMuscles", "sin preferencia → pecho y espalda", own, own.copy(muscles = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)), Channel.ORDER_PRIORITIES)
        change("[plan propio] experience", "intermedio → empezando", own, own.copy(experience = "new"), Channel.SETS)
        // Lo que el camino del catálogo NO recibe (se anota; si algún día cambia, la fila lo dirá).
        change("[plan propio] freshestDay", "jueves → lunes", own, own.copy(freshDay = 1))
        change("[plan propio] weekStartDay", "empieza el jueves → empieza el lunes", own, own.copy(weekStart = 1))
        change("[plan propio] liftMarks", "140/100/170 kg → 150/110/180 kg", own, own.copy(marks = mapOf(LiftMark.SQUAT to 150.0, LiftMark.BENCH to 110.0, LiftMark.DEADLIFT to 180.0)))
        finish("plan propio del catálogo")
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // Comprobaciones comunes
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private fun dayName(day: Int): String = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")[day - 1]

    /** La forma de la semana: cada sesión con su día, su lugar y sus ejercicios (configuración y series). */
    private fun shapeOf(program: Program): List<Any?> = firstWeek(program).map { session ->
        listOf(session.name, session.dayOfWeek, session.isMainSession, session.placeId, session.allExercises().map { it.catalogConfigurationId to it.sets.size })
    }

    /** Lo que TODA activación cumple, cambie lo que cambie la persona. */
    private fun assertInvariants(a: Activated) {
        ProgramExecutionContract.requireExecutable(a.program)
        assertEquals("autorregulación «sugerir y confirmar»", AutoregulationMode.PROPOSE, a.program.autoregulationMode)
        assertEquals("el programa activado es el activo", a.commitId, a.activeProgramId)
        if (a.declared.planId == null) assertTrue("toda sesión lleva su lugar: ${a.read(Channel.PLACES)}", a.sessions.all { it.placeId != null })
        assertEquals("el tipo de atleta sale del objetivo", GoalProfileMapping.athleteTypeOf(a.declared.profile), a.settings.athleteType)
        assertNotNull("la calibración de volumen viaja a Ajustes", a.settings.volumeCalibrationProfile)
        assertEquals(
            "lo previsualizado es lo activado",
            shapeOf(a.previewed),
            shapeOf(a.program),
        )
    }

    /**
     * Un caso: activa [from] y [to] (la persona base y la misma con UN dato cambiado), exige que cambien al menos los canales
     * [expected] y que se cumplan las invariantes de toda activación y la comprobación [direction] (el efecto es el que se
     * declaró, no un cambio cualquiera). Devuelve los canales que cambiaron y deja una fila en la tabla.
     */
    private suspend fun TestScope.change(
        datum: String,
        case: String,
        from: Declared,
        to: Declared,
        vararg expected: Channel,
        direction: (Activated, Activated) -> String? = { _, _ -> null },
    ): Set<Channel> {
        val a = activated(from)
        val b = activated(to)
        val changed = Channel.entries.filter { a.read(it) != b.read(it) }.toSet()
        table += "$datum · $case → cambian: ${changed.joinToString { it.name }.ifEmpty { "NADA" }}"
        val missing = expected.filter { it !in changed }
        if (missing.isNotEmpty()) failures += "$datum ($case): no cambia ${missing.joinToString { it.name }} (cambian: ${changed.joinToString { it.name }.ifEmpty { "nada" }})"
        direction(a, b)?.let { failures += "$datum ($case): $it" }
        return changed
    }

    private fun finish(group: String) {
        val file = File("build/reports/entreno-declaration-effect/table.txt")
        file.parentFile.mkdirs()
        file.writeText(table.joinToString("\n"))
        println("Efecto de lo declarado ($group): ${table.size} filas; fallos: ${failures.size}")
        assertTrue("controles muertos o efectos que no son los declarados (${failures.size}):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // El recorrido: el asistente real de punta a punta hasta activar
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private suspend fun TestScope.activated(declared: Declared): Activated = cache.getOrPut(declared) { activate(declared) }

    private suspend fun TestScope.activate(d: Declared): Activated {
        val db = KpknDatabase.createInMemory(app)
        try {
            val vm = SetupWizardViewModel(
                app,
                SavedStateHandle(),
                persistence = RoomPersistence(db),
                environment = FixedSettingsEnvironment(Settings()),
                commits = RoomCommits(db),
            ).also { viewModelStore.put("q-effect-${vmCounter++}", it) }
            vm.initialize(SetupWizardMode.TRAINING_ONLY, draftId = "q-effect-draft-$vmCounter")
            await(vm, "wizard cargado") { !it.isLoading && it.currentStep == SetupStepId.NAME }
            walkToPlan(vm, d)

            val chosen = d.planId ?: GeneratedPlans.entryIdFor(d.profile)
            val swept = await(vm, "programa a medida listo") { idle(it) && it.planSweep == SetupPlanSweep.READY }
            check(swept.availablePlanCandidates.any { it.id == chosen }) {
                "el plan $chosen no es viable para $d: ${swept.availablePlanCandidates.map { it.id }}"
            }
            vm.selectPlan(chosen)
            await(vm, "programa elegido y preparado") { idle(it) && it.programPreview != null && it.draft.selectedCatalogId == chosen }
            repeat(d.versions) { index ->
                vm.anotherPlanVersion()
                await(vm, "versión ${index + 1}") { idle(it) && it.draft.planVariantSeed == index + 1 && it.programPreview != null && it.planSweep == SetupPlanSweep.READY }
            }
            confirm(vm, SetupStepId.PLAN, SetupStepId.WEEK_LAYOUT)
            await(vm, "semana armada") { idle(it) && it.programPreview != null && it.weekLayout != null }

            d.moves.forEach { (fromDay, toDay) ->
                val sessionId = checkNotNull(vm.state.value.weekLayout?.assignment?.get(fromDay)) { "no hay sesión el día $fromDay" }
                vm.moveSession(sessionId, toDay)
                await(vm, "sesión movida del $fromDay al $toDay") { idle(it) && it.weekLayout?.assignment?.get(toDay) == sessionId }
            }
            d.splitIndex?.let { index ->
                val split = vm.state.value.weekLayout!!.splitOptions[index]
                vm.adaptToSplit(split.id)
                await(vm, "adaptado al reparto ${split.id}") { idle(it) && it.draft.adaptedSplitId == split.id && it.weekLayout?.selectedSplitId == split.id }
            }

            confirm(vm, SetupStepId.WEEK_LAYOUT, SetupStepId.MILESTONE_TRAINING)
            confirm(vm, SetupStepId.MILESTONE_TRAINING, SetupStepId.REVIEW_ACTIVATE)
            await(vm, "revisión en reposo") { idle(it) && it.programPreview != null }
            val previewed = checkNotNull(vm.state.value.programPreview)

            val receipt = vm.commit()
            assertNotNull("la activación de $d falló: ${vm.state.value.errors}", receipt)
            assertEquals(WizChatMachineState.Committed, vm.state.value.machineState)
            val commitId = vm.state.value.draft.commitId
            val saved = checkNotNull(room { db.programDao().getById(commitId) }) { "programa no guardado" }.toProgram()
            val settings = checkNotNull(room { db.settingsDao().get() }) { "ajustes no guardados" }.toSettings()
            val active = room { db.stateDao().getActiveProgram() }?.toActiveProgramState()?.programId
            val result = Activated(d, saved, previewed, settings, active, commitId)
            assertInvariants(result)
            return result
        } finally {
            viewModelStore.clear()
            db.close()
        }
    }

    /** Recorre la ruta REAL del alta respondiendo cada paso con lo declarado hasta llegar a PLAN. */
    private fun TestScope.walkToPlan(vm: SetupWizardViewModel, d: Declared) {
        var guard = 0
        while (vm.state.value.currentStep != SetupStepId.PLAN) {
            check(guard++ < 60) { "el recorrido no llega a PLAN (cursor ${vm.state.value.currentStep})" }
            val step = vm.state.value.currentStep
            answer(vm, step, d)
            await(vm, "reposo tras responder $step") { rest(it) }
            val next = checkNotNull(SetupStepGraph.next(step, vm.state.value.draft.stepContext())) { "ruta sin paso tras $step" }
            confirm(vm, step, next)
        }
    }

    private fun answer(vm: SetupWizardViewModel, step: SetupStepId, d: Declared) {
        when (step) {
            SetupStepId.NAME -> vm.setStepText(SetupStepId.NAME, "Ana")
            SetupStepId.AGE -> vm.setStepNumber(SetupStepId.AGE, 30.0)
            SetupStepId.HEIGHT -> vm.setStepNumber(SetupStepId.HEIGHT, 175.0)
            SetupStepId.WEIGHT -> vm.setStepNumber(SetupStepId.WEIGHT, 72.0)
            SetupStepId.EQUATION_SEX -> vm.setStepChoice(SetupStepId.EQUATION_SEX, "male")
            SetupStepId.BODY_FAT -> vm.updateStep(SetupStepId.BODY_FAT) { it.withBodyFatRulerValue(18) }
            SetupStepId.EXPERIENCE -> vm.setStepChoice(SetupStepId.EXPERIENCE, d.experience)
            SetupStepId.EQUIPMENT -> vm.updateStep(SetupStepId.EQUIPMENT) { it.withPlaces(d.places) }
            SetupStepId.AVAILABILITY -> d.material?.let { material -> vm.updateStep(SetupStepId.AVAILABILITY) { it.withMaterial(material) } }
            SetupStepId.GOAL -> vm.setGoalProfile(d.profile)
            SetupStepId.FRESH_DAY -> vm.setFreshDay(d.freshDay)
            SetupStepId.WEEKDAYS -> vm.updateStep(SetupStepId.WEEKDAYS) { draft ->
                val withDays = draft.withWeekdays(d.weekdays)
                val withStart = d.weekStart?.let { withDays.withWeekStart(it) } ?: withDays
                d.dayPlaces.entries.fold(withStart) { current, (day, place) -> current.withDayPlace(day, place) }
            }
            SetupStepId.SESSION_TIME -> vm.setSessionMinutes(d.minutes)
            SetupStepId.CARDIO_TYPE -> {
                if (d.bike) vm.setOutdoorBike(true)
                vm.setStepChoice(SetupStepId.CARDIO_TYPE, d.cardioType)
            }
            SetupStepId.CARDIO_TIME -> vm.setStepChoice(SetupStepId.CARDIO_TIME, d.cardioMinutes)
            SetupStepId.VOLUME_TECHNIQUE -> vm.setStepChoice(step, d.technique)
            SetupStepId.VOLUME_CONSISTENCY -> vm.setStepChoice(step, d.consistency)
            SetupStepId.VOLUME_STRENGTH -> vm.setStepChoice(step, d.strength)
            SetupStepId.VOLUME_MOBILITY -> vm.setStepChoice(step, d.mobility)
            SetupStepId.CAPABILITIES -> vm.state.value.draft.capabilitySkills().forEach { skill ->
                vm.setCapability(skill, d.capabilities[skill] ?: CapabilityLevel.SOME)
            }
            SetupStepId.PRIORITIES -> vm.updateStep(SetupStepId.PRIORITIES) { it.withMuscles(d.muscles) }
            SetupStepId.TRAINING_MAX -> {
                d.marks.forEach { (lift, kg) -> vm.setLiftMark(lift, kg) }
                if (d.marksUnit != "kg") vm.setMarksUnit(d.marksUnit)
            }
            else -> Unit
        }
    }

    private fun rest(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing

    private fun idle(state: SetupWizardState): Boolean =
        rest(state) && state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading

    private fun TestScope.confirm(vm: SetupWizardViewModel, step: SetupStepId, expectedNext: SetupStepId) {
        await(vm, "reposo antes de confirmar $step") { idle(it) }
        val result = vm.submitCurrentStep(step, expectedRevision = vm.state.value.draft.revision)
        assertEquals(
            "Continuar sobre $step (errores=${vm.state.value.errors}, cursor=${vm.state.value.currentStep})",
            SetupSubmitOutcome.ACCEPTED,
            result.outcome,
        )
        await(vm, "cursor en $expectedNext tras $step") { it.currentStep == expectedNext }
    }

    private fun TestScope.await(
        vm: SetupWizardViewModel,
        what: String,
        timeoutMs: Long = 60_000,
        condition: (SetupWizardState) -> Boolean,
    ): SetupWizardState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            val state = vm.state.value
            if (condition(state)) return state
            if (System.currentTimeMillis() > deadline) {
                fail(
                    "Timeout esperando $what en ${state.currentStep}: máquina=${state.machineState} barrido=${state.planSweep} " +
                        "errores=${state.errors} preview=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading} " +
                        "candidatos=${state.availablePlanCandidates.map { it.id }}",
                )
            }
            Thread.sleep(10)
        }
    }

    private fun <T> room(block: suspend () -> T): T = runBlocking { block() }

    private class RoomPersistence(db: KpknDatabase) : SetupWizardPersistence {
        private val drafts = SetupDraftRepository(db)
        private val resolver = SetupDraftResolver(db)
        override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)
        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            drafts.save(draftId, payloadJson, revision, catalogRevision)
        override suspend fun discard(draftId: String) = drafts.discard(draftId)
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
    }

    private class RoomCommits(db: KpknDatabase) : SetupWizardCommits {
        private val coordinator = SetupCommitCoordinator(db)
        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult = coordinator.commit(request)
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
