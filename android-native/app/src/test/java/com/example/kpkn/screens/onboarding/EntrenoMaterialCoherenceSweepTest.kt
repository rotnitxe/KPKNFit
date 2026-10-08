package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MarksContext
import com.example.kpkn.domain.onboarding.PlaceMaterial
import com.example.kpkn.domain.onboarding.SessionPlaceFit
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingGoalRequirements
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ConfigurationEquipmentFilter
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.approach.ApproachLevel
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import com.example.kpkn.domain.training.generator.DayEquipment
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import com.example.kpkn.domain.training.generator.MaterialProfile
import com.example.kpkn.domain.training.generator.RoutineContractChecks
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import com.example.kpkn.domain.training.resolveEffectiveEquipment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 (Q) · barrido de coherencia material ↔ ejercicios sobre el programa ACTIVABLE, no sobre la salida cruda del
 * generador: 7 perfiles de material (solo cuerpo, parque, casa con mancuernas y banco, casa con anillas y cajón, gimnasio
 * completo, gimnasio sin rack y multi-lugar) × los 10 perfiles de objetivo × 1–7 días × 30–180 min (el rango del reloj), con la experiencia y el
 * día con más energía rotando de forma determinista. Cada celda arma el borrador con los reductores reales del asistente,
 * genera con el pedido del borrador ([routineRequest]), completa el programa como la activación ([generatedProgramOf]) y, con
 * varios lugares, aplica la semana armada ([applyLayout] con el contraste de lugares), y exige:
 *
 *  1. el contrato del generador (`RoutineContractChecks`: días, ventana de minutos o nota, ≥ 3 ejercicios, identidad de catálogo,
 *     techos de volumen, un día = cuerpo completo…);
 *  2. `ProgramExecutionContract` y la autorregulación «sugerir y confirmar» del alta;
 *  3. que cada sesión lleve su lugar y que NINGÚN ejercicio pida material que su lugar no tiene, medido con el filtro único
 *     ([ConfigurationEquipmentFilter]) sobre el equipo efectivo del material de ese lugar y con el contraste que usa la
 *     semana armada ([SessionPlaceFit]);
 *  4. que la sesión más larga quepa en los minutos pedidos con la tolerancia de un programa «a medida» ([SessionTimeFit], +1) desde
 *     los 60 min (con menos manda la estructura de la sesión; ver [STRICT_MINUTES_FROM]);
 *  5. la sesión principal en el día con más energía (o el primer día de entreno posterior);
 *  6. que toda sesión pesada lleve aproximación y movilidad: el primer ejercicio pesado abre con rampa y movilidad, y volver a
 *     pasar el planificador sobre la sesión activable no añade nada (no falta ni sobra).
 */
class EntrenoMaterialCoherenceSweepTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private val configurations: Map<String, ExerciseConfigurationV2> by lazy {
        catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
    }

    private val approachInfoOf by lazy { GeneratorCatalog.of(catalog).approachInfoOf }

    private data class Material(val id: String, val places: Set<TrainingPlace>, val symbols: Set<EquipmentSymbolId>)

    private val gymSeed = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))

    private val materials = listOf(
        Material("solo cuerpo", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)),
        Material("parque", setOf(TrainingPlace.PUBLIC), EquipmentSymbols.seedFor(setOf(TrainingPlace.PUBLIC))),
        Material("casa mancuernas+banco", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH)),
        Material("casa anillas+cajón", setOf(TrainingPlace.HOME), setOf(EquipmentSymbolId.RINGS, EquipmentSymbolId.BOX)),
        Material("gimnasio completo", setOf(TrainingPlace.GYM), gymSeed),
        Material("gimnasio sin rack", setOf(TrainingPlace.GYM), gymSeed - EquipmentSymbolId.RACK),
        Material(
            "multi-lugar",
            setOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.PUBLIC),
            gymSeed + EquipmentSymbolId.RINGS,
        ),
    )

    /** Los tiempos que el reloj del asistente ofrece como atajos y puntos de control: de su mínimo (30) a su máximo (180). */
    private val minutesGrid = listOf(30, 45, 60, 75, 90, 120, 150, 180)

    /**
     * Desde cuántos minutos pedidos el programa «a medida» cabe SIEMPRE en la tolerancia del contrato (+1 min). Por debajo, lo que
     * manda es la estructura de la sesión y no el último minuto: la sesión mínima (el calentamiento fijo de 3 min del estimador,
     * la aproximación, la movilidad y tres ejercicios con sus descansos mínimos), el bloque de cardio que no se recorta y las
     * series prioritarias que el ajuste fino no sacrifica por uno o dos minutos. Ahí el generador deja la sesión dentro de su
     * ventana (85–110 %, que `RoutineContractChecks` sí exige) y la revisión lo dice con la nota de tiempo («~47 min por sesión: un
     * poco más de los 45 que pediste»). El informe cuenta cuántas celdas pasan de +1 min en cada tiempo pedido.
     *
     * Por eso el reloj no baja de 30 min (decisión del 2026-10-08): con 20 min (que el barrido medía antes) 284 de 490 celdas, el 58 %,
     * se pasaban de lo pedido, hasta +10 min, porque un ejercicio pesado con su aproximación y su movilidad ya pide ~30 min.
     */
    private val STRICT_MINUTES_FROM = 60

    /** Columnas de tiempo que el reloj retiró de la rejilla (la de 20 min) y que la rotación determinista de las celdas sigue contando. */
    private val RETIRED_MINUTES_COLUMNS = 1

    private fun isStrictAboutMinutes(cell: Cell): Boolean = cell.minutes >= STRICT_MINUTES_FROM

    private val levels = listOf(SetupExperience.NEW, SetupExperience.RETURNING, SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED)

    /** El día con más energía de cada celda: el primero, el último, uno libre o el de en medio (nunca sin declarar: el paso es obligatorio). */
    private fun freshestFor(counter: Int, days: List<Int>): Int = when (counter % 4) {
        0 -> days.first()
        1 -> days.last()
        2 -> (1..7).firstOrNull { it !in days } ?: days[days.size / 2]
        else -> days[days.size / 2]
    }

    private data class Cell(
        val profile: TrainingGoalProfile,
        val material: Material,
        val dayCount: Int,
        val minutes: Int,
        val level: SetupExperience,
        val freshest: Int,
    ) {
        val label: String get() = "${profile.name}/${material.id}/${dayCount}d/${minutes}min/${level.name}/fresco$freshest"
    }

    private fun draftOf(cell: Cell): SetupWizardDraft {
        val days = RoutineTestSupport.weekdays(cell.dayCount)
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(cell.profile)))
        val goal = GoalProfileMapping.setupGoalOf(cell.profile)
        var draft = SetupWizardDraft(
            commitId = "sweep-${cell.label}",
            includeTraining = true,
            experience = cell.level,
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = SetupTrainingPath.PERSONALIZE,
        )
            .withPlaces(cell.material.places)
            .withMaterial(cell.material.symbols)
            .withGoalProfile(cell.profile)
            .withFreshestDay(cell.freshest)
            .withWeekdays(days.toSet())
        // Con varios lugares, cada día de entreno recibe uno (gimnasio, casa, espacios públicos, en rotación).
        val places = TrainingPlace.entries.filter { it in cell.material.places }
        if (places.size >= 2) {
            days.sorted().forEachIndexed { index, day -> draft = draft.withDayPlace(day, places[index % places.size]) }
        }
        draft = draft.withSessionMinutes(cell.minutes)
        return draft.copy(
            cardioType = if (goal.requiresCardio) CardioType.WALK else null,
            cardioMinutes = if (goal.requiresCardio) 20 else null,
            selectedCatalogId = entry.id,
        )
    }

    private fun firstWeek(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.first().sessions

    /** El material declarado para [place] (el de SU lugar con varios lugares; el declarado con uno solo). */
    private fun availabilityAt(draft: SetupWizardDraft, place: TrainingPlace): EquipmentAvailability {
        val declared = draft.trainingOptions.availability ?: EquipmentAvailability()
        return PlaceMaterial.byPlace(EquipmentSymbols.selectedFrom(declared), draft.trainingPlaces)[place] ?: declared
    }

    private fun approachLevelOf(level: SetupExperience): ApproachLevel = when (level) {
        SetupExperience.NEW, SetupExperience.RETURNING -> ApproachLevel.NOVICE
        SetupExperience.INTERMEDIATE -> ApproachLevel.INTERMEDIATE
        SetupExperience.ADVANCED -> ApproachLevel.ADVANCED
    }

    /** ¿El ejercicio es «pesado» para el planificador de aproximación? (las mismas señales que `ApproachPlanner`). */
    private fun isHeavy(exercise: Exercise, canBeHeavy: Boolean): Boolean {
        if (!canBeHeavy) return false
        val work = exercise.sets.filter { !it.isEmptySlot }
        if (work.isEmpty()) return false
        val percent = work.mapNotNull { it.targetPercentageRM }.maxOrNull()
        val lowestReps = work.mapNotNull { it.effectiveRepRange()?.min }.minOrNull()
        val rir = work.mapNotNull { it.targetRIR }.minOrNull()
        val rpe = work.mapNotNull { it.targetRPE }.maxOrNull()
        return (percent != null && percent >= ApproachOptions().heavyPercentOf1Rm) ||
            (lowestReps != null && lowestReps <= 6 && ((rir != null && rir <= 2) || (rpe != null && rpe >= 7.5)))
    }

    /** Cómo se reparte el tiempo de la sesión más larga: para distinguir el mínimo físico de un descuido del ajuste fino. */
    private fun timeBreakdownOf(program: Program): String {
        val session = firstWeek(program).maxByOrNull { SessionDurationEstimator.estimate(it).totalSeconds } ?: return "sin sesiones"
        val time = SessionDurationEstimator.estimate(session)
        val resistance = session.allExercises().filter { it.cardioDetails == null }
        val workSets = resistance.map { exercise -> exercise.sets.count { !it.isEmptySlot } }
        return "«${session.name}»: ${resistance.size} ejercicios con series $workSets; trabajo ${time.resistanceExecutionSeconds / 60} min, " +
            "descansos ${time.resistanceRestSeconds / 60} min, calentamiento+aproximación+movilidad ${time.warmupSeconds / 60} min, " +
            "cardio ${time.cardioSeconds / 60} min, preparación ${time.setupSeconds / 60} min"
    }

    /** Los problemas de una celda (vacío = cumple todo). */
    private fun problemsOf(cell: Cell, stats: Stats): List<String> {
        val problems = ArrayList<String>()
        val draft = draftOf(cell)
        val mode = GeneratedPlans.modeFor(cell.profile)
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(cell.profile)))
        val request = draft.routineRequest(mode, catalog)
        val routine = RoutineGenerator.generate(request)

        // 1. El contrato del generador. Un objetivo que el material no cumple (Strongman sin barra, Halterofilia sin rack…) el
        // asistente no deja confirmarlo (`TrainingGoalRequirements`): ahí el programa sigue siendo ejecutable y coherente con el
        // material, pero no se exige el mínimo de ejercicios por sesión que solo vale para lo que se puede activar.
        val compatible = TrainingGoalRequirements.isCompatible(cell.profile, cell.material.symbols)
        if (!compatible) stats.incompatible++
        val profile = MaterialProfile(cell.material.id, draft.trainingPlaces, request.availability, request.availabilityByPlace)
        problems += RoutineContractChecks.problems(profile, request, routine)
            .filter { compatible || !Regex("solo \\d+ ejercicios de fuerza|mixta con \\d+ ejercicios").containsMatchIn(it) }

        // El programa ACTIVABLE: como lo deja la activación, con la semana armada (contraste de lugares con varios lugares).
        var program = generatedProgramOf(routine, entry, draft)
        if (draft.trainingPlaces.size >= 2) {
            val fit = SessionPlaceFit.of(catalog, draft.trainingOptions.availability, draft.trainingPlaces)
            val outcome = applyLayout(draft, program, resolver = null, placeFit = fit)
            if (outcome.placeConflicts.isNotEmpty()) problems += "sin mover nada la semana avisa de ${outcome.placeConflicts.size} sesiones fuera de lugar"
            if (outcome.program != program) problems += "sin mover nada la semana armada cambia el programa"
            program = outcome.program
        }

        // 2. Ejecutable, autorregulación y días.
        ProgramExecutionContract.validate(program).takeIf { it.isNotEmpty() }?.let { problems += "no ejecutable: ${it.map { issue -> issue.message }}" }
        if (program.autoregulationMode != AutoregulationMode.PROPOSE) problems += "autorregulación ${program.autoregulationMode}, no «sugerir y confirmar»"
        val sessions = firstWeek(program)
        if (sessions.mapNotNull { it.dayOfWeek }.toSet() != draft.selectedWeekdays) {
            problems += "días ${sessions.map { it.dayOfWeek }} ≠ ${draft.selectedWeekdays}"
        }

        // 3. Lugar y material de cada sesión.
        val fit = SessionPlaceFit.of(catalog, draft.trainingOptions.availability, draft.trainingPlaces)
        sessions.forEach { session ->
            val day = session.dayOfWeek ?: return@forEach
            val place = TrainingPlace.entries.firstOrNull { it.name == session.placeId }
            if (place == null) {
                problems += "«${session.name}» (día $day) sin lugar (placeId=${session.placeId})"
                return@forEach
            }
            if (place != draft.placeForDay(day)) problems += "«${session.name}» está en $place y el día $day es de ${draft.placeForDay(day)}"
            if (!fit.fits(session, place)) problems += "«${session.name}» no cabe en $place según el contraste de la semana armada"
            val availability = availabilityAt(draft, place)
            val options = TrainingOptions(availability = availability)
            val tokens = options.resolveEffectiveEquipment(emptySet()).tokens
            val exact = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)
            val equipment = DayEquipment(availability)
            session.allExercises().forEach { exercise ->
                val cardio = exercise.cardioDetails
                if (cardio != null) {
                    if (cardio.type !in equipment.cardioTypes) problems += "«${session.name}» ($place) pide cardio ${cardio.type} que ese lugar no permite"
                    return@forEach
                }
                val id = exercise.catalogConfigurationId ?: return@forEach
                val configuration = configurations[id]
                if (configuration == null) {
                    problems += "«${session.name}» cita $id que no existe"
                } else if (!ConfigurationEquipmentFilter.allows(configuration, tokens, requireExactMachineConfiguration = exact)) {
                    problems += "«${session.name}» ($place, ${cell.material.id}) usa $id (${configuration.profile.equipmentId}) y su lugar no tiene ese material"
                }
            }
        }

        // 4. Minutos: lo que el asistente mide en la revisión.
        val requested = draft.requestedSessionMinutes()
        val longest = longestSessionMinutes(program)
        if (longest == null) {
            problems += "sin minutos medidos"
        } else {
            stats.noteMinutes(requested, longest)
            if (!isStrictAboutMinutes(cell) && longest > requested + GENERATED_TIME_TOLERANCE_MINUTES) {
                stats.lowTimeOvershoots += Triple(cell.label, longest - requested, timeBreakdownOf(program))
            }
            if (isStrictAboutMinutes(cell) && !SessionTimeFit.fits(requested, longest, generated = true)) {
                problems += "la sesión más larga mide $longest min y se pidieron $requested (tolerancia +${GENERATED_TIME_TOLERANCE_MINUTES}) · ${timeBreakdownOf(program)}"
            }
        }

        // 5. La sesión principal.
        val mains = sessions.filter { it.isMainSession }
        if (mains.size == 1) {
            val expected = RoutineTestSupport.expectedMainDay(sessions.mapNotNull { it.dayOfWeek }, cell.freshest)
            if (mains.single().dayOfWeek != expected) problems += "sesión principal en ${mains.single().dayOfWeek}, esperaba $expected (día con más energía ${cell.freshest})"
        } else {
            problems += "${mains.size} sesiones principales"
        }

        // 6. Aproximación y movilidad.
        val approachOptions = ApproachOptions(level = approachLevelOf(cell.level))
        sessions.forEach { session ->
            val resistance = session.allExercises().filter { it.cardioDetails == null && it.sets.any { set -> !set.isEmptySlot } }
            val first = resistance.firstOrNull()
            if (first != null) {
                val info = approachInfoOf(first)
                if (info != null && isHeavy(first, info.canBeHeavy)) {
                    stats.heavyOpeners++
                    if (first.warmupSets.isEmpty()) problems += "«${session.name}» abre con ${first.name}, pesado, sin series de aproximación"
                    if (first.mobilitySeries.isEmpty()) problems += "«${session.name}» abre con ${first.name}, pesado, sin movilidad previa"
                }
            }
            if (ApproachPlanner.apply(session, approachOptions, approachInfoOf) != session) {
                problems += "«${session.name}»: volver a pasar el planificador de aproximación añade o cambia algo"
            }
        }
        return problems
    }

    /** Lo que se mide de paso (no se exige): cuántas celdas, cuántas sesiones abren pesadas y cómo caen los minutos. */
    private class Stats {
        var cells = 0
        var incompatible = 0
        var heavyOpeners = 0
        var shorterThanWindow = 0
        var overshoot = 0
        var worstOvershoot = 0

        /** Celdas que pasan de +1 min, por minutos pedidos (y cuántas celdas hubo con ese tiempo): lo que el informe enseña. */
        val overshootByRequested = java.util.TreeMap<Int, Int>()
        val cellsByRequested = java.util.TreeMap<Int, Int>()

        /** Las celdas de menos de 60 min que pasan de +1 min, con el reparto del tiempo de su sesión más larga (celda, exceso, reparto). */
        val lowTimeOvershoots = ArrayList<Triple<String, Int, String>>()

        fun noteMinutes(requested: Int, longest: Int) {
            cellsByRequested.merge(requested, 1, Int::plus)
            if (longest < requested * 0.85) shorterThanWindow++
            if (longest > requested + 1) {
                overshoot++
                worstOvershoot = maxOf(worstOvershoot, longest - requested)
                overshootByRequested.merge(requested, 1, Int::plus)
            }
        }

        val byRequestedText: String
            get() = cellsByRequested.keys.joinToString(", ") { requested ->
                "$requested min: ${overshootByRequested[requested] ?: 0}/${cellsByRequested[requested]}"
            }
    }

    // ─── Las marcas que el asistente pregunta ───────────────────────────────────────────────────────────────

    /** El programa «a medida» del perfil en el gimnasio completo, con los días, minutos y nivel dados y las marcas que se pasen. */
    private fun markedProgram(profile: TrainingGoalProfile, days: Int, minutes: Int, level: SetupExperience, marks: Map<LiftMark, Double>): Program {
        val week = RoutineTestSupport.weekdays(days)
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        var draft = SetupWizardDraft(commitId = "marks-${profile.name}", includeTraining = true, experience = level)
            .withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(profile)
            .withFreshestDay(week.first())
            .withWeekdays(week.toSet())
            .withSessionMinutes(minutes)
        marks.forEach { (lift, kg) -> draft = draft.withLiftMark(lift, kg) }
        val routine = RoutineGenerator.generate(draft.routineRequest(GeneratedPlans.modeFor(profile), catalog))
        return generatedProgramOf(routine, entry, draft)
    }

    /** Configuración, 1RM de referencia y porcentajes de cada ejercicio, en orden: lo que cambia si cambia una marca. */
    private fun loadsOf(program: Program): List<Any?> = firstWeek(program).map { session ->
        session.allExercises().map { exercise -> Triple(exercise.catalogConfigurationId, exercise.reference1RM, exercise.sets.map { it.targetPercentageRM }) }
    }

    /**
     * Cada marca que el asistente pregunta (`MarksContext.liftsFor`) es leída por el programa: declararla cambia las cargas en
     * alguna configuración típica (3–5 días, 60–90 min, nivel intermedio o avanzado), y el informe dice en cuántas de las 12
     * (por perfil y marca), para que una marca que casi nunca se lee se vea. Una marca sin ninguna configuración es un control muerto.
     */
    @Test
    fun everyMarkTheWizardAsksChangesTheLoadsOfTheProgramInSomeTypicalConfiguration() {
        val configurations = buildList {
            for (days in listOf(3, 4, 5)) for (minutes in listOf(60, 90)) for (level in listOf(SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED)) {
                add(Triple(days, minutes, level))
            }
        }
        val profiles = listOf(
            TrainingGoalProfile.STRENGTH_MUSCLE, TrainingGoalProfile.POWERBUILDING, TrainingGoalProfile.POWERLIFTING,
            TrainingGoalProfile.STRONGMAN, TrainingGoalProfile.WEIGHTLIFTING,
        )
        val lines = ArrayList<String>()
        val dead = ArrayList<String>()
        profiles.forEach { profile ->
            val asked = MarksContext.liftsFor(profile, novice = false, hasBarbell = true, hasOlympicLifts = true)
            asked.forEach { lift ->
                val changed = configurations.count { (days, minutes, level) ->
                    loadsOf(markedProgram(profile, days, minutes, level, emptyMap())) !=
                        loadsOf(markedProgram(profile, days, minutes, level, mapOf(lift to 100.0)))
                }
                lines += "${profile.name} · ${lift.name}: cambia las cargas en $changed de ${configurations.size} configuraciones"
                if (changed == 0) dead += "${profile.name}/${lift.name}"
            }
        }
        val report = File("build/reports/entreno-material-sweep/marks.txt")
        report.parentFile.mkdirs()
        report.writeText(lines.joinToString("\n"))
        println(lines.joinToString("\n"))
        assertTrue("marcas que el asistente pregunta y que ninguna configuración típica lee: $dead\n${lines.joinToString("\n")}", dead.isEmpty())
        // Y ninguna se queda en un rincón: cada marca que se pregunta la lee el programa en al menos dos de cada tres configuraciones.
        val rare = lines.filter { line -> line.substringAfter("cambia las cargas en ").substringBefore(" de ").trim().toInt() < configurations.size * 2 / 3 }
        assertTrue("marcas que el programa casi nunca lee:\n${rare.joinToString("\n")}", rare.isEmpty())
    }

    @Test
    fun theActivatableProgramOfEveryMaterialProfileAndGoalIsCoherentWithItsPlaceMaterialItsMinutesAndItsApproach() {
        val started = System.nanoTime()
        val stats = Stats()
        val failures = ArrayList<Pair<String, String>>()
        var counter = 0
        for (profile in TrainingGoalProfile.entries) {
            for (material in materials) {
                for (dayCount in 1..7) {
                    val days = RoutineTestSupport.weekdays(dayCount)
                    // La rotación de experiencias y días con más energía cuenta también la columna de 20 min que el reloj retiró: así
                    // cada celda de 30 a 180 min conserva la experiencia y el día de siempre (las cifras del informe siguen siendo las
                    // de la auditoría) y la rotación no se alinea con los tiempos (con 8 columnas, múltiplo de 4, cada tiempo tendría
                    // siempre la misma experiencia y los 30 min solo se medirían con quien empieza).
                    counter += RETIRED_MINUTES_COLUMNS
                    for (minutes in minutesGrid) {
                        val cell = Cell(
                            profile = profile,
                            material = material,
                            dayCount = dayCount,
                            minutes = minutes,
                            level = levels[counter % levels.size],
                            freshest = freshestFor(counter / levels.size, days),
                        )
                        counter++
                        stats.cells++
                        val problems = try {
                            problemsOf(cell, stats)
                        } catch (t: Throwable) {
                            listOf("EXCEPCIÓN ${t::class.simpleName}: ${t.message}")
                        }
                        problems.forEach { failures += cell.label to it }
                    }
                }
            }
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        val categories = failures.groupBy { (_, text) -> text.replace(Regex("[0-9]+"), "#").take(60) }
        val summary = categories.entries.sortedByDescending { it.value.size }.take(15).joinToString("\n") { (category, items) ->
            "  [${items.size}] $category\n      p. ej.: ${items.take(2).joinToString(" | ") { (cell, text) -> "$cell: $text" }}"
        }
        val line = "Barrido de coherencia: ${stats.cells} celdas (${stats.incompatible} con un objetivo que el material no cumple) en ${"%.1f".format(seconds)} s; sesiones que abren con un ejercicio pesado: " +
            "${stats.heavyOpeners}; sesión más larga por debajo del 85 % pedido: ${stats.shorterThanWindow}; por encima de +1 min: " +
            "${stats.overshoot} (peor +${stats.worstOvershoot}; por tiempo pedido: ${stats.byRequestedText}); problemas: ${failures.size}"
        println(line)
        val report = File("build/reports/entreno-material-sweep/report.txt")
        report.parentFile.mkdirs()
        report.writeText(line + "\n" + failures.joinToString("\n") { (cell, text) -> "$cell: $text" })
        File("build/reports/entreno-material-sweep/overshoot.txt").writeText(
            stats.lowTimeOvershoots.sortedByDescending { it.second }.joinToString("\n") { (cell, extra, breakdown) -> "+$extra · $cell · $breakdown" },
        )
        assertEquals("celdas recorridas", TrainingGoalProfile.entries.size * materials.size * 7 * minutesGrid.size, stats.cells)
        // La rejilla va de un extremo a otro del reloj del asistente: si el rango cambia, el barrido cambia con él.
        assertEquals("el barrido parte del mínimo del reloj", EntrenoStepValues.SESSION_MINUTES_MIN, minutesGrid.first())
        assertEquals("el barrido llega al máximo del reloj", EntrenoStepValues.SESSION_MINUTES_MAX, minutesGrid.last())
        assertTrue("ningún tiempo de la rejilla queda fuera del reloj", minutesGrid.all { it in EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX })
        assertTrue("el barrido debe ejercitar sesiones con un ejercicio inicial pesado (${stats.heavyOpeners})", stats.heavyOpeners > 100)
        // Por debajo de 60 min la estructura puede pesar más que el último minuto, pero sigue siendo la excepción: con 45 min pasan de
        // +1 min menos de 3 de cada 100 celdas (hoy 4 de 490, hasta +4: el bloque de cardio que no se recorta y una sesión de cuerpo
        // completo con series prioritarias) y con 30 min, el mínimo del reloj, menos de 12 de cada 100 (hoy 30 de 490, hasta +3).
        // Con 20 min, que el reloj ya no ofrece, eran 284 de 490: una sesión con un ejercicio pesado mide ~30 min (calentamiento
        // fijo, aproximación y movilidad) y no cabe en 20.
        listOf(45 to 0.03, 30 to 0.12).forEach { (requested, share) ->
            val over = stats.overshootByRequested[requested] ?: 0
            val total = stats.cellsByRequested.getValue(requested)
            assertTrue("con $requested min pasan de +1 min $over de $total celdas (${stats.byRequestedText})", over.toDouble() / total < share)
        }
        assertTrue("problemas de coherencia (${failures.size}; todos en ${report.path}):\n$summary", failures.isEmpty())
    }
}
