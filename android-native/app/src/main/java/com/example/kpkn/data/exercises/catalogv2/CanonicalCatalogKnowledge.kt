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
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "codo", "Articulación del Codo", "Complejo articular en bisagra que permite flexoextensión y transmite carga."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "muñeca", "Articulación de la Muñeca (Radiocarpiana)", "Articulación que permite flexión, extensión y desviaciones de la mano."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "cadera", "Articulación de la Cadera", "Articulación que conecta pelvis y fémur y permite movimiento del miembro inferior."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "rodilla", "Articulación de la Rodilla", "Articulación que coordina flexión y extensión entre fémur, tibia y rótula."),
    CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "columna-lumbar", "Columna Lumbar", "Segmento lumbar que soporta carga y controla flexión, extensión y estabilidad del tronco."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "horizontal-push", "Empuje Horizontal", "Patrón de empuje en plano horizontal que aleja la carga del torso."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "horizontal-pull", "Tirón Horizontal", "Patrón de tracción horizontal que acerca la carga al torso."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "vertical-push", "Empuje Vertical", "Patrón de empuje por encima de la cabeza."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "vertical-pull", "Tirón Vertical", "Patrón de tracción vertical que lleva la carga hacia el torso."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "squat", "Patrón de Sentadilla", "Patrón dominante de rodilla con participación coordinada de cadera."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "hinge", "Patrón de Bisagra de Cadera", "Patrón dominante de cadera con control lumbar y rodilla semiflexionada."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "lunge", "Patrón de Estocada", "Patrón unilateral de tren inferior que exige estabilidad de cadera y rodilla."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "anti-extension", "Anti-Extensión", "Patrón de estabilidad donde el tronco resiste la extensión lumbar."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "anti-rotation", "Anti-Rotación", "Patrón de control que resiste la rotación ante una perturbación externa."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "rotation", "Rotación", "Patrón dinámico de rotación del tronco o cintura escapular."),
    CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "carry", "Patrón de Carga", "Patrón integral de locomoción y estabilidad bajo carga."),
)

private fun fallback(kind: CanonicalKnowledgeKind, id: String): CanonicalKnowledge? =
    fallbackEntries.firstOrNull { it.kind == kind && it.id == id }

fun canonicalMuscleKnowledge(catalogMuscleId: String): CanonicalKnowledge? {
    val wikiId = AprendeOntology.wikiLabMuscleId(catalogMuscleId) ?: return null
    return WikiLabRepository.getMuscleById(wikiId)?.toKnowledge() ?: fallback(CanonicalKnowledgeKind.MUSCLE, wikiId)
}

fun canonicalJointKnowledge(jointId: String, kind: CanonicalKnowledgeKind = CanonicalKnowledgeKind.JOINT): CanonicalKnowledge? {
    val id = jointId.trim()
    if (id.isBlank() || id !in AprendeOntology.wikiLabJointIds) return null
    return WikiLabRepository.getJointById(id)?.toKnowledge(kind) ?: fallback(kind, id)
}

fun canonicalPatternKnowledge(catalogPatternId: String): CanonicalKnowledge? {
    val wikiId = AprendeOntology.wikiLabPatternId(catalogPatternId) ?: return null
    return WikiLabRepository.getPatternById(wikiId)?.toKnowledge() ?: fallback(CanonicalKnowledgeKind.PATTERN, wikiId)
}
