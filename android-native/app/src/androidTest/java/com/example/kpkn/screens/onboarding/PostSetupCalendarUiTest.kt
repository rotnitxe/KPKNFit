package com.example.kpkn.screens.onboarding

import android.content.Context
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.WeeklyForecastDocument
import com.example.kpkn.domain.nutrition.decodeWeeklyForecastDocument
import com.example.kpkn.domain.nutrition.encodeWeeklyForecast
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.screens.programdetail.components.DAYS_OF_WEEK
import com.example.kpkn.screens.programdetail.components.DayView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Contratos de calendario POST-ALTA en el UI nativo: DayView real, callbacks
 * del ProgramDetailViewModel real, persistencia Room y previsión semanal del
 * NutritionRepository. La previsión se observa de forma reactiva; no se invoca
 * el coordinador ni se finge un callback de guardado.
 *
 * Los fixtures son propios de cada ejecución y su semana se ancla a hoy para
 * garantizar fechas futuras reales dentro del horizonte de la previsión. La
 * receta usa una configuración real del catálogo con carga externa estimable.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PostSetupCalendarUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val runId = UUID.randomUUID().toString().take(10)
    private val viewModelStore = ViewModelStore()

    private lateinit var context: Context
    private lateinit var db: KpknDatabase
    private lateinit var programs: ProgramRepository
    private lateinit var nutrition: NutritionRepository

    private var previousActiveProgram: ActiveProgramState? = null
    private var previousNutritionActiveState: NutritionActiveStateEntity? = null
    private var previousActivePlanId: String? = null
    private var previousPlanActiveFlags: Map<String, Boolean> = emptyMap()
    private var previousSettings: Settings? = null
    private var ownProgramId: String? = null
    private var ownPlanId: String? = null
    private var activeProgramTouched = false
    private var activePlanTouched = false
    private var settingsUserVitalsTouched = false
    private var qaStateCaptured = false
    private var detailViewModel: ProgramDetailViewModel? = null

    private data class CalendarFixture(
        val programId: String,
        val planId: String,
        val weekId: String,
        val today: LocalDate,
        val requiredSessionId: String,
        val requiredDay: Int,
        val requiredDate: LocalDate,
        val movedRequiredDate: LocalDate,
        val optionalAId: String,
        val optionalADate: LocalDate,
        val optionalBId: String,
        val optionalBDate: LocalDate,
    )

    @Before
    fun setUp() {
        // Guardia QA10 antes de inicializar repositorios, DB o catálogo.
        val uid = Process.myUid()
        Assume.assumeTrue(
            "Solo se permite instrumentación del usuario QA 10 (uid/100000 == 10); uid=$uid",
            uid / 100_000 == 10,
        )

        context = ApplicationProvider.getApplicationContext()
        val appId = uid % 100_000
        Assume.assumeTrue(
            "El proceso debe ser la app normal del paquete probado; uid=$uid, target=${context.applicationInfo.uid}",
            uid == context.applicationInfo.uid && appId in 10_000..19_999,
        )

        initializeExerciseDatabase(context)
        db = KpknDatabase.getInstance(context)
        programs = ProgramRepository.init(context)
        runBlocking { withTimeout(60_000L) { programs.isReady.first { it } } }

        // NutritionRepository inicia el coordinador productivo al terminar de
        // hidratarse. Esperar la carga inicial evita que una lectura antigua
        // publique su caché encima de los fixtures del test.
        nutrition = NutritionRepository.init(context)
        runBlocking {
            withTimeout(120_000L) { nutrition.foodDatabase.first { it.isNotEmpty() } }
        }

        previousActiveProgram = runBlocking {
            db.stateDao().getActiveProgram()?.toActiveProgramState()
        }
        previousNutritionActiveState = runBlocking { db.nutritionDao().getActiveState() }
        previousActivePlanId = previousNutritionActiveState?.activePlanId
        runBlocking {
            val existingPlans = db.nutritionDao().getAllPlans()
            previousPlanActiveFlags = existingPlans.associate { it.id to it.isActive }
        }
        previousSettings = runBlocking { db.settingsDao().get()?.toSettings() ?: programs.settings.value }

        val ongoing = runBlocking { db.stateDao().getOngoingWorkout() }
        Assume.assumeTrue("Hay una sesión en curso; no se cambia el programa activo.", ongoing == null)
        qaStateCaptured = true
    }

    @After
    fun restoreOwnQaEntries() {
        if (!qaStateCaptured) return

        val failures = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += "$name: ${error.message ?: error::class.java.simpleName}"
            }
        }

        step("limpiar ViewModel real") {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModelStore.clear()
                detailViewModel = null
            }
        }

        // Durante el cambio entre estado propio y estado QA no debe quedar un
        // plan activo; así la previsión nunca observa la receta propia junto al
        // programa previo del usuario (ni viceversa).
        if (activePlanTouched) {
            step("desactivar temporalmente el plan propio") {
                nutrition.setActiveNutritionPlanId(null)
                awaitCondition { db.nutritionDao().getActiveState()?.activePlanId == null }
            }
        }

        ownProgramId?.let { id ->
            step("borrar solo el programa propio") {
                programs.deleteProgram(id)
                awaitCondition { db.programDao().getById(id) == null }
            }
        }

        // deleteProgram solo puede tocar programQueueIds. El fixture usa un ID
        // UUID nuevo; si aun así cambiara esa clave, se restaura únicamente ese
        // campo y se espera el commit durable (nunca se reescriben Settings enteros).
        previousSettings?.let { previous ->
            step("restaurar solo la cola de programas si fue tocada") {
                val ownId = ownProgramId ?: return@step
                if (programs.settings.value.programQueueIds.contains(ownId)) {
                    programs.updateSettings { current ->
                        current.copy(programQueueIds = current.programQueueIds.filterNot { it == ownId })
                    }
                }
                awaitCondition {
                    val storedQueue = db.settingsDao().get()?.toSettings()?.programQueueIds
                    storedQueue?.none { it == ownId } == true &&
                        previous.programQueueIds.none { it == ownId }
                }
            }
        }

        if (settingsUserVitalsTouched) {
            step("restaurar solo userVitals") {
                val previousVitals = previousSettings?.userVitals
                    ?: error("No hay snapshot previo de userVitals")
                programs.updateSettings { current -> current.copy(userVitals = previousVitals) }
                awaitCondition {
                    db.settingsDao().get()?.toSettings()?.userVitals == previousVitals
                }
            }
        }

        if (activeProgramTouched) {
            step("restaurar programa activo durable") {
                previousActiveProgram?.let(programs::updateActiveProgramState)
                    ?: programs.clearActiveProgram()
                awaitCondition {
                    db.stateDao().getActiveProgram()?.toActiveProgramState() == previousActiveProgram
                }
            }
        }

        if (activePlanTouched || ownPlanId != null) {
            step("borrar plan propio y restaurar plan activo") {
                runBlocking {
                    withTimeout(60_000L) {
                        db.withTransaction {
                            ownPlanId?.let { db.nutritionDao().deletePlan(it) }
                            val currentRows = db.nutritionDao().getAllPlans().associateBy { it.id }
                            previousPlanActiveFlags.forEach { (id, wasActive) ->
                                val row = currentRows[id]
                                if (row != null && row.isActive != wasActive) {
                                    db.nutritionDao().upsertPlan(
                                        row.toNutritionPlan().copy(isActive = wasActive).toEntity(),
                                    )
                                }
                            }
                            if (previousNutritionActiveState == null) {
                                db.nutritionDao().clearActiveState()
                            } else {
                                db.nutritionDao().upsertActiveState(previousNutritionActiveState!!)
                            }
                        }
                        nutrition.publishNutritionPlanCommit()
                    }
                }
                awaitCondition {
                    val active = db.nutritionDao().getActiveState()?.activePlanId
                    val flags = db.nutritionDao().getAllPlans().associate { it.id to it.isActive }
                    active == previousActivePlanId &&
                        db.nutritionDao().getActiveState() == previousNutritionActiveState &&
                        previousPlanActiveFlags.all { (id, flag) -> flags[id] == flag } &&
                        ownPlanId?.let { own -> flags[own] == null } != false
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw AssertionError("Restauración QA incompleta: ${failures.joinToString(" | ")}")
        }
    }

    @Test
    fun scheduledDateEditPersistsAndAutomaticallyReforecastsOnlyFutureDays() {
        val fixture = installFixture()
        val baselinePlan = roomPlan(fixture.planId)
        val baselineForecast = requireForecast(baselinePlan)
        val programBefore = roomProgram(fixture.programId)
        val weekBefore = findWeek(programBefore, fixture.weekId)

        val viewModel = mountRealDayView(fixture)
        val originalHeader = dayHeader(fixture.requiredDate)
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(originalHeader))
        composeRule.onNodeWithText(originalHeader).performClick()
        composeRule.onNodeWithText("Fecha de entrenamiento").assertIsDisplayed()
        composeRule.onNode(hasInsertTextAtCursorAction())
            .performTextReplacement(fixture.movedRequiredDate.toString())
        composeRule.onNodeWithText("Guardar").performClick()

        val programAfter = awaitRoomProgram(fixture.programId) { saved ->
            findWeek(saved, fixture.weekId).trainingDayDates[fixture.requiredDay] ==
                fixture.movedRequiredDate.toString()
        }
        val weekAfter = findWeek(programAfter, fixture.weekId)
        assertEquals("Solo cambia la fecha anclada del día seleccionado", fixture.movedRequiredDate.toString(), weekAfter.trainingDayDates[fixture.requiredDay])
        assertEquals(
            "Las otras fechas de la semana permanecen intactas",
            weekBefore.trainingDayDates + (fixture.requiredDay to fixture.movedRequiredDate.toString()),
            weekAfter.trainingDayDates,
        )
        assertEquals("IDs y receta de las sesiones permanecen intactos", weekBefore.sessions, weekAfter.sessions)
        assertEquals("Las confirmaciones opcionales no cambian al editar una fecha", programBefore.optionalSessionConfirmations, programAfter.optionalSessionConfirmations)
        assertEquals(programBefore.id, programAfter.id)

        val updatedPlan = awaitRoomPlan(fixture.planId) { saved ->
            val forecast = forecastDocument(saved)
            forecast != null && forecast.revision > baselineForecast.revision
        }
        val updatedForecast = requireForecast(updatedPlan)
        assertForecastBudgetAndFrozenDays(fixture.today, baselineForecast, updatedForecast)
        val beforeByDate = baselineForecast.days.associateBy { it.date }
        val afterByDate = updatedForecast.days.associateBy { it.date }
        assertNotEquals(
            "El día futuro del entrenamiento requerido deja de cargar su gasto",
            beforeByDate.getValue(fixture.requiredDate),
            afterByDate.getValue(fixture.requiredDate),
        )
        assertNotEquals(
            "La nueva fecha futura recibe el gasto previsto sin llamar al forecaster desde el test",
            beforeByDate.getValue(fixture.movedRequiredDate),
            afterByDate.getValue(fixture.movedRequiredDate),
        )
        assertEquals(fixture.weekId, viewModel.selectedWeekMeta.value?.id)
    }

    @Test
    fun oneOptionalConfirmationIsDurableAndUndoRemovesOnlyItsExactKey() {
        val fixture = installFixture(preconfirmOptionalB = true)
        val baselineForecast = requireForecast(roomPlan(fixture.planId))
        assertEquals("Las dos opcionales comparten la misma fecha futura", fixture.optionalADate, fixture.optionalBDate)
        val preconfirmedB = OptionalSessionConfirmation(
            dayIso = fixture.optionalADate.toString(),
            sessionId = fixture.optionalBId,
        )
        assertEquals(listOf(preconfirmedB), roomProgram(fixture.programId).optionalSessionConfirmations)
        val viewModel = mountRealDayView(fixture)

        expandDay(fixture.optionalADate)
        composeRule.onNodeWithText("Optional A $runId").assertIsDisplayed()
        composeRule.onNodeWithText("Optional B $runId").assertIsDisplayed()
        val confirmA = optionalConfirmationAction("Optional A $runId", "Confirmar sesión opcional")
        confirmA.assertIsDisplayed()
        optionalConfirmationAction("Optional B $runId", "Retirar confirmación de sesión opcional")
            .assertIsDisplayed()
        confirmA.performClick()

        val confirmedProgram = awaitRoomProgram(fixture.programId) { saved ->
            saved.optionalSessionConfirmations == listOf(
                preconfirmedB,
                OptionalSessionConfirmation(
                    dayIso = fixture.optionalADate.toString(),
                    sessionId = fixture.optionalAId,
                ),
            )
        }
        assertEquals(
            "A se añade a la clave preexistente de B en la misma fecha",
            listOf(preconfirmedB, OptionalSessionConfirmation(fixture.optionalADate.toString(), fixture.optionalAId)),
            confirmedProgram.optionalSessionConfirmations,
        )
        optionalConfirmationAction("Optional A $runId", "Retirar confirmación de sesión opcional")
            .assertIsDisplayed()
        optionalConfirmationAction("Optional B $runId", "Retirar confirmación de sesión opcional")
            .assertIsDisplayed()

        val confirmedPlan = awaitRoomPlan(fixture.planId) { saved ->
            forecastDocument(saved)?.revision?.let { it > baselineForecast.revision } == true
        }
        val confirmedForecast = requireForecast(confirmedPlan)
        assertForecastBudgetAndFrozenDays(fixture.today, baselineForecast, confirmedForecast)
        assertNotEquals(
            "La confirmación futura de la opcional cambia su objetivo previsto productivo",
            baselineForecast.days.first { it.date == fixture.optionalADate },
            confirmedForecast.days.first { it.date == fixture.optionalADate },
        )

        optionalConfirmationAction("Optional A $runId", "Retirar confirmación de sesión opcional")
            .performClick()
        val undoneProgram = awaitRoomProgram(fixture.programId) { saved ->
            saved.optionalSessionConfirmations == listOf(preconfirmedB)
        }
        assertEquals("Retirar A conserva la confirmación durable de B", listOf(preconfirmedB), undoneProgram.optionalSessionConfirmations)
        optionalConfirmationAction("Optional A $runId", "Confirmar sesión opcional").assertIsDisplayed()
        optionalConfirmationAction("Optional B $runId", "Retirar confirmación de sesión opcional").assertIsDisplayed()

        val undonePlan = awaitRoomPlan(fixture.planId) { saved ->
            forecastDocument(saved)?.revision?.let { it > confirmedForecast.revision } == true
        }
        val undoneForecast = requireForecast(undonePlan)
        assertForecastBudgetAndFrozenDays(fixture.today, baselineForecast, undoneForecast)
        assertEquals("Al desconfirmar, vuelve la misma previsión calculada antes del cambio", baselineForecast.days, undoneForecast.days)
        assertNotEquals(
            "Desconfirmar la opcional retira su gasto del reforecast",
            confirmedForecast.days.first { it.date == fixture.optionalADate },
            undoneForecast.days.first { it.date == fixture.optionalADate },
        )
        assertEquals(fixture.weekId, viewModel.selectedWeekMeta.value?.id)
    }

    private fun installFixture(preconfirmOptionalB: Boolean = false): CalendarFixture {
        val today = LocalDate.now()
        val requiredDate = today.plusDays(1)
        val optionalADate = today.plusDays(2)
        val optionalBDate = if (preconfirmOptionalB) optionalADate else today.plusDays(3)
        val movedRequiredDate = today.plusDays(4)
        val programId = "m4-calendar-$runId-program"
        val planId = "m4-calendar-$runId-plan"
        val weekId = "$programId-week"
        val requiredSessionId = "$programId-required"
        val optionalAId = "$programId-optional-a"
        val optionalBId = "$programId-optional-b"
        val requiredDay = requiredDate.dayOfWeek.value

        ownProgramId = programId
        ownPlanId = planId
        val fixture = CalendarFixture(
            programId = programId,
            planId = planId,
            weekId = weekId,
            today = today,
            requiredSessionId = requiredSessionId,
            requiredDay = requiredDay,
            requiredDate = requiredDate,
            movedRequiredDate = movedRequiredDate,
            optionalAId = optionalAId,
            optionalADate = optionalADate,
            optionalBId = optionalBId,
            optionalBDate = optionalBDate,
        )

        val program = fixtureProgram(
            programId = programId,
            weekId = weekId,
            today = today,
            requiredDate = requiredDate,
            optionalADate = optionalADate,
            optionalBDate = optionalBDate,
            requiredSessionId = requiredSessionId,
            optionalAId = optionalAId,
            optionalBId = optionalBId,
            initialOptionalConfirmations = if (preconfirmOptionalB) {
                listOf(OptionalSessionConfirmation(optionalADate.toString(), optionalBId))
            } else {
                emptyList()
            },
        )
        assertTrue("El fixture usa un programa avanzado calendarizado", ProgramCalendarEngine.isCalendarized(program))
        val projectedDates = ProgramCalendarEngine.project(program).weeks.single().trainingDayDates
        assertEquals(requiredDate, projectedDates[requiredDay])
        assertEquals(optionalADate, projectedDates[optionalADate.dayOfWeek.value])
        assertEquals(optionalBDate, projectedDates[optionalBDate.dayOfWeek.value])

        val requiredSession = program.macrocycles.single().blocks.single().mesocycles.single()
            .weeks.single().sessions.first { it.id == requiredSessionId }
        val catalogInfo = resolveCatalogExerciseInfo(
            catalogConfigurationId = "bench_press__barbell",
            exerciseDbId = null,
            exerciseId = null,
            exerciseName = null,
        )
        assertNotNull("La configuración real de banca debe existir en el catálogo", catalogInfo)

        // Quita el plan activo previo antes de cambiar la receta/calendario: el
        // coordinador nunca escribirá el plan del QA usando este fixture.
        activePlanTouched = true
        nutrition.setActiveNutritionPlanId(null)
        awaitCondition { db.nutritionDao().getActiveState()?.activePlanId == null }

        programs.addProgram(program)
        awaitRoomProgram(programId) { it.id == programId }
        // La entrada temporal de cola hace observable en Room que el cleanup
        // async de deleteProgram terminó; se elimina y restaura al salir.
        programs.addProgramToQueue(programId)
        awaitCondition {
            db.settingsDao().get()?.toSettings()?.programQueueIds?.contains(programId) == true
        }

        // La estimación de energía usa peso corporal. Se fija solo este campo
        // para hacer el fixture determinista y se restaura durablemente al salir.
        if (programs.settings.value.userVitals.weight != FIXTURE_BODY_WEIGHT_KG) {
            settingsUserVitalsTouched = true
            programs.updateSettings { current ->
                current.copy(userVitals = current.userVitals.copy(weight = FIXTURE_BODY_WEIGHT_KG))
            }
            awaitCondition {
                db.settingsDao().get()?.toSettings()?.userVitals?.weight == FIXTURE_BODY_WEIGHT_KG
            }
        }

        val estimatedLoad = NutritionTrainingCalendarAdapter.estimateVariantKcal(
            requiredSession,
            programs.settings.value,
        )
        assertNotNull("El fixture de fuerza debe producir carga estimable por la API productiva", estimatedLoad)
        assertTrue("La estimación catalogada debe ser positiva", estimatedLoad!! > 0.0)

        activeProgramTouched = true
        programs.updateActiveProgramState(
            ActiveProgramState(
                programId = programId,
                currentWeekId = weekId,
                currentMacrocycleId = "$programId-macro",
                currentBlockId = "$programId-block",
                currentMesocycleId = "$programId-meso",
            ),
        )
        awaitCondition {
            db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId == programId
        }

        val initialPlan = fixtureNutritionPlan(planId, today)
        val initialDocument = requireForecast(initialPlan)
        runBlocking {
            withTimeout(60_000L) {
                db.withTransaction {
                    val plans = db.nutritionDao().getAllPlans().map { row ->
                        row.toNutritionPlan().copy(isActive = false).toEntity()
                    }
                    db.nutritionDao().activatePlanAtomic(
                        planId,
                        plans + initialPlan.toEntity(),
                    )
                }
                nutrition.publishNutritionPlanCommit()
            }
        }

        val baselinePlan = awaitRoomPlan(planId) { saved ->
            val document = forecastDocument(saved)
            document != null && document.revision > initialDocument.revision &&
                document.days != initialDocument.days
        }
        val baseline = requireForecast(baselinePlan)
        assertNotEquals("La previsión activa se escribió automáticamente en Room", initialDocument.days, baseline.days)
        assertEquals("El plan de la fixture quedó activo en Room", planId, runBlocking { db.nutritionDao().getActiveState()?.activePlanId })
        return fixture
    }

    private fun fixtureProgram(
        programId: String,
        weekId: String,
        today: LocalDate,
        requiredDate: LocalDate,
        optionalADate: LocalDate,
        optionalBDate: LocalDate,
        requiredSessionId: String,
        optionalAId: String,
        optionalBId: String,
        initialOptionalConfirmations: List<OptionalSessionConfirmation>,
    ): Program {
        val sessions = listOf(
            fixtureSession(requiredSessionId, "Requerida $runId", requiredDate, SessionRequirement.REQUIRED),
            fixtureSession(optionalAId, "Optional A $runId", optionalADate, SessionRequirement.OPTIONAL),
            fixtureSession(optionalBId, "Optional B $runId", optionalBDate, SessionRequirement.OPTIONAL),
        )
        val week = ProgramWeek(
            id = weekId,
            name = "Semana calendarizada $runId",
            sessions = sessions,
            trainingDayDates = mapOf(
                requiredDate.dayOfWeek.value to requiredDate.toString(),
                optionalADate.dayOfWeek.value to optionalADate.toString(),
                optionalBDate.dayOfWeek.value to optionalBDate.toString(),
            ),
        )
        return Program(
            id = programId,
            name = "M4 calendario $runId",
            structure = ProgramStructure.COMPLEX,
            startDay = 1,
            weekDays = 7,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = today.toString(),
                weekStartDay = today.dayOfWeek.value,
                trainingDays = sessions.mapNotNull { it.dayOfWeek }.toSet(),
                mode = com.example.kpkn.data.models.ScheduleMode.DATED,
            ),
            optionalSessionConfirmations = initialOptionalConfirmations,
            macrocycles = listOf(
                Macrocycle(
                    id = "$programId-macro",
                    name = "Macro $runId",
                    blocks = listOf(
                        Block(
                            id = "$programId-block",
                            name = "Bloque $runId",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "$programId-meso",
                                    name = "Meso $runId",
                                    weeks = listOf(week),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun fixtureSession(
        id: String,
        name: String,
        date: LocalDate,
        requirement: SessionRequirement,
    ): Session = Session(
        id = id,
        name = name,
        dayOfWeek = date.dayOfWeek.value,
        assignedDays = listOf(date.dayOfWeek.value),
        requirement = requirement,
        exercises = listOf(
            Exercise(
                id = "$id-exercise",
                name = "Press banca",
                exerciseDbId = "bench_press__barbell",
                exerciseId = "bench_press__barbell",
                canonicalExerciseId = "bench_press__barbell",
                catalogConfigurationId = "bench_press__barbell",
                sets = (0 until FIXTURE_SET_COUNT).map { index ->
                    ExerciseSet(
                        id = "$id-set-$index",
                        targetReps = 8,
                        targetRPE = 8.0,
                        weight = 100.0,
                        loadModeV2 = LoadModeV2.LOAD,
                    )
                },
                restTime = 150,
            ),
        ),
    )

    private fun fixtureNutritionPlan(planId: String, today: LocalDate): NutritionPlan {
        val effectiveDate = today.minusDays(1)
        val targets = (0 until FORECAST_DAYS).map { offset ->
            NutritionEditorDayTarget(
                date = effectiveDate.plusDays(offset.toLong()),
                calorieTargetKcal = BASE_KCAL,
                proteinG = BASE_PROTEIN,
                carbsG = BASE_CARBS,
                fatG = BASE_FAT,
            )
        }
        return NutritionPlan(
            id = planId,
            name = "Plan calendario $runId",
            calorieTarget = BASE_KCAL,
            proteinGoal = BASE_PROTEIN,
            carbGoal = BASE_CARBS,
            fatGoal = BASE_FAT,
            isActive = true,
            createdAt = Instant.now().toString(),
            direction = PlanDirection.MAINTENANCE,
            calculationOrigin = CalculationOrigin.PLAN,
            calculationSnapshot = NutritionPlanCalculationSnapshot(
                engineVersion = "eer-2023-v1",
                formula = "calendar-forecast-fixture",
                inputs = mapOf(
                    WEEKLY_FORECAST_KEY to encodeWeeklyForecast(
                        targets = targets,
                        revision = 1,
                        effectiveDate = effectiveDate,
                    ),
                    "weeklyDistribution" to "VARIABLE",
                ),
                calculatedAt = Instant.now().toString(),
            ),
        )
    }

    private fun mountRealDayView(fixture: CalendarFixture): ProgramDetailViewModel {
        val holder = mutableStateOf<ProgramDetailViewModel?>(null)
        lateinit var vm: ProgramDetailViewModel
        composeRule.runOnUiThread {
            vm = ProgramDetailViewModel(fixture.programId)
            vm.selectWeek(fixture.weekId)
            detailViewModel = vm
            viewModelStore.put("m4-program-detail-$runId", vm)
            holder.value = vm
        }
        composeRule.setContent {
            val viewModel = holder.value
            if (viewModel != null) {
                val program by viewModel.program.collectAsState()
                val selectedWeek by viewModel.selectedWeekMeta.collectAsState()
                val sessions by viewModel.displayedSessions.collectAsState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    program?.let { current ->
                        DayView(
                            program = current,
                            isSimpleProgram = false,
                            isCalendarized = ProgramCalendarEngine.isCalendarized(current),
                            selectedWeek = selectedWeek,
                            sessions = sessions,
                            onEditSession = {},
                            onAddSession = {},
                            onDeleteSession = {},
                            onStartWorkout = {},
                            onApplySessionsLayout = { viewModel.replaceWeekSessions(fixture.weekId, it) },
                            onUpdateStartDay = { _, _, _ -> },
                            onUpdateWeekMetadata = viewModel::updateWeekMetadata,
                            onUpdateTrainingDayDate = viewModel::updateWeekTrainingDayDate,
                            onToggleOptionalConfirmation = viewModel::toggleOptionalSessionConfirmation,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(30_000L) {
            vm.program.value?.id == fixture.programId &&
                vm.selectedWeekMeta.value?.id == fixture.weekId &&
                vm.selectedWeekMeta.value?.trainingDayDates?.get(fixture.requiredDay) == fixture.requiredDate.toString()
        }
        return vm
    }

    private fun expandDay(date: LocalDate) {
        val header = dayHeader(date)
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(header))
        val shortDay = DAYS_OF_WEEK[date.dayOfWeek.value - 1].short
        composeRule.onNodeWithText(shortDay, useUnmergedTree = true)
            .performTouchInput { click() }
    }

    /** Selecciona la acción de confirmación que comparte tarjeta con la sesión nombrada. */
    private fun optionalConfirmationAction(sessionName: String, contentDescription: String) =
        composeRule.onNode(
            hasContentDescription(contentDescription) and hasAnySibling(hasText(sessionName)),
        )

    private fun dayHeader(date: LocalDate): String {
        val day = DAYS_OF_WEEK[date.dayOfWeek.value - 1]
        return "${day.name} · ${date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
    }

    private fun assertForecastBudgetAndFrozenDays(
        fixtureToday: LocalDate,
        before: WeeklyForecastDocument,
        after: WeeklyForecastDocument,
    ) {
        val beforeByDate = before.days.associateBy { it.date }
        val afterByDate = after.days.associateBy { it.date }
        for (date in listOf(fixtureToday.minusDays(1), fixtureToday)) {
            assertEquals("Objetivo con macros de $date permanece byte a byte", beforeByDate.getValue(date), afterByDate.getValue(date))
            assertEquals("El objetivo congelado de $date conserva las kcal iniciales", BASE_KCAL, afterByDate.getValue(date).calorieTargetKcal)
            assertEquals(BASE_PROTEIN, afterByDate.getValue(date).proteinG)
            assertEquals(BASE_CARBS, afterByDate.getValue(date).carbsG)
            assertEquals(BASE_FAT, afterByDate.getValue(date).fatG)
        }
        assertEquals("El periodo durable contiene exactamente siete días", FORECAST_DAYS, after.days.size)
        assertEquals("Presupuesto semanal exacto", FORECAST_DAYS * BASE_KCAL, after.days.sumOf { it.calorieTargetKcal })
        assertEquals(
            "Presupuesto restante exacto desde mañana",
            (FORECAST_DAYS - 2) * BASE_KCAL,
            after.days.filter { it.date.isAfter(fixtureToday) }.sumOf { it.calorieTargetKcal },
        )
    }

    private fun findWeek(program: Program, weekId: String): ProgramWeek =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.first { it.id == weekId }

    private fun roomProgram(programId: String): Program = runBlocking {
        db.programDao().getById(programId)?.toProgram()
            ?: error("Room no contiene el programa propio $programId")
    }

    private fun awaitRoomProgram(programId: String, predicate: (Program) -> Boolean): Program = runBlocking {
        withTimeout(30_000L) {
            while (true) {
                val program = db.programDao().getById(programId)?.toProgram()
                if (program != null && predicate(program)) return@withTimeout program
                delay(25L)
            }
            error("inaccesible")
        }
    }

    private fun roomPlan(planId: String): NutritionPlan = runBlocking {
        db.nutritionDao().getAllPlans().firstOrNull { it.id == planId }?.toNutritionPlan()
            ?: error("Room no contiene el plan propio $planId")
    }

    private fun awaitRoomPlan(planId: String, predicate: (NutritionPlan) -> Boolean): NutritionPlan = runBlocking {
        withTimeout(90_000L) {
            while (true) {
                val plan = db.nutritionDao().getAllPlans()
                    .firstOrNull { it.id == planId }
                    ?.toNutritionPlan()
                if (plan != null && predicate(plan)) return@withTimeout plan
                delay(25L)
            }
            error("inaccesible")
        }
    }

    private fun forecastDocument(plan: NutritionPlan): WeeklyForecastDocument? =
        plan.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecastDocument)

    private fun requireForecast(plan: NutritionPlan): WeeklyForecastDocument =
        forecastDocument(plan) ?: error("Room no contiene weeklyForecast válido para ${plan.id}")

    /** Espera activa con límite: verifica Room, no una caché ni sleeps fijos. */
    private fun awaitCondition(condition: suspend () -> Boolean) {
        runBlocking {
            withTimeout(60_000L) {
                while (!condition()) delay(25L)
            }
        }
    }

    private companion object {
        const val BASE_KCAL = 2_000
        const val BASE_PROTEIN = 150
        const val BASE_CARBS = 200
        const val BASE_FAT = 60
        const val FIXTURE_BODY_WEIGHT_KG = 80.0
        const val FORECAST_DAYS = 7
        const val FIXTURE_SET_COUNT = 6
    }
}
