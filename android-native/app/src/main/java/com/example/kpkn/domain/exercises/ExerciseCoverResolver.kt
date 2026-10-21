package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseCover
import com.example.kpkn.data.models.ExerciseCoverKind
import com.example.kpkn.data.models.ExerciseMuscleInfo

/** Qué se muestra como portada de un ejercicio propio. */
sealed interface ExerciseCoverPresentation {
    /** Una foto de la persona, en el almacén privado de medios. */
    data class UserPhoto(val mediaId: String) : ExerciseCoverPresentation

    /** La imagen técnica revisada de una configuración del catálogo, como imagen de referencia. */
    data class CatalogImage(val definitionId: String, val configurationId: String) : ExerciseCoverPresentation

    /** La ilustración del patrón: respaldo cuando no hay foto ni imagen compatible. */
    data class PatternIllustration(val patternId: String?) : ExerciseCoverPresentation
}

/**
 * Portada de un ejercicio propio (MOT · E4/E5, AUD1 §3.5). La elección de la persona manda (una foto de su álbum, o la imagen de
 * referencia de una variante próxima); si ese medio ya no existe, la referencia cambió de revisión o su imagen ya no está, se vuelve a
 * la ilustración del patrón en vez de mostrar una imagen que no corresponde. Es PRESENTACIÓN: no cambia métricas, ids de
 * entrenamiento ni los medios del ejercicio del catálogo.
 */
object ExerciseCoverResolver {

    /**
     * La portada que corresponde mostrar. [mediaExists] dice si un medio del almacén privado sigue ahí; [catalogImageAvailable] si el
     * catálogo tiene hoy una imagen revisada para esa definición y configuración; [currentCatalogRevision] es la revisión vigente
     * (null = no se exige).
     */
    fun resolve(
        info: ExerciseMuscleInfo,
        mediaExists: (mediaId: String) -> Boolean = { true },
        catalogImageAvailable: (definitionId: String, configurationId: String) -> Boolean = { _, _ -> false },
        currentCatalogRevision: String? = null,
    ): ExerciseCoverPresentation {
        val cover = info.cover
        if (cover != null) {
            when (cover.kind) {
                ExerciseCoverKind.USER_PHOTO -> {
                    val mediaId = cover.mediaId
                    if (!mediaId.isNullOrBlank() && mediaExists(mediaId)) return ExerciseCoverPresentation.UserPhoto(mediaId)
                }
                ExerciseCoverKind.CATALOG_REFERENCE -> {
                    val definitionId = cover.definitionId
                    val configurationId = cover.configurationId
                    val sameRevision = currentCatalogRevision == null || cover.catalogRevision == currentCatalogRevision
                    if (!definitionId.isNullOrBlank() && !configurationId.isNullOrBlank() && sameRevision &&
                        catalogImageAvailable(definitionId, configurationId)
                    ) {
                        return ExerciseCoverPresentation.CatalogImage(definitionId, configurationId)
                    }
                }
                ExerciseCoverKind.PATTERN -> Unit
            }
        }
        return ExerciseCoverPresentation.PatternIllustration(patternIdFor(info))
    }

    /** El patrón de la ilustración: el elegido, el de la referencia, o el que se lee del nombre. */
    fun patternIdFor(info: ExerciseMuscleInfo): String? =
        info.cover?.patternId?.takeIf { it.isNotBlank() }
            ?: info.movementPattern?.takeIf { it.isNotBlank() }
            ?: ExercisePatternDetector.detect(info.name)?.patternId

    /**
     * La imagen de referencia que SE PUEDE reutilizar para este ejercicio propio, o null. Exige una referencia confirmada con su
     * configuración y que el material del propio coincida con el de esa configuración ([referenceImplementId], p. ej. `cable`): una
     * imagen de polea no ilustra un ejercicio de mancuernas. Sin imagen revisada para esa configuración tampoco hay reutilización.
     */
    fun suggestReferenceCover(
        info: ExerciseMuscleInfo,
        referenceImplementId: String?,
        catalogImageAvailable: (definitionId: String, configurationId: String) -> Boolean,
    ): ExerciseCover? {
        val reference = info.creationReference?.takeIf { it.confirmed } ?: return null
        val configurationId = reference.configurationId ?: return null
        val revision = reference.catalogRevision ?: return null
        val ownImplement = info.equipment?.let { SmartExerciseCreator.implementoIdFromLabel(it) } ?: return null
        if (referenceImplementId == null || ownImplement != referenceImplementId) return null
        if (!catalogImageAvailable(reference.definitionId, configurationId)) return null
        return ExerciseCover.catalogReference(reference.definitionId, configurationId, revision)
    }

    /** Aplica la portada elegida sin tocar nada más del ejercicio (el UUID, la descripción y los datos manuales se conservan). */
    fun withCover(info: ExerciseMuscleInfo, cover: ExerciseCover?): ExerciseMuscleInfo = info.copy(cover = cover)
}
