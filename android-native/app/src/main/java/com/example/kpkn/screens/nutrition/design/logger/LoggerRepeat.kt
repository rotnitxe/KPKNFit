package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.computeFoodTotals
import com.example.kpkn.domain.nutrition.expenditure.nutritionLogDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Lo que el registro ofrece repetir (lienzo f-empty «Repetir» y s-start «Recientes»). Todo puro: sale del historial de comidas, sin
 * tocar la base de datos ni Android.
 */

/** Una tarjeta de «Repetir»: «Avena con plátano» / «Desayuno de ayer · 412 kcal», con los alimentos que añade al tocarla. */
data class LoggerRepeatCard(val key: String, val title: String, val subtitle: String, val foods: List<LoggedFood>)

/** Una fila de «Recientes»: «Avena en copos» / «40 g · desayuno de ayer» / 150 kcal, con el alimento tal como se registró. */
data class LoggerRecentFood(val key: String, val name: String, val caption: String, val kcal: Int, val food: LoggedFood)

/** Días de la «semana» de los hábitos, contando hoy. */
private const val HABIT_WINDOW_DAYS = 6L

/** Veces mínimas para que una combinación sea un hábito. */
private const val HABIT_MIN_TIMES = 2

private val WEEKDAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Una comida consumida y válida, con su día y su orden (0 = la más reciente). */
private data class RepeatEntry(val log: NutritionLog, val day: LocalDate, val rank: Int, val key: String)

/** Las comidas consumidas, copiables y con día legible hasta [today], de la más reciente a la más antigua. */
private fun entriesOf(logs: List<NutritionLog>, today: LocalDate): List<RepeatEntry> {
    val usable = logs.mapNotNull { log ->
        val day = nutritionLogDay(log)
        if (day == null || day.isAfter(today)) null
        else if (log.status != NutritionStatus.CONSUMED || !MealReuse.isCopyable(log) || log.foods.any { it.foodName.isBlank() }) null
        else log to day
    }
    val sorted = usable.sortedWith(
        compareByDescending<Pair<NutritionLog, LocalDate>> { it.second }
            .thenByDescending { it.first.date.drop(11).take(5) }
            .thenByDescending { it.first.id },
    )
    return sorted.mapIndexed { index, (log, day) -> RepeatEntry(log, day, index, combinationKey(log)) }
}

/** La combinación de alimentos de una comida: sus nombres, en minúscula y sin orden. */
private fun combinationKey(log: NutritionLog): String =
    log.foods.map { it.foodName.trim().lowercase() }.sorted().joinToString("|")

/**
 * Las tarjetas de «Repetir» para la comida [meal], como mucho [max]: primero lo que se suele registrar a esa hora (la misma
 * combinación de alimentos, al menos dos veces en los últimos 7 días, «3 veces esta semana») y luego las comidas recientes de
 * cualquier tipo, las más nuevas primero y sin las de hoy. No repite el título. Solo cuentan las comidas consumidas y válidas.
 */
fun loggerRepeatCards(logs: List<NutritionLog>, meal: MealType, today: LocalDate, max: Int = 3): List<LoggerRepeatCard> {
    if (max <= 0) return emptyList()
    val entries = entriesOf(logs, today)
    val cards = mutableListOf<LoggerRepeatCard>()
    val titles = mutableSetOf<String>()
    fun add(card: LoggerRepeatCard) {
        if (cards.size < max && titles.add(card.title.lowercase())) cards += card
    }

    val weekStart = today.minusDays(HABIT_WINDOW_DAYS)
    entries
        .filter { it.log.mealType == meal && !it.day.isBefore(weekStart) }
        .groupBy { it.key }
        .values
        .filter { it.size >= HABIT_MIN_TIMES }
        .sortedWith(compareByDescending<List<RepeatEntry>> { it.size }.thenBy { it.first().rank })
        .forEach { group ->
            val latest = group.first()
            add(
                LoggerRepeatCard(
                    key = "habit:${latest.key}",
                    title = loggerRepeatTitle(latest.log.foods),
                    subtitle = "${group.size} veces esta semana · ${loggerKcalText(latest.log.foods)} kcal",
                    foods = latest.log.foods,
                ),
            )
        }

    entries.filter { it.day.isBefore(today) }.forEach { entry ->
        add(
            LoggerRepeatCard(
                key = "log:${entry.log.id}",
                title = loggerRepeatTitle(entry.log.foods),
                subtitle = "${MealSlots.mealLabel(entry.log.mealType)} ${loggerDayPhrase(entry.day, today)} · ${loggerKcalText(entry.log.foods)} kcal",
                foods = entry.log.foods,
            ),
        )
    }
    return cards
}

/**
 * Los alimentos de «Recientes», como mucho [max]: los de las comidas más recientes (hoy incluido), sin repetir nombre. La cantidad sale
 * del propio registro y la línea dice de qué comida y de qué día era: «40 g · desayuno de ayer».
 */
fun loggerRecentFoods(logs: List<NutritionLog>, today: LocalDate, max: Int = 3): List<LoggerRecentFood> {
    if (max <= 0) return emptyList()
    val seen = mutableSetOf<String>()
    val recents = mutableListOf<LoggerRecentFood>()
    for (entry in entriesOf(logs, today)) {
        for (food in entry.log.foods) {
            if (recents.size >= max) return recents
            val name = food.foodName.trim()
            if (!MealReuse.isCopyable(entry.log.copy(foods = listOf(food))) || !seen.add(name.lowercase())) continue
            val moment = "${MealSlots.mealLabel(entry.log.mealType).lowercase()} ${loggerDayPhrase(entry.day, today)}"
            recents += LoggerRecentFood(
                key = "recent:${name.lowercase()}",
                name = name.replaceFirstChar { it.uppercase() },
                caption = "${loggerAmountText(food.amount)} ${food.unit.trim().ifEmpty { "g" }} · $moment",
                kcal = food.calories.roundToInt(),
                food = food,
            )
        }
    }
    return recents
}

/**
 * El título de una tarjeta: los nombres de los alimentos, el primero con mayúscula y los demás en minúscula, unidos con «con»: «Avena
 * con plátano», «Pollo con arroz y brócoli», «A con B, C y D».
 */
internal fun loggerRepeatTitle(foods: List<LoggedFood>): String {
    val names = foods.map { it.foodName.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (names.isEmpty()) return ""
    val head = names.first().replaceFirstChar { it.uppercase() }
    val rest = names.drop(1).map { it.lowercase() }
    return when (rest.size) {
        0 -> head
        1 -> "$head con ${rest[0]}"
        else -> "$head con ${rest.dropLast(1).joinToString(", ")} y ${rest.last()}"
    }
}

/** Cuándo fue: «de hoy», «de ayer», «del lunes» (de 2 a 6 días) o «del 12 oct» (más antiguo, o futuro). */
internal fun loggerDayPhrase(day: LocalDate, today: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(day, today)
    return when {
        diff == 0L -> "de hoy"
        diff == 1L -> "de ayer"
        diff in 2L..6L -> "del ${WEEKDAYS[day.dayOfWeek.value - 1]}"
        else -> "del ${day.dayOfMonth} ${MONTHS[day.monthValue - 1]}"
    }
}

/** Las kcal de una comida, redondeadas y con punto de millar a partir de 1.000 («1.752»). */
internal fun loggerKcalText(foods: List<LoggedFood>): String {
    val kcal = computeFoodTotals(foods).calories.roundToInt()
    return if (kcal >= 1000) "${kcal / 1000}.${(kcal % 1000).toString().padStart(3, '0')}" else kcal.toString()
}

/** La cantidad sin decimales si es entera («40») y con una coma si no («1,5»). */
internal fun loggerAmountText(amount: Double): String {
    val rounded = amount.roundToInt()
    return if (kotlin.math.abs(amount - rounded) < 0.05) rounded.toString()
    else String.format(Locale.forLanguageTag("es"), "%.1f", amount)
}
