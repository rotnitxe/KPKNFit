package com.example.kpkn.domain.training

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresión del acceso real de la pantalla: la lista de definiciones no contiene las configuraciones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramAnalyticsV2IndexTest {
    @Test
    fun `el acceso por defecto resuelve configuraciones aprobadas ausentes de la lista por definicion`() {
        initializeExerciseDatabase(ApplicationProvider.getApplicationContext<Context>())
        val index = catalogExerciseIndex()
        val snapshot = exerciseCatalogSnapshot()
        val configuration = index.values.first { info ->
            info.id == info.catalogConfigurationId && info.id != info.catalogDefinitionId &&
                info.involvedMuscles.any(VolumeCalculator::isStandardVolumeMuscle)
        }
        assertTrue(snapshot.none { it.id == configuration.id })
        val exercise = Exercise(
            id = "ocurrencia", name = configuration.name,
            catalogConfigurationId = configuration.id, catalogDefinitionId = configuration.catalogDefinitionId,
            sets = List(3) { ExerciseSet("serie-$it", targetReps = 8, weight = 40.0) },
        )
        val week = TrainingVolumeFixtures.week("v2", TrainingVolumeFixtures.today).copy(
            sessions = listOf(Session("sesion", "Sesión", exercises = listOf(exercise))),
        )
        val report = ProgramAnalyticsEngine.analyze(TrainingVolumeFixtures.program(listOf(week)), emptyList(), snapshot)
        val volume = VolumeCalculator.calculateVolume(week.sessions, index)
        assertTrue(report.coverage.musclesByWeeklySets.isNotEmpty())
        assertEquals(volume.muscles.keys, report.coverage.musclesByWeeklySets.map { it.name }.toSet())
    }
}
