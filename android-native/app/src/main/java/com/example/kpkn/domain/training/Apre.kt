package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.domain.workout.LoadSuggestionEngine

/**
 * Las tres versiones de APRE (Autoregulatory Progressive Resistance Exercise) de Bryan Mann (RES1 §2, fuente primaria: *Understanding
 * APRE* y *In Control*, Training & Conditioning, 2017). Cada una trabaja sobre un XRM: el máximo de repeticiones de la versión.
 *
 * | Versión | XRM | Serie 1 (50 % del XRM) | Serie 2 (75 %) | Series 3 y 4 |
 * |---|---|---|---|---|
 * | APRE 10 (hipertrofia) | 10RM = 75 % del 1RM | 12 reps | 10 reps | máximo |
 * | APRE 6 (fuerza base) | 6RM = 83 % | 10 reps | 6 reps | máximo |
 * | APRE 3 (fuerza máxima y potencia) | 3RM = 92 % | 6 reps | 3 reps | máximo |
 */
enum class ApreVariant(val xrmReps: Int, val oneRmFraction: Double, val set1Reps: Int, val set2Reps: Int, val label: String) {
    APRE_3(3, 0.92, 6, 3, "APRE 3"),
    APRE_6(6, 0.83, 10, 6, "APRE 6"),
    APRE_10(10, 0.75, 12, 10, "APRE 10"),
}

/** Una de las cuatro series de un bloque APRE. [targetReps] null = al máximo; [minimumReps] es el mínimo del AMRAP (el XRM). */
data class ApreBlockSet(
    val ordinal: Int,
    val loadKg: Double,
    val targetReps: Int?,
    val minimumReps: Int?,
    val purpose: SetPurpose,
    val calibrationScope: CalibrationScope?,
    val percentOfXrm: Double,
)

/**
 * La tabla de ajuste de Mann (RES1 §2.2, fuente primaria) y el bloque de cuatro series. Dominio puro.
 *
 * - La serie 3, al máximo con el XRM, fija la carga de la serie 4 de la MISMA sesión con la tabla.
 * - Las repeticiones de la serie 4 fijan el XRM de la semana siguiente con la MISMA tabla, aplicada a la carga de la serie 4 (en el
 *   ejemplo de Mann: serie 4 a 315 lb, +10 lb por sus repeticiones, semana 2 con un 6RM de 325 lb).
 * - Las repeticiones con mala técnica no cuentan ([validReps]).
 *
 * La tabla original está en libras; aquí va en porcentaje de la carga y se redondea al material. La conversión ([Band]) es decisión de
 * producto sobre el ejemplo de Mann (6RM de 300 lb: 5 lb = 1,7 %, 10 lb = 3,3 %, 15 lb = 5 %): cada banda de la tabla vale el
 * punto medio de su rango en libras. Los casos que la fuente no define (APRE 10 con menos de 4 repeticiones y APRE 3 con 0) toman
 * la bajada más grande de su tabla.
 */
object Apre {
    const val SET1_FRACTION = 0.50
    const val SET2_FRACTION = 0.75

    /** Salto de material por defecto: la barra con discos de 1,25 kg por lado. */
    const val DEFAULT_INCREMENT_KG = 2.5

    /** Carga del ejemplo de Mann con la que se pasa la tabla de libras a porcentaje. */
    private const val EXAMPLE_LOAD_LB = 300.0

    /** Banda de la tabla: el ajuste en porcentaje de la carga y su rango original en libras. */
    enum class Band(val percent: Double, val originalLb: String) {
        DROP_LARGE(-7.5 / EXAMPLE_LOAD_LB * 100.0, "−5 a −10 lb"),
        DROP_SMALL(-2.5 / EXAMPLE_LOAD_LB * 100.0, "−0 a −5 lb"),
        HOLD(0.0, "mantener"),
        RAISE_SMALL(7.5 / EXAMPLE_LOAD_LB * 100.0, "+5 a +10 lb"),
        RAISE_LARGE(12.5 / EXAMPLE_LOAD_LB * 100.0, "+10 a +15 lb"),
    }

    /** La banda de la tabla de [variant] para [reps] repeticiones válidas. */
    fun band(variant: ApreVariant, reps: Int): Band {
        val r = reps.coerceAtLeast(0)
        return when (variant) {
            // Mann, Figura 2: APRE 10: 4-6 / 7-8 / 9-11 / 12-16 / 17+.
            ApreVariant.APRE_10 -> when {
                r <= 6 -> Band.DROP_LARGE
                r <= 8 -> Band.DROP_SMALL
                r <= 11 -> Band.HOLD
                r <= 16 -> Band.RAISE_SMALL
                else -> Band.RAISE_LARGE
            }
            // APRE 6: 0-2 / 3-4 / 5-7 / 8-12 / 13+.
            ApreVariant.APRE_6 -> when {
                r <= 2 -> Band.DROP_LARGE
                r <= 4 -> Band.DROP_SMALL
                r <= 7 -> Band.HOLD
                r <= 12 -> Band.RAISE_SMALL
                else -> Band.RAISE_LARGE
            }
            // APRE 3: 1-2 / 3-4 / 5-6 / 7+.
            ApreVariant.APRE_3 -> when {
                r <= 2 -> Band.DROP_LARGE
                r <= 4 -> Band.HOLD
                r <= 6 -> Band.RAISE_SMALL
                else -> Band.RAISE_LARGE
            }
        }
    }

    /** Las repeticiones que cuentan: las hechas menos las de mala técnica (Mann: 13 hechas con 2 malas son 11). */
    fun validReps(reps: Int, badTechniqueReps: Int = 0): Int = (reps - badTechniqueReps.coerceAtLeast(0)).coerceAtLeast(0)

    /** El XRM de [variant] a partir de un 1RM, con la equivalencia de Mann, redondeado al material. */
    fun xrmKg(oneRmKg: Double, variant: ApreVariant, incrementKg: Double = DEFAULT_INCREMENT_KG): Double =
        if (oneRmKg <= 0.0) 0.0 else LoadSuggestionEngine.roundLoad(oneRmKg * variant.oneRmFraction, incrementKg)

    /**
     * La carga siguiente: [loadKg] ajustada por la banda de [validReps]. Con «mantener» la carga no se toca (aunque no esté en el
     * salto del material); si no, se redondea al material. Sirve para las dos veces que la tabla se usa: serie 3 → serie 4 y serie 4 → XRM.
     */
    fun nextLoadKg(loadKg: Double, validReps: Int, variant: ApreVariant, incrementKg: Double = DEFAULT_INCREMENT_KG): Double {
        if (loadKg <= 0.0) return 0.0
        val band = band(variant, validReps)
        if (band == Band.HOLD) return loadKg
        return LoadSuggestionEngine.roundLoad(loadKg * (1.0 + band.percent / 100.0), incrementKg)
    }

    /** La carga de la serie 4 de la misma sesión: el XRM ajustado por las repeticiones válidas de la serie 3. */
    fun set4LoadKg(xrmLoadKg: Double, set3ValidReps: Int, variant: ApreVariant, incrementKg: Double = DEFAULT_INCREMENT_KG): Double =
        nextLoadKg(xrmLoadKg, set3ValidReps, variant, incrementKg)

    /** El XRM de la semana siguiente: la carga de la serie 4 ajustada por las repeticiones válidas de esa misma serie 4. */
    fun nextWeekXrmKg(set4LoadKg: Double, set4ValidReps: Int, variant: ApreVariant, incrementKg: Double = DEFAULT_INCREMENT_KG): Double =
        nextLoadKg(set4LoadKg, set4ValidReps, variant, incrementKg)

    /** La versión de APRE que corresponde a un mínimo de AMRAP de [xrmReps] repeticiones (10, 6 o 3); null si no es ninguna. */
    fun variantOfXrmReps(xrmReps: Int): ApreVariant? = ApreVariant.entries.firstOrNull { it.xrmReps == xrmReps }

    /**
     * «Bloque APRE» para el editor (CAL4): las cuatro series de un ejercicio a partir de su [oneRmKg].
     * 1. 50 % del XRM × las repeticiones de la serie 1;
     * 2. 75 % del XRM × las de la serie 2;
     * 3. al máximo con el XRM: AMRAP calibradora de la sesión (mínimo = el XRM), que fija la carga de la serie 4;
     * 4. al máximo con la carga ajustada por la serie 3 (la carga inicial es el XRM): sus repeticiones proponen el XRM de la semana siguiente.
     * Con un 1RM no positivo devuelve una lista vacía: sin referencia no se inventan cargas.
     */
    fun block(oneRmKg: Double, variant: ApreVariant, incrementKg: Double = DEFAULT_INCREMENT_KG): List<ApreBlockSet> {
        if (oneRmKg <= 0.0 || !oneRmKg.isFinite()) return emptyList()
        val xrm = xrmKg(oneRmKg, variant, incrementKg)
        return listOf(
            ApreBlockSet(1, LoadSuggestionEngine.roundLoad(xrm * SET1_FRACTION, incrementKg), variant.set1Reps, null, SetPurpose.NONE, null, 50.0),
            ApreBlockSet(2, LoadSuggestionEngine.roundLoad(xrm * SET2_FRACTION, incrementKg), variant.set2Reps, null, SetPurpose.NONE, null, 75.0),
            ApreBlockSet(3, xrm, null, variant.xrmReps, SetPurpose.CALIBRATOR, CalibrationScope.SESSION, 100.0),
            ApreBlockSet(4, xrm, null, variant.xrmReps, SetPurpose.AMRAP, null, 100.0),
        )
    }
}
