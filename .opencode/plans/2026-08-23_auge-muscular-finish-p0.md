---
flags: [auge, ios, backend]
---

# P0 — Corrección integral del drenaje muscular AUGE al finalizar una sesión

Ruta para guardar este plan:

`.opencode/plans/2026-08-23_auge-muscular-finish-p0.md`

## Objetivo y criterios no negociables

Corregir el valor muscular automático mostrado en el sheet que aparece inmediatamente al finalizar una sesión, antes de cualquier recalibración manual.

La implementación estará terminada únicamente si:

- El preview inmediato usa cero minutos de recuperación transcurrida.
- Una ejecución equivalente produce el mismo impacto que el plan del editor, dentro de ±2 puntos por redondeo.
- Una ejecución más intensa nunca produce menor drenaje en los músculos involucrados.
- Pecho, tríceps y demás músculos trabajados reciben drenaje local real; no basta con corregir el ring global.
- El ring del sheet deja de promediar las 23 baterías internas.
- Los valores posteriores a una recalibración quedan separados del resultado automático.
- Editar un músculo no modifica, ancla ni enseña al sistema sobre músculos no editados.
- No se cambian textos, conceptos ni valores de referencia del editor de sesiones.
- No se recalibra AUGE a un 60% personal fijo.

## Implementación por fases

### Fase 0 — Preservación del árbol y baseline

1. Antes de editar, leer:

   - `AGENTS.md`
   - `.opencode/kpkn-map.md`
   - `.opencode/memory/MEMORY.md`

2. Inspeccionar:

   ```powershell
   git status --short --branch
   git diff -- android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt
   git diff -- android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFeedbackModels.kt
   git diff -- android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt
   git diff -- android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetRecorder.kt
   ```

3. Esos cuatro archivos ya tienen cambios concurrentes. Integrar sobre ellos; queda prohibido ejecutar `reset`, `checkout`, `restore`, `stash`, limpieza masiva o formateo de archivo completo.

4. Conservar como evidencia externa, sin copiar datos personales al repositorio:

   - Sesión `3313ad2e-56b4-4094-b37e-49e58420872f`.
   - 9 ejercicios y 30 series finales únicas.
   - Valor automático reportado antes de editar: ring muscular 99%, pecho 97%.
   - JSONL bajo `C:\Users\valen\CrossDevice\Z Flip5 de Matias (1)\storage\KPKN\KPKN\logs`.

5. Ejecutar baseline desde `android-native`:

   ```powershell
   powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeRingDrainRealismTest'"
   powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeRecoveryEngineManualOverrideTest'"
   powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.WorkoutFinishControllerTimeoutTest'"
   ```

Criterio de salida: baseline documentado y cambios concurrentes identificados.

---

### Fase 1 — Fixtures y pruebas rojas antes de producción

Crear un fixture anonimizado reutilizable:

`android-native/app/src/test/java/com/example/kpkn/domain/auge/AugeRealSessionFixtures.kt`

Debe representar 9 ejercicios y 30 series:

- Sentadilla: 4.
- Prensa: 4.
- Extensión de cuádriceps: 3.
- RDL: 3.
- Press banca: 4.
- Press inclinado: 3.
- Aperturas: 3.
- Press de hombro: 3.
- Press francés: 3.

Incluir cargas, repeticiones, RIR 0/1 y fallo semejantes al log, pero reemplazar IDs personales por IDs deterministas de test.

Crear inicialmente pruebas que reproduzcan los defectos:

1. `AugePostSessionPreviewImmediateTest.kt`

   - Reloj fijo en `America/Santiago`.
   - Finalización a las 17:25.
   - El log temporal debe conservar 17:25, no 00:00.
   - `hoursSinceLastSession` debe ser cero o inferior a un minuto.
   - Cubrir 00:01, 17:25, 23:59 y transición DST.

2. `AugeEditorCompletedParityTest.kt`

   - Construir la misma sesión como plan y como ejecución.
   - Congelar primero los valores actuales del editor.
   - El refactor no puede cambiar esos valores.
   - Comparar impacto global y por músculo.

3. `AugeMuscleAttributionTest.kt`

   - Pecho primario en banca/inclinado/aperturas.
   - Tríceps secundario en presses y primario en press francés.
   - Cada contribución debe ser positiva y aplicarse una sola vez.
   - Reordenar los ejercicios de piernas no puede cambiar pecho o tríceps.

4. `WorkoutFinishCalibrationTest.kt`

   - Sin edición: delta vacío y ajuste muscular cero.
   - Editar sólo pecho: delta contiene únicamente pecho.
   - Volver el slider al valor automático elimina la clave editada.
   - El mapa completo jamás se envía como override.

5. `WorkoutLogAugeImpactSerializationTest.kt`

   - Decodificación de logs antiguos.
   - Round-trip del nuevo snapshot AUGE.
   - Preservación de las 30 series y todos sus campos de intensidad.

Criterio de salida: los nuevos tests deben fallar por las causas esperadas, mientras los tests antiguos no relacionados permanecen verdes.

---

### Fase 2 — Motor canónico de impacto muscular

#### 2.1 Nuevos contratos

Crear:

`android-native/app/src/main/java/com/example/kpkn/domain/auge/MuscularSessionImpactEngine.kt`

Definir:

```kotlin
@Serializable
data class MuscularSessionImpactV2(
    val modelVersion: String = "muscle-impact-v2",
    val completionInstantIso: String,
    val globalMuscularDrain: Int,
    val perMuscle: Map<String, MuscleSessionImpactV2>,
    val involvedVolumeMuscles: Set<String>,
    val setInputHash: String,
    val contextHash: String,
)

@Serializable
data class MuscleSessionImpactV2(
    val stressUnits: Double,
    val capacityAtCompletion: Double,
    val immediateDrainPct: Double,
    val directStressUnits: Double,
    val indirectStressUnits: Double,
)
```

Añadir a `WorkoutLog`:

```kotlin
val muscularImpactV2: MuscularSessionImpactV2? = null
```

El campo es opcional y el entity de Room continúa almacenando un único JSON `data`; no cambiar versión 23 ni crear columnas.

#### 2.2 Unificar entradas planificadas y reales

Crear dos adaptadores hacia un único modelo interno:

- `fromPlannedSession(...)`
- `fromCompletedExercises(...)`

Ambos deben terminar en la misma función `evaluate(...)`.

Resolución obligatoria:

1. `catalogConfigurationId`.
2. `catalogDefinitionId`/`exerciseDbId`.
3. `exerciseId`.
4. Heurística por nombre únicamente para ejercicios personalizados o logs antiguos.

En ejecuciones reales, `effectiveMuscles` persistido tiene prioridad sobre el catálogo actual.

#### 2.3 Política de cálculo

- Mantener intacto el resultado global actual del editor.
- Extraer la lógica compartida actualmente duplicada entre:
  - `AugeFatigueEngine.calculateAdjustedPredictedDrain`;
  - `calculateCompletedSessionDrainBreakdown`;
  - `SessionEditorAugeComputation`.
- Usar las contribuciones canónicas de `VolumeCalculator.buildPerExerciseMuscleContributions`.
- Aplicar la contribución muscular exactamente una vez.
- Eliminar del camino local la combinación `FATIGUE_ROLE_MULTIPLIERS × volumeContribution`.
- Mantener acumuladores de series y diminishing por músculo. El acumulador global puede conservarse para los rings globales, pero no puede reducir el impacto local de un músculo ajeno.
- Mantener soft-cap por músculo y los límites fisiológicos actuales.
- Usar `AugeFatigueEngine.getEffectiveRPE` como precedencia única:
  - fallo;
  - `actualIntensityMode/value`;
  - RIR;
  - RPE reportado.

#### 2.4 Capacidad previa

Extraer `calculateUserWorkCapacity` a:

`android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeMuscleCapacityEngine.kt`

Reglas:

- Para una sesión nueva, usar únicamente logs con timestamp anterior a `completionInstant`.
- La sesión actual nunca puede aumentar el denominador con el que se mide a sí misma.
- Guardar `capacityAtCompletion` dentro del snapshot.
- `AugeRecoveryEngine` decae el impacto almacenado; no vuelve a aplicar rol, activación o capacidad.
- Para logs legacy sin snapshot, derivar V2 temporalmente en orden cronológico usando sólo historial anterior. No reescribir datos del usuario.

Criterio de salida: paridad plan/ejecución, monotonicidad y atribución muscular pasan en tests puros.

---

### Fase 3 — Timestamp y estado automático del sheet

#### 3.1 Captura de finalización

Modificar `FinishResumeSnapshot` en `WorkoutUiModels.kt` para incluir:

- `finishOperationId`.
- `completionInstantIso`.
- `completedSetInputHash`.

`WorkoutViewModel.openFinishSheet()` debe capturarlos una sola vez al abrir el sheet. Si el sheet se recupera tras rotación, reutilizar los mismos valores.

#### 3.2 Preview

Modificar `AugeViewModel.computePostSessionPreview`:

- Recibir `completionInstantIso`, `finishOperationId` e `inputHash`.
- Sustituir `LocalDate.now().toString()` por el mismo ISO instantáneo.
- Evaluar el preview con `now == completionInstant`.
- Pasar `capacityHistory = baseHistory`; el preview se añade sólo como impacto, nunca como capacidad previa.

Ampliar `PostSessionPreview` con:

- `finishOperationId`.
- `completionInstantIso`.
- `inputHash`.
- `source = FINISH_AUTO_PREVIEW`.
- `automaticImpact`.
- `involvedVolumeMuscles`.

#### 3.3 Estado de carga

En `WorkoutScreen.kt`, reemplazar el placeholder 100/100/100 por:

```kotlin
sealed interface FinishAugePreviewState {
    data object Loading
    data class Ready(val preview: PostSessionPreview)
    data class Error(val reason: String)
}
```

- No renderizar valores finales como 100 mientras calcula.
- Deshabilitar confirmación hasta `Ready`.
- Verificar que el hash del preview coincida con las series finales actuales.

#### 3.4 Universo del sheet

En `WorkoutFinishHost.kt`:

- Sembrar y renderizar sliders sólo para `involvedVolumeMuscles`.
- Filtrar mediante los músculos canónicos visibles y contables por `VolumeCalculator`.
- No usar las 23 claves de `BATTERY_MUSCLES`.
- `derivedMuscularFinal` debe usar `postSessionPreview.muscular` cuando no hay edición.
- Si existe edición, recalcular mediante el agregador canónico; eliminar definitivamente `muscleFinal.values.average()`.

Criterio de salida: el sheet inmediato usa el snapshot automático correcto, sin músculos agregados artificialmente ni recuperación ficticia.

---

### Fase 4 — Recalibración manual correcta

#### 4.1 Payload delta

Cambiar `SessionClosingFeedback`:

- Sustituir `musclesEdited: Boolean` por `editedMuscleKeys: Set<String>`.
- `finalMuscleBatteries` debe contener sólo las claves cuyo valor final difiera del automático.
- Mantener campos legacy únicamente si son necesarios para decodificación, sin nuevas escrituras.

En `WorkoutFinishHost`:

```kotlin
editedMuscleKeys =
    currentValues.keys.filterTo(mutableSetOf()) {
        currentValues[it] != automaticSeed[it]
    }
```

#### 4.2 Ajustes y aprendizaje

- Si `editedMuscleKeys` está vacío:
  - `muscularAdjustment = 0`;
  - no actualizar prediction bias;
  - no crear ancla;
  - no ejecutar aprendizaje muscular;
  - no modificar `sessionStressScore`.
- Si hay delta:
  - combinarlo temporalmente con el snapshot automático;
  - recalcular el ring mediante el agregador canónico;
  - `muscularAdjustment = automaticGlobalBattery - recalibratedGlobalBattery`;
  - conservar clamp actual `[-35, 35]`;
  - aprender únicamente de las claves editadas.

`updatePredictionBiasFromClosingFeedback` debe actualizar sólo los canales realmente editados. Editar pecho no puede modificar o decaer los biases neural y espinal.

#### 4.3 Overrides con ancla individual

En `AugeModels.kt` añadir:

```kotlin
@Serializable
data class ManualMuscleBatteryOverride(
    val battery: Int,
    val anchorEpochMs: Long,
    val sourceSessionId: String?,
    val automaticBatteryAtAnchor: Int,
)
```

Y en `DailyWellbeingLog`:

```kotlin
val manualMuscleOverridesV2:
    Map<String, ManualMuscleBatteryOverride> = emptyMap()
```

Modificar `AugeRepository` y `AugeViewModel.applyManualBatteries`:

- Recibir `perMuscleDelta`, no el mapa completo.
- Fusionar sólo claves editadas.
- Aplicar expiración y filtrado de historial por la ancla de cada músculo.
- `clearManualBatteryOverrides` debe limpiar tanto V1 como V2.
- Leer V1 por compatibilidad; escribir exclusivamente V2.

#### 4.4 Invalidación del aprendizaje muscular contaminado

El flujo antiguo entrenó sesgos y multiplicadores usando mapas completos y ajustes negativos incluso sin edición. Para que el arreglo no siga condicionado por esa información:

- Subir `AugeAdaptiveCache.schemaVersion` a 2.
- Al leer V1:
  - conservar recuperación personalizada, CNS y columna;
  - limpiar sólo `muscleDeltas` y `muscleDrainMultipliers`;
  - iniciar un contador muscular V2 en cero.
- Añadir versión muscular a `PredictionBiasProfile`.
- En la primera normalización V2:
  - poner únicamente `muscularBias = 0`;
  - preservar `cnsBias` y `spinalBias`.
- Emitir una sola entrada diagnóstica `muscular_calibration_reset_v2`.
- No borrar silenciosamente overrides manuales legacy; continuarán con su expiración vigente.

Criterio de salida: una recalibración afecta exclusivamente los músculos tocados y el nuevo motor no hereda aprendizaje muscular generado por el bug.

---

### Fase 5 — Persistencia, orden y telemetría

#### 5.1 Orden de finalización

La secuencia única será:

1. Congelar series finales, timestamp, operación e input hash.
2. Calcular snapshot automático.
3. Persistir `WorkoutLog` con el mismo timestamp y `muscularImpactV2`.
4. Publicar `POST_PERSISTED_AUTO`.
5. Aplicar delta manual sólo si existe.
6. Actualizar aprendizaje/bias sólo para canales editados.
7. Ejecutar una recomputación final.
8. Publicar Home.

Añadir una generación monotónica a las recomputaciones de `AugeViewModel`; un resultado anterior no puede sobrescribir otro posterior. No clasificar eventos distintos como carrera si sus fuentes o hashes difieren.

#### 5.2 Eventos JSONL

Usar `KpknDiagnosticLogger` con `finishOperationId`, `logId` e `inputHash`:

- `auge/session_input`
- `auge/session_impact`
- `auge/finish_auto_preview`
- `auge/post_persisted_auto`
- `auge/manual_override_applied`
- `auge/snapshot_published`

Campos mínimos:

- `completionInstantIso`.
- Conteo de ejercicios y series finales.
- IDs canónicos/configuración.
- Músculos involucrados.
- Ring automático y batería automática por músculo.
- Drenaje directo/indirecto.
- Capacidad aplicada.
- `editedMuscleKeys`.
- Valores manuales únicamente si existen.
- Trigger de recomputación y versiones del motor/cache.

En `WorkoutViewModel`/`WorkoutSetRecorder`, registrar cada reemplazo de serie aunque el contador no aumente:

- `setKey`.
- `operation = insert|replace`.
- `actualIntensityMode`.
- `actualIntensityValue`.
- RIR.
- RPE reportado y efectivo.
- fallo.

No registrar notas, fotografías, credenciales ni datos personales.

Criterio de salida: el valor automático 99/97, el resultado automático corregido y cualquier recalibración aparecen como estados distintos y causalmente identificables.

---

### Fase 6 — Paridad iOS y backend

#### iOS

Portar contratos y fixtures a:

- `ios-native/KPKNFit/KPKNFit/Domain/Auge/AugeFatigueEngine.swift`
- `ios-native/KPKNFit/KPKNFit/Domain/Auge/AugeRecoveryEngine.swift`
- `ios-native/KPKNFit/KPKNFit/Domain/Auge/AugeAdaptiveEngine.swift`
- `ios-native/KPKNFit/KPKNFit/Data/Models/AugeModels.swift`
- `ios-native/KPKNFit/KPKNFit/Presentation/Screens/Auge/AugeViewModel.swift`
- `ios-native/KPKNFit/KPKNFit/Presentation/Screens/Auge/PostSessionSheet.swift`

Crear:

`ios-native/KPKNFit/KPKNFitTests/AugeMuscularSessionImpactTests.swift`

Usar el mismo reloj y fixture dorado. No rediseñar la UI iOS.

#### Backend

Actualizar:

- `backend/engines/fatigue_engine.py`
- `backend/engines/recovery_engine.py`
- `backend/models/common.py`
- `backend/routers/fatigue.py`

Añadir opcionalmente `muscularImpactV2` a la respuesta existente sin romper campos actuales.

Crear:

`backend/tests/test_auge_muscular_session_impact.py`

Criterio de salida: mismo input dorado y diferencias máximas de ±2 puntos entre Android, iOS y backend.

## Rutas

### Nuevas Android

- `domain/auge/MuscularSessionImpactEngine.kt`
- `domain/auge/AugeMuscleCapacityEngine.kt`
- Tests de preview, paridad, atribución, calibración y serialización.

### Android modificadas

- `domain/auge/AugeFatigueEngine.kt`
- `domain/auge/AugeRecoveryEngine.kt`
- `domain/auge/AugeUtils.kt`
- `screens/auge/AugeViewModel.kt`
- `screens/sessioneditor/SessionEditorAugeComputation.kt`
- `screens/workout/WorkoutFinishHost.kt`
- `screens/workout/WorkoutFinishController.kt`
- `screens/workout/WorkoutScreen.kt`
- `screens/workout/WorkoutViewModel.kt`
- `screens/workout/WorkoutUiModels.kt`
- `screens/workout/WorkoutFeedbackModels.kt`
- `screens/workout/WorkoutSetRecorder.kt`
- `data/models/WorkoutLog.kt`
- `data/models/AugeModels.kt`
- `data/models/AugeAdaptiveModels.kt`
- `data/models/Settings.kt`
- `data/repository/AugeRepository.kt`
- `data/diagnostics/KpknDiagnosticLogger.kt`

### Rutas que no deben tocarse

- UI o textos del editor fuera de `SessionEditorAugeComputation.kt`.
- Catálogos/datasets de ejercicios.
- Voice/hardware.
- Esquema Room, salvo que aparezca una necesidad nueva y se revise este plan con flag `room`.
- Fórmulas y coeficientes de Energía/Columna.

## Impacto

- Android continúa local-first.
- Room permanece en versión 23; los modelos nuevos viven en blobs JSON compatibles.
- Los logs antiguos siguen decodificando.
- Home puede mantener su universo completo de recuperación; el sheet usa solamente músculos involucrados y el agregador global canónico.
- El editor mantiene sus resultados actuales y se convierte en el oráculo de paridad.
- El aprendizaje muscular anterior se invalida selectivamente; CNS, columna y recuperación personalizada se conservan.
- No habrá cambios de copy ni aclaraciones dentro de los rings.

## Pruebas

### Unitarias focalizadas

Desde `android-native`:

```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugePostSessionPreviewImmediateTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeEditorCompletedParityTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeMuscleAttributionTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.WorkoutFinishCalibrationTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeRecoveryEngineManualOverrideTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AugeRingDrainRealismTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.WorkoutFinishControllerTimeoutTest'"
```

Luego:

```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "compileBaseDebugKotlin"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "assembleBaseDebug"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "connectedBaseDebugAndroidTest"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "installBaseDebug"
```

### Gates funcionales

Bloquear entrega si ocurre cualquiera:

- Preview inmediato con `hoursSinceLastSession > 0`.
- Plan equivalente y ejecución difieren más de 2 puntos.
- Una ejecución más intensa drena menos.
- El fixture duro de pecho sigue mostrando sólo 3% de drenaje.
- Ejercicios de piernas cambian el drenaje de pecho/tríceps.
- El sheet vuelve a promediar todas las baterías internas.
- Confirmar sin editar crea ajuste, bias, aprendizaje o ancla.
- Editar pecho modifica otro músculo.
- Más de un `session_finished` por operación.
- Finish y Home automáticos difieren con el mismo snapshot/hash.

### Emulador

- Instalar en `emulator-5554`.
- Ejecutar editor → sesión → sheet inmediato.
- Capturar screenshot, UI tree, `dumpsys activity` y JSONL real.
- Confirmar que `com.example.kpkn/.MainActivity` está top-resumed.
- Repetir sin editar y editando solamente pecho.

### Z Flip5 físico

- Detectar serial real mediante `adb devices -l`.
- Ejecutar una sesión representativa con presses y trabajo directo.
- Capturar el sheet antes de tocar sliders.
- Verificar JSONL:
  - 9 ejercicios/30 series en fixture o conteo equivalente;
  - timestamp completo;
  - snapshot automático;
  - músculos involucrados;
  - ausencia de evento manual antes de editar;
  - delta exclusivamente de pecho al recalibrarlo.
- Reportar build, instalación, UI, JSONL y dispositivo físico como evidencias separadas.

### iOS/backend

```bash
python -m pytest -q backend/tests/test_auge_muscular_session_impact.py
```

En macOS:

```bash
xcodebuild -list -project ios-native/KPKNFit/KPKNFit.xcodeproj
xcodebuild -project ios-native/KPKNFit/KPKNFit.xcodeproj \
  -scheme KPKNFit \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  test CODE_SIGNING_ALLOWED=NO
```

Si iOS no puede ejecutarse desde Windows, declararlo explícitamente como no validado; no presentar la paridad estática como E2E verde.

## Riesgos

- **Árbol muy sucio:** integrar cambios quirúrgicamente y revisar el diff de cada archivo.
- **Aprendizaje muscular contaminado:** invalidación selectiva V2 obligatoria; sin ella el motor corregido podría seguir drenando poco.
- **Overrides legacy activos:** no borrarlos automáticamente; distinguirlos en telemetría y esperar su expiración o pedir autorización antes de limpiarlos.
- **Cambio accidental del editor:** los golden tests se crean antes del refactor y bloquean cualquier diferencia.
- **Regresión en otros rings:** mantener sus coeficientes y exigir golden tests con el mismo timestamp.
- **Rounding Android/iOS/backend:** tolerancia máxima ±2, nunca desigualdades direccionales.
- **Tests parciales:** una compilación o un test focalizado no equivale a suite completa, instalación, UI o prueba física.


