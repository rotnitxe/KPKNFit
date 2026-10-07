package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineRequest
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Entreno v2, cierre de D3: ¿qué pasa con la aproximación y la movilidad cuando un programa del generador de rutinas (D1)
 * vuelve a pasar por el planificador?
 *
 * El generador ya llama a [ApproachPlanner.apply] al final de cada sesión (`SessionAssembler`) con su propio `infoOf` y su
 * propia traducción del nivel. `PlanMaterializer` solo lo aplica a las sesiones que ARMA desde una receta; un
 * `GeneratedRoutine.program` no tiene receta, así que no pasa por él. Otros pasos sí repiten el planificador sobre sesiones
 * ya armadas (D5, `SplitRedistributor`, tras repartir la semana) y cada uno trae su `infoOf`. Aquí se fija lo que debe
 * cumplirse con CUALQUIER `infoOf`: una segunda pasada con el proveedor del materializador ([ApproachInfoProvider]) jamás
 * duplica ni reemplaza lo que el generador ya puso (solo completa lo que falta) y es idempotente; además se mide cuánto
 * añade, que es lo que S-B debe saber si materializa o reparte un programa generado.
 */
class ApproachGeneratedRoutineTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val infoOf by lazy { ApproachInfoProvider.fromMetadata(CatalogCompositionMetadataProvider.fromCatalog(catalog)) }

    private fun levelOf(level: RoutineLevel): ApproachLevel = when (level) {
        RoutineLevel.NOVICE, RoutineLevel.RETURNING -> ApproachLevel.NOVICE
        RoutineLevel.INTERMEDIATE -> ApproachLevel.INTERMEDIATE
        RoutineLevel.ADVANCED -> ApproachLevel.ADVANCED
    }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private class Probe(val label: String, val request: RoutineRequest)

    private fun probes(): List<Probe> {
        val profiles = listOf(RoutineTestSupport.bodyOnly, RoutineTestSupport.homeBarbell, RoutineTestSupport.gym)
        val modes = listOf(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.CUSTOM_POWERLIFTING)
        val levels = listOf(RoutineLevel.NOVICE, RoutineLevel.ADVANCED)
        return profiles.flatMap { profile ->
            modes.flatMap { mode ->
                levels.map { level ->
                    Probe(
                        "${profile.id}/${mode.name}/${level.name}",
                        RoutineTestSupport.request(profile, mode, level, dayCount = 3, minutes = 60),
                    )
                }
            }
        }
    }

    @Test
    fun generated_sessions_already_carry_the_mandatory_approach() {
        var withMobilityFirst = 0
        var strengthSessions = 0
        probes().forEach { probe ->
            sessionsOf(RoutineGenerator.generate(probe.request).program).forEach { session ->
                val first = session.allExercises().firstOrNull { exercise -> exercise.cardioDetails == null && exercise.sets.isNotEmpty() }
                    ?: return@forEach
                strengthSessions++
                if (first.mobilitySeries.isNotEmpty()) withMobilityFirst++
            }
        }
        assertTrue("el generador no devolvió sesiones de fuerza", strengthSessions > 0)
        // La regla del usuario: el primer ejercicio de fuerza de CADA sesión lleva movilidad (la aproximación solo si es pesado).
        assertEquals("el primer ejercicio de cada sesión generada lleva movilidad previa", strengthSessions, withMobilityFirst)
    }

    @Test
    fun a_second_pass_with_the_materializer_provider_never_duplicates_or_replaces_and_is_idempotent() {
        var sessions = 0
        var changed = 0
        var addedSeconds = 0L
        val changedLabels = mutableListOf<String>()
        probes().forEach { probe ->
            val options = ApproachOptions(level = levelOf(probe.request.level))
            sessionsOf(RoutineGenerator.generate(probe.request).program).forEach { before ->
                val after = ApproachPlanner.apply(before, options, infoOf)
                sessions++
                before.allExercises().zip(after.allExercises()).forEach { (b, a) ->
                    assertEquals("${probe.label}: el orden de los ejercicios no cambia", b.id, a.id)
                    if (b.warmupSets.isNotEmpty()) {
                        assertEquals("${probe.label}: '${b.name}' conserva su aproximación (no se reemplaza)", b.warmupSets, a.warmupSets)
                    }
                    if (b.mobilitySeries.isNotEmpty()) {
                        assertEquals("${probe.label}: '${b.name}' conserva su movilidad (no se reemplaza)", b.mobilitySeries, a.mobilitySeries)
                    }
                    assertEquals("${probe.label}: '${a.name}' no repite escalones de aproximación", a.warmupSets.size, a.warmupSets.map { it.id }.distinct().size)
                    assertEquals("${probe.label}: '${a.name}' no repite movimientos de movilidad", a.mobilitySeries.size, a.mobilitySeries.map { it.id }.distinct().size)
                }
                assertEquals("${probe.label}: una tercera pasada no cambia nada", after, ApproachPlanner.apply(after, options, infoOf))
                if (after != before) {
                    changed++
                    changedLabels += probe.label
                    addedSeconds += SessionDurationEstimator.estimate(after).totalSeconds - SessionDurationEstimator.estimate(before).totalSeconds
                }
            }
        }
        println(
            "\n[D3] segunda pasada del planificador (proveedor del materializador) sobre sesiones del generador: " +
                "sesiones=$sessions cambian=$changed" +
                (if (changed > 0) " (+${addedSeconds / changed} s de media en las que cambian; ${changedLabels.distinct()})" else "") + "\n",
        )
        assertTrue(sessions > 0)
    }
}
