package com.example.kpkn.domain.templates

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File

/** Test-only fixture: template contracts must resolve the compiled v2 asset. */
internal object CatalogV2TestFixture {
    fun configurationLookup(): Map<String, ExerciseMuscleInfo> =
        ExerciseCatalogV2Loader.decodeApproved(findCatalogFile().readText()).toLegacyConfigurationLookup()

    private fun findCatalogFile(): File {
        val resource = CatalogV2TestFixture::class.java.classLoader?.getResource("exercise_catalog_v2.json")
        if (resource != null) return File(resource.toURI())
        val candidates = listOf(
            "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
            "../android-native/app/src/main/assets/exercise_catalog_v2.json",
            "android-native/app/src/main/assets/exercise_catalog_v2.json",
        )
        return candidates.asSequence()
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("No se encontró exercise_catalog_v2.json.")
    }
}
