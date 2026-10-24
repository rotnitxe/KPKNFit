package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.testsupport.MotCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
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
}
