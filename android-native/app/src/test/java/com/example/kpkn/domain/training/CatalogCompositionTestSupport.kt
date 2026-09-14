package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import java.io.File

object CatalogCompositionTestSupport {
    val catalog: ExerciseCatalogV2 by lazy {
        val resource = CatalogV2TestFixture::class.java.classLoader?.getResource("exercise_catalog_v2.json")
        val file = if (resource != null) File(resource.toURI()) else listOf(
            "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
            "../android-native/app/src/main/assets/exercise_catalog_v2.json",
            "android-native/app/src/main/assets/exercise_catalog_v2.json",
        ).map(::File).first { it.exists() }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }

    val metadata: ExerciseCompositionMetadataProvider by lazy {
        CatalogCompositionMetadataProvider.fromCatalog(catalog)
    }

    fun install() {
        CompositionMetadataHolder.current = metadata
    }
}
