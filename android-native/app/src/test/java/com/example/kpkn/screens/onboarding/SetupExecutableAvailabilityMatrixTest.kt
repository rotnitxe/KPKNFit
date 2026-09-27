package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.programs.AdaptationPolicy
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.templates.SessionTemplateEngine
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.effectiveEquipment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * T-019 — MATRIZ DE FALSACIÓN «ANTES DE SPLIT» sobre el [SetupWizardViewModel] REAL.
 *
 * Qué demuestra y qué NO demuestra:
 * - Evalúa 24 perfiles VÁLIDOS y explícitos (A12, B6, C2, D2, E1, F1) de principio a fin contra
 *   los motores de producción ([com.example.kpkn.domain.onboarding.SetupTrainingPlanner],
 *   `SimpleCyclePersonalizer` a través de `materializeProgram` del VM, y las recetas
 *   PROTOCOL/TEMPLATE).
 * - `materializeOverride` queda **NULL**: en esta clase no hay generador, candidato ni predicado
 *   duplicado. La lista de candidatos la publica el VM y el preview lo materializa el motor real.
 * - NO es una prueba de cobertura universal: el espacio categórico son 2^11 = 2048 subconjuntos y
 *   hay más variables además de estos 24 (objetivo, foco, split, marcas, prioridades). Pasar aquí
 *   no prueba la garantía P-006; fallar aquí la refuta en estos puntos concretos.
 * - La matriz es «antes de SPLIT» (`selectedSplitId == null` en las 24 filas). La cobertura
 *   posterior a la elección de split es PENDING y no se afirma aquí.
 *
 * ORÁCULO por fila (idéntico para las 24, sin atajos por tipo de material):
 *  1. El borrador REAL conserva todas las entradas pedidas —no se cambia ninguna respuesta para
 *     conseguir un resultado— y el cálculo de candidatos ha TERMINADO.
 *  2. `availablePlanCandidates` y `planCandidates` no vacías. Una lista vacía es FALLO DE
 *     COBERTURA aunque publique un motivo honesto: el motivo es diagnóstico, nunca un PASS.
 *  3. Se elige un testigo (preferentemente NATIVO) con `vm.selectPlan(...)` REAL y se espera a su
 *     `programPreview`. Si ningún candidato materializa un programa ejecutable → FALLO.
 *  4. Sobre el programa devuelto se comprueba el contrato canónico de producción:
 *     `ProgramExecutionContract`, contenido ejecutable por sesión
 *     (`SessionTemplateEngine.sessionHasCompleteExecutableContent`), frecuencia/calendario,
 *     ids de configuración canónicos que resuelven en el catálogo aprobado de MAIN y NO fuga de
 *     material frente al `TrainingOptions.effectiveEquipment` declarado.
 *  5. Tiempo: la fuente NATIVE usa `Session.targetDurationMinutes` (fórmula de slots); una receta
 *     fija usa SU propio `state.fixedSessionEstimateMinutes`. Son dos estimadores distintos y cada
 *     aserción se hace sobre su propia fuente: aquí no se finge que coincidan.
 *  6. T-027 — sólo cuando `row.goal == MIXED`: el cardio tiene que ser CONTENIDO ESTRUCTURADO
 *     emitido por el motor —exactamente un bloque de cardio por día de entrenamiento, con el tipo
 *     y los minutos REALES declarados en el borrador (CAMINAR), sin cardio suelto, sin cardio
 *     duplicado y sin configuración de máquina inventada para andar—, junto a fuerza real. Se
 *     leen las APIs canónicas del modelo de producción, no el nombre del programa ni una anotación.
 *     Las filas NO MIXED conservan su oráculo intacto.
 *
 * Las notas no fatales del motor (`previewReport.limitations`) NO son un fallo.
 *
 * Arnés: Robolectric SDK 34 + `StandardTestDispatcher` + `ViewModelStore`, Room **en memoria** por
 * fila, `Settings` fijo y el mismo ciclo de vida global de repos que usa el resto del sandbox
 * Robolectric. Cada fila tiene VM, borrador y BD propios y los cierra al terminar: no hay fuga
 * entre filas. El catálogo es el asset de producción y su identidad de bytes se comprueba en
 * [T019_00_asset_identidad_y_oraculo_de_configuraciones].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupExecutableAvailabilityMatrixTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    /** Etiqueta del caso en curso; la fija el propio test para la traza de evidencia. */
    @Volatile
    private var currentCaseLabel: String = "<sin-nombre>"

    // ─── Arnés de ciclo de vida (T-019-r1) ──────────────────────────────────
    //
    // `ProgramRepository.closeInstance()` y `NutritionRepository.closeInstance()` hacen
    // `runBlocking { repositoryJob.cancelAndJoin() }` (ProgramRepository.kt:1807 y
    // NutritionRepository.kt:1299) sobre RobolectricMain. Con
    // `Dispatchers.setMain(StandardTestDispatcher())` ese hilo es a la vez el que debe drenar la
    // cola del scheduler, así que la llamada se autoestacionaba: el volcado de hilos de T-019 la
    // encontró PARKED en `runBlocking` (`SDK 34 Main Thread`, TIMED_WAITING) sin worker de app
    // calculando.
    //
    // Arreglo EXCLUSIVO de arnés, sin tocar producción: los dos `closeInstance()` REALES se ejecutan
    // en UN hilo auxiliar propio mientras RobolectricMain sigue drenando SU scheduler. No se mueve
    // ningún callback de RobolectricMain al hilo auxiliar, no se cambia el dispatcher global a
    // Unconfined y no se sustituye ni se reimplementa el cierre de producción.
    private fun closeRealRepositoriesBounded(where: String) {
        val contamination = cleanupContamination.get()
        if (contamination != null) {
            // FAIL-FAST (nunca skip ni PASS): un cierre anterior ya falló o se quedó vivo, así que
            // cualquier método posterior operaría sobre un estado ya contaminado.
            throw AssertionError(
                "HARNESS_BLOCKER: el cierre de repos ya falló o quedó vivo en $where; no se intenta " +
                    "otro cierre concurrente sobre el mismo estado. Detalle previo=$contamination",
            )
        }
        val failure = AtomicReference<Throwable?>(null)
        val finished = CountDownLatch(1)
        val closer = Thread(
            {
                try {
                    ProgramRepository.closeInstance()
                    NutritionRepository.closeInstance()
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    finished.countDown()
                }
            },
            "t019-repo-close-$where",
        )
        closer.isDaemon = true
        val startedAt = System.nanoTime()
        trace("LIFECYCLE close-start where=$where")
        closer.start()
        val deadlineNanos = startedAt + CLOSE_BUDGET_NANOS
        var completed = false
        while (System.nanoTime() < deadlineNanos) {
            // FINITO: `runCurrent()` sólo ejecuta tareas de la cola en el instante virtual actual.
            // Nunca se pone un `advanceUntilIdle()` sin cota detrás de un reloj nominal.
            dispatcher.scheduler.runCurrent()
            if (finished.await(2, TimeUnit.MILLISECONDS)) {
                completed = true
                break
            }
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
        if (!completed) {
            // El auxiliar sigue vivo: no se cierra su base de datos por debajo y no se lanza un
            // segundo cerrador concurrente. Se declara el bloqueo y se marca la contaminación.
            val detail = "el cierre real de repos no terminó en $CLOSE_BUDGET_MILLIS ms (hilo auxiliar " +
                "t019-repo-close-$where sigue vivo; NO se cierra su BD ni se lanza otro cerrador)"
            cleanupContamination.compareAndSet(null, detail)
            trace("LIFECYCLE close-timeout where=$where elapsedMs=$elapsedMillis :: $detail")
            throw AssertionError("HARNESS_BLOCKER: $detail")
        }
        val captured = failure.get()
        if (captured != null) {
            // El throwable capturado se RELANZA en el hilo del test. Nunca se descarta.
            val detail = "el cierre real de repos lanzó ${captured::class.java.name}: ${captured.message}"
            cleanupContamination.compareAndSet(null, detail)
            trace("LIFECYCLE close-throw where=$where elapsedMs=$elapsedMillis :: $detail")
            throw captured
        }
        trace("LIFECYCLE close-end where=$where elapsedMs=$elapsedMillis status=ok")
    }

    /**
     * RESULTADO DEL CIERRE DE UNA FILA. Devuelve completitud/fallo en vez de lanzar: la decisión de
     * marcar `HARNESS_BLOCKER` es de quien llama, para que un diagnóstico que pida un plazo corto a
     * propósito no envenene el resto de la clase.
     */
    private class RowCloseOutcome(
        val completed: Boolean,
        val elapsedMillis: Long,
        val detail: String,
        val clearFailure: Throwable?,
    )

    /**
     * Cierre de los recursos de UNA fila. Es el MISMO helper para la matriz y para la regresión
     * `T019_01_*`; sólo cambia el plazo, que es un parámetro privado.
     *
     * Secuencia: cancelación REAL con `store.clear()` (nunca un `owner.cancel()` sustituto) →
     * espera de la finalización real del padre de la fila → y **sólo entonces** `db.close()`.
     *
     * PREDICADO (GREEN): la finalización real del padre de la fila, `owner.isCompleted`. El centinela
     * FIFO encolado en el scheduler de test que usaba v4 queda **rechazado**: la persistencia real
     * corre en `Dispatchers.IO` (`SetupDraftRepository.save` =
     * `withContext(Dispatchers.IO) { db.withTransaction { … } }`), así que el centinela no puede
     * observar ni unirse a ella, y `withContext` no interrumpe un bloque ya en ejecución. La
     * regresión `T019_01_*` demuestra esa diferencia con un guardado real aparcado.     */
    private fun TestScope.closeRowResources(
        owner: Job,
        store: ViewModelStore,
        db: KpknDatabase,
        label: String,
        budgetNanos: Long,
    ): RowCloseOutcome {
        val startedAt = System.nanoTime()
        var clearFailure: Throwable? = null
        try {
            store.clear()
        } catch (error: Throwable) {
            clearFailure = error
        }
        val deadlineNanos = startedAt + budgetNanos
        var settled = false
        while (System.nanoTime() < deadlineNanos) {
            // FINITO y explícito: se bompea el scheduler de test (las continuaciones post-IO
            // regresan por `Dispatchers.Main.immediate`, inyectado como `StandardTestDispatcher`) y
            // se espera la finalización REAL del padre de la fila, que cubre a TODOS sus hijos:
            // escritores de BD, `previewJob` y `candidateJob`. Un centinela FIFO encolado en el
            // scheduler de test NO podría observar ni unirse a un `withContext(Dispatchers.IO)`
            // ya en ejecución, y `withContext` no interrumpe un bloque en curso: por eso el
            // predicado es `owner.isCompleted` y no el orden de la cola del test.
            dispatcher.scheduler.runCurrent()
            if (owner.isCompleted) {
                settled = true
                break
            }
            Thread.sleep(2L)
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
        if (clearFailure != null) {
            return RowCloseOutcome(
                completed = false,
                elapsedMillis = elapsedMillis,
                detail = "store.clear() falló: ${clearFailure!!::class.java.name}: ${clearFailure!!.message}",
                clearFailure = clearFailure,
            )
        }
        if (!settled) {
            return RowCloseOutcome(
                completed = false,
                elapsedMillis = elapsedMillis,
                detail = "la fila $label no alcanzó la finalización exigida en ${budgetNanos / 1_000_000L} ms; " +
                    "su BD NO se cierra para no cerrarla con trabajo real en vuelo",
                clearFailure = null,
            )
        }
        return try {
            db.close()
            RowCloseOutcome(
                completed = true,
                elapsedMillis = elapsedMillis,
                detail = "fila $label cerrada tras completar el padre de la fila",
                clearFailure = null,
            )
        } catch (error: Throwable) {
            RowCloseOutcome(
                completed = false,
                elapsedMillis = elapsedMillis,
                detail = "db.close() de la fila $label falló: ${error::class.java.name}: ${error.message}",
                clearFailure = null,
            )
        }
    }

    // ─── Evidencia que sobrevive a un fallo de aserción o de teardown ─────────

    /**
     * Traza propia y compacta en `build/reports/T019/`, con nombre único por JVM para no tocar
     * ninguna salida ajena. Se vacía en cada marcador, así que un fallo de cleanup deja en disco el
     * estado real de la fila. Sin datos de usuario, sin volcados de estado de la app.
     */
    private fun trace(line: String) {
        val writer = traceWriter ?: return
        synchronized(traceLock) {
            writer.println(line)
            writer.flush()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        openTrace()
        trace("LIFECYCLE before-enter test=${currentTestName()}")
        // Shadow SQLite de Robolectric se recrea por método: los singletons de conexión se
        // limpian AQUÍ para que cada método use punteros de SU propia base de sandbox.
        resetAugeRepositorySingleton()
        closeRealRepositoriesBounded("before")
        KpknDatabase.closeInstance()
        ProgramRepository.init(app)
        NutritionRepository.init(app)
        // Catálogo REAL de producción calentado FUERA del presupuesto de runTest.
        val warm = runCatching { runBlocking { CatalogV2ProcessCache.getOrLoad(app) } }
        assertTrue(
            "HARNESS_BLOCKER: el catálogo real $ASSET_NAME no carga en Robolectric: " +
                warm.exceptionOrNull()?.message,
            warm.isSuccess,
        )
        trace("LIFECYCLE before-ok test=${currentTestName()}")
    }

    @After
    fun tearDown() {
        // Orden real de producción, con el cierre de repos en el hilo auxiliar acotado:
        // repos -> Auge -> base de datos -> resetMain. `resetMain` va al final porque el hilo
        // principal es el scheduler de pruebas y no puede quedar trabajo pendiente sin drenar.
        closeRealRepositoriesBounded("after")
        resetAugeRepositorySingleton()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        trace("LIFECYCLE after-ok test=${currentTestName()}")
    }

    /**
     * Etiqueta del caso en curso para la traza. La fija el propio test (identidad o grupo) con su
     * nombre real, sin reglas de JUnit adicionales ni datos de usuario.
     */
    private fun currentTestName(): String = currentCaseLabel

    /**
     * Abre la traza propia de esta JVM en `build/reports/T019/` con nombre único por PID, para no
     * tocar ninguna salida ajena ni borrar ficheros de otro. Si el destino no es escribible es un
     * problema de ARNÉS explícito: perder la evidencia en silencio sería peor que un rojo honesto.
     */
    private fun openTrace() {
        if (traceWriter != null) return
        synchronized(traceLock) {
            if (traceWriter != null) return
            val directory = File("build", "reports").resolve("T019")
            val file = File(directory, "trace-T019-${ProcessHandle.current().pid()}.txt")
            try {
                directory.mkdirs()
                traceFile = file
                traceWriter = PrintWriter(file)
            } catch (error: Throwable) {
                throw AssertionError(
                    "HARNESS_BLOCKER: no se pudo abrir la traza de evidencia propia en " +
                        "${file.absolutePath}: ${error::class.java.name}: ${error.message}",
                    error,
                )
            }
        }
    }


    /**
     * `AugeRepository` no expone reset y su `dao` apunta a la BD de fichero que Robolectric
     * recrea entre métodos. Acceso por reflejo SOLO desde esta clase y con fallo explícito si la
     * forma del producto cambia: ningún `runCatching` que lo oculte.
     */
    private fun resetAugeRepositorySingleton() {
        val field = try {
            AugeRepository::class.java.getDeclaredField("INSTANCE")
        } catch (error: NoSuchFieldException) {
            throw AssertionError(
                "AugeRepository.INSTANCE ya no es un campo privado estático: revisa este helper de test",
                error,
            )
        }
        field.isAccessible = true
        field.set(null, null)
        check(field.get(null) == null) {
            "No se pudo limpiar AugeRepository.INSTANCE: su dao apuntaría a la conexión de otro test"
        }
    }

    // ─── 0. Identidad del asset: BLOQUEANTE DE ARNÉS, no fallo de producto ────

    /**
     * El motor del ViewModel lee `app.assets.open("exercise_catalog_v2.json")`. Para que las 24
     * filas sean evidencia de PRODUCCIÓN, ese asset tiene que ser el fichero real de
     * `src/main/assets`: se resuelve subiendo por los ancestros del directorio de trabajo del test
     * —nada de rutas de disco fijas— y se comparan bytes y SHA-256. Si la identidad falla, el
     * resto de la matriz es HARNESS_BLOCKED y no evidencia de producto.
     *
     * La decodificación usa el cargador de producción existente ([ExerciseCatalogV2Loader]) y da
     * el oráculo de configuración del resto de la clase. NO se usa
     * `CatalogCompositionTestSupport` (su rama de classloader puede sombrear el asset) ni se
     * llama a su `install()` (mutaría metadatos globales y haría el orden dependiente).
     */
    @Test
    fun T019_00_asset_identidad_y_oraculo_de_configuraciones() {
        currentCaseLabel = "T019_00_asset_identidad"
        openTrace()
        val startedAt = System.nanoTime()
        trace("CASE_BEGIN case=$currentCaseLabel")
        val fromAssets = app.assets.open(ASSET_NAME).use { it.readBytes() }
        val file = resolveMainAssetFile()
        assertTrue("HARNESS_BLOCKER: el asset resuelto no es un fichero: $file", file.isFile && file.length() > 0)
        val canonical = file.canonicalPath
        assertTrue(
            "HARNESS_BLOCKER: el asset resuelto no vive en src/main/assets (¿sombra de classpath?): $canonical",
            canonical.contains(File.separator + "src" + File.separator + "main" + File.separator + "assets" + File.separator),
        )
        val fromDisk = file.readBytes()
        assertEquals(
            "HARNESS_BLOCKER: el asset que lee el ViewModel y el de src/main/assets difieren " +
                "(assets=${fromAssets.size}B sha=${sha256(fromAssets)} / disco=${fromDisk.size}B sha=${sha256(fromDisk)})",
            sha256(fromDisk),
            sha256(fromAssets),
        )

        val catalog = approvedCatalog
        assertTrue("HARNESS_BLOCKER: el catálogo decodificado está vacío", catalog.families.isNotEmpty())
        assertTrue(
            "HARNESS_BLOCKER: el oráculo de configuración aprobado está vacío",
            approvedNames.isNotEmpty() && approvedEquipmentByConfiguration.isNotEmpty(),
        )
        println(
            "[T-019] asset OK: $canonical sha256=${sha256(fromDisk)} bytes=${fromDisk.size} " +
                "familias=${catalog.families.size} configuraciones=${approvedNames.size}",
        )
        trace(
            "CASE_END case=$currentCaseLabel outcome=PASS elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000L} " +
                "asset=$canonical sha256=${sha256(fromDisk)} bytes=${fromDisk.size}",
        )
    }

    /**
     * T-019 v5 — REGRESIÓN DETERMINISTA del cierre de fila (comprobación negativa de T-023 §E-6).
     *
     * reproduce la condición límite «una escritura REAL de la fila sigue en vuelo en un hilo IO
     * cuando la fila termina»: se aparca un `save` real (puerta sólo-test) **dentro** del delegado
     * real de [SetupDraftRepository], se fuerza un fallo de aserción de fila intencionado y se
     * invoca el MISMO helper de cierre que usa [evaluateRow] con un plazo corto inyectado.
     *
     * Lo que se verifica (todo con APIs públicas reales: `RoomDatabase.isOpen()`, `Job.isCompleted`):
     * la limpieza informa **incompleta**, la aserción original sigue registrada, el padre de la fila
     * **no** ha terminado, la BD **sigue abierta** y el guardado real **no** ha completado. Tras
     * liberar la puerta se espera el guardado delegado real y la finalización del padre, y sólo
     * entonces se cierra la BD.
     *
     * Nada de esto reproduce corrupción de base de datos: reproduce la **insensibilidad** del
     * predicado de limpieza. La puerta es una condición de estrés deliberadamente resistente a la
     * cancelación, no una afirmación sobre el comportamiento de producción.
     */
    @Test
    fun T019_01_row_cleanup_waits_for_the_real_row_owner() = runTest(timeout = 2.minutes) {
        currentCaseLabel = "T019_01_row_cleanup"
        openTrace()
        trace("CASE_BEGIN case=$currentCaseLabel")
        val db = KpknDatabase.createInMemory(app)
        val gate = RowSaveGate { line -> trace(line) }
        val store = ViewModelStore()
        val handle = SavedStateHandle()
        // Mismo cableado real que `evaluateRow`, con `materializeOverride` ausente (null).
        val vm = SetupWizardViewModel(
            app,
            handle,
            RoomWizardPersistence(db, gate),
            FixedSettingsEnvironment(Settings()),
            RoomWizardCommits(db),
        )
        store.put("t019-01", vm)
        val owner: Job = vm.viewModelScope.coroutineContext[Job]
            ?: error("T019_01: viewModelScope no expone Job")
        trace("LIFECYCLE row-owner-captured row=T019_01")
        val problems = mutableListOf<String>()
        var close: RowCloseOutcome? = null
        try {
            try {
                vm.initialize(SetupWizardMode.FULL, draftId = "t019-01")
                if (!awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading }) {
                    throw AssertionError("HARNESS: el wizard no salió de isLoading (${stateDump(vm.state.value)})")
                }
                // La puerta se arma DESPUÉS de la inicialización y sólo afecta a la siguiente
                // mutación pública explícita.
                gate.arm()
                vm.updateStep(SetupStepId.NAME) { it.copy(name = "T019-01") }
                assertTrue(
                    "HARNESS: el guardado real no llegó a la puerta sólo-test (writeEntered=false)",
                    awaitGateEntered(gate, GATE_ENTER_BUDGET_MILLIS),
                )
                // Fallo de aserción de fila INTENCIONADO, con la escritura real todavía en vuelo.
                throw AssertionError("INTENTIONAL_ROW_FAILURE: fallo de fila deliberado con el guardado real en vuelo")
            } catch (error: Throwable) {
                // La aserción original se REGISTRA, nunca se descarta.
                problems += (error.message ?: error.toString()).replace('\n', ' ')
                trace("REGRESSION intentional-row-failure recorded=${problems.first()}")
            } finally {
                // El MISMO helper de `evaluateRow`, con plazo corto inyectado. Pide explícitamente
                // un tiempo insuficiente: eso NO envenena la clase, porque el helper devuelve
                // completitud/fallo y quien decide el HARNESS_BLOCKER es `evaluateRow`.
                close = closeRowResources(owner, store, db, "T019_01", REGRESSION_SHORT_BUDGET_NANOS)
                trace(
                    "REGRESSION close outcome completed=${close?.completed} " +
                        "elapsedMs=${close?.elapsedMillis} detail=${close?.detail}",
                )
            }

            // ── Aserciones que DISTINGUEN el predicado viejo del nuevo ──────────────
            assertTrue(
                "RED-ESPERADO: la limpieza debe informar INCOMPLETA mientras el guardado real sigue " +
                    "aparcado; el predicado se vio como completado (detalle=${close?.detail})",
                close != null && !close.completed,
            )
            assertTrue(
                "la aserción original de la fila debe seguir registrada (problems=$problems)",
                problems.any { it.contains("INTENTIONAL_ROW_FAILURE") },
            )
            assertFalse(
                "el padre real de la fila no puede estar completado con un guardado en vuelo",
                owner.isCompleted,
            )
            assertTrue(
                "la BD de la fila debe SEGUIR ABIERTA mientras el guardado real está en vuelo",
                db.isOpen,
            )
            assertFalse(
                "el guardado real no puede haber completado antes de liberar la puerta",
                gate.delegateCompleted,
            )
        } finally {
            // Liberación GARANTIZADA: aunque las aserciones anteriores fallen (RED), la puerta se
            // libera y el trabajo real se drena con techo finito. Nunca se espera sin cota.
            gate.releaseGate()
            var drained = false
            if (db.isOpen) {
                drained = awaitOwnerAfterRelease(owner, gate, GATE_RELEASE_BUDGET_MILLIS)
                db.close()
            } else {
                // RED: el helper deficiente ya cerró la BD. Eso ES el defecto demostrado; la puerta
                // se libera igualmente para no dejar trabajo real en vuelo.
                drained = awaitOwnerAfterRelease(owner, gate, GATE_RELEASE_BUDGET_MILLIS)
            }
            trace(
                "REGRESSION after-release drained=$drained delegateCompleted=${gate.delegateCompleted} " +
                    "ownerCompleted=${owner.isCompleted} holdTimedOut=${gate.holdTimedOut} dbOpen=${db.isOpen}",
            )
        }
        // ── Verificación posterior a la liberación (sólo se alcanza si todo lo anterior pasó) ──
        assertFalse(
            "la puerta no debe agotar su propio techo: la liberó el test (holdTimedOut=${gate.holdTimedOut})",
            gate.holdTimedOut,
        )
        assertTrue("tras liberar, el delegado de guardado real debe completar", gate.delegateCompleted)
        assertTrue("tras liberar, el padre real de la fila debe completar", owner.isCompleted)
        assertFalse("tras completar, la BD de la fila debe estar cerrada", db.isOpen)
        trace("CASE_END case=$currentCaseLabel outcome=PASS")
    }

    /**
     * Doble de E/S **sólo-test**: su `close()` lanza `IOException`. No sustituye ningún
     * comportamiento de producto; sólo hace que el `PrintWriter` real registre su indicador de error
     * agregado, que es exactamente el fallo que `closeOwnedTraceWriter` debe hacer visible.
     */
    private class FailingOnCloseWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close(): Unit = throw IOException("T019_02: fallo de E/S inyectado en close()")
    }

    /**
     * T-019 v7 — el estado de error REAL del escritor debe hacer fallar el cierre de la clase.
     *
     * `java.io.PrintWriter.close()` nunca lanza: absorbe la `IOException` y la registra en un
     * indicador agregado. Por eso un cierre fallido se leía como cierre correcto. Esta regresión
     * invoca el **mismo** método real de cierre (`closeOwnedTraceWriter`, el de `@AfterClass`) con un
     * `PrintWriter` que envuelve un `Writer` cuyo `close()` falla, y exige un fallo explícito en vez
     * de una confirmación de éxito.
     *
     * El escritor y el fichero de traza REALES se guardan y se restauran en un `finally` garantizado,
     * de modo que el flujo de evidencia del propio test y el `@AfterClass` posterior siguen siendo
     * válidos. No se muta `cleanupContamination`: se exige sana como precondición, de modo que el
     * atajo POISONED no se puede usar para saltarse la comprobación.
     *
     * Si se eliminara la comprobación `checkError()`, el método devolvería con normalidad e imprimiría
     * su confirmación de éxito, y esta regresión fallaría: la distinción es por construcción.
     */
    @Test
    fun T019_02_trace_closure_fails_on_real_writer_error() {
        currentCaseLabel = "T019_02_trace_error"
        openTrace()
        trace("CASE_BEGIN case=$currentCaseLabel")
        val originalWriter = traceWriter
        val originalFile = traceFile
        try {
            assertNull(
                "precondición: con `cleanupContamination` marcada se tomaría la rama POISONED y no se " +
                    "ejercitaría el cierre sano que se quiere comprobar; no se muta para saltársela",
                cleanupContamination.get(),
            )
            // Hecho de API que hace necesaria la comprobación: `PrintWriter` ABSORBE la `IOException`
            // de `close()` y sólo la deja en su indicador interno.
            val swallowProbe = PrintWriter(FailingOnCloseWriter())
            swallowProbe.close()
            assertTrue(
                "PrintWriter debe haber registrado el fallo de E/S en su indicador (checkError=true)",
                swallowProbe.checkError(),
            )

            // Se sustituye SÓLO el escritor, y se invoca el método real de cierre de la clase.
            traceWriter = PrintWriter(FailingOnCloseWriter())
            traceFile = null
            var thrown: Throwable? = null
            try {
                closeOwnedTraceWriter()
            } catch (error: Throwable) {
                thrown = error
            }
            assertNotNull(
                "el cierre real de la traza debe FALLAR explícitamente cuando el escritor reporta " +
                    "error, en vez de declararse cerrado correctamente",
                thrown,
            )
            assertTrue(
                "el fallo debe ser un error de test explícito (AssertionError), no una excepción de " +
                    "E/S cruda ni un retorno silencioso: ${thrown?.let { it::class.java.name }}",
                thrown is AssertionError,
            )
            assertNotNull(
                "el mensaje debe identificar el fallo de escritura/flush/close de la traza: ${thrown?.message}",
                thrown?.message,
            )
        } finally {
            // Restauración GARANTIZADA del flujo de evidencia real, pase lo que pase antes.
            traceWriter = originalWriter
            traceFile = originalFile
            trace("CASE_END case=T019_02_trace_error (flujo de traza real restaurado)")
        }
    }

    // ─── 1. Matriz BEFORE-SPLIT de 24 perfiles VÁLIDOS ──────────────────────────

    /** A12: MÚSCULO, NEW→BEGINNER, material declarado explícitamente VACÍO (solo peso corporal). */
    @Test
    fun T019_A_musculo_new_peso_corporal_12_filas() = runGroup("A", rowsA(), 25.minutes)

    /** B6: MIXED, INTERMEDIATE, 11 categorías, cardio WALK 10 min, días dentro y fuera de 2..4. */
    @Test
    fun T019_B_mixto_intermediate_todo_el_material_6_filas() = runGroup("B", rowsB(), 20.minutes)

    /** C2: MÚSCULO, INTERMEDIATE, 5 días / 60 min, barra sola y barra + apoyo estable. */
    @Test
    fun T019_C_musculo_intermediate_barra_sin_y_con_apoyo_2_filas() = runGroup("C", rowsC(), 15.minutes)

    /** D2: MÚSCULO, 11 categorías, 3 días / 60 min, NEW e INTERMEDIATE. */
    @Test
    fun T019_D_musculo_todo_el_material_3_dias_2_filas() = runGroup("D", rowsD(), 15.minutes)

    /** E1: control positivo del arnés. Si esta fila no alcanza candidato+preview, el bloqueo es del arnés. */
    @Test
    fun T019_E_control_positivo_musculo_intermediate_todo_el_material_3_dias_100min() =
        runGroup("E", rowsE(), 15.minutes)

    /** F1: MÚSCULO, NEW, 11 categorías, 3 días / 20 min (presupuesto mínimo admitido). */
    @Test
    fun T019_F_musculo_new_todo_el_material_3_dias_20min() = runGroup("F", rowsF(), 15.minutes)

    /**
     * T-027 — REGRESIÓN REAL DEL ViewModel para las frecuencias **dentro** de 2..4 del objetivo
     * MIXED (2, 3 y 4 días / 60 min, INTERMEDIATE, las 11 categorías, cardio CAMINAR 10 min).
     *
     * Son TRES filas ADITIVAS: las 24 originales (A12, B6, C2, D2, E1, F1) se conservan tal cual
     * y este método NO las sustituye. Se reusan `runGroup`/`evaluateRow` y TODO el oráculo de
     * `assertProgramContract`, incluida la sección MIXED que exige cardio estructurado real. Su
     * propósito es que la ampliación de la familia nativa a 1..6 no rompa lo que ya funcionaba, y
     * que las frecuencias 2, 3 y 4 con cardio se comprueban por el mismo camino real que 1, 5 y 6.
     */
    @Test
    fun T027_M_mixto_intermediate_todo_el_material_2_3_4_dias_60min() = runGroup("T027", rowsT027(), 20.minutes)

    // ─── Filas de la matriz ────────────────────────────────────────────────────

    private val allCategories: Set<EquipmentCategory> = EquipmentCategory.entries.toSet()

    private fun rowsA(): List<MatrixRow> = listOf(1, 3, 5, 6).flatMap { days ->
        listOf(20, 60, 100).map { minutes ->
            MatrixRow(
                id = "A-d$days-m$minutes",
                group = "A",
                goal = SetupGoal.MUSCLE,
                experience = SetupExperience.NEW,
                categories = emptySet(),
                daysPerWeek = days,
                minutes = minutes,
            )
        }
    }

    private fun rowsB(): List<MatrixRow> = listOf(1, 5, 6).flatMap { days ->
        listOf(60, 100).map { minutes ->
            MatrixRow(
                id = "B-d$days-m$minutes",
                group = "B",
                goal = SetupGoal.MIXED,
                experience = SetupExperience.INTERMEDIATE,
                categories = allCategories,
                daysPerWeek = days,
                minutes = minutes,
                cardioMinutes = 10,
            )
        }
    }

    private fun rowsC(): List<MatrixRow> = listOf(
        setOf(EquipmentCategory.BARBELL),
        setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT),
    ).map { categories ->
        MatrixRow(
            id = "C-" + categories.sorted().joinToString("-"),
            group = "C",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.INTERMEDIATE,
            categories = categories,
            daysPerWeek = 5,
            minutes = 60,
        )
    }

    private fun rowsD(): List<MatrixRow> = listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE).map { experience ->
        MatrixRow(
            id = "D-${experience.name}",
            group = "D",
            goal = SetupGoal.MUSCLE,
            experience = experience,
            categories = allCategories,
            daysPerWeek = 3,
            minutes = 60,
        )
    }

    private fun rowsE(): List<MatrixRow> = listOf(
        MatrixRow(
            id = "E-control-positivo",
            group = "E",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.INTERMEDIATE,
            categories = allCategories,
            daysPerWeek = 3,
            minutes = 100,
        ),
    )

    private fun rowsF(): List<MatrixRow> = listOf(
        MatrixRow(
            id = "F-presupuesto-minimo",
            group = "F",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.NEW,
            categories = allCategories,
            daysPerWeek = 3,
            minutes = 20,
        ),
    )

    /**
     * T-027 — Las TRES frecuencias que el catálogo nativo ya admitía (2..4) con el objetivo MIXED
     * completo: INTERMEDIATE, las 11 categorías, 60 min y cardio CAMINAR 10 min. Mismo perfil que
     * el grupo B salvo por la frecuencia, para que la comparación sea honesta.
     */
    private fun rowsT027(): List<MatrixRow> = listOf(2, 3, 4).map { days ->
        MatrixRow(
            id = "T027-d$days-m60",
            group = "T027",
            goal = SetupGoal.MIXED,
            experience = SetupExperience.INTERMEDIATE,
            categories = allCategories,
            daysPerWeek = days,
            minutes = 60,
            cardioMinutes = 10,
        )
    }

    private data class MatrixRow(
        val id: String,
        val group: String,
        val goal: SetupGoal,
        val experience: SetupExperience,
        val categories: Set<EquipmentCategory>,
        val daysPerWeek: Int,
        val minutes: Int,
        val cardioMinutes: Int? = null,
    ) {
        /** Disponibilidad EXPLÍCITA: vacía = solo peso corporal, nunca `null` (legacy). */
        val availability: EquipmentAvailability get() = EquipmentAvailability(categories)
        val weekdays: Set<Int> get() = (1..daysPerWeek).toSet()
        val environmentLabel: String get() = if (categories.isEmpty()) "none" else "gym"
        val requestedMaterial: String
            get() = if (categories.isEmpty()) "bodyweight_only(empty)" else categories.sorted().joinToString("+")
        val requested: String
            get() = "goal=${goal.name} exp=${experience.name} material=$requestedMaterial " +
                "dias=$daysPerWeek($weekdays) minutos=$minutes cardio=${cardioMinutes ?: "-"}"
    }

    private class RowOutcome(
        val row: MatrixRow,
        val status: String,
        val problems: List<String>,
        val entries: Map<String, String>,
    )

    // ─── Motor de la matriz ───────────────────────────────────────────────────

    private fun runGroup(label: String, rows: List<MatrixRow>, budget: Duration) = runTest(timeout = budget) {
        currentCaseLabel = "grupo-$label"
        trace("GROUP_BEGIN group=$label filas=${rows.size} budgetMs=${budget.inWholeMilliseconds}")
        val startedAt = System.nanoTime()
        val outcomes = rows.map { row ->
            val outcome = evaluateRow(row)
            printMatrixRow(outcome)
            outcome
        }
        trace(
            "GROUP_END group=$label elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000L} " +
                "filas=${outcomes.size} ok=${outcomes.count { it.status == "OK" }} " +
                "conProblemas=${outcomes.count { it.problems.isNotEmpty() }}",
        )
        reportGroup(label, outcomes)
    }

    private fun TestScope.evaluateRow(row: MatrixRow): RowOutcome {
        val store = ViewModelStore()
        val db = KpknDatabase.createInMemory(app)
        val handle = SavedStateHandle()
        val persistence = RoomWizardPersistence(db)
        val commits = RoomWizardCommits(db)
        val environment = FixedSettingsEnvironment(Settings())
        // `materializeOverride` INTENCIONALMENTE ausente (null): motor real de producción.
        val vm = SetupWizardViewModel(app, handle, persistence, environment, commits)
        store.put("t019-${row.id}", vm)
        // Propietario REAL de todo el trabajo de la fila, capturado con la VM viva y ANTES de
        // cualquier clear. Es el padre que cubre a todos sus hijos (escritores de BD, preview y
        // candidatos); esperar sólo la última mutación sería insuficiente.
        val owner: Job = vm.viewModelScope.coroutineContext[Job]
            ?: error("T019: viewModelScope de la fila ${row.id} no expone Job")
        trace("LIFECYCLE row-owner-captured row=${row.id}")
        val evidence = linkedMapOf<String, String>()
        val problems = mutableListOf<String>()
        var harnessFailure = false
        val rowStartedAt = System.nanoTime()
        trace("ROW_BEGIN id=${row.id} inputs=${sanitize(row.requested)}")
        try {
            evidence["requested"] = row.requested

            vm.initialize(SetupWizardMode.FULL, draftId = "t019-${row.id}")
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading }) {
                problems += "HARNESS: el wizard no salió de isLoading (${stateDump(vm.state.value)})"
                return RowOutcome(row, "HARNESS_FAIL", problems, evidence)
            }

            applyFixture(vm, row)

            var settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matchesRequested(it.draft, row) }
            if (settled) {
                repeat(30) { advanceUntilIdle() }
                settled = awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && matchesRequested(it.draft, row) }
            }
            if (!settled) {
                problems += "HARNESS: no se alcanzó un borrador EN REPOSO que conserve las entradas pedidas " +
                    "(borrador real=${draftFingerprint(vm.state.value.draft)}; ${stateDump(vm.state.value)})"
                return RowOutcome(row, "HARNESS_FAIL", problems, evidence)
            }

            var state = vm.state.value
            evidence["draft"] = draftFingerprint(state.draft)
            evidence["declaredEquipment"] = declaredEquipment(state.draft).sorted().joinToString("+")
            evidence["publishedEntries"] = publishedEntryIds(state.draft)
            evidence["candidatesAvailable"] = state.availablePlanCandidates.size.toString()
            evidence["candidatesVisible"] = state.planCandidates.size.toString()
            evidence["isCandidateLoading"] = state.isCandidateLoading.toString()
            evidence["planAdaptedToBodyweight"] = state.planAdaptedToBodyweight.toString()
            evidence["candidates"] = state.availablePlanCandidates.joinToString(";") { "${it.id}[${it.source}]" }
            evidence["candidateReason"] = state.errors["candidates"] ?: "-"

            assertTrue(
                "el cálculo de candidatos debe terminar antes de juzgar la fila " +
                    "(isCandidateLoading=${state.isCandidateLoading})",
                !state.isCandidateLoading,
            )
            if (row.categories.isEmpty()) {
                assertTrue(
                    "una petición solo-peso-corporal no puede activar el segundo pase adaptado " +
                        "(adaptado=${state.planAdaptedToBodyweight})",
                    !state.planAdaptedToBodyweight,
                )
            }
            assertTrue(
                "availablePlanCandidates vacía para un perfil VÁLIDO; motivo real = ${state.errors["candidates"]}",
                state.availablePlanCandidates.isNotEmpty(),
            )
            assertTrue(
                "planCandidates vacía con availablePlanCandidates=${state.availablePlanCandidates.size}",
                state.planCandidates.isNotEmpty(),
            )

            // Testigo: NATIVO primero; si no hay ninguno, cualquier KPKN genuino sirve de testigo.
            val ordered = state.availablePlanCandidates.sortedBy {
                if (it.source == CatalogSource.NATIVE.name) 0 else 1
            }
            val attempts = mutableListOf<String>()
            var witness: SetupPlanCandidate? = null
            var witnessProgram: Program? = null
            for (candidate in ordered) {
                vm.selectPlan(candidate.id)
                if (!awaitPreviewFor(vm, candidate.id, PREVIEW_BUDGET_MS)) {
                    attempts += "${candidate.id}: NO SE ASENTÓ (${stateDump(vm.state.value)})"
                    continue
                }
                val after = vm.state.value
                val program = after.programPreview
                if (program == null) {
                    attempts += "${candidate.id}: sin preview (previewError=${after.previewError} " +
                        "errors[preview]=${after.errors["preview"]} lastFailure=${after.lastFailure})"
                    continue
                }
                if (program.structureTemplateId != null && program.structureTemplateId != candidate.id) {
                    attempts += "${candidate.id}: el preview corresponde a OTRO plan (${program.structureTemplateId})"
                    continue
                }
                val issues = ProgramExecutionContract.validate(program)
                if (issues.isNotEmpty()) {
                    attempts += "${candidate.id}: no ejecutable -> ${issues.joinToString("; ") { it.message }}"
                    continue
                }
                witness = candidate
                witnessProgram = program
                break
            }

            state = vm.state.value
            evidence["witness"] = witness?.let { "${it.id}[${it.source}]" } ?: "-"
            evidence["attempts"] = attempts.ifEmpty { listOf("-") }.joinToString(" ;; ")
            evidence["selectedId"] = state.draft.selectedCatalogId ?: "-"
            evidence["previewError"] = state.previewError ?: "-"
            evidence["fixedEstimate"] = state.fixedSessionEstimateMinutes?.toString() ?: "-"
            evidence["limitations"] = state.previewReport?.limitations?.joinToString(" | ")?.ifBlank { "-" } ?: "-"

            assertTrue(
                "ningún candidato de la lista viable produjo un programa ejecutable. intentos=${evidence["attempts"]}",
                witness != null && witnessProgram != null,
            )
            assertTrue(
                "el borrador debe conservar el plan elegido: ${state.draft.selectedCatalogId} != ${witness?.id}",
                state.draft.selectedCatalogId == witness?.id,
            )
            assertProgramContract(row, witness!!, witnessProgram!!, state, evidence)
        } catch (error: Throwable) {
            // Ninguna excepción se convierte en PASS: la fila queda registrada como problema
            // y el GRUPO entero falla al final con todas las filas con nombre.
            problems += (error.message ?: error.toString()).replace('\n', ' ')
            if (error !is AssertionError) problems += "clase=${error::class.java.name}"
        } finally {
            // Mismo helper que usa la regresión T019_01: cancelación real con `store.clear()`,
            // espera de la finalización REAL del padre de la fila, y sólo después `db.close()`.
            // Si esa finalización no se alcanza en el techo, la BD NO se cierra y se registra
            // HARNESS_BLOCKER: no se finge una limpieza completada. Si el fallo es de aserción y
            // además falla la limpieza, se reportan AMBOS (la aserción va primera).
            val cleanupStartedAt = System.nanoTime()
            val close = closeRowResources(owner, store, db, row.id, ROW_CLEANUP_BUDGET_NANOS)
            if (close.clearFailure != null) {
                harnessFailure = true
                problems += "HARNESS_BLOCKER: store.clear() de la fila ${row.id} falló: " +
                    "${close.clearFailure::class.java.name}: ${close.clearFailure.message}"
            } else if (!close.completed) {
                harnessFailure = true
                problems += "HARNESS_BLOCKER: ${close.detail}"
            }
            val cleanupMillis = (System.nanoTime() - cleanupStartedAt) / 1_000_000L
            trace(
                "LIFECYCLE row-cleanup-end row=${row.id} elapsedMs=$cleanupMillis " +
                    "completed=${close.completed} closeElapsedMs=${close.elapsedMillis} " +
                    "clearFailed=${close.clearFailure != null} harnessFailure=$harnessFailure",
            )
        }
        val status = when {
            harnessFailure -> "HARNESS_FAIL"
            problems.isEmpty() -> "OK"
            else -> "PRODUCT_FAIL"
        }
        trace(
            "ROW_END id=${row.id} status=$status elapsedMs=${(System.nanoTime() - rowStartedAt) / 1_000_000L} " +
                "problems=${problems.size} inputs=${sanitize(row.requested)} " +
                "witness=${sanitize(evidence["witness"].orEmpty())} " +
                "outcome=${sanitize(problems.joinToString(" ~~ "))}",
        )
        return RowOutcome(row, status, problems, evidence)
    }

    /**
     * Oráculo de producto sobre el programa REAL devuelto por el motor. Todas son aserciones de
     * DESEO (qué debe cumplirse), no del comportamiento actual.
     */
    private fun assertProgramContract(
        row: MatrixRow,
        witness: SetupPlanCandidate,
        program: Program,
        state: SetupWizardState,
        evidence: MutableMap<String, String>,
    ) {
        val entry = requireNotNull(PersonalizedPlanCatalog.find(witness.id)) {
            "${row.id}: el plan elegido debe existir en el catálogo de producción: ${witness.id}"
        }
        val isNative = entry.source == CatalogSource.NATIVE

        // (a) Contrato canónico de ejecución de producción.
        val issues = ProgramExecutionContract.validate(program)
        assertTrue(
            "${row.id}: el programa devuelto no es ejecutable -> ${issues.joinToString("; ") { it.message }}",
            issues.isEmpty(),
        )
        ProgramExecutionContract.requireExecutable(program)

        // (b) Sesiones no vacías y con contenido ejecutable real.
        val sessions = program.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
        assertTrue("${row.id}: el programa no tiene sesiones", sessions.isNotEmpty())
        sessions.forEach { session ->
            assertTrue(
                "${row.id}: sesión '${session.id}' sin contenido ejecutable completo",
                SessionTemplateEngine.sessionHasCompleteExecutableContent(session),
            )
        }

        // (c) Frecuencia y calendario reales.
        val plan = program.resolvedSchedulePlan()
        val sessionDays = sessions.mapNotNull { it.dayOfWeek }.toSet()
        evidence["trainingDays"] = plan.trainingDays.sorted().joinToString(",").ifEmpty { "-" }
        evidence["sessionDays"] = sessionDays.sorted().joinToString(",").ifEmpty { "-" }
        assertEquals(
            "${row.id}: el calendario resuelto debe traer los ${row.daysPerWeek} días solicitados, no ${plan.trainingDays.size}",
            row.daysPerWeek,
            plan.trainingDays.size,
        )
        assertEquals(
            "${row.id}: los días del calendario y los de las sesiones deben coincidir",
            plan.trainingDays,
            sessionDays,
        )

        // (d) Tiempo: cada estimador sobre SU fuente. Nativo = slots; fijo = su propio estimate.
        if (isNative) {
            evidence["targetDurationMinutes"] = sessions.joinToString(",") { "${it.targetDurationMinutes}" }
            sessions.forEach { session ->
                assertTrue(
                    "${row.id}: sesión nativa '${session.id}' sin targetDurationMinutes",
                    session.targetDurationMinutes != null,
                )
                assertTrue(
                    "${row.id}: sesión nativa '${session.id}' de ${session.targetDurationMinutes} min " +
                        "supera los ${row.minutes} min pedidos",
                    session.targetDurationMinutes!! <= row.minutes,
                )
            }
        } else {
            val estimate = state.fixedSessionEstimateMinutes
            assertTrue(
                "${row.id}: una receta fija debe publicar su propia estimación de sesión",
                estimate != null,
            )
            assertTrue(
                "${row.id}: la receta fija estima ${estimate} min y el presupuesto es ${row.minutes}",
                estimate!! <= row.minutes,
            )
        }

        // (e) Configuraciones canónicas que resuelven en el catálogo aprobado de MAIN.
        val names = approvedNames
        val strength = sessions.flatMap { it.allExercises() }
            .filter { it.cardioDetails == null && it.mobilitySeries.isEmpty() && it.mobilityConfig == null }
        assertTrue("${row.id}: el programa no tiene ejercicios de fuerza con configuración", strength.isNotEmpty())
        strength.forEach { exercise ->
            val configurationId = exercise.catalogConfigurationId
            assertTrue(
                "${row.id}: '${exercise.name}' sin catalogConfigurationId (no es una configuración canónica)",
                configurationId != null,
            )
            assertTrue(
                "${row.id}: '$configurationId' no resuelve en el catálogo aprobado de MAIN",
                names.containsKey(configurationId),
            )
            assertEquals(
                "${row.id}: el nombre del ejercicio debe ser el canónico del catálogo para $configurationId",
                names[configurationId]?.name,
                exercise.name,
            )
            assertTrue(
                "${row.id}: '${exercise.name}' sin catalogDefinitionId",
                exercise.catalogDefinitionId != null,
            )
        }

        // (f) Honestidad de material: sólo el declarado, sin stock ni gimnasio inventados.
        val declared = declaredEquipment(state.draft)
        assertTrue("${row.id}: el material declarado nunca puede ser un conjunto vacío", declared.isNotEmpty())
        assertTrue("${row.id}: el material declarado nunca puede asumir 'general_gym' ($declared)", "general_gym" !in declared)
        assertTrue(
            "${row.id}: no se puede inventar una configuración de máquina exacta ($declared)",
            declared.none { it.startsWith("machine_config:") },
        )
        assertTrue(
            "${row.id}: esta matriz no declara stock, así que el borrador no puede llevar inventario",
            state.draft.trainingOptions.inventory == null,
        )
        val equipmentByConfiguration = approvedEquipmentByConfiguration
        strength.forEach { exercise ->
            val equipmentId = equipmentByConfiguration.getValue(requireNotNull(exercise.catalogConfigurationId))
            assertTrue(
                "${row.id}: '$equipmentId' (${exercise.catalogConfigurationId}) no pertenece al material declarado $declared",
                equipmentId in declared,
            )
        }

        // (g) Atribución de autor: un método con nombre no se adapta ni se rebautiza en silencio.
        if (!isNative) {
            assertEquals(
                "${row.id}: una receta de autor es de prescripción fija, no personalizable",
                AdaptationPolicy.FIXED_PRESCRIPTION,
                entry.adaptation,
            )
            if (entry.sourceAuthor != null) {
                assertTrue(
                    "${row.id}: el candidato debe publicar autoría y revisión (details=${witness.details})",
                    !witness.details.isNullOrBlank(),
                )
            }
        }
        evidence["attribution"] = "author=${entry.sourceAuthor} revision=${entry.sourceRevision} " +
            "adaptation=${entry.adaptation} disclaimer=${entry.disclaimer != null} details=${witness.details}"

        // (h) Identidad: el preview corresponde al plan elegido y al borrador actual.
        assertEquals(
            "${row.id}: el preview debe llevar el commitId del borrador",
            state.draft.commitId,
            program.id,
        )
        evidence["programId"] = program.id
        evidence["structureTemplateId"] = program.structureTemplateId ?: "-"

        // (i) MIXED: el cardio tiene que ser CONTENIDO ESTRUCTURADO REAL emitido por el motor.
        //
        // Esta sección se evalúa SÓLO cuando el objetivo de la fila es fuerza+cardio: el resto
        // de las 24 filas conserva exactamente su oráculo. No se mira el nombre del programa, ni
        // una anotación, ni una respuesta fija escrita aquí: se leen las APIs canónicas del
        // modelo de producción ([com.example.kpkn.data.models.SessionPart.isCardioPart],
        // [com.example.kpkn.data.models.Exercise.cardioDetails]) sobre las sesiones devueltas.
        // El tipo y los minutos esperados salen de las RESPUESTAS REALES del borrador, no de
        // literales: si el producto cambiara el tipo pedido, esta aserción lo delataría.
        if (row.goal == SetupGoal.MIXED) {
            val requestedType = state.draft.cardioType
            val requestedMinutes = requireNotNull(row.cardioMinutes) {
                "${row.id}: una fila MIXED declara minutos de cardio en su propia definición"
            }
            val declaredType = requireNotNull(requestedType) {
                "${row.id}: una fila MIXED debe declarar un tipo de cardio real en el borrador"
            }
            val declaredMinutes = requireNotNull(state.draft.cardioMinutes) {
                "${row.id}: el borrador MIXED debe conservar los minutos de cardio pedidos"
            }
            assertEquals(
                "${row.id}: el tipo de cardio pedido es CAMINAR y no puede alterarse para conseguir resultado",
                CardioType.WALK,
                declaredType,
            )
            assertEquals(
                "${row.id}: el borrador debe conservar los ${requestedMinutes} min de cardio pedidos",
                requestedMinutes,
                declaredMinutes,
            )
            assertEquals(
                "${row.id}: debe emitirse una sesión por cada día de entrenamiento pedido",
                row.daysPerWeek,
                sessions.size,
            )
            val cardioEvidence = mutableListOf<String>()
            val strengthBySession = mutableListOf<String>()
            sessions.forEach { session ->
                val cardioParts = session.parts.filter { it.isCardioPart() }
                assertEquals(
                    "${row.id}: la sesión '${session.id}' (día ${session.dayOfWeek}) debe llevar EXACTAMENTE " +
                        "un bloque de cardio y lleva ${cardioParts.map { it.id }}",
                    1,
                    cardioParts.size,
                )
                val part = cardioParts.single()
                val cardioExercises = part.exercises
                assertEquals(
                    "${row.id}: el bloque de cardio de '${session.id}' no puede traer material extra: " +
                        cardioExercises.map { it.id },
                    1,
                    cardioExercises.size,
                )
                val cardioExercise = cardioExercises.single()
                val details = requireNotNull(cardioExercise.cardioDetails) {
                    "${row.id}: el ejercicio '${cardioExercise.id}' del bloque de cardio de '${session.id}' " +
                        "no trae cardioDetails: el cardio no es contenido estructurado"
                }
                assertEquals(
                    "${row.id}: tipo de cardio emitido en '${session.id}'",
                    declaredType,
                    details.type,
                )
                val emittedSeconds = requireNotNull(details.targetDurationSeconds) {
                    "${row.id}: el cardio de '${session.id}' no fija duración objetivo"
                }
                assertEquals(
                    "${row.id}: el cardio de '${session.id}' dura ${emittedSeconds}s y se pidieron " +
                        "${declaredMinutes} min",
                    declaredMinutes * 60,
                    emittedSeconds,
                )
                // ANDAR no necesita aparato: el bloque de cardio no puede inventar una
                // configuración de máquina ni colarse en el catálogo de fuerza.
                assertNull(
                    "${row.id}: el cardio CAMINAR de '${session.id}' no puede llevar configuración de " +
                        "máquina '${cardioExercise.catalogConfigurationId}'",
                    cardioExercise.catalogConfigurationId,
                )
                assertFalse(
                    "${row.id}: el cardio CAMINAR de '${session.id}' no puede ser un ejercicio de fuerza " +
                        "del catálogo (catalogDefinitionId=${cardioExercise.catalogDefinitionId})",
                    cardioExercise.catalogDefinitionId != null,
                )
                // Y el cardio no puede esconderse fuera de su bloque.
                assertTrue(
                    "${row.id}: la sesión '${session.id}' no puede llevar cardio suelto fuera del bloque: " +
                        session.exercises.filter { it.cardioDetails != null }.map { it.id },
                    session.exercises.none { it.cardioDetails != null },
                )
                // Una sesión MIXED tiene que ser REALMENTE mixta: además de su bloque de cardio
                // debe emitir contenido de FUERZA propio. La lista se deriva con la MISMA
                // fuente real (`session.allExercises()`) y el MISMO predicado de exclusión
                // cardio/movilidad que usa el oráculo agregado de fuerza de la sección (e), así
                // que no hay un segundo algoritmo de resultado ni heurística por nombre: se
                // cuenta lo que el motor emitió. `SessionTemplateEngine` admite una sesión con
                // sólo cardio, de modo que el agregado NO alcanza para detectarlo: por eso la
                // comprobación es POR SESIÓN y falla aunque otra sesión sí traiga fuerza.
                val sessionStrength = session.allExercises()
                    .filter { it.cardioDetails == null && it.mobilitySeries.isEmpty() && it.mobilityConfig == null }
                assertTrue(
                    "${row.id}: la sesión '${session.id}' (día ${session.dayOfWeek}) es sólo cardio: " +
                        "una sesión mixta debe emitir también contenido de fuerza, y esta sesión no " +
                        "tiene ninguno (todo=${session.allExercises().map { it.id }})",
                    sessionStrength.isNotEmpty(),
                )
                strengthBySession += "${session.id}/d${session.dayOfWeek}=${sessionStrength.size}"
                cardioEvidence +=
                    "d${session.dayOfWeek}:${details.type}/$emittedSeconds s/parte=${part.id}"
            }
            // Nada de cardio inventado en ninguna otra parte del programa.
            val totalCardio = sessions.sumOf { session -> session.allExercises().count { it.cardioDetails != null } }
            assertEquals(
                "${row.id}: el programa no puede emitir cardio extra o por duplicado; hay $totalCardio " +
                    "ejercicios de cardio y ${sessions.size} sesiones",
                sessions.size,
                totalCardio,
            )
            assertTrue(
                "${row.id}: el objetivo MIXED no puede quedarse sin fuerza: cardio=${cardioEvidence}",
                strength.isNotEmpty(),
            )
            evidence["cardio"] = cardioEvidence.joinToString(",").ifEmpty { "-" }
            evidence["cardioRequested"] = "$declaredType/${declaredMinutes} min por día de entrenamiento"
            evidence["strengthBySession"] = strengthBySession.joinToString(",").ifEmpty { "-" }
        }
    }

    /** Eventos PÚBLICOS reales del VM; el material es una declaración explícita, no un default. */
    private fun applyFixture(vm: SetupWizardViewModel, row: MatrixRow) {
        // Vitales: fixtures explícitos y válidos, ningún dato de usuario real.
        vm.updateStep(SetupStepId.NAME) { it.copy(name = "T019") }
        vm.updateStep(SetupStepId.AGE) { it.copy(ageYears = 30) }
        vm.updateStep(SetupStepId.HEIGHT) { it.copy(heightCm = 175.0) }
        vm.updateStep(SetupStepId.WEIGHT) { it.copy(weightKg = 70.0) }
        vm.updateStep(SetupStepId.EQUATION_SEX) { it.copy(profileGender = Gender.MALE) }
        vm.updateStep(SetupStepId.EXPERIENCE) { it.copy(experience = row.experience) }
        vm.updateStep(SetupStepId.GOAL) { it.copy(goal = row.goal, focus = SetupFocus.FULL_BODY) }
        if (row.goal == SetupGoal.MIXED) {
            // MIXED no infiere referencia del objetivo: se declara el MISMO estilo que el
            // objetivo MÚSCULO inferiría, tomado del propio producto y no de un literal.
            vm.updateStep(SetupStepId.STYLE) {
                it.copy(volumeAnswers = it.volumeAnswers.copy(style = SetupGoal.MUSCLE.inferredTrainingStyle))
            }
        }
        vm.updateStep(SetupStepId.EQUIPMENT) { it.copy(trainingEnvironment = row.environmentLabel) }
        vm.updateStep(SetupStepId.AVAILABILITY) {
            it.copy(trainingOptions = it.trainingOptions.copy(availability = row.availability))
        }
        vm.updateStep(SetupStepId.DAYS) {
            it.copy(daysPerWeek = row.daysPerWeek, selectedWeekdays = row.weekdays)
        }
        vm.updateStep(SetupStepId.SESSION_TIME) { it.copy(minutesPerSession = row.minutes) }
        if (row.cardioMinutes != null) {
            vm.updateStep(SetupStepId.CARDIO_TYPE) {
                it.copy(cardioType = CardioType.WALK, cardioMinutes = row.cardioMinutes)
            }
        }
    }

    // ─── Esperas: reloj de pared acotado + advanceUntilIdle ────────────────────

    private fun TestScope.awaitUntil(
        vm: SetupWizardViewModel,
        timeoutMs: Long,
        condition: (SetupWizardState) -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            advanceUntilIdle()
            if (condition(vm.state.value)) return true
            if (System.currentTimeMillis() > deadline) return false
        }
    }

    /**
     * Preview terminal del plan recién elegido, observado DOS veces consecutivas tras drenar:
     * descarta la salida asíncrona obsoleta (un `programPreview` del intento anterior o el
     * `isLoading=false` inicial antes de que arranque el job real).
     */
    private fun TestScope.awaitPreviewFor(vm: SetupWizardViewModel, candidateId: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var stable = 0
        while (true) {
            repeat(10) { advanceUntilIdle() }
            val state = vm.state.value
            val terminal = state.draft.selectedCatalogId == candidateId && !state.isPreviewLoading &&
                !state.isCandidateLoading && !state.isLoading &&
                state.machineState != WizChatMachineState.PreparingPreview &&
                state.machineState != WizChatMachineState.PersistingAnswer &&
                (state.programPreview != null || state.previewError != null)
            stable = if (terminal) stable + 1 else 0
            if (stable >= 2) return true
            if (System.currentTimeMillis() > deadline) return false
        }
    }

    private fun isIdle(state: SetupWizardState): Boolean =
        !state.isLoading && !state.isCommitting && !state.isSubmittingAnswer && !state.isSavingAndExiting &&
            state.machineState != WizChatMachineState.PersistingAnswer &&
            state.machineState != WizChatMachineState.Committing &&
            state.machineState != WizChatMachineState.PreparingPreview &&
            !state.isPreviewLoading && !state.isCandidateLoading && !state.ringsPreviewLoading

    /** El borrador real tiene que conservar EXACTAMENTE lo pedido: no se cambia ninguna respuesta. */
    private fun matchesRequested(draft: SetupWizardDraft, row: MatrixRow): Boolean =
        draft.goal == row.goal &&
            draft.experience == row.experience &&
            draft.focus == SetupFocus.FULL_BODY &&
            draft.daysPerWeek == row.daysPerWeek &&
            draft.selectedWeekdays == row.weekdays &&
            draft.minutesPerSession == row.minutes &&
            draft.trainingOptions.availability == row.availability &&
            draft.trainingOptions.inventory == null &&
            draft.programRoute == SetupProgramRoute.CUSTOMIZABLE &&
            draft.trainingPath != SetupTrainingPath.FROM_SCRATCH &&
            draft.selectedCatalogId == null &&
            draft.selectedSplitId == null &&
            draft.customSplitPattern.isEmpty() &&
            draft.ageYears == 30 && draft.heightCm == 175.0 && draft.weightKg == 70.0 &&
            (row.cardioMinutes == null ||
                (draft.cardioType == CardioType.WALK && draft.cardioMinutes == row.cardioMinutes)) &&
            (row.goal != SetupGoal.MIXED || draft.trainingReference() == TrainingReference.HYPERTROPHY)

    /**
     * Equipo efectivo por el CONTRATO DE PRODUCCIÓN ([effectiveEquipment]). Con `availability` no
     * nulo la producción ignora el argumento legacy, así que se le pasa vacío: aquí no se
     * reimplementa ninguna decisión de material.
     */
    private fun declaredEquipment(draft: SetupWizardDraft): Set<String> =
        draft.trainingOptions.effectiveEquipment(emptySet())

    /** Entradas que el PLANificador de producción publica para este perfil (evidencia de prefiltro). */
    private fun publishedEntryIds(draft: SetupWizardDraft): String =
        SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = draft.trainingReference(),
                frequency = draft.daysPerWeek,
                equipment = declaredEquipment(draft),
                level = when (draft.experience) {
                    SetupExperience.ADVANCED -> CatalogLevel.ADVANCED
                    SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE
                    else -> CatalogLevel.BEGINNER
                },
                focus = com.example.kpkn.data.programs.TrainingFocus.valueOf(draft.focus.name),
                protocolOnly = draft.programRoute == SetupProgramRoute.PROTOCOL,
                mixedTraining = draft.goal == SetupGoal.MIXED,
            ),
        ).joinToString(",") { it.id }.ifEmpty { "-" }

    // ─── Evidencia e informe ──────────────────────────────────────────────────

    private fun draftFingerprint(draft: SetupWizardDraft): String =
        "goal=${draft.goal} exp=${draft.experience} focus=${draft.focus} dias=${draft.daysPerWeek}" +
            " weekdays=${draft.selectedWeekdays.sorted()} min=${draft.minutesPerSession}" +
            " cardio=${draft.cardioType}/${draft.cardioMinutes}" +
            " availability=${draft.trainingOptions.availability?.categories?.sorted()}" +
            " inventory=${draft.trainingOptions.inventory != null}" +
            " env=${draft.trainingEnvironment} route=${draft.programRoute} path=${draft.trainingPath}" +
            " split=${draft.selectedSplitId} selectedCatalog=${draft.selectedCatalogId}" +
            " ref=${draft.trainingReference()} age=${draft.ageYears} h=${draft.heightCm} w=${draft.weightKg}"

    private fun stateDump(state: SetupWizardState): String =
        "machine=${state.machineState} errores=${state.errors} lastFailure=${state.lastFailure} " +
            "previewError=${state.previewError} cargando=${state.isPreviewLoading}/${state.isCandidateLoading}/" +
            "${state.ringsPreviewLoading} candidatos=${state.planCandidates.size}/${state.availablePlanCandidates.size} " +
            "borrador=${draftFingerprint(state.draft)}"

    private fun printMatrixRow(outcome: RowOutcome) {
        val row = outcome.row
        val e = outcome.entries
        println(
            listOf(
                "MATRIX_ROW",
                sanitize(row.id),
                sanitize(row.group),
                sanitize(row.goal.name),
                sanitize(row.experience.name),
                sanitize(row.requestedMaterial),
                row.daysPerWeek.toString(),
                row.minutes.toString(),
                (row.cardioMinutes ?: 0).toString(),
                outcome.status,
                outcome.problems.size.toString(),
                sanitize(e["draft"].orEmpty()),
                sanitize(e["declaredEquipment"].orEmpty()),
                sanitize(e["publishedEntries"].orEmpty()),
                sanitize(e["candidates"].orEmpty()),
                sanitize(e["candidateReason"].orEmpty()),
                sanitize(e["witness"].orEmpty()),
                sanitize(e["attempts"].orEmpty()),
                sanitize(e["trainingDays"].orEmpty()),
                sanitize(e["sessionDays"].orEmpty()),
                sanitize(e["targetDurationMinutes"].orEmpty()),
                sanitize(e["fixedEstimate"].orEmpty()),
                sanitize(e["attribution"].orEmpty()),
                sanitize(e["limitations"].orEmpty()),
                sanitize(outcome.problems.joinToString(" ~~ ")),
                // T-027: cardio estructurado realmente emitido. Columnas ADITIVAS al final: las
                // 25 columnas originales conservan su posición para poder comparar con T-024-r1.
                sanitize(e["cardio"].orEmpty()),
                sanitize(e["cardioRequested"].orEmpty()),
                // T-027-r2: ejercicios de FUERZA emitidos por cada sesión mixta (sólo MIXED).
                sanitize(e["strengthBySession"].orEmpty()),
            ).joinToString("|"),
        )
    }

    private fun sanitize(value: String): String = value.replace('|', '/').replace('\n', ' ').trim()

    private fun reportGroup(label: String, outcomes: List<RowOutcome>) {
        val failures = outcomes.filter { it.problems.isNotEmpty() }
        val harnessBlocked = outcomes.count { it.status == "HARNESS_FAIL" }
        val reachedProgram = outcomes.count { it.entries["witness"] != null && it.entries["witness"] != "-" }
        println(
            "[T-019] grupo $label: filas=${outcomes.size} ok=${outcomes.count { it.status == "OK" }} " +
                "conProblemas=${failures.size} harnessBlocked=$harnessBlocked filasQueAlcanzaronPrograma=$reachedProgram",
        )
        if (failures.isEmpty()) return
        val report = buildString {
            append("[T-019] grupo $label: ${failures.size}/${outcomes.size} filas con problemas")
            append(" (harnessBlocked=$harnessBlocked, filasQueAlcanzaronPrograma=$reachedProgram)\n")
            failures.forEach { outcome ->
                append("FALLA [${outcome.status}] ${outcome.row.id} :: ${outcome.row.requested}\n")
                outcome.problems.forEach { append("    - $it\n") }
                outcome.entries.forEach { (key, value) -> append("    $key=$value\n") }
            }
        }.trimEnd()
        if (harnessBlocked == outcomes.size) {
            fail("HARNESS_BLOCKER: ninguna fila se asentó; el arnés no es evidencia de producto.\n$report")
        }
        fail(report)
    }

    // ─── Adaptadores reales sobre Room ───────────────────────────────────────

    /** Adaptador fino y REAL sobre Room: delega, nunca reimplementa el guard. */
    private class RoomWizardPersistence(
        db: KpknDatabase,
        private val gate: RowSaveGate? = null,
    ) : SetupWizardPersistence {
        private val drafts = SetupDraftRepository(db)
        private val resolver = SetupDraftResolver(db)

        override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)

        /**
         * Delegación real a [SetupDraftRepository] (que escribe con
         * `withContext(Dispatchers.IO) { db.withTransaction { … } }`). La puerta es SÓLO-TEST y
         * afecta únicamente a esta regresión: con `gate == null` —las 24 filas de la matriz y el
         * control E— el comportamiento es exactamente el de antes.
         *
         * Cuando la puerta está armada, la espera controlada ocurre en `Dispatchers.IO` y **nunca**
         * dentro de `runCurrent()` ni en Main. Se envuelve en `NonCancellable` a propósito para
         * que la escritura real sobreviva a la cancelación de `store.clear()` y se complete DESPUÉS
         * de la liberación: es una condición de estrés deliberadamente resistente a la
         * cancelación en un arnés de test, **no** una afirmación de que una mutación normal de
         * producción use `NonCancellable` (la matriz no invoca `saveAndExit`/`confirmDiscard`).
         */
        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft {
            val active = gate
            return if (active != null && active.armed) {
                withContext(Dispatchers.IO + NonCancellable) {
                    active.writeEntered.countDown()
                    if (!active.release.await(active.holdMillis, TimeUnit.MILLISECONDS)) {
                        active.holdTimedOut = true
                    }
                    val saved = drafts.save(draftId, payloadJson, revision, catalogRevision)
                    active.delegateCompleted = true
                    active.trace("GATE delegate-completed draftId=$draftId")
                    saved
                }
            } else {
                drafts.save(draftId, payloadJson, revision, catalogRevision)
            }
        }

        override suspend fun discard(draftId: String) = drafts.discard(draftId)

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
    }

    /**
     * Puerta SÓLO-TEST sobre el delegado real de guardado, con contadores y señales reales. Se arma
     * **después** de la inicialización y afecta a la siguiente mutación pública explícita. Todas sus
     * esperas tienen techo finito y liberación garantizada, de modo que ni un fallo de aserción
     * inesperado pueda dejar al worker colgado.
     */
    private class RowSaveGate(private val sink: (String) -> Unit) {
        @Volatile
        var armed: Boolean = false
            private set

        @Volatile
        var holdMillis: Long = 20_000L

        @Volatile
        var holdTimedOut: Boolean = false

        @Volatile
        var delegateCompleted: Boolean = false

        val writeEntered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun arm() {
            armed = true
            trace("GATE armed holdMs=$holdMillis")
        }

        fun trace(line: String) = sink(line)

        fun releaseGate() {
            release.countDown()
            sink("GATE release signalled")
        }
    }

    /**
     * Espera acotada y finita de la escritura real aparcada en la puerta, bombeando el scheduler de
     * test para que la corrutina de la mutación llegue a `Dispatchers.IO`. NUNCA espera sin cota.
     */
    private fun TestScope.awaitGateEntered(gate: RowSaveGate, budgetMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            dispatcher.scheduler.runCurrent()
            if (gate.writeEntered.await(5, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    /**
     * Drenado acotado tras liberar la puerta: se espera la finalización REAL del padre de la fila y
     * la finalización del delegado, bombeando el scheduler. Techo finito; nunca espera sin cota.
     */
    private fun TestScope.awaitOwnerAfterRelease(
        owner: Job,
        gate: RowSaveGate,
        budgetMillis: Long,
    ): Boolean {
        val deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            dispatcher.scheduler.runCurrent()
            if (owner.isCompleted && gate.delegateCompleted) return true
            Thread.sleep(2L)
        }
        return false
    }


    /** Coordinador REAL de altas (sin repos): esta matriz no confirma ninguna alta. */
    private class RoomWizardCommits(db: KpknDatabase) : SetupWizardCommits {
        private val coordinator = SetupCommitCoordinator(db)

        override suspend fun commit(request: SetupCommitRequest): SetupCommitResult = coordinator.commit(request)
    }

    /** Entorno acotado: fija Settings y no toca los repos de Android. */
    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }

    private companion object {
        const val ASSET_NAME = "exercise_catalog_v2.json"
        const val SETTLE_BUDGET_MS = 60_000L
        const val PREVIEW_BUDGET_MS = 60_000L

        /** Techos EXPLÍCITOS del arnés de limpieza, medidos con reloj monótono. */
        const val CLOSE_BUDGET_MILLIS = 15_000L
        const val CLOSE_BUDGET_NANOS = CLOSE_BUDGET_MILLIS * 1_000_000L
        const val ROW_CLEANUP_BUDGET_MILLIS = 20_000L
        const val ROW_CLEANUP_BUDGET_NANOS = ROW_CLEANUP_BUDGET_MILLIS * 1_000_000L

        /** Techos de la regresión T019_01. Todos finitos; la puerta se libera siempre. */
        const val GATE_ENTER_BUDGET_MILLIS = 10_000L

        /** Plazo CORTO e inyectado a propósito: la regresión pide que no dé tiempo a completar. */
        const val REGRESSION_SHORT_BUDGET_MILLIS = 200L
        const val REGRESSION_SHORT_BUDGET_NANOS = REGRESSION_SHORT_BUDGET_MILLIS * 1_000_000L
        const val GATE_RELEASE_BUDGET_MILLIS = 20_000L

        /**
         * Contaminación del ciclo de vida en el proceso de pruebas. Una vez marcada, los métodos
         * posteriores fallan rápido con `HARNESS_BLOCKER` (nunca skip, nunca PASS) en vez de
         * operar sobre un estado cuyo cierre quedó a medias.
         */
        val cleanupContamination = AtomicReference<String?>(null)

        val traceLock = Any()

        @Volatile
        var traceWriter: PrintWriter? = null

        @Volatile
        var traceFile: File? = null

        /**
         * Cierre EXPLÍCITO del escritor de traza propio, en el punto de ciclo de vida de la clase
         * (`@AfterClass`), que corre cuando ya han terminado todos los métodos y con ellos el
         * cleanup por fila y los cierres reales de repositorio. Con esto la traza deja de depender
         * de la finalización de JVM/OS para liberar el descriptor.
         *
         * SEMÁNTICA REAL DEL ESCRITOR — `traceWriter` es un `java.io.PrintWriter`, y eso obliga a
         * corregir la afirmación anterior de este KDoc: `PrintWriter.write/flush/close()` **nunca
         * lanzan**; capturan internamente cualquier `IOException` y sólo la registran en un
         * indicador agregado que se consulta con `checkError()`. Por tanto un `close()` fallido se
         * leería como un cierre «sin excepción» si nadie mira ese indicador.
         *
         * - Camino sano: marcador de punto final volcado justo ANTES del cierre real, después
         *   `flush()` + `close()` explícitos y a continuación la consulta OBLIGATORIA a
         *   `checkError()`. Si el indicador está activo se lanza un fallo explícito de test **antes**
         *   de anular las referencias o emitir la confirmación de cierre, de modo que un fallo de
         *   E/S nunca pueda leerse como traza cerrada correctamente. El indicador es **agregado**
         *   (escritura, flush o cierre): no se afirma una etapa concreta que la API no distingue.
         * - Camino envenenado (`cleanupContamination` marcada): hay trabajo real aún vivo, así que
         *   cerrar el escritor bajo un productor vivo sería una carrera. Se registra como evidencia
         *   de ejecución envenenada y la contención queda en manos del proceso externo.
         */
        @AfterClass
        @JvmStatic
        fun closeOwnedTraceWriter() {
            val writer = traceWriter ?: return
            val contamination = cleanupContamination.get()
            if (contamination != null) {
                // Productor todavía vivo: NO se cierra el escritor bajo él. Se registra el hecho.
                synchronized(traceLock) {
                    writer.println("LIFECYCLE trace-endpoint status=POISONED writerClosed=false :: $contamination")
                    writer.flush()
                }
                return
            }
            synchronized(traceLock) {
                // Marcador de punto final, volcado inmediatamente antes del cierre real para que el
                // verificador pueda detectar el extremo. No declara éxito de cierre.
                writer.println("LIFECYCLE trace-endpoint status=HEALTHY closeAttempted=true writerClosedPending=true")
                writer.flush()
                writer.close()
                // `PrintWriter.close()` NO lanza: el fallo de E/S vive en este indicador agregado.
                if (writer.checkError()) {
                    throw AssertionError(
                        "HARNESS_BLOCKER: el escritor de traza propio reportó un fallo de E/S tras " +
                            "escritura/flush/close (checkError=true). PrintWriter sólo expone un " +
                            "indicador agregado, así que no se puede atribuir a una etapa concreta. " +
                            "NO se anulan las referencias ni se declara la traza cerrada correctamente.",
                    )
                }
            }
            traceWriter = null
            traceFile = null
            println("[T-019] traza propia cerrada explícitamente en @AfterClass (flush+close reales, checkError=false)")
        }

        /**
         * Catálogo aprobado desde el asset de MAIN ya verificado. Cacheado a nivel de proceso
         * porque el fichero en disco no cambia durante una ejecución y `decodeApproved` sobre
         * ~4 MB de JSON es caro; la identidad se comprueba una vez en
         * [T019_00_asset_identidad_y_oraculo_de_configuraciones].
         */
        val approvedCatalog: ExerciseCatalogV2 by lazy {
            ExerciseCatalogV2Loader.decodeApproved(resolveMainAssetFile().readText())
        }

        val approvedNames: Map<String, ExerciseMuscleInfo> by lazy {
            approvedCatalog.toLegacyConfigurationLookup()
        }

        val approvedEquipmentByConfiguration: Map<String, String> by lazy {
            approvedCatalog.families.flatMap { it.definitions }.flatMap { it.configurations }
                .associate { it.id to it.profile.equipmentId }
        }

        /** Sube por los ancestros del directorio de trabajo del test; nada de rutas fijas. */
        fun resolveMainAssetFile(): File {
            val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
            var dir: File? = start
            var hops = 0
            while (dir != null && hops < 12) {
                listOf(
                    "src${File.separator}main${File.separator}assets${File.separator}$ASSET_NAME",
                    "app${File.separator}src${File.separator}main${File.separator}assets${File.separator}$ASSET_NAME",
                ).forEach { relative ->
                    val candidate = File(dir, relative)
                    if (candidate.isFile) return candidate
                }
                dir = dir.parentFile
                hops++
            }
            fail("HARNESS_BLOCKER: no se encontró src/main/assets/$ASSET_NAME subiendo desde $start")
            error("inalcanzable: fail() siempre lanza")
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
    }
}
