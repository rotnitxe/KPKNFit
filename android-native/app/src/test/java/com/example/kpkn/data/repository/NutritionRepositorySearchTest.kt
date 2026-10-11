package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.OffSearchFixture
import com.example.kpkn.domain.nutrition.TextKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-S2: the Search tab through the REAL repository: Room retrieval (phrase, alias and stem terms with LIKE), the
 * static catalog pool, duplicate collapsing, ranking and the filter before the limit. The in-memory database holds the
 * committed OFF fixture; the importer is a no-op so the 70 MB catalog is never read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionRepositorySearchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<NutritionRepository>()
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        ProgramRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() = runBlocking {
        val repositoryJob = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repositories.forEach { (repositoryJob.get(it) as Job).cancelAndJoin() }
        repositories.clear()
        db.close()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    private val noImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: com.example.kpkn.data.food.FoodImporter.ImportMetadata?,
            onMetaUpdated: (com.example.kpkn.data.food.FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    /** A repository over the in-memory database seeded with the OFF fixture, with the static catalog published. */
    private fun seededRepository(): NutritionRepository = runBlocking {
        db.nutritionDao().insertGlobalFoods(OffSearchFixture.entities())
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
            FoodCatalogImporter::class.java,
        )
        ctor.isAccessible = true
        val repo = ctor.newInstance(context, db, false, noImport).also { repositories += it }
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        repo
    }

    private fun ids(repo: NutritionRepository, query: String, limit: Int = 15, loggerFilter: Boolean = true) = runBlocking {
        withTimeout(TIMEOUT_MS) { repo.searchFoodCandidates(query, limit, loggerFilter) }.map { it.foodId }
    }

    @Test
    fun `huevos retrieves the OFF rows named huevo through the stem term and ranks the curated egg first`() {
        val repo = seededRepository()
        val found = ids(repo, "huevos")
        assertEquals("gen007", found.first())
        // "Huevo Extra Blanco" never contains the phrase "huevos": only the stem term "huevo" can bring it.
        assertTrue(found.any { it == "off_7804133000035" })
    }

    @Test
    fun `leche colun finds the Colun SKUs although no row holds the phrase`() {
        val repo = seededRepository()
        val results = runBlocking { repo.searchFoodCandidates("leche colun", 15, true) }
        assertTrue(results.size >= 3)
        assertTrue(results.take(3).all { it.brandMatched && TextKeys.normalize(it.food.brand.orEmpty()).contains("colun") })
    }

    @Test
    fun `leche puts the curated milk first, keeps dulce de leche out and shows curated rows before OFF`() {
        val repo = seededRepository()
        val found = ids(repo, "leche")
        assertEquals("gen016", found.first())
        assertTrue("OFF too early: $found", found.indexOfFirst { it.startsWith("off_") } >= 3)
        assertTrue("off_0721450761012" !in ids(repo, "leche", 50))
    }

    @Test
    fun `pan finds no substring decoys and every row passes the logger rules`() {
        val repo = seededRepository()
        val results = runBlocking { repo.searchFoodCandidates("pan", 15, true) }
        assertEquals("gen019", results.first().foodId)
        assertTrue(results.none { candidate -> listOf("empanad", "panch", "pancake", "biopan").any { it in TextKeys.normalize(candidate.food.name) } })
        assertTrue(results.all { NutrientBasis.isVerified(it.food) && FoodIdentity.matchesDeclaredIdentity("pan", it.food) })
    }

    @Test
    fun `identical OFF rows collapse into one result`() {
        val repo = seededRepository()
        assertEquals(1, ids(repo, "red bull").size)
    }

    @Test
    fun `searchFood is the same ranking without the candidates`() {
        val repo = seededRepository()
        val candidates = runBlocking { repo.searchFoodCandidates("yogurt") }.map { it.food.id }
        assertEquals(candidates, runBlocking { repo.searchFood("yogurt") }.map { it.id })
        assertEquals(setOf("gen017", "gen087"), candidates.take(2).toSet())
    }

    @Test
    fun `a blank query and a query of stop words return nothing`() {
        val repo = seededRepository()
        assertTrue(ids(repo, "   ").isEmpty())
        assertTrue(ids(repo, "de la").isEmpty())
        assertTrue(ids(repo, "xyzq").isEmpty())
    }

    @Test
    fun `the food a person picked for a query moves up for that query`() {
        val repo = seededRepository()
        val before = ids(repo, "leche", 50)
        val picked = "off_0400008174954" // Leche Natural, Lider: an OFF row the curated milks outrank
        assertTrue(before.indexOf(picked) > 3)
        val food = runBlocking { repo.getFoodById(picked) }!!
        repo.recordFoodSelection("leche", food)
        val after = runBlocking { repo.searchFoodCandidates("leche", 50, true) }
        assertTrue(after.indexOfFirst { it.foodId == picked } < before.indexOf(picked))
        assertTrue(after.first { it.foodId == picked }.learned)
    }

    @Test
    fun `a broken database never fails the search, the static catalog still answers`() {
        val repo = seededRepository()
        db.close()
        val found = ids(repo, "huevo")
        assertEquals("gen007", found.first())
        assertTrue(found.none { it.startsWith("off_") })
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
