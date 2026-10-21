package com.example.kpkn.screens.sessioneditor.design

import android.content.Context
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.ExerciseCoverPresentation
import com.example.kpkn.domain.exercises.ExerciseCoverResolver
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogImageVariants
import java.io.File

/** Lo que pinta la fila de un ejercicio propio como portada: un recurso de imagen, un archivo de foto o nada (entonces, el glifo del patrón). */
internal data class CustomCoverModel(
    val imageResId: Int?,
    val photoFile: File?,
)

/**
 * Resuelve la portada de un ejercicio propio (MOT · E4/E5): su foto del almacén privado de medios, la imagen de referencia del
 * catálogo si es de la revisión vigente y sigue disponible, o el glifo del patrón. Una foto borrada o una referencia retirada no
 * rompen la fila: caen a la ilustración.
 */
internal fun customExerciseCoverModel(context: Context, exercise: ExerciseMuscleInfo): CustomCoverModel {
    if (exercise.cover == null) return CustomCoverModel(null, null)
    val thumbs = runCatching { WorkoutMediaStore(context).thumbsDir() }.getOrNull()
    val presentation = ExerciseCoverResolver.resolve(
        info = exercise,
        mediaExists = { mediaId -> thumbs != null && File(thumbs, "$mediaId.jpg").isFile },
        catalogImageAvailable = { definitionId, configurationId ->
            exerciseCatalogImageVariants(definitionId, configurationId).isNotEmpty()
        },
        currentCatalogRevision = approvedExerciseCatalogV2()?.catalogRevision,
    )
    return when (presentation) {
        is ExerciseCoverPresentation.UserPhoto -> CustomCoverModel(null, thumbs?.let { File(it, "${presentation.mediaId}.jpg") })
        is ExerciseCoverPresentation.CatalogImage -> CustomCoverModel(
            imageResId = exerciseCatalogImageVariants(presentation.definitionId, presentation.configurationId).firstOrNull()?.imageResId,
            photoFile = null,
        )
        is ExerciseCoverPresentation.PatternIllustration -> CustomCoverModel(null, null)
    }
}
