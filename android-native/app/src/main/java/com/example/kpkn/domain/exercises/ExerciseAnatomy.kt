package com.example.kpkn.domain.exercises

data class MuscleHead(
    val name: String,
    val emphasisKeyword: String?,
)

data class MuscleAnatomy(
    val canonicalName: String,
    val displayName: String,
    val heads: List<MuscleHead>,
)

val ALL_MUSCLES: List<MuscleAnatomy> = listOf(
    MuscleAnatomy(
        canonicalName = "Pectorales",
        displayName = "Pectorales",
        heads = listOf(
            MuscleHead(name = "Clavicular/Superior", emphasisKeyword = "superior"),
            MuscleHead(name = "Esternal/Inferior", emphasisKeyword = "inferior"),
            MuscleHead(name = "Plano/Medio", emphasisKeyword = null),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Deltoides",
        displayName = "Deltoides",
        heads = listOf(
            MuscleHead(name = "Anterior", emphasisKeyword = "anterior"),
            MuscleHead(name = "Lateral/Medio", emphasisKeyword = "medio"),
            MuscleHead(name = "Posterior", emphasisKeyword = "posterior"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Dorsales",
        displayName = "Dorsales",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Trapecio",
        displayName = "Trapecio",
        heads = listOf(
            MuscleHead(name = "Descendente/Superior", emphasisKeyword = "superior"),
            MuscleHead(name = "Transversa/Media", emphasisKeyword = "medio"),
            MuscleHead(name = "Ascendente/Inferior", emphasisKeyword = "inferior"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Romboides",
        displayName = "Romboides",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Erectores Espinales",
        displayName = "Erectores Espinales",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Cuádriceps",
        displayName = "Cuádriceps",
        heads = listOf(
            MuscleHead(name = "Recto Femoral", emphasisKeyword = "recto femoral"),
            MuscleHead(name = "Vasto Lateral", emphasisKeyword = null),
            MuscleHead(name = "Vasto Medial", emphasisKeyword = null),
            MuscleHead(name = "Vasto Intermedio", emphasisKeyword = null),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Isquiosurales",
        displayName = "Isquiosurales",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Glúteos",
        displayName = "Glúteos",
        heads = listOf(
            MuscleHead(name = "Mayor", emphasisKeyword = "mayor"),
            MuscleHead(name = "Medio", emphasisKeyword = "medio"),
            MuscleHead(name = "Menor", emphasisKeyword = null),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Aductores",
        displayName = "Aductores",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Pantorrillas",
        displayName = "Pantorrillas",
        heads = listOf(
            MuscleHead(name = "Gastrocnemio", emphasisKeyword = "gastrocnemio"),
            MuscleHead(name = "Sóleo", emphasisKeyword = "sóleo"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Bíceps",
        displayName = "Bíceps",
        heads = listOf(
            MuscleHead(name = "Larga", emphasisKeyword = "larga"),
            MuscleHead(name = "Corta", emphasisKeyword = "corta"),
            MuscleHead(name = "Braquial", emphasisKeyword = "braquial"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Tríceps",
        displayName = "Tríceps",
        heads = listOf(
            MuscleHead(name = "Larga", emphasisKeyword = "larga"),
            MuscleHead(name = "Lateral", emphasisKeyword = "lateral"),
            MuscleHead(name = "Medial", emphasisKeyword = "medial"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Antebrazo",
        displayName = "Antebrazo",
        heads = listOf(
            MuscleHead(name = "Flexores", emphasisKeyword = "flexor"),
            MuscleHead(name = "Extensores", emphasisKeyword = "extensor"),
            MuscleHead(name = "Pronador/Supinador", emphasisKeyword = "pronador"),
        ),
    ),
    MuscleAnatomy(
        canonicalName = "Abdomen",
        displayName = "Abdomen",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Core",
        displayName = "Transverso/Core",
        heads = emptyList(),
    ),
    MuscleAnatomy(
        canonicalName = "Cuello",
        displayName = "Cuello",
        heads = emptyList(),
    ),
)

val MUSCLE_BY_CANONICAL: Map<String, MuscleAnatomy> =
    ALL_MUSCLES.associateBy { it.canonicalName }
