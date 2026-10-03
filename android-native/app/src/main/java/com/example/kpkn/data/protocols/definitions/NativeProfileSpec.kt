package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayMinimumDose
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.RecipeCardioProgression
import com.example.kpkn.data.protocols.RecipeCompositionProfile
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import kotlin.math.ceil

/**
 * Los cuatro planes propios KPKN de §11.1 (paquete F, T-004a).
 *
 * Este archivo es SOLO tablas de especificación: calendarios §11.3/§11.4,
 * dosis §11.2, variantes de material §13.3 y las reglas de semana/descarga
 * §12.1. La resolución real de configuraciones (catálogo aprobado + material
 * declarado), el fitter §12.3 y la materialización viven en
 * `domain/training/SimpleCyclePersonalizer.kt`; este paquete no importa
 * `android.*` ni conoce el catálogo en runtime.
 *
 * Regla STOP: los IDs de configuración de [NativeCandidateTable] existen en
 * `CatalogIds`, en los pools curados ya usados por el motor nativo o fueron
 * verificados APPROVED en los dos assets distribuidos por el paquete E. Nunca
 * se inventa una configuración aquí.
 */
enum class NativeProfileKind(
    val entryId: String,
    val sourceId: String,
    /** Composición de §14.3. */
    val compositionProfile: RecipeCompositionProfile,
) {
    // El título y la descripción de cada perfil viven en PlanEditorialTable
    // (`com.example.kpkn.data.programs`): este enum solo lleva identidad y composición.
    STRENGTH(
        entryId = "native:strength-foundation-v2",
        sourceId = "strength-foundation",
        compositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
    ),
    MUSCLE(
        entryId = "native:muscle-foundation-v2",
        sourceId = "muscle-foundation",
        compositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
    ),
    POWERBUILDING(
        entryId = "native:powerbuilding-foundation-v2",
        sourceId = "powerbuilding-foundation",
        compositionProfile = RecipeCompositionProfile.NATIVE_COMPACT,
    ),
    COMPLETE_ATHLETE(
        entryId = "native:complete-athlete-v2",
        sourceId = "complete-athlete",
        compositionProfile = RecipeCompositionProfile.MIXED_CARDIO,
    ),
    ;

    companion object {
        private val byEntryId = enumValues<NativeProfileKind>().associateBy { it.entryId }
        private val bySourceId = enumValues<NativeProfileKind>().associateBy { it.sourceId }

        /**Perfil propio por id de entrada (`native:…`) o sourceId; null = no es propio. */
        fun fromEntryId(id: String): NativeProfileKind? = byEntryId[id] ?: bySourceId[id]
    }
}

/** Nivel de exigencia según §15.1 (NEW/RETURNING → principiante). */
enum class NativeDoseLevel { BEGINNER, INTERMEDIATE, ADVANCED }

/** Claves de slot de los arquetipos de §11.2 (S/B/D/R/V/O/U/C/L/A/T/G + variantes). */
enum class NativeSlotKey {
    /** Sentadilla. */
    S,
    /** Empuje horizontal. */
    B,
    /** Bisagra (peso muerto / rumano). */
    D,
    /** Remo / tirón horizontal. */
    R,
    /** Tirón vertical. */
    V,
    /** Empuje vertical. */
    O,
    /** Pierna unilateral. */
    U,
    /** Core. */
    C,
    /** Elevación lateral. */
    L,
    /** Curl de bíceps. */
    A,
    /** Extensión de tríceps. */
    T,
    /** Gemelo. */
    G,
    /** Potencia sobre patrón de sentadilla (§11.5 `P(S)`). */
    PS,
    /** Potencia sobre patrón de empuje horizontal (§11.5 `P(B)`). */
    PB,
    /** Puente de glúteos corporal (extensión de cadera §13.3/§13.6). */
    BG,
    /** Superman corporal (extensores; nunca cuenta como tirón §13.3). */
    SM,
}

/** Slot de arquetipo: `key` + intención F/Fv/H/I/C/P de §11.2. */
data class NativeArchetypeSlot(val key: NativeSlotKey, val intent: SlotIntent)

/**
 * Parsea la notación de §11.3: `S:F,B:Fv,R:H,C:C` (letra dos puntos intención).
 * `Fv` se mapea a [SlotIntent.FV].
 */
fun parseNativeArchetype(spec: String): List<NativeArchetypeSlot> =
    if (spec.isBlank()) {
        emptyList()
    } else spec.split(',').map { token ->
        val clean = token.trim()
        val keyPart = clean.substringBefore(':')
        val intentPart = clean.substringAfter(':', missingDelimiterValue = "H").trim()
        val key = when (keyPart) {
            "S" -> NativeSlotKey.S
            "B" -> NativeSlotKey.B
            "D" -> NativeSlotKey.D
            "R" -> NativeSlotKey.R
            "V" -> NativeSlotKey.V
            "O" -> NativeSlotKey.O
            "U" -> NativeSlotKey.U
            "C" -> NativeSlotKey.C
            "L" -> NativeSlotKey.L
            "A" -> NativeSlotKey.A
            "T" -> NativeSlotKey.T
            "G" -> NativeSlotKey.G
            "PS" -> NativeSlotKey.PS
            "PB" -> NativeSlotKey.PB
            "BG" -> NativeSlotKey.BG
            "SM" -> NativeSlotKey.SM
            else -> error("Slot de arquetipo desconocido: $keyPart")
        }
        val intent = when (intentPart) {
            "F" -> SlotIntent.F
            "Fv" -> SlotIntent.FV
            "H" -> SlotIntent.H
            "I" -> SlotIntent.I
            "C" -> SlotIntent.C
            "P" -> SlotIntent.P
            else -> error("Intención de arquetipo desconocida: $intentPart")
        }
        NativeArchetypeSlot(key, intent)
    }

/** Clase de material resuelta para una sesión (§11.1/§13.3). */
enum class NativeMaterialTier { BARBELL, DUMBBELL, BAND, BODYWEIGHT }

/**
 * Dosis base de §11.2 por intención y nivel. `repsMin/repsMax` son el rango de
 * trabajo; `sets` es la dosis inicial (se materializa el mínimo del rango de
 * series). Descarga (§12.1): `ceil(sets/2)` con mínimo 1 y RIR 4.
 */
data class NativeDose(
    val role: SlotRole,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val rir: Int,
    val restSeconds: Int,
    /** true = modalidad de tiempo (20–30 s) en lugar de reps. */
    val timed: Boolean = false,
)

object NativeDoseTable {
    const val SPEED_REST_SECONDS = 90
    const val SPEED_REPS = 3
    const val SPEED_RIR = 5
    /** §11.5: 2×3 en semanas 1–2 para todos los niveles; base después. */
    const val SPEED_SETS_WEEKS_1_2 = 2
    const val HEAVY_T1_REST_SECONDS = 180
    const val T2_REST_SECONDS = 120
    const val T3_COMPOUND_REST_SECONDS = 120
    const val ISOLATION_REST_SECONDS = 90
    const val CORE_REST_SECONDS = 60

    fun doseFor(intent: SlotIntent, level: NativeDoseLevel, bodyweightRepRange: Boolean): NativeDose = when (intent) {
        SlotIntent.F -> when (level) {
            NativeDoseLevel.BEGINNER -> NativeDose(SlotRole.T1_MAIN, 2, 4, 6, 3, HEAVY_T1_REST_SECONDS)
            else -> NativeDose(SlotRole.T1_MAIN, 3, 3, 5, 2, HEAVY_T1_REST_SECONDS)
        }
        SlotIntent.FV -> when (level) {
            NativeDoseLevel.BEGINNER -> NativeDose(SlotRole.T2_SUPPLEMENTAL, 2, 6, 8, 3, T2_REST_SECONDS)
            else -> NativeDose(SlotRole.T2_SUPPLEMENTAL, 2, 6, 8, 2, T2_REST_SECONDS)
        }
        SlotIntent.H -> when (level) {
            NativeDoseLevel.BEGINNER ->
                NativeDose(SlotRole.T3_ACCESSORY, 2, if (bodyweightRepRange) 8 else 8, if (bodyweightRepRange) 15 else 12, 3, T3_COMPOUND_REST_SECONDS)
            else ->
                NativeDose(SlotRole.T3_ACCESSORY, 3, 8, if (bodyweightRepRange) 15 else 12, 2, T3_COMPOUND_REST_SECONDS)
        }
        SlotIntent.I -> when (level) {
            NativeDoseLevel.BEGINNER -> NativeDose(SlotRole.T3_ACCESSORY, 1, 10, 15, 3, ISOLATION_REST_SECONDS)
            else -> NativeDose(SlotRole.T3_ACCESSORY, 2, 10, 15, 2, ISOLATION_REST_SECONDS)
        }
        SlotIntent.C -> when (level) {
            NativeDoseLevel.BEGINNER -> NativeDose(SlotRole.T3_ACCESSORY, 1, 8, 12, 3, CORE_REST_SECONDS)
            else -> NativeDose(SlotRole.T3_ACCESSORY, 2, 8, 12, 2, CORE_REST_SECONDS)
        }
        SlotIntent.P -> NativeDose(
            role = SlotRole.SPEED,
            sets = if (level == NativeDoseLevel.BEGINNER) SPEED_SETS_WEEKS_1_2 else SPEED_SETS_WEEKS_1_2 + 1,
            repsMin = SPEED_REPS,
            repsMax = SPEED_REPS,
            rir = SPEED_RIR,
            restSeconds = SPEED_REST_SECONDS,
        )
    }

    /** §12.1: `ceil(sets/2)` con mínimo 1 por slot de resistencia ordinaria. */
    fun deloadSets(baseSets: Int): Int = ceil(baseSets / 2.0).toInt().coerceAtLeast(1)
}

/**
 * Tabla de selección por slot (§13.3): orden de elección barra → mancuerna →
 * aparato/polea → banda → reserva corporal. La resolución filtra por material
 * declarado y catálogo aprobado; un candidato no viable se salta al siguiente.
 * Lista vacía = slot suprimido (aislamiento sin resistencia, §13.3).
 */
object NativeCandidateTable {
    // Reservas curadas sin constante en CatalogIds.
    private const val SQ_GOBLET = "quads_sentadilla_copa__default"
    private const val SQ_BODYWEIGHT = "quads_sentadilla_sin_carga__default"
    private const val KNEE_PUSH_UP = "knee_push_up__default"
    private const val PUSH_UP_FLAT = "push_up__flat"
    private const val BAND_ROW = "back_remo_banda__default"
    private const val FROG_PUMP = "glutes_frog_pumps__default"
    private const val SUPERMAN = "back_superman_suelo__default"
    private const val BAND_CHEST_PRESS = "tren_superior_press_banda_resistencia__default"
    private const val MACHINE_OLYMPIC_PRESS = "military_press__machine"
    private const val MACHINE_LATERAL_RAISE = "seated_lateral_raise__machine"
    private const val MACHINE_PUSHDOWN = "triceps_pushdown__bilateral__machine"
    private const val BODYWEIGHT_TRICEPS_PRESS = "triceps_flexiones_esfinge__default"
    private const val MACHINE_PREACHER_CURL = "preacher_curl__machine"
    private const val MACHINE_ROW = "chest_supported_row__machine__medium"
    private const val MACHINE_PULLDOWN = "lat_pulldown__bilateral__machine"
    private const val INCLINE_DB_PRESS = "incline_bench_press__dumbbells"

    /**
     * Candidatos por (clave de slot, intención). `F/Fv` de S/B/D usan las
     * configuraciones de competición cuando hay barra (§11.1/§14.3); `H` usa la
     * variante de volumen. El material decide cuál se resuelve.
     */
    fun candidatesFor(key: NativeSlotKey, intent: SlotIntent): List<String> = when (key) {
        NativeSlotKey.S -> when (intent) {
            SlotIntent.F, SlotIntent.FV -> listOf(CatalogIds.SQ_LOW, SQ_GOBLET, SQ_BODYWEIGHT)
            else -> listOf(CatalogIds.SQ_HIGH, SQ_GOBLET, SQ_BODYWEIGHT)
        }
        NativeSlotKey.B -> when (intent) {
            SlotIntent.F, SlotIntent.FV ->
                listOf(CatalogIds.BP, CatalogIds.BP_DB, CatalogIds.BP_FLOOR, KNEE_PUSH_UP, PUSH_UP_FLAT)
            else ->
                listOf(
                    CatalogIds.BP,
                    CatalogIds.BP_DB,
                    INCLINE_DB_PRESS,
                    CatalogIds.BP_FLOOR,
                    BAND_CHEST_PRESS,
                    KNEE_PUSH_UP,
                    PUSH_UP_FLAT,
                )
        }
        NativeSlotKey.D -> when (intent) {
            SlotIntent.F -> listOf(CatalogIds.DL, CatalogIds.RDL, RDL_DB, GLUTE_BRIDGE, FROG_PUMP)
            else -> listOf(CatalogIds.RDL, RDL_DB, GLUTE_BRIDGE, FROG_PUMP)
        }
        NativeSlotKey.R -> listOf(
            CatalogIds.ROW,
            CatalogIds.ROW_DB,
            CatalogIds.ROW_CABLE,
            MACHINE_ROW,
            BAND_ROW,
            LOW_BAR_ROW,
        )
        NativeSlotKey.V -> listOf(
            CatalogIds.LAT,
            MACHINE_PULLDOWN,
            CatalogIds.PULLUP,
            LOW_BAR_ROW,
            CatalogIds.ROW_DB,
            CatalogIds.ROW,
            BAND_ROW,
        )
        NativeSlotKey.O -> listOf(
            CatalogIds.OHP,
            OHP_DB,
            MACHINE_OLYMPIC_PRESS,
            KNEE_PUSH_UP,
            PUSH_UP_FLAT,
        )
        NativeSlotKey.U -> listOf(CatalogIds.LUNGE_W_BARBELL, CatalogIds.LUNGE_W, CatalogIds.LUNGE_REVERSE_BODYWEIGHT)
        NativeSlotKey.C -> listOf(CatalogIds.CRUNCH, CatalogIds.PLANCHA)
        NativeSlotKey.L -> listOf(CatalogIds.LATERAL, MACHINE_LATERAL_RAISE, CatalogIds.LATERAL_CABLE)
        NativeSlotKey.A -> listOf(CatalogIds.CURL, CatalogIds.HAMMER, HAMMER_BAND, MACHINE_PREACHER_CURL)
        NativeSlotKey.T -> listOf(CatalogIds.PUSHDOWN, BAND_PUSHDOWN, MACHINE_PUSHDOWN, BODYWEIGHT_TRICEPS_PRESS)
        NativeSlotKey.G -> listOf(CatalogIds.CALF, CALF_BODYWEIGHT)
        // §11.5: carga ligera específica con barra/rack; corporal sin salto si no.
        NativeSlotKey.PS -> listOf(CatalogIds.SQ_HIGH, PS_BODYWEIGHT, SQ_GOBLET)
        NativeSlotKey.PB -> listOf(CatalogIds.BP, CatalogIds.BP_DB, CatalogIds.BP_FLOOR, PB_BODYWEIGHT, PUSH_UP_FLAT)
        NativeSlotKey.BG -> listOf(GLUTE_BRIDGE_BODYWEIGHT, FROG_PUMP)
        NativeSlotKey.SM -> listOf(SUPERMAN)
    }

    /** Variante corporal de P(S) para principiante/material mínimo (§11.5). */
    const val PS_BODYWEIGHT = "quads_sentadilla_sin_carga__default"
    /** Variante corporal de P(B): flexión con rodillas, sin salto de manos (§11.5). */
    const val PB_BODYWEIGHT = "knee_push_up__default"
    const val GLUTE_BRIDGE_BODYWEIGHT = "glutes_puente_gluteos__bilateral__bodyweight"
    const val GLUTE_BRIDGE = GLUTE_BRIDGE_BODYWEIGHT
    const val CALF_BODYWEIGHT = "calf_raise__bilateral__bodyweight"
    /** Remo horizontal alternativo, válido solo con barra baja confirmada (§13.3). */
    const val LOW_BAR_ROW = "back_remo_invertido__default"
    const val BAND_PUSHDOWN = "triceps_pushdown__bilateral__band"
    const val RDL_DB = "romanian_deadlift__bilateral__dumbbells"
    const val OHP_DB = "military_press__dumbbells"
    const val HAMMER_BAND = "hammer_curl__band"

    /**
     * Exención del filtro de dificultad para principiantes (§13.3): los SBD y
     * presses básicos se permiten como aprendizaje con RIR 3 y aproximaciones.
     * Las variantes difíciles de cuerpo (dominadas, Nordics, cossack) siguen
     * excluidas por [SimpleCyclePersonalizer.hardBodyweight].
     */
    fun beginnerDifficultyExempt(key: NativeSlotKey): Boolean =
        key in setOf(NativeSlotKey.S, NativeSlotKey.B, NativeSlotKey.D)
}

/** Cadena de una semana: arquetipos de día en orden (§11.3/§11.4). */
data class NativeDayArchetype(
    val name: String,
    val slots: List<NativeArchetypeSlot>,
    /** Bloque de cardio del día (Atleta completo). */
    val cardio: NativeCardioRole = NativeCardioRole.NONE,
)

enum class NativeCardioRole {
    /** Sin cardio. */
    NONE,
    /** Cardio continuo tras la resistencia (default por SESSION_TIME §11.4). */
    AFTER_STRENGTH,
    /** Día dedicado: cardio primero y accesorios después (§11.4 día 3/5). */
    DEDICATED_WITH_ACCESSORIES,
    /** Día solo cardio (§11.4). */
    ONLY,
    /** Bloque breve tras XH (10 min). */
    AFTER_STRENGTH_BRIEF,
}

object NativeProfileCalendars {
    private fun a(name: String, spec: String, cardio: NativeCardioRole = NativeCardioRole.NONE) =
        NativeDayArchetype(name, parseNativeArchetype(spec), cardio)

    // ─── Músculo (§11.3) ────────────────────────────────────────────────────
    private val FA = a("FA", "S:H,B:H,R:H,D:H,C:C")
    private val FB = a("FB", "U:H,O:H,V:H,D:H,G:I")
    private val UA = a("UA", "B:H,R:H,O:H,A:I,T:I")
    private val UB = a("UB", "V:H,B:H,R:H,L:I,A:I")
    private val LA = a("LA", "S:H,D:H,U:H,G:I,C:C")
    private val LB = a("LB", "D:H,S:H,U:H,G:I,C:C")
    private val PU = a("PU", "B:H,O:H,L:I,T:I")
    private val PL = a("PL", "R:H,V:H,A:I,C:C")

    // ─── Músculo sin tirón disponible (§11.3 especialización corporal) ─────
    private val BFA = a("BFA", "S:H,B:H,BG:H,SM:I,C:C")
    private val BFB = a("BFB", "U:H,B:H,BG:H,G:I,C:C")
    private val BU = a("BU", "B:H,SM:I,C:C")
    private val BL = a("BL", "S:H,BG:H,U:H,G:I")
    // DEV-r2-01 (docs/WIZARD_PLAN_DEVIATIONS.md): desviación aceptada del
    // calendario literal de r2 §11.3 (BL=[S,H; puente,H; U,H; G,I] en los tres BL
    // de 5 y 6 días): ese literal da ≥18 series de glúteo directo con MRV 16
    // (HARD, §14.3), es insatisfacible y contradice los positivos §17.2 #3/#8.
    // Estado: ACEPTADA por el dueño del producto el 2026-10-02 (registro y anexo
    // fechado en el documento citado); el plan r2 externo sigue sin editarse.
    // Correction mínima E0 de 5/6 días: en dos de los tres BL semanales se
    // sustituye BG:H por B:H. La flexión aprobada no suma volumen de glúteos;
    // quedan 7 slots directos (S/U en los tres BL + un puente): 14 series en
    // principiante. En inter/avanzado son 21 antes de ajustar; el fitter retira
    // SM:I opcional y reduce cinco H permitidos para llegar a 16. El puente
    // restante conserva el mínimo semanal de extensión de cadera; no cambia dosis
    // base ni MRV global.
    private val BL_MRV_ADJUSTED = a("BL", "S:H,B:H,U:H,G:I")

    // ─── Fuerza (§11.3, SBD con barra sin sustitución de competición) ───────
    private val strength: Map<Int, List<NativeDayArchetype>> = mapOf(
        1 to listOf(a("f1a", "S:F,B:F,D:F,R:H")),
        2 to listOf(a("f2a", "S:F,B:F,R:H"), a("f2b", "D:F,B:Fv,S:Fv,C:C")),
        3 to listOf(a("f3a", "S:F,B:Fv,R:H"), a("f3b", "B:F,D:Fv,C:C"), a("f3c", "D:F,S:Fv,B:Fv")),
        4 to listOf(
            a("f4a", "S:F,B:Fv,R:H"),
            a("f4b", "B:F,O:Fv,C:C"),
            a("f4c", "D:F,S:Fv,R:H"),
            a("f4d", "B:Fv,D:Fv,C:C"),
        ),
        5 to listOf(
            a("f5a", "S:F,R:H"),
            a("f5b", "B:F,O:Fv"),
            a("f5c", "D:F,C:C"),
            a("f5d", "S:Fv,R:H"),
            a("f5e", "B:Fv,D:Fv,C:C"),
        ),
        6 to listOf(
            a("f6a", "S:F,R:H"),
            a("f6b", "B:F,A:I"),
            a("f6c", "D:F,C:C"),
            a("f6d", "S:Fv,U:H"),
            a("f6e", "B:Fv,O:Fv"),
            a("f6f", "D:Fv,R:H"),
        ),
    )

    // ─── Fuerza y músculo (§11.3) ──────────────────────────────────────────
    private val powerbuilding: Map<Int, List<NativeDayArchetype>> = mapOf(
        1 to listOf(a("p1a", "S:F,B:F,R:H,D:H")),
        2 to listOf(a("p2a", "S:F,B:Fv,R:H,C:C"), a("p2b", "D:F,B:F,U:H,R:H")),
        3 to listOf(a("p3a", "S:F,B:H,R:H,C:C"), a("p3b", "B:F,D:H,V:H,L:I"), a("p3c", "D:F,S:H,B:H,A:I")),
        4 to listOf(
            a("p4a", "B:F,R:H,O:H,A:I"),
            a("p4b", "S:F,D:H,U:H,C:C"),
            a("p4c", "O:F,B:H,V:H,T:I"),
            a("p4d", "D:F,S:H,U:H,G:I"),
        ),
        5 to listOf(
            a("p5a", "B:F,R:H,O:H,A:I"),
            a("p5b", "S:F,D:H,C:C"),
            a("p5c", "B:H,O:H,L:I,T:I"),
            a("p5d", "R:F,V:H,A:I"),
            a("p5e", "D:F,S:H,U:H,G:I"),
        ),
        6 to listOf(
            a("p6a", "B:F,O:H,T:I"),
            a("p6b", "R:F,V:H,A:I"),
            a("p6c", "S:F,D:H,G:I"),
            a("p6d", "B:H,O:H,L:I"),
            a("p6e", "R:H,V:H,A:I"),
            a("p6f", "D:F,S:H,U:H,C:C"),
        ),
    )

    // ─── Músculo (§11.3 arquetipos por frecuencia) ─────────────────────────
    private val muscleGeneral: Map<Int, List<String>> = mapOf(
        1 to listOf("FA"),
        2 to listOf("FA", "FB"),
        3 to listOf("FA", "FB", "FA"),
        4 to listOf("UA", "LA", "UB", "LB"),
        5 to listOf("UA", "LA", "PU", "PL", "LB"),
        6 to listOf("PU", "PL", "LA", "PU", "PL", "LB"),
    )
    private val muscleNoPull: Map<Int, List<String>> = mapOf(
        1 to listOf("BFA"),
        2 to listOf("BFA", "BFB"),
        3 to listOf("BFA", "BFB", "BFA"),
        4 to listOf("BU", "BL", "BU", "BL"),
        5 to listOf("BL", "BU", "BL_MRV", "BU", "BL_MRV"),
        6 to listOf("BU", "BL", "BU", "BL_MRV", "BU", "BL_MRV"),
    )
    private val muscleArchetypes: Map<String, NativeDayArchetype> = mapOf(
        "FA" to FA, "FB" to FB, "UA" to UA, "UB" to UB, "LA" to LA, "LB" to LB, "PU" to PU, "PL" to PL,
        "BFA" to BFA, "BFB" to BFB, "BU" to BU, "BL" to BL, "BL_MRV" to BL_MRV_ADJUSTED,
    )

    // ─── Atleta completo (§11.4) ───────────────────────────────────────────
    private val XA = a("XA", "PS:P,B:F,S:Fv,R:H,C:C")
    private val XB = a("XB", "PB:P,D:F,R:Fv,B:H,U:H")
    private val XH = a("XH", "S:H,B:H,R:H,D:H,C:C")
    private val X1 = a("X1", "PS:P,B:F,S:Fv,R:H,D:H", NativeCardioRole.AFTER_STRENGTH)
    private val XA_CARDIO = a("XA+", "PS:P,B:F,S:Fv,R:H,C:C", NativeCardioRole.AFTER_STRENGTH)
    private val XB_CARDIO = a("XB+", "PB:P,D:F,R:Fv,B:H,U:H", NativeCardioRole.AFTER_STRENGTH)
    private val XH_CARDIO = a("XH+", "S:H,B:H,R:H,D:H,C:C", NativeCardioRole.AFTER_STRENGTH_BRIEF)
    private val X_CARDIO_ONLY = a("CARDIO", "", NativeCardioRole.ONLY)
    private val X_ACC_U_C = a("ACC-UC", "U:H,C:C", NativeCardioRole.DEDICATED_WITH_ACCESSORIES)
    private val X_ACC_C = a("ACC-C", "C:C", NativeCardioRole.DEDICATED_WITH_ACCESSORIES)
    private val X_D6 = a("D6", "U:H,B:H,R:H,C:C")
    // DEV-r2-01 (docs/WIZARD_PLAN_DEVIATIONS.md), XH_NP_SIX_DAY y X_D6_NO_PULL: el
    // calendario literal de r2 §11.4 (XH=[S,H; B,H; puente,H; U,H; C,C] y día 6
    // [U,H; B,H; puente,H; C,C] sin tirón) suma XA 6 + XH 6 + XB 4 + D6 4 = 20
    // series de glúteo directo en principiante (suelo del fitter 18/19) frente a
    // un MRV HARD de 16, así que no cabe. Estado: ACEPTADA por el dueño del
    // producto el 2026-10-02 (registro y anexo fechado en el documento citado).
    // Atleta corporal de 6 días: XH y D6 reemplazan BG:H con una segunda B:H.
    // Las flexiones aprobadas se fusionan H/H hasta cuatro series. Beginner queda
    // en 16 series de glúteo (MRV real); inter/avanzado: en semanas 3–5 el máximo
    // 21 baja a 16 con H 3→2 en cuatro slots y Fv 2→1 en una práctica S adicional.
    // Potencia, fuerza relativa, hipertrofia y cardio siguen presentes, sin tirón/SBD ficticio.
    private val XH_NP_SIX_DAY = a("XH", "S:H,B:H,B:H,U:H,C:C")
    // DEV-r2-01: día 6 corporal sin tirón (literal r2: [U,H; B,H; puente,H; C,C]).
    private val X_D6_NO_PULL = a("D6b", "U:H,B:H,B:H,C:C")

    // Variantes corporales sin tirón (§11.4): nunca dejan un slot R sin resolver.
    private val XA_NP = a("XA", "PS:P,B:F,S:Fv,BG:H,C:C")
    private val XB_NP = a("XB", "PB:P,U:F,BG:Fv,B:H,G:I")
    private val XH_NP = a("XH", "S:H,B:H,BG:H,U:H,C:C")
    private val X1_NP = a("X1", "PS:P,B:F,S:Fv,BG:H,U:H", NativeCardioRole.AFTER_STRENGTH)
    private val XA_NP_CARDIO = a("XA+", "PS:P,B:F,S:Fv,BG:H,C:C", NativeCardioRole.AFTER_STRENGTH)
    private val XB_NP_CARDIO = a("XB+", "PB:P,U:F,BG:Fv,B:H,G:I", NativeCardioRole.AFTER_STRENGTH)
    private val XH_NP_CARDIO = a("XH+", "S:H,B:H,BG:H,U:H,C:C", NativeCardioRole.AFTER_STRENGTH_BRIEF)

    fun muscle(days: Int, pullAvailable: Boolean): List<NativeDayArchetype> {
        val names = if (pullAvailable) muscleGeneral.getValue(days) else muscleNoPull.getValue(days)
        return names.map { muscleArchetypes.getValue(it) }
    }

    fun strength(days: Int): List<NativeDayArchetype> = strength.getValue(days)

    fun powerbuilding(days: Int): List<NativeDayArchetype> = powerbuilding.getValue(days)

    /**
     * Atleta completo (§11.4): los cuatro componentes en la semana; con 1 día
     * hay UNA exposición global explícitamente de base; con 3 días hay un día
     * dedicado de cardio; ≥2 días = al menos dos exposiciones de potencia y
     * resistencia en días distintos.
     */
    fun athlete(days: Int, pullAvailable: Boolean): List<NativeDayArchetype> = when (days) {
        1 -> listOf(if (pullAvailable) X1 else X1_NP)
        2 -> listOf(if (pullAvailable) XA_CARDIO else XA_NP_CARDIO, if (pullAvailable) XB_CARDIO else XB_NP_CARDIO)
        3 -> listOf(if (pullAvailable) XA else XA_NP, if (pullAvailable) XB else XB_NP, X_ACC_U_C)
        4 -> listOf(
            if (pullAvailable) XA else XA_NP,
            X_ACC_C,
            if (pullAvailable) XB else XB_NP,
            if (pullAvailable) XH_CARDIO else XH_NP_CARDIO,
        )
        5 -> listOf(
            if (pullAvailable) XA else XA_NP,
            X_CARDIO_ONLY,
            if (pullAvailable) XB else XB_NP,
            if (pullAvailable) XH else XH_NP,
            X_ACC_C,
        )
        6 -> listOf(
            if (pullAvailable) XA else XA_NP,
            X_CARDIO_ONLY,
            if (pullAvailable) XH else XH_NP_SIX_DAY,
            if (pullAvailable) XB else XB_NP,
            X_CARDIO_ONLY,
            if (pullAvailable) X_D6 else X_D6_NO_PULL,
        )
        else -> error("Calendario Atleta solo existe para 1..6 días")
    }

    /** Defaults de día de la semana (§11.3): editables en preview. */
    val DEFAULT_WEEKDAYS: Map<Int, List<Int>> = mapOf(
        1 to listOf(1),
        2 to listOf(1, 4),
        3 to listOf(1, 3, 5),
        4 to listOf(1, 2, 4, 5),
        5 to listOf(1, 2, 4, 5, 6),
        6 to listOf(1, 2, 3, 4, 5, 6),
    )
}

/**
 * Defaults de cardio de §11.4: la base es EXACTAMENTE `SESSION_TIME`
 * respondido (44 min → 10; 45 min → 15; ≥60 → 20), antes de restar
 * resistencia/cardio y sin recalcular con el tiempo restante. El fitter tampoco
 * recorta este valor ni los minutos explícitos del usuario: si el día no cabe, el
 * resultado es `TIME_BUDGET` con el mínimo real (DEC-w1-01 en
 * docs/WIZARD_PLAN_DEVIATIONS.md).
 */
object NativeCardioDefaults {
    val OFFERED_MINUTES = listOf(10, 15, 20, 30)
    const val MIN_BLOCK_MINUTES = 10

    fun defaultBlockMinutes(sessionTimeMinutes: Int): Int = when {
        sessionTimeMinutes >= 60 -> 20
        sessionTimeMinutes >= 45 -> 15
        else -> 10
    }

    /**
     * Día dedicado: 20 min si el presupuesto admite el bloque con sus
     * accesorios (~10 min); si solo cabe eso, 10 min (§11.4).
     */
    fun defaultDedicatedMinutes(sessionTimeMinutes: Int): Int =
        if (sessionTimeMinutes >= 35) 20 else MIN_BLOCK_MINUTES

    /** Bloque breve tras XH: 10 min salvo preferencia explícita mayor. */
    fun briefBlockMinutes(explicitMinutes: Int?): Int =
        explicitMinutes?.takeIf { it >= MIN_BLOCK_MINUTES } ?: MIN_BLOCK_MINUTES

    fun details(
        type: com.example.kpkn.data.models.CardioType,
        minutes: Int,
        intensity: CardioIntensity = CardioIntensity.MEDIA,
    ): CardioDetails = CardioDetails(
        type = type,
        intensity = intensity,
        targetDurationSeconds = minutes * 60,
    )

    /**
     * Progresión de §12.4: tras resistencia 10→15 y para de proponer en 15;
     * día dedicado 10→15→20→30. Si el usuario ya eligió 20/30 tras
     * resistencia se conservan (nunca se reducen) y no hay escalón automático.
     */
    fun progression(role: NativeCardioRole, explicitMinutes: Int?): RecipeCardioProgression = when (role) {
        NativeCardioRole.DEDICATED_WITH_ACCESSORIES, NativeCardioRole.ONLY ->
            RecipeCardioProgression(
                offeredDurationsMinutes = OFFERED_MINUTES,
                stopAtMinutes = OFFERED_MINUTES.last(),
                note = "Día dedicado:10→15→20→30 min tras dos exposiciones completas a intensidad conversacional.",
            )
        else -> {
            val chosen = explicitMinutes
            if (chosen != null && chosen > 15) {
                RecipeCardioProgression(
                    offeredDurationsMinutes = listOf(chosen),
                    stopAtMinutes = chosen,
                    note = "Minutos elegidos por ti: se conservan y no se reducen.",
                )
            } else {
                RecipeCardioProgression(
                    offeredDurationsMinutes = listOf(10, 15),
                    stopAtMinutes = 15,
                    note = "Tras resistencia:10→15 min y se detiene ahí; nunca valores fuera de los ofertados.",
                )
            }
        }
    }
}

/** Mínimos declarados por sesión (§14.3 NATIVE_COMPACT / MIXED_CARDIO). */
object NativeMinimumDose {
    /** Suelo de un día STRENGTH: 2 configuraciones distintas / 4 series. */
    fun strength(essentialSlotIds: List<String>): DayMinimumDose =
        DayMinimumDose(minDistinctConfigurations = 2, minResistanceSets = 4, essentialSlotIds = essentialSlotIds)

    /** Cardio + accesorios esenciales: 1 ejercicio/1–2 series es válido. */
    fun cardioAccessory(essentialSlotIds: List<String>): DayMinimumDose =
        DayMinimumDose(minDistinctConfigurations = 0, minResistanceSets = 0, essentialSlotIds = essentialSlotIds)

    /** Día solo cardio: cero slots de resistencia. */
    fun cardioOnly(): DayMinimumDose =
        DayMinimumDose(minDistinctConfigurations = 0, minResistanceSets = 0, essentialSlotIds = emptyList())
}

/**
 * Semana materializada de un plan propio: seis semanas (§12.1) con la semana 6
 * como descarga KPKN (`blockIndex=1`, `blockGoal=DELOAD`,
 * `kind=WeekExecutionKind.DELOAD`) y RIR3/RIR2 por nivel en las semanas 1–5.
 */
object NativeWeekBuilder {
    const val WEEKS = 6
    const val DELOAD_WEEK = 6
    const val BASE_BLOCK_NAME = "Acumulación"
    const val DELOAD_BLOCK_NAME = "Descarga KPKN"

    /** RIR de las semanas 1–5 por nivel (§12.1): principiante RIR 3 siempre. */
    fun trainingRir(level: NativeDoseLevel, weekNumber: Int): Int = when (level) {
        NativeDoseLevel.BEGINNER -> 3
        else -> if (weekNumber <= 1) 3 else 2
    }
}

/**
 * Reparto semanal de progresión de §12.4 expuesto por los planes propios.
 * La identidad es `configurationId + loadMode + unitMode + lado + slotPurpose`
 * con la referencia de receta; nunca modifica la regla de otros protocolos ni
 * el algoritmo AUGE.
 */
data class NativeProgressionContracts(
    val strategy: NativeProgressionStrategy,
    val exposuresBeforeProposal: Int,
) {
    fun shouldProposeLoadIncrement(
        completedExposures: Int,
        allSetsAtTopOfRange: Boolean,
        rirAtTargetOrAbove: Boolean,
    ): Boolean = allSetsAtTopOfRange && rirAtTargetOrAbove &&
        completedExposures >= exposuresBeforeProposal

    /**
     * Doble progresión (§12.4): mantener la carga y subir reps dentro del
     * rango; tras [exposuresBeforeProposal] exposiciones completas con TODAS
     * las series en el tope del rango y RIR objetivo o mayor, proponer el
     * menor incremento conocido del equipo. Ausencia de registro no es fallo
     * ni éxito: devuelve [NativeProgressionOutcome.NO_RECORD].
     */
    fun exposureOutcome(
        completedExposures: Int?,
        allSetsAtTopOfRange: Boolean,
        rirAtTargetOrAbove: Boolean,
    ): NativeProgressionOutcome = when {
        completedExposures == null -> NativeProgressionOutcome.NO_RECORD
        shouldProposeLoadIncrement(completedExposures, allSetsAtTopOfRange, rirAtTargetOrAbove) ->
            NativeProgressionOutcome.PROPOSE_INCREMENT
        completedExposures >= exposuresBeforeProposal ->
            NativeProgressionOutcome.KEEP_OR_REGRESS
        else -> NativeProgressionOutcome.KEEP
    }

    /**
     * Progresión corporal (§12.4): al tope de dos exposiciones se sugiere la
     * siguiente variante técnica curada más difícil limpiando referencias; si
     * no existe se conserva y se explica el límite (nunca lastre automático).
     */
    fun bodyweightNextVariant(hasHarderCuratedVariant: Boolean): Boolean = hasHarderCuratedVariant
}

enum class NativeProgressionOutcome { NO_RECORD, KEEP, PROPOSE_INCREMENT, KEEP_OR_REGRESS }

/** Escalones de cardio de §12.4: solo valores ofertados 10/15/20/30. */
object NativeCardioEscalation {
    const val AFTER_STRENGTH_STOP = 15
    const val DEDICATED_STOP = 30

    /**
     * Siguiente escalón propuesto. `userChosenMinutes` = valor elegido ya por
     * el usuario (20/30 tras resistencia se conservan, sin reducir ni proponer
     * incremento). Devuelve null cuando no hay escalón (o no cabepresión).
     */
    fun nextOffered(
        currentMinutes: Int,
        dedicatedDay: Boolean,
        userChosenMinutes: Int?,
        budgetAllows: Boolean = true,
    ): Int? {
        if (userChosenMinutes != null && userChosenMinutes >= 20 && !dedicatedDay) return null
        val ladder = NativeCardioDefaults.OFFERED_MINUTES
        val next = ladder.firstOrNull { it > currentMinutes } ?: return null
        val stop = if (dedicatedDay) DEDICATED_STOP else AFTER_STRENGTH_STOP
        if (next > stop) return null
        return next.takeIf { budgetAllows }
    }
}
