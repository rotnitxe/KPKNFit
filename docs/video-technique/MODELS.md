# Modelos de pose de la ola VT

Descargados el 2026-10-13 con permiso del usuario (decisión U4), desde el bucket oficial de MediaPipe. La ruta es versionada
(`float16/1`); nunca se usa `/latest/`.

| Archivo | Origen | Bytes | SHA-256 | MD5 (base64, x-goog-hash) | Dónde va |
|---|---|---|---|---|---|
| `pose_landmarker_full.task` | https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task | 9 398 198 | `5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1` | `g4eWidNz0UO+CUyXI1Xkjg==` | `android-native/app/src/main/assets/mediapipe/` (en la app) |
| `pose_landmarker_heavy.task` | https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task | 30 664 242 | `64437af838a65d18e5ba7a0d39b465540069bc8aae8308de3e318aad31fcbc7b` | `RT3sTQLMxNPOgStt6E+lFg==` | FUERA del repo. Copia local en `C:\kw\models\mediapipe-pose-float16-1\`; el banco de depuración la lee de un archivo empujado al dispositivo |

**Licencia:** Apache-2.0. Son los modelos de MediaPipe Solutions (BlazePose GHUM 3D) que distribuye Google para Pose
Landmarker; la librería `com.google.mediapipe:tasks-vision:1.1.0` también es Apache-2.0.

**Uso:**
- Los lee `MediaPipePoseEstimator` (VT4b) con `BaseOptions.setModelAssetPath("mediapipe/pose_landmarker_full.task")`.
- `androidResources { noCompress += "task" }`, para que se lean del APK sin descomprimir.

**Para comprobar una copia:** `sha256sum pose_landmarker_full.task` debe dar el hash de la tabla.
