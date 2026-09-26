package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.domain.training.TrainingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Ruta fija (protocolo y plantilla) con las opciones del usuario en la PRIMERA
 * materialización: los motores reciben `defaultOptions` y lo reenvían a
 * `PlanMaterializer.materialize`, que es quien decide la precedencia
 * (elección/autor ya guardada en el programa > `options`) y asigna los pasos de
 * calentamiento por ejercicio.
 *
 * Se afirma sobre el [Program] materializado (config persistida Y pasos reales
 * por ejercicio), nunca sobre JSON reescrito después. Cubre el bug confirmado:
 * una copia de `planWarmupConfig` DESPUÉS de materializar no reasigna warmups.
 */
class SetupFixedWarmupOptionsTest {

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private const val PROTOCOL_ID = "gzclp"
        private const val TEMPLATE_ID = "power-12-3"
    }

    private fun protocol() = PROTOCOL_LIBRARY.first { it.id == PROTOCOL_ID }

    private fun template() = PROGRAM_TEMPLATES.first { it.id == TEMPLATE_ID }

    private fun baseProgram(id: String = "p") = Program(id = id, name = "Base")

    private fun sessionsOf(program: Program): List<com.example.kpkn.data.models.Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }

    /** Pasos de calentamiento reales materializados, porcentaje por ejercicio. */
    private fun warmupPercents(program: Program): List<List<Double>> =
        sessionsOf(program).flatMap { it.allExercises() }
            .filter { it.warmupSets.isNotEmpty() }
            .map { exercise -> exercise.warmupSets.map { it.percentageOfWorkingWeight } }

    private fun applyProtocolWithOptions(options: TrainingOptions): Program =
        ProgramProtocolEngine.applyProtocol(baseProgram(), protocol(), SeqIds(), defaultOptions = options)

    // ── Contrato de la elección en la PRIMERA materialización ────────────────

    @Test
    fun legacyCallerWithoutOptionsKeepsPresetChoice() {
        val applied = ProgramProtocolEngine.applyProtocol(baseProgram(), protocol(), SeqIds())
        assertNull(
            "Sin opciones (caller legado) no se escribe elección: rige el preset",
            applied.planWarmupConfig,
        )
    }

    @Test
    fun firstMaterializationAppliesDisabledWarmups() {
        val applied = applyProtocolWithOptions(TrainingOptions(warmup = emptyList()))
        assertEquals(
            "La elección vacía debe llegar en la PRIMERA materialización (bug del copy posterior)",
            emptyList<SetRecipe>(),
            applied.planWarmupConfig,
        )
        // La receta conserva sus propios calentamientos de autor; el plan ya no
        // añade los suyos: nunca más pasos que el control con preset.
        val control = ProgramProtocolEngine.applyProtocol(baseProgram(), protocol(), SeqIds())
        assertTrue(
            "Desactivar no añade pasos de plan: control=${warmupPercents(control)} " +
                "desactivado=${warmupPercents(applied)}",
            warmupPercents(applied).sumOf { it.size } <= warmupPercents(control).sumOf { it.size },
        )
    }

    @Test
    fun firstMaterializationAppliesCustomWarmups() {
        val custom = listOf(SetRecipe(reps = 6, percent = 35.0))
        val applied = applyProtocolWithOptions(TrainingOptions(warmup = custom))
        assertEquals(
            "Los pasos propios quedan en la config del programa",
            listOf(35.0),
            applied.planWarmupConfig?.map { it.percent },
        )
        // Y se materializan de verdad en los huecos que la receta deja libres.
        val percents = warmupPercents(applied).flatten()
        assertTrue(
            "Pasos propios aplicados en los huecos de la receta; percents=$percents",
            35.0 in percents,
        )
    }

    @Test
    fun persistedChoiceWinsOverDisabledOptionsWithoutMixing() {
        val authorChoice = listOf(SetRecipe(reps = 2, percent = 45.0))
        val base = baseProgram("autor").copy(planWarmupConfig = authorChoice)
        val applied = ProgramProtocolEngine.applyProtocol(
            base,
            protocol(),
            SeqIds(),
            defaultOptions = TrainingOptions(warmup = emptyList()),
        )
        assertEquals(
            "La elección ya guardada manda sobre las opciones de esta llamada",
            listOf(45.0),
            applied.planWarmupConfig?.map { it.percent },
        )
        val percents = warmupPercents(applied).flatten()
        assertTrue(
            "La elección guardada es la que se materializa en los huecos de la receta; percents=$percents",
            45.0 in percents,
        )
    }

    // ── Misma garantía en la ruta de plantilla ───────────────────────────────

    @Test
    fun templateFirstMaterializationAppliesTheChoice() {
        val disabled = ProgramTemplateEngine.applyTemplate(
            baseProgram("t-desactivado"),
            template(),
            forceReplace = true,
            defaultOptions = TrainingOptions(warmup = emptyList()),
        ).program
        assertEquals(
            "Plantilla: elección vacía en la primera materialización",
            emptyList<SetRecipe>(),
            disabled.planWarmupConfig,
        )

        val custom = listOf(SetRecipe(reps = 6, percent = 35.0))
        val customProgram = ProgramTemplateEngine.applyTemplate(
            baseProgram("t-custom"),
            template(),
            forceReplace = true,
            defaultOptions = TrainingOptions(warmup = custom),
        ).program
        assertEquals(
            "Plantilla: pasos propios en la primera materialización",
            listOf(35.0),
            customProgram.planWarmupConfig?.map { it.percent },
        )
        assertTrue(
            "Plantilla: los pasos propios llegan a los huecos de la receta; " +
                "percents=${warmupPercents(customProgram).flatten()}",
            35.0 in warmupPercents(customProgram).flatten(),
        )
    }
}
