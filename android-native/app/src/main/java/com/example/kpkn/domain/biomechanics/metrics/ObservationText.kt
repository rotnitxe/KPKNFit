package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import java.util.Locale
import kotlin.math.abs

/**
 * Los códigos estables de las observaciones (`TechniqueObservation.code`): la interfaz y las tendencias los usan para
 * agrupar y para saltar al instante. No renombrar.
 */
object ObservationCodes {
    const val ROM_TREND = "rom_trend"
    const val TRUNK_CHANGE = "trunk_change"
    const val SQUAT_DEPTH_ABOVE = "squat_depth_above"
    const val SQUAT_DEPTH_IRREGULAR = "squat_depth_irregular"
    const val SQUAT_DEPTH_SHALLOW_REP = "squat_depth_shallow_rep"
    const val SQUAT_HIPS_FIRST = "squat_hips_first"
    const val SQUAT_BAR_DRIFT = "squat_bar_drift"
    const val SQUAT_HEEL_LIFT = "squat_heel_lift"
    const val DEADLIFT_HIPS_FIRST = "deadlift_hips_first"
    const val DEADLIFT_LOCKOUT = "deadlift_lockout"
    const val DEADLIFT_LEAN_BACK = "deadlift_lean_back"
    const val DEADLIFT_STYLE = "deadlift_style"
    const val DEADLIFT_BOUNCE = "deadlift_bounce"
    const val HINGE_KNEE_FLEXION = "hinge_knee_flexion"
    const val HINGE_BAR_AHEAD = "hinge_bar_ahead"
    const val ROM_VARIES = "rom_varies"
    const val THRUST_LOCKOUT = "thrust_lockout"
    const val THRUST_PAUSE = "thrust_pause"
    const val BENCH_ATYPICAL = "bench_atypical"
    const val BENCH_CONTACT = "bench_contact"
    const val BENCH_SHORTER = "bench_shorter"
    const val BENCH_PAUSE = "bench_pause"
    const val PUSHUP_SHORT = "pushup_short"
    const val PUSHUP_BODY_LINE = "pushup_body_line"
    const val OHP_LEAN_BACK = "ohp_lean_back"
    const val OHP_LOCKOUT = "ohp_lockout"
    const val OHP_KNEE_DRIVE = "ohp_knee_drive"
    const val PULL_CHIN = "pull_chin"
    const val PULL_HANG = "pull_hang"
    const val PULL_SWING = "pull_swing"
    const val PULLDOWN_LEAN_BACK = "pulldown_lean_back"
    const val ROW_TORSO_RISE = "row_torso_rise"
    const val ROW_SWING = "row_swing"
    const val ARM_TRUNK_SWING = "arm_trunk_swing"
    const val ARM_ELBOW_DRIFT = "arm_elbow_drift"
    const val CURL_EXTENSION = "curl_extension"
    const val RAISE_MOMENTUM = "raise_momentum"
}

/**
 * Los textos de las observaciones (§7): español neutro, en tipo frase, hechos con su cifra y su repetición.
 *
 * - **Repeticiones:** las que recibe cada función son los índices de `RepReport.index` (desde 0); el texto las numera desde 1.
 * - **Cifras:** el decimal es la coma. Un «≈» marca lo que es una estimación (la profundidad, el talón, la barbilla, todo lo que
 *   sale sin calibrar); las cifras de ángulo medidas de lado y las de centímetros del disco calibrado van sin él.
 * - **Nunca** «error», «incorrecto», «peligroso» ni «riesgo», ni ninguna de las cosas que la especificación (§7, «NUNCA se
 *   emite») no mide: columna, cabeza, rodillas por delante de la punta, valgo, agarre, RIR… Los textos describen lo que se midió.
 *   Una prueba recorre todas las reglas con datos extremos buscando esas palabras.
 */
object ObservationText {
    // ------------------------------------------------------------------------------------------------------------
    // Cifras y repeticiones
    // ------------------------------------------------------------------------------------------------------------

    /** [x] con [decimals] decimales y coma decimal. */
    internal fun number(x: Double, decimals: Int = 0): String {
        val text = String.format(Locale.ROOT, "%.${decimals}f", x)
        return text.replace('.', ',')
    }

    /** Grados enteros: «9°». */
    internal fun deg(x: Double): String = "${number(abs(x), 0)}°"

    /** Grados con un decimal: «2,5°». */
    internal fun deg1(x: Double): String = "${number(abs(x), 1)}°"

    /** Centímetros: con un decimal por debajo de 10 («4,2 cm») y enteros desde ahí («12 cm»); sin «,0» si es redondo («5 cm»). */
    internal fun cm(x: Double): String {
        val v = abs(x)
        val tenths = Math.round(v * 10.0) / 10.0
        return when {
            v >= 10.0 || tenths == Math.floor(tenths) -> "${number(tenths, 0)} cm"
            else -> "${number(tenths, 1)} cm"
        }
    }

    /** Porcentaje entero: «12 %». */
    internal fun pct(x: Double): String = "${number(abs(x), 0)} %"

    /** Segundos con un decimal: «0,3 s». */
    internal fun seconds(ms: Double): String = "${number(ms / 1000.0, 1)} s"

    /**
     * Las repeticiones como se dicen: «la repetición 4», «las repeticiones 7 y 8», «las repeticiones 3, 5 y 6» y «las repeticiones
     * 3 a 6» si son cuatro o más seguidas. [indices] son de `RepReport.index` (desde 0); se numeran desde 1.
     */
    internal fun repetitions(indices: List<Int>): String {
        val numbers = indices.map { it + 1 }.distinct().sorted()
        return when {
            numbers.isEmpty() -> "ninguna repetición"
            numbers.size == 1 -> "la repetición ${numbers[0]}"
            else -> "las repeticiones ${list(numbers)}"
        }
    }

    /** «7 y 8», «3, 5 y 6» o, si hay una tanda seguida de 4 o más, «3 a 6». */
    private fun list(numbers: List<Int>): String {
        val runs = ArrayList<IntRange>()
        var start = numbers[0]
        var prev = numbers[0]
        for (n in numbers.drop(1)) {
            if (n == prev + 1) {
                prev = n
            } else {
                runs.add(start..prev)
                start = n
                prev = n
            }
        }
        runs.add(start..prev)
        val parts = ArrayList<String>()
        for (run in runs) {
            val size = run.last - run.first + 1
            if (size >= 4) parts.add("${run.first} a ${run.last}") else for (n in run) parts.add(n.toString())
        }
        return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    }

    /** Primera letra en mayúscula. */
    private fun sentence(text: String): String = text.replaceFirstChar { it.uppercase() }

    /** «En » + las repeticiones, para empezar una frase: «En las repeticiones 7 y 8», «En la repetición 4». */
    private fun inReps(indices: List<Int>): String = "En ${repetitions(indices)}"

    // ------------------------------------------------------------------------------------------------------------
    // Reglas comunes
    // ------------------------------------------------------------------------------------------------------------

    /** El complemento «de …» de la articulación del perfil, ya contraído: «de la rodilla», «del codo», «del tronco». */
    internal fun ofJoint(angle: RomAngle): String = when (angle) {
        RomAngle.KNEE -> "de la rodilla"
        RomAngle.HIP -> "de la cadera"
        RomAngle.ELBOW -> "del codo"
        RomAngle.ARM_ELEVATION -> "de la elevación del brazo"
        RomAngle.TRUNK -> "del tronco"
    }

    /** Tendencia del recorrido: [deltaDeg] con signo (negativo: se acorta) y su proporción de la media. */
    fun romTrend(angle: RomAngle, deltaDeg: Double, percent: Double): String {
        val verb = if (deltaDeg < 0.0) "se acortó" else "creció"
        return "El recorrido ${ofJoint(angle)} $verb ${deg(deltaDeg)} entre las primeras y las últimas repeticiones (≈ ${pct(percent)})."
    }

    /** Variación del recorrido entre repeticiones (coeficiente de variación). */
    fun romVaries(angle: RomAngle, cvPct: Double): String =
        "El recorrido ${ofJoint(angle)} varió ≈ ${pct(cvPct)} entre repeticiones."

    /** El tronco en el giro de la última repetición frente al de la primera: [deltaDeg] con signo (positivo: más inclinado). */
    fun trunkChange(deltaDeg: Double): String {
        val more = if (deltaDeg >= 0.0) "más" else "menos"
        return "El tronco se inclinó ${deg(deltaDeg)} $more en la última repetición que en la primera."
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sentadilla
    // ------------------------------------------------------------------------------------------------------------

    /** La cadera no bajó del paralelo: [aboveDeg] es lo que quedó por encima (media de las repeticiones). */
    fun squatDepthAbove(reps: List<Int>, aboveDeg: Double): String =
        "${inReps(reps)} la cadera no bajó del paralelo (≈ ${deg(aboveDeg)} por encima)."

    /** La profundidad varió: desviación típica de α entre las repeticiones. */
    fun squatDepthIrregular(sdDeg: Double): String =
        "La profundidad varió entre repeticiones (DE ≈ ${deg(sdDeg)})."

    /** Una repetición quedó más alta que la mediana de la serie. */
    fun squatDepthShallowRep(reps: List<Int>, higherDeg: Double): String =
        "${inReps(reps)} la cadera quedó ≈ ${deg(higherDeg)} más alta que la mediana de la serie."

    /** La cadera sube más que el hombro al empezar la subida: [ratio] veces, y el tronco se inclina [trunkDeg] más. */
    fun squatHipsFirst(reps: List<Int>, ratio: Double, trunkDeg: Double): String =
        "${inReps(reps)} la cadera subió ≈ ${number(ratio, 1)} veces más que el hombro al empezar la subida " +
            "(el tronco se inclinó ${deg(trunkDeg)} más)."

    /**
     * La barra se desplaza durante la subida. [centimetres] `true` con el disco calibrado ([amount] en cm); si no, [amount] es un
     * % del recorrido vertical. [forward]: hacia donde mira el atleta.
     */
    fun barDrift(reps: List<Int>, amount: Double, centimetres: Boolean, forward: Boolean): String {
        val direction = if (forward) "hacia delante" else "hacia atrás"
        val size = if (centimetres) cm(amount) else "≈ ${pct(amount)} del recorrido"
        val prefix = if (reps.size > 1) "hasta " else ""
        return "La barra se desplazó $prefix$size $direction durante la subida en ${repetitions(reps)}."
    }

    /** El talón se despega del suelo [percentOfFoot] % del largo del pie. */
    fun squatHeelLift(reps: List<Int>, percentOfFoot: Double): String =
        "El talón se despegó del suelo (≈ ${pct(percentOfFoot)} del largo del pie) en ${repetitions(reps)}."

    // ------------------------------------------------------------------------------------------------------------
    // Peso muerto, rumano y hip thrust
    // ------------------------------------------------------------------------------------------------------------

    /** La cadera sube [ratio] veces más que la barra al despegar. */
    fun deadliftHipsFirst(reps: List<Int>, ratio: Double, trunkDeg: Double): String =
        "${inReps(reps)} la cadera subió ≈ ${number(ratio, 1)} veces más que la barra al empezar a levantar " +
            "(el tronco se inclinó ${deg(trunkDeg)} más)."

    /**
     * El bloqueo no llegó: [hipDeg] y [kneeDeg] son los ángulos incluidos arriba (`null` el que no falló o no se midió); los
     * mínimos de un bloqueo completo son [hipMin] y [kneeMin].
     */
    fun deadliftLockout(reps: List<Int>, hipDeg: Double?, kneeDeg: Double?, hipMin: Double, kneeMin: Double): String {
        val what = when {
            hipDeg != null && kneeDeg != null ->
                "la cadera llegó a ≈ ${deg(hipDeg)} y la rodilla a ≈ ${deg(kneeDeg)} arriba (completo: ${deg(hipMin)} y ${deg(kneeMin)} o más)"
            hipDeg != null -> "la cadera llegó a ≈ ${deg(hipDeg)} arriba (completa: ${deg(hipMin)} o más)"
            kneeDeg != null -> "la rodilla llegó a ≈ ${deg(kneeDeg)} arriba (completa: ${deg(kneeMin)} o más)"
            else -> "no se completó la extensión arriba"
        }
        return "${inReps(reps)} $what."
    }

    /** El tronco acaba inclinado hacia atrás arriba. */
    fun leanBackAtTop(reps: List<Int>, backDeg: Double): String =
        "${inReps(reps)} el tronco quedó inclinado ${deg(backDeg)} hacia atrás arriba."

    /** Estilo del peso muerto: con parada en el suelo en [paused] repeticiones y continuo en [continuous]. */
    fun deadliftStyle(paused: Int, continuous: Int): String =
        "En el suelo hubo parada de 0,25 s o más en $paused de ${paused + continuous} repeticiones y las otras $continuous fueron continuas."

    /** Rebote en el suelo (menos de 0,1 s de contacto). */
    fun deadliftBounce(reps: List<Int>): String =
        "${inReps(reps)} la barra rebotó en el suelo (menos de ${number(0.1, 1)} s de contacto)."

    /** La rodilla se flexiona [growthDeg] durante la bajada del rumano. */
    fun hingeKneeFlexion(reps: List<Int>, growthDeg: Double): String =
        "${inReps(reps)} la rodilla se flexionó ${deg(growthDeg)} durante la bajada."

    /** La barra queda por delante del medio pie en el fondo. */
    fun hingeBarAhead(reps: List<Int>, centimetres: Double): String =
        "${inReps(reps)} la barra quedó ${cm(centimetres)} por delante del medio pie en el fondo."

    /** El bloqueo del hip thrust. */
    fun thrustLockout(reps: List<Int>, hipDeg: Double, hipMin: Double): String =
        "${inReps(reps)} la cadera llegó a ≈ ${deg(hipDeg)} arriba (completa: ${deg(hipMin)} o más)."

    /** Pausa media arriba en el hip thrust, en ms. */
    fun thrustPause(pauseMs: Double): String =
        "Pausa arriba de ≈ ${seconds(pauseMs)} (mediana de las repeticiones)."

    // ------------------------------------------------------------------------------------------------------------
    // Banca y flexiones
    // ------------------------------------------------------------------------------------------------------------

    /** Repetición atípica: [amount] es lo que se separó de la trayectoria habitual (cm o % del recorrido). */
    fun benchAtypical(reps: List<Int>, amount: Double, centimetres: Boolean): String {
        val size = if (centimetres) cm(amount) else "≈ ${pct(amount)} del recorrido"
        return "En ${repetitions(reps)} la barra se desvió $size respecto de la trayectoria habitual de la serie."
    }

    /** El punto de contacto de la barra con el pecho varía entre repeticiones. */
    fun benchContact(amount: Double, centimetres: Boolean): String {
        val size = if (centimetres) cm(amount) else "≈ ${pct(amount)} del recorrido"
        return "El punto de contacto de la barra con el pecho varió $size entre repeticiones (DE)."
    }

    /** Repetición más corta que la mediana: el fondo quedó [amount] más arriba. */
    fun benchShorter(reps: List<Int>, amount: Double, centimetres: Boolean): String {
        val size = if (centimetres) cm(amount) else "≈ ${pct(amount)} del recorrido"
        val verb = if (reps.size > 1) "quedaron más cortas" else "quedó más corta"
        return "${sentence(repetitions(reps))} $verb: el fondo $size más arriba que en la mediana."
    }

    /** Pausa en el pecho de menos de 0,5 s en una serie con pausa. */
    fun benchPause(reps: List<Int>, shortestMs: Double, medianMs: Double): String {
        val shortest = if (shortestMs <= 0.0) "menos de 0,25 s" else "≈ ${seconds(shortestMs)}"
        return "La pausa en el pecho fue de $shortest en ${repetitions(reps)} (menos de 0,5 s; mediana de la serie: ${seconds(medianMs)})."
    }

    /** Flexión corta: el codo no bajó de [elbowDeg]. */
    fun pushUpShort(reps: List<Int>, elbowDeg: Double): String =
        "${inReps(reps)} el codo no bajó de ≈ ${deg(elbowDeg)} (completa: 90° o menos)."

    /** La línea del cuerpo se quiebra en la cadera [deg] grados: [sags] si cae por debajo de la línea, si no por encima. */
    fun pushUpBodyLine(reps: List<Int>, degrees: Double, sags: Boolean): String {
        val where = if (sags) "por debajo" else "por encima"
        return "${inReps(reps)} la cadera quedó ≈ ${deg(degrees)} $where de la línea del cuerpo."
    }

    // ------------------------------------------------------------------------------------------------------------
    // Press militar
    // ------------------------------------------------------------------------------------------------------------

    /** Inclinación hacia atrás en el pico de velocidad del press. */
    fun ohpLeanBack(reps: List<Int>, backDeg: Double): String =
        "${inReps(reps)} el tronco se inclinó ${deg(backDeg)} hacia atrás durante la subida."

    /** El codo no llegó a la extensión completa arriba: [elbowDeg] es el ángulo incluido. */
    fun ohpLockoutElbow(reps: List<Int>, elbowDeg: Double, elbowMin: Double): String =
        "${inReps(reps)} el codo llegó a ≈ ${deg(elbowDeg)} arriba (completo: ${deg(elbowMin)} o más)."

    /** La muñeca no pasó por encima de la nariz arriba. */
    fun ohpLockoutWrist(reps: List<Int>): String =
        "${inReps(reps)} la muñeca no pasó por encima de la nariz arriba."

    /** Flexión de rodilla antes de subir, en press estricto. */
    fun ohpKneeDrive(reps: List<Int>, kneeDeg: Double): String =
        "Antes de subir, en ${repetitions(reps)} la rodilla se flexionó ≈ ${deg(kneeDeg)}."

    // ------------------------------------------------------------------------------------------------------------
    // Dominada, jalón y remo
    // ------------------------------------------------------------------------------------------------------------

    /** Barbilla sobre la barra en [reached] de [total] repeticiones; [missed] son las que no llegaron. */
    fun pullChin(reached: Int, total: Int): String =
        "Barbilla sobre la barra en ≈ $reached de $total repeticiones."

    /** No se colgó del todo abajo: el codo quedó en [elbowDeg]. */
    fun pullHang(reps: List<Int>, elbowDeg: Double, elbowMin: Double): String =
        "${inReps(reps)} el codo quedó en ≈ ${deg(elbowDeg)} abajo (colgado: ${deg(elbowMin)} o más)."

    /** Balanceo de las caderas ([hipPercentOfBody] % de la altura corporal) o de las rodillas ([kneeDeg]). */
    fun pullSwing(reps: List<Int>, hipPercentOfBody: Double?, kneeDeg: Double?): String {
        val parts = ArrayList<String>()
        if (hipPercentOfBody != null) parts.add("las caderas se balancearon ≈ ${pct(hipPercentOfBody)} de la altura corporal")
        if (kneeDeg != null) parts.add("las rodillas se flexionaron ≈ ${deg(kneeDeg)}")
        return "${inReps(reps)} ${parts.joinToString(" y ")}."
    }

    /** En el jalón el tronco se inclina hacia atrás durante el tirón. */
    fun pulldownLeanBack(reps: List<Int>, backDeg: Double): String =
        "${inReps(reps)} el tronco se inclinó ${deg(backDeg)} hacia atrás durante el tirón."

    /** En el remo el torso se levanta durante la tracción. */
    fun rowTorsoRise(reps: List<Int>, riseDeg: Double): String =
        "${inReps(reps)} el torso se levantó ${deg(riseDeg)} durante la tracción."

    /** Balanceo del tronco en una repetición (remo, curl, tríceps, elevaciones). */
    fun trunkSwing(reps: List<Int>, rangeDeg: Double): String =
        "${inReps(reps)} el tronco se balanceó ${deg(rangeDeg)}."

    // ------------------------------------------------------------------------------------------------------------
    // Curl, tríceps y elevaciones
    // ------------------------------------------------------------------------------------------------------------

    /** El codo se aleja del tronco: θ(cadera, hombro, codo) cambia [driftDeg] en una repetición. */
    fun elbowDrift(reps: List<Int>, driftDeg: Double): String =
        "${inReps(reps)} el codo se desplazó ${deg(driftDeg)} respecto del tronco."

    /** El curl no llega a la extensión completa abajo: el codo quedó en [elbowDeg]. */
    fun curlExtension(reps: List<Int>, elbowDeg: Double, elbowMin: Double): String =
        "${inReps(reps)} el codo quedó en ≈ ${deg(elbowDeg)} abajo (extensión de ${deg(elbowMin)} o más)."

    /** Impulso en las elevaciones: el tronco se balancea [trunkDeg] o la rodilla se flexiona [kneeDeg]. */
    fun raiseMomentum(reps: List<Int>, trunkDeg: Double?, kneeDeg: Double?): String {
        val parts = ArrayList<String>()
        if (trunkDeg != null) parts.add("el tronco se balanceó ${deg(trunkDeg)}")
        if (kneeDeg != null) parts.add("la rodilla se flexionó ≈ ${deg(kneeDeg)}")
        return "${inReps(reps)} ${parts.joinToString(" y ")} al elevar."
    }
}
