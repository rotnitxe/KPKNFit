package com.example.kpkn.data.media.technique

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.exercises.ExercisePatternDetector
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.RETIRED_CONFIGURATION_REPLACEMENTS
import java.io.File

/**
 * Completa el [ExerciseRef] del análisis con lo que sabe el catálogo v2: de los ids del medio saca el patrón de movimiento, el
 * material y la lateralidad que el resolvedor de perfiles necesita. El `ExerciseRef` de la petición llega PARCIAL (el Worker solo
 * sabe los ids de catálogo, el nombre y el perfil elegido por el usuario); este es el que de verdad se resuelve.
 *
 * Orden de la búsqueda, la primera que acierta:
 * 1. `configurationId` del medio (o su sustituta, si la configuración se retiró del catálogo): configuración exacta, con su
 *    patrón, material y lateralidad.
 * 2. `definitionId`: la definición, con el patrón de su configuración por defecto; el material y la lateralidad quedan como los
 *    traía la petición (sin dato, desde el Worker), porque no se sabe qué configuración se hizo (un material equivocado haría
 *    buscar un disco donde no lo hay).
 * 3. `canonicalExerciseId` del medio (como configuración y como definición): así un vídeo grabado antes de que el medio guardase
 *    los ids del catálogo (Room v29) no queda como «propio» por error. Un id que empieza por `custom:` nunca se busca.
 *
 * Si nada acierta el ejercicio es propio (`isCustom`) y [ExerciseRef.guessedMovementPatternId] es el patrón que
 * [ExercisePatternDetector] deduce del nombre. Sin catálogo (no se pudo cargar) se devuelven los ids tal cual. En los dos casos el
 * patrón, el material y la lateralidad que la petición ya traiga se conservan.
 *
 * [ExerciseRef.userProfileOverride] gana a todo: el de la petición y, si no trae, el de `<id>.capture.json`.
 *
 * Sin estado salvo los índices del catálogo, que se arman una vez: se comparte entre análisis.
 */
class ExerciseRefResolver(catalog: ExerciseCatalogV2?) {
    private class Hit(val definition: ExerciseDefinitionV2, val configuration: ExerciseConfigurationV2)

    /** `false` si no hay catálogo (no se pudo cargar): entonces nada se resuelve y los ids viajan tal cual. */
    val hasCatalog: Boolean = catalog != null

    private val configurations = HashMap<String, Hit>()
    private val definitions = HashMap<String, ExerciseDefinitionV2>()

    init {
        catalog?.families?.forEach { family ->
            family.definitions.forEach { definition ->
                definitions[definition.id.lowercase()] = definition
                definition.configurations.forEach { configuration ->
                    configurations[configuration.id.lowercase()] = Hit(definition, configuration)
                }
            }
        }
    }

    /**
     * El [ExerciseRef] completo de [requested] (el de la petición) con el medio [media] (`null` si ya no existe). Si la petición no
     * trae el perfil elegido por el usuario, lo busca en [overridesFile] (`<id>.capture.json`); E/S bloqueante en ese caso.
     */
    fun resolve(requested: ExerciseRef, media: WorkoutMedia?, overridesFile: File? = null): ExerciseRef {
        val name = requested.name.ifBlank { media?.exerciseName.orEmpty() }.trim()
        val definitionId = trimmed(requested.definitionId ?: media?.catalogDefinitionId)
        val configurationId = trimmed(requested.configurationId ?: media?.catalogConfigurationId)
        val canonicalKey = trimmed(media?.canonicalExerciseId)?.lowercase()?.takeUnless { it.startsWith(CUSTOM_PREFIX) }
        val override = requested.userProfileOverride
            ?: overridesFile?.let { CaptureOverridesFile.read(it).userProfileOverride }

        if (!hasCatalog) {
            // Sin catálogo no se resuelve nada: los ids (y lo que ya traiga la petición) viajan tal cual; el resolvedor aún acierta por definición.
            return unresolved(requested, definitionId, configurationId, name, override, isCustom = definitionId == null && configurationId == null)
        }

        configurationId?.lowercase()?.let { configurationOf(it) }?.let { return fromConfiguration(it, name, override) }
        definitionId?.lowercase()?.let { definitions[it] }?.let { return fromDefinition(it, requested, name, override) }
        if (canonicalKey != null) {
            configurationOf(canonicalKey)?.let { return fromConfiguration(it, name, override) }
            definitions[canonicalKey]?.let { return fromDefinition(it, requested, name, override) }
        }
        // Propio, o con ids que el catálogo ya no conoce: el nombre da la conjetura del patrón.
        return unresolved(requested, definitionId, configurationId, name, override, isCustom = true)
    }

    /** Lo que se sabe sin el catálogo: los ids, y el patrón, el material y la lateralidad solo si la petición ya los traía. */
    private fun unresolved(
        requested: ExerciseRef,
        definitionId: String?,
        configurationId: String?,
        name: String,
        override: AnalysisProfileId?,
        isCustom: Boolean,
    ): ExerciseRef = ExerciseRef(
        definitionId = definitionId,
        configurationId = configurationId,
        movementPatternId = requested.movementPatternId,
        equipmentId = requested.equipmentId,
        laterality = requested.laterality,
        name = name,
        isCustom = isCustom,
        userProfileOverride = override,
        guessedMovementPatternId = guessOf(name),
    )

    /** La configuración de [key] (en minúsculas) o, si se retiró del catálogo, la que la sustituye. */
    private fun configurationOf(key: String): Hit? =
        configurations[key] ?: RETIRED_CONFIGURATION_REPLACEMENTS[key]?.let { configurations[it.lowercase()] }

    private fun fromConfiguration(hit: Hit, name: String, override: AnalysisProfileId?): ExerciseRef = ExerciseRef(
        definitionId = hit.definition.id,
        configurationId = hit.configuration.id,
        movementPatternId = hit.configuration.profile.movementPatternId,
        equipmentId = hit.configuration.profile.equipmentId,
        laterality = hit.configuration.profile.laterality.name,
        name = name.ifBlank { hit.definition.canonicalName },
        isCustom = false,
        userProfileOverride = override,
        guessedMovementPatternId = null,
    )

    private fun fromDefinition(
        definition: ExerciseDefinitionV2,
        requested: ExerciseRef,
        name: String,
        override: AnalysisProfileId?,
    ): ExerciseRef {
        val base = definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
            ?: definition.configurations.firstOrNull()
        return ExerciseRef(
            definitionId = definition.id,
            configurationId = null,
            movementPatternId = base?.profile?.movementPatternId ?: requested.movementPatternId,
            equipmentId = requested.equipmentId,
            laterality = requested.laterality,
            name = name.ifBlank { definition.canonicalName },
            isCustom = false,
            userProfileOverride = override,
            guessedMovementPatternId = null,
        )
    }

    private fun guessOf(name: String): String? = ExercisePatternDetector.detect(name)?.patternId

    /** El id sin espacios alrededor; `null` si está en blanco. */
    private fun trimmed(id: String?): String? = id?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}
