package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.testsupport.RoomTestRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Álbum por ejercicio (LV8): `listForExerciseKeys` junta los medios de un ejercicio ENTRE sesiones por todas las claves con las que se pudo
 * guardar, sin repetir y del más reciente al más antiguo; y el borrado definitivo quita la fila y los archivos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaExerciseAlbumTest {

    @get:Rule
    val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase = KpknDatabase.createInMemory(context).also { databases += it }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
    }

    private fun medio(id: String, ejercicio: String?, cuando: Long, sesion: String = "sk-$id", ruta: String = "/tmp/$id.jpg") = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = ruta,
        createdAtMs = cuando,
        sessionKey = sesion,
        canonicalExerciseId = ejercicio,
    )

    @Test
    fun elAlbumDeUnEjercicioJuntaSusMediosDeTodasLasSesionesDelMasRecienteAlMasAntiguo() = runBlocking {
        val repo = WorkoutMediaRepository.forDatabase(context, db())
        repo.upsert(medio("lunes", "sentadilla", cuando = 10L, sesion = "sesion-lunes"))
        repo.upsert(medio("jueves", "sentadilla", cuando = 30L, sesion = "sesion-jueves"))
        repo.upsert(medio("martes-banca", "banca", cuando = 20L, sesion = "sesion-martes"))
        repo.upsert(medio("hoy", "sentadilla", cuando = 40L, sesion = "sesion-hoy"))

        val album = repo.listForExerciseKeys(listOf("sentadilla"))

        assertEquals(listOf("hoy", "jueves", "lunes"), album.map { it.id })
        assertEquals("el álbum cruza tres sesiones distintas", 3, album.map { it.sessionKey }.distinct().size)
    }

    @Test
    fun conVariasClavesNoSeRepitenNiSeMezclanOtrosEjercicios() = runBlocking {
        val repo = WorkoutMediaRepository.forDatabase(context, db())
        repo.upsert(medio("por-clave-canonica", "sentadilla", cuando = 30L))
        repo.upsert(medio("por-id", "ej-1", cuando = 20L))
        repo.upsert(medio("otro", "banca", cuando = 10L))

        val album = repo.listForExerciseKeys(listOf("sentadilla", "ej-1", "sentadilla", "  ", ""))

        assertEquals(listOf("por-clave-canonica", "por-id"), album.map { it.id })
    }

    @Test
    fun sinClavesOSinMediosElAlbumEstaVacio() = runBlocking {
        val repo = WorkoutMediaRepository.forDatabase(context, db())
        repo.upsert(medio("a", "banca", cuando = 1L))

        assertTrue(repo.listForExerciseKeys(emptyList()).isEmpty())
        assertTrue(repo.listForExerciseKeys(listOf("peso-muerto")).isEmpty())
    }

    @Test
    fun elAlbumLeeElEsfuerzoDelLogDeCadaFechaYLadoSinInventarlo() = runBlocking {
        val database = db()
        val repo = WorkoutMediaRepository.forDatabase(context, database)
        fun log(id: String, rir: Int) = WorkoutLog(
            id = id, programId = "p", sessionId = "s", sessionName = "Pierna", date = "2026-10-15", durationMinutes = 30,
            completedExercises = listOf(CompletedExercise(
                exerciseId = "ej-1", exerciseName = "Sentadilla",
                sets = listOf(CompletedSet(id = "ej-1_0_L", rir = rir), CompletedSet(id = "ej-1_0_R", rir = 4)),
            )),
        )
        database.workoutLogDao().insert(log("lunes", 3).toEntity())
        database.workoutLogDao().insert(log("jueves", 1).toEntity())
        val lunes = medio("a", "sentadilla", 1).copy(workoutLogId = "lunes", exerciseId = "ej-1", setIndex = 0, side = "left")
        val jueves = lunes.copy(id = "b", workoutLogId = "jueves")
        val derecho = jueves.copy(id = "c", side = "right")
        val sinSerie = jueves.copy(id = "d", setIndex = 4)
        val sinLog = jueves.copy(id = "e", workoutLogId = null)

        assertEquals(mapOf("a" to "RIR 3", "b" to "RIR 1", "c" to "RIR 4"), repo.effortLabelsForMedia(listOf(lunes, jueves, derecho, sinSerie, sinLog)))
    }

    @Test
    fun elBorradoDefinitivoQuitaLaFilaYElArchivo() = runBlocking {
        val repo = WorkoutMediaRepository.forDatabase(context, db())
        val almacen = WorkoutMediaStore(context.applicationContext.filesDir)
        val archivo = almacen.destinationFile("borrable", WorkoutMediaKind.PHOTO, createdAtMs = 5L)
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        repo.upsert(medio("borrable", "sentadilla", cuando = 5L, ruta = archivo.absolutePath))
        repo.upsert(medio("se-queda", "sentadilla", cuando = 6L))
        assertTrue(archivo.isFile)

        repo.delete("borrable")

        assertNull(repo.getById("borrable"))
        assertFalse("el archivo se borra con la fila", File(archivo.absolutePath).exists())
        assertEquals(listOf("se-queda"), repo.listForExerciseKeys(listOf("sentadilla")).map { it.id })
    }

    // ── Borrar con «Deshacer» contra el repositorio real: filas y archivos solo se tocan al caducar o al salir ───────────────

    private fun conArchivo(almacen: WorkoutMediaStore, id: String, cuando: Long): WorkoutMedia {
        val archivo = almacen.destinationFile(id, WorkoutMediaKind.PHOTO, createdAtMs = cuando)
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        return medio(id, "sentadilla", cuando = cuando, ruta = archivo.absolutePath)
    }

    private class Escenario(val repo: WorkoutMediaRepository, val reloj: TestScope, val aplazado: DeferredMediaDelete, val a: WorkoutMedia, val b: WorkoutMedia)

    private fun escenario(): Escenario = runBlocking {
        val repo = WorkoutMediaRepository.forDatabase(context, db())
        val almacen = WorkoutMediaStore(context.applicationContext.filesDir)
        val a = conArchivo(almacen, "borrable", cuando = 5L)
        val b = conArchivo(almacen, "se-queda", cuando = 6L)
        repo.upsert(a)
        repo.upsert(b)
        val reloj = TestScope()
        // El borrado definitivo es el del repositorio, el que quita la fila y los archivos.
        val aplazado = DeferredMediaDelete(reloj, commit = { items -> runBlocking { items.forEach { repo.delete(it.id) } } })
        Escenario(repo, reloj, aplazado, a, b)
    }

    private fun existe(e: Escenario, medio: WorkoutMedia): Boolean = runBlocking {
        e.repo.getById(medio.id) != null && File(medio.filePath).exists()
    }

    @Test
    fun alBorrarNoSeTocanLaFilaNiElArchivoHastaQueCaduca() {
        val e = escenario()

        e.aplazado.schedule(listOf(e.a))
        e.reloj.advanceTimeBy(4_999L)
        e.reloj.runCurrent()
        assertTrue("antes de caducar el aviso la fila y el archivo siguen ahí", existe(e, e.a))

        e.reloj.advanceTimeBy(2L)
        e.reloj.runCurrent()

        assertFalse("al caducar se borran la fila y el archivo", existe(e, e.a))
        assertNull(runBlocking { e.repo.getById(e.a.id) })
        assertFalse(File(e.a.filePath).exists())
        assertTrue("lo que no se borró no se toca", existe(e, e.b))
    }

    @Test
    fun siSeDeshaceLaFilaYElArchivoSiguenAhiPasadoElTiempo() {
        val e = escenario()
        e.aplazado.schedule(listOf(e.a))
        e.reloj.advanceTimeBy(2_000L)

        e.aplazado.undo()
        e.reloj.advanceTimeBy(60_000L)
        e.reloj.runCurrent()

        assertTrue("deshacer no borra nada, ni después", existe(e, e.a))
    }

    @Test
    fun alSalirDelAlbumLoQueSeguiaEnEsperaSeBorraDeVerdad() {
        val e = escenario()
        e.aplazado.schedule(listOf(e.a))
        e.reloj.advanceTimeBy(1_000L)
        assertTrue("antes de salir, nada", existe(e, e.a))

        e.aplazado.flush()

        assertFalse("al salir se borran la fila y el archivo", existe(e, e.a))
        assertTrue(existe(e, e.b))
    }
}
