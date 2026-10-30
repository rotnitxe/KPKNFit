package com.example.kpkn.domain.nutrition.supplements

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.SupplementPilaItem
import kotlin.math.abs
import kotlin.math.roundToLong

/** Una toma de suplemento ya registrada: hora `HH:mm` (vacía en los registros viejos sin hora) y dosis en la unidad del suplemento. */
data class SupplementTake(val time: String, val dose: Double, val logId: String = "")

/**
 * Aviso de techo (no bloqueo): registrar [dose] llevaría el día a [projected], por encima de [cap]. «Registrar igual» siempre está disponible.
 */
data class SupplementCeiling(val projected: Double, val cap: Double, val exceeded: Boolean)

/**
 * Lógica pura de la pila de suplementos (Pulido v1 · SUPLEMENTOS): orden por momento, tomas del día a partir de los registros, techo,
 * semana y construcción del registro de una toma. Sin Android.
 */
object SupplementEngine {

    private const val FOOD_ID_PREFIX = "sup:"
    private const val LEGACY_CREATINE_ID = "gen106"
    private const val NOTE_PREFIX = "Suplemento · "

    /** Franja útil de cafeína del lienzo (160–250 mg), la clara bajo la barra. */
    const val CAFFEINE_BAND_MIN_MG = 160.0
    const val CAFFEINE_BAND_MAX_MG = 250.0

    /** kcal por gramo de proteína en polvo (estimadas: el aviso «estimado» las acompaña). */
    const val PROTEIN_KCAL_PER_G = 4.4

    fun takeFoodId(supplementId: String): String = FOOD_ID_PREFIX + supplementId

    /** La pila ordenada por momento del día (estable: a igual momento, el orden en que se añadieron). */
    fun sorted(pila: List<SupplementPilaItem>): List<SupplementPilaItem> = pila.sortedBy { it.moment }

    fun isDone(item: SupplementPilaItem, takes: List<SupplementTake>): Boolean = takes.size >= item.doses.coerceAtLeast(1)

    fun takenCount(pila: List<SupplementPilaItem>, takesById: Map<String, List<SupplementTake>>): Int =
        pila.count { isDone(it, takesById[it.id].orEmpty()) }

    fun dayTotal(takes: List<SupplementTake>): Double = takes.sumOf { it.dose }

    /**
     * Las tomas del día [date] (`yyyy-MM-dd`) por id de suplemento, a partir de los registros. Un alimento `sup:<id>` es una toma de ese
     * suplemento; la creatina rápida de antes (`gen106`) cuenta como toma de creatina con sus gramos. Los registros planificados no cuentan.
     */
    fun takesFromLogs(logs: List<NutritionLog>, date: String): Map<String, List<SupplementTake>> {
        val out = linkedMapOf<String, MutableList<SupplementTake>>()
        for (log in logs) {
            if (log.date.take(10) != date.take(10) || log.status == NutritionStatus.PLANNED) continue
            val time = log.notes?.takeIf { it.startsWith(NOTE_PREFIX) }?.removePrefix(NOTE_PREFIX)?.take(5).orEmpty()
            for (food in log.foods) {
                when {
                    food.id.startsWith(FOOD_ID_PREFIX) ->
                        out.getOrPut(food.id.removePrefix(FOOD_ID_PREFIX)) { mutableListOf() }.add(SupplementTake(time, food.amount, log.id))
                    food.id == LEGACY_CREATINE_ID && food.creatineG > 0.0 ->
                        out.getOrPut("creatina") { mutableListOf() }.add(SupplementTake(time, food.creatineG, log.id))
                }
            }
        }
        return out
    }

    /** Cafeína de comidas y bebidas = total del día menos lo tomado como suplemento (nunca negativa). */
    fun foodCaffeineMg(totalCaffeineMg: Double, caffeineTakes: List<SupplementTake>): Double =
        (totalCaffeineMg - dayTotal(caffeineTakes)).coerceAtLeast(0.0)

    /**
     * El techo del suplemento [def] si se registra [dose] sobre lo que ya hay hoy ([dayTotalNow], en la unidad del suplemento).
     * Null si el suplemento no tiene techo propio.
     */
    fun ceiling(def: SupplementDef, dayTotalNow: Double, dose: Double): SupplementCeiling? {
        val cap = def.cap ?: return null
        val projected = dayTotalNow + dose
        return SupplementCeiling(projected = projected, cap = cap, exceeded = projected > cap + 1e-9)
    }

    /** El registro de una toma: un tentempié con el alimento `sup:<id>`, la hora en las notas y los nutrientes que el motor ya conoce. */
    fun buildTakeLog(def: SupplementDef, dose: Double, time: String, date: String, logId: String): NutritionLog {
        val protein = if (def.id == "proteina") dose else 0.0
        val food = LoggedFood(
            id = takeFoodId(def.id),
            foodName = def.name,
            amount = dose,
            unit = def.unit,
            calories = protein * PROTEIN_KCAL_PER_G,
            protein = protein,
            caffeineMg = if (def.id == "cafeina") dose else 0.0,
            creatineG = if (def.id == "creatina") dose else 0.0,
            isUncertain = protein > 0.0,
        )
        return NutritionLog(
            id = logId,
            date = date,
            mealType = MealType.SNACK,
            foods = listOf(food),
            notes = NOTE_PREFIX + time,
        )
    }

    /**
     * Fracción de la pila tomada por día ([days] de más antiguo a más reciente): suplementos con al menos una toma ese día entre los de
     * la pila. Con la pila vacía, 0 en todos.
     */
    fun weekFractions(pilaIds: Set<String>, takenByDay: List<Set<String>>): List<Float> =
        takenByDay.map { taken -> if (pilaIds.isEmpty()) 0f else (taken intersect pilaIds).size.toFloat() / pilaIds.size }

    /** «100 mg», «1 porción», «2 porciones», «1,6 g». */
    fun doseLabel(def: SupplementDef, amount: Double): String =
        if (def.noun) "${formatNumberEs(amount)} ${if (abs(amount - 1.0) < 1e-9) def.unit else def.unitPlural ?: def.unit}"
        else "${formatNumberEs(amount)} ${def.unit}"

    /** Número en español: miles con punto, decimales con coma, hasta 2 decimales («1.240», «1,6»). */
    fun formatNumberEs(value: Double): String {
        val scaled = (value * 100).roundToLong()
        val whole = scaled / 100
        val frac = abs(scaled % 100)
        val wholeText = abs(whole).toString().reversed().chunked(3).joinToString(".").reversed()
        val sign = if (scaled < 0) "-" else ""
        val fracText = if (frac == 0L) "" else "," + frac.toString().padStart(2, '0').trimEnd('0')
        return sign + wholeText + fracText
    }

    /** «Toca ahora»: hay recordatorio y ya pasó su hora hace menos de [windowMin] minutos (hora `HH:mm` de reloj, mismo día). */
    fun isDueNow(reminder: String?, nowHhmm: String, windowMin: Int = 60): Boolean {
        if (reminder == null) return false
        fun minutes(t: String): Int? = t.split(":").takeIf { it.size == 2 }?.let { (it[0].toIntOrNull() ?: return null) * 60 + (it[1].toIntOrNull() ?: return null) }
        val r = minutes(reminder) ?: return false
        val n = minutes(nowHhmm) ?: return false
        return n - r in 0 until windowMin
    }

    /** Dosis nueva al pulsar − / + en el ajuste: un paso del catálogo, acotada a su mínimo y máximo y redondeada a 2 decimales. */
    fun stepDose(def: SupplementDef, current: Double, direction: Int): Double =
        (((current + def.step * direction) * 100).roundToLong() / 100.0).coerceIn(def.min, def.max)

    /** Las opciones «Cuándo» del ajuste: minutos hacia atrás desde ahora (ahora, −30 min, −1 h) y «esta mañana» a las 08:30. */
    fun whenOptions(nowHhmm: String): List<Pair<String, String>> {
        val (h, m) = nowHhmm.split(":").let { it[0].toInt() to it[1].toInt() }
        val total = h * 60 + m
        fun label(min: Int): String {
            val t = ((min % 1440) + 1440) % 1440
            return "%02d:%02d".format(t / 60, t % 60)
        }
        return listOf(
            "Ahora" to nowHhmm,
            "Hace 30 min" to label(total - 30),
            "Hace 1 h" to label(total - 60),
            "Esta mañana" to "08:30",
        )
    }
}
