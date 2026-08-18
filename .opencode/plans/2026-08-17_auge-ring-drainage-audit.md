---
flags: [auge, ios, backend, nutrition]
---

# Fix: drenaje muscular real AUGE estrangulado (rings per-músculo quedan en 97-99% tras sesión intensa)

## Problema (confirmado por auditoría)

Los músculos apenas drenan tras entrenar intenso (2-3% en el ring del músculo específico, todos los músculos, incluso fullbody). **No es un bug de lectura de datos**: los sets reales se graban y se leen bien. Es la matemática de la ruta real de drenaje (`AugeRecoveryEngine.calculateMuscleBattery`, `domain/auge/AugeRecoveryEngine.kt:408`), que estrangula el drenaje en tres puntos:

1. **Divisor de capacidad inflado**: `capacity = clamp(weeklyAvg×1.8, 500, 3500)` (`:402`). El floor `ATHLETE_CAPACITY` (500-1200, `AugeFatigueEngine.kt:29-37`) domina siempre — harían falta >278 pp/semana de estrés muscular para superarlo. Una sesión dura de pecho (~30 pp de estrés) produce `rawFatiguePct ≈ 6`.
2. **Compresión exponencial**: `100×(1-exp(-rawFatiguePct/90))` (`:553`) convierte ese 6 en una penalización de ~6.5 puntos de batería.
3. **Decaimiento acelerado**: la fatiga decae `×exp(-k×horas_sigmoidales)` (`:547`, k del perfil) → a las 24h el ring ya recuperó la mitad de lo poco que drenó; a las 48h, ~99.5%.

Resultado medido en la traza: sesión 4×8 RPE8 de pecho → ring de pecho 93.5% al terminar, ~97% a las 24h. El usuario observa aún menos, coherente con sesiones donde el multiplicador progresivo (sets 7+ pierden hasta 70%) y la división de capacidad reducen más el estrés efectivo. Origen: calibración heredada del `recoveryService.ts` de la PWA, descalibrada respecto a la magnitud actual de los drains (post-caps de 32%/set y conservación 0.85).

Efectos secundarios detectados en la auditoría (documentar, no necesariamente fix):
- `calculateCompletedSessionMuscleDrains` (`AugeFatigueEngine.kt:698`) no tiene callers en main/ — el desglose per-músculo del cierre de sesión nunca alimenta nada.
- La proyección per-músculo del editor (`SessionEditorAugeComputation.kt:598-613`) omite conservación/diminishing/soft-cap → la estimación del editor y el ring real no son comparables.
- Sets con `reps=0, weight>0` pasan `isSetEffective` pero drenan ~4× menos.
- El ring global muscular es promedio de 13 pilares (explica por qué el ring general se mueve aún menos).

## Rutas

### Fase 0 — Tests de caracterización (antes de tocar nada)
- Nuevo `android-native/app/src/test/java/com/example/kpkn/domain/auge/MuscleBatteryVsPredictedParityTest.kt`:
  - Sesión dura per-músculo (ej. pecho 4 ejercicios × 4 sets RPE8) → `calculateMuscleBattery("Pectorales", history, nowOverride=t0)` a 0h/12h/24h/48h. Pin de los valores actuales.
  - Pin del floor: con historial realista de 4 semanas, `calculateUserWorkCapacity` devuelve el floor → expone que ×1.8 nunca actúa.
  - Sensibilidad reps=0.
  - Coherencia editor: `muscleDrainProjection` vs `calculateCompletedSessionMuscleDrains`.
  - Estos tests DEBEN romperse con la Fase 1 y se actualizan con los nuevos valores justificados.

### Fase 1 — Recalibración del drenaje real (EL FIX)
- `domain/auge/AugeRecoveryEngine.kt` (`calculateUserWorkCapacity` :344-403, `calculateMuscleBattery` :408+):
  - Reemplazar o rebajar el floor de `ATHLETE_CAPACITY` para que la sensibilidad real sea significativa (el esfuerzo de una sesión dura debe mover el ring del músculo decenas de puntos, no 2-6).
  - Revisar la constante del compresor exponencial (`/90`) y el decaimiento sigmoidal (`:547`, `AugeUtils.kt:100-107`) para que la fatiga de una sesión dura persista de forma razonable (no ~50% recuperado en 24h para un músculo machacado).
  - **Requisito AUGE**: el Constructor debe simular y documentar en el PR una tabla antes/después (estrés de sesión → batería a 0h/24h/48h, para sesión suave/media/dura, y para perfiles ENTHUSIAST/BODYBUILDER) y elegir los valores con justificación numérica. Sin números mágicos sin justificar.
  - Verificar que `calculateGlobalBatteries` (:943+) y sus deltas adaptativos/floor (:1041-1057) siguen siendo coherentes con la nueva escala.
  - Verificar interacción con `AugeAdaptiveCacheEntity`/EMA (`adjustPredictedDrainWithEMA`) tras el cambio de magnitudes.

### Fase 2 — Alinear la estimación del editor (para que ambos números hablen el mismo idioma)
- `screens/sessioneditor/SessionEditorAugeComputation.kt` (`muscleDrainProjection` :576-613): aplicar conservación/diminishing/soft-cap al reparto per-músculo o consumir `calculateCompletedSessionMuscleDrains`, de modo que la estimación del editor anticipe razonablemente lo que el ring hará.
- Revisar etiquetas en `AssistantSheet.kt` / `SessionEditorControls.kt` (`SessionEstimatedRings`) si la semántica cambia.

### Fase 3 — Paridad obligatoria (mismo PR o PR encadenado inmediato)
- `ios-native/`: `AugeRecoveryEngine.swift:269-319` replica la misma matemática — portar la recalibración con los mismos valores y la misma tabla de justificación.
- `backend/`: `recoveryService.ts` (origen del floor heredado) — alinear si aplica.
- Actualizar documentación de paridad AUGE en `docs/`.

## Impacto

- **Android**: `domain/auge/AugeRecoveryEngine.kt` (y constantes en `AugeFatigueEngine.kt`/`AugeUtils.kt` si la recalibración las toca) → cambian rings de Home, pantalla AUGE, y toda recomendación adaptativa que lea baterías. Comportamiento histórico recalculado cambiará (el ring se deriva del historial; usuarios verán rings distintos al actualizar — es el efecto deseado).
- **Editor de sesiones**: solo Fase 2.
- **iOS / Backend**: Fase 3, paridad estricta.
- **Room**: sin cambios de esquema.
- **Voz**: sin impacto.

## Pruebas

- Fase 0: `testBaseDebugUnitTest --tests '*MuscleBatteryVsPredictedParityTest'` + suite AUGE existente completa (debe pasar antes del fix).
- Fase 1: actualizar pins con valores nuevos justificados; suite AUGE completa; `testBaseDebugUnitTest` completo; `compileBaseDebugKotlin`. La tabla de simulación antes/después es entregable obligatorio.
- Fase 2: tests del editor (`SessionEditor*`) + test de coherencia proyección↔breakdown.
- Validación manual: sesión intensa registrada → ring del músculo debe caer de forma claramente visible al terminar y seguir bajo al día siguiente; sesión suave → caída modesta.

## Riesgos

- **Cambio de comportamiento transversal**: toda batería/recomendación AUGE cambia de magnitud. Mitigación: Fase 0 fija el comportamiento actual; tabla de justificación obligatoria; paridad en el mismo PR.
- **Sobre-drenaje**: si la recalibración es agresiva, usuarios aparecerán "machacados" siempre → el Constructor debe incluir en la simulación semanas de alto volumen (5-6 sesiones) y verificar que el músculo no queda clavado en el floor permanentemente, y que la recuperación completa sigue siendo alcanzable en 48-96h según perfil.
- **Acoplamientos ocultos**: `decelerateBattery`, blend 85/15 global, floor fisiológico, DOMS cap y EMA adaptativa pueden interactuar con la nueva escala — verificación explícita requerida en Fase 1.
- **Descalibración iOS/backend si se omite Fase 3**: bloqueante para merge.
