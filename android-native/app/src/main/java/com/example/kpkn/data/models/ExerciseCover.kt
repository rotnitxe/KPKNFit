package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseCoverKind {
    /** Una foto de la persona (álbum o almacenamiento privado de medios): se guarda el id del medio, nunca una ruta. */
    USER_PHOTO,

    /** La imagen técnica revisada de una configuración del catálogo, como «imagen de referencia» de una variante próxima. */
    CATALOG_REFERENCE,

    /** La ilustración del patrón de movimiento (glifo del APK): respaldo cuando no hay foto ni imagen compatible. */
    PATTERN,
}

/**
 * Portada de un ejercicio propio (MOT · E4/E5, AUD1 §3.5). Es un valor plano (sin polimorfismo) para que un JSON de otra versión
 * nunca rompa la lectura del ejercicio. Qué campos valen depende de [kind]:
 *
 * - USER_PHOTO: [mediaId] del medio en el almacén privado (`WorkoutMedia`), ligado al UUID del ejercicio propio.
 * - CATALOG_REFERENCE: [definitionId] + [configurationId] + [catalogRevision]; la imagen se resuelve al mostrarla, sin guardar
 *   ningún identificador de recurso del APK. Es presentación, NO identidad de entrenamiento.
 * - PATTERN: [patternId] + [styleVersion] de la ilustración.
 */
@Serializable
data class ExerciseCover(
    val kind: ExerciseCoverKind = ExerciseCoverKind.PATTERN,
    val mediaId: String? = null,
    val definitionId: String? = null,
    val configurationId: String? = null,
    val catalogRevision: String? = null,
    val patternId: String? = null,
    val styleVersion: Int = 1,
) {
    companion object {
        fun userPhoto(mediaId: String) = ExerciseCover(kind = ExerciseCoverKind.USER_PHOTO, mediaId = mediaId)

        fun catalogReference(definitionId: String, configurationId: String, catalogRevision: String) = ExerciseCover(
            kind = ExerciseCoverKind.CATALOG_REFERENCE,
            definitionId = definitionId,
            configurationId = configurationId,
            catalogRevision = catalogRevision,
        )

        fun pattern(patternId: String, styleVersion: Int = 1) = ExerciseCover(
            kind = ExerciseCoverKind.PATTERN,
            patternId = patternId,
            styleVersion = styleVersion,
        )
    }
}

/**
 * Procedencia de los datos de un ejercicio propio creado por parecido: de qué definición/configuración se copiaron los músculos y
 * las cargas. Va APARTE de la identidad del ejercicio (su id `custom:uuid` no cambia) y actualizar el catálogo no reescribe los
 * datos del propio.
 */
@Serializable
data class CustomExerciseReference(
    val definitionId: String,
    val configurationId: String? = null,
    val catalogRevision: String? = null,
    /** true = la referencia se confirmó (nombre claro o elegida por la persona); false = solo sugerida. */
    val confirmed: Boolean = true,
)
