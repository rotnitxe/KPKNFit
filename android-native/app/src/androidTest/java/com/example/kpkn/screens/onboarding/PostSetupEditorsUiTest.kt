package com.example.kpkn.screens.onboarding

import android.content.Context
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasInsertTextAtCursorAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.PersistenceFactory
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.nutrition.NutritionGoalSource
import com.example.kpkn.domain.nutrition.atwaterKcal
import com.example.kpkn.domain.nutrition.planDayTargetForDate
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.ProgramPersistNormalizer
import com.example.kpkn.screens.nutrition.NutritionPlanEditorScreen
import com.example.kpkn.screens.programs.ProgramEditorScreen
import com.example.kpkn.screens.programs.ProgramEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * Cierre de validación POST-ALTA (M9): los editores directos con VM/root
 * REALES sobre Room QA, entrando desde un alta real hecha por
 * [com.example.kpkn.data.onboarding.SetupCommitCoordinator] — mismo motor y
 * mismo contrato que la app ([ProgramExecutionContract.requireExecutable] y
 * programa + plan activos, salvo TrackingOnly). Fixture de programa mínimo y
 * válido (no arbitrario): normalizado con el mismo normalizador del producto
 * y con sesión requerida de prescripción ejecutable.
 *
 * Dos métodos, ambos con clicks reales de pantalla:
 *  1. Editor de programa: guarda el nombre → reabre → sesiones/IDs/receta
 *     intactos; y si el programa desaparece, el editor informa el error en vez
 *     de resucitar una fila en blanco.
 *  2. Editor de nutrición («Objetivos propios»): editar un macro deja HOY y
 *     PASADO congelados en su snapshot (insert-once) y recalcula el
 *     presupuesto desde MAÑANA.
 *
 * Datos: entradas con PREFIJO único por ejecución, cleanup SOLO de las
 * entradas propias y restauración del estado activo/ajustes previos del QA.
 * Nada se muta antes del guard de proceso.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PostSetupEditorsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val runId: String = UUID.randomUUID().toString().take(8)

    private lateinit var context: Context
    private lateinit var db: KpknDatabase
    private lateinit var programs: ProgramRepository
    private lateinit var nutrition: NutritionRepository
    private lateinit var persistence: PersistenceFactory

    /** Estado QA previo a la ejecución; se restaura en el cleanup. */
    private var previousActiveProgram: ActiveProgramState? = null
    private var previousActivePlanId: String? = null
    private var previousSettings: Settings? = null

    /** Entradas propias de ESTA ejecución (prefijo único): solo se borran ellas. */
    private var ownProgramId: String? = null
    private var ownPlanId: String? = null

    /** true solo si el guard pasó y el estado QA quedó capturado: el cleanup
     *  no restaura nada cuando el test fue descartado (Assume) por seguridad. */
    private var qaStateCaptured = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        guardQaProcessBeforeMutatingData()

        programs = ProgramRepository.init(context)
        runBlocking { withTimeout(60_000L) { programs.isReady.first { it } } }
        nutrition = NutritionRepository.init(context)
        persistence = persistenceFactory(context)
        db = KpknDatabase.getInstance(context)

        // El alta activa el programa; con una sesión en curso la app lo rechaza.
        val ongoing = runBlocking { db.stateDao().getOngoingWorkout() }
        Assume.assumeTrue(
            "Hay una sesión en curso en QA; el alta no puede activar un plan.",
            ongoing == null,
        )

        // Estado previo leído de Room (fuente comprometida, no la caché).
        previousActiveProgram = runBlocking { db.stateDao().getActiveProgram()?.toActiveProgramState() }
        previousActivePlanId = runBlocking { db.nutritionDao().getActiveState()?.activePlanId }
        previousSettings = programs.settings.value
        qaStateCaptured = true
    }

    /**
     * Guard ANTES de mutar cualquier dato: solo corre en el usuario de QA 10 —
     * `Process.myUid() / 100000 == 10` — y como la app bajo prueba (mismo uid
     * que el paquete, con appId de app normal: nunca root/system/shell).
     *
     * El Usuario 0 (Owner0) guarda los datos originales y su backup debe quedar
     * protegido: con cualquier otro usuario (`uid / 100000 != 10`) el test se
     * descarta vía [Assume] y no se escribe nada.
     */
    private fun guardQaProcessBeforeMutatingData() {
        val uid = Process.myUid()
        val targetUid = context.applicationInfo.uid
        val perUserBase = uid / 100_000
        val appId = uid % 100_000
        val isQaUser10 = perUserBase == 10
        val isQaAppProcess = isQaUser10 && uid == targetUid && appId in 10_000..19_999
        Assume.assumeTrue(
            "Proceso no QA (usuario 10): uid=$uid, uid/100000=$perUserBase (se requiere 10), " +
                "appId=$appId, target=$targetUid. No se mutan datos.",
            isQaAppProcess,
        )
    }

    @After
    fun restoreOwnQaEntries() {
        // Si el guard descartó el test antes de capturar el estado, no se mutó
        // nada: el cleanup no debe tocar (ni inicializar) el QA.
        if (!qaStateCaptured) return

        val failures = mutableListOf<String>()

        fun step(name: String, block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += "$name: ${error.message ?: error::class.java.simpleName}"
            }
        }

        // 1) Solo entradas propias: el programa de ESTA ejecución.
        ownProgramId?.let { programId ->
            step("borrar programa propio") {
                programs.deleteProgram(programId)
                awaitCondition { db.programDao().getById(programId) == null }
            }
        }
        // 2) Solo entradas propias: el plan de ESTA ejecución.
        ownPlanId?.let { planId ->
            step("borrar plan propio") {
                nutrition.deleteNutritionPlan(planId)
                awaitCondition { db.nutritionDao().getAllPlans().none { it.id == planId } }
            }
        }
        // 3) Restaurar el plan activo previo (la alta desactivó el anterior).
        step("restaurar plan activo") {
            val previous = previousActivePlanId
            if (previous != null) nutrition.activatePlan(previous)
            else nutrition.setActiveNutritionPlanId(null)
        }
        // 4) Restaurar el programa activo previo (borrar el propio ya limpia el nuestro).
        step("restaurar programa activo") {
            val previous = previousActiveProgram
            when {
                previous != null -> programs.updateActiveProgramState(previous)
                programs.activeProgramState.value != null -> programs.clearActiveProgram()
            }
        }
        // 5) Restaurar SOLO los ajustes que el editor de nutrición refleja.
        step("restaurar ajustes") {
            val previous = previousSettings ?: return@step
            programs.updateSettings { current ->
                current.copy(
                    dailyCalorieGoal = previous.dailyCalorieGoal,
                    dailyProteinGoal = previous.dailyProteinGoal,
                    dailyCarbGoal = previous.dailyCarbGoal,
                    dailyFatGoal = previous.dailyFatGoal,
                    calorieGoalObjective = previous.calorieGoalObjective,
                    nutritionTrackingOnly = previous.nutritionTrackingOnly,
                )
            }
            awaitCondition {
                val stored = db.settingsDao().get()?.toSettings()
                stored?.dailyCalorieGoal == previous.dailyCalorieGoal &&
                    stored?.nutritionTrackingOnly == previous.nutritionTrackingOnly
            }
        }

        if (failures.isNotEmpty()) {
            throw AssertionError("Restauración QA incompleta: ${failures.joinToString(" | ")}")
        }
    }

    // ─── Método 1: editor de programa ────────────────────────────────────────

    @Test
    fun programEditorSavesNameAndReopenKeepsSessionsIdsAndRecipe() {
        val programId = "m9-psu-$runId-program"
        val programName = "M9 QA $runId"
        runAlta(
            program = fixtureProgram(programId, programName),
            plan = null,
            activateProgram = true,
            activateNutrition = false,
        )

        // Copia INMUTABLE del programa tal como quedó en Room tras el alta. La
        // comparación posterior es igualdad total del data class, así que
        // cubre sesiones, IDs, receta y calendario, además de cualquier campo
        // con default que añada M6 (p. ej. la confirmación por fecha en JSON).
        val programBefore = awaitRoomProgram(programId)
            ?: error("Room no tiene la copia inicial del programa")
        val savedName = "M9 reeditado $runId"

        val generation = mutableStateOf(0)
        val editorVm = mutableStateOf<ProgramEditorViewModel?>(null)
        val savedProgramId = mutableStateOf<String?>(null)
        composeRule.setContent {
            key(generation.value) {
                val viewModel = remember { ProgramEditorViewModel(programs).also { editorVm.value = it } }
                ProgramEditorScreen(
                    programId = programId,
                    onSaved = { savedProgramId.value = it },
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitUntil(30_000L) { editorVm.value?.uiState?.value?.isLoading == false }
        assertTrue("El editor abre en modo edición", editorVm.value!!.uiState.value.isEditMode)

        // Clicks reales: campo «Nombre del programa» y «Guardar» de la barra.
        fieldNearLabel("Nombre del programa").performTextReplacement(savedName)
        composeRule.onAllNodesWithText("Guardar").onFirst().performClick()

        // Éxito SOLO con la fila durable en Room y con el navIntent real.
        composeRule.waitUntil(30_000L) {
            programs.programs.value.firstOrNull { it.id == programId }?.name == savedName
        }
        composeRule.waitUntil(30_000L) { savedProgramId.value != null }
        assertEquals("onSaved entrega el id real de la ruta de detalle", programId, savedProgramId.value)
        assertEquals(savedName, awaitRoomProgram(programId)?.name)

        // Reapertura con VM y composición nuevos: el estado recargado conserva
        // sesiones, IDs de la jerarquía y receta; solo cambia el nombre.
        val firstVm = editorVm.value
        composeRule.runOnUiThread { generation.value += 1 }
        composeRule.waitUntil(30_000L) {
            editorVm.value !== firstVm && editorVm.value?.uiState?.value?.isLoading == false
        }
        assertEquals(savedName, editorVm.value!!.uiState.value.name)
        assertEquals(
            "Solo cambia el nombre: la copia inmutable conserva sesiones, IDs, " +
                "receta y calendario (y todo campo con default de M6)",
            programBefore.copy(name = savedName),
            awaitRoomProgram(programId),
        )

        // Error UX duradero: el programa desaparece mientras se edita y el
        // editor lo informa sin resucitar una fila en blanco.
        programs.deleteProgram(programId)
        composeRule.onAllNodesWithText("Guardar").onFirst().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("El programa ya no existe."), 30_000L)
        awaitCondition { db.programDao().getById(programId) == null }
    }

    // ─── Método 2: editor de nutrición (objetivos propios) ───────────────────

    @Test
    fun nutritionEditorSelfDefinedGoalsFreezeTodayAndPastAndMoveBudgetFromTomorrow() {
        val programId = "m9-psu-$runId-program"
        val planId = "m9-psu-$runId-plan"
        runAlta(
            program = fixtureProgram(programId, "M9 QA $runId"),
            plan = fixturePlan(planId),
            activateProgram = true,
            activateNutrition = true,
        )

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        // Snapshots de HOY y PASADO para ESTE plan (INSERT IGNORE: si el QA ya
        // tiene fila propia, se conserva la suya y nunca se pisa).
        snapshotInForce(planId, today, FIXTURE_KCAL)
        snapshotInForce(planId, yesterday, FIXTURE_KCAL)
        runBlocking { nutrition.publishNutritionPlanCommit() }

        val planBefore = nutrition.nutritionPlans.value.firstOrNull { it.id == planId }
        assertNotNull("El alta publicó el plan en la caché", planBefore)
        val todayBefore = runBlocking { nutrition.getDailyGoalSnapshot(today.toString()) }
        val yesterdayBefore = runBlocking { nutrition.getDailyGoalSnapshot(yesterday.toString()) }
        assertNotNull("HOY tiene snapshot congelado", todayBefore)
        assertNotNull("el PASADO tiene snapshot congelado", yesterdayBefore)
        val tomorrowBefore = runBlocking { nutrition.getDailyGoalSnapshot(tomorrow.toString()) }
        val budgetBefore = planDayTargetForDate(
            planBefore!!,
            tomorrow,
            NutritionGoalSource.PLAN_FORECAST,
        ).calorieTargetKcal

        val done = mutableStateOf(false)
        composeRule.setContent {
            NutritionPlanEditorScreen(
                planId = planId,
                pendingDraftId = null,
                onDone = { done.value = true },
                onBack = {},
            )
        }
        // La primera tarjeta marca «cargado» (el spinner no la pinta).
        composeRule.waitUntilAtLeastOneExists(hasText("Dirección"), 30_000L)

        // Clicks reales: el scroll lo manda el CONTENEDOR scrollable (LazyColumn,
        // único nodo con ScrollToNode); el label es un Text plano y no tiene
        // esa acción, por eso no se le puede pedir el desplazamiento.
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(MACRO_LABEL))
        // Tras el scroll se confirma el campo aislado en pantalla y se reescribe.
        val macroField = fieldNearLabel(MACRO_LABEL)
        macroField.assertIsDisplayed()
        macroField.performTextReplacement(NEW_PROTEIN.toString())
        // En pantalla la base revisada adopta el macro con su total Atwater.
        val expectedBaseKcal = atwaterKcal(
            NEW_PROTEIN.toDouble(),
            FIXTURE_CARBS.toDouble(),
            FIXTURE_FAT.toDouble(),
        )
        composeRule.waitUntilAtLeastOneExists(
            hasText("Base revisada que se guardará tal cual: $expectedBaseKcal kcal", substring = true),
            30_000L,
        )
        composeRule.onAllNodesWithText("Guardar").onFirst().performClick()

        // Éxito solo con el plan durable en Room.
        composeRule.waitUntil(30_000L) {
            nutrition.nutritionPlans.value.firstOrNull { it.id == planId }?.proteinGoal == NEW_PROTEIN
        }
        composeRule.waitUntil(30_000L) { done.value }
        assertTrue("onDone dispara la navegación de salida", done.value)

        val planAfter = runBlocking { db.nutritionDao().getAllPlans() }
            .firstOrNull { it.id == planId }
            ?.toNutritionPlan()
        assertNotNull("el plan sigue en Room tras el guardado", planAfter)
        // Macro editable durable (y el total Atwater que mostró la UI) y la
        // procedencia sigue siendo de objetivos propios.
        assertEquals(NEW_PROTEIN, planAfter!!.proteinGoal)
        assertEquals(
            atwaterKcal(NEW_PROTEIN.toDouble(), FIXTURE_CARBS.toDouble(), FIXTURE_FAT.toDouble()),
            planAfter.calorieTarget,
        )
        assertEquals(CalculationOrigin.MANUAL, planAfter.calculationOrigin)

        // HOY y PASADO: sus snapshots siguen idénticos (insert-once).
        assertEquals(
            "HOY no se reescribe al editar el plan",
            todayBefore,
            runBlocking { nutrition.getDailyGoalSnapshot(today.toString()) },
        )
        assertEquals(
            "el PASADO no se reescribe al editar el plan",
            yesterdayBefore,
            runBlocking { nutrition.getDailyGoalSnapshot(yesterday.toString()) },
        )
        // Cuando la evidencia de HOY pertenece a ESTE plan, el plan editado la
        // conserva también en su previsión (si el QA ya tenía fila ajena, no
        // se fija y esta comprobación no aplica).
        if (todayBefore?.planId == planId) {
            assertEquals(
                todayBefore!!.calorieTargetKcal,
                planDayTargetForDate(planAfter, today, NutritionGoalSource.PLAN_FORECAST).calorieTargetKcal,
            )
        }

        // MAÑANA en adelante: editar el plan no crea ni cambia snapshots del
        // futuro, así que su presupuesto sale del plan editado.
        assertEquals(
            "editar el plan no toca snapshots del futuro",
            tomorrowBefore,
            runBlocking { nutrition.getDailyGoalSnapshot(tomorrow.toString()) },
        )
        val budgetAfter = planDayTargetForDate(
            planAfter,
            tomorrow,
            NutritionGoalSource.PLAN_FORECAST,
        ).calorieTargetKcal
        assertTrue(
            "El presupuesto de mañana debe recalcularse con los objetivos nuevos " +
                "(antes=$budgetBefore, ahora=$budgetAfter)",
            budgetAfter != budgetBefore,
        )
    }

    // ─── Fixtures y utilidades ───────────────────────────────────────────────

    /**
     * Fixture de programa MÍNIMO y válido, con la misma normalización que aplica
     * el producto y con la sesión requerida de prescripción ejecutable: pasa el
     * contrato de alta sin construir un árbol arbitrario.
     */
    private fun fixtureProgram(id: String, name: String): Program {
        val session = Session(
            id = "$id-session",
            name = "Día 1",
            dayOfWeek = 1,
            exercises = listOf(
                Exercise(
                    id = "$id-exercise",
                    name = "Sentadilla",
                    sets = listOf(ExerciseSet(id = "$id-set", targetReps = 5, targetRPE = 7.0)),
                ),
            ),
        )
        val week = ProgramWeek(id = "$id-week", name = "Semana 1", sessions = listOf(session))
        val mesocycle = Mesocycle(id = "$id-meso", name = "Meso 1", weeks = listOf(week))
        val block = Block(id = "$id-block", name = "Bloque 1", mesocycles = listOf(mesocycle))
        val macrocycle = Macrocycle(id = "$id-macro", name = "Macro 1", blocks = listOf(block))
        return Program(
            id = id,
            name = name,
            coverImage = "gradient://ember",
            structure = ProgramStructure.SIMPLE,
            mode = ProgramMode.HYPERTROPHY,
            startDay = 1,
            weekDays = 3,
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1)),
            macrocycles = listOf(macrocycle),
        )
    }

    /** Plan de nutrición en objetivos propios (MANUAL → SELF_DEFINED en el editor). */
    private fun fixturePlan(id: String): NutritionPlan = NutritionPlan(
        id = id,
        name = "Plan M9 $runId",
        goalType = GoalMetric.WEIGHT,
        calorieTarget = FIXTURE_KCAL,
        proteinGoal = FIXTURE_PROTEIN,
        carbGoal = FIXTURE_CARBS,
        fatGoal = FIXTURE_FAT,
        isActive = true,
        createdAt = java.time.Instant.now().toString(),
        direction = PlanDirection.MAINTENANCE,
        calculationOrigin = CalculationOrigin.MANUAL,
    )

    /** Alta real por el coordinador de persistencia del setup (contrato de la app). */
    private fun runAlta(
        program: Program,
        plan: NutritionPlan?,
        activateProgram: Boolean,
        activateNutrition: Boolean,
    ) {
        ownProgramId = program.id
        ownPlanId = plan?.id
        val executable = ProgramPersistNormalizer.normalize(program).normalizedIdentityFields()
        ProgramExecutionContract.requireExecutable(executable)
        runBlocking {
            withTimeout(60_000L) {
                persistence.commits.commit(
                    SetupCommitRequest(
                        commitId = "m9-psu-$runId-commit-${UUID.randomUUID()}",
                        draftId = null,
                        settings = programs.settings.value,
                        program = executable,
                        nutritionPlan = plan,
                        activateProgram = activateProgram,
                        activateNutrition = activateNutrition,
                    ),
                )
            }
        }
        assertNotNull("Room tiene la fila del programa tras el alta", awaitRoomProgram(program.id))
    }

    /** Snapshot de un día: si el QA ya tiene fila se conserva (INSERT IGNORE). */
    private fun snapshotInForce(planId: String, date: LocalDate, kcal: Int): DailyGoalSnapshot? {
        val existing = runBlocking { nutrition.getDailyGoalSnapshot(date.toString()) }
        if (existing != null) return existing
        runBlocking {
            withTimeout(30_000L) {
                db.nutritionDao().insertDailyGoalSnapshot(
                    DailyGoalSnapshot(
                        date = date.toString(),
                        planId = planId,
                        calorieTargetKcal = kcal,
                        proteinGoalG = FIXTURE_PROTEIN,
                        carbGoalG = FIXTURE_CARBS,
                        fatGoalG = FIXTURE_FAT,
                        direction = PlanDirection.MAINTENANCE,
                        calculationOrigin = CalculationOrigin.PLAN,
                        capturedAtEpochMs = System.currentTimeMillis(),
                    ).toEntity(),
                )
            }
        }
        return runBlocking { nutrition.getDailyGoalSnapshot(date.toString()) }
    }

    /**
     * Campo de texto más cercano en Y al label dado. Evita índices y navegación
     * del árbol de semántica: el label es un Text plano, su campo es el de la
     * misma fila y, si hay varios campos a la misma altura (p. ej. los macros),
     * el que contiene horizontalmente al label.
     */
    private fun fieldNearLabel(label: String): SemanticsNodeInteraction {
        val labelCenter = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center
        val fields = composeRule.onAllNodes(hasInsertTextAtCursorAction()).fetchSemanticsNodes()
        if (fields.isEmpty()) error("No hay campos de texto en pantalla para el label '$label'")
        val minDy = fields.minOf { abs(it.boundsInRoot.center.y - labelCenter.y) }
        val sameRow = fields.filter { abs(it.boundsInRoot.center.y - labelCenter.y) == minDy }
        val chosen = sameRow.firstOrNull { field ->
            labelCenter.x >= field.boundsInRoot.left && labelCenter.x <= field.boundsInRoot.right
        } ?: sameRow.minByOrNull { abs(it.boundsInRoot.center.x - labelCenter.x) }
        ?: error("No hay campos de texto en pantalla para el label '$label'")
        return composeRule.onAllNodes(hasInsertTextAtCursorAction()).get(fields.indexOf(chosen))
    }

    private fun awaitRoomProgram(programId: String): Program? = runBlocking {
        withTimeout(30_000L) {
            var row = db.programDao().getById(programId)
            while (row == null) {
                delay(25)
                row = db.programDao().getById(programId)
            }
            row?.toProgram()
        }
    }

    /** Espera activa (con tope) a una condición de Room; sin sleeps fijos. */
    private fun awaitCondition(condition: suspend () -> Boolean) {
        runBlocking {
            withTimeout(30_000L) {
                while (!condition()) delay(25)
            }
        }
    }

    private companion object {
        const val FIXTURE_KCAL = 1800
        const val FIXTURE_PROTEIN = 150
        const val FIXTURE_CARBS = 200
        const val FIXTURE_FAT = 60
        const val NEW_PROTEIN = 160
        const val MACRO_LABEL = "Proteína (g)"
    }
}
