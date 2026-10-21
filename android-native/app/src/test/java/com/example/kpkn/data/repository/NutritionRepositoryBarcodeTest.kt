package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.nutrition.customfood.CustomFoodForm
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * N3: búsqueda por código de barras sin red (alimentos propios primero, catálogo global después), alimentos propios durables y
 * su vuelta a la búsqueda. Base en memoria; el repositorio no arranca la carga del catálogo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionRepositoryBarcodeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<NutritionRepository>()
    private lateinit var db: KpknDatabase

    @Before fun setUp() {
        SyncRoomExecutor.install()
        db = KpknDatabase.createInMemory(context)
    }

    @After fun tearDown() = runBlocking {
        val job = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repositories.forEach { (job.get(it) as Job).cancelAndJoin() }
        runCatching { db.close() }
        SyncRoomExecutor.uninstall()
    }

    private fun repository(): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java, Boolean::class.javaPrimitiveType)
        ctor.isAccessible = true
        return ctor.newInstance(context, db, false).also { repositories += it }
    }

    private fun global(
        code: String?,
        name: String,
        foodId: String = "off_$code",
        verified: Double = 0.72,
        source: String = "OFF Chile",
        kcal: Double = 60.0,
        protein: Double = 3.0,
        carbs: Double = 5.0,
        fats: Double = 3.0,
        flagsJson: String = "[]",
    ) = GlobalFoodEntity(
        foodId = foodId, name = name, brand = "Marca", calories = kcal, protein = protein, carbs = carbs, fats = fats,
        source = source, sourcePriority = 80, verifiedScore = verified, sourceRecordId = code, qualityFlagsJson = flagsJson,
    )

    private fun customFood(code: String?, name: String = "Galletas Marilan de prueba", id: String = "custom_galletas"): FoodItem {
        val form = CustomFoodForm(
            name = name, brand = "Marilan", barcode = code.orEmpty(), servingAmount = "30", servingUnit = "g",
            calories = "135", protein = "2", carbs = "21", fats = "4.5",
        )
        return requireNotNull(CustomFoodValidator.validate(form, id).food)
    }

    private fun found(lookup: BarcodeLookup) = lookup as BarcodeLookup.Found

    // ── Catálogo global ──────────────────────────────────────────────────────

    @Test fun `encuentra un producto del catalogo por su codigo y dice que viene de la base global`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("4006381333931", "Leche entera")))
        val result = found(repository().findFoodByBarcode("4006381333931"))
        assertEquals("Leche entera", result.food.name)
        assertEquals(BarcodeOrigin.CATALOGO, result.origin)
        assertEquals("4006381333931", result.food.sourceRecordId)
    }

    @Test fun `encuentra el producto aunque la fila guarde el codigo sin ceros o el lector lo lea como UPC-A o EAN-13`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("36000291452", "Cereal sin ceros")))
        val repo = repository()
        // UPC-A de 12 dígitos y EAN-13 con cero delante: la fila lo guarda sin ceros.
        assertEquals("Cereal sin ceros", found(repo.findFoodByBarcode("036000291452")).food.name)
        assertEquals("Cereal sin ceros", found(repo.findFoodByBarcode("0036000291452")).food.name)
        assertEquals("Cereal sin ceros", found(repo.findFoodByBarcode(" 0360-0029 1452 ")).food.name)
    }

    @Test fun `encuentra un EAN-8 guardado en su forma corta`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("96385074", "Chicle")))
        assertEquals("Chicle", found(repository().findFoodByBarcode("96385074")).food.name)
    }

    @Test fun `con filas duplicadas gana la de mejor puntuacion verificada`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(
            listOf(
                global("4006381333931", "Duplicado flojo", foodId = "off_a", verified = 0.30),
                global("4006381333931", "Duplicado bueno", foodId = "off_b", verified = 0.90),
            ),
        )
        assertEquals("Duplicado bueno", found(repository().findFoodByBarcode("4006381333931")).food.name)
    }

    @Test fun `una ficha de USDA cuyo FDC ID coincide con los digitos de un codigo no se toma por producto`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("96385074", "Ficha USDA", foodId = "usda_96385074", source = "USDA")))
        assertEquals(BarcodeLookup.NotFound, repository().findFoodByBarcode("96385074"))
    }

    @Test fun `un codigo valido que nadie tiene es NotFound y un texto invalido es Invalid`() = runBlocking {
        val repo = repository()
        assertEquals(BarcodeLookup.NotFound, repo.findFoodByBarcode("7801234567894"))
        assertEquals(BarcodeLookup.Invalid, repo.findFoodByBarcode("7801234567890"))
        assertEquals(BarcodeLookup.Invalid, repo.findFoodByBarcode("123"))
        assertEquals(BarcodeLookup.Invalid, repo.findFoodByBarcode("https://example.com"))
    }

    @Test fun `un producto con macros vacios sale con la bandera INCOMPLETE y las banderas de la fila se conservan`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(
            listOf(
                global("4006381333931", "Sin macros", kcal = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0),
                global("7801234567894", "Energía dudosa", flagsJson = "[\"ENERGY_MISMATCH\",\"LOW_QUALITY\"]"),
            ),
        )
        val repo = repository()
        val empty = found(repo.findFoodByBarcode("4006381333931")).food
        assertTrue("INCOMPLETE" in empty.qualityFlags)
        assertEquals("nunca se inventan macros", 0.0, empty.calories, 0.0)
        val doubtful = found(repo.findFoodByBarcode("7801234567894")).food
        assertEquals(listOf("ENERGY_MISMATCH", "LOW_QUALITY"), doubtful.qualityFlags)
        assertEquals(60.0, doubtful.calories, 0.0)
    }

    // ── Alimentos propios ────────────────────────────────────────────────────

    @Test fun `un alimento propio con el codigo gana al del catalogo`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("4006381333931", "Versión del catálogo")))
        val repo = repository()
        repo.saveCustomFood(customFood("4006381333931", name = "Mi versión"))
        val result = found(repo.findFoodByBarcode("4006381333931"))
        assertEquals("Mi versión", result.food.name)
        assertEquals(BarcodeOrigin.PROPIO, result.origin)
        assertTrue(result.food.isCustom)
    }

    @Test fun `el alimento propio se encuentra con cualquier lectura del mismo codigo`() = runBlocking {
        val repo = repository()
        repo.saveCustomFood(customFood("036000291452"))
        listOf("036000291452", "0036000291452", " 0360-0029 1452 ").forEach {
            assertEquals("lectura $it", "Galletas Marilan de prueba", (repo.findFoodByBarcode(it) as? BarcodeLookup.Found)?.food?.name)
        }
    }

    @Test fun `guardar espera a Room y publica, y reintentar no duplica`() = runBlocking {
        val repo = repository()
        val food = customFood("4006381333931")
        repo.saveCustomFood(food)
        assertEquals(1, db.nutritionDao().getAllCustomFoods().size)
        assertEquals(1, repo.foodDatabase.value.count { it.id == food.id })
        repo.saveCustomFood(food)
        assertEquals(1, db.nutritionDao().getAllCustomFoods().size)
        assertEquals(1, repo.foodDatabase.value.count { it.id == food.id })
        val stored = db.nutritionDao().getAllCustomFoods().single()
        assertEquals(food.id, stored.id)
    }

    @Test fun `si Room falla el alimento no se publica y el error llega a quien llama`() = runBlocking {
        val repo = repository()
        // Sin la tabla, el upsert falla de verdad (cerrar la base en memoria la reabriría vacía).
        db.openHelper.writableDatabase.execSQL("DROP TABLE nutrition_custom_foods")
        val result = runCatching { repo.saveCustomFood(customFood("4006381333931")) }
        assertTrue(result.isFailure)
        assertTrue(repo.foodDatabase.value.isEmpty())
    }

    @Test fun `un alimento invalido ni se guarda ni se publica`() = runBlocking {
        val repo = repository()
        val bad = customFood(null).copy(calories = Double.NaN)
        assertTrue(runCatching { repo.saveCustomFood(bad) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repo.saveCustomFood(customFood(null).copy(id = "")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(db.nutritionDao().getAllCustomFoods().isEmpty())
        assertTrue(repo.foodDatabase.value.isEmpty())
    }

    @Test fun `editar cambia la fila y la publicada sin duplicar`() = runBlocking {
        val repo = repository()
        val food = customFood("4006381333931")
        repo.saveCustomFood(food)
        repo.saveCustomFood(food.copy(name = "Galletas renombradas"))
        assertEquals(1, db.nutritionDao().getAllCustomFoods().size)
        assertEquals(listOf("Galletas renombradas"), repo.foodDatabase.value.filter { it.id == food.id }.map { it.name })
        assertEquals("Galletas renombradas", found(repo.findFoodByBarcode("4006381333931")).food.name)
    }

    @Test fun `borrar quita el alimento de Room y de lo publicado y el codigo vuelve a ser NotFound`() = runBlocking {
        val repo = repository()
        val food = customFood("4006381333931")
        repo.saveCustomFood(food)
        repo.deleteCustomFood(food.id)
        assertTrue(db.nutritionDao().getAllCustomFoods().isEmpty())
        assertTrue(repo.foodDatabase.value.none { it.id == food.id })
        assertEquals(BarcodeLookup.NotFound, repo.findFoodByBarcode("4006381333931"))
    }

    @Test fun `borrar no toca un alimento que no es propio aunque comparta el id`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(global("4006381333931", "Del catálogo", foodId = "off_4006381333931")))
        val repo = repository()
        repo.deleteCustomFood("off_4006381333931")
        assertNotNull(db.nutritionDao().getGlobalFoodById("off_4006381333931"))
    }

    @Test fun `un alimento propio creado aparece en la busqueda y se encuentra al escanear su codigo`() = runBlocking {
        val repo = repository()
        assertEquals(BarcodeLookup.NotFound, repo.findFoodByBarcode("4006381333931"))
        val food = customFood("4006381333931", name = "Galletas Marilan de prueba")
        repo.saveCustomFood(food)
        val search = repo.searchFoodCandidates("galletas marilan", limit = 15, loggerFilter = true)
        assertTrue("la búsqueda debe traer el alimento creado: ${search.map { it.foodId }}", search.any { it.foodId == food.id })
        assertEquals(food.id, found(repo.findFoodByBarcode("4006381333931")).food.id)
        assertFalse(found(repo.findFoodByBarcode("4006381333931")).food.isAiInferred)
    }
}
