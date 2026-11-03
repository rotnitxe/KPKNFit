package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.screens.programs.isLibraryFamilyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ficha del catálogo no inventa entrenamiento: semanas, días, fases y descargas salen de la receta publicada, y
 * sin receta se muestran como desconocidas o con el dato exacto del catálogo, nunca con un 6 ni con fases fijas.
 */
class CatalogDerivationTest {

    private val entries = PersonalizedPlanCatalog.listedEntries()
        .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) }

    @Test
    fun semanasDiasFasesYDescargasSalenDeLaReceta() {
        val entry = entries.first { catalogRecipe(it)?.weeks?.isNotEmpty() == true }
        val recipe = catalogRecipe(entry)!!

        assertEquals(recipe.weeks.size, catalogWeeks(entry))
        val expectedDays = recipe.daysPerWeek?.takeIf { it > 0 }
            ?: entry.supportedFrequencies.last.takeIf { entry.supportedFrequencies.first == entry.supportedFrequencies.last }
        assertEquals(expectedDays, catalogExactDays(entry))
        assertEquals(
            "una fase por bloque publicado",
            recipe.weeks.map { it.blockIndex }.distinct().size,
            catalogPhaseList(entry).size,
        )
        assertEquals(
            recipe.weeks
                .filter { it.kind == WeekExecutionKind.DELOAD || it.blockGoal == BlockGoal.DELOAD }
                .map { it.weekNumber }
                .toSet(),
            catalogDeloadWeeks(entry),
        )
    }

    @Test
    fun sinRecetaNoSeInventanFasesNiDescargas() {
        val entry = entries.first { catalogRecipe(it) == null }

        assertEquals(entry.durationWeeks, catalogWeeks(entry))
        assertTrue("sin receta no hay fases publicadas", catalogPhaseList(entry).isEmpty())
        assertTrue("sin receta no hay semanas de descarga inventadas", catalogDeloadWeeks(entry).isEmpty())
        val expectedDays = entry.supportedFrequencies.last
            .takeIf { entry.supportedFrequencies.first == entry.supportedFrequencies.last }
        if (expectedDays == null) {
            assertNull("un rango de días no se convierte en un número exacto", catalogExactDays(entry))
        } else {
            assertEquals(expectedDays, catalogExactDays(entry))
        }
    }

    @Test
    fun lasEtiquetasSeDerivanDeLosCamposDelCatalogo() {
        val entry = entries.first { entry ->
            entry.levels.isNotEmpty() && entry.references.isNotEmpty() &&
                entry.requiredEquipment.any {
                    it in setOf("general_gym", "machine", "bodyweight", "support", "pull_up_bar", "dumbbells", "barbell")
                }
        }

        val expectedLevels = entry.levels.sortedBy { it.ordinal }.map {
            when (it) {
                CatalogLevel.BEGINNER -> "Principiante"
                CatalogLevel.INTERMEDIATE -> "Intermedio"
                CatalogLevel.ADVANCED -> "Avanzado"
            }
        }
        assertEquals(expectedLevels, catalogLevelLabels(entry))
        assertEquals(
            entry.references.map {
                when (it) {
                    TrainingReference.POWERLIFTING -> "Fuerza"
                    TrainingReference.HYPERTROPHY -> "Músculo"
                    TrainingReference.POWERBUILDING -> "Fuerza y músculo"
                }
            }.distinct(),
            catalogGoalLabels(entry),
        )
        assertTrue("las etiquetas de material salen de requiredEquipment", catalogEquipmentLabels(entry).isNotEmpty())
        assertTrue("no se inventa 'Sin datos publicados' si hay datos", "Sin datos publicados" !in catalogEquipmentLabels(entry))
    }
}
