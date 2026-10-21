package com.example.kpkn.domain.auge

/**
 * AugeParameters — Registro único de los parámetros numéricos del modelo de recuperación de AUGE (WP 2.1, F-61).
 *
 * Cada τ base, cada C por canal, cada τ por defecto, la constante de 95 % y los límites de τ se definen AQUÍ y solo aquí:
 * el motor ([AugeRecoveryEngine]), la evidencia inicial ([InitialRecoveryV2Model]), el aprendiz ([AugeAdaptiveEngine]) y la
 * interferencia ([InterferenceEngine]) los leen. `AugeParametersConsistencyTest` falla si reaparece una copia fuera de este archivo.
 *
 * Lo que ya tenía una sola fuente se queda donde está: [AugeUtils] (conservación, rendimientos decrecientes, pesos de estrés,
 * límites de capacidad, pisos fisiológicos y warps de horas), [AcwrThresholds], [RecoveryBands] y `AugeFatigueEngine.ATHLETE_CAPACITY`.
 */
object AugeParameters {

    /** Versión del registro: súbela cuando cambie CUALQUIER valor de este objeto; el caché del aprendiz la guarda para invalidar los τ aprendidos. */
    const val VERSION: Int = 1

    // ─── τ base por perfil (horas) ───────────────────────────────────────────

    /** τ del perfil rápido (bíceps, tríceps, deltoides, pantorrillas, abdomen…). */
    const val TAU_FAST_H = 24.0

    /** τ del perfil medio (pectorales, dorsales, trapecio, core…); también el de un músculo sin perfil. */
    const val TAU_MEDIUM_H = 48.0

    /** τ del perfil lento (cuádriceps, glúteos). */
    const val TAU_SLOW_H = 72.0

    /** τ del perfil pesado (isquiosurales, erectores espinales). */
    const val TAU_HEAVY_H = 96.0

    private const val PROFILE_FAST = "fast"
    private const val PROFILE_MEDIUM = "medium"
    private const val PROFILE_SLOW = "slow"
    private const val PROFILE_HEAVY = "heavy"

    /** τ base de cada perfil, por su nombre (el que devuelve [profileOf]). */
    val PROFILE_TAU_HOURS: Map<String, Double> = mapOf(
        PROFILE_FAST to TAU_FAST_H,
        PROFILE_MEDIUM to TAU_MEDIUM_H,
        PROFILE_SLOW to TAU_SLOW_H,
        PROFILE_HEAVY to TAU_HEAVY_H,
    )

    /** Perfil de recuperación de cada músculo con perfil, en el orden histórico del motor. */
    private val MUSCLE_PROFILE: Map<String, String> = mapOf(
        "Bíceps" to PROFILE_FAST, "Tríceps" to PROFILE_FAST, "Deltoides" to PROFILE_FAST,
        "Deltoides Anterior" to PROFILE_FAST, "Deltoides Lateral" to PROFILE_FAST, "Deltoides Posterior" to PROFILE_FAST,
        "Pantorrillas" to PROFILE_FAST, "Abdomen" to PROFILE_FAST, "Antebrazo" to PROFILE_FAST,
        "Gemelos" to PROFILE_FAST, "Sóleo" to PROFILE_FAST, "Oblicuos" to PROFILE_FAST, "Costales" to PROFILE_FAST,
        "Pectorales" to PROFILE_MEDIUM, "Dorsales" to PROFILE_MEDIUM, "Hombros" to PROFILE_MEDIUM, "Trapecio" to PROFILE_MEDIUM,
        "Romboide" to PROFILE_MEDIUM, "Redondo Menor" to PROFILE_MEDIUM, "Redondo Mayor" to PROFILE_MEDIUM,
        "Infraespinoso" to PROFILE_MEDIUM, "Supraespinoso" to PROFILE_MEDIUM, "Subescapular" to PROFILE_MEDIUM,
        "Serrato Anterior" to PROFILE_MEDIUM,
        "Cuádriceps" to PROFILE_SLOW, "Glúteos" to PROFILE_SLOW,
        "Glúteo Mayor" to PROFILE_SLOW, "Glúteo Medio" to PROFILE_SLOW, "Glúteo Menor" to PROFILE_SLOW,
        "Aductores" to PROFILE_MEDIUM, "Tensor Fascia Lata" to PROFILE_MEDIUM,
        "Isquiosurales" to PROFILE_HEAVY, "Bíceps Femoral" to PROFILE_HEAVY, "Semitendinoso" to PROFILE_HEAVY,
        "Semimembranoso" to PROFILE_HEAVY, "Erectores Espinales" to PROFILE_HEAVY, "Core" to PROFILE_MEDIUM,
        "Cuello" to PROFILE_MEDIUM, "Psoas" to PROFILE_MEDIUM, "Lumbar" to PROFILE_HEAVY,
    )

    /** Músculos con perfil, en el orden del mapa: tras los 13 [PILLARS], el motor arma con ellos el conjunto completo de baterías por músculo. */
    val PROFILED_MUSCLES: List<String> = MUSCLE_PROFILE.keys.toList()

    /** Índice por nombre normalizado (minúsculas, sin tildes); si dos nombres chocaran, manda el primero, como el recorrido lineal de antes. */
    private val PROFILE_BY_NORMALIZED_NAME: Map<String, String> = buildMap {
        MUSCLE_PROFILE.forEach { (muscle, profile) ->
            val key = normKey(muscle)
            if (key !in this) put(key, profile)
        }
    }

    /** Perfil de [muscle] (`fast`, `medium`, `slow` o `heavy`), o null si no tiene; ignora mayúsculas, tildes y espacios de los extremos. */
    fun profileOf(muscle: String): String? = PROFILE_BY_NORMALIZED_NAME[normKey(muscle)]

    /** τ base en horas de [muscle]: el de su perfil, o el medio si no tiene. Sin límites: el motor los aplica con [TAU_MIN_H] y [TAU_MAX_H]. */
    fun baseTauHours(muscle: String): Double = PROFILE_TAU_HOURS.getValue(profileOf(muscle) ?: PROFILE_MEDIUM)

    // ─── Pilares y guardas ───────────────────────────────────────────────────

    /** Los 13 pilares de las baterías por músculo y del promedio muscular global, en el orden del motor. */
    val PILLARS: List<String> = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

    /** τ base de cada pilar; se deriva de [baseTauHours], no se escribe aparte. */
    val PILLAR_TAU_HOURS: Map<String, Double> = PILLARS.associateWith { baseTauHours(it) }

    /** Músculos que guardan a Columna (erectores o lumbar, dorsales y trapecio). */
    val SPINE_GUARD_MUSCLES: List<String> = listOf("Erectores Espinales", "Dorsales", "Trapecio")

    // ─── C por canal ─────────────────────────────────────────────────────────

    /** C del músculo: la batería es `100·exp(−R/C)`, con R el % de la capacidad que sigue sin recuperar. */
    const val C_MUSCLE = 90.0

    /** C de la Energía (misma ley que [C_MUSCLE]). */
    const val C_ENERGY = 75.0

    /** C de la Columna (misma ley que [C_MUSCLE]). */
    const val C_SPINAL = 70.0

    // ─── τ por defecto de los canales ────────────────────────────────────────

    /** τ de la Energía, en horas crudas, cuando el caché no trae uno aprendido. */
    const val DEFAULT_TAU_ENERGY_H = 36.0

    /** τ de la Columna, en horas efectivas del warp espinal, cuando el caché no trae uno aprendido. */
    const val DEFAULT_TAU_SPINAL_H = 52.0

    /** τ del anillo muscular global en un ajuste manual sin τ aprendidos, en horas del warp muscular. */
    const val DEFAULT_TAU_GLOBAL_MUSCLE_H = 48.0

    // ─── Constante de recuperación y límites ─────────────────────────────────

    /** ln 20 ≈ 2,9957: con k = TAU_K_95 / τ el residuo cae al 5 % (95 % recuperado) tras τ horas efectivas. */
    const val TAU_K_95 = 2.9957

    /** τ mínimo de un músculo en el motor, en horas. */
    const val TAU_MIN_H = 18.0

    /** τ máximo de un músculo en el motor, en horas. */
    const val TAU_MAX_H = 144.0

    private fun normKey(s: String): String = s
        .lowercase().trim()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
}
