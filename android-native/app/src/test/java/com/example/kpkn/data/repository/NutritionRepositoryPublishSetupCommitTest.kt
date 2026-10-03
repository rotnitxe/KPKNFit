package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * C2 + C5 con el repositorio REAL: el alta del asistente escribe plan, plan activo y
 * meta de HOY en Room y después publica en las cachés que lee el Home. Si
 * `publishSetupCommit` no releyera los snapshots, el Home seguiría con la meta del
 * plan anterior hasta reiniciar la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionRepositoryPublishSetupCommitTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        // La publicación del commit usa Dispatchers.Main.immediate.
        Dispatchers.setMain(mainDispatcher)
        NutritionRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        NutritionRepository.closeInstance()
        // BodyProgressRepository abre la base de archivo: que no quede enlazada a otra prueba.
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun goal(planId: String, kcal: Int) =
        DailyGoalSnapshot(today.toString(), planId, kcal, 150, 200, 60, null, CalculationOrigin.PLAN, kcal.toLong())

    private fun activation(commitId: String, planId: String, kcal: Int) = SetupCommitRequest(
        commitId = commitId,
        draftId = null,
        settings = Settings(),
        program = null,
        nutritionPlan = NutritionPlan(id = planId, name = "Plan $planId", calorieTarget = kcal, isActive = true),
        activateProgram = false,
        activateNutrition = true,
        dailyGoalSnapshot = goal(planId, kcal),
    )

    private fun cachedTodayGoal(repository: NutritionRepository): DailyGoalSnapshot? =
        repository.dailyGoalSnapshots.value.firstOrNull { it.date == today.toString() }

    @Test
    fun setupCommitRepublishesPlansActiveIdAndTheGoalThatReplacedToday() = runBlocking {
        val repository = NutritionRepository.getInstance()
        // loadFromDb publica sus cachés (leídas antes de este test) justo antes de asignar los
        // alimentos: esperarlos evita que esa carga inicial pise lo que publica el commit.
        withTimeout(60_000L) { while (repository.foodDatabase.value.isEmpty()) delay(20) }
        val coordinator = SetupCommitCoordinator(repository.databaseForTests(), nutritionRepository = repository)

        coordinator.commit(activation("commit-a", "plan-a", 2000))
        assertEquals("plan-a", cachedTodayGoal(repository)?.planId)
        assertEquals(2000, cachedTodayGoal(repository)?.calorieTargetKcal)

        // Un plan DISTINTO el mismo día: la meta de hoy cambia en Room y en la caché del Home.
        coordinator.commit(activation("commit-b", "plan-b", 2600))

        assertEquals("plan-b", cachedTodayGoal(repository)?.planId)
        assertEquals(2600, cachedTodayGoal(repository)?.calorieTargetKcal)
        assertEquals("plan-b", repository.activeNutritionPlanId.value)
        // C5: solo el plan del estado activo queda marcado activo en la caché, aunque el
        // JSON viejo de plan-a siga diciendo isActive=true.
        assertEquals(listOf("plan-b"), repository.nutritionPlans.value.filter { it.isActive }.map { it.id })
    }
}
