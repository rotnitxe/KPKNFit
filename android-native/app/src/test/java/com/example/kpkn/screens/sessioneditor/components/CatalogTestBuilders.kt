package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogEvidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseFamilyV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import java.io.File

/**
 * Catálogos de prueba del selector (LV5): uno pequeño hecho a mano, para fijar una regla con pocas piezas, y el catálogo REAL de los
 * assets, para comprobar contra los datos que ve la persona.
 */
internal object CatalogTestBuilders {

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )

    /** Una configuración con el implemento en su id (`<definición>__<implemento>`), como las del catálogo real. */
    fun configuration(
        definitionId: String,
        implement: String?,
        pattern: String,
        muscles: List<String>,
        extraOptions: Map<String, String> = emptyMap(),
    ): ExerciseConfigurationV2 {
        val options = buildMap {
            if (implement != null) put("implement", implement)
            putAll(extraOptions)
        }
        val id = listOfNotNull(definitionId, implement).joinToString("__")
        return ExerciseConfigurationV2(
            id = id,
            selectedOptions = options,
            displaySummary = options.values.joinToString(" · "),
            profile = ResolvedExerciseProfileV2(
                movementPatternId = pattern,
                bodyRegion = ExerciseBodyRegionV2.UPPER,
                kineticChain = ExerciseKineticChainV2.ANTERIOR,
                laterality = ExerciseLateralityV2.BILATERAL,
                equipmentId = implement ?: "bodyweight",
                loadMode = "external",
                primaryMuscles = muscles,
                efc = 1.0,
                cnc = 1.0,
                ssc = 0.0,
                ttc = 1.0,
                axialLoadFactor = 0.0,
                technicalDifficulty = 2.0,
                resistanceProfile = "test",
                setupCues = listOf("Setup."),
                executionCues = listOf("Execute."),
                performanceProfileId = id,
            ),
            evidence = evidence(),
        )
    }

    /** Una definición con una configuración por implemento; [defaultImplement] es la que el catálogo marca por defecto. */
    fun definition(
        id: String,
        name: String,
        pattern: String,
        muscles: List<String>,
        implements: List<String> = emptyList(),
        defaultImplement: String? = implements.firstOrNull(),
    ): ExerciseDefinitionV2 {
        val configurations = if (implements.isEmpty()) {
            listOf(configuration(id, null, pattern, muscles))
        } else {
            implements.map { configuration(id, it, pattern, muscles) }
        }
        return ExerciseDefinitionV2(
            id = id,
            familyId = "family",
            kind = ExerciseDefinitionKindV2.PARENT,
            canonicalName = name,
            description = "Descripción de prueba de $name.",
            optionAxes = if (implements.isEmpty()) emptyList() else listOf("implement"),
            configurations = configurations,
            defaultConfigurationId = listOfNotNull(id, defaultImplement.takeIf { implements.isNotEmpty() }).joinToString("__"),
            evidence = evidence(),
        )
    }

    fun catalog(vararg definitions: ExerciseDefinitionV2): ExerciseCatalogV2 = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "test-revision",
        ontologyRevision = "test-ontology",
        families = listOf(
            ExerciseFamilyV2(
                id = "family",
                canonicalName = "Familia",
                definitions = definitions.toList(),
                evidence = evidence(),
            ),
        ),
    )

    /** El catálogo real que se empaqueta en la app (`assets/exercise_catalog_v2.json`). */
    val real: ExerciseCatalogV2 by lazy {
        val resource = CatalogTestBuilders::class.java.classLoader?.getResource("exercise_catalog_v2.json")
        val file = if (resource != null) {
            File(resource.toURI())
        } else {
            listOf(
                "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
                "../android-native/app/src/main/assets/exercise_catalog_v2.json",
                "android-native/app/src/main/assets/exercise_catalog_v2.json",
                "src/main/assets/exercise_catalog_v2.json",
            ).map(::File).first { it.exists() }
        }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }

    fun realDefinition(id: String): ExerciseDefinitionV2 =
        real.families.flatMap { it.definitions }.first { it.id == id }
}
