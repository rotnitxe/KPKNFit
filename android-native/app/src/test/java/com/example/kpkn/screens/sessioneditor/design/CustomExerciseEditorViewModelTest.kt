package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.testsupport.MotCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.*
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomExerciseEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }
    private fun vm(name: String, initial: ExerciseMuscleInfo? = null,
        save: suspend (ExerciseMuscleInfo) -> Result<ExerciseMuscleInfo> = { Result.success(it) },
        photo: suspend (CustomExerciseCoverDraft.DevicePhoto, ExerciseMuscleInfo) -> WorkoutMedia = { _, _ -> error("No debe importar") },
    ) = CustomExerciseEditorViewModel(initial, name, MotCatalog.legacyDefaultCatalog, MotCatalog.catalog, save, photo, dispatcher)

    @Test fun reutilizaRankingMotYNoOfreceCandidatosCuandoElMotorSeAbstiene() = runTest(dispatcher) {
        val editor = vm("sentadila trasera barra alta")
        advanceUntilIdle()
        val expected = SmartExerciseCreator.findReferences("sentadila trasera barra alta", MotCatalog.legacyDefaultCatalog, MotCatalog.catalog)
        assertEquals(expected.candidates.map { it.definitionId }, editor.state.value.candidates.map { it.definitionId })
        assertEquals(ReferenceOutcome.CONFIRMED, editor.state.value.preview!!.referenceResult!!.outcome)
        assertFalse(editor.state.value.preview!!.exercise.description.isNullOrBlank())
        val id = editor.state.value.preview!!.exercise.id
        editor.edit { it.copy(name = "XQZ 123") }
        advanceUntilIdle()
        assertTrue(editor.state.value.candidates.isEmpty())
        assertEquals(ReferenceOutcome.NONE, editor.state.value.preview!!.referenceResult!!.outcome)
        assertNull(editor.state.value.preview!!.exercise.description)
        assertEquals(id, editor.state.value.preview!!.exercise.id)
    }

    @Test fun nombreAmbiguoNoGeneraDescripcionYLaDescripcionManualSeRespeta() = runTest(dispatcher) {
        val editor = vm("curl")
        advanceUntilIdle()
        assertNotEquals(ReferenceOutcome.CONFIRMED, editor.state.value.preview!!.referenceResult!!.outcome)
        assertNull(editor.state.value.preview!!.exercise.description)
        editor.edit { it.copy(description = "Mi ejecución confirmada por mí") }
        advanceUntilIdle()
        assertEquals("Mi ejecución confirmada por mí", editor.state.value.preview!!.exercise.description)
        assertEquals("USER", editor.state.value.preview!!.exercise.descriptionOrigin)
    }

    @Test fun guardarExigeRevisionYEsperaElResultadoDurableSinCerrarAntes() = runTest(dispatcher) {
        val finish = CompletableDeferred<Result<ExerciseMuscleInfo>>()
        var calls = 0
        val editor = vm("sentadilla barra baja", save = { calls++; finish.await() })
        advanceUntilIdle()
        editor.save(); advanceUntilIdle()
        assertEquals(0, calls)
        editor.edit { it.copy(
            implement = SmartExerciseCreator.IMPLEMENTO_IDS.first(),
            station = SmartExerciseCreator.ESTACION_IDS.first(),
            laterality = SmartExerciseCreator.LATERALIDAD_IDS.first(),
        ) }; advanceUntilIdle()
        editor.review(true); editor.save(); runCurrent()
        assertTrue(editor.state.value.saving)
        assertNull(editor.state.value.saved)
        val info = editor.state.value.preview!!.exercise
        finish.complete(Result.success(info)); advanceUntilIdle()
        assertEquals(info, editor.state.value.saved)
        assertFalse(editor.state.value.saving)
    }

    @Test fun falloYReintentoConservanFormularioUuidYLaFotoSinImportarlaDosVeces() = runTest(dispatcher) {
        var attempts = 0
        var imports = 0
        val writes = mutableListOf<ExerciseMuscleInfo>()
        val editor = vm("XQZ 123", save = { info ->
            writes += info
            if (++attempts == 1) Result.failure(IllegalStateException("Room rechazó")) else Result.success(info)
        }, photo = { pending, info -> imports++; WorkoutMedia(pending.importId, WorkoutMediaKind.PHOTO, "private.jpg", createdAtMs = 1, canonicalExerciseId = info.id) })
        advanceUntilIdle()
        editor.edit { it.copy(
            implement = SmartExerciseCreator.IMPLEMENTO_IDS.first(),
            station = SmartExerciseCreator.ESTACION_IDS.first(),
            laterality = SmartExerciseCreator.LATERALIDAD_IDS.first(),
        ) }; advanceUntilIdle()
        editor.edit { it.copy(description = "Texto personal") }; advanceUntilIdle()
        val choice = CustomExerciseCoverDraft.DevicePhoto("content://picker/photo", "photo-id")
        editor.cover(choice)
        assertEquals(0, imports)
        editor.review(true); editor.save(); advanceUntilIdle()
        assertNotNull(editor.state.value.error)
        assertNull(editor.state.value.saved)
        assertEquals("Texto personal", editor.state.value.description)
        assertEquals(1, imports)
        editor.save(); advanceUntilIdle()
        assertEquals(1, imports)
        assertEquals(writes[0], writes[1])
        assertEquals(ExerciseCover.userPhoto("photo-id"), editor.state.value.saved!!.cover)
    }

    @Test fun elegirPortadaSinGuardarNoPublicaNiImportaYNoMutaElOriginal() = runTest(dispatcher) {
        var writes = 0
        val original = ExerciseMuscleInfo("custom:existing", "Propio", description = "Manual", descriptionOrigin = "USER",
            isCustom = true, images = listOf("image"), videos = listOf("video"), cover = ExerciseCover.userPhoto("old"))
        val editor = vm("", original, save = { writes++; Result.success(it) })
        advanceUntilIdle()
        editor.cover(CustomExerciseCoverDraft.Pattern)
        assertEquals(ExerciseCover.userPhoto("old"), original.cover)
        assertEquals(0, writes)
        editor.review(true); editor.save(); advanceUntilIdle()
        val saved = editor.state.value.saved!!
        assertEquals(original.id, saved.id)
        assertEquals(original.images, saved.images)
        assertEquals(original.videos, saved.videos)
        assertEquals(original.description, saved.description)
    }

    @Test fun falloDeImportacionNoGuardaElEjercicioYPermiteReintentarLaMismaEleccion() = runTest(dispatcher) {
        var writes = 0
        var imports = 0
        val editor = vm("XQZ 123", save = { writes++; Result.success(it) }, photo = { pending, _ ->
            if (++imports == 1) error("Original no disponible temporalmente")
            WorkoutMedia(pending.importId, WorkoutMediaKind.PHOTO, "private.jpg", createdAtMs = 1)
        })
        advanceUntilIdle()
        editor.edit { it.copy(
            implement = SmartExerciseCreator.IMPLEMENTO_IDS.first(),
            station = SmartExerciseCreator.ESTACION_IDS.first(),
            laterality = SmartExerciseCreator.LATERALIDAD_IDS.first(),
        ) }; advanceUntilIdle()
        val choice = CustomExerciseCoverDraft.DevicePhoto("content://picker/photo", "same-photo")
        editor.cover(choice); editor.review(true); editor.save(); advanceUntilIdle()
        assertEquals(0, writes)
        assertEquals(choice, editor.state.value.cover)
        assertNotNull(editor.state.value.error)
        editor.save(); advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(ExerciseCover.userPhoto(choice.importId), editor.state.value.saved!!.cover)
    }

    @Test fun portadaDeReferenciaConservaDescripcionManualPeroSeRetiraSiCambiaLaVariante() = runTest(dispatcher) {
        val editor = vm("sentadilla barra baja")
        advanceUntilIdle()
        val cover = customExerciseReferenceCover(editor.state.value.preview) { _, _ -> true }!!
        editor.cover(CustomExerciseCoverDraft.Reference(cover))
        editor.edit { it.copy(description = "Mi descripción") }; advanceUntilIdle()
        assertEquals(CustomExerciseCoverDraft.Reference(cover), editor.state.value.cover)
        editor.edit { it.copy(implement = "cable") }; advanceUntilIdle()
        assertEquals(CustomExerciseCoverDraft.Pattern, editor.state.value.cover)
    }

    @Test fun quitarTodosLosMusculosNoLosRellenaDeNuevoConUnaInferencia() = runTest(dispatcher) {
        val editor = vm("sentadilla barra baja")
        advanceUntilIdle()
        assertTrue(editor.state.value.preview!!.exercise.involvedMuscles.isNotEmpty())
        editor.edit { it.copy(muscles = emptyList()) }; advanceUntilIdle()
        assertTrue(editor.state.value.preview!!.exercise.involvedMuscles.isEmpty())
    }

    @Test fun altaManualExigeElegirLosTresEjesAntesDeRevisarYGuardar() = runTest(dispatcher) {
        var writes = 0
        val editor = vm("Press personalizado", save = { writes++; Result.success(it) })
        advanceUntilIdle()

        editor.review(true)
        editor.save()
        advanceUntilIdle()
        assertFalse(editor.state.value.reviewed)
        assertEquals(0, writes)

        editor.edit { it.copy(implement = SmartExerciseCreator.IMPLEMENTO_IDS.first()) }
        advanceUntilIdle()
        editor.review(true)
        editor.save()
        advanceUntilIdle()
        assertFalse(editor.state.value.reviewed)
        assertEquals(0, writes)

        editor.edit { it.copy(station = SmartExerciseCreator.ESTACION_IDS.first()) }
        advanceUntilIdle()
        editor.edit { it.copy(laterality = SmartExerciseCreator.LATERALIDAD_IDS.first()) }
        advanceUntilIdle()
        editor.review(true)
        editor.save()
        advanceUntilIdle()
        assertEquals(1, writes)
        assertNotNull(editor.state.value.saved)
    }

    @Test fun edicionCargaYConservaLosEjesTecnicosYaGuardados() = runTest(dispatcher) {
        val implement = SmartExerciseCreator.IMPLEMENTO_IDS.first()
        val station = SmartExerciseCreator.ESTACION_IDS.first()
        val laterality = SmartExerciseCreator.LATERALIDAD_IDS.first()
        val original = ExerciseMuscleInfo(
            id = "custom:axes",
            name = "Press propio",
            equipment = SmartExerciseCreator.implementoLabel(implement),
            catalogVariantChips = listOf(SmartExerciseCreator.estacionLabel(station), SmartExerciseCreator.lateralidadLabel(laterality)),
            isCustom = true,
        )
        val editor = vm("", initial = original)
        advanceUntilIdle()

        assertEquals(implement, editor.state.value.implement)
        assertEquals(station, editor.state.value.station)
        assertEquals(laterality, editor.state.value.laterality)
        editor.review(true)
        editor.save()
        advanceUntilIdle()
        assertEquals(original.equipment, editor.state.value.saved!!.equipment)
        assertEquals(
            listOf(
                SmartExerciseCreator.implementoLabel(implement),
                SmartExerciseCreator.estacionLabel(station),
                SmartExerciseCreator.lateralidadLabel(laterality),
            ),
            editor.state.value.saved!!.catalogVariantChips,
        )
    }

    @Test fun debounceYResultadosFueraDeOrdenNuncaPermitenRevisarOGuardarLaIdentidadAnterior() = runTest(dispatcher) {
        val pendingPreviewTasks = mutableListOf<Runnable>()
        val manualPreviewDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                pendingPreviewTasks += block
            }
        }
        var savedInfo: ExerciseMuscleInfo? = null
        val editor = CustomExerciseEditorViewModel(
            initial = null,
            initialName = "Sentadilla con barra",
            catalog = MotCatalog.legacyDefaultCatalog,
            catalogV2 = MotCatalog.catalog,
            persist = { info -> savedInfo = info; Result.success(info) },
            importPhoto = { _, _ -> error("no photo") },
            previewDispatcher = manualPreviewDispatcher,
        )
        advanceTimeBy(360)
        runCurrent()
        assertEquals(1, pendingPreviewTasks.size)
        pendingPreviewTasks.removeAt(0).run()
        advanceUntilIdle()
        assertTrue(editor.state.value.isPreviewCurrent)

        editor.edit {
            it.copy(
                implement = SmartExerciseCreator.IMPLEMENTO_IDS.first(),
                station = SmartExerciseCreator.ESTACION_IDS.first(),
                laterality = SmartExerciseCreator.LATERALIDAD_IDS.first(),
            )
        }
        advanceTimeBy(360)
        runCurrent()
        pendingPreviewTasks.removeAt(0).run()
        advanceUntilIdle()
        editor.review(true)
        assertTrue(editor.state.value.reviewed)

        // Rapid identity switch #1 ("Press inclinado"): blocked during 350ms debounce & slow preview computation.
        editor.edit { it.copy(name = "Press inclinado propio") }
        assertFalse(editor.state.value.reviewed)
        assertTrue(editor.state.value.recalculating)
        advanceTimeBy(150)
        runCurrent()
        editor.review(true)
        editor.save()
        assertFalse(editor.state.value.reviewed)
        assertNull(savedInfo)

        // Advance past 350ms so task #1 is dispatched to manualPreviewDispatcher (held uncompleted).
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, pendingPreviewTasks.size)

        // Identity switch #2 ("Peso muerto rumano propio") before task #1 completes.
        editor.edit { it.copy(name = "Peso muerto rumano propio") }
        advanceTimeBy(360)
        runCurrent()
        assertEquals(2, pendingPreviewTasks.size)

        // Out-of-order completion: task #2 (index 1) finishes BEFORE stale task #1 (index 0).
        pendingPreviewTasks[1].run()
        advanceUntilIdle()
        assertEquals("Peso muerto rumano propio", editor.state.value.preview!!.exercise.name)
        assertTrue(editor.state.value.isPreviewCurrent)

        // Late task #1 arrives after task #2 already published — must be ignored cleanly.
        pendingPreviewTasks[0].run()
        advanceUntilIdle()
        assertEquals("Peso muerto rumano propio", editor.state.value.preview!!.exercise.name)
        assertTrue(editor.state.value.isPreviewCurrent)

        editor.review(true)
        editor.save()
        advanceUntilIdle()
        assertEquals("Peso muerto rumano propio", savedInfo!!.name)
    }

    @Test fun nombreVacioTemporalConservaBorradorYAporteEditadoSeConservaAlReabrir() = runTest(dispatcher) {
        var savedInfo: ExerciseMuscleInfo? = null
        val editor = vm("Curl propio", save = { info -> savedInfo = info; Result.success(info) })
        advanceUntilIdle()
        editor.edit {
            it.copy(
                implement = SmartExerciseCreator.IMPLEMENTO_IDS.first(),
                station = SmartExerciseCreator.ESTACION_IDS.first(),
                laterality = SmartExerciseCreator.LATERALIDAD_IDS.first(),
            )
        }
        advanceUntilIdle()
        editor.review(true)
        assertTrue(editor.state.value.reviewed)

        val customMuscles = listOf(
            InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 0.85),
            InvolvedMuscle("Antebrazo", MuscleRole.SECONDARY, 0.70),
            InvolvedMuscle("Core", MuscleRole.STABILIZER, 0.40),
        )
        editor.edit { it.copy(description = "Codos pegados", muscles = customMuscles) }
        // Changing muscles must invalidate prior technical review and apply contributions immediately.
        assertFalse(editor.state.value.reviewed)
        assertEquals(customMuscles, editor.state.value.preview!!.exercise.involvedMuscles)
        assertEquals("Codos pegados", editor.state.value.preview!!.exercise.description)

        // Clearing name temporarily preserves typed description, muscles & axes.
        editor.edit { it.copy(name = "") }
        assertNull(editor.state.value.preview)
        assertEquals("Codos pegados", editor.state.value.description)
        assertEquals(customMuscles, editor.state.value.muscles)
        assertFalse(editor.state.value.isPreviewCurrent)

        // Re-typing final name recalculates and keeps draft muscles + description.
        editor.edit { it.copy(name = "Curl concentrado propio") }
        advanceUntilIdle()
        editor.review(true)
        editor.save()
        advanceUntilIdle()

        assertEquals("Curl concentrado propio", savedInfo!!.name)
        assertEquals("Codos pegados", savedInfo!!.description)
        assertEquals(customMuscles, savedInfo!!.involvedMuscles)

        // Reopening existing custom exercise preserves customized 0.85/0.70/0.40 contributions until edited.
        val reopened = vm("", initial = savedInfo)
        advanceUntilIdle()
        assertEquals(customMuscles, reopened.state.value.preview!!.exercise.involvedMuscles)
    }
}
