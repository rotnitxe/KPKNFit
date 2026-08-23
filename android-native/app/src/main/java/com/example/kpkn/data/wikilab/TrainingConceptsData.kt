package com.example.kpkn.data.wikilab

import androidx.compose.ui.graphics.Color

/**
 * Training Concepts Database — "Conceptos Clave" del entrenamiento.
 *
 * Runtime-safe compact rows used by Home's Conceptos Clave surface.
 */

data class TrainingConcept(
    val id: String,
    val name: String,
    val category: ConceptCategory,
    val shortDescription: String,
)

enum class ConceptCategory(
    val label: String,
    val color: Color,
    val icon: String,
) {
    LOAD_MANAGEMENT("Gestión de Carga", Color(0xFFE53935), "barbell"),
    INTENSITY("Intensidad y Esfuerzo", Color(0xFFFF8F00), "flame"),
    FATIGUE("Fatiga y Recuperación", Color(0xFF9C27B0), "recovery"),
    HYPERTROPHY("Mecanismos de Hipertrofia", Color(0xFF1E88E5), "muscle"),
    MOVEMENT("Mecánica del Movimiento", Color(0xFF43A047), "movement"),
    EQUIPMENT("Equipamiento y Medios", Color(0xFF00897B), "equipment"),
    QUALITIES("Cualidades Físicas", Color(0xFF5C6BC0), "qualities"),
    PERIODIZATION("Periodización", Color(0xFF795548), "calendar"),
}

val TRAINING_CONCEPTS_DATABASE: List<TrainingConcept> = listOf(

    // ═══════════════════════════════════════════════════════════════════
    // GESTIÓN DE CARGA
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "volumen-entrenamiento",
        name = "Volumen de Entrenamiento",
        category = ConceptCategory.LOAD_MANAGEMENT,
        shortDescription = "Cantidad total de trabajo realizado en una sesión o semana",
    ),


    TrainingConcept(
        id = "intensidad",
        name = "Intensidad",
        category = ConceptCategory.LOAD_MANAGEMENT,
        shortDescription = "Porcentaje de carga relativa al máximo o nivel de esfuerzo",
    ),


    TrainingConcept(
        id = "frecuencia",
        name = "Frecuencia de Entrenamiento",
        category = ConceptCategory.LOAD_MANAGEMENT,
        shortDescription = "Número de veces que se entrena un grupo muscular por semana",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // INTENSIDAD Y ESFUERZO
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "rir",
        name = "RIR (Repeticiones en Reserva)",
        category = ConceptCategory.INTENSITY,
        shortDescription = "Cuántas repeticiones te quedan antes de llegar al fallo",
    ),


    TrainingConcept(
        id = "rpe",
        name = "RPE (Percepción del Esfuerzo)",
        category = ConceptCategory.INTENSITY,
        shortDescription = "Escala subjetiva de esfuerzo percibido del 1 al 10",
    ),


    TrainingConcept(
        id = "fallo-muscular",
        name = "Fallo Muscular",
        category = ConceptCategory.INTENSITY,
        shortDescription = "Punto donde no puedes completar otra repetición concéntrica",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // FATIGA Y RECUPERACIÓN
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "fatiga-sistemica",
        name = "Fatiga Sistémica",
        category = ConceptCategory.FATIGUE,
        shortDescription = "Fatiga acumulada que afecta al organismo completo, no solo al músculo",
    ),


    TrainingConcept(
        id = "dano-muscular",
        name = "Daño Muscular",
        category = ConceptCategory.FATIGUE,
        shortDescription = "Microlesiones en las fibras musculares causadas por el entrenamiento",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // MECANISMOS DE HIPERTROFIA
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "tension-mecanica",
        name = "Tensión Mecánica (y Torque Articular)",
        category = ConceptCategory.HYPERTROPHY,
        shortDescription = "Fuerza y torque aplicado sobre el músculo durante su contracción",
    ),


    TrainingConcept(
        id = "estres-metabolico",
        name = "Estrés Metabólico",
        category = ConceptCategory.HYPERTROPHY,
        shortDescription = "Acumulación de metabolitos durante series de alta repetición",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // MECÁNICA DEL MOVIMIENTO
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "rom",
        name = "ROM (Rango de Movimiento)",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "La amplitud total del movimiento articular durante un ejercicio",
    ),


    TrainingConcept(
        id = "perfil-resistencia",
        name = "Perfil de Resistencia y Curvas de Fuerza",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "Cómo varía la dificultad y el brazo de momento a lo largo del recorrido",
    ),


    TrainingConcept(
        id = "fase-excentrica",
        name = "Fase Excéntrica",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "La fase de descenso controlado donde el músculo se alarga bajo tensión",
    ),


    TrainingConcept(
        id = "fase-concentrica",
        name = "Fase Concéntrica",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "La fase de levantamiento donde el músculo se acorta produciendo fuerza",
    ),


    TrainingConcept(
        id = "isometria",
        name = "Isometría",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "Contracción muscular sin cambio en la longitud del músculo",
    ),


    TrainingConcept(
        id = "pliometria",
        name = "Pliometría",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "Movimientos explosivos que aprovechan el ciclo estiramiento-acortamiento",
    ),


    TrainingConcept(
        id = "carga-axial",
        name = "Carga Axial",
        category = ConceptCategory.MOVEMENT,
        shortDescription = "Fuerza que se aplica a lo largo del eje longitudinal del cuerpo o segmento",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // EQUIPAMIENTO Y MEDIOS
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "pesos-libres",
        name = "Pesos Libres",
        category = ConceptCategory.EQUIPMENT,
        shortDescription = "Barras, mancuernas y kettlebells — resistencia sin guía de trayectoria",
    ),


    TrainingConcept(
        id = "maquinas",
        name = "Máquinas de Entrenamiento",
        category = ConceptCategory.EQUIPMENT,
        shortDescription = "Equipos con trayectoria guiada para aislamiento muscular controlado",
    ),


    TrainingConcept(
        id = "poleas",
        name = "Poleas y Cables",
        category = ConceptCategory.EQUIPMENT,
        shortDescription = "Sistemas de cable que mantienen tensión constante en cualquier ángulo",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // CUALIDADES FÍSICAS
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "fuerza",
        name = "Fuerza y Generación de Torque",
        category = ConceptCategory.QUALITIES,
        shortDescription = "Capacidad neuromuscular de ejercer Torque articular contra resistencia",
    ),


    TrainingConcept(
        id = "explosividad",
        name = "Explosividad / Potencia",
        category = ConceptCategory.QUALITIES,
        shortDescription = "Capacidad de generar la máxima fuerza en el menor tiempo posible",
    ),


    TrainingConcept(
        id = "elasticidad",
        name = "Elasticidad",
        category = ConceptCategory.QUALITIES,
        shortDescription = "Capacidad de almacenar y liberar energía elástica en el complejo músculo-tendón",
    ),


    TrainingConcept(
        id = "resistencia-muscular",
        name = "Resistencia Muscular",
        category = ConceptCategory.QUALITIES,
        shortDescription = "Capacidad de mantener la producción de fuerza durante un período prolongado",
    ),

    // ═══════════════════════════════════════════════════════════════════
    // PERIODIZACIÓN
    // ═══════════════════════════════════════════════════════════════════


    TrainingConcept(
        id = "sobrecarga-progresiva",
        name = "Sobrecarga Progresiva",
        category = ConceptCategory.PERIODIZATION,
        shortDescription = "Incrementar gradualmente el estímulo para forzar adaptaciones continuas",
    ),


    TrainingConcept(
        id = "deload",
        name = "Deload (Semana de Descarga)",
        category = ConceptCategory.PERIODIZATION,
        shortDescription = "Reducción planificada del volumen/intensidad para disipar fatiga acumulada",
    ),


    TrainingConcept(
        id = "especificidad",
        name = "Especificidad",
        category = ConceptCategory.PERIODIZATION,
        shortDescription = "Las adaptaciones son específicas al tipo de estímulo aplicado",
    ),
)

/** Get all unique categories present in the database */
fun getConceptCategories(): List<ConceptCategory> =
    TRAINING_CONCEPTS_DATABASE.map { it.category }.distinct().sortedBy { it.ordinal }

/** Compatibility search over the compact fields only. */
fun searchConcepts(query: String): List<TrainingConcept> {
    if (query.isBlank()) return TRAINING_CONCEPTS_DATABASE
    val q = query.lowercase()
    return TRAINING_CONCEPTS_DATABASE.filter { concept ->
        concept.name.lowercase().contains(q) ||
            concept.shortDescription.lowercase().contains(q) ||
            concept.category.label.lowercase().contains(q)
    }
}
