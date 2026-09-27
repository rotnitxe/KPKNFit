package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.programs.toTrainingReference
import com.example.kpkn.domain.onboarding.SetupTrainingPlanner
import com.example.kpkn.domain.onboarding.SetupTrainingPlannerInput
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barrido puro de disponibilidad (T-004, plan kpknfit-wizard-cierre-20260927):
 * prueba PUBLICACIÓN de candidatos para todo el universo admisible del wizard —
 * 2^11 subconjuntos de [EquipmentCategory] × 6 frecuencias × 4 experiencias ×
 * las filas objetivo/estilo — sin Room, Compose, VM ni materialización. La
 * generación real se prueba por separado en la matriz T019.
 *
 * Ocho tests determinados por los 3 primeros bits del orden del enum; cada uno
 * itera los 256 subconjuntos restantes. Convierte categorías a tokens con
 * `TrainingOptions.effectiveEquipment(emptySet())` (vacío → bodyweight), nunca
 * bits como IDs de equipo ni `general_gym` artificial.
 *
 * Clasificación honesta (AC-T004-02):
 * - `candidates()` no vacío → PASS para esa combinación.
 * - Vacío con ENTRADA PUBLICADA de esa referencia a esa frecuencia → **FALLA**
 *   (un filtro la está excluyendo indebidamente).
 * - Vacío SIN entrada publicada de esa referencia a esa frecuencia → fila
 *   **REGISTRADA con causa** (`sin-entrada-publicada`): el catálogo no tiene un
 *   plan honesto de esa disciplina para esa frecuencia y las nativas no se
 *   reetiquetan (`PersonalizedPlanCatalog.nativeEntry`). El conjunto registrado
 *   se contrasta contra la cobertura derivada del catálogo y contra el conjunto
 *   esperado conocido, de modo que cualquier deriva del catálogo destaca.
 */
class SetupTrainingPlannerAvailabilitySweepTest {

    private data class GoalRow(
        val label: String,
        val goal: SetupGoal,
        val style: TrainingStyle?,
        val mixed: Boolean,
    ) {
        val expectedReference: TrainingReference
            get() = when (goal) {
                SetupGoal.STRENGTH -> TrainingReference.POWERLIFTING
                SetupGoal.MUSCLE -> TrainingReference.HYPERTROPHY
                SetupGoal.STRENGTH_MUSCLE -> TrainingReference.POWERBUILDING
                SetupGoal.HEALTH, SetupGoal.MIXED -> requireNotNull(style).toTrainingReference()
            }
    }

    private val goalRows: List<GoalRow> = buildList {
        add(GoalRow("STRENGTH", SetupGoal.STRENGTH, null, mixed = false))
        add(GoalRow("MUSCLE", SetupGoal.MUSCLE, null, mixed = false))
        add(GoalRow("STRENGTH_MUSCLE", SetupGoal.STRENGTH_MUSCLE, null, mixed = false))
        listOf(TrainingStyle.POWERLIFTER, TrainingStyle.BODYBUILDER, TrainingStyle.POWERBUILDER).forEach { style ->
            add(GoalRow("HEALTH/${style.name}", SetupGoal.HEALTH, style, mixed = false))
            add(GoalRow("MIXED/${style.name}", SetupGoal.MIXED, style, mixed = true))
        }
    }

    private val experiences = listOf(
        SetupExperience.NEW, SetupExperience.RETURNING, SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED,
    )

    @Test
    fun shard0() = runShard(0)

    @Test
    fun shard1() = runShard(1)

    @Test
    fun shard2() = runShard(2)

    @Test
    fun shard3() = runShard(3)

    @Test
    fun shard4() = runShard(4)

    @Test
    fun shard5() = runShard(5)

    @Test
    fun shard6() = runShard(6)

    @Test
    fun shard7() = runShard(7)

    private fun runShard(shard: Int) {
        val categories = EquipmentCategory.entries
        require(categories.size == 11) { "El barrido asume 11 categorías; hay ${categories.size}" }
        require(shard in 0..7) { "shard fuera de rango: $shard" }
        val failures = mutableListOf<String>()
        val registered = mutableMapOf<String, Int>()
        var rows = 0
        for (rest in 0 until 256) {
            val bits = (shard shl 8) or rest
            val subset = categories.filterIndexed { index, _ -> (bits shr index) and 1 == 1 }.toSet()
            val equipmentTokens = TrainingOptions(availability = EquipmentAvailability(subset)).effectiveEquipment(emptySet())
            for (frequency in 1..6) {
                for (experience in experiences) {
                    for (row in goalRows) {
                        rows++
                        val reference = referenceOf(row)
                        val candidates = SetupTrainingPlanner.candidates(
                            SetupTrainingPlannerInput(
                                reference = reference,
                                frequency = frequency,
                                equipment = equipmentTokens,
                                level = levelOf(experience),
                                focus = TrainingFocus.FULL_BODY,
                                protocolOnly = false,
                                mixedTraining = row.mixed,
                            ),
                        )
                        if (candidates.isNotEmpty()) continue
                        val key = "goal=${row.label} freq=$frequency exp=${experience.name}"
                        if (publishedCoverage(row, frequency)) {
                            failures += "CANDIDATO_AUSENTE shard=$shard bits=$bits subset=$subset tokens=$equipmentTokens $key ref=$reference"
                        } else {
                            registered.merge("sin-entrada-publicada $key ref=$reference", 1, Int::plus)
                        }
                    }
                }
            }
        }
        val report = buildString {
            appendLine("[T-004 SWEEP] shard=$shard filas=$rows fallos=${failures.size} registradas=${registered.size}")
            failures.take(40).forEach { appendLine("  FALLO $it") }
            registered.entries.sortedBy { it.key }.forEach { appendLine("  REGISTRADA ${it.key} filas=${it.value}") }
        }
        println(report)
        assertTrue(report, failures.isEmpty())
        // Toda fila registrada debe ser realmente un hueco de cobertura derivado
        // del catálogo (la causa), y el conjunto de huecos por referencia×frecuencia
        // debe coincidir con el esperado conocido para que la deriva destaque.
        registered.keys.forEach { key ->
            val reference = key.substringAfter("ref=").trim()
            val frequency = key.substringAfter("freq=").substringBefore(" ").toInt()
            val covered = PersonalizedPlanCatalog.entries().any { entry ->
                entry.publication.name == "PUBLISHED" &&
                    entry.references.any { it.name == reference } &&
                    entry.supportedFrequencies.contains(frequency)
            }
            assertTrue("REGISTRADA sin causa real: $key sí tiene entrada publicada", !covered)
        }
        val derivedGaps = derivedGapMap()
        val expectedGaps = mapOf(
            TrainingReference.POWERLIFTING to setOf(2, 6),
            TrainingReference.POWERBUILDING to setOf(1, 2, 3, 6),
            TrainingReference.HYPERTROPHY to emptySet(),
        )
        assertTrue("Huecos de cobertura derivados $derivedGaps ≠ esperados $expectedGaps", derivedGaps == expectedGaps)
    }

    /**
     * Precondición cerrada del plan: para HEALTH/MIXED el estilo se declara
     * explícito y el draft debe traducirlo a una referencia NO nula antes de
     * consultar; para los otros objetivos se comprueba la referencia inferida.
     */
    private fun referenceOf(row: GoalRow): TrainingReference {
        val draft = SetupWizardDraft(
            goal = row.goal,
            volumeAnswers = SetupVolumeAnswers(style = row.style),
        )
        val mapped = draft.trainingReference()
        check(mapped != null) { "trainingReference() null para ${row.label}" }
        check(mapped == row.expectedReference) { "${row.label}: referencia $mapped ≠ esperada ${row.expectedReference}" }
        return mapped
    }

    private fun levelOf(experience: SetupExperience): CatalogLevel = when (experience) {
        SetupExperience.ADVANCED -> CatalogLevel.ADVANCED
        SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE
        else -> CatalogLevel.BEGINNER
    }

    /** ¿Existe alguna entrada PUBLICADA de esa referencia que soporte esa frecuencia? */
    private fun publishedCoverage(row: GoalRow, frequency: Int): Boolean {
        if (row.mixed) return true // MIXED no filtra por referencia: exige schedulesCardio
        return PersonalizedPlanCatalog.entries().any { entry ->
            entry.publication.name == "PUBLISHED" &&
                entry.references.contains(row.expectedReference) &&
                entry.supportedFrequencies.contains(frequency)
        }
    }

    private fun derivedGapMap(): Map<TrainingReference, Set<Int>> =
        listOf(TrainingReference.POWERLIFTING, TrainingReference.HYPERTROPHY, TrainingReference.POWERBUILDING)
            .associateWith { reference ->
                (1..6).filter { frequency ->
                    PersonalizedPlanCatalog.entries().none { entry ->
                        entry.publication.name == "PUBLISHED" &&
                            entry.references.contains(reference) &&
                            entry.supportedFrequencies.contains(frequency)
                    }
                }.toSet()
            }
}
