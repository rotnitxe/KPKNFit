# VT-A · Dependencias y base de compilación de la ola VT

- **Worktree:** `C:\kw\vt-a`.
- **Rama:** `vt/vt-a`, desde `feat/video-technique`.
- **Antes de empezar, lee:**
  - `docs/video-technique/briefs/VT_COMUN.md`, entero;
  - `docs/video-technique/VIDEO_TECHNIQUE_V1.md` §0, §9.1 y §11;
  - `docs/video-technique/MODELS.md`.
- **Logs:** `C:\kw\logs\vt-A-<n>.log`.

Este paquete deja el proyecto listo para el resto de la ola: librerías, manifiesto, R8 y modelo. Además, retira el análisis viejo de ML Kit, que hoy está apagado y sin interfaz. **No añade funciones nuevas.**

## Archivos permitidos
- `android-native/gradle/libs.versions.toml`
- `android-native/app/build.gradle.kts`
- `android-native/app/proguard-rules.pro`
- `android-native/app/src/main/AndroidManifest.xml`
- `android-native/app/src/main/assets/mediapipe/pose_landmarker_full.task` (nuevo)
- `android-native/app/src/main/java/com/example/kpkn/data/media/PoseTrajectoryAnalyzer.kt` → se MUEVE a
  `android-native/app/src/debug/java/com/example/kpkn/data/media/PoseTrajectoryAnalyzer.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutMediaCaptureController.kt`: solo quitar código (punto 5)
- Solo si la subida de CameraX rompe la compilación, y solo para arreglarla, sin cambiar el comportamiento:
  - `screens/workout/components/SetCardExerciseMediaBack.kt`;
  - `WorkoutMediaCaptureController.kt`;
  - las pruebas que los usan.

## Qué haces
1. **Versiones** (respeta el estilo de cada línea: las de cámara y ML Kit están escritas en línea en `build.gradle.kts`, ~L231-239):
   - **WorkManager:** `work = "2.7.1"` → `"2.11.2"` en `libs.versions.toml`. Añade `testImplementation("androidx.work:work-testing:2.11.2")`, o su entrada en el catálogo, junto a las demás de prueba.
   - **CameraX:** `camera-camera2`, `camera-lifecycle`, `camera-view` y `camera-video` pasan de 1.4.2 a **1.5.3**.
     - Comprueba en el árbol de dependencias que NO arrastra Media3.
     - **Media3 se queda en 1.5.1.**
   - **MediaPipe:** `implementation("com.google.mediapipe:tasks-vision:1.1.0")`, versión FIJA.
     - Nunca `+` ni `latest.release`.
     - La versión `0.20230731` es vieja, aunque se ordene como más nueva.
   - **ML Kit pose:** `com.google.mlkit:pose-detection:18.0.0-beta5` pasa de `implementation` a **`debugImplementation`**.
     - `text-recognition` NO se toca.
     - Ajusta el comentario vecino («Bundled base pose model…»).
2. **Modelo:**
   - en el bloque `android { … }` de `app/build.gradle.kts`: `androidResources { noCompress += "task" }`, o la forma que acepte AGP 9 (comprueba que compila);
   - copia `C:\kw\models\mediapipe-pose-float16-1\pose_landmarker_full.task` a `android-native/app/src/main/assets/mediapipe/pose_landmarker_full.task`;
   - comprueba el SHA-256 (`sha256sum`): `5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1`;
   - el modelo heavy NO entra en el repo.
3. **R8** (`proguard-rules.pro`):
   - quita SOLO `-keep class com.google.mlkit.vision.pose.** { *; }`;
   - se quedan `vision.common`, `common.internal`, `common.sdkinternal` y `-dontwarn com.google.mlkit.**`, que usa el OCR;
   - añade, en un bloque «MediaPipe Tasks (VT)»:
     ```
     -keep class com.google.mediapipe.** { *; }
     -keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
     -dontwarn com.google.mediapipe.**
     -dontwarn com.google.auto.value.**
     -dontwarn javax.lang.model.**
     ```
4. **Manifiesto** (`src/main/AndroidManifest.xml`):
   - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"/>` junto a los demás `FOREGROUND_SERVICE_*`;
   - dentro de `<application>`:
     ```xml
     <service
         android:name="androidx.work.impl.foreground.SystemForegroundService"
         android:foregroundServiceType="dataSync|mediaProcessing"
         tools:node="merge" />
     ```
   - si falta `xmlns:tools` en `<manifest>`, añádelo.
5. **Retirar el análisis viejo:**
   - mueve `PoseTrajectoryAnalyzer.kt` a `src/debug/java/com/example/kpkn/data/media/`, con el mismo paquete y contenido; es el único que importa ML Kit pose;
   - en `WorkoutMediaCaptureController.kt`, borra:
     - `attachPoseTrack(...)` (~L616-636);
     - su lanzamiento dentro de `observeDurableWrite` (~L597-602, el `if (saved.kind == VIDEO && poseTrajectoryEnabled())`);
     - los imports que queden sin uso.
   - Se quedan, porque VT9 los retira después:
     - el parámetro `poseTrajectoryEnabled`;
     - `isPoseTrajectoryEnabled()`;
     - las lecturas de la bandera en otros archivos.
   - Comprueba con Grep que nada en `src/main` ni en `src/test` importa `PoseTrajectoryAnalyzer` ni `com.google.mlkit.vision.pose`. `PoseTrajectoryAnalysis.kt` (puro) se queda en `main`.
6. **Árbol de dependencias.** Con `:app:dependencies --configuration baseReleaseRuntimeClasspath > /c/kw/logs/vt-A-deps.txt`, anota en el informe:
   - las versiones resueltas de `tasks-vision`, `tasks-core`, `camera-*`, `media3-*`, `work-runtime`, `guava` y `protobuf`;
   - si aparece `com.google.android.datatransport` (posible telemetría; VT4b la comprueba en ejecución). No lo excluyas todavía.

## Pruebas
Una corrida por línea, en este orden, cada una con su log.
1. `:app:compileBaseDebugKotlin`
2. `:app:compileBaseReleaseKotlin`: demuestra que `main` ya no depende de ML Kit pose.
3. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.media.*" --tests "com.example.kpkn.domain.biomechanics.*"`: siguen en verde `PoseTrajectoryAnalysisTest` y `TrajectoryMetricsTest`.
4. `:app:compileBaseDebugAndroidTestKotlin`: la subida de CameraX no debe romper `WorkoutExternalCameraCaptureInstrumentedTest` ni otras pruebas.
5. `:app:assembleBaseDebug`, para comprobar el empaquetado. Después, con `unzip -lv` sobre `app/build/outputs/apk/base/debug/*.apk`, comprueba:
   - `assets/mediapipe/pose_landmarker_full.task` está **Stored** (sin comprimir);
   - las `.so` de MediaPipe están por ABI. Apunta su nombre y tamaño en arm64-v8a.

   Apunta también el tamaño total del APK.

Si una tarea falla por la subida de CameraX o de WorkManager:
- arréglalo con el cambio mínimo en los archivos permitidos;
- si la causa es incompatible (Kotlin, AGP), vuelve a la versión anterior de ESA librería y explícalo; no fuerces.

## Commits
1. `build(technique): MediaPipe tasks-vision 1.1.0, CameraX 1.5.3 y WorkManager 2.11.2`
2. `build(technique): modelo pose_landmarker_full y reglas de R8 de MediaPipe`
3. `refactor(technique): ML Kit pose solo en depuración y fuera el análisis viejo de la captura`
4. Si hicieron falta arreglos de compilación por CameraX: `fix(technique): …`

## Informe
Además de lo de `VT_COMUN`:
- el resumen del árbol de dependencias (punto 6);
- el tamaño del APK;
- `.so` y modelo: si van sin comprimir;
- cualquier aviso de duplicados o de páginas de 16 KB.
