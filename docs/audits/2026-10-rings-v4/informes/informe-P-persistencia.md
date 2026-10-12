# Informe del agente P · persistencia (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

# Auditoría AUGE: orquestación, persistencia, concurrencia y observabilidad

Solo lectura, sobre HEAD `00431a27d`. No se ejecutó Gradle, tests ni escrituras. Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/` salvo que se indique. Lo marcado como HIPÓTESIS no está demostrado.

## A. Mapa de alcance

### A1. Mutex y llamadas a `recompute` (Q1)

| Función (VM) | Toma `augeWriteMutex` | Llama `recompute` dentro | Llamador |
|---|---|---|---|
| `saveWellbeing` | :412 | sí, :455 | `WorkoutSessionOverlaysHost:101` (readiness) |
| `applyManualBatteries` | :611 | sí, :697 | `WorkoutScreen:1747` (cierre con anillo editado) |
| `dismissAdvisory` | :1060 | sí, :1066 | `HomeScreen:321` |
| `markModelUpdateNoticeShown` | :1075 | no | `HomeScreen:322` |
| `clearManualBatteryOverrides` | :1135 | sí, :1138 | ninguno (muerto) |
| `resetAdaptiveCache` | :1148 | sí, :1151 | ninguno (muerto) |
| `recompute`, persistencia | :396, solo si `cacheToPersist != null && generation == actual` (:395) | — | todos los anteriores |

`recompute` sin mutex: colector de `init` (:166–167), `refresh()` (:471–475), temporizador de 5 min (:173–178) y `WorkoutScreen:1774`. `savePostSessionFeedback` (:463) no tiene llamadores.

kotlinx `Mutex` no es reentrante: un `lock()` desde la corrutina que ya lo sostiene queda suspendido para siempre (P-01).

Lectura-modificación-escritura fuera del mutex:

| Dato | Ruta | Riesgo |
|---|---|---|
| Caché | `learnFromLatestFinishedLog` lee en :209 y escribe en :869 y :904 sin mutex; llamado desde init/refresh | pierde cambios concurrentes |
| Caché | `recompute` persiste en :399 con la lectura de :209; la comprobación de generación no ve `markModelUpdateNoticeShown`, que no sube generación | revierte `modelUpdateNoticeShown` |
| Caché | `SettingsJsonBackup:234` sin mutex ni generación | un recálculo en vuelo pisa el caché importado |
| Bienestar | `SettingsJsonBackup:229` (reemplazo por fila) sin mutex | pisa escrituras concurrentes |
| Bienestar | `WorkoutSessionOverlaysHost:77–97` parte de `todayWellbeing`, leído una vez en `WorkoutScreen:352–354` | copias obsoletas tratadas como ajustes (P-03) |

### A2. Línea de tiempo del cierre (Q2)

1. `finish()` (`WorkoutFinishController:104–130`): espera la grabación (≤10 s) y aplica guardas (:113–119).
2. Lee la caché cruda (:231–233), sin mutex.
3. `automaticImpact` y `stressScore` en Default (:234–343).
4. Construye `WorkoutLog` con `logId = programId|sessionId|weekId|startTimeMs` (:217–222), `muscularImpactV2` y `sessionStressScore` (:382–383) y `ringStartSnapshot` solo si la hoja lo trajo (:421–433).
5. `finalizeWorkout` (`ProgramRepository:849`, `NonCancellable` :856, `ongoingWorkoutMutex` :857). Dentro hay dos `computeAugeSnapshot` (:967–968) y una transacción Room (:1026–1037: log, limpieza de ongoing, programa, estado activo). **Fase 1 atómica.**
6. `_history.value` se publica (RP:1046). El colector del VM lanza R1 sin mutex.
7. `updateState(isComplete)` y telemetría (:496–546).
8. `onComplete` (:555–557) → `applyManualBatteries` (fila :664, aprendizaje :684) o `refresh()`. **Fase 2, no atómica** con la 1.
9. `recompute` R2 dentro de `applyManualBatteries` (:697) → posible interbloqueo (P-01).

Si la app muere tras el paso 5, el log queda guardado y AUGE lo ve al arrancar; se pierde la fase 2 (anillos editados). El aprendizaje ignora logs de más de 6 h al recalcular y los marca como aprendidos (VM:866–871). Reintento: si ya existe el registro, `finalizeWorkout` reinserta el guardado y descarta el contenido nuevo (RP:874–887, identidad congelada). `learnFromManualAdjustment` no es idempotente por log (VM:712–835): depende del guard de finish.

Ventanas con log incompleto: no ocurren con `muscularImpactV2` ni `sessionStressScore` en logs nuevos. Sí sin `ringStartSnapshot` en cierre por voz (P-13) y sin `preSessionMuscular` (`WorkoutFinishHost:416`). El fallback de `preSession*` a valores post-sesión es inalcanzable: `AugeRecoveryEngine:1925–1927` siempre rellena los tres.

### A3. Escritores de `DailyWellbeingLog` (Q4)

| Escritor | Ruta | Mutex / merge | Conserva | Pierde o altera |
|---|---|---|---|---|
| Readiness | `WorkoutSessionOverlaysHost:76–101` → `VM.saveWellbeing:410` | sí / `mergeWellbeingForSave` | `capturedFields` (unión), anclas, V2 no declaradas | `source` pasa a `DAILY_CHECK_IN` (`WellbeingSaveMerge:38–46`); copias de la lectura inicial |
| Ajuste en cierre | `VM.applyManualBatteries:596` | sí / no | check-in, notas, molestias | `source` y `capturedFields` por defecto (VM:639–663); V1 vacío si toca V2 (:655); ancla de fila reestampada (:656–659) |
| Voz | `WorkoutVoiceCommandHandler:801,1350` → `WorkoutViewModel.finalizeVoiceSession:6448` | no | — | `voiceFinalNeural/Spinal` nunca se escriben (P-02) |
| Onboarding | `SetupPersistence:274–276` → `mergeSetupWellbeing:431` | DAO en transacción | `id`, `source`, `capturedFields` | — |
| Restauración | `SettingsJsonBackup:229–234` | DAO en transacción, reemplaza | payload completo | sin refresco del VM; sin mutex |
| Depuración | `DemoDataSeeder:68` | — | — | — |

Código muerto: `AugeRepository.clearManualBatteryOverrides:71` e `importBackupSlice:159`.

### A4. Snapshot del programa frente a Home (Q3)

| Aspecto | `ProgramRepository.computeAugeSnapshot` (RP:1154–1226) | `AugeViewModel.recompute` (VM:188–405) |
|---|---|---|
| Bienestar | `getTodayWellbeing() ?: getActiveWellbeingWithManualOverrides()` (RP:1158) | override si hoy no tiene ninguno (VM:192–205) |
| V1 a pilares | no | sí (VM:212–214) |
| Índice de catálogo | `exerciseCatalogSnapshot().associateBy{id}` (RP:1163), sin claves V2 | `catalogExerciseIndex()` = id + V2 (`ExerciseDatabase.kt:72, 117`) |
| Reloj | `currentTimeMillis` (RP:1197); motores sin `nowOverride` | un único `evaluationNowMs` (VM:225, 239, 253, 261, 299–311) |
| Cobertura | no | `computeSnapshotCoverage` (VM:285) |
| Errores | `runCatching{}.getOrNull()` (RP:1156, 1225): señales a null o 0 en silencio | sin captura: propaga (P-09) |
| Constantes | `overtrained = emptyList()`, `consecutiveHighE1rm = 0` (RP:1219–1220) | calculados |
| `cumulativeFatigue` | `cumFatigue.takeIf{>0} ?: batteries.cnc` (RP:1215) | suma sin fallback (VM:266–277) |
| Llamadas | 2 por finalización (RP:967–968) | 1 por recálculo |
| Caché | cruda y sin el aprendizaje del último log | remapeada (VM:209–211) |

El remapeo de multiplicadores no cambia resultados, porque los motores remapean internamente (`AugeFatigueEngine:681, 847`; `AugeRecoveryEngine:714`).

Acciones del programa que dependen del snapshot (P-07): `ProgramAutoregulationEngine.buildProposals` (readiness, `cumulativeFatigue`, nivel de aviso), solo con `autoregulationMode ≠ OFF` (default OFF, `Program.kt:65`). `BlockTransitionEngine.evaluate` usa AUGE solo si `augeAutoDeload` está activo (default false, `Settings.kt:299`, `AugeFatigueEngine:1058`). Su EMA de estrés (`BlockTransitionEngine:142`) no depende de AUGE.

### A5. Hilos y frecuencia (Q5)

| Función | Main | Default | IO |
|---|---|---|---|
| `recompute` | :296–405 (monitores, orden, publicación, telemetría) | :226–294 | lecturas del repositorio |
| `learnFromLatestFinishedLog` | :885–904 (`observations`, `applyToCache`) | :874 (`predictedStartBatteries`) | :869, :904 |
| `learnFromManualAdjustment` | todo, incluido `calculateCompletedSessionDrain` (:748) | — | :742, :834 |
| `computePostSessionPreview` | :488–525 | :526–575 | :502–518 |
| `finalizeWorkout` | — | — | IO+NonCancellable con `ongoingWorkoutMutex` (RP:856–857) |

Frecuencia de `recompute`: arranque (1), cada emisión de historial o ajustes, temporizador (288 al día), `refresh` desde `WorkoutScreen:1774`, `saveWellbeing`, `applyManualBatteries` y `dismissAdvisory`. `refresh()` no se cancela (VM:471–475): en el estado de P-01 cada tick deja una corrutina colgada.

### A6. Fidelidad de persistencia (Q6)

| Entidad | Ida y vuelta | Pérdidas |
|---|---|---|
| `DailyWellbeingLog` | completa (`Entities:190`) | ver A3; decodificación fallida → null (`Entities:191–193`) |
| `AugeAdaptiveCache` | completa; migración solo al leer (`Entities:237`), no persistida hasta la siguiente escritura | decodificación fallida → defaults (P-11) |
| `PostSessionFeedback` | completa | no se escribe en producción (P-14) |
| `SleepLog` básico | derivado del extendido (`AugeModels:405–410`) | `bedTime`, `quality`, `awakenings`, `notes` no entran |
| `WorkoutLog` (V2, ringStart, stress) | completa (`WorkoutLog.kt:19–20, 56`) | — |

`dbJson` (`Entities:16–20`): `ignoreUnknownKeys`, `encodeDefaults` y `coerceInputValues`. No fija `allowSpecialFloatingPointValues`.

Migraciones que tocan `auge_*`: 11→12 crea `auge_sleep_extended` (`KpknDatabase:343–355`); 18→19 crea `auge_adaptive_cache` (:469–479); 19→20 deduplica `auge_wellbeing` por fecha conservando el `rowid` mayor (:485–491, **borra filas antiguas**, ya aplicada en instalaciones previas) y crea el índice único (:493–495). Ninguna migración posterior toca `auge_*`.

## B. Hallazgos previos

| ID | Estado | Evidencia |
|---|---|---|
| H25 | ABIERTO | `lastSession` sigue siendo el log de fecha máxima (VM:730–736); el override V2 mantiene `sourceSessionId = null` (VM:629). |
| H31 | ABIERTO, sin UI, agravado | Sin llamadas desde UI (VM:1133–1156). `resetAdaptiveCache` dispara el interbloqueo de P-01 porque el default tiene `lastAdvisoryDay = null` (`AugeAdaptiveModels:25`). `clearManualBatteryOverrides` solo limpia la fila de hoy (`AugeRepository:71–83`). |
| H32 | CAMBIÓ-DE-FORMA | La readiness escribe V1 (`WSOH:91–97`) solo con los músculos editados (`WorkoutReadinessSheet:320–333`). El merge trata un cambio V1 como declaración y **elimina** la ancla V2 de ese pilar (`WellbeingSaveMerge:27–30`). V1 no tiene ancla por músculo (`AugeModels:26`, `Map<String,Int>`). Ya no son «dos semánticas»: la última escritura borra la otra. |
| B12 | ARREGLADO | Filtro `ms > 0` y ≤ +1 h (VM:730–733, 850), tope de 14 días (VM:59, 740), `AxialLoadMonitor:35` y `SystemicLoadMonitor:29`. No verificado `recentSessionCount` (`AugeRecoveryEngine:344`). |

H33 se confirma por otra vía: no hay escrituras de feedback en producción (P-14).

## C. Hallazgos nuevos

### P-01 [P0 · bug] Interbloqueo del mutex al recalcular dentro de una sección crítica
- `VM:395–396`: `if (cacheToPersist != null && generation == recomputeGeneration.get()) {` / `augeWriteMutex.lock()`.
- Secuencia con cierre que edita un anillo: (1) RP:1046 publica el log y R1 queda en vuelo. (2) `applyManualBatteries` toma el mutex (:611), escribe la fila (:664) y aprende (:684); **esas escrituras sí persisten**. (3) R2 (:697) sube la generación (:189) y R1 sale en :338 sin persistir. (4) `cacheToPersist ≠ null` si el cierre cambió un nivel de aviso: la tercera sesión axial en 4 días ya basta (`LoadAdvisoryEngine:121`, `extraWatch` → WATCH, aunque el historial sea insuficiente), o si es el primer recálculo UTC del día (`LoadAdvisoryEngine:114, 157`). (5) R2 llama a `lock()` en :396 y queda suspendida con el mutex tomado hasta destruir la VM.
- Efecto: después, `saveWellbeing` (la primera línea del lock es :412), `applyManualBatteries`, `dismissAdvisory` y `markModelUpdateNoticeShown` se cuelgan sin error. La readiness y los ajustes de anillo no se persisten, aunque la pantalla se cierre como si se hubiera guardado. Cada tick de 5 min deja otra corrutina colgada en :396, porque el caché nunca se persiste. El snapshot sigue publicándose (:347), así que el fallo es silencioso.
- Verificación propuesta (no ejecutada): test JVM con `runTest`, `Dispatchers.setMain` y repositorio falso; forzar `cacheToPersist ≠ null` dentro de `saveWellbeing` y medir con `withTimeout`. En dispositivo: tercera sesión axial en 4 días, editar un anillo al cerrar y luego readiness; comprobar que `auge_wellbeing` no cambia.
- Arreglo: no tomar el mutex dentro de `recompute`. Pasar `holdsMutex: Boolean` y saltar :396–403 cuando sea `true`.

### P-02 [P0 · bug] Los anillos dictados por voz no llegan a AUGE
- `WorkoutViewModel.kt:6448–6499`: `finalNeuralBattery = state.voiceFinalNeural` (:6481), `finalSpinalBattery` (:6482) y `onComplete = { voiceController.speakSessionSaved() }` (:6492–6494).
- `applyManualBatteries` solo se llama desde `WorkoutScreen:1747`, el `onComplete` de la hoja. `finalNeuralBattery` solo se lee en `WorkoutScreen:1740–1760`. El controlador no lee `finalNeuralBattery`, `finalSpinalBattery` ni `neuralEdited`.
- Escenario: con la hoja abierta, el usuario dice «energía 40» y luego «sesión terminada». `WorkoutVoiceCommandHandler:801` llama a `finalizeVoiceSession`. El log se guarda; Home sigue con la energía automática y el dato dictado no queda en ningún sitio.
- Efecto: pérdida silenciosa de un dato del usuario.
- Arreglo: que `finalizeVoiceSession` ejecute el mismo bloque de `WorkoutScreen:1745–1775` (`applyManualBatteries` o `refresh`) antes de `speakSessionSaved`.

### P-03 [P1 · bug, condicionado] La readiness tras medianoche reescribe la fila de ayer
- `WorkoutScreen:352–354`: `produceState` lee `getTodayWellbeing()` una sola vez. `WorkoutSessionOverlaysHost:77–78`: `id = todayWellbeing?.id ?: UUID…` y `date = LocalDate.now().toString()`.
- Escenario: la pantalla se compuso el día D con una fila existente (id_D). La readiness se confirma el D+1. El log lleva id_D y fecha D+1. `storedToday` es null (VM:429) y `saveWellbeingLog` no encuentra fila para D+1 (`AugeRepository:36–44`). El `upsert` por PK id_D **cambia la fecha de la fila de ayer a D+1**. Además, las copias de overrides de D cuentan como ajustes nuevos (VM:415–426) con ancla = ahora.
- Efecto: se pierde la fila de D y sus overrides reviven durante todo el D+1.
- Arreglo: leer la fila al guardar (no `produceState`) y reutilizar `id` solo si `date == hoy`.
- Verificación: no hay reloj inyectable en VM ni en `AugeRepository` (`LocalDate.now()` en :47 y :51–53).

### P-04 [P1 · procedencia] El motor usa el registro de ayer entero y lo elige todo-o-nada
- `VM:192–205` y `VM:502–515`: `if (overrideWellbeing != null && !todayHasManualOverrides) overrideWellbeing`. `AugeRepository:50–68` elige una fila completa (`firstOrNull`), no canales.
- Efecto (a): mientras ayer tenga un override vivo (≤18 h tras el ancla) y hoy no tenga ninguno, el motor usa **todo** el registro de ayer, incluido `stressLevel` (`AugeRecoveryEngine:1281`), `sleepQuality`, `doms`, `preWorkoutDiscomforts` y notas. El check-in de hoy se ignora. Efecto (b): cuando hoy tiene **un** override, los de ayer (energía, columna) desaparecen del cálculo.
- Escenario: D 22:00 cierre con energía 40 (ancla 22:00). D+1 08:00 toca el pecho en readiness: hoy ya tiene V1, la energía vuelve al valor automático.
- Arreglo mínimo: combinar por canal con su ancla en vez de por fila.

### P-05 [P1 · procedencia] Se pierden `source` y `capturedFields` al guardar
- `VM:639–663` (`applyManualBatteries`) construye la fila sin `source` ni `capturedFields`; quedan en `DAILY_CHECK_IN` y `∅`. `WellbeingSaveMerge:38–46` toma `source` del entrante (readiness → `DAILY_CHECK_IN`).
- `RingsChannelCoverage.kt:226–243` (`declaredCheckInChannels`) usa `capturedFields` y `source == ONBOARDING_INITIAL`. `VM:1199` lo pasa a `RingsCoverage.fromEngineInputs`.
- Escenario: alta con energía y músculo declarados (fila `ONBOARDING_INITIAL`); cierre con un toque en pecho. Esos canales dejan de contarse como declarados el mismo día.
- Efecto: la etiqueta de procedencia de Home cambia. El efecto exacto en `fromEngineInputs` no está leído.
- Arreglo: en `VM:639`, `source = base?.source ?: DAILY_CHECK_IN` y `capturedFields = base?.capturedFields.orEmpty()`; en `WellbeingSaveMerge:38`, `source = existing.source`.

### P-06 [P1 · inconsistencia] El snapshot del programa no coincide con Home
- Diferencias de A4 con efecto concreto:
  - `RP:1163` no contiene las claves V2 (`v2ConfigurationLookupCache`, `ExerciseDatabase.kt:72, 94, 254, 265, 274`). `resolveCatalogExerciseInfoInIndex` busca por id (`ExerciseDatabase.kt:182–198`). Un ejercicio referenciado por `catalogConfigurationId` puede resolverse en Home y no en la transición. Efecto plausible: el readiness de la autorregulación cuenta 0 para ese ejercicio. HIPÓTESIS, no ejecutada.
  - `RP:1156, 1225`: `runCatching…getOrNull()` deja señales a null o 0 sin registro.
- Arreglo: usar `catalogExerciseIndex()`, la misma lógica de bienestar que VM:192–205 y un `nowMs` único.

### P-07 [P1 · matemática] `cumulativeFatigue` es una suma de 14 días contra un umbral de 75
- Estrés de sesión = 0.45·cns + 0.25·músculo + 0.30·spinal (`AugeUtils.kt:25–27`). RP:1197–1207 y VM:266–277 suman 14 días. `ProgramAutoregulationEngine.kt:933`: `val autoDeload = readiness != null && fatigue != null && readiness < 40 && fatigue > 75.0`. `BlockTransitionEngine.kt:142` usa 75 sobre una EMA por sesión (α = 0.17, RP:1085). Son escalas distintas con el mismo umbral.
- Simulación con los supuestos del brief (3×/semana, estrés 35–50): unas 6 sesiones en 14 días, suma 210–300. `> 75` ya se cumple con 2 sesiones de estrés ≥38. **`autoDeload` equivale a `readiness < 40`.**
- Efecto: con `AUTO`, `INSERT_DELOAD` se aplica sin confirmación (`ProgramProgressEngine:1141` → `ProgramAutoregulationEngine:225–234, 912–919`). Con `PROPOSE` queda pendiente (:199–210). Por defecto `OFF`.
- Fallback `batteries.cnc` (RP:1215): una batería de 100 (fresco) entra como fatiga. Solo es alcanzable sin sesiones efectivas en 14 días (HIPÓTESIS: sesión solo de cardio sin `cardioDetails`).
- Arreglo: media por sesión o ratio frente a línea base; o documentar el umbral como «suma de 14 días».

### P-08 [P1 · rendimiento] Monitores y capacidad sobre el historial completo, en Main
- En Main: `VM:299` (`AxialLoadMonitor`) calcula `axialUnits` para **todos** los logs (`AxialLoadMonitor:33–39`) y filtra a 28 días después (:40). `VM:300` (`SystemicLoadMonitor`) calcula `calculateCompletedSessionDrain` para todos (`SystemicLoadMonitor:27–39`). `VM:885` (`PerformanceTauLearner.observations`) corre en Main.
- Historial completo sin recortar: `AugeRecoveryEngine:964–983` llama 3 veces a `calculateUserWorkCapacity`. `AugeMuscleCapacityEngine:38` recalcula `MuscularSessionImpactEngine.evaluate` por cada log de 35 días que no tenga la clave del músculo en `perMuscle`.
- Cuadrático: `AugeRecoveryEngine:989–994` hace `history.filter { logDateMs(it) <= logTime }` dentro de `recentLogs.forEach`; :997 crea un `SimpleDateFormat` por feedback y log.
- Estimación, no medida (500 logs × 25 series, ~13 músculos según el informe previo): unas 25–35 pasadas completas por recálculo, unas 12.5k evaluaciones de serie en cada monitor y hasta ~200 evaluaciones de sesión por capacidad. Con 288 recálculos diarios por temporizador (`VM:173–178`), el orden es de ~8·10⁶ evaluaciones de serie al día solo por el temporizador. `previewPostSessionBatteries` llama a `calculateGlobalBatteries` tres veces (`AugeRecoveryEngine:1815, 1840, 1900`).
- Arreglo: envolver los monitores en `withContext(Dispatchers.Default)`, prefiltrar a 28 días antes de `axialUnits` y `calculateCompletedSessionDrain`, y no recalcular por temporizador sin cambios.

### P-09 [P1 · robustez] `recompute` no captura excepciones
- `VM:188–405` no tiene `try`. Un fallo en un motor termina el colector `collectLatest` de `init` (`VM:163–169`) y deja de recalcular hasta recrear la VM. Sin `CoroutineExceptionHandler`, la excepción llega al handler por defecto: `NutritionCrashHook:23–30` delega en el previo, y el propio repositorio lo documenta (`ProgramRepository:1489–1491`: «una excepción sin capturar tumbaría el proceso»). `isLoading` queda en `true` (`VM:191`).
- Arreglo: `try/catch` que registre con `KpknDiagnosticLogger.exception`, deje `isLoading = false` y reenvíe `CancellationException`.

### P-10 [P1 · procedencia] Ancla por fila y aprendizaje alimentado con valores de readiness
- `VM:656–659`: un toque en cualquier canal reestampa la fila entera. `VM:1028–1036` (`manualTouchesFromFinish`): `energy = finishAnchor && manualNeuralBattery != null`. Si la energía vino de readiness horas antes, cuenta como «tocada al cierre».
- `VM:1004–1020` (`wellbeingForStartPrediction`): con ancla de cierre, anula **todos** los canales, también los de readiness, para la predicción de inicio.
- Efecto: el aprendizaje recibe procedencia equivocada. La matemática del aprendizaje queda fuera de este alcance.
- Arreglo: ancla por canal y `manualTouchesFromFinish` leyendo la ancla de ese canal.

### P-11 [P2 · persistencia] Decodificación silenciosa, NaN y valor inventado
- `Entities:224–238`: `toAdaptiveCache()` devuelve `AugeAdaptiveCache()` ante cualquier fallo (:238), sin diagnóstico. La siguiente escritura persiste el default. `Entities:191–193` y `:212–214` devuelven null en silencio.
- `dbJson` no fija `allowSpecialFloatingPointValues`: un `Double` NaN o ∞ en el caché hace fallar `saveAdaptiveCache` (`AugeRepository:150`) dentro de `recompute` (VM:399), sin captura (P-09). HIPÓTESIS: hoy no hay evidencia de que algún motor lo produzca; hay guardas (`PerformanceTauLearner:438`).
- `VM:630`: `automaticBatteryAtAnchor = predictedMuscleBatteries[muscle] ?: 100` persiste un 100 inventado. Ningún motor lo lee (solo `AugeModels:117` lo declara), así que es fidelidad de datos, no efecto en anillos.

### P-12 [P2 · robustez] `learnFromLatestFinishedLog` traga la cancelación
- `VM:864`: `return runCatching {`. `collectLatest` cancela recálculos en vuelo; la `CancellationException` se captura en :926–943, se registra como `tau_from_performance_failed` (VM:932) y se devuelve la caché sin relanzar. Resultado: eventos de fallo falsos.
- Arreglo: `catch (e: CancellationException) { throw e }` antes de `getOrElse`.

### P-13 [P2 · inconsistencia] Cierre por voz sin `ringStartSnapshot` ni instante
- `WorkoutViewModel:6468–6487`: no pasa `ringStartSnapshot`, `completionInstantIso` ni `completedSetInputHash`. El controlador deja `ringStartSnapshot` a null (:421) y usa `Instant.now()` (:225–227). `AxialLoadMonitor:58`, `SystemicLoadMonitor:46` y `VM:320` excluyen ese log de tendencias y línea base.

### P-14 [P2 · código muerto e inertes]
- Sin llamadores: `savePostSessionFeedback` (`VM:463–468`, `AugeRepository:120–122`), `mapWorkoutToPostSessionFeedback` (`WorkoutFeedbackModels:309`), `clearManualBatteryOverrides` (VM:1133), `resetAdaptiveCache` (VM:1146, con interbloqueo), `importBackupSlice` (`AugeRepository:159`).
- Consecuencia: `auge_feedback` solo se llena por restauración. Todo lo que depende de feedbacks está inerte en producción (confirma H33).
- Inertes en el programa: `consecutiveHighE1rm = 0` (RP:1220) → `PROMOTE_TM` nunca (`ProgramAutoregulationEngine:964`); `overtrained = emptyList()` (RP:1219); `repeatedJointPain` (RP:1221–1223) sin consumidor (`ProgramAutoregulationEngine:987–990`).

### P-15 [P2 · concurrencia] La restauración escribe fuera del mutex y puede borrar el caché
- `SettingsJsonBackup:229–234` escribe por DAO sin `augeWriteMutex`, sin subir generación y sin refrescar el VM. Un recálculo en vuelo puede persistir su caché previo encima (`VM:399`).
- `BackupTableManifest:85, 99–119`: `auge_adaptive_cache` es tabla core; si el respaldo no trae caché (`SettingsJsonBackup:234`, `?.let`), el caché queda en defaults.

### P-16 [P2 · inconsistencia] Día del aviso en UTC, día del bienestar en local
- `LoadAdvisoryEngine:114` usa `ZoneOffset.UTC`. El bienestar usa `LocalDate.now()` (`AugeRepository:47, 51`; `VM:641`; `WSOH:78`).
- Efecto: en UTC+2 el día del aviso cambia a las 02:00 locales, y `allowDrop = !sameDay` (`LoadAdvisoryEngine:116, 125`) se reinicia entonces.

### P-17 [P2 · observabilidad] Lo que impide el backtest (Q7)
- Eventos «auge»: `snapshot_published` y `auge_computed` (VM:364, 376; batería por canal, `overallScore`, `durationMs`, `generation`); `finish_auto_preview` (VM:577; solo `muscularBattery`); `manual_override_applied` (VM:667; solo `hasAutomaticBaseline`); `tau_from_performance[_failed]` (VM:906, 932); `muscular_calibration_reset_v2` (`AugeRepository:140`, CRITICAL); `session_input`, `session_impact` y `post_persisted_auto` (Controller:268–300, 509–520).
- Faltan: predicho frente a reportado para energía y columna; valores manuales por canal; niveles de aviso; cobertura; decisiones de omitir el aprendizaje manual (`learnFromManualAdjustment` retorna sin evento).
- Persistencia: `auge_wellbeing` tiene una fila por fecha y sobrescribe las escrituras del mismo día (`Entities:188`). `auge_adaptive_cache` es una sola fila sin historial. El predicho de energía y columna no se guarda. `ringStartSnapshot` y `muscularImpactV2` sí están en el log.
- Telemetría: rotación a 30 días, 50 MB y 64 ficheros (`Logger:100–103`); no entra en el respaldo JSON.
- Repetibilidad: `System.currentTimeMillis()` (VM:225) y `LocalDate.now()` (`AugeRepository:47`) sin reloj inyectable; `AugeClock` solo lo usan los motores (`AugeClock.kt:8–10`).
- PII: `sanitizeText` solo redacta patrones de API key (`Logger:900–904`); `exceptionMessage` pasa literal (VM:938).

## D. Lo que está sano y no debe tocarse
- Guardas de generación (`VM:338`, `:398`) descartan resultados obsoletos (salvo P-01).
- Cierre en una transacción Room (`RP:1026–1037`) con identidad determinista (`Controller:217–222`) y reintento congelado (`RP:874–887`). Guardas de doble cierre (`Controller:113–119`, `WorkoutViewModel:6450`).
- `mergeWellbeingForSave` (`WellbeingSaveMerge:20–47`) conserva anclas, V2 no declaradas y la unión de `capturedFields`.
- `dbJson` con `ignoreUnknownKeys` y `encodeDefaults`; índice único de fecha (`Entities:188`); sin incoherencias visibles en las sentencias de migración (no comparado con `schemas/28.json`).
- Importación transaccional con snapshot de rollback (`SettingsViewModel:247–283`) y compuerta de versión (`SettingsJsonBackup:170–172`).
- Telemetría: claves sensibles redactadas (`Logger:894–898`) y NaN convertido a null (`Logger:881`).
- Histéresis de avisos (`LoadAdvisoryEngine:116, 125`) y filtros de fecha 0 (B12).

## E. Preguntas abiertas y no verificado
1. P-01 no se ha reproducido en dispositivo ni con test. La frecuencia real depende de cuántos cierres cambian un nivel de aviso.
2. P-05: efecto exacto en `RingsCoverage.fromEngineInputs` (no leído).
3. P-03: cómo resuelve Room `@Upsert` una fila del mismo día que no decodifica (`AugeRepository:36–44`): ¿excepción o no-op?
4. Arranque en frío: `_history` empieza vacío (RP:833) y el colector recalcula con historial vacío (VM:163–167). Posible publicación con cobertura «sin datos» antes del bootstrap. Verificable con `snapshot_published.historyCount = 0`.
5. Esquema base: `auge_wellbeing`, `auge_sleep` y `auge_feedback` no tienen `CREATE TABLE` en las migraciones listadas. Solo hay JSON de esquema para las versiones 20–28.
6. Coste: `getPerMuscleBatteries`, `calculateSystemicFatigue` y `AugeTtcEngine` no contados pasada a pasada. Las cifras de P-08 son estimaciones.
7. P-06: `resolveCatalogExerciseInfoInIndex` resuelve por id con redirecciones, pero no se auditaron los accesos directos `exerciseDb[…]`.
8. `WorkoutFinishHost.kt` (1347 líneas) solo se leyó en 228–243, 300–316 y 396–441.
9. Otras áreas, ya auditadas, una línea: `learnFromManualAdjustment` sin idempotencia por logId (VM:712–835).
10. `addWorkoutLog` publica antes de persistir (RP:838–839), pero solo lo usa el seeder de depuración.

## F. Cobertura real

| Archivo | Leído | No leído o parcial |
|---|---|---|
| `screens/auge/AugeViewModel.kt` | 1–1213 (completo) | — |
| `data/repository/AugeRepository.kt` | 1–187 (completo) | — |
| `data/models/AugeModels.kt` | 1–489 (completo) | — |
| `data/models/AugeAdaptiveModels.kt` | 1–40 (completo) | — |
| `data/models/WorkoutLog.kt` | 1–75 | 76–402 (fuera de alcance) |
| `data/db/Entities.kt` | 14–27, 186–242, grep del resto | resto de entidades |
| `data/db/Daos.kt` | 200–278 (AugeDao) | resto |
| `data/db/KpknDatabase.kt` | 20–100, 340–400, 466–534, grep | 400–466 (sin referencias a `auge_*`) |
| `data/db/BackupTableManifest.kt` | 1–128 (completo) | — |
| `data/settings/SettingsJsonBackup.kt` | 1–280 | 281–417 (fuera de AUGE) |
| `data/backup/AppBootstrap.kt` | 1–38 (completo) | — |
| `data/diagnostics/KpknDiagnosticLogger.kt` | 30–150, 300–500, 840–915 | escritor y rotación (150–300, 500–840) |
| `domain/auge/AugeAdaptiveCacheMigration.kt` | 1–38 (completo) | — |
| `domain/auge/AugeClock.kt` | 1–10 (completo) | — |
| `domain/auge/AxialLoadMonitor.kt`, `SystemicLoadMonitor.kt` | completos | — |
| `domain/auge/WellbeingSaveMerge.kt` | 1–50 (completo) | — |
| `domain/auge/LoadAdvisoryEngine.kt` | 60–191 | 1–59, 192–281 |
| `domain/auge/AugeRecoveryEngine.kt` | 344, 923–1008, 1269–1308, 1815–1930 (grep) | resto |
| `domain/auge/AugeMuscleCapacityEngine.kt` | 14–49 | resto |
| `domain/auge/AugeUtils.kt` | 25–27, 74–93 | resto |
| `domain/auge/AugeFatigueEngine.kt` | 681, 847, 1053–1065 (grep y lectura) | resto |
| `domain/auge/PerformanceTauLearner.kt` | 272–293, 431, 438 | resto |
| `screens/workout/WorkoutFinishController.kt` | 1–650 | 651–857 |
| `screens/workout/WorkoutFinishHost.kt` | 228–243, 300–316, 396–441 (grep) | resto |
| `screens/workout/WorkoutScreen.kt` | 346–391, 1380–1457, 1636–1786 | resto |
| `screens/workout/WorkoutSessionOverlaysHost.kt` | 1–307 (completo) | — |
| `screens/workout/WorkoutViewModel.kt` | 155–168, 5664–5698, 6420–6500 | resto |
| `screens/workout/WorkoutVoiceCommandHandler.kt` | 794–806, 1336–1352 | resto |
| `screens/workout/components/WorkoutReadinessSheet.kt` | 296–335 | resto |
| `data/repository/ProgramRepository.kt` | 826–1237, 1484–1496, 1984–2033 | resto |
| `domain/training/ProgramAutoregulationEngine.kt` | 140–238, 876–1000 | resto |
| `domain/training/ProgramProgressEngine.kt` | 1086–1149 | resto |
| `domain/training/BlockTransitionEngine.kt` | 28–202 | 203–351 |
| `domain/onboarding/RingsChannelCoverage.kt` | 212–243 | resto |
| `data/onboarding/SetupPersistence.kt` | 262–283, 424–452 | resto |
| `data/exercises/ExerciseDatabase.kt` | 27–117, 182–198, 254–276 | resto |
| `telemetry/nutrition/NutritionCrashHook.kt` | 14–36 | — |
| Informes previos (3) | completos | — |

## G. Observaciones de sueño (decisión de producto: solo registro)
- La readiness ya no escribe `sleepHours ?: 7.5`; copia `todayWellbeing?.sleepHours` (`WorkoutSessionOverlaysHost:83`). B13 sigue vigente.
- El respaldo exporta `getLastNSleepLogs(30)` de la tabla básica (`SettingsViewModel:220`). Filas básicas sin extendida y fuera de las 30 más recientes quedan fuera del respaldo.
- `SleepLog` básico se deriva del extendido con `ZoneId.systemDefault()` (`AugeModels:413–420`): `endTime` depende de la zona del dispositivo al guardar.
- `sleepMult` sigue en el motor (H4); no re-verificado en este alcance.
