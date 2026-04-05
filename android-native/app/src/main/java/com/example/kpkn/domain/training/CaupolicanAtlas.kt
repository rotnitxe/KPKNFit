package com.example.kpkn.domain.training

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.example.kpkn.R
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class AtlasSide { FRONT, BACK }

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

@Serializable
data class BodyLandmark(
    val id: String,
    val label: String,
    val point: NormalizedPoint,
)

@Serializable
data class MuscleSubzone(
    val id: String,
    val label: String,
    val points: List<NormalizedPoint>,
)

@Serializable
data class MuscleRegion(
    val id: String,
    val muscleName: String,
    val side: AtlasSide,
    val subzones: List<MuscleSubzone>,
)

@Serializable
data class CanonicalMuscleAtlasBinding(
    val muscleName: String,
    val regionIds: List<String>,
)

@Serializable
data class AtlasImageSpec(
    @DrawableRes val drawableResId: Int,
    val expectedResourceName: String,
    val expectedWidth: Int,
    val expectedHeight: Int,
    val expectedSha256: String,
)

@Serializable
data class BodySilhouette(
    val points: List<NormalizedPoint>,
)

@Serializable
data class CaupolicanAtlas(
    val side: AtlasSide,
    val imageSpec: AtlasImageSpec,
    val silhouette: BodySilhouette,
    val landmarks: List<BodyLandmark>,
    val regions: List<MuscleRegion>,
    val bindings: List<CanonicalMuscleAtlasBinding>,
)

@Serializable
data class CaupolicanAtlasBundle(
    val front: CaupolicanAtlas,
    val back: CaupolicanAtlas,
    val exportedAtIso: String = Instant.now().toString(),
)

data class AtlasValidationIssue(
    val code: String,
    val message: String,
)

data class CaupolicanAtlasValidationReport(
    val isValid: Boolean,
    val issues: List<AtlasValidationIssue>,
)

object CaupolicanAtlasRepository {
    val frontAtlas: CaupolicanAtlas = createFrontAtlas()
    val backAtlas: CaupolicanAtlas = createBackAtlas()
    val defaultBundle: CaupolicanAtlasBundle = CaupolicanAtlasBundle(
        front = frontAtlas,
        back = backAtlas,
    )

    fun atlasFor(side: AtlasSide): CaupolicanAtlas = when (side) {
        AtlasSide.FRONT -> frontAtlas
        AtlasSide.BACK -> backAtlas
    }

    fun validateStructure(atlas: CaupolicanAtlas): CaupolicanAtlasValidationReport {
        val issues = mutableListOf<AtlasValidationIssue>()
        val regionMap = atlas.regions.associateBy { it.id }
        val silhouettePoints = atlas.silhouette.points

        if (silhouettePoints.size < 3) {
            issues += AtlasValidationIssue("silhouette_missing", "La silueta corporal necesita al menos 3 puntos.")
        }

        atlas.landmarks.forEach { landmark ->
            if (!landmark.point.isNormalized()) {
                issues += AtlasValidationIssue(
                    "landmark_out_of_bounds",
                    "El landmark ${landmark.id} sale de los límites normalizados."
                )
            }
        }

        atlas.regions.forEach { region ->
            if (region.subzones.isEmpty()) {
                issues += AtlasValidationIssue("region_without_subzones", "La región ${region.id} no tiene subzonas.")
            }
            region.subzones.forEach { subzone ->
                if (subzone.points.size < 3) {
                    issues += AtlasValidationIssue(
                        "subzone_invalid_polygon",
                        "La subzona ${subzone.id} necesita al menos 3 puntos."
                    )
                }
                subzone.points.forEach { point ->
                    if (!point.isNormalized()) {
                        issues += AtlasValidationIssue(
                            "subzone_out_of_bounds",
                            "La subzona ${subzone.id} tiene puntos fuera del rango 0..1."
                        )
                    }
                    if (silhouettePoints.size >= 3 && !pointInsidePolygon(point, silhouettePoints)) {
                        issues += AtlasValidationIssue(
                            "subzone_outside_silhouette",
                            "La subzona ${subzone.id} sale de la silueta corporal."
                        )
                    }
                }
            }
        }

        atlas.bindings.forEach { binding ->
            if (binding.regionIds.isEmpty()) {
                issues += AtlasValidationIssue(
                    "binding_without_regions",
                    "El músculo ${binding.muscleName} no tiene regiones asociadas."
                )
            }
            binding.regionIds.forEach { regionId ->
                if (regionMap[regionId] == null) {
                    issues += AtlasValidationIssue(
                        "binding_region_missing",
                        "El binding ${binding.muscleName} referencia la región inexistente $regionId."
                    )
                }
            }
        }

        calculateBoundingBoxOverlapWarnings(atlas).forEach { warning ->
            issues += AtlasValidationIssue("overlap_warning", warning)
        }
        calculateSymmetryWarnings(atlas).forEach { warning ->
            issues += AtlasValidationIssue("symmetry_warning", warning)
        }

        return CaupolicanAtlasValidationReport(
            isValid = issues.none { it.code.contains("invalid") || it.code.contains("missing") || it.code.contains("outside") || it.code.contains("out_of_bounds") },
            issues = issues,
        )
    }

    fun validateAssetFingerprint(context: Context, atlas: CaupolicanAtlas): CaupolicanAtlasValidationReport {
        val issues = mutableListOf<AtlasValidationIssue>()
        val entryName = context.resources.getResourceEntryName(atlas.imageSpec.drawableResId)
        if (entryName != atlas.imageSpec.expectedResourceName) {
            issues += AtlasValidationIssue(
                "resource_name_mismatch",
                "El atlas espera ${atlas.imageSpec.expectedResourceName} pero encontró $entryName."
            )
        }

        val bitmap = BitmapFactory.decodeResource(context.resources, atlas.imageSpec.drawableResId)
        if (bitmap.width != atlas.imageSpec.expectedWidth || bitmap.height != atlas.imageSpec.expectedHeight) {
            issues += AtlasValidationIssue(
                "resource_dimension_mismatch",
                "El atlas espera ${atlas.imageSpec.expectedWidth}x${atlas.imageSpec.expectedHeight} pero encontró ${bitmap.width}x${bitmap.height}."
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")
        context.resources.openRawResource(atlas.imageSpec.drawableResId).use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02X".format(it) }
        if (actualHash != atlas.imageSpec.expectedSha256) {
            issues += AtlasValidationIssue(
                "resource_hash_mismatch",
                "La huella del asset no coincide con la esperada por el atlas."
            )
        }

        return CaupolicanAtlasValidationReport(
            isValid = issues.isEmpty(),
            issues = issues,
        )
    }
}

fun CaupolicanAtlas.findRegionsForMuscle(muscleName: String): List<MuscleRegion> {
    val regionIds = bindings.firstOrNull { it.muscleName == muscleName }?.regionIds.orEmpty().toSet()
    return regions.filter { it.id in regionIds }
}

fun CaupolicanAtlas.withUpdatedPoint(
    regionId: String,
    subzoneId: String,
    pointIndex: Int,
    newPoint: NormalizedPoint,
): CaupolicanAtlas {
    return copy(
        regions = regions.map { region ->
            if (region.id != regionId) region
            else {
                region.copy(
                    subzones = region.subzones.map { subzone ->
                        if (subzone.id != subzoneId) subzone
                        else subzone.copy(
                            points = subzone.points.mapIndexed { index, point ->
                                if (index == pointIndex) {
                                    NormalizedPoint(newPoint.x.coerceIn(0f, 1f), newPoint.y.coerceIn(0f, 1f))
                                } else {
                                    point
                                }
                            }
                        )
                    }
                )
            }
        }
    )
}

fun CaupolicanAtlas.withAddedPoint(
    regionId: String,
    subzoneId: String,
    newPoint: NormalizedPoint,
): CaupolicanAtlas {
    return copy(
        regions = regions.map { region ->
            if (region.id != regionId) region
            else {
                region.copy(
                    subzones = region.subzones.map { subzone ->
                        if (subzone.id != subzoneId) subzone
                        else subzone.copy(
                            points = subzone.points + NormalizedPoint(newPoint.x.coerceIn(0f, 1f), newPoint.y.coerceIn(0f, 1f))
                        )
                    }
                )
            }
        }
    )
}

fun CaupolicanAtlas.withRemovedPoint(
    regionId: String,
    subzoneId: String,
    pointIndex: Int,
): CaupolicanAtlas {
    return copy(
        regions = regions.map { region ->
            if (region.id != regionId) region
            else {
                region.copy(
                    subzones = region.subzones.map { subzone ->
                        if (subzone.id != subzoneId || subzone.points.size <= 3) subzone
                        else subzone.copy(
                            points = subzone.points.filterIndexed { index, _ -> index != pointIndex }
                        )
                    }
                )
            }
        }
    )
}

fun CaupolicanAtlas.withInsertedPoint(
    regionId: String,
    subzoneId: String,
    insertAfterIndex: Int,
    newPoint: NormalizedPoint,
): CaupolicanAtlas {
    val clampedPoint = NormalizedPoint(
        x = newPoint.x.coerceIn(0f, 1f),
        y = newPoint.y.coerceIn(0f, 1f),
    )
    return copy(
        regions = regions.map { region ->
            if (region.id != regionId) region
            else {
                region.copy(
                    subzones = region.subzones.map { subzone ->
                        if (subzone.id != subzoneId) subzone
                        else {
                            val safeIndex = insertAfterIndex.coerceIn(0, subzone.points.lastIndex)
                            subzone.copy(
                                points = buildList {
                                    subzone.points.forEachIndexed { index, point ->
                                        add(point)
                                        if (index == safeIndex) add(clampedPoint)
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

fun CaupolicanAtlas.withUpdatedLandmark(
    landmarkId: String,
    newPoint: NormalizedPoint,
): CaupolicanAtlas {
    return copy(
        landmarks = landmarks.map { landmark ->
            if (landmark.id == landmarkId) {
                landmark.copy(
                    point = NormalizedPoint(
                        x = newPoint.x.coerceIn(0f, 1f),
                        y = newPoint.y.coerceIn(0f, 1f),
                    )
                )
            } else {
                landmark
            }
        }
    )
}

fun CaupolicanAtlas.withAddedRegion(
    muscleName: String,
    side: AtlasSide,
    center: NormalizedPoint,
): CaupolicanAtlas {
    val safeCenter = NormalizedPoint(center.x.coerceIn(0f, 1f), center.y.coerceIn(0f, 1f))
    val slug = muscleName
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(" ", "-")
    val regionCount = regions.count { it.muscleName == muscleName && it.side == side } + 1
    val regionId = "$slug-${side.name.lowercase()}-$regionCount"
    val subzoneId = "$regionId-subzone-1"
    val dx = 0.035f
    val dy = 0.05f
    val defaultPolygon = listOf(
        NormalizedPoint((safeCenter.x - dx).coerceIn(0f, 1f), safeCenter.y.coerceIn(0f, 1f)),
        NormalizedPoint((safeCenter.x - dx * 0.35f).coerceIn(0f, 1f), (safeCenter.y - dy).coerceIn(0f, 1f)),
        NormalizedPoint((safeCenter.x + dx * 0.35f).coerceIn(0f, 1f), (safeCenter.y - dy).coerceIn(0f, 1f)),
        NormalizedPoint((safeCenter.x + dx).coerceIn(0f, 1f), safeCenter.y.coerceIn(0f, 1f)),
        NormalizedPoint((safeCenter.x + dx * 0.35f).coerceIn(0f, 1f), (safeCenter.y + dy).coerceIn(0f, 1f)),
        NormalizedPoint((safeCenter.x - dx * 0.35f).coerceIn(0f, 1f), (safeCenter.y + dy).coerceIn(0f, 1f)),
    )
    val newRegion = MuscleRegion(
        id = regionId,
        muscleName = muscleName,
        side = side,
        subzones = listOf(
            MuscleSubzone(
                id = subzoneId,
                label = "$muscleName base",
                points = defaultPolygon,
            )
        ),
    )

    val existingBinding = bindings.firstOrNull { it.muscleName == muscleName }
    val updatedBindings = if (existingBinding != null) {
        bindings.map { binding ->
            if (binding.muscleName == muscleName) {
                binding.copy(regionIds = (binding.regionIds + regionId).distinct())
            } else {
                binding
            }
        }
    } else {
        bindings + CanonicalMuscleAtlasBinding(muscleName = muscleName, regionIds = listOf(regionId))
    }

    return copy(
        regions = regions + newRegion,
        bindings = updatedBindings,
    )
}

fun calculateBoundingBoxOverlapWarnings(atlas: CaupolicanAtlas): List<String> {
    val warnings = mutableListOf<String>()
    val regions = atlas.regions
    for (i in regions.indices) {
        for (j in i + 1 until regions.size) {
            val first = regions[i]
            val second = regions[j]
            if (first.muscleName == second.muscleName) continue
            val overlap = estimateRegionBoundingBoxOverlap(first, second)
            if (overlap > 0.18f) {
                warnings += "Solapamiento alto entre ${first.id} y ${second.id} (${(overlap * 100).toInt()}%)."
            }
        }
    }
    return warnings
}

fun calculateSymmetryWarnings(atlas: CaupolicanAtlas): List<String> {
    val warnings = mutableListOf<String>()
    atlas.regions.groupBy { it.muscleName }.forEach { (muscleName, group) ->
        val left = group.filter { it.id.contains("left") }
        val right = group.filter { it.id.contains("right") }
        if (left.size == 1 && right.size == 1) {
            val leftBox = boundsForRegion(left.first())
            val rightBox = boundsForRegion(right.first())
            val mirroredCenterDelta = abs((1f - rightBox.centerX) - leftBox.centerX)
            val areaDelta = abs(leftBox.area - rightBox.area)
            if (mirroredCenterDelta > 0.05f || areaDelta > 0.025f) {
                warnings += "La simetría bilateral de $muscleName excede la tolerancia del atlas."
            }
        }
    }
    return warnings
}

private data class RegionBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val area: Float,
    val centerX: Float,
)

private fun estimateRegionBoundingBoxOverlap(first: MuscleRegion, second: MuscleRegion): Float {
    val a = boundsForRegion(first)
    val b = boundsForRegion(second)
    val intersectLeft = max(a.left, b.left)
    val intersectTop = max(a.top, b.top)
    val intersectRight = min(a.right, b.right)
    val intersectBottom = min(a.bottom, b.bottom)
    if (intersectRight <= intersectLeft || intersectBottom <= intersectTop) return 0f
    val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
    return intersectArea / max(min(a.area, b.area), 0.0001f)
}

private fun boundsForRegion(region: MuscleRegion): RegionBounds {
    val points = region.subzones.flatMap { it.points }
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val right = points.maxOf { it.x }
    val bottom = points.maxOf { it.y }
    val area = (right - left) * (bottom - top)
    return RegionBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        area = area,
        centerX = left + (right - left) / 2f,
    )
}

private fun NormalizedPoint.isNormalized(): Boolean = x in 0f..1f && y in 0f..1f

private fun pointInsidePolygon(point: NormalizedPoint, polygon: List<NormalizedPoint>): Boolean {
    var result = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val intersects = ((yi > point.y) != (yj > point.y)) &&
            (point.x < (xj - xi) * (point.y - yi) / ((yj - yi).takeIf { abs(it) > 0.0001f } ?: 0.0001f) + xi)
        if (intersects) result = !result
        j = i
    }
    return result
}

private fun pt(x: Float, y: Float) = NormalizedPoint(x, y)

private fun createFrontAtlas(): CaupolicanAtlas {
    val regions = listOf(
        MuscleRegion(
            id = "neck-front",
            muscleName = "Cuello",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "neck-front-center",
                    label = "Cuello frontal",
                    points = listOf(
                        pt(0.47f, 0.118f), pt(0.53f, 0.118f), pt(0.55f, 0.155f),
                        pt(0.53f, 0.194f), pt(0.47f, 0.194f), pt(0.45f, 0.155f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "deltoid-left-front",
            muscleName = "Deltoides",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "deltoid-left-front-cap",
                    label = "Deltoides frontal izquierdo",
                    points = listOf(
                        pt(0.205f, 0.167f), pt(0.265f, 0.148f), pt(0.322f, 0.178f),
                        pt(0.326f, 0.232f), pt(0.280f, 0.268f), pt(0.222f, 0.236f),
                        pt(0.194f, 0.198f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "deltoid-right-front",
            muscleName = "Deltoides",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "deltoid-right-front-cap",
                    label = "Deltoides frontal derecho",
                    points = listOf(
                        pt(0.795f, 0.167f), pt(0.735f, 0.148f), pt(0.678f, 0.178f),
                        pt(0.674f, 0.232f), pt(0.720f, 0.268f), pt(0.778f, 0.236f),
                        pt(0.806f, 0.198f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "pectoral-left-front",
            muscleName = "Pectorales",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "pectoral-left-front-main",
                    label = "Pectoral izquierdo",
                    points = listOf(
                        pt(0.324f, 0.188f), pt(0.455f, 0.194f), pt(0.472f, 0.256f),
                        pt(0.420f, 0.302f), pt(0.332f, 0.288f), pt(0.286f, 0.242f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "pectoral-right-front",
            muscleName = "Pectorales",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "pectoral-right-front-main",
                    label = "Pectoral derecho",
                    points = listOf(
                        pt(0.545f, 0.194f), pt(0.676f, 0.188f), pt(0.714f, 0.242f),
                        pt(0.668f, 0.288f), pt(0.580f, 0.302f), pt(0.528f, 0.256f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "biceps-left-front",
            muscleName = "Bíceps",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "biceps-left-front-main",
                    label = "Bíceps izquierdo",
                    points = listOf(
                        pt(0.183f, 0.244f), pt(0.235f, 0.256f), pt(0.268f, 0.333f),
                        pt(0.245f, 0.403f), pt(0.194f, 0.392f), pt(0.164f, 0.318f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "biceps-right-front",
            muscleName = "Bíceps",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "biceps-right-front-main",
                    label = "Bíceps derecho",
                    points = listOf(
                        pt(0.817f, 0.244f), pt(0.765f, 0.256f), pt(0.732f, 0.333f),
                        pt(0.755f, 0.403f), pt(0.806f, 0.392f), pt(0.836f, 0.318f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "forearm-left-front",
            muscleName = "Antebrazo",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "forearm-left-front-main",
                    label = "Antebrazo izquierdo",
                    points = listOf(
                        pt(0.152f, 0.386f), pt(0.205f, 0.402f), pt(0.214f, 0.566f),
                        pt(0.166f, 0.634f), pt(0.128f, 0.552f), pt(0.124f, 0.438f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "forearm-right-front",
            muscleName = "Antebrazo",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "forearm-right-front-main",
                    label = "Antebrazo derecho",
                    points = listOf(
                        pt(0.848f, 0.386f), pt(0.795f, 0.402f), pt(0.786f, 0.566f),
                        pt(0.834f, 0.634f), pt(0.872f, 0.552f), pt(0.876f, 0.438f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "abdomen-front",
            muscleName = "Abdomen",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "abdomen-front-main",
                    label = "Abdomen frontal",
                    points = listOf(
                        pt(0.424f, 0.304f), pt(0.576f, 0.304f), pt(0.598f, 0.472f),
                        pt(0.500f, 0.562f), pt(0.402f, 0.472f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "core-left-front",
            muscleName = "Core",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "core-left-front-oblique",
                    label = "Oblicuo izquierdo",
                    points = listOf(
                        pt(0.342f, 0.336f), pt(0.408f, 0.346f), pt(0.394f, 0.504f),
                        pt(0.332f, 0.474f), pt(0.310f, 0.392f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "core-right-front",
            muscleName = "Core",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "core-right-front-oblique",
                    label = "Oblicuo derecho",
                    points = listOf(
                        pt(0.658f, 0.336f), pt(0.592f, 0.346f), pt(0.606f, 0.504f),
                        pt(0.668f, 0.474f), pt(0.690f, 0.392f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "adductor-left-front",
            muscleName = "Aductores",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "adductor-left-front-main",
                    label = "Aductor izquierdo",
                    points = listOf(
                        pt(0.474f, 0.568f), pt(0.450f, 0.640f), pt(0.420f, 0.746f),
                        pt(0.470f, 0.742f), pt(0.495f, 0.640f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "adductor-right-front",
            muscleName = "Aductores",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "adductor-right-front-main",
                    label = "Aductor derecho",
                    points = listOf(
                        pt(0.526f, 0.568f), pt(0.550f, 0.640f), pt(0.580f, 0.746f),
                        pt(0.530f, 0.742f), pt(0.505f, 0.640f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "quadriceps-left-front",
            muscleName = "Cuádriceps",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "quadriceps-left-front-main",
                    label = "Cuádriceps izquierdo",
                    points = listOf(
                        pt(0.360f, 0.564f), pt(0.444f, 0.580f), pt(0.470f, 0.744f),
                        pt(0.438f, 0.864f), pt(0.354f, 0.842f), pt(0.324f, 0.682f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "quadriceps-right-front",
            muscleName = "Cuádriceps",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "quadriceps-right-front-main",
                    label = "Cuádriceps derecho",
                    points = listOf(
                        pt(0.640f, 0.564f), pt(0.556f, 0.580f), pt(0.530f, 0.744f),
                        pt(0.562f, 0.864f), pt(0.646f, 0.842f), pt(0.676f, 0.682f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "calf-left-front",
            muscleName = "Pantorrillas",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "calf-left-front-main",
                    label = "Pantorrilla izquierda",
                    points = listOf(
                        pt(0.364f, 0.848f), pt(0.420f, 0.856f), pt(0.448f, 0.952f),
                        pt(0.428f, 0.995f), pt(0.376f, 0.982f), pt(0.348f, 0.902f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "calf-right-front",
            muscleName = "Pantorrillas",
            side = AtlasSide.FRONT,
            subzones = listOf(
                MuscleSubzone(
                    id = "calf-right-front-main",
                    label = "Pantorrilla derecha",
                    points = listOf(
                        pt(0.636f, 0.848f), pt(0.580f, 0.856f), pt(0.552f, 0.952f),
                        pt(0.572f, 0.995f), pt(0.624f, 0.982f), pt(0.652f, 0.902f),
                    )
                ),
            )
        ),
    )

    return CaupolicanAtlas(
        side = AtlasSide.FRONT,
        imageSpec = AtlasImageSpec(
            drawableResId = R.drawable.caupolican_front,
            expectedResourceName = "caupolican_front",
            expectedWidth = 1338,
            expectedHeight = 3200,
            expectedSha256 = "CEA919B0E797654A3F4246A1AE2E44C3A1C217423BC419BD6FC04C39A5965BBC",
        ),
        silhouette = BodySilhouette(
            points = listOf(
                pt(0.50f, 0.015f), pt(0.42f, 0.028f), pt(0.32f, 0.062f), pt(0.24f, 0.138f),
                pt(0.18f, 0.236f), pt(0.14f, 0.374f), pt(0.11f, 0.540f), pt(0.18f, 0.652f),
                pt(0.24f, 0.706f), pt(0.31f, 0.875f), pt(0.33f, 0.972f), pt(0.39f, 0.998f),
                pt(0.46f, 0.944f), pt(0.50f, 0.930f), pt(0.54f, 0.944f), pt(0.61f, 0.998f),
                pt(0.67f, 0.972f), pt(0.69f, 0.875f), pt(0.76f, 0.706f), pt(0.82f, 0.652f),
                pt(0.89f, 0.540f), pt(0.86f, 0.374f), pt(0.82f, 0.236f), pt(0.76f, 0.138f),
                pt(0.68f, 0.062f), pt(0.58f, 0.028f),
            )
        ),
        landmarks = listOf(
            BodyLandmark("vertex", "Vértice craneal", pt(0.50f, 0.018f)),
            BodyLandmark("neck_base", "Base del cuello", pt(0.50f, 0.154f)),
            BodyLandmark("shoulder_left", "Hombro izquierdo", pt(0.252f, 0.166f)),
            BodyLandmark("shoulder_right", "Hombro derecho", pt(0.748f, 0.166f)),
            BodyLandmark("axilla_left", "Axila izquierda", pt(0.315f, 0.267f)),
            BodyLandmark("axilla_right", "Axila derecha", pt(0.685f, 0.267f)),
            BodyLandmark("waist_left", "Cintura izquierda", pt(0.366f, 0.468f)),
            BodyLandmark("waist_right", "Cintura derecha", pt(0.634f, 0.468f)),
            BodyLandmark("hip_left", "Cadera izquierda", pt(0.406f, 0.586f)),
            BodyLandmark("hip_right", "Cadera derecha", pt(0.594f, 0.586f)),
            BodyLandmark("pelvis_center", "Centro pélvico", pt(0.50f, 0.598f)),
            BodyLandmark("knee_left", "Rodilla izquierda", pt(0.424f, 0.842f)),
            BodyLandmark("knee_right", "Rodilla derecha", pt(0.576f, 0.842f)),
            BodyLandmark("ankle_left", "Tobillo izquierdo", pt(0.396f, 0.982f)),
            BodyLandmark("ankle_right", "Tobillo derecho", pt(0.604f, 0.982f)),
        ),
        regions = regions,
        bindings = listOf(
            CanonicalMuscleAtlasBinding("Cuello", listOf("neck-front")),
            CanonicalMuscleAtlasBinding("Deltoides", listOf("deltoid-left-front", "deltoid-right-front")),
            CanonicalMuscleAtlasBinding("Pectorales", listOf("pectoral-left-front", "pectoral-right-front")),
            CanonicalMuscleAtlasBinding("Bíceps", listOf("biceps-left-front", "biceps-right-front")),
            CanonicalMuscleAtlasBinding("Antebrazo", listOf("forearm-left-front", "forearm-right-front")),
            CanonicalMuscleAtlasBinding("Abdomen", listOf("abdomen-front")),
            CanonicalMuscleAtlasBinding("Core", listOf("core-left-front", "core-right-front")),
            CanonicalMuscleAtlasBinding("Aductores", listOf("adductor-left-front", "adductor-right-front")),
            CanonicalMuscleAtlasBinding("Cuádriceps", listOf("quadriceps-left-front", "quadriceps-right-front")),
            CanonicalMuscleAtlasBinding("Pantorrillas", listOf("calf-left-front", "calf-right-front")),
        ),
    )
}

private fun createBackAtlas(): CaupolicanAtlas {
    val regions = listOf(
        MuscleRegion(
            id = "neck-back",
            muscleName = "Cuello",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "neck-back-main",
                    label = "Nuca",
                    points = listOf(
                        pt(0.454f, 0.102f), pt(0.546f, 0.102f), pt(0.562f, 0.176f),
                        pt(0.500f, 0.194f), pt(0.438f, 0.176f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "trap-left-back",
            muscleName = "Trapecio",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "trap-left-back-main",
                    label = "Trapecio izquierdo",
                    points = listOf(
                        pt(0.288f, 0.142f), pt(0.436f, 0.132f), pt(0.486f, 0.236f),
                        pt(0.396f, 0.314f), pt(0.308f, 0.256f), pt(0.256f, 0.184f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "trap-right-back",
            muscleName = "Trapecio",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "trap-right-back-main",
                    label = "Trapecio derecho",
                    points = listOf(
                        pt(0.712f, 0.142f), pt(0.564f, 0.132f), pt(0.514f, 0.236f),
                        pt(0.604f, 0.314f), pt(0.692f, 0.256f), pt(0.744f, 0.184f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "deltoid-left-back",
            muscleName = "Deltoides",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "deltoid-left-back-main",
                    label = "Deltoides posterior izquierdo",
                    points = listOf(
                        pt(0.190f, 0.168f), pt(0.270f, 0.154f), pt(0.320f, 0.214f),
                        pt(0.292f, 0.282f), pt(0.214f, 0.264f), pt(0.172f, 0.204f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "deltoid-right-back",
            muscleName = "Deltoides",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "deltoid-right-back-main",
                    label = "Deltoides posterior derecho",
                    points = listOf(
                        pt(0.810f, 0.168f), pt(0.730f, 0.154f), pt(0.680f, 0.214f),
                        pt(0.708f, 0.282f), pt(0.786f, 0.264f), pt(0.828f, 0.204f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "dorsal-left-back",
            muscleName = "Dorsales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "dorsal-left-back-main",
                    label = "Dorsal izquierdo",
                    points = listOf(
                        pt(0.304f, 0.270f), pt(0.420f, 0.290f), pt(0.458f, 0.432f),
                        pt(0.406f, 0.542f), pt(0.310f, 0.492f), pt(0.258f, 0.364f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "dorsal-right-back",
            muscleName = "Dorsales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "dorsal-right-back-main",
                    label = "Dorsal derecho",
                    points = listOf(
                        pt(0.696f, 0.270f), pt(0.580f, 0.290f), pt(0.542f, 0.432f),
                        pt(0.594f, 0.542f), pt(0.690f, 0.492f), pt(0.742f, 0.364f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "triceps-left-back",
            muscleName = "Tríceps",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "triceps-left-back-main",
                    label = "Tríceps izquierdo",
                    points = listOf(
                        pt(0.152f, 0.260f), pt(0.214f, 0.252f), pt(0.258f, 0.346f),
                        pt(0.230f, 0.438f), pt(0.166f, 0.420f), pt(0.128f, 0.320f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "triceps-right-back",
            muscleName = "Tríceps",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "triceps-right-back-main",
                    label = "Tríceps derecho",
                    points = listOf(
                        pt(0.848f, 0.260f), pt(0.786f, 0.252f), pt(0.742f, 0.346f),
                        pt(0.770f, 0.438f), pt(0.834f, 0.420f), pt(0.872f, 0.320f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "forearm-left-back",
            muscleName = "Antebrazo",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "forearm-left-back-main",
                    label = "Antebrazo posterior izquierdo",
                    points = listOf(
                        pt(0.118f, 0.420f), pt(0.174f, 0.434f), pt(0.190f, 0.594f),
                        pt(0.154f, 0.666f), pt(0.112f, 0.588f), pt(0.102f, 0.468f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "forearm-right-back",
            muscleName = "Antebrazo",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "forearm-right-back-main",
                    label = "Antebrazo posterior derecho",
                    points = listOf(
                        pt(0.882f, 0.420f), pt(0.826f, 0.434f), pt(0.810f, 0.594f),
                        pt(0.846f, 0.666f), pt(0.888f, 0.588f), pt(0.898f, 0.468f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "erector-left-back",
            muscleName = "Erectores Espinales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "erector-left-back-main",
                    label = "Erector izquierdo",
                    points = listOf(
                        pt(0.470f, 0.282f), pt(0.490f, 0.282f), pt(0.490f, 0.566f),
                        pt(0.458f, 0.566f), pt(0.446f, 0.430f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "erector-right-back",
            muscleName = "Erectores Espinales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "erector-right-back-main",
                    label = "Erector derecho",
                    points = listOf(
                        pt(0.510f, 0.282f), pt(0.530f, 0.282f), pt(0.542f, 0.430f),
                        pt(0.542f, 0.566f), pt(0.510f, 0.566f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "glute-left-back",
            muscleName = "Glúteos",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "glute-left-back-main",
                    label = "Glúteo izquierdo",
                    points = listOf(
                        pt(0.360f, 0.538f), pt(0.472f, 0.550f), pt(0.494f, 0.650f),
                        pt(0.438f, 0.718f), pt(0.350f, 0.682f), pt(0.316f, 0.598f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "glute-right-back",
            muscleName = "Glúteos",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "glute-right-back-main",
                    label = "Glúteo derecho",
                    points = listOf(
                        pt(0.640f, 0.538f), pt(0.528f, 0.550f), pt(0.506f, 0.650f),
                        pt(0.562f, 0.718f), pt(0.650f, 0.682f), pt(0.684f, 0.598f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "hamstring-left-back",
            muscleName = "Isquiosurales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "hamstring-left-back-main",
                    label = "Isquiosural izquierdo",
                    points = listOf(
                        pt(0.350f, 0.694f), pt(0.438f, 0.706f), pt(0.456f, 0.870f),
                        pt(0.418f, 0.960f), pt(0.344f, 0.932f), pt(0.316f, 0.790f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "hamstring-right-back",
            muscleName = "Isquiosurales",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "hamstring-right-back-main",
                    label = "Isquiosural derecho",
                    points = listOf(
                        pt(0.650f, 0.694f), pt(0.562f, 0.706f), pt(0.544f, 0.870f),
                        pt(0.582f, 0.960f), pt(0.656f, 0.932f), pt(0.684f, 0.790f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "calf-left-back",
            muscleName = "Pantorrillas",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "calf-left-back-main",
                    label = "Pantorrilla posterior izquierda",
                    points = listOf(
                        pt(0.356f, 0.872f), pt(0.434f, 0.890f), pt(0.448f, 0.982f),
                        pt(0.416f, 0.998f), pt(0.362f, 0.974f), pt(0.336f, 0.914f),
                    )
                ),
            )
        ),
        MuscleRegion(
            id = "calf-right-back",
            muscleName = "Pantorrillas",
            side = AtlasSide.BACK,
            subzones = listOf(
                MuscleSubzone(
                    id = "calf-right-back-main",
                    label = "Pantorrilla posterior derecha",
                    points = listOf(
                        pt(0.644f, 0.872f), pt(0.566f, 0.890f), pt(0.552f, 0.982f),
                        pt(0.584f, 0.998f), pt(0.638f, 0.974f), pt(0.664f, 0.914f),
                    )
                ),
            )
        ),
    )

    return CaupolicanAtlas(
        side = AtlasSide.BACK,
        imageSpec = AtlasImageSpec(
            drawableResId = R.drawable.caupolican_back,
            expectedResourceName = "caupolican_back",
            expectedWidth = 1376,
            expectedHeight = 3024,
            expectedSha256 = "F9616E10D2DDFB5290CE1ADE5449A8D0D2D824F6DB12EEE603F356D73616E64B",
        ),
        silhouette = BodySilhouette(
            points = listOf(
                pt(0.50f, 0.010f), pt(0.41f, 0.024f), pt(0.30f, 0.056f), pt(0.22f, 0.134f),
                pt(0.15f, 0.240f), pt(0.10f, 0.420f), pt(0.13f, 0.598f), pt(0.26f, 0.686f),
                pt(0.31f, 0.892f), pt(0.35f, 0.996f), pt(0.43f, 0.998f), pt(0.50f, 0.940f),
                pt(0.57f, 0.998f), pt(0.65f, 0.996f), pt(0.69f, 0.892f), pt(0.74f, 0.686f),
                pt(0.87f, 0.598f), pt(0.90f, 0.420f), pt(0.85f, 0.240f), pt(0.78f, 0.134f),
                pt(0.70f, 0.056f), pt(0.59f, 0.024f),
            )
        ),
        landmarks = listOf(
            BodyLandmark("vertex", "Vértice craneal", pt(0.50f, 0.012f)),
            BodyLandmark("neck_base", "Base del cuello", pt(0.50f, 0.148f)),
            BodyLandmark("shoulder_left", "Hombro izquierdo", pt(0.242f, 0.172f)),
            BodyLandmark("shoulder_right", "Hombro derecho", pt(0.758f, 0.172f)),
            BodyLandmark("axilla_left", "Axila izquierda", pt(0.304f, 0.298f)),
            BodyLandmark("axilla_right", "Axila derecha", pt(0.696f, 0.298f)),
            BodyLandmark("waist_left", "Cintura izquierda", pt(0.392f, 0.514f)),
            BodyLandmark("waist_right", "Cintura derecha", pt(0.608f, 0.514f)),
            BodyLandmark("hip_left", "Cadera izquierda", pt(0.404f, 0.610f)),
            BodyLandmark("hip_right", "Cadera derecha", pt(0.596f, 0.610f)),
            BodyLandmark("pelvis_center", "Centro pélvico", pt(0.50f, 0.616f)),
            BodyLandmark("knee_left", "Rodilla izquierda", pt(0.424f, 0.874f)),
            BodyLandmark("knee_right", "Rodilla derecha", pt(0.576f, 0.874f)),
            BodyLandmark("ankle_left", "Tobillo izquierdo", pt(0.410f, 0.988f)),
            BodyLandmark("ankle_right", "Tobillo derecho", pt(0.590f, 0.988f)),
        ),
        regions = regions,
        bindings = listOf(
            CanonicalMuscleAtlasBinding("Cuello", listOf("neck-back")),
            CanonicalMuscleAtlasBinding("Trapecio", listOf("trap-left-back", "trap-right-back")),
            CanonicalMuscleAtlasBinding("Deltoides", listOf("deltoid-left-back", "deltoid-right-back")),
            CanonicalMuscleAtlasBinding("Dorsales", listOf("dorsal-left-back", "dorsal-right-back")),
            CanonicalMuscleAtlasBinding("Tríceps", listOf("triceps-left-back", "triceps-right-back")),
            CanonicalMuscleAtlasBinding("Antebrazo", listOf("forearm-left-back", "forearm-right-back")),
            CanonicalMuscleAtlasBinding("Erectores Espinales", listOf("erector-left-back", "erector-right-back")),
            CanonicalMuscleAtlasBinding("Glúteos", listOf("glute-left-back", "glute-right-back")),
            CanonicalMuscleAtlasBinding("Isquiosurales", listOf("hamstring-left-back", "hamstring-right-back")),
            CanonicalMuscleAtlasBinding("Pantorrillas", listOf("calf-left-back", "calf-right-back")),
        ),
    )
}
