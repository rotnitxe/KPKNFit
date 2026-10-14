# VT4b · Motor de pose MediaPipe, sonda de GPU y banco de depuración

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt4b`, desde `feat/video-technique` con VT5b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.1, §3.7 (bloque `data/media/pose`) y §9 punto 3;
  - `docs/video-technique/MODELS.md`;
  - el arnés `src/debug/.../debug/TechniqueBenchActivity.kt` (VT4a);
  - `src/debug/.../data/media/PoseTrajectoryAnalyzer.kt` (el ML Kit viejo, que VT-A movió a depuración).
- **Logs:** `C:\kw\logs\vt-4b-<n>.log`.

## Archivos permitidos
- **`data/media/pose/` (nuevos):**
  - `PoseEstimator.kt`: `RgbaFrame`, `DetectedPose` y la interfaz de §3.7;
  - `MediaPipePoseEstimator.kt`;
  - `GpuProbe.kt`;
  - `PoseTrackAssembler.kt`: el paso de `DetectedPose` por fotograma a `PoseTrackV2`, usando `AthleteSelector` y `PoseLandmark.KEPT_*`.
- **`src/debug/java/com/example/kpkn/data/media/pose/MlKitPoseEstimator.kt` (nuevo):** adapta el uso de ML Kit de `PoseTrajectoryAnalyzer` a la interfaz, en `STREAM_MODE`.
- **`src/debug/.../debug/TechniqueBenchActivity.kt`:** modos nuevos.
- **Pruebas:** `src/test/java/com/example/kpkn/data/media/pose/` (Robolectric o JVM, para `PoseTrackAssembler` y la lógica pura de la sonda).

## Qué haces
1. **`MediaPipePoseEstimator`.**
   - **Creación:** `MediaPipePoseEstimators.create(context, mode: RunningMode /*VIDEO|IMAGE*/, model: PoseModel /*FULL|HEAVY_FROM_FILE(path)*/, preferGpu: Boolean = true, numPoses: Int = 2): PoseEstimator`.
     - **Modelo:** full = `setModelAssetPath("mediapipe/pose_landmarker_full.task")`; heavy = `setModelAssetPath` no sirve para un archivo: usa `setModelAssetBuffer` con un `MappedByteBuffer` del archivo.
     - **Opciones:** `setNumPoses(2)`, confianzas de detección, presencia y seguimiento a 0,5, sin máscaras.
   - **Hilo:** todo ocurre en un hilo propio `vt-pose` (ejecutor de un solo hilo): crear, `detectForVideo` o `detect` y `close`.
   - **Tiempos:** ts = max(pts/1000, último + 1).
   - **Imagen:**
     - `ByteBufferImageBuilder(rgba, w, h, MPImage.IMAGE_FORMAT_RGBA).build()`;
     - si la tarea lo rechaza (excepción), un `Bitmap` ARGB_8888 reutilizado con `copyPixelsFromBuffer`;
     - **NUNCA** `MPImage.close()`, porque recicla el bitmap.
   - **Salida:** `List<DetectedPose>` con los 33 puntos de imagen (x, y, z, visibility.orElse(0), presence.orElse(0)) y los 33 «world».
   - **`engine`:** `PoseEngineInfo("mediapipe-pose", "full"|"heavy", "GPU"|"CPU")`.
2. **`GpuProbe`** (§9.3):
   - comprobación previa: `reqGlEsVersion ≥ 0x30001`;
   - antes de crear con GPU, escribe en SharedPreferences `gpu_probe=<versionCode>`, y lo borra tras la 1.ª detección correcta;
   - si al arrancar sigue escrito → GPU vetada para esa `versionCode` (se guarda) y se usa CPU;
   - si crear o detectar con GPU lanza una excepción → se cierra y se recrea en CPU;
   - modo degradado: `isLowRamDevice` o < 3 GB de RAM total, o estado térmico ≥ MODERATE (API 29+) → `recommendedPoseStride = 2`.
3. **`PoseTrackAssembler`:**
   - acumula por fotograma `tUs`, las poses detectadas y la elegida (`AthleteSelector`, con el centro del torso anterior);
   - construye `PoseTrackV2` con `frameWidth/Height` del fotograma girado, la rotación, el fps nominal, `poseStride` y la gravedad o f_px si se los dan;
   - expone `multiPersonFraction`, que hace falta para la flag MULTI_PERSON.
4. **`MlKitPoseEstimator` (debug):** la misma interfaz.
   - Mapea los 33 `PoseLandmark` de ML Kit: `inFrameLikelihood` → visibility y presence; z de ML Kit; sin «world», con ceros.
   - Una sola pose.
5. **Modos del banco** (`TechniqueBenchActivity`):
   - **`pose_bench`:**
     - **Entrada:** cada vídeo de `getExternalFilesDir("vt-bench/in")`.
     - **Motores:** `mp-full-gpu`, `mp-full-cpu`, `mp-heavy-gpu` (si existe `vt-bench/models/pose_landmarker_heavy.task`) y `mlkit-base`.
     - **Proceso:** `VideoFrameSources.open` → pose de cada fotograma (o 1 de 2 a 60 fps) → `PoseTrackAssembler`.
       - **Heredado de VT4a:** el `DecodedFrame` solo vale dentro de `onFrame`, porque el pool reutiliza 3 huecos por turnos. En el banco, calcula la pose DENTRO de `onFrame`, en el hilo de pose y esperando su resultado, o copia el buffer antes de salir. Nada de colas sin copia.
       - Pide `FrameNeeds.SKIP` en los fotogramas sin pose (stride 2): así ni se dibujan.
       - Anota en `bench.json` el camino de decodificación: `(source as? VideoDecodeReport)?.path` y `details`.
     - **Escribe:**
       - `vt-bench/out/<vídeo>.<motor>.pose2.json.gz`, con `PoseTrackCodec`;
       - `vt-bench/out/bench.json`: por vídeo y motor, ms por fotograma de decodificación y de pose (media, p50 y p95), fotogramas con pose, % con 2 poses, delegado real usado y errores.
   - **`telemetry_check`:**
     - ejecuta 100 detecciones con `mp-full` sobre un clip;
     - escribe en `vt-bench/out/telemetry.json`:
       - (a) las líneas del logcat PROPIO del proceso (`Runtime.exec("logcat -d -v time")`, filtrando `TRuntime|TransportRuntime|datatransport|clearcut|play.googleapis` sin distinguir mayúsculas);
       - (b) `TrafficStats.getUidTxBytes(myUid)` antes y después;
       - (c) si la clase `com.google.android.datatransport.runtime.TransportRuntime` se puede cargar (`Class.forName`).
   - **`--ez quit true`:** al terminar, `finish()`.
6. **Prueba de humo de R8** (compilación, no ejecución): `-I C:/kw/tools/dbg-r8.init.gradle :app:assembleBaseRelease`.
   - Tiene que compilar sin `Missing class` de MediaPipe ni de protobuf.
   - Anota el tamaño del APK y las `.so` de MediaPipe en arm64.

## Pruebas y corridas
1. **JVM:** `PoseTrackAssemblerTest` (2 poses → sigue al atleta; KEPT; tiempos), con `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.media.pose.*"`.
2. `:app:compileBaseDebugKotlin`
3. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`
4. **Emulador** (autorizado; sin `adb` suelto):
   - un clip propio: el orquestador deja en `C:\kw\vt-bench\in\` los vídeos con permiso; si no hay ninguno, genera uno con `SyntheticClipEncoder` (sin persona: comprueba que no falla);
   - el modelo heavy, desde `C:\kw\models\mediapipe-pose-float16-1\`:
     ```
     python C:\kw\tools\emu_run.py --apk <apk debug .dbg> --push-dbg C:\kw\vt-bench\in=vt-bench/in ^
       --component com.example.kpkn.dbg/com.example.kpkn.debug.TechniqueBenchActivity --extra "--es mode pose_bench" ^
       --out C:\kw\shots\vt\4b --tag bench --wait-file vt-bench/out/bench.json --wait-secs 900 ^
       --pull-dbg vt-bench/out=C:\kw\shots\vt\4b\out --crash-log
     ```
     Antes, empuja el heavy con otro `--push-dbg C:\kw\models\mediapipe-pose-float16-1=vt-bench/models`, en una ejecución con `--no-start`, o en la misma, si el script admite un solo `--push-dbg`: copia el modelo a una carpeta temporal tuya junto a los vídeos y empuja todo.
   - después, `telemetry_check` igual, esperando a `vt-bench/out/telemetry.json`.
5. `-I C:/kw/tools/dbg-r8.init.gradle :app:assembleBaseRelease` (punto 6).

## Informe
Además de lo común:
- la tabla de ms por fotograma por motor en el emulador;
- el delegado que funcionó;
- lo que salió en `telemetry.json`;
- el resultado de la prueba de humo de R8;
- los tamaños.

## Commits
- `feat(technique): motor de pose MediaPipe con sonda de GPU y ensamblado de la traza v2`
- `test(technique): banco de pose y comprobación de telemetría en el arnés de depuración`
