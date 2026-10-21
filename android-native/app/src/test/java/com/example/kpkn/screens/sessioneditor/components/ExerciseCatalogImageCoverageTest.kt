package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.R
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Approval manifests are the independent source for historical image expectations. */
class ExerciseCatalogImageCoverageTest {
    private val appRoot = listOf(File("."), File("app"), File("android-native/app"))
        .first { File(it, "src/main/assets/exercise_catalog_v2.json").isFile }.canonicalFile
    private val repoRoot = checkNotNull(checkNotNull(appRoot.parentFile).parentFile)
    private val catalog by lazy {
        ExerciseCatalogV2Loader.decodeApproved(File(appRoot, "src/main/assets/exercise_catalog_v2.json").readText())
    }

    private fun manifest(name: String) = Json.parseToJsonElement(
        File(repoRoot, "catalog/exercises/images/$name").readText(),
    ).jsonObject

    private fun drawable(name: String) = R.drawable::class.java.getField(name).getInt(null)

    @Test
    fun every_historical_approval_has_an_exact_published_runtime_binding() {
        val images = manifest("historical-approved-technique-images.json").getValue("images").jsonArray
        val configurationIds = catalog.families.flatMap { it.definitions }
            .flatMap { it.configurations }.map { it.id }.toSet()
        assertEquals(81, images.size)
        var checked = 0
        images.forEach { element ->
            val image = element.jsonObject
            val expected = drawable(image.getValue("resourceName").jsonPrimitive.content)
            image.getValue("configurationIds").jsonArray.forEach { idElement ->
                val id = idElement.jsonPrimitive.content
                assertTrue(id, id in configurationIds)
                assertEquals(id, expected, ExerciseTechniqueImageLookup.resolveImageResId(
                    catalogDefinitionId = image.getValue("definitionId").jsonPrimitive.content,
                    exerciseDbId = id, exerciseId = "persisted-occurrence",
                    catalogConfigurationId = id,
                ))
                checked++
            }
        }
        assertEquals(84, checked)
    }

    @Test
    fun every_available_catalog_image_is_visible_for_the_selected_implement() {
        val report = mutableListOf("configurationId,resourceId,pickerVisible")
        catalog.families.flatMap { it.definitions }.forEach { definition ->
            definition.configurations.forEach { configuration ->
                val implement = configuration.selectedOptions["implement"]
                val expected = ExerciseTechniqueImageLookup.resolveImageResId(
                    catalogDefinitionId = definition.id, exerciseDbId = null, exerciseId = "occurrence",
                    catalogConfigurationId = configuration.id, selectedImplementation = implement,
                )
                val variants = exerciseCatalogImageVariants(definition.id, configuration.id, implement)
                val selected = selectedCatalogImageVariantIndex(variants, implement)
                if (expected == null) {
                    assertTrue(configuration.id, variants.isEmpty())
                } else {
                    assertNotNull(configuration.id, selected)
                    assertEquals(configuration.id, expected, variants[selected!!].imageResId)
                }
                report += "${configuration.id},${expected ?: ""},${selected != null}"
            }
        }
        File(appRoot, "build/reports/exercise-image-coverage/catalog-configurations.csv").also {
            it.parentFile.mkdirs()
            it.writeText(report.joinToString("\n", postfix = "\n"))
        }
    }

    @Test
    fun folded_cards_show_the_resolved_image_before_any_implement_chip_is_selected() {
        catalog.families.flatMap { it.definitions }.forEach { definition ->
            definition.configurations.forEach { configuration ->
                val expected = ExerciseTechniqueImageLookup.resolveImageResId(
                    catalogDefinitionId = definition.id, exerciseDbId = null, exerciseId = "occurrence",
                    catalogConfigurationId = configuration.id,
                )
                val variants = exerciseCatalogImageVariants(definition.id, configuration.id)
                assertEquals(configuration.id, expected, variants.firstOrNull()?.imageResId)
            }
        }
    }

    @Test
    fun an_explicit_configuration_wins_over_stale_definition_db_id_and_implement() {
        assertEquals(R.drawable.exercise_press_de_banca_plano, ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = "high_bar_back_squat", exerciseDbId = "bench_press__dumbbells",
            exerciseId = "occurrence", catalogConfigurationId = "bench_press__barbell",
            selectedImplementation = "dumbbells",
        ))
        assertEquals(R.drawable.exercise_forward_lunge_cable_batch20261008,
            ExerciseTechniqueImageLookup.resolveImageResId(
                catalogDefinitionId = "forward_lunge", exerciseDbId = "forward_lunge__barbell",
                exerciseId = "occurrence", catalogConfigurationId = "forward_lunge__cable",
                selectedImplementation = "barbell",
            ))
        assertEquals(R.drawable.exercise_sentadilla_sissy_maquina,
            ExerciseTechniqueImageLookup.resolveImageResId(
                "sissy_squat", "sissy_squat__bodyweight", "occurrence", "sissy_squat__machine",
            ))
        assertEquals(R.drawable.exercise_pendlay_row_barbell,
            ExerciseTechniqueImageLookup.resolveImageResId(
                "pendlay_row", "pendlay_row__cable", "occurrence", "pendlay_row__barbell",
            ))
        assertNull(ExerciseTechniqueImageLookup.resolveImageResId(
            "pull_up", "pull_up__pronated__medium", "occurrence", "pull_up__supinated__close",
        ))
        assertNull(ExerciseTechniqueImageLookup.resolveImageResId(
            "hip_abduction", "hip_abduction__seated__machine__unilateral", "occurrence",
            "hip_abduction__standing__machine__unilateral",
        ))
    }

    @Test
    fun flat_and_elevated_pike_images_are_distinct_and_hyperextension_uses_its_correction() {
        assertEquals(R.drawable.exercise_pike_push_up_bodyweight_batch20261008,
            ExerciseTechniqueImageLookup.resolveImageResId("pike_push_up", null, "occurrence", "pike_push_up__flat"))
        assertEquals(R.drawable.exercise_pike_push_up_feet_elevated,
            ExerciseTechniqueImageLookup.resolveImageResId("pike_push_up", null, "occurrence", "pike_push_up__feet_elevated"))
        assertEquals(listOf(R.drawable.exercise_back_hiperextension_45_zercher_espalda_baja_default),
            ExerciseTechniqueImageLookup.variants("back_hiperextension_45_zercher_espalda_baja").map { it.imageResId })
        assertNull(ExerciseTechniqueImageLookup.resolveImageResId(
            "pull_up", null, "occurrence", "pull_up__supinated__wide",
        ))
    }

    @Test
    fun all_three_manifests_ship_their_webp_files_and_exact_hashes_without_duplicate_pngs() {
        val manifests = listOf("reviewed-technique-images.json", "historical-approved-technique-images.json",
            "legacy-webp-conversions.json")
        val names = mutableSetOf<String>()
        manifests.forEach { name ->
            manifest(name).getValue("images").jsonArray.forEach { element ->
                val image = element.jsonObject
                val resource = image.getValue("resourceName").jsonPrimitive.content
                assertTrue(resource, names.add(resource))
                drawable(resource)
                val file = File(repoRoot, image.getValue("file").jsonPrimitive.content)
                assertTrue(file.path, file.isFile)
                assertEquals("webp", file.extension)
                val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                assertEquals(resource, image.getValue("webpSha256").jsonPrimitive.content, actual)
                assertTrue(resource, !File(file.parentFile, "$resource.png").exists())
            }
        }
        assertEquals(623, names.size)
    }
}
