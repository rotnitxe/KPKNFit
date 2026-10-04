package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.definitions.NativeProfileKind

/**
 * Paquete A · E2 (curaduría de programas, decisión D6, DEC-w2-04 parte 2) — tabla «calendario propio ↔ reparto
 * equivalente» de los cuatro planes propios.
 *
 * Cada plan propio arma su semana con un calendario fijo por número de días (`NativeProfileCalendars`). Un reparto
 * del catálogo de repartos solo se puede aplicar a un plan propio cuando describe ESE calendario día por día: ese
 * reparto es el «testigo» del par (perfil, días). Esta tabla es la única fuente de esa equivalencia. El generador
 * ([SimpleCyclePersonalizer]) la consulta para aceptar el reparto elegido —y entonces nombra cada día con la
 * etiqueta del reparto y lo deja anotado en el programa— o para rechazarlo con el motivo cerrado `SPLIT`, que se
 * repara quitando el reparto. Nunca se reescribe el calendario ni las dosis para acomodar un reparto.
 *
 * | Perfil | 1 día | 2 días | 3 días | 4 días | 5 días | 6 días |
 * |---|---|---|---|---|---|---|
 * | Músculo (con tirón) | – | `minimalist_x2` | `fullbody_x3` | `ul_x4` | `ppl_ul` | `ppl_x6` |
 * | Músculo sin tirón | – | `minimalist_x2` | `fullbody_x3` | `ul_x4` | – | `ul_x6` |
 * | Fuerza y músculo | – | `minimalist_x2` | `fullbody_x3` | `ul_x4` | `ppl_ul` | `ppl_x6` |
 * | Fuerza | – | – | `pl_sbd_x3` | – | – | – |
 * | Atleta completo | – | – | – | – | – | – |
 *
 * Por qué cada celda es la que es (calendarios de §11.3 y §11.4 del plan r2):
 *  - Músculo y Fuerza y músculo: 2 y 3 días son cuerpo completo, 4 días torso y pierna alternados, 5 días torso,
 *    pierna, empuje, tirón y pierna, y 6 días empuje, tirón y pierna dos veces.
 *  - Músculo sin tirón disponible (calendario corporal): sin remo ni jalón no hay día de tirón. A 6 días los días
 *    son torso y pierna alternados tres veces; a 5 días son pierna, torso, pierna, torso y pierna, que ningún reparto
 *    del catálogo describe, y a 1 día no hay reparto de un solo día.
 *  - Fuerza: los tres días de sentadilla, banca y peso muerto con barra (`f3a`, `f3b` y `f3c`). Con otros días el
 *    calendario de Fuerza no coincide con ningún reparto publicado.
 *  - Atleta completo mezcla días de potencia, fuerza, hipertrofia y cardio: ningún reparto lo describe.
 *
 * Dominio puro: solo identificadores. Que cada id exista en el catálogo de repartos, sea visible, tenga los días
 * indicados y coincida con la estructura del calendario real lo comprueba `NativeProfileSplitWitnessTest`.
 */
object NativeProfileSplitWitness {

    /**
     * Un par (perfil, días) con su reparto equivalente. [pull] dice de qué disponibilidad de tirón depende la
     * equivalencia: `true` = solo con tirón, `false` = solo sin tirón, `null` = la misma con y sin tirón.
     */
    data class Witness(
        val profile: NativeProfileKind,
        val days: Int,
        val pull: Boolean?,
        val splitId: String,
    )

    /** Músculo con tirón y Fuerza y músculo (cuyo calendario no depende del tirón). */
    private val GENERAL: Map<Int, String> = mapOf(
        2 to "minimalist_x2",
        3 to "fullbody_x3",
        4 to "ul_x4",
        5 to "ppl_ul",
        6 to "ppl_x6",
    )

    /** Músculo sin tirón: igual que [GENERAL] salvo 5 días (sin equivalente) y 6 días (torso y pierna ×3). */
    private val MUSCLE_WITHOUT_PULL: Map<Int, String> = mapOf(
        2 to "minimalist_x2",
        3 to "fullbody_x3",
        4 to "ul_x4",
        6 to "ul_x6",
    )

    /** Fuerza: tres días de sentadilla, banca y peso muerto. */
    private const val STRENGTH_THREE_DAYS = "pl_sbd_x3"

    /**
     * Id del reparto equivalente al calendario propio de [profile] con [days] días, o null si ese calendario no
     * tiene reparto equivalente. [hasPull] (por defecto `true`) solo cambia la respuesta de Músculo: es si el
     * material y el nivel permiten algún remo o jalón (si no, el generador usa el calendario corporal sin día de tirón).
     */
    fun witnessSplitId(profile: NativeProfileKind, days: Int, hasPull: Boolean = true): String? = when (profile) {
        NativeProfileKind.STRENGTH -> STRENGTH_THREE_DAYS.takeIf { days == 3 }
        NativeProfileKind.MUSCLE -> (if (hasPull) GENERAL else MUSCLE_WITHOUT_PULL)[days]
        NativeProfileKind.POWERBUILDING -> GENERAL[days]
        NativeProfileKind.COMPLETE_ATHLETE -> null
    }

    /** ¿[splitId] es el reparto equivalente del calendario propio de [profile] con [days] días? */
    fun accepts(profile: NativeProfileKind, days: Int, hasPull: Boolean, splitId: String): Boolean =
        witnessSplitId(profile, days, hasPull) == splitId

    /**
     * Todos los pares (perfil, días) que tienen reparto equivalente, derivados de [witnessSplitId] para que la lista
     * y la función nunca se contradigan. Cuando la respuesta es la misma con y sin tirón sale UNA entrada con
     * `pull = null`; cuando cambia, una por cada disponibilidad en la que existe.
     */
    fun allWitnesses(): List<Witness> = NativeProfileKind.entries.flatMap { profile ->
        (1..MAX_DAYS).flatMap { days ->
            val withPull = witnessSplitId(profile, days, hasPull = true)
            val withoutPull = witnessSplitId(profile, days, hasPull = false)
            when {
                withPull == null && withoutPull == null -> emptyList()
                withPull == withoutPull -> listOf(Witness(profile, days, pull = null, splitId = checkNotNull(withPull)))
                else -> listOfNotNull(
                    withPull?.let { Witness(profile, days, pull = true, splitId = it) },
                    withoutPull?.let { Witness(profile, days, pull = false, splitId = it) },
                )
            }
        }
    }

    /** El wizard ofrece de 1 a 6 días por semana. */
    private const val MAX_DAYS = 6
}
