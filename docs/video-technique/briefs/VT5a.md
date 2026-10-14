# VT5a · Persistencia: Room v29, sidecars, resumen y ajustes

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt5a`, desde `feat/video-technique` con VT4b y VT3a integrados. VT3a crea `SetTechniqueReport` (`domain/biomechanics/metrics/SetTechniqueReport.kt`), que necesitas para `toSummary` y para el sidecar de análisis (D26).
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.5, §3.8, §3.9, §3.10 y §9 punto 5;
  - el código de `data/db/KpknDatabase.kt` (migraciones en el companion y `.addMigrations`);
  - `data/db/WorkoutMediaEntity.kt`;
  - `data/db/BackupTableManifest.kt`;
  - `data/repository/WorkoutMediaRepository.kt`;
  - `data/media/WorkoutMediaStore.kt`;
  - la prueba `src/test/.../data/db/WorkoutMediaMigration27To28Test.kt` (patrón).
- **Logs:** `C:\kw\logs\vt-5a-<n>.log`.

## Paso 0: verificación de integración (lo primero, antes de editar)
`feat/video-technique` acaba de recibir paquetes del carril de dominio (VT2b, VT3a y, si ya está, VT3b), que solo se probaron con el arnés JVM.

Tu PRIMERA corrida, por `gradle_slot.sh`, es:
`:app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.*" --tests "com.example.kpkn.data.media.*"`
- Es la verificación oficial, en lote, de esos paquetes.
- Mientras espera en la cola, puedes leer y planificar, pero no edites hasta que termine.
- Si falla algo de `domain/biomechanics` o de `data/media/video`, no lo arregles: anótalo en el informe con el error exacto y sigue con tu paquete, si tu parte compila.

## Archivos permitidos
- **`data/db/`:**
  - `VideoAnalysisEntity.kt` (nuevo, con `VideoAnalysisDao`);
  - `VideoAnalysisMigration.kt` (nuevo);
  - `KpknDatabase.kt` (versión 29, entidad, DAO y migración);
  - `WorkoutMediaEntity.kt` (2 columnas, índice, mapeos y DAO dirigido);
  - `BackupTableManifest.kt`.
- **`android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/29.json`:** lo genera KSP; añádelo al commit.
- **`data/models/`:**
  - `WorkoutMedia.kt` (2 campos);
  - `VideoAnalysisSummary.kt` (nuevo);
  - `Settings.kt` (`TechniqueVideoSettings` y el campo `techniqueVideo` AL FINAL de `Settings`).
- **`data/media/technique/TechniqueSidecars.kt` (nuevo):** nombres y rutas de los sidecars de §3.8, escritura atómica (temporal + rename) y lectura de `<id>.analysis.json`.
- **`data/repository/WorkoutMediaRepository.kt`:** solo lo de abajo.
- **`screens/workout/WorkoutMediaCaptureController.kt`:**
  - solo añadir `catalogDefinitionId` y `catalogConfigurationId` (por defecto `null`) a `WorkoutMediaCaptureRequest`;
  - copiarlos al construir `WorkoutMedia`.
  - Quién los rellena lo decide VT8a.
- **Pruebas:** en `src/test/java/com/example/kpkn/data/{db,repository,media/technique}/`.

## Qué haces
1. **Room v29** (§3.9 al pie de la letra).
   - **Entidad `VideoAnalysisEntity`:** `@Entity(tableName = "video_analysis")` con las columnas de §3.9.
     - Los nombres de columna son los de §3.9; nunca `view`, que es palabra reservada de SQL.
     - Índices `(status)` y `(exerciseKey, recordedAtMs)`.
     - `ForeignKey(entity = WorkoutMediaEntity::class, parentColumns = ["id"], childColumns = ["mediaId"], onDelete = CASCADE)`.
   - **`WorkoutMediaEntity`:** `catalogDefinitionId` y `catalogConfigurationId` (TEXT, nulables) e índice `sessionKey`.
   - **`VideoAnalysisMigration.kt`:** `fun videoAnalysisMigration(from: Int): Migration`, que crea exactamente el esquema que Room valida.
     - Copia el DDL del `29.json` generado: sin `DEFAULT` que Room no espera.
     - Se registra en `.addMigrations(...)` como 28→29.
   - **`VideoAnalysisDao`:**
     - `upsert` (`@Upsert`) y `getByMedia(id)`;
     - `observeByMedia(id): Flow<VideoAnalysisEntity?>`;
     - `observeBySessionKey(key)`: JOIN con `workout_media` (`m.sessionKey = :key AND m.kind = 'VIDEO'`) → filas con `mediaId`, `exerciseId`, `setIndex`, `side`, `createdAtMs`, `status`, `progressPct`, `repCount`, `partialReps`, `failedReps` y `confidence`;
     - `observeExerciseSummaries()`: agrupado por `exerciseKey`, con nombre, número de vídeos y último `recordedAtMs`;
     - `observeTrend(exerciseKey)`: filas DONE por fecha;
     - `observeConfigurationPoints(configurationKey)`: carga, `mcvBest`, `velocityUnit` y fecha;
     - `updateProgress(id, status, progressPct, updatedAtMs)`;
     - `delete(id)`;
     - `allIds()`.
   - **DAO dirigido en `WorkoutMediaDao`:**
     - `setPoseTrackPath(id, path)`;
     - `setSetSnapshot(id, reps, weightKg)`;
     - `setCatalogIds(id, def, cfg)`;
     - `setExerciseAssignment(id, exerciseId, canonicalExerciseId, exerciseName, def, cfg)`.

     Nunca `REPLACE`: dispararía la cascada.
2. **`BackupTableManifest`:**
   - `"video_analysis" to Category.CACHE`;
   - revisa cómo trata `workout_media_session_associations`, que hoy falta. Si el patrón es claro y no cambia la restauración de medios, añádelo como USER_DATA. Si no, NO lo toques y anótalo.
3. **`VideoAnalysisSummary`** (`data/models`):
   - espejo de la fila;
   - `fun SetTechniqueReport.toSummary(media: WorkoutMedia, exerciseKey: String, configurationKey: String?, loadKg: Double?, reportPath: String, nowMs: Long): VideoAnalysisSummary`;
   - mapeos a la entidad y desde ella;
   - `flagsJson` = las flags y las observaciones en JSON compacto.
   - **Claves de §3.9:**
     - `exerciseKey` = `catalogDefinitionId` ?: `canonicalExerciseId` ?: `"name:" + nombre en minúsculas sin acentos`;
     - `configurationKey` = `catalogConfigurationId`.
4. **`TechniqueSidecars`.**
   - **Rutas** junto al vídeo, para pose2, bar, analysis, capture y part.
   - **Escritura y lectura:**
     - `writeAtomically(file, bytes)`;
     - `readAnalysis(file): AnalysisSidecar?`;
     - `writeAnalysis(...)`, con el formato `{"summaryVersion":1,"report":…,"exercise":ExerciseRef,"loadKg":…,"setKey":…,"analyzedAtMs":…}`.
     - `ExerciseRef` necesita `@Serializable`. Si hay que tocar `profiles/ExerciseRef.kt` (VT2a) para eso, ese cambio se permite.
   - **`deleteAll(videoFile)`.**
5. **`WorkoutMediaRepository`:**
   - `delete(id)` borra además los sidecars nuevos y la fila de análisis; la cascada lo hace, pero se borra de forma explícita y ANTES;
   - `setPoseTrackPath` y `setSetSnapshot`, dirigidos;
   - `syncSetSnapshots(sessionKey, completedSets: Map<String, CompletedSet>)`: actualiza reps y kg de los vídeos de la sesión según su clave de serie, con el lado normalizado (el ayudante de lado lo define VT8a; aquí, «left»/«right»/`null`);
   - `assignExercise(mediaId, …)`;
   - quita la escritura con `upsert(saved.copy(...))` que quede para la pose vieja.
6. **Ajustes:** `TechniqueVideoSettings` (§3.10) con su campo al final de `Settings`. `poseTrajectoryEnabled` NO se toca: lo retira VT9.

## Pruebas
- **`VideoAnalysisMigration28To29Test`** (Robolectric, SQL en bruto, patrón 27To28):
  - crea una BD v28 con una fila en `workout_media`, migra y comprueba las columnas y la tabla nueva;
  - con `PRAGMA foreign_keys = ON`, borrar el medio borra el análisis.
- **`VideoAnalysisMigrationValidateTest`:** `MigrationTestHelper.runMigrationsAndValidate(29, true, videoAnalysisMigration(28))`. Los esquemas ya son assets de prueba.
- **`VideoAnalysisDaoTest`** (Robolectric con BD en memoria):
  - `upsert` y `observeByMedia`;
  - `observeBySessionKey` con dos vídeos;
  - resúmenes por ejercicio;
  - `@Upsert` sobre `workout_media` NO borra el análisis.
- **`TechniqueSidecarsTest`:** ida y vuelta del sidecar de análisis; escritura atómica.
- **`VideoAnalysisSummaryTest`:** el mapeo desde un `SetTechniqueReport` de ejemplo; las claves.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.db.*Migration*" --tests "com.example.kpkn.data.db.VideoAnalysis*" --tests "com.example.kpkn.data.media.technique.*" --tests "com.example.kpkn.data.models.*VideoAnalysis*"`
  3. Las de medios que ya existen: `--tests "com.example.kpkn.data.repository.*Media*"`.

## Commits
- `feat(technique): Room v29 con la tabla video_analysis e ids de catálogo en workout_media`
- `feat(technique): sidecars del análisis, resumen y ajustes de técnica en vídeo`
- `test(technique): migración 28→29, DAO y sidecars`
