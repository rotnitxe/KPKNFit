package com.example.kpkn.data.sessions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSessionTemplateOrNull
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.SessionTemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Room/read-back contracts for USER template lifecycle. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SessionTemplateRepositoryTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @org.junit.Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        SessionTemplateRepository.resetForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun durableCrudPublishesOnlyAfterDaoAndRestoresArchivedRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SessionTemplateRepository.getInstance(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        val id = "test-template-${UUID.randomUUID()}"
        val template = fixture(id, "Original")
        try {
            val firstSave = repository.saveUserTemplateNow(template)
            assertTrue(firstSave.exceptionOrNull()?.stackTraceToString().orEmpty(), firstSave.isSuccess)
            val persisted = KpknDatabase.getInstance(context)
                .sessionTemplateDao()
                .getById(id)
                ?.toSessionTemplateOrNull()
            assertNotNull("la fila debe existir antes de publicar el éxito", persisted)
            assertEquals("Original", persisted?.name)
            assertEquals("Original", repository.getByIdAfterReady(id)?.name)

            val latest = template.copy(name = "Latest", description = "edited")
            assertTrue(repository.saveUserTemplateNow(latest).isSuccess)
            assertEquals("Latest", repository.getByIdAfterReady(id)?.name)
            assertEquals(
                "Latest",
                KpknDatabase.getInstance(context).sessionTemplateDao().getById(id)
                    ?.toSessionTemplateOrNull()?.name,
            )

            assertTrue(repository.archiveUserTemplateNow(id).isSuccess)
            assertTrue(repository.userTemplates.value.first { it.id == id }.isArchived)
            withTimeout(5_000) { repository.allTemplates.first { templates -> templates.none { it.id == id } } }

            assertTrue(repository.restoreUserTemplateNow(id).isSuccess)
            withTimeout(5_000) { repository.allTemplates.first { templates -> templates.any { it.id == id } } }

            assertTrue(repository.deleteUserTemplateNow(id).isSuccess)
            assertNull(repository.getByIdAfterReady(id))
            assertFalse(repository.userTemplates.value.any { it.id == id })
        } finally {
            // Keep the shared Robolectric database clean even on assertion failure.
            repository.deleteUserTemplateNow(id)
        }
    }

    @Test
    fun invalidSystemWriteFailsWithoutFlowMutation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SessionTemplateRepository.getInstance(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        val system = SESSION_TEMPLATES_SYSTEM.first()
        val before = repository.userTemplates.value
        val thrown = runCatching { repository.saveUserTemplateNow(system) }.exceptionOrNull()
        assertNotNull("un template SYSTEM no puede entrar al DAO USER", thrown)
        assertEquals(before, repository.userTemplates.value)
    }

    @Test
    fun generationCatalogIncludesOnlyOptInEligibleVisibleUsers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SessionTemplateRepository.getInstance(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        val eligibleId = "generation-eligible-${UUID.randomUUID()}"
        val excludedId = "generation-excluded-${UUID.randomUUID()}"
        try {
            assertTrue(repository.saveUserTemplateNow(fixture(eligibleId, "Opt-in")).isSuccess)
            assertTrue(repository.saveUserTemplateNow(fixture(excludedId, "No opt-in").copy(autoGenerationEligible = false)).isSuccess)
            withTimeout(10_000) {
                repository.generationTemplates.first { templates -> templates.any { it.id == eligibleId } }
            }
            assertTrue(repository.generationTemplates.value.any { it.id == eligibleId })
            assertFalse(repository.generationTemplates.value.any { it.id == excludedId })
            assertFalse(repository.generationTemplates.value.any { it.isArchived })
        } finally {
            repository.deleteUserTemplateNow(eligibleId)
            repository.deleteUserTemplateNow(excludedId)
        }
    }

    private fun fixture(id: String, name: String): SessionTemplate = SessionTemplate(
        id = id,
        sourceType = SessionTemplateSourceType.USER,
        name = name,
        description = "fixture",
        session = Session(
            id = "session-$id",
            name = name,
            exercises = listOf(
                Exercise(
                    id = "exercise-$id",
                    name = "Press banca con barra",
                    exerciseDbId = "bench_press__barbell",
                    sets = listOf(
                        ExerciseSet(
                            id = "set-$id",
                            targetReps = 8,
                            targetRPE = 7.0,
                        ),
                    ),
                ),
            ),
        ),
        publicationStatus = SessionTemplatePublicationStatus.KPKN_NATIVE,
        splitIds = listOf("custom"),
        splitDayLabels = listOf("Pecho"),
        autoGenerationEligible = true,
    )
}
