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
5. **Enganches:**
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

## Commits
- `feat(technique): Worker del análisis con cola, puerta de cámara y primer plano`
- `feat(technique): repositorio de análisis, encolado al guardar y reconciliación al arrancar`
- `test(technique): Worker, cola y reconciliación`
