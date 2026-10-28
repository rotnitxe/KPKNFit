package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.programs.CatalogDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las etiquetas de la pestaña Estructura (P3b): en español, en el orden de su hoja y sin ningún nombre de enum a la vista. */
class ProgramStructureLabelsTest {

    @Test
    fun objetivos_de_bloque_en_espanol_y_en_orden() {
        assertEquals(BlockGoal.entries.toSet(), BLOCK_GOAL_CHOICES.toSet())
        assertEquals(
            listOf(
                "Acumulación", "Intensificación", "Especificidad", "Realización", "Pico",
                "Descarga", "Puesta a punto", "Densidad", "Personalizado",
            ),
            BLOCK_GOAL_CHOICES.map(::blockGoalLabel),
        )
    }

    @Test
    fun esquemas_de_progresion_en_espanol_y_en_orden() {
        assertEquals(BlockProgressionScheme.entries.toSet(), BLOCK_SCHEME_CHOICES.toSet())
        assertEquals(
            listOf("Sin progresión", "Carga lineal", "Ondulante", "Porcentaje de 1RM", "Tope de RPE"),
            BLOCK_SCHEME_CHOICES.map(::blockProgressionLabel),
        )
    }

    @Test
    fun objetivos_de_mesociclo_en_espanol_y_en_orden() {
        assertEquals(MesocycleGoal.entries.toSet(), MESOCYCLE_GOAL_CHOICES.toSet())
        assertEquals(
            listOf("Acumulación", "Intensificación", "Realización", "Descarga", "Personalizado"),
            MESOCYCLE_GOAL_CHOICES.map(::mesocycleGoalLabel),
        )
    }

    @Test
    fun ninguna_etiqueta_es_un_nombre_de_enum_ni_ingles() {
        val labels = BlockGoal.entries.map(::blockGoalLabel) +
            BlockProgressionScheme.entries.map(::blockProgressionLabel) +
            MesocycleGoal.entries.map(::mesocycleGoalLabel)
        labels.forEach { label ->
            assertTrue("«$label» no debe llevar guion bajo", !label.contains('_'))
            assertTrue("«$label» no debe escribirse como nombre de enum", label != label.uppercase())
        }
        val english = setOf("Taper", "Custom", "Peak", "Deload", "None", "Linear", "Undulating")
        assertTrue(labels.none { it in english })
    }

    @Test
    fun las_duraciones_de_la_biblioteca_tienen_sus_tres_nombres() {
        assertEquals("Semana que se repite", catalogDurationLabel(CatalogDuration.REPEATING_WEEK))
        assertEquals("Ciclo que se repite", catalogDurationLabel(CatalogDuration.REPEATING_CYCLE))
        assertEquals("Ciclo que termina", catalogDurationLabel(CatalogDuration.FINITE_CYCLE))
    }
}
