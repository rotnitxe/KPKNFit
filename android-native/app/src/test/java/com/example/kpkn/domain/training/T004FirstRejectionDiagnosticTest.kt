package com.example.kpkn.domain.training

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fase diagnóstica acotada de T-004 (plan kpknfit-wizard-cierre-20260927):
 * registra, PARA CADA CANDIDATO PUBLICADO de la fila A-d3-m60, el primer
 * rechazo tipado/observado del pipeline real
 * ([SetupTrainingPlanner.candidates] → [OnboardingPlanGenerator.generate] →
 * [SimpleCyclePersonalizer.personalize]).
 *
 * Este test NO es un oráculo de aceptación: es la evidencia del rechazo que
 * fija la regla de decisión cerrada del plan (identidad → T-003; pool sin ID →
 * ID concreto; equipo/soporte → mapeo con evidencia; tiempo/variedad →
 * composición nativa). Mientras el defecto subsista, falla con el inventario
 * de rechazos para no perder la causa real dentro del `catch` de candidatos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class T004FirstRejectionDiagnosticTest {

    @Test
    fun `A-d3-m60 primer rechazo por cada candidato publicado`() = runBlocking {
        val repository = ApprovedAssetExerciseCatalogRepositoryV2(ApplicationProvider.getApplicationContext())
        repository.load()
        val entryList = SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = TrainingReference.HYPERTROPHY,
                frequency = 3,
                equipment = setOf("bodyweight"),
                level = CatalogLevel.BEGINNER,
                focus = TrainingFocus.FULL_BODY,
                protocolOnly = false,
                mixedTraining = false,
            ),
        )
        val generator = OnboardingPlanGenerator(SimpleCyclePersonalizer(repository))
        val report = StringBuilder()
        report.appendLine("[T-004 DIAG] fila A-d3-m60: candidatos publicados=${entryList.size}")
        var stillFailing = false
        entryList.forEach { entry ->
            val result = generator.generate(
                programId = "diag-A-d3-m60-${entry.id}",
                input = PersonalizerInput(
                    catalogEntryId = entry.id,
                    focus = TrainingFocus.FULL_BODY,
                    frequency = 3,
                    weekdays = listOf(1, 2, 3),
                    equipment = setOf("bodyweight"),
                    level = CatalogLevel.BEGINNER,
                    availableMinutes = 60,
                    cardio = null,
                    calibration = Calibration.CONSERVATIVE,
                    volumeRecommendations = emptyList(),
                    priorityMuscles = emptySet(),
                    lowerEmphasisMuscles = emptySet(),
                    splitId = null,
                    splitPattern = emptyList(),
                    splitName = null,
                ),
                options = TrainingOptions(),
            )
            val programOk = result.program != null
            if (!programOk) stillFailing = true
            report.appendLine(
                "[T-004 DIAG] entry=${entry.id} source=${entry.source} adaptation=${entry.adaptation} " +
                    "programa=${if (programOk) "SI" else "NO"} rechazo=${result.report.limitations.joinToString(" || ").ifEmpty { "<sin limitations>" }}",
            )
        }
        println(report)
        println(probeLevels(repository))
        val viable = entryList.count { entry ->
            generator.generate(
                programId = "diag-final-${entry.id}",
                input = PersonalizerInput(
                    catalogEntryId = entry.id,
                    focus = TrainingFocus.FULL_BODY,
                    frequency = 3,
                    weekdays = listOf(1, 2, 3),
                    equipment = setOf("bodyweight"),
                    level = CatalogLevel.BEGINNER,
                    availableMinutes = 60,
                    cardio = null,
                    calibration = Calibration.CONSERVATIVE,
                    volumeRecommendations = emptyList(),
                    priorityMuscles = emptySet(),
                    lowerEmphasisMuscles = emptySet(),
                    splitId = null,
                    splitPattern = emptyList(),
                    splitName = null,
                ),
                options = TrainingOptions(),
            ).program != null
        }
        check(viable > 0) {
            "T-004 DIAG: NINGÚN candidato publicado materializa en A-d3-m60 (contrato: al menos uno).\n$report"
        }
    }

    /** Sondas acotadas para aislar la puerta que rechaza: nivel, días y foco. */
    private fun probeLevels(repository: ApprovedAssetExerciseCatalogRepositoryV2): String = runBlocking {
        val generator = OnboardingPlanGenerator(SimpleCyclePersonalizer(repository))
        fun probe(id: String, level: CatalogLevel, frequency: Int, weekdays: List<Int>, focus: TrainingFocus): String {
            val result = generator.generate(
                programId = "diag-probe-$id",
                input = PersonalizerInput(
                    catalogEntryId = "native:bodyweight",
                    focus = focus,
                    frequency = frequency,
                    weekdays = weekdays,
                    equipment = setOf("bodyweight"),
                    level = level,
                    availableMinutes = 60,
                    cardio = null,
                    calibration = Calibration.CONSERVATIVE,
                    volumeRecommendations = emptyList(),
                    priorityMuscles = emptySet(),
                    lowerEmphasisMuscles = emptySet(),
                    splitId = null,
                    splitPattern = emptyList(),
                    splitName = null,
                ),
                options = TrainingOptions(),
            )
            return "[T-004 PROBE] $id level=$level freq=$frequency focus=$focus programa=" +
                (if (result.program != null) "SI" else "NO") +
                " rechazo=${result.report.limitations.joinToString(" || ").ifEmpty { "-" }}"
        }
        listOf(
            probe("p1-beginner-d3", CatalogLevel.BEGINNER, 3, listOf(1, 2, 3), TrainingFocus.FULL_BODY),
            probe("p2-intermediate-d3", CatalogLevel.INTERMEDIATE, 3, listOf(1, 2, 3), TrainingFocus.FULL_BODY),
            probe("p3-beginner-d1", CatalogLevel.BEGINNER, 1, listOf(1), TrainingFocus.FULL_BODY),
            probe("p4-intermediate-d1", CatalogLevel.INTERMEDIATE, 1, listOf(1), TrainingFocus.FULL_BODY),
            probe("p5-beginner-d3-legs", CatalogLevel.BEGINNER, 3, listOf(1, 2, 3), TrainingFocus.LEGS),
            probe("p6-advanced-d3", CatalogLevel.ADVANCED, 3, listOf(1, 2, 3), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p7-one-day-bodyweight", "native:one-day", setOf("bodyweight"), CatalogLevel.BEGINNER, 1, listOf(1), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p8-bodyweight-plus-band", "native:bodyweight", setOf("bodyweight", "band"), CatalogLevel.BEGINNER, 3, listOf(1, 2, 3), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p9-home-plus-band-dumbbells", "native:home-training", setOf("bodyweight", "band", "dumbbells"), CatalogLevel.BEGINNER, 3, listOf(1, 2, 3), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p10-one-day-intermediate", "native:one-day", setOf("bodyweight"), CatalogLevel.INTERMEDIATE, 1, listOf(1), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p11-strength-cardio-d5-m60", "native:strength-cardio", setOf("bodyweight"), CatalogLevel.BEGINNER, 5, listOf(1, 2, 3, 4, 5), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p12-strength-cardio-d5-m20", "native:strength-cardio", setOf("bodyweight"), CatalogLevel.BEGINNER, 5, listOf(1, 2, 3, 4, 5), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p13-gym-muscle-d5-m60", "native:gym-muscle", setOf("bodyweight"), CatalogLevel.BEGINNER, 5, listOf(1, 2, 3, 4, 5), TrainingFocus.FULL_BODY),
            probeEntry(repository, "p14-strength-cardio-d6-m60", "native:strength-cardio", setOf("bodyweight"), CatalogLevel.BEGINNER, 6, listOf(1, 2, 3, 4, 5, 6), TrainingFocus.FULL_BODY),
        ).joinToString("\n")
    }

    private fun probeEntry(
        repository: ApprovedAssetExerciseCatalogRepositoryV2,
        id: String,
        entryId: String,
        equipment: Set<String>,
        level: CatalogLevel,
        frequency: Int,
        weekdays: List<Int>,
        focus: TrainingFocus,
    ): String = runBlocking {
        val generator = OnboardingPlanGenerator(SimpleCyclePersonalizer(repository))
        val result = generator.generate(
            programId = "diag-probe-$id",
            input = PersonalizerInput(
                catalogEntryId = entryId,
                focus = focus,
                frequency = frequency,
                weekdays = weekdays,
                equipment = equipment,
                level = level,
                availableMinutes = 60,
                cardio = null,
                calibration = Calibration.CONSERVATIVE,
                volumeRecommendations = emptyList(),
                priorityMuscles = emptySet(),
                lowerEmphasisMuscles = emptySet(),
                splitId = null,
                splitPattern = emptyList(),
                splitName = null,
            ),
            options = TrainingOptions(),
        )
        "[T-004 PROBE] $id entry=$entryId equipment=$equipment level=$level freq=$frequency programa=" +
            (if (result.program != null) "SI" else "NO") +
            " rechazo=${result.report.limitations.joinToString(" || ").ifEmpty { "-" }}"
    }
}
