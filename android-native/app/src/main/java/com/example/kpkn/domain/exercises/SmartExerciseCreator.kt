package com.example.kpkn.domain.exercises

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.CustomExerciseReference
import com.example.kpkn.data.models.ExerciseCover
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import java.util.UUID

/**
 * Creación de ejercicios propios por parecido (MOT · E1–E3, AUD1 §3). Cuando una búsqueda no encuentra nada, la persona puede crear el
 * ejercicio y sus datos técnicos se derivan de UNA referencia confirmada del catálogo:
 *
 * - El parecido lo decide [ExerciseReferenceFinder], que comparte catálogo y léxico con la búsqueda: lee la identidad del nombre
 *   (con erratas y alias), separa la variante pedida (material, estación, lateralidad) y solo CONFIRMA una referencia clara cuya
 *   variante existe. El material escrito prevalece; nunca hay un material «por defecto» que pise al nombre.
 * - Con referencia confirmada se copian de ELLA (no de varios ejercicios) músculos, aportes, cargas y señales de ejecución, y se
 *   anota su procedencia ([CustomExerciseReference]) aparte de la identidad `custom:uuid`.
 * - Sin referencia confirmada (nombre genérico, ambiguo, variante inexistente o desconocido) el ejercicio nace con los datos
 *   DESCONOCIDOS: sin músculos, sin cargas, y la carga axial `null` (no 0), de modo que el motor usa su heurística por nombre y la
 *   persona los completa en el editor. Tampoco se genera texto.
 * - La descripción automática sale solo de una configuración confirmada; la escrita por la persona se respeta siempre.
 *
 * Con el catálogo v2 completo ([ExerciseCatalogV2]) se comprueba la variante exacta; sin él (instantánea plana de una configuración por
 * ejercicio) se confirma por la identidad del nombre y el material se ajusta como antes.
 */
object SmartExerciseCreator {

    private val IMPLEMENTO_LABELS = mapOf(
        "barbell" to "Barra",
        "dumbbells" to "Mancuerna",
        "ez_bar" to "Barra EZ",
        "cable" to "Polea",
        "machine" to "Máquina",
        "smith_machine" to "Máquina Smith",
        "kettlebell" to "Kettlebell",
        "band" to "Banda elástica",
        "plate" to "Disco",
        "bodyweight" to "Peso Corporal",
        "safety_bar" to "Barra de Seguridad",
        "hex_bar" to "Barra hexagonal",
        "h_bar" to "Barra H",
        "t_bar" to "Barra T",
        "trx" to "TRX",
        "ghd" to "Máquina GHD",
    )

    private val AXIAL_EQUIPMENT = setOf(
        "Barra", "Máquina Smith", "Disco", "Barra hexagonal",
        "Barra de Seguridad", "Barra H", "Barra T",
    )

    /** Multiplicadores (EFC, CNC) por material, solo para una instantánea plana cuyo material no coincide con el de la referencia. */
    private fun equipmentMultipliers(equipment: String): Pair<Double, Double> = when {
        equipment.equals("Barra", ignoreCase = true) -> 1.0 to 1.2
        equipment.equals("Mancuerna", ignoreCase = true) -> 0.9 to 1.1
        equipment.equals("Máquina", ignoreCase = true) || equipment.equals("Polea", ignoreCase = true) -> 0.8 to 0.6
        equipment.equals("Peso Corporal", ignoreCase = true) -> 0.8 to 0.8
        else -> 1.0 to 1.0
    }

    private class CachedFinder(
        val v2: ExerciseCatalogV2?,
        val snapshot: List<ExerciseMuscleInfo>,
        val excludeId: String?,
        val finder: ExerciseReferenceFinder,
    )

    @Volatile
    private var cachedFinder: CachedFinder? = null

    /**
     * El motor de parecidos de este catálogo (se reutiliza mientras no cambie la instantánea ni el catálogo). [excludeId] es el
     * ejercicio propio que se está editando: no puede ser su propia referencia ni darse un parecido a sí mismo.
     */
    private fun finderFor(snapshot: List<ExerciseMuscleInfo>, catalogV2: ExerciseCatalogV2?, excludeId: String? = null): ExerciseReferenceFinder {
        cachedFinder?.let {
            if (it.v2 === catalogV2 && it.snapshot === snapshot && it.excludeId == excludeId) return it.finder
        }
        val others = snapshot.filter { !(excludeId != null && it.id.equals(excludeId, ignoreCase = true)) }
        val finder = if (catalogV2 != null) {
            ExerciseReferenceFinder.forCatalog(catalogV2, others.filter { it.isCustom })
        } else {
            ExerciseReferenceFinder.forSnapshot(others)
        }
        cachedFinder = CachedFinder(catalogV2, snapshot, excludeId, finder)
        return finder
    }

    /**
     * Los parecidos de un nombre, con su resultado (confirmado, similares o ninguno) y el porqué: la API para la interfaz de creación,
     * que enseña los tres primeros y deja elegir. Comparte motor con [preview].
     */
    fun findReferences(
        name: String,
        catalog: List<ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2? = null,
        request: SmartCreateRequest? = null,
    ): ReferenceResult =
        finderFor(catalog, catalogV2, request?.existingId).find(name.trim(), request?.let { explicitRestrictions(it) }.orEmpty())

    /**
     * Crea (o reutiliza, si `existingId` viene en el request) un ejercicio personalizado a partir de la detección automática.
     */
    fun create(
        request: SmartCreateRequest,
        catalog: List<ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2? = null,
    ): ExerciseMuscleInfo = preview(request, catalog, catalogV2).exercise

    /**
     * Creación automática sin formulario: toma el nombre buscado y deriva material, estación y lateralidad de lo que el nombre dice
     * y de la referencia confirmada, si la hay. La persona ajusta lo demás después, al editar el ejercicio.
     */
    fun createAutomatic(
        name: String,
        catalog: List<ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2? = null,
    ): ExerciseMuscleInfo = preview(SmartCreateRequest(name = name.trim(), implementoId = ""), catalog, catalogV2).exercise

    private fun explicitRestrictions(request: SmartCreateRequest): Map<String, String> = buildMap {
        request.implementoId.takeIf { it.isNotBlank() }?.let { put("implement", it) }
        request.estacionId?.let { put("station", it) }
        request.lateralidadId?.let { put("laterality", it) }
    }

    /**
     * Vista previa del creador: el ejercicio derivado, el patrón detectado, cuántos parecidos hubo y si conviene completar los datos a
     * mano (siempre que no haya una referencia confirmada).
     */
    fun preview(
        request: SmartCreateRequest,
        catalog: List<ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2? = null,
    ): SmartCreatePreview {
        val name = request.name.trim()
        val detected = ExercisePatternDetector.detect(name)
        val result = if (name.isBlank()) null else finderFor(catalog, catalogV2, request.existingId).find(name, explicitRestrictions(request))
        val confirmed = result?.confirmed
        val reference: ExerciseMuscleInfo? = confirmed?.let { referenceInfo(it, catalog, catalogV2) }

        // Material: lo escrito (elegido o dicho en el nombre) manda; si no, el de la referencia confirmada; si no, desconocido.
        val parsedRestrictions = result?.parsed?.restrictions.orEmpty()
        val implementId = request.implementoId.takeIf { it.isNotBlank() } ?: parsedRestrictions["implement"]
        val equipment: String? = implementId?.let { IMPLEMENTO_LABELS[it] } ?: reference?.equipment
        val stationId = request.estacionId ?: parsedRestrictions["station"]
            ?: reference?.catalogVariantChips?.firstNotNullOfOrNull { estacionIdFromLabel(it) }
        val lateralityId = request.lateralidadId ?: parsedRestrictions["laterality"]
            ?: reference?.catalogVariantChips?.firstNotNullOfOrNull { lateralidadIdFromLabel(it) }
        val chosenOptions = buildList {
            equipment?.let { add(it) }
            stationId?.let { add(STATION_LABELS[it] ?: it) }
            lateralityId?.let { add(LATERALITY_LABELS[it] ?: it) }
        }

        val existing = request.existing
        // Datos escritos a mano en un ejercicio propio que NO nació de una referencia: se conservan al editar (no se pisan con lo
        // derivado del nombre). Un propio nacido de una referencia sí se vuelve a derivar.
        val keepManual = existing != null && existing.creationReference == null && existing.hasDataEntered()
        val involved = when {
            !request.musclesOverride.isNullOrEmpty() -> buildInvolvedMuscles(request, reference)
            existing != null && keepManual && existing.involvedMuscles.isNotEmpty() -> existing.involvedMuscles
            else -> buildInvolvedMuscles(request, reference)
        }
        val userDescription = request.description?.trim()?.takeIf { it.isNotBlank() }
        val generated = if (userDescription == null && reference != null) {
            describeConfirmedReference(reference, equipment, involved)
        } else {
            null
        }
        val displayName = name.ifBlank { "Ejercicio nuevo" }
        val derived = adjustedMetrics(reference, equipment, catalogV2 != null)
        val patternId = reference?.movementPattern ?: detected?.patternId
        fun <T> derivedOrKept(kept: T?, fromReference: T?): T? = if (keepManual) kept ?: fromReference else fromReference

        val base = existing ?: ExerciseMuscleInfo(id = "", name = displayName)
        val exercise = base.copy(
            id = request.existingId ?: existing?.id ?: "custom:${UUID.randomUUID()}",
            name = displayName,
            alias = name,
            description = userDescription ?: generated,
            involvedMuscles = involved,
            equipment = if (keepManual) equipment ?: existing!!.equipment else equipment,
            category = if (keepManual) existing!!.category ?: reference?.category ?: "Fuerza" else reference?.category ?: "Fuerza",
            type = if (keepManual) existing!!.type ?: reference?.type ?: "Básico" else reference?.type ?: "Básico",
            force = derivedOrKept(existing?.force, reference?.force),
            chain = derivedOrKept(existing?.chain, reference?.chain),
            bodyPart = derivedOrKept(existing?.bodyPart, reference?.bodyPart),
            tier = derivedOrKept(existing?.tier, reference?.tier),
            isCustom = true,
            // Sin referencia confirmada TODO queda desconocido (null): el motor usa su heurística por nombre y la carga axial no se
            // certifica como cero. Con ella, lo copia tal cual de esa configuración.
            efc = derivedOrKept(existing?.efc, derived.first),
            cnc = derivedOrKept(existing?.cnc, derived.second),
            ssc = derivedOrKept(existing?.ssc, derived.third),
            ttc = derivedOrKept(existing?.ttc, reference?.ttc),
            axialLoadFactor = derivedOrKept(existing?.axialLoadFactor, reference?.axialLoadFactor),
            averageRestSeconds = derivedOrKept(existing?.averageRestSeconds, reference?.averageRestSeconds),
            setupCues = derivedOrKept(existing?.setupCues, reference?.setupCues?.distinct()?.take(3)),
            executionCues = derivedOrKept(existing?.executionCues, reference?.executionCues?.distinct()?.take(3)),
            commonMistakes = derivedOrKept(
                existing?.commonMistakes,
                reference?.commonMistakes?.distinctBy { it.mistake }?.take(4)?.map { CommonMistake(it.mistake, it.correction) },
            ),
            anatomicalConsiderations = derivedOrKept(
                existing?.anatomicalConsiderations,
                reference?.anatomicalConsiderations?.distinctBy { it.trait }?.take(4)?.map { AnatomicalConsideration(it.trait, it.advice) },
            ),
            // La identidad de patrón del catálogo solo viaja con una referencia confirmada: el patrón del detector es de presentación.
            movementPattern = derivedOrKept(existing?.movementPattern, reference?.movementPattern),
            // Chips user-facing de la variante (Implemento · Estación · Lateralidad).
            catalogVariantChips = chosenOptions,
            creationReference = confirmed?.let {
                CustomExerciseReference(
                    definitionId = it.definitionId,
                    configurationId = it.configurationId ?: reference?.catalogConfigurationId,
                    catalogRevision = catalogV2?.catalogRevision ?: reference?.catalogRevision,
                    confirmed = true,
                )
            } ?: existing?.creationReference,
            descriptionOrigin = when {
                userDescription != null -> "USER"
                generated != null -> "GENERATED"
                else -> null
            },
            // La portada que la persona eligió se conserva al editar; sin elección, la ilustración del patrón.
            cover = existing?.cover ?: patternId?.let { ExerciseCover.pattern(it) },
        )
        return SmartCreatePreview(
            exercise = exercise,
            detectedPattern = detected,
            matchCount = result?.candidates?.size ?: 0,
            manualRecommended = confirmed == null,
            reference = reference,
            referenceResult = result,
        )
    }

    /** La ficha de la referencia confirmada: la configuración exacta del catálogo v2, o la entrada de la instantánea plana. */
    private fun referenceInfo(
        candidate: ReferenceCandidate,
        snapshot: List<ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2?,
    ): ExerciseMuscleInfo? {
        if (catalogV2 != null && !candidate.isCustom) {
            val configurationId = candidate.configurationId ?: return null
            return catalogV2.toLegacySelection(
                ExerciseSelectionV2(
                    definitionId = candidate.definitionId,
                    configurationId = configurationId,
                    catalogRevision = catalogV2.catalogRevision,
                ),
            )
        }
        return snapshot.firstOrNull { (it.catalogDefinitionId ?: it.id) == candidate.definitionId }
    }

    private fun buildInvolvedMuscles(request: SmartCreateRequest, reference: ExerciseMuscleInfo?): List<InvolvedMuscle> {
        if (!request.musclesOverride.isNullOrEmpty()) {
            return request.musclesOverride.map { muscle ->
                muscle.copy(volumeContribution = resolveMuscleVolumeContribution(muscle))
            }
        }
        // De UNA referencia: sus músculos con su rol y su aporte (explícito o el de respaldo 1/0,5/0,4), nada de otros ejercicios.
        return reference?.involvedMuscles.orEmpty().map { muscle ->
            muscle.copy(
                volumeContribution = resolveMuscleVolumeContribution(muscle),
                // Retired adapted explanations are not carried into the new catalog/editor projections.
                biomechanicalReason = null,
            )
        }
    }

    /**
     * Métricas de la referencia. Con la configuración exacta del catálogo v2 se copian tal cual; con una instantánea plana cuyo
     * material no coincide con el pedido se ajusta por material como antes (y solo en ese caso).
     */
    private fun adjustedMetrics(
        reference: ExerciseMuscleInfo?,
        equipment: String?,
        exactConfiguration: Boolean,
    ): Triple<Double?, Double?, Double?> {
        if (reference == null) return Triple(null, null, null)
        val sameEquipment = equipment == null || reference.equipment == null || reference.equipment.equals(equipment, ignoreCase = true)
        if (exactConfiguration || sameEquipment) return Triple(reference.efc, reference.cnc, reference.ssc)
        val (efcMult, cncMult) = equipmentMultipliers(equipment!!)
        val sscMult = if (equipment in AXIAL_EQUIPMENT) 1.0 else 0.4
        return Triple(
            reference.efc?.let { (it * efcMult).coerceIn(0.5, 5.0) },
            reference.cnc?.let { (it * cncMult).coerceIn(0.5, 5.0) },
            reference.ssc?.let { (it * sscMult).coerceIn(0.0, 2.0) },
        )
    }

    private val STATION_LABELS = mapOf("seated" to "Sentado", "standing" to "De pie")
    private val LATERALITY_LABELS = mapOf(
        "bilateral" to "Bilateral",
        "unilateral" to "Unilateral",
        "alternating" to "Alternada",
    )

    val IMPLEMENTO_IDS: List<String> = IMPLEMENTO_LABELS.keys.sorted()
    val ESTACION_IDS: List<String> = STATION_LABELS.keys.sorted()
    val LATERALIDAD_IDS: List<String> = LATERALITY_LABELS.keys.sorted()

    fun implementoLabel(id: String): String = IMPLEMENTO_LABELS[id] ?: id
    fun estacionLabel(id: String): String = STATION_LABELS[id] ?: id
    fun lateralidadLabel(id: String): String = LATERALITY_LABELS[id] ?: id

    /** Devuelve el id de implemento a partir de la etiqueta guardada en chips. */
    fun implementoIdFromLabel(label: String): String? =
        IMPLEMENTO_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key

    fun estacionIdFromLabel(label: String): String? =
        STATION_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key

    fun lateralidadIdFromLabel(label: String): String? =
        LATERALITY_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key
}

data class SmartCreateRequest(
    val name: String,
    val implementoId: String,
    val estacionId: String? = null,
    val lateralidadId: String? = null,
    /** Descripción escrita por la persona; null/blanco dispara el borrador automático (solo con referencia confirmada). */
    val description: String? = null,
    /** Override manual del involucramiento muscular (rol + aporte). */
    val musclesOverride: List<InvolvedMuscle>? = null,
    /** Id existente para editar un ejercicio personalizado; null crea uno nuevo. */
    val existingId: String? = null,
    /**
     * El ejercicio propio que se está editando, tal como está guardado: lo que no se deriva del nombre (portada, fotos, vídeos,
     * procedencia y datos escritos a mano) se conserva al volver a guardar.
     */
    val existing: ExerciseMuscleInfo? = null,
)

data class SmartCreatePreview(
    val exercise: ExerciseMuscleInfo,
    val detectedPattern: ExercisePatternDetector.DetectedMovementPattern?,
    /** Cuántos parecidos se encontraron (como mucho tres). */
    val matchCount: Int,
    /** true siempre que no haya una referencia confirmada: los datos quedan por completar. */
    val manualRecommended: Boolean,
    /** La referencia CONFIRMADA de la que salen los datos; null si no la hay. */
    val reference: ExerciseMuscleInfo? = null,
    /** El resultado completo del motor de parecidos: los tres candidatos y el porqué de la confirmación o la abstención. */
    val referenceResult: ReferenceResult? = null,
)

/** true si el ejercicio ya lleva datos técnicos o músculos escritos (a mano o heredados): no deben pisarse al editar. */
private fun ExerciseMuscleInfo.hasDataEntered(): Boolean =
    efc != null || cnc != null || ssc != null || ttc != null || axialLoadFactor != null || involvedMuscles.isNotEmpty() ||
        !setupCues.isNullOrEmpty() || !executionCues.isNullOrEmpty()
