package com.example.kpkn.domain.training

import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2

/**
 * **Único** filtro de material por configuración del catálogo: dado el equipo efectivo (los tokens de
 * [resolveEffectiveEquipment]), ¿se puede ejecutar esta configuración? Lo llaman el planificador y los planes propios
 * (`SimpleCyclePersonalizer`) y el generador de rutinas (`DayEquipment.allows`): no queda ninguna réplica que pueda
 * divergir.
 *
 * Reglas (las mismas que tenía el filtro privado de `SimpleCyclePersonalizer`):
 * - La **familia** del plan restringe el implemento: `machine-muscle` solo admite máquinas, `bodyweight` solo el cuerpo y
 *   `home-training` cuerpo, banda o mancuernas. Sin familia (`null`) no restringe nada.
 * - **Máquina concreta**: con el modo «configuración exacta» activo, una configuración de implemento `machine` solo se
 *   admite si su token `machine_config:<id>` consta (leg curl ≠ chest press ≠ prensa); sin él, la categoría `machine`
 *   basta para las variantes de máquina nativas aprobadas.
 * - **Implemento**: la configuración pasa si su `equipmentId` consta en el equipo o si su máquina concreta está
 *   declarada; el perfil legacy `general_gym` (solo para LEER programas previos) abre cualquier implemento.
 * - **Soportes**: todo lo que pide [supportRequirementsFor] (banco, rack, barra de dominadas, paralelas, barra baja,
 *   balón, anclaje de Nordic…) debe constar, o ser `general_gym`.
 *
 * Lo que cada símbolo del wizard acredita lo decide el resolutor, no este filtro: aquí solo se contrasta el equipo ya
 * resuelto con lo que la configuración exige.
 */
internal object ConfigurationEquipmentFilter {

    /** Planes de «Músculo con máquinas»: solo configuraciones de implemento `machine`. */
    const val FAMILY_MACHINE_MUSCLE = "machine-muscle"

    /** Planes de peso corporal: solo configuraciones de implemento `bodyweight`. */
    const val FAMILY_BODYWEIGHT = "bodyweight"

    /** Planes de entrenamiento en casa: cuerpo, banda o mancuernas. */
    const val FAMILY_HOME_TRAINING = "home-training"

    private const val MACHINE = "machine"
    private const val BODYWEIGHT = "bodyweight"
    private const val GENERAL_GYM = "general_gym"

    private val HOME_TRAINING_EQUIPMENT = setOf(BODYWEIGHT, "band", "dumbbells")

    /**
     * ¿Rige el modo «configuración exacta» de máquinas? Sí con una declaración CONCRETA de máquinas o poleas (presencia
     * por llave: los soportes, la barra de dominadas y la bici exterior no cuentan; paquete A · B3, DEV-r2-06) o, sin
     * disponibilidad nueva, con inventario declarado. Una disponibilidad solo categórica, con o sin soportes
     * confirmados, sigue permitiendo las variantes de máquina nativas aprobadas.
     */
    fun requiresExactMachineConfiguration(options: TrainingOptions): Boolean =
        options.availability?.hasExplicitMachinePresence() ?: (options.inventory != null)

    /** ¿Se puede ejecutar [configuration] con el equipo efectivo [tokens]? */
    fun allows(
        configuration: ExerciseConfigurationV2,
        tokens: Set<String>,
        family: String? = null,
        requireExactMachineConfiguration: Boolean = false,
    ): Boolean = allowsPrecomputed(
        equipmentId = configuration.profile.equipmentId,
        machineToken = machineConfigToken(configuration.id),
        supportRequirements = supportRequirementsFor(configuration.id),
        tokens = tokens,
        family = family,
        requireExactMachineConfiguration = requireExactMachineConfiguration,
    )

    /**
     * El mismo filtro con las piezas ya calculadas (`machine_config:<id>` y [supportRequirementsFor]) para quien las
     * guarda y pregunta miles de veces por la misma configuración (el generador de rutinas). Es el único sitio con la
     * lógica: [allows] solo calcula las piezas y delega aquí.
     */
    fun allowsPrecomputed(
        equipmentId: String,
        machineToken: String,
        supportRequirements: Set<String>,
        tokens: Set<String>,
        family: String? = null,
        requireExactMachineConfiguration: Boolean = false,
    ): Boolean {
        if (family == FAMILY_MACHINE_MUSCLE && equipmentId != MACHINE) return false
        if (family == FAMILY_BODYWEIGHT && equipmentId != BODYWEIGHT) return false
        if (family == FAMILY_HOME_TRAINING && equipmentId !in HOME_TRAINING_EQUIPMENT) return false
        val machineConfigDeclared = machineToken in tokens
        if (requireExactMachineConfiguration && equipmentId == MACHINE && !machineConfigDeclared) return false
        if (!machineConfigDeclared && GENERAL_GYM !in tokens && equipmentId !in tokens) return false
        // Requisitos de soporte de ESTA configuración: todos declarados o el perfil legacy `general_gym`.
        return supportRequirements.isEmpty() || GENERAL_GYM in tokens || supportRequirements.all { it in tokens }
    }
}
