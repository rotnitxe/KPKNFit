package com.example.kpkn.screens.sessioneditor.design

import android.content.Context
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogImageVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class CustomCoverModel(
    val imageResId: Int? = null,
    val photoFile: File? = null,
    val provenance: String = "Ilustración del patrón",
    val unavailableReason: String? = null,
)

/** La miniatura sola no demuestra que una foto exista: la portada exige su original privado. */
internal suspend fun customExerciseCoverModel(context: Context, exercise: ExerciseMuscleInfo): CustomCoverModel = withContext(Dispatchers.IO) {
    val mediaRepository = runCatching { WorkoutMediaRepository.getInstance() }.getOrNull()
    val media = exercise.cover?.mediaId?.let { id -> runCatching { mediaRepository?.getById(id) }.getOrNull() }
    val original = media?.takeIf { it.kind == WorkoutMediaKind.PHOTO }?.filePath?.let(::File)
        ?.takeIf { it.isFile && it.length() > 0 && mediaRepository?.isManagedPath(it) == true }
    val catalog = approvedExerciseCatalogV2()
    presentCustomExerciseCover(exercise, media, original != null, catalog?.catalogRevision,
        imageFor = { definition, configuration -> exerciseCatalogImageVariants(definition, configuration).firstOrNull()?.imageResId },
        materialFor = { definition, configuration -> catalog?.families?.asSequence()?.flatMap { it.definitions.asSequence() }
            ?.firstOrNull { it.id == definition }?.configurations?.firstOrNull { it.id == configuration }?.selectedOptions?.get("implement") })
}

/** Política de presentación, con hechos del archivo y del catálogo inyectados; nunca confunde thumb con original. */
internal fun presentCustomExerciseCover(
    exercise: ExerciseMuscleInfo, media: WorkoutMedia?, originalAvailable: Boolean, catalogRevision: String?,
    imageFor: (String, String) -> Int?, materialFor: (String, String) -> String?,
): CustomCoverModel {
    fun compatible(definitionId: String, configurationId: String): Boolean {
        val material = exercise.equipment?.let(SmartExerciseCreator::implementoIdFromLabel) ?: return false
        return material == materialFor(definitionId, configurationId) && imageFor(definitionId, configurationId) != null
    }
    val presentation = ExerciseCoverResolver.resolve(exercise,
        mediaExists = { it == media?.id && originalAvailable && media?.kind == WorkoutMediaKind.PHOTO },
        catalogImageAvailable = ::compatible, currentCatalogRevision = catalogRevision)
    return when (presentation) {
        is ExerciseCoverPresentation.UserPhoto -> CustomCoverModel(photoFile = media?.filePath?.let(::File), provenance = "Foto privada · elegida por ti")
        is ExerciseCoverPresentation.CatalogImage -> CustomCoverModel(imageResId = imageFor(presentation.definitionId, presentation.configurationId),
            provenance = "Imagen de referencia del catálogo")
        is ExerciseCoverPresentation.PatternIllustration -> CustomCoverModel(unavailableReason = when (exercise.cover?.kind) {
            ExerciseCoverKind.USER_PHOTO -> "La foto original ya no está disponible. Se muestra la ilustración."
            ExerciseCoverKind.CATALOG_REFERENCE -> "La imagen de referencia ya no es compatible o no está disponible. Se muestra la ilustración."
            else -> null
        })
    }
}
