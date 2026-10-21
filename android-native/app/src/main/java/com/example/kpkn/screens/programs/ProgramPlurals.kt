package com.example.kpkn.screens.programs

/** «0 sesiones», «1 sesión», «3 sesiones»: el plural de sesión en los textos de Programas y del detalle del programa. */
internal fun pluralSesiones(count: Int): String = plural(count, "sesión", "sesiones")

/** «1 semana», «2 semanas». */
internal fun pluralSemanas(count: Int): String = plural(count, "semana", "semanas")

/** «1 día», «7 días». */
internal fun pluralDias(count: Int): String = plural(count, "día", "días")

/** La cifra con su palabra: en singular solo con 1; cualquier otra cifra, en plural. */
internal fun plural(count: Int, singular: String, many: String): String = "$count ${if (count == 1) singular else many}"
