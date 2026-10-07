package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.VolumeCalculator
import java.util.concurrent.ConcurrentHashMap

/**
 * Generador de rutinas «a medida» (Entreno v2): arma una SEMANA CÍCLICA ejecutable a partir de lo que la persona
 * declaró (días, minutos, material por lugar, nivel, capacidades, prioridades y marcas), sin plantilla fija.
 *
 * Es una función pura y determinista: la misma [RoutineRequest] (con su `variantSeed`) produce siempre la misma
 * [GeneratedRoutine], con los mismos ids. Para cualquier combinación válida (1–7 días, 20–180 min, cualquier material,
 * incluido solo el cuerpo, y cualquier nivel) devuelve un programa que pasa `ProgramExecutionContract.requireExecutable`;
 * lo que el catálogo no permita cubrir con ese material va a `notes` con una acción concreta. Lanza solo
 * [RoutineGenerationException] para entradas imposibles.
 *
 * Flujo: validar → reparto de la semana ([WeekPlanner]) → girar el ciclo para que la sesión más exigente caiga en el día
 * más fresco → cambiar por una sesión de cuerpo completo las sesiones específicas que el material de su día no permite
 * (un «Tirón» sin barra, bandas ni mancuernas) → armar cada sesión con el material de SU lugar ([SessionAssembler]: elección
 * de ejercicios, prescripción, ajuste a minutos con `SessionDurationEstimator`, aproximación con `ApproachPlanner`) →
 * programa, resumen e informe.
 */
object RoutineGenerator {

    /** Revisión del algoritmo; viaja en el bloque del programa generado. */
    const val REVISION = "routine-generator-v1"

    private val deviceCache = ConcurrentHashMap<EquipmentAvailability, DayEquipment>()

    private fun equipmentOf(availability: EquipmentAvailability): DayEquipment {
        if (deviceCache.size > 512) deviceCache.clear()
        return deviceCache.getOrPut(availability) { DayEquipment(availability) }
    }

    fun generate(request: RoutineRequest): GeneratedRoutine {
        validate(request)
        val weekStart = request.weekStartDay
        val days = request.weekdays.sortedBy { Math.floorMod(it - weekStart, 7) }
        val normalized = request.copy(
            weekdays = days,
            priorityMuscles = request.priorityMuscles.distinct().take(5),
            freshestDay = request.freshestDay?.takeIf { it in 1..7 },
        )
        val catalog = GeneratorCatalog.of(normalized.catalog)
        val programId = normalized.programId ?: stableProgramId(normalized)
        val budgets = VolumeBudgets.of(normalized)
        val ctx = GenContext(normalized, catalog, programId, days, budgets)

        val plans = WeekPlanner.plans(ctx)
        val mainDay = WeekPlanner.mainDay(days, normalized.freshestDay)
        val usedFallbacks = HashSet<String>()
        val arranged = WeekPlanner.arrange(plans, days, mainDay).mapIndexed { index, plan ->
            val place = placeOf(normalized, days[index])
            repairIfInfeasible(ctx, plan, equipmentOf(availabilityFor(normalized, place)), index, usedFallbacks)
        }
        val caps = SessionCaps(budgets, arranged)

        // Las sesiones se arman de la más exigente a la menos (la principal elige primero y no se queda sin variedad ni
        // sin techo de volumen), pero ids y orden de la semana siguen el calendario.
        val buildOrder = arranged.indices.sortedWith(compareByDescending<Int> { arranged[it].demand }.thenBy { it })
        val results = arrayOfNulls<AssembledSession>(arranged.size)
        for (index in buildOrder) {
            val day = days[index]
            val place = placeOf(normalized, day)
            val availability = availabilityFor(normalized, place)
            results[index] = SessionAssembler.assemble(
                ctx = ctx,
                plan = arranged[index],
                dayOfWeek = day,
                place = place,
                equipment = equipmentOf(availability),
                sessionIndex = index,
                isMain = day == mainDay,
                caps = caps,
            )
            caps.markBuilt(index)
        }
        val assembled = results.map { requireNotNull(it) }
        val sessions = assembled.map { it.session }

        val week = ProgramWeek(id = "$programId-week", name = "Semana repetible", sessions = sessions)
        val name = RoutineNarrative.suggestedName(ctx)
        val oneLiner = RoutineNarrative.oneLiner(ctx, assembled)
        val missing = ctx.missing.keys.filter { it !in ctx.covered }.toSet()
        val usedIds = sessions.flatMap { it.allExercises() }.mapNotNull { it.catalogConfigurationId }.toSet()
        val initial = RoutineNarrative.initialVersion(ctx, usedIds)
        val notes = (listOfNotNull(initial?.second) + RoutineNarrative.gapNotes(ctx, missing) + ctx.notes + listOfNotNull(RoutineNarrative.timeNote(ctx))).distinct()
        val program = Program(
            id = programId,
            name = name,
            description = (listOf(oneLiner) + notes).joinToString("\n"),
            mode = programModeOf(normalized.mode),
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            macrocycles = listOf(
                Macrocycle(
                    id = "$programId-macro",
                    name = "Mi plan",
                    blocks = listOf(
                        Block(
                            id = "$programId-block",
                            name = "Semana cíclica",
                            mesocycles = listOf(Mesocycle(id = "$programId-meso", name = "Base", weeks = listOf(week))),
                            sourceDefinitionId = "generated:${normalized.mode.name.lowercase()}",
                            sourceRevision = REVISION,
                            prescriptionOrigin = "KPKN_GENERATED",
                        ),
                    ),
                ),
            ),
            startDay = weekStart,
            volumeRecommendations = budgets.recommendations,
            schedulePlan = ProgramSchedulePlan(weekStartDay = weekStart, trainingDays = days.toSet()),
            autoregulationMode = AutoregulationMode.PROPOSE,
            tags = listOf("KPKN_GENERATED", normalized.mode.name),
        )
        ProgramExecutionContract.requireExecutable(program)

        val summary = RoutineSummary(
            suggestedName = name,
            oneLiner = oneLiner,
            days = assembled.map { item ->
                RoutineDaySummary(
                    dayOfWeek = item.dayOfWeek,
                    sessionId = item.session.id,
                    title = item.plan.title,
                    focus = item.plan.focus,
                    estimatedMinutes = item.minutes,
                    exerciseCount = item.session.allExercises().size,
                    mainExercises = item.mainExerciseNames,
                    isMain = item.dayOfWeek == mainDay,
                    kind = item.kind,
                    place = item.place,
                )
            },
            mainExercises = assembled.flatMap { it.mainExerciseNames }.distinct().take(8),
            reasons = RoutineNarrative.reasons(ctx, assembled, mainDay),
            mainSessionDay = mainDay,
            isInitialVersion = initial != null,
            initialVersionMissing = initial?.first.orEmpty(),
        )

        val infos = sessions.flatMap { it.allExercises() }.mapNotNull { it.catalogConfigurationId }.distinct()
            .mapNotNull { catalog.entry(it)?.legacy }
        val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessions, infos)
        val report = RoutineReport(
            patternsCovered = ctx.covered.toSet(),
            patternsMissing = missing,
            weeklyDirectSets = volume.mapValues { it.value.directSets },
            weeklyIndirectSets = volume.mapValues { it.value.indirectSets },
            weeklyDirectCeilings = budgets.muscles.associateWith { budgets.ceiling(it) },
            weeklyDirectTargets = budgets.muscles.associateWith { budgets.target(it) },
            weeklyMinimums = budgets.muscles.associateWith { budgets.minimum(it) },
            sessionMinutes = assembled.map { it.minutes },
            minutesWindow = ctx.windowMinutes,
            sessions = assembled.map { item ->
                RoutineSessionReport(
                    dayOfWeek = item.dayOfWeek,
                    title = item.plan.title,
                    kind = item.kind,
                    estimatedMinutes = item.minutes,
                    strengthExerciseCount = item.strengthCount,
                    hasCardio = item.hasCardio,
                    hasMobility = item.hasMobility,
                    patterns = item.patterns,
                    configurationIds = item.configurationIds,
                    place = item.place,
                    inWindow = item.inWindow,
                )
            },
            mainSessionDay = mainDay,
            minutesMisses = assembled.count { !it.inWindow },
        )
        return GeneratedRoutine(program = program, summary = summary, notes = notes, report = report)
    }

    // ─── Sesiones inviables con el material del día ────────────────────────────────────────────────────────

    private fun feasibleSlots(ctx: GenContext, plan: SessionPlan, equipment: DayEquipment): Int =
        plan.slots.count { slot ->
            ExerciseSelector.choose(slot.pattern, slot.role, ctx, equipment, SessionUse(), salt = 0, region = plan.region, tag = slot.tag) != null
        }

    /**
     * Una sesión específica (empuje, tirón, pierna o torso) a la que el material de su día deja menos de 3 huecos, o menos de
     * la mitad, se cambia por la sesión de cuerpo completo con más huecos posibles: un «Tirón» sin barra, bandas ni mancuernas
     * sería un solo ejercicio y media hora de caminata. Las notas de límites siguen diciendo qué material lo arreglaría.
     */
    private fun repairIfInfeasible(ctx: GenContext, plan: SessionPlan, equipment: DayEquipment, index: Int, used: MutableSet<String>): SessionPlan {
        val specific = plan.region == SessionRegion.PUSH || plan.region == SessionRegion.PULL ||
            plan.region == SessionRegion.LEGS || plan.region == SessionRegion.UPPER ||
            (ctx.mode.isDiscipline && plan.kind == RoutineSessionKind.STRENGTH && plan.region == SessionRegion.FULL)
        if (!specific || plan.slots.isEmpty()) return plan
        val own = feasibleSlots(ctx, plan, equipment)
        if (own >= 3 && own * 2 >= plan.slots.size) return plan
        val options = WeekPlanner.fullBodyFallbacks()
        val rotated = options.indices.map { options[(it + index) % options.size] }
        val scored = rotated.map { it to feasibleSlots(ctx, it, equipment) }
        val top = scored.maxOf { it.second }
        // Entre las que casi igualan a la mejor se prefiere una que la semana aún no use (A y B alternan en vez de repetirse).
        val best = (scored.firstOrNull { (candidate, count) -> count >= top - 1 && candidate.key !in used } ?: scored.first { it.second == top }).first
        if (feasibleSlots(ctx, best, equipment) <= own) return plan
        used += best.key
        val prefix = if (plan.title.startsWith("Fuerza · ")) "Fuerza · " else ""
        val renamed = best.copy(key = "${best.key}-r$index", title = prefix + best.title)
        return WeekPlanner.applyPriorities(listOf(renamed), ctx).single()
    }

    // ─── Validación ────────────────────────────────────────────────────────────────────────────────────────

    private fun validate(request: RoutineRequest) {
        if (request.weekdays.isEmpty()) throw RoutineGenerationException("Hace falta al menos un día de entreno.")
        if (request.weekdays.any { it !in 1..7 }) throw RoutineGenerationException("Los días de entreno van de 1 (lunes) a 7 (domingo).")
        if (request.weekdays.distinct().size != request.weekdays.size) throw RoutineGenerationException("Hay días de entreno repetidos.")
        if (request.targetMinutes !in 20..180) throw RoutineGenerationException("Los minutos por sesión van de 20 a 180.")
        if (request.weekStartDay !in 1..7) throw RoutineGenerationException("El inicio de semana va de 1 (lunes) a 7 (domingo).")
    }

    // ─── Lugares y material ────────────────────────────────────────────────────────────────────────────────

    /** Lugar del día: el asignado si está entre los elegidos; si no, el primero en orden gimnasio, casa, espacios públicos. */
    private fun placeOf(request: RoutineRequest, day: Int): TrainingPlace? {
        val places = request.places
        if (places.isEmpty()) return request.dayPlaces[day]
        request.dayPlaces[day]?.takeIf { it in places }?.let { return it }
        return listOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.PUBLIC).firstOrNull { it in places }
    }

    /**
     * Material del lugar. Con un solo lugar (o sin lugares) manda la disponibilidad declarada; con varios, la propia del
     * lugar y, si no vino, lo que ese lugar aporta de serie: nunca la unión (no se mezclan lugares).
     */
    private fun availabilityFor(request: RoutineRequest, place: TrainingPlace?): EquipmentAvailability {
        if (place == null) return request.availability
        request.availabilityByPlace[place]?.let { return it }
        if (request.places.size <= 1) return request.availability
        return EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(place)), setOf(place))
    }

    private fun programModeOf(mode: RoutineMode): ProgramMode = when (mode) {
        RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.CUSTOM_POWERBUILDING -> ProgramMode.POWERBUILDING
        RoutineMode.CUSTOM_POWERLIFTING, RoutineMode.DISCIPLINE_STRONGMAN, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE -> ProgramMode.POWERLIFTING
        else -> ProgramMode.HYPERTROPHY
    }

    // ─── Identidad estable ─────────────────────────────────────────────────────────────────────────────────

    private fun canonical(a: EquipmentAvailability): String =
        "c=" + a.categories.map { it.name }.sorted().joinToString(",") +
            ";a=" + a.apparatus.entries.map { "${it.key}:${it.value.name}" }.sorted().joinToString(",") +
            ";s=" + a.supports.entries.map { "${it.key}:${it.value.name}" }.sorted().joinToString(",")

    private fun stableProgramId(request: RoutineRequest): String {
        val text = buildString {
            append(request.mode.name).append('|')
            append(request.weekdays.joinToString(",")).append('|')
            append(request.weekStartDay).append('|').append(request.freshestDay ?: 0).append('|')
            append(request.targetMinutes).append('|').append(request.level.name).append('|')
            append(listOf(request.technique, request.consistency, request.strength, request.mobility).joinToString(",") { (it ?: 0).toString() }).append('|')
            append(canonical(request.availability)).append('|')
            append(request.places.map { it.name }.sorted().joinToString(",")).append('|')
            append(request.dayPlaces.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }).append('|')
            append(request.availabilityByPlace.entries.sortedBy { it.key.name }.joinToString(";") { "${it.key.name}=${canonical(it.value)}" }).append('|')
            append(request.priorityMuscles.joinToString(",") { it.name }).append('|')
            append(request.capabilities.entries.sortedBy { it.key.name }.joinToString(",") { "${it.key.name}:${it.value.name}" }).append('|')
            append(request.cardio?.let { "${it.type.name}:${it.minutes}:${it.intensity.name}" } ?: "-").append('|')
            append(request.marks.entries.sortedBy { it.key.name }.joinToString(",") { "${it.key.name}:${it.value}" }).append('|')
            append(request.catalog.catalogRevision).append('|').append(request.variantSeed)
        }
        return "gen-" + Integer.toHexString(fnv1a(text))
    }

    private fun fnv1a(text: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (char in text) {
            hash = hash xor char.code
            hash *= 16777619
        }
        return hash
    }
}
