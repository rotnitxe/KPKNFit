package com.example.kpkn.domain.training

import com.example.kpkn.data.models.LoadQuantityConvention
import java.util.Locale

/**
 * Redacción en español llano de las propuestas y avisos de la progresión nativa (§12.4).
 *
 * Dominio puro: sin `android.*` y SIN nombres de ejercicio. El nombre lo resuelve quien dibuja
 * (el programa o el catálogo), así una propuesta guardada sigue leyéndose bien aunque el plan
 * cambie. Los textos describen la evidencia («lo hiciste dos veces con 12 reps…») y la acción
 * propuesta («subir de 20 a 22 kg por mancuerna»), sin códigos internos.
 */
object NativeProgressionText {

    /** `20.0` → «20», `62.5` → «62,5», `21.25` → «21,25»: coma decimal y sin ceros sobrantes. */
    fun formatKg(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.').replace('.', ',')

    /** Veces que se cumplió el criterio: «una vez», «dos veces», «tres veces»… */
    fun timesLabel(count: Int): String = when (count) {
        1 -> "una vez"
        2 -> "dos veces"
        3 -> "tres veces"
        4 -> "cuatro veces"
        else -> "$count veces"
    }

    /**
     * Unidad que acompaña a una carga: «kg por mancuerna», «kg en total»… [stockKind] sale del
     * equipo del catálogo; sin él, una carga por implemento se dice «kg por mano».
     */
    fun loadUnit(
        convention: LoadQuantityConvention,
        stockKind: NativeLoadConventions.StockKind?,
    ): String = when (convention) {
        LoadQuantityConvention.PER_IMPLEMENT -> when (stockKind) {
            NativeLoadConventions.StockKind.DUMBBELL -> "kg por mancuerna"
            NativeLoadConventions.StockKind.KETTLEBELL -> "kg por kettlebell"
            else -> "kg por mano"
        }
        LoadQuantityConvention.TOTAL_EXTERNAL ->
            if (stockKind == NativeLoadConventions.StockKind.BARBELL) "kg en total" else "kg"
        LoadQuantityConvention.ADDITIONAL_BODYWEIGHT -> "kg de lastre"
        // En asistencia el verbo ya dice «la asistencia»; basta «kg».
        LoadQuantityConvention.ASSISTANCE, LoadQuantityConvention.UNSPECIFIED -> "kg"
    }

    // ─── Propuestas ─────────────────────────────────────────────────────────

    /**
     * Subida de carga. [fromKg] es la carga con la que se cumplió el criterio y [toKg] la
     * siguiente que el material declarado permite; sin [toKg] el atleta elige la carga. En
     * asistencia, progresar es asistir MENOS.
     */
    fun loadIncrease(
        times: Int,
        topReps: Int?,
        fromKg: Double?,
        toKg: Double?,
        unit: String,
        assistance: Boolean,
    ): String {
        val evidence = "Lo hiciste ${timesLabel(times)} ${topRepsClause(topReps)}."
        val proposal = when {
            toKg == null && assistance ->
                "Propuesta: en el próximo entrenamiento elige una asistencia un poco menor y deja las mismas repeticiones en reserva."
            toKg == null ->
                "Propuesta: en el próximo entrenamiento elige una carga un poco mayor y deja las mismas repeticiones en reserva."
            assistance -> "Propuesta: bajar la asistencia ${change(fromKg, toKg, unit)}."
            else -> "Propuesta: subir ${change(fromKg, toKg, unit)}."
        }
        return "$evidence $proposal"
    }

    /** Bajada de carga tras quedarse corto con la misma carga. En asistencia, bajar es asistir MÁS. */
    fun loadReduction(
        times: Int,
        minReps: Int?,
        fromKg: Double?,
        toKg: Double?,
        unit: String,
        assistance: Boolean,
    ): String {
        val evidence = "${fellShortClause(times, minReps)}."
        val proposal = when {
            toKg == null && assistance ->
                "Propuesta: en el próximo entrenamiento elige una asistencia un poco mayor."
            toKg == null ->
                "Propuesta: en el próximo entrenamiento elige una carga un poco menor."
            assistance -> "Propuesta: subir la asistencia ${change(fromKg, toKg, unit)}."
            else -> "Propuesta: bajar ${change(fromKg, toKg, unit)}."
        }
        return "$evidence $proposal"
    }

    /** Cambio de variante de un ejercicio con peso corporal; [targetName] es el nombre de la variante. */
    fun bodyweightVariant(
        harder: Boolean,
        times: Int,
        topReps: Int?,
        minReps: Int?,
        targetName: String?,
    ): String {
        val evidence = if (harder) {
            "Lo hiciste ${timesLabel(times)} ${topRepsClause(topReps)}."
        } else {
            "${fellShortClause(times, minReps)}."
        }
        val comparison = if (harder) "más difícil" else "más fácil"
        val destination = targetName?.takeIf { it.isNotBlank() }?.let { "«$it», una" } ?: "una"
        return "$evidence Propuesta: pasar a $destination variante $comparison."
    }

    // ─── Avisos (la progresión no se propone y se explica por qué) ──────────

    /** Había evidencia, pero ya no queda ninguna sesión sin entrenar donde aplicarla. */
    fun noFutureSessionNotice(): String =
        "Ya no quedan sesiones sin entrenar donde aplicar esta progresión; tus registros se conservaron."

    /** Subida merecida, pero el material declarado no ofrece un paso mayor ([detail] lo explica). */
    fun increaseAtLimitNotice(times: Int, topReps: Int?, detail: String): String =
        "Lo hiciste ${timesLabel(times)} ${topRepsClause(topReps)}, pero $detail " +
            "Se mantiene la carga actual; no se propone un cambio."

    /** Bajada merecida, pero el material declarado no ofrece un paso menor ([detail] lo explica). */
    fun reduceAtLimitNotice(times: Int, minReps: Int?, detail: String): String =
        "${fellShortClause(times, minReps)}, pero $detail " +
            "Se mantiene la carga actual; considera una variante más fácil."

    /** La variante corporal cambia el ejercicio completo, pero el registro fue por lados. */
    fun lateralVariantNotice(): String =
        "La variante corporal se aplica al ejercicio completo, pero se registró por lados; " +
            "no se cambia solo una parte del ejercicio."

    /** Subida merecida en un ejercicio con peso corporal sin una variante más difícil en el catálogo. */
    fun noHarderVariantNotice(times: Int, topReps: Int?): String =
        "No hay una variante corporal más difícil disponible para este ejercicio. " +
            "Lo hiciste ${timesLabel(times)} ${topRepsClause(topReps)}; " +
            "se mantiene el ejercicio y no se añade lastre automáticamente."

    /** Hay variante más difícil, pero exige un apoyo estable que no está en el material declarado (H-BW). */
    fun missingSupportNotice(times: Int, topReps: Int?, targetName: String?): String {
        val variant = targetName?.takeIf { it.isNotBlank() }?.let { "«$it»" } ?: "la variante más difícil"
        return "Lo hiciste ${timesLabel(times)} ${topRepsClause(topReps)} y ya podrías pasar a $variant, " +
            "pero necesita un apoyo estable (banco, cajón o similar) que no tienes en tu material. " +
            "Añádelo en tu material y te la propongo; mientras tanto se mantiene el ejercicio."
    }

    // ─── Piezas ─────────────────────────────────────────────────────────────

    private fun topRepsClause(topReps: Int?): String =
        if (topReps != null) "con $topReps reps en todas las series"
        else "llegando al máximo de repeticiones en todas las series"

    /** «Las últimas dos veces no llegaste a las 8 reps mínimas o terminaste con menos repeticiones en reserva de las previstas». */
    private fun fellShortClause(times: Int, minReps: Int?): String {
        val lastTimes = if (times <= 1) "La última vez" else "Las últimas ${timesLabel(times)}"
        val minimum = if (minReps != null) "no llegaste a las $minReps reps mínimas" else "no llegaste al mínimo de repeticiones"
        return "$lastTimes $minimum o terminaste con menos repeticiones en reserva de las previstas"
    }

    private fun change(fromKg: Double?, toKg: Double, unit: String): String =
        if (fromKg != null) "de ${formatKg(fromKg)} a ${formatKg(toKg)} $unit" else "a ${formatKg(toKg)} $unit"
}
