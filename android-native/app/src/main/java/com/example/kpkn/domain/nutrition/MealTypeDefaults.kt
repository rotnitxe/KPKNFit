package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.SystemAppClock
import java.time.ZoneId

/**
 * Tipo de comida que se propone al abrir el logger sin una elección explícita (WP-U13 / C12).
 *
 * Una sola regla horaria para Home y Nutrición (antes Nutrición proponía siempre «Almuerzo»
 * y Home decidía por hora): 5-10 desayuno, 11-15 almuerzo, 16-20 cena y el resto (noche y
 * madrugada) snack. [hour] es la hora local 0-23; cualquier otro valor cae en snack.
 */
fun defaultMealTypeFor(hour: Int): MealType = when (hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..20 -> MealType.DINNER
    else -> MealType.SNACK
}

/**
 * [defaultMealTypeFor] con la hora local de [clock] en [zone]. Reloj y zona son inyectables
 * para probar los bordes horarios sin depender de la hora real del dispositivo.
 */
fun defaultMealTypeNow(
    clock: AppClock = SystemAppClock,
    zone: ZoneId = ZoneId.systemDefault(),
): MealType = defaultMealTypeFor(clock.now().atZone(zone).hour)
