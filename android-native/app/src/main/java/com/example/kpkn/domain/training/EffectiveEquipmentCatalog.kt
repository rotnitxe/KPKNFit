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

/** Requisito que una configuración expresa fuera de su `equipmentId` (§13.2). */
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
    configurationId.startsWith(BENCH_PRESS_PREFIX) -> buildSet {
        add(REQUIREMENT_BENCH)
        if (configurationId.endsWith(BARBELL_SUFFIX)) add(REQUIREMENT_RACK)
    }
    // Fondos y dominadas: cada uno con SU apoyo (§13.2: no derivar uno del otro).
    "triceps_fondos_entre_bancos__default" == configurationId -> setOf(REQUIREMENT_BENCH)
    "tren_superior_fondos__default" == configurationId -> setOf(REQUIREMENT_DIP_BARS)
    configurationId.startsWith(PULL_UP_PREFIX) -> setOf(REQUIREMENT_PULL_UP_BAR)
    // Apoyos concretos
    "back_remo_invertido__default" == configurationId -> setOf(REQUIREMENT_LOW_BAR_SUPPORT)
    "curl_isquios_con_balon__default" == configurationId -> setOf(REQUIREMENT_BALL)
    "hams_curl_nordic_peso_corporal__default" == configurationId -> setOf(REQUIREMENT_NORDIC_ANCHOR)
    "push_up__feet_elevated" == configurationId -> setOf(REQUIREMENT_SUPPORT)
    else -> emptySet()
}

private const val BENCH_PRESS_PREFIX = "bench_press__"
private const val INCLINE_BENCH_PRESS_PREFIX = "incline_bench_press__"
private const val DECLINE_BENCH_PRESS_PREFIX = "decline_bench_press__"
private const val PULL_UP_PREFIX = "pull_up__"
private const val BARBELL_SUFFIX = "__barbell"

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

/** Polea alta y baja: jalón, remo y extensiones con montaje curado (no doble polea ni cuerda). */
private val CABLE_HIGH_LOW_CONFIGURATIONS = setOf(
    "lat_pulldown__bilateral__cable",
    "lat_pulldown__unilateral__cable",
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
 */
internal fun EquipmentAvailability.hasExplicitMachinePresence(): Boolean =
    EFFECTIVE_EQUIPMENT_KEYS.any { spec ->
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
