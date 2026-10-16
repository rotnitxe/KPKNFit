package com.example.kpkn.data.media

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El álbum de un ejercicio (LV8, A4): los medios entre sesiones del más reciente al más antiguo, filtrados por tipo, agrupados por día con
 * las cabeceras del prototipo («HOY · JUEVES 15 OCT») y con los textos de cada registro («Serie 3 · 95 kg × 6», «RIR 2 · Vídeo · 18:42»).
 */
class ExerciseAlbumModelTest {

    private val zona = ZoneOffset.UTC

    /** El espacio inseparable (U+00A0) que lleva la carga antes de «kg». */
    private val NBSP = Char(0x00A0)

    // Jueves 15 de octubre de 2026, el «hoy» del prototipo.
    private val hoy = momento(2026, 10, 15, 20, 0)

    private fun momento(anio: Int, mes: Int, dia: Int, hora: Int, minuto: Int): Long =
        LocalDateTime.of(anio, mes, dia, hora, minuto).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun medio(
        id: String,
        kind: WorkoutMediaKind,
        cuando: Long,
        serie: Int? = null,
        peso: Double? = null,
        reps: Int? = null,
        marca: Boolean = false,
        duracionMs: Long? = null,
    ) = WorkoutMedia(
        id = id,
        kind = kind,
        filePath = "/medios/$id",
        createdAtMs = cuando,
        exerciseId = "sentadilla",
        canonicalExerciseId = "sentadilla",
        exerciseName = "Sentadilla trasera",
        setIndex = serie,
        weightKg = peso,
        reps = reps,
        isPr = marca,
        durationMs = duracionMs,
    )

    private val videoHoy = medio("v-hoy", WorkoutMediaKind.VIDEO, momento(2026, 10, 15, 18, 42), serie = 2, peso = 95.0, reps = 6, duracionMs = 14_000L)
    private val fotoHoy = medio("f-hoy", WorkoutMediaKind.PHOTO, momento(2026, 10, 15, 18, 31), serie = 0, peso = 95.0, reps = 6)
    private val videoLunes = medio("v-lunes", WorkoutMediaKind.VIDEO, momento(2026, 10, 12, 19, 5), serie = 3, peso = 92.5, reps = 5, marca = true, duracionMs = 21_000L)
    private val todos = listOf(fotoHoy, videoLunes, videoHoy)

    private fun dias(filtro: AlbumFilter = AlbumFilter.ALL, ocultos: Set<String> = emptySet(), esfuerzo: (WorkoutMedia) -> String? = { null }) =
        ExerciseAlbumModel.days(todos, filtro, ocultos, nowMs = hoy, zone = zona, effortFor = esfuerzo)

    @Test
    fun losDiasVanDelMasRecienteAlMasAntiguoConLasCabecerasDelPrototipo() {
        val dias = dias()

        assertEquals(listOf("HOY · JUEVES 15 OCT", "LUNES 12 OCT"), dias.map { it.header })
        assertEquals("dentro de un día, el más reciente primero", listOf("v-hoy", "f-hoy"), dias[0].entries.map { it.media.id })
    }

    @Test
    fun elOrdenNoDependeDelOrdenDeEntrada() {
        val barajados = ExerciseAlbumModel.days(listOf(videoLunes, videoHoy, fotoHoy), AlbumFilter.ALL, nowMs = hoy, zone = zona)
        assertEquals(dias().map { it.header }, barajados.map { it.header })
        assertEquals(dias().flatMap { d -> d.entries.map { it.media.id } }, barajados.flatMap { d -> d.entries.map { it.media.id } })
    }

    @Test
    fun cadaRegistroDiceLaSerieLaCargaElEsfuerzoElTipoYLaHora() {
        val entrada = dias(esfuerzo = { "RIR 2" })[0].entries[0]

        assertEquals("Serie 3 · 95${NBSP}kg × 6", entrada.title)
        assertEquals("RIR 2 · Vídeo · 18:42", entrada.detail)
        assertEquals("0:14", entrada.durationLabel)
        assertFalse(entrada.isMark)
    }

    @Test
    fun laCargaLlevaEspacioInseparableAntesDeKgYComaDecimal() {
        val marca = dias()[1].entries.single()

        assertEquals("Serie 4 · 92,5${NBSP}kg × 5", marca.title)
        assertEquals("el espacio antes de «kg» es inseparable", NBSP, marca.title[marca.title.indexOf("kg") - 1])
        assertTrue("una marca de récord se destaca", marca.isMark)
        assertEquals("0:21", marca.durationLabel)
    }

    @Test
    fun unaFotoNoTieneDuracionYSeLlamaFoto() {
        val foto = dias()[0].entries[1]

        assertEquals("Foto · 18:31", foto.detail)
        assertNull(foto.durationLabel)
    }

    @Test
    fun sinEsfuerzoConocidoElRegistroNoLoNombra() {
        val detalle = dias()[0].entries[0].detail

        assertEquals("Vídeo · 18:42", detalle)
        assertFalse(detalle.contains("RIR"))
    }

    @Test
    fun losFiltrosSeparanFotosYVideos() {
        assertEquals(listOf("f-hoy"), dias(AlbumFilter.PHOTOS).flatMap { d -> d.entries.map { it.media.id } })
        assertEquals(listOf("v-hoy", "v-lunes"), dias(AlbumFilter.VIDEOS).flatMap { d -> d.entries.map { it.media.id } })
        assertEquals(3, dias(AlbumFilter.ALL).sumOf { it.entries.size })
        assertEquals("un filtro sin registros no deja días vacíos", 1, dias(AlbumFilter.PHOTOS).size)
    }

    @Test
    fun loBorradoEnEsperaNoSeVe() {
        val dias = dias(ocultos = setOf("v-hoy", "v-lunes"))

        assertEquals(listOf("HOY · JUEVES 15 OCT"), dias.map { it.header })
        assertEquals(listOf("f-hoy"), dias.single().entries.map { it.media.id })
    }

    @Test
    fun conOtroAnioLaCabeceraLlevaElAnio() {
        val viejo = medio("v-viejo", WorkoutMediaKind.VIDEO, momento(2025, 10, 13, 8, 0))

        val cabecera = ExerciseAlbumModel.days(listOf(viejo), AlbumFilter.ALL, nowMs = hoy, zone = zona).single().header

        assertEquals("LUNES 13 OCT 2025", cabecera)
    }

    @Test
    fun laCabeceraDeHoyYLaDeOtroDiaSeEscribenComoElPrototipo() {
        val hoyFecha = LocalDate.of(2026, 10, 15)
        assertEquals("HOY · JUEVES 15 OCT", ExerciseAlbumModel.dayHeader(hoyFecha, hoyFecha))
        assertEquals("DOMINGO 4 ENE", ExerciseAlbumModel.dayHeader(LocalDate.of(2026, 1, 4), hoyFecha))
        assertEquals("MIÉRCOLES 2 SEP", ExerciseAlbumModel.dayHeader(LocalDate.of(2026, 9, 2), hoyFecha))
    }

    @Test
    fun elRecuentoYElSubtituloEstanEnEspanol() {
        assertEquals("9 registros", ExerciseAlbumModel.countLabel(9))
        assertEquals("1 registro", ExerciseAlbumModel.countLabel(1))
        assertEquals("Sin registros", ExerciseAlbumModel.countLabel(0))
        assertEquals("Sentadilla trasera · 9 registros", ExerciseAlbumModel.heading("Sentadilla trasera", 9))
    }

    @Test
    fun laCargaSeDiceConLoQueSeSabe() {
        assertEquals("80 kg × 8", ExerciseAlbumModel.loadLabel(80.0, 8)!!.replace(' ', ' '))
        assertEquals("80 kg", ExerciseAlbumModel.loadLabel(80.0, null)!!.replace(' ', ' '))
        assertEquals("8 reps", ExerciseAlbumModel.loadLabel(null, 8))
        assertNull(ExerciseAlbumModel.loadLabel(null, null))
        assertNull(ExerciseAlbumModel.loadLabel(0.0, 0))
    }

    @Test
    fun unMedioSinSerieNiCargaSeNombraPorSuEjercicio() {
        val suelto = medio("suelto", WorkoutMediaKind.PHOTO, momento(2026, 10, 15, 9, 0))

        assertEquals("Sentadilla trasera", ExerciseAlbumModel.entryOf(suelto, zona).title)
    }

    @Test
    fun laSerieDeUnMedioSeBuscaConLaClaveQueUsaLaSesion() {
        assertEquals("sentadilla_2", ExerciseAlbumModel.completedSetKey(videoHoy))
        assertEquals("sentadilla_2_L", ExerciseAlbumModel.completedSetKey(videoHoy.copy(side = "left")))
        assertEquals("sentadilla_2_R", ExerciseAlbumModel.completedSetKey(videoHoy.copy(side = "right")))
        assertNull(ExerciseAlbumModel.completedSetKey(videoHoy.copy(setIndex = null)))
        assertNull(ExerciseAlbumModel.completedSetKey(videoHoy.copy(exerciseId = null)))
    }

    @Test
    fun elEsfuerzoDeUnaSerieRegistradaSeEscribeComoRirRpeOFallo() {
        assertEquals("RIR 2", ExerciseAlbumModel.effortLabel(CompletedSet(id = "a", actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 2.0)))
        assertEquals("RPE 7,5", ExerciseAlbumModel.effortLabel(CompletedSet(id = "b", actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 7.5)))
        assertEquals("RPE 8", ExerciseAlbumModel.effortLabel(CompletedSet(id = "c", actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0)))
        assertEquals("Fallo", ExerciseAlbumModel.effortLabel(CompletedSet(id = "d", isFailure = true)))
        assertEquals("Fallo", ExerciseAlbumModel.effortLabel(CompletedSet(id = "e", actualIntensityMode = IntensityMode.FAILURE, actualIntensityValue = 10.0)))
        assertEquals("RIR 1", ExerciseAlbumModel.effortLabel(CompletedSet(id = "f", rir = 1)))
        assertNull("sin esfuerzo registrado no se inventa", ExerciseAlbumModel.effortLabel(CompletedSet(id = "g")))
    }
}
