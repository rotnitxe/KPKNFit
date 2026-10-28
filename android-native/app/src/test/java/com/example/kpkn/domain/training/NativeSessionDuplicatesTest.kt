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
 * DUP-07 · Los planes propios nativos no repiten ejercicio (configuración, definición ni familia de movimiento) en una
 * sesión: la potencia (P) elige otra configuración que la del resto del día, y dos slots no potentes con la misma
 * configuración se fusionan en el principal.
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
    fun `atleta completo conserva los slots de potencia al evitar el duplicado`() {
        listOf(AuthoredPlanFixtures.fullGym, AuthoredPlanFixtures.dumbbellsAndBench).forEach { gear ->
            val program = requireNotNull(personalize(NativeProfileKind.COMPLETE_ATHLETE, CatalogLevel.INTERMEDIATE, 4, gear))
            val speed = AuthoredPlanFixtures.exercisesOf(program).filter { it.slotRole == SlotRole.SPEED }
            assertTrue("${gear.label}: el atleta debe seguir con potencia", speed.isNotEmpty())
        }
    }

    @Test
    fun `atleta completo con barra no hace dos sentadillas traseras el mismo dia`() {
        val program = requireNotNull(personalize(NativeProfileKind.COMPLETE_ATHLETE, CatalogLevel.ADVANCED, 4, AuthoredPlanFixtures.fullGym))
        AuthoredPlanFixtures.weeksOf(program).flatMap { it.sessions }.forEach { session ->
            val families = session.allExercises().mapNotNull { SessionSlopRules.familyOf(it.catalogConfigurationId) }
            assertEquals("${session.name}: $families", families.distinct().size, families.size)
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
