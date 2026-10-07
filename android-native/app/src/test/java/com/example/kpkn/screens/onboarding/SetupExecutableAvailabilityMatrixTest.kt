package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
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
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.PlanRejectionPresenter
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.RejectionAction
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.templates.SessionTemplateEngine
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.effectiveEquipment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * T-019 — MATRIZ DE FALSACIÓN «ANTES DE SPLIT» sobre el [SetupWizardViewModel] REAL.
 *
 * Qué demuestra y qué NO demuestra:
 * - Evalúa perfiles VÁLIDOS y explícitos de principio a fin (los 24 originales A12, B6, C2,
 *   D2, E1 y F —que desde 2026-10-02 son F-m20 negativa + F-m28 positiva, ver GRUPO F—, más
 *   los de T027 y la fila de bloqueo T-001) contra
 *   los motores de producción ([com.example.kpkn.domain.onboarding.SetupTrainingPlanner],
 *   `SimpleCyclePersonalizer` a través de `materializeProgram` del VM, y las recetas
 *   PROTOCOL/TEMPLATE).
 * - `materializeOverride` queda **NULL** en TODAS las filas de la matriz: en esta clase no hay
 *   generador, candidato ni predicado duplicado. La lista de candidatos la publica el VM y el
 *   preview lo materializa el motor real. La ÚNICA excepción es la regresión de concurrencia
 *   `T001_03_*`, que inyecta su propia puerta sólo-test SOBRE el materializador para decidir
 *   CUÁNDO se materializa —nunca qué candidatos se publican— y demostrar que un resultado
 *   antiguo no sobreescribe al nuevo; esa prueba no ejerce materialización de catálogo.
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
 *  5. Tiempo: la fuente NATIVE se mide con el estimador común de producción
 *     (`SessionDurationEstimator`, §12.2) aplicado AQUÍ a cada sesión del programa devuelto, no
 *     solo con lo que el generador sella en `Session.targetDurationMinutes` (que además debe
 *     coincidir con esa medida: un generador no puede pasar sellando su propia cifra); una receta
 *     fija usa SU propio `state.fixedSessionEstimateMinutes`. Son dos estimadores distintos y cada
 *     aserción se hace sobre su propia fuente: aquí no se finge que coincidan.
 *  6. T-027 — sólo cuando `row.goal == MIXED`: el cardio tiene que ser CONTENIDO ESTRUCTURADO
 *     emitido por el motor —exactamente un bloque de cardio por día de entrenamiento, con el tipo
 *     y los minutos REALES declarados en el borrador (CAMINAR), sin cardio suelto, sin cardio
 *     duplicado y sin configuración de máquina inventada para andar—, junto a fuerza real. Se
 *     leen las APIs canónicas del modelo de producción, no el nombre del programa ni una anotación.
 *     Las filas NO MIXED conservan su oráculo intacto.
 *  7. T-001 — ANTES de exigir la lista, cada rechazo estructurado
 *     (`state.candidateRejections`) tiene etapa coherente con su causa y una causa que conserva
 *     clase+mensaje (AC-T001-02), y la fila registra ID publicado, equipo efectivo y primer
 *     rechazo. La fila de bloqueo (gimnasio + «Fuerza y músculo» + 5 días) usa este mismo
 *     oráculo: si el producto la bloquea, FALLA y ese rojo es el baseline de T-001 (AC-T001-01),
 *     sin relajar la expectativa.
 *
 * GRUPO A (consolidación 2026-10-01, decisión del propietario): el mínimo real de Músculo corporal
 * principiante es 21 min, no 20, en las cuatro frecuencias del grupo (1, 3, 5 y 6 días). Se calcula
 * POR FILA y de forma independiente en [independentBodyweightMuscleFloorMinutes] (arquetipos de
 * r2 §11.3 con la desviación DEV-r2-01 en 5/6 días + las constantes del estimador común de §12.2;
 * no lee el motor): un día mínimo es 180 s de calentamiento general + 300 s por H corporal + 90 s
 * por aproximación (más los accesorios que el suelo de dosis diaria obliga a conservar). El día
 * más largo de cada fila —BFA/BFB en 1 y 3 días, BL_MRV en 5 y 6— son 3 H + 2 aproximaciones =
 * 1260 s = 21,0 min; BL (19,5 → 20) y BU (14,8 → 15) quedan por debajo. Respaldado por los 41 min
 * de Atleta corporal 1 día de `NativeProfileRecipeAndFitterTest`. Por eso `rowsA()` conserva sus 12
 * positivos con {30,60,100} min; las 4 filas de 20 min son NEGATIVAS (`T019_A_negativo_…`:
 * TIME_BUDGET con requiredMinutes == mínimo de ESA fila) y 4 filas en el mínimo de cada fila
 * (`T019_A_suficiencia_…`) prueban que ese TIME_BUDGET no es un cajón de sastre: exigen que el plan
 * PROPIO `native:muscle-foundation-v2` —no cualquier otro candidato histórico que también quepa—
 * llegue a un programa ejecutable con exactamente ese presupuesto. Las filas negativas instalan los
 * metadatos de composición del catálogo en `CompositionMetadataHolder` como hace
 * `initializeExerciseDatabase` en producción (ver [withProductionCompositionMetadata]). Los
 * contadores de A12, B6, C2, D2 y E1 no cambian (F pasa de 1 a 2 filas: ver GRUPO F); las filas
 * A20, A21 y las de Atleta completo con material completo
 * (`T006_Q4_completeAthlete_full_material_…`) se suman aparte. B y T027 siguen siendo la regresión del generador HISTÓRICO `native:strength-cardio`
 * (MIXED).
 *
 * GRUPO F (consolidación 2026-10-02; testigo cambiado en la curaduría 2026-10-03, C.P2b): la fila
 * F-presupuesto-minimo (Músculo, principiante, 11 categorías declaradas, 3 días, 20 min) se
 * desdobla en F-m20 NEGATIVA y F-m28 POSITIVA. Hasta C.P2b el testigo era el generador HISTÓRICO
 * `native:full-body`, cuyo mínimo (21 min) dependía del desempate del catálogo tras el lote «pecho»
 * (`flat_chest_fly__machine` gana tres estabilizadores y el desempate pasa del aislamiento al press
 * convergente). La decisión D2 (DEC-w2-07) lo oculta del planner: `native:full-body` ya no es
 * candidato y el testigo pasa al PLAN PROPIO `native:muscle-foundation-v2`, como en el GRUPO A y
 * con el mismo criterio de suficiencia. En esta fila el planner publica tres candidatos
 * —`native:machine-muscle`, `native:muscle-foundation-v2` y `native:bodyweight`— y a 20 min los
 * tres rechazan con TIME_BUDGET tipado (mínimos de 21, 28 y 21 min): sigue siendo un negativo
 * limpio, sin candidatos viables ni tarjetas.
 *
 * El mínimo del plan propio, 28 min, se calcula de forma independiente en
 * [independentOwnMusclePlanFloorMinutes] (no lee el motor): con material el calendario de 3 días
 * es FA/FB/FA (r2 §11.3: FA = S, B, R, D y core; FB = U, O, V, D y gemelo) y el paso 2 de r2 §12.3
 * retira primero el aislamiento (I) y luego el core (C) mientras el día conserve el suelo de dosis
 * diaria (≥ 2 configuraciones y ≥ 4 series). El día mínimo son, pues, CUATRO H de principiante
 * (2 series de 8–12 repeticiones, descanso de 120 s), cada uno de una familia de patrón distinta
 * (sentadilla o unilateral, empuje, tirón, bisagra), y el estimador común (§12.2) los mide así:
 * 180 s de calentamiento general + 4 × (60 s de preparación + 2 series de 48 s —el extremo alto
 * del rango, max(4 s × 12, 45 s)— + 120 s de descanso entre ambas) + 4 aproximaciones técnicas de
 * 90 s (30 s de ejecución + 60 s de descanso, una por familia de patrón) = 180 + 1104 + 360 =
 * 1644 s = 27,4 → 28 min, en FA y en FB. Medido con el motor el 2026-10-03: de 20 a 27 min el plan
 * propio informa TIME_BUDGET con 28; con 28 min llega a programa y su sesión más larga mide 28
 * (1644 s: preparación 240, ejecución 384, descansos 480, calentamiento y aproximaciones 540); con
 * 30 min recupera el core y el gemelo. F-m20 exige que `native:muscle-foundation-v2` rechace con
 * TIME_BUDGET TIPADO y requiredMinutes == 28; F-m28 exige que ESE mismo plan llegue a un programa
 * ejecutable con exactamente 28 min y que su sesión más larga mida 28. Contadores sin cambios: las
 * 25 filas originales (A12, B6, C2, D2, E1 y F2).
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

    /**
     * A12: MÚSCULO, NEW→BEGINNER, material declarado explícitamente VACÍO (solo peso corporal),
     * días {1,3,5,6} × minutos {30,60,100}. Los 20 min no están aquí: el piso real es 21 min y
     * esas cuatro filas son negativas ([T019_A_negativo_musculo_new_peso_corporal_20min_4_filas]).
     */
    @Test
    fun T019_A_musculo_new_peso_corporal_12_filas() = runGroup("A", rowsA(), 25.minutes)

    /**
     * A20 (negativo): las mismas entradas de A con 20 min. El plan propio de Músculo necesita,
     * en cada una de las cuatro frecuencias, el mínimo de ESA fila (aritmética independiente por
     * fila en [independentBodyweightMuscleFloorMinutes]: 21 min), así que el producto debe
     * rechazar con TIME_BUDGET tipado, ese mínimo exacto y etapa de duración, conservando las
     * respuestas; nunca un PASS vacío ni un éxito parcial (AC-T004-03).
     */
    @Test
    fun T019_A_negativo_musculo_new_peso_corporal_20min_4_filas() =
        runGroup("A20", rowsANegative20(), 15.minutes)

    /**
     * A21 (suficiencia): con el mínimo real de cada fila (21 min exactos) el plan PROPIO
     * `native:muscle-foundation-v2` SÍ llega a un programa ejecutable. Un candidato histórico que
     * también quepa no vale como testigo: sin esta exigencia el TIME_BUDGET de A20 podría ser un
     * cajón de sastre (así pasaba la fila de 3 días mientras el fitter informaba 25 min).
     */
    @Test
    fun T019_A_suficiencia_musculo_new_peso_corporal_21min_4_filas() =
        runGroup("A21", rowsASufficiencyAtFloor(), 15.minutes)

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

    /**
     * F2: MÚSCULO, NEW, 11 categorías, 3 días. F-m20 (presupuesto mínimo admitido) es NEGATIVA:
     * el plan propio `native:muscle-foundation-v2` rechaza con TIME_BUDGET tipado y el mínimo real
     * independiente (28 min, ver GRUPO F y [independentOwnMusclePlanFloorMinutes]). F-m28 es
     * POSITIVA: ese mismo plan llega a un programa ejecutable con exactamente 28 min.
     */
    @Test
    fun T019_F_musculo_new_todo_el_material_3_dias_20min() = runGroup("F", rowsF(), 15.minutes)

    /**
     * T-027 — REGRESIÓN REAL DEL ViewModel para las frecuencias **dentro** de 2..4 del objetivo
     * MIXED (2, 3 y 4 días / 60 min, INTERMEDIATE, las 11 categorías, cardio CAMINAR 10 min).
     *
     * Son TRES filas ADITIVAS: las originales (A12, B6, C2, D2, E1 y F, esta última desdoblada en
     * F-m20/F-m28 por el GRUPO F) se conservan tal cual y este método NO las sustituye. Se reusan
     * `runGroup`/`evaluateRow` y TODO el oráculo de `assertProgramContract`, incluida la sección MIXED que exige cardio estructurado real. Su
     * propósito es que la ampliación de la familia nativa a 1..6 no rompa lo que ya funcionaba, y
     * que las frecuencias 2, 3 y 4 con cardio se comprueban por el mismo camino real que 1, 5 y 6.
     */
    @Test
    fun T027_M_mixto_intermediate_todo_el_material_2_3_4_dias_60min() = runGroup("T027", rowsT027(), 20.minutes)

    /**
     * T-001 / AC-T001-01 — REGRESIÓN DEL BLOQUEO con el catálogo REAL de producción:
     * gimnasio (todas las categorías) + «Fuerza y músculo» + 5 días / 60 min, con inputs
     * explícitos y `materializeOverride` AUSENTE (esta fila pasa por el motor real, igual
     * que las 25 anteriores).
     *
     * El oráculo es el MISMO de la matriz: candidato real publicado y preview ejecutable
     * (`ProgramExecutionContract`), más el registro que pide el plan — ID publicado, equipo
     * efectivo y primer rechazo — que `evaluateRow` deja en la evidencia de la fila. Si el
     * producto sigue bloqueado, esta fila FALLA y ese rojo es el baseline documentado de
     * T-001: aquí NO se relaja la expectativa para aceptar el error (T-006 debe ponerla verde).
     * AC-T001-02 se evalúa dentro de `evaluateRow`, para TODAS las filas.
     */
    @Test
    fun T001_FuerzaMusculo_gimnasio_5_dias_60min() = runGroup("T001", rowsT001(), 15.minutes)

    /** T-006 Q4: el perfil nuevo Atleta completo llega a un preview real del VM con solo cuerpo. */
    @Test
    fun T006_Q4_completeAthlete_bodyweight_one_day_60min_real_vm_preview() =
        runGroup("T006-Q4-Athlete", rowsT006Athlete(), 20.minutes)

    /**
     * T-006 Q4 / §17.2 #5: Atleta completo con TODO el material (11 categorías) y cardio de 10 min,
     * 1 y 2 días / 60 min (principiante e intermedio) llega a un preview real del VM con la receta
     * nativa `native:complete-athlete-v2` (cuatro componentes, estimador común <= 60). Complementa a
     * B/T027, que siguen siendo la regresión del generador histórico `strength-cardio`: el mixto de
     * 1 día/60 min no es imposible, y esta fila fija que el perfil de producto tampoco lo es.
     */
    @Test
    fun T006_Q4_completeAthlete_full_material_one_and_two_days_60min_real_vm_preview() =
        runGroup("T006-Q4-AthleteFull", rowsT006AthleteFullMaterial(), 20.minutes)

    /** T-006 Q4: un presupuesto realmente insuficiente queda tipado antes de review, no como PASS vacío. */
    @Test
    fun T006_Q4_bodyweight_time_below_minimum_is_a_structured_negative() =
        runGroup("T006-Q4-TimeNegative", rowsT006TimeNegative(), 15.minutes)

    /**
     * T-001 / AC-T001-03 — un resultado de candidatos ANTIGUO, y su cancelación, jamás
     * sobreescribe la respuesta más nueva.
     *
     * Usa MÚSCULO porque necesita DOS conjuntos de ID publicados NO vacíos y DISTINTOS de A
     * (3 días) para poder observar qué respuesta ganó: X (6 días) y B (5 días). Con este perfil la
     * última traza registrada publica A=8, X=5 y B=6 entradas; la distinción se comprueba como
     * PRECONDICIÓN dentro de la propia prueba (A≠X y A≠B), no se da por supuesta. «Fuerza y
     * músculo» ya no es un caso de conjunto único (`powerbuilding-foundation-v2` cubre 1..6 días):
     * serviría igual, pero se conserva MÚSCULO por ser el perfil con la evidencia registrada. El
     * caso de bloqueo de cinco días es la fila T001, que ejerce el motor real SIN puerta.
     *
     * Secuencia REAL (3 → 6 → 3 → 5 días), sin carreras de tiempo ni esperas sin cota:
     *  1. El materializador sólo-test queda ATRAPADO en su puerta: el cálculo de candidatos
     *     de A (3 días) queda en vuelo con la publicación pendiente.
     *  2. Se cambia UNA respuesta (X, 6 días) con el guardado de Room FALLANDO: es el camino real
     *     de fallo de persistencia —el borrador cambia EN MEMORIA y `updateCandidates` NO se
     *     llama—, así que el job de A queda obsoleto SIN cancelarse. La lista publicada se
     *     retira y la marca de carga no finge que el job viejo represente a X. PRECONDICIÓN:
     *     los ID publicados de A y de X son distintos.
     *  3. El borrador vuelve a A (3 días, también con el guardado fallando), por lo que la clave A
     *     vuelve a ser igual (ABA): solo la generación, no la comparación de claves, puede impedir
     *     que el resultado tardío se publique. Tras soltar la puerta, lo que se compara es contra A.
     *  4. Se suelta la puerta: el cálculo obsoleto termina y llega a su punto de publicación.
     *     Ni la lista ni `candidateRejections` ni `errors["candidates"]` pueden ser rellenadas.
     *  5. Con la persistencia restaurada, la respuesta NUEVA (B, 5 días) sí recalcula y publica:
     *     el estado final tiene que describir a 5 días (sus ID publicados, su mensaje y sus
     *     respuestas intactas), nunca a A. PRECONDICIÓN: los ID publicados de B y de A son distintos.
     *
     * Sin la generación del paso 3, el job de A publicaría sobre el segundo borrador A; esta
     * prueba discrimina específicamente la carrera ABA además del cambio simple de clave.
     */
    @Test
    fun T001_03_resultado_antiguo_no_sobreescribe_la_respuesta_nueva() = runTest(timeout = 10.minutes) {
        currentCaseLabel = "T001_03_stale"
        trace("CASE_BEGIN case=$currentCaseLabel")
        val db = KpknDatabase.createInMemory(app)
        val store = ViewModelStore()
        val handle = SavedStateHandle()
        val gate = CandidateScanGate { line -> trace(line) }
        // Adaptador REAL sobre Room con un interruptor sólo-test: cuando `failing` está activo,
        // `save` lanza y el VM actualiza el borrador EN MEMORIA sin recomputar candidatos. Es el
        // camino real de fallo de guardado y es lo que deja el job de A obsoleto SIN cancelarlo.
        val persistence = FailOnDemandPersistence(db)
        val vm = SetupWizardViewModel(
            app,
            handle,
            persistence,
            FixedSettingsEnvironment(Settings()),
            RoomWizardCommits(db),
            // Única inyección de la clase en esta prueba: controla el TIEMPO del materializado,
            // no qué candidatos se publican (esa decisión sigue siendo del VM real).
            SetupWizardMaterializer { draft -> gate.materialize(draft) },
        )
        store.put("t001-03", vm)
        val owner: Job = vm.viewModelScope.coroutineContext[Job]
            ?: error("T001_03: viewModelScope no expone Job")
        trace("LIFECYCLE row-owner-captured row=T001_03")
        // Mismo perfil que la fila T001 salvo por el objetivo: MÚSCULO publica conjuntos de
        // entradas no vacíos y distintos en 3 (A), 6 (X) y 5 (B) días; las PRECONDICIONES de
        // abajo (A≠X, A≠B) lo comprueban en cada ejecución.
        val base = MatrixRow(
            id = "T001-03-B",
            group = "T001",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.INTERMEDIATE,
            categories = allCategories,
            daysPerWeek = 5,
            minutes = 60,
        )
        val rowA = base.copy(id = "T001-03-A", daysPerWeek = 3)
        val rowX = base.copy(id = "T001-03-X", daysPerWeek = 6)
        val rowB = base
        val problems = mutableListOf<String>()
        var publishedA: Set<String> = emptySet()
        var publishedX: Set<String> = emptySet()
        var publishedB: Set<String> = emptySet()
        try {
            vm.initialize(SetupWizardMode.FULL, draftId = "t001-03")
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading }) {
                throw AssertionError("HARNESS: el wizard no salió de isLoading (${stateDump(vm.state.value)})")
            }
            applyFixture(vm, rowA)
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { matchesRequested(it.draft, rowA) }) {
                throw AssertionError(
                    "HARNESS: el borrador A no se asentó (${draftFingerprint(vm.state.value.draft)})",
                )
            }
            if (!awaitMaterializerEntered(gate, T001_GATE_ENTER_BUDGET_MILLIS)) {
                throw AssertionError(
                    "HARNESS: el cálculo de candidatos de A no llegó al materializador " +
                        "(entradas=${gate.enteredCount.get()}; ${stateDump(vm.state.value)})",
                )
            }
            val draftA = vm.state.value.draft
            publishedA = publishedEntryIdSet(draftA)
            if (publishedA.isEmpty()) {
                throw AssertionError(
                    "HARNESS: A debe publicar entradas para que la carrera exista " +
                        "(publicadas=${publishedEntryIds(draftA)})",
                )
            }
            trace(
                "RACE step=A draft=${draftFingerprint(draftA)} publicadas=${publishedA.sorted()} " +
                    "equipo=${declaredEquipment(draftA).sorted()} entradasPuerta=${gate.enteredCount.get()}",
            )
            // ── FASE 2: una respuesta cambia con el guardado de Room FALLANDO ──────────────
            // El borrador pasa a 6 días EN MEMORIA sin que `updateCandidates` se llame: es el
            // camino real de fallo de guardado y deja el job de A obsoleto SIN cancelarse.
            persistence.failing = true
            vm.updateStep(SetupStepId.WEEKDAYS) {
                it.copy(daysPerWeek = 6, selectedWeekdays = (1..6).toSet())
            }
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { matchesRequested(it.draft, rowX) }) {
                throw AssertionError(
                    "HARNESS: el borrador X (6 días) no se asentó en memoria " +
                        "(${draftFingerprint(vm.state.value.draft)}; errores=${vm.state.value.errors})",
                )
            }
            val draftX = vm.state.value.draft
            publishedX = publishedEntryIdSet(draftX)
            if (publishedX.isEmpty() || publishedX == publishedA) {
                throw AssertionError(
                    "PRECONDICIÓN de la carrera: los ID publicados de A (3 días) y de X (6 días) " +
                        "deben ser distintos y no vacíos (A=${publishedA.sorted()} X=${publishedX.sorted()})",
                )
            }
            assertFalse(
                "AC-T001-03: la clave de A ya NO es vigente tras cambiar la respuesta",
                vm.isCurrentCandidateKey(draftA),
            )
            assertTrue(
                "AC-T001-03: la clave de X (la respuesta nueva en memoria) sí es vigente",
                vm.isCurrentCandidateKey(draftX),
            )
            assertFalse(
                "la UI no presenta como carga vigente el cálculo de A sobre X",
                vm.state.value.isCandidateLoading,
            )
            trace(
                "RACE step=X persistencia=fallida draft=${draftFingerprint(draftX)} " +
                    "publicadasA=${publishedA.sorted()} publicadasX=${publishedX.sorted()} " +
                    "entradasPuerta=${gate.enteredCount.get()}",
            )

            // ABA intencional: la huella vuelve a ser A mientras el mismo job A sigue retenido.
            vm.updateStep(SetupStepId.WEEKDAYS) {
                it.copy(daysPerWeek = 3, selectedWeekdays = rowA.weekdays)
            }
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { matchesRequested(it.draft, rowA) }) {
                throw AssertionError(
                    "HARNESS: el borrador volvió a A en memoria " +
                        "(${draftFingerprint(vm.state.value.draft)}; errores=${vm.state.value.errors})",
                )
            }
            assertTrue(
                "la huella A vuelve a coincidir; la generación debe distinguir este nuevo A",
                vm.isCurrentCandidateKey(draftA),
            )
            assertFalse("A no se recalcula mientras falla el guardado", vm.state.value.isCandidateLoading)

            // ── FASE 3: se suelta la puerta; el cálculo A obsoleto no puede publicar sobre A nuevo ──
            gate.releaseGate()
            if (!awaitMaterializerSettled(gate, T001_GATE_SETTLE_BUDGET_MILLIS)) {
                throw AssertionError(
                    "HARNESS: el cálculo obsoleto de A no terminó tras liberar la puerta " +
                        "(entradas=${gate.enteredCount.get()} salidas=${gate.releasedCount.get()})",
                )
            }
            val stale = vm.state.value
            assertFalse(
                "AC-T001-03: el job obsoleto NO queda representado como carga vigente",
                stale.isCandidateLoading,
            )
            assertTrue("la lista de A obsoleto no se publica", stale.availablePlanCandidates.isEmpty())
            assertTrue(
                "AC-T001-03: los rechazos de A no se publican sobre la respuesta nueva " +
                    "(rechazos=${stale.candidateRejections})",
                stale.candidateRejections.isEmpty(),
            )
            assertNull(
                "AC-T001-03: tampoco el motivo de A puede publicarse (${stale.errors["candidates"]})",
                stale.errors["candidates"],
            )
            assertTrue(
                "AC-T001-03: tras volver a A la respuesta en memoria sigue intacta " +
                    "(borrador=${draftFingerprint(stale.draft)})",
                matchesRequested(stale.draft, rowA),
            )
            trace(
                "RACE stale-published=false rechazos=${stale.candidateRejections.size} " +
                    "motivo=${stale.errors["candidates"] ?: "-"}",
            )
            // ── FASE 4: persistencia restaurada; la respuesta NUEVA recalcula y publica ─────
            persistence.failing = false
            vm.updateStep(SetupStepId.WEEKDAYS) {
                it.copy(daysPerWeek = 5, selectedWeekdays = (1..5).toSet())
            }
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) }) {
                throw AssertionError(
                    "HARNESS: el cálculo de B (5 días) no terminó (${stateDump(vm.state.value)})",
                )
            }
            val fresh = vm.state.value
            publishedB = publishedEntryIdSet(fresh.draft)
            if (publishedB.isEmpty() || publishedB == publishedA) {
                throw AssertionError(
                    "PRECONDICIÓN: los ID publicados de A y de B deben ser distintos y no vacíos " +
                        "(A=${publishedA.sorted()} B=${publishedB.sorted()})",
                )
            }
            assertTrue(
                "AC-T001-03: la respuesta nueva sí publica su propio resultado " +
                    "(rechazos=${fresh.candidateRejections.size})",
                fresh.candidateRejections.isNotEmpty(),
            )
            trace(
                "RACE step=B draft=${draftFingerprint(fresh.draft)} publicadas=${publishedB.sorted()} " +
                    "rechazos=${fresh.candidateRejections.mapNotNull { it.planId }.sorted()} " +
                    "motivo=${fresh.errors["candidates"] ?: "-"}",
            )
            trace("RACE guard staleA=false currentA=true y publicacion-de-B=ok")
        } catch (error: Throwable) {
            // Ninguna aserción se convierte en PASS: el fallo queda registrado y se relanza.
            problems += (error.message ?: error.toString()).replace('\n', ' ')
            if (error !is AssertionError) problems += "clase=${error::class.java.name}"
        } finally {
            // Liberación GARANTIZADA: pase lo que pase, la puerta se abre y el materializador
            // se drena con techo finito; nunca se espera sin cota.
            gate.releaseGate()
            val settled = awaitMaterializerSettled(gate, T001_GATE_SETTLE_BUDGET_MILLIS)
            trace(
                "REGRESSION after-release settled=$settled entradas=${gate.enteredCount.get()} " +
                    "salidas=${gate.releasedCount.get()}",
            )
            if (!settled) {
                problems += "HARNESS_BLOCKER: el materializador no drenó tras liberar la puerta " +
                    "(entradas=${gate.enteredCount.get()} salidas=${gate.releasedCount.get()})"
            }
            val outcome = closeRowResources(owner, store, db, "T001_03", ROW_CLEANUP_BUDGET_NANOS)
            if (outcome.clearFailure != null) {
                problems += "HARNESS_BLOCKER: store.clear() de T001_03 falló: " +
                    "${outcome.clearFailure::class.java.name}: ${outcome.clearFailure.message}"
            } else if (!outcome.completed) {
                problems += "HARNESS_BLOCKER: ${outcome.detail}"
            }
            trace(
                "REGRESSION close outcome completed=${outcome.completed} elapsedMs=${outcome.elapsedMillis} " +
                    "detail=${outcome.detail}",
            )
        }
        if (problems.isNotEmpty()) {
            fail("T001_03 no alcanzó la secuencia exigida:\n  - ${problems.joinToString("\n  - ")}")
        }
        val finalState = vm.state.value
        // ── Verificación posterior a la liberación (sólo se alcanza si todo lo anterior pasó) ──
        assertTrue(
            "AC-T001-03: con la puerta abierta B tiene que publicar sus rechazos " +
                "(rechazos=${finalState.candidateRejections.size})",
            finalState.candidateRejections.isNotEmpty(),
        )
        assertEquals(
            "AC-T001-03: los rechazos publicados tienen que ser los ID publicados de B, no los de A " +
                "(A=${publishedA.sorted()} B=${publishedB.sorted()})",
            publishedB,
            finalState.candidateRejections.mapNotNull { it.planId }.toSet(),
        )
        val reason = finalState.errors["candidates"]
        assertTrue(
            "AC-T001-03: el motivo publicado tiene que describir a B (${publishedB.size} planes " +
                "publicados), no a A (${publishedA.size}): $reason",
            reason != null && reason.contains("${publishedB.size} planes publicados"),
        )
        assertTrue(
            "AC-T001-03: las respuestas nuevas de B tienen que seguir intactas " +
                "(borrador=${draftFingerprint(finalState.draft)})",
            matchesRequested(finalState.draft, rowB),
        )
        trace("CASE_END case=$currentCaseLabel outcome=PASS")
    }

    // ═════════════════════════════════════════════════════════════════════════════════════
    // T020 · A3 (curaduría de programas, 2026-10-03) — PARIDAD del ViewModel con las reparaciones
    // ═════════════════════════════════════════════════════════════════════════════════════
    //
    // Las mismas entradas que el contrato de cobertura (`PlanCoverageContractTest`, que prueba el asesor sobre el
    // motor sin ViewModel) pero a través del [SetupWizardViewModel] REAL: `materializeOverride` AUSENTE, planificador,
    // evaluador, caché y asesor de producción. Cada fila comprueba lo que la persona ve y toca:
    //  1. el rechazo del plan PROPIO del objetivo publica la reparación que el asesor probó;
    //  2. el aviso (texto del presentador y botón con la etiqueta de D5) la ofrece;
    //  3. el toque (`performNoticeEffect` → `applyRepairs`) deja el plan propio viable y se puede elegir;
    //  4. el programa que sale es ejecutable y lleva el nombre y el modo de su ficha (C.P6).
    // Ningún oráculo de las filas anteriores cambia: son pruebas ADITIVAS, cada una con su ViewModel y su base.
    //
    //  a. Fuerza + gimnasio completo SIN confirmar soportes: falta confirmar, «Sí, tengo rack y banco».
    //  b. Fuerza en casa: con mancuernas «Cambiar a Fuerza y músculo»; sin resistencia «Cambiar a Músculo».
    //  c. TIME_BUDGET (Músculo en 20 min): «Ajustar a N min» con el mínimo EXACTO del plan propio.
    //  d. Selección caída: el plan elegido deja de caber al bajar el tiempo; el aviso lleva la reparación.
    //  e. Reintento tras CATALOG_NOT_READY (repositorio que falla una vez).
    //  f. C.P6: un plan de autor o un método elegido en el asistente se llama como su ficha y toma el modo de su disciplina.

    /** Fila de T020: perfil válido y explícito, sin cardio salvo que se pida. */
    private fun t020Row(
        id: String,
        goal: SetupGoal,
        experience: SetupExperience,
        categories: Set<EquipmentCategory>,
        days: Int,
        minutes: Int,
    ) = MatrixRow(
        id = "T020-$id",
        group = "T020",
        goal = goal,
        experience = experience,
        categories = categories,
        daysPerWeek = days,
        minutes = minutes,
    )

    /**
     * Un ViewModel REAL (Room en memoria, motor real) con su propio ciclo de vida: se inicializa, se corre [body] y se
     * cierra con el mismo helper de las filas de la matriz (`store.clear()`, finalización real del padre y sólo
     * entonces `db.close()`). Un fallo de cierre se suma al de la fila (o la falla si la fila pasaba).
     */
    private fun <T> TestScope.withT020Vm(
        label: String,
        catalog: ExerciseCatalogRepositoryV2? = null,
        mode: SetupWizardMode = SetupWizardMode.FULL,
        preselectedPlanId: String? = null,
        body: (SetupWizardViewModel) -> T,
    ): T {
        currentCaseLabel = "T020-$label"
        trace("CASE_BEGIN case=$currentCaseLabel")
        val store = ViewModelStore()
        val db = KpknDatabase.createInMemory(app)
        // `materializeOverride` INTENCIONALMENTE ausente (null): motor real de producción.
        val vm = SetupWizardViewModel(
            app,
            SavedStateHandle(),
            RoomWizardPersistence(db),
            FixedSettingsEnvironment(Settings()),
            RoomWizardCommits(db),
            null,
            catalog,
        )
        store.put("t020-$label", vm)
        val owner: Job = vm.viewModelScope.coroutineContext[Job]
            ?: error("T020: viewModelScope de $label no expone Job")
        var failure: Throwable? = null
        try {
            vm.initialize(mode, draftId = "t020-$label", preselectedPlanId = preselectedPlanId)
            if (!awaitUntil(vm, SETTLE_BUDGET_MS) { !it.isLoading }) {
                throw AssertionError("HARNESS: el wizard T020-$label no salió de isLoading (${stateDump(vm.state.value)})")
            }
            // Como en producción (`initializeExerciseDatabase`): las plantillas resuelven sus metadatos por el holder.
            return withProductionCompositionMetadata(enabled = true) { body(vm) }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val close = closeRowResources(owner, store, db, "T020-$label", ROW_CLEANUP_BUDGET_NANOS)
            if (close.clearFailure != null || !close.completed) {
                val blocker = AssertionError("HARNESS_BLOCKER: ${close.detail}")
                if (failure != null) failure.addSuppressed(blocker) else throw blocker
            }
            trace("CASE_END case=$currentCaseLabel failed=${failure != null}")
        }
    }

    /** Estado en reposo que cumple [condition]; si no llega a tiempo, falla con el estado completo. */
    private fun TestScope.requireSettled(
        vm: SetupWizardViewModel,
        what: String,
        condition: (SetupWizardState) -> Boolean,
    ): SetupWizardState {
        if (!awaitUntil(vm, SETTLE_BUDGET_MS) { isIdle(it) && condition(it) }) {
            throw AssertionError("T020: no se alcanzó «$what» | ${stateDump(vm.state.value)}")
        }
        return vm.state.value
    }

    /**
     * El aviso que la persona ve para el plan propio, EXACTAMENTE como lo decide la pantalla del paso PLAN: el de
     * encima de la lista cuando hay otros planes viables y el de «ningún plan viable» cuando no hay ninguno.
     */
    private fun t020Notice(state: SetupWizardState): RejectionNotice = when (val gate = candidateListGate(state)) {
        is CandidateListGate.Candidates -> checkNotNull(gate.ownPlanNotice) { "T020: la lista no explica el plan propio" }
        CandidateListGate.NoneViable -> incompatibilityNotice(state)
        else -> throw AssertionError("T020: la puerta de la lista no explica ningún rechazo: $gate")
    }

    /** Ningún texto del aviso lleva ids, tokens de material, códigos cerrados ni texto crudo del motor. */
    private fun assertT020PlainLanguage(notice: RejectionNotice) {
        listOf(notice.text, notice.primary?.label.orEmpty(), notice.secondary?.label.orEmpty()).forEach { text ->
            assertFalse("«$text» lleva un id con prefijo", Regex("""\b(native|template|protocol|original|adapted):[a-z0-9]""").containsMatchIn(text))
            assertFalse("«$text» lleva un guion bajo", text.contains('_'))
            assertFalse(
                "«$text» lleva un código cerrado",
                Regex("APPARATUS|TIME_BUDGET|PROFILE_MISMATCH|INTERNAL_|CATALOG_NOT").containsMatchIn(text),
            )
            assertFalse("«$text» lleva un token de material", Regex("""\b(barbell|dumbbells|general_gym|bodyweight)\b""").containsMatchIn(text))
        }
        assertTrue("como mucho dos botones", notice.buttons.size in 1..2)
    }

    /**
     * Toca el botón principal del aviso del plan propio, espera a que el plan propio sea viable, lo elige y devuelve su
     * programa ya preparado (ejecutable, con el contrato canónico de producción).
     */
    private fun TestScope.tapTheOwnRepairAndOpenTheOwnPlan(
        vm: SetupWizardViewModel,
        ownId: String,
        before: SetupWizardState,
    ): Program {
        val notice = t020Notice(before)
        performNoticeEffect(checkNotNull(notice.primary) { "el aviso no trae botón principal" }.effect, vm)
        requireSettled(vm, "plan propio $ownId viable tras la reparación") { state ->
            state.availablePlanCandidates.any { it.id == ownId }
        }
        vm.selectPlan(ownId)
        if (!awaitPreviewFor(vm, ownId, PREVIEW_BUDGET_MS)) {
            throw AssertionError("T020: la vista previa de $ownId no se asentó | ${stateDump(vm.state.value)}")
        }
        val state = vm.state.value
        val program = state.programPreview
            ?: throw AssertionError("T020: $ownId sin vista previa (previewError=${state.previewError})")
        val issues = ProgramExecutionContract.validate(program)
        assertTrue("T020: $ownId no es ejecutable: ${issues.joinToString("; ") { it.message }}", issues.isEmpty())
        // C.P6: el programa se llama como su ficha y toma el modo de su disciplina.
        val entry = checkNotNull(PersonalizedPlanCatalog.find(ownId))
        assertEquals("T020: nombre del programa de $ownId", entry.displayName, program.name)
        assertEquals("T020: modo del programa de $ownId", programModeFor(entry), program.mode)
        return program
    }

    @Test
    fun T020_a_fuerza_gimnasio_completo_sin_confirmar_soportes_ofrece_el_toque_y_el_plan_propio_llega_a_programa() =
        runTest(timeout = 10.minutes) {
            withT020Vm("a-fuerza-gimnasio-sin-confirmar") { vm ->
                val own = NativeProfileKind.STRENGTH.entryId
                val row = t020Row("a", SetupGoal.STRENGTH, SetupExperience.INTERMEDIATE, allCategories, days = 3, minutes = 60)
                applyFixture(vm, row)
                val before = requireSettled(vm, "plan propio de Fuerza rechazado") { state ->
                    matchesRequested(state.draft, row) && state.candidateRejections.any { it.planId == own }
                }

                val rejection = before.candidateRejections.single { it.planId == own }
                assertEquals(
                    "gimnasio sin confirmar: FALTA CONFIRMAR, no «declaraste ausente»",
                    PlanRejectionReason.APPARATUS_UNKNOWN,
                    rejection.reasonCode,
                )
                val repair = rejection.repairs.firstOrNull()
                assertTrue("el rechazo trae la confirmación del material: ${rejection.repairs}", repair is PlanRepair.ConfirmApparatus)
                assertEquals(listOf("squat_rack", "bench_flat"), (repair as PlanRepair.ConfirmApparatus).keys)
                val notice = t020Notice(before)
                assertTrue(
                    "etiqueta de D5 en el botón principal: ${notice.primary?.label}",
                    notice.primary?.label?.startsWith("Sí, tengo rack y banco") == true,
                )
                assertT020PlainLanguage(notice)

                val program = tapTheOwnRepairAndOpenTheOwnPlan(vm, own, before)

                val availability = checkNotNull(vm.state.value.draft.trainingOptions.availability)
                assertEquals(ApparatusPresence.PRESENT, availability.supports["squat_rack"])
                assertEquals(ApparatusPresence.PRESENT, availability.supports["bench_flat"])
                assertEquals(ProgramMode.POWERLIFTING, program.mode)
            }
        }

    @Test
    fun T020_b1_fuerza_en_casa_con_mancuernas_ofrece_cambiar_a_fuerza_y_musculo() = runTest(timeout = 10.minutes) {
        withT020Vm("b1-fuerza-casa-mancuernas") { vm ->
            val own = NativeProfileKind.STRENGTH.entryId
            val destination = NativeProfileKind.POWERBUILDING.entryId
            val categories = setOf(EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT)
            val row = t020Row("b1", SetupGoal.STRENGTH, SetupExperience.INTERMEDIATE, categories, days = 3, minutes = 60)
            applyFixture(vm, row)
            val before = requireSettled(vm, "plan propio de Fuerza rechazado") { state ->
                matchesRequested(state.draft, row) && state.candidateRejections.any { it.planId == own }
            }

            val rejection = before.candidateRejections.single { it.planId == own }
            assertEquals("sin barra no hay Fuerza", PlanRejectionReason.APPARATUS_ABSENT, rejection.reasonCode)
            val repair = rejection.repairs.firstOrNull()
            assertTrue("destino honesto: ${rejection.repairs}", repair is PlanRepair.SwitchGoal)
            assertEquals(PlanGoalProfile.STRENGTH_MUSCLE, (repair as PlanRepair.SwitchGoal).goal)
            val notice = t020Notice(before)
            assertTrue(
                "etiqueta de D5 en el botón principal: ${notice.primary?.label}",
                notice.primary?.label?.startsWith("Cambiar a Fuerza y músculo") == true,
            )
            assertT020PlainLanguage(notice)

            val program = tapTheOwnRepairAndOpenTheOwnPlan(vm, destination, before)

            assertEquals(SetupGoal.STRENGTH_MUSCLE, vm.state.value.draft.goal)
            assertEquals(ProgramMode.POWERBUILDING, program.mode)
        }
    }

    @Test
    fun T020_b2_fuerza_sin_resistencia_externa_ofrece_cambiar_a_musculo() = runTest(timeout = 10.minutes) {
        withT020Vm("b2-fuerza-sin-material") { vm ->
            val own = NativeProfileKind.STRENGTH.entryId
            val destination = NativeProfileKind.MUSCLE.entryId
            // Solo peso corporal: disponibilidad vacía EXPLÍCITA (no `null`).
            val row = t020Row("b2", SetupGoal.STRENGTH, SetupExperience.INTERMEDIATE, emptySet(), days = 3, minutes = 60)
            applyFixture(vm, row)
            val before = requireSettled(vm, "plan propio de Fuerza rechazado") { state ->
                matchesRequested(state.draft, row) && state.candidateRejections.any { it.planId == own }
            }

            val rejection = before.candidateRejections.single { it.planId == own }
            assertEquals("sin barra no hay Fuerza", PlanRejectionReason.APPARATUS_ABSENT, rejection.reasonCode)
            val repair = rejection.repairs.firstOrNull()
            assertTrue("destino honesto: ${rejection.repairs}", repair is PlanRepair.SwitchGoal)
            assertEquals(PlanGoalProfile.MUSCLE, (repair as PlanRepair.SwitchGoal).goal)
            val notice = t020Notice(before)
            assertTrue(
                "etiqueta de D5 en el botón principal: ${notice.primary?.label}",
                notice.primary?.label?.startsWith("Cambiar a Músculo") == true,
            )
            assertT020PlainLanguage(notice)

            val program = tapTheOwnRepairAndOpenTheOwnPlan(vm, destination, before)

            assertEquals(SetupGoal.MUSCLE, vm.state.value.draft.goal)
            assertEquals(ProgramMode.HYPERTROPHY, program.mode)
        }
    }

    @Test
    fun T020_c_time_budget_ofrece_ajustar_al_minimo_exacto_del_plan_propio() = runTest(timeout = 10.minutes) {
        withT020Vm("c-musculo-20-min") { vm ->
            val own = NativeProfileKind.MUSCLE.entryId
            // Las mismas entradas que la fila F-m20: a 20 min ningún candidato cabe y el propio pide más que los demás.
            val row = t020Row("c", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 20)
            applyFixture(vm, row)
            val before = requireSettled(vm, "plan propio de Músculo rechazado por tiempo") { state ->
                matchesRequested(state.draft, row) &&
                    state.candidateRejections.any { it.planId == own && it.reasonCode == PlanRejectionReason.TIME_BUDGET }
            }

            val rejection = before.candidateRejections.single { it.planId == own }
            val required = checkNotNull(rejection.requiredMinutes) { "el rechazo de tiempo informa el mínimo exacto" }
            assertTrue("el mínimo supera lo elegido y cabe en el wizard: $required", required in 21..100)
            assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(required)), rejection.repairs)
            // El aviso habla del plan PROPIO (no del que pida menos minutos) y da el botón de ajuste.
            val primary = PlanRejectionPresenter.primary(before.candidateRejections.map { it.toRejectionView() }, own)
            assertEquals(own, primary?.planId)
            val notice = t020Notice(before)
            assertTrue(notice.text, notice.text.contains("necesita $required min por sesión y elegiste 20"))
            assertEquals("Ajustar a $required min", notice.primary?.label)
            assertT020PlainLanguage(notice)

            val program = tapTheOwnRepairAndOpenTheOwnPlan(vm, own, before)

            val after = vm.state.value
            assertEquals(required, after.draft.minutesPerSession)
            assertTrue("la persona tocó el paso de tiempo", SetupStepId.SESSION_TIME in after.draft.declaredSteps)
            val longest = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }
                .maxOf { SessionDurationEstimator.estimate(it).totalMinutes }
            assertTrue("la sesión más larga ($longest min) cabe en los $required min", longest <= required)
        }
    }

    @Test
    fun T020_d_seleccion_caida_al_bajar_el_tiempo_lleva_la_reparacion_en_su_aviso() = runTest(timeout = 10.minutes) {
        withT020Vm("d-seleccion-caida") { vm ->
            val own = NativeProfileKind.MUSCLE.entryId
            val row = t020Row("d", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 60)
            applyFixture(vm, row)
            requireSettled(vm, "plan propio viable con 60 min") { state ->
                matchesRequested(state.draft, row) && state.availablePlanCandidates.any { it.id == own }
            }
            vm.selectPlan(own)
            if (!awaitPreviewFor(vm, own, PREVIEW_BUDGET_MS)) {
                throw AssertionError("T020: la vista previa de $own no se asentó | ${stateDump(vm.state.value)}")
            }
            assertNotNull("el plan elegido prepara su programa", vm.state.value.programPreview)

            // Entre el mínimo de los planes más cortos (21 min) y el del propio (28): el propio ya no cabe y los demás sí,
            // así que la lista sigue ahí y el plan elegido cae con su aviso (si ninguno cupiera no habría lista).
            vm.updateStep(SetupStepId.SESSION_TIME) { it.copy(minutesPerSession = 24) }
            val dropped = requireSettled(vm, "selección caída") { it.droppedSelection != null }

            val fallen = checkNotNull(dropped.droppedSelection)
            assertEquals(own, fallen.planId)
            val why = checkNotNull(fallen.rejection) { "el plan se evaluó: tiene rechazo" }
            assertEquals(PlanRejectionReason.TIME_BUDGET, why.reasonCode)
            val required = checkNotNull(why.requiredMinutes)
            assertEquals(listOf<PlanRepair>(PlanRepair.SetMinutes(required)), why.repairs)
            val notice = droppedSelectionNotice(fallen, dropped.draft)
            assertTrue(notice.text, notice.text.startsWith(DROPPED_SELECTION_LEAD))
            assertTrue(notice.text, notice.text.contains("necesita $required min por sesión y elegiste 24"))
            assertEquals("Ajustar a $required min", notice.primary?.label)
            assertT020PlainLanguage(notice)

            performNoticeEffect(checkNotNull(notice.primary).effect, vm)
            val fixed = requireSettled(vm, "plan propio viable otra vez") { state ->
                state.draft.minutesPerSession == required && state.availablePlanCandidates.any { it.id == own }
            }
            assertNull("la búsqueda nueva retira el aviso de la selección caída", fixed.droppedSelection)
        }
    }

    @Test
    fun T020_e_reintento_tras_un_catalogo_que_no_cargo_vuelve_a_cargarlo_y_publica_la_lista() =
        runTest(timeout = 10.minutes) {
            val flaky = FlakyCatalogRepository(ApprovedAssetExerciseCatalogRepositoryV2(app), failures = 1)
            withT020Vm("e-reintento-catalogo", catalog = flaky) { vm ->
                val own = NativeProfileKind.MUSCLE.entryId
                val row = t020Row("e", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 60)
                applyFixture(vm, row)
                val failed = requireSettled(vm, "fallo del catálogo publicado") { it.errors["candidates"] != null }

                assertEquals(CATALOG_UNAVAILABLE_MESSAGE, failed.errors["candidates"])
                val why = failed.candidateRejections.single()
                assertNull("el rechazo es global: no hay candidato que evaluar", why.planId)
                assertEquals(PlanRejectionReason.CATALOG_NOT_READY, why.reasonCode)
                assertEquals(CandidateListGate.SearchFailed(CATALOG_UNAVAILABLE_MESSAGE), candidateListGate(failed))
                // C.P11: el presentador lo dice en llano y su botón es «Reintentar».
                val presented = PlanRejectionPresenter.present(why.toRejectionView(), presentationContextOf(failed.draft))
                assertEquals(PlanRejectionPresenter.CATALOG_TEXT, presented.text)
                assertEquals(RejectionAction.Retry, presented.primary)
                assertTrue("las respuestas siguen intactas", matchesRequested(failed.draft, row))

                performRejectionAction(checkNotNull(presented.primary), vm)
                val recovered = requireSettled(vm, "catálogo recuperado y lista publicada") { state ->
                    state.availablePlanCandidates.any { it.id == own }
                }

                assertNull(recovered.errors["candidates"])
                assertTrue(recovered.candidateRejections.none { it.planId == null })
                assertTrue(candidateListGate(recovered) is CandidateListGate.Candidates)
                assertTrue("las respuestas siguen intactas", matchesRequested(recovered.draft, row))
            }
        }

    @Test
    fun T020_f_un_plan_de_autor_elegido_en_el_asistente_se_llama_como_su_ficha_y_toma_el_modo_de_su_disciplina() =
        runTest(timeout = 10.minutes) {
            withT020Vm("f-nombre-y-modo-de-la-ficha") { vm ->
                // El escenario de PHUL de `SetupWizardAuthoredPlansTest`: Fuerza y músculo, 4 días, 100 min y gimnasio completo
                // con todos los aparatos confirmados; PHUL original y adaptado salen viables con el motor real.
                val gym = AuthoredPlanFixtures.fullGym.availability
                val row = t020Row("f", SetupGoal.STRENGTH_MUSCLE, SetupExperience.INTERMEDIATE, allCategories, days = 4, minutes = 100)
                applyFixture(vm, row)
                vm.updateStep(SetupStepId.AVAILABILITY) { it.copy(trainingOptions = it.trainingOptions.copy(availability = gym)) }
                val original = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
                val adapted = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID
                val settled = requireSettled(vm, "PHUL original y adaptado viables con el gimnasio completo") { state ->
                    state.draft.trainingOptions.availability == gym &&
                        state.draft.minutesPerSession == row.minutes &&
                        state.availablePlanCandidates.any { it.id == original } &&
                        state.availablePlanCandidates.any { it.id == adapted }
                }

                // Antes el programa salía como «Plan de {nombre de la persona}» con el modo de hipertrofia; ahora lleva el
                // nombre de su ficha y el modo de su disciplina (PHUL: Fuerza y músculo, powerbuilding). Además del plan de
                // autor se prueba el primer método sin receta de autor y la primera plantilla que salgan viables, si los hay.
                val authored = listOf(original, adapted)
                val others = settled.availablePlanCandidates
                    .mapNotNull { candidate -> PersonalizedPlanCatalog.find(candidate.id) }
                    .filter { entry -> entry.authoredSource == null && entry.source != CatalogSource.NATIVE }
                    .distinctBy { entry -> entry.source }
                    .map { entry -> entry.id }
                (authored + others).forEach { planId ->
                    vm.selectPlan(planId)
                    if (!awaitPreviewFor(vm, planId, PREVIEW_BUDGET_MS)) {
                        throw AssertionError("T020: la vista previa de $planId no se asentó | ${stateDump(vm.state.value)}")
                    }
                    val state = vm.state.value
                    val program = state.programPreview
                        ?: throw AssertionError("T020: $planId sin vista previa (previewError=${state.previewError})")
                    val entry = checkNotNull(PersonalizedPlanCatalog.find(planId))
                    assertEquals("T020: nombre del programa de $planId", entry.displayName, program.name)
                    assertFalse("T020: «${program.name}» no debe ser el nombre de la persona", program.name.startsWith("Plan de "))
                    if (planId in authored) {
                        assertEquals("T020: modo del programa de $planId", ProgramMode.POWERBUILDING, program.mode)
                    } else if (entry.source == CatalogSource.PROTOCOL) {
                        // Un método sin receta de autor ya no sale como powerlifting ni como hipertrofia por defecto.
                        assertEquals("T020: modo del programa de $planId", programModeFor(entry), program.mode)
                    }
                }
            }
        }

    // ═════════════════════════════════════════════════════════════════════════════════════
    // T021 · A.E2 (curaduría de programas, 2026-10-04) — el reparto elegido y el plan propio, con el ViewModel REAL
    // ═════════════════════════════════════════════════════════════════════════════════════
    //
    // Mismo arnés que T020 (motor real, planificador, evaluador, caché y asesor de producción). Una sola fila con las
    // cuatro cosas que ve y toca la persona (D6):
    //  1. la lista del paso SPLIT no ofrece un reparto de powerlifting en Músculo...;
    //  2. ...pero si el borrador ya lo trae (p. ej. el objetivo se cambió a mano), el plan propio lo rechaza como SPLIT, el
    //     aviso lo explica en llano y ofrece «Quitar el reparto» (la reparación de un toque) y «Cambiar reparto»;
    //  3. el toque deja el plan propio viable: el programa sale sin reparto anotado y con sus días «Día N»;
    //  4. el reparto equivalente de su calendario sí se acepta: el programa lo anota, nombra sus días como él, la revisión
    //     dice su nombre y los días de entrenamiento siguen siendo los que eligió la persona.

    /** Nombre de cada sesión de la primera semana del programa, en orden de día. */
    private fun firstWeekDayNames(program: Program): List<String> = program.macrocycles
        .flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        .first().sessions.map { it.name }

    @Test
    fun T021_a_un_reparto_de_powerlifting_en_musculo_se_rechaza_con_un_toque_para_quitarlo_y_el_reparto_equivalente_se_acepta() =
        runTest(timeout = 10.minutes) {
            withT020Vm("t021-a-reparto") { vm ->
                val own = NativeProfileKind.MUSCLE.entryId
                // Las mismas entradas que T020_d (Músculo, principiante, 3 días, 60 min, gimnasio sin confirmar): el propio cabe.
                val row = t020Row("t021a", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 60)
                applyFixture(vm, row)
                val start = requireSettled(vm, "plan propio de Músculo viable sin reparto") { state ->
                    matchesRequested(state.draft, row) && state.availablePlanCandidates.any { it.id == own }
                }

                // 1. D6: la lista del paso SPLIT no ofrece los repartos de powerlifting fuera de Fuerza.
                val startDay = row.weekdays.minOrNull() ?: 1
                val offered = compatibleSplitTemplates(row.daysPerWeek, startDay, start.draft.goal).map { it.id }
                assertTrue("Músculo ofrece su reparto equivalente: $offered", "fullbody_x3" in offered)
                assertFalse("Músculo no ofrece repartos de powerlifting: $offered", "texas_method" in offered || "pl_sbd_x3" in offered)

                // 2. Un borrador que ya trae uno (p. ej. cambiado de objetivo a mano) no se acepta en silencio.
                vm.updateStep(SetupStepId.SPLIT) { it.copy(selectedSplitId = "texas_method") }
                val before = requireSettled(vm, "plan propio rechazado por el reparto") { state ->
                    state.draft.selectedSplitId == "texas_method" &&
                        state.candidateRejections.any { it.planId == own && it.reasonCode == PlanRejectionReason.SPLIT }
                }
                val rejection = before.candidateRejections.single { it.planId == own }
                assertEquals(SetupCandidateRejectionStage.FREQUENCY, rejection.stage)
                assertEquals(listOf<PlanRepair>(PlanRepair.ClearSplit), rejection.repairs)
                val notice = t020Notice(before)
                assertTrue(notice.text, notice.text.contains("El reparto elegido no encaja con este plan."))
                assertEquals("Quitar el reparto", notice.primary?.label)
                assertEquals("Cambiar reparto", notice.secondary?.label)
                assertT020PlainLanguage(notice)

                // 3. El toque quita el reparto y el plan propio sale como siempre.
                val usual = tapTheOwnRepairAndOpenTheOwnPlan(vm, own, before)
                assertNull("quitar el reparto lo retira del borrador", vm.state.value.draft.selectedSplitId)
                assertNull("sin reparto el programa no anota ninguno", usual.selectedSplitId)
                assertEquals(listOf("Día 1", "Día 2", "Día 3"), firstWeekDayNames(usual))

                // 4. Su reparto equivalente sí se acepta: el programa lo anota y nombra sus días como él.
                vm.updateStep(SetupStepId.SPLIT) { it.copy(selectedSplitId = "fullbody_x3") }
                val accepted = requireSettled(vm, "vista previa con el reparto aceptado") { state ->
                    state.draft.selectedSplitId == "fullbody_x3" && state.programPreview?.selectedSplitId == "fullbody_x3"
                }
                val program = checkNotNull(accepted.programPreview)
                assertEquals(listOf("Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C"), firstWeekDayNames(program))
                assertEquals("la revisión dice el nombre del reparto, no su id", "Cuerpo completo, 3 días", draftSplitLabel(accepted))
                assertEquals("los días de entrenamiento son los que eligió la persona", row.weekdays, program.schedulePlan?.trainingDays)
                val issues = ProgramExecutionContract.validate(program)
                assertTrue("el programa con reparto es ejecutable: ${issues.joinToString("; ") { it.message }}", issues.isEmpty())
                assertTrue(
                    "el plan propio sigue viable con su reparto equivalente",
                    accepted.availablePlanCandidates.any { it.id == own },
                )
            }
        }

    // ═════════════════════════════════════════════════════════════════════════════════════
    // T022 · H2 (curaduría de programas, 2026-10-04) — el plan de la biblioteca que el planificador excluye, con el motor REAL
    // ═════════════════════════════════════════════════════════════════════════════════════
    //
    // Mismo arnés que T020 (motor real, planificador, evaluador, caché y asesor de producción), con el asistente de SOLO
    // entrenamiento que abre la biblioteca cuando el alta ya está completa (`TRAINING_ONLY`) y el plan preseleccionado:
    //  a. Fuerza → Músculo con el plan propio de Fuerza preseleccionado: el aviso explica el perfil, la intención se
    //     conserva, y al volver a Fuerza (gimnasio sin confirmar) el rechazo de material lleva su reparación de un toque y el
    //     plan, que sigue elegido, llega a programa sin volver a elegirlo.
    //  b. Un método de 4 días preseleccionado con 3 días elegidos: «Este plan usa 4 días distintos; elegiste 3 días.» y el
    //     botón «Cambiar días» (antes, el genérico «ya no está entre los planes que corresponden a tus respuestas»).

    @Test
    fun T022_a_fuerza_preseleccionada_y_el_objetivo_pasa_a_musculo_explica_el_perfil_y_la_intencion_llega_a_programa_al_volver() =
        runTest(timeout = 10.minutes) {
            val own = NativeProfileKind.STRENGTH.entryId
            withT020Vm("t022-a-fuerza-a-musculo", mode = SetupWizardMode.TRAINING_ONLY, preselectedPlanId = own) { vm ->
                val seeded = vm.state.value.draft
                assertEquals("el plan de la biblioteca, como intención", own, seeded.selectedCatalogId)
                assertEquals("el objetivo del plan, prefijado y sin confirmar", SetupGoal.STRENGTH, seeded.goal)
                assertFalse(SetupStepId.GOAL in seeded.stepProgress.answers)

                // 1. La persona pasa a Músculo: el plan propio de Fuerza deja de ser candidato del objetivo.
                applyFixture(vm, t020Row("t022a-musculo", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 60))
                val dropped = requireSettled(vm, "plan de Fuerza caído por el objetivo") { state ->
                    state.draft.goal == SetupGoal.MUSCLE && state.droppedSelection?.planId == own
                }
                assertEquals(
                    "el planificador ni lo evaluó: el motivo se sintetiza",
                    PlanRejectionReason.PROFILE_MISMATCH,
                    checkNotNull(dropped.droppedSelection?.rejection).reasonCode,
                )
                assertEquals("la intención se conserva", own, dropped.draft.selectedCatalogId)
                val profileNotice = droppedSelectionNotice(checkNotNull(dropped.droppedSelection), dropped.draft)
                assertTrue(profileNotice.text, profileNotice.text.startsWith(DROPPED_SELECTION_LEAD))
                assertTrue(profileNotice.text, profileNotice.text.contains("músculo"))
                assertEquals("Cambiar objetivo", profileNotice.primary?.label)
                assertEquals("Ver alternativas", profileNotice.secondary?.label)
                assertT020PlainLanguage(profileNotice)

                // 2. Vuelve a Fuerza con el gimnasio sin confirmar: ahora el plan SÍ se evalúa y falta rack y banco.
                applyFixture(vm, t020Row("t022a-fuerza", SetupGoal.STRENGTH, SetupExperience.INTERMEDIATE, allCategories, days = 3, minutes = 60))
                val unknown = requireSettled(vm, "plan de Fuerza rechazado por material sin confirmar, con su reparación") { state ->
                    state.draft.goal == SetupGoal.STRENGTH &&
                        state.candidateRejections.any { it.planId == own && it.repairs.isNotEmpty() }
                }
                assertEquals(own, unknown.draft.selectedCatalogId)
                val notice = unknown.droppedSelection?.let { droppedSelectionNotice(it, unknown.draft) } ?: t020Notice(unknown)
                assertTrue(
                    "etiqueta de D5 en el botón principal: ${notice.primary?.label}",
                    notice.primary?.label?.startsWith("Sí, tengo rack y banco") == true,
                )
                assertT020PlainLanguage(notice)

                // 3. Un toque: el plan sigue elegido (nadie lo eligió otra vez) y llega a un programa ejecutable.
                performNoticeEffect(checkNotNull(notice.primary).effect, vm)
                val ready = requireSettled(vm, "plan de la biblioteca viable, elegido y preparado") { state ->
                    state.droppedSelection == null && state.draft.selectedCatalogId == own &&
                        state.programPreview != null && state.availablePlanCandidates.any { it.id == own }
                }
                val program = checkNotNull(ready.programPreview)
                val issues = ProgramExecutionContract.validate(program)
                assertTrue("T022: $own no es ejecutable: ${issues.joinToString("; ") { it.message }}", issues.isEmpty())
                assertEquals(ProgramMode.POWERLIFTING, program.mode)
            }
        }

    @Test
    fun T022_b_un_metodo_de_cuatro_dias_con_tres_dias_elegidos_dice_cuantos_dias_usa_y_ofrece_cambiar_los_dias() =
        runTest(timeout = 10.minutes) {
            val method = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID
            withT020Vm("t022-b-metodo-de-cuatro-dias", mode = SetupWizardMode.TRAINING_ONLY, preselectedPlanId = method) { vm ->
                val seeded = vm.state.value.draft
                assertEquals(method, seeded.selectedCatalogId)
                assertEquals("los días del método, sin confirmar", 4, seeded.daysPerWeek)
                assertFalse(SetupStepId.WEEKDAYS in seeded.stepProgress.answers)

                // La persona elige Músculo y 3 días: el planificador ya no ofrece un método de 4 días.
                applyFixture(vm, t020Row("t022b", SetupGoal.MUSCLE, SetupExperience.NEW, allCategories, days = 3, minutes = 60))
                val dropped = requireSettled(vm, "método de 4 días caído por los 3 días elegidos") { state ->
                    state.draft.daysPerWeek == 3 && state.droppedSelection?.planId == method
                }

                assertEquals(
                    PlanRejectionReason.FREQUENCY,
                    checkNotNull(dropped.droppedSelection?.rejection).reasonCode,
                )
                assertEquals("la intención se conserva", method, dropped.draft.selectedCatalogId)
                val notice = droppedSelectionNotice(checkNotNull(dropped.droppedSelection), dropped.draft)
                assertEquals("$DROPPED_SELECTION_LEAD Este plan usa 4 días distintos; elegiste 3 días.", notice.text)
                assertEquals("Cambiar días", notice.primary?.label)
                assertEquals(NoticeEffect.Act(RejectionAction.ChangeDays), notice.primary?.effect)
                assertNull(notice.secondary)
                assertT020PlainLanguage(notice)

                // El botón lleva al paso de los días.
                performNoticeEffect(checkNotNull(notice.primary).effect, vm)
                requireSettled(vm, "cursor en el paso de los días") { it.currentStep == SetupStepId.WEEKDAYS }
            }
        }

    // ─── Filas de la matriz ────────────────────────────────────────────────────

    private val allCategories: Set<EquipmentCategory> = EquipmentCategory.entries.toSet()

    /** Días del grupo A: la misma lista para los 12 positivos, los 4 negativos de 20 min y las 4 filas de 21 min. */
    private val groupADays = listOf(1, 3, 5, 6)

    /** A12 positivos: 4 frecuencias × {30,60,100} min. Los 20 min son negativos (ver [rowsANegative20]). */
    private fun rowsA(): List<MatrixRow> = groupADays.flatMap { days ->
        listOf(30, 60, 100).map { minutes ->
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

    /**
     * Pieza de un día de Músculo corporal en el cálculo independiente del piso de tiempo.
     * [intent] es H (hipertrofia, 2 series en principiante), I (aislamiento, 1) o C (core, 1);
     * [approachFamily] es la familia de patrón compuesto que recibe UNA aproximación técnica en su
     * primer slot (null = aislamiento o sin patrón compuesto, sin aproximación).
     */
    private data class FloorSlot(val intent: Char, val approachFamily: String? = null)

    /**
     * Arquetipos de día de Músculo sin soporte de tirón de r2 §11.3 (BFA, BFB, BU, BL) más BL_MRV,
     * la desviación DEV-r2-01 de 5 y 6 días (docs/WIZARD_PLAN_DEVIATIONS.md): flexión en lugar de
     * puente en dos de los tres BL. S/U = sentadilla y zancada (familia SQUAT), B = flexión
     * (PUSH), SM = superman (extensión: familia HINGE); el puente (BG), el glúteo aislado (G) y el
     * core (C) no llevan aproximación. Escritos aquí desde el plan, sin leer las tablas del motor.
     */
    private val floorArchetypes: Map<String, List<FloorSlot>> = mapOf(
        "BFA" to listOf(
            FloorSlot('H', "SQUAT"), FloorSlot('H', "PUSH"), FloorSlot('H'), FloorSlot('I', "HINGE"), FloorSlot('C'),
        ),
        "BFB" to listOf(
            FloorSlot('H', "SQUAT"), FloorSlot('H', "PUSH"), FloorSlot('H'), FloorSlot('I'), FloorSlot('C'),
        ),
        "BU" to listOf(FloorSlot('H', "PUSH"), FloorSlot('I', "HINGE"), FloorSlot('C')),
        "BL" to listOf(FloorSlot('H', "SQUAT"), FloorSlot('H'), FloorSlot('H', "SQUAT"), FloorSlot('I')),
        "BL_MRV" to listOf(FloorSlot('H', "SQUAT"), FloorSlot('H', "PUSH"), FloorSlot('H', "SQUAT"), FloorSlot('I')),
    )

    /** Calendario por frecuencia de r2 §11.3 (3 días: BFA/BFB/BFA; 5 y 6 con BL_MRV por DEV-r2-01). */
    private val floorCalendars: Map<Int, List<String>> = mapOf(
        1 to listOf("BFA"),
        3 to listOf("BFA", "BFB", "BFA"),
        5 to listOf("BL", "BU", "BL_MRV", "BU", "BL_MRV"),
        6 to listOf("BU", "BL", "BU", "BL_MRV", "BU", "BL_MRV"),
    )

    /**
     * Mínimo real, en minutos, de la sesión más larga de Músculo corporal principiante (solo
     * peso corporal, material declarado vacío) con [days] días, tras el fitter de r2 §12.3. Es un
     * cálculo INDEPENDIENTE y POR FILA: parte del calendario y los arquetipos del plan
     * ([floorCalendars], [floorArchetypes]) y de las constantes publicadas del estimador común
     * (§12.2) escritas aquí; no lee el motor ni sus tablas.
     *
     * Por día: se retiran los accesorios opcionales —I primero, C después— mientras el día
     * conserve el suelo de dosis diaria (≥2 configuraciones y ≥4 series; H no se retira ni se
     * reduce en principiante: ya está en 2 series), y el día mínimo cuesta:
     *  - 180 s de calentamiento general (3 min, §12.2).
     *  - H corporal: 60 s de preparación + 2 series de 60 s (el estimador mide el extremo alto del
     *    rango 8–15: max(4 s × 15 reps, 45 s)) + 120 s de descanso entre ambas (T3 compuesto) = 300 s.
     *    Un I conservado: 60 + 60 = 120 s (1 serie, rango 10–15). Un C conservado: 60 + max(4 × 12, 45)
     *    = 108 s (1 serie, rango 8–12).
     *  - 90 s (30 s de ejecución + 60 s de descanso) por aproximación técnica: una por familia de
     *    patrón compuesto presente (sentadilla, empuje, bisagra); el puente no lleva.
     * Resultado por arquetipo mínimo: BFA y BFB = 3 H + 2 aproximaciones = 180 + 900 + 180 = 1260 s
     * = 21,0 min; BL_MRV = 3 H + 2 aproximaciones = 1260 s = 21,0 min; BL = 3 H + 1 aproximación
     * (U comparte la familia de S) = 1170 s = 19,5 → 20 min; BU conserva SM y C porque quitar
     * cualquiera deja 3 series < 4 = 180 + 300 + 120 + 108 + 2 · 90 = 888 s = 14,8 → 15 min. El
     * piso de la fila es el del día más largo: 21 min en 1, 3, 5 y 6 días. Esas cifras (20, 15 y
     * 21 por día en 5 días; 15, 20, 15, 21… en 6) son además las que mide el estimador sobre el
     * programa que llega a la vista previa, y los 41 min de Atleta corporal 1 día
     * (`NativeProfileRecipeAndFitterTest.cardio_defaults_use_session_time_boundaries_44_and_45`) se
     * obtienen con esta misma aritmética.
     */
    private fun independentBodyweightMuscleFloorMinutes(days: Int): Int {
        val generalWarmupSeconds = 3 * 60
        val setupSecondsPerExercise = 60
        val approachSeconds = 30 + 60
        val minimumConfigurations = 2
        val minimumWorkingSets = 4
        fun setsOf(slot: FloorSlot): Int = if (slot.intent == 'H') 2 else 1
        fun secondsOf(slot: FloorSlot): Int = when (slot.intent) {
            'H' -> setupSecondsPerExercise + 2 * maxOf(4 * 15, 45) + (2 - 1) * 120
            'I' -> setupSecondsPerExercise + 1 * maxOf(4 * 15, 45)
            else -> setupSecondsPerExercise + 1 * maxOf(4 * 12, 45)
        }
        fun daySeconds(slots: List<FloorSlot>): Int = generalWarmupSeconds +
            slots.sumOf { secondsOf(it) } +
            slots.mapNotNull { it.approachFamily }.distinct().size * approachSeconds
        fun minimalDaySeconds(archetype: List<FloorSlot>): Int {
            val kept = archetype.toMutableList()
            for (intent in listOf('I', 'C')) {
                for (slot in archetype.filter { it.intent == intent }) {
                    val trial = kept.toMutableList().also { it.remove(slot) }
                    if (trial.size >= minimumConfigurations && trial.sumOf { setsOf(it) } >= minimumWorkingSets) {
                        kept.clear()
                        kept += trial
                    }
                }
            }
            return daySeconds(kept)
        }
        val longestDaySeconds = floorCalendars.getValue(days).maxOf { name ->
            minimalDaySeconds(floorArchetypes.getValue(name))
        }
        return (longestDaySeconds + 59) / 60
    }

    /**
     * A20 negativas: las mismas entradas de A con 20 min. El plan propio de Músculo
     * (`native:muscle-foundation-v2`) debe rechazar con TIME_BUDGET y requiredMinutes == el piso
     * independiente de ESA fila (21 min en 1, 3, 5 y 6 días).
     */
    private fun rowsANegative20(): List<MatrixRow> = groupADays.map { days ->
        MatrixRow(
            id = "A-d$days-m20",
            group = "A20",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.NEW,
            categories = emptySet(),
            daysPerWeek = days,
            minutes = 20,
            expectedNegativeReason = PlanRejectionReason.TIME_BUDGET,
            negativeWitnessPlanId = NativeProfileKind.MUSCLE.entryId,
            expectedRequiredMinutes = independentBodyweightMuscleFloorMinutes(days),
        )
    }

    /**
     * A21 suficiencia: en el piso de CADA fila (no en un 21 fijo) el plan propio de Músculo, y
     * no otro candidato, alcanza un programa ejecutable cuya sesión más larga mide ese piso.
     */
    private fun rowsASufficiencyAtFloor(): List<MatrixRow> = groupADays.map { days ->
        val floor = independentBodyweightMuscleFloorMinutes(days)
        MatrixRow(
            id = "A-d$days-m$floor",
            group = "A21",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.NEW,
            categories = emptySet(),
            daysPerWeek = days,
            minutes = floor,
            requiredWitnessPlanId = NativeProfileKind.MUSCLE.entryId,
        )
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

    /**
     * Arquetipos de día de Músculo CON tirón disponible (r2 §11.3): FA y FB, que con 3 días forman
     * el calendario FA/FB/FA. Escritos aquí desde el plan, sin leer las tablas del motor. Cada
     * [FloorSlot.approachFamily] es la familia de patrón del slot: S y U comparten la de
     * sentadilla (el primer compuesto de cada patrón del día recibe la aproximación técnica de
     * §12.2), B es empuje horizontal, R tirón horizontal, O empuje vertical, V tirón vertical y D
     * bisagra. El gemelo (G) es aislamiento y el core (C) no lleva aproximación.
     */
    private val floorGeneralArchetypes: Map<String, List<FloorSlot>> = mapOf(
        "FA" to listOf(
            FloorSlot('H', "SQUAT"), FloorSlot('H', "PUSH_H"), FloorSlot('H', "PULL_H"),
            FloorSlot('H', "HINGE"), FloorSlot('C'),
        ),
        "FB" to listOf(
            FloorSlot('H', "SQUAT"), FloorSlot('H', "PUSH_V"), FloorSlot('H', "PULL_V"),
            FloorSlot('H', "HINGE"), FloorSlot('I'),
        ),
    )

    /**
     * Mínimo real, en minutos, del plan propio `native:muscle-foundation-v2` para Músculo
     * principiante con las 11 categorías declaradas y 3 días (GRUPO F). Es un cálculo INDEPENDIENTE
     * del motor: parte del calendario FA/FB/FA y de los arquetipos de r2 §11.3
     * ([floorGeneralArchetypes]) y de las constantes publicadas del estimador común (§12.2),
     * escritas aquí; no lee `SimpleCyclePersonalizer`, `NativeProfileSpec` ni `SessionDurationEstimator`.
     * Es la misma aritmética de [independentBodyweightMuscleFloorMinutes], pero con ejercicios con
     * carga (H de 2 series en el rango 8–12) y con tirón.
     *
     * Por día: se retiran los accesorios opcionales —I primero, C después— mientras el día conserve
     * el suelo de dosis diaria (≥ 2 configuraciones y ≥ 4 series; H no se retira ni se reduce en
     * principiante: ya está en 2 series), así que el día mínimo de FA y de FB son sus CUATRO H, y
     * cuesta:
     *  - 180 s de calentamiento general (3 min, §12.2).
     *  - H con carga: 60 s de preparación + 2 series de 48 s (el estimador mide el extremo alto del
     *    rango 8–12: max(4 s × 12, 45 s)) + 120 s de descanso entre ambas (T3 compuesto) = 276 s.
     *    Un I conservado: 60 + max(4 × 15, 45) = 120 s; un C conservado: 60 + max(4 × 12, 45) = 108 s.
     *  - 90 s (30 s de ejecución + 60 s de descanso) por aproximación técnica: una por familia de
     *    patrón compuesto presente. FA tiene sentadilla, empuje horizontal, tirón horizontal y
     *    bisagra; FB, sentadilla (unilateral), empuje vertical, tirón vertical y bisagra: cuatro
     *    familias en cada uno.
     * Resultado: 180 + 4 × 276 + 4 × 90 = 1644 s = 27,4 → 28 min, en FA y en FB. Coincide con lo
     * que el motor mide el 2026-10-03: de 20 a 27 min informa TIME_BUDGET con 28 y con 28 min las
     * tres sesiones de la semana miden 1644 s (preparación 240, ejecución 384, descansos 480,
     * calentamiento y aproximaciones 540); con 30 min recupera el core (1752 s) y el gemelo (1764 s).
     */
    private fun independentOwnMusclePlanFloorMinutes(): Int {
        val generalWarmupSeconds = 3 * 60
        val setupSecondsPerExercise = 60
        val approachSeconds = 30 + 60
        val restBetweenHeavySets = 120
        val minimumConfigurations = 2
        val minimumWorkingSets = 4
        fun setsOf(slot: FloorSlot): Int = if (slot.intent == 'H') 2 else 1
        fun secondsOf(slot: FloorSlot): Int = when (slot.intent) {
            'H' -> setupSecondsPerExercise + 2 * maxOf(4 * 12, 45) + (2 - 1) * restBetweenHeavySets
            'I' -> setupSecondsPerExercise + 1 * maxOf(4 * 15, 45)
            else -> setupSecondsPerExercise + 1 * maxOf(4 * 12, 45)
        }
        fun daySeconds(slots: List<FloorSlot>): Int = generalWarmupSeconds +
            slots.sumOf { secondsOf(it) } +
            slots.mapNotNull { it.approachFamily }.distinct().size * approachSeconds
        fun minimalDaySeconds(archetype: List<FloorSlot>): Int {
            val kept = archetype.toMutableList()
            for (intent in listOf('I', 'C')) {
                for (slot in archetype.filter { it.intent == intent }) {
                    val trial = kept.toMutableList().also { it.remove(slot) }
                    if (trial.size >= minimumConfigurations && trial.sumOf { setsOf(it) } >= minimumWorkingSets) {
                        kept.clear()
                        kept += trial
                    }
                }
            }
            return daySeconds(kept)
        }
        val longestDaySeconds = listOf("FA", "FB", "FA").maxOf { name ->
            minimalDaySeconds(floorGeneralArchetypes.getValue(name))
        }
        return (longestDaySeconds + 59) / 60
    }

    /**
     * F-m20 (negativa, presupuesto mínimo admitido) y F-m28 (suficiencia en el piso del plan
     * propio; el número sale de [independentOwnMusclePlanFloorMinutes]). El testigo de ambas es
     * `native:muscle-foundation-v2`: el plan que a 20 min informa el TIME_BUDGET tipado con ese
     * mínimo es el que debe llegar a programa con ese mismo mínimo, para que el rechazo no sea un
     * cajón de sastre (mismo criterio que A20/A21). Con `native:full-body` oculto (D2, DEC-w2-07)
     * ya no hay un candidato histórico que pueda hacer pasar la fila en lugar del propio.
     */
    private fun rowsF(): List<MatrixRow> {
        val floor = independentOwnMusclePlanFloorMinutes()
        // F-m20 es negativa solo si 20 min no alcanzan; si el piso bajara a 20 o menos, la fila
        // sería un positivo y habría que rediseñarla, no relajarla.
        check(floor > 20) { "El piso independiente del plan propio ($floor min) debe superar los 20 min de F-m20" }
        return listOf(
            MatrixRow(
                id = "F-m20",
                group = "F",
                goal = SetupGoal.MUSCLE,
                experience = SetupExperience.NEW,
                categories = allCategories,
                daysPerWeek = 3,
                minutes = 20,
                expectedNegativeReason = PlanRejectionReason.TIME_BUDGET,
                negativeWitnessPlanId = NativeProfileKind.MUSCLE.entryId,
                expectedRequiredMinutes = floor,
            ),
            MatrixRow(
                id = "F-m$floor",
                group = "F",
                goal = SetupGoal.MUSCLE,
                experience = SetupExperience.NEW,
                categories = allCategories,
                daysPerWeek = 3,
                minutes = floor,
                requiredWitnessPlanId = NativeProfileKind.MUSCLE.entryId,
            ),
        )
    }

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

    /**
     * T-001 — CASO DE BLOQUEO: gimnasio (todas las categorías) + «Fuerza y músculo» +
     * 5 días / 60 min, con inputs EXPLÍCITOS y el catálogo real de producción.
     * `categories` y `goal` son los que filtran el planner; nada se relaja aquí para
     * que la fila pueda pasar: si el producto la bloquea, la fila queda ROJA.
     */
    private fun rowsT001(): List<MatrixRow> = listOf(
        MatrixRow(
            id = "T001-fuerza-musculo-gym-d5-m60",
            group = "T001",
            goal = SetupGoal.STRENGTH_MUSCLE,
            experience = SetupExperience.INTERMEDIATE,
            categories = allCategories,
            daysPerWeek = 5,
            minutes = 60,
        ),
    )

    private fun rowsT006Athlete(): List<MatrixRow> = listOf(
        MatrixRow(
            id = "T006-Q4-athlete-bodyweight-d1-m60",
            group = "T006-Q4-Athlete",
            goal = SetupGoal.COMPLETE_ATHLETE,
            experience = SetupExperience.NEW,
            categories = emptySet(),
            daysPerWeek = 1,
            minutes = 60,
            cardioMinutes = 10,
        ),
    )

    /**
     * §17.2 #5: Atleta completo con las 11 categorías de material y cardio CAMINAR 10 min, 1 y 2
     * días / 60 min, principiante e intermedio. Sustituye la lectura errónea «el mixto de 1
     * día/60 no cabe»: lo que no cabía era el generador histórico (filas B y T027); el perfil de
     * producto `native:complete-athlete-v2` sí (sesión única de base, §11.4).
     */
    private fun rowsT006AthleteFullMaterial(): List<MatrixRow> = listOf(1, 2).flatMap { days ->
        listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE).map { experience ->
            MatrixRow(
                id = "T006-Q4-athlete-full-d$days-m60-${experience.name}",
                group = "T006-Q4-AthleteFull",
                goal = SetupGoal.COMPLETE_ATHLETE,
                experience = experience,
                categories = allCategories,
                daysPerWeek = days,
                minutes = 60,
                cardioMinutes = 10,
            )
        }
    }

    /**
     * Mismas entradas que `A-d1-m20` (Músculo, NEW, solo cuerpo, 1 día, 20 min): ambas filas deben
     * dar el mismo veredicto, el mínimo real de 21 min calculado de forma independiente.
     */
    private fun rowsT006TimeNegative(): List<MatrixRow> = listOf(
        MatrixRow(
            id = "T006-Q4-muscle-bodyweight-d1-m20-negative",
            group = "T006-Q4-TimeNegative",
            goal = SetupGoal.MUSCLE,
            experience = SetupExperience.NEW,
            categories = emptySet(),
            daysPerWeek = 1,
            minutes = 20,
            expectedNegativeReason = PlanRejectionReason.TIME_BUDGET,
            negativeWitnessPlanId = NativeProfileKind.MUSCLE.entryId,
            expectedRequiredMinutes = independentBodyweightMuscleFloorMinutes(days = 1),
        ),
    )

    private data class MatrixRow(
        val id: String,
        val group: String,
        val goal: SetupGoal,
        val experience: SetupExperience,
        val categories: Set<EquipmentCategory>,
        val daysPerWeek: Int,
        val minutes: Int,
        val cardioMinutes: Int? = null,
        val expectedNegativeReason: PlanRejectionReason? = null,
        /**
         * Solo filas negativas: el plan cuyo rechazo TIME_BUDGET tipado debe informar el mínimo
         * real [expectedRequiredMinutes] (calculado de forma independiente en el test).
         */
        val negativeWitnessPlanId: String? = null,
        val expectedRequiredMinutes: Int? = null,
        /**
         * Solo filas positivas de suficiencia: el plan que debe ser el testigo del programa. Otro
         * candidato viable (p. ej. un nativo histórico que también cabe) no demuestra que el plan
         * propio llegue a un programa en este presupuesto.
         */
        val requiredWitnessPlanId: String? = null,
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

    /**
     * En producción `initializeExerciseDatabase` publica en [CompositionMetadataHolder] los
     * metadatos de composición del catálogo aprobado, y las plantillas del wizard
     * (`CatalogSource.TEMPLATE` → `ProgramTemplateEngine.applyTemplate`) los resuelven por ese
     * holder: el ViewModel no se los pasa. Este arnés no inicializa `ExerciseDatabase`, así que
     * sin el holder cada plantilla publicada (`template:body-20-5` con 5 días,
     * `template:body-16-4` con 6) se rechazaba con `IllegalStateException` convertida en
     * INTERNAL_MATERIALIZATION, un estado que el usuario nunca ve, y la invariante «un rechazo
     * interno no puede pasar como incompatibilidad de usuario» de las filas negativas fallaba por
     * el arnés y no por el producto. Se instala el MISMO proveedor que en producción (derivado del
     * asset real ya verificado por identidad) solo en las filas negativas —las únicas que juzgan
     * los rechazos de todos los candidatos— y se restaura el valor anterior; las demás filas
     * conservan su comportamiento, que depende del orden de los rechazos de plantillas
     * (T001_03 compara el conjunto de rechazos con los ID publicados).
     */
    private inline fun <T> withProductionCompositionMetadata(enabled: Boolean, block: () -> T): T {
        if (!enabled) return block()
        val previous = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = CatalogCompositionMetadataProvider.fromCatalog(approvedCatalog)
        try {
            return block()
        } finally {
            CompositionMetadataHolder.current = previous
        }
    }

    private fun runGroup(label: String, rows: List<MatrixRow>, budget: Duration) = runTest(timeout = budget) {
        currentCaseLabel = "grupo-$label"
        trace("GROUP_BEGIN group=$label filas=${rows.size} budgetMs=${budget.inWholeMilliseconds}")
        val startedAt = System.nanoTime()
        val outcomes = rows.map { row ->
            val outcome = withProductionCompositionMetadata(enabled = row.expectedNegativeReason != null) {
                evaluateRow(row)
            }
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
        var negativePass = false
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

            // T-001 / AC-T001-02: el informe ESTRUCTURADO por candidato se juzga ANTES de
            // exigir que la lista no esté vacía, porque el bloqueo es exactamente el caso en
            // el que los rechazos son el único diagnóstico. El plan pide registrar además el
            // ID publicado, el equipo efectivo y el PRIMER rechazo: los tres quedan en la
            // evidencia de la fila (y el primero, en la propia línea MATRIX_ROW).
            val publishedNow = publishedEntryIdSet(state.draft)
            evidence["firstRejection"] = state.candidateRejections.firstOrNull()?.let { rejection ->
                "${rejection.planId ?: "(global)"}@${rejection.stage}: ${rejection.reason}"
            } ?: "(sin rechazo)"
            evidence["candidateRejections"] = state.candidateRejections.joinToString("; ") { rejection ->
                "${rejection.planId ?: "(global)"}@${rejection.stage}: ${rejection.reason}"
            }.ifBlank { "(ninguno)" }
            assertStructuredRejections(row, state.candidateRejections, publishedNow)

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
            if (row.expectedNegativeReason != null) {
                assertTrue(
                    "una fila negativa esperada no puede publicar candidatos viables: ${state.availablePlanCandidates}",
                    state.availablePlanCandidates.isEmpty(),
                )
                assertTrue(
                    "una fila negativa esperada no puede mostrar tarjetas: ${state.planCandidates}",
                    state.planCandidates.isEmpty(),
                )
                assertNull("no se selecciona un candidato sin tiempo suficiente", state.draft.selectedCatalogId)
                assertNull("no se prepara preview para una combinación rechazada", state.programPreview)
                assertTrue(
                    "la interfaz conserva una explicación de los candidatos rechazados",
                    !state.errors["candidates"].isNullOrBlank(),
                )
                val matchingRejections = state.candidateRejections.filter {
                    it.reasonCode == row.expectedNegativeReason
                }
                assertTrue(
                    "falta el rechazo tipado ${row.expectedNegativeReason}: ${state.candidateRejections}",
                    matchingRejections.isNotEmpty(),
                )
                matchingRejections.forEach { rejection ->
                    assertEquals(
                        "${rejection.planId}: etapa de rechazo temporal",
                        SetupCandidateRejectionStage.DURATION,
                        rejection.stage,
                    )
                }
                // No todos los caminos de rechazo de materialización aportan el mínimo en el campo
                // tipado (p. ej. una receta informa «el mínimo real es 21 min» en su detalle). La
                // fila sí exige un testigo estructurado que conserve requiredMinutes > presupuesto;
                // no exige ese dato redundante a cada candidato rechazado.
                val requiredMinutes = matchingRejections.mapNotNull { it.requiredMinutes }.maxOrNull()
                assertTrue(
                    "el rechazo TIME_BUDGET debe incluir un mínimo tipado mayor que ${row.minutes}: " +
                        matchingRejections.map { "${it.planId}=${it.requiredMinutes}" },
                    requiredMinutes != null && requiredMinutes > row.minutes,
                )
                // Filas con mínimo calculado de forma independiente (grupo A20): el plan propio
                // debe informar EXACTAMENTE ese mínimo, no cualquier número mayor que el presupuesto
                // ni el de un nativo histórico. Sin esto, el TIME_BUDGET de un solo candidato
                // histórico bastaría para el PASS.
                val witnessPlanId = row.negativeWitnessPlanId
                if (witnessPlanId != null) {
                    val witnessRejection = matchingRejections.singleOrNull { it.planId == witnessPlanId }
                    assertNotNull(
                        "${row.id}: falta el rechazo TIME_BUDGET tipado de $witnessPlanId: " +
                            state.candidateRejections.map { "${it.planId}=${it.reasonCode}/${it.requiredMinutes}" },
                        witnessRejection,
                    )
                    assertEquals(
                        "${row.id}: $witnessPlanId debe informar el mínimo real calculado de forma " +
                            "independiente (${row.expectedRequiredMinutes} min)",
                        row.expectedRequiredMinutes,
                        witnessRejection!!.requiredMinutes,
                    )
                    evidence["witnessRequiredMinutes"] = witnessRejection.requiredMinutes.toString()
                }
                assertTrue(
                    "un rechazo interno no puede pasar como incompatibilidad de usuario",
                    state.candidateRejections.none {
                        it.reasonCode == PlanRejectionReason.INTERNAL_MATERIALIZATION ||
                            it.reasonCode == PlanRejectionReason.CATALOG_NOT_READY
                    },
                )
                evidence["outcome"] = "NEGATIVE_PASS"
                evidence["negativeReason"] = row.expectedNegativeReason.name
                evidence["requiredMinutes"] = requireNotNull(requiredMinutes).toString()
                negativePass = true
            } else {
                assertTrue(
                    "availablePlanCandidates vacía para un perfil VÁLIDO; motivo real = ${state.errors["candidates"]}",
                    state.availablePlanCandidates.isNotEmpty(),
                )
                assertTrue(
                    "planCandidates vacía con availablePlanCandidates=${state.availablePlanCandidates.size}",
                    state.planCandidates.isNotEmpty(),
                )

                // Testigo: NATIVO primero; si no hay ninguno, cualquier KPKN genuino sirve de testigo.
                // Una fila de suficiencia exige un plan concreto: otro candidato viable no cuenta.
                val requiredWitnessId = row.requiredWitnessPlanId
                if (requiredWitnessId != null) {
                    assertTrue(
                        "${row.id}: $requiredWitnessId debe ser viable con el mínimo real de la fila " +
                            "(${row.minutes} min); viables=${state.availablePlanCandidates.map { it.id }} " +
                            "rechazos=${state.candidateRejections.map { "${it.planId}=${it.reasonCode}/${it.requiredMinutes}" }}",
                        state.availablePlanCandidates.any { it.id == requiredWitnessId },
                    )
                }
                val ordered = state.availablePlanCandidates
                    .filter { requiredWitnessId == null || it.id == requiredWitnessId }
                    .sortedBy {
                        when {
                            row.goal == SetupGoal.COMPLETE_ATHLETE && it.id == "native:complete-athlete-v2" -> 0
                            it.source == CatalogSource.NATIVE.name -> 1
                            else -> 2
                        }
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
                if (requiredWitnessId != null) {
                    // En el piso de la fila la sesión más larga mide EXACTAMENTE ese piso: ni
                    // cabe por debajo (el piso es independiente del motor) ni se pasa del presupuesto.
                    val longestMeasured = requireNotNull(witnessProgram).macrocycles.flatMap { it.blocks }
                        .flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
                        .maxOf { SessionDurationEstimator.estimate(it).totalMinutes }
                    assertEquals(
                        "${row.id}: la sesión más larga del programa mínimo de $requiredWitnessId debe medir " +
                            "el piso independiente de la fila",
                        row.minutes,
                        longestMeasured,
                    )
                }
            }
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
            problems.isEmpty() && negativePass -> "NEGATIVE_PASS"
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
        if (row.goal == SetupGoal.COMPLETE_ATHLETE) {
            assertEquals(
                "Atleta completo debe previsualizar su receta nativa, no un nativo legacy",
                "native:complete-athlete-v2",
                witness.id,
            )
            val firstWeek = requireNotNull(program.sourceRecipe?.weeks?.firstOrNull()) {
                "${row.id}: el preview de Atleta no conserva receta semanal"
            }
            val athleteSlots = firstWeek.days.flatMap { it.slots }
            val hasStrength = athleteSlots.any { it.intent == SlotIntent.F || it.intent == SlotIntent.FV }
            val hasHypertrophy = athleteSlots.any { it.intent == SlotIntent.H }
            val hasPower = athleteSlots.any { it.intent == SlotIntent.P }
            val hasCardio = firstWeek.days.any { it.cardioBlocks.isNotEmpty() }
            assertTrue("${row.id}: falta fuerza real", hasStrength)
            assertTrue("${row.id}: falta hipertrofia real", hasHypertrophy)
            assertTrue("${row.id}: falta potencia real", hasPower)
            assertTrue("${row.id}: falta cardio estructurado real", hasCardio)
            evidence["athleteComponents"] = "strength=$hasStrength/hypertrophy=$hasHypertrophy/power=$hasPower/cardio=$hasCardio"
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

        // (d) Tiempo: cada estimador sobre SU fuente. Nativo = estimador común medido AQUÍ sobre
        // la sesión (además de lo que el generador sella); fijo = su propio estimate.
        if (isNative) {
            evidence["targetDurationMinutes"] = sessions.joinToString(",") { "${it.targetDurationMinutes}" }
            evidence["measuredMinutes"] = sessions.joinToString(",") {
                "${SessionDurationEstimator.estimate(it).totalMinutes}"
            }
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
                // Comprobación independiente: un generador no puede pasar sellando su propia cifra.
                val measured = SessionDurationEstimator.estimate(session).totalMinutes
                assertTrue(
                    "${row.id}: sesión nativa '${session.id}' mide $measured min con el estimador común " +
                        "(el generador selló ${session.targetDurationMinutes}) y el presupuesto es ${row.minutes} min",
                    measured <= row.minutes,
                )
                assertEquals(
                    "${row.id}: sesión nativa '${session.id}' sella una duración distinta de la medida " +
                        "con el estimador común",
                    measured,
                    session.targetDurationMinutes!!,
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
        vm.updateStep(SetupStepId.WEEKDAYS) {
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
    private fun publishedEntryIdSet(draft: SetupWizardDraft): Set<String> =
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
                // Mismo prefiltro que el wizard (DEC-w2-06): solo Atleta completo exige capacidades
                // declaradas; así la evidencia de «publicadas» es la lista que el VM evalúa de verdad.
                requiredCapabilities = if (draft.goal == SetupGoal.COMPLETE_ATHLETE) {
                    PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE)
                } else {
                    emptySet()
                },
            ),
        ).map { it.id }.toSet()

    /** Misma lista, en la forma de cadena que ya consumía la evidencia de las filas. */
    private fun publishedEntryIds(draft: SetupWizardDraft): String =
        publishedEntryIdSet(draft).joinToString(",").ifEmpty { "-" }

    /**
     * T-001 / AC-T001-02 — CADA rechazo tiene que poder diagnosticarse:
     * etapa tipada coherente con la causa descrita y causa que conserva el detalle
     * real (clase + mensaje de la excepción, o una frase concreta del motor), nunca
     * «sólo la clase» ni el «falta de material» de antes.
     *
     * Se aplica a TODAS las filas de la matriz, incluida la fila de bloqueo: si no
     * hay rechazos no hay nada que juzgar aquí, y la exigencia de candidato real
     * (AC-T001-01) sigue siendo la que falla. El clasificador de causas es propio
     * de esta prueba y sigue el orden de §15.2; NO se importa el de producción, para
     * que la expectativa no se valide a sí misma.
     */
    private fun assertStructuredRejections(
        row: MatrixRow,
        rejections: List<SetupCandidateRejection>,
        publishedIds: Set<String>,
    ) {
        rejections.forEach { rejection ->
            val who = "${row.id}: rechazo de ${rejection.planId ?: "(global)"}"
            assertTrue("$who con causa vacía", rejection.reason.isNotBlank())
            assertTrue(
                "$who conserva SÓLO la clase de la excepción y pierde su mensaje: ${rejection.reason}",
                !rejection.reason.trim().matches(CLASS_NAME_ONLY),
            )
            if (rejection.planId == null) {
                assertEquals(
                    "$who: sin candidato concreto sólo puede ser un fallo de catálogo " +
                        "(etapa=${rejection.stage})",
                    SetupCandidateRejectionStage.CATALOG,
                    rejection.stage,
                )
            } else {
                assertTrue(
                    "$who: CATALOG es una etapa global; un candidato concreto se rechaza en otra " +
                        "(etapa=${rejection.stage})",
                    rejection.stage != SetupCandidateRejectionStage.CATALOG,
                )
                assertTrue(
                    "$who no pertenece a las entradas publicadas de este borrador ($publishedIds)",
                    rejection.planId in publishedIds,
                )
            }
            if (rejection.stage == SetupCandidateRejectionStage.MATERIAL) {
                val cause = dominantCauseOf(rejection.reason)
                assertTrue(
                    "$who etiquetado MATERIAL pero su causa describe otra etapa " +
                        "(${cause ?: "sin causa reconocible"}): ${rejection.reason}",
                    cause == null || cause == SetupCandidateRejectionStage.MATERIAL,
                )
            }
        }
    }

    /**
     * Etapa que describe la CAUSA escrita en el motivo, siguiendo el orden de §15.2
     * (catálogo → frecuencia → material → duración → perfil → composición).
     * `null` cuando el texto no permite decidir: en ese caso no se afirma nada.
     */
    private fun dominantCauseOf(reason: String): SetupCandidateRejectionStage? {
        val text = reason.lowercase()
        return when {
            "catálogo" in text || "catalogo" in text -> SetupCandidateRejectionStage.CATALOG
            "frecuencia" in text -> SetupCandidateRejectionStage.FREQUENCY
            // PRECEDENCIA §15.2: material ANTES que duración/perfil. Un motivo que
            // menciona «equipo … y tiempo» o «equipo y experiencia» describe MATERIAL
            // en primer término, igual que exige el orden del plan; el orden NO es
            // arbitrario (invertirlo hacía fallar filas cuyo rechazo era correcto).
            MATERIAL_CAUSE.containsMatchIn(text) -> SetupCandidateRejectionStage.MATERIAL
            MINUTE.containsMatchIn(text) || "tiempo" in text -> SetupCandidateRejectionStage.DURATION
            "experiencia" in text || "nivel" in text -> SetupCandidateRejectionStage.PROFILE
            "volumen" in text -> SetupCandidateRejectionStage.COMPOSITION
            else -> null
        }
    }

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
                // T-001: ID publicado + etapa + causa del PRIMER rechazo estructurado. Columna
                // ADITIVA al final: conserva la posición de las 28 anteriores.
                sanitize(e["firstRejection"].orEmpty()),
            ).joinToString("|"),
        )
    }

    private fun sanitize(value: String): String = value.replace('|', '/').replace('\n', ' ').trim()

    private fun reportGroup(label: String, outcomes: List<RowOutcome>) {
        val failures = outcomes.filter { it.problems.isNotEmpty() }
        val harnessBlocked = outcomes.count { it.status == "HARNESS_FAIL" }
        val negativePasses = outcomes.count { it.status == "NEGATIVE_PASS" }
        val reachedProgram = outcomes.count { it.entries["witness"] != null && it.entries["witness"] != "-" }
        println(
            "[T-019] grupo $label: filas=${outcomes.size} positivos=${outcomes.count { it.status == "OK" }} " +
                "negativePasses=$negativePasses conProblemas=${failures.size} " +
                "harnessBlocked=$harnessBlocked filasQueAlcanzaronPrograma=$reachedProgram",
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
     * Adaptador REAL sobre Room con un interruptor SÓLO-TEST: mientras `failing` está activo,
     * `save` lanza y el VM actualiza el borrador EN MEMORIA sin llamar a `updateCandidates`.
     * Ese es el camino real de fallo de guardado (AC-T001-03): la respuesta nueva cambia la
     * clave de entrenamiento mientras el cálculo anterior sigue en vuelo, y ése NO se cancela.
     * No reimplementa ninguna decisión: delega todo lo demás en [RoomWizardPersistence].
     */
    private class FailOnDemandPersistence(db: KpknDatabase) : SetupWizardPersistence {
        private val delegate = RoomWizardPersistence(db)

        @Volatile
        var failing: Boolean = false

        override suspend fun load(draftId: String): SetupDraft? = delegate.load(draftId)

        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft {
            if (failing) throw IOException("T001_03: fallo de guardado inyectado")
            return delegate.save(draftId, payloadJson, revision, catalogRevision)
        }

        override suspend fun discard(draftId: String) = delegate.discard(draftId)

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = delegate.listRecoverable()
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
     * Puerta SÓLO-TEST sobre el materializador de T-001 / AC-T001-03: el primer cálculo de
     * candidatos queda ATRAPADO dentro y todos los llamadores siguientes también, hasta
     * [releaseGate]. No decide qué candidatos se publican (eso sigue en el VM real): sólo
     * retrasa el `SetupPreview` que devolvería el motor, para que la publicación anterior
     * siga pendiente cuando cambian las respuestas.
     *
     * `CompletableDeferred.await` es cancelable: cuando `updateCandidates` cancela el job
     * viejo, ese cálculo se retira con `CancellationException` (que NUNCA se convierte en un
     * rechazo de producto) en lugar de publicar. La puerta se libera SIEMPRE en el `finally`.
     */
    private class CandidateScanGate(private val sink: (String) -> Unit) {
        /** Señal de que el materializador está dentro; una sola vez, como la primera entrada. */
        val entered = CountDownLatch(1)

        /** Contador real de entradas: distingue el cálculo de A del de X/B en la traza. */
        val enteredCount = AtomicInteger(0)

        /** Contador real de salidas: permite saber, con techo, cuándo el escaneo terminó. */
        val releasedCount = AtomicInteger(0)

        private val release = CompletableDeferred<Unit>()

        suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
            val count = enteredCount.incrementAndGet()
            entered.countDown()
            sink(
                "GATE materialize-enter count=$count dias=${draft.daysPerWeek} " +
                    "min=${draft.minutesPerSession} plan=${draft.selectedCatalogId ?: "-"}",
            )
            // El `finally` también corre si la espera se cancela: así un cálculo abandonado
            // descuenta su entrada y los contadores siguen equilibrados (AC-T001-03).
            try {
                release.await()
            } finally {
                releasedCount.incrementAndGet()
            }
            sink("GATE materialize-release count=$count")
            // Programa NULO: todos los candidatos publicados quedan rechazados, de modo que el
            // conjunto de rechazos del estado final identifica SIN AMBIGÜEDAD a qué borrador
            // pertenece la última publicación.
            return SetupPreview(null, null)
        }

        fun releaseGate() {
            release.complete(Unit)
            sink("GATE release signalled")
        }
    }

    /**
     * Espera acotada y finita de la entrada real en la puerta del materializador, bombeando el
     * scheduler de test para que el job de candidatos llegue a `Dispatchers.IO`. NUNCA espera
     * sin cota; mismo patrón que [awaitGateEntered].
     */
    private fun TestScope.awaitMaterializerEntered(gate: CandidateScanGate, budgetMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            dispatcher.scheduler.runCurrent()
            if (gate.entered.await(5, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    /**
     * Espera ACOTADA a que el escaneo que pasó por la puerta termine de verdad: todas las
     * entradas abiertas tienen su salida Y el equilibrio se sostiene durante 5 muestras
     * seguidas (entre una entrada y la siguiente sólo hay instrucciones no suspensivas, así
     * que 50 ms de estabilidad significan que el bucle terminó y el job ya pasó por su punto
     * de publicación). Bompea el scheduler en cada muestra para que la continuación en
     * `Dispatchers.Main` se ejecute. Techo finito; nunca espera sin cota.
     */
    private fun TestScope.awaitMaterializerSettled(gate: CandidateScanGate, budgetMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L
        var stable = 0
        while (System.nanoTime() < deadlineNanos) {
            dispatcher.scheduler.advanceUntilIdle()
            stable = if (gate.enteredCount.get() == gate.releasedCount.get()) stable + 1 else 0
            if (stable >= 5) return true
            Thread.sleep(10L)
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


    /**
     * T020_e — repositorio del catálogo que falla las primeras [failures] cargas (publica `Error`, como el real, sin
     * lanzar) y luego delega en el repositorio real: el VM no debe darlo por cargado y «Reintentar» debe volver a leerlo.
     */
    private class FlakyCatalogRepository(
        private val real: ExerciseCatalogRepositoryV2,
        private var failures: Int,
    ) : ExerciseCatalogRepositoryV2 by real {
        private val flakyState = MutableStateFlow<ExerciseCatalogStateV2>(ExerciseCatalogStateV2.Loading)

        override val state: StateFlow<ExerciseCatalogStateV2> get() = flakyState

        override suspend fun load() {
            if (failures > 0) {
                failures -= 1
                flakyState.value = ExerciseCatalogStateV2.Error("catalogo_de_prueba_no_disponible")
                return
            }
            real.load()
            flakyState.value = real.state.value
        }
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

        /** Puerta SÓLO-TEST de T-001 / AC-T001-03: techos finitos, liberación garantizada. */
        const val T001_GATE_ENTER_BUDGET_MILLIS = 15_000L
        const val T001_GATE_SETTLE_BUDGET_MILLIS = 20_000L

        /**
         * T-001 / AC-T001-02 — un motivo que es SÓLO el nombre de una clase de excepción
         * perdió su mensaje: ya no explica nada. Causas redactadas (del motor o clase+mensaje)
         * contienen espacios, así que no encajan en este patrón.
         */
        val CLASS_NAME_ONLY = Regex("""[A-Za-z_][\w.$]*""")

        /** Duración expresada en minutos, con o sin unidad completa (`100 min`, `60 minutos`). */
        val MINUTE = Regex("""\bmin\b|\bminutos?\b""")

        /** Causa de MATERIAL descrita en el motivo: lo contrario del «todo es falta de material». */
        val MATERIAL_CAUSE = Regex("""material|equipo|aparato|enfoque|m[áa]quina|banco|barra|mancuerna""")

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
