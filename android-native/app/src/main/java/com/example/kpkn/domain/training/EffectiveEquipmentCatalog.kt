package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory

/**
 * Vocabulario curado del subpanel «¿Qué tienes disponible?» (§13.2) y mapa
 * aparato/soporte → configuraciones del catálogo.
 *
 * Regla STOP de este módulo: **solo ids verificados** en los JSON de familias
 * de `catalog/exercises/v2/source/families` y en los dos assets
 * distribuidos (`app/src/main/assets` y `app/src/main/resources`, revisión
 * `v2-approved-2026-08-12-a`). Una clave curada sin id verificable queda
 * [EffectiveEquipmentKey.machineConfigurations] vacía y se reporta; nunca se
 * inventa un id ni se deriva una equivalencia entre aparatos distintos
 * (§13.2: «sin equivalencia automática»).
 *
 * Este archivo es la ÚNICA fuente de verdad compartida entre el resolver
 * (`TrainingOptions.resolveEffectiveEquipment`), la guardia de recetas fijas y
 * el filtro real del motor, para que ninguna ruta pueda divergir.
 */

// ─── Claves estables del panel (§13.2) ──────────────────────────────────────

/** Claves estables del subpanel de aparatos/soportes; la UI usa sus etiquetas. */
internal object EquipmentKeys {
    // Piernas (aparatos)
    const val LEG_PRESS = "leg_press"
    const val HACK_SQUAT = "hack_squat"
    const val LEG_EXTENSION = "leg_extension"
    const val LEG_CURL_LYING = "leg_curl_lying"
    const val LEG_CURL_SEATED = "leg_curl_seated"
    const val CALF_STANDING = "calf_standing"
    const val CALF_SEATED = "calf_seated"
    const val CALF_DONKEY = "calf_donkey"
    // Torso (aparatos)
    const val CHEST_PRESS_CONVERGING = "chest_press_converging"
    const val CABLE_HIGH_LOW = "cable_high_low"
    const val DUAL_CABLE = "dual_cable"
    const val ROPE_ATTACHMENT = "rope_attachment"
    // Bancos y rack (soportes)
    const val BENCH_FLAT = "bench_flat"
    const val BENCH_ADJUSTABLE = "bench_adjustable"
    const val SQUAT_RACK = "squat_rack"
    const val PREACHER_BENCH = "preacher_bench"
    // Tirón y apoyos (soportes)
    const val PULLUP_BAR = "pullup_bar"
    const val DIP_BARS = "dip_bars"
    const val LOW_BAR_SUPPORT = "low_bar_support"
    const val EZ_BAR = "ez_bar"
}

/** Grupos resumidos del panel (§13.2): bancos/rack, tirón/apoyos, piernas, torso. */
internal object EquipmentKeyGroups {
    const val LEGS = "piernas"
    const val TORSO = "torso"
    const val BENCH_RACK = "bancos/rack"
    const val PULL_SUPPORT = "tirón/apoyos"
}

/**
 * Una clave curada del panel: etiqueta humana para la UI, categoría que la
 * delimita (§13.1 regla 1; `null` = sin categoría propia, solo con una
 * respuesta confirmada no vacía), configuraciones exactas que habilita al
 * estar PRESENT (maquinaria) y tokens materia que acredita al estar PRESENT
 * (soportes).
 */
internal data class EffectiveEquipmentKey(
    val key: String,
    val label: String,
    val group: String,
    val category: EquipmentCategory?,
    val machineConfigurations: Set<String> = emptySet(),
    val attestedTokens: Set<String> = emptySet(),
)

// ─── Vocabulario de requisitos de soporte (§13.2, «Actualizar supportDependencyFor») ───

internal const val REQUIREMENT_BENCH = "bench"
internal const val REQUIREMENT_BENCH_INCLINE = "bench_incline"
internal const val REQUIREMENT_RACK = "rack"
internal const val REQUIREMENT_PULL_UP_BAR = "pull_up_bar"
internal const val REQUIREMENT_DIP_BARS = "dip_bars"
internal const val REQUIREMENT_LOW_BAR_SUPPORT = "low_bar_support"
internal const val REQUIREMENT_BALL = "ball"
internal const val REQUIREMENT_SUPPORT = "support"
internal const val REQUIREMENT_NORDIC_ANCHOR = "nordic_anchor"

/**
 * Aparatos propios de un gimnasio que piden dos configuraciones con disco (paquete E2): el banco declinado del crunch
 * lastrado y el banco de hiperextensión a 45°. Los acredita el símbolo «Banco» solo con gimnasio entre los lugares
 * ([SYMBOL_EQUIPMENT_KEYS]); no son del subpanel §13.2 ni de [KNOWN_REQUIREMENTS], así que una receta que los pida sin
 * ellos los ve ausentes, sin una pregunta que nunca los acreditaría.
 */
internal const val REQUIREMENT_DECLINE_BENCH = "decline_bench"
internal const val REQUIREMENT_HYPEREXTENSION_BENCH = "hyperextension_bench"

/**
 * Requisitos que el vocabulario del panel puede acreditar. La evidencia de
 * cada uno viaja en `EffectiveEquipmentResult.requirements` para que la UI
 * distinga «ausente» (negado o categoría sin esos ítems) de «desconocido»
 * (falta confirmar) — §15.2 `APPARATUS_ABSENT` / `APPARATUS_UNKNOWN`.
 */
internal val KNOWN_REQUIREMENTS: Set<String> = setOf(
    REQUIREMENT_BENCH,
    REQUIREMENT_BENCH_INCLINE,
    REQUIREMENT_RACK,
    REQUIREMENT_PULL_UP_BAR,
    REQUIREMENT_DIP_BARS,
    REQUIREMENT_LOW_BAR_SUPPORT,
    REQUIREMENT_BALL,
    REQUIREMENT_SUPPORT,
    REQUIREMENT_NORDIC_ANCHOR,
)

/**
 * Clase de soporte que el chip legacy `support` («Soportes, rack o banco»)
 * atestigua en las rutas SIN disponibilidad nueva (§13.1 regla 4: compatibilidad
 * legacy para LEER programas previos — AC-C3). El vocabulario legacy no tiene
 * chips de banco ni de rack (`SetupInventoryControls.SUPPORT_EQUIPMENT_ALIASES`
 * los colapsa a `support`), así que sin este paraguas una receta antigua con
 * banca quedaría inalcanzable para perfiles que sí declararon sus soportes.
 *
 * En la ruta de disponibilidad NO existe este paraguas: ahí cada clave atestigua
 * lo suyo con precisión, que es lo que permite que `bench_flat = ABSENT` bloquee
 * realmente (AC-C2).
 */
internal val LEGACY_SUPPORT_ATTESTED_REQUIREMENTS: Set<String> = setOf(
    REQUIREMENT_BENCH,
    REQUIREMENT_BENCH_INCLINE,
    REQUIREMENT_RACK,
    REQUIREMENT_LOW_BAR_SUPPORT,
    REQUIREMENT_DIP_BARS,
    REQUIREMENT_NORDIC_ANCHOR,
)

/**
 * Requisito que una configuración expresa fuera de su `equipmentId` (§13.2).
 *
 * Paquete A · B4 (DEC-w2-04, parte 1; `docs/WIZARD_PLAN_DEVIATIONS.md`):
 * - El rack sigue a la LIFT, no al implemento: las sentadillas con barra que se descargan de un
 *   soporte (trasera alta y baja, frontal, con pausa, a cajón, Anderson y con barra de seguridad)
 *   lo exigen; el peso muerto, el press militar, la Smith y las variantes de kettlebell o
 *   mancuerna NO (la Smith trae su propio soporte y las demás no se descargan de un rack).
 * - La banca por NOMBRE: las variantes de barra de la banca plana (con pausa, con agarre cerrado,
 *   Spoto y con cadenas) exigen banco y rack igual que la banca de barra; el prefijo `bench_press__`
 *   no las cubría.
 * - Huecos de soporte: el rack chin, las dominadas escapulares, la suspensión en barra y el jalón con
 *   banda dependen de una barra (baja estable o de dominadas), y el hip thrust con banda y el curl
 *   inclinado dependen de un banco.
 *
 * Lote BW-1 (peso corporal): la dominada negativa pide la barra de dominadas; la pica con los pies elevados, el
 * step-up y la sentadilla búlgara sin carga piden un apoyo elevado ([ELEVATED_SUPPORT_CONFIGURATIONS]). El resto de las
 * altas (dead bug, bird dog, plancha lateral, hollow body, sentadilla en pared, pica plana, flexiones diamante y
 * arquero y las zancadas, sumo, sissy, rumano a una pierna y buenos días sin carga) no necesita ningún soporte.
 */
internal fun supportRequirementsFor(configurationId: String): Set<String> = when {
    // Banca: exige banco; la variante de barra exige además rack; las
    // inclinadas/declinadas exigen un banco regulable. `floor_press__*` queda
    // FUERA deliberadamente (se hace en suelo, sin banco).
    configurationId.startsWith(INCLINE_BENCH_PRESS_PREFIX) ||
        configurationId.startsWith(DECLINE_BENCH_PRESS_PREFIX) ->
        buildSet {
            add(REQUIREMENT_BENCH)
            add(REQUIREMENT_BENCH_INCLINE)
            if (configurationId.endsWith(BARBELL_SUFFIX)) add(REQUIREMENT_RACK)
        }
    FLAT_BENCH_PRESS_PREFIXES.any { configurationId.startsWith(it) } ||
        configurationId in BARBELL_BENCH_VARIANT_CONFIGURATIONS -> buildSet {
        add(REQUIREMENT_BENCH)
        if (configurationId.endsWith(BARBELL_SUFFIX) || configurationId in BARBELL_BENCH_VARIANT_CONFIGURATIONS) {
            add(REQUIREMENT_RACK)
        }
    }
    // Curl inclinado: el banco regulable lo acredita (y acredita también el plano).
    INCLINE_BICEPS_CURL_CONFIGURATION == configurationId ->
        setOf(REQUIREMENT_BENCH, REQUIREMENT_BENCH_INCLINE)
    // Sentadillas de barra descargadas de un soporte.
    configurationId in RACK_SQUAT_CONFIGURATIONS -> setOf(REQUIREMENT_RACK)
    // Hip thrust con banda: la espalda alta se apoya en un banco.
    configurationId.startsWith(HIP_THRUST_PREFIX) && configurationId.endsWith(BAND_SUFFIX) ->
        setOf(REQUIREMENT_BENCH)
    // Jalón con banda: la banda se ancla arriba, en la barra de dominadas.
    configurationId.startsWith(LAT_PULLDOWN_PREFIX) && configurationId.endsWith(BAND_SUFFIX) ->
        setOf(REQUIREMENT_PULL_UP_BAR)
    // Fondos y dominadas: cada uno con SU apoyo (§13.2: no derivar uno del otro).
    "triceps_fondos_entre_bancos__default" == configurationId -> setOf(REQUIREMENT_BENCH)
    "tren_superior_fondos__default" == configurationId -> setOf(REQUIREMENT_DIP_BARS)
    configurationId.startsWith(PULL_UP_PREFIX) ||
        configurationId.startsWith(NEGATIVE_PULL_UP_PREFIX) ||
        configurationId.startsWith(SCAPULAR_PULL_UP_PREFIX) ||
        configurationId.startsWith(DEAD_HANG_PREFIX) -> setOf(REQUIREMENT_PULL_UP_BAR)
    // Apoyos concretos
    "back_remo_invertido__default" == configurationId ||
        configurationId.startsWith(RACK_CHIN_PREFIX) -> setOf(REQUIREMENT_LOW_BAR_SUPPORT)
    "curl_isquios_con_balon__default" == configurationId -> setOf(REQUIREMENT_BALL)
    "hams_curl_nordic_peso_corporal__default" == configurationId -> setOf(REQUIREMENT_NORDIC_ANCHOR)
    "push_up__feet_elevated" == configurationId -> setOf(REQUIREMENT_SUPPORT)
    // Lote BW-1 (peso corporal): sin un apoyo elevado estas configuraciones no se pueden hacer, y como su implemento es
    // `bodyweight` (siempre acreditado) nada más las bloquearía.
    configurationId in ELEVATED_SUPPORT_CONFIGURATIONS -> setOf(REQUIREMENT_SUPPORT)
    // Discos con un aparato de gimnasio que el catálogo no declara: banco declinado e hiperextensión a 45° (paquete E2).
    "core_crunch_banco_declinado_lastrado_disco__default" == configurationId -> setOf(REQUIREMENT_DECLINE_BENCH)
    "glutes_hiperextension_45__plate" == configurationId -> setOf(REQUIREMENT_HYPEREXTENSION_BENCH)
    else -> emptySet()
}

private const val BENCH_PRESS_PREFIX = "bench_press__"
private const val PAUSED_BENCH_PRESS_PREFIX = "paused_bench_press__"
private const val CLOSE_GRIP_BENCH_PRESS_PREFIX = "close_grip_bench_press__"
private const val INCLINE_BENCH_PRESS_PREFIX = "incline_bench_press__"
private const val DECLINE_BENCH_PRESS_PREFIX = "decline_bench_press__"
private const val PULL_UP_PREFIX = "pull_up__"
private const val NEGATIVE_PULL_UP_PREFIX = "negative_pull_up__"
private const val SCAPULAR_PULL_UP_PREFIX = "back_dominadas_escapulares__"
private const val DEAD_HANG_PREFIX = "forearms_suspension_isometrica_barra_fija__"
private const val RACK_CHIN_PREFIX = "rack_chin__"
private const val HIP_THRUST_PREFIX = "hip_thrust__"
private const val LAT_PULLDOWN_PREFIX = "lat_pulldown__"
private const val BARBELL_SUFFIX = "__barbell"
private const val BAND_SUFFIX = "__band"
private const val INCLINE_BICEPS_CURL_CONFIGURATION = "incline_biceps_curl__dumbbells"

/** Banca plana por nombre de definición: la de barra (`__barbell`) exige además el rack. */
private val FLAT_BENCH_PRESS_PREFIXES = listOf(
    BENCH_PRESS_PREFIX,
    PAUSED_BENCH_PRESS_PREFIX,
    CLOSE_GRIP_BENCH_PRESS_PREFIX,
)

/**
 * Variantes de la banca con barra cuyo id no lleva el sufijo `__barbell` (definiciones
 * `tren_superior_*`, sufijo `__default`): Spoto y con cadenas. Banco y rack como la banca de barra.
 */
private val BARBELL_BENCH_VARIANT_CONFIGURATIONS = setOf(
    "tren_superior_press_spoto_barra__default",
    "tren_superior_press_banca_cadenas__default",
)

/**
 * Configuraciones de peso corporal del lote BW-1 que necesitan un apoyo elevado estable (banco, cajón, escalón o sofá):
 * la pica con los pies elevados, el step-up y la sentadilla búlgara sin carga. «Apoyo elevado» ([REQUIREMENT_SUPPORT]) es el
 * mismo requisito que ya pide la flexión con los pies elevados; lo acredita cualquier símbolo de soporte del paso de
 * material, y el cajón ([SymbolEquipmentKeys.PLYO_BOX], en [SYMBOL_EQUIPMENT_KEYS]) también.
 */
private val ELEVATED_SUPPORT_CONFIGURATIONS = setOf(
    "pike_push_up__feet_elevated",
    "step_up__bodyweight",
    "bulgarian_split_squat__bodyweight",
)

/**
 * Sentadillas con barra que se cargan desde un rack (paquete A · B4). La Smith, la kettlebell y la
 * mancuerna no están aquí a propósito; tampoco el peso muerto ni el press militar.
 */
private val RACK_SQUAT_CONFIGURATIONS = setOf(
    "high_bar_back_squat__barbell",
    "low_bar_back_squat__barbell",
    "front_squat__barbell",
    "paused_back_squat__barbell",
    "high_bar_back_squat__safety_bar",
    "quads_sentadilla_cajon__default",
    "quads_sentadilla_anderson__default",
)

// ─── Mapeo curado clave → configuraciones (§13.2 «Habilita / no habilita») ───

private val LEG_PRESS_CONFIGURATIONS = setOf(
    "quads_prensa_piernas__bilateral",
    "quads_prensa_piernas__unilateral",
    // §13.2: la clave prensa habilita también el gemelo en ESA misma prensa.
    "calf_raise__bilateral__leg_press_machine",
)

/** Hack: la hack curada y sus variantes en la MISMA máquina; nunca la Smith. */
private val HACK_SQUAT_CONFIGURATIONS = setOf(
    "quads_sentadilla_hack__machine",
    "quads_sentadilla_hack_invertida_maquina__default",
    "quads_zancada_inversa_maquina_hack__default",
)

private val LEG_EXTENSION_CONFIGURATIONS = setOf(
    "quads_extension_cuadriceps__machine__bilateral",
    "quads_extension_cuadriceps__machine__unilateral",
)

/**
 * Gemelo de pie (§13.2 `calf_standing`): la máquina de gemelos de pie, bilateral
 * y unilateral. Es un aparato DISTINTO del sentado y del donkey: sin
 * equivalencia automática entre las tres claves.
 */
private val CALF_STANDING_CONFIGURATIONS = setOf(
    "calf_raise__bilateral__machine",
    "calf_raise__unilateral__machine",
)

/** Gemelo sentado (§13.2 `calf_seated`): máquina con carga sobre los muslos (soleo). */
private val CALF_SEATED_CONFIGURATIONS = setOf(
    "calf_raise__bilateral__seated_machine",
)

/** Gemelo donkey (§13.2 `calf_donkey`): máquina con tronco inclinado y carga en la espalda. */
private val CALF_DONKEY_CONFIGURATIONS = setOf(
    "calf_raise__bilateral__donkey_machine",
)

/**
 * Banco predicador (§13.2 `preacher_bench`): variantes con los brazos apoyados
 * en el banco predicador. La variante en máquina tiene su propio aparato y no
 * depende de este banco.
 */
private val PREACHER_BENCH_CONFIGURATIONS = setOf(
    "preacher_curl__barbell",
    "preacher_curl__ez_bar",
    "preacher_curl__dumbbells",
)

/** Barra EZ (§13.2 `ez_bar`): todas las configuraciones publicadas con implemento `__ez_bar`. */
private val EZ_BAR_CONFIGURATIONS = setOf(
    "preacher_curl__ez_bar",
    "standing_biceps_curl__ez_bar",
    "overhead_triceps__ez_bar",
    "z_press__ez_bar",
    "california_press__ez_bar",
    "jm_press__ez_bar",
    "triceps_press_frances__ez_bar",
    "forearms_curl_muneca_sentado__ez_bar",
    "forearms_curl_muneca_inverso_sentado__ez_bar",
)

private val LEG_CURL_LYING_CONFIGURATIONS = setOf(
    "lying_leg_curl__bilateral__machine",
    "lying_leg_curl__unilateral__machine",
)

private val LEG_CURL_SEATED_CONFIGURATIONS = setOf(
    "seated_leg_curl__bilateral__machine",
    "seated_leg_curl__unilateral__machine",
)

/**
 * Convergente: solo el plano convergente verificado. La inclinada es otro
 * aparato distinto, así que no se habilita por esta clave.
 */
private val CHEST_PRESS_CONVERGING_CONFIGURATIONS = setOf(
    "tren_superior_press_pecho_maquina_convergente__default",
)

/**
 * Polea alta y baja: jalón, remo y extensiones con montaje curado (no doble polea ni cuerda).
 * Paquete A · B4: incluye el jalón con agarre cerrado (alta M4 del catálogo), la misma polea alta
 * con otro agarre; sin él, PHAT dejaría de ser viable con polea al migrar `lat-close` a esa configuración.
 */
private val CABLE_HIGH_LOW_CONFIGURATIONS = setOf(
    "lat_pulldown__bilateral__cable",
    "lat_pulldown__unilateral__cable",
    "close_grip_lat_pulldown__cable",
    "conventional_row__cable",
    "triceps_pushdown__bilateral__cable",
    "triceps_pushdown__unilateral__cable",
    "overhead_triceps__cable",
)

/**
 * Panel completo §13.2 (20 claves). Las claves sin `machineConfigurations`
 * ni `attestedTokens` documentan un aparato real cuyo id curado NO se pudo
 * verificar en la fuente (ver informe del paquete): se muestran y se pueden
 * confirmar, pero no habilitan nada todavía — nunca se inventa el id.
 */
internal val EFFECTIVE_EQUIPMENT_KEYS: List<EffectiveEquipmentKey> = listOf(
    // Piernas
    EffectiveEquipmentKey(
        key = EquipmentKeys.LEG_PRESS,
        label = "Prensa de piernas",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = LEG_PRESS_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.HACK_SQUAT,
        label = "Hack squat",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = HACK_SQUAT_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.LEG_EXTENSION,
        label = "Extensión de cuádriceps",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = LEG_EXTENSION_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.LEG_CURL_LYING,
        label = "Curl femoral tumbado",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = LEG_CURL_LYING_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.LEG_CURL_SEATED,
        label = "Curl femoral sentado",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = LEG_CURL_SEATED_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.CALF_STANDING,
        label = "Gemelo de pie",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = CALF_STANDING_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.CALF_SEATED,
        label = "Gemelo sentado",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = CALF_SEATED_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.CALF_DONKEY,
        label = "Gemelo donkey",
        group = EquipmentKeyGroups.LEGS,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = CALF_DONKEY_CONFIGURATIONS,
    ),
    // Torso
    EffectiveEquipmentKey(
        key = EquipmentKeys.CHEST_PRESS_CONVERGING,
        label = "Press de pecho convergente",
        group = EquipmentKeyGroups.TORSO,
        category = EquipmentCategory.MACHINES,
        machineConfigurations = CHEST_PRESS_CONVERGING_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.CABLE_HIGH_LOW,
        label = "Polea alta y baja",
        group = EquipmentKeyGroups.TORSO,
        category = EquipmentCategory.CABLE,
        machineConfigurations = CABLE_HIGH_LOW_CONFIGURATIONS,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.DUAL_CABLE,
        label = "Doble polea",
        group = EquipmentKeyGroups.TORSO,
        category = EquipmentCategory.CABLE,
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.ROPE_ATTACHMENT,
        label = "Cuerda",
        group = EquipmentKeyGroups.TORSO,
        category = EquipmentCategory.CABLE,
    ),
    // Bancos y rack
    EffectiveEquipmentKey(
        key = EquipmentKeys.BENCH_FLAT,
        label = "Banco plano",
        group = EquipmentKeyGroups.BENCH_RACK,
        category = EquipmentCategory.SUPPORT,
        attestedTokens = setOf(REQUIREMENT_BENCH),
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.BENCH_ADJUSTABLE,
        label = "Banco regulable",
        group = EquipmentKeyGroups.BENCH_RACK,
        category = EquipmentCategory.SUPPORT,
        // Regulable habilita plano + inclinado; el plano NO habilita inclinado.
        attestedTokens = setOf(REQUIREMENT_BENCH, REQUIREMENT_BENCH_INCLINE),
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.SQUAT_RACK,
        label = "Rack de sentadilla",
        group = EquipmentKeyGroups.BENCH_RACK,
        category = EquipmentCategory.SUPPORT,
        attestedTokens = setOf(REQUIREMENT_RACK),
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.PREACHER_BENCH,
        label = "Banco predicador",
        group = EquipmentKeyGroups.BENCH_RACK,
        category = EquipmentCategory.SUPPORT,
        machineConfigurations = PREACHER_BENCH_CONFIGURATIONS,
    ),
    // Tirón y apoyos
    EffectiveEquipmentKey(
        key = EquipmentKeys.PULLUP_BAR,
        label = "Barra de dominadas",
        group = EquipmentKeyGroups.PULL_SUPPORT,
        category = EquipmentCategory.PULL_UP_BAR,
        attestedTokens = setOf(REQUIREMENT_PULL_UP_BAR),
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.DIP_BARS,
        label = "Paralelas",
        group = EquipmentKeyGroups.PULL_SUPPORT,
        category = EquipmentCategory.SUPPORT,
        attestedTokens = setOf(REQUIREMENT_DIP_BARS),
    ),
    EffectiveEquipmentKey(
        key = EquipmentKeys.LOW_BAR_SUPPORT,
        label = "Barra baja estable",
        group = EquipmentKeyGroups.PULL_SUPPORT,
        category = EquipmentCategory.SUPPORT,
        attestedTokens = setOf(REQUIREMENT_LOW_BAR_SUPPORT),
    ),
    EffectiveEquipmentKey(
        // Sin categoría propia en §13.1: se honra solo con una respuesta
        // confirmada (categorías no vacías); habilita sus configuraciones `__ez_bar`.
        key = EquipmentKeys.EZ_BAR,
        label = "Barra EZ",
        group = EquipmentKeyGroups.PULL_SUPPORT,
        category = null,
        machineConfigurations = EZ_BAR_CONFIGURATIONS,
        attestedTokens = setOf("ez_bar"),
    ),
)

/**
 * `dual_cable` y `rope_attachment` (§13.2) quedan SIN configuraciones: ninguna
 * configuración publicada declara esos requisitos en `requiredEquipment`. El
 * pushdown de cuerda de PHAT se resuelve con la configuración canónica de
 * polea alta (`triceps_pushdown__bilateral__cable`), cuya copia editorial ya
 * describe el empuje con la cuerda; la estación se acredita con `cable_high_low`.
 * Regla STOP: no se les asigna un id que la fuente no declara.
 */

/** Claves curadas cuya categoría es [category] (para delimitar por categoría). */
internal fun equipmentKeysOf(category: EquipmentCategory): List<EffectiveEquipmentKey> =
    EFFECTIVE_EQUIPMENT_KEYS.filter { it.category == category }

/** Configuraciones exactas que acredita una clave PRESENT. */
internal fun curatedConfigurationsOf(key: String): Set<String> =
    EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { it.key == key }?.machineConfigurations.orEmpty()

/** Tokens materia que acredita una clave PRESENT. */
internal fun attestedTokensOf(key: String): Set<String> =
    EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { it.key == key }?.attestedTokens.orEmpty()

/**
 * Paquete A · B3: true cuando la persona declaró (Sí o No) la presencia de ALGUNA máquina o polea
 * curada, es decir, una llave de [EFFECTIVE_EQUIPMENT_KEYS] cuya categoría es `MACHINES` o `CABLE`.
 * Los soportes (banco, rack, paralelas…), la barra de dominadas y la bicicleta exterior
 * (`outdoor_bike`, que ni siquiera es una llave del panel) NO cuentan.
 *
 * Es el criterio del modo «configuración exacta» del generador: con una máquina concreta declarada,
 * solo se admiten las máquinas cuyo token `machine_config:<id>` esté acreditado; con las máquinas
 * solo por categoría (aunque haya soportes confirmados), el plan propio puede usar las variantes de
 * máquina aprobadas sin afirmar una configuración. Ver DEV-r2-06 en `docs/WIZARD_PLAN_DEVIATIONS.md`.
 *
 * Con [EquipmentAvailability.machinesAsCategory] es siempre false: «Máquinas» como una sala de máquinas es una declaración
 * por categoría aunque sus llaves consten `PRESENT` (esas llaves solo sirven para que las recetas de autor vean sus
 * tokens `machine_config:`).
 */
internal fun EquipmentAvailability.hasExplicitMachinePresence(): Boolean =
    !machinesAsCategory && EFFECTIVE_EQUIPMENT_KEYS.any { spec ->
        (spec.category == EquipmentCategory.MACHINES || spec.category == EquipmentCategory.CABLE) &&
            presenceOf(spec.key) != ApparatusPresence.UNKNOWN
    }

/** true si ALGUNA clave con ausencia explícita es dueña de esa configuración. */
internal fun configurationDeniedByAbsentKey(
    configurationId: String,
    availability: EquipmentAvailability,
): Boolean = EFFECTIVE_EQUIPMENT_KEYS.any { spec ->
    availability.presenceOf(spec.key) == ApparatusPresence.ABSENT &&
        configurationId in spec.machineConfigurations
}

// ─── Llaves de símbolo que el subpanel no pinta (paquete E) ─────────────────────

/**
 * Llaves que viajan desde `EquipmentSymbols` (el paso «¿Con qué material entrenas?») y que el subpanel §13.2 no pinta:
 * anillas, cajón, cuerda de saltar y los extras habituales de un gimnasio sin símbolo propio. Es la fuente única de sus
 * nombres: `EquipmentSymbols` los reexporta y el resolutor las acredita con [SYMBOL_EQUIPMENT_KEYS].
 */
internal object SymbolEquipmentKeys {
    const val RINGS = "rings"
    const val PLYO_BOX = "plyo_box"
    const val JUMP_ROPE = "jump_rope"
    const val PLATE = "plate"
    const val HEX_BAR = "hex_bar"
    const val T_BAR = "t_bar"
    const val GHD = "ghd"
    const val AB_WHEEL = "ab_wheel"
    const val DECLINE_BENCH = REQUIREMENT_DECLINE_BENCH
    const val HYPEREXTENSION_BENCH = REQUIREMENT_HYPEREXTENSION_BENCH
}

/**
 * Una llave de símbolo: la categoría que la delimita (debe constar en la respuesta confirmada, como en
 * [EffectiveEquipmentKey]) y los tokens que acredita al estar `PRESENT`. No habilita configuraciones por máquina concreta
 * ni forma parte del subpanel: por eso vive en una lista aparte de [EFFECTIVE_EQUIPMENT_KEYS].
 */
internal data class SymbolEquipmentKey(
    val key: String,
    val category: EquipmentCategory,
    val attestedTokens: Set<String>,
)

/**
 * Acreditación de las llaves de símbolo en `resolveWithAvailability` (una sola implementación para el planificador, los
 * planes de autor y el generador de rutinas). Regla STOP: cada token es un `equipmentId` que el catálogo ya declara, un
 * requisito de [supportRequirementsFor] o un token que piden las reservas del generador; no se inventa ninguno.
 *
 * Quedan SIN acreditar a propósito (son raros): `safety_bar`, `h_bar`, `sliders` y `wrist_roller`.
 */
internal val SYMBOL_EQUIPMENT_KEYS: List<SymbolEquipmentKey> = listOf(
    // Anillas o TRX: el catálogo llama `trx` al implemento de suspensión y las reservas del generador piden `rings`.
    SymbolEquipmentKey(SymbolEquipmentKeys.RINGS, EquipmentCategory.SUPPORT, setOf("trx", "rings")),
    // Cajón o step: además del token propio de las reservas del generador, es un apoyo elevado (`support`), el requisito
    // de la pica con los pies elevados, el step-up y la búlgara sin carga (lote BW-1). Las anillas NO lo son.
    SymbolEquipmentKey(
        SymbolEquipmentKeys.PLYO_BOX,
        EquipmentCategory.SUPPORT,
        setOf(SymbolEquipmentKeys.PLYO_BOX, REQUIREMENT_SUPPORT),
    ),
    SymbolEquipmentKey(SymbolEquipmentKeys.JUMP_ROPE, EquipmentCategory.CARDIO, setOf(SymbolEquipmentKeys.JUMP_ROPE)),
    // Barra baja de un parque: la barra de dominadas de un parque suele traerla (`EquipmentSymbols` escribe la llave con
    // `PUBLIC` entre los lugares y la barra de dominadas elegida). La categoría que consta ahí es la de la barra de
    // dominadas, no la de soportes, así que esta entrada duplica la llave del subpanel con su otra puerta.
    SymbolEquipmentKey(EquipmentKeys.LOW_BAR_SUPPORT, EquipmentCategory.PULL_UP_BAR, setOf(REQUIREMENT_LOW_BAR_SUPPORT)),
    // Extras habituales de un gimnasio, con su símbolo madre elegido: discos, barra hexagonal y barra T con la barra;
    // GHD y rueda abdominal con las máquinas.
    SymbolEquipmentKey(SymbolEquipmentKeys.PLATE, EquipmentCategory.BARBELL, setOf(SymbolEquipmentKeys.PLATE)),
    SymbolEquipmentKey(SymbolEquipmentKeys.HEX_BAR, EquipmentCategory.BARBELL, setOf(SymbolEquipmentKeys.HEX_BAR)),
    SymbolEquipmentKey(SymbolEquipmentKeys.T_BAR, EquipmentCategory.BARBELL, setOf(SymbolEquipmentKeys.T_BAR)),
    SymbolEquipmentKey(SymbolEquipmentKeys.GHD, EquipmentCategory.MACHINES, setOf(SymbolEquipmentKeys.GHD)),
    SymbolEquipmentKey(SymbolEquipmentKeys.AB_WHEEL, EquipmentCategory.MACHINES, setOf(SymbolEquipmentKeys.AB_WHEEL)),
    // Aparatos de gimnasio con banco (paquete E2): el banco declinado del crunch con disco y el de hiperextensión a 45°.
    // Con el símbolo «Banco» y gimnasio entre los lugares; en casa un banco no los trae.
    SymbolEquipmentKey(SymbolEquipmentKeys.DECLINE_BENCH, EquipmentCategory.SUPPORT, setOf(REQUIREMENT_DECLINE_BENCH)),
    SymbolEquipmentKey(SymbolEquipmentKeys.HYPEREXTENSION_BENCH, EquipmentCategory.SUPPORT, setOf(REQUIREMENT_HYPEREXTENSION_BENCH)),
)
