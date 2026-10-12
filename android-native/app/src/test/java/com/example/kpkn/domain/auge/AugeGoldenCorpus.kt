package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.ZoneId

/** Caso del corpus: lo que el render necesita para producir sus líneas. */
internal class GoldenCase(
    val id: String,
    val title: String,
    val settings: Settings,
    /** Instante de referencia de las horas `t=…h` del caso. */
    val origin: Long,
    val history: List<WorkoutLog>,
    val wellbeing: DailyWellbeingLog?,
    /** Sesión cuyo drenaje y `muscularImpactV2` se imprimen (la última del historial en C). */
    val described: WorkoutLog?,
    val instants: List<Long>,
    val preview: GoldenPreview?,
)

/** Vista previa del cierre (grupo E): historial previo, sesión previsualizada y su identidad de entrada. */
internal class GoldenPreview(
    val baseHistory: List<WorkoutLog>,
    val previewLog: WorkoutLog,
    val finishMs: Long,
    val operationId: String,
    val inputHash: String,
)

/**
 * Corpus del máster dorado (WP 0.2). Sin aleatoriedad ni reloj real: todo se construye desde el
 * instante base que recibe [build]; las horas de evaluación son absolutas. Zona UTC.
 */
internal object AugeGoldenCorpus {
    val ZONE: ZoneId = ZoneId.of("UTC")
    const val HORA_MS = 3_600_000L
    const val DIA_MS = 24L * HORA_MS

    /** Base congelada del golden (lunes 2025-03-03 10:00 UTC), muy anterior al reloj real a propósito. */
    val BASE_MS: Long = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()

    /** Desplazamiento de la prueba de invariancia: 52 semanas exactas (mismo día de la semana y hora). */
    const val DESPLAZAMIENTO_MS = 364L * DIA_MS

    /** Base de ejercicios propia del corpus, con fichas heterogéneas (compuesto/aislamiento, axial 0 a 1). */
    val EXERCISE_DB: Map<String, ExerciseMuscleInfo> = listOf(
        ficha("squat", "Sentadilla", "barra", 4.2, 4.5, 1.0, 1.0,
            principal("Cuádriceps"), secundario("Glúteos", 0.5), estabilizador("Erectores Espinales")),
        ficha("deadlift", "Peso muerto", "barra", 4.5, 4.8, 1.2, 1.0,
            principal("Isquiosurales"), secundario("Glúteos"), secundario("Erectores Espinales", 0.6),
            estabilizador("Trapecio")),
        ficha("rdl", "Peso muerto rumano", "barra", 3.5, 3.4, 0.9, 0.8,
            principal("Isquiosurales"), secundario("Glúteos"), estabilizador("Erectores Espinales")),
        ficha("leg-press", "Prensa", "máquina", 3.4, 2.6, 0.3, 0.0,
            principal("Cuádriceps"), secundario("Glúteos")),
        ficha("leg-extension", "Extensión de cuádriceps", "máquina", 2.0, 1.5, 0.1, 0.0,
            principal("Cuádriceps")),
        ficha("leg-curl", "Curl femoral", "máquina", 2.0, 1.5, 0.1, 0.0, principal("Isquiosurales")),
        ficha("calf-raise", "Gemelos", "máquina", 1.8, 1.2, 0.2, 0.2, principal("Pantorrillas")),
        ficha("bench", "Press banca", "barra", 3.2, 3.5, 0.35, 0.0,
            principal("Pectorales"), secundario("Tríceps", 0.45), secundario("Deltoides", 0.35)),
        ficha("incline", "Press inclinado", "mancuernas", 3.0, 3.0, 0.3, 0.0,
            principal("Pectorales"), secundario("Deltoides", 0.45), secundario("Tríceps", 0.35)),
        ficha("ohp", "Press militar", "barra", 3.4, 3.6, 0.6, 0.5,
            principal("Deltoides"), secundario("Tríceps", 0.45), secundario("Trapecio")),
        ficha("fly", "Aperturas", "mancuernas", 2.0, 1.5, 0.1, 0.0, principal("Pectorales")),
        ficha("row", "Remo", "barra", 3.4, 3.2, 0.7, 0.6,
            principal("Dorsales"), secundario("Bíceps"), secundario("Trapecio"),
            estabilizador("Erectores Espinales")),
        ficha("pulldown", "Jalón al pecho", "polea", 2.8, 2.4, 0.2, 0.0,
            principal("Dorsales"), secundario("Bíceps")),
        ficha("pullup-assisted", "Dominada asistida", "máquina", 2.8, 2.4, 0.2, 0.0,
            principal("Dorsales"), secundario("Bíceps")),
        ficha("curl", "Curl de bíceps", "mancuernas", 1.5, 1.2, 0.1, 0.0, principal("Bíceps")),
        ficha("triceps-ext", "Extensión de tríceps", "polea", 1.5, 1.2, 0.1, 0.0, principal("Tríceps")),
        ficha("lat-raise", "Elevaciones laterales", "mancuernas", 1.5, 1.2, 0.1, 0.0,
            principal("Deltoides")),
        ficha("plank", "Plancha", "peso corporal", 1.5, 1.3, 0.3, 0.3,
            principal("Core"), secundario("Abdomen")),
        ficha("lunge-uni", "Zancada", "mancuernas", 3.2, 3.0, 0.4, 0.3,
            principal("Cuádriceps"), secundario("Glúteos")),
    ).associateBy { it.id }

    private fun principal(musculo: String) = InvolvedMuscle(musculo, MuscleRole.PRIMARY, 1.0)

    private fun secundario(musculo: String, peso: Double? = null) =
        InvolvedMuscle(musculo, MuscleRole.SECONDARY, peso)

    private fun estabilizador(musculo: String) = InvolvedMuscle(musculo, MuscleRole.STABILIZER, null)

    private fun ficha(
        id: String,
        nombre: String,
        equipo: String,
        efc: Double,
        cnc: Double,
        ssc: Double,
        axial: Double,
        vararg musculos: InvolvedMuscle,
    ) = ExerciseMuscleInfo(
        id = id,
        name = nombre,
        equipment = equipo,
        efc = efc,
        cnc = cnc,
        ssc = ssc,
        axialLoadFactor = axial,
        involvedMuscles = musculos.toList(),
    )

    /** Plantilla de sesión: nombre y ejercicios completados (sin tiempo: el instante lo pone el caso). */
    private class Plantilla(val nombre: String, val ejercicios: List<CompletedExercise>)

    private fun tanda(prefijo: String, n: Int, kg: Double, reps: Int, rpe: Double): List<CompletedSet> =
        List(n) { i -> CompletedSet(id = "$prefijo-$i", weight = kg, reps = reps, rpe = rpe) }

    private fun ejercicio(
        id: String,
        nombre: String,
        descanso: Int,
        series: List<CompletedSet>,
        supersetId: String? = null,
        supersetCuenta: Int = 1,
        supersetRondas: Int? = null,
        supersetEntre: Int? = null,
        supersetDespues: Int? = null,
    ) = CompletedExercise(
        exerciseId = id,
        exerciseName = nombre,
        exerciseDbId = id,
        sets = series,
        restTime = descanso,
        supersetId = supersetId,
        supersetExerciseCount = supersetCuenta,
        supersetRounds = supersetRondas,
        supersetRestBetween = supersetEntre,
        supersetRestAfter = supersetDespues,
    )

    // Grupo A (una sesión) y piezas de los grupos B–E. Pesos y repeticiones razonables, fijados aquí.
    private val PIERNAS = Plantilla(
        "Piernas pesadas",
        listOf(
            ejercicio("squat", "Sentadilla", 180, tanda("sq", 5, 140.0, 5, 8.5)),
            ejercicio("rdl", "Peso muerto rumano", 150, tanda("rdl", 3, 110.0, 8, 8.0)),
            ejercicio("leg-press", "Prensa", 120, tanda("lp", 3, 220.0, 10, 8.0)),
            ejercicio("calf-raise", "Gemelos", 60, tanda("calf", 4, 80.0, 12, 8.0)),
        ),
    )
    private val EMPUJE = Plantilla(
        "Empuje superior",
        listOf(
            ejercicio("bench", "Press banca", 150, tanda("bn", 4, 90.0, 6, 8.5)),
            ejercicio("incline", "Press inclinado", 120, tanda("inc", 3, 30.0, 10, 8.0)),
            ejercicio("ohp", "Press militar", 120, tanda("ohp", 3, 50.0, 8, 8.0)),
            ejercicio("triceps-ext", "Extensión de tríceps", 60, tanda("tri", 3, 25.0, 12, 8.0)),
            ejercicio("lat-raise", "Elevaciones laterales", 60, tanda("lat", 3, 8.0, 15, 8.0)),
        ),
    )
    private val TIRON = Plantilla(
        "Tirón con peso muerto",
        listOf(
            ejercicio("deadlift", "Peso muerto", 180, tanda("dl", 3, 170.0, 4, 8.5)),
            ejercicio("row", "Remo", 120, tanda("row", 4, 70.0, 8, 8.0)),
            ejercicio("pulldown", "Jalón al pecho", 90, tanda("pd", 3, 60.0, 10, 8.0)),
            ejercicio("curl", "Curl de bíceps", 60, tanda("curl", 3, 15.0, 12, 8.0)),
        ),
    )
    private val BANCA_LIGERA = Plantilla(
        "Banca ligera",
        listOf(ejercicio("bench", "Press banca", 120, tanda("bl", 3, 60.0, 10, 7.0))),
    )
    private val SOLO_CURL = Plantilla(
        "Solo curl",
        listOf(ejercicio("curl", "Curl de bíceps", 60, tanda("cu", 10, 12.0, 10, 9.0))),
    )
    private val PIERNA_VOLUMEN = Plantilla(
        "Pierna de volumen",
        listOf(
            ejercicio("leg-press", "Prensa", 120, tanda("lp6", 6, 180.0, 12, 9.0)),
            ejercicio("leg-extension", "Extensión de cuádriceps", 90, tanda("le", 5, 40.0, 15, 9.0)),
            ejercicio("squat", "Sentadilla", 150, tanda("sq8", 5, 120.0, 8, 9.0)),
            ejercicio("leg-curl", "Curl femoral", 90, tanda("lc", 4, 35.0, 12, 9.0)),
        ),
    )
    private val AL_FALLO = Plantilla(
        "Banca al fallo",
        listOf(
            ejercicio(
                "bench", "Press banca", 150,
                listOf(
                    CompletedSet(
                        id = "f-0", weight = 90.0, reps = 8, rpe = 10.0, isFailure = true,
                        dropSets = listOf(DropSetData(weight = 70.0, reps = 6)),
                        restPauses = listOf(RestPauseData(restTime = 15, reps = 3)),
                    ),
                    CompletedSet(
                        id = "f-1", weight = 90.0, reps = 6, rpe = 10.0, isFailure = true,
                        dropSets = listOf(DropSetData(weight = 65.0, reps = 4)),
                    ),
                    CompletedSet(
                        id = "f-2", weight = 90.0, reps = 5, rpe = 9.5,
                        restPauses = listOf(
                            RestPauseData(restTime = 20, reps = 2),
                            RestPauseData(restTime = 20, reps = 1),
                        ),
                    ),
                ),
            ),
        ),
    )
    private val SUPERSERIE = Plantilla(
        "Superserie de empuje y tirón",
        listOf(
            ejercicio(
                "bench", "Press banca", 90, tanda("ssb", 4, 80.0, 8, 8.0),
                supersetId = "ss-1", supersetCuenta = 2, supersetRondas = 4, supersetEntre = 30, supersetDespues = 90,
            ),
            ejercicio(
                "row", "Remo", 90, tanda("ssr", 4, 60.0, 8, 8.0),
                supersetId = "ss-1", supersetCuenta = 2, supersetRondas = 4, supersetEntre = 30, supersetDespues = 90,
            ),
        ),
    )
    private val UNILATERAL = Plantilla(
        "Zancadas unilaterales",
        listOf(
            ejercicio(
                "lunge-uni", "Zancada", 90,
                (0 until 4).flatMap { i ->
                    listOf(
                        CompletedSet(id = "lu-izq-$i", weight = 20.0, reps = 8, rpe = 8.0, side = "left"),
                        CompletedSet(id = "lu-der-$i", weight = 20.0, reps = 8, rpe = 8.0, side = "right"),
                    )
                },
            ),
        ),
    )
    private val PLANCHA_ASISTIDA = Plantilla(
        "Plancha y dominadas asistidas",
        listOf(
            ejercicio(
                "plank", "Plancha", 60,
                List(3) { i -> CompletedSet(id = "pl-$i", timeSeconds = 45, reps = 0, rpe = 8.0) },
            ),
            ejercicio(
                "pullup-assisted", "Dominada asistida", 90,
                List(3) { i -> CompletedSet(id = "pa-$i", weight = 0.0, reps = 8, rpe = 8.0, assistedReps = 3) },
            ),
        ),
    )
    private val PESO_MUERTO_PESADO = Plantilla(
        "Peso muerto pesado",
        listOf(
            ejercicio(
                "deadlift", "Peso muerto", 240,
                listOf(
                    CompletedSet(id = "dh-0", weight = 200.0, reps = 1, rpe = 9.5),
                    CompletedSet(id = "dh-1", weight = 180.0, reps = 3, rpe = 9.0),
                    CompletedSet(id = "dh-2", weight = 180.0, reps = 3, rpe = 9.0),
                ),
            ),
        ),
    )
    private val CALENTAMIENTO_RPE5 = Plantilla(
        "Calentamiento y series suaves",
        listOf(
            ejercicio(
                "bench", "Press banca", 150,
                listOf(
                    CompletedSet(id = "wu-0", weight = 40.0, reps = 10, rpe = 5.0, isWarmup = true),
                    CompletedSet(id = "wu-1", weight = 60.0, reps = 6, rpe = 6.0, isWarmup = true),
                ) + tanda("bw", 3, 90.0, 6, 8.5),
            ),
            ejercicio("row", "Remo", 90, tanda("r5", 2, 40.0, 10, 5.0)),
        ),
    )
    private val MODERADA = Plantilla(
        "Sesión moderada",
        listOf(
            ejercicio("bench", "Press banca", 120, tanda("mb", 3, 70.0, 8, 7.5)),
            ejercicio("row", "Remo", 120, tanda("mr", 3, 55.0, 8, 7.5)),
        ),
    )
    private val MIXTA = Plantilla("Piernas y empuje", PIERNAS.ejercicios + EMPUJE.ejercicios)
    private val ENORME = Plantilla("Pierna enorme", PIERNA_VOLUMEN.ejercicios + PIERNAS.ejercicios)

    /** Réplica de producción (`WorkoutFinishController`, líneas 236-266): impacto V2 con capacidades de cierre. */
    private fun crearSesion(
        id: String,
        plantilla: Plantilla,
        atMs: Long,
        previas: List<WorkoutLog>,
        ajustes: Settings,
    ): WorkoutLog {
        val iso = Instant.ofEpochMilli(atMs).toString()
        val cache = AugeAdaptiveCache()
        val entrada = MuscularSessionImpactEngine.fromCompletedExercises(
            completedExercises = plantilla.ejercicios,
            completionInstantIso = iso,
            exerciseDb = EXERCISE_DB,
            settings = ajustes,
            adaptiveCache = cache,
        )
        val inicial = MuscularSessionImpactEngine.evaluate(
            input = entrada,
            exerciseDb = EXERCISE_DB,
            settings = ajustes,
            adaptiveCache = cache,
        )
        val capacidades = AugeMuscleCapacityEngine.capacitiesFor(
            muscles = inicial.involvedVolumeMuscles,
            history = previas,
            settings = ajustes,
            exerciseDb = EXERCISE_DB,
            completionInstantIso = iso,
            adaptiveCache = cache,
        )
        val impacto = MuscularSessionImpactEngine.evaluate(
            input = entrada,
            exerciseDb = EXERCISE_DB,
            settings = ajustes,
            adaptiveCache = cache,
            capacitiesAtCompletion = capacidades,
        )
        return WorkoutLog(
            id = id,
            programId = "golden",
            sessionId = id,
            sessionName = plantilla.nombre,
            date = iso,
            durationMinutes = 60,
            completedExercises = plantilla.ejercicios,
            muscularImpactV2 = impacto,
        )
    }

    /** Sesiones en orden cronológico; cada una ve como historial previo solo a las anteriores. */
    private fun secuencia(prefijo: String, ajustes: Settings, eventos: List<Pair<Long, Plantilla>>): List<WorkoutLog> {
        val salida = mutableListOf<WorkoutLog>()
        eventos.sortedBy { it.first }.forEachIndexed { i, (atMs, plantilla) ->
            salida += crearSesion("$prefijo-s${i + 1}", plantilla, atMs, salida.toList(), ajustes)
        }
        return salida
    }

    /** Casos del corpus para [baseMs]: A01–A12, B01–B07, C01–C09, D01–D04 y E01–E03 (35 casos). */
    fun build(baseMs: Long): List<GoldenCase> {
        val casos = mutableListOf<GoldenCase>()
        val ajustes = Settings()
        val fecha = Instant.ofEpochMilli(baseMs).atZone(ZONE).toLocalDate().toString()
        fun horas(origen: Long, hs: List<Int>): List<Long> = hs.map { origen + it * HORA_MS }
        fun hace(dias: Int): Long = baseMs - dias * DIA_MS

        // Grupo A: una sesión, evaluada tras terminar.
        val horasA = listOf(0, 6, 12, 24, 48, 72, 120, 168)
        val grupoA = listOf(
            Triple("A01", "piernas pesadas", PIERNAS),
            Triple("A02", "empuje superior", EMPUJE),
            Triple("A03", "tirón con peso muerto", TIRON),
            Triple("A04", "banca ligera", BANCA_LIGERA),
            Triple("A05", "solo curl", SOLO_CURL),
            Triple("A06", "pierna de mucho volumen", PIERNA_VOLUMEN),
            Triple("A07", "al fallo con dropsets y rest-pause", AL_FALLO),
            Triple("A08", "superseries de descanso corto", SUPERSERIE),
            Triple("A09", "unilateral", UNILATERAL),
            Triple("A10", "plancha por tiempo y dominada asistida", PLANCHA_ASISTIDA),
            Triple("A11", "peso muerto pesado de pocas repeticiones", PESO_MUERTO_PESADO),
            Triple("A12", "calentamientos y series de RPE 5", CALENTAMIENTO_RPE5),
        )
        for ((id, titulo, plantilla) in grupoA) {
            val sesion = crearSesion("$id-s1", plantilla, baseMs, emptyList(), ajustes)
            casos += GoldenCase(
                id = id,
                title = titulo,
                settings = ajustes,
                origin = baseMs,
                history = listOf(sesion),
                wellbeing = null,
                described = sesion,
                instants = horas(baseMs, horasA),
                preview = null,
            )
        }

        // Grupo B: tipos de atleta con la misma sesión mixta pesada (piernas + empuje).
        val tipos = listOf(
            "B01" to AthleteType.POWERLIFTER,
            "B02" to AthleteType.BODYBUILDER,
            "B03" to AthleteType.POWERBUILDER,
            "B04" to AthleteType.WEIGHTLIFTER,
            "B05" to AthleteType.CALISTHENICS,
            "B06" to AthleteType.HYBRID,
            "B07" to AthleteType.ZERCHER_LIFTER,
        )
        for ((id, tipo) in tipos) {
            val ajustesTipo = Settings(athleteType = tipo)
            val sesion = crearSesion("$id-s1", MIXTA, baseMs, emptyList(), ajustesTipo)
            casos += GoldenCase(
                id = id,
                title = tipo.name,
                settings = ajustesTipo,
                origin = baseMs,
                history = listOf(sesion),
                wellbeing = null,
                described = sesion,
                instants = horas(baseMs, listOf(0, 12, 24, 72)),
                preview = null,
            )
        }

        // Grupo C: historiales, evaluados tras la última sesión (que está en la base).
        val horasC = listOf(0, 12, 24, 72, 168)
        fun casoDeHistorial(id: String, titulo: String, historial: List<WorkoutLog>) {
            casos += GoldenCase(
                id = id,
                title = titulo,
                settings = ajustes,
                origin = baseMs,
                history = historial,
                wellbeing = null,
                described = historial.last(),
                instants = horas(baseMs, horasC),
                preview = null,
            )
        }
        casoDeHistorial("C01", "1 sesión", secuencia("C01", ajustes, listOf(baseMs to MIXTA)))
        casoDeHistorial(
            "C02", "2 sesiones en días consecutivos",
            secuencia("C02", ajustes, listOf(hace(1) to PIERNAS, baseMs to EMPUJE)),
        )
        casoDeHistorial(
            "C03", "3 sesiones en días consecutivos",
            secuencia("C03", ajustes, listOf(hace(2) to PIERNAS, hace(1) to EMPUJE, baseMs to TIRON)),
        )
        casoDeHistorial(
            "C04", "5 sesiones en días consecutivos",
            secuencia(
                "C04", ajustes,
                listOf(hace(4) to PIERNAS, hace(3) to EMPUJE, hace(2) to TIRON, hace(1) to PIERNAS, baseMs to EMPUJE),
            ),
        )
        casoDeHistorial(
            "C05", "7 sesiones en días consecutivos",
            secuencia(
                "C05", ajustes,
                listOf(
                    hace(6) to PIERNAS, hace(5) to EMPUJE, hace(4) to TIRON, hace(3) to PIERNAS,
                    hace(2) to EMPUJE, hace(1) to TIRON, baseMs to PIERNAS,
                ),
            ),
        )
        casoDeHistorial(
            "C06", "dos sesiones de las mismas zonas separadas 24 h",
            secuencia("C06", ajustes, listOf(hace(1) to PIERNAS, baseMs to PIERNAS)),
        )
        val c07 = secuencia(
            "C07", ajustes,
            (0 until 24).map { i ->
                val j = 23 - i
                (baseMs - ((j / 3) * 7 + (j % 3) * 2) * DIA_MS) to listOf(PIERNAS, EMPUJE, TIRON)[i % 3]
            },
        )
        casoDeHistorial("C07", "3 sesiones por semana durante 8 semanas", c07)
        casoDeHistorial(
            "C08", "recién llegado con 5 sesiones en 15 días",
            secuencia(
                "C08", ajustes,
                listOf(
                    hace(12) to BANCA_LIGERA, hace(9) to BANCA_LIGERA, hace(6) to BANCA_LIGERA,
                    hace(3) to BANCA_LIGERA, baseMs to BANCA_LIGERA,
                ),
            ),
        )
        val c09Eventos: List<Pair<Long, Plantilla>> = (0 until 18).map { i ->
            (baseMs - (2 + 7 * (i / 3) + 2 * (i % 3)) * DIA_MS) to MODERADA
        } + listOf(baseMs to ENORME)
        casoDeHistorial(
            "C09", "6 semanas de 3 sesiones moderadas y luego una sesión enorme",
            secuencia("C09", ajustes, c09Eventos),
        )

        // Grupo D: bienestar, anclas manuales y evidencia inicial.
        val horasD = listOf(0, 2, 6, 12, 17, 19, 24, 48, 72)
        val a01 = crearSesion("D-a01-s1", PIERNAS, baseMs, emptyList(), ajustes)
        casos += GoldenCase(
            id = "D01",
            title = "anclas manuales globales",
            settings = ajustes,
            origin = baseMs,
            history = listOf(a01),
            wellbeing = DailyWellbeingLog(
                id = "D01",
                date = fecha,
                manualMuscularBattery = 70,
                manualNeuralBattery = 75,
                manualSpinalBattery = 80,
                manualBatteryAnchorMs = baseMs + 2 * HORA_MS,
            ),
            described = a01,
            instants = horas(baseMs, horasD),
            preview = null,
        )
        val automatica = AugeRecoveryEngine.getPerMuscleBatteries(
            history = listOf(a01),
            wellbeing = null,
            settings = ajustes,
            exerciseDb = EXERCISE_DB,
            nowOverrideMs = baseMs + 3 * HORA_MS,
            zone = ZONE,
        ).getValue("Cuádriceps").recoveryScore
        casos += GoldenCase(
            id = "D02",
            title = "sobreescritura V2 por músculo",
            settings = ajustes,
            origin = baseMs,
            history = listOf(a01),
            wellbeing = DailyWellbeingLog(
                id = "D02",
                date = fecha,
                manualMuscleOverridesV2 = mapOf(
                    "Cuádriceps" to ManualMuscleBatteryOverride(
                        battery = 60,
                        anchorEpochMs = baseMs + 3 * HORA_MS,
                        automaticBatteryAtAnchor = automatica,
                    ),
                ),
            ),
            described = a01,
            instants = horas(baseMs, horasD),
            preview = null,
        )
        val evidencia = InitialRecoveryEvidenceFactory.fromInputsV2(
            capturedAtMs = baseMs,
            zone = ZONE,
            trainedDays = setOf(1),
            intensity = InitialRecoveryIntensity.HARD,
            lifts = setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.HINGE_ROW),
            groupSets = mapOf(RingsMuscleGroup.LEGS to 9),
        )
        val ajustesEvidencia = Settings(initialRecoveryEvidence = evidencia)
        casos += GoldenCase(
            id = "D03",
            title = "evidencia inicial del alta",
            settings = ajustesEvidencia,
            origin = baseMs,
            history = emptyList(),
            wellbeing = null,
            described = null,
            instants = horas(baseMs, listOf(0, 12, 24, 48, 72, 120)),
            preview = null,
        )
        val altaConSesion = crearSesion("D04-s1", PIERNAS, baseMs + 6 * HORA_MS, emptyList(), ajustesEvidencia)
        casos += GoldenCase(
            id = "D04",
            title = "la misma evidencia y una sesión a las 6 h",
            settings = ajustesEvidencia,
            origin = baseMs,
            history = listOf(altaConSesion),
            wellbeing = null,
            described = altaConSesion,
            instants = horas(baseMs, listOf(0, 6, 12, 24, 48, 72, 120)),
            preview = null,
        )

        // Grupo E: vista previa del cierre (previewPostSessionBatteries).
        val previasE01 = secuencia(
            "E01-previa", ajustes,
            listOf(
                (baseMs - 72 * HORA_MS) to PIERNAS,
                (baseMs - 48 * HORA_MS) to TIRON,
                (baseMs - 24 * HORA_MS) to PIERNAS,
            ),
        )
        val sesionE01 = crearSesion("E01-sesion", EMPUJE, baseMs, previasE01, ajustes)
        casos += GoldenCase(
            id = "E01",
            title = "vista previa con 3 sesiones previas y empuje",
            settings = ajustes,
            origin = baseMs,
            history = emptyList(),
            wellbeing = null,
            described = null,
            instants = emptyList(),
            preview = GoldenPreview(previasE01, sesionE01, baseMs, "E01-cierre", "E01-entrada"),
        )
        val sesionE02 = crearSesion("E02-sesion", PIERNAS, baseMs, emptyList(), ajustes)
        casos += GoldenCase(
            id = "E02",
            title = "vista previa sin historial y piernas",
            settings = ajustes,
            origin = baseMs,
            history = emptyList(),
            wellbeing = null,
            described = null,
            instants = emptyList(),
            preview = GoldenPreview(emptyList(), sesionE02, baseMs, "E02-cierre", "E02-entrada"),
        )
        val previasE03 = c07.filter { (AugeUtils.logDateMs(it) in (baseMs - 7 * DIA_MS) until baseMs) }
        val sesionE03 = crearSesion("E03-sesion", TIRON, baseMs, previasE03, ajustes)
        casos += GoldenCase(
            id = "E03",
            title = "vista previa con 7 días de C07 y tirón",
            settings = ajustes,
            origin = baseMs,
            history = emptyList(),
            wellbeing = null,
            described = null,
            instants = emptyList(),
            preview = GoldenPreview(previasE03, sesionE03, baseMs, "E03-cierre", "E03-entrada"),
        )
        return casos
    }
}
