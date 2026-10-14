# VT8a · Grabador de técnica a pantalla completa (encuadre, 60 fps, REC, metadatos) y arreglos de la captura

> **⚠ PENDIENTE DE REESCRIBIR (D28, 14-oct). NO LANZAR con este texto.**
> - El usuario rechazó el rediseño de la sesión en vivo (LS1–LS8). `screens/workout/` vuelve a la sesión ORIGINAL de la PR11 (`8f0afc9dc`) en `feat/sessions-v1`.
> - Desaparecen la hoja «Técnica» de LS2b, `WorkoutTechniqueSheetHolder`, `WorkoutSetPanel`, `WorkoutRestPanel` y su `videoSlot`, entre otros.
> - Siguen igual `SetCardExerciseMediaBack`, `WorkoutMediaCaptureController` y `WorkoutMediaPreviewDialog`.
> - El orquestador reescribe este brief cuando entre la restauración, con los puntos de entrada equivalentes que pasará la sesión «KPKN Wizard definitivo».

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt8a`, desde `feat/video-technique` con VT7b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 1, §4 punto 14 (`FramingEvaluator`), los «Convenios fijados por VT1b» de §4 y §9 punto 6;
  - el código de hoy:
    - `screens/workout/WorkoutMediaCaptureController.kt`;
    - `screens/workout/components/SetCardExerciseMediaBack.kt`;
    - `screens/workout/components/SetExecutionCard.kt`: la hoja «Técnica» en ~L1230-1241 y ~L1642-1700, y el `WorkoutTechniqueSheetHolder`;
    - `screens/workout/WorkoutV2Body.kt`;
    - `data/media/WorkoutMediaPrFlags.kt` (`sideToken`);
    - `data/media/technique/CaptureMetadata.kt` (VT4c);
    - `data/media/pose/MediaPipePoseEstimator.kt` (VT4b);
    - `data/work/AnalysisGate.kt` (VT5b).
- **Logs:** `C:\kw\logs\vt-8a-<n>.log`.
- **PNG:** `C:/kw/shots/vt/8a`.

**La ola LS** se comprometió a no tocar `SetCardExerciseMediaBack`, `WorkoutMediaCaptureController` ni `screens/workout/technique/`. Esos archivos son tuyos. En `SetExecutionCard.kt` y `WorkoutV2Body.kt`, solo cambios mínimos y parámetros al final.

## Archivos permitidos
- **Nuevos en `screens/workout/technique/`:**
  - `TechniqueRecorderHolder.kt`: con `var request by mutableStateOf<TechniqueRecorderRequest?>(null)`, como `WorkoutTechniqueSheetHolder`;
  - `TechniqueRecorderScreen.kt`: con estado, a pantalla completa;
  - `RecorderSessionFactory.kt`;
  - `FramingCheckController.kt`;
  - `CaptureMetadataRecorder.kt`.
- **Nuevos en `screens/workout/technique/design/`** (sin estado): `RecorderTopBar.kt`, `RecordButton.kt`, `RecTimerPill.kt`, `FramingChips.kt`, `FramingGuideOverlay.kt` y `RecorderBadges.kt`.
- **`data/media/WorkoutMediaSides.kt`** (nuevo): `normalize(side, exercise)` y `token(...)`, sacados de `WorkoutMediaPrFlags.sideToken`; `WorkoutMediaPrFlags` pasa a usarlo.
- **Modificados:**
  - `WorkoutMediaCaptureController.kt`;
  - `SetCardExerciseMediaBack.kt`;
  - `SetExecutionCard.kt`: solo para abrir el grabador y cerrar la hoja;
  - `WorkoutV2Body.kt` y/o `WorkoutScreen.kt`: crear el holder y pintar el grabador;
  - `WorkoutMediaPrFlags.kt`;
  - `ui/components/icons/KpknLineIcons.kt`: añadir AL FINAL `Camera` y `Record`.
- **Depuración:** `src/debug/.../debug/TechniqueBenchActivity.kt`, con el modo `recorder_preview`.
- **Pruebas:** `src/test/.../screens/workout/technique/`, `src/test/.../render/TechniqueRecorderRenderSheetTest.kt` y `src/test/.../data/media/WorkoutMediaSidesTest.kt`.

## Qué haces
1. **Abrir el grabador.**
   - En `SetCardExerciseMediaBack`, el chip «Vídeo» llama a un nuevo parámetro final `onOpenRecorder: ((WorkoutMediaCaptureRequest) -> Unit)? = null`. Si es null, sigue el comportamiento de hoy.
   - La tarjeta (`SetInputCardV2`) cierra la hoja «Técnica» y pone `holder.request`.
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
- `feat(technique): grabador a pantalla completa con encuadre, 60 fps y metadatos de captura`
- `fix(workout): lado normalizado, cifras al finalizar y miniaturas por serie en los vídeos de la serie`
- `test(technique): hoja de render del grabador`
