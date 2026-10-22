package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.PartialSummary
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.work.TechniqueAnalysisWorker
import com.example.kpkn.data.work.TechniqueWorkTestKit
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La referencia PERSONAL de recorrido (M4b) sobre Room en memoria y sidecars de verdad: de qué análisis anteriores sale, cuáles se
 * descartan y que lo que la fila no guarda (la confianza propia de las parciales) se lee del sidecar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoAnalysisPersonalReferenceTest {
    @Rule @JvmField val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: VideoAnalysisRepository

    @Before fun setUp() {
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = VideoAnalysisRepository.forDatabase(context, db, store)
    }

    @After fun tearDown() {
        db.close()
        KpknDatabase.closeInstance()
    }

    private fun report(
        amplitude: Double?,
        signal: CountSignal? = CountSignal.DROP_HIP_MID,
        confidence: CountConfidence = CountConfidence.ALTA,
        engine: Int = 9,
    ): SetTechniqueReport = TechniqueReportSamples.fullReport().copy(
        engineVersion = engine, confidence = confidence, countSignal = signal, countUnit = SignalUnit.BODY_HEIGHTS, fullAmplitude = amplitude,
    )

    /** Un vídeo con su análisis DONE ya guardado (sidecar y fila), grabado en [at]. */
    private suspend fun done(
        id: String,
        at: Long,
        report: SetTechniqueReport,
        configure: (WorkoutMedia) -> WorkoutMedia = { it },
    ): WorkoutMedia {
        val media = TechniqueWorkTestKit.insertVideo(db, store, id) { configure(it.copy(createdAtMs = at)) }
        repository.completeDone(media, report, TechniqueAnalysisWorker.exerciseRefOf(media, null), 80.0, AnalysisMode.FULL)
        return media
    }

    private suspend fun current(configure: (WorkoutMedia) -> WorkoutMedia = { it }): WorkoutMedia =
        TechniqueWorkTestKit.insertVideo(db, store, "now") { configure(it.copy(createdAtMs = 9_000L)) }

    @Test fun zero_one_two_and_three_previous_analyses() = runBlocking<Unit> {
        val now = current()
        assertNull(repository.personalRangeReference(now))

        done("a", 1L, report(0.40))
        assertNull("una sola serie no basta", repository.personalRangeReference(now))

        done("b", 2L, report(0.50))
        val two = repository.personalRangeReference(now)!!
        assertEquals(0.45, two.amplitude, 1e-9)
        assertEquals(2, two.seriesCount)
        assertEquals(RangeReferenceSource.PERSONAL, two.source)
        assertEquals(CountSignal.DROP_HIP_MID, two.signal)

        done("c", 3L, report(0.90))
        val three = repository.personalRangeReference(now)!!
        assertEquals(0.50, three.amplitude, 1e-9)
        assertEquals(3, three.seriesCount)
    }

    @Test fun the_video_itself_is_not_counted_even_when_it_already_has_an_analysis() = runBlocking<Unit> {
        done("a", 1L, report(0.40))
        val own = done("own", 2L, report(5.00))
        assertNull(repository.personalRangeReference(own))
        done("b", 3L, report(0.50))
        val reference = repository.personalRangeReference(own)!!
        assertEquals(2, reference.seriesCount)
        assertEquals(0.45, reference.amplitude, 1e-9)
    }

    @Test fun another_count_signal_is_not_pooled() = runBlocking<Unit> {
        val now = current()
        done("a", 1L, report(0.40))
        done("b", 2L, report(0.50, signal = CountSignal.RISE_HAND))
        assertNull(repository.personalRangeReference(now))
        done("c", 3L, report(0.60))
        val reference = repository.personalRangeReference(now)!!
        assertEquals(CountSignal.DROP_HIP_MID, reference.signal)
        assertEquals(2, reference.seriesCount)
        assertEquals(0.50, reference.amplitude, 1e-9)
    }

    @Test fun only_the_same_catalog_configuration_counts_or_the_same_exercise_when_the_video_has_none() = runBlocking<Unit> {
        val dumbbell = { media: WorkoutMedia -> media.copy(catalogConfigurationId = "back_squat__dumbbell") }
        done("a", 1L, report(0.40))
        done("b", 2L, report(0.50), dumbbell)
        done("c", 3L, report(0.60), dumbbell)

        val barbell = current()
        assertNull("solo una serie con barra", repository.personalRangeReference(barbell))
        val dumbbellNow = TechniqueWorkTestKit.insertVideo(db, store, "now-dumbbell") { dumbbell(it.copy(createdAtMs = 9_050L)) }
        val withDumbbell = repository.personalRangeReference(dumbbellNow)!!
        assertEquals(2, withDumbbell.seriesCount)
        assertEquals(0.55, withDumbbell.amplitude, 1e-9)

        // Sin configuración en el vídeo, vale todo el ejercicio.
        val noConfiguration = TechniqueWorkTestKit.insertVideo(db, store, "no-config") { it.copy(createdAtMs = 9_100L, catalogConfigurationId = null) }
        assertEquals(3, repository.personalRangeReference(noConfiguration)!!.seriesCount)
    }

    @Test fun low_confidence_and_older_engine_analyses_are_left_out() = runBlocking<Unit> {
        val now = current()
        done("a", 1L, report(0.40))
        done("low", 2L, report(0.50, confidence = CountConfidence.BAJA))
        done("old", 3L, report(0.60, engine = 8))
        assertNull(repository.personalRangeReference(now))
        done("medium", 4L, report(0.70, confidence = CountConfidence.MEDIA))
        val reference = repository.personalRangeReference(now)!!
        assertEquals(2, reference.seriesCount)
        assertEquals(0.55, reference.amplitude, 1e-9)
    }

    @Test fun the_sidecar_gives_the_own_confidence_and_the_shared_zone_of_the_partials() = runBlocking<Unit> {
        val base = TechniqueReportSamples.fullReport()
        val partial = base.reps.first { it.kind == RepKind.PARTIAL }
        val withZones = base.copy(
            reps = base.reps.filter { it.kind != RepKind.PARTIAL } + listOf(10, 11).map { partial.copy(index = it, zone = PartialZone.STRETCHED) },
            partialReps = 2,
            partialConfidence = CountConfidence.MEDIA,
        )
        val media = done("p", 1L, withZones)
        val path = repository.getByMedia("p")!!.reportPath!!
        assertEquals(PartialSummary(CountConfidence.MEDIA, PartialZone.STRETCHED), repository.partialSummaryAt(path))
        assertNull("sin sidecar no hay resumen", repository.partialSummaryAt(File(folder.root, "no-existe.analysis.json").absolutePath))
        assertEquals(TechniqueSidecars.analysisFile(File(media.filePath)).absolutePath, path)
    }
}
