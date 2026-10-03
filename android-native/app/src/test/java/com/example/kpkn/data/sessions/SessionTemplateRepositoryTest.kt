package com.example.kpkn.data.sessions

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSessionTemplateOrNull
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.SessionTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contratos Room / lectura de vuelta del ciclo de vida de las plantillas de USUARIO.
 *
 * Cada prueba trabaja sobre su PROPIA base Room en memoria: se crea en [setUp], se
 * enlaza como `KpknDatabase.INSTANCE` (el único punto por el que
 * [SessionTemplateRepository] abre la base) y se cierra y desenlaza en [tearDown].
 *
 * Por qué no se usa la base de archivo `kpkn.db` de producción: con ella, a partir de la
 * segunda prueba de la clase `isReady` nunca llegaba a emitir (las pruebas vencían a los
 * 10 s) y la tarea de Gradle no terminaba. Causa probable (análisis estático en
 * `artifacts/consolidation-20261001/research/sessiontemplate-hang/informe.md`; no existe
 * un volcado de hilos del worker): el `KpknDatabase.INSTANCE` de la primera prueba seguía
 * enlazado después de que Robolectric reiniciara sus conexiones SQLite, y la
 * `KpknApplication` real (que instala `NutritionCrashHook`) convertía esa excepción de
 * fondo en el cierre del proceso. Una base en memoria por prueba no comparte nada entre
 * métodos, y `application = Application::class` evita instalar ese gancho; es el patrón de
 * `SessionEditorViewModelRulesTest` y de las demás pruebas Robolectric de repositorios.
 *
 * No se toca código de producción: solo se usan `KpknDatabase.createInMemory`,
 * `KpknDatabase.closeInstance` y `SessionTemplateRepository.resetForTests`, que ya
 * existían para esto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SessionTemplateRepositoryTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: KpknDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Que ningún repositorio ni base de otra prueba (de esta clase o de otra del
        // mismo proceso) llegue vivo a esta prueba.
        SessionTemplateRepository.resetForTests()
        KpknDatabase.closeInstance()
        database = KpknDatabase.createInMemory(context)
        // El repositorio abre la base con KpknDatabase.getInstance(...): se enlaza la base
        // en memoria ANTES de construirlo, o abriría el archivo kpkn.db real.
        setDatabaseSingletonForTest(database)
    }

    @After
    fun tearDown() {
        try {
            // Primero se cancela el repositorio (su scope usa la base) y después se
            // cierra la base; nunca al revés.
            SessionTemplateRepository.resetForTests()
            if (this::database.isInitialized) database.close()
        } finally {
            setDatabaseSingletonForTest(null)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun durableCrudPublishesOnlyAfterDaoAndRestoresArchivedRows(): Unit = runBlocking {
        val repository = readyRepository()
        // Id fijo: la base es nueva en cada prueba, no hace falta limpiar al terminar.
        val id = "plantilla-crud"
        val template = fixture(id, "Original")

        val firstSave = repository.saveUserTemplateNow(template)
        assertTrue(firstSave.exceptionOrNull()?.stackTraceToString().orEmpty(), firstSave.isSuccess)
        val persisted = database.sessionTemplateDao().getById(id)?.toSessionTemplateOrNull()
        assertNotNull("la fila debe existir antes de publicar el éxito", persisted)
        assertEquals("Original", persisted?.name)
        assertEquals("Original", repository.getByIdAfterReady(id)?.name)
        // allTemplates se recalcula de forma asíncrona: esperar a verla antes de archivar
        // hace que la espera posterior de «desaparece» sea una comprobación real.
        awaitAllTemplates(repository) { templates -> templates.any { it.id == id } }

        val latest = template.copy(name = "Latest", description = "edited")
        assertTrue(repository.saveUserTemplateNow(latest).isSuccess)
        assertEquals("Latest", repository.getByIdAfterReady(id)?.name)
        assertEquals(
            "Latest",
            database.sessionTemplateDao().getById(id)?.toSessionTemplateOrNull()?.name,
        )

        assertTrue(repository.archiveUserTemplateNow(id).isSuccess)
        assertTrue(repository.userTemplates.value.first { it.id == id }.isArchived)
        awaitAllTemplates(repository) { templates -> templates.none { it.id == id } }

        assertTrue(repository.restoreUserTemplateNow(id).isSuccess)
        awaitAllTemplates(repository) { templates -> templates.any { it.id == id } }

        assertTrue(repository.deleteUserTemplateNow(id).isSuccess)
        assertNull(repository.getByIdAfterReady(id))
        assertFalse(repository.userTemplates.value.any { it.id == id })
    }

    @Test
    fun invalidSystemWriteFailsWithoutFlowMutation(): Unit = runBlocking {
        val repository = readyRepository()
        val system = SESSION_TEMPLATES_SYSTEM.first()
        val before = repository.userTemplates.value
        val thrown = runCatching { repository.saveUserTemplateNow(system) }.exceptionOrNull()
        assertNotNull("un template SYSTEM no puede entrar al DAO USER", thrown)
        assertEquals(before, repository.userTemplates.value)
    }

    @Test
    fun generationCatalogIncludesOnlyOptInEligibleVisibleUsers(): Unit = runBlocking {
        val repository = readyRepository()
        val eligibleId = "generacion-elegible"
        val excludedId = "generacion-excluida"

        assertTrue(repository.saveUserTemplateNow(fixture(eligibleId, "Opt-in")).isSuccess)
        assertTrue(
            repository.saveUserTemplateNow(
                fixture(excludedId, "No opt-in").copy(autoGenerationEligible = false),
            ).isSuccess,
        )
        withTimeout(FLOW_TIMEOUT_MS) {
            repository.userTemplates.first { templates -> templates.any { it.id == eligibleId } }
        }
        withTimeout(FLOW_TIMEOUT_MS) {
            repository.generationTemplates.first { templates -> templates.any { it.id == eligibleId } }
        }
        assertTrue(repository.generationTemplates.value.any { it.id == eligibleId })
        assertFalse(repository.generationTemplates.value.any { it.id == excludedId })
        assertFalse(repository.generationTemplates.value.any { it.isArchived })
    }

    /** Repositorio nuevo sobre la base en memoria de esta prueba, ya hidratado desde Room. */
    private suspend fun readyRepository(): SessionTemplateRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SessionTemplateRepository.getInstance(context)
        withTimeout(FLOW_TIMEOUT_MS) { repository.isReady.first { it } }
        return repository
    }

    /** Espera (con tope) a que `allTemplates`, que se recalcula en IO, cumpla [predicate]. */
    private suspend fun awaitAllTemplates(
        repository: SessionTemplateRepository,
        predicate: (List<SessionTemplate>) -> Boolean,
    ) {
        withTimeout(FLOW_TIMEOUT_MS) { repository.allTemplates.first { predicate(it) } }
    }

    /**
     * `INSTANCE` es un campo estático privado del companion de [KpknDatabase] (no hay otro
     * punto de enlace). Mismo helper que `SessionEditorViewModelRulesTest`.
     */
    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.set(null, database)
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
        shortDescription = "Enfoque pecho",
        autoGenerationEligible = true,
    )

    private companion object {
        /** Tope generoso: la primera lectura del catálogo estático puede tardar en frío. */
        const val FLOW_TIMEOUT_MS = 30_000L
    }
}
