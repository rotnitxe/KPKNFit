package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.training.quality.SessionSlopRules
import com.example.kpkn.domain.training.quality.SlopRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DUP-07 / I-1 · Los planes propios nativos no dejan dos tarjetas sin identidad propia del mismo ejercicio en una sesión.
 * La potencia (P) conserva la barra ligera específica (§11.5: sentadilla de barra alta, banca con barra) emparejada con el
 * básico de trabajo (`supplementalOf`) y rotulada «Potencia»; dos slots NO potentes con la misma configuración se fusionan
 * en el principal.
 */
class NativeSessionDuplicatesTest {

    private val personalizer = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also { runBlocking { it.load() } },
    )

    private val duplicateRules = setOf(SlopRule.DUPLICATE_CONFIGURATION, SlopRule.DUPLICATE_DEFINITION, SlopRule.SAME_MOVEMENT_FAMILY)

    private fun personalize(kind: NativeProfileKind, level: CatalogLevel, days: Int, gear: AuthoredPlanFixtures.Gear): Program? =
        personalizer.personalize(
            programId = "dup07-${kind.name.lowercase()}-${level.name.lowercase()}-$days",
            input = PersonalizerInput(
                catalogEntryId = kind.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                weekdays = NativeProfileCalendars.DEFAULT_WEEKDAYS.getValue(days),
                equipment = emptySet(),
                level = level,
                availableMinutes = 75,
                cardio = if (kind == NativeProfileKind.COMPLETE_ATHLETE) CardioPreference(CardioType.WALK, 10) else null,
            ),
            options = TrainingOptions(availability = gear.availability),
        ).program

    private fun duplicatesOf(program: Program): List<String> = AuthoredPlanFixtures.weeksOf(program).flatMap { week ->
        week.sessions.flatMap { session ->
            SessionSlopRules.violations(session).filter { it.rule in duplicateRules }.map { "${week.name} · ${session.name}: ${it.rule} ${it.detail}" }
        }
    }

    @Test
    fun `atleta completo intermedio y avanzado no repite ejercicio en una sesion`() {
        val problems = mutableListOf<String>()
        listOf(AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.dumbbellsAndBench).forEach { gear ->
            listOf(CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
                (2..6).forEach { days ->
                    val program = personalize(NativeProfileKind.COMPLETE_ATHLETE, level, days, gear)
                    assertNotNull("Atleta $level $days d ${gear.label} no se materializó", program)
                    duplicatesOf(requireNotNull(program)).distinct().forEach { problems += "${gear.label} $level $days d · $it" }
                }
            }
        }
        assertTrue(problems.take(10).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `atleta completo conserva los slots de potencia`() {
        listOf(AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.dumbbellsAndBench).forEach { gear ->
            val program = requireNotNull(personalize(NativeProfileKind.COMPLETE_ATHLETE, CatalogLevel.INTERMEDIATE, 4, gear))
            val speed = AuthoredPlanFixtures.exercisesOf(program).filter { it.slotRole == SlotRole.SPEED }
            assertTrue("${gear.label}: el atleta debe seguir con potencia", speed.isNotEmpty())
        }
    }

    @Test
    fun `atleta completo con barra hace la potencia con barra alta y banca con barra, emparejada y rotulada Potencia`() {
        listOf(CatalogLevel.INTERMEDIATE, CatalogLevel.ADVANCED).forEach { level ->
            val program = requireNotNull(personalize(NativeProfileKind.COMPLETE_ATHLETE, level, 4, AuthoredPlanFixtures.fullGym))
            // La banca de potencia comparte configuración con el básico: `normalizedIdentityFields` borra ese enlace a sí
            // misma, así que la identidad la da el rótulo; la sentadilla de barra alta queda enlazada a la de barra baja.
            val power = AuthoredPlanFixtures.exercisesOf(program).filter { it.slotRole == SlotRole.SPEED }
            assertTrue("$level: el atleta debe seguir con potencia", power.isNotEmpty())
            power.forEach {
                assertEquals("$level: ${it.name} rotulada", "Potencia", it.occurrenceLabel)
            }
            val configurations = power.mapNotNull { it.catalogConfigurationId }.toSet()
            assertTrue("$level: potencia de sentadilla con barra alta $configurations", "high_bar_back_squat__barbell" in configurations)
            assertTrue("$level: potencia de banca con barra $configurations", "bench_press__barbell" in configurations)
            power.filter { it.catalogConfigurationId == "high_bar_back_squat__barbell" }.forEach {
                assertEquals("$level: ${it.name} enlazada a la sentadilla de barra baja", "low_bar_back_squat__barbell", it.relativeToCanonicalExerciseId)
            }
            AuthoredPlanFixtures.weeksOf(program).flatMap { it.sessions }.forEach { session ->
                // Una aparición repetida solo cabe si tiene identidad propia; el resto de la sesión no repite nada.
                assertEquals(
                    "$level · ${session.name}",
                    emptyList<Any>(),
                    SessionSlopRules.violations(session).filter { it.rule in duplicateRules },
                )
            }
        }
    }

    @Test
    fun `fuerza y musculo principiante con mancuernas no duplica el remo`() {
        (2..6).forEach { days ->
            val program = personalize(NativeProfileKind.POWERBUILDING, CatalogLevel.BEGINNER, days, AuthoredPlanFixtures.dumbbellsAndBench)
            if (program == null) return@forEach
            assertEquals("$days d", emptyList<String>(), duplicatesOf(program).distinct())
        }
    }

    @Test
    fun `fuerza y musculo conserva la dosis del remo fusionado`() {
        val program = requireNotNull(
            personalize(NativeProfileKind.POWERBUILDING, CatalogLevel.BEGINNER, 5, AuthoredPlanFixtures.dumbbellsAndBench),
        )
        val rows = AuthoredPlanFixtures.weeksOf(program).first().sessions
            .flatMap { it.allExercises() }
            .filter { it.catalogConfigurationId == "conventional_row__dumbbells" }
        assertTrue("el remo con mancuernas sigue en el plan", rows.isNotEmpty())
        assertTrue("series del remo fusionado: ${rows.map { it.sets.size }}", rows.all { it.sets.size >= 2 })
    }
}
