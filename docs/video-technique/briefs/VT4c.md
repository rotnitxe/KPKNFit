# VT4c · El analizador real: pipeline de una pasada (pose + disco), sidecars y puntos de control

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt4c`, desde `feat/video-technique` con VT4b integrado (y con el dominio entero: VT1a–VT6a).
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3 (entera), §8 punto 3 (adquisición) y §9 puntos 3–4;
  - el código de `data/media/{video,pose,technique}`, `data/work/` y `domain/biomechanics/`.
- **Logs:** `C:\kw\logs\vt-4c-<n>.log`.

## Paso 0: verificación oficial del dominio (lo primero, antes de editar)
VT3b y VT6a solo se probaron en el arnés JVM. Tu PRIMERA corrida, por `gradle_slot.sh`, es:
`:app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.*" --tests "com.example.kpkn.data.work.*" --tests "com.example.kpkn.data.media.*"`
- Mientras espera en la cola, lee y planifica, pero no edites hasta que termine.
- Si falla algo del dominio, NO lo arregles: anótalo con el error exacto y avisa en el informe. Sigue si tu parte compila.

## Heredado de VT6a (ya integrado en `a5b450ea1`; los nombres REALES mandan)
- **`LumaFrame(width, height, data: ByteArray)`:** NO copia `data`. El rastreador no guarda referencias al búfer entre llamadas, así que puedes reutilizar el mismo `ByteArray` fotograma a fotograma. Lo que deba vivir más (el búfer circular hacia atrás) sí se copia.
- **`PlateDetector.propose(frame, wristsPx: List<Pair<Float, Float>>, expectedRadiusPx: Float, max = 3): List<PlateCandidate>`:** devuelve `PlateCandidate(cx, cy, r, score, coverage)` en píxeles de la luma, ya ordenados.
  - El radio previo sale de la escala corporal, como dice §8 punto 3.
  - La mejor propuesta pasa a `PlateSeed(tUs, cx, cy, r, userConfirmed = false)`.
- **`PlateTracker(seed, fps, config = PlateTrackerConfig())`:**
  - `.track(frame, tUs, wristPriorPx: Pair<Float, Float>?): BarSample` en cada fotograma, con la muñeca del lado cercano como prior;
  - no es seguro para varios hilos: un rastreador por dirección (hacia delante y hacia atrás), cada uno en un solo hilo;
  - asigna 48 B por fotograma.
- **Coordenadas:** la pose va normalizada y la luma en píxeles. Las muñecas se pasan en píxeles del MISMO fotograma de luma, ya girado igual que la pose.
- **Rendimiento en el arnés JVM** (720×1280): 0,4–3,1 ms por fotograma según el radio. Cabe de sobra a 60 fps; la cifra buena es la del teléfono.

## Heredado de VT5b (ya integrado; los nombres REALES mandan)
- **Contrato**, en `data/media/technique/TechniqueAnalyzer.kt`:
  - `AnalysisMode { FULL, RECOMPUTE, PLATE_ONLY }`, que se guarda por nombre;
  - `TechniqueAnalysisRequest(mediaId, videoFile, mode, exercise: ExerciseRef, loadKg, plateDiameterMm, plateSeed, userHeightCm, setHints)`;
  - `TechniqueAnalysisOutcome`: `Done(report)`, `Unsupported(reason)`, `NeedsProfile(guess)` y `Failed(errorCode, transient)`;
  - `TechniqueAnalyzer.analyze(request, onProgress: suspend (Int) -> Unit)`.
- **Proveedor:** `TechniqueAnalyzerProvider.get(context)`. **Cambia SOLO su `create(context)`** para devolver tu pipeline. `UnavailableTechniqueAnalyzer` se queda, para las pruebas y como respaldo.
- **El `ExerciseRef` del request es PARCIAL.** El Worker lo arma con `exerciseRefOf(media, override)`: los ids de catálogo, el nombre, `isCustom = sin ids de catálogo` y `userProfileOverride`, sin patrón, material ni lateralidad.
  - Tu pipeline lo completa con `ExerciseRefResolver`, a partir del medio cargado por `request.mediaId`.
  - Usa también su `canonicalExerciseId` si no hay ids de catálogo: así un vídeo anterior a v29 no queda como «propio» por error.
  - Conserva el `userProfileOverride` del request, que gana a todo.
  - No toques el Worker.
- **Sidecar de captura `<id>.capture.json`:** VT5b ya escribe ahí las elecciones del usuario (`userProfileOverride`, `plateSeed` y `plateDiameterMm`) con `CaptureOverridesFile`, sin pisar otras claves.
  - Tu `CaptureMetadata` lee y escribe EL MISMO archivo. Todas las claves son opcionales.
  - Al escribir, conserva las claves que no conoces, igual que hace `CaptureOverridesFile`. Si puedes, reutilízalo en vez de duplicarlo.
- **Pausa de cámara:** `AnalysisGate.cameraBusy`, un `MutableStateFlow`. El Worker ya tiene el `mutex` y `awaitClear` antes de llamarte; tú solo esperas cada 30 fotogramas si `cameraBusy` está activo.
- **Estados:**
  - NEEDS_PROFILE guarda la conjetura en `profileId`;
  - un FULL cancela un RECOMPUTE o un PLATE_ONLY pendientes;
  - las peticiones explícitas (`KEY_EXPLICIT`) corren aunque el ajuste esté apagado.

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
   - **Si empieza por `custom:`, o no resuelve:** `isCustom = true`, y `guessedMovementPatternId` = el resultado de `ExercisePatternDetector` con el nombre (busca su firma).
   - **`userProfileOverride`:** se lee de `<id>.capture.json`.
2. **`TechniqueAnalysisPipeline.analyze(request, onProgress)`:**
   1. **Perfil.** `AnalysisProfileResolver.resolve(ref)`. Si da `Unsupported` o `NeedsProfile` → se devuelve sin decodificar.
   2. **FULL.**
      - `CaptureMetadata`: gravedad, f_px y fps pedidos y concedidos.
      - `VideoFrameSources.open(file)`.
      - **Plan por fotograma:**
        - pose en cada fotograma si fs ≤ 35; si no, 1 de cada 2 (y 1 de 2 también si `GpuProbe` recomienda stride 2);
        - LUMA en todos si `profile.usesPlate`.
      - **Hilos:** decodificación y pose solapadas, con un `Channel`. **Heredado de VT4a:**
        - `DecodedFrame` solo vale dentro de `onFrame`. El pool (`FramePool.SLOTS` en `data/media/video/VideoInfo.kt`) tiene 3 huecos que se reutilizan por turnos.
        - Con un `Channel(C)` llegan a estar vivos C + 2 fotogramas: el que procesa la pose, los C de la cola y el que espera en `send`. Por eso hace falta `SLOTS ≥ C + 2`.
        - Usa `Channel(2)` y sube `FramePool.SLOTS` a 4: es un cambio de una línea y está permitido. La otra opción es `Channel(1)` con 3 huecos.
        - Nunca guardes el `ByteBuffer` ni la luma fuera de esa ventana. Lo que deba vivir más, como el búfer circular de luma del disco, se COPIA.
      - **Heredado de VT4b** (`data/media/pose`, ya integrado):
        - `MediaPipePoseEstimators.create(context, mode, model = PoseModel.Full | HeavyFromFile(path), preferGpu = true, numPoses = 2)`;
        - `estimate(frame, timestampMs)` se puede llamar desde cualquier hilo: pasa al hilo `vt-pose` y espera el resultado. Entre la llamada y su vuelta el `RgbaFrame` no se puede reutilizar;
        - `GpuProbe.get(ctx).recommendedPoseStride` da el stride de pose (1 o 2);
        - `PoseTrackAssembler`: `add`, `build(engine)`, `resumeFrom` (para reanudar desde `<id>.pose.part`) y `multiPersonFraction` (para la flag MULTI_PERSON);
        - `PoseEstimatorReport` dice qué delegado se usó (GPU o CPU): anótalo en el sidecar.
      - **Camino de decodificación** (`(source as? VideoDecodeReport)?.path`): con `RETRIEVER`, la confianza del conteo se topa en MEDIA y se añade la flag `DECODE_FALLBACK`. Esa flag se añade AL FINAL de `AnalysisFlag` (`domain/biomechanics/metrics/AnalysisFlag.kt`): es una línea y está permitida.
      - **Cierre:** `source.use { … }` alrededor de la decodificación entera. Nunca se llama a `close()` mientras `decode` sigue en marcha: hay que cancelar y esperar antes.
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
