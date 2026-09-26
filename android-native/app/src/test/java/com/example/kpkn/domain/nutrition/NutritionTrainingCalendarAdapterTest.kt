package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompetitionDetails
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.domain.calculations.CardioCalorieInput
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine.ResolvedLoadSource
import com.example.kpkn.domain.training.ProgramCalendarEngine
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Adaptador REAL del calendario de entrenamiento al gasto previsto por fecha:
 * consume los motores existentes y respeta la honestidad (no estimable ≠ 0,
 * UNA variante por sesión+fecha, opcionales confirmadas por sesión+fecha, cardio
 * solo con peso corporal, sesiones sin scheduling ≠ descanso).
 */
class NutritionTrainingCalendarAdapterTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)
    private val week: List<LocalDate> = (0L until 7L).map { monday.plusDays(it) }
    private val settings = Settings()
    private val settingsWithWeight = Settings(userVitals = UserVitals(weight = 80.0))

    // ─── Índice de catálogo (metadatos REALES, mismo patrón que el resto del repo) ─

    /**
     * El índice es global: se limpia antes y después para no contaminar tests
     * que esperan el catálogo aprobado completo (misma disciplina que
     * `ExerciseReadinessEngineArticularTest`).
     */
    @Before
    fun resetCatalogIndex() = setCatalogIndex(emptyMap())

    @After
    fun restoreCatalogIndex() = setCatalogIndex(emptyMap())

    private fun setCatalogIndex(index: Map<String, ExerciseMuscleInfo>) {
        val field = Class.forName("com.example.kpkn.data.exercises.ExerciseDatabaseKt")
            .getDeclaredField("exerciseDatabaseByIdCache")
        field.isAccessible = true
        field.set(null, index)
    }

    private fun injectCatalog(vararg info: ExerciseMuscleInfo) =
        setCatalogIndex(info.associateBy { it.id.lowercase() })

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private fun weightedSession(id: String, day: Int, weight: Double = 100.0): Session {
        val set = ExerciseSet(id = "${id}_s", weight = weight, targetReps = 8, targetRPE = 8.0)
        return Session(
            id = id,
            name = "Fuerza $id",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(id = "${id}_e", name = "Press", restTime = 90, sets = listOf(set)),
            ),
        )
    }

    private fun rirOnlySession(id: String, day: Int): Session =
        Session(
            id = id,
            name = "RIR sin carga $id",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(
                    id = "${id}_e",
                    name = "Press",
                    restTime = 90,
                    sets = listOf(ExerciseSet(id = "${id}_s", targetReps = 8, targetRIR = 2)),
                ),
            ),
        )

    /**
     * Sesión MIXTA: un ejercicio con carga explícita (suma kcal y descanso) y
     * otro sin carga externa resuelta. El motor devuelve `mid > 0` (el descanso
     * del primero pisa la balanza) pero el gasto real es DESCONOCIDO.
     */
    private fun mixedLoadSession(id: String, day: Int): Session =
        Session(
            id = id,
            name = "Mixta $id",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(
                    id = "${id}_e1",
                    name = "Press banca",
                    restTime = 90,
                    sets = listOf(ExerciseSet(id = "${id}_s1", weight = 100.0, targetReps = 8, targetRPE = 8.0)),
                ),
                Exercise(
                    id = "${id}_e2",
                    name = "Curl bíceps",
                    restTime = 90,
                    sets = listOf(ExerciseSet(id = "${id}_s2", targetReps = 10, targetRIR = 2)),
                ),
            ),
        )

    private fun cardioSession(id: String, day: Int, seconds: Int = 1800): Session =
        Session(
            id = id,
            name = "Cardio $id",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(
                    id = "${id}_e",
                    name = "Cinta",
                    cardioDetails = CardioDetails(
                        type = CardioType.TREADMILL,
                        intensity = CardioIntensity.MEDIA,
                        targetDurationSeconds = seconds,
                    ),
                ),
            ),
        )

    private fun optionalSession(id: String, day: Int): Session =
        weightedSession(id, day).copy(requirement = SessionRequirement.OPTIONAL)

    private fun competitionSession(id: String, date: String? = null, keyDateId: String? = null): Session =
        Session(
            id = id,
            name = "Competición",
            competitionDetails = CompetitionDetails(competitionDate = date),
            competitionKeyDateId = keyDateId,
            isCompetitionSession = true,
        )

    private fun program(weeks: List<List<Session>>, keyDates: List<ProgramKeyDate> = emptyList()): Program =
        Program(
            id = "prog-cal",
            name = "Calendarizado",
            structure = ProgramStructure.COMPLEX,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-09-21",
                weekStartDay = 1,
                trainingDays = setOf(1, 3, 5),
                mode = ScheduleMode.DATED,
            ),
            keyDates = keyDates,
            macrocycles = listOf(
                Macrocycle(
                    id = "mac1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    goal = MesocycleGoal.ACCUMULATION,
                                    weeks = weeks.mapIndexed { index, sessions ->
                                        ProgramWeek(id = "w${index + 1}", name = "Semana ${index + 1}", sessions = sessions)
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    /** Mismo programa pero SIN ancla de calendario (imposible fechar sesiones). */
    private fun programWithoutAnchor(sessions: List<Session>): Program =
        program(listOf(sessions)).copy(
            schedulePlan = ProgramSchedulePlan(
                anchorDate = null,
                weekStartDay = 1,
                trainingDays = setOf(1, 3, 5),
                mode = ScheduleMode.FLOATING,
            ),
        )

    private fun adaptResult(
        sessions: List<Session>,
        settings: Settings = this.settings,
        fixedProgram: (Program)? = null,
        today: LocalDate = monday,
        windowDays: Long = NutritionTrainingCalendarAdapter.DEFAULT_WINDOW_DAYS,
        plannedVariants: Map<NutritionSessionInstance, SessionVariant> = emptyMap(),
        confirmedOptionalSessions: Set<NutritionSessionInstance> = emptySet(),
        keyDates: List<ProgramKeyDate> = emptyList(),
    ): NutritionTrainingCalendarResult =
        NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(
                program = fixedProgram ?: program(listOf(sessions), keyDates = keyDates),
                settings = settings,
                today = today,
                windowDays = windowDays,
                plannedVariants = plannedVariants,
                confirmedOptionalSessions = confirmedOptionalSessions,
            ),
        )

    private fun estimated(result: NutritionTrainingCalendarResult, date: LocalDate): DayExpenditure.Estimated =
        result.expendituresByDate.getValue(date) as DayExpenditure.Estimated

    private fun instance(sessionId: String, date: LocalDate) = NutritionSessionInstance(sessionId, date)

    // ─── Cobertura de ventana y estimación básica ────────────────────────────

    @Test
    fun `explicit weight sessions estimate and uncovered days stay explicit rest`() {
        val result = adaptResult(listOf(weightedSession("a", 1), weightedSession("b", 5)))
        assertTrue(result.enabled)
        // Gasto positivo en los días con sesión de peso conocido.
        assertTrue(estimated(result, week[0]).kcal > 0.0)
        assertTrue(estimated(result, week[4]).kcal > 0.0)
        // El resto de la ventana es descanso EXPLÍCITO (nunca estimable).
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[1]])
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[2]])
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[3]])
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[5]])
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[6]])
        assertEquals(week.toSet(), result.expendituresByDate.keys)
        assertEquals(listOf(week[0], week[4]), result.sessions.map { it.date })
    }

    @Test
    fun `rir-only session without load is NotEstimable and never zero`() {
        val result = adaptResult(listOf(weightedSession("a", 1), rirOnlySession("b", 3)))
        assertTrue(estimated(result, week[0]).kcal > 0.0)
        // La sesión existe pero su gasto es desconocido: NO es descanso.
        assertEquals(DayExpenditure.NotEstimable, result.expendituresByDate[week[2]])
        val rir = result.sessions.single { it.sessionId == "b" }
        assertEquals(week[2], rir.date)
        assertTrue(rir.variantEstimatesKcal.isEmpty())
    }

    @Test
    fun `unknown external load with positive rest kcal is NotEstimable`() {
        val session = mixedLoadSession("mix", day = 1)
        // Premisa: el motor devuelve un total POSITIVO (el ejercicio con carga
        // aporta trabajo Y descanso) aunque falte carga en el otro ejercicio.
        val summary = TrainingEnergyEngine.estimatePlannedSession(session, settings)
        assertTrue(
            "El total debe ser positivo para que el test tenga sentido",
            summary.totalKcal.mid > 0,
        )
        assertNull(
            "`mid > 0` NO garantiza estimable: falta carga externa en una serie de trabajo",
            NutritionTrainingCalendarAdapter.estimateVariantKcal(session, settings),
        )

        val result = adaptResult(listOf(session))
        assertEquals(DayExpenditure.NotEstimable, result.expendituresByDate[week[0]])
    }

    // ─── Programa/calenadario sin ancla: nunca un falso descanso ────────────

    @Test
    fun `program without anchor but with sessions is NotEstimable for the whole window`() {
        val result = adaptResult(sessions = emptyList(), fixedProgram = programWithoutAnchor(listOf(weightedSession("a", 1))))
        assertFalse(result.enabled)
        assertTrue(result.sessions.isEmpty())
        assertTrue(result.expendituresByDate.values.all { it === DayExpenditure.NotEstimable })
    }

    @Test
    fun `program without anchor and without sessions is explicit rest`() {
        val result = adaptResult(sessions = emptyList(), fixedProgram = programWithoutAnchor(emptyList()))
        assertFalse(result.enabled)
        assertTrue(result.expendituresByDate.values.all { it === DayExpenditure.Rest })
    }

    @Test
    fun `session that cannot be scheduled is NotEstimable instead of rest`() {
        // Semana calendariada pero la sesión no tiene día ni competición: no
        // podemos afirmar que los días libres son descanso.
        val floating = rirOnlySession("floating", day = 1).copy(dayOfWeek = null)
        val result = adaptResult(listOf(floating))
        assertTrue(result.sessions.isEmpty())
        assertTrue(result.expendituresByDate.values.all { it === DayExpenditure.NotEstimable })
    }

    @Test
    fun `disabled calendar without program is explicit rest`() {
        val disabled = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = null, settings = settings, today = monday),
        )
        assertFalse(disabled.enabled)
        assertTrue(disabled.sessions.isEmpty())
        assertEquals(week.toSet(), disabled.expendituresByDate.keys)
        assertTrue(disabled.expendituresByDate.values.all { it === DayExpenditure.Rest })
    }

    // ─── Cardio: solo con peso corporal válido ───────────────────────────────

    @Test
    fun `cardio estimates only with a valid body weight`() {
        val withoutWeight = adaptResult(listOf(cardioSession("c", 1)), settings = settings)
        assertEquals(DayExpenditure.NotEstimable, withoutWeight.expendituresByDate[week[0]])

        val withWeight = adaptResult(listOf(cardioSession("c", 1)), settings = settingsWithWeight)
        val details = cardioSession("c", 1).exercises.first().cardioDetails!!
        val expected = CardioCalorieEngine.estimate(
            CardioCalorieInput(details = details, weightKg = 80.0, durationSeconds = details.effectiveDurationSeconds()),
        )
        assertEquals(expected, estimated(withWeight, week[0]).kcal, 0.001)
    }

    // ─── UNA variante por sesión+fecha ──────────────────────────────────────

    @Test
    fun `planned variant B counts once and never adds A`() {
        val base = weightedSession("v", 1, weight = 100.0)
        val withB = weightedSession("v-b", 1, weight = 120.0)
        val session = base.copy(sessionB = withB)
        val result = adaptResult(
            listOf(session),
            plannedVariants = mapOf(instance("v", week[0]) to SessionVariant.B),
        )
        val bKcal = NutritionTrainingCalendarAdapter.estimateVariantKcal(withB, settings)!!
        assertEquals(bKcal, estimated(result, week[0]).kcal, 0.001)
        // Una sola sesión por día: la variante elegida, sin sumar A.
        assertEquals(1, result.sessions.count { it.date == week[0] })
    }

    @Test
    fun `unmaterialized variant falls back to A`() {
        val session = weightedSession("v", 1, weight = 100.0)
        val result = adaptResult(
            listOf(session),
            plannedVariants = mapOf(instance("v", week[0]) to SessionVariant.B),
        )
        assertEquals(SessionVariant.A, result.sessions.single().chosenVariant)
        assertEquals(
            NutritionTrainingCalendarAdapter.estimateVariantKcal(session, settings)!!,
            estimated(result, week[0]).kcal,
            0.001,
        )
    }

    @Test
    fun `materialized but unestimable variant is honest NotEstimable`() {
        // La variante C EXISTE pero no tiene carga: no se cae en silencio a A.
        val base = weightedSession("v", 1, weight = 100.0)
        val session = base.copy(sessionC = rirOnlySession("v-c", 1))
        val result = adaptResult(
            listOf(session),
            plannedVariants = mapOf(instance("v", week[0]) to SessionVariant.C),
        )
        assertEquals(SessionVariant.C, result.sessions.single().chosenVariant)
        assertEquals(DayExpenditure.NotEstimable, result.expendituresByDate[week[0]])
    }

    // ─── Opcionales confirmadas por SESIÓN + FECHA ──────────────────────────

    @Test
    fun `optional sessions only count when confirmed for their own date`() {
        val result = adaptResult(
            listOf(optionalSession("opt-mon", 1), optionalSession("opt-wed", 3)),
            confirmedOptionalSessions = setOf(instance("opt-wed", week[2])),
        )
        // La opcional sin confirmar NO aparece (ni como sesión ni como gasto).
        assertTrue(result.sessions.none { it.sessionId == "opt-mon" })
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[0]])
        // La confirmada sí cuenta.
        assertTrue(estimated(result, week[2]).kcal > 0.0)
        assertTrue(result.sessions.single { it.sessionId == "opt-wed" }.confirmedForDate)
    }

    @Test
    fun `confirming one optional does not activate the other optionals of the same day`() {
        val result = adaptResult(
            listOf(optionalSession("opt-a", 1), optionalSession("opt-b", 1)),
            confirmedOptionalSessions = setOf(instance("opt-a", week[0])),
        )
        // SOLO la sesión+fecha confirmada cuenta; la otra opcional del MISMO día
        // sigue fuera (confirmar por fecha activaría todas, y eso es un error).
        val confirmed = result.sessions.filter { it.date == week[0] }
        assertEquals(listOf("opt-a"), confirmed.map { it.sessionId })
        val expected = NutritionTrainingCalendarAdapter.estimateVariantKcal(optionalSession("opt-a", 1), settings)!!
        assertEquals(expected, estimated(result, week[0]).kcal, 0.001)
    }

    @Test
    fun `two real sessions on the same day are summed once each`() {
        val result = adaptResult(listOf(weightedSession("a", 1), weightedSession("b", 1)))
        val first = NutritionTrainingCalendarAdapter.estimateVariantKcal(weightedSession("a", 1), settings)!!
        val second = NutritionTrainingCalendarAdapter.estimateVariantKcal(weightedSession("b", 1), settings)!!
        assertEquals(2, result.sessions.count { it.date == week[0] })
        assertEquals(first + second, estimated(result, week[0]).kcal, 0.001)
    }

    // ─── Fechas ──────────────────────────────────────────────────────────────

    @Test
    fun `competition session without day maps to its event date and stays in week`() {
        val match = competitionSession("meet", date = "2026-09-26")
        val result = adaptResult(listOf(match))
        assertEquals(week[5], result.sessions.single().date)
        // La sesión está planificada pero sin contenido estimable → desconocido,
        // nunca un descanso.
        assertEquals(DayExpenditure.NotEstimable, result.expendituresByDate[week[5]])
    }

    @Test
    fun `competition key date resolves when the event has no explicit date`() {
        val keyDate = ProgramKeyDate(
            id = "k1",
            title = "Campeonato",
            type = KeyDateType.COMPETITION,
            startDate = "2026-09-26",
            eventDate = "2026-09-26",
        )
        val match = competitionSession("meet", date = null, keyDateId = "k1")
        val result = adaptResult(listOf(match), keyDates = listOf(keyDate))
        assertEquals(week[5], result.sessions.single().date)
    }

    @Test
    fun `optional confirmations come from real workout logs and never from thin air`() {
        val optional = optionalSession("opt", 1)
        val log = WorkoutLog(
            id = "log-1",
            programId = "prog-cal",
            sessionId = "opt",
            sessionName = "Opcional",
            date = "2026-09-21T10:00:00.000Z",
            durationMinutes = 45,
        )
        val alienProgram = log.copy(id = "log-2", programId = "otro-programa")

        // Origen productivo ÚNICO: registro real, emparejado por (sesión, fecha).
        val confirmed = confirmedOptionalSessionsOf(listOf(log, alienProgram), "prog-cal")
        assertEquals(setOf(instance("opt", week[0])), confirmed)
        assertTrue(confirmedOptionalSessionsOf(listOf(log), "otro-programa").isEmpty())
        assertTrue(confirmedOptionalSessionsOf(listOf(log), null).isEmpty())

        // Con esa evidencia la opcional cuenta; SIN ella es descanso explícito
        // (el adaptador no inventa confirmaciones).
        val withLog = adaptResult(listOf(optional), confirmedOptionalSessions = confirmed)
        assertTrue(estimated(withLog, week[0]).kcal > 0.0)
        val withoutEvidence = adaptResult(listOf(optional))
        assertEquals(DayExpenditure.Rest, withoutEvidence.expendituresByDate[week[0]])
        assertTrue(withoutEvidence.sessions.isEmpty())
    }

    @Test
    fun `same session id across two weeks counts once per date`() {
        val shared = weightedSession("shared", day = 1)
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(
                program = program(listOf(listOf(shared), listOf(shared))),
                settings = settings,
                today = monday,
                windowDays = 14L,
            ),
        )
        val kcal = NutritionTrainingCalendarAdapter.estimateVariantKcal(shared, settings)!!
        val nextMonday = monday.plusDays(7L)
        // Dos fechas distintas: cada una cuenta la sesión una vez (no se
        // colapsa por sessionId a nivel global).
        assertEquals(2, result.sessions.size)
        assertEquals(setOf(week[0], nextMonday), result.sessions.map { it.date }.toSet())
        assertTrue(result.sessions.all { it.sessionId == "shared" })
        assertEquals(kcal, estimated(result, week[0]).kcal, 0.001)
        assertEquals(kcal, estimated(result, nextMonday).kcal, 0.001)
    }

    // ─── Evidencia de carga por FUENTE real (catálogo inyectado) ───────────

    @Test
    fun `bodyweight only set with a declared zero external load and known weight stays estimable`() {
        injectCatalog(
            ExerciseMuscleInfo(
                id = "flexion_bw",
                name = "Flexión de pecho",
                equipment = "Peso corporal",
                movementPattern = "Flexión",
                bodyPart = "upper",
                force = "Empuje",
                type = "Básico",
                category = "Fuerza",
                efc = 2.5,
                cnc = 2.0,
                ssc = 1.0,
                ttc = 1.5,
            ),
        )
        val set = ExerciseSet(
            id = "bw_s",
            loadModeV2 = LoadModeV2.BODYWEIGHT,
            targetReps = 12,
            targetRPE = 8.0,
        )
        val exercise = Exercise(
            id = "flexion_bw",
            name = "Flexión de pecho",
            exerciseDbId = "flexion_bw",
            restTime = 60,
            sets = listOf(set),
        )
        val session = Session(id = "bw", name = "Pecho BW", dayOfWeek = 1, exercises = listOf(exercise))

        // Fuente REAL del motor: 0 kg externos declarados (un 0 con valor).
        val resolved = TrainingEnergyEngine.resolvePlannedExternalLoadKg(
            exercise,
            set,
            settingsWithWeight.weightUnit,
        )
        assertEquals(ResolvedLoadSource.BODYWEIGHT_ONLY, resolved.source)
        assertEquals(0.0, resolved.externalLoadKg!!, 0.0)
        val dbInfo = resolveCatalogExerciseInfo(null, "flexion_bw", null, exercise.name)
        assertNotNull(dbInfo)
        assertTrue(TrainingEnergyEngine.bodyweightParticipation(dbInfo!!) > 0.0)

        // Con peso corporal conocido el cuerpo ES la carga ⇒ estimable.
        val estimate = NutritionTrainingCalendarAdapter.estimateVariantKcal(session, settingsWithWeight)
        assertNotNull(estimate)
        assertTrue(estimate!! > 0.0)
        val withWeight = adaptResult(listOf(session), settings = settingsWithWeight)
        assertTrue(estimated(withWeight, week[0]).kcal > 0.0)

        // Sin peso corporal el motor no la puntúa ⇒ gasto desconocido.
        assertNull(NutritionTrainingCalendarAdapter.estimateVariantKcal(session, settings))
        val withoutWeight = adaptResult(listOf(session), settings = settings)
        assertEquals(DayExpenditure.NotEstimable, withoutWeight.expendituresByDate[week[0]])
    }

    @Test
    fun `barbell squat percent one rm without reference load is unknown expenditure not estimable by body alone`() {
        injectCatalog(
            ExerciseMuscleInfo(
                id = "sentadilla_barra",
                name = "Sentadilla con barra",
                equipment = "Barra",
                movementPattern = "Sentadilla",
                bodyPart = "lower",
                force = "Empuje",
                type = "Básico",
                category = "Fuerza",
                efc = 4.0,
                cnc = 3.0,
                ssc = 1.5,
                ttc = 3.0,
            ),
        )
        val set = ExerciseSet(
            id = "sq_s",
            targetPercentageRM = 75.0,
            targetReps = 5,
            targetRPE = 8.0,
        )
        val exercise = Exercise(
            id = "sentadilla_barra",
            name = "Sentadilla con barra",
            exerciseDbId = "sentadilla_barra",
            restTime = 180,
            sets = listOf(set),
        )
        val session = Session(id = "sq", name = "Pierna", dayOfWeek = 1, exercises = listOf(exercise))

        // Auditoría contra el motor REAL: %1RM sin RM de referencia ⇒ MISSING
        // (externalLoad null) y, a la vez, participación corporal del catálogo > 0.
        val dbInfo = resolveCatalogExerciseInfo(null, "sentadilla_barra", null, exercise.name)
        assertNotNull(dbInfo)
        assertTrue("Participación corporal real del catálogo", TrainingEnergyEngine.bodyweightParticipation(dbInfo!!) > 0.0)
        val resolved = TrainingEnergyEngine.resolvePlannedExternalLoadKg(
            exercise,
            set,
            settingsWithWeight.weightUnit,
        )
        assertEquals(ResolvedLoadSource.MISSING, resolved.source)
        assertNull(resolved.externalLoadKg)

        // El motor devuelve un total POSITIVO (cuerpo parcial + descanso)…
        val summary = TrainingEnergyEngine.estimatePlannedSession(session, settingsWithWeight)
        assertTrue("Premisa: el total del motor es positivo", summary.totalKcal.mid > 0)

        // …pero el gasto es DESCONOCIDO: no se estima «sólo por el cuerpo».
        assertNull(NutritionTrainingCalendarAdapter.estimateVariantKcal(session, settingsWithWeight))
        val result = adaptResult(listOf(session), settings = settingsWithWeight)
        assertEquals(DayExpenditure.NotEstimable, result.expendituresByDate[week[0]])

        // ⇒ el reparto semanal cae a UNIFORME PROVISIONAL (ni descanso ni número fabricado).
        // Oráculo con el periodo REAL que devuelve el adaptador: los 7 días de la
        // semana (no 2 sueltos), para que el presupuesto por defecto 7·B = 14000
        // se reparta exacto en 7 → 2000/día.
        val distribution = NutritionDayDistribution.distribute(
            NutritionDayDistributionInput(
                futureDates = week,
                expenditures = result.expendituresByDate,
                dailyMeanKcal = 2_000.0,
            ),
        )
        assertEquals(NutritionDistributionStatus.PROVISIONAL_UNIFORM, distribution.status)
        assertEquals(listOf(week[0]), distribution.missingEstimateDates)
        assertTrue(distribution.targetsByDate.values.all { it == 2_000 })
        // Presupuesto exacto del periodo: Σ T_i = 7 · 2000.
        assertEquals(14_000, distribution.targetsByDate.values.sum())
    }

    // ─── Confirmaciones manuales del calendario (campo JSON del programa) ───

    @Test
    fun `program confirmation counts only its own optional instance`() {
        val program = program(
            listOf(
                listOf(
                    optionalSession("opt-a", 1),
                    optionalSession("opt-b", 1),
                    optionalSession("opt-c", 3),
                ),
            ),
        ).copy(
            optionalSessionConfirmations = listOf(
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "opt-a"),
            ),
        )
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = program, settings = settings, today = monday),
        )

        // SÓLO esa sesión+fecha cuenta (nunca «todas las del día» ni el otro día).
        assertEquals(listOf("opt-a"), result.sessions.map { it.sessionId })
        val expected = NutritionTrainingCalendarAdapter.estimateVariantKcal(optionalSession("opt-a", 1), settings)!!
        assertEquals(expected, estimated(result, week[0]).kcal, 0.001)
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[1]])
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[2]])
    }

    @Test
    fun `removing one program confirmation leaves the other confirmations intact`() {
        val optionals = listOf(
            optionalSession("opt-a", 1),
            optionalSession("opt-b", 1),
            optionalSession("opt-c", 3),
        )
        val all = listOf(
            OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "opt-a"),
            OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "opt-b"),
            OptionalSessionConfirmation(dayIso = week[2].toString(), sessionId = "opt-c"),
        )
        // Se retira SÓLO la de «opt-a»: el resto de confirmaciones sigue vivo.
        val program = program(listOf(optionals)).copy(
            optionalSessionConfirmations = all.filterNot { it.sessionId == "opt-a" },
        )
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = program, settings = settings, today = monday),
        )

        assertEquals(setOf("opt-b", "opt-c"), result.sessions.map { it.sessionId }.toSet())
        val optB = NutritionTrainingCalendarAdapter.estimateVariantKcal(optionalSession("opt-b", 1), settings)!!
        val optC = NutritionTrainingCalendarAdapter.estimateVariantKcal(optionalSession("opt-c", 3), settings)!!
        assertEquals(optB, estimated(result, week[0]).kcal, 0.001)
        assertEquals(optC, estimated(result, week[2]).kcal, 0.001)
    }

    @Test
    fun `program confirmation pins a single variant and never sums B and C`() {
        val base = weightedSession("v", 1, weight = 100.0)
        val variantB = weightedSession("v-b", 1, weight = 120.0)
        val variantC = weightedSession("v-c", 1, weight = 140.0)
        val session = base.copy(
            requirement = SessionRequirement.OPTIONAL,
            sessionB = variantB,
            sessionC = variantC,
        )
        // Dos claves conflictivas para la MISMA instancia: gana la primera
        // declarada (misma regla que la deduplicación de sesiones) y jamás
        // se suman B + C.
        val program = program(listOf(listOf(session))).copy(
            optionalSessionConfirmations = listOf(
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v", variantKey = "B"),
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v", variantKey = "C"),
            ),
        )
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = program, settings = settings, today = monday),
        )

        assertEquals(1, result.sessions.size)
        assertEquals(SessionVariant.B, result.sessions.single().chosenVariant)
        val onlyB = NutritionTrainingCalendarAdapter.estimateVariantKcal(variantB, settings)!!
        assertEquals(onlyB, estimated(result, week[0]).kcal, 0.001)
    }

    @Test
    fun `program without confirmation key or with a broken record is ignored`() {
        val program = program(listOf(listOf(optionalSession("opt-a", 1)))).copy(
            optionalSessionConfirmations = listOf(
                // Día ilegible y sesión vacía: se ignoran, nunca fabrican sesión.
                OptionalSessionConfirmation(dayIso = "no-es-fecha", sessionId = "opt-a"),
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "   "),
            ),
        )
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = program, settings = settings, today = monday),
        )
        assertTrue(result.sessions.isEmpty())
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[0]])
    }

    @Test
    fun `first confirmation without variant key pins A even when a later duplicate declares B`() {
        val base = weightedSession("v", 1, weight = 100.0)
        val variantB = weightedSession("v-b", 1, weight = 120.0)
        val optionalVariant = base.copy(requirement = SessionRequirement.OPTIONAL, sessionB = variantB)

        // Primer registro SIN variante ⇒ A fijado; el duplicado con B se ignora.
        val withoutKey = program(listOf(listOf(optionalVariant))).copy(
            optionalSessionConfirmations = listOf(
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v"),
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v", variantKey = "B"),
            ),
        )
        val first = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = withoutKey, settings = settings, today = monday),
        )
        assertEquals(SessionVariant.A, first.sessions.single().chosenVariant)
        assertEquals(
            NutritionTrainingCalendarAdapter.estimateVariantKcal(base, settings)!!,
            estimated(first, week[0]).kcal,
            0.001,
        )

        // Primer registro con clave ILEGIBLE ⇒ también A (no se salta a la B).
        val invalidKey = program(listOf(listOf(optionalVariant))).copy(
            optionalSessionConfirmations = listOf(
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v", variantKey = "NOPE"),
                OptionalSessionConfirmation(dayIso = week[0].toString(), sessionId = "v", variantKey = "B"),
            ),
        )
        val second = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(program = invalidKey, settings = settings, today = monday),
        )
        assertEquals(SessionVariant.A, second.sessions.single().chosenVariant)
    }

    @Test
    fun `optional occurrence validation requires the real optional session on that exact date`() {
        val optionalMonday = optionalSession("opt", 1)
        val requiredWednesday = weightedSession("req", 3)
        val withAnchor = program(listOf(listOf(optionalMonday, requiredWednesday)))

        // Ocurrencia real: esa sesión OPCAIONAL en ESA fecha.
        assertTrue(NutritionTrainingCalendarAdapter.optionalOccurrenceOf(withAnchor, week[0], "opt"))
        // Nunca para una obligatoria, otra fecha o un id inexistente.
        assertFalse(NutritionTrainingCalendarAdapter.optionalOccurrenceOf(withAnchor, week[0], "req"))
        assertFalse(NutritionTrainingCalendarAdapter.optionalOccurrenceOf(withAnchor, week[1], "opt"))
        assertFalse(NutritionTrainingCalendarAdapter.optionalOccurrenceOf(withAnchor, week[0], "no-existe"))
        // Sin ancla la proyección no tiene ocurrencias ⇒ nada que confirmar.
        assertFalse(
            NutritionTrainingCalendarAdapter.optionalOccurrenceOf(
                programWithoutAnchor(listOf(optionalMonday)),
                week[0],
                "opt",
            ),
        )
        assertFalse(NutritionTrainingCalendarAdapter.optionalOccurrenceOf(null, week[0], "opt"))
    }

    @Test
    fun `wizard style program resolves real day dates from the calendar projection`() {
        // Formato del wizard (ancla + calendarización): el motor YA materializa
        // la fecha real de cada día.
        val wizardStyle = program(listOf(listOf(weightedSession("a", 1), weightedSession("b", 5))))
        val projection = ProgramCalendarEngine.project(wizardStyle)
        assertTrue(projection.enabled)
        val projectedDates = projection.weeks.first { it.weekId == "w1" }.trainingDayDates
        assertEquals(monday, projectedDates[1])
        assertEquals(monday.plusDays(4), projectedDates[5])

        // El fallback de DayView (meta vacío → MISMA proyección) resuelve lo
        // mismo; nunca un weekday aislado inventado.
        val emptyMeta = emptyMap<Int, String>()
        assertEquals(monday, emptyMeta[1] ?: projectedDates[1])
        assertEquals(monday.plusDays(4), emptyMeta[5] ?: projectedDates[5])

        // Programa sin ancla: la proyección no tiene ocurrencias reales ⇒ no se
        // deriva ninguna fecha (la acción queda oculta, no fabricada).
        val floating = programWithoutAnchor(listOf(weightedSession("a", 1)))
        val floatingProjection = ProgramCalendarEngine.project(floating)
        assertFalse(floatingProjection.enabled)
        assertTrue(floatingProjection.weeks.isEmpty())
        assertNull(floatingProjection.weeks.firstOrNull()?.trainingDayDates?.get(1))
    }
}
