# VT5b · Trabajo en segundo plano: Worker, cola, puerta de cámara, repositorio de análisis y reconciliación

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt5b`, desde `feat/video-technique` con VT5a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.7 (bloque `data/media/technique`) y §9 punto 4;
  - el código de VT5a;
  - `data/repository/WorkoutMediaRepository.kt` (`submitFinalizedVideoCapture`, `submitUriCapture`, `MediaPersistenceOwner`);
  - `data/backup/AppBootstrap.kt`;
  - `AndroidManifest.xml`: VT-A ya fusionó `SystemForegroundService`.
- **Logs:** `C:\kw\logs\vt-5b-<n>.log`.

## Paso 0: verificación de integración (lo primero, antes de editar)
`feat/video-technique` acaba de recibir dos cosas:
- la restauración de la sesión en vivo ORIGINAL de la PR11, desde `feat/sessions-v1` (U7, D28);
- VT5a (Room v29).

Es la primera compilación de esa combinación. Tu PRIMERA corrida, por `gradle_slot.sh`, es:
`:app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.data.db.*" --tests "com.example.kpkn.data.media.*" --tests "com.example.kpkn.data.models.*VideoAnalysis*" --tests "com.example.kpkn.data.repository.*Media*" --tests "com.example.kpkn.screens.workout.*Media*"`
- Mientras espera en la cola, lee y planifica, pero no edites hasta que termine.
- Si falla algo de la sesión en vivo restaurada (código de `sessions-v1`), NO lo arregles: anótalo con el error exacto y sigue si tu parte compila.

## Heredado de VT5a (ya integrado; los nombres REALES mandan)
- **DAO:** `VideoAnalysisEntity` y `VideoAnalysisDao`: `upsert`, `getByMedia`, `observeByMedia`, `observeBySessionKey`, `observeExerciseSummaries` (cuenta solo DONE), `observeTrend`, `observeConfigurationPoints`, `updateProgress`, `delete`, `allIds` y `count`.
- **Modelos:** `VideoAnalysisStatus`, `VideoAnalysisSummary` (con `pending`), `SetTechniqueReport.toSummary(...)` (con 3 opcionales al final), y `techniqueExerciseKey`, `techniqueConfigurationKey` y `techniqueSetKey`.
- **Sidecars:** `AnalysisSidecar` y `TechniqueSidecars`, con la escritura atómica y la lectura y escritura de `<id>.analysis.json`.
- **`WorkoutMediaRepository`:** `setPoseTrackPath`, `setSetSnapshot`, `syncSetSnapshots`, `assignExercise` y un `delete` que ya borra los sidecars y la fila de análisis.
- **Ajustes:** `TechniqueVideoSettings`, al final de `Settings`.
- **Orden obligatorio al terminar bien:** primero `<id>.analysis.json` y DESPUÉS la fila. La restauración de una copia vacía `video_analysis` por la cascada de `workout_media`: el sidecar es lo que permite reconstruirla (`reconcile`). Nunca REPLACE sobre `workout_media`.

## Archivos permitidos
- **Nuevos:**
  - `data/media/technique/TechniqueAnalyzer.kt`: el contrato de §3.7 más `TechniqueAnalyzerProvider`, un objeto que hoy devuelve un analizador «aún no disponible» (resultado `Failed("analyzer_unavailable", transient = false)`) y que VT4c sustituirá por el real;
  - `data/work/TechniqueAnalysisWorker.kt`;
  - `data/work/TechniqueAnalysisScheduler.kt`;
  - `data/work/AnalysisGate.kt`;
  - `data/repository/VideoAnalysisRepository.kt`.
- **Modificados:**
  - `data/repository/WorkoutMediaRepository.kt`: encolar al guardar un vídeo y cancelar al borrar;
  - `data/backup/AppBootstrap.kt`: una línea para `reconcile()`.
- **Si hace falta**, recursos de texto para la notificación de primer plano: `res/values/strings.xml`, solo añadir.
- **Pruebas:** en `src/test/java/com/example/kpkn/data/{work,repository}/`.

## Qué haces
1. **`AnalysisGate`** (objeto):
   - `val mutex = Mutex()`: un análisis cada vez;
   - `val cameraBusy = MutableStateFlow(false)`: VT8a lo pone a true mientras se graba;
   - `suspend fun awaitClear(context)`: espera mientras `cameraBusy` sea true o el estado térmico sea ≥ SEVERE (`PowerManager.currentThermalStatus`, API 29+).
2. **`TechniqueAnalysisWorker`** (`CoroutineWorker`). Entrada: `mediaId` y `mode` (FULL, RECOMPUTE o PLATE_ONLY).
   - **Secuencia:**
     1. Si el análisis está desactivado en los ajustes, o el medio ya no existe → `Result.success()`.
     2. `AnalysisGate.mutex.withLock`.
     3. Fila en RUNNING.
     4. `AnalysisGate.awaitClear`.
     5. Arma un `TechniqueAnalysisRequest` con los datos del medio:
        - el ejercicio sale de sus ids de catálogo; el `ExerciseRef` lo completará VT4c con su resolvedor de catálogo, y aquí basta lo que haya en el medio;
        - la carga, de `weightKg`;
        - el disco, de los ajustes y del sidecar de captura;
        - la altura del usuario, de `UserVitals.height`.
     6. `TechniqueAnalyzerProvider.get(appContext).analyze(request) { p -> … }`.
   - **Progreso:** a Room como mucho 1 vez por segundo, y solo si cambia ≥ 5 %.
   - **Según el resultado:**
     - `Done` → escribe `<id>.analysis.json` (fuente de verdad) y, en una transacción, comprueba que el medio sigue y hace upsert de la fila DONE;
     - `Unsupported` → UNSUPPORTED;
     - `NeedsProfile` → NEEDS_PROFILE;
     - `Failed` transitorio → `Result.retry()`, con backoff exponencial desde 30 s;
     - `Failed` con 3 intentos o no transitorio → FAILED con `errorCode`.
   - **`getForegroundInfo()`:** notificación «Analizando la técnica del vídeo» con progreso.
     - Tipo MEDIA_PROCESSING en API 35+ y DATA_SYNC en 29–34.
     - Canal propio, con importancia baja.
   - **`setForeground`:** solo con vídeos de más de 45 s o importados, dentro de `try/catch`.
3. **`TechniqueAnalysisScheduler`:**
   - `uniqueName(id) = "technique-analysis:$id"`;
   - `enqueue(context, mediaId, mode, userInitiated)`:
     - FULL: expedited con `RUN_AS_NON_EXPEDITED_WORK_REQUEST` y política KEEP;
     - RECOMPUTE: con `setRequiresBatteryNotLow(true)` y REPLACE;
     - PLATE_ONLY: REPLACE;
     - antes de encolar, la fila pasa a QUEUED;
   - `cancel(context, mediaId)`;
   - `suspend fun reconcile(context)`:
     - por cada `<id>.analysis.json` sin fila (p. ej. tras restaurar una copia), reconstruye la fila;
     - borra las filas sin medio y los sidecars huérfanos;
     - las filas con una `engineVersion` menor que la actual quedan para RECOMPUTE perezoso, que se lanza al abrir el análisis (no aquí).
4. **`VideoAnalysisRepository`** (singleton al estilo de `WorkoutMediaRepository.init/getInstance`):
   - Flows de VT5a, mapeados a `VideoAnalysisSummary`;
   - `requestAnalysis(mediaId, mode)`;
   - `reanalyze(mediaId)`;
   - `setPlateSeed(mediaId, seed, diameterMm)`: guarda la semilla en el sidecar de captura o en un sidecar `<id>.plate.json` (elige y documenta) y encola PLATE_ONLY;
   - `setUserProfileOverride(mediaId, profileId)` para ejercicios propios: lo guarda en el sidecar de captura y encola FULL.
5. **Telemetría (D27, decisión del usuario U9: «lo que NO AFECTE LA FUNCIONALIDAD»).** VT4b midió que `tasks-core` de MediaPipe envía métricas de uso a `firebaselogging.googleapis.com` mediante datatransport.
   - **Cambio:** en `src/main/AndroidManifest.xml`, dentro de `<application>`, declara deshabilitados los dos componentes del planificador de datatransport:
     ```xml
     <service android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
         android:enabled="false" tools:replace="android:enabled" />
     <receiver android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"
         android:enabled="false" tools:replace="android:enabled" />
     ```
     Comprueba en el manifiesto fusionado (`build/intermediates/merged_manifests/...`) que quedan con `enabled=false` y con el resto de atributos intactos.
   - **Condición: no afecta a la funcionalidad.** Compruébalo en el EMULADOR con el APK `.dbg` y `emu_run.py`, sin `adb` suelto:
     1. `TechniqueBenchActivity`, modo `telemetry_check`, SIN apagar nada en tiempo de ejecución (el manifiesto ya lo hace). Esperado: 0 B a firebaselogging en ≥ 110 s, ninguna caída y la pose de MediaPipe funcionando.
     2. El OCR del importador sigue funcionando con ML Kit: usa el arnés `ImportOcrHarnessActivity` como diga su KDoc, y que reconozca texto como antes.
     3. Arranque en frío dos veces seguidas: sin caídas y sin `FATAL` en `--crash-log`.
   - **Si algo de la funcionalidad cambia,** revierte el cambio del manifiesto y anótalo. Entonces queda la opción «dejarla y avisar» para el texto de privacidad.
   - Archivo permitido para esto: `src/main/AndroidManifest.xml`, solo esas dos entradas.
6. **Enganches:**
   - **`WorkoutMediaRepository`:** encola FULL (`userInitiated = true`) en dos casos:
     - al terminar bien la ingesta de un VIDEO (cámara: `submitFinalizedVideoCapture`; galería: `submitUriCapture`);
     - con `analyzeVideos` activo.
   - **`delete(id)`:** antes de borrar, cancela el trabajo.
   - **`AppBootstrap`:** lanza `reconcile` en segundo plano al arrancar, en el proceso principal.

## Pruebas (Robolectric + `androidx.work:work-testing`)
- **`TechniqueAnalysisWorkerTest`:** con `WorkManagerTestInitHelper` y un `TechniqueAnalyzerProvider` sustituible por un falso:
  - FULL con un analizador falso que devuelve `Done` → la fila queda en DONE, existe `analysis.json` y el resumen es correcto;
  - `Unsupported` → UNSUPPORTED;
  - `Failed` transitorio → reintento;
  - el progreso se escribe con límite;
  - con `cameraBusy = true`, espera; al soltarlo, sigue.
- **`TechniqueAnalysisSchedulerTest`:**
  - KEEP en FULL y REPLACE en PLATE_ONLY;
  - cancelación;
  - `reconcile` reconstruye una fila desde el sidecar tras simular una restauración (`DELETE FROM workout_media` + reinsertar el medio) y borra los huérfanos.
- **`WorkoutMediaRepository`:** guardar un VIDEO encola el trabajo y borrarlo lo cancela.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.work.*" --tests "com.example.kpkn.data.repository.*VideoAnalysis*" --tests "com.example.kpkn.data.repository.*Media*"`
  3. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`, para las comprobaciones de telemetría en el emulador (punto 5).

## Commits
- `feat(technique): Worker del análisis con cola, puerta de cámara y primer plano`
- `feat(technique): repositorio de análisis, encolado al guardar y reconciliación al arrancar`
- `test(technique): Worker, cola y reconciliación`
