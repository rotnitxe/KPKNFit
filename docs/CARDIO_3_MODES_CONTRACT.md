# Contrato de cardio en 3 modos

Este documento fija el contrato multiplataforma del JSON y de los cálculos de cardio introducidos en Android el 2026-08-19. iOS y backend pueden leerlo de forma tolerante aunque todavía no implementen el editor nativo.

## Modos y precedencia

`CardioDetails.programMode()` deriva el modo sin una migración Room:

1. `hiit != null` → `HIIT_SIT`.
2. Si no, `intervalBlocks` no vacío → `INTERVALS`.
3. Si no, → `STEADY` (JSON legado).

La configuración HIIT conserva `warmupSeconds`, `workSeconds`, `restSeconds`, `rounds`, `sets`, `restBetweenSetsSeconds`, `cooldownSeconds`, `workTargetType`, `workTargetValue`, `protocol`, `targetRpe`, `restNature`, `beepsEnabled`, `voiceCuesEnabled`, `vibrationEnabled` y `keepScreenOn`. Los targets `targetKcal` y `targetDistanceMeters` viven únicamente en bloques `WORK`.

## RPE y kcal

El RPE programado es subjetivo (1–10) y no se presenta como una zona de oxidación de grasas. `resolvedRpe()` usa `hiit.targetRpe` cuando existe y, en sesiones antiguas, el nivel de intensidad como fallback.

Las kcal son una estimación MET: modalidad, velocidad/vatios/nivel, duración y peso corporal. Un objetivo en kcal sin peso no se auto-corta silenciosamente: la UI debe explicar que el peso es necesario para la estimación y pedirlo antes de iniciar.

## Drenaje AUGE

`CardioRingDrainEngine` reutiliza METs de `CardioIntervalEngine` y los tanques personalizados de AUGE. La implementación conserva estas relaciones:

```text
WM = WORK×1.0 + RECOVER activo×0.3 + RECOVER pasivo×0.1 + WARMUP/COOLDOWN×0.2
density = work / (work + recover)
protocol = SIT 1.25 · HIIT 1.0 · continuo 0.6
cnsPoints = 6.0 × WM × (rpeMultiplier − 1) × protocol × (0.7 + 0.6×density)
musPoints = 2.2 × METmin × (0.6 + 0.4×RPE/10) × (0.8 + 0.4×density)
spinPoints = 9.0 × impactFactor(modality) × minutes × (0.5 + 0.5×RPE/10)
```

El motor aplica escalas conservadoras y convierte puntos a porcentajes de tanque. La salida incluye `muscleDrains` para recovery e interferencia. Carrera/cinta curva tienen mayor impacto espinal que bicicleta, Air Bike, SkiErg o elíptica; la distribución muscular sigue la modalidad.

## Compatibilidad

Todos los campos nuevos tienen defaults. Un lector anterior puede ignorarlos; un escritor anterior puede conservar el modo estático. iOS debe preservar `hiit`, los targets por bloque y los cuatro `CardioType` nuevos al re-serializar. Backend debe tratar el contrato como referencia hasta consumir cardio en sus motores espejo.

## Evidencia

La compilación `:app:compileBaseDebugKotlin` y las pruebas unitarias focalizadas de builders, serialización, cues, saltos, voz y drenaje pasan en Android. La reproducción de audio, GPS, keep-awake y TTS en hardware físico requieren QA posterior; no quedan demostrados por estas pruebas puras.
