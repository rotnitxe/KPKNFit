# VT8a · Grabador de técnica a pantalla completa (encuadre, 60 fps, REC, metadatos) y arreglos de la captura


- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt8a`. La crea el orquestador desde `feat/video-technique`, con VT4c, VT7b y `sessions-v1` @5f78eb4a4 ya integrados.
- **Antes de empezar, lee:**
  - `docs/video-technique/briefs/VT_COMUN.md`, `docs/screens-v1/briefs/S_COMUN.md` y `docs/design/KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 1, §4 punto 14 (`FramingEvaluator`), los «Convenios fijados por VT1b» de §4 y §9 punto 6;
  - el código de hoy:
    - `screens/workout/WorkoutMediaCaptureController.kt`;
    - `screens/workout/components/SetCardExerciseMediaBack.kt`;
    - `screens/workout/components/SetExecutionCard.kt`: `SetInputCardV2` (~L760), el botón «Ver ejercicio/Fotos» (`cardFace = ExerciseMedia`, ~L2355), el reverso con `rotationY` (~L3314-3330), donde se llama a `SetCardExerciseMediaBack` (~L3320), y `openMediaFace` (~L1204);
    - `screens/workout/WorkoutV2Body.kt` (llama a `SetInputCardV2` en ~L2067), `screens/workout/WorkoutScreen.kt` (`requestOpenMediaFace`, ~L1373) y `screens/workout/WorkoutOverlayHost.kt`;
    - `data/media/WorkoutMediaPrFlags.kt` (`sideToken`);
    - `data/media/technique/CaptureMetadata.kt` (VT4c);
    - `data/media/pose/MediaPipePoseEstimator.kt` (VT4b);
    - `data/work/AnalysisGate.kt` (VT5b).
- **Logs:** `C:\kw\logs\vt-8a-<n>.log`.
- **PNG:** `C:/kw/shots/vt/8a`.

**Sesión en vivo ORIGINAL (PR11; U7, D28).** El rediseño LS se retiró.
- `SetCardExerciseMediaBack`, `WorkoutMediaCaptureController`, `WorkoutMediaPreviewDialog` y `screens/workout/technique/` son tuyos.
- La sesión «KPKN Wizard definitivo» refina SOLO el aspecto de la sesión, con la misma disposición y los mismos controles.
  - Comprobado el 14-oct: sus paquetes en curso (LR1b, LR1c1, LR1c2 y LR1d) no tocan tus archivos.
  - LR1b añade el icono `Mic` al final de `KpknLineIcons.kt`: no uses ese nombre.
- En `SetExecutionCard.kt`, `WorkoutV2Body.kt` y `WorkoutScreen.kt`: cambios mínimos, con los parámetros nuevos al final y con valor por defecto.

## Heredado de VT4c (ya integrado; los nombres REALES mandan)
- **`CaptureMetadata`** (`data/media/technique/CaptureMetadata.kt`): `requestedFps`, `grantedFps`, `gravityRollDeg`, `gravityPitchDeg`, `focalLengthPx`, `lens`, `deviceModel` y `recordedAtMs`, todos opcionales.
  - Se escribe con `CaptureMetadataFile.update(file) { it.copy(…) }` o `write(file, metadata)`, sobre `TechniqueSidecars.captureFile(videoFile)`.
  - Conserva las claves que no conoce, incluidas las del usuario (`userProfileOverride`, `plateSeed` y `plateDiameterMm`).
- **`focalLengthPx`** va en píxeles del vídeo GRABADO, ya girado (`displayWidth × displayHeight`). El pipeline la lleva al fotograma de análisis.
- **El pipeline lee de ahí** la gravedad y la focal. Los fps reales los saca del contenedor; `requestedFps` y `grantedFps` son para el informe y la insignia.
- **`AnalysisGate.cameraBusy`** (VT5b): ponlo a `true` mientras se graba. El pipeline espera cada 30 fotogramas.

## Archivos permitidos
- **Nuevos en `screens/workout/technique/`:**
  - `TechniqueRecorderHolder.kt`: un holder estable (`@Stable class`) con `var request by mutableStateOf<TechniqueRecorderRequest?>(null)`, recordado en `WorkoutScreen`;
  - `TechniqueRecorderScreen.kt`: con estado, a pantalla completa;
  - `RecorderSessionFactory.kt`;
  - `FramingCheckController.kt`;
  - `CaptureMetadataRecorder.kt`.
- **Nuevos en `screens/workout/technique/design/`** (sin estado): `RecorderTopBar.kt`, `RecordButton.kt`, `RecTimerPill.kt`, `FramingChips.kt`, `FramingGuideOverlay.kt` y `RecorderBadges.kt`.
- **`data/media/WorkoutMediaSides.kt`** (nuevo): `normalize(side, exercise)` y `token(...)`, sacados de `WorkoutMediaPrFlags.sideToken`; `WorkoutMediaPrFlags` pasa a usarlo.
- **Modificados:**
  - `WorkoutMediaCaptureController.kt`;
  - `SetCardExerciseMediaBack.kt`;
  - `SetExecutionCard.kt`: solo para pasar `onOpenRecorder` a `SetCardExerciseMediaBack` y volver a la cara frontal;
  - `WorkoutScreen.kt`, y si hace falta `WorkoutV2Body.kt` o `WorkoutOverlayHost.kt`: crear el holder, bajarlo a la tarjeta y pintar el grabador;
  - `WorkoutMediaPrFlags.kt`;
  - `ui/components/icons/KpknLineIcons.kt`: añadir AL FINAL `Camera` y `Record`.
- **Depuración:** `src/debug/.../debug/TechniqueBenchActivity.kt`, con el modo `recorder_preview`.
- **Pruebas:** `src/test/.../screens/workout/technique/`, `src/test/.../render/TechniqueRecorderRenderSheetTest.kt` y `src/test/.../data/media/WorkoutMediaSidesTest.kt`.
- **Solo para el paso 0:**
  - `data/media/technique/CaptureMetadata.kt`, `TechniqueAnalyzer.kt` y `TechniqueAnalysisPipeline.kt`;
  - `data/repository/VideoAnalysisRepository.kt`: solo `CaptureOverridesFile`, `completeDone` y `upsertDoneRow`;
  - `data/work/TechniqueAnalysisWorker.kt`;
  - sus pruebas en `src/test/.../data/{media/technique,repository,work}/`.

## Paso 0 · Dos arreglos heredados de VT4c (commit aparte, ANTES del grabador)
1. **Un solo candado para `<id>.capture.json`.**
   - Hoy `CaptureOverridesFile` (en `VideoAnalysisRepository.kt`, ~L486) y `CaptureMetadataFile` (en `CaptureMetadata.kt`, ~L67) tienen cada uno su `private val lock`. Si escriben a la vez, uno pierde la escritura del otro.
   - Crea `internal object CaptureFileLock` en `CaptureMetadata.kt` y haz que los dos sincronicen sobre él.
   - Tu grabador escribirá ahí al terminar, justo cuando el usuario puede estar calibrando el disco de otro vídeo.
   - **Prueba:** 2 hilos × 200 actualizaciones, uno con cada escritor, sobre el mismo archivo. Al final están todas las claves de los dos.
2. **El informe guarda el ejercicio completo y la versión de la pose.**
   - Hoy `TechniqueAnalysisOutcome.Done` solo lleva el informe. El Worker escribe en `analysis.json` el `ExerciseRef` PARCIAL de la petición (sin patrón, material ni lateralidad; un vídeo anterior a v29 queda como «propio»). La fila guarda `poseVersion` por defecto y `poseEngine = null`.
   - Añade a `Done` tres parámetros AL FINAL, con valor por defecto: `exercise: ExerciseRef? = null`, `poseEngine: String? = null` y `poseVersion: Int? = null`.
   - **El pipeline los rellena:**
     - en FULL, el ejercicio completado, una cadena corta del motor (motor, modelo y delegado; mira qué da `estimator.engine`) y `POSE_VERSION`;
     - en RECOMPUTE y PLATE_ONLY, solo el ejercicio.
   - **El Worker:**
     - pasa `outcome.exercise ?: request.exercise` a `completeDone`;
     - en FULL, la fila toma `poseVersion` y `poseEngine` del resultado;
     - en los otros modos, conserva los anteriores, como hoy.
   - **Pruebas:**
     - Worker con un analizador falso que devuelve `Done` con ejercicio y pose → `analysis.json` lleva el ref completo y la fila lleva `poseVersion` y `poseEngine`;
     - un `Done` sin esos campos se comporta como hoy.
3. **Comprobación del paso 0:** `:app:compileBaseDebugKotlin` y luego `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.media.technique.*" --tests "com.example.kpkn.data.work.*" --tests "com.example.kpkn.data.repository.*VideoAnalysis*"`.
   - Es además la primera compilación de la rama con VT4c, VT7b y la fusión de `sessions-v1`.
   - Si algo falla por código ajeno a tu paquete, para y descríbelo en el informe.

## Qué haces
1. **Abrir el grabador.**
   - En `SetCardExerciseMediaBack`, el chip «Vídeo» llama a un nuevo parámetro final `onOpenRecorder: ((WorkoutMediaCaptureRequest) -> Unit)? = null`. Si es null, sigue el comportamiento de hoy.
   - La tarjeta (`SetInputCardV2`) vuelve a la cara frontal (`cardFace = SetCardFace.Front`) y pone `holder.request`.
   - `WorkoutScreen`/`WorkoutV2Body` pinta `TechniqueRecorderScreen` sobre todo cuando hay petición: `Dialog` a pantalla completa con `usePlatformDefaultWidth = false` y `decorFitsSystemWindows = false`.
   - Al cerrar el grabador, `request = null`.
   - La vista previa de la tarjeta pasa a enlazar SOLO Preview + ImageCapture (las fotos).
2. **`RecorderSessionFactory`** (CameraX 1.5.3). Busca en el AAR de la caché de Gradle los nombres reales de la API de `SessionConfig` y del grupo de 60 fps, y si son `@Experimental…`, el `@OptIn`.
   - **Niveles:**
     1. grupo de 60 fps soportado;
     2. rango (60, 60) entre los soportados;
     3. 30 fps.
   - **Use cases:** Preview 16:9 (`RATIO_16_9_FALLBACK_AUTO_STRATEGY`) + VideoCapture (`Recorder` con `hdThenSdSelector`).
   - **Nunca** el grupo junto con `setFrameRateRange` ni con `setTargetFrameRate`.
   - **60 fps** solo si `prefer60Fps` (ajustes) y el perfil usa disco o es de velocidad (SQUAT, DEADLIFT, BENCH, OHP, HIP_THRUST, ROW).
   - **Función pura de elección del nivel**, para poder probarla: `chooseTier(caps, want60)`.
3. **Pantalla del grabador** (negro, sin Material):
   - **Vista previa:** `PreviewView` en COMPATIBLE y FIT_CENTER, 16:9.
   - **Arriba** (`RecorderTopBar`):
     - `KpknIconButton(Close)`;
     - el nombre del ejercicio y «Serie 2»;
     - las insignias de «60 fps» (solo si se concedió) y «Disco 45 cm» (perfiles con disco; al tocarla, chips 45/40/35/Otro).
   - **`FramingGuideOverlay`:** silueta de líneas (vista lateral o frontal, según el `FramingSpec` del perfil), en tinta al 25 %, con el texto guía del perfil (§1).
   - **`FramingChips`:** de `FramingCheckController`; «✓ Encuadre» o los arreglos.
   - **Abajo:**
     - `RecordButton`: 72 dp; en reposo, un anillo de tinta con un punto en `WizardColors.danger`; grabando, un cuadrado;
     - `RecTimerPill`: «● 0:12».
   - Al parar: se cierra y el aviso queda en la serie (VT8b). Aquí basta con cerrar.
4. **`FramingCheckController`:**
   - `PreviewView.bitmap` a ~2 Hz, en el hilo principal; se reduce a 256 px fuera de él;
   - MediaPipe en IMAGE con CPU, en su hilo `vt-framing`. Si la detección anterior no ha acabado, se salta;
   - opcional: `PlateDetector.propose` sobre la luma reducida (perfiles con disco) para `plateSeen`;
   - `FramingEvaluator` con la gravedad;
   - `StateFlow<FramingVerdict>`;
   - solo con la vista previa activa y sin grabar.
5. **Grabación** (`WorkoutMediaCaptureController`):
   - **Inicio:** `startTechniqueRecording(request: () -> WorkoutMediaCaptureRequest, audio, session)`.
   - **Límite:** `FileOutputOptions.setDurationLimitMillis(maxRecordSeconds·1000)`.
   - **Cronómetro:** desde `Start`; con `Status.recordingStats.recordedDurationNanos`.
   - **Orientación:**
     - `OrientationEventListener` → `videoCapture.targetRotation` antes de empezar;
     - mientras graba, la actividad queda con `SCREEN_ORIENTATION_LOCKED`, y al terminar se restaura.
   - **Cámara ocupada:** `AnalysisGate.cameraBusy` a true mientras graba.
   - **Al finalizar** (`Finalize`):
     - las cifras se leen OTRA VEZ del `requestProvider` (no las de inicio);
     - `catalogDefinitionId`/`catalogConfigurationId` de `exercise.catalogDefinitionId`/`catalogConfigurationId`;
     - el lado con `WorkoutMediaSides.normalize`: `null` en bilaterales.
   - **`CaptureMetadataRecorder`:**
     - TYPE_GRAVITY durante la grabación → mediana de roll y pitch, con los convenios de VT1b;
     - f_px de `Camera2CameraInfo`: `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`, `SENSOR_INFO_PHYSICAL_SIZE` y `SENSOR_INFO_PIXEL_ARRAY_SIZE`, escalado a la resolución grabada;
     - fps pedidos y concedidos, lente y modelo del dispositivo;
     - todo se escribe con `CaptureMetadata` al tener el id del medio.
   - **Parar:** al cerrar el grabador o al irse de la página, con `DisposableEffect`.
   - **Se quita** la rama «sigue enlazado mientras graba» de la vista previa de la tarjeta.
6. **Tira de miniaturas** en `SetCardExerciseMediaBack`: filtra por ejercicio, serie y lado normalizado. Hoy filtra solo por ejercicio.
7. **Banco de depuración:** modo `recorder_preview`.
   - Abre `TechniqueRecorderScreen` con una petición falsa (SQUAT, «Serie 1»).
   - Sirve para capturas en el emulador con su cámara virtual: `emu_run.py` con `--extra "--es mode recorder_preview"`, `--actions "wait:3;shot:idle;tap:<botón>;wait:3;shot:rec;tap:<botón>;wait:2;shot:stop"` y `--crash-log`.

## Pruebas
- **JVM:**
  - `WorkoutMediaSidesTest`: bilateral → `null`; «L»/«left» → «left»;
  - `RecorderSessionFactory.chooseTier` con las 3 combinaciones;
  - `FramingCheckController` con un estimador falso (3 OK → «✓ Encuadre»).
- **Hoja de render** (360 dp, 100 % y 130 %):
  - barra superior con y sin insignias;
  - botón en reposo y grabando;
  - cronómetro;
  - los chips de encuadre (OK y cada aviso);
  - la silueta de lado y de frente sobre un fondo gris.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.technique.*" --tests "com.example.kpkn.data.media.*" --tests "com.example.kpkn.screens.workout.*Media*"`
  3. `:app:compileBaseDebugAndroidTestKotlin`: `WorkoutExternalCameraCaptureInstrumentedTest` debe compilar
  4. hoja de render
  5. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`, y el modo `recorder_preview` en el emulador.

## Commits
- `fix(technique): un solo candado para capture.json; el informe guarda el ejercicio completo y la versión de la pose` (paso 0)
- `feat(technique): grabador a pantalla completa con encuadre, 60 fps y metadatos de captura`
- `fix(workout): lado normalizado, cifras al finalizar y miniaturas por serie en los vídeos de la serie`
- `test(technique): hoja de render del grabador`
