package com.example.kpkn.screens.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U13 / C12: la solicitud de abrir el logger solo lleva comida si alguien la eligió a propósito
 * (botón de una comida concreta). Widget, share y deep link no la llevan: la pantalla aplica la de la hora.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionViewModelOpenRequestTest {

    /** Sin importación del catálogo: estas pruebas solo miran la solicitud del VM, no la base de alimentos. */
    private object NoCatalogImport : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    private lateinit var vm: NutritionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context, NoCatalogImport)
        ProgramRepository.initForTests(context)
        vm = NutritionViewModel()
    }

    @After
    fun tearDown() {
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun `a request without a meal leaves the choice to the screen`() {
        vm.requestFoodLoggerOpen(tab = 1)
        assertEquals(NutritionViewModel.FoodLoggerOpenRequest(tab = 1), vm.foodLoggerOpenRequest.value)
        assertNull(vm.foodLoggerOpenRequest.value?.mealType)
    }

    @Test
    fun `a request for a specific meal carries it`() {
        vm.requestFoodLoggerOpen(tab = 0, mealType = MealType.DINNER)
        assertEquals(MealType.DINNER, vm.foodLoggerOpenRequest.value?.mealType)
        assertEquals(0, vm.foodLoggerOpenRequest.value?.tab)
    }

    @Test
    fun `a shared description opens the logger without choosing a meal`() {
        vm.enqueueSharedDescription("  2 huevos y pan  ")
        val request = vm.foodLoggerOpenRequest.value
        assertEquals("2 huevos y pan", request?.description)
        assertNull(request?.mealType)
    }

    @Test
    fun `a request to edit carries the log id and a blank id is not an edit`() {
        vm.requestFoodLoggerOpen(tab = 0, editLogId = "log-7")
        val request = vm.foodLoggerOpenRequest.value
        assertEquals("log-7", request?.editLogId)
        assertEquals(0, request?.tab)
        assertNull(request?.description)
        assertNull(request?.mealType)

        vm.requestFoodLoggerOpen(tab = 0, editLogId = "   ")
        assertNull(vm.foodLoggerOpenRequest.value?.editLogId)
        vm.requestFoodLoggerOpen(tab = 1)
        assertEquals(NutritionViewModel.FoodLoggerOpenRequest(tab = 1), vm.foodLoggerOpenRequest.value)
    }

    @Test
    fun `consuming the request clears it and a later one starts fresh`() {
        vm.requestFoodLoggerOpen(tab = 0, mealType = MealType.BREAKFAST)
        vm.consumeFoodLoggerOpenRequest()
        assertNull(vm.foodLoggerOpenRequest.value)

        vm.requestFoodLoggerOpen(tab = 0)
        assertNull(vm.foodLoggerOpenRequest.value?.mealType)
    }
}
