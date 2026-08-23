package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.db.JointEntity
import com.example.kpkn.data.db.MovementPatternEntity
import com.example.kpkn.data.db.MuscleGroupEntity
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.domain.exercises.catalogv2.AprendeOntology

enum class CanonicalKnowledgeKind { MUSCLE, JOINT, STABILIZER, PATTERN }

/** Exactly the two fields allowed inside a catalog knowledge tooltip. */
data class CanonicalKnowledge(
    val kind: CanonicalKnowledgeKind,
    val id: String,
    val name: String,
    val description: String,
)

private fun MuscleGroupEntity.toKnowledge(kind: CanonicalKnowledgeKind = CanonicalKnowledgeKind.MUSCLE) =
    CanonicalKnowledge(kind, id, name, description)

private fun JointEntity.toKnowledge(kind: CanonicalKnowledgeKind = CanonicalKnowledgeKind.JOINT) =
    CanonicalKnowledge(kind, id, name, description)

private fun MovementPatternEntity.toKnowledge() =
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, id, name, description)

/**
 * Explicit fallback for the small set needed before the Room static cache has
 * finished. These entries mirror the bundled canonical introductions; they
 * are never selected from a display name.
 */
private val fallbackEntries = listOf(
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "pectoral", "Pectoral", "Grupo muscular del pecho que produce aducción horizontal y flexión del hombro."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "dorsal-ancho", "Dorsal Ancho", "Músculo de la espalda que participa en aducción y extensión del brazo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "deltoides", "Deltoides", "Músculo del hombro con porciones anterior, lateral y posterior."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "bíceps", "Bíceps", "Músculo anterior del brazo que flexiona el codo y supina el antebrazo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "tríceps", "Tríceps", "Músculo posterior del brazo que extiende el codo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "trapecio", "Trapecio", "Músculo superficial de la espalda que mueve y estabiliza la escápula."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "romboides", "Romboides", "Músculos entre columna y escápula que participan en la retracción escapular."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "recto-abdominal", "Recto Abdominal", "Músculo abdominal que participa en flexión del tronco y control anti-extensión."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "core", "Core", "Sistema lumbopélvico que transfiere fuerza y protege la columna bajo carga."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "erectores-espinales", "Erectores Espinales", "Grupo muscular que extiende la columna y sostiene la postura."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "glúteo-mayor", "Glúteo Mayor", "Gran extensor de cadera que contribuye a la potencia de bisagra, sprint y salto."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "glúteo-medio", "Glúteo Medio", "Abductor de cadera con un papel importante en la estabilidad frontal."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "isquiosurales", "Isquiosurales", "Grupo posterior del muslo que flexiona la rodilla y extiende la cadera."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "cuádriceps", "Cuádriceps", "Grupo anterior del muslo cuya función principal es extender la rodilla."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "aductores", "Aductores", "Grupo de la cara interna del muslo que aproxima la pierna al centro del cuerpo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "pantorrillas", "Pantorrillas", "Grupo formado por gastrocnemio y sóleo que realiza flexión plantar."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "tibial-anterior", "Tibial Anterior", "Dorsiflexor principal del tobillo que ayuda a despejar el pie."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "antebrazo", "Antebrazo", "Grupo de flexores, extensores, pronadores y supinadores que sostiene el agarre."),
    CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "cuello", "Cuello", "Musculatura cervical que flexiona, extiende, rota e inclina la cabeza."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "glenohumeral", "Articulación Glenohumeral (Hombro)", "Articulación esferoidea de gran movilidad entre húmero y escápula."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "acromioclavicular", "Articulación Acromioclavicular", "Une clavícula y acromion y permite pequeños ajustes de la escápula durante la elevación del brazo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "esternoclavicular", "Articulación Esternoclavicular", "Conecta la clavícula con el esternón y acompaña la elevación, descenso y rotación de la cintura escapular."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "codo", "Articulación del Codo", "Complejo articular en bisagra que permite flexoextensión y transmite carga."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "radiocubital-proximal", "Articulación Radiocubital Proximal", "Articulación pivote que permite pronación y supinación para orientar la mano y transferir carga."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "muñeca", "Articulación de la Muñeca (Radiocarpiana)", "Articulación que permite flexión, extensión y desviaciones de la mano."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "columna-cervical", "Columna Cervical", "Segmento cervical que sostiene la cabeza y coordina flexión, extensión, inclinación y rotación."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "columna-toracica", "Columna Torácica", "Segmento torácico relacionado con costillas y escápula que participa en movilidad y transferencia del tronco."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "cadera", "Articulación de la Cadera", "Articulación que conecta pelvis y fémur y permite movimiento del miembro inferior."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "rodilla", "Articulación de la Rodilla", "Articulación que coordina flexión y extensión entre fémur, tibia y rótula."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "tobillo", "Articulación del Tobillo", "Complejo talocrural que permite dorsiflexión y flexión plantar y trabaja junto con el retropié."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "subtalar", "Articulación Subastragalina", "Ajusta la orientación del retropié mediante inversión y eversión para adaptar el apoyo al terreno."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "sacroiliaca", "Articulación Sacroilíaca", "Transfiere fuerzas entre columna y pelvis con movilidad pequeña y función importante durante apoyo y bisagra."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "columna-lumbar", "Columna Lumbar", "Segmento lumbar que soporta carga y controla flexión, extensión y estabilidad del tronco."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "escapulotoracica", "Articulación Escapulotorácica", "Interfaz funcional donde la escápula se desliza sobre la parrilla costal para coordinar el hombro."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "horizontal-push", "Empuje Horizontal", "Patrón de empuje en el plano horizontal que aleja la carga del torso mediante hombro y extensión de codo; se diferencia del empuje vertical porque la resistencia viaja delante del cuerpo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "horizontal-pull", "Tirón Horizontal", "Patrón de tracción horizontal que acerca la carga al torso con hombro, codo y escápula; se distingue del tirón vertical por la dirección predominante de la resistencia."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "vertical-push", "Empuje Vertical", "Patrón de empuje que lleva la carga por encima de la cabeza con acción coordinada de hombro, codo y escápula; el tronco debe conservar su organización mientras la resistencia asciende."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "vertical-pull", "Tirón Vertical", "Patrón de tracción vertical que lleva la carga hacia el torso o eleva el cuerpo a una barra, combinando hombro y flexión de codo en una trayectoria superior."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "squat", "Patrón de Sentadilla", "Patrón dominante de rodilla donde flexión y extensión de rodilla organizan el descenso y el ascenso con participación de cadera y tobillo; se diferencia de una bisagra por el avance relativo de la rodilla."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hinge", "Patrón de Bisagra de Cadera", "Patrón dominante de cadera donde la pelvis viaja atrás y adelante con rodilla semiflexionada y columna controlada; la producción nace principalmente en la cadera, no en un gran avance de rodilla."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "lunge", "Patrón de Estocada", "Patrón unilateral del tren inferior en el que una pierna recibe la mayor parte de la carga mientras la otra asiste; exige estabilidad de cadera, rodilla y tobillo durante la asimetría."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "anti-extension", "Anti-Extensión", "Patrón de estabilidad donde el tronco resiste la extensión lumbar que separaría costillas y pelvis; el objetivo es impedir el movimiento ante la carga, no producir una extensión dinámica."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "anti-rotation", "Anti-Rotación", "Patrón de control donde el tronco resiste una rotación provocada por carga asimétrica o fuerza externa; conservar la orientación es el resultado buscado, no girar."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "rotation", "Rotación", "Patrón dinámico que gira tronco o cintura escapular alrededor de su eje longitudinal coordinando pelvis y hombros; se diferencia de la anti-rotación porque aquí el giro es la acción buscada."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "carry", "Patrón de Carga", "Patrón integral en el que la persona camina o permanece erguida sosteniendo una carga; el agarre, tronco y pelvis transmiten fuerzas durante la locomoción y el control de pasos."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "ankle-dorsiflexion", "Dorsiflexión de Tobillo", "Acerca el empeine a la tibia al cerrar el ángulo del tobillo; se diferencia de la flexión plantar, que apunta el pie hacia abajo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "diagonal-push", "Empuje Diagonal", "Desplaza la carga hacia delante y arriba en una trayectoria diagonal que combina hombro, codo y tronco."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "elbow-flexion", "Flexión de Codo", "Acerca el antebrazo al brazo al reducir el ángulo del codo; es opuesta a la extensión."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "elbow-extension", "Extensión de Codo", "Aumenta el ángulo del codo y aleja el antebrazo del brazo; es la acción final de muchos empujes."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hip-abduction", "Abducción de Cadera", "Aleja el muslo de la línea media en el plano frontal y exige control de la pelvis."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hip-adduction", "Aducción de Cadera", "Acerca el muslo a la línea media en el plano frontal; no es extensión de cadera."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hip-extension", "Extensión de Cadera", "Lleva el muslo hacia atrás respecto a la pelvis, con participación de glúteo mayor e isquiosurales."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hip-flexion", "Flexión de Cadera", "Acerca el muslo al tronco y reduce el ángulo entre pelvis y fémur."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "horizontal-abduction", "Abducción Horizontal de Hombro", "Lleva el brazo hacia atrás en el plano horizontal, distinta de elevarlo lateralmente."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "isometric-grip", "Agarre Isométrico", "Mantiene fuerza de prensión sin recorrer un rango visible; el objetivo es sostener la carga."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "knee-flexion", "Flexión de Rodilla", "Reduce el ángulo entre fémur y tibia y acerca el talón hacia el muslo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "knee-extension", "Extensión de Rodilla", "Aumenta el ángulo entre fémur y tibia; el cuádriceps es el motor principal."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "lateral-knee-dominant", "Dominante de Rodilla Lateral", "Carga una pierna mientras el centro de masa se desplaza lateralmente y la rodilla controla el plano frontal."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "lateral-trunk-flexion", "Flexión Lateral de Tronco", "Inclina el tronco hacia un lado en el plano frontal, distinta de girarlo en rotación."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "neck-extension", "Extensión Cervical", "Lleva la cabeza hacia atrás y aumenta el ángulo de la columna cervical."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "neck-flexion", "Flexión Cervical", "Acerca el mentón al pecho al reducir el ángulo de la columna cervical."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "neck-lateral-flexion", "Flexión Lateral Cervical", "Inclina la cabeza hacia un hombro sin girarla alrededor de su eje."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "pinch-grip", "Agarre de Pinza", "Sujeta un objeto entre pulgar y dedos; es diferente del agarre cilíndrico que envuelve la mano."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "plantar-flexion", "Flexión Plantar de Tobillo", "Apunta el pie alejándolo de la tibia, como al despegar en un salto; es opuesta a la dorsiflexión."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "scapular-depression", "Depresión Escapular", "Desplaza las escápulas hacia abajo sobre la caja torácica, distinta de acercarlas a la columna."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "scapular-elevation", "Elevación Escapular", "Desplaza las escápulas hacia arriba sin necesidad de mover el codo."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "shoulder-abduction", "Abducción de Hombro", "Eleva el brazo alejándolo del costado en el plano frontal; no es flexión hacia delante."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "shoulder-flexion", "Flexión de Hombro", "Eleva el brazo hacia delante en el plano sagital, distinta de la abducción lateral."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "spinal-extension", "Extensión de Columna", "Lleva uno o varios segmentos vertebrales hacia atrás o recupera la posición neutra."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "spinal-flexion", "Flexión de Columna", "Redondea o inclina la columna; es distinta de flexionar solo la cadera con espalda neutra."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "trunk-flexion", "Flexión de Tronco", "Acerca el tórax a la pelvis mediante flexión del tronco, no mediante una bisagra de cadera neutra."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "wrist-extension", "Extensión de Muñeca", "Lleva el dorso de la mano hacia el antebrazo y aumenta el ángulo posterior de la muñeca."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "wrist-flexion", "Flexión de Muñeca", "Acerca la palma al antebrazo al cerrar el ángulo anterior de la muñeca."),
)

private fun fallback(kind: CanonicalKnowledgeKind, id: String): CanonicalKnowledge? =
    fallbackEntries.firstOrNull { it.kind == kind && it.id == id }

fun canonicalMuscleKnowledge(catalogMuscleId: String): CanonicalKnowledge? {
    val wikiId = AprendeOntology.wikiLabMuscleId(catalogMuscleId) ?: return null
    return WikiLabRepository.getMuscleById(wikiId)?.toKnowledge() ?: fallback(CanonicalKnowledgeKind.MUSCLE, wikiId)
}

/**
 * Explicit bridge for the compact volume labels used by legacy/editor rows.
 * This is intentionally a closed table: a display label that represents an
 * aggregate (for example "Glúteos") does not receive a made-up single-muscle
 * definition.
 */
private val volumeLabelToCatalogMuscleId: Map<String, String> = mapOf(
    "Pectorales" to "pectoralis",
    "Dorsales" to "latissimus_dorsi",
    "Deltoides" to "deltoid",
    "Bíceps" to "biceps",
    "Tríceps" to "triceps",
    "Antebrazo" to "forearm",
    "Trapecio" to "trapezius",
    "Romboides" to "rhomboids",
    "Glúteo Medio" to "gluteus_medius",
    "Erectores Espinales" to "erector_spinae",
    "Cuádriceps" to "quadriceps",
    "Isquiosurales" to "hamstrings",
    "Aductores" to "adductors",
    "Pantorrillas" to "calves",
    "Tibial Anterior" to "tibialis_anterior",
    "Cuello" to "neck",
    "Core" to "core",
    "Abdomen" to "abdominals",
)

fun canonicalMuscleKnowledgeForVolumeLabel(label: String): CanonicalKnowledge? =
    volumeLabelToCatalogMuscleId[label]?.let(::canonicalMuscleKnowledge)

fun canonicalJointKnowledge(jointId: String, kind: CanonicalKnowledgeKind = CanonicalKnowledgeKind.JOINT): CanonicalKnowledge? {
    val id = jointId.trim()
    if (id.isBlank() || id !in AprendeOntology.wikiLabJointIds) return null
    return WikiLabRepository.getJointById(id)?.toKnowledge(kind) ?: fallback(kind, id)
}

fun canonicalPatternKnowledge(catalogPatternId: String): CanonicalKnowledge? {
    val wikiId = AprendeOntology.wikiLabPatternId(catalogPatternId) ?: return null
    return WikiLabRepository.getPatternById(wikiId)?.toKnowledge() ?: fallback(CanonicalKnowledgeKind.PATTERN, wikiId)
}
