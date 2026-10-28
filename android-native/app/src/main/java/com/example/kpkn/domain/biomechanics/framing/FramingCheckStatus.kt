package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Estado de la comprobación de encuadre. [CHECKING] evita presentar una aprobación inventada
 * mientras el estimador no ha evaluado ningún fotograma; [UNAVAILABLE] declara el fallo.
 */
enum class FramingCheckStatus {
    CHECKING,
    EVALUATED,
    UNAVAILABLE,
}

/**
 * La captura espera un veredicto estable. «No se pudo comprobar» es un error
 * explícito que no bloquea; «comprobando» y un veredicto negativo sí bloquean.
 */
fun FramingCheckStatus.allowsCapture(verdict: FramingVerdict): Boolean = when (this) {
    FramingCheckStatus.UNAVAILABLE -> true
    FramingCheckStatus.EVALUATED -> verdict.ok
    FramingCheckStatus.CHECKING -> false
}

/**
 * Textos únicos para el chip, el subtítulo de la guía hablada y la síntesis de voz.
 * La guía previa sólo lee lo que el usuario ve: mismas palabras, sin instrucciones paralelas.
 */
object FramingInstruction {
    const val READY = "Encuadre listo"
    const val READY_SPEECH = "Encuadre correcto. Puedes comenzar."
    const val CHECKING_SHORT = "Comprobando encuadre…"
    const val CHECKING_SPEECH = "Comprobando el encuadre."
    const val UNAVAILABLE_SHORT = "No se pudo comprobar el encuadre"
    const val UNAVAILABLE_SPEECH = "No se pudo comprobar el encuadre. Puedes grabar igualmente."
    /** Durante la serie no se interrumpe por oscilaciones; la captura queda para revisión. */
    const val RECORDING_CAPTION = "El encuadre se conserva para revisión."

    /** Instrucción prioritaria breve; el chip muestra sólo esta. */
    fun short(issue: FramingIssue, isFrontPreferred: Boolean = false): String = when (issue) {
        FramingIssue.NO_PERSON -> "Ponte en cuadro"
        FramingIssue.TWO_PEOPLE -> "Hay 2 personas"
        FramingIssue.TOO_CLOSE -> "Demasiado cerca"
        FramingIssue.FEET_OUT -> "Aléjate: no se ven los pies"
        FramingIssue.HEAD_OUT -> "Aléjate: no se ve la cabeza"
        FramingIssue.TOO_SMALL -> "Acércate más"
        FramingIssue.WRONG_VIEW -> if (isFrontPreferred) "Gírate de frente" else "Gírate de lado"
        FramingIssue.FACE_HIDDEN -> "Cara no visible"
        FramingIssue.TILTED -> "Endereza el móvil"
        FramingIssue.NO_PLATE -> "No veo el disco"
    }

    /** Instrucción concreta que se muestra bajo la guía y se pronuncia igual. */
    fun spoken(
        verdict: FramingVerdict,
        status: FramingCheckStatus,
        preferredView: ViewKind = ViewKind.SIDE,
    ): String {
        if (status == FramingCheckStatus.UNAVAILABLE) return UNAVAILABLE_SPEECH
        if (status == FramingCheckStatus.CHECKING) return CHECKING_SPEECH
        if (verdict.ok) return READY_SPEECH
        val issue = verdict.issues.firstOrNull() ?: return CHECKING_SPEECH
        return spoken(issue, preferredView)
    }

    fun spoken(issue: FramingIssue, preferredView: ViewKind = ViewKind.SIDE): String = when (issue) {
        FramingIssue.NO_PERSON -> "No se detecta al atleta. Entra en el encuadre."
        FramingIssue.TWO_PEOPLE -> "Debe aparecer una sola persona en el encuadre."
        FramingIssue.TOO_CLOSE -> "Estás demasiado cerca. Aléjate hasta que se vea el cuerpo completo."
        FramingIssue.FEET_OUT -> "Los pies quedan fuera. Aléjate un poco de la cámara."
        FramingIssue.HEAD_OUT -> "La cabeza queda fuera del cuadro. Aléjate un poco de la cámara."
        FramingIssue.TOO_SMALL -> "Te veo demasiado lejos. Acércate un poco."
        FramingIssue.WRONG_VIEW -> when (preferredView) {
            ViewKind.FRONT -> "Coloca la cámara de frente para este ejercicio."
            ViewKind.OBLIQUE -> "Coloca la cámara en diagonal para este ejercicio."
            else -> "Coloca la cámara de lado para este ejercicio."
        }
        FramingIssue.FACE_HIDDEN -> "No se ve tu cara. Gira un poco la cabeza o mejora la luz."
        FramingIssue.TILTED -> "El móvil está inclinado. Enderezalo."
        FramingIssue.NO_PLATE -> "No se ve el disco. Colócalo dentro del encuadre."
    }
}
