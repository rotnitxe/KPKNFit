# VT4c · El analizador real: pipeline de una pasada (pose + disco), sidecars y puntos de control

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt4c`, desde `feat/video-technique` con VT4b integrado (y con el dominio entero: VT1a–VT6a).
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3 (entera), §8 punto 3 (adquisición) y §9 puntos 3–4;
  - el código de `data/media/{video,pose,technique}`, `data/work/` y `domain/biomechanics/`.
- **Logs:** `C:\kw\logs\vt-4c-<n>.log`.

## Archivos permitidos
- **`data/media/technique/` (nuevos):**
  - `TechniqueAnalysisPipeline.kt`: implementa `TechniqueAnalyzer`;
  - `ExerciseRefResolver.kt`: `WorkoutMedia` + catálogo → `ExerciseRef`;
  - `CaptureMetadata.kt`: lee y escribe `<id>.capture.json`;
  - `AnalysisCheckpoint.kt`.
- **`data/media/technique/TechniqueAnalyzer.kt`:** `TechniqueAnalyzerProvider` pasa a devolver el pipeline real.
- **Pruebas:** `src/test/java/com/example/kpkn/data/media/technique/`, con Robolectric y falsos de `VideoFrameSource` y `PoseEstimator`.

## Qué haces
1. **`ExerciseRefResolver`:**
   - **Con `catalogConfigurationId` o `catalogDefinitionId`:** saca `movementPatternId`, `equipmentId` y `laterality` del catálogo, con `CatalogV2ProcessCache` / `approvedExerciseCatalogV2()` / `resolveCatalogExerciseInfo`, lo que exista. Busca la firma real con Grep.
   - **Solo con `canonicalExerciseId`:** intenta resolverlo en el catálogo.
   - **Si empieza por `custom:`, o no resuelve:** `isCustom = true`.
   - **`userProfileOverride`:** se lee de `<id>.capture.json`.
2. **`TechniqueAnalysisPipeline.analyze(request, onProgress)`:**
   1. **Perfil.** `AnalysisProfileResolver.resolve(ref)`. Si da `Unsupported` o `NeedsProfile` → se devuelve sin decodificar.
   2. **FULL.**
      - `CaptureMetadata`: gravedad, f_px y fps pedidos y concedidos.
      - `VideoFrameSources.open(file)`.
      - **Plan por fotograma:**
        - pose en cada fotograma si fs ≤ 35; si no, 1 de cada 2 (y 1 de 2 también si `GpuProbe` recomienda stride 2);
        - LUMA en todos si `profile.usesPlate`.
      - **Hilos:** decodificación y pose solapadas, con `Channel(2)` y un pool de 3 buffers.
      - **Pose:** cada fotograma va al estimador; el resultado, a `PoseTrackAssembler`.
      - **Disco** (si `usesPlate`):
        - en el primer tramo con la barra quieta (≥ 0,5 s con las muñecas casi inmóviles), `PlateDetector.propose` cerca de las muñecas, con el radio previo de la escala corporal;
        - la mejor propuesta es la semilla automática (`userConfirmed = false`);
        - desde ahí, `PlateTracker.track` en cada fotograma, hacia delante;
        - los fotogramas anteriores a la semilla se rastrean hacia atrás con un segundo rastreador sobre luma guardada en un búfer circular de como mucho 3 s; si no da tiempo, quedan sin disco.
        - `BarTrack.plateDiameterMm = null` hasta que el usuario confirme.
      - **Puntos de control:** cada 150 fotogramas, escribe `<id>.pose.part` con la traza parcial, el último PTS y el estado del rastreador.
        - Al reanudar: `decode(fromUs = últimoPts)` y continuar.
        - Al terminar, se borra.
      - **Pausa de cámara:** cada 30 fotogramas, si `AnalysisGate.cameraBusy` está activo, espera.
      - **Sidecars:** `<id>.pose2.json.gz` y `<id>.bar.json.gz`.
   3. **RECOMPUTE.** Lee los sidecars (pose2, bar y la semilla o el diámetro confirmados) sin decodificar.
   4. **PLATE_ONLY.**
      - Lee pose2.
      - Decodifica solo LUMA desde la semilla confirmada, hacia delante y hacia atrás.
      - Reescribe bar con `plateDiameterMm` = el confirmado.
   5. **Motor.** `TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(pose, bar, profile, loadKg, userHeightCm, hints))` → `Done(report)`.
   6. **Progreso:** la decodificación lleva de 0 a 95 % (por PTS/duración) y el motor de 95 a 100.
   7. **Errores:**
      - `CodecException` transitoria o IO → `Failed(code, transient = true)`;
      - cualquier otro → `Failed(code, false)`.

      Nunca lanza fuera.
3. **`TechniqueAnalyzerProvider.get(context)`:** devuelve el pipeline, con el `PoseEstimator` de `MediaPipePoseEstimators` en VIDEO y el modelo full.

## Pruebas
- **`TechniqueAnalysisPipelineTest`** (Robolectric):
  - con un `VideoFrameSource` falso que emite fotogramas de una traza de `SyntheticLifter` y un `PoseEstimator` falso que devuelve esos puntos → `Done`, con las reps de la verdad;
  - sidecars escritos;
  - progreso que sube de forma monótona hasta 100;
  - RECOMPUTE da el mismo informe sin tocar el `VideoFrameSource`;
  - un perfil sin soporte no abre el vídeo;
  - un punto de control a medio camino → la reanudación da el mismo resultado.
- **`ExerciseRefResolverTest`:** una configuración real del catálogo → su patrón y su equipamiento; `custom:` → `isCustom`.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.media.technique.*" --tests "com.example.kpkn.data.work.*"`
  3. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`
- **Emulador:** el arnés gana el modo `analyze_file`, que analiza `vt-bench/in/<vídeo>` con el pipeline real y escribe `vt-bench/out/<vídeo>.analysis.json`. Ejecútalo con un clip de `C:\kw\vt-bench\in\` si lo hay. El cambio en `TechniqueBenchActivity` se permite.

## Commits
- `feat(technique): pipeline del analizador (pose + disco en una pasada) con puntos de control`
- `feat(technique): resolución del ejercicio desde el catálogo y metadatos de captura`
