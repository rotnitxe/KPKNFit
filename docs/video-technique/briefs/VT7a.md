# VT7a · Reproductor de técnica: vídeo, capa sincronizada, controles y línea de repeticiones

- **Worktree y rama:** `C:\kw\vt-b`, rama `vt/vt7a`, desde `feat/video-technique` con el dominio entero (VT1a–VT6a).
  - No depende de VT4c: solo usa tipos del dominio (`PoseTrackV2`, `BarTrack`, `SetTechniqueReport`) y muestras de prueba.
  - VT4c se hace a la vez en `C:\kw\vt-a`.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, entero, incluida la sección «Pantallas»;
  - `docs/screens-v1/briefs/S_COMUN.md`;
  - `docs/design/KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 4, §3.1–§3.2, §3.5 y §9 punto 8 (reproductor);
  - el reproductor de hoy: `screens/workout/components/WorkoutVideoPlayer.kt` y `PoseTrajectoryOverlay.kt`;
  - la hoja de render patrón: `src/test/.../render/CatalogListRenderSheetTest.kt` o `BodyHistoryRenderSheetTest.kt`, que usan piezas de `ui/design`. `WorkoutRestRenderSheetTest.kt` ya no existe: se fue con la restauración de la sesión (U7).
- **Logs:** `C:\kw\logs\vt-7a-<n>.log`.
- **PNG:** `C:/kw/shots/vt/7a`.

Es una pieza reutilizable. La usan la pantalla de análisis (VT7b) y la de comparar (VT9b). Media3 sigue en **1.5.1**: no se sube.

## Archivos permitidos (nuevos, salvo los iconos)
- **`screens/technique/player/`:**
  - `TechniqueVideoPlayer.kt`: el composable con estado que posee el ExoPlayer;
  - `TechniquePlayerState.kt`: estado y acciones, con `rememberTechniquePlayerState(...)`;
  - `TechniqueOverlayData.kt`: los datos preparados para pintar: puntos por fotograma, trayectoria y marcas.
- **`screens/technique/player/design/`, sin estado:**
  - `TechniqueOverlay.kt`;
  - `TechniquePlayerControls.kt`;
  - `TechniqueScrubber.kt`;
  - `TechniqueRepChips.kt`.
- **`ui/components/icons/KpknLineIcons.kt`:** SOLO añadir al final `FrameBack`, `FrameForward` y `Target` (disco/diana), con la regla de la casa: retícula de 24, trazo de 1,75, extremos redondos y `icono(...)`.
- **Pruebas:**
  - `src/test/.../render/TechniquePlayerRenderSheetTest.kt`;
  - `src/test/.../screens/technique/TechniqueSamples.kt` (créalo);
  - `src/test/.../screens/technique/player/TechniqueOverlayDataTest.kt`.

## Qué haces
1. **`TechniqueOverlayData.from(track: PoseTrackV2?, bar: BarTrack?, report: SetTechniqueReport?, videoWidth: Int, videoHeight: Int)`**, puro:
   - por fotograma: `tUs` y los puntos del esqueleto en coordenadas normalizadas del fotograma girado (el mismo espacio que el vídeo en pantalla);
   - trayectoria del punto de barra: disco si hay `BarTrack` con cobertura; si no, el sustituto del perfil (muñecas o hombros). Cada punto con su velocidad relativa (0..1) por repetición;
   - `guideX` (medio pie) si el perfil mide verticalidad;
   - marcas de repetición: inicio, giro, fin, clase y lado;
   - la lista de PTS de los fotogramas para el paso a paso.
2. **`TechniqueVideoPlayer(file, overlay: TechniqueOverlayData?, state: TechniquePlayerState, modifier)`**:
   - ExoPlayer con `PlayerView` (`AndroidView`, `useController = false`, `resizeMode = FIT`) y `SeekParameters.EXACT`;
   - **velocidades:** 0,25×, 0,5× y 1× con `setPlaybackSpeed`;
   - **paso a paso:** `seekTo(floor(nextPtsUs / 1000))`, usando la lista de PTS (nunca redondear hacia arriba) y con pausa;
   - **bucle por repetición:** un `PlayerMessage` en el fin de la repetición, que vuelve a su inicio, con `setDeleteAfterDelivery(false)`; al cambiar de repetición o al desactivar el bucle, se cancela;
   - **capa sin sondeo:**
     - `setVideoFrameMetadataListener` escribe el PTS en un `AtomicLong`;
     - un bucle `withFrameNanos` lo copia a un `mutableLongStateOf`;
     - la capa (`Canvas`) lo lee SOLO en la fase de dibujo, así no recompone.
     - El rectángulo del vídeo sale de `player.videoSize`, ya girado, y del tamaño del contenedor (FIT).
   - **ciclo de vida:** suelta el reproductor en `onDispose` y se pausa en `ON_PAUSE`.
3. **`TechniqueOverlay`** (Canvas, sin estado; recibe `overlay`, `positionUs: () -> Long`, `videoRect` y opciones):
   - **esqueleto:** trazo de 1,5 dp en `WizardColors.text` al 35 %; el lado cercano al 60 %;
   - **trayectoria:** solo hasta la posición actual.
     - Color por velocidad relativa, de `SymbolPalette.columna` (lenta) a `SymbolPalette.energia` (rápida).
     - Trazo de 3 dp, con un punto de 6 dp en la posición actual.
   - **guía vertical:** punteada, en `WizardColors.textFaint`;
   - **sin asignaciones por fotograma:** reutiliza `Path` y arrays;
   - **movimiento reducido** (`wizardReducedMotion()`): sin trayectoria animada; se pinta entera.
4. **`TechniquePlayerControls`** (sin estado; ≥ 48 dp y con descripciones):
   - Play/Pausa (`KpknIconButton` con `Play`/`Pause`);
   - `FrameBack` y `FrameForward`;
   - velocidad (`KpknSegmentedControl` compacto «0,25× · 0,5× · 1×»);
   - bucle (`KpknIconButton(Repeat)`, `filled` si está activo).
5. **`TechniqueScrubber`** (sin estado):
   - pista fina con el progreso;
   - marcas por repetición: una muesca de 6 dp al inicio de cada una, más fuerte en la elegida y atenuada en las parciales;
   - arrastrar busca la posición;
   - TalkBack: «Posición 0:12 de 0:31, repetición 4 de 8».
6. **`TechniqueRepChips`** (sin estado): fila con desplazamiento de `KpknChip`, «1 2 3 …».
   - Las parciales llevan «·p» y las fallidas «·f».
   - Tocar una → `onSelectRep(i)`: el estado busca su inicio y, si el bucle está activo, lo repite.

## Pruebas
- **`TechniqueOverlayDataTest`:**
  - con la traza del `SyntheticLifter` (en el dominio de pruebas), las coordenadas caen en 0..1;
  - las velocidades relativas van de 0 a 1 en cada repetición;
  - la lista de PTS está ordenada;
  - sin `BarTrack`, se usa el sustituto.
- **Hoja de render** (360 dp, 100 % y 130 %):
  - controles en reposo y reproduciendo;
  - el scrubber con 8 marcas y la 4 elegida;
  - los chips con parciales y fallidas;
  - la capa sobre un fondo gris de 9:16 con una traza de ejemplo de `TechniqueSamples`, para ver el esqueleto, la trayectoria y la guía.
  - 3 rondas de crítica como mucho.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*"`
  3. `KPKN_RENDER_DIR=C:/kw/shots/vt/7a … --tests "com.example.kpkn.render.TechniquePlayerRenderSheetTest"`

## Commits
- `feat(technique): reproductor de técnica con capa sincronizada, paso a paso y bucle por repetición`
- `feat(ui): iconos FrameBack, FrameForward y Target en KPKN Line`
- `test(technique): hoja de render del reproductor`
