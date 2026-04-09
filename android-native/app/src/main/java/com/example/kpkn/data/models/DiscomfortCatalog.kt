package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class DiscomfortSection(val label: String) {
    SHOULDERS_ARMS("Hombro y brazos"),
    SPINE_NECK("Columna y cuello"),
    HIP_PELVIS("Cadera y pelvis"),
    KNEE("Rodilla"),
    ANKLE_FOOT("Tobillo y pie"),
    GENERAL("General"),
}

@Serializable
data class DiscomfortCatalogEntry(
    val id: String,
    val label: String,
    val description: String,
    val section: DiscomfortSection,
    val relatedMuscles: List<String> = emptyList(),
    val relatedArticular: List<ArticularBattery> = emptyList(),
)

val DISCOMFORT_CATALOG: List<DiscomfortCatalogEntry> = listOf(
    DiscomfortCatalogEntry(
        id = "none",
        label = "Sin molestias",
        description = "No percibiste molestias relevantes en este ejercicio.",
        section = DiscomfortSection.GENERAL,
    ),
    DiscomfortCatalogEntry(
        id = "shoulder_anterior",
        label = "Hombro anterior",
        description = "Molestia en la parte frontal del hombro, frecuente en empujes y elevaciones.",
        section = DiscomfortSection.SHOULDERS_ARMS,
        relatedMuscles = listOf("Deltoides", "Pectorales"),
        relatedArticular = listOf(ArticularBattery.SHOULDER),
    ),
    DiscomfortCatalogEntry(
        id = "shoulder_posterior",
        label = "Hombro posterior",
        description = "Molestia en la cara posterior del hombro, común en tirones o rotaciones.",
        section = DiscomfortSection.SHOULDERS_ARMS,
        relatedMuscles = listOf("Deltoides", "Trapecio", "Dorsales"),
        relatedArticular = listOf(ArticularBattery.SHOULDER),
    ),
    DiscomfortCatalogEntry(
        id = "elbow_medial",
        label = "Codo (cara interna)",
        description = "Molestia en flexores/pronadores del antebrazo cerca del codo.",
        section = DiscomfortSection.SHOULDERS_ARMS,
        relatedMuscles = listOf("Antebrazo", "Bíceps"),
        relatedArticular = listOf(ArticularBattery.ELBOW),
    ),
    DiscomfortCatalogEntry(
        id = "elbow_lateral",
        label = "Codo (cara externa)",
        description = "Molestia en extensores del antebrazo cerca del epicóndilo.",
        section = DiscomfortSection.SHOULDERS_ARMS,
        relatedMuscles = listOf("Antebrazo", "Tríceps"),
        relatedArticular = listOf(ArticularBattery.ELBOW),
    ),
    DiscomfortCatalogEntry(
        id = "wrist_hand",
        label = "Muñeca / mano",
        description = "Molestia durante agarre, apoyo o extensión/flexión de muñeca.",
        section = DiscomfortSection.SHOULDERS_ARMS,
        relatedMuscles = listOf("Antebrazo"),
        relatedArticular = listOf(ArticularBattery.ELBOW),
    ),
    DiscomfortCatalogEntry(
        id = "neck_cervical",
        label = "Cuello / cervical",
        description = "Rigidez o dolor cervical relacionado con carga axial o tensión de trapecio.",
        section = DiscomfortSection.SPINE_NECK,
        relatedMuscles = listOf("Cuello", "Trapecio"),
        relatedArticular = listOf(ArticularBattery.CERVICAL),
    ),
    DiscomfortCatalogEntry(
        id = "upper_back",
        label = "Espalda alta",
        description = "Molestia en zona torácica alta o entre escápulas.",
        section = DiscomfortSection.SPINE_NECK,
        relatedMuscles = listOf("Trapecio", "Dorsales"),
        relatedArticular = listOf(ArticularBattery.CERVICAL, ArticularBattery.SHOULDER),
    ),
    DiscomfortCatalogEntry(
        id = "lumbar",
        label = "Lumbar",
        description = "Molestia en zona baja de la espalda asociada a bisagra o compresión.",
        section = DiscomfortSection.SPINE_NECK,
        relatedMuscles = listOf("Erectores Espinales", "Core"),
        relatedArticular = listOf(ArticularBattery.HIP, ArticularBattery.CERVICAL),
    ),
    DiscomfortCatalogEntry(
        id = "hip_front",
        label = "Cadera anterior",
        description = "Molestia en flexión de cadera o al final del rango en sentadillas/zancadas.",
        section = DiscomfortSection.HIP_PELVIS,
        relatedMuscles = listOf("Cuádriceps", "Aductores", "Core"),
        relatedArticular = listOf(ArticularBattery.HIP),
    ),
    DiscomfortCatalogEntry(
        id = "hip_lateral",
        label = "Cadera lateral / glútea",
        description = "Molestia lateral de cadera o glúteo medio durante apoyo unilateral.",
        section = DiscomfortSection.HIP_PELVIS,
        relatedMuscles = listOf("Glúteos", "Aductores"),
        relatedArticular = listOf(ArticularBattery.HIP),
    ),
    DiscomfortCatalogEntry(
        id = "adductor_groin",
        label = "Aductores / ingle",
        description = "Molestia en la cara interna del muslo o región inguinal.",
        section = DiscomfortSection.HIP_PELVIS,
        relatedMuscles = listOf("Aductores"),
        relatedArticular = listOf(ArticularBattery.HIP),
    ),
    DiscomfortCatalogEntry(
        id = "hamstring_proximal",
        label = "Isquiosurales proximales",
        description = "Molestia en inserción alta de isquiosurales cerca de cadera.",
        section = DiscomfortSection.HIP_PELVIS,
        relatedMuscles = listOf("Isquiosurales", "Glúteos"),
        relatedArticular = listOf(ArticularBattery.HIP),
    ),
    DiscomfortCatalogEntry(
        id = "knee_patellar",
        label = "Rodilla anterior",
        description = "Molestia bajo/entorno de la rótula, común en flexión repetida de rodilla.",
        section = DiscomfortSection.KNEE,
        relatedMuscles = listOf("Cuádriceps"),
        relatedArticular = listOf(ArticularBattery.KNEE),
    ),
    DiscomfortCatalogEntry(
        id = "knee_medial",
        label = "Rodilla interna/externa",
        description = "Molestia en compartimentos medial o lateral de rodilla.",
        section = DiscomfortSection.KNEE,
        relatedMuscles = listOf("Cuádriceps", "Isquiosurales", "Aductores"),
        relatedArticular = listOf(ArticularBattery.KNEE),
    ),
    DiscomfortCatalogEntry(
        id = "achilles",
        label = "Tendón de Aquiles",
        description = "Molestia en tendón de Aquiles durante saltos, carrera o elevaciones de talón.",
        section = DiscomfortSection.ANKLE_FOOT,
        relatedMuscles = listOf("Pantorrillas"),
        relatedArticular = listOf(ArticularBattery.ANKLE),
    ),
    DiscomfortCatalogEntry(
        id = "ankle",
        label = "Tobillo",
        description = "Molestia en estabilidad o movilidad de tobillo bajo carga.",
        section = DiscomfortSection.ANKLE_FOOT,
        relatedMuscles = listOf("Pantorrillas"),
        relatedArticular = listOf(ArticularBattery.ANKLE),
    ),
    DiscomfortCatalogEntry(
        id = "plantar_foot",
        label = "Planta del pie",
        description = "Molestia en arco plantar o apoyo del pie.",
        section = DiscomfortSection.ANKLE_FOOT,
        relatedMuscles = listOf("Pantorrillas"),
        relatedArticular = listOf(ArticularBattery.ANKLE),
    ),
)

val DISCOMFORT_CATALOG_BY_ID: Map<String, DiscomfortCatalogEntry> =
    DISCOMFORT_CATALOG.associateBy { it.id }

fun discomfortLabel(id: String): String =
    DISCOMFORT_CATALOG_BY_ID[id]?.label ?: id
