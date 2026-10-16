package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El álbum con el aspecto de A4 (LV8) con la pieza REAL: título y subtítulo, registros por fecha, filtros, borrar con «Deshacer» y
 * «Seleccionar». Lo borrado no se toca hasta que caduca el aviso o se sale del álbum.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class AlbumSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val reloj = TestScope()
    private val borrados = mutableListOf<List<String>>()
    private val aplazado = DeferredMediaDelete(reloj, commit = { items -> borrados += items.map { it.id } })

    private val ahora = System.currentTimeMillis()

    private fun medio(id: String, kind: WorkoutMediaKind, hace: Long, serie: Int, marca: Boolean = false) = WorkoutMedia(
        id = id,
        kind = kind,
        filePath = "/medios/$id",
        createdAtMs = ahora - hace,
        exerciseId = "sentadilla",
        canonicalExerciseId = "sentadilla",
        exerciseName = "Sentadilla trasera",
        setIndex = serie,
        weightKg = 95.0,
        reps = 6,
        isPr = marca,
        durationMs = if (kind == WorkoutMediaKind.VIDEO) 14_000L else null,
    )

    private val video = medio("v1", WorkoutMediaKind.VIDEO, hace = 60_000L, serie = 2)
    private val foto = medio("f1", WorkoutMediaKind.PHOTO, hace = 120_000L, serie = 0)
    private val viejo = medio("v2", WorkoutMediaKind.VIDEO, hace = 3L * 24 * 3_600_000L, serie = 3, marca = true)

    private fun mostrar(items: List<WorkoutMedia> = listOf(video, foto, viejo)) {
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(700.dp)) {
                        AlbumSheetContent(
                            subject = "Sentadilla trasera",
                            items = items,
                            loading = false,
                            effortFor = { "RIR 2" },
                            deferred = aplazado,
                            poseOverlayEnabled = false,
                            emptyText = "Todavía no hay nada.",
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun filas() = rule.onAllNodesWithTag(AlbumRowTag)

    private fun avanzar(milisegundos: Long) {
        reloj.advanceTimeBy(milisegundos)
        reloj.runCurrent()
        rule.waitForIdle()
    }

    @Test
    fun muestraElTituloElSubtituloYLosRegistrosAgrupadosPorFecha() {
        mostrar()

        rule.onNodeWithText("Álbum").assertExists()
        rule.onNodeWithText("Sentadilla trasera · 3 registros").assertExists()
        filas().assertCountEquals(3)
        rule.onNodeWithText("HOY ·", substring = true).assertExists()
        rule.onNodeWithText("Serie 3 · 95 kg × 6", substring = true).assertExists()
        // Los dos vídeos (el de hoy y el de hace tres días) nombran su esfuerzo y su tipo; la foto dice «Foto».
        rule.onAllNodesWithText("RIR 2 · Vídeo", substring = true).assertCountEquals(2)
        rule.onAllNodesWithText("RIR 2 · Foto", substring = true).assertCountEquals(1)
    }

    @Test
    fun losFiltrosSeparanFotosYVideos() {
        mostrar()

        rule.onNodeWithText("Fotos").performClick()
        rule.waitForIdle()
        filas().assertCountEquals(1)

        rule.onNodeWithText("Vídeos").performClick()
        rule.waitForIdle()
        filas().assertCountEquals(2)

        rule.onNodeWithText("Todo").performClick()
        rule.waitForIdle()
        filas().assertCountEquals(3)
    }

    @Test
    fun borrarOcultaElRegistroYMuestraDeshacerSinBorrarNada() {
        mostrar()

        rule.onAllNodesWithTag(AlbumDeleteTag).onFirst().performClick()
        rule.waitForIdle()

        filas().assertCountEquals(2)
        rule.onNodeWithText("Sentadilla trasera · 2 registros").assertExists()
        rule.onNodeWithText("Vídeo borrado").assertExists()
        rule.onNodeWithText("Deshacer").assertExists()
        assertTrue("al borrar no se toca nada todavía", borrados.isEmpty())
    }

    @Test
    fun deshacerLoDevuelveYNoBorraNadaNiAlCaducarLaVentana() {
        mostrar()
        rule.onAllNodesWithTag(AlbumDeleteTag).onFirst().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(AlbumUndoTag).performClick()
        rule.waitForIdle()

        filas().assertCountEquals(3)
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
        avanzar(60_000L)
        assertTrue("deshacer no borra nada, ni después", borrados.isEmpty())
    }

    @Test
    fun alCaducarElAvisoSeBorraDeVerdadYElAvisoDesaparece() {
        mostrar()
        rule.onAllNodesWithTag(AlbumDeleteTag).onFirst().performClick()
        rule.waitForIdle()
        avanzar(4_900L)
        assertTrue("a los 4,9 s aún no se borra", borrados.isEmpty())
        rule.onNodeWithText("Deshacer").assertExists()

        avanzar(200L)

        assertEquals(listOf(listOf("v1")), borrados)
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun alSalirDelAlbumSeBorraYaLoQueSeguiaEnEspera() {
        mostrar()
        rule.onAllNodesWithTag(AlbumDeleteTag).onFirst().performClick()
        rule.waitForIdle()
        assertTrue(borrados.isEmpty())

        aplazado.flush()

        assertEquals(listOf(listOf("v1")), borrados)
    }

    @Test
    fun seleccionarPermiteBorrarVariosConUnSoloAviso() {
        mostrar()

        rule.onNodeWithTag(AlbumSelectTag).performClick()
        rule.waitForIdle()
        // «Cancelar» sale en la cabecera y en el pie.
        rule.onAllNodesWithText("Cancelar").assertCountEquals(2)
        filas().onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(AlbumRowTag).get(1).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Borrar (2)").performClick()
        rule.waitForIdle()

        filas().assertCountEquals(1)
        rule.onNodeWithText("2 elementos borrados").assertExists()
        assertTrue("el borrado es aplazado también aquí", borrados.isEmpty())
        avanzar(5_100L)
        assertEquals(listOf(listOf("v1", "f1")), borrados)
    }

    @Test
    fun sinRegistrosSeDiceYNoSeOfreceSeleccionar() {
        mostrar(items = emptyList())

        rule.onNodeWithText("Todavía no hay nada.").assertExists()
        rule.onNodeWithText("Sentadilla trasera · Sin registros").assertExists()
        rule.onAllNodesWithTag(AlbumSelectTag).assertCountEquals(0)
    }
}
