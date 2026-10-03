package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U12 (C11): los tamaños de utensilios que el usuario guardó viven en SharedPreferences y en el motor de porciones
 * (estado global del proceso). El diálogo guarda lo que cambió con `saveUtensilOverride` y devuelve a la base lo que
 * no con `clearUtensilOverride`; el arranque los reaplica con `loadUtensilOverrides`.
 *
 * Mismo montaje que NutritionRepositoryStartupTest: repositorio real sobre una base en memoria, con un importador
 * del catálogo que no hace nada (aquí no se prueba el catálogo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionRepositoryUtensilTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var repository: NutritionRepository
    private lateinit var engineBefore: Map<String, Double>

    private val noImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setUp() {
        // El motor es global: se guarda lo que había y se parte de cero para no depender de otras pruebas.
        engineBefore = SubjectivePortionEngine.currentUtensilOverrides()
        SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
        prefs.edit().clear().commit()
        // Singletons que pueden venir de otras pruebas (mismo criterio que NutritionRepositoryStartupTest).
        ProgramRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        repository = NutritionRepository.initForTests(context, noImport)
    }

    @After
    fun tearDown() {
        NutritionRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        SubjectivePortionEngine.applyUtensilOverrides(engineBefore)
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("nutrition_utensils", Context.MODE_PRIVATE)

    private fun overrides(): Map<String, Double> = SubjectivePortionEngine.currentUtensilOverrides()

    private fun tazaDeAvenaGrams(): Double =
        SubjectivePortionEngine.resolve(expression = "una taza de avena")?.grams ?: Double.NaN

    @Test
    fun `a saved utensil volume reaches the portion engine and is persisted`() {
        val base = tazaDeAvenaGrams()

        repository.saveUtensilOverride("taza", 300.0)

        assertEquals(300.0, overrides()["taza"])
        assertEquals(300f, prefs.getFloat("ml_taza", -1f), 0f)
        assertEquals(base * 300.0 / 240.0, tazaDeAvenaGrams(), 0.01)
    }

    @Test
    fun `saved volumes come back after the engine loses its state`() {
        repository.saveUtensilOverride("taza", 300.0)
        repository.saveUtensilOverride("plato_hondo", 450.0)

        // Un proceso nuevo: el motor arranca vacío y lo guardado solo vive en las preferencias.
        SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
        assertTrue(overrides().isEmpty())
        repository.loadUtensilOverrides()

        assertEquals(mapOf("taza" to 300.0, "plato_hondo" to 450.0), overrides())
    }

    @Test
    fun `a restarted repository reapplies the saved volumes by itself`() {
        repository.saveUtensilOverride("taza", 300.0)
        SubjectivePortionEngine.applyUtensilOverrides(emptyMap())

        // initForTests cierra el repositorio anterior y vuelve a arrancar; retorna con la fase 1 del arranque hecha.
        NutritionRepository.initForTests(context, noImport)

        assertEquals(300.0, overrides()["taza"])
    }

    @Test
    fun `clearing a utensil removes it from the engine and from storage`() {
        val base = tazaDeAvenaGrams()
        repository.saveUtensilOverride("taza", 300.0)
        repository.saveUtensilOverride("vaso", 200.0)

        repository.clearUtensilOverride("taza")

        assertNull(overrides()["taza"])
        assertEquals(200.0, overrides()["vaso"])
        assertFalse(prefs.contains("ml_taza"))
        assertTrue(prefs.contains("ml_vaso"))
        // Sin override el motor vuelve al volumen base.
        assertEquals(base, tazaDeAvenaGrams(), 0.01)

        // Y no resucita en la próxima carga.
        SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
        repository.loadUtensilOverrides()
        assertEquals(mapOf("vaso" to 200.0), overrides())
    }

    @Test
    fun `clearing a utensil that was never saved changes nothing`() {
        repository.saveUtensilOverride("vaso", 200.0)

        repository.clearUtensilOverride("taza")

        assertEquals(mapOf("vaso" to 200.0), overrides())
        assertTrue(prefs.contains("ml_vaso"))
        assertFalse(prefs.contains("ml_taza"))
    }
}
