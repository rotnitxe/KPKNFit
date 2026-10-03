package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P3 + A.D1 (DEC-w2-06): el planner ordena por la ficha editorial —(disciplina pedida,
 * nivel entre los `levels`, clase de plan, `rank`, id)—, filtra con el prefiltro de
 * capacidades de Atleta completo y no esconde las plantillas con receta fija cuando el
 * enfoque no es cuerpo completo. Ningún criterio de orden oculta una entrada (DEC-w1-04).
 *
 * Las expectativas de orden se escriben a mano y, además, se comprueba la propiedad general
 * con un oráculo propio de la prueba (no el comparador de producción) sobre una rejilla de
 * entradas, para que un cambio de ficha o de comparador falle con el nombre de la entrada.
 */
class SetupTrainingPlannerRankingTest {

    private val ownStrength = "native:strength-foundation-v2"
    private val ownMuscle = "native:muscle-foundation-v2"
    private val ownPowerbuilding = "native:powerbuilding-foundation-v2"
    private val ownAthlete = "native:complete-athlete-v2"

    private val hiddenHistoricals = setOf(
        "native:full-body",
        "native:gym-muscle",
        "native:one-day",
        "native:return-training",
        "native:home-training",
    )

    private val athleteCapabilities: Set<TrainingCapability> =
        PlanGoalMatcher.requiredCapabilities(PlanGoalProfile.COMPLETE_ATHLETE)

    private fun input(
        reference: TrainingReference? = TrainingReference.POWERLIFTING,
        frequency: Int? = 4,
        equipment: Set<String> = setOf("general_gym"),
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
        focus: TrainingFocus = TrainingFocus.FULL_BODY,
        protocolOnly: Boolean = false,
        mixedTraining: Boolean = false,
        requiredCapabilities: Set<TrainingCapability> = emptySet(),
    ) = SetupTrainingPlannerInput(
        reference = reference,
        frequency = frequency,
        equipment = equipment,
        level = level,
        focus = focus,
        protocolOnly = protocolOnly,
        mixedTraining = mixedTraining,
        requiredCapabilities = requiredCapabilities,
    )

    private fun candidates(input: SetupTrainingPlannerInput): List<CatalogEntry> =
        SetupTrainingPlanner.candidates(input)

    private fun idsOf(input: SetupTrainingPlannerInput): List<String> = candidates(input).map { it.id }

    private val allReferences: List<TrainingReference?> = listOf(null) + TrainingReference.entries
    private val allFrequencies: List<Int?> = listOf<Int?>(null) + (1..6).toList()

    // ─── (a) Fuerza ───────────────────────────────────────────────────────────

    @Test
    fun strengthBeginnerListsTheOwnPlanThenTheBeginnerTemplateAndLeavesSpecializationsLast() {
        val list = candidates(input(reference = TrainingReference.POWERLIFTING, frequency = null, level = CatalogLevel.BEGINNER))
        val ids = list.map { it.id }
        println("[C.P3] Fuerza · principiante · sin filtro de días: $ids")

        assertEquals("el plan propio de Fuerza va primero", ownStrength, ids[0])
        assertEquals("la plantilla de principiantes va justo detrás", "template:power-12-3", ids[1])
        // Solo esos dos declaran el nivel principiante: todo lo demás está en otro nivel, sin ocultarse.
        list.drop(2).forEach { entry ->
            assertFalse("${entry.id} declara nivel principiante y debería ir antes", CatalogLevel.BEGINNER in entry.levels)
        }
        assertTrue("los métodos de otro nivel siguen apareciendo (DEC-w1-04)", ids.size > 10)

        // Smolov, Smolov Jr y Coan (kind ≠ PLAN) cierran la lista; ningún PLAN va detrás de ellos.
        assertEquals(
            listOf("protocol:smolov", "protocol:smolov-jr", "protocol:coan-phillipi-dl"),
            ids.takeLast(3),
        )
        val firstNonPlan = list.indexOfFirst { it.kind != PlanKind.PLAN }
        assertTrue("hay una primera entrada que no es un PLAN", firstNonPlan > 0)
        assertTrue(
            "ningún PLAN puede ir después de una especialización o un complemento",
            list.drop(firstNonPlan).none { it.kind == PlanKind.PLAN },
        )
    }

    @Test
    fun strengthAdvancedKeepsSpecializationsAfterTheAdvancedPlansButBeforeOtherLevels() {
        val list = candidates(input(reference = TrainingReference.POWERLIFTING, frequency = null, level = CatalogLevel.ADVANCED))
        println("[C.P3] Fuerza · avanzado · sin filtro de días: ${list.map { it.id }}")

        val matching = list.filter { CatalogLevel.ADVANCED in it.levels }
        // El bloque del nivel va primero y entero (el nivel pesa más que la clase de plan).
        assertEquals(matching.map { it.id }, list.take(matching.size).map { it.id })
        assertEquals("el plan propio abre la lista", ownStrength, matching.first().id)
        // Dentro del bloque: los PLAN, y detrás Smolov, Smolov Jr y el complemento de Coan.
        assertEquals(
            listOf("protocol:smolov", "protocol:smolov-jr", "protocol:coan-phillipi-dl"),
            matching.takeLast(3).map { it.id },
        )
        assertTrue(matching.dropLast(3).all { it.kind == PlanKind.PLAN })
    }

    @Test
    fun theOwnPlanOfEachDisciplineLeadsItsListAtEveryFrequencyAndLevel() {
        val leaders = mapOf(
            TrainingReference.POWERLIFTING to ownStrength,
            TrainingReference.HYPERTROPHY to ownMuscle,
            TrainingReference.POWERBUILDING to ownPowerbuilding,
        )
        leaders.forEach { (reference, own) ->
            CatalogLevel.entries.forEach { level ->
                (1..6).forEach { days ->
                    val ids = idsOf(input(reference = reference, frequency = days, level = level))
                    assertEquals("$reference/$level/${days}d: el plan propio va primero", own, ids.firstOrNull())
                }
            }
        }
    }

    // ─── (b) Músculo ──────────────────────────────────────────────────────────

    @Test
    fun muscleListsTheOwnPlanBeforeTheKeptHistoricalsAndNeverOffersTheHiddenOnes() {
        val machine = "native:machine-muscle"
        val bodyweight = "native:bodyweight"
        CatalogLevel.entries.forEach { level ->
            allFrequencies.forEach { frequency ->
                val ids = idsOf(input(reference = TrainingReference.HYPERTROPHY, frequency = frequency, level = level))
                val label = "Músculo/$level/${frequency ?: "sin filtro"}"
                assertTrue("$label: ofrece un histórico oculto ${ids.filter { it in hiddenHistoricals }}", ids.none { it in hiddenHistoricals })
                assertEquals("$label: el propio va primero", ownMuscle, ids.first())
                val kept = ids.filter { it == machine || it == bodyweight }
                assertEquals("$label: máquinas antes que peso corporal", kept.sortedBy { if (it == machine) 0 else 1 }, kept)
            }
        }
        val three = idsOf(input(reference = TrainingReference.HYPERTROPHY, frequency = 3, level = CatalogLevel.BEGINNER))
        println("[C.P3] Músculo · principiante · 3 días: $three")
        assertEquals(
            "el propio precede a los dos históricos que se conservan",
            listOf(ownMuscle, machine, bodyweight),
            three.filter { it == ownMuscle || it == machine || it == bodyweight },
        )
        assertEquals("el propio es la primera opción de Músculo", ownMuscle, three.first())
    }

    // ─── (c) Fuerza y músculo ─────────────────────────────────────────────────

    @Test
    fun strengthMuscleIntermediateAtFourDaysOrdersOwnThenOriginalThenAdaptedThenLegacy() {
        val list = candidates(input(reference = TrainingReference.POWERBUILDING, frequency = 4, level = CatalogLevel.INTERMEDIATE))
        val ids = list.map { it.id }
        println("[C.P3] Fuerza y músculo · intermedio · 4 días: $ids")

        assertEquals(
            listOf(
                ownPowerbuilding,
                "original:phul-ms-2021-r1",
                "adapted:phul-kpkn-r1",
                "protocol:wendler-531-bbb",
                "protocol:phul-verified",
            ),
            ids.take(5),
        )
        // Lo que no declara el nivel intermedio (la plantilla avanzada) va después, sin ocultarse.
        list.drop(5).forEach { assertFalse("${it.id} declara nivel intermedio", CatalogLevel.INTERMEDIATE in it.levels) }
        assertTrue("la plantilla avanzada sigue disponible", "template:powerbuild-16-4" in ids)
    }

    @Test
    fun strengthMuscleAdvancedAtFiveDaysOrdersOwnThenOriginalThenAdaptedThenLegacy() {
        val ids = idsOf(input(reference = TrainingReference.POWERBUILDING, frequency = 5, level = CatalogLevel.ADVANCED))
        println("[C.P3] Fuerza y músculo · avanzado · 5 días: $ids")
        assertEquals(
            listOf(
                ownPowerbuilding,
                "original:phat-biolayne-2016-r1",
                "adapted:phat-kpkn-r1",
                "protocol:phat-verified",
            ),
            ids.take(4),
        )
    }

    // ─── (d) Atleta completo ──────────────────────────────────────────────────

    @Test
    fun completeAthletePrefilterLeavesOnlyTheOwnAthletePlanAtEveryFrequency() {
        (1..6).forEach { days ->
            CatalogLevel.entries.forEach { level ->
                TrainingFocus.entries.forEach { focus ->
                    val ids = idsOf(
                        input(
                            reference = null,
                            frequency = days,
                            level = level,
                            focus = focus,
                            requiredCapabilities = athleteCapabilities,
                        ),
                    )
                    assertEquals("Atleta/$level/${days}d/$focus", listOf(ownAthlete), ids)
                }
            }
        }
        // El plan propio no es un protocolo: la ruta solo-protocolos queda vacía.
        assertTrue(idsOf(input(reference = null, frequency = 3, protocolOnly = true, requiredCapabilities = athleteCapabilities)).isEmpty())
    }

    @Test
    fun withoutThePrefilterAthleteStillSeesTheWholeCatalogAndTheOwnPlanAmongIt() {
        val perFrequency = (1..6).associateWith { days -> idsOf(input(reference = null, frequency = days, level = CatalogLevel.BEGINNER)) }
        perFrequency.forEach { (days, ids) ->
            assertTrue("${days}d: el plan propio de Atleta sigue estando", ownAthlete in ids)
            assertTrue("${days}d: sin prefiltro hay más candidatos que el propio", ids.size > 1)
        }
        // Cifras para el registro de DEC-w2-06: con el prefiltro, Q1 pasa de estos candidatos a uno por fila.
        println(
            "[C.P3] Atleta sin prefiltro por días: " +
                perFrequency.entries.joinToString { "${it.key}d=${it.value.size}" } +
                " (suma=${perFrequency.values.sumOf { it.size }}; con el prefiltro: 6)",
        )
    }

    // ─── (e) Comportamiento heredado sin capacidades requeridas ───────────────

    @Test
    fun withoutRequiredCapabilitiesTheDisciplineFiltersBehaveAsBefore() {
        val published = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }
        TrainingReference.entries.forEach { reference ->
            (1..6).forEach { days ->
                CatalogLevel.entries.forEach { level ->
                    val expected = published
                        .filter { reference in it.references && days in it.supportedFrequencies }
                        .map { it.id }
                        .toSet()
                    val actual = idsOf(input(reference = reference, frequency = days, level = level))
                    assertEquals("$reference/${days}d/$level: el nivel solo ordena, no filtra", expected, actual.toSet())
                    assertEquals("$reference/${days}d/$level: sin repetidos", actual.size, actual.toSet().size)
                }
            }
        }
        // El modo mixto sigue sirviendo solo lo que programa cardio, con cualquier disciplina.
        TrainingReference.entries.forEach { reference ->
            assertEquals(listOf("native:strength-cardio"), idsOf(input(reference = reference, frequency = 3, mixedTraining = true)))
        }
        assertTrue("el valor por defecto no exige capacidades", input().requiredCapabilities.isEmpty())
    }

    @Test
    fun theLevelOnlyOrdersAndNeverChangesTheMembership() {
        allReferences.forEach { reference ->
            allFrequencies.forEach { frequency ->
                listOf(TrainingFocus.FULL_BODY, TrainingFocus.GLUTES).forEach { focus ->
                    val byLevel = CatalogLevel.entries.associateWith { level ->
                        idsOf(input(reference = reference, frequency = frequency, level = level, focus = focus)).toSet()
                    }
                    assertEquals(
                        "$reference/${frequency ?: "sin filtro"}/$focus: la membresía cambia con el nivel",
                        1,
                        byLevel.values.toSet().size,
                    )
                }
            }
        }
    }

    // ─── (f) Exención de enfoque para plantillas con receta ───────────────────

    @Test
    fun templatesWithAFixedRecipeAreNotHiddenByAFocusAndSimpleTemplatesAre() {
        val recipeTemplates = PersonalizedPlanCatalog.listedEntries()
            .filter { it.source == CatalogSource.TEMPLATE && it.template?.recipe != null }
        val simpleTemplates = PersonalizedPlanCatalog.listedEntries()
            .filter { it.source == CatalogSource.TEMPLATE && it.template?.recipe == null }
        assertEquals("siete plantillas con receta", 7, recipeTemplates.size)
        assertEquals("tres plantillas simples", 3, simpleTemplates.size)

        TrainingFocus.entries.forEach { focus ->
            recipeTemplates.forEach { template ->
                val days = template.supportedFrequencies.first
                val ids = idsOf(input(reference = null, frequency = days, level = CatalogLevel.INTERMEDIATE, focus = focus))
                assertTrue("${template.id} debe aparecer con el enfoque $focus", template.id in ids)
            }
            val three = idsOf(input(reference = null, frequency = 3, focus = focus))
            if (focus == TrainingFocus.FULL_BODY) {
                simpleTemplates.forEach { assertTrue("${it.id} aparece con cuerpo completo", it.id in three) }
            } else {
                simpleTemplates.forEach { assertFalse("${it.id} no aparece con el enfoque $focus", it.id in three) }
            }
        }
        assertTrue("template:power-12-3" in idsOf(input(reference = null, frequency = 3, focus = TrainingFocus.GLUTES)))
        assertTrue(
            "con disciplina de Fuerza y enfoque glúteos, la plantilla de principiantes sigue",
            "template:power-12-3" in idsOf(
                input(reference = TrainingReference.POWERLIFTING, frequency = 3, level = CatalogLevel.BEGINNER, focus = TrainingFocus.GLUTES),
            ),
        )
    }

    @Test
    fun theFocusExemptionDoesNotLeakToOtherEntries() {
        listOf(TrainingFocus.GLUTES, TrainingFocus.ARMS).forEach { focus ->
            allFrequencies.forEach { frequency ->
                candidates(input(reference = null, frequency = frequency, focus = focus)).forEach { entry ->
                    val exempt = entry.source == CatalogSource.PROTOCOL ||
                        (entry.source == CatalogSource.TEMPLATE && entry.template?.recipe != null)
                    assertTrue(
                        "${entry.id} entra con el enfoque $focus sin declararlo ni estar exenta",
                        focus in entry.supportedFocuses || exempt,
                    )
                }
            }
        }
    }

    // ─── Propiedad general del orden editorial ────────────────────────────────

    /** Oráculo propio de la prueba: la clase de plan se ordena con esta lista, no con el comparador de producción. */
    private val kindOrder = listOf(PlanKind.PLAN, PlanKind.ESPECIALIZACION, PlanKind.COMPLEMENTO, PlanKind.ESTRUCTURA)

    private fun editorialComparator(input: SetupTrainingPlannerInput): Comparator<CatalogEntry> {
        val reference = input.reference
        return compareBy<CatalogEntry>(
            { if (reference != null && reference in it.references) 0 else 1 },
            { if (input.level in it.levels) 0 else 1 },
            { kindOrder.indexOf(it.kind) },
            { it.rank },
            { it.id },
        )
    }

    @Test
    fun everyListIsInEditorialOrderAndNeverOffersAHiddenHistorical() {
        var lists = 0
        allReferences.forEach { reference ->
            allFrequencies.forEach { frequency ->
                CatalogLevel.entries.forEach { level ->
                    listOf(TrainingFocus.FULL_BODY, TrainingFocus.GLUTES).forEach { focus ->
                        listOf(false, true).forEach { mixed ->
                            listOf<Set<TrainingCapability>>(emptySet(), athleteCapabilities).forEach { required ->
                                val request = input(
                                    reference = reference,
                                    frequency = frequency,
                                    level = level,
                                    focus = focus,
                                    mixedTraining = mixed,
                                    requiredCapabilities = required,
                                )
                                val actual = candidates(request)
                                val expected = actual.sortedWith(editorialComparator(request))
                                lists++
                                assertEquals(
                                    "fuera de orden editorial: ref=$reference días=$frequency nivel=$level foco=$focus mixto=$mixed caps=$required",
                                    expected.map { it.id },
                                    actual.map { it.id },
                                )
                                assertTrue("sin ids repetidos", actual.map { it.id }.toSet().size == actual.size)
                                assertTrue("oculto en la lista", actual.none { it.id in hiddenHistoricals })
                                assertTrue(
                                    "capacidades requeridas incumplidas",
                                    actual.all { it.capabilities.containsAll(required) },
                                )
                            }
                        }
                    }
                }
            }
        }
        assertEquals("listas recorridas", 4 * 7 * 3 * 2 * 2 * 2, lists)
    }

    @Test
    fun theEditorialKeyPutsPlansBeforeSpecializationsComplementsAndStructuresWithinTheSameLevelGroup() {
        // Estructuras en blanco: sin disciplina declarada solo aparecen sin referencia, al final de su grupo de nivel.
        val list = candidates(input(reference = null, frequency = 3, level = CatalogLevel.BEGINNER))
        val structures = list.filter { it.kind == PlanKind.ESTRUCTURA }
        assertEquals("tres estructuras en blanco", 3, structures.size)
        val matchingLevel = list.filter { CatalogLevel.BEGINNER in it.levels }
        val lastPlan = matchingLevel.indexOfLast { it.kind == PlanKind.PLAN }
        val firstStructure = matchingLevel.indexOfFirst { it.kind == PlanKind.ESTRUCTURA }
        assertTrue(
            "las estructuras en blanco van detrás de todos los PLAN de su nivel",
            lastPlan in 0 until firstStructure,
        )
        // El orden entre estructuras sale del rank (950, 951, 952), no del id.
        assertEquals(
            listOf("template:simple-1", "template:simple-ab", "template:simple-4"),
            structures.map { it.id },
        )
    }
}
