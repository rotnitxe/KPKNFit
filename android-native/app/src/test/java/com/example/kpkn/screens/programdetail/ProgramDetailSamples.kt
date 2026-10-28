package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.design.ProgramDayUi
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.screens.programdetail.design.ProgramPhaseBlockUi
import com.example.kpkn.screens.programdetail.design.ProgramPhaseWeekUi
import com.example.kpkn.screens.programdetail.design.ProgramSessionUi
import com.example.kpkn.screens.programdetail.design.ProgramVolumeAssociation
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.ProgramVolumeDiscomfort
import com.example.kpkn.screens.programdetail.design.ProgramVolumeLine
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeScope
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSplit
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.programdetail.design.programMetaLine
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockUi
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleLoop
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleWeek
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesModel
import com.example.kpkn.screens.programdetail.design.structure.ProgramLibraryItem
import com.example.kpkn.screens.programdetail.design.structure.ProgramMesoUi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Datos de ejemplo de Program Detail para la hoja de render de la cabecera (P1). Sin datos reales ni inventados fuera de ejemplo. */
internal object ProgramDetailSamples {

    /** Un programa de fuerza de 12 semanas, con portada de estudio en negro y noche. */
    val active: Program = Program(
        id = "generated:strength-muscle",
        name = "Fuerza de base",
        description = "Para empezar",
        coverImage = "kpkn-studio://PLATES/NOCHE",
        mode = ProgramMode.POWERLIFTING,
        weekDays = 4,
    )

    /** El mismo programa con un nombre largo, para ver cómo se encoge el título. */
    val longName: Program = active.copy(name = "Fuerza y masa muscular con acumulación e intensificación de doce semanas")

    /** Un programa simple de una semana que se repite. */
    val simple: Program = active.copy(
        id = "template:simple-1",
        name = "Semana de tres días",
        structure = ProgramStructure.SIMPLE,
        coverImage = "kpkn-studio://WAVES/PAPEL",
        mode = ProgramMode.HYPERTROPHY,
        weekDays = 3,
    )

    /** El estado de la cabecera que pinta [program], con sus textos y su progreso. */
    fun headerState(
        program: Program,
        status: ProgramHeaderStatus,
        primary: ProgramHeaderPrimary,
        weekCount: Int,
        currentWeek: Int?,
        progressLabel: String?,
        repeating: Boolean = false,
    ): ProgramHeaderState = ProgramHeaderState(
        name = program.name,
        status = status,
        meta = programMetaLine(weekCount, repeating, program.weekDays, program.mode),
        identity = coverIdentityForProgram(program),
        photoUrl = null,
        bundled = null,
        weekCount = weekCount,
        currentWeekIndex = currentWeek,
        progressLabel = progressLabel,
        primary = primary,
    )

    // ─── P2: la pestaña Semana (fases, días y sesiones) ───

    /** Un ejercicio de ejemplo con [sets] series de 8 repeticiones; sin catálogo, su nombre sale tal cual. */
    fun exercise(id: String, name: String, sets: Int): Exercise = Exercise(
        id = id,
        name = name,
        sets = List(sets) { ExerciseSet(id = "$id-s$it", targetReps = 8) },
    )

    /** Una sesión de ejemplo en el día [day] (1 = lunes), con sus ejercicios. */
    fun session(id: String, name: String, day: Int, exercises: List<Exercise>, optional: Boolean = false): Session = Session(
        id = id,
        name = name,
        dayOfWeek = day,
        assignedDays = listOf(day),
        exercises = exercises,
        requirement = if (optional) SessionRequirement.OPTIONAL else SessionRequirement.REQUIRED,
    )

    /** Torso A: tres ejercicios. */
    val torsoA: Session = session(
        id = "torso-a",
        name = "Torso A",
        day = 1,
        exercises = listOf(exercise("bench", "Press banca", 4), exercise("row", "Remo con barra", 4), exercise("press", "Press militar", 3)),
    )

    /** Pierna A, para el segundo día. */
    val piernaA: Session = session(
        id = "pierna-a",
        name = "Pierna A",
        day = 2,
        exercises = listOf(exercise("squat", "Sentadilla", 5), exercise("rdl", "Peso muerto rumano", 3)),
    )

    /** Veinte series en un solo ejercicio: el estimador da otro total que series × 3 (60 min). */
    val twentySets: Session = session(
        id = "curl-20",
        name = "Brazos largos",
        day = 3,
        exercises = listOf(exercise("curl", "Curl de bíceps", 20)),
    )

    /** La fila de una sesión tal como la arma la pantalla: los minutos salen del estimador común. */
    fun sessionUi(
        session: Session,
        done: Boolean = false,
        customized: Boolean = false,
        restorable: Boolean = false,
        optionalDate: String? = null,
        confirmed: Boolean = false,
    ): ProgramSessionUi = ProgramSessionUi(
        session = session,
        exerciseCount = session.allExercises().size,
        minutes = SessionDurationEstimator.estimate(session).totalMinutes,
        done = done,
        customized = customized,
        restorable = restorable,
        optionalDateIso = optionalDate,
        optionalConfirmed = confirmed,
    )

    /** Un día de la semana: [number] es su fecha si hay calendario (null sin calendario). */
    fun day(
        id: Int,
        name: String,
        short: String,
        sessions: List<ProgramSessionUi>,
        number: String? = null,
        today: Boolean = false,
        start: Boolean = false,
    ): ProgramDayUi = ProgramDayUi(
        id = id,
        name = name,
        short = short,
        dateNumber = number,
        sessions = sessions,
        isToday = today,
        isStartDay = start,
        isOutsideProgram = false,
        isDropTarget = false,
        dateEditable = false,
    )

    /** Tres bloques para el módulo «Fases»: acumulación con dos semanas hechas, intensificación con la actual y la competición. */
    fun phaseBlocks(): List<ProgramPhaseBlockUi> = listOf(
        ProgramPhaseBlockUi(
            id = "b1",
            eyebrow = "Bloque 1 · Acumulación",
            phase = ProgramPhase.ACCUMULATION,
            weeks = (1..4).map { n -> ProgramPhaseWeekUi(id = "w$n", number = n, isCurrent = false, isDone = n <= 2) },
        ),
        ProgramPhaseBlockUi(
            id = "b2",
            eyebrow = "Bloque 2 · Intensificación",
            phase = ProgramPhase.INTENSIFICATION,
            weeks = (5..8).map { n -> ProgramPhaseWeekUi(id = "w$n", number = n, isCurrent = n == 5, isDone = false) },
        ),
        ProgramPhaseBlockUi(
            id = "b3",
            eyebrow = "Bloque 3 · Competición",
            phase = ProgramPhase.COMPETITION,
            weeks = (9..10).map { n -> ProgramPhaseWeekUi(id = "w$n", number = n, isCurrent = false, isDone = false) },
        ),
    )

    /** Zonas de ejemplo de un músculo: mínimo efectivo, máximo adaptativo y máximo recuperable (P4). */
    private fun zonesOf(min: Int, adaptive: Int, recoverable: Int): ProgramVolumeZones =
        ProgramVolumeZones(minEffective = min, maxAdaptive = adaptive, maxRecoverable = recoverable)

    /**
     * Una semana de diez músculos en todos los estados de volumen (P4): exceso por zonas y por la analítica, sobrecarga funcional,
     * alto tolerable, óptimo (dos rótulos) y subentrenado. Viene ordenada por series, como la pestaña.
     */
    val volumeWeek: List<ProgramVolumeMuscle> = listOf(
        ProgramVolumeMuscle("Cuádriceps", 26.0, zonesOf(8, 16, 22), real = 22.0,   scaleMax = 26.0),
        ProgramVolumeMuscle("Dorsales", 18.0, zonesOf(10, 16, 22), real = 14.0,   scaleMax = 22.0),
        ProgramVolumeMuscle("Pectorales", 16.0, zonesOf(8, 16, 22), real = 16.0,   scaleMax = 22.0),
        ProgramVolumeMuscle("Trapecio", 12.0, zonesOf(6, 12, 18), real = null,   scaleMax = 18.0),
        ProgramVolumeMuscle("Deltoides", 12.0, zonesOf(8, 14, 20), real = null,   scaleMax = 20.0),
        ProgramVolumeMuscle("Pantorrillas", 10.0, zonesOf(6, 12, 18), real = null,   scaleMax = 18.0),
        ProgramVolumeMuscle("Bíceps", 8.0, zonesOf(8, 12, 18), real = 6.0,   scaleMax = 18.0),
        ProgramVolumeMuscle("Isquiosurales", 6.0, zonesOf(8, 14, 20), real = null,   scaleMax = 20.0),
        ProgramVolumeMuscle("Antebrazo", 4.0, zonesOf(6, 16, 20), real = null,   scaleMax = 20.0),
        ProgramVolumeMuscle("Abdomen", 2.0, zonesOf(4, 10, 16), real = null,   scaleMax = 16.0),
    )

    /** La misma semana sin calibrar: sin zonas, todas las barras comparten la escala de la semana. */
    val volumeUncalibrated: List<ProgramVolumeMuscle> = volumeWeek.take(6).map { muscle ->
        muscle.copy(zones = null, scaleMax = 26.0, )
    }

    /** El alcance de una semana, con sus tres tipos. */
    val volumeScopeWeek = ProgramVolumeScope(
        label = "S3 · Acumulación",
        detail = "Conteo exacto de esa semana.",
        kinds = listOf("Semana", "Bloque", "Programa"),
        selectedKind = 0,
        canPrevious = true,
        canNext = true,
    )

    /** El desglose de pectorales: tres ejercicios y sin cabezas. */
    val volumeBreakdownPectorales = ProgramVolumeBreakdown(
        exercises = listOf(
            ProgramVolumeLine("Press banca", 6.0),
            ProgramVolumeLine("Aperturas en polea", 4.0),
            ProgramVolumeLine("Fondos", 3.0),
        ),
        subMuscles = emptyList(),
    )

    /** El reparto de la semana de ejemplo, con empuje por delante. */
    val volumeSplit = ProgramVolumeSplit(push = 62.0, pull = 48.0, legs = 40.0)

    /** Molestias de ejemplo para el resumen. */
    val volumeDiscomforts = listOf(ProgramVolumeDiscomfort("Hombro derecho", 3), ProgramVolumeDiscomfort("Lumbar", 1))

    /** Molestias asociadas a un ejercicio de ejemplo. */
    val volumeAssociations = listOf(ProgramVolumeAssociation("Press militar", "Pinchazo en hombro", 2))

    /** Tres sesiones de ejemplo de una semana, en días distintos: lo que una aplicación con reemplazo borra (P5). */
    private fun splitSessions(week: Int): List<Session> = listOf(
        Session(id = "split-$week-a", name = "Torso A", dayOfWeek = 1),
        Session(id = "split-$week-b", name = "Pierna A", dayOfWeek = 3),
        Session(id = "split-$week-c", name = "Torso B", dayOfWeek = 5),
    )

    /** Un programa de ejemplo con [weeks] semanas de tres sesiones cada una, en un solo bloque, para las hojas de aplicar (P5). */
    fun splitProgram(weeks: Int, startDay: Int = 1): Program = Program(
        id = "split-sample",
        name = "Fuerza de base",
        startDay = startDay,
        macrocycles = listOf(
            Macrocycle(
                id = "split-macro",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "split-block",
                        name = "Bloque 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "split-meso",
                                name = "Mesociclo 1",
                                weeks = (1..weeks).map { week ->
                                    ProgramWeek(id = "split-w$week", name = "Semana $week", sessions = splitSessions(week))
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** Un reparto propio de ejemplo, con sus siete días y un día de descanso de cada lado (P5). */
    val splitCustomPattern: List<String> = listOf("Torso", "Pierna", "Descanso", "Empuje", "Tirón", "Pierna", "Descanso")
}

/**
 * Datos de ejemplo de la pestaña Estructura (P3a) para la hoja de render y las pruebas de las hojas. Hoy es fijo (13 oct 2026) para que
 * la cuenta atrás y el calendario salgan siempre iguales.
 */
internal object ProgramStructureSamples {

    /** El día de hoy de los ejemplos. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    /** Dos fechas clave que no son la competición. */
    val keyDates: List<ProgramKeyDate> = listOf(
        ProgramKeyDate(
            id = "key_exams",
            title = "Examen de química",
            type = KeyDateType.EXAMS,
            startDate = "2026-10-20",
            endDate = "2026-10-24",
        ),
        ProgramKeyDate(
            id = "key_vacation",
            title = "Vacaciones de invierno",
            type = KeyDateType.VACATION,
            startDate = "2026-12-21",
            endDate = "2027-01-04",
        ),
    )

    /** Avisos del motor de ejemplo: la competición cae fuera de las semanas y el término manual queda antes del proyectado. */
    val engineMessages: List<String> = listOf(
        "La competición cae fuera de las semanas del programa. Mueve la fecha o añade semanas.",
        "La fecha de fin que pusiste llega antes de que acabe el programa. Quita semanas o mueve la fecha.",
    )

    // ─── P3b: los módulos de bloque de la pestaña Estructura ───

    /** Una semana del módulo de ejemplo: la de número [number] (id `s<number>`), con la semana [current] en curso y las anteriores hechas. */
    private fun blockWeek(number: Int, current: Int, loop: Boolean = false, competition: Boolean = false): ProgramPhaseWeekUi =
        ProgramPhaseWeekUi(
            id = "s$number",
            number = number,
            isCurrent = number == current,
            isDone = number < current,
            isLoop = loop,
            isCompetition = competition,
        )

    /** Acumulación de seis semanas con dos mesociclos; la semana 4 es la actual y las tres primeras están hechas. */
    val acumulacion: ProgramBlockUi = ProgramBlockUi(
        id = "bloque-acumulacion",
        name = "Acumulación 1",
        macroIndex = 0,
        blockIndex = 0,
        phaseLabel = "Acumulación",
        phase = ProgramPhase.ACCUMULATION,
        mesocycles = listOf(
            ProgramMesoUi(name = "Mesociclo 1", goalLabel = "Acumulación"),
            ProgramMesoUi(name = "Mesociclo 2", goalLabel = "Realización"),
        ),
        weeks = (1..6).map { blockWeek(number = it, current = 4) },
        schemeLabel = "Carga lineal",
    )

    /** Intensificación de cuatro semanas, con un solo mesociclo (no tiene fila propia). */
    val intensificacion: ProgramBlockUi = ProgramBlockUi(
        id = "bloque-intensificacion",
        name = "Intensificación",
        macroIndex = 0,
        blockIndex = 1,
        phaseLabel = "Intensificación",
        phase = ProgramPhase.INTENSIFICATION,
        mesocycles = listOf(ProgramMesoUi(name = "Mesociclo 1", goalLabel = "Intensificación")),
        weeks = (7..10).map { blockWeek(number = it, current = 4) },
        schemeLabel = "Top set + back-off",
    )

    /** Realización de tres semanas con una de ciclo: lleva la marca de repetición. */
    val realizacion: ProgramBlockUi = ProgramBlockUi(
        id = "bloque-realizacion",
        name = "Realización",
        macroIndex = 0,
        blockIndex = 2,
        phaseLabel = "Realización",
        phase = ProgramPhase.REALIZATION,
        mesocycles = listOf(ProgramMesoUi(name = "Mesociclo 1", goalLabel = "Realización")),
        weeks = listOf(blockWeek(11, 4), blockWeek(12, 4, loop = true), blockWeek(13, 4)),
    )

    /** Bloque de competición de dos semanas: la segunda es la competición y lleva bandera. */
    val competicion: ProgramBlockUi = ProgramBlockUi(
        id = "bloque-competicion",
        name = "Puesta a punto",
        macroIndex = 0,
        blockIndex = 3,
        phaseLabel = "Competición",
        phase = ProgramPhase.COMPETITION,
        mesocycles = listOf(ProgramMesoUi(name = "Mesociclo 1", goalLabel = "Descarga")),
        weeks = listOf(blockWeek(14, 4), blockWeek(15, 4, competition = true)),
    )

    /** Los cuatro bloques del programa de ejemplo, en su orden. */
    val programaCuatroBloques: List<ProgramBlockUi> = listOf(acumulacion, intensificacion, realizacion, competicion)

    /** El bloque único de un programa simple con ciclo: sin fase, con «Semana que se repite» y «Ciclo 5». */
    val simpleCiclo: ProgramBlockUi = ProgramBlockUi(
        id = "bloque-simple",
        name = "Bloque 1",
        macroIndex = 0,
        blockIndex = 0,
        phaseLabel = null,
        phase = null,
        mesocycles = listOf(ProgramMesoUi(name = "Mesociclo 1", goalLabel = "Acumulación")),
        weeks = listOf(blockWeek(1, 2), blockWeek(2, 2, loop = true), blockWeek(3, 2)),
        cycleLabel = "Ciclo 5",
    )

    /** El bloque de acumulación como modelo de datos, para la hoja de edición: con descripción, objetivo y progresión. */
    val bloqueEditable: Block = Block(
        id = "bloque-acumulacion",
        name = "Acumulación 1",
        description = "Volumen alto con técnica estable.",
        mesocycles = listOf(
            Mesocycle(id = "meso-1", name = "Mesociclo 1", goal = MesocycleGoal.ACCUMULATION, weeks = listOf(ProgramWeek(id = "s1", name = "Semana 1"))),
            Mesocycle(id = "meso-2", name = "Mesociclo 2", goal = MesocycleGoal.REALIZATION, weeks = listOf(ProgramWeek(id = "s4", name = "Semana 4"))),
        ),
        goal = BlockGoal.ACCUMULATION,
        progressionScheme = BlockProgressionScheme.LINEAR_LOAD,
    )

    /** La semana 4 como modelo de datos, para la hoja de semana, con su descripción. */
    val semanaEditable: ProgramWeek = ProgramWeek(id = "s4", name = "Semana 4", description = "Pico de volumen antes del descanso.")
}

/** Datos de ejemplo de la hoja de máximos y del TM (P6): un test de fuerza y la estimación del historial. Sin datos reales. */
internal object ProgramOneRmSamples {
    /** Los 1RM del test en kg: sentadilla 142,5, banca 100 y peso muerto 180. */
    val testedKg: Map<LiftMark, Double> = mapOf(
        LiftMark.SQUAT to 142.5,
        LiftMark.BENCH to 100.0,
        LiftMark.DEADLIFT to 180.0,
    )

    /** Un TM ya guardado: los tres 1RM y un press militar. */
    val savedProfile = PowerliftingProfile(squat1RM = 142.5, bench1RM = 100.0, deadlift1RM = 180.0, overhead1RM = 62.5)

    /** La estimación del historial que ofrece «Calcular desde mi historial». */
    val historyEstimate = PowerliftingProfile(squat1RM = 145.0, bench1RM = 102.5, deadlift1RM = 185.0)
}

/**
 * Datos de ejemplo de las hojas de Estructura de P3c (ciclos, ruta, plantillas e historial) para la hoja de render y las pruebas. Hoy es
 * fijo (13 oct 2026), como en [ProgramStructureSamples]. Son datos simples: las hojas no conocen el motor.
 */
internal object ProgramStructureSheetSamples {

    /** El día de hoy de los ejemplos. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    /** Dos ciclos activos: una descarga cada 4 y un test de 1RM cada 8. */
    val cycleLoops: List<ProgramCycleLoop> = listOf(
        ProgramCycleLoop(id = "loop-deload", title = "Descarga", type = LoopType.DELOAD, cadence = 4, cancelled = false),
        ProgramCycleLoop(id = "loop-test", title = "Test 1RM", type = LoopType.ONE_RM_TEST, cadence = 8, cancelled = false),
    )

    /** Una semana de ciclo próxima: la descarga del ciclo 5, movida desde el 4. */
    val cycleWeeks: List<ProgramCycleWeek> = listOf(
        ProgramCycleWeek(
            occurrenceId = "occ-deload-5",
            loopTitle = "Descarga",
            loopType = LoopType.DELOAD,
            weekName = "Semana 9",
            cycle = 5,
            originCycle = 4,
            dayLabel = "Jueves",
            countdown = "3 días",
            blockId = "block-3",
            weekId = "week-9",
        ),
    )

    /** La hoja de Ciclos: dos ciclos activos, una semana especial y el aviso de dos eventos antiguos por migrar. */
    val cycles: ProgramCyclesModel = ProgramCyclesModel(
        currentCycle = 5,
        loops = cycleLoops,
        weeks = cycleWeeks,
        legacyEventCount = 2,
        collisionLines = emptyList(),
    )

    /** Tres plantillas de la biblioteca, con sus días por semana. */
    val templates: List<ProgramLibraryItem> = listOf(
        ProgramLibraryItem(key = "fuerza-4", name = "Fuerza 4 días", detail = "4 sesiones por semana"),
        ProgramLibraryItem(key = "torso-pierna", name = "Torso y pierna", detail = "De 3 a 4 sesiones por semana"),
        ProgramLibraryItem(key = "competicion-16", name = "Competición a 16 semanas", detail = "De 3 a 5 sesiones por semana"),
    )

    /** Dos métodos de progresión, con la primera frase de su descripción. */
    val methods: List<ProgramLibraryItem> = listOf(
        ProgramLibraryItem(key = "protocolo-sbd", name = "Protocolo de fuerza SBD", detail = "Sentadilla, banca y peso muerto con cargas por porcentaje del máximo"),
        ProgramLibraryItem(key = "protocolo-hipertrofia", name = "Hipertrofia por bloques", detail = "Acumulación de volumen, después intensificación y una descarga corta"),
    )

    /** Una hora de copia en la zona del móvil: así «mar 13 oct · 10:42» sale igual en cualquier zona. */
    private fun savedAt(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Tres copias del programa, de la más reciente a la más antigua. */
    val copies: List<ProgramSnapshot> = listOf(
        ProgramSnapshot(
            id = "snap-3",
            programId = "program-1",
            program = ProgramDetailSamples.active,
            savedAtMs = savedAt(13, 10, 42),
            reason = "Antes de \"Fuerza 4 días\"",
        ),
        ProgramSnapshot(
            id = "snap-2",
            programId = "program-1",
            program = ProgramDetailSamples.active,
            savedAtMs = savedAt(9, 18, 5),
            reason = "Antes de \"Descarga de 4 semanas\"",
        ),
        ProgramSnapshot(
            id = "snap-1",
            programId = "program-1",
            program = ProgramDetailSamples.active,
            savedAtMs = savedAt(2, 8, 30),
            reason = "Antes de \"Hipertrofia 4 días\"",
        ),
    )
}
