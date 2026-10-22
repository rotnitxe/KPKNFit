package com.example.kpkn.data.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueAnalysisOutcome
import com.example.kpkn.data.media.technique.TechniqueAnalysisRequest
import com.example.kpkn.data.media.technique.TechniqueAnalyzer
import com.example.kpkn.data.media.technique.TechniqueAnalyzerProvider
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.domain.biomechanics.metrics.RangeReference
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La referencia PERSONAL de recorrido (M4b) viaja del repositorio a la petición del analizador, en los tres modos y, en
 * particular, también en RECOMPUTE (lo que se pide al subir el motor).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class TechniqueAnalysisWorkerReferenceTest {
    @Rule @JvmField val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultThermal = AnalysisGate.thermalStatus
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var repository: VideoAnalysisRepository
    private val requests = CopyOnWriteArrayList<TechniqueAnalysisRequest>()

    @Before fun setUp() {
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        repository = VideoAnalysisRepository.forDatabase(context, db, store)
        VideoAnalysisRepository.installForTests(repository)
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = { -1 }
        TechniqueAnalyzerProvider.overrideForTests(object : TechniqueAnalyzer {
            override suspend fun analyze(
                request: TechniqueAnalysisRequest,
                onProgress: suspend (percent: Int) -> Unit,
            ): TechniqueAnalysisOutcome {
                requests += request
                return TechniqueAnalysisOutcome.Done(report(0.40))
            }
        })
    }

    @After fun tearDown() {
        TechniqueAnalyzerProvider.overrideForTests(null)
        VideoAnalysisRepository.installForTests(null)
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = defaultThermal
        db.close()
        KpknDatabase.closeInstance()
    }

    private fun report(amplitude: Double): SetTechniqueReport = TechniqueReportSamples.fullReport().copy(
        countSignal = CountSignal.DROP_HIP_MID, countUnit = SignalUnit.BODY_HEIGHTS, fullAmplitude = amplitude,
    )

    private suspend fun prior(id: String, at: Long, amplitude: Double): WorkoutMedia {
        val media = TechniqueWorkTestKit.insertVideo(db, store, id) { it.copy(createdAtMs = at) }
        repository.completeDone(
            media, report(amplitude), TechniqueAnalysisWorker.exerciseRefOf(media, null), 80.0, AnalysisMode.FULL,
        )
        return media
    }

    private fun work(mediaId: String, mode: AnalysisMode) =
        TestListenableWorkerBuilder<TechniqueAnalysisWorker>(context)
            .setInputData(
                workDataOf(
                    TechniqueAnalysisWorker.KEY_MEDIA_ID to mediaId,
                    TechniqueAnalysisWorker.KEY_MODE to mode.name,
                    TechniqueAnalysisWorker.KEY_USER_INITIATED to true,
                    TechniqueAnalysisWorker.KEY_EXPLICIT to true,
                ),
            )
            .build()

    @Test fun the_request_carries_the_median_of_the_previous_series_in_full_and_in_recompute() = runBlocking<Unit> {
        prior("p1", 1L, 0.30)
        prior("p2", 2L, 0.34)
        prior("p3", 3L, 0.32)
        TechniqueWorkTestKit.insertVideo(db, store, "now") { it.copy(createdAtMs = 9L) }
        val expected = RangeReference(0.32, CountSignal.DROP_HIP_MID, RangeReferenceSource.PERSONAL, 3, view = null)

        assertEquals(ListenableWorker.Result.success(), work("now", AnalysisMode.FULL).doWork())
        assertEquals(expected, requests.last().rangeReference)

        // Ahora «now» ya tiene su propio análisis (0,40): no cuenta consigo mismo en el RECOMPUTE.
        assertEquals(ListenableWorker.Result.success(), work("now", AnalysisMode.RECOMPUTE).doWork())
        assertEquals(AnalysisMode.RECOMPUTE, requests.last().mode)
        assertEquals(expected, requests.last().rangeReference)
    }

    @Test fun fewer_than_two_previous_series_leave_the_request_without_a_reference() = runBlocking<Unit> {
        prior("p1", 1L, 0.30)
        TechniqueWorkTestKit.insertVideo(db, store, "now") { it.copy(createdAtMs = 9L) }

        assertEquals(ListenableWorker.Result.success(), work("now", AnalysisMode.FULL).doWork())
        assertNull(requests.last().rangeReference)
        // Con el propio análisis de «now» ya guardado siguen siendo una sola serie ajena.
        assertEquals(ListenableWorker.Result.success(), work("now", AnalysisMode.RECOMPUTE).doWork())
        assertNull(requests.last().rangeReference)
    }
}
