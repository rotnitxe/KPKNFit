package com.example.kpkn.domain.exercises.catalogv2

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAxialSscConsistencyTest {
    @Test
    fun catalogHasNoContradictoryAxialSscPairs() {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        val catalog = ExerciseCatalogV2Loader.decodeApproved(file.readText())
        val highSscNoAxial = mutableListOf<String>()
        val highAxialLowSsc = mutableListOf<String>()
        catalog.families.forEach { family ->
            family.definitions.forEach { definition ->
                definition.configurations.forEach { configuration ->
                    val axial = configuration.profile.axialLoadFactor
                    val ssc = configuration.profile.ssc
                    val id = "${definition.id}/${configuration.id} axial=$axial ssc=$ssc"
                    if (axial == 0.0 && ssc >= 0.9) highSscNoAxial += id
                    if (axial >= 0.6 && ssc <= 0.2) highAxialLowSsc += id
                }
            }
        }
        assertTrue(
            "axial=0 && ssc>=0.9: $highSscNoAxial",
            highSscNoAxial.isEmpty(),
        )
        assertTrue(
            "axial>=0.6 && ssc<=0.2: $highAxialLowSsc",
            highAxialLowSsc.isEmpty(),
        )
    }
}
