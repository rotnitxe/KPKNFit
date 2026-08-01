package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole

/**
 * Generates the short explanation shown when a catalog muscle contribution is tapped.
 *
 * The catalog may provide a curated [InvolvedMuscle.biomechanicalReason]. When it does not,
 * the explanation is derived from the exercise movement pattern, force direction, muscle and
 * role so the copy remains tied to the actual exercise instead of using a role-only sentence.
 */
fun explainMuscleContribution(
    exercise: ExerciseMuscleInfo,
    involvement: InvolvedMuscle,
): String {
    involvement.biomechanicalReason
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val movementContext = listOfNotNull(
        exercise.movementPattern,
        exercise.name,
        exercise.type,
    ).joinToString(" ").lowercase()
    val forceContext = exercise.force.orEmpty().lowercase()
    val muscle = involvement.muscle.lowercase()
    val action = when {
        isHorizontalPush(movementContext) -> horizontalPushAction(muscle)
        isVerticalPush(movementContext) -> verticalPushAction(muscle)
        isHorizontalPull(movementContext) -> horizontalPullAction(muscle)
        isVerticalPull(movementContext) -> verticalPullAction(muscle)
        isKneeDominant(movementContext) -> kneeDominantAction(muscle)
        isHipHinge(movementContext) -> hipHingeAction(muscle)
        isElbowFlexion(movementContext) -> elbowFlexionAction(muscle)
        isElbowExtension(movementContext) -> elbowExtensionAction(muscle)
        isShoulderAction(movementContext) -> shoulderAction(muscle)
        isHipAction(movementContext) -> hipAction(muscle)
        isTrunkAction(movementContext) -> trunkAction(muscle)
        isAnkleAction(movementContext) -> ankleAction(muscle)
        isWristOrGripAction(movementContext) -> wristOrGripAction(muscle)
        isNeckAction(movementContext) -> neckAction(muscle)
        else -> null
    } ?: forceAction(muscle, forceContext)
        ?: muscleSpecificAction(muscle, exercise.force)

    return roleSentence(involvement.role, action)
}

private fun forceAction(muscle: String, force: String): String? = when {
    force.containsAny("empuje") ->
        horizontalPushAction(muscle) ?: verticalPushAction(muscle)
    force.containsAny("tirón", "tiron", "tracción", "traccion") ->
        horizontalPullAction(muscle) ?: verticalPullAction(muscle)
    force.containsAny("bisagra", "extensión cadera", "extension cadera", "dominante cadera") ->
        hipHingeAction(muscle)
    force.containsAny("dominante de rodilla", "sentadilla") ->
        kneeDominantAction(muscle)
    force.containsAny("flexión codo", "flexion codo") ->
        elbowFlexionAction(muscle)
    force.containsAny("extensión codo", "extension codo") ->
        elbowExtensionAction(muscle)
    else -> null
}

private fun roleSentence(role: MuscleRole, action: String): String = when (role) {
    MuscleRole.PRIMARY ->
        "Es el motor principal: $action."
    MuscleRole.SECONDARY ->
        "Es secundario porque $action, mientras el motor principal completa el patrón."
    MuscleRole.STABILIZER ->
        "Actúa como estabilizador: $action sin generar la mayor parte del movimiento."
    MuscleRole.NEUTRALIZER ->
        "Actúa como neutralizador: $action para limitar compensaciones durante el recorrido."
}

private fun isHorizontalPush(context: String): Boolean =
    context.containsAny("empuje horizontal", "press de banca", "press banca", "floor press", "empuje diagonal")

private fun isVerticalPush(context: String): Boolean =
    context.containsAny("empuje vertical", "press militar", "press hombro", "press overhead")

private fun isHorizontalPull(context: String): Boolean =
    context.containsAny("tirón horizontal", "remo", "abducción horizontal")

private fun isVerticalPull(context: String): Boolean =
    context.containsAny("tirón vertical", "dominada", "jalón", "tracción vertical")

private fun isKneeDominant(context: String): Boolean =
    context.containsAny(
        "dominante de rodilla",
        "extensión rodilla",
        "flexión rodilla",
        "sentadilla",
        "prensa de piernas",
        "zancada",
        "estocada",
    )

private fun isHipHinge(context: String): Boolean =
    context.containsAny(
        "bisagra",
        "extensión cadera",
        "hip thrust",
        "peso muerto",
        "puente de glúteo",
        "dominante cadera",
    )

private fun isElbowFlexion(context: String): Boolean =
    context.containsAny("flexión codo", "curl de", "curl ", "flexor de codo")

private fun isElbowExtension(context: String): Boolean =
    context.containsAny("extensión codo", "extensor de codo", "tríceps")

private fun isShoulderAction(context: String): Boolean =
    context.containsAny("abducción hombro", "flexión hombro", "elevación escapular", "depresión escapular")

private fun isHipAction(context: String): Boolean =
    context.containsAny("abducción cadera", "aducción cadera", "extensión/abducción cadera", "rotación externa cadera")

private fun isTrunkAction(context: String): Boolean =
    context.containsAny(
        "flexión tronco",
        "flexión columna",
        "extensión columna",
        "anti-extensión",
        "anti-rotación",
        "rotación tronco",
        "control pelvis",
    )

private fun isAnkleAction(context: String): Boolean =
    context.containsAny("flexión plantar", "flexión dorsal", "tobillo")

private fun isWristOrGripAction(context: String): Boolean =
    context.containsAny("flexión muñeca", "extensión muñeca", "agarre", "pinza")

private fun isNeckAction(context: String): Boolean =
    context.containsAny("flexión cuello", "extensión cuello", "flexión lateral cuello")

private fun horizontalPushAction(muscle: String): String? = when {
    muscle.containsAny("pectoral", "pecho") ->
        "produce aducción horizontal y flexión del hombro contra la resistencia"
    muscle.containsAny("deltoides", "hombro") ->
        "sus fibras anteriores ayudan a flexionar y aducir horizontalmente el hombro"
    muscle.containsAny("tríceps", "triceps") ->
        "extiende el codo para completar el empuje"
    muscle.containsAny("bíceps", "biceps") ->
        "coapta el hombro y ayuda a mantener centrada la cabeza humeral"
    muscle.containsAny("trapecio", "romboides") ->
        "mantiene la escápula estable mientras el húmero transmite la fuerza"
    muscle.containsAny("core", "abdomen", "erector") ->
        "mantiene el tronco rígido para transferir la fuerza sin perder posición"
    else -> null
}

private fun verticalPushAction(muscle: String): String? = when {
    muscle.containsAny("deltoides", "hombro") ->
        "eleva y flexiona el hombro para llevar la carga por encima de la cabeza"
    muscle.containsAny("tríceps", "triceps") ->
        "extiende el codo en la fase de bloqueo"
    muscle.containsAny("pectoral", "pecho") ->
        "ayuda a flexionar el hombro, sobre todo con el brazo delante del tronco"
    muscle.containsAny("trapecio", "romboides") ->
        "coordina y estabiliza la escápula para que el brazo rote sin perder centrado"
    muscle.containsAny("core", "abdomen", "erector") ->
        "resiste la extensión lumbar y mantiene alineadas costillas y pelvis"
    else -> null
}

private fun horizontalPullAction(muscle: String): String? = when {
    muscle.containsAny("romboides", "trapecio") ->
        "retrae y fija la escápula para ofrecer una base estable al tirón"
    muscle.containsAny("dorsal", "espalda", "redondo") ->
        "extiende el hombro y acerca el brazo al tronco durante el tirón"
    muscle.containsAny("deltoides", "hombro") ->
        "sus fibras posteriores llevan el brazo hacia atrás mediante abducción horizontal"
    muscle.containsAny("bíceps", "biceps", "braquial") ->
        "flexiona el codo para acercar la carga al tronco"
    muscle.containsAny("antebrazo", "agarre") ->
        "sostiene el agarre y estabiliza la muñeca para que la fuerza llegue al codo"
    muscle.containsAny("erector", "core", "abdomen") ->
        "mantiene el tronco isométrico para que el remo no se convierta en una compensación lumbar"
    else -> null
}

private fun verticalPullAction(muscle: String): String? = when {
    muscle.containsAny("dorsal", "espalda", "redondo") ->
        "aduce y extiende el hombro para llevar el cuerpo o la carga hacia la barra"
    muscle.containsAny("bíceps", "biceps", "braquial") ->
        "flexiona el codo y comparte la tracción con la musculatura de la espalda"
    muscle.containsAny("antebrazo", "agarre") ->
        "mantiene el agarre y transmite la fuerza entre la mano y el codo"
    muscle.containsAny("trapecio", "romboides") ->
        "deprime o fija la escápula para que el tirón tenga una base estable"
    muscle.containsAny("core", "abdomen", "erector") ->
        "evita el balanceo y mantiene la pelvis y el tronco alineados"
    else -> null
}

private fun kneeDominantAction(muscle: String): String? = when {
    muscle.containsAny("cuádriceps", "cuadriceps") ->
        "extiende la rodilla para elevar el cuerpo o la carga"
    muscle.containsAny("glúteo", "gluteo") ->
        "extiende la cadera y ayuda a estabilizar la pelvis al salir de la flexión"
    muscle.containsAny("isquio", "femoral") ->
        "controla la flexión de rodilla y contribuye a extender la cadera"
    muscle.containsAny("aductor") ->
        "asiste la extensión de cadera y mantiene el fémur alineado con la pelvis"
    muscle.containsAny("pantorrilla", "gemelo", "gastrocnemio", "sóleo", "soleo") ->
        "controla el tobillo y permite transferir la fuerza contra el suelo"
    muscle.containsAny("erector", "core", "abdomen") ->
        "resiste la flexión del tronco y conserva la rigidez necesaria para la sentadilla"
    else -> null
}

private fun hipHingeAction(muscle: String): String? = when {
    muscle.containsAny("glúteo", "gluteo") ->
        "extiende la cadera para llevar el tronco y la pelvis hacia la posición final"
    muscle.containsAny("isquio", "femoral") ->
        "extiende la cadera y frena la flexión de forma excéntrica cuando el músculo se alarga"
    muscle.containsAny("erector", "espinal", "lumbar") ->
        "produce o sostiene extensión isométrica de la columna para resistir la flexión"
    muscle.containsAny("cuádriceps", "cuadriceps") ->
        "extiende la rodilla al despegar la carga y reduce la demanda inicial de la cadera"
    muscle.containsAny("aductor") ->
        "ayuda a extender la cadera y estabiliza el fémur durante la bisagra"
    muscle.containsAny("core", "abdomen") ->
        "crea presión y rigidez para mantener costillas, pelvis y columna alineadas"
    muscle.containsAny("pantorrilla", "gemelo") ->
        "estabiliza el tobillo para que la fuerza del suelo llegue a la cadena posterior"
    else -> null
}

private fun elbowFlexionAction(muscle: String): String? = when {
    muscle.containsAny("bíceps", "biceps", "braquial") ->
        "flexiona el codo y, en el bíceps, también puede contribuir a la supinación"
    muscle.containsAny("antebrazo", "braquiorradial") ->
        "ayuda a flexionar el codo y mantiene firme la muñeca durante el agarre"
    muscle.containsAny("deltoides", "hombro") ->
        "mantiene centrada la articulación del hombro mientras el codo se flexiona"
    muscle.containsAny("trapecio", "romboides") ->
        "fija la escápula para que la flexión del codo no arrastre el hombro"
    else -> null
}

private fun elbowExtensionAction(muscle: String): String? = when {
    muscle.containsAny("tríceps", "triceps") ->
        "extiende el codo para alejar la carga del cuerpo"
    muscle.containsAny("pectoral", "pecho") ->
        "ayuda a producir el empuje del hombro y comparte la demanda con el tríceps"
    muscle.containsAny("deltoides", "hombro") ->
        "estabiliza el hombro para que la extensión del codo se transmita de forma limpia"
    muscle.containsAny("antebrazo") ->
        "mantiene estable la muñeca mientras el codo completa la extensión"
    else -> null
}

private fun shoulderAction(muscle: String): String? = when {
    muscle.containsAny("deltoides", "hombro") ->
        "mueve el húmero en el plano de abducción o flexión indicado por el patrón"
    muscle.containsAny("trapecio", "romboides") ->
        "coordina la posición de la escápula para que el húmero tenga una base estable"
    muscle.containsAny("pectoral", "pecho") ->
        "ayuda a flexionar o aproximar horizontalmente el hombro según el ángulo"
    muscle.containsAny("dorsal", "espalda") ->
        "controla la extensión y aducción del hombro frente a la resistencia"
    muscle.containsAny("bíceps", "biceps", "tríceps", "triceps") ->
        "centra la articulación y limita movimientos accesorios mientras el hombro trabaja"
    else -> null
}

private fun hipAction(muscle: String): String? = when {
    muscle.containsAny("glúteo medio", "glúteo menor", "tensor", "gluteo medio", "gluteo menor") ->
        "abduce la cadera y evita que la pelvis caiga hacia el lado contrario"
    muscle.containsAny("glúteo", "gluteo") ->
        "controla la posición de la cadera y contribuye a extenderla o rotarla"
    muscle.containsAny("aductor") ->
        "aproxima el fémur y estabiliza la pelvis frente a la resistencia lateral"
    muscle.containsAny("isquio", "femoral") ->
        "asiste la extensión de la cadera y controla el recorrido"
    muscle.containsAny("flexores cadera", "psoas") ->
        "flexiona la cadera y ayuda a mantener la pelvis orientada"
    else -> null
}

private fun trunkAction(muscle: String): String? = when {
    muscle.containsAny("core", "abdomen", "oblicuo") ->
        "resiste la extensión o rotación del tronco y mantiene la pelvis estable"
    muscle.containsAny("erector", "espinal", "lumbar") ->
        "extiende o fija la columna para resistir que el tronco colapse"
    muscle.containsAny("glúteo", "gluteo") ->
        "controla la pelvis para que la fuerza del tronco se transmita a las piernas"
    muscle.containsAny("flexores cadera", "psoas") ->
        "ayuda a flexionar la cadera sin perder la posición pélvica"
    else -> null
}

private fun ankleAction(muscle: String): String? = when {
    muscle.containsAny("pantorrilla", "gemelo", "gastrocnemio", "sóleo", "soleo") ->
        "produce o controla la flexión plantar y estabiliza el tobillo contra el suelo"
    muscle.containsAny("tibial") ->
        "dorsiflexiona el tobillo y controla la posición del pie"
    muscle.containsAny("cuádriceps", "cuadriceps", "isquio", "femoral") ->
        "ayuda a mantener alineada la cadena inferior mientras el tobillo se mueve"
    else -> null
}

private fun wristOrGripAction(muscle: String): String? = when {
    muscle.containsAny("antebrazo", "braquiorradial") ->
        "genera el agarre y mantiene la muñeca alineada con la carga"
    muscle.containsAny("bíceps", "biceps", "tríceps", "triceps") ->
        "mantiene estable el codo y la muñeca para transmitir fuerza"
    else -> null
}

private fun neckAction(muscle: String): String? = when {
    muscle.containsAny("cuello", "cervical") ->
        "mueve o fija la columna cervical contra la resistencia"
    muscle.containsAny("trapecio") ->
        "estabiliza la cintura escapular y modula la posición cervical"
    else -> null
}

private fun muscleSpecificAction(muscle: String, force: String?): String = when {
    muscle.containsAny("pectoral", "pecho") ->
        "contribuye a la aducción horizontal del hombro"
    muscle.containsAny("deltoides", "hombro") ->
        "contribuye a mover y centrar el hombro"
    muscle.containsAny("dorsal", "espalda") ->
        "contribuye a extender o aducir el hombro"
    muscle.containsAny("bíceps", "biceps", "braquial") ->
        "contribuye a flexionar el codo"
    muscle.containsAny("tríceps", "triceps") ->
        "contribuye a extender el codo"
    muscle.containsAny("glúteo", "gluteo") ->
        "contribuye a extender y estabilizar la cadera"
    muscle.containsAny("isquio", "femoral") ->
        "contribuye a extender la cadera y controlar la rodilla"
    muscle.containsAny("cuádriceps", "cuadriceps") ->
        "contribuye a extender la rodilla"
    muscle.containsAny("aductor") ->
        "contribuye a aproximar y estabilizar la cadera"
    muscle.containsAny("core", "abdomen") ->
        "contribuye a resistir movimientos no deseados del tronco"
    muscle.containsAny("erector", "espinal", "lumbar") ->
        "contribuye a mantener la columna extendida y estable"
    muscle.containsAny("pantorrilla", "gemelo", "sóleo", "soleo") ->
        "contribuye a controlar la flexión plantar del tobillo"
    muscle.containsAny("tibial") ->
        "contribuye a controlar la dorsiflexión del tobillo"
    muscle.containsAny("antebrazo") ->
        "contribuye a mantener el agarre y la muñeca alineada"
    else ->
        "participa en el patrón de ${force?.takeIf { it.isNotBlank() } ?: "fuerza"} y ayuda a controlar la articulación más cercana"
}

private fun String.containsAny(vararg terms: String): Boolean = terms.any(::contains)