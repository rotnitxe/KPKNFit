package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.TrainingValidation

/**
 * Contrato real de entrenamiento del onboarding, re-exportado desde el modelo
 * común puro [TrainingOptions] (que vive en `domain/training` para que el motor
 * no dependa de `domain/onboarding`). El dueño del draft conserva la misma API:
 * `SetupTrainingOptions()` con sus campos, `validate()`, `applyTo(input)` y
 * `applyTo(program)`, `resolvedWarmupSteps()` y [SetupTrainingValidation].
 *
 * Comportamiento (ver [TrainingOptions]):
 * - `autoregulationMode` nace en PROPOSE; AUTO exige confirmación explícita.
 * - `orderPriorities`: bolsa de orden de ejercicios, máximo 2 puntos por
 *   músculo, 5 en total y sin negativos; SOLO orden, nunca volumen ni recetas.
 * - `inventory` nullable hasta declarar material; una declaración NUEVA exige
 *   cantidades explícitas y finitas (el lector legacy conserva nulos al leer).
 * - `effectiveEquipment(legacy)` (extensión en `domain/training`) es el equipo
 *   efectivo COMPARTIDO: con inventario declarado manda la presencia real del
 *   material (`general_gym` nunca se asume) y sin inventario se conserva el
 *   perfil legacy. Lo consumen el readiness/candidatos del wizard y el filtro
 *   real de `SimpleCyclePersonalizer`.
 * - `warmup`: vacío = sin calentamientos; null = preset 40 % × 8 / 60 % × 5 /
 *   80 % × 3 sobre la carga de trabajo; lista = pasos personalizados ya
 *   normalizados (orden ascendente, sin duplicados ±5 puntos porcentuales).
 *
 * Nota de serialización: la clase real reside en `domain/training` con el
 * nombre de serialización de [TrainingOptions]; todavía no hay ningún draft
 * persistido con esta configuración (la UI la cableará en la próxima ola), así
 * que no hay ningún JSON compatible que romper.
 */
typealias SetupTrainingOptions = TrainingOptions

/** Resultado de [TrainingOptions.validate] con la API pública que ya usa el draft. */
typealias SetupTrainingValidation = TrainingValidation