package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Rutas, escritura atómica y lectura de los sidecars del análisis (§3.8). JVM puro: solo `java.io`. */
class TechniqueSidecarsTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val report = TechniqueReportSamples.fullReport()
    private val exercise = TechniqueReportSamples.exercise()

    private fun sidecar(report: SetTechniqueReport = this.report) = AnalysisSidecar(
        report = report,
        exercise = exercise,
        loadKg = 102.5,
        setKey = "squat_2_L",
        analyzedAtMs = 1_780_000_123_456L,
    )

    @Test
    fun the_sidecars_live_next_to_the_video_and_are_named_after_its_id() {
        val video = File(folder.root, "workout_media/2026-10/abc-123.mp4")

        assertEquals("abc-123", TechniqueSidecars.mediaId(video))
        assertEquals(File(video.parentFile, "abc-123.pose2.json.gz"), TechniqueSidecars.poseFile(video))
        assertEquals(File(video.parentFile, "abc-123.bar.json.gz"), TechniqueSidecars.barFile(video))
        assertEquals(File(video.parentFile, "abc-123.analysis.json"), TechniqueSidecars.analysisFile(video))
        assertEquals(File(video.parentFile, "abc-123.capture.json"), TechniqueSidecars.captureFile(video))
        assertEquals(File(video.parentFile, "abc-123.pose.part"), TechniqueSidecars.partFile(video))

        // Las mismas rutas a partir de la carpeta y el id (las usa quien recorre la carpeta sin el vídeo).
        val directory = video.parentFile!!
        assertEquals(TechniqueSidecars.poseFile(video), TechniqueSidecars.poseFile(directory, "abc-123"))
        assertEquals(TechniqueSidecars.barFile(video), TechniqueSidecars.barFile(directory, "abc-123"))
        assertEquals(TechniqueSidecars.analysisFile(video), TechniqueSidecars.analysisFile(directory, "abc-123"))
        assertEquals(TechniqueSidecars.captureFile(video), TechniqueSidecars.captureFile(directory, "abc-123"))
        assertEquals(TechniqueSidecars.partFile(video), TechniqueSidecars.partFile(directory, "abc-123"))
    }

    @Test
    fun a_video_with_dots_in_its_name_keeps_everything_before_the_extension() {
        val video = File(folder.root, "a.b.c.mp4")

        assertEquals("a.b.c", TechniqueSidecars.mediaId(video))
        assertEquals("a.b.c.analysis.json", TechniqueSidecars.analysisFile(video).name)
    }

    @Test
    fun allFiles_lists_the_five_sidecars_and_their_temporaries() {
        val video = File(folder.root, "m1.mp4")

        val names = TechniqueSidecars.allFiles(video).map { it.name }

        assertEquals(
            listOf(
                "m1.pose2.json.gz", "m1.pose2.json.gz.tmp",
                "m1.bar.json.gz", "m1.bar.json.gz.tmp",
                "m1.analysis.json", "m1.analysis.json.tmp",
                "m1.capture.json", "m1.capture.json.tmp",
                "m1.pose.part", "m1.pose.part.tmp",
            ),
            names,
        )
    }

    @Test
    fun sidecarMediaId_recognises_the_sidecars_and_their_temporaries_by_name() {
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.pose2.json.gz")))
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.bar.json.gz")))
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.analysis.json")))
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.capture.json")))
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.pose.part")))
        assertEquals("m1", TechniqueSidecars.sidecarMediaId(File("/x/m1.analysis.json.tmp")))
        assertEquals("a.b", TechniqueSidecars.sidecarMediaId(File("/x/a.b.analysis.json")))
        // Lo que NO es un sidecar de la técnica: el vídeo, la miniatura y la pose antigua (`.pose.json`).
        assertNull(TechniqueSidecars.sidecarMediaId(File("/x/m1.mp4")))
        assertNull(TechniqueSidecars.sidecarMediaId(File("/x/m1.jpg")))
        assertNull(TechniqueSidecars.sidecarMediaId(File("/x/m1.pose.json")))
        assertNull(TechniqueSidecars.sidecarMediaId(File("/x/.analysis.json")))
    }

    @Test
    fun the_analysis_sidecar_round_trips_the_report_the_exercise_and_the_set() {
        val file = File(folder.root, "workout_media/2026-10/m1.analysis.json")

        TechniqueSidecars.writeAnalysis(file, sidecar())
        val back = TechniqueSidecars.readAnalysis(file)

        assertEquals(sidecar(), back)
        assertEquals(report, back?.report)
        assertEquals(exercise, back?.exercise)
        assertEquals(102.5, back?.loadKg ?: 0.0, 0.0)
        assertEquals("squat_2_L", back?.setKey)
        assertEquals(1_780_000_123_456L, back?.analyzedAtMs)
        assertFalse("no debe quedar el temporal", File(file.parentFile, "m1.analysis.json.tmp").exists())
    }

    @Test
    fun writing_a_sidecar_caps_partial_confidence_at_the_overall_confidence() {
        val file = File(folder.root, "m1.analysis.json")
        val inconsistent = report.copy(
            confidence = CountConfidence.MEDIA,
            partialConfidence = CountConfidence.ALTA,
        )

        TechniqueSidecars.writeAnalysis(file, sidecar(inconsistent))

        assertTrue(file.readText(Charsets.UTF_8).contains("\"partialConfidence\":\"MEDIA\""))
        assertEquals(CountConfidence.MEDIA, TechniqueSidecars.readAnalysis(file)?.report?.partialConfidence)
    }

    @Test
    fun reading_a_historical_sidecar_caps_only_partial_confidence_and_does_not_rewrite_it() {
        val file = File(folder.root, "historical.analysis.json")
        val inconsistent = report.copy(
            confidence = CountConfidence.MEDIA,
            partialConfidence = CountConfidence.ALTA,
            reps = report.reps.map { rep ->
                if (rep.kind == RepKind.PARTIAL) rep.copy(
                    zone = PartialZone.STRETCHED,
                    amplitudeFraction = 0.55,
                    amplitude = 0.44,
                ) else rep
            },
        )
        val json = Json {
            encodeDefaults = true
            allowSpecialFloatingPointValues = true
        }
        val originalBytes = json.encodeToString(AnalysisSidecar.serializer(), sidecar(inconsistent))
        file.writeText(originalBytes, Charsets.UTF_8)

        val back = TechniqueSidecars.readAnalysis(file)!!

        assertEquals(CountConfidence.MEDIA, back.report.partialConfidence)
        assertEquals(CountConfidence.MEDIA, back.report.confidence)
        assertEquals(inconsistent.repCount, back.report.repCount)
        assertEquals(inconsistent.partialReps, back.report.partialReps)
        assertEquals(inconsistent.reps, back.report.reps)
        assertEquals(inconsistent.fullAmplitude, back.report.fullAmplitude)
        assertEquals(
            "la lectura no modifica el sidecar guardado",
            originalBytes,
            file.readText(Charsets.UTF_8),
        )
    }

    @Test
    fun normalization_preserves_low_and_legacy_unknown_partial_confidence() {
        val low = report.copy(confidence = CountConfidence.MEDIA, partialConfidence = CountConfidence.BAJA)
        val globalLow = report.copy(confidence = CountConfidence.BAJA, partialConfidence = CountConfidence.ALTA)
        val legacy = report.copy(confidence = CountConfidence.MEDIA, partialConfidence = null)
        val high = report.copy(confidence = CountConfidence.ALTA, partialConfidence = CountConfidence.ALTA)
        val mediumPartial = report.copy(confidence = CountConfidence.ALTA, partialConfidence = CountConfidence.MEDIA)

        assertEquals(CountConfidence.BAJA, low.capPartialConfidenceAtOverall().partialConfidence)
        assertEquals(globalLow.copy(partialConfidence = CountConfidence.BAJA), globalLow.capPartialConfidenceAtOverall())
        assertNull(legacy.capPartialConfidenceAtOverall().partialConfidence)
        assertEquals(CountConfidence.MEDIA, legacy.capPartialConfidenceAtOverall().confidence)
        assertEquals(high, high.capPartialConfidenceAtOverall())
        assertEquals(mediumPartial, mediumPartial.capPartialConfidenceAtOverall())
    }

    @Test
    fun the_long_form_of_writeAnalysis_writes_the_same_file() {
        val a = File(folder.root, "a.analysis.json")
        val b = File(folder.root, "b.analysis.json")

        TechniqueSidecars.writeAnalysis(a, sidecar())
        TechniqueSidecars.writeAnalysis(b, report, exercise, 102.5, "squat_2_L", 1_780_000_123_456L)

        assertEquals(a.readText(), b.readText())
    }

    @Test
    fun the_analysis_json_has_the_documented_layout_and_stores_enums_by_name() {
        val file = File(folder.root, "m1.analysis.json")

        TechniqueSidecars.writeAnalysis(file, sidecar())
        val text = file.readText(Charsets.UTF_8)

        // {"summaryVersion":1,"report":…,"exercise":…,"loadKg":…,"setKey":…,"analyzedAtMs":…}
        val keys = listOf("\"summaryVersion\":1", "\"report\":{", "\"exercise\":{", "\"loadKg\":102.5", "\"setKey\":\"squat_2_L\"", "\"analyzedAtMs\":1780000123456")
        val positions = keys.map { text.indexOf(it) }
        assertTrue("faltan claves en $text", positions.all { it >= 0 })
        assertEquals("las claves van en el orden documentado", positions.sorted(), positions)
        assertTrue(text.startsWith("{\"summaryVersion\":1,"))
        for (name in listOf("\"SQUAT\"", "\"ALTA\"", "\"SIDE\"", "\"MPS\"", "\"PLATE\"", "\"PLATE_ELLIPSE\"", "\"back_squat__barbell\"")) {
            assertTrue("falta $name", name in text)
        }
        assertFalse("compacto, sin saltos de línea", '\n' in text)
    }

    @Test
    fun a_missing_empty_or_corrupt_sidecar_reads_as_null_and_never_throws() {
        assertNull(TechniqueSidecars.readAnalysis(File(folder.root, "missing.analysis.json")))
        assertNull(TechniqueSidecars.readAnalysis(folder.root)) // una carpeta no es un sidecar

        val broken = listOf(
            "",
            "   \n",
            "{",
            "[]",
            "null",
            "no es json",
            """{"summaryVersion":1}""",
            """{"summaryVersion":1,"report":{},"exercise":{},"analyzedAtMs":1}""",
        )
        for ((index, content) in broken.withIndex()) {
            val file = folder.newFile("broken-$index.analysis.json")
            file.writeText(content)
            assertNull("contenido $index: «$content»", TechniqueSidecars.readAnalysis(file))
        }

        // Cortado a la mitad, como lo dejaría un corte de luz con una escritura no atómica.
        val full = File(folder.root, "full.analysis.json")
        TechniqueSidecars.writeAnalysis(full, sidecar())
        val half = folder.newFile("half.analysis.json")
        val text = full.readText()
        half.writeText(text.substring(0, text.length / 2))
        assertNull(TechniqueSidecars.readAnalysis(half))
    }

    @Test
    fun an_enum_name_this_version_does_not_know_reads_as_null() {
        val file = File(folder.root, "m1.analysis.json")
        TechniqueSidecars.writeAnalysis(file, sidecar())
        file.writeText(file.readText().replace("\"SQUAT\"", "\"BARRA_FIJA_DEL_FUTURO\""))

        assertNull(TechniqueSidecars.readAnalysis(file))
    }

    @Test
    fun a_sidecar_from_a_newer_format_is_not_read_and_an_unknown_field_is_ignored() {
        val file = File(folder.root, "m1.analysis.json")
        TechniqueSidecars.writeAnalysis(file, sidecar())
        val text = file.readText()

        file.writeText(text.replace("\"summaryVersion\":1", "\"summaryVersion\":${TechniqueSidecars.SUMMARY_VERSION + 1}"))
        assertNull("una versión posterior no se interpreta", TechniqueSidecars.readAnalysis(file))
        file.writeText(text.replace("\"summaryVersion\":1", "\"summaryVersion\":0"))
        assertNull(TechniqueSidecars.readAnalysis(file))

        file.writeText(text.replaceFirst("{", "{\"campoNuevo\":{\"a\":1},"))
        assertEquals(sidecar(), TechniqueSidecars.readAnalysis(file))
    }

    @Test
    fun a_stray_nan_does_not_lose_the_whole_analysis() {
        val withNan = report.copy(
            mcvBest = Double.NaN,
            reps = report.reps.map { it.copy(mcv = Double.NaN, romDeg = Double.POSITIVE_INFINITY) },
        )
        val file = File(folder.root, "m1.analysis.json")

        TechniqueSidecars.writeAnalysis(file, sidecar(withNan))
        val back = TechniqueSidecars.readAnalysis(file)

        assertNotNull(back)
        assertTrue(back!!.report.mcvBest!!.isNaN())
        assertTrue(back.report.reps.all { it.mcv!!.isNaN() && it.romDeg == Double.POSITIVE_INFINITY })
        assertEquals(report.repCount, back.report.repCount)
    }

    @Test
    fun writeAtomically_creates_the_folder_and_replaces_the_previous_content() {
        val file = File(folder.root, "workout_media/2026-10/m1.bar.json.gz")

        TechniqueSidecars.writeAtomically(file, byteArrayOf(1, 2, 3))
        assertEquals(listOf<Byte>(1, 2, 3), file.readBytes().toList())

        TechniqueSidecars.writeAtomically(file, byteArrayOf(9))
        assertEquals(listOf<Byte>(9), file.readBytes().toList())
        assertEquals(listOf("m1.bar.json.gz"), file.parentFile!!.list()!!.sorted())

        TechniqueSidecars.writeAtomically(file, ByteArray(0))
        assertTrue("un archivo vacío también es válido", file.isFile && file.length() == 0L)
    }

    @Test
    fun the_new_content_is_complete_in_the_temporary_before_it_replaces_the_old_one() {
        val file = File(folder.root, "m1.analysis.json")
        file.writeText("anterior")

        val staged = TechniqueSidecars.stage(file, "nuevo".toByteArray())

        // Hasta el renombrado, quien lee el sidecar sigue viendo el contenido anterior entero.
        assertEquals("m1.analysis.json.tmp", staged.name)
        assertEquals("anterior", file.readText())
        assertEquals("nuevo", staged.readText())

        TechniqueSidecars.commit(staged, file)

        assertEquals("nuevo", file.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun a_failed_replace_keeps_what_was_there_and_leaves_no_temporary() {
        // El destino es una carpeta con contenido: no se puede reemplazar por un archivo.
        val blocked = File(folder.root, "blocked.analysis.json")
        check(blocked.mkdirs())
        File(blocked, "inner.txt").writeText("no tocar")

        assertThrows(IOException::class.java) { TechniqueSidecars.writeAtomically(blocked, byteArrayOf(1)) }

        assertTrue(blocked.isDirectory)
        assertEquals("no tocar", File(blocked, "inner.txt").readText())
        assertFalse(File(folder.root, "blocked.analysis.json.tmp").exists())
    }

    @Test
    fun a_commit_that_cannot_place_the_new_content_gives_back_the_previous_one() {
        val target = File(folder.root, "m1.analysis.json").apply { writeText("anterior") }

        // El temporal no existe: el renombrado falla justo después de apartar el destino, y lo apartado vuelve a su sitio.
        assertThrows(IOException::class.java) { TechniqueSidecars.commit(File(folder.root, "no-existe.tmp"), target) }

        assertEquals("anterior", target.readText())
        assertEquals("sin restos de la copia apartada", listOf("m1.analysis.json"), folder.root.list()!!.sorted())
    }

    @Test
    fun a_parent_that_is_a_file_fails_with_io_exception_and_writes_nothing() {
        val notAFolder = folder.newFile("not-a-folder")

        assertThrows(IOException::class.java) {
            TechniqueSidecars.writeAtomically(File(notAFolder, "m1.analysis.json"), byteArrayOf(1))
        }

        assertTrue(notAFolder.isFile)
    }

    @Test
    fun deleteAll_removes_every_sidecar_and_leaves_the_video_and_other_media_alone() {
        val directory = folder.newFolder("workout_media", "2026-10")
        val video = File(directory, "m1.mp4").apply { writeText("video") }
        val other = File(directory, "m2.mp4").apply { writeText("otro") }
        val otherSidecar = TechniqueSidecars.analysisFile(other).apply { writeText("{}") }
        val oldPose = File(directory, "m1.pose.json").apply { writeText("pose antigua") }
        for (file in TechniqueSidecars.allFiles(video)) file.writeText("x")

        val deleted = TechniqueSidecars.deleteAll(video)

        assertEquals(10, deleted)
        assertTrue(TechniqueSidecars.allFiles(video).none { it.exists() })
        assertTrue("el vídeo no es un sidecar", video.isFile)
        assertTrue(other.isFile && otherSidecar.isFile)
        assertTrue("la traza antigua tiene su propio borrado", oldPose.isFile)
        assertEquals(0, TechniqueSidecars.deleteAll(video))
    }

    @Test
    fun deleteAll_only_touches_the_files_the_filter_accepts() {
        val directory = folder.newFolder("media")
        val video = File(directory, "m1.mp4")
        val analysis = TechniqueSidecars.analysisFile(video).apply { writeText("{}") }
        val pose = TechniqueSidecars.poseFile(video).apply { writeText("x") }

        val deleted = TechniqueSidecars.deleteAll(video) { it.name.endsWith(".analysis.json") }

        assertEquals(1, deleted)
        assertFalse(analysis.exists())
        assertTrue(pose.exists())
    }
}
