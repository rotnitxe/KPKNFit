package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Lo que el perfil de análisis pide al encuadre de la grabación.
 *
 * @property preferredViews vistas que sirven para el perfil (SIDE, FRONT u OBLIQUE; la del evaluador sale solo de ψ_2D).
 * @property needsPlate el perfil mide con el disco: debe verse en cuadro.
 * @property needsFeet los pies deben estar dentro del cuadro con margen (casi todos los perfiles).
 * @property needsFace la cabeza debe estar dentro del cuadro y la cara a la vista.
 */
data class FramingSpec(
    val preferredViews: Set<ViewKind>,
    val needsPlate: Boolean,
    val needsFeet: Boolean,
    val needsFace: Boolean = true,
)

/** Problemas de encuadre, en orden de prioridad: la interfaz muestra el primero. */
enum class FramingIssue {
    /** No hay nadie en el cuadro. */
    NO_PERSON,

    /** Hay una segunda persona casi tan grande como el atleta. */
    TWO_PEOPLE,

    /** El cuerpo llena el cuadro: demasiado cerca. */
    TOO_CLOSE,

    /** Los pies quedan fuera del cuadro o a menos del 5 % del borde. */
    FEET_OUT,

    /** La cabeza queda fuera del cuadro o a menos del 5 % del borde. */
    HEAD_OUT,

    /** El cuerpo ocupa menos del 40 % del alto. */
    TOO_SMALL,

    /** La vista (ψ_2D) no está entre las preferidas del perfil. */
    WRONG_VIEW,

    /** La cara no se ve (de espaldas o tapada). */
    FACE_HIDDEN,

    /** El móvil está inclinado más de 3°. */
    TILTED,

    /** El perfil necesita el disco y no se ve. */
    NO_PLATE,
}

/**
 * Veredicto de una evaluación. [issues] va en orden de prioridad (vacío = sin problemas); [ok] exige varias
 * evaluaciones seguidas sin problemas (histéresis); [view] es la vista de ψ_2D, si se pudo medir.
 */
data class FramingVerdict(val ok: Boolean, val issues: List<FramingIssue>, val view: ViewKind?)
